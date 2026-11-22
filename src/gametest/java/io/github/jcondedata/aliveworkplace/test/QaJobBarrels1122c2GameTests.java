package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.fish.FishermanCap;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane C (2026-11-22, second pass), B1463/B1476/B1477 from their spec, the gaps {@link QaJobBarrels1122cGameTests}
 * and {@link QaJobBarrels1122cBugTests} leave: with the fisherman cap on (26.13), a jobless villager's job search skips a
 * barrel within supply reach (8 across, 4 up or down) of a free workstation of another job that a jobless villager could
 * really take (by himself, with a standable cell on one of its 4 sides or on top); smokers and furnaces never hide a
 * barrel; workstations taken only by an item (jukebox, bee nest, mailbox) or switched off by config never do.
 * <p>
 * Shared setup: {@link QaJobBarrels1122cGameTests#setup} (own batch, {@code Leftovers.clear}, the cap restored after).
 */
public class QaJobBarrels1122c2GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BARREL = QaJobBarrels1122cGameTests.BARREL;
	private static final BlockPos BESIDE = QaJobBarrels1122cGameTests.BESIDE;

	private static BlockPos site(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(null);
	}

	private static String where(GameTestHelper helper, Villager v) {
		BlockPos s = site(v);
		return "job " + v.getVillagerData().getProfession() + ", site " + (s == null ? "none" : s.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " "
			+ helper.getLevel().getBlockState(s).getBlock().getName().getString());
	}

	/** What a jobless villager's own job search (vanilla's AcquirePoi query, with him thinking) returns, absolute. */
	private static List<BlockPos> search(GameTestHelper helper, Villager v) {
		WorkerLimits.thinking(v);
		try {
			return helper.getLevel().getPoiManager().findAllClosestFirstWithType(t -> t.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
				p -> true, v.blockPosition(), 48, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
				.map(com.mojang.datafixers.util.Pair::getSecond).map(BlockPos::immutable).toList();
		} finally {
			WorkerLimits.thinking(null);
		}
	}

	private static String rel(GameTestHelper helper, List<BlockPos> seen) {
		return seen.stream().map(q -> q.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).toList().toString();
	}

	/** The jobless villager's search, his AI off: it doesn't see the barrel at absolute {@code barrel}. */
	private static void searchHidesTheBarrel(GameTestHelper helper, Villager v, BlockPos barrel, BlockPos job, String what) {
		v.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			List<BlockPos> seen = search(helper, v);
			helper.assertTrue(seen.contains(job), "(setup) " + what + ": the search doesn't see the workstation either: " + rel(helper, seen));
			helper.assertFalse(seen.contains(barrel), what + ": a jobless villager's search still sees the barrel: " + rel(helper, seen));
			v.discard();
			helper.succeed();
		});
	}

	/** Never a Fisherman; succeeds when {@code v} holds the workstation at absolute {@code job} with profession {@code prof}. */
	private static void takes(GameTestHelper helper, Villager v, BlockPos job, VillagerProfession prof, String what) {
		helper.onEachTick(() -> helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.FISHERMAN,
			what + ": took the barrel: " + where(helper, v)));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(job.equals(site(v)) && v.getVillagerData().getProfession() == prof,
				what + ": hasn't taken the workstation yet: " + where(helper, v));
			v.discard();
		});
	}

	/** A lone barrel, {@code block} 2 blocks east of it, the villager beside the barrel: the village still gets its fisherman. */
	private static void itemOnlyBeside(GameTestHelper helper, Block block, String what) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(2, 0, 0), block);
		Villager v = QaJobBarrels1122cGameTests.jobless(helper, BESIDE);
		QaJobBarrels1122cGameTests.becomesFishermanAt(helper, v, helper.absolutePos(BARREL), what);
	}

	// ---- 1. a shipped build with a smoker in its barrel's reach --------------------------------------------------------

	/**
	 * ranch.md: Ranch II keeps I's smoker (6, 1, 10) (the Rancher's job block, also the Butcher's) and adds the barrel
	 * (14, 1, 10), "storage and the Fisherman's job site", 8 across from the smoker. A smoker never hides a barrel: a
	 * jobless villager beside the barrel, the village with no fisherman, becomes its Fisherman (B1476's kin, a second
	 * shipped build). Placed at (4, 2, 4) so all 21 x 21 of it lies inside the 30-block area.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122c2Ranch"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122c2Ranch")
	public void qaAJoblessVillagerBesideTheRanchIIBarrelBecomesItsFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(StarterBlueprints.RANCH_2.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE),
			net.minecraft.util.RandomSource.create(173L), 2);
		BlockPos barrel = origin.offset(14, 1, 10);
		BlockPos smoker = origin.offset(6, 1, 10);
		helper.assertTrue(level.getBlockState(barrel).is(Blocks.BARREL) && level.getBlockState(smoker).is(Blocks.SMOKER), "(setup) ranch_2 has changed");
		BlockPos at = null;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos c = barrel.relative(d);
			if (io.github.jcondedata.aliveworkplace.work.Walker.canStand(level, c)) {
				at = c;
				break;
			}
		}
		helper.assertTrue(at != null, "(setup) no open cell beside the ranch's barrel");
		Villager v = EntityType.VILLAGER.create(level);
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		level.addFreshEntity(v);
		QaJobBarrels1122cGameTests.becomesFishermanAt(helper, v, barrel, "ranch_2 (its smoker 8 across)");
	}

	// ---- 2. a vanilla workstation hides the barrel; item-only ones don't ------------------------------------------------

	/**
	 * A free fletching table (a vanilla job a jobless villager takes by himself) 3 blocks from the barrel: the barrel is its
	 * stock, so the villager beside the barrel becomes the Fletcher, never a Fisherman (the Shop Counter tests' rule for a
	 * vanilla workstation).
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122c2Fletch"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122c2Fletch")
	public void qaAFreeFletchingTableBesideTheBarrelMakesAFletcherNotAFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos table = BARREL.offset(3, 0, 0);
		helper.setBlock(table, Blocks.FLETCHING_TABLE);
		Villager v = QaJobBarrels1122cGameTests.jobless(helper, BESIDE);
		takes(helper, v, helper.absolutePos(table), VillagerProfession.FLETCHER, "a free fletching table 3 across");
	}

	/** A jukebox (a job taken only by an item, B1477) 2 blocks from the barrel: the village still gets its fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122c2Jukebox"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122c2Jukebox")
	public void qaAJukeboxBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		itemOnlyBeside(helper, Blocks.JUKEBOX, "a jukebox 2 blocks from the barrel");
	}

	/** A bee nest (taken only by an item, B1477) 2 blocks from the barrel: the village still gets its fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122c2Nest"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122c2Nest")
	public void qaABeeNestBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		itemOnlyBeside(helper, Blocks.BEE_NEST, "a bee nest 2 blocks from the barrel");
	}

	/** A mailbox (taken only by an item, B1477) 2 blocks from the barrel: the village still gets its fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122c2Mailbox"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122c2Mailbox")
	public void qaAMailboxBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		itemOnlyBeside(helper, ModBlocks.MAILBOX, "a mailbox 2 blocks from the barrel");
	}

	/**
	 * Config movers off (45.3): a jobless villager doesn't take a Mover's Crate, so a free crate 2 blocks from the barrel
	 * is no workstation he could take instead: the village still gets its fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122c2MoversOff"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122c2MoversOff")
	public void qaAMoversCrateWithMoversOffDoesntStopTheFisherman(GameTestHelper helper) {
		boolean movers = MoverWork.ENABLED;
		MoverWork.ENABLED = false;
		Leftovers.after(helper, () -> MoverWork.ENABLED = movers);
		itemOnlyBeside(helper, ModBlocks.MOVERS_CRATE, "a Mover's Crate (movers off) 2 blocks from the barrel");
	}

	// ---- 3. reachable: a side or the top ------------------------------------------------------------------------------

	/**
	 * A Shop Counter 3 blocks from the barrel, glass on all 4 sides but its top open (a villager can stand on it): it can
	 * still be taken, so the barrel stays its stock and the jobless search doesn't see it.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaJobBarrels1122c2TopOnly"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobBarrels1122c2TopOnly")
	public void qaACounterReachedOnlyFromOnTopStillHidesTheBarrel(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos counter = BARREL.offset(3, 0, 0);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.setBlock(counter.relative(d), Blocks.GLASS);
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.work.Walker.canStand(helper.getLevel(), helper.absolutePos(counter.above())),
			"(setup) the counter's top isn't standable");
		Villager v = QaJobBarrels1122cGameTests.jobless(helper, BESIDE);
		searchHidesTheBarrel(helper, v, helper.absolutePos(BARREL), helper.absolutePos(counter), "a counter open only on top");
	}

	/**
	 * A Shop Counter 3 blocks from the barrel, glass on 3 sides and on top; the 4th side's cell is open but has glass
	 * right above it (one block of headroom: no villager stands there). No standable cell beside or on top, so it is
	 * free forever: the barrel isn't hidden, and the village still gets its fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122c2NoHead"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122c2NoHead")
	public void qaACounterWhoseOnlyOpenSideHasNoHeadroomDoesntStopTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos counter = BARREL.offset(3, 0, 0);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		helper.setBlock(counter.north(), Blocks.GLASS);
		helper.setBlock(counter.south(), Blocks.GLASS);
		helper.setBlock(counter.west(), Blocks.GLASS);
		helper.setBlock(counter.above(), Blocks.GLASS);
		helper.setBlock(counter.east().above(), Blocks.GLASS); // the open east cell: no headroom
		Villager v = QaJobBarrels1122cGameTests.jobless(helper, BESIDE);
		QaJobBarrels1122cGameTests.becomesFishermanAt(helper, v, helper.absolutePos(BARREL), "a counter whose one open side is 1 block high");
	}

	// ---- 4. the cap switched off mid-game -----------------------------------------------------------------------------

	/**
	 * The cap on: a barrel 2 blocks from a free Shop Counter is hidden from the search; the player switches the cap off
	 * (vanilla) and the very next search sees it again; switched back on, it is hidden again.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaJobBarrels1122c2Toggle"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobBarrels1122c2Toggle")
	public void qaSwitchingTheCapOffMidGameShowsTheBarrelAgain(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(2, 0, 0), ModBlocks.SHOP_COUNTER);
		Villager v = QaJobBarrels1122cGameTests.jobless(helper, BESIDE);
		v.setNoAi(true);
		BlockPos barrel = helper.absolutePos(BARREL);
		helper.runAfterDelay(2, () -> {
			List<BlockPos> on = search(helper, v);
			helper.assertFalse(on.contains(barrel), "cap on: the search sees the barrel by a free counter: " + rel(helper, on));
			FishermanCap.ENABLED = false;
			List<BlockPos> off = search(helper, v);
			helper.assertTrue(off.contains(barrel), "cap switched off: the search still hides the barrel: " + rel(helper, off));
			FishermanCap.ENABLED = true;
			List<BlockPos> back = search(helper, v);
			helper.assertFalse(back.contains(barrel), "cap switched back on: the search sees the barrel: " + rel(helper, back));
			v.discard();
			helper.succeed();
		});
	}
}
