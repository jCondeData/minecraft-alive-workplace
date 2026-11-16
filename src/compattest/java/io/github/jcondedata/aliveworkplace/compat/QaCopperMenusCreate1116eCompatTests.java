package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.store.DropBoxBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, 2026-11-16), ROADMAP 44.1 and 44.2 with Create loaded: the Shop Counter, the Drop Box and the Mailbox moved
 * onto their own Copperline menus, and the mod's jobs' trades onto the Hall's look. From the spec ("slots, shift-clicks
 * and the counter's sales still work", "every trade ... still works"): Create's own items (a stack of andesite alloy,
 * unstackable wrenches, a renamed filter) go in and out of each new menu by shift-click, counted once, never lost or
 * made; a counter priced through its menu in Create items sells Create goods from a Create item vault, both on the
 * shop's own screen and in the shopkeeper's trade list. The plain suite's menu tests only use vanilla items.
 */
public class QaCopperMenusCreate1116eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static Item create(String id) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:" + id));
		if (item == net.minecraft.world.item.Items.AIR) {
			throw new IllegalStateException("create:" + id + " is not registered");
		}
		return item;
	}

	private static void use(GameTestHelper helper, BlockPos pos, ServerPlayer player) {
		BlockPos at = helper.absolutePos(pos);
		helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		return player;
	}

	private static ItemStack namedFilter() {
		ItemStack filter = new ItemStack(create("filter"));
		filter.set(DataComponents.CUSTOM_NAME, Component.literal("Ore sorter"));
		return filter;
	}

	private static boolean isNamedFilter(ItemStack stack) {
		return stack.is(create("filter")) && stack.getCount() == 1 && stack.has(DataComponents.CUSTOM_NAME)
			&& "Ore sorter".equals(stack.getHoverName().getString());
	}

	private static int count(net.minecraft.world.Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	/**
	 * The Drop Box's Copperline menu: 64 andesite alloy, 3 wrenches (unstackable) and a renamed Create filter, each
	 * shift-clicked from the hotbar, all land in the box (the filter keeps its name) and leave the pack; shift-clicked
	 * back out, all return to the pack and the box is empty. Nothing is counted twice or lost.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_e1116_copper_dropbox_create"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1116_copper_dropbox_create")
	public void dropBoxShiftClicksCreateItemsInAndOut(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, ModBlocks.DROP_BOX);
		DropBoxBlockEntity box = helper.getBlockEntity(pos);
		ServerPlayer player = player(helper);
		Item alloy = create("andesite_alloy");
		Item wrench = create("wrench");
		player.getInventory().setItem(0, new ItemStack(alloy, 64));
		for (int i = 1; i <= 3; i++) {
			player.getInventory().setItem(i, new ItemStack(wrench));
		}
		player.getInventory().setItem(4, namedFilter());
		use(helper, pos, player);
		helper.assertTrue(player.containerMenu instanceof ChestMenu m && m.getType() == ModBlocks.DROP_BOX_MENU,
			"the Drop Box should open on its Copperline menu, not " + player.containerMenu);
		ChestMenu menu = (ChestMenu) player.containerMenu;
		int hotbar = DropBoxBlockEntity.SIZE + 27; // the box's rows, then the pack, then the hotbar
		for (int i = 0; i <= 4; i++) {
			menu.quickMoveStack(player, hotbar + i);
		}
		helper.assertTrue(count(box, alloy) == 64, "the box holds " + count(box, alloy) + " andesite alloy, not 64");
		helper.assertTrue(count(box, wrench) == 3, "the box holds " + count(box, wrench) + " wrenches, not 3");
		boolean filterIn = false;
		for (int i = 0; i < box.getContainerSize(); i++) {
			filterIn |= isNamedFilter(box.getItem(i));
		}
		helper.assertTrue(filterIn, "the renamed Create filter didn't land in the box with its name");
		helper.assertTrue(player.getInventory().isEmpty(), "Create items stayed in the pack after shift-clicking them into the box");

		for (int i = 0; i < DropBoxBlockEntity.SIZE; i++) {
			if (menu.getSlot(i).hasItem()) {
				menu.quickMoveStack(player, i);
			}
		}
		helper.assertTrue(box.isEmpty(), "the box still holds items after shift-clicking every one out");
		helper.assertTrue(player.getInventory().countItem(alloy) == 64 && player.getInventory().countItem(wrench) == 3,
			"back in the pack: " + player.getInventory().countItem(alloy) + " alloy, " + player.getInventory().countItem(wrench) + " wrenches");
		boolean filterBack = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			filterBack |= isNamedFilter(player.getInventory().getItem(i));
		}
		helper.assertTrue(filterBack, "the renamed filter didn't come back to the pack with its name");
		player.closeContainer();
		helper.succeed();
	}

	/**
	 * The Mailbox's 3x3 parcel: Create items shift-clicked from the pack go into the parcel (never the take-only inbox);
	 * the screen closed unsent hands every one back, the filter with its name.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_e1116_copper_mail_create"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1116_copper_mail_create")
	public void mailboxParcelTakesCreateItemsByShiftClickAndGivesThemBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, ModBlocks.MAILBOX);
		ServerPlayer player = player(helper);
		Item alloy = create("andesite_alloy");
		Item wrench = create("wrench");
		player.getInventory().setItem(0, new ItemStack(alloy, 40));
		player.getInventory().setItem(1, new ItemStack(wrench));
		player.getInventory().setItem(2, new ItemStack(wrench));
		player.getInventory().setItem(3, namedFilter());
		use(helper, pos, player);
		helper.assertTrue(player.containerMenu instanceof MailboxMenu, "using the mailbox should open it, not " + player.containerMenu);
		MailboxMenu menu = (MailboxMenu) player.containerMenu;
		int hotbar = MailboxMenu.OUT + 27 + 27;
		for (int i = 0; i <= 3; i++) {
			menu.quickMoveStack(player, hotbar + i);
		}
		int alloyOut = 0;
		int wrenchesOut = 0;
		boolean filterOut = false;
		for (int i = 0; i < MailboxMenu.OUT; i++) {
			ItemStack s = menu.getSlot(i).getItem();
			alloyOut += s.is(alloy) ? s.getCount() : 0;
			wrenchesOut += s.is(wrench) ? s.getCount() : 0;
			filterOut |= isNamedFilter(s);
		}
		helper.assertTrue(alloyOut == 40 && wrenchesOut == 2 && filterOut,
			"the parcel holds " + alloyOut + " alloy, " + wrenchesOut + " wrenches, filter " + filterOut + "; expected 40, 2, the named filter");
		for (int i = MailboxMenu.OUT; i < MailboxMenu.OUT + 27; i++) {
			helper.assertTrue(!menu.getSlot(i).hasItem(), "a shift-click put " + menu.getSlot(i).getItem() + " in the take-only inbox");
		}
		helper.assertTrue(player.getInventory().isEmpty(), "Create items stayed in the pack after shift-clicking them into the parcel");
		player.closeContainer();
		helper.assertTrue(player.getInventory().countItem(alloy) == 40 && player.getInventory().countItem(wrench) == 2,
			"closed unsent, the pack got back " + player.getInventory().countItem(alloy) + " alloy and " + player.getInventory().countItem(wrench) + " wrenches");
		boolean filterBack = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			filterBack |= isNamedFilter(player.getInventory().getItem(i));
		}
		helper.assertTrue(filterBack, "closed unsent, the renamed filter didn't come back with its name");
		helper.succeed();
	}

	/**
	 * A shop priced entirely in Create items through the counter's Copperline menu: the owner shift-clicks 4 andesite
	 * alloy into the goods row and clicks 2 brass ingots into the price row. Its shopkeeper's trade list then offers 4
	 * alloy for 2 brass; a buyer pays 2 brass on the shop's screen and gets 4 alloy from the Create item vault by the
	 * counter, the brass goes into the vault, and the sale is logged.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_e1116_copper_counter_create"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1116_copper_counter_create")
	public void counterPricedInCreateItemsThroughItsMenuSellsFromAVault(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos counterAt = new BlockPos(3, 2, 3);
		helper.setBlock(counterAt, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterAt);
		BlockPos vault = helper.absolutePos(counterAt.north());
		set(helper.getLevel(), vault, with(block("create:item_vault").defaultBlockState(), "axis", "x"));
		Item alloy = create("andesite_alloy");
		Item brass = create("brass_ingot");

		ServerPlayer owner = player(helper);
		counter.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		owner.getInventory().setItem(0, new ItemStack(alloy, 4));
		use(helper, counterAt, owner);
		helper.assertTrue(owner.containerMenu instanceof ChestMenu m && m.getType() == ModBlocks.SHOP_COUNTER_MENU,
			"the owner's counter should open on its Copperline menu, not " + owner.containerMenu);
		ChestMenu menu = (ChestMenu) owner.containerMenu;
		int containerSlots = menu.getRowCount() * 9;
		menu.quickMoveStack(owner, containerSlots + 27); // hotbar slot 0
		helper.assertTrue(counter.goods(0).is(alloy) && counter.goods(0).getCount() == 4,
			"shift-clicking 4 andesite alloy should set column 0's goods, it is " + counter.goods(0));
		menu.setCarried(new ItemStack(brass, 2));
		menu.clicked(ShopCounterBlockEntity.COLUMNS, 0, ClickType.PICKUP, owner);
		helper.assertTrue(counter.price(0).is(brass) && counter.price(0).getCount() == 2,
			"clicking 2 brass into the price row should set column 0's price, it is " + counter.price(0));
		helper.assertTrue(menu.getCarried().isEmpty() && owner.getInventory().isEmpty(), "the owner kept " + menu.getCarried() + " on the cursor");
		owner.closeContainer();

		ItemStack rest = SupplyContainers.insert(helper.getLevel(), List.of(vault), new ItemStack(alloy, 64));
		helper.assertTrue(rest.isEmpty(), "the vault didn't take the shop's 64 alloy: " + rest);

		Villager keeper = helper.spawn(EntityType.VILLAGER, counterAt.south());
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(counterAt), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		helper.runAfterDelay(20, () -> {
			Shops.refreshOffers(helper.getLevel(), keeper);
			boolean offered = false;
			for (MerchantOffer offer : keeper.getOffers()) {
				offered |= offer.getResult().is(alloy) && offer.getResult().getCount() == 4
					&& offer.getCostA().is(brass) && offer.getCostA().getCount() == 2;
			}
			helper.assertTrue(offered, "the shopkeeper's trades don't offer 4 alloy for 2 brass: " + keeper.getOffers().size() + " offers");

			ServerPlayer buyer = player(helper);
			buyer.getInventory().setItem(0, new ItemStack(brass, 2));
			boolean bought = Shops.buy(buyer, keeper, 0);
			helper.assertTrue(bought, "a buyer with 2 brass couldn't buy column 0");
			helper.assertTrue(buyer.getInventory().countItem(alloy) == 4 && buyer.getInventory().countItem(brass) == 0,
				"the buyer has " + buyer.getInventory().countItem(alloy) + " alloy and " + buyer.getInventory().countItem(brass) + " brass, expected 4 and 0");
			long alloyLeft = SupplyContainers.count(helper.getLevel(), List.of(vault), alloy);
			long brassIn = SupplyContainers.count(helper.getLevel(), List.of(vault), brass);
			helper.assertTrue(alloyLeft == 60 && brassIn == 2, "the vault holds " + alloyLeft + " alloy and " + brassIn + " brass, expected 60 and 2");
			helper.assertTrue(counter.sales().size() == 1 && counter.sales().get(0).goods().is(alloy),
				"the counter's log has " + counter.sales().size() + " sales");
			helper.succeed();
		});
	}
}
