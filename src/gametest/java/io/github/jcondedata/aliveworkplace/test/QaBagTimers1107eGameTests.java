package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.CrafterWork;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.mend.MendJob;
import io.github.jcondedata.aliveworkplace.mend.MendingWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-e-1107-2212): the two waits that decide when a villager's saved bag outlives his workstation, each checked on
 * both sides of its boundary, as a jobless villager loaded with the job on him (the old-save and reload case of
 * B1008/B1018). B970: a smith's mending job is dropped at his feet after 100 ticks (5 s) without a grindstone, not one
 * tick sooner or later. B353: a crafting job is let go after 600 ticks (30 s) without a bench. The nightly mutation run
 * (2026-11-07) found MendingWork's {@code now - since < STATIONLESS_TICKS} changed to {@code <=} undetected.
 * The villagers have no AI, so the timers are the only thing that touches the bag; each is ticked once per game tick.
 */
public class QaBagTimers1107eGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	private static Villager jobless(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		Leftovers.after(helper, v::discard);
		return v;
	}

	private static int onGround(GameTestHelper helper, Item item) {
		AABB box = helper.getBounds().inflate(2);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	/** B970: a jobless ex-smith with a saved mending job and the helmet in his bag drops it at exactly 100 ticks. */
	//$ gametest_ticks_batch AREA '400' '"qa1107eMendTimer"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1107eMendTimer")
	public void qaAnExSmithsMendingBagIsDroppedAtFiveSecondsNotATickSooner(GameTestHelper helper) {
		Villager v = jobless(helper);
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		helmet.setDamageValue(120);
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(helmet.copy());
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.IRON_INGOT, 3));
		ModAttachments.MEND_JOB.set(v, new MendJob(helmet, helper.absolutePos(new BlockPos(1, 2, 1)), Items.IRON_INGOT, 3,
			helper.absolutePos(new BlockPos(1, 2, 7)), MendJob.Phase.MENDING));
		run(helper, v, () -> MendingWork.stationlessTick(v), 100, "the ex-smith's mending bag", () -> {
			helper.assertTrue(onGround(helper, Items.IRON_HELMET) == 1 && onGround(helper, Items.IRON_INGOT) == 3,
				"the helmet and 3 iron should lie at his feet: helmets " + onGround(helper, Items.IRON_HELMET) + ", iron "
					+ onGround(helper, Items.IRON_INGOT));
			helper.assertTrue(!ModAttachments.MEND_JOB.has(v), "the mending job is still saved on him after the drop");
		});
	}

	/** B353: a jobless ex-carpenter with a saved crafting job and logs in his bag lets it go at exactly 600 ticks. */
	//$ gametest_ticks_batch AREA '1000' '"qa1107eCraftTimer"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qa1107eCraftTimer")
	public void qaAnExCarpentersCraftingJobIsLetGoAtThirtySecondsNotATickSooner(GameTestHelper helper) {
		Villager v = jobless(helper);
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.OAK_LOG, 6));
		ModAttachments.CRAFTER_JOB.set(v, new CrafterJob(helper.absolutePos(new BlockPos(1, 2, 1)), Optional.empty(), Items.BARREL, 3,
			List.of(new Crafting.Step(Map.of(Items.OAK_LOG, 1), new ItemStack(Items.OAK_PLANKS, 4), 6)),
			Map.of(Items.OAK_LOG, 6), List.of(), "qa1107e:barrel", CrafterJob.Phase.FETCHING, 0, "aliveworkplace:carpenter"));
		run(helper, v, () -> CrafterWork.benchlessTick(v), 600, "the ex-carpenter's crafting bag", () -> {
			helper.assertTrue(onGround(helper, Items.OAK_LOG) == 6, "the 6 logs should lie at his feet (no chest to take them to): "
				+ onGround(helper, Items.OAK_LOG));
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(v) == null, "the crafting job is still saved on him after it was let go");
		});
	}

	private static void run(GameTestHelper helper, Villager v, Runnable timer, long wait, String what, Runnable after) {
		long[] firstLook = {-1};
		long[] lastHeld = {-1};
		long[] emptiedAt = {-1};
		helper.onEachTick(() -> {
			if (emptiedAt[0] >= 0) {
				return;
			}
			long now = helper.getLevel().getGameTime();
			if (firstLook[0] < 0) {
				firstLook[0] = now;
			}
			timer.run();
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(v);
			if (bag == null || bag.isEmpty()) {
				emptiedAt[0] = now - firstLook[0];
			} else {
				lastHeld[0] = now - firstLook[0];
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(emptiedAt[0] >= 0, what + " is still full " + lastHeld[0] + " ticks after the first look");
			helper.assertTrue(lastHeld[0] == wait - 1 && emptiedAt[0] == wait, what + " should be held through tick " + (wait - 1)
				+ " and emptied at tick " + wait + " after the first look; it was last held at " + lastHeld[0] + " and emptied at "
				+ emptiedAt[0]);
			after.run();
		});
	}
}
