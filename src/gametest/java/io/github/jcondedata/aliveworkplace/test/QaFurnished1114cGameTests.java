package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-14, lane C) for furnished builds (ROADMAP 37.2, 37.3, 37.4) meeting the storage a job uses and the Mover
 * (45.1), from the spec: "a config change or a removed mod never changes a build going up" and the choice "costs their
 * items". Played with a real builder on the test nook, the test mod {@code aliveworkplace_test_pieces} (its chair an
 * upturned spruce stair): the builder's chest broken mid-job and put back, the chair's stair only in the far half of a
 * double chest, the double chest split mid-job, a chest otherwise full of a player's diamonds, the switch turned off while
 * the builder waits for the piece, a restart while it waits, and the finished furnished nook captured and moved.
 */
public class QaFurnished1114cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	private record Job(ServerLevel level, Villager builder, BuildSite site, BlueprintData.Placement placement, BlockPos chairAt, BuildPlan plan) {
	}

	private static Job begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		List<String> mods = Furnishings.MODS;
		boolean enabled = Furnishings.ENABLED;
		Leftovers.after(helper, () -> {
			Furnishings.MODS = mods;
			Furnishings.ENABLED = enabled;
		});
		Furnishings.ENABLED = true;
		Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		ServerLevel level = helper.getLevel();
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the nook");
		helper.assertFalse(site.furniture().isVanilla(), "the setup: the nook didn't choose the test mod's chair: " + site.furniture());
		return new Job(level, builder, site, placement, placement.origin().offset(QaFurnishedGameTests.CHAIR), plan);
	}

	private static boolean modChair(BlockState state) {
		return state.is(Blocks.SPRUCE_STAIRS) && state.getValue(StairBlock.HALF) == Half.TOP;
	}

	private static BuildSite live(Job job) {
		return BuildSiteManager.get(job.level).get(job.site.id());
	}

	private static void restart(ServerLevel level) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}

	private static int count(Container chest, Item item) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	private static void take(Container chest, Item item) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				chest.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/** Every planned block stands, the chair is the mod's, and (when known) nothing was skipped. */
	private static void assertWhole(GameTestHelper helper, Job job, int skipped, String when) {
		BuildSite live = live(job);
		helper.assertTrue(live == null, when + ": still building: " + (live == null ? "" : live.stage() + " " + live.status() + " missing " + live.missing()));
		helper.assertTrue(skipped == 0, when + ": " + skipped + " steps skipped");
		for (Map.Entry<BlockPos, BlockState> e : QaFurnishedGameTests.stepStates(job.plan).entrySet()) {
			BlockState there = job.level.getBlockState(e.getKey());
			helper.assertTrue(there.getBlock() == e.getValue().getBlock(), when + ": " + e.getKey() + " is " + there + ", planned " + e.getValue());
		}
		BlockState chair = job.level.getBlockState(job.chairAt);
		helper.assertTrue(modChair(chair), when + ": the finished nook's chair is " + chair + ", wanted the test mod's upturned stair it was begun with");
	}

	/** The chair spot never holds anything but air or the mod's chair while the job runs. */
	private static void neverAStandIn(GameTestHelper helper, Job job, int[] skipped) {
		helper.onEachTick(() -> {
			BuildSite live = live(job);
			if (live != null) {
				skipped[0] = live.skipped();
			}
			BlockState chair = job.level.getBlockState(job.chairAt);
			if (!chair.isAir() && !modChair(chair)) {
				throw new GameTestAssertException("tick " + helper.getTick() + ": the chair spot holds a stand-in " + chair);
			}
		});
	}

	private static void dropsGone(GameTestHelper helper, BlockPos rel) {
		BlockPos at = helper.absolutePos(rel);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3))) {
			e.discard();
		}
	}

	/**
	 * The builder's chest is broken (its contents lost) once the build has started and before the chair is down: the
	 * builder never puts a stand-in on the chair spot and skips nothing; a new chest with the materials put back lets the
	 * nook finish with the mod's chair.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaFurn1114cChestBroken"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaFurn1114cChestBroken")
	public void theBuildersChestBrokenMidJobAndPutBackStillEndsWithTheModsChair(GameTestHelper helper) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		int[] skipped = {0};
		neverAStandIn(helper, job, skipped);
		int[] phase = {0};
		helper.onEachTick(() -> {
			if (phase[0] == 0) {
				BuildSite live = live(job);
				if (live == null) {
					throw new GameTestAssertException("the setup: the nook finished before its chest could be broken");
				}
				if (live.placed() > 0 && job.level.getBlockState(job.chairAt).isAir()) {
					helper.setBlock(QaFurnishedGameTests.CHEST, Blocks.AIR);
					dropsGone(helper, QaFurnishedGameTests.CHEST);
					phase[0] = 1;
					helper.runAfterDelay(400, () -> {
						helper.setBlock(QaFurnishedGameTests.CHEST, Blocks.CHEST);
						QaFurnishedGameTests.stock(helper, job.plan);
						phase[0] = 2;
					});
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "the chest not yet put back");
			assertWhole(helper, job, skipped[0], "the chest broken mid-job and put back");
		});
	}

	/**
	 * The builder's chest is the near half of a double chest and the chair's spruce stair lies only in the far half: the
	 * builder finds it there and the nook ends with the mod's chair, nothing skipped.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurn1114cFarHalf"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurn1114cFarHalf")
	public void theChairsStairInTheFarHalfOfADoubleChestIsUsed(GameTestHelper helper) {
		Job job = begin(helper);
		BlockPos near = QaFurnishedGameTests.CHEST;
		BlockPos far = near.west();
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		helper.setBlock(far, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(near, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		helper.assertTrue(ChestBlock.getConnectedDirection(helper.getBlockState(far)) == Direction.EAST, "setup: the far half doesn't join east");
		QaFurnishedGameTests.stock(helper, job.plan);
		Container nearBox = helper.getBlockEntity(near);
		int stairs = count(nearBox, Items.SPRUCE_STAIRS);
		helper.assertTrue(stairs >= 1, "the setup: the plan's materials have no spruce stair: " + job.plan.materials());
		take(nearBox, Items.SPRUCE_STAIRS);
		((Container) helper.getBlockEntity(far)).setItem(13, new ItemStack(Items.SPRUCE_STAIRS, stairs));
		int[] skipped = {0};
		neverAStandIn(helper, job, skipped);
		helper.succeedWhen(() -> assertWhole(helper, job, skipped[0], "the stair in the far half"));
	}

	/**
	 * Every material lies in the far half of a double chest; once the build has started the (empty) near half by the bench
	 * is broken, splitting it. The remaining single chest still feeds the builder and the nook ends with the mod's chair.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaFurn1114cSplit"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaFurn1114cSplit")
	public void aDoubleChestSplitMidJobStillFeedsTheFurnishedBuild(GameTestHelper helper) {
		Job job = begin(helper);
		BlockPos near = QaFurnishedGameTests.CHEST;
		BlockPos far = near.west();
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		helper.setBlock(far, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(near, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		Container farBox = helper.getBlockEntity(far);
		((Container) helper.getBlockEntity(near)).clearContent();
		int slot = 0;
		for (Map.Entry<Item, Integer> e : job.plan.materials().entrySet()) {
			farBox.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		int[] skipped = {0};
		neverAStandIn(helper, job, skipped);
		boolean[] split = {false};
		helper.onEachTick(() -> {
			if (split[0]) {
				return;
			}
			BuildSite live = live(job);
			if (live == null) {
				throw new GameTestAssertException("the setup: the nook finished before the chest could be split");
			}
			if (live.placed() > 0) {
				helper.setBlock(near, Blocks.AIR);
				dropsGone(helper, near);
				split[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(split[0], "not yet split");
			helper.assertTrue(helper.getBlockState(far).getValue(ChestBlock.TYPE) == ChestType.SINGLE, "the far half didn't become a single chest");
			assertWhole(helper, job, skipped[0], "the double chest split mid-job");
		});
	}

	/**
	 * The builder's chest holds the materials and every other slot is full of a player's diamonds: the nook ends with the
	 * mod's chair and every diamond is still in the chest.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurn1114cFull"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurn1114cFull")
	public void aChestFullOfThePlayersThingsLosesNoneOfThem(GameTestHelper helper) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		int diamonds = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).isEmpty()) {
				chest.setItem(i, new ItemStack(Items.DIAMOND, 64));
				diamonds += 64;
			}
		}
		helper.assertTrue(diamonds > 0, "the setup: no slot left for the diamonds");
		int want = diamonds;
		int[] skipped = {0};
		neverAStandIn(helper, job, skipped);
		helper.succeedWhen(() -> {
			assertWhole(helper, job, skipped[0], "a chest full of diamonds");
			int now = count(chest, Items.DIAMOND);
			helper.assertTrue(now == want, "the chest had " + want + " diamonds, now " + now);
		});
	}

	/**
	 * The chair's stair is missing and the furnishing switch is turned off while the builder waits for it: the build
	 * going up keeps its choice, so it still waits for the mod's piece (no vanilla stand-in) and finishes with it once
	 * the stair is brought.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurn1114cOffWaiting"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurn1114cOffWaiting")
	public void theSwitchTurnedOffWhileWaitingForThePieceStillWaitsForIt(GameTestHelper helper) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		helper.assertTrue(count(chest, Items.SPRUCE_STAIRS) >= 1, "the setup: the plan's materials have no spruce stair: " + job.plan.materials());
		take(chest, Items.SPRUCE_STAIRS);
		int[] skipped = {0};
		neverAStandIn(helper, job, skipped);
		boolean[] brought = {false};
		helper.runAfterDelay(400, () -> Furnishings.ENABLED = false);
		helper.runAfterDelay(900, () -> {
			BuildSite live = live(job);
			if (live == null) {
				throw new GameTestAssertException("the nook finished without its chair's stair: the chair is " + job.level.getBlockState(job.chairAt));
			}
			helper.assertTrue(job.level.getBlockState(job.chairAt).isAir(), "waiting with the switch off, the chair spot holds " + job.level.getBlockState(job.chairAt));
			helper.assertTrue(live.missing().containsKey(Items.SPRUCE_STAIRS), "with the switch off the builder waits for " + live.missing() + ", not the chair's spruce stair");
			chest.setItem(0, new ItemStack(Items.SPRUCE_STAIRS, 1));
			brought[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brought[0], "not yet brought");
			assertWhole(helper, job, skipped[0], "the switch off while waiting, then the stair brought");
		});
	}

	/**
	 * The server restarts while the builder waits for the chair's missing stair: after the reload it still waits for that
	 * stair (the saved choice is kept), and finishes with the mod's chair once the stair is brought.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurn1114cReloadWaiting"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurn1114cReloadWaiting")
	public void aRestartWhileWaitingForThePieceKeepsWaitingForIt(GameTestHelper helper) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		take(chest, Items.SPRUCE_STAIRS);
		int[] skipped = {0};
		neverAStandIn(helper, job, skipped);
		boolean[] brought = {false};
		helper.runAfterDelay(500, () -> restart(job.level));
		helper.runAfterDelay(900, () -> {
			BuildSite live = live(job);
			if (live == null) {
				throw new GameTestAssertException("after the restart the nook finished without its chair's stair: the chair is "
					+ job.level.getBlockState(job.chairAt));
			}
			helper.assertFalse(live.furniture().isVanilla(), "the restart lost the nook's furniture choice: " + live.furniture());
			helper.assertTrue(live.missing().containsKey(Items.SPRUCE_STAIRS), "after the restart the builder waits for " + live.missing()
				+ ", not the chair's spruce stair");
			chest.setItem(0, new ItemStack(Items.SPRUCE_STAIRS, 1));
			brought[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brought[0], "not yet brought");
			assertWhole(helper, job, skipped[0], "a restart while waiting, then the stair brought");
		});
	}

	/**
	 * The finished furnished nook is captured as a moving plan (through a save and load of the plan, as the Mover keeps
	 * it) and rebuilt elsewhere turned a quarter: the mod's chair stands at the turned spot, still the upturned stair.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurn1114cMoved"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurn1114cMoved")
	public void aFinishedFurnishedNookMovedKeepsTheModsChair(GameTestHelper helper) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		int[] skipped = {0};
		neverAStandIn(helper, job, skipped);
		helper.succeedWhen(() -> {
			assertWhole(helper, job, skipped[0], "the nook before the move");
			BuildSiteManager.Finished finished = null;
			for (BuildSiteManager.Finished f : BuildSiteManager.get(job.level).finishedNear(job.level, job.placement.origin(), 4)) {
				if (f.placement().origin().equals(job.placement.origin())) {
					finished = f;
				}
			}
			helper.assertTrue(finished != null, "the finished nook isn't recorded");
			Optional<MovePlan> captured = MovePlan.capture(job.level, finished);
			helper.assertTrue(captured.isPresent(), "the finished nook couldn't be captured");
			Optional<MovePlan> loaded = MovePlan.load(captured.get().save());
			helper.assertTrue(loaded.isPresent(), "the captured plan didn't load back");
			MovePlan plan = loaded.get();
			BlockPos min = helper.absolutePos(new BlockPos(5, 2, 1));
			plan.place(job.level, min, Rotation.CLOCKWISE_90);
			BlockPos rel = job.chairAt.subtract(new BlockPos(MovePlan.box(job.level, finished).minX(), MovePlan.box(job.level, finished).minY(),
				MovePlan.box(job.level, finished).minZ()));
			BlockPos at = plan.at(rel, min, Rotation.CLOCKWISE_90);
			BlockState moved = job.level.getBlockState(at);
			helper.assertTrue(modChair(moved), "the moved nook's chair at " + at + " is " + moved + ", wanted the test mod's upturned stair");
			BlockState original = job.level.getBlockState(job.chairAt);
			helper.assertTrue(moved.getValue(StairBlock.FACING) == Rotation.CLOCKWISE_90.rotate(original.getValue(StairBlock.FACING)),
				"the moved chair faces " + moved.getValue(StairBlock.FACING) + ", the original faced " + original.getValue(StairBlock.FACING)
					+ " (turned a quarter)");
		});
	}
}
