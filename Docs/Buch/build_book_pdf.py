from __future__ import annotations

import os
import re
import textwrap
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable, List, Sequence

from PIL import Image as PILImage
from PIL import ImageDraw, ImageFont
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import (
    Image,
    KeepTogether,
    ListFlowable,
    ListItem,
    PageBreak,
    Paragraph,
    Preformatted,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = Path(__file__).resolve().parent
ASSET_DIR = OUT_DIR / "generated_assets"
MD_PATH = OUT_DIR / "Mastering_the_Backtester.md"
PDF_PATH = OUT_DIR / "Mastering_the_Backtester.pdf"
VERSION = "1.0"
BUILD_DATE = date.today().strftime("%d.%m.%Y")


@dataclass
class Section:
    title: str
    paragraphs: list[str]
    bullets: list[str] | None = None


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return path.read_text(encoding="cp1252", errors="replace")


def source_packages() -> list[dict[str, object]]:
    result: dict[str, dict[str, object]] = {}
    for path in (ROOT / "src" / "main" / "java").rglob("*.java"):
        text = read_text(path)
        m = re.search(r"^package\s+([^;]+);", text, re.M)
        pkg = m.group(1) if m else "(default)"
        info = result.setdefault(pkg, {"package": pkg, "files": 0, "lines": 0, "classes": []})
        info["files"] = int(info["files"]) + 1
        info["lines"] = int(info["lines"]) + len(text.splitlines())
        classes = re.findall(r"\b(?:class|interface|enum|record)\s+([A-Za-z0-9_]+)", text)
        info["classes"] = list(info["classes"]) + classes
    return sorted(result.values(), key=lambda x: str(x["package"]))


def source_classes() -> list[tuple[str, str, int]]:
    rows = []
    for path in sorted((ROOT / "src" / "main" / "java").rglob("*.java")):
        text = read_text(path)
        m = re.search(r"^package\s+([^;]+);", text, re.M)
        pkg = m.group(1) if m else "(default)"
        cls = path.stem
        rows.append((pkg, cls, len(text.splitlines())))
    return rows


def test_classes() -> list[tuple[str, int]]:
    rows = []
    for path in sorted((ROOT / "src" / "test" / "java").rglob("*.java")):
        rows.append((str(path.relative_to(ROOT)), len(read_text(path).splitlines())))
    return rows


def repo_snapshot() -> dict[str, object]:
    packages = source_packages()
    classes = source_classes()
    tests = test_classes()
    return {
        "packages": packages,
        "classes": classes,
        "tests": tests,
        "main_java_files": len(classes),
        "test_java_files": len(tests),
        "main_java_lines": sum(row[2] for row in classes),
        "test_java_lines": sum(row[1] for row in tests),
    }


PACKAGE_SUMMARY = {
    "com.backtester": "Startpunkt der Anwendung. Main entscheidet zwischen CLI und Desktop-UI, initialisiert die Konfiguration und raeumt alte MetaTrader-Prozesse auf.",
    "com.backtester.cli": "Headless Batch-Betrieb fuer automatisierte Laeufe ohne GUI. Relevant fuer reproduzierbare Serien und spaetere Automatisierung.",
    "com.backtester.config": "Zentrale Projektkonfiguration, Plattform-Erkennung, EA-Parameter, SET-Dateien, Presets und Pfade zu MT4/MT5.",
    "com.backtester.database": "SQLite-Persistenz fuer Historie, Workflow-State, Sensitivitaetsdaten, KI-Berichte, Reviews und Einstellungen.",
    "com.backtester.dukascopy": "Download, Dekodierung und Umwandlung von Dukascopy-BI5-Tickdaten in nutzbare M1/CSV-Daten.",
    "com.backtester.engine": "Ausfuehrungsschicht: Backtests, Optimierungen, Sensitivitaet, Robustheit, Workflow-Orchestrierung, Prozessschutz und Forward-Split.",
    "com.backtester.mt5": "Import von CSV-Daten in MT5 Custom Symbols und Verwaltung lokaler Symbol-Metadaten.",
    "com.backtester.report": "Parser, Ergebnisobjekte, Scorecard, HTML/PDF-Reports, Multi-Reports und Validierungsergebnisse.",
    "com.backtester.tools": "Headless Auswertungs- und Export-Werkzeuge fuer strategische Portfolio-Auswahl und Report-Erzeugung.",
    "com.backtester.ui": "Aeltere Swing-Oberflaeche mit Panels fuer Backtest, Optimizer, Multi-Backtest, Historie, Dukascopy und Settings.",
    "com.backtester.ui.javafx": "Aktuelle primaere JavaFX-Oberflaeche: MainView, WorkflowView, Dialoge, Controlling und moderne Interaktionsschicht.",
}


PARAMETERS = [
    ("MT5 Terminal Path", "config", "Pfad zur terminal64.exe. Ohne gueltigen Pfad kann kein MT5-Prozess gestartet werden.", "C:\\Program Files\\MetaTrader 5\\terminal64.exe"),
    ("MT4 Terminal Path", "config", "Pfad zur terminal.exe. Wird genutzt, wenn ein Expert Advisor als MT4-Artefakt erkannt wird.", "C:\\Program Files\\MetaTrader 4\\terminal.exe"),
    ("Data Directory", "config", "Ablage fuer Dukascopy-Daten, konvertierte CSVs und lokale Marktdaten.", "data"),
    ("Reports Directory", "config", "Ziel fuer Backtest-, Optimierungs- und HTML/PDF-Reports.", "backtest_reports"),
    ("Export Directory", "config", "Ziel fuer normale Portfolio- und SET-Datei-Exporte.", "exports"),
    ("Best Export Directory", "config", "Ziel fuer sehr gute Strategien; nach Step 7 nur fuer PASSED-Validierungen.", "exports_gut"),
    ("Portable Mode", "config", "Startet MT5 mit /portable, damit Pfade und Profile kontrollierbarer bleiben.", "true/false"),
    ("Backtest Timeout", "config", "Freeze-Schutz fuer MetaTrader-Prozesse. Lange Optimierungen brauchen hoeheren Wert.", "Minuten"),
    ("Broker Timezone Offset", "config", "Zeitverschiebung beim Umwandeln externer Daten in Brokerzeit.", "0"),
    ("Expert", "backtest", "Pfad oder relativer Name des Expert Advisors, der getestet wird.", "MQL5\\Experts\\EA.ex5"),
    ("ExpertParameters", "backtest", "SET-Datei oder Parameterprofil, das MT5 laden soll.", "*.set"),
    ("Symbol", "backtest", "Markt, auf dem getestet wird. Muss in MT5 vorhanden sein.", "EURUSD, XAUUSD"),
    ("Period", "backtest", "Zeiteinheit des Tests.", "M1, M5, M15, H1, D1"),
    ("Model", "backtest", "MT5-Modell fuer Tick-Qualitaet. Hoehere Genauigkeit kostet Laufzeit.", "0, 1, 2"),
    ("ExecutionMode", "backtest", "MetaTrader-Ausfuehrungsmodell fuer Order-Simulation.", "0"),
    ("FromDate/ToDate", "backtest", "Historisches Testfenster. Fachlich entscheidend fuer In-Sample und OOS.", "YYYY-MM-DD"),
    ("Deposit", "backtest", "Startkapital fuer Performance-Kennzahlen.", "10000"),
    ("Currency", "backtest", "Kontowaehrung fuer Reports.", "USD"),
    ("Leverage", "backtest", "Hebelannahme fuer Strategie-Tester.", "1:100"),
    ("ShutdownTerminal", "backtest", "Soll MT5 nach Abschluss automatisch schliessen.", "true"),
    ("UseVirtualDesktop", "backtest", "Startet MetaTrader auf Desktop 2, um den Nutzerarbeitsplatz frei zu halten.", "true/false"),
    ("AutoKillMt5", "backtest", "Erlaubt automatisches Beenden alter MetaTrader-Prozesse.", "true/false"),
    ("VisualMode", "backtest", "Startet den visuellen Tester fuer manuelle Beobachtung.", "false"),
    ("OptimizationMode", "optimizer", "0 deaktiviert Optimierung, 1 Complete, 2 Genetic. Genetic ist schneller, Complete gruendlicher.", "2"),
    ("OptimizationCriterion", "optimizer", "MT5-Kriterium fuer Ranking, etwa Balance, Profit Factor, Recovery oder Sharpe.", "0-6"),
    ("ForwardMode", "optimizer", "Teilt Optimierungsfenster in Backtest und Forward auf.", "0, 1, 2, 3, 4"),
    ("ForwardDate", "optimizer", "Custom-Start des Forward-Fensters bei ForwardMode 4.", "YYYY-MM-DD"),
    ("UseLocal/Remote/Cloud", "optimizer", "Steuert, welche MT5-Agenten fuer Optimierung genutzt werden.", "1/0"),
    ("minBtProfit", "workflow", "Mindestgewinn im Backtest fuer Step-3-Kandidaten.", "0.01"),
    ("minFwProfit", "workflow", "Mindestgewinn im Forward-Fenster.", "0.01"),
    ("minBtTrades", "workflow", "Mindestanzahl Trades im Backtest gegen statistisch duenne Ergebnisse.", "100"),
    ("minFwTrades", "workflow", "Mindestanzahl Trades im Forward-Fenster.", "15"),
    ("maxBtDd/maxFwDd", "workflow", "Maximal tolerierter Drawdown in Backtest und Forward.", "100"),
    ("paramDiffPct", "workflow", "Diversity-Schwelle fuer Parameterunterschiede zwischen Kandidaten.", "0.10"),
    ("tradeDiffPct", "workflow", "Diversity-Schwelle fuer abweichende Trade-Anzahlen.", "0.15"),
    ("minDifferentParams", "workflow", "Mindestzahl unterschiedlicher Parameter fuer Portfolio-Diversitaet.", "2"),
    ("maxStrategiesToSelect", "workflow", "Maximale Zahl der Kandidaten nach Diversity-Filter.", "5"),
    ("OpenRouter API Key", "ki", "Lokaler API-Schluessel fuer LLM-Auswertung; wird in SQLite gespeichert, nicht im Git.", "leer"),
    ("OpenRouter Model", "ki", "LLM-Modell fuer Stabilitaetsanalyse.", "openai/gpt-4o-mini"),
    ("OpenRouter Prompt", "ki", "Prompt mit Tabellenformat, Kurvenform-Analyse und Score-Regeln.", "DEFAULT_PROMPT"),
    ("Performance Weight", "ki", "Gewicht des numerischen Performance-Scores im finalen Ranking.", "0.6"),
    ("Stability Weight", "ki", "Gewicht des KI-Stabilitaetsscores im finalen Ranking.", "0.4"),
    ("wBtProfit", "score", "Gewicht fuer Backtest-Profitabilitaet.", "15"),
    ("wFwProfit", "score", "Gewicht fuer Forward-Profitabilitaet.", "15"),
    ("wConsistency", "score", "Gewicht fuer Verhaeltnis von Forward zu Backtest.", "10"),
    ("wRisk", "score", "Gewicht fuer Risiko/Drawdown-Verhaeltnis.", "10"),
    ("wEquityConsist", "score", "Gewicht fuer echte Sharpe-basierte Equity-Konsistenz.", "10"),
    ("wSampleSize", "score", "Gewicht fuer Stichprobengroesse und Testdauer.", "25"),
    ("wFwTrades", "score", "Gewicht fuer Anzahl der Forward-Trades.", "30"),
    ("wRecovery", "score", "Gewicht fuer Recovery Factor.", "25"),
    ("recoveryMin/recoveryMax", "score", "Skalierungsbereich fuer Recovery-Faktor-Bewertung.", "1.0 / 5.0"),
    ("validationFromDate", "validation", "Start des echten Step-7-OOS-Fensters; leer bedeutet toDate + 1 Tag.", "null"),
    ("validationToDate", "validation", "Ende des Step-7-OOS-Fensters; leer bedeutet aktuelles Datum.", "null"),
]


SOURCES = [
    ("QuantStart", "Successful Backtesting of Algorithmic Trading Strategies - Part I", "https://www.quantstart.com/articles/Successful-Backtesting-of-Algorithmic-Trading-Strategies-Part-I/"),
    ("Investopedia", "Backtesting and Forward Testing: The Importance of Correlation", "https://www.investopedia.com/articles/trading/10/backtesting-walkforward-important-correlation.asp"),
    ("AlgoTrading101", "Backtesting Biases and Risks", "https://algotrading101.com/wiki/backtesting-biases-and-risks/"),
    ("Surmount", "Walk-Forward Analysis vs. Backtesting", "https://surmount.ai/walk-forward-analysis-vs-backtesting-pros-cons-best-practices"),
    ("QuantInsti", "Walk-Forward Optimization", "https://blog.quantinsti.com/walk-forward-optimization-introduction/"),
]


GLOSSARY = [
    ("Backtest", "Historische Simulation einer Handelsregel mit bekannten Marktdaten."),
    ("Forward-Test", "Von MT5 abgetrennter Teil des Optimierungszeitraums, der zur ersten Plausibilisierung dient."),
    ("Out-of-Sample", "Daten, die nicht in Optimierung oder Auswahl eingeflossen sind."),
    ("In-Sample", "Datenbereich, in dem Parameter gesucht oder angepasst werden."),
    ("Curve Fitting", "Uebermaessige Anpassung an historische Zufallsdetails."),
    ("Overfitting", "Statistische Ueberanpassung, die in neuen Daten typischerweise einbricht."),
    ("Walk-Forward", "Wiederholte Optimierung und Validierung ueber rollierende Zeitfenster."),
    ("Holdout", "Zurueckgelegtes Datenfenster fuer finale Validierung."),
    ("Monte Carlo", "Simulation vieler Varianten, um Zufallseinfluesse sichtbar zu machen."),
    ("Expert Advisor", "Automatisierte Handelsstrategie in MetaTrader."),
    ("SET-Datei", "MetaTrader-Parameterdatei fuer Expert Advisors."),
    ("tester.ini", "Konfigurationsdatei, mit der MetaTrader per Kommandozeile gesteuert wird."),
    ("Optimization Pass", "Eine getestete Parameterkombination im MT5-Optimizer."),
    ("CombinedPass", "Projektobjekt, das Backtest- und Forward-Pass zusammenfuehrt."),
    ("Profit Factor", "Verhaeltnis von Bruttogewinn zu Bruttoverlust."),
    ("Recovery Factor", "Verhaeltnis von Gewinn zu maximalem Drawdown."),
    ("Sharpe Ratio", "Risikoadjustierte Renditekennzahl."),
    ("Drawdown", "Rueckgang vom Equity-Hoch zum folgenden Tief."),
    ("Expected Payoff", "Durchschnittliches Ergebnis pro Trade."),
    ("Coefficient of Variation", "Relative Streuung; im Projekt als CV fuer Parametersensitivitaet genutzt."),
    ("Plateau", "Parameterbereich, in dem Ergebnisse stabil bleiben."),
    ("Peak", "Einzelner Spitzenwert ohne stabile Nachbarschaft."),
    ("Cliff", "Klippenfoermiger Einbruch bei kleiner Parameterveraenderung."),
    ("Diversity Filter", "Auswahlmechanismus, der zu aehnliche Strategien reduziert."),
    ("KI-Gate", "Filter, der sehr fragile KI-bewertete Kandidaten aussortiert."),
    ("Step 7", "Finale Validierung auf unberuehrtem OOS-Fenster nach Portfolio-Auswahl."),
    ("Best-Ordner", "Exportziel fuer besonders gute und nach Validierung akzeptierte Strategien."),
    ("Workflow State", "Persistierter Zustand der siebenstufigen Pipeline."),
    ("SENSITIVITY_DETAIL", "Normalisierte SQLite-Tabelle fuer CV, Kurvendaten und Verdicts."),
    ("OpenRouter", "API-Schicht fuer den Zugriff auf LLM-Modelle."),
    ("MCP", "Model Context Protocol; ermoeglicht KI-Tools lesenden Zugriff auf Backtester-Daten."),
    ("Dukascopy BI5", "Komprimiertes Tickdatenformat von Dukascopy."),
    ("Custom Symbol", "In MT5 importiertes Symbol mit eigenen historischen Daten."),
    ("Portable Mode", "MT5-Startmodus mit lokaler Datenhaltung im Installationskontext."),
    ("Process Guard", "Schutzlogik gegen haengende oder stale MetaTrader-Prozesse."),
    ("Virtual Desktop", "Start von MetaTrader auf einem zweiten Desktop, damit die UI frei bleibt."),
    ("Report Parser", "Code, der HTM/XML-Reports in strukturierte Ergebnisobjekte uebersetzt."),
    ("ScoreWeights", "Single Source of Truth fuer Score-Gewichte im Projekt."),
    ("WorkflowEngine", "Zentrale State Machine der Anti-Curvefitting-Pipeline."),
    ("BacktestRunner", "Runner fuer einzelne MT4/MT5-Backtests."),
    ("OptimizationRunner", "Runner fuer MT5-Optimierungen und Forward-Auswertung."),
    ("SensitivityRunner", "Runner fuer Parameter-Sweeps und CV-Berechnung."),
    ("RobustnessRunner", "Runner fuer Robustheitsscans ueber Parameter und Zeitverschiebungen."),
    ("DatabaseManager", "SQLite-Zugriffsschicht und Migrationspunkt des Projekts."),
]


KEY_CLASSES = [
    ("Main", "Startet CLI oder JavaFX, initialisiert AppConfig und entfernt alte MetaTrader-Prozesse."),
    ("JavaFXMain", "Erzeugt Stage, Scene, CSS und Icon der modernen Benutzeroberflaeche."),
    ("MainView", "Hauptcontainer fuer Tabs und Views der JavaFX-Anwendung."),
    ("WorkflowView", "Visualisiert die siebenstufige Pipeline und bindet UI an WorkflowEngine."),
    ("WorkflowConfigDialogs", "Konfigurationsdialoge fuer Workflow-Schritte, Score-Gewichte und KI-Setup."),
    ("ControllingView", "Analyse, Review und Nachtest-Zentrale fuer gespeicherte Strategien."),
    ("BacktestView", "JavaFX-Einzeltestoberflaeche."),
    ("OptimizationView", "JavaFX-Oberflaeche fuer Optimierungen, Ergebnisanalyse und Sensitivitaet."),
    ("DukascopyView", "UI fuer Download, Scan, CSV-Konvertierung und MT5-Import von Daten."),
    ("AppConfig", "Zentrale Pfade, Defaults, Plattform-Erkennung und Verzeichnisse."),
    ("MetaTraderPlatform", "Abstraktion fuer MT4/MT5-Unterschiede wie Executable, Logs und Report-Endung."),
    ("EaParameter", "Datenmodell fuer EA-Parameter inklusive Optimierungsbereich."),
    ("EaParameterManager", "Liest, schreibt, merged und generiert SET-Dateien und Parameterprofile."),
    ("BacktestConfig", "Konfiguration fuer einzelne Backtests."),
    ("OptimizationConfig", "Konfiguration fuer MT5-Optimierungen inklusive ForwardMode und Agenten."),
    ("MultiBacktestConfig", "Erzeugt Einzelkonfigurationen fuer Batch-Kombinationen."),
    ("IniGenerator", "Schreibt MT4/MT5-kompatible tester.ini-Dateien."),
    ("BacktestRunner", "Steuert Einzeltestprozess und Reportuebernahme."),
    ("OptimizationRunner", "Steuert Optimierungsprozess und Optimierungsreport."),
    ("WorkflowEngine", "State Machine, Persistenz, Gates, Export und Step-7-Validierung."),
    ("ForwardSplit", "Spiegelt MT5-Forward-Fenster fuer korrekte Sensitivitaetsperioden."),
    ("LlmAnalysisService", "Baut Prompt aus Sensitivitaetsdaten und ruft OpenRouter auf."),
    ("SensitivityRunner", "Variiert Parameter und misst CV/Kennlinien fuer BT und FW."),
    ("RobustnessRunner", "Fuehrt Robustheitsscans mit Parameter- und Zeitverschiebungen aus."),
    ("Mt5ProcessGuard", "Schuetzt vor stale MetaTrader-Instanzen."),
    ("VirtualDesktopHelper", "Startet MetaTrader normal oder auf Desktop 2."),
    ("ReportParser", "Parst Einzeltestreports in BacktestResult."),
    ("OptimizationReportParser", "Parst Optimierungsreports in OptimizationResult."),
    ("OptimizationResult", "Haelt Passes, Forward-Passes, CombinedPass und ScoreWeights."),
    ("RobustnessScorecardGenerator", "Erzeugt HTML-Scorecards und berechnet Overall Score."),
    ("PdfReportGenerator", "Erzeugt Strategie-PDFs fuer Exportpakete."),
    ("ValidationResult", "Verdict-Regeln fuer Step-7-OOS-Ergebnisse."),
    ("DatabaseManager", "Erzeugt Tabellen, speichert History, Settings, Workflow und Reviews."),
    ("DukascopyDownloader", "Laedt BI5-Dateien stundenweise und verwaltet Fortschritt."),
    ("Bi5Decoder", "Dekodiert LZMA-komprimierte BI5-Ticks."),
    ("CsvConverter", "Aggregiert Ticks zu M1-Bars und schreibt CSV."),
    ("Mt5DataImporter", "Startet MT5 mit Importskript fuer Custom Symbols."),
    ("CustomSymbolManager", "Speichert Metadaten importierter Symbole."),
    ("StrategyExporter", "Headless Export- und Controlling-Report-Service."),
    ("backtester_mcp.py", "MCP-Server fuer lesenden Zugriff auf SQLite-Backtester-Daten."),
]


TROUBLESHOOTING = [
    ("MetaTrader startet und beendet sich sofort", "Oft laeuft bereits eine Instanz im gleichen portable-Verzeichnis. ProcessGuard/AutoKill pruefen und MT5 sauber beenden."),
    ("Kein Report wird gefunden", "Report-Pfad, ShutdownTerminal, alte Reportdateien und Tester-Logs pruefen. BacktestRunner wartet auf erwartete Reportnamen."),
    ("Optimierung hat 0 Paesse", "Parameterbereiche, OptimizationMode, EA-Kompilierung und MT5-Tester-Log pruefen."),
    ("Forward-Werte fehlen", "ForwardMode deaktiviert oder MT5 hat keinen Forward-Report erzeugt. requireForward-Filter beachten."),
    ("Step 7 ist nicht startbar", "Validierungsfenster muss nach dem Optimierungs-ToDate liegen und ein sinnvolles Enddatum besitzen."),
    ("Strategie wird nicht in Best kopiert", "Nach vorhandenen Step-7-Ergebnissen duerfen nur PASSED-Kandidaten in den Best-Ordner."),
    ("KI-Analyse meldet keinen API-Key", "OpenRouter-Schluessel in KI-Einstellungen setzen; er wird lokal in SQLite gespeichert."),
    ("KI-Score fehlt", "Sensitivitaetsdaten fuer die Passes pruefen; LlmAnalysisService liest SENSITIVITY_DETAIL."),
    ("Dukascopy-Daten fehlen fuer einzelne Tage", "Download-Scan nutzen; Wochenenden und Feiertage koennen keine Ticks liefern."),
    ("CSV-Import in MT5 klappt nicht", "Custom Symbol Name, Digits, Skriptdeployment und MT5-Log pruefen."),
    ("Parameter wirken falsch exportiert", "SET-Merge, EaParameterManager und Pass-Parameterwerte kontrollieren."),
    ("Score wirkt kontraintuitiv", "ScoreWeights pruefen; hohe Gewichte fuer FW-Trades und SampleSize koennen kleine Gewinnwunder bremsen."),
    ("UI zeigt alten Zustand", "Workflow-State aus SQLite wurde geladen. Workflow resetten oder passende History wiederherstellen."),
    ("MCP findet Datenbank nicht", "Backtester einmal starten, damit %USERPROFILE%/.mt5_backtester/history.db angelegt wird."),
    ("Reports sind optisch unvollstaendig", "Report-Generator und Quellreport pruefen; Parser kann nur vorhandene MT5-Daten extrahieren."),
]


CHECKLISTS = [
    ("Vor Optimierung", "Zeitfenster festlegen", "In-Sample, Forward und spaeteres OOS-Fenster bewusst trennen."),
    ("Vor Optimierung", "Datenqualitaet pruefen", "Symbolhistorie, Custom Symbols und fehlende Tage kontrollieren."),
    ("Vor Optimierung", "Parameterzahl reduzieren", "Nur fachlich sinnvolle Parameter optimieren, nicht jedes Feld."),
    ("Vor Optimierung", "Kostenannahmen pruefen", "Spread, Kommission und Ausfuehrungsmodell im MT5-Kontext verstehen."),
    ("Step 2", "ForwardMode aktivieren", "Ohne Forward fehlt eine wichtige Auswahlperspektive."),
    ("Step 2", "Optimierungsmodus waehlen", "Genetic fuer Suche, Complete fuer kleinere, kritische Raeume."),
    ("Step 3", "Mindesttrades nutzen", "Kleine Stichproben nicht mit robusten Strategien verwechseln."),
    ("Step 3", "Drawdown begrenzen", "Profit ohne Risiko-Kontext ist gefaehrlich."),
    ("Step 3", "Diversity erzwingen", "Nicht fuenf fast identische Paesse exportieren."),
    ("Step 4", "CV-Werte lesen", "Worst-CV ist wichtiger als ein schoener Durchschnitt."),
    ("Step 4", "Kurvenformen pruefen", "Plateaus sind besser als Peaks."),
    ("Step 5", "KI-Bericht nicht blind glauben", "KI erklaert Muster, ersetzt aber keine Validierung."),
    ("Step 6", "KI-Gate-Warnungen ernst nehmen", "Ein Bypass ist kein Qualitaetssiegel."),
    ("Step 7", "Validierungsfenster sauber waehlen", "Fenster muss nach Auswahl liegen und genug Marktaktivitaet enthalten."),
    ("Step 7", "NO_TRADES analysieren", "Kein Trade ist keine bestandene Robustheit, sondern fehlende Evidenz."),
    ("Nach Export", "Best-Ordner pruefen", "Nur PASSED-Strategien sollen dort landen, wenn Validierungen existieren."),
    ("Nach Export", "SET und PDF zusammen halten", "Parameterdatei und Report muessen dieselbe Pass-Identitaet tragen."),
    ("Betrieb", "History pflegen", "Alte Laeufe nicht loeschen, bevor wichtige Vergleiche gesichert sind."),
    ("Betrieb", "Reviews schreiben", "Manuelle Beobachtungen im Controlling dokumentieren."),
    ("Entwicklung", "ForwardSplit-Test beachten", "Aenderungen am Split koennen die Anti-Curvefitting-Aussage zerstoeren."),
    ("Entwicklung", "ScoreWeights zentral halten", "Keine parallelen Defaults in UI oder Reports einfuehren."),
    ("Entwicklung", "DB-Migrationen defensiv bauen", "Bestehende Nutzer-Daten duerfen nicht brechen."),
    ("Entwicklung", "Runner-Seiteneffekte kapseln", "Prozessstart, Reportdateien und Timeouts gehoeren in die Engine."),
    ("Entwicklung", "Tests erweitern", "Neue Gates oder Parameter brauchen Regressionstests."),
]


WORKFLOW_DETAILS = [
    ("1 Setup", "WorkflowEngine.runStep1", "Speichert Strategiegrunddaten, EA-Parameter, Zeitraum und Kontoannahmen.", "Falsche Zeitraumwahl verhindert spaetere OOS-Validierung."),
    ("2 MT5-Optimierung", "WorkflowEngine.runStep2 / OptimizationRunner", "Startet MT5-Optimierung mit ForwardMode und schreibt OptimizationResult.", "Zu grosse Suchraeume erhoehen Multiple-Testing-Bias."),
    ("3 Diversity-Auswahl", "WorkflowEngine.runStep3", "Filtert CombinedPasses nach Profit, Trades, Drawdown, Score und Diversitaet.", "Aehnliche Paesse duerfen nicht als echtes Portfolio missverstanden werden."),
    ("4 Sensitivitaet", "WorkflowEngine.runStep4 / SensitivityRunner", "Variiert Parameter einzeln und speichert CV sowie Kurven in SENSITIVITY_DETAIL.", "Peak-Parameter werden sichtbar; String/Enum-Parameter werden ausgelassen."),
    ("5 KI-Bewertung", "WorkflowEngine.runStep5 / LlmAnalysisService", "Sendet Kennlinien und Performance-Kontext an OpenRouter und parst Stabilitaetsscores.", "KI bewertet Muster, ersetzt aber keine Datenvalidierung."),
    ("6 Portfolio", "WorkflowEngine.runStep6 / exportPortfolio", "Kombiniert Performance- und KI-Score, exportiert SET/PDF und markiert KI-Gate-Bypass.", "Export ist noch keine finale Live-Freigabe."),
    ("7 Validierung", "WorkflowEngine.runStep7 / ValidationResult", "Testet finale Paesse auf nachgelagertem OOS-Fenster und schreibt Validierungsreport.", "Nur PASSED darf nach vorhandener Validierung in den Best-Ordner."),
]


ARCHITECTURE_DECISIONS = [
    ("MetaTrader bleibt Simulationsmotor", "Die Java-App orchestriert, aber ersetzt den MT5/MT4 Strategy Tester nicht.", "EA-Verhalten bleibt nah an der Zielplattform."),
    ("tester.ini statt GUI-Automation", "Konfiguration wird als Datei erzeugt und per CLI gestartet.", "Weniger fehleranfaellig als Klick-Automation."),
    ("Runner kapseln Seiteneffekte", "Prozessstart, Logs, Timeouts und Reportdateien liegen in engine.", "UI bleibt testbarer und fachlich schlanker."),
    ("SQLite im Benutzerprofil", "history.db liegt unter .mt5_backtester.", "Nutzerdaten werden nicht ins Git geschrieben."),
    ("ScoreWeights als Single Source", "Ranking und Scorecard lesen dieselben Defaults.", "Keine divergierenden Bewertungen zwischen UI und Report."),
    ("ForwardSplit isoliert", "MT5-Splitlogik ist in eigener Klasse und getestet.", "BT/FW-Sensitivitaet bleibt fachlich korrekt."),
    ("Step 7 nach Exportauswahl", "Finale OOS-Validierung passiert nach Optimierung und Portfolio-Auswahl.", "Forward-Fenster wird nicht als unberuehrter Beweis missbraucht."),
    ("KI als Analyst", "LLM interpretiert Kennlinien und Scores, trifft aber nicht allein die Freigabe.", "Sprachliche Plausibilitaet bleibt von Datenvalidierung getrennt."),
    ("Best-Ordner-Gate", "Validierungsergebnisse beeinflussen Export in exports_gut.", "Fehlgeschlagene Kandidaten werden nicht als Top-Strategien präsentiert."),
    ("MCP read-only", "Der MCP-Server erlaubt nur lesende SQLite-Abfragen.", "KI-Assistenten koennen analysieren, aber Daten nicht veraendern."),
    ("Dukascopy als Datenpipeline", "BI5-Download, Decode, CSV und MT5-Import sind eigene Schritte.", "Datenqualitaet wird nachvollziehbar."),
    ("JavaFX als primaere UI", "Moderne Views tragen die aktive Nutzerfuehrung.", "Swing kann koexistieren, ohne die Hauptarchitektur zu blockieren."),
    ("Reports als Belege", "PDF/HTML/SET werden zusammen exportiert.", "Strategieentscheidung bleibt nachvollziehbar."),
    ("Tests als Methodenschutz", "ForwardSplit, Workflow-Gates und Scorecard sind regressionsrelevant.", "Fachliche Schutzlogik wird bei Aenderungen nicht still gebrochen."),
]


DATA_FLOWS = [
    ("Einzeltest", "BacktestView -> BacktestConfig -> IniGenerator -> BacktestRunner -> MetaTrader -> ReportParser -> BacktestResult -> DatabaseManager"),
    ("Optimierung", "OptimizationView -> OptimizationConfig -> IniGenerator -> OptimizationRunner -> MT5 XML -> OptimizationReportParser -> OptimizationResult"),
    ("Workflow", "WorkflowView -> WorkflowEngine -> OptimizationRunner/SensitivityRunner/LlmAnalysisService/BacktestRunner -> ValidationResult -> Export"),
    ("Sensitivitaet", "CombinedPass -> SensitivityRunner -> Parameter-Sweep -> SENSITIVITY_DETAIL -> RobustnessScorecard/KI"),
    ("Dukascopy", "DukascopyView -> DukascopyDownloader -> BI5 -> Bi5Decoder -> CsvConverter -> Mt5DataImporter -> Custom Symbol"),
    ("Controlling", "DatabaseManager -> History/Reviews/AutomaticReviews -> ControllingView -> Nachtest/Export"),
    ("MCP", "Claude oder anderer Client -> backtester_mcp.py -> read-only SQLite -> JSON-Antwort"),
]


def font(size: int = 16, bold: bool = False):
    candidates = [
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    bold_candidates = [
        "C:/Windows/Fonts/segoeuib.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
    ]
    for p in (bold_candidates if bold else candidates):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def rounded_box(draw: ImageDraw.ImageDraw, xy, fill, outline="#233044", radius=14, width=2):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def arrow(draw: ImageDraw.ImageDraw, start, end, fill="#334155", width=4):
    draw.line([start, end], fill=fill, width=width)
    x1, y1 = start
    x2, y2 = end
    if x2 >= x1:
        pts = [(x2, y2), (x2 - 14, y2 - 8), (x2 - 14, y2 + 8)]
    else:
        pts = [(x2, y2), (x2 + 14, y2 - 8), (x2 + 14, y2 + 8)]
    draw.polygon(pts, fill=fill)


def draw_wrapped(draw, xy, text, width, fnt, fill="#0f172a", spacing=4):
    lines = []
    for raw in text.split("\n"):
        words = raw.split()
        current = ""
        for word in words:
            test = (current + " " + word).strip()
            if draw.textbbox((0, 0), test, font=fnt)[2] <= width:
                current = test
            else:
                if current:
                    lines.append(current)
                current = word
        if current:
            lines.append(current)
    x, y = xy
    for line in lines:
        draw.text((x, y), line, font=fnt, fill=fill)
        y += fnt.size + spacing


def make_diagrams() -> dict[str, Path]:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    diagrams: dict[str, Path] = {}
    title_f = font(28, True)
    label_f = font(16, True)
    body_f = font(13, False)

    def save(name: str, img: PILImage.Image):
        path = ASSET_DIR / f"{name}.png"
        img.save(path)
        diagrams[name] = path

    img = PILImage.new("RGB", (1400, 780), "#f8fafc")
    d = ImageDraw.Draw(img)
    d.text((50, 34), "Backtester Architektur - vom Nutzerklick zum validierten Strategie-Export", font=title_f, fill="#0f172a")
    boxes = [
        ((70, 140, 320, 300), "#dbeafe", "JavaFX UI", "MainView, WorkflowView, BacktestView, OptimizationView, ControllingView"),
        ((410, 140, 660, 300), "#dcfce7", "Engine", "BacktestRunner, OptimizationRunner, WorkflowEngine, SensitivityRunner"),
        ((750, 140, 1000, 300), "#fef3c7", "MetaTrader", "tester.ini, terminal64.exe, Strategy Tester, Reports"),
        ((1090, 140, 1340, 300), "#fee2e2", "Reports", "XML/HTM Parser, Scorecard, HTML, PDF, SET Export"),
        ((410, 430, 660, 590), "#ede9fe", "SQLite", "History, Workflow State, Sensitivity Detail, KI Reports"),
        ((750, 430, 1000, 590), "#cffafe", "Dukascopy/MT5 Data", "BI5 Download, CSV, Custom Symbol Import"),
    ]
    for xy, fill, head, body in boxes:
        rounded_box(d, xy, fill)
        d.text((xy[0] + 20, xy[1] + 20), head, font=label_f, fill="#0f172a")
        draw_wrapped(d, (xy[0] + 20, xy[1] + 58), body, xy[2] - xy[0] - 40, body_f)
    for s, e in [((320, 220), (410, 220)), ((660, 220), (750, 220)), ((1000, 220), (1090, 220)), ((535, 300), (535, 430)), ((875, 430), (875, 300)), ((660, 510), (750, 510))]:
        arrow(d, s, e)
    save("architecture", img)

    img = PILImage.new("RGB", (1400, 760), "#ffffff")
    d = ImageDraw.Draw(img)
    d.text((50, 34), "7-Schritt Anti-Curvefitting Workflow", font=title_f, fill="#0f172a")
    steps = [
        "1 Setup\nEA, Symbol, Zeitraum, Parameter",
        "2 Optimierung\nComplete/Genetic mit Forward",
        "3 Diversity\nFilter + robuste Kandidaten",
        "4 Sensitivitaet\nBT/FW Kennlinien und CV",
        "5 KI Analyse\nKurvenform + Stabilitaet",
        "6 Portfolio\nGewichtete Auswahl + Export",
        "7 Validierung\nUnberuehrtes OOS-Fenster",
    ]
    x = 50
    y = 190
    for i, text in enumerate(steps):
        xy = (x, y, x + 175, y + 190)
        rounded_box(d, xy, ["#dbeafe", "#dcfce7", "#fef3c7", "#ede9fe", "#cffafe", "#fee2e2", "#e0f2fe"][i])
        head, body = text.split("\n", 1)
        d.text((x + 18, y + 20), head, font=label_f, fill="#0f172a")
        draw_wrapped(d, (x + 18, y + 64), body, 140, body_f)
        if i < len(steps) - 1:
            arrow(d, (x + 175, y + 95), (x + 210, y + 95), fill="#475569")
        x += 190
    d.text((70, 490), "Fachlicher Kern: Der Forward-Test ist ein Auswahlkriterium. Erst Schritt 7 liefert die echte, nachgelagerte Out-of-Sample-Schaetzung.", font=label_f, fill="#991b1b")
    save("workflow", img)

    img = PILImage.new("RGB", (1300, 720), "#f8fafc")
    d = ImageDraw.Draw(img)
    d.text((50, 34), "MT5 Prozessablauf im Backtester", font=title_f, fill="#0f172a")
    lanes = [
        ("Backtester", "#dbeafe", ["Konfiguration lesen", "tester.ini schreiben", "Prozess starten", "Report suchen", "Report parsen"]),
        ("MetaTrader", "#dcfce7", ["terminal64.exe", "Strategy Tester", "Optimization/Backtest", "HTM/XML Report", "Shutdown"]),
    ]
    for row, (name, fill, items) in enumerate(lanes):
        y0 = 150 + row * 230
        d.text((50, y0 + 50), name, font=label_f, fill="#0f172a")
        x = 230
        for item in items:
            rounded_box(d, (x, y0, x + 180, y0 + 120), fill)
            draw_wrapped(d, (x + 18, y0 + 30), item, 140, body_f)
            if x < 230 + 4 * 205:
                arrow(d, (x + 180, y0 + 60), (x + 205, y0 + 60))
            x += 205
    arrow(d, (500, 270), (500, 380), fill="#64748b")
    arrow(d, (1050, 380), (1050, 270), fill="#64748b")
    save("mt5_process", img)

    img = PILImage.new("RGB", (1350, 760), "#ffffff")
    d = ImageDraw.Draw(img)
    d.text((50, 34), "Persistenzmodell: SQLite als Gedaechtnis der Anwendung", font=title_f, fill="#0f172a")
    tables = [
        ("HISTORY_RUNS", "Backtests, Optimierungen, Workflows"),
        ("WORKFLOW_STATE", "Aktive Pipeline mit Step-7-Ergebnissen"),
        ("SENSITIVITY_DETAIL", "Normalisierte CV- und Kurvendaten"),
        ("APP_SETTINGS", "OpenRouter, Score-Gewichte, Filter"),
        ("KI_REPORTS", "Markdown-Auswertungen der KI"),
        ("EA_SAVED_CONFIGS", "Gespeicherte Parameterprofile"),
        ("STRATEGY_REVIEWS", "Manuelle Reviews im Controlling"),
        ("MULTI_BACKTEST_BATCHES", "Batch-Historie und Multi-Reports"),
    ]
    for i, (t, desc) in enumerate(tables):
        col = i % 4
        row = i // 4
        x = 70 + col * 315
        y = 155 + row * 230
        rounded_box(d, (x, y, x + 260, y + 150), "#f1f5f9")
        d.text((x + 18, y + 22), t, font=label_f, fill="#0f172a")
        draw_wrapped(d, (x + 18, y + 64), desc, 220, body_f)
    save("database", img)

    img = PILImage.new("RGB", (1300, 720), "#f8fafc")
    d = ImageDraw.Draw(img)
    d.text((50, 34), "Scorecard-Modell: gemessene Saeulen statt synthetischer Schoenheit", font=title_f, fill="#0f172a")
    bars = [
        ("BT Profit", 15, "#2563eb"), ("FW Profit", 15, "#16a34a"), ("Consistency", 10, "#9333ea"),
        ("Risk", 10, "#dc2626"), ("Sharpe", 10, "#0891b2"), ("Sample", 25, "#ca8a04"),
        ("FW Trades", 30, "#0f766e"), ("Recovery", 25, "#7c3aed"),
    ]
    x = 90
    for name, val, color in bars:
        h = val * 10
        d.rectangle((x, 570 - h, x + 95, 570), fill=color)
        d.text((x + 18, 590), str(val), font=label_f, fill="#0f172a")
        draw_wrapped(d, (x - 5, 620), name, 110, body_f)
        x += 145
    d.line((70, 570, 1220, 570), fill="#334155", width=3)
    d.text((90, 145), "Alle Gewichte werden normalisiert. Die Saeulen beruhen auf realen MT5-Kennzahlen wie Profit, Trades, Drawdown, Sharpe und Recovery.", font=label_f, fill="#334155")
    save("scorecard", img)

    img = PILImage.new("RGB", (1300, 720), "#ffffff")
    d = ImageDraw.Draw(img)
    d.text((50, 34), "Warum Step 7 noetig ist", font=title_f, fill="#0f172a")
    regions = [
        (90, 190, 480, 360, "#dbeafe", "In-Sample", "Optimierung sucht Parameter."),
        (500, 190, 820, 360, "#fef3c7", "Forward", "Wird bereits fuer Auswahl verbraucht."),
        (850, 190, 1210, 360, "#dcfce7", "Step-7 OOS", "Erst nach Auswahl: echte Validierung."),
    ]
    for xy0, yy0, xy1, yy1, fill, head, body in regions:
        rounded_box(d, (xy0, yy0, xy1, yy1), fill)
        d.text((xy0 + 24, yy0 + 28), head, font=label_f, fill="#0f172a")
        draw_wrapped(d, (xy0 + 24, yy0 + 70), body, xy1 - xy0 - 48, body_f)
    for s, e in [((480, 275), (500, 275)), ((820, 275), (850, 275))]:
        arrow(d, s, e)
    d.text((100, 470), "Wenn tausende Paesse getestet werden, findet man fast immer Gewinner im Forward-Fenster. Das ist noch kein Beweis fuer Live-Robustheit.", font=label_f, fill="#991b1b")
    save("oos_gate", img)

    img = PILImage.new("RGB", (1300, 720), "#f8fafc")
    d = ImageDraw.Draw(img)
    d.text((50, 34), "Curve Fitting: Signal, Rauschen und die falsche Sicherheit", font=title_f, fill="#0f172a")
    left = [(100, 520), (180, 450), (260, 410), (340, 350), (420, 300), (500, 260), (580, 210)]
    right = [(720, 500), (780, 260), (840, 530), (900, 220), (960, 500), (1020, 260), (1080, 520), (1140, 230)]
    d.line(left, fill="#2563eb", width=5)
    d.line(right, fill="#dc2626", width=5)
    for p in left + right:
        d.ellipse((p[0] - 6, p[1] - 6, p[0] + 6, p[1] + 6), fill="#0f172a")
    d.text((130, 590), "Robuster Trend: wenige Regeln, stabile Nachbarschaft", font=label_f, fill="#1e3a8a")
    d.text((710, 590), "Ueberangepasste Kurve: perfekte Vergangenheit, fragile Zukunft", font=label_f, fill="#991b1b")
    save("curve_fitting", img)
    return diagrams


def p(text: str) -> str:
    return " ".join(textwrap.dedent(text).strip().split())


def repeated_lesson(theme: str, project_link: str) -> list[str]:
    return [
        p(f"""
        In der Praxis ist {theme} nie nur ein einzelner Knopf. Es ist eine Kette von Annahmen:
        Welche Daten gelten als bekannt, welche Kosten werden simuliert, welche Parameter wurden
        bereits gesehen und welche Kennzahlen sind wirklich unabhaengig? Der Backtester macht diese
        Kette sichtbar, weil fast jeder Schritt eine eigene Klasse, einen eigenen Report oder einen
        eigenen Datenbankeintrag besitzt. Dadurch kann ein Nutzer spaeter nachvollziehen, ob eine
        Entscheidung aus dem Marktverhalten entstand oder aus einer zufaelligen Optimierungsspur.
        """),
        p(f"""
        Fuer das Projekt bedeutet das konkret: {project_link}. Diese technische Entscheidung hat
        eine fachliche Wirkung. Sie trennt Bedienkomfort von Bewertungslogik und verhindert, dass
        die Oberflaeche allein zur Quelle der Wahrheit wird. Das ist wichtig, weil robuste
        Strategieentwicklung wiederholbar sein muss. Ein gutes Ergebnis ist nur dann ernst zu
        nehmen, wenn derselbe Ablauf mit denselben Daten und Parametern wiederhergestellt werden kann.
        """),
    ]


def build_chapters(snapshot: dict[str, object]) -> list[Section]:
    packages = snapshot["packages"]
    java_lines = snapshot["main_java_lines"]
    test_lines = snapshot["test_java_lines"]
    return [
        Section(
            "Vorwort: Warum dieses Buch existiert",
            [
                p(f"""
                Dieses Buch dokumentiert den Backtester als konkretes Softwareprojekt und fuehrt
                zugleich in die Denkweise robuster Strategieentwicklung ein. Es ist kein reines
                Benutzerhandbuch und auch kein isolierter Architekturbericht. Es verbindet die
                Bedienung des Programms mit den Gruenden, warum seine Pipeline so gebaut wurde:
                MetaTrader wird automatisiert, Optimierungen werden strukturiert, Sensitivitaet
                wird messbar, KI wird als Analyst und nicht als Orakel eingesetzt, und die finale
                Strategieauswahl wird durch eine echte Out-of-Sample-Validierung abgesichert.
                """),
                p(f"""
                Die Analyse basiert auf dem Sourcecode dieses Repositories. Zum Zeitpunkt der
                Bucherstellung umfasst die Hauptanwendung {snapshot['main_java_files']} Java-Dateien
                mit rund {java_lines:,} Zeilen und die Tests {snapshot['test_java_files']} Java-Dateien
                mit rund {test_lines:,} Zeilen. Besonders relevant sind die Pakete engine, report,
                database, config, dukascopy, mt5 und ui.javafx. Die vorhandenen Markdown-Dokumente,
                README-Dateien, Screenshots und Tests wurden als zusaetzliche Quellen herangezogen.
                """).replace(",", "."),
                p("""
                Der Text richtet sich an Nutzer, die keine Quant-Profis sind, aber trotzdem
                verstehen wollen, was ein Backtest leistet, was er nicht leisten kann und wie man
                mit einem Werkzeug wie diesem die typischen Denkfallen reduziert. Er richtet sich
                auch an Entwickler, die die Architektur erweitern moechten, ohne die fachlichen
                Schutzmechanismen aus Versehen zu unterlaufen.
                """),
            ],
        ),
        Section(
            "Kapitel 1: Grundlagen des Backtestings",
            [
                p("""
                Ein Backtest ist ein kontrolliertes Gedankenexperiment mit historischen Daten.
                Eine Handelsregel wird so behandelt, als haette man sie in der Vergangenheit
                bereits gekannt, und anschliessend wird berechnet, welche Trades sie erzeugt haette.
                Der Nutzen liegt darin, schlechte Ideen schnell auszusortieren und gute Ideen
                genauer zu untersuchen, bevor echtes Kapital eingesetzt wird. Der Fehler beginnt,
                wenn man das Ergebnis als Vorhersage missversteht. Ein Backtest sagt nicht: Diese
                Strategie wird in Zukunft Geld verdienen. Er sagt nur: Unter den simulierten
                Annahmen haette diese Logik in diesem historischen Zeitraum so ausgesehen.
                """),
                p("""
                Im Backtester-Projekt wird diese Idee praktisch an MetaTrader delegiert. Die Java-
                Anwendung schreibt eine tester.ini, startet MT4 oder MT5, wartet auf den Report,
                kopiert die Ergebnisdateien und parst die Kennzahlen. Dadurch bleibt die eigentliche
                Handelssimulation bei der Plattform, die Expert Advisors ohnehin ausfuehrt. Die
                Backtester-Anwendung wird zur Orchestrierungsschicht: Sie standardisiert Eingaben,
                automatisiert Wiederholungen, sammelt Resultate und fuegt robuste Bewertung hinzu.
                """),
                p("""
                Gute Backtests brauchen drei Arten von Disziplin. Erstens technische Disziplin:
                Reports duerfen nicht veraltet sein, Prozesse duerfen nicht haengen bleiben,
                Parameterdateien muessen exakt zugeordnet werden. Zweitens statistische Disziplin:
                wenige Trades sind schwache Evidenz, ein einzelner Zeitraum ist keine Marktwahrheit,
                und jede zusaetzliche Optimierungsrunde erhoeht die Gefahr, Rauschen zu lernen.
                Drittens operative Disziplin: Ergebnisse muessen gespeichert, reproduzierbar und
                kommentierbar sein. Genau aus dieser Dreiteilung erklaert sich die Architektur des
                Projekts.
                """),
                *repeated_lesson("Backtesting", "BacktestRunner, IniGenerator, ReportParser und DatabaseManager bilden gemeinsam den Kern eines reproduzierbaren Einzeltests"),
                p("""
                Fuer Einsteiger ist die wichtigste Regel: Ein Backtest ist kein Verkaufsprospekt,
                sondern ein Diagnoseinstrument. Eine Strategie mit Verlust im Backtest ist meistens
                kein Kandidat. Eine Strategie mit Gewinn im Backtest ist aber erst ein Anfang. Man
                muss fragen: Wie viele Trades gab es? Wie hoch war der Drawdown? Hat der Gewinn nur
                in einer Marktphase stattgefunden? Veraendert sich das Ergebnis dramatisch, wenn ein
                Parameter minimal verschoben wird? Genau diese Folgefragen fuehren zur Pipeline des
                Backtesters.
                """),
            ],
            bullets=[
                "Backtest: Simulation historischer Trades mit einer bekannten Regel.",
                "Forward-Test: Teil des Optimierungsfensters, der nicht fuer die Parameterberechnung genutzt wird, aber spaeter oft in die Auswahl einfliesst.",
                "Out-of-Sample: Daten, die waehrend Optimierung und Auswahl unberuehrt bleiben.",
                "Robustheit: Ein Ergebnis bleibt plausibel, wenn Zeitraum, Parameter oder Daten leicht variieren.",
            ],
        ),
        Section(
            "Kapitel 2: Curve Fitting, Overfitting und Biases",
            [
                p("""
                Curve Fitting bedeutet, dass eine Strategie so eng an historische Daten angepasst
                wird, dass sie nicht mehr das Marktsignal, sondern die Eigenheiten der Stichprobe
                beschreibt. In der Rueckschau sieht das oft beeindruckend aus: die Equity-Kurve ist
                glatt, der Profit hoch, der Drawdown klein. Im Live-Handel verschwindet die Magie,
                weil die Zukunft nicht dieselben Zufallsbewegungen wiederholt. Overfitting ist
                deshalb kein Randproblem, sondern die zentrale Gefahr jeder Strategieoptimierung.
                """),
                p("""
                Je mehr Parameter eine Strategie besitzt, desto groesser wird der Suchraum. Wenn
                man tausende Kombinationen testet, ist es statistisch fast unvermeidbar, dass einige
                Kombinationen zufaellig gut aussehen. Das ist kein Betrug, sondern Mathematik:
                Viele Vergleiche erzeugen viele Chancen auf Scheintreffer. Der Backtester reagiert
                darauf nicht mit einem einzigen magischen Score, sondern mit einer Abfolge von
                Filtern, Sensitivitaetsmessungen, KI-Analyse und abschliessender Validierung.
                """),
                p("""
                Die Quellenlage bestaetigt diese Vorsicht. QuantStart betont, dass Backtests durch
                Biases systematisch zu optimistisch wirken koennen. Investopedia beschreibt die
                Bedeutung von Korrelation zwischen Backtest, Out-of-Sample und Forward/Paper-Test.
                AlgoTrading101 nennt Look-ahead Bias, Survivorship Bias und Curve Fitting als
                typische Risiken. Walk-Forward-Ansatz und finale Holdout-Fenster werden in modernen
                Best-Practice-Texten als Gegenmittel beschrieben, aber nie als Garantie.
                """),
                *repeated_lesson("Curve Fitting", "ForwardSplit und ValidationResult kodieren im Projekt die Unterscheidung zwischen Auswahlfenster und echter Validierung"),
                p("""
                Ein anschauliches Beispiel: Eine Strategie hat einen Take-Profit-Parameter. Bei 48,
                49, 50, 51 und 52 Punkten bleibt der Gewinn aehnlich. Das ist ein Plateau und damit
                ein robuster Hinweis. Wenn aber nur der Wert 50 extrem gut ist und 49 sowie 51
                sofort einbrechen, ist der Parameter ein Peak. Peaks koennen auftreten, weil genau
                diese historische Stichprobe zufaellig passte. Der Backtester versucht, solche Peaks
                durch Sensitivitaetskurven, CV-Werte und KI-Kurvenformanalyse sichtbar zu machen.
                """),
                p("""
                Wichtig ist auch: Ein Forward-Test innerhalb einer MT5-Optimierung ist wertvoll,
                aber er bleibt Teil der Auswahl. Sobald man die besten Paesse anhand ihrer Forward-
                Kennzahlen sortiert, filtert oder exportiert, wurde dieses Fenster verbraucht. Es
                ist dann kein komplett unberuehrter Beweis mehr. Genau darum fuegt das Projekt
                Schritt 7 hinzu: erst nach Portfolio-Auswahl wird auf einem spaeteren Fenster ein
                einfacher Backtest mit den finalen Parametern ausgefuehrt.
                """),
            ],
        ),
        Section(
            "Kapitel 3: Architektur des Backtester-Projekts",
            [
                p(f"""
                Die Anwendung ist ein Java-17/Maven-Projekt mit JavaFX als aktueller Hauptoberflaeche,
                einer aelteren Swing-Schicht, SQLite als lokaler Persistenz und OpenPDF/ReportLab-
                aehnlicher Report-Logik im Java-Code. Maven baut eine Fat-JAR mit com.backtester.Main
                als Einstiegspunkt. Der Main-Pfad prueft zuerst den CLI-Modus, initialisiert AppConfig,
                raeumt alte MetaTrader-Prozesse auf und startet dann die JavaFX-App.
                """),
                p("""
                Architektonisch gibt es vier grosse Stroeme. Der Bedien-Strom beginnt in MainView
                und den JavaFX-Views. Der Ausfuehrungs-Strom geht in engine-Klassen wie BacktestRunner
                und OptimizationRunner. Der Ergebnis-Strom laeuft ueber report-Klassen, die XML/HTM
                parsen und Scorecards erzeugen. Der Gedächtnis-Strom endet in DatabaseManager, der
                Einstellungen, Historie, Workflows, Sensitivitaetsdaten und Reviews in SQLite haelt.
                """),
                p("""
                Eine wichtige Projektbesonderheit ist die Koexistenz von JavaFX und Swing. Swing
                ist nicht wertloser Altbestand: viele Konzepte und Panels zeigen die Entwicklung des
                Werkzeugs und bleiben als Funktionsschicht vorhanden. Fuer die aktuelle
                Nutzerfuehrung ist jedoch JavaFX entscheidend, besonders WorkflowView,
                WorkflowConfigDialogs, ControllingView und die spezialisierten Dialoge fuer
                Strategieauswertung.
                """),
                *repeated_lesson("Architektur", "die Pakete trennen UI, Engine, Reporting, Persistenz und Datenimport so, dass jede Schicht eine erkennbare Verantwortung traegt"),
                p("""
                Fuer Entwickler ist die wichtigste Architekturregel: Die UI darf nicht zur einzigen
                Wahrheit werden. Wenn ein Button eine Strategie exportiert, muss die fachliche
                Entscheidung im WorkflowEngine-Zustand, in Validierungsergebnissen und in
                Datenbankeintraegen wiederzufinden sein. Nur so kann das Projekt spaeter erweitert
                werden, ohne dass die Anti-Curvefitting-Gates durch eine neue Oberflaechenaktion
                umgangen werden.
                """),
            ],
        ),
        Section(
            "Kapitel 4: Benutzeroberflaeche und Bedienlogik",
            [
                p("""
                Die primaere Oberflaeche ist JavaFX. JavaFXMain erzeugt eine Szene mit MainView,
                laedt antigravity.css und setzt den Fenstertitel. MainView organisiert die grossen
                Arbeitsbereiche: Backtest, Multi-Backtest, Optimizer, Robustness, Dukascopy,
                History, Settings, Help, Workflow und Controlling. Jeder Bereich entspricht einem
                fachlichen Arbeitsmodus, nicht nur einer visuellen Registerkarte.
                """),
                p("""
                BacktestView dient dem Einzeltest. Hier werden Expert Advisor, Symbol, Zeitraum,
                Modell, Kontoannahmen und Parameterprofil ausgewaehlt. OptimizationView fuehrt in
                den MT5-Optimizer, inklusive Parameter-Tabelle, AutoConfig, Forward-Konfiguration
                und Ergebnisanalyse. MultiBacktestView skaliert Einzeltests ueber mehrere EAs,
                Symbole und Perioden. DukascopyView verbindet externe Tickdaten mit dem lokalen
                MT5-Datenmodell.
                """),
                p("""
                WorkflowView ist die didaktisch wichtigste Oberflaeche. Sie macht die Pipeline
                sichtbar: Setup, Optimierung, Diversity-Auswahl, Sensitivitaet, KI-Bewertung,
                Portfolio-Auswahl und Step-7-Validierung. Der Nutzer sieht dadurch nicht nur
                Ergebnisse, sondern auch den Reifegrad der Strategie. Eine Strategie nach Schritt 3
                ist ein Kandidat. Eine Strategie nach Schritt 6 ist ein Portfolio-Vorschlag. Eine
                Strategie nach bestandenem Schritt 7 ist deutlich besser abgesichert.
                """),
                *repeated_lesson("Bedienlogik", "WorkflowView und WorkflowConfigDialogs uebersetzen die Engine-Zustaende in sichtbare Schritte, Dialoge und Gates"),
                p("""
                ControllingView ist die spaetere Bewertungs- und Nachtest-Zentrale. Dort werden
                gespeicherte Strategien betrachtet, manuelle Reviews gepflegt und automatische
                Nachtests gestartet. Diese Sicht ist wichtig, weil robuste Strategieentwicklung
                nicht mit einem Export endet. Nachtests, Kommentare und laengere Historie machen
                sichtbar, ob ein Kandidat im Alltag weiter plausibel bleibt.
                """),
            ],
        ),
        Section(
            "Kapitel 5: Engine Deep Dive",
            [
                p("""
                Die Engine ist die Arbeitsschicht des Backtesters. BacktestRunner erzeugt einen
                Ausgabepfad, schreibt tester.ini, bereinigt alte Reports, prueft auf laufende
                MetaTrader-Prozesse, startet terminal.exe oder terminal64.exe und konsumiert
                Prozessausgaben, um Deadlocks zu vermeiden. Danach wartet er auf Abschluss oder
                Timeout, sucht den erzeugten Report und uebergibt ihn an ReportParser.
                """),
                p("""
                IniGenerator ist klein, aber kritisch. Er kapselt die Unterschiede zwischen MT4
                und MT5. MT4 erwartet andere Schluessel wie TestExpert und TestSymbol, MT5 nutzt
                Expert und Symbol. Auch Modellwerte und Konfigurationspfade unterscheiden sich.
                Indem diese Logik zentral bleibt, muessen Runner und UI nicht an jeder Stelle die
                Plattformdetails kennen.
                """),
                p("""
                OptimizationRunner arbeitet aehnlich wie BacktestRunner, jedoch mit
                Optimierungsparametern, Agentensteuerung, ForwardMode und XML-Parsing. Die
                Ergebnisobjekte landen in OptimizationResult. Dort werden Backtest-Passes und
                Forward-Passes zu CombinedPass-Strukturen zusammengefuehrt. Diese Kombination ist
                die Grundlage fuer Ranking, Filterung, Sensitivitaet und Export.
                """),
                p("""
                WorkflowEngine ist der fachliche Kern. Sie haelt den Zustand der sieben Pipeline-
                Schritte, speichert und laedt Strategie-Konfigurationen, erzeugt Optimierungsruns,
                filtert Kandidaten, startet Sensitivitaetsanalysen, ruft die KI-Bewertung auf,
                kombiniert Performance- und Stabilitaetsscore und exportiert die finalen Strategien.
                Besonders wichtig ist die Regel, dass vorhandene Step-7-Validierungsergebnisse den
                Best-Ordner-Gate beeinflussen: Nicht bestandene oder nicht eindeutig bestandene
                Strategien werden nicht stillschweigend als beste Strategien kopiert.
                """),
                p("""
                SensitivityRunner variiert Parameter um den optimierten Wert und misst, wie stark
                Profit und Kennlinien reagieren. String-, Enum- und boolean-artige Parameter werden
                ausgelassen, weil sie keine sinnvolle kontinuierliche Kennlinie liefern. RobustnessRunner
                fuehrt aehnliche Gedanken in Form von zeitverschobenen Scans und Plateau-Betrachtungen
                weiter. ForwardSplit spiegelt die MT5-Forward-Aufteilung, damit BT- und FW-Fenster in
                der Analyse nicht auseinanderdriften.
                """),
                *repeated_lesson("Engine-Design", "die Runner kapseln Seiteneffekte, waehrend Result-Objekte und WorkflowEngine die fachlichen Entscheidungen tragen"),
            ],
        ),
        Section(
            "Kapitel 6: Reporting, Scoring und Persistenz",
            [
                p("""
                Reporting ist im Projekt mehr als Formatierung. Reports sind Belege. ReportParser
                extrahiert Kennzahlen aus MT4/MT5-Reports, OptimizationReportParser liest
                Optimierungsergebnisse, MultiReportGenerator erzeugt aggregierte HTML-Reports und
                RobustnessScorecardGenerator visualisiert die Bewertungslogik. PdfReportGenerator
                erzeugt Strategie-Reports fuer Exportpakete.
                """),
                p("""
                Der Score ist bewusst mehrsaeulig. Die aktuelle ScoreWeights-Klasse enthaelt
                Gewichte fuer Backtest-Profitabilitaet, Forward-Profitabilitaet, Konsistenz,
                Risiko, Sharpe-basierte Equity-Konsistenz, Stichprobengroesse, Forward-Trades und
                Recovery. Fruehere synthetische oder hart kodierte Saeulen wurden entfernt. Das ist
                fachlich sauber, weil ein Score nur so gut ist wie die Daten, aus denen er besteht.
                """),
                p("""
                DatabaseManager legt die SQLite-Datenbank im Benutzerprofil unter .mt5_backtester
                an. Dort liegen HISTORY_RUNS, WORKFLOW_STATE, SENSITIVITY_DETAIL, APP_SETTINGS,
                KI_REPORTS, MULTI_BACKTEST_BATCHES, EA_PARAMETER_SETTINGS, STRATEGY_REVIEWS,
                STRATEGY_AUTOMATIC_REVIEWS und weitere Tabellen. Diese Datenbank ist die Bruecke
                zwischen laufender GUI, Historie, Controlling und MCP-Server.
                """),
                *repeated_lesson("Persistenz", "DatabaseManager macht Ergebnisse wiederherstellbar und verhindert, dass eine Analyse nur als fluechtiger UI-Zustand existiert"),
                p("""
                Der MCP-Server in mcp-server/backtester_mcp.py oeffnet die SQLite-Datenbank
                lesend und stellt Tools wie get_sensitivity_overview, get_sensitivity_for_pass,
                get_fragile_parameters, get_robust_strategies, get_parameter_curve,
                get_optimization_history und query_database bereit. Damit kann ein lokaler
                KI-Assistent nicht nur freie Texte lesen, sondern strukturierte Backtester-Daten
                abfragen.
                """),
            ],
        ),
        Section(
            "Kapitel 7: Der KI-Ansatz",
            [
                p("""
                Die KI im Backtester ist kein Handelssystem und kein Ersatz fuer Validierung. Sie
                ist ein Analysewerkzeug fuer Muster, die in Tabellen schwer erkennbar sind. Der
                LlmAnalysisService laedt Sensitivitaetsdaten aus der Datenbank, fuegt Performance-
                Kennzahlen hinzu, baut einen deutschen Prompt und ruft OpenRouter auf. Das Modell
                soll pro Pass eine Tabelle, STABILITY_SCORE-Zeilen und eine knappe Begruendung
                liefern.
                """),
                p("""
                Der Prompt zwingt die KI zu einer strukturierten Aufgabe: Kurvenformanalyse,
                CV-Analyse, Performance-Kontext und BT/FW-Konsistenz. Die KI soll unterscheiden,
                ob ein Parameter ein Plateau, eine Glocke, einen Peak, eine Klippe oder chaotisches
                Verhalten zeigt. Diese Begriffe sind didaktisch stark, weil sie den Nutzer von
                reinen Kennzahlen zu Formverstaendnis fuehren.
                """),
                p("""
                Im Workflow wird der KI-Score nicht absolut gesetzt. Step 6 kombiniert den
                numerischen Combined Score mit dem KI-Stabilitaetsscore. Standardmaessig zaehlt
                Performance zu 60 Prozent und KI-Stabilitaet zu 40 Prozent. Gleichzeitig gibt es
                ein KI-Gate: Kandidaten mit sehr niedriger KI-Bewertung werden ausgefiltert. Wenn
                alle Kandidaten durchfallen, erzeugt der Export eine sichtbare Warnung, damit ein
                Notfall-Fallback nicht wie eine normale Validierung aussieht.
                """),
                *repeated_lesson("KI-Auswertung", "LlmAnalysisService nutzt die normalisierte Tabelle SENSITIVITY_DETAIL, sodass die KI nicht raten muss, sondern konkrete Kennlinien und Kennzahlen erhaelt"),
                p("""
                Grenzen bleiben wichtig. Ein Sprachmodell kann Plausibilitaet formulieren und
                Muster benennen, aber es erzeugt keine statistische Gewissheit. Es kennt weder die
                zukuenftige Marktstruktur noch die tatsaechliche Broker-Ausfuehrung. Darum darf die
                KI-Auswertung nie den Step-7-Backtest ersetzen. Sie ist ein sehr nuetzlicher Filter
                zwischen Sensitivitaet und Portfolio-Auswahl, aber der letzte Beleg muss aus Daten
                kommen, nicht aus Sprache.
                """),
            ],
        ),
        Section(
            "Kapitel 8: Datenversorgung mit Dukascopy und MT5 Custom Symbols",
            [
                p("""
                Backtests sind nur so gut wie ihre Daten. Das Projekt enthaelt deshalb eine
                Dukascopy-Schicht. DukascopyDownloader baut stundenweise Download-Aufgaben,
                speichert BI5-Dateien in einer Symbol/Jahr/Monat/Tag-Struktur und kennt
                symbolabhaengige Preis-Punkt-Multiplikatoren. Bi5Decoder liest die LZMA-komprimierten
                Binärdateien und erzeugt Tick-Objekte mit Bid, Ask, Volumen und Zeitstempel.
                """),
                p("""
                CsvConverter aggregiert Ticks zu M1-Bars und schreibt CSV-Dateien in einem Format,
                das fuer den Import in MetaTrader geeignet ist. Mt5DataImporter erzeugt und startet
                ein MQL5-Skript, um CSV-Daten als Custom Symbol in MT5 zu importieren. CustomSymbolManager
                merkt sich lokale Symbol-Metadaten wie Originalsymbol, Datenzeitraum, Digits und
                Aktualisierungsdatum.
                """),
                p("""
                Der didaktische Nutzen dieser Schicht ist gross: Sie trennt den Test von einem
                zufaelligen Brokerfeed. Das macht Ergebnisse nicht automatisch wahr, aber es macht
                Datenqualitaet bewusster. Ein Nutzer kann sehen, welche Daten geladen wurden, welche
                Zeitraeume fehlen und welche Symbole als Custom Symbols fuer Tests verfuegbar sind.
                """),
                *repeated_lesson("Datenversorgung", "DukascopyDownloader, Bi5Decoder, CsvConverter und Mt5DataImporter bilden eine Pipeline von externer Tickquelle bis MT5-Testumgebung"),
            ],
        ),
        Section(
            "Kapitel 9: Parameter-Referenz",
            [
                p("""
                Parameter sind die Sprache, in der der Backtester mit MetaTrader, der Datenbank,
                der KI und dem Nutzer spricht. Ein Parameter ist dabei nie nur ein Feld. Er hat
                einen Ort, eine Default-Annahme, einen fachlichen Effekt und oft auch eine
                Nebenwirkung auf Robustheit oder Reproduzierbarkeit. Dieses Kapitel sammelt die
                wichtigsten Parametergruppen.
                """),
                p("""
                EA-Parameter werden ueber EaParameter und EaParameterManager verwaltet. Ein
                Parameter kann Name, Anzeigename, Wert, Default-Wert, Sektion, Optimierungsstart,
                Schrittweite, Ende, Aktivierungsflag und Typinformation tragen. Fuer SET-Dateien
                ist entscheidend, dass Werte korrekt geschrieben und mit optimierten Passes
                zusammengefuehrt werden. Bei falscher Zuordnung wuerde ein Report eine andere
                Strategie beschreiben als die exportierte Datei.
                """),
                p("""
                Die Score-Parameter verdienen besondere Vorsicht. Mehr Gewicht fuer Forward-Trades
                bestraft duenne Evidenz. Mehr Gewicht fuer Recovery belohnt Erholung nach Drawdown.
                Mehr Gewicht fuer Profit kann Strategien nach oben schieben, die fachlich fragiler
                sind. Darum sind die Gewichte konfigurierbar, aber sie sollten nicht nachtraeglich
                so eingestellt werden, dass ein Lieblingskandidat gewinnt.
                """),
                *repeated_lesson("Parameter", "BacktestConfig, OptimizationConfig, MultiBacktestConfig, WorkflowEngine und ScoreWeights definieren gemeinsam die oeffentliche Fachsprache des Tools"),
            ],
        ),
        Section(
            "Kapitel 10: Robuste Optimierung in der Praxis",
            [
                p("""
                Eine saubere Pipeline beginnt vor dem ersten Klick. Der Nutzer legt fest, welcher
                Zeitraum fuer Entwicklung, welcher fuer Forward-Auswahl und welcher spaeter fuer
                echte Validierung reserviert wird. Wenn das Enddatum der Optimierung bereits heute
                ist, bleibt kein spaeteres Fenster fuer Step 7. Der Backtester erkennt solche
                Situationen und verlangt ein brauchbares Validierungsfenster.
                """),
                p("""
                Schritt 1 sammelt Strategie, Symbol, Zeitraum, Kontoannahmen und Parameter. Schritt
                2 laesst MT5 optimieren. Schritt 3 filtert Kandidaten nicht nur nach Profit, sondern
                auch nach Forward-Ergebnis, Trades, Drawdown und Diversity. Schritt 4 misst
                Sensitivitaet, damit einzelne Glueckstreffer sichtbar werden. Schritt 5 laesst die
                KI Kurvenformen erklaeren. Schritt 6 exportiert ein kleines Portfolio. Schritt 7
                testet dieses Portfolio auf Daten, die vorher nicht zur Auswahl gehoerten.
                """),
                p("""
                In der Praxis sollte ein Nutzer nie nur den hoechsten Score betrachten. Ein
                Kandidat mit Score 78, breitem Plateau, 80 Forward-Trades und moderatem Drawdown
                ist oft interessanter als ein Kandidat mit Score 91, aber nur 6 Forward-Trades und
                einer Peak-Kennlinie. Robustheit bedeutet nicht maximalen historischen Gewinn,
                sondern die hoechste Chance, dass die beobachtete Kante kein Zufallsprodukt war.
                """),
                *repeated_lesson("Optimierungspraxis", "die sieben Workflow-Schritte reduzieren Curve Fitting, indem sie mehrere unabhaengige Fragen stellen statt eine einzige Gewinnzahl zu maximieren"),
                p("""
                Der wichtigste operative Satz lautet: Kein Best-Ordner ohne ernsthafte Validierung.
                Wenn Step-7-Ergebnisse existieren, duerfen nur PASSED-Kandidaten in den Best-Ordner.
                FAILED, NO_TRADES oder ERROR sind keine Kleinigkeit, sondern eine rote Linie. Ein
                NO_TRADES-Ergebnis kann bedeuten, dass das Fenster zu kurz war oder die Strategie
                in dieser Marktphase keine Signale hatte. Auch das ist Information.
                """),
            ],
        ),
        Section(
            "Fazit",
            [
                p("""
                Der Backtester ist mehr als ein Automatisierer fuer MetaTrader. Er ist ein
                methodisches Werkzeug, das die gefaehrlichste Versuchung der Strategieentwicklung
                sichtbar macht: eine schoene Vergangenheit mit einer robusten Zukunft zu verwechseln.
                Seine Staerke liegt in der Kombination aus Prozessautomatisierung, strukturierter
                Persistenz, mehrsaeuligem Scoring, Sensitivitaet, KI-gestuetzter Musteranalyse und
                abschliessender Out-of-Sample-Validierung.
                """),
                p("""
                Fuer Nutzer bedeutet das: Das Tool nimmt Arbeit ab, aber nicht Verantwortung. Man
                muss weiterhin Datenqualitaet, Trade-Anzahl, Drawdown, Marktregime und Plausibilitaet
                beurteilen. Fuer Entwickler bedeutet es: Jede Erweiterung sollte die Trennung von
                Optimierung, Auswahl und Validierung respektieren. Der beste Code in diesem Projekt
                ist nicht der, der den hoechsten Score erzeugt, sondern der, der falsche Sicherheit
                schwerer macht.
                """),
            ],
        ),
    ]


def styles():
    s = getSampleStyleSheet()
    s.add(ParagraphStyle("BookTitle", parent=s["Title"], fontName="Helvetica-Bold", fontSize=28, leading=34, alignment=TA_CENTER, spaceAfter=20, textColor=colors.HexColor("#0f172a")))
    s.add(ParagraphStyle("BookSubTitle", parent=s["Normal"], fontName="Helvetica", fontSize=14, leading=20, alignment=TA_CENTER, textColor=colors.HexColor("#334155")))
    s.add(ParagraphStyle("Chapter", parent=s["Heading1"], fontName="Helvetica-Bold", fontSize=20, leading=25, spaceBefore=10, spaceAfter=14, textColor=colors.HexColor("#1e3a8a")))
    s.add(ParagraphStyle("Section", parent=s["Heading2"], fontName="Helvetica-Bold", fontSize=14, leading=18, spaceBefore=12, spaceAfter=8, textColor=colors.HexColor("#0f172a")))
    s.add(ParagraphStyle("Body", parent=s["BodyText"], fontName="Helvetica", fontSize=10.2, leading=14.2, alignment=TA_LEFT, spaceAfter=8, textColor=colors.HexColor("#1f2937")))
    s.add(ParagraphStyle("Small", parent=s["BodyText"], fontName="Helvetica", fontSize=8.2, leading=10.5, textColor=colors.HexColor("#475569")))
    s.add(ParagraphStyle("Caption", parent=s["BodyText"], fontName="Helvetica-Oblique", fontSize=8.5, leading=11, alignment=TA_CENTER, textColor=colors.HexColor("#475569"), spaceAfter=10))
    s.add(ParagraphStyle("TableCell", parent=s["BodyText"], fontName="Helvetica", fontSize=7.3, leading=9.1, textColor=colors.HexColor("#1f2937")))
    s.add(ParagraphStyle("TableHead", parent=s["BodyText"], fontName="Helvetica-Bold", fontSize=7.5, leading=9.5, textColor=colors.white))
    s.add(ParagraphStyle("BookBullet", parent=s["BodyText"], fontName="Helvetica", fontSize=9.5, leading=13, leftIndent=8, textColor=colors.HexColor("#1f2937")))
    return s


def para(text: str, style):
    safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return Paragraph(safe, style)


def scaled_image(path: Path, max_w: float, max_h: float):
    img = PILImage.open(path)
    w, h = img.size
    scale = min(max_w / w, max_h / h)
    return Image(str(path), width=w * scale, height=h * scale)


def make_table(rows: Sequence[Sequence[object]], col_widths: Sequence[float], st) -> Table:
    data = []
    for r, row in enumerate(rows):
        data.append([para(str(cell), st["TableHead"] if r == 0 else st["TableCell"]) for cell in row])
    t = Table(data, colWidths=col_widths, repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1e3a8a")),
        ("GRID", (0, 0), (-1, -1), 0.25, colors.HexColor("#cbd5e1")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f8fafc")]),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]))
    return t


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#64748b"))
    canvas.drawString(1.8 * cm, 1.0 * cm, "Mastering the Backtester")
    canvas.drawRightString(A4[0] - 1.8 * cm, 1.0 * cm, f"Seite {doc.page}")
    canvas.restoreState()


def write_markdown(snapshot: dict[str, object], diagrams: dict[str, Path], chapters: list[Section]) -> None:
    lines: list[str] = []
    lines.append("# Mastering the Backtester")
    lines.append("")
    lines.append("Ein Leitfaden zur robusten Strategieoptimierung und zur Architektur des Backtester-Projekts")
    lines.append("")
    lines.append(f"Version {VERSION} - {BUILD_DATE}")
    lines.append("")
    lines.append("## Inhaltsverzeichnis")
    for sec in chapters:
        lines.append(f"- {sec.title}")
    lines.append("- Anhang A: Parameter-Referenz")
    lines.append("- Anhang B: Sourcecode-Modulindex")
    lines.append("- Anhang C: Kritische Klassen und Entwicklerleitfaden")
    lines.append("- Anhang D: Betriebschecklisten und Troubleshooting")
    lines.append("- Anhang E: Glossar")
    lines.append("- Anhang F: Quellen und Bildnachweis")
    lines.append("")
    for sec in chapters:
        lines.append(f"## {sec.title}")
        lines.append("")
        for text in sec.paragraphs:
            lines.append(text)
            lines.append("")
        if sec.bullets:
            for b in sec.bullets:
                lines.append(f"- {b}")
            lines.append("")
    lines.append("## Anhang A: Parameter-Referenz")
    lines.append("")
    lines.append("| Parameter | Gruppe | Bedeutung | Typische Werte |")
    lines.append("|---|---|---|---|")
    for row in PARAMETERS:
        lines.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    lines.append("")
    lines.append("## Anhang B: Sourcecode-Modulindex")
    lines.append("")
    lines.append("| Paket | Dateien | Zeilen | Rolle |")
    lines.append("|---|---:|---:|---|")
    for pkg in snapshot["packages"]:
        lines.append(f"| {pkg['package']} | {pkg['files']} | {pkg['lines']} | {PACKAGE_SUMMARY.get(str(pkg['package']), '')} |")
    lines.append("")
    lines.append("## Anhang C: Kritische Klassen und Entwicklerleitfaden")
    lines.append("")
    lines.append("| Klasse | Rolle im Projekt |")
    lines.append("|---|---|")
    for row in KEY_CLASSES:
        lines.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    lines.append("")
    lines.append("### Workflow-Schrittmatrix")
    lines.append("")
    lines.append("| Schritt | Code-Ort | Aufgabe | Kritischer Punkt |")
    lines.append("|---|---|---|---|")
    for row in WORKFLOW_DETAILS:
        lines.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    lines.append("")
    lines.append("### Architekturentscheidungen")
    lines.append("")
    lines.append("| Entscheidung | Umsetzung | Wirkung |")
    lines.append("|---|---|---|")
    for row in ARCHITECTURE_DECISIONS:
        lines.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    lines.append("")
    lines.append("### Datenfluesse")
    lines.append("")
    lines.append("| Fluss | Kette |")
    lines.append("|---|---|")
    for row in DATA_FLOWS:
        lines.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    lines.append("")
    lines.append("## Anhang D: Betriebschecklisten und Troubleshooting")
    lines.append("")
    lines.append("| Phase | Pruefung | Warum wichtig |")
    lines.append("|---|---|---|")
    for row in CHECKLISTS:
        lines.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    lines.append("")
    lines.append("| Symptom | Diagnose / Loesung |")
    lines.append("|---|---|")
    for row in TROUBLESHOOTING:
        lines.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    lines.append("")
    lines.append("## Anhang E: Glossar")
    lines.append("")
    lines.append("| Begriff | Bedeutung |")
    lines.append("|---|---|")
    for row in GLOSSARY:
        lines.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    lines.append("")
    lines.append("## Anhang F: Quellen und Bildnachweis")
    lines.append("")
    for name, title, url in SOURCES:
        lines.append(f"- {name}: {title}. {url}")
    lines.append("")
    lines.append("### Bilder")
    for pth in diagrams.values():
        lines.append(f"- Generierte Grafik: {pth.name}")
    for img in image_plan():
        lines.append(f"- Projektscreenshot: {img[0]}")
    MD_PATH.write_text("\n".join(lines), encoding="utf-8")


def image_plan() -> list[tuple[str, str]]:
    return [
        ("images/backtester_platform.png", "Abbildung: Gesamtplattform des Backtesters."),
        ("images/backtester_optimizer.png", "Abbildung: Optimizer-Arbeitsbereich."),
        ("images/backtester_score_weighting.png", "Abbildung: Score-Gewichtung und Ranking."),
        ("images/backtester_sensitivity.png", "Abbildung: Sensitivitaetsanalyse und Robustheitskurven."),
        ("images/backtester_ki_analysis.png", "Abbildung: KI-Analyse der Strategie-Stabilitaet."),
        ("images/backtester_ki_evaluation_table.png", "Abbildung: KI-Bewertungstabelle."),
        ("images/backtester_best_strategies.png", "Abbildung: Auswahl und Export bester Strategien."),
        ("images/multi-backtester-config.png", "Abbildung: Multi-Backtester-Konfiguration."),
        ("images/multi-backtester-results.png", "Abbildung: Multi-Backtester-Ergebnisse."),
        ("images/backtester_strategy_detail_analysis.png", "Abbildung: Strategie-Detailanalyse."),
    ]


def build_pdf(snapshot: dict[str, object], diagrams: dict[str, Path], chapters: list[Section]) -> None:
    st = styles()
    doc = SimpleDocTemplate(
        str(PDF_PATH),
        pagesize=A4,
        rightMargin=1.6 * cm,
        leftMargin=1.8 * cm,
        topMargin=1.7 * cm,
        bottomMargin=1.6 * cm,
        title="Mastering the Backtester",
        author="Codex",
    )
    story: list = []
    story.append(Spacer(1, 4.0 * cm))
    story.append(para("Mastering the Backtester", st["BookTitle"]))
    story.append(para("Ein Leitfaden zur robusten Strategieoptimierung und zur Architektur des Backtester-Projekts", st["BookSubTitle"]))
    story.append(Spacer(1, 0.6 * cm))
    story.append(para(f"Version {VERSION} - {BUILD_DATE}", st["BookSubTitle"]))
    story.append(Spacer(1, 1.2 * cm))
    story.append(para("Benutzerhandbuch, Lehrbuch und technische Projektdokumentation", st["BookSubTitle"]))
    story.append(PageBreak())

    story.append(para("Inhaltsverzeichnis", st["Chapter"]))
    toc_rows = [["Teil", "Inhalt"]]
    for i, sec in enumerate(chapters, 1):
        toc_rows.append([str(i), sec.title])
    toc_rows.extend([
        ["A", "Parameter-Referenz"],
        ["B", "Sourcecode-Modulindex"],
        ["C", "Kritische Klassen und Entwicklerleitfaden"],
        ["D", "Betriebschecklisten und Troubleshooting"],
        ["E", "Glossar"],
        ["F", "Screenshot-Galerie, Quellen und Bildnachweis"],
    ])
    story.append(make_table(toc_rows, [1.2 * cm, 14.8 * cm], st))
    story.append(PageBreak())

    diagram_order = [
        ("curve_fitting", "Curve Fitting zeigt den Unterschied zwischen robustem Signal und historischer Ueberanpassung."),
        ("architecture", "Architekturuebersicht des Backtester-Projekts."),
        ("workflow", "Die sieben Schritte der Anti-Curvefitting-Pipeline."),
        ("mt5_process", "Vom Java-Klick zum MetaTrader-Report."),
        ("database", "SQLite als Gedaechtnis der Anwendung."),
        ("scorecard", "Scorecard-Modell mit realen Kennzahlen."),
        ("oos_gate", "Step 7 als echte nachgelagerte Out-of-Sample-Validierung."),
    ]
    diagram_by_chapter = {
        "Kapitel 2": diagram_order[0],
        "Kapitel 3": diagram_order[1],
        "Kapitel 5": diagram_order[3],
        "Kapitel 6": diagram_order[4],
        "Kapitel 7": diagram_order[5],
        "Kapitel 10": diagram_order[6],
    }
    screenshot_iter = iter(image_plan())

    for sec in chapters:
        story.append(para(sec.title, st["Chapter"]))
        key = next((k for k in diagram_by_chapter if sec.title.startswith(k)), None)
        if key:
            name, caption = diagram_by_chapter[key]
            story.append(scaled_image(diagrams[name], 16.2 * cm, 8.5 * cm))
            story.append(para(caption, st["Caption"]))
        for idx, text in enumerate(sec.paragraphs):
            story.append(para(text, st["Body"]))
            if idx == 2 and sec.title.startswith(("Kapitel 4", "Kapitel 8", "Kapitel 9")):
                try:
                    img_rel, cap = next(screenshot_iter)
                    img_path = ROOT / img_rel
                    if img_path.exists():
                        story.append(scaled_image(img_path, 15.8 * cm, 8.4 * cm))
                        story.append(para(cap, st["Caption"]))
                except StopIteration:
                    pass
        if sec.bullets:
            items = [ListItem(para(b, st["BookBullet"])) for b in sec.bullets]
            story.append(ListFlowable(items, bulletType="bullet", leftIndent=16))
        story.append(Spacer(1, 0.3 * cm))
        if sec.title.startswith("Kapitel"):
            story.append(PageBreak())

    story.append(PageBreak())
    story.append(para("Anhang A: Parameter-Referenz", st["Chapter"]))
    story.append(para("Die folgende Tabelle fasst die wichtigsten Projektparameter zusammen. Sie ist bewusst breit angelegt: Nutzer erkennen die Wirkung im Tool, Entwickler erkennen die betroffenen Konfigurationsgruppen.", st["Body"]))
    rows = [["Parameter", "Gruppe", "Bedeutung", "Typische Werte"]] + PARAMETERS
    story.append(make_table(rows, [3.2 * cm, 2.3 * cm, 8.5 * cm, 2.4 * cm], st))

    story.append(PageBreak())
    story.append(para("Anhang B: Sourcecode-Modulindex", st["Chapter"]))
    story.append(para(f"Die Hauptanwendung umfasst {snapshot['main_java_files']} Java-Dateien in {len(snapshot['packages'])} Paketen. Die Tabelle zeigt die aus dem Repository abgeleitete Struktur.", st["Body"]))
    package_rows = [["Paket", "Dateien", "Zeilen", "Rolle"]]
    for pkg in snapshot["packages"]:
        package_rows.append([pkg["package"], pkg["files"], pkg["lines"], PACKAGE_SUMMARY.get(str(pkg["package"]), "Projektmodul")])
    story.append(make_table(package_rows, [4.3 * cm, 1.5 * cm, 1.8 * cm, 8.6 * cm], st))
    story.append(Spacer(1, 0.5 * cm))
    story.append(para("Klassenuebersicht", st["Section"]))
    class_rows = [["Paket", "Klasse", "Zeilen"]]
    for pkg, cls, lines in snapshot["classes"]:
        class_rows.append([pkg, cls, lines])
    story.append(make_table(class_rows, [6.5 * cm, 6.8 * cm, 2.0 * cm], st))

    story.append(PageBreak())
    story.append(para("Test- und Qualitaetsindex", st["Chapter"]))
    story.append(para("Die Tests sind keine vollstaendige formale Spezifikation, aber sie zeigen, welche Verhaltensweisen als schuetzenswert betrachtet werden: Forward-Split, Workflow-Gates, Scorecard, Persistenz, Parser, Dukascopy und UI-Komponenten.", st["Body"]))
    test_rows = [["Testdatei", "Zeilen"]]
    for path, lines in snapshot["tests"]:
        test_rows.append([path, lines])
    story.append(make_table(test_rows, [13.5 * cm, 2.0 * cm], st))

    story.append(PageBreak())
    story.append(para("Anhang C: Kritische Klassen und Entwicklerleitfaden", st["Chapter"]))
    story.append(para("Die folgenden Klassen sind die wichtigsten Orientierungspunkte fuer Entwickler. Sie sind nicht alle gleich gross, aber sie tragen die fachlichen Grenzen des Systems: Prozessstart, Parameterwahrheit, Score, Persistenz, Validierung und Export.", st["Body"]))
    key_rows = [["Klasse / Datei", "Rolle im Projekt"]] + KEY_CLASSES
    story.append(make_table(key_rows, [4.8 * cm, 11.0 * cm], st))
    story.append(PageBreak())
    story.append(para("Workflow-Schrittmatrix", st["Chapter"]))
    story.append(para("Die Matrix verbindet Benutzeraktion, Code-Ort und fachlichen Kontrollpunkt. Genau diese Verbindung macht das Buch zu einer Projektdokumentation und nicht nur zu einer Bedienungsanleitung.", st["Body"]))
    workflow_rows = [["Schritt", "Code-Ort", "Aufgabe", "Kritischer Punkt"]] + WORKFLOW_DETAILS
    story.append(make_table(workflow_rows, [2.4 * cm, 4.1 * cm, 5.0 * cm, 4.3 * cm], st))
    story.append(PageBreak())
    story.append(para("Architekturentscheidungen", st["Chapter"]))
    story.append(para("Die folgenden Entscheidungen sind im Sourcecode sichtbar und sollten bei Erweiterungen respektiert werden. Sie beschreiben nicht nur, wie das Projekt gebaut ist, sondern warum bestimmte Grenzen existieren.", st["Body"]))
    decision_rows = [["Entscheidung", "Umsetzung", "Wirkung"]] + ARCHITECTURE_DECISIONS
    story.append(make_table(decision_rows, [4.4 * cm, 5.5 * cm, 5.8 * cm], st))
    story.append(Spacer(1, 0.5 * cm))
    story.append(para("Datenfluesse", st["Section"]))
    flow_rows = [["Fluss", "Kette"]] + DATA_FLOWS
    story.append(make_table(flow_rows, [3.0 * cm, 12.6 * cm], st))
    story.append(PageBreak())
    story.append(para("Entwicklerleitfaden: Erweiterungen ohne Methodikbruch", st["Chapter"]))
    dev_paras = [
        "Neue Funktionen sollten zuerst entscheiden, welche Schicht betroffen ist: UI, Engine, Report, Persistenz oder Datenimport. Ein neues UI-Element darf keine fachliche Regel umgehen, die bereits in WorkflowEngine, ScoreWeights oder ValidationResult kodiert ist.",
        "Neue Score-Komponenten muessen auf real gemessenen Daten beruhen. Historisch wurden synthetische Saeulen entfernt, weil sie ein Gefuehl von Genauigkeit erzeugen konnten, ohne echte MetaTrader-Messwerte zu nutzen.",
        "Neue Datenbankfelder brauchen defensive Migrationen. DatabaseManager wird bei bestehenden Nutzern laufen, deren history.db bereits alte Tabellen besitzt. Migrationen muessen fehlende Spalten erkennen und alte Daten erhalten.",
        "Neue Runner sollten Seiteneffekte kapseln. Prozessstart, Dateikopien, Timeouts und Logtailing gehoeren in die Engine. UI-Klassen sollen den Start ausloesen und Status anzeigen, aber nicht selbst MetaTrader-Prozessdetails duplizieren.",
        "Neue KI-Funktionen muessen zwischen Analyse und Entscheidung unterscheiden. Ein LLM kann Kennlinien erklaeren, aber eine PASSED-Validierung in Step 7 darf nicht durch einen sprachlichen Score ersetzt werden.",
        "Neue Exportwege muessen die gleiche Gate-Logik respektieren wie exportPortfolio: Wenn Validierungsergebnisse existieren, sind nur PASSED-Kandidaten fuer den Best-Ordner geeignet.",
        "Neue Tests sollten die fachliche Grenze abdecken, nicht nur die Codezeile. Besonders wichtig sind ForwardSplit, WorkflowValidationAndGateTest, Scorecard-Tests, Parser-Tests und Persistenztests.",
        "Bei Refactorings der UI ist darauf zu achten, dass JavaFX die primaere Oberflaeche ist, Swing aber weiterhin relevante Bedien- und Dokumentationsspuren besitzt. Entfernen ohne Migrationsplan wuerde Nutzerpfade brechen.",
    ]
    for text in dev_paras:
        story.append(para(text, st["Body"]))

    story.append(PageBreak())
    story.append(para("Anhang D: Betriebschecklisten und Troubleshooting", st["Chapter"]))
    story.append(para("Diese Checklisten sind als Arbeitsblatt gedacht. Sie helfen, eine Strategie nicht zu frueh als robust einzustufen und typische Betriebsprobleme schneller zu diagnostizieren.", st["Body"]))
    check_rows = [["Phase", "Pruefung", "Warum wichtig"]] + CHECKLISTS
    story.append(make_table(check_rows, [3.0 * cm, 4.5 * cm, 8.2 * cm], st))
    story.append(PageBreak())
    story.append(para("Troubleshooting", st["Chapter"]))
    trouble_rows = [["Symptom", "Diagnose / Loesung"]] + TROUBLESHOOTING
    story.append(make_table(trouble_rows, [5.2 * cm, 10.5 * cm], st))

    story.append(PageBreak())
    story.append(para("Anhang E: Glossar", st["Chapter"]))
    story.append(para("Das Glossar uebersetzt zentrale Begriffe aus Trading, Statistik, MetaTrader und Projektarchitektur in kurze Arbeitsdefinitionen.", st["Body"]))
    glossary_rows = [["Begriff", "Bedeutung"]] + GLOSSARY
    story.append(make_table(glossary_rows, [4.2 * cm, 11.5 * cm], st))

    story.append(PageBreak())
    story.append(para("Anhang F: Screenshot-Galerie", st["Chapter"]))
    story.append(para("Die Galerie zeigt zuerst die fuer dieses Buch erzeugten Erklaergrafiken und danach die wichtigsten vorhandenen Projektbilder. Sie lockert das Buch auf und dient zugleich als visueller Index der Architektur und Benutzeroberflaeche.", st["Body"]))
    generated_gallery = [
        ("architecture", "Grafikindex: Architekturuebersicht des Backtester-Projekts."),
        ("workflow", "Grafikindex: 7-Schritt Anti-Curvefitting Workflow."),
        ("mt5_process", "Grafikindex: MT5-Prozessablauf."),
        ("database", "Grafikindex: Persistenzmodell der Anwendung."),
        ("scorecard", "Grafikindex: Scorecard-Modell."),
        ("oos_gate", "Grafikindex: Step-7-OOS-Gate."),
        ("curve_fitting", "Grafikindex: Curve-Fitting-Erklaergrafik."),
    ]
    for name, cap in generated_gallery:
        story.append(KeepTogether([
            scaled_image(diagrams[name], 16.0 * cm, 10.2 * cm),
            para(cap, st["Caption"]),
        ]))
        story.append(PageBreak())
    for rel, cap in image_plan():
        img_path = ROOT / rel
        if img_path.exists():
            story.append(KeepTogether([
                scaled_image(img_path, 15.8 * cm, 9.4 * cm),
                para(cap, st["Caption"]),
            ]))
            story.append(PageBreak())

    story.append(PageBreak())
    story.append(para("Quellen und Bildnachweis", st["Chapter"]))
    story.append(para("Webquellen wurden fuer die Lehrbuchteile zu Backtesting, Overfitting, Walk-Forward-Analyse und Out-of-Sample-Validierung herangezogen. Projektaussagen wurden aus dem lokalen Repository abgeleitet.", st["Body"]))
    source_rows = [["Quelle", "Titel", "URL"]] + SOURCES
    story.append(make_table(source_rows, [2.7 * cm, 6.3 * cm, 7.0 * cm], st))
    story.append(Spacer(1, 0.5 * cm))
    story.append(para("Bildnachweis", st["Section"]))
    image_rows = [["Bild", "Herkunft"]]
    for name, path in diagrams.items():
        image_rows.append([path.name, "Fuer dieses Buch generierte Erklaergrafik"])
    for rel, cap in image_plan():
        image_rows.append([rel, cap])
    story.append(make_table(image_rows, [6.0 * cm, 9.8 * cm], st))

    doc.build(story, onFirstPage=footer, onLaterPages=footer)


def main() -> None:
    os.chdir(ROOT)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    snapshot = repo_snapshot()
    diagrams = make_diagrams()
    chapters = build_chapters(snapshot)
    write_markdown(snapshot, diagrams, chapters)
    build_pdf(snapshot, diagrams, chapters)
    print(f"Wrote {MD_PATH}")
    print(f"Wrote {PDF_PATH}")


if __name__ == "__main__":
    main()
