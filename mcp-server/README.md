# MT5 Backtester MCP Server
# ===========================

## Setup

Der MCP-Server ist bereits in der Claude Desktop Konfiguration eingetragen.
Nach einem **Neustart von Claude Desktop** erscheint der Server "mt5backtester" 
in der Tool-Liste.

## Verfügbare Tools

| Tool | Beschreibung |
|------|-------------|
| `get_sensitivity_overview` | Übersicht aller Sensitivitätsanalyse-Ergebnisse |
| `get_sensitivity_for_pass` | Detailanalyse für einen bestimmten Pass |
| `get_fragile_parameters` | Alle fragilen Parameter finden |
| `get_robust_strategies` | Strategien nach Robustheit ranken |
| `get_parameter_curve` | Profit-Kurve eines Parameters |
| `get_optimization_history` | Letzte Optimierungsläufe |
| `query_database` | Beliebige SQL SELECT-Abfrage |
| `get_database_schema` | Datenbankschema anzeigen |

## Beispiel-Prompt für Claude

```
Ich habe eine Sensitivitätsanalyse für mehrere Trading-Strategien durchgeführt.
Bitte analysiere die Ergebnisse aus meiner Backtester-Datenbank:

1. Zeige mir eine Übersicht aller analysierten Strategien und ranke sie nach Robustheit
2. Für die beste Strategie: Analysiere jeden Parameter einzeln und erkläre,
   welche Parameter stabil sind und welche fragil
3. Schaue dir die Profit-Kurven der fragilen Parameter an und erkläre,
   ob es sich um eine "Klippe" (plötzlicher Einbruch) oder ein "Plateau" 
   (langsamer Rückgang) handelt
4. Gib mir eine Gesamtbewertung: Kann ich die Strategie im Live-Handel einsetzen?
   Wenn ja, worauf muss ich achten? Wenn nein, warum nicht?

Erkläre alles auf Deutsch und so, dass jemand ohne Statistik-Hintergrund es versteht.
```

## Datenbank-Pfad

`%USERPROFILE%\.mt5_backtester\history.db` (SQLite)
