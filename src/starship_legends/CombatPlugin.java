package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatFleetManager;

import java.util.*;

public class CombatPlugin implements EveryFrameCombatPlugin {
    boolean damageHasBeenLogged = false;

    int tick = 0;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        try {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (engine == null || !engine.isInCampaign() || Global.getSector() == null || Global.getSector().getPlayerFleet() == null)
                return;

            CampaignScript.collectRealSnapshotInfoIfNeeded(engine.getDamageData());


            if (tick++ % 300 == 0) {
                for (ShipAPI ship : engine.getShips()) {
                    FleetMemberAPI fm = getFleetMember(ship);

                    if (!ship.isFighter() || ship.getWing() == null || fm == null) continue;

                    String key = fm.getId();

                    if (!CampaignScript.wingSourceMap.containsKey(key)) {
                        Global.getLogger(this.getClass()).info(ship.getHullSpec().getHullId() + " - " + key + " - " + ship.getWing().getSourceShip().getFleetMemberId());
                        CampaignScript.wingSourceMap.put(key, ship.getWing().getSourceShip().getFleetMemberId());
                    }
                }

            }

//            if (!damageHasBeenLogged && damageData.isCombatOver()) {
//
//                Set<String> playerShips = new HashSet<>();
//                for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
//                    playerShips.add(ship.getId());
//                }
//
//                for (Map.Entry<FleetMemberAPI, CombatDamageData.DealtByFleetMember> e : damageData.getDamageData().getDealt().entrySet()) {
//
//                    String sourceID = e.getKey().isFighterWing() && wingSourceMap.containsKey(e.getKey().getId())
//                            ? wingSourceMap.get(e.getKey().getId())
//                            : e.getKey().getId();
//
//                    Global.getLogger(this.getClass()).info("Recording damage for " + e.getKey().getHullId() + " from " + sourceID + " " + wingSourceMap.containsKey(e.getKey().getId()) + " - " + e.getKey().getId());
//
//                    if (playerShips.contains(sourceID)) {
//                        CampaignScript.recordDamage(sourceID, getFpWorthOfDamageDealt(e.getValue()));
//                    }
//                }
//
//                damageHasBeenLogged = true;
//            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
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

    public FleetMemberAPI getFleetMember(ShipAPI ship) {
        FleetMemberAPI fleetMember = null;

        CombatFleetManager manager = CombatEngine.getInstance().getFleetManager(ship.getOriginalOwner());
        if (manager != null && manager.getDeployedFleetMemberEvenIfDisabled(ship) != null) {
            fleetMember = manager.getDeployedFleetMemberEvenIfDisabled(ship).getMember();
            if (fleetMember != null) {
                if (ship.getOriginalOwner() == 0) {
                    fleetMember.setOwner(0);
                } else if (ship.getOriginalOwner() == 1) {
                    fleetMember.setOwner(1);
                }
            }
        }
        return fleetMember;
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
