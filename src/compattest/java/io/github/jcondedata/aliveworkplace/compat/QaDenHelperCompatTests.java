package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trainer.RaidAssist;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane d, 2026-10-24) for ROADMAP 38.1/38.1a, 38.3/38.3a and 38.4/38.4a as built: hired raid helpers. Written from the
 * spec (ROADMAP Notes 2026-10-19 and docs/design/M38.md "As built in 38.1a"), through the player's own entry point where
 * there is one: the Defence page's "Hire a raid helper" button clicked as a client's click arrives, with a real player
 * whose chat is read back. Covers the price boundary in CobbleDollars and in real emeralds (with a non-default
 * {@code denHelperPrice}), a village with no trainers, a stale screen clicked after the den went to rest, a save and
 * reload mid-hire, the boss's quarter floor (and no healing below it), the rank-under-tier rule on both sides of every
 * boundary, cheers once a raid with the Attack/Defense/Heal cycle, the walk home, and the switch turned off mid-hire.
 * Each test runs alone in its own batch and leaves no hiring behind.
 */
public class QaDenHelperCompatTests implements FabricGameTest {
	static final BlockPos[] POSTS = {new BlockPos(2, 2, 8), new BlockPos(4, 2, 8), new BlockPos(6, 2, 8), new BlockPos(8, 2, 8),
		new BlockPos(10, 2, 8)};
	static final long DOLLARS = (long) RaidHelpers.PRICE * 100;

	/** A player whose chat (system messages; client messages arrive through them) is read back, in survival. */
	record Talker(ServerPlayer player, List<Component> seen) {
		List<String> said() {
			return seen.stream().map(Component::getString).toList();
		}

		String last() {
			return seen.isEmpty() ? "(nothing)" : seen.get(seen.size() - 1).getString();
		}
	}

	static Talker talker(GameTestHelper helper, BlockPos pos, long dollars) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "QaRaider" + (System.nanoTime() % 10000)), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				if (!overlay) {
					seen.add(message);
				}
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		player.getInventory().clearContent();
		CobbleDollarsBank.take(player, CobbleDollarsBank.balance(player));
		CobbleDollarsBank.add(player, dollars);
		seen.clear();
		return new Talker(player, seen);
	}

	/** A village trainer of {@code rank} (1 Novice to 5 Master) at Training Post {@code index}. */
	static Villager trainer(GameTestHelper helper, int index, int rank) {
		helper.setBlock(POSTS[index], ModBlocks.TRAINING_POST);
		Villager villager = helper.spawn(EntityType.VILLAGER, POSTS[index].offset(0, 0, -2));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POSTS[index]), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		villager.setVillagerData(villager.getVillagerData().setLevel(rank));
		return villager;
	}

	/** The Defence page as the player sees it at this hall. */
	static ChoiceMenu page(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		DefencePage.render(menu, (ServerLevel) player.level(), hall);
		return menu;
	}

	/** The lines of the first den's helpers button (empty when there's none). */
	static List<String> button(ChoiceMenu menu) {
		ItemStack icon = menu.icon(DefencePage.HELPERS[0]);
		return icon.isEmpty() ? List.of() : RaidDenCompatTests.lines(icon);
	}

	/** A click on the helpers button, as the client's click on the open screen arrives. */
	static void click(ChoiceMenu menu, ServerPlayer player) {
		menu.clicked(DefencePage.HELPERS[0], 0, ClickType.PICKUP, player);
	}

	static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	static int count(ServerPlayer player, String id) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	static void noRawText(GameTestHelper helper, List<String> lines) {
		for (String l : lines) {
			helper.assertFalse(l.contains("aliveworkplace.") || l.contains("%") || l.contains("cobblemonraiddens."), "a raw line: " + l);
		}
	}

	/** Takes the CobbleDollars bank away (a world without CobbleDollars); returns what was there, to put back. */
	static Object noBank() {
		try {
			java.lang.reflect.Field f = io.github.jcondedata.aliveworkplace.work.Extension.class.getDeclaredField("filling");
			f.setAccessible(true);
			Object was = f.get(Bank.EXTENSION);
			f.set(Bank.EXTENSION, null);
			return was;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("can't take the bank away: " + e);
		}
	}

	static void bankBack(Object was) {
		try {
			java.lang.reflect.Field f = io.github.jcondedata.aliveworkplace.work.Extension.class.getDeclaredField("filling");
			f.setAccessible(true);
			f.set(Bank.EXTENSION, was);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("can't put the bank back: " + e);
		}
	}

	static final String HIRED = ".+ will help you raid the tier 3 Fire raid den \\(.+\\): %d of 3 hired\\. Lead the way!";

	/**
	 * The player's own way in: the Defence page's button, clicked. Exactly the price hires the best trainer (balance 0),
	 * the button then counts "1 of 3"; one CobbleDollar short is refused with the price and takes nothing; the helpers keep
	 * up with the player.
	 */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '800' '"qaDenHelperClick"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 800, batch = "qaDenHelperClick")
	public void theDefencePageButtonHiresAtExactlyThePrice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		RaidHelperCompatTests.time(level, 1000);
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager journeyman = trainer(helper, 1, 3);
		Villager master = trainer(helper, 0, 5);
		Talker t = talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS);
		ServerPlayer player = t.player();
		ChoiceMenu menu = page(player, at[0]);
		List<String> before = button(menu);
		helper.assertTrue(before.contains("Hire a raid helper") && before.contains("Hired: 0 of 3 (2 free in the village)"), "the button: " + before);
		noRawText(helper, before);
		click(menu, player);
		helper.assertTrue(t.last().matches(String.format(HIRED, 1)), "after the click the player read: " + t.said());
		helper.assertTrue(CobbleDollarsBank.balance(player) == 0, "left after paying exactly the price: " + CobbleDollarsBank.balance(player));
		helper.assertTrue(RaidHelperCompatTests.hire(helper, player).helpers.equals(List.of(master.getUUID())), "the Master should be hired first");
		List<String> after = button(menu);
		helper.assertTrue(after.contains("Hired: 1 of 3 (1 free in the village)"), "the button after hiring: " + after);
		// one CobbleDollar short
		CobbleDollarsBank.add(player, DOLLARS - 1);
		int lines = t.seen().size();
		click(menu, player);
		helper.assertTrue(t.seen().size() == lines + 1 && t.last().equals("A raid helper costs " + DOLLARS + " CobbleDollars"), "one short: " + t.said());
		helper.assertTrue(CobbleDollarsBank.balance(player) == DOLLARS - 1 && RaidHelperCompatTests.hire(helper, player).helpers.size() == 1,
			"one short took " + (DOLLARS - 1 - CobbleDollarsBank.balance(player)) + " and hired " + RaidHelperCompatTests.hire(helper, player).helpers.size());
		CobbleDollarsBank.add(player, 1);
		click(menu, player);
		helper.assertTrue(t.last().matches(String.format(HIRED, 2)) && CobbleDollarsBank.balance(player) == 0, "the second: " + t.said());
		helper.assertTrue(button(menu).contains("Hired: 2 of 3 (0 free in the village)"), "the button with two: " + button(menu));
		BlockPos away = helper.absolutePos(new BlockPos(12, 2, 4));
		player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(master.distanceTo(player) <= 4.5 && journeyman.distanceTo(player) <= 4.5,
				"the helpers are " + Math.round(master.distanceTo(player)) + " and " + Math.round(journeyman.distanceTo(player)) + " blocks off"))
			.thenExecute(() -> {
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
				RaidHelperCompatTests.time(level, time);
			})
			.thenSucceed();
	}

	/**
	 * A world without CobbleDollars pays in real emeralds from the inventory, at the configured {@code denHelperPrice} (7
	 * here): 6 emeralds over two stacks are refused and kept, the 7th makes it, and all 7 are taken. The page shows 7.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenHelperEmeralds"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenHelperEmeralds")
	public void emeraldsAtACustomPriceExactlyAndOneShort(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = trainer(helper, 0, 5);
		Talker t = talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), 0);
		ServerPlayer player = t.player();
		int price = RaidHelpers.PRICE;
		Object bank = noBank();
		try {
			RaidHelpers.PRICE = 7;
			player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 4));
			player.getInventory().setItem(5, new ItemStack(Items.EMERALD, 2));
			ChoiceMenu menu = page(player, at[0]);
			helper.assertTrue(button(menu).contains("7 emeralds a helper"), "the price on the page: " + button(menu));
			click(menu, player);
			helper.assertTrue(t.last().equals("A raid helper costs 7 emeralds"), "with 6 of 7 emeralds: " + t.said());
			helper.assertTrue(emeralds(player) == 6 && RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && !RaidHelpers.busy(master),
				"refused but " + (6 - emeralds(player)) + " emeralds taken / hired anyway");
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 1));
			click(menu, player);
			helper.assertTrue(t.last().matches(String.format(HIRED, 1)), "with exactly 7: " + t.said());
			helper.assertTrue(emeralds(player) == 0 && RaidHelpers.busy(master), "emeralds left " + emeralds(player));
		} finally {
			RaidHelpers.PRICE = price;
			bankBack(bank);
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** A village with villagers but no trainers: the button says 0 free, a click is refused in plain words and costs nothing. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenHelperNone"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenHelperNone")
	public void noTrainersInTheVillageMeansNoHelper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 6));
		farmer.setVillagerData(farmer.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(5));
		Talker t = talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS * 3);
		ServerPlayer player = t.player();
		try {
			ChoiceMenu menu = page(player, at[0]);
			helper.assertTrue(button(menu).contains("Hired: 0 of 3 (0 free in the village)"), "the button: " + button(menu));
			click(menu, player);
			helper.assertTrue(t.last().equals("No trainer of the village is free to help"), "with no trainers: " + t.said());
			helper.assertTrue(CobbleDollarsBank.balance(player) == DOLLARS * 3 && RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty()
				&& !RaidHelpers.busy(farmer), "money taken or the farmer hired");
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * The den goes to rest while the screen is open: the old button, clicked, is refused ("resting") and costs nothing; the
	 * page drawn again has no hire button for it.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenHelperResting"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenHelperResting")
	public void aStaleButtonForADenThatWentToRestIsRefused(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		trainer(helper, 0, 5);
		trainer(helper, 1, 4);
		Talker t = talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS * 2);
		ServerPlayer player = t.player();
		try {
			ChoiceMenu menu = page(player, at[0]);
			click(menu, player);
			helper.assertTrue(t.last().matches(String.format(HIRED, 1)), "the first: " + t.said());
			ChoiceMenu stale = page(player, at[0]);
			level.setBlockAndUpdate(at[1], RaidDenCompatTests.den("tier_three", "fire", false));
			RaidDens.near(level, at[0]);
			click(stale, player);
			helper.assertTrue(t.last().equals("That den is resting: hire helpers when it glows again"), "a resting den's old button: " + t.said());
			helper.assertTrue(CobbleDollarsBank.balance(player) == DOLLARS && RaidHelperCompatTests.hire(helper, player).helpers.size() == 1,
				"the resting den took money or a helper");
			ChoiceMenu fresh = page(player, at[0]);
			helper.assertFalse(button(fresh).contains("Hire a raid helper"), "a hire button for a resting den: " + button(fresh));
		} finally {
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * Saved and read back mid-hire (the level's saved data swapped for what the file holds): the helper is still hired and
	 * still follows the player; a helper on the way home is still on the way home after a save.
	 */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '800' '"qaDenHelperReload"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 800, batch = "qaDenHelperReload")
	public void aHiringSavedAndReadBackStillFollows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		RaidHelperCompatTests.time(level, 1000);
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = trainer(helper, 0, 5);
		Villager expert = trainer(helper, 1, 4);
		Talker t = talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS * 2);
		ServerPlayer player = t.player();
		RaidHelpers.hire(level, at[0], at[1], player);
		CompoundTag saved = RaidHelpers.Data.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set("aliveworkplace_raid_helpers", RaidHelpers.Data.load(saved.copy(), level.registryAccess()));
		helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).map(h -> h.helpers.equals(List.of(master.getUUID()))).orElse(false)
			&& RaidHelpers.busy(master) && !RaidHelpers.busy(expert), "after reading the save back: " + saved);
		BlockPos away = helper.absolutePos(new BlockPos(13, 2, 3));
		player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(master.distanceTo(player) <= 4.5, "the reloaded helper is " + Math.round(master.distanceTo(player)) + " blocks off"))
			.thenExecute(() -> {
				RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
				hire.until = level.getGameTime();
				RaidHelpers.tick(level, hire);
				helper.assertTrue(t.last().equals("A day has passed: your raid helpers for the tier 3 Fire raid den head home"), "the day over: " + t.said());
				CompoundTag home = RaidHelpers.Data.get(level).save(new CompoundTag(), level.registryAccess());
				level.getDataStorage().set("aliveworkplace_raid_helpers", RaidHelpers.Data.load(home.copy(), level.registryAccess()));
				helper.assertTrue(RaidHelpers.homeward(level, master.getUUID()) && RaidHelpers.busy(master)
					&& RaidHelpers.free(level, at[0]).stream().noneMatch(v -> v == master), "the walk home lost in the save: " + home);
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
				RaidHelperCompatTests.time(level, time);
			})
			.thenSucceed();
	}

	/**
	 * The rank-under-tier rule on both sides of every boundary (a helper more than one rank under the tier only cheers),
	 * the quarter floor reached exactly from just above it, and no healing when the player has already taken the boss
	 * below a quarter. A Novice alone at a tier 3 den deals nothing but still cheers.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenHelperRanks"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenHelperRanks")
	public void theRankRuleAndTheQuarterFloor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager[] ranks = new Villager[6];
		for (int r = 1; r <= 5; r++) {
			ranks[r] = trainer(helper, r - 1, r);
		}
		for (int r = 1; r <= 5; r++) {
			helper.assertTrue(RaidHelpers.strength(ranks[r], r + 1) > 0f, "rank " + r + " should strike a tier " + (r + 1) + " boss");
			helper.assertTrue(RaidHelpers.strength(ranks[r], r) > 0f, "rank " + r + " should strike a tier " + r + " boss");
			helper.assertTrue(RaidHelpers.strength(ranks[r], r + 2) == 0f, "rank " + r + " should only cheer at tier " + (r + 2));
		}
		ServerPlayer player = talker(helper, at[1].offset(3, 0, 0), DOLLARS * 3).player();
		ServerPlayer[] novicePlayer = new ServerPlayer[1];
		try {
			// three hired: Master, Expert, Journeyman (the best first)
			for (int i = 0; i < 3; i++) {
				RaidHelpers.hire(level, at[0], at[1], player);
			}
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			RaidHelperCompatTests.FakeRaid raid = new RaidHelperCompatTests.FakeRaid(1000f);
			raid.health = 255f; // just above a quarter
			float dealt = RaidHelpers.strike(level, hire, raid);
			helper.assertTrue(Math.abs(raid.health - 250f) < 0.01f && Math.abs(dealt - 0.005f) < 1.0e-4f,
				"from 25.5%: health " + raid.health + ", dealt " + dealt);
			helper.assertTrue(RaidHelpers.strike(level, hire, raid) == 0f && Math.abs(raid.health - 250f) < 0.01f, "under the floor: " + raid.health);
			raid.health = 100f; // the player's own blows took it to a tenth
			float none = RaidHelpers.strike(level, hire, raid);
			helper.assertTrue(none == 0f && Math.abs(raid.health - 100f) < 0.01f, "the helpers healed the boss to " + raid.health + " (dealt " + none + ")");
			// a Novice alone at a tier 3 den: cheers, no blows
			RaidHelpers.Data.get(level).hires().clear();
			for (int r = 2; r <= 5; r++) {
				ranks[r].discard();
			}
			novicePlayer[0] = talker(helper, at[1].offset(3, 0, 0), DOLLARS).player();
			String said = RaidHelpers.hire(level, at[0], at[1], novicePlayer[0]).getString();
			helper.assertTrue(said.matches(String.format(HIRED, 1)), "the Novice: " + said);
			RaidHelperCompatTests.stat(novicePlayer[0], RaidHelpers.JOINED);
			helper.assertTrue(RaidHelperCompatTests.cheers(novicePlayer[0]) == 1, "the Novice's cheer: " + RaidHelperCompatTests.cheers(novicePlayer[0]));
			RaidHelperCompatTests.FakeRaid tough = new RaidHelperCompatTests.FakeRaid(400f);
			helper.assertTrue(RaidHelpers.strike(level, RaidHelperCompatTests.hire(helper, novicePlayer[0]), tough) == 0f && tough.health == 400f,
				"a Novice scratched a tier 3 boss: " + tough.health);
		} finally {
			if (novicePlayer[0] != null) {
				RaidHelperCompatTests.cleanUp(helper, at[1], player, novicePlayer[0]);
			} else {
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
			}
		}
		helper.succeed();
	}

	/**
	 * Three helpers, a raid joined at the den: one Cheer each, Attack, Defense and Heal, each named in chat with the strike
	 * line after; joined again, nothing. Won: the player reads the win, all three head home and can't be hired on the way;
	 * home again, a new hiring's raid cheers again.
	 */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '600' '"qaDenHelperCheers"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 600, batch = "qaDenHelperCheers")
	public void threeHelpersCheerOnceEachAndAgainNextRaid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		RaidHelperCompatTests.time(level, 1000);
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager[] three = {trainer(helper, 0, 5), trainer(helper, 1, 4), trainer(helper, 2, 3)};
		Talker t = talker(helper, at[1].offset(3, 0, 0), DOLLARS * 4);
		ServerPlayer player = t.player();
		for (int i = 0; i < 3; i++) {
			RaidHelpers.hire(level, at[0], at[1], player);
		}
		int from = t.seen().size();
		RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
		List<String> joined = t.said().subList(from, t.seen().size());
		helper.assertTrue(count(player, "cobblemonraiddens:cheer_attack") == 1 && count(player, "cobblemonraiddens:cheer_defense") == 1
			&& count(player, "cobblemonraiddens:cheer_heal") == 1, "the cheers: " + RaidHelperCompatTests.cheers(player) + " " + joined);
		helper.assertTrue(joined.stream().filter(s -> s.matches(".+ cheers you on: a .+ for the raid!")).count() == 3, "the cheer lines: " + joined);
		String strike = RaidAssist.EXTENSION.present()
			? "Your raid helpers send their Pokémon at the boss: they wear it down, but the last blows are yours"
			: "Your raid helpers cheer from beside the den";
		helper.assertTrue(joined.contains(strike), "the raid line: " + joined);
		noRawText(helper, joined);
		RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
		helper.assertTrue(RaidHelperCompatTests.cheers(player) == 3, "a second join cheered again: " + RaidHelperCompatTests.cheers(player));
		RaidHelperCompatTests.stat(player, "tier_three_completed");
		helper.assertTrue(t.said().contains("The tier 3 Fire raid den is beaten! Your raid helpers head home"), "the win: " + t.said());
		for (Villager v : three) {
			helper.assertTrue(RaidHelpers.homeward(level, v.getUUID()), "a helper not heading home");
		}
		// a fresh hiring for the next raid: nobody's free while they walk home
		level.setBlockAndUpdate(at[1], RaidDenCompatTests.den("tier_three", "fire", true));
		RaidDens.near(level, at[0]);
		String walking = RaidHelpers.hire(level, at[0], at[1], player).getString();
		helper.assertTrue(walking.equals("No trainer of the village is free to help") && CobbleDollarsBank.balance(player) == DOLLARS,
			"hiring helpers on their way home: " + walking);
		for (int i = 0; i < 3; i++) {
			BlockPos post = helper.absolutePos(POSTS[i]).offset(0, 0, -1);
			three[i].teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
		}
		helper.startSequence()
			.thenWaitUntil(() -> {
				for (Villager v : three) {
					helper.assertFalse(RaidHelpers.homeward(level, v.getUUID()), "a helper still on the way home at its post");
				}
			})
			.thenExecute(() -> {
				String again = RaidHelpers.hire(level, at[0], at[1], player).getString();
				// (Raid Dens may roll the den's tier and type again meanwhile: any tier and type)
				helper.assertTrue(again.matches(".+ will help you raid the tier \\d .+ raid den \\(.+\\): 1 of 3 hired\\. Lead the way!"),
					"hiring for the next raid: " + again);
				RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
				helper.assertTrue(RaidHelperCompatTests.cheers(player) == 4, "the next raid's cheer: " + RaidHelperCompatTests.cheers(player));
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
				RaidHelperCompatTests.time(level, time);
			})
			.thenSucceed();
	}

	/** Switched off mid-hire (denHelpers false): the hiring ends on the next look, the helper walks home, and no button. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '1000' '"qaDenHelperOff"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 1000, batch = "qaDenHelperOff")
	public void switchedOffMidHireTheHelperWalksHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		RaidHelperCompatTests.time(level, 1000);
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = trainer(helper, 0, 5);
		Talker t = talker(helper, helper.absolutePos(new BlockPos(3, 2, 3)), DOLLARS);
		ServerPlayer player = t.player();
		RaidHelpers.hire(level, at[0], at[1], player);
		BlockPos away = helper.absolutePos(new BlockPos(14, 2, 3));
		player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		BlockPos post = helper.absolutePos(POSTS[0]);
		boolean was = RaidHelpers.ENABLED;
		boolean[] released = new boolean[2];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(master.distanceTo(player) <= 4.5, "the helper is " + Math.round(master.distanceTo(player)) + " blocks off"))
			.thenExecute(() -> RaidHelpers.ENABLED = false)
			.thenExecuteAfter(12, () -> {
				released[0] = RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty();
				released[1] = RaidHelpers.homeward(level, master.getUUID());
				boolean button = button(page(player, at[0])).contains("Hire a raid helper");
				RaidHelpers.ENABLED = was;
				helper.assertTrue(released[0] && released[1], "switched off: hiring ended " + released[0] + ", heading home " + released[1]);
				helper.assertFalse(button, "a hire button while switched off");
			})
			.thenWaitUntil(() -> helper.assertTrue(!RaidHelpers.homeward(level, master.getUUID()),
				"the helper is " + Math.round(Math.sqrt(master.blockPosition().distSqr(post))) + " blocks from its post"))
			.thenExecute(() -> {
				helper.assertTrue(master.blockPosition().distSqr(post) <= 25, "home is the post: " + master.blockPosition() + " vs " + post);
				RaidHelpers.ENABLED = was;
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
				RaidHelperCompatTests.time(level, time);
			})
			.thenSucceed();
	}
}
