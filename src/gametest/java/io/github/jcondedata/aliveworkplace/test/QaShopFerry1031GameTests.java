package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.PriceTagItem;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.travel.FerryRides;
import io.github.jcondedata.aliveworkplace.travel.TicketData;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA 2026-10-31: the Shopkeeper and the Ferryman, from their guide pages (docs/guide/shopkeeper.md, shop-counter.md,
 * price-tag.md, ferryman.md, travel-post.md, travel-ticket.md, ferry-house-build.md). Each test quotes the sentence it
 * checks; the player's own entry points (placing and right-clicking blocks, a sneak-right-click on a villager, the trade
 * screen's clicks, an anvil, using a ticket) drive them. Runs without CobbleDollars, so prices are in emeralds.
 */
public class QaShopFerry1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos COUNTER = new BlockPos(10, 2, 10);
	private static final BlockPos CHEST = new BlockPos(10, 2, 13);
	private static final BlockPos KEEPER = new BlockPos(12, 2, 10);

	// ---------------------------------------------------------------- helpers

	private static QaStageFiveGameTests.Client client(GameTestHelper helper) {
		return QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaShopFerry"));
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static boolean saw(List<Component> seen, String text) {
		return seen.stream().anyMatch(c -> c.getString().equals(text));
	}

	/** The player right-clicks the {@code face} of the block at {@code pos} holding {@code held} (the use-item-on packet's server side). */
	private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false));
	}

	/** The player places {@code held} (a block item) on the floor at {@code pos} (y = 2: clicks the top of y = 1). */
	private static void place(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack held) {
		use(helper, player, pos.below(), Direction.UP, held);
	}

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	/** A shop: a counter owned by "Frank" selling 16 cobblestone for an emerald, a chest of {@code stock} cobblestone, a still shopkeeper. */
	private static ShopCounterBlockEntity counter(GameTestHelper helper) {
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		return counter;
	}

	private static Villager keeper(GameTestHelper helper) {
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		return keeper;
	}

	private static Villager reloaded(GameTestHelper helper, Villager v) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		return copy;
	}

	private static GlobalPos global(GameTestHelper helper, BlockPos relative) {
		return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
	}

	/** A post on the network far outside the test area (no block there), taken off the network when the test ends. */
	private static TravelNetwork.Post farPost(GameTestHelper helper, BlockPos absolute, String name) {
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		GlobalPos where = GlobalPos.of(helper.getLevel().dimension(), absolute);
		TravelNetwork.Post post = network.add(where, name);
		Leftovers.after(helper, () -> network.remove(where));
		return post;
	}

	private static ItemStack namedPost(String name) {
		ItemStack stack = new ItemStack(ModBlocks.TRAVEL_POST);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}

	private static List<String> tooltip(GameTestHelper helper, ItemStack stack, ServerPlayer player) {
		return stack.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
	}

	private static ItemStack tag(long dollars) {
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		PriceTagItem.setPrice(tag, dollars);
		return tag;
	}

	// ---------------------------------------------------------------- the Shopkeeper: getting one

	/**
	 * shopkeeper.md: "A villager already at a counter becomes one with an emerald." shop-counter.md: sneak-right-click
	 * with "an emerald: back to a Shopkeeper" (here from an Innkeeper picked with a bed). A plain right-click doesn't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopHireEmerald"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopHireEmerald")
	public void aVillagerAtACounterBecomesAShopkeeperWithAnEmerald(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(11, 2, 11));
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.EMERALD), false);
		helper.assertTrue(job(v) == VillagerProfession.NONE, "a plain right-click with an emerald gave " + job(v));
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.RED_BED), true);
		helper.assertTrue(job(v) == ModVillagers.INNKEEPER, "setup: a bed gave " + job(v));
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.EMERALD), true);
		helper.assertTrue(job(v) == ModVillagers.SHOPKEEPER, "an emerald gave " + job(v) + ", not back to a Shopkeeper");
		helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(helper.absolutePos(COUNTER))).orElse(false),
			"the shopkeeper works at " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE));
		helper.assertTrue(player.getMainHandItem().getCount() <= 1, "the emerald was duplicated");
		helper.succeed();
	}

	/**
	 * shop-counter.md: "a villager without a job nearby takes it by themselves and becomes its Shopkeeper." (With the
	 * config's randomStationJobs off; on, the counter's jobs are drawn at random, see the report.)
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaShopByThemselves"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaShopByThemselves")
	public void aJoblessVillagerTakesACounterByThemselves(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean was = RandomJobs.ENABLED;
		RandomJobs.ENABLED = false;
		Leftovers.after(helper, () -> RandomJobs.ENABLED = was);
		helper.setDayTime(2000);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 13));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(job(v) == ModVillagers.SHOPKEEPER, "the villager is a " + job(v));
			helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(helper.absolutePos(COUNTER))).orElse(false),
				"works at " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE));
		});
	}

	// ---------------------------------------------------------------- the Shop Counter

	/**
	 * shop-counter.md: "Owner: whoever places it"; "Place it and the shop is yours"; "Only you, your friends and operators
	 * can change the prices." The owner's right-click opens the price list; a stranger's says whose shop it is.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopOwner"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopOwner")
	public void placingACounterMakesItYoursAndStrangersCannotChangePrices(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client owner = client(helper);
		QaStageFiveGameTests.Client stranger = client(helper);
		place(helper, owner.player(), COUNTER, new ItemStack(ModBlocks.SHOP_COUNTER));
		helper.assertBlockPresent(ModBlocks.SHOP_COUNTER, COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		helper.assertTrue(owner.player().getUUID().equals(counter.owner()), "the owner is " + counter.owner());
		helper.assertTrue(owner.seen().stream().anyMatch(c -> c.getString().startsWith("Your Shop Counter is up.")),
			"the placer wasn't told: " + texts(owner.seen()));

		stranger.player().moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(10, 2, 12))));
		use(helper, stranger.player(), COUNTER, Direction.SOUTH, ItemStack.EMPTY);
		helper.assertTrue(stranger.player().containerMenu == stranger.player().inventoryMenu, "a stranger opened the price list");
		String expected = "This is " + owner.player().getGameProfile().getName() + "'s shop. Talk to the shopkeeper to buy.";
		helper.assertTrue(saw(stranger.seen(), expected), "the stranger read " + texts(stranger.seen()));

		owner.player().moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(10, 2, 12))));
		use(helper, owner.player(), COUNTER, Direction.SOUTH, ItemStack.EMPTY);
		helper.assertTrue(owner.player().containerMenu instanceof ChestMenu, "the owner's click opened " + owner.player().containerMenu);
		owner.player().closeContainer();
		helper.succeed();
	}

	/**
	 * shop-counter.md: "Sneak-right-click the counter to see your sales, whatever you have in your hand." The log reads
	 * "1 sale" for one, "sales" otherwise.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopLogClick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopLogClick")
	public void sneakClickShowsTheSalesWithAnythingInHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client owner = client(helper);
		place(helper, owner.player(), COUNTER, new ItemStack(ModBlocks.SHOP_COUNTER));
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		owner.player().moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(10, 2, 12))));
		owner.seen().clear();
		owner.player().setShiftKeyDown(true);
		use(helper, owner.player(), COUNTER, Direction.SOUTH, new ItemStack(Items.STICK));
		helper.assertTrue(saw(owner.seen(), "No sales yet."), "with a stick in hand: " + texts(owner.seen()));
		helper.assertTrue(owner.player().containerMenu == owner.player().inventoryMenu, "the sneak-click opened the price list");

		counter.logSale(new ShopCounterBlockEntity.Sale(3, "Bob", new ItemStack(Items.COBBLESTONE, 16), 0, new ItemStack(Items.EMERALD, 2)));
		owner.seen().clear();
		use(helper, owner.player(), COUNTER, Direction.SOUTH, new ItemStack(Items.DIAMOND_SWORD));
		helper.assertTrue(saw(owner.seen(), "Your shop: 1 sale. Latest:"), "header for one sale: " + texts(owner.seen()));
		helper.assertTrue(saw(owner.seen(), "Day 3 · Bob bought 16× Cobblestone for 2× Emerald"), "log line: " + texts(owner.seen()));
		counter.logSale(new ShopCounterBlockEntity.Sale(4, "Ann", new ItemStack(Items.BREAD, 4), 0, new ItemStack(Items.EMERALD, 1)));
		owner.seen().clear();
		use(helper, owner.player(), COUNTER, Direction.SOUTH, ItemStack.EMPTY);
		helper.assertTrue(saw(owner.seen(), "Your shop: 2 sales. Latest:"), "header for two sales (empty hands): " + texts(owner.seen()));
		owner.player().setShiftKeyDown(false);
		helper.succeed();
	}

	/**
	 * shopkeeper.md: "The counter keeps a log of the last 20 sales." The 21st pushes the oldest out; the log, the totals and
	 * the price list survive a save and reload, and so does the shopkeeper's sales count.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theLogKeepsTheLast20SalesThroughAReload(GameTestHelper helper) {
		ShopCounterBlockEntity counter = counter(helper);
		for (int i = 1; i <= 25; i++) {
			counter.logSale(new ShopCounterBlockEntity.Sale(i, "buyer" + i, new ItemStack(Items.COBBLESTONE, 16), 0, new ItemStack(Items.EMERALD)));
		}
		helper.assertTrue(counter.sales().size() == 20, "the log keeps " + counter.sales().size() + " sales");
		helper.assertTrue(counter.sales().get(0).buyer().equals("buyer25") && counter.sales().get(19).buyer().equals("buyer6"),
			"newest " + counter.sales().get(0).buyer() + ", oldest " + counter.sales().get(19).buyer());
		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = counter.saveWithFullMetadata(registries);
		ShopCounterBlockEntity copy = new ShopCounterBlockEntity(counter.getBlockPos(), counter.getBlockState());
		copy.loadWithComponents(saved, registries);
		helper.assertTrue(copy.sales().size() == 20 && copy.sales().get(0).buyer().equals("buyer25") && copy.totalSales() == 25,
			"after a reload: " + copy.sales().size() + " sales, newest " + (copy.sales().isEmpty() ? "-" : copy.sales().get(0).buyer()) + ", total " + copy.totalSales());
		helper.assertTrue(copy.goods(0).is(Items.COBBLESTONE) && copy.goods(0).getCount() == 16 && copy.price(0).is(Items.EMERALD),
			"the price list after a reload: " + copy.goods(0) + " for " + copy.price(0));
		helper.assertTrue("Frank".equals(copy.ownerName()) && counter.owner().equals(copy.owner()), "the owner after a reload: " + copy.ownerName());

		Villager keeper = keeper(helper);
		ModAttachments.SHOP_SALES.set(keeper, 7);
		Villager back = reloaded(helper, keeper);
		helper.assertTrue(ModAttachments.SHOP_SALES.getOrElse(back, 0) == 7, "the shopkeeper's sales after a reload: " + ModAttachments.SHOP_SALES.getOrElse(back, 0));
		helper.assertTrue(job(back) == ModVillagers.SHOPKEEPER, "reloaded as a " + job(back));
		helper.succeed();
	}

	/**
	 * shop-counter.md: "Goods and takings: chests within 8 blocks." A chest 8 blocks along stocks the shop; one 9 blocks
	 * along doesn't. The offers come from the right-click (what's for sale is what's in the chests).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaShopRange"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaShopRange")
	public void chestsWithin8BlocksStockTheShopButNot9(GameTestHelper helper) {
		Leftovers.clear(helper);
		ShopCounterBlockEntity counter = counter(helper);
		counter.setItem(1, new ItemStack(Items.BREAD, 4));
		counter.setItem(ShopCounterBlockEntity.COLUMNS + 1, new ItemStack(Items.EMERALD, 1));
		helper.setBlock(new BlockPos(18, 2, 10), Blocks.CHEST); // 8 along x
		helper.setBlock(new BlockPos(10, 2, 19), Blocks.CHEST); // 9 along z
		Container near = helper.getBlockEntity(new BlockPos(18, 2, 10));
		Container far = helper.getBlockEntity(new BlockPos(10, 2, 19));
		near.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		far.setItem(0, new ItemStack(Items.BREAD, 64));
		Villager keeper = keeper(helper);
		QaStageFiveGameTests.Client buyer = client(helper);
		QaStageFourGameTests.pressOn(buyer.player(), keeper, buyer.seen());
		List<String> offers = keeper.getOffers().stream().map(o -> o.getResult().getCount() + " " + o.getResult().getHoverName().getString()).toList();
		buyer.player().closeContainer();
		helper.assertTrue(offers.equals(List.of("16 Cobblestone")), "offers " + offers + ": the chest 8 blocks away should stock the cobblestone, the one 9 away not the bread");
		helper.assertTrue(keeper.getOffers().get(0).getMaxUses() == 2, "32 cobblestone in 16s should allow 2 sales, not " + keeper.getOffers().get(0).getMaxUses());
		helper.succeed();
	}

	/**
	 * shopkeeper.md: "Other players buy by trading with the shopkeeper, and the payment lands in your chests." With the
	 * counter broken while the buyer's trade screen is open (shop-counter.md, "Breaking it"), the screen hands over
	 * nothing and keeps the emerald.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopBrokenMidTrade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopBrokenMidTrade")
	public void aCounterBrokenMidTradeSellsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		counter(helper);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		Villager keeper = keeper(helper);
		QaStageFiveGameTests.Client buyer = client(helper);
		ServerPlayer p = buyer.player();
		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
		helper.getLevel().destroyBlock(helper.absolutePos(COUNTER), true);
		p.containerMenu.getSlot(0).set(new ItemStack(Items.EMERALD, 1));
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		int got = p.getInventory().countItem(Items.COBBLESTONE) + (p.containerMenu.getCarried().is(Items.COBBLESTONE) ? p.containerMenu.getCarried().getCount() : 0);
		p.closeContainer();
		helper.assertTrue(got == 0, "the buyer got " + got + " cobblestone from a shop whose counter is gone");
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 64, "the chest lost cobblestone: " + chest.countItem(Items.COBBLESTONE));
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 1, "the emerald didn't come back: " + p.getInventory().countItem(Items.EMERALD));
		helper.succeed();
	}

	/**
	 * shopkeeper.md: "Over their head: how many sales, and "open for business"." A working shopkeeper with 2 sales shows
	 * "Shop · 2 sold" over "open for business".
	 */
	//$ gametest_ticks_batch AREA '400' '"qaShopOverHead"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaShopOverHead")
	public void overTheirHeadHowManySalesAndOpenForBusiness(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(3000);
		counter(helper);
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		ModAttachments.SHOP_SALES.set(keeper, 2);
		helper.succeedWhen(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(keeper, helper.getLevel().getGameTime());
			helper.assertTrue(e != null, "nothing over the shopkeeper's head");
			helper.assertTrue(e.title().getString().equals("Shop · 2 sold") && e.line().getString().equals("open for business"),
				"over their head: " + e.title().getString() + " / " + e.line().getString());
		});
	}

	// ---------------------------------------------------------------- the Price Tag

	/**
	 * price-tag.md: "rename it in an anvil to a number (like 250)"; "The tooltip shows the price it holds"; shopkeeper.md:
	 * "Without CobbleDollars a tag's price is paid in emeralds at the server's rate." Renamed in a real anvil, the tag
	 * holds 250 and its tooltip says what it's paid as.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPriceTagAnvil"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPriceTagAnvil")
	public void aPriceTagRenamedInAnAnvilHoldsThePrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos anvil = new BlockPos(5, 2, 5);
		helper.setBlock(anvil, Blocks.ANVIL);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer p = client.player();
		p.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 6))));
		p.setExperienceLevels(10);
		ContainerLevelAccess access = ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(anvil));
		p.openMenu(new SimpleMenuProvider((id, inventory, who) -> new AnvilMenu(id, inventory, access), Component.literal("Repair & Name")));
		helper.assertTrue(p.containerMenu instanceof AnvilMenu, "no anvil screen: " + p.containerMenu);
		AnvilMenu menu = (AnvilMenu) p.containerMenu;
		menu.getSlot(0).set(new ItemStack(ModItems.PRICE_TAG));
		menu.setItemName("250");
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		p.closeContainer();
		ItemStack tag = ItemStack.EMPTY;
		for (ItemStack s : p.getInventory().items) {
			if (s.is(ModItems.PRICE_TAG)) {
				tag = s;
			}
		}
		helper.assertTrue(!tag.isEmpty(), "no renamed tag came out of the anvil");
		helper.assertTrue(PriceTagItem.dollars(tag) == 250, "the tag holds " + PriceTagItem.dollars(tag));
		List<String> lines = tooltip(helper, tag, p);
		helper.assertTrue(lines.contains("Price: 3 emeralds"), "tooltip: " + lines);
		helper.assertTrue(tooltip(helper, new ItemStack(ModItems.PRICE_TAG), p).stream().anyMatch(l -> l.startsWith("Right-click to set a price")),
			"a blank tag's tooltip: " + tooltip(helper, new ItemStack(ModItems.PRICE_TAG), p));
		helper.succeed();
	}

	/**
	 * shop-counter.md: "100 per emerald" is the rate; a tag is paid in emeralds at it (price-tag.md). 1 and 100 are one
	 * emerald ("1 emerald", singular), 101 is two; the shopkeeper's trade asks the same.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void priceTagsCostEmeraldsAtTheRateOnBothSidesOf100(GameTestHelper helper) {
		helper.assertTrue(Shops.emeraldPrice(tag(1)) == 1, "1 costs " + Shops.emeraldPrice(tag(1)));
		helper.assertTrue(Shops.emeraldPrice(tag(100)) == 1, "100 costs " + Shops.emeraldPrice(tag(100)));
		helper.assertTrue(Shops.emeraldPrice(tag(101)) == 2, "101 costs " + Shops.emeraldPrice(tag(101)));
		helper.assertTrue(Shops.emeraldPrice(tag(1234)) == 13, "1,234 costs " + Shops.emeraldPrice(tag(1234)));
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		List<String> one = tooltip(helper, tag(100), p);
		helper.assertTrue(one.contains("Price: 1 emerald"), "a 100 tag's tooltip: " + one);
		List<String> two = tooltip(helper, tag(101), p);
		helper.assertTrue(two.contains("Price: 2 emeralds"), "a 101 tag's tooltip: " + two);

		ShopCounterBlockEntity counter = counter(helper);
		counter.setItem(ShopCounterBlockEntity.COLUMNS, tag(101));
		helper.setBlock(CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		Villager keeper = keeper(helper);
		Shops.refreshOffers(helper.getLevel(), keeper);
		var cost = keeper.getOffers().get(0).getCostA();
		helper.assertTrue(cost.is(Items.EMERALD) && cost.getCount() == 2, "a 101 tag's trade asks " + cost);
		helper.succeed();
	}

	/**
	 * The infoboxes: Price Tag "paper, a gold nugget and string, anywhere in the grid (makes four)", stack size 16; Shop
	 * Counter "an emerald on top, then planks, a chest and planks", 64, mined with an axe; Travel Post "any sign on top,
	 * then planks, a boat and planks", 64, axe; Travel Ticket stack size 16.
	 */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void recipesStackSizesAndToolsMatchTheInfoboxes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack tags = craft(level, Items.AIR, Items.STRING, Items.AIR, Items.AIR, Items.AIR, Items.GOLD_NUGGET, Items.PAPER, Items.AIR, Items.AIR);
		helper.assertTrue(tags.is(ModItems.PRICE_TAG) && tags.getCount() == 4, "paper, nugget and string made " + tags);
		ItemStack counter = craft(level, Items.AIR, Items.EMERALD, Items.AIR, Items.SPRUCE_PLANKS, Items.CHEST, Items.SPRUCE_PLANKS, Items.AIR, Items.AIR, Items.AIR);
		helper.assertTrue(counter.is(ModBlocks.SHOP_COUNTER.asItem()) && counter.getCount() == 1, "the counter recipe made " + counter);
		ItemStack post = craft(level, Items.AIR, Items.BIRCH_SIGN, Items.AIR, Items.OAK_PLANKS, Items.BIRCH_BOAT, Items.OAK_PLANKS, Items.AIR, Items.AIR, Items.AIR);
		helper.assertTrue(post.is(ModBlocks.TRAVEL_POST.asItem()), "a birch sign and boat made " + post);
		helper.assertTrue(new ItemStack(ModItems.PRICE_TAG).getMaxStackSize() == 16, "price tags stack to " + new ItemStack(ModItems.PRICE_TAG).getMaxStackSize());
		helper.assertTrue(new ItemStack(ModItems.TRAVEL_TICKET).getMaxStackSize() == 16, "tickets stack to " + new ItemStack(ModItems.TRAVEL_TICKET).getMaxStackSize());
		helper.assertTrue(new ItemStack(ModBlocks.SHOP_COUNTER).getMaxStackSize() == 64 && new ItemStack(ModBlocks.TRAVEL_POST).getMaxStackSize() == 64,
			"counters and posts should stack to 64");
		helper.assertTrue(ModBlocks.SHOP_COUNTER.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE)
			&& ModBlocks.TRAVEL_POST.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE), "counter and post should be mined with an axe");
		helper.succeed();
	}

	private static ItemStack craft(ServerLevel level, Item... grid) {
		NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
		for (int i = 0; i < 9; i++) {
			items.set(i, new ItemStack(grid[i]));
		}
		CraftingInput input = CraftingInput.of(3, 3, items);
		return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------- the Travel Post

	/**
	 * travel-post.md: "Name it in an anvil first ("Riverside"): that's the stop's name. Unnamed, it's called after where it
	 * stands ("Post at 120, -48")." The placer knows the post they placed.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPostNames"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPostNames")
	public void aNamedPostKeepsItsNameAndAnUnnamedOneIsCalledAfterWhereItStands(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer p = client.player();
		place(helper, p, new BlockPos(5, 2, 5), namedPost("Riverside"));
		place(helper, p, new BlockPos(20, 2, 20), new ItemStack(ModBlocks.TRAVEL_POST));
		helper.assertBlockPresent(ModBlocks.TRAVEL_POST, new BlockPos(5, 2, 5));
		helper.assertBlockPresent(ModBlocks.TRAVEL_POST, new BlockPos(20, 2, 20));
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		TravelNetwork.Post named = network.at(global(helper, new BlockPos(5, 2, 5)));
		TravelNetwork.Post unnamed = network.at(global(helper, new BlockPos(20, 2, 20)));
		helper.assertTrue(named != null && named.name().equals("Riverside"), "the named post: " + named);
		BlockPos where = helper.absolutePos(new BlockPos(20, 2, 20));
		String expected = "Post at " + where.getX() + ", " + where.getZ();
		helper.assertTrue(unnamed != null && unnamed.name().equals(expected), "the unnamed post: " + unnamed + ", expected " + expected);
		List<TravelNetwork.Post> known = network.known(p.getUUID(), null);
		helper.assertTrue(known.contains(named) && known.contains(unnamed), "the placer knows " + known);
		helper.assertTrue(client.seen().stream().anyMatch(c -> c.getString().startsWith("Travel Post \"Riverside\" is up.")),
			"placing it said " + texts(client.seen()));
		helper.succeed();
	}

	/**
	 * travel-post.md: "Right-click a Travel Post to add it to the posts you know; you're told how many others you know."
	 * Found: the first click; known: a second click. (The count of one, "1 other posts", is in QaShopFerryBug1031Tests.)
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPostFound"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPostFound")
	public void rightClickingAPostAddsItAndSaysHowManyOthers(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos a = new BlockPos(5, 2, 5);
		BlockPos b = new BlockPos(20, 2, 5);
		BlockPos c = new BlockPos(5, 2, 20);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		for (BlockPos pos : List.of(a, b, c)) {
			helper.setBlock(pos, ModBlocks.TRAVEL_POST);
		}
		network.add(global(helper, a), "Alder");
		network.add(global(helper, b), "Brook");
		network.add(global(helper, c), "Cove");
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer p = client.player();
		p.moveTo(Vec3.atCenterOf(helper.absolutePos(a.south())));
		use(helper, p, a, Direction.SOUTH, ItemStack.EMPTY);
		helper.assertTrue(saw(client.seen(), "Found the Travel Post \"Alder\"! Ferrymen elsewhere can now take you here. You know 0 other posts."),
			"first click: " + texts(client.seen()));
		client.seen().clear();
		use(helper, p, a, Direction.SOUTH, ItemStack.EMPTY);
		helper.assertTrue(saw(client.seen(), "Travel Post \"Alder\" · you know 0 other posts"), "second click: " + texts(client.seen()));
		p.moveTo(Vec3.atCenterOf(helper.absolutePos(b.south())));
		use(helper, p, b, Direction.SOUTH, ItemStack.EMPTY);
		p.moveTo(Vec3.atCenterOf(helper.absolutePos(c.south())));
		client.seen().clear();
		use(helper, p, c, Direction.SOUTH, ItemStack.EMPTY);
		helper.assertTrue(saw(client.seen(), "Found the Travel Post \"Cove\"! Ferrymen elsewhere can now take you here. You know 2 other posts."),
			"third post: " + texts(client.seen()));
		helper.assertTrue(network.known(p.getUUID(), null).size() == 3, "knows " + network.known(p.getUUID(), null).size());
		helper.succeed();
	}

	/**
	 * travel-post.md: "A villager without a job nearby takes the post by themselves and becomes its Ferryman."
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFerryByThemselves"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFerryByThemselves")
	public void aJoblessVillagerTakesAPostByThemselves(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(10, 2, 10);
		helper.setBlock(post, ModBlocks.TRAVEL_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 13));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			helper.assertTrue(job(v) == ModVillagers.FERRYMAN, "the villager is a " + job(v));
			helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(helper.absolutePos(post))).orElse(false),
				"works at " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE));
		});
	}

	// ---------------------------------------------------------------- the Ferryman and fares

	/**
	 * ferryman.md: "A ferryman sells a Travel Ticket to every other post you know." One who knows no other post is told
	 * so ("You don't know any other Travel Posts yet...") and gets no trades; after right-clicking another post, the
	 * ferryman sells a ticket to it, named "Ticket to ...".
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFerryNowhere"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFerryNowhere")
	public void aFerrymanSellsOnlyToPostsYouKnowAndSaysWhenThereAreNone(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos home = new BlockPos(5, 2, 5);
		BlockPos away = new BlockPos(24, 2, 24);
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		network.add(global(helper, home), "Home");
		network.add(global(helper, away), "Riverside");
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 5));
		ferryman.setNoAi(true);
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(home), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer p = client.player();
		QaStageFourGameTests.pressOn(p, ferryman, client.seen());
		helper.assertTrue(saw(client.seen(), "You don't know any other Travel Posts yet. Find one and right-click it."), "told: " + texts(client.seen()));
		helper.assertTrue(ferryman.getOffers().isEmpty(), "offers with nowhere to go: " + ferryman.getOffers().size());
		p.closeContainer();

		p.moveTo(Vec3.atCenterOf(helper.absolutePos(away.south())));
		use(helper, p, away, Direction.SOUTH, ItemStack.EMPTY);
		QaStageFourGameTests.pressOn(p, ferryman, client.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "no trade screen once a post is known: " + p.containerMenu);
		helper.assertTrue(ferryman.getOffers().size() == 1, ferryman.getOffers().size() + " tickets, expected one to Riverside");
		ItemStack ticket = ferryman.getOffers().get(0).getResult();
		helper.assertTrue(ticket.getHoverName().getString().equals("Ticket to Riverside"), "the ticket is called " + ticket.getHoverName().getString());
		helper.assertTrue(ferryman.getOffers().get(0).getCostA().is(Items.EMERALD) && ferryman.getOffers().get(0).getCostA().getCount() == 1,
			"a 27-block trip costs " + ferryman.getOffers().get(0).getCostA());
		p.closeContainer();
		helper.succeed();
	}

	/**
	 * ferryman.md: "an emerald for every 256 blocks". 1, 255 and 256 blocks cost 1; 257 and 512 cost 2; 513 costs 3; and
	 * the ferryman asks 2 for a post 300 blocks off.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFerryFares"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFerryFares")
	public void faresAreAnEmeraldForEvery256BlocksOnBothSides(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		GlobalPos from = global(helper, new BlockPos(5, 2, 5));
		int[][] cases = {{1, 1}, {255, 1}, {256, 1}, {257, 2}, {512, 2}, {513, 3}, {1024, 4}, {1025, 5}};
		for (int[] c : cases) {
			GlobalPos to = GlobalPos.of(from.dimension(), from.pos().offset(c[0], 0, 0));
			int fare = TravelNetwork.fare(helper.getLevel().getServer(), from, to, UUID.randomUUID());
			helper.assertTrue(fare == c[1], c[0] + " blocks cost " + fare + ", expected " + c[1]);
		}
		BlockPos home = new BlockPos(5, 2, 5);
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		network.add(from, "Home");
		TravelNetwork.Post far = farPost(helper, helper.absolutePos(home).offset(300, 0, 0), "Faraway");
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 5));
		ferryman.setNoAi(true);
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(home), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		QaStageFiveGameTests.Client client = client(helper);
		network.visit(client.player().getUUID(), far);
		QaStageFourGameTests.pressOn(client.player(), ferryman, client.seen());
		helper.assertTrue(ferryman.getOffers().size() == 1, "offers: " + ferryman.getOffers().size());
		var cost = ferryman.getOffers().get(0).getCostA();
		client.player().closeContainer();
		helper.assertTrue(cost.is(Items.EMERALD) && cost.getCount() == 2, "300 blocks cost " + cost);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Travel Ticket

	/**
	 * travel-ticket.md: "Use it within 16 blocks of any Travel Post and you arrive at the ticket's post." ferryman.md: "The
	 * ticket is used up on the way." 17 blocks off it says where to use it and is kept; 16 blocks off the player sets off,
	 * the ticket is used, and they come to next to the ticket's post.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaTicketRange"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaTicketRange")
	public void aTicketWorksWithin16BlocksOfAPostNotAt17(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos home = new BlockPos(2, 2, 2);
		BlockPos away = new BlockPos(27, 2, 27);
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		network.add(global(helper, home), "Home");
		TravelNetwork.Post dest = network.add(global(helper, away), "Riverside");
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer p = client.player();
		ItemStack tickets = io.github.jcondedata.aliveworkplace.travel.Ferrymen.ticket(dest).copyWithCount(3);
		p.setItemInHand(InteractionHand.MAIN_HAND, tickets);

		p.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 19)))); // 17 blocks from Home, 25 from Riverside
		p.gameMode.useItem(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
		helper.assertFalse(FerryRides.riding(p), "set off 17 blocks from a post");
		helper.assertTrue(p.getMainHandItem().getCount() == 3, "a refused ticket was used: " + p.getMainHandItem().getCount() + " left");
		helper.assertTrue(saw(client.seen(), "Use your ticket within 16 blocks of a Travel Post."), "told: " + texts(client.seen()));

		p.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 18)))); // 16 blocks from Home
		p.gameMode.useItem(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
		helper.assertTrue(FerryRides.riding(p), "didn't set off 16 blocks from a post");
		helper.assertTrue(p.getMainHandItem().getCount() == 2, "the ticket wasn't used up: " + p.getMainHandItem().getCount() + " left");
		helper.runAfterDelay(FerryRides.RIDE_TICKS + 10, () -> {
			helper.assertFalse(FerryRides.riding(p), "still riding");
			helper.assertTrue(p.blockPosition().closerThan(helper.absolutePos(away), 4), "came to at " + helper.relativePos(p.blockPosition()));
			helper.succeed();
		});
	}

	/**
	 * travel-post.md: "Breaking it. Mine it with an axe; it drops itself, and the stop leaves the network." A ticket to it
	 * then says the post isn't there and is kept, not used up.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaTicketGone"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTicketGone")
	public void aTicketToABrokenPostSaysSoAndIsKept(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos home = new BlockPos(2, 2, 2);
		BlockPos away = new BlockPos(24, 2, 24);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer p = client.player();
		place(helper, p, home, namedPost("Home"));
		place(helper, p, away, namedPost("Riverside"));
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		TravelNetwork.Post dest = network.at(global(helper, away));
		helper.assertTrue(dest != null, "setup: Riverside isn't on the network");
		ItemStack ticket = io.github.jcondedata.aliveworkplace.travel.Ferrymen.ticket(dest).copyWithCount(2);

		p.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(away.north())));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		helper.assertTrue(p.gameMode.destroyBlock(helper.absolutePos(away)), "the player couldn't break the post");
		helper.assertBlockNotPresent(ModBlocks.TRAVEL_POST, away);
		helper.assertTrue(network.post(dest.id()) == null, "the broken post is still on the network");
		boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(away)).inflate(2),
			e -> e.getItem().is(ModBlocks.TRAVEL_POST.asItem())).isEmpty() || p.getInventory().countItem(ModBlocks.TRAVEL_POST.asItem()) > 0;
		helper.assertTrue(dropped, "the broken post didn't drop itself");

		p.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(home.south(2))));
		p.setItemInHand(InteractionHand.MAIN_HAND, ticket);
		client.seen().clear();
		p.gameMode.useItem(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
		helper.assertFalse(FerryRides.riding(p), "set off for a post that's gone");
		helper.assertTrue(p.getMainHandItem().getCount() == 2, "the ticket to a gone post was used up");
		helper.assertTrue(client.seen().stream().anyMatch(c -> c.getString().startsWith("The Travel Post \"Riverside\" isn")), "told: " + texts(client.seen()));
		helper.succeed();
	}

	/**
	 * travel-ticket.md: "The ticket is named for where it goes ("Ticket to Riverside"), and its tooltip says where that
	 * post stands." The name, place and destination survive a save and reload of the item.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aTicketIsNamedForWhereItGoesAndSaysWhereThatIs(GameTestHelper helper) {
		BlockPos away = helper.absolutePos(new BlockPos(4, 2, 4)).offset(120, 0, -48);
		TravelNetwork.Post dest = farPost(helper, away, "Riverside");
		ItemStack ticket = io.github.jcondedata.aliveworkplace.travel.Ferrymen.ticket(dest);
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(ticket.getHoverName().getString().equals("Ticket to Riverside"), "named " + ticket.getHoverName().getString());
		List<String> lines = tooltip(helper, ticket, p);
		String where = "Travel Post at " + away.getX() + ", " + away.getZ();
		helper.assertTrue(lines.contains(where), "tooltip " + lines + ", expected " + where);
		helper.assertTrue(lines.contains("Use it within 16 blocks of any Travel Post to go there"), "tooltip " + lines);
		var registries = helper.getLevel().registryAccess();
		ItemStack back = ItemStack.parseOptional(registries, (CompoundTag) ticket.save(registries));
		TicketData data = back.get(ModComponents.TICKET);
		helper.assertTrue(data != null && data.post().equals(dest.id()) && data.destination().pos().equals(away), "after a reload: " + data);
		helper.assertTrue(back.getHoverName().getString().equals("Ticket to Riverside"), "after a reload named " + back.getHoverName().getString());
		helper.succeed();
	}
}
