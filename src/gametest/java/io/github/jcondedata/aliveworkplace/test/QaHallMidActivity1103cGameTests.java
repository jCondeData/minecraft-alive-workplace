package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.GuardRally;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1103, round 3): the Village Hall's People table, Job ▾ and No job (40.3), used on a villager in the middle
 * of something: a guard running to the rung village bell, a shopkeeper whose trade screen another player has open, and
 * a guard friend with an open "tools of my trade" request who is given No job. Every change through the hall's own menu
 * clicks. Written from the spec, docs/wiki/village-hall.md, docs/guide/guard.md and personal-requests.md: the old job's
 * duties end at once, its trades go, and nothing a player or chest holds is lost or made twice.
 */
public class QaHallMidActivity1103cGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos BELL = new BlockPos(15, 2, 4);
	private static final BlockPos GRIND_A = new BlockPos(9, 2, 24);
	private static final BlockPos GRIND_B = new BlockPos(21, 2, 24);
	private static final BlockPos STONECUTTER = new BlockPos(11, 2, 25);
	private static final BlockPos COUNTER = new BlockPos(10, 2, 20);
	private static final BlockPos CHEST = new BlockPos(10, 2, 22);

	// --- helpers ------------------------------------------------------------------------------------------------------

	/** A guard at his own grindstone with an iron sword, AI on (he must walk to the bell himself). */
	private static Villager guardAt(GameTestHelper helper, String name, BlockPos grindstone) {
		helper.setBlock(grindstone, Blocks.GRINDSTONE);
		Villager v = QaHallScreenGameTests.villager(helper, name, grindstone.offset(0, 0, -1));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(grindstone), PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
		v.setVillagerXp(5);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Guards.updateHealth(v);
		v.setNoAi(false);
		return v;
	}

	private static void twoClicks(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager v, VillagerProfession job) {
		QaHallScreenGameTests.jobDropdown(helper, player, menu, v);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
	}

	private static void ring(GameTestHelper helper) {
		((BellBlock) Blocks.BELL).attemptToRing(helper.getLevel(), helper.absolutePos(BELL), Direction.NORTH);
	}

	private static double toBell(GameTestHelper helper, Villager v) {
		BlockPos bell = helper.absolutePos(BELL);
		double dx = v.getX() - (bell.getX() + 0.5);
		double dz = v.getZ() - (bell.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Every {@code item} anywhere a test could hide it: the villagers' hands, armor and inventories, the players'
	 * inventories, every container block in the area and every dropped item.
	 */
	private static int everywhere(GameTestHelper helper, Item item, List<Villager> villagers, List<ServerPlayer> players) {
		int n = 0;
		for (Villager v : villagers) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (v.getItemBySlot(slot).is(item)) {
					n += v.getItemBySlot(slot).getCount();
				}
			}
			n += v.getInventory().countItem(item);
		}
		for (ServerPlayer p : players) {
			n += p.getInventory().countItem(item);
			if (p.containerMenu.getCarried().is(item)) {
				n += p.containerMenu.getCarried().getCount();
			}
		}
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int y = 1; y < 8; y++) {
				for (int z = 0; z < 30; z++) {
					BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, y, z)));
					if (be instanceof Container c && !(be instanceof ShopCounterBlockEntity)) {
						n += c.countItem(item);
					}
				}
			}
		}
		return n;
	}

	private static int chestCount(GameTestHelper helper, Item item) {
		return ((Container) helper.getBlockEntity(CHEST)).countItem(item);
	}

	private static boolean sellsCobble(Villager v) {
		for (MerchantOffer offer : v.getOffers()) {
			if (offer.getResult().is(Items.COBBLESTONE)) {
				return true;
			}
		}
		return false;
	}

	// --- a guard answering the bell -------------------------------------------------------------------------------------

	/**
	 * The bell rings and both guards run for it; on the way, one (Bram) is retrained as a Mason from the hall. He gives up
	 * the rally (8 seconds on he is still more than 8 blocks from the bell, while Cole, still a guard, is at the bell),
	 * and the bell rung a second time doesn't send him there either. His sword is not lost or made twice: one sword for
	 * him, one for Cole, nowhere else.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaHmaBellMason"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaHmaBellMason")
	public void aGuardRetrainedOnHisWayToTheBellStaysAway(GameTestHelper helper) {
		bellCase(helper, VillagerProfession.MASON);
	}

	/**
	 * The same with No job: while jobless he leaves the rally and a second ring doesn't send him, and his sword is kept
	 * once. A jobless villager may take a free block back by himself (village-hall.md), and if he takes a grindstone he
	 * is a guard again by his own choice: from then on his answering the bell isn't held against him.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaHmaBellNoJob"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaHmaBellNoJob")
	public void aGuardGivenNoJobOnHisWayToTheBellStaysAway(GameTestHelper helper) {
		bellCase(helper, VillagerProfession.NONE);
	}

	private static void bellCase(GameTestHelper helper, VillagerProfession job) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(BELL, Blocks.BELL);
		if (job != VillagerProfession.NONE) {
			helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		}
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		level.setDayTime(1000);
		Leftovers.after(helper, () -> level.setDayTime(time));
		Villager bram = guardAt(helper, "Bram", GRIND_A);
		Villager cole = guardAt(helper, "Cole", GRIND_B);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		// The nearest Bram comes to the bell while he isn't a guard, from the change on. Given No job he may take his free
		// grindstone back by himself (village-hall.md: "They may then take a free block nearby by themselves"); from then on
		// he is a guard by his own choice and answering the bell is right, so it is no longer watched. (Registered here, not
		// inside a callback: the helper's tick list can't be added to while it runs.)
		double[] nearest = {Double.MAX_VALUE};
		boolean[] watching = {false};
		boolean[] tookItBack = {false};
		helper.onEachTick(() -> {
			if (!watching[0]) {
				return;
			}
			if (Guards.isGuard(bram)) {
				tookItBack[0] = true;
			}
			if (!tookItBack[0]) {
				nearest[0] = Math.min(nearest[0], toBell(helper, bram));
			}
		});
		helper.runAfterDelay(5, () -> ring(helper));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(GuardRally.isRallying(bram) && GuardRally.isRallying(cole),
				"setup: the bell didn't call both guards: Bram " + GuardRally.isRallying(bram) + ", Cole " + GuardRally.isRallying(cole));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, bram, job);
			player.closeContainer();
			helper.assertTrue(bram.getVillagerData().getProfession() == job, "the change didn't take: " + bram.getVillagerData() + "; told " + seen);
			helper.assertTrue(!Guards.isGuard(bram) && Guards.isGuard(cole), "guards now: Bram " + Guards.isGuard(bram) + ", Cole " + Guards.isGuard(cole));
			double start = toBell(helper, bram);
			nearest[0] = start;
			watching[0] = true;
			helper.runAfterDelay(100, () -> ring(helper));
			helper.runAfterDelay(160, () -> {
				double now = toBell(helper, bram);
				level.getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qaHmaBell " + job + "] Bram " + String.format("%.1f", start)
					+ " -> " + String.format("%.1f", now) + " from the bell (nearest while not a guard " + String.format("%.1f", nearest[0]) + ", took a guard's block back "
					+ tookItBack[0] + "), Cole " + String.format("%.1f", toBell(helper, cole))
					+ ", GuardRally.isRallying(Bram) " + GuardRally.isRallying(bram) + ", Bram now " + bram.getVillagerData().getProfession()));
				helper.assertTrue(Guards.isGuard(cole) && toBell(helper, cole) < 5, "the control guard Cole never reached the bell: " + toBell(helper, cole));
				helper.assertTrue(job == VillagerProfession.NONE || !tookItBack[0], "the Mason is a guard again: " + bram.getVillagerData());
				helper.assertTrue(nearest[0] > 8, "Bram, no longer a guard, still made for the bell: " + String.format("%.1f", start) + " -> "
					+ String.format("%.1f", nearest[0]) + " blocks");
				int swords = everywhere(helper, Items.IRON_SWORD, List.of(bram, cole), List.of(player));
				helper.assertTrue(swords == 2, "iron swords about: " + swords + " (expected 2: Bram's and Cole's)");
				helper.assertTrue(cole.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "Cole lost his sword");
				helper.succeed();
			});
		});
	}

	// --- a shopkeeper with his trade screen open -----------------------------------------------------------------------

	/**
	 * A buyer has Sal's shop trades open (16 cobblestone for an emerald) when another player retrains Sal as a Mason at
	 * the hall. The buyer can't buy the shop's cobblestone from the Mason through the screen still open: putting in 3
	 * emeralds and shift-clicking the result gets no cobblestone, the chest keeps all 64, and when the screen is closed
	 * every emerald is either back with the buyer or (a sale) in the shop's chest: none vanish, none are made.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHmaShopOpen"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHmaShopOpen")
	public void aShopkeepersOpenTradesSellNothingAfterHeIsRetrained(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(1, new ItemStack(Items.EMERALD, 7));
		Villager sal = QaHallScreenGameTests.villager(helper, "Sal", COUNTER.offset(1, 0, 0));
		Jobs.employ(helper.getLevel(), sal, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		sal.setVillagerData(sal.getVillagerData().setLevel(2));
		sal.setVillagerXp(140);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 10);
		helper.runAfterDelay(3, () -> {
			Shops.refreshOffers(level, sal);
			helper.assertTrue(Shops.isShopkeeper(sal) && sellsCobble(sal), "setup: Sal doesn't run the shop: " + sal.getOffers().size() + " offers");
			ServerPlayer buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHmaBuy")).player();
			buyer.getInventory().setItem(5, new ItemStack(Items.EMERALD, 3));
			QaStageFourGameTests.pressOn(buyer, sal, new ArrayList<>());
			helper.assertTrue(buyer.containerMenu instanceof MerchantMenu, "setup: the right-click didn't open Sal's trades: " + buyer.containerMenu);
			MerchantMenu trades = (MerchantMenu) buyer.containerMenu;
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, sal, VillagerProfession.MASON);
			helper.assertTrue(sal.getVillagerData().getProfession() == VillagerProfession.MASON, "not retrained: " + sal.getVillagerData() + "; told " + seen);
			helper.runAfterDelay(2, () -> {
				boolean stillOpen = buyer.containerMenu == trades;
				int bricks = 0;
				if (stillOpen) {
					buyer.getInventory().removeItem(5, 3);
					trades.getSlot(0).set(new ItemStack(Items.EMERALD, 3));
					QaStageSixGameTests.click(buyer, 2, 0, ClickType.QUICK_MOVE);
					bricks = buyer.getInventory().countItem(Items.BRICK);
				}
				buyer.closeContainer();
				int cobble = buyer.getInventory().countItem(Items.COBBLESTONE);
				int buyerEm = buyer.getInventory().countItem(Items.EMERALD);
				level.getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qaHmaShopOpen] trade screen still open after the retraining: "
					+ stillOpen + "; buyer then has " + cobble + " cobblestone, " + buyerEm + " emeralds, " + bricks + " bricks; chest "
					+ chestCount(helper, Items.COBBLESTONE) + " cobblestone, " + chestCount(helper, Items.EMERALD) + " emeralds"));
				helper.assertTrue(cobble == 0, "the buyer bought " + cobble + " of the shop's cobblestone from a Mason");
				helper.assertTrue(chestCount(helper, Items.COBBLESTONE) == 64, "the shop's chest: " + chestCount(helper, Items.COBBLESTONE) + " cobblestone, not 64");
				int emeraldsNow = buyerEm + chestCount(helper, Items.EMERALD);
				helper.assertTrue(emeraldsNow == 10 || bricks > 0, "emeralds: the buyer " + buyerEm + " + the chest " + chestCount(helper, Items.EMERALD)
					+ " = " + emeraldsNow + ", expected 10 (3 of the buyer's + 7 takings) with nothing bought");
				helper.assertTrue(everywhere(helper, Items.COBBLESTONE, List.of(sal), List.of(buyer, player)) == 64, "cobblestone made or lost: "
					+ everywhere(helper, Items.COBBLESTONE, List.of(sal), List.of(buyer, player)));
				helper.assertTrue(!sellsCobble(sal) && !Shops.isShopkeeper(sal), "the Mason still sells the shop's cobblestone");
				helper.succeed();
			});
		});
	}

	// --- a guard friend's "tools of my trade" when he loses his job ----------------------------------------------------

	/**
	 * A guard friend asks for the tools of his trade (a diamond sword) and is then given No job at the hall. The player
	 * brings the sword from the journal's Personal tab anyway. Whatever the mod decides (the request finished and paid,
	 * or called off), the request doesn't hang open with the sword in hand, and the one diamond sword is not lost: it is
	 * still the player's, or in a chest, or with the villager, never gone; and if it was taken, the 5 emeralds were paid.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHmaToolsNoJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHmaToolsNoJob")
	public void theToolsOfMyTradeBroughtToAJoblessFriendAreNotLost(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		QaHallRolesDuty1103bGameTests.requestsOn(helper);
		BlockPos oldChest = new BlockPos(9, 2, 20);
		helper.setBlock(oldChest, Blocks.CHEST);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager bram = QaHallRolesDuty1103bGameTests.guard(helper, "Bram", false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			Friendship.add(bram, player, 450);
			Quest quest = QaHallRolesDuty1103bGameTests.accepted(helper, "tools_of_my_trade", bram, player);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, bram, VillagerProfession.NONE);
			player.closeContainer();
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "No job didn't take: " + bram.getVillagerData() + "; " + seen);
			io.github.jcondedata.aliveworkplace.hall.VillageQuests.tick(level, hall,
				(io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall));
			boolean openAfterNoJob = PersonalRequests.of(bram) == quest;
			player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
			if (openAfterNoJob) {
				ChoiceMenu journal = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.PERSONAL));
				journal.press(QuestJournal.PERSONAL_ROW, player);
			}
			int held = player.getInventory().countItem(Items.DIAMOND_SWORD);
			int emeralds = player.getInventory().countItem(Items.EMERALD);
			int all = everywhere(helper, Items.DIAMOND_SWORD, List.of(bram), List.of(player));
			level.getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qaHmaToolsNoJob] open after No job " + openAfterNoJob
				+ ", open after bringing " + (PersonalRequests.of(bram) == quest) + ", player swords " + held + ", emeralds " + emeralds + ", swords anywhere " + all));
			helper.assertTrue(all == 1, "diamond swords anywhere: " + all + " (expected the one the player had)");
			helper.assertTrue(!(PersonalRequests.of(bram) == quest && held == 1), "the request hangs open though the player brings the sword: " + quest.objectives + "; " + seen);
			helper.assertTrue(held == 1 || emeralds == 5, "the sword was taken but the reward wasn't paid: " + emeralds + " emeralds");
			helper.succeed();
		});
	}
}
