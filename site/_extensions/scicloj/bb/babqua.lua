-- Quarto Lua filter for evaluating Babashka code blocks.
--
-- Usage in .qmd frontmatter:
--   filters:
--     - bb
--
-- Code block options (use {.clojure .bb} for editor syntax highlighting):
--   ```{.clojure .bb}                — evaluate, show code + result
--   ```{.clojure .bb output=html}    — evaluate, render result as raw HTML
--   ```{.clojure .bb output=markdown} — evaluate, render result as markdown
--   ```{.clojure .bb output=hidden}  — evaluate silently (no code, no output)
--   ```{.clojure .bb eval=false}     — show code only, don't evaluate
--   ```{.clojure .bb echo=false}     — evaluate but hide the code
--
-- Kindly convention (metadata on the value):
--   ^:kind/hiccup [:div ...]   — hiccup → HTML (rendered on bb side)
--   ^:kind/html ["..."]        — render string as raw HTML
--   ^:kind/md ["..."]          — render string as markdown
--   ^:kind/hidden ...          — suppress output (code still shown)
--   ^:kind/mermaid ["..."]     — Mermaid diagram (CDN)
--   ^:kind/graphviz ["..."]    — Graphviz DOT diagram (CDN)
--   ^:kind/tex ["..."]         — TeX/LaTeX formula
--   ^:kind/code ["..."]        — syntax-highlighted Clojure code display
--   ^:kind/vega-lite {...}     — Vega-Lite chart (CDN)
--   ^:kind/plotly {...}        — Plotly chart (CDN)
--   ^:kind/echarts {...}       — ECharts chart (CDN)
--   ^:kind/cytoscape {...}     — Cytoscape graph (CDN)
--   ^:kind/highcharts {...}    — Highcharts chart (CDN)
--   ^:kind/table ...           — HTML table (rendered on bb side)
--
-- The filter runs in three passes:
--   1. Collect — record every `.bb` block's source (AST untouched).
--   2. Pandoc — invoke bb once on the collected sources, store the
--      JSON response of per-block results.
--   3. Replace — walk the AST again, swap each block for its
--      rendered output, inject babqua.css into header-includes.
-- Two evaluation modes (chosen per-render in run_evaluations):
--   - One-shot — `bb <script>`, fresh process per render. Default
--     for `quarto render` and any session without a persistent REPL.
--   - Persistent — when babqua-lifecycle.bb has spawned a long-lived
--     bb nREPL (detected via .babqua-pid + .babqua-nrepl-port), the
--     filter forwards the same eval script through
--     babqua-nrepl-client.bb. Defs accumulate across renders.
-- See the "persistent (nREPL preview) mode" section below.

-- Module-level state. Reset every render.
local project_root = nil
local default_hide_stdout = nil
local block_sources = {}   -- filled in pass 1: { {src, attrs, eval, result_idx} ... }
local block_results = nil  -- filled in pass 2 Meta: array from bb
local block_counter = 0    -- incremented per .bb block in pass 2 CodeBlock
local eval_failure_reason = nil  -- short cause string when block_results is nil
local cdn_emitted = {}
local div_counter = 0

-- ----- small helpers ---------------------------------------------------

local function parse_bool(s)
  if s == nil then return nil end
  s = tostring(s):lower()
  if s == "true" or s == "yes" or s == "1" or s == "on" then return true end
  if s == "false" or s == "no" or s == "0" or s == "off" then return false end
  return nil
end

local function digits_or_nil(s)
  if s and s:match("^%d+$") then return s end
  return nil
end

-- Shell-escape a string for use inside single quotes.
local function shell_quote(s)
  return "'" .. s:gsub("'", "'\\''") .. "'"
end

-- Wrap a string as a Clojure string literal: backslash-escape backslashes
-- and double quotes; leave real newlines literal (Clojure string syntax
-- supports them).
local function clj_string(s)
  s = s:gsub('\\', '\\\\')
  s = s:gsub('"', '\\"')
  return '"' .. s .. '"'
end

-- Encode a Lua string as a JSON/JS string literal (with quotes).
-- Used when splicing user-controlled content into inline <script> blocks.
-- `<` is escaped to `\u003c` because the HTML5 parser scans `<script>`
-- bodies for `</script>` regardless of JS string quoting, so a user
-- string containing `</script>` would otherwise terminate the element.
local function js_string_encode(s)
  s = s:gsub('\\', '\\\\')
  s = s:gsub('"', '\\"')
  s = s:gsub('\n', '\\n')
  s = s:gsub('\r', '\\r')
  s = s:gsub('\t', '\\t')
  s = s:gsub('<', '\\u003c')
  return '"' .. s .. '"'
end

-- Resolve where to anchor relative paths and the bb working directory.
-- Quarto's `quarto.project.directory` returns the project root for projects
-- and the input file's directory for standalone renders.
local function resolve_project_root()
  if quarto and quarto.project and quarto.project.directory then
    return quarto.project.directory
  end
  if quarto and quarto.doc and quarto.doc.input_file and pandoc.path then
    return pandoc.path.directory(quarto.doc.input_file)
  end
  return "."
end

-- Whether the current Pandoc target is HTML. Charts and diagrams are
-- HTML-only; on LaTeX/docx/gfm we pass `.bb` blocks through as plain
-- syntax-highlighted code rather than poison the AST with stray HTML.
local function html_target()
  if quarto and quarto.doc and quarto.doc.is_format then
    return quarto.doc.is_format("html") or false
  end
  return true
end

local function read_file(path)
  local f = io.open(path, "r")
  if not f then return "" end
  local s = f:read("*a")
  f:close()
  return s or ""
end

local function next_div_id()
  div_counter = div_counter + 1
  return "babqua-plot-" .. div_counter
end

-- ----- error / output helpers ------------------------------------------

local function print_loud(lines)
  local sep = string.rep("=", 64)
  io.stderr:write(sep .. "\n")
  for _, line in ipairs(lines) do
    io.stderr:write("[babqua] " .. line .. "\n")
  end
  io.stderr:write(sep .. "\n")
end

-- Build a visible error block for the rendered document. Mirrors Quarto's
-- native cell-output-error pattern so themes apply uniformly.
local function error_block(title, detail)
  io.stderr:write("[babqua] ERROR: " .. title .. "\n")
  local content = "[babqua] ERROR: " .. title
  if detail and detail ~= "" then
    content = content .. "\n\n" .. detail
  end
  return pandoc.Div(
    pandoc.CodeBlock(content, pandoc.Attr("", {"error"})),
    pandoc.Attr("", {"cell-output", "cell-output-error"})
  )
end

local function emit_stdout_block(blocks, stdout)
  if stdout and stdout ~= "" then
    table.insert(blocks, pandoc.Div(
      pandoc.CodeBlock(stdout),
      pandoc.Attr("", {"cell-output", "cell-output-stdout"})
    ))
  end
end

local function emit_display_block(blocks, content)
  table.insert(blocks, pandoc.Div(
    content,
    pandoc.Attr("", {"cell-output", "cell-output-display"})
  ))
end

-- ----- CSS injection ---------------------------------------------------

-- Inject babqua.css into the document via header-includes. The CSS
-- contribution declared in _extension.yml is honored only for *format*
-- extensions; Babqua is a filter extension, so the file never reaches the
-- rendered HTML otherwise.
local function inject_header_css(meta)
  local script = PANDOC_SCRIPT_FILE
  local dir = script:match("(.*[/\\])") or "./"
  local f = io.open(dir .. "babqua.css", "r")
  if not f then return meta end
  local css = f:read("*a")
  f:close()
  if not css or css == "" then return meta end

  local style_block = pandoc.RawBlock("html", "<style>\n" .. css .. "</style>")
  local existing = meta["header-includes"]
  if not existing then
    meta["header-includes"] = pandoc.MetaBlocks({style_block})
  elseif existing.t == "MetaBlocks" then
    table.insert(existing, style_block)
  elseif existing.t == "MetaList" then
    table.insert(existing, pandoc.MetaBlocks({style_block}))
  else
    meta["header-includes"] = pandoc.MetaList({
      existing,
      pandoc.MetaBlocks({style_block}),
    })
  end
  return meta
end

-- ----- CDN scripts -----------------------------------------------------

-- Pinned CDN scripts with SRI integrity hashes. Loose version tags would
-- silently follow upstream updates and provide no integrity check, so a
-- CDN compromise or a malicious patch could be served to every viewer of
-- every rendered doc. Pinning version + SRI hash means the browser
-- refuses to execute if served bytes don't match.
--
-- To refresh a pin: pick a new version, then
--   curl -sL <url> | openssl dgst -sha384 -binary | openssl base64 -A
local CDN = {
  mermaid = {
    src = "https://cdn.jsdelivr.net/npm/mermaid@11.14.0/dist/mermaid.min.js",
    integrity = "sha384-1CMXl090wj8Dd6YfnzSQUOgWbE6suWCaenYG7pox5AX7apTpY3PmJMeS2oPql4Gk",
  },
  vega = {
    src = "https://cdn.jsdelivr.net/npm/vega@5.33.1",
    integrity = "sha384-NMXhl2TbCXxcN7o4ROC56Funm78m4AylL8gMg/7Kn4YU+wrm23K9l7cY8lDRXQ9d",
  },
  ["vega-lite"] = {
    src = "https://cdn.jsdelivr.net/npm/vega-lite@5.23.0",
    integrity = "sha384-D9LYH0esGjcxQJsBuxOuXtCDJGXRWW1+KhluzWPqi0rLJmiR/ygPChefaD+rFFDQ",
  },
  ["vega-embed"] = {
    src = "https://cdn.jsdelivr.net/npm/vega-embed@6.29.0",
    integrity = "sha384-M+Ax7e/WFJpxSOF09HzI+Sj4wg9ottVd/uxmV2ItGGh02fLH28t2FAOJx3TJBap5",
  },
  plotly = {
    src = "https://cdn.plot.ly/plotly-2.35.0.min.js",
    integrity = "sha384-TAqBiqItCr14J//ULLD26bSQ8Z6uPnlisSwkvWaqP8SCSiDkgR8jNknuAv8uxSOT",
  },
  echarts = {
    src = "https://cdn.jsdelivr.net/npm/echarts@5.6.0/dist/echarts.min.js",
    integrity = "sha384-pPi0zxBAoDu6+JXW/C68UZLvBUUtU+7zonhif43rqj7pxsGyqyqzcian2Rj37Rss",
  },
  cytoscape = {
    src = "https://cdn.jsdelivr.net/npm/cytoscape@3.33.2/dist/cytoscape.min.js",
    integrity = "sha384-UBHkMiqJzzg1WHS7U4a5IU9bewC9iEYdOsU7c7ar4TgobsyodECBexvEuovn7a0P",
  },
  highcharts = {
    src = "https://code.highcharts.com/12.6.0/highcharts.js",
    integrity = "sha384-oVN+UvYVEgXjYVI7ww5itQNNt/Tgr7TOADG2btfqV/eQkPwpOL44P81GtEp2L7wt",
  },
  graphviz = {
    src = "https://cdn.jsdelivr.net/npm/@viz-js/viz@3.17.0/dist/viz-global.min.js",
    integrity = "sha384-hhfCh87gn6AaMzlh2cgEwt+9VyM3DUYUrUg4H8eLNaLkjDrX78xSf3Nc84ZUmnLL",
  },
}

local function script_tag(name)
  if cdn_emitted[name] then return "" end
  cdn_emitted[name] = true
  local s = CDN[name]
  return '<script src="' .. s.src
    .. '" integrity="' .. s.integrity
    .. '" crossorigin="anonymous"></script>'
end

local function dim_style(width, height, default_w, default_h)
  local w = width or default_w
  local h = height or default_h
  if not w and not h then return "" end
  local parts = {}
  if w then table.insert(parts, "width:" .. w .. "px") end
  if h then table.insert(parts, "height:" .. h .. "px") end
  return ' style="' .. table.concat(parts, ";") .. ';"'
end

-- Chart kinds: each pairs a list of CDN libraries with the inline init
-- script that hands the JSON spec to the library. ECharts/Cytoscape ship
-- default sizes because their libraries refuse to render to a size-less
-- div; the others auto-fit.
local CHART_LIB = {
  ["vega-lite"] = {
    libs = {"vega", "vega-lite", "vega-embed"},
    init = function(div_id, json)
      return 'vegaEmbed("#' .. div_id .. '", ' .. json .. ');'
    end,
  },
  plotly = {
    libs = {"plotly"},
    init = function(div_id, json)
      return 'var spec=' .. json .. ';'
        .. 'Plotly.newPlot("' .. div_id .. '", spec.data, spec.layout);'
    end,
  },
  echarts = {
    libs = {"echarts"},
    default_w = "600",
    default_h = "400",
    init = function(div_id, json)
      return 'echarts.init(document.getElementById("' .. div_id
        .. '")).setOption(' .. json .. ');'
    end,
  },
  cytoscape = {
    libs = {"cytoscape"},
    default_w = "600",
    default_h = "400",
    init = function(div_id, json)
      return 'var spec=' .. json
        .. ';spec.container=document.getElementById("' .. div_id .. '");'
        .. 'cytoscape(spec);'
    end,
  },
  highcharts = {
    libs = {"highcharts"},
    init = function(div_id, json)
      return 'Highcharts.chart("' .. div_id .. '", ' .. json .. ');'
    end,
  },
}

local function emit_chart(blocks, lib_name, json, width, height)
  local cfg = CHART_LIB[lib_name]
  if not cfg then
    table.insert(blocks, error_block(
      "Unknown chart library: " .. lib_name, json))
    return
  end
  local div_id = next_div_id()
  local tags = {}
  for _, lib in ipairs(cfg.libs) do
    table.insert(tags, script_tag(lib))
  end
  local html = '<div id="' .. div_id .. '"'
    .. dim_style(width, height, cfg.default_w, cfg.default_h) .. '></div>'
    .. table.concat(tags)
    .. '<script>' .. cfg.init(div_id, json) .. '</script>'
  emit_display_block(blocks, pandoc.RawBlock("html", html))
end

local function emit_mermaid(blocks, source, width, height)
  local div_id = next_div_id()
  local html = '<div id="' .. div_id .. '"' .. dim_style(width, height) .. '></div>'
    .. script_tag("mermaid")
    .. '<script>'
    .. 'mermaid.initialize({ startOnLoad: false });'
    .. 'mermaid.render("' .. div_id .. '-svg", '
    .. js_string_encode(source) .. ').then(({svg}) => {'
    .. 'document.getElementById("' .. div_id .. '").innerHTML = svg;'
    .. '}).catch(e => {'
    .. 'document.getElementById("' .. div_id .. '").innerText = '
    .. '"Mermaid error: " + (e && e.message ? e.message : e);'
    .. '});'
    .. '</script>'
  emit_display_block(blocks, pandoc.RawBlock("html", html))
end

local function emit_graphviz(blocks, source, width, height)
  local div_id = next_div_id()
  local html = '<div id="' .. div_id .. '"' .. dim_style(width, height) .. '></div>'
    .. script_tag("graphviz")
    .. '<script>'
    .. 'Viz.instance().then(function (viz) {'
    .. 'try {'
    .. 'document.getElementById("' .. div_id .. '").appendChild('
    .. 'viz.renderSVGElement(' .. js_string_encode(source) .. '));'
    .. '} catch (e) {'
    .. 'document.getElementById("' .. div_id .. '").innerText = '
    .. '"Graphviz error: " + (e && e.message ? e.message : e);'
    .. '}'
    .. '}).catch(function (e) {'
    .. 'document.getElementById("' .. div_id .. '").innerText = '
    .. '"Graphviz library failed to load: " + (e && e.message ? e.message : e);'
    .. '});'
    .. '</script>'
  emit_display_block(blocks, pandoc.RawBlock("html", html))
end

-- ----- bb invocation ---------------------------------------------------

-- Whether `bb` is on PATH.
local function bb_available()
  local ok = os.execute("command -v bb >/dev/null 2>&1")
  return ok == true or ok == 0
end

-- ----- persistent (nREPL preview) mode --------------------------------
--
-- Babqua has two evaluation paths:
--   1. **One-shot** — spawn `bb` with the eval script directly. Used for
--      `quarto render` and any session where the user hasn't started a
--      persistent REPL.
--   2. **Persistent** — when the lifecycle script (`babqua-lifecycle.bb
--      start`) has spawned a long-lived bb nREPL, the filter detects
--      `.babqua-nrepl-port` + a live `.babqua-pid` and forwards the same
--      eval script through `babqua-nrepl-client.bb`. Defs accumulate
--      across renders the way they would in any persistent REPL session.
--
-- Mode is decided per-render solely by port-file presence and PID
-- liveness. There's no Quarto-preview env-var sniff (Quarto 1.9 doesn't
-- expose one to filters), and no auto-start — the user opts into
-- persistence explicitly with the lifecycle script.

local function read_number_file(path)
  local f = io.open(path, "r")
  if not f then return nil end
  local s = f:read("*a") or ""
  f:close()
  s = s:gsub("%s+", "")
  if s:match("^%d+$") then return s end
  return nil
end

local function pid_alive(pid)
  local ok = os.execute("kill -0 " .. pid .. " >/dev/null 2>&1")
  return ok == true or ok == 0
end

-- Discover the live nREPL port for this project root, or nil if no
-- persistent REPL is running. Cleans up stale PID/port files when the
-- referenced process is dead so the next render doesn't keep tripping
-- on them.
local function live_nrepl_port()
  if not project_root then return nil end
  local pid = read_number_file(project_root .. "/.babqua-pid")
  local port = read_number_file(project_root .. "/.babqua-nrepl-port")
  if pid and port and pid_alive(pid) then return port end
  if pid and not pid_alive(pid) then
    -- Stale leftovers — let the lifecycle script's next start handle
    -- cleanup, but warn so the user knows preview mode is silently off.
    io.stderr:write("[babqua] Stale .babqua-pid (PID " .. pid
      .. " not alive) — falling back to one-shot mode.\n")
  end
  return nil
end

local function lifecycle_script_path()
  local script_dir = PANDOC_SCRIPT_FILE:match("(.*[/\\])") or "./"
  return script_dir .. "babqua-lifecycle.bb"
end

local function nrepl_client_path()
  local script_dir = PANDOC_SCRIPT_FILE:match("(.*[/\\])") or "./"
  return script_dir .. "babqua-nrepl-client.bb"
end

-- `babqua: { reset-on-render: true }` in frontmatter — escape hatch
-- for users who want a fresh process each render. Stops the running
-- session before this render begins so the next render starts cold
-- and the user must explicitly restart for persistence.
local function reset_on_render_requested(meta)
  if not (meta and meta.babqua) then return false end
  local opt = meta.babqua["reset-on-render"]
  if opt == nil then return false end
  return parse_bool(pandoc.utils.stringify(opt)) == true
end

local function stop_lifecycle()
  local cmd = "BABQUA_PROJECT_ROOT=" .. shell_quote(project_root) .. " "
    .. "bb " .. shell_quote(lifecycle_script_path()) .. " stop"
    .. " >/dev/null 2>&1"
  os.execute(cmd)
end

-- Build the Clojure script that loads the runtime and calls run-blocks
-- on the collected sources. Output is one line of JSON: an array of
-- per-block result maps.
local function build_bb_script(runtime_path, eval_sources)
  local parts = {}
  table.insert(parts, "(load-file " .. clj_string(runtime_path) .. ")\n")
  table.insert(parts, "(require '[cheshire.core :as babqua-json])\n")
  -- Wrap in try/catch so a load-file or runtime-internal failure still
  -- produces parseable JSON output rather than crashing bb mid-stream.
  table.insert(parts, "(try\n")
  table.insert(parts, "  (println (babqua-json/generate-string (babqua.runtime/run-blocks [")
  for _, src in ipairs(eval_sources) do
    table.insert(parts, "{:src " .. clj_string(src) .. "} ")
  end
  table.insert(parts, "])))\n")
  table.insert(parts, "  (catch Throwable e\n")
  table.insert(parts, "    (println (babqua-json/generate-string\n")
  table.insert(parts, "      {:babqua/fatal (or (.getMessage e) (str e))}))))\n")
  return table.concat(parts)
end

-- Invoke bb on the script. Returns (stdout, stderr, ok).
--
-- One-shot path: `bb <script_file>` — fresh bb, fresh user ns.
-- Persistent path (when nrepl_port is non-nil): pipe the same script to
-- `bb babqua-nrepl-client.bb <port>`, which forwards it as one nREPL
-- eval. The runtime's `(println (json/generate-string ...))` becomes
-- one or more `:out` chunks, and the client relays them to its own
-- stdout — so the JSON-extraction logic downstream is identical.
-- Temp files live inside a per-render mode-0700 directory created by
-- `pandoc.system.with_temporary_directory`. `os.tmpname()` would return
-- a name *without* creating the file on POSIX, leaving a window for a
-- local attacker to plant a symlink at the path before `io.open(..., "w")`
-- or the shell `>` redirection follows it.
local function run_bb(script_text, nrepl_port)
  local stdout, stderr, ok
  pandoc.system.with_temporary_directory("babqua", function(dir)
    local script_file = dir .. "/script.bb"
    local stdout_file = dir .. "/stdout"
    local stderr_file = dir .. "/stderr"

    local f = io.open(script_file, "w")
    if not f then
      stdout, stderr, ok = nil, "Could not create temp script file", false
      return
    end
    f:write(script_text)
    f:close()

    local cmd
    if nrepl_port then
      cmd = "cd " .. shell_quote(project_root)
        .. " && bb " .. shell_quote(nrepl_client_path())
        .. " " .. nrepl_port
        .. " < " .. shell_quote(script_file)
        .. " > " .. shell_quote(stdout_file)
        .. " 2> " .. shell_quote(stderr_file)
    else
      cmd = "cd " .. shell_quote(project_root)
        .. " && bb " .. shell_quote(script_file)
        .. " > " .. shell_quote(stdout_file)
        .. " 2> " .. shell_quote(stderr_file)
    end
    local ok_exec = os.execute(cmd)
    ok = (ok_exec == true or ok_exec == 0)

    stdout = read_file(stdout_file)
    stderr = read_file(stderr_file)
  end)
  return stdout, stderr, ok
end

-- Find the JSON line in mixed stdout. The runtime's last println is the
-- response; anything else (logs, warnings) gets ignored.
local function extract_json_line(stdout)
  local last
  for line in stdout:gmatch("[^\r\n]+") do
    local trimmed = line:gsub("^%s+", ""):gsub("%s+$", "")
    if trimmed:sub(1, 1) == "[" or trimmed:sub(1, 1) == "{" then
      last = trimmed
    end
  end
  return last
end

local function ensure_json_decoder()
  if pandoc.json and pandoc.json.decode then return true end
  return false
end

-- Set in pass2_run_bb when reset-on-render is requested via frontmatter.
-- Read here so run_evaluations can stop the running session before
-- resolving the port — that way the reset takes effect on this render,
-- not the next.
local reset_requested = false
-- Set in run_evaluations from live_nrepl_port(); read by run_bb to
-- decide between one-shot and nrepl-client invocation.
local persistent_port = nil

local function run_evaluations()
  if #block_sources == 0 then return end

  if not bb_available() then
    print_loud({
      "ERROR: `bb` is not on PATH.",
      "Babqua needs Babashka to evaluate `.bb` code blocks.",
      "",
      "Install from: https://babashka.org",
    })
    eval_failure_reason = "`bb` is not on PATH. Install Babashka from https://babashka.org."
    block_results = nil
    return
  end

  if not ensure_json_decoder() then
    print_loud({
      "ERROR: pandoc.json is not available in this Pandoc build.",
      "Babqua needs `pandoc.json.decode` to parse the bb runtime's response.",
      "",
      "Upgrade Quarto / Pandoc to a version with pandoc.json (Pandoc 2.18+).",
    })
    eval_failure_reason = "`pandoc.json` is unavailable. Upgrade Quarto/Pandoc to a build with Pandoc 2.18+."
    block_results = nil
    return
  end

  -- Resolve evaluation mode. `reset-on-render` short-circuits any live
  -- REPL so this render starts cold and the user has to manually
  -- restart for persistence.
  if reset_requested then
    if live_nrepl_port() then
      io.stderr:write("[babqua] reset-on-render: stopping persistent bb nREPL.\n")
      stop_lifecycle()
    end
    persistent_port = nil
  else
    persistent_port = live_nrepl_port()
    if persistent_port then
      io.stderr:write("[babqua] Using persistent bb nREPL on port "
        .. persistent_port .. " (state accumulates across renders).\n")
    end
  end

  -- Filter to only blocks marked for eval, recording where each result
  -- will land so pass 2's CodeBlock can index back.
  local eval_sources = {}
  for _, s in ipairs(block_sources) do
    if s.eval then
      table.insert(eval_sources, s.src)
      s.result_idx = #eval_sources
    end
  end

  if #eval_sources == 0 then return end

  local script_path = PANDOC_SCRIPT_FILE
  local script_dir = script_path:match("(.*[/\\])") or "./"
  local runtime_path = script_dir .. "runtime.bb"

  local script = build_bb_script(runtime_path, eval_sources)
  local stdout, stderr, ok = run_bb(script, persistent_port)

  if not ok then
    print_loud({
      "bb invocation exited with a non-zero status.",
      "stderr (truncated):",
      (stderr or ""):sub(1, 2000),
    })
  end

  local json_line = stdout and extract_json_line(stdout)
  if not json_line then
    print_loud({
      "ERROR: Could not find a JSON response in bb's stdout.",
      "stdout:",
      (stdout or ""):sub(1, 1000),
      "stderr:",
      (stderr or ""):sub(1, 1000),
    })
    eval_failure_reason = "bb produced no JSON response. See the framed stderr block for the bb stdout/stderr."
    block_results = nil
    return
  end

  local ok_decode, decoded = pcall(pandoc.json.decode, json_line)
  if not ok_decode then
    print_loud({
      "ERROR: Could not decode bb's JSON response.",
      "Decode error: " .. tostring(decoded),
      "Response (truncated):",
      json_line:sub(1, 1000),
    })
    eval_failure_reason = "Could not decode bb's JSON response: " .. tostring(decoded)
    block_results = nil
    return
  end

  if type(decoded) == "table" and decoded["babqua/fatal"] then
    print_loud({
      "ERROR: bb runtime hit a fatal error before evaluating any block:",
      tostring(decoded["babqua/fatal"]),
    })
    eval_failure_reason = "bb runtime fatal error: " .. tostring(decoded["babqua/fatal"])
    block_results = nil
    return
  end

  block_results = decoded
end

-- ----- option parsing --------------------------------------------------

local function read_meta_defaults(meta)
  if not (meta and meta.babqua) then return end
  local h = meta.babqua["hide-stdout"]
  if h ~= nil then
    default_hide_stdout = parse_bool(pandoc.utils.stringify(h))
  end
end

-- ----- pass 1: collect -------------------------------------------------

-- Quarto's `#| key: value` cell-options syntax is left in the block's
-- text by Quarto/Pandoc; engines like the Jupyter/knitr ones strip it
-- themselves. Babqua does the same: parse leading `#|` lines into the
-- block's attributes and remove them from the source bb sees, so e.g.
-- `#| output: hidden` behaves identically to `{.bb output=hidden}`.
local function parse_cell_directives(text, attrs)
  local i, n = 1, #text
  while i <= n do
    local j = text:find("\n", i, true) or (n + 1)
    local line = text:sub(i, j - 1)
    local key, value = line:match("^#|%s*([%w_-]+)%s*:%s*(.+)$")
    if key then
      attrs[key] = (value:gsub("%s+$", ""))
      i = j + 1
    else
      return text:sub(i)
    end
  end
  return ""
end

local function collect_block(el)
  if not el.classes:includes("bb") then return nil end
  if not html_target() then return nil end
  -- Copy attributes so we can layer in `#|` directives without mutating
  -- the AST node (which pass 3 will re-read).
  local attrs = {}
  for k, v in pairs(el.attributes) do attrs[k] = v end
  local src = parse_cell_directives(el.text, attrs)
  table.insert(block_sources, {
    src = src,
    attrs = attrs,
    eval = attrs["eval"] ~= "false",
    result_idx = nil,
  })
  return nil  -- leave AST untouched in pass 1
end

-- ----- pass 2: replace -------------------------------------------------

local function dispatch_render(blocks, result, output_mode_override, width, height)
  -- output_mode_override comes from `output=` fence attr; "html" / "markdown"
  -- / "hidden" override Kindly's choice. Otherwise we honor result.format.
  local format = result.format
  if output_mode_override == "hidden" then
    return
  elseif output_mode_override == "html" then
    if result.rendered then
      emit_display_block(blocks, pandoc.RawBlock("html", result.rendered))
    end
    return
  elseif output_mode_override == "markdown" then
    if result.rendered then
      local doc = pandoc.read(result.rendered, "markdown")
      emit_display_block(blocks, doc.blocks)
    end
    return
  end

  if format == "hidden" then
    return
  elseif format == "raw-html" then
    emit_display_block(blocks, pandoc.RawBlock("html", result.rendered or ""))
  elseif format == "markdown" then
    local doc = pandoc.read(result.rendered or "", "markdown")
    emit_display_block(blocks, doc.blocks)
  elseif format == "tex" then
    local md = "$$" .. (result.rendered or "") .. "$$"
    local doc = pandoc.read(md, "markdown")
    emit_display_block(blocks, doc.blocks)
  elseif format == "mermaid" then
    emit_mermaid(blocks, result.rendered or "", width, height)
  elseif format == "graphviz" then
    emit_graphviz(blocks, result.rendered or "", width, height)
  elseif format == "chart" then
    emit_chart(blocks, result.lib, result.rendered or "{}", width, height)
  elseif format == "code-display" or format == "code-default" then
    emit_display_block(blocks,
      pandoc.CodeBlock(result.rendered or "", pandoc.Attr("", {"clojure"})))
  else
    table.insert(blocks, error_block(
      "Unknown render format: " .. tostring(format),
      result.rendered or "(no rendered payload)"))
  end
end

local function replace_block(el)
  if not el.classes:includes("bb") then return nil end
  if not html_target() then return nil end

  block_counter = block_counter + 1
  local src_record = block_sources[block_counter]
  if not src_record then
    -- pass-2 block count diverged from pass 1 (shouldn't happen with our
    -- nil-returning pass 1). Surface rather than render silently wrong.
    return error_block(
      "Internal: block index " .. block_counter .. " has no recorded source.",
      el.text)
  end

  -- Use the attrs computed in pass 1 (which folded in `#| ...` directives),
  -- not el.attributes — the AST node still carries only the fence
  -- attributes, not anything Quarto's per-cell directive syntax added.
  local attrs = src_record.attrs
  local code = src_record.src
  local output_mode = attrs["output"] -- nil / html / markdown / hidden
  local echo = attrs["echo"] ~= "false"
  local eval = src_record.eval

  local hide_stdout = parse_bool(attrs["hide-stdout"])
  if hide_stdout == nil then hide_stdout = default_hide_stdout end
  if hide_stdout == nil then hide_stdout = false end

  local fence_width = digits_or_nil(attrs["width"])
  local fence_height = digits_or_nil(attrs["height"])

  if output_mode == "hidden" then echo = false end

  local blocks = {}

  if echo then
    table.insert(blocks, pandoc.CodeBlock(code, pandoc.Attr("", {"clojure"})))
  end

  if eval then
    if not block_results then
      local detail = eval_failure_reason
        or "See stderr above for the underlying error."
      table.insert(blocks, error_block(
        "Babqua could not evaluate this block (no results from bb).",
        detail))
    else
      local result = block_results[src_record.result_idx]
      if not result then
        table.insert(blocks, error_block(
          "No result for block #" .. tostring(src_record.result_idx) .. ".",
          code))
      elseif result.error then
        local err_text = result.error
        if result.stack and result.stack ~= "" then
          err_text = err_text .. "\n\n" .. result.stack
        end
        local err_children = {pandoc.CodeBlock(err_text, pandoc.Attr("", {"error"}))}
        if not echo then
          table.insert(err_children, pandoc.RawBlock("html",
            "<details><summary>Show failing source</summary>"))
          table.insert(err_children, pandoc.CodeBlock(code, pandoc.Attr("", {"clojure"})))
          table.insert(err_children, pandoc.RawBlock("html", "</details>"))
        end
        table.insert(blocks, pandoc.Div(
          err_children,
          pandoc.Attr("", {"cell-output", "cell-output-error"})
        ))
        if not hide_stdout then emit_stdout_block(blocks, result.stdout) end
      else
        if not hide_stdout then emit_stdout_block(blocks, result.stdout) end
        local opts = result.options or {}
        local w = fence_width or opts.width
        local h = fence_height or opts.height
        if output_mode ~= "hidden" then
          dispatch_render(blocks, result, output_mode, w, h)
        end
      end
    end
  end

  -- Wrap in Quarto's outer `cell` Div so theme rules apply uniformly.
  if #blocks > 0 then
    return { pandoc.Div(blocks, pandoc.Attr("", {"cell"})) }
  end
  return blocks
end

-- ----- pass orchestration ---------------------------------------------

-- Pandoc visits Meta AFTER Blocks within a single pass, so we can't put
-- the bb invocation in a Meta function alongside the replacement
-- CodeBlock — replace_block would fire first with no results to look up.
-- Solution: three passes.
--   Pass 1: collect every `.bb` block source.
--   Pass 2: in a Pandoc function (which runs at the end of the pass),
--           parse frontmatter and invoke bb. Doc is unchanged.
--   Pass 3: replace blocks with their rendered AST and inject CSS.
local function pass2_run_bb(doc)
  if not html_target() then return doc end
  project_root = resolve_project_root()
  read_meta_defaults(doc.meta)
  reset_requested = reset_on_render_requested(doc.meta)
  eval_failure_reason = nil
  run_evaluations()
  block_counter = 0
  return doc
end

return {
  { CodeBlock = collect_block },
  { Pandoc = pass2_run_bb },
  { Meta = inject_header_css, CodeBlock = replace_block }
}
