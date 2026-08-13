package com.backtester;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ApplicationVersionTest {

    @Test
    public void developmentFallbackMatchesTheCurrentProjectVersion() {
        assertEquals("1.2.6", ApplicationVersion.current());
        assertEquals("v1.2.6", ApplicationVersion.display());
        assertFalse(ApplicationVersion.current().isBlank());
    }
}
