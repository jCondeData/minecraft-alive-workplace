package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA stage 6 (2026-10-24): what stages 4 and 5 left unclicked that a player can reach. The Storehouse requests board's
 * own clicks, sent as the client's real container-click packets; {@code /workplace friend add} and {@code remove} with
 * a real profile cache, and what a friend may then do; a real blueprint upload, piece by piece, from a player who may
 * upload; and the Guild Charter and Gray Cough healer clicks through the real interact packet.
 *
 * <p>Expectations from README.md, docs/wiki and en_us.json, not the code: "clicking one hands over what you have
 * straight into that worker's chests" (README, requests board); "Only you, your friends (/workplace friend add) and
 * operators can open your mailbox"; "pick one and press Upload. It becomes a blueprint everyone on the server can use"
 * and "/gamerule workplaceAllowUploads false: only operators can upload blueprint files"; "sneak-right-click a Master
 * villager with it [a Guild Charter]" (guilds.md); "Talk to the healer (right-click them) ... Bring back 3 of each and
 * right-click the healer; you can hand them in a herb at a time" (the-sickness.md).
 */
public class QaStageSixGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final AtomicInteger NAMES = new AtomicInteger();

	/** A stage-5 client (a real connection whose packets are read back) with a name of its own. */
	static QaStageFiveGameTests.Client client(GameTestHelper helper, GameType mode, String name) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
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
		QaStageFiveGameTests.Client client = new QaStageFiveGameTests.Client(player, channel, seen);
		client.sent();
		return client;
	}

	static String name(String prefix) {
		return prefix + NAMES.incrementAndGet();
	}

	/** A click on slot {@code slot} of the open screen, as the client sends it. */
	static void click(ServerPlayer p, int slot, int button, ClickType type) {
		var menu = p.containerMenu;
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot, button, type, ItemStack.EMPTY,
			new Int2ObjectOpenHashMap<>()));
	}

	static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	static int slotOf(ChoiceMenu menu, net.minecraft.world.item.Item item) {
		for (int i = io.github.jcondedata.aliveworkplace.store.StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (menu.icon(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * A right-click on a villager as the client sends it: for the main hand, then the off hand, the click at a spot on
	 * them and then the plain click, stopping when something answered (a line shown or a screen opened).
	 */
	static void clickVillager(ServerPlayer p, Villager v, List<Component> seen) {
		p.moveTo(v.getX() + 1.5, v.getY(), v.getZ(), 90f, 0f);
		for (InteractionHand hand : InteractionHand.values()) {
			int before = seen.size();
			p.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(v, p.isShiftKeyDown(), hand, new Vec3(0, v.getBbHeight() / 2, 0)));
			if (seen.size() != before || p.containerMenu != p.inventoryMenu) {
				return;
			}
			p.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(v, p.isShiftKeyDown(), hand));
			if (seen.size() != before || p.containerMenu != p.inventoryMenu) {
				return;
			}
		}
	}

	// --- The Storehouse requests board ----------------------------------------------------------------------------

	/** A builder waiting for cobblestone at a hut (everything else in his chest), and the village's Storehouse. */
	record Waiting(Villager builder, BuildSite site, BlockPos chest, BlockPos storehouse) {
	}

	static Waiting waitingBuilder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chest = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		Container c = helper.getBlockEntity(chest);
		c.setItem(0, new ItemStack(Items.OAK_PLANKS, 55));
		c.setItem(1, new ItemStack(Items.OAK_DOOR));
		c.setItem(2, new ItemStack(Items.TORCH));
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, v, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, v, null, ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut"),
			new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)),
				Rotation.NONE, Mirror.NONE));
		BlockPos storehouse = new BlockPos(14, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		return new Waiting(v, site, chest, storehouse);
	}

	/**
	 * README: "Right-click the Storehouse to see what the workers nearby are waiting for ... clicking one hands over what
	 * you have straight into that worker's chests." Clicked with the client's real container-click packet: the builder's
	 * cobblestone is listed (25, "Click to give 25 of yours" for a player with 40); a left click hands 25 into his chest
	 * and the player keeps 15, told "Gave 25 Cobblestone"; nothing ends up on the cursor; the board stays open and the
	 * info book does nothing when clicked.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa6Board"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa6Board")
	public void boardClickHandsYourItemsToTheWorker(GameTestHelper helper) {
		Waiting w = waitingBuilder(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(w.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder isn't waiting: " + w.site().status());
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6b"));
			ServerPlayer p = c.player();
			p.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 40));
			QaStageFourGameTests.press(helper, p, w.storehouse());
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
			ChoiceMenu board = (ChoiceMenu) p.containerMenu;
			int slot = slotOf(board, Items.COBBLESTONE);
			helper.assertTrue(slot >= 0, "the board doesn't list the cobblestone");
			helper.assertTrue(board.icon(slot).getCount() == 25, "asks for " + board.icon(slot).getCount());
			helper.assertTrue(lore(board.icon(slot)).contains("Click to give 25 of yours"), "lore: " + lore(board.icon(slot)));
			// the info book isn't a button
			click(p, io.github.jcondedata.aliveworkplace.store.StorehouseBoard.INFO_SLOT, 0, ClickType.PICKUP);
			helper.assertTrue(p.getInventory().countItem(Items.WRITABLE_BOOK) == 0 && p.containerMenu.getCarried().isEmpty(), "the info book came out");
			c.seen().clear();
			click(p, slot, 0, ClickType.PICKUP);
			Container chest = helper.getBlockEntity(w.chest());
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 25, "the builder's chest got " + chest.countItem(Items.COBBLESTONE));
			helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 15, "the player kept " + p.getInventory().countItem(Items.COBBLESTONE));
			helper.assertTrue(p.containerMenu.getCarried().isEmpty(), "something on the cursor: " + p.containerMenu.getCarried());
			Component gave = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.storehouse.gave");
			helper.assertTrue(gave != null, "not told, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, gave, "25", "Cobblestone");
			helper.assertTrue(p.containerMenu == board, "the board closed after the click");
			helper.succeed();
		});
	}

	/**
	 * The board's buttons are pictures, not items: right-click, shift-click, a number key, Q and a creative middle-click on
	 * a request never put the icon in the player's hands, inventory or on the ground. With none of the item the click is
	 * "You have no Cobblestone to give" and nothing moves; a right- or shift-click with some hands it over like a click.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa6BoardIcons"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa6BoardIcons")
	public void boardIconsCannotBeTakenAndNoneSaysSo(GameTestHelper helper) {
		Waiting w = waitingBuilder(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(w.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder isn't waiting: " + w.site().status());
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6i"));
			ServerPlayer p = c.player();
			QaStageFourGameTests.press(helper, p, w.storehouse());
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
			ChoiceMenu board = (ChoiceMenu) p.containerMenu;
			int slot = slotOf(board, Items.COBBLESTONE);
			helper.assertTrue(slot >= 0, "the board doesn't list the cobblestone");
			helper.assertTrue(lore(board.icon(slot)).contains("You have none"), "lore with none: " + lore(board.icon(slot)));
			c.seen().clear();
			click(p, slot, 0, ClickType.PICKUP);
			Component none = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.storehouse.none");
			helper.assertTrue(none != null, "not told he has none, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, none, "Cobblestone");
			for (Object[] how : new Object[][] {{1, ClickType.PICKUP}, {0, ClickType.QUICK_MOVE}, {0, ClickType.SWAP}, {40, ClickType.SWAP}, {0, ClickType.THROW},
				{1, ClickType.THROW}, {2, ClickType.CLONE}, {0, ClickType.PICKUP_ALL}}) {
				for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
					p.setGameMode(mode);
					click(p, slot, (Integer) how[0], (ClickType) how[1]);
					String what = mode + " " + how[1] + " " + how[0];
					helper.assertTrue(p.containerMenu.getCarried().isEmpty(), what + ": the icon is on the cursor");
					helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 0, what + ": the icon is in the inventory");
					helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(6)).isEmpty(), what + ": something was dropped");
				}
			}
			p.setGameMode(GameType.SURVIVAL);
			Container chest = helper.getBlockEntity(w.chest());
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "cobblestone appeared in the chest from nothing: " + chest.countItem(Items.COBBLESTONE));
			// with some: a right-click and a shift-click hand over too
			p.getInventory().setItem(7, new ItemStack(Items.COBBLESTONE, 10));
			click(p, slot, 1, ClickType.PICKUP);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 10 && p.getInventory().countItem(Items.COBBLESTONE) == 0,
				"a right-click gave " + chest.countItem(Items.COBBLESTONE));
			p.getInventory().setItem(7, new ItemStack(Items.COBBLESTONE, 20));
			int s2 = slotOf(board, Items.COBBLESTONE);
			helper.assertTrue(s2 >= 0, "the cobblestone left the board with 15 still missing");
			click(p, s2, 0, ClickType.QUICK_MOVE);
			// (the builder hasn't looked again yet, so the board may still ask for all 25: what's handed over is up to that)
			int handed = 20 - p.getInventory().countItem(Items.COBBLESTONE);
			helper.assertTrue(handed > 0 && handed <= 25 && chest.countItem(Items.COBBLESTONE) == 10 + handed,
				"a shift-click: chest " + chest.countItem(Items.COBBLESTONE) + ", kept " + p.getInventory().countItem(Items.COBBLESTONE));
			helper.succeed();
		});
	}

	/**
	 * The worker's chests full: the click hands nothing over and says "(or their chests are full)"; the player keeps all.
	 * Walking away closes the board (a screen that stays usable from afar would hand things over from anywhere).
	 */
	//$ gametest_ticks_batch AREA '600' '"qa6BoardFull"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa6BoardFull")
	public void boardWithTheWorkersChestFullKeepsYourItems(GameTestHelper helper) {
		Waiting w = waitingBuilder(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(w.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder isn't waiting: " + w.site().status());
			Container chest = helper.getBlockEntity(w.chest());
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).isEmpty()) {
					chest.setItem(i, new ItemStack(Items.DIRT, 64));
				}
			}
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6f"));
			ServerPlayer p = c.player();
			p.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 40));
			QaStageFourGameTests.press(helper, p, w.storehouse());
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
			int slot = slotOf((ChoiceMenu) p.containerMenu, Items.COBBLESTONE);
			helper.assertTrue(slot >= 0, "the board doesn't list the cobblestone");
			c.seen().clear();
			click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 40, "the player lost " + (40 - p.getInventory().countItem(Items.COBBLESTONE)));
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "a full chest took " + chest.countItem(Items.COBBLESTONE));
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.storehouse.none"), "not told, saw " + QaStageFourGameTests.keys(c.seen()));
			p.moveTo(Vec3.atCenterOf(helper.absolutePos(w.storehouse())).add(20, 0, 0));
			helper.assertFalse(p.containerMenu.stillValid(p), "the board stays usable 20 blocks away");
			helper.succeed();
		});
	}

	/**
	 * The Stock orders page from the board: "Hold an item and click it (top right) to order it; click an order to keep
	 * more". Holding bread: the orders book opens the page, the bread button orders it, the order is listed, and a click
	 * on it keeps more; the back arrow returns to the requests. The bread stays in the player's hand.
	 */
	//$ gametest_batch AREA '"qa6Orders"'
	@GameTest(template = AREA, batch = "qa6Orders")
	public void boardStockOrdersFromTheHeldItem(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos storehouse = new BlockPos(3, 2, 3);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		Leftovers.after(helper, () -> helper.setBlock(storehouse, Blocks.AIR));
		QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6o"));
		ServerPlayer p = c.player();
		QaStageFourGameTests.press(helper, p, storehouse);
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
		ChoiceMenu board = (ChoiceMenu) p.containerMenu;
		int orders = io.github.jcondedata.aliveworkplace.store.StorehouseBoard.ORDERS_SLOT;
		helper.assertTrue(board.icon(orders).getHoverName().getString().equals("Stock orders (0)"), "orders book: " + board.icon(orders).getHoverName().getString());
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3));
		click(p, orders, 0, ClickType.PICKUP);
		helper.assertTrue(board.icon(orders).is(Items.BREAD), "no button to order the bread in hand: " + board.icon(orders));
		click(p, orders, 0, ClickType.PICKUP);
		helper.assertTrue(p.getMainHandItem().is(Items.BREAD) && p.getMainHandItem().getCount() == 3, "the bread in hand: " + p.getMainHandItem());
		int at = slotOf(board, Items.BREAD);
		helper.assertTrue(at >= 0, "the bread order isn't listed");
		String first = board.icon(at).getHoverName().getString();
		click(p, at, 0, ClickType.PICKUP);
		int again = slotOf(board, Items.BREAD);
		helper.assertTrue(again >= 0 && !board.icon(again).getHoverName().getString().equals(first),
			"clicking the order didn't keep more: " + first + " -> " + (again < 0 ? "gone" : board.icon(again).getHoverName().getString()));
		click(p, io.github.jcondedata.aliveworkplace.store.StorehouseBoard.BACK_SLOT, 0, ClickType.PICKUP);
		helper.assertTrue(board.icon(orders).getHoverName().getString().equals("Stock orders (1)"), "back on the board: " + board.icon(orders).getHoverName().getString());
		helper.assertTrue(p.containerMenu.getCarried().isEmpty() && p.getInventory().countItem(Items.BREAD) == 3, "the bread moved");
		helper.succeed();
	}

	// --- /workplace friend add and remove -------------------------------------------------------------------------

	/** Gives the GameTest server a profile cache (it has none), so player names can be looked up as on a real server. */
	static GameProfileCache profileCache(GameTestHelper helper, ServerPlayer... players) {
		MinecraftServer server = helper.getLevel().getServer();
		try {
			java.lang.reflect.Field field = MinecraftServer.class.getDeclaredField("services");
			field.setAccessible(true);
			Services old = (Services) field.get(server);
			File file = File.createTempFile("qa6-usercache", ".json");
			file.deleteOnExit();
			com.mojang.authlib.GameProfileRepository nobody = (names, callback) -> {
				for (String n : names) {
					callback.onProfileLookupFailed(n, new com.mojang.authlib.yggdrasil.ProfileNotFoundException());
				}
			};
			GameProfileCache cache = new GameProfileCache(nobody, file);
			field.set(server, new Services(old.sessionService(), old.servicesKeySet(), old.profileRepository(), cache));
			Leftovers.after(helper, () -> {
				try {
					field.set(server, old);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			});
			for (ServerPlayer p : players) {
				cache.add(p.getGameProfile());
			}
			return cache;
		} catch (ReflectiveOperationException | java.io.IOException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("can't give the server a profile cache: " + e);
		}
	}

	static void command(QaStageFiveGameTests.Client c, String command) {
		c.seen().clear();
		c.player().server.getCommands().performPrefixedCommand(c.player().createCommandSourceStack(), command);
	}

	/**
	 * README: "Only you, your friends (/workplace friend add) and operators can open your mailbox". A survival player who
	 * isn't an operator types the commands. Before: the other player is refused at the owner's mailbox. "friend add" says
	 * "X can now give orders to your builders and cancel your builds." and the friend opens it; adding again says "already",
	 * adding yourself "Your builders already work for you."; the list names the friend; "friend remove" says "X can no
	 * longer give orders" and the friend is refused again; removing again says "X is not on your list."
	 */
	//$ gametest_ticks_batch AREA '100' '"qa6Friends"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa6Friends")
	public void friendAddAndRemoveDecideWhoOpensYourMailbox(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		QaStageFiveGameTests.Client owner = client(helper, GameType.SURVIVAL, name("qa6owner"));
		QaStageFiveGameTests.Client friend = client(helper, GameType.SURVIVAL, name("qa6friend"));
		helper.assertFalse(owner.player().hasPermissions(2), "the owner is an operator; the check would prove nothing");
		String friendName = friend.player().getGameProfile().getName();
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.build.Friends.get(helper.getLevel().getServer())
			.remove(owner.player().getUUID(), friend.player().getUUID()));
		profileCache(helper, owner.player(), friend.player());
		owner.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.MAILBOX));
		QaStageFourGameTests.press(helper, owner.player(), new BlockPos(3, 1, 3));
		BlockPos box = new BlockPos(3, 2, 3);
		helper.assertBlockPresent(ModBlocks.MAILBOX, box);

		friend.seen().clear();
		QaStageFourGameTests.press(helper, friend.player(), box);
		helper.assertTrue(friend.player().containerMenu == friend.player().inventoryMenu, "a stranger opened the mailbox");

		command(owner, "/workplace friend add " + friendName);
		Component added = QaStageFourGameTests.find(owner.seen(), "command.aliveworkplace.friend.added");
		helper.assertTrue(added != null, "friend add: saw " + owner.seen().stream().map(Component::getString).toList());
		QaStageFourGameTests.assertRendered(helper, added, friendName, "can now give orders");
		QaStageFourGameTests.press(helper, friend.player(), box);
		helper.assertTrue(friend.player().containerMenu instanceof MailboxMenu, "the friend can't open the mailbox: " + friend.player().containerMenu);
		QaStageFiveGameTests.closeScreen(friend.player());

		command(owner, "/workplace friend add " + friendName);
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "command.aliveworkplace.friend.already"), "added twice: " + owner.seen().stream().map(Component::getString).toList());
		command(owner, "/workplace friend add " + owner.player().getGameProfile().getName());
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "command.aliveworkplace.friend.self"), "added himself: " + owner.seen().stream().map(Component::getString).toList());
		command(owner, "/workplace friend list");
		Component list = QaStageFourGameTests.find(owner.seen(), "command.aliveworkplace.friend.list");
		helper.assertTrue(list != null, "list: " + owner.seen().stream().map(Component::getString).toList());
		QaStageFourGameTests.assertRendered(helper, list, friendName);

		command(owner, "/workplace friend remove " + friendName);
		Component removed = QaStageFourGameTests.find(owner.seen(), "command.aliveworkplace.friend.removed");
		helper.assertTrue(removed != null, "friend remove: saw " + owner.seen().stream().map(Component::getString).toList());
		QaStageFourGameTests.assertRendered(helper, removed, friendName);
		friend.seen().clear();
		QaStageFourGameTests.press(helper, friend.player(), box);
		helper.assertTrue(friend.player().containerMenu == friend.player().inventoryMenu, "a removed friend still opens the mailbox");
		helper.assertTrue(QaStageFourGameTests.saw(friend.seen(), "message.aliveworkplace.mail.not_yours"), "not told, saw " + QaStageFourGameTests.keys(friend.seen()));
		command(owner, "/workplace friend remove " + friendName);
		Component notFound = QaStageFourGameTests.find(owner.seen(), "command.aliveworkplace.friend.not_found");
		helper.assertTrue(notFound != null, "removed twice: " + owner.seen().stream().map(Component::getString).toList());
		QaStageFourGameTests.assertRendered(helper, notFound, friendName);
		command(owner, "/workplace friend list");
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "command.aliveworkplace.friend.none"), "list after: " + owner.seen().stream().map(Component::getString).toList());
		helper.succeed();
	}

	/**
	 * A friend added while offline (by name, from the cache, as on a real server) counts once they come online; and a
	 * friend list survives the server's save (Friends is saved data): the friend's name is still listed afterwards.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa6FriendsSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa6FriendsSaved")
	public void aFriendAddedByNameIsKeptOverASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client owner = client(helper, GameType.SURVIVAL, name("qa6keep"));
		com.mojang.authlib.GameProfile away = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name("qa6away"));
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.build.Friends.get(helper.getLevel().getServer()).remove(owner.player().getUUID(), away.getId()));
		GameProfileCache cache = profileCache(helper, owner.player());
		cache.add(away);
		command(owner, "/workplace friend add " + away.getName());
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "command.aliveworkplace.friend.added"), "add an offline player: " + owner.seen().stream().map(Component::getString).toList());
		var friends = io.github.jcondedata.aliveworkplace.build.Friends.get(helper.getLevel().getServer());
		helper.assertTrue(friends.mayDirect(owner.player().getUUID(), away.getId()), "the offline friend may not direct the owner's workers");
		var tag = friends.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
		String saved = tag.toString();
		helper.assertTrue(saved.contains(away.getName()), "the saved list doesn't name the friend: " + saved);
		helper.succeed();
	}

	// --- A real blueprint upload ----------------------------------------------------------------------------------

	static void upload(QaStageFiveGameTests.Client c, BlockPos table, String file, byte[] bytes, int piece) {
		for (int offset = 0; offset < bytes.length; offset += piece) {
			c.send(new TablePayloads.UploadChunk(table, file, bytes.length, offset, Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + piece))));
		}
	}

	/**
	 * README: "pick one and press Upload. It becomes a blueprint everyone on the server can use." A survival player who
	 * isn't an operator, at the table, with uploads allowed (the default): the table's library says he may upload; the
	 * file goes up in the client's pieces; the answer is a success naming the new blueprint, the library is sent again
	 * with it, and another player takes a copy of it for a Blank Blueprint.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa6Upload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa6Upload")
	public void aPlayerUploadsABlueprintEveryoneCanUse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		BlockPos table = helper.absolutePos(rel);
		String uploader = name("qa6up");
		ImportGameTests.clearGenerated(helper, "uploads/" + uploader);
		Leftovers.after(helper, () -> ImportGameTests.clearGenerated(helper, "uploads/" + uploader));
		QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, uploader);
		helper.assertFalse(c.player().hasPermissions(2), "the uploader is an operator; the check would prove nothing");
		QaStageFourGameTests.press(helper, c.player(), rel);
		List<TablePayloads.Open> open = c.sent(TablePayloads.Open.class);
		helper.assertTrue(open.size() == 1 && open.get(0).canUpload(), "the table doesn't offer the upload: " + open);
		upload(c, table, "QA Six Hut.litematic", ImportGameTests.bytes("hut.litematic"), 1000);
		List<net.minecraft.network.protocol.common.custom.CustomPacketPayload> back = c.sent();
		List<TablePayloads.UploadResult> results = back.stream().filter(TablePayloads.UploadResult.class::isInstance).map(TablePayloads.UploadResult.class::cast).toList();
		helper.assertTrue(results.size() == 1 && results.get(0).ok() && results.get(0).id().isPresent(), "the upload's answer: " + results);
		ResourceLocation id = results.get(0).id().get();
		helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), id).isPresent(), "not in the library: " + id);
		QaStageFourGameTests.assertRendered(helper, results.get(0).message());
		List<TablePayloads.Open> again = back.stream().filter(TablePayloads.Open.class::isInstance).map(TablePayloads.Open.class::cast).toList();
		helper.assertTrue(!again.isEmpty() && again.get(again.size() - 1).entries().stream().anyMatch(e -> e.id().equals(id)), "the library sent back doesn't list " + id);
		helper.runAfterDelay(3, () -> {
			QaStageFiveGameTests.Client other = client(helper, GameType.SURVIVAL, name("qa6other"));
			other.player().getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT));
			QaStageFourGameTests.press(helper, other.player(), rel);
			other.sent();
			other.send(new TablePayloads.Take(table, id));
			helper.assertTrue(other.player().getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0, "the blank wasn't used");
			boolean got = false;
			for (ItemStack s : other.player().getInventory().items) {
				got |= s.is(ModItems.BLUEPRINT) && io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(s).map(d -> d.structure().equals(id)).orElse(false);
			}
			helper.assertTrue(got, "another player didn't get the uploaded blueprint");
			helper.succeed();
		});
	}

	/**
	 * "/gamerule workplaceAllowUploads false: only operators can upload blueprint files". With it off, a player who isn't
	 * an operator is told uploading is off (the table says so before he tries) and nothing is made; an operator still
	 * uploads.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa6UploadOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa6UploadOff")
	public void withUploadsOffOnlyOperatorsUpload(GameTestHelper helper) {
		Leftovers.clear(helper);
		MinecraftServer server = helper.getLevel().getServer();
		var rule = helper.getLevel().getGameRules().getRule(ModGameRules.ALLOW_UPLOADS);
		rule.set(false, server);
		Leftovers.after(helper, () -> rule.set(true, server));
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		BlockPos table = helper.absolutePos(rel);
		String player = name("qa6no");
		String op = name("qa6op");
		Leftovers.after(helper, () -> {
			ImportGameTests.clearGenerated(helper, "uploads/" + player);
			ImportGameTests.clearGenerated(helper, "uploads/" + op);
		});
		QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, player);
		QaStageFourGameTests.press(helper, c.player(), rel);
		List<TablePayloads.Open> open = c.sent(TablePayloads.Open.class);
		helper.assertTrue(open.size() == 1 && !open.get(0).canUpload(), "the table offers the upload with uploads off: " + open);
		byte[] hut = ImportGameTests.bytes("hut.litematic");
		upload(c, table, "Forbidden Hut.litematic", hut, 1000);
		List<TablePayloads.UploadResult> results = c.sent(TablePayloads.UploadResult.class);
		helper.assertTrue(!results.isEmpty() && results.stream().noneMatch(TablePayloads.UploadResult::ok), "uploaded with uploads off: " + results);
		helper.assertTrue(results.get(0).message().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
			&& t.getKey().equals("message.aliveworkplace.table.upload_denied"), "told: " + results.get(0).message().getString());
		helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), AliveWorkplace.id("uploads/" + player + "/forbidden_hut")).isEmpty(), "a blueprint was made");
		helper.runAfterDelay(3, () -> {
			QaStageFiveGameTests.Client o = client(helper, GameType.SURVIVAL, op);
			server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(o.player().getGameProfile(), 4, false));
			Leftovers.after(helper, () -> server.getPlayerList().deop(o.player().getGameProfile()));
			helper.assertTrue(o.player().hasPermissions(2), "not an operator");
			QaStageFourGameTests.press(helper, o.player(), rel);
			List<TablePayloads.Open> opOpen = o.sent(TablePayloads.Open.class);
			helper.assertTrue(!opOpen.isEmpty() && opOpen.get(0).canUpload(), "the table doesn't offer an operator the upload: " + opOpen);
			upload(o, table, "Op Hut.litematic", hut, TablePayloads.UPLOAD_CHUNK);
			List<TablePayloads.UploadResult> opResults = o.sent(TablePayloads.UploadResult.class);
			helper.assertTrue(opResults.size() == 1 && opResults.get(0).ok(), "an operator couldn't upload: " + opResults);
			helper.succeed();
		});
	}

	// --- Guild Charter on a Master ----------------------------------------------------------------------------------

	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	static VillageHallBlockEntity guildVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	static void ready(VillageHallBlockEntity hall, VillageRanks.Rank rank) {
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(rank);
		hall.setGuilds(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Guilds.forget();
		Moods.forget();
	}

	static Villager guildVillager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	/**
	 * guilds.md: "sneak-right-click a Master villager with it. They become the Guild Master of their trade's guild, and
	 * everyone is told". Through the real interact packet, in survival: a Journeyman builder is refused ("isn't one yet"),
	 * the charter kept, no trade screen; then the Master builder is chartered, the line rendered with her name, one charter
	 * used, no trade screen. In creative, a second village's charter on a Master isn't used up.
	 */
	//$ gametest_ticks_batch BIG '100' '"qa6Guild"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa6Guild")
	public void guildCharterSneakClickedOnAMaster(GameTestHelper helper) {
		VillageHallBlockEntity hall = guildVillage(helper);
		Villager jo = guildVillager(helper, new BlockPos(6, 2, 6), "Jo", ModVillagers.BUILDER, 3);
		Villager dara = guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6g"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 3));
			clickVillager(p, jo, c.seen());
			Component refused = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.guild.not_master");
			helper.assertTrue(refused != null, "not told Jo isn't a Master, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, refused, "Jo");
			helper.assertTrue(p.getMainHandItem().getCount() == 3, "a refused charter was used");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "a trade screen opened on the refusal");
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(jo) == null, "Jo leads a guild");

			c.seen().clear();
			clickVillager(p, dara, c.seen());
			Component chartered = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.guild.chartered");
			helper.assertTrue(chartered != null, "not told Dara was chartered, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, chartered, "Dara", "Guild Master", "Builders' Guild");
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(dara) != null, "Dara isn't a Guild Master");
			helper.assertTrue(p.getMainHandItem().is(ModItems.GUILD_CHARTER) && p.getMainHandItem().getCount() == 2, "charters left: " + p.getMainHandItem());
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "a trade screen opened on the charter");
			helper.succeed();
		});
	}

	/** In creative a charter isn't used up. */
	//$ gametest_ticks_batch BIG '100' '"qa6GuildHands"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa6GuildHands")
	public void guildCharterInCreative(GameTestHelper helper) {
		VillageHallBlockEntity hall = guildVillage(helper);
		Villager dara = guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = client(helper, GameType.CREATIVE, name("qa6gc"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 1));
			clickVillager(p, dara, c.seen());
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(dara) != null, "creative: Dara isn't chartered, saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getMainHandItem().is(ModItems.GUILD_CHARTER), "creative: the charter was used up");
			helper.succeed();
		});
	}

	/**
	 * QA stage 6 (B341): the charter from the off hand (main hand empty), sneak-clicked on a Master in survival, charters her
	 * and is used up, as from the main hand; the builder's status line doesn't take the click.
	 */
	//$ gametest_ticks_batch BIG '100' '"qa6GuildOffHand"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa6GuildOffHand")
	public void guildCharterFromTheOffHand(GameTestHelper helper) {
		VillageHallBlockEntity hall = guildVillage(helper);
		Villager dara = guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6go"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GUILD_CHARTER, 2));
			clickVillager(p, dara, c.seen());
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(dara) != null, "off hand: Dara isn't chartered, saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getOffhandItem().getCount() == 1, "off hand: charters left " + p.getOffhandItem());
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a trade screen opened");
			helper.succeed();
		});
	}

	/**
	 * B341's twin: the Evergreen Charm from the off hand (main hand empty), sneak-clicked on an elder Master builder in
	 * survival, makes him ageless and is used up, as from the main hand; the builder's status line doesn't take the click.
	 */
	//$ gametest_ticks_batch BIG '100' '"qa6CharmOffHand"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa6CharmOffHand")
	public void evergreenCharmFromTheOffHand(GameTestHelper helper) {
		VillageHallBlockEntity hall = guildVillage(helper);
		Villager bram = guildVillager(helper, new BlockPos(8, 2, 6), "Bram", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			ready(hall, VillageRanks.Rank.VILLAGE);
			io.github.jcondedata.aliveworkplace.people.LifeStages.setAdultSince(bram,
				io.github.jcondedata.aliveworkplace.hall.Chronicle.day(helper.getLevel()) - 1300);
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6co"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.EVERGREEN_CHARM, 2));
			clickVillager(p, bram, c.seen());
			helper.assertTrue(io.github.jcondedata.aliveworkplace.people.LifeStages.isAgeless(bram),
				"off hand: Bram isn't ageless, saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getOffhandItem().getCount() == 1, "off hand: charms left " + p.getOffhandItem());
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a trade screen opened");
			helper.succeed();
		});
	}

	/** B341's twin: Builder's Tea from the off hand (main hand empty), right-clicked on a builder, is drunk and used up; no trade screen. */
	//$ gametest_ticks_batch BIG '100' '"qa6TonicOffHand"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa6TonicOffHand")
	public void tonicFromTheOffHand(GameTestHelper helper) {
		VillageHallBlockEntity hall = guildVillage(helper);
		Villager dara = guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 2);
		helper.runAfterDelay(5, () -> {
			ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6to"));
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BUILDERS_TEA, 2));
			clickVillager(p, dara, c.seen());
			helper.assertTrue(ModAttachments.TONIC.has(dara), "off hand: Dara didn't drink the tea, saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getOffhandItem().getCount() == 1, "off hand: tea left " + p.getOffhandItem());
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a trade screen opened");
			helper.succeed();
		});
	}

	/** B341's twin: a Remedy from the off hand (main hand empty), right-clicked on a villager, keeps them well and is used up, the bottle back; no trade screen. */
	//$ gametest_ticks_batch BIG '100' '"qa6RemedyOffHand"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa6RemedyOffHand")
	public void remedyFromTheOffHand(GameTestHelper helper) {
		VillageHallBlockEntity hall = guildVillage(helper);
		Villager dara = guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 2);
		helper.runAfterDelay(5, () -> {
			ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6ro"));
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.REMEDY, 2));
			clickVillager(p, dara, c.seen());
			helper.assertTrue(QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.remedy.kept_well") != null,
				"off hand: the Remedy wasn't given, saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getOffhandItem().getCount() == 1, "off hand: remedies left " + p.getOffhandItem());
			helper.assertTrue(p.getInventory().countItem(net.minecraft.world.item.Items.GLASS_BOTTLE) == 1, "off hand: no bottle back");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a trade screen opened");
			helper.succeed();
		});
	}

	/** B341's twin: a Gift from the off hand (main hand empty), right-clicked on a named villager of the village, is given; no trade screen. */
	//$ gametest_ticks_batch BIG '100' '"qa6GiftOffHand"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa6GiftOffHand")
	public void giftFromTheOffHand(GameTestHelper helper) {
		VillageHallBlockEntity hall = guildVillage(helper);
		Villager dara = guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 2);
		helper.runAfterDelay(5, () -> {
			ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6gf"));
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.OFF_HAND, io.github.jcondedata.aliveworkplace.story.Gifts.wrap(
				new ItemStack(net.minecraft.world.item.Items.BREAD), p.getGameProfile().getName()));
			clickVillager(p, dara, c.seen());
			helper.assertTrue(p.getOffhandItem().isEmpty(), "off hand: the Gift wasn't given, still " + p.getOffhandItem()
				+ ", saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(io.github.jcondedata.aliveworkplace.story.Friendship.of(dara).bond(p.getUUID()).giftDay() >= 0,
				"off hand: no gift on record");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a trade screen opened");
			helper.succeed();
		});
	}

	// --- The Gray Cough's healer -------------------------------------------------------------------------------------

	private static final BlockPos SICK_HALL = new BlockPos(15, 2, 15);

	/**
	 * the-sickness.md: "Talk to the healer (right-click them) ... gives you a map to a patch of each. Bring back 3 of each
	 * and right-click the healer; you can hand them in a herb at a time". Through the real interact packet, holding a
	 * sword (the herbs are in the bag, as a player carries them): the first click gives three maps; a click with only
	 * two herbs' worth hands those in and the brew waits; the third herb's click starts it. No trade screen opens.
	 */
	//$ gametest_batch TALL '"qa6Healer"'
	@GameTest(template = TALL, batch = "qa6Healer")
	public void theHealerIsTalkedToAndTakesTheHerbsByClicks(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean sick = Sickness.ENABLED;
		SicknessStory.Locator locator = SicknessStory.LOCATOR;
		BlockPos hall = helper.absolutePos(SICK_HALL);
		VillageHalls.RADIUS = 12;
		Arcs.NEAR = 0;
		Sickness.ENABLED = true;
		SicknessStory.LOCATOR = (l, from, herb) -> {
			String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(herb).getPath();
			return switch (path) {
				case "sea_lavender" -> from.offset(200, 0, 0);
				case "mirewort" -> from.offset(400, 0, 0);
				case "glowcap" -> from.offset(600, 0, 0);
				default -> null;
			};
		};
		Leftovers.after(helper, () -> {
			Arcs.stopAll(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Sickness.ENABLED = sick;
			SicknessStory.LOCATOR = locator;
		});
		helper.setBlock(SICK_HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		for (int i = 0; i < 8; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(11 + (i % 4) * 3, 2, 12 + (i / 4) * 3));
			v.setNoAi(true);
			v.setCustomName(Component.literal("Villager " + (i + 1)));
		}
		BlockPos station = new BlockPos(20, 2, 20);
		helper.setBlock(station, Blocks.BREWING_STAND);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(21, 2, 20));
		nurse.setNoAi(true);
		nurse.setCustomName(Component.literal("Nurse Ada"));
		Jobs.employ(level, nurse, helper.absolutePos(station), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
		nurse.setVillagerXp(70);
		level.setDayTime(Math.floorDiv(level.getDayTime(), VillageNeeds.DAY) * VillageNeeds.DAY + VillageNeeds.DAY + 1000);

		ArcState s = Arcs.start(level, hall, Arcs.find("the_sickness").orElseThrow());
		helper.assertTrue(s != null, "the arc didn't start");
		QaStageFiveGameTests.Client c = client(helper, GameType.SURVIVAL, name("qa6h"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		clickVillager(p, nurse, c.seen());
		helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 3, "maps after talking to the healer: " + p.getInventory().countItem(Items.FILLED_MAP)
			+ ", saw " + QaStageFourGameTests.keys(c.seen()));
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "a trade screen opened on the healer");
		List<HerbBlock> herbs = SicknessStory.herbs(s);
		helper.assertTrue(herbs.size() == 3, "herbs: " + herbs);
		// (each click a few ticks apart, as a player clicks: one click's two packets in one tick count once)
		helper.runAfterDelay(3, () -> {
			p.getInventory().add(new ItemStack(herbs.get(0), 3));
			p.getInventory().add(new ItemStack(herbs.get(1), 3));
			c.seen().clear();
			clickVillager(p, nurse, c.seen());
			helper.assertTrue(p.getInventory().countItem(herbs.get(0).asItem()) == 0 && p.getInventory().countItem(herbs.get(1).asItem()) == 0,
				"two herbs weren't taken from the bag with a sword in hand: " + p.getInventory().countItem(herbs.get(0).asItem()) + " left, chapter " + s.chapter
					+ ", saw " + c.seen().stream().map(Component::getString).toList());
			helper.assertTrue(s.chapter == 1, "the brew began with a herb missing: chapter " + s.chapter);
		});
		helper.runAfterDelay(6, () -> {
			p.getInventory().add(new ItemStack(herbs.get(2), 5));
			clickVillager(p, nurse, c.seen());
			helper.assertTrue(p.getInventory().countItem(herbs.get(2).asItem()) == 2, "the third herb: kept " + p.getInventory().countItem(herbs.get(2).asItem()) + " of 5");
			helper.assertTrue(s.chapter == 2 && SicknessStory.busy(level, nurse), "the brew didn't begin: chapter " + s.chapter);
			helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 3, "more maps on later clicks");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "a trade screen opened on the healer");
		});
		helper.runAfterDelay(9, () -> {
			// a click while she brews tells how far along it is ("The tonic is brewing: N% done."), nothing more
			c.seen().clear();
			clickVillager(p, nurse, c.seen());
			helper.assertTrue(c.seen().stream().anyMatch(m -> m.getString().contains("The tonic is brewing")), "the brewing talk: saw "
				+ c.seen().stream().map(Component::getString).toList());
			helper.assertTrue(s.chapter == 2 && p.getInventory().countItem(herbs.get(2).asItem()) == 2, "a click while brewing changed something");
			helper.succeed();
		});
	}
}
