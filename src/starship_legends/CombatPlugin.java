package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.*;

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
        float total = engines.size() + weapons.size();

        final int APROX_MAX_SLOTS = 30;

        if(rand.nextInt(APROX_MAX_SLOTS) < total) { // Prevent excessive disabling for ships with few mounts/engines
            if (!ship.getUsableWeapons().isEmpty() && rand.nextBoolean() && weapons.size() > 0) {
                weapons.get(rand.nextInt(weapons.size())).disable();
            } else if (!ship.getEngineController().isFlamedOut() && engines.size() > 0) {
                engines.get(rand.nextInt(engines.size())).disable();
            }
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        try {
            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

            CombatEngineAPI engine = Global.getCombatEngine();

            if(engine == null) return;

            if(!engine.isPaused() && rand.nextFloat() * 2 <= amount) { // Should happen about once per second, on average
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
                    FleetMemberAPI fm = ship.getFleetMember();

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
                        fm =  ship.getParentStation().getFleetMember();

                        hpTotals.put(key, ship.getMaxHitpoints() + (hpTotals.containsKey(key) ? hpTotals.get(key) : 0));
                        stationDeployCosts.put(key, Util.getShipStrength(fm, false));
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

//            if(Global.getSettings().getModManager().isModEnabled("RealisticCombat")) {
//                DamageReportManagerV1 damageData = DamageReportManagerV1.getDamageReportManager();
//
//                for (DamageReportV1 e : damageData.getDamageReports()) {
//                    if(e.getSource() instanceof ShipAPI && e.getTarget() instanceof ShipAPI) {
//                        FleetMemberAPI src = ((ShipAPI)e.getSource()).getFleetMember();
//                        FleetMemberAPI tgt = ((ShipAPI)e.getTarget()).getFleetMember();
//
//                        if(src == null || tgt == null) continue;
//
//                        Global.getCombatEngine().getDamageData().getDealtBy(src).addHullDamage(tgt, e.getHullDamage());
//                    }
//                }
//            }

            if (!damageHasBeenLogged && (engine.isCombatOver() || engine.isEnemyInFullRetreat())) {
                compileDamageDealt();

                damageHasBeenLogged = true;
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
//    void compileDamageDealtByRealisticCombat() {
//        DamageReportManagerV1 damageData = DamageReportManagerV1.getDamageReportManager();
//
//        if(damageData == null || damageData.getDamageReports() == null) return;
//
//        for (DamageReportV1 e : damageData.getDamageReports()) {
//            if(!(e.getSource() instanceof FleetMemberAPI && e.getTarget() instanceof FleetMemberAPI)) continue;
//
//            String sourceID;
//            float acc = 0;
//            FleetMemberAPI dealer = (FleetMemberAPI) e.getSource();
//            FleetMemberAPI target = (FleetMemberAPI) e.getTarget();
//
//            if(sectionSourceMap.containsKey(dealer.getId())) {
//                sourceID = sectionSourceMap.get(dealer.getId());
//            } else if(dealer.isFighterWing() && wingSourceMap.containsKey(dealer.getId())) {
//                sourceID = wingSourceMap.get(dealer.getId());
//            } else {
//                sourceID = dealer.getId();
//            }
//
//            String targetID = sectionSourceMap.containsKey(target.getId())
//                    ? sectionSourceMap.get(target.getId())
//                    : target.getId();
//            float dmg = e.getHullDamage();
//            float hp = hpTotals.containsKey(targetID)
//                    ? hpTotals.get(targetID)
//                    : target.getStats().getHullBonus().computeEffective(target.getHullSpec().getHitpoints());
//
//            if(target.isFighterWing() || target.isMothballed() || target.isCivilian() || dmg <= 1) continue;
//
//            //msg += "\r      " +  sourceID + " dealt " + dmg + "/" + hp + " damage to " + target.getHullId() + " (" + target.getOwner() + ")";
//
//            if(playerShips.contains(targetID)) {
//                float damageFraction = dmg / hp;
//
//                if(ModPlugin.HULL_REGEN_SHIPS.containsKey(target.getHullId())) {
//                    damageFraction *= ModPlugin.HULL_REGEN_SHIPS.get(target.getHullId());
//                    //Global.getLogger(CampaignScript.class).info("damage reduced to " + ModPlugin.HULL_REGEN_SHIPS.get(target.getHullId()) + " for " + target.getHullId());
//                }
//
//                CampaignScript.recordDamageSustained(targetID, damageFraction);
//            }
//
//            if(playerShips.contains(sourceID) && !playerShips.contains(targetID) && !target.isAlly()
//                    && dealer.getOwner() != target.getOwner()) {
//
//                acc += (dmg / hp) * (stationDeployCosts.containsKey(targetID)
//                        ? stationDeployCosts.get(targetID)
//                        : Util.getShipStrength(target, false));
//            }
//
//            //msg = "Damage dealt by " + source.getHullId() + " (" + source.getOwner() + ")";
//
//            if (playerShips.contains(sourceID)) CampaignScript.recordDamageDealt(sourceID, acc);
//
//            //Global.getLogger(this.getClass()).info(msg);
//        }
//    }
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
                    //Global.getLogger(CampaignScript.class).info("damage reduced to " + ModPlugin.HULL_REGEN_SHIPS.get(target.getHullId()) + " for " + target.getHullId());
                }

                CampaignScript.recordDamageSustained(targetID, damageFraction);
            }

            if(playerShips.contains(sourceID) && !playerShips.contains(targetID) && !target.isAlly()
                    && dealer.getOwner() != target.getOwner()) {

                acc += (dmg / hp) * (stationDeployCosts.containsKey(targetID)
                        ? stationDeployCosts.get(targetID)
                        : Util.getShipStrength(target, false));
            }
        }

        //if(acc > 0) msg += "\r      Total FP worth of damage: " + acc;

        return acc;
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