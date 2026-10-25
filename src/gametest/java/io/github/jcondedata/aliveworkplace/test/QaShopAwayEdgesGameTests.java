package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.shop.ShopLedger;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, optional-mod absence and config extremes), after B416's fix: a shop's takings owed in CobbleDollars
 * while its owner was offline are paid in emeralds when CobbleDollars is gone by the time they join. These check the
 * corners {@code QaShopAwayBugTests} leaves: the payout through the real join (the player list's login, not a direct
 * call), an owner who joins with a full or nearly full inventory (no emerald may vanish: what doesn't fit lands at
 * their feet), the server's {@code dollarsPerEmerald} at both ends of its range (1 and 10,000), and two owners' debts
 * kept apart. This test world has no CobbleDollars.
 */
public class QaShopAwayEdgesGameTests implements FabricGameTest {
	private static final String AWAY = "message.aliveworkplace.shop.while_away";

	private static List<String> awayLines(List<Component> seen) {
		Set<Component> once = Collections.newSetFromMap(new IdentityHashMap<>());
		return seen.stream().filter(c -> AWAY.equals(QaStageFiveGameTests.key(c)) && once.add(c)).map(Component::getString).toList();
	}

	/** The emeralds lying on the ground within 6 blocks of the player, removed as they are counted. */
	private static int emeraldsDroppedNear(ServerPlayer player) {
		int n = 0;
		for (ItemEntity e : player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(6),
			e -> e.getItem().is(Items.EMERALD))) {
			n += e.getItem().getCount();
			e.discard();
		}
		return n;
	}

	/** Fills every main inventory slot but {@code free} with dirt. */
	private static void fillBut(ServerPlayer p, int free) {
		p.getInventory().clearContent();
		for (int s = 0; s < 36 - free; s++) {
			p.getInventory().setItem(s, new ItemStack(Items.DIRT, 64));
		}
	}

	/**
	 * The owner joins through the server's own login (placeNewPlayer, as a real client does) with CobbleDollars gone:
	 * the takings owed under their UUID before they joined are paid in emeralds and they are told so.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayJoin"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayJoin")
	public void theRealJoinPaysTheTakingsInEmeralds(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		helper.assertTrue(Money.DOLLARS_PER_EMERALD == 100, "setup: the rate is " + Money.DOLLARS_PER_EMERALD);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		UUID id = UUID.randomUUID();
		ShopLedger.get(server).owe(id, 950); // sold while the owner was offline, CobbleDollars installed then
		List<Component> seen = new ArrayList<>();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(id, "qaAway"), false);
		ServerPlayer owner = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, owner, cookie);
		Leftovers.after(helper, () -> server.getPlayerList().remove(owner));
		owner.setGameMode(GameType.SURVIVAL);
		helper.runAfterDelay(2, () -> {
			int emeralds = owner.getInventory().countItem(Items.EMERALD) + emeraldsDroppedNear(owner);
			helper.assertTrue(emeralds == 9, "joining with 950 CobbleDollars owed paid " + emeralds + " emeralds, not 9");
			long owed = ShopLedger.get(server).pending(id);
			helper.assertTrue(owed == 50, "after the join " + owed + " CobbleDollars are owed, not the 50 left over");
			helper.assertTrue(awayLines(seen).equals(List.of("While you were away, your shops took 9 emeralds.")), "the owner was told: " + awayLines(seen));
			helper.succeed();
		});
	}

	/** An owner who joins with every slot full loses no emerald: the whole payout lands at their feet. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayFull"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayFull")
	public void aFullInventoryGetsTheEmeraldsAtItsFeet(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		MinecraftServer server = helper.getLevel().getServer();
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer owner = c.player();
		emeraldsDroppedNear(owner);
		fillBut(owner, 0);
		ShopLedger.get(server).owe(owner.getUUID(), 100L * 150);
		ShopLedger.collect(owner);
		int held = owner.getInventory().countItem(Items.EMERALD);
		int dropped = emeraldsDroppedNear(owner);
		helper.assertTrue(held == 0 && dropped == 150, "150 emeralds for a full inventory: " + held + " held and " + dropped + " dropped");
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 0, "still owed: " + ShopLedger.get(server).pending(owner.getUUID()));
		helper.assertTrue(owner.getInventory().countItem(Items.DIRT) == 36 * 64, "the owner's own things changed: " + owner.getInventory().countItem(Items.DIRT) + " dirt");
		helper.succeed();
	}

	/**
	 * Two free slots and a partly filled emerald stack: what fits goes into the inventory (topping up the stack first),
	 * the rest is dropped, and the total is exactly what was owed.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayNearly"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayNearly")
	public void aNearlyFullInventoryKeepsWhatFitsAndDropsTheRest(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		MinecraftServer server = helper.getLevel().getServer();
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer owner = c.player();
		emeraldsDroppedNear(owner);
		fillBut(owner, 3);
		owner.getInventory().setItem(33, new ItemStack(Items.EMERALD, 50)); // 14 more fit here, then two empty slots
		ShopLedger.get(server).owe(owner.getUUID(), 100L * 200 + 99);
		ShopLedger.collect(owner);
		int held = owner.getInventory().countItem(Items.EMERALD) - 50;
		int dropped = emeraldsDroppedNear(owner);
		helper.assertTrue(held == 14 + 128, "the inventory took " + held + " of the 200 emeralds, not the 142 that fit");
		helper.assertTrue(held + dropped == 200, "200 emeralds owed: " + held + " held and " + dropped + " dropped");
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 99, "still owed: " + ShopLedger.get(server).pending(owner.getUUID()));
		helper.assertTrue(awayLines(c.seen()).equals(List.of("While you were away, your shops took 200 emeralds.")), "the owner was told: " + awayLines(c.seen()));
		helper.succeed();
	}

	/** The rate at both ends of the config's range (1 and 10,000 CobbleDollars an emerald). */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayRate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayRate")
	public void theRateAtBothEndsOfItsRange(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		MinecraftServer server = helper.getLevel().getServer();
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer owner = c.player();
		int before = Money.DOLLARS_PER_EMERALD;
		try {
			Money.DOLLARS_PER_EMERALD = 1;
			owner.getInventory().clearContent();
			ShopLedger.get(server).owe(owner.getUUID(), 5000);
			ShopLedger.collect(owner);
			int got = owner.getInventory().countItem(Items.EMERALD) + emeraldsDroppedNear(owner);
			helper.assertTrue(got == 36 * 64, "at 1 an emerald, 5000 owed paid " + got + " emeralds, not an inventory's 2304");
			helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 5000 - 36 * 64,
				"at 1 an emerald still owed " + ShopLedger.get(server).pending(owner.getUUID()) + ", not 2696");
			owner.getInventory().clearContent();
			ShopLedger.collect(owner);
			ShopLedger.collect(owner);
			got = owner.getInventory().countItem(Items.EMERALD) + emeraldsDroppedNear(owner);
			helper.assertTrue(got == 2696 && ShopLedger.get(server).pending(owner.getUUID()) == 0,
				"two more joins paid " + got + " of 2696 and left " + ShopLedger.get(server).pending(owner.getUUID()));

			Money.DOLLARS_PER_EMERALD = 10_000;
			owner.getInventory().clearContent();
			ShopLedger.get(server).owe(owner.getUUID(), 25_000);
			ShopLedger.collect(owner);
			got = owner.getInventory().countItem(Items.EMERALD) + emeraldsDroppedNear(owner);
			helper.assertTrue(got == 2, "at 10,000 an emerald, 25,000 owed paid " + got + " emeralds, not 2");
			helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 5000, "at 10,000 still owed " + ShopLedger.get(server).pending(owner.getUUID()));
		} finally {
			Money.DOLLARS_PER_EMERALD = before;
		}
		helper.succeed();
	}

	/** One owner's join pays only their own takings; another offline owner's stay owed in full. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayEdges"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayEdges")
	public void anotherOwnersTakingsStayOwed(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		MinecraftServer server = helper.getLevel().getServer();
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer owner = c.player();
		owner.getInventory().clearContent();
		UUID other = UUID.randomUUID();
		ShopLedger.get(server).owe(other, 777);
		ShopLedger.get(server).owe(owner.getUUID(), 300);
		ShopLedger.collect(owner);
		helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 3, "the owner got " + owner.getInventory().countItem(Items.EMERALD) + " emeralds, not 3");
		helper.assertTrue(ShopLedger.get(server).pending(other) == 777, "the other owner's 777 became " + ShopLedger.get(server).pending(other));
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 0, "the owner still owed " + ShopLedger.get(server).pending(owner.getUUID()));
		ShopLedger.get(server).owe(other, -777); // a negative or zero sum is never owed
		helper.assertTrue(ShopLedger.get(server).pending(other) == 777, "a negative sale changed the debt to " + ShopLedger.get(server).pending(other));
		helper.succeed();
	}
}
