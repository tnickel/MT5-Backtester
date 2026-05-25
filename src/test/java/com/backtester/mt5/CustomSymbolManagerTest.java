package com.backtester.mt5;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class CustomSymbolManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testToCustomName() {
        assertEquals("EURUSD_Duka", CustomSymbolManager.toCustomName("EURUSD"));
        assertEquals("GBPUSD_Duka", CustomSymbolManager.toCustomName("gbpusd"));
        assertEquals("XAUUSD_Duka", CustomSymbolManager.toCustomName("xauUsd"));
    }

    @Test
    public void testRegisterSymbol() throws Exception {
        CustomSymbolManager manager = new CustomSymbolManager(tempFolder.getRoot().toPath());
        
        LocalDate from = LocalDate.of(2023, 1, 1);
        LocalDate to = LocalDate.of(2023, 12, 31);
        
        manager.registerSymbol("EURUSD_Duka", "EURUSD", from, to, 5, 100000L);
        
        assertTrue("Symbol should be registered", manager.hasSymbol("EURUSD_Duka"));
        CustomSymbolManager.SymbolInfo info = manager.getSymbolInfo("EURUSD_Duka");
        
        assertNotNull(info);
        assertEquals("EURUSD_Duka", info.customName);
        assertEquals("EURUSD", info.originName);
        assertEquals("2023-01-01", info.dataFrom);
        assertEquals("2023-12-31", info.dataTo);
        assertEquals(5, info.digits);
        assertEquals(100000L, info.barCount);
    }

    @Test
    public void testRemoveSymbol() throws Exception {
        CustomSymbolManager manager = new CustomSymbolManager(tempFolder.getRoot().toPath());
        manager.registerSymbol("EURUSD_Duka", "EURUSD", LocalDate.now(), LocalDate.now(), 5, 100L);
        
        assertTrue(manager.hasSymbol("EURUSD_Duka"));
        
        manager.removeSymbol("EURUSD_Duka");
        assertFalse("Symbol should be removed", manager.hasSymbol("EURUSD_Duka"));
        assertNull(manager.getSymbolInfo("EURUSD_Duka"));
    }

    @Test
    public void testHasSymbol() throws Exception {
        CustomSymbolManager manager = new CustomSymbolManager(tempFolder.getRoot().toPath());
        assertFalse("Should return false for non-existent symbol", manager.hasSymbol("UNKNOWN_Duka"));
        
        manager.registerSymbol("TEST_Duka", "TEST", LocalDate.now(), LocalDate.now(), 2, 10L);
        assertTrue("Should return true after registration", manager.hasSymbol("TEST_Duka"));
    }

    @Test
    public void testLoadAndSaveSymbolsPersistence() throws Exception {
        // Register a symbol using one instance
        CustomSymbolManager manager1 = new CustomSymbolManager(tempFolder.getRoot().toPath());
        manager1.registerSymbol("PERSIST_Duka", "PERSIST", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), 4, 5000L);
        
        // Create a completely new instance pointing to the same folder
        // It should automatically load symbols.json from that folder
        CustomSymbolManager manager2 = new CustomSymbolManager(tempFolder.getRoot().toPath());
        
        assertTrue("Symbol should be loaded from disk", manager2.hasSymbol("PERSIST_Duka"));
        CustomSymbolManager.SymbolInfo loadedInfo = manager2.getSymbolInfo("PERSIST_Duka");
        assertEquals("Data originName should be preserved", "PERSIST", loadedInfo.originName);
        assertEquals("Data barCount should be preserved", 5000L, loadedInfo.barCount);
    }

    @Test
    public void testToCustomNameEdgeCases() {
        assertEquals("_Duka", CustomSymbolManager.toCustomName(""));
        try {
            CustomSymbolManager.toCustomName(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testRegisterDuplicateSymbol() throws Exception {
        CustomSymbolManager manager = new CustomSymbolManager(tempFolder.getRoot().toPath());
        manager.registerSymbol("EURUSD_Duka", "EURUSD", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1), 5, 100L);
        assertEquals(100L, manager.getSymbolInfo("EURUSD_Duka").barCount);
        
        manager.registerSymbol("EURUSD_Duka", "EURUSD", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1), 5, 250L);
        assertEquals(250L, manager.getSymbolInfo("EURUSD_Duka").barCount);
        assertEquals("2025-03-01", manager.getSymbolInfo("EURUSD_Duka").dataTo);
    }

    @Test
    public void testRegisterInvalidDigits() throws Exception {
        CustomSymbolManager manager = new CustomSymbolManager(tempFolder.getRoot().toPath());
        manager.registerSymbol("EURUSD_Duka", "EURUSD", LocalDate.now(), LocalDate.now(), -1, 100L);
        assertEquals(-1, manager.getSymbolInfo("EURUSD_Duka").digits);
    }

    @Test
    public void testRemoveNonExistentSymbol() throws Exception {
        CustomSymbolManager manager = new CustomSymbolManager(tempFolder.getRoot().toPath());
        assertFalse(manager.hasSymbol("NON_EXISTENT_Duka"));
        manager.removeSymbol("NON_EXISTENT_Duka");
        assertFalse(manager.hasSymbol("NON_EXISTENT_Duka"));
    }
}
