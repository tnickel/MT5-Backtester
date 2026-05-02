# MT5 Backtester – Ausführliches Benutzerhandbuch

## Inhaltsverzeichnis
1. [Einleitung: Wozu dient der MT5 Backtester?](#1-einleitung-wozu-dient-der-mt5-backtester)
2. [Bereich: Settings (Einstellungen)](#2-bereich-settings-einstellungen)
3. [Bereich: Single Backtest (Einzelner Durchlauf)](#3-bereich-single-backtest-einzelner-durchlauf)
4. [Bereich: Multi-Backtester (Stapelverarbeitung)](#4-bereich-multi-backtester-stapelverarbeitung)
5. [Bereich: Optimizer (Strategie-Optimierung)](#5-bereich-optimizer-strategie-optimierung)
6. [Bereich: Robustness Scanner (Kennlinienfahrt)](#6-bereich-robustness-scanner-kennlinienfahrt)
7. [Bereich: Database (Datenbank)](#7-bereich-database-datenbank)
8. [Bereich: Dukascopy (Tickdaten-Download)](#8-bereich-dukascopy-tickdaten-download)
9. [Bereich: Log (Protokoll & Überwachung)](#9-bereich-log-protokoll--überwachung)
10. [MT5-Prozessschutz (Process Guard)](#10-mt5-prozessschutz-process-guard)

---

### 1. Einleitung: Wozu dient der MT5 Backtester?
Der **MT5 Backtester** ist eine spezialisierte und hochgradig automatisierte Desktop-Software, die eine Brücke zum MetaTrader 5 (MT5) schlägt, um professionellen Tradern und Entwicklern enorm viel Handarbeit abzunehmen. Normalerweise ist das Testen von Expert Advisors (EAs, automatischen Handelssystemen) im MT5 mühsam: Man muss jeden Parameter manuell in verschachtelten Menüs setzen, einen Start-Button drücken, auf das Ergebnis warten und dann mühsam Berichte per Hand vergleichen.

**Die Hauptvorteile dieses Backtesters umfassen:**
- **Automatisierung von Massentests**: Starten Sie mühelos Hunderte von Kombinationen (verschiedene EAs, Symbole, Zeitrahmen) unbeaufsichtigt.
- **Spezialisierte Auswertungen**: Anders als im MT5 bietet diese Software eigene, hochauflösende Charts und erweiterte Statistik-Auswertungen sowie Zusammenfassungen in visuell ansprechenden HTML-Dateien.
- **Robustheits-Check (Kennlinienfahrt)**: Diese einzigartige Funktion prüft systematisch, wie empfindlich die Parameter Ihrer EAs auf unsichtbare Marktschwankungen in der Historie reagieren (verhindert trügerisches "Overfitting").
- **Zentrale Parameterverwaltung**: Anstatt in diversen unübersichtlichen `.set`-Dateien zu enden, werden alle EA-Parameter direkt in einer lokalen SQLite-Datenbank persistiert und lassen sich in Form von übersichtlichen Listen bearbeiten und abspeichern.
- **Echtzeit-Logüberwachung**: Alle MT5-Logdateien werden während der Ausführung fortlaufend überwacht. Fehler werden sofort erkannt und im UI hervorgehoben.
- **Intelligenter Prozessschutz**: Vor jedem neuen MT5-Start wird geprüft, ob noch ein alter Prozess läuft, und der Benutzer wird informiert.

---

### 2. Bereich: Settings (Einstellungen)
Dieses Tab (Reiter) bildet das Fundament der Software. Hier konfigurieren Sie die wichtigsten Pfade zu Ihrem MetaTrader 5 Terminal und legen Verzeichnisse für die auszugebenden Berichte fest. All diese Werte werden auf Ihrem Computer dauerhaft für den nächsten Start gespeichert.

**Einträge und ihre Bedeutung:**
- **Terminal Path**: Geben Sie mit dem *Browse...*-Button den absoluten Dateipfad zur `terminal64.exe` an. Das ist die Hauptdatei, über die die Software Ihren MetaTrader 5 im Hintergrund fernsteuert.
- **Use Portable Mode (/portable flag)**: Ist dieses Kästchen angekreuzt, wird MT5 beim Start der Parameter `/portable` übergeben. Dies zwingt den MT5 dazu, seine Logdateien, Vorlagen und Historien nicht tief in den Windows Systemordnern (`AppData/Roaming/MetaQuotes/Terminal/`) zu vergraben, sondern exakt dort zu führen, wo Sie das Programm auf der Festplatte installiert haben (z. B. Laufwerk C:). Das wird hierfür dringenst empfohlen!
- **Reports Output**: Bestimmt das Windows-Verzeichnis, in welchem alle späteren Zusammenfassungen, Grafiken (Charts), und HTML-Auswertungen abgespeichert werden (die Voreinstellung ist der Unterordner `backtest_reports`).
- **Data Directory**: Hier definieren Sie einen lokalen Sammelplatz für historische Marktdaten, die zum Beispiel später über Dukascopy als Ticks heruntergeladen werden.
- **Default Deposit / Default Currency**: Um nicht in jedem Test erneut eingeben zu müssen, wie viel Startkapital zur Verfügung steht, definieren Sie hier einen festen Standard-Betrag (Deposit, z. B. `10.000`) und eine Kontowährung (Currency, z. B. `USD` oder `EUR`).
- **Default Leverage**: Ihr gewünschter Standard-Hebel für neue Testformulare (z. B. `1:100` oder `1:500`). Ein höherer Hebel verkleinert im Metatrader Simulator künstlich die erforderliche Maintenance-Margin für eingehende Trades.
- **Default Tick Model**: Das Standard-Algorithmusmodell, wie feingranular der MT5 die Preischarts aufbaut (genaue Erklärung der Ausprägungen finden Sie im Kapitel "Single Backtest").
- **Broker Timezone (UTC+)**: Historische Rohdaten aus dem Internet werden meist als "UTC" bereitgestellt (Weltzeit). MetaTrader-Broker verwenden jedoch oft Osteuropäische Zeit (oft UTC+2 in Normalzeit / UTC+3 in Sommerzeit). Diese Abweichung vom Nullmeridian stellen Sie hier ein, was bei externen Tickdaten extrem wichtig für den Abgleich der asiatischen, europäischen oder amerikanischen Eröffnungssitzungen des Tages ist.
- **Save Settings Button**: Bestätigt alle Änderungen unwiderruflich und schreibt diese in die Konfigurationsdatei der Software weg.

---

### 3. Bereich: Single Backtest (Einzelner Durchlauf)
Dieser Reiter eignet sich, um einen isolierten, einfachen Backtestausflug für eine konkrete Handelsstrategie (den Expert Advisor) vorzunehmen. Ideal zum Testen der Kernlogik und des Setups.

**Einträge und ihre Bedeutung:**
- **Expert Advisor**: Nutzen Sie den `...` Button und steuern Sie direkt zu einer kompilierten EA-Datei (Zieldateiformat `.ex5`).
- **Zahnrad-Button (⚙) / EA Parameter Editor**: Ein Highlight der Software! Es öffnet ein interaktives Zusatzfenster. Das System extrahiert hier vollautomatisch alle Variablen aus der Quellcode-Logik des EAs (Take Profit, Lot Size, Indikatoren-Werte) und packt sie in eine sortierbare Liste. Verändern Sie Werte wie Sie möchten; das System hebt geänderte Elemente hervor. Wenn Sie Ihre Einstellungen als Profil abspeichern und verwenden, springt die Info-Schrift darunter auf "Custom Config (x parameters modified)".
- **Symbol**: Über dieses Dropdown definieren Sie den spezifischen Finanzmarkt, auf welchem der Trading-Bot in diesem Lauf operiert (beispielsweise ein Währungspaar aus dem Devisenmarkt wie `EURUSD` oder `GBPUSD`).
- **Timeframe**: Das Zeitfenster für die Chart-Sicht, welche der EA als Hauptdatenquelle interpretiert, z.B. `M15` (Balken bestehen aus 15 Minuten) oder `H1` (Stundenkerzen).
- **Tick Model (Test-Modell)**: Beeinflusst drastisch die Rechenzeit und Simulationstreue Ihres Testumlaufs:
  - *Every tick (Jeder Tick)*: Bestmögliche historische Nähe. Simuliert oder verwendet lückenlos jede noch so kleine Preisänderung von Millisekunde zu Millisekunde, ist zeitintensiv, aber zwingend notwendig beim Skalpieren von Mikro-Punkten.
  - *1 Minute OHLC*: Schnell bei soliden Ergebnissen. Das System erzeugt künstlich 4 Berechnungsdaten (Open, High, Low, Close) innerhabelb von Minutenkerzen.
  - *Open prices only (Nur Eröffnungspreise)*: Extrem schnell, aber der Marktpreis existiert hier pro Chart-Kerze quasi nur eine einzige logische Sekunde lang beim Start! Ausschließlich nutzbar, wenn der Roboter nach Logik-Regeln strikt nur zu neuen Kerzen agiert.
- **From Date / To Date**: Das Datum für den Eintritt in den virtuellen Markt und das Datum des Test-Endes - über diesen Lebenszyklus muss Ihr MetaTrader auch über geladene historische Marktdaten im Terminal verfügen, sonst bleibt das Ergebnis fehlerhaft leer.
- **Deposit, Currency, Leverage**: Verändern Sie bei einem spezifischen Test den virtuell simulierten Kontostand gegenüber den Standard-Werten.
- **Start Backtest Button**: Der Test startet völlig geräuschlos und ohne grafische Benutzeroberfläche (headless) im Hintergrund - das Programm meldet sich, sobald das Ergebnis im Report-Fenster vorliegt!
- **Start Visual (MT5) Button**: Durchbricht die unsichtbare Automatisierung. MetaTrader 5 öffnet sein Fenster und Sie können live in Zeitraffer auf dem Chart-Tickgitter verfolgen, an welchen Linien und Ständen der Roboter Käufe ("Buys") oder Leerverkäufe ("Sells") umsetzt.

---

### 4. Bereich: Multi-Backtester (Stapelverarbeitung)
Der Zeit-Retter schlechthin! Umfassende EAs sollen meist nicht auf nur einen Wert eingestellt werden. Sie wollen herausfinden ob ihr EA nicht nur in M15-Charts für EURUSD, sondern auch im Stundenchart H1 für USDJPY stark funktioniert? Dafür gibt es diesen Tab.

**Einträge und ihre Bedeutung:**
- **Expert Advisors List**: Sie können mehr als einen Roboter markieren.
- **Symbols List (Symbole)**: Markieren Sie mit gedrückter Maus oder `Strg`-Taste beliebig viele Handelsinstrumente von Interesse.
- **Timeframes List (Zeitebenen)**: Dasselbe Prinzip für Balkenperioden.
- **Was geschieht im Hintergrund?**: Die Software bildet aus Ihren Markierungen völlig selbständig und logisch korrekt absolut jede Kombination (Multiplikation der Einträge) und stellt diese wie auf ein Montageband in eine Liste (Queue). Drücken Sie auf Ausführen, rattert die Software absolut fehlerfrei, eine Instanz nach der anderen, im reinen Hintergrund (ohne Crash-Potenzial!) durch Ihr Windows.
- **Das Endresultat**: Wurden alle z.B. 120 Durchläufe gemeistert, generiert der MT5 Backtester eine große, leicht vergleichbare HTML-Website-Datei in Ihren Output-Ordner. Dort sehen Sie nebeneinander sortiert, welche Zeitebene zu welchem Markt die meisten Profits hervorgebracht hat.
- **Einstellungen merken**: Ab Version 1.2.0 speichert der Multi-Backtester automatisch alle Ihre zuletzt gewählten Einstellungen (EAs, Symbole, Zeitrahmen, Datumsbereich, Tick Model, Deposit). Beim nächsten Programmstart werden diese Einstellungen exakt wiederhergestellt, sodass Sie direkt weiterarbeiten können.

---

### 5. Bereich: Optimizer (Strategie-Optimierung)
Ein exzellenter Trader weiß, dass ein System immer optimiert werden kann. Der Optimizer ist die Schaltzentrale, um mathematisch die stärksten Inputs für einen EA durch tausendfache Berechnungen zu filtern. Er kontrolliert die mächtige Optimierungs-Engine im MetaTrader 5 mit komfortabler Steuerung und erweiterter Hilfe.

**Einträge und ihre Bedeutung:**
- **Optimization Mode (Optimierungs-Verfahren)**:
  - *Disabled*: Normalfahrt (Keine Optimierung an).
  - *Slow Complete Algorithm*: Rechnet buchstäblich gnadenlos Kombination für Kombination ab. Hat der Take Profit zwanzig Ebenen und der StopLoss dreißig, ergibt allein das 600 Fahrten. Bringt das stärkste isolierte Top-Resultat zutage, benötigt für alles oberhalb von 3 Variablen astronomische PC-Rechenzeiten.
  - *Fast Genetic Algorithm*: Hier wird es revolutionär. Der Markt wird mittels Evolution erforscht. Das System paart Variablen. Gute Profit-Überlebende kriegen Kinder miteinander (Kreuzungen guter Variablen), ineffiziente Zweige sterben restlos weg, ohne noch bis in jede letzte Ecke geprüft zu werden. Man kommt dem absoluten Ziel-Ergebnis 100-mal schneller ungemein nah.
  - *All Symbols Selected...*: Wendet stattdessen die immergleiche Konfig auf alle Märkte an, welche gerade im Terminalfenster sichtbar verankert sind.
- **Optimization Criterion (Zielkriterium)**: Damit MT5 im Optimierer Erfolg haben kann, muss er wissen, wofür er auf die Jagd geht:
  - *Balance (Max)*: Bringe reines, blankes Eigenkapital heim. Es wird alles geopfert für Profit. Gefahr: Meist extrem risikoreiche Parameter.
  - *Balance + Max Profit Factor*: Der Gewinn soll wachsen, aber das Verhältnis zwischen guten und miesen Trades muss dabei so hoch wie möglich bleiben!
  - *Balance + Min Drawdown*: Suche mir die Einstellungen, bei der der simulierte Verlauf (die Equity) am sanftesten (ohne heftige Verlustrückschläge!) nach oben geht, ideal für Sicherheitsbewusste.
  - *Balance + Max Sharpe Ratio*: Versuche Einstellungen zu picken, bei denen die Schwankungsbrillianz und Volatilität das insgesamte Marktrisiko weit in den Schatten stellen.
- **Forward Testing (Zukunfts-Validierung)**: Der Schutz vor "Glück". Stellt man dies z.B. ein auf "1/4", dann sucht der Genetic Algorithm bei seinem Durchrechnen ausschließlich in den ältesten 75 % Ihres angegebenen Zeitraums nach der ultimativen Einstellung. Hat er sie, friert er sie ein und jagt diese Parametrierung komplett unabhängig ("Vorwärts") durch die absolut unsichtbaren verbleibendsten neuesten 25 % an historischen Marktdaten! Erweist sich der Traumergebnistest auf einmal als Bankrott, hatte das Setup kein Konzept, sondern auf die alten Kerzen nur Zufall über-programmiert.
- **Agent Configuration (Rechenherzen)**: Wo gerechnet wird (Local entspricht den Kernen Ihres PCs, Remote Netzwerken und Cloud MQL5 den Rechenwelten des Herstellers MetaQuotes gegen Kreditaufladung).
- **Parameter Table (Raster der Variationen)**:
  - *Start*: Wo startet die Suche einer Variablen (z.B. Start 20 für ein TakeProfit).
  - *Step*: In welchen Treppenstufen wird gesucht (bei Step 2 rechnet er Lauf 1 in 20, Lauf 2 in 22, Lauf 3 in 24...).
  - *End*: Welcher Grenzwert nicht überschritten werden soll (z.B. End 30).
  - Um für einen Parameter nach Variationen iterieren zu lassen, ist der Haken vorne in seiner Table-Zeile strikte Voraussetzung!
- **AutoConfig Button (Der Zauberstab)**: Spart immens Zeit. Statt unzählige Raster mit Logik vollzuschreiben, klickt man hier. Die Implementierung errechnet auf reiner Heuristik von Ihren Grundparametern her perfekte logarithmische Distanzen für Steps, Startpunkte unter, sowie Endziele über dem Basiswert und setzt in der Sekunde automatisiert die Auswahlschalter um!
- **Combined Analysis Tab (Verwaltung & Robustheits-Tiefentest)**: Dieser Unterbereich ist Ihre zentrale Sammelstelle für die absoluten Top-Ergebnisse aus verschiedenen Optimierungsläufen.
  - **Ergebnisse verwalten**: Sie können unerwünschte Optimierungen per Klick (oder mehrere gleichzeitig mit der Shift-Taste) markieren und über einen Button dauerhaft löschen (inkl. Sicherheitsabfrage).
  - **Sensitivity Analysis (Stresstest)**: Wählen Sie ein Top-Ergebnis in der Liste aus und klicken Sie auf `Sensitivity Analysis`. Die Software startet einen **isolierten Stresstest (Slow Complete Algorithm)**: Jeder Parameter der Strategie wird um ±10% variiert, um zu sehen, ob das System einbricht, wenn sich der Markt nur minimal ändert.
  - **Die Kennlinien-Diagnose**: Machen Sie nach dem Test einen **Doppelklick** auf das Ergebnis, um das Analyse-Popup zu öffnen:
    - **CV% Breakdown**: Sie sehen für jeden Parameter sofort den *Coefficient of Variation* (Schwankungskoeffizient).
    - **Optische Graphen**: Die blauen Kennlinien zeigen Ihnen visuell, ob Sie sich auf einem sicheren Plateau oder am Rand einer Klippe befinden. Der rote Punkt markiert Ihren genutzten Basiswert, zusammen mit Start-, Step- und End-Koordinaten.
    - **Transparenz für Laien (Info-Button)**: Ein Klick auf das leuchtende `ℹ`-Symbol öffnet eine verständliche Erklärung. Hier wird Ihnen anhand Ihrer echten Testergebnisse mathematisch bewiesen, warum ein Parameter riskant oder sicher ist (inklusive Daumenregeln wie "< 20% = sicher", "> 50% = gefährlich").
---

### 6. Bereich: Robustness Scanner (Kennlinienfahrt)
In vielen Hinsichten ist das "Optimum" nicht das, worum es im automatischen Devisenmarkt geht. Dieser Reiter ist für wahre Stabilität. Anstatt Parameter auf messerscharfen Gewinn auf einen alten Zeitpunkt zu stutzen, durchleuchtet dieses Tab systematisch die Toleranz des Roboters. "Was bricht da draußen zusammen, wenn die Kerzen doch minimal anders verlaufen?"

**Einträge und ihre Funktionsweisen:**
- **Wie arbeitet die Logik (Konzept):** Dieser Prozess ignoriert genetische Vielfalt und stiehlt EINEN Parameter. Ihn jagt er ganz allein alle Werte ("Slow Complete", Schrittgröße) nach oben und notiert alle seine Verdienste aus dem System, wärend absolut alle restlichen Inputs im Profil eingefroren stagnieren. Die Kennlinie pro Wert für diese isolierte Variable entsteht. Dann macht die Software etwas Unerwartetes: Sie dreht die System-Uhr schlagartig im Kalender um z.B. 1 Quartal zurück und lässt den Test genauso akkurat neu durch alle Start/Stops wandern (die Historie wird zeitlich verschoben, aber dieselben Setups verglichen).
- **No. of periods (Anzahl Zeitebenen)**: In wie viele Zeitalter eine Historie gestückelt werden soll - 3 Periods liefert Kurven aus drei voneinander losgelösten Monaten hintereinander in den Verlauf.
- **Shift days (Verschachtelung in Tagen)**: Der Abstand auf der Zeitleiste zum letzen Sprung in die Historie (z.B. ein Wert von `90` Tagen wandert pro neuer Zeitebene im simulierten Server exakt ein viertel Kalenderjahr tiefer an die Wurzel des Marktes!)
- **Zielgebung: Plateau Detection (Die grüne Entdeckung)**: In der fertigen interaktiven Analyse-Website überlagern sich diese Zeitfenster-Kurven über dem simulierten Parameter als Linien ("Layers"). Fängt das Bild wild an Spitzen und Einbrüche beim verstellen um kleine Einheiten wie Lot 10.3 oder 10.4 auf und abzufeuern, stürzt eine echte Strategie real auch zusammen, sie ist wackelig konfiguriert. Gibt es exakte weite grüne Zonen, wo auf der X-Achse verstellte Werte auf keinen der überlagerten Zeitepochen eine Absturz-Verzerrung > 5 Prozent hervorrufen, hat das Tool Ihnen geholfen, eine grundsolide "Hochebene" (ein extrem stabiles und sicheres *Überlebens-Plateau*) als Parameter-Block für diesen Trade-Algorithmus offen zu legen. Es geht hier darum, die Parametereinstellungen unangreifbar und fehlertolerant abzusichern.

---

### 7. Bereich: Database (Datenbank)
Damit keine mühsam erarbeitete Aufzeichnung oder Konfiguration aus versehentlich überschriebenen `reports`-Ordnern für immer verloren geht, arbeitet ein eigener Thread die Verläufe automatisiert und dauerhaft in eine relationale Datenbank weg (SQLite, nicht flüchtig), welche beim Systemstart diesen Tab generiert.

**Einträge und ihre Bedeutung:**
- **Kategorien-Baum**: Schlägt alle Operationen elegant im Akkordeon-Format (ausklappende Knoten) auf und sortiert sich strickt in die Ordner Ihrer Laufstile (`BACKTEST`, `MULTI`, `OPTIMIZATION`, `ROBUSTNESS`). Ausklappen zeigt sofort das Herkunfts-EA-Projekt und die Einzel-Zeitstempel-Chronik tief an.
- **Aufweckende Markierungen (Heutige Tests)**: Die Logik des Tree-Renderers färbt Ihre Verläufe vom aktuell zutreffenden Arbeitstag sehr hellgrün und typografisch fettdruckend ein. Wenn Sie durch alte Baumlisten Scrollen, stoßen sie damit intuitiv exakt an die Stelle sofortiger Sichtbarkeit.
- **Doppelklick-Wiederbelebung**: Das File-Handling lässt Berichte im Baum bei einem schnellen Doppelklick sofort aufleben und rendert die Java2D Charts (Kurvenverläufe, Equity im Dialog) oder bei Browser Reports (Kennlinien in HTML) direkt auf Ihren primären Desktop-Browser visuell aufwendig im neuen Tab.

---

### 8. Bereich: Dukascopy (Tickdaten-Download)
Für den Profibereich der Simulation im Metatrader benötigen und verlassen wir uns hier nicht mehr auf die stark abstrahierten, oftmals Lücken reißenden Kursverläufe eines MetaQuotes-Demo Serverfeeds. Hier schalten wir per Http-Protokoll an Server der Dukascopy-Schweizerbank an und ziehen "Ticks" (Bid[Verkauf] + Ask[Kauf]) in Millisekundenbruchteilen direkt tief aus dem Intrabank-Markt herunter. 

**Echter Tauschhandel aus Level 1 Servern:**
- **Symbol / Date Range (Handelsmarke und Datumsstrecke)**: Stellen Sie auf Dropdown Ihr Paar wie USDJPY exakt auf die Sekunde präzise Von / Bis auf ein sauberes Start und End-Intervall im Kalender.
- **Download Ticks (.bi5)**: Die Bank überträgt hier nicht als Excel, sondern im gepackten und proprietären Dateiformat (`.bi5`) per LZMA-Bit-Kompression direkt zehntausende Tagesordner auf Ihre Festplatte unter das (in den "Settings" deklarierte) "Data-Directory".
- **Convert to CSV / MT5 Import (CSV Wandler)**: Dem Modul beigelegt ist ein spezieller Java Encoder-Stream. Dieser liest und zersetzt das Dukascopy `.bi5` Format rückwirkend und strömt das Konstrukt neu formatiert in riesige Komma-Separierte `.csv` Flat-Files herunter. Dieses Format ist perfekt - Sie können die CSV-Datei umgehend im MetaTrader 5 unter dem Menüpunkt [Symbols -> Custom (Individuell erstellen) -> Ticks] massenhaft in den Metatrader hochladen (importieren). Der MT5 ist anschließend im Stande völlig getrennt von der Außenwelt auf fehlerfreier und professionellster Ticks-Genauigkeit (höchste 100% History-Quality) alle Tests dieses Programms perfekt auszuführen!

---

### 9. Bereich: Log (Protokoll & Überwachung)
Der Log-Reiter zeigt alle Nachrichten, Warnungen und Fehler des Backtester-Systems in Echtzeit an.

**Echtzeit-Logüberwachung (Mt5LogTailer):**
Ab Version 1.2.0 überwacht der Backtester während jeder MT5-Ausführung (Backtest, Multi-Backtest, Optimierung, Robustness Scan) automatisch und fortlaufend die MetaTrader 5 Logdateien im Hintergrund.

- **Überwachte Logdateien**: Sowohl die Hauptlogs (`logs/YYYYMMDD.log`) als auch die Tester-Logs (`Tester/logs/YYYYMMDD.log`) des MT5-Installationsverzeichnisses werden in Echtzeit mitgelesen.
- **UTF-16LE Kodierung**: MT5 schreibt seine Logs im nativen `UTF-16LE`-Format. Der Backtester dekodiert diese korrekt, sodass keine unleserlichen Zeichen auftreten.
- **Automatische Fehlererkennung**: Jede neue Logzeile wird auf Schlüsselwörter wie `error`, `failed`, `cannot load` oder `critical` geprüft. Wird ein Treffer erkannt, wird die Zeile automatisch mit einem ❌-Symbol markiert und im Log-Tab hervorgehoben.
- **Abdeckung aller Runner**: Die Überwachung ist in den `BacktestRunner` und `OptimizationRunner` integriert. Da der `MultiBacktestRunner` intern den `BacktestRunner` und der `RobustnessRunner` den `OptimizationRunner` nutzt, sind **alle vier Systemkomponenten** automatisch abgedeckt.

---

### 10. MT5-Prozessschutz (Process Guard)
Ab Version 1.2.0 verfügt der Backtester über einen intelligenten Prozessschutz, der verhindert, dass sich alte MetaTrader 5 Instanzen gegenseitig blockieren.

**Funktionsweise:**
- **Vor jedem MT5-Start** (Backtest, Optimierung, etc.) prüft das System blitzschnell, ob noch ein alter MT5-Prozess aus einem vorherigen Lauf aktiv ist. Diese Prüfung verwendet die native Java `ProcessHandle`-API und ist extrem schnell (Mikrosekunden, kein Shell-Aufruf nötig).
- **Nur eigene Prozesse**: Es werden ausschließlich MT5-Prozesse erkannt und beendet, die **von diesem Backtester** gestartet wurden. Fremde MT5-Instanzen (z.B. Ihr manuelles Trading-Terminal) bleiben unberührt.
- **Benutzer-Dialog**: Wird ein alter Prozess gefunden, erscheint ein Dialogfenster mit der Frage, ob der alte Prozess beendet werden soll. Bei Bestätigung wird er sauber beendet; bei Ablehnung wird der neue Lauf abgebrochen.
- **Typisches Szenario**: Sie brechen einen Multi-Backtest ab und starten sofort einen neuen. Der alte MT5 hat sich noch nicht vollständig geschlossen. Der Process Guard erkennt dies und bietet Ihnen an, den alten Prozess sauber zu beenden, bevor der neue gestartet wird.
