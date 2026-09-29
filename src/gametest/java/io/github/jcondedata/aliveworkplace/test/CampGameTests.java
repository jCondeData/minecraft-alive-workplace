package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** The Settler's Wagon: a camp, supplies and two settlers, one of them a builder. */
public class CampGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** On open ground: the wagon, the bench, the chest of supplies (with a Village Hall) and two settlers, the first a builder. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "settlersMakeCamp")
	public void settlersMakeCamp(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(11, 2, 6)));
		helper.assertTrue(settlers.size() == 2, "settlers: " + settlers.size());
		BlockPos[] bench = new BlockPos[1];
		BlockPos[] chest = new BlockPos[1];
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(21, 8, 21)))) {
			if (level.getBlockState(p).is(ModBlocks.BUILDERS_BENCH)) {
				bench[0] = p.immutable();
			}
			if (level.getBlockState(p).is(Blocks.CHEST)) {
				chest[0] = p.immutable();
			}
		}
		helper.assertTrue(bench[0] != null && chest[0] != null, "bench " + bench[0] + ", chest " + chest[0]);
		ChestBlockEntity box = (ChestBlockEntity) level.getBlockEntity(chest[0]);
		box.unpackLootTable(player);
		helper.assertTrue(box.countItem(ModBlocks.VILLAGE_HALL.asItem()) == 1 && box.countItem(ModItems.BLUEPRINT) == 2, "supplies: hall "
			+ box.countItem(ModBlocks.VILLAGE_HALL.asItem()) + ", blueprints " + box.countItem(ModItems.BLUEPRINT));
		helper.succeedWhen(() -> helper.assertTrue(settlers.get(0).getVillagerData().getProfession() == ModVillagers.BUILDER,
			"the first settler is a " + settlers.get(0).getVillagerData().getProfession()));
	}

	/** Not in the middle of a hill: no camp, no settlers. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "noCampInsideAHill")
	public void noCampInsideAHill(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (int x = 2; x < 20; x++) {
			for (int z = 2; z < 20; z++) {
				for (int y = 2; y < 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(helper.getLevel(), player, helper.absolutePos(new BlockPos(11, 2, 6)));
		helper.assertTrue(settlers.isEmpty(), "made camp inside stone");
		helper.succeed();
	}
}
