package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class BattleReport extends BaseIntelPlugin {
    static final float DURATION = 30;

    BattleAPI battle;
    FactionAPI enemyFaction;
    float battleDifficulty = 0;
    long timestamp;
    List<RepChange> changes = new LinkedList<>();

    public BattleReport(float battleDifficulty, BattleAPI battle) {
        this.battle = battle;
        this.battleDifficulty = battleDifficulty;
        this.timestamp = Global.getSector().getLastPlayerBattleTimestamp();

        if(battle != null) enemyFaction = battle.getPrimary(battle.getNonPlayerSide()).getFaction();
    }

    public void addChange(RepChange change) {
        changes.add(change);
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        String title = enemyFaction == null
                ? "Battle report"
                : "Report for battle against %s";

        info.addPara(title, 0f, Misc.getTextColor(), getFactionForUIColors().getColor(),
                enemyFaction == null ? "" : enemyFaction.getDisplayNameLongWithArticle());
    }

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() { return true; }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        try {
            int noteCount = 0;

            for (RepChange rc : changes) if (rc.hasAnyChanges()) noteCount++;

            float NOTE_HEIGHT = 64;
            String title = enemyFaction == null
                    ? "Battle report"
                    : "Report for the battle against %s " + Misc.getAgoStringForTimestamp(timestamp).toLowerCase();
            Color difficultyColor = battleDifficulty > 1.25f
                    ? Misc.getPositiveHighlightColor()
                    : (battleDifficulty < 0.75f ? Misc.getNegativeHighlightColor() : Misc.getTextColor());
            String dmgMaybe = width > 1200 ? "Damage " : "";
            float w = width - 40, totalHeight = 214 + Math.max(0, changes.size() - 3) * 20;
            TooltipMakerAPI outer = panel.createUIElement(width, height, true);
            CustomPanelAPI inner = panel.createCustomPanel(width, totalHeight + noteCount * NOTE_HEIGHT, null);
            TooltipMakerAPI e = inner.createUIElement(width, totalHeight + noteCount * NOTE_HEIGHT, false);

            outer.addCustom(inner, 0);
            e.addSectionHeading(" Battle Report", Alignment.LMID, 10);

            e.addPara(title, 10, Misc.getTextColor(), getFactionForUIColors().getColor(),
                    enemyFaction == null ? "" : enemyFaction.getDisplayNameLongWithArticle());

            e.addPara("Difficulty rating: %s", 10, Misc.getTextColor(), difficultyColor, (int) (battleDifficulty * 100) + "%");

            e.beginTable(Global.getSector().getPlayerFaction(), 20, "Name", 0.16f * w, "Class", 0.14f * w,
                    "Status", 0.10f * w, dmgMaybe + "Sustained", 0.14f * w, dmgMaybe + "Inflicted", 0.14f * w,
                    "Loyalty", 0.14f * w, "Reputation", 0.18f * w);

            for (RepChange rc : changes) {
                String trait, status = rc.disabled ? "disabled" : (rc.deployed ? "deployed" : "reserved"),
                        loyalty = rc.captain != null && !rc.captain.isDefault()
                                ? rc.getLoyaltyLevel().getName()
                                : "-";
                Color statusColor = rc.disabled ? Misc.getNegativeHighlightColor() : (rc.deployed ? Misc.getTextColor() : Misc.getGrayColor()),
                        takenColor = rc.damageTakenFraction == 0 ? Misc.getGrayColor() : (rc.damageTakenFraction > 0.25 ? Misc.getNegativeHighlightColor() : Misc.getTextColor()),
                        dealtColor = rc.damageDealtPercent == 0 ? Misc.getGrayColor() : (rc.damageDealtPercent > 1 ? Misc.getPositiveHighlightColor() : Misc.getTextColor()),
                        loyaltyColor, traitColor;

                if (rc.trait == null) {
                    traitColor = Misc.getGrayColor();
                    trait = "- ";
                } else if (rc.shuffleSign == 0) {
                    traitColor = rc.trait.effectSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
                    trait = "+ " + rc.trait.getName(rc.ship.getMinCrew() > 0);
                } else {
                    traitColor = rc.shuffleSign > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();

                    if (RepRecord.existsFor(rc.ship) && !RepRecord.get(rc.ship).hasTrait(rc.trait)) {
                        trait = "- " + rc.trait.getName(rc.ship.getMinCrew() > 0);
                    } else {
                        trait = rc.shuffleSign > 0 ? "[ + ]" : "[ - ]";
                    }
                }

                switch (rc.captainOpinionChange) {
                    case 0:
                        loyaltyColor = Misc.getGrayColor();
                        break;
                    case 1:
                        loyaltyColor = Misc.getPositiveHighlightColor();
                        break;
                    case -1:
                        loyaltyColor = Misc.getNegativeHighlightColor();
                        break;
                    default:
                        loyaltyColor = Color.RED;
                }

                e.addRow(
                        Alignment.MID, Misc.getTextColor(), rc.ship.getShipName(),
                        Alignment.MID, Misc.getTextColor(), rc.ship.getHullSpec().getHullName(),
                        Alignment.MID, statusColor, status,
                        Alignment.MID, takenColor, (int) (rc.damageTakenFraction * 100) + "%",
                        Alignment.MID, dealtColor, (int) (rc.damageDealtPercent * 100) + "%",
                        Alignment.MID, loyaltyColor, loyalty,
                        Alignment.MID, traitColor, trait
                );

            }

            e.addTable("", 0, 10);
            e.addPara("", 0);
            e.addSectionHeading(" Notes", Alignment.LMID, 10);

            inner.addUIElement(e);

            List<FleetMemberAPI> sl = new ArrayList<>();

            for (RepChange rc : changes) {
                if (!rc.hasAnyChanges()) continue;

//            e.addSectionHeading(" " + rc.ship.getShipName() + " - " + rc.ship.getHullSpec().getHullName() + " class "
//                    + rc.ship.getHullSpec().getDesignation(), Alignment.LMID, 5);

                int notes = 0;
                if (rc.trait != null) ++notes;
                if (rc.captainOpinionChange != 0) ++notes;

                TooltipMakerAPI e1 = inner.createUIElement(NOTE_HEIGHT, NOTE_HEIGHT, false);
                sl.clear();
                sl.add(rc.ship);
                e1.addShipList(1, 1, NOTE_HEIGHT, Color.WHITE, sl, 10);
                inner.addUIElement(e1).inTL(0, totalHeight);

                TooltipMakerAPI e2 = inner.createUIElement(width - NOTE_HEIGHT, NOTE_HEIGHT, false);
                rc.addCommentsToTooltip(e2);
                inner.addUIElement(e2).inTL(NOTE_HEIGHT, totalHeight + NOTE_HEIGHT / 2f - notes * 5f);

                totalHeight += NOTE_HEIGHT;
            }

            outer.getPosition().setSize(width, totalHeight);
            panel.addUIElement(outer).inTL(0, 0);
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public String getSortString() {
        return "Battle Report";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return enemyFaction == null ? Global.getSector().getPlayerFaction() : enemyFaction;
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        //Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.REFIT, buttonId);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("starship_legends", "battleReport");
    }

    @Override
    public boolean shouldRemoveIntel() {
        return Global.getSector().getClock().getElapsedDaysSince(timestamp) >= DURATION;
    }

    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_FLEET_LOG);
        if(enemyFaction != null) tags.add(enemyFaction.getId());
        return tags;
    }
}
