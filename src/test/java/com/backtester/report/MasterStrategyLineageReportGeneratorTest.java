package com.backtester.report;

import com.backtester.workflow.CustomProject;
import com.backtester.workflow.MasterStrategyEntry;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MasterStrategyLineageReportGeneratorTest {

    @Test
    public void testGenerateReportWithLineageData() throws Exception {
        CustomProject project = new CustomProject();
        project.setName("TestProject_EURUSD");
        project.setExpert("ToTheMoon_KI");
        project.setSymbol("EURUSD");
        project.setPeriod("M5");

        List<MasterStrategyEntry> lineage = new ArrayList<>();

        // Pick 1 (Baseline)
        MasterStrategyEntry e1 = new MasterStrategyEntry();
        e1.setSequence(1);
        e1.setCreatedAt(System.currentTimeMillis());
        e1.setStageTaskName("01 Base Preset");
        e1.setSourceDatabank("g01_base");
        e1.setProfit(1000.0);
        e1.setReturnToDrawdown(5.0);
        e1.setMaxDrawdownPercent(10.0);
        e1.setTotalTrades(100);
        e1.setBacktestSucceeded(true);
        e1.setVerdict(MasterStrategyEntry.Verdict.UNBEKANNT);
        List<double[]> curve1 = List.of(
                new double[]{0, 10000},
                new double[]{50, 10500},
                new double[]{100, 11000}
        );
        e1.setEquityCurve(curve1);
        e1.setOptimizedParameters(List.of(
                new MasterStrategyEntry.ParameterChange("Inp_TakeProfit", "50", "50")
        ));
        lineage.add(e1);

        // Pick 2 (Improved)
        MasterStrategyEntry e2 = new MasterStrategyEntry();
        e2.setSequence(2);
        e2.setCreatedAt(System.currentTimeMillis() + 1000);
        e2.setStageTaskName("02 Order Taktung");
        e2.setSourceDatabank("g02_order_taktung");
        e2.setProfit(1500.0);
        e2.setReturnToDrawdown(7.5);
        e2.setMaxDrawdownPercent(8.0);
        e2.setTotalTrades(120);
        e2.setBacktestSucceeded(true);
        e2.setVerdict(MasterStrategyEntry.Verdict.BESSER);
        e2.setComparedToSequence(1);
        e2.setDeltaProfit(500.0);
        e2.setDeltaReturnToDrawdown(2.5);
        List<double[]> curve2 = List.of(
                new double[]{0, 10000},
                new double[]{60, 10800},
                new double[]{120, 11500}
        );
        e2.setEquityCurve(curve2);
        e2.setOptimizedParameters(List.of(
                new MasterStrategyEntry.ParameterChange("Inp_MaxOrders", "3", "5")
        ));
        lineage.add(e2);

        project.setMasterStrategyLineage(lineage);

        Path reportPath = MasterStrategyLineageReportGenerator.generateReport(project);
        assertNotNull(reportPath);
        assertTrue(Files.exists(reportPath));

        String content = Files.readString(reportPath);
        assertTrue(content.contains("Master-Strategie Abschlussbericht"));
        assertTrue(content.contains("TestProject_EURUSD"));
        assertTrue(content.contains("01 Base Preset"));
        assertTrue(content.contains("02 Order Taktung"));
        assertTrue(content.contains("besser"));
        assertTrue(content.contains("class=\"trend-svg\""));
        assertTrue(content.contains("class=\"equity-svg\""));
        assertTrue(content.contains("Inp_MaxOrders"));
    }

    @Test
    public void confirmedMasterNotTheLastRejectedMeasurementDefinesCurrentState() throws Exception {
        CustomProject project = new CustomProject("ReportMaster", "EA", "EURUSD", "M5");
        MasterStrategyEntry confirmed = measured(1, "Bestätigte Basis", 1000.0, 4.0);
        MasterStrategyEntry rejected = measured(2, "Verworfener Versuch", 200.0, 0.8);
        rejected.setVerdict(MasterStrategyEntry.Verdict.SCHLECHTER);
        rejected.setComparedToSequence(1);
        project.setMasterStrategyLineage(List.of(confirmed, rejected));
        project.setConfirmedMasterSequence(1);

        Path reportPath = MasterStrategyLineageReportGenerator.generateReport(project);
        try {
            String content = Files.readString(reportPath);

            assertTrue(content.contains("<div class=\"kpi-title\">Bestätigter Master</div>"));
            assertTrue(content.contains("Initial 1000.00 → Bestätigter Master 1000.00"));
            assertFalse(content.contains("Bestätigter Master 200.00"));
            assertTrue(content.contains("class=\"confirmed-master-row\""));
            assertTrue(content.contains("confirmed-master-card\" id=\"pick-1\""));
            assertFalse(content.contains("confirmed-master-card\" id=\"pick-2\""));
            assertTrue(content.contains("BESTÄTIGTER MASTER"));
        } finally {
            Files.deleteIfExists(reportPath);
        }
    }

    private static MasterStrategyEntry measured(int sequence, String stage, double profit, double ratio) {
        MasterStrategyEntry entry = new MasterStrategyEntry();
        entry.setSequence(sequence);
        entry.setCreatedAt(System.currentTimeMillis() + sequence);
        entry.setStageTaskName(stage);
        entry.setBacktestSucceeded(true);
        entry.setProfit(profit);
        entry.setReturnToDrawdown(ratio);
        entry.setMaxDrawdownAbsolute(profit / ratio);
        entry.setVerdict(sequence == 1
                ? MasterStrategyEntry.Verdict.UNBEKANNT : MasterStrategyEntry.Verdict.SCHLECHTER);
        return entry;
    }
}
