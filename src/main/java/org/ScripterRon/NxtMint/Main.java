/**
 * Copyright 2015 Ronald W Hoffman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ScripterRon.NxtMint;

import org.ScripterRon.NxtCore.Crypto;
import org.ScripterRon.NxtCore.Currency;
import org.ScripterRon.NxtCore.MintingTarget;
import org.ScripterRon.NxtCore.Nxt;
import org.ScripterRon.NxtCore.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.LogManager;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.amd.aparapi.device.OpenCLDevice;
import com.amd.aparapi.internal.opencl.OpenCLPlatform;

/**
 * NxtMint will mint a Nxt currency
 */
public class Main {

    /** Logger instance */
    public static final Logger log = LoggerFactory.getLogger("org.ScripterRon.NxtMint");

    /** File separator */
    public static String fileSeparator;

    /** Line separator */
    public static String lineSeparator;

    /** User home */
    public static String userHome;

    /** Operating system */
    public static String osName;

    /** Application identifier */
    public static String applicationID;

    /** Application name */
    public static String applicationName;

    /** Application version */
    public static String applicationVersion;

    /** Application properties */
    public static Properties properties;

    /** Data directory */
    public static String dataPath;

    /** Application properties file */
    private static File propFile;
    
    /** Enable the GUI */
    public static boolean enableGUI = true;

    /** Main application window */
    public static MainWindow mainWindow;

    /** Application lock file */
    private static RandomAccessFile lockFile;

    /** Application lock */
    private static FileLock fileLock;

    /** Deferred exception text */
    private static String deferredText;

    /** Deferred exception */
    private static Throwable deferredException;

    /** Nxt node host name */
    public static String nxtHost = "localhost";

    /** Nxt API port */
    public static int apiPort = 7876;
    
    /** Secret phrase */
    public static String secretPhrase;
    
    /** Currency code */
    public static String currencyCode;
    
    /** Currency units */
    public static double currencyUnits;
    
    /** CPU worker thread count */
    public static int cpuThreads = 1;
    
    /** GPU intensity */
    public static int gpuIntensity = 0;
    
    /** GPU devices */
    public static List<Integer> gpuDevices = new ArrayList<>();
    
    /** GPU work group sizes */
    public static List<Integer> gpuSizes = new ArrayList<>();
    
    /** GPU work group counts */
    public static List<Integer> gpuCounts = new ArrayList<>();
    
    /** Minting account identifier */
    public static long accountId;
    
    /** Minting currency */
    public static Currency currency;
    
    /** Minting target */
    public static MintingTarget mintingTarget;
    
    /** Minting units expressed as a whole number with an implied decimal point */
    public static long mintingUnits;
    
    /** GPU devices */
    public static List<GpuDevice> gpuDeviceList = new ArrayList<>();

    /**
     * Handles program initialization
     *
     * @param   args                Command-line arguments
     */
    public static void main(String[] args) {
        try {
            fileSeparator = System.getProperty("file.separator");
            lineSeparator = System.getProperty("line.separator");
            userHome = System.getProperty("user.home");
            osName = System.getProperty("os.name").toLowerCase();
            //
            // Process command-line options
            //
            dataPath = System.getProperty("nxt.datadir");
            if (dataPath == null) {
                if (osName.startsWith("win"))
                    dataPath = userHome+"\\Appdata\\Roaming\\NxtMint";
                else if (osName.startsWith("linux"))
                    dataPath = userHome+"/.NxtMint";
                else if (osName.startsWith("mac os"))
                    dataPath = userHome+"/Library/Application Support/NxtMint";
                else
                    dataPath = userHome+"/NxtMint";
            }
            //
            // Create the data directory if it doesn't exist
            //
            File dirFile = new File(dataPath);
            if (!dirFile.exists())
                dirFile.mkdirs();
            //
            // Initialize the Aparapi subsystem.  We need to do this before initializing
            // the logger since Aparapi resets logging.
            //
            GpuFunction.isSupported(0);
            //
            // Initialize the logging properties from 'logging.properties'
            //
            File logFile = new File(dataPath+fileSeparator+"logging.properties");
            if (logFile.exists()) {
                FileInputStream inStream = new FileInputStream(logFile);
                LogManager.getLogManager().readConfiguration(inStream);
            }
            //
            // Use the brief logging format
            //
            BriefLogFormatter.init();
            //
            // Process configuration file options
            //
            processConfig();
            if (secretPhrase==null || secretPhrase.length() == 0)
                throw new IllegalArgumentException("Secret phrase not specified");
            if (currencyCode==null || currencyCode.length()<3 || currencyCode.length()>5)
                throw new IllegalArgumentException("Currency code is not valid");
            if (gpuIntensity > 1024)
                throw new IllegalArgumentException("Maximum gpuIntensity is 1024");
            accountId = Utils.getAccountId(Crypto.getPublicKey(secretPhrase));
            //
            // Get the application build properties
            //
            Class<?> mainClass = Class.forName("org.ScripterRon.NxtMint.Main");
            try (InputStream classStream = mainClass.getClassLoader().getResourceAsStream("META-INF/application.properties")) {
                if (classStream == null)
                    throw new IllegalStateException("Application build properties not found");
                Properties applicationProperties = new Properties();
                applicationProperties.load(classStream);
                applicationID = applicationProperties.getProperty("application.id");
                applicationName = applicationProperties.getProperty("application.name");
                applicationVersion = applicationProperties.getProperty("application.version");
            }
            log.info(String.format("%s Version %s", applicationName, applicationVersion));
            log.info(String.format("Application data path: %s", dataPath));
            log.info(String.format("Using Nxt node at %s:%d", nxtHost, apiPort));
            log.info(String.format("Minting %,f units of %s for account %s: %d CPU threads, %d GPU intensity", 
                                   currencyUnits, currencyCode, Utils.getAccountRsId(accountId), 
                                   cpuThreads, gpuIntensity));
            //
            // Open the application lock file
            //
            lockFile = new RandomAccessFile(dataPath+fileSeparator+".lock", "rw");
            fileLock = lockFile.getChannel().tryLock();
            if (fileLock == null) {
                JOptionPane.showMessageDialog(null, "NxtMint is already running", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
            //
            // Load the saved application properties
            //
            propFile = new File(dataPath+fileSeparator+"NxtMint.properties");
            properties = new Properties();
            if (propFile.exists()) {
                try (FileInputStream in = new FileInputStream(propFile)) {
                    properties.load(in);
                }
            }
            //
            // Initialize the NxtCore library
            //
            Nxt.init(nxtHost, apiPort);
            //
            // Get the currency definition
            //
            currency = Nxt.getCurrency(currencyCode, false);
            if (!currency.isMintable())
                throw new IllegalArgumentException(String.format("Currency %s is not mintable", currencyCode));
            if (!HashFunction.isSupported(currency.getAlgorithm()))
                throw new IllegalArgumentException(String.format("Currency algorithm %d is not supported",
                                                   currency.getAlgorithm()));
            if (gpuIntensity>0 && !GpuFunction.isSupported(currency.getAlgorithm()))
                throw new IllegalArgumentException(String.format("Currency algorithm %d is not supported on the GPU",
                                                   currency.getAlgorithm()));
            //
            // Get the current minting target
            //
            mintingUnits = (long)(currencyUnits*Math.pow(10, currency.getDecimals()));
            mintingTarget = Nxt.getMintingTarget(currency.getCurrencyId(), accountId, mintingUnits);
            long maxUnits = (currency.getMaxSupply()-currency.getReserveSupply())/10000;
            if (mintingUnits > maxUnits)
                throw new IllegalArgumentException(String.format("Maximum minting units is %f for currency %s",
                                                    (double)maxUnits*Math.pow(10, -currency.getDecimals()),
                                                    currencyCode));
            //
            // Get the GPU device list if GPU intensity is non-zero
            //
            if (gpuIntensity > 0) {
                if (gpuDevices.isEmpty()) {
                    gpuDevices.add(0);
                    gpuSizes.add(256);
                    gpuCounts.add(0);
                }
                buildGpuList();
                for (int i=0; i<gpuDevices.size(); i++) {
                    int devnum = gpuDevices.get(i);
                    if (devnum >= gpuDeviceList.size())
                        throw new IllegalArgumentException(String.format("GPU device %d is not available", devnum));
                    GpuDevice gpuDevice = gpuDeviceList.get(devnum);
                    OpenCLDevice clDevice = gpuDevice.getDevice();
                    if (gpuSizes.get(i) > clDevice.getMaxWorkGroupSize()) {
                        log.warn(String.format("Work group size %d for GPU %d exceeds maximum size %d, using maximum size",
                                               gpuSizes.get(i), devnum, clDevice.getMaxWorkGroupSize()));
                        gpuDevice.setWorkGroupSize(clDevice.getMaxWorkGroupSize());
                    } else {
                        gpuDevice.setWorkGroupSize(gpuSizes.get(i));
                    }
                    gpuDevice.setWorkGroupCount(gpuCounts.get(i));
                }
            }
            //
            // Start the GUI
            //
            if (enableGUI) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                javax.swing.SwingUtilities.invokeLater(() -> createAndShowGUI());
                while (mainWindow == null)
                    Thread.sleep(1000);
            }
            //
            // Start minting
            //
            Mint.mint();
        } catch (Exception exc) {
            log.error("Exception during program initialization", exc);
        }
    }
    
    /**
     * Create and show our application GUI
     *
     * This method is invoked on the AWT event thread to avoid timing
     * problems with other window events
     */
    private static void createAndShowGUI() {
        //
        // Use the normal window decorations as defined by the look-and-feel
        // schema
        //
        JFrame.setDefaultLookAndFeelDecorated(true);
        //
        // Create the main application window
        //
        mainWindow = new MainWindow();
        //
        // Show the application window
        //
        mainWindow.pack();
        mainWindow.setVisible(true);
    }

    /**
     * Shutdown and exit
     */
    public static void shutdown() {
        //
        // Stop minting
        //
        Mint.shutdown();
        //
        // Save the application properties
        //
        saveProperties();
        //
        // Close the application lock file
        //
        try {
            fileLock.release();
            lockFile.close();
        } catch (IOException exc) {
        }
        //
        // All done
        //
        System.exit(0);
    }

    /**
     * Save the application properties
     */
    public static void saveProperties() {
        try {
            try (FileOutputStream out = new FileOutputStream(propFile)) {
                properties.store(out, "NxtMint Properties");
            }
        } catch (IOException exc) {
            log.error("Exception while saving application properties", exc);
        }
    }

    /**
     * Process the configuration file
     *
     * @throws      IllegalArgumentException    Invalid configuration option
     * @throws      IOException                 Unable to read configuration file
     */
    private static void processConfig() throws IOException, IllegalArgumentException {
        //
        // Use the defaults if there is no configuration file
        //
        File configFile = new File(dataPath+Main.fileSeparator+"NxtMint.conf");
        if (!configFile.exists())
            return;
        //
        // Process the configuration file
        //
        try (BufferedReader in = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line=in.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#')
                    continue;
                int sep = line.indexOf('=');
                if (sep < 1)
                    throw new IllegalArgumentException(String.format("Invalid configuration option: %s", line));
                String option = line.substring(0, sep).trim().toLowerCase();
                String value = line.substring(sep+1).trim();
                switch (option) {
                    case "connect":
                        nxtHost = value;
                        break;
                    case "apiport":
                        apiPort = Integer.valueOf(value);
                        break;
                    case "secretphrase":
                        secretPhrase = value;
                        break;
                    case "currency":
                        currencyCode = value;
                        break;
                    case "units":
                        currencyUnits = Double.valueOf(value);
                        break;
                    case "cputhreads":
                        cpuThreads = Integer.valueOf(value);
                        break;
                    case "gpuintensity":
                        gpuIntensity = Integer.valueOf(value);
                        break;
                    case "gpudevice":
                        String[] splits = value.split(",");
                        gpuDevices.add(Integer.valueOf(splits[0].trim()));
                        if (splits.length > 1) {
                            gpuSizes.add(Integer.valueOf(splits[1].trim()));
                            if (splits.length > 2) {
                                gpuCounts.add(Integer.valueOf(splits[2].trim()));
                            } else {
                                gpuCounts.add(0);
                            }
                        } else {
                            gpuSizes.add(256);
                            gpuCounts.add(0);
                        }
                        break;
                    case "enablegui":
                        if (value.equalsIgnoreCase("true"))
                            enableGUI = true;
                        else if (value.equalsIgnoreCase("false"))
                            enableGUI = false;
                        else
                            throw new IllegalArgumentException(String.format("enableGUI must be TRUE or FALSE"));
                        break;
                    default:
                        throw new IllegalArgumentException(String.format("Invalid configuration option: %s", line));
                }
            }
        }
    }
    
    /**
     * Build a list of available GPU devices
     */
    private static void buildGpuList() {
        //
        // Enumerate the OpenCL platforms and build the device list
        //
        List<OpenCLPlatform> platforms = new OpenCLPlatform().getOpenCLPlatforms();
        platforms.stream().forEach((platform) -> {
            List<OpenCLDevice> devices = platform.getOpenCLDevices();
            devices.stream()
                   .filter((device) -> (device.getType().equals(OpenCLDevice.TYPE.GPU)))
                   .forEach((device) -> {
                log.info(String.format(
                        "GPU device %d: %s\n"
                            + "  %dMB global memory, %dKB local memory, %d compute units, Max work group size %d",
                        gpuDeviceList.size(), platform.getName(),
                        device.getGlobalMemSize()/(1024*1024), device.getLocalMemSize()/1024,
                        device.getMaxComputeUnits(), device.getMaxWorkGroupSize()));
                gpuDeviceList.add(new GpuDevice(device));
            });
        });
    }

    /**
     * Display a dialog when an exception occurs.
     *
     * @param       text        Text message describing the cause of the exception
     * @param       exc         The Java exception object
     */
    public static void logException(String text, Throwable exc) {
        if (SwingUtilities.isEventDispatchThread()) {
            StringBuilder string = new StringBuilder(512);
            //
            // Display our error message
            //
            string.append("<html><b>");
            string.append(text);
            string.append("</b><br><br>");
            //
            // Display the exception object
            //
            string.append(exc.toString());
            string.append("<br>");
            //
            // Display the stack trace
            //
            StackTraceElement[] trace = exc.getStackTrace();
            int count = 0;
            for (StackTraceElement elem : trace) {
                string.append("<br>");
                string.append(elem.toString());
                if (++count == 25)
                    break;
            }
            string.append("</html>");
            JOptionPane.showMessageDialog(mainWindow, string, "Error", JOptionPane.ERROR_MESSAGE);
        } else if (deferredException == null) {
            deferredText = text;
            deferredException = exc;
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    Main.logException(deferredText, deferredException);
                    deferredException = null;
                    deferredText = null;
                });
            } catch (Exception logexc) {
                log.error("Unable to log exception during program initialization");
            }
        }
    }

    /**
     * Dumps a byte array to the log
     *
     * @param       text        Text message
     * @param       data        Byte array
     */
    public static void dumpData(String text, byte[] data) {
        dumpData(text, data, 0, data.length);
    }

    /**
     * Dumps a byte array to the log
     *
     * @param       text        Text message
     * @param       data        Byte array
     * @param       length      Length to dump
     */
    public static void dumpData(String text, byte[] data, int length) {
        dumpData(text, data, 0, length);
    }

    /**
     * Dump a byte array to the log
     *
     * @param       text        Text message
     * @param       data        Byte array
     * @param       offset      Offset into array
     * @param       length      Data length
     */
    public static void dumpData(String text, byte[] data, int offset, int length) {
        StringBuilder outString = new StringBuilder(512);
        outString.append(text);
        outString.append("\n");
        for (int i=0; i<length; i++) {
            if (i%32 == 0)
                outString.append(String.format(" %14X  ", i));
            else if (i%4 == 0)
                outString.append(" ");
            outString.append(String.format("%02X", data[offset+i]));
            if (i%32 == 31)
                outString.append("\n");
        }
        if (length%32 != 0)
            outString.append("\n");
        log.debug(outString.toString());
    }
}
