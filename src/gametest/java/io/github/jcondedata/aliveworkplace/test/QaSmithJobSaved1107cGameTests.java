package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.DROP;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.POST_CHEST;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.STOCK;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.chest;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.damageIn;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.guard;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.onGround;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.weaponsmith;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.worn;

import io.github.jcondedata.aliveworkplace.mend.MendingWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1107-1252) for B971's fix, from its words and MendJob's promise: "the piece still goes back where it was
 * found after a chunk unload or a restart", "a gone material only means the piece goes back unmended", and nothing a
 * player gave the smith is lost. B971's own tests reload the smith with the guard's helmet before and after mending;
 * these take the corners: the material's mod removed between saves, the guard's chest gone while the smith is away, and a
 * finished job leaving nothing behind that keeps the smith busy after a reload.
 */
public class QaSmithJobSaved1107cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A Weaponsmith, three iron in his stock chest, and a guard whose chest holds a worn iron helmet (120 of 165). */
	private static Container[] setUp(GameTestHelper helper, Villager[] smith) {
		Leftovers.village(helper, 48);
		smith[0] = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
		Villager guard = guard(helper, new BlockPos(18, 2, 18));
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		Container guards = chest(helper, POST_CHEST);
		guards.setItem(0, worn(Items.IRON_HELMET, 120));
		return new Container[] {stock, chest(helper, DROP), guards};
	}

	/** Saves {@code v} and loads it as a new villager, with {@code from} replaced by {@code to} in its saved text (SNBT). */
	private static Villager reloadEdited(GameTestHelper helper, Villager v, String from, String to) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		String text = tag.toString();
		helper.assertTrue(text.contains(from), "setup: the saved smith has no " + from + " in " + text.substring(0, Math.min(400, text.length())));
		try {
			tag = TagParser.parseTag(text.replace(from, to));
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("setup: the edited save doesn't parse: " + e.getMessage());
		}
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(copy != null, "a villager to load into");
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		Leftovers.after(helper, copy::discard);
		return copy;
	}

	/**
	 * The smith has fetched the guard's helmet and is on his way to the iron; he is saved, and the iron's mod is gone when
	 * he's loaded (his saved job names a material that no longer exists, so there is nothing to fetch). The helmet still goes back to the guard's chest, unmended, and
	 * the job is let go.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaSmithGoneMaterial1107c"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaSmithGoneMaterial1107c")
	public void aSavedJobWhoseMaterialsModIsGoneReturnsThePieceUnmended(GameTestHelper helper) {
		Villager[] smith = new Villager[1];
		Container[] c = setUp(helper, smith);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			var job = ModAttachments.MEND_JOB.get(smith[0]);
			if (!reloaded[0] && job != null && job.phase() == io.github.jcondedata.aliveworkplace.mend.MendJob.Phase.MATERIAL) {
				helper.assertTrue(bag(smith[0]).count(Items.IRON_HELMET) == 1, "setup: on his way to the iron without the helmet: " + bag(smith[0]).stacks());
				smith[0] = reloadEdited(helper, smith[0], "material:\"minecraft:iron_ingot\"", "material:\"qagonemod:mithril\"");
				reloaded[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the smith never set off for the iron with the helmet");
			int damage = damageIn(c[2], Items.IRON_HELMET);
			helper.assertTrue(damage == 120, "the guard's helmet after the material's mod went: damage in the guard's chest " + damage
				+ " (-1: not there; expected back unmended, 120); stock " + damageIn(c[0], Items.IRON_HELMET) + ", drop-off "
				+ damageIn(c[1], Items.IRON_HELMET) + ", ground " + onGround(helper, Items.IRON_HELMET) + "; the smith carries " + bag(smith[0]).stacks());
			helper.assertTrue(!ModAttachments.MEND_JOB.has(smith[0]) && !MendingWork.isBusy(smith[0]),
				"the helmet is back but the smith still holds the job " + ModAttachments.MEND_JOB.get(smith[0]));
		});
	}

	/**
	 * The smith carries the mended helmet back; the guard's chest is broken meanwhile, and the smith is saved and reloaded.
	 * With nowhere to put it, the helmet is not lost: within the test it is in his own chests or on the ground (where a
	 * player can get it), not kept in his bag, and he is free for the next job.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaSmithGuardChestGone1107c"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaSmithGuardChestGone1107c")
	public void theGuardsChestGoneWhileTheSmithCarriesTheHelmetLosesNothing(GameTestHelper helper) {
		Villager[] smith = new Villager[1];
		Container[] c = setUp(helper, smith);
		long[] reloadedAt = {-1};
		helper.onEachTick(() -> {
			if (reloadedAt[0] < 0 && bag(smith[0]).stacks().stream().anyMatch(s -> s.is(Items.IRON_HELMET) && s.getDamageValue() == 0)) {
				helper.destroyBlock(POST_CHEST);
				helper.assertTrue(onGround(helper, Items.IRON_HELMET) == 0, "setup: the guard's chest dropped a helmet");
				smith[0] = QaSmithScribe1105cGameTests.reload(helper, smith[0]);
				reloadedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt[0] >= 0, "the smith never mended the helmet");
			int reachable = c[0].countItem(Items.IRON_HELMET) + c[1].countItem(Items.IRON_HELMET) + onGround(helper, Items.IRON_HELMET);
			helper.assertTrue(reachable == 1 && bag(smith[0]).count(Items.IRON_HELMET) == 0, "the guard's chest gone, the mended helmet: "
				+ reachable + " where a player can get it (the smith's chests or the ground), the smith carries " + bag(smith[0]).stacks());
			helper.assertTrue(!MendingWork.isBusy(smith[0]), "the smith is still busy with " + ModAttachments.MEND_JOB.get(smith[0]));
		});
	}

	/**
	 * A whole job, start to end: the helmet back in the guard's chest, mended. No job is left saved on the smith, and a
	 * reload after it doesn't bring one back (a stale saved job would keep him "busy" and out of other work for good).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaSmithJobDone1107c"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaSmithJobDone1107c")
	public void aFinishedJobLeavesNoSavedJobBehindThroughAReload(GameTestHelper helper) {
		Villager[] smith = new Villager[1];
		Container[] c = setUp(helper, smith);
		boolean[] reloaded = {false};
		helper.succeedWhen(() -> {
			int damage = damageIn(c[2], Items.IRON_HELMET);
			helper.assertTrue(damage == 0, "the helmet isn't back mended yet: damage " + damage);
			if (!reloaded[0]) {
				smith[0] = QaSmithScribe1105cGameTests.reload(helper, smith[0]);
				reloaded[0] = true;
			}
			helper.assertTrue(!ModAttachments.MEND_JOB.has(smith[0]), "a finished job is still saved on the smith: "
				+ ModAttachments.MEND_JOB.get(smith[0]));
			helper.assertTrue(!MendingWork.isBusy(smith[0]), "the smith is still busy after his job was done and he was reloaded");
		});
	}
}
