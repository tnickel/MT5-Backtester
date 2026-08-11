package com.backtester.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTML/CSS/Vue markup builders for the Robustness Scorecard page.
 * Extracted from {@link RobustnessScorecardGenerator} (presentation only).
 */
final class RobustnessScorecardHtmlSections {
    private static final Logger log = LoggerFactory.getLogger(RobustnessScorecardHtmlSections.class);
    private static final String VUE_JS_CONTENT = loadVueJs();

    private RobustnessScorecardHtmlSections() {}

    /** Assembles the full self-contained scorecard HTML page. */
    static String buildHtml(String strategyJson, String statsJson) {
        return headOpen()
                + styles()
                + headClose()
                + bodyMarkup()
                + vueAppScript(strategyJson, statsJson);
    }

    /** DOCTYPE, console bridge, title, embedded Vue runtime. */
    static String headOpen() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\" data-theme=\"dark\">\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<script>\n" +
                "(function() {\n" +
                "    function bindBridge() {\n" +
                "        if (window.consoleBridge) {\n" +
                "            console.log = function(msg) { window.consoleBridge.log(String(msg)); };\n" +
                "            console.error = function(msg) { window.consoleBridge.error(String(msg)); };\n" +
                "            console.warn = function(msg) { window.consoleBridge.log('[WARN] ' + String(msg)); };\n" +
                "        }\n" +
                "    }\n" +
                "    bindBridge();\n" +
                "    window.addEventListener('DOMContentLoaded', bindBridge);\n" +
                "    window.onload = bindBridge;\n" +
                "    window.onerror = function(msg, url, line) {\n" +
                "        if (window.consoleBridge) {\n" +
                "            window.consoleBridge.error('JS EXCEPTION: ' + msg + ' at ' + url + ':' + line);\n" +
                "        } else {\n" +
                "            alert('JS EXCEPTION: ' + msg + ' at ' + url + ':' + line);\n" +
                "        }\n" +
                "        return false;\n" +
                "    };\n" +
                "})();\n" +
                "</script>\n" +
                "<title>Robustness Scorecard</title>\n" +
                "<script>\n" + VUE_JS_CONTENT + "\n</script>\n";
    }

    /** Theme CSS for light/dark scorecard UI. */
    static String styles() {
        return "<style>\n" +
                ":root {\n" +
                "  --bg-body: #ffffff;\n" +
                "  --bg-surface: #f6f7f9;\n" +
                "  --bg-surface-2: #eceff3;\n" +
                "  --text-main: #1f2430;\n" +
                "  --text-muted: #6b7280;\n" +
                "  --border-color: #e1e4e8;\n" +
                "  --primary: #337ab7;\n" +
                "  --good: #2ca25f;\n" +
                "  --ok:   #d4a017;\n" +
                "  --bad:  #d9534f;\n" +
                "  --bar-track: #e5e7eb;\n" +
                "}\n" +
                "[data-theme=\"dark\"] {\n" +
                "  --bg-body: #0d0f17;\n" +
                "  --bg-surface: #171b26;\n" +
                "  --bg-surface-2: #1e2332;\n" +
                "  --text-main: #e2e8f0;\n" +
                "  --text-muted: #7e889a;\n" +
                "  --border-color: #2e3543;\n" +
                "  --primary: #00e5ff;\n" +
                "  --good: #00e676;\n" +
                "  --ok:   #ffd740;\n" +
                "  --bad:  #ff5252;\n" +
                "  --bar-track: #2a2d3a;\n" +
                "}\n" +
                "\n" +
                "*, *::before, *::after { box-sizing: border-box; }\n" +
                "html, body { height: 100%; }\n" +
                "body {\n" +
                "  margin: 0;\n" +
                "  padding: 16px 20px;\n" +
                "  background: var(--bg-body);\n" +
                "  color: var(--text-main);\n" +
                "  font-family: \"Segoe UI\", -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif;\n" +
                "  font-size: 14px;\n" +
                "  overflow-y: auto;\n" +
                "}\n" +
                "#app {\n" +
                "  display: flex;\n" +
                "  flex-direction: column;\n" +
                "  min-height: 100%;\n" +
                "  gap: 12px;\n" +
                "}\n" +
                "\n" +
                ".header {\n" +
                "  display: flex;\n" +
                "  justify-content: space-between;\n" +
                "  align-items: baseline;\n" +
                "  border-bottom: 1px solid var(--border-color);\n" +
                "  padding-bottom: 8px;\n" +
                "  flex-shrink: 0;\n" +
                "}\n" +
                ".header h1 {\n" +
                "  font-size: 16px;\n" +
                "  margin: 0;\n" +
                "  font-weight: 600;\n" +
                "  color: var(--primary);\n" +
                "}\n" +
                ".header .subtitle {\n" +
                "  font-size: 12px;\n" +
                "  color: var(--text-muted);\n" +
                "}\n" +
                "\n" +
                "/* Info Button */\n" +
                ".info-btn {\n" +
                "  background: transparent;\n" +
                "  color: var(--good);\n" +
                "  border: 1.5px solid var(--good);\n" +
                "  border-radius: 50%;\n" +
                "  width: 18px;\n" +
                "  height: 18px;\n" +
                "  display: inline-flex;\n" +
                "  align-items: center;\n" +
                "  justify-content: center;\n" +
                "  font-family: \"Segoe UI\", sans-serif;\n" +
                "  font-size: 11px;\n" +
                "  font-weight: bold;\n" +
                "  cursor: pointer;\n" +
                "  padding: 0;\n" +
                "  transition: all 0.2s ease;\n" +
                "  line-height: 1;\n" +
                "}\n" +
                ".info-btn:hover {\n" +
                "  background: var(--good);\n" +
                "  color: #11141d;\n" +
                "  box-shadow: 0 0 8px var(--good);\n" +
                "}\n" +
                "\n" +
                "/* Modal Explanation Styling */\n" +
                ".modal-overlay {\n" +
                "  position: fixed;\n" +
                "  top: 0;\n" +
                "  left: 0;\n" +
                "  width: 100%;\n" +
                "  height: 100%;\n" +
                "  background: rgba(11, 13, 19, 0.75);\n" +
                "  backdrop-filter: blur(5px);\n" +
                "  display: flex;\n" +
                "  align-items: center;\n" +
                "  justify-content: center;\n" +
                "  z-index: 2000;\n" +
                "  padding: 20px;\n" +
                "}\n" +
                ".modal-content {\n" +
                "  background: var(--bg-surface);\n" +
                "  border: 1px solid var(--border-color);\n" +
                "  border-radius: 12px;\n" +
                "  width: 100%;\n" +
                "  max-width: 680px;\n" +
                "  max-height: 90%;\n" +
                "  display: flex;\n" +
                "  flex-direction: column;\n" +
                "  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);\n" +
                "  animation: modalFadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);\n" +
                "}\n" +
                "@keyframes modalFadeIn {\n" +
                "  from { opacity: 0; transform: scale(0.96) translateY(10px); }\n" +
                "  to { opacity: 1; transform: scale(1) translateY(0); }\n" +
                "}\n" +
                ".modal-header {\n" +
                "  padding: 16px 20px;\n" +
                "  border-bottom: 1px solid var(--border-color);\n" +
                "  display: flex;\n" +
                "  justify-content: space-between;\n" +
                "  align-items: center;\n" +
                "}\n" +
                ".modal-header h2 {\n" +
                "  font-size: 15px;\n" +
                "  margin: 0;\n" +
                "  font-weight: bold;\n" +
                "  color: var(--primary);\n" +
                "}\n" +
                ".modal-close {\n" +
                "  background: transparent;\n" +
                "  border: none;\n" +
                "  color: var(--text-muted);\n" +
                "  font-size: 24px;\n" +
                "  cursor: pointer;\n" +
                "  padding: 0;\n" +
                "  line-height: 1;\n" +
                "}\n" +
                ".modal-close:hover {\n" +
                "  color: var(--text-main);\n" +
                "}\n" +
                ".modal-body {\n" +
                "  padding: 20px;\n" +
                "  overflow-y: auto;\n" +
                "  flex: 1;\n" +
                "  font-size: 13px;\n" +
                "  line-height: 1.5;\n" +
                "}\n" +
                ".modal-body p {\n" +
                "  margin: 0 0 12px 0;\n" +
                "}\n" +
                ".section-title {\n" +
                "  font-size: 13px;\n" +
                "  font-weight: bold;\n" +
                "  color: var(--primary);\n" +
                "  margin: 20px 0 10px 0;\n" +
                "  text-transform: uppercase;\n" +
                "  letter-spacing: 0.5px;\n" +
                "  border-bottom: 1.5px solid var(--border-color);\n" +
                "  padding-bottom: 4px;\n" +
                "}\n" +
                ".pillar-desc {\n" +
                "  background: var(--bg-surface-2);\n" +
                "  border: 1px solid var(--border-color);\n" +
                "  border-left: 3px solid var(--primary);\n" +
                "  border-radius: 6px;\n" +
                "  padding: 12px;\n" +
                "  margin-bottom: 12px;\n" +
                "}\n" +
                ".pillar-desc p {\n" +
                "  margin: 4px 0 8px 0;\n" +
                "}\n" +
                ".pillar-desc ul {\n" +
                "  margin: 0;\n" +
                "  padding-left: 16px;\n" +
                "}\n" +
                ".pillar-desc li {\n" +
                "  margin-bottom: 4px;\n" +
                "}\n" +
                ".pillar-desc-header {\n" +
                "  font-weight: bold;\n" +
                "  font-size: 13px;\n" +
                "}\n" +
                ".modal-footer {\n" +
                "  padding: 12px 20px;\n" +
                "  border-top: 1px solid var(--border-color);\n" +
                "  display: flex;\n" +
                "  justify-content: flex-end;\n" +
                "}\n" +
                ".modal-close-btn {\n" +
                "  background: var(--bg-surface-2);\n" +
                "  border: 1px solid var(--border-color);\n" +
                "  color: var(--text-main);\n" +
                "  padding: 6px 16px;\n" +
                "  border-radius: 6px;\n" +
                "  font-size: 12px;\n" +
                "  font-weight: 600;\n" +
                "  cursor: pointer;\n" +
                "  transition: all 0.2s ease;\n" +
                "}\n" +
                ".modal-close-btn:hover {\n" +
                "  background: var(--border-color);\n" +
                "}\n" +
                "\n" +
                ".hero {\n" +
                "  display: flex;\n" +
                "  align-items: center;\n" +
                "  gap: 24px;\n" +
                "  padding: 18px 20px;\n" +
                "  background: var(--bg-surface);\n" +
                "  border: 1px solid var(--border-color);\n" +
                "  border-radius: 10px;\n" +
                "  flex-shrink: 0;\n" +
                "}\n" +
                ".score-circle {\n" +
                "  width: 120px;\n" +
                "  height: 120px;\n" +
                "  border-radius: 50%;\n" +
                "  display: flex;\n" +
                "  flex-direction: column;\n" +
                "  align-items: center;\n" +
                "  justify-content: center;\n" +
                "  color: #11141d;\n" +
                "  font-weight: bold;\n" +
                "  flex-shrink: 0;\n" +
                "  box-shadow: 0 2px 8px rgba(0,0,0,0.3);\n" +
                "}\n" +
                ".score-circle .num {\n" +
                "  font-size: 42px;\n" +
                "  font-weight: 700;\n" +
                "  line-height: 1;\n" +
                "}\n" +
                ".score-circle .denom {\n" +
                "  font-size: 12px;\n" +
                "  opacity: 0.85;\n" +
                "  margin-top: 4px;\n" +
                "}\n" +
                ".score-circles-row {\n" +
                "  display: flex;\n" +
                "  align-items: flex-end;\n" +
                "  gap: 14px;\n" +
                "  flex-shrink: 0;\n" +
                "}\n" +
                ".score-circle-wrap {\n" +
                "  display: flex;\n" +
                "  flex-direction: column;\n" +
                "  align-items: center;\n" +
                "  gap: 6px;\n" +
                "}\n" +
                ".score-circle-main {\n" +
                "  width: 110px;\n" +
                "  height: 110px;\n" +
                "}\n" +
                ".score-circle-main .num { font-size: 38px; }\n" +
                ".score-circle-small {\n" +
                "  width: 70px;\n" +
                "  height: 70px;\n" +
                "}\n" +
                ".score-circle-small .num { font-size: 22px; }\n" +
                ".score-circle-small .denom { font-size: 9px; margin-top: 2px; }\n" +
                ".score-label {\n" +
                "  font-size: 10px;\n" +
                "  font-weight: 600;\n" +
                "  color: var(--text-muted);\n" +
                "  text-transform: uppercase;\n" +
                "  letter-spacing: 0.5px;\n" +
                "}\n" +
                ".hero-info {\n" +
                "  display: flex;\n" +
                "  flex-direction: column;\n" +
                "  gap: 6px;\n" +
                "  flex: 1;\n" +
                "  min-width: 0;\n" +
                "}\n" +
                ".grade {\n" +
                "  font-size: 28px;\n" +
                "  font-weight: 700;\n" +
                "}\n" +
                ".verdict {\n" +
                "  font-size: 13px;\n" +
                "  color: var(--text-muted);\n" +
                "}\n" +
                ".weaknesses {\n" +
                "  font-size: 12px;\n" +
                "  color: var(--text-muted);\n" +
                "}\n" +
                ".weaknesses strong {\n" +
                "  color: var(--text-main);\n" +
                "  font-weight: 600;\n" +
                "}\n" +
                "\n" +
                ".pillars {\n" +
                "  flex: 1;\n" +
                "  display: flex;\n" +
                "  flex-direction: column;\n" +
                "  gap: 10px;\n" +
                "  padding-right: 4px;\n" +
                "}\n" +
                ".pillar {\n" +
                "  display: grid;\n" +
                "  grid-template-columns: 150px 1fr 44px;\n" +
                "  align-items: center;\n" +
                "  gap: 12px;\n" +
                "  padding: 10px 12px;\n" +
                "  background: var(--bg-surface);\n" +
                "  border: 1px solid var(--border-color);\n" +
                "  border-radius: 8px;\n" +
                "  position: relative;\n" +
                "}\n" +
                ".pillar-name {\n" +
                "  font-weight: 600;\n" +
                "  font-size: 13px;\n" +
                "}\n" +
                ".pillar-name .weight {\n" +
                "  font-weight: 400;\n" +
                "  color: var(--text-muted);\n" +
                "  font-size: 11px;\n" +
                "  margin-left: 4px;\n" +
                "}\n" +
                ".bar-track {\n" +
                "  background: var(--bar-track);\n" +
                "  height: 10px;\n" +
                "  border-radius: 5px;\n" +
                "  overflow: hidden;\n" +
                "}\n" +
                ".bar-fill {\n" +
                "  height: 100%;\n" +
                "  border-radius: 5px;\n" +
                "  transition: width 0.35s ease;\n" +
                "}\n" +
                ".pillar-score {\n" +
                "  text-align: right;\n" +
                "  font-weight: 700;\n" +
                "  font-size: 14px;\n" +
                "  font-variant-numeric: tabular-nums;\n" +
                "}\n" +
                ".pillar-inputs {\n" +
                "  grid-column: 1 / -1;\n" +
                "  font-size: 11px;\n" +
                "  color: var(--text-muted);\n" +
                "  margin-top: 4px;\n" +
                "  display: flex;\n" +
                "  flex-wrap: wrap;\n" +
                "  gap: 12px;\n" +
                "}\n" +
                ".pillar-inputs span b {\n" +
                "  color: var(--text-main);\n" +
                "  font-weight: 600;\n" +
                "}\n" +
                ".pillar-inputs span.missing {\n" +
                "  opacity: 0.5;\n" +
                "}\n" +
                "\n" +
                ".empty, .loading {\n" +
                "  flex: 1;\n" +
                "  display: flex;\n" +
                "  align-items: center;\n" +
                "  justify-content: center;\n" +
                "  color: var(--text-muted);\n" +
                "  font-size: 14px;\n" +
                "}\n" +
                "\n" +
                "/* Tooltip */\n" +
                "[data-tip] { position: relative; }\n" +
                "[data-tip]:hover::after {\n" +
                "  content: attr(data-tip);\n" +
                "  position: absolute;\n" +
                "  left: 0;\n" +
                "  top: 100%;\n" +
                "  margin-top: 6px;\n" +
                "  white-space: pre-line;\n" +
                "  background: var(--bg-surface-2);\n" +
                "  color: var(--text-main);\n" +
                "  border: 1px solid var(--border-color);\n" +
                "  padding: 8px 10px;\n" +
                "  border-radius: 6px;\n" +
                "  font-size: 11px;\n" +
                "  line-height: 1.5;\n" +
                "  max-width: 300px;\n" +
                "  min-width: 220px;\n" +
                "  z-index: 1000;\n" +
                "  box-shadow: 0 4px 14px rgba(0,0,0,0.25);\n" +
                "  pointer-events: none;\n" +
                "  font-weight: 400;\n" +
                "  text-align: left;\n" +
                "}\n" +
                ".pillar [data-tip]:hover::after { top: auto; bottom: 100%; margin-bottom: 6px; margin-top: 0; }\n" +
                ".help-cursor { cursor: help; border-bottom: 1px dotted var(--text-muted); }\n" +
                "\n" +
                ".disclaimer {\n" +
                "  margin-top: 20px;\n" +
                "  padding-top: 15px;\n" +
                "  border-top: 1px solid var(--border-color);\n" +
                "  font-size: 11px;\n" +
                "  color: var(--text-muted);\n" +
                "  line-height: 1.5;\n" +
                "  text-align: justify;\n" +
                "  flex-shrink: 0;\n" +
                "}\n" +
                "</style>\n";
    }

    /** Closes head before body. */
    static String headClose() {
        return "</head>\n";
    }

    /** Header, hero scores, pillars, disclaimer, explanation modal. */
    static String bodyMarkup() {
        return "<body>\n" +
                "<div id=\"app\">\n" +
                "  <div class=\"header\">\n" +
                "    <div style=\"display: flex; align-items: center; gap: 8px;\">\n" +
                "      <h1 class=\"help-cursor\" :data-tip=\"TOOLTIPS.total\">Robustness Scorecard</h1>\n" +
                "      <button class=\"info-btn\" @click=\"showExplainModal = true\" title=\"Erklärung der Robustheits-Bewertung öffnen\">i</button>\n" +
                "    </div>\n" +
                "    <div class=\"subtitle help-cursor\" :data-tip=\"TOOLTIPS.strategy\" v-if=\"strategy\">{{ strategy.projectName }} / {{ strategy.strategyName }}</div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div v-if=\"!strategy || !strategy.projectName\" class=\"empty\">\n" +
                "    Wähle eine Strategie aus.\n" +
                "  </div>\n" +
                "  <div v-else-if=\"loading\" class=\"loading\">Stats werden geladen…</div>\n" +
                "  <template v-else-if=\"result\">\n" +
                "    <div class=\"hero\">\n" +
                "      <div class=\"score-circles-row\">\n" +
                "        <div class=\"score-circle-wrap help-cursor\" :data-tip=\"TOOLTIPS.total\">\n" +
                "          <div class=\"score-circle score-circle-main\" :style=\"{ background: scoreColor(result.total) }\">\n" +
                "            <div class=\"num\">{{ result.total }}</div>\n" +
                "            <div class=\"denom\">/ 100</div>\n" +
                "          </div>\n" +
                "          <div class=\"score-label\">Unified</div>\n" +
                "        </div>\n" +
                "        <div class=\"score-circle-wrap help-cursor\" :data-tip=\"TOOLTIPS.sensitiv\">\n" +
                "          <div class=\"score-circle score-circle-small\" :style=\"{ background: sensitivScore >= 0 ? scoreColor(sensitivScore) : 'var(--bg-surface-2)' }\">\n" +
                "            <div class=\"num\">{{ sensitivScore >= 0 ? sensitivScore : '—' }}</div>\n" +
                "            <div class=\"denom\" v-if=\"sensitivScore >= 0\">/ 100</div>\n" +
                "          </div>\n" +
                "          <div class=\"score-label\">Sensitiv</div>\n" +
                "        </div>\n" +
                "        <div class=\"score-circle-wrap help-cursor\" :data-tip=\"TOOLTIPS.ki\">\n" +
                "          <div class=\"score-circle score-circle-small\" :style=\"{ background: kiScoreVal >= 0 ? scoreColor(kiScoreVal) : 'var(--bg-surface-2)' }\">\n" +
                "            <div class=\"num\">{{ kiScoreVal >= 0 ? kiScoreVal : '—' }}</div>\n" +
                "            <div class=\"denom\" v-if=\"kiScoreVal >= 0\">/ 100</div>\n" +
                "          </div>\n" +
                "          <div class=\"score-label\">KI</div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "      <div class=\"hero-info\">\n" +
                "        <div class=\"grade help-cursor\" :style=\"{ color: scoreColor(result.total) }\" :data-tip=\"TOOLTIPS.grade\">Klasse {{ result.grade }}</div>\n" +
                "        <div class=\"verdict\">{{ verdict(result.total) }}</div>\n" +
                "        <div class=\"weaknesses\" v-if=\"weaknesses.length\">\n" +
                "          Größte Schwachpunkte: <strong>{{ weaknesses.join(', ') }}</strong>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"pillars\">\n" +
                "      <div v-for=\"p in result.pillars\" :key=\"p.name\" class=\"pillar\">\n" +
                "        <div class=\"pillar-name help-cursor\" :data-tip=\"TOOLTIPS[p.key]\">\n" +
                "          {{ p.name }}<span class=\"weight\">· Gew. {{ p.weight }}</span>\n" +
                "        </div>\n" +
                "        <div class=\"bar-track\">\n" +
                "          <div class=\"bar-fill\" :style=\"{ width: p.score + '%', background: scoreColor(p.score) }\"></div>\n" +
                "        </div>\n" +
                "        <div class=\"pillar-score\" :style=\"{ color: scoreColor(p.score) }\">{{ p.score }}</div>\n" +
                "        <div class=\"pillar-inputs\">\n" +
                "          <span v-for=\"(inp, i) in p.inputs\" :key=\"i\" class=\"help-cursor\" :class=\"{ missing: inp.missing }\" :data-tip=\"inp.tip\">\n" +
                "            {{ inp.label }}: <b>{{ inp.missing ? 'n/a' : inp.display }}</b>\n" +
                "          </span>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </template>\n" +
                "\n" +
                "  <div class=\"disclaimer\">\n" +
                "    Diese Analyse dient ausschließlich Informations- und Bildungszwecken innerhalb des MT5-Backtesters.\n" +
                "    Sie stellt keine Finanz-, Anlage- oder Tradingberatung dar. Alle Ergebnisse basieren auf historischen Daten und hypothetischen Backtest-Läufen,\n" +
                "    die zukünftige Marktbedingungen nicht vorhersagen können. Der Robustheits-Score ist ein heuristischer Indikator; ein hoher Score ist keine Garantie für zukünftige Gewinne, und ein niedriger Score kein Garant für Misserfolg.\n" +
                "    <strong>Nutze immer Out-of-Sample-Validierung (Forward-Test) und deinen eigenen Verstand vor einer Live-Bereitstellung.</strong>\n" +
                "  </div>\n" +
                "\n" +
                "  <!-- Explanation Modal Overlay -->\n" +
                "  <div v-if=\"showExplainModal\" class=\"modal-overlay\" @click.self=\"showExplainModal = false\">\n" +
                "    <div class=\"modal-content\">\n" +
                "      <div class=\"modal-header\">\n" +
                "        <h2>🛡️ Robustness Scorecard — Erklärung</h2>\n" +
                "        <button class=\"modal-close\" @click=\"showExplainModal = false\">&times;</button>\n" +
                "      </div>\n" +
                "      <div class=\"modal-body\">\n" +
                "        <p>Der <strong>Robustness Score (0–100)</strong> ist Teil des <strong>Unified Scores</strong>, der Performance und Robustheit in einer einzigen Bewertung vereint. Er basiert auf 8 Säulen — ausschließlich aus echten MetaTrader-Messwerten — und hilft dabei, überoptimierte Strategien (Curve-Fitting) zu identifizieren.</p>\n" +
                "        \n" +
                "        <div style=\"background: rgba(0, 229, 255, 0.08); border-left: 4px solid var(--primary); padding: 12px; margin-bottom: 15px; border-radius: 4px;\">\n" +
"          <strong style=\"color: var(--primary);\">📊 Die drei Scores im Überblick:</strong><br>\n" +
                "          <strong>1. Unified Score:</strong> Bewertet Performance (Profit, Drawdown, PF) UND Robustheit (Konsistenz, Sharpe, Stichprobe, Recovery) in einem gewichteten Score. Hier konfigurierbar.<br>\n" +
                "          <strong>2. Sensitiv Score:</strong> Misst die Parameter-Stabilität beim Rütteln — wie stark schwanken die Ergebnisse, wenn Parameter leicht verändert werden? (Berechnet über CV-Werte der Sensitivitätsanalyse)<br>\n" +
                "          <strong>3. KI Score:</strong> KI-Analyse, die den Unified Score, den Sensitiv Score und die Kennlinienverläufe zusammen bewertet. Im finalen Portfolio (Schritt 6) wird dieser Score gewichtet mit dem Unified Score zum Gesamtwert verrechnet (Standard: 60% Performance / 40% KI-Stabilität, konfigurierbar in den KI-Einstellungen).\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"section-title\">Die 8 Säulen des Unified Score</div>\n" +
                "        \n" +
                "        <div class=\"pillar-desc\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--primary);\">1. BT-Profitabilität</div>\n" +
                "          <p>ROI (Rendite) und Profit Factor im Backtest. Ein Profit Factor ab 1.50 gilt als solide, ab 2.0 als exzellent.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--primary);\">2. FW-Profitabilität</div>\n" +
                "          <p>ROI (Rendite) und Profit Factor in der Out-of-Sample Forward-Testphase. Zeigt, ob die Strategie auf unbekannten Daten profitabel bleibt.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--primary);\">3. FW/BT Konsistenz</div>\n" +
                "          <p>Verhältnis der Performance zwischen der Backtest- und Forward-Phase. Starke Abweichungen deuten auf Überoptimierung hin.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--primary);\">4. Risiko-Verhältnis</div>\n" +
                "          <p>Gewinn im Verhältnis zum maximalen Drawdown (Return/DD) und annualisiert (Calmar Ratio).</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--primary);\">5. Sharpe Ratio</div>\n" +
                "          <p>Die von MetaTrader pro Pass gemessene Sharpe Ratio (Backtest und Forward gemittelt). Bewertet die Gleichmäßigkeit der Erträge relativ zu ihrer Schwankung — eine echte Messgröße, kein Schätzwert.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--primary);\">6. Stichprobengröße</div>\n" +
                "          <p>Mindestens 100 Trades und ausreichende historische Testjahre (real aus dem Testzeitraum berechnet). Weniger als 100 Trades deckeln diese Säule auf max. 30 Punkte.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--primary);\">7. FW Trade Count</div>\n" +
                "          <p>Anzahl der Trades in der Forward-Phase zur Absicherung der statistischen Signifikanz.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--primary);\">8. Erholungsfaktor</div>\n" +
                "          <p>Fähigkeit der Strategie, sich schnell aus Verlustphasen zu erholen (gemessen im Backtest und Forward-Test).</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\" style=\"border-left-color: var(--ok);\">\n" +
                "          <div class=\"pillar-desc-header\" style=\"color: var(--ok);\">Entfernte Säulen (Transparenz-Hinweis)</div>\n" +
                "          <p>Die früheren Säulen <i>Equity-Konsistenz (R²/SQN)</i>, <i>Symmetrie</i> und <i>Tail-Risk</i> wurden entfernt: Sie basierten nicht auf Messdaten, sondern auf einer synthetisch generierten Equity-Kurve bzw. fest angenommenen Werten und haben dem Score damit Schein-Information hinzugefügt.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"section-title\">Interpretations-Beispiele</div>\n" +
                "        \n" +
                "        <div class=\"pillar-desc\" style=\"border-left-color: var(--good); background: rgba(0, 230, 118, 0.05);\">\n" +
                "          <strong style=\"color: var(--good);\">Beispiel A: Robuster Gewinner (Score: 85 - Klasse A)</strong>\n" +
                "          <p style=\"margin: 4px 0 0 0;\">350 Trades über 5 Jahre, Sharpe 1.4 im Backtest und 1.2 im Forward, Calmar = 2.4, Forward-Profit proportional zum Backtest. Diese Strategie zeigt eine hervorragende, breit abgestützte Stabilität über verschiedene Marktphasen hinweg.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"pillar-desc\" style=\"border-left-color: var(--bad); background: rgba(255, 82, 82, 0.05);\">\n" +
                "          <strong style=\"color: var(--bad);\">Beispiel B: Curve-Fitted Illusion (Score: 35 - Klasse D/E)</strong>\n" +
                "          <p style=\"margin: 4px 0 0 0;\">35 Trades über 2 Jahre, Sharpe nahe 0, Forward-Profit bricht gegenüber dem Backtest massiv ein. Ein einziger Riesen-Gewinn-Trade rettet die Performance. Extrem hohes Risiko, dass diese Strategie im Live-Trading scheitert.</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"section-title\">Worauf man achten muss</div>\n" +
                "        <ol style=\"padding-left: 20px; margin: 8px 0; line-height: 1.6;\">\n" +
                "          <li><strong>Keine Live-Garantie:</strong> Ein hoher Score ist ein sehr guter Filter, aber kein Versprechen. Nutze <i>immer</i> Out-of-Sample-Validierung (Forward-Test).</li>\n" +
                "          <li><strong>Die Stichprobe ist König:</strong> Achte darauf, dass deine Strategie genügend Trades macht. Wenige Glückstrades verfälschen alle anderen Metriken.</li>\n" +
                "          <li><strong>Validierung auf unberührten Daten:</strong> Der Forward-Test wird bereits für die Auswahl benutzt. Nutze Schritt 7 (Validierung), um die finalen Strategien auf einem Zeitfenster zu prüfen, das weder Optimierung noch Auswahl je gesehen hat.</li>\n" +
                "        </ol>\n" +
                "      </div>\n" +
                "      <div class=\"modal-footer\">\n" +
                "        <button class=\"modal-close-btn\" @click=\"showExplainModal = false\">Schließen</button>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n" +
                "\n";
    }

    /** Injected strategy/stats JSON plus Vue 3 app script. */
    static String vueAppScript(String strategyJson, String statsJson) {
        return "<script>\n" +
                "// Inject data from Java\n" +
                strategyJson + "\n" +
                statsJson + "\n" +
                "\n" +
                "const { createApp, ref, reactive, computed, onMounted } = Vue;\n" +
                "console.log('JS: Script started, Vue object type is: ' + typeof Vue);\n" +
                "console.log('JS: Injected strategy exists: ' + (window.INJECTED_STRATEGY !== undefined));\n" +
                "console.log('JS: Injected stats exists: ' + (window.INJECTED_STATS !== undefined));\n" +
                "\n" +
                "// ---- Tooltip copy ----\n" +
                "const TOOLTIPS = {\n" +
                "  total:\n" +
                "    'Unified Score (0–100)\\n' +\n" +
                "    'Vereint Performance (Profit, PF, DD) und Robustheit (Konsistenz, Sharpe, Stichprobe, Recovery) in einem gewichteten Score mit 8 Säulen aus echten Messdaten.\\n' +\n" +
                "    'Noten: 85+ A · 70+ B · 55+ C · 40+ D · darunter F.',\n" +
                "  sensitiv:\n" +
                "    'Sensitiv Score (0–100)\\n' +\n" +
                "    'Misst die Parameter-Stabilität: Wie stark schwanken die Ergebnisse, wenn Parameter leicht verändert werden (Rütteln)?\\n' +\n" +
                "    'Basiert auf den CV-Werten der Sensitivitätsanalyse. Hoher Wert = stabil.',\n" +
                "  ki:\n" +
                "    'KI Score (0–100)\\n' +\n" +
                "    'KI-Bewertung, die den Unified Score, den Sensitiv Score und die Kennlinienverläufe zusammen analysiert.\\n' +\n" +
                "    'Ergibt ein Gesamtbild der Strategie-Qualität.',\n" +
                "  grade:\n" +
                "    'Vom Unified Score abgeleitete Qualitätsklasse.\\n' +\n" +
                "    'A = Sehr robust, übersteht die meisten Stresstests.\\n' +\n" +
                "    'C = Brauchbar, weist aber klare Schwachstellen auf.\\n' +\n" +
                "    'F = Instabil oder überoptimiert (Curve-Fitted) — nicht live einsetzen.',\n" +
                "\n" +
                "  // Pillars\n" +
                "  bt_profitability:\n" +
                "    'Säule BT-Profitabilität\\n' +\n" +
                "    'Bewertet ROI und Profit Factor im Backtest.\\n' +\n" +
                "    'Eingangsdaten: ROI (Rendite), Profit Factor.',\n" +
                "  fw_profitability:\n" +
                "    'Säule FW-Profitabilität\\n' +\n" +
                "    'Bewertet ROI und Profit Factor im Forward-Test (Out-of-Sample).\\n' +\n" +
                "    'Eingangsdaten: FW-ROI, FW-Profit Factor.',\n" +
                "  consistency:\n" +
                "    'Säule FW/BT Konsistenz\\n' +\n" +
                "    'Vergleicht die Performance des Backtests mit dem Forward-Test.\\n' +\n" +
                "    'Eingangsdaten: FW/BT Profit Ratio.',\n" +
                "  risk:\n" +
                "    'Säule Risiko-Verhältnis\\n' +\n" +
                "    'Bewertet das Verhältnis von Gewinn zu Drawdown und den Calmar-Ratio.\\n' +\n" +
                "    'Eingangsdaten: Return/Drawdown, Calmar-Ratio.',\n" +
                "  equity_consistency:\n" +
                "    'Säule Sharpe Ratio\\n' +\n" +
                "    'Bewertet die von MetaTrader gemessene Sharpe Ratio (BT und FW gemittelt).\\n' +\n" +
                "    'Eingangsdaten: BT-Sharpe, FW-Sharpe.',\n" +
                "  sample_size:\n" +
                "    'Säule Stichprobengröße\\n' +\n" +
                "    'Bewertet die Anzahl der Trades und die Dauer des Tests in Jahren.\\n' +\n" +
                "    'Eingangsdaten: Trades, Testjahre.',\n" +
                "  fw_trades:\n" +
                "    'Säule FW Trade Count\\n' +\n" +
                "    'Bewertet die Anzahl der ausgeführten Trades in der Forward-Phase.\\n' +\n" +
                "    'Eingangsdaten: FW Trades.',\n" +
                "  recovery:\n" +
                "    'Säule Erholungsfaktor\\n' +\n" +
                "    'Bewertet die Fähigkeit der Strategie, sich von Drawdowns zu erholen.\\n' +\n" +
                "    'Eingangsdaten: BT-Recovery, FW-Recovery.',\n" +
                "\n" +
                "  // Raw metric inputs\n" +
                "  roi:\n" +
                "    'Return on Investment (Nettogewinn ÷ Einzahlung im Backtest).\\n' +\n" +
                "    'Zeigt den prozentualen Zuwachs des Kontos.',\n" +
                "  fwRoi:\n" +
                "    'Return on Investment in der Forward-Phase (Out-of-Sample).\\n' +\n" +
                "    'Zeigt den prozentualen Zuwachs des Kontos in der Testphase.',\n" +
                "  pf:\n" +
                "    'Profit Factor = Bruttogewinn ÷ Bruttoverlust.\\n' +\n" +
                "    'Schwach: < 1.2 · OK: 1.2–1.5 · Gut: 1.5+.\\n' +\n" +
                "    'Beispiel: 1.87 bedeutet, dass du 1,87 $ verdienst für jeden 1 $, den du verlierst.',\n" +
                "  rexp:\n" +
                "    'R-Expectancy = Erwartungswert pro Trade gemessen in Einheiten des Risikos (R).\\n' +\n" +
                "    'Schwach: < 0.1 · OK: 0.1–0.3 · Gut: 0.3+.\\n' +\n" +
                "    'Beispiel: 0.35R bedeutet, jeder Trade bringt im Schnitt das 0,35-fache des Risikos.',\n" +
                "  rdd:\n" +
                "    'Return / Drawdown = Nettogewinn ÷ max. Drawdown.\\n' +\n" +
                "    'Schwach: < 2 · OK: 2–5 · Gut: 5+.\\n' +\n" +
                "    'Beispiel: 4.0 bedeutet, du hast das 4-fache deines schlimmsten Drawdowns verdient.',\n" +
                "  calmar:\n" +
                "    'Calmar-Ratio = Annualisierter Ertrag ÷ max. Drawdown.\\n' +\n" +
                "    'Schwach: < 1 · OK: 1–2 · Gut: 2+.\\n' +\n" +
                "    'Beispiel: 2.5 bedeutet, der jährliche Ertrag ist 2.5x so hoch wie der maximale Drawdown.',\n" +
                "  btSharpe:\n" +
                "    'Sharpe Ratio im Backtest, von MetaTrader gemessen.\\n' +\n" +
                "    'Schwach: < 0.5 · OK: 0.5–2.0 · Gut: 2.0+.',\n" +
                "  fwSharpe:\n" +
                "    'Sharpe Ratio im Forward-Test (Out-of-Sample), von MetaTrader gemessen.\\n' +\n" +
                "    'Konsistenz mit dem BT-Wert ist wichtiger als die absolute Höhe.',\n" +
                "  trades:\n" +
                "    'Anzahl der Trades im Backtest.\\n' +\n" +
                "    '< 100 unglaubwürdig · 300+ solide · 1000+ statistisch signifikant.',\n" +
                "  years:\n" +
                "    'Laufzeit des Backtests in Kalenderjahren.\\n' +\n" +
                "    '< 1 Jahr schwach · 3+ Jahre deckt Zyklen ab · 7+ Jahre sehr robust.',\n" +
                "  fwTrades:\n" +
                "    'Anzahl der ausgeführten Trades in der Forward-Phase (Out-of-Sample).\\n' +\n" +
                "    'Wichtig für die statistische Relevanz des Forward-Tests.',\n" +
                "  btRecovery:\n" +
                "    'Erholungsfaktor im Backtest (Nettogewinn ÷ max. Drawdown).\\n' +\n" +
                "    'Zeigt, wie schnell sich das Konto erholt.',\n" +
                "  fwRecovery:\n" +
                "    'Erholungsfaktor im Forward-Test (Nettogewinn ÷ max. Drawdown).\\n' +\n" +
                "    'Zeigt die Erholungsfähigkeit in der Testphase.',\n" +
                "\n" +
                "  strategy:\n" +
                "    'Aktuelle geladene Strategie aus dem Backtester.'\n" +
                "};\n" +
                "\n" +
                "function computeScore(statsFull, statsLong, statsShort) {\n" +
                "  const s = statsFull || {};\n" +
                "  const pillars = s.pillars || [];\n" +
                "  for (const p of pillars) {\n" +
                "    p.score = Math.round(p.score);\n" +
                "  }\n" +
                "  const total = Math.round(s.total || 0);\n" +
                "\n" +
                "  let grade = 'F';\n" +
                "  if (total >= 85) grade = 'A';\n" +
                "  else if (total >= 70) grade = 'B';\n" +
                "  else if (total >= 55) grade = 'C';\n" +
                "  else if (total >= 40) grade = 'D';\n" +
                "\n" +
                "  return { total, grade, pillars };\n" +
                "}\n" +
                "\n" +
                "// ---- Vue app ----\n" +
                "createApp({\n" +
                "  setup() {\n" +
                "    console.log('JS: Vue setup function entered');\n" +
                "    const strategy = ref(null);\n" +
                "    const loading = ref(false);\n" +
                "    const result = ref(null);\n" +
                "    const statsFull = ref(null);\n" +
                "    const showExplainModal = ref(false);\n" +
                "\n" +
                "    const weaknesses = computed(() => {\n" +
                "      if (!result.value) return [];\n" +
                "      return [...result.value.pillars]\n" +
                "        .filter(p => p.score !== null)\n" +
                "        .sort((a, b) => a.score - b.score)\n" +
                "        .slice(0, 2)\n" +
                "        .map(p => p.name);\n" +
                "    });\n" +
                "\n" +
                "    const sensitivScore = computed(() => {\n" +
                "      if (!statsFull.value || statsFull.value.SensitivScore == null || statsFull.value.SensitivScore < 0) return -1;\n" +
                "      return Math.round(statsFull.value.SensitivScore);\n" +
                "    });\n" +
                "\n" +
                "    const kiScoreVal = computed(() => {\n" +
                "      if (!statsFull.value || statsFull.value.KiScore == null || statsFull.value.KiScore < 0) return -1;\n" +
                "      return Math.round(statsFull.value.KiScore);\n" +
                "    });\n" +
                "\n" +
                "    function scoreColor(score) {\n" +
                "      if (score >= 70) return 'var(--good)';\n" +
                "      if (score >= 55) return 'var(--ok)';\n" +
                "      return 'var(--bad)';\n" +
                "    }\n" +
                "\n" +
                "    function verdict(score) {\n" +
                "      if (score >= 85) return 'Hervorragende Robustheit — Stabil in allen Bereichen.';\n" +
                "      if (score >= 70) return 'Robuste Strategie — Gutes Gesamtprofil.';\n" +
                "      if (score >= 55) return 'Durchwachsen — Nutzung mit erhöhter Vorsicht.';\n" +
                "      if (score >= 40) return 'Schwach — Deutliche Schwachstellen vorhanden.';\n" +
                "      return 'Fragil oder Überoptimiert — Nicht im Live-Betrieb nutzen.';\n" +
                "    }\n" +
                "\n" +
                "    function tryCompute() {\n" +
                "      console.log('JS: tryCompute entering');\n" +
                "      result.value = computeScore(statsFull.value, null, null);\n" +
                "      loading.value = false;\n" +
                "      console.log('JS: tryCompute finished, result total=' + (result.value ? result.value.total : 'null'));\n" +
                "    }\n" +
                "\n" +
                "    onMounted(() => {\n" +
                "      console.log('JS: Vue onMounted function entered');\n" +
                "      document.documentElement.setAttribute('data-theme', 'dark');\n" +
                "      \n" +
                "      console.log('JS: Checking INJECTED_STRATEGY=' + (window.INJECTED_STRATEGY !== undefined) + ' and INJECTED_STATS=' + (window.INJECTED_STATS !== undefined));\n" +
                "      if (window.INJECTED_STRATEGY && window.INJECTED_STATS) {\n" +
                "        strategy.value = window.INJECTED_STRATEGY;\n" +
                "        statsFull.value = window.INJECTED_STATS;\n" +
                "        tryCompute();\n" +
                "      }\n" +
                "    });\n" +
                "\n" +
                "    console.log('JS: Vue setup returning state refs');\n" +
                "    return { strategy, loading, result, weaknesses, sensitivScore, kiScoreVal, scoreColor, verdict, TOOLTIPS, showExplainModal, statsFull };\n" +
                "  }\n" +
                "}).mount('#app');\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
    }

    static String loadVueJs() {
        log.info("Loading vue.global.prod.js from classpath...");
        try (java.io.InputStream is = RobustnessScorecardHtmlSections.class.getResourceAsStream("/vue.global.prod.js")) {
            if (is == null) {
                log.error("Resource /vue.global.prod.js not found in classpath!");
                return "";
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                String content = sb.toString();
                log.info("Successfully loaded vue.global.prod.js from classpath, size: {} bytes", content.length());
                return content;
            }
        } catch (Exception e) {
            log.error("Exception loading vue.global.prod.js: ", e);
            return "";
        }
    }
}
