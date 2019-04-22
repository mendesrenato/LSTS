/*
 * Copyright (c) 2004-2019 Universidade do Porto - Faculdade de Engenharia
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
 * 20 Apr, 2019
 */
package pt.lsts.neptus.plugins.cdc3;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.eventbus.Subscribe;

import net.miginfocom.swing.MigLayout;
import pt.lsts.imc.TransmissionRequest;
import pt.lsts.imc.TransmissionRequest.COMM_MEAN;
import pt.lsts.imc.TransmissionRequest.DATA_MODE;
import pt.lsts.imc.TransmissionStatus;
import pt.lsts.imc.UamRxFrame;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.console.notifications.Notification;
import pt.lsts.neptus.console.plugins.SubPanelChangeEvent;
import pt.lsts.neptus.console.plugins.SubPanelChangeListener;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.mp.ManeuverLocation;
import pt.lsts.neptus.mp.templates.PlanCreator;
import pt.lsts.neptus.planeditor.IEditorMenuExtension;
import pt.lsts.neptus.planeditor.IMapPopup;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.PluginUtils;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.plugins.cdc3.msg.Cdc3Message;
import pt.lsts.neptus.plugins.cdc3.msg.RetaskToMissionMessage;
import pt.lsts.neptus.plugins.cdc3.msg.RetaskToWaypointMessage;
import pt.lsts.neptus.plugins.cdc3.msg.serialization.Cdc3Serializer;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.ReflectionUtil;

/**
 * @author pdias
 *
 */
@PluginDescription(name = "CDC3 Panel",
    description = "")
@Popup(pos = POSITION.BOTTOM_RIGHT, width = 400, height = 400)
@SuppressWarnings("serial")
public class Cdc3Panel extends ConsolePanel implements IEditorMenuExtension, SubPanelChangeListener {
    
    @NeptusProperty
    private String systemsNames = "lauv-nemo-1, iver-3345";
    @NeptusProperty
    private int requestTimeoutSeconds = 60 * 5;
    @NeptusProperty
    private String vehicleDestination = "lauv-nemo-1";
    @NeptusProperty
    private float vehicleSpeed = 1;

    // GUI
    private JTextArea textArea;
    private JButton clearTextAreaButton;
    
    private AtomicInteger requestIdCounter = new AtomicInteger((int) System.currentTimeMillis()); 
    private Map<Integer, Pair<TransmissionRequest, LocalDateTime>> requests = Collections.synchronizedMap(new HashMap<>());

    private Vector<IMapPopup> renderersPopups;

    /**
     * @param console
     */
    public Cdc3Panel(ConsoleLayout console) {
        super(console);
    }

    public Cdc3Panel(ConsoleLayout console, boolean usedInsideAnotherConsolePanel) {
        super(console, usedInsideAnotherConsolePanel);
    }
    
    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#initSubPanel()
     */
    @Override
    public void initSubPanel() {
        this.removeAll();
        this.setLayout(new MigLayout());
        
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setBackground(Color.white);
        
        JScrollPane textAreaScroll = new JScrollPane(textArea);
        this.add(textAreaScroll, "w 100%, h 100%, grow, wrap");
        
        clearTextAreaButton = new JButton("clear text");
        clearTextAreaButton.addActionListener(l  -> {
            textArea.setText("");
        });
        this.add(clearTextAreaButton, "grow");

        renderersPopups = getConsole().getSubPanelsOfInterface(IMapPopup.class);
        for (IMapPopup str2d : renderersPopups) {
            str2d.addMenuExtension(this);
        }

    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#cleanSubPanel()
     */
    @Override
    public void cleanSubPanel() {
        if (renderersPopups != null) {
            for (IMapPopup str2d : renderersPopups) {
                str2d.removeMenuExtension(this);
            }
        }
    }

    
    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.consolebase.SubPanelChangeListener#subPanelChanged(pt.lsts.neptus.consolebase.
     * SubPanelChangeEvent)
     */
    @Override
    public void subPanelChanged(SubPanelChangeEvent panelChange) {
        if (panelChange == null)
            return;

        renderersPopups = getConsole().getSubPanelsOfInterface(IMapPopup.class);

        if (ReflectionUtil.hasInterface(panelChange.getPanel().getClass(), IMapPopup.class)) {

            IMapPopup sub = (IMapPopup) panelChange.getPanel();

            if (panelChange.added()) {
                renderersPopups.add(sub);
                IMapPopup str2d = sub;
                if (str2d != null) {
                    str2d.addMenuExtension(this);
                }
            }

            if (panelChange.removed()) {
                renderersPopups.remove(sub);
                IMapPopup str2d = sub;
                if (str2d != null) {
                    str2d.removeMenuExtension(this);
                }
            }
        }
    }

//    @Subscribe
//    public void on(AcousticSystems systems) {
//        String acSystems = systems.getString("list", false);
//        boolean newSystem = false;
//
//        for (String s : acSystems.split(","))
//            newSystem |= knownSystems.add(s);
//
//        if (newSystem)
//            propertiesChanged();
//    }
//
//    @Periodic(millisBetweenUpdates = 120000)
//    public void requestSysListing() {
//        if (sysDiscovery) {
//            AcousticSystemsQuery asq = new AcousticSystemsQuery();
//            for (ImcSystem s : gateways())
//                send(s.getName(), asq);
//        }
//    }
    
    public void on(TransmissionStatus msg) {
        Pair<TransmissionRequest, LocalDateTime> rqst = requests.get(msg.getReqId());
        if (rqst == null)
            return;
        
        String txtNot = I18n.textf("The request %s1 for %s2", msg.getStatusStr(), rqst.getLeft().asJSON(false));
        textArea.append("\n>TransmissionStatus::>" + txtNot + "\n");
        switch(msg.getStatus()) {
            case DELIVERED:
                post(Notification.success(I18n.text(this.getName()), txtNot));
                removeRequest(msg);
                break;
            case INPUT_FAILURE:
                post(Notification.error(I18n.text(this.getName()), txtNot));
                removeRequest(msg);
                break;
            case IN_PROGRESS:
                post(Notification.info(I18n.text(this.getName()), txtNot));
                break;
            case MAYBE_DELIVERED:
                post(Notification.success(I18n.text(this.getName()), txtNot));
                removeRequest(msg);
                break;
            case PERMANENT_FAILURE:
                post(Notification.error(I18n.text(this.getName()), txtNot));
                removeRequest(msg);
                break;
            case RANGE_RECEIVED:
                post(Notification.success(I18n.text(this.getName()), txtNot));
                removeRequest(msg);
                break;
            case SENT:
                post(Notification.success(I18n.text(this.getName()), txtNot));
                removeRequest(msg);
                break;
            case TEMPORARY_FAILURE:
                post(Notification.warning(I18n.text(this.getName()), txtNot));
                removeRequest(msg);
                break;
            default:
                post(Notification.warning(I18n.text(this.getName()), txtNot));
                removeRequest(msg);
                break;
        }
    }

    @Subscribe
    private void on(UamRxFrame msg) {
        String sourceName = msg.getSourceName();
        byte[] sntDataBytes = msg.getData();
        ByteBuffer buf = ByteBuffer.wrap(sntDataBytes);
        
        Cdc3Message recMsg = Cdc3Serializer.unserialize(buf);
        if (recMsg != null) {
            recMsg.setSource(sourceName);
            
            NeptusLog.pub().warn(recMsg + "\nBuffer::" + Cdc3Serializer.bufferToString(buf));
            textArea.append("\n>UamRxFrame::>" + recMsg + "\n");
        }
    }
    
    /**
     * @param msg
     */
    private void removeRequest(TransmissionStatus msg) {
        synchronized (requests) {
            requests.remove(msg.getReqId());
        }
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.planeditor.IEditorMenuExtension#getApplicableItems(pt.lsts.neptus.types.coord.LocationType, pt.lsts.neptus.planeditor.IMapPopup)
     */
    @Override
    public Collection<JMenuItem> getApplicableItems(LocationType loc, IMapPopup source) {
        ArrayList<JMenuItem> ret = new ArrayList<>();
        
        JMenu menu = new JMenu(PluginUtils.getPluginI18nName(getClass()));

        JMenuItem vehicleToUse = new JMenuItem("Target vehicle " + vehicleDestination);
        vehicleToUse.addActionListener(e ->  {
            String opt = (String) JOptionPane.showInputDialog(getConsole(), "Choose New target vehicle", "Target vehicle",
                    JOptionPane.QUESTION_MESSAGE, null, systemsNames.split("( *)?,( *)?"), vehicleDestination);
            if (opt != null) {
                vehicleDestination = opt;
            }
        });
        menu.add(vehicleToUse);

        JMenuItem planStart = new JMenuItem("Plan " + vehicleDestination + " start");
        planStart.addActionListener(e ->  {
            String opt = JOptionPane.showInputDialog(getConsole(), "Plan ID", 1);
            if (opt != null) {
                try {
                    int id = Integer.parseInt(opt);
                    RetaskToMissionMessage msg = new RetaskToMissionMessage();
                    msg.setMissionId(id);
                    
                    ByteBuffer serBuf = Cdc3Serializer.serialize(msg);
                    byte[] rawData = ArrayUtils.subarray(serBuf.array(), serBuf.arrayOffset(), serBuf.position());
                    
                    TransmissionRequest txRqst = new TransmissionRequest();
                    txRqst.setReqId(requestIdCounter.getAndIncrement());
                    txRqst.setDestination(vehicleDestination);
                    txRqst.setCommMean(COMM_MEAN.ACOUSTIC);
                    txRqst.setDataMode(DATA_MODE.RAW);
                    txRqst.setRawData(rawData);
                    txRqst.setDeadline(System.currentTimeMillis() / 1E3 + requestTimeoutSeconds);
                    
                    send(txRqst);
                    NeptusLog.pub().warn(msg);
                    NeptusLog.pub().warn(txRqst.asJSON());
                    textArea.append("\n>SENT Plan " + vehicleDestination + " start::>"
                            + "\nBuffer::" + Cdc3Serializer.bufferToString(serBuf) + "\n"
                            + msg + "\n" + txRqst.asJSON(false) + "\n");
                }
                catch (NumberFormatException e1) {
                    e1.printStackTrace();
                }
            }
        });
        menu.add(planStart);

        JMenuItem planWp = new JMenuItem("Plan " + vehicleDestination + " WP here");
        planWp.addActionListener(e ->  {
            try {
                LocationType locCp = loc.getNewAbsoluteLatLonDepth();
                RetaskToWaypointMessage msg = new RetaskToWaypointMessage();
                msg.setLatitudeRads((float) locCp.getLatitudeRads());
                msg.setLongitudeRads((float) locCp.getLongitudeRads());
                msg.setSpeedMps(vehicleSpeed);

                ByteBuffer serBuf = Cdc3Serializer.serialize(msg);
                byte[] rawData = ArrayUtils.subarray(serBuf.array(), serBuf.arrayOffset(), serBuf.position());

                PlanCreator creator = new PlanCreator(getConsole().getMission());
                creator.setLocation(locCp);
                creator.setZ(0, ManeuverLocation.Z_UNITS.DEPTH);
                creator.addManeuver("Goto", "speed", vehicleSpeed, "speedUnits", "m/s");
                PlanType plan = creator.getPlan();
                plan.setVehicle(vehicleDestination);
                plan.setId("cdc-wp-" + vehicleDestination);
                plan = addPlanToMission(plan);

                TransmissionRequest txRqst = new TransmissionRequest();
                txRqst.setReqId(requestIdCounter.getAndIncrement());
                txRqst.setDestination(vehicleDestination);
                txRqst.setCommMean(COMM_MEAN.ACOUSTIC);
                txRqst.setDataMode(DATA_MODE.RAW);
                txRqst.setRawData(rawData);
                txRqst.setDeadline(System.currentTimeMillis() / 1E3 + requestTimeoutSeconds);

                send(txRqst);
                NeptusLog.pub().warn(msg);
                NeptusLog.pub().warn(txRqst.asJSON());
                textArea.append("\n>SENT Plan " + vehicleDestination + " WP here::>" 
                        + "\nBuffer::" + Cdc3Serializer.bufferToString(serBuf) + "\n"
                        + msg + "\n" + txRqst.asJSON(false) + "\n");
            }
            catch (NumberFormatException e1) {
                e1.printStackTrace();
            }
        });
        menu.add(planWp);

        ret.add(menu);

        return ret;
    }

    private PlanType addPlanToMission(PlanType plan) {
        final String planId = plan.getId();
        getConsole().getMission().addPlan(plan);
        getConsole().getMission().save(true);
        getConsole().updateMissionListeners();
        PlanType result = getConsole().getMission().getIndividualPlansList().get(planId);
        getConsole().setPlan(result);

        return result;
    }

    public static void main(String[] args) {
        Cdc3Panel comp = new Cdc3Panel(ConsoleLayout.forge());
        GuiUtils.testFrame(comp);
    }
}
