package com.backtester.config;

import org.junit.Test;
import static org.junit.Assert.*;

public class EaParameterTest {

    @Test
    public void testIsModified() {
        EaParameter param = new EaParameter("TakeProfit", "50");
        
        // Initial sollte der Wert dem Standardwert entsprechen, also nicht modifiziert sein
        assertFalse("Parameter sollte initial nicht als modifiziert gelten", param.isModified());
        
        // Wert ändern
        param.setValue("60");
        assertTrue("Parameter sollte nach Änderung als modifiziert gelten", param.isModified());
    }

    @Test
    public void testResetToDefault() {
        EaParameter param = new EaParameter("StopLoss", "30");
        param.setValue("40");
        assertTrue(param.isModified());
        
        // Zurücksetzen
        param.resetToDefault();
        assertEquals("Wert sollte wieder auf Standardwert zurückgesetzt sein", "30", param.getValue());
        assertFalse("Parameter sollte nach Reset nicht mehr als modifiziert gelten", param.isModified());
    }

    @Test
    public void testToSetFileLine_StringType() {
        EaParameter param = new EaParameter("ExpertName", "MyBot");
        param.setStringType(true);
        
        // String-Parameter haben keine || Formatierungen in .set Dateien
        String expected = "ExpertName=MyBot";
        assertEquals("Formatierung für String-Typ fehlgeschlagen", expected, param.toSetFileLine());
    }

    @Test
    public void testToSetFileLine_NumericType_NotOptimized() {
        EaParameter param = new EaParameter("TrailingStop", "15");
        param.setOptimizeStart("10");
        param.setOptimizeStep("5");
        param.setOptimizeEnd("50");
        param.setOptimizeEnabled(false);
        
        // Erwartetes Format: Name=Value||Start||Step||End||N
        String expected = "TrailingStop=15||10||5||50||N";
        assertEquals("Formatierung für numerischen Typ (nicht optimiert) fehlgeschlagen", expected, param.toSetFileLine());
    }

    @Test
    public void testToSetFileLine_NumericType_Optimized() {
        EaParameter param = new EaParameter("MagicNumber", "12345");
        param.setOptimizeStart("10000");
        param.setOptimizeStep("1");
        param.setOptimizeEnd("20000");
        param.setOptimizeEnabled(true);
        
        // Erwartetes Format: Name=Value||Start||Step||End||Y
        String expected = "MagicNumber=12345||10000||1||20000||Y";
        assertEquals("Formatierung für numerischen Typ (optimiert) fehlgeschlagen", expected, param.toSetFileLine());
    }
}
