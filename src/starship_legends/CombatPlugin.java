package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatFleetManager;
import starship_legends.hullmods.Reputation;

import java.util.*;
import java.util.List;

public class CombatPlugin implements EveryFrameCombatPlugin {
    boolean damageHasBeenLogged = false, isFirstFrame = true;

    int tick = 0;
    Random rand = new Random();
    Map<String, String> wingSourceMap = new HashMap(), sectionSourceMap = new HashMap();
    Map<String, Float> hpTotals = new HashMap();
    Map<String, Float> stationDeployCosts = new HashMap();
    Set<String> playerShips = new HashSet();
    String msg = "";

    public static Map<String, Float> CURSED = new HashMap();
    public static Map<String, Float> PHASEMAD = new HashMap();

    void disableRandom(ShipAPI ship) {
        List<ShipEngineControllerAPI.ShipEngineAPI> engines = ship.getEngineController().getShipEngines();
        List<WeaponAPI> weapons = ship.getUsableWeapons();

        if(!ship.getUsableWeapons().isEmpty() && rand.nextBoolean() && weapons.size() > 0) {
            weapons.get(rand.nextInt(weapons.size())).disable();
        } else if(!ship.getEngineController().isFlamedOut() && engines.size() > 0) {
            engines.get(rand.nextInt(engines.size())).disable();
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        try {
            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

            CombatEngineAPI engine = Global.getCombatEngine();

            if(engine == null) return;

            if(!engine.isPaused() && rand.nextFloat() <= amount) { // Should happen about once per second, on average
                for(ShipAPI ship : engine.getShips()) {
                    if(ship.isFighter()) continue;

                    String id = ship.getFleetMemberId();

                    if(CURSED.containsKey(id) && CURSED.get(id) >= rand.nextInt(101)) disableRandom(ship);
                    if(PHASEMAD.containsKey(id) && ship.isPhased() && PHASEMAD.get(id) >= rand.nextInt(101)) disableRandom(ship);
                }
            }

            if (!engine.isInCampaign() || Global.getSector() == null || Global.getSector().getPlayerFleet() == null)
                return;

            if(isFirstFrame) {
                CampaignScript.collectRealSnapshotInfoIfNeeded();

                for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    playerShips.add(ship.getId());
                }

                isFirstFrame = false;
            }

            if (tick++ % 300 == 0) {
                for (ShipAPI ship : engine.getShips()) {
                    FleetMemberAPI fm = getFleetMember(ship);

                    if(fm == null) continue;

                    String key = fm.getId();

                    if (ship.isFighter()
                            && ship.getWing() != null
                            && ship.getWing().getSourceShip() != null
                            && !wingSourceMap.containsKey(key)) {

                        ShipAPI source = ship.getWing().getSourceShip();

                        if(source.getParentStation() != null) source = source.getParentStation();

                        //Global.getLogger(this.getClass()).info("Wing found: " + ship.getHullSpec().getHullId() + " - " + key + " - " + source.getFleetMemberId());

                        wingSourceMap.put(key, source.getFleetMemberId());
                    } else if(ship.getParentStation() != null
                            //&& ship.getMaxFlux() > 0
                            && !sectionSourceMap.containsKey(key)
                            && hpTotals.containsKey(ship.getParentStation().getFleetMemberId())) {
                        //Global.getLogger(this.getClass()).info("Section found: " + ship.getHullSpec().getHullId() + " fp: " + getShipStrength(fm) + " id: " + ship.getParentStation().getFleetMemberId() + " hp: " + ship.getMaxHitpoints());

                        sectionSourceMap.put(key, ship.getParentStation().getFleetMemberId());

                        key = ship.getParentStation().getFleetMemberId();
                        fm =  getFleetMember(ship.getParentStation());

                        hpTotals.put(key, ship.getMaxHitpoints() + (hpTotals.containsKey(key) ? hpTotals.get(key) : 0));
                        stationDeployCosts.put(key, Util.getShipStrength(fm));
                    } else if(//!ship.isStation()
                            //&& ship.getMaxFlux() > 0
                            !ship.isFighter()
                            && ship.getParentStation() == null
                            && !hpTotals.containsKey(key)) {
                        //Global.getLogger(this.getClass()).info("Core found: " + ship.getHullSpec().getHullId() + " fp: " + getShipStrength(fm) + " id: " + ship.getFleetMemberId() + " hp: " + ship.getMaxHitpoints() + " station: " + ship.isStation());

                        hpTotals.put(key, ship.getMaxHitpoints() + (hpTotals.containsKey(key) ? hpTotals.get(key) : 0));
                    }
                }

            }

            if (!damageHasBeenLogged && (engine.isCombatOver() || isEnemyInFullRetreat())) {
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

            //msg = "Damage dealt by " + e.getKey().getHullId() + " (" + e.getKey().getOwner() + ")";

            float dmg = getFpWorthOfDamageDealt(e.getValue(), sourceID);

            if (playerShips.contains(sourceID)) CampaignScript.recordDamageDealt(sourceID, dmg);

            //Global.getLogger(this.getClass()).info(msg);
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
            float hp = hpTotals.containsKey(targetID)
                    ? hpTotals.get(targetID)
                    : target.getStats().getHullBonus().computeEffective(target.getHullSpec().getHitpoints());

            if(target.isFighterWing() || target.isMothballed() || target.isCivilian() || dmg <= 1) continue;

            //msg += "\r      " +  sourceID + " dealt " + dmg + "/" + hp + " damage to " + target.getHullId() + " (" + target.getOwner() + ")";

            if(playerShips.contains(targetID)) {
                float damageFraction = dmg / hp;

                if(ModPlugin.HULL_REGEN_SHIPS.containsKey(target.getHullId())) {
                    damageFraction *= ModPlugin.HULL_REGEN_SHIPS.get(target.getHullId());
                    Global.getLogger(CampaignScript.class).info("damage reduced to " + ModPlugin.HULL_REGEN_SHIPS.get(target.getHullId()) + " for " + target.getHullId());
                }

                CampaignScript.recordDamageSustained(targetID, damageFraction);
            }

            if(playerShips.contains(sourceID) && !playerShips.contains(targetID) && !target.isAlly()
                    && dealer.getOwner() != target.getOwner()) {

                acc += (dmg / hp) * (stationDeployCosts.containsKey(targetID)
                        ? stationDeployCosts.get(targetID)
                        : Util.getShipStrength(target));
            }
        }

        //if(acc > 0) msg += "\r      Total FP worth of damage: " + acc;

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

    public boolean isEnemyInFullRetreat() {
        CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(FleetSide.ENEMY);
        if (cfm == null) return false;

        CombatTaskManagerAPI taskManager = cfm.getTaskManager(false);
        if (taskManager == null) return false;

        boolean allDeployedRetreating = true;
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

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) { }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) { }

    @Override
    public void renderInUICoords(ViewportAPI viewport) { }

    @Override
    public void init(CombatEngineAPI engine) {}
}
