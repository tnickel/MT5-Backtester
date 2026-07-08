package com.backtester.report;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import com.backtester.config.EaParameter;

public class MassiveCoverageTest {

    // --- GROUP 1: BacktestResult (Tests 1 to 50) ---

    @Test
    public void test001_setExpertNormal() {
        BacktestResult res = new BacktestResult();
        res.setExpert("MySuperEA");
        assertEquals("MySuperEA", res.getExpert());
    }

    @Test
    public void test002_setExpertNull() {
        BacktestResult res = new BacktestResult();
        res.setExpert(null);
        assertNull(res.getExpert());
    }

    @Test
    public void test003_setExpertEmpty() {
        BacktestResult res = new BacktestResult();
        res.setExpert("");
        assertEquals("", res.getExpert());
    }

    @Test
    public void test004_setExpertSpecialChars() {
        BacktestResult res = new BacktestResult();
        res.setExpert("EA_v1.0-Beta!");
        assertEquals("EA_v1.0-Beta!", res.getExpert());
    }

    @Test
    public void test005_setSymbolNormal() {
        BacktestResult res = new BacktestResult();
        res.setSymbol("EURUSD");
        assertEquals("EURUSD", res.getSymbol());
    }

    @Test
    public void test006_setSymbolNull() {
        BacktestResult res = new BacktestResult();
        res.setSymbol(null);
        assertNull(res.getSymbol());
    }

    @Test
    public void test007_setSymbolEmpty() {
        BacktestResult res = new BacktestResult();
        res.setSymbol("");
        assertEquals("", res.getSymbol());
    }

    @Test
    public void test008_setPeriodNormal() {
        BacktestResult res = new BacktestResult();
        res.setPeriod("H1");
        assertEquals("H1", res.getPeriod());
    }

    @Test
    public void test009_setPeriodNull() {
        BacktestResult res = new BacktestResult();
        res.setPeriod(null);
        assertNull(res.getPeriod());
    }

    @Test
    public void test010_setPeriodEmpty() {
        BacktestResult res = new BacktestResult();
        res.setPeriod("");
        assertEquals("", res.getPeriod());
    }

    @Test
    public void test011_setOutputDirectoryNormal() {
        BacktestResult res = new BacktestResult();
        res.setOutputDirectory("/user/desktop/reports");
        assertEquals("/user/desktop/reports", res.getOutputDirectory());
    }

    @Test
    public void test012_setOutputDirectoryNull() {
        BacktestResult res = new BacktestResult();
        res.setOutputDirectory(null);
        assertNull(res.getOutputDirectory());
    }

    @Test
    public void test013_setOutputDirectoryEmpty() {
        BacktestResult res = new BacktestResult();
        res.setOutputDirectory("");
        assertEquals("", res.getOutputDirectory());
    }

    @Test
    public void test014_setSuccessTrue() {
        BacktestResult res = new BacktestResult();
        res.setSuccess(true);
        assertTrue(res.isSuccess());
    }

    @Test
    public void test015_setSuccessFalse() {
        BacktestResult res = new BacktestResult();
        res.setSuccess(false);
        assertFalse(res.isSuccess());
    }

    @Test
    public void test016_setMessageNormal() {
        BacktestResult res = new BacktestResult();
        res.setMessage("Completed successfully");
        assertEquals("Completed successfully", res.getMessage());
    }

    @Test
    public void test017_setMessageNull() {
        BacktestResult res = new BacktestResult();
        res.setMessage(null);
        assertNull(res.getMessage());
    }

    @Test
    public void test018_setMessageEmpty() {
        BacktestResult res = new BacktestResult();
        res.setMessage("");
        assertEquals("", res.getMessage());
    }

    @Test
    public void test019_setUsedDefaultConfigTrue() {
        BacktestResult res = new BacktestResult();
        res.setUsedDefaultConfig(true);
        assertTrue(res.isUsedDefaultConfig());
    }

    @Test
    public void test020_setUsedDefaultConfigFalse() {
        BacktestResult res = new BacktestResult();
        res.setUsedDefaultConfig(false);
        assertFalse(res.isUsedDefaultConfig());
    }

    @Test
    public void test021_setConfigInfoNormal() {
        BacktestResult res = new BacktestResult();
        res.setConfigInfo("Custom settings");
        assertEquals("Custom settings", res.getConfigInfo());
    }

    @Test
    public void test022_setConfigInfoNull() {
        BacktestResult res = new BacktestResult();
        res.setConfigInfo(null);
        assertNull(res.getConfigInfo());
    }

    @Test
    public void test023_setTotalProfitPositive() {
        BacktestResult res = new BacktestResult();
        res.setTotalProfit(1234.56);
        assertEquals(1234.56, res.getTotalProfit(), 0.0001);
    }

    @Test
    public void test024_setTotalProfitNegative() {
        BacktestResult res = new BacktestResult();
        res.setTotalProfit(-500.20);
        assertEquals(-500.20, res.getTotalProfit(), 0.0001);
    }

    @Test
    public void test025_setTotalProfitNaN() {
        BacktestResult res = new BacktestResult();
        res.setTotalProfit(Double.NaN);
        assertTrue(Double.isNaN(res.getTotalProfit()));
    }

    @Test
    public void test026_setGrossProfitAndLoss() {
        BacktestResult res = new BacktestResult();
        res.setGrossProfit(2000.0);
        res.setGrossLoss(-800.0);
        assertEquals(2000.0, res.getGrossProfit(), 0.0001);
        assertEquals(-800.0, res.getGrossLoss(), 0.0001);
    }

    @Test
    public void test027_setTotalTradesPositive() {
        BacktestResult res = new BacktestResult();
        res.setTotalTrades(150);
        assertEquals(150, res.getTotalTrades());
    }

    @Test
    public void test028_setTotalTradesZero() {
        BacktestResult res = new BacktestResult();
        res.setTotalTrades(0);
        assertEquals(0, res.getTotalTrades());
    }

    @Test
    public void test029_setWinRateNormal() {
        BacktestResult res = new BacktestResult();
        res.setWinRate(65.4);
        assertEquals(65.4, res.getWinRate(), 0.0001);
    }

    @Test
    public void test030_setWinRateBoundary() {
        BacktestResult res = new BacktestResult();
        res.setWinRate(100.0);
        assertEquals(100.0, res.getWinRate(), 0.0001);
    }

    @Test
    public void test031_setMaxDrawdownMetrics() {
        BacktestResult res = new BacktestResult();
        res.setMaxDrawdown(12.5);
        res.setMaxDrawdownAbsolute(1250.0);
        res.setMaxDrawdownPercent(12.5);
        assertEquals(12.5, res.getMaxDrawdown(), 0.0001);
        assertEquals(1250.0, res.getMaxDrawdownAbsolute(), 0.0001);
        assertEquals(12.5, res.getMaxDrawdownPercent(), 0.0001);
    }

    @Test
    public void test032_setBalanceDrawdownMetrics() {
        BacktestResult res = new BacktestResult();
        res.setBalanceDrawdown(8.4);
        res.setBalanceDrawdownAbsolute(840.0);
        assertEquals(8.4, res.getBalanceDrawdown(), 0.0001);
        assertEquals(840.0, res.getBalanceDrawdownAbsolute(), 0.0001);
    }

    @Test
    public void test033_setProfitFactor() {
        BacktestResult res = new BacktestResult();
        res.setProfitFactor(2.15);
        assertEquals(2.15, res.getProfitFactor(), 0.0001);
    }

    @Test
    public void test034_setSharpeRatio() {
        BacktestResult res = new BacktestResult();
        res.setSharpeRatio(1.85);
        assertEquals(1.85, res.getSharpeRatio(), 0.0001);
    }

    @Test
    public void test035_setRecoveryFactor() {
        BacktestResult res = new BacktestResult();
        res.setRecoveryFactor(4.5);
        assertEquals(4.5, res.getRecoveryFactor(), 0.0001);
    }

    @Test
    public void test036_setExpectedPayoff() {
        BacktestResult res = new BacktestResult();
        res.setExpectedPayoff(15.2);
        assertEquals(15.2, res.getExpectedPayoff(), 0.0001);
    }

    @Test
    public void test037_setPositions() {
        BacktestResult res = new BacktestResult();
        res.setShortPositions(40);
        res.setLongPositions(60);
        assertEquals(40, res.getShortPositions());
        assertEquals(60, res.getLongPositions());
    }

    @Test
    public void test038_setProfitAndLossTrades() {
        BacktestResult res = new BacktestResult();
        res.setProfitTrades(70);
        res.setLossTrades(30);
        assertEquals(70, res.getProfitTrades());
        assertEquals(30, res.getLossTrades());
    }

    @Test
    public void test039_setInitialDeposit() {
        BacktestResult res = new BacktestResult();
        res.setInitialDeposit(10000.0);
        assertEquals(10000.0, res.getInitialDeposit(), 0.0001);
    }

    @Test
    public void test040_setFinalBalance() {
        BacktestResult res = new BacktestResult();
        res.setFinalBalance(11500.0);
        assertEquals(11500.0, res.getFinalBalance(), 0.0001);
    }

    @Test
    public void test041_setLargestWinAndLoss() {
        BacktestResult res = new BacktestResult();
        res.setLargestWin(500.0);
        res.setLargestLoss(-300.0);
        assertEquals(500.0, res.getLargestWin(), 0.0001);
        assertEquals(-300.0, res.getLargestLoss(), 0.0001);
    }

    @Test
    public void test042_setAverageWinAndLoss() {
        BacktestResult res = new BacktestResult();
        res.setAverageWin(45.5);
        res.setAverageLoss(-32.1);
        assertEquals(45.5, res.getAverageWin(), 0.0001);
        assertEquals(-32.1, res.getAverageLoss(), 0.0001);
    }

    @Test
    public void test043_setDbId() {
        BacktestResult res = new BacktestResult();
        res.setDbId(999);
        assertEquals(999, res.getDbId());
    }

    @Test
    public void test044_getEquityHistoryNotNullInitially() {
        BacktestResult res = new BacktestResult();
        assertNotNull(res.getEquityHistory());
        assertEquals(0, res.getEquityHistory().size());
    }

    @Test
    public void test045_setEquityHistoryList() {
        BacktestResult res = new BacktestResult();
        List<double[]> list = new ArrayList<>();
        list.add(new double[]{1.0, 10000.0, 10000.0});
        list.add(new double[]{2.0, 10100.0, 10050.0});
        res.setEquityHistory(list);
        assertEquals(2, res.getEquityHistory().size());
        assertEquals(10100.0, res.getEquityHistory().get(1)[1], 0.0001);
    }

    @Test
    public void test046_equityHistoryClear() {
        BacktestResult res = new BacktestResult();
        List<double[]> list = new ArrayList<>();
        list.add(new double[]{1.0, 10000.0, 10000.0});
        res.setEquityHistory(list);
        res.getEquityHistory().clear();
        assertEquals(0, res.getEquityHistory().size());
    }

    @Test
    public void test047_equityHistoryElements() {
        BacktestResult res = new BacktestResult();
        List<double[]> list = new ArrayList<>();
        double[] row = new double[]{10.0, 15000.0, 14900.0};
        list.add(row);
        res.setEquityHistory(list);
        assertSame(row, res.getEquityHistory().get(0));
    }

    @Test
    public void test048_toStringNormal() {
        BacktestResult res = new BacktestResult();
        res.setExpert("EA");
        res.setSymbol("EURUSD");
        res.setPeriod("M15");
        res.setTotalProfit(500.0);
        res.setTotalTrades(20);
        res.setWinRate(70.0);
        res.setMaxDrawdown(5.5);
        res.setProfitFactor(1.5);
        String s = res.toString();
        assertTrue(s.contains("expert='EA'"));
        assertTrue(s.contains("symbol='EURUSD'"));
        assertTrue(s.contains("profit="));
        assertTrue(s.contains("drawdown="));
    }

    @Test
    public void test049_toStringEmpty() {
        BacktestResult res = new BacktestResult();
        String s = res.toString();
        assertTrue(s.contains("expert=''"));
        assertTrue(s.contains("symbol=''"));
    }

    @Test
    public void test050_toStringNegative() {
        BacktestResult res = new BacktestResult();
        res.setTotalProfit(-123.45);
        res.setMaxDrawdown(15.75);
        String s = res.toString();
        assertTrue(s.contains("profit="));
        assertTrue(s.contains("drawdown="));
    }

    // --- GROUP 2: EaParameter (Tests 51 to 80) ---

    @Test
    public void test051_eaParamNameNormal() {
        EaParameter p = new EaParameter();
        p.setName("InpLots");
        assertEquals("InpLots", p.getName());
    }

    @Test
    public void test052_eaParamNameNull() {
        EaParameter p = new EaParameter();
        p.setName(null);
        assertNull(p.getName());
    }

    @Test
    public void test053_eaParamValueNormal() {
        EaParameter p = new EaParameter();
        p.setValue("0.1");
        assertEquals("0.1", p.getValue());
    }

    @Test
    public void test054_eaParamValueNull() {
        EaParameter p = new EaParameter();
        p.setValue(null);
        assertNull(p.getValue());
    }

    @Test
    public void test055_eaParamDefaultValueNormal() {
        EaParameter p = new EaParameter();
        p.setDefaultValue("0.2");
        assertEquals("0.2", p.getDefaultValue());
    }

    @Test
    public void test056_eaParamDefaultValueNull() {
        EaParameter p = new EaParameter();
        p.setDefaultValue(null);
        assertNull(p.getDefaultValue());
    }

    @Test
    public void test057_eaParamSectionNormal() {
        EaParameter p = new EaParameter();
        p.setSection("Trading Settings");
        assertEquals("Trading Settings", p.getSection());
    }

    @Test
    public void test058_eaParamSectionNull() {
        EaParameter p = new EaParameter();
        p.setSection(null);
        assertNull(p.getSection());
    }

    @Test
    public void test059_eaParamOptimizeStartNormal() {
        EaParameter p = new EaParameter();
        p.setOptimizeStart("1.0");
        assertEquals("1.0", p.getOptimizeStart());
    }

    @Test
    public void test060_eaParamOptimizeStartNull() {
        EaParameter p = new EaParameter();
        p.setOptimizeStart(null);
        assertNull(p.getOptimizeStart());
    }

    @Test
    public void test061_eaParamOptimizeStepNormal() {
        EaParameter p = new EaParameter();
        p.setOptimizeStep("0.1");
        assertEquals("0.1", p.getOptimizeStep());
    }

    @Test
    public void test062_eaParamOptimizeStepNull() {
        EaParameter p = new EaParameter();
        p.setOptimizeStep(null);
        assertNull(p.getOptimizeStep());
    }

    @Test
    public void test063_eaParamOptimizeEndNormal() {
        EaParameter p = new EaParameter();
        p.setOptimizeEnd("5.0");
        assertEquals("5.0", p.getOptimizeEnd());
    }

    @Test
    public void test064_eaParamOptimizeEndNull() {
        EaParameter p = new EaParameter();
        p.setOptimizeEnd(null);
        assertNull(p.getOptimizeEnd());
    }

    @Test
    public void test065_eaParamOptimizeEnabled() {
        EaParameter p = new EaParameter();
        p.setOptimizeEnabled(true);
        assertTrue(p.isOptimizeEnabled());
        p.setOptimizeEnabled(false);
        assertFalse(p.isOptimizeEnabled());
    }

    @Test
    public void test066_eaParamStringType() {
        EaParameter p = new EaParameter();
        p.setStringType(true);
        assertTrue(p.isStringType());
        p.setStringType(false);
        assertFalse(p.isStringType());
    }

    @Test
    public void test067_eaParamRawLineNormal() {
        EaParameter p = new EaParameter();
        p.setRawLine("InpLots=0.1||0.05||0.01||0.5||N");
        assertEquals("InpLots=0.1||0.05||0.01||0.5||N", p.getRawLine());
    }

    @Test
    public void test068_eaParamRawLineNull() {
        EaParameter p = new EaParameter();
        p.setRawLine(null);
        assertNull(p.getRawLine());
    }

    @Test
    public void test069_eaParamConstructor() {
        EaParameter p = new EaParameter("InpRisk", "2.0");
        assertEquals("InpRisk", p.getName());
        assertEquals("2.0", p.getValue());
        assertEquals("2.0", p.getDefaultValue());
    }

    @Test
    public void test070_eaParamDefaultConstructorState() {
        EaParameter p = new EaParameter();
        assertNull(p.getName());
        assertNull(p.getValue());
        assertNull(p.getDefaultValue());
        assertNull(p.getSection());
        assertEquals("", p.getOptimizeStart());
        assertEquals("", p.getOptimizeStep());
        assertEquals("", p.getOptimizeEnd());
        assertFalse(p.isOptimizeEnabled());
        assertFalse(p.isStringType());
        assertEquals("", p.getRawLine());
    }

    @Test
    public void test071_eaParamIsModifiedTrue() {
        EaParameter p = new EaParameter("InpStep", "10");
        p.setValue("15");
        assertTrue(p.isModified());
    }

    @Test
    public void test072_eaParamIsModifiedFalse() {
        EaParameter p = new EaParameter("InpStep", "10");
        p.setValue("10");
        assertFalse(p.isModified());
    }

    @Test
    public void test073_eaParamResetToDefault() {
        EaParameter p = new EaParameter("InpStep", "10");
        p.setValue("15");
        p.resetToDefault();
        assertEquals("10", p.getValue());
        assertFalse(p.isModified());
    }

    @Test
    public void test074_eaParamResetToDefaultNotModified() {
        EaParameter p = new EaParameter("InpStep", "10");
        p.resetToDefault();
        assertEquals("10", p.getValue());
    }

    @Test
    public void test075_toSetFileLineOptimizeEnabled() {
        EaParameter p = new EaParameter("InpA", "5");
        p.setOptimizeStart("1");
        p.setOptimizeStep("2");
        p.setOptimizeEnd("10");
        p.setOptimizeEnabled(true);
        assertEquals("InpA=5||1||2||10||Y", p.toSetFileLine());
    }

    @Test
    public void test076_toSetFileLineOptimizeDisabled() {
        EaParameter p = new EaParameter("InpA", "5");
        p.setOptimizeStart("1");
        p.setOptimizeStep("2");
        p.setOptimizeEnd("10");
        p.setOptimizeEnabled(false);
        assertEquals("InpA=5||1||2||10||N", p.toSetFileLine());
    }

    @Test
    public void test077_toSetFileLineStringType() {
        EaParameter p = new EaParameter("InpComment", "TestEA");
        p.setStringType(true);
        assertEquals("InpComment=TestEA", p.toSetFileLine());
    }

    @Test
    public void test078_toggleOptimizeEnabled() {
        EaParameter p = new EaParameter("InpB", "2");
        p.setOptimizeEnabled(false);
        assertFalse(p.isOptimizeEnabled());
        p.setOptimizeEnabled(true);
        assertTrue(p.isOptimizeEnabled());
    }

    @Test
    public void test079_toSetFileLineSpecialValues() {
        EaParameter p = new EaParameter("InpName", "EA_Param=Value");
        p.setStringType(true);
        assertEquals("InpName=EA_Param=Value", p.toSetFileLine());
    }

    @Test
    public void test080_toSetFileLineEmptyValue() {
        EaParameter p = new EaParameter("InpEmpty", "");
        p.setStringType(true);
        assertEquals("InpEmpty=", p.toSetFileLine());
    }

    // --- GROUP 3: ScoreWeights & Score Calculations (Tests 81 to 100) ---

    @Test
    public void test081_scoreWeightsDefaultsTotal() {
        OptimizationResult.ScoreWeights w = OptimizationResult.ScoreWeights.defaults();
        double total = w.wBtProfit + w.wFwProfit + w.wConsistency + w.wRisk +
                       w.wEquityConsist + w.wSampleSize + w.wFwTrades + w.wRecovery;
        assertEquals(140.0, total, 0.001);
        assertEquals(total, w.total(), 0.001);
    }

    @Test
    public void test082_scoreWeightsDefaultsValues() {
        OptimizationResult.ScoreWeights w = OptimizationResult.ScoreWeights.defaults();
        assertEquals(15.0, w.wBtProfit, 0.001);
        assertEquals(15.0, w.wFwProfit, 0.001);
        assertEquals(10.0, w.wConsistency, 0.001);
        assertEquals(10.0, w.wRisk, 0.001);
        assertEquals(10.0, w.wEquityConsist, 0.001);
        assertEquals(25.0, w.wSampleSize, 0.001);
        assertEquals(30.0, w.wFwTrades, 0.001);
        assertEquals(25.0, w.wRecovery, 0.001);
    }

    @Test
    public void test083_computeScoreDefaultWeights() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(500.0);
        bt.setTotalTrades(100);
        bt.setProfitFactor(2.0);
        bt.setDrawdownPercent(5.0);
        bt.setSharpeRatio(1.5);
        bt.setRecoveryFactor(3.0);

        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setProfit(200.0);
        fw.setTotalTrades(50);
        fw.setProfitFactor(1.8);
        fw.setDrawdownPercent(6.0);
        fw.setSharpeRatio(1.2);
        fw.setRecoveryFactor(2.5);

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);
        r.addForwardPass(fw);

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(true);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertTrue(cp.getScore() > 0.0);
    }

    @Test
    public void test084_computeScoreNullForward() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(500.0);
        bt.setTotalTrades(100);
        bt.setProfitFactor(2.0);
        bt.setDrawdownPercent(5.0);
        bt.setSharpeRatio(1.5);
        bt.setRecoveryFactor(3.0);

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(false);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertTrue(cp.getScore() > 0.0);
    }

    @Test
    public void test085_scoreFwTradesPiecewiseScalingHigh() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setTotalTrades(200);
        bt.setProfit(100.0);
        
        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setTotalTrades(1000); // Should yield high sFwTrades score
        fw.setProfit(100.0);

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);
        r.addForwardPass(fw);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wFwTrades = 100.0; // Focus only on FwTrades
        // Make sure all other weights are 0 so we isolate FwTrades
        w.wBtProfit = 0; w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0;
        w.wEquityConsist = 0; w.wSampleSize = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(true, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertEquals(100.0, cp.getScore(), 0.01);
    }

    @Test
    public void test086_scoreFwTradesPiecewiseScalingLow() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setTotalTrades(200);
        bt.setProfit(100.0);

        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setTotalTrades(100); // Low trade count
        fw.setProfit(100.0);

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);
        r.addForwardPass(fw);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wFwTrades = 100.0;
        w.wBtProfit = 0; w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0;
        w.wEquityConsist = 0; w.wSampleSize = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(true, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertTrue(cp.getScore() < 20.0); // Piecewise linear mapping at 100 should be 0.0
    }

    @Test
    public void test087_scoreConsistencyHigh() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(100.0);
        bt.setRecoveryFactor(2.0);
        
        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setProfit(100.0); // Exact match -> consistency ratio = 1.0
        fw.setRecoveryFactor(2.0);

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);
        r.addForwardPass(fw);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wConsistency = 100.0;
        w.wBtProfit = 0; w.wFwProfit = 0; w.wRisk = 0; w.wEquityConsist = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(true, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertEquals(100.0, cp.getScore(), 0.001);
    }

    @Test
    public void test088_scoreConsistencyLow() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(100.0);
        bt.setRecoveryFactor(10.0);
        
        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setProfit(-10.0); // Negative profit in forward -> poor consistency
        fw.setRecoveryFactor(1.0);

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);
        r.addForwardPass(fw);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wConsistency = 100.0;
        w.wBtProfit = 0; w.wFwProfit = 0; w.wRisk = 0; w.wEquityConsist = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(true, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertTrue(cp.getScore() < 50.0);
    }

    @Test
    public void test089_scoreProfitFactorHigh() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setBalance(10000.0);
        bt.setProfit(3000.0);
        bt.setProfitFactor(5.0); // High PF

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 100.0;
        w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0; w.wEquityConsist = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(false, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertTrue(cp.getScore() > 80.0);
    }

    @Test
    public void test090_scoreProfitFactorLow() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setBalance(10000.0);
        bt.setProfit(100.0);
        bt.setProfitFactor(0.8); // Low PF

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 100.0;
        w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0; w.wEquityConsist = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(false, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertTrue(cp.getScore() < 20.0);
    }

    @Test
    public void test091_scoreWeightsProfitOnly() {
        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 100.0;
        w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0; w.wEquityConsist = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;
        assertEquals(100.0, w.total(), 0.001);
    }

    @Test
    public void test092_scoreWeightsRecoveryOnly() {
        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 0; w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0;
        w.wEquityConsist = 0; w.wSampleSize = 0; w.wFwTrades = 0;
        w.wRecovery = 100.0;
        assertEquals(100.0, w.total(), 0.001);
    }

    @Test
    public void test093_scoreWeightsFwTradesOnly() {
        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 0; w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0;
        w.wEquityConsist = 0; w.wSampleSize = 0;
        w.wFwTrades = 100.0;
        w.wRecovery = 0;
        assertEquals(100.0, w.total(), 0.001);
    }

    @Test
    public void test094_scoreNegativeProfit() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(-500.0);
        
        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 100.0;
        w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0; w.wEquityConsist = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(false, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertEquals(0.0, cp.getScore(), 0.001); // Negative profit should yield 0 score for profit
    }

    @Test
    public void test095_scoreNaNProfit() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(Double.NaN);
        
        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 100.0;
        w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0; w.wEquityConsist = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(false, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        assertEquals(50.0, cp.getScore(), 0.001);
    }

    @Test
    public void test096_scoreWeightsSettersAndGetters() {
        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 10.0;
        w.wFwProfit = 15.0;
        w.wConsistency = 5.0;
        w.wRisk = 20.0;
        w.wEquityConsist = 8.0;
        w.wSampleSize = 12.0;
        w.wFwTrades = 21.0;
        w.wRecovery = 9.0;

        assertEquals(10.0, w.wBtProfit, 0.001);
        assertEquals(15.0, w.wFwProfit, 0.001);
        assertEquals(5.0, w.wConsistency, 0.001);
        assertEquals(20.0, w.wRisk, 0.001);
        assertEquals(8.0, w.wEquityConsist, 0.001);
        assertEquals(12.0, w.wSampleSize, 0.001);
        assertEquals(21.0, w.wFwTrades, 0.001);
        assertEquals(9.0, w.wRecovery, 0.001);
        assertEquals(100.0, w.total(), 0.001);
    }

    @Test
    public void test097_scoreSharpeDeterministicAcrossPassNumbers() {
        // Anti-Curvefitting-Invariante: identische Kennzahlen müssen unabhängig
        // von der Pass-Nummer denselben Score ergeben. (Die frühere synthetische
        // "Equity-Konsistenz" hing per RNG-Seed an der Pass-Nummer.)
        OptimizationResult.Pass bt1 = new OptimizationResult.Pass();
        bt1.setPassNumber(1);
        bt1.setProfit(500.0);
        bt1.setTotalTrades(200);
        bt1.setProfitFactor(1.8);
        bt1.setSharpeRatio(1.1);
        bt1.setRecoveryFactor(3.0);

        OptimizationResult.Pass bt2 = new OptimizationResult.Pass();
        bt2.setPassNumber(99999); // andere Pass-Nummer, sonst identisch
        bt2.setProfit(500.0);
        bt2.setTotalTrades(200);
        bt2.setProfitFactor(1.8);
        bt2.setSharpeRatio(1.1);
        bt2.setRecoveryFactor(3.0);

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt1);
        r.addPass(bt2);

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(false);
        assertEquals(2, list.size());
        assertEquals(list.get(0).getScore(), list.get(1).getScore(), 0.0001);
    }

    @Test
    public void test098_scoreSharpeMonotonic() {
        // Höhere (echte) Sharpe Ratio muss bei isolierter Sharpe-Säule einen
        // höheren Score ergeben.
        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wEquityConsist = 100.0;
        w.wBtProfit = 0; w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;

        OptimizationResult.Pass low = new OptimizationResult.Pass();
        low.setPassNumber(1);
        low.setProfit(100.0);
        low.setSharpeRatio(0.2);

        OptimizationResult rLow = new OptimizationResult();
        rLow.addPass(low);
        double scoreLow = rLow.buildCombinedPasses(false, w).get(0).getScore();

        OptimizationResult.Pass high = new OptimizationResult.Pass();
        high.setPassNumber(1);
        high.setProfit(100.0);
        high.setSharpeRatio(1.5);

        OptimizationResult rHigh = new OptimizationResult();
        rHigh.addPass(high);
        double scoreHigh = rHigh.buildCombinedPasses(false, w).get(0).getScore();

        assertTrue("Score mit Sharpe 1.5 muss höher sein als mit Sharpe 0.2", scoreHigh > scoreLow);
    }

    @Test
    public void test099_scoreEquityConsistency() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(100.0);

        OptimizationResult r = new OptimizationResult();
        r.addPass(bt);

        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wEquityConsist = 100.0;
        w.wBtProfit = 0; w.wFwProfit = 0; w.wConsistency = 0; w.wRisk = 0;
        w.wSampleSize = 0; w.wFwTrades = 0; w.wRecovery = 0;

        List<OptimizationResult.CombinedPass> list = r.buildCombinedPasses(false, w);
        assertEquals(1, list.size());
        OptimizationResult.CombinedPass cp = list.get(0);
        // Ohne gemessene Sharpe Ratio (0.0) trägt die Säule nichts bei
        assertEquals(0.0, cp.getScore(), 0.001);
    }

    @Test
    public void test100_combinedPassDescription() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(7);
        OptimizationResult.CombinedPass cp = new OptimizationResult.CombinedPass(bt, null, 75.5, 1.2, "Some details");
        assertEquals("Some details", cp.getScoreDetails());
        assertEquals(75.5, cp.getScore(), 0.001);
        assertEquals(1.2, cp.getConsistency(), 0.001);
    }
}
