/*
 * Copyright (c) 2004-2015 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: tsmarques
 * 27 Apr 2015
 */
package pt.lsts.neptus.console.plugins.kml;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.TreeMap;

import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;

import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.renderer2d.LayerPriority;
import pt.lsts.neptus.util.ImageUtils;

/**
 * @author tsmarques
 *
 */
@SuppressWarnings("serial")
@PluginDescription(name = "Kml Import", description = "Import map features from KML, from a file or URL", author = "tsmarques", version = "0.1")
@Popup(name = "Kml Import", pos = POSITION.CENTER, width = 230, height = 500)
@LayerPriority(priority = 50)
public class KmlImport extends ConsolePanel {
    private static final int WIDTH = 230;
    private static final int HEIGHT = 500;

    private JMenuBar menuBar;
    private JMenu openMenu;
    private JMenuItem kmlFile; /* load kml features from a file */
    private JMenuItem kmlUrl; /* load kml features from a URL */
    
    private String kmlFeatUrl; /* Url given by the user */

    private JPopupMenu popup;
    private JMenuItem addItem;


    private JList<JLabel> listingPanel; /* actual listing of kml features */
    private final DefaultListModel<JLabel> listModel = new DefaultListModel<>();
    private JFileChooser fileChooser;

    private TreeMap<String, String> kmlFeatures;


    public KmlImport(ConsoleLayout console) {
        super(console);
        initPluginPanel();        
        initListingPanel();  
    }


    private void initPluginPanel() {
        setLayout(new BorderLayout());
        
        menuBar  = new JMenuBar();
        openMenu = new JMenu("Open");
        kmlFile = new JMenuItem("Open from file");
        kmlUrl = new JMenuItem("Open from Url");
        kmlFeatUrl = "";

        openMenu.add(kmlFile);
        openMenu.add(kmlUrl);
        menuBar.add(openMenu);

        add(menuBar, BorderLayout.NORTH);
        addMenuListeners();

        fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        popup = new JPopupMenu();
        addItem = new JMenuItem("Add to map");
        popup.add(addItem);

        addItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                /* Do something */
            }
        });
    }

    private void initListingPanel() {
        listingPanel = new JList<>(listModel);
        listingPanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        add(listingPanel);

        listingPanel.setCellRenderer(new CustomListCellRenderer());

        listingPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent me) {
                if(SwingUtilities.isRightMouseButton(me) 
                        && !listingPanel.isSelectionEmpty()
                        && listingPanel.locationToIndex(me.getPoint())
                        == listingPanel.getSelectedIndex()) {
                    popup.show(listingPanel, me.getX(), me.getY());
                }
            }
        });
    }

    private void listKmlFeatures(URL url) {
        cleanListing();

        KmlReader kml = new KmlReader(url, true);
        kmlFeatures = kml.extractFeatures();

        for(String fname : kmlFeatures.keySet()) {
            String fgeom = kmlFeatures.get(fname);
            listModel.addElement(getFeatureLabel(fname, fgeom));
        }
    }
    
    private JLabel getFeatureLabel(String fname, String fgeom) {
        JLabel feature = new JLabel(fname);
        String iconUrl = "";
        
        if(fgeom.equals("Point"))
            iconUrl = "pt/lsts/neptus/console/plugins/kml/icons/point.png";
        else if(fgeom.equals("LineString"))
            iconUrl = "pt/lsts/neptus/console/plugins/kml/icons/lnstr.png";
        else if(fgeom.equals("Point"))
            iconUrl = "pt/lsts/neptus/console/plugins/kml/icons/polyg.png";
        
        feature.setIcon(ImageUtils.getScaledIcon(iconUrl, 15, 15));        
        return feature;
    }

    private void addMenuListeners() {
        kmlFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int result = fileChooser.showOpenDialog(getParent());
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    System.out.println("Selected file: " + selectedFile.getAbsolutePath());                 
                    try {
                        URL fileUrl = new URL(selectedFile.getAbsolutePath().toString());
                        listKmlFeatures(fileUrl);
                    }
                    catch(MalformedURLException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        kmlUrl.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {               
                String urlStr = JOptionPane.showInputDialog("Enter a URL", kmlFeatUrl);
                System.out.println("URL: " + urlStr);

                try {
                    kmlFeatUrl = urlStr;
                    listKmlFeatures(new URL(urlStr));
                }
                catch(MalformedURLException e1) {
                    e1.printStackTrace();
                }
            }
        });
    }

    private void cleanListing() {
        int nElements = listModel.getSize();
        if(nElements != 0)           
            listModel.removeAllElements();
    }


    @Override
    public void cleanSubPanel() {}

    @Override
    public void initSubPanel() {}

    private class CustomListCellRenderer implements ListCellRenderer<JLabel> {
        @Override
        public Component getListCellRendererComponent(JList<? extends JLabel> list, JLabel value, int index,
                boolean isSelected, boolean cellHasFocus) {
            value.setOpaque(true);
            if (isSelected) {
                value.setBackground(list.getSelectionBackground());
                value.setForeground(list.getSelectionForeground());
            }
            else {
                value.setBackground(list.getBackground());
                value.setForeground(list.getForeground());
            }        
            return value;
        }
    }
}
