package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlockEntity;
import io.github.jcondedata.aliveworkplace.table.TableLibrary;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA stage 5 (2026-10-23): the block clicks stage 4 left unclicked (Grave, Mailbox, Drop Box, Storehouse, Bounty Board,
 * Foundation Stone, Blueprint Table) and the Blueprint Table's and preview's packets sent as a client sends them,
 * including spoofed values and a player far away. Clicks go through {@link QaStageFourGameTests#press} (the main hand
 * then the off hand, block first, as the client does); packets through the real {@code handleCustomPayload}; what the
 * server sends back is read off the player's connection.
 *
 * <p>Expectations from README.md, CHANGELOG.md and en_us.json, not the code: "Open it" (Mailbox; "Only the owner, their
 * friends and operators can open it"), "Right-click the Storehouse to see what the workers nearby are waiting for",
 * "Craft a Blueprint Table ... and right-click it", "take a copy for a Blank Blueprint", the Stored tab ("Take One Out
 * hands one back"), the Foundation Stone ("anything else shows the stone's status, as with an empty hand").
 */
public class QaStageFiveGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A player on a real connection whose outgoing packets can be read back, and the lines they were shown. */
	record Client(ServerPlayer player, EmbeddedChannel channel, List<Component> seen) {
		/** The custom payloads sent to this player since the last call. */
		List<CustomPacketPayload> sent() {
			List<CustomPacketPayload> out = new ArrayList<>();
			Object msg;
			while ((msg = channel.readOutbound()) != null) {
				if (msg instanceof ClientboundCustomPayloadPacket packet) {
					out.add(packet.payload());
				}
			}
			return out;
		}

		<T> List<T> sent(Class<T> type) {
			return sent().stream().filter(type::isInstance).map(type::cast).toList();
		}

		void send(CustomPacketPayload payload) {
			player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(payload));
		}
	}

	static Client client(GameTestHelper helper, GameType mode) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa5-" + Long.toHexString(level.getGameTime() % 0xfffff)), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(mode);
		player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Client client = new Client(player, channel, seen);
		client.sent(); // the login's packets
		return client;
	}

	static String key(Component c) {
		return QaStageFourGameTests.key(c);
	}

	static List<String> keys(List<Component> seen) {
		return QaStageFourGameTests.keys(seen);
	}

	/** The ways a player holds things when they right-click a block, each set up on {@code p} by {@link #hold}. */
	enum Hands {
		EMPTY, DIRT_MAIN, SWORD_MAIN, DIRT_OFF, SNEAK_EMPTY, FULL_BAG
	}

	static void hold(ServerPlayer p, Hands hands) {
		p.getInventory().clearContent();
		p.setShiftKeyDown(false);
		switch (hands) {
			case EMPTY -> {
			}
			case DIRT_MAIN -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 16));
			case SWORD_MAIN -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			case DIRT_OFF -> p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 16));
			case SNEAK_EMPTY -> p.setShiftKeyDown(true);
			case FULL_BAG -> {
				QaStageFourGameTests.fill(p);
				p.getInventory().selected = 3;
			}
		}
	}

	static void closeScreen(ServerPlayer p) {
		if (p.containerMenu != p.inventoryMenu) {
			p.closeContainer();
		}
	}

	// --- Blocks that answer with a line ---------------------------------------------------------------------------

	/**
	 * A villager's grave right-clicked shows its epitaph (their name), whatever the player holds, in survival and
	 * creative, from either hand, sneaking empty-handed and with a full bag; a block in the hand isn't placed on it.
	 */
	//$ gametest_batch AREA '"qa5Grave"'
	@GameTest(template = AREA, batch = "qa5Grave")
	public void graveShowsItsEpitaphWhateverYouHold(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		ada.setVillagerData(ada.getVillagerData().setProfession(VillagerProfession.FARMER));
		ada.setCustomName(Component.literal("Ada"));
		BlockPos abs = Graves.onDeath(helper.getLevel(), ada);
		helper.assertTrue(abs != null, "no grave was dug for a named farmer");
		ada.discard();
		BlockPos rel = null;
		for (BlockPos q : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(7, 5, 7))) {
			if (helper.absolutePos(q).equals(abs)) {
				rel = q.immutable();
			}
		}
		helper.assertTrue(rel != null, "the grave was dug outside the test area at " + abs);
		helper.assertBlockPresent(ModBlocks.GRAVE, rel);
		for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
			Client c = client(helper, mode);
			for (Hands hands : Hands.values()) {
				hold(c.player(), hands);
				c.seen().clear();
				QaStageFourGameTests.press(helper, c.player(), rel);
				Component epitaph = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.grave.epitaph");
				helper.assertTrue(epitaph != null, mode + " " + hands + ": no epitaph, saw " + keys(c.seen()));
				QaStageFourGameTests.assertRendered(helper, epitaph, "Ada");
				helper.assertBlockPresent(Blocks.AIR, rel.above());
				helper.assertBlockPresent(ModBlocks.GRAVE, rel);
			}
		}
		helper.succeed();
	}

	/**
	 * The Foundation Stone right-clicked with an empty hand, or holding anything that isn't one of the stage's goods,
	 * shows its status (no Wonder at this stone: "status.none"), in survival and creative, from either hand.
	 */
	//$ gametest_batch AREA '"qa5Stone"'
	@GameTest(template = AREA, batch = "qa5Stone")
	public void foundationStoneShowsItsStatusWhateverYouHold(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.FOUNDATION_STONE);
		for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
			Client c = client(helper, mode);
			for (Hands hands : Hands.values()) {
				hold(c.player(), hands);
				c.seen().clear();
				QaStageFourGameTests.press(helper, c.player(), rel);
				Component status = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.wonder.status.none");
				helper.assertTrue(status != null, mode + " " + hands + ": no status, saw " + keys(c.seen()));
				QaStageFourGameTests.assertRendered(helper, status);
				helper.assertBlockPresent(Blocks.AIR, rel.above());
			}
		}
		helper.succeed();
	}

	/**
	 * A Bounty Board with no village hall near says so ("bounty.no_hall") whatever the player holds; a Captain's Insignia
	 * clicked on it there is refused the same way and stays with the player.
	 */
	//$ gametest_batch AREA '"qa5Bounty"'
	@GameTest(template = AREA, batch = "qa5Bounty")
	public void bountyBoardWithNoHallSaysSoAndKeepsTheInsignia(GameTestHelper helper) {
		Leftovers.clear(helper);
		// "no hall near": only this test area counts, whatever other tests left standing round it
		int radius = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 4;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radius);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.BOUNTY_BOARD);
		for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
			Client c = client(helper, mode);
			for (Hands hands : Hands.values()) {
				hold(c.player(), hands);
				c.seen().clear();
				QaStageFourGameTests.press(helper, c.player(), rel);
				helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.bounty.no_hall"),
					mode + " " + hands + ": not told there's no hall, saw " + keys(c.seen()));
				closeScreen(c.player());
			}
			hold(c.player(), Hands.EMPTY);
			c.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CAPTAINS_INSIGNIA));
			c.seen().clear();
			QaStageFourGameTests.press(helper, c.player(), rel);
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.bounty.no_hall"), mode + " insignia: saw " + keys(c.seen()));
			helper.assertTrue(QaStageFourGameTests.count(c.player(), ModItems.CAPTAINS_INSIGNIA) == 1, mode + ": the insignia was used up with no hall");
		}
		helper.succeed();
	}

	// --- Blocks that open a screen ---------------------------------------------------------------------------------

	/**
	 * A Mailbox placed by a player is theirs: right-clicking it opens it for them however they hold things (README:
	 * "Open it, ..."), in survival and creative. Another player is refused, told whose it is, and no screen opens.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa5Mailbox"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa5Mailbox")
	public void mailboxOpensForItsOwnerOnlyWhateverTheyHold(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		Client owner = client(helper, GameType.SURVIVAL);
		owner.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.MAILBOX));
		QaStageFourGameTests.press(helper, owner.player(), new BlockPos(3, 1, 3));
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.assertBlockPresent(ModBlocks.MAILBOX, rel);
		MailboxBlockEntity mailbox = (MailboxBlockEntity) helper.getBlockEntity(rel);
		helper.assertTrue(owner.player().getUUID().equals(mailbox.owner()), "the placer doesn't own the mailbox: " + mailbox.owner());
		for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
			owner.player().setGameMode(mode);
			for (Hands hands : Hands.values()) {
				hold(owner.player(), hands);
				owner.seen().clear();
				QaStageFourGameTests.press(helper, owner.player(), rel);
				helper.assertTrue(owner.player().containerMenu instanceof MailboxMenu,
					mode + " " + hands + ": the owner's mailbox didn't open (" + owner.player().containerMenu + "), saw " + keys(owner.seen()));
				closeScreen(owner.player());
				helper.assertBlockPresent(Blocks.AIR, rel.above());
			}
		}
		helper.runAfterDelay(3, () -> {
			Client stranger = client(helper, GameType.SURVIVAL);
			for (Hands hands : List.of(Hands.EMPTY, Hands.DIRT_MAIN)) {
				hold(stranger.player(), hands);
				stranger.seen().clear();
				QaStageFourGameTests.press(helper, stranger.player(), rel);
				helper.assertTrue(stranger.player().containerMenu == stranger.player().inventoryMenu, hands + ": a stranger opened someone's mailbox");
				Component refused = QaStageFourGameTests.find(stranger.seen(), "message.aliveworkplace.mail.not_yours");
				helper.assertTrue(refused != null, hands + ": the stranger wasn't told, saw " + keys(stranger.seen()));
				QaStageFourGameTests.assertRendered(helper, refused, owner.player().getGameProfile().getName());
			}
			helper.succeed();
		});
	}

	/** A Drop Box right-clicked opens it, however the player holds things, in survival and creative. */
	//$ gametest_batch AREA '"qa5DropBox"'
	@GameTest(template = AREA, batch = "qa5DropBox")
	public void dropBoxOpensWhateverYouHold(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.DROP_BOX);
		for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
			Client c = client(helper, mode);
			for (Hands hands : Hands.values()) {
				hold(c.player(), hands);
				QaStageFourGameTests.press(helper, c.player(), rel);
				helper.assertTrue(c.player().containerMenu != c.player().inventoryMenu, mode + " " + hands + ": the drop box didn't open");
				closeScreen(c.player());
				helper.assertBlockPresent(Blocks.AIR, rel.above());
			}
		}
		helper.succeed();
	}

	/**
	 * A Storehouse placed by a player, right-clicked, opens its requests board ("Right-click the Storehouse to see what
	 * the workers nearby are waiting for"), however the player holds things, in survival and creative.
	 */
	//$ gametest_batch AREA '"qa5Storehouse"'
	@GameTest(template = AREA, batch = "qa5Storehouse")
	public void storehouseBoardOpensWhateverYouHold(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		Client placer = client(helper, GameType.SURVIVAL);
		placer.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.STOREHOUSE));
		QaStageFourGameTests.press(helper, placer.player(), new BlockPos(3, 1, 3));
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.assertBlockPresent(ModBlocks.STOREHOUSE, rel);
		for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
			placer.player().setGameMode(mode);
			for (Hands hands : Hands.values()) {
				hold(placer.player(), hands);
				QaStageFourGameTests.press(helper, placer.player(), rel);
				helper.assertTrue(placer.player().containerMenu instanceof ChoiceMenu,
					mode + " " + hands + ": the storehouse board didn't open (" + placer.player().containerMenu + "), saw " + keys(placer.seen()));
				closeScreen(placer.player());
				helper.assertBlockPresent(Blocks.AIR, rel.above());
			}
		}
		helper.succeed();
	}

	/**
	 * The Blueprint Table right-clicked opens its library ("Craft a Blueprint Table ... and right-click it": the Open
	 * packet for that table), however the player holds things, in survival and creative; and with a blueprint in the
	 * main or off hand.
	 */
	//$ gametest_batch AREA '"qa5TableClick"'
	@GameTest(template = AREA, batch = "qa5TableClick")
	public void blueprintTableOpensWhateverYouHold(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		BlockPos abs = helper.absolutePos(rel);
		for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
			Client c = client(helper, mode);
			List<Object> ways = new ArrayList<>(List.of(Hands.values()));
			ways.add("blueprint main");
			ways.add("blueprint off");
			for (Object way : ways) {
				if (way instanceof Hands hands) {
					hold(c.player(), hands);
				} else {
					hold(c.player(), Hands.EMPTY);
					c.player().setItemInHand(way.equals("blueprint main") ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
						BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), null));
				}
				c.sent();
				QaStageFourGameTests.press(helper, c.player(), rel);
				List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
				helper.assertTrue(opened.size() == 1 && opened.get(0).table().equals(abs),
					mode + " " + way + ": the table didn't open, sent " + opened + ", saw " + keys(c.seen()));
				helper.assertTrue(!opened.get(0).entries().isEmpty(), mode + " " + way + ": the library is empty");
				helper.assertBlockPresent(Blocks.AIR, rel.above());
			}
		}
		helper.succeed();
	}

	// --- The table's and the preview's packets --------------------------------------------------------------------

	/**
	 * The Stored tab's packets as a client sends them, honest and spoofed: Store from 30 blocks away does nothing; Store
	 * nearby shelves the held blueprint; a TakeStored claiming 64 hands back exactly the one stored; asked again it's
	 * "not stored" and nothing comes; a TakeStored of a diamond, or for a table position that isn't a table, gives
	 * nothing; with a full bag the blueprint stays on the shelf.
	 */
	//$ gametest_batch AREA '"qa5TableStored"'
	@GameTest(template = AREA, batch = "qa5TableStored")
	public void storedTabPacketsRefuseSpoofs(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.CHEST);
		BlockPos table = helper.absolutePos(rel);
		BlockPos chest = helper.absolutePos(new BlockPos(5, 2, 5));
		BlueprintTableBlockEntity shelf = (BlueprintTableBlockEntity) helper.getBlockEntity(rel);
		Client c = client(helper, GameType.SURVIVAL);
		ServerPlayer p = c.player();
		ItemStack bp = BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), null);
		p.getInventory().clearContent();
		p.getInventory().setItem(0, bp.copy());
		p.getInventory().selected = 0;
		Vec3 near = Vec3.atBottomCenterOf(table).add(1.5, 0, 0);
		p.moveTo(near.add(30, 0, 0));
		c.send(new TablePayloads.Store(table, false));
		c.send(new TablePayloads.Store(table, true));
		helper.assertTrue(TableLibrary.stored(shelf).isEmpty(), "stored from 30 blocks away");
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 1, "the blueprint left a far player's bag");
		p.moveTo(near);
		c.send(new TablePayloads.Store(chest, false));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 1, "stored into a chest's position");
		c.send(new TablePayloads.Store(table, false));
		helper.assertTrue(TableLibrary.stored(shelf).size() == 1 && TableLibrary.stored(shelf).get(0).count() == 1,
			"the held blueprint wasn't shelved: " + TableLibrary.stored(shelf));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 0, "the blueprint is both shelved and held");
		// A full bag: the take is refused and it stays on the shelf.
		QaStageFourGameTests.fill(p);
		c.send(new TablePayloads.TakeStored(table, bp.copyWithCount(1)));
		helper.assertTrue(TableLibrary.stored(shelf).size() == 1, "taken out into a full bag: " + TableLibrary.stored(shelf));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 0 && QaStageFourGameTests.dropped(helper, p, ModItems.BLUEPRINT).isEmpty(),
			"a blueprint came out into a full bag");
		p.getInventory().clearContent();
		// Spoofed: a diamond, a chest's position, far away, and 64 of the stored one.
		c.send(new TablePayloads.TakeStored(table, new ItemStack(Items.DIAMOND, 64)));
		helper.assertTrue(QaStageFourGameTests.count(p, Items.DIAMOND) == 0, "the table handed out diamonds");
		c.send(new TablePayloads.TakeStored(chest, bp.copyWithCount(1)));
		p.moveTo(near.add(30, 0, 0));
		c.send(new TablePayloads.TakeStored(table, bp.copyWithCount(1)));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 0 && TableLibrary.stored(shelf).size() == 1,
			"taken out from a chest's position or from 30 blocks away");
		p.moveTo(near);
		c.send(new TablePayloads.TakeStored(table, bp.copyWithCount(64)));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 1, "asking for 64 handed out "
			+ QaStageFourGameTests.count(p, ModItems.BLUEPRINT) + " (one was stored)");
		helper.assertTrue(TableLibrary.stored(shelf).isEmpty(), "still on the shelf after it was taken: " + TableLibrary.stored(shelf));
		c.seen().clear();
		c.send(new TablePayloads.TakeStored(table, bp.copyWithCount(1)));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 1, "a second blueprint came out of an empty shelf");
		helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.table.not_stored"), "not told it isn't stored: " + keys(c.seen()));
		helper.succeed();
	}

	/**
	 * The library's Take packet (README 0.x: "take a copy for a Blank Blueprint"): from 30 blocks away, or for a position
	 * that isn't a table, nothing and the blank kept; without a blank, refused ("need_blank"); an unknown or malformed id
	 * gives nothing; nearby with a blank, one blueprint for one blank; with a full bag of blanks, the blueprint lands at
	 * the player's feet, never lost; in creative, no blank needed.
	 */
	//$ gametest_batch AREA '"qa5TableTake"'
	@GameTest(template = AREA, batch = "qa5TableTake")
	public void takePacketChargesABlankAndRefusesSpoofs(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.CHEST);
		BlockPos table = helper.absolutePos(rel);
		BlockPos chest = helper.absolutePos(new BlockPos(5, 2, 5));
		ResourceLocation id = StarterBlueprints.MARKET_STALL.id();
		Client c = client(helper, GameType.SURVIVAL);
		ServerPlayer p = c.player();
		Vec3 near = Vec3.atBottomCenterOf(table).add(1.5, 0, 0);
		p.getInventory().clearContent();
		p.moveTo(near);
		c.seen().clear();
		c.send(new TablePayloads.Take(table, id));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 0, "a blueprint without a blank");
		helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.table.need_blank"), "not told a blank is needed: " + keys(c.seen()));
		p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 3));
		p.moveTo(near.add(30, 0, 0));
		c.send(new TablePayloads.Take(table, id));
		p.moveTo(near);
		c.send(new TablePayloads.Take(chest, id));
		c.send(new TablePayloads.Take(table, ResourceLocation.fromNamespaceAndPath("aliveworkplace", "no_such_build")));
		c.send(new TablePayloads.Take(table, ResourceLocation.fromNamespaceAndPath("aliveworkplace", "../../../level")));
		c.send(new TablePayloads.Take(table, ResourceLocation.fromNamespaceAndPath("minecraft", "")));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 0, "a blueprint from far away, a chest or a bad id");
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLANK_BLUEPRINT) == 3, "a blank was used for nothing: "
			+ QaStageFourGameTests.count(p, ModItems.BLANK_BLUEPRINT));
		c.send(new TablePayloads.Take(table, id));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 1 && QaStageFourGameTests.count(p, ModItems.BLANK_BLUEPRINT) == 2,
			"one take: " + QaStageFourGameTests.count(p, ModItems.BLUEPRINT) + " blueprints, " + QaStageFourGameTests.count(p, ModItems.BLANK_BLUEPRINT) + " blanks");
		helper.assertTrue(p.getInventory().items.stream().filter(s -> s.is(ModItems.BLUEPRINT)).findFirst()
			.flatMap(BlueprintItem::data).map(d -> d.structure().equals(id)).orElse(false), "the blueprint isn't the Market Stall");
		// A full bag: every slot holds 64 blanks; the blueprint goes to the player's feet.
		p.getInventory().clearContent();
		for (int i = 0; i < p.getInventory().items.size(); i++) {
			p.getInventory().items.set(i, new ItemStack(ModItems.BLANK_BLUEPRINT, 64));
		}
		int blanks = QaStageFourGameTests.count(p, ModItems.BLANK_BLUEPRINT);
		c.send(new TablePayloads.Take(table, id));
		int got = QaStageFourGameTests.count(p, ModItems.BLUEPRINT) + QaStageFourGameTests.dropped(helper, p, ModItems.BLUEPRINT).size();
		helper.assertTrue(got == 1, "with a full bag the blueprint was lost or doubled: " + got);
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLANK_BLUEPRINT) == blanks - 1, "a full bag: blanks " + blanks + " -> "
			+ QaStageFourGameTests.count(p, ModItems.BLANK_BLUEPRINT));
		// Creative: no blank.
		p.getInventory().clearContent();
		p.setGameMode(GameType.CREATIVE);
		c.send(new TablePayloads.Take(table, id));
		helper.assertTrue(QaStageFourGameTests.count(p, ModItems.BLUEPRINT) == 1, "creative: no blueprint without a blank");
		helper.succeed();
	}

	/**
	 * The detail and preview requests the table's screen sends: a known blueprint gets its details and its preview
	 * (for that id); an unknown, empty or path-like id gets nothing and breaks nothing; the same preview asked again at
	 * once isn't sent twice. A survival player's upload chunks with spoofed sizes and offsets are refused without
	 * an exception and give nothing.
	 */
	//$ gametest_batch AREA '"qa5Preview"'
	@GameTest(template = AREA, batch = "qa5Preview")
	public void detailAndPreviewRequestsSurviveSpoofs(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		BlockPos table = helper.absolutePos(rel);
		ResourceLocation id = StarterBlueprints.MARKET_STALL.id();
		Client c = client(helper, GameType.SURVIVAL);
		c.player().moveTo(Vec3.atBottomCenterOf(table).add(1.5, 0, 0));
		c.send(new TablePayloads.RequestDetails(id));
		List<CustomPacketPayload> sent = c.sent();
		helper.assertTrue(sent.stream().anyMatch(s -> s instanceof TablePayloads.Details d && d.id().equals(id)),
			"no details for the Market Stall: " + sent.stream().map(s -> s.type().id().toString()).toList());
		c.send(new PreviewNetworking.Request(id));
		List<PreviewNetworking.Data> previews = c.sent(PreviewNetworking.Data.class);
		helper.assertTrue(previews.size() == 1 && previews.get(0).id().equals(id) && previews.get(0).blocks().length > 0,
			"the Market Stall's preview: " + previews.size() + " sent");
		c.send(new PreviewNetworking.Request(id));
		helper.assertTrue(c.sent(PreviewNetworking.Data.class).isEmpty(), "the same preview was sent again at once");
		for (ResourceLocation bad : List.of(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "no_such_build"),
			ResourceLocation.fromNamespaceAndPath("aliveworkplace", "../../../level"), ResourceLocation.fromNamespaceAndPath("minecraft", ""))) {
			c.send(new TablePayloads.RequestDetails(bad));
			c.send(new PreviewNetworking.Request(bad));
			List<CustomPacketPayload> answer = c.sent();
			helper.assertTrue(answer.stream().noneMatch(s -> s instanceof PreviewNetworking.Data d && !d.id().equals(bad)),
				bad + ": another build's preview was sent");
		}
		int before = c.player().getInventory().countItem(ModItems.BLUEPRINT);
		for (int[] spoof : new int[][] {{Integer.MAX_VALUE, 0}, {-1, 0}, {16, -8}, {16, 1_000_000}, {0, 0}}) {
			c.send(new TablePayloads.UploadChunk(table, "x.nbt", spoof[0], spoof[1], new byte[] {1, 2, 3, 4}));
			c.send(new TablePayloads.UploadChunk(table, "../../evil.nbt", spoof[0], spoof[1], new byte[0]));
		}
		helper.assertTrue(c.player().getInventory().countItem(ModItems.BLUEPRINT) == before, "a spoofed upload gave a blueprint");
		List<TablePayloads.UploadResult> results = c.sent(TablePayloads.UploadResult.class);
		helper.assertTrue(!results.isEmpty() && results.stream().noneMatch(TablePayloads.UploadResult::ok),
			"spoofed uploads: " + results.size() + " answers, " + results.stream().filter(TablePayloads.UploadResult::ok).count() + " accepted");
		helper.assertTrue(!c.player().hasDisconnected(), "the spoofed packets disconnected the player");
		helper.succeed();
	}
	/**
	 * The mailbox's Send packet as a client sends it: from the owner with the mailbox open, no name ("Write who the
	 * parcel is for."), a name nobody has ("No player called %s has been on this server.", the name in it), nothing to
	 * send ("Write a letter or put what you want to send in the top row first."); from a player with no mailbox open,
	 * nothing happens and nothing is said.
	 */
	//$ gametest_batch AREA '"qa5MailSend"'
	@GameTest(template = AREA, batch = "qa5MailSend")
	public void mailSendPacketRefusesBadParcels(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		Client owner = client(helper, GameType.SURVIVAL);
		owner.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.MAILBOX));
		QaStageFourGameTests.press(helper, owner.player(), new BlockPos(3, 1, 3));
		BlockPos rel = new BlockPos(3, 2, 3);
		QaStageFourGameTests.press(helper, owner.player(), rel);
		helper.assertTrue(owner.player().containerMenu instanceof MailboxMenu, "the mailbox didn't open");
		owner.seen().clear();
		owner.send(new io.github.jcondedata.aliveworkplace.mail.Mail.Send("   ", "hello"));
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "message.aliveworkplace.mail.no_name"), "no name: saw " + keys(owner.seen()));
		owner.send(new io.github.jcondedata.aliveworkplace.mail.Mail.Send("NobodyQa5", "hello"));
		Component unknown = QaStageFourGameTests.find(owner.seen(), "message.aliveworkplace.mail.unknown");
		helper.assertTrue(unknown != null, "an unknown name: saw " + keys(owner.seen()));
		QaStageFourGameTests.assertRendered(helper, unknown, "NobodyQa5");
		owner.send(new io.github.jcondedata.aliveworkplace.mail.Mail.Send(owner.player().getGameProfile().getName(), "   "));
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "message.aliveworkplace.mail.nothing"), "nothing to send: saw " + keys(owner.seen()));
		closeScreen(owner.player());
		helper.runAfterDelay(3, () -> {
			Client other = client(helper, GameType.SURVIVAL);
			other.seen().clear();
			other.send(new io.github.jcondedata.aliveworkplace.mail.Mail.Send(owner.player().getGameProfile().getName(), "spoofed"));
			helper.assertTrue(other.seen().isEmpty(), "a Send with no mailbox open answered: " + keys(other.seen()));
			MailboxBlockEntity box = (MailboxBlockEntity) helper.getBlockEntity(rel);
			helper.assertTrue(!box.getBlockState().getValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.HAS_MAIL),
				"a Send with no mailbox open delivered mail");
			helper.succeed();
		});
	}
	// --- The Captain's Insignia handed in at the board -------------------------------------------------------------

	/**
	 * README: "He drops his Captain's Insignia. Hand it in at the board, at the hall (right-click with it) ...". A real
	 * captain is raised and killed, his dropped insignia picked up, and the Bounty Board right-clicked holding it in
	 * {@code hand} (the other hand empty): the bounty pays emeralds and the insignia is gone.
	 */
	static void insigniaHandedInAtTheBoard(GameTestHelper helper, InteractionHand hand) {
		insigniaHandedIn(helper, hand, false);
	}

	/** As {@link #insigniaHandedInAtTheBoard}, right-clicking the village hall instead when {@code atHall}. */
	static void insigniaHandedIn(GameTestHelper helper, InteractionHand hand, boolean atHall) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		int radius = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 16;
		io.github.jcondedata.aliveworkplace.bounty.Bounties.forget(level);
		BlockPos hallRel = new BlockPos(5, 2, 5);
		BlockPos hallAbs = helper.absolutePos(hallRel);
		Leftovers.after(helper, () -> {
			// the hall goes with the test, so a later test's "no hall near" board doesn't find it
			level.setBlockAndUpdate(hallAbs, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(10, 2, 5)), Blocks.AIR.defaultBlockState());
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.bounty.Bounties.forget(level);
		});
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		var hallEntity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hallRel));
		hallEntity.setLastQuestDay(Long.MAX_VALUE / 2);
		hallEntity.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE);
		BlockPos hall = helper.absolutePos(hallRel);
		BlockPos boardRel = new BlockPos(10, 2, 5);
		helper.setBlock(boardRel, ModBlocks.BOUNTY_BOARD);
		BlockPos camp = new BlockPos(18, 2, 18);
		var bounty = io.github.jcondedata.aliveworkplace.bounty.Bounties.post(level, hall,
			io.github.jcondedata.aliveworkplace.AliveWorkplace.id("hexer"), 0, 0, hall.offset(160, 0, 0));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.bounty.Bounties.raise(level, bounty, helper.absolutePos(camp),
			helper.absolutePos(camp.north(4))), "the captain didn't stand");
		net.minecraft.world.entity.Mob captain = level.getEntity(bounty.captain) instanceof net.minecraft.world.entity.Mob m ? m : null;
		helper.assertTrue(captain != null, "no captain");
		Client c = client(helper, GameType.SURVIVAL);
		captain.hurt(level.damageSources().playerAttack(c.player()), 1000f);
		helper.runAfterDelay(2, () -> {
			var drop = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(helper.absolutePos(camp)).inflate(6), e -> e.getItem().is(ModItems.CAPTAINS_INSIGNIA))
				.stream().findFirst().orElse(null);
			helper.assertTrue(drop != null, "no insignia dropped");
			ItemStack insignia = drop.getItem().copy();
			drop.discard();
			c.player().getInventory().clearContent();
			c.player().setItemInHand(hand, insignia);
			c.seen().clear();
			QaStageFourGameTests.press(helper, c.player(), atHall ? hallRel : boardRel);
			int emeralds = QaStageFourGameTests.count(c.player(), Items.EMERALD);
			helper.assertTrue(emeralds > 0 && QaStageFourGameTests.count(c.player(), ModItems.CAPTAINS_INSIGNIA) == 0,
				hand + ": the insignia right-clicked on the " + (atHall ? "hall" : "board") + " paid " + emeralds + " emeralds, insignia left "
					+ QaStageFourGameTests.count(c.player(), ModItems.CAPTAINS_INSIGNIA) + ", screen " + c.player().containerMenu.getClass().getSimpleName()
					+ ", saw " + keys(c.seen()));
			helper.succeed();
		});
	}

	/** The insignia in the main hand, right-clicked on the Bounty Board, is paid. */
	//$ gametest_ticks_batch HUGE '60' '"qa5InsigniaMain"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa5InsigniaMain")
	public void insigniaFromTheMainHandIsPaidAtTheBoard(GameTestHelper helper) {
		insigniaHandedInAtTheBoard(helper, InteractionHand.MAIN_HAND);
	}
	/** README: "Hand it in ... at the hall (right-click with it)": the insignia in the main hand on the hall is paid. */
	//$ gametest_ticks_batch HUGE '60' '"qa5InsigniaHall"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa5InsigniaHall")
	public void insigniaFromTheMainHandIsPaidAtTheHall(GameTestHelper helper) {
		insigniaHandedIn(helper, InteractionHand.MAIN_HAND, true);
	}
}
