package io.github.jcondedata.aliveworkplace.compat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane E, 2026-11-01), ROADMAP 39.2 with the owner's Create port: the Mechanic's part is never lost to a player
 * standing by. The part fetched from the store rides in the Mechanic's main hand, the hand vanilla's trade display
 * empties when a player is close. Two cases, the owner beside the Mechanic all the while: the shaft is mended in from
 * the store (one shaft out, one in the hole); and the owner puts the shaft back himself while the Mechanic carries the
 * store's, so the carried one must come back to the store ("the part in hand goes back").
 */
public class QaMechanicOwnerBeside1101CompatTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static int shaftsAround(GameTestHelper helper, Villager mechanic, Item shaft) {
		int n = mechanic.getMainHandItem().is(shaft) ? mechanic.getMainHandItem().getCount() : 0;
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, mechanic.getBoundingBox().inflate(16))) {
			if (drop.getItem().is(shaft)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}

	private static ServerPlayer ownerBeside(GameTestHelper helper, Villager mechanic) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.onEachTick(() -> owner.teleportTo(mechanic.getX() + 1.5, mechanic.getY(), mechanic.getZ()));
		return owner;
	}

	/** The owner beside the Mechanic as he mends a broken shaft: the store's one shaft is in the hole, none lost or made. */
	//$ gametest_ticks_batch AREA '1800' '"qa_mechanic_owner_mend_1101"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa_mechanic_owner_mend_1101")
	public void qaTheOwnerBesideTheMechanicLosesNoShaft(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item shaft = MechanicCompatTests.createItem("shaft");
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		Container chest = MechanicCompatTests.chest(helper, new ItemStack(shaft, 1));
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		helper.setBlock(line.get(1), net.minecraft.world.level.block.Blocks.AIR);
		helper.setDayTime(2000);
		ownerBeside(helper, mechanic);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(line.get(1)).is(MechanicCompatTests.create("shaft")),
				"the shaft isn't back: " + helper.getBlockState(line.get(1)) + "; chest " + MechanicCompatTests.count(chest, shaft)
					+ ", hand " + mechanic.getMainHandItem());
			helper.assertTrue(MechanicCompatTests.count(chest, shaft) == 0, "the shaft in the hole didn't come from the store: "
				+ MechanicCompatTests.count(chest, shaft) + " left");
			helper.assertTrue(shaftsAround(helper, mechanic, shaft) == 0, "a shaft too many: " + shaftsAround(helper, mechanic, shaft)
				+ " in hand or on the ground");
		});
	}

	/**
	 * The owner beside the Mechanic puts the shaft back himself while the Mechanic carries the store's: the carried shaft
	 * goes back to the store (or lies at the toolbox), never lost.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa_mechanic_owner_mended_1101"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa_mechanic_owner_mended_1101")
	public void qaAShaftCarriedWhenTheOwnerMendsTheHoleGoesBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item shaft = MechanicCompatTests.createItem("shaft");
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		var whole = helper.getBlockState(line.get(1));
		Container chest = MechanicCompatTests.chest(helper, new ItemStack(shaft, 1));
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		helper.setBlock(line.get(1), net.minecraft.world.level.block.Blocks.AIR);
		helper.setDayTime(2000);
		ownerBeside(helper, mechanic);
		boolean[] mended = {false};
		int[] after = {0};
		helper.onEachTick(() -> {
			if (!mended[0] && mechanic.getMainHandItem().is(shaft)) {
				mended[0] = true;
				helper.setBlock(line.get(1), whole); // the owner puts his own shaft in
			}
			if (mended[0]) {
				after[0]++;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(mended[0], "the Mechanic never carried the shaft from the store: chest " + MechanicCompatTests.count(chest, shaft));
			helper.assertTrue(after[0] >= 400, "waiting");
			int back = MechanicCompatTests.count(chest, shaft) + shaftsAround(helper, mechanic, shaft);
			helper.assertTrue(back == 1, "the store's shaft was lost after the owner mended the hole himself: " + MechanicCompatTests.count(chest, shaft)
				+ " in the chest, " + shaftsAround(helper, mechanic, shaft) + " in hand or on the ground, " + after[0] + " ticks on");
		});
	}

	/**
	 * The shift ends (the meeting hour) while the Mechanic carries the store's shaft to the hole, the owner beside him all
	 * evening: the one shaft is still somewhere, in the hole, the store, his hand or at the toolbox, never gone.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_mechanic_owner_evening_1101"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_mechanic_owner_evening_1101")
	public void qaAShaftCarriedAsTheShiftEndsOutlastsTheEvening(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item shaft = MechanicCompatTests.createItem("shaft");
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		Container chest = MechanicCompatTests.chest(helper, new ItemStack(shaft, 1));
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		helper.setBlock(line.get(1), net.minecraft.world.level.block.Blocks.AIR);
		helper.setDayTime(2000);
		ownerBeside(helper, mechanic);
		boolean[] evening = {false};
		int[] after = {0};
		helper.onEachTick(() -> {
			if (!evening[0] && mechanic.getMainHandItem().is(shaft)) {
				evening[0] = true;
				helper.setDayTime(9500); // the shift ends with the shaft in his hand
			}
			if (!evening[0]) {
				return;
			}
			after[0]++;
			int all = MechanicCompatTests.count(chest, shaft) + shaftsAround(helper, mechanic, shaft)
				+ (helper.getBlockState(line.get(1)).is(MechanicCompatTests.create("shaft")) ? 1 : 0);
			if (all != 1) {
				helper.fail("the store's one shaft is " + all + " after " + after[0] + " ticks of evening with the owner beside: chest "
					+ MechanicCompatTests.count(chest, shaft) + ", hand " + mechanic.getMainHandItem() + ", hole "
					+ helper.getBlockState(line.get(1)));
			}
			if (after[0] >= 1200) {
				helper.succeed();
			}
		});
	}
}
