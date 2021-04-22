package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class RepChange {
    FleetMemberAPI ship;
    PersonAPI captain;
    int captainOpinionChange, shuffleSign = 0, loyaltyLevel = Integer.MIN_VALUE;
    Trait trait, traitDown;

    float damageTakenFraction = 0, damageDealtPercent = 0, newRating = Integer.MIN_VALUE, ratingAdjustment = 0;
    boolean deployed, disabled;

    public LoyaltyLevel getLoyaltyLevel() {
        return loyaltyLevel == Integer.MIN_VALUE && RepRecord.existsFor(ship)
                ? RepRecord.get(ship).getLoyalty(captain)
                : (loyaltyLevel == Integer.MIN_VALUE ? LoyaltyLevel.INDIFFERENT : LoyaltyLevel.values()[loyaltyLevel]);
    }
    public void setLoyaltyChange(int loyaltyChange) {
        captainOpinionChange = loyaltyChange;

        if(RepRecord.existsFor(ship) && captain != null && !captain.isDefault()) {
            loyaltyLevel = RepRecord.get(ship).getLoyalty(captain).ordinal() + loyaltyChange;
        }
    }
    public void setTraitChange(Trait trait) {
        this.trait = trait;
        this.shuffleSign = 0;
    }
    public void setTraitChange(Trait trait[], int shuffleSign) {
        this.trait = trait.length > 0 ? trait[0] : null;
        this.traitDown = trait.length > 1 ? trait[1] : null;
        this.shuffleSign = shuffleSign;
    }

    public RepChange(FleetMemberAPI ship) {
        this.ship = ship;
    }
    public RepChange(FleetMemberAPI ship, CampaignScript.BattleRecord br, boolean deployed, boolean disabled) {
        this.ship = ship;
        this.deployed = deployed;
        this.disabled = disabled;
        this.captain = br.originalCaptain != null ? br.originalCaptain : ship.getCaptain();
        this.damageTakenFraction = br.fractionDamageTaken;
        this.damageDealtPercent = deployed ? br.damageDealt : 0;
    }

    public boolean hasAnyChanges() {
        return trait != null || (captainOpinionChange != 0 && captain != null && !captain.isDefault());
    }

    public boolean apply(boolean allowNotification) {
        if(ship == null) throw new RuntimeException("RepChange ship is null");

        boolean showNotification = false;
        RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);
        MessageIntel intel = new MessageIntel();
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();

        if(newRating != Integer.MIN_VALUE) rep.setRating(newRating / 100f);

        if(!hasAnyChanges()) return false;

        for(Trait t : rep.getTraits()) {
            if(shuffleSign == 0 && trait != null && t.getType() == trait.getType()) return false;
        }

        if(allowNotification && pf != null && ModPlugin.SHOW_NEW_TRAIT_NOTIFICATIONS) {
            for (FleetMemberAPI s : pf.getFleetData().getMembersListCopy()) {
                if(ship == s) {
                    showNotification = true;
                    break;
                }
            }
        }

        if(trait != null) {
            if(shuffleSign == 0) {
                rep.getTraits().add(trait);
//                intel.addLine("The %s has gained a reputation for %s %s.", Misc.getTextColor(),
//                        new String[]{ship.getShipName(), trait.getDescPrefix(ship.getMinCrew() > 0), trait.getName(ship.getMinCrew() > 0).toLowerCase()},
//                        Misc.getTextColor(), Misc.getTextColor(), trait.getHighlightColor());
            } else {
                //intel.addLine("Trait shuffle");

                for(int i = 1; i < rep.getTraits().size(); i++) {
                    Trait t = rep.getTraits().get(i);

                    if(t.equals(trait)) {
                        int shuffleDirection = t.effectSign > 0 ? -shuffleSign : shuffleSign;

                        if(i + shuffleDirection >= rep.getTraits().size()) {
                            rep.getTraits().remove(i);
                        } else {
                            rep.getTraits().set(i, rep.getTraits().get(i + shuffleDirection));
                            rep.getTraits().set(i + shuffleDirection, t);
                        }

                        break;
                    }
                }
            }
        }

        if(captainOpinionChange != 0 && captain != null && !captain.isDefault() && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
            LoyaltyLevel ll = rep.getLoyalty(captain);
            int llSign = (int)Math.signum(ll.getIndex());
            String change = captainOpinionChange < 0 ? "lost faith in" : "grown to trust";
            String merelyMaybe = (llSign != 0 && llSign != captainOpinionChange) ? "merely " : "";

            rep.adjustLoyalty(captain, captainOpinionChange);

//            intel.addLine("The crew of the %s has %s %s and is now " + merelyMaybe + "%s.", Misc.getTextColor(),
//                    new String[] { ship.getShipName(), change, captain.getNameString().trim(), rep.getLoyalty(captain).getName().toLowerCase() },
//                    Misc.getTextColor(), Misc.getTextColor(), Misc.getTextColor(),
//                    captainOpinionChange < 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor());
        }

        if(showNotification && trait != null) {
            boolean requiresCrew = ship.getMinCrew() > 0 || ship.isMothballed();
            String message = "The " + ship.getShipName() + " has gained a reputation for "
                    + trait.getDescPrefix(requiresCrew) + " %s";

            intel.setSound("ui_intel_something_posted");
            intel.setIcon(ship.getHullSpec().getSpriteName());
            intel.addLine(message, Misc.getTextColor(), new String[] { trait.getName(requiresCrew) },
                    trait.effectSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor());
            Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.REFIT_TAB, ship);
        }

        RepRecord.updateRepHullMod(ship);

        return showNotification;
    }

    public void addCommentsToTooltip(TooltipMakerAPI tooltip) {
        if(!hasAnyChanges()) return;
        if(ship == null) throw new RuntimeException("RepChange ship is null");
        if(!RepRecord.existsFor(ship)) throw new RuntimeException("Ship with RepChange has no RepRecord");

        RepRecord rep = RepRecord.get(ship);
        String message;
        boolean requiresCrew = ship.getMinCrew() > 0 || ship.isMothballed();

        if(trait != null) {
            if (shuffleSign == 0) {
                message = "The " + ship.getShipName() + " has gained a reputation for "
                        + trait.getDescPrefix(requiresCrew) + " %s  %s";
                String desc = (trait.effectSign * trait.getType().getBaseBonus() > 0 ? "(increased " : "(reduced ")
                        + trait.getType().getEffectDescription() + ")";

                tooltip.addPara(message, 3, Misc.getTextColor(), trait.getLowerCaseName(requiresCrew), desc)
                        .setHighlightColors(trait.effectSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                                Misc.getGrayColor());
            } else if(!rep.hasTrait(trait)) {
                message = "The " + ship.getShipName() + " is no longer known for "
                        + trait.getDescPrefix(requiresCrew) + " %s.";

                tooltip.addPara(message, 3, Misc.getTextColor(),
                        shuffleSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                        trait.getLowerCaseName(requiresCrew));
            } else {
                int shuffleDirection = trait.effectSign > 0 ? -shuffleSign : shuffleSign;

                if(traitDown == null) {
                    message = "The reputation of the " + ship.getShipName()
                            + " for " + trait.getDescPrefix(requiresCrew) + " %s has become %s prominent.";

                    tooltip.addPara(message, 3, Misc.getTextColor(),
                            shuffleSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                            trait.getLowerCaseName(requiresCrew), (shuffleDirection < 0 ? "more" : "less"));
                } else {
                    message = "The " + ship.getShipName() + " is now known better for "
                            + trait.getDescPrefix(true) + " %s than " + traitDown.getDescPrefix(true) + " %s";

                    Color hl = traitDown != null && trait.getEffectSign() == traitDown.getEffectSign()
                            ? Misc.getHighlightColor()
                            : (shuffleSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor());

                    tooltip.addPara(message, 3, Misc.getTextColor(), hl, trait.getLowerCaseName(requiresCrew),
                            traitDown.getLowerCaseName(requiresCrew));
                }
            }
        }

        if(captainOpinionChange != 0 && captain != null && !captain.isDefault() && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {

            LoyaltyLevel ll = getLoyaltyLevel();
            int llSign = (int)Math.signum(ll.getIndex());
            String change = captainOpinionChange < 0 ? "lost faith in" : "grown to trust";
            String merelyMaybe = llSign != captainOpinionChange ? "merely " : "";
            String crewOrAI = ship.getMinCrew() > 0 || ship.isMothballed() ? "crew" : "AI persona";
            message = BaseIntelPlugin.BULLET + "The " + crewOrAI + " of the " + ship.getShipName() + " has %s "
                    + captain.getNameString().trim() + " and is now " + merelyMaybe + "%s.";

            tooltip.addPara(message, 3, Misc.getTextColor(),
                    captainOpinionChange > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                    change, ll.getName().toLowerCase());
        }

        //tooltip.addButton("View", ship, 50, 20, 3);
    }
}
