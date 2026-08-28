package com.backtester.workflow;

import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ClusterIdentityTest {

    @Test
    public void stampKeepsCousinsUpToPooledCapNotJustTenPasses() {
        List<CombinedPass> input = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            CombinedPass pass = pass(i, 100.0 - i);
            input.add(pass);
        }

        List<CombinedPass> stamped = ClusterIdentity.stampInOrder(input);

        assertEquals(30, stamped.size());
        Set<String> ids = new HashSet<>();
        for (CombinedPass pass : stamped) {
            assertTrue(ClusterIdentity.hasId(pass));
            ids.add(pass.getClusterId());
        }
        assertEquals(ClusterIdentity.MAX_CLUSTERS, ids.size());
        assertTrue(ids.contains("B1"));
        assertTrue(ids.contains("B10"));
    }

    @Test
    public void stampScoreCapsAtMaxPooledStrategies() {
        List<CombinedPass> input = new ArrayList<>();
        for (int i = 1; i <= ClusterIdentity.MAX_POOLED_STRATEGIES + 25; i++) {
            input.add(pass(i, i)); // higher pass number = higher score
        }

        List<CombinedPass> stamped = ClusterIdentity.stampUnassignedByScore(input);

        assertEquals(ClusterIdentity.MAX_POOLED_STRATEGIES, stamped.size());
        // Highest scores kept
        assertEquals(ClusterIdentity.MAX_POOLED_STRATEGIES + 25, stamped.get(0).getPassNumber());
    }

    @Test
    public void existingIdsCanShareSameLineAsCousins() {
        CombinedPass a = pass(1, 90);
        a.setClusterId("B3");
        CombinedPass b = pass(2, 80);
        b.setClusterId("B3");
        CombinedPass c = pass(3, 70);

        List<CombinedPass> stamped = ClusterIdentity.stampInOrder(List.of(a, b, c));

        assertEquals(3, stamped.size());
        assertEquals("B3", stamped.get(0).getClusterId());
        assertEquals("B3", stamped.get(1).getClusterId());
        assertEquals("B1", stamped.get(2).getClusterId());
    }

    private static CombinedPass pass(int number, double score) {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(number);
        bt.setProfit(score);
        CombinedPass combined = new CombinedPass(bt, null, score, 1.0, "test");
        // CombinedPass score comes from constructor profit/score fields — set via getScore
        return combined;
    }
}
