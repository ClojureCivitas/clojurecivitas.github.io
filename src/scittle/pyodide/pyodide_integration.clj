^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Python + ClojureScript: Pyodide Integration with Scittle"
         :quarto {:author [:burinc]
                  :description "Run Python code directly in the browser using Pyodide with Scittle and ClojureScript"
                  :type :post
                  :date "2025-11-05"
                  :category :libs
                  :tags [:scittle
                         :clojurescript
                         :python
                         :pyodide
                         :data-science
                         :no-build]
                  :keywords [:pyodide
                             :python
                             :scittle
                             :browser
                             :data-science
                             :matplotlib
                             :pandas]}}}

(ns scittle.pyodide.pyodide-integration
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Python + ClojureScript: Pyodide Integration with Scittle

;; ## About This Project

;; This is the **second installment** in my exploration of browser-native development with Scittle. In my first article, [Building Browser-Native Presentations with Scittle](https://clojurecivitas.github.io/scittle/presentations/browser_native_slides.html), I shared how I've been using Scittle to create interactive presentations for local meetups, spreading the love of Clojure and ClojureScript by showing how accessible it can be without build tools. That project came from a desire to make ClojureScript more approachable and, honestly, more fun.

;; After presenting those interactive ClojureScript demos to the community, I kept thinking: "How do I reach Python developers? How do I show them what ClojureScript brings to the table?" The Python community has incredible data science tools like Pandas and Matplotlib, but they're typically tied to server-side execution. I wanted to bridge these two worlds in a way that would be both impressive and fun - showing Python developers that ClojureScript isn't just another JavaScript framework, while giving Clojure developers a way to leverage Python's rich ecosystem.

;; This project is my answer - and my way of keeping the joy of programming alive. By combining Pyodide (Python compiled to WebAssembly) with Scittle's zero-build approach, I can demonstrate something pretty cool: you can write Python for data science, wrap it in elegant ClojureScript UIs, and deploy everything as a single HTML file. No Python backend, no Node.js build pipeline, no complexity. Just fun, interactive data science in the browser.

;; Whether you're a Python developer curious about ClojureScript, a Clojure developer wanting to leverage Python libraries, or someone like me who just loves exploring what's possible when you remove unnecessary complexity, I hope these examples inspire you. The source code is fully available for you to learn from, adapt, and build upon. Let's keep programming fun and accessible.

;; Now let's explore how ClojureScript and Python can work together, beautifully.

;; ## The Vision

;; Imagine being able to:
;; - Write Python data science code in your browser
;; - Visualize data with matplotlib without any backend
;; - Process data with pandas entirely client-side
;; - All while orchestrating everything with ClojureScript
;; - **Without any build tools or server**

;; This isn't science fiction. It's **Pyodide + Scittle**.

;; ## What is Pyodide?

;; [Pyodide](https://pyodide.org/) is a Python distribution compiled to WebAssembly that runs entirely in the browser. It includes:
;; - Full Python 3.11 interpreter
;; - NumPy, Pandas, Matplotlib, SciPy
;; - 100+ scientific packages
;; - Full file system (in-memory)
;; - ~8MB download (compressed)

;; **Think of it as Python's REPL, but in your browser.**

;; ## Simple Example: Hello Python

;; Let's start with the simplest possible example - loading Pyodide and running Python code.

;; The demo below shows a basic Python REPL where you can:
;; - Load Pyodide from CDN
;; - Execute Python expressions
;; - See results instantly
;; - Handle errors gracefully

;; ### Try It Live

(kind/hiccup
 [:div#hello-python-demo {:style {:min-height "400px"}}
  ;; Load Pyodide CDN first - this provides js/loadPyodide
  [:script {:src "https://cdn.jsdelivr.net/pyodide/v0.28.3/full/pyodide.js"}]
  ;; Then load the shared Pyodide bridge
  [:script {:type "application/x-scittle"
            :src "pyodide_bridge.cljs"}]
  ;; Finally load the demo
  [:script {:type "application/x-scittle"
            :src "hello_python.cljs"}]])

;; ## How It Works

;; The integration uses a **shared bridge** pattern:

;; 1. **pyodide_bridge.cljs** - Manages the Pyodide instance and state
;; 2. **hello_python.cljs** - Uses the bridge to run Python code
;; 3. The bridge is loaded first, then the demo can use its functions

;; This prevents multiple Pyodide instances from conflicting!

;; ## Interactive Code Editor

;; Now let's build something more powerful - an interactive code editor where you can write and run your own Python code!

;; ### Key Features

;; - Live code editing with syntax highlighting
;; - Run button to execute code
;; - Output display for results and errors
;; - Pre-filled with example code

;; ### Try It Live

(kind/hiccup
 [:div#code-editor-demo {:style {:min-height "500px"}}
  ;; Load the code editor (Pyodide is already loaded from first demo)
  [:script {:type "application/x-scittle"
            :src "code_editor.cljs"}]])

;; ## Data Visualization with Matplotlib

;; Now for the exciting part - creating beautiful data visualizations with Python's matplotlib library!

;; The demo below captures matplotlib plots as base64 PNG images and displays them inline. This technique:
;; - Uses matplotlib's non-interactive 'Agg' backend
;; - Captures plots as PNG data
;; - Displays high-quality visualizations
;; - Includes pre-loaded examples (line, bar, scatter, pie charts)

;; ### Try It Live

(kind/hiccup
 [:div#data-visualization-demo {:style {:min-height "700px"}}
  ;; Load the data visualization demo (uses already-loaded Pyodide)
  [:script {:type "application/x-scittle"
            :src "data_visualization.cljs"}]])

;; ## DataFrame Analysis with Pandas

;; Beyond visualization, Pyodide includes the full **Pandas** library for powerful data analysis and manipulation!

;; ### Why Pandas in the Browser?

;; Pandas is the standard tool for data analysis in Python. With Pyodide, you can:
;; - Load and manipulate tabular data
;; - Perform statistical analysis
;; - Filter, group, and transform datasets
;; - All without a backend server!

;; The demo includes examples of:
;; - **Creating DataFrames** - Build tables from Python dictionaries
;; - **Statistical Analysis** - Summary statistics and correlations
;; - **Data Transformations** - Filter, calculate, sort, and rank
;; - **GroupBy Operations** - Aggregate data by categories

;; ### Try It Live

(kind/hiccup
 [:div#pandas-demo {:style {:min-height "700px"}}
  ;; Load the pandas demo (uses already-loaded Pyodide)
  [:script {:type "application/x-scittle"
            :src "pandas_demo.cljs"}]])

;; ## Complete Data Science Workflows

;; Now for the **most powerful** demonstration - combining Pandas and Matplotlib for complete end-to-end data science workflows!

;; ### Why Combine Them?

;; In real-world data science, you rarely use just one tool. You:
;; 1. **Load and clean data** with Pandas
;; 2. **Analyze and transform** to extract insights
;; 3. **Visualize results** with Matplotlib
;; 4. **Present findings** with tables and charts together

;; ### Complete Workflow Examples

;; **Sales Trend Analysis:**
;; - Load monthly sales data into DataFrame
;; - Calculate profit and margins
;; - Generate statistical summary
;; - Create trend lines and bar charts
;; - Display both tables and visualizations

;; **Product Performance:**
;; - Analyze product sales across quarters
;; - Aggregate by product category
;; - Compare units sold and revenue
;; - Show horizontal and vertical bar charts

;; **Regional Breakdown:**
;; - Multi-dimensional analysis by region and quarter
;; - Create pivot tables
;; - Generate 4 different chart types
;; - Display comprehensive dashboard

;; **Time Series Analysis:**
;; - 90 days of daily sales data
;; - Calculate 7-day and 30-day moving averages
;; - Aggregate to monthly totals
;; - Visualize trends and patterns

;; ### Key Features

;; - **Side-by-side results** - See both tables and charts
;; - **Multiple visualizations** - Up to 4 charts per workflow
;; - **Real analysis** - Statistical summaries, aggregations, transformations
;; - **Complete code** - Fully editable Python workflows

;; ### Try It Live

(kind/hiccup
 [:div#pandas-viz-demo {:style {:min-height "800px"}}
  ;; Load the combined pandas + matplotlib workflow demo
  [:script {:type "application/x-scittle"
            :src "pandas_viz_demo.cljs"}]])

;; ## What's Possible?

;; With Pyodide + Scittle, you can:

;; - 📊 **Data Visualization** - Create interactive charts and plots with matplotlib
;; - 🐍 **Python REPL** - Run any Python code in the browser
;; - 🧮 **Scientific Computing** - Use NumPy, SciPy for calculations
;; - 🐼 **Data Analysis** - Process and analyze data with Pandas DataFrames
;; - 📈 **Statistical Analysis** - Descriptive statistics, correlations, grouping
;; - 🎯 **Complete Workflows** - Combine pandas analysis with matplotlib visualizations
;; - 🎨 **Custom UIs** - Wrap Python functionality in beautiful ClojureScript UIs

;; ## Next Steps

;; Explore more advanced features:
;; - Advanced pandas operations (joins, pivots, time series)
;; - Interactive data exploration with dynamic filtering
;; - Machine learning with scikit-learn
;; - Custom Python packages and modules

;; ---

;; ## Acknowledgments

;; A special thanks to [Timothy Pratley](https://github.com/timothypratley) for creating ClojureCivitas - a welcoming space where the Clojure community can come together to share knowledge, experiences, and our collective love for the Clojure/ClojureScript ecosystem. Projects like this thrive when we have platforms to showcase what's possible and inspire others to explore.

;; ---

;; **Questions?** Find me on Clojurians Slack (@agilecreativity)
;;
;; *Built with Scittle, Pyodide, Reagent, and ❤️*
