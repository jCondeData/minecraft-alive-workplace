package io.github.jcondedata.aliveworkplace.compat;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * QA lane E, 2026-11-03 (saves): ROADMAP 39.2's Mechanic is "restartable at any tick: only the round is saved; a part in
 * hand when the work stops goes back to the store" (MechanicWork's header). {@code MechanicCompatTests} checks the part
 * going back when the work stops in the same session; here the server saves and loads him (a restart, his chunk
 * unloading) after he has taken the cogwheel from the store and before he has put it back: the hole is filled with
 * that one cogwheel, none taken twice, none lost or dropped; and if a player fills the hole while he is unloaded, the
 * cogwheel he carries goes back to the store rather than staying in his hand.
 */
public class QaMechanicPartInHandSaved1103eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** Saved and loaded holding the cogwheel: he puts that one in the hole, and the store gives no second. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1103_mechanic_hand_saved"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1103_mechanic_hand_saved")
	public void aCogwheelInHandThroughASaveGoesIntoTheHoleOnce(GameTestHelper helper) {
		Setup s = setup(helper, false);
		helper.succeedWhen(() -> {
			helper.assertTrue(s.reloaded[0] != null, "the Mechanic never took the cogwheel from the store");
			Item cog = MechanicCompatTests.createItem("cogwheel");
			helper.assertTrue(helper.getBlockState(s.hole).is(MechanicCompatTests.create("cogwheel")),
				"the cogwheel isn't back after the save: " + helper.getBlockState(s.hole) + "; he holds " + s.reloaded[0].getMainHandItem());
			helper.assertTrue(MechanicCompatTests.count(s.store, cog) == 1, "the store holds " + MechanicCompatTests.count(s.store, cog)
				+ " of its 2 cogwheels: more than the one he put back was taken, or it went back as well");
			helper.assertTrue(s.reloaded[0].getMainHandItem().isEmpty(), "he still holds " + s.reloaded[0].getMainHandItem());
			helper.assertTrue(ground(helper, cog) == 0, ground(helper, cog) + " cogwheel(s) lie on the ground");
		});
	}

	/** Saved and loaded holding the cogwheel; a player fills the hole meanwhile: the cogwheel goes back to the store. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1103_mechanic_hand_filled"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1103_mechanic_hand_filled")
	public void aCogwheelInHandThroughASaveGoesBackWhenAPlayerFilledTheHole(GameTestHelper helper) {
		Setup s = setup(helper, true);
		helper.succeedWhen(() -> {
			helper.assertTrue(s.reloaded[0] != null, "the Mechanic never took the cogwheel from the store");
			Item cog = MechanicCompatTests.createItem("cogwheel");
			helper.assertTrue(helper.getBlockState(s.hole).is(MechanicCompatTests.create("cogwheel")),
				"the player's cogwheel isn't standing in the hole: " + helper.getBlockState(s.hole));
			helper.assertTrue(MechanicCompatTests.count(s.store, cog) == 2, "the store holds " + MechanicCompatTests.count(s.store, cog)
				+ " of its 2 cogwheels, the reloaded Mechanic holding " + s.reloaded[0].getMainHandItem() + " and "
				+ ground(helper, cog) + " on the ground");
			helper.assertTrue(s.reloaded[0].getMainHandItem().isEmpty(), "he still holds " + s.reloaded[0].getMainHandItem());
			helper.assertTrue(ground(helper, cog) == 0, ground(helper, cog) + " cogwheel(s) lie on the ground");
		});
	}

	/**
	 * As above, then a shaft of the line breaks too: he fetches a shaft from the store and puts it back, and the cogwheel
	 * he came back from the save holding is not lost on the way (store, hand and ground hold both cogwheels between them).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1103_mechanic_hand_then_shaft"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1103_mechanic_hand_then_shaft")
	public void aCogwheelInHandThroughASaveIsNotLostWhenHeFetchesAShaft(GameTestHelper helper) {
		Setup s = setup(helper, true);
		Item cog = MechanicCompatTests.createItem("cogwheel");
		Item shaft = MechanicCompatTests.createItem("shaft");
		BlockPos shaftAt = s.line().get(1);
		boolean[] broken = new boolean[1];
		helper.onEachTick(() -> {
			if (s.reloaded[0] != null && !broken[0]) {
				broken[0] = true;
				s.store().setItem(5, new ItemStack(shaft));
				helper.setBlock(shaftAt, net.minecraft.world.level.block.Blocks.AIR);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the Mechanic never took the cogwheel from the store");
			Villager m = s.reloaded[0];
			helper.assertTrue(helper.getBlockState(shaftAt).is(MechanicCompatTests.create("shaft")), "the shaft isn't back: " + helper.getBlockState(shaftAt));
			int cogs = MechanicCompatTests.count(s.store, cog) + (m.getMainHandItem().is(cog) ? m.getMainHandItem().getCount() : 0) + ground(helper, cog);
			helper.assertTrue(cogs == 2, "of the 2 cogwheels only " + cogs + " are left (store " + MechanicCompatTests.count(s.store, cog)
				+ ", his hand " + m.getMainHandItem() + ", the ground " + ground(helper, cog) + ")");
			helper.assertTrue(MechanicCompatTests.count(s.store, shaft) == 0 && !m.getMainHandItem().is(shaft), "the shaft didn't come from the store");
		});
	}

	private record Setup(BlockPos hole, Container store, Villager[] reloaded, List<BlockPos> line) {
	}

	/**
	 * The test drive line with its cogwheel gone, a store of 2 cogwheels and a Mechanic; the tick he holds the cogwheel
	 * he took, he is saved and loaded in his place (and with {@code playerFills}, a player sets a cogwheel in the hole).
	 */
	private static Setup setup(GameTestHelper helper, boolean playerFills) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		Container store = MechanicCompatTests.chest(helper, new ItemStack(MechanicCompatTests.createItem("cogwheel"), 2));
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		BlockPos hole = line.get(3);
		helper.setBlock(hole, net.minecraft.world.level.block.Blocks.AIR);
		helper.setDayTime(2000);
		Villager[] reloaded = new Villager[1];
		helper.onEachTick(() -> {
			if (reloaded[0] != null || !mechanic.getMainHandItem().is(MechanicCompatTests.createItem("cogwheel"))) {
				return;
			}
			helper.assertTrue(MechanicCompatTests.count(store, MechanicCompatTests.createItem("cogwheel")) == 1,
				"setup: the store doesn't hold the 1 cogwheel left after he took one");
			CompoundTag tag = new CompoundTag();
			if (!mechanic.save(tag)) {
				throw new GameTestAssertException("the Mechanic wasn't saved");
			}
			mechanic.discard();
			if (playerFills) {
				helper.setBlock(hole, MechanicCompatTests.with(MechanicCompatTests.create("cogwheel").defaultBlockState(), "axis", "x"));
			}
			Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
			if (!(back instanceof Villager villager)) {
				throw new GameTestAssertException("the Mechanic didn't load as a villager: " + back);
			}
			level.addFreshEntity(villager);
			reloaded[0] = villager;
		});
		return new Setup(hole, store, reloaded, line);
	}

	private static int ground(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
			if (drop.getItem().is(item)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}
}
