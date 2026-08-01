# Mastering the Backtester

Ein Leitfaden zur robusten Strategieoptimierung und zur Architektur des Backtester-Projekts

Version 1.2 - 08.07.2026

## Inhaltsverzeichnis
- Bedienungsanleitung 1: Orientierung im Programm
- Bedienungsanleitung 2: Backtester - Einzeltests richtig ausfuehren
- Bedienungsanleitung 3: Multi-Backtester - Maerkte und Timeframes vergleichen
- Bedienungsanleitung 4: Optimizer, Filtersettings und Score-Gewichtung
- Bedienungsanleitung 5: Der 8-Phasen-Workflow in der Praxis
- Bedienungsanleitung 6: Robustness, Controlling, Daten, Settings und Reports
- Vorwort: Warum dieses Buch existiert
- Kapitel 1: Grundlagen des Backtestings
- Kapitel 2: Curve Fitting, Overfitting und Biases
- Kapitel 3: Architektur des Backtester-Projekts
- Kapitel 4: Benutzeroberflaeche und Bedienlogik
- Kapitel 5: Engine Deep Dive
- Kapitel 6: Reporting, Scoring und Persistenz
- Kapitel 7: Der KI-Ansatz
- Kapitel 8: Datenversorgung mit Dukascopy und MT5 Custom Symbols
- Kapitel 9: Parameter-Referenz
- Kapitel 10: Robuste Optimierung in der Praxis
- Fazit
- Anhang A: Parameter-Referenz
- Anhang B: Sourcecode-Modulindex
- Anhang C: Kritische Klassen und Entwicklerleitfaden
- Anhang D: Betriebschecklisten und Troubleshooting
- Anhang E: Glossar
- Anhang F: Quellen und Bildnachweis

## Bedienungsanleitung 1: Orientierung im Programm

Die Anwendung ist kein einzelner Backtest-Knopf, sondern eine Arbeitsumgebung fuer Strategieentwicklung. Die Registerkarten bilden einen typischen Lebenszyklus ab: Zuerst werden globale Pfade und Daten vorbereitet, dann werden Einzeltests und Batchtests genutzt, danach folgt die Optimierung mit Forward-Analyse, Sensitivitaet, KI-Bewertung, Portfolio-Auswahl und spaeteres Controlling. Wer diese Reihenfolge versteht, vermeidet viele typische Fehlinterpretationen.

Im Alltag beginnt man selten direkt im Workflow. Ein neuer EA wird oft zuerst im Backtest-Tab geprueft: laeuft er ueberhaupt, schreibt MT5 einen Report, stimmen Symbol und Zeitraum? Danach kann der Multi-Backtester zeigen, auf welchen Maerkten und Timeframes die Strategie grundsaetzlich reagiert. Erst wenn diese Vorarbeit sinnvoll aussieht, lohnt sich die Optimierung. Genau diese Trennung macht die Ergebnisse belastbarer.

Die Registerkarte Optimizer ist die tiefste Einzelsicht. Dort liegen Suchraum, Forward-Modus, Combined Analysis, Filtersettings, Score-Gewichtung, Advanced Evaluator, Selected-Tab und Sensitivitaetsanalyse. Der Workflow Automator fasst diese Funktionen in eine gefuehrte Pipeline zusammen. Controlling und Database sind danach die Orte fuer Rueckblick, Nachtests und Pflege der erzeugten Strategien.

Professionelle Nutzung bedeutet: Nicht jedes gute Zwischenergebnis wird sofort als Strategie akzeptiert. Ein Einzeltest beantwortet nur, ob ein Parameter-Set in einem Zeitraum funktioniert hat. Ein Multi-Backtest beantwortet, ob eine Idee ueber Maerkte streut. Eine Optimierung beantwortet, welche Parameterkombinationen in einem Suchraum auffallen. Erst der Workflow verbindet diese Antworten zu einem belastbaren Entscheidungsprozess.

- Vor jedem Lauf: Settings, MT5-Pfad, portable Mode, Reportordner und Datenqualitaet pruefen.
- Bei neuen EAs zuerst einen kleinen Einzeltest starten, bevor Batch oder Optimierung laufen.
- Forward- und OOS-Fenster schon vor der Optimierung planen.
- Reports nicht nur nach Profit lesen: Tradezahl, Drawdown, Recovery, Sharpe und Equity-Form zaehlen.

![Gesamtbild der Plattformidee: Backtesting, Optimierung, Batch Running und Performance Analytics als zusammenhaengender Arbeitsplatz.](images/backtester_platform.png)

### Menuepunkte und Arbeitsbereiche

| Bereich | Zweck | Bedienung | Wann nutzen |
|---|---|---|---|
| Backtest | Einzelner MT4/MT5-Test | EA, Symbol, Zeitraum, Konto, Tickmodell und Parameter setzen; danach Report, Historie und HTML-Verzeichnis oeffnen. | Fuer schnelle Plausibilitaet und finale Nachtests einzelner Parameter-Sets. |
| Multi-Backtester | Batch ueber Maerkte und Timeframes | Ein EA wird mit globalen Konto- und Datumsannahmen ueber mehrere Symbol/Perioden-Kombinationen getestet. | Fuer Markt-Screening: Wo funktioniert die Strategie, wo nicht? |
| Optimizer | MT5-Optimierung und Analyse | Parameter-Suchraeume, Forward-Modus, Combined Analysis, Filter, Score-Gewichtung, Advanced Evaluator und Sensitivitaet. | Fuer systematische Parametersuche mit Anti-Curvefitting-Gates. |
| Robustness | Robustheitsscans | Parameter- und Zeitverschiebungen fuer einzelne Konfigurationen pruefen. | Fuer Stresstests ausserhalb des grossen Workflows. |
| Workflow Automator | Gefuehrte Pipeline | Setup, Optimierung, Diversity, Sensitivitaet, KI, Portfolio und OOS-Validierung werden als Zustand gefuehrt. | Fuer ernsthafte Strategieauswahl mit nachvollziehbaren Gates. |
| Controlling | Nachtest und Strategiepflege | Gespeicherte Strategien, Reviews, Nachtests und Exporte kontrollieren. | Fuer laufende Qualitaetssicherung nach dem ersten Export. |
| Database | Historie | Backtests, Optimierungen und gespeicherte Ergebnisse anzeigen, oeffnen oder bereinigen. | Fuer Nachvollziehbarkeit und Aufraeumen alter Runs. |
| Dukascopy Data | Marktdatenversorgung | BI5-Tickdaten laden, scannen, in CSV/M1 konvertieren und als MT5 Custom Symbol importieren. | Fuer bessere Datenkontrolle jenseits der Broker-Historie. |
| Settings | Globale Pfade und Defaults | MT4/MT5-Pfade, portable Mode, Report-/Datenpfade, Deposit, Waehrung, Hebel, Tickmodell und Zeitzone. | Vor jedem produktiven Lauf pruefen. |
| Log | Live-Protokoll | Status, Fehler und laufende Prozessmeldungen ansehen. | Wenn MT5 haengt, kein Report erscheint oder ein Batch unklar stoppt. |
| Manual | In-App-Hilfe | Kurze Bedienhilfe innerhalb der Anwendung. | Fuer schnelle Erinnerung; dieses Buch ist die ausfuehrliche Referenz. |

## Bedienungsanleitung 2: Backtester - Einzeltests richtig ausfuehren

Der Backtest-Tab ist die Werkbank fuer einzelne MT4/MT5-Laeufe. Er dient drei Zwecken: technische Funktionspruefung eines EAs, schnelle Plausibilitaet eines Parameter-Sets und finaler Nachtest einer bereits ausgewaehlten Strategie. Die Maske besteht aus Backtest Configuration oben und Backtest History & Results unten.

Expert Advisor waehlt den EA. Symbol und Period bestimmen Markt und Timeframe. Dates und To setzen das historische Fenster. Deposit, Currency und Leverage geben die Kontoannahmen vor. Tick Model bestimmt, wie genau MT5 historische Bewegungen simuliert. Der Start Backtest-Button startet den normalen, reproduzierbaren Lauf; Visual Mode ist fuer Diagnose gedacht; Manual Mode haelt MT4/MT5 offen.

Die Ergebnistabelle zeigt Expert, Symbol, Period, Profit, Trades, Win Rate und Drawdown. Ein gruener Profit allein reicht nicht. Zwei Trades mit 100 Prozent Win Rate sind statistisch nahezu wertlos; 300 Trades mit moderatem Profit und kontrolliertem Drawdown sind aussagekraeftiger. Open HTML Report oeffnet den Detailreport, Open Directory fuehrt zu den erzeugten Dateien.

Best Practice: Einzeltests nicht als Optimierungsersatz verwenden. Wer manuell so lange Werte aendert, bis ein Backtest schoen aussieht, betreibt ebenfalls Curve Fitting. Der Einzeltest ist stark, wenn er eine konkrete Hypothese prueft: Laeuft der EA auf XAUUSD H1? Bleibt ein aus dem Workflow exportiertes SET im spaeteren Zeitraum plausibel? Sind die Reportdateien vollstaendig?

Wenn ein Lauf kein Ergebnis erzeugt, ist der Log-Tab der naechste Ort. Typische Ursachen sind falscher Terminalpfad, nicht vorhandenes Symbol, EA-Kompilierungsfehler, blockierte MT5-Instanz, fehlendes Datenfenster oder ein Report, den MT5 anders ablegt als erwartet. Manual Mode hilft bei Diagnose, sollte aber bei Batch- und Workflow-Laeufen nicht dauerhaft aktiv bleiben.

![Backtest-Hauptmaske: oben Einzeltest-Konfiguration, unten Historie und Ergebnisaktionen.](images/backtester_ui1.png)

![Einzelreport-Dialog mit Kennzahlen, Equity-Kurve, Detailstatistik und Report-Aktionen.](images/backtester_ui3.png)

### Backtester: alle Einstellungen und Aktionen

| Feld / Aktion | Funktion | Best Practice |
|---|---|---|
| Expert Advisor | Pfad oder relativer Name des EAs. Browse oeffnet die Dateiauswahl. | Ohne EA startet kein Test. Nach EA-Auswahl werden Parameterprofile geladen oder vorbereitet. |
| Symbol | Markt aus der festen Liste: AUDCAD bis XTIUSD. | Symbol muss in MT5 vorhanden sein. Bei Custom Symbols zuerst Datenimport pruefen. |
| Period | Zeiteinheit M1, M5, M15, M30, H1, H4, D1, W1 oder MN1. | Niedrige Timeframes brauchen mehr Datenqualitaet und Laufzeit. |
| Dates / To | Historisches Testfenster. | Nicht zu kurz waehlen; fuer finale Checks ein Fenster nutzen, das nicht zur Optimierung diente. |
| Deposit | Startkapital fuer MT5-Kennzahlen. | Konstant halten, wenn Ergebnisse verglichen werden. |
| Currency | Kontowaehrung: USD, EUR oder GBP in JavaFX-BacktestView. | Muss zu Broker- und Reportannahmen passen. |
| Leverage | Hebel als Text, z.B. 1:100. | Realistische Werte verwenden; Margin-Situationen nicht schoenrechnen. |
| Tick Model | Every tick, 1 minute OHLC, Open price only, Math calculations oder Every tick (real ticks). | Fuer schnelle Vorpruefung OHLC; fuer ernsthafte Validierung realistischeres Modell verwenden. |
| Manual Mode / Keep MT4/5 Open | Verhindert automatisches Schliessen von MT nach dem Lauf. | Gut zur Diagnose. Fuer Batchlaeufe besser automatisch schliessen lassen. |
| Start Backtest | Startet MT4/MT5 ohne Visualisierung und speichert Ergebnis in der Datenbank. | Standard fuer reproduzierbare Einzeltests. |
| Visual Mode | Startet den visuellen Tester und laesst Beobachtung im Terminal zu. | Nur zur Diagnose und Strategie-Verstaendnis, nicht fuer Massenlaeufe. |
| Cancel | Bricht einen laufenden Test ab. | Danach Log und Reportordner pruefen; ein abgebrochener Lauf kann unvollstaendige Dateien hinterlassen. |
| History & Results | Tabelle mit Expert, Symbol, Period, Profit, Trades, Win Rate, Drawdown. | Doppelklick/Buttons nutzen, um Report zu oeffnen; nicht nur Profit betrachten. |
| Open HTML Report | Oeffnet den erzeugten HTML/Report-Dialog. | Erste Sichtpruefung der Equity-Kurve, Statistik und Tradezahl. |
| Open Directory | Oeffnet das Reportverzeichnis. | Wichtig, um SET, HTM/XML und Exportartefakte zusammenzuhalten. |

## Bedienungsanleitung 3: Multi-Backtester - Maerkte und Timeframes vergleichen

Der Multi-Backtester automatisiert viele Einzeltests mit denselben globalen Annahmen. Er beantwortet nicht die Frage nach den besten Parametern, sondern die Vorfrage: Wo zeigt diese Strategie ueberhaupt Verhalten, das eine tiefere Analyse rechtfertigt? Dadurch spart man viel Zeit und vermeidet Optimierungen auf Maerkten, die schon im Basistest unplausibel sind.

Oben stehen Expert Advisor, Datum, Deposit, Currency, Leverage, Tick Model und Presets. Darunter befinden sich zwei Auswahllisten: Symbols und Timeframes. Jedes markierte Symbol wird mit jedem markierten Timeframe kombiniert. Drei Symbole und drei Timeframes erzeugen also neun Jobs. Start Batch arbeitet diese Warteschlange sequentiell ab und schreibt die Resultate in die Batch-Historie.

Presets sind hier besonders wichtig. Ein Preset speichert EA, Symbolauswahl, Timeframes und den Parameter-Snapshot. Neu erstellt ein Preset, Speichern ueberschreibt das gewaählte Preset mit den aktuellen Einstellungen, Aendern passt Namen und Inhalt an, Loeschen entfernt es. Damit lassen sich wiederkehrende Markt-Screenings reproduzieren.

Nach dem Lauf zeigt die Results Table Robot, Symbol, Period, Trades, Win Rate, Drawdown, Recovery Factor, Profit und Status. Open Multi-Report Node oeffnet den aggregierten Summary-Report. Show Single Report oeffnet den Report des markierten Einzelruns. Genau hier sollte man die grobe Auswertung machen: Gibt es Cluster nach Timeframe? Sind Gewinner nur einzelne Ausreisser? Haben profitable Runs genug Trades?

Best Practice: Den Multi-Backtester nicht als Lotterie verwenden. Wer alle Symbole und alle Timeframes testet und danach nur den besten Run nimmt, hat einen massiven Auswahlbias erzeugt. Besser ist eine explorative Phase mit Notizen: Welche Marktgruppen verhalten sich konsistent? Wo ist die Tradezahl ausreichend? Welche Kombinationen verdienen eine saubere Optimierung mit Forward-Split?

![Multi-Backtest-Summary-Report: Vergleich mehrerer Symbol/Timeframe-Runs mit Detailbereichen.](images/backtester_ui2.png)

![Einzelreport aus einem Multi-Backtest-Run mit Equity-Kurve und Kennzahlen.](images/multi-backtester-results.png)

![MT5-Reportausschnitt mit Kontostand-Kurve; im Projektbildbestand irrefuehrend als Multi-Konfiguration benannt.](images/multi-backtester-config.png)

### Multi-Backtester: alle Einstellungen und Aktionen