package com.backtester.report;

import com.backtester.engine.OptimizationConfig;
import com.backtester.config.EaParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RobustnessHtmlGenerator {

    private static final Logger log = LoggerFactory.getLogger(RobustnessHtmlGenerator.class);

    public static void generateReport(RobustnessResult result, OptimizationConfig config, String targetMetric, String yAxisLabel, List<EaParameter> originalParams) {
        Path reportPath = java.nio.file.Paths.get(result.getOutputDirectory(), "robustness_report.html");
        
        StringBuilder jsData = new StringBuilder();
        jsData.append("const sweeps = [];\n");
        
        for (Map.Entry<String, Map<String, OptimizationResult>> paramEntry : result.getParameterSweeps().entrySet()) {
            String paramName = paramEntry.getKey();
            Map<String, OptimizationResult> periods = paramEntry.getValue();
            if (periods.isEmpty()) continue;
            
            String defaultValue = "0";
            if (originalParams != null) {
                for (EaParameter p : originalParams) {
                    if (p.getName().equals(paramName)) {
                        defaultValue = p.getValue();
                        break;
                    }
                }
            }
                
            jsData.append("sweeps.push({\n");
            jsData.append("  paramName: '").append(paramName).append("',\n");
            jsData.append("  defaultValue: '").append(defaultValue).append("',\n");
            
            // Extract sorted labels from the first period (Base)
            OptimizationResult baseRes = periods.values().iterator().next();
            List<OptimizationResult.Pass> basePasses = baseRes.getPasses().stream()
                .sorted(Comparator.comparingDouble(p -> {
                    try { return Double.parseDouble(p.getParameter(paramName)); } 
                    catch (Exception e) { return 0.0; }
                }))
                .collect(Collectors.toList());

            jsData.append("  labels: [");
            for (int i = 0; i < basePasses.size(); i++) {
                jsData.append("'").append(basePasses.get(i).getParameter(paramName)).append("'");
                if (i < basePasses.size() - 1) jsData.append(",");
            }
            jsData.append("],\n");
            jsData.append("  yLabel: '").append(yAxisLabel).append("',\n");

            // Extract each period dataset
            jsData.append("  periods: [\n");
            int periodIdx = 0;
            for (Map.Entry<String, OptimizationResult> periodEntry : periods.entrySet()) {
                String pLabel = periodEntry.getKey();
                OptimizationResult pRes = periodEntry.getValue();
                
                List<OptimizationResult.Pass> pPasses = pRes.getPasses().stream()
                    .sorted(Comparator.comparingDouble(p -> {
                        try { return Double.parseDouble(p.getParameter(paramName)); } 
                        catch (Exception e) { return 0.0; }
                    }))
                    .collect(Collectors.toList());

                jsData.append("    {\n");
                jsData.append("      label: '").append(pLabel).append("',\n");
                jsData.append("      data: [");
                for (int i = 0; i < pPasses.size(); i++) {
                    jsData.append(extractMetric(pPasses.get(i), targetMetric));
                    if (i < pPasses.size() - 1) jsData.append(",");
                }
                jsData.append("],\n");
                jsData.append("      isBase: ").append(periodIdx == 0).append("\n");
                jsData.append("    }");
                if (periodIdx < periods.size() - 1) jsData.append(",");
                jsData.append("\n");
                periodIdx++;
            }
            jsData.append("  ]\n");
            jsData.append("});\n");
        }
        
        String html = buildHtmlTemplate(jsData.toString(), config.getExpert(), targetMetric);
        
        try {
            Files.write(reportPath, html.getBytes(StandardCharsets.UTF_8));
            log.info("Robustness HTML report generated at: {}", reportPath);
        } catch (IOException e) {
            log.error("Failed to write robustness HTML report", e);
        }
    }
    
    private static double extractMetric(OptimizationResult.Pass pass, String metric) {
        switch (metric) {
            case "Profit": return pass.getProfit();
            case "Profit Factor": return pass.getProfitFactor();
            case "Expected Payoff": return pass.getExpectedPayoff();
            case "Sharpe": return pass.getSharpeRatio();
            case "Drawdown": return pass.getDrawdownPercent(); 
            default: return pass.getProfit();
        }
    }

    private static String buildHtmlTemplate(String jsData, String expert, String metricName) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Robustness Scanner Report</title>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation@2.1.0\"></script>\n" +
                "    <style>\n" +
                "        :root {\n" +
                "            --bg-dark: #121212;\n" +
                "            --bg-card: #1E1E1E;\n" +
                "            --text-main: #E0E0E0;\n" +
                "            --text-muted: #A0A0A0;\n" +
                "            --accent: #4E9AF1;\n" +
                "            --stable-zone: rgba(46, 204, 113, 0.2);\n" +
                "        }\n" +
                "        body {\n" +
                "            margin: 0;\n" +
                "            padding: 20px;\n" +
                "            background-color: var(--bg-dark);\n" +
                "            color: var(--text-main);\n" +
                "            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
                "        }\n" +
                "        .header {\n" +
                "            text-align: center;\n" +
                "            margin-bottom: 40px;\n" +
                "        }\n" +
                "        .header h1 {\n" +
                "            margin: 0;\n" +
                "            color: var(--accent);\n" +
                "            font-weight: 300;\n" +
                "            letter-spacing: 1px;\n" +
                "        }\n" +
                "        .header p {\n" +
                "            margin-top: 10px;\n" +
                "            color: var(--text-muted);\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 1400px;\n" +
                "            margin: 0 auto;\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(auto-fit, minmax(600px, 1fr));\n" +
                "            gap: 20px;\n" +
                "        }\n" +
                "        .card {\n" +
                "            background: var(--bg-card);\n" +
                "            border-radius: 10px;\n" +
                "            padding: 20px;\n" +
                "            box-shadow: 0 8px 16px rgba(0,0,0,0.4);\n" +
                "        }\n" +
                "        .chart-container {\n" +
                "            position: relative;\n" +
                "            height: 350px;\n" +
                "            width: 100%;\n" +
                "        }\n" +
                "        .card-header h3 {\n" +
                "            margin-top: 0;\n" +
                "            font-weight: 500;\n" +
                "            border-bottom: 1px solid #333;\n" +
                "            padding-bottom: 10px;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <h1>Robustness Scanner Report</h1>\n" +
                "        <p>Expert: <strong>" + expert + "</strong> | Target Metric: <strong>" + metricName + "</strong></p>\n" +
                "        <p style=\"font-size: 0.9em\">Green transparent areas represent tableaus (< 5% variance) on the base period. The Green Dot marks the original default value.</p>\n" +
                "    </div>\n" +
                "    <div class=\"container\" id=\"charts-container\"></div>\n" +
                "\n" +
                "    <script>\n" +
                "        " + jsData + "\n" +
                "        \n" +
                "        const palette = ['#4E9AF1', '#E74C3C', '#F1C40F', '#9B59B6', '#E67E22', '#1ABC9C', '#34495E', '#D35400', '#C0392B', '#8E44AD'];\n" +
                "        \n" +
                "        // Utility to find plateaus (tableaus)\n" +
                "        function findPlateaus(data) {\n" +
                "            const plateaus = [];\n" +
                "            if(data.length < 3) return plateaus;\n" +
                "            \n" +
                "            let min = Math.min(...data);\n" +
                "            let max = Math.max(...data);\n" +
                "            let amplitude = max - min;\n" +
                "            if (amplitude === 0) amplitude = 1;\n" +
                "            \n" +
                "            const threshold = amplitude * 0.05; // 5% variance threshold\n" +
                "            \n" +
                "            let currentStart = -1;\n" +
                "            for(let i = 1; i < data.length; i++) {\n" +
                "                let diff = Math.abs(data[i] - data[i-1]);\n" +
                "                let isStable = diff <= threshold;\n" +
                "                \n" +
                "                if(isStable && currentStart === -1) {\n" +
                "                    currentStart = i - 1;\n" +
                "                } else if(!isStable && currentStart !== -1) {\n" +
                "                    if(i - currentStart >= 3) {\n" +
                "                        plateaus.push({ start: currentStart, end: i - 1 });\n" +
                "                    }\n" +
                "                    currentStart = -1;\n" +
                "                }\n" +
                "            }\n" +
                "            if(currentStart !== -1 && data.length - currentStart >= 3) {\n" +
                "                plateaus.push({ start: currentStart, end: data.length - 1 });\n" +
                "            }\n" +
                "            return plateaus;\n" +
                "        }\n" +
                "\n" +
                "        const container = document.getElementById('charts-container');\n" +
                "        \n" +
                "        sweeps.forEach((sweep, index) => {\n" +
                "            const card = document.createElement('div');\n" +
                "            card.className = 'card';\n" +
                "            \n" +
                "            const header = document.createElement('div');\n" +
                "            header.className = 'card-header';\n" +
                "            header.innerHTML = `<h3>Parameter: <span>${sweep.paramName}</span></h3>`;\n" +
                "            card.appendChild(header);\n" +
                "            \n" +
                "            const chartDiv = document.createElement('div');\n" +
                "            chartDiv.className = 'chart-container';\n" +
                "            \n" +
                "            const canvas = document.createElement('canvas');\n" +
                "            canvas.id = 'chart-' + index;\n" +
                "            chartDiv.appendChild(canvas);\n" +
                "            card.appendChild(chartDiv);\n" +
                "            container.appendChild(card);\n" +
                "            \n" +
                "            const datasets = [];\n" +
                "            let baseData = [];\n" +
                "            \n" +
                "            sweep.periods.forEach((period, pIdx) => {\n" +
                "                const color = palette[pIdx % palette.length];\n" +
                "                const ds = {\n" +
                "                    label: period.label,\n" +
                "                    data: period.data,\n" +
                "                    borderColor: color,\n" +
                "                    backgroundColor: color + '1A',\n" + // 10% opacity via hex
                "                    borderWidth: period.isBase ? 3 : 2,\n" +
                "                    pointBackgroundColor: color,\n" +
                "                    pointBorderColor: color,\n" +
                "                    pointRadius: period.isBase ? 4 : 2,\n" +
                "                    pointHoverRadius: 6,\n" +
                "                    fill: period.isBase,\n" +
                "                    tension: 0.3\n" +
                "                };\n" +
                "                datasets.push(ds);\n" +
                "                if (period.isBase) {\n" +
                "                    baseData = period.data;\n" +
                "                }\n" +
                "            });\n" +
                "\n" +
                "            // Map default param dot on base dataset\n" +
                "            const baseDataset = datasets.find(d => d.fill === true);\n" +
                "            if (baseDataset) {\n" +
                "                const pointColors = [];\n" +
                "                const pointRadii = [];\n" +
                "                const pointHoverRadii = [];\n" +
                "                sweep.labels.forEach((lbl, idx) => {\n" +
                "                    let isDef = false;\n" +
                "                    try { isDef = parseFloat(lbl) === parseFloat(sweep.defaultValue); } catch(e) {}\n" +
                "                    if (isDef) {\n" +
                "                        pointColors.push('#2ecc71');\n" +
                "                        pointRadii.push(8);\n" +
                "                        pointHoverRadii.push(10);\n" +
                "                    } else {\n" +
                "                        pointColors.push(baseDataset.borderColor);\n" +
                "                        pointRadii.push(4);\n" +
                "                        pointHoverRadii.push(6);\n" +
                "                    }\n" +
                "                });\n" +
                "                baseDataset.pointBackgroundColor = pointColors;\n" +
                "                baseDataset.pointBorderColor = pointColors;\n" +
                "                baseDataset.pointRadius = pointRadii;\n" +
                "                baseDataset.pointHoverRadius = pointHoverRadii;\n" +
                "            }\n" +
                "\n" +
                "            // Annotations only for Base Data\n" +
                "            const plateaus = findPlateaus(baseData);\n" +
                "            const annotations = {};\n" +
                "            plateaus.forEach((p, pIdx) => {\n" +
                "                annotations['box' + pIdx] = {\n" +
                "                    type: 'box',\n" +
                "                    xMin: p.start,\n" +
                "                    xMax: p.end,\n" +
                "                    backgroundColor: 'rgba(46, 204, 113, 0.15)',\n" +
                "                    borderColor: 'rgba(46, 204, 113, 0.4)',\n" +
                "                    borderWidth: 1\n" +
                "                };\n" +
                "            });\n" +
                "\n" +
                "            // Chart setup\n" +
                "            new Chart(canvas, {\n" +
                "                type: 'line',\n" +
                "                data: {\n" +
                "                    labels: sweep.labels,\n" +
                "                    datasets: datasets\n" +
                "                },\n" +
                "                options: {\n" +
                "                    responsive: true,\n" +
                "                    maintainAspectRatio: false,\n" +
                "                    interaction: {\n" +
                "                        mode: 'index',\n" +
                "                        intersect: false,\n" +
                "                    },\n" +
                "                    plugins: {\n" +
                "                        legend: {\n" +
                "                            labels: { color: '#E0E0E0' }\n" +
                "                        },\n" +
                "                        annotation: {\n" +
                "                            annotations: annotations\n" +
                "                        },\n" +
                "                        tooltip: {\n" +
                "                            backgroundColor: 'rgba(0,0,0,0.8)',\n" +
                "                            titleColor: '#fff',\n" +
                "                            bodyColor: '#fff',\n" +
                "                            borderColor: '#333',\n" +
                "                            borderWidth: 1\n" +
                "                        }\n" +
                "                    },\n" +
                "                    scales: {\n" +
                "                        x: {\n" +
                "                            grid: { color: '#333' },\n" +
                "                            ticks: { color: '#A0A0A0' }\n" +
                "                        },\n" +
                "                        y: {\n" +
                "                            grid: { color: '#333' },\n" +
                "                            ticks: { color: '#A0A0A0' }\n" +
                "                        }\n" +
                "                    }\n" +
                "                }\n" +
                "            });\n" +
                "        });\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
