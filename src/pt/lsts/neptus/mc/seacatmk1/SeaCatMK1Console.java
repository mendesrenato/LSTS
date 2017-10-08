/*
 * Copyright (c) 2004-2017 Universidade do Porto - Faculdade de Engenharia
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
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: pdias
 * 03/05/2017
 */
package pt.lsts.neptus.mc.seacatmk1;

import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.gui.Loader;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.loader.NeptusMain;
import pt.lsts.neptus.mc.lauvconsole.LAUVConsole;
import pt.lsts.neptus.mp.ManeuverLocation;
import pt.lsts.neptus.mp.SpeedType.Units;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.conf.ConfigFetch;
import pt.lsts.neptus.util.conf.GeneralPreferences;

/**
 * @author pdias
 *
 */
public class SeaCatMK1Console extends LAUVConsole {

    private static final long serialVersionUID = 1L;

    static {
        consoleURL = "conf/consoles/seacat-mk1.ncon";
        lauvVehicle = "seacat-mk1-01";
    }
    
    public SeaCatMK1Console() {
    }

    public static ConsoleLayout create(String[] args) {
        try {
            return create(SeaCatMK1Console.class, args);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * This will force some values. 
     */
    private static void setupGeneralPreferencesChanges() {
        GeneralPreferences.speedUnits = Units.Knots;
        GeneralPreferences.forceSpeedUnits = true;
        GeneralPreferences.validZUnits = new ManeuverLocation.Z_UNITS[] { ManeuverLocation.Z_UNITS.NONE,
                ManeuverLocation.Z_UNITS.DEPTH, ManeuverLocation.Z_UNITS.ALTITUDE };
        GeneralPreferences.useMainVehicleComboOnConsoles = false;
        GeneralPreferences.placeNotificationButtonOnConsoleStatusBar = false;
    }

    private static void removeExtraMenus(ConsoleLayout con) {
        con.removeMenuItem(I18n.text("View"));
        con.removeMenuItem(I18n.text("Tools"));
        con.removeMenuItem(I18n.text("Advanced"));
        con.removeMenuItem(I18n.text("Profiles"));
        con.removeMenuItem(I18n.text("Help"));
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        ConfigFetch.initialize();

        loader = new Loader("images/seacatmk1-loader.png");
        
        GeneralPreferences.initialize();
        setupGeneralPreferencesChanges();

        loader.start();
        ConfigFetch.setSuperParentFrameForced(loader);

        boolean neptusLookAndFeel = true;
        NeptusMain.loadPreRequirementsDataExceptConfigFetch(loader, neptusLookAndFeel);

        ConsoleLayout con = create(new String[0]);
        
        con.getJMenuBar().setVisible(false);
        // con.getStatusBar().setVisible(false);
        
        removeExtraMenus(con);
        
        // To stop the comms
        ImcMsgManager.getManager().stop();
        
        NeptusMain.wrapMainApplicationWindowWithCloseActionWindowAdapter(con);
        
        loader.setText(I18n.text("Application started"));
        loader.end();
    }
}
