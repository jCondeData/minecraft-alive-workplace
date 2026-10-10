package io.github.jcondedata.aliveworkplace.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialLedger;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Cradles;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.HarvestIdols;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.hall.WorkHorn;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /workplace season}: a season under the edicts (ROADMAP 30.22), as {@link CitySoak} is for the Steward. Lays
 * out a City of {@link #VILLAGERS} villagers on flat ground in harvest season (autumn): six farmers on six fields round
 * a kitchen (a chef at a smoker) and a Storehouse (a porter) that share the store's chests, four masons and four
 * carpenters, 60 lit beds with a Cradle by the first, a Harvest Idol among the fields, a Harvest Guild founded under
 * a Master farmer, and a rush called from the console each morning. Long Shifts, Free Bread, Large Families and
 * Festival Season are in force for the first days; then all four are reformed for as many again.
 *
 * <p>Each in-game day logs one {@code Season day} line: the meals taken from the store and left in it, births, the
 * ill, the average mood (at noon in the rush, and at dusk worn out), what came into the store and the crops harvested, the treasury and what the festival
 * took from it, the pace of five workers (in the morning before the horn, and in the rush), the fastest pace anyone reached, and
 * the tick (average and worst, as the server sprints). Our share of the tick comes from the profile ({@code
 * tools/packtest/perf.py --days}), which also reads the {@code Season long ticks} lines (when each tick over 50 ms
 * ended and how long it took) to say how much of each long tick was our code. The run ends with one {@code Season
 * result:} line that says whether the costs showed and whether anything ran away.
 *
 * <p>The pack server has no player on it, and vanilla gives random ticks only to chunks near one, so the run gives the
 * fields' crops the random ticks a player standing by would bring ({@link #grow}).
 *
 * <p>What a real City has and this one is only given on paper, so its rank holds: 25 finished buildings (records, the
 * Guildhall among them; nothing is built) and 7 research levels in topics that change nothing measured here (Lore,
 * Architecture, Logistics 2, Fortification 2, Warding). Only registered with {@code -Daliveworkplace.benchmark=true}.
 */
public final class SeasonSoak {
	/** Days under the edicts, and as many again reformed. */
	public static final int DAYS = 4;
	public static final int VILLAGERS = 35;
	public static final int FARMERS = 6;
	public static final int BEDS = 60;
	public static final String DAY_LINE = "Season day";
	public static final String RESULT = "Season result:";
	/** The four edicts of the run. */
	public static final List<String> EDICTS = List.of("long_shifts", "free_bread", "large_families", "festival_season");
	/** The treasury at the start, in emeralds: enough for the first festival whichever day it falls on. */
	public static final int TREASURY = 20;
	/** The first day of autumn on the village calendar (16-day seasons): day 33. */
	static final long FIRST_DAY = 33;
	/** When the horn is blown each day (day time), and how long after it the rush is measured. */
	static final long RUSH_AT = 3000;
	/** When the five's everyday pace is read: in the morning's work, before the horn. */
	static final long MORNING = 2000;
	static final long NOON = 6000;
	public static final String LONG_TICKS = "Season long ticks";
	static final long DUSK = 13000;
	/** The ground laid out round the hall, in blocks each way. */
	static final int REACH = 44;

	/** Which part of the season a run covers: both halves in one village, or one half on its own (a shorter night job). */
	public enum Part {
		BOTH, EDICTS, REFORMED;

		static Part of(String text) {
			return valueOf(text.toUpperCase(Locale.ROOT));
		}
	}

	/** One day's numbers. */
	public record Day(int number, boolean reformed, int taken, long left, boolean emptyAllDay, int births, int villagers, int ill, int illPeak,
		float moodNoon, float moodDusk, int treasury, int festivalPaid, boolean festival, boolean festivalMissed, List<Integer> pace,
		List<Integer> rushPace, int fastest, float slowestFactor, double tickMs, double worstMs, int over50, long stored, int harvested) {
		String line() {
			return DAY_LINE + " " + number + " (" + (reformed ? "reformed" : "edicts") + "): meals taken " + taken + ", brought in " + stored
				+ ", left " + left + (emptyAllDay ? " (the store was empty all day)" : "") + "; crops harvested " + harvested + "; births " + births + ", villagers " + villagers + "; ill " + ill
				+ " (peak " + illPeak + "); mood " + one(moodNoon) + " at noon, " + one(moodDusk) + " at dusk; treasury " + treasury
				+ " emeralds" + (festival ? ", the festival took " + festivalPaid : festivalMissed ? ", no money for the festival" : "")
				+ "; pace of five in the morning " + percents(pace) + ", in the rush " + percents(rushPace) + ", fastest of all +" + fastest
				+ "%; tick " + String.format(Locale.ROOT, "%.2f", tickMs) + " ms on average, worst " + String.format(Locale.ROOT, "%.0f", worstMs)
				+ " ms, " + over50 + " over 50 ms";
		}
	}

	/** One season run in progress. */
	static final class Run {
		final ServerLevel level;
		final BlockPos hall;
		final Part part;
		final int perPart;
		final int days;
		final boolean[] flags;
		final List<Villager> five = new ArrayList<>();
		final List<String> fiveNames = new ArrayList<>();
		final List<Villager> farmers = new ArrayList<>();
		/** Every crop's place on the six fields, and the fixed random their random ticks come from. */
		final List<BlockPos> crops = new ArrayList<>();
		final RandomSource growth = RandomSource.create(30_22_1L);
		int harvestedBefore;
		long leftBefore;
		long treasuryTotalBefore;
		int treasuryBefore;
		final List<Day> record = new ArrayList<>();
		long day;
		boolean reformed;
		// The day so far.
		int takenBefore;
		int birthsBefore;
		int samples;
		int emptySamples;
		int illPeak;
		float moodNoon = Float.NaN;
		float moodDusk = Float.NaN;
		List<Integer> pace = List.of();
		List<Integer> rushPace = List.of();
		long rushSampleAt = -1;
		int fastest;
		float slowestFactor = 1f;
		long lastTickAt;
		long dayNanos;
		long dayTicks;
		long dayWorst;
		int over50;
		/** The day's ticks over 50 ms: when each ended (milliseconds of the day, by the wall clock) and how long it took. */
		final List<long[]> longTicks = new ArrayList<>();

		Run(ServerLevel level, BlockPos hall, Part part, int perPart, boolean[] flags) {
			this.level = level;
			this.hall = hall;
			this.part = part;
			this.perPart = perPart;
			this.days = part == Part.BOTH ? 2 * perPart : perPart;
			this.flags = flags;
		}
	}

	@Nullable
	private static Run running;

	/** The last result line. */
	@Nullable
	public static volatile String lastResult;

	public static void init() {
		Platform.get().onServerTick(SeasonSoak::tick);
		if (!Boolean.getBoolean("aliveworkplace.benchmark")) {
			return;
		}
		Platform.get().onRegisterCommands(dispatcher -> dispatcher.register(
			Commands.literal("workplace").then(Commands.literal("season")
				.requires(s -> s.hasPermission(4))
				.executes(ctx -> run(ctx.getSource(), DAYS, Part.BOTH))
				.then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
					.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days"), Part.BOTH))
					.then(Commands.argument("part", StringArgumentType.word())
						.suggests((ctx, builder) -> builder.suggest("both").suggest("edicts").suggest("reformed").buildFuture())
						.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days"),
							Part.of(StringArgumentType.getString(ctx, "part")))))))));
	}

	static int run(CommandSourceStack source, int days, Part part) {
		String text = begin(source.getLevel(), BlockPos.containing(source.getPosition()), days, part);
		source.sendSuccess(() -> Component.literal(text), true);
		return running == null ? 0 : 1;
	}

	/** Starts a season run with its hall at {@code origin}, {@code days} days a part, and returns (and logs) its summary line. */
	public static String begin(ServerLevel level, BlockPos origin, int days, Part part) {
		lastResult = null;
		Run run = start(level, origin, days, part);
		running = run;
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(run.hall);
		String text = "Season: a " + entity.rank().name().toLowerCase(Locale.ROOT) + " of "
			+ level.getEntitiesOfClass(Villager.class, VillageHalls.area(run.hall), Villager::isAlive).size() + " villagers, " + FARMERS
			+ " farms, a kitchen and a store of " + meals(run) + " meals; " + entity.edicts().size() + " edicts in force"
			+ (run.reformed ? " (all reformed)" : "") + ", " + (Cradles.nursery(level, run.hall) ? "a Cradle" : "no Cradle") + ", "
			+ (HarvestIdols.harvestSeason(level) ? "harvest season" : "not harvest season") + " with " + HarvestIdols.idols(level).size()
			+ " idol, " + founded(run, entity) + " guild founded; " + run.days + " days (" + part.name().toLowerCase(Locale.ROOT)
			+ "); the five: " + String.join(", ", run.fiveNames);
		AliveWorkplace.LOG.info(text);
		return text;
	}

	private static int founded(Run run, VillageHallBlockEntity entity) {
		return (int) entity.guilds().stream().filter(c -> Guilds.founded(run.level, entity, c.id())).count();
	}

	/** Lays out the City; package-visible for the GameTest. */
	static Run start(ServerLevel level, BlockPos hall, int perPart, Part part) {
		MinecraftServer server = level.getServer();
		Rules.set(level, GameRules.RULE_DAYLIGHT, true, server);
		Rules.set(level, GameRules.RULE_DOMOBSPAWNING, false, server);
		// The first morning of autumn: harvest season for the whole run.
		level.setDayTime((FIRST_DAY - 1) * VillageNeeds.DAY);
		// Milestone 30's switches are on for the run whatever the expansion gate says (1.4 isn't released yet); no raids.
		boolean[] flags = {Edicts.ENABLED, WorkHorn.ENABLED, Cradles.ENABLED, Guilds.ENABLED, HarvestIdols.ENABLED, VillageRaids.ENABLED,
			BanditCamps.ENABLED};
		Edicts.setEnabled(true);
		WorkHorn.ENABLED = true;
		Cradles.ENABLED = true;
		Guilds.ENABLED = true;
		HarvestIdols.ENABLED = true;
		VillageRaids.ENABLED = false;
		BanditCamps.ENABLED = false;
		Run run = new Run(level, hall.immutable(), part, perPart, flags);
		ground(level, hall);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		// A City on paper: 25 finished buildings and 7 research levels (see the class comment).
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlockPos guildhall = hall.offset(-30, 0, 36);
		sites.recordFinished(StarterBlueprints.GUILDHALL.id(), placement(level, guildhall), UUID.randomUUID());
		for (int i = 0; i < 24; i++) {
			sites.recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), placement(level, hall.offset(-24 + 2 * i, 0, 40)), UUID.randomUUID());
		}
		entity.setResearch(new Research.State(Map.of("lore", 1, "architecture", 1, "logistics", 2, "fortification", 2, "warding", 1),
			Optional.empty(), 0, false));
		entity.setRank(VillageRanks.Rank.CITY);
		entity.setTreasury(TREASURY * 100);
		run.treasuryBefore = entity.treasury();
		run.treasuryTotalBefore = entity.treasuryTotal();

		// The store: six chests between the kitchen and the Storehouse, within reach of every composter.
		List<BlockPos> chests = new ArrayList<>();
		for (int x = -2; x <= 2; x += 2) {
			for (int z = 17; z <= 19; z += 2) {
				BlockPos chest = hall.offset(x, 0, z);
				level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
				chests.add(chest);
			}
		}
		put(level, chests.get(0), new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64), new ItemStack(Items.BAKED_POTATO, 64),
			new ItemStack(Items.CARROT, 64));
		BlockPos smoker = hall.offset(-1, 0, 15);
		level.setBlockAndUpdate(smoker, Blocks.SMOKER.defaultBlockState());
		Villager chef = worker(level, smoker.north(), smoker, PoiTypes.BUTCHER, ModVillagers.CHEF);
		BlockPos storehouse = hall.offset(1, 0, 15);
		level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState());
		Villager porter = spawn(level, storehouse.north());
		if (porter != null) {
			Porters.employ(level, porter, storehouse);
		}

		// Six 9x9 fields, three each side, each with water in the middle and its composter towards the store.
		RandomSource random = RandomSource.create(30_22L);
		Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES};
		List<Villager> farmers = new ArrayList<>();
		for (int f = 0; f < FARMERS; f++) {
			int side = f < 3 ? -1 : 1;
			int z0 = 4 + 10 * (f % 3);
			int xNear = side * 12;
			int xFar = side * 20;
			BoundingBox box = field(level, hall, Math.min(xNear, xFar), z0, crops[f % 3], random);
			for (BlockPos soil : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockState(soil).getBlock() instanceof FarmBlock) {
					run.crops.add(soil.above());
				}
			}
			// Composters at z 11, 18 and 25: each within 8 blocks of the store's chests (z 17 and 19), where the harvest goes.
			BlockPos composter = hall.offset(side * 8, 0, 11 + 7 * (f % 3));
			level.setBlockAndUpdate(composter, Blocks.COMPOSTER.defaultBlockState());
			Villager farmer = worker(level, composter.relative(side < 0 ? Direction.EAST : Direction.WEST), composter, PoiTypes.FARMER,
				VillagerProfession.FARMER);
			if (farmer != null) {
				Fields.start(level, farmer, box);
				farmers.add(farmer);
			}
		}
		level.setBlockAndUpdate(hall.offset(0, 0, 23), ModBlocks.HARVEST_IDOL.defaultBlockState());
		run.farmers.addAll(farmers);

		// Four masons and four carpenters east of the hall.
		Villager mason = null;
		for (int i = 0; i < 4; i++) {
			BlockPos cutter = hall.offset(24 + 3 * i, 0, -4);
			level.setBlockAndUpdate(cutter, Blocks.STONECUTTER.defaultBlockState());
			Villager v = worker(level, cutter.south(), cutter, PoiTypes.MASON, VillagerProfession.MASON);
			mason = mason == null ? v : mason;
			BlockPos bench = hall.offset(24 + 3 * i, 0, -8);
			level.setBlockAndUpdate(bench, ModBlocks.CARPENTERS_BENCH.defaultBlockState());
			worker(level, bench.south(), bench, ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		}

		// 60 beds north of the hall, a torch by each, and the Cradle by the first.
		for (int i = 0; i < BEDS; i++) {
			BlockPos foot = hall.offset(-18 + 4 * (i % 10), 0, -10 - 4 * (i / 10));
			BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
			level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
			level.setBlock(foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
			level.setBlockAndUpdate(foot.east(2), Blocks.TORCH.defaultBlockState());
		}
		level.setBlockAndUpdate(hall.offset(-19, 0, -10), ModBlocks.CRADLE.defaultBlockState());

		// The rest of the 35: villagers without a job, round the hall.
		int workers = 2 + farmers.size() + 8;
		for (int i = workers; i < VILLAGERS; i++) {
			spawn(level, hall.offset(-9 + i % 10 * 2, 0, -3 - 2 * (i / 10 % 2)));
		}
		// Everybody last ate a day ago, so day 1 has its meals like every other day.
		long ate = level.getGameTime() - VillageNeeds.DAY;
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			ModAttachments.LAST_MEAL.set(v, ate);
		}

		// The Harvest Guild: the first farmer is a Master, chartered, and the Guildhall on record founds it.
		if (!farmers.isEmpty()) {
			Villager master = farmers.get(0);
			master.setVillagerData(master.getVillagerData().setLevel(5));
			Guilds.Offer offer = Guilds.grant(level, master);
			if (offer.outcome() != Guilds.Outcome.GRANTED) {
				AliveWorkplace.LOG.warn("Season: no guild: {}", offer.message().getString());
			}
			Guilds.round(level, hall, entity);
		}

		// The four edicts; in the reformed part they are reformed from the first morning.
		for (String id : EDICTS) {
			Optional<Edicts.Edict> edict = Edicts.find(id);
			Edicts.Result result = edict.map(e -> Edicts.proclaim(level, hall, null, e)).orElse(null);
			if (result == null || !result.done()) {
				AliveWorkplace.LOG.warn("Season: {} not proclaimed: {}", id, result == null ? "no such edict" : result.message().getString());
			}
		}
		if (part == Part.REFORMED) {
			reform(run, entity);
		}

		// The five whose pace is recorded: the Guild Master, another farmer, the chef, the porter and a mason.
		track(run, farmers.isEmpty() ? null : farmers.get(0), "the Guild Master (farmer)");
		track(run, farmers.size() < 2 ? null : farmers.get(1), "a farmer");
		track(run, chef, "the chef");
		track(run, porter, "the porter");
		track(run, mason, "a mason");
		run.day = Chronicle.day(level);
		run.leftBefore = meals(run);
		MaterialLedger.start();
		return run;
	}

	private static void track(Run run, @Nullable Villager villager, String name) {
		if (villager != null) {
			run.five.add(villager);
			run.fiveNames.add(name);
		}
	}

	/** All four edicts reformed, as a village that finished every reform step. */
	static void reform(Run run, VillageHallBlockEntity entity) {
		List<Reforms.Progress> done = new ArrayList<>();
		for (String id : EDICTS) {
			done.add(new Reforms.Progress(Edicts.id(id).toString(), 3, true, 0));
		}
		entity.setReforms(done);
		Moods.forget();
		run.reformed = true;
	}

	private static BlueprintData.Placement placement(ServerLevel level, BlockPos origin) {
		return new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE);
	}

	/** Flat ground: grass over dirt and stone, open sky. */
	static void ground(ServerLevel level, BlockPos hall) {
		int ground = hall.getY() - 1;
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				int x = hall.getX() + dx;
				int z = hall.getZ() + dz;
				level.getChunk(x >> 4, z >> 4);
				for (int y = ground - 3; y <= ground + 24; y++) {
					level.setBlock(new BlockPos(x, y, z), y < ground - 1 ? Blocks.STONE.defaultBlockState()
						: y < ground ? Blocks.DIRT.defaultBlockState()
						: y == ground ? Blocks.GRASS_BLOCK.defaultBlockState()
						: Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	/** A 9x9 field from (x0, z0) from the hall: wet farmland round a water block, sown with {@code crop} at mixed ages. */
	static BoundingBox field(ServerLevel level, BlockPos hall, int x0, int z0, Block crop, RandomSource random) {
		BlockPos corner = hall.offset(x0, -1, z0);
		for (int dx = 0; dx < 9; dx++) {
			for (int dz = 0; dz < 9; dz++) {
				BlockPos soil = corner.offset(dx, 0, dz);
				if (dx == 4 && dz == 4) {
					level.setBlock(soil, Blocks.WATER.defaultBlockState(), 3);
					continue;
				}
				level.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 3);
				level.setBlock(soil.above(), ((CropBlock) crop).getStateForAge(random.nextInt(((CropBlock) crop).getMaxAge() + 1)), 3);
			}
		}
		return BoundingBox.fromCorners(corner, corner.offset(8, 0, 8));
	}

	private static void put(ServerLevel level, BlockPos chest, ItemStack... stacks) {
		if (level.getBlockEntity(chest) instanceof Container box) {
			for (int i = 0; i < stacks.length; i++) {
				box.setItem(i, stacks[i]);
			}
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

	@Nullable
	private static Villager worker(ServerLevel level, BlockPos at, BlockPos station, ResourceKey<PoiType> poi, VillagerProfession job) {
		Villager villager = spawn(level, at);
		if (villager != null) {
			Jobs.employ(level, villager, station, poi, job);
		}
		return villager;
	}

	/** The meals in the village's store now. */
	static long meals(Run run) {
		long meals = 0;
		for (Map.Entry<Item, Long> e : SupplyContainers.contents(run.level, VillageNeeds.store(run.level, run.hall)).entrySet()) {
			if (VillageNeeds.isMeal(new ItemStack(e.getKey()))) {
				meals += e.getValue();
			}
		}
		return meals;
	}

	private static int taken() {
		return MaterialLedger.eaten().values().stream().mapToInt(Integer::intValue).sum();
	}

	private static void tick(MinecraftServer server) {
		Run run = running;
		if (run == null) {
			return;
		}
		long nanos = System.nanoTime();
		// Tick times count only while the server sprints: at the usual 20 a second every tick takes 50 ms, most of it asleep.
		if (run.lastTickAt != 0 && server.tickRateManager().isSprinting()) {
			long took = nanos - run.lastTickAt;
			run.dayNanos += took;
			run.dayTicks++;
			run.dayWorst = Math.max(run.dayWorst, took);
			if (took > 50_000_000L) {
				run.over50++;
				run.longTicks.add(new long[] {java.time.LocalTime.now().toNanoOfDay() / 1_000_000L, took / 1_000_000L});
			}
		}
		run.lastTickAt = server.tickRateManager().isSprinting() ? nanos : 0;
		ServerLevel level = run.level;
		long time = level.getGameTime();
		if (!(level.getBlockEntity(run.hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		grow(run);
		if (time % 20 != 0) {
			return;
		}
		long day = Chronicle.day(level);
		if (day != run.day) {
			endDay(run, entity);
			run.day = day;
			if (run.record.size() >= run.days) {
				finish();
				return;
			}
			if (run.part == Part.BOTH && run.record.size() == run.perPart) {
				reform(run, entity);
				AliveWorkplace.LOG.info("Season: all four edicts are reformed from day {}", run.record.size() + 1);
			}
		}
		long tod = Math.floorMod(level.getDayTime(), VillageNeeds.DAY);
		if (tod >= RUSH_AT && entity.hornDay() != day) {
			WorkHorn.Result rush = WorkHorn.blow(level, null, run.hall);
			if (rush.outcome() == WorkHorn.Outcome.RUSH) {
				run.rushSampleAt = time + 200;
			} else {
				AliveWorkplace.LOG.warn("Season: no rush on day {}: {}", run.record.size() + 1, rush.outcome());
				entity.startRush(day, 0); // not asked again today
			}
		}
		if (run.rushSampleAt >= 0 && time >= run.rushSampleAt) {
			run.rushSampleAt = -1;
			run.rushPace = pace(run);
		}
		if (tod >= MORNING && run.pace.isEmpty() && !WorkHorn.rushing(level, entity)) {
			run.pace = pace(run);
		}
		if (tod >= NOON && Float.isNaN(run.moodNoon)) {
			run.moodNoon = mood(run);
		}
		if (tod >= DUSK && Float.isNaN(run.moodDusk)) {
			run.moodDusk = mood(run);
		}
		if (time % VillageNeeds.CHECK_EVERY == 0) {
			run.samples++;
			if (meals(run) == 0) {
				run.emptySamples++;
			}
			run.illPeak = Math.max(run.illPeak, ill(run));
			everyone(run);
		}
	}

	/**
	 * The fields' random ticks. Vanilla (1.21.1) gives random ticks only to chunks near a player, and the pack server
	 * has none, so nothing would ever grow. Each crop gets what a player standing by would bring it: {@code
	 * randomTickSpeed} picks a tick out of 4096 blocks, through the block's own random tick (so the Harvest Idol's hook
	 * runs as in play).
	 */
	static void grow(Run run) {
		int speed = run.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
		for (int i = 0; i < speed; i++) {
			int pick = run.growth.nextInt(4096);
			if (pick < run.crops.size()) {
				BlockPos pos = run.crops.get(pick);
				BlockState state = run.level.getBlockState(pos);
				if (state.isRandomlyTicking()) {
					state.randomTick(run.level, pos, run.growth);
				}
			}
		}
	}

	private static List<Villager> villagers(Run run) {
		return run.level.getEntitiesOfClass(Villager.class, VillageHalls.area(run.hall), Villager::isAlive);
	}

	private static int ill(Run run) {
		return (int) villagers(run).stream().filter(Sickness::isIll).count();
	}

	/** The average mood of the grown villagers. */
	private static float mood(Run run) {
		int sum = 0;
		int n = 0;
		for (Villager v : villagers(run)) {
			Moods.Mood mood = Moods.of(v);
			if (mood != null) {
				sum += mood.score();
				n++;
			}
		}
		return n == 0 ? 0f : sum / (float) n;
	}

	/** The five's pace now, in percent faster than usual; everyone's is looked at too. */
	private static List<Integer> pace(Run run) {
		everyone(run);
		List<Integer> out = new ArrayList<>();
		for (Villager v : run.five) {
			out.add(v.isAlive() ? Pace.of(v).percent() : 0);
		}
		return out;
	}

	/** The fastest pace anyone in the village works at now, and the least share of the usual time anyone's work takes. */
	private static void everyone(Run run) {
		for (Villager v : villagers(run)) {
			if (v.isBaby()) {
				continue;
			}
			Pace.Breakdown pace = Pace.of(v);
			run.fastest = Math.max(run.fastest, pace.percent());
			run.slowestFactor = Math.min(run.slowestFactor, pace.factor());
		}
	}

	private static void endDay(Run run, VillageHallBlockEntity entity) {
		int taken = taken();
		long today = run.day;
		long left = meals(run);
		int harvested = 0;
		for (Villager farmer : run.farmers) {
			harvested += ModAttachments.FARM_HARVESTED.getOrElse(farmer, 0);
		}
		// What was paid out of the treasury today (the festival): the day's takings less what the treasury grew by.
		int paid = (int) Math.max(0, ((entity.treasuryTotal() - run.treasuryTotalBefore) - (entity.treasury() - run.treasuryBefore)) / 100);
		Day day = new Day(run.record.size() + 1, run.reformed, taken - run.takenBefore, left,
			run.samples > 0 && run.emptySamples == run.samples, entity.births() - run.birthsBefore, villagers(run).size(), ill(run), run.illPeak,
			Float.isNaN(run.moodNoon) ? 0f : run.moodNoon, Float.isNaN(run.moodDusk) ? 0f : run.moodDusk, entity.treasury() / 100,
			paid, entity.festivalDay() == today, entity.festivalMissed() == today, run.pace, run.rushPace, run.fastest,
			run.slowestFactor, run.dayTicks == 0 ? 0 : run.dayNanos / 1e6 / run.dayTicks, run.dayWorst / 1e6, run.over50,
			left - run.leftBefore + (taken - run.takenBefore), harvested - run.harvestedBefore);
		run.record.add(day);
		AliveWorkplace.LOG.info(day.line());
		if (!run.longTicks.isEmpty()) {
			// For the profile (tools/packtest/perf.py --days): which samples fell in a long tick, and whose code they were in.
			StringBuilder ticks = new StringBuilder(LONG_TICKS + " day " + day.number() + ":");
			for (long[] t : run.longTicks) {
				ticks.append(' ').append(t[0]).append('+').append(t[1]);
			}
			AliveWorkplace.LOG.info(ticks.toString());
			run.longTicks.clear();
		}
		run.takenBefore = taken;
		run.leftBefore = left;
		run.harvestedBefore = harvested;
		run.treasuryBefore = entity.treasury();
		run.treasuryTotalBefore = entity.treasuryTotal();
		run.birthsBefore = entity.births();
		run.samples = 0;
		run.emptySamples = 0;
		run.illPeak = 0;
		run.moodNoon = Float.NaN;
		run.moodDusk = Float.NaN;
		run.pace = List.of();
		run.rushPace = List.of();
		run.fastest = 0;
		run.slowestFactor = 1f;
		run.dayNanos = 0;
		run.dayTicks = 0;
		run.dayWorst = 0;
		run.over50 = 0;
	}

	/** Ends the running season now: logs and returns its result line, or null when none is running. */
	@Nullable
	public static String finish() {
		Run run = running;
		if (run == null) {
			return null;
		}
		String result = result(run.record, run.part, villagersAtStart(run));
		AliveWorkplace.LOG.info(result);
		lastResult = result;
		end(run);
		return result;
	}

	private static int villagersAtStart(Run run) {
		return VILLAGERS;
	}

	/**
	 * The result line from the days' numbers. The costs show when the store gave out more meals a day under the edicts
	 * than reformed and the treasury paid for a festival under the edicts but for none reformed; nothing ran away when
	 * the store was never empty for a whole day, nobody's work took less than the cap's share of the usual time, and
	 * the village never shrank. A part run on its own has no other half to compare with, so its costs aren't judged.
	 */
	public static String result(List<Day> days, Part part, int villagersAtStart) {
		Map<Boolean, int[]> sums = new LinkedHashMap<>(); // reformed -> days, meals taken, births, festival emeralds, festivals missed
		sums.put(false, new int[5]);
		sums.put(true, new int[5]);
		boolean empty = false;
		boolean shrank = false;
		float slowest = 1f;
		int fastest = 0;
		int before = villagersAtStart;
		double worst = 0;
		double tickSum = 0;
		int over50 = 0;
		for (Day d : days) {
			int[] s = sums.get(d.reformed());
			s[0]++;
			s[1] += d.taken();
			s[2] += d.births();
			s[3] += d.festivalPaid();
			s[4] += d.festivalMissed() ? 1 : 0;
			empty |= d.emptyAllDay();
			shrank |= d.villagers() < before;
			before = d.villagers();
			slowest = Math.min(slowest, d.slowestFactor());
			fastest = Math.max(fastest, d.fastest());
			worst = Math.max(worst, d.worstMs());
			tickSum += d.tickMs();
			over50 += d.over50();
		}
		int[] edicts = sums.get(false);
		int[] reformed = sums.get(true);
		float cap = Pace.cap();
		boolean beyondCap = slowest < cap - 0.0005f;
		String costs;
		if (part != Part.BOTH || edicts[0] == 0 || reformed[0] == 0) {
			costs = "not judged (one part)";
		} else {
			boolean store = edicts[1] / (float) edicts[0] > reformed[1] / (float) reformed[0];
			boolean treasury = edicts[3] > 0 && reformed[3] == 0;
			costs = store && treasury ? "show" : "DON'T SHOW (" + (store ? "" : "the store gave out no more under the edicts")
				+ (store || treasury ? "" : "; ") + (treasury ? "" : "the treasury paid " + edicts[3] + " under the edicts, " + reformed[3] + " reformed") + ")";
		}
		boolean ranAway = empty || beyondCap || shrank;
		return RESULT + " " + days.size() + " days; under the edicts (" + edicts[0] + " days): " + per(edicts) + "; reformed (" + reformed[0]
			+ " days): " + per(reformed) + "; costs " + costs + "; ran away: " + (ranAway ? "YES" : "nothing") + " (store empty a whole day: "
			+ (empty ? "yes" : "never") + "; fastest pace +" + fastest + "%, least work time " + String.format(Locale.ROOT, "%.3f", slowest)
			+ " of usual, cap " + String.format(Locale.ROOT, "%.3f", cap) + (beyondCap ? " BEYOND THE CAP" : "") + "; villagers "
			+ villagersAtStart + " to " + before + (shrank ? ", SHRANK" : ", never fewer") + "); tick "
			+ String.format(Locale.ROOT, "%.2f", days.isEmpty() ? 0 : tickSum / days.size()) + " ms on average, worst "
			+ String.format(Locale.ROOT, "%.0f", worst) + " ms, " + over50 + " over 50 ms";
	}

	/** Whether a result line passes: the costs show (or weren't judged) and nothing ran away. */
	public static boolean passed(String result) {
		return result.startsWith(RESULT) && !result.contains("DON'T SHOW") && result.contains("ran away: nothing");
	}

	private static String per(int[] s) {
		if (s[0] == 0) {
			return "not run";
		}
		return one(s[1] / (float) s[0]) + " meals a day from the store, " + s[2] + " births, " + s[3] + " emeralds for festivals"
			+ (s[4] > 0 ? " (" + s[4] + " missed for want of money)" : "");
	}

	private static String one(float v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}

	private static String percents(List<Integer> list) {
		if (list.isEmpty()) {
			return "not read";
		}
		List<String> out = new ArrayList<>();
		for (int p : list) {
			out.add((p >= 0 ? "+" : "") + p + "%");
		}
		return String.join(" ", out);
	}

	/** Puts the switches back as they were. */
	private static void end(Run run) {
		MaterialLedger.stop();
		Edicts.setEnabled(run.flags[0]);
		WorkHorn.ENABLED = run.flags[1];
		Cradles.ENABLED = run.flags[2];
		Guilds.ENABLED = run.flags[3];
		HarvestIdols.ENABLED = run.flags[4];
		VillageRaids.ENABLED = run.flags[5];
		BanditCamps.ENABLED = run.flags[6];
		if (running == run) {
			running = null;
		}
	}

	/** Ends a run started by the GameTest without a result. */
	static void abandon(Run run) {
		end(run);
	}

	private SeasonSoak() {
	}
}
