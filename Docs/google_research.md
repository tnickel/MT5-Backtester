Gemini hat gesagt

Hier ist das aufbereitete, strukturierte und gut lesbare Markdown-Dokument basierend auf deinen Vorgaben:



MT5 Backtesting \& Data Integration Specification 



Die vorliegende architektonische Ausarbeitung dient als umfassende Wissensbasis und technische Spezifikation für die Entwicklung einer Java-basierten Desktop-Applikation (GUI). Ziel der Software ist die vollautomatisierte Orchestrierung des MetaTrader 5 (MT5) Strategietesters, die Verwaltung historischer Kursdaten sowie die Auswertung von Backtest-Ergebnissen. Die Analyse evaluiert die Schnittstellen der MT5-Kommandozeile, den Import von hochauflösenden Dukascopy-Daten via Custom Symbols und die notwendigen Java-Bibliotheken für ein thread-sicheres Prozess-Management.

+2



Systemarchitektur und asynchroner Kontrollfluss 



Der Kontrollfluss initiiert asynchrone Daten-Downloads (z.B. Dukascopy .bi5 Dateien), transformiert diese in CSV-Dateien und injiziert sie via MQL5-Skript in MetaTrader 5, bevor der Java ProcessBuilder den eigentlichen Backtest über die Kommandozeile (CLI) startet.



1\. Schnittstellen und CLI-Steuerung von MetaTrader 5 



MetaTrader 5 ist primär als monolithische Desktop-Anwendung konzipiert, bietet jedoch eine native Kommandozeilenschnittstelle (Command Line Interface, CLI), um das Terminal sowie den integrierten Strategietester programmgesteuert zu automatisieren. Für ein externes Java-Programm stellt diese CLI den stabilsten und deterministischsten Weg dar, um Backtests "headless" oder in einem vollautomatisierten Workflow anzustoßen. Die Ausführung stützt sich auf Konfigurationsdateien, welche die manuellen Eingaben des Benutzers in der grafischen Oberfläche des Strategietesters vollständig ersetzen.

+2



1.1 Der Aufruf über die Kommandozeile (terminal64.exe) 



Um MT5 über die CLI zu steuern, wird die ausführbare Datei terminal64.exe aufgerufen.



Der essenzielle Parameter für das automatisierte Testing ist /config, welcher den absoluten oder relativen Pfad zu einer Konfigurationsdatei übermittelt, die standardmäßig die Dateiendung .ini trägt.



Diese Konfigurationsdateien werden vom Terminal im Nur-Lese-Modus ("read only") verarbeitet; Änderungen, die während der Laufzeit in der Plattform vorgenommen werden, überschreiben diese Datei nicht.



Der Startparameter /portable ist von absolut kritischer Bedeutung für die Systemintegration. Ohne diesen Parameter speichert MetaTrader 5 Konfigurationsdaten, Logs, historische Kursdaten und Testergebnisse im versteckten Windows-Benutzerverzeichnis.

+1



Der Portable-Modus erzwingt, dass das Terminal das eigene Installationsverzeichnis als Root-Verzeichnis für sämtliche Lese- und Schreiboperationen verwendet.



Ein syntaktisch korrekter Kommandozeilenaufruf aus der Java-Umgebung lautet somit:



DOS

"C:\\Trading\\MetaTrader5\\terminal64.exe" /portable /config:C:\\Trading\\Jobs\\tester\_001.ini

1.2 Aufbau der tester.ini Konfigurationsdatei 



Die INI-Datei fungiert beim Start als präzise Spezifikation für den Backtest und überschreibt temporär die GUI-Einstellungen des Terminals. Für das Java-Programm ist es die primäre Aufgabe, diese Datei dynamisch für jeden Backtest-Durchlauf neu zu generieren.

+2



Parameter	Datentyp	Beschreibung und Wertebereich

Expert	String	Der Dateiname des Expert Advisors (EA), der getestet werden soll (relativ zum Verzeichnis MQL5\\Experts\\).

ExpertParameters	String	Der Name der Datei (z.B. .set), die die Input-Parameter des EAs enthält (im Verzeichnis MQL5\\Profiles\\Tester).

Symbol	String	Das primäre Finanzinstrument für den Test (z.B. EURUSD\_Duka).

Period	String	Die Zeiteinheit des Charts (z.B. M1, M5, H1, D1). Standard ist H1.

Model	Integer	Tick-Generierung: 0="Every tick", 1="1 minute OHLC", 2="Open price only", 3="Math calculations", 4="Every tick based on real ticks".

ExecutionMode	Integer	Emulation der Handelsausführung: 0=Normal, -1=Zufällige Verzögerung, >0=Verzögerung in Millisekunden.

FromDate / ToDate	Date	Start- und Enddatum des Tests im Format YYYY.MM.DD.

Deposit	Integer	Die initiale Kontogröße für den simulierten Backtest (z.B. 10000).

Currency	String	Die Kontowährung (z.B. USD oder EUR).

Leverage	String	Der simulierte Hebel des Kontos im Format 1:100.

Optimization	Integer	0=Deaktiviert, 1=Langsamer kompletter Algorithmus, 2=Schneller genetischer Algorithmus.

Report	String	Der Name der Ausgabedatei (z.B. .xml erzwingen für maschinenlesbares Format).

ShutdownTerminal	Integer	Beendet das Terminal nach Abschluss des Tests (1=Aktiviert, 0=Deaktiviert). Essenziell für externe Aufrufe.



(Tabelle generiert aus den Spezifikationen )

+2





Beispiel einer produktionsreifen tester.ini: 



Ini, TOML

\[Tester]

Expert=MyCustomEAs\\MovingAverageCross

Symbol=EURUSD\_Duka

Period=H1

Model=4

ExecutionMode=0

FromDate=2023.01.01

ToDate=2023.12.31

Deposit=10000

Currency=USD

Leverage=1:100

Optimization=0

Report=Reports\\Backtest\_EURUSD\_H1.xml

ReplaceReport=1

ShutdownTerminal=1



Besonderes Augenmerk liegt auf dem Parameter ShutdownTerminal=1. Ohne diesen Parameter verbleibt der MT5-Prozess unendlich lange im Arbeitsspeicher. Durch den Wert 1 signalisiert das Terminal dem System einen regulären Exit-Code (0).

+1



1.3 Erkennung der Backtest-Beendigung in Java 





Ansatz A: Synchrone Prozess-Überwachung (Empfohlen): Da ShutdownTerminal=1 gesetzt ist, beendet sich das Programm selbstständig. In Java wird der ausführende Task-Thread mittels process.waitFor() blockiert, bis der Exit-Code zurückgeliefert wird. Dies garantiert, dass alle Datei-Handles geschlossen und die Ausgabedateien vollständig geschrieben wurden.

+2





Ansatz B: Asynchrone Dateisystem-Überwachung (WatchService): Nutzung der nativen Java API java.nio.file.WatchService, falls das Terminal geöffnet bleiben muss. Hier besteht jedoch die Gefahr von Datei-Sperren (File Locks).

+2



1.4 Evaluierung: Java Wrapper vs. Python MetaTrader5 Bibliothek 



Die offizielle Python-Bibliothek MetaTrader5 bietet keine nativen Funktionen zum Starten, Steuern oder Auslesen des Strategietesters (kein mt5.run\_backtest()). Architektonische Entscheidung: Die Java-Anwendung sollte auf Python-Skripte für die Steuerung des Testers verzichten und die INI-Erstellung sowie den Aufruf der terminal64.exe via ProcessBuilder direkt selbst in Java implementieren.

+1



2\. Beschaffung und Import von Dukascopy-Kursdaten 



Der Broker Dukascopy bietet eine frei zugängliche, hochauflösende Datenbank mit extrem präzisen Tick-Daten (OHLCV).



2.1 Struktur des Dukascopy .bi5-Formats 



Dukascopy distribuiert seine Tick-Daten in proprietären, LZMA-komprimierten .bi5 Dateien. Entpackt man diese, offenbart sich eine Struktur von 20-Byte-Blöcken pro Tick:

+1





TIME (4 Bytes): 32-Bit Integer, Millisekunden seit Stundenbeginn.





ASKP (4 Bytes): Ask-Preis (skalierter Integer).





BIDP (4 Bytes): Bid-Preis (skalierter Integer).





ASKV (4 Bytes): Ask-Volumen (32-Bit Float).





BIDV (4 Bytes): Bid-Volumen (32-Bit Float).



2.2 Automatisierung des Downloads und der Dekompression 





Weg 1: Orchestrierung von Python-Skripten Bibliotheken wie dukascopy-python können Daten laden und als CSV speichern.

+1



Python

import dukascopy\_python

from datetime import datetime



\# Abruf hochauflösender M1-Daten

interval = dukascopy\_python.INTERVAL\_MINUTE\_1

offer\_side = dukascopy\_python.OFFER\_SIDE\_BID

df = dukascopy\_python.fetch(symbol, interval, offer\_side, start, end)

df.to\_csv(output\_file, index=False, header=True)



(Code snippet gekürzt basierend auf )





Weg 2: Native Java-Implementierung Nutzung der Bibliothek org.tukaani.xz für die LZMA-Dekompression:

+1



Java

import org.tukaani.xz.LZMAInputStream;

import java.nio.ByteBuffer;



// ... innerhalb der Parse-Methode ...

byte\[] buffer = new byte\[20]; // 20 Bytes pro Tick

while (lzmaIn.read(buffer) == 20) {

&#x20;   ByteBuffer bb = ByteBuffer.wrap(buffer);

&#x20;   int timeOffset = bb.getInt();

&#x20;   // Konvertierungslogik anwenden und in CSV schreiben...

}



(Code snippet gekürzt basierend auf )



2.3 Das Architektur-Problem der Zeitzonen-Konvertierung 



Dukascopy liefert Daten strikt in UTC (GMT+0). MT5-Broker nutzen jedoch oft die "New York Close" Methodik (GMT+2 / GMT+3 EET). Unbearbeitete UTC-Daten erzeugen unweigerlich verkürzte "Sonntags-Kerzen", die technische Indikatoren stark verfälschen. Die Daten-Pipeline der Java-Software muss dieses Problem dynamisch lösen.

+3



2.4 Import in MetaTrader 5 über Custom Symbols 



Um fremde Daten in MT5 zu speisen, muss ein "Custom Symbol" erstellt werden. Der korrekte architektonische Ansatz ist ein MQL5-Skript, welches die aufbereiteten CSV-Dateien programmatisch injiziert.

+1



2.5 MQL5-Skript zur automatisierten Dateninjektion 



Die Funktion CustomRatesReplace überschreibt die Historie im definierten Zeitraum vollständig und ausradiert alte Restdaten.



Code-Snippet

// Custom Symbol Importer (MQL5)

if(CustomSymbolCreate(InpCustomName, "CustomData", InpOriginName)) {

&#x20;   CustomSymbolSetInteger(InpCustomName, SYMBOL\_CHART\_MODE, SYMBOL\_CHART\_MODE\_BID);

&#x20;   CustomSymbolSetInteger(InpCustomName, SYMBOL\_TRADE\_EXEMODE, SYMBOL\_TRADE\_EXECUTION\_MARKET);

}

// Array füllen und injizieren

int replaced = CustomRatesReplace(InpCustomName, rates\[0].time, rates\[count-1].time, rates);

SymbolSelect(InpCustomName, true);



(Stark vereinfachtes MQL5 Snippet basierend auf )



3\. GitHub Prior Art (Bestehender Code \& Projekte) 



Repository	Sprache	Kern-Fokus	Architektur-Paradigma

EA31337/EA-Tester	Shell/Python	MT Auto Login Testing	CLI-Ausführung (Wine)

fortesenselabs/metatrader-terminal	Python	Server-Brücke für Backtesting	Docker, Socket.io Server

giuse88/duka	Python	Dukascopy Tick-Daten	CLI-Terminalanwendung

nj4x	Java/.NET	Terminal-Schnittstelle (MTS)	Hintergrund-Terminal-Server

mayeranalytics/bi5	Rust	Parser für bi5	Binär-Parsing (LZMA encoded)



(Zusammenfassung der Prior Art Tabelle )



Die Analyse zeigt eine klare Tendenz weg von direkten In-Memory Injektionen hin zu CLI-gesteuerten Konfigurationsarchitekturen. Eine zustandslose, dateibasierte Steuerung (INI-Datei als Input, XML-Report als Output) via CLI ist für Testing-Zwecke stabiler.

+1



4\. Architektur-Vorgaben für die Java GUI 



Blockierende Aufrufe auf dem Main-UI-Thread (JavaFX Application Thread) sind strikt zu vermeiden.



4.1 Prozess-Management und der 64KB-Deadlock 



Auf Windows stellt das OS einen Puffer von 64KB für Daten-Pipes zur Verfügung. Ohne aktives Auslesen füllt sich dieser, was zu einem fatalen Deadlock in process.waitFor() führt.

+2



Java

ProcessBuilder pb = new ProcessBuilder(terminalPath, "/portable", "/config:" + iniPath);

pb.redirectErrorStream(true); // KRITISCH: Zusammenführung der Streams

Process process = pb.start();



// Asynchroner Stream-Consumer-Thread, der den 64KB Buffer permanent leert

Thread outputConsumer = new Thread(() -> {

&#x20;   try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

&#x20;       String line;

&#x20;       while ((line = reader.readLine()) != null) { /\* konsumieren \*/ }

&#x20;   } catch (IOException e) { e.printStackTrace(); }

});

outputConsumer.start();

int exitCode = process.waitFor(); // Blockieren des Background-Tasks



(Code basiert auf Vorgaben aus )



4.2 Parsing der MT5 XML-Ergebnisberichte 



Der MT5-XML-Report ist oftmals als strukturiertes HTML in XML-Tags formatiert. Für das robuste Deserialisieren wird FasterXML Jackson Dataformat XML empfohlen.

+1



Java

XmlMapper xmlMapper = new XmlMapper();

xmlMapper.disable(DeserializationFeature.FAIL\_ON\_UNKNOWN\_PROPERTIES); // Striktes Parsing deaktivieren



(Code basiert auf )



4.3 High-Performance Verarbeitung von Kursdaten (CSV) 



Klassische Java-String-Operationen führen bei großen Finanzdaten oft zu Out-of-Memory-Errors (OOM). Es wird der zwingende Einsatz von High-Performance-Bibliotheken wie univocity-parsers oder FastCSV gefordert.

+1



Zusammenfassung und Implementierungs-Roadmap 



Infrastruktur \& Isolation: Starten des Terminals ausschließlich über terminal64.exe /portable /config:.... INI-Dateien werden pro Testlauf dynamisch generiert.





Thread-Sicherheit: Verhinderung von Deadlocks durch asynchrone Konsumierung von Output- und Error-Streams.





Datenaufbereitung \& Zeitzonen: Zeitzonen-Korrektur (Verschiebung von UTC auf Broker-DST) ist zwingend.





MT5-Injektion: Übergabe der CSV-Daten durch ein via Kommandozeile aufgerufenes MQL5-Skript (CustomSymbolCreate und CustomRatesReplace).





Auswertung: Parsing der XML-Reports durch FasterXML Jackson.

