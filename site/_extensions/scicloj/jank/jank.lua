-- Quarto Lua filter for evaluating Jank code blocks via nREPL.
--
-- Usage in .qmd frontmatter:
--   filters:
--     - jank
--
-- Port is resolved automatically (no config needed).
--
-- Code block options (use {.clojure .jank} for editor syntax highlighting):
--   ```{.clojure .jank}                — evaluate, show code + result as code block
--   ```{.clojure .jank output=html}    — evaluate, render result as raw HTML (SVG, Plotly, etc.)
--   ```{.clojure .jank output=markdown} — evaluate, render result as markdown (tables, etc.)
--   ```{.clojure .jank output=hidden}  — evaluate silently (no code, no output)
--   ```{.clojure .jank eval=false}     — show code only, don't evaluate
--   ```{.clojure .jank echo=false}     — evaluate but hide the code
--
-- Kindly convention:
--   ^:kind/hiccup [:div ...]  — auto-converts hiccup to HTML
--   ^:kind/html "..."         — renders string as raw HTML
--   ^:kind/md "..."           — renders string as markdown
--   ^:kind/hidden [...]       — suppresses output (code still shown)
--   ^:kind/mermaid "..."      — Mermaid diagram (Quarto native)
--   ^:kind/graphviz "..."     — Graphviz DOT diagram (via CDN)
--   ^:kind/tex "..."          — TeX/LaTeX formula
--   ^:kind/code "..."         — syntax-highlighted Clojure code display
--   ^:kind/vega-lite {...}    — Vega-Lite chart (via CDN)
--   ^:kind/plotly {...}       — Plotly chart (via CDN)
--   ^:kind/echarts {...}      — ECharts chart (via CDN)
--   ^:kind/cytoscape {...}    — Cytoscape graph (via CDN)
--   ^:kind/highcharts {...}   — Highcharts chart (via CDN)

local jank_port = nil
local project_root = nil

-- Document-wide defaults read from frontmatter `jank:` map. Per-block fence
-- attributes and value-level Kindly options override these.
local default_timeout = nil
local default_hide_stdout = nil

-- Parse a string into a boolean. Accepts the common true/false aliases.
-- Returns nil for unrecognized input so callers can distinguish "explicitly
-- set" from "absent".
local function parse_bool(s)
  if s == nil then return nil end
  s = tostring(s):lower()
  if s == "true" or s == "yes" or s == "1" or s == "on" then return true end
  if s == "false" or s == "no" or s == "0" or s == "off" then return false end
  return nil
end

-- Return s if it's a non-empty digit-only string, nil otherwise.
-- Used everywhere we accept user input that should be a port, PID, or
-- pixel count: filenames, env vars, frontmatter values, fence attributes.
local function digits_or_nil(s)
  if s and s:match("^%d+$") then return s end
  return nil
end

-- Resolve where to anchor PID/port/log files for this render.
-- Quarto's `quarto.project.directory` returns the input file's directory in
-- both project mode (with _quarto.yml) and standalone mode (without). We
-- pass the resolved path to the lifecycle script via JANQUA_PROJECT_ROOT so
-- the script doesn't have to re-derive it.
-- Filename namespace (`.jank-pid`, `.jank-nrepl-port`, `.jank-repl.log`) is
-- private to Janqua, so even an unexpected anchor only touches our own files.
local function resolve_project_root()
  if quarto and quarto.project and quarto.project.directory then
    return quarto.project.directory
  end
  if quarto and quarto.doc and quarto.doc.input_file and pandoc.path then
    return pandoc.path.directory(quarto.doc.input_file)
  end
  return nil
end

-- Shell-escape a string for use inside single quotes.
-- Replaces ' with '\'' (end quote, escaped quote, reopen quote).
local function shell_quote(s)
  return "'" .. s:gsub("'", "'\\''") .. "'"
end

-- Verify a port speaks nREPL by performing a trivial eval.
-- A plain TCP-connect probe would falsely accept any listener that
-- happens to occupy the port (e.g. after the original jank crashed and
-- the OS recycled the port to an unrelated service). Doing a real eval
-- proves the listener is actually nREPL.
-- Port must be validated as numeric before calling this function.
local function nrepl_probe(port)
  local cmd = "clj-nrepl-eval -p " .. shell_quote(port)
    .. " --timeout 2000 '(+ 1 2)' 2>&1"
  local handle = io.popen(cmd)
  if not handle then return false end
  local out = handle:read("*a")
  handle:close()
  return out:match("=> 3") ~= nil
end

-- Read a single number from a file (used for PID and port files).
-- Returns the number as a string, or nil if the file is missing/corrupt.
local function read_number_file(path)
  local f = io.open(path, "r")
  if not f then return nil end
  local content = f:read("*l")
  f:close()
  return digits_or_nil(content)
end

-- Discover jank nREPL port from a running jank process.
-- Uses lsof (cross-platform) with ss as fallback (Linux).
-- Scoped to the current user via `pgrep -u $UID`: a global scan would
-- find another user's jank session on a shared host, and an unrelated
-- render could end up evaluating code in their REPL. The lifecycle
-- script's discover_port() takes the same pid-first shape.
local function discover_port_from_process()
  local lsof_cmd = [[
    for pid in $(pgrep -u "$(id -u)" -x jank 2>/dev/null); do
      port=$(lsof -a -iTCP -sTCP:LISTEN -nP -p "$pid" 2>/dev/null \
             | awk -F: '/LISTEN/ {print $NF}' | awk '{print $1}' | head -1)
      if [ -n "$port" ]; then echo "$port"; exit 0; fi
    done
  ]]
  local handle = io.popen(lsof_cmd)
  if handle then
    local port = handle:read("*l")
    handle:close()
    if digits_or_nil(port) then return port end
  end

  local ss_cmd = [[
    for pid in $(pgrep -u "$(id -u)" -x jank 2>/dev/null); do
      port=$(ss -tlnp 2>/dev/null | grep "pid=$pid" \
             | awk '{print $4}' | awk -F: '{print $NF}' | head -1)
      if [ -n "$port" ]; then echo "$port"; exit 0; fi
    done
  ]]
  handle = io.popen(ss_cmd)
  if handle then
    local port = handle:read("*l")
    handle:close()
    if digits_or_nil(port) then return port end
  end

  return nil
end

-- Resolve the path to jank-lifecycle.sh (sibling of this filter script).
local function lifecycle_script()
  local script = PANDOC_SCRIPT_FILE
  local dir = script:match("(.*[/\\])") or "./"
  return dir .. "jank-lifecycle.sh"
end

-- Inject jank.css into the document via `header-includes`. The CSS
-- contribution declared in `_extension.yml` is honored by Quarto only
-- for *format* extensions; Janqua is a filter extension, so the file
-- never reaches the rendered HTML otherwise. Reading and inlining it
-- here keeps jank.css as the single source of truth.
local function inject_header_css(meta)
  local script = PANDOC_SCRIPT_FILE
  local dir = script:match("(.*[/\\])") or "./"
  local f = io.open(dir .. "jank.css", "r")
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
    -- Other meta types (e.g. MetaInlines from a single-string include):
    -- preserve the original by promoting to a MetaList.
    meta["header-includes"] = pandoc.MetaList({
      existing,
      pandoc.MetaBlocks({style_block}),
    })
  end
  return meta
end

-- Check if a PID is alive via kill -0.
-- PID must be validated as numeric before calling this function.
local function pid_alive(pid)
  local ok = os.execute("kill -0 " .. shell_quote(pid) .. " 2>/dev/null")
  return ok == true or ok == 0
end

-- Print a multi-line block to stderr, framed by separators, so the user
-- cannot miss that something with persistent side effects just happened.
local function print_loud(lines)
  local sep = string.rep("=", 64)
  io.stderr:write(sep .. "\n")
  for _, line in ipairs(lines) do
    io.stderr:write("[janqua] " .. line .. "\n")
  end
  io.stderr:write(sep .. "\n")
end

-- Build a visible error block for the rendered document, mirroring the
-- existing cell-output-error pattern used for user-code exceptions.
-- Also writes a stderr line so build logs flag the failure even when
-- nobody opens the rendered HTML.
local function error_block(title, detail)
  io.stderr:write("[janqua] ERROR: " .. title .. "\n")
  local content = "[janqua] ERROR: " .. title
  if detail and detail ~= "" then
    content = content .. "\n\n" .. detail
  end
  return pandoc.Div(
    pandoc.CodeBlock(content, pandoc.Attr("", {"error"})),
    pandoc.Attr("", {"cell-output", "cell-output-error"})
  )
end

-- Append a cell-output-stdout block for any captured stdout, no-op otherwise.
-- Used by every kind-specific output branch so `(println ...)` is visible
-- alongside charts, diagrams, and other rendered values.
local function emit_stdout_block(blocks, stdout)
  if stdout then
    table.insert(blocks, pandoc.Div(
      pandoc.CodeBlock(stdout),
      pandoc.Attr("", {"cell-output", "cell-output-stdout"})
    ))
  end
end

-- Wrap a rendered visual output (chart, diagram, HTML, formatted markdown)
-- in `cell-output cell-output-display`, mirroring Quarto's native cell DOM
-- so themes that target `.cell-output-display` (figure spacing, dark-mode
-- panels) apply uniformly. Content can be a single Block or a list.
local function emit_display_block(blocks, content)
  table.insert(blocks, pandoc.Div(
    content,
    pandoc.Attr("", {"cell-output", "cell-output-display"})
  ))
end

-- Check whether the user has explicitly disabled auto-start.
-- Default is enabled; this returns true only when an explicit "off" signal
-- is present in frontmatter or the JANK_AUTO_START env var.
local function auto_start_disabled(meta)
  if meta and meta.jank then
    local opt = meta.jank["auto-start"]
    if opt ~= nil then
      local s = pandoc.utils.stringify(opt):lower()
      if s == "false" or s == "no" or s == "0" then
        return true
      end
    end
  end
  local env = os.getenv("JANK_AUTO_START")
  if env then
    local s = env:lower()
    if s == "false" or s == "0" or s == "no" or s == "off" then
      return true
    end
  end
  return false
end

-- Whether the user requested a clean render via frontmatter
-- `jank: reset-on-render: true`. Default is false. When true, Meta()
-- stops any running Jank session before resolving a port, so the next
-- render starts from a fresh process — the slow-but-bulletproof escape
-- hatch when stale defs from a prior render contaminate the current
-- one.
local function reset_on_render_requested(meta)
  if meta and meta.jank then
    local opt = meta.jank["reset-on-render"]
    if opt ~= nil then
      return parse_bool(pandoc.utils.stringify(opt)) == true
    end
  end
  return false
end

-- Stop any running Jank session anchored at project_root. Idempotent —
-- safe to call when nothing is running. Used by reset-on-render.
-- Stderr is suppressed because the lifecycle script's stop is chatty
-- about the no-session case, which is normal here.
local function stop_jank_session()
  local script_path = lifecycle_script()
  local cmd = "JANQUA_PROJECT_ROOT=" .. shell_quote(project_root) .. " "
    .. shell_quote(script_path) .. " stop 2>/dev/null"
  os.execute(cmd)
end

-- Start jank repl via lifecycle script and return the port.
-- On success, prints a loud block so the user knows a long-lived process
-- was spawned and how to stop it.
-- Check whether `jank` is on PATH. Only needed for auto-start: if the
-- user already has a running session (steps 1-4 of resolve_port), we
-- never invoke `jank` directly. Defined adjacent to `auto_start_jank`
-- because Lua resolves references at definition time — this helper
-- must be in scope before the caller is defined.
local function jank_available()
  local ok = os.execute("command -v jank >/dev/null 2>&1")
  return ok == true or ok == 0
end

local function auto_start_jank()
  if not jank_available() then
    print_loud({
      "ERROR: `jank` is not on PATH.",
      "Janqua tried to auto-start a Jank nREPL but couldn't find the binary.",
      "",
      "Install Jank from:",
      "  https://jank-lang.org",
      "",
      "(See the Getting Started guide for full prerequisites.)",
    })
    return nil
  end

  io.stderr:write("[janqua] No running Jank nREPL found. Starting one...\n")

  local script_path = lifecycle_script()
  local cmd = "JANQUA_PROJECT_ROOT=" .. shell_quote(project_root) .. " "
    .. shell_quote(script_path) .. " start 2>/dev/null"
  local handle = io.popen(cmd)
  local port = handle:read("*l")
  handle:close()

  if digits_or_nil(port) then
    local pid = read_number_file(project_root .. "/.jank-pid") or "(see .jank-pid)"
    print_loud({
      "Started a long-lived Jank nREPL session.",
      "  PID:  " .. pid,
      "  Port: " .. port,
      "  Log:  " .. project_root .. "/.jank-repl.log",
      "  Dir:  " .. project_root,
      "",
      "This session KEEPS RUNNING after `quarto render` exits.",
      "To stop it (run from the dir above, or any subdirectory):",
      "  " .. script_path .. " stop",
      "",
      "To disable auto-start, add to your document's frontmatter:",
      "  jank:",
      "    auto-start: false",
      "(or set env var JANK_AUTO_START=0)",
    })
    return port
  end

  print_loud({
    "ERROR: Failed to start Jank.",
    "Try starting manually:",
    "  " .. script_path .. " start",
    "Then check: .jank-repl.log",
  })
  return nil
end

-- Print instructions for starting Jank manually when auto-start is disabled.
local function print_manual_start_instructions()
  local script_path = lifecycle_script()
  print_loud({
    "No running Jank nREPL was found, and auto-start is disabled.",
    "",
    "Start Jank manually:",
    "  " .. script_path .. " start",
    "",
    "Or re-enable auto-start by removing `auto-start: false` from",
    "frontmatter (and unsetting JANK_AUTO_START).",
  })
end

-- Check whether `clj-nrepl-eval` is on PATH. Every discovery step and
-- every evaluation depends on it, so missing this binary turns into
-- silent probe failures and misleading downstream errors.
local function clj_nrepl_eval_available()
  local ok = os.execute("command -v clj-nrepl-eval >/dev/null 2>&1")
  return ok == true or ok == 0
end

-- Resolve the Jank nREPL port using the discovery chain.
-- Each candidate port is validated before use; stale ports are skipped.
local function resolve_port(meta)
  if not clj_nrepl_eval_available() then
    print_loud({
      "ERROR: `clj-nrepl-eval` is not on PATH.",
      "Janqua uses it to talk to the Jank nREPL.",
      "",
      "Install it with:",
      "  bbin install https://github.com/bhauman/clojure-mcp-light.git --tag v0.2.1 --as clj-nrepl-eval --main-opts '[\"-m\" \"clojure-mcp-light.nrepl-eval\"]'",
      "",
      "(See the Getting Started guide for full prerequisites.)",
    })
    return nil
  end

  -- 1. Explicit port in frontmatter
  if meta and meta.jank then
    local port = meta.jank.port
    if port then
      port = pandoc.utils.stringify(port)
      if digits_or_nil(port) and nrepl_probe(port) then return port end
      io.stderr:write("[jank filter] Port " .. port .. " from frontmatter did not respond as nREPL, trying next.\n")
    end
  end

  -- 2. PID file + port file (managed by lifecycle script).
  -- Always read via the absolute project_root path so the filter and
  -- the lifecycle script never disagree about file location.
  local pid = read_number_file(project_root .. "/.jank-pid")
  if pid and pid_alive(pid) then
    local port = read_number_file(project_root .. "/.jank-nrepl-port")
    if port and nrepl_probe(port) then return port end
  end

  -- 3. Environment variable
  local env_port = digits_or_nil(os.getenv("JANK_PORT"))
  if env_port then
    if nrepl_probe(env_port) then return env_port end
    io.stderr:write("[jank filter] Port " .. env_port .. " from JANK_PORT did not respond as nREPL, trying next.\n")
  end

  -- 4. Process discovery via lsof/ss
  -- NOTE: This finds any jank process, not just this project's. If multiple
  -- projects run jank simultaneously, this could connect to the wrong session.
  -- Steps 1-3 above are project-specific and preferred.
  local port = discover_port_from_process()
  if port then return port end

  -- 5. Auto-start via lifecycle script (unless explicitly disabled)
  if auto_start_disabled(meta) then
    print_manual_start_instructions()
    return nil
  end
  return auto_start_jank()
end

-- Unquote a Clojure string value: strip outer quotes and unescape.
local function unquote_clj_string(s)
  if not s then return nil end
  -- Strip surrounding quotes
  s = s:match('^"(.*)"$') or s
  -- Unescape Clojure string escapes
  s = s:gsub('\\\\', '\0BACKSLASH\0')  -- protect escaped backslashes
  s = s:gsub('\\"', '"')
  s = s:gsub('\\n', '\n')
  s = s:gsub('\\t', '\t')
  s = s:gsub('\\r', '\r')
  s = s:gsub('\0BACKSLASH\0', '\\')    -- restore backslashes
  return s
end


-- Jank code to bootstrap janqua helpers (sent on first connection).
-- Defines both helpers in a dedicated `janqua.runtime` namespace so they
-- never collide with the user's code.
--
-- We use `create-ns` + `intern` rather than `(ns janqua.runtime) (defn ...)`
-- inside a do-block: the `defn` macro resolves the target namespace at
-- compile time, and the compiler walks the entire do-form before any of it
-- runs, so a runtime `in-ns` mid-form doesn't redirect subsequent defns.
-- `intern` interns into the named ns regardless of the current *ns*.
--
-- Self-references inside the functions use the local fn name set by
-- `(fn hiccup->html ...)`, so the body doesn't need the fully-qualified var.
--
-- NOTE: hiccup->html does not escape HTML special characters in attribute
-- values or text content. This is acceptable for a document authoring tool
-- where the user controls the code, but means hiccup with untrusted data
-- could produce malformed HTML.
local janqua_bootstrap = [=[
(do
  (create-ns 'janqua.runtime)
  (intern 'janqua.runtime 'hiccup->html
    (fn hiccup->html [form]
      (cond
        (string? form) form
        (number? form) (str form)
        (vector? form)
        (let [tag (name (first form))
              has-attrs (map? (second form))
              attrs (if has-attrs (second form) {})
              children (if has-attrs (drop 2 form) (rest form))
              attr-str (apply str
                         (map (fn [[k v]]
                                (str " " (name k) "=\"" v "\""))
                              attrs))]
          (str "<" tag attr-str ">"
               (apply str (map hiccup->html children))
               "</" tag ">"))
        :else (str form))))
  (let [json-escape
        (fn [s]
          (clojure.string/escape s
            {\\ "\\\\"
             \" "\\\""
             \< "\\u003c"  ; keep </script> in user data from breaking inline JS
             \newline "\\n"
             \return "\\r"
             \tab "\\t"}))]
    (intern 'janqua.runtime 'to-json
      (fn to-json [v]
        (cond
          (nil? v) "null"
          (true? v) "true"
          (false? v) "false"
          (string? v) (str "\"" (json-escape v) "\"")
          (keyword? v) (str "\"" (json-escape (name v)) "\"")
          (number? v) (str v)
          (vector? v) (str "[" (clojure.string/join ", " (map to-json v)) "]")
          (seq? v) (str "[" (clojure.string/join ", " (map to-json v)) "]")
          (map? v) (str "{"
                     (clojure.string/join ", "
                       (map (fn [[k val]]
                              (let [k-str (cond
                                            (string? k) k
                                            (keyword? k) (name k)
                                            :else (str k))]
                                (str "\"" (json-escape k-str) "\": " (to-json val))))
                            v))
                     "}")
          :else (str "\"" (json-escape (str v)) "\"")))))
  :janqua.runtime/bootstrapped)
]=]

local janqua_bootstrapped = false
local janqua_div_counter = 0

-- Generate a unique div ID for JS-rendered outputs.
local function next_div_id()
  janqua_div_counter = janqua_div_counter + 1
  return "janqua-plot-" .. janqua_div_counter
end

-- Encode a Lua string as a JSON/JS string literal (with quotes).
local function js_string_encode(s)
  s = s:gsub('\\', '\\\\')
  s = s:gsub('"', '\\"')
  s = s:gsub('\n', '\\n')
  s = s:gsub('\r', '\\r')
  s = s:gsub('\t', '\\t')
  return '"' .. s .. '"'
end

-- Pinned CDN scripts with SRI integrity hashes. Loose version tags (e.g.
-- `vega@5`) would silently follow upstream updates and provide no integrity
-- check, so a CDN compromise or a malicious patch could be served to every
-- viewer of every rendered doc. Pinning the exact version + SRI hash means
-- the browser refuses to execute if the served bytes don't match.
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

-- Tracks which CDN libraries have already had a <script src> emitted in
-- this render, so multiple charts of the same kind don't repeat the load
-- tag. Browsers dedupe by URL anyway, but emitting once produces cleaner
-- HTML and avoids redundant integrity-check work.
local cdn_emitted = {}

-- Build a <script> tag with SRI integrity for a CDN entry.
-- First call for a given name emits the tag; subsequent calls return "".
-- Inline init code (e.g. `Plotly.newPlot(...)`) keeps working because the
-- library is already loaded as a global by the time the second block's
-- inline script runs.
local function script_tag(name)
  if cdn_emitted[name] then
    return ""
  end
  cdn_emitted[name] = true
  local s = CDN[name]
  return '<script src="' .. s.src
    .. '" integrity="' .. s.integrity
    .. '" crossorigin="anonymous"></script>'
end

-- Build an inline CSS style attribute for a chart wrapper div.
-- For ECharts/Cytoscape (libraries that won't render to a size-less div)
-- callers pass default_w/default_h ("600"/"400") so size is always present.
-- Other chart kinds pass nil defaults so the wrapper stays bare unless the
-- user explicitly sets width and/or height. Inputs are pre-validated as
-- digit strings; the function returns "" or ` style="..."` ready to splice.
local function dim_style(width, height, default_w, default_h)
  local w = width or default_w
  local h = height or default_h
  if not w and not h then return "" end
  local parts = {}
  if w then table.insert(parts, "width:" .. w .. "px") end
  if h then table.insert(parts, "height:" .. h .. "px") end
  return ' style="' .. table.concat(parts, ";") .. ';"'
end

-- Send raw code to Jank via clj-nrepl-eval.
-- Returns: raw output string, error string (may be nil)
-- `timeout` is in milliseconds; nil falls back to 10000.
local function eval_jank_raw(code, timeout)
  if not jank_port then
    return nil, "Jank nREPL port not available. Could not discover or start Jank."
  end

  local timeout_ms = timeout or "10000"
  local cmd = "clj-nrepl-eval -p " .. shell_quote(jank_port) .. " --timeout " .. shell_quote(tostring(timeout_ms)) .. " " .. shell_quote(code) .. " 2>&1"
  local handle = io.popen(cmd)
  local raw = handle:read("*a")
  handle:close()

  if raw:match("clj%-nrepl%-eval: command not found")
     or raw:match("clj%-nrepl%-eval: not found")
     or raw:match("No such file or directory.*clj%-nrepl%-eval") then
    return nil, "`clj-nrepl-eval` is not on PATH. Install it with `bbin install https://github.com/bhauman/clojure-mcp-light.git --tag v0.2.1 --as clj-nrepl-eval --main-opts '[\"-m\" \"clojure-mcp-light.nrepl-eval\"]'` (see the Getting Started guide for full prerequisites)."
  end

  if raw:match("ConnectException") or raw:match("Connection refused") then
    return nil, "Cannot connect to Jank nREPL on port " .. jank_port .. ". Is `jank repl` running?"
  end

  return raw, nil
end

-- Parse clj-nrepl-eval output into value + stdout.
local function parse_nrepl_output(raw)
  local lines = {}
  for line in raw:gmatch("[^\n]+") do
    table.insert(lines, line)
  end

  local stdout_lines = {}
  local value = nil

  for _, line in ipairs(lines) do
    if line:match("^=> ") then
      value = line:sub(4)
    elseif line:match("^%*========") then
      -- skip footer
    elseif value == nil then
      table.insert(stdout_lines, line)
    end
  end

  local stdout = nil
  if #stdout_lines > 0 then
    stdout = table.concat(stdout_lines, "\n")
  end

  return value, stdout
end
-- Convert a Clojure value to JSON via janqua.runtime/to-json in the Jank session.
-- Returns (json_string, nil) on success, (nil, err_message) on failure.
--
-- to-json always returns a string, which the nREPL pretty-prints as a
-- quoted Clojure literal (e.g. `"{\"a\": 1}"`). When eval errors instead,
-- the error text comes back unquoted (e.g. `Unable to resolve symbol ...`).
-- We use the leading `"` to distinguish; otherwise an error message would
-- silently get spliced into the rendered chart's JavaScript.
local function clj_to_json(clj_value)
  local raw, err = eval_jank_raw("(janqua.runtime/to-json " .. clj_value .. ")")
  if err then return nil, err end
  local json_value = parse_nrepl_output(raw)
  if not json_value then
    return nil, "to-json returned no value"
  end
  if not json_value:match('^"') then
    return nil, "to-json eval failed: " .. json_value
  end
  return unquote_clj_string(json_value), nil
end


-- Kindly kind → output_mode dispatch. `:kind/hiccup` is special-cased in
-- CodeBlock because it needs a second eval to convert hiccup → HTML before
-- the html branch can render it; the rest are pure renames.
local KIND_TO_MODE = {
  [":kind/html"] = "html",
  [":kind/md"] = "markdown",
  [":kind/hidden"] = "hidden",
  [":kind/mermaid"] = "mermaid",
  [":kind/graphviz"] = "graphviz",
  [":kind/tex"] = "tex",
  [":kind/code"] = "code-display",
  [":kind/vega-lite"] = "vega-lite",
  [":kind/plotly"] = "plotly",
  [":kind/echarts"] = "echarts",
  [":kind/cytoscape"] = "cytoscape",
  [":kind/highcharts"] = "highcharts",
}

-- JS-rendered chart kinds: each entry pairs a list of CDN libraries with
-- the inline init script that hands the JSON spec to the library. ECharts
-- and Cytoscape ship default sizes because their libraries refuse to
-- render to a size-less div; the others auto-fit.
local CHART_KINDS = {
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

-- Render a Kindly chart value as a wrapper div + library script tag(s) +
-- inline init script. Any clj_to_json failure surfaces as a visible
-- error block (the chart-kind name lets the reader spot which block).
local function emit_chart(blocks, output_mode, value, width, height)
  local cfg = CHART_KINDS[output_mode]
  local clj_src = unquote_clj_string(value)
  local json, json_err = clj_to_json(clj_src)
  if not json then
    table.insert(blocks, error_block(
      ":kind/" .. output_mode .. " — " .. (json_err or "unknown error"),
      clj_src))
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


-- Bootstrap janqua helpers in the Jank session (once).
-- If no port is available, skip silently — the loud block from resolve_port
-- already informed the user; per-block warnings would just be noise.
-- On bootstrap failure, print a loud block: plain code blocks may still
-- evaluate, but Kindly chart kinds and hiccup conversion will not work.
local function ensure_bootstrap()
  if janqua_bootstrapped then return end
  if not jank_port then return end
  local raw, err = eval_jank_raw(janqua_bootstrap)
  if err then
    print_loud({
      "ERROR: Failed to bootstrap janqua.runtime helpers.",
      "Detail: " .. err,
      "",
      "Plain code blocks may still evaluate, but Kindly chart kinds",
      "(vega-lite, plotly, ...) and hiccup conversion will fail.",
    })
  end
  janqua_bootstrapped = true
end

-- Wrap user code to extract Kindly metadata from the result.
-- Uses fn call instead of let to work around a Jank bug where let bindings
-- lose reader-attached metadata (e.g. ^:kind/hiccup).
-- :width and :height come from the value's :kindly/options map (layout
-- concerns belong in Kindly's vocabulary, so library functions can set
-- defaults via with-meta). Other Janqua knobs (timeout, hide-stdout) are
-- not picked up from :kindly/options — they're Janqua-specific and live
-- on the fence attribute or in frontmatter.
-- Type-check before coercion so a malformed option (e.g. :width "800px")
-- doesn't throw and lose the user's actual return value.
local function wrap_with_kindly(code)
  return '((fn [v__janqua]'
    .. ' ((fn [m__janqua]'
    .. ' ((fn [kind__janqua opts__janqua]'
    .. ' {:janqua/kind kind__janqua'
    .. ' :janqua/width (when (integer? (:width opts__janqua))'
    .. '                 (:width opts__janqua))'
    .. ' :janqua/height (when (integer? (:height opts__janqua))'
    .. '                  (:height opts__janqua))'
    .. ' :janqua/value (pr-str'
    .. ' (if (or (get-in m__janqua [:kindly/options :wrapped-value])'
    .. '         (and (#{:kind/html :kind/md :kind/mermaid :kind/graphviz :kind/tex :kind/code} kind__janqua)'
    .. '              (vector? v__janqua)))'
    .. '   (first v__janqua) v__janqua))})'
    .. ' (when m__janqua'
    .. ' (or (:kindly/kind m__janqua)'
    .. ' (some (fn [k] (when (and (keyword? k) (= (namespace k) "kind") (get m__janqua k)) k))'
    .. ' (keys m__janqua))))'
    .. ' (or (:kindly/options m__janqua) {})))'
    .. ' (meta v__janqua)))'
    .. ' (do ' .. code .. '))'
end

-- Parse the wrapper response to extract kind, value, and options.
-- Options keys appear before :janqua/value in the wrapper map, so the
-- existing greedy `:janqua/value (.+)}%s*$` value match still anchors
-- correctly on the trailing `}` of the outer map.
local function parse_kindly_response(value_str)
  if not value_str then return nil, nil, nil end

  local kind = value_str:match(":janqua/kind (:kind/[%w_-]+)")

  local opts = {}
  opts.width = value_str:match(":janqua/width (%d+)")
  opts.height = value_str:match(":janqua/height (%d+)")

  local raw_value = value_str:match(":janqua/value (.+)}%s*$")

  return kind, raw_value, opts
end

-- Evaluate Jank code via clj-nrepl-eval with Kindly wrapper.
-- Returns: value, stdout, err, kind, opts (all may be nil except opts which
-- is always a table). `opts` carries :width and :height extracted from the
-- value's :kindly/options metadata.
-- `timeout` overrides the per-block eval timeout (ms).
local function eval_jank(code, timeout)
  ensure_bootstrap()

  local wrapped = wrap_with_kindly(code)
  local raw, err = eval_jank_raw(wrapped, timeout)
  if err then return nil, nil, err, nil, {} end

  local value, stdout = parse_nrepl_output(raw)

  -- nREPL returned no `=> value` line. Surface as an error rather than
  -- silently emitting an empty block (which used to swallow the source
  -- block entirely).
  if not value then
    return nil, stdout,
      "Jank returned no value. Raw response: " .. (raw or "(empty)"),
      nil, {}
  end

  -- If the wrapper map wasn't returned, the code likely threw an error.
  -- The raw value IS the error message in that case.
  if not value:match(":janqua/kind") then
    return nil, stdout, value, nil, {}
  end

  local kind, actual_value, opts = parse_kindly_response(value)

  -- Kind matched but value regex missed: surface as an error so we don't
  -- silently render an empty block.
  if kind and not actual_value then
    return nil, stdout,
      "Could not parse Kindly wrapper response (kind " .. kind ..
      "). Raw response: " .. value,
      kind, opts
  end

  -- Unquote the pr-str serialization layer from the wrapper
  actual_value = unquote_clj_string(actual_value)

  return actual_value, stdout, nil, kind, opts
end

-- Whether the current Pandoc target is HTML. Janqua's chart/diagram
-- branches all emit `RawBlock("html", ...)` and Pandoc drops those in
-- LaTeX/docx/gfm — so on non-HTML targets the right behavior is to
-- skip evaluation entirely (no jank session spun up, no bare HTML in
-- the AST) and pass `.jank` blocks through as regular code.
-- Defaults to true when `quarto.doc.is_format` is unavailable so the
-- filter still works under bare-pandoc invocation.
local function html_target()
  if quarto and quarto.doc and quarto.doc.is_format then
    return quarto.doc.is_format("html") or false
  end
  return true
end

-- Extract the Jank port from document metadata.
-- resolve_port already prints a loud block when it fails, so don't add
-- another redundant warning here.
function Meta(meta)
  if not html_target() then return end
  project_root = resolve_project_root()
  if not project_root or project_root == "" or project_root == "/" then
    print_loud({
      "ERROR: Could not resolve a safe directory for Janqua state files.",
      "Refusing to operate (resolved root: '" .. tostring(project_root) .. "').",
    })
    return
  end

  -- Doc-wide defaults from frontmatter `jank:` map.
  if meta and meta.jank then
    local t = meta.jank.timeout
    if t then
      default_timeout = digits_or_nil(pandoc.utils.stringify(t))
    end
    local h = meta.jank["hide-stdout"]
    if h ~= nil then
      default_hide_stdout = parse_bool(pandoc.utils.stringify(h))
    end
  end

  if reset_on_render_requested(meta) then
    io.stderr:write("[janqua] reset-on-render: stopping any running Jank session for a clean start.\n")
    stop_jank_session()
  end

  jank_port = resolve_port(meta)
  return inject_header_css(meta)
end

-- Process Jank code blocks.
function CodeBlock(el)
  if not el.classes:includes("jank") then
    return nil
  end
  -- Non-HTML target: leave `.jank` blocks alone so Pandoc renders them
  -- as plain syntax-highlighted code in PDF/docx/gfm. The user keeps the
  -- source visible and we don't poison the AST with stray HTML.
  if not html_target() then
    return nil
  end

  local code = el.text
  local output_mode = el.attributes["output"] or "code"
  local echo = el.attributes["echo"] ~= "false"
  local eval = el.attributes["eval"] ~= "false"

  -- Per-block options. timeout and hide-stdout are Janqua-specific knobs
  -- (fence > frontmatter > default). width/height also accept :kindly/options
  -- on the value, so the fence reads happen now but final resolution waits
  -- until after eval (precedence: fence > :kindly/options > built-in default).
  local timeout = digits_or_nil(el.attributes["timeout"]) or default_timeout
  local hide_stdout = parse_bool(el.attributes["hide-stdout"])
  if hide_stdout == nil then hide_stdout = default_hide_stdout end
  if hide_stdout == nil then hide_stdout = false end
  local fence_width = digits_or_nil(el.attributes["width"])
  local fence_height = digits_or_nil(el.attributes["height"])

  -- output=hidden implies both echo=false and suppressed output
  if output_mode == "hidden" then
    echo = false
  end

  local blocks = {}

  -- Evaluate first so Kindly metadata can influence echo/output_mode
  local value, stdout, err, kind, opts = nil, nil, nil, nil, {}
  if eval then
    value, stdout, err, kind, opts = eval_jank(code, timeout)

    -- Kindly metadata overrides the output= attribute. `eval_jank` may
    -- return `kind` together with a non-nil `err` when the wrapper-value
    -- regex misses; in that case `value` is nil and the hiccup branch
    -- below would crash on string concat (and the unsupported-kind
    -- branch would overwrite the real error). The eval-error block
    -- downstream handles it.
    if kind and not err then
      if kind == ":kind/hiccup" then
        -- Convert hiccup to HTML via a second eval.
        -- hiccup->html returns a string, which the nREPL prints quoted.
        -- An unquoted result means the eval errored — surface as an error
        -- block instead of leaking the error text as raw HTML.
        local hiccup_src = unquote_clj_string(value)
        local html_raw, html_err = eval_jank_raw("(janqua.runtime/hiccup->html " .. hiccup_src .. ")")
        if html_err then
          table.insert(blocks, error_block(
            "hiccup->HTML conversion failed: " .. html_err,
            hiccup_src))
          value = nil
        else
          local html_value = parse_nrepl_output(html_raw)
          if not html_value then
            table.insert(blocks, error_block(
              "hiccup->HTML returned no value",
              hiccup_src))
            value = nil
          elseif not html_value:match('^"') then
            table.insert(blocks, error_block(
              "hiccup->HTML eval failed: " .. html_value,
              hiccup_src))
            value = nil
          else
            value = html_value
          end
        end
        output_mode = "html"
      elseif KIND_TO_MODE[kind] then
        output_mode = KIND_TO_MODE[kind]
      else
        -- Recognized as a Kindly kind but no dispatch branch handles it.
        -- Without this, an unsupported kind looks identical to a typo:
        -- both fall through to default code rendering. Surface as an
        -- error so users can tell the two apart.
        err = "Kindly kind " .. kind .. " is not supported by Janqua. Value: " .. (value or "nil")
      end
    end
  end

  -- Apply hide_stdout (already resolved pre-eval) to the captured stdout
  -- up front, so every render branch picks it up without per-branch checks.
  if hide_stdout then stdout = nil end

  -- width/height: nil means "no override"; chart branches choose whether
  -- to apply a built-in default (ECharts/Cytoscape) or stay bare (others).
  local width = fence_width or opts.width
  local height = fence_height or opts.height

  if echo then
    table.insert(blocks, pandoc.CodeBlock(code, pandoc.Attr("", {"clojure"})))
  end

  if eval then
    if err then
      -- When echo=false, the source isn't shown above the error and the
      -- user has no immediate way to tell *which* block failed in a long
      -- doc. Knitr/Jupyter honor echo=false even on errors (we follow
      -- the same default), but tucking the source into a collapsed
      -- <details> inside the error block adds a one-click recovery
      -- path without overriding the user's stated preference.
      local err_children = {pandoc.CodeBlock(err, pandoc.Attr("", {"error"}))}
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
    elseif output_mode ~= "hidden" then
      emit_stdout_block(blocks, stdout)
      if value then
        if output_mode == "html" then
          emit_display_block(blocks, pandoc.RawBlock("html", unquote_clj_string(value)))
        elseif output_mode == "markdown" then
          local doc = pandoc.read(unquote_clj_string(value), "markdown")
          emit_display_block(blocks, doc.blocks)
        elseif output_mode == "mermaid" then
          -- Use the UMD build (loaded via <script src>) instead of the ESM
          -- module import: SRI on a <script> tag is broadly supported, but
          -- import-map integrity for ESM imports is not.
          local diagram = unquote_clj_string(value)
          local div_id = next_div_id()
          -- The .catch surfaces Mermaid syntax errors in the page itself.
          -- Without it, a malformed diagram leaves a blank div with the
          -- error only in the browser console — breaks the "errors are
          -- visible" contract that filter-side failures already follow
          -- via `cell-output-error` blocks.
          local html = '<div id="' .. div_id .. '"' .. dim_style(width, height) .. '></div>'
            .. script_tag("mermaid")
            .. '<script>'
            .. 'mermaid.initialize({ startOnLoad: false });'
            .. 'mermaid.render("' .. div_id .. '-svg", '
            .. js_string_encode(diagram) .. ').then(({svg}) => {'
            .. 'document.getElementById("' .. div_id .. '").innerHTML = svg;'
            .. '}).catch(e => {'
            .. 'document.getElementById("' .. div_id .. '").innerText = '
            .. '"Mermaid error: " + (e && e.message ? e.message : e);'
            .. '});'
            .. '</script>'
          emit_display_block(blocks, pandoc.RawBlock("html", html))
        elseif output_mode == "graphviz" then
          -- Render in the browser via @viz-js/viz (a WASM build of
          -- Graphviz). Avoids requiring `dot` to be installed on the
          -- build machine, at the cost of HTML-only output. Errors
          -- from the library or the DOT source surface in the page
          -- itself, mirroring the mermaid pattern.
          local dot_src = unquote_clj_string(value)
          local div_id = next_div_id()
          local html = '<div id="' .. div_id .. '"' .. dim_style(width, height) .. '></div>'
            .. script_tag("graphviz")
            .. '<script>'
            .. 'Viz.instance().then(function (viz) {'
            .. 'try {'
            .. 'document.getElementById("' .. div_id .. '").appendChild('
            .. 'viz.renderSVGElement(' .. js_string_encode(dot_src) .. '));'
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
        elseif output_mode == "tex" then
          local md = "$$" .. unquote_clj_string(value) .. "$$"
          local doc = pandoc.read(md, "markdown")
          emit_display_block(blocks, doc.blocks)
        elseif output_mode == "code-display" then
          emit_display_block(blocks, pandoc.CodeBlock(unquote_clj_string(value), pandoc.Attr("", {"clojure"})))
        elseif CHART_KINDS[output_mode] then
          emit_chart(blocks, output_mode, value, width, height)
        else
          -- Default: code output. Stdout and value go in separate divs so a
          -- reader can tell debug `(println …)` from the actual return value,
          -- and so theme rules scoped to `.cell-output-stdout` don't apply
          -- to return values. Mirrors the `:kind/code` branch.
          emit_display_block(blocks, pandoc.CodeBlock(value, pandoc.Attr("", {"clojure"})))
        end
      end
    end
  end

  -- Wrap the (echo, cell-output-*) pair in Quarto's outer `cell` Div so
  -- theme rules scoped to `.cell` (spacing, dark-mode panels, collapse
  -- chrome) apply to Janqua blocks the same way they apply to native
  -- executable cells. Empty list returns as-is so we don't leave an
  -- empty `<div class="cell"></div>` in the rendered doc.
  if #blocks > 0 then
    return { pandoc.Div(blocks, pandoc.Attr("", {"cell"})) }
  end
  return blocks
end

-- Ensure Meta runs before CodeBlock.
return {
  { Meta = Meta },
  { CodeBlock = CodeBlock }
}
