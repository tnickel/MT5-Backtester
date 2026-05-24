<div align="center">
  <h1>Testkonzept & Testfallspezifikation</h1>
  <h2>MT5 Multi-Backtester: Unit-Test Implementierung</h2>
  <p><i>Umfassende Spezifikation für 73 Unit-Tests und Aufwandsabschätzung</i></p>
</div>

<br>

**Dokumenteninformation**
| Eigenschaft | Wert |
| :--- | :--- |
| **Dokumentenversion:** | 1.1 |
| **Status:** | Zur Prüfung / Entwurf |
| **Autor:** | Thomas Nickel / IT Development & QA |
| **Datum:** | 07. Mai 2026 |
| **Projekt:** | MT5 Multi-Backtester |
| **Klassifizierung:** | Intern / Vertraulich |

---

## Inhaltsverzeichnis
1. [Management Summary](#1-management-summary)
2. [Teststrategie & Methodik](#2-teststrategie--methodik)
3. [Detaillierte Testfallspezifikation (Die 73 Tests)](#3-detaillierte-testfallspezifikation-die-73-tests)
   - [3.1 Core Engine & Backtest Logic](#31-core-engine--backtest-logic-25-tests)
   - [3.2 Database Management](#32-database-management-16-tests)
   - [3.3 Dukascopy API & Tick-Data](#33-dukascopy-api--tick-data-18-tests)
   - [3.4 UI Models & Reporting](#34-ui-models--reporting-14-tests)
4. [Definition of Done (DoD)](#4-definition-of-done-dod)
5. [Aufwandsabschätzung (Effort Estimation)](#5-aufwandsabschätzung-effort-estimation)
6. [Risikomanagement](#6-risikomanagement)

---

## 1. Management Summary

Dieses Dokument definiert das vollumfängliche Testkonzept zur signifikanten Erhöhung der Softwarequalität und Testabdeckung des **MT5 Multi-Backtester** Projekts. Um die Systemstabilität und die Zuverlässigkeit der automatisierten Trading-Infrastruktur dauerhaft sicherzustellen, wurden **73 kritische Unit-Tests** identifiziert. 
Das Konzept dient als verbindliche Spezifikation für die Softwareentwicklung und als belastbare Grundlage für die Aufwandskalkulation. Ziel ist es, regressionsfreie Releases zu gewährleisten und alle Kernkomponenten (Engine, Database, Dukascopy API, UI Models) vor produktiven Ausfällen abzusichern.

## 2. Teststrategie & Methodik

### 2.1 Test-Frameworks
- **Test-Runner:** JUnit 5 (Jupiter) – Standard für moderne Java-Applikationen.
- **Mocking:** Mockito & Mockito-Inline – Zur Simulation von Dateisystemen, Netzwerkaufrufen und Datenbank-Antworten.
- **Assertions:** AssertJ – Für aussagekräftige und lesbare Fehlerbeschreibungen im Fall eines fehlschlagenden Tests.
- **Coverage-Analyse:** JaCoCo – Das Ziel ist eine Line-Coverage von > 85% auf alle Geschäftslogik-Klassen.

### 2.2 Test-Prinzipien
- **Isolierung:** Jeder Test läuft vollständig unabhängig. Externe Abhängigkeiten (H2 Datenbank, MT5 Terminal) werden strikt gemockt, um Netzwerklatenzen und unerwartete Seiteneffekte zu vermeiden.
- **Namenskonvention:** Eindeutige und sprechende Benennung nach dem Muster `Methode_Ausgangszustand_ErwartetesErgebnis` (z.B. `generateSetFile_validParameters_createsFileSuccessfully`).
- **FIRST-Prinzip:** Fast (schnell), Independent (unabhängig), Repeatable (wiederholbar), Self-Validating (selbstprüfend), Timely (zeitnah).

---

## 3. Detaillierte Testfallspezifikation (Die 73 Tests)

Die folgenden 73 Tests sind die Mindestanforderung für eine produktionsreife Freigabe des nächsten Releases. Sie sind in logische Geschäftsdomänen unterteilt. Jeder Testfall ist so beschrieben, dass die Intention für Entwickler und Fachexperten eindeutig ist.

### 3.1 Core Engine & Backtest Logic (25 Tests)
Diese Tests verifizieren die kritische Logik der Parametergenerierung und der Backtest-Steuerung.

| Test-ID | Testfall-Name | Beschreibung (Was wird getestet?) | Erwartetes Ergebnis |
| :--- | :--- | :--- | :--- |
| **TC-ENG-01** | `SetFile_ValidParams` | Prüft die Erstellung einer `.set` Datei mit vollständigen, korrekten Parametern. | Datei wird mit ANSI-Encoding und allen Parametern geschrieben. |
| **TC-ENG-02** | `SetFile_MissingParams` | Simuliert das Fehlen kritischer Parameter (z.B. Lotgröße). | System wirft eine `IllegalArgumentException`. |
| **TC-ENG-03** | `SetFile_InvalidPath` | Testet den Schreibzugriff auf ein nicht-existierendes oder schreibgeschütztes Verzeichnis. | Graceful Error Handling, Log-Eintrag, Abbruch der Erstellung. |
| **TC-ENG-04** | `SetFile_SpecialChars` | Prüft, wie die Engine mit Sonderzeichen in Parameternamen umgeht. | Sonderzeichen werden korrekt escaped. |
| **TC-ENG-05** | `SetFile_EmptyConfig` | Übergabe einer leeren Konfiguration an den Generator. | Fehler wird geloggt, Prozess bricht ab, keine Datei wird erstellt. |
| **TC-ENG-06** | `SetFile_LinuxPathing` | Überprüft das Verhalten der Pfad-Formatierung auf Linux-Systemen. | Slashes `/` werden anstelle von Backslashes `\` verwendet. |
| **TC-ENG-07** | `SetFile_WindowsPathing` | Überprüft das Verhalten der Pfad-Formatierung auf Windows-Systemen. | Backslashes `\` werden anstelle von Slashes `/` verwendet. |
| **TC-ENG-08** | `SetFile_Overwriting` | Prüft, ob eine bereits bestehende `.set` Datei korrekt überschrieben wird. | Alte Datei wird überschrieben, keine doppelten Einträge. |
| **TC-ENG-09** | `Runner_StandardExecution` | Startet den RobustnessRunner mit einem simulierten (gemockten) erfolgreichen MT5-Durchlauf. | Runner meldet Status `SUCCESS` zurück. |
| **TC-ENG-10** | `Runner_MT5Crash` | Simuliert einen plötzlichen Absturz des MT5 Terminals während des Backtests. | Runner erkennt Absturz, führt Restart durch oder meldet `FAILED`. |
| **TC-ENG-11** | `Runner_Timeout` | Simuliert einen hängenden MT5-Prozess (keine Rückmeldung nach X Minuten). | Timeout greift, Prozess wird gekillt, Fehler geloggt. |
| **TC-ENG-12** | `Runner_LogParsingSuccess` | Überprüft das Einlesen einer fehlerfreien MT5 Report-XML/HTML. | Metriken (Profit, Drawdown) werden korrekt in Java-Objekte geparst. |
| **TC-ENG-13** | `Runner_LogParsingCorrupt` | Überprüft das Verhalten bei einer beschädigten oder leeren MT5 Report-Datei. | Parser wirft `ParseException`, Runner behandelt den Fehler sauber. |
| **TC-ENG-14** | `Runner_ZeroTrades` | Testet den Fall, in dem der Backtest durchläuft, aber exakt 0 Trades ausgeführt wurden. | Pass wird ignoriert oder als `NO_TRADES` markiert. |
| **TC-ENG-15** | `Runner_NegativeProfit` | Testet die Verarbeitung eines Passes mit negativem Gesamtergebnis (Loss). | Negativer Profit wird korrekt verbucht und farblich/logisch markiert. |
| **TC-ENG-16** | `Runner_MarginCall` | Simuliert das Parsen eines MT5-Logs, das einen Margin Call aufweist. | Pass wird sofort als ungültig/gescheitert markiert. |
| **TC-ENG-17** | `Math_PermutationCount` | Prüft die Formel zur Berechnung der Gesamtanzahl der Backtest-Durchläufe (Passes). | Korrekte Anzahl wird berechnet (z.B. Start 10, Stop 20, Step 2 = 5 Passes). |
| **TC-ENG-18** | `Math_InvalidStep` | Testet das Verhalten bei einem Step-Wert von 0. | `ArithmeticException` wird abgefangen, Nutzer wird gewarnt. |
| **TC-ENG-19** | `Math_NegativeStep` | Testet das Verhalten bei negativen Step-Werten. | Parameter-Generator zählt korrekt rückwärts. |
| **TC-ENG-20** | `Math_FloatingPointPrecision` | Prüft die Addition von Fließkommazahlen (z.B. Lotsize 0.01 + 0.01). | Kein Rundungsfehler (z.B. 0.0200000001), exakte Werte generiert. |
| **TC-ENG-21** | `Math_MinMaxInverted` | Start-Wert ist größer als Stop-Wert bei positivem Step. | Validation-Error wird vor dem Start des Backtests geworfen. |
| **TC-ENG-22** | `AI_ScoreExtraction_Valid` | Simuliert eine LLM-Antwort, die den Score "Score: 85/100" enthält. | Parser extrahiert den Wert `85` als Integer. |
| **TC-ENG-23** | `AI_ScoreExtraction_Invalid` | Simuliert eine LLM-Antwort ohne erkennbaren Score. | Parser fällt auf den Standardwert `0` zurück. |
| **TC-ENG-24** | `AI_PromptGeneration` | Überprüft, ob der übergebene Prompt alle Metriken (Drawdown, Trades) enthält. | Generierter Prompt beinhaltet alle erforderlichen Platzhalter. |
| **TC-ENG-25** | `AI_NetworkError` | Simuliert einen Verbindungsabbruch zur OpenRouter API. | Retries werden ausgeführt, danach geordneter Abbruch mit Fehlermeldung. |

### 3.2 Database Management (16 Tests)
Absicherung der Persistenzschicht und der H2-Datenbankinteraktion.

| Test-ID | Testfall-Name | Beschreibung (Was wird getestet?) | Erwartetes Ergebnis |
| :--- | :--- | :--- | :--- |
| **TC-DB-01** | `DB_PoolInit` | Prüft die Initialisierung des HikariCP Connection Pools mit validen Credentials. | Pool wird erstellt, Verbindung ist `active`. |
| **TC-DB-02** | `DB_InvalidCredentials` | Testet den Verbindungsaufbau mit falschen Datenbank-Passwörtern. | `SQLException` wird geworfen, Start-Vorgang der App bricht ab. |
| **TC-DB-03** | `DB_SchemaMigration` | Überprüft, ob die Tabellen (`optimization_results`) beim ersten Start korrekt angelegt werden. | Tabellenstruktur existiert in In-Memory DB. |
| **TC-DB-04** | `DB_GracefulShutdown` | Testet das geordnete Schließen aller Datenbankverbindungen bei App-Exit. | Keine Memory-Leaks, DB-Lock-Dateien werden entfernt. |
| **TC-DB-05** | `CRUD_InsertValid` | Einfügen eines vollständigen `OptimizationResult` Objekts. | Datensatz ist per `SELECT` wieder auffindbar. |
| **TC-DB-06** | `CRUD_InsertDuplicate` | Versuch, einen Pass mit derselben ID zweimal einzufügen. | `UniqueConstraintViolation` wird abgefangen, Update statt Insert ausgeführt. |
| **TC-DB-07** | `CRUD_ReadById` | Lesen eines spezifischen Backtest-Ergebnisses anhand der ID. | Korrektes Objekt mit allen Feldern wird zurückgegeben. |
| **TC-DB-08** | `CRUD_ReadAll` | Abfragen aller Ergebnisse für eine bestimmte Strategie. | Liste der Objekte wird zurückgegeben, korrekte Größe der Liste. |
| **TC-DB-09** | `CRUD_UpdateScore` | Aktualisierung des AI-Scores eines bestehenden Datensatzes. | Datensatz zeigt nach dem Update den neuen Score an. |
| **TC-DB-10** | `CRUD_DeleteSelected` | Löschen einzelner, markierter Zeilen aus der Datenbank. | Nur die spezifischen Zeilen sind entfernt, Rest bleibt erhalten. |
| **TC-DB-11** | `CRUD_DeleteAll` | Löschen der gesamten Tabelle (Reset-Funktion). | Tabelle ist leer (`COUNT = 0`), Struktur bleibt erhalten. |
| **TC-DB-12** | `Bulk_InsertPerformance` | Simuliert das Einfügen von 10.000 Backtest-Ergebnissen. | Vorgang schließt in unter 2 Sekunden ab (Transaktions-Test). |
| **TC-DB-13** | `Bulk_RollbackOnError` | Ein Fehler tritt beim Bulk-Insert in Zeile 50 auf. | Gesamte Transaktion wird gerollt, keine inkonsistenten Daten in DB. |
| **TC-DB-14** | `Query_BestProfit` | Überprüft die SQL-Logik zum Sortieren nach dem höchsten Profit. | Liste ist strikt absteigend nach Net Profit sortiert. |
| **TC-DB-15** | `Query_LowestDrawdown` | Überprüft die SQL-Logik zum Filtern nach geringstem Drawdown. | Liste ist strikt aufsteigend nach Drawdown % sortiert. |
| **TC-DB-16** | `Query_ComplexFilter` | Filter nach "Profit > 1000 UND Drawdown < 10%". | Nur Datensätze, die beide Kriterien erfüllen, werden zurückgegeben. |

### 3.3 Dukascopy API & Tick-Data (18 Tests)
Sicherstellung der historischen Datenintegrität für präzise Backtests.

| Test-ID | Testfall-Name | Beschreibung (Was wird getestet?) | Erwartetes Ergebnis |
| :--- | :--- | :--- | :--- |
| **TC-DUK-01** | `Auth_LoginSuccess` | Mocking eines erfolgreichen Logins an der Dukascopy API. | Session-Token wird gespeichert und in Folge-Requests genutzt. |
| **TC-DUK-02** | `Auth_LoginFailed` | Simuliert falsche Login-Daten. | Authentifizierungsfehler wird an die UI weitergegeben. |
| **TC-DUK-03** | `Auth_TokenExpired` | Simuliert einen abgelaufenen Token während des Downloads. | Automatischer Re-Login wird getriggert, Download wird fortgesetzt. |
| **TC-DUK-04** | `Auth_RateLimit` | API meldet "Too Many Requests" (HTTP 429). | Exponential Backoff (Wartezeit) wird eingeleitet, danach Retry. |
| **TC-DUK-05** | `Auth_NoConnection` | Keine Internetverbindung beim Login-Versuch. | Sofortige Fehlermeldung "Netzwerkfehler", kein Absturz. |
| **TC-DUK-06** | `DL_SingleDay` | Download von Tick-Daten für einen einzelnen Tag (Mock-Response). | Daten werden als unkomprimiertes CSV im Cache abgelegt. |
| **TC-DUK-07** | `DL_ChunkingMultiYear` | Download von 5 Jahren Daten. Prüft, ob Anfrage in Monats-Chunks aufgeteilt wird. | Requests werden gestückelt, um API-Limits nicht zu brechen. |
| **TC-DUK-08** | `DL_ResumePartial` | Simulation eines Abbruchs nach 50% des Downloads. Prüft Wiederaufnahme. | Download startet nicht bei 0, sondern setzt an letzter Datei an. |
| **TC-DUK-09** | `DL_InvalidSymbol` | Anfrage für ein nicht-existierendes Währungspaar (z.B. "XXX/YYY"). | Graceful Error, Warnung wird geloggt. |
| **TC-DUK-10** | `DL_DiskFull` | Simulation von unzureichendem Speicherplatz während des Downloads. | System stoppt den Download sicher und meldet den Speicherfehler. |
| **TC-DUK-11** | `DL_ChecksumVerify` | Überprüfung, ob heruntergeladene Dateien korrupt sind. | Fehlerhafte Datei wird erkannt und neu angefordert. |
| **TC-DUK-12** | `DL_WeekendHandling` | Überprüfung des Download-Verhaltens für Samstage/Sonntage. | Leere Responses am Wochenende werden ignoriert, verursachen keine Fehler. |
| **TC-DUK-13** | `CSV_ParseValid` | Parsen einer standardkonformen Dukascopy CSV-Datei. | Ticks werden korrekt in Java-Objekte (Bid, Ask, Timestamp) gewandelt. |
| **TC-DUK-14** | `CSV_ParseInvalidHeader` | Einlesen einer CSV-Datei mit fehlendem oder falschem Header. | Parser lehnt die Datei als inkompatibel ab. |
| **TC-DUK-15** | `CSV_ParseCorruptRow` | Eine Zeile im CSV hat fehlende Spalten (z.B. kein Ask-Preis). | Fehlerhafte Zeile wird übersprungen, Fehler-Zähler im Log erhöht sich. |
| **TC-DUK-16** | `CSV_TransformToMT5` | Umwandlung der Dukascopy-Struktur in das benötigte MT5-Format. | Zeitzonen (z.B. UTC zu Broker-Time) werden korrekt verschoben. |
| **TC-DUK-17** | `CSV_GapDetection` | Prüft eine CSV-Datei, die eine Lücke von > 4 Stunden (unter der Woche) hat. | Gap wird vom Analyzer erkannt und im Report als Warnung gelistet. |
| **TC-DUK-18** | `CSV_EmptyFile` | Verarbeitung einer CSV-Datei mit 0 Bytes. | Datei wird ignoriert, Vorgang läuft ohne NullPointerException weiter. |

### 3.4 UI Models & Reporting (14 Tests)
Validierung der reibungslosen Datendarstellung in der Benutzeroberfläche und der Exporte.

| Test-ID | Testfall-Name | Beschreibung (Was wird getestet?) | Erwartetes Ergebnis |
| :--- | :--- | :--- | :--- |
| **TC-UI-01** | `Model_UpdateAsync` | Simulation von eingehenden Backtest-Ergebnissen im Hintergrund-Thread. | `SwingUtilities.invokeLater` wird korrekt genutzt, Tabelle aktualisiert sich sicher. |
| **TC-UI-02** | `Model_ClearData` | Aufruf der `clear()` Methode im TableModel. | Model ist leer, UI-Tabelle zeigt 0 Zeilen. |
| **TC-UI-03** | `Model_RemoveSelected` | Löschen der Indizes 2 und 4 aus der UI-Tabelle. | Exakt diese Reihen werden entfernt, restliche Daten rutschen korrekt auf. |
| **TC-UI-04** | `Model_PassColumnSync` | Überprüfung der Synchronisation der Pass-Nummer über mehrere Tabs. | Pass "42" hat in Sensitivity- und Overview-Tab identische IDs. |
| **TC-UI-05** | `Model_FormatCurrency` | Überprüfung der Formatierung von Geldbeträgen (z.B. 1000.5). | Darstellung im UI entspricht lokalem Standard (z.B. "1.000,50 €" oder "$1,000.50"). |
| **TC-UI-06** | `Sort_ByScoreAsc` | Aufsteigende Sortierung der Tabelle nach der Spalte "AI Score". | Reihenfolge ist von 0 bis 100 korrekt sortiert. |
| **TC-UI-07** | `Sort_ByScoreDesc` | Absteigende Sortierung der Tabelle nach der Spalte "AI Score". | Höchster Score (z.B. 98) steht in Zeile 0. |
| **TC-UI-08** | `Sort_HandleNulls` | Sortierung, wenn einige Zeilen noch keinen AI-Score (Null/Leer) haben. | Leere Felder werden bei Sortierung konsequent ans Ende gereiht. |
| **TC-UI-09** | `Export_HTMLSuccess` | Aufruf des HTML-Report-Generators mit validen Daten. | `report.html` wird im Zielordner erstellt und beinhaltet Tabellen-Struktur. |
| **TC-UI-10** | `Export_HTMLEmpty` | Versuch, eine leere Tabelle als HTML zu exportieren. | Warnung wird angezeigt, kein leerer Report wird geschrieben. |
| **TC-UI-11** | `Export_PDFSuccess` | Aufruf des PDF-Exporters mit einem Diagramm und Daten. | `.pdf` Datei wird generiert und ist nicht korrupt. |
| **TC-UI-12** | `Export_PDFPermissions` | PDF-Export in einen schreibgeschützten Ordner. | Fehlermeldung "Zugriff verweigert" wird dem Nutzer im UI präsentiert. |
| **TC-UI-13** | `Chart_DataSeriesAdd` | Hinzufügen eines neuen Datenpunktes (z.B. Balance) zum Chart-Model. | Series-Count erhöht sich, Chart erzwingt einen Repaint. |
| **TC-UI-14** | `Chart_DataSeriesMax` | Hinzufügen von > 10.000 Punkten zum Chart. | Älteste Punkte werden verworfen oder Chart bleibt performant (Downsampling). |

---

## 4. Definition of Done (DoD)

Ein Unit-Test gilt für diese Spezifikation als "Done" und freigabefähig, wenn:
1.  **Code Quality:** Der Test ist sauber dokumentiert (Given/When/Then-Struktur) und enthält keine "Magic Numbers".
2.  **Pass Rate:** Der Test läuft sowohl lokal auf Windows als auch in der Linux CI-Pipeline zu 100% erfolgreich durch.
3.  **Isolation:** Externe Dienste (Datenbank, Internet, File-IO) sind vollständig über Mockito entkoppelt.
4.  **Coverage:** Der JaCoCo-Bericht bestätigt, dass die zu testende Methode vollständig durchlaufen wurde (Branch & Line Coverage).
5.  **Review:** Der Code wurde durch einen Peer-Review-Prozess (Vier-Augen-Prinzip) verifiziert.

---

## 5. Aufwandsabschätzung (Effort Estimation)

Diese detaillierte Aufwandsabschätzung kalkuliert die benötigten Entwicklerressourcen zur vollständigen Umsetzung der Spezifikation.
*(Basis: 1 Personentag (PT) = 8 Arbeitsstunden eines erfahrenen Java-Entwicklers).*

| Projektphase / Komponente | Umfang | Geschätzter Aufwand (PT) | Begründung & Tätigkeiten |
| :--- | :---: | :---: | :--- |
| **1. Setup & Infrastruktur** | - | **1,5 PT** | Konfiguration von JUnit5, Mockito-Extensions, JaCoCo Plugins im `pom.xml`, Bereitstellung abstrakter Base-Test-Klassen. |
| **2. Core Engine Tests** | 25 Tests | **3,5 PT** | Höchste Komplexität. Aufwendiges Mocking des Dateisystems für Set-Files und Simulation asynchroner Prozess-Ausführungen (RobustnessRunner). |
| **3. Database Management** | 16 Tests | **2,0 PT** | Setup einer H2-In-Memory-Datenbank pro Testlauf. Vorbereiten von SQL-Testdaten und Verifizieren der DAO-Methoden. |
| **4. Dukascopy API & Ticks** | 18 Tests | **3,0 PT** | Komplexe Simulation von HTTP-Responses und Web-Client Interaktionen. Testen der IO-Streams beim CSV-Parsing erfordert Sorgfalt. |
| **5. UI Models & Reporting** | 14 Tests | **1,5 PT** | Reine Logik-Tests der TableModels und Exporter. Keine Headless-Browser/Swing-Robots nötig, daher zügig umsetzbar. |
| **6. CI/CD & Pipeline Setup** | - | **0,5 PT** | Integration der Tests in GitHub Actions, Blockieren von Pull Requests bei Fehlschlag. |
| **7. Code Review & Fixes** | - | **1,5 PT** | Eingeplanter Puffer für Code Reviews, Nachbesserungen und Refactoring von schlecht testbarem Legacy-Code. |
| **Gesamtaufwand** | **73 Tests** | **13,5 PT** | **Entspricht knapp 3 Entwickler-Wochen (1 Entwickler Vollzeit) zur Erreichung vollständiger Testabdeckung.** |

---

## 6. Risikomanagement

*   **Refactoring-Bedarf:** Sollten Klassen in der bisherigen Implementierung (z.B. `RobustnessRunner`) zu stark gekoppelt sein (Verwendung vieler `static` Methoden oder fester Instanziierungen im Konstruktor), muss vorab ein Refactoring mittels *Dependency Injection* erfolgen. Dieser Aufwand ist im Puffer (Phase 7) mit einkalkuliert, könnte aber bei extremem Spaghetticode überschritten werden.
*   **Test-Ausführungszeit:** 73 Tests müssen in unter 15 Sekunden auf lokaler Hardware durchlaufen. Längere Laufzeiten deuten auf fehlgeschlagene Isolation hin (z.B. echte Thread.sleeps statt Mocking) und müssen sofort behoben werden.

---
<div align="center">
  <i>Dieses Dokument wurde maschinell als Vorlage erstellt und entspricht gängigen industriellen Spezifikationsstandards.</i>
</div>
