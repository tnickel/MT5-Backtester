# MT5 Backtester — Project Documentation

## 1. Project Overview
The **MT5 Backtester** is a comprehensive, Java-based desktop application (Swing) designed to act as a professional-grade orchestrator for the MetaTrader 5 Terminal. Its primary purpose is to completely automate, streamline, and extend the strategy testing lifecycle — from individual backtests to full-scale parameter optimization and robustness analysis.

Usually, testing an Expert Advisor (EA) across multiple symbols and timeframes requires hundreds of manual clicks. This application replaces that entire workflow with a fully automated pipeline: a **Batch-Runner Engine** controls MT5 via CLI and INI configurations, runs tests sequentially, extracts the results, persists history to a local SQLite database, and renders professional-quality offline reports — all from a single, unified dark-mode UI.

## 2. Core Modules & Features

### 2.1. Single Backtester
- Run a single EA backtest with configurable Symbol, Timeframe, Tick Model, Date Range, Deposit, Currency, and Leverage.
- **EA Parameter Configuration Dialog**: Full GUI editor for `.set` files with section-grouped table view, color-coded "Modified" highlighting, live filtering, reset-to-default, and Generate Default (auto-starts MT5 briefly to export compiled defaults).
- Integrated **Report Viewer Dialog** with metric cards (Profit, Trades, Win Rate, Drawdown, Profit Factor, Sharpe), a high-resolution `Java2D` equity chart, and a detailed statistics panel.

### 2.2. Sequential Multi-Backtesting
Define a batch of $N$ Expert Advisors × $M$ Symbols × $X$ Timeframes. The platform generates all $N × M × X$ combinations and runs them strictly sequentially — no concurrency lockups, no chart overlaps, no CPU exhaustion.
- **Fault Tolerance**: If a specific run fails, the engine logs the error and proceeds to the next run.
- **Master-Detail UI**: Session-persistent tree structure with batch history. Individual runs can be inspected, deleted, or reopened.
- **Aggregated HTML Summary**: All runs compile into a single `multi_report.html` with Base64 embedded equity charts for effortless sharing via email.
- **Green Row Highlighting**: Latest generated report is visually highlighted in the results table.

### 2.3. MT5 Strategy Optimizer (Genetic & Complete)
Full integration with the MT5 built-in genetic and complete algorithm optimizer.
- **Optimization Modes**: Disabled, Slow Complete Algorithm, Fast Genetic Algorithm, All Symbols (Selected in Market Watch).
- **Optimization Criteria**: Balance, Balance + Max Profit Factor, Balance + Max Expected Payoff, Balance + Min Drawdown, Balance + Max Recovery Factor, Balance + Max Sharpe Ratio, Custom (Max).
- **Forward Testing**: Supports No Forward, 1/2, 1/3, 1/4 period, or Custom Forward Date.
- **Agent Selection**: Local, Remote, and MQL5 Cloud agents.
- **Result Table**: Sortable with all key metrics (Pass #, Profit, Trades, Profit Factor, Expected Payoff, Drawdown, Recovery Factor, Sharpe) plus all optimized parameter columns.
- **Forward Test Results**: Separate tab for forward-test pass data.
- **Double-Click to Backtest**: Double-click any optimization pass to instantly run a single backtest with those exact parameters and view the Report Viewer.
- **Apply Best Parameters**: One-click to write the best-performing pass parameters back into the EA configuration.
- **AutoConfig Button**: Automatically calculates reasonable Start/Step/End optimization ranges for all numeric parameters based on heuristic analysis of default values.
- **Combined Analysis Tab**: A unified view to manage and inspect top-performing strategies across multiple optimizations.
  - **Result Management**: Delete individual passes or bulk-delete multiple passes (Shift-click) with a safety confirmation dialog.
  - **Robustness Test (Sensitivity Drill-Down)**: Run a deep-dive sensitivity analysis on any optimized pass. The system sweeps each optimized parameter from -10% to +10% using the Slow Complete algorithm to verify stability against market noise.
  - **Diagnostic UI**: Double-clicking a sensitivity result opens an advanced diagnostic popup containing:
    - Overall CV% (Coefficient of Variation) and Base Profit.
    - Parameter CV Breakdown: A detailed table listing the CV% for each parameter.
    - Interactive Line Charts (Kennlinien): Sparkline charts visualizing parameter variations vs. profit, with the base value explicitly highlighted in red and precise Start/Step/End axis labels.
    - Calculation Transparency: An Info-Button (`ℹ`) displays a detailed, layman-friendly mathematical explanation of the CV calculation for that specific parameter row.
    - Full list of the original optimized strategy settings.
  - **AI Strategy Stability Scoring (LLM Integration)**: Automates the evaluation of sensitivity results using Large Language Models via OpenRouter. The AI analyzes parameter robustness, detects "cliffs" vs. "plateaus", and assigns a normalized Stability Score (0-100). The parsed AI stability score is automatically synchronized across the Sensitivity, Combined, and Selected analysis tables for seamless data consistency.
  - **Optimization Results Filtering**: Features a robust 12-criterion trade filter with user-configurable thresholds for profit, trade counts, drawdown, expected payoff, Sharpe ratio, and recovery factor.
    - *Dynamic NaN Handling*: Dynamically processes `NaN` values (for passes lacking forward results) in out-of-sample filters, filtering them out only if the corresponding boundary is set to a restrictive value.
    - *Auto-Activation & SQLite Persistence*: Clicking "Anwenden & Schließen" automatically updates the active filter status in the UI, and persists the 12 thresholds, filter-enabled state, and only-matched-state to the `APP_SETTINGS` database table.
### 2.4. Robustness Scanner (Parameter Sensitivity / "Kennlinienfahrt")
A unique module for advanced strategy validation through systematic parameter sweeps.
- **Individual Parameter Sweep**: Each selected parameter is optimized in isolation via the Slow Complete Algorithm while all other parameters remain fixed.
- **Historical Time-Shift**: Run each sweep across up to 20 shifted time periods (configurable shift in days), to verify parameter stability across different market regimes.
- **Interactive HTML Report**: Generates a full-featured Chart.js based HTML report with:
  - Per-parameter line charts with all time-period overlays.
  - Plateau (Tableau) detection: Green shaded areas highlight stable performance zones (< 5% variance).
  - Default value annotation: A green dot marks the currently configured value on the base-period curve.
- **Live Status Feedback**: Active scan parameter is highlighted in blue, completed parameters are color-coded (green = success, orange = error, yellow = flat/insensitive).
- **Remove Failed**: One-click deselect of parameters that caused errors or produced flat (insensitive) curves.
- **Plateau Metric Selection**: Sweep target can be Profit, Profit Factor, Expected Payoff, Sharpe, or Drawdown.
- **AutoConfig Button**: Same heuristic range generator as in the Optimizer module.
- **ETA Estimation**: Real-time estimated time remaining based on elapsed sweep times.

### 2.5. EA Configuration Management
Comprehensive EA input parameter lifecycle management.
- **`.set` File I/O**: Full read/write support for MT5's UTF-16 LE `.set` file format with BOM detection and UTF-8 fallback.
- **Custom vs. Default Configs**: Custom overrides stored in `config/ea_params/`, with transparent merge against MT5's `MQL5/Profiles/Tester/` defaults.
- **Generate Default Config**: Starts MT5 briefly with a minimal 1-day backtest to force MT5 to export the EA's compiled parameter list.
- **Database-Backed Snapshots**: Store/retrieve named parameter snapshots to/from a local SQLite database (EA_SAVED_CONFIGS table). Multiple named configurations per EA are supported.
- **Configuration Selection Dialog**: Visual table-based picker for database-stored configs with Create, Overwrite, Delete, and Load operations.
- **Visual DB Status**: "Store DB" and "Get from DB" buttons glow green when database entries exist for the current EA.

### 2.6. History & Persistence
- **SQLite Database**: Local persistent storage at `~/.mt5_backtester/history.db` (via `sqlite-jdbc`).
- **History Panel**: Master-detail tree browser grouped by Run Type (BACKTEST, MULTI, OPTIMIZATION, ROBUSTNESS) → Expert → individual timestamped runs.
- **Today Highlighting**: Runs from today are rendered in bold green for instant identification.
- **Double-Click to Open**: Reports are opened directly in the system browser.
- **Run Summary**: Details pane shows run metadata and JSON-formatted result metrics.
- **Automatic Run Saving**: All backtest types (single, multi, optimization, robustness) automatically persist their results and report paths to the database.

### 2.7. Dukascopy Tick Data Integration
- Direct HTTP download from Dukascopy servers for tick-precision Bid/Ask data.
- **BI5 Decoding**: Native Java decoder for Dukascopy's LZMA-compressed `.bi5` binary tick format.
- **CSV Conversion**: Convert raw tick data to MT5-importable CSV format with configurable timeframe aggregation.
- **Custom Symbol Import**: Create custom symbols in MT5 and import external tick data for fully independent offline testing.

### 2.8. Integrated Reporting Engine
- **Report Parser**: Handles MT5's UTF-16LE HTML reports (often falsely suffixed `.xml`) with regex-based extraction tuned for both German and English localizations.
  - Extracts: Net Profit, Gross Profit/Loss, Drawdown (Equity + Balance), Profit Factor, Sharpe, Recovery Factor, Expected Payoff, Win Rate, Short/Long positions, Total Trades, and more.
  - Parses full trade history with timestamps for date-based equity chart X-axis.
- **Equity Chart Panel**: Custom `Graphics2D` renderer with anti-aliasing, gradient fills, date-aware X-axis, Balance/Equity dual lines, profit/loss color zones, deposit reference line, and legend.
- **Report Viewer Dialog**: Modal dialog with dark-mode metrics cards, embedded equity chart, detailed statistics, and "Open in Browser" / "Open Folder" actions.
- **Multi-Report HTML Generator**: Aggregates batch results into a single HTML document with Base64-embedded equity chart PNGs.
- **Robustness HTML Generator**: Produces Chart.js interactive line charts for parameter sensitivity analysis.

### 2.9. Model Context Protocol (MCP) Server
- **Claude Desktop Integration**: Includes a Python-based MCP Server (`backtester_mcp.py`) that allows AI assistants (like Claude) to directly connect to the Backtester's local SQLite database.
- **Available Tools**: Exposes SQL queries, schema extraction, and specialized functions like `get_sensitivity_overview`, `get_robust_strategies`, and `get_fragile_parameters`.
- **Use Case**: Enables users to prompt Claude to perform deep statistical analysis on optimization runs and parameter sensitivity curves directly from the chat interface.

## 3. Technology Stack & Architecture

### 3.1. Technologies Used
| Component | Technology |
|---|---|
| **Language** | Java 17+ (compatible up to Java 21) |
| **Build Tool** | Maven (Shade plugin for single-file Uber-JAR deployment) |
| **UI Framework** | Java Swing |
| **Look & Feel** | FlatLaf 3.4 Dark Mode (custom grey/orange accent palette) |
| **Charting** | Native `java.awt.Graphics2D` (in-app) + Chart.js (HTML reports) |
| **Database** | SQLite via `sqlite-jdbc` |
| **JSON** | Gson 2.10 |
| **Logging** | SLF4J + Logback |
| **Data Parsing** | Jackson XML, Univocity CSV Parsers |
| **Compression** | XZ / LZMA (Tukaani) for Dukascopy BI5 decoding |
| **Date Picker** | LGoodDatePicker |
| **Installer** | jpackage + WiX Toolset (MSI) |

### 3.2. Package Architecture

```
com.backtester
├── Main.java                      # Application entry point (FlatLaf init, MainFrame launch)
├── config/
│   ├── AppConfig.java             # Singleton config manager (properties + path management)
│   ├── EaParameter.java           # EA parameter data model (.set file entry)
│   └── EaParameterManager.java    # .set file I/O, default generation, backtest preparation
├── database/
│   ├── DatabaseManager.java       # SQLite singleton (HISTORY_RUNS + EA_SAVED_CONFIGS tables)
│   ├── EaDbConfig.java            # DB config snapshot model
│   └── HistoryRun.java            # DB history run model
├── engine/
│   ├── BacktestConfig.java        # Single backtest configuration (symbols, timeframes, etc.)
│   ├── BacktestRunner.java        # ProcessBuilder execution of terminal64.exe with pipe management
│   ├── IniGenerator.java          # Dynamic MT5 tester.ini generation
│   ├── MultiBacktestConfig.java   # Multi-batch configuration model
│   ├── MultiBacktestRunner.java   # SwingWorker-based batch orchestrator
│   ├── OptimizationConfig.java    # Optimization configuration (modes, criteria, forward, agents)
│   ├── OptimizationRunner.java    # MT5 optimization execution + XML result parsing
│   └── RobustnessRunner.java      # Parameter sweep orchestrator with progress/ETA
├── report/
│   ├── BacktestResult.java        # Backtest result data model (all metrics + equity history)
│   ├── ReportParser.java          # MT5 HTML/XML report parser (UTF-16LE/UTF-8, DE/EN)
│   ├── MultiReportGenerator.java  # Batch HTML report with Base64 charts
│   ├── OptimizationReportParser.java  # MT5 optimization XML result parser
│   ├── OptimizationResult.java    # Optimization result model (passes + forward passes)
│   ├── RobustnessResult.java      # Robustness scan result container
│   └── RobustnessHtmlGenerator.java  # Chart.js interactive HTML robustness report
├── mt5/
│   ├── CustomSymbolManager.java   # MT5 custom symbol creation API
│   └── Mt5DataImporter.java       # External tick data import to MT5
├── dukascopy/
│   ├── DukascopyDownloader.java   # HTTP tick data retrieval from Dukascopy servers
│   ├── Bi5Decoder.java            # LZMA decompression for .bi5 binary format
│   └── CsvConverter.java          # Tick data to MT5-CSV conversion
└── ui/
    ├── MainFrame.java             # Top-level window (8-tab TabbedPane + header + status bar)
    ├── BacktestPanel.java         # Single backtest configuration & execution
    ├── MultiBacktestPanel.java    # Multi-backtesting batch builder & tree view
    ├── OptimizationPanel.java     # Strategy optimizer with parameter table & results
    ├── RobustnessPanel.java       # Robustness scanner with sweep status & live feedback
    ├── HistoryPanel.java          # Persistent run history tree with today-highlighting
    ├── DukascopyPanel.java        # Dukascopy data download & management
    ├── SettingsPanel.java         # Application settings (paths, defaults)
    ├── LogPanel.java              # Real-time log viewer with level-based coloring
    ├── ReportViewerDialog.java    # Modal backtest report dialog with charts
    ├── EaConfigDialog.java        # EA parameter editor with section grouping
    ├── EquityChartPanel.java      # Java2D equity curve chart renderer
    └── DbConfigSelectionDialog.java  # DB config store/load picker
```

## 4. Execution Flow

### 4.1. Single Backtest
1. User configures a test (EA, Symbol, Timeframe, Model, Dates, Account settings).
2. App generates a `tester.ini` inside a unique timestamped reporting directory.
3. If EA parameters are configured, the `.set` file is copied to `MQL5/Profiles/Tester/`.
4. App spawns a local process via `ProcessBuilder` executing `terminal64.exe /config:tester.ini`.
5. The Java application reads MT5's `stdout` continuously on a daemon thread to prevent OS-level 64KB pipe deadlocks.
6. Once the process terminates, the system locates the generated MT5 HTML report (`report.htm` or `report.xml`).
7. `ReportParser` decodes the UTF-16LE stream, extracts all metrics via regex, and parses the trade history table for equity data.
8. Results are displayed in the `ReportViewerDialog` and saved to the SQLite history database.

### 4.2. Multi-Batch Backtest
1. User selects multiple EAs, Symbols, and Timeframes.
2. `MultiBacktestRunner` calculates all combinations and executes them sequentially.
3. Each run follows the single backtest flow (4.1).
4. After all runs complete, `MultiReportGenerator` creates an aggregated HTML summary.
5. The batch is saved as a single history entry in the database.

### 4.3. Optimization
1. User configures optimization settings (Mode, Criterion, Forward, Agents) and edits parameter ranges.
2. A specialized `tester.ini` is generated with `Optimization=2` (Genetic) or `Optimization=1` (Complete).
3. MT5 runs the optimization and produces an XML result file with all pass data.
4. `OptimizationReportParser` parses the result XML and populates sortable result tables.
5. User can double-click any pass to run a verification backtest with those exact parameters.

### 4.4. Robustness Scan
1. User selects parameters to sweep and configures the number of historical time shifts.
2. `RobustnessRunner` iterates through each parameter in isolation, running a Complete Algorithm optimization for each across all time periods.
3. Real-time visual feedback: active parameter highlighted, completed parameters color-coded by status.
4. On completion, `RobustnessHtmlGenerator` produces an interactive Chart.js HTML report with plateau detection.

## 5. Configuration & Data Paths

| Path | Purpose |
|---|---|
| `config/backtester.properties` | Application settings (MT5 path, directories, defaults) |
| `config/ea_params/` | Custom EA `.set` parameter files |
| `data/` | Downloaded Dukascopy tick data |
| `backtest_reports/` | Generated backtest reports, charts, and HTML summaries |
| `~/.mt5_backtester/history.db` | SQLite database (run history + saved EA configs) |
| `logs/backtester.log` | Application log files (Logback, relative to app execution directory) |
| `C:\Forex\Mt5\TickmillLifeMql5\MQL5\Logs\` | MetaTrader 5 Terminal Logs (for MT5 actions & MQL5 scripts) |
| `C:\Forex\Mt5\TickmillLifeMql5\tester\logs\` | MetaTrader 5 Strategy Tester Logs (for CLI backtest & optimization runs) |

### Logging Notes for AI & Developers
* **Application Logs**: Standard application logs (using SLF4J and Logback) are written to `logs/backtester.log` relative to the current directory from which the JAR is executed. Daily log rotation keeps up to 30 days of history as `logs/backtester.YYYY-MM-DD.log`.
* **MT5 Logs**: When executing backtests or optimizations via CLI, MT5 records the tester-specific execution logs in the MT5 data directory under `tester/logs/` (e.g., `C:\Forex\Mt5\TickmillLifeMql5\tester\logs\YYYYMMDD.log`). Terminal-wide messages are stored in `MQL5/Logs/`.
* **Uncaught JavaFX Exceptions**: Any uncaught exceptions thrown on the UI thread (JavaFX Application Thread) that are not caught explicitly will be written to the standard error stream (`System.err`). They can be observed in the terminal console when starting the application via `start.bat`.


## 6. Supported Currency Pairs
EURUSD, GBPUSD, USDJPY, USDCHF, AUDUSD, NZDUSD, USDCAD, EURGBP, EURJPY, GBPJPY, EURCHF, EURAUD, GBPAUD, AUDNZD, AUDCAD — all with correct Dukascopy point multiplier mappings for data download.

## 7. Build & Distribution
- **Development**: `mvn clean compile` + run from IDE.
- **Fat JAR**: `mvn clean package -DskipTests` produces a single shaded Uber-JAR in `target/`.
- **Windows Installer**: `build_installer.ps1` uses `jpackage` + WiX Toolset to generate a professional MSI installer with bundled JRE, desktop shortcut, and start menu integration.

## 8. Automation & AI Development Context
This project serves as a showcase for modern AI-assisted Software Engineering. Engineered using an *Antigravity + Gemini Ultra* prompt chain workflow, the boilerplate generation, complex architectural wireframing, and feature implementation were dramatically accelerated. The application grew from initial concept to a full-featured, professional-grade desktop application with 8 major modules, 30+ Java classes, and 15,000+ lines of code in a fraction of the time traditional development would require. Focus remained strictly on logic refinement, debugging, edge-case handling (UTF-16 parsing, German locale support), and UX polish.

## 9. Quality Assurance & Test Engineering Framework
To ensure system stability and support continuous integration, QA teams and test engineers can use the following framework to design test concepts and test specifications:

### 9.1. Architectural Test Boundaries (Mocking Strategy)
The application relies heavily on external systems. A robust Unit/Integration test concept must strictly isolate the core Java logic from these systems:
- **MT5 Terminal Integration**: The execution of `terminal64.exe` via `ProcessBuilder` (in `BacktestRunner`, `OptimizationRunner`, and `RobustnessRunner`) must be mocked. Tests should inject simulated MT5 HTML/XML reports from the file system to test the `ReportParser` logic without needing an actual MT5 instance running.
- **SQLite Database**: The `DatabaseManager` operations should be tested using an in-memory database (e.g., H2) or an isolated, temporary SQLite test-file to ensure tests remain independent and fast.
- **Dukascopy API**: The HTTP endpoints in `DukascopyDownloader` must be mocked (e.g., using Mockito or WireMock) to return static binary data arrays (`.bi5`), preventing network dependencies, rate-limiting, and slow test executions.

### 9.2. Critical Path Testing
A comprehensive test concept should prioritize the following components based on their business value and risk:
1. **Core Engine Logic (Highest Priority)**:
   - Parameter permutation logic (math accuracy for start/step/stop optimization ranges).
   - Correct generation and encoding of `tester.ini` and `.set` files (UTF-16LE with BOM, correct Windows vs. Linux path formatting).
   - Robustness scanner calculations (correct aggregation of metrics, coefficient of variation (CV%), and plateau detection).
   - AI Stability Score parsing from LLM API responses.
2. **Data Parsers & Converters**:
   - Reliable extraction of metrics from varying MT5 report localizations (Regex handling for German/English reports).
   - Correct decompression of `.bi5` files and conversion to MT5 CSV format (handling timezone shifts and gap detection).
3. **Database Layer (Persistence)**:
   - Thread-safe CRUD operations (Create, Read, Update, Delete) for Backtest and Optimization results.
   - Bulk-insert transaction performance and automated rollbacks on SQL errors.

### 9.3. Test Design Guidelines
When writing detailed test concepts for this project, it is recommended to structure test cases logically by package modules (Engine, Database, Dukascopy, UI). 
- Target a Line Coverage of **> 85%** on the packages `com.backtester.engine.*` and `com.backtester.report.*`.
- Pure business logic (e.g., the mathematics inside `RobustnessResult` and `MultiBacktestConfig`) must be fully unit-tested.
- UI Tests (Swing Components) should be considered secondary; prioritize the underlying TableModels and Export logic over visual rendering tests.
