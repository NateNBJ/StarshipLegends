package starship_legends.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.PersonBountyIntel;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import starship_legends.*;

import java.awt.*;
import java.util.*;

public class FamousDerelictIntel extends FleetLogIntel {
	public enum LocationGranularity {CONSTELATION, SYSTEM, ENTITY }
	public enum TimeScale {
		//         survv  dist  slvg  grnu  trts  xp
		Months    (0.60f, 0.0f, 0.8f, 0.9f, 5, 6, 100,   "Surprisingly"),
		Years     (0.20f, 0.3f, 0.4f, 0.6f, 5, 7, 500,   "Amazingly"),
		Decades   (0.05f, 0.6f, 0.1f, 0.3f, 6, 7, 2500,  "Miraculously"),
		Centuries (0.00f, 0.9f, 0.0f, 0.1f, 6, 8, 12500, "Impossibly");

		final float survivorChance, salvagerChance, improveGranularityChance, minDistance, xp;
		final int minTraits, maxTraits;
		final String odds;


		TimeScale(float survivorChance, float minDistance, float salvagerChance, float improveGranularityChance,
				  int minTraits, int maxTraits, int xp, String odds) {

			this.survivorChance = survivorChance;
			this.salvagerChance = salvagerChance;
			this.improveGranularityChance = improveGranularityChance;
			this.minDistance = minDistance;
			this.minTraits = minTraits;
			this.maxTraits = maxTraits;
			this.xp = xp;
			this.odds = odds;
		}

		public String getName() {
			return this == Years ? "Several Cycles" : name();
		}
		public float getMinDistance() { return minDistance; }
		public String getSurvivalDescription() {
			return odds + ", a small fraction of the original crew has managed to survive after " + name().toLowerCase()
					+ " within the cold husk of the ship.";
		}
		public int chooseTraitCount(Random random) {
			return (int)((minTraits + random.nextInt(maxTraits - minTraits))
					* (ModPlugin.DEFAULT_TRAIT_LIMIT / (float)Trait.getTraitLimit()));
		}

		public LocationGranularity chooseLocationGranularity(Random random) {
			if(random.nextFloat() < improveGranularityChance) {
				if(random.nextFloat() < improveGranularityChance) {
					return LocationGranularity.ENTITY;
				} else return LocationGranularity.SYSTEM;
			} else return LocationGranularity.CONSTELATION;
		}

		public static TimeScale chooseTimeScale(Random random) {
			float roll = random.nextFloat();

			if(roll < 0.1f) return Centuries;
			if(roll < 0.3f) return Decades;
			if(roll < 0.6f) return Years;
			if(roll < 1.0f) return Months;

			return Months;
		}
	}

	public static float MAX_DURATION = PersonBountyIntel.MAX_DURATION * 0.8f;
	public static final String MEMORY_KEY = "$sun_sl_famousDerelictKey";

	boolean derelictRecoveredByPlayer = false, derelictRecoveredByRival = false, survivorsWereRescued = false;

	protected FleetMemberAPI ship;
	protected final SectorEntityToken derelict;
	protected final RepRecord rep;
	protected final ShipRecoverySpecial.PerShipData wreckData;
	protected final LocationGranularity granularity;
	protected final CampaignFleetAPI rivalFleet;
	protected final TimeScale timeScale;
	protected final MarketAPI market;
	protected final SectorEntityToken rivalOrigin;

	protected void sendRivalFleetHome() {
		if(rivalFleet == null || rivalFleet.isDespawning() || !rivalFleet.isAlive() || rivalFleet.isEmpty()) return;

		Misc.makeUnimportant(rivalFleet, "sun_sl_famous_derelict");

		rivalFleet.clearAssignments();
		rivalFleet.setFaction(Factions.INDEPENDENT);
		rivalFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, rivalOrigin, Float.MAX_VALUE);
	}
	protected CampaignFleetAPI spawnRivalFleet(FamousShipBarEvent event) {
		Random random = event.getRandom();
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);

		float fp = ship.getHullSpec().getFleetPoints();

		picker.add(FleetTypes.SCAVENGER_SMALL, 15f - fp);
		picker.add(FleetTypes.SCAVENGER_MEDIUM, 10f);
		picker.add(FleetTypes.SCAVENGER_LARGE, fp - 10f);

		String type = picker.pick();

		int combat = 0;
		int freighter = 0;
		int tanker = 0;
		int transport = 0;
		int utility = 0;

		if (type.equals(FleetTypes.SCAVENGER_SMALL)) {
			combat = random.nextInt(2) + 1;
			tanker = random.nextInt(2) + 1;
			utility = random.nextInt(2) + 1;
		} else if (type.equals(FleetTypes.SCAVENGER_MEDIUM)) {
			combat = 4 + random.nextInt(5);
			freighter = 4 + random.nextInt(5);
			tanker = 3 + random.nextInt(4);
			transport = random.nextInt(2);
			utility = 2 + random.nextInt(3);
		} else if (type.equals(FleetTypes.SCAVENGER_LARGE)) {
			combat = 7 + random.nextInt(8);
			freighter = 6 + random.nextInt(7);
			tanker = 5 + random.nextInt(6);
			transport = 3 + random.nextInt(8);
			utility = 4 + random.nextInt(5);
		}

		combat *= 5f;
		freighter *= 3f;
		tanker *= 3f;
		transport *= 1.5f;

		FleetParamsV3 params = new FleetParamsV3(
				market,
				rivalOrigin.getLocationInHyperspace(),
				Factions.SCAVENGERS, // quality will always be reduced by non-market-faction penalty, which is what we want
				null,
				type,
				combat, // combatPts
				freighter, // freighterPts
				tanker, // tankerPts
				transport, // transportPts
				0f, // linerPts
				utility, // utilityPts
				0f // qualityMod
		);
		params.random = random;

		final CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

		if (fleet == null || fleet.isEmpty()) return null;

		fleet.setLocation(rivalOrigin.getLocation().x, rivalOrigin.getLocation().y);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SCAVENGER, true);
		fleet.setFaction(Factions.INDEPENDENT, true);

		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, rivalOrigin, MAX_DURATION * 0.2f,
				"preparing for a recovery expedition");
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, derelict, MAX_DURATION, "on a recovery expedition", new Script() {
			@Override
			public void run() {
				Misc.makeLowRepImpact(fleet, "scav");
				fleet.setFaction(Factions.PIRATES);
				fleet.clearAssignments();
				fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, derelict,
						MAX_DURATION - Global.getSector().getClock().getElapsedDaysSince(timestamp),
						"preparing to recover the " + ship.getShipName());
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, derelict, 30, "recovering the " + ship.getShipName(),
						new Script() {
							@Override
							public void run() {
								sendRivalFleetHome();
								Misc.makeImportant(rivalFleet, "sun_sl_famous_derelict");
								ship.getRepairTracker().setMothballed(true);
								rivalFleet.getFleetData().addFleetMember(ship);
								derelictRecoveredByRival = true;

								if (derelict != null) {
									Misc.makeUnimportant(derelict, "sun_sl_famous_derelict");
									derelict.getContainingLocation().removeEntity(derelict);
								}
							}
						});
			}
		});

		fleet.setNoFactionInName(true);

		rivalOrigin.getContainingLocation().addEntity(fleet);

		return fleet;
	}
	protected void showRecoveryDescription(TextPanelAPI text) {
		text.addPara("Signature markings and energy readings indicate that this is, in fact, the "
				+ ship.getShipName() + ". Your salvage crews determine that the ship could be restored to basic "
				+ "functionality.");

		if(rep.hasTraitWithTag(TraitType.Tags.CREW)) {
			if(survivorsWereRescued) {
				text.addPara("The survivors of the " + ship.getShipName() + " volunteer to assist in the recovery "
						+ "effort. They seem to be eager to reclaim it.");
			} else {
				int crew = Math.max(3, (int) ship.getMinCrew() / 20);

				text.addPara(timeScale.getSurvivalDescription() + " Grateful for being rescued, the survivors join your "
						+ "fleet and offer to assist in the recovery effort.");

				Global.getSector().getPlayerFleet().getCargo().addCrew(crew);

				AddRemoveCommodity.addCommodityGainText(Commodities.CREW, crew, text);

				survivorsWereRescued = true;
			}
		}
	}

	public void notifyThatPlayerRecoveredDerelict() {
		derelictRecoveredByPlayer = true;
		sendRivalFleetHome();
	}
	public void checkIfPlayerRecoveredDerelict() {
		if(rivalFleet == null) return;

		for(FleetMemberAPI fm : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
			if(fm == ship) {
				notifyThatPlayerRecoveredDerelict();
				break;
			}
		}
	}

	public FamousDerelictIntel(FamousShipBarEvent event) {
		super(event.rivalSalvageFleet ? MAX_DURATION : Float.MAX_VALUE);

		ship = event.ship;
		rep = event.rep;
		derelict = event.derelict;
		wreckData = event.wreckData;
		granularity = event.granularity;
		market = event.getMarket();
		timeScale = event.timeScale;

		if(event.rivalSalvageFleet) {

			WeightedRandomPicker<SectorEntityToken> eligibleMarkets = new WeightedRandomPicker<>();

			for(StarSystemAPI system : Global.getSector().getStarSystems()) {
				if(!system.hasTag("theme_core_populated") || system.hasTag("hidden") || system.hasTag("sun_sl_hidden"))
					continue;

				for(MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
					if(m.isHidden() || m.getFaction().isHostileTo(Factions.INDEPENDENT)) continue;

					SectorEntityToken entity = m.getPrimaryEntity();

					float weight = 1 / (1 + Misc.getDistanceLY(market.getPrimaryEntity(), entity));

					weight += m.getSize() / 2f;

					if(m.getFaction().getId().equals(Factions.INDEPENDENT)) weight *= 3;

					eligibleMarkets.add(entity, weight);
				}
			}

			rivalOrigin = eligibleMarkets.pick(event.getRandom());

			//rivalOrigin = market.getPrimaryEntity();
			rivalFleet = spawnRivalFleet(event);
		} else {
			rivalOrigin = null;
			rivalFleet = null;
		}

		derelict.getMemoryWithoutUpdate().set(MEMORY_KEY, this);

		Misc.makeImportant(derelict, "sun_sl_famous_derelict");
		setRemoveTrigger(derelict);
	}

	@Override
	protected void advanceImpl(float amount) {
		if(MAX_DURATION < Global.getSector().getClock().getElapsedDaysSince(timestamp)) sendRivalFleetHome();
	}

	@Override
	public String getSmallDescriptionTitle() {
		return "Derelict - " + ship.getShipName();
	}

	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		info.addPara("Derelict - " + ship.getShipName(), getTitleColor(mode), 0f);

		float pad = 3f;
		float opad = 10f;
		Color tc = getBulletColorForMode(mode);
		float initPad = (mode == ListInfoMode.IN_DESC) ? opad : pad;


		bullet(info);
		//boolean isUpdate = getListInfoParam() != null; // true if notification?

		info.addPara("Ship Class: %s", initPad, tc, Misc.getHighlightColor(), ship.getHullSpec().getHullName());

		if(getDaysRemaining() > 0 && rivalFleet != null) addDays(info, "left", getDaysRemaining(), tc, 0f);
	}

	@Override
	public float getTimeRemainingFraction() {
		return rivalFleet == null
				? super.getTimeRemainingFraction()
				: 1 - Global.getSector().getClock().getElapsedDaysSince(timestamp) / MAX_DURATION;
	}

	float getDaysRemaining() { return Math.round(MAX_DURATION - Global.getSector().getClock().getElapsedDaysSince(timestamp)); }

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		try {
			String place = "", place2 = "", placeDesc, timeAgo = Misc.getAgoStringForTimestamp(timestamp).toLowerCase();

			info.addPara("You heard about a famous derelict ship " + timeAgo + " that is likely to be recoverable. " +
					"It is %s named the %s, which was known for the following traits:", 10,
					Misc.getTextColor(), Misc.getHighlightColor(), Util.getShipDescription(ship), ship.getShipName());

			info.addPara("", 0);

			Util.showTraits(info, rep, null, !FactionConfig.get(Factions.NEUTRAL).isCrewlessTraitNamesUsed(), ship.getHullSpec().getHullSize());

			switch (granularity) {
				case CONSTELATION:
					placeDesc = "somewhere in the %s constellation";
					place = derelict.getConstellation().getName();
					break;
				case SYSTEM:
					placeDesc = "somewhere in the %s";
					place = derelict.getStarSystem().getName();
					break;
				case ENTITY:
					placeDesc = derelict.getOrbitFocus().isStar() ? "orbiting %s" : "orbiting %s in the %s";
					place = derelict.getOrbitFocus().getName();
					place2 = derelict.getStarSystem().getName();
					break;
				default:
					placeDesc = "ERROR";
					break;
			}

			info.addPara("According to your informant, the " + ship.getShipName() + " can be found " + placeDesc, 20,
					Misc.getTextColor(), Misc.getHighlightColor(), place, place2);

			if(rivalFleet != null) {
				int daysLeft = (int)getDaysRemaining();
				String msg = "It is likely that others are searching for the " + ship.getShipName() + " as well";

				msg += daysLeft > 0
						? ", but you should have about %s before it is recovered by someone else."
						: ". It was probably %s by now. If so, it might still be possible to find the recovery fleet"
								+ " on its way back to the core worlds.";

				String days = daysLeft > 1 ? daysLeft + " days" : "a day";

				info.addPara(msg, 10,
						Misc.getTextColor(), Misc.getHighlightColor(), daysLeft > 0
								? days : "already recovered by someone else");
			}

			if(Global.getSettings().isDevMode()) {
				info.addButton("Go to derelict", "gotoDerelict", width, 20, 6);
				if(rivalFleet != null) info.addButton("Go to rival salvage fleet", "gotoRival", width, 20, 6);
			}
		} catch (Exception e) {
			ModPlugin.reportCrash(e);
		}
	}

	@Override
	public boolean shouldRemoveIntel() {
		boolean noRivalFleet = rivalFleet == null || rivalFleet.isDespawning() || rivalFleet.isEmpty()
				|| !rivalFleet.isAlive();

		return ModPlugin.REMOVE_ALL_DATA_AND_FEATURES || derelictRecoveredByPlayer
				|| (noRivalFleet && !derelict.isInCurrentLocation() && super.shouldRemoveIntel());
	}

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		super.buttonPressConfirmed(buttonId, ui);

		switch ((String)buttonId) {
			case "gotoDerelict": Util.teleportEntity(Global.getSector().getPlayerFleet(), derelict); break;
			case "gotoRival": Util.teleportEntity(Global.getSector().getPlayerFleet(), rivalFleet); break;
		}
	}

	@Override
	public void reportRemovedIntel() {
		try {
			super.reportRemovedIntel();

			setImportant(false);
			setNew(false);

			derelict.getMemoryWithoutUpdate().clear();

			sendRivalFleetHome();

			if (!derelictRecoveredByPlayer) RepRecord.deleteFor(ship);

			if (derelict != null) {
				Misc.makeUnimportant(derelict, "sun_sl_famous_derelict");
				derelict.getContainingLocation().removeEntity(derelict);
			}
		} catch (Exception e) { ModPlugin.reportCrash(e); }
	}

	@Override
	public String getSortString() {
		return "Famous Derelict" + timestamp;
	}

	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if(derelict == null) return null;

		switch(granularity) {
			case ENTITY: return derelict.getOrbitFocus();
			case SYSTEM: return derelict.getStarSystem().getCenter();
			case CONSTELATION: return map.getConstellationLabelEntity(derelict.getConstellation());
			default: return super.getMapLocation(map);
		}
	}
}