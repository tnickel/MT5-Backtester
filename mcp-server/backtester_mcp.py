"""
MT5 Backtester MCP Server
==========================
Exposes the backtester's SQLite database to Claude via MCP.
Claude can then analyze optimization results, sensitivity data,
and strategy robustness using natural language.

Database location: %USERPROFILE%\\.mt5_backtester\\history.db
"""

import json
import os
import sqlite3
import time
from pathlib import Path
from typing import Any

from mcp.server.fastmcp import FastMCP

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------

DB_PATH = Path.home() / ".mt5_backtester" / "history.db"

# query_database is deliberately narrower than the fixed tools above. These are the
# only tables documented for arbitrary MCP SQL and therefore the only ones its
# SQLite authorizer permits.
QUERY_ALLOWED_TABLES = frozenset({
    "SENSITIVITY_DETAIL",
    "HISTORY_RUNS",
    "OPTIMIZATION_STATE",
    "EA_SAVED_CONFIGS",
})
QUERY_BLOCKED_FUNCTIONS = frozenset({
    "eval",
    "fts3_tokenizer",
    "load_extension",
    "readfile",
    "writefile",
})
QUERY_MAX_ROWS = 1000
QUERY_MAX_RESPONSE_BYTES = 1_000_000
QUERY_MAX_SQL_BYTES = 100_000
QUERY_PROGRESS_INTERVAL = 1000
QUERY_MAX_VM_STEPS = 500_000
QUERY_MAX_SECONDS = 2.0

mcp = FastMCP(
    "MT5 Backtester",
    description="Zugriff auf MT5 Backtester Datenbank: Optimierungsergebnisse, Sensitivitätsanalyse, Strategiebewertung"
)


def get_db() -> sqlite3.Connection:
    """Open a read-only connection to the backtester database."""
    if not DB_PATH.exists():
        raise FileNotFoundError(
            f"Backtester-Datenbank nicht gefunden: {DB_PATH}\n"
            "Bitte starte zuerst den MT5 Backtester, damit die DB erstellt wird."
        )
    conn = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    return conn


def rows_to_dicts(rows: list[sqlite3.Row]) -> list[dict]:
    """Convert sqlite3.Row objects to plain dicts for JSON serialization."""
    return [dict(row) for row in rows]


def _query_authorizer(action: int, arg1: str | None, arg2: str | None,
                      database: str | None, source: str | None) -> int:
    """SQLite-level policy for arbitrary MCP SQL, applied after SQL parsing."""
    if action == sqlite3.SQLITE_READ:
        table = (arg1 or "").upper()
        return sqlite3.SQLITE_OK if table in QUERY_ALLOWED_TABLES else sqlite3.SQLITE_DENY
    if action == sqlite3.SQLITE_FUNCTION:
        function = (arg2 or arg1 or "").lower()
        return sqlite3.SQLITE_DENY if function in QUERY_BLOCKED_FUNCTIONS else sqlite3.SQLITE_OK
    if action in (sqlite3.SQLITE_SELECT, sqlite3.SQLITE_RECURSIVE):
        return sqlite3.SQLITE_OK
    # Deny writes, transactions, schema changes, ATTACH/DETACH, PRAGMA and every
    # future action that was not explicitly required for a SELECT.
    return sqlite3.SQLITE_DENY


def _guard_query_connection(conn: sqlite3.Connection) -> None:
    """Install parser-level authorization and bounded execution resources."""
    conn.set_authorizer(_query_authorizer)
    started = time.monotonic()
    executed_steps = 0

    def stop_expensive_query() -> int:
        nonlocal executed_steps
        executed_steps += QUERY_PROGRESS_INTERVAL
        return int(executed_steps > QUERY_MAX_VM_STEPS
                   or time.monotonic() - started > QUERY_MAX_SECONDS)

    conn.set_progress_handler(stop_expensive_query, QUERY_PROGRESS_INTERVAL)
    if hasattr(conn, "setlimit"):
        conn.setlimit(sqlite3.SQLITE_LIMIT_LENGTH, QUERY_MAX_RESPONSE_BYTES)
        conn.setlimit(sqlite3.SQLITE_LIMIT_SQL_LENGTH, QUERY_MAX_SQL_BYTES)


# ---------------------------------------------------------------------------
# Tools
# ---------------------------------------------------------------------------

@mcp.tool()
def get_sensitivity_overview() -> str:
    """
    Gibt eine Übersicht aller Sensitivitätsanalyse-Ergebnisse zurück.
    Zeigt für jeden Pass die Parameter mit ihren CV-Werten und Verdicts.
    Ideal als Einstiegspunkt für die Strategiebewertung.
    """
    conn = get_db()
    try:
        rows = conn.execute("""
            SELECT pass_number, pass_name, expert_name, symbol,
                   parameter_name, period, cv, verdict, 
                   base_profit, mean_profit, stddev,
                   min_profit, max_profit, num_variants, base_value
            FROM SENSITIVITY_DETAIL
            ORDER BY pass_number, period, cv DESC
        """).fetchall()
        
        if not rows:
            return "Keine Sensitivitätsanalyse-Daten vorhanden. Bitte zuerst eine Analyse im Backtester durchführen."
        
        return json.dumps(rows_to_dicts(rows), indent=2, ensure_ascii=False)
    finally:
        conn.close()


@mcp.tool()
def get_sensitivity_for_pass(pass_number: int) -> str:
    """
    Gibt die detaillierte Sensitivitätsanalyse für einen bestimmten Pass zurück.
    Enthält alle Parameter-Sweeps mit Kurvendaten, CV-Werten und Verdicts.
    
    Args:
        pass_number: Die Pass-Nummer aus der Optimierung (z.B. 15545)
    """
    conn = get_db()
    try:
        rows = conn.execute("""
            SELECT parameter_name, period, cv, verdict,
                   base_value, base_profit, mean_profit, stddev,
                   min_profit, max_profit, num_variants,
                   sweep_start, sweep_step, sweep_end,
                   curve_json
            FROM SENSITIVITY_DETAIL
            WHERE pass_number = ?
            ORDER BY period, cv DESC
        """, (pass_number,)).fetchall()
        
        if not rows:
            return f"Keine Sensitivitätsdaten für Pass {pass_number} gefunden."
        
        return json.dumps(rows_to_dicts(rows), indent=2, ensure_ascii=False)
    finally:
        conn.close()


@mcp.tool()
def get_fragile_parameters(min_cv: float = 50.0) -> str:
    """
    Findet alle fragilen Parameter über alle analysierten Strategien.
    Zeigt Parameter, die über dem angegebenen CV-Schwellenwert liegen.
    
    Args:
        min_cv: Minimaler CV-Wert in Prozent (Standard: 50.0). 
                Parameter mit CV >= min_cv werden als fragil eingestuft.
    """
    conn = get_db()
    try:
        rows = conn.execute("""
            SELECT pass_number, pass_name, parameter_name, period,
                   cv, verdict, base_value, base_profit, mean_profit, stddev,
                   min_profit, max_profit
            FROM SENSITIVITY_DETAIL
            WHERE cv >= ?
            ORDER BY cv DESC
        """, (min_cv,)).fetchall()
        
        if not rows:
            return f"Keine Parameter mit CV >= {min_cv}% gefunden. Alle Parameter sind stabil!"
        
        return json.dumps(rows_to_dicts(rows), indent=2, ensure_ascii=False)
    finally:
        conn.close()


@mcp.tool()
def get_robust_strategies() -> str:
    """
    Rankt alle analysierten Strategien nach ihrer Gesamtrobustheit.
    Berechnet den durchschnittlichen CV und zählt fragile Parameter pro Pass.
    Die beste Strategie hat den niedrigsten avg_cv und die wenigsten fragilen Parameter.
    """
    conn = get_db()
    try:
        rows = conn.execute("""
            SELECT 
                pass_number, 
                pass_name,
                expert_name,
                symbol,
                ROUND(AVG(cv), 2) as avg_cv,
                ROUND(MAX(cv), 2) as worst_cv,
                COUNT(*) as total_params,
                SUM(CASE WHEN verdict = 'ROBUST' THEN 1 ELSE 0 END) as robust_count,
                SUM(CASE WHEN verdict = 'ACCEPTABLE' THEN 1 ELSE 0 END) as acceptable_count,
                SUM(CASE WHEN verdict = 'FRAGILE' THEN 1 ELSE 0 END) as fragile_count,
                ROUND(AVG(CASE WHEN period = 'BT' THEN cv END), 2) as avg_cv_bt,
                ROUND(AVG(CASE WHEN period = 'FW' THEN cv END), 2) as avg_cv_fw
            FROM SENSITIVITY_DETAIL
            GROUP BY pass_number, pass_name, expert_name, symbol
            ORDER BY avg_cv ASC
        """).fetchall()
        
        if not rows:
            return "Keine Sensitivitätsdaten vorhanden."
        
        return json.dumps(rows_to_dicts(rows), indent=2, ensure_ascii=False)
    finally:
        conn.close()


@mcp.tool()
def get_parameter_curve(pass_number: int, parameter_name: str, period: str = "BT") -> str:
    """
    Gibt die Profit-Kurve für einen bestimmten Parameter zurück.
    Zeigt, wie sich der Profit ändert wenn der Parameterwert variiert wird.
    Ideal zur Identifikation von Klippen, Plateaus oder Peaks.
    
    Args:
        pass_number: Die Pass-Nummer
        parameter_name: Name des Parameters (z.B. 'factorTP', 'maxOpenTrades')
        period: 'BT' für Backtest oder 'FW' für Forward-Test
    """
    conn = get_db()
    try:
        row = conn.execute("""
            SELECT parameter_name, period, cv, verdict,
                   base_value, base_profit, mean_profit, stddev,
                   min_profit, max_profit, num_variants,
                   sweep_start, sweep_step, sweep_end,
                   curve_json
            FROM SENSITIVITY_DETAIL
            WHERE pass_number = ? AND parameter_name = ? AND period = ?
        """, (pass_number, parameter_name, period)).fetchone()
        
        if not row:
            return f"Keine Daten für Pass {pass_number}, Parameter '{parameter_name}', Period '{period}' gefunden."
        
        return json.dumps(dict(row), indent=2, ensure_ascii=False)
    finally:
        conn.close()


@mcp.tool()
def get_optimization_history(limit: int = 20) -> str:
    """
    Gibt die letzten Optimierungs- und Backtest-Läufe zurück.
    
    Args:
        limit: Maximale Anzahl der Einträge (Standard: 20)
    """
    conn = get_db()
    try:
        rows = conn.execute("""
            SELECT id, run_type, expert_name, timestamp, html_path
            FROM HISTORY_RUNS
            ORDER BY timestamp DESC
            LIMIT ?
        """, (limit,)).fetchall()
        
        if not rows:
            return "Keine History-Einträge vorhanden."
        
        results = []
        for row in rows:
            d = dict(row)
            # Convert timestamp to readable date
            import datetime
            d['date'] = datetime.datetime.fromtimestamp(
                d['timestamp'] / 1000
            ).strftime('%Y-%m-%d %H:%M:%S')
            results.append(d)
        
        return json.dumps(results, indent=2, ensure_ascii=False)
    finally:
        conn.close()


@mcp.tool()
def query_database(sql_query: str) -> str:
    """
    Führt eine beliebige SQL-Abfrage (nur SELECT) auf der Backtester-Datenbank aus.
    
    Verfügbare Tabellen:
    - SENSITIVITY_DETAIL: Normalisierte Sensitivitätsdaten (ein Row pro Parameter/Periode)
      Spalten: pass_number, pass_name, expert_name, symbol, parameter_name, base_value,
               period (BT/FW), base_profit, mean_profit, stddev, cv, min_profit, max_profit,
               num_variants, sweep_start, sweep_step, sweep_end, curve_json, verdict
    - HISTORY_RUNS: Alle bisherigen Backtest/Optimierungs-Läufe
      Spalten: id, run_type, expert_name, timestamp, result_json, html_path
    - OPTIMIZATION_STATE: Aktueller Zustand der Optimierung (JSON-Blobs)
    - EA_SAVED_CONFIGS: Gespeicherte EA-Konfigurationen

    Nicht verfügbar (Secrets): APP_SETTINGS (enthält API-Keys).
    
    Args:
        sql_query: SQL SELECT-Abfrage. Nur lesende Zugriffe erlaubt.
    """
    if not isinstance(sql_query, str) or len(sql_query.encode("utf-8")) > QUERY_MAX_SQL_BYTES:
        return f"Fehler: SQL-Abfrage ist zu groß (maximal {QUERY_MAX_SQL_BYTES} Bytes)."

    # Fast rejection for non-queries and an explicit, user-friendly secret error.
    # The SQLite authorizer below is the actual security boundary and also catches
    # aliases/views or future syntax that a text filter cannot understand.
    normalized = sql_query.strip().upper()
    if not normalized.startswith("SELECT"):
        return "Fehler: Nur SELECT-Abfragen sind erlaubt. Keine Schreibzugriffe möglich."

    # Block secret tables / key names even for read-only SELECT
    blocked_markers = (
        "APP_SETTINGS",
        "OPENROUTER_API_KEY",
        "OPENROUTER API KEY",
    )
    for marker in blocked_markers:
        if marker in normalized:
            return (
                "Fehler: Abfragen auf APP_SETTINGS / API-Keys sind aus Sicherheitsgründen "
                "gesperrt. Secrets werden nicht über MCP exponiert."
            )
    
    conn = get_db()
    try:
        _guard_query_connection(conn)
        cursor = conn.execute(sql_query)
        rows = cursor.fetchmany(QUERY_MAX_ROWS + 1)
        if len(rows) > QUERY_MAX_ROWS:
            return (
                f"Fehler: Abfrage liefert mehr als {QUERY_MAX_ROWS} Zeilen. "
                "Bitte WHERE/LIMIT verwenden."
            )
        if not rows:
            return "Abfrage lieferte keine Ergebnisse."

        response = json.dumps(rows_to_dicts(rows), indent=2, ensure_ascii=False)
        if len(response.encode("utf-8")) > QUERY_MAX_RESPONSE_BYTES:
            return (
                f"Fehler: Abfrageergebnis ist größer als {QUERY_MAX_RESPONSE_BYTES} Bytes. "
                "Bitte weniger Zeilen oder Spalten auswählen."
            )
        return response
    except sqlite3.Error as e:
        error = str(e).lower()
        if "interrupted" in error:
            return "SQL-Fehler: Abfrage wegen Zeit-/Rechenlimit abgebrochen."
        if "not authorized" in error or "prohibited" in error:
            return "SQL-Fehler: Abfrage greift auf nicht freigegebene Daten oder Funktionen zu."
        return f"SQL-Fehler: {e}"
    finally:
        conn.close()


@mcp.tool()
def get_database_schema() -> str:
    """
    Zeigt das Datenbankschema aller Tabellen an.
    Hilfreich um zu verstehen welche Daten verfügbar sind.
    """
    conn = get_db()
    try:
        tables = conn.execute("""
            SELECT name, sql FROM sqlite_master 
            WHERE type='table' AND name NOT LIKE 'sqlite_%'
              AND UPPER(name) <> 'APP_SETTINGS'
            ORDER BY name
        """).fetchall()
        
        result = []
        for table in tables:
            info = {"table": table["name"], "create_sql": table["sql"]}
            
            # Get row count
            count = conn.execute(f"SELECT COUNT(*) as cnt FROM [{table['name']}]").fetchone()
            info["row_count"] = count["cnt"]
            
            result.append(info)
        
        return json.dumps(result, indent=2, ensure_ascii=False)
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print(f"MT5 Backtester MCP Server")
    print(f"Database: {DB_PATH}")
    print(f"Database exists: {DB_PATH.exists()}")
    mcp.run()
