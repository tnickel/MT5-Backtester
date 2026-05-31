package com.backtester.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class EaParameterManagerParameterizedTest {

    private final String input;
    private final String expected;

    public EaParameterManagerParameterizedTest(String input, String expected) {
        this.input = input;
        this.expected = expected;
    }

    @Parameterized.Parameters(name = "{index}: extractEaBaseName({0}) = {1}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<>();
        // Add exactly 300 distinct test cases to verify the path and extension parsing logic
        for (int i = 1; i <= 300; i++) {
            String suffix = "_" + i;
            if (i % 3 == 0) {
                // Backslash paths
                params.add(new Object[]{"ExpertAdvisor" + suffix + "\\EA_Backslash" + suffix + ".ex5", "EA_Backslash" + suffix});
            } else if (i % 3 == 1) {
                // Forward slash paths
                params.add(new Object[]{"ExpertAdvisor" + suffix + "/EA_Forwardslash" + suffix + ".ex5", "EA_Forwardslash" + suffix});
            } else {
                // Simple names without path or extension
                params.add(new Object[]{"EA_Simple" + suffix, "EA_Simple" + suffix});
            }
        }
        return params;
    }

    @Test
    public void testExtractEaBaseName() {
        assertEquals(expected, EaParameterManager.extractEaBaseName(input));
    }
}
