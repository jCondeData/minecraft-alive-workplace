package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.ColonyMap;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 33.8, the Colony Charter: a City's Colonies tab sells the hall's owner a charter for 32 emeralds, out of the
 * treasury first and the buyer's pocket for the rest; the charter is bound to its hall, and a click on its map (the
 * packet the screen sends) or on the ground chooses the spot, which must lie 256 to 1,024 blocks from the hall and 128
 * clear of every other hall; the spot is kept on the charter and named in its tooltip; an anvil names the colony.
 * Refused: below City, without the money, by anyone who doesn't rule the village, with a colony on the road, inside
 * the wait, at the cap, with the switch off, and for the charter of a hall that's gone. Also the screen's
 * map-to-world sum, the "What next?" tip, the map the server sends (nothing loaded for it) and save and reload.
 * Colonies are off in GameTests (other tests count the Trade page's tabs), so every test here turns them on, alone in
 * its batch.
 */
public class ColonyCharterGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	/** A test's village: its hall, owned by {@code player}, who holds nothing. */
	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player, String name) {
		GlobalPos mother() {
			return GlobalPos.of(level.dimension(), hall);
		}
	}

	/**
	 * A hall of {@code rank} with {@code treasury} hundredths of an emerald, owned by a survival player carrying
	 * {@code emeralds}; colonies on with the usual limits, no hall rounds during the test, the colony records empty.
	 * Everything is put back when the test ends.
	 */
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
		// villages earlier batches' tests left on the caravans' list near here aren't this test's (it is alone in its batch)
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

	/** The hall's screen, its minecart pressed and the Colonies tab opened: the way a player gets there. */
	private static ChoiceMenu coloniesTab(GameTestHelper helper, Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		menu.press(VillageHallScreen.ROUTES, v.player());
		ItemStack tab = menu.icon(TradePage.Tab.COLONIES.slot());
		helper.assertTrue(tab.is(Items.FILLED_MAP) && tab.getHoverName().getString().equals("Colonies of " + v.name()), "no Colonies tab: " + tab);
		menu.press(TradePage.Tab.COLONIES.slot(), v.player());
		helper.assertTrue(TradePage.marks(menu.icon(TradePage.Tab.COLONIES.slot())).contains(TradePage.OPEN), "the Colonies tab didn't open");
		helper.assertTrue(menu.icon(Colonies.BUY).is(ModItems.COLONY_CHARTER) && menu.icon(Colonies.BUY).getHoverName().getString().equals("Buy a Colony Charter"),
			"no charter for sale: " + menu.icon(Colonies.BUY));
		return menu;
	}

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	/** The charters the player carries. */
	private static List<ItemStack> charters(ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.COLONY_CHARTER)) {
				out.add(player.getInventory().getItem(i));
			}
		}
		return out;
	}

	/** Buys a charter on the tab and puts it in the player's main hand. */
	private static ItemStack buyAndHold(GameTestHelper helper, Village v) {
		coloniesTab(helper, v).press(Colonies.BUY, v.player());
		List<ItemStack> charters = charters(v.player());
		helper.assertTrue(charters.size() == 1, "the tab sold " + charters.size() + " charters");
		ItemStack charter = charters.get(0).copy();
		v.player().getInventory().clearOrCountMatchingItems(s -> s.is(ModItems.COLONY_CHARTER), -1, v.player().inventoryMenu.getCraftSlots());
		v.player().setItemInHand(InteractionHand.MAIN_HAND, charter);
		return charter;
	}

	private static Optional<BlockPos> spot(ItemStack charter) {
		ColonyCharterItem.Charter data = charter.get(ModComponents.COLONY_CHARTER);
		return data == null ? Optional.empty() : data.spot();
	}

	private static List<String> tooltip(Village v, ItemStack stack) {
		return stack.getTooltipLines(Item.TooltipContext.of(v.level()), v.player(), TooltipFlag.NORMAL).stream().map(Component::getString).toList();
	}

	/** A click on the charter's map at the column {@code dx}, {@code dz} from the hall: the packet the screen sends. */
	private static Colonies.Chosen click(Village v, int dx, int dz) {
		return Colonies.clicked(new Colonies.Choose(true, v.hall().getX() + dx, v.hall().getZ() + dz), v.player());
	}

	/** Below City the tab sells nothing and says why; a City's treasury buys the charter, bound to the hall, no spot yet. */
	//$ gametest_ticks_batch AREA '100' '"colonyRank"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyRank")
	public void theCharterIsRefusedBelowCityAndSoldToACity(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.TOWN, 5000, 64);
		ChoiceMenu menu = coloniesTab(helper, v);
		String why = v.name() + " must be a City to found a colony (it is a Town).";
		helper.assertTrue(BoardTradeGameTests.lore(menu.icon(Colonies.BUY)).contains(why), "the tab doesn't say why: " + BoardTradeGameTests.lore(menu.icon(Colonies.BUY)));
		menu.press(Colonies.BUY, v.player());
		helper.assertTrue(charters(v.player()).isEmpty() && v.entity().treasury() == 5000 && emeralds(v.player()) == 64,
			"a Town bought a charter: " + charters(v.player()).size() + ", treasury " + v.entity().treasury() + ", emeralds " + emeralds(v.player()));
		Colonies.Bought refused = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(refused.refusal() == Colonies.Refusal.RANK && refused.message().getString().equals(why), "the refusal: " + refused.message().getString());

		v.entity().setRank(VillageRanks.Rank.CITY);
		menu = coloniesTab(helper, v);
		List<String> lore = BoardTradeGameTests.lore(menu.icon(Colonies.BUY));
		helper.assertTrue(lore.contains("Costs 32 emeralds") && lore.contains("The treasury pays all of it") && lore.contains("Click to buy"), "the offer: " + lore);
		menu.press(Colonies.BUY, v.player());
		List<ItemStack> charters = charters(v.player());
		helper.assertTrue(charters.size() == 1, "a City got " + charters.size() + " charters");
		ColonyCharterItem.Charter data = charters.get(0).get(ModComponents.COLONY_CHARTER);
		helper.assertTrue(data != null && data.hall().equals(v.mother()) && data.name().getString().equals(v.name()) && data.spot().isEmpty(),
			"the charter isn't bound to its hall: " + data);
		helper.assertTrue(v.entity().treasury() == 1800 && emeralds(v.player()) == 64, "the treasury should pay all 32: treasury " + v.entity().treasury()
			+ ", emeralds " + emeralds(v.player()));
		List<String> tip = tooltip(v, charters.get(0));
		helper.assertTrue(tip.get(0).equals("Colony Charter") && tip.contains("Of " + v.name() + ": founds its colony")
			&& tip.contains("No spot yet: right-click the air for the map,") && tip.contains("or the ground where the colony should go")
			&& tip.contains("Rename it in an anvil to name the colony"), "the new charter's tooltip: " + tip);
		helper.assertTrue(BoardTradeGameTests.lore(menu.icon(Colonies.COUNT)).contains("A colony can set out now")
			&& menu.icon(Colonies.COUNT).getHoverName().getString().equals("Colonies founded: 0 of 3"), "the count: " + menu.icon(Colonies.COUNT).getHoverName().getString());
		helper.succeed();
	}

	/** Without the money nothing is sold or taken; with it the treasury pays what it has and the player the rest. */
	//$ gametest_ticks_batch AREA '100' '"colonyMoney"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyMoney")
	public void theTreasuryPaysWhatItHasAndThePlayerTheRest(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 1050, 21); // 10 whole emeralds in the treasury: 22 to find
		ChoiceMenu menu = coloniesTab(helper, v);
		helper.assertTrue(BoardTradeGameTests.lore(menu.icon(Colonies.BUY)).contains("The treasury pays 10 emeralds, you pay 22 emeralds"),
			"the split: " + BoardTradeGameTests.lore(menu.icon(Colonies.BUY)));
		menu.press(Colonies.BUY, v.player());
		helper.assertTrue(charters(v.player()).isEmpty() && v.entity().treasury() == 1050 && emeralds(v.player()) == 21,
			"sold without the money: treasury " + v.entity().treasury() + ", emeralds " + emeralds(v.player()));
		Colonies.Bought refused = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(refused.refusal() == Colonies.Refusal.MONEY && refused.message().getString()
			.equals("A Colony Charter costs 32 emeralds: the treasury pays 10 emeralds and you don't have the other 22 emeralds."), "the refusal: " + refused.message().getString());
		helper.assertTrue(v.entity().treasury() == 1050 && emeralds(v.player()) == 21, "a refusal took money");

		v.player().getInventory().add(new ItemStack(Items.EMERALD, 3));
		Colonies.Bought bought = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(bought.ok() && bought.fromTreasury() == 10 && bought.fromPlayer() == 22, "who paid: " + bought);
		helper.assertTrue(bought.message().getString().equals("A Colony Charter of " + v.name() + ": the treasury paid 10 emeralds and you paid 22 emeralds."),
			"the message: " + bought.message().getString());
		helper.assertTrue(charters(v.player()).size() == 1 && v.entity().treasury() == 50 && emeralds(v.player()) == 2,
			"after buying: treasury " + v.entity().treasury() + ", emeralds " + emeralds(v.player()));

		// an empty treasury and an empty pocket; then a stranger with a full one
		v.entity().setTreasury(0);
		Colonies.Bought broke = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(broke.refusal() == Colonies.Refusal.MONEY && emeralds(v.player()) == 2, "bought with 2 emeralds: " + broke);
		// in creative the treasury still pays its share and the rest is free, and the tab and the message say so
		v.entity().setTreasury(550);
		v.player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		helper.assertTrue(BoardTradeGameTests.lore(coloniesTab(helper, v).icon(Colonies.BUY)).contains("The treasury pays 5 emeralds; in creative mode the rest is free"),
			"the tab for a creative player: " + BoardTradeGameTests.lore(coloniesTab(helper, v).icon(Colonies.BUY)));
		Colonies.Bought creative = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(creative.ok() && creative.fromTreasury() == 5 && creative.fromPlayer() == 0 && emeralds(v.player()) == 2 && v.entity().treasury() == 50,
			"a creative player's charter: " + creative + ", emeralds " + emeralds(v.player()) + ", treasury " + v.entity().treasury());
		helper.assertTrue(creative.message().getString().equals("A Colony Charter of " + v.name() + ": the treasury paid 5 emeralds, and in creative mode the rest is free."),
			"what a creative player is told: " + creative.message().getString());
		v.player().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		stranger.getInventory().add(new ItemStack(Items.EMERALD, 64));
		v.entity().setTreasury(6400);
		Colonies.Bought theirs = Colonies.buy(v.level(), v.hall(), stranger);
		helper.assertTrue(theirs.refusal() == Colonies.Refusal.NOT_YOURS && v.entity().treasury() == 6400 && emeralds(stranger) == 64,
			"a stranger bought the village's charter: " + theirs);
		helper.assertTrue(theirs.message().getString().equals("Only Owner and their friends can buy a Colony Charter of " + v.name() + "."),
			"what the stranger is told: " + theirs.message().getString());
		v.entity().setOwner(null, "");
		helper.assertTrue(Colonies.buy(v.level(), v.hall(), v.player()).refusal() == Colonies.Refusal.NO_OWNER, "a hall nobody owns sold a charter");
		helper.succeed();
	}

	/**
	 * The spot: too near the hall, beyond the ring and within 128 blocks of another hall are refused and leave the
	 * charter as it was; a good spot is kept on it and named in the tooltip; a right-click on the ground chooses where
	 * the player stands.
	 */
	//$ gametest_ticks_batch AREA '100' '"colonySpot"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonySpot")
	public void aGoodSpotIsKeptAndBadOnesRefused(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 3200, 0);
		buyAndHold(helper, v);
		BlockPos ashford = v.hall().offset(500, 0, 0);
		Caravans.Data.get(v.level()).setWants(ashford, Component.literal("Ashford"), List.of());
		ItemStack held = v.player().getMainHandItem();

		Colonies.Chosen near = click(v, 100, 0);
		helper.assertTrue(!near.ok() && near.message().getString().equals("Too near " + v.name() + ": a colony goes at least 256 blocks from its hall (this is 100)."),
			"100 blocks out: " + near.message().getString());
		Colonies.Chosen far = click(v, 0, -1025);
		helper.assertTrue(!far.ok() && far.message().getString().equals("Too far from " + v.name() + ": a colony goes at most 1024 blocks from its hall (this is 1025)."),
			"1,025 blocks out: " + far.message().getString());
		Colonies.Chosen corner = click(v, 900, 900); // on the map, outside the ring
		helper.assertTrue(!corner.ok(), "the map's corner (1,273 blocks out) was taken");
		Colonies.Chosen crowded = click(v, 560, 40);
		helper.assertTrue(!crowded.ok() && crowded.message().getString().equals("Too near Ashford: a colony keeps 128 blocks from every other Village Hall."),
			"72 blocks from Ashford: " + crowded.message().getString());
		helper.assertTrue(spot(held).isEmpty(), "a refused spot was kept: " + spot(held));

		Colonies.Chosen good = click(v, 433, -433);
		helper.assertTrue(good.ok() && good.message().getString().equals("The colony will go 612 blocks north-east of " + v.name() + "."),
			"a good spot: " + good.message().getString());
		BlockPos kept = spot(held).orElse(null);
		helper.assertTrue(kept != null && kept.getX() == v.hall().getX() + 433 && kept.getZ() == v.hall().getZ() - 433, "the spot on the charter: " + kept);
		helper.assertTrue(tooltip(v, held).contains("The spot: 612 blocks north-east of " + v.name()), "the tooltip: " + tooltip(v, held));
		// the edges of the ring and of Ashford's circle count as in
		helper.assertTrue(click(v, 256, 0).ok() && click(v, 0, 1024).ok() && click(v, 372, 0).ok(), "256, 1,024 and 128 blocks from Ashford should do");
		helper.assertTrue(!click(v, 255, 0).ok() && !click(v, 373, 0).ok(), "255 blocks out, or 127 from Ashford, shouldn't");
		// a bad click afterwards leaves the last good spot
		BlockPos last = spot(held).orElseThrow();
		click(v, 10, 10);
		helper.assertTrue(spot(held).orElseThrow().equals(last), "a refused click moved the spot");

		// on the ground: by the hall it's too near; standing 300 blocks west it's the spot
		BlockHitResult ground = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 1, 5))), Direction.UP, helper.absolutePos(new BlockPos(5, 1, 5)), false);
		v.player().setPos(v.hall().getX() + 2.5, v.hall().getY(), v.hall().getZ() + 2.5);
		v.player().gameMode.useItemOn(v.player(), v.level(), held, InteractionHand.MAIN_HAND, ground);
		helper.assertTrue(spot(held).orElseThrow().equals(last), "a right-click by the hall moved the spot to " + spot(held));
		v.player().setPos(v.hall().getX() - 300 + 0.5, v.hall().getY() + 7, v.hall().getZ() + 0.5);
		v.player().gameMode.useItemOn(v.player(), v.level(), held, InteractionHand.MAIN_HAND, ground);
		helper.assertTrue(spot(held).orElseThrow().equals(new BlockPos(v.hall().getX() - 300, v.hall().getY() + 7, v.hall().getZ())),
			"where the player stood: " + spot(held) + " (hall " + v.hall() + ")");
		helper.assertTrue(tooltip(v, held).contains("The spot: 300 blocks west of " + v.name()), "the tooltip after: " + tooltip(v, held));
		helper.succeed();
	}

	/** One colony on the road at a time, 7 days between colonies, 3 colonies a village: each stops the sale and says so. */
	//$ gametest_ticks_batch AREA '100' '"colonyLimits"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyLimits")
	public void theThreeLimitsStopTheSale(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 6400, 0);
		RealmData data = RealmData.get(v.level().getServer());
		long today = Chronicle.day(v.level());
		BlockPos spot = v.hall().offset(400, 0, 0);

		data.putOrder(new RealmData.Order(v.mother(), spot, Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD, 0, 0, 3200));
		Colonies.Bought onRoad = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(onRoad.refusal() == Colonies.Refusal.ON_ROAD && onRoad.message().getString().equals(v.name() + " already has a colony on the road."),
			"with a colony on the road: " + onRoad.message().getString());
		ChoiceMenu menu = coloniesTab(helper, v);
		helper.assertTrue(menu.icon(Colonies.ON_ROAD).getHoverName().getString().equals("On the way: Newbrook")
			&& BoardTradeGameTests.lore(menu.icon(Colonies.ON_ROAD)).contains("400 blocks east of " + v.name()), "the order on the tab: " + menu.icon(Colonies.ON_ROAD));
		// another village's order is no concern of ours
		data.removeOrder(v.mother());
		data.putOrder(new RealmData.Order(GlobalPos.of(v.level().dimension(), v.hall().offset(3000, 0, 0)), spot, Optional.empty(), RealmData.ON_ROAD, 0, 0, 3200));
		helper.assertTrue(Colonies.limit(v.level(), v.hall()) == null, "another village's order stopped ours: " + Colonies.limit(v.level(), v.hall()));

		data.recordFounded(v.mother(), GlobalPos.of(v.level().dimension(), spot), today - 2);
		Colonies.Bought waiting = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(waiting.refusal() == Colonies.Refusal.COOLDOWN && waiting.message().getString().equals(v.name() + " can send its next colony in 5 days."),
			"two days after a colony: " + waiting.message().getString());
		menu = coloniesTab(helper, v);
		helper.assertTrue(menu.icon(Colonies.COUNT).getHoverName().getString().equals("Colonies founded: 1 of 3")
			&& BoardTradeGameTests.lore(menu.icon(Colonies.COUNT)).contains("The next colony can set out in 5 days"), "the count: " + BoardTradeGameTests.lore(menu.icon(Colonies.COUNT)));
		helper.assertTrue(BoardTradeGameTests.lore(menu.icon(Colonies.FOUNDED)).contains("Founded on day " + (today - 2))
			&& BoardTradeGameTests.lore(menu.icon(Colonies.FOUNDED)).contains("400 blocks east of " + v.name()), "the colony on the tab: " + BoardTradeGameTests.lore(menu.icon(Colonies.FOUNDED)));
		Colonies.COOLDOWN_DAYS = 2;
		helper.assertTrue(Colonies.limit(v.level(), v.hall()) == null, "the wait didn't end after colonyCooldownDays: " + Colonies.limit(v.level(), v.hall()));
		Colonies.COOLDOWN_DAYS = 7;

		data.clearColonies();
		for (int i = 0; i < 3; i++) {
			data.recordFounded(v.mother(), GlobalPos.of(v.level().dimension(), spot.offset(i * 200, 0, 0)), today - 30 - i);
		}
		Colonies.Bought full = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(full.refusal() == Colonies.Refusal.CAP && full.message().getString().equals(v.name() + " has founded all 3 colonies a village may."),
			"after three colonies: " + full.message().getString());
		Colonies.PER_VILLAGE = 4;
		helper.assertTrue(Colonies.buy(v.level(), v.hall(), v.player()).ok(), "coloniesPerVillage 4 should allow a fourth");
		helper.assertTrue(charters(v.player()).size() == 1 && v.entity().treasury() == 3200, "one charter for 32 emeralds: " + charters(v.player()).size()
			+ ", treasury " + v.entity().treasury());
		helper.succeed();
	}

	/** The switch off: no Colonies tab, no charter sold, and a charter bought before chooses nothing; on again, all is back. */
	//$ gametest_ticks_batch AREA '100' '"colonySwitch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonySwitch")
	public void switchedOffNothingIsSoldOrChosen(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 6400, 0);
		ItemStack held = buyAndHold(helper, v);
		helper.assertTrue(TradePage.tabs().contains(TradePage.Tab.COLONIES), "on, the tab should show: " + TradePage.tabs());
		Colonies.ENABLED = false;
		helper.assertTrue(!TradePage.tabs().contains(TradePage.Tab.COLONIES), "off, the tab still shows: " + TradePage.tabs());
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		menu.press(VillageHallScreen.ROUTES, v.player());
		helper.assertTrue(menu.icon(TradePage.Tab.COLONIES.slot()).isEmpty(), "off, the hall shows the tab: " + menu.icon(TradePage.Tab.COLONIES.slot()));
		Colonies.Bought refused = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(refused.refusal() == Colonies.Refusal.OFF && v.entity().treasury() == 3200 && charters(v.player()).size() == 1,
			"off, a charter was sold: " + refused);
		Colonies.Chosen chosen = click(v, 400, 0);
		helper.assertTrue(!chosen.ok() && chosen.message().getString().equals("Colonies are switched off on this server.") && spot(v.player().getMainHandItem()).isEmpty(),
			"off, a spot was chosen: " + chosen.message().getString());
		helper.assertTrue(VillageAdvice.tips(v.level(), v.hall()).stream().noneMatch(t -> t.key().equals("colony")), "off, the hall still suggests a colony");
		Colonies.ENABLED = true;
		helper.assertTrue(click(v, 400, 0).ok() && spot(v.player().getMainHandItem()).isPresent(), "on again, the charter should work");
		helper.assertTrue(held.is(ModItems.COLONY_CHARTER), "the charter is still a charter");
		helper.succeed();
	}

	/** The charter of a hall that's gone chooses nothing and says so; an unbound one (from the creative tab) says where charters come from. */
	//$ gametest_ticks_batch AREA '100' '"colonyHallGone"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyHallGone")
	public void theCharterOfARemovedHallFoundsNothing(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 3200, 0);
		buyAndHold(helper, v);
		helper.assertTrue(click(v, 300, 0).ok(), "with its hall standing the charter should work");
		BlockPos before = spot(v.player().getMainHandItem()).orElseThrow();
		helper.setBlock(HALL, Blocks.AIR);
		Colonies.Chosen chosen = click(v, 0, 500);
		helper.assertTrue(!chosen.ok() && chosen.message().getString().equals(v.name() + " has no Village Hall any more: this charter founds nothing."),
			"with the hall gone: " + chosen.message().getString());
		helper.assertTrue(spot(v.player().getMainHandItem()).orElseThrow().equals(before), "the spot moved though the hall is gone");
		// right-clicked in the air it opens no map and breaks nothing
		v.player().gameMode.useItem(v.player(), v.level(), v.player().getMainHandItem(), InteractionHand.MAIN_HAND);
		helper.assertTrue(Colonies.limit(v.level(), v.hall()) == Colonies.Refusal.NO_HALL, "a village without a hall may found: " + Colonies.limit(v.level(), v.hall()));

		ItemStack blank = new ItemStack(ModItems.COLONY_CHARTER);
		v.player().setItemInHand(InteractionHand.MAIN_HAND, blank);
		Colonies.Chosen unbound = click(v, 300, 0);
		helper.assertTrue(!unbound.ok() && unbound.message().getString().equals("This charter belongs to no village: buy one on a City's Colonies tab."),
			"an unbound charter: " + unbound.message().getString());
		v.player().gameMode.useItem(v.player(), v.level(), blank, InteractionHand.MAIN_HAND);
		helper.assertTrue(tooltip(v, blank).contains("Bought at a City's Village Hall: Trade page, Colonies tab"), "the blank charter's tooltip: " + tooltip(v, blank));
		// holding something else when the click arrives
		v.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		helper.assertTrue(!click(v, 300, 0).ok(), "a stick chose a spot");
		helper.succeed();
	}

	/** Save and reload: the charter keeps its hall and spot through the item's saved form, and the colony records come back as written. */
	//$ gametest_ticks_batch AREA '100' '"colonySave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonySave")
	public void saveAndReloadKeepTheSpotAndTheRecords(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 3200, 0);
		buyAndHold(helper, v);
		helper.assertTrue(click(v, -433, 433).ok(), "the spot wasn't chosen");
		ItemStack held = v.player().getMainHandItem();
		Tag saved = held.save(v.level().registryAccess());
		ItemStack loaded = ItemStack.parse(v.level().registryAccess(), saved).orElseThrow();
		ColonyCharterItem.Charter data = loaded.get(ModComponents.COLONY_CHARTER);
		helper.assertTrue(data != null && data.equals(held.get(ModComponents.COLONY_CHARTER)) && data.hall().equals(v.mother())
			&& data.spot().orElseThrow().getX() == v.hall().getX() - 433, "the charter after a reload: " + data);
		helper.assertTrue(tooltip(v, loaded).contains("The spot: 612 blocks south-west of " + v.name()), "its tooltip: " + tooltip(v, loaded));
		// a charter saved before any spot was chosen (and one from a save without the field) loads with none
		ItemStack fresh = ColonyCharterItem.of(v.level(), v.hall());
		helper.assertTrue(spot(ItemStack.parse(v.level().registryAccess(), fresh.save(v.level().registryAccess())).orElseThrow()).isEmpty(), "a spot from nowhere");

		RealmData realms = RealmData.get(v.level().getServer());
		long today = Chronicle.day(v.level());
		GlobalPos colony = GlobalPos.of(v.level().dimension(), v.hall().offset(600, 0, 0));
		realms.putOrder(new RealmData.Order(v.mother(), v.hall().offset(-433, 0, 433), Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD, 100, 4000, 3200));
		realms.recordFounded(v.mother(), colony, today);
		RealmData back = RealmData.load(realms.save(new CompoundTag(), v.level().registryAccess()), v.level().registryAccess());
		helper.assertTrue(back.orders().equals(realms.orders()) && back.orders().size() == 1 && back.orderOf(v.mother()).name().orElseThrow().getString().equals("Newbrook"),
			"the orders after a reload: " + back.orders());
		helper.assertTrue(back.foundedBy(v.mother()).equals(List.of(new RealmData.Founded(v.mother(), colony, today)))
			&& back.cooldown(v.mother()).equals(new RealmData.Cooldown(v.mother(), today, 1)), "the records after a reload: " + back.founded() + " " + back.cooldown(v.mother()));
		// a world from before colonies: an empty file loads empty
		RealmData old = RealmData.load(new CompoundTag(), v.level().registryAccess());
		helper.assertTrue(old.orders().isEmpty() && old.founded().isEmpty() && old.cooldown(v.mother()).founded() == 0, "an empty file isn't empty");
		helper.succeed();
	}

	/** Renamed in an anvil, the charter names the colony; it stays bound and keeps its spot. */
	//$ gametest_ticks_batch AREA '100' '"colonyName"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyName")
	public void renamingInAnAnvilNamesTheColony(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 3200, 0);
		buyAndHold(helper, v);
		helper.assertTrue(click(v, 433, -433).ok(), "the spot wasn't chosen");
		ItemStack held = v.player().getMainHandItem();
		helper.assertTrue(ColonyCharterItem.colonyName(held) == null, "a new charter has a name: " + ColonyCharterItem.colonyName(held));
		helper.setBlock(new BlockPos(6, 2, 6), Blocks.ANVIL);
		v.player().giveExperienceLevels(5);
		AnvilMenu anvil = new AnvilMenu(1, v.player().getInventory(), ContainerLevelAccess.create(v.level(), helper.absolutePos(new BlockPos(6, 2, 6))));
		anvil.getSlot(0).set(held.copy());
		anvil.setItemName("Newbrook");
		ItemStack named = anvil.getSlot(2).getItem();
		helper.assertTrue(named.is(ModItems.COLONY_CHARTER) && named.get(DataComponents.CUSTOM_NAME) != null, "the anvil made " + named);
		helper.assertTrue(ColonyCharterItem.colonyName(named).getString().equals("Newbrook"), "the colony's name: " + ColonyCharterItem.colonyName(named));
		helper.assertTrue(named.get(ModComponents.COLONY_CHARTER).equals(held.get(ModComponents.COLONY_CHARTER)), "the anvil changed the charter");
		List<String> tip = tooltip(v, named);
		helper.assertTrue(tip.contains("The colony will be called Newbrook") && tip.contains("The spot: 612 blocks north-east of " + v.name())
			&& !tip.contains("Rename it in an anvil to name the colony"), "the named charter's tooltip: " + tip);
		helper.succeed();
	}

	/** "What next?" suggests a colony once the village is a City and could send one, and not before or while it can't. */
	//$ gametest_ticks_batch AREA '100' '"colonyAdvice"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyAdvice")
	public void whatNextSuggestsAColonyOnceACity(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.TOWN, 0, 0);
		helper.assertTrue(VillageAdvice.tips(v.level(), v.hall()).stream().noneMatch(t -> t.key().equals("colony")), "a Town is told to found a colony");
		v.entity().setRank(VillageRanks.Rank.CITY);
		VillageAdvice.Tip tip = VillageAdvice.tips(v.level(), v.hall()).stream().filter(t -> t.key().equals("colony")).findFirst().orElse(null);
		helper.assertTrue(tip != null && tip.icon() == ModItems.COLONY_CHARTER, "a City isn't told to found a colony");
		helper.assertTrue(tip.title().getString().equals("Found a colony") && tip.how().getString().equals(
			"On the hall's Trade page, the Colonies tab sells a Colony Charter for 32 emeralds; its map chooses a spot 256 to 1024 blocks away"),
			"the tip: " + tip.title().getString() + " / " + tip.how().getString());
		Colonies.RANK = VillageRanks.Rank.TOWN; // colonyRank: town
		v.entity().setRank(VillageRanks.Rank.TOWN);
		helper.assertTrue(VillageAdvice.tips(v.level(), v.hall()).stream().anyMatch(t -> t.key().equals("colony")), "colonyRank town: a Town should be told");
		helper.assertTrue(Colonies.limit(v.level(), v.hall()) == null, "colonyRank town: a Town should be able to buy");
		RealmData.get(v.level().getServer()).putOrder(new RealmData.Order(v.mother(), v.hall().offset(400, 0, 0), Optional.empty(), RealmData.GATHERING, 0, 0, 0));
		helper.assertTrue(VillageAdvice.tips(v.level(), v.hall()).stream().noneMatch(t -> t.key().equals("colony")), "told to found a colony with one on the road");
		helper.succeed();
	}

	/**
	 * The map the server sends for the charter: 128 by 128 map colours with the land here drawn and nothing loaded for
	 * it, every other village on the map with its name (not the mother village, not one off the map), and the spot.
	 */
	//$ gametest_ticks_batch AREA '100' '"colonyMapData"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyMapData")
	public void theMapShowsLoadedLandAndEveryVillageWithoutLoadingAnything(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 3200, 0);
		buyAndHold(helper, v);
		Caravans.Data caravans = Caravans.Data.get(v.level());
		caravans.setWants(v.hall(), Component.literal(v.name()), List.of());
		caravans.setWants(v.hall().offset(500, 0, -200), Component.literal("Ashford"), List.of());
		caravans.setWants(v.hall().offset(-1500, 0, 0), Component.literal("Farholt"), List.of());
		helper.assertTrue(click(v, -300, 300).ok(), "the spot wasn't chosen");
		int loaded = v.level().getChunkSource().getLoadedChunksCount();
		Colonies.Open open = Colonies.screen(v.level(), v.player().getMainHandItem(), true);
		helper.assertTrue(v.level().getChunkSource().getLoadedChunksCount() == loaded, "drawing the map loaded chunks: " + loaded + " -> "
			+ v.level().getChunkSource().getLoadedChunksCount());
		helper.assertTrue(open != null && open.colors().length == ColonyMap.PIXELS * ColonyMap.PIXELS, "the map's colours");
		helper.assertTrue(open.hall().equals(v.hall()) && open.village().getString().equals(v.name()) && open.mainHand(), "whose map: " + open.hall());
		// the pixel the hall stands in is drawn (its chunk is loaded); most of the map isn't
		int here = open.colors()[ColonyMap.PIXELS / 2 + ColonyMap.PIXELS / 2 * ColonyMap.PIXELS] & 0xFF;
		int drawn = 0;
		for (byte b : open.colors()) {
			drawn += b != 0 ? 1 : 0;
		}
		helper.assertTrue(here != 0, "the land by the hall isn't drawn");
		helper.assertTrue(drawn < open.colors().length, "every pixel is drawn, though the server hasn't the whole 2,048 blocks loaded");
		for (int px = 0; px < ColonyMap.PIXELS; px++) {
			for (int pz = 0; pz < ColonyMap.PIXELS; pz++) {
				int x = v.hall().getX() - ColonyMap.HALF + px * ColonyMap.STEP;
				int z = v.hall().getZ() - ColonyMap.HALF + pz * ColonyMap.STEP;
				if (open.colors()[px + pz * ColonyMap.PIXELS] != 0 && !v.level().hasChunk(x >> 4, z >> 4)) {
					helper.fail("pixel " + px + "," + pz + " is drawn but its chunk isn't loaded");
				}
			}
		}
		List<String> names = open.marks().stream().map(m -> m.name().getString()).toList();
		helper.assertTrue(names.equals(List.of("Ashford")), "the villages on the map: " + names);
		helper.assertTrue(open.marks().get(0).hall().equals(v.hall().offset(500, 0, -200)), "where Ashford is: " + open.marks().get(0).hall());
		helper.assertTrue(open.spot().isPresent() && open.spot().get().getX() == v.hall().getX() - 300, "the spot on the map: " + open.spot());
		helper.succeed();
	}

	/**
	 * The screen's map-to-world sum: at every size the screen draws the map, a click on the place a block column is drawn
	 * lands within 16 blocks of it (one map pixel), the map's corners are the corners of the 2,048 blocks, and the
	 * server keeps the clicked column as the spot.
	 */
	//$ gametest_ticks_batch AREA '100' '"colonyMapSum"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyMapSum")
	public void aClickOnTheMapLandsWithin16BlocksOfThePlace(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.CITY, 3200, 0);
		buyAndHold(helper, v);
		int[][] places = {{433, -433}, {-256, 0}, {0, 1023}, {-700, -700}, {1023, 5}, {-1024, -3}, {17, 300}, {-999, 31}};
		for (int size : new int[] {96, 128, 173, 200, 256}) {
			for (int origin : new int[] {0, 37, 211}) {
				helper.assertTrue(ColonyMap.toWorld(v.hall().getX(), origin, origin, size) == v.hall().getX() - 1024, "the map's left edge at size " + size);
				helper.assertTrue(ColonyMap.toWorld(v.hall().getZ(), origin + size - 0.01, origin, size) == v.hall().getZ() + 1023, "the map's bottom edge at size " + size);
				for (int[] place : places) {
					int x = v.hall().getX() + place[0];
					int z = v.hall().getZ() + place[1];
					// the screen pixel the column is drawn in, then a click on that pixel's middle
					double sx = Math.floor(ColonyMap.toScreen(v.hall().getX(), x, origin, size)) + 0.5;
					double sz = Math.floor(ColonyMap.toScreen(v.hall().getZ(), z, origin, size)) + 0.5;
					helper.assertTrue(sx >= origin && sx < origin + size && sz >= origin && sz < origin + size, "a place on the map is drawn off it: " + place[0] + "," + place[1]);
					int cx = ColonyMap.toWorld(v.hall().getX(), sx, origin, size);
					int cz = ColonyMap.toWorld(v.hall().getZ(), sz, origin, size);
					if (Math.abs(cx - x) > 16 || Math.abs(cz - z) > 16) {
						helper.fail("at size " + size + " a click on " + place[0] + "," + place[1] + " landed " + (cx - x) + "," + (cz - z) + " blocks off");
					}
					helper.assertTrue(ColonyMap.onMap(v.hall(), cx, cz), "the click left the map");
				}
			}
		}
		// through the server: the click's column is the spot, within 16 blocks of the place meant
		int size = 200;
		int origin = 37;
		int x = v.hall().getX() + 433;
		int z = v.hall().getZ() - 433;
		int cx = ColonyMap.toWorld(v.hall().getX(), Math.floor(ColonyMap.toScreen(v.hall().getX(), x, origin, size)) + 0.5, origin, size);
		int cz = ColonyMap.toWorld(v.hall().getZ(), Math.floor(ColonyMap.toScreen(v.hall().getZ(), z, origin, size)) + 0.5, origin, size);
		Colonies.Chosen chosen = Colonies.clicked(new Colonies.Choose(true, cx, cz), v.player());
		BlockPos kept = spot(v.player().getMainHandItem()).orElse(null);
		helper.assertTrue(chosen.ok() && kept != null && Math.abs(kept.getX() - x) <= 16 && Math.abs(kept.getZ() - z) <= 16, "the spot kept: " + kept + ", meant " + x + "," + z);
		if (!v.level().hasChunk(kept.getX() >> 4, kept.getZ() >> 4)) {
			helper.assertTrue(kept.getY() == v.hall().getY(), "a spot the server hasn't loaded takes the hall's height: " + kept);
		}
		// off the map there is nothing to choose
		helper.assertTrue(!ColonyMap.onMap(v.hall(), ColonyMap.toWorld(v.hall().getX(), origin - 1, origin, size), v.hall().getZ()), "left of the map counts as on it");
		helper.assertTrue(ColonyMap.verdict(v.hall(), v.hall().getX() + 182, v.hall().getZ() + 182, List.of()) == ColonyMap.Verdict.OK
			&& ColonyMap.verdict(v.hall(), v.hall().getX() + 180, v.hall().getZ() + 180, List.of()) == ColonyMap.Verdict.TOO_NEAR
			&& ColonyMap.verdict(v.hall(), v.hall().getX() + 725, v.hall().getZ() + 725, List.of()) == ColonyMap.Verdict.TOO_FAR, "the ring is round, not square");
		helper.succeed();
	}
}
