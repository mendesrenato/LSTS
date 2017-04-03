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
 * Author: keila
 * 09/03/2017
 */
package pt.lsts.neptus.plugins.nvl_runtime;

import java.util.ArrayList;
import java.util.List;

import pt.lsts.neptus.nvl.runtime.NVLVehicle;
import pt.lsts.neptus.nvl.runtime.PayloadComponent;
import pt.lsts.neptus.nvl.runtime.Position;
import pt.lsts.neptus.nvl.runtime.TaskSpecification;
import pt.lsts.neptus.nvl.runtime.VehicleRequirements;
import pt.lsts.imc.IMCMessage;
import pt.lsts.neptus.comm.manager.imc.ImcSystem;
import pt.lsts.neptus.comm.manager.imc.ImcSystemsHolder;
import pt.lsts.neptus.console.events.ConsoleEventVehicleStateChanged;
import pt.lsts.neptus.console.events.ConsoleEventVehicleStateChanged.STATE;
import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.types.mission.TransitionType;
import pt.lsts.neptus.types.mission.plan.PlanCompatibility;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.types.vehicle.VehicleType;
import pt.lsts.neptus.types.vehicle.VehicleType.SystemTypeEnum;
import pt.lsts.neptus.types.vehicle.VehiclesHolder;

/**
 * @author keila
 *
 */
public class NeptusTaskSpecificationAdapter implements TaskSpecification {
    private  IMCMessage imcmessage;  //TODO
    private  String id;
    private List<NVLVehicle> vehicles_id; 
    private Maneuver[] maneuvers;
    private TransitionType[] transitions;
    private List<Position> areaToMap;
    private VehicleRequirements requirements;
    
  
  /**
     * Create a NVLTaskSpecification from Neptus PlanType
     * @param plan 
     * 
     */
    public NeptusTaskSpecificationAdapter(PlanType plan) {
        
        this.imcmessage = plan.asIMCPlan();
        List<PayloadComponent> payload = new ArrayList<>();
        List<NVLVehicle> vs = new ArrayList<>();        
        plan.getVehicles().stream().forEach(v -> vs.add(new NeptusVehicleAdapter(ImcSystemsHolder.getSystemWithName(v.getId()),STATE.values()[(int) (ImcSystemsHolder.getSystemWithName(v.getId()).getLastAnnounceStateReceived())])));
        this.vehicles_id =  vs;
        this.id = plan.getId();
        this.maneuvers = plan.getGraph().getAllManeuvers();
        this.transitions = plan.getGraph().getTransitions().values().toArray(transitions);
        for( String p: PlanCompatibility.payloadsRequired(plan)) {
            payload.add(new NeptusPayloadAdapter(p));
        }
        this.requirements.setRequiredPayload(payload);
        //this.requirements.setAreaCenter(areaCenter);
        //this.requirements.setRequiredType(requiredType);
        //this.requirements.setRequiredAvailability(requiredAvailability);
        
     
    }
    
    // 1 requirement <-> 1 maneuver ?
    public NeptusTaskSpecificationAdapter(List<VehicleRequirements> reqs,List<Position> areaToMap) {//polygon-> see #AreaSurvey Maneuver
        for(ImcSystem vehicle: ImcSystemsHolder.lookupActiveSystemVehicles())
            vehicles_id = VehicleRequirements.filter(reqs,((List<NVLVehicle>)  new NeptusVehicleAdapter(vehicle,STATE.values()[(int) (vehicle.getLastAnnounceStateReceived())])));
        //TODO define maneuvers -> goto sequence of subList Position (each vehicle area assignment)
       //ManeuverPayloadConfig specify payload in maneuver? 
    }
    

    /**
     * @return
     */
    PlanType toPlanType(MissionType mission) {
        PlanType result = new PlanType(mission);
        result.setId(getId());
        vehicles_id.stream().forEach(v -> result.addVehicle(v.getId()));
        for(Maneuver m: maneuvers){
            if(m.isInitialManeuver())
                result.getGraph().setInitialManeuver(m.getId());
            result.getGraph().addManeuver(m);
        }
        for(TransitionType tt: transitions){
            result.getGraph().addTransition(tt);
        }
        
        
        return result;
    }

/* (non-Javadoc)
 * @see pt.lsts.neptus.nvl.runtime.TaskSpecification#getRequirements()
 */
@Override
public List<VehicleRequirements> getRequirements() {
    // TODO Auto-generated method stub
    return null;
}

public void vehiclesAreaToMap(){
    //TODO according to vehicle capabilities assign proportional area to cover <- ZP task
}

/* (non-Javadoc)
 * @see pt.lsts.neptus.nvl.runtime.TaskSpecification#getId()
 */
@Override
public String getId() {
    return this.id;
}

/**
 * @return the imcmessage
 */
public IMCMessage getMessage() {
    return imcmessage;
}

/* (non-Javadoc)
 * @see pt.lsts.neptus.nvl.runtime.TaskSpecification#getAreaToMap()
 */
@Override
public List<Position> getAreaToMap() {
    // TODO Auto-generated method stub
    return null;
}

}
