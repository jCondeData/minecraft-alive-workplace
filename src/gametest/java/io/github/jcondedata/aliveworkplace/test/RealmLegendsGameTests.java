package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.LeadColoniesPower;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.TradeReachPower;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.CaravanTrade;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 33.23, Legends of the realm. The Founder ({@code lead_colonies}): his village founds colonies from Town rank,
 * sends four settlers instead of two, waits half as long (4 days of 7) between colonies; a colony he blessed says so in
 * both chronicles and his statue stands beside its camp. The Merchant Prince ({@code trade_reach}): his village's board
 * compares the prices of every village within caravan range, its caravans take goods only to the partner paying most,
 * and its spread is halved (110% becomes 105%). Each with the Legend and, the same village, without: nothing changes.
 */
public class RealmLegendsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String COLONY_AREA = "aliveworkplace_test:colony_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation PRINCE = AliveWorkplace.id("merchant_prince");
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;

	/** Only {@code id}'s real file loaded, moods and needs off, while {@code body} runs; the Legends put back after. */
	private static void staged(GameTestHelper helper, ResourceLocation id, Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		Leftovers.after(helper, () -> {
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(32), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend legend = Legends.get(id).orElseThrow(() -> new AssertionError(id + " didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(id, legend);
			Legends.setForTest(map);
			LegendPowers.forget();
			body.accept(legend);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
		}
	}

	/** The Legend made of a new villager at {@code at}, settled in the village at {@code hall}. */
	private static Villager legend(GameTestHelper helper, Legend legend, BlockPos at, BlockPos hall) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend, "test", Optional.of(hall));
		LegendPowers.forget();
		return v;
	}

	private static void gone(Villager legend) {
		LegendRecord.get((ServerLevel) legend.level()).forget(legend.getUUID());
		legend.discard();
		LegendPowers.forget();
	}

	/** A Town owned by nobody in particular, colonies on with the usual limits, no hall rounds; put back after. */
	private static VillageHallBlockEntity town(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		Colonies.COOLDOWN_DAYS = 7;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		RealmData.get(level.getServer()).clearColonies();
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			Colonies.COOLDOWN_DAYS = cooldown;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			RealmData.get(level.getServer()).clearColonies();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setRank(VillageRanks.Rank.TOWN);
		return entity;
	}

	/**
	 * The Founder's colony rules: a Town may not found a colony, waits 7 days after one and spares two settlers; with
	 * the Founder living there it may, waits 4 days and sends four; with him gone it's as before.
	 */
	//$ gametest_ticks_batch AREA '100' '"founderColonies"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "founderColonies")
	public void theFounderLetsATownFoundColoniesWithFourSettlersAndHalfTheWait(GameTestHelper helper) {
		VillageHallBlockEntity entity = town(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, Founder.ID, founder -> {
			helper.assertTrue(founder.powers(LeadColoniesPower.class).equals(List.of(new LeadColoniesPower(VillageRanks.Rank.TOWN, 4, 50))),
				"founder.json's power: " + founder.powers(LeadColoniesPower.class));
			for (int i = 0; i < 6; i++) {
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i, 2, 7));
				v.setNoAi(true);
				v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
			}
			// without him
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.CITY && Colonies.limit(level, hall) == Colonies.Refusal.RANK,
				"a Town without the Founder: " + Colonies.limit(level, hall));
			Settlers.Volunteers two = Settlers.volunteers(level, hall);
			helper.assertTrue(two != null && two.all().size() == 2, "without the Founder two go: " + (two == null ? null : two.all().size()));
			RealmData data = RealmData.get(level.getServer());
			long today = Chronicle.day(level);
			data.recordFounded(GlobalPos.of(level.dimension(), hall), GlobalPos.of(level.dimension(), hall.offset(400, 0, 0)), today - 2);
			helper.assertTrue(Colonies.cooldownDays(level, hall) == 7 && Colonies.cooldownLeft(level, hall) == 5,
				"the wait without him: " + Colonies.cooldownLeft(level, hall));

			// with him
			Villager him = legend(helper, founder, new BlockPos(3, 2, 6), hall);
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.TOWN, "with the Founder: " + Colonies.rankNeeded(level, hall));
			helper.assertTrue(Colonies.cooldownDays(level, hall) == 4 && Colonies.cooldownLeft(level, hall) == 2,
				"the wait with him: " + Colonies.cooldownDays(level, hall) + " " + Colonies.cooldownLeft(level, hall));
			helper.assertTrue(Colonies.limit(level, hall) == Colonies.Refusal.COOLDOWN, "still waiting two days: " + Colonies.limit(level, hall));
			data.clearColonies();
			helper.assertTrue(Colonies.limit(level, hall) == null, "a Town with the Founder may found: " + Colonies.limit(level, hall));
			Settlers.Volunteers four = Settlers.volunteers(level, hall);
			helper.assertTrue(four != null && four.all().size() == 4 && four.all().stream().noneMatch(v -> v == him),
				"with the Founder four go (never him): " + (four == null ? null : four.all().size()));
			helper.assertTrue(founder.powers(LeadColoniesPower.class).get(0).describe().getString()
				.equals("Leads the colonies: from Town rank on, 4 settlers each, the wait between them 50% as long, and his statue beside each camp"),
				"the power's line: " + founder.powers(LeadColoniesPower.class).get(0).describe().getString());

			// gone again
			gone(him);
			helper.assertTrue(Colonies.limit(level, hall) == Colonies.Refusal.RANK && Settlers.volunteers(level, hall).all().size() == 2
				&& Colonies.cooldownDays(level, hall) == 7, "with the Founder gone: " + Colonies.limit(level, hall));
			entity.setRank(VillageRanks.Rank.TOWN);
		});
		helper.succeed();
	}

	/** The Founder's blessing on the order: given when he lives in the mother village, not without him. */
	//$ gametest_ticks_batch AREA '100' '"founderBlessing"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "founderBlessing")
	public void anOrderGivenWithTheFounderCarriesHisBlessing(GameTestHelper helper) {
		town(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, Founder.ID, founder -> {
			helper.assertTrue(LeadColoniesPower.of(level, hall).isEmpty(), "a power without him");
			Villager him = legend(helper, founder, new BlockPos(3, 2, 6), hall);
			helper.assertTrue(LeadColoniesPower.of(level, hall).isPresent(), "no power with him");
			gone(him);
			// a saved order from before 33.23 reads unblessed
			RealmData.Order old = RealmData.Order.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,
				RealmData.Order.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
					new RealmData.Order(GlobalPos.of(level.dimension(), hall), hall.offset(300, 0, 0), Optional.empty(), RealmData.ON_ROAD, 0, 0, 3200)).getOrThrow()).getOrThrow();
			helper.assertTrue(!old.blessed(), "an old order came back blessed");
			RealmData.Order blessed = new RealmData.Order(old.mother(), old.spot(), old.name(), old.state(), 0, 0, 3200, 0, List.of(), Optional.empty(), Map.of(), List.of(), true);
			RealmData.Order back = RealmData.Order.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,
				RealmData.Order.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, blessed).getOrThrow()).getOrThrow();
			helper.assertTrue(back.blessed() && back.withState(RealmData.ARRIVING, 1, 2).blessed(), "the blessing didn't survive a save");
		});
		helper.succeed();
	}

	/** A settler as the road keeps them. */
	private static CompoundTag saved(GameTestHelper helper, String name, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		CompoundTag tag = new CompoundTag();
		v.saveWithoutId(tag);
		v.discard();
		return tag;
	}

	private static void openSky(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** The colony founded from an order {@code blessed} or not: both chronicles, and the statue beside the camp. */
	private static void arrival(GameTestHelper helper, boolean blessed) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		boolean enabled = Colonies.ENABLED;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		Colonies.ENABLED = true;
		RealmData realms = RealmData.get(level.getServer());
		realms.clearColonies();
		openSky(helper);
		BlockPos motherAt = new BlockPos(3, 2, 22);
		helper.setBlock(motherAt, ModBlocks.VILLAGE_HALL);
		BlockPos mother = helper.absolutePos(motherAt);
		((VillageHallBlockEntity) helper.getBlockEntity(motherAt)).setCustomName(Component.literal("Thornholm"));
		AABB area = helper.getBounds();
		Leftovers.after(helper, () -> {
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			Colonies.ENABLED = enabled;
			Caravans.Data caravans = Caravans.Data.get(level);
			for (RealmData.Founded f : realms.founded()) {
				caravans.remove(f.hall().pos());
				io.github.jcondedata.aliveworkplace.travel.TravelNetwork network = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.get(level.getServer());
				for (BlockPos p : BlockPos.betweenClosed(f.hall().pos().offset(-8, -2, -8), f.hall().pos().offset(8, 2, 8))) {
					network.remove(GlobalPos.of(level.dimension(), p.immutable()));
				}
			}
			caravans.remove(mother);
			caravans.clearRoad(mother);
			realms.clearColonies();
			io.github.jcondedata.aliveworkplace.build.BuildSiteManager sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
			for (var site : List.copyOf(sites.all())) {
				if (area.inflate(16).contains(site.placement().origin().getCenter())) {
					sites.remove(site.id());
				}
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, area.inflate(16))) {
				v.discard();
			}
			// the statue may stand past the area's walls: taken down again
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(area.minX - 24, area.minY, area.minZ - 24), BlockPos.containing(area.maxX + 24, area.maxY + 4, area.maxZ + 24))) {
				if (!area.contains(p.getCenter()) && statueBlock(level, p)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		});
		helper.runAfterDelay(2, () -> {
			CompoundTag tomas = saved(helper, "Tomas", ModVillagers.BUILDER);
			CompoundTag dara = saved(helper, "Dara", VillagerProfession.FARMER);
			long now = level.getGameTime();
			realms.putOrder(new RealmData.Order(GlobalPos.of(level.dimension(), mother), helper.absolutePos(new BlockPos(22, 2, 22)), Optional.of(Component.literal("Newbrook")),
				RealmData.ON_ROAD, now - 100, now, 3200, now - 200, List.of(), Optional.of(tomas.getUUID("UUID")), Map.of(), List.of(tomas, dara), blessed));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(realms.orders().isEmpty() && realms.foundedBy(GlobalPos.of(level.dimension(), mother)).size() == 1, "not founded yet");
			BlockPos colony = realms.foundedBy(GlobalPos.of(level.dimension(), mother)).get(0).hall().pos();
			List<String> theirs = chronicle(level, colony);
			List<String> ours = chronicle(level, mother);
			String day = "Founded on day " + Chronicle.day(level);
			if (blessed) {
				helper.assertTrue(theirs.stream().anyMatch(l -> l.startsWith(day + " with the Founder's blessing, by settlers from Thornholm: Tomas and Dara")),
					"the colony's chronicle: " + theirs);
				helper.assertTrue(ours.stream().anyMatch(l -> l.startsWith("Our settlers Tomas and Dara founded Newbrook") && l.endsWith("with the Founder's blessing")),
					"the mother's chronicle: " + ours);
			} else {
				helper.assertTrue(theirs.stream().anyMatch(l -> l.equals(day + " by settlers from Thornholm: Tomas and Dara")) && theirs.stream().noneMatch(l -> l.contains("blessing")),
					"the colony's chronicle: " + theirs);
				helper.assertTrue(ours.stream().anyMatch(l -> l.startsWith("Our settlers Tomas and Dara founded Newbrook")) && ours.stream().noneMatch(l -> l.contains("blessing")),
					"the mother's chronicle: " + ours);
			}
			BlockPos copper = null;
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(area.minX - 24, area.minY, area.minZ - 24), BlockPos.containing(area.maxX + 24, area.maxY + 4, area.maxZ + 24))) {
				if (level.getBlockState(p).is(Blocks.WAXED_CUT_COPPER)) {
					copper = p.immutable();
					break;
				}
			}
			if (blessed) {
				helper.assertTrue(copper != null && copper.distManhattan(colony) <= 40, "no statue beside the camp: " + copper + " (hall " + colony + ")");
			} else {
				helper.assertTrue(copper == null, "a statue without the blessing at " + copper);
			}
		});
	}

	private static boolean statueBlock(ServerLevel level, BlockPos p) {
		var s = level.getBlockState(p);
		return s.is(Blocks.WAXED_CUT_COPPER) || s.is(Blocks.CHISELED_STONE_BRICKS) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.CRACKED_STONE_BRICKS)
			|| s.is(Blocks.STONE_BRICK_STAIRS) || s.is(Blocks.STONE_BRICK_SLAB) || s.is(Blocks.STONE_BRICK_WALL) || s.is(Blocks.ANDESITE) || s.is(Blocks.ANDESITE_WALL)
			|| s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.POLISHED_ANDESITE_STAIRS) || s.is(Blocks.LANTERN) || s.is(Blocks.STONE_BUTTON) || s.is(Blocks.STONE_SLAB);
	}

	private static List<String> chronicle(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.chronicle().stream().map(x -> x.text().getString()).toList() : List.of();
	}

	/** A blessed colony: "with the Founder's blessing" in both chronicles, and the Founder's statue beside its camp. */
	//$ gametest_ticks_batch COLONY_AREA '200' '"founderBlessedColony"'
	@GameTest(template = COLONY_AREA, timeoutTicks = 200, batch = "founderBlessedColony")
	public void aBlessedColonyHasItsStatueAndItsChronicleLines(GameTestHelper helper) {
		arrival(helper, true);
	}

	/** The same colony without the blessing: the usual lines and no statue. */
	//$ gametest_ticks_batch COLONY_AREA '200' '"founderUnblessedColony"'
	@GameTest(template = COLONY_AREA, timeoutTicks = 200, batch = "founderUnblessedColony")
	public void aColonyWithoutTheFounderHasNoStatue(GameTestHelper helper) {
		arrival(helper, false);
	}

	/** Today's market for {@code hall}: its lists, and Test Timber at {@code cents}. */
	private static void market(ServerLevel level, Caravans.Data data, BlockPos hall, List<ResourceLocation> knownFor, int cents) {
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>();
		prices.put(TIMBER, new Market.Price(cents, cents, 0));
		data.setMarket(hall, new Market(knownFor, List.of(), prices, Chronicle.day(level), List.of()));
	}

	/**
	 * The Merchant Prince's trade: Thornholm (known for timber at 1 emerald) has routes to Ashford (1.3) and Brightwater
	 * (1.6); Dunmore (2.0) is in caravan range but no partner. Without him: the board sells at 110%, compares Ashford and
	 * Brightwater only, and caravans take timber to both. With him: 105%, Dunmore's price is on the board, and timber goes
	 * only to Brightwater. Gone again: as before.
	 */
	//$ gametest_ticks_batch AREA '100' '"princeBoard"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "princeBoard")
	public void theMerchantPrinceSeesEveryPriceSellsWhereItPaysMostAndHalvesTheSpread(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean economy = Economy.ENABLED;
		VillageHalls.RADIUS = 6;
		Economy.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = a.offset(300, 0, 0);
		BlockPos c = a.offset(0, 0, 300);
		BlockPos d = a.offset(-300, 0, 0);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Economy.ENABLED = economy;
			for (BlockPos p : List.of(a, b, c, d)) {
				data.remove(p);
				data.clearRoad(p);
			}
		});
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setCustomName(Component.literal("Thornholm"));
		data.setWants(a, Component.literal("Thornholm"), List.of());
		data.setWants(b, Component.literal("Ashford"), List.of());
		data.setWants(c, Component.literal("Brightwater"), List.of());
		data.setWants(d, Component.literal("Dunmore"), List.of());
		market(level, data, a, List.of(TIMBER), 100);
		market(level, data, b, List.of(), 130);
		market(level, data, c, List.of(), 160);
		market(level, data, d, List.of(), 200);
		helper.assertTrue(data.toggleRoute(a, b) && data.toggleRoute(a, c), "no routes");
		TradeGoods.Good timber = TradeGoods.get(TIMBER);
		helper.assertTrue(timber != null, "no Test Timber");
		List<ItemStack> stock = List.of(new ItemStack(Items.OAK_LOG, 64));

		staged(helper, PRINCE, prince -> {
			helper.assertTrue(prince.powers(TradeReachPower.class).equals(List.of(new TradeReachPower(50))), "merchant_prince.json's power: " + prince.powers(TradeReachPower.class));
			// without him
			helper.assertTrue(TradePage.sellPercent(level, a) == 110, "the spread without him: " + TradePage.sellPercent(level, a));
			helper.assertTrue(TradePage.boardVillages(level, a, data).equals(java.util.Set.of(b, c)), "the board without him: " + TradePage.boardVillages(level, a, data));
			List<String> lore = BoardTradeGameTests.lore(TradePage.goodIcon(level, a, timber, null, null, null));
			helper.assertTrue(lore.stream().anyMatch(l -> l.startsWith("Dearer in Brightwater: ")) && lore.stream().noneMatch(l -> l.contains("Dunmore")), "the board's timber without him: " + lore);
			helper.assertTrue(!CaravanTrade.plan(level, a, b, data, stock).isEmpty() && !CaravanTrade.plan(level, a, c, data, stock).isEmpty(),
				"without him timber goes to both partners");

			// with him
			Villager him = legend(helper, prince, new BlockPos(3, 2, 6), a);
			helper.assertTrue(TradePage.sellPercent(level, a) == 105, "the spread with him: " + TradePage.sellPercent(level, a));
			helper.assertTrue(TradePage.boardVillages(level, a, data).containsAll(List.of(b, c, d)), "the board with him: " + TradePage.boardVillages(level, a, data));
			lore = BoardTradeGameTests.lore(TradePage.goodIcon(level, a, timber, null, null, null));
			helper.assertTrue(lore.stream().anyMatch(l -> l.startsWith("Dearer in Dunmore: ")), "the board's timber with him: " + lore);
			helper.assertTrue(CaravanTrade.plan(level, a, b, data, stock).isEmpty(), "with him timber still goes to Ashford: " + CaravanTrade.plan(level, a, b, data, stock));
			helper.assertTrue(!CaravanTrade.plan(level, a, c, data, stock).isEmpty(), "with him timber doesn't go to Brightwater");
			helper.assertTrue(prince.powers(TradeReachPower.class).get(0).describe().getString()
				.equals("The board knows the prices of every village within caravan range, goods go where they pay most, and the board's spread is 50% of what it was"),
				"the power's line: " + prince.powers(TradeReachPower.class).get(0).describe().getString());

			// gone again
			gone(him);
			helper.assertTrue(TradePage.sellPercent(level, a) == 110 && TradePage.boardVillages(level, a, data).equals(java.util.Set.of(b, c))
				&& !CaravanTrade.plan(level, a, b, data, stock).isEmpty(), "with him gone the board and caravans are as before");
		});
		helper.succeed();
	}
}
