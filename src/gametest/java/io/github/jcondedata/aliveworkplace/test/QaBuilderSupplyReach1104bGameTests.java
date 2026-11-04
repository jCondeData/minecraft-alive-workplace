package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1104-1238) of where a Builder takes his materials from, written from docs/wiki/builder.md "Materials": "A
 * Builder takes what it needs from the chests and barrels within {@code supplyRadius} of the bench (and 4 blocks up or
 * down)"; "A new build leaves out the chests inside its own box (they are what it puts up, its owner's)"; and
 * config-switches.md: {@code supplyRadius} 8 (2 to 32), "Chests and barrels within this many blocks of a workstation are
 * that villager's supply chests". The counting at those edges (the Book and Quill list) is QaBuilderLimitsGameTests' and
 * QaBuilderSuppliesGameTests'; these tests check the real job: what the Builder actually TAKES and builds, on both sides
 * of each edge, across a config change and a save and reload mid-job.
 *
 * <p>Layout: a Builder's Bench at (2,2,2), the main chest at (2,2,4) holding the test hut's cobblestone and planks, the hut
 * at (6,2,6). The hut's door and torch (one of each) are put in the containers under test: whichever is in reach is
 * built, whichever is out of reach is never taken and never built.
 */
public class QaBuilderSupplyReach1104bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);
	/** Ticks a stalled build is watched after everything else stands, to be sure the out-of-reach item stays put. */
	private static final int WATCH = 300;

	private record Run(ServerLevel level, Villager[] builder, UUID site, BuildPlan plan) {
		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
		}
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	private static ServerLevel rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
		});
		helper.assertTrue(SupplyContainers.RADIUS == 8, "the suite should run with the default supplyRadius 8, not " + SupplyContainers.RADIUS);
		return level;
	}

	/** Puts the config's supplyRadius in effect as a config file read would, and the defaults back when the test ends. */
	private static void radius(GameTestHelper helper, int r) {
		WorkplaceConfig.parse("{\"supplyRadius\": " + r + "}").apply();
		helper.assertTrue(SupplyContainers.RADIUS == r, "supplyRadius " + r + " wasn't put into effect: " + SupplyContainers.RADIUS);
	}

	private static void restoreConfigAfter(GameTestHelper helper) {
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
	}

	/** A Builder at his bench, the main chest holding the hut's cobblestone and planks (exactly), starting the test hut. */
	private static Run start(GameTestHelper helper, ServerLevel level) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(BENCH));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, TEST_HUT, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		helper.assertTrue(plan.materials().getOrDefault(Items.OAK_DOOR, 0) == 1 && plan.materials().getOrDefault(Items.TORCH, 0) == 1,
			"setup: the test hut should take one door and one torch: " + plan.materials());
		LOGGER.info("qa supply reach: hut bounds {} materials {}", plan.bounds(), plan.materials());
		return new Run(level, new Villager[]{villager}, site.id(), plan);
	}

	/** A container block at {@code pos} holding {@code items} (a fresh one: whatever stood there is replaced). */
	private static void stock(GameTestHelper helper, BlockPos pos, Block block, ItemStack... items) {
		helper.setBlock(pos, block);
		Container c = helper.getBlockEntity(pos);
		for (int i = 0; i < items.length; i++) {
			c.setItem(i, items[i].copy());
		}
	}

	/** A double chest of two halves at {@code left} and {@code right}, each half's block entity, facing {@code facing}. */
	private static void doubleChest(GameTestHelper helper, BlockPos left, BlockPos right, Direction facing) {
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
		helper.setBlock(left, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		helper.assertTrue(ChestBlock.getConnectedDirection(helper.getBlockState(left)) == Direction.fromDelta(right.getX() - left.getX(),
			right.getY() - left.getY(), right.getZ() - left.getZ()), "setup: the two halves don't join into a double chest");
	}

	private static int in(GameTestHelper helper, BlockPos pos, Item item) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c ? c.countItem(item) : -1;
	}

	private static int bag(Run r, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(r.builder()[0]).count(item);
	}

	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	/** Every place of the plan (any stage) whose block is {@code item}'s. */
	private static Set<BlockPos> placesOf(Run r, Item item) {
		Set<BlockPos> out = new HashSet<>();
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			for (BuildPlan.Step s : r.plan().steps(stage)) {
				if (!s.state().isAir() && s.state().getBlock().asItem() == item && stage != BuildPlan.Stage.CLEAR) {
					out.add(s.pos().immutable());
				}
			}
		}
		return out;
	}

	private static List<BlockPos> unfinished(Run r) {
		return r.plan().unfinished(r.level());
	}

	private static String state(GameTestHelper helper, Run r) {
		BuildSite site = r.live();
		List<BlockPos> wrong = unfinished(r);
		return (site == null ? "site gone" : "stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing() + " skipped="
			+ site.skipped()) + "; " + wrong.size() + " unfinished e.g. " + wrong.stream().limit(4).map(p -> helper.relativePos(p) + "="
			+ r.level().getBlockState(p)).collect(Collectors.joining(", ")) + "; bag " + ModAttachments.BUILDER_BAG.getOrCreate(r.builder()[0]).stacks()
			+ "; builder at " + helper.relativePos(r.builder()[0].blockPosition());
	}

	/** Keeps the day at work hours and the watch counter for {@link #stalledOn}. */
	private static void everyTick(GameTestHelper helper, Runnable also) {
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			also.run();
		});
	}

	/**
	 * True once everything but {@code item}'s places stands (or the site is gone) and has for {@link #WATCH} ticks;
	 * {@code since} holds the tick it first stood so.
	 */
	private static boolean stalledOn(GameTestHelper helper, Run r, Item item, long[] since) {
		return stalledOn(helper, r, item, since, Set.of());
	}

	/** The same, {@code alsoLeft} (a container standing where the plan draws air, never broken) left unfinished too. */
	private static boolean stalledOn(GameTestHelper helper, Run r, Item item, long[] since, Set<BlockPos> alsoLeft) {
		Set<BlockPos> places = new HashSet<>(placesOf(r, item));
		places.addAll(alsoLeft);
		List<BlockPos> wrong = unfinished(r);
		boolean onlyThat = places.containsAll(wrong);
		if (!onlyThat) {
			since[0] = -1;
			return false;
		}
		long now = helper.getTick();
		if (since[0] < 0) {
			since[0] = now;
		}
		return now - since[0] >= WATCH;
	}

	/**
	 * True once the site has waited on exactly one {@code item} (and nothing else) for {@link #WATCH} ticks; {@code since}
	 * holds the tick it began.
	 */
	private static boolean waitingOn(GameTestHelper helper, Run r, Item item, long[] since) {
		BuildSite site = r.live();
		if (site == null || site.status() != BuildSite.Status.WAITING_FOR_MATERIALS || !site.missing().equals(java.util.Map.of(item, 1))) {
			since[0] = -1;
			return false;
		}
		long now = helper.getTick();
		if (since[0] < 0) {
			since[0] = now;
		}
		return now - since[0] >= WATCH;
	}

	/** A server restart: every build site and the Builder saved and loaded again (the Builder as a fresh entity). */
	private static void restart(GameTestHelper helper, Run r) {
		ServerLevel level = r.level();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		Villager old = r.builder()[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		r.builder()[0] = (Villager) loaded;
	}

	/**
	 * The shared check for "in reach is taken, out of reach is not": the hut stands but for {@code out}'s places, {@code in}
	 * (one) was taken from {@code inAt} and stands, and {@code out} still lies in {@code outAt} (none in the bag, none
	 * on the ground) after the build has had {@link #WATCH} ticks with nothing else to do.
	 */
	private static void assertTakenAndLeft(GameTestHelper helper, Run r, BlockPos inAt, Item inItem, BlockPos outAt, Item outItem, String what) {
		helper.assertTrue(r.builder()[0].isAlive(), "the builder died: " + state(helper, r));
		helper.assertTrue(in(helper, inAt, inItem) == 0, what + ": the " + inItem + " in reach at " + inAt + " was never taken: " + state(helper, r));
		Set<BlockPos> inPlaces = placesOf(r, inItem);
		helper.assertTrue(unfinished(r).stream().noneMatch(inPlaces::contains), what + ": the " + inItem + " taken from reach isn't built: " + state(helper, r));
		helper.assertTrue(in(helper, outAt, outItem) == 1, what + ": the " + outItem + " out of reach at " + outAt + " was taken (it holds "
			+ in(helper, outAt, outItem) + "): " + state(helper, r));
		helper.assertTrue(bag(r, outItem) == 0 && onGround(helper, outItem) == 0, what + ": a " + outItem + " out of reach turned up in the bag ("
			+ bag(r, outItem) + ") or on the ground (" + onGround(helper, outItem) + ")");
		Set<BlockPos> outPlaces = placesOf(r, outItem);
		helper.assertTrue(unfinished(r).containsAll(outPlaces), what + ": the " + outItem + " stands though none was in reach: " + state(helper, r));
		LOGGER.info("qa supply reach {}: took the {} at {}, left the {} at {}; {}", what, inItem, inAt, outItem, outAt, state(helper, r));
	}

	// ------------------------------------------------------------------------------------------- the 8-block edge

	/**
	 * "within supplyRadius of the bench" (8): the door in a chest exactly 8 blocks east is fetched and hung; the torch in a
	 * barrel 9 blocks east is never taken, though the hut waits on nothing else.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaReachEast"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaReachEast")
	public void aChestEightEastIsTakenFromAndABarrelNineEastIsNot(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, level);
		BlockPos eight = BENCH.east(8), nine = BENCH.east(9);
		stock(helper, eight, Blocks.CHEST, new ItemStack(Items.OAK_DOOR));
		stock(helper, nine, Blocks.BARREL, new ItemStack(Items.TORCH));
		long[] since = {-1};
		everyTick(helper, () -> {
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stalledOn(helper, r, Items.TORCH, since), "not yet down to the torch: " + state(helper, r));
			assertTakenAndLeft(helper, r, eight, Items.OAK_DOOR, nine, Items.TORCH, "east 8/9");
		});
	}

	/**
	 * The same edge on the other axis, the container kinds swapped: the door in a barrel 8 blocks south is fetched, the
	 * torch in a chest 9 blocks south (and one east, so it is 9 off on the long side only) is never taken.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaReachSouth"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaReachSouth")
	public void aBarrelEightSouthIsTakenFromAndAChestNineSouthIsNot(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, level);
		BlockPos eight = BENCH.south(8), nine = BENCH.south(9).east();
		stock(helper, eight, Blocks.BARREL, new ItemStack(Items.OAK_DOOR));
		stock(helper, nine, Blocks.CHEST, new ItemStack(Items.TORCH));
		long[] since = {-1};
		everyTick(helper, () -> {
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stalledOn(helper, r, Items.TORCH, since), "not yet down to the torch: " + state(helper, r));
			assertTakenAndLeft(helper, r, eight, Items.OAK_DOOR, nine, Items.TORCH, "south 8/9");
		});
	}

	/**
	 * "4 blocks up or down": the door in a barrel 4 blocks above the bench's level is fetched, the torch in a chest 5
	 * above (both within a block or two sideways) is never taken.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaReachUp"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaReachUp")
	public void aBarrelFourUpIsTakenFromAndAChestFiveUpIsNot(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, level);
		BlockPos four = BENCH.above(4).west(1), five = BENCH.above(5).north(1);
		stock(helper, four, Blocks.BARREL, new ItemStack(Items.OAK_DOOR));
		stock(helper, five, Blocks.CHEST, new ItemStack(Items.TORCH));
		long[] since = {-1};
		everyTick(helper, () -> {
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stalledOn(helper, r, Items.TORCH, since), "not yet down to the torch: " + state(helper, r));
			assertTakenAndLeft(helper, r, four, Items.OAK_DOOR, five, Items.TORCH, "up 4/5");
		});
	}

	/**
	 * The reach is a box, not a ball: a barrel at its far corner (8 east, 8 south and 4 up, 13 blocks off in a straight
	 * line) holds the door and is taken from; a chest one block above that corner (5 up) holds the torch and is not.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaReachCorner"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaReachCorner")
	public void aBarrelAtTheReachsFarCornerIsTakenFromAndAChestOneAboveIsNot(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, level);
		BlockPos corner = BENCH.east(8).south(8).above(4), above = corner.above();
		stock(helper, corner, Blocks.BARREL, new ItemStack(Items.OAK_DOOR));
		stock(helper, above, Blocks.CHEST, new ItemStack(Items.TORCH));
		long[] since = {-1};
		everyTick(helper, () -> {
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stalledOn(helper, r, Items.TORCH, since), "not yet down to the torch: " + state(helper, r));
			assertTakenAndLeft(helper, r, corner, Items.OAK_DOOR, above, Items.TORCH, "corner 8/8/4 vs 5 up");
		});
	}

	/**
	 * A double chest at the edge: its two halves 7 and 8 blocks east, the door and the torch both in the far half (8 off).
	 * The Builder takes both from it and finishes the hut; nothing is left in either half, his bag or on the ground.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaReachDouble"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaReachDouble")
	public void aDoubleChestAtTheEdgeIsTakenFromItsFarHalf(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, level);
		BlockPos far = BENCH.east(8), near = BENCH.east(7);
		// facing south, a LEFT half's other half is to its west
		doubleChest(helper, far, near, Direction.SOUTH);
		Container farHalf = helper.getBlockEntity(far);
		farHalf.setItem(13, new ItemStack(Items.OAK_DOOR));
		farHalf.setItem(26, new ItemStack(Items.TORCH));
		everyTick(helper, () -> {
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(unfinished(r).isEmpty(), "the hut isn't finished: " + state(helper, r));
			helper.assertTrue(r.live() == null, "the hut stands but the site is still open: " + state(helper, r));
			for (Item item : List.of(Items.OAK_DOOR, Items.TORCH)) {
				helper.assertTrue(in(helper, far, item) == 0 && in(helper, near, item) == 0 && bag(r, item) == 0 && onGround(helper, item) == 0,
					item + " left over: far half " + in(helper, far, item) + ", near half " + in(helper, near, item) + ", bag " + bag(r, item));
			}
			LOGGER.info("qa supply reach double chest: hut finished from the far half 8 east");
		});
	}

	// -------------------------------------------------------------------------------------- supplyRadius mid-job

	/**
	 * config-switches.md: supplyRadius, "Chests and barrels within this many blocks of a workstation are that villager's
	 * supply chests". Mid-job (the first trip in the bag, the hut short only of its door) the owner sets it to 6, then a
	 * player puts the door in a chest 7 blocks east: it is never taken while the radius is 6, though the site waits on
	 * the door alone for 300 ticks; set back to 8, the door is fetched and the hut finished.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaReachShrink"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaReachShrink")
	public void aSupplyRadiusShrunkMidJobPastAChestStopsTheTakingAndPutBackResumesIt(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		restoreConfigAfter(helper);
		Run r = start(helper, level);
		BlockPos seven = BENCH.east(7);
		stock(helper, seven, Blocks.CHEST);
		stock(helper, CHEST.east(), Blocks.BARREL, new ItemStack(Items.TORCH));
		boolean[] shrunk = {false}, restored = {false};
		long[] since = {-1};
		everyTick(helper, () -> {
			if (!shrunk[0] && !ModAttachments.BUILDER_BAG.getOrCreate(r.builder()[0]).isEmpty()) {
				radius(helper, 6);
				((Container) helper.getBlockEntity(seven)).setItem(4, new ItemStack(Items.OAK_DOOR));
				shrunk[0] = true;
				LOGGER.info("qa supply reach shrink: supplyRadius 6 and the door put 7 east at {}", state(helper, r));
			}
			if (shrunk[0] && !restored[0]) {
				helper.assertTrue(in(helper, seven, Items.OAK_DOOR) == 1 && bag(r, Items.OAK_DOOR) == 0,
					"with supplyRadius 6 the door 7 blocks east was taken: chest " + in(helper, seven, Items.OAK_DOOR) + "; " + state(helper, r));
				if (waitingOn(helper, r, Items.OAK_DOOR, since)) {
					helper.assertTrue(unfinished(r).containsAll(placesOf(r, Items.OAK_DOOR)), "the door stands with none taken: " + state(helper, r));
					LOGGER.info("qa supply reach shrink: waited {} ticks on the door, radius back to 8; {}", WATCH, state(helper, r));
					new WorkplaceConfig().apply();
					restored[0] = true;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restored[0], "not yet down to the door with supplyRadius 6: " + state(helper, r));
			helper.assertTrue(unfinished(r).isEmpty(), "with supplyRadius 8 again the hut isn't finished: " + state(helper, r));
			helper.assertTrue(in(helper, seven, Items.OAK_DOOR) == 0 && bag(r, Items.OAK_DOOR) == 0, "the door wasn't used");
		});
	}

	/**
	 * The other way: the torch lies in a barrel 10 blocks east, out of the default reach, so the hut waits on it; the
	 * owner raises supplyRadius to 10 mid-job and the Builder fetches it and finishes.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaReachGrow"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaReachGrow")
	public void aSupplyRadiusRaisedMidJobReachesAChestThatWasOutOfReach(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		restoreConfigAfter(helper);
		Run r = start(helper, level);
		BlockPos ten = BENCH.east(10);
		stock(helper, CHEST.east(), Blocks.BARREL, new ItemStack(Items.OAK_DOOR));
		stock(helper, ten, Blocks.BARREL, new ItemStack(Items.TORCH));
		boolean[] grown = {false};
		long[] since = {-1};
		everyTick(helper, () -> {
			if (!grown[0]) {
				helper.assertTrue(in(helper, ten, Items.TORCH) == 1, "with supplyRadius 8 the torch 10 east was taken: " + state(helper, r));
				if (stalledOn(helper, r, Items.TORCH, since)) {
					radius(helper, 10);
					grown[0] = true;
					LOGGER.info("qa supply reach grow: supplyRadius 10 at {}", state(helper, r));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(grown[0], "not yet down to the torch: " + state(helper, r));
			helper.assertTrue(unfinished(r).isEmpty(), "with supplyRadius 10 the hut isn't finished: " + state(helper, r));
			helper.assertTrue(in(helper, ten, Items.TORCH) == 0 && bag(r, Items.TORCH) == 0, "the torch 10 east wasn't used");
		});
	}

	// ------------------------------------------------------------------------------------------ save and reload

	/**
	 * A server restart mid-job (the sites and the Builder saved and loaded, once the first trip is in the bag) changes
	 * nothing about the reach: the door 8 east is still fetched and hung, the torch 9 east is still never taken.
	 */
	//$ gametest_ticks_batch HUGE '8000' '"qaReachReload"'
	@GameTest(template = HUGE, timeoutTicks = 8000, batch = "qaReachReload")
	public void afterARestartMidJobEightIsStillInReachAndNineIsNot(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, level);
		BlockPos eight = BENCH.east(8), nine = BENCH.east(9);
		stock(helper, eight, Blocks.CHEST, new ItemStack(Items.OAK_DOOR));
		stock(helper, nine, Blocks.CHEST, new ItemStack(Items.TORCH));
		boolean[] restarted = {false};
		long[] since = {-1};
		everyTick(helper, () -> {
			if (!restarted[0] && !ModAttachments.BUILDER_BAG.getOrCreate(r.builder()[0]).isEmpty()) {
				restart(helper, r);
				restarted[0] = true;
				LOGGER.info("qa supply reach reload: restarted at {}", state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "the builder never carried anything before the restart");
			helper.assertTrue(stalledOn(helper, r, Items.TORCH, since), "not yet down to the torch: " + state(helper, r));
			assertTakenAndLeft(helper, r, eight, Items.OAK_DOOR, nine, Items.TORCH, "after a restart");
		});
	}

	// ------------------------------------------------------------------------------------- the build's own box

	/**
	 * "A new build leaves out the chests inside its own box (they are what it puts up, its owner's)", on a real job: a
	 * chest standing in the hut's box (a place the hut leaves empty) holds the torch, and a chest just outside the box
	 * holds the door. The door is fetched and hung; the torch is never taken from the chest in the box, the chest is
	 * left standing and the hut waits on the torch.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaReachOwnBox"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaReachOwnBox")
	public void aNewBuildNeverTakesFromAChestInItsOwnBoxButDoesFromOneJustOutside(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, level);
		BoundingBox box = r.plan().bounds();
		Set<BlockPos> drawn = new HashSet<>();
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			if (stage != BuildPlan.Stage.CLEAR) {
				for (BuildPlan.Step s : r.plan().steps(stage)) {
					if (!s.state().isAir()) {
						drawn.add(s.pos().immutable());
					}
				}
			}
		}
		BlockPos inside = null;
		for (BlockPos p : BlockPos.betweenClosed(box.minX() + 1, box.minY() + 1, box.minZ() + 1, box.maxX() - 1, box.minY() + 1, box.maxZ() - 1)) {
			if (!drawn.contains(p) && drawn.contains(p.below())) {
				inside = p.immutable();
				break;
			}
		}
		helper.assertTrue(inside != null, "setup: no empty place inside the hut at " + box);
		BlockPos outside = new BlockPos(box.minX() - 1, box.minY(), box.minZ());
		BlockPos bench = helper.absolutePos(BENCH);
		helper.assertTrue(box.isInside(inside) && !box.isInside(outside) && SupplyContainers.inReach(bench, inside) && SupplyContainers.inReach(bench, outside),
			"setup: both chests should be in the bench's reach, one in the box and one out: " + inside + ", " + outside + ", box " + box);
		// placed by their world positions: the helper may turn the test area, and its relativePos is no exact inverse then
		for (BlockPos at : List.of(inside, outside)) {
			level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
		}
		((Container) level.getBlockEntity(inside)).setItem(0, new ItemStack(Items.TORCH));
		((Container) level.getBlockEntity(outside)).setItem(0, new ItemStack(Items.OAK_DOOR));
		BlockPos in = inside, out = outside;
		helper.assertTrue(SupplyContainers.find(level, bench, null).contains(in) && SupplyContainers.find(level, bench, null).contains(out),
			"setup: the two chests aren't stores the bench reaches: " + SupplyContainers.find(level, bench, null));
		LOGGER.info("qa supply reach own box: the job's stores {} (exclude {}), the chest in the box at {}", SupplyContainers.find(level, bench,
			r.plan().supplyExclude()), r.plan().supplyExclude(), in);
		long[] since = {-1};
		everyTick(helper, () -> {
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stalledOn(helper, r, Items.TORCH, since, Set.of(in)), "not yet down to the torch: " + state(helper, r));
			helper.assertTrue(level.getBlockState(in).is(Blocks.CHEST), "the chest in the hut's box was broken: " + level.getBlockState(in));
			helper.assertTrue(((Container) level.getBlockEntity(out)).countItem(Items.OAK_DOOR) == 0, "the door just outside the box was never taken: "
				+ state(helper, r));
			helper.assertTrue(unfinished(r).stream().noneMatch(placesOf(r, Items.OAK_DOOR)::contains), "the door isn't hung: " + state(helper, r));
			helper.assertTrue(((Container) level.getBlockEntity(in)).countItem(Items.TORCH) == 1 && bag(r, Items.TORCH) == 0
				&& onGround(helper, Items.TORCH) == 0, "the torch in the new build's own box was taken: chest " + ((Container) level.getBlockEntity(in))
				.countItem(Items.TORCH) + ", bag " + bag(r, Items.TORCH) + "; " + state(helper, r));
			helper.assertTrue(unfinished(r).containsAll(placesOf(r, Items.TORCH)), "the torch stands: " + state(helper, r));
			LOGGER.info("qa supply reach own box: door taken from outside the box, torch left in the box; {}", state(helper, r));
		});
	}
}
