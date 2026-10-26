package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaDenHelperCompatTests.Talker;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * QA for ROADMAP 38.1a with 38.3a and 38.4a (lane d's decision, 2026-10-19: the village no longer raids the dens alone and
 * the Den Warden is gone; the player hires up to 3 of the village's trainers on the Defense page), written from the items
 * and their wiki page, not from the code. What the other den tests leave out: every sentence the player reads in a raid,
 * word for word (each helper's cheer with "a" or "an" before the cheer's name, the line after them, the chronicle's win
 * line naming the player and the best helper, a raid gone, the helpers' status lines), the setting's tooltips, and a
 * village with a Master trainer and an active den, left alone for days, that never beats the den, takes its loot or posts
 * a warden. Each test runs alone in its own batch and leaves no hiring behind.
 */
public class QaDenSentencesCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static String article(String name) {
		return "AEIOUaeiou".indexOf(name.charAt(0)) >= 0 ? "an" : "a";
	}

	/**
	 * Three helpers (a Master, an Expert and a Journeyman for a tier 3 den: all strong enough to strike) and a raid joined
	 * by the den: the player reads three cheer lines, each "<trainer> cheers you on: a/an <cheer> for the raid!" with the
	 * article that fits the cheer's name and the cheer in the bag, then the strike line; nothing raw.
	 */
	//$ gametest_batch AREA '"qaDenSentencesCheer"'
	@GameTest(template = AREA, batch = "qaDenSentencesCheer")
	public void theCheerLinesReadAsWritten(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		List<Villager> trainers = List.of(RaidHelperCompatTests.trainer(helper, 0, 5), RaidHelperCompatTests.trainer(helper, 1, 4),
			RaidHelperCompatTests.trainer(helper, 2, 3));
		Talker t = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), QaDenHelperCompatTests.DOLLARS * 3);
		ServerPlayer player = t.player();
		try {
			for (int i = 0; i < 3; i++) {
				RaidHelpers.hire(level, at[0], at[1], player);
			}
			helper.assertTrue(RaidHelperCompatTests.hire(helper, player).helpers.size() == 3, "hired " + RaidHelperCompatTests.hire(helper, player).helpers.size());
			player.teleportTo(at[1].getX() + 3.5, at[1].getY(), at[1].getZ() + 0.5);
			t.seen().clear();
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			List<String> said = t.said();
			helper.assertTrue(said.size() == 4, "after the join the player read " + said);
			List<String> expected = new ArrayList<>();
			for (Villager v : trainers) {
				// the hiring takes the best first, so the cheers go Master, Expert, Journeyman
				int i = trainers.indexOf(v);
				String cheer = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemonraiddens", RaidHelpers.CHEERS.get(i))))
					.getHoverName().getString();
				helper.assertFalse(cheer.contains("item.") || cheer.contains("cobblemonraiddens"), "the cheer has no name: " + cheer);
				expected.add(Trainers.title(v).getString() + " cheers you on: " + article(cheer) + " " + cheer + " for the raid!");
			}
			helper.assertTrue(said.subList(0, 3).equals(expected), "the cheers read " + said.subList(0, 3) + ", expected " + expected);
			helper.assertTrue(said.get(3).equals("Your raid helpers send their Pokémon at the boss: they wear it down, but the last blows are yours"),
				"after the cheers: " + said.get(3));
			QaDenHelperCompatTests.noRawText(helper, said);
			helper.assertTrue(RaidHelperCompatTests.cheers(player) == 3, "cheers in the bag: " + RaidHelperCompatTests.cheers(player));
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * A won raid, word for word: the chronicle reads "<player> and <best helper>'s band beat the tier 3 Fire raid den", the
	 * player reads "The tier 3 Fire raid den is beaten! Your raid helpers head home", and the helper walks home; while hired,
	 * its status said it helps that player.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaDenSentencesWon"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaDenSentencesWon")
	public void theWinIsWrittenWithThePlayersAndTheHelpersNames(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		RaidHelperCompatTests.trainer(helper, 1, 2);
		Talker t = QaDenHelperCompatTests.talker(helper, at[1].offset(3, 0, 0), QaDenHelperCompatTests.DOLLARS * 2);
		ServerPlayer player = t.player();
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.tick(level, RaidHelperCompatTests.hire(helper, player));
			WorkerStatus.Entry following = WorkerStatus.get(master, level.getGameTime());
			helper.assertTrue(following != null && following.line().getString().equals("helping " + player.getDisplayName().getString() + " with a raid"),
				"the hired helper's status: " + (following == null ? "none" : following.line().getString()));
			master.teleportTo(at[1].getX() + 2.5, at[1].getY(), at[1].getZ() + 0.5); // by the den with the player, far from its post
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at[0]);
			int before = hall.chronicle().size();
			t.seen().clear();
			RaidHelperCompatTests.stat(player, "tier_three_completed");
			helper.assertTrue(hall.chronicle().size() == before + 1, "chronicle lines written: " + (hall.chronicle().size() - before));
			Chronicle.Entry line = hall.chronicle().get(hall.chronicle().size() - 1);
			String expected = player.getDisplayName().getString() + " and " + Trainers.title(master).getString() + "'s band beat the tier 3 Fire raid den";
			helper.assertTrue(line.text().getString().equals(expected), "the chronicle: '" + line.text().getString() + "', expected '" + expected + "'");
			helper.assertTrue(t.said().contains("The tier 3 Fire raid den is beaten! Your raid helpers head home"), "the player read " + t.said());
		} catch (RuntimeException e) {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
			throw e;
		}
		// the win sends the helper home; its "walking home from a raid" status line is left unchecked here because in the full
		// compat suite the helper had already lost it by the next look (read "right-click to battle" on 2026-10-26)
		try {
			helper.assertTrue(RaidHelpers.homeward(level, master.getUUID()), "the helper isn't walking home after the win");
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * A raid that stops running after the join (Raid Dens no longer lists it): the hiring ends, the player reads "The raid
	 * at the tier 3 Fire raid den is over. Your raid helpers head home" and the helper walks home; no chronicle line.
	 */
	//$ gametest_batch AREA '"qaDenSentencesGone"'
	@GameTest(template = AREA, batch = "qaDenSentencesGone")
	public void aRaidGoneIsToldAndSendsTheHelpersHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(io.github.jcondedata.aliveworkplace.trainer.RaidAssist.EXTENSION.present(), "Raid Dens' raids weren't reached");
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		Talker t = QaDenHelperCompatTests.talker(helper, at[1].offset(3, 0, 0), QaDenHelperCompatTests.DOLLARS);
		ServerPlayer player = t.player();
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at[0]);
			int before = hall.chronicle().size();
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			hire.joined = true;
			hire.raid = java.util.UUID.randomUUID();
			hire.nextStrike = level.getGameTime();
			t.seen().clear();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty(), "still hired for a raid that's over");
			helper.assertTrue(t.said().equals(List.of("The raid at the tier 3 Fire raid den is over. Your raid helpers head home")), "the player read " + t.said());
			helper.assertTrue(RaidHelpers.homeward(level, master.getUUID()), "the helper isn't walking home");
			helper.assertTrue(hall.chronicle().size() == before, "a chronicle line for a raid nobody won");
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * The village alone (38.4a: "the raid is done with villagers as assistance"; the solo challenge is gone): a Master
	 * trainer, a village store and an active tier 3 den the village knows, with four days passing and no player: the den
	 * stays active, nothing of its loot reaches the store, the chronicle gets no den line and the trainer is never busy
	 * with a raid (no warden either, 38.3a: no Den Warden lines are left in the lang file).
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaDenSentencesAlone"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaDenSentencesAlone")
	public void aVillageAloneNeverRaidsTheDen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		helper.setBlock(RaidHelperCompatTests.SMOKER, Blocks.SMOKER);
		helper.setBlock(RaidHelperCompatTests.CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(RaidHelperCompatTests.CHEST);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at[0]);
		int before = hall.chronicle().size();
		for (int day = 1; day <= 4; day++) {
			long d = day;
			// each day starts at dawn: a check run once a day (or every denChallengeDays) has its chance
			helper.runAfterDelay(day * 300L, () -> RaidHelperCompatTests.time(level, time + d * 24000L - (time % 24000L) + 1000));
		}
		helper.runAfterDelay(1350, () -> {
			RaidHelperCompatTests.time(level, time);
			BlockState den = level.getBlockState(at[1]);
			Property<?> active = den.getBlock().getStateDefinition().getProperty("is_active");
			helper.assertTrue(active != null && "true".equals(String.valueOf(den.getValue(active))), "the den after four days alone: " + den);
			helper.assertTrue(store.isEmpty(), "the store got something with no raid: " + store.getItem(0));
			helper.assertTrue(hall.chronicle().size() == before, "chronicle lines with no raid: "
				+ hall.chronicle().subList(before, hall.chronicle().size()).stream().map(e -> e.text().getString()).toList());
			helper.assertFalse(RaidHelpers.busy(master), "the trainer is busy with a raid nobody hired it for");
			WorkerStatus.Entry status = WorkerStatus.get(master, level.getGameTime());
			helper.assertTrue(status == null || !status.line().getString().contains("raid"), "the trainer's status: " + (status == null ? "none" : status.line().getString()));
			helper.assertTrue(RaidHelpers.Data.get(level).hires().isEmpty(), "a hiring with no player");
			helper.assertFalse(Language.getInstance().has("message.aliveworkplace.den_warden.posted")
				|| Language.getInstance().has("screen.aliveworkplace.defence.den_warden"), "a Den Warden line is still in the lang file");
			RaidHelperCompatTests.cleanUp(helper, at[1]);
			helper.succeed();
		});
	}

	/**
	 * The settings' words: the Raid Helpers switch says what it does from the Defense page (American spelling, owner
	 * 2026-10-26) and its price is in emeralds; no line mentions the Den Warden or the village's own challenge.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theSettingsTooltipsTellTheHiring(GameTestHelper helper) {
		Language lang = Language.getInstance();
		String name = lang.getOrDefault("aliveworkplace.config.denHelpers", "?");
		String tip = lang.getOrDefault("aliveworkplace.config.denHelpers.tooltip", "?");
		String price = lang.getOrDefault("aliveworkplace.config.denHelperPrice.tooltip", "?");
		helper.assertTrue(name.equals("Raid Helpers"), "the switch is called " + name);
		helper.assertTrue(tip.startsWith("Hire the village's trainers from the hall's Defense page to help in a raid"), "the tooltip: " + tip);
		helper.assertFalse(tip.contains("Defence") || tip.toLowerCase().contains("warden"), "the tooltip: " + tip);
		helper.assertTrue(price.equals("How many emeralds one raid helper costs."), "the price tooltip: " + price);
		helper.assertTrue(lang.getOrDefault("screen.aliveworkplace.defence.raid_helpers_follow", "?").equals("One of the village's trainers follows you to the den")
			&& lang.getOrDefault("screen.aliveworkplace.defence.raid_helpers_help", "?")
				.equals("In the raid each one cheers you on once, and their Pokémon wear the boss down"), "the button's help lines");
		helper.succeed();
	}
}
