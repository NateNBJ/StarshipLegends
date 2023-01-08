package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.thoughtworks.xstream.XStream;

import java.awt.*;

public class RepChange {
    class Intel extends MessageIntel {
        RepChange rc;
        Trait.Tier tier;

        Intel(RepChange rc, Trait.Tier tier) {
            this.rc = rc;
            this.tier = tier;

            setSound(tier == Trait.Tier.Legendary ? "ui_increase_ship_rep_max" : "ui_increase_ship_rep");
            //setIcon(rc.ship.getHullSpec().getSpriteName());
            setIcon(tier.getIntelIcon());
        }
        @Override

        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            boolean requiresCrew = Util.isShipCrewed(ship);
            ShipHullSpecAPI hull = rc.ship.getHullSpec().getBaseHull() == null
                    ? rc.ship.getHullSpec()
                    : rc.ship.getHullSpec().getBaseHull();

//            info.beginImageWithText(tier.getIcon(), TRAIT_TIER_SIZE)
//                    .addPara("The " + rc.ship.getShipName() + " is now known for:", 0);
//            info.addImageWithText(0);

            info.addPara("The " + rc.ship.getShipName() + " (" + hull.getHullName()
                    + ") is now known for:", 0);
            rc.addTraitBulletToTooltip(info, trait1, requiresCrew);
            rc.addTraitBulletToTooltip(info, trait2, requiresCrew);
        }
    }

    public static void configureXStream(XStream x) {
        x.alias("sun_sl_rc", RepChange.class);
        x.aliasAttribute(RepChange.class, "ship", "s");
        x.aliasAttribute(RepChange.class, "captain", "c");
        x.aliasAttribute(RepChange.class, "captainOpinionChange", "oc");
        x.aliasAttribute(RepChange.class, "loyaltyLevel", "ll");
        x.aliasAttribute(RepChange.class, "trait1", "t1");
        x.aliasAttribute(RepChange.class, "trait2", "t2");
        x.aliasAttribute(RepChange.class, "damageDealtPercent", "dd");
        x.aliasAttribute(RepChange.class, "damageTakenFraction", "dt");
        x.aliasAttribute(RepChange.class, "xpEarned", "xp");
        x.aliasAttribute(RepChange.class, "deployed", "dp");
        x.aliasAttribute(RepChange.class, "disabled", "ds");
    }
    FleetMemberAPI ship;
    PersonAPI captain;
    int captainOpinionChange, loyaltyLevel = Integer.MIN_VALUE;
    Trait trait1, trait2;
    float damageDealtPercent = 0, damageTakenFraction = 0, xpEarned = 0;
    boolean deployed, disabled, foughtInBattle = true;

    public LoyaltyLevel getLoyaltyLevel() {
        return loyaltyLevel == Integer.MIN_VALUE && RepRecord.isShipNotable(ship)
                ? RepRecord.get(ship).getLoyalty(captain)
                : (loyaltyLevel == Integer.MIN_VALUE ? LoyaltyLevel.INDIFFERENT : LoyaltyLevel.values()[loyaltyLevel]);
    }
    public void setLoyaltyChange(int loyaltyChange) {
        captainOpinionChange = loyaltyChange;

        if(RepRecord.isShipNotable(ship) && captain != null && !captain.isDefault()) {
            loyaltyLevel = RepRecord.get(ship).getLoyalty(captain).ordinal() + loyaltyChange;
            loyaltyLevel = Math.max(Math.min(loyaltyLevel, LoyaltyLevel.INSPIRED.ordinal()), 0);
        }
    }

    public RepChange(FleetMemberAPI ship) {
        this.ship = ship;
    }
    public RepChange(FleetMemberAPI ship, CampaignScript.BattleRecord br, boolean deployed, boolean disabled, boolean foughtInBattle) {
        this.ship = ship;
        this.deployed = deployed;
        this.disabled = disabled;
        this.foughtInBattle = foughtInBattle;
        this.captain = br.originalCaptain != null ? br.originalCaptain : ship.getCaptain();
        this.damageTakenFraction = br.fractionDamageTaken;
        this.damageDealtPercent = deployed ? br.damageDealt : 0;
    }

    public boolean hasAnyChanges() {
        return hasNewTraitPair() || (captainOpinionChange != 0 && captain != null && !captain.isDefault());
    }
    public boolean hasNewTraitPair() { return trait1 != null && trait2 != null; }

    public boolean apply(boolean allowNotification) {
        if(ship == null) throw new RuntimeException("RepChange ship is null");

        boolean showNotification = false;
        RepRecord rep = RepRecord.getOrCreate(ship);
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();

        if(!hasAnyChanges()) return false;

        if(allowNotification && pf != null && ModPlugin.SHOW_NEW_TRAIT_NOTIFICATIONS) {
            for (FleetMemberAPI s : pf.getFleetData().getMembersListCopy()) {
                if(ship == s) {
                    showNotification = true;
                    break;
                }
            }
        }

        Trait.Tier prevTier = rep.getTier();

        if(trait1 != null) rep.getTraits().add(trait1);
        if(trait2 != null) rep.getTraits().add(trait2);

        if(rep.getTier() == Trait.Tier.Legendary && prevTier != Trait.Tier.Legendary) {
            RepRecord.getQueuedStories().add(ship.getId());
        }

        if(captainOpinionChange != 0 && captain != null && !captain.isDefault() && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
            LoyaltyLevel ll = rep.getLoyalty(captain);

            rep.adjustLoyalty(captain, captainOpinionChange);
        }

        if(showNotification && hasAnyChanges()) {
            Intel intel = new Intel(this, rep.getTier());
            Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.REFIT_TAB, ship);
        }

        RepRecord.updateRepHullMod(ship);

        return showNotification;
    }

    public void addTraitBulletToTooltip(TooltipMakerAPI tooltip, Trait trait, boolean requiresCrew) {
        tooltip.addPara("  - " + "%s %s %s", 0,
            new Color[] {
                Misc.getTextColor(),
                trait.getEffectSign() > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                Misc.getGrayColor()
            },
            trait.getDescPrefix(requiresCrew),
            trait.getName(requiresCrew),
            trait.getParentheticalDescription()
        );
    }
    public int addCommentsToTooltip(TooltipMakerAPI tooltip) {
        if(!hasAnyChanges()) return 0;
        if(ship == null) throw new RuntimeException("RepChange ship is null");

        int lines = 1;
        int PAD = 0;
        boolean requiresCrew = Util.isShipCrewed(ship);

        tooltip.addPara(" " + ship.getShipName() + ":", PAD);
//        RepRecord rep = RepRecord.getOrCreate(ship);
//        tooltip.beginImageWithText(rep.getTier().getIcon(), TRAIT_TIER_SIZE)
//                .addPara(ship.getShipName() + ":", PAD);
//        tooltip.addImageWithText(0);

        if(hasNewTraitPair()) {
            String
                    pref1 = trait1.getDescPrefix(requiresCrew).toLowerCase(),
                    pref2 = "and" + trait2.getDescPrefix(requiresCrew, pref1).toLowerCase();

            // Leave the double space below. Otherwise the highlight color to the left of "and" will sometimes bleed over to it for some reason...
            tooltip.addPara(BaseIntelPlugin.BULLET + "Now known for %s %s %s %s %s %s", PAD,
                new Color[]{
                        Misc.getTextColor(),
                        trait1.effectSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                        Misc.getGrayColor(),
                        Misc.getTextColor(),
                        trait2.effectSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                        Misc.getGrayColor()
                },
                pref1,
                trait1.getLowerCaseName(requiresCrew),
                trait1.getParentheticalDescription(),
                pref2,
                trait2.getLowerCaseName(requiresCrew),
                trait2.getParentheticalDescription()
            );

            lines += 1;
        }

        if(captainOpinionChange != 0 && captain != null && !captain.isDefault() && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
            LoyaltyLevel ll = getLoyaltyLevel();
            int llSign = (int)Math.signum(ll.getIndex());
            String highlight1 = requiresCrew
                    ? captainOpinionChange < 0 ? "lost faith in" : "gained trust in"
                    : captainOpinionChange < 0 ? "degraded" : "improved";
            String highlight2 = requiresCrew ? ll.getName().toLowerCase() : ll.getAiIntegrationStatusName().toLowerCase();
            String crewOrAI = requiresCrew ? "crew" : "ship's integration status";
            String message = BaseIntelPlugin.BULLET + "The " + crewOrAI;

            if(ll == LoyaltyLevel.INSPIRED) {
                highlight1 = highlight2;

                message += requiresCrew
                    ? " has become %s by " + captain.getNameString().trim()
                    : " has become %s";

                message += ", and will remain so for at least " + RepRecord.getDaysOfInspirationRemaining(ship, captain)
                        + " more days.";
            } else if(ll == LoyaltyLevel.FIERCELY_LOYAL && captainOpinionChange < 0) {
                message += requiresCrew
                    ? " is no longer inspired by " + captain.getNameString().trim() + "."
                    : " is no longer amplified.";
            } else {
                String merelyMaybe = llSign != (int)Math.signum(captainOpinionChange) ? "merely " : "";
                message += requiresCrew
                    ? " has %s " + captain.getNameString().trim() + " and is now " + merelyMaybe + "%s."
                    : " has %s and is now " + merelyMaybe + "%s.";
            }

            tooltip.addPara(message, PAD, Misc.getTextColor(),
                    captainOpinionChange > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                    highlight1, highlight2);

            lines += 1;
        }

        return lines;
    }
}
