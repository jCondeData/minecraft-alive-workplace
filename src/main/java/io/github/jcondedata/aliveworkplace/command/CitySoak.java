package io.github.jcondedata.aliveworkplace.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialLedger;
import io.github.jcondedata.aliveworkplace.build.StallWatch;
import io.github.jcondedata.aliveworkplace.city.CaravanRoads;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.city.StewardCost;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /workplace city}: the 1.1 yardstick (ROADMAP 27.22), as {@link Soak} is for builders. Lays out a plains village
 * round a Village Hall: a Steward set to Run the village, 3 builders at their tables, a storehouse with its porter and
 * stocked chests, 12 more villagers and an old vanilla house; a plan with Homes (renewing old houses), Workshops, Farms,
 * Market, Gardens and a Keep Clear square round the hall, two streets and a wall line, and a raid on record so the
 * village wants its wall. Then the Steward runs the village for 6 in-game days (or the days given), on his own.
 *
 * <p>The storehouse is kept stocked the way a player keeps it: when the Steward opens a build, its materials go into the
 * storehouse's chests, and count as stocked. After the days are up he starts nothing new (Ask first, roads and walls
 * opening no more segments) and the builders get up to {@link #WIND_DOWN_DAYS} more days to finish what he started.
 * Then one {@code City result:} line: builds finished, any outside its zone or in Keep Clear, stalls ({@link
 * StallWatch}), every item whose count doesn't add up ({@link MaterialLedger}) and the Steward's cost a tick
 * ({@link StewardCost}: p50, p95, p99 and the worst tick). Only registered with {@code -Daliveworkplace.benchmark=true}.
 */
public final class CitySoak {
	public static final int DAYS = 6;
	/** Days the builders get after the Steward's days to finish what he started. */
	public static final int WIND_DOWN_DAYS = 2;
	public static final String RESULT = "City result:";
	/** The ground laid out round the hall, in blocks each way (the plan's grid is 128 across: 64 each way). */
	public static final int REACH = 72;
	/** The two streets: east-west along z = +10, north-south along x = +10, from -56 to +56. */
	public static final int STREET = 10;
	public static final int STREET_END = 56;
	/** The wall line: a closed square 60 blocks out. */
	public static final int WALL = 60;
	/** Keep Clear: the square round the hall, -8 to +7 each way. */
	public static final int SQUARE = 8;
	public static final int VILLAGERS = 12;
	public static final int BUILDERS = 3;
	/** Where the old vanilla house stands in the Homes zone, from the hall. */
	static final BlockPos OLD_HOUSE = new BlockPos(-44, -1, -44);
	/** Chests round the storehouse: odd x from -7 to -1 (a gap between columns, so none join), z 1 to 7, two layers. */
	static final BlockPos STOREHOUSE = new BlockPos(-4, 0, 0);

	/** What a build the Steward started is. */
	public enum Kind {
		BUILDING, ROAD, WALL, TAKE_DOWN
	}

	/** One build the Steward started: its site, what it is, and its outline when it opened. */
	record Started(UUID id, ResourceLocation structure, Kind kind, BlockPos origin, @Nullable BoundingBox box) {
	}

	/** One city soak in progress. */
	static final class Run {
		final ServerLevel level;
		final BlockPos hall;
		final long start;
		final long stewardTicks;
		final long limit;
		final Map<UUID, Started> started = new LinkedHashMap<>();
		final List<BlockPos> chests = new ArrayList<>();
		final List<Villager> builders = new ArrayList<>();
		final Map<Item, Integer> stocked = new TreeMap<>(CitySoak::byId);
		/** What was put in the storehouse for each of his builds: its list can grow (a lamp added, the ground replanned). */
		final Map<UUID, Map<Item, Integer>> stockedFor = new java.util.HashMap<>();
		final int stallsBefore;
		final boolean[] flags;
		int nextChest;
		boolean windingDown;

		Run(ServerLevel level, BlockPos hall, long stewardTicks, boolean[] flags) {
			this.level = level;
			this.hall = hall;
			this.start = level.getGameTime();
			this.stewardTicks = stewardTicks;
			this.limit = stewardTicks + WIND_DOWN_DAYS * Soak.DAY;
			this.stallsBefore = StallWatch.stalls();
			this.flags = flags;
		}
	}

	@Nullable
	private static Run running;

	/** The last result line, for the time-lapse scene. */
	@Nullable
	public static volatile String lastResult;

	public static void init() {
		// Watching a run costs nothing while none is (the scene starts one without the command).
		Platform.get().onServerTick(CitySoak::tick);
		if (!Boolean.getBoolean("aliveworkplace.benchmark")) {
			return;
		}
		Platform.get().onRegisterCommands(dispatcher -> dispatcher.register(
			Commands.literal("workplace").then(Commands.literal("city")
				.requires(s -> s.hasPermission(4))
				.executes(ctx -> run(ctx.getSource(), DAYS))
				.then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
					.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days")))))));
	}

	static int run(CommandSourceStack source, int days) {
		String text = begin(source.getLevel(), BlockPos.containing(source.getPosition()), days);
		source.sendSuccess(() -> Component.literal(text), true);
		return running == null ? 0 : 1;
	}

	/** Starts a city soak with its hall at {@code origin} and returns (and logs) its summary line. The scene starts here too. */
	public static String begin(ServerLevel level, BlockPos origin, int days) {
		lastResult = null;
		Run run = start(level, origin, days * Soak.DAY, RandomSource.create(27_22L));
		running = run;
		StewardCost.begin();
		String text = "City: a Steward, " + run.builders.size() + " builders, " + VILLAGERS + " villagers, "
			+ ((VillageHallBlockEntity) level.getBlockEntity(run.hall)).plan().zones().size() + " zones, 2 streets and a wall line; "
			+ days + " days";
		AliveWorkplace.LOG.info(text);
		return text;
	}

	/** Builds the Steward started so far, those finished, and whether the soak is still running. */
	public static int[] progress() {
		Run run = running;
		if (run == null) {
			return new int[] {0, 0, 0};
		}
		BuildSiteManager manager = BuildSiteManager.get(run.level);
		int finished = (int) run.started.keySet().stream().filter(id -> manager.get(id) == null).count();
		return new int[] {finished, run.started.size(), 1};
	}

	/** Ends the running soak now: logs and returns its result line, or null when none is running. */
	@Nullable
	public static String finish() {
		Run run = running;
		if (run == null) {
			return null;
		}
		String result = result(run);
		AliveWorkplace.LOG.info(result);
		lastResult = result;
		end(run);
		return result;
	}

	/** Lays out the village and its plan; package-visible for the GameTest. */
	static Run start(ServerLevel level, BlockPos hall, long stewardTicks, RandomSource random) {
		MinecraftServer server = level.getServer();
		Rules.set(level, ModGameRules.FREE_MATERIALS, false, server);
		Rules.set(level, GameRules.RULE_DAYLIGHT, true, server);
		Rules.set(level, GameRules.RULE_DOMOBSPAWNING, false, server);
		level.setDayTime(1000);
		// The Steward's switches are on for the soak whatever the expansion gate says (1.1 isn't released yet); the
		// raid that makes the wall wanted is put on record, not fought.
		boolean[] flags = {Stewards.ENABLED, StewardDesk.SELF_RUN, Roads.ENABLED, Walls.ENABLED, Renewals.ENABLED, CaravanRoads.ENABLED,
			VillageRaids.ENABLED, BanditCamps.ENABLED};
		Stewards.ENABLED = true;
		StewardDesk.SELF_RUN = true;
		Roads.ENABLED = true;
		Walls.ENABLED = true;
		Renewals.ENABLED = true;
		CaravanRoads.ENABLED = false;
		VillageRaids.ENABLED = false;
		BanditCamps.ENABLED = false;
		plains(level, hall, random);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		entity.setLastRaidDay(Chronicle.day(level));
		entity.setPlan(plan(hall));
		// The old house in the Homes zone, for renewal.
		StructurePlaceSettings settings = new StructurePlaceSettings();
		settings.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		settings.addProcessor(JigsawReplacementProcessor.INSTANCE);
		BlockPos house = hall.offset(OLD_HOUSE);
		level.getStructureManager().get(ResourceLocation.withDefaultNamespace("village/plains/houses/plains_medium_house_1"))
			.ifPresent(t -> t.placeInWorld(level, house, house, settings, RandomSource.create(27_21L), 2));
		Run run = new Run(level, hall.immutable(), stewardTicks, flags);
		// The Steward: a seasoned builder, made Steward of the hall (as the City Plan does), Apprentice level: 2 builds open.
		Villager steward = spawn(level, hall.south(2));
		if (steward != null) {
			Stations.assign(level, steward, hall, ModVillagers.STEWARD);
			steward.setVillagerData(steward.getVillagerData().setProfession(ModVillagers.STEWARD).setLevel(2));
			steward.setVillagerXp(10);
		}
		for (int b = 0; b < BUILDERS; b++) {
			BlockPos table = hall.offset(-1 + 3 * b, 0, -6);
			level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			Villager builder = spawn(level, table.south());
			if (builder != null) {
				Builders.employ(level, builder, table);
				run.builders.add(builder);
			}
		}
		BlockPos store = hall.offset(STOREHOUSE);
		level.setBlockAndUpdate(store, ModBlocks.STOREHOUSE.defaultBlockState());
		Villager porter = spawn(level, store.north());
		if (porter != null) {
			Jobs.employ(level, porter, store, ModVillagers.STOREHOUSE_POI, ModVillagers.PORTER);
		}
		for (int i = 0; i < VILLAGERS; i++) {
			spawn(level, hall.offset(i % 2 == 0 ? 6 : 3, 0, -4 + i));
		}
		// A storehouse already stocked with the basics; each build the Steward opens adds its own list.
		Map<Item, Integer> basics = new TreeMap<>(CitySoak::byId);
		basics.put(Blocks.OAK_PLANKS.asItem(), 256);
		basics.put(Blocks.OAK_LOG.asItem(), 128);
		basics.put(Blocks.COBBLESTONE.asItem(), 256);
		basics.put(Blocks.STONE_BRICKS.asItem(), 128);
		basics.put(Blocks.GLASS_PANE.asItem(), 64);
		basics.put(Blocks.TORCH.asItem(), 64);
		// And the village's food: villagers eat from the storehouse once a day (VillageNeeds), something different each time.
		basics.put(net.minecraft.world.item.Items.BREAD, 128);
		basics.put(net.minecraft.world.item.Items.BAKED_POTATO, 64);
		basics.put(net.minecraft.world.item.Items.CARROT, 64);
		basics.put(net.minecraft.world.item.Items.POTATO, 64);
		basics.put(net.minecraft.world.item.Items.APPLE, 64);
		stock(run, basics);
		MaterialLedger.start();
		return run;
	}

	/** The plan: the zones by quarter, Keep Clear round the hall, the two streets and the wall line, in Run the village. */
	public static CityPlan plan(BlockPos hall) {
		CityPlan plan = CityPlan.EMPTY;
		plan = zone(plan, hall, "keep_clear", "Keep Clear", -SQUARE, -SQUARE, SQUARE - 1, SQUARE - 1, null);
		// North-west: homes, which renew the old house; north-east: workshops; south-west: farms and gardens; south-east: market.
		plan = zone(plan, hall, "homes", "Homes", -STREET_END, -STREET_END, STREET - 3, STREET - 3, "keep_clear");
		plan = plan.editZone(1, "homes", "Homes", "", true);
		plan = zone(plan, hall, "workshops", "Workshops", STREET + 2, -STREET_END, STREET_END - 1, STREET - 3, null);
		plan = zone(plan, hall, "farms", "Farms", -STREET_END, STREET + 2, -20, STREET_END - 1, null);
		plan = zone(plan, hall, "gardens", "Gardens", -19, STREET + 2, STREET - 3, STREET_END - 1, null);
		plan = zone(plan, hall, "market", "Market", STREET + 2, STREET + 2, STREET_END - 1, STREET_END - 1, null);
		plan = plan.addRoad(new CityPlan.Road(List.of(new BlockPos(-STREET_END, 0, STREET), new BlockPos(STREET_END, 0, STREET)),
			CityPlan.Road.STREET, "", true));
		plan = plan.addRoad(new CityPlan.Road(List.of(new BlockPos(STREET, 0, -STREET_END), new BlockPos(STREET, 0, STREET_END)),
			CityPlan.Road.STREET, "", true));
		plan = plan.withWall(new CityPlan.Wall(List.of(new BlockPos(-WALL, 0, -WALL), new BlockPos(WALL - 1, 0, -WALL),
			new BlockPos(WALL - 1, 0, WALL - 1), new BlockPos(-WALL, 0, WALL - 1)), true));
		return plan.withMode(CityPlan.Mode.RUN);
	}

	/** Adds a zone over the blocks from (x0, z0) to (x1, z1) from the hall, leaving out cells already in {@code except}'s zone. */
	private static CityPlan zone(CityPlan plan, BlockPos hall, String kind, String name, int x0, int z0, int x1, int z1, @Nullable String except) {
		BitSet cells = new BitSet();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				BlockPos pos = hall.offset(x, 0, z);
				if (except == null || plan.zoneAt(hall, pos).map(zn -> !zn.kind().equals(except)).orElse(true)) {
					int cell = CityPlan.cellAt(hall, pos);
					if (cell >= 0) {
						cells.set(cell);
					}
				}
			}
		}
		CityPlan next = plan.addZone(kind, name, "");
		return next.paint(next.zones().size() - 1, cells);
	}

	/** Flat plains: grass over dirt and stone, open sky, a few oaks scattered outside the square and the streets. */
	static void plains(ServerLevel level, BlockPos hall, RandomSource random) {
		int ground = hall.getY() - 1;
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				int x = hall.getX() + dx;
				int z = hall.getZ() + dz;
				level.getChunk(x >> 4, z >> 4);
				for (int y = ground - 4; y <= ground + 30; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					level.setBlock(pos, y < ground - 2 ? Blocks.STONE.defaultBlockState()
						: y < ground ? Blocks.DIRT.defaultBlockState()
						: y == ground ? Blocks.GRASS_BLOCK.defaultBlockState()
						: Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		for (int i = 0; i < 24; i++) {
			int dx = random.nextInt(2 * REACH - 8) - REACH + 4;
			int dz = random.nextInt(2 * REACH - 8) - REACH + 4;
			if (Math.abs(dx) < SQUARE + 4 && Math.abs(dz) < SQUARE + 4 || Math.abs(dx - STREET) < 4 || Math.abs(dz - STREET) < 4) {
				continue;
			}
			BlockPos pos = hall.offset(dx, 0, dz);
			level.setBlock(pos, Blocks.OAK_SAPLING.defaultBlockState(), 2);
			TreeGrower.OAK.growTree(level, level.getChunkSource().getGenerator(), pos, level.getBlockState(pos), random);
		}
	}

	@Nullable
	private static Villager spawn(ServerLevel level, BlockPos pos) {
		Villager villager = EntityType.VILLAGER.spawn(level, pos, MobSpawnType.COMMAND);
		if (villager != null) {
			villager.setPersistenceRequired();
		}
		return villager;
	}

	/** Puts {@code items} into the storehouse's chests (new ones as they fill), and counts them as stocked. */
	static void stock(Run run, Map<Item, Integer> items) {
		List<ItemStack> stacks = new ArrayList<>();
		items.forEach((item, n) -> {
			int left = n;
			while (left > 0) {
				int take = Math.min(left, item.getDefaultMaxStackSize());
				stacks.add(new ItemStack(item, take));
				left -= take;
			}
		});
		for (ItemStack stack : stacks) {
			ItemStack rest = stack.copy();
			for (BlockPos pos : run.chests) {
				rest = into(run.level, pos, rest);
				if (rest.isEmpty()) {
					break;
				}
			}
			while (!rest.isEmpty()) {
				BlockPos pos = nextChest(run);
				if (pos == null) {
					AliveWorkplace.LOG.warn("City: the storehouse's chests are full; {} {} not stocked", rest.getCount(), rest.getItem());
					break;
				}
				rest = into(run.level, pos, rest);
			}
			int put = stack.getCount() - rest.getCount();
			if (put > 0) {
				run.stocked.merge(stack.getItem(), put, Integer::sum);
			}
		}
	}

	/** The next chest by the storehouse, placed now, or null when all 56 places are taken. */
	@Nullable
	private static BlockPos nextChest(Run run) {
		while (run.nextChest < 56) {
			int n = run.nextChest++;
			int layer = n / 28;
			int x = -7 + 2 * (n % 4);
			int z = 1 + (n % 28) / 4;
			BlockPos pos = run.hall.offset(x, layer, z);
			if (!run.level.getBlockState(pos).isAir()) {
				continue;
			}
			run.level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
			run.chests.add(pos);
			return pos;
		}
		return null;
	}

	private static ItemStack into(ServerLevel level, BlockPos pos, ItemStack stack) {
		if (!(level.getBlockEntity(pos) instanceof Container chest)) {
			return stack;
		}
		ItemStack rest = stack.copy();
		for (int slot = 0; slot < chest.getContainerSize() && !rest.isEmpty(); slot++) {
			ItemStack in = chest.getItem(slot);
			if (in.isEmpty()) {
				chest.setItem(slot, rest.copy());
				rest = ItemStack.EMPTY;
			} else if (ItemStack.isSameItemSameComponents(in, rest) && in.getCount() < in.getMaxStackSize()) {
				int move = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
				in.grow(move);
				rest.shrink(move);
				chest.setChanged();
			}
		}
		return rest;
	}

	private static void tick(MinecraftServer server) {
		StewardCost.endTick();
		Run run = running;
		if (run == null || run.level.getGameTime() % 100 != 0) {
			return;
		}
		watch(run);
		long ticks = run.level.getGameTime() - run.start;
		if (!run.windingDown && ticks >= run.stewardTicks) {
			// His days are up: he starts nothing new, and what he started gets finished.
			run.windingDown = true;
			StewardDesk.SELF_RUN = false;
			Roads.ENABLED = false;
			Walls.ENABLED = false;
			Renewals.ENABLED = false;
			AliveWorkplace.LOG.info("City: the Steward's {} days are up with {} builds started; the builders finish what is open",
				run.stewardTicks / Soak.DAY, run.started.size());
		}
		if (run.windingDown && (open(run) == 0 || ticks >= run.limit)) {
			finish();
		}
	}

	/** Notes every build the Steward has opened since the last look, and keeps the storehouse stocked with what each lists. */
	static void watch(Run run) {
		List<BuildSite> sites = new ArrayList<>(StewardDesk.openSites(run.level, run.hall));
		for (BuildSite s : Roads.openSegments(run.level, run.hall)) {
			if (sites.stream().noneMatch(o -> o.id().equals(s.id()))) {
				sites.add(s);
			}
		}
		for (BuildSite site : sites) {
			BuildPlan plan = site.plan(run.level);
			Started known = run.started.get(site.id());
			if (known == null) {
				Kind kind = Roads.isSegment(site.structure()) ? Kind.ROAD
					: Walls.isPiece(site.structure()) ? Kind.WALL
					: Renewals.isScan(site.structure()) ? Kind.TAKE_DOWN : Kind.BUILDING;
				known = new Started(site.id(), site.structure(), kind, site.placement().origin(), plan == null ? null : plan.bounds());
				run.started.put(site.id(), known);
			}
			if (plan == null || known.kind() == Kind.TAKE_DOWN) {
				continue;
			}
			// The storehouse gets what the build's list asks for beyond what it was given: the whole list when it opens,
			// the difference when the list grows.
			Map<Item, Integer> given = run.stockedFor.computeIfAbsent(site.id(), id -> new java.util.HashMap<>());
			Map<Item, Integer> more = new TreeMap<>(CitySoak::byId);
			plan.materials().forEach((item, n) -> {
				int extra = n - given.getOrDefault(item, 0);
				if (extra > 0) {
					more.put(item, extra);
					given.merge(item, extra, Integer::sum);
				}
			});
			if (!more.isEmpty()) {
				stock(run, more);
			}
		}
	}

	/** The Steward's builds still open. */
	static int open(Run run) {
		BuildSiteManager manager = BuildSiteManager.get(run.level);
		return (int) run.started.keySet().stream().filter(id -> manager.get(id) != null).count();
	}

	/**
	 * Where a build stands on the plan: "" when it is all in one zone that isn't Keep Clear (a building), clear of Keep
	 * Clear (a wall piece, a take-down) or anywhere (a road: roads may cross Keep Clear); else what is wrong with it.
	 */
	public static String where(CityPlan plan, BlockPos hall, Kind kind, BoundingBox box) {
		String zone = null;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				var at = plan.zoneAt(hall, new BlockPos(x, hall.getY(), z));
				if (kind != Kind.ROAD && at.isPresent() && at.get().kind().equals("keep_clear")) {
					return "in Keep Clear"; // roads may cross it (the zone kind's own rule)
				}
				if (kind != Kind.BUILDING) {
					continue;
				}
				if (at.isEmpty()) {
					return "outside the zones";
				}
				if (zone == null) {
					zone = at.get().name();
				} else if (!zone.equals(at.get().name())) {
					return "across " + zone + " and " + at.get().name();
				}
			}
		}
		return "";
	}

	/** The result line. */
	static String result(Run run) {
		ServerLevel level = run.level;
		watch(run);
		MaterialLedger.stop();
		long[] cost = StewardCost.finish();
		BuildSiteManager manager = BuildSiteManager.get(level);
		CityPlan plan = level.getBlockEntity(run.hall) instanceof VillageHallBlockEntity e ? e.plan() : CityPlan.EMPTY;
		int finished = 0;
		Map<Kind, Integer> byKind = new TreeMap<>();
		List<String> unfinished = new ArrayList<>();
		List<String> misplaced = new ArrayList<>();
		for (Started s : run.started.values()) {
			byKind.merge(s.kind(), 1, Integer::sum);
			if (manager.get(s.id()) == null) {
				finished++;
			} else {
				unfinished.add(s.structure().getPath());
			}
			if (s.box() != null) {
				String wrong = where(plan, run.hall, s.kind(), s.box());
				if (!wrong.isEmpty()) {
					misplaced.add(s.structure().getPath() + " " + wrong);
				}
			}
		}
		// Left: every container on the soak's ground and the builders' bags.
		Map<Item, Integer> left = new TreeMap<>(CitySoak::byId);
		BlockPos from = run.hall.offset(-REACH, -8, -REACH);
		BlockPos to = run.hall.offset(REACH, 40, REACH);
		BoundingBox area = BoundingBox.fromCorners(from, to);
		for (int cx = from.getX() >> 4; cx <= to.getX() >> 4; cx++) {
			for (int cz = from.getZ() >> 4; cz <= to.getZ() >> 4; cz++) {
				for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (be instanceof Container box && area.isInside(be.getBlockPos())) {
						for (int i = 0; i < box.getContainerSize(); i++) {
							ItemStack stack = box.getItem(i);
							if (!stack.isEmpty()) {
								left.merge(stack.getItem(), stack.getCount(), Integer::sum);
							}
						}
					}
				}
			}
		}
		for (Villager builder : run.builders) {
			for (ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(builder).stacks()) {
				if (!stack.isEmpty()) {
					left.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
		}
		// What builders built in, and the meals the village ate from the store.
		Map<Item, Integer> usedOrEaten = new TreeMap<>(CitySoak::byId);
		usedOrEaten.putAll(MaterialLedger.used());
		MaterialLedger.eaten().forEach((item, n) -> usedOrEaten.merge(item, n, Integer::sum));
		int eaten = MaterialLedger.eaten().values().stream().mapToInt(Integer::intValue).sum();
		long ticks = level.getGameTime() - run.start;
		String kinds = byKind.isEmpty() ? "none" : String.join(", ", byKind.entrySet().stream()
			.map(e -> e.getValue() + " " + e.getKey().name().toLowerCase(Locale.ROOT).replace('_', ' ')).toList());
		return RESULT + " " + finished + "/" + run.started.size() + " builds the Steward started finished (" + kinds + ") in " + ticks
			+ " ticks (" + String.format(Locale.ROOT, "%.1f", ticks / (double) Soak.DAY) + " days, " + run.stewardTicks / Soak.DAY
			+ " of them his); outside their zone or in Keep Clear: " + (misplaced.isEmpty() ? "none" : String.join(", ", misplaced))
			+ "; " + eaten + " meals eaten from the store; " + (StallWatch.stalls() - run.stallsBefore) + " stalls; items off: "
			+ Soak.itemsOff(run.stocked, MaterialLedger.gained(), usedOrEaten, MaterialLedger.dropped(), left)
			+ "; Steward cost per tick (1 village, " + cost.length + " ticks): p50 " + ms(StewardCost.percentileMs(cost, 50))
			+ ", p95 " + ms(StewardCost.percentileMs(cost, 95)) + ", p99 " + ms(StewardCost.percentileMs(cost, 99))
			+ ", worst " + ms(StewardCost.percentileMs(cost, 100))
			+ " (slowest call: " + StewardCost.worstCall() + ")"
			+ (unfinished.isEmpty() ? "" : "; unfinished: " + String.join(", ", unfinished))
			+ "; " + state(run);
	}

	/** What else the village was doing at the end: its rank, the wall, the old houses and the desk's proposals. */
	static String state(Run run) {
		ServerLevel level = run.level;
		CityPlan plan = level.getBlockEntity(run.hall) instanceof VillageHallBlockEntity e ? e.plan() : CityPlan.EMPTY;
		String wall = plan.wall().map(w -> w.approved() ? "approved (" + w.kit() + ")" : w.kit().isEmpty() ? "drawn" : "proposed (" + w.kit() + ")")
			.orElse("none");
		String houses = io.github.jcondedata.aliveworkplace.city.OldHouses.result(level, run.hall)
			.map(list -> list.isEmpty() ? "none found" : String.join(", ", list.stream().map(h -> h.verdict().name().toLowerCase(Locale.ROOT)).toList()))
			.orElse("not surveyed");
		List<String> proposals = StewardDesk.of(level, run.hall).proposals().stream().map(p -> p.name().getString()).toList();
		return "rank " + VillageRanks.of(level, run.hall).name().toLowerCase(Locale.ROOT) + "; wall " + wall + " (wanted: "
			+ Walls.wanted(level, run.hall) + ", " + Walls.standing(level, run.hall) + " pieces standing); old houses: " + houses
			+ "; renewals going: " + Renewals.active(level, run.hall).size() + "; on the desk: " + (proposals.isEmpty() ? "nothing" : String.join(" | ", proposals));
	}

	private static String ms(double v) {
		return String.format(Locale.ROOT, "%.3f ms", v);
	}

	/** Puts the switches back as they were. */
	private static void end(Run run) {
		Stewards.ENABLED = run.flags[0];
		StewardDesk.SELF_RUN = run.flags[1];
		Roads.ENABLED = run.flags[2];
		Walls.ENABLED = run.flags[3];
		Renewals.ENABLED = run.flags[4];
		CaravanRoads.ENABLED = run.flags[5];
		VillageRaids.ENABLED = run.flags[6];
		BanditCamps.ENABLED = run.flags[7];
		if (running == run) {
			running = null;
		}
	}

	/** Ends a run started by the GameTest without a result. */
	static void abandon(Run run) {
		MaterialLedger.stop();
		StewardCost.finish();
		end(run);
	}

	private static int byId(Item a, Item b) {
		return BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b));
	}

	private CitySoak() {
	}
}
