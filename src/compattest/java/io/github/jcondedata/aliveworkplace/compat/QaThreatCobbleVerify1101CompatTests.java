package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA verification (2026-11-01) of B350, from its expected behaviour: with CobbleDollars installed, the shop's own
 * screen (opened by right-clicking the shopkeeper empty-handed, bought with two clicks on the goods) counts and takes an
 * item price from the whole inventory, the off hand included. A survival player on a real (embedded) connection; the
 * click on the shopkeeper and the clicks on the screen are the packets a client sends.
 */
public class QaThreatCobbleVerify1101CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos COUNTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos KEEPER = new BlockPos(4, 2, 2);
	private static final int GOODS = Shops.FIRST_GOODS_SLOT;

	private record Client(ServerPlayer player, List<Component> seen) {
	}

	private record Shop(Container chest, Villager keeper) {
	}

	private static Client client(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qav1101shop" + (int) (Math.random() * 100000)), false);
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
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 4))));
		return new Client(player, seen);
	}

	/** A counter owned by "Frank" selling 8 oak logs for 3 diamonds, its chest with 64 logs, and its shopkeeper. */
	private static Shop shop(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = (ShopCounterBlockEntity) helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.OAK_LOG, 8));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.DIAMOND, 3));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		return new Shop(chest, keeper);
	}

	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? c.getString() : key(c.getSiblings().get(0));
	}

	private static List<String> keys(Client c) {
		return c.seen().stream().map(QaThreatCobbleVerify1101CompatTests::key).toList();
	}

	/** The right-click on the shopkeeper as the client sends it (main hand, at a point then plain): the shop's screen. */
	private static ChoiceMenu open(GameTestHelper helper, Client c, Villager keeper) {
		ServerPlayer p = c.player();
		p.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(keeper, false, InteractionHand.MAIN_HAND,
			new Vec3(0, keeper.getBbHeight() / 2, 0)));
		if (p.containerMenu == p.inventoryMenu) {
			p.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(keeper, false, InteractionHand.MAIN_HAND));
		}
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the shop's screen didn't open: " + p.containerMenu + ", saw " + keys(c));
		return (ChoiceMenu) p.containerMenu;
	}

	/** A left click on {@code slot} of the open screen, as the client sends it. */
	private static void click(ServerPlayer p, int slot) {
		var menu = p.containerMenu;
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	/** Whether the logs' "Price: ..." line on the screen is green (can pay) rather than red. */
	private static boolean priceGreen(GameTestHelper helper, ChoiceMenu menu) {
		ItemStack icon = menu.icon(GOODS);
		helper.assertTrue(icon.is(Items.OAK_LOG), "the logs aren't on the screen: " + icon);
		ItemLore lore = icon.get(DataComponents.LORE);
		helper.assertTrue(lore != null && !lore.lines().isEmpty(), "the logs have no price line");
		Component price = lore.lines().get(0);
		helper.assertTrue(price.getString().contains("3× Diamond"), "the price line: " + price.getString());
		TextColor color = price.getStyle().getColor();
		return TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(color);
	}

	/** Every diamond the player carries: bag, hotbar and off hand (Inventory.countItem counts them all). */
	private static int diamonds(ServerPlayer p) {
		return p.getInventory().countItem(Items.DIAMOND);
	}

	private static void run(GameTestHelper helper, Client c, Runnable body) {
		try {
			helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
			body.run();
		} finally {
			c.player().closeContainer();
			helper.getLevel().getServer().getPlayerList().remove(c.player());
		}
	}

	/**
	 * B350's own case on the screen: 2 diamonds in the bag and 1 in the off hand (main hand empty, so the click opens the
	 * screen). The price line is green; two clicks buy the 8 logs; all 3 diamonds are taken (the off hand empty) and are
	 * in the shop's chest; "Bought ...".
	 */
	//$ gametest_batch AREA '"qav1101ShopOffHand"'
	@GameTest(template = AREA, batch = "qav1101ShopOffHand")
	public void qaTwoInTheBagAndOneInTheOffHandBuyAThreeDiamondLot(GameTestHelper helper) {
		Shop shop = shop(helper);
		Client c = client(helper);
		ServerPlayer p = c.player();
		run(helper, c, () -> {
			p.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 2));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 1));
			ChoiceMenu menu = open(helper, c, shop.keeper());
			helper.assertTrue(priceGreen(helper, menu), "3 diamonds (2 + 1 in the off hand) show the price in red");
			click(p, GOODS);
			click(p, GOODS);
			helper.assertTrue(QaThreatCobbleVerify1101CompatTests.keys(c).contains("message.aliveworkplace.shop.bought"),
				"3 diamonds (2 in the bag, 1 in the off hand) didn't buy: saw " + keys(c));
			helper.assertTrue(diamonds(p) == 0 && p.getOffhandItem().isEmpty(), "diamonds left: " + diamonds(p) + ", off hand " + p.getOffhandItem());
			helper.assertTrue(p.getInventory().countItem(Items.OAK_LOG) == 8, "logs bought: " + p.getInventory().countItem(Items.OAK_LOG));
			helper.assertTrue(shop.chest().countItem(Items.DIAMOND) == 3 && shop.chest().countItem(Items.OAK_LOG) == 56,
				"the chest: diamonds " + shop.chest().countItem(Items.DIAMOND) + ", logs " + shop.chest().countItem(Items.OAK_LOG));
		});
		helper.succeed();
	}

	/**
	 * One short: 1 diamond in the bag and 1 in the off hand. The price line is red; two clicks are refused with "That
	 * costs 3× Diamond."; nothing is taken and nothing changes hands.
	 */
	//$ gametest_batch AREA '"qav1101ShopShort"'
	@GameTest(template = AREA, batch = "qav1101ShopShort")
	public void qaTwoDiamondsInAllAreRefused(GameTestHelper helper) {
		Shop shop = shop(helper);
		Client c = client(helper);
		ServerPlayer p = c.player();
		run(helper, c, () -> {
			p.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 1));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 1));
			ChoiceMenu menu = open(helper, c, shop.keeper());
			helper.assertFalse(priceGreen(helper, menu), "2 diamonds show a 3-diamond price in green");
			click(p, GOODS);
			click(p, GOODS);
			Component poor = c.seen().stream().filter(m -> "message.aliveworkplace.shop.too_poor".equals(key(m))).findFirst().orElse(null);
			helper.assertTrue(poor != null, "2 diamonds weren't refused: saw " + keys(c));
			helper.assertTrue(poor.getString().equals("That costs 3× Diamond."), "the refusal reads: " + poor.getString());
			helper.assertFalse(keys(c).contains("message.aliveworkplace.shop.bought"), "2 diamonds bought a 3-diamond lot");
			helper.assertTrue(diamonds(p) == 2 && p.getOffhandItem().getCount() == 1, "diamonds taken on a refused sale: " + diamonds(p)
				+ ", off hand " + p.getOffhandItem());
			helper.assertTrue(p.getInventory().countItem(Items.OAK_LOG) == 0 && shop.chest().countItem(Items.OAK_LOG) == 64
				&& shop.chest().countItem(Items.DIAMOND) == 0, "goods moved on a refused sale");
		});
		helper.succeed();
	}

	/** All 3 diamonds in the off hand, none in the bag: bought, the off hand left empty. */
	//$ gametest_batch AREA '"qav1101ShopAllOff"'
	@GameTest(template = AREA, batch = "qav1101ShopAllOff")
	public void qaThreeDiamondsInTheOffHandAloneBuy(GameTestHelper helper) {
		Shop shop = shop(helper);
		Client c = client(helper);
		ServerPlayer p = c.player();
		run(helper, c, () -> {
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 3));
			ChoiceMenu menu = open(helper, c, shop.keeper());
			helper.assertTrue(priceGreen(helper, menu), "3 diamonds in the off hand show the price in red");
			click(p, GOODS);
			click(p, GOODS);
			helper.assertTrue(keys(c).contains("message.aliveworkplace.shop.bought"), "3 diamonds in the off hand didn't buy: saw " + keys(c));
			helper.assertTrue(p.getOffhandItem().isEmpty() && diamonds(p) == 0, "off hand after: " + p.getOffhandItem());
			helper.assertTrue(p.getInventory().countItem(Items.OAK_LOG) == 8 && shop.chest().countItem(Items.DIAMOND) == 3, "the sale didn't go through");
		});
		helper.succeed();
	}

	/**
	 * More than enough, split: 2 in the bag and 5 in the off hand. Exactly 3 are taken (4 left between bag and off hand),
	 * and a second purchase straight after takes 3 more (1 left); a third, with 1, is refused.
	 */
	//$ gametest_batch AREA '"qav1101ShopRest"'
	@GameTest(template = AREA, batch = "qav1101ShopRest")
	public void qaOnlyThePriceIsTakenAndTheRestCountsAgain(GameTestHelper helper) {
		Shop shop = shop(helper);
		Client c = client(helper);
		ServerPlayer p = c.player();
		run(helper, c, () -> {
			p.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 2));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 5));
			open(helper, c, shop.keeper());
			click(p, GOODS);
			click(p, GOODS);
			int left = diamonds(p);
			helper.assertTrue(left == 4, "7 diamonds, a 3-diamond lot: " + left + " left (bag " + diamonds(p) + ", off hand " + p.getOffhandItem() + ")");
			click(p, GOODS);
			click(p, GOODS);
			left = diamonds(p);
			helper.assertTrue(left == 1 && p.getInventory().countItem(Items.OAK_LOG) == 16, "after the second lot: " + left + " diamonds, "
				+ p.getInventory().countItem(Items.OAK_LOG) + " logs");
			c.seen().clear();
			click(p, GOODS);
			click(p, GOODS);
			helper.assertTrue(keys(c).contains("message.aliveworkplace.shop.too_poor"), "1 diamond wasn't refused: saw " + keys(c));
			helper.assertTrue(diamonds(p) == 1 && shop.chest().countItem(Items.DIAMOND) == 6,
				"the last diamond: " + (diamonds(p)) + ", chest " + shop.chest().countItem(Items.DIAMOND));
		});
		helper.succeed();
	}
}
