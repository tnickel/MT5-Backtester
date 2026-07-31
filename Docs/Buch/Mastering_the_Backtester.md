# Mastering the Backtester

**Ein Leitfaden zur robusten Strategieoptimierung und zur Architektur des Backtester-Projekts**

Version 1.3 — 08.07.2026

---

## Inhaltsverzeichnis

- Vorwort: Warum dieses Buch existiert
- Kapitel 1: Grundlagen und Orientierung im Programm
- Kapitel 2: Der erste Einzeltest
- Kapitel 3: Märkte und Timeframes vergleichen
- Kapitel 4: Curve Fitting und die Optimierungs-Pipeline
- Kapitel 5: Der Workflow als roter Faden
- Kapitel 6: Robustness, Controlling, Daten, Settings und Reports
- Kapitel 7: Architektur und Engine
- Kapitel 8: Reporting, Scoring und Persistenz
- Kapitel 9: Der KI-Ansatz
- Kapitel 10: Datenversorgung mit Dukascopy und MT5 Custom Symbols
- Kapitel 11: Parameter-Referenz
- Fazit
- Anhang A: Glossar
- Anhang B: Quellen und Bildnachweis
- Anhang C: Häufige Probleme

---

## Vorwort: Warum dieses Buch existiert

Dieses Buch dokumentiert den Backtester als konkretes Softwareprojekt und führt zugleich in die Denkweise robuster Strategieentwicklung ein. Es ist kein reines Benutzerhandbuch und auch kein isolierter Architekturbericht. Es verbindet die Bedienung des Programms mit den Gründen, warum seine Pipeline so gebaut wurde: MetaTrader wird automatisiert, Optimierungen werden strukturiert, Sensitivität wird messbar, KI wird als Analyst und nicht als Orakel eingesetzt, und die finale Strategieauswahl wird durch eine echte Out-of-Sample-Validierung abgesichert.

Die Analyse basiert auf dem Sourcecode dieses Repositories. Zum Zeitpunkt der Bucherstellung umfasst die Hauptanwendung 79 Java-Dateien mit rund 44.000 Zeilen und die Tests 41 Java-Dateien mit rund 8.400 Zeilen. Besonders relevant sind die Pakete `engine`, `report`, `database`, `config`, `dukascopy`, `mt5` und `ui.javafx`. Die vorhandenen Markdown-Dokumente, README-Dateien, Screenshots und Tests wurden als zusätzliche Quellen herangezogen.

Der Text richtet sich an Nutzer, die keine Quant-Profis sind, aber trotzdem verstehen wollen, was ein Backtest leistet, was er nicht leisten kann und wie man mit einem Werkzeug wie diesem die typischen Denkfallen reduziert. Er richtet sich auch an Entwickler, die die Architektur erweitern möchten, ohne die fachlichen Schutzmechanismen aus Versehen zu unterlaufen.

---

## Kapitel 1: Grundlagen und Orientierung im Programm

### Was ein Backtest ist

Ein Backtest ist ein kontrolliertes Gedankenexperiment mit historischen Daten. Eine Handelsregel wird so behandelt, als hätte man sie in der Vergangenheit bereits gekannt, und anschließend wird berechnet, welche Trades sie erzeugt hätte. Der Nutzen liegt darin, schlechte Ideen schnell auszusortieren und gute Ideen genauer zu untersuchen, bevor echtes Kapital eingesetzt wird. Der Fehler beginnt, wenn man das Ergebnis als Vorhersage missversteht. Ein Backtest sagt nicht: *Diese Strategie wird in Zukunft Geld verdienen.* Er sagt nur: *Unter den simulierten Annahmen hätte diese Logik in diesem historischen Zeitraum so ausgesehen.*

Im Backtester-Projekt wird diese Idee praktisch an MetaTrader delegiert. Die Java-Anwendung schreibt eine `tester.ini`, startet MT4 oder MT5, wartet auf den Report, kopiert die Ergebnisdateien und parst die Kennzahlen. Dadurch bleibt die eigentliche Handelssimulation bei der Plattform, die Expert Advisors ohnehin ausführt. Die Backtester-Anwendung wird zur Orchestrierungsschicht: Sie standardisiert Eingaben, automatisiert Wiederholungen, sammelt Resultate und fügt robuste Bewertung hinzu.

### Drei Arten von Disziplin

Gute Backtests brauchen drei Arten von Disziplin:

1. **Technische Disziplin:** Reports dürfen nicht veraltet sein, Prozesse dürfen nicht hängen bleiben, Parameterdateien müssen exakt zugeordnet werden.
2. **Statistische Disziplin:** wenige Trades sind schwache Evidenz, ein einzelner Zeitraum ist keine Marktwahrheit, und jede zusätzliche Optimierungsrunde erhöht die Gefahr, Rauschen zu lernen.
3. **Operative Disziplin:** Ergebnisse müssen gespeichert, reproduzierbar und kommentierbar sein.

Genau aus dieser Dreiteilung erklärt sich die Architektur des Projekts.

### Die Anwendung als Arbeitsplatz

Die Anwendung ist kein einzelner Backtest-Knopf, sondern eine Arbeitsumgebung für Strategieentwicklung. Die Registerkarten bilden einen typischen Lebenszyklus ab: Zuerst werden globale Pfade und Daten vorbereitet, dann werden Einzeltests und Batchtests genutzt, danach folgt die Optimierung mit Forward-Analyse, Sensitivität, KI-Bewertung, Portfolio-Auswahl und späteres Controlling. Wer diese Reihenfolge versteht, vermeidet viele typische Fehlinterpretationen.

Im Alltag beginnt man selten direkt im Workflow. Ein neuer EA wird oft zuerst im Backtest-Tab geprüft: läuft er überhaupt, schreibt MT5 einen Report, stimmen Symbol und Zeitraum? Danach kann der Multi-Backtester zeigen, auf welchen Märkten und Timeframes die Strategie grundsätzlich reagiert. Erst wenn diese Vorarbeit sinnvoll aussieht, lohnt sich die Optimierung. Genau diese Trennung macht die Ergebnisse belastbarer.

| Bereich | Zweck | Wann nutzen |
|---|---|---|
| Backtest | Einzelner MT4/MT5-Test | Für schnelle Plausibilität und finale Nachtests einzelner Parameter-Sets. |
| Multi-Backtester | Batch über Märkte und Timeframes | Für Markt-Screening: Wo funktioniert die Strategie, wo nicht? |
| Optimizer | MT5-Optimierung und Analyse | Für systematische Parametersuche mit Anti-Curvefitting-Gates. |
| Robustness | Robustheitsscans | Für Stresstests außerhalb des großen Workflows. |
| Workflow Automator | Geführte Pipeline | Für ernsthafte Strategieauswahl mit nachvollziehbaren Gates. |
| Controlling | Nachtest und Strategiepflege | Für laufende Qualitätssicherung nach dem ersten Export. |
| Database | Historie | Für Nachvollziehbarkeit und Aufräumen alter Runs. |
| Dukascopy Data | Marktdatenversorgung | Für bessere Datenkontrolle jenseits der Broker-Historie. |
| Settings | Globale Pfade und Defaults | Vor jedem produktiven Lauf prüfen. |
| Log | Live-Protokoll | Wenn MT5 hängt, kein Report erscheint oder ein Batch unklar stoppt. |
| Manual | In-App-Hilfe | Für schnelle Erinnerung; dieses Buch ist die ausführliche Referenz. |

**Checkliste für jeden Lauf:**
- Vor jedem Lauf: Settings, MT5-Pfad, portable Mode, Reportordner und Datenqualität prüfen.
- Bei neuen EAs zuerst einen kleinen Einzeltest starten, bevor Batch oder Optimierung laufen.
- Forward- und OOS-Fenster schon vor der Optimierung planen.
- Reports nicht nur nach Profit lesen: Tradezahl, Drawdown, Recovery, Sharpe und Equity-Form zählen.

![Gesamtbild der Plattformidee: Backtesting, Optimierung, Batch Running und Performance Analytics als zusammenhängender Arbeitsplatz.](images/backtester_platform.png)

**Wichtige Begriffe:**
- **Backtest:** Simulation historischer Trades mit einer bekannten Regel.
- **Forward-Test:** Teil des Optimierungsfensters, der nicht für die Parameterberechnung genutzt wird, aber später oft in die Auswahl einfließt.
- **Out-of-Sample:** Daten, die während Optimierung und Auswahl unberührt bleiben.
- **Robustheit:** Ein Ergebnis bleibt plausibel, wenn Zeitraum, Parameter oder Daten leicht variieren.

---

## Kapitel 2: Der erste Einzeltest

Der Backtest-Tab ist die Werkbank für einzelne MT4/MT5-Läufe. Er dient drei Zwecken: technische Funktionsprüfung eines EAs, schnelle Plausibilität eines Parameter-Sets und finaler Nachtest einer bereits ausgewählten Strategie.

Expert Advisor wählt den EA. Symbol und Period bestimmen Markt und Timeframe. Dates und To setzen das historische Fenster. Deposit, Currency und Leverage geben die Kontoannahmen vor. Tick Model bestimmt, wie genau MT5 historische Bewegungen simuliert. Der Start Backtest-Button startet den normalen, reproduzierbaren Lauf; Visual Mode ist für Diagnose gedacht; Manual Mode hält MT4/MT5 offen.

Die Ergebnistabelle zeigt Expert, Symbol, Period, Profit, Trades, Win Rate und Drawdown. Ein grüner Profit allein reicht nicht. Zwei Trades mit 100 Prozent Win Rate sind statistisch nahezu wertlos; 300 Trades mit moderatem Profit und kontrolliertem Drawdown sind aussagekräftiger. Open HTML Report öffnet den Detailreport, Open Directory führt zu den erzeugten Dateien.

Best Practice: Einzeltests nicht als Optimierungsersatz verwenden. Wer manuell so lange Werte ändert, bis ein Backtest schön aussieht, betreibt ebenfalls Curve Fitting. Der Einzeltest ist stark, wenn er eine konkrete Hypothese prüft: Läuft der EA auf XAUUSD H1? Bleibt ein aus dem Workflow exportiertes SET im späteren Zeitraum plausibel? Sind die Reportdateien vollständig?

Wenn ein Lauf kein Ergebnis erzeugt, ist der Log-Tab der nächste Ort. Typische Ursachen sind falscher Terminalpfad, nicht vorhandenes Symbol, EA-Kompilierungsfehler, blockierte MT5-Instanz, fehlendes Datenfenster oder ein Report, den MT5 anders ablegt als erwartet. Manual Mode hilft bei Diagnose, sollte aber bei Batch- und Workflow-Läufen nicht dauerhaft aktiv bleiben.

![Backtest-Hauptmaske: oben Einzeltest-Konfiguration, unten Historie und Ergebnisaktionen.](images/backtester_ui1.png)

![Einzelreport-Dialog mit Kennzahlen, Equity-Kurve, Detailstatistik und Report-Aktionen.](images/backtester_ui3.png)

| Feld / Aktion | Funktion | Best Practice |
|---|---|---|
| Expert Advisor | Pfad oder relativer Name des EAs. | Ohne EA startet kein Test. Nach EA-Auswahl werden Parameterprofile geladen. |
| Symbol | Markt aus der festen Liste. | Symbol muss in MT5 vorhanden sein. Bei Custom Symbols zuerst Dukascopy-Import prüfen. |
| Period | Zeiteinheit M1, M5, M15, M30, H1, H4, D1, W1 oder MN1. | Niedrige Timeframes brauchen mehr Datenqualität und Laufzeit. |
| Dates / To | Historisches Testfenster. | Nicht zu kurz wählen; für finale Checks ein Fenster nutzen, das nicht zur Optimierung diente. |
| Deposit | Startkapital für MT5-Kennzahlen. | Konstant halten, wenn Ergebnisse verglichen werden. |
| Currency | Kontowährung: USD, EUR oder GBP. | Muss zu Broker- und Reportannahmen passen. |
| Leverage | Hebel als Text, z. B. 1:100. | Realistische Werte verwenden; Margin-Situationen nicht schönrechnen. |
| Tick Model | Every tick, 1 minute OHLC, Open price only, Math calculations oder Every tick (real ticks). | Für schnelle Vorprüfung OHLC; für ernsthafte Validierung realistischeres Modell. |
| Manual Mode / Keep MT4/5 Open | Verhindert automatisches Schließen von MT nach dem Lauf. | Gut zur Diagnose. Für Batchläufe besser automatisch schließen lassen. |
| Start Backtest | Startet MT4/MT5 ohne Visualisierung und speichert Ergebnis in der Datenbank. | Standard für reproduzierbare Einzeltests. |
| Visual Mode | Startet den visuellen Tester für manuelle Beobachtung. | Nur zur Diagnose und Strategie-Verständnis. |
| Cancel | Bricht einen laufenden Test ab. | Danach Log und Reportordner prüfen. |
| History & Results | Tabelle mit Expert, Symbol, Period, Profit, Trades, Win Rate, Drawdown. | Nicht nur Profit betrachten. |
| Open HTML Report | Öffnet den erzeugten Report-Dialog. | Erste Sichtprüfung der Equity-Kurve, Statistik und Tradezahl. |
| Open Directory | Öffnet das Reportverzeichnis. | Wichtig, um SET, HTM/XML und Exportartefakte zusammenzuhalten. |

---

## Kapitel 3: Märkte und Timeframes vergleichen

Der Multi-Backtester automatisiert viele Einzeltests mit denselben globalen Annahmen. Er beantwortet nicht die Frage nach den besten Parametern, sondern die Vorfrage: Wo zeigt diese Strategie überhaupt Verhalten, das eine tiefere Analyse rechtfertigt? Dadurch spart man viel Zeit und vermeidet Optimierungen auf Märkten, die schon im Basistest unplausibel sind.

Oben stehen Expert Advisor, Datum, Deposit, Currency, Leverage, Tick Model und Presets. Darunter befinden sich zwei Auswahllisten: Symbols und Timeframes. Jedes markierte Symbol wird mit jedem markierten Timeframe kombiniert. Drei Symbole und drei Timeframes erzeugen also neun Jobs. Start Batch arbeitet diese Warteschlange sequentiell ab und schreibt die Resultate in die Batch-Historie.

Presets sind hier besonders wichtig. Ein Preset speichert EA, Symbolauswahl, Timeframes und den Parameter-Snapshot. Neu erstellt ein Preset, Speichern überschreibt das gewählte Preset mit den aktuellen Einstellungen, Ändern passt Namen und Inhalt an, Löschen entfernt es. Damit lassen sich wiederkehrende Markt-Screenings reproduzieren.

Nach dem Lauf zeigt die Results Table Robot, Symbol, Period, Trades, Win Rate, Drawdown, Recovery Factor, Profit und Status. Open Multi-Report Node öffnet den aggregierten Summary-Report. Show Single Report öffnet den Report des markierten Einzelruns. Genau hier sollte man die grobe Auswertung machen: Gibt es Cluster nach Timeframe? Sind Gewinner nur einzelne Ausreißer? Haben profitable Runs genug Trades?

Best Practice: Den Multi-Backtester nicht als Lotterie verwenden. Wer alle Symbole und alle Timeframes testet und danach nur den besten Run nimmt, hat einen massiven Auswahlbias erzeugt. Besser ist eine explorative Phase mit Notizen: Welche Marktgruppen verhalten sich konsistent? Wo ist die Tradezahl ausreichend? Welche Kombinationen verdienen eine saubere Optimierung mit Forward-Split?

![Multi-Backtest-Summary-Report: Vergleich mehrerer Symbol/Timeframe-Runs mit Detailbereichen.](images/backtester_ui2.png)

![Einzelreport aus einem Multi-Backtest-Run mit Equity-Kurve und Kennzahlen.](images/multi-backtester-results.png)

![MT5-Reportausschnitt mit Kontostand-Kurve; im Projektbildbestand irreführend als Multi-Konfiguration benannt.](images/multi-backtester-config.png)

| Feld / Aktion | Funktion | Best Practice |
|---|---|---|
| Expert Advisor | Ein EA für alle Batch-Kombinationen. | Parameter müssen für alle gewählten Märkte plausibel sein. |
| Dates / To | Globales Zeitfenster für alle Runs. | Nur gleiche Zeitfenster machen Symbol- und Timeframe-Vergleiche fair. |
| Deposit, Currency, Lev | Globale Kontoannahmen. | Nicht zwischen Runs ändern, wenn die Tabelle vergleichbar bleiben soll. |
| Tick Model | Globales Tester-Modell für den Batch. | Je mehr Kombinationen, desto stärker wirkt die Laufzeit des Modells. |
| Presets | Preset wählen, Neu, Speichern, Ändern oder Löschen. | Speichert EA, Symbole, Timeframes und Parameter-Snapshot. |
| Symbols | Checkbox-Liste plus Add Custom. | Custom nur verwenden, wenn MT5 das Symbol kennt oder importiert hat. |
| Timeframes | M1, M5, M15, M30, H1, H4, D1, W1, MN1. | Nicht blind alle Timeframes testen; sonst steigt Multiple-Testing-Bias. |
| Start Batch | Erzeugt eine Warteschlange aller Kombinationen. | Vorher Anzahl Jobs überschlagen; Batchs können lange laufen. |
| Cancel | Stoppt den laufenden Batch. | Nach Abbruch können Teilresultate vorhanden sein. |
| Batch History | Liste gespeicherter Batchläufe. | Erlaubt späteres Öffnen oder Löschen. |
| Open Multi-Report Node | Öffnet den aggregierten Multi-Report. | Gut für Überblick: welche Kombinationen waren OK? |
| Results Table | Robot, Symbol, Period, Trades, Win Rate, Drawdown, Recovery Factor, Profit, Status. | Nach Profit, Drawdown oder Status sortieren, aber schwache Tradezahlen aussortieren. |
| Show Single Report | Öffnet den Einzelreport des markierten Runs. | Die Equity-Kurve prüfen, bevor ein Run als guter Markt interpretiert wird. |
| Delete Batch / Delete Selected Runs | Bereinigt Historie oder einzelne Runs. | Erst löschen, wenn Report und Entscheidung nicht mehr benötigt werden. |

---

## Kapitel 4: Curve Fitting und die Optimierungs-Pipeline

### Die Gefahr des Curve Fittings

Curve Fitting bedeutet, dass eine Strategie so eng an historische Daten angepasst wird, dass sie nicht mehr das Marktsignal, sondern die Eigenheiten der Stichprobe beschreibt. In der Rückschau sieht das oft beeindruckend aus: die Equity-Kurve ist glatt, der Profit hoch, der Drawdown klein. Im Live-Handel verschwindet die Magie, weil die Zukunft nicht dieselben Zufallsbewegungen wiederholt. Overfitting ist deshalb kein Randproblem, sondern die zentrale Gefahr jeder Strategieoptimierung.

Je mehr Parameter eine Strategie besitzt, desto größer wird der Suchraum. Wenn man tausende Kombinationen testet, ist es statistisch fast unvermeidbar, dass einige Kombinationen zufällig gut aussehen. Das ist kein Betrug, sondern Mathematik: Viele Vergleiche erzeugen viele Chancen auf Scheintreffer. Der Backtester reagiert darauf nicht mit einem einzigen magischen Score, sondern mit einer Abfolge von Filtern, Sensitivitätsmessungen, KI-Analyse und abschließender Validierung.

Ein anschauliches Beispiel: Eine Strategie hat einen Take-Profit-Parameter. Bei 48, 49, 50, 51 und 52 Punkten bleibt der Gewinn ähnlich. Das ist ein **Plateau** und damit ein robuster Hinweis. Wenn aber nur der Wert 50 extrem gut ist und 49 sowie 51 sofort einbrechen, ist der Parameter ein **Peak**. Peaks können auftreten, weil genau diese historische Stichprobe zufällig passte. Der Backtester versucht, solche Peaks durch Sensitivitätskurven, CV-Werte und KI-Kurvenformanalyse sichtbar zu machen.

Wichtig ist auch: Ein Forward-Test innerhalb einer MT5-Optimierung ist wertvoll, aber er bleibt Teil der Auswahl. Sobald man die besten Pässe anhand ihrer Forward-Kennzahlen sortiert, filtert oder exportiert, wurde dieses Fenster verbraucht. Es ist dann kein komplett unberührter Beweis mehr. Genau darum gibt es den Workflow mit Schritt 7 (siehe Kapitel 5): erst nach Portfolio-Auswahl wird auf einem späteren Fenster ein einfacher Backtest mit den finalen Parametern ausgeführt.

### Der Optimizer

Der Optimizer ist das Zentrum für Parametersuche. Links werden EA, Symbol, Period, Date Range, Kontoannahmen, Tick Model, Opt. Mode, Opt. Criterion und Forward Test gesetzt. Rechts liegt die EA Parameters & Optimization Ranges Tabelle. Jede Zeile kann fix bleiben oder über Opt, Start, Step und Stop in den Suchraum aufgenommen werden.

Der wichtigste Bediengrundsatz lautet: Suchräume müssen fachlich klein und begründet sein. Ein breiter Suchraum mit vielen Parametern produziert schnell tausende Pässe. Darin findet man fast immer ein historisch schönes Ergebnis. Das ist keine Stärke des EAs, sondern eine Nebenwirkung vieler Vergleiche. AutoConfig kann helfen, ersetzt aber nicht die fachliche Entscheidung, welche Parameter überhaupt optimiert werden dürfen.

Combined Analysis verbindet Backtest- und Forward-Resultate. Die Tabelle zeigt Score, Konsistenz, Robustness Scorecard, KI-Spalte, RI, Pass sowie Backtest- und Forward-Kennzahlen. Die Filtersettings entscheiden, welche Pässe überhaupt in die Auswahl gelangen. Filter aktiv und Nur Passes mit Forward-Ergebnis sind die beiden wichtigsten Schalter für eine ernsthafte Auswertung.

Advanced Evaluator und Konsistenzdialog helfen bei der Interpretation. Konsistenz ist das Verhältnis von Forward Profit zu Backtest Profit, begrenzt und normalisiert für die Bewertung. Eine Konsistenz um 0.8 ist oft viel gesünder als ein riesiger Backtest-Gewinn mit Forward-Einbruch.

![Optimizer-Arbeitsbereich mit Suchraum, Combined Analysis, Filtern, Score-Gewichtung und Ergebnisliste.](images/backtester_optimizer.png)

![Score-Gewichtungsdialog mit Presets und relativen Gewichten.](images/backtester_score_weighting.png)

![Konsistenz-Hilfedialog: Bedeutung und Bewertung des Forward/Backtest-Verhältnisses.](images/backtester_consistency_ratio.png)

![Advanced Strategy Evaluator mit Qualitätskriterien, Verteilung und Kandidatenklassifikation.](images/backtester_advanced_evaluator.png)

| Feld / Aktion | Funktion | Best Practice |
|---|---|---|
| Expert Advisor | EA für die Optimierung. | Parameterliste wird aus SET/EA-Kontext geladen. |
| Symbol | Zu optimierender Markt. | Nicht mehrere Märkte in einem Optimizer-Lauf mischen. |
| Period | M1, M5, M15, M30, H1, H4 oder D1. | Der Suchraum sollte zum Timeframe passen. |
| Date Range | Optimierungsfenster, im UI mit Monatsanzeige. | So wählen, dass später ein unberührtes Step-7-Fenster übrig bleibt. |
| Deposit / Currency / Leverage | Kontoannahmen für den Strategy Tester. | Vergleichbarkeit nur bei konstanten Werten. |
| Tick Model | MT5-Modell für die Optimierung. | Schnelleres Modell für breite Suche, genaueres für engere Prüfungen. |
| Opt. Mode | Slow Complete Algorithm oder Fast Genetic Algorithm. | Genetic für große Räume, Complete für kleine kritische Räume. |
| Opt. Criterion | Balance, Profit Factor, Expected Payoff, Drawdown, Recovery, Sharpe, Custom OnTester oder Complex Criterion. | Recovery/Sharpe sind oft robuster als reiner Gewinn. |
| Forward Test | Off, 1/2 period, 1/3 period, 1/4 period oder Custom date. | Für Anti-Curvefitting praktisch immer aktivieren. |
| Forward Date | Nur bei Custom date relevant. | Datum bewusst setzen; es trennt Backtest- und Forward-Teil. |
| Opt | Checkbox in der Parameter-Tabelle. | Nur Parameter optimieren, die fachlich Sinn ergeben. |
| Value | Fester Wert, wenn Opt nicht aktiv ist. | Baseline für nicht optimierte Parameter. |
| Start / Step / Stop | Suchraum eines optimierten Parameters. | Kleine Steps und breite Räume erzeugen sehr viele Kombinationen. |
| AutoConfig | Erzeugt sinnvolle Suchräume aus Parameterwerten. | Startpunkt, aber fachlich prüfen. |
| Load .set / Save .set | Parameter aus MetaTrader-SET laden oder sichern. | SET-Dateien sind die reproduzierbare Wahrheit für EA-Inputs. |
| Start Optimization | Startet Optimierung und schließt MT5 danach. | Standard für reproduzierbare Optimierung. |
| Start (Keep MT5 Open) | Startet Optimierung und lässt Terminal offen. | Nur für Diagnose oder manuelle Nachprüfung. |
| Apply Best Parameters | Übernimmt Parameter des markierten Passes. | Vor dem Übernehmen prüfen, ob Forward und Robustheit stimmen. |
| Open XML | Öffnet den MT5-Optimierungsreport. | Nützlich zur Fehlersuche beim Parser oder bei fehlenden Forward-Daten. |

### Filtersettings der Combined Analysis

| Filter | Funktion | Best Practice |
|---|---|---|
| Filter aktiv | Schaltet die Combined-Analysis-Filter an. | Nach Apply im Filterdialog automatisch aktiv. |
| Nur Passes mit Forward-Ergebnis | Blendet CombinedPasses ohne Forward aus. | Für robuste Auswahl fast immer aktiv lassen. |
| Sortierung | Sortiert nach kombiniertem Score oder anderen Metriken. | Sortierung ist keine Qualitätsgarantie; Filter vorher sauber setzen. |
| Suchfeld | Sucht in Combined-/Selected-Tabellen. | Hilft bei Passnummern und Parametern. |
| BT Profit >= | Mindestgewinn im Backtest. | Default im Code 0.01; Reset im Dialog kann 0.0 setzen. |
| FW Profit >= | Mindestgewinn im Forward. | Verhindert Forward-Verlierer im Kandidatenpool. |
| Min BT Trades >= | Mindestanzahl Backtest-Trades. | Code-Default 100; kleine Stichproben sind gefährlich. |
| Min FW Trades >= | Mindestanzahl Forward-Trades. | Code-Default 15; bei wenigen Trades nur schwache Evidenz. |
| Max BT Drawdown% <= | Obergrenze für Backtest-Drawdown. | Drawdown in Relation zu Profit und Recovery betrachten. |
| Max FW Drawdown% <= | Obergrenze für Forward-Drawdown. | Forward-Drawdown ist für Live-Risiko besonders wichtig. |
| BT Exp. Payoff >= | Mindestdurchschnitt pro Trade im Backtest. | Hilft gegen Strategien, die nur durch viele Kleinsttrades scheinbar gut sind. |
| FW Exp. Payoff >= | Mindestdurchschnitt pro Trade im Forward. | Forward-Payoff sollte nicht dramatisch einbrechen. |
| BT Sharpe Ratio >= | Mindest-Sharpe im Backtest. | Risikoadjustierte Stabilität statt nur absolutem Profit. |
| FW Sharpe Ratio >= | Mindest-Sharpe im Forward. | Guter Indikator für glatteres OOS-Verhalten. |
| BT Recovery Factor >= | Mindest-Recovery im Backtest. | Profit im Verhältnis zum maximalen Rückschlag. |
| FW Recovery Factor >= | Mindest-Recovery im Forward. | Einer der wichtigsten Praxisfilter für Kandidaten. |
| Score min | Separater Dialog mit Low 30, Med 50, High 70. | Je strenger, desto weniger aber bessere Kandidaten. |
| Consistency min | Separater Dialog mit Low 0.4, Med 0.6, High 0.8. | Misst Forward/Backtest-Verhältnis; Schutz vor Forward-Einbruch. |

### Score-Gewichtung

Score-Gewichtung bestimmt nicht, ob ein Pass wahr oder falsch ist, sondern welche Eigenschaften im Ranking mehr Gewicht erhalten. Ein aggressives Profil kann Profit betonen; ein konservatives Profil betont Forward-Trades, Drawdown-Strafe, Recovery und Konsistenz. Die Presets Low/Zahm, Med/Ausgewogen, High/Streng und Grid/High-Trade sind Arbeitsprofile, keine Naturgesetze.

| Gewicht | Bedeutung | Best Practice |
|---|---|---|
| BT Profit | Gewicht für historischen Backtest-Gewinn. | Nicht zu hoch setzen, sonst gewinnt Vergangenheit gegen Robustheit. |
| FW Profit | Gewicht für Forward-Gewinn. | Sollte meist mindestens so wichtig sein wie BT Profit. |
| Konsistenz FW/BT | Gewicht für das Verhältnis Forward zu Backtest. | Hohe Werte belohnen Strategien, die nach dem Split nicht kollabieren. |
| Risk / Drawdown-Strafe | Bestrafung hoher Drawdowns. | Hohe Gewichtung macht Ranking konservativer. |
| Equity Consistency / Sharpe | Stabilität der Ergebnisentwicklung (echte MT5-Sharpe-Ratio). | Hilft gegen einzelne Glückstreffer. |
| Sample Size | Gewicht für Testdauer und Stichprobe. | Schützt vor extrem dünnen Ergebnissen. |
| FW Trade Count | Gewicht für Forward-Tradezahl. | Im Screenshot sehr hoch; gut gegen Scheinsieger mit wenigen Trades. |
| Recovery Factor | Gewicht für Gewinn/Drawdown-Verhältnis. | Praktischer Stabilitätsindikator. |
| Recovery Min/Max | Skalierungsbereich für Recovery-Bewertung. | Default 1.0 bis 5.0 in der Dialoglogik. |
| Presets | Low/Zahm, Med/Ausgewogen, High/Streng, Grid/High-Trade. | Schnelle Arbeitsprofile für verschiedene Suchphasen. |

---

## Kapitel 5: Der Workflow als roter Faden

Der Workflow Automator ist die professionelle Hauptstrecke des Projekts. Im Code sind sieben UI-Schritte implementiert. Dieses Buch dokumentiert sie als acht Arbeitsphasen, weil vor Schritt 1 eine unverzichtbare Phase 0 liegt: Vorbereitung von Settings, Daten, EA, Symbolen und OOS-Planung. Ohne diese Vorbereitung sind die folgenden Schritte formal korrekt, aber fachlich schwach.

Phase 1 speichert Strategie-Auswahl und Suchräume. Phase 2 startet die MT5-Optimierung mit Algorithmus, Ziel und Forward-Modus. Phase 3 filtert und erzwingt Diversity. Phase 4 misst Sensitivität. Phase 5 lässt die KI Kurvenform und Stabilität bewerten. Phase 6 baut das finale Portfolio und exportiert. Phase 7 führt die echte Out-of-Sample-Validierung auf einem späteren, unberührten Fenster aus.

Der Workflow speichert Zustand und Zwischenergebnisse in SQLite. Das ist wichtig: Ein Workflow ist nicht nur eine Reihe von Buttons, sondern eine reproduzierbare Entscheidungskette. Wenn später eine Strategie in den Best-Ordner wandert, sollte nachvollziehbar sein, welche Parameter, Filter, CV-Werte, KI-Einschätzung und Validierung dazu geführt haben.

Die häufigste Fehlbedienung ist, den Forward-Test als finale Wahrheit zu behandeln. Im Workflow wird Forward bereits für Auswahl, Filter und Ranking genutzt. Dadurch ist dieses Fenster verbraucht. Die Step-7-Validierung ist deshalb kein optionales Extra, sondern der entscheidende Realitätscheck nach der Auswahl. Sie muss zeitlich nach dem Optimierungsfenster liegen und darf sich nicht überlappen.

Best Practice für den Workflow: Nur wenige Parameter optimieren, Forward immer aktivieren, Mindesttrades ernst nehmen, Diversity erzwingen, Sensitivitätskurven visuell prüfen, KI-Urteil als Analyse lesen und Step 7 nie überspringen. Eine Strategie, die im Backtest gut ist, im Forward ordentlich bleibt, im Sweep stabile Plateaus zeigt und in Step 7 besteht, ist deutlich glaubwürdiger als ein reiner Optimierungssieger.

![Sensitivitätsdetails: CV-Werte und Kurvenformen zeigen, ob Parameter ein Plateau oder eine Klippe bilden.](images/backtester_sensitivity.png)

![KI-Analyse im Sensitivity-Tab: OpenRouter verarbeitet Performance- und Stabilitätsdaten.](images/backtester_ki_analysis.png)

![KI-Bewertungstabelle mit Pass, Status, Score, CV worst, Fragile und Fazit.](images/backtester_ki_evaluation_table.png)

| Phase | Ziel | Code-Ort | Best Practice |
|---|---|---|---|
| 0 Vorbereitung | Settings, Daten, EA und Zeitfenster prüfen. | SettingsView, DukascopyView, AppConfig | MT5-Pfad, portable Mode, Datenqualität, Reportpfade und ein späteres OOS-Fenster müssen stimmen. |
| 1 Strategie-Auswahl | EA, Symbol(e), Periode, Preset, Datum, Konto, Hebel, Modell und Parameter-Suchraum setzen. | WorkflowConfigDialogs.showStep1Config | Nur Parameter optimieren, deren Bedeutung verstanden wird. |
| 2 Optimizer-Konfiguration | Algorithmus, Optimierungsziel, Forward-Test und optional Forward-Datum setzen. | showStep2Config, OptimizationRunner | Forward aktivieren; Genetic für breite Suche, Complete für kleine Räume. |
| 3 Filter und Diversität | Profit-, Trade-, Drawdown- und Diversity-Schwellen anwenden. | showStep3Config, WorkflowEngine.runStep3 | Nicht fünf fast identische Pässe als Portfolio akzeptieren. |
| 4 Sensitivität | Parameter-Sweeps für selektierte Kandidaten ausführen. | showStep4Config, SensitivityRunner | Plateaus sind besser als Peaks; hohe CV-Werte sind Warnsignale. |
| 5 KI-Bewertung | OpenRouter-Key, Modell, Prompt sowie Performance/Stability-Gewichtung setzen. | showStep5Config, LlmAnalysisService | KI ist Analyst, nicht Freigabebehörde. |
| 6 Finales Portfolio | 3–5 beste Strategien, Export- und Best-Verzeichnis auswählen. | showStep6Portfolio, exportPortfolio | Export ist noch keine Live-Freigabe. |
| 7 OOS-Validierung | Validierung von/bis auf unberührtem späterem Fenster setzen. | showStep7ValidationConfig, ValidationResult | Nur PASSED-Kandidaten gehören nach vorhandener Validierung in den Best-Ordner. |

### Robuste Optimierung in der Praxis

Eine saubere Pipeline beginnt vor dem ersten Klick. Der Nutzer legt fest, welcher Zeitraum für Entwicklung, welcher für Forward-Auswahl und welcher später für echte Validierung reserviert wird. Wenn das Enddatum der Optimierung bereits heute ist, bleibt kein späteres Fenster für Step 7. Der Backtester erkennt solche Situationen und verlangt ein brauchbares Validierungsfenster.

In der Praxis sollte ein Nutzer nie nur den höchsten Score betrachten. Ein Kandidat mit Score 78, breitem Plateau, 80 Forward-Trades und moderatem Drawdown ist oft interessanter als ein Kandidat mit Score 91, aber nur 6 Forward-Trades und einer Peak-Kennlinie. Robustheit bedeutet nicht maximalen historischen Gewinn, sondern die höchste Chance, dass die beobachtete Kante kein Zufallsprodukt war.

Der wichtigste operative Satz lautet: **Kein Best-Ordner ohne ernsthafte Validierung.** Wenn Step-7-Ergebnisse existieren, dürfen nur PASSED-Kandidaten in den Best-Ordner. FAILED, NO_TRADES oder ERROR sind keine Kleinigkeit, sondern eine rote Linie. Ein NO_TRADES-Ergebnis kann bedeuten, dass das Fenster zu kurz war oder die Strategie in dieser Marktphase keine Signale hatte. Auch das ist Information.

---

## Kapitel 6: Robustness, Controlling, Daten, Settings und Reports

Robustness ist die freie Stresstest-Werkbank neben dem geführten Workflow. Sie erlaubt, einen EA mit Symbol, Period, Modell, Datum, Konto, Metrik, Shifts und Shift-Tagen zu prüfen. AutoConfig, Load .set, Save Config und Generate Defaults helfen beim Aufbau der Parameterbasis. Remove Failed bereinigt gescheiterte Runs.

Controlling ist die Sicht für spätere Entscheidungen. Hier werden kombinierte Strategien, KI-Scores, Forward- und Backtest-Kennzahlen, Reviews und Nachtests zusammengeführt. Die Strategie-Detailanalyse zeigt Konsistenz, Score-Erklärung, Backtest- und Forward-Metriken, Equity-Kurve und Parameter. Sie ist der Ort, an dem man eine Strategie vor dem Live-Einsatz wirklich liest.

Dukascopy Data ist die Datenpipeline. BI5-Dateien werden stundenweise geladen, dekodiert, zu CSV/M1 aggregiert und anschließend per MT5-Importskript als Custom Symbol verfügbar gemacht. Dieser Bereich ist wichtig, wenn Brokerdaten unvollständig sind oder wenn ein Test mit kontrollierter externer Historie laufen soll (ausführlich in Kapitel 10).

Settings ist die technische Basis: MT5 Terminal Path, MT4 Terminal Path, Portable Mode, Output Directory, Data Directory, Default Deposit, Currency, Leverage, Default Model und Broker Timezone Offset. Viele scheinbare Backtestfehler sind eigentlich Settings-Fehler. Wenn keine Reports erscheinen, sollte man hier beginnen.

Reports sind Belege. Ein professioneller Workflow hält SET-Datei, HTML/XML-Report, PDF-Report, Scorecard und Validierung zusammen. Der Best-Ordner ist kein Sammelplatz für alles, was gut aussah, sondern für Kandidaten, die die vorhandenen Gates bestanden haben. Sobald Step-7-Ergebnisse existieren, gehören nur PASSED-Kandidaten dorthin.

![Best-Strategies-Tabelle: kombinierter Score, KI-Wert, Backtest- und Forward-Kennzahlen.](images/backtester_best_strategies.png)

![Strategie-Detailanalyse mit Konsistenz, Score-Erklärung, Kennzahlen, Equity-Kurve und Parametern.](images/backtester_strategy_detail_analysis.png)

---

## Kapitel 7: Architektur und Engine

### Vier Ströme

Die Anwendung ist ein Java-17/Maven-Projekt mit JavaFX als aktueller Hauptoberfläche, einer älteren Swing-Schicht, SQLite als lokaler Persistenz und OpenPDF/ReportLab-ähnlicher Report-Logik im Java-Code. Maven baut eine Fat-JAR mit `com.backtester.Main` als Einstiegspunkt. Der Main-Pfad prüft zuerst den CLI-Modus, initialisiert AppConfig, räumt alte MetaTrader-Prozesse auf und startet dann die JavaFX-App.

Architektonisch gibt es vier große Ströme:
- **Bedien-Strom:** beginnt in MainView und den JavaFX-Views.
- **Ausführungs-Strom:** geht in engine-Klassen wie BacktestRunner und OptimizationRunner.
- **Ergebnis-Strom:** läuft über report-Klassen, die XML/HTM parsen und Scorecards erzeugen.
- **Gedächtnis-Strom:** endet in DatabaseManager, der Einstellungen, Historie, Workflows, Sensitivitätsdaten und Reviews in SQLite hält.

Eine wichtige Projektbesonderheit ist die Koexistenz von JavaFX und Swing. Swing ist nicht wertloser Altbestand: viele Konzepte und Panels zeigen die Entwicklung des Werkzeugs und bleiben als Funktionsschicht vorhanden. Für die aktuelle Nutzerführung ist jedoch JavaFX entscheidend, besonders WorkflowView, WorkflowConfigDialogs, ControllingView und die spezialisierten Dialoge für Strategieauswertung.

Für Entwickler ist die wichtigste Architekturregel: **Die UI darf nicht zur einzigen Wahrheit werden.** Wenn ein Button eine Strategie exportiert, muss die fachliche Entscheidung im WorkflowEngine-Zustand, in Validierungsergebnissen und in Datenbankeinträgen wiederzufinden sein. Nur so kann das Projekt später erweitert werden, ohne dass die Anti-Curvefitting-Gates durch eine neue Oberflächenaktion umgangen werden.

### Die Engine im Detail

**BacktestRunner** erzeugt einen Ausgabepfad, schreibt `tester.ini`, bereinigt alte Reports, prüft auf laufende MetaTrader-Prozesse, startet `terminal.exe` oder `terminal64.exe` und konsumiert Prozessausgaben, um Deadlocks zu vermeiden. Danach wartet er auf Abschluss oder Timeout, sucht den erzeugten Report und übergibt ihn an ReportParser.

**IniGenerator** ist klein, aber kritisch. Er kapselt die Unterschiede zwischen MT4 und MT5. MT4 erwartet andere Schlüssel wie `TestExpert` und `TestSymbol`, MT5 nutzt `Expert` und `Symbol`. Auch Modellwerte und Konfigurationspfade unterscheiden sich. Indem diese Logik zentral bleibt, müssen Runner und UI nicht an jeder Stelle die Plattformdetails kennen.

**OptimizationRunner** arbeitet ähnlich wie BacktestRunner, jedoch mit Optimierungsparametern, Agentensteuerung, ForwardMode und XML-Parsing. Die Ergebnisobjekte landen in `OptimizationResult`. Dort werden Backtest-Passes und Forward-Passes zu `CombinedPass`-Strukturen zusammengeführt. Diese Kombination ist die Grundlage für Ranking, Filterung, Sensitivität und Export.

**WorkflowEngine** ist der fachliche Kern. Sie hält den Zustand der sieben Pipeline-Schritte, speichert und lädt Strategie-Konfigurationen, erzeugt Optimierungsruns, filtert Kandidaten, startet Sensitivitätsanalysen, ruft die KI-Bewertung auf, kombiniert Performance- und Stabilitätsscore und exportiert die finalen Strategien. Besonders wichtig ist die Regel, dass vorhandene Step-7-Validierungsergebnisse den Best-Ordner-Gate beeinflussen: Nicht bestandene oder nicht eindeutig bestandene Strategien werden nicht stillschweigend als beste Strategien kopiert.

**SensitivityRunner** variiert Parameter um den optimierten Wert und misst, wie stark Profit und Kennlinien reagieren. String-, Enum- und boolean-artige Parameter werden ausgelassen, weil sie keine sinnvolle kontinuierliche Kennlinie liefern. **RobustnessRunner** führt ähnliche Gedanken in Form von zeitverschobenen Scans und Plateau-Betrachtungen weiter. **ForwardSplit** spiegelt die MT5-Forward-Aufteilung, damit BT- und FW-Fenster in der Analyse nicht auseinanderdriften.

![Architekturübersicht des Backtester-Projekts.](images/backtester_optimizer.png)

### Zentrale Architekturentscheidungen

| Entscheidung | Umsetzung | Wirkung |
|---|---|---|
| MetaTrader bleibt Simulationsmotor | Die Java-App orchestriert, aber ersetzt den MT5/MT4 Strategy Tester nicht. | EA-Verhalten bleibt nah an der Zielplattform. |
| tester.ini statt GUI-Automation | Konfiguration wird als Datei erzeugt und per CLI gestartet. | Weniger fehleranfällig als Klick-Automation. |
| Runner kapseln Seiteneffekte | Prozessstart, Logs, Timeouts und Reportdateien liegen in engine. | UI bleibt testbarer und fachlich schlanker. |
| SQLite im Benutzerprofil | history.db liegt unter .mt5_backtester. | Nutzerdaten werden nicht ins Git geschrieben. |
| ScoreWeights als Single Source | Ranking und Scorecard lesen dieselben Defaults. | Keine divergierenden Bewertungen zwischen UI und Report. |
| ForwardSplit isoliert | MT5-Splitlogik ist in eigener Klasse und getestet. | BT/FW-Sensitivität bleibt fachlich korrekt. |
| Step 7 nach Exportauswahl | Finale OOS-Validierung passiert nach Optimierung und Portfolio-Auswahl. | Forward-Fenster wird nicht als unberührter Beweis missbraucht. |
| KI als Analyst | LLM interpretiert Kennlinien und Scores, trifft aber nicht allein die Freigabe. | Sprachliche Plausibilität bleibt von Datenvalidierung getrennt. |
| Best-Ordner-Gate | Validierungsergebnisse beeinflussen Export in exports_gut. | Fehlgeschlagene Kandidaten werden nicht als Top-Strategien präsentiert. |
| MCP read-only | Der MCP-Server erlaubt nur lesende SQLite-Abfragen. | KI-Assistenten können analysieren, aber Daten nicht verändern. |
| Dukascopy als Datenpipeline | BI5-Download, Decode, CSV und MT5-Import sind eigene Schritte. | Datenqualität wird nachvollziehbar. |
| JavaFX als primäre UI | Moderne Views tragen die aktive Nutzerführung. | Swing kann koexistieren, ohne die Hauptarchitektur zu blockieren. |
| Reports als Belege | PDF/HTML/SET werden zusammen exportiert. | Strategieentscheidung bleibt nachvollziehbar. |
| Tests als Methodenschutz | ForwardSplit, Workflow-Gates und Scorecard sind regressionsrelevant. | Fachliche Schutzlogik wird bei Änderungen nicht still gebrochen. |

---

## Kapitel 8: Reporting, Scoring und Persistenz

Reporting ist im Projekt mehr als Formatierung. Reports sind Belege. **ReportParser** extrahiert Kennzahlen aus MT4/MT5-Reports, **OptimizationReportParser** liest Optimierungsergebnisse, **MultiReportGenerator** erzeugt aggregierte HTML-Reports und **RobustnessScorecardGenerator** visualisiert die Bewertungslogik. **PdfReportGenerator** erzeugt Strategie-Reports für Exportpakete.

Der Score ist bewusst mehrsäulig. Die aktuelle `ScoreWeights`-Klasse enthält Gewichte für Backtest-Profitabilität, Forward-Profitabilität, Konsistenz, Risiko, Sharpe-basierte Equity-Konsistenz, Stichprobengröße, Forward-Trades und Recovery. Frühere synthetische oder hart kodierte Säulen (Symmetrie als Konstante 0.80, Tail-Risk aus angenommener Verlustverteilung, R²/SQN auf einer *zufällig generierten* Equity-Kurve) wurden entfernt. Das ist fachlich sauber, weil ein Score nur so gut ist wie die Daten, aus denen er besteht.

**DatabaseManager** legt die SQLite-Datenbank im Benutzerprofil unter `.mt5_backtester` an. Dort liegen `HISTORY_RUNS`, `WORKFLOW_STATE`, `SENSITIVITY_DETAIL`, `APP_SETTINGS`, `KI_REPORTS`, `MULTI_BACKTEST_BATCHES`, `EA_PARAMETER_SETTINGS`, `STRATEGY_REVIEWS`, `STRATEGY_AUTOMATIC_REVIEWS` und weitere Tabellen. Diese Datenbank ist die Brücke zwischen laufender GUI, Historie, Controlling und MCP-Server.

Der MCP-Server in `mcp-server/backtester_mcp.py` öffnet die SQLite-Datenbank lesend und stellt Tools wie `get_sensitivity_overview`, `get_sensitivity_for_pass`, `get_fragile_parameters`, `get_robust_strategies`, `get_parameter_curve`, `get_optimization_history` und `query_database` bereit. Damit kann ein lokaler KI-Assistent nicht nur freie Texte lesen, sondern strukturierte Backtester-Daten abfragen.

![Scorecard-Modell mit realen Kennzahlen.](images/backtester_score_weighting.png)

---

## Kapitel 9: Der KI-Ansatz

Die KI im Backtester ist kein Handelssystem und kein Ersatz für Validierung. Sie ist ein Analysewerkzeug für Muster, die in Tabellen schwer erkennbar sind. Der **LlmAnalysisService** lädt Sensitivitätsdaten aus der Datenbank, fügt Performance-Kennzahlen hinzu, baut einen deutschen Prompt und ruft OpenRouter auf. Das Modell soll pro Pass eine Tabelle, `STABILITY_SCORE`-Zeilen und eine knappe Begründung liefern.

Der Prompt zwingt die KI zu einer strukturierten Aufgabe: Kurvenformanalyse, CV-Analyse, Performance-Kontext und BT/FW-Konsistenz. Die KI soll unterscheiden, ob ein Parameter ein Plateau, eine Glocke, einen Peak, eine Klippe oder chaotisches Verhalten zeigt. Diese Begriffe sind didaktisch stark, weil sie den Nutzer von reinen Kennzahlen zu Formverständnis führen.

Im Workflow wird der KI-Score nicht absolut gesetzt. Step 6 kombiniert den numerischen Combined Score mit dem KI-Stabilitätsscore. Standardmäßig zählt Performance zu 60 Prozent und KI-Stabilität zu 40 Prozent. Gleichzeitig gibt es ein KI-Gate: Kandidaten mit sehr niedriger KI-Bewertung werden ausgefiltert. Wenn alle Kandidaten durchfallen, erzeugt der Export eine sichtbare Warnung, damit ein Notfall-Fallback nicht wie eine normale Validierung aussieht.

Grenzen bleiben wichtig. Ein Sprachmodell kann Plausibilität formulieren und Muster benennen, aber es erzeugt keine statistische Gewissheit. Es kennt weder die zukünftige Marktstruktur noch die tatsächliche Broker-Ausführung. Darum darf die KI-Auswertung nie den Step-7-Backtest ersetzen. Sie ist ein sehr nützlicher Filter zwischen Sensitivität und Portfolio-Auswahl, aber der letzte Beleg muss aus Daten kommen, nicht aus Sprache.

![KI-Analyse im Sensitivity-Tab: OpenRouter verarbeitet Performance- und Stabilitätsdaten.](images/backtester_ki_analysis.png)

---

## Kapitel 10: Datenversorgung mit Dukascopy und MT5 Custom Symbols

Backtests sind nur so gut wie ihre Daten. Das Projekt enthält deshalb eine Dukascopy-Schicht.

- **DukascopyDownloader** baut stundenweise Download-Aufgaben, speichert BI5-Dateien in einer Symbol/Jahr/Monat/Tag-Struktur und kennt symbolabhängige Preis-Punkt-Multiplikatoren.
- **Bi5Decoder** liest die LZMA-komprimierten Binärdateien und erzeugt Tick-Objekte mit Bid, Ask, Volumen und Zeitstempel.
- **CsvConverter** aggregiert Ticks zu M1-Bars und schreibt CSV-Dateien in einem Format, das für den Import in MetaTrader geeignet ist.
- **Mt5DataImporter** erzeugt und startet ein MQL5-Skript, um CSV-Daten als Custom Symbol in MT5 zu importieren.
- **CustomSymbolManager** merkt sich lokale Symbol-Metadaten wie Originalsymbol, Datenzeitraum, Digits und Aktualisierungsdatum.

Der didaktische Nutzen dieser Schicht ist groß: Sie trennt den Test von einem zufälligen Brokerfeed. Das macht Ergebnisse nicht automatisch wahr, aber es macht Datenqualität bewusster. Ein Nutzer kann sehen, welche Daten geladen wurden, welche Zeiträume fehlen und welche Symbole als Custom Symbols für Tests verfügbar sind.

![Datenfluss von der externen Tickquelle bis zur MT5-Testumgebung.](images/backtester_platform.png)

---

## Kapitel 11: Parameter-Referenz

Parameter sind die Sprache, in der der Backtester mit MetaTrader, der Datenbank, der KI und dem Nutzer spricht. Ein Parameter ist dabei nie nur ein Feld. Er hat einen Ort, eine Default-Annahme, einen fachlichen Effekt und oft auch eine Nebenwirkung auf Robustheit oder Reproduzierbarkeit.

EA-Parameter werden über `EaParameter` und `EaParameterManager` verwaltet. Ein Parameter kann Name, Anzeigename, Wert, Default-Wert, Sektion, Optimierungsstart, Schrittweite, Ende, Aktivierungsflag und Typinformation tragen. Für SET-Dateien ist entscheidend, dass Werte korrekt geschrieben und mit optimierten Passes zusammengeführt werden. Bei falscher Zuordnung würde ein Report eine andere Strategie beschreiben als die exportierte Datei.

Die Score-Parameter verdienen besondere Vorsicht. Mehr Gewicht für Forward-Trades bestraft dünne Evidenz. Mehr Gewicht für Recovery belohnt Erholung nach Drawdown. Mehr Gewicht für Profit kann Strategien nach oben schieben, die fachlich fragiler sind. Darum sind die Gewichte konfigurierbar, aber sie sollten nicht nachträglich so eingestellt werden, dass ein Lieblingskandidat gewinnt.

| Parameter | Gruppe | Bedeutung | Typische Werte |
|---|---|---|---|
| MT5 Terminal Path | config | Pfad zur terminal64.exe. Ohne gültigen Pfad kann kein MT5-Prozess gestartet werden. | C:\Program Files\MetaTrader 5\terminal64.exe |
| MT4 Terminal Path | config | Pfad zur terminal.exe. Wird genutzt, wenn ein Expert Advisor als MT4-Artefakt erkannt wird. | C:\Program Files\MetaTrader 4\terminal.exe |
| Data Directory | config | Ablage für Dukascopy-Daten, konvertierte CSVs und lokale Marktdaten. | data |
| Reports Directory | config | Ziel für Backtest-, Optimierungs- und HTML/PDF-Reports. | backtest_reports |
| Export Directory | config | Ziel für normale Portfolio- und SET-Datei-Exporte. | exports |
| Best Export Directory | config | Ziel für sehr gute Strategien; nach Step 7 nur für PASSED-Validierungen. | exports_gut |
| Portable Mode | config | Startet MT5 mit /portable, damit Pfade und Profile kontrollierbarer bleiben. | true/false |
| Backtest Timeout | config | Freeze-Schutz für MetaTrader-Prozesse. Lange Optimierungen brauchen höheren Wert. | Minuten |
| Broker Timezone Offset | config | Zeitverschiebung beim Umwandeln externer Daten in Brokerzeit. | 0 |
| Expert | backtest | Pfad oder relativer Name des Expert Advisors, der getestet wird. | MQL5\Experts\EA.ex5 |
| ExpertParameters | backtest | SET-Datei oder Parameterprofil, das MT5 laden soll. | *.set |
| Symbol | backtest | Markt, auf dem getestet wird. Muss in MT5 vorhanden sein. | EURUSD, XAUUSD |
| Period | backtest | Zeiteinheit des Tests. | M1, M5, M15, H1, D1 |
| Model | backtest | MT5-Modell für Tick-Qualität. Höhere Genauigkeit kostet Laufzeit. | 0, 1, 2 |
| ExecutionMode | backtest | MetaTrader-Ausführungsmodell für Order-Simulation. | 0 |
| FromDate/ToDate | backtest | Historisches Testfenster. Fachlich entscheidend für In-Sample und OOS. | YYYY-MM-DD |
| Deposit | backtest | Startkapital für Performance-Kennzahlen. | 10000 |
| Currency | backtest | Kontowährung für Reports. | USD |
| Leverage | backtest | Hebelannahme für Strategie-Tester. | 1:100 |
| ShutdownTerminal | backtest | Soll MT5 nach Abschluss automatisch schließen. | true |
| UseVirtualDesktop | backtest | Startet MetaTrader auf Desktop 2, um den Nutzerarbeitsplatz frei zu halten. | true/false |
| AutoKillMt5 | backtest | Erlaubt automatisches Beenden alter MetaTrader-Prozesse. | true/false |
| VisualMode | backtest | Startet den visuellen Tester für manuelle Beobachtung. | false |
| OptimizationMode | optimizer | 0 deaktiviert Optimierung, 1 Complete, 2 Genetic. Genetic ist schneller, Complete gründlicher. | 2 |
| OptimizationCriterion | optimizer | MT5-Kriterium für Ranking, etwa Balance, Profit Factor, Recovery oder Sharpe. | 0–6 |
| ForwardMode | optimizer | Teilt Optimierungsfenster in Backtest und Forward auf. | 0, 1, 2, 3, 4 |
| ForwardDate | optimizer | Custom-Start des Forward-Fensters bei ForwardMode 4. | YYYY-MM-DD |
| UseLocal/Remote/Cloud | optimizer | Steuert, welche MT5-Agenten für Optimierung genutzt werden. | 1/0 |
| minBtProfit | workflow | Mindestgewinn im Backtest für Step-3-Kandidaten. | 0.01 |
| minFwProfit | workflow | Mindestgewinn im Forward-Fenster. | 0.01 |
| minBtTrades | workflow | Mindestanzahl Trades im Backtest gegen statistisch dünne Ergebnisse. | 100 |
| minFwTrades | workflow | Mindestanzahl Trades im Forward-Fenster. | 15 |
| maxBtDd/maxFwDd | workflow | Maximal tolerierter Drawdown in Backtest und Forward. | 100 |
| paramDiffPct | workflow | Diversity-Schwelle für Parameterunterschiede zwischen Kandidaten. | 0.10 |
| tradeDiffPct | workflow | Diversity-Schwelle für abweichende Trade-Anzahlen. | 0.15 |
| minDifferentParams | workflow | Mindestzahl unterschiedlicher Parameter für Portfolio-Diversität. | 2 |
| maxStrategiesToSelect | workflow | Maximale Zahl der Kandidaten nach Diversity-Filter. | 5 |
| OpenRouter API Key | ki | Lokaler API-Schlüssel für LLM-Auswertung; wird in SQLite gespeichert, nicht im Git. | leer |
| OpenRouter Model | ki | LLM-Modell für Stabilitätsanalyse. | openai/gpt-4o-mini |
| OpenRouter Prompt | ki | Prompt mit Tabellenformat, Kurvenform-Analyse und Score-Regeln. | DEFAULT_PROMPT |
| Performance Weight | ki | Gewicht des numerischen Performance-Scores im finalen Ranking. | 0.6 |
| Stability Weight | ki | Gewicht des KI-Stabilitätsscores im finalen Ranking. | 0.4 |
| wBtProfit | score | Gewicht für Backtest-Profitabilität. | 15 |
| wFwProfit | score | Gewicht für Forward-Profitabilität. | 15 |
| wConsistency | score | Gewicht für Verhältnis von Forward zu Backtest. | 10 |
| wRisk | score | Gewicht für Risiko/Drawdown-Verhältnis. | 10 |
| wEquityConsist | score | Gewicht für echte Sharpe-basierte Equity-Konsistenz. | 10 |
| wSampleSize | score | Gewicht für Stichprobengröße und Testdauer. | 25 |
| wFwTrades | score | Gewicht für Anzahl der Forward-Trades. | 30 |
| wRecovery | score | Gewicht für Recovery Factor. | 25 |
| recoveryMin/recoveryMax | score | Skalierungsbereich für Recovery-Faktor-Bewertung. | 1.0 / 5.0 |
| validationFromDate | validation | Start des echten Step-7-OOS-Fensters; leer bedeutet toDate + 1 Tag. | null |
| validationToDate | validation | Ende des Step-7-OOS-Fensters; leer bedeutet aktuelles Datum. | null |

---

## Fazit

Der Backtester ist mehr als ein Automatisierer für MetaTrader. Er ist ein methodisches Werkzeug, das die gefährlichste Versuchung der Strategieentwicklung sichtbar macht: eine schöne Vergangenheit mit einer robusten Zukunft zu verwechseln. Seine Stärke liegt in der Kombination aus Prozessautomatisierung, strukturierter Persistenz, mehrsäuligem Scoring, Sensitivität, KI-gestützter Musteranalyse und abschließender Out-of-Sample-Validierung.

Für Nutzer bedeutet das: Das Tool nimmt Arbeit ab, aber nicht Verantwortung. Man muss weiterhin Datenqualität, Trade-Anzahl, Drawdown, Marktregime und Plausibilität beurteilen. Für Entwickler bedeutet es: Jede Erweiterung sollte die Trennung von Optimierung, Auswahl und Validierung respektieren. Der beste Code in diesem Projekt ist nicht der, der den höchsten Score erzeugt, sondern der, der falsche Sicherheit schwerer macht.

---

## Anhang A: Glossar

| Begriff | Bedeutung |
|---|---|
| Backtest | Historische Simulation einer Handelsregel mit bekannten Marktdaten. |
| Forward-Test | Von MT5 abgetrennter Teil des Optimierungszeitraums, der zur ersten Plausibilisierung dient. |
| Out-of-Sample | Daten, die nicht in Optimierung oder Auswahl eingeflossen sind. |
| In-Sample | Datenbereich, in dem Parameter gesucht oder angepasst werden. |
| Curve Fitting | Übermäßige Anpassung an historische Zufallsdetails. |
| Overfitting | Statistische Überanpassung, die in neuen Daten typischerweise einbricht. |
| Walk-Forward | Wiederholte Optimierung und Validierung über rollierende Zeitfenster. |
| Expert Advisor | Automatisierte Handelsstrategie in MetaTrader. |
| SET-Datei | MetaTrader-Parameterdatei für Expert Advisors. |
| CombinedPass | Projektobjekt, das Backtest- und Forward-Pass zusammenführt. |
| Profit Factor | Verhältnis von Bruttogewinn zu Bruttoverlust. |
| Recovery Factor | Verhältnis von Gewinn zu maximalem Drawdown. |
| Sharpe Ratio | Risikoadjustierte Renditekennzahl. |
| Drawdown | Rückgang vom Equity-Hoch zum folgenden Tief. |
| Plateau | Parameterbereich, in dem Ergebnisse stabil bleiben. |
| Peak | Einzelner Spitzenwert ohne stabile Nachbarschaft. |
| Diversity Filter | Auswahlmechanismus, der zu ähnliche Strategien reduziert. |
| KI-Gate | Filter, der sehr fragile KI-bewertete Kandidaten aussortiert. |
| Step 7 | Finale Validierung auf unberührtem OOS-Fenster nach Portfolio-Auswahl. |
| Best-Ordner | Exportziel für besonders gute und nach Validierung akzeptierte Strategien. |
| Custom Symbol | In MT5 importiertes Symbol mit eigenen historischen Daten. |
| ScoreWeights | Single Source of Truth für Score-Gewichte im Projekt. |

---

## Anhang B: Quellen und Bildnachweis

### Quellen
- QuantStart: Successful Backtesting of Algorithmic Trading Strategies - Part I. https://www.quantstart.com/articles/Successful-Backtesting-of-Algorithmic-Trading-Strategies-Part-I/
- Investopedia: Backtesting and Forward Testing: The Importance of Correlation. https://www.investopedia.com/articles/trading/10/backtesting-walkforward-important-correlation.asp
- AlgoTrading101: Backtesting Biases and Risks. https://algotrading101.com/wiki/backtesting-biases-and-risks/
- Surmount: Walk-Forward Analysis vs. Backtesting. https://surmount.ai/walk-forward-analysis-vs-backtesting-pros-cons-best-practices
- QuantInsti: Walk-Forward Optimization. https://blog.quantinsti.com/walk-forward-optimization-introduction/

### Bilder
- Projektscreenshot: images/backtester_platform.png
- Projektscreenshot: images/backtester_ui1.png
- Projektscreenshot: images/backtester_ui3.png
- Projektscreenshot: images/backtester_ui2.png
- Projektscreenshot: images/multi-backtester-results.png
- Projektscreenshot: images/multi-backtester-config.png
- Projektscreenshot: images/backtester_optimizer.png
- Projektscreenshot: images/backtester_score_weighting.png
- Projektscreenshot: images/backtester_consistency_ratio.png
- Projektscreenshot: images/backtester_advanced_evaluator.png
- Projektscreenshot: images/backtester_sensitivity.png
- Projektscreenshot: images/backtester_ki_analysis.png
- Projektscreenshot: images/backtester_ki_evaluation_table.png
- Projektscreenshot: images/backtester_best_strategies.png
- Projektscreenshot: images/backtester_strategy_detail_analysis.png

---

## Anhang C: Häufige Probleme

| Symptom | Diagnose / Lösung |
|---|---|
| MetaTrader startet und beendet sich sofort | Oft läuft bereits eine Instanz im gleichen portable-Verzeichnis. ProcessGuard/AutoKill prüfen und MT5 sauber beenden. |
| Kein Report wird gefunden | Report-Pfad, ShutdownTerminal, alte Reportdateien und Tester-Logs prüfen. BacktestRunner wartet auf erwartete Reportnamen. |
| Optimierung hat 0 Pässe | Parameterbereiche, OptimizationMode, EA-Kompilierung und MT5-Tester-Log prüfen. |
| Forward-Werte fehlen | ForwardMode deaktiviert oder MT5 hat keinen Forward-Report erzeugt. requireForward-Filter beachten. |
| Step 7 ist nicht startbar | Validierungsfenster muss nach dem Optimierungs-ToDate liegen und ein sinnvolles Enddatum besitzen. |
| Strategie wird nicht in Best kopiert | Nach vorhandenen Step-7-Ergebnissen dürfen nur PASSED-Kandidaten in den Best-Ordner. |
| KI-Analyse meldet keinen API-Key | OpenRouter-Schlüssel in KI-Einstellungen setzen; er wird lokal in SQLite gespeichert. |
| Dukascopy-Daten fehlen für einzelne Tage | Download-Scan nutzen; Wochenenden und Feiertage können keine Ticks liefern. |
| CSV-Import in MT5 klappt nicht | Custom Symbol Name, Digits, Skriptdeployment und MT5-Log prüfen. |
| Parameter wirken falsch exportiert | SET-Merge, EaParameterManager und Pass-Parameterwerte kontrollieren. |
| MCP findet Datenbank nicht | Backtester einmal starten, damit %USERPROFILE%/.mt5_backtester/history.db angelegt wird. |
