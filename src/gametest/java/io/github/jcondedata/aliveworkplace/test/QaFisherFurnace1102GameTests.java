package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.work.Furnaces;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * QA for B147 from its spec ("he collects his smelted output and stores it like his catch"): when a furnace by the
 * fisherman's barrel calls him home ({@code Furnaces.needsTending}). Cooked fish with room in the barrel calls him; an
 * empty furnace with raw fish in the barrel calls him; cooked fish alone (nothing to cook) or a barrel with no room
 * at all doesn't. One edge from both sides: a barrel whose every slot holds something but whose cooked cod stack still
 * has room takes the cooked cod, so it must call him too.
 */
public class QaFisherFurnace1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BARREL = new BlockPos(2, 2, 2);
	private static final BlockPos FURNACE = new BlockPos(4, 2, 2);

	private static Container barrel(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(FURNACE, Blocks.FURNACE);
		return (Container) helper.getBlockEntity(BARREL);
	}

	private static AbstractFurnaceBlockEntity furnace(GameTestHelper helper) {
		return (AbstractFurnaceBlockEntity) helper.getBlockEntity(FURNACE);
	}

	private static boolean calls(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(BARREL);
		return Furnaces.needsTending(helper.getLevel(), at, List.of(at), Furnaces::isFish);
	}

	/** Every slot of the barrel but the last full of cobblestone; the last holds {@code last}. */
	private static void fill(Container barrel, ItemStack last) {
		for (int i = 0; i < barrel.getContainerSize() - 1; i++) {
			barrel.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		barrel.setItem(barrel.getContainerSize() - 1, last);
	}

	/** Cooked cod in the furnace, room in the barrel: he's called home. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void qaCookedFishWithRoomCallsHim(GameTestHelper helper) {
		barrel(helper);
		furnace(helper).setItem(2, new ItemStack(Items.COOKED_COD, 3));
		helper.assertTrue(calls(helper), "cooked cod in the furnace and an empty barrel, but he isn't called");
		helper.succeed();
	}

	/** An empty furnace and raw salmon in the barrel: he's called to load it; only cooked fish in the barrel: he isn't. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void qaRawFishCallsHimCookedFishDoesnt(GameTestHelper helper) {
		Container barrel = barrel(helper);
		barrel.setItem(0, new ItemStack(Items.COOKED_SALMON, 5));
		barrel.setItem(1, new ItemStack(Items.PUFFERFISH, 2));
		helper.assertTrue(!calls(helper), "only cooked salmon and a pufferfish (nothing to cook) in the barrel, but he's called");
		barrel.setItem(2, new ItemStack(Items.SALMON, 1));
		helper.assertTrue(calls(helper), "raw salmon in the barrel and an empty furnace, but he isn't called");
		helper.succeed();
	}

	/** Cooked cod waiting and a barrel full to the brim (every stack full): there is nowhere to put it, so no call. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void qaABrimFullBarrelDoesntCallHim(GameTestHelper helper) {
		Container barrel = barrel(helper);
		fill(barrel, new ItemStack(Items.COBBLESTONE, 64));
		furnace(helper).setItem(2, new ItemStack(Items.COOKED_COD, 3));
		helper.assertTrue(!calls(helper), "the barrel has no room at all, but he's called");
		helper.succeed();
	}

	/** Every slot holds something, but the cooked cod stack is 10 of 64: the 3 cooked cod fit, so he's called. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void qaABarrelWithRoomOnTheCookedStackCallsHim(GameTestHelper helper) {
		Container barrel = barrel(helper);
		fill(barrel, new ItemStack(Items.COOKED_COD, 10));
		furnace(helper).setItem(2, new ItemStack(Items.COOKED_COD, 3));
		helper.assertTrue(SupplyContainers.freeSlots(helper.getLevel(), List.of(helper.absolutePos(BARREL))) == 0, "setup: the barrel has a free slot");
		helper.assertTrue(calls(helper), "the barrel's cooked cod stack has room for 54 more, but the 3 cooked cod in the furnace don't call him");
		helper.succeed();
	}
}
