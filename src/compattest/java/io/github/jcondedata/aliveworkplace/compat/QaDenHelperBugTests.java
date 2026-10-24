package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane d, 2026-10-24): failing tests for bugs found in the 38.1a raid helpers. Not on main until fixed; each is
 * named after the bug.
 */
public class QaDenHelperBugTests implements FabricGameTest {

	/**
	 * With CobbleDollars installed the hire charges {@code denHelperPrice} x 100 CobbleDollars (and emeralds in the
	 * inventory don't count), but the Defence page's button says "5 emeralds a helper". A player holding 5 emeralds clicks
	 * and is told "A raid helper costs 500 CobbleDollars". The button should show the price the click charges.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenHelperBugPrice"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenHelperBugPrice")
	public void theButtonsPriceMatchesWhatTheClickCharges(GameTestHelper helper) {
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.Talker t = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), 0);
		ServerPlayer player = t.player();
		try {
			player.getInventory().setItem(0, new ItemStack(Items.EMERALD, RaidHelpers.PRICE));
			ChoiceMenu menu = QaDenHelperCompatTests.page(player, at[0]);
			List<String> button = QaDenHelperCompatTests.button(menu);
			QaDenHelperCompatTests.click(menu, player);
			String charged = t.last();
			long dollars = (long) RaidHelpers.PRICE * 100;
			helper.assertTrue(charged.equals("A raid helper costs " + dollars + " CobbleDollars") && CobbleDollarsBank.balance(player) == 0,
				"the click (with " + RaidHelpers.PRICE + " emeralds, no CobbleDollars): " + charged);
			helper.assertTrue(button.stream().anyMatch(l -> l.startsWith(dollars + " CobbleDollars")),
				"the button says " + button + " but the click charges " + dollars + " CobbleDollars");
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * A hired helper whose chunk unloads for a moment (the player logs off by the den, or the server restarts and ticks
	 * before that chunk loads) silently ends the hiring: the price is gone, the player isn't told, and the helper isn't
	 * sent home either, so when it loads again it's neither following nor walking home. The spec ends a hiring only on a
	 * won or lost raid, the raid gone, a day, or the switch; the helper should still be hired (or at least walk home).
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenHelperBugUnload"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenHelperBugUnload")
	public void aHelperUnloadedForAMomentIsStillHired(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.Talker t = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), RaidHelperCompatTests.PRICE);
		ServerPlayer player = t.player();
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			CompoundTag saved = new CompoundTag();
			master.save(saved);
			master.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			RaidHelpers.tick(level, hire); // one look while the helper's chunk isn't loaded
			Villager back = (Villager) EntityType.loadEntityRecursive(saved, level, e -> e);
			helper.assertTrue(back != null && back.getUUID().equals(master.getUUID()) && level.addFreshEntity(back), "couldn't load the helper back");
			boolean hired = RaidHelpers.hireOf(level.getServer(), player.getUUID()).isPresent();
			helper.assertTrue(hired || RaidHelpers.homeward(level, back.getUUID()),
				"after one look with the helper unloaded: hiring gone, helper not heading home (busy " + RaidHelpers.busy(back) + "), player told " + t.said());
			back.discard();
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}
}
