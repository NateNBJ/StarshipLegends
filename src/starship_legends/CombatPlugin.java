package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.combat.CombatEngine;

import java.util.*;

public class CombatPlugin implements EveryFrameCombatPlugin {
    boolean damageHasBeenLogged = false;

    float getFpWorthOfDamageDealt(CombatDamageData.DealtByFleetMember dmgBy) {
        if(dmgBy == null || dmgBy.getDamage().isEmpty()) return 0;

        float acc = 0;

        for(Map.Entry<FleetMemberAPI, CombatDamageData.DamageToFleetMember> e : dmgBy.getDamage().entrySet()) {
            FleetMemberAPI target = e.getKey();
            float dmg = e.getValue().hullDamage;
            float hp = target.getStats().getHullBonus().computeEffective(target.getHullSpec().getHitpoints());

            if(target.isAlly() || target.isFighterWing() || target.isMothballed() || target.isCivilian() || dmg <= 1)
                continue;

            //String msg = name + " dealt " + dmg + "/" + hp + " damage to " + target.getHullId();
            //Global.getLogger(this.getClass()).info(msg);
            //Global.getCombatEngine().getCombatUI().addMessage(1, Misc.getTextColor(), msg);

            acc += (dmg / hp) * target.getDeploymentCostSupplies();
        }

        return acc;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CampaignScript.collectRealSnapshotInfoIfNeeded();

        CombatEngineAPI engine = Global.getCombatEngine();

        if(!damageHasBeenLogged && engine != null && engine.isCombatOver() && Global.getCombatEngine().isInCampaign()
                && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {

            Set<FleetMemberAPI> playerShips = new HashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());

            for(Map.Entry<FleetMemberAPI, CombatDamageData.DealtByFleetMember> e : Global.getCombatEngine().getDamageData().getDealt().entrySet()) {
                if(playerShips.contains(e.getValue().getMember())) {
                    CampaignScript.recordDamage(e.getValue().getMember(), getFpWorthOfDamageDealt(e.getValue()));
                }
            }

            damageHasBeenLogged = true;
        }

//
//        projectilesThatHitThisFrame.clear();
////null).getDamageTo(null).hullDamage;
//        for(DamagingProjectileAPI proj : Global.getCombatEngine().getProjectiles()) {
//            if(proj.didDamage()) projectilesThatHitThisFrame.add(proj);
//        }
//
//        for(ShipAPI ship : Global.getCombatEngine().getShips()) {
//            float hullDmg = hullLevelLastFrame.containsKey(ship) ? hullLevelLastFrame.get(ship) - ship.getHullLevel() : 0;
//
//            if(hullDmg > 0 && !ship.isFighter()) {
//                for(DamagingProjectileAPI proj : projectilesThatHitThisFrame) {
//                    if(proj.getDamageTarget() == ship) {
//                        CombatDamageData.DealtByFleetMember d = Global.getCombatEngine().getDamageData().getDealtBy(Global.getSector().getPlayerFleet().getFlagship());
//
//                        float dmg = 0;
//
//                        for(Map.Entry<FleetMemberAPI, CombatDamageData.DamageToFleetMember> e : d.getDamage().entrySet()) {
//                            dmg += e.getValue().hullDamage;
//                        }
//
//                        Global.getCombatEngine().getCombatUI().addMessage(1, Misc.getTextColor(), "" + dmg);
//                    }
//                }
//            }
//
//            hullLevelLastFrame.put(ship, ship.getHullLevel());
//        }
    }


//    static Map<ShipAPI, Float> hullLevelLastFrame = new HashMap<>();
//
//    List<DamagingProjectileAPI> projectilesThatHitThisFrame = new ArrayList<>();

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) { }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) { }

    @Override
    public void renderInUICoords(ViewportAPI viewport) { }

    @Override
    public void init(CombatEngineAPI engine) { }
}
