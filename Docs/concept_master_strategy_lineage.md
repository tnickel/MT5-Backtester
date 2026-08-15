# Konzept: Master-Strategie-Verlauf (Lineage-Monitoring)

Status: umgesetzt (Phasen 1–6)
Bezug: `Docs/concept_interactive_multistage_workflow.md`, Guided-Workflow ToTheMoon132,
`Docs/concept_b_cluster_linienbaum.md` (mehrere Cluster-Linien B1–B10; dieses Dokument misst die Master-Linie)

---

## 1. Problem

Beobachtung: Die Master-Strategie (die über die Stufen 01…11 fortlaufend verbesserte
Parameterkombination) wird gefühlt nicht besser.

Befund aus der Code-Analyse — die Beobachtung ist plausibel, und zwar aus vier Gründen,
von denen drei belegbar sind und einer strukturell ist:

### 1.1 Es gibt aktuell überhaupt keine vergleichbare Messgröße

Der angezeigte Score ist **laufrelativ**, nicht absolut:

```530:539:src/main/java/com/backtester/report/OptimizationResult.java
    public List<CombinedPass> buildCombinedPasses(boolean requireForward, ScoreWeights weights) {
        ...
        double fwTradesThreshold = computeFwTradesThreshold(forwardPasses);
```

Die Schwelle ist `median(fwTrades) / 2` **der jeweiligen Optimierung** und multipliziert
den Score:

```751:761:src/main/java/com/backtester/report/OptimizationResult.java
        if (fw != null && fwTradesThreshold > 0) {
            int n = fw.getTotalTrades();
            if (n < fwTradesThreshold) {
                double penalty = Math.max(0.5, (double) n / fwTradesThreshold);
                raw *= penalty;
```

Folge: Score 74 aus Stufe 02 und Score 71 aus Stufe 03 sind **nicht** direkt vergleichbar —
sie stammen aus unterschiedlichen Populationen mit unterschiedlicher Strafschwelle.
„Wird besser oder nicht“ lässt sich mit den heutigen Zahlen schlicht **nicht beantworten**.

### 1.2 Kein Monotonie-Schutz

Es existiert nirgends ein Vergleich „neues Stufenoptimum ≥ bisheriges Optimum“.
Die Kette ist ein Koordinatenabstieg: Stufe N optimiert nur ihre eigenen Zielparameter,
alle vorherigen sind fix (`Opt=N`). Das ist bewusst so — garantiert aber keine Verbesserung.

### 1.3 Der Filter zwischen den Stufen kann den Besten entfernen

Zwischen Optimizer (`gXX_raw`) und nächster Stufe liegt ein `PRE_FILTER` (`gXX_pick`)
mit harten Schwellen (`BT_TOTAL_TRADES ≥ 750`, `FW_PF ≥ 1.15`, `MAX_DD ≤ 12` …).
Bei `deleteFailed=true` landet der Score-Beste gar nicht in `_pick`, wenn er eine Schwelle
knapp reißt. Die Kette läuft dann auf einem schlechteren Ast weiter.

### 1.4 Resume-Skip prüft die Basis nicht

```335:352:src/main/java/com/backtester/ui/javafx/ProjectWorkflowPipelineRunner.java
        if (databankHasStrategies != null && databankHasStrategies.test(task.getTargetDatabank())) {
            return true;
        }
```

Skip-Entscheidung nur nach „Ziel-Databank hat Strategien“ — **nicht** danach, ob sich die
Parameter-Basis seit dem letzten Lauf geändert hat. Nach einem neuen Hand-Pick können also
veraltete Optimizer-Ergebnisse weiterverwendet werden.

---

## 2. Ziel

Zwei Dinge, getrennt zu betrachten:

| Ziel | Mittel |
|---|---|
| **A — Messen:** objektiv sehen, ob die Master-Strategie besser wird | Referenz-Backtest nach jedem Pick + Verlaufsliste (dieses Konzept, Teil 3–6) |
| **B — Sicherstellen:** dass sie besser werden *kann* | Champion-Carry-Over + Filter-Bypass + Skip-Invalidierung (Teil 7) |

Ohne A ist B nicht überprüfbar. Deshalb zuerst A.

---

## 3. Kernidee: Referenz-Backtest unter eingefrorenen Bedingungen

Nach jedem Hand-Pick (und im Automatikmodus nach jeder automatischen Übernahme) wird die
**adoptierte Parameterbasis** — also exakt die Master-Strategie in ihrem aktuellen Stand —
einmal als **Einzel-Backtest** gerechnet.

Entscheidend: Dieser Referenz-Backtest läuft **immer mit identischen Rahmenbedingungen**,
unabhängig von der Stufe:

- gleiches Symbol / Timeframe (Projekt-Default, z. B. AUDCAD M5)
- gleicher Zeitraum (fix konfiguriert, z. B. Dev-Range 2022-08-01 … 2025-08-01)
- gleiches Modelling (z. B. OHLC M1)
- gleiche Startbedingungen (Deposit, Leverage)

Nur so ist Eintrag N mit Eintrag N−1 vergleichbar. Der Optimizer-Score bleibt davon
unberührt — er ist weiterhin das Auswahlkriterium *innerhalb* einer Stufe, aber **nicht**
mehr das Fortschrittsmaß *über* Stufen.

**Gemessene Basis:** Nach der Übernahme kann `applyFilterGateRecommendation` ein `Use_*`-Gate
im Snapshot der Folgestufe erzwingen. Maßgeblich für Anzeige, Setfile, `measurementSignature`
und Messung ist deshalb ausschließlich der Snapshot der Folgestufe **nach** dieser
Erzwingung, nicht die Parameterkopie aus `AdoptionResult`. Sonst misst der Referenztest eine
Strategie, die der nächste Optimizer nie rechnet.

---

## 4. Datenmodell

Neu, im Projekt persistiert (kein DB-Schema-Wechsel nötig — `CUSTOM_PROJECTS.project_json`
serialisiert das Projekt bereits vollständig via Gson):

```
CustomProject
 └── masterStrategyLineage : List<MasterStrategyEntry>   // append-only, chronologisch
```

```java
class MasterStrategyEntry {
    int      sequence;            // 1, 2, 3 … fortlaufend
    String   stageTaskName;       // "03 Envelopes oben — Optimizer"
    String   sourceDatabank;      // "g02_cadence_pick"
    int      sourcePassNumber;    // gepickter Pass
    long     createdAt;

    // Was in diesem Schritt neu fixiert wurde (für die Verlaufsanzeige)
    List<String> adoptedChanges;              // "Env_Period: 5 → 9" (Kurzform)
    String       optimizedStageName;          // Stufe, die diese Parameter optimiert hat
    List<ParameterChange>    optimizedParameters;  // Name + alter + neuer Wert, auch unverändert
    List<ParameterChange>    additionalChanges;    // Rest der Preset-Übernahme
    List<OptimizationTarget> nextStageTargets;     // was die Folgestufe variiert (Wert + Raster)

    // Referenz-Backtest
    String   reportDirectory;     // backtest_reports/MasterStrategyReference_.../
    String   equityImagePath;     // BacktestReport.png (MT5)
    List<double[]> equityCurve;   // {tradeNr, equity, balance} aus ReportParser
    boolean  backtestSucceeded;   // + failureMessage, wenn MT5 nichts lieferte
    double   profit, profitFactor, maxDrawdownPercent, maxDrawdownAbsolute,
             recoveryFactor, sharpeRatio, expectedPayoff, finalBalance;
    int      totalTrades;
    String   setfileContent;      // vollständige Parameterbasis, reproduzierbar

    // Vergleich gegen den bisher besten Eintrag
    double   returnToDrawdown;    // profit / maxDrawdownAbsolute — das Bewertungsmaß
    int      comparedToSequence;
    double   deltaProfit, deltaReturnToDrawdown, deltaMaxDrawdownPercent;
    Verdict  verdict;             // BESSER | NEUTRAL | SCHLECHTER | UNBEKANNT
}
```

**Bewertungsmaß:** Profit allein belohnt mehr Risiko — 50 % mehr Gewinn bei doppeltem
Drawdown ist keine bessere Strategie. Deshalb entscheidet **Profit/Drawdown**; Änderungen
von weniger als 1 % gelten als Rauschen (`NEUTRAL`). Ab 1 % gilt die Änderung bereits als
relevant. Fehlt der Drawdown (0) oder ist eine
Zahl nicht endlich, wird kein Urteil erfunden, sondern `UNBEKANNT` gesetzt; solche Einträge
können auch nicht Bestwert werden. Bei einer Baseline von praktisch null gibt es keine
relative Skala — dort entscheidet die Richtung der Differenz. Verglichen wird gegen den
**besten** bisherigen Eintrag, nicht gegen den direkten Vorgänger.

**Vergleichskohorte:** Verglichen wird nur innerhalb identischer Referenzbedingungen
(`contextKey` aus Expert, Symbol, Timeframe, Zeitraum, Modell, Deposit, Currency, Hebel).
Wechselt das Projekt das Symbol, beginnt eine neue Kohorte statt eines sinnlosen Vergleichs;
der Trendchart blendet fremde Kohorten aus und sagt, wie viele es sind.

**Datenvolumen:** Die Equitykurve wird auf 1.500 Punkte gedeckelt (Anfang und Ende bleiben
erhalten). Ohne Deckel wachsen elf Einträge mit je mehreren tausend Trades ungebremst in das
Projekt-JSON.

**Wiederverwendung statt Neubau:**

- `BacktestRunner.runBacktest(BacktestConfig)` erzeugt bereits alle Artefakte
  (`report.htm`, `statistics.json`, `summary.txt`, `expert-parameters.set`,
  `BacktestReport*.png`).
- `ReportParser.parseTradeHistory()` liefert die Equity-Punkte.
- `PassPresetResolver` / `GuidedOptimizationService.AdoptionResult.getParameters()` liefern
  die exakte Parameterbasis.
- `StrategyBacktestArchive` ist **nicht** geeignet: es ist `upsert` pro Tab-Key, überschreibt
  also. Wir brauchen append-only. Deshalb eigene Liste.

---

## 5. Ablauf

```
Hand-Pick (Rechtsklick → Parameter-Basis übernehmen)
        │
        ├─ Diff-Dialog (alt → neu)  ── Abbruch ─► nichts passiert
        │        │ OK
        ├─ adoptPassParameters()  → Snapshot der nächsten Stufe (Opt=Y nur neue Ziele)
        │
        ├─ NEU: MasterStrategyLineageService.recordAfterAdoption(...)
        │        ├─ BacktestConfig aus adoptiertem Snapshot + Referenz-Rahmen bauen
        │        ├─ BacktestRunner.runBacktest()      (asynchron, Progress im Log)
        │        ├─ Ergebnis + Equity + PNG-Pfad + Deltas → MasterStrategyEntry
        │        └─ project.masterStrategyLineage.add(entry) ; saveProject()
        │
        └─ Verlauf-Fenster aktualisieren (falls offen)
```

**Automatikmodus:** identischer Hook in `adoptBestPassAutomatically(...)`, ohne Dialog.
Die Pipeline wartet auf den Referenz-Backtest, bevor die nächste Stufe startet — sonst
konkurrieren zwei MT5-Terminals um dieselbe Installation.

**Die Messung entscheidet, nicht die Schätzung.** Der Master wird nur besser, nie
schlechter. Das wird an zwei Stellen durchgesetzt:

1. *Vor* der Messung wählt der geschätzte Profit/DD aus dem Optimizer-Report aus, **welcher**
   Kandidat überhaupt gemessen wird. Liegt schon der beste Kandidat unter der Master-Basis,
   wird die Stufe sofort verworfen und die bisherige Basis unverändert weitergereicht
   (Teil 8, Punkt 3). Gemessen wird dann nichts, weil sich nichts geändert hat.
2. *Nach* der Messung entscheidet der Referenz-Backtest, ob die Übernahme bestehen bleibt.
   Bestätigt ist nur das Verdikt `BESSER`. `NEUTRAL` bedeutet eine Abweichung von weniger
   als einem Prozent, `UNBEKANNT` heißt, dass sich Profit/DD nicht vergleichen ließ — beides ist
   kein Beleg für eine Verbesserung. Nur die allererste Messung ist eine Ausnahme: Sie hat
   nichts, womit sie sich vergleichen könnte, meldet deshalb ebenfalls `UNBEKANNT` und
   begründet den Master, statt gegen einen noch nicht existierenden Master verworfen zu
   werden. Ob es schon einen Master gibt, entscheidet allein `provenMasterParameters` und
   *nicht* der Zustand des Verlaufs: Auch ein leerer oder durchgängig unbewertbarer Verlauf
   meldet `UNBEKANNT` ohne Anker, und das als Erstmessung zu lesen hieße, dass jeder
   beliebige Kandidat einen bestätigten Master ohne einen einzigen Vergleich überschreibt.
   Auch die Erstmessung muss bewertbar sein — ein Lauf ohne endliches Profit/DD (etwa bei
   Drawdown 0) belegt nichts und wäre als Master für nichts mehr ein Vergleichsmaßstab.
   Diese Unterscheidung trifft `confirmsImprovement(...)`.
   Ohne Bestätigung setzt `restoreProvenMasterBasis(...)` Snapshot und EA-Parameter auf den
   zuletzt bestätigten Master zurück; die nächste Stufe startet von genau den Parametern,
   die vorher in Kraft waren, nur mit ihren eigenen Zielparametern geöffnet.

**Wo der bestätigte Master steht.** In `CustomProject.provenMasterParameters`, geschrieben
ausschließlich von `commitProvenMaster(...)` nach einer bestätigten Messung. Das ist die
einzige Quelle der Wahrheit für „worauf fallen wir zurück“, und zwar aus zwei Gründen.
Erstens taugen die Stufen-Snapshots dafür nicht: Die Factory befüllt jeden Optimizer vorab
mit dem Ausgangspreset (`ToTheMoon132GuidedWorkflowFactory`), eine noch nicht übernommene
Stufe trägt also die Startwerte der Kette und nicht die inzwischen bewiesenen. Ein Rückfall
auf diesen Snapshot würde alle bisherigen Verbesserungen verwerfen. Zweitens überlebt ein
persistiertes Feld den Absturz während des minutenlangen Referenzlaufs — der unbestätigte
Kandidat steht zu diesem Zeitpunkt bereits im Task-Snapshot.

Aus demselben Grund wird auch die Profit/DD-Untergrenze erst nach einer bestätigten Messung
angehoben. Gespeichert wird dabei das Profit/DD des Referenzlaufs, nicht die vorherige
Optimizer-Schätzung. Andernfalls könnte eine zu optimistische Schätzung spätere, tatsächlich
bessere Kandidaten bereits vor ihrer Messung blockieren. Weil die Untergrenze nur bei
Bestätigung steigt, muss sie beim Rückfall auch nicht zurückgesetzt werden.

Beim Weiterreichen gilt: Werte kommen aus dem bewiesenen Master, Suchbänder aus der Vorlage
der Zielstufe. `carryBasisToNextOptimizer(...)` übergibt die bewiesene Basis deshalb als
geerbte Werte und nicht bloß als Ersatzvorlage. Übertragen werden dabei nur Parameter, die
es auf beiden Seiten gibt. Weicht das Schema ab — ein Master-Parameter fehlt in der Stufe
oder die Stufe kennt einen Parameter, den der Master nicht hat —, entsteht eine Mischung, die
in dieser Form nie gemessen wurde. Das bricht die Kette nicht ab, wird aber mit den
betroffenen Parameternamen in die Laufkonsole geschrieben (`AUTOMATIK-WARNUNG`), damit es
nicht unbemerkt bleibt. Der Rückfall geht denselben Weg: Er stellt
zuerst den Stufen-Snapshot von *vor* der Übernahme wieder her und überlagert ihn dann mit
den Master-Werten. Würde er den Master direkt in den Snapshot schreiben, wäre der Master
auch die Vorlage — und weil eine gemessene Basis nur das Band trägt, an dem sie gemessen
wurde, käme die Stufe mit einem auf einen einzigen Punkt zusammengeschrumpften Suchraum aus
dem Rückfall heraus.

**Master, Untergrenze und Referenzbedingungen sind eine Einheit.** Eine Messung bedeutet nur
etwas zusammen mit Symbol, Zeitrahmen und Expert, unter denen sie lief. Deshalb speichert
`commitProvenMaster(...)` alle drei gemeinsam (`provenMasterContextKey`), und alle drei
werden gemeinsam verworfen: beim Leeren des Verlaufs (`MasterStrategyLineageService.clear`)
und beim Wechsel der Referenzbedingungen (`rebaselineOnContextChange`). Sonst bliebe eine
Untergrenze zurück, die jeden Kandidaten blockiert, bevor unter den neuen Bedingungen
überhaupt eine Basis gemessen wurde — oder ein Master ohne Messung dahinter, den der nächste
Kandidat als vermeintliche Erstmessung ohne Vergleich überschreibt.

Der Commit wird zusätzlich sofort auf die Platte gezwungen (`flush`) statt dem verzögerten
Speichern überlassen: Die nächste Stufe lässt MT5 minutenlang laufen, und ein Absturz darin
würde genau die eben erarbeitete Bestätigung verlieren.

**Projekte aus der Vorversion** haben Verlauf und Untergrenze, aber keinen gespeicherten
Master. Da jede erfolgreiche Messung ihr Preset mitspeichert, ist der beste Eintrag unter den
aktuellen Referenzbedingungen genau diese bewiesene Basis; sie wird beim ersten Bedarf einmalig
daraus rekonstruiert (`recoverProvenMasterFromLineage`). Gelingt das nicht, gibt es eben
keinen bestätigten Master — dann ist die nächste bewertbare Messung die neue Grundlinie.

**Ohne Referenz-Backtest gibt es keine Bestätigung.** Ist die Messung abgeschaltet, läuft die
Kette bewusst ungesichert: Die Basis wird weitergereicht, aber weder als Master hinterlegt
noch die Untergrenze angehoben, damit später nichts wie gemessen aussieht, was nie gemessen
wurde. Das wird einmal pro Übernahme deutlich ins Protokoll geschrieben.

Der verworfene Messpunkt bleibt im Verlauf stehen: Er ist der Beleg dafür, was probiert
wurde. Vergleichsanker ist immer der **beste** Eintrag, ein schlechterer verfälscht also
keinen späteren Vergleich.

**Gemessene Verschlechterung und fehlgeschlagene Messung werden verschieden behandelt.**
Ein Rückschritt ist ein *Ergebnis*: Die Basis fällt zurück, die Kette läuft weiter, denn die
nächste Stufe optimiert andere Parameter und bekommt so die Chance, den Rückschritt
einzuholen. Eine Messung ohne Ergebnis — MT5 abgestürzt, kein Report — ist dagegen ein
*Fehler*, den jemand ansehen muss. Auch dort wird zuerst auf den bewiesenen Master
zurückgesetzt, damit keine halb übernommene Basis liegen bleibt; anschließend hält eine
`WorkflowPauseException` die Kette an. Ein erneuter Start wiederholt Übernahme und Messung.

**Bereits gemessene Basen:** Jeder Eintrag trägt eine `measurementSignature` (SHA-256 über
alle Parameterwerte **plus** den vollständigen Referenzkontext). Ist die Basis bereits der
neueste **erfolgreiche** Messpunkt, wird nicht noch einmal gemessen — aber das damalige
Verdikt gilt weiter. Sonst würde ein Neustart eine einmal verworfene Basis als „nichts mehr
zu messen“ durchwinken. Fehlgeschlagene Messungen zählen nicht als Bestätigung und werden
beim nächsten Lauf wiederholt.

**Hand-Pick:** Dort greift der Rückfall bewusst nicht. Wer einen Pass von Hand übernimmt,
hat sich entschieden; die Messung meldet das Ergebnis im Banner, setzt aber nichts zurück.

**Terminal-Exklusivität:** Optimizer, Einzelstep und Referenzlauf holen sich vorher
`MetaTraderRunLock`. Der Lock ist prozessweit und reentrant: Der Referenzlauf im
Automatikmodus läuft auf dem Pipeline-Thread, der ihn bereits hält, ein manueller
Referenzlauf wartet dagegen, bis die Pipeline fertig ist. Ohne das killen sich die Läufe
gegenseitig die Terminals und überschreiben `tester.ini` und Reportdatei.

**Serielle Messaufträge:** Manuelle Referenzläufe laufen über einen Single-Thread-Executor.
Ein zweiter Pick während einer laufenden Messung wird danach gemessen statt verworfen. Der
Auftrag hält das Projekt, für das er gestartet wurde; wird zwischenzeitlich ein anderes
Projekt geladen, wird der Messpunkt verworfen statt in das falsche Projekt geschrieben.
Gespeichert wird direkt aus dem Worker (nicht per `Platform.runLater`), sonst geht das
Ergebnis beim Schließen der App verloren. Beim Shutdown wird ein laufender Referenzlauf
abgebrochen — auf einen mehrminütigen MT5-Lauf zu warten wäre schlimmer als diese eine
Messung zu verlieren.

**Kosten:** ein zusätzlicher Backtest pro Stufe. Bei 11 Stufen und ~1–3 min pro
OHLC-M1-Lauf über 3 Jahre sind das ~15–35 min pro Gesamtdurchlauf. Deshalb:
Schalter `Referenz-Backtest nach Hand-Pick` (Default: an) in den Projekteinstellungen.

---

## 6. UI: „Master-Strategie-Verlauf“

Eigenes, nicht-modales Fenster (analog `OptimizerSettingsHighlightDialog`), erreichbar über
Button in der Workflow-Toolbar und Kontextmenü.

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Master-Strategie-Verlauf — ToTheMoon132 AUDCAD M5                       │
│ Referenz: 2022-08-01 … 2025-08-01 · OHLC M1 · 11 Einträge               │
├──────────────┬──────────────────────────────┬───────────────────────────┤
│ Einträge     │ Equitykurve                  │ Kennzahlen                │
│              │                              │                           │
│ #01 g01 ▲    │      ╱╲    ╱╲╱               │ Profit      12.430  ▲ +8% │
│ #02 g02 ▲    │    ╱╲╱  ╲╱╱                  │ Profitfaktor  1.42  ▲     │
│ #03 g03 ▼    │  ╱╲╱                         │ Max DD %      8.9   ▼     │
│ #04 g04 ▲    │ ╱                            │ Trades        1.284       │
│ ...          │                              │ Sharpe        0.94        │
│              │ [MT5-PNG] [Eigener Chart]    │ Recovery      2.10        │
│              │                              │ ───────────────────────── │
│              │                              │ Geändert in #03:          │
│              │                              │  TimeFrame_Env  M1 → M15  │
│              │                              │  Env_Period     5  → 9    │
└──────────────┴──────────────────────────────┴───────────────────────────┘
```

- **Links:** chronologische Liste, Zeile eingefärbt nach `verdict` (grün besser, gelb
  neutral, rot schlechter), darunter ein Trendchart Profit/Drawdown über alle Picks —
  die eigentliche Antwort auf „wird sie besser?“ in einem Bild.
- **Mitte:** Equitykurve als JavaFX-`LineChart` aus `equityCurve` (robust, auch wenn der
  Report-Ordner verschoben wurde) und darunter das MT5-`BacktestReport.png`, sofern noch
  vorhanden.
- **Rechts:** Kennzahlen mit Delta-Pfeil gegen den **bisher besten** Eintrag (nicht gegen
  den direkten Vorgänger — sonst kaschieren zwei kleine Rückschritte einen großen), Herkunft
  des Picks und drei Parameterblöcke:
  - *Optimierte Parameter* — was die vorherige Stufe variiert hat, je Zeile
    `Parameter · vorher · nachher`. Unverändert gebliebene Ziele bleiben stehen (grau
    statt grün): „optimiert und beibehalten“ ist ein Ergebnis, keine Lücke.
  - *Weitere übernommene Werte* — der Rest der Preset-Übernahme, getrennt gelistet, damit
    er die eigentliche Stufenentscheidung nicht überdeckt.
  - *Nächste Stufe variiert* — Zielparameter der Folgestufe mit Startwert und Suchraster,
    also was der nächste Schritt überhaupt verändern kann.
- Kopfzeile: Schalter „Referenz-Backtest nach jedem Pick“ (Default an).

Vorlagen im Bestand: `ExpressStrategyDetailDialog` (SplitPane Chart links / Details rechts),
`DatabankEquityGalleryDialog.createEquityLineChart()`, `StrategyDetailsModalDialog`.

---

## 7. Damit sie tatsächlich besser wird — umgesetzt

Das Monitoring zeigt nur an. Die Verbesserungsgarantie brauchte drei Regeln; alle drei sind
implementiert.

### 7.1 Champion-Carry-Over (wichtigster Hebel) — `ChampionSearchSpaceAligner`

Regel: **Der aktuelle Wert eines Zielparameters muss im Suchraster der Stufe liegen.**

Ist `X = 5` und optimiert Stufe N den Parameter `X`, dann muss `5` in `Start/Step/Stop`
vorkommen. Dann ist die Champion-Konfiguration selbst einer der Passes, und das Stufenoptimum
kann in-sample nicht schlechter sein als der Champion. Liegt `5` nicht im Raster
(z. B. Raster 3/6/9/12), kann die Stufe **strukturell verschlechtern**.

Zwei Korrekturen, beide unter Beibehaltung der Schrittweite:

- **Rasterphase verschieben**, wenn der Wert im Band, aber zwischen zwei Gitterpunkten liegt
  (Start sinkt um weniger als einen Schritt: 0.005/0.005/0.030 bei Wert 0.007 → Start 0.002).
- **Band erweitern**, wenn der Wert knapp außerhalb liegt (30/2.5/50 bei Wert 25 → Start 25).

Grenze: maximal 10 Schritte Erweiterung. Weiter außen widersprechen sich Band und Wert;
dann wird **nicht** stillschweigend gedehnt, sondern der Konflikt gemeldet
(`SKIPPED_TOO_FAR`) — ein aufgerissener Suchraum wäre ein anderes Experiment, kein Schutz.
`PERIOD_CURRENT` wird nie in ein Band zurückgeholt (MT5 lässt dort den Schritt fallen) und
stattdessen als unerreichbar gemeldet; Timeframes rechnen auf ENUM-Positionen, nicht auf den
nichtlinearen Zahlencodes.

Angewandt beim Stufen-Snapshot-Bau (`buildStageSnapshot`) und bei jeder Übernahme
(`prepareAdoption`). Der Repair-Check vergleicht gegen das *ausgerichtete* Band, sonst würde
er jede Stufe bei jedem Projektladen als defekt einstufen und neu bauen.

### 7.2 Filter-Transparenz — `FilterRejectionReport`

Verwirft der `PRE_FILTER` den Score-Besten, steht das jetzt im Log und am Task:
„Filter hat den Score-Besten verworfen: Pass #123 (Score 78.4). Grund: Profit factor
(Forward / OOS) = 1.14 verletzt >= 1.15. Übernommen wird stattdessen Pass #98 (Score 71.2,
−7.2).“ Die Filterentscheidung selbst bleibt unangetastet — sichtbar ist wichtiger als
automatisch überstimmt.

### 7.4 Auswahlregel: Score entscheidet die Vorauswahl, Profit/DD die Übernahme

Belegt am Lauf vom 2026-08-12, Stufe 03: Der genetische Algorithmus hat 174 von mindestens
26.208 Kombinationen gerechnet und die Champion-Kombination **nie getestet** (Prüfung im
`optimization_report.xml`: null Treffer). Zusätzlich zeigten drei verschiedene Zielgrößen in
eine unterschiedliche Richtung — MT5 optimiert das Complex Criterion, der Picker rankt nach
Unified Score, gemessen wird Profit/Drawdown. Der gepickte Pass #67 lag mit geschätztem
Profit/DD 7,73 weit unter der Basis (11,79) und war nicht einmal unter den fünf
profitabelsten der 174.

Regel seit diesem Befund:

1. Der Score bildet eine Shortlist (`ADOPTION_SHORTLIST`, 10 Pässe). Damit bleiben die
   Qualitätsanforderungen des Scores — Tradezahl, Forward-Konsistenz, Profitfaktor —
   wirksam.
2. Innerhalb der Shortlist gewinnt das beste Profit/Drawdown. Geschätzt aus dem
   Optimizer-Report: Der Recovery-Faktor ist Profit geteilt durch absoluten Drawdown, also
   ist der Drawdown je Segment daraus rekonstruierbar. Backtest- und Forward-Profit werden
   addiert, die Drawdowns nicht — es zählt der größere.
3. Liegt der beste Shortlist-Wert unter dem Profit/DD der aktuellen Master-Basis
   (`CustomProject.masterSelectionRatio`), bleibt `AdoptionChoice.getSelected()` leer —
   kein Aufrufer übernimmt versehentlich eine Verschlechterung. Der Automatikmodus
   **verwirft die Stufe** und reicht die bisherige Basis unverändert weiter
   (`carryBasisToNextOptimizer`): Es wird kein Passwert übernommen, kein Filter-Gate
   erzwungen und der Floor bleibt stehen; geöffnet werden nur die Zielparameter der
   Folgestufe. Der abgelehnte Pass bleibt über `getBestAvailable()` erreichbar, damit das
   Log ihn benennen kann. Ohne diese Regel würde der Floor mit jeder übernommenen
   Verschlechterung mitsinken und der Verlust über die Stufen kumulieren — 8 × „nur 5 %
   schlechter“ sind noch 66 % der Ausgangsqualität, ohne dass je eine Warnung greift.
4. Ist Profit/DD für keinen Shortlist-Pass bestimmbar, entscheidet der Score. Existiert
   dabei bereits eine Master-Basis, meldet `isMasterFloorUnverified()` diesen Fall: Die
   Übernahme ist weder bestätigt noch abgelehnt, und der Referenz-Backtest ist der einzige
   Beleg für diese Stufe.

Warum nicht einfach nur nach Profit/DD ranken: Der Referenz-Backtest deckt denselben
Zeitraum ab, den der Optimizer gesehen hat. Würde der Picker exakt die Größe maximieren, die
der Monitor misst, wäre der Monitor kein unabhängiger Prüfer mehr, sondern die Zielfunktion —
und stünde per Konstruktion fast immer auf „BESSER“.

Beim Hand-Pick bleibt die Entscheidung beim Nutzer; liegt der gewählte Pass unter der
aktuellen Basis, steht das als Warnung im Bestätigungsdialog.

Grenze der Regel: Sie kann nur wählen, was der genetische Algorithmus überhaupt gerechnet
hat. Findet eine Stufe nichts über der Schwelle, entscheidet der Modus: Im Automatikmodus
läuft die Kette mit dem besten verfügbaren Pass weiter (Warnung im Log, roter Messpunkt im
Verlauf), beim Hand-Pick warnt der Bestätigungsdialog und die Entscheidung bleibt beim
Nutzer.

### 7.3 Skip-Invalidierung nach Adoption

Jede Übernahme leert die Databanks der Zielstufe und aller nachgelagerten Stufen und setzt
deren Status auf PENDING. Sonst greift `shouldReuseExistingTaskResult` — das nur prüft, ob
die Ziel-Databank Strategien enthält — und die Folgestufe rechnet mit der neuen Basis nie neu.
Der Hand-Pick-Dialog nennt die betroffenen Databanks vor der Bestätigung.

---

## 8. Umsetzung in Phasen

| Phase | Inhalt | Aufwand |
|---|---|---|
| ~~1~~ | Datenmodell `MasterStrategyEntry` + Persistenz im Projekt-JSON | **erledigt** |
| ~~2~~ | `MasterStrategyLineageService`: Referenz-Backtest nach Adoption, Ergebnis + Equity + PNG erfassen | **erledigt** |
| ~~3~~ | Verlauf-Fenster `MasterStrategyLineageWindow` (Liste / Chart / Kennzahlen + Deltas) | **erledigt** |
| ~~4~~ | Automatikmodus-Hook + Projektschalter `referenceBacktestEnabled` | **erledigt** |
| ~~5~~ | Champion-Carry-Over-Validierung (7.1) | **erledigt** |
| ~~6~~ | Filter-Transparenz + Skip-Invalidierung (7.2, 7.3) | **erledigt** |

Abweichungen vom Entwurf: Die Kennzahlen liegen flach im Entry statt als `Pass`-Objekt
(Gson-freundlich, keine Kopplung an das Optimizer-Modell), `adoptedChanges` sind fertige
Textzeilen, und die Lineage wird auch dann persistiert, wenn Databank-Inhalte nicht
gespeichert werden — sie ist der einzige Nachweis über den Verlauf.

---

## 8a. Bewusst nicht umgesetzt

- **Overlay-Modus** (mehrere Equitykurven übereinander) — der Trendchart Profit/Drawdown
  beantwortet dieselbe Frage kompakter.
- **Kontextmenü-Eintrag** zum Öffnen des Verlaufs; nur Toolbar-Button.
- **Kein End-to-End-Lauf gegen MT5.** Die Bewertungs-, Signatur- und Persistenzlogik ist
  durch Unit-Tests abgedeckt; dass MT5 den Referenzlauf tatsächlich wie erwartet ausführt,
  zeigt erst der erste echte Pick.
- **Kein prozessübergreifender Terminal-Lock.** `MetaTraderRunLock` schützt innerhalb einer
  laufenden Anwendung. Zwei parallel gestartete Instanzen des Backtesters auf derselben
  MT5-Installation können sich weiterhin stören.
- **Kein Projektwechsel-Schutz.** Wird während eines manuellen Referenzlaufs ein anderes
  Projekt geladen, wird der Messpunkt verworfen (mit Logeintrag) statt in ein Projekt zu
  wandern, in das er nicht gehört.
- **`DatePicker.getValue()` im Pipeline-Worker** (`applyTaskExecutionConfig`) ist Altcode und
  bleibt vorerst: JavaFX-Lesezugriff außerhalb des FX-Threads, unabhängig von diesem Feature.

---

## 9. Getroffene Entscheidungen

1. **Referenz-Backtest:** Dev-Range (2022-08-01 … 2025-08-01, inkl. Forward-Fenster),
   Modelling **OHLC M1**. Every Tick bleibt den Retest-Stufen 13/14 vorbehalten.
2. **Verschlechterung im Automatikmodus:** Kette **weiterlaufen lassen**, aber nie auf
   einer schlechteren Basis. Der Master bewegt sich nur vorwärts: Was die Messung nicht als
   Verbesserung bestätigt, wird verworfen, und die Kette macht mit der zuletzt bestätigten
   Basis bei der nächsten Stufe weiter. Zwei Varianten wurden probiert und wieder entfernt.
   *Anhalten* parkte den Automatikmodus regelmäßig, ohne etwas zu verbessern — die folgende
   Stufe optimiert andere Parameter und kann den Rückschritt einholen. *Trotzdem übernehmen
   und nur warnen* senkte die Untergrenze mit ab, wodurch die Verluste über die Stufen
   kumulierten, ohne dass je eine Warnung greift. Eine Messung **ohne Ergebnis** setzt
   ebenfalls auf den bewiesenen Master zurück, hält die Kette danach aber an: Ein
   MT5-Absturz ist kein Messergebnis, sondern ein Fehler, der untersucht werden soll.
3. **Historie:** pro Projekt.
4. **Reihenfolge:** Reparatur (Abschnitt 7) zuerst, danach Monitoring (Phasen 1–4).
