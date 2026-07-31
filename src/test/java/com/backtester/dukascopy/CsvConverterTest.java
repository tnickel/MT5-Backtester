package com.backtester.dukascopy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CsvConverterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGetDigits() {
        assertEquals(3, CsvConverter.getDigits("USDJPY"));
        assertEquals(3, CsvConverter.getDigits("EURJPY"));
        assertEquals(3, CsvConverter.getDigits("xtiusd"));
        assertEquals(2, CsvConverter.getDigits("XAUUSD"));
        assertEquals(5, CsvConverter.getDigits("XAGUSD"));
        assertEquals(5, CsvConverter.getDigits("EURUSD"));
        assertEquals(5, CsvConverter.getDigits("GBPUSD"));
        assertEquals(2, CsvConverter.getDigits("SPAIN35"));
        assertEquals(2, CsvConverter.getDigits("FRANCE40"));
        assertEquals(2, CsvConverter.getDigits("DE40"));
        assertEquals(2, CsvConverter.getDigits("US30"));
    }

    @Test
    public void testAggregateToM1Empty() {
        CsvConverter converter = new CsvConverter();
        List<CsvConverter.M1Bar> bars = converter.aggregateToM1(Collections.emptyList());
        assertTrue(bars.isEmpty());
    }

    @Test
    public void testAggregateToM1() {
        CsvConverter converter = new CsvConverter(2); // Timezone offset of +2 hours

        List<Bi5Decoder.Tick> ticks = new ArrayList<>();
        // Ticks are in UTC.
        // Tick 1: 10:00:05 UTC. Offset is +2 -> adjusted is 12:00:05
        ticks.add(new Bi5Decoder.Tick(
                LocalDateTime.of(2024, 1, 2, 10, 0, 5),
                1.10020, 1.10000, 1.0f, 1.0f
        ));
        // Tick 2: 10:00:30 UTC -> adjusted is 12:00:30
        ticks.add(new Bi5Decoder.Tick(
                LocalDateTime.of(2024, 1, 2, 10, 0, 30),
                1.10040, 1.10030, 2.0f, 2.0f
        ));
        // Tick 3: 10:00:55 UTC -> adjusted is 12:00:55
        ticks.add(new Bi5Decoder.Tick(
                LocalDateTime.of(2024, 1, 2, 10, 0, 55),
                1.09990, 1.09980, 1.5f, 1.5f
        ));
        // Tick 4: 10:01:10 UTC -> adjusted is 12:01:10
        ticks.add(new Bi5Decoder.Tick(
                LocalDateTime.of(2024, 1, 2, 10, 1, 10),
                1.10010, 1.10000, 3.0f, 3.0f
        ));

        List<CsvConverter.M1Bar> bars = converter.aggregateToM1(ticks);
        assertEquals(2, bars.size());

        // First bar: 12:00
        CsvConverter.M1Bar bar1 = bars.get(0);
        assertEquals(LocalDateTime.of(2024, 1, 2, 12, 0, 0), bar1.dateTime);
        assertEquals(1.10000, bar1.open, 0.00001);
        assertEquals(1.10030, bar1.high, 0.00001);
        assertEquals(1.09980, bar1.low, 0.00001);
        assertEquals(1.09980, bar1.close, 0.00001);
        assertEquals(3, bar1.tickVolume);
        assertEquals(0, bar1.volume);

        // Second bar: 12:01
        CsvConverter.M1Bar bar2 = bars.get(1);
        assertEquals(LocalDateTime.of(2024, 1, 2, 12, 1, 0), bar2.dateTime);
        assertEquals(1.10000, bar2.open, 0.00001);
        assertEquals(1.10000, bar2.high, 0.00001);
        assertEquals(1.10000, bar2.low, 0.00001);
        assertEquals(1.10000, bar2.close, 0.00001);
        assertEquals(1, bar2.tickVolume);
    }

    @Test
    public void testWriteCsv() throws IOException {
        CsvConverter converter = new CsvConverter();
        Path outputFile = tempFolder.newFile("output.csv").toPath();

        List<CsvConverter.M1Bar> bars = new ArrayList<>();
        bars.add(new CsvConverter.M1Bar(
                LocalDateTime.of(2024, 1, 2, 12, 0),
                1.10000, 1.10050, 1.09950, 1.10020, 15, 0, 2
        ));
        bars.add(new CsvConverter.M1Bar(
                LocalDateTime.of(2024, 1, 2, 12, 1),
                1.10020, 1.10090, 1.10000, 1.10070, 22, 0, 3
        ));

        converter.writeCsv(bars, outputFile, 5);

        assertTrue(Files.exists(outputFile));
        List<String> lines = Files.readAllLines(outputFile);
        assertEquals(3, lines.size());
        assertEquals("Date,Time,Open,High,Low,Close,TickVolume,Volume,Spread", lines.get(0));
        assertEquals("2024.01.02,12:00,1.10000,1.10050,1.09950,1.10020,15,0,2", lines.get(1));
        assertEquals("2024.01.02,12:01,1.10020,1.10090,1.10000,1.10070,22,0,3", lines.get(2));
    }
}
