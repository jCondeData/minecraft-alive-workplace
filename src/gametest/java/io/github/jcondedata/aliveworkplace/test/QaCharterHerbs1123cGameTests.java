package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.ColonyMap;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.herb.Herbs;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA, lane c 2026-11-23: the Colony Charter (ROADMAP 33.8, docs/wiki/colony-charter.md) and the far herbs (31.17,
 * docs/wiki/far-herbs.md), written from the spec and the wiki for what ColonyCharterGameTests, QaColonyCharterGameTests,
 * HerbGameTests and QaSicknessGameTests leave out: one emerald in the singular, a treasury of hundredths, every side of
 * the compass in the spot's words, a charter in the off hand, a charter far from its (unloaded) hall, two charters at
 * once; every ground the wiki's table names (badlands terracotta too), the Glowcap's shade, a herb whose ground turns
 * unfit, a patch under a canopy and a waiting patch through a save and reload.
 * Colonies are off in GameTests, so each charter test turns them on, alone in its batch, and puts everything back.
 */
public class QaCharterHerbs1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos MIDDLE = new BlockPos(15, 2, 15);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player, String name) {
		GlobalPos mother() {
			return GlobalPos.of(level.dimension(), hall);
		}
	}

	/** A City-or-other hall with {@code treasury} hundredths, owned by a survival player with {@code emeralds}; all put back after. */
	private static Village village(GameTestHelper helper, VillageRanks.Rank rank, int treasury, int emeralds) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		int cap = Colonies.PER_VILLAGE;
		int every = VillageNeeds.CHECK_EVERY;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		Colonies.COOLDOWN_DAYS = 7;
		Colonies.PER_VILLAGE = 3;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		RealmData.get(level.getServer()).clearColonies();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data data = Caravans.Data.get(level);
		for (Caravans.Village left : data.villages()) {
			if (ColonyMap.distance(hall, left.hall().getX(), left.hall().getZ()) <= 2 * ColonyMap.SPAN) {
				data.remove(left.hall());
			}
		}
		List<BlockPos> before = data.villages().stream().map(Caravans.Village::hall).toList();
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			Colonies.COOLDOWN_DAYS = cooldown;
			Colonies.PER_VILLAGE = cap;
			VillageNeeds.CHECK_EVERY = every;
			RealmData.get(level.getServer()).clearColonies();
			for (Caravans.Village v : data.villages()) {
				if (!before.contains(v.hall())) {
					data.remove(v.hall());
				}
			}
		});
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(rank);
		entity.setTreasury(treasury);
		if (emeralds > 0) {
			player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		}
		return new Village(level, hall, entity, player, VillageHalls.name(level, hall).getString());
	}

	/** The Colonies tab, opened the player's way: the hall's screen, its minecart, the tab. */
	private static ChoiceMenu coloniesTab(GameTestHelper helper, Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		menu.press(VillageHallScreen.ROUTES, v.player());
		helper.assertTrue(!menu.icon(TradePage.Tab.COLONIES.slot()).isEmpty(), "no Colonies tab");
		menu.press(TradePage.Tab.COLONIES.slot(), v.player());
		return menu;
	}

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	private static List<ItemStack> charters(ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.COLONY_CHARTER)) {
				out.add(player.getInventory().getItem(i));
			}
		}
		return out;
	}

	/** Buys one charter on the tab's button and takes it out of the inventory. */
	private static ItemStack buy(GameTestHelper helper, Village v) {
		coloniesTab(helper, v).press(Colonies.BUY, v.player());
		List<ItemStack> charters = charters(v.player());
		helper.assertTrue(charters.size() == 1, "the tab sold " + charters.size() + " charters");
		ItemStack charter = charters.get(0).copy();
		v.player().getInventory().clearOrCountMatchingItems(s -> s.is(ModItems.COLONY_CHARTER), -1, v.player().inventoryMenu.getCraftSlots());
		return charter;
	}

	private static Optional<BlockPos> spot(ItemStack charter) {
		ColonyCharterItem.Charter data = charter.get(ModComponents.COLONY_CHARTER);
		return data == null ? Optional.empty() : data.spot();
	}

	private static List<String> tooltip(Village v, ItemStack stack) {
		return stack.getTooltipLines(Item.TooltipContext.of(v.level()), v.player(), TooltipFlag.NORMAL).stream().map(Component::getString).toList();
	}

	private static Colonies.Chosen click(Village v, boolean mainHand, int dx, int dz) {
		return Colonies.clicked(new Colonies.Choose(mainHand, v.hall().getX() + dx, v.hall().getZ() + dz), v.player());
	}

	// ---- the Colony Charter -------------------------------------------------------------------------------------------

	/**
	 * One emerald is "1 emerald" on the tab and in the message, whichever side pays it: a treasury of exactly one
	 * emerald and a player of exactly 31 buy it (nothing left in either), then a treasury of 31 and a player's last one.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChH1123One"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChH1123One")
	public void qaOneEmeraldIsSingularOnTheTabAndInTheMessage(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 100, 31);
		List<String> lore = BoardTradeGameTests.lore(coloniesTab(helper, v).icon(Colonies.BUY));
		helper.assertTrue(lore.contains("The treasury pays 1 emerald, you pay 31 emeralds"), "the split for a treasury of 1: " + lore);
		Colonies.Bought bought = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(bought.ok() && bought.fromTreasury() == 1 && bought.fromPlayer() == 31, "who paid: " + bought);
		helper.assertTrue(bought.message().getString().equals("A Colony Charter of " + v.name() + ": the treasury paid 1 emerald and you paid 31 emeralds."),
			"the message: " + bought.message().getString());
		helper.assertTrue(v.entity().treasury() == 0 && emeralds(v.player()) == 0 && charters(v.player()).size() == 1,
			"after: treasury " + v.entity().treasury() + ", emeralds " + emeralds(v.player()) + ", charters " + charters(v.player()).size());

		v.entity().setTreasury(3100);
		v.player().getInventory().add(new ItemStack(Items.EMERALD, 1));
		lore = BoardTradeGameTests.lore(coloniesTab(helper, v).icon(Colonies.BUY));
		helper.assertTrue(lore.contains("The treasury pays 31 emeralds, you pay 1 emerald"), "the split for a player's 1: " + lore);
		Colonies.Bought last = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(last.ok() && last.message().getString().equals("A Colony Charter of " + v.name() + ": the treasury paid 31 emeralds and you paid 1 emerald."),
			"the message: " + last.message().getString());
		helper.assertTrue(v.entity().treasury() == 0 && emeralds(v.player()) == 0, "after: treasury " + v.entity().treasury() + ", emeralds " + emeralds(v.player()));
		// one short: 30 whole emeralds and 99 hundredths in the treasury, one emerald in the pocket, so 1 is missing
		v.entity().setTreasury(3099);
		v.player().getInventory().add(new ItemStack(Items.EMERALD, 1));
		Colonies.Bought shy = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(shy.refusal() == Colonies.Refusal.MONEY
			&& shy.message().getString().equals("A Colony Charter costs 32 emeralds: the treasury pays 30 emeralds and you don't have the other 2 emeralds."),
			"one short: " + shy.message().getString());
		helper.assertTrue(v.entity().treasury() == 3099 && emeralds(v.player()) == 1, "a refusal took money: " + v.entity().treasury() + ", " + emeralds(v.player()));
		helper.succeed();
	}

	/**
	 * A treasury of less than one emerald pays nothing (it pays in whole emeralds, its hundredths stay), so the player
	 * pays all 32; an empty treasury the same, and the message says the player paid it all.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChH1123Hundredths"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChH1123Hundredths")
	public void qaHundredthsStayInTheTreasuryAndThePlayerPaysAll(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 99, 32);
		Colonies.Bought bought = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(bought.ok() && bought.fromTreasury() == 0 && bought.fromPlayer() == 32, "who paid: " + bought);
		helper.assertTrue(v.entity().treasury() == 99 && emeralds(v.player()) == 0, "the hundredths: treasury " + v.entity().treasury() + ", emeralds " + emeralds(v.player()));
		helper.assertTrue(bought.message().getString().startsWith("A Colony Charter of " + v.name() + ": you paid 32 emeralds"), "the message: " + bought.message().getString());

		v.entity().setTreasury(0);
		v.player().getInventory().add(new ItemStack(Items.EMERALD, 32));
		helper.assertTrue(BoardTradeGameTests.lore(coloniesTab(helper, v).icon(Colonies.BUY)).stream().anyMatch(l -> l.contains("you pay 32 emeralds")),
			"the tab with an empty treasury: " + BoardTradeGameTests.lore(coloniesTab(helper, v).icon(Colonies.BUY)));
		Colonies.Bought all = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(all.ok() && all.message().getString().equals("A Colony Charter of " + v.name() + ": you paid 32 emeralds (the treasury is empty)."),
			"the message: " + all.message().getString());
		helper.assertTrue(emeralds(v.player()) == 0 && charters(v.player()).size() == 2, "two charters for 64 emeralds: " + charters(v.player()).size());
		helper.succeed();
	}

	/**
	 * The spot's words name the right side of the compass all the way round, on the click's answer and on the
	 * tooltip: north is -z, east +x; a spot a little off an axis still reads as that axis.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChH1123Compass"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChH1123Compass")
	public void qaEverySideOfTheCompassReadsRight(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 3200, 0);
		ItemStack held = buy(helper, v);
		v.player().setItemInHand(InteractionHand.MAIN_HAND, held);
		Object[][] sides = {
			{0, -400, "400 blocks north"}, {283, -283, "400 blocks north-east"}, {400, 0, "400 blocks east"}, {283, 283, "400 blocks south-east"},
			{0, 400, "400 blocks south"}, {-283, 283, "400 blocks south-west"}, {-400, 0, "400 blocks west"}, {-283, -283, "400 blocks north-west"},
			{500, -60, "504 blocks east"}, {-40, 700, "701 blocks south"}, {-1000, 100, "1005 blocks west"}, {30, -260, "262 blocks north"},
		};
		List<String> wrong = new ArrayList<>();
		for (Object[] side : sides) {
			Colonies.Chosen chosen = click(v, true, (int) side[0], (int) side[1]);
			String said = chosen.message().getString();
			if (!chosen.ok() || !said.equals("The colony will go " + side[2] + " of " + v.name() + ".")) {
				wrong.add(side[0] + "," + side[1] + ": " + said);
			}
			if (!tooltip(v, v.player().getMainHandItem()).contains("The spot: " + side[2] + " of " + v.name())) {
				wrong.add(side[0] + "," + side[1] + " tooltip: " + tooltip(v, v.player().getMainHandItem()));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the compass: " + wrong);
		helper.succeed();
	}

	/**
	 * A charter in the off hand: right-clicked in the air its map says so (the click comes back for the off hand), a
	 * click chooses the off-hand charter's spot and leaves another charter in the main hand alone, and so does a
	 * right-click on the ground with the off hand.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChH1123OffHand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChH1123OffHand")
	public void qaTheOffHandCharterChoosesItsOwnSpot(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 6400, 0);
		ItemStack off = buy(helper, v);
		ItemStack main = buy(helper, v);
		v.player().setItemInHand(InteractionHand.OFF_HAND, off);
		v.player().setItemInHand(InteractionHand.MAIN_HAND, main);
		Colonies.Open open = Colonies.screen(v.level(), v.player().getOffhandItem(), false);
		helper.assertTrue(open != null && !open.mainHand() && open.hall().equals(v.hall()), "the off hand's map: " + open);

		Colonies.Chosen chosen = click(v, false, 0, -300);
		helper.assertTrue(chosen.ok(), "the off-hand click: " + chosen.message().getString());
		helper.assertTrue(spot(v.player().getOffhandItem()).map(p -> p.getZ() == v.hall().getZ() - 300).orElse(false), "the off hand's spot: " + spot(v.player().getOffhandItem()));
		helper.assertTrue(spot(v.player().getMainHandItem()).isEmpty(), "the main hand's charter got a spot: " + spot(v.player().getMainHandItem()));

		// the main hand's click moves only the main hand's
		helper.assertTrue(click(v, true, 300, 0).ok(), "the main-hand click");
		helper.assertTrue(spot(v.player().getOffhandItem()).map(p -> p.getZ() == v.hall().getZ() - 300 && p.getX() == v.hall().getX()).orElse(false),
			"the main-hand click moved the off hand's: " + spot(v.player().getOffhandItem()));

		// on the ground, with the off hand, standing 400 blocks south
		v.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockPos floor = helper.absolutePos(new BlockPos(5, 1, 5));
		BlockHitResult ground = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
		v.player().setPos(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 400.5);
		v.player().gameMode.useItemOn(v.player(), v.level(), v.player().getOffhandItem(), InteractionHand.OFF_HAND, ground);
		helper.assertTrue(spot(v.player().getOffhandItem()).map(p -> p.getX() == v.hall().getX() && p.getZ() == v.hall().getZ() + 400).orElse(false),
			"the ground click with the off hand: " + spot(v.player().getOffhandItem()));
		helper.assertTrue(tooltip(v, v.player().getOffhandItem()).contains("The spot: 400 blocks south of " + v.name()), "its tooltip: " + tooltip(v, v.player().getOffhandItem()));
		helper.succeed();
	}

	/**
	 * Far from home the charter is taken at its word: its hall stands 20,000 blocks away where nothing is loaded, yet a
	 * click 400 blocks from that hall is kept (at the hall's height, the ground there unknown), the ring is that hall's,
	 * and nothing is loaded for it; the hall's own name speaks in the answer.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChH1123FarHome"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChH1123FarHome")
	public void qaFarFromHomeTheCharterTakesItsWord(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 0, 0);
		ServerLevel level = v.level();
		BlockPos far = new BlockPos(v.hall().getX() + 20_000, 70, v.hall().getZ() + 20_000);
		helper.assertTrue(!level.isLoaded(far) && !level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "the far hall's chunk is loaded already");
		ItemStack charter = new ItemStack(ModItems.COLONY_CHARTER);
		charter.set(ModComponents.COLONY_CHARTER, new ColonyCharterItem.Charter(GlobalPos.of(level.dimension(), far), Component.literal("Farholt"), Optional.empty()));
		v.player().setItemInHand(InteractionHand.MAIN_HAND, charter);
		int loaded = level.getChunkSource().getLoadedChunksCount();

		Colonies.Chosen near = Colonies.clicked(new Colonies.Choose(true, far.getX() + 100, far.getZ()), v.player());
		helper.assertTrue(!near.ok() && near.message().getString().equals("Too near Farholt: a colony goes at least 256 blocks from its hall (this is 100)."),
			"100 blocks from the far hall: " + near.message().getString());
		Colonies.Chosen chosen = Colonies.clicked(new Colonies.Choose(true, far.getX() - 400, far.getZ()), v.player());
		helper.assertTrue(chosen.ok() && chosen.message().getString().equals("The colony will go 400 blocks west of Farholt."), "400 blocks from the far hall: " + chosen.message().getString());
		BlockPos kept = spot(v.player().getMainHandItem()).orElse(null);
		helper.assertTrue(kept != null && kept.equals(new BlockPos(far.getX() - 400, far.getY(), far.getZ())), "the spot kept: " + kept);
		// a spot by the test's own hall is 28,000 blocks from the charter's: too far
		Colonies.Chosen here = click(v, true, 300, 0);
		helper.assertTrue(!here.ok() && here.message().getString().startsWith("Too far from Farholt"), "near the test's hall: " + here.message().getString());
		Colonies.Open open = Colonies.screen(level, v.player().getMainHandItem(), true);
		helper.assertTrue(open != null && open.hall().equals(far) && open.spot().isPresent(), "the far charter's map: " + open);
		helper.assertTrue(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4) && level.getChunkSource().getLoadedChunksCount() == loaded,
			"the far charter loaded chunks: " + loaded + " -> " + level.getChunkSource().getLoadedChunksCount());
		helper.succeed();
	}

	/** A charter doesn't stack; two charters of one village keep two spots, and each tooltip names its own. */
	//$ gametest_ticks_batch AREA '100' '"qaChH1123Two"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChH1123Two")
	public void qaTwoChartersNeverStackAndKeepTheirOwnSpots(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 6400, 0);
		helper.assertTrue(new ItemStack(ModItems.COLONY_CHARTER).getMaxStackSize() == 1, "a charter stacks to " + new ItemStack(ModItems.COLONY_CHARTER).getMaxStackSize());
		coloniesTab(helper, v).press(Colonies.BUY, v.player());
		coloniesTab(helper, v).press(Colonies.BUY, v.player());
		List<ItemStack> both = charters(v.player());
		helper.assertTrue(both.size() == 2 && both.get(0).getCount() == 1 && both.get(1).getCount() == 1 && v.entity().treasury() == 0,
			"two purchases: " + both + ", treasury " + v.entity().treasury());
		ItemStack a = both.get(0).copy();
		ItemStack b = both.get(1).copy();
		v.player().getInventory().clearOrCountMatchingItems(s -> s.is(ModItems.COLONY_CHARTER), -1, v.player().inventoryMenu.getCraftSlots());
		v.player().setItemInHand(InteractionHand.MAIN_HAND, a);
		helper.assertTrue(click(v, true, 0, 600).ok(), "a's spot");
		v.player().setItemInHand(InteractionHand.MAIN_HAND, b);
		helper.assertTrue(click(v, true, -600, 0).ok(), "b's spot");
		helper.assertTrue(tooltip(v, a).contains("The spot: 600 blocks south of " + v.name()) && tooltip(v, b).contains("The spot: 600 blocks west of " + v.name()),
			"the tooltips: " + tooltip(v, a) + " / " + tooltip(v, b));
		// two charters with different spots are different items: they don't merge in an inventory
		v.player().getInventory().clearContent();
		v.player().getInventory().add(a.copy());
		v.player().getInventory().add(b.copy());
		helper.assertTrue(charters(v.player()).size() == 2, "the two charters merged: " + charters(v.player()));
		helper.succeed();
	}

	// ---- the far herbs ------------------------------------------------------------------------------------------------

	private static void openSky(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	private static BlockState leaves() {
		return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	/**
	 * Every ground the wiki's table names holds its herb (badlands' colored terracotta is terracotta too), and grounds
	 * it doesn't name don't. The Glowcap is tried under a roof.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaChH1123Ground"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaChH1123Ground")
	public void qaEveryGroundTheWikiNamesHoldsItsHerb(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		Object[][] table = {
			{ModBlocks.FROSTBLOOM, new Block[] {Blocks.SNOW_BLOCK, Blocks.GRASS_BLOCK}, new Block[] {Blocks.SAND, Blocks.PODZOL, Blocks.ICE, Blocks.STONE}},
			{ModBlocks.SUNTHISTLE, new Block[] {Blocks.SAND, Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
				Blocks.WHITE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA},
				new Block[] {Blocks.GRASS_BLOCK, Blocks.SANDSTONE, Blocks.STONE}},
			{ModBlocks.MIREWORT, new Block[] {Blocks.MUD, Blocks.GRASS_BLOCK}, new Block[] {Blocks.SAND, Blocks.STONE, Blocks.CLAY}},
			{ModBlocks.PEAKMOSS, new Block[] {Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.GRASS_BLOCK},
				new Block[] {Blocks.SAND, Blocks.COBBLESTONE, Blocks.OBSIDIAN}},
			{ModBlocks.JUNGLE_ORCHID, new Block[] {Blocks.GRASS_BLOCK}, new Block[] {Blocks.SAND, Blocks.STONE, Blocks.MUD}},
			{ModBlocks.SEA_LAVENDER, new Block[] {Blocks.SAND, Blocks.GRAVEL}, new Block[] {Blocks.GRASS_BLOCK, Blocks.STONE}},
			{ModBlocks.GLOWCAP, new Block[] {Blocks.GRASS_BLOCK, Blocks.PODZOL}, new Block[] {Blocks.SAND, Blocks.STONE, Blocks.MUD}},
			{ModBlocks.PETAL_BALM, new Block[] {Blocks.GRASS_BLOCK}, new Block[] {Blocks.SAND, Blocks.STONE, Blocks.PODZOL}},
		};
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		int x = 1;
		for (Object[] row : table) {
			HerbBlock herb = (HerbBlock) row[0];
			int z = 1;
			for (int fit = 1; fit <= 2; fit++) {
				for (Block ground : (Block[]) row[fit]) {
					BlockPos at = new BlockPos(x, 1, z);
					helper.setBlock(at, ground);
					helper.setBlock(at.above(5), Blocks.STONE); // shade for the Glowcap; the others don't mind it
					boolean stands = herb.defaultBlockState().canSurvive(level, helper.absolutePos(at.above()));
					if (stands != (fit == 1)) {
						wrong.add(herb.getName().getString() + (fit == 1 ? " won't stand on " : " stands on ") + ground.getName().getString());
					}
					z += 2;
				}
			}
			x += 3;
		}
		helper.assertTrue(wrong.isEmpty(), "the grounds: " + wrong);
		helper.succeed();
	}

	/**
	 * The Glowcap's shade is a block that stops movement or leaves anywhere above it in its column: glass far up and
	 * azalea leaves count; a cobweb or the open sky don't, and a player can't plant it there.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaChH1123Shade"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaChH1123Shade")
	public void qaGlowcapShadeIsASolidBlockOrLeavesAbove(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		ServerLevel level = helper.getLevel();
		Object[][] cases = {
			{Blocks.GLASS.defaultBlockState(), 20, true},
			{Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 3, true},
			{Blocks.COBWEB.defaultBlockState(), 3, false},
			{null, 0, false},
		};
		List<String> wrong = new ArrayList<>();
		int x = 2;
		for (Object[] c : cases) {
			BlockPos ground = new BlockPos(x, 1, 4);
			helper.setBlock(ground, Blocks.PODZOL);
			if (c[0] != null) {
				helper.setBlock(ground.above((int) c[1] + 1), (BlockState) c[0]);
			}
			boolean stands = ModBlocks.GLOWCAP.defaultBlockState().canSurvive(level, helper.absolutePos(ground.above()));
			if (stands != (boolean) c[2]) {
				wrong.add((c[0] == null ? "the open sky" : c[0].toString()) + ": stands " + stands);
			}
			net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.GLOWCAP));
			helper.placeAt(player, player.getMainHandItem(), ground, Direction.UP);
			if (helper.getBlockState(ground.above()).is(ModBlocks.GLOWCAP) != (boolean) c[2]) {
				wrong.add((c[0] == null ? "the open sky" : c[0].toString()) + ": a player's planting went wrong");
			}
			x += 3;
		}
		helper.assertTrue(wrong.isEmpty(), "the Glowcap's shade: " + wrong);
		helper.succeed();
	}

	/**
	 * A herb whose ground turns into a block it can't stand on (sand dug and stone put in, not just air) pops off like a
	 * flower and drops itself; a herb whose ground stays its kind (grass to podzol for the Glowcap) stays.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaChH1123Swap"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaChH1123Swap")
	public void qaAHerbPopsOffWhenItsGroundTurnsUnfit(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		BlockPos sand = new BlockPos(4, 1, 4);
		helper.setBlock(sand, Blocks.SAND);
		helper.setBlock(sand.above(), ModBlocks.SEA_LAVENDER);
		helper.assertBlockPresent(ModBlocks.SEA_LAVENDER, sand.above());
		helper.setBlock(sand, Blocks.STONE);
		helper.assertBlockNotPresent(ModBlocks.SEA_LAVENDER, sand.above());
		helper.assertItemEntityPresent(ModBlocks.SEA_LAVENDER.asItem(), sand.above(), 2);

		BlockPos grass = new BlockPos(10, 1, 4);
		helper.setBlock(grass, Blocks.GRASS_BLOCK);
		helper.setBlock(grass.above(4), Blocks.STONE);
		helper.setBlock(grass.above(), ModBlocks.GLOWCAP);
		helper.setBlock(grass, Blocks.PODZOL);
		helper.assertBlockPresent(ModBlocks.GLOWCAP, grass.above());
		// its shade taken away and a neighbor changing next to it: it no longer can stand, and goes
		helper.setBlock(grass.above(4), Blocks.AIR);
		helper.setBlock(grass.above().east(), Blocks.STONE);
		helper.assertBlockNotPresent(ModBlocks.GLOWCAP, grass.above());
		long drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(grass)).inflate(3)).stream()
			.filter(e -> e.getItem().is(ModBlocks.GLOWCAP.asItem())).count();
		helper.assertTrue(drops == 1, "the Glowcap dropped " + drops);
		helper.succeed();
	}

	/**
	 * A patch under a closed leaf canopy goes on the ground beneath it, never on top of the leaves: a Jungle Orchid patch
	 * where oak leaves cover the whole field at height 7 plants 5 to 9 on the grass.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaChH1123Canopy"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaChH1123Canopy")
	public void qaAPatchUnderACanopyPlantsOnTheGroundBelow(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				helper.setBlock(new BlockPos(x, 7, z), leaves());
			}
		}
		for (int seed = 0; seed < 5; seed++) {
			for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 29, 9, 29)) {
				if (helper.getBlockState(p).is(ModBlocks.JUNGLE_ORCHID)) {
					helper.setBlock(p.immutable(), Blocks.AIR);
				}
			}
			int planted = Herbs.patch(helper.getLevel(), helper.absolutePos(MIDDLE), ModBlocks.JUNGLE_ORCHID, RandomSource.create(seed));
			int below = 0;
			int above = 0;
			for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 29, 9, 29)) {
				if (helper.getBlockState(p).is(ModBlocks.JUNGLE_ORCHID)) {
					if (p.getY() == 2) {
						below++;
					} else {
						above++;
					}
				}
			}
			helper.assertTrue(above == 0, "seed " + seed + ": " + above + " on top of the leaves");
			helper.assertTrue(planted >= Herbs.LEAST && planted <= Herbs.MOST && below == planted, "seed " + seed + ": said " + planted + ", " + below + " on the grass");
		}
		helper.succeed();
	}

	/**
	 * A herb patch waiting for a player is one of the arc's places, saved with it: after a save and reload it is still
	 * waiting at the same spot, and then plants its herb there and sets its flag.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaChH1123PatchSave"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaChH1123PatchSave")
	public void qaAWaitingPatchIsKeptThroughASaveAndPlantedAfter(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.MUD);
			}
		}
		ServerLevel level = helper.getLevel();
		ArcState s = new ArcState("aliveworkplace:qa_herb_save");
		JsonObject json = new JsonObject();
		json.addProperty("type", "herb_patch");
		json.addProperty("herb", "aliveworkplace:mirewort");
		json.addProperty("key", "reeds");
		JsonObject rule = new JsonObject();
		JsonArray offset = new JsonArray();
		offset.add(-2);
		offset.add(4);
		rule.add("offset", offset);
		json.add("rule", rule);
		BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 11));
		ArcEffects.herbPatch(level, hall, s, json);
		ArcState.ArcPlace before = s.place("reeds");
		helper.assertTrue(before != null && before.waiting(), "no waiting place: " + before);

		CompoundTag saved = s.save();
		ArcState back = ArcState.load(saved.copy());
		ArcState.ArcPlace p = back.place("reeds");
		helper.assertTrue(p != null && p.waiting() && p.spot.equals(before.spot) && p.stages.equals(before.stages),
			"after a reload: " + (p == null ? null : p.spot + " " + p.stages) + ", before " + before.spot + " " + before.stages);
		helper.assertTrue(p.stages.size() == 1 && p.stages.get(0).equals("herb_patch:aliveworkplace:mirewort"), "the saved stage: " + p.stages);
		helper.assertTrue(ArcEffects.build(level, back, p) && !p.waiting(), "the reloaded patch wasn't planted");
		int n = 0;
		for (BlockPos q : BlockPos.betweenClosed(0, 1, 0, 29, 8, 29)) {
			if (helper.getBlockState(q).is(ModBlocks.MIREWORT)) {
				n++;
				int dx = helper.absolutePos(q).getX() - p.spot.getX();
				int dz = helper.absolutePos(q).getZ() - p.spot.getZ();
				helper.assertTrue(dx * dx + dz * dz <= 36 && helper.getBlockState(q.below()).is(Blocks.MUD), "a Mirewort out of place at " + q);
			}
		}
		helper.assertTrue(n >= Herbs.LEAST && n <= Herbs.MOST, "planted " + n);
		helper.assertTrue(back.flags.containsKey("reeds_done"), "no reeds_done flag after the reload");
		// once done it stays done through another save
		ArcState again = ArcState.load(back.save());
		helper.assertTrue(!again.place("reeds").waiting(), "a planted patch waits again after a reload");
		helper.succeed();
	}
}
