package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for B201 from its spec ("the old target's POI is released first") at its other edge: the block a villager walks to
 * on their own IS the one they're given. It must stay held for them, once: not let go (then anyone could take it) and
 * not held twice. And a second villager can't then be given it.
 */
public class QaStationsWalkedTo1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COMPOSTER = new BlockPos(6, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 5);

	/** Walking to the composter and given it with sweet berries: the Orchard Keeper there, the composter held once, for them. */
	//$ gametest_ticks_batch AREA '100' '"qaStationsWalkedToKept1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaStationsWalkedToKept1102")
	public void qaTheBlockTheyWalkedToAndAreGivenStaysTheirs(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		PoiManager poi = level.getPoiManager();
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		BlockPos composter = helper.absolutePos(COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.runAfterDelay(2, () -> {
			// as vanilla's AcquirePoi holds a block a jobless villager walks to
			villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
			helper.assertTrue(poi.take(h -> true, (h, p) -> p.equals(composter), composter, 1).isPresent(), "setup: the composter couldn't be held");
			villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), composter));

			StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), villager, new ItemStack(Items.SWEET_BERRIES), true);

			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER
				&& composter.equals(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"not made the Orchard Keeper at the composter they walked to: " + villager.getVillagerData().getProfession() + " at "
					+ villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(), "still walking to it");
			helper.assertTrue(poi.getFreeTickets(composter) == 0, "the composter they were given, the one they walked to, was let go (free places "
				+ poi.getFreeTickets(composter) + ")");
			helper.assertTrue(poi.getType(composter).isPresent(), "the composter is no job block any more");
			helper.succeed();
		});
	}
}
