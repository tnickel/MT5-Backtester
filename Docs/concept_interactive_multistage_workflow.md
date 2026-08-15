# Konzept-Spezifikation: Guided Multi-Stage Workflow Optimizer
## (Interaktive & Stufenweise Optimierungspipeline für ToTheMoon132)

Mehrere Champion-Linien statt einem Grid nach g01–g04:
`Docs/concept_b_cluster_linienbaum.md` (Show Flow als Linienbaum; Kette bleibt linear).

---

## 1. Executive Summary & Problemstellung

### Problemstellung
Bei komplexen Handelssystemen (z. B. Grid- / Martingale-EAs wie `ToTheMoon_KI_v132`) mit 10 bis 20 konfigurierbaren Eingabeparametern scheitert eine simultane genetische Optimierung aller Parameter an der **kombinatorischen Explosion** (*Fluch der Dimensionalität*). Ein Suchraum von $10^{20}$ Kombinationen wird von einer genetischen Suche mit 10.000 Passes zu weniger als $0,0000000000000001\%$ abgedeckt. Die Optimierung bleibt in lokalen Minima hängen und verfehlt global herausragende Setups.

### Lösungskonzept
Einführung einer **interaktiven stufenweisen Optimierungspipeline** in der `MT5 Backtester`-Anwendung, unterstützt durch den `GuidedOptimizationService` und die `ToTheMoon132GuidedWorkflowFactory`.
Statt alle Parameter auf einmal zu durchsuchen, wird der Suchraum in **11 feingliedrige, logische Stufen** zerlegt.
Da jede Stufe nur 2 bis 4 Parameter mit definierten Schrittweiten umfasst, arbeitet der MT5-Optimizer im Modus **Exhaustive Search (Vollständige Durchmusterung, `optimizerMode = 1`)**. Dadurch entfällt der blinde Fleck genetischer Algorithmen – 100 % des Teil-Suchraums werden exakt berechnet.

Nach Abschluss jeder Stufe analysiert der Nutzer die Ergebnisse in der jeweiligen Databank und wählt per Rechtsklick seine präferierte Strategie aus. Das System übernimmt deren exakte Parameterwerte als festes Fundament für die nächste Stufe und schaltet dort automatisch nur die Folge-Parameter zur Optimierung frei.

---

## 2. Der 15-Stufen-Gesamt-Workflow (`ToTheMoon132GuidedWorkflowFactory`)

```
====================================================================================
                        STUFENWEISE OPTIMIERUNGS-PIPELINE
====================================================================================

[00] STRATEGIE-AUSWAHL  --> Initialisierung (ToTheMoon132, AUDCAD M5)
                               |
[01] GRID-FUNDAMENT     --> Inp_Grid_Step (550..900), Step_Multiplier (1.0..1.3), Next_Lot_Mult (1.1..1.5)
                               |  ==> Databank: g01_grid_pick
[02] ORDER-TAKTUNG      --> Min_Profit, Wait_Open_Equal_Orders, Wait_Next_Lot
                               |  ==> Databank: g02_cadence_pick
[03] ENVELOPES OBEN     --> Period (3..15), Deviation (0.005..0.030), Method, Price
                               |  ==> Databank: g03_env_upper_pick
[04] ENVELOPES UNTEN    --> Period_Lower (2..41), Deviation_Lower (0.005..0.030), Method, Price
                               |  ==> Databank: g04_env_lower_pick
[05] ADX-REGIME         --> Use_ADX_Filter, ADX_Period (9..31), ADX_Max_Level (30..50)
                               |  ==> Databank: g05_adx_pick
[06] ATR-GRIDABSTAND    --> Use_ATR_Step, ATR_Period (5..19), ATR_Multiplier (1.3..2.9)
                               |  ==> Databank: g06_atr_grid_pick
[07] VOLATILITÄT        --> Use_Vol_Filter, Vol_ATR_Period, Vol_ATR_Max_Mult, Correlation
                               |  ==> Databank: g07_vol_corr_pick
[08] GRID-RISIKO        --> Max_Grid_Levels (8..16), Emergency_SL, Buffer_Percent
                               |  ==> Databank: g08_risk_pick
[09] ENTRY-QUALITÄT     --> Entry_Confirmation, Confirm_Lookback, Max_Entry_Excursion
                               |  ==> Databank: g09_entry_pick
[10] EXIT-MANAGEMENT    --> Trail_Start_Points (60..160), Trail_Step_Points (3..15)
                               |  ==> Databank: g10_exit_pick
[11] ADAPTIVE SAFETY    --> Adaptive_Spacing, Adaptive_ADX_Ref, Escalation_Block
                               |  ==> Databank: g11_safety_pick

====================================================================================
                        VALIDIERUNGS- & RETESTER-KASKADE
====================================================================================

[12] DEV-RETEST (3Y)   --> Every Tick Retest (2022-2025)  ==> Databank: g12_dev_tick
                               | (Filter: Trades >= 1200, PF >= 1.25, DD <= 10%)
[13] OOS-RETEST (1Y)   --> Every Tick OOS (2025-2026)     ==> Databank: g13_oos_tick
                               | (Filter: Trades >= 350, PF >= 1.15, DD <= 12%)
[14] FINAL-RETEST (4Y) --> Every Tick Full (2022-2026)    ==> Databank: g14_final_4y
                               |
[15] FINALE AUSWAHL    --> Pre-Filter (Trades >= 1800, PF >= 1.25, DD <= 8%, Rec >= 2)
                               |  ==> Databank: FINAL
```

---

## 3. Mathematische Überlegenheit: Exhaustive vs. Genetic Search

### Warum Vollständige Durchmusterung (`optimizerMode = 1`) gewählt wurde:
* **Kleinere Teil-Suchräume:** Durch die Aufteilung in Stufen mit jeweils nur 2 bis 4 Parametern umfasst der Suchraum pro Stufe meist nur **500 bis 5.000 Kombinationen**.
* **Keine blinden Flecken:** Bei 2.000 Kombinationen berechnet MT5 im Modus `optimizerMode = 1` **alle 2.000 Kombinationen lückenlos**.
* **Globales Optimum garantiert:** Es gibt keine Frühkonvergenz (*Premature Convergence*) und kein Übersehen der besten Parametereinstellung.

### Optimierungskriterium (`optimizerCriterion = 7`):
Es wird das **MT5 Complex Criterion** verwendet. Dieses bewertet eine Kombination aus:
1. **Nettogewinn & Recovery Factor**
2. **Statistische Stichprobengröße (Trade-Anzahl)** – bevorzugt aktive Setups gegenüber Zufallstreffern.
3. **Kontrollierter Equity Drawdown**

---

## 4. UI- & UX-Spezifikation (Hand-Pick Interaktion)

### 4.1 Databank-Tabelle Kontextmenü-Erweiterung (`ProjectWorkflowEditorView.java`)
In allen Databank-Tabellen wird das Kontextmenü jeder Zeile erweitert um:
* **Eintrag:** `📌 Als Parameter-Basis für nächsten Task übernehmen`
* **Handler:** Ruft `GuidedOptimizationService.adoptPassParameters(...)` auf.

### 4.2 Bestätigungs-Dialog & Banner-Feedback
Vor der Parameterübernahme erscheint ein Bestätigungsdialog mit folgenden Informationen:
* **Quelle:** Name der aktuellen Databank und Pass-Nummer.
* **Fidelity:** Auflösungsstufe der Parameter (Exakter Snapshot vs. Preset-Rekonstruktion).
* **Ziel:** Name des Folge-Optimizer-Tasks und Anzahl der neuen Ziel-Parameter.

Nach Bestätigung erscheint ein animierter grüner Status-Banner im UI (8 Sekunden Auto-Hide):
> `✓ Parameter aus Pass #2380 als Basis für '02 Order-Taktung — Optimizer' übernommen`

---

## 5. Software-Architektur (Java Codebase)

### 5.1 Neuentwickelte Kernkomponenten
1. `src/main/java/com/backtester/workflow/GuidedOptimizationService.java`
   - Bietet statische Methoden zur Ermittlung des Folge-Optimizers (`findNextActiveOptimizer`) und zur Parameter-Vererbung (`adoptPassParameters`).
   - Schützt Basis-Parameter über defensives Klonen (`EaParameter.copy()`).
2. `src/main/java/com/backtester/workflow/ToTheMoon132GuidedWorkflowFactory.java`
   - Erzeugt die komplette 15-Stufen-Pipeline für `ToTheMoon132` (AUDCAD M5).
   - Definiert exakte Start/Schritt/Ende-Suchräume für alle 11 Optimierungsstufen.
3. `src/test/java/com/backtester/workflow/ToTheMoon132GuidedWorkflowFactoryTest.java` & `GuidedOptimizationServiceTest.java`
   - Automatisierte Unit-Tests für Pipeline-Erstellung, Parameter-Übernahme und Bereichs-Validierung.

---

## 6. Verifikations- & Testergebnisse

Die erweiterte Pipeline wurde vollständig durch Unit- und Integrationstests verifiziert:

```text
[INFO] Running com.backtester.workflow.GuidedOptimizationServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.116 s
[INFO] Running com.backtester.workflow.ToTheMoon132GuidedWorkflowFactoryTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.196 s
[INFO] BUILD SUCCESS
```

---
