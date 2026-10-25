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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane d, 2026-10-24): bugs found in the 38.1a raid helpers, each test named after its bug and kept once fixed.
 */
public class QaDenHelperBugTests implements FabricGameTest {

	/**
	 * B414: with CobbleDollars installed the hire charges {@code denHelperPrice} x 100 CobbleDollars (and emeralds in the
	 * inventory don't count), but the Defence page's button said "5 emeralds a helper". A player holding 5 emeralds
	 * clicked and was told "A raid helper costs 500 CobbleDollars". The button shows the price the click charges.
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
			helper.assertTrue(QaDenHelperCompatTests.emeralds(player) == RaidHelpers.PRICE, "emeralds taken: " + QaDenHelperCompatTests.emeralds(player));
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * B415: a hired helper whose chunk unloads for a moment (the player logs off by the den, or the server restarts and ticks
	 * before that chunk loads) silently ended the hiring: the price was gone, the player wasn't told, and the helper wasn't
	 * sent home either. A hiring ends only on a won or lost raid, the raid gone, a day, or the switch; once loaded again the
	 * helper is still hired and follows the player.
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
			helper.assertTrue(hired && RaidHelpers.helpers(level, hire).contains(back), "the helper loaded back isn't the hiring's: " + hire.helpers);
			BlockPos away = helper.absolutePos(new BlockPos(13, 2, 3));
			player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			RaidHelpers.tick(level, hire);
			helper.assertTrue(back.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent() || back.distanceTo(player) <= 5, "the helper loaded back doesn't follow the player");
			back.discard();
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** B415's neighbour: a day after the hiring, helpers that aren't loaded are still sent home and the player is told. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenHelperBugUnloadDay"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenHelperBugUnloadDay")
	public void aDayLaterUnloadedHelpersAreSentHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.Talker t = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), RaidHelperCompatTests.PRICE);
		ServerPlayer player = t.player();
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			master.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			hire.until = level.getGameTime();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && RaidHelpers.homeward(level, master.getUUID()),
				"a day later with the helper unloaded: hiring " + RaidHelpers.hireOf(level.getServer(), player.getUUID()).isPresent()
					+ ", heading home " + RaidHelpers.homeward(level, master.getUUID()));
			helper.assertTrue(t.said().contains("A day has passed: your raid helpers for the tier 3 Fire raid den head home"), "player told " + t.said());
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** B415's neighbour: a loaded helper that is no longer a trainer leaves the hiring (the one helper gone, the hiring ends). */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenHelperBugNoTrainer"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenHelperBugNoTrainer")
	public void aHelperNoLongerATrainerLeavesTheHiring(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.Talker t = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), RaidHelperCompatTests.PRICE);
		ServerPlayer player = t.player();
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			master.setVillagerData(master.getVillagerData().setProfession(VillagerProfession.NONE));
			RaidHelpers.tick(level, hire);
			helper.assertTrue(hire.helpers.isEmpty() && RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && !RaidHelpers.busy(master),
				"a helper that's no longer a trainer is still hired: " + hire.helpers);
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}
}
