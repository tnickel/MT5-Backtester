# Anti-Curve-Fitting-Methodik des Backtesters

Dieses Dokument beschreibt, wie die Workflow-Pipeline gegen Überanpassung
(Curve-Fitting) schützt, welche Kennzahlen wirklich gemessen werden, und wo
die bekannten Grenzen liegen.

Stand: Juli 2026 (nach dem Umbau "Score-Bereinigung + Step 7 Validierung").

---

## 1. Die Workflow-Pipeline (7 Schritte)

| Schritt | Name | Zweck | Anti-Curvefitting-Beitrag |
|---|---|---|---|
| 1 | Strategie-Auswahl | EA, Symbol, Zeitraum, Parameterbereiche | — |
| 2 | MT5 Optimizer | Genetische/vollständige Parametersuche mit **Forward-Split** | Forward-Test (Out-of-Sample innerhalb der Optimierung) |
| 3 | Diversitäts-Filter | Top-Strategien nach Unified Score, aber **divers** | Verhindert, dass 5 Nachbarn desselben Overfitting-Peaks gewählt werden |
| 4 | Robustness Test (CV) | Parameter-Sweeps (±2 Schritte) getrennt auf BT- und FW-Fenster | Erkennt spitze Parameter-Peaks (CV = Variationskoeffizient) |
| 5 | KI-Bewertung | LLM bewertet Kennlinien-Formen (Plateau/Peak/Cliff) | Mustererkennung auf den Sensitivitätskurven |
| 6 | Portfolio Export | Finale 3–5 Strategien, gewichteter Score + KI-Gate | KI-Score < 30 wird gefiltert; Fallback wird **sichtbar markiert** |
| 7 | **Validierung (OOS)** | Backtest der finalen Strategien auf **unberührten Daten** | Einzige echte Out-of-Sample-Schätzung nach der Selektion |

### Warum Schritt 7 nötig ist (Selection Bias)

Das Forward-Fenster aus Schritt 2 ist zwar Out-of-Sample für die
*Optimierung* — aber die Schritte 3–6 **selektieren** nach
Forward-Kennzahlen (FW-Profit, FW-Trades, FW/BT-Konsistenz, FW-Recovery).
Wer aus tausenden Pässen die besten nach Forward-Performance auswählt,
verbraucht das Forward-Fenster: Bei genügend Kandidaten sehen einige dort
rein zufällig gut aus (Multiple-Testing-Problem).

Schritt 7 testet deshalb jede finale Strategie per Einzel-Backtest auf einem
Zeitfenster, das **weder Optimierung noch Selektion je gesehen hat**:

- Standard-Fenster: `Optimierungs-Enddatum + 1 Tag` bis `heute`.
- Konfigurierbar über den Schritt-7-Dialog; eine Überlappung mit dem
  Optimierungszeitraum wird hart abgelehnt
  (`WorkflowEngine.runStep7`).
- Verdikte: `PASSED` (Profit > 0), `FAILED` (Verlust), `NO_TRADES`, `ERROR`.
- Ergebnisse landen in `VALIDIERUNGS_REPORT.txt` im Export-Ordner;
  Strategien mit `FAILED` bekommen im "Best"-Ordner eine Warndatei und
  werden bei erneutem Export nicht mehr dorthin kopiert.

**Praktische Konsequenz:** Lege das Optimierungs-Enddatum bewusst in die
Vergangenheit (z. B. 3 Monate), damit unberührte Daten für die Validierung
übrig bleiben. Endet die Optimierung "heute", wird Schritt 7 im
Batch-Workflow mit Hinweis übersprungen.

---

## 2. Der Unified Score: 8 Säulen, nur echte Messdaten

Berechnung: `OptimizationResult.computeScore` (Ranking in Schritt 3) und
`RobustnessScorecardGenerator.computePillars` (Scorecard-Anzeige) — beide
nutzen dieselben Normalisierungskurven.

| # | Säule | Datenquelle (alles MT5-Report) | DB-Key |
|---|---|---|---|
| 1 | BT-Profitabilität | ROI + Profit Factor (Backtest) | `opt.weight.btProfit` |
| 2 | FW-Profitabilität | ROI + Profit Factor (Forward) | `opt.weight.fwProfit` |
| 3 | FW/BT-Konsistenz | Profit- und Recovery-Verhältnis FW/BT | `opt.weight.consistency` |
| 4 | Risiko-Verhältnis | Recovery-Faktor + Calmar (mit **realen Testjahren**) | `opt.weight.risk` |
| 5 | Sharpe Ratio | Von MT5 gemessene Sharpe Ratio (BT + FW) | `opt.weight.equityConsist` |
| 6 | Stichprobengröße | Trades + **reale Testjahre** aus dem Datumsbereich | `opt.weight.sampleSize` |
| 7 | FW Trade Count | Trades im Forward-Fenster | `opt.weight.fwTrades` |
| 8 | Erholungsfaktor | Recovery BT + FW | `opt.weight.recovery` |

Zusätzlich: multiplikative Strafe (max. −50 %), wenn die FW-Trade-Anzahl
unter `median/2` aller Pässe liegt.

### Entfernte Säulen (Transparenz)

Die folgenden früheren Säulen wurden im Juli 2026 **entfernt**, weil sie
nicht auf Messdaten basierten und dem Score Schein-Information hinzufügten:

| Entfernte Säule | Problem |
|---|---|
| Equity-Konsistenz (R²) | R² einer **synthetisch generierten** Equity-Kurve mit `Random(passNumber…)` — der Wert war Pseudo-Zufall und hing von der Pass-Nummer ab. Zwei identische Strategien bekamen unterschiedliche "Stabilität". |
| SQN | Aus Profit/Trades/PF mit angenommener 55%-Winrate abgeleitet — deterministisch redundant, kein neues Signal. |
| Symmetrie L/S | Hart kodiert auf 0.80 — konstant für jeden Pass, reines Totgewicht. Die Scorecard zeigte "0.80 / 50/50" an, als wäre es gemessen. |
| Tail-Risk | `maxLoss = avgLoss · 2.8` angenommen — synthetisch. |

Ersatz für die Equity-Säule ist die **echte Sharpe Ratio**, die MT5 pro Pass
liefert (Skala: 0 → 0 Punkte, 0.5 → 50, 2.0 → 100; siehe
`OptimizationResult.SHARPE_PWL`).

### Eine einzige Quelle für Gewichts-Defaults

Vorher gab es **fünf** divergierende Default-Sätze (ScoreWeights-Klasse,
WorkflowEngine, Scorecard-Generator ×2, UI-Reset-Button) — je nach Codepfad
rankte Schritt 3 mit anderen Gewichten, als die Scorecard anzeigte.

Jetzt gilt: `OptimizationResult.ScoreWeights` ist die **einzige Quelle**.
Alle Verbraucher (Workflow-Ranking, Scorecard, UI-Dialoge, Reset-Buttons)
lesen über `ScoreWeights.defaults()` bzw. `ScoreWeights.loadFromDatabase()`.
Ein Test (`WorkflowValidationAndGateTest.testScoreWeightsLoadFromDatabaseUsesClassDefaults`)
sichert das ab.

Default-Gewichte: BT-Profit 15 · FW-Profit 15 · Konsistenz 10 · Risiko 10 ·
Sharpe 10 · Stichprobe 25 · FW-Trades 30 · Recovery 25 (Summe wird
normalisiert, muss nicht 100 ergeben).

---

## 3. Sensitivitätsanalyse (Schritt 4)

- Jeder optimierte Parameter wird isoliert gesweept: ±2 Optimierungsschritte
  um den Bestwert (bzw. ±5 % ohne definierten Schritt).
- Der Sweep läuft **getrennt** auf dem BT-Fenster und dem FW-Fenster; der
  Datums-Split spiegelt MT5s Forward-Split exakt — die Logik ist in
  `ForwardSplit` isoliert und durch `ForwardSplitTest` eingefroren
  (drift = FW-Sensitivität auf falschem Fenster = wertlose Aussage).
- Kennzahl: CV = StdDev(Profit) / |BaseProfit| · 100, gecappt bei 200 %.
  Verdikte: ROBUST < 30 % ≤ ACCEPTABLE ≤ 60 % < FRAGILE.
- Aggregation pro Strategie: **Worst-Case** über alle Parameter
  (`SensitivityResult.computeWorstCase`) — eine Strategie ist nur so robust
  wie ihr schwächster Parameter.

**Bewusste Design-Entscheidung (dokumentierte Grenze):** Der Sweep-Bereich
wurde historisch von ±5 auf ±2 Schritte verengt. Ein Overfitting-Peak, der
breiter als 2 Schritte ist, gilt damit als robust. Wer strenger prüfen will,
vergrößert die Optimierungs-Schrittweite oder prüft die Kennlinien in der
KI-Auswertung manuell.

## 4. KI-Gate (Schritt 6)

- Strategien mit KI-Stabilitäts-Score < 30 werden aus dem finalen Portfolio
  gefiltert.
- **Fallback sichtbar gemacht:** Fallen *alle* Kandidaten durch, werden sie
  zwar exportiert (wie bisher), aber:
  - `WorkflowEngine.isKiGateBypassed()` liefert `true`,
  - der Export-Ordner erhält `WARNUNG_KI_GATE_UMGANGEN.txt`,
  - die Schritt-6-Kachel im Workflow zeigt **WARNUNG** in Rot.
- Der Bypass-Zustand wird in `WORKFLOW_STATE.ki_gate_bypassed` **persistiert**
  und überlebt einen App-Neustart (die Kachel bleibt rot). Die Warndatei
  selbst entsteht nur beim Export — wer einen alten Workflow-Stand lädt und
  nicht erneut exportiert, sieht die Warnung an der Kachel, nicht im Ordner.
- Der "Best"-Ordner (KI ≥ 70) nimmt nach Schritt 7 nur noch Strategien auf,
  die die Validierung nicht explizit `FAILED` haben. Die pro Pass erzeugten
  Warndateien (`WARNUNG_Pass<N>_VALIDIERUNG_FEHLGESCHLAGEN.txt`) sind eigene
  Artefakte des Tools: Besteht ein Pass eine spätere Validierung, wird seine
  veraltete Warndatei automatisch entfernt (bei Schritt 7 und beim erneuten
  Best-Export).

## 5. Bekannte Grenzen (ehrlich dokumentiert)

1. **KI-Score ist nicht deterministisch.** Schritt 5 nutzt ein LLM
   (Default `gpt-4o-mini` via OpenRouter); das Regex-Parsing
   (`STABILITY_SCORE|Pass|Score`) kann bei abweichendem Antwortformat
   Scores verlieren. Der gewichtete Final-Score fällt dann auf den
   Unified Score zurück.
2. **Keine Monte-Carlo-Simulation.** Der Robustness Scanner macht
   deterministische Parameter- und Perioden-Sweeps — die Hilfetexte wurden
   entsprechend korrigiert. Trade-Resampling (Monte Carlo) wäre eine
   sinnvolle Erweiterung.
3. **PDF-Equity-Kurve ist synthetisch** und auch so beschriftet
   ("Synthetische Äquitätskurve") — MT5-Optimierungsreports enthalten keine
   echten Equity-Kurven pro Pass. Sie ist reine Illustration und fließt in
   keinen Score ein.
4. **Ein Validierungsfenster ist kein Walk-Forward.** Schritt 7 prüft ein
   einzelnes Fenster. Ein echtes Walk-Forward mit mehreren Folds (rollende
   Optimierung + Validierung) wäre der nächste Ausbauschritt.
5. **Kurze Validierungsfenster liefern schwache Evidenz.** Der Dialog warnt
   bei < 30 Tagen; `PASSED` mit einer Handvoll Trades ist kein Beweis.

## 6. Tests der Anti-Curvefitting-Kette

| Test | Sichert ab |
|---|---|
| `ForwardSplitTest` | MT5-Datums-Split (BT/FW-Fenster, alle Modi, keine Überlappung) |
| `MassiveCoverageTest.test097` | Score-Invariante: identische Kennzahlen + andere Pass-Nummer ⇒ identischer Score (hätte den alten RNG-Bug sofort gefangen) |
| `MassiveCoverageTest.test098` | Sharpe-Säule ist monoton in der echten Sharpe Ratio |
| `WorkflowValidationAndGateTest` | KI-Gate-Bypass-Flag, Validierungsfenster-Regeln (Überlappung verboten), Verdikte, Persistenz, `computeTotalPasses`, `yearsBetween`, Gewichts-Default-Konsistenz |
| `OptimizationResultTest.testContinuousFwTradeScoring` | Score monoton in FW-Trades |
| `WorkflowDiversityTest` | Diversitäts-Filter (Ähnlichkeit, Schwellen, Reihenfolge) |

## 7. Changelog dieses Umbaus (Juli 2026)

- **Score:** 10 → 8 Säulen; RNG-Stabilität, SQN, Symmetrie, Tail-Risk
  entfernt; echte Sharpe Ratio als Säule 5; reale Testjahre statt hart 3.0
  (Calmar + Stichprobe).
- **Gewichte:** fünf divergierende Default-Sätze auf
  `ScoreWeights.defaults()/loadFromDatabase()` konsolidiert.
- **Schritt 7 (neu):** Out-of-Sample-Validierung auf unberührtem Fenster;
  UI-Kachel, Konfigurationsdialog, DB-Spalte `validation_results_json`
  (automatische Migration), Batch-Workflow-Integration, Export-Report,
  Best-Ordner-Schutz.
- **KI-Gate:** Fallback sichtbar (Flag, Warndatei, rote UI-Kachel).
- **Kleine Fixes:** `totalPasses` aus Parameterbereichen berechnet statt
  hart 1000; `getWorstCvForPass` liefert `NaN` statt 0.0 bei fehlenden Daten
  (fehlende Daten sahen wie perfekte Robustheit aus); Watchdog auch im
  "Terminal bleibt offen"-Modus; `cancelled`/`currentProcess` volatile;
  Monte-Carlo-Behauptungen aus Hilfetexten entfernt; Warnung bei
  0-Pass-Optimierungsergebnis in Schritt 3.
- **Nacharbeiten aus Zweit-Review:** `computeTotalPasses` überlaufsicher
  (Cap vor Cast und vor jeder Multiplikation — Wrap-around auf kleine
  positive Werte ist ausgeschlossen); `ki_gate_bypassed` wird in
  WORKFLOW_STATE persistiert (rote Kachel überlebt Neustart); veraltete
  Validierungs-Warndateien im Best-Ordner werden automatisch entfernt,
  wenn ein Pass später besteht; `PASSED` mit < 10 Trades wird als
  "schwache Evidenz" markiert; Validierungs-JSON-Parsing in einen Helper
  dedupliziert.
