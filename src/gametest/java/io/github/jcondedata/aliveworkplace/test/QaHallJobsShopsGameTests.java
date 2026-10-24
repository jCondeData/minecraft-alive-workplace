package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.HallRetraining;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1024-0953): mutation survivors from the nightly run, and the Village Hall's job pages (ROADMAP 36.9, 36.18)
 * from their specs, not the code.
 *
 * <p>Survivors: a shopkeeper's trade screen must refuse the lot when the chests were emptied with the screen open (B348);
 * a Shop Counter gives nothing to a hopper under it and takes nothing from one above ("players can't steal a shop's stock
 * with hoppers"); one single item handed over at the Storehouse board comes off the worker's request at once (B344).
 *
 * <p>36.9: "the Village Hall has a Free job blocks page listing every job block in the village with no villager on it ...
 * and for each, the villagers without a job; picking one assigns that block to them (they claim the POI and walk to it)".
 * 36.18: "a villager with a job gets a Change job button ... picking one shows the price (5-50 emeralds by level), a second
 * click pays ... not enough emeralds (nothing changes) ... game rule workplaceRetraining off hides the button". Every test
 * clicks the hall the way a player does (ChoiceMenu.clicked, and where marked, the client's real container-click packet
 * on the screen the hall opens), including the ways a click arrives late or wrong.
 */
public class QaHallJobsShopsGameTests implements FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("QaHallJobsShops");
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos LECTERN = new BlockPos(4, 2, 4);
	private static final BlockPos STANDING = new BlockPos(5, 2, 5);
	private static final BlockPos STOREHOUSE = new BlockPos(18, 2, 17);
	private static final BlockPos COMPOSTER = new BlockPos(16, 2, 4);

	// --- helpers ---------------------------------------------------------------------------------------------------

	/** The hall and a free Storehouse, the village kept to this test's area. */
	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
	}

	/** Dara, a librarian at her lectern, at {@code level}, standing still. */
	private static Villager librarian(GameTestHelper helper, int level) {
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		villager.setNoAi(true);
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		villager.setVillagerData(villager.getVillagerData().setLevel(level));
		villager.setVillagerXp(level == 1 ? 5 : 70 * level);
		villager.setCustomName(Component.literal("Dara"));
		return villager;
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at, String name) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		villager.setCustomName(Component.literal(name));
		return villager;
	}

	private static ServerPlayer payer(GameTestHelper helper, List<String> seen, int emeralds) {
		ServerPlayer player = listeningPlayer(helper, seen);
		if (emeralds > 0) {
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, emeralds));
		}
		return player;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static int resident(ChoiceMenu menu, String name) {
		for (int s = VillageHallScreen.FIRST_PERSON; s < ChoiceMenu.SIZE; s++) {
			if (menu.icon(s).getHoverName().getString().startsWith(name)) {
				return s;
			}
		}
		throw new GameTestAssertException(name + " isn't on the hall's list");
	}

	private static int slotWith(ChoiceMenu menu, net.minecraft.world.item.Item item) {
		for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
			if (menu.icon(s).is(item)) {
				return s;
			}
		}
		return -1;
	}

	/** The hall's list, {@code name} right-clicked, then Change job: the page of jobs they could change to. */
	private static ChoiceMenu jobsPage(GameTestHelper helper, ServerPlayer player, String name) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		menu.clicked(resident(menu, name), 1, ClickType.PICKUP, player);
		ItemStack change = menu.icon(HallRetraining.CHANGE);
		helper.assertTrue(change.is(Items.EMERALD) && change.getHoverName().getString().equals("Change job"), "no Change job button: " + change);
		menu.clicked(HallRetraining.CHANGE, 0, ClickType.PICKUP, player);
		return menu;
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	/** A real client standing by the hall, with the hall's own screen opened as a right-click on the block opens it. */
	private static QaStageFiveGameTests.Client atTheHall(GameTestHelper helper, String prefix) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(prefix));
		c.player().moveTo(Vec3.atCenterOf(helper.absolutePos(HALL.offset(-2, 0, -2))));
		VillageHallScreen.open(c.player(), helper.absolutePos(HALL));
		helper.assertTrue(c.player().containerMenu instanceof ChoiceMenu, "the hall's screen didn't open: " + c.player().containerMenu);
		return c;
	}

	private static void assertUnchanged(GameTestHelper helper, Villager dara, BlockPos lectern, ServerPlayer player, int emeralds, String why) {
		helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, why + ": retrained anyway: " + dara.getVillagerData().getProfession());
		helper.assertTrue(site(dara).equals(Optional.of(lectern)), why + ": her job site moved: " + site(dara));
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == emeralds, why + ": money taken: " + player.getInventory().countItem(Items.EMERALD) + " of " + emeralds);
		helper.assertTrue(!ModAttachments.RETRAIN_WALK.has(dara), why + ": she's on her way somewhere");
	}

	// --- A. mutation survivors ---------------------------------------------------------------------------------------

	/**
	 * B348: the shop's chests emptied while a buyer has the trade screen open, the emerald already in. The result slot may
	 * not be picked up (mayPickup false for the buyer), a shift-click on it hands nothing over, and the chest gains no
	 * emerald. (Kills MerchantResultSlotMixin `return false` -> `return true`.)
	 */
	//$ gametest_batch AREA '"qaHjsTradeEmptied"'
	@GameTest(template = AREA, batch = "qaHjsTradeEmptied")
	public void theTradeSlotRefusesALotTheChestsNoLongerHold(GameTestHelper helper) {
		QaEconomyGameTests.Shop shop = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1),
			new ItemStack(Items.COBBLESTONE, 32));
		QaStageFiveGameTests.Client c = QaEconomyGameTests.buyer(helper);
		ServerPlayer p = c.player();
		QaStageFourGameTests.pressOn(p, shop.keeper(), c.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "setup: the right-click didn't open the trades: " + p.containerMenu);
		MerchantMenu menu = (MerchantMenu) p.containerMenu;
		menu.getSlot(0).set(new ItemStack(Items.EMERALD, 1));
		helper.assertTrue(menu.getSlot(2).getItem().is(Items.COBBLESTONE), "setup: the result slot shows " + menu.getSlot(2).getItem());
		helper.assertTrue(menu.getSlot(2).mayPickup(p), "setup: a stocked lot can't be taken");
		shop.chest().clearContent();
		helper.assertFalse(menu.getSlot(2).mayPickup(p), "the lot can be taken although the chests are empty");
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		helper.assertTrue(QaEconomyGameTests.carried(p, Items.COBBLESTONE) == 0, "the buyer got " + QaEconomyGameTests.carried(p, Items.COBBLESTONE) + " cobblestone from an empty shop");
		helper.assertTrue(shop.chest().countItem(Items.EMERALD) == 0, "the empty shop was paid " + shop.chest().countItem(Items.EMERALD));
		p.closeContainer();
		helper.assertTrue(QaEconomyGameTests.carried(p, Items.EMERALD) == 1, "the unspent emerald didn't come back: " + QaEconomyGameTests.carried(p, Items.EMERALD));
		helper.succeed();
	}

	/**
	 * "Players can't steal a shop's stock with hoppers": a hopper under a Shop Counter with a lot and its price pulls
	 * nothing out in 60 ticks, a hopper above with bread pushes nothing in, and the counter refuses every slot through
	 * every face (what other mods' pipes ask it). (Kills ShopCounterBlockEntity#canTakeItemThroughFace -> true.)
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHjsCounterHoppers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHjsCounterHoppers")
	public void hoppersNeitherTakeFromNorFillAShopCounter(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterAt = new BlockPos(3, 3, 3);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		helper.setBlock(counterAt, ModBlocks.SHOP_COUNTER);
		helper.setBlock(new BlockPos(3, 4, 3), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterAt);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.DIAMOND, 4));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		HopperBlockEntity below = helper.getBlockEntity(new BlockPos(3, 2, 3));
		HopperBlockEntity above = helper.getBlockEntity(new BlockPos(3, 4, 3));
		above.setItem(0, new ItemStack(Items.BREAD, 5));
		ItemStack probe = new ItemStack(Items.DIAMOND);
		for (int slot = 0; slot < counter.getContainerSize(); slot++) {
			for (Direction face : Direction.values()) {
				helper.assertFalse(counter.canTakeItemThroughFace(slot, probe, face), "slot " + slot + " gives through " + face);
				helper.assertFalse(counter.canPlaceItemThroughFace(slot, probe, face), "slot " + slot + " takes in through " + face);
			}
		}
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(below.isEmpty(), "the hopper under the counter pulled " + below.getItem(0) + " " + below.getItem(1));
			helper.assertTrue(above.countItem(Items.BREAD) == 5, "the hopper above pushed " + (5 - above.countItem(Items.BREAD)) + " bread in");
			helper.assertTrue(counter.getItem(0).is(Items.DIAMOND) && counter.getItem(0).getCount() == 4
				&& counter.getItem(ShopCounterBlockEntity.COLUMNS).is(Items.EMERALD) && counter.getItem(ShopCounterBlockEntity.COLUMNS).getCount() == 2,
				"the counter's lot and price changed: " + counter.getItem(0) + ", " + counter.getItem(ShopCounterBlockEntity.COLUMNS));
			for (int slot = 0; slot < counter.getContainerSize(); slot++) {
				helper.assertTrue(!counter.getItem(slot).is(Items.BREAD), "bread got into slot " + slot);
			}
			helper.succeed();
		});
	}

	/**
	 * B344 at its boundary: ONE cobblestone handed over at the Storehouse board (the client's real click) comes off the
	 * builder's request at once: the board asks for 24, not 25, and the builder's missing list says 24.
	 * (Kills Requests.lower `count <= 0` -> `count <= 1`.)
	 */
	//$ gametest_ticks_batch SMALL '600' '"qaHjsBoardOne"'
	@GameTest(template = SMALL, timeoutTicks = 600, batch = "qaHjsBoardOne")
	public void handingOverOneItemLowersTheRequestAtOnce(GameTestHelper helper) {
		QaStageSixGameTests.Waiting w = QaStageSixGameTests.waitingBuilder(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(w.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "setup: the builder isn't waiting: " + w.site().status());
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHjsOne"));
			ServerPlayer p = c.player();
			Container chest = helper.getBlockEntity(w.chest());
			p.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 1));
			QaStageFourGameTests.press(helper, p, w.storehouse());
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "setup: the board didn't open: " + p.containerMenu);
			ChoiceMenu board = (ChoiceMenu) p.containerMenu;
			int slot = QaStageSixGameTests.slotOf(board, Items.COBBLESTONE);
			helper.assertTrue(slot >= 0 && board.icon(slot).getCount() == 25, "setup: asks for " + (slot < 0 ? "none" : board.icon(slot).getCount()));
			QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 1 && p.getInventory().countItem(Items.COBBLESTONE) == 0,
				"the click: chest " + chest.countItem(Items.COBBLESTONE) + ", kept " + p.getInventory().countItem(Items.COBBLESTONE));
			int after = QaStageSixGameTests.slotOf(board, Items.COBBLESTONE);
			helper.assertTrue(after >= 0 && board.icon(after).getCount() == 24, "after one the board asks for " + (after < 0 ? "none" : board.icon(after).getCount()));
			Integer missing = Builders.activeSite(helper.getLevel(), w.builder()).missing().get(Items.COBBLESTONE);
			helper.assertTrue(missing != null && missing == 24, "the builder's missing list: " + missing);
			helper.succeed();
		});
	}

	// --- B. 36.9: the Free job blocks page -----------------------------------------------------------------------------

	/**
	 * The player's own way, with the client's real clicks on the screen a hall opens: the Free job blocks tab, the
	 * Storehouse, then Jo: Jo is the Porter at once, the Storehouse is his job site and claimed, he walks to it, and the
	 * player reads "Jo is now a Porter".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHjsFreeReal"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsFreeReal")
	public void aFreeBlockIsGivenWithTheClientsOwnClicks(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos store = helper.absolutePos(STOREHOUSE);
		Villager jo = jobless(helper, new BlockPos(9, 2, 14), "Jo");
		helper.runAfterDelay(2, () -> {
			QaStageFiveGameTests.Client c = atTheHall(helper, "qaHjsF");
			ServerPlayer p = c.player();
			ChoiceMenu menu = (ChoiceMenu) p.containerMenu;
			int tab = HallPages.slot(FreeJobsPage.PAGE);
			QaStageSixGameTests.click(p, tab, 0, ClickType.PICKUP);
			helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "the tab didn't open the page: " + menu.icon(4));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(ModBlocks.STOREHOUSE.asItem()), "the Storehouse isn't listed: " + menu.icon(VillageHallScreen.FIRST_ROW));
			QaStageSixGameTests.click(p, VillageHallScreen.FIRST_ROW, 0, ClickType.PICKUP);
			helper.assertTrue(lore(menu.icon(VillageHallScreen.FIRST_ROW)).contains("Click: Jo becomes a Porter"), "Jo's line: " + lore(menu.icon(VillageHallScreen.FIRST_ROW)));
			QaStageSixGameTests.click(p, VillageHallScreen.FIRST_ROW, 0, ClickType.PICKUP);
			helper.assertTrue(jo.getVillagerData().getProfession() == ModVillagers.PORTER, "not a Porter: " + jo.getVillagerData().getProfession());
			helper.assertTrue(site(jo).equals(Optional.of(store)), "job site: " + site(jo));
			helper.assertTrue(level.getPoiManager().getFreeTickets(store) == 0, "the Storehouse isn't claimed");
			helper.assertTrue(jo.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getTarget().currentBlockPosition().equals(store)).orElse(false),
				"not walking to it");
			Component told = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.hall.assigned");
			helper.assertTrue(told != null, "not told, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, told, "Jo is now a Porter");
			helper.assertTrue(p.containerMenu.getCarried().isEmpty() && p.getInventory().isEmpty(), "an icon ended up with the player");
			helper.succeed();
		});
	}

	/** The block broken between opening its page and picking Jo: Jo stays jobless, nothing is claimed, the list drops it. */
	//$ gametest_ticks_batch AREA '100' '"qaHjsFreeBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsFreeBroken")
	public void aBlockBrokenBeforeThePickGivesNothing(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager jo = jobless(helper, new BlockPos(9, 2, 14), "Jo");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 0);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			menu.clicked(HallPages.slot(FreeJobsPage.PAGE), 0, ClickType.PICKUP, player);
			menu.clicked(VillageHallScreen.FIRST_ROW, 0, ClickType.PICKUP, player);
			helper.assertTrue(menu.icon(4).is(ModBlocks.STOREHOUSE.asItem()), "setup: the Storehouse's page: " + menu.icon(4));
			helper.setBlock(STOREHOUSE, Blocks.AIR);
			try {
				menu.clicked(VillageHallScreen.FIRST_ROW, 0, ClickType.PICKUP, player);
			} catch (RuntimeException e) {
				throw new GameTestAssertException("the pick threw " + e);
			}
			helper.assertTrue(jo.getVillagerData().getProfession() == VillagerProfession.NONE, "Jo got a job at a broken block: " + jo.getVillagerData().getProfession());
			helper.assertTrue(site(jo).isEmpty(), "Jo has a job site: " + site(jo));
			helper.assertTrue(FreeJobsPage.blocks(level, hall).isEmpty(), "a broken block is still free: " + FreeJobsPage.blocks(level, hall));
			helper.assertTrue(slotWith(menu, ModBlocks.STOREHOUSE.asItem()) < 0, "the broken Storehouse is still on the page");
			helper.succeed();
		});
	}

	/**
	 * Nobody without a job: the block's page says "Nobody without a job can take this block" and no click on it gives the
	 * nitwit, the child or the librarian the Storehouse.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHjsFreeNobody"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsFreeNobody")
	public void aBlockWithNobodyJoblessOffersNobody(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager dara = librarian(helper, 1);
		Villager nitwit = jobless(helper, new BlockPos(9, 2, 14), "Nim");
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Villager child = jobless(helper, new BlockPos(13, 2, 14), "Kid");
		child.setBaby(true);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 0);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			menu.clicked(HallPages.slot(FreeJobsPage.PAGE), 0, ClickType.PICKUP, player);
			int store = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			helper.assertTrue(store >= 0, "setup: the Storehouse isn't listed");
			helper.assertTrue(lore(menu.icon(store)).contains("No villager without a job can take it"), "the list line: " + lore(menu.icon(store)));
			menu.clicked(store, 0, ClickType.PICKUP, player);
			helper.assertTrue(menu.icon(4).is(ModBlocks.STOREHOUSE.asItem()), "setup: the Storehouse's page: " + menu.icon(4));
			boolean said = false;
			for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
				said |= menu.icon(s).getHoverName().getString().equals("Nobody without a job can take this block");
			}
			helper.assertTrue(said, "the page doesn't say nobody can take it");
			for (int s = 1; s < ChoiceMenu.SIZE; s++) {
				if (menu.icon(4).is(ModBlocks.STOREHOUSE.asItem())) {
					menu.clicked(s, 0, ClickType.PICKUP, player);
				}
			}
			helper.assertTrue(nitwit.getVillagerData().getProfession() == VillagerProfession.NITWIT && child.getVillagerData().getProfession() == VillagerProfession.NONE
				&& dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "someone was given the Storehouse");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(STOREHOUSE)) == 1, "the Storehouse was claimed");
			helper.succeed();
		});
	}

	/**
	 * Clicks a player's client could send that aren't buttons, on the Storehouse's page with Jo on it, through the real
	 * packet: slots -999, -1, 54, 89, 200; and on Jo's own slot a number-key swap, a throw, a creative clone, a pickup-all
	 * and a middle button. None gives Jo the job, throws, or puts an icon in the player's hands; a real click after them
	 * still works.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHjsFreeJunk"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsFreeJunk")
	public void madeUpClicksOnTheFreeJobsPageDoNothing(GameTestHelper helper) {
		village(helper);
		Villager jo = jobless(helper, new BlockPos(9, 2, 14), "Jo");
		helper.runAfterDelay(2, () -> {
			QaStageFiveGameTests.Client c = atTheHall(helper, "qaHjsJ");
			ServerPlayer p = c.player();
			ChoiceMenu menu = (ChoiceMenu) p.containerMenu;
			QaStageSixGameTests.click(p, HallPages.slot(FreeJobsPage.PAGE), 0, ClickType.PICKUP);
			QaStageSixGameTests.click(p, VillageHallScreen.FIRST_ROW, 0, ClickType.PICKUP);
			helper.assertTrue(menu.icon(4).is(ModBlocks.STOREHOUSE.asItem()), "setup: the Storehouse's page: " + menu.icon(4));
			List<String> wrong = new ArrayList<>();
			Object[][] clicks = {{-999, 0, ClickType.PICKUP}, {-1, 0, ClickType.PICKUP}, {54, 0, ClickType.PICKUP}, {89, 0, ClickType.QUICK_MOVE},
				{200, 0, ClickType.PICKUP}, {VillageHallScreen.FIRST_ROW, 0, ClickType.SWAP}, {VillageHallScreen.FIRST_ROW, 40, ClickType.SWAP},
				{VillageHallScreen.FIRST_ROW, 0, ClickType.THROW}, {VillageHallScreen.FIRST_ROW, 2, ClickType.CLONE},
				{VillageHallScreen.FIRST_ROW, 0, ClickType.PICKUP_ALL}, {VillageHallScreen.FIRST_ROW, 0, ClickType.QUICK_CRAFT}};
			for (Object[] k : clicks) {
				try {
					QaStageSixGameTests.click(p, (Integer) k[0], (Integer) k[1], (ClickType) k[2]);
				} catch (RuntimeException e) {
					wrong.add(k[2] + " " + k[0] + " threw " + e);
				}
				if (jo.getVillagerData().getProfession() != VillagerProfession.NONE) {
					wrong.add(k[2] + " " + k[0] + "/" + k[1] + " gave Jo a job");
				}
				if (!p.containerMenu.getCarried().isEmpty() || !p.getInventory().isEmpty()) {
					wrong.add(k[2] + " " + k[0] + " left an icon with the player");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "made-up clicks: " + wrong);
			helper.assertTrue(p.containerMenu == menu, "the screen closed: " + p.containerMenu);
			if (!menu.icon(4).is(ModBlocks.STOREHOUSE.asItem())) {
				QaStageSixGameTests.click(p, HallPages.slot(FreeJobsPage.PAGE), 0, ClickType.PICKUP);
				QaStageSixGameTests.click(p, VillageHallScreen.FIRST_ROW, 0, ClickType.PICKUP);
			}
			QaStageSixGameTests.click(p, VillageHallScreen.FIRST_ROW, 0, ClickType.PICKUP);
			helper.assertTrue(jo.getVillagerData().getProfession() == ModVillagers.PORTER, "a real click after the junk didn't give the job: " + jo.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	// --- B. 36.18: Change job ------------------------------------------------------------------------------------------

	/**
	 * The player's own way, with the client's real clicks: right-click Dara on the list, Change job, the Storehouse twice.
	 * She's the Porter at once with the Storehouse claimed, the Apprentice price (10) is paid, and the hall shows her new job.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainReal"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainReal")
	public void aJobIsChangedWithTheClientsOwnClicks(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos store = helper.absolutePos(STOREHOUSE);
		Villager dara = librarian(helper, 2);
		helper.runAfterDelay(2, () -> {
			QaStageFiveGameTests.Client c = atTheHall(helper, "qaHjsR");
			ServerPlayer p = c.player();
			p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 12));
			ChoiceMenu menu = (ChoiceMenu) p.containerMenu;
			QaStageSixGameTests.click(p, resident(menu, "Dara"), 1, ClickType.PICKUP);
			helper.assertTrue(menu.icon(HallRetraining.CHANGE).is(Items.EMERALD), "no Change job button after a right-click: " + menu.icon(HallRetraining.CHANGE));
			helper.assertTrue(lore(menu.icon(HallRetraining.CHANGE)).contains("Retraining costs 10 emeralds (Apprentice)"), "the button's price: " + lore(menu.icon(HallRetraining.CHANGE)));
			QaStageSixGameTests.click(p, HallRetraining.CHANGE, 0, ClickType.PICKUP);
			int slot = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			helper.assertTrue(slot >= 0, "the Storehouse isn't offered");
			QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && p.getInventory().countItem(Items.EMERALD) == 12,
				"the first click changed something");
			QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.PORTER && site(dara).equals(Optional.of(store)),
				"not the Porter at the Storehouse: " + dara.getVillagerData().getProfession() + " " + site(dara));
			helper.assertTrue(level.getPoiManager().getFreeTickets(store) == 0, "the Storehouse isn't claimed");
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 2, "paid " + (12 - p.getInventory().countItem(Items.EMERALD)));
			helper.assertTrue(menu.icon(resident(menu, "Dara")).is(ModBlocks.STOREHOUSE.asItem()), "the hall doesn't show her new job: " + menu.icon(resident(menu, "Dara")));
			helper.assertTrue(p.containerMenu.getCarried().isEmpty(), "an icon is on the cursor");
			helper.succeed();
		});
	}

	/** The Apprentice price from both sides: 9 emeralds changes nothing and says so; with exactly 10 she's retrained and none are left. */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainBoundary"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainBoundary")
	public void theApprenticePriceIsExactlyTen(GameTestHelper helper) {
		village(helper);
		BlockPos lectern = helper.absolutePos(LECTERN);
		Villager dara = librarian(helper, 2);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 9);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			int slot = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			assertUnchanged(helper, dara, lectern, player, 9, "9 emeralds");
			helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Retraining Dara costs 10 emeralds: you're 1 emerald") && s.endsWith("short. Nothing changed.")),
				"not told the price and how short: " + seen);
			player.getInventory().setItem(10, new ItemStack(Items.EMERALD, 1));
			slot = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.PORTER, "exactly 10 didn't retrain her: " + dara.getVillagerData().getProfession());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "left with " + player.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/**
	 * The Storehouse broken between the click that shows the price and the click that pays: nothing changes, no money is
	 * taken, and she still works at her lectern.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainBroken")
	public void aBlockBrokenBetweenTheClicksChangesNothing(GameTestHelper helper) {
		village(helper);
		BlockPos lectern = helper.absolutePos(LECTERN);
		Villager dara = librarian(helper, 3);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 30);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			int slot = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			helper.setBlock(STOREHOUSE, Blocks.AIR);
			try {
				menu.clicked(slot, 0, ClickType.PICKUP, player);
			} catch (RuntimeException e) {
				throw new GameTestAssertException("the paying click threw " + e);
			}
			assertUnchanged(helper, dara, lectern, player, 30, "the block broken");
			helper.assertTrue(slotWith(menu, ModBlocks.STOREHOUSE.asItem()) < 0, "the broken Storehouse is still offered");
			helper.succeed();
		});
	}

	/** Another villager given the Storehouse between the two clicks: nothing changes for Dara and the player keeps his money. */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainTaken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainTaken")
	public void aBlockTakenBetweenTheClicksChangesNothing(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		BlockPos lectern = helper.absolutePos(LECTERN);
		Villager dara = librarian(helper, 1);
		Villager jo = jobless(helper, new BlockPos(9, 2, 14), "Jo");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			int slot = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			FreeJobsPage.FreeBlock block = FreeJobsPage.at(level, hall, store);
			helper.assertTrue(block != null && FreeJobsPage.give(level, jo, block.station()), "setup: Jo couldn't take the Storehouse");
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			assertUnchanged(helper, dara, lectern, player, 5, "taken meanwhile");
			helper.assertTrue(site(jo).equals(Optional.of(store)), "Jo lost the Storehouse: " + site(jo));
			helper.assertTrue(seen.contains("Someone took that workstation first"), "not told: " + seen);
			helper.succeed();
		});
	}

	/**
	 * A first click on the Storehouse and then a click on the composter doesn't pay for either: the composter's click shows
	 * its price. Its second click makes her the Farmer at the composter, not the Porter.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainSwitch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainSwitch")
	public void aClickOnAnotherBlockShowsItsPriceInsteadOfPaying(GameTestHelper helper) {
		village(helper);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		BlockPos lectern = helper.absolutePos(LECTERN);
		Villager dara = librarian(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			int store = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			int composter = slotWith(menu, Items.COMPOSTER);
			helper.assertTrue(store >= 0 && composter >= 0, "setup: offered " + store + ", " + composter);
			menu.clicked(store, 0, ClickType.PICKUP, player);
			composter = slotWith(menu, Items.COMPOSTER);
			menu.clicked(composter, 0, ClickType.PICKUP, player);
			assertUnchanged(helper, dara, lectern, player, 5, "a click on another block");
			helper.assertTrue(seen.contains("Dara (Librarian, Novice) can be retrained as a Farmer for 5 emeralds. Their trades and level are lost: they start again as a Novice."),
				"the composter's price wasn't shown: " + seen);
			composter = slotWith(menu, Items.COMPOSTER);
			menu.clicked(composter, 0, ClickType.PICKUP, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.FARMER && site(dara).equals(Optional.of(helper.absolutePos(COMPOSTER))),
				"not the Farmer at the composter: " + dara.getVillagerData().getProfession() + " " + site(dara));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "paid " + (5 - player.getInventory().countItem(Items.EMERALD)));
			helper.succeed();
		});
	}

	/**
	 * A stranger at the hall can't retrain another player's builder: both clicks change nothing, he keeps his emeralds and
	 * reads "Bob works for someone else: only they can change their job." The builder's own employer can.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainStranger")
	public void aStrangerCannotRetrainSomeoneElsesBuilder(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(3, 2, 8);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager bob = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 8));
		bob.setNoAi(true);
		bob.setCustomName(Component.literal("Bob"));
		Builders.employ(level, bob, helper.absolutePos(table));
		List<String> ownerSeen = new ArrayList<>();
		ServerPlayer owner = payer(helper, ownerSeen, 5);
		Friends.hire(owner, bob);
		List<String> seen = new ArrayList<>();
		ServerPlayer stranger = payer(helper, seen, 64);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!stranger.hasPermissions(2), "setup: the stranger is an operator");
			ChoiceMenu menu = jobsPage(helper, stranger, "Bob");
			int slot = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			menu.clicked(slot, 0, ClickType.PICKUP, stranger);
			menu.clicked(slot, 0, ClickType.PICKUP, stranger);
			helper.assertTrue(bob.getVillagerData().getProfession() == ModVillagers.BUILDER && site(bob).equals(Optional.of(helper.absolutePos(table))),
				"a stranger retrained Bob: " + bob.getVillagerData().getProfession() + " " + site(bob));
			helper.assertTrue(stranger.getInventory().countItem(Items.EMERALD) == 64, "the stranger paid " + (64 - stranger.getInventory().countItem(Items.EMERALD)));
			helper.assertTrue(seen.contains("Bob works for someone else: only they can change their job."), "not told: " + seen);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(STOREHOUSE)) == 1, "the Storehouse was claimed");
			ChoiceMenu own = jobsPage(helper, owner, "Bob");
			slot = slotWith(own, ModBlocks.STOREHOUSE.asItem());
			own.clicked(slot, 0, ClickType.PICKUP, owner);
			own.clicked(slot, 0, ClickType.PICKUP, owner);
			helper.assertTrue(bob.getVillagerData().getProfession() == ModVillagers.PORTER, "his employer couldn't retrain Bob: " + bob.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	/** The rule turned off with the jobs page open: the paying click changes nothing and takes nothing. */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainRuleOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainRuleOff")
	public void theRuleTurnedOffMidwayStopsThePayingClick(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(LECTERN);
		Villager dara = librarian(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			int slot = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			level.getGameRules().getRule(ModGameRules.RETRAINING).set(false, level.getServer());
			try {
				menu.clicked(slot, 0, ClickType.PICKUP, player);
				assertUnchanged(helper, dara, lectern, player, 5, "the rule off");
				helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(STOREHOUSE)) == 1, "the Storehouse was claimed");
			} finally {
				level.getGameRules().getRule(ModGameRules.RETRAINING).set(true, level.getServer());
			}
			helper.succeed();
		});
	}

	/** Dara dies between the two clicks: nothing is paid and the Storehouse stays free. */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainGone"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainGone")
	public void aVillagerGoneBetweenTheClicksCostsNothing(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		Villager dara = librarian(helper, 5);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 50);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			int slot = slotWith(menu, ModBlocks.STOREHOUSE.asItem());
			menu.clicked(slot, 0, ClickType.PICKUP, player);
			dara.kill();
			try {
				menu.clicked(slot, 0, ClickType.PICKUP, player);
			} catch (RuntimeException e) {
				throw new GameTestAssertException("the paying click threw " + e);
			}
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 50, "paid " + (50 - player.getInventory().countItem(Items.EMERALD)) + " for a dead villager");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(STOREHOUSE)) == 1, "the Storehouse was claimed for a dead villager");
			helper.assertTrue(!dara.isAlive() && !ModAttachments.RETRAIN_WALK.has(dara), "the dead villager is on her way to the Storehouse");
			helper.succeed();
		});
	}

	/** A villager without a job has no Change job (they take a block from the Free job blocks page, for free). */
	//$ gametest_ticks_batch AREA '100' '"qaHjsRetrainJobless"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHjsRetrainJobless")
	public void aJoblessVillagerHasNoChangeJobButton(GameTestHelper helper) {
		village(helper);
		Villager jo = jobless(helper, new BlockPos(9, 2, 14), "Jo");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 20);
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(HallRetraining.canRetrain(helper.getLevel(), jo), "a jobless villager can be retrained");
			ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
			menu.clicked(resident(menu, "Jo"), 1, ClickType.PICKUP, player);
			helper.assertTrue(!menu.icon(HallRetraining.CHANGE).getHoverName().getString().equals("Change job"), "a jobless villager has a Change job button");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 20 && jo.getVillagerData().getProfession() == VillagerProfession.NONE, "something changed");
			helper.succeed();
		});
	}

	/** Every sentence the two pages show exists in en_us.json and reads right with its arguments. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHallJobPagesTextIsTranslated(GameTestHelper helper) {
		String[] keys = {"screen.aliveworkplace.free_jobs", "screen.aliveworkplace.free_jobs.title", "screen.aliveworkplace.free_jobs.about",
			"screen.aliveworkplace.free_jobs.retrain", "screen.aliveworkplace.free_jobs.in", "screen.aliveworkplace.free_jobs.outside",
			"screen.aliveworkplace.free_jobs.nobody", "screen.aliveworkplace.free_jobs.takers", "screen.aliveworkplace.free_jobs.takers.one",
			"screen.aliveworkplace.free_jobs.click", "screen.aliveworkplace.free_jobs.back", "screen.aliveworkplace.free_jobs.none_jobless",
			"screen.aliveworkplace.free_jobs.none_jobless_hint", "screen.aliveworkplace.hall.give_job", "message.aliveworkplace.hall.assigned",
			"message.aliveworkplace.hall.not_assigned", "message.aliveworkplace.free_jobs.found_work", "screen.aliveworkplace.hall_retrain.change",
			"screen.aliveworkplace.hall_retrain.change_about", "screen.aliveworkplace.hall_retrain.price", "screen.aliveworkplace.hall_retrain.lost",
			"screen.aliveworkplace.hall_retrain.free", "screen.aliveworkplace.hall_retrain.free.one", "screen.aliveworkplace.hall_retrain.change_click",
			"screen.aliveworkplace.hall_retrain.pick", "screen.aliveworkplace.hall_retrain.pay", "screen.aliveworkplace.hall_retrain.no_table",
			"message.aliveworkplace.hall_retrain.confirm", "message.aliveworkplace.hall_retrain.not_yours", "message.aliveworkplace.retrain.offer",
			"message.aliveworkplace.retrain.short", "message.aliveworkplace.retrain.done"};
		List<String> missing = new ArrayList<>();
		for (String key : keys) {
			if (Component.translatable(key).getString().equals(key)) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "untranslated: " + missing);
		Component dara = Component.literal("Dara");
		Component porter = Component.literal("Porter");
		String[][] rendered = {
			{Component.translatable("screen.aliveworkplace.hall_retrain.pick", dara, porter).getString(), "Click: see the price for Dara to become a Porter"},
			{Component.translatable("screen.aliveworkplace.hall_retrain.pay", "20 emeralds", dara, porter).getString(), "Click again to pay 20 emeralds: Dara becomes a Porter"},
			{Component.translatable("screen.aliveworkplace.hall_retrain.free.one", 1).getString(), "1 free job block to choose from"},
			{Component.translatable("screen.aliveworkplace.hall_retrain.free", 3).getString(), "3 free job blocks to choose from"},
			{Component.translatable("message.aliveworkplace.retrain.done", dara, "Librarian", porter, "Storehouse", "20 emeralds").getString(),
				"Dara gave up the Librarian job and was retrained as a Porter at the Storehouse, for 20 emeralds."},
			{Component.translatable("message.aliveworkplace.free_jobs.found_work", dara).getString(), "Dara found work meanwhile"},
			{Component.translatable("screen.aliveworkplace.free_jobs.takers.one", 1).getString(), "1 villager without a job could take it"}};
		for (String[] r : rendered) {
			helper.assertTrue(r[0].equals(r[1]), "reads '" + r[0] + "', the spec's wording is '" + r[1] + "'");
		}
		LOGGER.info("qaHjs lang: {} keys translated, {} sentences rendered", keys.length, rendered.length);
		helper.succeed();
	}
}
