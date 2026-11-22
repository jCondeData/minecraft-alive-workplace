package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (2026-11-22, lane b) of ROADMAP 51.7 (Market Square, Market Cross, Market Corner rebuilt) and 51.8 (Barracks I-III,
 * Guard House, Fire Station, Gatehouse I-II, Lookout Tower I-IV rebuilt), written from their blind briefs
 * ({@code tools/blueprints/briefs/<family>.md}). The drawings' pins and villagers' paths are already checked by
 * {@code QaRebuiltMarkets1121cGameTests} and {@code QaRebuiltDefense1121cGameTests}, and the live threats by the 1122d
 * classes; this class covers the build side a player meets:
 * <ul>
 * <li>a real Builder builds the Market Square, Barracks I, the Fire Station and Gatehouse I from exactly the Material
 * List the player reads on the held blueprint, across a server restart mid-walls, ending with nothing skipped and every
 * block as drawn, and the finished build does what its brief pins (traders' ground, grindstones and bunks as job sites
 * and homes, the basin's water held in, the gates shut and the portcullis drawn up);</li>
 * <li>live upgrades: Barracks I to II and Lookout Tower II to III over a standing build with a guard's grindstone claimed
 * and the player's gear in its chest, both kept;</li>
 * <li>breakage: the Market Cross one material short waits, then finishes once the player brings it; the Lookout Tower
 * II's grindstone broken mid-build is put back;</li>
 * <li>every upgrade keeps the level below's chests, job blocks and beds in their cells, facing the same way; every name
 * reads in American English.</li>
 * </ul>
 */
public class QaRebuiltMarketsDefense1122bGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);
	/** QaDecorations1104fGameTests.exact's supply chests. */
	private static final List<BlockPos> CHESTS = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(1, 2, 5), new BlockPos(4, 2, 1));
	private static final BlockPos[] BARRELS = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);

	// ------------------------------------------------------------------------------------------------------ helpers

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new AssertionError("missing " + entry.id()));
	}

	private static String n(StarterBlueprints.Entry entry) {
		return entry.id().getPath();
	}

	private static String where(BuildSite live) {
		return live == null ? "done" : live.stage() + " " + live.status() + ", placed " + live.placed() + ", skipped " + live.skipped() + ", missing " + live.missing();
	}

	/** Sets the builder rules a player's game uses in these tests (delay 2, no help, no free materials) and restores them. */
	private static void rules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	/** Keeps the clock in working hours (builders go home at night). */
	private static void holdDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		if (level.getDayTime() % 24000 > 11000) {
			helper.setDayTime(2000);
		}
	}

	/** A server restart: the build sites saved and loaded, the builder saved, removed and loaded back. */
	private static Villager restart(GameTestHelper helper, Villager old) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		return (Villager) loaded;
	}

	private static List<BlockPos> inBox(ServerLevel level, BlockPos origin, Vec3i size, int basement, java.util.function.Predicate<BlockState> what) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin.below(basement), origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			if (what.test(level.getBlockState(p))) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static boolean bedHead(BlockState s) {
		return s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD;
	}

	/**
	 * A real Builder builds {@code entry} at {@code origin} from exactly the Material List read off the held blueprint
	 * (QaDecorations1104fGameTests.exact: bench, four supply chests), with a server restart once its walls are going up
	 * (STRUCTURE, at least 20 placed). It must end DONE with every block as drawn and 0 skipped; then {@code after} runs
	 * on the finished build's placement. It must finish within {@code budget} ticks of the test's start (the build time its
	 * brief gives, restart included).
	 */
	private static void exactAcrossARestart(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin, int budget,
		Consumer<BlueprintData.Placement> after) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		rules(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, entry, origin);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(job.placement()));
		UUID id = job.site().id();
		Villager[] who = {job.villager()};
		boolean[] restarted = {false};
		int[] skipped = {0};
		long[] doneAt = {-1};
		helper.onEachTick(() -> {
			holdDay(helper);
			BuildSite live = BuildSiteManager.get(level).get(id);
			if (live == null) {
				if (doneAt[0] < 0) {
					doneAt[0] = helper.getTick();
					LOGGER.info("qa markets/defense 1122b: {} done at tick {} (budget {})", n(entry), doneAt[0], budget);
				}
				return;
			}
			skipped[0] = live.skipped();
			if (!restarted[0] && live.stage() == BuildPlan.Stage.STRUCTURE && live.placed() >= 20) {
				restarted[0] = true;
				who[0] = restart(helper, who[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(id) != null, n(entry) + ": the site didn't come back from the restart");
				LOGGER.info("qa markets/defense 1122b: restarted {} at {}", n(entry), where(BuildSiteManager.get(level).get(id)));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], n(entry) + " never reached its walls (STRUCTURE with 20 placed) to restart in");
			BuildSite live = BuildSiteManager.get(level).get(id);
			helper.assertTrue(live == null, n(entry) + " from exactly its Material List is still building after the restart: " + where(live)
				+ ", builder at " + helper.relativePos(who[0].blockPosition()) + (who[0].isAlive() ? "" : " (dead)"));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), n(entry) + ": " + unfinished.size() + " block(s) not as drawn after the build, e.g. "
				+ unfinished.stream().limit(5).map(p -> p.subtract(job.placement().origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(skipped[0] == 0, n(entry) + ": the build skipped " + skipped[0] + " block(s)");
			helper.assertTrue(doneAt[0] >= 0 && doneAt[0] <= budget, n(entry) + " took " + doneAt[0] + " ticks, its brief gives " + budget);
			after.accept(job.placement());
		});
	}

	// ----------------------------------------------------------------- 1. built from exactly its list, restarted

	/**
	 * market_square.md: built from exactly its list (20,000 ticks in the brief's own test; 30,000 is the critic's fallback),
	 * across a restart. Built, "the traders' 9 x 9" round the middle (6, 1, 6) holds several dozen cells a trader can stand
	 * on (a sturdy top under it, two cells with no collision and no fluid, y -1 to 4 within 4 blocks across of the middle),
	 * and no block of the finished square is a villager job site (only a bell may be a point of interest).
	 */
	//$ gametest_ticks_batch BIG '30000' '"qaMD1122b_marketSquare"'
	@GameTest(template = BIG, timeoutTicks = 30000, batch = "qaMD1122b_marketSquare")
	public void qaTheMarketSquareIsBuiltFromExactlyItsListAcrossARestartWithTradersGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		exactAcrossARestart(helper, StarterBlueprints.MARKET_SQUARE, ORIGIN, 20000, placement -> {
			BlockPos middle = placement.origin().offset(6, 1, 6);
			int cells = 0;
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-4, -2, -4), middle.offset(4, 3, 4))) {
				BlockState under = level.getBlockState(p.below());
				BlockState feet = level.getBlockState(p);
				BlockState head = level.getBlockState(p.above());
				if (under.isFaceSturdy(level, p.below(), Direction.UP) && feet.getCollisionShape(level, p).isEmpty() && feet.getFluidState().isEmpty()
					&& head.getCollisionShape(level, p.above()).isEmpty() && head.getFluidState().isEmpty()) {
					cells++;
				}
			}
			helper.assertTrue(cells >= 36, "the built Market Square has only " + cells + " cells a trader can stand on within 4 of its middle (the brief: several dozen)");
			List<String> sites = new ArrayList<>();
			for (BlockPos p : inBox(level, placement.origin(), StarterBlueprints.MARKET_SQUARE.size(), 0, s -> PoiTypes.forState(s).isPresent() && !s.is(Blocks.BELL))) {
				sites.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath() + " " + p.subtract(placement.origin()).toShortString());
			}
			helper.assertTrue(sites.isEmpty(), "the built Market Square holds villager job sites: " + sites);
		});
	}

	/**
	 * barracks.md, Level I: built from exactly its list across a restart (the brief's own test gives 30,000 ticks). Built:
	 * exactly 2 grindstones, each a Weaponsmith/Guard job site in the game's POI records; exactly 2 beds, each a home POI;
	 * a Training Dummy; and a chest within 8 across and 4 up or down of each grindstone.
	 */
	//$ gametest_ticks_batch BIG '30000' '"qaMD1122b_barracks"'
	@GameTest(template = BIG, timeoutTicks = 30000, batch = "qaMD1122b_barracks")
	public void qaBarracksIIsBuiltFromExactlyItsListAcrossARestartWithTwoGuardsPlaces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.BARRACKS;
		exactAcrossARestart(helper, e, ORIGIN, 30000, placement -> {
			BlockPos o = placement.origin();
			List<BlockPos> grind = inBox(level, o, e.size(), 0, s -> s.is(Blocks.GRINDSTONE));
			List<BlockPos> beds = inBox(level, o, e.size(), 0, QaRebuiltMarketsDefense1122bGameTests::bedHead);
			List<BlockPos> chests = inBox(level, o, e.size(), 0, s -> s.getBlock() instanceof ChestBlock);
			helper.assertTrue(grind.size() == 2, "the built Barracks has " + grind.size() + " grindstones, the brief says 2");
			helper.assertTrue(beds.size() == 2, "the built Barracks has " + beds.size() + " beds, the brief says 2");
			helper.assertTrue(!inBox(level, o, e.size(), 0, s -> s.is(ModBlocks.TRAINING_DUMMY)).isEmpty(), "the built Barracks has no Training Dummy");
			for (BlockPos g : grind) {
				helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.WEAPONSMITH, g), "the built grindstone at " + g.subtract(o).toShortString()
					+ " is no job site in the game's records");
				helper.assertTrue(chests.stream().anyMatch(c -> Math.abs(c.getX() - g.getX()) <= 8 && Math.abs(c.getZ() - g.getZ()) <= 8 && Math.abs(c.getY() - g.getY()) <= 4),
					"no built chest within 8 / 4 of the grindstone at " + g.subtract(o).toShortString());
			}
			for (BlockPos b : beds) {
				helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.HOME, b), "the built bed at " + b.subtract(o).toShortString() + " is no home");
			}
		});
	}

	/**
	 * fire_station.md: built from exactly its list (water buckets included) across a restart, in the brief's plot
	 * (huge_area at (12, 2, 8)). Built: exactly one grindstone and it is a job site; the basin holds at least 2 water
	 * sources at y = 1; no flowing water anywhere in or round the build ("held in on every side and below so it never
	 * flows out").
	 */
	//$ gametest_ticks_batch HUGE '24000' '"qaMD1122b_fireStation"'
	@GameTest(template = HUGE, timeoutTicks = 24000, batch = "qaMD1122b_fireStation")
	public void qaTheFireStationIsBuiltFromExactlyItsListAcrossARestartWithItsWaterHeldIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.FIRE_STATION;
		exactAcrossARestart(helper, e, new BlockPos(12, 2, 8), 12000, placement -> {
			BlockPos o = placement.origin();
			List<BlockPos> grind = inBox(level, o, e.size(), 0, s -> s.is(Blocks.GRINDSTONE));
			helper.assertTrue(grind.size() == 1, "the built Fire Station has " + grind.size() + " grindstones, the brief says exactly 1");
			helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.WEAPONSMITH, grind.get(0)), "the built grindstone is no job site");
			int basin = 0;
			List<String> flowing = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(o.offset(-1, -1, -1), o.offset(e.size().getX(), e.size().getY() - 1, e.size().getZ()))) {
				var fluid = level.getFluidState(p);
				if (fluid.isEmpty()) {
					continue;
				}
				if (!fluid.isSource()) {
					flowing.add(p.subtract(o).toShortString());
				} else if (p.getY() == o.getY() + 1 && level.getBlockState(p).is(Blocks.WATER)) {
					basin++;
				}
			}
			helper.assertTrue(flowing.isEmpty(), "water flows out of the built Fire Station at " + flowing);
			helper.assertTrue(basin >= 2, "the built Fire Station's basin holds " + basin + " water sources at y = 1, the brief says at least 2");
		});
	}

	/**
	 * gatehouse.md, Level I: built from exactly its list across a restart. Built, the pinned gate blocks are there as the
	 * brief draws them: spruce fence gates at (4..6, 1, 2), shut, across the way; the portcullis drawn up, iron bars at
	 * (4..6, 3, 3) with (4..6, 1..2, 3) free; no other fence gate, door or iron bar in the build; and the middle way
	 * (5, 1..2, z) holds nothing but the gate blocks from the outside face to z = 0.
	 */
	//$ gametest_ticks_batch BIG '30000' '"qaMD1122b_gatehouse"'
	@GameTest(template = BIG, timeoutTicks = 30000, batch = "qaMD1122b_gatehouse")
	public void qaGatehouseIIsBuiltFromExactlyItsListAcrossARestartWithItsGatesShutAndBarsUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.GATEHOUSE;
		exactAcrossARestart(helper, e, ORIGIN, 30000, placement -> {
			BlockPos o = placement.origin();
			List<BlockPos> gates = inBox(level, o, e.size(), 0, s -> s.getBlock() instanceof FenceGateBlock).stream().map(p -> p.subtract(o)).toList();
			List<BlockPos> bars = inBox(level, o, e.size(), 0, s -> s.is(Blocks.IRON_BARS)).stream().map(p -> p.subtract(o)).toList();
			List<BlockPos> doors = inBox(level, o, e.size(), 0, s -> s.is(net.minecraft.tags.BlockTags.DOORS)).stream().map(p -> p.subtract(o)).toList();
			helper.assertTrue(gates.equals(List.of(new BlockPos(4, 1, 2), new BlockPos(5, 1, 2), new BlockPos(6, 1, 2))), "the built Gatehouse's fence gates: " + gates);
			helper.assertTrue(bars.equals(List.of(new BlockPos(4, 3, 3), new BlockPos(5, 3, 3), new BlockPos(6, 3, 3))), "the built Gatehouse's iron bars: " + bars);
			helper.assertTrue(doors.isEmpty(), "the built Gatehouse has doors (gate blocks the siege counts): " + doors);
			for (BlockPos g : gates) {
				BlockState s = level.getBlockState(o.offset(g));
				helper.assertTrue(s.is(Blocks.SPRUCE_FENCE_GATE) && !s.getValue(FenceGateBlock.OPEN) && s.getValue(FenceGateBlock.FACING).getAxis() == Direction.Axis.Z,
					"the built gate at " + g.toShortString() + " is " + s + " (want a shut spruce fence gate across the way)");
			}
			for (int x = 4; x <= 6; x++) {
				for (int y = 1; y <= 2; y++) {
					BlockPos p = o.offset(x, y, 3);
					helper.assertTrue(level.getBlockState(p).getCollisionShape(level, p).isEmpty(), "under the drawn-up portcullis " + new BlockPos(x, y, 3).toShortString()
						+ " is " + level.getBlockState(p));
				}
			}
			for (int z = 0; z < e.size().getZ(); z++) {
				for (int y = 1; y <= 2; y++) {
					BlockPos p = o.offset(5, y, z);
					BlockState s = level.getBlockState(p);
					helper.assertTrue(s.getBlock() instanceof FenceGateBlock || s.getCollisionShape(level, p).isEmpty(), "the middle way is blocked at "
						+ new BlockPos(5, y, z).toShortString() + " by " + s);
				}
			}
		});
	}

	// --------------------------------------------------------------------------------------- 2. live upgrades

	private static void stockBarrels(GameTestHelper helper, List<ItemStack> stock, int lift) {
		helper.assertTrue(stock.size() <= BARRELS.length * 27, "(setup) the test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < BARRELS.length; i++) {
			helper.setBlock(BARRELS[i].above(lift), Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(BARRELS[i].above(lift));
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}

	private static String contents(Container c) {
		List<String> out = new ArrayList<>();
		for (int slot = 0; slot < c.getContainerSize(); slot++) {
			if (!c.getItem(slot).isEmpty()) {
				out.add(slot + ":" + c.getItem(slot).getCount() + "x" + BuiltInRegistries.ITEM.getKey(c.getItem(slot).getItem()).getPath());
			}
		}
		return out.toString();
	}

	/**
	 * The level below stands finished at {@code at}; a guard's grindstone ({@code grindstone}, blueprint cell) is claimed
	 * as a job site; the player's gear (a diamond sword, 20 arrows, an iron chestplate) is in the chest at {@code chest};
	 * a Builder stocked with exactly what the upgrade still needs (the supply report) builds {@code upper} over it. When
	 * DONE: every block of the upper level as drawn, the grindstone still claimed (never broken and re-placed), the gear
	 * still in the chest slot for slot, and {@code after} on the finished upgrade. {@code grindstone} may be null (no job
	 * block). {@code lift} raises the ground: earth fills the test area up to {@code lift} blocks above its floor (a
	 * basement is dug into it) and the bench, barrels and builder stand on it.
	 */
	private static void upgradeKeeps(GameTestHelper helper, StarterBlueprints.Entry lower, StarterBlueprints.Entry upper, BlockPos at, int lift, BlockPos grindstone,
		BlockPos chest, Consumer<BlockPos> after) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		for (int y = 2; y < 2 + lift; y++) {
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					helper.setBlock(new BlockPos(x, y, z), y == 1 + lift ? Blocks.GRASS_BLOCK : Blocks.DIRT);
				}
			}
		}
		BlockPos origin = helper.absolutePos(at.above(lift));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(lower.id()).orElseThrow()
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1122L), 2);
		BuildSiteManager.get(level).recordFinished(lower.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BlockPos grind = grindstone == null ? null : origin.offset(grindstone);
		if (grind != null) {
			helper.assertTrue(level.getBlockState(grind).is(Blocks.GRINDSTONE), "(setup) no grindstone at " + grindstone.toShortString() + " of " + n(lower));
			helper.assertTrue(level.getPoiManager().take(h -> h.is(PoiTypes.WEAPONSMITH), (h, p) -> p.equals(grind), grind, 1).isPresent(),
				"(setup) the grindstone at " + grindstone.toShortString() + " couldn't be claimed");
		}
		BlockPos box = origin.offset(chest);
		helper.assertTrue(level.getBlockEntity(box) instanceof Container, "(setup) no chest at " + chest.toShortString() + " of " + n(lower));
		Container gear = (Container) level.getBlockEntity(box);
		gear.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
		gear.setItem(4, new ItemStack(Items.ARROW, 20));
		gear.setItem(13, new ItemStack(Items.IRON_CHESTPLATE));
		String before = contents(gear);
		helper.setBlock(BENCH.above(lift), ModBlocks.BUILDERS_BENCH);
		var report = BlueprintSupplies.check(level, new BlueprintData(upper.id(), Optional.of(upper.size()), Optional.of(placement)))
			.orElseThrow(() -> new AssertionError("no supply report for " + n(upper)));
		List<ItemStack> stock = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!stock.isEmpty(), "(setup) the upgrade needs nothing: " + report);
		stockBarrels(helper, stock, lift);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2 + lift, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH.above(lift)));
		BuildSite site = Builders.start(level, builder, null, upper.id(), placement);
		helper.assertTrue(site != null, n(upper) + " didn't start over the finished " + n(lower));
		UUID id = site.id();
		int[] skipped = {0};
		helper.onEachTick(() -> {
			holdDay(helper);
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (s != null) {
				skipped[0] = s.skipped();
			}
		});
		helper.succeedWhen(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, n(upper) + " over " + n(lower) + " is still building: " + where(s));
			List<BlockPos> wrong = BuildPlan.create(blueprint(helper, upper), placement).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of " + n(upper) + " wrong after the upgrade, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(skipped[0] == 0, "the upgrade to " + n(upper) + " skipped " + skipped[0] + " block(s)");
			if (grind != null) {
				helper.assertTrue(level.getBlockState(grind).is(Blocks.GRINDSTONE), "the guard's grindstone at " + grindstone.toShortString() + " is gone after the upgrade");
				helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.WEAPONSMITH, grind) && level.getPoiManager().getFreeTickets(grind) == 0,
					"the guard's claimed grindstone at " + grindstone.toShortString() + " was freed by the upgrade (broken and re-placed): he lost his post");
			}
			helper.assertTrue(level.getBlockEntity(box) instanceof Container, "the gear chest at " + chest.toShortString() + " is gone after the upgrade");
			String now = contents((Container) level.getBlockEntity(box));
			helper.assertTrue(now.equals(before), "the player's gear in the chest at " + chest.toShortString() + " changed in the upgrade: " + before + " -> " + now);
			after.accept(origin);
		});
	}

	/**
	 * barracks.md, "Kept cells": Barracks II built over a standing Barracks I keeps the guard's claimed grindstone (2, 1, 2)
	 * and the gear in his chest (2, 1, 3); built, II has exactly 4 grindstones and 4 beds (the quest and guide texts).
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaMD1122b_barracksUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaMD1122b_barracksUpgrade")
	public void qaBarracksIIOverIKeepsTheGuardsClaimedGrindstoneAndHisGear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry two = StarterBlueprints.BARRACKS_2;
		upgradeKeeps(helper, StarterBlueprints.BARRACKS, two, new BlockPos(6, 2, 9), 0, new BlockPos(2, 1, 2), new BlockPos(2, 1, 3), origin -> {
			int grind = inBox(level, origin, two.size(), 0, s -> s.is(Blocks.GRINDSTONE)).size();
			int beds = inBox(level, origin, two.size(), 0, QaRebuiltMarketsDefense1122bGameTests::bedHead).size();
			helper.assertTrue(grind == 4 && beds == 4, "Barracks II built over I has " + grind + " grindstones and " + beds + " beds, the brief says 4 and 4");
		});
	}

	/**
	 * lookout_tower.md, "Kept cells": Lookout Tower III built over a standing II keeps II's guard's claimed grindstone
	 * (5, 1, 5) and the gear in II's chest (7, 1, 5); built, III has at least two guard job blocks and its bunks.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaMD1122b_towerUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaMD1122b_towerUpgrade")
	public void qaLookoutTowerIIIOverIIKeepsTheWatchmansClaimedGrindstoneAndHisGear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry three = StarterBlueprints.LOOKOUT_TOWER_3;
		upgradeKeeps(helper, StarterBlueprints.LOOKOUT_TOWER_2, three, new BlockPos(8, 2, 10), 0, new BlockPos(5, 1, 5), new BlockPos(7, 1, 5), origin -> {
			int posts = inBox(level, origin, three.size(), 0, s -> s.is(Blocks.GRINDSTONE) || s.is(ModBlocks.GUARD_POST)).size();
			int beds = inBox(level, origin, three.size(), 0, QaRebuiltMarketsDefense1122bGameTests::bedHead).size();
			helper.assertTrue(posts >= 2, "Lookout Tower III built over II has " + posts + " guard job blocks, the brief says at least 2");
			helper.assertTrue(beds >= 1, "Lookout Tower III built over II has no bunks");
		});
	}

	/**
	 * barracks.md, Level III ("a cellar exactly 4 deep under II, reached from inside"; "III above the ground is II
	 * exactly"): the cellar is dug under a standing Barracks II on real ground (earth 6 deep), with the third guard's
	 * grindstone (14, 1, 2) claimed and the player's gear in its chest (14, 1, 3); both kept; built, III still has 4
	 * grindstones and 4 beds, all above the ground, and a lantern in the cellar.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaMD1122b_barracksCellar"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaMD1122b_barracksCellar")
	public void qaBarracksIIIDigsItsCellarUnderIIKeepingTheGuardsPostAndGear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry three = StarterBlueprints.BARRACKS_3;
		upgradeKeeps(helper, StarterBlueprints.BARRACKS_2, three, new BlockPos(4, 2, 10), 6, new BlockPos(14, 1, 2), new BlockPos(14, 1, 3), origin -> {
			List<BlockPos> grind = inBox(level, origin, three.size(), 4, s -> s.is(Blocks.GRINDSTONE));
			List<BlockPos> beds = inBox(level, origin, three.size(), 4, QaRebuiltMarketsDefense1122bGameTests::bedHead);
			helper.assertTrue(grind.size() == 4 && grind.stream().allMatch(p -> p.getY() > origin.getY()), "Barracks III built over II has grindstones at "
				+ grind.stream().map(p -> p.subtract(origin).toShortString()).toList() + " (want 4, all above the ground)");
			helper.assertTrue(beds.size() == 4 && beds.stream().allMatch(p -> p.getY() > origin.getY()), "Barracks III built over II has beds at "
				+ beds.stream().map(p -> p.subtract(origin).toShortString()).toList() + " (want 4, all above the ground)");
			helper.assertTrue(!inBox(level, origin.below(4), new Vec3i(three.size().getX(), 4, three.size().getZ()), 0, s -> s.is(Blocks.LANTERN)).isEmpty(),
				"Barracks III built over II has no lantern in its cellar");
		});
	}

	/**
	 * lookout_tower.md, Level IV ("a basement exactly 4 deep under the build; nothing above the ground needs to change"):
	 * the basement is dug under a standing Lookout Tower III on real ground (earth 6 deep), with the second guard's
	 * grindstone (16, 1, 2) claimed and the player's gear in III's chest (7, 1, 5); both kept; built, IV has the
	 * basement's lantern below the ground and no more beds than III.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaMD1122b_towerBasement"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaMD1122b_towerBasement")
	public void qaLookoutTowerIVDigsItsBasementUnderIIIKeepingTheGuardsPostAndGear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry four = StarterBlueprints.LOOKOUT_TOWER_4;
		upgradeKeeps(helper, StarterBlueprints.LOOKOUT_TOWER_3, four, new BlockPos(8, 2, 12), 6, new BlockPos(16, 1, 2), new BlockPos(7, 1, 5), origin -> {
			helper.assertTrue(!inBox(level, origin.below(4), new Vec3i(four.size().getX(), 4, four.size().getZ()), 0, s -> s.is(Blocks.LANTERN)).isEmpty(),
				"Lookout Tower IV built over III has no lantern in its basement");
			int beds = inBox(level, origin, four.size(), 4, QaRebuiltMarketsDefense1122bGameTests::bedHead).size();
			helper.assertTrue(beds <= 2, "Lookout Tower IV built over III has " + beds + " beds, more than III's 2");
		});
	}

	/**
	 * gatehouse.md, "Level II over level I": Gatehouse II built over a standing Gatehouse I keeps the player's things in
	 * the walkway chest (2, 6, 3) and level I's gates and bars in their cells; built, both gate lines' six spruce fence
	 * gates are shut and both portcullises drawn up (iron bars at (4..6, 3, 3) and (4..6, 3, 7) only).
	 */
	//$ gametest_ticks_batch HUGE '30000' '"qaMD1122b_gatehouseUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 30000, batch = "qaMD1122b_gatehouseUpgrade")
	public void qaGatehouseIIOverIKeepsTheWalkwayChestAndBothGateLines(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry two = StarterBlueprints.GATEHOUSE_2;
		upgradeKeeps(helper, StarterBlueprints.GATEHOUSE, two, new BlockPos(10, 2, 10), 0, null, new BlockPos(2, 6, 3), origin -> {
			List<BlockPos> gates = inBox(level, origin, two.size(), 0, s -> s.getBlock() instanceof FenceGateBlock).stream().map(p -> p.subtract(origin)).toList();
			List<BlockPos> bars = inBox(level, origin, two.size(), 0, s -> s.is(Blocks.IRON_BARS)).stream().map(p -> p.subtract(origin)).toList();
			List<BlockPos> wantGates = new ArrayList<>();
			List<BlockPos> wantBars = new ArrayList<>();
			for (int x = 4; x <= 6; x++) {
				wantGates.add(new BlockPos(x, 1, 2));
				wantGates.add(new BlockPos(x, 1, 8));
				wantBars.add(new BlockPos(x, 3, 3));
				wantBars.add(new BlockPos(x, 3, 7));
			}
			helper.assertTrue(new java.util.HashSet<>(gates).equals(new java.util.HashSet<>(wantGates)), "Gatehouse II's fence gates after the upgrade: " + gates);
			helper.assertTrue(new java.util.HashSet<>(bars).equals(new java.util.HashSet<>(wantBars)), "Gatehouse II's iron bars after the upgrade: " + bars);
			for (BlockPos g : gates) {
				helper.assertTrue(!level.getBlockState(origin.offset(g)).getValue(FenceGateBlock.OPEN), "the gate at " + g.toShortString() + " is open after the upgrade");
			}
		});
	}

	/**
	 * lookout_tower.md, Level I: built from exactly its list across a restart within the brief's 9,000 ticks (the critic:
	 * "Level I grows to 430 blocks"); built, no bed (a bed would make the Steward upgrade towers for homes) and a door on
	 * the ground floor.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaMD1122b_tower"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaMD1122b_tower")
	public void qaLookoutTowerIIsBuiltFromExactlyItsListAcrossARestartWithinItsTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.LOOKOUT_TOWER;
		exactAcrossARestart(helper, e, ORIGIN, 9000, placement -> {
			BlockPos o = placement.origin();
			helper.assertTrue(inBox(level, o, e.size(), 0, QaRebuiltMarketsDefense1122bGameTests::bedHead).isEmpty(), "the built Lookout Tower I has a bed");
			helper.assertTrue(inBox(level, o, e.size(), 0, s -> s.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)).stream().anyMatch(p -> p.getY() - o.getY() == 1),
				"the built Lookout Tower I has no door on its ground floor");
		});
	}

	// ----------------------------------------------------------------------------------------------- 3. breakage

	/**
	 * market_cross.md, built from its list: with the item its walls need most left out of the chests it doesn't finish,
	 * and its site says it is missing that item; once the player puts exactly the missing count in a chest it finishes as
	 * drawn with nothing skipped.
	 */
	//$ gametest_ticks_batch BIG '20000' '"qaMD1122b_crossShort"'
	@GameTest(template = BIG, timeoutTicks = 20000, batch = "qaMD1122b_crossShort")
	public void qaTheMarketCrossShortOfOneMaterialWaitsThenFinishesOnceSupplied(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		rules(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.MARKET_CROSS, ORIGIN);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(job.placement()));
		Map<Item, Integer> structure = new LinkedHashMap<>();
		for (BuildPlan.Step s : job.plan().steps(BuildPlan.Stage.STRUCTURE)) {
			Item item = s.state().getBlock().asItem();
			if (job.list().containsKey(item)) {
				structure.merge(item, 1, Integer::sum);
			}
		}
		helper.assertTrue(!structure.isEmpty(), "the Market Cross's list has nothing its STRUCTURE stage places");
		Item short_ = structure.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
		int count = job.list().get(short_);
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).is(short_)) {
					c.setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		LOGGER.info("qa markets/defense 1122b: market cross short of {} x{}", short_, count);
		boolean[] sawWait = {false};
		boolean[] restocked = {false};
		int[] skipped = {0};
		helper.onEachTick(() -> {
			holdDay(helper);
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			if (live != null) {
				skipped[0] = live.skipped();
				if (!restocked[0] && live.missing().containsKey(short_)) {
					sawWait[0] = true;
				}
			}
			if (!restocked[0] && helper.getTick() >= 5000) {
				helper.assertTrue(live != null, "the Market Cross finished without its " + short_ + " (" + count + " asked for)");
				helper.assertTrue(sawWait[0], "the Market Cross never said it was missing " + short_ + ": " + where(live));
				restocked[0] = true;
				Container c = helper.getBlockEntity(CHESTS.get(0));
				int left = count;
				for (int slot = 0; slot < c.getContainerSize() && left > 0; slot++) {
					if (c.getItem(slot).isEmpty()) {
						int take = Math.min(left, short_.getDefaultMaxStackSize());
						c.setItem(slot, new ItemStack(short_, take));
						left -= take;
					}
				}
				helper.assertTrue(left == 0, "(setup) no room in the chest to restock");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restocked[0], "not restocked yet");
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "restocked, the Market Cross is still building: " + where(live));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "restocked, " + unfinished.size() + " block(s) of the Market Cross are not as drawn");
			helper.assertTrue(skipped[0] == 0, "restocked, the Market Cross skipped " + skipped[0] + " block(s)");
		});
	}

	/**
	 * lookout_tower.md, Level II ("a guard's job block at the tower"): built from exactly its list plus one spare
	 * grindstone in a chest; the moment its grindstone is placed (the site still building) it is broken. The builder puts
	 * it back: the tower ends DONE with every block as drawn, nothing skipped, the grindstone in its cell and a job site.
	 */
	//$ gametest_ticks_batch BIG '24000' '"qaMD1122b_towerBroken"'
	@GameTest(template = BIG, timeoutTicks = 24000, batch = "qaMD1122b_towerBroken")
	public void qaLookoutTowerIIsGrindstoneBrokenMidBuildIsPutBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		rules(helper);
		StarterBlueprints.Entry e = StarterBlueprints.LOOKOUT_TOWER_2;
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, e, ORIGIN);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(job.placement()));
		BlockPos grind = job.placement().origin().offset(5, 1, 5);
		helper.assertTrue(job.plan().steps(BuildPlan.Stage.DECORATION).stream().anyMatch(s -> s.state().is(Blocks.GRINDSTONE))
			|| job.plan().steps(BuildPlan.Stage.STRUCTURE).stream().anyMatch(s -> s.state().is(Blocks.GRINDSTONE)), "(setup) the plan places no grindstone");
		boolean spare = false;
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize() && !spare; slot++) {
				if (c.getItem(slot).isEmpty()) {
					c.setItem(slot, new ItemStack(Items.GRINDSTONE));
					spare = true;
				}
			}
		}
		helper.assertTrue(spare, "(setup) no room for the spare grindstone");
		boolean[] broken = {false};
		int[] skipped = {0};
		helper.onEachTick(() -> {
			holdDay(helper);
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			if (live == null) {
				return;
			}
			skipped[0] = live.skipped();
			if (!broken[0] && level.getBlockState(grind).is(Blocks.GRINDSTONE)) {
				broken[0] = true;
				level.setBlockAndUpdate(grind, Blocks.AIR.defaultBlockState());
				LOGGER.info("qa markets/defense 1122b: broke the lookout tower II's grindstone at {}", where(live));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the grindstone was never placed while the tower was building");
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "after its grindstone was broken the Lookout Tower II is still building: " + where(live));
			helper.assertTrue(level.getBlockState(grind).is(Blocks.GRINDSTONE), "the broken grindstone at (5, 1, 5) wasn't put back: " + level.getBlockState(grind));
			helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.WEAPONSMITH, grind), "the put-back grindstone is no job site");
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the Lookout Tower II not as drawn, e.g. "
				+ unfinished.stream().limit(5).map(p -> p.subtract(job.placement().origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(skipped[0] == 0, "the Lookout Tower II skipped " + skipped[0] + " block(s)");
		});
	}

	// ------------------------------------------------------------------------------- 4. drawings and words

	private static boolean kept(BlockState s) {
		Block b = s.getBlock();
		return s.hasBlockEntity() && !(b instanceof BedBlock) && !s.is(Blocks.BELL) && !s.is(net.minecraft.tags.BlockTags.ALL_SIGNS)
			&& !s.is(net.minecraft.tags.BlockTags.BANNERS) && !s.is(net.minecraft.tags.BlockTags.CAMPFIRES)
			|| PoiTypes.forState(s).isPresent();
	}

	/**
	 * barracks.md, lookout_tower.md and gatehouse.md, "Kept cells": every chest, block with block-entity data, job block
	 * and bed of the level below is the same block in the same cell of the next level, facing the same way (a builder
	 * never breaks a container or a job block, so a turned or moved one can't be finished, and a guard's claimed post or
	 * bunk would be lost): Barracks I, II, III; Lookout Tower I, II, III, IV; Gatehouse I, II.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryDefenseUpgradeKeepsChestsJobBlocksAndBedsInTheirCellsFacingTheSameWay(GameTestHelper helper) {
		List<StarterBlueprints.Entry[]> steps = List.of(
			new StarterBlueprints.Entry[] {StarterBlueprints.BARRACKS, StarterBlueprints.BARRACKS_2},
			new StarterBlueprints.Entry[] {StarterBlueprints.BARRACKS_2, StarterBlueprints.BARRACKS_3},
			new StarterBlueprints.Entry[] {StarterBlueprints.LOOKOUT_TOWER, StarterBlueprints.LOOKOUT_TOWER_2},
			new StarterBlueprints.Entry[] {StarterBlueprints.LOOKOUT_TOWER_2, StarterBlueprints.LOOKOUT_TOWER_3},
			new StarterBlueprints.Entry[] {StarterBlueprints.LOOKOUT_TOWER_3, StarterBlueprints.LOOKOUT_TOWER_4},
			new StarterBlueprints.Entry[] {StarterBlueprints.GATEHOUSE, StarterBlueprints.GATEHOUSE_2});
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry[] step : steps) {
			Map<BlockPos, BlockState> lower = QaBasementGameTests.blocks(blueprint(helper, step[0]));
			Map<BlockPos, BlockState> upper = QaBasementGameTests.blocks(blueprint(helper, step[1]));
			List<String> lost = new ArrayList<>();
			lower.forEach((p, s) -> {
				if (!kept(s) && !(s.getBlock() instanceof BedBlock)) {
					return;
				}
				BlockState u = QaBasementGameTests.at(upper, p);
				boolean same = u.is(s.getBlock());
				if (same && s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && u.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
					same = s.getValue(BlockStateProperties.HORIZONTAL_FACING) == u.getValue(BlockStateProperties.HORIZONTAL_FACING);
				}
				if (same && s.getBlock() instanceof BedBlock) {
					same = s.getValue(BedBlock.PART) == u.getValue(BedBlock.PART);
				}
				if (!same) {
					lost.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + " " + p.toShortString() + " -> " + u);
				}
			});
			if (!lost.isEmpty()) {
				lost.sort(null);
				problems.add(n(step[0]) + " -> " + n(step[1]) + ": " + lost);
			}
		}
		helper.assertTrue(problems.isEmpty(), "an upgrade moves, turns or drops what the level below had: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every name these builds bring reads on the Blueprint Table as its brief names it (Market Square, Market Cross, Market
	 * Corner, Barracks I-III, Guard House, Fire Station, Gatehouse I-II, Lookout Tower I-IV), each from en_us.json; and no
	 * line of en_us.json that names one of these builds uses British spelling (owner, 2026-10-26: American English).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryMarketAndDefenseNameReadsInAmericanEnglish(GameTestHelper helper) {
		Map<StarterBlueprints.Entry, String> names = new LinkedHashMap<>();
		names.put(StarterBlueprints.MARKET_SQUARE, "Market Square");
		names.put(StarterBlueprints.MARKET_CROSS, "Market Cross");
		names.put(StarterBlueprints.MARKET_CORNER, "Market Corner");
		names.put(StarterBlueprints.BARRACKS, "Barracks");
		names.put(StarterBlueprints.BARRACKS_2, "Barracks II");
		names.put(StarterBlueprints.BARRACKS_3, "Barracks III");
		names.put(StarterBlueprints.GUARD_HOUSE, "Guard House");
		names.put(StarterBlueprints.FIRE_STATION, "Fire Station");
		names.put(StarterBlueprints.GATEHOUSE, "Gatehouse");
		names.put(StarterBlueprints.GATEHOUSE_2, "Gatehouse II");
		names.put(StarterBlueprints.LOOKOUT_TOWER, "Lookout Tower");
		names.put(StarterBlueprints.LOOKOUT_TOWER_2, "Lookout Tower II");
		names.put(StarterBlueprints.LOOKOUT_TOWER_3, "Lookout Tower III");
		names.put(StarterBlueprints.LOOKOUT_TOWER_4, "Lookout Tower IV");
		com.google.gson.JsonObject lang;
		try (InputStream in = QaRebuiltMarketsDefense1122bGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "en_us.json not found");
			lang = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException ex) {
			throw new AssertionError(ex);
		}
		List<String> bad = new ArrayList<>();
		names.forEach((entry, want) -> {
			String key = "blueprint.aliveworkplace." + n(entry);
			if (!lang.has(key)) {
				bad.add(key + " missing from en_us.json");
			} else if (!lang.get(key).getAsString().equals(want)) {
				bad.add(key + " reads \"" + lang.get(key).getAsString() + "\", the brief names it \"" + want + "\"");
			}
			String shown = Blueprints.displayName(entry.id()).getString();
			if (!shown.equals(want)) {
				bad.add(n(entry) + " shows on the Table as \"" + shown + "\", want \"" + want + "\"");
			}
		});
		java.util.regex.Pattern british = java.util.regex.Pattern.compile("(?i)\\b(defence|defences|armour|armoury|colour|centre|honour|harbour|grey|cancelled|jeweller|neighbour|organise|realise)\\b");
		java.util.regex.Pattern ours = java.util.regex.Pattern.compile("(?i)market square|market cross|market corner|barracks|guard house|fire station|gatehouse|lookout tower");
		for (Map.Entry<String, com.google.gson.JsonElement> line : lang.entrySet()) {
			String v = line.getValue().getAsString();
			if (ours.matcher(v).find() && british.matcher(v).find()) {
				bad.add(line.getKey() + " = \"" + v + "\" (British spelling)");
			}
		}
		helper.assertTrue(bad.isEmpty(), "names: " + bad);
		helper.succeed();
	}
}
