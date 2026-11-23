package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane C (2026-11-23), B1496 from its spec (villager-jobs.md, "Fishermen per village"): with the fisherman cap on,
 * a smoker (or furnace) within a free barrel's reach is the Fisherman's, not a Butcher's, for a jobless villager nearer
 * that barrel than the smoker while the village has room for a fisherman and the barrel isn't another workshop's stock:
 * he becomes the Fisherman every time. A villager nearer the smoker still takes the smoker. The fix's words: "a free
 * barrel he can take", so a barrel he can't take, a full village, a claimed or broken barrel never leave him jobless.
 * <p>
 * Every test: its own batch, {@link QaJobBarrels1122cGameTests#setup} (Leftovers.clear, random jobs and the worker cap
 * off, the cap on with 1 fisherman per village, the day held at morning; all restored after).
 */
public class QaFisherSmoker1123cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos BARREL = new BlockPos(10, 2, 10);
	private static final BlockPos BESIDE = new BlockPos(10, 2, 11);

	// ---- helpers -------------------------------------------------------------------------------------------------------

	static Villager jobless(GameTestHelper helper, BlockPos rel) {
		return QaJobBarrels1122cGameTests.jobless(helper, rel);
	}

	private static BlockPos site(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
	}

	private static String where(GameTestHelper helper, Villager v) {
		BlockPos s = site(v);
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		return "job " + v.getVillagerData().getProfession() + ", site " + (s == null ? "none" : s.subtract(o).toShortString() + " "
			+ helper.getLevel().getBlockState(s).getBlock().getName().getString()) + ", at " + v.blockPosition().subtract(o).toShortString();
	}

	private static boolean holds(Villager v, BlockPos abs, VillagerProfession prof) {
		return v.getVillagerData().getProfession() == prof && abs.equals(site(v));
	}

	/** What a jobless villager's own job search (vanilla's AcquirePoi query, with him thinking) returns, absolute. */
	static List<BlockPos> search(GameTestHelper helper, Villager v) {
		WorkerLimits.thinking(v);
		try {
			return helper.getLevel().getPoiManager().findAllClosestFirstWithType(t -> t.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
				p -> true, v.blockPosition(), 48, PoiManager.Occupancy.HAS_SPACE)
				.map(com.mojang.datafixers.util.Pair::getSecond).map(BlockPos::immutable).toList();
		} finally {
			WorkerLimits.thinking(null);
		}
	}

	static String rel(GameTestHelper helper, List<BlockPos> seen) {
		return seen.stream().map(q -> q.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).toList().toString();
	}

	/** Never {@code never}; succeeds once {@code v} holds the block at absolute {@code job} as {@code prof}. */
	static void becomes(GameTestHelper helper, Villager v, BlockPos job, VillagerProfession prof, VillagerProfession never, String what) {
		helper.onEachTick(() -> helper.assertTrue(v.getVillagerData().getProfession() != never,
			what + ": became a " + never + " instead: " + where(helper, v)));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), what + ": the villager died");
			helper.assertTrue(holds(v, job, prof), what + ": no " + prof + " there yet: " + where(helper, v));
			v.discard();
		});
	}

	/** As a save and a load of the world do to a villager. */
	private static Villager reload(GameTestHelper helper, Villager old) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		if (!old.save(tag)) {
			throw new GameTestAssertException("(setup) the villager wasn't saved");
		}
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		if (!(back instanceof Villager v)) {
			throw new GameTestAssertException("the villager didn't load back as a villager: " + back);
		}
		level.addFreshEntity(v);
		return v;
	}

	// ---- 1. the rule's two sides ---------------------------------------------------------------------------------------

	/** A lone barrel, a free smoker 4 blocks east of it, the jobless villager beside the barrel: Fisherman, never Butcher. */
	//$ gametest_ticks_batch HUGE '1200' '"qaFisherSmoker1123cBeside"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaFisherSmoker1123cBeside")
	public void qaBesideTheBarrelWithASmokerFourAwayBecomesTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(4, 0, 0), Blocks.SMOKER);
		Villager v = jobless(helper, BESIDE);
		becomes(helper, v, helper.absolutePos(BARREL), VillagerProfession.FISHERMAN, VillagerProfession.BUTCHER, "beside the barrel, a smoker 4 across");
	}

	private static void stand(GameTestHelper helper, Villager v, BlockPos rel) {
		BlockPos abs = helper.absolutePos(rel);
		v.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
	}

	/**
	 * B1556: the villager who started beside the free barrel (his first ticks there, then AI off) and now stands beside the
	 * smoker, as after a stroll during vanilla's retry wait: his search still sees the barrel and not the smoker. Once the
	 * barrel is gone he sees the smoker.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaFisherSmoker1123cStrolled"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaFisherSmoker1123cStrolled")
	public void qaStartedBesideTheBarrelAndStrolledToTheSmokerHeStillDoesntSeeTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos smoker = BARREL.offset(4, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager v = jobless(helper, BESIDE);
		helper.runAfterDelay(2, () -> {
			v.setNoAi(true); // after his first tick: a villager with AI off from the start has none
			stand(helper, v, smoker.south());
			List<BlockPos> a = search(helper, v);
			helper.assertTrue(a.contains(helper.absolutePos(BARREL)), "strolled to the smoker: his search doesn't see the free barrel: " + rel(helper, a));
			helper.assertFalse(a.contains(helper.absolutePos(smoker)), "strolled to the smoker: his search sees the smoker again: " + rel(helper, a));
			helper.setBlock(BARREL, Blocks.AIR);
			List<BlockPos> b = search(helper, v);
			helper.assertTrue(b.contains(helper.absolutePos(smoker)), "the barrel gone: his search still doesn't see the smoker: " + rel(helper, b));
			v.discard();
			helper.succeed();
		});
	}

	/**
	 * B1556: one who started beside the smoker, searched beside the free barrel (so it is his) and went back to the smoker
	 * doesn't see the smoker; with the village's fisherman place taken meanwhile he sees it again.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaFisherSmoker1123cCameBy"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaFisherSmoker1123cCameBy")
	public void qaSearchedBesideTheBarrelThenBackAtTheSmokerHeDoesntSeeItUntilTheVillageHasItsFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos smoker = BARREL.offset(4, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager v = jobless(helper, smoker.south());
		v.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			List<BlockPos> first = search(helper, v);
			helper.assertTrue(first.contains(helper.absolutePos(smoker)), "beside the smoker from the start: his search doesn't see it: " + rel(helper, first));
			stand(helper, v, BESIDE);
			search(helper, v);
			stand(helper, v, smoker.south());
			List<BlockPos> a = search(helper, v);
			helper.assertFalse(a.contains(helper.absolutePos(smoker)), "back at the smoker after a search beside the barrel: he sees the smoker: " + rel(helper, a));
			Villager fisher = helper.spawn(EntityType.VILLAGER, BARREL.offset(0, 0, 3));
			fisher.setNoAi(true);
			fisher.setVillagerData(fisher.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
			List<BlockPos> b = search(helper, v);
			helper.assertTrue(b.contains(helper.absolutePos(smoker)), "the village has its fisherman: his search still doesn't see the smoker: " + rel(helper, b));
			helper.assertFalse(b.contains(helper.absolutePos(BARREL)), "the village has its fisherman: his search sees the barrel: " + rel(helper, b));
			v.discard();
			fisher.discard();
			helper.succeed();
		});
	}

	/** The same at the edge of reach: the smoker 8 blocks from the barrel (the Fisher's Hut II's own spacing). */
	//$ gametest_ticks_batch HUGE '1200' '"qaFisherSmoker1123cEdge"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaFisherSmoker1123cEdge")
	public void qaBesideTheBarrelWithASmokerEightAwayBecomesTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(8, 0, 0), Blocks.SMOKER);
		Villager v = jobless(helper, BESIDE);
		becomes(helper, v, helper.absolutePos(BARREL), VillagerProfession.FISHERMAN, VillagerProfession.BUTCHER, "beside the barrel, a smoker 8 across");
	}

	/**
	 * The rule itself, without vanilla's random retries: the villager beside the free barrel (AI off) doesn't see the smoker
	 * 4 east of it in his job search, and does see the barrel; a second villager beside the smoker sees the smoker.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaFisherSmoker1123cSearch"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaFisherSmoker1123cSearch")
	public void qaBesideTheBarrelHisSearchDoesntSeeTheSmokerButBesideTheSmokerItDoes(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos smoker = BARREL.offset(4, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager byBarrel = jobless(helper, BESIDE);
		Villager byOven = jobless(helper, smoker.south());
		byBarrel.setNoAi(true);
		byOven.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			List<BlockPos> a = search(helper, byBarrel);
			helper.assertTrue(a.contains(helper.absolutePos(BARREL)), "beside the barrel: his search doesn't see the free barrel: " + rel(helper, a));
			helper.assertFalse(a.contains(helper.absolutePos(smoker)), "beside the barrel: his search still sees the smoker 4 across: " + rel(helper, a));
			List<BlockPos> b = search(helper, byOven);
			helper.assertTrue(b.contains(helper.absolutePos(smoker)), "beside the smoker: his search doesn't see it: " + rel(helper, b));
			byBarrel.discard();
			byOven.discard();
			helper.succeed();
		});
	}

	/** The boundary: a villager exactly as far from the barrel as from the smoker isn't nearer the barrel; he sees the smoker. */
	//$ gametest_ticks_batch HUGE '200' '"qaFisherSmoker1123cTie"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaFisherSmoker1123cTie")
	public void qaHalfwayBetweenBarrelAndSmokerHisSearchSeesTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos smoker = BARREL.offset(4, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager v = jobless(helper, BARREL.offset(2, 0, 1));
		v.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(v.blockPosition().distSqr(helper.absolutePos(BARREL)) == v.blockPosition().distSqr(helper.absolutePos(smoker)),
				"(setup) the villager isn't halfway: at " + v.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
			List<BlockPos> seen = search(helper, v);
			helper.assertTrue(seen.contains(helper.absolutePos(smoker)), "halfway between them, his search doesn't see the smoker: " + rel(helper, seen));
			v.discard();
			helper.succeed();
		});
	}

	/** A furnace (no job block of its own) 3 blocks from the barrel never stops the Fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaFisherSmoker1123cFurnace"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaFisherSmoker1123cFurnace")
	public void qaAFurnaceBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(3, 0, 0), Blocks.FURNACE);
		Villager v = jobless(helper, BESIDE);
		QaJobBarrels1122cGameTests.becomesFishermanAt(helper, v, helper.absolutePos(BARREL), "a furnace 3 across");
	}

	/**
	 * A blast furnace (the Armorer's, not "a smoker or furnace") 4 blocks from the barrel: the wiki's B1463 rule, the barrel
	 * is a free workstation's stock, so the villager beside the barrel becomes the Armorer, never the Fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaFisherSmoker1123cBlast"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaFisherSmoker1123cBlast")
	public void qaABlastFurnaceInTheBarrelsReachMakesAnArmorer(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos blast = BARREL.offset(4, 0, 0);
		helper.setBlock(blast, Blocks.BLAST_FURNACE);
		Villager v = jobless(helper, BESIDE);
		becomes(helper, v, helper.absolutePos(blast), VillagerProfession.ARMORER, VillagerProfession.FISHERMAN, "beside the barrel, a blast furnace 4 across");
	}

	// ---- 2. when the barrel isn't one he can take -----------------------------------------------------------------------

	/**
	 * The village already has its one fisherman (cap 1, 2 villagers): no room, so the smoker isn't the Fisherman's; the
	 * jobless villager beside the barrel takes the smoker rather than staying jobless.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaFisherSmoker1123cFull"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaFisherSmoker1123cFull")
	public void qaWithNoRoomForAFishermanTheVillagerBesideTheBarrelTakesTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos smoker = BARREL.offset(4, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager fisher = helper.spawn(EntityType.VILLAGER, new BlockPos(22, 2, 22));
		fisher.setVillagerData(fisher.getVillagerData().setProfession(VillagerProfession.FISHERMAN).setLevel(2));
		fisher.setVillagerXp(20);
		fisher.setNoAi(true);
		Villager v = jobless(helper, BESIDE);
		helper.onEachTick(() -> helper.assertTrue(fisher.isAlive() && fisher.getVillagerData().getProfession() == VillagerProfession.FISHERMAN,
			"(setup) the village's fisherman is gone: " + where(helper, fisher)));
		becomes(helper, v, helper.absolutePos(smoker), VillagerProfession.BUTCHER, VillagerProfession.FISHERMAN, "no room for a fisherman, beside the barrel");
	}

	/**
	 * The barrel is another workshop's stock (a free, reachable Shop Counter 3 blocks west of it): the smoker 4 east isn't
	 * hidden from the villager beside the barrel (his search sees it), and the barrel is.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaFisherSmoker1123cStock"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaFisherSmoker1123cStock")
	public void qaABarrelThatIsAShopsStockDoesntHideTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos counter = BARREL.offset(-3, 0, 0);
		helper.setBlock(counter, io.github.jcondedata.aliveworkplace.registry.ModBlocks.SHOP_COUNTER);
		BlockPos smoker = BARREL.offset(4, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager v = jobless(helper, BESIDE);
		v.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			List<BlockPos> seen = search(helper, v);
			helper.assertTrue(seen.contains(helper.absolutePos(counter)), "(setup) the search doesn't see the Shop Counter: " + rel(helper, seen));
			helper.assertFalse(seen.contains(helper.absolutePos(BARREL)), "(setup) the shop's stock barrel is still seen: " + rel(helper, seen));
			helper.assertTrue(seen.contains(helper.absolutePos(smoker)), "a shop's stock barrel hides the smoker from the villager beside it: " + rel(helper, seen));
			v.discard();
			helper.succeed();
		});
	}

	// ---- 3. the barrel lost while he waits ------------------------------------------------------------------------------

	/** Beside the barrel (AI off: waiting for it), the smoker 4 east; then the barrel is claimed or broken, and his AI on. */
	private static void barrelLost(GameTestHelper helper, boolean broken) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos smoker = BARREL.offset(4, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager v = jobless(helper, BESIDE);
		v.setNoAi(true);
		BlockPos barrel = helper.absolutePos(BARREL);
		String what = broken ? "the barrel broken while he waited" : "the barrel claimed while he waited";
		helper.runAfterDelay(2, () -> {
			List<BlockPos> before = search(helper, v);
			helper.assertTrue(before.contains(barrel), "(setup) the search doesn't see the free barrel: " + rel(helper, before));
			if (broken) {
				helper.destroyBlock(BARREL);
			} else {
				helper.assertTrue(helper.getLevel().getPoiManager().take(t -> t.is(PoiTypes.FISHERMAN), (t, p) -> p.equals(barrel), barrel, 1).isPresent(),
					"(setup) couldn't claim the barrel");
			}
			v.setNoAi(false);
		});
		// registered up front (a tick task added from inside one breaks the test framework's tick loop); his AI is off till then
		becomes(helper, v, helper.absolutePos(smoker), VillagerProfession.BUTCHER, VillagerProfession.FISHERMAN, what);
	}

	//$ gametest_ticks_batch HUGE '1200' '"qaFisherSmoker1123cClaimed"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaFisherSmoker1123cClaimed")
	public void qaTheBarrelClaimedWhileHeWaitsLetsHimTakeTheSmoker(GameTestHelper helper) {
		barrelLost(helper, false);
	}

	//$ gametest_ticks_batch HUGE '1200' '"qaFisherSmoker1123cBroken"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaFisherSmoker1123cBroken")
	public void qaTheBarrelBrokenWhileHeWaitsLetsHimTakeTheSmoker(GameTestHelper helper) {
		barrelLost(helper, true);
	}

	// ---- 4. save and reload --------------------------------------------------------------------------------------------

	/**
	 * Beside the barrel, a smoker 4 east; saved and loaded back on his first tick (still jobless, waiting), and again
	 * 20 ticks later: he still becomes the Fisherman, never the Butcher.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaFisherSmoker1123cReload"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaFisherSmoker1123cReload")
	public void qaSavedAndLoadedWhileWaitingHeStillBecomesTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(4, 0, 0), Blocks.SMOKER);
		BlockPos barrel = helper.absolutePos(BARREL);
		Villager[] who = {jobless(helper, BESIDE)};
		int[] ticks = {0};
		int[] reloads = {0};
		helper.onEachTick(() -> {
			Villager v = who[0];
			helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.BUTCHER, "after a reload he became the Butcher: " + where(helper, v));
			ticks[0]++;
			if ((ticks[0] == 1 || ticks[0] == 20) && v.getVillagerData().getProfession() == VillagerProfession.NONE) {
				who[0] = reload(helper, v);
				reloads[0]++;
			}
		});
		helper.succeedWhen(() -> {
			Villager v = who[0];
			helper.assertTrue(reloads[0] >= 1, "(setup) never reloaded while jobless: " + where(helper, v));
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(holds(v, barrel, VillagerProfession.FISHERMAN), "after " + reloads[0] + " reload(s): no Fisherman at the barrel yet: " + where(helper, v));
			v.discard();
		});
	}
}
