package starship_legends.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.hullmods.CompromisedStructure;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.Console;
import starship_legends.*;

import java.awt.*;
import java.util.*;
import java.util.List;


public class Reputation extends BaseHullMod {
    private final static Map<ShipAPI.HullSize, Integer> FLAT_EFFECT_MULT = new HashMap();
    static {
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.DEFAULT, 0);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.FIGHTER, 0);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.FRIGATE, 1);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.DESTROYER, 2);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.CRUISER, 4);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.CAPITAL_SHIP, 6);
    }

    static transient Saved<HashSet<FleetMemberAPI>> shipsOfNote = new Saved<>("shipsOfNote", new HashSet<FleetMemberAPI>());
    public static transient HashMap<String, FleetMemberAPI> moduleMap = new HashMap<>(); // TODO - Remove this nonsense if Alex fixes getFleetMember

    public static final String ENEMY_HULLMOD_ID = "sun_sl_enemy_reputation";
    public static final Color BORDER_COLOR = new Color(147, 102, 50, 0);
    public static final Color NAME_COLOR = new Color(224, 184, 139, 255);

    public static FleetMemberAPI getFleetMember(MutableShipStatsAPI stats) {
        String id = stats.getVariant().getHullVariantId();

        return moduleMap.containsKey(id) ? moduleMap.get(id) : stats.getFleetMember();
    }
    public static FleetMemberAPI getFleetMember(ShipAPI ship) {
        String id = ship.getVariant().getHullVariantId();

        return moduleMap.containsKey(id) ? moduleMap.get(id) : ship.getFleetMember();
    }
    public static void printRegistry() {
        String msg = "";

        for(FleetMemberAPI ship : shipsOfNote.val) {
            msg += System.lineSeparator() + ship.toString();
        }

        if(Global.getSettings().getModManager().isModEnabled("lw_console")) Console.showMessage(msg);
        Global.getLogger(RepRecord.class).info(msg);
    }
    public static float getFlatEffectMult(ShipAPI.HullSize size) {
        return size == null ? 1 : FLAT_EFFECT_MULT.get(size);
    }
    public static void addShipOfNote(FleetMemberAPI ship) {
        shipsOfNote.val.add(ship);
    }
    public static void removeShipOfNote(FleetMemberAPI ship) {
        shipsOfNote.val.remove(ship);
    }
    public static Collection<FleetMemberAPI> getShipsOfNote() {
        return shipsOfNote.val;
    }

    public static void applyEffects(FleetMemberAPI ship) {
        if(!RepRecord.existsFor(ship)) return;

        RepRecord rep = RepRecord.get(ship);

        applyEffects(rep, ship, ship.getHullSpec().getHullSize(), ship.getCaptain(), ship.getStats(),
                ship.isFighterWing(), rep.getTeir().getHullModID());
    }
    public static void applyEffects(RepRecord rep, FleetMemberAPI ship, ShipAPI.HullSize size, PersonAPI captain, MutableShipStatsAPI stats, boolean isFighter, String id) {
        try {
            int traitsLeft = Math.min(rep.getTraits().size(), Trait.getTraitLimit());
            int loyaltyEffectAdjustment = 0;
            float effectMult = id.equals(ENEMY_HULLMOD_ID) ? ModPlugin.FLEET_TRAIT_EFFECT_MULT : 1.0f;

            if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault()) {
                loyaltyEffectAdjustment = rep.getLoyalty(captain).getTraitAdjustment();
                stats.getCRLossPerSecondPercent().modifyPercent(id, rep.getLoyalty(captain).getCrDecayMult());
            }

            for(Trait trait : rep.getTraits()) {
                if(traitsLeft <= 0) break;

                float e = Math.max(ModPlugin.MINIMUM_EFFECT_REDUCTION_PERCENT, effectMult
                        * trait.getEffect(RepRecord.getTierFromTraitCount(traitsLeft--), loyaltyEffectAdjustment, size));

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
                            stats.getArmorDamageTakenMult().modifyPercent(id, -e);
                            break;
                    }
                } else {
                    switch (trait.getTypeId()) {
                        case "ballistics_rof":
                            stats.getBallisticRoFMult().modifyPercent(id, e);
                            break;
                        case "energy_cost":
                            stats.getEnergyWeaponFluxCostMod().modifyPercent(id, e);
                            break;
                        case "pd_range":
                            stats.getNonBeamPDWeaponRangeBonus().modifyPercent(id, e);
                            stats.getBeamPDWeaponRangeBonus().modifyPercent(id, e);
                            break;
                        case "pd_damage":
                            stats.getDamageToMissiles().modifyPercent(id, e);
                            stats.getDamageToFighters().modifyPercent(id, e);
                            break;
                        case "dmod_integrity":
                            int dmods = 0;
                            for(String modId : ship.getVariant().getPermaMods()) {
                                if(Global.getSettings().getHullModSpec(modId).hasTag("dmod")) dmods++;
                            }

                            stats.getHullBonus().modifyPercent(id, e * dmods);
                            break;
                        case "missile_guidance":
                            stats.getMissileGuidance().modifyPercent(id, e);
                            stats.getMissileAccelerationBonus().modifyPercent(id, e);
                            stats.getMissileMaxSpeedBonus().modifyPercent(id, e);
                            stats.getMissileTurnAccelerationBonus().modifyPercent(id, e);
                            stats.getMissileMaxTurnRateBonus().modifyPercent(id, e);
                            break;
                        case "missile_reload":
                            stats.getMissileRoFMult().modifyPercent(id, e);
                            break;
                        case "cursed":
                            CombatPlugin.CURSED.put(ship.getId(), e);
                            break;
                        case "phase_mad":
                            CombatPlugin.PHASEMAD.put(ship.getId(), e);
                            break;
                        case "dmod_effect":
                            stats.getDynamic().getStat(Stats.DMOD_EFFECT_MULT).modifyPercent(id, e);
                            break;
                        case "survey":
                            stats.getDynamic().getMod(Stats.getSurveyCostReductionId(Commodities.SUPPLIES)).modifyFlat(id, -e);
                            break;
                        case "blockade_runner":
                            stats.getZeroFluxSpeedBoost().modifyPercent(id, e);
                            break;
                        case "drive_stabilizer":
                            stats.getSensorProfile().modifyFlat(id, e);
                            break;
                        case "command_support":
                            stats.getDynamic().getMod(Stats.COMMAND_POINT_RATE_FLAT).modifyFlat(id, e * 0.01f);
                            break;
                        case "nav_support":
                            stats.getDynamic().getMod(Stats.COORDINATED_MANEUVERS_FLAT).modifyFlat(id, e);
                            break;
                        case "ecm_support":
                            stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(id, e);
                            break;
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
                            stats.getDynamic().getStat(Stats.FIGHTER_CREW_LOSS_MULT).modifyPercent(id, e);
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
                            stats.getMissileWeaponRangeBonus().modifyPercent(id, e);
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
    public static void applyEffects(FleetMemberAPI ship, ShipAPI.HullSize size, PersonAPI captain, MutableShipStatsAPI stats, boolean isFighter, String id) {
        try {
            if(!ModPlugin.settingsHaveBeenRead()) return;
            if(ship == null || !RepRecord.existsFor(ship)) return;

            RepRecord rep = RepRecord.get(ship);
            applyEffects(rep, ship, size, captain, stats, isFighter, id);
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        try {
            if(!ModPlugin.settingsHaveBeenRead() || ModPlugin.REMOVE_ALL_DATA_AND_FEATURES)
                return;

            FleetMemberAPI ship = getFleetMember(stats);

            if(ship == null) return;

            try {
                if (!stats.getVariant().getStationModules().isEmpty()) {
                    moduleMap.clear();

                    for (Map.Entry<String, String> e : stats.getVariant().getStationModules().entrySet()) {
                        ShipVariantAPI module = stats.getVariant().getModuleVariant(e.getKey());

                        moduleMap.put(module.getHullVariantId(), ship);
                    }
                }
            } catch (Exception e) {
                ModPlugin.reportCrash(e, false);
            }

            if(id.equals(ENEMY_HULLMOD_ID)) {
                if(FactionConfig.getEnemyFleetRep() != null) {
                    applyEffects(FactionConfig.getEnemyFleetRep(), ship, hullSize, ship.getFleetCommanderForStats(),
                            stats, false, id);
                }
            } else {
                applyEffects(ship, hullSize, ship.getCaptain(), stats, false, id);

                if(ship.getOwner() != 0) {
                    // Try to prevent famous enemy ships from being randomly selected as recoverable, since they are
                    //  manually forced to be recoverable anyway
                    stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, Float.MIN_VALUE * 0.8f);
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

        PersonAPI liege = ship.getOriginalOwner() == 0 ? ship.getCaptain() : ship.getFleetMember().getFleetCommanderForStats();

        applyEffects(ship.getFleetMember(), ship.getHullSize(), liege, fighter.getMutableStats(), true, id);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return "";

        FleetMemberAPI fm = getFleetMember(ship);

        if(fm == null) return "SHIP NOT FOUND";

        return index == 1 && RepRecord.existsFor(fm)
                ? (int)(RepRecord.get(fm).getRating() * 100) + "%"
                : fm.getShipName();
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        try {
            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

            FleetMemberAPI fm = getFleetMember(ship);

            if(fm == null || !RepRecord.existsFor(fm)) {
                String msg = fm == null
                        ? "ERROR: Could not find ship: "
                            + ship.toString()
                        : "ERROR: No reputation record was found for this ship." +
                            "\n Hull ID: " + fm.getHullSpec().getHullId() +
                            "\n Is registered: " + shipsOfNote.val.contains(fm) +
                            "\n Ship ID: " + fm.getId();

                msg += "\n Please notify the mod author with this information.";

                if(Global.getSettings().getModManager().isModEnabled("lw_console")) {
                    msg += "\n\n Note that changes made using console commands from the refit screen will not be reflected immediately.";
                }

                tooltip.addPara(msg, 10, Misc.getNegativeHighlightColor());

                return;
            }

            RepRecord rep = RepRecord.get(fm);
            Trait.Tier previousTier = Trait.Tier.UNKNOWN;
            int traitsLeft = Math.min(rep.getTraits().size(), Trait.getTraitLimit());
            int loyaltyEffectAdjustment = 0;
            boolean requiresCrew = fm.getMinCrew() > 0 || fm.isMothballed();

            if(Global.getSettings().isDevMode() || (ModPlugin.SHOW_COMBAT_RATINGS && !fm.getHullSpec().isCivilianNonCarrier())) {
                tooltip.addPara("It has a rating of %s on the Evans-Zhao combat performance scale.", 10,
                        Misc.getHighlightColor(), (int) (rep.getRating() * 100f) + "%");

                if(Global.getSettings().isDevMode()) {
                    tooltip.addPara("(Dev) Actual bonus fraction of traits: %s.", 10, Misc.getGrayColor(),
                            Misc.getHighlightColor(), (int) (rep.getFractionOfBonusEffectFromTraits() * 100f) + "%");
                    tooltip.addPara("(Dev) Strength estimation: %s.", 10, Misc.getGrayColor(),
                            Misc.getHighlightColor(), (int) Util.getShipStrength(fm) + "");
                }
            }

            Trait.Tier tier = RepRecord.getTierFromTraitCount(traitsLeft);

            if(tier == Trait.Tier.UNKNOWN) {
                String details = "\n Limit: "  + Trait.getTraitLimit()
                        + "\n Traits: " + rep.getTraits()
                        + "\n Please notify the mod author with this information.";

                tooltip.addPara(tier.getFlavorText(requiresCrew) + details, 10, Misc.getNegativeHighlightColor());
            } else {
                tooltip.addPara(tier.getFlavorText(requiresCrew), 10, Misc.getGrayColor(), Misc.getGrayColor(),
                        fm.getShipName());
            }

            if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
                if(fm.getCaptain() != null && !fm.getCaptain().isDefault()) {
                    loyaltyEffectAdjustment = rep.getLoyalty(fm.getCaptain()).getTraitAdjustment();
                    LoyaltyLevel ll = rep.getLoyalty(fm.getCaptain());
                    Color clr = ll.ordinal() < LoyaltyLevel.INDIFFERENT.ordinal()
                            ? Misc.getNegativeHighlightColor()
                            : Misc.getHighlightColor();
                    String message = "The " + (requiresCrew ? "crew" : "AI persona") + " of the " + fm.getShipName()
                            + " is %s " + ll.getPreposition()
                            + " its captain, " + fm.getCaptain().getNameString().trim();
                    String upOrDown = ll.getCrDecayMult() < 0 ? "reducing" : "increasing";

                    if (ll.getCrDecayMult() == 0 && ll.getTraitAdjustment() == 0) message += ".";
                    else if (ll.getTraitAdjustment() == 0) message += ", " + upOrDown + " CR decay rate by %s.";
                    else message += ", " + upOrDown + " CR decay rate by %s and %s the ship's traits.";

                    tooltip.beginImageWithText(fm.getCaptain().getPortraitSprite(), 64).addPara(message, 3, clr,
                            ll.getName(), (int) Math.abs(ll.getCrDecayMult()) + "%", ll.getTraitAdjustDesc());
                    tooltip.addImageWithText(8);
                } else if(!rep.getOpinionsOfOfficers().isEmpty()) {
                    int bestOpinion = 0;
                    Set<String> trustedOfficers = new HashSet<>();
                    Set<String> unfoundOfficers = new HashSet<>();

                    for(Map.Entry<String, Integer> e : rep.getOpinionsOfOfficers().entrySet()) {
                        boolean officerNotFound = !e.getKey().equals(Global.getSector().getPlayerPerson().getId());

                        if(officerNotFound) {
                            for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                                if (e.getKey().equals(officer.getPerson().getId())) {
                                    officerNotFound = false;
                                    break;
                                }
                            }
                        }

                        if(officerNotFound) {
                            unfoundOfficers.add(e.getKey());
                            continue;
                        }

                        if(e.getValue() > bestOpinion) {
                            trustedOfficers.clear();
                            bestOpinion = e.getValue();
                        }

                        if(e.getValue() == bestOpinion) trustedOfficers.add(e.getKey());
                    }

                    for(String id : unfoundOfficers) rep.getOpinionsOfOfficers().remove(id);

                    if(bestOpinion > 0 && !trustedOfficers.isEmpty()) {
                        LoyaltyLevel ll = LoyaltyLevel.values()[bestOpinion + ModPlugin.LOYALTY_LIMIT];
                        String message = "The " + (requiresCrew ? "crew" : "AI persona") + " is %s " + ll.getPreposition()
                                + " the following officers:" + fm.getCaptain().getNameString().trim();
                        tooltip.addPara(message, 10, Misc.getHighlightColor(), ll.getName());
                        List<PersonAPI> captains = new LinkedList<>();
                        captains.add(Global.getSector().getPlayerPerson());

                        for (OfficerDataAPI od : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                            captains.add(od.getPerson());
                        }

                        for (int i = 0; i < captains.size(); i++) {
                            PersonAPI captain = captains.get(i);

                            if(trustedOfficers.contains(captain.getId())) {
                                tooltip.beginImageWithText(captain.getPortraitSprite(), 24)
                                    .addPara(captain.getNameString().trim(), 3);
                                tooltip.addImageWithText(i + 1 == captains.size() ? 10 : 3);
                            }
                        }
                    }
                }
            }

            for(Trait trait : rep.getTraits()) {
                if(traitsLeft <= 0) break;

                Trait.Tier currentTier = RepRecord.getTierFromTraitCount(traitsLeft--);

                if(currentTier != previousTier) {
                    tooltip.addPara("%s traits:", 10, Color.WHITE, currentTier.getDisplayName());
                    previousTier = currentTier;
                }

                trait.addParagraphTo(tooltip, currentTier, loyaltyEffectAdjustment, requiresCrew, hullSize, false, false);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public Color getBorderColor() {
        return BORDER_COLOR;
    }

    @Override
    public Color getNameColor() {
        return NAME_COLOR;
    }

    @Override
    public int getDisplaySortOrder() {
        return -6;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 0;
    }
}
