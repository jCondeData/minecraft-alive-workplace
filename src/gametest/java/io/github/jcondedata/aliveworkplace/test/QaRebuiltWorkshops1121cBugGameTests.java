package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA lane C (2026-11-21, lane-c-1121-1853), bugs found testing the rebuilt workshops (Milestone 50) from their briefs.
 * Failing until fixed; they live on {@code tests/rebuilt-workshops-1121c}, not on main.
 */
public class QaRebuiltWorkshops1121cBugGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * flower_shop.md, "Storage": "Every indoor chest ... is reachable on foot". The drawing placed, a villager in the
	 * street in front: the game's pathfinder finds a path to within two blocks of every chest on the ground floor (a
	 * player walks there too). Found: the small room east of the shop (template x 8-10, z 2-4) holding the chest at
	 * (10, 1, 2) is sealed: its only opening, (7, 1, 3), is behind a top slab with a potted allium on it at (6, 1-2, 3),
	 * so neither a villager nor a player gets in; the path ends outside at (11, 1, 0).
	 */
	private static void everyGroundFloorChestIsWalkedTo(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, entry);
		var size = entry.size();
		List<BlockPos> chests = BlockPos.betweenClosedStream(origin.offset(0, 1, 0), origin.offset(size.getX() - 1, 2, size.getZ() - 1))
			.filter(p -> level.getBlockState(p).getBlock() instanceof ChestBlock || level.getBlockState(p).getBlock() instanceof BarrelBlock)
			.map(BlockPos::immutable).toList();
		helper.assertTrue(!chests.isEmpty(), "(setup) no chest on the ground floor");
		Villager walker = helper.spawn(EntityType.VILLAGER, QaRebuiltWorkshops1121cLiveGameTests.AT.offset((size.getX() - 1) / 2, 0, -3));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			List<String> cut = new ArrayList<>();
			for (BlockPos c : chests) {
				Path path = walker.getNavigation().createPath(Set.of(c), 2);
				if (path == null || !path.canReach()) {
					cut.add(c.subtract(origin).toShortString() + (path == null ? " (no path)" : " (ends at " + path.getEndNode().asBlockPos().subtract(origin).toShortString() + ")"));
				}
			}
			walker.discard();
			helper.assertTrue(cut.isEmpty(), entry.id().getPath() + ": ground-floor chests nobody can walk to from the street: " + cut + " of " + chests.size());
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cBugFlower1"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cBugFlower1")
	public void qaEveryChestOfTheFlowerShopIsWalkedTo(GameTestHelper helper) {
		everyGroundFloorChestIsWalkedTo(helper, StarterBlueprints.FLOWER_SHOP);
	}

	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cBugFlower2"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cBugFlower2")
	public void qaEveryChestOfTheFlowerShopIIIsWalkedTo(GameTestHelper helper) {
		everyGroundFloorChestIsWalkedTo(helper, StarterBlueprints.FLOWER_SHOP_2);
	}
}
