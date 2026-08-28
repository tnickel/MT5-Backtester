package com.backtester.workflow;

import com.backtester.report.PassPresetResolver;
import com.backtester.report.OptimizationResult.CombinedPass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class Mt5OptimizationImportServiceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void importFromMt5InstallReadsXmlWithoutTouchingProcesses() throws Exception {
        Path mtDir = tempFolder.newFolder("mt5").toPath();
        Files.writeString(mtDir.resolve("OptimizationReport.xml"), spreadsheet(
                "<Title>ToTheMoon AUDCAD,M5 2024.08.01-2025.08.01</Title>",
                row("Pass", "Result", "Profit", "Equity DD %", "Trades", "Inp_Grid_Step"),
                row("1", "1.93", "1652.58", "7.1271", "350", "350"),
                row("2", "1.50", "900.00", "5.0", "200", "400")));

        Mt5OptimizationImportService.ImportResult result =
                Mt5OptimizationImportService.importFromMt5Install(mtDir);

        assertEquals(2, result.passCount());
        assertFalse(result.forwardUsed());
        assertEquals(1652.58, result.passes().get(0).getBtProfit(), 0.001);
        assertTrue(result.hasDateRange());
        assertEquals("2024-08-01", result.fromDate());
        assertEquals("2025-08-01", result.toDate());
        assertEquals("AUDCAD", result.reportSymbol());
        assertEquals("M5", result.reportPeriod());
        assertTrue(result.message().contains("2024-08-01"));
    }

    @Test
    public void importUsesForwardWhenPresent() throws Exception {
        Path mtDir = tempFolder.newFolder("mt5fwd").toPath();
        Files.writeString(mtDir.resolve("OptimizationReport.xml"), spreadsheet(
                "<Title>EA SYM,M5 2024.03.01-2025.03.01</Title>",
                row("Pass", "Result", "Profit", "Equity DD %", "Trades", "Inp_Grid_Step"),
                row("7", "1.93", "1652.58", "7.1271", "350", "350")));
        Files.writeString(mtDir.resolve("OptimizationReport.forward.xml"), spreadsheet(
                row("Pass", "Forward Result", "Back Result", "Profit", "Trades", "Inp_Grid_Step"),
                row("7", "1.83", "1.93", "1392.19", "314", "350")));

        Mt5OptimizationImportService.ImportResult result =
                Mt5OptimizationImportService.importFromMt5Install(mtDir);

        assertEquals(1, result.passCount());
        assertTrue(result.forwardUsed());
        CombinedPass pass = result.passes().get(0);
        assertEquals(7, pass.getPassNumber());
        assertEquals(1392.19, pass.getFwProfit(), 0.001);
        assertEquals("2024-03-01", result.fromDate());
        assertEquals("2025-03-01", result.toDate());
    }

    @Test
    public void importArchivesOriginalPresetAndEmbedsLazilyOnResolve() throws Exception {
        Path mtDir = tempFolder.newFolder("mt5preset").toPath();
        Files.writeString(mtDir.resolve("OptimizationReport.xml"), spreadsheet(
                "<Title>EA AUDCAD,M5 2024.08.01-2025.08.01</Title>",
                row("Pass", "Result", "Profit", "Equity DD %", "Trades", "Inp_Grid_Step"),
                row("7", "1.93", "1652.58", "7.1271", "350", "350")));
        Files.writeString(mtDir.resolve("tester_optimization.ini"),
                "[Tester]\r\nSymbol=AUDCAD\r\nPeriod=M5\r\nFromDate=2024.08.01\r\n"
                        + "ToDate=2025.08.01\r\nExpertParameters=Original.set\r\nOptimization=2\r\n");
        Path profiles = mtDir.resolve("MQL5").resolve("Profiles").resolve("Tester");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("Original.set"),
                "Inp_Grid_Step=500||1||1||6000||Y\n"
                        + "Inp_Spread_Max=40||4||1||400||N\n");

        Mt5OptimizationImportService.ImportResult result =
                Mt5OptimizationImportService.importFromMt5Install(mtDir);
        CombinedPass pass = result.passes().get(0);

        // No bulk embedding anymore: the first resolve works from the archived
        // optimization snapshot with the pass's reported values applied.
        PassPresetResolver.Resolution resolution =
                PassPresetResolver.resolve(pass.getBacktestPass(), "");
        assertEquals(PassPresetResolver.Fidelity.OPTIMIZATION_BASE, resolution.fidelity());
        assertEquals("350", valueOf(resolution, "Inp_Grid_Step"));
        assertEquals("40", valueOf(resolution, "Inp_Spread_Max"));

        // Resolving embeds the concrete setfile lazily into THIS pass only, so a
        // second resolve is exact and self-contained.
        assertTrue(pass.getBacktestPass().hasParameterSetSnapshot());
        PassPresetResolver.Resolution second =
                PassPresetResolver.resolve(pass.getBacktestPass(), "");
        assertEquals(PassPresetResolver.Fidelity.EMBEDDED_PASS, second.fidelity());
        assertEquals("350", valueOf(second, "Inp_Grid_Step"));
        assertEquals("40", valueOf(second, "Inp_Spread_Max"));
        assertTrue(result.message().contains("Original-Optimierungsset wurde im Snapshot archiviert"));
    }

    @Test
    public void importWithoutOriginalPresetDoesNotClaimExactReproducibility() throws Exception {
        Path mtDir = tempFolder.newFolder("mt5nopreset").toPath();
        Files.writeString(mtDir.resolve("OptimizationReport.xml"), spreadsheet(
                "<Title>EA AUDCAD,M5 2024.08.01-2025.08.01</Title>",
                row("Pass", "Result", "Profit", "Equity DD %", "Trades", "Inp_Grid_Step"),
                row("7", "1.93", "1652.58", "7.1271", "350", "350")));

        Mt5OptimizationImportService.ImportResult result =
                Mt5OptimizationImportService.importFromMt5Install(mtDir);

        assertFalse(result.passes().get(0).getBacktestPass().hasParameterSetSnapshot());
        assertTrue(result.message().contains("nicht exakt reproduzierbar"));
    }

    @Test
    public void datesFallBackToTesterOptimizationIni() throws Exception {
        Path mtDir = tempFolder.newFolder("mt5ini").toPath();
        Files.writeString(mtDir.resolve("OptimizationReport.xml"), spreadsheet(
                row("Pass", "Result", "Profit", "Equity DD %", "Trades", "Inp_Grid_Step"),
                row("1", "1.0", "100", "2.0", "50", "350")));
        Files.writeString(mtDir.resolve("tester_optimization.ini"),
                "[Tester]\r\nFromDate=2023.01.15\r\nToDate=2024.01.15\r\n");

        Mt5OptimizationImportService.ImportResult result =
                Mt5OptimizationImportService.importFromMt5Install(mtDir);

        assertEquals("2023-01-15", result.fromDate());
        assertEquals("2024-01-15", result.toDate());
    }

    @Test
    public void normalizeIsoDateAcceptsDotsAndDashes() {
        assertEquals("2024-08-01", Mt5OptimizationImportService.normalizeIsoDate("2024.08.01"));
        assertEquals("2024-08-01", Mt5OptimizationImportService.normalizeIsoDate("2024-08-01"));
        assertEquals("", Mt5OptimizationImportService.normalizeIsoDate(""));
        assertEquals("", Mt5OptimizationImportService.normalizeIsoDate(null));
    }

    @Test
    public void resolveMainReportPrefersXmlThenHtm() throws Exception {
        Path mtDir = tempFolder.newFolder("resolve").toPath();
        assertNull(Mt5OptimizationImportService.resolveMainReport(mtDir));

        Path htm = mtDir.resolve("OptimizationReport.htm");
        Files.writeString(htm, "<html></html>");
        assertEquals(htm, Mt5OptimizationImportService.resolveMainReport(mtDir));

        Path xml = mtDir.resolve("OptimizationReport.xml");
        Files.writeString(xml, spreadsheet(
                row("Pass", "Profit", "Trades", "Inp_X"),
                row("1", "10", "5", "1")));
        assertEquals(xml, Mt5OptimizationImportService.resolveMainReport(mtDir));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingReportFailsClearly() throws Exception {
        Path mtDir = tempFolder.newFolder("empty").toPath();
        Mt5OptimizationImportService.importFromMt5Install(mtDir);
    }

    private static String spreadsheet(String... parts) {
        StringBuilder body = new StringBuilder();
        for (String part : parts) {
            body.append(part);
        }
        return "<?xml version=\"1.0\"?><Workbook xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">"
                + "<Worksheet><Table>" + body + "</Table></Worksheet></Workbook>";
    }

    private static String row(String... cells) {
        StringBuilder sb = new StringBuilder("<Row>");
        for (String cell : cells) {
            sb.append("<Cell><Data ss:Type=\"String\">").append(cell).append("</Data></Cell>");
        }
        return sb.append("</Row>").toString();
    }

    private static String valueOf(PassPresetResolver.Resolution resolution, String name) {
        return resolution.parameters().stream()
                .filter(parameter -> name.equals(parameter.getName()))
                .map(parameter -> parameter.getValue())
                .findFirst()
                .orElse(null);
    }
}
