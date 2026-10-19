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
 * Bug B201: a jobless villager walking to a job block they picked on their own (vanilla holds it for them on the way) and
 * given another one with a job's item let the first stay held for nobody, so no other villager could take it.
 */
public class StationsWalkedToGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos LECTERN = new BlockPos(3, 2, 3);
	private static final BlockPos COMPOSTER = new BlockPos(6, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 5);

	/**
	 * A villager on the way to a lectern, sneak-right-clicked with sweet berries, becomes the Orchard Keeper at the
	 * composter: the lectern is free again, and the composter is theirs.
	 */
	//$ gametest_ticks_batch AREA '100' '"stationsWalkedToBlockLetGo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "stationsWalkedToBlockLetGo")
	public void aBlockTheyWalkedToIsLetGoWhenGivenAnother(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		PoiManager poi = level.getPoiManager();
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		BlockPos lectern = helper.absolutePos(LECTERN);
		BlockPos composter = helper.absolutePos(COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.runAfterDelay(2, () -> {
			// what vanilla's AcquirePoi does when a jobless villager picks a block to walk to: its place is taken and it's
			// remembered as their potential job site
			villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
			helper.assertTrue(poi.take(h -> true, (h, p) -> p.equals(lectern), lectern, 1).isPresent(), "setup: the lectern couldn't be held");
			villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), lectern));
			helper.assertTrue(poi.getFreeTickets(lectern) == 0, "setup: the lectern they walk to isn't held");

			StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), villager, new ItemStack(Items.SWEET_BERRIES), true);

			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER
				&& composter.equals(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"setup: not made the Orchard Keeper at the composter: " + villager.getVillagerData().getProfession() + " at "
					+ villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(), "still walking to the lectern");
			helper.assertTrue(poi.getFreeTickets(lectern) == 1, "the lectern they walked to is still held, for nobody (free places "
				+ poi.getFreeTickets(lectern) + ")");
			helper.assertTrue(poi.getFreeTickets(composter) == 0, "the composter they were given isn't held for them");
			helper.succeed();
		});
	}
}
