package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-e-1107-0413, Create and the pack): a Create funnel outside a protected village on the side of a chest in its
 * last column. village-hall.md: with protection on only the owner, friends and operators may "open chests" in the
 * hall's area, and machines outside stop at the edge (B745). Vanilla has no way to take from a chest from the next
 * column (a hopper pulls only from above, in the chest's own column); Create's funnel on a chest's side does. The
 * control: with the village open the same funnel empties the same chest.
 */
public class QaCreateFunnelAtProtectedVillage1107eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** The village's last column on the hall's row (see {@link QaCreateIntoProtectedVillage1107eCompatTests}). */
	static final BlockPos CHEST = QaCreateIntoProtectedVillage1107eCompatTests.EDGE;

	static BlockState brassFunnelPulling() {
		return MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("brass_funnel").defaultBlockState(), "facing", "east"),
			"extracting", "true");
	}

	/** Frank's chest at the edge holding 16 diamonds, a brass funnel on its outside face; the diamonds left in it. */
	static Container chestAndFunnel(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 16));
		helper.setBlock(CHEST.east(), brassFunnelPulling());
		return chest;
	}

	static int diamonds(Container chest) {
		int n = 0;
		for (int s = 0; s < chest.getContainerSize(); s++) {
			if (chest.getItem(s).is(Items.DIAMOND)) {
				n += chest.getItem(s).getCount();
			}
		}
		return n;
	}

	static int diamondsOnTheGround(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.DIAMOND))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** The control: with the village open the brass funnel outside takes the diamonds out of the chest. */
	//$ gametest_ticks_batch AREA '300' '"qa_cprot_funnel_open"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cprot_funnel_open")
	public void aFunnelOutsideAnOpenVillageTakesFromTheChestAtItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaCreateIntoProtectedVillage1107eCompatTests.hall(helper, false);
		Container chest = chestAndFunnel(helper);
		helper.runAfterDelay(200, () -> {
			QaCreateIntoProtectedVillage1107eCompatTests.done(helper, hall);
			helper.assertTrue(diamonds(chest) < 16, "the control funnel took nothing from the chest: it holds " + diamonds(chest)
				+ " of 16 diamonds, " + diamondsOnTheGround(helper) + " on the ground");
			helper.succeed();
		});
	}
}
