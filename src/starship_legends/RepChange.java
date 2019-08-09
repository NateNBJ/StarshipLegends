package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import starship_legends.hullmods.Reputation;

public class RepChange {
    FleetMemberAPI ship;
    PersonAPI captain;
    int captainOpinionChange, shuffleSign = 0, loyaltyLevel = Integer.MIN_VALUE;
    Trait trait, traitDown;

    float damageTakenFraction = 0, damageDealtPercent = 0, newRating = Integer.MIN_VALUE, ratingAdjustment = 0;
    boolean deployed, disabled;

    public LoyaltyLevel getLoyaltyLevel() {
        return loyaltyLevel == Integer.MIN_VALUE && RepRecord.existsFor(ship)
                ? RepRecord.get(ship).getLoyaltyLevel(captain)
                : (loyaltyLevel == Integer.MIN_VALUE ? LoyaltyLevel.INDIFFERENT : LoyaltyLevel.values()[loyaltyLevel]);
    }
    public void setLoyaltyChange(int loyaltyChange) {
        captainOpinionChange = loyaltyChange;

        if(RepRecord.existsFor(ship) && captain != null && !captain.isDefault()) {
            loyaltyLevel = RepRecord.get(ship).getLoyaltyLevel(captain).ordinal() + loyaltyChange;
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

    public RepChange(FleetMemberAPI ship, PersonAPI captain) {
        this.ship = ship;
        this.captain = captain;
    }

    public boolean hasAnyChanges() {
        return trait != null || (captainOpinionChange != 0 && captain != null && !captain.isDefault());
    }

    public boolean apply(boolean allowNotification) {
        if(ship == null) throw new RuntimeException("RepChange ship is null");
        if(!RepRecord.existsFor(ship)) throw new RuntimeException("Ship with RepChange has no RepRecord");

        boolean showNotification = false;
        RepRecord rep = RepRecord.get(ship);
        MessageIntel intel = new MessageIntel();
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();

        if(newRating != Integer.MIN_VALUE) rep.setRating(newRating / 100f);

        if(!hasAnyChanges()) return false;

        for(Trait t : rep.getTraits()) {
            if(shuffleSign == 0 && trait != null && t.getType() == trait.getType()) return false;
        }

        if(allowNotification && pf != null && ModPlugin.SHOW_REPUTATION_CHANGE_NOTIFICATIONS) {
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
            LoyaltyLevel ll = rep.getLoyaltyLevel(captain);
            int llSign = (int)Math.signum(ll.getIndex());
            String change = captainOpinionChange < 0 ? "lost faith in" : "grown to trust";
            String merelyMaybe = (llSign != 0 && llSign != captainOpinionChange) ? "merely " : "";

            rep.adjustOpinionOfOfficer(captain, captainOpinionChange);

//            intel.addLine("The crew of the %s has %s %s and is now " + merelyMaybe + "%s.", Misc.getTextColor(),
//                    new String[] { ship.getShipName(), change, captain.getNameString().trim(), rep.getLoyaltyLevel(captain).getName().toLowerCase() },
//                    Misc.getTextColor(), Misc.getTextColor(), Misc.getTextColor(),
//                    captainOpinionChange < 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor());
        }

        if(showNotification) {
            intel.setSound("ui_intel_something_posted");
            intel.setIcon(ship.getHullSpec().getSpriteName());
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

        if(trait != null) {
            if (shuffleSign == 0) {
                message = BaseIntelPlugin.BULLET + "The " + ship.getShipName() + " has gained a reputation for "
                        + trait.getDescPrefix(ship.getMinCrew() > 0) + " %s  %s";
                String desc = (trait.effectSign * trait.getType().getBaseBonus() > 0 ? "(increased " : "(reduced ")
                        + trait.getType().getEffectDescription() + ")";

                tooltip.addPara(message, 3, Misc.getTextColor(), trait.getLowerCaseName(ship.getMinCrew() > 0), desc)
                        .setHighlightColors(trait.effectSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                                Misc.getGrayColor());
            } else if(!rep.hasTrait(trait)) {
                message = BaseIntelPlugin.BULLET + "The " + ship.getShipName() + " is no longer known for "
                        + trait.getDescPrefix(ship.getMinCrew() > 0) + " %s.";

                tooltip.addPara(message, 3, Misc.getTextColor(),
                        shuffleSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                        trait.getLowerCaseName(ship.getMinCrew() > 0));
            } else {
                int shuffleDirection = trait.effectSign > 0 ? -shuffleSign : shuffleSign;

                if(traitDown == null) {
                    message = BaseIntelPlugin.BULLET + "The reputation of the " + ship.getShipName()
                            + " for " + trait.getDescPrefix(ship.getMinCrew() > 0) + " %s has become %s prominent.";

                    tooltip.addPara(message, 3, Misc.getTextColor(),
                            shuffleSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                            trait.getLowerCaseName(ship.getMinCrew() > 0), (shuffleDirection < 0 ? "more" : "less"));
                } else {
                    message = BaseIntelPlugin.BULLET + "The " + ship.getShipName() + " is now known better for "
                            + trait.getDescPrefix(true) + " %s than " + traitDown.getDescPrefix(true) + " %s";

                    tooltip.addPara(message, 3, Misc.getTextColor(),
                            shuffleSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                            trait.getLowerCaseName(ship.getMinCrew() > 0),
                            traitDown.getLowerCaseName(ship.getMinCrew() > 0));
                }
            }
        }

        if(captainOpinionChange != 0 && captain != null && !captain.isDefault() && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
            LoyaltyLevel ll = getLoyaltyLevel();
            int llSign = (int)Math.signum(ll.getIndex());
            String change = captainOpinionChange < 0 ? "lost faith in" : "grown to trust";
            String merelyMaybe = llSign != captainOpinionChange ? "merely " : "";
            String crewOrAI = ship.getMinCrew() > 0 ? "crew" : "AI persona";
            message = BaseIntelPlugin.BULLET + "The " + crewOrAI + " of the " + ship.getShipName() + " has %s "
                    + captain.getNameString().trim() + " and is now " + merelyMaybe + "%s.";

            tooltip.addPara(message, 3, Misc.getTextColor(),
                    captainOpinionChange > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                    change, ll.getName().toLowerCase());
        }

        //tooltip.addButton("View", ship, 50, 20, 3);
    }
}
