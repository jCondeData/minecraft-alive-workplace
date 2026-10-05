package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.Tithe;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Festival Season and Tithe, the treasury edicts (ROADMAP 30.8): the effects {@code festival_every},
 * {@code festival_cost}, {@code tithe} and {@code trade_prices}. Festivals 4 days apart; each one's cost (3 emeralds and 1
 * for every 4 villagers) taken on its morning; the day the treasury can't pay (no festival, the chronicle, a disappointed
 * village); a festival called with a cake free; The Festival Fund free. Trades through the trade screen: 20 emeralds
 * become 22 and put 2.20 in the treasury, 4 stay 4; with The Fair Ledger 20 stay 20 and still put in 2.00.
 */
public class TreasuryEdictGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation FESTIVAL_SEASON = AliveWorkplace.id("festival_season");
	private static final ResourceLocation TITHE = AliveWorkplace.id("tithe");

	/** Both load with their texts, icons, effects and three-step reforms that take the cost away; the sums and the rounding. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void festivalSeasonAndTitheLoadWithTheirReforms(GameTestHelper helper) {
		Edicts.Edict season = Edicts.get(FESTIVAL_SEASON).orElse(null);
		helper.assertTrue(season != null && season.name().getString().equals("Festival Season")
			&& season.icon().equals(ResourceLocation.withDefaultNamespace("cake")), "Festival Season: " + season);
		helper.assertTrue(season.boost().size() == 1 && season.boost().get(0) instanceof CivicEffects.FestivalEvery e && e.days() == 4, "boost: " + season.boost());
		helper.assertTrue(season.cost().size() == 1 && season.cost().get(0) instanceof CivicEffects.FestivalCost c && c.emeralds() == 3 && c.perVillagers() == 4,
			"cost: " + season.cost());
		Reforms.Reform fund = season.reform().orElse(null);
		helper.assertTrue(fund != null && fund.name().getString().equals("The Festival Fund") && fund.effects().isEmpty() && fund.steps().size() == 3, "reform: " + fund);
		step(helper, fund.steps().get(0), "minecraft:cake", 24, 5);
		step(helper, fund.steps().get(1), "minecraft:firework_rocket", 256, 5);
		step(helper, fund.steps().get(2), "minecraft:note_block", 48, 4);

		Edicts.Edict tithe = Edicts.get(TITHE).orElse(null);
		helper.assertTrue(tithe != null && tithe.name().getString().equals("Tithe") && tithe.icon().equals(ResourceLocation.withDefaultNamespace("emerald")),
			"Tithe: " + tithe);
		helper.assertTrue(tithe.boost().size() == 1 && tithe.boost().get(0) instanceof CivicEffects.TitheShare t && t.percent() == 10, "boost: " + tithe.boost());
		helper.assertTrue(tithe.cost().size() == 1 && tithe.cost().get(0) instanceof CivicEffects.TradePrices p && p.percent() == 10, "cost: " + tithe.cost());
		Reforms.Reform ledger = tithe.reform().orElse(null);
		helper.assertTrue(ledger != null && ledger.name().getString().equals("The Fair Ledger") && ledger.effects().isEmpty() && ledger.steps().size() == 3,
			"reform: " + ledger);
		step(helper, ledger.steps().get(0), "minecraft:writable_book", 24, 4);
		step(helper, ledger.steps().get(1), "minecraft:gold_ingot", 192, 6);
		Reforms.Step battle = ledger.steps().get(2);
		helper.assertTrue(battle.kind() == VillageQuests.Kind.BATTLE && battle.reward() == 8, "step 3: " + battle);
		Reforms.Step without = battle.resolve(false);
		helper.assertTrue(without.kind() == VillageQuests.Kind.SLAY && without.count() == 32 && without.reward() == 8, "without a trainer: " + without);

		// Summed: the fewest days, costs add, shares and prices add; none: every 8 days, free, no tithe.
		CivicEffects.Sum sum = new CivicEffects.Sum(List.of(
			new CivicEffects.Active(new CivicEffects.FestivalEvery(4, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.FestivalEvery(6, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.FestivalCost(3, 4, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.FestivalCost(1, 0, List.of()), Component.empty())));
		helper.assertTrue(sum.festivalEvery(8) == 4 && sum.festivalCost(9) == 3 + 2 + 1 && sum.festivalCost(3) == 4, "summed: " + sum.festivalEvery(8)
			+ ", " + sum.festivalCost(9) + ", " + sum.festivalCost(3));
		helper.assertTrue(CivicEffects.Sum.EMPTY.festivalEvery(8) == 8 && CivicEffects.Sum.EMPTY.festivalCost(20) == 0, "no edicts: the usual");
		// 10% more, rounded: 20 is 22, 4 stays 4, 5 is 6, 15 is 17.
		helper.assertTrue(Tithe.rise(20, 10) == 2 && Tithe.rise(4, 10) == 0 && Tithe.rise(5, 10) == 1 && Tithe.rise(15, 10) == 2,
			"rises: " + Tithe.rise(20, 10) + ", " + Tithe.rise(4, 10) + ", " + Tithe.rise(5, 10) + ", " + Tithe.rise(15, 10));
		helper.succeed();
	}

	private static void step(GameTestHelper helper, Reforms.Step step, String item, int count, int reward) {
		helper.assertTrue(step.kind() == VillageQuests.Kind.BRING && step.item().equals(item) && step.count() == count && step.reward() == reward,
			"step: " + step + ", expected " + count + " " + item + " for " + reward);
	}

	/**
	 * Under Festival Season the village's festivals come every 4 days (counted from its own day, the 8-day one among
	 * them); without it every 8. The hall's rounds every morning for 16 days, with a treasury that can pay.
	 */
	//$ gametest_ticks_batch AREA '100' '"festivalEveryFour"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "festivalEveryFour")
	public void festivalSeasonHoldsAFestivalEveryFourDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			long first = Chronicle.day(level) + 1;
			List<Long> usual = festivals(level, hall, entity, first, 16);
			helper.assertTrue(usual.size() == 2 && usual.get(1) - usual.get(0) == Festivals.EVERY_DAYS, "without the edict, every 8 days: " + usual);

			entity.setEdicts(List.of(new Edicts.InForce(FESTIVAL_SEASON.toString(), first)));
			entity.setTreasury(64 * 100);
			entity.setFestivalDay(-1);
			List<Long> season = festivals(level, hall, entity, first + 16, 16);
			helper.assertTrue(season.size() == 4, "four festivals in 16 days: " + season);
			for (int i = 1; i < season.size(); i++) {
				helper.assertTrue(season.get(i) - season.get(i - 1) == 4, "festivals 4 days apart: " + season);
			}
			helper.assertTrue(Math.floorMod(season.get(0) - usual.get(0), 4) == 0, "the 8-day festival is one of them: " + usual + ", " + season);
			helper.assertTrue(Festivals.every(entity) == 4, "every " + Festivals.every(entity));
			helper.succeed();
		});
	}

	/** The days in {@code days} days from {@code from} on which the hall's morning round planned a festival (8 villagers). */
	private static List<Long> festivals(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long from, int days) {
		List<Long> out = new ArrayList<>();
		for (long day = from; day < from + days; day++) {
			morning(level, day);
			Festivals.round(level, hall, entity, 8);
			if (entity.festivalDay() == day) {
				out.add(day);
			}
		}
		return out;
	}

	/**
	 * A festival's cost, 3 emeralds and 1 for every 4 villagers (8 here: 5), comes out of the treasury on its morning,
	 * once; the hall's festival icon tells it beforehand (and how much the treasury holds) and the players are told.
	 */
	//$ gametest_ticks_batch AREA '100' '"festivalCost"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "festivalCost")
	public void festivalSeasonTakesTheCostOnTheMorning(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setTreasury(1000);
			long day = seasonDay(level, hall, entity, false);
			villagers(helper, 8);
			int villagers = VillageHalls.census(level, hall).villagers();
			helper.assertTrue(villagers == 8, "villagers counted: " + villagers);
			ServerPlayer owner = player(helper);
			List<String> lore = festivalLore(owner, hall);
			helper.assertTrue(lore.contains("One every 4 days (Festival Season)") && lore.contains("Costs the treasury 5 emeralds (it holds 10)"),
				"the festival icon: " + lore);
			morning(level, day);
			Festivals.round(level, hall, entity, villagers);
			helper.assertTrue(entity.festivalDay() == day, "no festival planned on day " + day + ": " + entity.festivalDay());
			helper.assertTrue(entity.treasury() == 500, "the treasury holds " + entity.treasury() + " hundredths, expected 500");
			Festivals.round(level, hall, entity, villagers);
			helper.assertTrue(entity.treasury() == 500, "paid twice: " + entity.treasury());
			helper.assertTrue(entity.festivalMissed() < day, "marked as missed");
			helper.succeed();
		});
	}

	/**
	 * A treasury that can't pay: no festival, the chronicle says there was no money, every villager is 5 less happy
	 * ("disappointed") that day only, the round doesn't try again that day, and the day is kept over a save.
	 */
	//$ gametest_ticks_batch AREA '100' '"festivalNoMoney"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "festivalNoMoney")
	public void festivalSeasonWithoutMoneyDisappointsTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setTreasury(250); // 2.50: short of the 5 it costs
			long day = seasonDay(level, hall, entity, false);
			List<Villager> people = villagers(helper, 8);
			morning(level, day);
			Moods.forget();
			Moods.Mood before = Moods.work(level, people.get(0));
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(entity.festivalDay() < day, "a festival without money: " + entity.festivalDay());
			helper.assertTrue(entity.festivalMissed() == day && entity.treasury() == 250, "missed " + entity.festivalMissed() + ", treasury " + entity.treasury());
			Chronicle.Entry last = entity.chronicle().get(entity.chronicle().size() - 1);
			helper.assertTrue(last.kind() == Chronicle.Kind.FESTIVAL && last.text().getString().equals(
				"No money for the festival: the treasury hadn't the 5 emeralds it costs, and the village was disappointed"), "chronicle: " + last.text().getString());
			Moods.forget();
			Moods.Mood after = Moods.work(level, people.get(0));
			helper.assertTrue(before.score() - after.score() == Festivals.DISAPPOINTED && after.bad().stream().anyMatch(c -> c.getString().equals("disappointed")),
				"mood " + before.score() + " -> " + after.score() + ", " + after.bad().stream().map(Component::getString).toList());
			ServerPlayer owner = player(helper);
			helper.assertTrue(festivalLore(owner, hall).contains("No money for today's festival: the village is disappointed"), "icon: " + festivalLore(owner, hall));
			// Money later that day: still no festival today.
			entity.setTreasury(6400);
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(entity.festivalDay() < day && entity.treasury() == 6400, "tried again the same day");
			// Kept over a save; a hall saved before 30.8 never missed one.
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && copy.festivalMissed() == day, "reloaded: " + (copy == null ? null : copy.festivalMissed()));
			tag.remove("festivalMissed");
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(old != null && old.festivalMissed() == -1, "an old hall: " + (old == null ? null : old.festivalMissed()));
			// The next day the disappointment is gone.
			morning(level, day + 1);
			Moods.forget();
			Moods.Mood next = Moods.work(level, people.get(0));
			helper.assertTrue(next.bad().stream().noneMatch(c -> c.getString().equals("disappointed")), "still disappointed the next day");
			helper.succeed();
		});
	}

	/** A festival called with a cake under Festival Season costs the empty treasury nothing, on its own day or another. */
	//$ gametest_ticks_batch AREA '100' '"festivalCalledFree"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "festivalCalledFree")
	public void festivalSeasonLeavesACalledFestivalFree(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			long day = seasonDay(level, hall, entity, false);
			villagers(helper, 8);
			ServerPlayer player = player(helper);
			// On the festival's own day, before the round: called with a cake, held, nothing paid, nobody disappointed.
			morning(level, day);
			player.getInventory().add(new ItemStack(Items.CAKE));
			String said = Festivals.call(level, hall, player).getString();
			helper.assertTrue(said.equals("Festival today, after work!"), "called: " + said);
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(entity.festivalDay() == day && entity.treasury() == 0 && entity.festivalMissed() < day,
				"festival " + entity.festivalDay() + ", treasury " + entity.treasury() + ", missed " + entity.festivalMissed());
			helper.assertTrue(player.getInventory().countItem(Items.CAKE) == 0, "the cake wasn't taken");
			// On a day between festivals, after the rest: free too.
			long other = day + Festivals.CALL_REST;
			helper.assertTrue(Math.floorMod(other - day, 4) != 0, "not a festival day");
			morning(level, other);
			player.getInventory().add(new ItemStack(Items.CAKE));
			said = Festivals.call(level, hall, player).getString();
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(said.equals("Festival today, after work!") && entity.festivalDay() == other && entity.treasury() == 0,
				"called again: " + said + ", " + entity.festivalDay() + ", " + entity.treasury());
			helper.succeed();
		});
	}

	/** Reformed by The Festival Fund: still every 4 days, and the empty treasury pays nothing. */
	//$ gametest_ticks_batch AREA '100' '"festivalFund"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "festivalFund")
	public void theFestivalFundMakesFestivalsFree(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			long day = seasonDay(level, hall, entity, true);
			helper.assertTrue(Festivals.every(entity) == 4 && Festivals.cost(entity, 8) == 0, "reformed: every " + Festivals.every(entity)
				+ ", costs " + Festivals.cost(entity, 8));
			morning(level, day);
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(entity.festivalDay() == day && entity.treasury() == 0 && entity.festivalMissed() < day,
				"festival " + entity.festivalDay() + ", treasury " + entity.treasury() + ", missed " + entity.festivalMissed());
			morning(level, day + 4);
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(entity.festivalDay() == day + 4, "the next one 4 days later: " + entity.festivalDay());
			helper.succeed();
		});
	}

	/**
	 * Under the Tithe a librarian's 20-emerald trade costs 22 on the trade screen and, bought there, puts 2.20 in the
	 * treasury; the 4-emerald trade still costs 4 (and puts in 0.40). Closed, the prices go back; with edicts off nothing
	 * changes.
	 */
	//$ gametest_ticks_batch AREA '100' '"titheTrades"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "titheTrades")
	public void titheRaisesPricesAndFillsTheTreasury(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(TITHE.toString(), 1)));
			Villager librarian = librarian(helper);
			ServerPlayer player = player(helper);
			MerchantMenu menu = trade(helper, librarian, player);
			helper.assertTrue(menu.getOffers().get(0).getCostA().getCount() == 22, "20 emeralds became " + menu.getOffers().get(0).getCostA().getCount());
			helper.assertTrue(menu.getOffers().get(1).getCostA().getCount() == 4, "4 emeralds became " + menu.getOffers().get(1).getCostA().getCount());
			buy(helper, menu, player, 0, 22, Items.BOOKSHELF);
			helper.assertTrue(entity.treasury() == 220, "the treasury got " + entity.treasury() + " hundredths, expected 220");
			buy(helper, menu, player, 1, 4, Items.LANTERN);
			helper.assertTrue(entity.treasury() == 260, "the treasury holds " + entity.treasury() + " hundredths, expected 260");
			player.closeContainer();
			helper.assertTrue(librarian.getOffers().get(0).getCostA().getCount() == 20, "the price stayed up after trading");
			// Edicts off: the usual price, no tithe.
			Edicts.setEnabled(false);
			Leftovers.after(helper, () -> Edicts.setEnabled(true));
			menu = trade(helper, librarian, player);
			helper.assertTrue(menu.getOffers().get(0).getCostA().getCount() == 20, "edicts off: " + menu.getOffers().get(0).getCostA().getCount());
			buy(helper, menu, player, 0, 20, Items.BOOKSHELF);
			helper.assertTrue(entity.treasury() == 260, "edicts off, the treasury got a tithe: " + entity.treasury());
			player.closeContainer();
			Edicts.setEnabled(true);
			helper.succeed();
		});
	}

	/** Reformed by The Fair Ledger: the 20-emerald trade costs 20 and still puts 2.00 in the treasury, up to its cap. */
	//$ gametest_ticks_batch AREA '100' '"fairLedger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "fairLedger")
	public void theFairLedgerKeepsTheTitheAtTheUsualPrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(TITHE.toString(), 1)));
			entity.setReforms(List.of(new Reforms.Progress(TITHE.toString(), 3, true, 0)));
			Villager librarian = librarian(helper);
			ServerPlayer player = player(helper);
			MerchantMenu menu = trade(helper, librarian, player);
			helper.assertTrue(menu.getOffers().get(0).getCostA().getCount() == 20, "reformed, 20 emeralds became " + menu.getOffers().get(0).getCostA().getCount());
			buy(helper, menu, player, 0, 20, Items.BOOKSHELF);
			helper.assertTrue(entity.treasury() == 200, "the treasury got " + entity.treasury() + " hundredths, expected 200");
			// Up to the cap (a Hamlet's 64 emeralds).
			entity.setTreasury(64 * 100 - 50);
			buy(helper, menu, player, 0, 20, Items.BOOKSHELF);
			helper.assertTrue(entity.treasury() == 64 * 100, "past the cap: " + entity.treasury());
			player.closeContainer();
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** A librarian by the hall selling 3 bookshelves for 20 emeralds and a lantern for 4 (fixed offers). */
	private static Villager librarian(GameTestHelper helper) {
		Villager librarian = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		librarian.setNoAi(true);
		librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(2));
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BOOKSHELF, 3), 12, 5, 0.05f));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.LANTERN), 12, 1, 0.05f));
		librarian.setOffers(offers);
		CivicEffects.forget();
		return librarian;
	}

	/** {@code player} right-clicks {@code villager}: the trade screen it opens. */
	private static MerchantMenu trade(GameTestHelper helper, Villager villager, ServerPlayer player) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		villager.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.containerMenu instanceof MerchantMenu, "no trade screen: " + player.containerMenu);
		return (MerchantMenu) player.containerMenu;
	}

	/** On the trade screen: picks offer {@code index} with exactly {@code emeralds} in hand and takes what it sells. */
	private static void buy(GameTestHelper helper, MerchantMenu menu, ServerPlayer player, int index, int emeralds, net.minecraft.world.item.Item goods) {
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		menu.setSelectionHint(index);
		menu.tryMoveItems(index); // what the client's click on the offer asks for
		helper.assertTrue(menu.getSlot(2).getItem().is(goods), "nothing for sale with " + emeralds + " emeralds: " + menu.getSlot(2).getItem());
		menu.clicked(2, 0, ClickType.PICKUP, player);
		helper.assertTrue(menu.getCarried().is(goods), "bought " + menu.getCarried());
		int left = player.getInventory().countItem(Items.EMERALD) + menu.getSlot(0).getItem().getCount() + menu.getSlot(1).getItem().getCount();
		helper.assertTrue(left == 0, emeralds + " emeralds paid, " + left + " left over");
		menu.setCarried(ItemStack.EMPTY);
	}

	/** {@code count} grown villagers standing still in the village. */
	private static List<Villager> villagers(GameTestHelper helper, int count) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3 + 2 * (i % 4), 2, 3 + 2 * (i / 4)));
			v.setNoAi(true);
			out.add(v);
		}
		CivicEffects.forget();
		return out;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(new BlockPos(9, 2, 9));
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return player;
	}

	/**
	 * Festival Season in force (reformed or not) and the evening of today, so no round plans one before the test asks;
	 * returns the day of the village's next festival.
	 */
	private static long seasonDay(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, boolean reformed) {
		entity.setEdicts(List.of(new Edicts.InForce(FESTIVAL_SEASON.toString(), 1)));
		if (reformed) {
			entity.setReforms(List.of(new Reforms.Progress(FESTIVAL_SEASON.toString(), 3, true, 0)));
		}
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + Festivals.END + 500);
		return Festivals.nextDay(level, hall, entity);
	}

	/** Early on {@code day}, before the festival's gathering. */
	private static void morning(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
	}

	private static List<String> festivalLore(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		ItemLore lore = menu.icon(VillageHallScreen.FESTIVAL).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** A village of radius 16 with regular festivals on; every cache, the switch and the clock's day reset after the test. */
	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = helper.getLevel().getDayTime();
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true; // off in GameTest runs (the config), so no other test's hall holds one by surprise
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Festivals.ENABLED = festivals;
			helper.getLevel().setDayTime(time);
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
			Festivals.forget();
		});
	}

	/** The hall after its first round: a Hamlet with nothing in force, no reform, an empty treasury and no festival. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		hall.setTreasury(0);
		hall.setFestivalDay(-1);
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}
}
