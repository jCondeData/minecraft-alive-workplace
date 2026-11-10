package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CrafterWork;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B1111/B1112: crafters stopped making anything for a builder whose Storehouse was full, and his line said "Nobody here
 * makes". Now the slots the makings free count as room, another store he takes from with room gets the makings, two
 * crafters don't count the same empty slot, and with no room anywhere his line says so. The whole-village cases are in
 * {@link QaStalledCrafting1109bGameTests} and {@link QaStalledCrafting1109bBugTests}.
 */
public class CraftersFullStoreGameTests implements FabricGameTest {
	private static final String BIG = QaStalledCrafting1109bGameTests.BIG;
	private static final BlockPos CHEST = new BlockPos(1, 2, 1);

	/** A full chest whose last slot holds exactly what's taken: that slot frees, so the made thing fits. */
	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void theSlotAnIngredientFreesCountsAsRoom(GameTestHelper helper) {
		List<BlockPos> chests = fullChest(helper, new ItemStack(Items.STONE_BRICKS, 2));
		Map<net.minecraft.world.item.Item, Integer> makes = Map.of(Items.STONE_BRICK_SLAB, 4);
		if (CrafterWork.roomFor(helper.getLevel(), chests, makes)) {
			throw new GameTestAssertException("a full chest has room as it is");
		}
		if (!CrafterWork.roomAfter(helper.getLevel(), chests, makes, Map.of(Items.STONE_BRICKS, 2), chests, 0)) {
			throw new GameTestAssertException("the 2 bricks taken free a slot for the 4 slabs");
		}
		// another crafter's job already counts on that slot (B1112: the Mason's slabs took the Carpenter's)
		if (CrafterWork.roomAfter(helper.getLevel(), chests, makes, Map.of(Items.STONE_BRICKS, 2), chests, 1)) {
			throw new GameTestAssertException("a slot another job will fill was counted twice");
		}
		// part of a stack taken frees nothing
		if (CrafterWork.roomAfter(helper.getLevel(), chests, makes, Map.of(Items.STONE_BRICKS, 1), chests, 0)) {
			throw new GameTestAssertException("1 brick of 2 taken freed a slot");
		}
		// taken from somewhere else first, the chest's slot stays full
		if (CrafterWork.roomAfter(helper.getLevel(), chests, makes, Map.of(Items.STONE_BRICKS, 2), List.of(), 0)) {
			throw new GameTestAssertException("bricks taken from other chests freed a slot here");
		}
		helper.succeed();
	}

	/** The builder's line names what has nowhere to go, ahead of what's short, and never "Nobody here makes" for it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFullStoreIsSaidOnTheBuildersLine(GameTestHelper helper) {
		UUID site = UUID.randomUUID();
		long now = helper.getLevel().getGameTime();
		try {
			JobShortages.full(site, Items.CHEST, now);
			JobShortages.full(site, Items.LADDER, now);
			Component line = JobShortages.line(helper.getLevel(), site, Map.of(Items.CHEST, 1, Items.LADDER, 3));
			String text = line == null ? "" : line.getString();
			if (!text.equals("No room to bring Ladder, Chest: the chests he takes from are full")) {
				throw new GameTestAssertException("full line: " + text);
			}
			JobShortages.lacks(site, Items.BREWING_STAND, JobShortages.lackLine(Map.of(Items.BLAZE_ROD, 1), 1, Items.BREWING_STAND), now);
			text = JobShortages.line(helper.getLevel(), site, Map.of(Items.CHEST, 1, Items.BREWING_STAND, 1)).getString();
			if (!text.equals("No room to bring Chest: the chests he takes from are full. Can't be made yet: short of 1 Blaze Rod to make 1 Brewing Stand")) {
				throw new GameTestAssertException("full and short line: " + text);
			}
			// a job that can make it after all wins (room was made)
			JobShortages.covered(site, Items.CHEST, now);
			Component after = JobShortages.line(helper.getLevel(), site, Map.of(Items.CHEST, 1));
			if (after != null) {
				throw new GameTestAssertException("still said after a job took it: " + after.getString());
			}
		} finally {
			JobShortages.forget(site);
		}
		helper.succeed();
	}

	/**
	 * The Storehouse by the builder's table is full (16 bricks among cobblestone, nothing frees a slot), a second
	 * Storehouse across the village has room: the Mason cuts the slabs into that one and the builder lays them.
	 */
	//$ gametest_ticks_batch BIG '2400' '"fullStoreOtherStore"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "fullStoreOtherStore")
	public void aFullStoresMakingsGoToAnotherStoreWithRoom(GameTestHelper helper) {
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "otherstore", QaStalledCrafting1109bGameTests.slabRow(),
			true, false, List.of(new ItemStack(Items.STONE_BRICKS, 16)), true, false, false);
		BlockPos far = new BlockPos(20, 2, 19);
		helper.setBlock(far, ModBlocks.STOREHOUSE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(far));
		helper.setBlock(far.south(2), Blocks.CHEST);
		QaStalledCrafting1109bGameTests.madeOrSaid(helper, run, Map.of(Blocks.STONE_BRICK_SLAB, 4), Items.STONE_BRICK_SLAB, Integer.MAX_VALUE);
	}

	/** A chest full of cobblestone, its last slot holding {@code last}. */
	private static List<BlockPos> fullChest(GameTestHelper helper, ItemStack last) {
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize() - 1; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		chest.setItem(chest.getContainerSize() - 1, last.copy());
		return List.of(helper.absolutePos(CHEST));
	}
}
