package com.backtester.ui.javafx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SingleBacktestHelperModelParseTest {

    @Test
    public void parsesOhclM1Variants() {
        assertEquals(1, SingleBacktestHelper.parseModelToId("1 minute OHLC"));
        assertEquals(1, SingleBacktestHelper.parseModelToId("1 Minute OHLC"));
        assertEquals(1, SingleBacktestHelper.parseModelToId("OHLC M1 (Every tick based on OHLC M1)"));
        assertEquals(0, SingleBacktestHelper.parseModelToId("Every tick"));
        assertEquals(4, SingleBacktestHelper.parseModelToId("Every tick (real ticks)"));
    }
}
