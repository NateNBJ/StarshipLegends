package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.thoughtworks.xstream.XStream;
import org.lazywizard.console.Console;
import starship_legends.hullmods.Reputation;

import java.util.*;

import static starship_legends.ModPlugin.LOYALTY_LIMIT;

public class RepRecord {
    public static class Origin {
        public enum Type {
            Unknown("mysterious origins"),
            StartedWith("origin as part of your initial fleet"),
            BlackMarket("purchase on the black market of %s"),
            OpenMarket("purchase at %s"),
            MilitaryMarket("acquisition from military surplus on %s"),
            OtherMarket("purchase at %s"),
            BattleSalvage("restoration after a battle in %s"),
            FamousDerelict("discovery in %s and subsequent recovery"),
            AncientDerelict("miraculous discovery in %s after centuries adrift"),
            FamousFlagship("appropriation from a %s commander"),
            Manufactured("construction on %s"),
            Recruitment("recruitment at %s"),
            Derelict("recovery in %s");

            String text;

            Type(String text) {
                this.text = text;
            }
        }

        public static void configureXStream(XStream x) {
            x.alias("sun_sl_origin", Origin.class);
            x.aliasAttribute(Origin.class, "type", "t");
            x.aliasAttribute(Origin.class, "detail", "d");
        }
        Type type = Type.Unknown;
        String detail = "";

        public Origin(Type type, String locationName) {
            this.type = type;
            this.detail = locationName;
        }
        public String getOriginStoryString() {
            return String.format(type.text, detail);
        }
    }
    public static class Story {
        public static void configureXStream(XStream x) {
            x.alias("sun_sl_story", Story.class);
            x.aliasAttribute(Story.class, "timesDisabled", "td");
            x.aliasAttribute(Story.class, "lowestLoyalty", "ll");
            x.aliasAttribute(Story.class, "highestLoyalty", "hl");
            x.aliasAttribute(Story.class, "highestDamageDealt", "hdd");
            x.aliasAttribute(Story.class, "enemyAgainstHighestDamageDealt", "ehdd");
            x.aliasAttribute(Story.class, "enemyAgainstWhichLastDisabled", "eld");
            x.aliasAttribute(Story.class, "mostHatedCaptain", "mhc");
            x.aliasAttribute(Story.class, "favoriteCaptain", "fc");
        }
        private int timesDisabled = 0, lowestLoyalty = 0, highestLoyalty = 0;
        private float
                highestDamageDealt = 0f;
        private String
                enemyAgainstHighestDamageDealt = null,
                enemyAgainstWhichLastDisabled = null,
                mostHatedCaptain = null,
                favoriteCaptain = null;

        private String getContributionToMostSignificantBattle() {
            float dmg = highestDamageDealt;

            if(dmg > 32) return "displayed a godlike capacity for destruction";
            else if(dmg > 16) return "laid waste to the enemy fleet, creating a dense field of death and debris";
            else if(dmg > 8) return "carved a swath of destruction through the enemy";
            else if(dmg > 4) return "crushed a large portion of the enemy fleet";
            else if(dmg > 2) return "destroyed several ships of a similar weight class";
            else if(dmg > 1) return "proved its value as a machine of destruction";
            else if(dmg > 0.25) return "provided decisive fire support";
            else return "provided vital logistical support";
        }

        public void update(RepChange rc, FactionAPI enemy) {
            if(enemy != null) {
                if (rc.deployed && rc.damageDealtPercent > highestDamageDealt) {
                    highestDamageDealt = rc.damageDealtPercent;
                    enemyAgainstHighestDamageDealt = enemy.getId();
                }

                if (rc.disabled) {
                    timesDisabled++;
                    enemyAgainstWhichLastDisabled = enemy.getId();
                }
            }

            if(rc.loyaltyLevel != 0) {
                if (rc.loyaltyLevel > Float.MIN_VALUE + 100 && rc.loyaltyLevel <= lowestLoyalty) {
                    lowestLoyalty = rc.loyaltyLevel;
                    mostHatedCaptain = Util.getCaptainId(rc.captain);
                }

                if (rc.loyaltyLevel >= highestLoyalty) {
                    highestLoyalty = rc.loyaltyLevel;
                    favoriteCaptain = Util.getCaptainId(rc.captain);
                }
            }
        }

        public void tell(FleetMemberAPI ship, TextPanelAPI textPanel, RelationshipAPI rel) {
            Origin origin = RepRecord.SHIP_ORIGINS.val.containsKey(ship.getId())
                    ? RepRecord.SHIP_ORIGINS.val.get(ship.getId())
                    : new Origin(Origin.Type.Unknown, null);
            boolean wasLost = Util.findPlayerShip(ship.getId()) == null;
            FactionAPI disabledAgainst = enemyAgainstWhichLastDisabled == null ? null
                    : Global.getSector().getFaction(enemyAgainstWhichLastDisabled);
            FactionAPI foughtAgainst = enemyAgainstHighestDamageDealt == null ? null
                    : Global.getSector().getFaction(enemyAgainstHighestDamageDealt);
            String storyType = "",
                    lastNotableEvents = "",
                    loyaltyStatement = "",
                    closingStatement = "",
                    crewOrAI = Util.isShipCrewed(ship) ? "crew" : "AI persona";

            if(rel.isAtWorst(RepLevel.WELCOMING)) {
                storyType = "an idealistic allegory";
                closingStatement = "ends with a series of platitudes about the merits of duty and honor";
            } else if(rel.isAtBest(RepLevel.SUSPICIOUS)) {
                storyType = "an unflattering satire";
                closingStatement = "eventually devolves into a tirade against you";
            } else {
                storyType = "a droll account";
                closingStatement = "eventually transitions into a discussion about local matters";
            }

            if(wasLost) closingStatement = "concludes with a warning against hubris";

            if(foughtAgainst != null) {
                if(wasLost) {
                    lastNotableEvents += "its eventual destruction. Much of the story involves a battle against "
                            + foughtAgainst.getDisplayNameWithArticle() + " in which it "
                            + getContributionToMostSignificantBattle();
                } else if(enemyAgainstWhichLastDisabled != null) {
                    if (enemyAgainstWhichLastDisabled.equals(enemyAgainstHighestDamageDealt)) {
                        lastNotableEvents += "its recovery after a brutal battle against "
                                + foughtAgainst.getDisplayNameWithArticle() + ", in which it "
                                + getContributionToMostSignificantBattle();
                    } else {
                        lastNotableEvents += "being disabled during a hard-fought battle against "
                                + disabledAgainst.getDisplayNameWithArticle()
                                + ". Much of the story involves a different battle, in which the " + ship.getShipName()
                                + " " + getContributionToMostSignificantBattle() + " while facing off against "
                                + (foughtAgainst.getId().equals("pirates") ? foughtAgainst.getDisplayName() : foughtAgainst.getDisplayNameWithArticle());
                    }
                } else {
                    lastNotableEvents += "its participation in a glorious battle against "
                            + foughtAgainst.getDisplayNameWithArticle()
                            + ", in which it " + getContributionToMostSignificantBattle();
                }
            } else {
                if(wasLost) {
                    lastNotableEvents += "its eventual destruction";
                } else {
                    lastNotableEvents += "its participation in an expedition to far-flung worlds";
                }
            }

            if(timesDisabled > 3) {
                lastNotableEvents += ". The storyteller makes a point of emphasizing the resilience of the "
                        + ship.getShipName() + ", explaining how it carried on " + (wasLost ? "for so long" : "admirably")
                        + " in spite of the battle scars it earned from being disabled and recovered "
                        + (timesDisabled > 6 ? "countless" : "several") + " times";
                closingStatement = "ends with adulation for the value of persistence";
            }

            if(!Util.isShipCrewed(ship)) {
                loyaltyStatement += "The fact that the ship is entirely autonomous is brought up frequently, which " +
                        "seems to make many of the listeners rather uneasy. ";
            } else if(mostHatedCaptain != null || favoriteCaptain != null) {
                PersonAPI favCap = null, hatedCap = null;

                for(OfficerDataAPI data : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                    if(Util.getCaptainId(data.getPerson()).equals(favoriteCaptain)) favCap = data.getPerson();
                    if(Util.getCaptainId(data.getPerson()).equals(mostHatedCaptain)) hatedCap = data.getPerson();
                }

                LoyaltyLevel llGood = LoyaltyLevel.fromInt(highestLoyalty - LOYALTY_LIMIT);
                LoyaltyLevel llBad = LoyaltyLevel.fromInt(lowestLoyalty - LOYALTY_LIMIT);

                loyaltyStatement += "A reoccurring topic throughout the story is the ";

                if(favCap != null && hatedCap != null && favCap == hatedCap) {
                    loyaltyStatement += "evolving relationship between the " + crewOrAI + " and their captain, "
                            + favCap.getNameString() + ", which ranges from " + llBad.getName().toLowerCase() + " to "
                            + llGood.getName().toLowerCase() + ". ";
                } else if(hatedCap != null && Math.abs(lowestLoyalty) >= highestLoyalty) {
                    loyaltyStatement += "constant struggle between the " + crewOrAI + " and their captain, "
                            + hatedCap.getNameString();

                    if(llBad == LoyaltyLevel.MUTINOUS) {
                        loyaltyStatement += ", which eventually results in mutiny";
                    }

                    loyaltyStatement += ". ";
                } else if(favCap != null && highestLoyalty >= 2) {
                    loyaltyStatement += "steadfast support of the " + crewOrAI + " for their captain, "
                            + favCap.getNameString() + ". ";
                } else {
                    loyaltyStatement = "";
                }
            }

            String text = "Before long, you realize the story is " + storyType + " about a starship of your own, the "
                    + ship.getShipName() + ". Favoring drama over veracity, the storyteller covers the entire breadth "
                    + "of the ship's service under your command, from its " + origin.getOriginStoryString() + ", to "
                    + lastNotableEvents + ". " + loyaltyStatement + "The story " + closingStatement + ". With a hint of mirth, you "
                    + "reflect that none of the other listeners realize you're one of the central characters.";

            textPanel.addPara(text);
        }
    }

    static final Saved<Map<String, RepRecord>> INSTANCE_REGISTRY
            = new Saved<Map<String, RepRecord>>("reputationRecords", new HashMap<String, RepRecord>());
    static final Saved<Map<String, Map<String, Long>>> PENDING_INSPIRATION_EXPIRATIONS
            = new Saved<Map<String, Map<String, Long>>>("pendingInspirationExpirations", new HashMap<String, Map<String, Long>>());
    static final Saved<Map<String, Long>> PENDING_CHRONICLER_EXPIRATIONS
            = new Saved<Map<String, Long>>("pendingChroniclerExpirations", new HashMap<String, Long>());
    static final Saved<Map<String, Origin>> SHIP_ORIGINS = new Saved<Map<String, Origin>>("shipOrigins", new HashMap<String, Origin>());
    static final Saved<List<String>> QUEUED_STORIES = new Saved<List<String>>("queuedStories", new LinkedList<String>());

    public static Map<String, RepRecord> getInstanceRegistryCopy() { return new HashMap<>(INSTANCE_REGISTRY.val); }
    public static void printRegistry() {
        String msg = "";

        for(Map.Entry<String, RepRecord> entry : INSTANCE_REGISTRY.val.entrySet()) {
            msg += System.lineSeparator() + entry.getKey() + " : " + entry.getValue().toString();
        }

        if(Global.getSettings().getModManager().isModEnabled("lw_console")) Console.showMessage(msg);
        Global.getLogger(RepRecord.class).info(msg);
    }
    public static boolean existsFor(String shipID) { return INSTANCE_REGISTRY.val.containsKey(shipID); }
    public static boolean existsFor(ShipAPI ship) { return existsFor(ship.getFleetMemberId()); }
    public static boolean existsFor(FleetMemberAPI ship) { return existsFor(ship.getId()); }
    public static boolean isShipNotable(FleetMemberAPI ship) {
        return existsFor(ship.getId()) && !RepRecord.get(ship).traits.isEmpty();
    }
    public static RepRecord get(String shipID) { return INSTANCE_REGISTRY.val.get(shipID); }
    public static RepRecord get(ShipAPI ship) { return get(ship.getFleetMemberId()); }
    public static RepRecord get(FleetMemberAPI ship) { return get(ship.getId()); }
    public static RepRecord getOrCreate(FleetMemberAPI ship) {
        return RepRecord.existsFor(ship) ? get(ship.getId()) : new RepRecord(ship);
    }
    public static void deleteFor(FleetMemberAPI ship) {
        INSTANCE_REGISTRY.val.remove(ship.getId());
        Reputation.removeShipOfNote(ship);
        Util.removeRepHullmodFromVariant(ship.getVariant());
    }
    public static void transfer(FleetMemberAPI from, FleetMemberAPI to) {
        if(from == null || to == null || from == to || !existsFor(from)) return;

        deleteFor(to);

        INSTANCE_REGISTRY.val.put(to.getId(), get(from));

        deleteFor(from);
        updateRepHullMod(to);
    }
    public static Trait.Tier getTierFromTraitCount(int count) {
        if(count > 3 * ModPlugin.TRAITS_PER_TIER) return Trait.Tier.Legendary;
        if(count > 2 * ModPlugin.TRAITS_PER_TIER) return Trait.Tier.Famous;
        if(count > 1 * ModPlugin.TRAITS_PER_TIER) return Trait.Tier.Wellknown;
        if(count > 0 * ModPlugin.TRAITS_PER_TIER) return Trait.Tier.Notable;
        else return Trait.Tier.UNKNOWN;
    }
    public static void updateRepHullMod(FleetMemberAPI ship) {
        if(!RepRecord.existsFor(ship)) return;

        Trait.Tier tier = RepRecord.get(ship).getTier();
        ShipVariantAPI v;

        if(tier == Trait.Tier.UNKNOWN) {
            Reputation.removeShipOfNote(ship);
            Util.removeRepHullmodFromVariant(ship.getVariant());
            ship.updateStats();
            return;
        }

        if(ship.getVariant().isStockVariant()) {
            v = ship.getVariant().clone();
            v.setSource(VariantSource.REFIT);
            ship.setVariant(v, false, false);
        } else v = ship.getVariant();

        Util.removeRepHullmodFromVariant(v);
        v.addPermaMod(tier.getHullModID());

        List<String> slots = v.getModuleSlots();

        for(int i = 0; i < slots.size(); ++i) {
            ShipVariantAPI module = v.getModuleVariant(slots.get(i));

            if(module == null) continue;

            if(module.isStockVariant()) {
                module = module.clone();
                module.setSource(VariantSource.REFIT);
                v.setModuleVariant(slots.get(i), module);
            }

            module.addPermaMod(tier.getHullModID());
        }

        Reputation.addShipOfNote(ship);

        ship.updateStats();
    }
    public static int getXpRequiredToLevelUp(FleetMemberAPI ship) {
        int currentTraits = RepRecord.isShipNotable(ship) ? RepRecord.get(ship).getTraits().size() : 0;
        Trait.Tier nextTier = getTierFromTraitCount(currentTraits + 2);
        Random rand = new Random((ship.getId() + nextTier.name()).hashCode());
        float variationMult = 0.5f + rand.nextFloat();

        return nextTier.getXpRequired() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int)(nextTier.getXpRequired() * variationMult);
    }
    public static boolean shouldSkipCombatLogisticsMismatch(TraitType type, Random rand, boolean isCivShip) {
        if(type.getTags().contains(TraitType.Tags.LOGISTICAL)
                && !isCivShip
                && rand.nextFloat() <= ModPlugin.CHANCE_TO_IGNORE_LOGISTICS_TRAITS_ON_COMBAT_SHIPS) {

            return true;
        } else if(!type.getTags().contains(TraitType.Tags.LOGISTICAL)
                && isCivShip
                && rand.nextFloat() <= ModPlugin.CHANCE_TO_IGNORE_COMBAT_TRAITS_ON_CIVILIAN_SHIPS) {

            return true;
        }

        return false;
    }
    public static List<Trait> getDestinedTraitsForShip(FleetMemberAPI ship, boolean allowCrewTraits) {
        List<Trait> retVal = new ArrayList<>();
        ShipHullSpecAPI hull = ship.getHullSpec();
        Random rand = Util.getUniqueShipRng(ship);
        String themeKey = null;
        RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : null;
        WeightedRandomPicker<TraitType> goodTraits = null;
        WeightedRandomPicker<TraitType> badTraits = null;
        WeightedRandomPicker<TraitType> discardedTraits = new WeightedRandomPicker<>(rand);
        int negTraitRange = ModPlugin.MAX_INITIAL_NEGATIVE_TRAITS - ModPlugin.MIN_INITIAL_NEGATIVE_TRAITS;
        int negTraitsRemaining = ModPlugin.MIN_INITIAL_NEGATIVE_TRAITS
                + (negTraitRange <= 0 ? 0 : rand.nextInt(negTraitRange));
        boolean[] traitIsBad = new boolean[Trait.getTraitLimit()];

        for(int i = 0; i < Trait.getTraitLimit(); i += 2) {
            int traitsRemaining = Trait.getTraitLimit() - i;

            if(negTraitsRemaining <= 0) {
                traitIsBad[i] = traitIsBad[i+1] = false;
            } else if(negTraitsRemaining - traitsRemaining / 2 > 0) {
                traitIsBad[i] = traitIsBad[i+1] = true;
                negTraitsRemaining -= 2;
            } else {
                traitIsBad[i] = false;
                traitIsBad[i+1] = true;
                negTraitsRemaining -= 1;
            }
        }

        if(rep != null) {
            if(rep.themeKey == null) {
                Random rand2 = Util.getUniqueShipRng(ship);
                rep.themeKey = RepTheme.pickRandomTheme(rand2).getKey();
            }

            if(rep.themeKeyIsFactionId && FactionConfig.get(rep.themeKey) != null) {
                FactionConfig cfg = FactionConfig.get(rep.themeKey);

                goodTraits = cfg.createGoodTraitPicker(rand);
                badTraits = cfg.createBadTraitPicker(rand);
            } else {
                themeKey = rep.themeKey;
            }
        }

        if(goodTraits == null || badTraits == null) {
            Random rand2 = Util.getUniqueShipRng(ship);
            RepTheme theme = themeKey == null ? RepTheme.pickRandomTheme(rand2) : RepTheme.get(themeKey);
            goodTraits = theme.createGoodTraitPicker(rand);
            badTraits = theme.createBadTraitPicker(rand);
        }

        while(retVal.size() < Trait.getTraitLimit()) {
            WeightedRandomPicker<TraitType> picker = traitIsBad[retVal.size()] ? badTraits : goodTraits;
            WeightedRandomPicker<TraitType> opposite = traitIsBad[retVal.size()] ? goodTraits : badTraits;

            if(picker.isEmpty()) break;

            TraitType type = picker.pickAndRemove();
            Trait trait = type.getTrait(traitIsBad[retVal.size()]);

            if(trait == null) continue;

            boolean traitIsRelevant = trait.isRelevantFor(ship, allowCrewTraits);
            boolean skipMismatch = shouldSkipCombatLogisticsMismatch(type, rand, hull.isCivilianNonCarrier());
            boolean shipAlreadyHasTraitOfType = rep.hasTraitType(type);

            opposite.remove(type);


            if(traitIsRelevant && !shipAlreadyHasTraitOfType) {
                if(skipMismatch) discardedTraits.add(type);
                else  retVal.add(trait);
            }
        }

        // In cases where traitPairsPerTier is increased, it is possible to run out of traits due to
        // combatLogisticsMismatch, so the loop below is necessary to re-add the discarded traits in such cases
        while(retVal.size() < Trait.getTraitLimit() && !discardedTraits.isEmpty()) {
            TraitType type = discardedTraits.pickAndRemove();
            Trait trait = type.getTrait(traitIsBad[retVal.size()]);
            retVal.add(trait);
        }

        return retVal;
    }
    public static void addDestinedTraitsToShip(FleetMemberAPI ship, boolean allowCrewTraits, int traitCount) {
        List<Trait> destinedTraits = RepRecord.getDestinedTraitsForShip(ship, allowCrewTraits);
        RepRecord rep = RepRecord.getOrCreate(ship);

        for (int i = 0; i < Trait.getTraitLimit() && i < destinedTraits.size(); i++) {
            if (traitCount > 0) {
                rep.getTraits().add(destinedTraits.get(i));
                traitCount--;
            } else break;
        }
    }
    public static Trait chooseRandomNewTrait(FleetMemberAPI ship, Random rand, boolean traitIsBad) {
        if(ship == null || rand == null) return null;

        RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : null;
        RepTheme theme = RepTheme.get("default");
        WeightedRandomPicker<TraitType> picker = traitIsBad
                ? theme.createBadTraitPicker(rand)
                : theme.createGoodTraitPicker(rand);

        if(rep != null) {
//            if (rep.hasMaximumTraits()) return null;

            for (Trait trait : rep.getTraits()) picker.remove(trait.getType());
        }

        while(!picker.isEmpty()) {
            TraitType type = picker.pickAndRemove();
            Trait trait = type.getTrait(traitIsBad);

            if(trait == null) continue;

            boolean traitIsRelevant = trait.isRelevantFor(ship);
            boolean skipMismatch = shouldSkipCombatLogisticsMismatch(type, rand, ship.getHullSpec().isCivilianNonCarrier());
            boolean shipAlreadyHasTraitOfType = rep.hasTraitType(type);

            if(trait != null && traitIsRelevant && !skipMismatch && !shipAlreadyHasTraitOfType)
                return trait;
        }

        return null;
    }
    public static int getDaysOfInspirationRemaining(FleetMemberAPI ship, PersonAPI captain) {
        int retVal = Integer.MIN_VALUE;

        if(PENDING_INSPIRATION_EXPIRATIONS.val.containsKey(ship.getId())) {
            Map<String, Long> inspirations = PENDING_INSPIRATION_EXPIRATIONS.val.get(ship.getId());

            if(inspirations.containsKey(Util.getCaptainId(captain))) {
                long now = Global.getSector().getClock().getTimestamp();
                long expiration = inspirations.get(Util.getCaptainId(captain));
                retVal = (int)((expiration - now) / ModPlugin.TIMESTAMP_TICKS_PER_DAY);
            }
        }

        return retVal;
    }
    public static void clearInspirationExpiration(FleetMemberAPI ship, PersonAPI captain) {
        if(PENDING_INSPIRATION_EXPIRATIONS.val.containsKey(ship.getId())) {
            Map<String, Long> inspirations = PENDING_INSPIRATION_EXPIRATIONS.val.get(ship.getId());
            String id = Util.getCaptainId(captain);

            if(inspirations.containsKey(id)) inspirations.remove(id);

            if(inspirations.isEmpty()) PENDING_INSPIRATION_EXPIRATIONS.val.remove(ship.getId());
        }
    }
    public static boolean shipHasChronicler(FleetMemberAPI ship) {
        return PENDING_CHRONICLER_EXPIRATIONS.val.containsKey(ship.getId());
    }
    public static void addChroniclerDays(FleetMemberAPI ship, int days) {
        long now = Global.getSector().getClock().getTimestamp();

        if(shipHasChronicler(ship)) days += Math.max(0, getDaysOfChroniclerRemaining(ship));

        PENDING_CHRONICLER_EXPIRATIONS.val.put(ship.getId(), (long)(now + days * ModPlugin.TIMESTAMP_TICKS_PER_DAY));
    }
    public static int getDaysOfChroniclerRemaining(FleetMemberAPI ship) {
        int retVal = Integer.MIN_VALUE;

        if(PENDING_CHRONICLER_EXPIRATIONS.val.containsKey(ship.getId())) {
            long now = Global.getSector().getClock().getTimestamp();
            long expiration = PENDING_CHRONICLER_EXPIRATIONS.val.get(ship.getId());
            retVal = (int)((expiration - now) / ModPlugin.TIMESTAMP_TICKS_PER_DAY);

        }

        return retVal;
    }
    public static void clearChroniclerExpiration(FleetMemberAPI ship) {
        if(PENDING_CHRONICLER_EXPIRATIONS.val.containsKey(ship.getId())) {
            PENDING_CHRONICLER_EXPIRATIONS.val.remove(ship.getId());
        }
    }
    public static void setShipOrigin(FleetMemberAPI ship, Origin.Type type) {
        setShipOrigin(ship, type, null);
    }
    public static void setShipOrigin(FleetMemberAPI ship, Origin.Type type, String detail) {
        if(!SHIP_ORIGINS.val.containsKey(ship.getId())) {
            if(detail == null) {
                LocationAPI location = Global.getSector().getCurrentLocation();
                detail = location.isHyperspace() ? "hyperspace" : "the " + location.getName();
            }

            SHIP_ORIGINS.val.put(ship.getId(), new Origin(type, detail));
        }
    }
    public static boolean shipTierIsAtLeast(FleetMemberAPI ship, Trait.Tier minTier) {
        return RepRecord.existsFor(ship) && RepRecord.get(ship).getTier().ordinal() >= minTier.ordinal();
    }
    public static List<String> getQueuedStories() { return QUEUED_STORIES.val; }
    public static void configureXStream(XStream x) {
        x.alias("sun_sl_rr", RepRecord.class);
        x.aliasAttribute(RepRecord.class, "story", "s");
        x.aliasAttribute(RepRecord.class, "themeKeyIsFactionId", "tkif");
        x.aliasAttribute(RepRecord.class, "themeKey", "tk");
        x.aliasAttribute(RepRecord.class, "traits", "t");
        x.aliasAttribute(RepRecord.class, "captainLoyaltyLevels", "cll");
        x.aliasAttribute(RepRecord.class, "captainLoyaltyXp", "clxp");
        x.aliasAttribute(RepRecord.class, "xp", "xp");
    }

    private Story story = new Story();
    private boolean themeKeyIsFactionId = false;
    private String themeKey = null;
    private List<Trait> traits = new ArrayList<>();
    private Map<String, Integer> captainLoyaltyLevels = new HashMap<>();
    private Map<String, Integer> captainLoyaltyXp = new HashMap<>();
    private int xp = 0;

    public RepRecord() { }
    public RepRecord(FleetMemberAPI ship) { this(ship, null, false); }
    public RepRecord(FleetMemberAPI ship, String themeKey, boolean themeKeyIsFactionId) {
        if(ship != null) INSTANCE_REGISTRY.val.put(ship.getId(), this);

        if(themeKey == null) {
            Random rand = Util.getUniqueShipRng(ship);
            this.themeKey = RepTheme.pickRandomTheme(rand).getKey();
        } else {
            this.themeKey = themeKey;
        }

        this.themeKeyIsFactionId = themeKeyIsFactionId;
    }

    public void progress(float xpEarned, RepChange rc, CampaignScript.BattleRecord br) {
        int xpRequiredToLevelUp = RepRecord.getXpRequiredToLevelUp(rc.ship);
        float fameXpEarned = xpEarned * (1 + ModPlugin.FAME_BONUS_PER_PLAYER_LEVEL * Global.getSector().getPlayerStats().getLevel());
        boolean officerIsApplicable = ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && rc.captain != null
                && !rc.captain.isDefault() && !getTraits().isEmpty() && (!rc.captain.isAICore() || Misc.isUnremovable(rc.captain));

        if(officerIsApplicable) fameXpEarned *= 1 + getLoyalty(rc.captain).getFameGainBonus() / 100f;

        if(PENDING_CHRONICLER_EXPIRATIONS.val.containsKey(rc.ship.getId())) {
            fameXpEarned *= 1 + (rc.ship.getHullSpec().isCivilianNonCarrier()
                    ? ModPlugin.FAME_BONUS_FROM_CHRONICLERS_FOR_CIVILIAN_SHIPS
                    : ModPlugin.FAME_BONUS_FROM_CHRONICLERS_FOR_COMBAT_SHIPS);

            if(getDaysOfChroniclerRemaining(rc.ship) < 0) clearChroniclerExpiration(rc.ship);
        }

        if(xp < 0) xp = 0;

        xp += fameXpEarned;
        rc.xpEarned = fameXpEarned;

        if(getXp() >= xpRequiredToLevelUp && getTraits().size() < Trait.getTraitLimit()) {
            xp -= xpRequiredToLevelUp;

            List<Trait> destinedTraits = RepRecord.getDestinedTraitsForShip(rc.ship, true);

            if(destinedTraits.size() > getTraits().size() + 1) {
                rc.trait1 = destinedTraits.get(getTraits().size());
                rc.trait2 = destinedTraits.get(getTraits().size() + 1);
            }
        }

        if (officerIsApplicable && rc.deployed) {
            LoyaltyLevel ll = getLoyalty(rc.captain);
            float loyaltyXpEarned = xpEarned * getLoyaltyRateMult(rc.captain);
            int loyaltyXp = 0;

            if(ll != LoyaltyLevel.INSPIRED) {
                adjustLoyaltyXp((int) (loyaltyXpEarned * ModPlugin.LOYALTY_IMPROVEMENT_RATE_MULT), rc.captain);
                loyaltyXp = getLoyaltyXp(rc.captain);
            }

            if(rc.disabled) {
                float loyaltyLoss = ModPlugin.BASE_LOYALTY_LEVELS_LOST_WHEN_DISABLED
                        * (br == null ? 1 : br.loyaltyLossMult)
                        * getLoyaltyLossChanceMult(rc.captain);

                loyaltyLoss = Math.max(loyaltyLoss, ModPlugin.MIN_LOYALTY_LEVELS_LOST_WHEN_DISABLED);
                loyaltyLoss = Math.min(loyaltyLoss, ModPlugin.MAX_LOYALTY_LEVELS_LOST_WHEN_DISABLED);

                int minLoss = (int)Math.floor(loyaltyLoss);
                float extraLossChance = loyaltyLoss - minLoss;
                int actualLoyaltyLoss = minLoss + (extraLossChance > Math.random() ? 1 : 0);

                if(actualLoyaltyLoss > 0) {
                    rc.setLoyaltyChange(-actualLoyaltyLoss);
                    clearInspirationExpiration(rc.ship, rc.captain);
                }

                adjustLoyaltyXp(-loyaltyXp, rc.captain);
            } else if(ll == LoyaltyLevel.INSPIRED) {
                if(getDaysOfInspirationRemaining(rc.ship, rc.captain) <= 0) {
                    rc.setLoyaltyChange(-1);
                    clearInspirationExpiration(rc.ship, rc.captain);
                    adjustLoyaltyXp(-loyaltyXp, rc.captain);
                }
            } else if(ll == LoyaltyLevel.FIERCELY_LOYAL) {
                int MIN_DAYS = 30, MAX_DAYS = 120, XP_PER_DAY_OF_INSPIRATION = 150000;
                int daysOfInspiration = (int)Math.min(MAX_DAYS, loyaltyXp / (float)XP_PER_DAY_OF_INSPIRATION);
                float inspireChance = (daysOfInspiration - MIN_DAYS) / (MAX_DAYS - MIN_DAYS);
                CampaignClockAPI clock = Global.getSector().getClock();
                Random rand = new Random((rc.ship.getId() + clock.getDateString()).hashCode());

                if(rand.nextFloat() < inspireChance) {
                    long expireTS = (long)(clock.getTimestamp() + daysOfInspiration * ModPlugin.TIMESTAMP_TICKS_PER_DAY);

                    Map<String, Long> inspirations;

                    if(PENDING_INSPIRATION_EXPIRATIONS.val.containsKey(rc.ship.getId())) {
                        inspirations = PENDING_INSPIRATION_EXPIRATIONS.val.get(rc.ship.getId());
                    } else {
                        inspirations = new HashMap<>();
                        PENDING_INSPIRATION_EXPIRATIONS.val.put(rc.ship.getId(), inspirations);
                    }

                    inspirations.put(Util.getCaptainId(rc.captain), expireTS);

                    rc.setLoyaltyChange(1);
                    adjustLoyaltyXp(-loyaltyXp, rc.captain);
                }
            } else if(loyaltyXp > ll.getXpToImprove()) {
                rc.setLoyaltyChange(1);
                adjustLoyaltyXp(-ll.getXpToImprove(), rc.captain);
            }
        }
    }
    public Story getStory() { return story; }
    public int getXp() { return xp; }
    public int getTraitPosition(Trait trait) {
        for(int i = 0; i < getTraits().size(); ++i) {
            if(getTraits().get(i) == trait) return i;
        }

        return -1;
    }
    public float getTraitEffect(Trait trait) {
        return getTraitEffect(trait.getType(), 0, ShipAPI.HullSize.DEFAULT);
    }
    public float getTraitEffect(TraitType type) { return getTraitEffect(type, 0, ShipAPI.HullSize.DEFAULT); }
    public float getTraitEffect(String typeID) { return getTraitEffect(TraitType.get(typeID), 0, ShipAPI.HullSize.DEFAULT); }
    public float getTraitEffect(TraitType type, int loyaltyEffectAdjustment, ShipAPI.HullSize size) {
        int traitsLeft = Math.min(getTraits().size(), Trait.getTraitLimit());

        for(Trait trait : getTraits()) {
            if(traitsLeft <= 0) break;

            traitsLeft--;

            if(trait.getType().equals(type)) {
                return trait.getEffect(RepRecord.getTierFromTraitCount(traitsLeft--), loyaltyEffectAdjustment, size);
            }
        }

        return -1;
    }
    public boolean hasMaximumTraits() {
        return getTraits().size() >= Trait.getTraitLimit();
    }
    public boolean hasTrait(Trait trait) {
        for(Trait t : getTraits()) if(t.equals(trait)) return true;

        return false;
    }
    public boolean hasTraitType(String typeID) {
        return hasTraitType(TraitType.get(typeID));
    }
    public boolean hasTraitType(TraitType type) {
        for(Trait t : getTraits()) if(t.getType().equals(type)) return true;

        return false;
    }
    public boolean hasTraitWithTag(String tag) {
        for(Trait t : getTraits()) if(t.getType().getTags().contains(tag)) return true;

        return false;
    }
    public Map<String, Integer> getCaptainLoyaltyLevels() {
        return captainLoyaltyLevels;
    }
    public int getLoyaltyXp(PersonAPI officer) {
        String id = Util.getCaptainId(officer);
        if (officer == null || !captainLoyaltyXp.containsKey(id)) return 0;
        else return captainLoyaltyXp.get(id);
    }
    public void adjustLoyaltyXp(int xp, PersonAPI captain) {
        if(captain != null && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
            String id =Util.getCaptainId(captain);
            int currentVal = captainLoyaltyXp.containsKey(id) ? captainLoyaltyXp.get(id) : 0;
            int newVal = (int)(currentVal + xp);

            captainLoyaltyXp.put(id, newVal);
        }
    }
    public LoyaltyLevel getLoyalty(PersonAPI officer) {
        String id = Util.getCaptainId(officer);
        if(officer == null || officer.isDefault()) return LoyaltyLevel.INDIFFERENT;

        int index = captainLoyaltyLevels.containsKey(id)
                ? captainLoyaltyLevels.get(id)
                : 0;
        return LoyaltyLevel.fromInt(index);
    }
    public void adjustLoyalty(PersonAPI officer, int adjustment) {
        String id = Util.getCaptainId(officer);
        int currentVal = captainLoyaltyLevels.containsKey(id) ? captainLoyaltyLevels.get(id) : 0;
        int newVal = Math.max(Math.min(currentVal + adjustment, LOYALTY_LIMIT), -LOYALTY_LIMIT);

        captainLoyaltyLevels.put(id, newVal);
    }
    public void setLoyalty(PersonAPI officer, int newOpinionLevel) {
        String id = Util.getCaptainId(officer);
        int newVal = Math.max(Math.min(newOpinionLevel, LOYALTY_LIMIT), -LOYALTY_LIMIT);

        captainLoyaltyLevels.put(id, newVal);
    }
    public List<Trait> getTraits() { return traits; }
    public Trait.Tier getTier() { return getTierFromTraitCount(traits.size()); }
    public String getThemeKey() { return themeKey; }
    public void setTheme(RepTheme theme) {
        themeKey = theme.getKey();
        themeKeyIsFactionId = false;
    }
    public void setFactionTheme(FactionAPI faction) {
        themeKey = faction.getId();
        themeKeyIsFactionId = true;
    }
    public float getFractionOfBonusEffectFromTraits() {
        return getFractionOfBonusEffectFromTraits(false);
    }
    public float getFractionOfBonusEffectFromTraits(boolean ignoreNewestTrait) {
        int traitsLeft = Math.min(getTraits().size(), Trait.getTraitLimit());
        float goodness = 0, total = 0;

        if(ignoreNewestTrait) traitsLeft--;

        for(Trait trait : getTraits()) {
            if (traitsLeft <= 0) break;

            float effect = getTierFromTraitCount(traitsLeft--).getEffectMultiplier();

            total += effect;

            if(trait.effectSign > 0) goodness += effect;
        }

        return total <= 0 ? 0 : goodness / total;
    }
    public float getFractionOfBonusEffectFromTraits(int signOfAdditionalTrait) {
        int traitsLeft = Math.min(getTraits().size(), Trait.getTraitLimit()) + 1;
        float goodness = 0, total = 0;

        for(Trait trait : getTraits()) {
            float effect = getTierFromTraitCount(traitsLeft--).getEffectMultiplier();

            total += effect;

            if(trait.effectSign > 0) goodness += effect;
        }

        if(signOfAdditionalTrait != 0) {
            float effect = getTierFromTraitCount(traitsLeft--).getEffectMultiplier();

            total += effect;

            if (signOfAdditionalTrait > 0) goodness += effect;
        }

        return total <= 0 ? 0 : goodness / total;
    }
    public float getLoyaltyRateMult(PersonAPI captain) {
        boolean isNonIntegratedAiCore = captain.isAICore() && !Misc.isUnremovable(captain);
        int traitsLeft = Math.min(getTraits().size(), Trait.getTraitLimit());
        int loyaltyEffectAdjustment = isNonIntegratedAiCore ? 0 : getLoyalty(captain).getTraitAdjustment();

        for(Trait trait : getTraits()) {
            if (traitsLeft <= 0) break;

            if(trait.getType().getId().equals("loyalty")) {
                float percent = trait.getEffect(RepRecord.getTierFromTraitCount(traitsLeft), loyaltyEffectAdjustment, null);
                return 1 + percent * 0.01f;
            }

            traitsLeft--;
        }

        return 1;
    }
    public float getLoyaltyLossChanceMult(PersonAPI captain) {
        boolean isNonIntegratedAiCore = captain.isAICore() && !Misc.isUnremovable(captain);
        int traitsLeft = Math.min(getTraits().size(), Trait.getTraitLimit());
        int loyaltyEffectAdjustment = isNonIntegratedAiCore ? 0 : getLoyalty(captain).getTraitAdjustment();

        for(Trait trait : getTraits()) {
            if (traitsLeft <= 0) break;

            if(trait.getType().getId().equals("loyalty_loss") || trait.getType().getId().equals("loyalty_loss_rugged")) {
                float percent = trait.getEffect(RepRecord.getTierFromTraitCount(traitsLeft), loyaltyEffectAdjustment, null);
                return 1 + percent * 0.01f;
            }

            traitsLeft--;
        }

        return 1;
    }
    public Trait getFlipTrait() {
        Trait retVal = null;
        int negTraits = 0;

        for(Trait trait : traits) {
            if(trait.isMalus()) {
                retVal = trait;
                negTraits++;
            }
        }

        return negTraits > ModPlugin.MIN_NEGATIVE_TRAITS ? retVal : null;
    }
    public boolean applyToShip(FleetMemberAPI ship) {
        if(ship == null || ship.getVariant() == null) return false;

        INSTANCE_REGISTRY.val.put(ship.getId(), this);

        updateRepHullMod(ship);

        return true;
    }

    @Override
    public String toString() {
        return "{ theme:" + themeKey +
                ", xp:" + xp +
                ", loyalties:" + captainLoyaltyLevels.size() +
                ", traits:" + getTraits() +
                " }";
    }
}
