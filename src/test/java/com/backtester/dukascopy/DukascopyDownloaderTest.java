package com.backtester.dukascopy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DukascopyDownloaderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGetPricePoint() {
        assertEquals(100000, DukascopyDownloader.getPricePoint("EURUSD"));
        assertEquals(100000, DukascopyDownloader.getPricePoint("GBPUSD"));
        assertEquals(1000, DukascopyDownloader.getPricePoint("EURJPY"));
        assertEquals(1000, DukascopyDownloader.getPricePoint("USDJPY"));
        assertEquals(1000, DukascopyDownloader.getPricePoint("XAUUSD"));
        assertEquals(100000, DukascopyDownloader.getPricePoint("XAGUSD"));
        assertEquals(1000, DukascopyDownloader.getPricePoint("XTIUSD"));
        // Default check
        assertEquals(100000, DukascopyDownloader.getPricePoint("UNKNOWN"));
    }

    @Test
    public void testGetDownloadedDatesAndLocalFiles() throws IOException {
        Path cacheDir = tempFolder.newFolder("cache").toPath();
        DukascopyDownloader downloader = new DukascopyDownloader(cacheDir);

        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 3);
        String symbol = "EURUSD";

        // Initially no files exist
        Map<LocalDate, Boolean> downloaded = downloader.getDownloadedDates(symbol, from, to);
        assertEquals(3, downloaded.size());
        assertFalse(downloaded.get(LocalDate.of(2024, 1, 1)));
        assertFalse(downloaded.get(LocalDate.of(2024, 1, 2)));
        assertFalse(downloaded.get(LocalDate.of(2024, 1, 3)));

        List<Path> localFiles = downloader.getLocalFiles(symbol, from, to);
        assertTrue(localFiles.isEmpty());

        // Create a cache file for 2024-01-02 hour 12
        // Path should be cacheDir/EURUSD/2024/01/02/12h_ticks.bi5
        Path cachedFile = cacheDir.resolve("EURUSD")
                .resolve("2024")
                .resolve("01")
                .resolve("02")
                .resolve("12h_ticks.bi5");
        Files.createDirectories(cachedFile.getParent());
        Files.write(cachedFile, new byte[]{1, 2, 3});

        // Recheck
        downloaded = downloader.getDownloadedDates(symbol, from, to);
        assertFalse(downloaded.get(LocalDate.of(2024, 1, 1)));
        assertTrue(downloaded.get(LocalDate.of(2024, 1, 2)));
        assertFalse(downloaded.get(LocalDate.of(2024, 1, 3)));

        localFiles = downloader.getLocalFiles(symbol, from, to);
        assertEquals(1, localFiles.size());
        assertEquals(cachedFile, localFiles.get(0));
    }

    @Test
    public void testCancel() throws Exception {
        Path cacheDir = tempFolder.newFolder("cache").toPath();
        DukascopyDownloader downloader = new DukascopyDownloader(cacheDir);

        java.lang.reflect.Field cancelledField = DukascopyDownloader.class.getDeclaredField("cancelled");
        cancelledField.setAccessible(true);

        // Initially not cancelled
        assertFalse((boolean) cancelledField.get(downloader));

        // Call cancel
        downloader.cancel();

        // Should be cancelled now
        assertTrue((boolean) cancelledField.get(downloader));
    }

    @Test
    public void testPrivateUrlAndPathHelpers() throws Exception {
        Path cacheDir = tempFolder.newFolder("cache").toPath();
        DukascopyDownloader downloader = new DukascopyDownloader(cacheDir);

        // Test buildUrl via reflection
        java.lang.reflect.Method buildUrlMethod = DukascopyDownloader.class.getDeclaredMethod(
                "buildUrl", String.class, LocalDate.class, int.class);
        buildUrlMethod.setAccessible(true);

        // Remember months are 0-indexed in Dukascopy URLs: January is 0
        String url = (String) buildUrlMethod.invoke(downloader, "EURUSD", LocalDate.of(2024, 1, 15), 9);
        assertEquals("https://datafeed.dukascopy.com/datafeed/EURUSD/2024/00/15/09h_ticks.bi5", url);

        // Test getLocalFilePath via reflection
        java.lang.reflect.Method getLocalFilePathMethod = DukascopyDownloader.class.getDeclaredMethod(
                "getLocalFilePath", String.class, LocalDate.class, int.class);
        getLocalFilePathMethod.setAccessible(true);

        Path path = (Path) getLocalFilePathMethod.invoke(downloader, "EURUSD", LocalDate.of(2024, 1, 15), 9);
        Path expectedPath = cacheDir.resolve("EURUSD").resolve("2024").resolve("01").resolve("15").resolve("09h_ticks.bi5");
        assertEquals(expectedPath, path);
    }
}
