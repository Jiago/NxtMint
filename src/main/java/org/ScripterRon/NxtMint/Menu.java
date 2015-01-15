/*
 * Copyright 2014 Ronald Hoffman.
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

import java.awt.event.ActionListener;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/**
 * Menu creates a JMenu containing one or more menu items
 */
public class Menu extends JMenu {

    /**
     * Create a JMenu from an item list.  Each item is a String array containing
     * the item label and the item action.
     *
     * @param       listener            Menu action listener
     * @param       label               Menu label
     * @param       items               One or more menu items
     */
    public Menu(ActionListener listener, String label, String[]... items) {
        super(label);
        for (String[] item : items) {
            JMenuItem menuItem = new JMenuItem(item[0]);
            menuItem.setActionCommand(item[1]);
            menuItem.addActionListener(listener);
            add(menuItem);
        }
    }
}
