package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.fish.FishermanCap;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * B1463: the rebuilt Supply Shop keeps six of its eight stock stores in barrels, right behind the Shop Counter, and a
 * barrel is the vanilla Fisherman's workstation. In a village that had no fisherman yet (the fisherman cap lets a village
 * take one), a jobless villager walking into the shop could take a stock barrel and turn fisherman instead of minding the
 * counter. Now, while a free workstation of another job stands within supply reach of a barrel (8 across, 4 up or down:
 * the reach in which that job block reads it as its store), a jobless villager's job search doesn't see the barrel: he
 * takes the workshop's own job block. With the fisherman cap off, villagers take barrels as in vanilla.
 */
public class B1463GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The shop's back aisle, template (5, 1, 8): a stock barrel either side, the Shop Counter two blocks in front. */
	private static final BlockPos AISLE = new BlockPos(5, 1, 8);

	/** The shop placed, a jobless villager in its back aisle between two stock barrels; the cap on or off. */
	private static Villager joblessInTheAisle(GameTestHelper helper, boolean cap) {
		Leftovers.clear(helper);
		boolean random = RandomJobs.ENABLED;
		boolean capped = FishermanCap.ENABLED;
		RandomJobs.ENABLED = false; // the Shop Counter's own first job, the Shopkeeper (36.17 picks at random)
		FishermanCap.ENABLED = cap;
		Leftovers.after(helper, () -> {
			RandomJobs.ENABLED = random;
			FishermanCap.ENABLED = capped;
		});
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, StarterBlueprints.SUPPLY_SHOP);
		helper.assertTrue(level.getBlockState(origin.offset(4, 1, 8)).is(Blocks.BARREL)
			&& level.getBlockState(origin.offset(6, 1, 8)).is(Blocks.BARREL)
			&& level.getBlockState(origin.offset(5, 1, 6)).is(ModBlocks.SHOP_COUNTER), "(setup) the shop's aisle has moved");
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		Villager v = helper.spawn(EntityType.VILLAGER, QaRebuiltWorkshops1121cLiveGameTests.AT.offset(AISLE));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		// B1478: he stands on the floor, but a freshly spawned mob counts as in the air after its first tick (it hasn't
		// moved, so it hasn't touched the floor), and vanilla's job search finds no path for a mob in the air. When that
		// search (first run 0 to 19 ticks in) fell on that tick, the five nearest job blocks (the barrels beside him, the
		// counter) were skipped for 2 to 4 seconds and he took a farther one (about 1 run in 10). He starts landed.
		v.setOnGround(true);
		v.setDeltaMovement(0, -0.1, 0);
		return v;
	}

	private static String where(Villager v, BlockPos origin) {
		BlockPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(null);
		return "job " + v.getVillagerData().getProfession() + ", site " + (site == null ? "none" : site.subtract(origin).toShortString())
			+ ", standing at " + v.blockPosition().subtract(origin).toShortString() + " (template coordinates)";
	}

	/** A jobless villager among the shop's stock barrels takes the Shop Counter, not a barrel. */
	//$ gametest_ticks_batch HUGE '1200' '"b1463Aisle"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "b1463Aisle")
	public void aJoblessVillagerAmongTheSupplyShopsBarrelsTakesTheCounter(GameTestHelper helper) {
		Villager v = joblessInTheAisle(helper, true);
		BlockPos origin = helper.absolutePos(QaRebuiltWorkshops1121cLiveGameTests.AT);
		BlockPos counter = origin.offset(5, 1, 6);
		helper.onEachTick(() -> helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.FISHERMAN,
			"took a stock barrel: " + where(v, origin)));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			BlockPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(null);
			helper.assertTrue(counter.equals(site) && v.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER,
				"hasn't taken the Shop Counter yet: " + where(v, origin));
			v.discard();
		});
	}

	/** The fisherman cap off (the config's vanilla villages): the barrel right beside him is a workstation like any. */
	//$ gametest_ticks_batch HUGE '1200' '"b1463CapOff"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "b1463CapOff")
	public void withTheFishermanCapOffTheBarrelBesideHimMakesAFisherman(GameTestHelper helper) {
		Villager v = joblessInTheAisle(helper, false);
		// B1478: with the cap off, vanilla's job search takes the nearest job block he can reach, so this checks the barrel
		// beside him is seen at all: he stays in the aisle (strolled to the shop's front, the Shop Counter is rightly nearer).
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		BlockPos origin = helper.absolutePos(QaRebuiltWorkshops1121cLiveGameTests.AT);
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(v.blockPosition().equals(origin.offset(AISLE)), "(setup) he left the aisle: " + where(v, origin));
			BlockPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(null);
			helper.assertTrue(site != null && helper.getLevel().getBlockState(site).is(Blocks.BARREL)
				&& v.getVillagerData().getProfession() == VillagerProfession.FISHERMAN, "no fisherman yet: " + where(v, origin));
			v.discard();
		});
	}
}
