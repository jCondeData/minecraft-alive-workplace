package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.fish.FishermanCap;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
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
 * QA lane E (2026-11-23), B1496's fix (d47d10901, WorkerLimits.hideFull) from its spec (villager-jobs.md, CHANGELOG):
 * "That smoker (or a furnace) is the Fisherman's, not a Butcher's, for a jobless villager nearer the free barrel than the
 * smoker while the village has room for a fisherman ... With the cap switched off, barrels hire fishermen as in
 * vanilla." So the smoker is only held back while the barrel is free, takeable and the village has room: with no room,
 * with the barrel taken, with the barrel another workshop's stock, or with the cap off, a jobless villager beside the
 * barrel still finds the smoker and becomes its Butcher (never left jobless).
 * <p>
 * Every test: its own batch, {@code Leftovers.clear} first ({@link QaJobBarrels1122cGameTests#setup}: random jobs and the
 * worker cap off, one fisherman per village plus one per 10 villagers, the day held at morning).
 */
public class QaBarrelSmoker1123eGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos BARREL = new BlockPos(10, 2, 10);
	static final BlockPos BESIDE = new BlockPos(10, 2, 11);
	/** The smoker, 4 east of the barrel: in its supply reach, 3 farther from the villager beside the barrel. */
	static final BlockPos SMOKER = new BlockPos(14, 2, 10);
	/** Another barrel far off (out of the smoker's reach), held by the village's one Fisherman. */
	static final BlockPos FAR_BARREL = new BlockPos(2, 2, 25);

	// ---- helpers -------------------------------------------------------------------------------------------------------

	static BlockPos site(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
	}

	static String where(GameTestHelper helper, Villager v) {
		BlockPos s = site(v);
		return "job " + v.getVillagerData().getProfession() + ", site " + (s == null ? "none" : s.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()
			+ " " + helper.getLevel().getBlockState(s).getBlock().getName().getString()) + ", at "
			+ v.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	static Villager joblessAbs(GameTestHelper helper, BlockPos abs) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.getLevel().addFreshEntity(v);
		return v;
	}

	/** As a save and a load of the world do to a villager (QaJobBarrelsSaved1122eGameTests#reload). */
	static Villager reload(GameTestHelper helper, Villager old) {
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

	static boolean holds(Villager v, VillagerProfession job, BlockPos abs) {
		return v.getVillagerData().getProfession() == job && abs.equals(site(v));
	}

	/** The village's one Fisherman, at a barrel of his own far off, standing still: the village (cap 1) is full. */
	static Villager theVillagesFisherman(GameTestHelper helper) {
		helper.setBlock(FAR_BARREL, Blocks.BARREL);
		Villager fisher = helper.spawn(EntityType.VILLAGER, FAR_BARREL.east());
		Jobs.employ(helper.getLevel(), fisher, helper.absolutePos(FAR_BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		fisher.setNoAi(true);
		return fisher;
	}

	/** A lone barrel with a free smoker 4 east of it (the player's own, no building). */
	static void barrelAndSmoker(GameTestHelper helper) {
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(SMOKER, Blocks.SMOKER);
		helper.assertTrue(helper.getBlockState(SMOKER).is(Blocks.SMOKER), "(setup) the smoker wasn't placed");
	}

	/** Never a Fisherman; succeeds once {@code v()} is the Butcher at the smoker. */
	static void becomesButcherAtTheSmoker(GameTestHelper helper, java.util.function.Supplier<Villager> v, String what, Runnable cleanup) {
		becomesButcherAtTheSmoker(helper, v, what, cleanup, () -> "(setup) no villager yet");
	}

	/** As above; {@code pending} says why there is no villager to check yet. */
	static void becomesButcherAtTheSmoker(GameTestHelper helper, java.util.function.Supplier<Villager> v, String what, Runnable cleanup,
		java.util.function.Supplier<String> pending) {
		BlockPos smoker = helper.absolutePos(SMOKER);
		helper.onEachTick(() -> {
			Villager w = v.get();
			if (w != null) {
				helper.assertTrue(w.getVillagerData().getProfession() != VillagerProfession.FISHERMAN, what + ": became a Fisherman: " + where(helper, w));
			}
		});
		helper.succeedWhen(() -> {
			Villager w = v.get();
			helper.assertTrue(w != null, what + ": " + pending.get());
			helper.assertTrue(w.isAlive(), what + ": the villager died");
			helper.assertTrue(holds(w, VillagerProfession.BUTCHER, smoker), what + ": not the Butcher at the free smoker yet: " + where(helper, w)
				+ "; village full of fishermen: " + FishermanCap.full(helper.getLevel(), helper.absolutePos(BARREL)));
			w.discard();
			cleanup.run();
		});
	}

	/** The jobless villager's own job search (vanilla's, as AcquirePoi calls it, with him thinking) returns {@code abs}. */
	static void searchSees(GameTestHelper helper, Villager v, BlockPos abs, String what) {
		v.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			List<BlockPos> seen;
			WorkerLimits.thinking(v);
			try {
				seen = helper.getLevel().getPoiManager().findAllClosestFirstWithType(t -> t.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
					p -> true, v.blockPosition(), 48, PoiManager.Occupancy.HAS_SPACE)
					.map(com.mojang.datafixers.util.Pair::getSecond).map(BlockPos::immutable).toList();
			} finally {
				WorkerLimits.thinking(null);
			}
			helper.assertTrue(seen.contains(abs), what + ": a jobless villager's search doesn't see the smoker at "
				+ abs.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + "; it sees "
				+ seen.stream().map(q -> q.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " " + helper.getLevel().getBlockState(q).getBlock().getName().getString()).toList());
			v.discard();
			helper.succeed();
		});
	}

	/** An open cell beside {@code block} (absolute) a villager can stand in. */
	static BlockPos besideOnFoot(GameTestHelper helper, BlockPos block) {
		ServerLevel level = helper.getLevel();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos c = block.relative(d);
			if (level.getBlockState(c).getCollisionShape(level, c).isEmpty() && level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty()
				&& !level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty()) {
				return c;
			}
		}
		throw new GameTestAssertException("(setup) no open cell beside " + block.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
	}

	// ---- 1. no room for a fisherman -------------------------------------------------------------------------------------

	/**
	 * The village already has its one Fisherman (2 villagers: cap 1). A jobless villager beside a free barrel, a free
	 * smoker 4 east of it: no room, so the smoker isn't the Fisherman's; he becomes its Butcher (never jobless forever).
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaBarrelSmoker1123eFull"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaBarrelSmoker1123eFull")
	public void qaWithNoRoomForAFishermanTheVillagerByTheBarrelTakesTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		Villager fisher = theVillagesFisherman(helper);
		barrelAndSmoker(helper);
		Villager v = joblessAbs(helper, helper.absolutePos(BESIDE));
		becomesButcherAtTheSmoker(helper, () -> v, "village full of fishermen", fisher::discard);
	}

	/** Case 1 seen by the search itself: with no room, the jobless villager's job search sees the smoker beside the barrel. */
	//$ gametest_ticks_batch HUGE '200' '"qaBarrelSmoker1123eFullSearch"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaBarrelSmoker1123eFullSearch")
	public void qaWithNoRoomForAFishermanTheSearchSeesTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		Villager fisher = theVillagesFisherman(helper);
		barrelAndSmoker(helper);
		Villager v = joblessAbs(helper, helper.absolutePos(BESIDE));
		helper.runAfterDelay(3, fisher::discard);
		searchSees(helper, v, helper.absolutePos(SMOKER), "village full of fishermen");
	}

	// ---- 2. the barrel already taken ------------------------------------------------------------------------------------

	/**
	 * Two fishermen allowed (room for another), the barrel beside the smoker already held by a Fisherman. A second jobless
	 * villager beside that barrel: it isn't free, so he takes the free smoker as its Butcher.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaBarrelSmoker1123eTaken"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaBarrelSmoker1123eTaken")
	public void qaWithTheBarrelTakenASecondVillagerBesideItTakesTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		FishermanCap.BASE = 2; // restored by setup's Leftovers.after
		barrelAndSmoker(helper);
		Villager first = helper.spawn(EntityType.VILLAGER, BARREL.west());
		Jobs.employ(helper.getLevel(), first, helper.absolutePos(BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		first.setNoAi(true);
		Villager v = joblessAbs(helper, helper.absolutePos(BESIDE));
		helper.onEachTick(() -> helper.assertTrue(holds(first, VillagerProfession.FISHERMAN, helper.absolutePos(BARREL)),
			"(setup) the first Fisherman lost his barrel: " + where(helper, first)));
		becomesButcherAtTheSmoker(helper, () -> v, "the barrel already a Fisherman's", first::discard);
	}

	// ---- 3. the barrel another workshop's stock (the shipped Inn II) ------------------------------------------------------

	/** Inn II's Shop Counter (8, 1, 6), its stock barrel (10, 1, 7) 2 across, its smoker (14, 1, 9) 4 across from that barrel. */
	private static BlockPos[] inn2(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, StarterBlueprints.INN_2);
		BlockPos counter = origin.offset(8, 1, 6);
		BlockPos barrel = origin.offset(10, 1, 7);
		BlockPos smoker = origin.offset(14, 1, 9);
		helper.assertTrue(level.getBlockState(counter).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.SHOP_COUNTER)
			&& level.getBlockState(barrel).is(Blocks.BARREL) && level.getBlockState(smoker).is(Blocks.SMOKER), "(setup) inn_2 has changed");
		return new BlockPos[] {counter, barrel, smoker};
	}

	/**
	 * The shipped Inn II: a jobless villager beside a stock barrel of its free Shop Counter (B1463: the counter's stock,
	 * no workstation), its smoker in that barrel's reach. The stock barrel isn't a free barrel he can take, so it doesn't
	 * hold the smoker back: his job search sees the smoker.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaBarrelSmoker1123eInnSearch"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaBarrelSmoker1123eInnSearch")
	public void qaTheInnIIStockBarrelDoesntHideItsSmokerFromTheSearch(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		BlockPos[] inn = inn2(helper);
		Villager v = joblessAbs(helper, besideOnFoot(helper, inn[1]));
		helper.assertTrue(v.blockPosition().distSqr(inn[1]) < v.blockPosition().distSqr(inn[2]), "(setup) the villager isn't nearer the barrel");
		searchSees(helper, v, inn[2], "inn_2 stock barrel by its free Shop Counter");
	}

	/**
	 * The same, live: the jobless villager beside Inn II's stock barrel takes a job within the time, the Shop Counter or
	 * the smoker (the Butcher's), never a Fisherman at a stock barrel, never left jobless.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaBarrelSmoker1123eInn"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaBarrelSmoker1123eInn")
	public void qaAJoblessVillagerByTheInnIIStockBarrelTakesAJobNotTheBarrel(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		BlockPos[] inn = inn2(helper);
		Villager v = joblessAbs(helper, besideOnFoot(helper, inn[1]));
		helper.onEachTick(() -> helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.FISHERMAN,
			"inn_2: took a stock barrel: " + where(helper, v)));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "inn_2: the villager died");
			boolean counter = inn[0].equals(site(v)) && v.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER;
			boolean smoker = holds(v, VillagerProfession.BUTCHER, inn[2]);
			helper.assertTrue(counter || smoker, "inn_2: hasn't taken the Shop Counter or the smoker yet: " + where(helper, v));
			v.discard();
		});
	}

	// ---- 4. a player's own barrel and smoker, room for a fisherman -------------------------------------------------------

	/**
	 * A player's free barrel and a free smoker 4 east (no building), the village with room for its one fisherman. The
	 * jobless villager beside the barrel becomes the Fisherman (never the Butcher); then a second jobless villager beside
	 * the barrel (now taken; the village full) becomes the Butcher at the smoker.
	 * <p>
	 * The rule holds only while he is nearer the barrel than the smoker (villager-jobs.md), and a jobless villager strolls:
	 * 1 of 60 repeats (and 1 of 10 in the 2026-11-23 nightly) he hopped off to (13, 2, 7), nearer the smoker, within 55
	 * ticks, his search saw the smoker there as the rule allows, and he turned Butcher. So he is stood back beside the
	 * barrel every tick while jobless: on open ground, the walk to the smoker as free as before (no pen, which would keep
	 * the smoker from him whatever the rule says), the barrel within his reach from where he stands. Standing still he
	 * could never finish a walk to the smoker, so the check is on the search itself: his job search never picks the smoker.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaBarrelSmoker1123ePlayer"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaBarrelSmoker1123ePlayer")
	public void qaAPlayersBarrelAndSmokerMakeAFishermanThenAButcher(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		barrelAndSmoker(helper);
		BlockPos barrel = helper.absolutePos(BARREL);
		BlockPos smoker = helper.absolutePos(SMOKER);
		BlockPos beside = helper.absolutePos(BESIDE);
		Villager first = joblessAbs(helper, beside);
		Villager[] second = {null};
		helper.onEachTick(() -> {
			helper.assertTrue(first.getVillagerData().getProfession() != VillagerProfession.BUTCHER,
				"the villager beside the free barrel became the Butcher: " + where(helper, first));
			if (first.getVillagerData().getProfession() == VillagerProfession.NONE) {
				helper.assertTrue(!first.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(GlobalPos::pos).filter(smoker::equals).isPresent(),
					"the jobless villager beside the free barrel picked the smoker in his job search: " + where(helper, first));
				first.setPos(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5); // beside the barrel, wherever he strolled this tick
				helper.assertTrue(first.blockPosition().distSqr(barrel) < first.blockPosition().distSqr(smoker), "(setup) the villager isn't nearer the barrel");
			}
			if (second[0] == null && holds(first, VillagerProfession.FISHERMAN, barrel)) {
				second[0] = joblessAbs(helper, helper.absolutePos(BARREL.north()));
			} else if (second[0] != null) {
				helper.assertTrue(holds(first, VillagerProfession.FISHERMAN, barrel), "the Fisherman left his barrel: " + where(helper, first));
			}
		});
		becomesButcherAtTheSmoker(helper, () -> second[0], "the second villager by the taken barrel", first::discard,
			() -> "no Fisherman at the free barrel yet: " + where(helper, first));
	}

	// ---- 5. saved and loaded mid-wait (case 1) -----------------------------------------------------------------------------

	/**
	 * Case 1 (no room for a fisherman), the jobless villager saved and loaded on his first tick and again 60 ticks later if
	 * still jobless: he still becomes the Butcher at the smoker.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaBarrelSmoker1123eReload"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaBarrelSmoker1123eReload")
	public void qaWithNoRoomTheVillagerSavedAndLoadedMidWaitStillTakesTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		Villager fisher = theVillagesFisherman(helper);
		barrelAndSmoker(helper);
		Villager[] who = {joblessAbs(helper, helper.absolutePos(BESIDE))};
		int[] reloads = {0};
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			Villager v = who[0];
			helper.assertTrue(v.isAlive(), "the villager died");
			if (reloads[0] == 0) {
				helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.NONE, "(setup) took a job before the first reload: " + where(helper, v));
				reloads[0] = 1;
				who[0] = reload(helper, v);
			} else if (reloads[0] == 1 && ticks[0] >= 60 && v.getVillagerData().getProfession() == VillagerProfession.NONE) {
				reloads[0] = 2;
				who[0] = reload(helper, v);
			}
		});
		becomesButcherAtTheSmoker(helper, () -> who[0], "village full, saved and loaded mid-wait", fisher::discard);
	}

	// ---- 6. the cap off -------------------------------------------------------------------------------------------------

	/** The cap off (vanilla): the jobless villager beside the free barrel; his job search sees the smoker too. */
	//$ gametest_ticks_batch HUGE '200' '"qaBarrelSmoker1123eOff"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaBarrelSmoker1123eOff")
	public void qaWithTheCapOffTheSearchBesideTheBarrelSeesTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, false);
		barrelAndSmoker(helper);
		Villager v = joblessAbs(helper, helper.absolutePos(BESIDE));
		searchSees(helper, v, helper.absolutePos(SMOKER), "cap off");
	}
}
