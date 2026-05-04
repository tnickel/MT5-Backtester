# MT5 Backtester – Ausfuehrliches Benutzerhandbuch

## Inhaltsverzeichnis
1. [Einleitung: Wozu dient der MT5 Backtester?](#1-einleitung-wozu-dient-der-mt5-backtester)
2. [Bereich: Settings (Einstellungen)](#2-bereich-settings-einstellungen)
3. [Bereich: Single Backtest (Einzelner Durchlauf)](#3-bereich-single-backtest-einzelner-durchlauf)
4. [Bereich: Multi-Backtester (Stapelverarbeitung)](#4-bereich-multi-backtester-stapelverarbeitung)
5. [Bereich: Optimizer (Strategie-Optimierung)](#5-bereich-optimizer-strategie-optimierung)
   5.1. [Combined Analysis Tab: Filter, Sortierung & Score-Gewichtung](#51-combined-analysis-tab-filter-sortierung--score-gewichtung)
   5.2. [Selected Tab (Staging)](#52-selected-tab-staging)
   5.3. [Sensitivity Analysis (Sensibilitaets-Analyse)](#53-sensitivity-analysis-sensibilitaets-analyse)
6. [Bereich: Robustness Scanner (Kennlinienfahrt)](#6-bereich-robustness-scanner-kennlinienfahrt)
7. [Bereich: Database (Datenbank)](#7-bereich-database-datenbank)
8. [Bereich: Dukascopy (Tickdaten-Download)](#8-bereich-dukascopy-tickdaten-download)
9. [Bereich: Log (Protokoll & Ueberwachung)](#9-bereich-log-protokoll--ueberwachung)
10. [MT5-Prozessschutz (Process Guard)](#10-mt5-prozessschutz-process-guard)

---

### 1. Einleitung: Wozu dient der MT5 Backtester?
Der **MT5 Backtester** ist eine spezialisierte und hochgradig automatisierte Desktop-Software, die eine Bruecke zum MetaTrader 5 (MT5) schlaegt, um professionellen Tradern und Entwicklern enorm viel Handarbeit abzunehmen. Normalerweise ist das Testen von Expert Advisors (EAs, automatischen Handelssystemen) im MT5 muehsam: Man muss jeden Parameter manuell in verschachtelten Menues setzen, einen Start-Button druecken, auf das Ergebnis warten und dann muehsam Berichte per Hand vergleichen.

**Die Hauptvorteile dieses Backtesters umfassen:**
- **Automatisierung von Massentests**: Starten Sie muehelos Hunderte von Kombinationen (verschiedene EAs, Symbole, Zeitrahmen) unbeaufsichtigt.
- **Spezialisierte Auswertungen**: Anders als im MT5 bietet diese Software eigene, hochaufloesende Charts und erweiterte Statistik-Auswertungen sowie Zusammenfassungen in visuell ansprechenden HTML-Dateien.
- **Robustheits-Check (Kennlinienfahrt)**: Diese einzigartige Funktion prueft systematisch, wie empfindlich die Parameter Ihrer EAs auf unsichtbare Marktschwankungen in der Historie reagieren (verhindert truegerisches "Overfitting").
- **Zentrale Parameterverwaltung**: Anstatt in diversen unuebersichtlichen `.set`-Dateien zu enden, werden alle EA-Parameter direkt in einer lokalen SQLite-Datenbank persistiert und lassen sich in Form von uebersichtlichen Listen bearbeiten und abspeichern.
- **Echtzeit-Logueberwachung**: Alle MT5-Logdateien werden waehrend der Ausfuehrung fortlaufend ueberwacht. Fehler werden sofort erkannt und im UI hervorgehoben.
- **Intelligenter Prozessschutz**: Vor jedem neuen MT5-Start wird geprueft, ob noch ein alter Prozess laeuft, und der Benutzer wird informiert.

---

### 2. Bereich: Settings (Einstellungen)
Dieses Tab (Reiter) bildet das Fundament der Software. Hier konfigurieren Sie die wichtigsten Pfade zu Ihrem MetaTrader 5 Terminal und legen Verzeichnisse fuer die auszugebenden Berichte fest. All diese Werte werden auf Ihrem Computer dauerhaft fuer den naechsten Start gespeichert.

**Eintraege und ihre Bedeutung:**
- **Terminal Path**: Geben Sie mit dem *Browse...*-Button den absoluten Dateipfad zur `terminal64.exe` an. Das ist die Hauptdatei, ueber die die Software Ihren MetaTrader 5 im Hintergrund fernsteuert.
- **Use Portable Mode (/portable flag)**: Ist dieses Kaestchen angekreuzt, wird MT5 beim Start der Parameter `/portable` uebergeben. Dies zwingt den MT5 dazu, seine Logdateien, Vorlagen und Historien nicht tief in den Windows Systemordnern (`AppData/Roaming/MetaQuotes/Terminal/`) zu vergraben, sondern exakt dort zu fuehren, wo Sie das Programm auf der Festplatte installiert haben (z. B. Laufwerk C:). Das wird hierfuer dringenst empfohlen!
- **Reports Output**: Bestimmt das Windows-Verzeichnis, in welchem alle spaeteren Zusammenfassungen, Grafiken (Charts), und HTML-Auswertungen abgespeichert werden (die Voreinstellung ist der Unterordner `backtest_reports`).
- **Data Directory**: Hier definieren Sie einen lokalen Sammelplatz fuer historische Marktdaten, die zum Beispiel spaeter ueber Dukascopy als Ticks heruntergeladen werden.
- **Default Deposit / Default Currency**: Um nicht in jedem Test erneut eingeben zu muessen, wie viel Startkapital zur Verfuegung steht, definieren Sie hier einen festen Standard-Betrag (Deposit, z. B. `10.000`) und eine Kontowaehrung (Currency, z. B. `USD` oder `EUR`).
- **Default Leverage**: Ihr gewuenschter Standard-Hebel fuer neue Testformulare (z. B. `1:100` oder `1:500`). Ein hoeherer Hebel verkleinert im Metatrader Simulator kuenstlich die erforderliche Maintenance-Margin fuer eingehende Trades.
- **Default Tick Model**: Das Standard-Algorithmusmodell, wie feingranular der MT5 die Preischarts aufbaut (genaue Erklaerung der Auspraegungen finden Sie im Kapitel "Single Backtest").
- **Broker Timezone (UTC+)**: Historische Rohdaten aus dem Internet werden meist als "UTC" bereitgestellt (Weltzeit). MetaTrader-Broker verwenden jedoch oft Osteuropaeische Zeit (oft UTC+2 in Normalzeit / UTC+3 in Sommerzeit). Diese Abweichung vom Nullmeridian stellen Sie hier ein, was bei externen Tickdaten extrem wichtig fuer den Abgleich der asiatischen, europaeischen oder amerikanischen Eroeffnungssitzungen des Tages ist.
- **Save Settings Button**: Bestaetigt alle Aenderungen unwiderruflich und schreibt diese in die Konfigurationsdatei der Software weg.

---

### 3. Bereich: Single Backtest (Einzelner Durchlauf)
Dieser Reiter eignet sich, um einen isolierten, einfachen Backtestausflug fuer eine konkrete Handelsstrategie (den Expert Advisor) vorzunehmen. Ideal zum Testen der Kernlogik und des Setups.

**Eintraege und ihre Bedeutung:**
- **Expert Advisor**: Nutzen Sie den `...` Button und steuern Sie direkt zu einer kompilierten EA-Datei (Zieldateiformat `.ex5`).
- **Zahnrad-Button (⚙) / EA Parameter Editor**: Ein Highlight der Software! Es oeffnet ein interaktives Zusatzfenster. Das System extrahiert hier vollautomatisch alle Variablen aus der Quellcode-Logik des EAs (Take Profit, Lot Size, Indikatoren-Werte) und packt sie in eine sortierbare Liste. Veraendern Sie Werte wie Sie moechten; das System hebt geaenderte Elemente hervor. Wenn Sie Ihre Einstellungen als Profil abspeichern und verwenden, springt die Info-Schrift darunter auf "Custom Config (x parameters modified)".
- **Symbol**: Ueber dieses Dropdown definieren Sie den spezifischen Finanzmarkt, auf welchem der Trading-Bot in diesem Lauf operiert (beispielsweise ein Waehrungspaar aus dem Devisenmarkt wie `EURUSD` oder `GBPUSD`).
- **Timeframe**: Das Zeitfenster fuer die Chart-Sicht, welche der EA als Hauptdatenquelle interpretiert, z.B. `M15` (Balken bestehen aus 15 Minuten) oder `H1` (Stundenkerzen).
- **Tick Model (Test-Modell)**: Beeinflusst drastisch die Rechenzeit und Simulationstreue Ihres Testumlaufs:
  - *Every tick (Jeder Tick)*: Bestmögliche historische Naehe. Simuliert oder verwendet lueckenlos jede noch so kleine Preisaenderung von Millisekunde zu Millisekunde, ist zeitintensiv, aber zwingend notwendig beim Skalpieren von Mikro-Punkten.
  - *1 Minute OHLC*: Schnell bei soliden Ergebnissen. Das System erzeugt kuenstlich 4 Berechnungsdaten (Open, High, Low, Close) innerhabelb von Minutenkerzen.
  - *Open prices only (Nur Eroeffnungspreise)*: Extrem schnell, aber der Marktpreis existiert hier pro Chart-Kerze quasi nur eine einzige logische Sekunde lang beim Start! Ausschliesslich nutzbar, wenn der Roboter nach Logik-Regeln strikt nur zu neuen Kerzen agiert.
- **From Date / To Date**: Das Datum fuer den Eintritt in den virtuellen Markt und das Datum des Test-Endes - ueber diesen Lebenszyklus muss Ihr MetaTrader auch ueber geladene historische Marktdaten im Terminal verfuegen, sonst bleibt das Ergebnis fehlerhaft leer.
- **Deposit, Currency, Leverage**: Veraendern Sie bei einem spezifischen Test den virtuell simulierten Kontostand gegenueber den Standard-Werten.
- **Start Backtest Button**: Der Test startet voellig geraeuschlos und ohne grafische Benutzeroberflaeche (headless) im Hintergrund - das Programm meldet sich, sobald das Ergebnis im Report-Fenster vorliegt!
- **Start Visual (MT5) Button**: Durchbricht die unsichtbare Automatisierung. MetaTrader 5 oeffnet sein Fenster und Sie koennen live in Zeitraffer auf dem Chart-Tickgitter verfolgen, an welchen Linien und Staenden der Roboter Kaeufe ("Buys") oder Leerverkaeufe ("Sells") umsetzt.

---

### 4. Bereich: Multi-Backtester (Stapelverarbeitung)
Der Zeit-Retter schlechthin! Umfassende EAs sollen meist nicht auf nur einen Wert eingestellt werden. Sie wollen herausfinden ob ihr EA nicht nur in M15-Charts fuer EURUSD, sondern auch im Stundenchart H1 fuer USDJPY stark funktioniert? Dafuer gibt es diesen Tab.

**Eintraege und ihre Bedeutung:**
- **Expert Advisors List**: Sie koennen mehr als einen Roboter markieren.
- **Symbols List (Symbole)**: Markieren Sie mit gedrueckter Maus oder `Strg`-Taste beliebig viele Handelsinstrumente von Interesse.
- **Timeframes List (Zeitebenen)**: Dasselbe Prinzip fuer Balkenperioden.
- **Was geschieht im Hintergrund?**: Die Software bildet aus Ihren Markierungen voellig selbstaendig und logisch korrekt absolut jede Kombination (Multiplikation der Eintraege) und stellt diese wie auf ein Montageband in eine Liste (Queue). Druecken Sie auf Ausfuehren, rattert die Software absolut fehlerfrei, eine Instanz nach der anderen, im reinen Hintergrund (ohne Crash-Potenzial!) durch Ihr Windows.
- **Das Endresultat**: Wurden alle z.B. 120 Durchlaeufe gemeistert, generiert der MT5 Backtester eine grosse, leicht vergleichbare HTML-Website-Datei in Ihren Output-Ordner. Dort sehen Sie nebeneinander sortiert, welche Zeitebene zu welchem Markt die meisten Profits hervorgebracht hat.
- **Einstellungen merken**: Ab Version 1.2.0 speichert der Multi-Backtester automatisch alle Ihre zuletzt gewaehlten Einstellungen (EAs, Symbole, Zeitrahmen, Datumsbereich, Tick Model, Deposit). Beim naechsten Programmstart werden diese Einstellungen exakt wiederhergestellt, sodass Sie direkt weiterarbeiten koennen.

---

### 5. Bereich: Optimizer (Strategie-Optimierung)
Ein exzellenter Trader weiss, dass ein System immer optimiert werden kann. Der Optimizer ist die Schaltzentrale, um mathematisch die staerksten Inputs fuer einen EA durch tausendfache Berechnungen zu filtern. Er kontrolliert die maechtige Optimierungs-Engine im MetaTrader 5 mit komfortabler Steuerung und erweiterter Hilfe.

#### Neue Funktionen in der aktuellen Version:
- **"Start (Keep MT5 Open)"-Modus**: Neben dem normalen Start gibt es einen zweiten Start-Button ("Start (Keep MT5 Open)"), der MT5 nach der Optimierung geoeffnet laesst. Praktisch zum schnellen visuellen Nachvollziehen der Top-Ergebnisse.
- **Automatische 1-Parameter-Warnung**: Wenn Sie einen Forward-Test mit nur einem optimierten Parameter starten moechten, erscheint eine Warnung. Der MT5-Genetische Algorithmus benoetigt mindestens 2 Parameter zur sinnvollen Berechnung.
- **Automatische Speicherung**: Alle Formular-Eingaben (EA, Symbol, Periode, Modell, Modus, Kriterium, Forward-Modus sowie die optimierten Parameter) werden automatisch gespeichert und beim naechsten Programmstart wiederhergestellt.

**Eintraege und ihre Bedeutung:**
- **Optimization Mode (Optimierungs-Verfahren)**:
  - *Disabled*: Normalfahrt (Keine Optimierung an).
  - *Slow Complete Algorithm*: Rechnet buchstaeblich gnadenlos Kombination fuer Kombination ab. Hat der Take Profit zwanzig Ebenen und der StopLoss dreissig, ergibt allein das 600 Fahrten. Bringt das staerkste isolierte Top-Resultat zutage, benoetigt fuer alles oberhalb von 3 Variablen astronomische PC-Rechenzeiten.
  - *Fast Genetic Algorithm*: Hier wird es revolutionaer. Der Markt wird mittels Evolution erforscht. Das System paart Variablen. Gute Profit-Ueberlebende kriegen Kinder miteinander (Kreuzungen guter Variablen), ineffiziente Zweige sterben restlos weg, ohne noch bis in jede letzte Ecke geprueft zu werden. Man kommt dem absoluten Ziel-Ergebnis 100-mal schneller ungemein nah.
  - *All Symbols Selected...*: Wendet stattdessen die immergleiche Konfig auf alle Maerkte an, welche gerade im Terminalfenster sichtbar verankert sind.
- **Optimization Criterion (Zielkriterium)**: Damit MT5 im Optimierer Erfolg haben kann, muss er wissen, wofuer er auf die Jagd geht:
  - *Balance (Max)*: Bringe reines, blankes Eigenkapital heim. Es wird alles geopfert fuer Profit. Gefahr: Meist extrem risikoreiche Parameter.
  - *Balance + Max Profit Factor*: Der Gewinn soll wachsen, aber das Verhaeltnis zwischen guten und miesen Trades muss dabei so hoch wie moeglich bleiben!
  - *Balance + Min Drawdown*: Suche mir die Einstellungen, bei der der simulierte Verlauf (die Equity) am sanftesten (ohne heftige Verlustrueckschlaege!) nach oben geht, ideal fuer Sicherheitsbewusste.
  - *Balance + Max Sharpe Ratio*: Versuche Einstellungen zu picken, bei denen die Schwankungsbrillianz und Volatilitaet das insgesamte Marktrisiko weit in den Schatten stellen.
- **Forward Testing (Zukuunfts-Validierung)**: Der Schutz vor "Glueck". Stellt man dies z.B. ein auf "1/4", dann sucht der Genetic Algorithm bei seinem Durchrechnen ausschliesslich in den aeltesten 75 % Ihres angegebenen Zeitraums nach der ultimativen Einstellung. Hat er sie, friert er sie ein und jagt diese Parametrierung komplett unabhaengig ("Vorwaerts") durch die absolut unsichtbaren verbleibendsten neuesten 25 % an historischen Marktdaten! Erweist sich der Traumergebnistest auf einmal als Bankrott, hatte das Setup kein Konzept, sondern auf die alten Kerzen nur Zufall ueber-programmiert.
- **Agent Configuration (Rechenherzen)**: Wo gerechnet wird (Local entspricht den Kernen Ihres PCs, Remote Netzwerken und Cloud MQL5 den Rechenwelten des Herstellers MetaQuotes gegen Kreditaufladung).
- **Parameter Table (Raster der Variationen)**:
  - *Start*: Wo startet die Suche einer Variablen (z.B. Start 20 fuer ein TakeProfit).
  - *Step*: In welchen Treppenstufen wird gesucht (bei Step 2 rechnet er Lauf 1 in 20, Lauf 2 in 22, Lauf 3 in 24...).
  - *End*: Welcher Grenzwert nicht ueberschritten werden soll (z.B. End 30).
  - Um fuer einen Parameter nach Variationen iterieren zu lassen, ist der Haken vorne in seiner Table-Zeile strikte Voraussetzung!
- **AutoConfig Button (Der Zauberstab)**: Spart immens Zeit. Statt unzaehlige Raster mit Logik vollzuschreiben, klickt man hier. Die Implementierung errechnet auf reiner Heuristik von Ihren Grundparametern her perfekte logarithmische Distanzen fuer Steps, Startpunkte unter, sowie Endziele ueber dem Basiswert und setzt in der Sekunde automatisiert die Auswahlschalter um!
- **Load .set / Save .set**: Laden und Speichern von MT5-Set-Dateien fuer die Parameterverwaltung.

---

#### 5.1 Combined Analysis Tab: Filter, Sortierung & Score-Gewichtung
Neu in der aktuellen Version: Nach einer Optimierung mit Forward-Test erscheint im Bereich **"Combined Analysis"** die intelligente Top-Ergebnisliste. Diese vereint Backtest- und Forward-Daten in einer einzigen, uebersichtlichen Tabelle.

**Farbkodierung der Werte:**
- **Score-Spalte**: Der berechnete Gesamtscore erscheint in drei Farben:
  - Gruen (≥ 70): Hervorragendes, robustes Ergebnis.
  - Gelb (45 – 69): Akzeptabel, aber mit Vorsicht zu geniessen.
  - Rot (< 45): Schlecht / instabil.
- **Konsistenz-Spalte**: Zeigt das Verhaeltnis Forward/Backtest an (1.0 = perfekte Reproduzierbarkeit). Ebenfalls gruen/gelb/rot kodiert.
- **Profit-Spalten**: Positive Werte gruen, negative rot.
- **Drawdown-Spalten**: < 15 % gruen, 15–25 % gelb, > 25 % rot.

**Filter & Sortierung (Filter & Sortierung...)**:
- Klicken Sie auf den blauen Button, um einen Filter-Dialog zu oeffnen. Hier koennen Sie **sechs Schwellwerte** gleichzeitig definieren:
  - Min. BT Profit / Min. FW Profit
  - Min. BT Trades / Min. FW Trades
  - Max. BT Drawdown% / Max. FW Drawdown%
- Ueber die Dropdown-Box "Sortierung" waehlen Sie aus sechs verschiedenen Sortierkriterien: kombinierter Score, BT/FW Profit, Konsistenz, FW Profit Factor, FW Drawdown oder Pass-Nummer.
- Mit der Checkbox **"Nur Passes mit Forward-Ergebnis"** blenden Sie alle Eintraege ohne Forward-Daten aus.
- Der Counter zeigt stets die aktuelle Anzahl der angezeigten Ergebnisse an.

**Score-Gewichtung (Score-Gewichtung...)**:
- Klicken Sie auf den gelben Button, um zu bestimmen, wie der kombinierte Score berechnet wird.
- **Fuenf Gewichte** steuerbar per Slider (0–100 %):
  - BT Profit (Standard: 25 %)
  - FW Profit (Standard: 35 %)
  - Konsistenz FW/BT (Standard: 20 %)
  - FW Profit Factor (Standard: 10 %)
  - Drawdown-Strafe (Standard: 5 %)
- Die Gewichte muessen nicht exakt 100 ergeben – das System normalisiert automatisch.

**Strategien auswaehlen (Select Strategies)**:
- Markieren Sie beliebige Zeilen in der Tabelle (mehrere per Strg/Shift) und klicken Sie auf **"Select Strategies"**, um diese in das neue **"Selected"-Tab** zu uebertragen. Von dort koennen Sie sie spaeter direkt einer **Sensitivity Analysis** (Stresstest) unterziehen.

**Ergebnisse loeschen (Loeschen)**:
- Markieren Sie unerwuenschte Zeilen und klicken Sie auf **"Loeschen"**. Die Eintraege werden nach Sicherheitsabfrage aus der Tabelle und dem Speicher entfernt.

---

#### 5.2 Selected Tab (Staging)
Das **"Selected"-Tab** ist Ihre persoenliche Kurzliste fuer Top-Strategien.

- **Zweck**: Sammeln Sie die interessantesten Ergebnisse aus verschiedenen Optimierungslaeufen an einem Ort, ohne die Uebersicht zu verlieren.
- **Hinzufuegen**: Im "Combined Analysis"-Tab waehlen Sie Strategien aus und klicken auf **"Select Strategies"**.
- **Verwalten**: Im Selected-Tab koennen Sie Eintraege entfernen ("Remove Selected") oder die gesamte Liste leeren ("Clear All").
- **Verwendung**: Diese Liste dient als Eingabe fuer den **Robustness Scanner** (Kennlinienfahrt) im Modus **"Use all strategies in Selected tab"** oder direkt fuer die **Sensitivity Analysis**.

---

#### 5.3 Sensitivity Analysis (Sensibilitaets-Analyse)
Neu: Das **"Sensitivity Analysis"-Tab** fuehrt einen **isolierten Stresstest** fuer jede in der "Selected"-Liste hinterlegte Strategie durch.

**Funktionsweise:**
- Jeder **eingefrorene Parameter** der ausgewaehlten Strategie wird systematisch um **±10 % variiert** (Slow Complete Algorithm).
- Es wird geprueft, ob das System zusammenbricht, wenn sich der Markt nur minimal aendert.
- Das Ergebnis: ein **Coefficient of Variation (CV)** pro Parameter – je niedriger, desto robuster.

**Starten:**
1. Fuegen Sie mindestens eine Strategie ins **"Selected"-Tab** ein.
2. Wechseln Sie zum **"Sensitivity Analysis"-Tab**.
3. Klicken Sie auf **"Start Sensitivity Analysis"**.

**Ergebnistabelle:**
- **Pass**: Original-Pass-Nummer.
- **Name**: Name der Strategie.
- **Base Net Profit**: Ausgangsprofit.
- **Robustness CV**: Gesamter Schwankungskoeffizient (je niedriger, desto besser).
- **Status**: "Pending", "Running" oder "Done".

**Detail-Analyse (Doppelklick auf Ergebnis):**
Nach einem Doppelklick oeffnet sich ein umfangreiches Detail-Popup:
- **Parameter Robustness (CV Breakdown)**: Uebersichtstabelle aller Parameter mit:
  - CV-Wert in Prozent (farblich hervorgehoben).
  - Berechnungsformel (StdDev / |Mean| x 100).
  - **Info-Button**: Oeffnet eine ausfuehrliche Erklaerung fuer Laien mit:
    - Konkreten Zahlen aus Ihrem Test.
    - Formel und Berechnungsschritten.
    - Faustregel: "< 20 % = sehr robust", "20–50 % = akzeptabel", "> 50 % = gefaehrlich".
- **Curve-Diagramm**: Fuer jeden Parameter ein kleiner Linienchart:
  - Die blaue Linie zeigt den Profitverlauf ueber die variierten Werte.
  - Der **rote Punkt** markiert Ihren genutzten Basiswert.
  - Das Diagramm zeigt sofort, ob Sie sich auf einem sicheren Plateau oder am Rand einer Klippe befinden.
- **Optimized Strategy Settings**: Untere Tabelle listet alle Parameterwerte der Strategie auf.

Dieses Tool gibt Ihnen mathematisch fundierte Sicherheit, ob Ihre Top-Strategie ueberhaupt robust genug fuer den Live-Handel ist.

---

### 6. Bereich: Robustness Scanner (Kennlinienfahrt)
In vielen Hinsichten ist das "Optimum" nicht das, worum es im automatischen Devisenmarkt geht. Dieser Reiter ist fuer wahre Stabilitaet. Anstatt Parameter auf messerscharfen Gewinn auf einen alten Zeitpunkt zu stutzen, durchleuchtet dieses Tab systematisch die Toleranz des Roboters. "Was bricht da draussen zusammen, wenn die Kerzen doch minimal anders verlaufen?"

**Eintraege und ihre Funktionsweisen:**
- **Wie arbeitet die Logik (Konzept):** Dieser Prozess ignoriert genetische Vielfalt und stiehlt EINEN Parameter. Ihn jagt er ganz allein alle Werte ("Slow Complete", Schrittgroesse) nach oben und notiert alle seine Verdienste aus dem System, waehrend absolut alle restlichen Inputs im Profil eingefroren stagnieren. Die Kennlinie pro Wert fuer diese isolierte Variable entsteht. Dann macht die Software etwas Unerwartetes: Sie dreht die System-Uhr schlagartig im Kalender um z.B. 1 Quartal zurueck und laesst den Test genauso akkurat neu durch alle Start/Stops wandern (die Historie wird zeitlich verschoben, aber dieselben Setups verglichen).
- **No. of periods (Anzahl Zeitebenen)**: In wie viele Zeitalter eine Historie gestueckelt werden soll - 3 Periods liefert Kurven aus drei voneinander losgeloesten Monaten hintereinander in den Verlauf.
- **Shift days (Verschachtelung in Tagen)**: Der Abstand auf der Zeitleiste zum letzen Sprung in die Historie (z.B. ein Wert von `90` Tagen wandert pro neuer Zeitebene im simulierten Server exakt ein viertel Kalenderjahr tiefer an die Wurzel des Marktes!)
- **Zielgebung: Plateau Detection (Die gruene Entdeckung)**: In der fertigen interaktiven Analyse-Website ueberlagern sich diese Zeitfenster-Kurven ueber dem simulierten Parameter als Linien ("Layers"). Faengt das Bild wild an Spitzen und Einbrueche beim verstellen um kleine Einheiten wie Lot 10.3 oder 10.4 auf und abzufeuern, stuerzt eine echte Strategie real auch zusammen, sie ist wackelig konfiguriert. Gibt es exakte weite gruene Zonen, wo auf der X-Achse verstellte Werte auf keinen der ueberlagerten Zeitepochen eine Absturz-Verzerrung > 5 Prozent hervorrufen, hat das Tool Ihnen geholfen, eine grundsolide "Hochebene" (ein extrem stabiles und sicheres *Ueberlebens-Plateau*) als Parameter-Block fuer diesen Trade-Algorithmus offen zu legen. Es geht hier darum, die Parametereinstellungen unangreifbar und fehlertolerant abzusichern.
- **Modus-Auswahl (Single EA / Selected Tab)**: Neu koennen Sie waehlen, ob Sie einen einzelnen EA scannen moechten oder alle Strategien, die Sie zuvor im **"Selected"-Tab** des Optimizers gesammelt haben. Dies ermoeglicht eine effiziente Massen-Robustheitspruefung ihrer besten Kandidaten.

---

### 7. Bereich: Database (Datenbank)
Damit keine muehsam erarbeitete Aufzeichnung oder Konfiguration aus versehentlich ueberschriebenen `reports`-Ordnern fuer immer verloren geht, arbeitet ein eigener Thread die Verlaeufe automatisiert und dauerhaft in eine relationale Datenbank weg (SQLite, nicht fluechtig), welche beim Systemstart diesen Tab generiert.

**Eintraege und ihre Bedeutung:**
- **Kategorien-Baum**: Schlaegt alle Operationen elegant im Akkordeon-Format (ausklappende Knoten) auf und sortiert sich strickt in die Ordner Ihrer Laufstile (`BACKTEST`, `MULTI`, `OPTIMIZATION`, `ROBUSTNESS`). Ausklappen zeigt sofort das Herkunfts-EA-Projekt und die Einzel-Zeitstempel-Chronik tief an.
- **Aufweckende Markierungen (Heutige Tests)**: Die Logik des Tree-Renderers faerbt Ihre Verlaeufe vom aktuell zutreffenden Arbeitstag sehr hellgruen und typografisch fettdruckend ein. Wenn Sie durch alte Baumlisten Scrollen, stossen sie damit intuitiv exakt an die Stelle sofortiger Sichtbarkeit.
- **Doppelklick-Wiederbelebung**: Das File-Handling laesst Berichte im Baum bei einem schnellen Doppelklick sofort aufleben und rendert die Java2D Charts (Kurvenverlaeufe, Equity im Dialog) oder bei Browser Reports (Kennlinien in HTML) direkt auf Ihren primaeren Desktop-Browser visuell aufwendig im neuen Tab.
- **Multi-Selektion & Loeschen**: Neu koennen Sie mehrere Eintraege mit `Strg` oder `Shift` markieren und ueber die `Entf`-Taste oder den "Delete Selected"-Button entfernen.

---

### 8. Bereich: Dukascopy (Tickdaten-Download)
Fuer den Profibereich der Simulation im Metatrader benoetigen und verlassen wir uns hier nicht mehr auf die stark abstrahierten, oftmals Luecken reissenden Kursverlaeufe eines MetaQuotes-Demo Serverfeeds. Hier schalten wir per Http-Protokoll an Server der Dukascopy-Schweizerbank an und ziehen "Ticks" (Bid[Verkauf] + Ask[Kauf]) in Millisekundenbruchteilen direkt tief aus dem Intrabank-Markt herunter. 

**Echter Tauschhandel aus Level 1 Servern:**
- **Symbol / Date Range (Handelsmarke und Datumsstrecke)**: Stellen Sie auf Dropdown Ihr Paar wie USDJPY exakt auf die Sekunde praecise Von / Bis auf ein sauberes Start und End-Intervall im Kalender.
- **Download Ticks (.bi5)**: Die Bank uebertraegt hier nicht als Excel, sondern im gepackten und proprietaeren Dateiformat (`.bi5`) per LZMA-Bit-Kompression direkt zehntausende Tagesordner auf Ihre Festplatte unter das (in den "Settings" deklarierte) "Data-Directory".
- **Convert to CSV / MT5 Import (CSV Wandler)**: Dem Modul beigelegt ist ein spezieller Java Encoder-Stream. Dieser liest und zersetzt das Dukascopy `.bi5` Format rueckwirkend und stroemt das Konstrukt neu formatiert in riesige Komma-Separierte `.csv` Flat-Files herunter. Dieses Format ist perfekt - Sie koennen die CSV-Datei umgehend im MetaTrader 5 unter dem Menuepunkt [Symbols -> Custom (Individuell erstellen) -> Ticks] massenhaft in den Metatrader hochladen (importieren). Der MT5 ist anschliessend im Stande voellig getrennt von der Aussenwelt auf fehlerfreier und professionellster Ticks-Genauigkeit (hoechste 100% History-Quality) alle Tests dieses Programms perfekt auszufuehren!
- **Select All / Select None**: Komfortbuttons zur schnellen Symbolauswahl.
- **Export CSV**: Direkter Export der konvertierten Daten in eine CSV-Datei.

---

### 9. Bereich: Log (Protokoll & Ueberwachung)
Der Log-Reiter zeigt alle Nachrichten, Warnungen und Fehler des Backtester-Systems in Echtzeit an.

**Echtzeit-Logueberwachung (Mt5LogTailer):**
Ab Version 1.2.0 ueberwacht der Backtester waehrend jeder MT5-Ausfuehrung (Backtest, Multi-Backtest, Optimierung, Robustness Scan) automatisch und fortlaufend die MetaTrader 5 Logdateien im Hintergrund.

- **Ueberwachte Logdateien**: Sowohl die Hauptlogs (`logs/YYYYMMDD.log`) als auch die Tester-Logs (`Tester/logs/YYYYMMDD.log`) des MT5-Installationsverzeichnisses werden in Echtzeit mitgelesen.
- **UTF-16LE Kodierung**: MT5 schreibt seine Logs im nativen `UTF-16LE`-Format. Der Backtester dekodiert diese korrekt, sodass keine unleserlichen Zeichen auftreten.
- **Automatische Fehlererkennung**: Jede neue Logzeile wird auf Schluesselwoerter wie `error`, `failed`, `cannot load` oder `critical` geprueft. Wird ein Treffer erkannt, wird die Zeile automatisch mit einem ❌-Symbol markiert und im Log-Tab hervorgehoben.
- **Abdeckung aller Runner**: Die Ueberwachung ist in den `BacktestRunner` und `OptimizationRunner` integriert. Da der `MultiBacktestRunner` intern den `BacktestRunner` und der `RobustnessRunner` den `OptimizationRunner` nutzt, sind **alle vier Systemkomponenten** automatisch abgedeckt.

---

### 10. MT5-Prozessschutz (Process Guard)
Ab Version 1.2.0 verfuegt der Backtester ueber einen intelligenten Prozessschutz, der verhindert, dass sich alte MetaTrader 5 Instanzen gegenseitig blockieren.

**Funktionsweise:**
- **Vor jedem MT5-Start** (Backtest, Optimierung, etc.) prueft das System blitzschnell, ob noch ein alter MT5-Prozess aus einem vorherigen Lauf aktiv ist. Diese Pruefung verwendet die native Java `ProcessHandle`-API und ist extrem schnell (Mikrosekunden, kein Shell-Aufruf noetig).
- **Nur eigene Prozesse**: Es werden ausschliesslich MT5-Prozesse erkannt und beendet, die **von diesem Backtester** gestartet wurden. Fremde MT5-Instanzen (z.B. Ihr manuelles Trading-Terminal) bleiben unberuehrt.
- **Benutzer-Dialog**: Wird ein alter Prozess gefunden, erscheint ein Dialogfenster mit der Frage, ob der alte Prozess beendet werden soll. Bei Bestaetigung wird er sauber beendet; bei Ablehnung wird der neue Lauf abgebrochen.
- **Typisches Szenario**: Sie brechen einen Multi-Backtest ab und starten sofort einen neuen. Der alte MT5 hat sich noch nicht vollstaendig geschlossen. Der Process Guard erkennt dies und bietet Ihnen an, den alten Prozess sauber zu beenden, bevor der neue gestartet wird.
