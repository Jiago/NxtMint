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

import org.ScripterRon.NxtCore.Utils;

import java.io.IOException;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;

/**
 * This is the main application window
 */
public final class MainWindow extends JFrame implements ActionListener {

    /** Main window is minimized */
    private boolean windowMinimized = false;

    /** Table column classes */
    private static final Class<?>[] columnClasses = {
        Date.class, String.class, Double.class, Integer.class, Integer.class};

    /** Table column names */
    private static final String[] columnNames = {
        "Date", "Transaction ID", "Units", "Counter", "Hashes (MH)"};

    /** Table column types */
    private static final int[] columnTypes = {
        SizedTable.DATE, SizedTable.IDENTIFIER, SizedTable.AMOUNT, SizedTable.COUNT, SizedTable.COUNT};

    /** Solution table model */
    private final SolutionTableModel tableModel;

    /** Solution table */
    private final JTable table;

    /** Solution scroll pane */
    private final JScrollPane scrollPane;
    
    /**
     * Create the application window
     */
    public MainWindow() {
        //
        // Create the frame
        //
        super("Nxt Mint");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        //
        // Position the window using the saved position from the last time
        // the program was run
        //
        int frameX = 320;
        int frameY = 10;
        String propValue = Main.properties.getProperty("window.main.position");
        if (propValue != null) {
            int sep = propValue.indexOf(',');
            frameX = Integer.parseInt(propValue.substring(0, sep));
            frameY = Integer.parseInt(propValue.substring(sep+1));
        }
        setLocation(frameX, frameY);
        //
        // Size the window using the saved size from the last time
        // the program was run
        //
        int frameWidth = 640;
        int frameHeight = 580;
        propValue = Main.properties.getProperty("window.main.size");
        if (propValue != null) {
            int sep = propValue.indexOf(',');
            frameWidth = Math.max(frameWidth, Integer.parseInt(propValue.substring(0, sep)));
            frameHeight = Math.max(frameHeight, Integer.parseInt(propValue.substring(sep+1)));
        }
        setPreferredSize(new Dimension(frameWidth, frameHeight));
        //
        // Create the application menu bar
        //
        JMenuBar menuBar = new JMenuBar();
        menuBar.setOpaque(true);
        menuBar.setBackground(new Color(230,230,230));
        //
        // Add the "File" menu to the menu bar
        //
        // The "File" menu contains "Exit"
        //
        menuBar.add(new Menu(this, "File", new String[] {"Exit", "exit"}));
        //
        // Add the "Help" menu to the menu bar
        //
        // The "Help" menu contains "About"
        //
        menuBar.add(new Menu(this, "Help", new String[] {"About", "about"}));
        //
        // Add the menu bar to the window frame
        //
        setJMenuBar(menuBar);
        //
        // Create the status pane containing the NRS node, minting account, currency, number of units
        // and target difficulty
        //
        Box statusPane = Box.createVerticalBox();
        statusPane.add(new JLabel("<html><b>Server: "+Main.nxtHost+":"+Main.apiPort+"</b></html>"));
        statusPane.add(new JLabel("<html><b>Account: "+Utils.getAccountRsId(Main.accountId)+"</b></html>"));
        statusPane.add(new JLabel("<html><b>Currency: "+Main.currencyCode+"</b></html>"));
        statusPane.add(new JLabel("<html><b>Units: "+Main.currencyUnits+"</b></html>"));
        statusPane.add(new JLabel("<html><b>Difficulty: "+Main.mintingTarget.getDifficulty()+"</b></html>"));
        //
        // Create the solutions table
        //
        tableModel = new SolutionTableModel(columnNames, columnClasses);
        table = new SizedTable(tableModel, columnTypes);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scrollPane = new JScrollPane(table);
        //
        // Create the content pane
        //
        JPanel contentPane = new JPanel();
        contentPane.add(statusPane);
        contentPane.add(Box.createVerticalStrut(100));
        contentPane.add(scrollPane);
        setContentPane(contentPane);
        //
        // Receive WindowListener events
        //
        addWindowListener(new ApplicationWindowListener());
    }

    /**
     * Action performed (ActionListener interface)
     *
     * @param       ae              Action event
     */
    @Override
    public void actionPerformed(ActionEvent ae) {
        //
        // "about"          - Display information about this program
        // "exit"           - Exit the program
        //
        try {
            String action = ae.getActionCommand();
            switch (action) {
                case "exit":
                    exitProgram();
                    break;
                case "about":
                    aboutNxtMint();
                    break;
            }
        } catch (Exception exc) {
            Main.logException("Exception while processing action event", exc);
        }
    }
    
    /**
     * New solution found
     * 
     * @param       solution        Target solution
     */
    public void solutionFound(Solution solution) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            tableModel.solutionFound(solution);
            javax.swing.SwingUtilities.invokeLater(() -> repaint());
        });
    }

    /**
     * Exit the application
     *
     * @exception       IOException     Unable to save application data
     */
    private void exitProgram() throws IOException {
        //
        // Remember the current window position and size unless the window
        // is minimized
        //
        if (!windowMinimized) {
            Point p = Main.mainWindow.getLocation();
            Dimension d = Main.mainWindow.getSize();
            Main.properties.setProperty("window.main.position", p.x+","+p.y);
            Main.properties.setProperty("window.main.size", d.width+","+d.height);
        }
        //
        // Shutdown and exit
        //
        Main.shutdown();
    }

    /**
     * Display information about the NxtMint application
     */
    private void aboutNxtMint() {
        StringBuilder info = new StringBuilder(256);
        info.append(String.format("<html>%s Version %s<br>",
                    Main.applicationName, Main.applicationVersion));

        info.append("<br>User name: ");
        info.append((String)System.getProperty("user.name"));

        info.append("<br>Home directory: ");
        info.append((String)System.getProperty("user.home"));

        info.append("<br><br>OS: ");
        info.append((String)System.getProperty("os.name"));

        info.append("<br>OS version: ");
        info.append((String)System.getProperty("os.version"));

        info.append("<br>OS patch level: ");
        info.append((String)System.getProperty("sun.os.patch.level"));

        info.append("<br><br>Java vendor: ");
        info.append((String)System.getProperty("java.vendor"));

        info.append("<br>Java version: ");
        info.append((String)System.getProperty("java.version"));

        info.append("<br>Java home directory: ");
        info.append((String)System.getProperty("java.home"));

        info.append("<br>Java class path: ");
        info.append((String)System.getProperty("java.class.path"));

        info.append("<br><br>Current Java memory usage: ");
        info.append(String.format("%,.3f MB", (double)Runtime.getRuntime().totalMemory()/(1024.0*1024.0)));

        info.append("<br>Maximum Java memory size: ");
        info.append(String.format("%,.3f MB", (double)Runtime.getRuntime().maxMemory()/(1024.0*1024.0)));

        info.append("</html>");
        JOptionPane.showMessageDialog(this, info.toString(), "About Nxt Mint",
                                      JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Listen for window events
     */
    private class ApplicationWindowListener extends WindowAdapter {

        /**
         * Create the window listener
         */
        public ApplicationWindowListener() {
        }

        /**
         * Window has gained the focus (WindowListener interface)
         *
         * @param       we              Window event
         */
        @Override
        public void windowGainedFocus(WindowEvent we) {
        }

        /**
         * Window has been minimized (WindowListener interface)
         *
         * @param       we              Window event
         */
        @Override
        public void windowIconified(WindowEvent we) {
            windowMinimized = true;
        }

        /**
         * Window has been restored (WindowListener interface)
         *
         * @param       we              Window event
         */
        @Override
        public void windowDeiconified(WindowEvent we) {
            windowMinimized = false;
        }

        /**
         * Window is closing (WindowListener interface)
         *
         * @param       we              Window event
         */
        @Override
        public void windowClosing(WindowEvent we) {
            try {
                exitProgram();
            } catch (Exception exc) {
                Main.logException("Exception while closing application window", exc);
            }
        }
    }

    /**
     * Table model for the solutions table
     */
    private class SolutionTableModel extends AbstractTableModel {

        /** Column names */
        private final String[] columnNames;

        /** Column classes */
        private final Class<?>[] columnClasses;

        /** Connection list */
        private final List<Solution> solutionList = new ArrayList<>(25);

        /**
         * Create the table model
         *
         * @param       columnName          Column names
         * @param       columnClasses       Column classes
         */
        public SolutionTableModel(String[] columnNames, Class<?>[] columnClasses) {
            super();
            this.columnNames = columnNames;
            this.columnClasses = columnClasses;
        }

        /**
         * Get the number of columns in the table
         *
         * @return                  The number of columns
         */
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        /**
         * Get the column class
         *
         * @param       column      Column number
         * @return                  The column class
         */
        @Override
        public Class<?> getColumnClass(int column) {
            return columnClasses[column];
        }

        /**
         * Get the column name
         *
         * @param       column      Column number
         * @return                  Column name
         */
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        /**
         * Get the number of rows in the table
         *
         * @return                  The number of rows
         */
        @Override
        public int getRowCount() {
            return solutionList.size();
        }

        /**
         * Get the value for a cell
         *
         * @param       row         Row number
         * @param       column      Column number
         * @return                  Returns the object associated with the cell
         */
        @Override
        public Object getValueAt(int row, int column) {
            if (row >= solutionList.size())
                return null;
            Object value = null;
            Solution solution = solutionList.get(row);
            switch (column) {
                case 0:                         // Date
                    value = solution.getDate();
                    break;
                case 1:                         // Transaction identifier
                    value = Utils.idToString(solution.getTxId());
                    break;
                case 2:                         // Units
                    value = solution.getUnits();
                    break;
                case 3:                         // Counter
                    value = (int)solution.getCounter();
                    break;
                case 4:                         // Hash count
                    value = (int)(solution.getHashCount()/1000000L);
                    break;
            }
            return value;
        }
        
        /**
         * A new solution has been found
         * 
         * @param       soltuion        Solution
         */
        public void solutionFound(Solution solution) {
            int row = solutionList.size();
            solutionList.add(solution);
            fireTableRowsInserted(row, row);
        }
    }
}
