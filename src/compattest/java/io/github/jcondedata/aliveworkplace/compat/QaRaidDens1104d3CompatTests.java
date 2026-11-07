package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-11-04) from the player guide's page docs/guide/raid-dens.md, what it promises, not the code. The other
 * den tests (RaidHelperCompatTests, QaDenHelper*, QaDenSentences*, QaDens1101*, QaDenMega*) prove most sentences; these
 * prove the ones they leave open:
 * <ul>
 * <li>"the best first (a Trainer Leader or a Master before a Novice)": a Trainer Leader is hired before a Novice, cheers,
 * and leads the band in the chronicle's win line;</li>
 * <li>"The button's tooltip says how many you've hired and how many trainers are still free", counted click by click,
 * and as another player sees it;</li>
 * <li>"up to 3 helpers at once, for one den": with two dens on the page, the other den's button counts none and its click
 * is refused and costs nothing;</li>
 * <li>"Raid Helper Price ... (1 to 64, 5 to start)": both ends of the setting, and the emerald price at 1 (read "1
 * emerald") and 64 (one short, then exactly);</li>
 * <li>"a day after you hired them": the day runs from the first hire (a later hire doesn't restart it), one tick before
 * it the helpers stay;</li>
 * <li>"once a raid, never more" over a save and reload mid-raid, and no more hiring once the raid is under way;</li>
 * <li>"Under each active den": a resting den has no button, one glowing again has;</li>
 * <li>"you get one ... Cheer from each of them" with a full inventory: dropped at the player's feet, not lost.</li>
 * </ul>
 * Each test runs alone in its own batch and leaves no hiring behind.
 */
public class QaRaidDens1104d3CompatTests implements FabricGameTest {
	static final long DOLLARS = (long) RaidHelpers.PRICE * 100;
	static final BlockPos DEN2 = new BlockPos(9, 2, 3);

	static List<String> lines(ChoiceMenu menu, int slot) {
		ItemStack icon = menu.icon(slot);
		return icon.isEmpty() ? List.of() : RaidDenCompatTests.lines(icon);
	}

	/** The Defence-page slot of the helpers button under {@code den}. */
	static int slotOf(GameTestHelper helper, BlockPos hall, BlockPos den) {
		List<RaidDens.Den> dens = RaidDens.near(helper.getLevel(), hall);
		for (int i = 0; i < dens.size(); i++) {
			if (dens.get(i).pos().equals(den)) {
				helper.assertTrue(i < DefencePage.HELPERS.length, "the den is listed " + i + "th, past the buttons");
				return DefencePage.HELPERS[i];
			}
		}
		throw new AssertionError("the den isn't listed: " + den);
	}

	static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	/** A Trainer Leader is hired before a Novice, cheers you on, and its band is named in the chronicle's win line. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDens1104d3Leader"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDens1104d3Leader")
	public void aTrainerLeaderIsHiredBeforeANoviceAndNamedInTheWin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager novice = QaDenHelperCompatTests.trainer(helper, 0, 1);
		Villager leader = MegaTrainerCompatTests.villager(helper, new BlockPos(4, 2, 6), ModVillagers.TRAINER_LEADER, 1);
		QaDenHelperCompatTests.Talker t = QaDenHelperCompatTests.talker(helper, at[1].offset(3, 0, 0), DOLLARS);
		ServerPlayer player = t.player();
		try {
			ChoiceMenu menu = QaDenHelperCompatTests.page(player, at[0]);
			helper.assertTrue(QaDenHelperCompatTests.button(menu).contains("Hired: 0 of 3 (2 free in the village)"),
				"a Trainer Leader isn't counted free: " + QaDenHelperCompatTests.button(menu));
			QaDenHelperCompatTests.click(menu, player);
			helper.assertTrue(t.last().matches("Trainer Leader will help you raid the tier 3 Fire raid den \\(.+\\): 1 of 3 hired\\. Lead the way!"),
				"the first hire with a Leader and a Novice free: " + t.said());
			helper.assertTrue(RaidHelpers.busy(leader) && !RaidHelpers.busy(novice), "the Novice was hired before the Trainer Leader");
			t.seen().clear();
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			helper.assertTrue(!t.said().isEmpty() && t.said().get(0).matches("Trainer Leader cheers you on: an? .+ for the raid!")
				&& RaidHelperCompatTests.cheers(player) == 1, "the Leader's cheer: " + t.said());
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at[0]);
			int before = hall.chronicle().size();
			RaidHelperCompatTests.stat(player, "tier_three_completed");
			helper.assertTrue(hall.chronicle().size() == before + 1, "chronicle lines for the win: " + (hall.chronicle().size() - before));
			Chronicle.Entry line = hall.chronicle().get(hall.chronicle().size() - 1);
			String expected = player.getDisplayName().getString() + " and Trainer Leader's band beat the tier 3 Fire raid den";
			helper.assertTrue(line.text().getString().equals(expected), "the win line: " + line.text().getString() + ", expected " + expected);
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** The button counts the player's helpers and the village's free trainers click by click; another player sees their own 0. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDens1104d3Counts"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDens1104d3Counts")
	public void theButtonCountsHiredAndFreeClickByClick(GameTestHelper helper) {
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.trainer(helper, 1, 4);
		QaDenHelperCompatTests.trainer(helper, 2, 3);
		QaDenHelperCompatTests.Talker a = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS * 3);
		QaDenHelperCompatTests.Talker b = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(4, 2, 3)), DOLLARS);
		try {
			for (int n = 0; n <= 3; n++) {
				ChoiceMenu menu = QaDenHelperCompatTests.page(a.player(), at[0]);
				String want = "Hired: " + n + " of 3 (" + (3 - n) + " free in the village)";
				helper.assertTrue(QaDenHelperCompatTests.button(menu).contains(want), "after " + n + " hires, expected '" + want + "': "
					+ QaDenHelperCompatTests.button(menu));
				if (n < 3) {
					QaDenHelperCompatTests.click(menu, a.player());
				}
			}
			ChoiceMenu theirs = QaDenHelperCompatTests.page(b.player(), at[0]);
			helper.assertTrue(QaDenHelperCompatTests.button(theirs).contains("Hired: 0 of 3 (0 free in the village)"),
				"the other player's button: " + QaDenHelperCompatTests.button(theirs));
			QaDenHelperCompatTests.click(theirs, b.player());
			helper.assertTrue(b.last().equals("No trainer of the village is free to help") && CobbleDollarsBank.balance(b.player()) == DOLLARS,
				"the other player's click: " + b.said() + ", left " + CobbleDollarsBank.balance(b.player()));
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], a.player(), b.player());
		}
		helper.succeed();
	}

	/** Two dens on the page: helpers hired at one leave the other's button at 0, and a click there is refused for free. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDens1104d3TwoDens"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDens1104d3TwoDens")
	public void helpersAreForOneDenOnThePage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		helper.setBlock(DEN2, RaidDenCompatTests.den("tier_two", "water", true));
		BlockPos other = helper.absolutePos(DEN2);
		RaidDens.chunkLoaded(level, level.getChunkAt(other));
		RaidDens.tick(level);
		QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.trainer(helper, 1, 4);
		QaDenHelperCompatTests.Talker t = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS * 2);
		ServerPlayer player = t.player();
		try {
			int fire = slotOf(helper, at[0], at[1]);
			int water = slotOf(helper, at[0], other);
			ChoiceMenu menu = QaDenHelperCompatTests.page(player, at[0]);
			menu.clicked(fire, 0, ClickType.PICKUP, player);
			helper.assertTrue(t.last().matches(".+ will help you raid the tier 3 Fire raid den \\(.+\\): 1 of 3 hired\\. Lead the way!"), "the hire: " + t.said());
			menu = QaDenHelperCompatTests.page(player, at[0]);
			helper.assertTrue(lines(menu, fire).contains("Hired: 1 of 3 (1 free in the village)"), "the Fire den's button: " + lines(menu, fire));
			helper.assertTrue(lines(menu, water).contains("Hired: 0 of 3 (1 free in the village)"), "the Water den's button: " + lines(menu, water));
			menu.clicked(water, 0, ClickType.PICKUP, player);
			helper.assertTrue(t.last().equals("Your raid helpers are hired for another den"), "a click at the other den: " + t.said());
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			helper.assertTrue(hire.den.equals(at[1]) && hire.helpers.size() == 1 && CobbleDollarsBank.balance(player) == DOLLARS,
				"after the other den's click: den " + hire.den + ", " + hire.helpers.size() + " helpers, left " + CobbleDollarsBank.balance(player));
		} finally {
			level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState());
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** The price setting holds between 1 and 64; at 1 the player reads "1 emerald", at 64 one short is refused and 64 hires. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDens1104d3Price"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDens1104d3Price")
	public void thePriceHoldsBetween1And64Emeralds(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int[][] cases = {{0, 1}, {-7, 1}, {1, 1}, {5, 5}, {64, 64}, {65, 64}};
		for (int[] c : cases) {
			int got = WorkplaceConfig.parse("{\"denHelperPrice\": " + c[0] + "}").denHelperPrice;
			if (got != c[1]) {
				wrong.add(c[0] + " read as " + got + " (expected " + c[1] + ")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the Raid Helper Price setting: " + wrong);
		helper.assertTrue(new WorkplaceConfig().denHelperPrice == 5, "the price to start: " + new WorkplaceConfig().denHelperPrice);

		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.trainer(helper, 1, 4);
		QaDenHelperCompatTests.Talker cheap = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), 0);
		QaDenHelperCompatTests.Talker dear = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(4, 2, 3)), 0);
		int price = RaidHelpers.PRICE;
		Object bank = QaDenHelperCompatTests.noBank();
		try {
			RaidHelpers.PRICE = 1;
			ChoiceMenu menu = QaDenHelperCompatTests.page(cheap.player(), at[0]);
			helper.assertTrue(QaDenHelperCompatTests.button(menu).contains("1 emerald a helper"), "the price 1 on the page: " + QaDenHelperCompatTests.button(menu));
			QaDenHelperCompatTests.click(menu, cheap.player());
			helper.assertTrue(cheap.last().equals("A raid helper costs 1 emerald"), "with no emerald: " + cheap.said());
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), cheap.player().getUUID()).isEmpty(), "hired for nothing");
			cheap.player().getInventory().setItem(3, new ItemStack(Items.EMERALD, 1));
			QaDenHelperCompatTests.click(menu, cheap.player());
			helper.assertTrue(cheap.last().matches(String.format(QaDenHelperCompatTests.HIRED, 1)) && emeralds(cheap.player()) == 0,
				"with the 1 emerald: " + cheap.said() + ", left " + emeralds(cheap.player()));

			RaidHelpers.PRICE = 64;
			ServerPlayer p = dear.player();
			p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 40));
			p.getInventory().setItem(7, new ItemStack(Items.EMERALD, 23));
			ChoiceMenu dearMenu = QaDenHelperCompatTests.page(p, at[0]);
			helper.assertTrue(QaDenHelperCompatTests.button(dearMenu).contains("64 emeralds a helper"), "the price 64 on the page: " + QaDenHelperCompatTests.button(dearMenu));
			QaDenHelperCompatTests.click(dearMenu, p);
			helper.assertTrue(dear.last().equals("A raid helper costs 64 emeralds") && emeralds(p) == 63
				&& RaidHelpers.hireOf(level.getServer(), p.getUUID()).isEmpty(), "with 63 of 64: " + dear.said() + ", left " + emeralds(p));
			p.getInventory().setItem(20, new ItemStack(Items.EMERALD, 1));
			QaDenHelperCompatTests.click(dearMenu, p);
			helper.assertTrue(dear.last().matches(String.format(QaDenHelperCompatTests.HIRED, 1)) && emeralds(p) == 0,
				"with exactly 64: " + dear.said() + ", left " + emeralds(p));
		} finally {
			RaidHelpers.PRICE = price;
			QaDenHelperCompatTests.bankBack(bank);
			RaidHelperCompatTests.cleanUp(helper, at[1], cheap.player(), dear.player());
		}
		helper.succeed();
	}

	/** The day runs from the first hire: a later hire doesn't restart it; a tick before it they stay, at it they head home. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDens1104d3Day"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDens1104d3Day")
	public void theDayRunsFromTheFirstHire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = QaDenHelperCompatTests.trainer(helper, 0, 5);
		Villager expert = QaDenHelperCompatTests.trainer(helper, 1, 4);
		QaDenHelperCompatTests.Talker t = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS * 2);
		ServerPlayer player = t.player();
		try {
			long first = level.getGameTime();
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			helper.assertTrue(hire.until == first + 24000, "a day after the hire is " + (hire.until - first) + " ticks");
			// the first hire made most of a day ago: 40 ticks of it left
			hire.until = level.getGameTime() + 40;
			long left = hire.until;
			RaidHelpers.hire(level, at[0], at[1], player);
			helper.assertTrue(hire.helpers.size() == 2 && hire.until == left, "a second hire moved the day's end by " + (hire.until - left) + " ticks");
			hire.until = level.getGameTime() + 1;
			t.seen().clear();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isPresent() && !RaidHelpers.homeward(level, master.getUUID()),
				"sent home a tick before the day was up: " + t.said());
			hire.until = level.getGameTime();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(t.said().contains("A day has passed: your raid helpers for the tier 3 Fire raid den head home"), "the day over: " + t.said());
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && RaidHelpers.homeward(level, master.getUUID())
				&& RaidHelpers.homeward(level, expert.getUUID()), "both helpers should head home at the day's end");
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** Cheers once a raid even over a save and reload mid-raid; the day's end and the hiring survive; no more hiring mid-raid. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDens1104d3Reload"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDens1104d3Reload")
	public void cheersOnceARaidOverASaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.trainer(helper, 1, 4);
		Villager spare = QaDenHelperCompatTests.trainer(helper, 2, 3);
		QaDenHelperCompatTests.Talker t = QaDenHelperCompatTests.talker(helper, at[1].offset(3, 0, 0), DOLLARS * 3);
		ServerPlayer player = t.player();
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			helper.assertTrue(RaidHelperCompatTests.cheers(player) == 2, "cheers from two helpers: " + RaidHelperCompatTests.cheers(player));
			long until = RaidHelperCompatTests.hire(helper, player).until;
			CompoundTag saved = RaidHelpers.Data.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set("aliveworkplace_raid_helpers", RaidHelpers.Data.load(saved.copy(), level.registryAccess()));
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			helper.assertTrue(hire.joined && hire.until == until && hire.helpers.size() == 2, "read back: joined " + hire.joined + ", until "
				+ hire.until + " (was " + until + "), " + hire.helpers.size() + " helpers");
			RaidHelpers.tick(level, hire);
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			RaidHelperCompatTests.stat(player, RaidHelpers.HOSTED);
			helper.assertTrue(RaidHelperCompatTests.cheers(player) == 2, "cheers again after the reload: " + RaidHelperCompatTests.cheers(player));
			ChoiceMenu menu = QaDenHelperCompatTests.page(player, at[0]);
			QaDenHelperCompatTests.click(menu, player);
			helper.assertTrue(t.last().equals("Your raid is under way: no more helpers can join it") && !RaidHelpers.busy(spare)
				&& CobbleDollarsBank.balance(player) == DOLLARS, "hiring mid-raid after the reload: " + t.said() + ", left " + CobbleDollarsBank.balance(player));
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** Only an active den has the button: a resting one has none, and glowing again it has. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDens1104d3Resting"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDens1104d3Resting")
	public void aRestingDenHasNoHireButton(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		QaDenHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS);
		try {
			level.setBlockAndUpdate(at[1], RaidDenCompatTests.den("tier_three", "fire", false));
			RaidDens.near(level, at[0]);
			helper.assertFalse(RaidDenCompatTests.listed(helper, at[0], at[1]).orElseThrow().active(), "the den isn't read as resting");
			ChoiceMenu menu = QaDenHelperCompatTests.page(player, at[0]);
			for (int slot : DefencePage.HELPERS) {
				helper.assertFalse(lines(menu, slot).contains("Hire a raid helper"), "a hire button under a resting den: " + lines(menu, slot));
			}
			level.setBlockAndUpdate(at[1], RaidDenCompatTests.den("tier_three", "fire", true));
			RaidDens.near(level, at[0]);
			ChoiceMenu again = QaDenHelperCompatTests.page(player, at[0]);
			int slot = slotOf(helper, at[0], at[1]);
			helper.assertTrue(lines(again, slot).contains("Hire a raid helper"), "no button under the den glowing again: " + lines(again, slot));
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** A full inventory still gets each helper's cheer: dropped at the player's feet, one a helper. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDens1104d3FullBag"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDens1104d3FullBag")
	public void aFullInventoryStillGetsTheCheers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		QaDenHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.trainer(helper, 1, 4);
		ServerPlayer player = RaidHelperCompatTests.player(helper, at[1].offset(3, 0, 0), DOLLARS * 2);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.hire(level, at[0], at[1], player);
			for (int i = 0; i < player.getInventory().items.size(); i++) {
				player.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			int dropped = 0;
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4))) {
				if (BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString().startsWith("cobblemonraiddens:cheer_")) {
					dropped += item.getItem().getCount();
				}
			}
			helper.assertTrue(dropped + RaidHelperCompatTests.cheers(player) == 2, "cheers with a full bag: " + dropped + " dropped, "
				+ RaidHelperCompatTests.cheers(player) + " in the bag (expected 2)");
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4))) {
				item.discard();
			}
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}
}
