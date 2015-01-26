/*
 * Copyright 2015 Ronald W Hoffman.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ScripterRon.NxtMint;

import java.util.ArrayList;
import java.util.List;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Dialog displayed when 'Worker Details' selected
 */
public class WorkerDetailsDialog extends JDialog {
    private static final int DETAILS_WIDTH = 200;
    private static final int MIN_HEIGHT = 350;

    private final Box mainContainer;
    private final List<WorkerDetailsPanel> workersPanels;

    /**
     * Create the worker details dialog
     * 
     * @param       owner           Parent frame
     * @param       workers         Mint worker list
     */
    public WorkerDetailsDialog(JFrame owner, List<MintWorker> workers) {
        super(owner, "Workers Details", false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(DETAILS_WIDTH,
                    owner.getSize().height<MIN_HEIGHT ? MIN_HEIGHT : owner.getSize().height));
        workersPanels = new ArrayList<>(workers.size());
        mainContainer = Box.createVerticalBox();
        add(new JScrollPane(mainContainer));
        workers.stream().forEach((worker) -> {
                WorkerDetailsPanel panel = new WorkerDetailsPanel(worker);
                mainContainer.add(panel);
                workersPanels.add(panel);
        });
        mainContainer.add(Box.createVerticalGlue());
        //
        // Receive WindowListener events
        //
        addWindowListener(new ApplicationWindowListener());
    }

    /**
     * Show the worker details dialog
     *
     * @param       parent              Parent frame
     * @param       workers             Worker list
     */
    public static void showDialog(JFrame parent, List<MintWorker> workers) {
        try {
            WorkerDetailsDialog dialog = new WorkerDetailsDialog(parent, workers);
            Main.mainWindow.workerDetailsDialog = dialog;
            dialog.pack();
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        } catch (Exception exc) {
            Main.logException("Exception while displaying dialog", exc);
        }
    }

    /**
     * Update the status labels for the worker details dialog
     * 
     * @param       totalHashrate       Total hash rate
     * @param       totalHashes         Total hashes
     */
    public void updateLabels(double totalHashrate, long totalHashes) {
        workersPanels.stream().forEach((panel) -> panel.updateLabels(totalHashrate, totalHashes));
    }

    /**
     * Individual worker details panel
     */
    private static class WorkerDetailsPanel extends JPanel {
        private final MintWorker worker;
        private final JLabel hashrateLabel;
        private final JLabel hashrateRatioLabel;
        private final JLabel totalHashesLabel;
        private final JLabel totalHashesRatioLabel;

        /**
         * Create the individual worker details panel
         * 
         * @param       worker          Mint worker
         */
        public WorkerDetailsPanel(MintWorker worker) {
            this.worker = worker;
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            String title = "#" + worker.getWorkerId() + (worker.isGpuWorker() ? " - GPU" : " - CPU")
                            + (worker.isGpuDisabled() ? " (GPU disabled => CPU)" : "");
            setBorder(BorderFactory.createTitledBorder(title));

            hashrateLabel = new JLabel();
            hashrateRatioLabel = new JLabel();
            totalHashesLabel = new JLabel();
            totalHashesRatioLabel = new JLabel();

            add(Box.createVerticalStrut(5));
            add(hashrateLabel);
            add(hashrateRatioLabel);
            add(totalHashesLabel);
            add(totalHashesRatioLabel);
            add(Box.createVerticalStrut(10));

            updateLabels(0, 0);
        }

        /**
         * Update the status labels for the individual worker details panel
         * 
         * @param       totalHashrate       Total hash rage
         * @param       totalHashes         Total hashes
         */
        public final void updateLabels(double totalHashrate, long totalHashes) {
            double hashrate = worker.getRate();
            double hashrateRatio = (totalHashrate>0 ? ((hashrate/totalHashrate)*100.0) : 0);

            long hashes = worker.getTotalHashes();
            double hashesRatio = (totalHashes>0 ? (((double)hashes/(double)totalHashes)*100.0) : 0);

            hashrateLabel.setText(String.format("<html><b>Hashrate: %,.2f MH/s</b></html>", 
                                        hashrate/1000000));
            hashrateRatioLabel.setText(String.format("<html><b>&#37; of total hashrate: %,.2f</b></html>", 
                                        hashrateRatio));
            totalHashesLabel.setText(String.format("<html><b>Total hashes: %,.2f MH</b></html>", 
                                        (double)hashes/1000000));
            totalHashesRatioLabel.setText(String.format("<html><b>&#37; of total hashes: %,.2f</b></html>", 
                                        hashesRatio));
        }
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
         * Window is closing (WindowListener interface)
         *
         * @param       we              Window event
         */
        @Override
        public void windowClosing(WindowEvent we) {
            Main.mainWindow.workerDetailsDialog = null;
        }
    }
}
