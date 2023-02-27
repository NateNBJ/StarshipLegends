package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.thoughtworks.xstream.XStream;

import java.awt.*;
import java.util.List;
import java.util.*;

public class BattleReport extends BaseIntelPlugin {
    public static Comparator<RepChange> CHANGE_RELEVANCE_COMPARATOR = new Comparator<RepChange>() {
        int getRelevance(RepChange rc) {
            if(rc.damageTakenFraction == Float.MAX_VALUE) return (int)rc.ship.getHullSpec().getHitpoints();
            else return (rc.hasAnyChanges() ? 10000000 : 0)
                    + (rc.foughtInBattle ? 1000000 : 0)
                    + (rc.deployed ? (rc.disabled ? 100000 : 500000 ) : 0)
                    + (rc.ship.getHullSpec().isCivilianNonCarrier() ? 0 : 50000)
                    + (rc.ship.getHullSpec().getHullSize().ordinal() * 10000)
                    + (int)(rc.damageDealtPercent * 1000)
                    + (int)rc.ship.getHullSpec().getHitpoints();
        }

        @Override
        public int compare(RepChange rc1, RepChange rc2) {
            return getRelevance(rc2) - getRelevance(rc1);
        }
    };

    static final float DURATION = 30, RIGHT_MARGIN = 40, NOTE_HEIGHT = 64;

    public static void configureXStream(XStream x) {
        x.alias("sun_sl_br", BattleReport.class);
        x.aliasAttribute(BattleReport.class, "enemyFaction", "e");
        //x.aliasAttribute(BattleReport.class, "timestamp", "ts");
        x.aliasAttribute(BattleReport.class, "changes", "c");
        x.aliasAttribute(BattleReport.class, "destroyed", "d");
        x.aliasAttribute(BattleReport.class, "routed", "r");
        x.aliasAttribute(BattleReport.class, "wasVictory", "v");
    }
    FactionAPI enemyFaction;
    long timestamp;
    List<RepChange> changes = new LinkedList<>();
    List<FleetMemberAPI> destroyed, routed;
    Boolean wasVictory;

    FactionAPI getEnemyFaction() { return enemyFaction; }
    float getShipListHeight(float width, List<FleetMemberAPI> ships) {
        return addShipList(null, width, null, ships);
    }
    float addShipList(TooltipMakerAPI e, float width, String title, List<FleetMemberAPI> ships) {
        if(ships == null || ships.isEmpty()) return 0;

        float H = 48;
        int columns = (int)Math.floor(width / H);
        int rows = (int)Math.ceil(ships.size() / (float)columns);

        if(e != null) {
            //e.addSectionHeading(" " + title, Alignment.LMID, 10);
            e.addPara(title, 20);
            e.addShipList(columns, rows, H, Color.WHITE, ships, 4);
        }

        return rows * H + 14 + 18;
    }

    public BattleReport(BattleAPI battle, Set<FleetMemberAPI> destroyed,
                        Set<FleetMemberAPI> routed, Boolean wasVictory) {

        this.timestamp = Global.getSector().getLastPlayerBattleTimestamp();
        this.wasVictory = wasVictory;

        routed.removeAll(destroyed);
        this.destroyed = new ArrayList<>(destroyed);
        this.routed = new ArrayList<>(routed);

        Collections.sort(this.destroyed, Util.SHIP_SIZE_COMPARATOR);
        Collections.sort(this.routed, Util.SHIP_SIZE_COMPARATOR);

        if(battle != null) {
            enemyFaction = battle.getPrimary(battle.getNonPlayerSide()).getFaction();
        }
    }

    public void addChange(RepChange change) {
        changes.add(change);
    }
    public void sortChanges() {
        Collections.sort(changes, CHANGE_RELEVANCE_COMPARATOR);
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 0f;
        float opad = 10f;
        Color tc = getBulletColorForMode(mode);
        float initPad = (mode == ListInfoMode.IN_DESC) ? opad : pad;

        if(enemyFaction != null) {
            String factionName = enemyFaction.isShowInIntelTab()
                    ? Misc.ucFirst(enemyFaction.getDisplayName())
                    : "undesignated OpFor";
            info.addPara("Battle against %s", 0f, Misc.getTextColor(), enemyFaction.getColor(), factionName);
        } else info.addPara("Battle report", 0f, Misc.getTextColor());

        bullet(info);

        float days = Global.getSector().getClock().getElapsedDaysSince(timestamp);
        info.addPara("Occurred " + (days < 1 ? "%s" : "%s days ago") , initPad, tc, Misc.getHighlightColor(),
                days < 1 ? "today" : (int)days + "");

        int changeCount = 0;

        for(RepChange rc : changes) if(rc.captainOpinionChange != 0 || rc.trait1 != null) changeCount++;

        if(changeCount != 0) {
            info.addPara("%s notable reputation changes", pad, tc, Misc.getHighlightColor(), changeCount + "");
        }
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

            String blurb = "This report provides an overview of ship diagnostic information collected during the battle"
                    + " against %s " + Misc.getAgoStringForTimestamp(timestamp).toLowerCase()
                    + ", as well as interview feedback from randomly sampled crew members. "
                    + " Note that the veracity of the information provided here should not be taken as"
                    + " categorically true as it is subject to the perceptions of those surveyed.";
            float w = width - RIGHT_MARGIN;
            float totalHeight = 280 + Math.max(0, changes.size() - 3) * 20
                    + getShipListHeight(w, destroyed) + getShipListHeight(w, routed);
            TooltipMakerAPI outer = panel.createUIElement(width, height, true);
            CustomPanelAPI inner = panel.createCustomPanel(width, totalHeight + noteCount * NOTE_HEIGHT, null);
            TooltipMakerAPI e = inner.createUIElement(width, totalHeight + noteCount * NOTE_HEIGHT, false);
            String resultMaybe = "";//(wasVictory != null) ? (wasVictory ?  "- Victory!" : "- Defeat!") : "";
            String factionName = enemyFaction == null || !enemyFaction.isShowInIntelTab()
                    ? "an undesignated opposing force"
                    : enemyFaction.getDisplayNameLongWithArticle();
            boolean showXp = Global.getSettings().isDevMode()
                    ? ModPlugin.SHOW_SHIP_XP_IN_DEV_MODE
                    : ModPlugin.SHOW_SHIP_XP;

            outer.addCustom(inner, 0);
            e.addSectionHeading(" Battle Report " + resultMaybe, Alignment.LMID, 10);

            e.addPara(blurb, 10, Misc.getTextColor(), getFactionForUIColors().getColor(), factionName);

            addShipList(e, w, "Enemies Destroyed:", destroyed);
            addShipList(e, w, "Enemies Routed:", routed);

            e.addSectionHeading(" Combat Performance Summary", Alignment.LMID, 10);

            if(Global.getSettings().getModManager().isModEnabled("RealisticCombat")) {
                boolean noDamageWasRegistered = true;

                for(RepChange rc : changes) {
                    if(rc.damageDealtPercent > 0) {
                        noDamageWasRegistered = false;
                        break;
                    }
                }

                if(noDamageWasRegistered) {
                    e.addPara("The method Starship Legends uses to retrieve the damage your ships deal may have been " +
                            "interfered with.", Misc.getNegativeHighlightColor(), 10);
                }
            }

            if(showXp) {
                e.beginTable(Global.getSector().getPlayerFaction(), 20, "Ship", 0.25f * w, "Class", 0.2f * w,
                        "Status", 0.11f * w, "Inflicted", 0.11f * w, "XP", 0.11f * w, "Loyalty", 0.11f * w,
                        "Reputation", 0.11f * w);
            } else {
                e.beginTable(Global.getSector().getPlayerFaction(), 20, "Ship", 0.29f * w, "Class", 0.23f * w,
                        "Status", 0.12f * w, "Inflicted", 0.12f * w, "Loyalty", 0.12f * w, "Reputation", 0.12f * w);
            }

            boolean changesWereFound = false;

            for (RepChange rc : changes) {
                String
                        reputation = "-",
                        status = rc.disabled ? "recovered" : (rc.deployed ? "deployed" : "reserved"),
                        loyalty = "-";

                Color
                        statusColor = rc.disabled ? Misc.getHighlightColor() : (rc.foughtInBattle ? Misc.getTextColor() : Misc.getGrayColor()),
                        dealtColor = rc.damageDealtPercent == 0 ? Misc.getGrayColor() : Misc.getTextColor(),
                        loyaltyColor,
                        repColor = Misc.getGrayColor(),
                        mainColor = Misc.getTextColor();

                if (rc.hasAnyChanges()) changesWereFound = true;

                if(rc.captainOpinionChange < 0 && rc.loyaltyLevel == LoyaltyLevel.FIERCELY_LOYAL.ordinal()) {
                    loyaltyColor = Misc.getTextColor();
                } else if(rc.captainOpinionChange > 0) {
                    loyaltyColor = Misc.getPositiveHighlightColor();
                } else if(rc.captainOpinionChange < 0) {
                    loyaltyColor = Misc.getNegativeHighlightColor();
                } else {
                    loyaltyColor = Misc.getGrayColor();
                }

                if(rc.damageTakenFraction == Float.MAX_VALUE) {
                    status = "lost";
                    statusColor = Misc.getNegativeHighlightColor();
                    reputation = loyalty = "-";
                    mainColor = loyaltyColor = repColor = dealtColor = Misc.getGrayColor();
                }

                if(RepRecord.isShipNotable(rc.ship)) {
                    RepRecord rep = RepRecord.get(rc.ship);

                    reputation = rep.getTier().getDisplayName();

                    if (rc.hasNewTraitPair()) {
                        if (rc.trait1.effectSign == 1 && rc.trait2.effectSign == 1)
                            repColor = Misc.getPositiveHighlightColor();
                        else if (rc.trait1.effectSign == -1 && rc.trait2.effectSign == -1)
                            repColor = Misc.getNegativeHighlightColor();
                        else repColor = Misc.getTextColor();
                    }

                    if(rc.captain != null && !rc.captain.isDefault() && !(rc.captain.isAICore() && !Misc.isUnremovable(rc.captain))) {
                        loyalty = Util.isShipCrewed(rc.ship)
                            ? rc.getLoyaltyLevel().getName()
                            : rc.getLoyaltyLevel().getAiIntegrationStatusName();
                    }
                }

                ShipHullSpecAPI dHullParent = rc.ship.getHullSpec().getDParentHull();

                if(showXp) {
                    String xpEarned = Util.getImpreciseNumberString(rc.xpEarned);
                    Color xpColor = Misc.getGrayColor();

                    if(xpEarned.endsWith("M")) xpColor = Misc.getHighlightColor();
                    else if(xpEarned.endsWith("K")) xpColor = Misc.getTextColor();

                    e.addRow(
                            Alignment.MID, mainColor, rc.ship.getShipName(),
                            Alignment.MID, mainColor, dHullParent != null
                                    ? dHullParent.getHullName()
                                    : rc.ship.getHullSpec().getHullName(),
                            Alignment.MID, statusColor, status,
                            Alignment.MID, dealtColor, (int) (rc.damageDealtPercent * 100) + "%",
                            Alignment.MID, xpColor, xpEarned,
                            Alignment.MID, loyaltyColor, loyalty,
                            Alignment.MID, repColor, reputation
                    );
                } else {
                    e.addRow(
                            Alignment.MID, mainColor, rc.ship.getShipName(),
                            Alignment.MID, mainColor, dHullParent != null
                                    ? dHullParent.getHullName()
                                    : rc.ship.getHullSpec().getHullName(),
                            Alignment.MID, statusColor, status,
                            Alignment.MID, dealtColor, (int) (rc.damageDealtPercent * 100) + "%",
                            Alignment.MID, loyaltyColor, loyalty,
                            Alignment.MID, repColor, reputation
                    );

                }
            }

            e.addTable("", 0, 3);

            if(changesWereFound) {
                e.addSectionHeading(" Notes on Crew Disposition and Ship Performance", Alignment.LMID, 20);

                List<FleetMemberAPI> sl = new ArrayList<>();

                for (RepChange rc : changes) {
                    if (!rc.hasAnyChanges() || !RepRecord.isShipNotable(rc.ship)) continue;

                    TooltipMakerAPI e1 = inner.createUIElement(NOTE_HEIGHT, NOTE_HEIGHT, false);
                    sl.clear();
                    sl.add(rc.ship);
                    e1.addShipList(1, 1, NOTE_HEIGHT, Color.WHITE, sl, 15);
                    inner.addUIElement(e1).inTL(0, totalHeight  - 45);

                    TooltipMakerAPI e2 = inner.createUIElement(width - NOTE_HEIGHT, NOTE_HEIGHT, false);
                    int lines = rc.addCommentsToTooltip(e2);
                    inner.addUIElement(e2).inTL(NOTE_HEIGHT, totalHeight - 45 + 15);// + NOTE_HEIGHT / 2f - lines * 5f);

                    totalHeight += NOTE_HEIGHT;
                }
            }

            inner.addUIElement(e);

            e.addPara("", 0);

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
        return "sun_sl_battle_report_" + timestamp;
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
        return Global.getSector().getClock().getElapsedDaysSince(timestamp) >= DURATION && !isImportant();
    }

    @Override
    public void reportRemovedIntel() {
        super.reportRemovedIntel();

        setImportant(false);
        setNew(false);
    }

    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Reports");

        if(enemyFaction != null && enemyFaction.isShowInIntelTab()) tags.add(enemyFaction.getId());
        return tags;
    }
}