package com.backtester.dukascopy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAOutputStream;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class Bi5DecoderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDecodeEmptyFile() throws Exception {
        Bi5Decoder decoder = new Bi5Decoder();
        File emptyFile = tempFolder.newFile("empty.bi5");
        List<Bi5Decoder.Tick> ticks = decoder.decode(emptyFile.toPath(), "EURUSD", LocalDate.of(2024, 1, 2), 10);
        assertTrue(ticks.isEmpty());
    }

    @Test
    public void testDecodeSingleTick() throws Exception {
        Bi5Decoder decoder = new Bi5Decoder();
        File tempFile = tempFolder.newFile("single_tick.bi5");

        // Write raw tick data (20 bytes)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteBuffer bb = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(1800000);   // time offset: 30 minutes in ms
        bb.putInt(110250);    // ask price (scaled by 100,000 for EURUSD)
        bb.putInt(110200);    // bid price (scaled by 100,000 for EURUSD)
        bb.putFloat(1.5f);    // ask volume
        bb.putFloat(2.5f);    // bid volume
        baos.write(bb.array());

        // Compress using LZMA
        try (OutputStream fileOut = new FileOutputStream(tempFile);
             LZMAOutputStream lzmaOut = new LZMAOutputStream(fileOut, new LZMA2Options(), baos.size())) {
            lzmaOut.write(baos.toByteArray());
        }

        List<Bi5Decoder.Tick> ticks = decoder.decode(tempFile.toPath(), "EURUSD", LocalDate.of(2024, 1, 2), 10);
        assertEquals(1, ticks.size());

        Bi5Decoder.Tick tick = ticks.get(0);
        assertEquals(LocalDateTime.of(2024, 1, 2, 10, 30, 0), tick.timestamp);
        assertEquals(1.10250, tick.ask, 0.000001);
        assertEquals(1.10200, tick.bid, 0.000001);
        assertEquals(1.5f, tick.askVolume, 0.000001f);
        assertEquals(2.5f, tick.bidVolume, 0.000001f);
        assertNotNull(tick.toString());
    }

    @Test
    public void testDecodeRange() throws Exception {
        Bi5Decoder decoder = new Bi5Decoder();
        Path baseDir = tempFolder.newFolder("data").toPath();

        // Let's create: data/EURUSD/2024/01/02/10h_ticks.bi5
        Path hour10File = baseDir.resolve("EURUSD").resolve("2024").resolve("01").resolve("02").resolve("10h_ticks.bi5");
        Files.createDirectories(hour10File.getParent());

        // Prepare compressed tick data for 10:15
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteBuffer bb = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(900000);    // 15 mins
        bb.putInt(110000);    // 1.1000
        bb.putInt(109900);    // 1.0990
        bb.putFloat(1.0f);
        bb.putFloat(2.0f);
        baos.write(bb.array());

        try (OutputStream fileOut = Files.newOutputStream(hour10File);
             LZMAOutputStream lzmaOut = new LZMAOutputStream(fileOut, new LZMA2Options(), baos.size())) {
            lzmaOut.write(baos.toByteArray());
        }

        // And data/EURUSD/2024/01/02/11h_ticks.bi5
        Path hour11File = baseDir.resolve("EURUSD").resolve("2024").resolve("01").resolve("02").resolve("11h_ticks.bi5");
        baos.reset();
        bb.clear();
        bb.putInt(600000);    // 10 mins into hour 11
        bb.putInt(110500);
        bb.putInt(110400);
        bb.putFloat(3.0f);
        bb.putFloat(4.0f);
        baos.write(bb.array());

        try (OutputStream fileOut = Files.newOutputStream(hour11File);
             LZMAOutputStream lzmaOut = new LZMAOutputStream(fileOut, new LZMA2Options(), baos.size())) {
            lzmaOut.write(baos.toByteArray());
        }

        List<Bi5Decoder.Tick> ticks = decoder.decodeRange(baseDir, "EURUSD", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));
        assertEquals(2, ticks.size());
        assertEquals(LocalDateTime.of(2024, 1, 2, 10, 15, 0), ticks.get(0).timestamp);
        assertEquals(LocalDateTime.of(2024, 1, 2, 11, 10, 0), ticks.get(1).timestamp);
    }

    @Test
    public void decodeRangeRejectsAndDeletesTruncatedTickRecord() throws Exception {
        Bi5Decoder decoder = new Bi5Decoder();
        Path baseDir = tempFolder.newFolder("corrupt-data").toPath();
        Path corrupt = baseDir.resolve("EURUSD/2024/01/02/10h_ticks.bi5");
        Files.createDirectories(corrupt.getParent());

        byte[] partialTick = new byte[19];
        try (OutputStream fileOut = Files.newOutputStream(corrupt);
             LZMAOutputStream lzmaOut = new LZMAOutputStream(fileOut, new LZMA2Options(), partialTick.length)) {
            lzmaOut.write(partialTick);
        }

        try {
            decoder.decodeRange(baseDir, "EURUSD", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));
            fail("Expected truncated BI5 data to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Truncated")
                    || expected.getMessage().contains("Failed to decode"));
        }
        assertFalse("Corrupt cache file must be removed so it can be downloaded again", Files.exists(corrupt));
    }
}
