package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.PlayerBank;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-03), verifying B406 from its text through the places it names, at 1 and at 2 (and the control at
 * 0): "one emerald reads '1 emeralds' without CobbleDollars. Money.describe (every price, purse, treasury and quest
 * reward on the hall pages and in chat) ... beating a Novice trainer ... posting a single item or a letter alone says
 * 'Posted 1 items to <player>'. Expected '1 emerald', '1 item'." QaOneEmeraldBugTests and PluralSweepGameTests check the
 * words and a Novice's prize; these go through the hall's page, the treasury collected, the bank, a quest reward on the
 * board, what a player is short, a trainer one tier up, and the mailbox's Send as a client sends it.
 */
public class QaOneEmerald1103d2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BlockPos abs = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			helper.getLevel().setBlockAndUpdate(abs, Blocks.AIR.defaultBlockState());
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		return entity;
	}

	/** Every lore line (and name) on the menu's icons. */
	private static List<String> lines(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot = 0; slot < 54; slot++) {
			ItemStack icon;
			try {
				icon = menu.icon(slot);
			} catch (RuntimeException e) {
				break;
			}
			if (icon == null || icon.isEmpty()) {
				continue;
			}
			out.add(icon.getHoverName().getString());
			ItemLore lore = icon.get(DataComponents.LORE);
			if (lore != null) {
				lore.lines().forEach(l -> out.add(l.getString()));
			}
		}
		return out;
	}

	private static List<String> said(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/**
	 * The treasury on the hall's page and collected: 1 emerald (100 cents, and 150: whole emeralds only) reads "1 emerald"
	 * both places; 2 read "2 emeralds"; an empty treasury "0 emeralds".
	 */
	//$ gametest_batch AREA '"qa1103d2Treasury"'
	@GameTest(template = AREA, batch = "qa1103d2Treasury")
	public void theTreasuryReadsOneEmeraldOnThePageAndWhenCollected(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "CobbleDollars is loaded in this run");
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		String name = VillageHalls.name(level, hall).getString();
		int[] cents = {100, 150, 250};
		String[] words = {"1 emerald", "1 emerald", "2 emeralds"};
		for (int i = 0; i < cents.length; i++) {
			entity.setTreasury(cents[i]);
			List<String> page = lines(VillageHallScreen.forTest(client.player(), hall));
			String want = "Treasury: " + words[i] + " (click to collect)";
			helper.assertTrue(page.contains(want), "the hall's page at " + cents[i] + " cents, wanted '" + want + "': "
				+ page.stream().filter(s -> s.startsWith("Treasury")).toList());
			String got = Treasury.collect(level, hall, client.player()).getString();
			String collected = "You collect " + words[i] + " from the treasury of " + name + ".";
			helper.assertTrue(got.equals(collected), "collected at " + cents[i] + " cents: '" + got + "', wanted '" + collected + "'");
		}
		entity.setTreasury(0);
		List<String> empty = lines(VillageHallScreen.forTest(client.player(), hall));
		helper.assertTrue(empty.contains("Treasury: 0 emeralds (click to collect)"), "an empty treasury: " + empty.stream().filter(s -> s.startsWith("Treasury")).toList());
		helper.assertTrue(client.player().getInventory().countItem(Items.EMERALD) == 4, "emeralds collected: " + client.player().getInventory().countItem(Items.EMERALD));
		helper.succeed();
	}

	/** A quest's reward on the hall's quest page: 1 reads "Reward: 1 emerald", 2 "Reward: 2 emeralds". */
	//$ gametest_batch AREA '"qa1103d2Reward"'
	@GameTest(template = AREA, batch = "qa1103d2Reward")
	public void aQuestRewardOfOneEmeraldReadsInTheSingularOnTheBoard(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		long now = level.getGameTime();
		entity.setQuests(List.of(
			new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.BRING, "minecraft:bread", 3, 0, 1, now, "", Optional.empty(), Optional.empty()),
			new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.BRING, "minecraft:wheat", 5, 0, 2, now, "", Optional.empty(), Optional.empty())));
		List<String> page = lines(ChoiceMenu.detached(client.player(), m -> VillageHallScreen.renderQuests(m, level, hall, client.player())));
		List<String> rewards = page.stream().filter(s -> s.startsWith("Reward")).toList();
		helper.assertTrue(rewards.contains("Reward: 1 emerald") && rewards.contains("Reward: 2 emeralds"), "the quest page's rewards: " + rewards + " (page " + page + ")");
		helper.succeed();
	}

	/**
	 * The purse: 1 emerald in the bag reads "1 emerald", 2 "2 emeralds" (the bank's deposits and withdrawals at 1 and 2 are
	 * QaBuilderWords1031GameTests'); and taking out of a bank that's closed but empty says so, not a sum.
	 */
	//$ gametest_batch AREA '"qa1103d2Bank"'
	@GameTest(template = AREA, batch = "qa1103d2Bank")
	public void thePurseReadsOneEmeraldInTheSingular(GameTestHelper helper) {
		hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		var player = client.player();
		player.getInventory().add(new ItemStack(Items.EMERALD, 1));
		helper.assertTrue(Money.balance(player).getString().equals("1 emerald"), "a purse of one: " + Money.balance(player).getString());
		player.getInventory().add(new ItemStack(Items.EMERALD, 1));
		helper.assertTrue(Money.balance(player).getString().equals("2 emeralds"), "a purse of two: " + Money.balance(player).getString());
		player.getInventory().clearContent();
		helper.assertTrue(Money.balance(player).getString().equals("0 emeralds"), "an empty purse: " + Money.balance(player).getString());
		String none = PlayerBank.withdraw(level, hall, player, 1).getString();
		String empty = Component.translatable("message.aliveworkplace.bank.empty").getString();
		helper.assertTrue(none.equals(empty) && !empty.startsWith("message."), "an empty account: '" + none + "'");
		helper.succeed();
	}

	/** What a player is short of a price: 1 emerald short reads "1 emerald", 2 short "2 emeralds", none short "0 emeralds". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void beingOneEmeraldShortReadsInTheSingular(GameTestHelper helper) {
		var player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.assertTrue(Retraining.shortBy(player, 200, 2).getString().equals("2 emeralds"), "2 short: " + Retraining.shortBy(player, 200, 2).getString());
		player.getInventory().add(new ItemStack(Items.EMERALD, 1));
		helper.assertTrue(Retraining.shortBy(player, 200, 2).getString().equals("1 emerald"), "1 short: " + Retraining.shortBy(player, 200, 2).getString());
		player.getInventory().add(new ItemStack(Items.EMERALD, 1));
		helper.assertTrue(Retraining.shortBy(player, 200, 2).getString().equals("0 emeralds"), "none short: " + Retraining.shortBy(player, 200, 2).getString());
		helper.succeed();
	}

	/** The plural side of the Novice's prize: a trainer one tier up (Apprentice) pays 2, "+2 emeralds", and the player has them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void beatingAnApprenticeTrainerPaysTwoEmeraldsInThePlural(GameTestHelper helper) {
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerLevel level = helper.getLevel();
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		trainer.setNoAi(true);
		trainer.setVillagerData(trainer.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(2));
		helper.assertTrue(Trainers.tier(trainer) == 2, "not tier 2: " + Trainers.tier(trainer));
		Trainers.battleOver(level.getServer(), trainer.getUUID(), client.player().getUUID(), true);
		String won = said(client.seen()).stream().filter(s -> s.startsWith("You beat ")).findFirst().orElse("");
		helper.assertTrue(won.endsWith("! +2 emeralds"), "the prize line: '" + won + "' (all: " + said(client.seen()) + ")");
		helper.assertTrue(client.player().getInventory().countItem(Items.EMERALD) == 2, "paid: " + client.player().getInventory().countItem(Items.EMERALD));
		helper.succeed();
	}

	/**
	 * The mailbox's Send as a client sends it, to the sender's own mailbox: a letter alone says "Posted 1 item to <name>. A
	 * postman will pick it up."; one item alone the same; a letter and an item "Posted 2 items ... pick them up."
	 */
	//$ gametest_batch AREA '"qa1103d2Mail"'
	@GameTest(template = AREA, batch = "qa1103d2Mail")
	public void postingOneThingSaysOneItem(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		QaStageFiveGameTests.Client owner = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		owner.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.MAILBOX));
		QaStageFourGameTests.press(helper, owner.player(), new BlockPos(3, 1, 3));
		BlockPos rel = new BlockPos(3, 2, 3);
		QaStageFourGameTests.press(helper, owner.player(), rel);
		helper.assertTrue(owner.player().containerMenu instanceof MailboxMenu, "the mailbox didn't open");
		String me = owner.player().getGameProfile().getName();
		String one = "Posted 1 item to " + me + ". A postman will pick it up.";
		String two = "Posted 2 items to " + me + ". A postman will pick them up.";

		owner.seen().clear();
		owner.send(new io.github.jcondedata.aliveworkplace.mail.Mail.Send(me, "hello"));
		helper.assertTrue(said(owner.seen()).stream().anyMatch(s -> s.startsWith(one)), "a letter alone: " + said(owner.seen()));

		owner.seen().clear();
		((MailboxMenu) owner.player().containerMenu).getSlot(0).set(new ItemStack(Items.DIRT));
		owner.send(new io.github.jcondedata.aliveworkplace.mail.Mail.Send(me, ""));
		helper.assertTrue(said(owner.seen()).stream().anyMatch(s -> s.startsWith(one)), "one item alone: " + said(owner.seen()));

		owner.seen().clear();
		((MailboxMenu) owner.player().containerMenu).getSlot(0).set(new ItemStack(Items.DIRT));
		owner.send(new io.github.jcondedata.aliveworkplace.mail.Mail.Send(me, "and a letter"));
		helper.assertTrue(said(owner.seen()).stream().anyMatch(s -> s.startsWith(two)), "a letter and an item: " + said(owner.seen()));
		QaStageFiveGameTests.closeScreen(owner.player());
		helper.succeed();
	}
}
