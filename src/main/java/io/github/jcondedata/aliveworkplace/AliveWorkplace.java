package io.github.jcondedata.aliveworkplace;

import io.github.jcondedata.aliveworkplace.build.BuilderEvents;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.command.WorkplaceCommand;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alive Workplace — villagers that do real jobs. Registers content and hooks; the loader's entrypoint
 * ({@code platform/fabric/AliveWorkplaceFabric}) calls {@link #init}. Nothing here may touch the loader or another mod
 * directly: that goes through {@link Platform} and {@code compat/}.
 * See ROADMAP.md for what is planned and CLAUDE.md for how the codebase is organised.
 */
public final class AliveWorkplace {
	public static final String MOD_ID = "aliveworkplace";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Starts the mod; {@code integrations} turns on other mods' integrations (only those installed). */
	public static void init(Runnable integrations) {
		WorkplaceConfig.loadAndApply(Platform.get().configDir());
		ModComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModVillagers.init();
		io.github.jcondedata.aliveworkplace.registry.ModEntities.init();
		ModAttachments.init();
		ModGameRules.init();
		ModTrades.init();

		io.github.jcondedata.aliveworkplace.hall.CaravanSights.init(); // 33.7; first, so a click on a carter or his llamas stops here
		io.github.jcondedata.aliveworkplace.story.SicknessStory.init(); // 31.18; before BuilderEvents, so a click on the healer is the story's first
		BuilderEvents.init();
		io.github.jcondedata.aliveworkplace.shop.ShopLedger.init();
		TableServer.init();
		io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.init(); // the Table as a collection (26.10)
		PreviewNetworking.init();
		BuilderStatusSync.init();
		io.github.jcondedata.aliveworkplace.world.VillageHouses.init();
		io.github.jcondedata.aliveworkplace.farm.Fields.init();
		io.github.jcondedata.aliveworkplace.fish.Fishers.init();
		io.github.jcondedata.aliveworkplace.travel.FerryRides.init();
		io.github.jcondedata.aliveworkplace.guard.Cavalry.init();
		io.github.jcondedata.aliveworkplace.guard.Relief.init();
		io.github.jcondedata.aliveworkplace.guard.Scouts.init(); // scouts sent to the lair, and what they bring home (32.12)
		io.github.jcondedata.aliveworkplace.mail.Mail.init();
		io.github.jcondedata.aliveworkplace.work.KeepLoaded.init();
		io.github.jcondedata.aliveworkplace.guard.GuardPartners.init();
		io.github.jcondedata.aliveworkplace.guard.Escorts.init();
		io.github.jcondedata.aliveworkplace.guard.WarParties.init(); // marching on the lair (32.13)
		io.github.jcondedata.aliveworkplace.guard.RaidDens.init(); // the raid dens a village knows (38.2)
		io.github.jcondedata.aliveworkplace.guard.DenWardens.init(); // guards posted at the dens (38.3)
		io.github.jcondedata.aliveworkplace.trainer.DenChallenges.init(); // village trainers challenge the dens (38.4)
		io.github.jcondedata.aliveworkplace.trainer.MegaBracelets.init(); // more Mega trainers, and their bracelets (38.5)
		io.github.jcondedata.aliveworkplace.jeweller.MegaStones.init(); // the Jeweller's Mega Stones and Key Stones (38.6)
		io.github.jcondedata.aliveworkplace.trainer.LeaderBracelets.init(); // a Mega Bracelet for beating a realm's Leaders (38.6)
		io.github.jcondedata.aliveworkplace.hall.Festivals.init();
		io.github.jcondedata.aliveworkplace.hall.Banquets.init();
		io.github.jcondedata.aliveworkplace.hall.Curfew.init();
		io.github.jcondedata.aliveworkplace.hall.Conscription.init();
		io.github.jcondedata.aliveworkplace.hall.Seasons.init();
		io.github.jcondedata.aliveworkplace.cup.CupThemes.init(); // 28.17: the Festival Cup's themes and its hall page
		io.github.jcondedata.aliveworkplace.cup.CupPage.init();
		io.github.jcondedata.aliveworkplace.cup.TypeChart.init(); // 28.18: the bouts' type chart, and the bouts at the ring
		io.github.jcondedata.aliveworkplace.cup.CupBouts.init();
		io.github.jcondedata.aliveworkplace.cup.CupMatches.init(); // 28.20
		io.github.jcondedata.aliveworkplace.cup.CupDays.init(); // 28.19: the Cup's day
		io.github.jcondedata.aliveworkplace.hall.HarvestIdols.init();
		io.github.jcondedata.aliveworkplace.people.Chatter.init();
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.init();
		io.github.jcondedata.aliveworkplace.city.StewardSafety.init(); // 27.19: after protection, so a refused break isn't marked
		io.github.jcondedata.aliveworkplace.city.CityPlans.init();
		io.github.jcondedata.aliveworkplace.hall.Edicts.init();
		io.github.jcondedata.aliveworkplace.hall.Guilds.init();
		io.github.jcondedata.aliveworkplace.colony.Colonies.init();
		io.github.jcondedata.aliveworkplace.realm.Realms.init();
		io.github.jcondedata.aliveworkplace.realm.Pacts.init();
		io.github.jcondedata.aliveworkplace.realm.Feuds.init();
		io.github.jcondedata.aliveworkplace.realm.Leaderboard.init();
		io.github.jcondedata.aliveworkplace.realm.RealmResearch.init();
		io.github.jcondedata.aliveworkplace.realm.RealmEdicts.init(); // 33.22: after the realm topics' effect types
		io.github.jcondedata.aliveworkplace.people.SocialClasses.init();
		io.github.jcondedata.aliveworkplace.people.ClassLook.init(); // dressed by class, in game (34.18)
		io.github.jcondedata.aliveworkplace.people.HomeGrades.init(); // 34.15: data files that grade homes
		io.github.jcondedata.aliveworkplace.hall.ClassesPage.init(); // the hall's Classes page (34.6)
		io.github.jcondedata.aliveworkplace.hall.Services.init(); // services nearby (34.3)
		io.github.jcondedata.aliveworkplace.people.Luxuries.init(); // luxuries from the village store (34.4)
		io.github.jcondedata.aliveworkplace.trade.TradeGoods.init(); // trade goods (33.2)
		io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes.init(); // the luxury workshops' recipes (34.5)
		Platform.get().onServerStarting(io.github.jcondedata.aliveworkplace.craft.Crafting::warm); // crafters' recipe index, before any tick (B101)
		Platform.get().onDataPacksReloaded(io.github.jcondedata.aliveworkplace.craft.Crafting::warm);
		io.github.jcondedata.aliveworkplace.people.Tonics.init();
		io.github.jcondedata.aliveworkplace.people.LifeStages.init(); // the Evergreen Charm (34.19a)
		io.github.jcondedata.aliveworkplace.city.StewardRules.init();
		io.github.jcondedata.aliveworkplace.ranch.PokemonChores.init();
		io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles.init();
		io.github.jcondedata.aliveworkplace.mechanic.Contraptions.init(); // contraptions from a blueprint (39.5)
		io.github.jcondedata.aliveworkplace.legend.Legends.init();
		io.github.jcondedata.aliveworkplace.wonder.Wonders.init(); // Wonders as data (35.2)
		io.github.jcondedata.aliveworkplace.wonder.WonderStages.init(); // a Wonder's stages, one after another (35.4)
		io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep.init(); // a finished Wonder's daily count and repairs (35.8)
		io.github.jcondedata.aliveworkplace.wonder.WondersPage.init(); // the hall's Wonders page (35.3)
		io.github.jcondedata.aliveworkplace.hall.FreeJobsPage.init(); // the hall's Free job blocks page (36.9)
		io.github.jcondedata.aliveworkplace.wonder.LighthousePowers.init(); // the Lighthouse's powers (35.12)
		io.github.jcondedata.aliveworkplace.wonder.LibraryPowers.init(); // the Great Library's second research tier (35.14)
		io.github.jcondedata.aliveworkplace.wonder.ColossusPlaque.init(); // the Colossus's plaque, filled in when finished (35.22)
		io.github.jcondedata.aliveworkplace.wonder.StadiumDays.init(); // the Stadium's Stadium Days (35.24)
		io.github.jcondedata.aliveworkplace.threat.Threats.init(); // raider cultures as data (32.2)
		io.github.jcondedata.aliveworkplace.threat.Disasters.init(); // disasters as data (32.15)
		io.github.jcondedata.aliveworkplace.people.RemedyItem.init(); // the Physician's Remedy given to a villager (32.18)
		io.github.jcondedata.aliveworkplace.threat.VillageFire.init(); // the village fire and the Firewarden (32.16)
		io.github.jcondedata.aliveworkplace.threat.Sieges.init(); // rams and gates (32.4)
		io.github.jcondedata.aliveworkplace.threat.Ladders.init(); // ladders over the walls (32.5)
		io.github.jcondedata.aliveworkplace.threat.SandRamps.init(); // sand ramps against the walls (32.9)
		io.github.jcondedata.aliveworkplace.threat.Risings.init(); // the Pharaoh's husks rise again (32.9)
		io.github.jcondedata.aliveworkplace.threat.Ships.init(); // the pirate ship: lairs on the sea, sinking (32.8)
		io.github.jcondedata.aliveworkplace.threat.Piglins.init(); // piglin raiders: never zombify, go for villagers (32.10)
		io.github.jcondedata.aliveworkplace.threat.Plunder.init(); // the tactic plunder: gold from the village's chests (32.10)
		io.github.jcondedata.aliveworkplace.threat.Covens.init(); // witch covens: the hex tactic, nightly hexes, the Coven Mother (32.11)
		io.github.jcondedata.aliveworkplace.guard.BattleStations.init(); // battle stations (32.6)
		io.github.jcondedata.aliveworkplace.story.QuestFiles.init(); // the quest engine's files (31.2)
		io.github.jcondedata.aliveworkplace.story.QuestTracker.init(); // tracked quests as boss bars, reach objectives (31.3)
		io.github.jcondedata.aliveworkplace.story.Friendship.init(); // friendship: favours, hits, the hearts look-up (31.5)
		io.github.jcondedata.aliveworkplace.story.Tastes.init(); // what villagers think of a gift, as data (31.6)
		io.github.jcondedata.aliveworkplace.story.Gifts.init(); // giving a Gift to a named villager (31.6)
		io.github.jcondedata.aliveworkplace.story.HeartEvents.init(); // what villagers tell their friends, as data (31.7)
		io.github.jcondedata.aliveworkplace.story.PersonalRequests.init(); // villagers ask their friends for help (31.9)
		io.github.jcondedata.aliveworkplace.story.Reputation.init(); // standing and titles: losses, raids, lairs, the title in chat (31.11)
		io.github.jcondedata.aliveworkplace.bounty.Bounties.init(); // the bounty board and bandit captains (31.13)
		io.github.jcondedata.aliveworkplace.story.Arcs.init(); // story arcs: their files, placements and mobs, talk (31.4)
		io.github.jcondedata.aliveworkplace.hall.Anthems.init(); // the anthem's player (29.19)
		io.github.jcondedata.aliveworkplace.legend.Gifted.init();
		io.github.jcondedata.aliveworkplace.research.ResearchTrees.init();
		io.github.jcondedata.aliveworkplace.work.PartnerShows.init();
		io.github.jcondedata.aliveworkplace.camp.CampCooks.init();
		io.github.jcondedata.aliveworkplace.craft.CuisineDishes.init(); // Cobblecuisine's dishes on the menus (38.8)
		io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.init();
		io.github.jcondedata.aliveworkplace.gem.GemGrowers.init();
		io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.init();
		io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture.init(); // ROADMAP 37.2: furniture spots
		io.github.jcondedata.aliveworkplace.furnish.FurnishingTable.init(); // ROADMAP 37.3: the furniture mods' pieces
		io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary.init(); // ROADMAP 37.12: the modded build library
		io.github.jcondedata.aliveworkplace.city.RoadStyles.init();
		io.github.jcondedata.aliveworkplace.city.WallKits.init();
		io.github.jcondedata.aliveworkplace.city.RenewalLists.init();
		integrations.run();
		io.github.jcondedata.aliveworkplace.build.MaterialFamilies.init();
		io.github.jcondedata.aliveworkplace.build.StallWatch.init();
		BlueprintOutline.init();
		WorkplaceCommand.init();
		io.github.jcondedata.aliveworkplace.command.Benchmark.init();
		io.github.jcondedata.aliveworkplace.command.Soak.init();
		io.github.jcondedata.aliveworkplace.command.CitySoak.init(); // 27.22
		io.github.jcondedata.aliveworkplace.command.SeasonSoak.init(); // 30.22
		io.github.jcondedata.aliveworkplace.command.Rehearsal.init(); // 34.23
		io.github.jcondedata.aliveworkplace.command.FurnishedPackTest.init(); // 37.11
		Platform.get().onServerStarting(server -> ClassWarmup.start()); // B103: no class loading stalls on day 1
		LOG.info("Alive Workplace ready — go hire a builder.");
	}

	private AliveWorkplace() {
	}
}
