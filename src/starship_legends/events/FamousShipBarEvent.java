package starship_legends.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.PersonBountyIntel;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.MutableValue;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.intel.VayraPersonBountyIntel;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import starship_legends.*;
import starship_legends.hullmods.Reputation;

import java.awt.*;
import java.util.List;
import java.util.*;

import static starship_legends.events.FamousDerelictIntel.LocationGranularity.CONSTELATION;

public class FamousShipBarEvent extends BaseBarEventWithPerson {
	public enum OptionId {
		PLAYER_SHIP_INIT,
		FLAGSHIP_INIT,
		DERELICT_INIT,
		INQUIRE,
		BOGUS_STORY,
		ACCEPT,
		DOUBLE_DOWN,
		CONTINUE,
		LEAVE,
	}

	public static final String KEY_ACCEPTED_AT_THIS_MARKET_RECENTLY = "$sun_sl_fsme_acceptedAtThisMarket";

	public static float ACCEPTED_AT_THIS_MARKET_DURATION = 7f;



	transient static final Saved<HashSet<String>> // These are cleared on odd / even months
			claimedFleetIDsOdd = new Saved<>("claimedFleetIDsOdd", new HashSet<String>()),
			claimedFleetIDsEven = new Saved<>("claimedFleetIDsEven", new HashSet<String>());

	public static void claimFleet(CampaignFleetAPI fleet) {
		if(Global.getSector().getClock().getMonth() % 2 == 0) claimedFleetIDsEven.val.add(fleet.getId());
		else claimedFleetIDsOdd.val.add(fleet.getId());
	}
	public static boolean isFleetClaimed(CampaignFleetAPI fleet) {
		return claimedFleetIDsOdd.val.contains(fleet.getId()) || claimedFleetIDsEven.val.contains(fleet.getId());
	}
	public static void clearPreviousMonthsClaimedFleets() {
		if(Global.getSector().getClock().getMonth() % 2 == 1) claimedFleetIDsEven.val.clear();
		else claimedFleetIDsOdd.val.clear();
	}
	public static float getChanceOfPlayerShipLegend() {
		return RepRecord.getQueuedStories().size() > 0 ? ModPlugin.HEAR_LEGEND_OF_OWN_SHIP_BAR_EVENT_CHANCE : 0;
	}
	public static float getChanceOfAnyStoryEvent() {
		return ModPlugin.FAMOUS_FLAGSHIP_BAR_EVENT_CHANCE
				+ ModPlugin.FAMOUS_DERELICT_BAR_EVENT_CHANCE
				+ FamousShipBarEvent.getChanceOfPlayerShipLegend();
	}
	public static String chooseDerelictVariant(MarketAPI market, Random random) {
		String variantID = null;
		FactionConfig cfg = FactionConfig.get(market.getFaction());

		for(int i = 0; i < 50; ++i) {
			String hullID = cfg.chooseDerelictHullType(random);
			// TODO better way to get variants?
			List<String> variantIDs = Global.getSettings().getHullIdToVariantListMap().get(hullID);

			if(variantIDs.size() <= 0) continue;

			variantID = variantIDs.get(random.nextInt(variantIDs.size()));

			if(variantID != null) return variantID;
		}

		return null;
	}
	public static void pickShipName(FleetMemberAPI ship, Random random) {
		try {
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);

			for(FactionAPI faction : Global.getSector().getAllFactions()) {
				if(faction.getKnownShips().contains(ship.getHullId())) {
					picker.add(faction.pickRandomShipName(random));
				}
			}

			ship.setShipName(picker.pick());
		} catch (Exception e) {
			Global.getLogger(FamousShipBarEvent.class).error("Failed to pick a faction-specific ship name.");
			ship.setShipName(Global.getSector().getFaction(Factions.NEUTRAL).pickRandomShipName(random));
		}
	}

	transient static final Set<String> APPROVED_FLEET_NAMES = new HashSet();
	static {
		APPROVED_FLEET_NAMES.add("fleet");
		APPROVED_FLEET_NAMES.add("group");
		APPROVED_FLEET_NAMES.add("force");
		APPROVED_FLEET_NAMES.add("convoy");
		APPROVED_FLEET_NAMES.add("picket");
		APPROVED_FLEET_NAMES.add("detachment");
		APPROVED_FLEET_NAMES.add("company");
		APPROVED_FLEET_NAMES.add("flotilla");
		APPROVED_FLEET_NAMES.add("armada");
		APPROVED_FLEET_NAMES.add("task-force");
		APPROVED_FLEET_NAMES.add("flight");
		APPROVED_FLEET_NAMES.add("squad");
		APPROVED_FLEET_NAMES.add("division");
	}

	CampaignFleetAPI playerFleet;
	boolean isPlayerShipStory = false, isDerelictMission = false, isShipDerelictLostByPlayer = false;
	String flagshipType = null;

	FleetMemberAPI ship = null;
	RepRecord rep = null;
	MarketAPI source = null;

	FactionAPI faction = null;
	CampaignFleetAPI fleet = null;
	PersonAPI commander = null;
	String activity = "";
	boolean newFleetWasCreated = false;
	boolean isRoaming = false;

	SectorEntityToken derelict = null;
	SectorEntityToken orbitedBody = null;
	ShipRecoverySpecial.PerShipData wreckData = null;
	FamousDerelictIntel.TimeScale timeScale = null;
	FamousDerelictIntel.LocationGranularity granularity = null;
	float cost = 0;
	boolean rivalSalvageFleet = false, rivalOriginIsKnown = false;
	float ambushFleetFP = 0;

	protected void endEvent() {
		market.getMemoryWithoutUpdate().set(KEY_ACCEPTED_AT_THIS_MARKET_RECENTLY, true,
				ACCEPTED_AT_THIS_MARKET_DURATION * (0.75f + (float)Math.random() * 0.5f));

		BarEventManager.getInstance().notifyWasInteractedWith(this);
	}
	protected void reset() {
		ship = null;
		rep = null;
		source = null;

		faction = null;
		fleet = null;
		commander = null;
		activity = "";
		newFleetWasCreated = false;

		derelict = null;
		orbitedBody = null;
		wreckData = null;
		timeScale = null;
		granularity = null;
		cost = 0;
		rivalSalvageFleet = false;
		ambushFleetFP = 0;
	}
	protected boolean isValidDerelictIntel() {
		boolean isValid = rep != null && ship != null && derelict != null && orbitedBody != null && wreckData != null
				&& rep.getTier() != Trait.Tier.UNKNOWN
				&& timeScale != null && granularity != null && derelict.getConstellation() != null
				&& (!granularity.equals(CONSTELATION) || derelict.getConstellation().getSystems().size() > 1)
				&& !ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE);

		if(!isValid) {
			try {
				String nl = System.lineSeparator() + "    ";

				String constellation = "ERROR", isNamedConstellation = "ERROR", isBoardable = "ERROR", noModules = "ERROR";

				try { constellation = "" + (derelict == null ? null : derelict.getConstellation()); } catch (Exception e) {}
				try { isNamedConstellation = "" + (!granularity.equals(CONSTELATION) || derelict.getConstellation().getSystems().size() > 1); } catch (Exception e) {}
				try { isBoardable = "" + !ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE); } catch (Exception e) {}
				try { noModules = "" + ship.getVariant().getModuleSlots().isEmpty(); } catch (Exception e) {}

				Global.getLogger(this.getClass()).error("Invalid derelict mission generated!"
						+ nl + "rep: " + rep
						+ nl + "ship: " + ship
						+ nl + "derelict: " + derelict
						+ nl + "orbitedBody: " + orbitedBody
						+ nl + "wreckData: " + wreckData
						+ nl + "timeScale: " + timeScale
						+ nl + "granularity: " + granularity
						+ nl + "constellation: " + constellation
						+ nl + "named constellation: " + isNamedConstellation
						+ nl + "ship is boardable: " + isBoardable
						+ nl + "ship has no modules: " + noModules
				);
			} catch (Exception e) {
				ModPlugin.reportCrash(e, false);
			}
		}

		return isValid;
	}
	protected boolean isValidFlagshipIntel() {
		if(!fleetHasValidAssignment(fleet) && flagshipType.equals("Remote")) setAssignmentForRemoteFleet();

		boolean isValid = rep != null && ship != null && faction != null && fleet != null && !fleet.isDespawning()
				&& rep.getTier() != Trait.Tier.UNKNOWN
				&& commander != null && fleetHasValidAssignment(fleet)
				&& !ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE);

		if(!isValid) {
			try {
				String nl = System.lineSeparator() + "    ";

				String isBoardable = "ERROR", noModules = "ERROR";

				try { isBoardable = "" + !ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE); } catch (Exception e) {}
				try { noModules = "" + ship.getVariant().getModuleSlots().isEmpty(); } catch (Exception e) {}

				Global.getLogger(this.getClass()).error("Invalid flagship mission generated!"
						+ nl + "type: " + flagshipType
						+ nl + "rep: " + rep
						+ nl + "ship: " + ship
						+ nl + "faction: " + faction
						+ nl + "fleet: " + fleet
						+ nl + "commander: " + commander
						+ nl + "fleetHasValidAssignment: " + (fleet == null ? false : fleetHasValidAssignment(fleet))
						+ nl + "fleetNotDespawning: " + (fleet == null ? false : !fleet.isDespawning())
						+ nl + "ship is boardable: " + isBoardable
						+ nl + "ship has no modules: " + noModules
				);
			} catch (Exception e) {
				ModPlugin.reportCrash(e, false);
			}
		}


		return isValid;
	}
	protected void createIntel() {
		if(random == null) random = new Random(seed + market.getId().hashCode());

		BaseIntelPlugin intel;

		if(!isDerelictMission && isValidFlagshipIntel()) {
			intel = new FamousFlagshipIntel(this);

			fleet.setNoAutoDespawn(true);
		} else if(isDerelictMission && isValidDerelictIntel()) {
			derelict.setDiscoverable(true);
			derelict.setDiscoveryXP(timeScale.xp);
			orbitedBody.getContainingLocation().addEntity(derelict);
			derelict.setCircularOrbit(orbitedBody, random.nextFloat() * 360, orbitedBody.getRadius() * (2f + random.nextFloat() * 2), 2 + (float)Math.pow(orbitedBody.getRadius() / 10, 2));
			derelict.setName("The " + ship.getShipName());
			derelict.getMemoryWithoutUpdate().set("$sun_sl_customType", "famousWreck");

			intel = new FamousDerelictIntel(this);
		} else {
			ModPlugin.reportCrash(new IllegalStateException("Invalid famous ship intel accepted"));
			return;
		}

		intel.setImportant(true);

		Global.getSector().getIntelManager().addIntel(intel, false, text);
	}
	protected SectorEntityToken chooseOrbitedBody(Random random) {
		float sectorInnerRadius = Global.getSettings().getFloat("sectorHeight") * 0.5f;
		WeightedRandomPicker<SectorEntityToken> eligibleEntities = new WeightedRandomPicker<>(random);

		for(StarSystemAPI system : Global.getSector().getStarSystems()) {
			float distance = system.getLocation().length() / sectorInnerRadius;

			if(system.getConstellation() == null
					|| system.hasTag("theme_core_populated")
					|| system.hasTag("hidden")
					|| system.hasTag("sun_sl_hidden")
					|| system.hasTag("theme_hidden")
					|| !Misc.getMarketsInLocation(system.getCenter().getContainingLocation()).isEmpty()
					|| distance < timeScale.getMinDistance()) continue;

			float flatSystemBonus = 3f / system.getPlanets().size();

			for(PlanetAPI planet : system.getPlanets()) {
				if(planet.getMarket() != null && planet.getMarket().isPlanetConditionMarketOnly()) continue;

				eligibleEntities.add(planet, 1 + flatSystemBonus);
			}
		}

		return eligibleEntities.pick(random);
	}
	protected String getConstellationString() {
		if(derelict.getConstellation() == null) return "Core Worlds";

		try {
			return derelict.getConstellation().getNameWithLowercaseType();
		} catch(Exception e) {
			// It's possible for getting the name of a constellation to fail
			return "depths of a distant constellation";
		}
	}
	protected boolean fleetHasValidAssignment(CampaignFleetAPI flt) {
		if(flt == null) return false;

		FleetAssignmentDataAPI a = flt.getCurrentAssignment();

		if(a == null || a.getAssignment() == null || a.getMaxDurationInDays() - a.getElapsedDays() < 3) return false;

		switch (a.getAssignment()) {
			case GO_TO_LOCATION_AND_DESPAWN: return false;
		}

		return true;
	}
	protected void setAssignmentForRemoteFleet() {
		if(source == null || fleet == null) return;

		if(isRoaming) {
			fleet.addAssignment(fleet.getFaction().isHostileTo(Factions.INDEPENDENT)
							? FleetAssignment.RAID_SYSTEM : FleetAssignment.PATROL_SYSTEM,
					source.getPrimaryEntity(), FamousFlagshipIntel.MAX_DURATION);
		} else {
			fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, source.getPrimaryEntity(),
					FamousFlagshipIntel.MAX_DURATION);

		}
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source.getPrimaryEntity(), Float.MAX_VALUE);

		newFleetWasCreated = true;
	}
	protected void showMap() {


		SectorEntityToken dest = null;// intel.getMapLocation(null);

		if(dest != null) {
			Set<String> tags = new LinkedHashSet<>();
			tags.add(Tags.INTEL_ACCEPTED);

			dialog.getVisualPanel().showMapMarker(dest, "Location: " + dest.getName(),
					getMarket().getFaction().getBaseUIColor(),
					true, rep.getTier().getIcon(), "TODO TEST text?", tags);
		}

		// Too much trouble for too little benefit in only a few cases
		if(false && isDerelictMission) {
			String icon = rep.getTier().getIcon();
			String text = "TEST text?";
			Color color = null;
			StarSystemAPI system = derelict.getStarSystem();
			Set<String> tags = new LinkedHashSet<String>();
			tags.add(Tags.INTEL_MISSIONS);
			SectorEntityToken loc = null;

			switch(granularity) {
				case ENTITY: loc = derelict.getOrbitFocus(); break;
				case SYSTEM: loc = derelict.getStarSystem().getCenter(); break;
				// No way to show location of constelation label...
				//case CONSTELATION: return;
				//case CONSTELATION: loc = (SectorMapAPI)map.getConstellationLabelEntity(derelict.getConstellation().); break;
				default: return;
			}

			if (system.getCenter() instanceof PlanetAPI) {
				color = Misc.setAlpha(((PlanetAPI) system.getCenter()).getSpec().getIconColor(), 255);
				color = Misc.setBrightness(color, 235);
			}

			dialog.getVisualPanel().showMapMarker(orbitedBody,
					"Location: " + orbitedBody.getName(), color,
					true, icon, text, tags);
		}
	}

	@Override
	public boolean shouldShowAtMarket(MarketAPI market) {
		if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return false;

		boolean isAllowed = Integration.isFamousFlagshipEventAvailableAtMarket(market)
				|| Integration.isFamousDerelictEventAvailableAtMarket(market);

		return isAllowed
				&& !market.getMemoryWithoutUpdate().getBoolean(KEY_ACCEPTED_AT_THIS_MARKET_RECENTLY)
				&& getChanceOfAnyStoryEvent() > 0;
	}

	@Override
	protected void regen(MarketAPI market) {
		try {
			playerFleet = Global.getSector().getPlayerFleet();
			if (playerFleet == null) return;
			//if (!Global.getSettings().isDevMode() && this.market == market) return;
            if (this.market == market) return;

			super.regen(market);
			reset();

//			if (Global.getSettings().isDevMode()) random = new Random();

			isPlayerShipStory = random.nextFloat() * getChanceOfAnyStoryEvent() <= getChanceOfPlayerShipLegend();

			if(isPlayerShipStory) {
				while (!RepRecord.getQueuedStories().isEmpty()) {
					String id = RepRecord.getQueuedStories().get(0);

					for(FleetMemberAPI ship : Reputation.getShipsOfNote()) {
						if(id.equals(ship.getId())) {
							this.ship = ship;
							this.rep = RepRecord.get(id);
							break;
						}
					}

					if(this.ship != null) break;
				}
			}

			if(ship == null || rep == null) isPlayerShipStory = false;

			if(!isPlayerShipStory) {
				boolean allowCrew = true;
				int traitCount = 0;
				float totalChance = ModPlugin.FAMOUS_DERELICT_BAR_EVENT_CHANCE + ModPlugin.FAMOUS_FLAGSHIP_BAR_EVENT_CHANCE;
				isDerelictMission = (random.nextFloat() * totalChance <= ModPlugin.FAMOUS_DERELICT_BAR_EVENT_CHANCE);

				if (!Integration.isFamousFlagshipEventAvailableAtMarket(market)) isDerelictMission = true;
				if (!Integration.isFamousDerelictEventAvailableAtMarket(market)) isDerelictMission = false;

				if (isDerelictMission) {
					DerelictShipEntityPlugin.DerelictShipData params = null;
					WeightedRandomPicker<FleetMemberAPI> lostShips = null;

					if(random.nextFloat() < 0.4f) {
						Set<String> idsOfOwnedShips = Util.getIdsOfShipsOwnedByPlayer();

						lostShips = new WeightedRandomPicker<>(random);

						for (FleetMemberAPI fm : Reputation.getShipsOfNote()) {
							RepRecord rep = RepRecord.get(fm);

							if (RepRecord.getLostFamousShips().contains(fm.getId())
									&& !idsOfOwnedShips.contains(fm.getId())
									&& rep != null) {

								if (rep.getTier().ordinal() >= Trait.Tier.Famous.ordinal() && rep.getXp() > 0) {
									lostShips.add(fm);
								}
							}
						}
					}

					if (lostShips != null && !lostShips.isEmpty()) {
						isShipDerelictLostByPlayer = true;
						ship = lostShips.pick();
						params = DerelictShipEntityPlugin.createVariant(ship.getVariant().getOriginalVariant(), this.random, 0);

						wreckData = params.ship;
						wreckData.sModProb = 0;
						timeScale = FamousDerelictIntel.TimeScale.Months;
						allowCrew = true;

						// This is to prevent the same unwanted ships from appearing in this event repeatedly
						// Also prevents the same ship from being chosen while its own recovery mission is active
						RepRecord.getLostFamousShips().remove(ship.getId());
					}

					if(params == null || ship == null) {
						isShipDerelictLostByPlayer = false;

						String variantID = chooseDerelictVariant(market, random);

						if (variantID == null) return;

						params = DerelictShipEntityPlugin.createVariant(variantID, this.random, 0);

						wreckData = params.ship;
						wreckData.sModProb = random.nextFloat();
						ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantID);
						pickShipName(ship, random);
						timeScale = FamousDerelictIntel.TimeScale.chooseTimeScale(random);
						traitCount = timeScale.chooseTraitCount(random);
						allowCrew = random.nextFloat() <= timeScale.survivorChance;
					}

					params.ship.shipName = ship.getShipName();
					params.ship.variant = ship.getVariant();
					orbitedBody = chooseOrbitedBody(random);
					granularity = timeScale.chooseLocationGranularity(random);
					rivalSalvageFleet = random.nextFloat() <= timeScale.salvagerChance && granularity != CONSTELATION;
					cost = random.nextInt(rivalSalvageFleet ? 4 : 6) * ship.getHullSpec().getBaseValue() * 0.03f;
					ambushFleetFP = ModPlugin.FAMOUS_DERELICT_MAY_BE_GUARDED_BY_REMNANT_FLEET
							&& random.nextFloat() <= timeScale.baseAmbushChance * (ship.getHullSpec().getFleetPoints() / 15f)
							? Math.max(0, ship.getHullSpec().getFleetPoints() - 10 * random.nextFloat()) * 8
							: 0;

					switch (granularity) {
						case CONSTELATION:
							cost *= 0;
							break;
						case SYSTEM:
							cost *= 1;
							break;
						case ENTITY:
							cost *= 2;
							break;
					}

					if (wreckData.condition == ShipRecoverySpecial.ShipCondition.PRISTINE && random.nextFloat() < 0.9f) {
						wreckData.condition = ShipRecoverySpecial.ShipCondition.GOOD;
					} else if (wreckData.condition == ShipRecoverySpecial.ShipCondition.GOOD && random.nextFloat() < 0.7f) {
						wreckData.condition = ShipRecoverySpecial.ShipCondition.AVERAGE;
					}

					switch (wreckData.condition) {
						case BATTERED:
							cost *= 0.4f;
							break;
						case WRECKED:
							cost *= 0.6f;
							break;
						case AVERAGE:
							cost *= 0.8f;
							break;
					}

					cost = ((int) (cost / 1000f)) * 1000;

					derelict = BaseThemeGenerator.addSalvageEntity(orbitedBody.getStarSystem(),
							Entities.WRECK, Factions.NEUTRAL, params);
					derelict.getContainingLocation().removeEntity(derelict);
					derelict.setContainingLocation(orbitedBody.getContainingLocation());

					faction = Global.getSector().getFaction(Factions.NEUTRAL);
				} else {
					List<CampaignFleetAPI> eligibleFleets = new LinkedList<>();
					HashMap<String, FactionAPI> bountyFactions = new HashMap<>();

					if (random.nextFloat() < 0.3f) { // Choose a bounty target
						flagshipType = "Bounty";
						boolean vayraBounties = Global.getSettings().getModManager().isModEnabled("vayrasector");

						List<IntelInfoPlugin> bounties = Global.getSector().getIntelManager().getIntel(PersonBountyIntel.class);
						if (vayraBounties) {
							try {
								bounties.addAll(Global.getSector().getIntelManager().getIntel(VayraPersonBountyIntel.class));
							} catch (Exception e) {
								ModPlugin.reportCrash(e, false);
							}
						}

						for (IntelInfoPlugin bounty : bounties) {
							for (CampaignFleetAPI flt : bounty.getMapLocation(null).getContainingLocation().getFleets()) {
								if (flt.getFaction().getId().equals(Factions.NEUTRAL)
										&& bounty.getTimeRemainingFraction() > 0.5f
										&& fleetHasValidAssignment(flt)
										&& !flt.isDespawning()
										&& !isFleetClaimed(flt)) {

									// TODO - Once vayra gives access to getBountyFaction, Fix famous flagship descriptions always show faction as pirates for flagships of fleets spawned by Vayra's sector bounty mission
//								if(vayraBounties && bounty instanceof VayraPersonBountyIntel) {
//									bountyFactions.put(flt.getId(), ((VayraPersonBountyIntel)bounty).getBountyFaction());
//								} else bountyFactions.put(flt.getId(), Global.getSector().getFaction(Factions.PIRATES));

									eligibleFleets.add(flt);
								}
							}
						}
					} else if (random.nextFloat() < 0.4f) { // Choose a fleet in the current system
						flagshipType = "Local";
						for (CampaignFleetAPI flt : Global.getSector().getCurrentLocation().getFleets()) {
							ShipHullSpecAPI spec = flt == null || flt.getFlagship() == null ? null : flt.getFlagship().getHullSpec();

							if (FactionConfig.get(flt.getFaction()).isFamousFlagshipAllowedInFleets()
									&& !flt.isStationMode()
									&& !flt.isDespawning()
									&& !flt.isPlayerFleet()
									&& flt.getBattle() == null
									&& flt.getFaction().isShowInIntelTab()
									&& spec != null
									&& !spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE)
									&& !spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX)
									&& !spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)
									&& !spec.isCivilianNonCarrier()
									&& !isFleetClaimed(flt)
									&& fleetHasValidAssignment(flt)) {

								eligibleFleets.add(flt);
							}
						}
					}

					if (eligibleFleets.isEmpty()) { // Create a fleet in another core system
						flagshipType = "Remote";
						List<MarketAPI> eligibleMarkets = new LinkedList<>();

						for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
							StarSystemAPI system = m.getStarSystem();

							if (system == null
									|| !system.hasTag("theme_core_populated")
									|| system == Global.getSector().getCurrentLocation()
									|| system.hasTag("theme_hidden")
									|| system.hasTag("hidden")
									|| system.hasTag("sun_sl_hidden"))
								continue;

							if (!m.isHidden() && m.getFaction() != null && FactionConfig.get(m.getFaction()) != null
									&& FactionConfig.get(m.getFaction()).isFamousFlagshipAllowedInFleets()) {

								eligibleMarkets.add(m);
							}
						}

						// Event generation has failed. Instead of displaying an error, the inconsequential story prose
						// will be displayed
						if(eligibleMarkets.isEmpty()) return;

						final int MAX_ATTEMPTS = 5;

						for (int i = 0; i < MAX_ATTEMPTS; i++) {
							source = eligibleMarkets.get(random.nextInt(eligibleMarkets.size()));
							FleetFactory.PatrolType type = FleetFactory.PatrolType.values()[random.nextInt(FleetFactory.PatrolType.values().length)];
							Vector2f at = source.getPrimaryEntity().getLocation();

							try {
								fleet = MilitaryBase.createPatrol(type, source.getFactionId(), null, source, source.getLocationInHyperspace(), this.random);

								fleet.setContainingLocation(source.getContainingLocation());
							} catch (Exception e) {
								Global.getLogger(ModPlugin.class).warn("Failed to generate remote fleet: " + source.getFactionId());
								ModPlugin.reportCrash(e, false);
							}

							if (fleet == null || at == null) {
								String nl = System.lineSeparator() + "    ";

								Global.getLogger(this.getClass()).error("Failed to generate valid fleet!"
										+ nl + "attempt:" + (i + 1)
										+ nl + "fleet: " + fleet
										+ nl + "at: " + at
										+ nl + "source: " + source
										+ nl + "faction: " + (source == null ? null : source.getFactionId())
										+ nl + "type: " + type
								);

								if (source != null) eligibleMarkets.remove(source);

								if (i == MAX_ATTEMPTS - 1) return;
								else continue;
							}

							fleet.setLocation(at.x, at.y);

							isRoaming = random.nextFloat() < 0.2f;

							setAssignmentForRemoteFleet();

							activity = isRoaming
									? fleet.getFaction().isHostileTo(Factions.INDEPENDENT) ? "raiding " : "patrolling "
									: "defending " + source.getPrimaryEntity().getName() + " ";

							break;
						}

					} else {
						fleet = eligibleFleets.get(random.nextInt(eligibleFleets.size()));

						if (fleet.getFaction().getId().equals(Factions.NEUTRAL)) {
							activity = "hiding out ";
						} else if (fleet.getCurrentAssignment() == null) {
							activity = "";
						} else if (fleet.getCurrentAssignment().getActionText() != null
								&& fleet.getCurrentAssignment().getActionText().length() > 3) {

							activity = fleet.getCurrentAssignment().getActionText();

							if (activity.startsWith("delivering")) {
								activity = activity.replace("delivering", "hauling");
								activity = activity.split(" to ")[0];
							}

							activity += " ";
						} else if (fleet.getCurrentAssignment().getAssignment() == FleetAssignment.PATROL_SYSTEM) {
							activity = fleet.getFaction().isHostileTo(Factions.INDEPENDENT) ? "raiding " : "patrolling ";
						}
					}

					if (fleet != null) {
						try {
							fleet.inflateIfNeeded();

							commander = fleet.getCommander();
							ship = fleet.getFlagship();
							faction = fleet.getFaction();
							traitCount = 4;

							if (fleet.getFleetPoints() > 30) traitCount += 2;
							if (fleet.getFleetPoints() > 300) traitCount += 2;

							claimFleet(fleet);
						} catch (Exception e) {
							reset();

							Global.getLogger(this.getClass()).warn("Failed to inflate fleet");
							ModPlugin.reportCrash(e, false);
						}
					}
				}

				if (ship != null) {
					ship.getVariant().addTag(Tags.TAG_RETAIN_SMODS_ON_RECOVERY);

					if (isDerelictMission) {
						rep = RepRecord.getOrCreate(ship);
						ship.setOwner(1);
						ship.setCaptain(null);
						CampaignFleetAPI temp = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, FleetTypes.PATROL_SMALL, true);
						temp.getFleetData().addFleetMember(ship);

						prepareMember(ship, wreckData);
					} else {
						rep = new RepRecord(ship, faction.getId(), allowCrew);
						int loyalty = (random.nextFloat() < 0.8 ? 1 : -1)
								* (int) Math.floor(Math.pow(random.nextFloat(), 0.75f) * ModPlugin.LOYALTY_LIMIT);
						rep.setLoyalty(commander, loyalty);

						int sMods = Math.max(1, random.nextInt(3) - ship.getVariant().getSMods().size());

						if (sMods > 0) {
							List<HullModSpecAPI> mods = new ArrayList<>();

							for (String id : ship.getVariant().getNonBuiltInHullmods()) {
								HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);

								if(!spec.hasTag(Tags.HULLMOD_NO_BUILD_IN)) mods.add(spec);
							}

							Collections.sort(mods, new Comparator<HullModSpecAPI>() {
								@Override
								public int compare(HullModSpecAPI mod1, HullModSpecAPI mod2) {
									ShipAPI.HullSize size = ship.getHullSpec().getHullSize();
									return mod1.getCostFor(size) - mod2.getCostFor(size);
								}
							});

							for (int i = 0; i < sMods && i < mods.size(); i++) {
								ship.getVariant().removeMod(mods.get(i).getId());
								ship.getVariant().addPermaMod(mods.get(i).getId(), true);
							}

							fleet.inflateIfNeeded();
						}
					}

					if(!isShipDerelictLostByPlayer) {
						RepRecord.addDestinedTraitsToShip(ship, allowCrew, traitCount);
						rep.applyToShip(ship);
					}
				}
			}
		} catch (Exception e) {
			ModPlugin.reportCrash(e);
		}
	}

	@Override
	public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		try {
			super.addPromptAndOption(dialog, memoryMap);

			regen(dialog.getInteractionTarget().getMarket());

			TextPanelAPI text = dialog.getTextPanel();
			text.addPara("An animated " + getManOrWoman() + " is loudly telling what seems to be a story " +
					"about the tribulations and exploits of some fleet. A few patrons look on with varying " +
					"degrees of interest.");

			dialog.getOptionPanel().addOption("Listen to the animated storyteller", this);
		} catch (Exception e) {
			ModPlugin.reportCrash(e);
		}
	}

	@Override
	public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		try {
			super.init(dialog, memoryMap);

			done = false;

			dialog.getVisualPanel().showPersonInfo(person, true);

			if(isPlayerShipStory) optionSelected(null, OptionId.PLAYER_SHIP_INIT);
			else if(!isDerelictMission && isValidFlagshipIntel()) optionSelected(null, OptionId.FLAGSHIP_INIT);
			else if(isDerelictMission && isValidDerelictIntel()) optionSelected(null, OptionId.DERELICT_INIT);
			else optionSelected(null, OptionId.BOGUS_STORY);
		} catch (Exception e) {
			ModPlugin.reportCrash(e);
		}
	}

	@Override
	public void optionSelected(String optionText, Object optionData) {
		try {
			if (!(optionData instanceof OptionId)) return;

			MutableValue purse = Global.getSector().getPlayerFleet().getCargo().getCredits();
			OptionPanelAPI options = dialog.getOptionPanel();
			TextPanelAPI text = dialog.getTextPanel();
			options.clearOptions();

			switch ((OptionId) optionData) {
				case PLAYER_SHIP_INIT: {
					dialog.getVisualPanel().showFleetMemberInfo(ship);

					rep.getStory().tell(ship, text, market.getFaction().getRelToPlayer());

					Global.getSector().getPlayerStats().addStoryPoints(1, text, false);

					options.addOption("Continue", OptionId.LEAVE);
					options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);

					endEvent();

					// Better for this to throw an error if the queue is empty at this point
					RepRecord.getQueuedStories().remove(0);
					break;
				}
				case FLAGSHIP_INIT: {
					dialog.getVisualPanel().showFleetMemberInfo(ship, true);

					String bestFactionPrefix = faction.getEntityNamePrefix();
					if (bestFactionPrefix.isEmpty()) bestFactionPrefix = faction.getPersonNamePrefix();
					if (bestFactionPrefix.isEmpty()) bestFactionPrefix = faction.getDisplayName();

					String
							hisOrHer = commander.getGender() == FullName.Gender.MALE ? "his" : "her",
							name = commander.getNameString().trim(),
							location = fleet.isInCurrentLocation() ? "" : fleet.getContainingLocation().getName(),
							locationDesc,
							fleetDesc = faction.getId().equals(Factions.NEUTRAL) ? "a wanted fleet"
									: Util.getWithAnOrA(bestFactionPrefix) + " " + fleet.getName().toLowerCase(),
							distance = fleet.isInCurrentLocation() ? "" :
									(int) Misc.getDistanceLY(fleet.getContainingLocation().getLocation(),
											Global.getSector().getPlayerFleet().getContainingLocation().getLocation()) + "";

					if(fleet.isInCurrentLocation()) {
						locationDesc = "%s%sin this system. ";
					} else if(faction.getId().equals(Factions.NEUTRAL)) {
						locationDesc = "in the %s about %s light years away. This will help you pinpoint the location of the bounty target. ";
					} else {
						locationDesc = "in the %s about %s light years from your current location. ";
					}

					String[] words = fleetDesc.split(" ");
					if (!APPROVED_FLEET_NAMES.contains(words[words.length - 1].toLowerCase())) {
						fleetDesc = Util.getWithAnOrA(bestFactionPrefix) + " fleet";
					}

					if (faction.getId().equals(Factions.NEUTRAL)) {
						faction = Global.getSector().getFaction(Factions.PIRATES);
					}

					activity = activity == null || activity.equals("null") ? "somewhere " : activity;

					if( fleet.isInCurrentLocation()) {
						if (activity.startsWith("raiding")) activity = "raiding ";
						else if (activity.startsWith("patrolling")) activity = "patrolling ";
					}

					text.addPara("With an excess of dramatic gestures and exclamations, the storyteller delivers an amateurish " +
							"narration about %s commander named " + name + " and " + hisOrHer + " flagship, the " + ship.getShipName() +
							". Much of the story is superfluous, but you glean a few details that just might turn out to " +
							"be useful.", Misc.getTextColor(), faction.getColor(), fleetDesc);

					text.addPara(name + "'s fleet was recently " + activity + locationDesc + Misc.ucFirst(hisOrHer) +
							" flagship is %s known for the following traits:",
							Misc.getTextColor(), Misc.getHighlightColor(), location, distance,
							Util.getShipDescription(ship));

					Util.showTraits(text, rep, null, Util.isShipCrewed(ship), Util.isShipBiological(ship), ship.getHullSpec().getHullSize());

					LoyaltyLevel ll = rep.getLoyalty(commander);
					String desc = "The crew of the " + ship.getShipName() + " is %s " + ll.getPreposition() + " "
							+ commander.getNameString().trim() + ".";

					text.addPara(desc, Misc.getTextColor(), Misc.getHighlightColor(), ll.getName());

					options.addOption("Take note of the whereabouts of " + commander.getNameString() + "'s fleet", OptionId.ACCEPT);
					options.addOption("Carry on with more important matters", OptionId.LEAVE);
					options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
					break;
				}
				case DERELICT_INIT: {
					dialog.getVisualPanel().showFleetMemberInfo(ship, true);

					String shape, shapeDesc, lossRecency = timeScale.ordinal() > 1 ? "long-lost" : "lost";

					switch (wreckData.condition) {
						case WRECKED:
							shapeDesc = "in spite of being %s";
							shape = "nearly demolished";
							break;
						case BATTERED:
							shapeDesc = "in spite of being %s";
							shape = "heavily damaged";
							break;
						case AVERAGE:
							shapeDesc = "and in %s";
							shape = "decent condition";
							break;
						default:
							shapeDesc = "and in surprisingly %s";
							shape = "good condition";
							break;
					}

					if(isShipDerelictLostByPlayer) {
						text.addPara("You join the sparse group of people near the storyteller as " + getHeOrShe() +
										" describes the destruction of an all-too familiar %s: the %s. Strangely, " +
										"many of the details don't match your recollection of parting with it, " +
										"and you begin to suspect that the story is not, in fact, about the ship you once owned. " +
										"As you continue to listen, however, you realize that, through a series of unlikely " +
										"events, the ship was eventually recovered by someone else. It was they who lost " +
										" the " + ship.getShipName() + " to the battle the storyteller is describing. " +
										"Your old ship is purportedly still in one piece " + shapeDesc +
										". Supposedly, it is now drifting through the void somewhere in the %s. " +
										"After making a few queries on your TriPad, you determine that this is, in fact, " +
										"quite plausible. You recall that the ship was known for the following traits:",
								Misc.getTextColor(), Misc.getHighlightColor(), Util.getShipDescription(ship, false), ship.getShipName(),
								shape, getConstellationString());
					} else {
						text.addPara("You join the sparse group of people near the storyteller as " + getHeOrShe() + " begins " +
										"a gruesome and gratuitously detailed description of the destruction of a " + lossRecency + " %s with a " +
										"name you think you may have heard before: the %s. After yet more " +
										"dismemberment and carnage, " + getHeOrShe() + " reveals that the ship is still in one piece " +
										shapeDesc + ". Supposedly, it's drifting lifelessly somewhere in the %s %s later. " +
										"After making a few queries on your TriPad, you determine that this is, in fact, " +
										"quite plausible. You also learn " +
										"that the ship is known for the following traits:", Misc.getTextColor(),
								Misc.getHighlightColor(), Util.getShipDescription(ship, false), ship.getShipName(),
								shape, getConstellationString(), timeScale.getName().toLowerCase());
					}

					Util.showTraits(text, rep, null, Util.isShipCrewed(ship), Util.isShipBiological(ship), ship.getHullSpec().getHullSize());

					options.addOption("Ask where the " + ship.getShipName() + " might be found", OptionId.INQUIRE);
					options.addOption("Carry on with more important matters", OptionId.LEAVE);
					options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
					break;
				}
				case INQUIRE: {
					dialog.getVisualPanel().showPersonInfo(getPerson(), true);

					String accept, payDouble = null,
							reject = "Bid the storyteller farewell and leave in search of more promising prospects",
							desc = "After a few minutes you get an opportunity to introduce yourself in private and ask about the "
									+ ship.getShipName() + ".";

					if (timeScale.ordinal() > 1 && granularity == CONSTELATION) {
						desc += " \"Oh, that's just an old " + (timeScale.ordinal() == 3 ? "legend" : "story") +
								", I'm afraid\" the storyteller explains wistfully. " +
								"\"No one has seen the " + ship.getShipName() + " in " + timeScale.getName().toLowerCase() +
								", and you're not the first to try to find it. I don't doubt that it's out there " +
								"somewhere in the " + getConstellationString() + ", but I " +
								"don't like your odds of finding it.";
						accept = "Try to find the long-lost derelict in spite of " + getHisOrHer() + " warning";
						reject = "Heed " + getHisOrHer() + " advice and forget about this fool's errand";
						showMap();
					} else if (granularity == CONSTELATION) {
						desc += " The storyteller rambles on for a bit, speculating about where the lost derelict might be, but " +
								"it quickly becomes apparent that " + getHeOrShe() + " doesn't know anything useful.";
						accept = "Record what you've learned anyway, in the hope of someday finding the lost ship";
						showMap();
					} else {
						desc += " The storyteller grins when " + getHeOrShe() + " answers. \"A treasure hunter! You're " +
								"in luck. I do happen to know where the " + ship.getShipName() + " is.";

						if (granularity == FamousDerelictIntel.LocationGranularity.ENTITY) {
							desc += " In fact, I know exactly where she was when she was lost.";
						}

						if (cost == 0 && orbitedBody != null) {
							if (granularity == FamousDerelictIntel.LocationGranularity.ENTITY) {
								desc += " She should still be in orbit around " + orbitedBody.getName()
										+ (orbitedBody.isStar() ? "." : ", in " + orbitedBody.getStarSystem().getName() + ".");

							} else {
								desc += " I don't know the specifics, but the " + ship.getShipName() + "'s fleet was " +
										"defeated somewhere in " + orbitedBody.getStarSystem().getName() + ".";
							}

							if (rivalSalvageFleet) {
								desc += " I should warn you, however; you're not the first fleet commander to ask about "
										+ "this. Others are probably after the same prize.\"";
							} else desc += "\"";

							accept = "Thank " + getHimOrHer() + " for the information and record what you've learned.";
							showMap();
						} else {
							desc += " Or course, you wouldn't expect me to part with such valuable information for free. " +
									"I'm sure we can settle on an appropriate finder's fee.\"\n\n";

							if (rivalSalvageFleet) {
								desc += "The storyteller becomes evasive when you ask how many times " + getHeOrShe()
										+ "'s sold this information.";

								payDouble = "Offer " + Misc.getDGSCredits(cost * 2) + " for the location of both the " +
										"derelict and any fleet planning to recover it.";
							} else {
								desc += "The storyteller assures you that " + getHeOrShe() + " hasn't divulged the derelict's " +
										"location to anyone else, and agrees not to if you strike a deal.";
							}

							desc += " After some haggling, the fee ends up being %s.";

							accept = "Agree to pay " + Misc.getDGSCredits(cost) + " for the location of the " + ship.getShipName();
						}
					}

					text.addPara(desc, Misc.getTextColor(), Misc.getHighlightColor(), Misc.getDGSCredits(cost));


					options.addOption(accept, OptionId.ACCEPT);
					if(payDouble != null) options.addOption(payDouble, OptionId.DOUBLE_DOWN);
					options.addOption(reject, OptionId.LEAVE);
					options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);

					if (cost > 0 && cost > purse.get()) {
						options.setEnabled(OptionId.ACCEPT, false);
						options.setTooltip(OptionId.ACCEPT, "You don't have enough credits.");
					}

					if (payDouble != null && cost > 0 && cost * 2 > purse.get()) {
						options.setEnabled(OptionId.DOUBLE_DOWN, false);
						options.setTooltip(OptionId.DOUBLE_DOWN, "You don't have enough credits.");
					}
					break;
				}
				case BOGUS_STORY: {
					text.addPara("For a while you listen to the story, but it quickly becomes obvious that it's nothing " +
							"but a fanciful fabrication.");
					options.addOption("Carry on with more important matters", OptionId.LEAVE);
					options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
					BarEventManager.getInstance().notifyWasInteractedWith(this);
					break;
				}
				case ACCEPT: {
					if (cost > 0) {
						purse.subtract(cost);
						AddRemoveCommodity.addCreditsLossText((int) cost, text);
					}

					text.addPara("You record what you've learned in the hope of eventually making use of the information.");

					showMap();
					createIntel();

					options.addOption("Continue", OptionId.CONTINUE);
					break;
				}
				case DOUBLE_DOWN: {
					cost *= 2;
					rivalOriginIsKnown = true;
					optionSelected(null, OptionId.ACCEPT);
					break;
				}
				case CONTINUE: {
					endEvent();
					optionSelected(null, OptionId.LEAVE);
				}
				case LEAVE: {
					if (newFleetWasCreated && fleet != null && fleet.getContainingLocation() != null) {
						fleet.getContainingLocation().removeEntity(fleet);
					}

					done = noContinue = true;
					break;
				}
			}
		} catch (Exception e) {
			ModPlugin.reportCrash(e);
		}
	}

	@Override
	protected String getPersonFaction() { return market.getFactionId(); }

	@Override
	protected String getPersonRank() { return Ranks.CITIZEN; }

	@Override
	protected String getPersonPost() { return Ranks.CITIZEN; }

	// TODO - Update methods below (yoinked from ShipRecoverySpecial) with new Starsector versions
	// Up to date as of 0.97a

	public void prepareMember(FleetMemberAPI member, ShipRecoverySpecial.PerShipData shipData) {

		int hits = getHitsForCondition(member, shipData.condition);
		int dmods = getDmodsForCondition(shipData.condition);

		int reduction = (int) playerFleet.getStats().getDynamic().getValue(Stats.SHIP_DMOD_REDUCTION, 0);
		reduction = random.nextInt(reduction + 1);
		dmods -= reduction;


		member.getStatus().setRandom(random);

		for (int i = 0; i < hits; i++) {
			member.getStatus().applyDamage(1000000f);
		}

		member.getStatus().setHullFraction(getHullForCondition(shipData.condition));
		member.getRepairTracker().setCR(0f);


		ShipVariantAPI variant = member.getVariant();
		variant = variant.clone();
		variant.setOriginalVariant(null);

		int dModsAlready = DModManager.getNumDMods(variant);
		dmods = Math.max(0, dmods - dModsAlready);

		if (dmods > 0 && shipData.addDmods) {
			DModManager.setDHull(variant);
		}
		member.setVariant(variant, false, true);

		if (dmods > 0 && shipData.addDmods) {
			DModManager.addDMods(member, true, dmods, random);
		}

		if (shipData.sModProb > 0 && random.nextFloat() < shipData.sModProb) {
			int num = 1;
			float r = random.nextFloat();
			if (r > 0.85f) {
				num = 3;
			} else if (r > 0.5f) {
				num = 2;
			}

			WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
			for (String id : variant.getHullMods()) {
				HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
				if (spec.isHidden()) continue;
				if (spec.isHiddenEverywhere()) continue;
				if (spec.hasTag(Tags.HULLMOD_DMOD)) continue;
				if (spec.hasTag(Tags.HULLMOD_NO_BUILD_IN)) continue;
				if (variant.getPermaMods().contains(spec.getId())) continue;
				picker.add(id, spec.getCapitalCost());
			}
			for (int i = 0; i < num && !picker.isEmpty(); i++) {
				String id = picker.pickAndRemove();
				variant.addPermaMod(id, true);
				//variant.getPermaMods().add(id);
			}
		}


		if (shipData.pruneWeapons) {
			float retain = getFighterWeaponRetainProb(shipData.condition);
			FleetEncounterContext.prepareShipForRecovery(member, false, false, false, retain, retain, random);
			member.getVariant().autoGenerateWeaponGroups();
		}
	}
	protected float getHullForCondition(ShipRecoverySpecial.ShipCondition condition) {
		switch (condition) {
			case PRISTINE: return 1f;
			case GOOD: return 0.6f + random.nextFloat() * 0.2f;
			case AVERAGE: return 0.4f + random.nextFloat() * 0.2f;
			case BATTERED: return 0.2f + random.nextFloat() * 0.2f;
			case WRECKED: return random.nextFloat() * 0.1f;
		}
		return 1;
	}
	protected int getDmodsForCondition(ShipRecoverySpecial.ShipCondition condition) {
		if (condition == ShipRecoverySpecial.ShipCondition.PRISTINE) return 0;

		switch (condition) {
			case GOOD: return 1;
			case AVERAGE: return 1 + random.nextInt(2);
			case BATTERED: return 2 + random.nextInt(2);
			case WRECKED: return 3 + random.nextInt(2);
		}
		return 1;
	}
	protected float getFighterWeaponRetainProb(ShipRecoverySpecial.ShipCondition condition) {
		switch (condition) {
			case PRISTINE: return 1f;
			case GOOD: return 0.67f;
			case AVERAGE: return 0.5f;
			case BATTERED: return 0.33f;
			case WRECKED: return 0.2f;
		}
		return 0f;
	}
	protected int getHitsForCondition(FleetMemberAPI member, ShipRecoverySpecial.ShipCondition condition) {
		if (condition == ShipRecoverySpecial.ShipCondition.PRISTINE) return 0;
		if (condition == ShipRecoverySpecial.ShipCondition.WRECKED) return 20;

		switch (member.getHullSpec().getHullSize()) {
			case CAPITAL_SHIP:
				switch (condition) {
					case GOOD: return 2 + random.nextInt(2);
					case AVERAGE: return 4 + random.nextInt(3);
					case BATTERED: return 7 + random.nextInt(6);
				}
				break;
			case CRUISER:
				switch (condition) {
					case GOOD: return 1 + random.nextInt(2);
					case AVERAGE: return 2 + random.nextInt(3);
					case BATTERED: return 4 + random.nextInt(4);
				}
				break;
			case DESTROYER:
				switch (condition) {
					case GOOD: return 1 + random.nextInt(2);
					case AVERAGE: return 2 + random.nextInt(2);
					case BATTERED: return 3 + random.nextInt(3);
				}
				break;
			case FRIGATE:
				switch (condition) {
					case GOOD: return 1;
					case AVERAGE: return 2;
					case BATTERED: return 3;
				}
				break;
		}
		return 1;
	}
}



