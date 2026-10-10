package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only harness: stages three builders building the starter blueprints in a flat world, saves a
 * series of screenshots (stitched into a GIF by tools/screenshots/make_gif.py) and quits.
 * Only active with -Daliveworkplace.shots=true (the "screenshots" Gradle run).
 */
public class ScreenshotHarness implements ClientModInitializer {
	private static final int FRAME_EVERY = 30;
	private static final int GIVE_UP_AT = 16000;
	private static final Vec3 WIDE = new Vec3(0.5, -49, 23.5);

	private int tick;
	private int frame;
	private int doneAt = -1;
	private final AtomicBoolean allDone = new AtomicBoolean(false);
	private final List<Villager> builders = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("aliveworkplace.shots")) {
			return;
		}
		Showcase.install();
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private final JobScenes jobScenes = new JobScenes();
	private final WordsScene words = new WordsScene();
	private final CityPlanScene cityPlan = new CityPlanScene();
	private final ColonyCharterScene colonyCharter = new ColonyCharterScene();
	private final ColonyDepartureScene colonyDeparture = new ColonyDepartureScene();
	private final LegendsHallScene legendsHall = new LegendsHallScene();
	private final CupPageScene cupPage = new CupPageScene();
	private final CupBoutScene cupBout = new CupBoutScene();
	private final CupMatchScene cupMatch = new CupMatchScene();
	private final CupDayScene cupDay = new CupDayScene();
	private final CupChampionsScene cupChampions = new CupChampionsScene();
	private final CupThemesScene cupThemes = new CupThemesScene();
	private final GiftedScene gifted = new GiftedScene();
	private final WorkHornScene workHorn = new WorkHornScene();
	private final GuildhallScene guildhall = new GuildhallScene();
	private final GuildsScene guilds = new GuildsScene();
	private final CradleScene cradle = new CradleScene();
	private final HarvestIdolScene harvestIdol = new HarvestIdolScene();
	private final VillageBannerScene villageBanner = new VillageBannerScene();
	private final TonicsScene tonics = new TonicsScene();
	private final AgelessElderScene agelessElder = new AgelessElderScene();
	private final EldersScene elders = new EldersScene();
	private final VillageTalkScene villageTalk = new VillageTalkScene();
	private final CityPlanGroundScene cityPlanGround = new CityPlanGroundScene();
	private final PartnersScene partners = new PartnersScene();
	private final PartnersLandScene partnersLand = new PartnersLandScene();
	private final PokemonCenterScene pokemonCenter = new PokemonCenterScene();
	private final ArenaScene arena = new ArenaScene();
	private final PokemonBuildsScene pokemonBuilds = new PokemonBuildsScene();
	private final LuxuryWorkshopsScene luxuryWorkshops = new LuxuryWorkshopsScene();
	private final VillageHabitatScene villageHabitat = new VillageHabitatScene();
	private final PartnersForgeScene partnersForge = new PartnersForgeScene();
	private final PartnersAllScene partnersAll = new PartnersAllScene();
	private final PaceScene pace = new PaceScene();
	private final StewardRulesScene stewardRules = new StewardRulesScene();
	private final StewardDeskScene stewardDesk = new StewardDeskScene();
	private final StewardSafetyScene stewardSafety = new StewardSafetyScene();
	private final OldHousesScene oldHouses = new OldHousesScene();
	private final RenewalScene renewal = new RenewalScene();
	private final StewardJobsScene stewardJobs = new StewardJobsScene();
	private final StewardHomesScene stewardHomes = new StewardHomesScene();
	private final StewardCivicScene stewardCivic = new StewardCivicScene();
	private final CityTimelapseScene cityTimelapse = new CityTimelapseScene();
	private final PieceLookScene pieceLook = new PieceLookScene();

	private void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
			return;
		}
		if (JobScenes.has(System.getProperty("aliveworkplace.scene"))) {
			jobScenes.tick(mc, mc.getSingleplayerServer(), System.getProperty("aliveworkplace.scene"));
			return;
		}
		if ("table".equals(System.getProperty("aliveworkplace.scene"))) {
			tableScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("quarry".equals(System.getProperty("aliveworkplace.scene"))) {
			quarryScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("staff".equals(System.getProperty("aliveworkplace.scene"))) {
			staffScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("guide".equals(System.getProperty("aliveworkplace.scene"))) {
			guideScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("steward_rules".equals(System.getProperty("aliveworkplace.scene"))) {
			stewardRules.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("steward_safety".equals(System.getProperty("aliveworkplace.scene"))) {
			stewardSafety.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("old_houses".equals(System.getProperty("aliveworkplace.scene"))) {
			oldHouses.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("renewal".equals(System.getProperty("aliveworkplace.scene"))) {
			renewal.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("steward_desk".equals(System.getProperty("aliveworkplace.scene"))) {
			stewardDesk.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("steward_jobs".equals(System.getProperty("aliveworkplace.scene"))) {
			stewardJobs.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("steward_homes".equals(System.getProperty("aliveworkplace.scene"))) {
			stewardHomes.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("city_timelapse".equals(System.getProperty("aliveworkplace.scene"))) {
			cityTimelapse.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("steward_civic".equals(System.getProperty("aliveworkplace.scene"))) {
			stewardCivic.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("piece_look".equals(System.getProperty("aliveworkplace.scene"))) {
			pieceLook.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("partners_all".equals(System.getProperty("aliveworkplace.scene"))) {
			partnersAll.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("partners_forge".equals(System.getProperty("aliveworkplace.scene"))) {
			partnersForge.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("pokemon_center".equals(System.getProperty("aliveworkplace.scene"))) {
			pokemonCenter.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("arena".equals(System.getProperty("aliveworkplace.scene"))) {
			arena.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("pokemon_builds".equals(System.getProperty("aliveworkplace.scene"))) {
			pokemonBuilds.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("luxury_workshops".equals(System.getProperty("aliveworkplace.scene"))) {
			luxuryWorkshops.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("village_habitat".equals(System.getProperty("aliveworkplace.scene"))) {
			villageHabitat.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("partners_land".equals(System.getProperty("aliveworkplace.scene"))) {
			partnersLand.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("pace".equals(System.getProperty("aliveworkplace.scene"))) {
			pace.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("partners_engine".equals(System.getProperty("aliveworkplace.scene"))) {
			partners.tick(mc, mc.getSingleplayerServer());
			return;
		}
		if ("words".equals(System.getProperty("aliveworkplace.scene"))) {
			words.tick(mc);
			return;
		}
		if ("city_plan".equals(System.getProperty("aliveworkplace.scene"))) {
			cityPlan.tick(mc);
			return;
		}
		if ("colony_charter".equals(System.getProperty("aliveworkplace.scene"))) {
			colonyCharter.tick(mc);
			return;
		}
		if ("colony_departure".equals(System.getProperty("aliveworkplace.scene"))) {
			colonyDeparture.tick(mc);
			return;
		}
		if ("legends_hall".equals(System.getProperty("aliveworkplace.scene"))) {
			legendsHall.tick(mc);
			return;
		}
		if ("cup_page".equals(System.getProperty("aliveworkplace.scene"))) {
			cupPage.tick(mc);
			return;
		}
		if ("cup_bout".equals(System.getProperty("aliveworkplace.scene"))) {
			cupBout.tick(mc);
			return;
		}
		if ("cup_match".equals(System.getProperty("aliveworkplace.scene"))) {
			cupMatch.tick(mc);
			return;
		}
		if ("cup_day".equals(System.getProperty("aliveworkplace.scene"))) {
			cupDay.tick(mc);
			return;
		}
		if ("cup_champions".equals(System.getProperty("aliveworkplace.scene"))) {
			cupChampions.tick(mc);
			return;
		}
		if ("cup_themes".equals(System.getProperty("aliveworkplace.scene"))) {
			cupThemes.tick(mc);
			return;
		}
		if ("gifted".equals(System.getProperty("aliveworkplace.scene"))) {
			gifted.tick(mc);
			return;
		}
		if ("work_horn".equals(System.getProperty("aliveworkplace.scene"))) {
			workHorn.tick(mc);
			return;
		}
		if ("guildhall".equals(System.getProperty("aliveworkplace.scene"))) {
			guildhall.tick(mc);
			return;
		}
		if ("guilds".equals(System.getProperty("aliveworkplace.scene"))) {
			guilds.tick(mc);
			return;
		}
		if ("ageless_elder".equals(System.getProperty("aliveworkplace.scene"))) {
			agelessElder.tick(mc);
			return;
		}
		if ("elders".equals(System.getProperty("aliveworkplace.scene"))) {
			elders.tick(mc);
			return;
		}
		if ("cradle".equals(System.getProperty("aliveworkplace.scene"))) {
			cradle.tick(mc);
			return;
		}
		if ("harvest_idol".equals(System.getProperty("aliveworkplace.scene"))) {
			harvestIdol.tick(mc);
			return;
		}
		if ("village_banner".equals(System.getProperty("aliveworkplace.scene"))) {
			villageBanner.tick(mc);
			return;
		}
		if ("tonics".equals(System.getProperty("aliveworkplace.scene"))) {
			tonics.tick(mc);
			return;
		}
		if ("village_talk".equals(System.getProperty("aliveworkplace.scene"))) {
			villageTalk.tick(mc);
			return;
		}
		if ("city_plan_ground".equals(System.getProperty("aliveworkplace.scene"))) {
			cityPlanGround.tick(mc);
			return;
		}
		if ("config".equals(System.getProperty("aliveworkplace.scene"))) {
			configScene(mc);
			return;
		}
		if ("guard".equals(System.getProperty("aliveworkplace.scene")) || "guard_pokemon".equals(System.getProperty("aliveworkplace.scene"))) {
			guardScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("mail".equals(System.getProperty("aliveworkplace.scene"))) {
			mailScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("farm".equals(System.getProperty("aliveworkplace.scene"))) {
			farmScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("tutor".equals(System.getProperty("aliveworkplace.scene"))) {
			tutorScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("trader".equals(System.getProperty("aliveworkplace.scene"))) {
			traderScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("missing".equals(System.getProperty("aliveworkplace.scene"))) {
			missingScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("battle".equals(System.getProperty("aliveworkplace.scene"))) {
			battleScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("smith".equals(System.getProperty("aliveworkplace.scene"))) {
			smithScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("chef".equals(System.getProperty("aliveworkplace.scene"))) {
			chefScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("carpenter".equals(System.getProperty("aliveworkplace.scene"))) {
			carpenterScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("camp".equals(System.getProperty("aliveworkplace.scene"))) {
			campScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("hall".equals(System.getProperty("aliveworkplace.scene"))) {
			hallScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("porter".equals(System.getProperty("aliveworkplace.scene"))) {
			porterScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("shop".equals(System.getProperty("aliveworkplace.scene"))) {
			shopScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("orchard".equals(System.getProperty("aliveworkplace.scene"))) {
			orchardScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("forest".equals(System.getProperty("aliveworkplace.scene"))) {
			forestScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("soak".equals(System.getProperty("aliveworkplace.scene"))) {
			soakScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("village".equals(System.getProperty("aliveworkplace.scene"))) {
			villageScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("gallery".equals(System.getProperty("aliveworkplace.scene")) || "decor".equals(System.getProperty("aliveworkplace.scene"))
			|| "styles".equals(System.getProperty("aliveworkplace.scene")) || "defences".equals(System.getProperty("aliveworkplace.scene"))
			|| "workshops".equals(System.getProperty("aliveworkplace.scene")) || "workplaces".equals(System.getProperty("aliveworkplace.scene"))) {
			galleryScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("fish".equals(System.getProperty("aliveworkplace.scene"))) {
			fishScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("extras".equals(System.getProperty("aliveworkplace.scene"))) {
			extrasScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("preview".equals(System.getProperty("aliveworkplace.scene"))) {
			previewScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("placing".equals(System.getProperty("aliveworkplace.scene"))) {
			placingScene(mc, mc.getSingleplayerServer());
			return;
		}
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.graphicsMode().set(GraphicsStatus.FAST);
			mc.options.framerateLimit().set(15);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 120) {
			shot(mc, "01_start");
		}
		if (tick == 140) {
			server.execute(() -> closeUp(server));
		}
		if (tick == 260) {
			shot(mc, "02_builder_closeup");
			server.execute(() -> camera(server, WIDE, 180, 22));
		}
		if (tick > 320 && doneAt < 0 && tick % FRAME_EVERY == 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(BuildSiteManager.get(server.overworld()).all().isEmpty()));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0) {
			int t = tick - doneAt;
			if (t == 40) {
				server.execute(() -> {
					int finished = BuildSiteManager.get(server.overworld()).finishedIn(server.overworld()).size();
					Showcase.check(finished >= 3, "the builders finished all three builds (" + finished + " finished)");
				});
				shot(mc, "03_finished_wide");
				server.execute(() -> camera(server, new Vec3(0.5, -56, 11.5), 180, 12));
			} else if (t == 140) {
				shot(mc, "04_cottage");
				server.execute(() -> camera(server, new Vec3(-12, -56, 9.5), 180, 10));
			} else if (t == 240) {
				shot(mc, "05_market_stall");
				server.execute(() -> camera(server, new Vec3(12.5, -52, 13.5), 180, 20));
			} else if (t == 340) {
				shot(mc, "06_lookout_tower");
			} else if (t == 360) {
				mc.stop();
			}
		}
		if (tick >= GIVE_UP_AT) {
			giveUp(mc, "the builders finished all three builds");
		}
	}

	// --- Blueprint Table scene ----------------------------------------------------------------

	private static final BlockPos TABLE = new BlockPos(0, -60, -3);
	private volatile int libraryBefore;

	private void tableScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				libraryBefore = BlueprintLibrary.list(server, false).size();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.setDayTime(6000);
				level.setBlockAndUpdate(TABLE, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.setGameMode(GameType.CREATIVE);
				player.teleportTo(level, 0.5, -60, 0.5, 180, 30);
			});
		}
		if (tick == 80) {
			server.execute(() -> io.github.jcondedata.aliveworkplace.table.TableServer.open(server.getPlayerList().getPlayers().get(0), TABLE));
		}
		if (tick == 110 && mc.screen instanceof io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen screen) {
			screen.select(StarterBlueprints.STARTER_COTTAGE.id());
		}
		if (tick == 160) {
			Showcase.check(mc.screen instanceof io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen, "the Blueprint Table screen opened");
			shot(mc, "10_table_library");
			if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen screen) {
				screen.showFiles();
			}
		}
		if (tick == 200) {
			shot(mc, "11_table_upload");
			if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen screen) {
				screen.upload();
			}
		}
		if (tick == 280) {
			shot(mc, "12_table_uploaded");
			server.execute(() -> {
				int now = BlueprintLibrary.list(server, false).size();
				Showcase.check(now > libraryBefore, "a .litematic file uploaded into the library (" + libraryBefore + " -> " + now + " blueprints)");
			});
		}
		if (tick == 300) {
			if (mc.screen == null) {
				shot(mc, "99_no_screen");
			}
			mc.stop();
		}
	}

	// --- See-through preview + status above the builder's head -----------------------------------

	private void previewScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.graphicsMode().set(GraphicsStatus.FAST);
			mc.options.framerateLimit().set(15);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				GameRules rules = level.getGameRules();
				rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
				rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				rules.getRule(ModGameRules.BUILD_DELAY).set(4, server);
				level.setDayTime(2500);
				BlueprintData.Placement placement = site(level, StarterBlueprints.STARTER_COTTAGE, new BlockPos(0, -60, 0));
				// The player holds the same blueprint: ghosts show what is still to be built.
				Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
				ItemStack held = BlueprintItem.create(blueprint.id(), blueprint.size());
				held.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
					BlueprintItem.data(held).orElseThrow().withPlacement(java.util.Optional.of(placement)));
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().setItem(player.getInventory().selected, held);
				hover(player, new Vec3(7.5, -55, 9.5), 145, 22);
			});
		}
		if (tick == 140) {
			shot(mc, "20_preview_start");
		}
		if (tick == 900) {
			shot(mc, "21_preview_half_built");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				float progress = BuildSiteManager.get(level).all().stream().map(site -> site.progress(site.plan(level))).max(Float::compare).orElse(1f);
				Showcase.check(progress > 0.05f, String.format("the builder made progress on the previewed site (%.0f%% built)", progress * 100));
			});
		}
		if (tick == 920) {
			server.execute(() -> {
				if (builders.isEmpty()) {
					return;
				}
				Villager v = builders.get(0);
				v.setNoAi(true);
				// 23.3: empty the barrels so the overhead names what the cottage is now short of, under where it takes from.
				for (int b = 0; b < 6; b++) {
					if (server.overworld().getBlockEntity(new BlockPos(4 + b, -60, 4)) instanceof BaseContainerBlockEntity barrel) {
						barrel.clearContent();
					}
				}
				// The close-up shows the overhead, not the ghost: put the blueprint away.
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				p.getInventory().setItem(p.getInventory().selected, ItemStack.EMPTY);
				Vec3 eye = v.getEyePosition();
				Vec3 cam = eye.add(3.3, 1.1, 4.6);
				Vec3 d = eye.add(0, 0.8, 0).subtract(cam);
				float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
				hover(server.getPlayerList().getPlayers().get(0), cam, yaw, pitch);
			});
		}
		if (tick == 1050) { // the materials lines are worked out every 100 ticks
			shot(mc, "22_status_closeup");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Villager v = builders.isEmpty() ? null : builders.get(0);
				var site = v == null ? null : BuildSiteManager.get(level).all().stream().filter(x -> v.getUUID().equals(x.builder())).findFirst().orElse(null);
				var status = site == null ? null : io.github.jcondedata.aliveworkplace.build.BuilderStatusSync.status(level, site, v);
				List<String> more = status == null ? List.of() : status.more().stream().map(Component::getString).toList();
				Showcase.check(more.size() == 2 && more.get(0).startsWith("Short of: ") && more.get(1).startsWith("Takes from: "),
					"the builder's overhead names what the build is short of and where it takes from: " + more);
			});
		}
		if (tick == 1070) {
			mc.stop();
		}
	}

	// --- Placing a build (23.8): place, turn, mirror, hand over, cancel, move onto a slope -------------

	private BlueprintData.Placement placingAt;
	private Villager placingBuilder;
	private io.github.jcondedata.aliveworkplace.build.BuildSite placingSite;
	private int placingDone;

	/** The blueprint in the player's hand, placed at {@code placement} (the ghost shows it there). */
	private static void holdPlaced(ServerPlayer player, Blueprint blueprint, BlueprintData.Placement placement, boolean mirrored) {
		ItemStack held = BlueprintItem.create(blueprint.id(), blueprint.size());
		held.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
			BlueprintItem.data(held).orElseThrow().withMirrored(mirrored).withPlacement(java.util.Optional.of(placement)));
		player.getInventory().setItem(player.getInventory().selected, held);
	}

	private void placingScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.graphicsMode().set(GraphicsStatus.FAST);
			mc.options.framerateLimit().set(15);
			mc.options.hideGui = true;
		}
		StarterBlueprints.Entry entry = StarterBlueprints.STARTER_COTTAGE;
		BlockPos spotA = new BlockPos(0, -60, 0);
		BlockPos spotB = new BlockPos(0, -60, 22);
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				GameRules rules = level.getGameRules();
				rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
				rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				rules.getRule(ModGameRules.BUILD_DELAY).set(3, server);
				level.setDayTime(2500);
				// The second spot is a slope: up a block every two blocks east, so the build there needs a foundation.
				for (int x = -8; x <= 10; x++) {
					int rise = Math.max(0, Math.min(5, (x + 8) / 3));
					for (int z = spotB.getZ() - 4; z <= spotB.getZ() + 12; z++) {
						for (int y = 0; y < rise; y++) {
							level.setBlock(new BlockPos(x, -60 + y, z), (y == rise - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
						}
					}
				}
				Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
				placingAt = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), spotA, BlueprintItem.rotationFacing(Direction.SOUTH));
				holdPlaced(server.getPlayerList().getPlayers().get(0), blueprint, placingAt, false);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(19.5, -46, -15.5), 50, 28);
			});
		}
		if (tick == 120) {
			shot(mc, "10_placed");
		}
		if (tick == 130) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
				// Sneak-right-clicking the ground again turns it a quarter, around the middle of its front.
				placingAt = BlueprintItem.placementAt(placingAt.dimension(), blueprint.size(), BlueprintItem.anchorWorld(placingAt, blueprint.size()),
					placingAt.rotation().getRotated(Rotation.CLOCKWISE_90));
				holdPlaced(server.getPlayerList().getPlayers().get(0), blueprint, placingAt, false);
			});
		}
		if (tick == 220) {
			shot(mc, "11_turned");
		}
		if (tick == 230) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ItemStack held = player.getMainHandItem();
				BlueprintData flipped = BlueprintItem.mirrored(BlueprintItem.data(held).orElseThrow(), true);
				held.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, flipped);
				placingAt = flipped.placement().orElseThrow();
				Showcase.check(placingAt.mirror() == Mirror.FRONT_BACK, "the placed blueprint flipped where it stands");
			});
		}
		if (tick == 320) {
			shot(mc, "12_mirrored");
		}
		if (tick == 330) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
				// Twice the materials, in barrels by a bench: enough to start here and build it all again on the slope.
				BlockPos bench = new BlockPos(9, -60, 12);
				level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
				List<ItemStack> stock = new ArrayList<>();
				for (int copy = 0; copy < 3; copy++) {
					for (Map.Entry<Item, Integer> e : BuildPlan.create(blueprint, placingAt).materials().entrySet()) {
						for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
							stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
						}
					}
				}
				stock.add(new ItemStack(Items.DIRT, 64));
				stock.add(new ItemStack(Items.DIRT, 64));
				stock.add(new ItemStack(Items.STONE, 64));
				stock.add(new ItemStack(Items.COBBLESTONE, 64));
				for (int b = 0; b * 27 < stock.size(); b++) {
					BlockPos barrelPos = bench.offset(1 + b, 0, 0);
					level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
					BaseContainerBlockEntity barrel = (BaseContainerBlockEntity) level.getBlockEntity(barrelPos);
					for (int slot = 0; slot < 27 && b * 27 + slot < stock.size(); slot++) {
						barrel.setItem(slot, stock.get(b * 27 + slot));
					}
				}
				placingBuilder = EntityType.VILLAGER.spawn(level, bench.offset(0, 0, -2), MobSpawnType.COMMAND);
				Builders.employ(level, placingBuilder, bench);
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				placingSite = Builders.start(level, placingBuilder, player, entry.id(), placingAt);
				builders.add(placingBuilder);
			});
		}
		if (tick == 1100) {
			shot(mc, "13_building");
		}
		if (tick == 1110) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
				int placed = placingSite.placed();
				Builders.cancel(level, placingSite);
				// The blueprint comes back still placed: hold it, and the ghost shows what is left to build here.
				ItemStack back = ItemStack.EMPTY;
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					if (player.getInventory().getItem(i).is(io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT)) {
						back = player.getInventory().getItem(i);
						player.getInventory().setItem(i, ItemStack.EMPTY);
					}
				}
				boolean placedThere = BlueprintItem.data(back).flatMap(BlueprintData::placement).filter(placingAt::equals).isPresent();
				player.getInventory().setItem(player.getInventory().selected, back);
				Showcase.check(placed > 10 && placedThere
						&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(placingBuilder).isEmpty(),
					"cancelled after " + placed + " blocks: the blueprint came back still placed and the builder carries nothing");
			});
		}
		if (tick == 1200) {
			shot(mc, "14_cancelled");
		}
		if (tick == 1210) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
				// Clicking the ground somewhere else moves it: onto the slope, facing south.
				BlockPos ground = spotB;
				while (!level.getBlockState(ground).isAir()) {
					ground = ground.above();
				}
				placingAt = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), ground, BlueprintItem.rotationFacing(Direction.SOUTH));
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				holdPlaced(player, blueprint, placingAt, false);
				hover(player, new Vec3(22.5, -46, 8.5), 62, 26);
			});
		}
		if (tick == 1300) {
			shot(mc, "15_moved_to_slope");
		}
		if (tick == 1310) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				placingSite = Builders.start(level, placingBuilder, server.getPlayerList().getPlayers().get(0), entry.id(), placingAt);
			});
		}
		if (tick > 1400 && tick % 100 == 0 && placingDone == 0 && placingSite != null
				&& (placingSite.stage().ordinal() > BuildPlan.Stage.FOUNDATION.ordinal() || tick >= 6000)) {
			placingDone = tick + 400; // a little of the walls on top of the foundation
		}
		if (placingDone > 0 && tick == placingDone) {
			shot(mc, "16_slope_building");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				BuildPlan plan = placingSite.plan(level);
				int foundation = plan == null ? 0 : plan.steps(BuildPlan.Stage.FOUNDATION).size();
				Showcase.check(foundation > 0 && placingSite.stage().ordinal() > BuildPlan.Stage.FOUNDATION.ordinal(),
					"on the slope the builder filled a foundation (" + foundation + " blocks) and went on to the walls (stage " + placingSite.stage() + ")");
			});
		}
		if (placingDone > 0 && tick == placingDone + 20) {
			mc.stop();
		}
	}

	// --- Quarry: a miner digs a block of stone and ore out, layer by layer ------------------------

	private io.github.jcondedata.aliveworkplace.mine.QuarrySite quarry;

	private void quarryScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, server);
				level.setDayTime(2500);
				java.util.Random rnd = new java.util.Random(4);
				BlockPos min = new BlockPos(-3, -60, -12);
				BlockPos max = new BlockPos(4, -55, -5);
				// A low hill around the quarry, so the pit is cut into the ground (and the stairs down its walls show).
				for (BlockPos p : BlockPos.betweenClosed(min.offset(-3, 0, -3), max.offset(3, 0, 3))) {
					level.setBlock(p, (p.getY() == max.getY() ? Blocks.GRASS_BLOCK : p.getY() >= max.getY() - 2 ? Blocks.DIRT : Blocks.STONE)
						.defaultBlockState(), 2);
				}
				for (BlockPos p : BlockPos.betweenClosed(min, max)) {
					int r = rnd.nextInt(100);
					level.setBlock(p, (r < 4 ? Blocks.COAL_ORE : r < 6 ? Blocks.IRON_ORE : r < 7 ? Blocks.COPPER_ORE : r < 30 ? Blocks.ANDESITE : Blocks.STONE)
						.defaultBlockState(), 2);
				}
				BlockPos bench = new BlockPos(0, -60, 2);
				level.setBlockAndUpdate(bench, Blocks.BLAST_FURNACE.defaultBlockState());
				level.setBlockAndUpdate(bench.east(), Blocks.CHEST.defaultBlockState());
				level.setBlockAndUpdate(bench.east(2), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(bench.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
				chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.TORCH, 16));
				Villager miner = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, miner, bench);
				quarry = io.github.jcondedata.aliveworkplace.mine.Miners.start(level, miner, server.getPlayerList().getPlayers().get(0),
					net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(min, max), 6);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(-8.5, -47, -18.5), -45, 38);
			});
		}
		if (tick > 60 && tick % 60 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(quarry != null && io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(server.overworld()).get(quarry.id()) == null));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick == doneAt + 30) {
			server.execute(() -> {
				int air = 0;
				int all = 0;
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(-3, -60, -12), new BlockPos(4, -55, -5))) {
					all++;
					air += server.overworld().getBlockState(p).isAir() ? 1 : 0;
				}
				Showcase.check(air * 10 >= all * 6, "the miner dug out the quarry (" + air + " of " + all + " blocks gone)");
			});
		}
		if (doneAt > 0 && tick == doneAt + 40) {
			shot(mc, "50_quarry_done");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			giveUp(mc, "the miner dug out the whole quarry");
		}
	}

	// --- Smith: a Ball Smith at the Ball Workbench, next to an Orchard Keeper's basket ----------------------

	private void smithScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doPokemonSpawning false");
				level.setDayTime(2500);
				BlockPos bench = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(bench, Blocks.SMITHING_TABLE.defaultBlockState());
				level.setBlockAndUpdate(bench.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(bench.east());
				chest.setItem(0, new ItemStack(com.cobblemon.mod.common.CobblemonItems.RED_APRICORN, 32));
				chest.setItem(1, new ItemStack(com.cobblemon.mod.common.CobblemonItems.BLUE_APRICORN, 16));
				chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 8));
				chest.setItem(3, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));
				Villager smith = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				worker = smith;
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, smith, bench,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.TOOLSMITH, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BALL_SMITH);
				BlockPos basket = new BlockPos(-3, -60, 0);
				level.setBlockAndUpdate(basket, Blocks.COMPOSTER.defaultBlockState());
				Villager keeper = EntityType.VILLAGER.spawn(level, basket.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, keeper, basket,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.ORCHARD_KEEPER);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(-0.5, -58.3, 5.5), 180, 12);
			});
		}
		if (tick == 330) {
			shot(mc, "01_smith_working");
		}
		if (tick > 330 && tick % 20 == 0 && doneAt < 0) {
			server.execute(() -> allDone.set(worker != null && io.github.jcondedata.aliveworkplace.registry.ModAttachments.BALLS_MADE.getOrElse(worker, 0) >= 1));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick == doneAt + 30) {
			shot(mc, "02_smith_done");
			Showcase.check(true, "the ball smith made balls from the apricorns");
			mc.stop();
		}
		if (tick >= 3000 && doneAt < 0) {
			giveUp(mc, "the ball smith made balls from the apricorns");
		}
	}

	/** The worker a one-worker scene checks on (the smith, the chef, the carpenter, the fisher). */
	private volatile Villager worker;

	// --- Chef: cooking at the Kitchen Stove -----------------------------------------------------------

	private void chefScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos stove = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(stove, Blocks.SMOKER.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(stove.east(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(stove.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(stove.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.WHEAT, 24));
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.BEEF, 8));
				chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.POTATO, 8));
				chest.setItem(3, new ItemStack(net.minecraft.world.item.Items.COD, 6));
				Villager chef = EntityType.VILLAGER.spawn(level, stove.south(), MobSpawnType.COMMAND);
				worker = chef;
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, chef, stove,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.BUTCHER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHEF);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -58.3, 5.0), 180, 14);
			});
		}
		if (tick >= 100 && tick % 50 == 0 && (tick <= 700 || doneAt < 0)) {
			shot(mc, String.format("%02d_chef", tick / 50));
			server.execute(() -> allDone.set(worker != null && io.github.jcondedata.aliveworkplace.registry.ModAttachments.ITEMS_CRAFTED.getOrElse(worker, 0) >= 1));
			if (allDone.get() && doneAt < 0) {
				doneAt = tick;
			}
		}
		if (tick >= 710 && doneAt > 0 && tick >= doneAt + 60) {
			Showcase.check(true, "the chef cooked food into the chest");
			mc.stop();
		}
		if (tick >= 3000 && doneAt < 0) {
			giveUp(mc, "the chef cooked food into the chest");
		}
	}

	// --- Carpenter: making what a builder is waiting for ----------------------------------------------

	private void carpenterScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos bench = new BlockPos(-4, -60, -2);
				level.setBlockAndUpdate(bench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				level.setBlockAndUpdate(bench.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(bench.west());
				// Everything for a Market Stall but the woodwork: spruce logs instead.
				Object[][] stock = {{net.minecraft.world.item.Items.SPRUCE_LOG, 20}, {net.minecraft.world.item.Items.MELON, 1}, {net.minecraft.world.item.Items.PUMPKIN, 1},
					{net.minecraft.world.item.Items.LANTERN, 1}, {net.minecraft.world.item.Items.HAY_BLOCK, 2}, {net.minecraft.world.item.Items.RED_WOOL, 20},
					{net.minecraft.world.item.Items.WHITE_WOOL, 15}};
				for (int i = 0; i < stock.length; i++) {
					chest.setItem(i, new ItemStack((net.minecraft.world.item.Item) stock[i][0], (Integer) stock[i][1]));
				}
				Villager builder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				Builders.employ(level, builder, bench);
				Builders.start(level, builder, null, io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.MARKET_STALL.id(),
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), new BlockPos(-4, -60, -12),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE));
				BlockPos carpenters = new BlockPos(3, -60, -2);
				level.setBlockAndUpdate(carpenters, Blocks.CRAFTING_TABLE.defaultBlockState());
				Villager carpenter = EntityType.VILLAGER.spawn(level, carpenters.south(), MobSpawnType.COMMAND);
				worker = carpenter;
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, carpenter, carpenters,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.CRAFTING_TABLE_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CARPENTER);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(-0.5, -54.5, 5.5), 180, 32);
			});
		}
		if (tick >= 60 && tick <= 1500 && tick % 60 == 0) {
			shot(mc, String.format("%02d_carpenter", tick / 60));
		}
		if (tick == 1510) {
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(3.5, -58.3, 2.5), 180, 20));
		}
		if (tick == 1550) {
			server.execute(() -> {
				int made = io.github.jcondedata.aliveworkplace.registry.ModAttachments.ITEMS_CRAFTED.getOrElse(worker, 0);
				Showcase.check(made >= 1, "the carpenter made woodwork for the builder (" + made + " pieces)");
			});
		}
		if (tick == 1560) {
			shot(mc, "30_carpenter_closeup");
			mc.stop();
		}
	}

	// --- Village Hall: the village at a glance ----------------------------------------------------------

	// --- Camp: a Settler's Wagon used on open ground --------------------------------------------------

	private void campScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(12600);
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				hover(player, new Vec3(0.5, -57.5, -6.5), 0, 20);
				int settlers = io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem.makeCamp(level, player, new BlockPos(0, -60, 0)).size();
				Showcase.check(settlers > 0, "the Settler's Wagon set up a camp with " + settlers + " settlers");
			});
		}
		if (tick == 60) {
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0), new Vec3(-7.5, -55.5, -6.5), new Vec3(0.5, -59, 4.5)));
		}
		if (tick == 140) {
			shot(mc, "01_camp");
		}
		if (tick == 150) {
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0), new Vec3(8.5, -56.5, 12.5), new Vec3(0.5, -59, 4.5)));
		}
		if (tick == 220) {
			shot(mc, "02_camp_back");
			mc.stop();
		}
	}

	private void hallScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos hall = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				// A builder waiting for materials, a lumberjack without an axe, a porter with food in the store, a guard, a
				// rancher, a florist, one villager without a job and a child; three beds.
				BlockPos builderBench = new BlockPos(-8, -60, -6);
				level.setBlockAndUpdate(builderBench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				Villager builder = EntityType.VILLAGER.spawn(level, builderBench.south(), MobSpawnType.COMMAND);
				Builders.employ(level, builder, builderBench);
				Builders.start(level, builder, null, StarterBlueprints.MARKET_STALL.id(),
					new BlueprintData.Placement(level.dimension().location(), new BlockPos(-18, -60, -16),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE));
				BlockPos chopping = new BlockPos(8, -60, -6);
				level.setBlockAndUpdate(chopping, Blocks.FLETCHING_TABLE.defaultBlockState());
				level.setBlockAndUpdate(chopping.east(), Blocks.CHEST.defaultBlockState());
				Villager lumberjack = EntityType.VILLAGER.spawn(level, chopping.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, lumberjack, chopping,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.FLETCHER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.LUMBERJACK);
				BlockPos storehouse = new BlockPos(10, -60, 8);
				level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState());
				level.setBlockAndUpdate(storehouse.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity store = (BaseContainerBlockEntity) level.getBlockEntity(storehouse.east());
				store.setItem(0, new ItemStack(net.minecraft.world.item.Items.BREAD, 32));
				store.setItem(1, new ItemStack(net.minecraft.world.item.Items.BAKED_POTATO, 18));
				store.setItem(2, new ItemStack(net.minecraft.world.item.Items.APPLE, 9));
				Villager porter = EntityType.VILLAGER.spawn(level, storehouse.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.store.Porters.employ(level, porter, storehouse);
				employ(level, new BlockPos(-10, -60, 8), Blocks.GRINDSTONE, net.minecraft.world.entity.ai.village.poi.PoiTypes.WEAPONSMITH,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
				employ(level, new BlockPos(-4, -60, 14), Blocks.SMOKER, net.minecraft.world.entity.ai.village.poi.PoiTypes.BUTCHER,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.RANCHER);
				employ(level, new BlockPos(5, -60, 14), Blocks.COMPOSTER, net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.FLORIST);
				EntityType.VILLAGER.spawn(level, new BlockPos(2, -60, 6), MobSpawnType.COMMAND);
				Villager child = EntityType.VILLAGER.spawn(level, new BlockPos(-2, -60, 6), MobSpawnType.COMMAND);
				child.setAge(-24000);
				for (int x = -3; x <= 1; x += 2) {
					level.setBlockAndUpdate(new BlockPos(x, -60, -2), Blocks.RED_BED.defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
					level.setBlockAndUpdate(new BlockPos(x, -60, -1), Blocks.RED_BED.defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
				}
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -58.3, 7.5), 180, 20);
			});
		}
		if (tick == 190) {
			// The hall's first round: everyone gets a name.
			server.execute(() -> io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(server.overworld(), new BlockPos(0, -60, 3)));
		}
		if (tick == 200) {
			shot(mc, "01_hall_block");
			server.execute(() -> io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(server.getPlayerList().getPlayers().get(0), new BlockPos(0, -60, 3)));
		}
		if (tick == 220) {
			pointAt(mc, 1);
		}
		if (tick == 230) {
			Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "the Village Hall screen opened");
			Showcase.check(mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen, "the hall opened on its own drawn screen, not a chest (30.4a): " + mc.screen);
			shot(mc, "02_hall_people");
			pointAt(mc, io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON);
		}
		if (tick == 245) {
			shot(mc, "03_hall_builder");
			pointAt(mc, 5);
		}
		if (tick == 260) {
			shot(mc, "04_hall_wellbeing");
			pointAt(mc, 6);
		}
		if (tick == 275) {
			shot(mc, "05_hall_requests");
			pointAt(mc, io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON + 1);
		}
		if (tick == 290) {
			shot(mc, "06_hall_worker");
			// ROADMAP 22.5: the page row, with the calendar (22.6) as its first tab.
			pointAt(mc, io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.Seasons.PAGE));
		}
		if (tick == 305) {
			shot(mc, "07_hall_calendar_tab");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu instanceof io.github.jcondedata.aliveworkplace.work.ChoiceMenu m) {
					int tab = io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.Seasons.PAGE);
					Showcase.check(m.icon(tab).is(net.minecraft.world.item.Items.CLOCK), "the calendar is the first tab of the page row");
					m.press(tab, player);
					Showcase.check(m.icon(4).is(net.minecraft.world.item.Items.CLOCK), "the calendar page opened");
				}
			});
		}
		if (tick == 315) {
			pointAt(mc, io.github.jcondedata.aliveworkplace.hall.Seasons.SEASON_SLOTS[
				io.github.jcondedata.aliveworkplace.hall.Seasons.today(server.overworld()).season().ordinal()]);
		}
		if (tick == 330) {
			shot(mc, "08_hall_calendar");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu instanceof io.github.jcondedata.aliveworkplace.work.ChoiceMenu m) {
					m.press(0, player); // back to the main page
				}
			});
			// GUI scale 4 needs a window at least 1280x960: 1920x1080 keeps the frames 16:9. The option only takes 4
			// once the window is big enough, so it is set after the resize.
			org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
		}
		if (tick == 340) {
			mc.options.guiScale().set(4);
			mc.resizeDisplay();
		}
		if (tick == 345) {
			pointAt(mc, 53); // an empty slot: no tooltip over the rows
		}
		if (tick == 360) {
			Showcase.check(mc.getWindow().getGuiScale() == 4, "the hall screen at GUI scale 4 (now " + mc.getWindow().getGuiScale() + ")");
			shot(mc, "09_hall_scale4");
			mc.stop();
		}
	}

	/** A workstation as placed on the ground, facing the camera (south): a grindstone on the floor, not on a wall. */
	static BlockState standing(BlockState state) {
		if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE)) {
			state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
				net.minecraft.world.level.block.state.properties.AttachFace.FLOOR);
		}
		if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
			state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		}
		return state;
	}

	private static void employ(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block station,
							   net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
							   net.minecraft.world.entity.npc.VillagerProfession job) {
		level.setBlockAndUpdate(pos, standing(station.defaultBlockState()));
		level.setBlockAndUpdate(pos.east(), Blocks.CHEST.defaultBlockState());
		Villager villager = EntityType.VILLAGER.spawn(level, pos.south(), MobSpawnType.COMMAND);
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, villager, pos, poi, job);
	}

	// --- Porter: carrying a miner's goods to the storehouse ------------------------------------------

	private void porterScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos bench = new BlockPos(-5, -60, 0);
				level.setBlockAndUpdate(bench, Blocks.BLAST_FURNACE.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(bench.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(bench.west());
				for (int i = 0; i < 6; i++) {
					chest.setItem(i, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
				}
				chest.setItem(6, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 24));
				chest.setItem(7, new ItemStack(net.minecraft.world.item.Items.COAL, 30));
				chest.setItem(8, new ItemStack(net.minecraft.world.item.Items.TORCH, 16));
				Villager miner = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				miner.setNoAi(true);
				io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, miner, bench);
				BlockPos storehouse = new BlockPos(5, -60, 0);
				level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.store.StorehouseBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(storehouse.east(), Blocks.BARREL.defaultBlockState()
					.setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
				level.setBlockAndUpdate(storehouse.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				Villager porter = EntityType.VILLAGER.spawn(level, storehouse.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.store.Porters.employ(level, porter, storehouse);
				// A builder behind, with nothing to build with: what they're missing goes on the storehouse's board.
				BlockPos builderBench = new BlockPos(-2, -60, -10);
				level.setBlockAndUpdate(builderBench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				Villager builder = EntityType.VILLAGER.spawn(level, builderBench.south(), MobSpawnType.COMMAND);
				Builders.employ(level, builder, builderBench);
				Builders.start(level, builder, null, io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.MARKET_STALL.id(),
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), new BlockPos(-12, -60, -20),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE));
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 64));
				player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.OAK_FENCE, 12));
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -57.0, 8.5), 180, 20);
			});
		}
		if (tick >= 50 && tick <= 350 && tick % 25 == 0) {
			shot(mc, String.format("%02d_porter", tick / 25));
		}
		if (tick == 360) {
			server.execute(() -> {
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(5.5, -58.3, 4.5), 180, 20);
				int stored = 0;
				for (BlockPos p : List.of(new BlockPos(6, -60, 0), new BlockPos(4, -60, 0))) {
					if (server.overworld().getBlockEntity(p) instanceof net.minecraft.world.Container c) {
						for (int i = 0; i < c.getContainerSize(); i++) {
							stored += c.getItem(i).getCount();
						}
					}
				}
				Showcase.check(stored > 0, "the porter carried the miner's goods into the storehouse (" + stored + " items)");
			});
		}
		if (tick == 420) {
			shot(mc, "20_storehouse_closeup");
			mc.options.hideGui = false;
			server.execute(() -> io.github.jcondedata.aliveworkplace.store.StorehouseBoard.open(server.getPlayerList().getPlayers().get(0), new BlockPos(5, -60, 0)));
		}
		if (tick == 470) {
			shot(mc, "21_request_board");
			Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "the requests board opened");
			mc.stop();
		}
	}

	// --- Shop: the shop screen with CobbleDollars prices ---------------------------------------------

	private void shopScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos counterPos = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(counterPos, ModBlocks.SHOP_COUNTER.defaultBlockState());
				level.setBlockAndUpdate(counterPos.east(), Blocks.CHEST.defaultBlockState());
				var counter = (io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity) level.getBlockEntity(counterPos);
				counter.setOwner(java.util.UUID.randomUUID(), "Jesse");
				int columns = io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity.COLUMNS;
				Object[][] stock = {
					{new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 16), 2}, {new ItemStack(net.minecraft.world.item.Items.BREAD, 6), 1},
					{new ItemStack(net.minecraft.world.item.Items.TORCH, 32), 1}, {new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 8), 5},
					{new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 1), 12}};
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(counterPos.east());
				for (int i = 0; i < stock.length; i++) {
					ItemStack goods = (ItemStack) stock[i][0];
					counter.setItem(i, goods.copy());
					counter.setItem(columns + i, new ItemStack(net.minecraft.world.item.Items.EMERALD, (Integer) stock[i][1]));
					chest.setItem(i, goods.copyWithCount(Math.min(64, goods.getCount() * 4)));
				}
				Villager keeper = EntityType.VILLAGER.spawn(level, counterPos.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, keeper, counterPos,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOP_COUNTER_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOPKEEPER);
				player.setGameMode(GameType.SURVIVAL);
				io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 1500);
				player.teleportTo(level, 2.5, -60, 6.5, 135, 20);
				io.github.jcondedata.aliveworkplace.shop.Shops.openMenu(player, keeper);
			});
		}
		if (tick == 90) {
			mc.getToasts().clear();
			pointAt(mc, io.github.jcondedata.aliveworkplace.shop.Shops.FIRST_GOODS_SLOT + 3);
		}
		if (tick == 100) {
			shot(mc, "01_shop_menu");
			Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "the shop screen opened");
		}
		if (tick == 105) {
			mc.setScreen(null);
			mc.options.hideGui = true;
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0), new Vec3(3.5, -58, 7.5), new Vec3(0.5, -59.5, 3.5)));
		}
		if (tick == 140) {
			shot(mc, "02_shop_counter");
			mc.stop();
		}
	}

	// --- Orchard: an orchard keeper picks berries, cocoa and (with Cobblemon) apricorns and berry plants -----

	private Villager keeper;
	private int orchardFruit;

	static BlockState cobblemonBlock(String id) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", id))
			.map(net.minecraft.world.level.block.Block::defaultBlockState).orElse(null);
	}

	static <T extends Comparable<T>> BlockState with(BlockState state, String property, T value) {
		for (var p : state.getProperties()) {
			if (p.getName().equals(property)) {
				@SuppressWarnings("unchecked")
				var typed = (net.minecraft.world.level.block.state.properties.Property<T>) p;
				return state.setValue(typed, value);
			}
		}
		return state;
	}

	private void orchardScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(6, server);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doPokemonSpawning false");
				level.setDayTime(2500);
				int fruit = 0;
				// A hedge of sweet berry bushes.
				for (int x = -5; x <= -1; x++) {
					level.setBlockAndUpdate(new BlockPos(x, -60, 5), Blocks.SWEET_BERRY_BUSH.defaultBlockState()
						.setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 3));
					fruit++;
				}
				// A jungle trunk with cocoa pods.
				for (int y = -60; y <= -56; y++) {
					level.setBlockAndUpdate(new BlockPos(-7, y, -2), Blocks.JUNGLE_LOG.defaultBlockState());
				}
				for (int y = -59; y <= -57; y++) {
					level.setBlockAndUpdate(new BlockPos(-6, y, -2), Blocks.COCOA.defaultBlockState()
						.setValue(net.minecraft.world.level.block.CocoaBlock.FACING, Direction.WEST).setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 2));
					fruit++;
				}
				// With Cobblemon: an apricorn tree and a bed of berry plants.
				BlockState leaves = cobblemonBlock("apricorn_leaves");
				if (leaves != null) {
					leaves = with(leaves, "persistent", true);
					for (int y = -60; y <= -56; y++) {
						level.setBlockAndUpdate(new BlockPos(6, y, -2), cobblemonBlock("apricorn_log"));
					}
					for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, -56, -3), new BlockPos(7, -55, -1))) {
						if (!(p.getX() == 6 && p.getZ() == -2 && p.getY() == -56)) {
							level.setBlockAndUpdate(p, leaves);
						}
					}
					level.setBlockAndUpdate(new BlockPos(6, -54, -2), leaves);
					Object[][] apricorns = {{"red_apricorn", new BlockPos(4, -56, -2), Direction.EAST}, {"yellow_apricorn", new BlockPos(8, -56, -2), Direction.WEST},
						{"blue_apricorn", new BlockPos(6, -56, 0), Direction.NORTH}, {"pink_apricorn", new BlockPos(5, -56, 0), Direction.NORTH}};
					for (Object[] a : apricorns) {
						BlockState state = with(with(cobblemonBlock((String) a[0]), "facing", (Direction) a[2]), "age", 3);
						level.setBlockAndUpdate((BlockPos) a[1], state);
						fruit++;
					}
					String[] berries = {"oran_berry", "pecha_berry", "cheri_berry"};
					for (int i = 0; i < berries.length; i++) {
						BlockPos p = new BlockPos(3 + i, -60, 5);
						level.setBlockAndUpdate(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
						level.setBlockAndUpdate(p, with(cobblemonBlock(berries[i]), "age", 5));
						if (level.getBlockEntity(p) instanceof com.cobblemon.mod.common.block.entity.BerryBlockEntity plant) {
							plant.generateSimpleYields();
						}
						fruit++;
					}
				}
				orchardFruit = fruit;
				BlockPos basket = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(basket, Blocks.COMPOSTER.defaultBlockState());
				level.setBlockAndUpdate(basket.east(), Blocks.CHEST.defaultBlockState());
				keeper = EntityType.VILLAGER.spawn(level, basket.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, keeper, basket,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.ORCHARD_KEEPER);
				// A Bulbasaur in a pasture nearby helps (a Pokémon partner).
				if (leaves != null) {
					BlockPos pasture = new BlockPos(-3, -60, -2);
					level.setBlockAndUpdate(pasture, with(with(cobblemonBlock("pasture"), "waterlogged", false), "part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
					level.setBlockAndUpdate(pasture.above(), with(with(cobblemonBlock("pasture"), "waterlogged", false), "part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
					ServerPlayer player = server.getPlayerList().getPlayers().get(0);
					var bulbasaur = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("bulbasaur level=12", " ", "=").create();
					com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(bulbasaur);
					if (level.getBlockEntity(pasture) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
						pen.tether(player, bulbasaur, Direction.SOUTH);
					}
				}
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -55.5, 10.5), 180, 24);
			});
		}
		if (tick == 80) {
			shot(mc, "10_orchard_start");
		}
		if (tick == 150) {
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), keeper.position().add(0, 1.2, 3.5), 180, 8));
		}
		if (tick == 175) {
			shot(mc, "20_orchard_partner");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -55.5, 10.5), 180, 24));
		}
		if (tick > 60 && tick % 10 == 0 && doneAt < 0 && (tick < 150 || tick > 185)) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(keeper != null
				&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.FRUIT_PICKED.getOrElse(keeper, 0) >= orchardFruit));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick < doneAt + 200 && tick % 10 == 0) {
			shot(mc, String.format("frame_%03d", frame++)); // the harvest goes to the chest
		}
		if (doneAt > 0 && tick == doneAt + 200) {
			Showcase.check(true, "the orchard keeper picked every fruit");
			shot(mc, "50_orchard_done");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			giveUp(mc, "the orchard keeper picked every fruit");
		}
	}

	// --- Forest: a lumberjack cuts and replants a few trees -----------------------------------------

	private Villager lumberjack;
	private static final int FOREST_TREES = 4;
	private int replantedAt = -1;
	private volatile long saplingsPlanted;

	private void forestScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, server);
				level.setDayTime(2500);
				var features = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
				var kinds = List.of(net.minecraft.data.worldgen.features.TreeFeatures.OAK, net.minecraft.data.worldgen.features.TreeFeatures.BIRCH,
					net.minecraft.data.worldgen.features.TreeFeatures.SPRUCE, net.minecraft.data.worldgen.features.TreeFeatures.OAK);
				BlockPos[] spots = {new BlockPos(-6, -60, -6), new BlockPos(3, -60, -9), new BlockPos(9, -60, -3), new BlockPos(-4, -60, 5)};
				net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7);
				for (int i = 0; i < spots.length; i++) {
					features.getHolderOrThrow(kinds.get(i)).value().place(level, level.getChunkSource().getGenerator(), random, spots[i]);
				}
				BlockPos block = new BlockPos(1, -60, 1);
				level.setBlockAndUpdate(block, Blocks.FLETCHING_TABLE.defaultBlockState());
				level.setBlockAndUpdate(block.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(block.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_AXE));
				// A sapling of each kind too, as a player keeps them (B9): leaves drop one only 1 time in 20, so now and then a
				// birch or spruce gives none, and the lumberjack replants that stump from the chests after the drop-off.
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.OAK_SAPLING));
				chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.BIRCH_SAPLING));
				chest.setItem(3, new ItemStack(net.minecraft.world.item.Items.SPRUCE_SAPLING));
				lumberjack = EntityType.VILLAGER.spawn(level, block.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, lumberjack, block,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.FLETCHER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.LUMBERJACK);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(11.5, -53, 12.5), 140, 28);
			});
		}
		if (tick > 60 && tick % 10 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(lumberjack != null
				&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.TREES_FELLED.getOrElse(lumberjack, 0) >= FOREST_TREES));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick < doneAt + 100 && tick % 10 == 0) {
			shot(mc, String.format("frame_%03d", frame++)); // the last sapling goes in
		}
		// The saplings go in after the last tree falls: wait up to 30 seconds for all four.
		if (doneAt > 0 && replantedAt < 0 && tick >= doneAt + 100 && tick % 20 == 0) {
			server.execute(() -> saplingsPlanted = BlockPos.betweenClosedStream(new BlockPos(-12, -60, -15), new BlockPos(15, -59, 11))
				.filter(p -> server.overworld().getBlockState(p).is(net.minecraft.tags.BlockTags.SAPLINGS)).count());
			if (saplingsPlanted >= FOREST_TREES || tick >= doneAt + 600) {
				replantedAt = tick;
				Showcase.check(true, "the lumberjack felled all four trees");
				Showcase.check(saplingsPlanted >= FOREST_TREES, "the lumberjack replanted (" + saplingsPlanted + " saplings in the ground)");
			}
		}
		if (replantedAt > 0 && tick == replantedAt + 10) {
			shot(mc, "50_forest_done");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			giveUp(mc, "the lumberjack felled all four trees");
		}
	}

	// --- Guide: the Guide Book a new player is given, every page in turn ------------------------------------

	/**
	 * ROADMAP 24.4: a chest-style screen's title must fit inside its panel at every GUI scale (the Shop Counter's ran
	 * past the right edge). Only a failure is recorded, so scenes keep their own checks.
	 */
	private static void titleFits(Minecraft mc, String shot) {
		if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
			return;
		}
		try {
			java.lang.reflect.Field width = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("imageWidth");
			java.lang.reflect.Field labelX = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("titleLabelX");
			width.setAccessible(true);
			labelX.setAccessible(true);
			// From the title's start to the panel's right edge, keeping the same inset there as on the left; a title that
			// starts in the right half (the player inventory's "Crafting", at 97 of 176) keeps vanilla's 8 (B41).
			int w = width.getInt(screen);
			int x = labelX.getInt(screen);
			int room = w - x - (x < w / 2 ? x : 8);
			if (mc.font.width(screen.getTitle()) > room) {
				Showcase.check(false, shot + ": the title '" + screen.getTitle().getString() + "' is wider than its screen ("
					+ mc.font.width(screen.getTitle()) + " > " + room + " pixels)");
			}
		} catch (ReflectiveOperationException e) {
			// Not a vanilla container screen layout: nothing to measure.
		}
	}

	private io.github.jcondedata.aliveworkplace.client.ConfigScreen configScreen;
	private String configBefore;

	/**
	 * The settings screen Mod Menu opens (ROADMAP 26.3): every option's label fits its button, a switch turned off is
	 * saved to config/aliveworkplace.json and put into effect when the screen closes. The file is put back afterwards
	 * (the run directory's config is shared by every scene).
	 */
	private void configScene(Minecraft mc) {
		tick++;
		java.nio.file.Path file = io.github.jcondedata.aliveworkplace.platform.Platform.get().configDir()
			.resolve(io.github.jcondedata.aliveworkplace.WorkplaceConfig.FILE);
		if (tick == 40) {
			try {
				configBefore = java.nio.file.Files.readString(file);
			} catch (java.io.IOException e) {
				configBefore = null;
			}
			configScreen = new io.github.jcondedata.aliveworkplace.client.ConfigScreen(null);
			mc.setScreen(configScreen);
		}
		if (tick == 55) {
			List<String> problems = new ArrayList<>();
			for (var widget : configScreen.optionWidgets()) {
				if (widget == null) {
					problems.add("an option has no button");
					continue;
				}
				String text = widget.getMessage().getString();
				if (text.contains("aliveworkplace.config")) {
					problems.add("untranslated " + text);
				} else if (mc.font.width(widget.getMessage()) > widget.getWidth() - 8) {
					problems.add("'" + text + "' is wider than its button");
				}
			}
			// A switch reads wider turned off ("OFF" vs "ON"): every switch must fit that way too (B61).
			int buttonWidth = configScreen.optionWidgets().isEmpty() || configScreen.optionWidgets().get(0) == null ? 150
				: configScreen.optionWidgets().get(0).getWidth();
			for (String name : io.github.jcondedata.aliveworkplace.WorkplaceConfig.optionNames()) {
				if (io.github.jcondedata.aliveworkplace.WorkplaceConfig.isSwitch(name)) {
					Component off = net.minecraft.client.Options.genericValueLabel(
						Component.translatable(io.github.jcondedata.aliveworkplace.client.ConfigScreen.labelKey(name)), net.minecraft.network.chat.CommonComponents.OPTION_OFF);
					if (mc.font.width(off) > buttonWidth - 8) {
						problems.add("'" + off.getString() + "' is wider than its button");
					}
				}
			}
			Showcase.check(problems.isEmpty() && configScreen.optionWidgets().size() == io.github.jcondedata.aliveworkplace.WorkplaceConfig.optionNames().size(),
				"every setting has a button whose label fits (" + configScreen.optionWidgets().size() + " settings"
					+ (problems.isEmpty() ? "" : ": " + String.join(", ", problems)) + ")");
			shot(mc, "01_config_numbers");
		}
		if (tick == 60) {
			configScreen.scrollToEnd();
		}
		if (tick == 70) {
			shot(mc, "02_config_switches");
			// Turn the festivals off with the button itself, as a player would.
			for (var widget : configScreen.optionWidgets()) {
				if (widget.getMessage().getString().startsWith(net.minecraft.client.resources.language.I18n.get("aliveworkplace.config.festivals"))) {
					widget.onClick(widget.getX() + 2, widget.getY() + 2);
				}
			}
		}
		if (tick == 80) {
			shot(mc, "03_config_festivals_off");
			mc.setScreen(null);
		}
		if (tick == 90) {
			var saved = io.github.jcondedata.aliveworkplace.WorkplaceConfig.load(file.getParent());
			Showcase.check(!saved.festivals && !io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED,
				"turning Festivals off and closing the screen saves it and puts it into effect");
			try {
				if (configBefore != null) {
					java.nio.file.Files.writeString(file, configBefore);
				}
			} catch (java.io.IOException e) {
				Showcase.check(false, "the config file was put back: " + e);
			}
			io.github.jcondedata.aliveworkplace.WorkplaceConfig.loadAndApply(file.getParent());
			mc.stop();
		}
	}

	private io.github.jcondedata.aliveworkplace.client.guide.GuideScreen guide;
	private final List<String> guideProblems = new ArrayList<>();
	private volatile boolean guideGiven;

	private void guideScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			// The first-join advancement hands every player the book (aliveworkplace:guide_book).
			server.execute(() -> guideGiven = server.getPlayerList().getPlayers().get(0).getInventory()
				.countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.GUIDE_BOOK) > 0);
		}
		if (tick == 60) {
			guide = new io.github.jcondedata.aliveworkplace.client.guide.GuideScreen(0);
			mc.setScreen(guide);
		}
		if (guide == null || tick < 70) {
			return;
		}
		int t = tick - 70;
		int page = t / 12;
		if (page < guide.pageCount()) {
			if (t % 12 == 0) {
				guide.show(page);
			}
			if (t % 12 == 8) {
				String name = page == 0 ? "contents" : guide.pageAt(page).id();
				if (mc.screen != guide || guide.page() != page) {
					guideProblems.add("page " + page + " didn't open");
				} else if (page > 0) {
					var p = guide.pageAt(page);
					if (guide.textLines(page) > io.github.jcondedata.aliveworkplace.client.guide.GuideScreen.TEXT_LINES) {
						guideProblems.add(name + "'s text takes " + guide.textLines(page) + " lines");
					}
					if (mc.getResourceManager().getResource(p.image()).isEmpty()) {
						guideProblems.add(name + " has no picture");
					}
					if (net.minecraft.client.resources.language.I18n.get("guide.aliveworkplace.page." + name + ".text")
						.startsWith("guide.aliveworkplace")) {
						guideProblems.add(name + " has no words");
					}
				}
				shot(mc, String.format("%02d_guide_%s", page, name));
			}
			return;
		}
		if (t == guide.pageCount() * 12) {
			Showcase.check(guideGiven, "a new player is given the Guide Book");
			Showcase.check(guideProblems.isEmpty(), "every page of the Guide Book opens with its picture and its words fit ("
				+ (guide.pageCount() - 1) + " pages" + (guideProblems.isEmpty() ? "" : ": " + String.join(", ", guideProblems)) + ")");
			mc.stop();
		}
	}

	// --- Staff: every workstation of ours with its worker beside it, along a village street ------------------

	/** The workstations ROADMAP 21.1a kept (the other jobs share vanilla's blocks), with their POI and job; the Village
	 * Hall has no worker of its own. */
	private static Object[][] staffRows() {
		return new Object[][] {
			{ModBlocks.BLUEPRINT_TABLE, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BLUEPRINT_TABLE_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER},
			{ModBlocks.STOREHOUSE, io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.PORTER},
			{ModBlocks.SHOP_COUNTER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOP_COUNTER_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOPKEEPER},
			{ModBlocks.VILLAGE_HALL, null, null},
			{ModBlocks.MAILBOX, io.github.jcondedata.aliveworkplace.registry.ModVillagers.MAILBOX_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.POSTMAN},
			{ModBlocks.TRAVEL_POST, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAVEL_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.FERRYMAN},
			{ModBlocks.TRAINING_POST, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINING_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINER},
		};
	}

	/** Where the staff scene's {@code i}th workstation stands; its worker stands one block east, both facing the street. */
	private static BlockPos staffSpot(int i) {
		return new BlockPos(i * 4 - 13, -60, 0);
	}

	private void staffScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				Object[][] staff = staffRows();
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(-16, -61, 2), new BlockPos(16, -61, 4))) {
					level.setBlockAndUpdate(p, Blocks.DIRT_PATH.defaultBlockState());
				}
				for (int i = 0; i < staff.length; i++) {
					BlockPos block = staffSpot(i);
					net.minecraft.world.level.block.Block b = (net.minecraft.world.level.block.Block) staff[i][0];
					BlockState state = standing(b.defaultBlockState());
					if (b == ModBlocks.MAILBOX) {
						state = state.setValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.HAS_MAIL, true);
					}
					level.setBlockAndUpdate(block, state);
					if (staff[i][1] == null) {
						continue;
					}
					Object[] row = staff[i];
					// The block's job-site record is added in a later server task, so the worker takes it after that.
					server.execute(() -> {
						Villager v = EntityType.VILLAGER.spawn(level, block.east(), MobSpawnType.COMMAND);
						v.setNoAi(true);
						v.setYRot(0);
						v.setYBodyRot(0);
						v.setYHeadRot(0);
						io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, v, block,
							(net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType>) row[1],
							(net.minecraft.world.entity.npc.VillagerProfession) row[2]);
					});
				}
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -57.6, 14.5), 180, 10);
			});
		}
		if (tick == 130) {
			server.execute(() -> {
				// Each workstation stands at its spot, and the villager beside it works there, in its job.
				ServerLevel level = server.overworld();
				Object[][] staff = staffRows();
				List<String> wrong = new ArrayList<>();
				for (int i = 0; i < staff.length; i++) {
					BlockPos block = staffSpot(i);
					net.minecraft.world.level.block.Block b = (net.minecraft.world.level.block.Block) staff[i][0];
					String name = b.getName().getString();
					if (!level.getBlockState(block).is(b)) {
						wrong.add(name + " missing");
						continue;
					}
					if (staff[i][2] == null) {
						continue;
					}
					var profession = (net.minecraft.world.entity.npc.VillagerProfession) staff[i][2];
					var poi = (net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType>) staff[i][1];
					if (!level.getPoiManager().existsAtPosition(poi, block) || level.getPoiManager().getFreeTickets(block) != 0) {
						wrong.add(name + " not taken as a job site");
						continue;
					}
					var site = net.minecraft.core.GlobalPos.of(level.dimension(), block);
					boolean works = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(block.east()).inflate(0.5),
						v -> v.getVillagerData().getProfession().equals(profession)
							&& v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).filter(site::equals).isPresent())
						.size() == 1;
					if (!works) {
						wrong.add("no " + io.github.jcondedata.aliveworkplace.work.Stations.name(profession).getString() + " at the " + name);
					}
				}
				Showcase.check(wrong.isEmpty(), "every workstation stands with its worker" + (wrong.isEmpty() ? "" : " (" + String.join(", ", wrong) + ")"));
			});
		}
		if (tick == 140) {
			shot(mc, "01_staff");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(-7.5, -58.2, 6.5), 180, 8));
		}
		if (tick == 180) {
			shot(mc, "02_staff_close_1");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(6.5, -58.2, 6.5), 180, 8));
		}
		if (tick == 220) {
			shot(mc, "03_staff_close_2");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -53.5, 10.5), 180, 35));
		}
		if (tick == 260) {
			shot(mc, "04_staff_above");
			mc.stop();
		}
	}

	// --- Guard: a guard gears up and fights off three husks ------------------------------------------

	private final List<net.minecraft.world.entity.Entity> husks = new ArrayList<>();

	private Villager guardForShot;
	private int trainedAt = -1;

	private void guardScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos post = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(post, Blocks.GRINDSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(post.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(post.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
				Villager guard = EntityType.VILLAGER.spawn(level, post.south(), MobSpawnType.COMMAND);
				guardForShot = guard;
				guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
				guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
				guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
				guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.CHAINMAIL_LEGGINGS));
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, guard, post,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.WEAPONSMITH, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
				// SCENE=guard_pokemon: a Machop and a Dratini in a pasture by the post fight beside the guard.
				if ("guard_pokemon".equals(System.getProperty("aliveworkplace.scene"))) {
					BlockPos pasture = new BlockPos(-3, -60, 2);
					level.setBlockAndUpdate(pasture, with(with(cobblemonBlock("pasture"), "waterlogged", false), "part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
					level.setBlockAndUpdate(pasture.above(), with(with(cobblemonBlock("pasture"), "waterlogged", false), "part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
					ServerPlayer player = server.getPlayerList().getPlayers().get(0);
					for (String species : List.of("machop level=30", "dratini level=30")) {
						var pokemon = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(species, " ", "=").create();
						com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
						if (level.getBlockEntity(pasture) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
							pen.tether(player, pokemon, species.startsWith("machop") ? Direction.NORTH : Direction.WEST);
						}
					}
				}
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(7.5, -55, 9.5), 145, 28);
			});
		}
		if (tick == 110 && guardForShot != null) {
			// A close look at the guard in their armor, before the husks come.
			server.execute(() -> {
				Villager g = guardForShot;
				// Slimes from the superflat world's slime chunks would draw the guard away.
				for (net.minecraft.world.entity.monster.Slime slime : g.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
					g.getBoundingBox().inflate(64))) {
					slime.discard();
				}
				g.setTarget(null);
				g.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
				g.getNavigation().stop();
				g.setNoAi(true);
				g.setYRot(-34.5f);
				g.setYHeadRot(-34.5f);
				g.setYBodyRot(-34.5f);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(g.getX() + 2.2, g.getY() + 1.9, g.getZ() + 3.2), 146, 18);
			});
		}
		if (tick == 125) {
			shot(mc, "10_guard_armor");
			server.execute(() -> {
				guardForShot.setYRot(60f);
				guardForShot.setYHeadRot(60f);
				guardForShot.setYBodyRot(60f);
			});
		}
		if (tick == 132) {
			shot(mc, "11_guard_armor_side");
			server.execute(() -> {
				guardForShot.setNoAi(false);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(7.5, -55, 9.5), 145, 28);
			});
		}
		if (tick == 140) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				for (BlockPos p : List.of(new BlockPos(-10, -60, -8), new BlockPos(-12, -60, -4), new BlockPos(-8, -60, -12))) {
					husks.add(EntityType.HUSK.spawn(level, p, MobSpawnType.COMMAND));
				}
			});
		}
		if (tick >= 150 && tick % 20 == 0 && "guard_pokemon".equals(System.getProperty("aliveworkplace.scene")) && guardForShot != null) {
			// Follow the guard from close by, to see the Pokémon's moves land.
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), guardForShot.position().add(4, 2.5, 5), 141, 20));
		}
		if (tick > 100 && tick % 5 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(!husks.isEmpty() && husks.stream().noneMatch(net.minecraft.world.entity.Entity::isAlive)));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick < doneAt + 60 && tick % 5 == 0) {
			shot(mc, String.format("frame_%03d", frame++));
		}
		if (doneAt > 0 && tick == doneAt + 80) {
			Showcase.check(true, "the guard killed the three husks");
			shot(mc, "50_guard_done");
			// Then a Training Dummy by the post: the guard spars with it.
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.setBlockAndUpdate(new BlockPos(3, -60, 3), ModBlocks.TRAINING_DUMMY.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				// First a close look at the dummy itself, from the front.
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(4.3, -58.6, 5.4), 150, 20);
			});
		}
		if (doneAt > 0 && tick == doneAt + 88) {
			shot(mc, "55_training_dummy");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(7.5, -58.2, 4.2), 100, 16));
		}
		if (doneAt > 0 && tick > doneAt + 80 && tick % 5 == 0 && trainedAt < 0 && guardForShot != null
			&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.DUMMY_HITS.getOrElse(guardForShot, 0) >= 2) {
			trainedAt = tick;
		}
		if (trainedAt > 0 && tick > trainedAt && tick <= trainedAt + 40 && tick % 4 == 0) {
			shot(mc, String.format("frame_%03d", frame++));
		}
		if (trainedAt > 0 && tick == trainedAt + 9) {
			shot(mc, "60_guard_training");
		}
		if (trainedAt > 0 && tick == trainedAt + 44) {
			Showcase.check(true, "the guard sparred with the Training Dummy");
			mc.stop();
		}
		if (tick >= 3000) {
			giveUp(mc, "the guard killed the husks and sparred with the dummy");
		}
	}

	// --- Mail: the mailbox screen, then a postman carrying a parcel to a friend's mailbox --------------

	private java.util.UUID parcelId;

	private void mailScene(Minecraft mc, MinecraftServer server) {
		tick++;
		BlockPos mine = new BlockPos(3, -60, -2);
		BlockPos theirs = new BlockPos(-9, -60, -12);
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				var office = io.github.jcondedata.aliveworkplace.mail.PostOffice.get(server);
				level.setBlockAndUpdate(mine, ModBlocks.MAILBOX.defaultBlockState().setValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.FACING, Direction.SOUTH));
				var box = (io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity) level.getBlockEntity(mine);
				box.setOwner(player.getUUID(), player.getGameProfile().getName());
				office.register(player.getUUID(), net.minecraft.core.GlobalPos.of(level.dimension(), mine));
				box.receive(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 5));
				box.receive(new ItemStack(net.minecraft.world.item.Items.COOKED_SALMON, 12));
				box.receive(new ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK));
				java.util.UUID friend = java.util.UUID.nameUUIDFromBytes("friend".getBytes());
				level.setBlockAndUpdate(theirs, ModBlocks.MAILBOX.defaultBlockState().setValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.FACING, Direction.SOUTH));
				((io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity) level.getBlockEntity(theirs)).setOwner(friend, "Friend");
				office.register(friend, net.minecraft.core.GlobalPos.of(level.dimension(), theirs));
				BlockPos desk = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(desk, ModBlocks.MAILBOX.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.FACING, Direction.SOUTH));
				Villager postman = EntityType.VILLAGER.spawn(level, desk.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, postman, desk,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.MAILBOX_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.POSTMAN);
				hover(player, new Vec3(4.5, -58, 3.5), 170, 30); // within reach, or the menu closes at once
				io.github.jcondedata.aliveworkplace.platform.Platform.get().openMenu(player, box, box.getBlockPos());
			});
		}
		if (tick == 80 && mc.screen instanceof io.github.jcondedata.aliveworkplace.client.MailboxScreen) {
			String[] texts = {"Friend", "Diamonds for the new roof!"};
			int n = 0;
			for (var child : mc.screen.children()) {
				if (child instanceof net.minecraft.client.gui.components.EditBox edit && n < texts.length) {
					edit.setValue(texts[n++]);
				}
			}
		}
		if (tick == 100) {
			Showcase.check(mc.screen instanceof io.github.jcondedata.aliveworkplace.client.MailboxScreen, "the mailbox screen opened");
			shot(mc, "01_mailbox_screen");
		}
		if (tick == 110) {
			mc.setScreen(null);
			mc.options.hideGui = true;
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				hover(player, new Vec3(8.5, -53, 6.5), 135, 32);
				java.util.UUID friend = java.util.UUID.nameUUIDFromBytes("friend".getBytes());
				parcelId = io.github.jcondedata.aliveworkplace.mail.PostOffice.get(server).post(player.getUUID(), player.getGameProfile().getName(), friend,
					"Friend", net.minecraft.core.GlobalPos.of(level.dimension(), mine), level.getGameTime(),
					List.of(new ItemStack(net.minecraft.world.item.Items.CAKE))).id();
			});
		}
		if (tick > 120 && tick % 10 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(parcelId != null && io.github.jcondedata.aliveworkplace.mail.PostOffice.get(server).parcel(parcelId) == null));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick == doneAt + 40) {
			Showcase.check(true, "the postman delivered the parcel");
			shot(mc, "50_mail_delivered");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			giveUp(mc, "the postman delivered the parcel");
		}
	}

	// --- Tutor: the Move Tutor's lesson screen (needs Cobblemon: run.sh adds it for this scene) ------

	private void tutorScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos desk = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(desk, ModBlocks.TRAINING_POST.defaultBlockState());
				Villager tutor = EntityType.VILLAGER.spawn(level, desk.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, tutor, desk,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINING_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TUTOR);
				tutor.setVillagerData(tutor.getVillagerData().setLevel(3));
				var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				for (String spec : List.of("pikachu level=30", "bulbasaur level=24", "eevee level=18")) {
					party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(spec, " ", "=").create());
				}
				player.setGameMode(GameType.SURVIVAL);
				player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, 40));
				player.teleportTo(level, 2.5, -60, 5.5, 135, 20);
				io.github.jcondedata.aliveworkplace.tutor.Tutors.open(player, tutor);
			});
		}
		if (tick == 90) {
			mc.getToasts().clear();
			pointAt(mc, 13); // an empty slot, so no tooltip covers the screen
		}
		if (tick == 100) {
			Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "the lesson screen opened");
			shot(mc, "01_tutor_screen");
			pointAt(mc, 19); // the second lesson: its tooltip
		}
		if (tick == 120) {
			mc.getToasts().clear();
			shot(mc, "02_tutor_lesson");
		}
		if (tick == 130) {
			mc.stop();
		}
	}

	// --- Missing: a placed blueprint's tooltip says what the builder's chests are short of ------------

	private void missingScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.setDayTime(2500);
				BlockPos bench = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(bench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				level.setBlockAndUpdate(bench.east(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
				if (level.getBlockEntity(bench.east()) instanceof net.minecraft.world.Container chest) {
					chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 64));
					chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
				}
				ItemStack blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(StarterBlueprints.STARTER_COTTAGE.id(), null);
				var bp = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
				var placement = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.placementAt(level.dimension().location(), bp.size(),
					new BlockPos(0, -60, 12), net.minecraft.world.level.block.Rotation.NONE);
				blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
					io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(blueprint).orElseThrow().withSize(bp.size()).withPlacement(java.util.Optional.of(placement)));
				player.getInventory().setItem(0, blueprint);
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(level, 2.5, -60, 6.5, 180, 10);
			});
		}
		if (tick == 110) {
			shot(mc, "00_missing_site");
		}
		if (tick == 120) {
			mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
		}
		if (tick == 130) {
			double scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			setMouse(mc, (left + 8 + 8) * scale, (top + 142 + 8) * scale);
		}
		if (tick == 150) {
			mc.getToasts().clear();
			shot(mc, "01_blueprint_missing");
			String heading = net.minecraft.locale.Language.getInstance().getOrDefault("tooltip.aliveworkplace.blueprint.missing");
			String summary = net.minecraft.locale.Language.getInstance().getOrDefault("tooltip.aliveworkplace.blueprint.missing_summary").split("%")[0];
			boolean says = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, mc.player.getInventory().getItem(0)).stream()
				.map(Component::getString).anyMatch(line -> line.startsWith(heading) || line.startsWith(summary));
			Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen && says,
				"the blueprint's tooltip says what the builder's chests are short of");
		}
		// 23.4: a Book and Quill in the other hand, right-click with the blueprint: the material list to take away.
		if (tick == 160) {
			mc.setScreen(null);
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().selected = 0;
				player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK));
				player.getMainHandItem().use(server.overworld(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			});
		}
		if (tick == 180) {
			ItemStack book = mc.player.getOffhandItem();
			if (book.is(net.minecraft.world.item.Items.WRITTEN_BOOK)) {
				mc.setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(
					net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess.fromItem(book)));
			}
		}
		if (tick == 195) {
			shot(mc, "02_material_list");
			if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen view) {
				view.setPage(1);
			}
		}
		if (tick == 210) {
			shot(mc, "03_material_list_page");
			var content = mc.player.getOffhandItem().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
			boolean listed = content != null && content.pages().size() > 1 && content.pages().get(1).raw().getString().contains("×");
			Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen && listed,
				"a Book and Quill became the blueprint's material list, with what's still to bring");
		}
		if (tick == 220) {
			mc.stop();
		}
	}

	// --- Trader: the Pokémon Trader's offers (needs Cobblemon too) ------------------------------------

	private Villager battleTrainer;
	private final AtomicBoolean megaSeen = new AtomicBoolean(false);
	private final AtomicBoolean battleOver = new AtomicBoolean(false);
	private final AtomicBoolean battleStarted = new AtomicBoolean(false);
	private int battleShots;
	private int lastTurn = -1;
	private int lastTurnTick;

	// B43: what a battle that stopped moving is waiting on (every field of the battle, sizes for collections).
	private static void dumpStuckBattle(Object battle) {
		for (Class<?> c = battle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for (java.lang.reflect.Field f : c.getDeclaredFields()) {
				try {
					f.setAccessible(true);
					Object v = f.get(battle);
					String s;
					if (v instanceof java.util.Collection<?> col) {
						s = "size " + col.size() + " " + col.stream().limit(6).map(String::valueOf).toList();
					} else if (v instanceof java.util.Map<?, ?> map) {
						s = "size " + map.size();
					} else {
						s = String.valueOf(v);
					}
					System.out.println("[battle scene] stuck " + f.getName() + " = " + (s.length() > 300 ? s.substring(0, 300) : s));
				} catch (Throwable t) {
					System.out.println("[battle scene] stuck " + f.getName() + " unreadable: " + t);
				}
			}
		}
	}

	/**
	 * SCENE=battle (Cobblemon + Mega Showdown): the player challenges a Master trainer whose lead holds its Mega Stone.
	 * The trainer's Pokémon come out beside them and Mega Evolve; the player's side always uses its first move.
	 */
	private void battleScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				com.cobblemon.mod.common.api.events.CobblemonEvents.MEGA_EVOLUTION.subscribe(com.cobblemon.mod.common.api.Priority.NORMAL, event -> {
					megaSeen.set(true);
					System.out.println("[battle scene] MEGA EVOLUTION: " + event.getPokemon().getEffectedPokemon().getSpecies().getName());
					return kotlin.Unit.INSTANCE;
				});
				// A trainer whose Master team leads with the Pokémon holding the Mega Stone.
				java.util.UUID id = null;
				for (int i = 0; i < 500 && id == null; i++) {
					java.util.UUID u = new java.util.UUID(0xba771eL, i);
					var team = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(u, 5);
					if (!team.isEmpty() && io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonMegas.holdsItsStone(team.get(0))) {
						id = u;
					}
				}
				System.out.println("[battle scene] trainer " + id);
				BlockPos post = new BlockPos(0, -60, 6);
				level.setBlockAndUpdate(post, ModBlocks.TRAINING_POST.defaultBlockState());
				Villager trainer = new Villager(EntityType.VILLAGER, level);
				trainer.setUUID(id);
				trainer.moveTo(0.5, -60, 5.5, 180, 0);
				level.addFreshEntity(trainer);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, trainer, post,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINING_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINER);
				trainer.setVillagerData(trainer.getVillagerData().setLevel(5));
				battleTrainer = trainer;
				var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				for (String p : List.of("snorlax level=100", "blissey level=100", "skarmory level=100", "tyranitar level=100", "dragonite level=100", "garchomp level=100")) {
					party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(p, " ", "=").create());
				}
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(level, 0.5, -60, -6.5, 0, 10);
			});
		}
		if (tick == 80) {
			server.execute(() -> io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(server.getPlayerList().getPlayers().get(0), battleTrainer));
		}
		if (tick > 80 && tick % 4 == 0) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				var battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player);
				if (battle == null) {
					battleOver.set(tick > 200);
					return;
				}
				battleStarted.set(true);
				var us = battle.getActor(player);
				if (tick % 100 == 0) {
					StringBuilder state = new StringBuilder("[battle scene] tick " + tick + " turn " + battle.getTurn());
					battle.getActors().forEach(a -> state.append(" | ").append(a.getName().getString()).append(" request=").append(a.getRequest() != null)
						.append(" mustChoose=").append(a.getMustChoose()).append(" responses=").append(a.getResponses().size()));
					System.out.println(state);
					if (battle.getTurn() == lastTurn && tick - lastTurnTick >= 300 && tick % 500 == 0) {
						dumpStuckBattle(battle);
					}
				}
				if (battle.getTurn() != lastTurn) {
					lastTurn = battle.getTurn();
					lastTurnTick = tick;
				}
				if (us == null || us.getRequest() == null || !us.getMustChoose() || !us.getResponses().isEmpty()) {
					return;
				}
				var request = us.getRequest();
				List<com.cobblemon.mod.common.battles.ShowdownActionResponse> choice = new ArrayList<>();
				if (request.getForceSwitch() != null && request.getForceSwitch().contains(true)) {
					// Send in the next Pokémon that can still fight.
					for (var pokemon : us.getPokemonList()) {
						if (pokemon.getHealth() > 0 && us.getActivePokemon().stream().noneMatch(a -> a.getBattlePokemon() == pokemon)) {
							choice.add(new com.cobblemon.mod.common.battles.SwitchActionResponse(pokemon.getUuid()));
							break;
						}
					}
				} else if (request.getActive() != null && !request.getActive().isEmpty()) {
					var moves = request.getActive().get(0).getMoves();
					// The strongest usable move (B43): the first one was sometimes a status move, and the battle ran past the
					// scene's time on turns that did no damage.
					var usable = moves.stream().filter(m -> m.canBeUsed())
						.max(java.util.Comparator.comparingDouble(m -> {
							var template = com.cobblemon.mod.common.api.moves.Moves.getByName(m.getId());
							return template == null ? 0 : template.getPower();
						}))
						.orElse(moves.get(0));
					if (tick % 100 == 0) {
						System.out.println("[battle scene] tick " + tick + " our move " + usable.getId());
					}
					var targets = usable.getTargets(us.getActivePokemon().get(0));
					String target = targets == null || targets.isEmpty() ? null : targets.stream().map(t -> t.getPNX())
						.filter(pnx -> pnx.startsWith("p2")).findFirst().orElse(targets.get(0).getPNX());
					choice.add(new com.cobblemon.mod.common.battles.MoveActionResponse(usable.getId(), target, null));
				}
				if (!choice.isEmpty()) {
					us.setActionResponses(choice);
				}
			});
		}
		if (tick > 100 && tick % 10 == 0 && battleShots < 250) {
			// Keep an eye on the trainer's side.
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.teleportTo(server.overworld(), 8.5, -58.2, 2.5, 72, 14);
			});
			shot(mc, String.format("battle_%03d", battleShots++));
		}
		if ((battleOver.get() || tick >= 8000) && tick % 20 == 0) {
			System.out.println("[battle scene] over at tick " + tick + ", mega " + megaSeen.get());
			Showcase.check(battleStarted.get(), "the Master trainer took the challenge");
			Showcase.check(megaSeen.get(), "the Master's lead Mega Evolved");
			Showcase.check(battleOver.get() && tick < 8000, "the battle ran to the end");
			shot(mc, "99_battle_end");
			mc.stop();
		}
	}

	private void traderScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos board = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(board, ModBlocks.SHOP_COUNTER.defaultBlockState());
				Villager trader = EntityType.VILLAGER.spawn(level, board.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, trader, board,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOP_COUNTER_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.POKEMON_TRADER);
				trader.setVillagerData(trader.getVillagerData().setLevel(5));
				var offer = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(trader).get(0);
				var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("pikachu level=30", " ", "=").create());
				// One that fits the first offer.
				for (var species : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getImplemented()) {
					boolean fits = false;
					for (var type : species.getTypes()) {
						fits |= type.getName().equals(offer.wanted().getName());
					}
					if (fits && species.getEvolutions().isEmpty()) {
						party.add(species.create(offer.minLevel() + 3));
						break;
					}
				}
				party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("eevee level=12", " ", "=").create());
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(level, 2.5, -60, 5.5, 135, 20);
				io.github.jcondedata.aliveworkplace.trader.PokemonTraders.open(player, trader);
			});
		}
		if (tick == 90) {
			mc.getToasts().clear();
			pointAt(mc, 0); // the first offer
		}
		if (tick == 100) {
			Showcase.check(mc.screen instanceof io.github.jcondedata.aliveworkplace.client.PokemonTradeScreen, "the trade screen opened");
			shot(mc, "01_trader_offer");
			pointAt(mc, 19); // the Pokémon that fits
		}
		if (tick == 120) {
			mc.getToasts().clear();
			shot(mc, "02_trader_party");
			pointAt(mc, 18); // one that doesn't
		}
		if (tick == 140) {
			shot(mc, "03_trader_refused");
			// The first click on the Pokémon that fits: pressed in, and the screen asks for the second (28.23).
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu instanceof io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu) {
					menu.press(19, player);
				}
			});
			pointAt(mc, 19);
		}
		if (tick == 160) {
			shot(mc, "04_trader_confirm");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu instanceof io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu) {
					menu.press(19, player);
				}
			});
			pointAt(mc, 8); // the trader's book
		}
		if (tick == 180) {
			mc.getToasts().clear();
			Showcase.check(mc.screen instanceof io.github.jcondedata.aliveworkplace.client.PokemonTradeScreen, "the trade went through on the trade screen");
			shot(mc, "05_trader_traded");
		}
		if (tick == 190) {
			mc.stop();
		}
	}

	/** Moves the mouse over slot {@code slot} of an open six-row chest screen. */
	static void pointAt(Minecraft mc, int slot) {
		pointAt(mc, slot, 6);
	}

	/** Moves the mouse over slot {@code slot} of an open chest screen with {@code rows} rows. */
	static void pointAt(Minecraft mc, int slot, int rows) {
		double scale = mc.getWindow().getGuiScale();
		if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen hall) {
			// The hall's own screen (ROADMAP 30.4a) puts its buttons where its drawing does, not on a chest's grid.
			int[] at = hall.centre(slot);
			setMouse(mc, at[0] * scale, at[1] * scale);
			return;
		}
		if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.PokemonTradeScreen trades) {
			// The Pokémon Trader's own screen (28.23): cards and a party panel, not a chest's grid.
			int[] at = trades.centre(slot);
			setMouse(mc, at[0] * scale, at[1] * scale);
			return;
		}
		int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
		int top = (mc.getWindow().getGuiScaledHeight() - (114 + rows * 18)) / 2;
		setMouse(mc, (left + 8 + (slot % 9) * 18 + 8) * scale, (top + 18 + (slot / 9) * 18 + 8) * scale);
	}

	/** Puts the mouse at window pixel ({@code x}, {@code y}); the harness has no real mouse. */
	private static void setMouse(Minecraft mc, double x, double y) {
		try {
			var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
			var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
			xpos.setAccessible(true);
			ypos.setAccessible(true);
			xpos.setDouble(mc.mouseHandler, x);
			ypos.setDouble(mc.mouseHandler, y);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	// --- Farm: a farmer harvests, replants, tills and sows a marked field ----------------------------

	private Villager farmer;

	private void farmScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
				level.setDayTime(2500);
				BlockPos water = new BlockPos(0, -61, -8);
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(-4, -61, -12), new BlockPos(4, -61, -4))) {
					if (p.equals(water)) {
						level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
					} else if (p.getZ() <= -7) {
						level.setBlock(p, Blocks.FARMLAND.defaultBlockState(), 2);
						var crop = p.getX() < 0 ? Blocks.WHEAT : Blocks.CARROTS;
						level.setBlock(p.above(), crop.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 2);
					}
				}
				BlockPos composter = new BlockPos(0, -60, 2);
				level.setBlockAndUpdate(composter, Blocks.COMPOSTER.defaultBlockState());
				level.setBlockAndUpdate(composter.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(composter.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.STONE_HOE));
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 16));
				farmer = EntityType.VILLAGER.spawn(level, composter.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, farmer, composter,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER, net.minecraft.world.entity.npc.VillagerProfession.FARMER);
				io.github.jcondedata.aliveworkplace.farm.Fields.start(level, farmer,
					net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(new BlockPos(-4, -61, -12), new BlockPos(4, -61, -4)));
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(9.5, -53, 4.5), 140, 35);
			});
		}
		if (tick > 60 && tick % 10 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(tick > 300 && farmer != null && io.github.jcondedata.aliveworkplace.farm.Fields.vanillaMayRun(farmer)));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick == doneAt + 30) {
			server.execute(() -> {
				int harvested = io.github.jcondedata.aliveworkplace.registry.ModAttachments.FARM_HARVESTED.getOrElse(farmer, 0);
				Showcase.check(harvested > 0, "the farmer harvested the field (" + harvested + " crops)");
			});
		}
		if (doneAt > 0 && tick == doneAt + 40) {
			shot(mc, "50_farm_done");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			giveUp(mc, "the farmer harvested and replanted the field");
		}
	}

	// --- Soak (ROADMAP 23.1): 10 builders, the whole starter set, hilly woods, no help: a time-lapse from above ---

	/** The soak's corner: away from the other scenes, high enough that its deepest stone stays above the world's floor. */
	private static final BlockPos SOAK_AT = new BlockPos(200, -56, 200);
	private static final int SOAK_DAYS = 6;
	/** Client ticks: 30 minutes, for 6 in-game days at 150+ server ticks a second plus the start. */
	private static final int SOAK_GIVE_UP = 36000;
	private static final int SOAK_STILL_EVERY = 600;
	private volatile String soakResult;
	private volatile int soakFinished = -1;
	private volatile int soakTotal;
	private volatile long soakTicks;
	private int soakDoneAt = -1;

	private void soakScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(8);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.graphicsMode().set(GraphicsStatus.FAST);
			mc.options.framerateLimit().set(15);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					"forceload add " + (SOAK_AT.getX() - 8) + " " + (SOAK_AT.getZ() - 8) + " " + (SOAK_AT.getX() + 152) + " " + (SOAK_AT.getZ() + 142));
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				// Night vision: the days go by in seconds, and the nights stay readable in the time-lapse.
				player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, -1, 0, false, false));
				hover(player, new Vec3(SOAK_AT.getX() + 70, SOAK_AT.getY() + 82, SOAK_AT.getZ() + 98), 180, 62);
			});
		}
		if (tick == 160) {
			server.execute(() -> {
				io.github.jcondedata.aliveworkplace.command.Soak.begin(server.overworld(), SOAK_AT, SOAK_DAYS);
				soakTotal = io.github.jcondedata.aliveworkplace.command.Soak.progress()[1];
				// Frozen while software rendering draws the new hills, so the first still shows the start.
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "tick freeze");
			});
		}
		// Software rendering takes about a minute to draw the new hills: the first still waits for them.
		if (tick == 1400) {
			shot(mc, "01_soak_start");
			server.execute(() -> {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "tick unfreeze");
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					"tick sprint " + (SOAK_DAYS * io.github.jcondedata.aliveworkplace.command.Soak.DAY));
			});
		}
		if (tick > 1400 && soakDoneAt < 0) {
			if (tick % SOAK_STILL_EVERY == 0) {
				shot(mc, String.format("frame_%03d", frame++));
			}
			if (tick % 20 == 0) {
				server.execute(() -> {
					int[] progress = io.github.jcondedata.aliveworkplace.command.Soak.progress();
					soakFinished = progress[0];
					soakTicks = server.overworld().getGameTime();
				});
			}
			if (soakTotal > 0 && soakFinished == soakTotal) {
				soakDoneAt = tick;
				server.execute(() -> {
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "tick sprint stop");
					soakResult = io.github.jcondedata.aliveworkplace.command.Soak.finish();
				});
			}
		}
		if (soakDoneAt > 0 && tick == soakDoneAt + 60) {
			String result = soakResult;
			// "items off: none" when every item adds up, then the end or the next part ("; village chunks: …", 23.6; B44).
			Showcase.check(result != null && result.contains(" " + soakTotal + "/" + soakTotal + " builds finished")
					&& java.util.regex.Pattern.compile("items off: none(;|$)").matcher(result).find(),
				"10 builders finished every starter build with nothing duplicated or lost: " + result);
			shot(mc, "02_soak_done");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0),
				new Vec3(SOAK_AT.getX() + 40, SOAK_AT.getY() + 30, SOAK_AT.getZ() + 80), 200, 30));
		}
		if (soakDoneAt > 0 && tick == soakDoneAt + 160) {
			shot(mc, "03_soak_close");
			mc.stop();
		}
		if (tick >= SOAK_GIVE_UP && soakDoneAt < 0) {
			server.execute(() -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("Soak scene gave up: {}",
				io.github.jcondedata.aliveworkplace.command.Soak.finish()));
			giveUp(mc, "10 builders finished every starter build (" + soakFinished + " of " + soakTotal + " at game tick " + soakTicks + ")");
		}
	}

	// --- Villages: one of each type, find the builder's workshops, one shot each ----------------

	private final List<BlockPos> workshops = new ArrayList<>();
	private final List<BlockPos> otherHouses = new ArrayList<>();
	private int villagesWithWorkshop;
	private int villagesBuilt;
	private int villageVoids;

	private void villageScene(Minecraft mc, MinecraftServer server) {
		tick++;
		List<String> styles = io.github.jcondedata.aliveworkplace.world.VillageHouses.STYLES;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.setDayTime(3000);
				for (int i = 0; i < styles.size(); i++) {
					int x = i * 256;
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
						"forceload add " + (x - 96) + " -96 " + (x + 96) + " 96");
				}
			});
		}
		if (tick == 400) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				for (int i = 0; i < styles.size(); i++) {
					int x = i * 256;
					net.minecraft.commands.CommandSource logger = new net.minecraft.commands.CommandSource() {
						@Override
						public void sendSystemMessage(net.minecraft.network.chat.Component message) {
							io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[village] command: {}", message.getString());
						}

						@Override
						public boolean acceptsSuccess() {
							return true;
						}

						@Override
						public boolean acceptsFailure() {
							return true;
						}

						@Override
						public boolean shouldInformAdmins() {
							return false;
						}
					};
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(logger),
						"place structure minecraft:village_" + styles.get(i) + " " + x + " -60 0");
					BlockPos found = null;
					int built = 0;
					int voids = 0; // structure_void placed as a real block leaves a hole with no collision (B5)
					for (BlockPos p : BlockPos.betweenClosed(x - 96, -62, -96, x + 96, -52, 96)) {
						BlockState st = level.getBlockState(p);
						if (st.is(Blocks.STRUCTURE_VOID)) {
							voids++;
						}
						if (st.is(ModBlocks.BLUEPRINT_TABLE) && found == null) {
							found = p.immutable();
						}
						for (net.minecraft.world.level.block.Block job : List.of(ModBlocks.TRAINING_POST, ModBlocks.MAILBOX, ModBlocks.SHOP_COUNTER, ModBlocks.STOREHOUSE, ModBlocks.TRAVEL_POST)) {
							if (st.is(job) && otherHouses.stream().noneMatch(h -> level.getBlockState(h).is(job))) {
								otherHouses.add(p.immutable());
							}
						}
						if (st.is(Blocks.BELL) || st.is(net.minecraft.tags.BlockTags.BEDS)) {
							built++;
						}
					}
					io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[village] {} village: {} bells/beds, {} structure_void, workshop bench at {}", styles.get(i), built, voids, found);
					if (found != null) {
						workshops.add(found);
						villagesWithWorkshop++;
					}
					villageVoids += voids;
					villagesBuilt += built > 0 ? 1 : 0;
				}
				workshops.addAll(otherHouses);
				findPokemonHouses(level);
				// Workshops are random (WORKSHOP_WEIGHT makes them likely, not certain): counted, not required.
				Showcase.check(villagesBuilt == styles.size() && villageVoids == 0, "every village type generated (" + villagesBuilt + " of "
					+ styles.size() + ", " + villagesWithWorkshop + " with a builder's workshop, " + villageVoids + " structure_void blocks)");
			});
		}
		int shots = workshops.size() + pokemonWorkers.size();
		if (tick >= 500 && (tick - 500) % 80 == 0 && (tick - 500) / 80 < shots) {
			int i = (tick - 500) / 80;
			BlockPos bench = i < workshops.size() ? workshops.get(i) : pokemonWorkers.get(i - workshops.size()).blockPosition();
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Vec3 target = Vec3.atCenterOf(bench);
				hoverLookingAt(player, clearView(server.overworld(), player, target), target);
			});
		}
		if (tick >= 560 && (tick - 560) % 80 == 0 && (tick - 560) / 80 < shots) {
			int i = (tick - 560) / 80;
			shot(mc, i < workshops.size() ? "40_workshop_" + i : "41_pokemon_" + (i - workshops.size()));
		}
		if (tick == 570 + Math.max(1, shots) * 80) {
			server.execute(this::checkPokemonHouses);
		}
		if (tick == 580 + Math.max(1, shots) * 80) {
			mc.stop();
		}
	}

	/** The workers of the Pokémon jobs' village houses found in the villages (ROADMAP 28.15), one per job at most. */
	private final List<Villager> pokemonWorkers = new ArrayList<>();

	/** The Pokémon jobs' houses: their workers, by the job each house's villager comes with. */
	private static final List<net.minecraft.world.entity.npc.VillagerProfession> POKEMON_HOUSE_JOBS = List.of(
		io.github.jcondedata.aliveworkplace.registry.ModVillagers.NURSE, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CAMP_COOK,
		io.github.jcondedata.aliveworkplace.registry.ModVillagers.BERRY_BREEDER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.DAYCARE_KEEPER,
		io.github.jcondedata.aliveworkplace.registry.ModVillagers.GEM_GROWER);

	/** Finds the Pokémon jobs' houses the villages grew (with Cobblemon): a villager of one of their jobs, the first of each. */
	private void findPokemonHouses(ServerLevel level) {
		if (!io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon")) {
			return;
		}
		List<String> styles = io.github.jcondedata.aliveworkplace.world.VillageHouses.STYLES;
		for (int i = 0; i < styles.size(); i++) {
			net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(i * 256 - 96, -64, -96, i * 256 + 96, -40, 96);
			for (Villager v : level.getEntitiesOfClass(Villager.class, box)) {
				var job = v.getVillagerData().getProfession();
				if (POKEMON_HOUSE_JOBS.contains(job) && pokemonWorkers.stream().noneMatch(w -> w.getVillagerData().getProfession() == job)) {
					pokemonWorkers.add(v);
					io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[village] {} village: a {} at {}", styles.get(i), job, v.blockPosition());
				}
			}
		}
	}

	/** With Cobblemon: a Pokémon jobs' house grew, and each such worker still has the job and stands free and unhurt (B6). */
	private void checkPokemonHouses() {
		if (!io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon")) {
			return;
		}
		List<String> problems = new ArrayList<>();
		for (Villager v : pokemonWorkers) {
			if (!v.isAlive() || v.isInWall() || v.getHealth() < v.getMaxHealth() || !POKEMON_HOUSE_JOBS.contains(v.getVillagerData().getProfession())) {
				problems.add(v.getVillagerData().getProfession() + " at " + v.blockPosition() + (v.isInWall() ? " in a wall" : "") + " health " + v.getHealth());
			}
		}
		Showcase.check(!pokemonWorkers.isEmpty() && problems.isEmpty(), "a Pokémon jobs' house grew in a Cobblemon village with its worker in the job, standing free ("
			+ pokemonWorkers.stream().map(w -> String.valueOf(w.getVillagerData().getProfession())).toList() + (problems.isEmpty() ? "" : "; " + problems) + ")");
	}

	// --- Fish: a fisherman casting into a pond (the bobber and its line) --------------------------

	private void fishScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos barrel = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(2, -61, 2), new BlockPos(6, -61, 6))) {
					level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
				}
				Villager fisher = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 1), MobSpawnType.COMMAND);
				worker = fisher;
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, fisher, barrel, net.minecraft.world.entity.ai.village.poi.PoiTypes.FISHERMAN,
					net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN);
				io.github.jcondedata.aliveworkplace.fish.Fishers.start(level, fisher, new ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(-3.5, -57.2, 7.5), 215, 28);
			});
		}
		for (int i = 0; i < 6; i++) {
			if (tick == 120 + i * 30) {
				shot(mc, "60_fish_" + i);
			}
		}
		if (tick >= 320 && tick % 20 == 0 && doneAt < 0) {
			server.execute(() -> allDone.set(worker != null && io.github.jcondedata.aliveworkplace.registry.ModAttachments.FISH_CAUGHT.getOrElse(worker, 0) >= 1));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick == doneAt + 20) {
			shot(mc, "61_fish_caught");
			Showcase.check(true, "the fisherman caught a fish");
			mc.stop();
		}
		if (tick >= 3600 && doneAt < 0) {
			giveUp(mc, "the fisherman caught a fish");
		}
	}

	// --- Extras: a fisher out in a boat, a guard on horseback, a ferry ride -------------------------

	private boolean boated;
	private boolean rode;

	private void extrasScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(8);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, server);
				level.setDayTime(2500);
				// A: a fisher with a boat by a lake
				BlockPos barrel = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
				if (level.getBlockEntity(barrel) instanceof BaseContainerBlockEntity c) {
					c.setItem(0, new ItemStack(net.minecraft.world.item.Items.SPRUCE_BOAT));
				}
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(3, -61, 3), new BlockPos(22, -61, 22))) {
					level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
				}
				Villager fisher = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 1), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, fisher, barrel, net.minecraft.world.entity.ai.village.poi.PoiTypes.FISHERMAN,
					net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN);
				io.github.jcondedata.aliveworkplace.fish.Fishers.start(level, fisher, new ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
				// B: a guard with a saddled horse by the post
				BlockPos post = new BlockPos(48, -60, 0);
				level.setBlockAndUpdate(post, Blocks.GRINDSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				BlockPos chest = post.south(2);
				level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
				if (level.getBlockEntity(chest) instanceof BaseContainerBlockEntity c) {
					c.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
					c.setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
					c.setItem(2, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
					c.setItem(3, new ItemStack(net.minecraft.world.item.Items.SHIELD));
				}
				Villager guard = EntityType.VILLAGER.spawn(level, post.offset(1, 0, 1), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, guard, post, net.minecraft.world.entity.ai.village.poi.PoiTypes.WEAPONSMITH,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
				net.minecraft.world.entity.animal.horse.Horse horse = EntityType.HORSE.spawn(level, post.offset(4, 0, 4), MobSpawnType.COMMAND);
				horse.setTamed(true);
				horse.equipSaddle(new ItemStack(net.minecraft.world.item.Items.SADDLE), null);
				// C: two travel posts, a ferryman at the first, and a pond to row across
				BlockPos harbor = new BlockPos(96, -60, 0);
				BlockPos far = new BlockPos(96, -60, 48);
				level.setBlockAndUpdate(harbor, ModBlocks.TRAVEL_POST.defaultBlockState());
				level.setBlockAndUpdate(far, ModBlocks.TRAVEL_POST.defaultBlockState());
				var network = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.get(server);
				network.add(net.minecraft.core.GlobalPos.of(level.dimension(), harbor), "Harbor");
				var farPost = network.add(net.minecraft.core.GlobalPos.of(level.dimension(), far), "Far Shore");
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(92, -61, 3), new BlockPos(104, -61, 20))) {
					level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
				}
				Villager ferryman = EntityType.VILLAGER.spawn(level, harbor.offset(1, 0, 1), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, ferryman, harbor, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAVEL_POST_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.FERRYMAN);
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				network.visit(player.getUUID(), farPost);
				hoverLookingAt(player, new Vec3(-5, -54, -5), new Vec3(11, -61, 11));
			});
		}
		for (int i = 0; i < 6; i++) {
			if (tick == 240 + i * 60) {
				shot(mc, "70_boat_" + i);
			}
		}
		if (tick >= 400 && tick < 600) {
			// Close up on the fisher once he's out in the boat (the wide view from above before that)
			Villager fisher = mc.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(new BlockPos(12, -61, 12)).inflate(16),
				v -> v.getVehicle() != null).stream().findFirst().orElse(null);
			if (fisher != null) {
				boated = true;
				follow(mc, fisher.getVehicle(), 4.5, 0, 1.6);
			} else {
				stopFollowing(mc, net.minecraft.client.CameraType.FIRST_PERSON);
			}
		}
		if (tick == 600) {
			stopFollowing(mc, net.minecraft.client.CameraType.FIRST_PERSON);
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Villager guard = server.overworld().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(new BlockPos(48, -60, 0)).inflate(30),
					v -> v.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD).stream().findFirst().orElse(null);
				Vec3 at = guard != null ? guard.position() : new Vec3(48, -60, 0);
				hoverLookingAt(player, at.add(6, 4, -6), at.add(0, 1, 0));
			});
		}
		if (tick >= 620 && tick < 740) {
			// The mounted guard from the side, from the front and from further off, the camera following the horse
			Villager rider = mc.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(new BlockPos(48, -60, 0)).inflate(40),
				v -> v.getVehicle() != null).stream().findFirst().orElse(null);
			if (rider != null) {
				rode = true;
				int view = tick < 650 ? 0 : tick < 690 ? 1 : 2;
				switch (view) {
					case 0 -> follow(mc, rider.getVehicle(), 4.5, 0, 1.8);
					case 1 -> follow(mc, rider.getVehicle(), 2.5, 3.5, 2.0);
					default -> follow(mc, rider.getVehicle(), -6, -3, 3.5);
				}
			}
		}
		for (int i = 0; i < 3; i++) {
			if (tick == 640 + i * 40) {
				shot(mc, "71_cavalry_" + i);
			}
		}
		if (tick == 740) {
			stopFollowing(mc, net.minecraft.client.CameraType.FIRST_PERSON);
		}
		if (tick == 780) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(server.overworld(), 97.5, -61, 5.5, 180f, 15f);
			});
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}
		if (tick == 820) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				var network = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.get(server);
				var far = network.known(player.getUUID(), null).stream().filter(p -> p.name().equals("Far Shore")).findFirst().orElseThrow();
				io.github.jcondedata.aliveworkplace.travel.Ferrymen.travel(player, io.github.jcondedata.aliveworkplace.travel.Ferrymen.ticket(far));
			});
		}
		if (tick >= 824 && tick < 856 && mc.player.getVehicle() != null) {
			// The boat from the side, the front quarter and the front (the ferryman at the oars, the player behind).
			// The player's own third-person camera turned round: another camera entity would hide the player.
			int view = tick < 834 ? 0 : tick < 844 ? 1 : 2;
			float yaw = mc.player.getVehicle().getYRot() + (view == 0 ? 90 : view == 1 ? 40 : 10);
			mc.setCameraEntity(mc.player);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
			mc.player.setYRot(yaw);
			mc.player.yRotO = yaw;
			mc.player.setYHeadRot(yaw);
			mc.player.yHeadRotO = yaw;
			mc.player.setXRot(-25);
			mc.player.xRotO = -25;
		}
		for (int i = 0; i < 3; i++) {
			if (tick == 832 + i * 10) {
				shot(mc, "72_ferry_" + i);
			}
		}
		if (tick == 856) {
			stopFollowing(mc, net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}
		if (tick == 920) {
			shot(mc, "72_ferry_arrived");
			Showcase.check(boated, "the fisher went out in the boat");
			Showcase.check(rode, "the guard rode his horse");
			double far = mc.player.position().distanceTo(new Vec3(96.5, -60, 48.5));
			Showcase.check(far < 16, String.format("the ferry reached the Far Shore (%.0f blocks from its post)", far));
		}
		if (tick == 940) {
			mc.stop();
		}
	}

	// --- Gallery: every starter blueprint placed instantly, one shot each -------------------------

	/** The 12 workplaces (ROADMAP 27.11): the village houses a builder can build, Cobblemon's five too. */
	private static List<StarterBlueprints.Entry> workplacesGallery() {
		List<StarterBlueprints.Entry> out = new java.util.ArrayList<>(StarterBlueprints.WORKPLACES);
		out.addAll(StarterBlueprints.COBBLEMON_WORKPLACES);
		return out;
	}

	private void galleryScene(Minecraft mc, MinecraftServer server) {
		tick++;
		List<StarterBlueprints.Entry> all = "decor".equals(System.getProperty("aliveworkplace.scene")) ? StarterBlueprints.DECORATIONS
			: "defences".equals(System.getProperty("aliveworkplace.scene")) ? StarterBlueprints.DEFENCES
			: "workshops".equals(System.getProperty("aliveworkplace.scene")) ? List.of(StarterBlueprints.TINKERS_WORKSHOP, StarterBlueprints.TINKERS_WORKSHOP_2, StarterBlueprints.NETHER_GATE, StarterBlueprints.NETHER_GATE_2)
			: "styles".equals(System.getProperty("aliveworkplace.scene")) ? styledGallery()
			: "workplaces".equals(System.getProperty("aliveworkplace.scene")) ? workplacesGallery()
			// every starter blueprint: the tiered ones, then those with no upgrade (the Map Room and Bandstand, ROADMAP 27.13, 27.14)
			: java.util.stream.Stream.concat(StarterBlueprints.ALL.stream(), StarterBlueprints.ONE_TIER.stream()).toList();
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(3000);
				for (int i = 0; i < all.size(); i++) {
					BlockPos origin = new BlockPos(i * GALLERY_SPACING, -60, 0);
					if (io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.parse(all.get(i).id()).isPresent()) {
						// A styled blueprint isn't a structure file: set its blocks one by one.
						var blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, all.get(i).id()).orElseThrow();
						for (var e : blueprint.blocks()) {
							level.setBlock(origin.offset(e.pos()), e.state(), 2);
						}
						continue;
					}
					net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate t =
						level.getStructureManager().get(all.get(i).id()).orElseThrow();
					t.placeInWorld(level, origin, origin, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
						level.getRandom(), 2);
					if (BlockPos.betweenClosedStream(origin, origin.offset(all.get(i).size()).offset(-1, -1, -1))
						.allMatch(p -> level.getBlockState(p).isAir())) {
						emptyBuilds.add(all.get(i).id().getPath());
					}
					if (all.get(i).id().getPath().startsWith("fishers_hut")) {
						// On a shore, as the Steward puts it (city/Plots): two deep water under the jetty and out in front, and
						// the jetty's log posts down to the bed, as a builder takes them.
						for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, -2, -5), origin.offset(all.get(i).size().getX() + 1, -1, 4))) {
							BlockState above = level.getBlockState(origin.offset(p.getX() - origin.getX(), 0, p.getZ() - origin.getZ()));
							level.setBlock(p, above.is(net.minecraft.tags.BlockTags.LOGS) ? above : net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
						}
					}
					if (all.get(i).id().getPath().startsWith("nether_gate")) {
						// Lit, as the builder leaves it when there's a flint and steel in the chests.
						net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, origin.offset(4, 2, 3), Direction.Axis.X)
							.ifPresent(net.minecraft.world.level.portal.PortalShape::createPortalBlocks);
					}
				}
			});
		}
		if (tick % 20 == 0) {
			// Slimes from the superflat world's slime chunks hop into the shots.
			server.execute(() -> {
				for (net.minecraft.world.entity.Entity e : server.overworld().getAllEntities()) {
					if (e instanceof net.minecraft.world.entity.monster.Slime) {
						e.discard();
					}
				}
			});
		}
		int index = (tick - 60) / 60;
		if (tick == 40) {
			// Look at the first build early so its chunks are drawn by the first shot.
			StarterBlueprints.Entry e = all.get(0);
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0), galleryCenter(0, e).add(8, 6, -14), galleryCenter(0, e)));
		}
		if (tick >= 60 && (tick - 60) % 60 == 0 && index < all.size()) {
			StarterBlueprints.Entry e = all.get(index);
			Vec3 center = galleryCenter(index, e);
			double dist = Math.max(Math.max(e.size().getX(), e.size().getZ()), e.size().getY()) * 0.75 + 5;
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0),
				center.add(dist * 0.55, e.size().getY() * 0.35 + 2, -dist), center));
		}
		if (tick >= 100 && (tick - 100) % 60 == 0 && (tick - 100) / 60 < all.size()) {
			shot(mc, "30_" + all.get((tick - 100) / 60).id().getPath().replace('/', '_'));
		}
		// And from behind, where upgrades often add their part.
		if (tick >= 105 && (tick - 105) % 60 == 0 && (tick - 105) / 60 < all.size()) {
			int i = (tick - 105) / 60;
			StarterBlueprints.Entry e = all.get(i);
			Vec3 center = galleryCenter(i, e);
			double dist = Math.max(Math.max(e.size().getX(), e.size().getZ()), e.size().getY()) * 0.75 + 5;
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0),
				center.add(-dist * 0.55, e.size().getY() * 0.35 + 3, dist), center));
		}
		if (tick >= 118 && (tick - 118) % 60 == 0 && (tick - 118) / 60 < all.size()) {
			shot(mc, "31_" + all.get((tick - 118) / 60).id().getPath().replace('/', '_') + "_back");
		}
		if (tick == 30 || tick == 90) {
			// Materials tooltip: the first call asks the server, a later one has the answer.
			for (Component line : io.github.jcondedata.aliveworkplace.client.BlueprintTooltip.materialLines(StarterBlueprints.HEALING_CENTER.id())) {
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[tooltip] {}", line.getString());
			}
		}
		if (tick == 100 + all.size() * 60) {
			Showcase.check(emptyBuilds.isEmpty(), emptyBuilds.isEmpty() ? "all " + all.size() + " builds were placed"
				: "these builds placed nothing: " + String.join(", ", emptyBuilds));
			mc.stop();
		}
	}

	private static final int GALLERY_SPACING = 48;
	private final List<String> emptyBuilds = java.util.Collections.synchronizedList(new ArrayList<>());

	/** The Starter Cottage II and the Stone House II as drawn and in every style. */
	private static List<StarterBlueprints.Entry> styledGallery() {
		List<StarterBlueprints.Entry> out = new java.util.ArrayList<>();
		for (StarterBlueprints.Entry base : List.of(StarterBlueprints.STARTER_COTTAGE_2, StarterBlueprints.STONE_HOUSE_2)) {
			out.add(base);
			for (var style : io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.all()) {
				out.add(new StarterBlueprints.Entry(io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.styled(base.id(), style.name()), base.size()));
			}
		}
		return out;
	}

	/** The middle of the gallery's {@code index}th build. */
	private static Vec3 galleryCenter(int index, StarterBlueprints.Entry e) {
		return new Vec3(index * GALLERY_SPACING + e.size().getX() / 2.0, -60 + e.size().getY() * 0.4, e.size().getZ() / 2.0);
	}

	/** Hovers at {@code pos} looking at {@code target}. */
	/**
	 * ROADMAP 24.3: a camera spot that sees {@code target} (or the building around it) with nothing in between (the village scene's fixed spot was
	 * sometimes inside the next house, filling the still with a log). Tries the four diagonals at a few distances and
	 * heights, the south-east first (the old spot).
	 */
	static Vec3 clearView(ServerLevel level, ServerPlayer player, Vec3 target) {
		int[][] dirs = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
		for (double height : new double[] {6, 9, 13}) {
			for (double dist : new double[] {9, 12, 7}) {
				for (int[] dir : dirs) {
					Vec3 cam = target.add(dir[0] * dist, height, dir[1] * dist);
					if (!level.getBlockState(BlockPos.containing(cam)).isAir()) {
						continue;
					}
					var hit = level.clip(new net.minecraft.world.level.ClipContext(cam, target, net.minecraft.world.level.ClipContext.Block.COLLIDER,
						net.minecraft.world.level.ClipContext.Fluid.NONE, player));
					if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceTo(target) < 6) {
						return cam;
					}
				}
			}
		}
		return target.add(9, 13, 9);
	}

	static void hoverLookingAt(ServerPlayer player, Vec3 pos, Vec3 target) {
		Vec3 d = target.subtract(pos);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		hover(player, pos, yaw, pitch);
	}

	static void hover(ServerPlayer player, Vec3 pos, float yaw, float pitch) {
		player.setGameMode(GameType.CREATIVE);
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, yaw, pitch);
	}

	/** A still camera that isn't the player (an armor stand only the client knows about). */
	private net.minecraft.world.entity.decoration.ArmorStand camera;

	/**
	 * Films {@code subject} from {@code side} blocks to its right, {@code ahead} blocks in front of it and {@code up} blocks
	 * above, going by the way it faces (a moving horse or boat stays in the frame).
	 */
	private void follow(Minecraft mc, net.minecraft.world.entity.Entity subject, double side, double ahead, double up) {
		Vec3 forward = Vec3.directionFromRotation(0, subject.getYRot());
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 at = subject.position().add(0, 0.8, 0);
		Vec3 eye = at.add(right.scale(side)).add(forward.scale(ahead)).add(0, up - 0.8, 0);
		if (camera == null || camera.level() != mc.level) {
			camera = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, eye.x, eye.y, eye.z);
			camera.setInvisible(true);
		}
		Vec3 d = at.subtract(eye);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		camera.moveTo(eye.x, eye.y - camera.getEyeHeight(), eye.z, yaw, pitch);
		camera.setYHeadRot(yaw);
		mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		mc.setCameraEntity(camera);
	}

	private void stopFollowing(Minecraft mc, net.minecraft.client.CameraType type) {
		mc.setCameraEntity(mc.player);
		mc.options.setCameraType(type);
	}

	/** The scene ran out of time: the last picture, a failed check, and quit. */
	private static void giveUp(Minecraft mc, String what) {
		shot(mc, "99_timeout");
		Showcase.check(false, what + " (gave up at the time limit)");
		mc.stop();
	}

	static void shot(Minecraft mc, String name) {
		titleFits(mc, name);
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {
		});
	}

	// --- server-side scene ------------------------------------------------------------------

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		GameRules rules = level.getGameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		rules.getRule(ModGameRules.BUILD_DELAY).set(4, server);
		level.setDayTime(2500);
		level.setWeatherParameters(12000, 0, false, false);

		site(level, StarterBlueprints.STARTER_COTTAGE, new BlockPos(0, -60, 0));
		site(level, StarterBlueprints.MARKET_STALL, new BlockPos(-12, -60, 0));
		site(level, StarterBlueprints.LOOKOUT_TOWER, new BlockPos(12, -60, 0));
		camera(server, WIDE, 180, 22);
	}

	private BlueprintData.Placement site(ServerLevel level, StarterBlueprints.Entry entry, BlockPos anchor) {
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), anchor,
			BlueprintItem.rotationFacing(Direction.SOUTH));
		BuildPlan plan = BuildPlan.create(blueprint, placement);

		BlockPos bench = anchor.offset(3, 0, 4);
		level.setBlockAndUpdate(bench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int b = 0; b * 27 < stock.size(); b++) {
			BlockPos barrelPos = bench.offset(1 + b, 0, 0);
			level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
			BaseContainerBlockEntity barrel = (BaseContainerBlockEntity) level.getBlockEntity(barrelPos);
			for (int slot = 0; slot < 27 && b * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(b * 27 + slot));
			}
		}

		Villager villager = EntityType.VILLAGER.spawn(level, anchor.offset(0, 0, 3), MobSpawnType.COMMAND);
		if (villager == null) {
			return placement;
		}
		Builders.employ(level, villager, bench);
		Builders.start(level, villager, level.getServer().getPlayerList().getPlayers().get(0), entry.id(), placement);
		builders.add(villager);
		return placement;
	}

	private void closeUp(MinecraftServer server) {
		if (builders.isEmpty()) {
			return;
		}
		Villager v = builders.get(0);
		Vec3 eye = v.getEyePosition();
		Vec3 cam = eye.add(1.6, 0.4, 2.4);
		Vec3 d = eye.subtract(cam);
		float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
		v.setNoAi(true); // hold still for the portrait
		camera(server, cam, yaw, pitch);
		server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 130, () -> v.setNoAi(false)));
	}

	private static void camera(MinecraftServer server, Vec3 pos, float yaw, float pitch) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		player.setGameMode(GameType.SPECTATOR);
		player.teleportTo(server.overworld(), pos.x, pos.y, pos.z, yaw, pitch);
	}
}
