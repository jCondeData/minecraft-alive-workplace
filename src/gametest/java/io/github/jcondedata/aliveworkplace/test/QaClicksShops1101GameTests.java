package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-01) of six landed click and shop fixes, written from each bug's Expected text and the guides, not from the
 * fixes: B336 (the counter's sales log "whatever you have in your hand", shop-counter.md), B338 (the Captain's Insignia
 * from either hand), B341 (the Guild Charter from either hand), B348 (what's for sale is what the shop's chests hold),
 * B349 (only the owner, friends and operators change the prices) and B360 (the City Plan, Village Ledger and Name Tag
 * from either hand at the hall). Each test covers a case the fix's own tests don't: other people at the counter, both
 * hands full, a refused or repeated hand-in, stock moved or counted twice, other automation, and the lines a player
 * reads.
 */
public class QaClicksShops1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Pattern RAW_KEY = Pattern.compile("\\b[a-z_]+\\.aliveworkplace\\.[a-z_.0-9]+");

	// --- Shared helpers ----------------------------------------------------------------------------------------------

	/** A real player (connection and all) whose lines are kept in {@code seen}; {@code op} answers as an operator. */
	private static ServerPlayer player(GameTestHelper helper, List<Component> seen, String name, boolean op) {
		ServerLevel level = helper.getLevel();
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

			@Override
			public boolean hasPermissions(int level) {
				return op || super.hasPermissions(level);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static List<String> keys(List<Component> seen) {
		return QaStageFourGameTests.keys(seen);
	}

	/** The line with key {@code key}, rendered: no raw key and no placeholder left, and naming each of {@code parts}. */
	private static String rendered(GameTestHelper helper, List<Component> seen, String key, String... parts) {
		Component c = QaStageFourGameTests.find(seen, key);
		helper.assertTrue(c != null, "no " + key + " line; saw " + keys(seen));
		String text = c.getString();
		helper.assertTrue(!RAW_KEY.matcher(text).find() && !text.contains("%s") && !text.contains("%1$s") && !text.equals(key),
			"the line isn't rendered: '" + text + "'");
		for (String part : parts) {
			helper.assertTrue(text.contains(part), "'" + text + "' doesn't say " + part);
		}
		return text;
	}

	/** Every line a player read is a real translation (no raw key showing). */
	private static void allRendered(GameTestHelper helper, List<Component> seen) {
		for (Component c : seen) {
			String text = c.getString();
			helper.assertTrue(!RAW_KEY.matcher(text).find() && !text.contains("%s"), "a raw or broken line: '" + text + "'");
		}
	}

	// --- B336: the sales log, whatever is in hand, for the owner, a friend and an operator --------------------------------

	private static final BlockPos COUNTER = new BlockPos(4, 2, 4);

	/** Nothing was placed against the counter by the click (its six neighbors are as set up: air, the floor below). */
	private static void nothingPlacedAround(GameTestHelper helper, BlockPos counter) {
		for (Direction d : Direction.values()) {
			if (d == Direction.DOWN) {
				continue;
			}
			helper.assertTrue(helper.getBlockState(counter.relative(d)).isAir(),
				"a block was placed " + d + " of the counter: " + helper.getBlockState(counter.relative(d)));
		}
	}

	/**
	 * shop-counter.md: "Sneak-right-click the counter to see your sales, whatever you have in your hand." The owner's
	 * hands in turn: bread in the OFF hand only, a cobblestone block in the main hand, a torch and emeralds in both. Each
	 * sneak-click shows the sales log, places nothing against the counter and uses nothing up.
	 */
	//$ gametest_batch AREA '"qaCs1101LogHands"'
	@GameTest(template = AREA, batch = "qaCs1101LogHands")
	public void theOwnerGetsTheSalesLogWithEitherOrBothHandsFull(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		List<String> wrong = new ArrayList<>();
		ItemStack[][] cases = {
			{ItemStack.EMPTY, new ItemStack(Items.BREAD, 3)},
			{new ItemStack(Items.COBBLESTONE, 5), ItemStack.EMPTY},
			{new ItemStack(Items.TORCH, 7), new ItemStack(Items.EMERALD, 4)},
		};
		for (ItemStack[] hands : cases) {
			List<Component> seen = new ArrayList<>();
			ServerPlayer owner = player(helper, seen, QaStageSixGameTests.name("qaCsOwn"), false);
			((ShopCounterBlockEntity) helper.getBlockEntity(COUNTER)).setOwner(owner.getUUID(), owner.getGameProfile().getName());
			owner.setItemInHand(InteractionHand.MAIN_HAND, hands[0].copy());
			owner.setItemInHand(InteractionHand.OFF_HAND, hands[1].copy());
			owner.setShiftKeyDown(true);
			QaStageFourGameTests.press(helper, owner, COUNTER, Direction.UP);
			owner.setShiftKeyDown(false);
			String what = hands[0].getHoverName().getString() + " / " + hands[1].getHoverName().getString();
			if (seen.stream().noneMatch(c -> QaStageFourGameTests.key(c).startsWith("message.aliveworkplace.shop.log"))) {
				wrong.add(what + ": no sales log, saw " + keys(seen));
			}
			if (owner.getMainHandItem().getCount() != hands[0].getCount() || owner.getOffhandItem().getCount() != hands[1].getCount()) {
				wrong.add(what + ": used up, now " + owner.getMainHandItem() + " / " + owner.getOffhandItem());
			}
			if (owner.containerMenu != owner.inventoryMenu) {
				wrong.add(what + ": a screen opened instead");
			}
			owner.closeContainer();
			allRendered(helper, seen);
		}
		nothingPlacedAround(helper, COUNTER);
		helper.assertTrue(wrong.isEmpty(), "the owner's sneak-click on the counter: " + wrong);
		helper.succeed();
	}

	/**
	 * Owner Frank's counter. A friend of his (/workplace friend add) and an operator, each holding goods, sneak-right-click
	 * it and get the sales log ("Only you, your friends and operators" run the shop); a stranger holding the same gets the
	 * "This is Frank's shop" line, no log, and nothing is placed or used up.
	 */
	//$ gametest_batch AREA '"qaCs1101LogWho"'
	@GameTest(template = AREA, batch = "qaCs1101LogWho")
	public void aFriendAndAnOperatorGetTheLogAStrangerDoesNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		UUID frank = UUID.randomUUID();
		((ShopCounterBlockEntity) helper.getBlockEntity(COUNTER)).setOwner(frank, "Frank");
		Friends friends = Friends.get(helper.getLevel().getServer());

		List<Component> fSeen = new ArrayList<>();
		ServerPlayer friend = player(helper, fSeen, QaStageSixGameTests.name("qaCsFr"), false);
		friends.add(frank, friend.getUUID(), friend.getGameProfile().getName());
		Leftovers.after(helper, () -> friends.remove(frank, friend.getUUID()));
		List<Component> oSeen = new ArrayList<>();
		ServerPlayer op = player(helper, oSeen, QaStageSixGameTests.name("qaCsOp"), true);
		List<Component> sSeen = new ArrayList<>();
		ServerPlayer stranger = player(helper, sSeen, QaStageSixGameTests.name("qaCsSt"), false);
		helper.assertTrue(!stranger.hasPermissions(2), "set-up: the stranger is an operator");

		for (ServerPlayer p : List.of(friend, op, stranger)) {
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 10));
			p.setShiftKeyDown(true);
			QaStageFourGameTests.press(helper, p, COUNTER, Direction.UP);
			p.setShiftKeyDown(false);
			helper.assertTrue(p.getMainHandItem().getCount() == 10, p.getGameProfile().getName() + "'s cobblestone was used: " + p.getMainHandItem());
			p.closeContainer();
		}
		helper.assertTrue(fSeen.stream().anyMatch(c -> QaStageFourGameTests.key(c).startsWith("message.aliveworkplace.shop.log")),
			"the owner's friend got no sales log: " + keys(fSeen));
		helper.assertTrue(oSeen.stream().anyMatch(c -> QaStageFourGameTests.key(c).startsWith("message.aliveworkplace.shop.log")),
			"an operator got no sales log: " + keys(oSeen));
		helper.assertTrue(sSeen.stream().noneMatch(c -> QaStageFourGameTests.key(c).startsWith("message.aliveworkplace.shop.log")),
			"a stranger was shown Frank's sales: " + keys(sSeen));
		rendered(helper, sSeen, "message.aliveworkplace.shop.not_yours", "Frank");
		helper.assertTrue(((ShopCounterBlockEntity) helper.getBlockEntity(COUNTER)).owner().equals(frank), "the counter changed hands");
		nothingPlacedAround(helper, COUNTER);
		helper.succeed();
	}

	// --- B348 shops: a counter, its chests and a shopkeeper ------------------------------------------------------------

	private static final BlockPos SHOP = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos KEEPER = new BlockPos(4, 2, 2);
	private static final int PAY = 0;
	private static final int RESULT = 2;

	private record Shop(ShopCounterBlockEntity counter, Villager keeper) {
	}

	/** A counter owned by {@code owner} selling 16 cobblestone for an emerald, and its shopkeeper; no chest yet. */
	private static Shop shop(GameTestHelper helper, UUID owner, String ownerName) {
		Leftovers.clear(helper);
		helper.setBlock(SHOP, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(SHOP);
		counter.setOwner(owner, ownerName);
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(SHOP), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		return new Shop(counter, keeper);
	}

	private static Container chest(GameTestHelper helper, BlockPos rel, int cobblestone) {
		helper.setBlock(rel, Blocks.CHEST);
		Container chest = helper.getBlockEntity(rel);
		for (int i = 0; cobblestone > 0; i++, cobblestone -= 64) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, Math.min(64, cobblestone)));
		}
		return chest;
	}

	private static MerchantMenu open(GameTestHelper helper, ServerPlayer p, Shop shop) {
		QaStageFourGameTests.pressOn(p, shop.keeper(), new ArrayList<>());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the right-click didn't open the trades: " + p.containerMenu);
		return (MerchantMenu) p.containerMenu;
	}

	private static int carried(ServerPlayer p, Item item) {
		return p.getInventory().countItem(item) + (p.containerMenu.getCarried().is(item) ? p.containerMenu.getCarried().getCount() : 0);
	}

	/** {@code emeralds} in the payment slot, then a shift-click on the goods; the cobblestone the buyer then has. */
	private static int buyAll(ServerPlayer p, MerchantMenu menu, int emeralds) {
		menu.getSlot(PAY).set(new ItemStack(Items.EMERALD, emeralds));
		QaStageSixGameTests.click(p, RESULT, 0, ClickType.QUICK_MOVE);
		return carried(p, Items.COBBLESTONE);
	}

	private static int count(GameTestHelper helper, List<BlockPos> chests, Item item) {
		int n = 0;
		for (BlockPos rel : chests) {
			n += ((Container) helper.getBlockEntity(rel)).countItem(item);
		}
		return n;
	}

	/**
	 * 48 cobblestone (three lots) in the chest when the buyer opens the trades; the owner then takes 20, leaving 28 (one
	 * whole lot). Three emeralds and a shift-click buy exactly one lot: 16 cobblestone, two emeralds back, the chest left
	 * with 12 and the one emerald.
	 */
	//$ gametest_batch BIG '"qaCs1101StockShrinks"'
	@GameTest(template = BIG, batch = "qaCs1101StockShrinks")
	public void stockShrinkingWhileOpenSellsOnlyTheLotsLeft(GameTestHelper helper) {
		Shop shop = shop(helper, UUID.randomUUID(), "Frank");
		Container chest = chest(helper, CHEST, 48);
		ServerPlayer p = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCsBuy")).player();
		MerchantMenu menu = open(helper, p, shop);
		chest.removeItem(0, 20);
		int got = buyAll(p, menu, 3);
		p.closeContainer();
		helper.assertTrue(got == 16, "28 left in the chest (one lot): the buyer got " + got + " cobblestone, expected 16");
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 2, "expected 2 emeralds back, has " + p.getInventory().countItem(Items.EMERALD));
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 12 && chest.countItem(Items.EMERALD) == 1,
			"the chest should hold 12 cobblestone and 1 emerald: " + chest.countItem(Items.COBBLESTONE) + ", " + chest.countItem(Items.EMERALD));
		helper.succeed();
	}

	/**
	 * "Put the goods in chests within 8 blocks": the owner moves the whole stock from one of the shop's chests to another
	 * of its chests while the buyer looks. It is still the shop's stock, so an emerald still buys a lot, taken from the
	 * second chest; and nothing is sold twice (the first chest is empty, the second has 16 left).
	 */
	//$ gametest_batch BIG '"qaCs1101StockMoved"'
	@GameTest(template = BIG, batch = "qaCs1101StockMoved")
	public void stockMovedToAnotherOfTheShopsChestsStillSells(GameTestHelper helper) {
		Shop shop = shop(helper, UUID.randomUUID(), "Frank");
		Container first = chest(helper, CHEST, 32);
		BlockPos secondRel = new BlockPos(0, 2, 4);
		Container second = chest(helper, secondRel, 0);
		ServerPlayer p = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCsMov")).player();
		MerchantMenu menu = open(helper, p, shop);
		first.clearContent();
		second.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		int got = buyAll(p, menu, 1);
		p.closeContainer();
		helper.assertTrue(got == 16, "the stock moved to the shop's other chest: the buyer got " + got + ", expected 16");
		helper.assertTrue(first.countItem(Items.COBBLESTONE) == 0 && second.countItem(Items.COBBLESTONE) == 16,
			"the lot should come out of the second chest: first " + first.countItem(Items.COBBLESTONE) + ", second " + second.countItem(Items.COBBLESTONE));
		helper.assertTrue(count(helper, List.of(CHEST, secondRel), Items.EMERALD) == 1, "the emerald didn't land in the shop's chests");
		helper.succeed();
	}

	/**
	 * shop-counter.md: "A chest nearer another player's Shop Counter is that shop's, not yours". The owner moves the stock
	 * into a chest beside a neighbor's counter (6 blocks from his own, 2 from the neighbor's) while the buyer looks: it's
	 * no longer his shop's, so the emerald buys nothing and comes back, and the neighbor's chest keeps all 32.
	 */
	//$ gametest_batch BIG '"qaCs1101StockNeighbor"'
	@GameTest(template = BIG, batch = "qaCs1101StockNeighbor")
	public void stockMovedToANeighborsChestIsNotSold(GameTestHelper helper) {
		Shop shop = shop(helper, UUID.randomUUID(), "Frank");
		Container mine = chest(helper, CHEST, 32);
		BlockPos neighborCounter = new BlockPos(10, 2, 2);
		helper.setBlock(neighborCounter, ModBlocks.SHOP_COUNTER);
		((ShopCounterBlockEntity) helper.getBlockEntity(neighborCounter)).setOwner(UUID.randomUUID(), "Greta");
		BlockPos theirsRel = new BlockPos(8, 2, 2);
		Container theirs = chest(helper, theirsRel, 0);
		ServerPlayer p = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCsNb")).player();
		MerchantMenu menu = open(helper, p, shop);
		mine.clearContent();
		theirs.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		int got = buyAll(p, menu, 1);
		p.closeContainer();
		helper.assertTrue(got == 0, "Frank's shop sold " + got + " cobblestone out of Greta's chest");
		helper.assertTrue(theirs.countItem(Items.COBBLESTONE) == 32 && theirs.countItem(Items.EMERALD) == 0,
			"Greta's chest changed: " + theirs.countItem(Items.COBBLESTONE) + " cobblestone, " + theirs.countItem(Items.EMERALD) + " emeralds");
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 1, "the emerald didn't come back: " + p.getInventory().countItem(Items.EMERALD));
		helper.succeed();
	}

	/**
	 * A double chest is one chest: 16 cobblestone (one lot) in its right half. Two emeralds and a shift-click buy one lot,
	 * not two (the double chest's halves counted twice), and the second emerald comes back.
	 */
	//$ gametest_batch BIG '"qaCs1101StockDouble"'
	@GameTest(template = BIG, batch = "qaCs1101StockDouble")
	public void aDoubleChestsStockIsCountedOnce(GameTestHelper helper) {
		Shop shop = shop(helper, UUID.randomUUID(), "Frank");
		BlockPos left = CHEST;
		BlockPos right = CHEST.east();
		helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		((Container) helper.getBlockEntity(right)).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		helper.assertTrue(ChestBlock.getContainer((ChestBlock) Blocks.CHEST, helper.getBlockState(left), helper.getLevel(), helper.absolutePos(left), true)
			.getContainerSize() == 54, "set-up: not a double chest");
		ServerPlayer p = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCsDbl")).player();
		MerchantMenu menu = open(helper, p, shop);
		int got = buyAll(p, menu, 2);
		p.closeContainer();
		helper.assertTrue(got == 16, "16 cobblestone in a double chest sold " + got);
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 1, "expected the second emerald back, has " + p.getInventory().countItem(Items.EMERALD));
		helper.assertTrue(count(helper, List.of(left, right), Items.COBBLESTONE) == 0, "cobblestone left in the double chest");
		helper.succeed();
	}

	/**
	 * B336 with a real sale: a buyer buys a lot through the shopkeeper's screen, then the owner, holding the goods he's
	 * about to restock, sneak-right-clicks the counter: the log says one sale (singular) and names the buyer, the lot and
	 * the price, in full sentences.
	 */
	//$ gametest_batch BIG '"qaCs1101LogSale"'
	@GameTest(template = BIG, batch = "qaCs1101LogSale")
	public void theLogAfterARealSaleNamesTheBuyer(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = player(helper, seen, QaStageSixGameTests.name("qaCsLog"), false);
		Shop shop = shop(helper, owner.getUUID(), owner.getGameProfile().getName());
		chest(helper, CHEST, 32);
		ServerPlayer buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCsBuyer")).player();
		MerchantMenu menu = open(helper, buyer, shop);
		int got = buyAll(buyer, menu, 1);
		buyer.closeContainer();
		helper.assertTrue(got == 16, "set-up: the sale didn't go through (" + got + ")");
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 64));
		owner.setShiftKeyDown(true);
		QaStageFourGameTests.press(helper, owner, SHOP, Direction.UP);
		owner.setShiftKeyDown(false);
		helper.assertTrue(QaStageFourGameTests.saw(seen, "message.aliveworkplace.shop.log.header_items.one")
				|| QaStageFourGameTests.saw(seen, "message.aliveworkplace.shop.log.header.one"),
			"no one-sale header in the log: " + keys(seen));
		String line = rendered(helper, seen, "message.aliveworkplace.shop.log.line", buyer.getGameProfile().getName(), "16", "Cobblestone", "Emerald");
		allRendered(helper, seen);
		helper.assertTrue(owner.getMainHandItem().getCount() == 64 && helper.getBlockState(SHOP.above()).isAir(),
			"the held cobblestone was placed or used: " + owner.getMainHandItem() + ", above " + helper.getBlockState(SHOP.above()));
		helper.assertTrue(!line.toLowerCase().contains("colour"), "British spelling: " + line);
		helper.succeed();
	}

	// --- B349: other automation and a reloaded counter ------------------------------------------------------------------

	/**
	 * B349's other automation: a hopper minecart on a rail under a counter (the counter reloaded from its saved data
	 * first, as after a restart) pulls nothing out of its price list in 60 ticks, while the same cart under a chest does
	 * pull, so the cart works. The counter's lot and price are exactly as the owner set them.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCs1101Minecart"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCs1101Minecart")
	public void aHopperMinecartUnderAReloadedCounterPullsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos counterRel = new BlockPos(3, 3, 3);
		BlockPos chestRel = new BlockPos(9, 3, 3);
		helper.setBlock(counterRel, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterRel);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		// a restart: the counter's block entity read back from what it saved
		CompoundTag saved = counter.saveWithFullMetadata(level.registryAccess());
		BlockEntity reloaded = BlockEntity.loadStatic(counter.getBlockPos(), counter.getBlockState(), saved, level.registryAccess());
		helper.assertTrue(reloaded instanceof ShopCounterBlockEntity, "set-up: the counter didn't load: " + reloaded);
		level.setBlockEntity(reloaded);
		ShopCounterBlockEntity live = helper.getBlockEntity(counterRel);
		helper.assertTrue(live == reloaded && live.getItem(0).getCount() == 16 && live.getItem(ShopCounterBlockEntity.COLUMNS).is(Items.EMERALD),
			"the reloaded counter lost its price list: " + live.getItem(0) + ", " + live.getItem(ShopCounterBlockEntity.COLUMNS));
		helper.setBlock(chestRel, Blocks.CHEST);
		((Container) helper.getBlockEntity(chestRel)).setItem(0, new ItemStack(Items.BREAD, 4));
		helper.setBlock(counterRel.below(), Blocks.RAIL);
		helper.setBlock(chestRel.below(), Blocks.RAIL);
		MinecartHopper underCounter = helper.spawn(EntityType.HOPPER_MINECART, counterRel.below());
		MinecartHopper underChest = helper.spawn(EntityType.HOPPER_MINECART, chestRel.below());
		helper.runAfterDelay(60, () -> {
			ShopCounterBlockEntity now = helper.getBlockEntity(counterRel);
			helper.assertTrue(underChest.countItem(Items.BREAD) > 0, "set-up: the hopper minecart under the chest pulled nothing");
			helper.assertTrue(underCounter.isEmpty(), "the hopper minecart under the counter pulled " + underCounter.getItem(0) + " out of the price list");
			helper.assertTrue(now.getItem(0).is(Items.COBBLESTONE) && now.getItem(0).getCount() == 16
					&& now.getItem(ShopCounterBlockEntity.COLUMNS).is(Items.EMERALD) && now.getItem(ShopCounterBlockEntity.COLUMNS).getCount() == 1,
				"the price list changed: " + now.getItem(0) + ", " + now.getItem(ShopCounterBlockEntity.COLUMNS));
			underCounter.discard();
			underChest.discard();
			helper.succeed();
		});
	}

	/**
	 * A dropper pointing into a counter, powered, puts nothing in as a new price; the same dropper pointing into a chest
	 * does drop its item in, so the dropper fired.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCs1101Dropper"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCs1101Dropper")
	public void aDropperIntoACounterAddsNoPrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterRel = new BlockPos(4, 2, 3);
		BlockPos chestRel = new BlockPos(4, 2, 8);
		helper.setBlock(counterRel, ModBlocks.SHOP_COUNTER);
		((ShopCounterBlockEntity) helper.getBlockEntity(counterRel)).setOwner(UUID.randomUUID(), "Frank");
		helper.setBlock(chestRel, Blocks.CHEST);
		for (BlockPos target : List.of(counterRel, chestRel)) {
			BlockPos dropper = target.west();
			helper.setBlock(dropper, Blocks.DROPPER.defaultBlockState().setValue(DropperBlock.FACING, Direction.EAST));
			((Container) helper.getBlockEntity(dropper)).setItem(0, new ItemStack(Items.BREAD, 2));
		}
		helper.runAfterDelay(2, () -> {
			helper.setBlock(counterRel.west().west(), Blocks.REDSTONE_BLOCK);
			helper.setBlock(chestRel.west().west(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(((Container) helper.getBlockEntity(chestRel)).countItem(Items.BREAD) == 1, "set-up: the dropper into the chest didn't fire");
			ShopCounterBlockEntity counter = helper.getBlockEntity(counterRel);
			helper.assertTrue(counter.isEmpty(), "a dropper put a price into the counter: " + counter.countItem(Items.BREAD) + " bread");
			helper.succeed();
		});
	}

	// --- B338: the Captain's Insignia ---------------------------------------------------------------------------------

	private static final BlockPos BOUNTY_HALL = new BlockPos(5, 2, 5);
	private static final BlockPos BOARD = new BlockPos(10, 2, 5);
	private static final BlockPos FAR_HALL = new BlockPos(26, 2, 26);

	private record Hunt(ServerPlayer player, List<Component> seen, ItemStack insignia, int reward) {
	}

	/** A village hall and its Bounty Board, a bounty posted there, and a player; the bounty's insignia (not handed out). */
	private static Hunt hunt(GameTestHelper helper, boolean farHall) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		Bounties.forget(level);
		List<BlockPos> placed = new ArrayList<>(List.of(BOUNTY_HALL, BOARD));
		if (farHall) {
			placed.add(FAR_HALL);
		}
		Leftovers.after(helper, () -> {
			for (BlockPos rel : placed) {
				level.setBlockAndUpdate(helper.absolutePos(rel), Blocks.AIR.defaultBlockState());
			}
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			Bounties.forget(level);
		});
		for (BlockPos rel : farHall ? List.of(BOUNTY_HALL, FAR_HALL) : List.of(BOUNTY_HALL)) {
			helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(rel));
			hall.setLastQuestDay(Long.MAX_VALUE / 2);
			hall.setRank(VillageRanks.Rank.VILLAGE);
		}
		if (farHall) {
			((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(BOUNTY_HALL))).setCustomName(Component.literal("Oakmere"));
		}
		helper.setBlock(BOARD, ModBlocks.BOUNTY_BOARD);
		BlockPos hall = helper.absolutePos(BOUNTY_HALL);
		var bounty = Bounties.post(level, hall, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("hexer"), 0, 0, hall.offset(160, 0, 0));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, QaStageSixGameTests.name("qaCsHunt"), false);
		player.getInventory().clearContent();
		return new Hunt(player, seen, Bounties.insignia(level, bounty), bounty.reward);
	}

	/**
	 * README: "Hand it in at the board, at the hall (right-click with it)". The insignia in the OFF hand while the main
	 * hand holds bread (something the board doesn't take): a right-click on the board pays the reward, the insignia is
	 * gone, the bread is kept, and the "Paid ..." line is a rendered sentence naming the reward.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaCs1101InsigniaBread"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaCs1101InsigniaBread")
	public void theOffHandsInsigniaIsPaidWhileTheMainHandHoldsBread(GameTestHelper helper) {
		Hunt h = hunt(helper, false);
		helper.assertTrue(h.reward() > 1, "set-up: a reward of " + h.reward());
		h.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3));
		h.player().setItemInHand(InteractionHand.OFF_HAND, h.insignia().copy());
		QaStageFourGameTests.press(helper, h.player(), BOARD);
		int emeralds = QaStageFourGameTests.count(h.player(), Items.EMERALD);
		helper.assertTrue(emeralds == h.reward(), "paid " + emeralds + " emeralds, the reward is " + h.reward() + "; saw " + keys(h.seen())
			+ ", screen " + h.player().containerMenu.getClass().getSimpleName());
		helper.assertTrue(QaStageFourGameTests.count(h.player(), ModItems.CAPTAINS_INSIGNIA) == 0, "the insignia wasn't taken");
		helper.assertTrue(h.player().getMainHandItem().is(Items.BREAD) && h.player().getMainHandItem().getCount() == 3, "the bread: " + h.player().getMainHandItem());
		rendered(helper, h.seen(), "message.aliveworkplace.bounty.paid", String.valueOf(h.reward()));
		allRendered(helper, h.seen());
		helper.succeed();
	}

	/**
	 * Paid exactly once: one insignia in each hand (the same captain's, as a duped copy would be) at the hall. The first
	 * right-click pays the reward once and takes one; the second (the off hand's copy) says the bounty was already
	 * claimed, pays nothing and keeps the copy.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaCs1101InsigniaTwice"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaCs1101InsigniaTwice")
	public void oneBountyIsPaidOnceWithAnInsigniaInEachHand(GameTestHelper helper) {
		Hunt h = hunt(helper, false);
		h.player().setItemInHand(InteractionHand.MAIN_HAND, h.insignia().copy());
		h.player().setItemInHand(InteractionHand.OFF_HAND, h.insignia().copy());
		QaStageFourGameTests.press(helper, h.player(), BOUNTY_HALL);
		h.player().closeContainer();
		int first = QaStageFourGameTests.count(h.player(), Items.EMERALD);
		helper.assertTrue(first == h.reward(), "the first click paid " + first + ", the reward is " + h.reward() + "; saw " + keys(h.seen()));
		helper.assertTrue(QaStageFourGameTests.count(h.player(), ModItems.CAPTAINS_INSIGNIA) == 1, "one click took "
			+ (2 - QaStageFourGameTests.count(h.player(), ModItems.CAPTAINS_INSIGNIA)) + " insignia");
		h.seen().clear();
		QaStageFourGameTests.press(helper, h.player(), BOUNTY_HALL);
		h.player().closeContainer();
		int second = QaStageFourGameTests.count(h.player(), Items.EMERALD);
		helper.assertTrue(second == h.reward(), "the second insignia was paid again: " + second + " emeralds in all");
		rendered(helper, h.seen(), "message.aliveworkplace.bounty.already");
		helper.assertTrue(QaStageFourGameTests.count(h.player(), ModItems.CAPTAINS_INSIGNIA) == 1, "the refused copy was taken");
		helper.succeed();
	}

	/**
	 * The insignia from the OFF hand at another village's hall: not this village's bounty, so nothing is paid, the
	 * insignia is kept, and the player is told where to hand it in ("posted by Oakmere").
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaCs1101InsigniaElsewhere"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaCs1101InsigniaElsewhere")
	public void theOffHandsInsigniaAtAnotherHallIsKeptAndPointsHome(GameTestHelper helper) {
		Hunt h = hunt(helper, true);
		h.player().setItemInHand(InteractionHand.OFF_HAND, h.insignia().copy());
		QaStageFourGameTests.press(helper, h.player(), FAR_HALL);
		h.player().closeContainer();
		helper.assertTrue(QaStageFourGameTests.count(h.player(), Items.EMERALD) == 0, "another village paid the bounty");
		helper.assertTrue(h.player().getOffhandItem().is(ModItems.CAPTAINS_INSIGNIA), "the insignia was taken at the wrong hall");
		rendered(helper, h.seen(), "message.aliveworkplace.bounty.not_here", "Oakmere");
		helper.succeed();
	}

	// --- B341: the Guild Charter from the off hand ----------------------------------------------------------------------

	/**
	 * guilds.md: "sneak-right-click a Master villager with it". From the OFF hand (main hand empty) on a Journeyman: refused
	 * with the "isn't one yet" line naming her, the charter kept, nobody chartered, no trade screen. Then on the Master,
	 * twice: chartered once (one charter used), the second click says she already leads a guild and uses nothing.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCs1101CharterOff"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCs1101CharterOff")
	public void theOffHandsCharterIsRefusedOnAJourneymanAndUsedOnceOnAMaster(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaStageSixGameTests.guildVillage(helper);
		Villager jo = QaStageSixGameTests.guildVillager(helper, new BlockPos(6, 2, 6), "Jo", ModVillagers.BUILDER, 3);
		Villager dara = QaStageSixGameTests.guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			QaStageSixGameTests.ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCsGc"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GUILD_CHARTER, 3));
			QaStageSixGameTests.clickVillager(p, jo, c.seen());
			rendered(helper, c.seen(), "message.aliveworkplace.guild.not_master", "Jo");
			helper.assertTrue(p.getOffhandItem().getCount() == 3, "the refused charter was used: " + p.getOffhandItem());
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(jo) == null, "Jo, a Journeyman, leads a guild");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "a trade screen opened on the refusal");

			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(dara) != null, "Dara isn't chartered: " + keys(c.seen()));
			rendered(helper, c.seen(), "message.aliveworkplace.guild.chartered", "Dara");
			helper.assertTrue(p.getOffhandItem().getCount() == 2, "charters left after one: " + p.getOffhandItem());

			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			rendered(helper, c.seen(), "message.aliveworkplace.guild.already", "Dara");
			helper.assertTrue(p.getOffhandItem().getCount() == 2, "a second click on a Guild Master used a charter: " + p.getOffhandItem());
			helper.assertTrue(hall.guilds().size() == 1, "the hall lists " + hall.guilds().size() + " guilds");
			p.setShiftKeyDown(false);
			helper.succeed();
		});
	}

	/**
	 * The charter needs the sneak: a plain right-click on the Master with the charter in the OFF hand (main hand empty)
	 * charters nobody and keeps the charter, as the plain click from the main hand would.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCs1101CharterNoSneak"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCs1101CharterNoSneak")
	public void aPlainClickWithTheOffHandsCharterChartersNobody(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaStageSixGameTests.guildVillage(helper);
		Villager dara = QaStageSixGameTests.guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			QaStageSixGameTests.ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCsGn"));
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GUILD_CHARTER, 1));
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			p.closeContainer();
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(dara) == null, "a plain click chartered Dara: " + keys(c.seen()));
			helper.assertTrue(p.getOffhandItem().is(ModItems.GUILD_CHARTER) && p.getOffhandItem().getCount() == 1, "the charter: " + p.getOffhandItem());
			helper.succeed();
		});
	}

	// --- B360: the City Plan, the Village Ledger and the Name Tag from the off hand ------------------------------------

	private static final BlockPos HALL = new BlockPos(4, 2, 4);

	/**
	 * village-hall.md: "use a Name Tag on the hall to call it what you like (the tag isn't used up)". From the OFF hand
	 * (main hand empty): the village is called exactly the tag's name, the line says so, and the tag is still there. A
	 * Name Tag never named in an anvil, from the off hand, renames nothing.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCs1101NameTag"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCs1101NameTag")
	public void theOffHandsNameTagNamesTheVillageExactlyAndIsKept(GameTestHelper helper) {
		QaHallHomesGameTests.hall(helper, HALL);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer p = player(helper, seen, QaStageSixGameTests.name("qaCsTag"), false);
			ItemStack tag = new ItemStack(Items.NAME_TAG);
			tag.set(DataComponents.CUSTOM_NAME, Component.literal("Oakmere Vale"));
			p.setItemInHand(InteractionHand.OFF_HAND, tag);
			QaStageFourGameTests.press(helper, p, HALL);
			p.closeContainer();
			helper.assertTrue("Oakmere Vale".equals(VillageHalls.name(level, abs).getString()), "the village is called " + VillageHalls.name(level, abs).getString());
			rendered(helper, seen, "message.aliveworkplace.hall.named", "Oakmere Vale");
			helper.assertTrue(p.getOffhandItem().is(Items.NAME_TAG) && p.getOffhandItem().getCount() == 1, "the tag was used up: " + p.getOffhandItem());

			seen.clear();
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.NAME_TAG));
			QaStageFourGameTests.press(helper, p, HALL);
			p.closeContainer();
			helper.assertTrue("Oakmere Vale".equals(VillageHalls.name(level, abs).getString()), "a blank Name Tag renamed the village to "
				+ VillageHalls.name(level, abs).getString());
			helper.assertTrue(!QaStageFourGameTests.saw(seen, "message.aliveworkplace.hall.named"), "a blank tag said it named the village");
			allRendered(helper, seen);
			helper.succeed();
		});
	}

	/**
	 * city-plan.md: "Bind it: right-click a Village Hall". The plan in the OFF hand while the main hand holds cobblestone:
	 * a plain right-click on the hall uses the hall (as a click on a chest holding a block opens the chest), so the plan
	 * is bound with its line, and no cobblestone is placed or used.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCs1101PlanBlock"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCs1101PlanBlock")
	public void theOffHandsPlanBindsWhileTheMainHandHoldsABlock(GameTestHelper helper) {
		QaHallHomesGameTests.hall(helper, HALL);
		BlockPos abs = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer p = player(helper, seen, QaStageSixGameTests.name("qaCsPlan"), false);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 5));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CITY_PLAN));
			QaStageFourGameTests.press(helper, p, HALL);
			p.closeContainer();
			var plan = p.getOffhandItem().get(ModComponents.CITY_PLAN_HALL);
			helper.assertTrue(plan != null && plan.hall().pos().equals(abs), "the plan wasn't bound: " + plan + "; saw " + keys(seen));
			rendered(helper, seen, "message.aliveworkplace.city_plan.bound");
			helper.assertTrue(p.getMainHandItem().is(Items.COBBLESTONE) && p.getMainHandItem().getCount() == 5, "the cobblestone: " + p.getMainHandItem());
			nothingPlacedAround(helper, HALL);
			helper.succeed();
		});
	}

	/**
	 * One click does one thing, the main hand's first (vanilla's order): a ledger in the main hand and a plan in the off
	 * hand bind the ledger only; the next click, the main hand now empty, binds the plan from the off hand.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCs1101LedgerPlan"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCs1101LedgerPlan")
	public void theMainHandsLedgerGoesFirstThenTheOffHandsPlan(GameTestHelper helper) {
		QaHallHomesGameTests.hall(helper, HALL);
		BlockPos abs = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer p = player(helper, seen, QaStageSixGameTests.name("qaCsLed"), false);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.VILLAGE_LEDGER));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CITY_PLAN));
			QaStageFourGameTests.press(helper, p, HALL);
			p.closeContainer();
			var ledger = p.getMainHandItem().get(ModComponents.LEDGER);
			helper.assertTrue(ledger != null && ledger.hall().pos().equals(abs), "the main hand's ledger wasn't bound: " + keys(seen));
			helper.assertTrue(p.getOffhandItem().get(ModComponents.CITY_PLAN_HALL) == null, "one click bound the off hand's plan too");
			rendered(helper, seen, "message.aliveworkplace.ledger.bound");

			seen.clear();
			ItemStack bound = p.getMainHandItem().copy();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			QaStageFourGameTests.press(helper, p, HALL);
			p.closeContainer();
			var plan = p.getOffhandItem().get(ModComponents.CITY_PLAN_HALL);
			helper.assertTrue(plan != null && plan.hall().pos().equals(abs), "the off hand's plan wasn't bound on the next click: " + keys(seen));
			helper.assertTrue(bound.get(ModComponents.LEDGER) != null, "the ledger lost its binding");
			allRendered(helper, seen);
			helper.succeed();
		});
	}
}
