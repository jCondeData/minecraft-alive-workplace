package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.fish.FishermanCap;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
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
 * ROADMAP 26.13, a cap on fishermen per village: builds are full of storage barrels (the Fisherman's workstation), so
 * without it most jobless villagers turned fisherman. A village takes one fisherman plus one more per ten villagers
 * ({@code fishermanCap}, {@code fishermenPerVillage}, {@code villagersPerFisherman}); a jobless villager passes a free
 * barrel by once the village has its fishermen and takes another workstation; a fisherman over the cap keeps his job,
 * across a reload too.
 */
public class FishermanCapGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The village's fisherman and his barrel. */
	private static final BlockPos FISHER_BARREL = new BlockPos(3, 2, 3);
	/** A second barrel with a fisherman, for the village over its cap. */
	private static final BlockPos SECOND_BARREL = new BlockPos(3, 2, 8);
	/** A free barrel (a storage barrel in a build), the jobless villager two blocks from it. */
	private static final BlockPos FREE_BARREL = new BlockPos(11, 2, 10);
	private static final BlockPos JOBLESS = new BlockPos(11, 2, 12);
	/** Another free workstation, farther from the jobless villager than the barrel. */
	private static final BlockPos FLETCHING = new BlockPos(17, 2, 10);

	/**
	 * The cap switched on with the given numbers (gametests run with it off, see {@code WorkplaceConfig.apply}), the
	 * village a short radius round the barrels; everything put back after the test.
	 */
	private static void cap(GameTestHelper helper, boolean on, int base, int perVillagers) {
		Leftovers.clear(helper);
		boolean enabled = FishermanCap.ENABLED;
		int b = FishermanCap.BASE;
		int per = FishermanCap.PER_VILLAGERS;
		int radius = FishermanCap.RADIUS;
		FishermanCap.ENABLED = on;
		FishermanCap.BASE = base;
		FishermanCap.PER_VILLAGERS = perVillagers;
		FishermanCap.RADIUS = 24;
		Leftovers.after(helper, () -> {
			FishermanCap.ENABLED = enabled;
			FishermanCap.BASE = b;
			FishermanCap.PER_VILLAGERS = per;
			FishermanCap.RADIUS = radius;
		});
		helper.setDayTime(1000); // a working morning: sleeping villagers look for no job
	}

	/** A fisherman at a barrel, given the job by order (orders aren't capped). */
	private static Villager fisherman(GameTestHelper helper, BlockPos barrel) {
		helper.setBlock(barrel, Blocks.BARREL);
		Villager fisher = helper.spawn(EntityType.VILLAGER, barrel.east());
		Jobs.employ(helper.getLevel(), fisher, helper.absolutePos(barrel), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		return fisher;
	}

	/** A villager standing still who counts as one of the village (a nitwit takes no job). */
	private static void neighbour(GameTestHelper helper, int i) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * (i % 8), 2, 17 + i / 8));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));
		v.setNoAi(true);
	}

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	/** Ticks the free barrel's place is held by someone (taken or picked), counted each tick. */
	private static void countHeld(GameTestHelper helper, int[] held) {
		ServerLevel level = helper.getLevel();
		BlockPos free = helper.absolutePos(FREE_BARREL);
		helper.onEachTick(() -> {
			if (level.getPoiManager().getCountInRange(h -> true, free, 0, PoiManager.Occupancy.HAS_SPACE) == 0) {
				held[0]++;
			}
		});
	}

	/**
	 * Cap reached: a village of two with its one fisherman. The jobless villager next to a free barrel never takes it (nor
	 * holds it while walking), and takes the free fletching table farther away instead.
	 */
	//$ gametest_ticks_batch AREA '1400' '"fishermanCapReached"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "fishermanCapReached")
	public void aVillageWithItsFishermanPassesTheBarrelBy(GameTestHelper helper) {
		cap(helper, true, 1, 10);
		ServerLevel level = helper.getLevel();
		fisherman(helper, FISHER_BARREL);
		helper.setBlock(FREE_BARREL, Blocks.BARREL);
		helper.setBlock(FLETCHING, Blocks.FLETCHING_TABLE);
		Villager jobless = helper.spawn(EntityType.VILLAGER, JOBLESS);
		helper.assertTrue(FishermanCap.full(level, helper.absolutePos(FREE_BARREL)), "a village of 2 with its fisherman counts as having room for another");
		int[] held = {0};
		countHeld(helper, held);
		helper.onEachTick(() -> helper.assertFalse(job(jobless) == VillagerProfession.FISHERMAN, "took the barrel: a second fisherman in a village of 2"));
		helper.succeedWhen(() -> {
			helper.assertTrue(job(jobless) == VillagerProfession.FLETCHER, "still " + job(jobless) + " (held the barrel " + held[0] + " ticks)");
			GlobalPos site = jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
			helper.assertTrue(site != null && site.pos().equals(helper.absolutePos(FLETCHING)), "works at " + site);
			helper.assertTrue(held[0] < 20, "the free barrel was held " + held[0] + " ticks");
		});
	}

	/**
	 * The cap grows with the village: nine villagers take one fisherman, ten take two. With the tenth villager, the
	 * jobless villager takes the free barrel (no other workstation is there).
	 */
	//$ gametest_ticks_batch AREA '1200' '"fishermanCapGrows"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "fishermanCapGrows")
	public void aBiggerVillageTakesAnotherFisherman(GameTestHelper helper) {
		cap(helper, true, 1, 10);
		ServerLevel level = helper.getLevel();
		BlockPos free = helper.absolutePos(FREE_BARREL);
		fisherman(helper, FISHER_BARREL);
		helper.setBlock(FREE_BARREL, Blocks.BARREL);
		for (int i = 0; i < 7; i++) {
			neighbour(helper, i);
		}
		Villager jobless = helper.spawn(EntityType.VILLAGER, JOBLESS);
		helper.assertTrue(FishermanCap.cap(9) == 1 && FishermanCap.cap(10) == 2 && FishermanCap.cap(19) == 2 && FishermanCap.cap(20) == 3,
			"1 fisherman plus 1 per 10 villagers: " + FishermanCap.cap(9) + ", " + FishermanCap.cap(10) + ", " + FishermanCap.cap(20));
		helper.assertTrue(FishermanCap.full(level, free), "9 villagers with a fisherman count as having room for another");
		neighbour(helper, 7);
		helper.assertFalse(FishermanCap.full(level, free), "10 villagers with one fisherman count as having no room for another");
		helper.succeedWhen(() -> {
			helper.assertTrue(job(jobless) == VillagerProfession.FISHERMAN, "still " + job(jobless));
			GlobalPos site = jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
			helper.assertTrue(site != null && site.pos().equals(free), "works at " + site);
		});
	}

	/** The cap switched off: the same village of two takes a second fisherman at the free barrel, as in vanilla. */
	//$ gametest_ticks_batch AREA '1200' '"fishermanCapOff"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "fishermanCapOff")
	public void withTheCapOffTheBarrelIsTaken(GameTestHelper helper) {
		cap(helper, false, 1, 10);
		ServerLevel level = helper.getLevel();
		fisherman(helper, FISHER_BARREL);
		helper.setBlock(FREE_BARREL, Blocks.BARREL);
		Villager jobless = helper.spawn(EntityType.VILLAGER, JOBLESS);
		helper.assertFalse(FishermanCap.full(level, helper.absolutePos(FREE_BARREL)), "the cap is off and still counts");
		helper.succeedWhen(() -> helper.assertTrue(job(jobless) == VillagerProfession.FISHERMAN, "still " + job(jobless)));
	}

	/** The file's switch and numbers: their defaults, clamping, and apply() putting them into effect. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFishermanCapIsReadFromTheConfig(GameTestHelper helper) {
		WorkplaceConfig defaults = WorkplaceConfig.parse("{}");
		helper.assertTrue(defaults.fishermanCap && defaults.fishermenPerVillage == 1 && defaults.villagersPerFisherman == 10,
			"defaults: " + defaults.fishermanCap + ", " + defaults.fishermenPerVillage + ", " + defaults.villagersPerFisherman);
		WorkplaceConfig low = WorkplaceConfig.parse("{\"fishermanCap\": false, \"fishermenPerVillage\": 0, \"villagersPerFisherman\": -4}");
		helper.assertTrue(!low.fishermanCap && low.fishermenPerVillage == 1 && low.villagersPerFisherman == 1,
			"not clamped up: " + low.fishermenPerVillage + ", " + low.villagersPerFisherman);
		WorkplaceConfig high = WorkplaceConfig.parse("{\"fishermenPerVillage\": 99, \"villagersPerFisherman\": 999, \"villageRadius\": 30}");
		helper.assertTrue(high.fishermenPerVillage == 20 && high.villagersPerFisherman == 100,
			"not clamped down: " + high.fishermenPerVillage + ", " + high.villagersPerFisherman);
		WorkplaceConfig mid = WorkplaceConfig.parse("{\"fishermenPerVillage\": 2, \"villagersPerFisherman\": 5, \"villageRadius\": 30}");
		// Applying (and putting the defaults back straight away: other tests run on the same server).
		mid.apply();
		boolean applied = FishermanCap.BASE == 2 && FishermanCap.PER_VILLAGERS == 5 && FishermanCap.RADIUS == 30 && FishermanCap.cap(10) == 4
			// off in gametests whatever the file says (fishermen in the tests beside one would count); its tests switch it on
			&& !FishermanCap.ENABLED;
		String inUse = FishermanCap.ENABLED + ", " + FishermanCap.BASE + ", " + FishermanCap.PER_VILLAGERS + ", " + FishermanCap.RADIUS;
		new WorkplaceConfig().apply();
		helper.assertTrue(applied, "apply() didn't put the values into effect: " + inUse);
		helper.assertTrue(FishermanCap.BASE == 1 && FishermanCap.PER_VILLAGERS == 10 && FishermanCap.RADIUS == 48, "defaults not restored");
		helper.succeed();
	}

	/**
	 * Save and reload: a village of three with two fishermen (given by order) is over its cap. The second fisherman,
	 * saved and read back as when his chunk loads, is still a fisherman at his barrel and stays one; the jobless
	 * villager still passes the free barrel by.
	 */
	//$ gametest_ticks_batch AREA '600' '"fishermanCapReload"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "fishermanCapReload")
	public void aFishermanOverTheCapKeepsHisJobAfterAReload(GameTestHelper helper) {
		cap(helper, true, 1, 10);
		ServerLevel level = helper.getLevel();
		Villager first = fisherman(helper, FISHER_BARREL);
		Villager second = fisherman(helper, SECOND_BARREL);
		helper.setBlock(FREE_BARREL, Blocks.BARREL);
		Villager jobless = helper.spawn(EntityType.VILLAGER, JOBLESS);
		helper.assertTrue(job(first) == VillagerProfession.FISHERMAN && job(second) == VillagerProfession.FISHERMAN,
			"an order was refused by the cap: " + job(first) + ", " + job(second));
		BlockPos barrel = helper.absolutePos(SECOND_BARREL);
		CompoundTag saved = second.saveWithoutId(new CompoundTag());
		second.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		Villager reloaded = EntityType.VILLAGER.create(level);
		reloaded.load(saved);
		reloaded.setUUID(UUID.randomUUID());
		helper.assertTrue(job(reloaded) == VillagerProfession.FISHERMAN, "the fisherman over the cap lost his job on reload: " + job(reloaded));
		level.addFreshEntity(reloaded);
		int[] held = {0};
		countHeld(helper, held);
		helper.onEachTick(() -> {
			helper.assertTrue(job(reloaded) == VillagerProfession.FISHERMAN, "the reloaded fisherman is now " + job(reloaded));
			helper.assertFalse(job(jobless) == VillagerProfession.FISHERMAN, "a third fisherman in a village of three");
		});
		helper.runAtTickTime(500, () -> {
			GlobalPos site = reloaded.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
			helper.assertTrue(site != null && site.pos().equals(barrel), "the reloaded fisherman works at " + site);
			helper.assertTrue(job(first) == VillagerProfession.FISHERMAN, "the first fisherman is now " + job(first));
			helper.assertTrue(job(jobless) == VillagerProfession.NONE, "the jobless villager is now " + job(jobless));
			helper.assertTrue(held[0] < 20, "the free barrel was held " + held[0] + " ticks");
			helper.succeed();
		});
	}
}
