# Verifikations-Ergebnis: Code-Review MT5-Backtester (Stand 2026-08-29)

> **Fix-Status 2026-08-29 (nach diesem Report umgesetzt):** Alle Defekt-Klassen aus den
> Abschnitten 2 und 3 wurden behoben (Details in Abschnitt 5). Offen bleiben bewusst nur
> U6 (Duplikat-Code) und U11 (God-Class-Split) als Wartbarkeitsschuld sowie der
> dokumentierten U10-Tradeoff. Validierung: `mvn test` — **1255 Tests, 0 Fehler**
> (JDK 25 / Temurin 25.0.4.1, inzwischen installiert).

Antwort auf den Verifikations-Brief vom 2026-08-28 (nach Abarbeitung gelöscht; die Befund-IDs
E1–E15, U1–U12, D1–D14, X1–X13 beziehen sich auf dessen Abschnitte 3.1–3.4, Negativbefunde in
Abschnitt 4). Geprüft wurde der **aktuelle Stand HEAD `5b2531d`** (Arbeitsbaum ist code-seitig
clean; die uncommitteten Änderungen betreffen nur Docs). Wichtiger Kontext: Nach dem Review-Snapshot (`5be94f7` ≈ `e2875b5`) wurden zwei
Fix-Commits gelandet:

- `6072d86` „Complete code-review fixes: threading, memory, persistence, timezone"
- `5b2531d` „Remove machine-local files and tick data from git; add properties template"

Deshalb lautet die zentrale Frage je Befund nicht nur „stimmt er?", sondern „ist er gefixt —
und ist der Fix korrekt?". Statuswerte: `GEFIXT_OK`, `GEFIXT_MANGELHAFT`, `TEILWEISE`,
`AKTIV`, `WIDERLEGT`.

## 0. Gesamtbild

- **54 von 54 Befunden des Reviews wurden am Code bestätigt — keiner ist WIDERLEGT.**
  Das ursprüngliche Review ist inhaltlich als sehr präzise zu bewerten.
- **41× GEFIXT_OK** (Fix korrekt, meist mit neuen Regressionstests abgesichert),
  **7× TEILWEISE**, **2× GEFIXT_MANGELHAFT**, **4× AKTIV** (unverändert bestehend).
- Beide KRITISCH-Befunde (D1 Spread, D6 Zahlen-Parsing) und X1 (taskkill maschinenweit)
  sind sauber behoben.
- **Aber:** Der Fix-Commit hat selbst neue Probleme eingeführt/übrig gelassen (Abschnitt 3),
  und ein ganzer Feature-Bereich (Dukascopy) ist in der laufenden App nicht erreichbar —
  dort landeten mehrere der besten Fixes (Abschnitt 2).
- Build-Check: `mvn compile` schlägt auf dieser Maschine am Enforcer fehl — es ist nur
  JDK 21 installiert, das Projekt erzwingt JDK 25 (`pom.xml`: `maven.compiler.source=25`).
  Der Compile-Erfolg der Fix-Commits konnte daher hier nicht reproduziert werden.

## 1. Befund-Verifikation

Legende: Status bezieht sich auf den **aktuellen Code**. „Fundort" = Position im HEAD-Stand.

### 1.1 Engine (E1–E15)

| ID | Status | Fundort (aktuell) | Schweregrad-Bewertung |
|----|--------|-------------------|------------------------|
| E1 | GEFIXT_OK | `BacktestRunner.java:487,554,610`; `Mt5ProcessGuard.findTerminalPidsForInstall` | HOCH angemessen |
| E2 | GEFIXT_OK (Rest) | `BacktestRunner.java:482-565` | HOCH → heute eher MITTEL |
| E3 | GEFIXT_OK (Rest) | `WorkflowEngine.java:84,113,116,128-129,143` (alle 6 Felder `volatile`) | HOCH angemessen |
| E4 | TEILWEISE | `OptimizationRunner.java:421-460`; `PassPresetResolver.java:356-402`; `WorkflowEngine.java:458-462`; **AKTIV:** `RobustnessRunner.java:141,225,240,243` | HOCH/MITTEL; Rest jetzt MITTEL |
| E5 | GEFIXT_OK | `Mt5ProcessGuard.java:25-27,53-67,306-339` (liegt im `engine`-, nicht `mt5`-Package) | MITTEL angemessen |
| E6 | GEFIXT_OK | `BacktestRunner.java:39-40`; `SensitivityRunner.java:24-25`; `RobustnessRunner.java:35-36`; `MultiBacktestRunner.java:28-30` | MITTEL angemessen |
| E7 | GEFIXT_OK | `WorkflowEngine.java:879+893, 2442+2464, 1755 (+2301-2315)` | MITTEL angemessen |
| E8 | GEFIXT_OK | `VirtualDesktopHelper.java:304-311, 448-454` | MITTEL angemessen |
| E9 | GEFIXT_OK | `Mt5ProcessGuard.showConfirmDialogOnEdt` 341-354, gerufen 121/224 | MITTEL angemessen |
| E10 | GEFIXT_OK | `Mt5LogTailer.java:27,170-181,209-212,263-278` (+Test) | MITTEL angemessen |
| E11 | GEFIXT_OK | `Mt5ProcessGuard.java:288-339`; `OptimizationRunner.java:113-124` | MITTEL angemessen |
| E12 | TEILWEISE | `OptimizationConfig.java:105,171-172`; `OptimizationRunner.java:85,113-114` | NIEDRIG → durch Fix-Nebenwirkung fast MITTEL |
| E13 | GEFIXT_OK | `LlmAnalysisService.java:94-96,527` | NIEDRIG angemessen |
| E14 | GEFIXT_OK | `SensitivityRunner.java:458,491-499`; `RobustnessRunner.java:128,230-238`; `WorkflowEngine.java:929-936,1876-1877` | NIEDRIG angemessen |
| E15 | GEFIXT_OK (Reste woanders) | `BacktestRunner.java:380-381,727,749` | NIEDRIG angemessen |

Bemerkungen zu den nicht-vollständigen:

- **E2:** `InterruptedException` wird jetzt separat gefangen (536-539) und restauriert; der
  generische `catch (Exception) → return true` (561-563) bleibt, ist aber kein Interrupt-Problem
  mehr — nur noch die „Infrastrukturfehler = safe to proceed"-Schwäche (nur `logMessage`,
  kein `log.error`).
- **E4:** Bulk-Embed beseitigt (Snapshot-Archivierung, `Mt5OptimizationImportService:199-204`
  „Deliberately NO bulk embedConcreteSetfile"), `saveStateDebounced()` drosselt auf 30 s.
  **Nicht gefixt:** `RobustnessRunner.periodMap` hält weiterhin die vollständigen
  `OptimizationResult`s aller Shifts im Heap (141/225/240/243).
- **E12:** `autoKillMt5` existiert, hat aber Default `true` und wird im GUI nie gesetzt →
  eine interaktive GUI-Optimierung killt Terminals der Installation **ohne Rückfrage**; der
  Bestätigungsdialog ist für Optimierungen toter Code. Widerspruch zum eigenen Kommentar in
  `OptimizationRunner.java:104-107` („false asks first (GUI) — a manual multi-day optimization
  must never be destroyed silently"). Fix: Default `false`, unattended-Pfade (Workflow/CLI)
  explizit `true` — wie `WorkflowEngine` es für BacktestConfigs vormacht.
- **E15:** Genannte Stellen nutzen jetzt `Locale.ROOT`; die Bug-Klasse lebt aber weiter in
  `IniGenerator.java:47,135`, `Mt5LogTailer.java:312,353` (dort beeinträchtigt sie die
  Critical-Failure-Erkennung), `SensitivityRunner.java:346`, `BacktestConfig.java:156`.

### 1.2 UI (U1–U12)

| ID | Status | Fundort (aktuell) | Schweregrad-Bewertung |
|----|--------|-------------------|------------------------|
| U1 | GEFIXT_OK | `ProjectWorkflowEditorView.java:4092-4135`; Flags 116/131/193/195 `volatile` | HOCH angemessen, behoben |
| U2 | GEFIXT_OK | `OptimizationView.java:93,1512-1521,1547` (Coalescing via `AtomicBoolean`) | HOCH angemessen, behoben |
| U3 | GEFIXT_OK | `EaParameterUiContext.java:14-27`; `EnumAwareParamCell.java:60-69`; Pins in 5 Views | MITTEL → niedrig |
| U4 | TEILWEISE | alle 5 genannten Stellen → Task; **Rest:** `ControllingView.java:1022,1110-1119` | MITTEL → niedrig-mittel |
| U5 | TEILWEISE | `MultiBacktestRunner.java:19` (kein SwingWorker mehr); `Main.java:140-166,214-256`; Swing-`ReportViewerDialog` bleibt (thread-korrekt) | MITTEL → niedrig |
| U6 | AKTIV | `BacktestView/RobustnessView/OptimizationView/MultiBacktestView` (4× IO); `ProjectWorkflowPipelineRunner.java:545 vs 800` | MITTEL (Wartbarkeit) |
| U7 | GEFIXT_MANGELHAFT | ~7.400 Z. gelöscht; **Reste:** `ui/DukascopyPanel.java` (633 Z.), `ui/LogPanel.java` (154 Z.) — referenzlos | MITTEL → niedrig |
| U8 | GEFIXT_OK | `OptimizationView.java:167-175,1689-1693` (`PauseTransition`-Debounce) | MITTEL → niedrig |
| U9 | GEFIXT_OK | `ProjectWorkflowEditorView.java:85-86,4268-4288` (Cap 200k/150k) | NIEDRIG angemessen |
| U10 | AKTIV | `ProjectWorkflowEditorView.java:84,4230-4266`; `JavaFXMain.java:44-47` | NIEDRIG (bewusster Tradeoff) |
| U11 | AKTIV | `ProjectWorkflowEditorView` 4.289 Z. (+84!), `OptimizationView` 2.375 Z., `ControllingView` 2.312 Z. | NIEDRIG |
| U12 | AKTIV | `OptimizationView.java:2321-2343`; `ControllingView.java:1332-1344` | NIEDRIG, harmlos |

- **U4-Rest:** `onStrategySelected` ruft pro Zeilen-Auswahl `PassPresetResolver.resolveForExecutionWithFallback`
  (Disk-IO) synchron auf dem FX-Thread.
- **U6:** Neu positiv: gemeinsamer `EaParameterTableHelper` und zentrale Logik in
  `EaParameterManager`; der Drift lebt aber fort (`MultiBacktestView.saveParametersOnDemand`
  speichert unter `"GLOBAL"/"GLOBAL"`, `RobustnessView` ohne `saveCustomParameters`), und das
  ~120-Zeilen-Duplikat des Task-Switches bleibt.
- **U7:** Der Hauptbestand (~7.400 Z.) wurde gelöscht, aber `DukascopyPanel`/`LogPanel`
  (~790 Z.) sind weiterhin referenzloser toter Code und hätten mitgelöscht werden sollen.
  `EquityChartPanel` und `ReportViewerDialog` sind legitim lebendig (7 JavaFX-Caller).

### 1.3 Daten / Report / Workflow (D1–D14)

| ID | Status | Fundort (aktuell) | Schweregrad-Bewertung |
|----|--------|-------------------|------------------------|
| D1 | GEFIXT_OK | `CsvConverter.java:110,117,120,128,134,224-227` (+`CsvConverterTest` XAUUSD-Fall) | KRITISCH gerechtfertigt, behoben |
| D2 | GEFIXT_OK | `PdfReportGenerator.java:74-76,341-359,466-484` | HOCH gerechtfertigt |
| D3 | TEILWEISE | konvertiert: `saveWorkflowState/saveWorkflowStrategyConfig/saveStrategyReview` → boolean + Escalation; **AKTIV:** `saveRun` 414-434 + 53 weitere log-and-return-Catches | HOCH → MITTEL |
| D4 | GEFIXT_OK | `Bi5Decoder.java:80-83,130-139` (+Test: trunciertes Record → Exception + Delete) | MITTEL gerechtfertigt |
| D5 | TEILWEISE | `DukascopyDownloader.java:29,173-181,293-299` (Timeout 120 s + `future.cancel(true)` + Backoff non-200); **AKTIV:** `MAX_PARALLEL_DOWNLOADS=10` (27) | MITTEL → NIEDRIG |
| D6 | GEFIXT_OK | `ReportParser.java:551-571` (else-Zweig 560-563, +Test) | KRITISCH gerechtfertigt, behoben |
| D7 | GEFIXT_OK | `DatabaseManager.java:1079-1084` (exakte Matches statt LIKE, +Test) | MITTEL gerechtfertigt |
| D8 | GEFIXT_OK | `DukascopyDownloader.java:207-210` (Sonntag ab 21 UTC, +Test) | MITTEL gerechtfertigt |
| D9 | GEFIXT_OK* | `CsvConverter.java:41-43,217-222`; `AppConfig.getDukascopyBrokerZone` (+DST-Test) — *aber nur im toten `DukascopyPanel` verdrahtet, Schlüssel fehlt im Template* | MITTEL gerechtfertigt |
| D10 | GEFIXT_OK | `ReportParser.java:489-494` (UTC + lenient(false) + Warnlog) | NIEDRIG-MITTEL gerechtfertigt |
| D11 | GEFIXT_OK | `CustomSymbolManager.java:122-148` (Temp-File + ATOMIC_MOVE + Fallback) | MITTEL gerechtfertigt |
| D12 | GEFIXT_OK | `MultiReportGenerator.java:335-336,384-389,866-873` (+Injection-Test) | MITTEL gerechtfertigt |
| D13 | GEFIXT_OK | `OptimizationReportParser.java:309-341` (BOM + NUL-Heuristik, +Test) | NIEDRIG gerechtfertigt |
| D14 | GEFIXT_OK | `CsvConverter.java:206-209` (digits aus Price-Point; Regex-Heuristik entfernt, +Test) | NIEDRIG gerechtfertigt |

- **D3:** Wie vermutet nur die Workflow-kritischen Muter konvertiert (mit Error-Alert in
  `ControllingView:1489,1500` und `DatabaseManagerPersistenceTest`). `saveRun` returnt still
  -1 und `BacktestRunner.java:299` / `WorkflowEngine.java:2194` ignorieren das — Run-History
  kann weiter still verloren gehen.
- **D5:** Kerngefahr behoben (Timeout > Worst-Case-Retry, Geist-Schreibungen via
  `future.cancel(true)` gestoppt, Backoff im non-200-Zweig mit Interrupt-Restaurierung).
  Empfohlene Drosselung von 10 Workern nicht umgesetzt.

### 1.4 Querschnitt / Repo-Hygiene (X1–X13)

| ID | Status | Fundort/Beweis | Schweregrad-Bewertung |
|----|--------|----------------|------------------------|
| X1 | GEFIXT_OK | `Main.java:62,81-167` — pfad-gefiltertes `findTerminalPidsForInstall` + Default-Nein-Dialog + PID-gezieltes Kill | KRITISCH korrekt entschärft |
| X2 | GEFIXT_OK | `git ls-files config/` → nur Template; Token in keinem getrackten File; History unbereinigt (erwartet) | KRITISCH für Repo-Stand behoben |
| X3 | GEFIXT_OK | `git ls-files data/` → 0; `git log --all -- '*.bi5'` → leer (nie committed, kein filter-repo nötig) | MITTEL vollständig behoben |
| X4 | GEFIXT_OK | `VirtualDesktopHelper.psQuote` + `toPowerShellArgumentArray` (`@('a','b')`) + `-EncodedCommand`; BacktestRunner powershell-frei | MITTEL Kern gelöst |
| X5 | GEFIXT_OK | `DukascopyDownloader.normalizeSymbol` `[A-Za-z0-9]{1,12}` vor URL/Pfad | MITTEL genau wie gefordert |
| X6 | GEFIXT_OK | `IniGenerator.validateIniValue` (lehnt CR/LF/NUL ab) + Test; `[`-Check entbehrlich | MITTEL Vektor geschlossen |
| X7 | GEFIXT_OK | `CliRunner` ohne `System.exit`/totes return; `Main` entscheidet | MITTEL saubere Schichtung |
| X8 | GEFIXT_OK | `git ls-files` → nicht getrackt; liegt weiter lokal auf Platte (Kandidat zum Löschen) | MITTEL Repo sauber |
| X9 | GEFIXT_OK | `StrategyExporter.sanitizeFilenameComponent` auf eaName (+Test) | MITTEL genau wie gefordert |
| X10 | GEFIXT_OK | `LocalBacktestHttpServer` Host-Check (`127.0.0.1:28987`/`localhost`) um alle Kontexte; CORS nur lokales Origin-Echo | NIEDRIG Rebinding-Vektor zu |
| X11 | TEILWEISE | `AppConfig.resolveBasePath` (sysprop `backtester.home` > env > `user.dir`); Doc-Bug behoben; **aber nichts im Repo setzt `backtester.home`/`BACKTESTER_HOME`** (auch `start.bat` nicht) → Default-Failure-Mode besteht fort | NIEDRIG |
| X12 | GEFIXT_OK | logback 1.5.38, xz 1.10 (CVE-2022-29153), sqlite-jdbc 3.53.4.0; JUnit 4.13.2 bleibt (EOL) | NIEDRIG gefordertes Minimum übertroffen |
| X13 | GEFIXT_MANGELHAFT | Getrackter Junk komplett entfernt (pyc/MSI/tmp/stdout/stderr/zip); **Lücke: `Ergebnisse/` fehlt in `.gitignore`** → dauerhaftes `??` in `git status` | NIEDRIG, kosmetische Lücke |

- **X1-Residual:** `metatester64.exe` wird weiterhin maschinenweit per Image-Name
  forced-gekillt (`Main.java:196-206`) — beendet auch Tester-Agents anderer/fremder
  Installationen. Dokumentiert als nie live-trading, aber inkonsistent zum neuen
  pfad-gefilterten Ansatz.

### 1.5 Stichprobe Abschnitt 4 (Negativbefunde)

| # | Behauptung | Ergebnis |
|---|------------|----------|
| 1 | SQL: durchgängig PreparedStatement | **STIMMT** — 39× prepareStatement mit `?`; 15× createStatement nur mit Literalen; projektweit keine Query-Konkatenation mit Variablen |
| 2 | XXE-Härtung `OptimizationReportParser` | **STIMMT** — alle 6 Flags gesetzt (137-147) |
| 3 | Secrets-Handling | **STIMMT** — Key nur in User-Home-DB, PasswordField-Maskierung, kein Logging (Near-Miss: `LlmAnalysisService:205` loggt nur Modell), Nullung vor JSON (355-356), MCP-Blocker + zusätzlicher SQLite-Authorizer |
| 4 | EA_CONFIGS-Migration transaktional | **STIMMT** — und durch `6072d86` zusätzlich idempotent (NOT EXISTS) |
| 5 | JDBC try-with-resources | **STIMMT** — alle 54 Statement-Stellen inkl. stmt2/3/4 |
| 6 | valuesEquivalent BigDecimal / Score Locale.US | **TEILWEISE** — BigDecimal stimmt; aber `OptimizationCombinedPanel.java:270` und `PassExplanationDialog.java:132` formatieren Scores ohne Locale (auf de-System „78,50" neben Locale.US-Spalten); Default-Locale in `OptimizationResult.java:841,852,859`, `StrategyExporter.java:316,338` |
| 7 | Keine AnimationTimer/Timeline-Leaks | **STIMMT** — keine Treffer; nur selbstbeendende `PauseTransition`-Debounces |

Zusätzlich bestätigt: `CustomProjectSaveCoordinator` (Single-Writer/Debounce/Versionszähler)
und `SettingsView`-Shutdown-Kette korrekt; `LogView`-Cap vorhanden; `VirtualDesktopHelper.awaitProcess`,
`OptimizationRunner`-volatile-Kommentar und `MetaTraderRunLock` (fair, interruptible,
Owner-Clear) stimmen — siehe Detailberichte der Teil-Prüfungen.

## 2. Größter Einzelbefund: Dukascopy-Feature unerreichbar

Die D1/D4/D5/D8/D9/D14-Fixes (inkl. neuer Tests) liegen in Code, der in der laufenden App
**nicht erreicht werden kann**:

- `ui/javafx/DukascopyView.java` ist in `MainView.java:101` eingebunden, hat aber **null**
  `setOnAction`-Handler (Download/Scan/Convert/Import/Export/Cancel sind tote Buttons) und
  referenziert weder `DukascopyDownloader` noch `CsvConverter` (181 Z. reine UI-Hülle).
- Das Swing-`DukascopyPanel` mit der eigentlichen Logik wird nirgends instanziiert (U7-Rest).
- `CsvConverter.convertFull` hat keinen produktiven Aufrufer; kein CLI-Einstieg (`grep dukascopy cli/` → leer).

Entscheidung nötig: View verdrahten (Downloader+Converter in `Task`s, Broker-Zone aus
`AppConfig`) oder Feature bewusst zurückbauen. Solange nicht geschehen, sind sechs der
besten Fixes produktiv wirkungslos.

## 3. Neue, im Review verpasste Befunde (konsolidiert, priorisiert)

1. **Dukascopy-Pipeline unerreichbar** — s. Abschnitt 2 (`DukascopyView.java` gesamt, `DukascopyPanel.java:55`).
2. **`Mt5OptimizationImportService` (neuer Code aus `6072d86`) dreifach problematisch:**
   Import blockiert den FX-Thread (`ProjectWorkflowDatabankPanel.java:781→910,930`, kein `Task`);
   `Files.readString` lädt die **gesamte** Report-Datei, um 8 KB Header zu regexen
   (`:276-279,315-316`, doppelt); Temp-Snapshot-Dirs `mt5_opti_import_*` werden **nie
   gelöscht** (`:147`, Abbrüche in `ProjectWorkflowDatabankPanel:950-953,983-985` leaken garantiert).
3. **`OptimizationConfig.autoKillMt5` Default `true` im GUI** (`OptimizationConfig.java:105`;
   `OptimizationView.java:1976`, `RobustnessView.java:809`) → stilles Terminal-Killen ohne
   Rückfrage, siehe E12.
4. **`MultiBacktestView.java:1030-1056` — SQLite-Batch-Write auf dem FX-Thread** (durch den
   U5-Fix neu entstanden: `saveBatch` + `saveRun`-Schleife in `Platform.runLater`).
5. **`saveRun` still -1** (`DatabaseManager.java:414-434`; ignoriert in `BacktestRunner.java:299`,
   `WorkflowEngine.java:2194`) + 53 weitere log-and-return-`catch (SQLException)` (D3-Rest).
6. **`RobustnessRunner.periodMap` hält alle `OptimizationResult`s aller Shifts im Heap**
   (`:141,225,240,243`, E4-Rest).
7. **WorkflowEngine-Sensitivity-Publishing nur oberflächlich thread-sicher:** flache Kopie
   `new ArrayList<>(targets)`, Elemente werden danach weitermutiert (`WorkflowEngine.java:1511-1517`
   + `SensitivityRunner.java:232-505`); `saveState()` liest 6 volatile Felder ohne gemeinsamen
   Lock → Cross-Field-Tear (`:464-505`); `runStep4` gibt die interne Liste ohne defensive
   Kopie zurück (`:1551`).
8. **`ControllingView.java:1022,1110-1119`** — Preset-Resolve (Disk) pro Zeilen-Auswahl auf
   dem FX-Thread; **`:756-778`** — `refreshResults`-Tasks ohne Coalescing/Abbruch (Überhol-Race).
9. **`VirtualDesktopHelper.java:296-302`** — fehlender `STARTED_PID` → kommentarloser
   Fallback `startNormally` ohne Prüfung, ob der PS-Start doch erfolgreich war → möglicher
   MT5-Doppelstart.
10. **`toLowerCase()`-Restklasse** — `IniGenerator.java:47,135`, `Mt5LogTailer.java:312,353`
    (beeinträchtigt Critical-Failure-Erkennung), `SensitivityRunner.java:346`, `BacktestConfig.java:156`.
11. **Bi5/Dukascopy-Kanten:** `Bi5Decoder.decodeRange` löscht Cache-Datei bei **jeder**
    Exception inkl. Pfad-Parse-Fehlern (`:130-135`); `future.cancel(true)` kann Teil-/Leer-.bi5
    im Cache hinterlassen (`DukascopyDownloader.java:181` vs. `Files.write:279`).
12. **`Mt5OptimizationImportService.java:360-363`** — bereits archiviertes
    `expert-parameters.set` wird ohne Symbol/Period/Datum-Konsistenzprüfung vertraut →
    falsches Preset kann als „Original" gelten.
13. **`WorkflowFlowSummaryDialog.java:79-84`** — Sync-Aufbau vor `show()` mit O(Stufen ×
    komplette-Databank-Kopien) auf dem FX-Thread (`WorkflowFlowSummaryService.java:283,612`,
    `DatabankManager.java:191`).
14. **Konfig/Doku-Lücken:** neuer Schlüssel `dukascopy.broker.zone` fehlt in
    `config/backtester.properties.template`; `backtester.home` wird nirgends gesetzt (X11);
    `Ergebnisse/` fehlt in `.gitignore` (X13); `doc/project_documentation.md:256-261` enthält
    maschinenlokale Broker-Pfade (getrackt); `DbReader.java` liegt weiterhin lokal im Root.
15. **Score-Formatierung ohne Locale** — `OptimizationCombinedPanel.java:270`,
    `PassExplanationDialog.java:132` (Korrektur des Negativbefunds 6).

## 4. Top-5-Umsetzungsreihenfolge

1. **Dukascopy-Pipeline verdrahten oder entfernen** (Abschnitt 2) — entscheidet, ob sechs
   gefixte Befunde überhaupt wirksam werden. Danach `dukascopy.broker.zone` ins Template.
2. **`autoKillMt5`-Default auf `false`**, unattended-Pfade explizit `true` (3 kleine Edits) —
   verhindert stilles Killen von Terminals bei interaktiven Optimierungen (E12/N3).
3. **MT5-Opti-Import härten:** Import in `Task`, `readString` auf Header-Fenster begrenzen,
   Temp-Dirs im `finally`/bei Abbruch aufräumen, Preset-Trust mit Konsistenzprüfung (N2/N12).
4. **D3-Rest schließen:** `saveRun` (und die kritischsten der 53 Rest-Catches) auf
   boolean-Vertrag, Aufrufer escalieren — stiller Verlust von Run-History ist die teuerste
   Fehlerklasse in einem Backtester.
5. **FX-Thread-Blocker beseitigen:** `MultiBacktestView`-Batch-Save aus `runLater` heraus in
   den Worker-Teil verschieben (N4) und `ControllingView`-Preset-Resolve entprellen/hinterlegen (N8).

Danach: RobustnessRunner-Memory (N6), WorkflowEngine-Publishing-Details (N7), VirtualDesktopHelper-Doppelstart (N9).

## 5. Fix-Umsetzung 2026-08-29

Alle Fixes sind umgesetzt und durch `mvn test` (1255 Tests, 0 Fehler, JDK 25) validiert.

**JDK 25:** Temurin 25.0.4.1 per winget unter `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` installiert; `start.bat` sucht jetzt zusätzlich dort (vor JAVA_HOME-Fallback).

1. **Dukascopy-Pipeline verdrahtet** (statt entfernt): `DukascopyView.java` (komplett neu, ~680 Z.) — alle 6 Buttons mit Handlern, Download/Scan/Convert/Import/Export je in `javafx.concurrent.Task`, Progress-Bars, Cancel über `DukascopyDownloader.cancel()`, Broker-Zone aus `AppConfig.getDukascopyBrokerZone()` bis `CsvConverter` durchgereicht, Fehler als Alert + LogView. Tote Swing-Reste `ui/DukascopyPanel.java` + `ui/LogPanel.java` gelöscht.
2. **MT5-Opti-Import gehärtet** (`workflow/Mt5OptimizationImportService.java`, `ui/javafx/ProjectWorkflowDatabankPanel.java`): Import läuft im `Task` (Button deaktiviert, Banner-Status); `readHeaderProbe()` liest nur 256 KB mit BOM/UTF-16-Heuristik statt der ganzen Datei; Temp-Snapshot-Dirs werden bei jedem Fehler-/Abbruchpfad rekursiv gelöscht (Erfolg bleibt bewusst erhalten); `findOriginalPreset` prüft die archivierte `expert-parameters.set` auf Symbol/Period/Datum-Konsistenz (`archivedPresetMatchesReport`) bevor sie vertraut wird, ini-Pfad gewinnt zuerst.
3. **autoKillMt5-Vertrag** (`engine/OptimizationConfig.java`, `WorkflowEngine.java`, `cli/CliRunner.java`, `engine/SensitivityRunner.java`): Default jetzt `false` (GUI fragt nach); Workflow-Engine und CLI setzen explizit `true` bzw. Settings-Wert; Sensitivity-Sweeps erben den Wert der Basis-Config.
4. **DB-Persistenz-Verträge** (`database/DatabaseManager.java` + 15 Aufrufer in 7 Dateien): `saveRun`, `saveOptimizationState`, `saveBatch`, `saveEaParameterSettings`, `saveAutomaticReview` geben jetzt `boolean` zurück, Aufrufer escalieren (log.error + LogView); neue `findRunId(...)`-Abfrage für Aufrufer, die die Run-ID brauchen (BacktestView); 2 neue Failure-Path-Tests.
5. **FX-Thread-Entlastung:** `MultiBacktestView` (DB-Write im `done()` auf Worker + FX-Queue-Drain gegen CME), `ControllingView` (Preset-Resolve asynchron mit Generationszähler, `refreshResults`-Überhol-Schutz), `OptimizationView`/`ControllingView` (redundante `Platform.runLater` entfernt), `WorkflowFlowSummaryDialog` (Timeline/Stufen-Aufbau off-FX im Task, Dialog erscheint mit fertigen Daten), `ProjectWorkflowEditorView` (Flush-Hinweis vor Shutdown).
6. **Engine-Korrektheit:** `WorkflowEngine` (tiefe Sensitivity-Snapshots, atomares Feld-Snapshot in `saveState()`, defensive Kopie in `runStep4`), `RobustnessRunner` (Sweeps auf Top-50-Pässe je Period begrenzt, Konsum-Analyse: HTML + View brauchen Passes, deshalb Top-N statt Aggregate), `VirtualDesktopHelper` (kein Doppelstart mehr bei fehlender STARTED_PID — Neuling-Poll vor Fallback), `DukascopyDownloader` (Cache-Writes atomar über `.part`+`Files.move`), `Bi5Decoder` (Cache-Delete nur noch bei echten Lese-/Dekodier-Fehlern), `Main.java`+`Mt5ProcessGuard` (metatester64-Kill jetzt install-pfad-scoped statt maschinenweit).
7. **Locale-Hygiene:** `Locale.ROOT` in `IniGenerator`, `Mt5LogTailer`, `SensitivityRunner`, `BacktestConfig`; `Locale.US` für Score-Formate in `OptimizationCombinedPanel`, `PassExplanationDialog`, `OptimizationResult`, `StrategyExporter`.
8. **Hygiene:** `Ergebnisse/` in `.gitignore`; `doc/project_documentation.md` ohne maschinenlokale Broker-Pfade; `DbReader.java` gelöscht; `dukascopy.broker.zone` ins Properties-Template; `start.bat` mit `BACKTESTER_HOME`-Anker (X11) und Adoptium-JDK-Suche.

**Bewusst nicht umgesetzt (Wartbarkeitsschuld, kein Defekt):**
- **U6** — 4× duplizierter EA-Parameter-IO + doppelter Task-Execution-Switch: Vereinheitlichung ist ein verhaltensnaher Refactor (der `GLOBAL`-Speicherpfad in `MultiBacktestView` ist mutmaßlich Absicht), zu riskant für diesen Fix-Durchlauf.
- **U11** — God-Class-Split (`ProjectWorkflowEditorView` 4.289 Z. etc.): reiner Struktur-Refactor ohne Verhaltensgewinn; separat angehen.
