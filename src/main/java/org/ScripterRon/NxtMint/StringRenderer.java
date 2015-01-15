/**
 * Copyright 2013-2015 Ronald W Hoffman
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

import javax.swing.table.DefaultTableCellRenderer;

/**
 * StringRenderer is a cell renderer for use with a JTable column. It aligns
 * the string within the column (LEFT, CENTER, RIGHT)
 */
public class StringRenderer extends DefaultTableCellRenderer {

    /**
     * Create a reconcile renderer
     *
     * @param       alignment       Desired alignment
     */
    public StringRenderer(int alignment) {
        super();
        setHorizontalAlignment(alignment);
    }
}

