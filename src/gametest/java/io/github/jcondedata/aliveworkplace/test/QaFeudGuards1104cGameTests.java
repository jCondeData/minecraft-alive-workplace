package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.GuardRally;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.HallRetraining;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmResearch;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane c, 2026-11-04), written from the spec, not the code: docs/wiki/feuds.md (33.20), realms.md (33.16's Common
 * Coin, 33.22a's Common Market), legends.md (33.23's Merchant Prince), village-hall.md (40.3's Job ▾) and
 * docs/guide/guard.md. Two corners the earlier QA left (lane-c handoff 2026-11-04):
 * <ul>
 * <li>a feud at a board whose spread a realm's research, its Common Market or the Merchant Prince has narrowed: the
 * rival's 25% comes on top of the narrower "We sell" once, never on the old 110% and never twice; the rival's sale is
 * paid 75% of the board's price; the other side's board, outside the realm, keeps its full spread; a feud with a realm's
 * member is with the capital too, so peace can be made at the capital; the feud survives a save round trip and the 25%
 * goes when peace is made;</li>
 * <li>guards after a job change at the hall: a Mason made a guard answers the bell, fights at his post and has a guard's
 * health (also after a reload mid-alarm, not doubled again); a guard made a Mason mid-alarm and reloaded stays away from
 * the bell; in a feud's war, the job change is what decides who the enemy owner may fight.</li>
 * </ul>
 */
public class QaFeudGuards1104cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;
	private static final ResourceLocation PRINCE = AliveWorkplace.id("merchant_prince");
	private static final String FEUD_LINE = "A feud: you're paid 25% less and charged 25% more here";

	// ---- the realm, the rival and the board ---------------------------------------------------------------------------

	/**
	 * A realm of hall 0 (the capital, a Town) and hall 1 (a member), both the realm owner's, and hall 2 outside it,
	 * owned by the rival, who declares a feud on hall 1 (so on the whole realm). A Storehouse and chest by hall 1.
	 */
	private record Rivalry(Land land, ServerPlayer rival, ServerPlayer neutral) {
		ServerLevel level() {
			return land.level();
		}
	}

	private static Rivalry rivalry(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		boolean pacts = Pacts.ENABLED;
		boolean feuds = Feuds.ENABLED;
		Pacts.ENABLED = true;
		Feuds.ENABLED = true;
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		RealmResearch.forget();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Feuds.ENABLED = feuds;
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
			RealmResearch.forget();
			Caravans.Data data = Caravans.Data.get(level);
			for (BlockPos hall : land.halls()) {
				data.remove(hall);
			}
		});
		ServerPlayer rival = RealmGameTests.listener(helper, new ArrayList<>(), "qa1104c-rival");
		land.entity(2).setOwner(rival.getUUID(), "qa1104c-rival");
		Realms.round(level, land.hall(2), land.entity(2), 7);
		Caravans.Data data = Caravans.Data.get(level);
		for (int i = 0; i < land.halls().size(); i++) {
			data.setWants(land.hall(i), Component.literal(land.name(i)), List.of());
		}
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		helper.setBlock(new BlockPos(12, 2, 6), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(12, 2, 8), Blocks.CHEST);
		helper.assertTrue(Feuds.declare(level, land.hall(2), land.hall(1), rival).ok(), "setup: the rival couldn't declare the feud");
		helper.assertTrue(Feuds.isRival(server, land.at(1), rival), "setup: the rival isn't a rival at the member it declared on");
		ServerPlayer neutral = RealmGameTests.listener(helper, new ArrayList<>(), "qa1104c-passer-by");
		return new Rivalry(land, rival, neutral);
	}

	/** Every hall's timber at {@code cents}, the chest by hall 1 refilled. */
	private static void prices(GameTestHelper helper, Land land, int cents) {
		Container chest = helper.getBlockEntity(new BlockPos(12, 2, 8));
		chest.clearContent();
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		for (BlockPos hall : land.halls()) {
			BoardTradeGameTests.price(helper, hall, TIMBER, cents);
		}
	}

	/** The timber's tooltip on the board of {@code hall} as {@code viewer} sees it. */
	private static List<String> tooltip(ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		return BoardTradeGameTests.lore(TradePage.goodIcon(level, hall, TradeGoods.get(TIMBER), null, viewer, null));
	}

	private static void sells(GameTestHelper helper, Rivalry r, int hall, ServerPlayer viewer, String money, boolean feudLine, String why) {
		List<String> lore = tooltip(r.level(), r.land().hall(hall), viewer);
		helper.assertTrue(lore.contains("We sell: " + money), why + ": wanted \"We sell: " + money + "\", the tooltip says " + lore);
		helper.assertTrue(lore.contains(FEUD_LINE) == feudLine, why + ": the feud line " + (feudLine ? "missing" : "shown") + ": " + lore);
	}

	private static void pays(GameTestHelper helper, Rivalry r, int hall, ServerPlayer viewer, String money, String why) {
		List<String> lore = tooltip(r.level(), r.land().hall(hall), viewer);
		helper.assertTrue(lore.contains("We pay: " + money), why + ": wanted \"We pay: " + money + "\", the tooltip says " + lore);
	}

	private record Bought(int logs, int emeralds, String said) {
	}

	/** {@code player} (not hall 1's owner) holding exactly {@code emeralds} right-clicks the timber on hall 1's board once. */
	private static Bought buy(GameTestHelper helper, Rivalry r, ServerPlayer player, int cents, int emeralds) {
		prices(helper, r.land(), cents);
		player.getInventory().clearContent();
		if (emeralds > 0) {
			player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		}
		ChoiceMenu menu = TradePage.forStrangerTest(player, r.land().hall(1));
		int slot = BoardTradeGameTests.slot(helper, menu, "Test Timber");
		menu.clicked(slot, 1, ClickType.PICKUP, player);
		List<String> lore = BoardTradeGameTests.lore(menu.icon(slot));
		Bought bought = new Bought(BoardTradeGameTests.has(player, Items.OAK_LOG), BoardTradeGameTests.has(player, Items.EMERALD), lore.isEmpty() ? "" : lore.get(0));
		player.getInventory().clearContent();
		player.closeContainer();
		return bought;
	}

	/** {@code player} holding exactly one bundle (16 logs) and nothing else sells it to hall 1's board; the emeralds paid. */
	private static int sellOne(GameTestHelper helper, Rivalry r, ServerPlayer player, int cents) {
		prices(helper, r.land(), cents);
		((Container) helper.getBlockEntity(new BlockPos(12, 2, 8))).clearContent();
		r.land().entity(1).setTreasury(5000);
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
		ChoiceMenu menu = TradePage.forStrangerTest(player, r.land().hall(1));
		int slot = BoardTradeGameTests.slot(helper, menu, "Test Timber");
		menu.press(slot, player);
		int logs = BoardTradeGameTests.has(player, Items.OAK_LOG);
		int paid = BoardTradeGameTests.has(player, Items.EMERALD);
		player.getInventory().clearContent();
		player.closeContainer();
		helper.assertTrue(logs == 0, "the bundle wasn't sold: " + logs + " logs left; " + BoardTradeGameTests.lore(menu.icon(slot)));
		return paid;
	}

	// ---- feuds at a narrower board -----------------------------------------------------------------------------------

	/**
	 * Hall 1 at 10 emeralds a bundle. Without research a rival is charged 125% of 11 (13.75); with Common Coin II the
	 * board sells at 104%, a neutral player reads 10.4 and the rival 13 (125% of 10.4, once): with 12 he is refused with
	 * "You can't pay 13 emeralds ...", with exactly 13 he buys and has nothing left. With Common Coin I, 125% of 10.7 is
	 * 13.375, to the cent 13.38. What the board pays the rival is untouched by the research: 7.5. The rival's own
	 * village, outside the realm, charges the realm's owner 125% of its full 110% (13.75), and the rival himself 11.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104cFeudCoin"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104cFeudCoin")
	public void aRivalPaysAQuarterOverCommonCoinsNarrowerSpread(GameTestHelper helper) {
		Rivalry r = rivalry(helper);
		Land land = r.land();
		prices(helper, land, 1000);
		sells(helper, r, 1, r.rival(), "13.75 emeralds", true, "the rival, no research");
		sells(helper, r, 1, r.neutral(), "11 emeralds", false, "a neutral player, no research");

		RealmTopicsGameTests.know(land, Map.of("royal_roads", 1, "common_coin", 2));
		helper.assertTrue(TradePage.sellPercent(r.level(), land.hall(1)) == 104, "setup: Common Coin II's spread " + TradePage.sellPercent(r.level(), land.hall(1)));
		sells(helper, r, 1, r.neutral(), "10.4 emeralds", false, "a neutral player, Common Coin II");
		sells(helper, r, 1, r.rival(), "13 emeralds", true, "the rival, Common Coin II");
		pays(helper, r, 1, r.rival(), "7.5 emeralds", "what the board pays the rival, Common Coin II");
		pays(helper, r, 1, r.neutral(), "10 emeralds", "what the board pays a neutral player, Common Coin II");
		Bought b = buy(helper, r, r.rival(), 1000, 12);
		helper.assertTrue(b.logs() == 0 && b.emeralds() == 12, "the rival bought a 13-emerald bundle with 12: " + b);
		helper.assertTrue(b.said().equals("You can't pay 13 emeralds for a bundle of Test Timber."), "the rival, one short: " + b.said());
		b = buy(helper, r, r.rival(), 1000, 13);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "the rival with exactly 13: " + b);
		helper.assertTrue(b.said().endsWith("for 13 emeralds."), "the rival's trade: " + b.said());
		b = buy(helper, r, r.neutral(), 1000, 11);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "a neutral player paid other than 11 for 10.4: " + b);

		RealmTopicsGameTests.know(land, Map.of("royal_roads", 1, "common_coin", 1));
		sells(helper, r, 1, r.rival(), "13.38 emeralds", true, "the rival, Common Coin I");
		sells(helper, r, 1, r.neutral(), "10.7 emeralds", false, "a neutral player, Common Coin I");

		// the rival's village is outside the realm: no Common Coin there
		helper.assertTrue(TradePage.sellPercent(r.level(), land.hall(2)) == 110, "Common Coin reached the rival's village: " + TradePage.sellPercent(r.level(), land.hall(2)));
		sells(helper, r, 2, land.owner(), "13.75 emeralds", true, "the realm's owner at the rival's board");
		pays(helper, r, 2, land.owner(), "7.5 emeralds", "what the rival's board pays the realm's owner");
		sells(helper, r, 2, r.rival(), "11 emeralds", false, "the rival at his own board");
		helper.succeed();
	}

	/**
	 * The Merchant Prince settled at hall 1 halves its spread (105%): the rival reads 13.13 (125% of 10.5, to the cent),
	 * is refused at 13 with "You can't pay 13.13 emeralds ..." and, charged up to whole emeralds, pays 14; a neutral
	 * player reads 10.5. With Common Coin II as well the spread is 2 points (102%): the rival 12.75. His one bundle sold
	 * at a 10-emerald price is paid 7 (7.5, the Prince doesn't touch "We pay"); a neutral player is paid 10.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104cFeudPrince"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104cFeudPrince")
	public void aRivalAtTheMerchantPrincesBoardPaysAQuarterOverHalfTheSpread(GameTestHelper helper) {
		Rivalry r = rivalry(helper);
		Land land = r.land();
		ServerLevel level = r.level();
		staged(helper, PRINCE);
		Legend prince = Legends.get(PRINCE).orElseThrow(() -> new AssertionError("merchant_prince didn't load"));
		Villager him = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 2));
		him.setNoAi(true);
		Legends.make(level, him, prince, "test", Optional.of(land.hall(1)));
		LegendPowers.forget();
		helper.assertTrue(TradePage.sellPercent(level, land.hall(1)) == 105, "setup: the Prince's spread " + TradePage.sellPercent(level, land.hall(1)));
		prices(helper, land, 1000);
		sells(helper, r, 1, r.neutral(), "10.5 emeralds", false, "a neutral player at the Prince's board");
		sells(helper, r, 1, r.rival(), "13.13 emeralds", true, "the rival at the Prince's board");
		Bought b = buy(helper, r, r.rival(), 1000, 13);
		helper.assertTrue(b.logs() == 0 && b.emeralds() == 13 && b.said().equals("You can't pay 13.13 emeralds for a bundle of Test Timber."),
			"the rival with 13 at the Prince's board: " + b);
		b = buy(helper, r, r.rival(), 1000, 14);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "the rival with 14 for 13.13: " + b);
		helper.assertTrue(sellOne(helper, r, r.rival(), 1000) == 7, "the rival's 7.5-emerald sale at the Prince's board wasn't paid 7");
		helper.assertTrue(sellOne(helper, r, r.neutral(), 1000) == 10, "a neutral player's 10-emerald sale wasn't paid 10");

		RealmTopicsGameTests.know(land, Map.of("royal_roads", 1, "common_coin", 2));
		prices(helper, land, 1000);
		helper.assertTrue(TradePage.sellPercent(level, land.hall(1)) == 102, "Common Coin II with the Prince: " + TradePage.sellPercent(level, land.hall(1)) + " (wanted 102)");
		sells(helper, r, 1, r.rival(), "12.75 emeralds", true, "the rival, the Prince and Common Coin II");
		sells(helper, r, 1, r.neutral(), "10.2 emeralds", false, "a neutral player, the Prince and Common Coin II");
		helper.succeed();
	}

	/**
	 * The Common Market at the realm (the members' average price: 8 at the capital, 12 at hall 1, so 10) and Common Coin
	 * II: the realm's owner buys at the board's 10, a neutral stranger at 10.4, the rival at 13 (125% of the stranger's
	 * price); the rival is paid 7.5 and reads the feud line; his friend is charged as he is.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104cFeudMarket"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104cFeudMarket")
	public void underTheCommonMarketARivalPaysAQuarterOverTheStrangersPrice(GameTestHelper helper) {
		Rivalry r = rivalry(helper);
		Land land = r.land();
		ServerLevel level = r.level();
		ServerPlayer friend = RealmGameTests.listener(helper, new ArrayList<>(), "qa1104c-rivals-friend");
		io.github.jcondedata.aliveworkplace.build.Friends friends = io.github.jcondedata.aliveworkplace.build.Friends.get(level.getServer());
		friends.add(r.rival().getUUID(), friend.getUUID(), "qa1104c-rivals-friend");
		Leftovers.after(helper, () -> friends.remove(r.rival().getUUID(), friend.getUUID()));
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		RealmTopicsGameTests.know(land, Map.of("royal_roads", 1, "common_coin", 2));
		prices(helper, land, 1000);
		BoardTradeGameTests.price(helper, land.hall(0), TIMBER, 800);
		BoardTradeGameTests.price(helper, land.hall(1), TIMBER, 1200);
		helper.assertTrue(Board.price(level, land.hall(1), TradeGoods.get(TIMBER)) == 1000, "setup: the members' average " + Board.price(level, land.hall(1), TradeGoods.get(TIMBER)));
		sells(helper, r, 1, land.owner(), "10 emeralds", false, "the realm's owner under the Common Market");
		sells(helper, r, 1, r.neutral(), "10.4 emeralds", false, "a stranger under the Common Market");
		sells(helper, r, 1, r.rival(), "13 emeralds", true, "the rival under the Common Market");
		sells(helper, r, 1, friend, "13 emeralds", true, "the rival's friend under the Common Market");
		pays(helper, r, 1, r.rival(), "7.5 emeralds", "what the board pays the rival under the Common Market");
		pays(helper, r, 1, r.neutral(), "10 emeralds", "what the board pays a stranger under the Common Market");
		helper.succeed();
	}

	/**
	 * The feud saved and read back (written into the live data again) still prices the rival at 13 under Common Coin
	 * II, at hall 1 and at the capital (the feud is with the whole realm). The rival offers peace from his hall and the
	 * realm's owner accepts at the capital, not at the member the feud was declared on: "Peace with X: the feud is
	 * over."; a second acceptance says "There is no feud with X."; the rival reads 10.4 with no feud line and buys a
	 * bundle with exactly 11; the rival's own board charges the realm's owner 11 again.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104cFeudPeace"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104cFeudPeace")
	public void peaceMadeAtTheCapitalLiftsTheQuarterAfterASave(GameTestHelper helper) {
		Rivalry r = rivalry(helper);
		Land land = r.land();
		ServerLevel level = r.level();
		MinecraftServer server = level.getServer();
		RealmTopicsGameTests.know(land, Map.of("royal_roads", 1, "common_coin", 2));
		prices(helper, land, 1000);

		Feuds.Data data = Feuds.Data.get(server);
		CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
		List<Feuds.Feud> before = data.feuds();
		data.clear();
		for (Feuds.Feud f : Feuds.Data.load(tag, server.registryAccess()).feuds()) {
			data.put(f);
		}
		helper.assertTrue(data.feuds().equals(before), "the feud read back differs: " + data.feuds() + " vs " + before);
		sells(helper, r, 1, r.rival(), "13 emeralds", true, "the rival after the save");
		sells(helper, r, 0, r.rival(), "13 emeralds", true, "the rival at the capital after the save");
		helper.assertTrue(Feuds.feuding(server, land.at(0), land.at(2)), "the capital isn't in the feud");

		helper.assertTrue(Feuds.offerPeace(level, land.hall(2), land.at(1), r.rival()).ok(), "the rival couldn't offer peace");
		sells(helper, r, 1, r.rival(), "13 emeralds", true, "the rival with peace only offered");
		Pacts.Result accepted = Feuds.acceptPeace(level, land.hall(0), land.at(2), land.owner());
		helper.assertTrue(accepted.ok(), "peace couldn't be accepted at the capital: " + accepted.message().getString());
		helper.assertTrue(accepted.message().getString().equals("Peace with " + land.name(2) + ": the feud is over."), "the acceptance: " + accepted.message().getString());
		Pacts.Result again = Feuds.acceptPeace(level, land.hall(0), land.at(2), land.owner());
		helper.assertTrue(!again.ok() && again.message().getString().equals("There is no feud with " + land.name(2) + "."), "accepted twice: " + again.message().getString());
		helper.assertTrue(!Feuds.feuding(server, land.at(1), land.at(2)) && !Feuds.isRival(server, land.at(1), r.rival()), "the feud stands after peace");

		sells(helper, r, 1, r.rival(), "10.4 emeralds", false, "the rival after peace");
		pays(helper, r, 1, r.rival(), "10 emeralds", "what the board pays the rival after peace");
		Bought b = buy(helper, r, r.rival(), 1000, 11);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "the rival after peace with 11 for 10.4: " + b);
		sells(helper, r, 2, land.owner(), "11 emeralds", false, "the realm's owner at the rival's board after peace");
		helper.succeed();
	}

	/** Only {@code id}'s Legend file loaded, moods and Legend needs off; all put back after. */
	private static void staged(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		Moods.ENABLED = false;
		LegendNeeds.ENABLED = false;
		Legends.reload(level.getServer().getResourceManager());
		Legend legend = Legends.get(id).orElseThrow(() -> new AssertionError(id + " didn't load"));
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(id, legend);
		Legends.setForTest(map);
		LegendPowers.forget();
		Leftovers.after(helper, () -> {
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(32), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	// ---- guards after a job change -----------------------------------------------------------------------------------

	private static final BlockPos BELL = new BlockPos(15, 2, 4);
	private static final BlockPos GRIND_A = new BlockPos(9, 2, 24);
	private static final BlockPos GRIND_B = new BlockPos(21, 2, 24);
	private static final BlockPos CUTTER_A = new BlockPos(11, 2, 25);
	private static final BlockPos CUTTER_B = new BlockPos(19, 2, 26);

	private static void ring(GameTestHelper helper) {
		((BellBlock) Blocks.BELL).attemptToRing(helper.getLevel(), helper.absolutePos(BELL), Direction.NORTH);
	}

	private static double to(GameTestHelper helper, Villager v, BlockPos relative) {
		BlockPos p = helper.absolutePos(relative);
		double dx = v.getX() - (p.getX() + 0.5);
		double dz = v.getZ() - (p.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** A village (the hall at 15,2,15), the bell, a work-hours day, and a player with {@code emeralds}. */
	private static ServerPlayer village(GameTestHelper helper, List<String> seen, int emeralds) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(BELL, Blocks.BELL);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		level.setDayTime(1000);
		Leftovers.after(helper, () -> level.setDayTime(time));
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, emeralds);
		player.setInvulnerable(true);
		return player;
	}

	/** A Novice Mason at his stonecutter, AI on. */
	private static Villager mason(GameTestHelper helper, String name, BlockPos cutter) {
		helper.setBlock(cutter, Blocks.STONECUTTER);
		Villager v = QaHallScreenGameTests.villager(helper, name, cutter.offset(0, 0, -1));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(cutter), PoiTypes.MASON, VillagerProfession.MASON);
		v.setVillagerXp(1);
		v.setNoAi(false);
		return v;
	}

	/** A Novice guard at his grindstone with an iron sword, AI on. */
	private static Villager guard(GameTestHelper helper, String name, BlockPos grindstone) {
		helper.setBlock(grindstone, Blocks.GRINDSTONE);
		Villager v = QaHallScreenGameTests.villager(helper, name, grindstone.offset(0, 0, -1));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(grindstone), PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
		v.setVillagerXp(5);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Guards.updateHealth(v);
		v.setNoAi(false);
		return v;
	}

	/** Job ▾ on {@code v}'s row, then the job twice (the offer, then the paid change), through the hall's own clicks. */
	private static void retrain(GameTestHelper helper, ServerPlayer player, Villager v, VillagerProfession job, List<String> seen) {
		ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
		QaHallScreenGameTests.jobDropdown(helper, player, menu, v);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
		player.closeContainer();
		helper.assertTrue(v.getVillagerData().getProfession() == job, "the change to " + job + " didn't take: " + v.getVillagerData() + "; told " + seen);
	}

	/**
	 * Puts a villager just retrained to the grindstone beside it, as if she had walked there (the walk itself is the
	 * hall's own tests'; in an open test area she can stroll off the platform on the way).
	 */
	private static void atPost(GameTestHelper helper, Villager v) {
		BlockPos at = helper.absolutePos(GRIND_B.offset(0, 0, -1));
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		v.getNavigation().stop();
	}

	private static Villager reloaded(GameTestHelper helper, Villager v) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		return copy;
	}

	private static int guardsCounted(GameTestHelper helper) {
		return VillageNeeds.count(helper.getLevel(), helper.absolutePos(QaHallScreenGameTestsHall.HALL)).guards();
	}

	/** The hall's position in {@link QaHallScreenGameTests#village}. */
	private static final class QaHallScreenGameTestsHall {
		static final BlockPos HALL = new BlockPos(15, 2, 15);
	}

	/**
	 * Steps run one after another, each when its condition holds (checked every tick), failing with its message if it
	 * doesn't within {@code within} ticks of the step before.
	 */
	private static final class Steps {
		private record Step(java.util.function.BooleanSupplier when, Runnable then, int within, String failure) {
		}

		private final GameTestHelper helper;
		private final List<Step> steps = new ArrayList<>();
		private int at = 0;
		java.util.function.Supplier<String> where = () -> "";
		private long since = -1;

		Steps(GameTestHelper helper) {
			this.helper = helper;
		}

		Steps watch(java.util.function.Supplier<String> where) {
			this.where = where;
			return this;
		}

		Steps then(java.util.function.BooleanSupplier when, int within, String failure, Runnable then) {
			steps.add(new Step(when, then, within, failure));
			return this;
		}

		void run() {
			helper.onEachTick(() -> {
				if (at >= steps.size()) {
					return;
				}
				long now = helper.getTick();
				if (since < 0) {
					since = now;
				}
				Step step = steps.get(at);
				try {
					if (step.when().getAsBoolean()) {
						step.then().run();
						at++;
						since = now;
						if (at >= steps.size()) {
							helper.succeed();
						}
					} else if (now - since > step.within()) {
						helper.fail(step.failure() + " [" + where.get() + "]");
					}
				} catch (RuntimeException e) {
					at = steps.size();
					System.out.println("[qa1104c] step " + steps.indexOf(step) + " failed: " + e.getMessage());
					throw e;
				}
			});
		}
	}

	/**
	 * A Novice Mason retrained as a guard at the free grindstone through Job ▾ (5 emeralds): a guard at once, with a
	 * guard's 40 health and counted by the hall. Once he's at his grindstone, the bell rings: he rallies and reaches it.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qa1104cNewGuardBell"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1104cNewGuardBell")
	public void aMasonRetrainedAsAGuardAnswersTheBell(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		ServerPlayer player = village(helper, seen, 5);
		Villager dara = mason(helper, "Dara", CUTTER_A);
		helper.setBlock(GRIND_B, Blocks.GRINDSTONE);
		new Steps(helper).watch(() -> "Dara at " + dara.blockPosition() + " (grindstone " + helper.absolutePos(GRIND_B) + "), " + dara.getVillagerData()
				+ ", site " + QaHallScreenGameTests.site(dara) + ", health " + dara.getMaxHealth() + ", noAi " + dara.isNoAi() + ", activity "
				+ dara.getBrain().getActiveNonCoreActivity() + ", walk target " + dara.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition()))
			.then(() -> helper.getTick() >= 5, 20, "never started", () -> {
				retrain(helper, player, dara, ModVillagers.GUARD, seen);
				helper.assertTrue(Guards.isGuard(dara), "Dara isn't a guard after the change");
				helper.assertTrue(QaHallScreenGameTests.site(dara).equals(Optional.of(helper.absolutePos(GRIND_B))), "her post: " + QaHallScreenGameTests.site(dara));
				helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "the change cost " + (5 - player.getInventory().countItem(Items.EMERALD)) + " (wanted 5)");
			})
			.then(() -> dara.getMaxHealth() == 40f, 40, "a new guard without a guard's health: " + dara.getMaxHealth(), () -> {
				helper.assertTrue(guardsCounted(helper) == 1, "the hall counts " + guardsCounted(helper) + " guards");
			})
			.then(() -> true, 5, "", () -> atPost(helper, dara))
			.then(() -> to(helper, dara, GRIND_B) < 3, 20, "the new guard didn't stay at her grindstone", () -> ring(helper))
			.then(() -> GuardRally.isRallying(dara), 20, "the bell didn't call the new guard", () -> {
			})
			.then(() -> to(helper, dara, BELL) < 5, 400, "the new guard never reached the bell", () -> {
			})
			.run();
	}

	/**
	 * Mid-alarm: Bram, a guard running to the bell, is retrained as a Mason and saved and loaded on the way. After the
	 * reload he is a Mason with a villager's 20 health, not rallying, and the bell rung again (80 ticks on, so the bell
	 * looks for villagers afresh) doesn't call him: he never rallies and never comes within 5 blocks of it (a Mason
	 * strolling by day may wander nearer than the 8 of the hall's own test: one run of 20 saw 7.4), while Cole, still a guard, is at the bell; his
	 * one sword is not doubled.
	 */
	//$ gametest_ticks_batch HUGE '600' '"qa1104cExGuardReload"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qa1104cExGuardReload")
	public void aGuardRetrainedMidAlarmStaysAwayAfterAReload(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		ServerPlayer player = village(helper, seen, 5);
		Villager bram = guard(helper, "Bram", GRIND_A);
		Villager cole = guard(helper, "Cole", GRIND_B); // the control: still a guard, he answers both rings
		helper.setBlock(CUTTER_B, Blocks.STONECUTTER);
		Villager[] back = new Villager[1];
		double[] nearest = {Double.MAX_VALUE};
		String[] when = {""};
		long[] rungAgain = {-1};
		boolean[] rallied = {false};
		helper.onEachTick(() -> {
			if (back[0] != null && back[0].isAlive()) {
				if (rungAgain[0] >= 0 && GuardRally.isRallying(back[0])) {
					rallied[0] = true;
				}
				double d = to(helper, back[0], BELL);
				if (d < nearest[0]) {
					nearest[0] = d;
					when[0] = "tick " + helper.getTick() + ", activity " + back[0].getBrain().getActiveNonCoreActivity() + ", walk target "
						+ back[0].getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition())
						+ ", heard bell " + back[0].getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HEARD_BELL_TIME)
						+ ", rallying " + GuardRally.isRallying(back[0]) + ", job " + back[0].getVillagerData().getProfession();
				}
			}
		});
		helper.runAfterDelay(5, () -> ring(helper));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(GuardRally.isRallying(bram), "setup: the bell didn't call Bram");
			retrain(helper, player, bram, VillagerProfession.MASON, seen);
			back[0] = reloaded(helper, bram);
			// a bell rung again within 60 ticks reuses the villagers it reached the first time: ring again after that
			helper.runAfterDelay(80, () -> {
				Villager b = back[0];
				helper.assertTrue(b.getVillagerData().getProfession() == VillagerProfession.MASON && !Guards.isGuard(b), "after the reload: " + b.getVillagerData());
				helper.assertTrue(b.getMaxHealth() == 20f, "after the reload a guard's health: " + b.getMaxHealth());
				helper.assertTrue(!GuardRally.isRallying(b), "after the reload still rallying to the bell");
				nearest[0] = to(helper, b, BELL);
				when[0] = "the second ring";
				rungAgain[0] = helper.getTick();
				ring(helper);
			});
			helper.runAfterDelay(240, () -> {
				helper.assertTrue(Guards.isGuard(cole) && to(helper, cole, BELL) < 5, "the control guard Cole isn't at the bell: " + to(helper, cole, BELL));
				helper.assertTrue(!rallied[0], "the reloaded Mason rallied to the bell rung again");
				helper.assertTrue(nearest[0] > 5, "the reloaded Mason made for the bell: " + String.format("%.1f", nearest[0]) + " blocks, at " + when[0]
					+ " (the second ring at tick " + rungAgain[0] + ")");
				int swords = 0;
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					swords += back[0].getItemBySlot(slot).is(Items.IRON_SWORD) ? 1 : 0;
				}
				swords += back[0].getInventory().countItem(Items.IRON_SWORD) + player.getInventory().countItem(Items.IRON_SWORD);
				for (net.minecraft.world.entity.item.ItemEntity e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					helper.getBounds().inflate(16))) {
					swords += e.getItem().is(Items.IRON_SWORD) ? e.getItem().getCount() : 0;
				}
				helper.assertTrue(swords <= 1, "iron swords about after the reload: " + swords);
				helper.succeed();
			});
		});
	}

	/**
	 * A Mason made a guard, saved and loaded while running to the rung bell: a guard with 40 health at most (the bonus
	 * not added twice), and the bell rung again calls him and he reaches it.
	 */
	//$ gametest_ticks_batch HUGE '1800' '"qa1104cNewGuardReload"'
	@GameTest(template = HUGE, timeoutTicks = 1800, batch = "qa1104cNewGuardReload")
	public void aNewGuardReloadedMidAlarmKeepsAGuardsHealthAndAnswersAgain(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		ServerPlayer player = village(helper, seen, 5);
		Villager dara = mason(helper, "Dara", CUTTER_A);
		helper.setBlock(GRIND_B, Blocks.GRINDSTONE);
		Villager[] back = new Villager[1];
		new Steps(helper).watch(() -> "Dara at " + dara.blockPosition() + " (grindstone " + helper.absolutePos(GRIND_B) + "), " + dara.getVillagerData()
				+ ", site " + QaHallScreenGameTests.site(dara) + ", health " + dara.getMaxHealth() + ", noAi " + dara.isNoAi() + ", activity "
				+ dara.getBrain().getActiveNonCoreActivity() + ", walk target " + dara.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition()))
			.then(() -> helper.getTick() >= 5, 20, "never started", () -> retrain(helper, player, dara, ModVillagers.GUARD, seen))
			.then(() -> true, 5, "", () -> atPost(helper, dara))
			.then(() -> to(helper, dara, GRIND_B) < 3 && dara.getMaxHealth() == 40f, 40, "the new guard, at her grindstone, without a guard's health", () -> ring(helper))
			.then(() -> GuardRally.isRallying(dara), 20, "the bell didn't call the new guard", () -> back[0] = reloaded(helper, dara))
			.then(() -> back[0].tickCount > 80, 120, "the reloaded guard didn't tick", () -> { // (a bell keeps whom it reaches for 60 ticks after a ring)
				Villager b = back[0];
				helper.assertTrue(Guards.isGuard(b), "after the reload not a guard: " + b.getVillagerData());
				helper.assertTrue(b.getMaxHealth() == 40f && b.getHealth() <= 40f, "after the reload: health " + b.getHealth() + " of " + b.getMaxHealth() + " (wanted 40 at most)");
				ring(helper);
			})
			.then(() -> GuardRally.isRallying(back[0]), 20, "the bell rung again didn't call the reloaded guard", () -> {
			})
			.then(() -> to(helper, back[0], BELL) < 5, 400, "the reloaded guard never reached the bell", () -> {
				helper.assertTrue(back[0].getMaxHealth() == 40f, "at the bell: " + back[0].getMaxHealth());
			})
			.run();
	}

	/** A Mason made a guard at the hall fights a husk that comes near his post (an invulnerable one: the fight lasts). */
	//$ gametest_ticks_batch HUGE '1600' '"qa1104cNewGuardFight"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1104cNewGuardFight")
	public void aMasonRetrainedAsAGuardFightsAtHisPost(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		ServerPlayer player = village(helper, seen, 5);
		Villager dara = mason(helper, "Dara", CUTTER_A);
		helper.setBlock(GRIND_B, Blocks.GRINDSTONE);
		new Steps(helper).watch(() -> "Dara at " + dara.blockPosition() + " (grindstone " + helper.absolutePos(GRIND_B) + "), " + dara.getVillagerData()
				+ ", site " + QaHallScreenGameTests.site(dara) + ", health " + dara.getMaxHealth() + ", noAi " + dara.isNoAi() + ", activity "
				+ dara.getBrain().getActiveNonCoreActivity() + ", walk target " + dara.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition()))
			.then(() -> helper.getTick() >= 5, 20, "never started", () -> retrain(helper, player, dara, ModVillagers.GUARD, seen))
			.then(() -> true, 5, "", () -> atPost(helper, dara))
			.then(() -> to(helper, dara, GRIND_B) < 3, 20, "the new guard didn't stay at her grindstone", () -> {
				helper.assertTrue(!GuardCombat.isFighting(dara), "fighting with nobody about");
				Husk husk = helper.spawn(EntityType.HUSK, GRIND_B.offset(-3, 0, -3));
				husk.setInvulnerable(true);
				Leftovers.after(helper, husk::discard);
			})
			.then(() -> GuardCombat.isFighting(dara), 300, "the new guard never fought the husk by her post", () -> {
			})
			.run();
	}

	/**
	 * In a feud's war (PvP on), village 1's guard is retrained to No job and its Mason made a guard, at village 1's
	 * hall. Before: the guard goes for the enemy owner in the village and the enemy may hurt him, but not the Mason.
	 * After: the ex-guard is no foe's target and can't be hurt by the enemy (the village is protected); the new guard
	 * is the enemy's foe and may be fought.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104cWarRetrain"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104cWarRetrain")
	public void inAWarTheJobChangeDecidesWhoTheEnemyMayFight(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		boolean pacts = Pacts.ENABLED;
		boolean feuds = Feuds.ENABLED;
		boolean wars = Feuds.WARS;
		boolean pvp = server.isPvpAllowed();
		Pacts.ENABLED = true;
		Feuds.ENABLED = true;
		Feuds.WARS = true;
		server.setPvpAllowed(true);
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Feuds.ENABLED = feuds;
			Feuds.WARS = wars;
			server.setPvpAllowed(pvp);
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
			land.entity(1).setProtected(false);
		});
		ServerPlayer them = RealmGameTests.listener(helper, new ArrayList<>(), "qa1104c-defender");
		land.entity(1).setOwner(them.getUUID(), "qa1104c-defender");
		land.entity(1).setProtected(true);
		Realms.round(level, land.hall(1), land.entity(1), 6);
		them.getInventory().add(new ItemStack(Items.EMERALD, 20));

		BlockPos grindA = new BlockPos(10, 2, 6);
		BlockPos grindB = new BlockPos(15, 2, 6);
		BlockPos cutter = new BlockPos(14, 2, 1);
		helper.setBlock(grindA, Blocks.GRINDSTONE);
		helper.setBlock(grindB, Blocks.GRINDSTONE);
		helper.setBlock(cutter, Blocks.STONECUTTER);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(11, 2, 5));
		guard.setNoAi(true);
		Jobs.employ(level, guard, helper.absolutePos(grindA), PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
		guard.setVillagerXp(5);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Villager mason = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 2));
		mason.setNoAi(true);
		Jobs.employ(level, mason, helper.absolutePos(cutter), PoiTypes.MASON, VillagerProfession.MASON);
		mason.setVillagerXp(1);

		ServerPlayer enemy = land.owner();
		BlockPos stand = land.hall(1).offset(0, 0, 1);
		enemy.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
		long now = server.overworld().getGameTime();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now - 1, now - 1 + Feuds.WAR_DAYS * 24000L, false, 0, 0, 0));

		helper.assertTrue(Guards.isFoe(enemy, guard) && VillageProtection.mayHurt(level, enemy, guard), "setup: the war's guard isn't fighting the enemy owner");
		helper.assertTrue(!Guards.isFoe(enemy, mason) && !VillageProtection.mayHurt(level, enemy, mason), "setup: the Mason is in the war");

		// No job for the guard (the hall's No job), the Guard job for the Mason (the hall's paid change)
		Retraining.unemploy(level, them, guard);
		helper.assertTrue(!Guards.isGuard(guard), "the guard is still a guard after No job: " + guard.getVillagerData());
		VillageHalls.FreeStation post = VillageHalls.freeStations(level, land.hall(1), mason).stream()
			.filter(s -> s.pos().equals(helper.absolutePos(grindB)) && VillageHalls.jobs(s).contains(ModVillagers.GUARD)).findFirst()
			.orElseThrow(() -> new AssertionError("the free grindstone isn't offered to the Mason as a guard's post: " + VillageHalls.freeStations(level, land.hall(1), mason)));
		HallRetraining.Outcome outcome = HallRetraining.retrain(them, level, land.hall(1), mason, new VillageHalls.FreeStation(post.pos(), ModVillagers.GUARD, post.poi()));
		helper.assertTrue(outcome == HallRetraining.Outcome.DONE && Guards.isGuard(mason), "the Mason wasn't made a guard: " + outcome + ", " + mason.getVillagerData());

		helper.assertTrue(!Guards.isFoe(enemy, guard), "the jobless ex-guard still goes for the enemy owner");
		helper.assertTrue(!VillageProtection.mayHurt(level, enemy, guard), "the enemy may still hurt the jobless ex-guard in a protected village");
		helper.assertTrue(Guards.isFoe(enemy, mason), "the new guard doesn't go for the enemy owner in the war");
		helper.assertTrue(VillageProtection.mayHurt(level, enemy, mason), "the enemy may not fight the new guard in the war");
		helper.succeed();
	}
}
