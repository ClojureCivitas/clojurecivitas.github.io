^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Free Weather Data with National Weather Service API"
         :quarto {:author [:burinc]
                  :description "Learn to build weather applications using the free NWS API with Scittle and ClojureScript - no API key required!"
                  :type :post
                  :date "2025-11-07"
                  :category :libs
                  :image "weather.png"
                  :tags [:scittle
                         :clojurescript
                         :reagent
                         :weather
                         :api
                         :nws
                         :no-build]
                  :keywords [:weather
                             :nws
                             :api
                             :free
                             :scittle
                             :browser-native]}}}

(ns scittle.weather.weather-nws-integration
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Free Weather Data with National Weather Service API

;; ## About This Project

;; This is part of my ongoing exploration of **browser-native development with Scittle**. In my previous articles on [Building Browser-Native Presentations](https://clojurecivitas.github.io/scittle/presentations/browser_native_slides.html) and [Python + ClojureScript Integration](https://clojurecivitas.github.io/scittle/pyodide/pyodide_integration.html), I've been sharing how to create interactive, educational content without build tools.

;; Today, I want to show you something practical: **building weather applications using the National Weather Service API**. What makes this special? The NWS API is:

;; - **Completely free** - No API keys, no registration, no rate limits
;; - **Official and accurate** - Data directly from the U.S. government
;; - **Comprehensive** - Current conditions, forecasts, alerts, and more
;; - **Well-documented** - Clear endpoints and data structures

;; But here's the real magic: we'll build everything with **Scittle** - meaning zero build tools, zero configuration, just ClojureScript running directly in your browser. And as a bonus, all our functions will use **keyword arguments** for clarity and ease of use.

;; Whether you're learning ClojureScript, exploring APIs, or building weather apps, this guide will show you how to do it the simple way. Let's dive in!

;; ## Why the National Weather Service API?

;; Before we dive into code, let's understand why the NWS API is such a great choice:

;; ### Free and Accessible

;; Most weather APIs require:
;;
;; - Signing up for an account
;; - Managing API keys
;; - Dealing with rate limits
;; - Paying for more requests

;; The NWS API requires **none of that**. Just make HTTP requests and get data. Perfect for learning, prototyping, or building personal projects.

;; ### Official Government Data

;; The data comes directly from NOAA (National Oceanic and Atmospheric Administration), the same source that powers weather forecasts across the United States. You're getting:

;; - Real-time observations from weather stations
;; - Professional meteorological forecasts
;; - Severe weather alerts and warnings
;; - Historical weather data

;; ### Rich Feature Set

;; The API provides:

;; - **Points API** - Convert coordinates to forecast zones
;; - **Forecast API** - 7-day forecasts with detailed periods
;; - **Hourly Forecasts** - Hour-by-hour predictions
;; - **Current Observations** - Real-time weather station data
;; - **Weather Alerts** - Watches, warnings, and advisories
;; - **Grid Data** - Raw forecast model output

;; ### Educational Value

;; The API's design teaches important concepts:

;; - RESTful API architecture
;; - Coordinate-based services
;; - Asynchronous data fetching
;; - Error handling in real-world scenarios

;; ## Understanding the API Architecture

;; The NWS API uses a **two-step process** to get weather data:

^:kindly/hide-code
(kind/mermaid
 "graph LR
    A[Latitude/Longitude] --> B[Points API]
    B --> C[Forecast URLs]
    C --> D[Forecast Data]
    C --> E[Hourly Data]
    C --> F[Grid Data]
    C --> G[Stations]
    G --> H[Current Observations]")

;; **Step 1: Get the Points**

;; First, you query the `/points/{lat},{lon}` endpoint with your coordinates. This returns URLs for various forecast products specific to that location.

;; **Step 2: Fetch the Data**

;; Use the returned URLs to fetch the specific weather data you need: forecasts, hourly predictions, current conditions, etc.

;; This design is smart because:
;;
;; - Forecasts are generated for grid points, not exact coordinates
;; - It allows the API to scale efficiently
;; - You get all relevant endpoints in one initial request

;; ## Why Keyword Arguments?

;; Throughout this article, you'll notice all our functions use **keyword arguments** instead of positional arguments. Here's why:

(kind/code
 ";; Traditional positional arguments
(fetch-points 40.7128 -74.0060
  (fn [result]
    (if (:success result)
      (handle-success (:data result))
      (handle-error (:error result)))))

;; Keyword arguments style
(fetch-points
  {:lat 40.7128
   :lon -74.0060
   :on-success handle-success
   :on-error handle-error})")

;; **Benefits of keyword arguments:**

;; 1. **Self-documenting** - Clear what each value represents
;; 2. **Flexible** - Order doesn't matter
;; 3. **Optional parameters** - Easy to add defaults
;; 4. **Extensible** - Add new options without breaking existing code
;; 5. **Beginner-friendly** - Easier to understand and use

;; This is especially valuable when teaching or sharing code examples.

;; ## Setting Up: What We Need

;; For this tutorial, you need absolutely nothing installed locally! We'll use:

;; - **Scittle** - ClojureScript interpreter (from CDN)
;; - **Reagent** - React wrapper for ClojureScript (from CDN)
;; - **NWS API** - Free weather data (no key needed)
;; - **Your browser** - That's it!

;; All demos will be embedded right in this article. You can:
;;
;; - Run them immediately
;; - View the source code
;; - Copy and adapt for your own projects

;; ## Coming Up

;; In the following sections, we'll build progressively complex examples:

;; 1. **Simple Weather Lookup** - Basic API call and display
;; 2. **Current Conditions** - Detailed weather information
;; 3. **7-Day Forecast** - Visual forecast cards
;; 4. **Hourly Forecast** - Time-based predictions
;; 5. **Weather Alerts** - Severe weather warnings
;; 6. **Complete Dashboard** - Full-featured weather app

;; Each example will be fully functional and ready to use. Let's get started!

;; ---

;; **Ready to build?** Let's start with the core API layer in the next section.

;; ## The API Layer

;; Before we build demos, let's look at our API functions. We've created a complete
;; API layer that uses **keyword arguments** for all functions.

^:kindly/hide-code
(kind/code (slurp "src/scittle/weather/weather_api.cljs"))

;; This API layer provides all the functions we need:

;; - **`fetch-points`** - Convert coordinates to NWS grid points
;; - **`fetch-forecast`** - Get 7-day forecast
;; - **`fetch-hourly-forecast`** - Get hourly predictions
;; - **`fetch-current-observations`** - Real-time weather data
;; - **`fetch-alerts-for-point`** - Active weather alerts
;; - **`fetch-complete-weather`** - Convenience function for all data

;; Notice how every function follows the same pattern: a single keyword argument map
;; with `:on-success` and `:on-error` callbacks. This makes the code self-documenting
;; and easy to use.

;; ## Demo 1: Simple Weather Lookup

;; Let's start with the simplest possible weather app. This demo:

;; - Accepts latitude and longitude input
;; - Fetches weather data using the NWS API
;; - Displays location, temperature, and forecast
;; - Includes quick-access buttons for major cities
;; - Shows loading and error states

;; **Key features:**

;; - Uses keyword arguments: `{:lat 40.7128 :lon -74.0060 :on-success ... :on-error ...}`
;; - Native browser fetch (no external libraries)
;; - Simple, clean Reagent components
;; - Inline styles (no CSS files needed)

^:kindly/hide-code
(kind/code (slurp "src/scittle/weather/simple_lookup.cljs"))

;; ### Try It Live

;; Click a city button or enter coordinates to see it in action!

(kind/hiccup
 [:div#simple-lookup-demo {:style {:min-height "500px"}}
  [:script {:type "application/x-scittle"
            :src "simple_lookup.cljs"}]])

;; ## Demo 2: Current Weather Conditions

;; Now let's build something more detailed. This demo shows comprehensive current conditions
;; with all available metrics from the weather station.

;; **What makes this demo powerful:**

;; - **Temperature unit conversion** - Toggle between Fahrenheit, Celsius, and Kelvin
;; - **Complete metrics grid** - Humidity, wind, pressure, visibility, dewpoint
;; - **Conditional data** - Heat index and wind chill when applicable
;; - **Station information** - See which weather station is reporting
;; - **Large display format** - Beautiful, scannable layout

;; **Technical features:**

;; - Two-step API process: coordinates → station → observations
;; - Helper functions for unit conversion (C→F, C→K)
;; - Wind direction formatting (degrees → compass direction)
;; - Distance conversion (meters → miles)
;; - Responsive grid layout

;; **The data flow:**

^:kindly/hide-code
(kind/mermaid
 "graph LR
    A[Coordinates] --> B[Get Station]
    B --> C[Station ID]
    C --> D[Latest Observations]
    D --> E[Display All Metrics]")

^:kindly/hide-code
(kind/code (slurp "src/scittle/weather/current_conditions.cljs"))

;; ### Try It Live

;; Click a city or enter coordinates, then use the F/C/K buttons to change units!

(kind/hiccup
 [:div#current-conditions-demo {:style {:min-height "700px"}}
  [:script {:type "application/x-scittle"
            :src "current_conditions.cljs"}]])

;; ## Demo 3: 7-Day Forecast Viewer

;; Now we're getting visual! This demo displays weather forecasts as beautiful cards
;; in a responsive grid layout.

;; **What makes this demo special:**

;; - **Visual weather cards** - Each period gets its own card with emoji weather icons
;; - **Smart icon mapping** - Automatically selects emojis based on forecast text
;; - **Flexible viewing** - Toggle between 7 days or all 14 periods (day + night)
;; - **Hover effects** - Cards lift and shadow when you hover
;; - **Responsive grid** - Automatically adjusts to screen size
;; - **Rich information** - Temperature, conditions, wind, precipitation chance

;; **Technical highlights:**

;; - Weather icon mapping with regex pattern matching
;; - CSS Grid with `auto-fill` for responsive layout
;; - Form-2 Reagent components for hover state
;; - Conditional rendering (precipitation only when > 0%)
;; - Toggle controls for view modes

;; **What you'll see on each card:**

;; - Period name (Tonight, Friday, Saturday, etc.)
;; - Weather emoji (⛈️, 🌧️, ☀️, ⛅, etc.)
;; - Temperature (with F/C/K conversion)
;; - Short forecast text
;; - Wind information (speed and direction)
;; - Precipitation probability (when applicable)

^:kindly/hide-code
(kind/code (slurp "src/scittle/weather/forecast_viewer.cljs"))

;; ### Try It Live

;; Select a city, toggle between 7 and 14 periods, and watch the cards rearrange!
;; Try hovering over cards to see the lift effect.

(kind/hiccup
 [:div#forecast-viewer-demo {:style {:min-height "800px"}}
  [:script {:type "application/x-scittle"
            :src "forecast_viewer.cljs"}]])

;; ## Demo 4: Hourly Forecast Timeline

;; This demo takes interactivity to the next level with a scrollable hourly timeline
;; and dynamic controls.

;; **Interactive features:**

;; - **Hour slider controls** - Choose between 6h, 12h, 24h, or 48h views
;; - **Horizontal scrolling timeline** - Swipe or drag to browse hours
;; - **Current hour highlighting** - Blue border marks "Now" with 🔵
;; - **Auto-scroll on load** - Automatically centers on current hour
;; - **Time formatting** - Clean display like "2 PM", "11 AM"
;; - **Rich hour cards** - Each shows weather icon, temp, precipitation, wind

;; **Technical innovations:**

;; - ISO 8601 time parsing and formatting
;; - Current hour detection (matches day and hour)
;; - Smooth scroll behavior with `scrollIntoView`
;; - Horizontal flex layout with `flex-shrink: 0`
;; - Responsive card sizing (min-width: 140px)
;; - Wind speed extraction from NWS string format

;; **User experience highlights:**

;; - **Visual scanning** - See weather trends at a glance
;; - **Touch-friendly** - Works great on mobile with swipe scrolling
;; - **Contextual information** - Only shows precipitation when > 0%
;; - **Hover feedback** - Cards lift up when you mouse over them
;; - **Current time anchor** - "🔵 Now" label makes orientation easy

;; **What each hour card displays:**

;; - Time label (or "🔵 Now" for current hour)
;; - Weather emoji matching conditions
;; - Temperature with unit conversion
;; - Precipitation percentage (when > 0%)
;; - Wind speed in mph

^:kindly/hide-code
(kind/code (slurp "src/scittle/weather/hourly_forecast.cljs"))

;; ### Try It Live

;; Select a city, then use the hour controls (6h, 12h, 24h, 48h) to adjust the timeline!
;; The current hour will be highlighted and centered automatically.

(kind/hiccup
 [:div#hourly-forecast-demo {:style {:min-height "700px"}}
  [:script {:type "application/x-scittle"
            :src "hourly_forecast.cljs"}]])

;; ## Demo 5: Weather Alerts System

;; Safety first! This demo displays active weather alerts with professional severity-based
;; styling and expandable detail views.

;; **Alert features:**

;; - **Severity-based color coding** - Left border colors indicate alert severity:
;;   - Extreme: Dark Red (#b91c1c) - Tornado warnings, extreme conditions
;;   - Severe: Orange (#ea580c) - Severe thunderstorm, flash flood warnings
;;   - Moderate: Yellow/Gold (#ca8a04) - Heat advisories, winter weather advisories
;;   - Minor: Green (#65a30d) - Frost advisories, minor weather events
;; - **Event-specific emoji icons** - 🌪️ tornado, 🌀 hurricane, 🌊 flood, 🔥 fire, etc.
;; - **Expandable alert cards** - Click to reveal full description and timing
;; - **Badge system** - Color-coded urgency, severity, and certainty badges
;; - **No alerts state** - Clean "✅ No Active Alerts" display when all is clear

;; **Technical features:**

;; - Dynamic border-left styling based on severity
;; - Expandable/collapsible sections with state management
;; - Badge color mapping for urgency (Immediate, Expected, Future)
;; - Badge color mapping for certainty (Observed, Likely, Possible)
;; - Time formatting for alert validity periods
;; - Conditional rendering (only show times when present)

;; **Color coding system:**

^:kindly/hide-code
(kind/mermaid
 "graph TD
    A[Alert Severity] --> B[Extreme - Dark Red]
    A --> C[Severe - Orange]
    A --> D[Moderate - Yellow]
    A --> E[Minor - Green]

    F[Urgency] --> G[Immediate - Red]
    F --> H[Expected - Orange]
    F --> I[Future - Blue]

    J[Certainty] --> K[Observed - Green]
    J --> L[Likely - Blue]
    J --> M[Possible - Purple]")

;; **What each alert card shows:**

;; - Event name with emoji icon (Tornado Warning 🌪️)
;; - Headline/summary
;; - Severity, urgency, certainty badges
;; - Expandable full description
;; - Effective and expiration times

;; **Pro tip:** Try Oklahoma City or Kansas City - they're in Tornado Alley and often
;; have active weather alerts!

^:kindly/hide-code
(kind/code (slurp "src/scittle/weather/weather_alerts.cljs"))

;; ### Try It Live

;; Try different cities - some locations may show "No Active Alerts" which is good news!
;; Click "View Details" on any alert to expand it.

(kind/hiccup
 [:div#weather-alerts-demo {:style {:min-height "600px"}}
  [:script {:type "application/x-scittle"
            :src "weather_alerts.cljs"}]])

;; ## Demo 6: Complete Weather Dashboard

;; **The Grand Finale!** This demo brings everything together into a professional,
;; full-featured weather application.

;; **What makes this special:**

;; This is what you build when you understand all the pieces. It's a complete weather
;; application that could serve as the foundation for a production app!

;; **Integrated features:**

;; - **Beautiful gradient header** - Purple gradient with location and last-updated time
;; - **Tab navigation system** - Switch between Current, 7-Day, Hourly, and Alerts views
;; - **Unified settings bar** - Temperature unit control and auto-refresh indicator
;; - **Grid-based city selector** - 8 major cities in a responsive grid layout
;; - **Parallel data fetching** - Loads all weather data simultaneously for speed
;; - **Comprehensive state management** - Tracks location, loading, units, and timestamps

;; **Technical architecture:**

;; - **Parallel API calls** - Uses atoms and completion checks to fetch 4 data sources at once
;; - **Unified API function** - `get-all-weather-data` consolidates the multi-step process
;; - **Tab-based views** - Each tab renders different data using the same source
;; - **Consistent styling** - Reuses patterns from previous demos in a cohesive design
;; - **Error handling** - Gracefully handles missing alerts or failed requests

;; **The data flow:**

^:kindly/hide-code
(kind/mermaid
 "graph TB
    A[User clicks city] --> B[Fetch Points]
    B --> C{Parallel Requests}
    C --> D[Forecast]
    C --> E[Hourly]
    C --> F[Station → Observations]
    C --> G[Alerts]
    D --> H[Complete Data]
    E --> H
    F --> H
    G --> H
    H --> I[Render Dashboard]")

;; **What each tab shows:**

;; - **☀️ Current** - Large temp display with emoji icon + key metrics (humidity, wind, pressure)
;; - **📅 7-Day** - Week forecast cards in responsive grid
;; - **⏰ Hourly** - Next 12 hours with time labels and temps
;; - **⚠️ Alerts** - Weather warnings or "All clear" message

;; **Why this matters:**

;; This demo shows how to build a real application by composing smaller pieces. Each previous
;; demo taught a specific pattern, and now we see how they fit together into something
;; useful and polished.

^:kindly/hide-code
(kind/code (slurp "src/scittle/weather/complete_dashboard.cljs"))

;; ### Try It Live

;; Click any city and watch all the data load! Switch between tabs to see different views
;; of the same weather data. Try changing the temperature unit - it updates across all tabs!

(kind/hiccup
 [:div#complete-dashboard-demo {:style {:min-height "900px"}}
  [:script {:type "application/x-scittle"
            :src "complete_dashboard.cljs"}]])

;; ---

;; ## Wrapping Up

;; **What We've Built**

;; Over these six demos, we've created a complete weather application suite:

;; 1. **Simple Lookup** - Foundation for API calls and state management
;; 2. **Current Conditions** - Unit conversion and comprehensive data display
;; 3. **Forecast Viewer** - Visual cards and responsive grids
;; 4. **Hourly Timeline** - Interactive controls and horizontal scrolling
;; 5. **Weather Alerts** - Severity-based styling and expandable content
;; 6. **Complete Dashboard** - Integration and professional UI

;; **Key Takeaways**

;; - **No API key required** - The NWS API is free, reliable, and comprehensive
;; - **Keyword arguments everywhere** - Self-documenting, flexible code
;; - **Scittle = Zero friction** - No build tools, instant feedback, pure browser development
;; - **Composable patterns** - Each demo builds on previous concepts
;; - **Production-ready patterns** - Error handling, loading states, responsive design

;; **What You Can Do Next**

;; These demos are starting points. Here are some ideas to extend them:

;; - Add geolocation to auto-detect user's location
;; - Implement search history with localStorage
;; - Add weather data visualization (charts, graphs)
;; - Create weather comparison tools (multiple locations)
;; - Build severe weather notification system
;; - Add unit preferences persistence
;; - Implement auto-refresh functionality
;; - Create mobile-optimized views
;; - Add weather data export features

;; **The Power of Browser-Native Development**

;; What makes this approach special:

;; - **Immediate feedback** - Changes appear instantly at localhost:1971
;; - **No dependencies** - Just ClojureScript, Reagent, and a browser
;; - **Educational** - See exactly how everything works
;; - **Shareable** - Send a single HTML file, no setup required
;; - **Extensible** - Add features without rebuild cycles

;; **Resources**

;; - [NWS API Documentation](https://www.weather.gov/documentation/services-web-api)
;; - [Scittle Documentation](https://github.com/babashka/scittle)
;; - [Reagent Guide](https://reagent-project.github.io/)
;; - [ClojureScript Cheatsheet](https://cljs.info/cheatsheet/)

;; **Thank You!**

;; I hope these demos inspire you to build your own weather applications - or any other
;; browser-based tools. The combination of Scittle, ClojureScript, and free APIs like
;; the NWS opens up endless possibilities for creative, educational, and practical projects.

;; Weather data belongs to everyone. Let's build great things with it! ☀️🌧️❄️

;; ---

;; *All source code is available in the article above. Copy, modify, and share freely!*
