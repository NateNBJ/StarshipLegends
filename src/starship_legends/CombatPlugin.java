package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatFleetManager;

import java.util.*;
import java.util.List;

public class CombatPlugin implements EveryFrameCombatPlugin {
    boolean damageHasBeenLogged = false, isFirstFrame = true;

    int tick = 0;
    Map<String, String> wingSourceMap = new HashMap<>(), sectionSourceMap = new HashMap<>();
    Map<String, Float> stationHpSums = new HashMap<>();
    Map<String, Float> stationDeployCosts = new HashMap<>();
//    Map<String, Float> hpHighs = new HashMap<>();
//    Map<String, Float> hpLows = new HashMap<>();
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

                    if(fm == null) continue;

                    String key = fm.getId();

//                    if(ship.getOriginalOwner() == 0) {
//                        float hp = ship.getHullLevel();
//
//                        if(!hpLows.containsKey(key) || hpLows.get(key) > hp) hpLows.put(key, hp);
//                        if(!hpHighs.containsKey(key) || hpHighs.get(key) < hp) hpHighs.put(key, hp);
//                    }

                    if (ship.isFighter() && ship.getWing() != null && !wingSourceMap.containsKey(key)) {
                        ShipAPI source = ship.getWing().getSourceShip();

                        if(source.getParentStation() != null) source = source.getParentStation();

                        //Global.getLogger(this.getClass()).info("Wing found: " + ship.getHullSpec().getHullId() + " - " + key + " - " + source.getFleetMemberId());

                        wingSourceMap.put(key, source.getFleetMemberId());
                    } else if(ship.getParentStation() != null && !sectionSourceMap.containsKey(key)) {
                        //Global.getLogger(this.getClass()).info("Section found: " + ship.getHullSpec().getHullId() + " - " + Math.max(fm.getFleetPointCost(), fm.getDeploymentCostSupplies()) + " - " + ship.getParentStation().getFleetMemberId());

                        sectionSourceMap.put(key, ship.getParentStation().getFleetMemberId());

                        key = ship.getParentStation().getFleetMemberId();
                        fm = getFleetMember(ship.getParentStation());

                        stationHpSums.put(key, ship.getMaxHitpoints() + (stationHpSums.containsKey(key) ? stationHpSums.get(key) : 0));
                        stationDeployCosts.put(key, Math.max(fm.getFleetPointCost(), fm.getDeploymentCostSupplies()));
                    }
                }

            }

            if (!damageHasBeenLogged && (engine.isCombatOver() || isEnemyInFullRetreat2())) {
                compileDamageDealt();

                damageHasBeenLogged = true;
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    void compileDamageDealt() {
        CombatDamageData damageData = Global.getCombatEngine().getDamageData();

        if(damageData == null || damageData.getDealt() == null) return;

        for (Map.Entry<FleetMemberAPI, CombatDamageData.DealtByFleetMember> e : damageData.getDealt().entrySet()) {

            String sourceID;

            if(sectionSourceMap.containsKey(e.getKey().getId())) {
                sourceID = sectionSourceMap.get(e.getKey().getId());
            } else if(e.getKey().isFighterWing() && wingSourceMap.containsKey(e.getKey().getId())) {
                sourceID = wingSourceMap.get(e.getKey().getId());
            } else {
                sourceID = e.getKey().getId();
            }

            msg = "Damage dealt by " + e.getKey().getHullId() + " (" + e.getKey().getOwner() + ")";

            float dmg = getFpWorthOfDamageDealt(e.getValue(), sourceID);

            if (playerShips.contains(sourceID)) CampaignScript.recordDamageDealt(sourceID, dmg);

            Global.getLogger(this.getClass()).info(msg);
        }
    }

    float getFpWorthOfDamageDealt(CombatDamageData.DealtByFleetMember dmgBy, String sourceID) {
        if(dmgBy == null || dmgBy.getDamage().isEmpty()) return 0;

        float acc = 0;
        FleetMemberAPI dealer = dmgBy.getMember();

        for(Map.Entry<FleetMemberAPI, CombatDamageData.DamageToFleetMember> e : dmgBy.getDamage().entrySet()) {
            FleetMemberAPI target = e.getKey();
            String targetID = sectionSourceMap.containsKey(target.getId())
                    ? sectionSourceMap.get(target.getId())
                    : target.getId();
            float dmg = e.getValue().hullDamage;
            float hp = sectionSourceMap.containsKey(target.getId())
                    ? stationHpSums.get(targetID)
                    : target.getStats().getHullBonus().computeEffective(target.getHullSpec().getHitpoints());

            if(target.isFighterWing() || target.isMothballed() || target.isCivilian() || dmg <= 1) continue;

            msg += "\r      " +  sourceID + " dealt " + dmg + "/" + hp + " damage to " + target.getHullId() + " (" + target.getOwner() + ")";

            if(playerShips.contains(targetID)) CampaignScript.recordDamageSustained(targetID, dmg / hp);

            if(playerShips.contains(sourceID) && !playerShips.contains(targetID) && !target.isAlly()
                    && dealer.getOwner() != target.getOwner()) {

                acc += (dmg / hp) * (stationDeployCosts.containsKey(targetID)
                        ? stationDeployCosts.get(targetID)
                        : target.getDeploymentCostSupplies());
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
