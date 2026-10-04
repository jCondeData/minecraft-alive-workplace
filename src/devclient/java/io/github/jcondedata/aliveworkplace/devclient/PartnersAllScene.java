package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=partners_all (ROADMAP 28.6, with Cobblemon): partners at work for everyone else. Twelve workers stand at their
 * workstations, each with a partner pastured beside them; one by one each worker's moment of work is cued, and the
 * camera takes a still of the partner at it (digging, swimming round the bobber, a floating book, a pink pulse...).
 */
final class PartnersAllScene {
	private record Stop(String name, Supplier<Block> station, ResourceKey<PoiType> poi, Supplier<VillagerProfession> job, String species,
						String cue, BlockPos work, ItemStack handling) {
	}

	/** Each job's corner of the grid: 10 blocks apart, 4 across and 3 deep. */
	private static BlockPos corner(int i) {
		return new BlockPos((i % 4) * 10 - 15, -60, (i / 4) * 10 - 10);
	}

	private static final List<Stop> STOPS = List.of(
		new Stop("miner", () -> ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, () -> ModVillagers.MINER, "geodude", "dig",
			new BlockPos(3, 0, 0), new ItemStack(Items.STONE)),
		new Stop("fisherman", () -> Blocks.BARREL, PoiTypes.FISHERMAN, () -> VillagerProfession.FISHERMAN, "psyduck", "cast",
			new BlockPos(3, -1, 0), ItemStack.EMPTY),
		new Stop("scholar", () -> ModBlocks.SCHOLARS_DESK, ModVillagers.SCHOLARS_DESK_POI, () -> ModVillagers.SCHOLAR, "abra", "study",
			new BlockPos(0, 0, 0), ItemStack.EMPTY),
		new Stop("teacher", () -> ModBlocks.TEACHERS_DESK, ModVillagers.TEACHERS_DESK_POI, () -> ModVillagers.TEACHER, "chansey", "lesson",
			new BlockPos(0, 0, 0), ItemStack.EMPTY),
		new Stop("nurse", () -> ModBlocks.NURSE_STATION, ModVillagers.NURSE_STATION_POI, () -> ModVillagers.NURSE, "clefairy", "cure",
			new BlockPos(3, 0, 0), ItemStack.EMPTY),
		new Stop("composter", () -> ModBlocks.COMPOST_BIN, ModVillagers.COMPOST_BIN_POI, () -> ModVillagers.COMPOSTER, "grimer", "compost",
			new BlockPos(0, 0, 0), ItemStack.EMPTY),
		new Stop("florist", () -> ModBlocks.FLOWER_STAND, ModVillagers.FLOWER_STAND_POI, () -> ModVillagers.FLORIST, "oddish", "grow",
			new BlockPos(3, 0, 0), ItemStack.EMPTY),
		new Stop("beekeeper", () -> ModBlocks.APIARY, ModVillagers.APIARY_POI, () -> ModVillagers.BEEKEEPER, "combee", "harvest",
			new BlockPos(3, 0, 0), ItemStack.EMPTY),
		new Stop("sifter", () -> ModBlocks.SIEVE, ModVillagers.SIEVE_POI, () -> ModVillagers.SIFTER, "sandshrew", "sift",
			new BlockPos(0, 0, 0), new ItemStack(Items.GRAVEL)),
		new Stop("netherworker", () -> ModBlocks.NETHER_BRAZIER, ModVillagers.NETHER_BRAZIER_POI, () -> ModVillagers.NETHERWORKER, "houndour", "depart",
			new BlockPos(3, 0, 0), ItemStack.EMPTY),
		new Stop("cartographer", () -> Blocks.CARTOGRAPHY_TABLE, PoiTypes.CARTOGRAPHER, () -> VillagerProfession.CARTOGRAPHER, "pidgey", "set_out",
			new BlockPos(4, 0, 2), ItemStack.EMPTY),
		new Stop("rancher", () -> ModBlocks.FEED_TROUGH, ModVillagers.FEED_TROUGH_POI, () -> ModVillagers.RANCHER, "tauros", "tame",
			new BlockPos(3, 0, 0), ItemStack.EMPTY));
	/** The longest a stop waits for its partner to arrive and start the show before the still is taken anyway. */
	private static final int SHOT_BY = 200;
	private static final int START = 200;

	private int tick;
	private final List<Villager> workers = new ArrayList<>();
	private volatile int out;
	private volatile boolean ready;
	/** The stop being filmed, when it started, and whether its partner is mid-show now. */
	private int stopIndex;
	private int stopAt = START;
	private volatile boolean playing;
	private int pendingShot = -1;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> setUp(server));
		}
		// Wait for every partner to come out of its pasture (up to 15 s).
		if (tick >= 60 && !ready && tick % 10 == 0) {
			server.execute(() -> out = server.overworld().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class,
				new AABB(corner(0)).inflate(60), e -> e.getTethering() != null).size());
			if (out >= STOPS.size()) {
				ready = true;
				tick = START - 20;
			} else if (tick > 360) {
				Showcase.check(false, "every partner came out of its pasture (" + out + " of " + STOPS.size() + ")");
				mc.stop();
			}
		}
		if (!ready || tick < START) {
			return;
		}
		int i = stopIndex;
		int at = tick - stopAt;
		if (i >= STOPS.size()) {
			if (at == 10) {
				server.execute(() -> {
					boolean leftover = !server.overworld().getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class,
						new AABB(corner(0)).inflate(60), d -> d.getTags().contains(PartnerShows.DISPLAY_TAG)).isEmpty();
					Showcase.check(!leftover && PartnerShows.running(server.overworld()) == 0, "every show ended, nothing left behind");
				});
			}
			if (at == 30) {
				mc.stop();
			}
			return;
		}
		Stop stop = STOPS.get(i);
		BlockPos base = corner(i);
		if (at == 0) {
			playing = false;
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Vec3 work = Vec3.atCenterOf(base.offset(stop.work()));
				Vec3 middle = Vec3.atCenterOf(base).add(work).scale(0.5).add(-1, 0, 1);
				ScreenshotHarness.hoverLookingAt(player, middle.add(1, 4.5, 8), middle);
			});
		}
		if (at == 5) {
			// Back by its pasture first: a partner that wandered off (out of the worker's reach) would miss its cue.
			server.execute(() -> server.overworld().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class,
				new AABB(base).inflate(30), e -> e.getTethering() != null && e.getPokemon().getSpecies().getName().equalsIgnoreCase(stop.species()))
				.forEach(e -> e.teleportTo(base.getX() - 2.5, base.getY(), base.getZ() + 2.5)));
		}
		if (at == 10) {
			server.execute(() -> {
				boolean on = PartnerShows.cue(workers.get(i), stop.cue(), base.offset(stop.work()), stop.handling());
				Showcase.check(on, "the " + stop.name() + "'s " + stop.cue() + " cued the " + stop.species() + (on ? "" : ": " + PartnerShows.lastRefusal()));
			});
		}
		if (at > 10 && at % 2 == 0) {
			server.execute(() -> playing = server.overworld().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class,
				new AABB(base).inflate(12), e -> e.getPokemon().getSpecies().getName().equalsIgnoreCase(stop.species())).stream()
				.anyMatch(PartnerShows::playing));
		}
		// The still: once the partner is at the work, a moment into its show (or at the time limit).
		if (at > 10 && (playing && at % 2 == 1 || at >= SHOT_BY)) {
			if (!playing) {
				Showcase.check(false, "the " + stop.species() + " reached the " + stop.name() + "'s work in time");
			}
			int wait = playing ? 12 : 0;
			stopAt = Integer.MAX_VALUE / 2; // paused: the shot is taken below, after a short wait for the effect
			pendingShot = tick + wait;
		}
		if (pendingShot > 0 && tick == pendingShot) {
			ScreenshotHarness.shot(mc, String.format("%02d_%s", 10 + i, stop.name()));
			pendingShot = -1;
			stopIndex++;
			stopAt = tick + 40; // the next stop, after this show has gone back
		}
	}

	private void setUp(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(3000);
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		for (int i = 0; i < STOPS.size(); i++) {
			Stop stop = STOPS.get(i);
			BlockPos base = corner(i);
			level.setBlockAndUpdate(base, stop.station().get().defaultBlockState());
			Villager worker = EntityType.VILLAGER.spawn(level, base.south(), MobSpawnType.COMMAND);
			Jobs.employ(level, worker, base, stop.poi(), stop.job().get());
			worker.setNoAi(true); // stands at the station for the pictures
			workers.add(worker);
			BlockPos work = base.offset(stop.work());
			switch (stop.name()) {
				case "miner" -> level.setBlockAndUpdate(work, Blocks.STONE.defaultBlockState());
				case "fisherman" -> {
					for (BlockPos p : BlockPos.betweenClosed(work.offset(-1, 0, -1), work.offset(1, 0, 1))) {
						level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
					}
				}
				case "nurse" -> {
					Villager patient = EntityType.VILLAGER.spawn(level, work, MobSpawnType.COMMAND);
					patient.setNoAi(true);
				}
				case "florist" -> {
					for (BlockPos p : BlockPos.betweenClosed(work.offset(-1, 0, -1), work.offset(1, 0, 1))) {
						level.setBlockAndUpdate(p, (p.getX() + p.getZ()) % 2 == 0 ? Blocks.POPPY.defaultBlockState() : Blocks.DANDELION.defaultBlockState());
					}
				}
				case "beekeeper" -> level.setBlockAndUpdate(work, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
				case "netherworker" -> {
					for (int y = 0; y < 3; y++) {
						level.setBlockAndUpdate(work.offset(0, y, -1), Blocks.OBSIDIAN.defaultBlockState());
						level.setBlockAndUpdate(work.offset(0, y, 2), Blocks.OBSIDIAN.defaultBlockState());
					}
				}
				case "rancher" -> {
					var horse = EntityType.HORSE.spawn(level, work, MobSpawnType.COMMAND);
					if (horse != null) {
						horse.setNoAi(true);
					}
				}
				default -> {
				}
			}
			BlockPos pasture = base.offset(-4, 0, 2);
			pasture(level, player, pasture, stop.species() + " level=25");
		}
		ScreenshotHarness.hover(player, Vec3.atCenterOf(corner(0)).add(1.5, 3.5, 8.5), 180, 20);
	}

	private static void pasture(ServerLevel level, ServerPlayer player, BlockPos at, String species) {
		level.setBlockAndUpdate(at, ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
			"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
		level.setBlockAndUpdate(at.above(), ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
			"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
		var pokemon = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(species, " ", "=").create();
		com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
		if (level.getBlockEntity(at) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
			pen.tether(player, pokemon, Direction.EAST);
		}
	}
}
