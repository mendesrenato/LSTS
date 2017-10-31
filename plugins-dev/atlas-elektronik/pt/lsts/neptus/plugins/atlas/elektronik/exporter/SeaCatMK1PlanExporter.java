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
 * 06/04/2017
 */
package pt.lsts.neptus.plugins.atlas.elektronik.exporter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.ProgressMonitor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;

import de.atlas.elektronik.simulation.SeacatGotoPreview;
import de.atlas.elektronik.simulation.SeacatSKeepPreview;
import de.atlas.elektronik.simulation.SeacatSurveyPreview;
import de.atlas.elektronik.simulation.SeacatTrajectoryPreview;
import pt.lsts.imc.EntityParameter;
import pt.lsts.imc.FollowTrajectory;
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.SetEntityParameters;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.mp.ManeuverLocation;
import pt.lsts.neptus.mp.ManeuverLocation.Z_UNITS;
import pt.lsts.neptus.mp.OperationLimits;
import pt.lsts.neptus.mp.RendezvousPoints;
import pt.lsts.neptus.mp.RendezvousPoints.Point;
import pt.lsts.neptus.mp.actions.PlanActions;
import pt.lsts.neptus.mp.element.IPlanElement;
import pt.lsts.neptus.mp.element.OperationLimitsPlanElement;
import pt.lsts.neptus.mp.element.PlanElements;
import pt.lsts.neptus.mp.element.RendezvousPointsPlanElement;
import pt.lsts.neptus.mp.maneuvers.CrossHatchPattern;
import pt.lsts.neptus.mp.maneuvers.Goto;
import pt.lsts.neptus.mp.maneuvers.LocatedManeuver;
import pt.lsts.neptus.mp.maneuvers.ManeuversUtil;
import pt.lsts.neptus.mp.maneuvers.PathProvider;
import pt.lsts.neptus.mp.maneuvers.RIPattern;
import pt.lsts.neptus.mp.maneuvers.RowsManeuver;
import pt.lsts.neptus.mp.maneuvers.RowsPattern;
import pt.lsts.neptus.mp.maneuvers.StationKeeping;
import pt.lsts.neptus.renderer2d.Renderer2DPainter;
import pt.lsts.neptus.renderer2d.StateRenderer2D;
import pt.lsts.neptus.mp.preview.ManPreviewFactory;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.mission.plan.IPlanFileExporter;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.util.AngleUtils;
import pt.lsts.neptus.util.ByteUtil;
import pt.lsts.neptus.util.FileUtil;

/**
 * @author pdias
 *
 */
public class SeaCatMK1PlanExporter implements IPlanFileExporter {

    private static final int DEFAULT_TURN_RADIUS = 15;
    
    private static final String NEW_LINE = "\r\n";
    private static final String COMMENT_CHAR = "%";
    private static final String COMMENT_CHAR_WITH_SPACE = COMMENT_CHAR + " ";

    private static final int COUNTER_PAYLOADS_MANEUVERS_GAP = 5;
    private static final int COUNTER_MANEUVERS_GAP = 10;

    /** Tue Dec 15 13:34:50 2009 */
    public static final SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
    public static HashMap<String, String> activeReplacementStringForPayload = new HashMap<>();
    public static HashMap<String, Pair<String, String>> booleanReplacementString = new HashMap<>();
    public static HashMap<String, ArrayList<String>> modelSystemPayloads = new HashMap<>();
    
    // register maneuver previews
    static {
        String[] vehicles = new String[] {"seacat-mk1-01"};
        for (String vehicle : vehicles) {
            ManPreviewFactory.registerPreview(vehicle, Goto.class, SeacatGotoPreview.class);
            ManPreviewFactory.registerPreview(vehicle, StationKeeping.class, SeacatSKeepPreview.class);
            ManPreviewFactory.registerPreview(vehicle, RowsManeuver.class, SeacatSurveyPreview.class);
            ManPreviewFactory.registerPreview(vehicle, CrossHatchPattern.class, SeacatTrajectoryPreview.class);
            ManPreviewFactory.registerPreview(vehicle, RIPattern.class, SeacatTrajectoryPreview.class);
        }
    }
    
    static {
        try {
            String mapperTxt = IOUtils.toString(FileUtil.getResourceAsStream("payload-active-replacement.txt"));
            String[] lines = mapperTxt.split("[\r\n]");
            for (String ln : lines) {
                if (ln.startsWith("#") || ln.startsWith("%") || ln.startsWith(";"))
                    continue;
                try {
                    if (ln.isEmpty())
                        continue;
                    String[] pair = ln.trim().split(" {1,}");
                    if (pair.length < 2)
                        continue;
                    activeReplacementStringForPayload.put(pair[0].trim(), pair[1].trim());
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        try {
            String mapperTxt = IOUtils.toString(FileUtil.getResourceAsStream("payload-boolean-replacement.txt"));
            String[] lines = mapperTxt.split("[\r\n]");
            for (String ln : lines) {
                if (ln.startsWith("#") || ln.startsWith("%") || ln.startsWith(";"))
                    continue;
                try {
                    if (ln.isEmpty())
                        continue;
                    String[] pair = ln.trim().split(" {1,}");
                    if (pair.length < 3)
                        continue;
                    booleanReplacementString.put(pair[0].trim(), Pair.of(pair[1].trim(), pair[2].trim()));
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        try {
            String mapperTxt = IOUtils.toString(FileUtil.getResourceAsStream("models-payloads.txt"));
            String[] lines = mapperTxt.split("[\r\n]");
            for (String ln : lines) {
                if (ln.startsWith("#") || ln.startsWith("%") || ln.startsWith(";"))
                    continue;
                try {
                    if (ln.isEmpty())
                        continue;
                    String[] pair = ln.trim().split(" {1,}");
                    if (pair.length < 2)
                        continue;
                    ArrayList<String> pls = new ArrayList<>();
                    for (int i = 1; i < pair.length; i++) {
                        pls.add(pair[i].trim());
                    }
                    if (!pls.isEmpty())
                        modelSystemPayloads.put(pair[0].trim(), pls);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    // This shall be reseted at the beginning of exporting
    private long commandLineCounter = 1;
    private ArrayList<String> payloadsInPlan = new ArrayList<>();
    private boolean isKeepPositionOrDriftAtEnd = true;
    private double turnRadius = DEFAULT_TURN_RADIUS;
    
    // Acoms vars
    private boolean acomsOnCurves = false;
    private int acomsRepetitions = 3;
    
    // Debug
    public static boolean debug = false;
    public static ArrayList<Shape> planShapes = new ArrayList<>();
    public static ArrayList<LocationType> planPoints = new ArrayList<>();
    public static ArrayList<LocationType> planControlPoints = new ArrayList<>();
    public static StateRenderer2D renderer = null;
    public static Renderer2DPainter painter = new Renderer2DPainter() {
        @Override
        public void paint(Graphics2D g, StateRenderer2D renderer) {
            Graphics2D g0 = (Graphics2D) g.create();
            
            Ellipse2D.Float ellipse = new Ellipse2D.Float(-5, -5, 10, 10); 
            final Color color = new Color(210, 176, 106, 160); // KHAKI
            // final Color color2 = new Color(210, 176, 106); // KHAKI
            final Color color3 = new Color(255, 0, 255, 160); // PLUM_RED
            final Color color4 = new Color(255, 0, 255); // PLUM_RED

            for (int i = 1; i < planPoints.size(); i++) {
                Graphics2D g2 = (Graphics2D) g0.create();
                Point2D pts = renderer.getScreenPosition(planPoints.get(i - 1));
                Point2D pte = renderer.getScreenPosition(planPoints.get(i));
                g2.setColor(color);
                Line2D line = new Line2D.Float(pts, pte);
                g2.draw(line);
                g2.dispose();
            }
            
            planShapes.parallelStream().forEach(sp -> {
                LocationType lt = planPoints.get(0);
                Graphics2D g2 = (Graphics2D) g0.create();
                Point2D pt = renderer.getScreenPosition(lt);
                g2.translate(pt.getX(), pt.getY());
                g2.scale(1, -1);
                g2.scale(renderer.getZoom(), renderer.getZoom());
                //g2.setStroke(new BasicStroke(2));
                g2.setColor(color4);
                g2.draw(sp);
                g2.dispose();
            });

            planControlPoints.parallelStream().forEach(lt -> {
                Graphics2D g2 = (Graphics2D) g0.create();
                Point2D pt = renderer.getScreenPosition(lt);
                g2.translate(pt.getX(), pt.getY());
                g2.setColor(color3);
                g2.fill(ellipse);
                g2.dispose();
            });

            planPoints.parallelStream().forEach(lt -> {
                Graphics2D g2 = (Graphics2D) g0.create();
                Point2D pt = renderer.getScreenPosition(lt);
                g2.translate(pt.getX(), pt.getY());
                g2.setColor(color);
                g2.fill(ellipse);
                g2.dispose();
            });

            g0.dispose();
        }
    };

    public SeaCatMK1PlanExporter() {
        resetLocalData();
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.types.mission.plan.IPlanFileExporter#getExporterName()
     */
    @Override
    public String getExporterName() {
        return "SeaCat-MK1 Mission File";
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.types.mission.plan.IPlanFileExporter#validExtensions()
     */
    @Override
    public String[] validExtensions() {
        return new String[] { "txt" };
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.types.mission.plan.IPlanFileExporter#createFileChooserAccessory(javax.swing.JFileChooser)
     */
    @Override
    public JComponent createFileChooserAccessory(JFileChooser fileChooser) {
        return new SeaCatMK1FilePreview(fileChooser);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.types.mission.plan.IPlanFileExporter#exportToFile(pt.lsts.neptus.types.mission.plan.PlanType,
     * java.io.File, javax.swing.ProgressMonitor)
     */
    @Override
    public void exportToFile(PlanType plan, File out, ProgressMonitor monitor) throws Exception {
        resetLocalData();

        String template = IOUtils.toString(FileUtil.getResourceAsStream("template.mis"));

        String genDateStr = getTimeStamp();
        
        HashMap<String, String> settingHeader = processPlanActionsExceptPayload(plan);
        String lowBatteryStateStr = getSectionLowBatteryState(settingHeader.get("LowBatteryState"));
        String emergencyEndStr = getSectionEmergencyEnd(settingHeader.get("EmergencyEnd"));
        String safeAltitudeStr = getSectionSafeAltitude(settingHeader.get("SafeAltitude"));
        
        try {
            if (settingHeader.containsKey("CurveRadiusAt3knots"))
                turnRadius = Double.parseDouble(settingHeader.get("CurveRadiusAt3knots"));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        String emergencyRendezvousPointStr = getSectionEmergencyRendezvousPoint(plan);

        // Depends on getSectionEmergencyEnd()
        String bodyStr = getSectionBody(plan);
        
        String autonomyAreaStr = getSectionAutonomyArea(plan);
        String explorationAreaStr = getSectionExplorationArea();
        
        Pair<String, String> systemAndSwapPayloadStr = getSectionPayloadCriticality(plan);
        
        template = replaceTokenWithKey(template, "GenDate", genDateStr);
        template = replaceTokenWithKey(template, "LowBatteryState", lowBatteryStateStr);
        template = replaceTokenWithKey(template, "EmergencyRendezvousPoint", emergencyRendezvousPointStr);
        template = replaceTokenWithKey(template, "EmergencyEnd", emergencyEndStr);
        template = replaceTokenWithKey(template, "AutonomyArea", autonomyAreaStr);
        template = replaceTokenWithKey(template, "ExplorationArea", explorationAreaStr);
        template = replaceTokenWithKey(template, "SafeAltitude", safeAltitudeStr);
        template = replaceTokenWithKey(template, "SystemPayload", systemAndSwapPayloadStr.getLeft());
        template = replaceTokenWithKey(template, "SwapPayload", systemAndSwapPayloadStr.getRight());
        template = replaceTokenWithKey(template, "Body", bodyStr);

        FileUtils.write(out, template);
    }

    private void resetLocalData() {
        resetCommandLineCounter();
        payloadsInPlan.clear();
        isKeepPositionOrDriftAtEnd = true;
        turnRadius = DEFAULT_TURN_RADIUS;
        acomsOnCurves = false;
        acomsRepetitions = 3;
    }

    private long resetCommandLineCounter() {
        return commandLineCounter = 1;
    }

    private long nextCommandLineCounter() {
        return commandLineCounter++;
    }

    private long nextCommandLineCounter(int gap) {
        return commandLineCounter += Math.max(0, gap - 1);
    }

    /**
     * Tue Dec 15 13:34:50 2009
     * 
     * @return
     */
    private String getTimeStamp() {
        return dateFormatter.format(new Date());
    }

    /**
     * @param plan
     * @return
     */
    private HashMap<String, String> processPlanActionsExceptPayload(PlanType plan) {
        HashMap<String, String> ret = new HashMap<>();
        
        PlanActions pActions = plan.getStartActions();
        for (IMCMessage msg : pActions.getAllMessages()) {
            try {
                if (msg instanceof SetEntityParameters) {
                    SetEntityParameters sep = (SetEntityParameters) msg;
                    if (!"General".equalsIgnoreCase(sep.getName()) && !"Limits".equalsIgnoreCase(sep.getName()))
                        continue;
                    
                    Vector<EntityParameter> params = sep.getParams();
                    for (EntityParameter ep : params) {
                        String name = ep.getName().trim();
                        String value = ep.getValue().trim();
                        ret.put(name, value);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    @SuppressWarnings("unused")
    private String translateValueToString(String name, String value) {
        return translateValueToString(name, value, (short) -1);
    }

    @SuppressWarnings("unused")
    private String translateValueToString(String value) {
        return translateValueToString("", value, (short) -1);
    }

    private String translateValueToString(String value, short decimalPlaces) {
        return translateValueToString("", value, decimalPlaces);
    }

    private String translateValueToString(String name, String value, short decimalPlaces) {
        Boolean boolValue = BooleanUtils.toBooleanObject(value);
        if (boolValue != null) {
            return replaceTextIfBoolean(name, value);
        }
        else {
            try {
                long lg = Long.parseLong(value);
                return formatInteger(lg);
            }
            catch (NumberFormatException e) {
                try {
                    double db = Double.parseDouble(value);
                    return decimalPlaces < 0 ? formatReal(db) : formatReal(db, decimalPlaces);
                }
                catch (NumberFormatException e1) {
                    return value;
                }
            }
        }
    }

    /**
     * H LowBatteryState 20
     * 
     * @param value 
     * @return
     */
    private String getSectionLowBatteryState(String value) {
        StringBuilder sb = new StringBuilder();
        sb.append("H LowBatteryState ");
        sb.append(translateValueToString(value, (short) 0));
        sb.append(NEW_LINE);
        return sb.toString();
    }

    /**
     * H EmergencyRendezvousPoint 2
     * 1 38.438316661 -9.1103307842
     * 2 38.438317761 -9.1103309942
     * 
     * @param plan 
     * @return
     */
    private String getSectionEmergencyRendezvousPoint(PlanType plan) {
        StringBuilder sb = new StringBuilder();
        PlanElements pes = plan.getPlanElements();
        for (IPlanElement<?> pe : pes.getPlanElements()) {
            if (pe.getClass() == RendezvousPointsPlanElement.class) {
                RendezvousPointsPlanElement rpe = (RendezvousPointsPlanElement) pe;
                RendezvousPoints rPoints = rpe.getElement();
                List<Point> pts = rPoints.getPoints();
                if (pts.isEmpty())
                    continue;

                int counter = 0;
                sb.append("H EmergencyRendezvousPoint ");
                sb.append(pts.size());
                sb.append(NEW_LINE);
                for (Point point : pts) {
                    sb.append(++counter);
                    sb.append(" ");
                    sb.append(point.getLatDeg());
                    sb.append(" ");
                    sb.append(point.getLonDeg());
                    sb.append(NEW_LINE);
                }
            }
        }
        return sb.toString();
    }

    /**
     * H EmergencyEnd D
     * 
     * @param value
     * @return
     */
    private String getSectionEmergencyEnd(String value) {
        StringBuilder sb = new StringBuilder();
        switch (value.trim().toUpperCase()) {
            case "D":
                isKeepPositionOrDriftAtEnd = false;
                break;
            case "P":
            default:
                isKeepPositionOrDriftAtEnd = true;
                break;
        }
        
        sb.append("H EmergencyEnd ");
        sb.append(isKeepPositionOrDriftAtEnd ? "P" : "D");
        sb.append(NEW_LINE);        
        return sb.toString();
    }

    /**
     * @param plan 
     * @return
     */
    private String getSectionAutonomyArea(PlanType plan) {
        StringBuilder sb = new StringBuilder();
        
        OperationLimitsPlanElement opLimits = (OperationLimitsPlanElement) plan.getPlanElements().getPlanElements()
                .stream().filter(t -> t.getClass() == OperationLimitsPlanElement.class).findFirst().orElse(null);        
        
        if (opLimits == null || opLimits.getElement() == null)
            return "";

        OperationLimits opl = opLimits.getElement();
        
        Double latDeg = opl.getOpAreaLat();
        if (latDeg != null) {
            Double lonDeg = opl.getOpAreaLon();
            Double width = opl.getOpAreaWidth();
            Double length = opl.getOpAreaLength();
            Double rotRad = opl.getOpRotationRads();
            LocationType locC = new LocationType(latDeg, lonDeg);

            ArrayList<LocationType> locs = new ArrayList<>();
            LocationType loc1 = locC.getNewAbsoluteLatLonDepth();
            double[] offset = AngleUtils.rotate(rotRad, length / 2, -width / 2, false);
            loc1.translatePosition(offset[0], offset[1], 0);
            loc1.convertToAbsoluteLatLonDepth();
            locs.add(loc1);

            loc1 = locC.getNewAbsoluteLatLonDepth();
            offset = AngleUtils.rotate(rotRad, length / 2, width / 2, false);
            loc1.translatePosition(offset[0], offset[1], 0);
            loc1.convertToAbsoluteLatLonDepth();
            locs.add(loc1);

            loc1 = locC.getNewAbsoluteLatLonDepth();
            offset = AngleUtils.rotate(rotRad, -length / 2, width / 2, false);
            loc1.translatePosition(offset[0], offset[1], 0);
            loc1.convertToAbsoluteLatLonDepth();
            locs.add(loc1);

            loc1 = locC.getNewAbsoluteLatLonDepth();
            offset = AngleUtils.rotate(rotRad, -length / 2, -width / 2, false);
            loc1.translatePosition(offset[0], offset[1], 0);
            loc1.convertToAbsoluteLatLonDepth();
            locs.add(loc1);

            sb.append("H AutonomyArea 4");
            sb.append(NEW_LINE);

            int counter = 0;
            for (LocationType l : locs) {
                sb.append(++counter);
                sb.append(" ");
                sb.append(formatReal(l.getLatitudeDegs()));
                sb.append(" ");
                sb.append(formatReal(l.getLongitudeDegs()));
                sb.append(NEW_LINE);
            }
        }
        
        return sb.toString();
    }

    /**
     * @return
     */
    private String getSectionExplorationArea() {
        StringBuilder sb = new StringBuilder();
        return sb.toString();
    }

    /**
     * H SafeAltitude 3.0
     * 
     * @param value 
     * @return
     */
    private String getSectionSafeAltitude(String value) {
        StringBuilder sb = new StringBuilder();
        sb.append("H SafeAltitude ");
        sb.append(translateValueToString(value, (short) 1));
        sb.append(NEW_LINE);
        return sb.toString();
    }

    /**
     * %System Payload
     * H SystemPayload 2
     * 1 C Edgetech2205
     * 2 N MicronDST
     *
     * % Swap Payload
     * H SwapPayload 4
     * 1 C NorbitWBMS
     * 2 N UUVCam
     * 3 C TritechPSBP
     * 4 N GeometricsG882
     *
     * @param plan
     * @return
     */
    private Pair<String, String> getSectionPayloadCriticality(PlanType plan) {
        int counterSystem = 0;
        int counterSwappable = 0;
        StringBuilder sbSystem = new StringBuilder();
        StringBuilder sbSwappable = new StringBuilder();
        
        PlanActions pActions = plan.getStartActions();
        for (IMCMessage msg : pActions.getAllMessages()) {
            try {
                if (msg instanceof SetEntityParameters) {
                    SetEntityParameters sep = (SetEntityParameters) msg;

                    Vector<EntityParameter> params = sep.getParams();
                    EntityParameter pMC = getParamWithName(params, "Mission Critical");
                    if (pMC != null) {
                        Boolean boolValue = BooleanUtils.toBooleanObject(pMC.getValue().trim());
                        if (boolValue == null)
                            continue;
                        String value = replaceTextIfBoolean(pMC.getName(), pMC.getValue());
                        boolean systemOrSwappable = isPayloadASystemOrSwappable(plan.getVehicle(), sep.getName());
                        int counter;
                        StringBuilder sb;
                        if (systemOrSwappable) {
                            counter = ++counterSystem;
                            sb = sbSystem;
                        }
                        else {
                            counter = ++counterSwappable;
                            sb = sbSwappable;
                        }

                        sb.append(NEW_LINE);
                        sb.append(counter);
                        sb.append(" ");
                        sb.append(value);
                        sb.append(" ");
                        sb.append(sep.getName());
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (counterSystem > 0) {
            String counterStr = Integer.toString(counterSystem);
            StringBuilder sb = new StringBuilder(16 + counterStr.length());
            sb.append("H SystemPayload ");
            sb.append(counterStr);
            sbSystem.insert(0, sb.toString());
        }
        if (counterSwappable > 0) {
            String counterStr = Integer.toString(counterSwappable);
            StringBuilder sb = new StringBuilder(14 + counterStr.length());
            sb.append("H SwapPayload ");
            sb.append(counterStr);
            sbSwappable.insert(0, sb.toString());
        }
        
        return Pair.of(sbSystem.toString(), sbSwappable.toString());
    }

    /**
     * @param vehicle
     * @param name
     * @return
     */
    private boolean isPayloadASystemOrSwappable(String vehicle, String name) {
        for (String model : modelSystemPayloads.keySet()) {
            if (vehicle.startsWith(model)) {
                ArrayList<String> pl = modelSystemPayloads.get(model);
                return pl.contains(name.trim());
            }
        }
        return false;
    }

    /**
     * @param plan
     * @return
     * @throws Exception
     */
    private String getSectionBody(PlanType plan) throws Exception {
        StringBuilder sb = new StringBuilder();

        sb.append(getCommentLine("Plan: ", plan.getId(), " (MD5:",
                ByteUtil.encodeAsString(plan.asIMCPlan().payloadMD5()), ")"));
        sb.append(NEW_LINE);

        processManeuvers(plan, sb);

        sb.append(getCommandsBeforeEnd());
        sb.append(NEW_LINE);
        sb.append(getCommandEnd(isKeepPositionOrDriftAtEnd));

        return sb.toString();
    }

    /**
     * @param plan
     * @param sb
     * @throws Exception
     */
    private void processManeuvers(PlanType plan, StringBuilder sb) throws Exception {
        // Debug
        planShapes.clear();
        planPoints.clear();
        planControlPoints.clear();
        
        for (Maneuver m : plan.getGraph().getManeuversSequence()) {
            double speedMS = ManeuversUtil.getSpeedMps(m);
            
            if (m instanceof PathProvider) {
                processHeaderCommentAndPayloadForManeuver(sb, m); // This fills acomsOnCurves

                Collection<ManeuverLocation> waypoints = ((LocatedManeuver) m).getWaypoints();
                waypoints.stream().forEach(wp -> wp.convertToAbsoluteLatLonDepth());

                ManeuverLocation prevWp = null;
                double curHeadingRad = Double.NaN;
                boolean prevWasCurve = false;
                for (ManeuverLocation wp : ((LocatedManeuver) m).getWaypoints()) {
                    if (prevWp != null) {
                        boolean curveAdded = false;
                        if (!Double.isNaN(curHeadingRad) && !prevWasCurve) {
                            if (m instanceof RowsManeuver || m instanceof RowsPattern) {
                                // We take advantage of the way the pattern is done, with 90deg curves
                                double nextHeadingRad = AngleUtils.nomalizeAngleRadsPi(wp.getXYAngle(prevWp));
                                double deltaAngleCurveRad = AngleUtils
                                        .nomalizeAngleRadsPi(nextHeadingRad - curHeadingRad);
                                if (Math.abs(Math.abs(Math.toDegrees(deltaAngleCurveRad)) - 90) < 2) {
                                    double distBetweenWp = wp.getDistanceInMeters(prevWp);
                                    boolean distLessThanTurnRadius = distBetweenWp < turnRadius * 2;
                                    
                                    double angleDirection = Math.signum(deltaAngleCurveRad);
                                    Character direction = Math.signum(deltaAngleCurveRad) > 0 ? 'R' : 'L';

                                    double[] dist = wp.getOffsetFrom(prevWp);

                                    if (distLessThanTurnRadius) {
                                        ManeuverLocation centerLocation = prevWp.getNewAbsoluteLatLonDepth();
                                        centerLocation.translatePosition(dist[0] / 2, dist[1] / 2, dist[2] / 2);
                                        centerLocation.convertToAbsoluteLatLonDepth();
                                        
                                        double midTurnRadius = distBetweenWp / 2.;
                                        double xDeltaC = -midTurnRadius * Math.cos(curHeadingRad);
                                        double yDeltaC = -midTurnRadius * Math.sin(curHeadingRad);
                                        LocationType curvCtrlLocation = centerLocation.getNewAbsoluteLatLonDepth();
                                        curvCtrlLocation.translatePosition(xDeltaC, yDeltaC, 0);
                                        curvCtrlLocation.convertToAbsoluteLatLonDepth();

                                        insertAcomsOnCurveIfEnabled(sb, true);
                                        
                                        double targetLatDegs = curvCtrlLocation.getLatitudeDegs();
                                        double targetLonDegs = curvCtrlLocation.getLongitudeDegs();
                                        double centerLatDegs = centerLocation.getLatitudeDegs();
                                        double centerLonDegs = centerLocation.getLongitudeDegs();
                                        sb.append(getCommandCurve(targetLatDegs, targetLonDegs, centerLatDegs,
                                                centerLonDegs, direction, wp.getZ(), wp.getZUnits(), speedMS));

                                        targetLatDegs = wp.getLatitudeDegs();
                                        targetLonDegs = wp.getLongitudeDegs();
                                        centerLatDegs = centerLocation.getLatitudeDegs();
                                        centerLonDegs = centerLocation.getLongitudeDegs();
                                        sb.append(getCommandCurve(targetLatDegs, targetLonDegs, centerLatDegs,
                                                centerLonDegs, direction, wp.getZ(), wp.getZUnits(), speedMS));

                                        insertAcomsOnCurveIfEnabled(sb, false);

                                        if (debug) {
                                            planControlPoints.add(centerLocation);
                                            planPoints.add(curvCtrlLocation);
                                            planPoints.add(wp);
                                            double xDelta = -turnRadius * Math.cos(curHeadingRad);
                                            double yDelta = -turnRadius * Math.sin(curHeadingRad);

                                            double xDeltaCtrlS = -midTurnRadius * Math.cos(curHeadingRad - angleDirection * Math.PI / 4);
                                            double yDeltaCtrlS = -midTurnRadius * Math.sin(curHeadingRad - angleDirection * Math.PI / 4);
                                            double xDeltaCtrlE = -midTurnRadius * Math.cos(curHeadingRad + angleDirection * Math.PI / 4);
                                            double yDeltaCtrlE = -midTurnRadius * Math.sin(curHeadingRad + angleDirection * Math.PI / 4);

                                            double[] offprev = prevWp.getOffsetFrom(planPoints.get(0));
                                            double[] offcenter = centerLocation.getOffsetFrom(planPoints.get(0));
                                            double[] off = curvCtrlLocation.getOffsetFrom(planPoints.get(0));
                                            QuadCurve2D curv = new QuadCurve2D.Double(offprev[1], offprev[0],
                                                    offcenter[1] + yDeltaCtrlS, offcenter[0] + xDeltaCtrlS, off[1], off[0]);
                                            planShapes.add(curv);
                                            offprev = curvCtrlLocation.getOffsetFrom(planPoints.get(0));
                                            offcenter = centerLocation.getOffsetFrom(planPoints.get(0));
                                            off = wp.getOffsetFrom(planPoints.get(0));
                                            curv = new QuadCurve2D.Double(offprev[1], offprev[0],
                                                    offcenter[1] + yDeltaCtrlE, offcenter[0] + xDeltaCtrlE, off[1], off[0]);
                                            planShapes.add(curv);

                                            LocationType centerLocationCtrlDraw = centerLocation.getNewAbsoluteLatLonDepth();
                                            centerLocationCtrlDraw = centerLocationCtrlDraw.translatePosition(xDelta, yDelta, 0);
                                            centerLocationCtrlDraw.convertToAbsoluteLatLonDepth();
                                            planControlPoints.add(centerLocationCtrlDraw);
                                            centerLocationCtrlDraw = centerLocation.getNewAbsoluteLatLonDepth();
                                            centerLocationCtrlDraw = centerLocationCtrlDraw.translatePosition(xDeltaCtrlS, yDeltaCtrlS, 0);
                                            centerLocationCtrlDraw.convertToAbsoluteLatLonDepth();
                                            planControlPoints.add(centerLocationCtrlDraw);
                                            centerLocationCtrlDraw = centerLocation.getNewAbsoluteLatLonDepth();
                                            centerLocationCtrlDraw = centerLocationCtrlDraw.translatePosition(xDeltaCtrlE, yDeltaCtrlE, 0);
                                            centerLocationCtrlDraw.convertToAbsoluteLatLonDepth();
                                            planControlPoints.add(centerLocationCtrlDraw);
                                        }
                                    }
                                    else {
                                        double xDeltaCS = -turnRadius * Math.cos(curHeadingRad);
                                        double yDeltaCS = -turnRadius * Math.sin(curHeadingRad);
                                        double xDeltaCE = -turnRadius * Math.cos(curHeadingRad);
                                        double yDeltaCE = -turnRadius * Math.sin(curHeadingRad);

                                        double xDeltaCtrlS = -turnRadius * Math.cos(curHeadingRad + angleDirection * Math.PI / 2);
                                        double yDeltaCtrlS = -turnRadius * Math.sin(curHeadingRad + angleDirection * Math.PI / 2);
                                        double xDeltaCtrlE = -turnRadius * Math.cos(curHeadingRad - angleDirection * Math.PI / 2);
                                        double yDeltaCtrlE = -turnRadius * Math.sin(curHeadingRad - angleDirection * Math.PI / 2);

                                        ManeuverLocation curvCtrlStartLocation = prevWp.getNewAbsoluteLatLonDepth();
                                        curvCtrlStartLocation.translatePosition(xDeltaCtrlS, yDeltaCtrlS, 0);
                                        curvCtrlStartLocation.convertToAbsoluteLatLonDepth();
                                        ManeuverLocation curvCtrlEndLocation = wp.getNewAbsoluteLatLonDepth();
                                        curvCtrlEndLocation.translatePosition(xDeltaCtrlE, yDeltaCtrlE, 0);
                                        curvCtrlEndLocation.convertToAbsoluteLatLonDepth();
                                        
                                        ManeuverLocation curvStartLocation = curvCtrlStartLocation.getNewAbsoluteLatLonDepth();
                                        curvStartLocation.translatePosition(xDeltaCS, yDeltaCS, 0);
                                        curvStartLocation.convertToAbsoluteLatLonDepth();
                                        ManeuverLocation curvEndLocation = curvCtrlEndLocation.getNewAbsoluteLatLonDepth();
                                        curvEndLocation.translatePosition(xDeltaCE, yDeltaCE, 0);
                                        curvEndLocation.convertToAbsoluteLatLonDepth();

                                        insertAcomsOnCurveIfEnabled(sb, true);
                                        
                                        sb.append(getCommandCurve(curvStartLocation.getLatitudeDegs(),
                                                curvStartLocation.getLongitudeDegs(),
                                                curvCtrlStartLocation.getLatitudeDegs(),
                                                curvCtrlStartLocation.getLongitudeDegs(), direction, wp.getZ(),
                                                wp.getZUnits(), speedMS));
                                        sb.append(getCommandGoto(curvEndLocation.getLatitudeDegs(),
                                                curvEndLocation.getLongitudeDegs(), wp.getZ(), wp.getZUnits(),
                                                speedMS));
                                        sb.append(getCommandCurve(wp.getLatitudeDegs(), wp.getLongitudeDegs(),
                                                curvCtrlEndLocation.getLatitudeDegs(),
                                                curvCtrlEndLocation.getLongitudeDegs(), direction, wp.getZ(),
                                                wp.getZUnits(), speedMS));

                                        insertAcomsOnCurveIfEnabled(sb, false);
                                        
                                        if (debug) {
                                            planPoints.add(curvStartLocation);
                                            planPoints.add(curvEndLocation);
                                            planPoints.add(wp);
                                            planControlPoints.add(curvCtrlStartLocation);
                                            planControlPoints.add(curvCtrlEndLocation);

                                            double xDeltaCtrlSDraw = -turnRadius * Math.cos(curHeadingRad + angleDirection * -Math.PI / 4);
                                            double yDeltaCtrlSDraw = -turnRadius * Math.sin(curHeadingRad + angleDirection * -Math.PI / 4);
                                            double xDeltaCtrlEDraw = -turnRadius * Math.cos(curHeadingRad - angleDirection * -Math.PI / 4);
                                            double yDeltaCtrlEDraw = -turnRadius * Math.sin(curHeadingRad - angleDirection * -Math.PI / 4);

                                            double[] offprev = prevWp.getOffsetFrom(planPoints.get(0));
                                            double[] offcenter = curvCtrlStartLocation.getOffsetFrom(planPoints.get(0));
                                            double[] offnext = curvStartLocation.getOffsetFrom(planPoints.get(0));
                                            QuadCurve2D curv = new QuadCurve2D.Double(offprev[1], offprev[0],
                                                    offcenter[1] + yDeltaCtrlSDraw, offcenter[0] + xDeltaCtrlSDraw, offnext[1], offnext[0]);
                                            planShapes.add(curv);

                                            offprev = curvEndLocation.getOffsetFrom(planPoints.get(0));
                                            offcenter = curvCtrlEndLocation.getOffsetFrom(planPoints.get(0));
                                            offnext = wp.getOffsetFrom(planPoints.get(0));
                                            curv = new QuadCurve2D.Double(offprev[1], offprev[0],
                                                    offcenter[1] + yDeltaCtrlEDraw, offcenter[0] + xDeltaCtrlEDraw, offnext[1], offnext[0]);
                                            planShapes.add(curv);
                                        }
                                    }

                                    curveAdded = true;
                                }
                            }
                        }
                        curHeadingRad = AngleUtils.nomalizeAngleRadsPi(wp.getXYAngle(prevWp));
                        if (curveAdded) {
                            prevWp = wp;
                            prevWasCurve = true;
                            continue;
                        }
                    }

                    sb.append(getCommandGoto(wp.getLatitudeDegs(), wp.getLongitudeDegs(), wp.getZ(), wp.getZUnits(),
                            speedMS));
                    if (debug) {
                        planPoints.add(wp);
                    }
                    prevWp = wp;
                    prevWasCurve = false;
                }
            }
            else if (m instanceof StationKeeping) {
                processHeaderCommentAndPayloadForManeuver(sb, m);

                ManeuverLocation wp = ((StationKeeping) m).getManeuverLocation();
                wp.convertToAbsoluteLatLonDepth();
                sb.append(getCommandKeepPosition(wp.getLatitudeDegs(), wp.getLongitudeDegs(), wp.getZ(), wp.getZUnits(),
                        ((StationKeeping) m).getDuration()));
                if (debug) {
                    planPoints.add(wp);
                }
            }
            else if (m instanceof Goto) { // Careful with ordering because of extensions
                if (Double.isNaN(speedMS))
                    continue;

                processHeaderCommentAndPayloadForManeuver(sb, m);

                ManeuverLocation wp = ((Goto) m).getManeuverLocation();
                wp.convertToAbsoluteLatLonDepth();
                sb.append(getCommandGoto(wp.getLatitudeDegs(), wp.getLongitudeDegs(), wp.getZ(), wp.getZUnits(),
                        speedMS));
                if (debug) {
                    planPoints.add(wp);
                }
            }
            else {
                NeptusLog.pub().warn(
                        String.format("Unsupported maneuver found \"%s\" in plan \"%s\".", m.getId(), plan.getId()));
            }

            nextCommandLineCounter(COUNTER_MANEUVERS_GAP);
            sb.append(NEW_LINE);
        }
    }

    /**
     * @param sb
     * @param curveStartOrEnd If curve segment start (true), or end (false)
     */
    private void insertAcomsOnCurveIfEnabled(StringBuilder sb, boolean curveStartOrEnd) {
        if (!acomsOnCurves)
            return;
        
        if (curveStartOrEnd)
            sb.append(getSetting('Q', "Acoms", Integer.toString(acomsRepetitions), "0"));
        else
            sb.append(getSetting('Q', "Acoms", "0"));
    }

    /**
     * Comment on maneuver id and payload are created and added to sb provided.
     * 
     * @param sb
     * @param m
     */
    private void processHeaderCommentAndPayloadForManeuver(StringBuilder sb, Maneuver m) {
        sb.append(getCommentLine(m.getId()));
        sb.append(getPayloadSettingsFromManeuver(m));
        nextCommandLineCounter(COUNTER_PAYLOADS_MANEUVERS_GAP);
    }

    /**
     * @param m
     * @return
     */
    private String getPayloadSettingsFromManeuver(Maneuver m) {
        StringBuilder sb = new StringBuilder();
        PlanActions pActions = m.getStartActions();
        for (IMCMessage msg : pActions.getAllMessages()) {
            try {
                if (msg instanceof SetEntityParameters) {
                    SetEntityParameters sep = (SetEntityParameters) msg;
                    switch (sep.getName()) {
                        case "ObstacleAvoidance":
                            sb.append(getSettingObstacleAvoidance(
                                    Boolean.parseBoolean(sep.getParams().get(0).getValue())));
                            break;
                        case "ExternalControl":
                            sb.append(
                                    getSettingExternalControl(Boolean.parseBoolean(sep.getParams().get(0).getValue())));
                            break;
                        case "Acoms":
                            Vector<EntityParameter> params = sep.getParams();
                            if (Boolean.parseBoolean(params.get(0).getValue())) { // "Auto Send" parameter
                                EntityParameter pRep = getParamWithName(params, "Repetitions");
                                EntityParameter pInt = getParamWithName(params, "Interval");
                                
                                try {
                                    acomsRepetitions = Integer.parseInt(pRep.getValue());
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                    acomsRepetitions = 3;
                                }
                                
                                try {
                                    acomsOnCurves = Integer.parseInt(pInt.getValue()) == -1 ? true : false;
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                    acomsOnCurves = false;
                                }
                                if (!acomsOnCurves)
                                    sb.append(getSetting('Q', "Acoms", pRep.getValue(), pInt.getValue()));
                                else
                                    sb.append(getSetting('Q', "Acoms", "0"));
                            }
                            else {
                                acomsOnCurves = false;
                                sb.append(getSetting('Q', "Acoms", "0"));
                            }
                            break;
                        default:
                            sb.append(processPayload(sep.getName(), sep.getParams()));
                            break;
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return sb.toString();
    }

    /**
     * Example: Edgetech2205 RANGE:50;GAIN:100;OPMODE:HF;PING:ON_LOG
     * 
     * @param name
     * @param params
     * @return
     */
    private String processPayload(String payloadName, Vector<EntityParameter> params) {
        StringBuilder sb = new StringBuilder();
        for (EntityParameter ep : params) {
            String name = ep.getName();
            boolean activeKey = false;
            boolean activeValue = false;
            if (name.equalsIgnoreCase("Active")) {
                name = translatePayloadActiveFor(payloadName);
                activeKey = true;
            }
            else {
                name = formatParameterName(name);
            }
            
            activeValue = Boolean.parseBoolean(ep.getValue().trim());
            String value = replaceTextIfBoolean(name, ep.getValue());
            
            sb.append(name.toUpperCase());
            sb.append(":");
            sb.append(formatParameterValue(value));
            if (activeKey && !activeValue) {
                // Mark payload for mission end switch off
                if (!payloadsInPlan.contains(payloadName))
                    payloadsInPlan.add(payloadName);

                break;
            }
            sb.append(";");
        }

        return getSetting('P', payloadName, sb.toString());
    }

    /**
     * @param value
     * @param string 
     * @return
     */
    private String replaceTextIfBoolean(String name, String value) {
        value = value.trim().toLowerCase();
        Boolean bvt = BooleanUtils.toBooleanObject(value);
        if (bvt == null)
            return value;
        
        Pair<String, String> boolRep = booleanReplacementString.get(formatParameterName(name));
        if (boolRep != null) {
            if (bvt == true)
                return boolRep.getRight();
            else
                return boolRep.getLeft();
        }
        
        // Default processing
        if (value.equalsIgnoreCase("true")) {
            return "ON";
        }
        else if (value.equalsIgnoreCase("false")) {
            return  "OFF";
        }
        return value;
    }

    /**
     * @param txt
     * @return
     */
    private String getCommentLine(String... txt) {
        int cap = COMMENT_CHAR_WITH_SPACE.length() + NEW_LINE.length();
        for (String st : txt)
            cap += st.length();
        StringBuilder sb = new StringBuilder(cap);
        sb.append(COMMENT_CHAR_WITH_SPACE);
        for (String st : txt)
            sb.append(st);
        sb.append(NEW_LINE);
        return sb.toString();
    }

    /**
     * @param payloadName
     * @param name
     * @return
     */
    private String translatePayloadActiveFor(String payloadName) {
        String name = activeReplacementStringForPayload.get(payloadName);
        return name == null ? "ACTIVE" : name;
    }

    /**
     * @param params
     * @param name
     * @return
     */
    private EntityParameter getParamWithName(Vector<EntityParameter> params, String name) {
        for (EntityParameter ep : params) {
            if (ep.getName().equalsIgnoreCase(name))
                return ep;
        }
        return null;
    }

    /**
     * @param original
     * @param key
     * @param replacement
     * @return
     */
    private String replaceTokenWithKey(String original, String key, String replacement) {
        return original.replaceAll("\\$\\{" + key + "\\}", replacement == null ? "" : replacement);
    }

    /**
     * @param name
     * @return
     */
    private String formatParameterName(String name) {
        if (name == null || name.isEmpty())
            return name;
        
        String ret = name.replaceAll(" {1,}", "");
        // ret = ret.replaceAll("-", "_");
        return ret.toUpperCase();
    }

    /**
     * @param value
     * @return
     */
    private String formatParameterValue(String value) {
        if (value == null || value.isEmpty())
            return value;

        String ret = value.replaceAll(" {1,}", "_");
        return ret.toUpperCase();
    }

    /**
     * @param value
     * @return
     */
    private String formatReal(double value) {
        return String.format(Locale.US, "%f", value);
    }

    /**
     * @param value
     * @param decimalPlaces
     * @return
     */
    private String formatReal(double value, short decimalPlaces) {
        if (decimalPlaces < 0)
            return formatReal(value);
        
        return String.format(Locale.US, "%." + decimalPlaces + "f", value);
    }

    /**
     * @param value
     * @return
     */
    private String formatInteger(long value) {
        return "" + value;
    }

    /**
     * The depth mode might read as ‘D’ – constant diving depth – or ‘A’ – constant altitude mode
     * 
     * @param depthUnit
     * @return
     * @throws Exception
     */
    private String formatDepthUnit(Z_UNITS depthUnit) throws Exception {
        switch (depthUnit) {
            case ALTITUDE:
                return "A";
            case DEPTH:
                return "D";
            case HEIGHT:
            case NONE:
            default:
                throw new Exception(
                        "Unsupported Z unit " + depthUnit + ", valid are " + Z_UNITS.DEPTH + " or " + Z_UNITS.ALTITUDE);
        }
    }

    /**
     * C [Line_Number] [Identifier] [Manoeuvre_Descriptor] [Parameter]
     * 
     * Each command starts with a “C” character followed by a line number. Line numbers must be in ascending order, but
     * not necessary of step-size one. A following string helps to make it human readable, it is not evaluated. The
     * string must not exceed 20 characters and may not contain blanks. The command is identified by a character which
     * is the identifier of the command and determines which parameter will follow.
     * 
     * @return
     */
    private String getCommand(Character command, String humanReadableCommand, String... parameter) {
        StringBuilder sb = new StringBuilder();
        sb.append("C ");
        sb.append(nextCommandLineCounter());
        sb.append(" ");
        sb.append(Character.toUpperCase(command));
        sb.append(" ");
        sb.append(humanReadableCommand.substring(0, Math.min(20, humanReadableCommand.length())).replace(" ", "_"));
        for (String st : parameter) {
            sb.append(" ");
            sb.append(st);
        }
        sb.append(NEW_LINE);
        return sb.toString();
    }

    private String getSetting(Character command, String humanReadableCommand, String... parameter) {
        StringBuilder sb = new StringBuilder();
        sb.append("S ");
        sb.append(nextCommandLineCounter());
        sb.append(" ");
        sb.append(Character.toUpperCase(command));
        sb.append(" ");
        sb.append(humanReadableCommand.substring(0, Math.min(20, humanReadableCommand.length())).replace(" ", "_"));
        for (String st : parameter) {
            sb.append(" ");
            sb.append(st);
        }
        sb.append(NEW_LINE);
        return sb.toString();
    }

    /**
     * @param active
     * @return
     */
    private String getSettingExternalControl(boolean active) {
        return getSetting('R', "ExternalControl", active ? "1" : "0");
    }

    /**
     * @param active
     * @return
     */
    private String getSettingObstacleAvoidance(boolean active) {
        return getSetting('O', "ObstacleAvoidance", active ? "1" : "0");
    }

    /**
     * C n A Goto Latitude Longitude depth depth-mode speed
     * 
     * @param latDegs
     * @param lonDegs
     * @param depth
     * @param depthUnit
     * @param speedMS
     * @return
     * @throws Exception
     */
    private String getCommandGoto(double latDegs, double lonDegs, double depth, ManeuverLocation.Z_UNITS depthUnit,
            double speedMS) throws Exception {
        return getCommand('A', "Goto", formatReal(latDegs), formatReal(lonDegs), formatReal(depth, (short) 1),
                formatDepthUnit(depthUnit), formatReal(speedMS, (short) 1));
    }

    /**
     * C n B Goto direction distance depth depth-mode speed
     * 
     * @param directionDegs
     * @param distanceMeters
     * @param depth
     * @param depthUnit
     * @param speedMS
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unused")
    private String getCommandGotoDirection(double directionDegs, double distanceMeters, double depth,
            ManeuverLocation.Z_UNITS depthUnit, double speedMS) throws Exception {
        double dir = AngleUtils.nomalizeAngleDegrees360(directionDegs);
        return getCommand('B', "Goto", formatReal(dir, (short) 1), formatReal(distanceMeters, (short) 1),
                formatReal(depth, (short) 1), formatDepthUnit(depthUnit), formatReal(speedMS, (short) 1));
    }

    /**
     * C n C Curve target-latitude target-longitude center-latitude center-longitude direction depth depth-mode speed
     * 
     * The direction may be either ‘R’ for clockwise turning or ‘L’ for counter-clockwise turnings. All other parameters
     * are the same as in the straight Goto command.
     *
     * @param targetLatDegs
     * @param targetLonDegs
     * @param centerLatDegs
     * @param centerLonDegs
     * @param direction
     * @param depth
     * @param depthUnit
     * @param speedMS
     * @return
     * @throws Exception
     */
    private String getCommandCurve(double targetLatDegs, double targetLonDegs, double centerLatDegs,
            double centerLonDegs, Character direction, double depth, ManeuverLocation.Z_UNITS depthUnit,
            double speedMS) throws Exception {
        return getCommand('C', "Curve", formatReal(targetLatDegs), formatReal(targetLonDegs), formatReal(centerLatDegs),
                formatReal(centerLonDegs), direction == 'L' ? "L" : "R", formatReal(depth, (short) 1),
                formatDepthUnit(depthUnit), formatReal(speedMS, (short) 1));
    }

    /**
     * The command ‘K’ makes the AUV to keep its position at the desired latitude an longitude in a desired depth and
     * depth-mode for a desired time in seconds.
     * 
     * C n K KeepPosition latitude longitude depth deph-mode time
     * 
     * @throws Exception
     */
    private String getCommandKeepPosition(double latDegs, double lonDegs, double depth,
            ManeuverLocation.Z_UNITS depthUnit, long timeSeconds) throws Exception {
        return getCommand('K', "KeepPosition", formatReal(latDegs), formatReal(lonDegs), formatReal(depth, (short) 1),
                formatDepthUnit(depthUnit), formatInteger(timeSeconds));
    }

    /**
     * @return
     */
    private String getCommandsBeforeEnd() {
        StringBuilder sb = new StringBuilder();

        sb.append(getCommentLine("Ending"));

        // Disabled settings
        sb.append(getSettingObstacleAvoidance(false));
        sb.append(getSettingExternalControl(false));

        // Switch off payloads
        for (String payloadName : payloadsInPlan) {
            String name = translatePayloadActiveFor(payloadName);
            sb.append(getSetting('P', payloadName, name.toUpperCase() + ":OFF"));
        }

        return sb.toString();
    }

    /**
     * C 100 Z MissionEnd P
     * 
     * @param keepPosOrDrift true to keep the position at the end or false for drift.
     * @return
     */
    private String getCommandEnd(boolean keepPosOrDrift) {
        return getCommand('Z', "MissionEnd", keepPosOrDrift ? "P" : "D");
    }
}
