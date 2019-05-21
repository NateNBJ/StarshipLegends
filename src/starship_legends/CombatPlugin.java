package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatFleetManager;

import java.awt.*;
import java.util.*;
import java.util.List;

public class CombatPlugin implements EveryFrameCombatPlugin {
    boolean damageHasBeenLogged = false, isFirstFrame = true;

    int tick = 0;
    Map<String, String> wingSourceMap = new HashMap<>();
    Set<String> playerShips = new HashSet<>();
    String msg = "";

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        try {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (engine == null || !engine.isInCampaign() || Global.getSector() == null || Global.getSector().getPlayerFleet() == null)
                return;

            if(isFirstFrame) {
                CampaignScript.collectRealSnapshotInfoIfNeeded();

                for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    playerShips.add(ship.getId());
                    //Global.getLogger(this.getClass()).info("Is in fleet: " + ship.getHullId());
                }

                isFirstFrame = false;
            }


            if (tick++ % 300 == 0) {
                for (ShipAPI ship : engine.getShips()) {
                    FleetMemberAPI fm = getFleetMember(ship);

                    if (!ship.isFighter() || ship.getWing() == null || fm == null) continue;

                    String key = fm.getId();

                    if (!wingSourceMap.containsKey(key)) {
                        Global.getLogger(this.getClass()).info(ship.getHullSpec().getHullId() + " - " + key + " - " + ship.getWing().getSourceShip().getFleetMemberId());
                        wingSourceMap.put(key, ship.getWing().getSourceShip().getFleetMemberId());
                    }
                }

            }

            if (!damageHasBeenLogged && isEnemyInFullRetreat2()) {
                compileDamageDealt();

                damageHasBeenLogged = true;
            }
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

    void compileDamageDealt() {
        CombatDamageData damageData = Global.getCombatEngine().getDamageData();

        if(damageData == null || damageData.getDealt() == null) return;

        for (Map.Entry<FleetMemberAPI, CombatDamageData.DealtByFleetMember> e : damageData.getDealt().entrySet()) {

            String sourceID = e.getKey().isFighterWing() && wingSourceMap.containsKey(e.getKey().getId())
                    ? wingSourceMap.get(e.getKey().getId())
                    : e.getKey().getId();

            msg = "Damage dealt by " + e.getKey().getHullId() + " (" + e.getKey().getOwner() + ")";

            float dmg = getFpWorthOfDamageDealt(e.getValue());

            if (playerShips.contains(sourceID)) CampaignScript.recordDamageDealt(sourceID, dmg);

            Global.getLogger(this.getClass()).info(msg);
        }
    }

    float getFpWorthOfDamageDealt(CombatDamageData.DealtByFleetMember dmgBy) {
        if(dmgBy == null || dmgBy.getDamage().isEmpty()) return 0;

        float acc = 0;
        FleetMemberAPI dealer = dmgBy.getMember();

        for(Map.Entry<FleetMemberAPI, CombatDamageData.DamageToFleetMember> e : dmgBy.getDamage().entrySet()) {
            FleetMemberAPI target = e.getKey();
            float dmg = e.getValue().hullDamage;
            float hp = target.getStats().getHullBonus().computeEffective(target.getHullSpec().getHitpoints());

            if(target.isFighterWing() || target.isMothballed() || target.isCivilian() || dmg <= 1) continue;

            msg += "\r      " +  dealer.getHullId() + " dealt " + dmg + "/" + hp + " damage to " + target.getHullId() + " (" + target.getOwner() + ")";

            if(playerShips.contains(target.getId())) CampaignScript.recordDamageSustained(target.getId(), dmg / hp);

            if(playerShips.contains(dealer.getId()) && !playerShips.contains(target.getId()) && !target.isAlly()
                    && dealer.getOwner() != target.getOwner()) {

                acc += (dmg / hp) * target.getDeploymentCostSupplies();
            }
        }

        if(acc > 0) msg += "\r      Total FP worth of damage: " + acc;

        return acc;
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

    public boolean isEnemyInFullRetreat2() {
        CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(FleetSide.ENEMY);
        if (cfm == null) return false;

        CombatTaskManagerAPI taskManager = cfm.getTaskManager(false);
        if (taskManager == null) return false;

        boolean allDeployedRetreating = true;
        //for (DeployedFleetMember dfm : cfm.getDeployed()) {
        for (DeployedFleetMemberAPI dfm : cfm.getDeployedCopyDFM()) {
            if (dfm.isFighterWing()) continue;
            if (!dfm.getShip().isRetreating()) {
                allDeployedRetreating = false;
            }
        }

        if (!allDeployedRetreating) return false;

        cfm = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
        if (cfm == null) return false;
        if (cfm.getReservesCopy().isEmpty() && cfm.getDeployedCopy().isEmpty()) {
            return false;
        }

        cfm = Global.getCombatEngine().getFleetManager(FleetSide.ENEMY);
        return cfm.getReservesCopy().isEmpty() || taskManager.isInFullRetreat();
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
