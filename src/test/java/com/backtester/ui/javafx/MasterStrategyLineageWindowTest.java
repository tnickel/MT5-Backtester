package com.backtester.ui.javafx;

import com.backtester.workflow.MasterStrategyEntry;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MasterStrategyLineageWindowTest {

    @Test
    public void passZeroIsARealOptimizerPassAndOnlyNegativeMeansCarryForward() {
        MasterStrategyEntry firstPass = new MasterStrategyEntry();
        firstPass.setSourcePassNumber(0);
        MasterStrategyEntry carried = new MasterStrategyEntry();
        carried.setSourcePassNumber(-1);

        assertEquals("#0", firstPass.sourcePassLabel());
        assertEquals("Master weitergetragen", carried.sourcePassLabel());
    }

    @Test
    public void rejectedLatestMeasurementIsNotPresentedAsTheCurrentMaster() {
        MasterStrategyEntry confirmed = entry(1, "Bestätigte Basis", 4.0,
                MasterStrategyEntry.Verdict.UNBEKANNT);
        MasterStrategyEntry rejected = entry(2, "Verworfener Versuch", 0.8,
                MasterStrategyEntry.Verdict.SCHLECHTER);

        String status = MasterStrategyLineageWindow.masterStatusText(
                List.of(confirmed, rejected), confirmed.getSequence());

        assertTrue(status.contains("BESTÄTIGTER MASTER: #1"));
        assertTrue(status.contains("Letzte Messung: #2 · schlechter · nicht übernommen"));
        assertFalse(status.contains("BESTÄTIGTER MASTER: #2"));
        assertTrue(MasterStrategyLineageWindow.entryStatusText(rejected, 1)
                .contains("nicht übernommen (Master #1)"));
        assertTrue(MasterStrategyLineageWindow.entryStatusText(confirmed, 1)
                .contains("BESTÄTIGTER MASTER #1"));
    }

    private static MasterStrategyEntry entry(int sequence, String stage, double ratio,
                                             MasterStrategyEntry.Verdict verdict) {
        MasterStrategyEntry entry = new MasterStrategyEntry();
        entry.setSequence(sequence);
        entry.setStageTaskName(stage);
        entry.setBacktestSucceeded(true);
        entry.setReturnToDrawdown(ratio);
        entry.setVerdict(verdict);
        return entry;
    }
}
