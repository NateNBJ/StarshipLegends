package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import starship_legends.hullmods.Reputation;

public class RepChange {
    FleetMemberAPI ship;
    PersonAPI captain;
    int captainOpinionChange, shuffleSign = 0, damageTakenPercent = 0, damageDealtPercent = 0;
    Trait trait;
    boolean deployed;

    public RepChange(FleetMemberAPI ship, PersonAPI captain) {
        this.ship = ship;
        this.captain = captain;
    }

    public boolean hasAnyChanges() {
        return trait != null || (captainOpinionChange != 0 && captain != null && !captain.isDefault());
    }

    public boolean apply() {
        if(!hasAnyChanges()) return false;
        if(ship == null) throw new RuntimeException("RepChange ship is null");
        if(!RepRecord.existsFor(ship)) throw new RuntimeException("Ship with RepChange has no RepRecord");

        boolean showNotification = false;
        RepRecord rep = RepRecord.get(ship);
        MessageIntel intel = new MessageIntel();
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();

        for(Trait t : rep.getTraits()) {
            if(trait != null && t.getType() == trait.getType()) return false;
        }

        if(pf != null && ModPlugin.SHOW_REPUTATION_CHANGE_NOTIFICATIONS) {
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
                intel.addLine("The %s has gained a reputation for %s %s.", Misc.getTextColor(),
                        new String[]{ship.getShipName(), trait.getDescPrefix(ship.getMinCrew() > 0), trait.getName(ship.getMinCrew() > 0).toLowerCase()},
                        Misc.getTextColor(), Misc.getTextColor(), trait.getHighlightColor());
            } else {
                rep.getTraits().add(trait);
                intel.addLine("Trait shuffle");
                // TODO
            }
        }

        if(captainOpinionChange != 0 && captain != null && !captain.isDefault() && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
            LoyaltyLevel ll = rep.getLoyaltyLevel(captain);
            int llSign = (int)Math.signum(ll.getIndex());
            String change = captainOpinionChange < 0 ? "lost faith in" : "grown to trust";
            String merelyMaybe = (llSign != 0 && llSign != captainOpinionChange) ? "merely " : "";

            rep.adjustOpinionOfOfficer(captain, captainOpinionChange);

            intel.addLine("The crew of the %s has %s %s and is now " + merelyMaybe + "%s.", Misc.getTextColor(),
                    new String[] { ship.getShipName(), change, captain.getNameString().trim(), rep.getLoyaltyLevel(captain).getName().toLowerCase() },
                    Misc.getTextColor(), Misc.getTextColor(), Misc.getTextColor(),
                    captainOpinionChange < 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor());
        }

        if(showNotification) {
            intel.setSound("ui_intel_something_posted");
            intel.setIcon(ship.getHullSpec().getSpriteName());
            Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.REFIT_TAB, ship);
        }

        updateRepHullMod(ship);

        return showNotification;
    }


    static void updateRepHullMod(FleetMemberAPI ship) {
        if(!RepRecord.existsFor(ship)) return;

        Trait.Teir teir = RepRecord.get(ship).getTeir();
        ShipVariantAPI v;

        if(teir == Trait.Teir.UNKNOWN) return;

        if(ship.getVariant().isStockVariant()) {
            v = ship.getVariant().clone();
            v.setSource(VariantSource.REFIT);
            //v.setHullVariantId(Misc.genUID());
            ship.setVariant(v, false, false);
        } else v = ship.getVariant();

        v.setHullVariantId(ModPlugin.VARIANT_PREFIX + ship.getId());

        v.removePermaMod("sun_sl_notable");
        v.removePermaMod("sun_sl_wellknown");
        v.removePermaMod("sun_sl_famous");
        v.removePermaMod("sun_sl_legendary");
        v.addPermaMod(teir.getHullModID());

        Reputation.addShipOfNote(ship);

        ship.updateStats();
    }
}
