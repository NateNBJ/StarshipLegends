package starship_legends.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import starship_legends.*;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Reputation extends BaseHullMod {
    private final static Map<ShipAPI.HullSize, Integer> FLAT_EFFECT_MULT = new HashMap();
    static {
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.FRIGATE, 1);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.DESTROYER, 2);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.CRUISER, 4);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.CAPITAL_SHIP, 8);
    }

    static Saved<HashMap<String, FleetMemberAPI>> shipsOfNote = new Saved<>("shipsOfNote", new HashMap<String, FleetMemberAPI>());

    public static float getFlatEffectMult(ShipAPI.HullSize size) {
        return size == null ? 1 : FLAT_EFFECT_MULT.get(size);
    }

    public static void addShipOfNote(FleetMemberAPI ship) {
        shipsOfNote.val.put(ship.getVariant().getHullVariantId(), ship);
    }
    public static void removeShipOfNote(String shipID) {
        shipsOfNote.val.remove(shipID);
    }
    public static Collection<FleetMemberAPI> getShipsOfNote() {
        return shipsOfNote.val.values();
    }

    void applyEffects(String shipID, ShipAPI.HullSize size, PersonAPI captain, MutableShipStatsAPI stats, boolean isFighter, String id) {
        try {
            if(!ModPlugin.settingsHaveBeenRead()) return;
            if(shipID == null || !RepRecord.existsFor(shipID)) return;
//            if(shipID == null) throw new RuntimeException("Could not find matching ship for reputation hullmod");
//            if(!RepRecord.existsFor(shipID)) throw new RuntimeException("Reputation hullmod exists without RepRecord entry for ship");

            RepRecord rep = RepRecord.get(shipID);
            int traitsLeft = Math.min(rep.getTraits().size(), Trait.getTraitLimit());
            int loyaltyEffectAdjustment = 0;

            if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault() && !isFighter) {
                loyaltyEffectAdjustment = rep.getLoyaltyLevel(captain).getTraitAdjustment();
//                int before = (int)stats.getCRLossPerSecondPercent().computeEffective(100);
                stats.getCRLossPerSecondPercent().modifyPercent(id, rep.getLoyaltyLevel(captain).getCrDecayMult());
//                int after = (int)stats.getCRLossPerSecondPercent().computeEffective(100);
//                Global.getLogger(this.getClass()).info(captain.getNameString() + " : " + before + " - " + after + " = " + (before - after));
            }

            for(Trait trait : rep.getTraits()) {
                if(traitsLeft <= 0) break;

                float e = trait.getEffect(RepRecord.getTierFromTraitCount(traitsLeft--), loyaltyEffectAdjustment, size);

                if(isFighter) {
                    switch (trait.getTypeId()) {
                        case "fighter_damage":
                            stats.getBallisticWeaponDamageMult().modifyPercent(id, e);
                            stats.getEnergyWeaponDamageMult().modifyPercent(id, e);
                            stats.getMissileWeaponDamageMult().modifyPercent(id, e);
                            break;
                        case "fighter_speed":
                            stats.getAcceleration().modifyPercent(id, e);
                            stats.getMaxSpeed().modifyPercent(id, e);
                            stats.getTurnAcceleration().modifyPercent(id, e);
                            break;
                        case "fighter_durability":
                            stats.getHullDamageTakenMult().modifyPercent(id, -e);
                            stats.getShieldDamageTakenMult().modifyPercent(id, -e);
                            stats.getHullDamageTakenMult().modifyPercent(id, -e);
                            break;
                    }
                } else {
                    switch (trait.getTypeId()) {
                        case "cr_cap":
                            stats.getMaxCombatReadiness().modifyFlat(id, e * 0.01f, trait.getName(true));
                            break;
                        case "cr_recovery":
                            stats.getBaseCRRecoveryRatePercentPerDay().modifyPercent(id, e);
                            break;
                        case "damage":
                            stats.getBallisticWeaponDamageMult().modifyPercent(id, e);
                            stats.getEnergyWeaponDamageMult().modifyPercent(id, e);
                            stats.getMissileWeaponDamageMult().modifyPercent(id, e);
                            break;
                        case "malfunction":
                            stats.getCriticalMalfunctionChance().modifyPercent(id, e);
                            stats.getEngineMalfunctionChance().modifyPercent(id, e);
                            stats.getShieldMalfunctionChance().modifyPercent(id, e);
                            stats.getWeaponMalfunctionChance().modifyPercent(id, e);
                            break;
                        case "mount_durability":
                            stats.getWeaponHealthBonus().modifyPercent(id, e);
                            break;
                        case "engine_durability":
                            stats.getEngineHealthBonus().modifyPercent(id, e);
                            break;
                        case "crew_casualties":
                            stats.getCrewLossMult().modifyPercent(id, e);
                            break;
                        case "recovery_chance":
                            stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyPercent(id, e);
                            stats.getBreakProb().modifyPercent(id, -e);
                            break;
                        case "maneuverability":
                            stats.getAcceleration().modifyPercent(id, e);
                            stats.getDeceleration().modifyPercent(id, e);
                            stats.getTurnAcceleration().modifyPercent(id, e);
                            stats.getMaxTurnRate().modifyPercent(id, e);
                            break;
                        case "hull_integrity":
                            stats.getHullBonus().modifyPercent(id, e);
                            break;
                        case "shield_strength":
                            stats.getShieldDamageTakenMult().modifyPercent(id, e);
                            break;
                        case "armor_strength":
                            stats.getArmorBonus().modifyPercent(id, e);
                            break;
                        case "engine_power":
                            stats.getMaxSpeed().modifyPercent(id, e);
                            break;
                        case "emp_resistance":
                            stats.getEmpDamageTakenMult().modifyPercent(id, -e);
                            break;
                        case "shield_stability":
                            stats.getShieldUpkeepMult().modifyPercent(id, e);
                            break;
                        case "peak_cr_time":
                            stats.getPeakCRDuration().modifyPercent(id, e);
                            break;
                        case "overload_time":
                            stats.getOverloadTimeMod().modifyPercent(id, e);
                            break;
                        case "flux_capacity":
                            stats.getFluxCapacity().modifyPercent(id, e);
                            break;
                        case "flux_dissipation":
                            stats.getFluxDissipation().modifyPercent(id, e);
                            break;
                        case "sensor_strength":
                            stats.getSensorStrength().modifyPercent(id, e);
                            break;
                        case "sensor_profile":
                            stats.getSensorProfile().modifyPercent(id, e);
                            break;
                        case "refit_time":
                            stats.getFighterRefitTimeMult().modifyPercent(id, e);
                            break;
                        case "salvage":
                            stats.getDynamic().getMod(Stats.SALVAGE_VALUE_MULT_MOD).modifyPercent(id, e);
                            break;
                        case "cargo_capacity":
                            stats.getCargoMod().modifyFlat(id, e);
                            break;
                        case "fuel_efficiency":
                            stats.getFuelUseMod().modifyPercent(id, e);
                            break;
                        case "fuel_capacity":
                            stats.getFuelMod().modifyFlat(id, e);
                            break;
                        case "supply_upkeep":
                            stats.getSuppliesPerMonth().modifyPercent(id, e);
                            break;
                        case "phase_cost":
                            stats.getPhaseCloakActivationCostBonus().modifyPercent(id, e);
                            stats.getPhaseCloakUpkeepCostBonus().modifyPercent(id, e);
                            break;
                        case "phase_cooldown":
                            stats.getPhaseCloakCooldownBonus().modifyPercent(id, e);
                            break;
                        case "range":
                            stats.getBallisticWeaponRangeBonus().modifyPercent(id, e);
                            stats.getEnergyWeaponRangeBonus().modifyPercent(id, e);
                            break;
                        case "repair":
                            stats.getCombatEngineRepairTimeMult().modifyPercent(id, -e);
                            stats.getCombatWeaponRepairTimeMult().modifyPercent(id, -e);
                            stats.getRepairRatePercentPerDay().modifyPercent(id, e);
                            break;
                        case "weapon_stability":
                            stats.getRecoilPerShotMult().modifyPercent(id, e);
                            break;
                        case "turret_rotation":
                            stats.getWeaponTurnRateBonus().modifyPercent(id, e);
                            break;
                        case "vent_rate":
                            stats.getVentRateMult().modifyPercent(id, e);
                            break;
                        case "shield_raise_rate":
                            stats.getShieldUnfoldRateMult().modifyPercent(id, e);
                            break;
                    }
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    FleetMemberAPI findShip(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats) {
        String key = stats.getVariant().getHullVariantId();

        if(shipsOfNote.val.containsKey(key)) return shipsOfNote.val.get(key);

        List<FleetMemberAPI> members;

        try {
            members = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();
        } catch (NullPointerException e) { return null; }

        if(stats.getEntity() != null) {
            if (stats.getEntity() instanceof FleetMemberAPI) {
                // TODO
                return (FleetMemberAPI)stats.getEntity();
            } else if (stats.getEntity() instanceof ShipAPI) {
                for (FleetMemberAPI s : members) {
                    if (s == stats.getEntity()) {
                        // TODO
                        //shipsOfNote.val.put(key, s);
                        return s;
                    }
                }
            }
        }

        for (FleetMemberAPI s : members) {
            if (key.equals(s.getVariant().getHullVariantId())) {
            //if(stats == s.getStats()) {
                shipsOfNote.val.put(key, s);
                return s;
            }
        }

        return null;
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        try {
            FleetMemberAPI ship = findShip(hullSize, stats);

            if(ship == null) {
                Global.getLogger(this.getClass()).info("Could not find ship with matching variant id: " + stats.getVariant().getHullVariantId());
                return;
            }

            applyEffects(ship.getId(), hullSize, ship.getCaptain(), stats, false, id);
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        applyEffects(ship.getFleetMemberId(), ship.getHullSize(), ship.getCaptain(), fighter.getMutableStats(), true, id);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        return index == 1 && RepRecord.existsFor(ship)
                ? (int)(RepRecord.get(ship).getRating() * 100) + "%"
                : ship.getName();
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        try {
            if(ship == null || !RepRecord.existsFor(ship)) return;
//            if(ship == null) throw new RuntimeException("Could not find matching ship for reputation hullmod");
//            if(!RepRecord.existsFor(ship)) throw new RuntimeException("Reputation hullmod exists without RepRecord entry for ship");

            RepRecord rep = RepRecord.get(ship);
            Trait.Teir previousTeir = Trait.Teir.UNKNOWN;
            int traitsLeft = Math.min(rep.getTraits().size(), Trait.getTraitLimit());
            int loyaltyEffectAdjustment = 0;
            boolean requiresCrew = ship.getMutableStats().getMinCrewMod().computeEffective(ship.getHullSpec().getMinCrew()) > 0;

            if(Global.getSettings().isDevMode() || (ModPlugin.SHOW_COMBAT_RATINGS && !ship.getHullSpec().isCivilianNonCarrier())) {
                tooltip.addPara("It has a rating of %s on the Evans-Zhao combat performance scale.", 10,
                        Misc.getHighlightColor(), (int) (rep.getRating() * 100f) + "%");

                if(Global.getSettings().isDevMode()) {
                    tooltip.addPara("(Dev) Actual bonus fraction of traits: %s.", 10, Misc.getGrayColor(),
                            Misc.getHighlightColor(), (int) (rep.getFractionOfBonusEffectFromTraits() * 100f) + "%");
                }
            }

            tooltip.addPara(RepRecord.getTierFromTraitCount(traitsLeft).getFlavorText(requiresCrew), 10,
                    Misc.getGrayColor(), Misc.getGrayColor(), ship.getName());

            if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && !ship.getCaptain().isDefault()) {
                loyaltyEffectAdjustment = rep.getLoyaltyLevel(ship.getCaptain()).getTraitAdjustment();
                LoyaltyLevel ll = rep.getLoyaltyLevel(ship.getCaptain());
                Color clr = ll.ordinal() < LoyaltyLevel.INDIFFERENT.ordinal()
                        ? Misc.getNegativeHighlightColor()
                        : Misc.getHighlightColor();
                String message = "The " + (requiresCrew ? "crew" : "AI persona") + " of the " + ship.getName()
                        + " is %s " + ll.getPreposition()
                        + " its captain, " + ship.getCaptain().getNameString().trim();
                String upOrDown = ll.getCrDecayMult() < 0 ? "reducing" : "increasing";

                if(ll.getCrDecayMult() == 0 && ll.getTraitAdjustment() == 0) message += ".";
                else if(ll.getTraitAdjustment() == 0) message += ", " + upOrDown + " CR decay rate by %s.";
                else message += ", " + upOrDown + " CR decay rate by %s and %s the ship's traits.";

                tooltip.beginImageWithText(ship.getCaptain().getPortraitSprite(), 64).addPara(message, 3, clr,
                        ll.getName(), (int)Math.abs(ll.getCrDecayMult()) + "%", ll.getTraitAdjustDesc());
                tooltip.addImageWithText(8);
            }

            for(Trait trait : rep.getTraits()) {
                if(traitsLeft <= 0) break;

                Trait.Teir teir = RepRecord.getTierFromTraitCount(traitsLeft--);

                if(teir != previousTeir) {
                    tooltip.addPara("%s traits:", 10, Color.WHITE, teir.getDisplayName());
                    previousTeir = teir;
                }

                trait.addParagraphTo(tooltip, teir, loyaltyEffectAdjustment, requiresCrew, hullSize);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
}
