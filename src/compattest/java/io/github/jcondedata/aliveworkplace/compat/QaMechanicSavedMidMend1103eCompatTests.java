package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
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
 * QA lane E, 2026-11-03 (saves): ROADMAP 39.2's Mechanic mending a finished build's drive line is "restartable at any
 * tick" (MechanicWork's header: only the round is saved). The server saves and loads him (a restart, his chunk
 * unloading) in the middle of a mend: on his way to the store before he has taken the cogwheel, and twice in a row three
 * ticks apart while he carries it. Each time the hole gets exactly one cogwheel from the store of two, nothing is left
 * in his hand and nothing on the ground. (Loaded holding a part the hole no longer needs is B884.)
 */
public class QaMechanicSavedMidMend1103eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** Saved and loaded on his way to the store, the cogwheel not yet taken: he takes one, not two. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1103_mechanic_saved_fetching"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1103_mechanic_saved_fetching")
	public void aMechanicSavedOnHisWayToTheStoreTakesOneCogwheel(GameTestHelper helper) {
		mend(helper, false);
	}

	/** Saved and loaded twice, three ticks apart, while he carries the cogwheel: it goes in once. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1103_mechanic_saved_twice"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1103_mechanic_saved_twice")
	public void aMechanicSavedTwiceCarryingTheCogwheelPutsItInOnce(GameTestHelper helper) {
		mend(helper, true);
	}

	private static void mend(GameTestHelper helper, boolean carrying) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Item cog = MechanicCompatTests.createItem("cogwheel");
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		Container store = MechanicCompatTests.chest(helper, new ItemStack(cog, 2));
		Villager[] mechanic = {MechanicCompatTests.mechanic(helper)};
		BlockPos far = helper.absolutePos(new BlockPos(7, 2, 1)); // a walk from the store, so he is seen on his way to it
		mechanic[0].teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		BlockPos hole = line.get(3);
		helper.setBlock(hole, net.minecraft.world.level.block.Blocks.AIR);
		helper.setDayTime(2000);
		long[] firstAt = {-1};
		int[] reloads = {0};
		int wanted = carrying ? 2 : 1;
		helper.onEachTick(() -> {
			if (reloads[0] >= wanted) {
				return;
			}
			boolean due;
			if (reloads[0] == 0) {
				due = carrying ? mechanic[0].getMainHandItem().is(cog)
					: mechanic[0].getMainHandItem().isEmpty() && said(helper, mechanic[0]).startsWith("Mechanic: fetching");
			} else {
				due = level.getGameTime() >= firstAt[0] + 3;
			}
			if (!due) {
				return;
			}
			if (reloads[0] == 0) {
				firstAt[0] = level.getGameTime();
				int left = MechanicCompatTests.count(store, cog);
				if (left != (carrying ? 1 : 2)) {
					throw new GameTestAssertException("setup: at the first save the store holds " + left + " cogwheels");
				}
			}
			mechanic[0] = reload(level, mechanic[0]);
			reloads[0]++;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloads[0] == wanted, "saved and loaded " + reloads[0] + " of " + wanted + " times; he says " + said(helper, mechanic[0]));
			helper.assertTrue(helper.getBlockState(hole).is(MechanicCompatTests.create("cogwheel")),
				"the cogwheel isn't back: " + helper.getBlockState(hole) + "; he holds " + mechanic[0].getMainHandItem() + " and says " + said(helper, mechanic[0]));
			helper.assertTrue(MechanicCompatTests.count(store, cog) == 1, "the store holds " + MechanicCompatTests.count(store, cog) + " of its 2 cogwheels");
			helper.assertTrue(mechanic[0].getMainHandItem().isEmpty(), "he still holds " + mechanic[0].getMainHandItem());
			int ground = 0;
			for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
				ground += drop.getItem().is(cog) ? drop.getItem().getCount() : 0;
			}
			helper.assertTrue(ground == 0, ground + " cogwheel(s) lie on the ground");
			helper.assertTrue(level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> v.isAlive()).size() == 1,
				"not one Mechanic after the loads");
		});
	}

	/** The villager saved and loaded in his place, as when the server restarts or his chunk unloads. */
	private static Villager reload(ServerLevel level, Villager old) {
		CompoundTag tag = new CompoundTag();
		if (!old.save(tag)) {
			throw new GameTestAssertException("the Mechanic wasn't saved");
		}
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		if (!(back instanceof Villager villager)) {
			throw new GameTestAssertException("the Mechanic didn't load as a villager: " + back);
		}
		level.addFreshEntity(villager);
		return villager;
	}

	private static String said(GameTestHelper helper, Villager villager) {
		WorkerStatus.Entry e = WorkerStatus.get(villager, helper.getLevel().getGameTime());
		return e == null ? "" : e.title().getString() + ": " + e.line().getString();
	}
}
