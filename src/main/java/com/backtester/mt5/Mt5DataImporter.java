package com.backtester.mt5;

import com.backtester.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Handles importing CSV data into MetaTrader 5 Custom Symbols.
 * 
 * Process:
 * 1. Copies the CSV file to MT5's MQL5/Files/ directory
 * 2. Deploys the DukaImporter.mq5 script to MQL5/Scripts/
 * 3. Creates an INI file to run the import script
 * 4. Launches MT5 to execute the script
 */
public class Mt5DataImporter {

    private static final Logger log = LoggerFactory.getLogger(Mt5DataImporter.class);
    private static final String IMPORTER_SCRIPT = "DukaImporter.mq5";
    private static final String IMPORTER_SCRIPT_RESOURCE = "/mql5/DukaImporter.mq5";

    private final AppConfig config;
    private Consumer<String> logCallback;

    public Mt5DataImporter() {
        this.config = AppConfig.getInstance();
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * Import a CSV file into MT5 as a Custom Symbol.
     *
     * @param csvFile       the CSV file to import
     * @param customSymbol  name for the custom symbol (e.g. "EURUSD_Duka")
     * @param originSymbol  base symbol to copy properties from (e.g. "EURUSD")
     * @param digits        number of decimal digits
     */
    public boolean importToMt5(Path csvFile, String customSymbol, String originSymbol, int digits) {
        try {
            Path mt5Dir = config.getMt5InstallDir();
            if (mt5Dir == null || !Files.exists(mt5Dir)) {
                logMsg("ERROR: MT5 installation directory not found: " + mt5Dir);
                return false;
            }

            // 1. Copy CSV to MQL5/Files/
            Path filesDir = mt5Dir.resolve("MQL5").resolve("Files");
            Files.createDirectories(filesDir);
            Path targetCsv = filesDir.resolve(csvFile.getFileName());
            Files.copy(csvFile, targetCsv, StandardCopyOption.REPLACE_EXISTING);
            logMsg("CSV copied to MT5: " + targetCsv);

            // 2. Deploy the importer script
            deployImporterScript(mt5Dir);

            // 3. Create import configuration file
            Path importIni = createImportIni(mt5Dir, customSymbol, originSymbol,
                    csvFile.getFileName().toString(), digits);

            // 4. Launch MT5 to run the script
            return launchMt5WithScript(importIni);

        } catch (Exception e) {
            log.error("Failed to import data to MT5", e);
            logMsg("ERROR: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deploy the MQL5 importer script to the MT5 Scripts directory.
     */
    private void deployImporterScript(Path mt5Dir) throws IOException {
        Path scriptsDir = mt5Dir.resolve("MQL5").resolve("Scripts");
        Files.createDirectories(scriptsDir);
        Path targetScript = scriptsDir.resolve(IMPORTER_SCRIPT);

        // Try to load from resources first, then from project's mql5 directory
        Path projectScript = config.getBasePath().resolve("mql5").resolve("Scripts").resolve(IMPORTER_SCRIPT);
        if (Files.exists(projectScript)) {
            Files.copy(projectScript, targetScript, StandardCopyOption.REPLACE_EXISTING);
            logMsg("Importer script deployed from project: " + targetScript);
        } else {
            // Create the script inline
            writeImporterScript(targetScript);
            logMsg("Importer script created: " + targetScript);
        }
    }

    /**
     * Write the MQL5 importer script.
     */
    private void writeImporterScript(Path targetScript) throws IOException {
        String script = """
                //+------------------------------------------------------------------+
                //| DukaImporter.mq5 - CSV Data Importer for Custom Symbols            |
                //| Imports M1 OHLCV data from CSV into MT5 Custom Symbols             |
                //+------------------------------------------------------------------+
                #property script_show_inputs
                
                input string InpCustomName = "EURUSD_Duka";     // Custom Symbol Name
                input string InpOriginName = "EURUSD";           // Base Symbol (for properties)
                input string InpCsvFile    = "EURUSD_M1.csv";   // CSV File Name (in MQL5/Files/)
                input int    InpDigits     = 5;                   // Price Digits
                
                //+------------------------------------------------------------------+
                void OnStart()
                {
                    // Create Custom Symbol
                    if(!CustomSymbolCreate(InpCustomName, "CustomData\\\\" + InpOriginName, InpOriginName))
                    {
                        int err = GetLastError();
                        if(err != 5304) // 5304 = symbol already exists
                        {
                            Print("ERROR: Cannot create symbol ", InpCustomName, " Error: ", err);
                            return;
                        }
                        Print("Custom symbol already exists: ", InpCustomName);
                    }
                    else
                    {
                        Print("Custom symbol created: ", InpCustomName);
                    }
                    
                    // Set symbol properties
                    CustomSymbolSetInteger(InpCustomName, SYMBOL_DIGITS, InpDigits);
                    CustomSymbolSetInteger(InpCustomName, SYMBOL_CHART_MODE, SYMBOL_CHART_MODE_BID);
                    CustomSymbolSetInteger(InpCustomName, SYMBOL_TRADE_EXEMODE, SYMBOL_TRADE_EXECUTION_MARKET);
                    CustomSymbolSetInteger(InpCustomName, SYMBOL_TRADE_CALC_MODE, SYMBOL_CALC_MODE_FOREX);
                    CustomSymbolSetDouble(InpCustomName, SYMBOL_POINT, MathPow(10, -InpDigits));
                    CustomSymbolSetDouble(InpCustomName, SYMBOL_TRADE_TICK_SIZE, MathPow(10, -InpDigits));
                    CustomSymbolSetDouble(InpCustomName, SYMBOL_TRADE_TICK_VALUE, 1.0);
                    CustomSymbolSetDouble(InpCustomName, SYMBOL_TRADE_CONTRACT_SIZE, 100000);
                    CustomSymbolSetDouble(InpCustomName, SYMBOL_VOLUME_MIN, 0.01);
                    CustomSymbolSetDouble(InpCustomName, SYMBOL_VOLUME_MAX, 100.0);
                    CustomSymbolSetDouble(InpCustomName, SYMBOL_VOLUME_STEP, 0.01);
                    
                    // Read CSV file
                    int fileHandle = FileOpen(InpCsvFile, FILE_READ|FILE_CSV|FILE_ANSI, ',');
                    if(fileHandle == INVALID_HANDLE)
                    {
                        Print("ERROR: Cannot open file ", InpCsvFile, " Error: ", GetLastError());
                        return;
                    }
                    
                    // Skip header line
                    if(!FileIsEnding(fileHandle))
                    {
                        string header = FileReadString(fileHandle);
                        while(!FileIsLineEnding(fileHandle) && !FileIsEnding(fileHandle))
                            header = FileReadString(fileHandle);
                    }
                    
                    MqlRates rates[];
                    int count = 0;
                    int batchSize = 50000;
                    ArrayResize(rates, batchSize);
                    
                    while(!FileIsEnding(fileHandle))
                    {
                        if(count >= ArraySize(rates))
                            ArrayResize(rates, ArraySize(rates) + batchSize);
                        
                        string dateStr  = FileReadString(fileHandle);
                        string timeStr  = FileReadString(fileHandle);
                        double openVal  = FileReadNumber(fileHandle);
                        double highVal  = FileReadNumber(fileHandle);
                        double lowVal   = FileReadNumber(fileHandle);
                        double closeVal = FileReadNumber(fileHandle);
                        long   tickVol  = (long)FileReadNumber(fileHandle);
                        long   volume   = (long)FileReadNumber(fileHandle);
                        int    spread   = (int)FileReadNumber(fileHandle);
                        
                        if(StringLen(dateStr) < 8) continue;
                        
                        // Parse date: YYYY.MM.DD
                        string dtStr = dateStr + " " + timeStr;
                        datetime dt = StringToTime(dtStr);
                        
                        if(dt <= 0) continue;
                        
                        rates[count].time       = dt;
                        rates[count].open       = openVal;
                        rates[count].high       = highVal;
                        rates[count].low        = lowVal;
                        rates[count].close      = closeVal;
                        rates[count].tick_volume = tickVol;
                        rates[count].real_volume = volume;
                        rates[count].spread     = spread;
                        
                        count++;
                    }
                    
                    FileClose(fileHandle);
                    
                    if(count == 0)
                    {
                        Print("ERROR: No valid data read from CSV");
                        return;
                    }
                    
                    ArrayResize(rates, count);
                    Print("Read ", count, " bars from CSV");
                    
                    // Import data using CustomRatesReplace (overwrites existing data in range)
                    int replaced = CustomRatesReplace(InpCustomName, rates[0].time, rates[count-1].time, rates);
                    
                    if(replaced > 0)
                    {
                        Print("SUCCESS: Imported ", replaced, " bars into ", InpCustomName);
                        Print("Data range: ", TimeToString(rates[0].time), " - ", TimeToString(rates[count-1].time));
                    }
                    else
                    {
                        Print("ERROR: CustomRatesReplace failed. Error: ", GetLastError());
                    }
                    
                    // Enable symbol in Market Watch
                    SymbolSelect(InpCustomName, true);
                    Print("Symbol ", InpCustomName, " added to Market Watch");
                    Print("=== Import Complete ===");
                }
                """;

        Files.writeString(targetScript, script);
    }

    /**
     * Create an INI file to launch MT5 and run the importer script.
     */
    private Path createImportIni(Path mt5Dir, String customSymbol, String originSymbol,
                                  String csvFileName, int digits) throws IOException {
        Path iniFile = config.getBasePath().resolve("config").resolve("import_temp.ini");
        Files.createDirectories(iniFile.getParent());

        // Note: MT5 doesn't directly support running scripts via CLI in the same way as backtests.
        // Instead, we create an EA-style configuration or use the startup script approach.
        // For now, we'll create the data files and instruct the user.
        String content = String.format("""
                ; MT5 Data Import Configuration
                ; Custom Symbol: %s
                ; Source: %s
                ; CSV File: %s
                ; Digits: %d
                ;
                ; To import the data:
                ; 1. Open MetaTrader 5
                ; 2. Open MetaEditor (F4)
                ; 3. Open MQL5/Scripts/DukaImporter.mq5
                ; 4. Compile (F7)
                ; 5. Drag the script onto a chart
                ; 6. Set parameters: CustomName=%s, CsvFile=%s
                """, customSymbol, originSymbol, csvFileName, digits, customSymbol, csvFileName);

        Files.writeString(iniFile, content);

        logMsg("Import configuration created. Script parameters:");
        logMsg("  Custom Symbol: " + customSymbol);
        logMsg("  CSV File: " + csvFileName);
        logMsg("  Digits: " + digits);

        return iniFile;
    }

    /**
     * Launch MT5 with the import script.
     * Note: MT5 has limited support for running scripts via CLI.
     * This method opens MT5 in portable mode — the user must then
     * manually run the DukaImporter script via the Navigator panel.
     */
    private boolean launchMt5WithScript(Path iniFile) {
        try {
            String terminalPath = config.getMt5TerminalPath();
            ProcessBuilder pb = new ProcessBuilder(terminalPath, "/portable");
            pb.directory(Paths.get(terminalPath).getParent().toFile());
            pb.redirectErrorStream(true);

            logMsg("Starting MT5 for data import...");
            logMsg("Please run the DukaImporter script from MT5 Navigator > Scripts");

            Process process = pb.start();

            // Don't wait for the process — MT5 will stay open for the user
            // to manually run the script
            Thread outputConsumer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("[MT5-Import] {}", line);
                    }
                } catch (IOException e) {
                    log.debug("MT5 output stream closed");
                }
            }, "MT5-Import-Output");
            outputConsumer.setDaemon(true);
            outputConsumer.start();

            return true;

        } catch (Exception e) {
            log.error("Failed to launch MT5 for import", e);
            logMsg("ERROR: Failed to launch MT5: " + e.getMessage());
            return false;
        }
    }

    private void logMsg(String msg) {
        log.info(msg);
        if (logCallback != null) logCallback.accept(msg);
    }
}
