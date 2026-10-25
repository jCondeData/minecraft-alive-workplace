package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.shop.ShopLedger;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * QA (lane E, saves): the world-wide records players count on, through a real restart: written to the world's data
 * folder and read back by the game's own loader (the cache dropped), not a copy in memory. The post (wiki
 * village-hall/mail: a parcel handed in for far away arrives at the next dawn; a player's mailbox is known to the
 * postmen), the Travel Posts a player has found (a ticket only goes to posts you have visited), and a shop's takings
 * while its owner was away (paid when they next join).
 */
public class QaWorldSavesGameTests implements FabricGameTest {

	/** Writes the overworld's data to disk and forgets {@code name}, so the next get() reads it from the file. */
	private static void restart(MinecraftServer server, String name) {
		DimensionDataStorage storage = server.overworld().getDataStorage();
		storage.save();
		try {
			Field cache = DimensionDataStorage.class.getDeclaredField("cache");
			cache.setAccessible(true);
			((Map<?, ?>) cache.get(storage)).remove(name);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** A parcel on its way (in transit to far away) and the sender's mailbox: every part of it comes back after a restart. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesMail"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesMail")
	public void aParcelInTransitAndAMailboxComeBackAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		UUID from = UUID.randomUUID();
		UUID to = UUID.randomUUID();
		GlobalPos box = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(1, 2, 1)));
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Grandpa's Blade"));
		sword.setDamageValue(17);
		PostOffice office = PostOffice.get(server);
		office.register(from, box);
		Parcel parcel = office.post(from, "Alice", to, "Bob", box, 12_345L, List.of(sword.copy(), new ItemStack(Items.EMERALD, 40)));
		parcel.setStatus(Parcel.Status.IN_TRANSIT);
		office.changed();
		UUID id = parcel.id();
		try {
			restart(server, "aliveworkplace_mail");
			PostOffice back = PostOffice.get(server);
			helper.assertTrue(back != office, "the post wasn't read back from the disk");
			helper.assertTrue(box.equals(back.mailboxOf(from)), "Alice's mailbox after a restart: " + back.mailboxOf(from));
			Parcel p = back.parcel(id);
			helper.assertTrue(p != null, "the parcel in transit was lost in the restart");
			helper.assertTrue(p.from().equals(from) && p.to().equals(to) && "Alice".equals(p.fromName()) && "Bob".equals(p.toName()),
				"the parcel's sender and recipient after a restart: " + p.fromName() + " to " + p.toName());
			helper.assertTrue(p.status() == Parcel.Status.IN_TRANSIT, "the parcel was in transit, now " + p.status());
			helper.assertTrue(p.sentAt() == 12_345L && box.equals(p.origin()), "when and where it was posted: " + p.sentAt() + " at " + p.origin());
			helper.assertTrue(p.items().size() == 2, "the parcel's items: " + p.items());
			ItemStack blade = p.items().get(0);
			helper.assertTrue(ItemStack.isSameItemSameComponents(blade, sword) && blade.getCount() == 1,
				"the named, worn sword came back as " + blade + " named " + blade.getHoverName().getString() + " damage " + blade.getDamageValue());
			helper.assertTrue(p.items().get(1).is(Items.EMERALD) && p.items().get(1).getCount() == 40, "the 40 emeralds came back as " + p.items().get(1));
			helper.assertTrue(p.count() == 41, "the parcel counts " + p.count() + " items, not 41");
		} finally {
			PostOffice now = PostOffice.get(server);
			Parcel p = now.parcel(id);
			if (p != null) {
				now.remove(p);
			}
			now.unregister(from, box);
			now.changed();
		}
		helper.succeed();
	}

	/** A Travel Post and the player who found it: after a restart the post keeps its id and name and the player still knows it. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesTravel"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesTravel")
	public void aTravelPostAPlayerFoundIsStillKnownAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		UUID player = UUID.randomUUID();
		UUID stranger = UUID.randomUUID();
		GlobalPos here = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(1, 2, 1)));
		GlobalPos there = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(1, 2, 1)).offset(400, 0, -250));
		TravelNetwork network = TravelNetwork.get(server);
		TravelNetwork.Post a = network.add(here, "Elderford Post");
		TravelNetwork.Post b = network.add(there, "Ashwick Post");
		network.visit(player, a);
		network.visit(player, b);
		try {
			restart(server, "aliveworkplace_travel");
			TravelNetwork back = TravelNetwork.get(server);
			helper.assertTrue(back != network, "the travel network wasn't read back from the disk");
			TravelNetwork.Post a2 = back.at(here);
			helper.assertTrue(a2 != null && a2.id().equals(a.id()) && "Elderford Post".equals(a2.name()), "the post after a restart: " + a2 + ", was " + a);
			List<TravelNetwork.Post> known = back.known(player, a2);
			helper.assertTrue(known.size() == 1 && known.get(0).id().equals(b.id()) && "Ashwick Post".equals(known.get(0).name()),
				"the posts the player found, other than the one they stand at, after a restart: " + known);
			helper.assertTrue(back.known(stranger, null).isEmpty(), "a player who found nothing knows posts after a restart: " + back.known(stranger, null));
			helper.assertTrue(!back.visit(player, a2), "the player's visit was forgotten in the restart (visiting again counted as new)");
		} finally {
			TravelNetwork now = TravelNetwork.get(server);
			now.remove(here);
			now.remove(there);
			now.setDirty();
		}
		helper.succeed();
	}

	/** What a shop took while its owner was offline is still owed after a restart, and settled when they join. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesShop"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesShop")
	public void aShopsTakingsWhileItsOwnerWasAwayAreOwedAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		var owner = helper.makeMockServerPlayerInLevel();
		server.getPlayerList().remove(owner);
		owner.getInventory().clearContent();
		ShopLedger.payOwner(server, owner.getUUID(), 700);
		ShopLedger.payOwner(server, owner.getUUID(), 534);
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 1234, "setup: owed " + ShopLedger.get(server).pending(owner.getUUID()));
		ShopLedger before = ShopLedger.get(server);
		restart(server, "aliveworkplace_shops");
		ShopLedger back = ShopLedger.get(server);
		helper.assertTrue(back != before, "the shops' ledger wasn't read back from the disk");
		helper.assertTrue(back.pending(owner.getUUID()) == 1234, "owed after a restart: " + back.pending(owner.getUUID()) + ", not 1234");
		ShopLedger.collect(owner); // this world has no CobbleDollars: paid in emeralds, the part under one kept owed (B416)
		int emeralds = owner.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
		helper.assertTrue(emeralds == 12, "1234 CobbleDollars owed paid " + emeralds + " emeralds after a restart, not 12");
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 34, "owed after joining: " + ShopLedger.get(server).pending(owner.getUUID()) + ", not 34");
		restart(server, "aliveworkplace_shops");
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 34, "the 34 left owed after a second restart: " + ShopLedger.get(server).pending(owner.getUUID()));
		helper.succeed();
	}
}
