package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Rebuilt workshops built and walked whole (B1451, B1454; written by QA lanes B and C on 2026-11-21 and kept here once
 * fixed). The drawings' wall-hung pieces are checked for every shipped build in tools/blueprints/test_owner_rules.py
 * (WallHungPieces), which also covers the Farmstead's hook (B1452).
 */
public class BlueprintPiecesGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * carpenters_workshop.md in the world: a Builder builds the Carpenter's Workshop from exactly its materials across a
	 * save and reload mid-STRUCTURE; it ends DONE with every block right (B1451: a tripwire hook drawn on a glass pane
	 * couldn't stand and was skipped) and nothing left over; the built workshop has one crafting table, a job site, a
	 * chest within 8 / 4 of it and a bed.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"blueprintPiecesCarpenterBuild"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "blueprintPiecesCarpenterBuild")
	public void aBuilderBuildsTheCarpentersWorkshopWholeFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaRebuiltWorkshops1121bGameTests.buildAcrossAReload(helper, StarterBlueprints.CARPENTERS_WORKSHOP, new BlockPos(9, 2, 9), placement -> {
			Vec3i size = StarterBlueprints.CARPENTERS_WORKSHOP.size();
			List<BlockPos> tables = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CRAFTING_TABLE);
			helper.assertTrue(tables.size() == 1, "the built workshop has " + tables.size() + " crafting tables");
			helper.assertTrue(level.getPoiManager().getType(tables.get(0)).isPresent(), "the built crafting table is no job site");
			helper.assertTrue(QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CHEST).stream()
				.anyMatch(c -> QaRebuiltWorkshops1121bGameTests.within(c, tables.get(0), 8, 4)), "no built chest within 8 / 4 of the crafting table");
			boolean bed = false;
			for (BlockPos p : BlockPos.betweenClosed(placement.origin(), placement.origin().offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				bed |= level.getBlockState(p).getBlock() instanceof BedBlock;
			}
			helper.assertTrue(bed, "the built workshop has no bed");
		});
	}

	/**
	 * flower_shop.md, "Storage": "Every indoor chest ... is reachable on foot". The drawing placed, a villager in the
	 * street in front: the game's pathfinder finds a path to within two blocks of every chest and barrel on the ground
	 * floor (B1454: the parlour's chest at (10, 1, 2) was sealed in behind a staging slab across its doorway).
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

	//$ gametest_ticks_batch HUGE '100' '"blueprintPiecesFlower1"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "blueprintPiecesFlower1")
	public void everyChestOfTheFlowerShopIsWalkedTo(GameTestHelper helper) {
		everyGroundFloorChestIsWalkedTo(helper, StarterBlueprints.FLOWER_SHOP);
	}

	//$ gametest_ticks_batch HUGE '100' '"blueprintPiecesFlower2"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "blueprintPiecesFlower2")
	public void everyChestOfTheFlowerShopIIIsWalkedTo(GameTestHelper helper) {
		everyGroundFloorChestIsWalkedTo(helper, StarterBlueprints.FLOWER_SHOP_2);
	}
}
