package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.CaravanTrade;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for ROADMAP 33.23 (Legends of the realm) and 33.14 (Colony Camp and Royal Hall), written from the spec: each
 * boundary from both sides (Village rank vs Town for the Founder, the wait at exactly half, caravan range to the block,
 * 32 blocks vs 33 for the Royal Hall), the Legend elsewhere, dead, reloaded and with Legends off, the best-paying
 * partner moving, the Royal Halls not stacking, an unfinished hall, a hall by a member village or in another dimension.
 */
public class QaRealmLegendsBuildsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation PRINCE = AliveWorkplace.id("merchant_prince");
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;

	// ---- staging (as RealmLegendsGameTests) ----

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
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		Moods.ENABLED = false;
		LegendNeeds.ENABLED = false;
		Legends.reload(level.getServer().getResourceManager());
		Legend legend = Legends.get(id).orElseThrow(() -> new AssertionError(id + " didn't load"));
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(id, legend);
		Legends.setForTest(map);
		LegendPowers.forget();
		body.accept(legend);
	}

	private static Villager legend(GameTestHelper helper, Legend legend, BlockPos at, BlockPos hall, boolean noAi) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(noAi);
		Legends.make(helper.getLevel(), v, legend, "test", Optional.of(hall));
		LegendPowers.forget();
		return v;
	}

	/** A Town, colonies on from City with a 7-day wait, no hall rounds; put back after. */
	private static VillageHallBlockEntity town(GameTestHelper helper, String batch) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		boolean legends = Legends.ENABLED;
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
			Legends.ENABLED = legends;
			RealmData.get(level.getServer()).clearColonies();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setRank(VillageRanks.Rank.TOWN);
		return entity;
	}

	private static void jobless(GameTestHelper helper, int n, int z) {
		for (int i = 0; i < n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i, 2, z));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		}
	}

	private static void founded(GameTestHelper helper, BlockPos hall, long daysAgo) {
		ServerLevel level = helper.getLevel();
		RealmData data = RealmData.get(level.getServer());
		data.clearColonies();
		data.recordFounded(GlobalPos.of(level.dimension(), hall), GlobalPos.of(level.dimension(), hall.offset(400, 0, 0)), Chronicle.day(level) - daysAgo);
	}

	// ---- the Founder ----

	/** Town rank is the Founder's floor: a Village (one rank below) with him is still refused for its rank, a Town is not. */
	//$ gametest_ticks_batch AREA '100' '"qaFounderBelowTown"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFounderBelowTown")
	public void aVillageBelowTownIsRefusedEvenWithTheFounder(GameTestHelper helper) {
		VillageHallBlockEntity entity = town(helper, "qaFounderBelowTown");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, Founder.ID, founder -> {
			jobless(helper, 6, 7);
			legend(helper, founder, new BlockPos(3, 2, 6), hall, true);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Colonies.limit(level, hall) == Colonies.Refusal.RANK, "a Village with the Founder: " + Colonies.limit(level, hall));
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue(Colonies.limit(level, hall) == Colonies.Refusal.RANK, "a Hamlet with the Founder: " + Colonies.limit(level, hall));
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(Colonies.limit(level, hall) == null, "a Town with the Founder: " + Colonies.limit(level, hall));
			entity.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Colonies.limit(level, hall) == null, "a City with the Founder: " + Colonies.limit(level, hall));
		});
		helper.succeed();
	}

	/** "From Town rank" lowers the bar, never raises it: with colonies configured from Village rank, the Founder leaves it at Village. */
	//$ gametest_ticks_batch AREA '100' '"qaFounderLowerRank"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFounderLowerRank")
	public void theFounderNeverRaisesALowerConfiguredRank(GameTestHelper helper) {
		VillageHallBlockEntity entity = town(helper, "qaFounderLowerRank");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Colonies.RANK = VillageRanks.Rank.VILLAGE;
		staged(helper, Founder.ID, founder -> {
			jobless(helper, 6, 7);
			legend(helper, founder, new BlockPos(3, 2, 6), hall, true);
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.VILLAGE, "the Founder raised the rank to " + Colonies.rankNeeded(level, hall));
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Colonies.limit(level, hall) == null, "a Village with colonies from Village: " + Colonies.limit(level, hall));
		});
		helper.succeed();
	}

	/** The wait is exactly half (7 days: 4): with him, 4 days after the last colony it may found and 3 days after it may not; without him 4 days leaves 3. */
	//$ gametest_ticks_batch AREA '100' '"qaFounderHalfWait"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFounderHalfWait")
	public void theFoundersWaitIsExactlyHalfFromBothSides(GameTestHelper helper) {
		VillageHallBlockEntity entity = town(helper, "qaFounderHalfWait");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		entity.setRank(VillageRanks.Rank.CITY);
		staged(helper, Founder.ID, founder -> {
			jobless(helper, 6, 7);
			founded(helper, hall, 4);
			helper.assertTrue(Colonies.cooldownLeft(level, hall) == 3 && Colonies.limit(level, hall) == Colonies.Refusal.COOLDOWN,
				"4 days after, without him: " + Colonies.cooldownLeft(level, hall) + " " + Colonies.limit(level, hall));
			founded(helper, hall, 7);
			helper.assertTrue(Colonies.cooldownLeft(level, hall) == 0 && Colonies.limit(level, hall) == null, "7 days after, without him: " + Colonies.limit(level, hall));
			legend(helper, founder, new BlockPos(3, 2, 6), hall, true);
			founded(helper, hall, 3);
			helper.assertTrue(Colonies.cooldownLeft(level, hall) == 1 && Colonies.limit(level, hall) == Colonies.Refusal.COOLDOWN,
				"3 days after, with him: " + Colonies.cooldownLeft(level, hall) + " " + Colonies.limit(level, hall));
			founded(helper, hall, 4);
			helper.assertTrue(Colonies.cooldownLeft(level, hall) == 0 && Colonies.limit(level, hall) == null,
				"4 days after, with him: " + Colonies.cooldownLeft(level, hall) + " " + Colonies.limit(level, hall));
		});
		helper.succeed();
	}

	/** A Founder settled in another village gives this Town nothing: City rank still needed, two settlers, the full wait. */
	//$ gametest_ticks_batch AREA '100' '"qaFounderElsewhere"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFounderElsewhere")
	public void aFounderInAnotherVillageChangesNothingHere(GameTestHelper helper) {
		town(helper, "qaFounderElsewhere");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos otherRel = new BlockPos(24, 2, 24);
		helper.setBlock(otherRel, ModBlocks.VILLAGE_HALL);
		BlockPos other = helper.absolutePos(otherRel);
		staged(helper, Founder.ID, founder -> {
			jobless(helper, 6, 7);
			// he stands right by our hall, but he lives in the other village
			legend(helper, founder, new BlockPos(3, 2, 5), other, true);
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.CITY && Colonies.limit(level, hall) == Colonies.Refusal.RANK,
				"the other village's Founder lowered our rank: " + Colonies.limit(level, hall));
			helper.assertTrue(Settlers.volunteers(level, hall).all().size() == 2, "the other village's Founder sent four of ours");
			helper.assertTrue(Colonies.cooldownDays(level, hall) == 7, "the other village's Founder shortened our wait");
			helper.assertTrue(Colonies.rankNeeded(level, other) == VillageRanks.Rank.TOWN, "his own village lost the power");
		});
		helper.succeed();
	}

	/** Four settlers with the Founder, never a child: two jobless adults and a jobless child among four farmers send the two adults and two farmers. */
	//$ gametest_ticks_batch AREA '100' '"qaFounderNoChild"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFounderNoChild")
	public void theFoundersFourSettlersAreNeverAChild(GameTestHelper helper) {
		town(helper, "qaFounderNoChild");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, Founder.ID, founder -> {
			jobless(helper, 2, 7);
			Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 7));
			child.setNoAi(true);
			child.setAge(-24000);
			child.setVillagerData(child.getVillagerData().setProfession(VillagerProfession.NONE));
			for (int i = 0; i < 4; i++) {
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i, 2, 9));
				v.setNoAi(true);
				v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
			}
			Villager him = legend(helper, founder, new BlockPos(3, 2, 6), hall, true);
			Settlers.Volunteers four = Settlers.volunteers(level, hall);
			helper.assertTrue(four != null && four.all().size() == 4, "volunteers with the Founder: " + (four == null ? null : four.all().size()));
			helper.assertTrue(four.all().stream().noneMatch(v -> v == child || v == him), "a child or the Founder went");
			long jobless = four.all().stream().filter(v -> v.getVillagerData().getProfession() == VillagerProfession.NONE).count();
			helper.assertTrue(jobless == 2, "the jobless adults first: " + jobless + " of 4");
		});
		helper.succeed();
	}

	/** The Founder dying turns his powers off by itself (no cache cleared by hand): within a refresh the Town needs City again and sends two. */
	//$ gametest_ticks_batch AREA '400' '"qaFounderDies"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaFounderDies")
	public void theFoundersDeathEndsHisPowers(GameTestHelper helper) {
		town(helper, "qaFounderDies");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, Founder.ID, founder -> {
			jobless(helper, 6, 7);
			Villager him = legend(helper, founder, new BlockPos(3, 2, 6), hall, true);
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.TOWN, "no power with him alive");
			him.kill();
		});
		helper.runAfterDelay(LegendPowers.REFRESH + 10, () -> {
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.CITY && Colonies.limit(level, hall) == Colonies.Refusal.RANK,
				"his power outlived him: " + Colonies.rankNeeded(level, hall));
			helper.assertTrue(Settlers.volunteers(level, hall).all().size() == 2 && Colonies.cooldownDays(level, hall) == 7, "four settlers or half the wait after his death");
			helper.succeed();
		});
	}

	/** Saved and loaded again (the villager written to NBT and read back as a new entity), the Founder's powers come back by themselves. */
	//$ gametest_ticks_batch AREA '700' '"qaFounderReload"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaFounderReload")
	public void theFoundersPowersSurviveASaveAndReload(GameTestHelper helper) {
		town(helper, "qaFounderReload");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, Founder.ID, founder -> {
			jobless(helper, 6, 7);
			Villager him = legend(helper, founder, new BlockPos(3, 2, 6), hall, false);
			CompoundTag tag = new CompoundTag();
			him.save(tag);
			him.discard();
			LegendPowers.forget();
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.CITY, "a power while he's unloaded");
			Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(back instanceof Villager && level.addFreshEntity(back), "he didn't load back");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.TOWN && Colonies.cooldownDays(level, hall) == 4,
				"the Founder's power after a reload: " + Colonies.rankNeeded(level, hall));
		});
	}

	/** Legends switched off in the config: the Founder's and Merchant Prince's powers are off with them. */
	//$ gametest_ticks_batch AREA '100' '"qaLegendsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendsOff")
	public void legendsOffTurnsTheFoundersPowersOff(GameTestHelper helper) {
		town(helper, "qaLegendsOff");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, Founder.ID, founder -> {
			jobless(helper, 6, 7);
			legend(helper, founder, new BlockPos(3, 2, 6), hall, true);
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.TOWN, "no power with Legends on");
			Legends.ENABLED = false;
			LegendPowers.forget();
			helper.assertTrue(Colonies.rankNeeded(level, hall) == VillageRanks.Rank.CITY && Colonies.cooldownDays(level, hall) == 7
				&& Settlers.volunteers(level, hall).all().size() == 2, "the Founder's power with Legends off");
			Legends.ENABLED = true;
			LegendPowers.forget();
		});
		helper.succeed();
	}

	// ---- the Merchant Prince ----

	private static void market(ServerLevel level, Caravans.Data data, BlockPos hall, List<ResourceLocation> knownFor, int cents) {
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>();
		prices.put(TIMBER, new Market.Price(cents, cents, 0));
		data.setMarket(hall, new Market(knownFor, List.of(), prices, Chronicle.day(level), List.of()));
	}

	/** Thornholm at {@code a} with villages at the given offsets named and priced; cleaned up after. */
	private static BlockPos trading(GameTestHelper helper, Caravans.Data data, List<BlockPos> others) {
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
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Economy.ENABLED = economy;
			data.remove(a);
			data.clearRoad(a);
			for (BlockPos p : others) {
				data.remove(p);
				data.clearRoad(p);
			}
		});
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setCustomName(Component.literal("Thornholm"));
		data.setWants(a, Component.literal("Thornholm"), List.of());
		market(level, data, a, List.of(TIMBER), 100);
		return a;
	}

	/** The board reaches every village within caravan range to the block: one at exactly the range is on it, one a block past is not (and neither without him). */
	//$ gametest_ticks_batch AREA '100' '"qaPrinceRange"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPrinceRange")
	public void thePrincesBoardStopsAtCaravanRange(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		BlockPos base = helper.absolutePos(HALL);
		int range = Caravans.RANGE;
		BlockPos edge = base.offset(range, 0, 0);
		BlockPos past = base.offset(0, 0, range + 1);
		BlockPos a = trading(helper, data, List.of(edge, past));
		data.setWants(edge, Component.literal("Edgewick"), List.of());
		data.setWants(past, Component.literal("Farmoor"), List.of());
		market(level, data, edge, List.of(), 150);
		market(level, data, past, List.of(), 300);
		staged(helper, PRINCE, prince -> {
			helper.assertTrue(TradePage.boardVillages(level, a, data).isEmpty(), "the board without him and no partners: " + TradePage.boardVillages(level, a, data));
			legend(helper, prince, new BlockPos(3, 2, 6), a, true);
			Set<BlockPos> board = TradePage.boardVillages(level, a, data);
			helper.assertTrue(board.contains(edge), "a village exactly at caravan range (" + range + ") is off the board: " + board);
			helper.assertTrue(!board.contains(past), "a village a block past caravan range is on the board: " + board);
		});
		helper.succeed();
	}

	/** The goods follow the best payer: of three partners the one paying most gets the timber; when its price drops below another's, the timber goes there instead. */
	//$ gametest_ticks_batch AREA '100' '"qaPrinceBestPayer"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPrinceBestPayer")
	public void thePrincesCaravansFollowTheBestPayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		BlockPos base = helper.absolutePos(HALL);
		BlockPos b = base.offset(300, 0, 0);
		BlockPos c = base.offset(0, 0, 300);
		BlockPos d = base.offset(-300, 0, 0);
		BlockPos a = trading(helper, data, List.of(b, c, d));
		data.setWants(b, Component.literal("Ashford"), List.of());
		data.setWants(c, Component.literal("Brightwater"), List.of());
		data.setWants(d, Component.literal("Dunmore"), List.of());
		market(level, data, b, List.of(), 130);
		market(level, data, c, List.of(), 160);
		market(level, data, d, List.of(), 150);
		helper.assertTrue(data.toggleRoute(a, b) && data.toggleRoute(a, c) && data.toggleRoute(a, d), "no routes");
		List<ItemStack> stock = List.of(new ItemStack(Items.OAK_LOG, 64));
		staged(helper, PRINCE, prince -> {
			helper.assertTrue(!CaravanTrade.plan(level, a, b, data, stock).isEmpty() && !CaravanTrade.plan(level, a, c, data, stock).isEmpty()
				&& !CaravanTrade.plan(level, a, d, data, stock).isEmpty(), "without him timber goes to all three");
			legend(helper, prince, new BlockPos(3, 2, 6), a, true);
			helper.assertTrue(!CaravanTrade.plan(level, a, c, data, stock).isEmpty() && CaravanTrade.plan(level, a, b, data, stock).isEmpty()
				&& CaravanTrade.plan(level, a, d, data, stock).isEmpty(), "with him only Brightwater (1.6) gets timber");
			market(level, data, c, List.of(), 120);
			helper.assertTrue(!CaravanTrade.plan(level, a, d, data, stock).isEmpty() && CaravanTrade.plan(level, a, c, data, stock).isEmpty()
				&& CaravanTrade.plan(level, a, b, data, stock).isEmpty(), "Brightwater fell to 1.2: only Dunmore (1.5) gets timber");
		});
		helper.succeed();
	}

	/** A Merchant Prince in another village leaves this one's board as it was: 110%, partners only, timber to every partner that pays more. */
	//$ gametest_ticks_batch AREA '100' '"qaPrinceElsewhere"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPrinceElsewhere")
	public void aPrinceInAnotherVillageChangesNothingHere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		BlockPos base = helper.absolutePos(HALL);
		BlockPos b = base.offset(300, 0, 0);
		BlockPos c = base.offset(0, 0, 300);
		BlockPos far = base.offset(-300, 0, 0);
		BlockPos a = trading(helper, data, List.of(b, c, far));
		BlockPos otherRel = new BlockPos(24, 2, 24);
		helper.setBlock(otherRel, ModBlocks.VILLAGE_HALL);
		BlockPos other = helper.absolutePos(otherRel);
		data.setWants(b, Component.literal("Ashford"), List.of());
		data.setWants(c, Component.literal("Brightwater"), List.of());
		data.setWants(far, Component.literal("Dunmore"), List.of());
		market(level, data, b, List.of(), 130);
		market(level, data, c, List.of(), 160);
		market(level, data, far, List.of(), 200);
		helper.assertTrue(data.toggleRoute(a, b) && data.toggleRoute(a, c), "no routes");
		List<ItemStack> stock = List.of(new ItemStack(Items.OAK_LOG, 64));
		staged(helper, PRINCE, prince -> {
			legend(helper, prince, new BlockPos(3, 2, 5), other, true);
			helper.assertTrue(TradePage.sellPercent(level, a) == 110, "the spread here with a Prince elsewhere: " + TradePage.sellPercent(level, a));
			helper.assertTrue(TradePage.boardVillages(level, a, data).equals(Set.of(b, c)), "the board here: " + TradePage.boardVillages(level, a, data));
			helper.assertTrue(!CaravanTrade.plan(level, a, b, data, stock).isEmpty(), "timber stopped going to Ashford");
			helper.assertTrue(TradePage.sellPercent(level, other) == 105, "his own village's spread: " + TradePage.sellPercent(level, other));
		});
		helper.succeed();
	}

	// ---- the Royal Hall ----

	private static BlueprintData.Placement at(ResourceLocation dimension, BlockPos pos) {
		return new BlueprintData.Placement(dimension, pos, Rotation.NONE, Mirror.NONE);
	}

	private static int cap(RealmGameTests.Land land) {
		return RealmTreasury.cap(land.level().getServer(), RealmTreasuryGameTests.theRealm(land));
	}

	/** 32 blocks counts and 33 does not: due east at 32 doubles the cap, at 33 nothing; diagonally (23, 23), 32.5 blocks off, nothing. */
	//$ gametest_ticks_batch AREA '100' '"qaRoyalHallRange"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRoyalHallRange")
	public void theRoyalHallCountsAt32BlocksAndNotAt33(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.finished(helper);
		ServerLevel level = land.level();
		ResourceLocation dim = level.dimension().location();
		BuildSiteManager sites = BuildSiteManager.get(level);
		helper.assertTrue(cap(land) == 256, "a Barony's cap: " + cap(land));
		BlueprintData.Placement p33 = at(dim, land.hall(0).offset(-33, 0, 0));
		sites.recordFinished(StarterBlueprints.ROYAL_HALL.id(), p33, land.owner().getUUID());
		helper.assertTrue(cap(land) == 256, "a Royal Hall 33 blocks off counted: " + cap(land));
		sites.forgetFinished(p33);
		BlueprintData.Placement diag = at(dim, land.hall(0).offset(-23, 0, -23));
		sites.recordFinished(StarterBlueprints.ROYAL_HALL.id(), diag, land.owner().getUUID());
		helper.assertTrue(cap(land) == 256, "a Royal Hall 32.5 blocks off (diagonally) counted: " + cap(land));
		sites.forgetFinished(diag);
		BlueprintData.Placement p32 = at(dim, land.hall(0).offset(-32, 0, 0));
		sites.recordFinished(StarterBlueprints.ROYAL_HALL.id(), p32, land.owner().getUUID());
		helper.assertTrue(cap(land) == 512, "a Royal Hall exactly 32 blocks off: " + cap(land));
		sites.forgetFinished(p32);
		helper.assertTrue(cap(land) == 256, "the cap after it came down: " + cap(land));
		helper.succeed();
	}

	/** The II's three times doesn't stack with a Royal Hall's two (no six times), and two Royal Halls are still two times. */
	//$ gametest_ticks_batch AREA '100' '"qaRoyalHallStack"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRoyalHallStack")
	public void royalHallsDoNotStack(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.finished(helper);
		ServerLevel level = land.level();
		ResourceLocation dim = level.dimension().location();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement one = at(dim, land.hall(0).offset(-10, 0, 0));
		BlueprintData.Placement two = at(dim, land.hall(0).offset(-10, 0, -20));
		sites.recordFinished(StarterBlueprints.ROYAL_HALL.id(), one, land.owner().getUUID());
		sites.recordFinished(BlueprintStyles.styled(StarterBlueprints.ROYAL_HALL.id(), "stonework"), two, land.owner().getUUID());
		helper.assertTrue(cap(land) == 512, "two Royal Halls: " + cap(land) + " (expected 2 times 256)");
		sites.forgetFinished(two);
		sites.recordFinished(StarterBlueprints.ROYAL_HALL_2.id(), two, land.owner().getUUID());
		helper.assertTrue(cap(land) == 768, "a Royal Hall and a Royal Hall II: " + cap(land) + " (expected 3 times 256, not 6)");
		List<String> lore = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(RealmTreasury.TREASURY));
		helper.assertTrue(lore.contains("The Royal Hall by the capital: 3 times the cap") && lore.contains("Holds 0.00 of 768 emeralds"), "the treasury's lines: " + lore);
		sites.forgetFinished(one);
		sites.forgetFinished(two);
		helper.succeed();
	}

	/** A Royal Hall still being built beside the capital gives nothing until it's finished. */
	//$ gametest_ticks_batch AREA '100' '"qaRoyalHallUnfinished"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRoyalHallUnfinished")
	public void anUnfinishedRoyalHallGivesNothing(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.finished(helper);
		ServerLevel level = land.level();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement by = at(level.dimension().location(), land.hall(0).offset(-10, 0, 0));
		BuildSite site = sites.create(land.owner().getUUID(), "Owner", StarterBlueprints.ROYAL_HALL_2.id(), by);
		Leftovers.after(helper, () -> sites.remove(site.id()));
		helper.assertTrue(cap(land) == 256, "a Royal Hall II under construction raised the cap to " + cap(land));
		List<String> lore = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(RealmTreasury.TREASURY));
		helper.assertTrue(lore.stream().noneMatch(l -> l.startsWith("The Royal Hall by the capital")), "the treasury claims a Royal Hall: " + lore);
		helper.succeed();
	}

	/** Only the capital's hall counts: a Royal Hall 25 blocks from a member village's hall (34 from the capital) gives nothing, nor one in another dimension at the capital's coordinates. */
	//$ gametest_ticks_batch AREA '100' '"qaRoyalHallWrongPlace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRoyalHallWrongPlace")
	public void aRoyalHallByAMemberOrInAnotherDimensionGivesNothing(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.finished(helper);
		ServerLevel level = land.level();
		BuildSiteManager sites = BuildSiteManager.get(level);
		helper.assertTrue(land.hall(1).distSqr(land.hall(0)) == 81, "the member's hall isn't 9 east: " + land.hall(1) + " " + land.hall(0));
		BlueprintData.Placement member = at(level.dimension().location(), land.hall(1).offset(25, 0, 0));
		sites.recordFinished(StarterBlueprints.ROYAL_HALL_2.id(), member, land.owner().getUUID());
		helper.assertTrue(cap(land) == 256, "a Royal Hall II by the member village counted: " + cap(land));
		sites.forgetFinished(member);
		BlueprintData.Placement nether = at(Level.NETHER.location(), land.hall(0).offset(2, 0, 2));
		sites.recordFinished(StarterBlueprints.ROYAL_HALL_2.id(), nether, land.owner().getUUID());
		boolean counted = cap(land) != 256;
		sites.forgetFinished(nether);
		helper.assertTrue(!counted, "a Royal Hall II in the Nether counted");
		helper.succeed();
	}

	/** Both tiers in every style are a hall on the village map. */
	//$ gametest_ticks_batch AREA '100' '"qaRoyalHallMap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRoyalHallMap")
	public void everyStyleOfRoyalHallIsAHallOnTheMap(GameTestHelper helper) {
		int n = 0;
		for (BlueprintStyles.Style style : BlueprintStyles.all()) {
			for (ResourceLocation base : List.of(StarterBlueprints.ROYAL_HALL.id(), StarterBlueprints.ROYAL_HALL_2.id())) {
				ResourceLocation id = BlueprintStyles.styled(base, style.name());
				helper.assertTrue(VillageMaps.kindOf(id).orElse(null) == VillageMaps.Kind.HALL, id + " isn't a hall on the map");
				n++;
			}
		}
		helper.assertTrue(n >= 8, "fewer styles than expected: " + n);
		helper.succeed();
	}

	// ---- text ----

	private static final Pattern BRITISH = Pattern.compile("(?i)\\b(colour|armour|defence|honour|favour|cancelled|grey|centre|jeweller|travelled|neighbour|realise|organise)");

	/** The blessing and Royal Hall lines render with their arguments and are American English. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmLegendsText"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmLegendsText")
	public void theBlessingAndRoyalHallLinesReadRight(GameTestHelper helper) {
		String colony = Component.translatable("chronicle.aliveworkplace.colony.founded_blessed", 12, "Thornholm", "Tomas and Dara").getString();
		helper.assertTrue(colony.equals("Founded on day 12 with the Founder's blessing, by settlers from Thornholm: Tomas and Dara"), "the colony's line: " + colony);
		String mother = Component.translatable("chronicle.aliveworkplace.colony.mother_blessed", "Tomas and Dara", "Newbrook", "to the east").getString();
		helper.assertTrue(mother.startsWith("Our settlers Tomas and Dara founded Newbrook") && mother.endsWith("with the Founder's blessing"), "the mother's line: " + mother);
		String about = Component.translatable("screen.aliveworkplace.realm.royal_hall.about", 32).getString();
		helper.assertTrue(about.contains("within 32 blocks") && about.contains("three times"), "the Royal Hall's about line: " + about);
		for (String key : List.of("chronicle.aliveworkplace.colony.founded_blessed", "chronicle.aliveworkplace.colony.mother_blessed",
			"screen.aliveworkplace.realm.royal_hall.about", "screen.aliveworkplace.realm.royal_hall.none", "screen.aliveworkplace.realm.royal_hall.has_1",
			"screen.aliveworkplace.realm.royal_hall.has_2", "screen.aliveworkplace.realm.royal_hall.how", "screen.aliveworkplace.realm.royal_hall.capital",
			"message.aliveworkplace.realm.royal_hall.drawn", "message.aliveworkplace.realm.royal_hall.needs_blank", "message.aliveworkplace.realm.royal_hall.built",
			"screen.aliveworkplace.realm.treasury.royal_hall", "legend.aliveworkplace.merchant_prince.title", "legend.aliveworkplace.founder.title")) {
			String text = Component.translatable(key, "A", "B", "C").getString();
			helper.assertTrue(!text.equals(key), "untranslated: " + key);
			helper.assertTrue(!BRITISH.matcher(text).find(), "British spelling in " + key + ": " + text);
		}
		helper.succeed();
	}
}
