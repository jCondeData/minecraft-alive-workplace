package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.store.DropBoxBlockEntity;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, 2026-11-16): the Mailbox, the Drop Box and the Shop Counter after 44.1 moved them onto their own
 * Copperline menus (the Drop Box and the Counter off vanilla's chest menu types, the Mailbox's parcel into a 3x3 side
 * panel). From the spec ("don't break existing saves", "slots, shift-clicks and the counter's sales still work"): what
 * a box holds survives a save and reload and shows in the same slots of its new menu; a block saved before (only its
 * items, none of the later fields) loads with empty defaults and still opens; and the parcel's nine slots, the ones
 * that moved, never lose an item when the screen closes unsent, even with a full pack.
 */
public class QaCopperSlotSaves1116eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

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

	/** {@code n} different items, never air, all stackable to at least 16. */
	private static List<Item> items(int n) {
		List<Item> all = List.of(Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.SAND, Items.GRAVEL, Items.IRON_INGOT, Items.GOLD_INGOT,
			Items.COAL, Items.REDSTONE, Items.EMERALD, Items.DIAMOND, Items.BRICK, Items.CLAY_BALL, Items.STICK, Items.BONE, Items.STRING,
			Items.FEATHER, Items.FLINT, Items.WHEAT, Items.CARROT, Items.POTATO, Items.APPLE, Items.BREAD, Items.GLASS, Items.SPRUCE_LOG,
			Items.BIRCH_LOG, Items.GLOWSTONE_DUST);
		return all.subList(0, n);
	}

	/** {@code be} saved as the world does and read back into a new block entity at the same place. */
	private static BlockEntity reloaded(GameTestHelper helper, BlockEntity be) {
		ServerLevel level = helper.getLevel();
		CompoundTag disk = be.saveWithFullMetadata(level.registryAccess());
		return BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), disk, level.registryAccess());
	}

	/**
	 * A Shop Counter with all nine columns priced, an owner and a sale in its log: saved and loaded, every column, the
	 * owner, the log and the totals come back; loaded into the counter in the world, its Copperline menu shows them in
	 * the same slots. A counter saved with only its price list (no owner, no log, no totals) loads with an empty log and
	 * zero totals and still opens on its menu.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void counterPriceListSurvivesReloadAndAnOldSaveOpens(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(pos);
		List<Item> goods = items(ShopCounterBlockEntity.COLUMNS);
		for (int c = 0; c < ShopCounterBlockEntity.COLUMNS; c++) {
			counter.setItem(c, new ItemStack(goods.get(c), c + 1));
			counter.setItem(ShopCounterBlockEntity.COLUMNS + c, new ItemStack(Items.EMERALD, 64 - c));
		}
		UUID owner = UUID.randomUUID();
		counter.setOwner(owner, "Shopowner");
		counter.logSale(new ShopCounterBlockEntity.Sale(3, "Buyer", new ItemStack(Items.COBBLESTONE, 1), 0, new ItemStack(Items.EMERALD, 500)));

		ShopCounterBlockEntity read = (ShopCounterBlockEntity) reloaded(helper, counter);
		helper.assertTrue(read != null, "the counter didn't load");
		for (int c = 0; c < ShopCounterBlockEntity.COLUMNS; c++) {
			helper.assertTrue(read.goods(c).is(goods.get(c)) && read.goods(c).getCount() == c + 1
				&& read.price(c).is(Items.EMERALD) && read.price(c).getCount() == 64 - c,
				"column " + c + " came back as " + read.goods(c) + " for " + read.price(c));
		}
		helper.assertTrue(owner.equals(read.owner()) && "Shopowner".equals(read.ownerName()), "the owner came back as " + read.ownerName());
		helper.assertTrue(read.sales().size() == 1 && read.sales().get(0).paid().getCount() == 500 && read.totalSales() == counter.totalSales(),
			"the sales log came back as " + read.sales() + ", " + read.totalSales() + " sales");

		// an old counter: only its price list on disk
		CompoundTag old = counter.saveWithFullMetadata(helper.getLevel().registryAccess());
		for (String later : List.of("owner", "ownerName", "sales", "totalSales", "totalDollars")) {
			old.remove(later);
		}
		counter.loadWithComponents(old, helper.getLevel().registryAccess());
		helper.assertTrue(counter.owner() == null && counter.ownerName().isEmpty() && counter.sales().isEmpty()
			&& counter.totalSales() == 0 && counter.totalDollars() == 0,
			"an old counter should load ownerless with an empty log, not " + counter.ownerName() + " / " + counter.sales().size() + " sales");
		ServerPlayer player = player(helper);
		use(helper, pos, player);
		helper.assertTrue(player.containerMenu instanceof ChestMenu m && m.getType() == ModBlocks.SHOP_COUNTER_MENU && m.getContainer() == counter,
			"the old counter should open on its Copperline menu, not " + player.containerMenu);
		ChestMenu menu = (ChestMenu) player.containerMenu;
		for (int c = 0; c < ShopCounterBlockEntity.COLUMNS; c++) {
			helper.assertTrue(menu.getSlot(c).getItem().is(goods.get(c)) && menu.getSlot(ShopCounterBlockEntity.COLUMNS + c).getItem().getCount() == 64 - c,
				"the menu's column " + c + " shows " + menu.getSlot(c).getItem() + " for " + menu.getSlot(ShopCounterBlockEntity.COLUMNS + c).getItem());
		}
		player.closeContainer();
		helper.succeed();
	}

	/**
	 * A full Drop Box (27 different stacks) saved and loaded keeps every stack in its slot; loaded into the box in the
	 * world, its Copperline menu (three rows) shows each in the same slot. A box saved empty (no Items list) loads empty.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void dropBoxContentsSurviveReloadInTheirSlots(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, ModBlocks.DROP_BOX);
		DropBoxBlockEntity box = helper.getBlockEntity(pos);
		List<Item> stuff = items(27);
		for (int i = 0; i < 27; i++) {
			box.setItem(i, new ItemStack(stuff.get(i), 1 + i % 16));
		}
		CompoundTag disk = box.saveWithFullMetadata(helper.getLevel().registryAccess());
		box.clearContent();
		box.loadWithComponents(disk, helper.getLevel().registryAccess());
		ServerPlayer player = player(helper);
		use(helper, pos, player);
		helper.assertTrue(player.containerMenu instanceof ChestMenu m && m.getType() == ModBlocks.DROP_BOX_MENU && m.getRowCount() == 3,
			"the box should open on its Copperline three-row menu, not " + player.containerMenu);
		ChestMenu menu = (ChestMenu) player.containerMenu;
		for (int i = 0; i < 27; i++) {
			ItemStack shown = menu.getSlot(i).getItem();
			helper.assertTrue(shown.is(stuff.get(i)) && shown.getCount() == 1 + i % 16,
				"slot " + i + " shows " + shown + " after the reload, expected " + BuiltInRegistries.ITEM.getKey(stuff.get(i)));
		}
		player.closeContainer();

		CompoundTag empty = disk.copy();
		empty.remove("Items");
		box.loadWithComponents(empty, helper.getLevel().registryAccess());
		helper.assertTrue(box.isEmpty(), "a box saved without items should load empty");
		helper.succeed();
	}

	/**
	 * The Mailbox: a full inbox with an owner saved and loaded comes back whole; and all nine parcel slots (the 3x3 side
	 * panel) filled and the screen closed unsent give every item back to the pack, and with a full pack drop every item
	 * at the player's feet: none is lost either way.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_mail_parcel_1116e"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_mail_parcel_1116e")
	public void mailboxInboxReloadsAndTheParcelNeverLosesAnItem(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, ModBlocks.MAILBOX);
		MailboxBlockEntity mailbox = helper.getBlockEntity(pos);
		UUID owner = UUID.randomUUID();
		mailbox.setOwner(owner, "Postie");
		List<Item> mail = items(MailboxBlockEntity.SIZE);
		for (int i = 0; i < MailboxBlockEntity.SIZE; i++) {
			mailbox.setItem(i, new ItemStack(mail.get(i), 2));
		}
		MailboxBlockEntity read = (MailboxBlockEntity) reloaded(helper, mailbox);
		for (int i = 0; i < MailboxBlockEntity.SIZE; i++) {
			helper.assertTrue(read.getItem(i).is(mail.get(i)) && read.getItem(i).getCount() == 2, "inbox slot " + i + " came back as " + read.getItem(i));
		}
		helper.assertTrue(owner.equals(read.owner()) && "Postie".equals(read.ownerName()), "the mailbox's owner came back as " + read.ownerName());

		// the parcel: a mailbox nobody owns yet, which anyone may open
		pos = new BlockPos(6, 2, 2);
		helper.setBlock(pos, ModBlocks.MAILBOX);
		List<Item> parcel = items(MailboxMenu.OUT);
		// 1. room in the pack: everything comes back
		ServerPlayer player = player(helper);
		use(helper, pos, player);
		helper.assertTrue(player.containerMenu instanceof MailboxMenu, "using the mailbox should open it, not " + player.containerMenu);
		MailboxMenu menu = (MailboxMenu) player.containerMenu;
		for (int i = 0; i < MailboxMenu.OUT; i++) {
			menu.getSlot(i).set(new ItemStack(parcel.get(i), 5));
		}
		player.closeContainer();
		for (Item item : parcel) {
			helper.assertTrue(player.getInventory().countItem(item) == 5,
				player.getInventory().countItem(item) + " " + BuiltInRegistries.ITEM.getKey(item) + " back in the pack after closing unsent, expected 5");
		}

		// 2. a full pack: everything lands at the player's feet (another new mailbox: the first one is now the first player's)
		pos = new BlockPos(2, 2, 8);
		helper.setBlock(pos, ModBlocks.MAILBOX);
		ServerPlayer full = player(helper);
		for (int i = 0; i < full.getInventory().items.size(); i++) {
			full.getInventory().items.set(i, new ItemStack(Items.NETHERRACK, 64));
		}
		use(helper, pos, full);
		MailboxMenu second = (MailboxMenu) full.containerMenu;
		for (int i = 0; i < MailboxMenu.OUT; i++) {
			second.getSlot(i).set(new ItemStack(parcel.get(i), 7));
		}
		full.closeContainer();
		AABB around = full.getBoundingBox().inflate(6);
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
		for (Item item : parcel) {
			int count = dropped.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(count == 7, count + " " + BuiltInRegistries.ITEM.getKey(item) + " dropped from a full pack, expected 7");
		}
		dropped.forEach(e -> e.discard());
		helper.succeed();
	}
}
