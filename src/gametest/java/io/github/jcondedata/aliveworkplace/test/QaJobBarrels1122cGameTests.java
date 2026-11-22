package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.fish.FishermanCap;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane C (2026-11-22), B1463 from its spec: with the fisherman cap on (26.13), a jobless villager's job search skips a
 * barrel within supply reach (8 across, 4 up or down) of a FREE workstation of another job; once that job block is taken
 * the barrels count again; with the cap off it stays vanilla. The player's view: a jobless villager at a finished Supply
 * Shop becomes its Shopkeeper, never a Fisherman from the shop's stock barrels; and a village still gets the fisherman
 * the cap allows it (fishers_hut.md: "a jobless villager by it [the barrel] becomes a Fisherman").
 * <p>
 * Each test: its own batch, {@code Leftovers.clear} first, the worker cap off, one fisherman allowed per village, the
 * Shop Counter's own first job (Shopkeeper: random jobs off), the day held at morning.
 */
public class QaJobBarrels1122cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The lone barrel of the hand-built layouts, and the villager's spot right beside it. */
	static final BlockPos BARREL = new BlockPos(10, 2, 10);
	static final BlockPos BESIDE = new BlockPos(10, 2, 11);

	static void setup(GameTestHelper helper, boolean cap) {
		setup(helper, cap, 0);
	}

	/** {@code maxWorkers}: the worker cap per village (25.5), 0 off. */
	static void setup(GameTestHelper helper, boolean cap, int maxWorkers) {
		Leftovers.clear(helper);
		boolean random = RandomJobs.ENABLED;
		boolean capped = FishermanCap.ENABLED;
		int base = FishermanCap.BASE;
		int per = FishermanCap.PER_VILLAGERS;
		int workers = WorkerLimits.MAX_PER_VILLAGE;
		RandomJobs.ENABLED = false;
		FishermanCap.ENABLED = cap;
		FishermanCap.BASE = 1;
		FishermanCap.PER_VILLAGERS = 10;
		WorkerLimits.MAX_PER_VILLAGE = maxWorkers;
		Leftovers.after(helper, () -> {
			RandomJobs.ENABLED = random;
			FishermanCap.ENABLED = capped;
			FishermanCap.BASE = base;
			FishermanCap.PER_VILLAGERS = per;
			WorkerLimits.MAX_PER_VILLAGE = workers;
		});
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
	}

	static Villager jobless(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		return v;
	}

	private static Villager joblessAbs(GameTestHelper helper, BlockPos abs) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.getLevel().addFreshEntity(v);
		return v;
	}

	private static BlockPos site(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(null);
	}

	private static String where(GameTestHelper helper, Villager v) {
		BlockPos s = site(v);
		return "job " + v.getVillagerData().getProfession() + ", site " + (s == null ? "none" : s.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " "
			+ helper.getLevel().getBlockState(s).getBlock().getName().getString());
	}

	/** Succeeds when {@code v} is a Fisherman whose job site is the barrel at absolute {@code barrel}. */
	static void becomesFishermanAt(GameTestHelper helper, Villager v, BlockPos barrel, String what) {
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(barrel.equals(site(v)) && v.getVillagerData().getProfession() == VillagerProfession.FISHERMAN,
				what + ": no fisherman at the barrel yet: " + where(helper, v));
			v.discard();
		});
	}

	/**
	 * Where the barrel isn't any free workstation's stock, vanilla's job search sees it beside the free Shop Counter, and
	 * which of the two the villager then picks is vanilla's (the pathfinder's, not always the nearest): the villager's own
	 * job search (PoiManager.findAllClosestFirstWithType, as vanilla's AcquirePoi calls it, with him thinking) returns
	 * the barrel. His AI is off so he takes nothing meanwhile.
	 */
	static void searchSeesTheBarrel(GameTestHelper helper, Villager v, BlockPos barrel, String what) {
		v.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			java.util.List<BlockPos> seen;
			WorkerLimits.thinking(v);
			try {
				seen = helper.getLevel().getPoiManager().findAllClosestFirstWithType(t -> t.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
					p -> true, v.blockPosition(), 48, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
					.map(com.mojang.datafixers.util.Pair::getSecond).map(BlockPos::immutable).toList();
			} finally {
				WorkerLimits.thinking(null);
			}
			helper.assertTrue(seen.contains(barrel), what + ": a jobless villager's search doesn't see the barrel; it sees "
				+ seen.stream().map(q -> q.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).toList());
			v.discard();
			helper.succeed();
		});
	}

	/** Never a Fisherman; succeeds when {@code v} holds the Shop Counter at absolute {@code counter} as Shopkeeper. */
	private static void becomesShopkeeperAt(GameTestHelper helper, Villager v, BlockPos counter, String what) {
		helper.onEachTick(() -> helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.FISHERMAN,
			what + ": took a barrel: " + where(helper, v)));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(counter.equals(site(v)) && v.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER,
				what + ": hasn't taken the Shop Counter yet: " + where(helper, v));
			v.discard();
		});
	}

	// ---- 1. the village still gets its fisherman --------------------------------------------------------------------

	/**
	 * Control: a lone barrel on open ground, a jobless villager beside it, the cap on and no fisherman in the village (it
	 * takes one): he becomes the Fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cLone"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cLone")
	public void qaALoneBarrelStillMakesTheVillagesFisherman(GameTestHelper helper) {
		setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		Villager v = jobless(helper, BESIDE);
		becomesFishermanAt(helper, v, helper.absolutePos(BARREL), "a lone barrel");
	}

	/** Control: the shipped Fisher's Hut I on flat ground, a jobless villager beside its barrel: he becomes its Fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cHut1"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cHut1")
	public void qaAJoblessVillagerAtTheFishersHutBecomesItsFisherman(GameTestHelper helper) {
		fishersHut(helper, StarterBlueprints.FISHERS_HUT);
	}

	static void fishersHut(GameTestHelper helper, StarterBlueprints.Entry entry) {
		setup(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, entry);
		BlockPos barrel = origin.offset(2, 1, 10);
		helper.assertTrue(level.getBlockState(barrel).is(Blocks.BARREL), "(setup) the hut's barrel has moved");
		Villager v = joblessAbs(helper, besideOnFoot(helper, barrel));
		becomesFishermanAt(helper, v, barrel, entry.id().getPath());
	}

	/** An open cell beside {@code block} (absolute) a villager can stand in. */
	private static BlockPos besideOnFoot(GameTestHelper helper, BlockPos block) {
		ServerLevel level = helper.getLevel();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos c = block.relative(d);
			if (level.getBlockState(c).getCollisionShape(level, c).isEmpty() && level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty()
				&& !level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty()) {
				return c;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("(setup) no open cell beside " + block.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
	}

	// ---- 2. the Supply Shop -----------------------------------------------------------------------------------------

	/**
	 * Two jobless villagers in the finished Supply Shop's back aisle: the first takes the Shop Counter (Shopkeeper); once
	 * it is taken the stock barrels count again under the cap, and the village (2 villagers, room for one fisherman)
	 * takes its fisherman: the other becomes a Fisherman at one of the shop's barrels. Never two on the counter.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaJobBarrels1122cTwo"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaJobBarrels1122cTwo")
	public void qaTwoJoblessVillagersInTheSupplyShopMakeAShopkeeperThenAFisherman(GameTestHelper helper) {
		setup(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, StarterBlueprints.SUPPLY_SHOP);
		BlockPos counter = origin.offset(5, 1, 6);
		helper.assertTrue(level.getBlockState(counter).is(ModBlocks.SHOP_COUNTER), "(setup) the Shop Counter has moved");
		List<Villager> two = List.of(jobless(helper, QaRebuiltWorkshops1121cLiveGameTests.AT.offset(5, 1, 8)),
			jobless(helper, QaRebuiltWorkshops1121cLiveGameTests.AT.offset(5, 1, 7)));
		helper.succeedWhen(() -> {
			int shopkeepers = 0;
			int fishermen = 0;
			for (Villager v : two) {
				helper.assertTrue(v.isAlive(), "a villager died");
				BlockPos s = site(v);
				if (counter.equals(s) && v.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER) {
					shopkeepers++;
				} else if (s != null && level.getBlockState(s).is(Blocks.BARREL) && v.getVillagerData().getProfession() == VillagerProfession.FISHERMAN
					&& QaRebuiltWorkshops1121bGameTests.within(s, counter, 8, 4)) {
					fishermen++;
				}
			}
			helper.assertTrue(shopkeepers == 1 && fishermen == 1, "not yet one Shopkeeper and one Fisherman: " + where(helper, two.get(0))
				+ " / " + where(helper, two.get(1)));
			two.forEach(Entity::discard);
		});
	}

	/**
	 * Save and reload mid-search: a jobless villager in the street before the shop is saved and loaded back (the shop's
	 * chunk unloaded and loaded) as soon as he has picked a workstation, or after 20 ticks; he still becomes the
	 * Shopkeeper, never a Fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cReload"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cReload")
	public void qaTheSupplyShopVillagerSavedAndLoadedMidSearchStillBecomesTheShopkeeper(GameTestHelper helper) {
		setup(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, StarterBlueprints.SUPPLY_SHOP);
		BlockPos counter = origin.offset(5, 1, 6);
		// in the street before the shop (as a player's villager walks up), so he is still on his way when saved
		Villager[] who = {jobless(helper, QaRebuiltWorkshops1121cLiveGameTests.AT.offset(5, 0, -3))};
		boolean[] reloaded = {false};
		long start = level.getGameTime();
		helper.onEachTick(() -> {
			Villager v = who[0];
			helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.FISHERMAN, "took a barrel: " + where(helper, v));
			if (!reloaded[0] && v.getVillagerData().getProfession() == VillagerProfession.NONE
				&& (v.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE) || level.getGameTime() - start >= 20)) {
				reloaded[0] = true;
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(v.save(tag), "the villager wasn't saved");
				v.discard();
				Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(back instanceof Villager, "the villager didn't load back: " + back);
				level.addFreshEntity(back);
				who[0] = (Villager) back;
			}
		});
		helper.succeedWhen(() -> {
			Villager v = who[0];
			helper.assertTrue(reloaded[0], "never saved and loaded before taking a job: " + where(helper, v));
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(counter.equals(site(v)) && v.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER,
				"hasn't taken the Shop Counter after the reload: " + where(helper, v));
			v.discard();
		});
	}

	// ---- 3. the edge of supply reach: 8 across and 4 up or down -----------------------------------------------------

	/** A barrel with the villager beside it, a free Shop Counter {@code dx} blocks east on flat ground. */
	private static void across(GameTestHelper helper, int dx, boolean hidden) {
		setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos counter = BARREL.offset(dx, 0, 0);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		Villager v = jobless(helper, BESIDE);
		if (hidden) {
			becomesShopkeeperAt(helper, v, helper.absolutePos(counter), "Shop Counter " + dx + " across");
		} else {
			searchSeesTheBarrel(helper, v, helper.absolutePos(BARREL), "Shop Counter " + dx + " across");
		}
	}

	/** 8 across: the barrel is in the counter's supply reach, so its stock: the villager beside it takes the counter. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cAcross8"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cAcross8")
	public void qaABarrelEightAcrossFromAFreeCounterIsItsStock(GameTestHelper helper) {
		across(helper, 8, true);
	}

	/** 9 across: out of the counter's reach, a plain barrel: the job search sees it. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cAcross9"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cAcross9")
	public void qaABarrelNineAcrossFromAFreeCounterIsSeenByTheJobSearch(GameTestHelper helper) {
		across(helper, 9, false);
	}

	/** 8 across on both axes (supply reach is a square, not a circle): still its stock. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cDiag"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cDiag")
	public void qaABarrelEightAcrossOnBothAxesFromAFreeCounterIsItsStock(GameTestHelper helper) {
		setup(helper, true);
		BlockPos barrel = new BlockPos(6, 2, 6);
		helper.setBlock(barrel, Blocks.BARREL);
		BlockPos counter = barrel.offset(8, 0, 8);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		Villager v = jobless(helper, barrel.offset(0, 0, -1));
		becomesShopkeeperAt(helper, v, helper.absolutePos(counter), "Shop Counter 8 across on x and z");
	}

	/**
	 * The barrel on a platform {@code h} blocks above a free Shop Counter on the ground (4 across, 1 along), a staircase of
	 * full blocks down from the platform; the villager on the platform beside the barrel.
	 */
	private static void above(GameTestHelper helper, int h, boolean hidden) {
		setup(helper, true);
		int top = 1 + h; // the platform's blocks; their top is the barrel's floor
		for (int x = 8; x <= 12; x++) {
			for (int z = 10; z <= 12; z++) {
				for (int y = 2; y <= top; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		for (int k = 1; k < h; k++) { // step k: its top one block lower each block south
			for (int y = 2; y <= top - k; y++) {
				helper.setBlock(new BlockPos(10, y, 12 + k), Blocks.STONE);
			}
		}
		BlockPos barrel = new BlockPos(10, top + 1, 10);
		helper.setBlock(barrel, Blocks.BARREL);
		BlockPos counter = new BlockPos(14, 2, 11);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		Villager v = jobless(helper, new BlockPos(10, top + 1, 11));
		if (hidden) {
			becomesShopkeeperAt(helper, v, helper.absolutePos(counter), "Shop Counter " + h + " below the barrel");
		} else {
			searchSeesTheBarrel(helper, v, helper.absolutePos(barrel), "Shop Counter " + h + " below the barrel");
		}
	}

	/** 4 down: in reach, the barrel is the counter's stock: the villager walks down and takes the counter. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cDown4"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cDown4")
	public void qaABarrelFourAboveAFreeCounterIsItsStock(GameTestHelper helper) {
		above(helper, 4, true);
	}

	/** 5 down: out of reach, a plain barrel: the job search sees it. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cDown5"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cDown5")
	public void qaABarrelFiveAboveAFreeCounterIsSeenByTheJobSearch(GameTestHelper helper) {
		above(helper, 5, false);
	}

	/** Once the counter is taken (a Shopkeeper already on it), the barrel 2 blocks away counts again: a Fisherman. */
	//$ gametest_ticks_batch HUGE '1600' '"qaJobBarrels1122cTaken"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaJobBarrels1122cTaken")
	public void qaOnceTheCounterIsTakenItsBarrelMakesAFisherman(GameTestHelper helper) {
		setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos counter = BARREL.offset(3, 0, 0);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		Villager keeper = jobless(helper, counter.offset(0, 0, 1));
		BlockPos abs = helper.absolutePos(counter);
		Villager[] second = {null};
		helper.onEachTick(() -> {
			if (second[0] == null && abs.equals(site(keeper))) {
				second[0] = jobless(helper, BESIDE);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(second[0] != null, "the first villager hasn't taken the counter: " + where(helper, keeper));
			Villager v = second[0];
			helper.assertTrue(helper.absolutePos(BARREL).equals(site(v)) && v.getVillagerData().getProfession() == VillagerProfession.FISHERMAN,
				"the barrel by a taken counter made no fisherman: " + where(helper, v));
			keeper.discard();
			v.discard();
		});
	}

	// ---- 4. the cap off ----------------------------------------------------------------------------------------------

	/** The cap off (vanilla): a barrel 2 blocks from a free Shop Counter: the job search sees it. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cOff"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cOff")
	public void qaWithTheCapOffTheBarrelNextToAFreeCounterIsSeenByTheJobSearch(GameTestHelper helper) {
		setup(helper, false);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(2, 0, 0), ModBlocks.SHOP_COUNTER);
		Villager v = jobless(helper, BESIDE);
		searchSeesTheBarrel(helper, v, helper.absolutePos(BARREL), "cap off");
	}

	/**
	 * The fisherman cap off but the worker cap on (25.5, 10 a village, none taken): the jobless search is filtered for the
	 * worker cap, yet barrels stay vanilla: a barrel 2 blocks from a free Shop Counter is seen.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cOffWorkers"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cOffWorkers")
	public void qaWithTheCapOffAndAWorkerCapOnTheBarrelNextToAFreeCounterIsSeenByTheJobSearch(GameTestHelper helper) {
		setup(helper, false, 10);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(2, 0, 0), ModBlocks.SHOP_COUNTER);
		Villager v = jobless(helper, BESIDE);
		searchSeesTheBarrel(helper, v, helper.absolutePos(BARREL), "cap off, worker cap 10");
	}

	// ---- 5. other rebuilt workshops with stock barrels by their job block ---------------------------------------------

	/**
	 * The shipped drawing placed, a jobless villager beside its stock barrel {@code barrelAt} (template coordinates, within
	 * reach of the job block at {@code jobAt}): he never turns Fisherman and takes the building's job block.
	 */
	private static void workshop(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos barrelAt, BlockPos jobAt, Block job) {
		setup(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, entry);
		BlockPos barrel = origin.offset(barrelAt);
		BlockPos jobBlock = origin.offset(jobAt);
		helper.assertTrue(level.getBlockState(barrel).is(Blocks.BARREL) && level.getBlockState(jobBlock).is(job), "(setup) " + entry.id().getPath() + " has changed");
		Villager v = joblessAbs(helper, besideOnFoot(helper, barrel));
		helper.onEachTick(() -> helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.FISHERMAN,
			entry.id().getPath() + ": took a stock barrel: " + where(helper, v)));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(jobBlock.equals(site(v)) && v.getVillagerData().getProfession() != VillagerProfession.NONE,
				entry.id().getPath() + ": hasn't taken its " + job.getName().getString() + " yet: " + where(helper, v));
			v.discard();
		});
	}

	/** The Supply Shop II: six stock barrels round its Shop Counter. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cShop2"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cShop2")
	public void qaAJoblessVillagerBesideTheSupplyShopIIsBarrelTakesItsCounter(GameTestHelper helper) {
		workshop(helper, StarterBlueprints.SUPPLY_SHOP_2, new BlockPos(8, 1, 8), new BlockPos(5, 1, 6), ModBlocks.SHOP_COUNTER);
	}

	/** The Inn: barrels behind its counter (the Shop Counter). */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cInn"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cInn")
	public void qaAJoblessVillagerBesideTheInnsBarrelTakesItsCounter(GameTestHelper helper) {
		workshop(helper, StarterBlueprints.INN, new BlockPos(10, 1, 6), new BlockPos(8, 1, 6), ModBlocks.SHOP_COUNTER);
	}

	/** The Storehouse: barrels by its Storehouse block. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cStore"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cStore")
	public void qaAJoblessVillagerBesideTheStorehousesBarrelTakesTheStorehouse(GameTestHelper helper) {
		workshop(helper, StarterBlueprints.STOREHOUSE, new BlockPos(6, 5, 3), new BlockPos(4, 5, 6), ModBlocks.STOREHOUSE);
	}
}
