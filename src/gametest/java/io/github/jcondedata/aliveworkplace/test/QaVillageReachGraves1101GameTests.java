package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.grave.Graveyards;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA, 2026-11-01: independent checks of six landed fixes, written from each bug's Expected text.
 * <ul>
 * <li>B212: a caravan's goods turn for home when the hall it was going to is taken away; a hall later set on the same
 * block isn't handed them; the turned caravan survives a save.</li>
 * <li>B222: graves go to the village's Graveyard in rows; old (switch-off) graves get moved; trades and partner are kept
 * through the move and the revival; a grave in a neighbouring village isn't this village's.</li>
 * <li>B550/B557 (36.20): a worker's block in a City Plan zone cell painted past the hall's radius is in the village at
 * any height; an unpainted cell just past it isn't; the refusal names the right village.</li>
 * <li>B562: a death inside the village (circle and HEIGHT) is mourned and dated in the Book of Families through a real
 * death; one a block past the circle or HEIGHT isn't; two villages side by side, only the right one.</li>
 * <li>B582: a trade-pact partner can be offered an alliance from either side on the Pacts tab, the pact keeps working
 * while it waits, the offer survives a save, decline keeps the pact, and alliances off hides the button.</li>
 * </ul>
 */
public class QaVillageReachGraves1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- B212: caravans ---------------------------------------------------------------------------------------------

	private static final BlockPos HALL_A = new BlockPos(3, 2, 3);
	private static final BlockPos CHEST_A = new BlockPos(5, 2, 5);
	private static final BlockPos HALL_B = new BlockPos(18, 2, 18);
	private static final BlockPos CHEST_B = new BlockPos(16, 2, 20);

	/** Two villages (A Thornholm, B Ashford, waiting for 32 oak logs), each with a Storehouse chest; no markets; instant caravans. */
	private static Caravans.Data caravans(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		int radius = VillageHalls.RADIUS;
		long travel = Caravans.MIN_TRAVEL;
		boolean economy = io.github.jcondedata.aliveworkplace.trade.Economy.ENABLED;
		VillageHalls.RADIUS = 6;
		Caravans.MIN_TRAVEL = 0;
		io.github.jcondedata.aliveworkplace.trade.Economy.ENABLED = true;
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Caravans.MIN_TRAVEL = travel;
			io.github.jcondedata.aliveworkplace.trade.Economy.ENABLED = economy;
			data.remove(a);
			data.remove(b);
			data.clearRoad(a);
			data.clearRoad(b);
		});
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(5, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_A, Blocks.CHEST);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(16, 2, 18), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		return data;
	}

	private static void listVillages(GameTestHelper helper, Caravans.Data data) {
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL_A)).setCustomName(Component.literal("Thornholm"));
		((VillageHallBlockEntity) helper.getBlockEntity(HALL_B)).setCustomName(Component.literal("Ashford"));
		data.setWants(a, Component.literal("Thornholm"), List.of());
		data.setWants(b, Component.literal("Ashford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		data.setMarket(a, Market.EMPTY);
		data.setMarket(b, Market.EMPTY);
	}

	private static List<Caravans.Shipment> road(GameTestHelper helper, Caravans.Data data) {
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		return data.onTheRoad().stream().filter(s -> s.to().equals(a) || s.to().equals(b) || s.from().equals(a) || s.from().equals(b)).toList();
	}

	private static int logs(List<ItemStack> goods) {
		return goods.stream().filter(s -> s.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
	}

	/**
	 * B212: the wanted logs on their way to Ashford turn for home when Ashford's hall is broken; the turned caravan is
	 * still on the road (home-bound, 32 logs) after a save and load; a new hall set on Ashford's block, waiting for the
	 * same logs, isn't handed them in its round; Thornholm's next round brings all 48 logs home, and its chronicle says so.
	 */
	//$ gametest_ticks_batch BIG '200' '"qa1101CaravanTurnedReload"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qa1101CaravanTurnedReload")
	public void wantedGoodsTurnedHomeSurviveASaveAndANewHallOnTheBlockGetsNone(GameTestHelper helper) {
		Caravans.Data data = caravans(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		helper.runAfterDelay(2, () -> {
			listVillages(helper, data);
			Container ours = helper.getBlockEntity(CHEST_A);
			Container theirs = helper.getBlockEntity(CHEST_B);
			ours.setItem(0, new ItemStack(Items.OAK_LOG, 48));
			helper.assertTrue(data.toggleRoute(a, b), "setup: no route");
			Caravans.round(level, a, null);
			helper.assertTrue(ours.countItem(Items.OAK_LOG) == 16, "setup: the caravan didn't load 32 logs: " + ours.countItem(Items.OAK_LOG));
			helper.assertTrue(road(helper, data).size() == 1 && road(helper, data).get(0).to().equals(b), "setup: no caravan to Ashford: " + road(helper, data));

			helper.setBlock(HALL_B, Blocks.AIR); // the player breaks Ashford's hall
			List<Caravans.Shipment> turned = road(helper, data);
			helper.assertTrue(turned.size() == 1 && turned.get(0).to().equals(a) && turned.get(0).back() && logs(turned.get(0).goods()) == 32,
				"the wanted logs should turn for Thornholm: " + turned);

			// a save and load (server restart) mid-way home
			CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
			Caravans.Data loaded = Caravans.Data.load(saved, level.registryAccess());
			List<Caravans.Shipment> after = loaded.onTheRoad().stream().filter(s -> s.to().equals(a) || s.to().equals(b)).toList();
			helper.assertTrue(after.size() == 1 && after.get(0).to().equals(a) && after.get(0).back() && logs(after.get(0).goods()) == 32,
				"after a save and load the caravan home is gone or changed: " + after);

			// a new hall on the same block, waiting for the same logs: its round isn't handed them
			helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
			data.setWants(b, Component.literal("Newford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
			data.setMarket(b, Market.EMPTY);
		});
		// its round comes when the caravan would long have arrived (caravans are instant here)
		helper.runAfterDelay(40, () -> {
			Container theirs = helper.getBlockEntity(CHEST_B);
			data.hurry(b);
			Caravans.round(level, b, null);
			helper.assertTrue(theirs.countItem(Items.OAK_LOG) == 0, "the new hall on Ashford's block was handed " + theirs.countItem(Items.OAK_LOG) + " of the old logs");
			helper.assertTrue(road(helper, data).stream().anyMatch(s -> s.to().equals(a) && s.back() && logs(s.goods()) == 32),
				"the caravan home to Thornholm was lost in the new hall's round: " + road(helper, data));
		});
		helper.runAfterDelay(42, () -> {
			Container ours = helper.getBlockEntity(CHEST_A);
			Caravans.round(level, a, null);
			helper.assertTrue(ours.countItem(Items.OAK_LOG) == 48, "the logs aren't all home: " + ours.countItem(Items.OAK_LOG));
			helper.assertTrue(road(helper, data).stream().noneMatch(s -> logs(s.goods()) > 0), "logs still on the road: " + road(helper, data));
			List<String> chronicle = ((VillageHallBlockEntity) helper.getBlockEntity(HALL_A)).chronicle().stream().map(e -> e.text().getString()).toList();
			helper.assertTrue(chronicle.stream().anyMatch(l -> l.startsWith("Our caravan came home from ") && l.contains("32 × Oak Log")),
				"Thornholm's chronicle doesn't say the caravan came home: " + chronicle);
			helper.succeed();
		});
	}

	// ---- B222: graveyards ---------------------------------------------------------------------------------------------

	static final BlockPos G_HALL = new BlockPos(3, 2, 3);
	static final BlockPos G_YARD = new BlockPos(3, 2, 14);
	private static final BlockPos G_TABLE = new BlockPos(8, 2, 8);
	private static final BlockPos G_CHEST = new BlockPos(10, 2, 8);

	/** A village (radius 12) with the Graveyard at G_YARD and the switch as {@code on}; everything put back after. */
	static BlueprintData.Placement graveVillage(GameTestHelper helper, boolean on) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.graves(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean was = Graveyards.ENABLED;
		VillageHalls.RADIUS = 12;
		VillageHalls.forgetNearest();
		Graveyards.ENABLED = on;
		helper.setDayTime(2000);
		helper.setBlock(G_HALL, ModBlocks.VILLAGE_HALL);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(G_YARD), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GRAVEYARD.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			Graveyards.ENABLED = was;
			BuildSiteManager.get(level).forgetFinished(placement);
		});
		return placement;
	}

	static Villager undertaker(GameTestHelper helper) {
		helper.setBlock(G_TABLE, ModBlocks.UNDERTAKERS_TABLE);
		helper.setBlock(G_CHEST, Blocks.CHEST);
		Villager undertaker = helper.spawn(EntityType.VILLAGER, G_TABLE.east());
		Jobs.employ(helper.getLevel(), undertaker, helper.absolutePos(G_TABLE), ModVillagers.UNDERTAKERS_TABLE_POI, ModVillagers.UNDERTAKER);
		return undertaker;
	}

	/** A named Journeyman Builder dying at the relative {@code at}: the grave left there. */
	static BlockPos dies(GameTestHelper helper, BlockPos at, String name, int bench) {
		ServerLevel level = helper.getLevel();
		BlockPos benchAt = new BlockPos(1 + bench, 2, 12);
		helper.setBlock(benchAt, ModBlocks.BUILDERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(level, v, helper.absolutePos(benchAt), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		v.setVillagerData(v.getVillagerData().setLevel(3));
		v.setVillagerXp(80);
		v.setCustomName(Component.literal(name));
		v.kill();
		List<BlockPos> graves = Graves.near(level, helper.absolutePos(at), 3);
		helper.assertTrue(graves.size() == 1, "setup: " + name + " left " + graves.size() + " graves");
		return graves.get(0);
	}

	/** The Graveyard's row slots, in the order filled: its own (1, 0, 6), (2, 0, 6), ... */
	static BlockPos slot(GameTestHelper helper, int x) {
		return helper.absolutePos(G_YARD.offset(x, 0, 6));
	}

	static String nameAt(ServerLevel level, BlockPos p) {
		return level.getBlockEntity(p) instanceof GraveBlockEntity g ? g.name().getString() : null;
	}

	/**
	 * B222 ("old saves' scattered graves get moved too", "switch off leaves graves where they fall", "in rows"): two
	 * villagers die in the streets with gravesToGraveyard off and a third inside the Graveyard's walls; for 300 ticks the
	 * Undertaker leaves them all. With the switch on (as an old save's graves meet the new version), the two street graves
	 * go to the first and second row slots, each its own; the one already in the Graveyard stays; three graves in all,
	 * still so 400 ticks later (graves at rest aren't moved again).
	 */
	//$ gametest_ticks_batch HUGE '4400' '"qa1101GravesOldRows"'
	@GameTest(template = HUGE, timeoutTicks = 4400, batch = "qa1101GravesOldRows")
	public void gravesLeftWithTheSwitchOffAreMovedLaterIntoTheRowsInOrder(GameTestHelper helper) {
		graveVillage(helper, false);
		ServerLevel level = helper.getLevel();
		BlockPos ada = dies(helper, new BlockPos(4, 2, 9), "Ada", 0);
		BlockPos bo = dies(helper, new BlockPos(13, 2, 4), "Bo", 2);
		BlockPos inYard = dies(helper, G_YARD.offset(6, 0, 9), "Cy", 4);
		undertaker(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(level.getBlockState(ada).is(ModBlocks.GRAVE) && level.getBlockState(bo).is(ModBlocks.GRAVE),
				"with the switch off a street grave was moved");
			helper.assertTrue(!level.getBlockState(slot(helper, 1)).is(ModBlocks.GRAVE), "with the switch off a grave reached the Graveyard");
			Graveyards.ENABLED = true;
		});
		int[] stable = {0};
		helper.succeedWhen(() -> {
			helper.assertTrue(Graveyards.ENABLED, "the switch is still off");
			try {
				helper.assertTrue(!level.getBlockState(ada).is(ModBlocks.GRAVE) && !level.getBlockState(bo).is(ModBlocks.GRAVE), "a street grave is still in the street");
				Set<String> rows = new java.util.HashSet<>();
				rows.add(nameAt(level, slot(helper, 1)));
				rows.add(nameAt(level, slot(helper, 2)));
				helper.assertTrue(rows.equals(Set.of("Ada", "Bo")), "the first two row slots hold " + rows + ", not Ada and Bo");
				helper.assertTrue("Cy".equals(nameAt(level, inYard)), "the grave already inside the Graveyard was moved");
				int all = Graves.near(level, helper.absolutePos(new BlockPos(15, 2, 15)), 30).size();
				helper.assertTrue(all == 3, all + " graves, not 3");
			} catch (RuntimeException e) {
				stable[0] = 0;
				throw e;
			}
			// and it stays so: graves at rest aren't moved again
			helper.assertTrue(++stable[0] >= 400, "settled for " + stable[0] + " ticks");
		});
	}

	/**
	 * B222 ("keep the grave's saved villager (name, job, level, trades, partner) intact", "revival still works from
	 * there"): Mira, with a trade and a partner, dies in the street; laid in the Graveyard's first slot, then a golden apple
	 * brings her back: her trade, her partner, her job and level as they were; a player nearby reads "Mira is back on
	 * their feet!".
	 */
	//$ gametest_ticks_batch HUGE '3600' '"qa1101GraveTradesPartner"'
	@GameTest(template = HUGE, timeoutTicks = 3600, batch = "qa1101GraveTradesPartner")
	public void aGraveMovedToTheGraveyardKeepsTradesAndPartnerThroughRevival(GameTestHelper helper) {
		graveVillage(helper, true);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		BlockPos benchAt = new BlockPos(1, 2, 12);
		helper.setBlock(benchAt, ModBlocks.BUILDERS_BENCH);
		Villager mira = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 4));
		Jobs.employ(level, mira, helper.absolutePos(benchAt), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		mira.setVillagerData(mira.getVillagerData().setLevel(3));
		mira.setVillagerXp(80);
		mira.setCustomName(Component.literal("Mira"));
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 7), new ItemStack(Items.SPYGLASS), 12, 1, 0.05f));
		mira.setOffers(offers);
		UUID tom = UUID.randomUUID(); // her partner, away (not loaded)
		ModAttachments.PARTNER.set(mira, new Couples.Partner(tom, Component.literal("Tom"), 5, true));
		UUID id = mira.getUUID();
		mira.kill();
		List<BlockPos> graves = Graves.near(level, helper.absolutePos(new BlockPos(13, 2, 4)), 3);
		helper.assertTrue(graves.size() == 1, "setup: " + graves.size() + " graves");
		BlockPos street = graves.get(0);
		undertaker(helper);
		List<Component> heard = new ArrayList<>();
		QaClassMigrationGameTests.listener(helper, heard, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10, 2, 10))), "qa-graveside");
		Container chest = helper.getBlockEntity(G_CHEST);
		boolean[] laid = {false};
		helper.succeedWhen(() -> {
			if (!laid[0]) {
				helper.assertTrue(!level.getBlockState(street).is(ModBlocks.GRAVE) && "Mira".equals(nameAt(level, slot(helper, 1))), "Mira isn't in the first row slot yet");
				laid[0] = true;
				chest.setItem(0, new ItemStack(Items.GOLDEN_APPLE));
			}
			Villager back = level.getEntity(id) instanceof Villager v && v.isAlive() ? v : null;
			helper.assertTrue(back != null, "Mira isn't back yet");
			helper.assertTrue(back.getVillagerData().getProfession() == ModVillagers.BUILDER && back.getVillagerData().getLevel() == 3, "back as " + back.getVillagerData());
			helper.assertTrue(!back.getOffers().isEmpty() && back.getOffers().get(0).getResult().is(Items.SPYGLASS)
				&& back.getOffers().get(0).getBaseCostA().getCount() == 7, "her trade is lost: " + back.getOffers().stream().map(o -> o.getResult().toString()).toList());
			Couples.Partner p = ModAttachments.PARTNER.get(back);
			helper.assertTrue(p != null && p.id().equals(tom) && p.married(), "her partner is lost: " + p);
			List<String> lines = heard.stream().map(Component::getString).toList();
			helper.assertTrue(lines.contains("Mira is back on their feet!"), "the player read: " + lines);
		});
	}

	// ---- B550/B557: work blocks in the plan's corner zones ------------------------------------------------------------

	/** A hall at {@code hall} (relative) with radius {@code radius}, field reach 16, protection off; put back after. */
	private static BlockPos planVillage(GameTestHelper helper, BlockPos hall, int radius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.players(helper);
		boolean protection = VillageProtection.ENABLED;
		int was = VillageHalls.RADIUS;
		int reach = Fields.MAX_DISTANCE;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = was;
			Fields.MAX_DISTANCE = reach;
			VillageHalls.forgetNearest();
		});
		VillageProtection.ENABLED = false;
		VillageHalls.RADIUS = radius;
		Fields.MAX_DISTANCE = 16;
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		return helper.absolutePos(hall);
	}

	/** Paints the cells at the given offsets from the hall into a Homes zone. */
	private static void paint(GameTestHelper helper, BlockPos relHall, BlockPos hall, List<int[]> offsets) {
		BitSet cells = new BitSet();
		for (int[] d : offsets) {
			int cell = CityPlan.cellAt(hall, hall.offset(d[0], 0, d[1]));
			helper.assertTrue(cell >= 0, "setup: the cell " + d[0] + "," + d[1] + " isn't on the plan's grid");
			cells.set(cell);
		}
		VillageHallBlockEntity entity = helper.getBlockEntity(relHall);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
	}

	/** A farmer at a composter at the relative {@code composterAt}, handed a 5 x 5 field at {@code centre}: empty if taken, else what he said. */
	private static List<String> fieldOffered(GameTestHelper helper, BlockPos composterAt, BlockPos centre) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(composterAt, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, composterAt.above());
		farmer.setNoAi(true);
		Jobs.employ(level, farmer, helper.absolutePos(composterAt), PoiTypes.FARMER, VillagerProfession.FARMER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(centre);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2))));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(p, farmer, seen);
		boolean taken = Fields.hasField(farmer);
		ModAttachments.FARM_FIELD.remove(farmer);
		farmer.discard();
		helper.setBlock(composterAt, Blocks.AIR);
		return taken ? List.of() : seen.stream().map(Component::getString).toList();
	}

	/**
	 * B550/B557 at the grid's very edge and up a hill: a composter on a 12-block pillar in the painted north-west corner
	 * cell (-16, -16 from a 12-block village's hall: the first cell of the grid, 22.6 blocks off) still works in the
	 * village: a field across it, 30 blocks off, is taken. (Reach: up and down never count, any height.)
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1101ZoneCornerHigh"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1101ZoneCornerHigh")
	public void aComposterUpAHillInTheGridsFirstCornerCellReachesAcrossTheVillage(GameTestHelper helper) {
		BlockPos rel = new BlockPos(17, 2, 17);
		BlockPos hall = planVillage(helper, rel, 12);
		helper.assertTrue(CityPlan.half() == 16, "setup: the grid reaches " + CityPlan.half());
		paint(helper, rel, hall, List.of(new int[] {-16, -16}, new int[] {-15, -16}, new int[] {-16, -15}));
		BlockPos composter = rel.offset(-16, 12, -16);
		for (int y = 2; y < composter.getY(); y++) {
			helper.setBlock(new BlockPos(composter.getX(), y, composter.getZ()), Blocks.STONE);
		}
		helper.assertTrue(Reach.claims(helper.getLevel(), hall, helper.absolutePos(composter)) && !VillageHalls.contains(hall, helper.absolutePos(composter)),
			"setup: the corner cell isn't the village's zone outside its circle");
		BlockPos field = rel.offset(5, 0, 5);
		List<String> refused = fieldOffered(helper, composter, field);
		helper.assertTrue(refused.isEmpty(), "a composter up a hill in a painted corner cell was refused a field across the village: " + refused);
		helper.succeed();
	}

	/**
	 * B550/B557, the other side of the boundary: the zone is painted at the south-east corner, and the composter stands in
	 * the unpainted cell just west of it (outside the circle, on the grid). That block is in no village, so a field across
	 * the village past the long reach is refused, in the plain sentence (no village named), with the distance rounded up.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1101ZoneCornerUnpainted"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1101ZoneCornerUnpainted")
	public void aComposterInTheUnpaintedCellBesideAZoneIsInNoVillage(GameTestHelper helper) {
		BlockPos rel = new BlockPos(15, 2, 15);
		BlockPos hall = planVillage(helper, rel, 12);
		paint(helper, rel, hall, List.of(new int[] {13, 13}, new int[] {14, 13}, new int[] {13, 14}, new int[] {14, 14}));
		BlockPos composter = rel.offset(12, 0, 13);
		helper.assertTrue(!Reach.claims(helper.getLevel(), hall, helper.absolutePos(composter)) && CityPlan.cellAt(hall, helper.absolutePos(composter)) >= 0,
			"setup: the composter's cell is painted or off the grid");
		helper.assertTrue(Reach.village(helper.getLevel(), helper.absolutePos(composter)).isEmpty(), "an unpainted cell outside the circle is some village's");
		BlockPos field = rel.offset(-8, 0, -2);
		int off = (int) Math.ceil(Reach.across(helper.absolutePos(composter), helper.absolutePos(field)));
		List<String> refused = fieldOffered(helper, composter, field);
		String expected = "That field is " + off + " blocks from the Composter the farmer works at — keep it within 16.";
		helper.assertTrue(refused.contains(expected), "expected \"" + expected + "\", the player read " + refused);
		helper.succeed();
	}

	/**
	 * B550/B557 with two villages side by side (radius 6, grid ±16): a composter in a cell painted in village A's
	 * north-west corner works in A: a field in A's circle 18 blocks off is taken; a field in village B's circle, outside
	 * A, 29 blocks off, is refused with the sentence that names A ("That field is outside Oakvale and 30 blocks ...").
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1101ZoneCornerTwo"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1101ZoneCornerTwo")
	public void aCornerZoneComposterWorksInItsOwnVillageNotTheNext(GameTestHelper helper) {
		BlockPos relA = new BlockPos(12, 2, 12);
		BlockPos hallA = planVillage(helper, relA, 6);
		BlockPos relB = new BlockPos(30, 2, 12);
		helper.setBlock(relB, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		((VillageHallBlockEntity) helper.getBlockEntity(relA)).setCustomName(Component.literal("Oakvale"));
		((VillageHallBlockEntity) helper.getBlockEntity(relB)).setCustomName(Component.literal("Brookby"));
		paint(helper, relA, hallA, List.of(new int[] {-11, -11}, new int[] {-12, -11}, new int[] {-11, -12}));
		BlockPos composter = relA.offset(-11, 0, -11);
		helper.assertTrue(Reach.village(helper.getLevel(), helper.absolutePos(composter)).equals(Optional.of(hallA)), "the corner composter isn't in Oakvale");
		BlockPos inA = relA.offset(2, 0, 2);
		List<String> a = fieldOffered(helper, composter, inA);
		helper.assertTrue(a.isEmpty(), "a field in Oakvale was refused to its corner-zone farmer: " + a);
		BlockPos inB = relB.offset(-2, 0, 0);
		helper.assertTrue(VillageHalls.contains(helper.absolutePos(relB), helper.absolutePos(inB)) && !Reach.claims(helper.getLevel(), hallA, helper.absolutePos(inB)),
			"setup: the second field isn't Brookby's only");
		int off = (int) Math.ceil(Reach.across(helper.absolutePos(composter), helper.absolutePos(inB)));
		List<String> b = fieldOffered(helper, composter, inB);
		String expected = "That field is outside Oakvale and " + off + " blocks from the Composter the farmer works at — keep it within 16.";
		helper.assertTrue(b.contains(expected), "expected \"" + expected + "\", the player read " + b);
		helper.succeed();
	}

	// ---- B562: a death inside the village is the village's -------------------------------------------------------------

	static final BlockPos M_HALL = new BlockPos(3, 2, 15);

	/** A 16-block village with mourning on; switches put back after. */
	static BlockPos mourningVillage(GameTestHelper helper, BlockPos rel, int radius) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.graves(helper);
		int was = VillageHalls.RADIUS;
		boolean mourning = Mourning.ENABLED;
		boolean book = FamilyBook.ENABLED;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = was;
			Mourning.ENABLED = mourning;
			FamilyBook.ENABLED = book;
			VillageNeeds.forget();
			Moods.forget();
			VillageHalls.forgetNearest();
		});
		Mourning.ENABLED = true;
		FamilyBook.ENABLED = true;
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(rel);
	}

	static void ready(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		entity.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		VillageHalls.forgetNearest();
	}

	static Villager farmerAt(GameTestHelper helper, BlockPos absolute, String name) {
		Villager v = QaVillageCircleGameTests.worker(helper, absolute, name, VillagerProfession.FARMER);
		return v;
	}

	static Villager sonOf(GameTestHelper helper, BlockPos absolute, String name, String mother) {
		Villager son = QaVillageCircleGameTests.worker(helper, absolute, name, VillagerProfession.FLETCHER);
		ModAttachments.PARENTS.set(son, new Families.Parents(Component.literal(mother), Component.literal("Tom"), "", "", true));
		return son;
	}

	private static boolean datedDead(ServerLevel level, BlockPos hall, UUID id, long day) {
		FamilyBook.Book book = FamilyBook.book(level, hall);
		FamilyBook.Person p = book == null ? null : book.get(id);
		return p != null && p.died() == day;
	}

	/**
	 * B562 through a real death: Mira, a Farmer, is killed on the village's rim up a hill (14 out and 10 up in a 16-block
	 * village: inside its circle and HEIGHT, 17.2 blocks off in a straight line), standing on a stone pillar. Her son by
	 * the hall grieves, a player by the hall reads "Mira, the village's Farmer, has died at an age nobody had counted. The
	 * village mourns.", and the hall's Book of Families dates her death today.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101RimDeathKilled"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101RimDeathKilled")
	public void aVillagerKilledOnTheRimUpAHillIsMournedAndDated(GameTestHelper helper) {
		BlockPos h = mourningVillage(helper, M_HALL, 16);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper, h);
			long today = Chronicle.day(level);
			BlockPos rim = h.offset(14, 10, 0);
			for (int y = h.getY(); y < rim.getY(); y++) {
				level.setBlockAndUpdate(new BlockPos(rim.getX(), y, rim.getZ()), Blocks.STONE.defaultBlockState());
			}
			QaVillageCircleGameTests.staged(helper, h, List.of(rim), List.of());
			helper.assertTrue(rim.distSqr(h) > 16.0 * 16.0, "setup: the rim is within RADIUS in a straight line");
			Villager mira = farmerAt(helper, rim, "Mira");
			Villager pip = sonOf(helper, h.offset(3, 0, 3), "Pip", "Mira");
			List<Component> heard = new ArrayList<>();
			QaClassMigrationGameTests.listener(helper, heard, Vec3.atBottomCenterOf(h.offset(2, 0, -2)), "qa-mourner");
			UUID id = mira.getUUID();
			mira.kill();
			List<String> wrong = new ArrayList<>();
			if (!Mourning.grieving(pip, today)) {
				wrong.add("her son by the hall doesn't grieve");
			}
			List<String> lines = heard.stream().map(Component::getString).toList();
			if (!lines.contains("Mira, the village's Farmer, has died at an age nobody had counted. The village mourns.")) {
				wrong.add("the player by the hall read " + lines);
			}
			if (!datedDead(level, h, id, today)) {
				wrong.add("the Book of Families doesn't date her death today: " + FamilyBook.book(level, h).get(id));
			}
			pip.discard();
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}

	/**
	 * B562, both sides of each edge through real deaths: a death exactly RADIUS out (flat) and one HEIGHT above the hall
	 * (hanging) are mourned and dated; one a block past RADIUS, and one a block above HEIGHT, aren't (their sons by the
	 * hall don't grieve, the book doesn't date them).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101DeathEdges"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101DeathEdges")
	public void deathsOnTheEdgeAreTheVillagesAndOnePastIsnt(GameTestHelper helper) {
		BlockPos h = mourningVillage(helper, M_HALL, 16);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper, h);
			long today = Chronicle.day(level);
			int top = QaVillageCircleGameTests.height(h);
			List<BlockPos> in = List.of(h.offset(16, 0, 0), h.offset(1, top, 1));
			List<BlockPos> out = List.of(h.offset(17, 0, 0), h.offset(-1, top + 1, -1));
			QaVillageCircleGameTests.staged(helper, h, in, out);
			List<String> wrong = new ArrayList<>();
			String[] names = {"Ann", "Bea", "Cat", "Dot"};
			List<BlockPos> all = new ArrayList<>(in);
			all.addAll(out);
			for (int i = 0; i < 4; i++) {
				Villager dead = farmerAt(helper, all.get(i), names[i]);
				Villager son = sonOf(helper, h.offset(2, 0, 2 + i), "Son of " + names[i], names[i]);
				UUID id = dead.getUUID();
				dead.kill();
				boolean mourned = Mourning.grieving(son, today);
				boolean dated = datedDead(level, h, id, today);
				boolean shouldBe = i < 2;
				if (mourned != shouldBe || dated != shouldBe) {
					wrong.add(names[i] + " at " + all.get(i).subtract(h) + (shouldBe ? " (in the village)" : " (outside it)") + ": mourned " + mourned + ", dated " + dated);
				}
				son.discard();
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}

	/**
	 * B562 with two villages side by side (radius 10, 22 apart): Mira dies on village A's rim up a hill. A's book dates
	 * her and her son in A grieves; her son standing in B doesn't, B's book doesn't list her death, and a player in B
	 * isn't told (a player in A is).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101DeathTwoVillages"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101DeathTwoVillages")
	public void aDeathOnOneVillagesRimIsThatVillagesOnly(GameTestHelper helper) {
		BlockPos a = mourningVillage(helper, new BlockPos(2, 2, 14), 10);
		helper.setBlock(new BlockPos(24, 2, 14), ModBlocks.VILLAGE_HALL);
		BlockPos b = helper.absolutePos(new BlockPos(24, 2, 14));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper, a);
			ready(helper, b);
			long today = Chronicle.day(level);
			BlockPos rim = a.offset(8, 8, 0);
			for (int y = a.getY(); y < rim.getY(); y++) {
				level.setBlockAndUpdate(new BlockPos(rim.getX(), y, rim.getZ()), Blocks.STONE.defaultBlockState());
			}
			QaVillageCircleGameTests.staged(helper, a, List.of(rim), List.of());
			helper.assertTrue(!VillageHalls.contains(b, rim) && rim.distSqr(a) > 100, "setup: the rim isn't A's only, up a hill");
			Villager mira = farmerAt(helper, rim, "Mira");
			Villager inA = sonOf(helper, a.offset(2, 0, 2), "Pip", "Mira");
			Villager inB = sonOf(helper, b.offset(-2, 0, 0), "Rob", "Mira");
			helper.assertTrue(VillageHalls.contains(b, inB.blockPosition()) && !VillageHalls.contains(a, inB.blockPosition()), "setup: Rob isn't in B only");
			List<Component> heardA = new ArrayList<>();
			List<Component> heardB = new ArrayList<>();
			QaClassMigrationGameTests.listener(helper, heardA, Vec3.atBottomCenterOf(a.offset(1, 0, 3)), "qa-in-a");
			QaClassMigrationGameTests.listener(helper, heardB, Vec3.atBottomCenterOf(b.offset(1, 0, 3)), "qa-in-b");
			UUID id = mira.getUUID();
			mira.kill();
			List<String> wrong = new ArrayList<>();
			if (!Mourning.grieving(inA, today)) {
				wrong.add("her son in A doesn't grieve");
			}
			if (Mourning.grieving(inB, today) || ModAttachments.GRIEF.has(inB)) {
				wrong.add("her son in B grieves");
			}
			if (!datedDead(level, a, id, today)) {
				wrong.add("A's book doesn't date her");
			}
			FamilyBook.Book bookB = FamilyBook.book(level, b);
			if (bookB != null && bookB.get(id) != null) {
				wrong.add("B's book lists her: " + bookB.get(id));
			}
			if (heardA.stream().noneMatch(c -> c.getString().contains("Mira, the village's Farmer"))) {
				wrong.add("the player in A wasn't told: " + heardA.stream().map(Component::getString).toList());
			}
			if (heardB.stream().anyMatch(c -> c.getString().contains("Mira"))) {
				wrong.add("the player in B was told: " + heardB.stream().map(Component::getString).toList());
			}
			inA.discard();
			inB.discard();
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}

	// ---- B582: the Pacts tab's alliance offer for a trade-pact partner ------------------------------------------------

	/**
	 * B582 from the other side, and the pact's working life while the offer waits: the neighbour (village 1) presses
	 * "Offer X an alliance" for the trade-pact partner it didn't start; it reads "A letter has gone to ... offering X an
	 * alliance. They have 3 days to answer."; the trade pact still lets caravans run and its route stays; the waiting offer
	 * survives a save and load; village 0's tab shows "Accept an alliance with Y" beside "Trade pact with Y" and no offer
	 * of its own; accepting there makes the pact an alliance from the same day, the route still there.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101PactAllianceOtherSide"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101PactAllianceOtherSide")
	public void thePartnerWhoDidntStartThePactCanOfferAnAllianceAndThePactRunsMeanwhile(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		QaPactsRealmEdictsGameTests.pact(land, false);
		Caravans.Data caravans = Caravans.Data.get(land.level());
		helper.assertTrue(caravans.toggleRoute(land.hall(1), land.hall(0)), "setup: no route from the neighbour");
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(menu, "Trade pact with " + land.name(0)) >= 0, "the neighbour's tab doesn't show the pact");
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(menu, "Offer " + land.name(0) + " a trade pact") < 0, "the neighbour is offered a second trade pact");
		int offer = QaPactsRealmEdictsGameTests.find(menu, "Offer " + land.name(0) + " an alliance");
		helper.assertTrue(offer >= 0, "the pact partner who didn't start the pact has no alliance offer on its Pacts tab");
		two.theirs().clear();
		menu.press(offer, two.them());
		String owner = "Owner"; // the name village 0 is owned under (RealmGameTests.land)
		String sent = "A letter has gone to " + owner + " offering " + land.name(0) + " an alliance. They have 3 days to answer.";
		helper.assertTrue(QaPactsRealmEdictsGameTests.said(two.theirs(), sent), "expected \"" + sent + "\", read " + two.theirs().stream().map(Component::getString).toList());
		helper.assertTrue(Pacts.to(server, land.at(0)).size() == 1 && Pacts.to(server, land.at(0)).get(0).alliance(), "no alliance offer waiting at village 0");
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(1), land.hall(0)) && caravans.routesFrom(land.hall(1)).contains(land.hall(0)),
			"while the alliance offer waits, the trade pact's caravans stopped");
		ChoiceMenu after = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(after, "Offer " + land.name(0) + " an alliance") < 0, "the sent offer is still offered");

		CompoundTag saved = Pacts.Data.get(server).save(new CompoundTag(), land.level().registryAccess());
		Pacts.Data loaded = Pacts.Data.load(saved, land.level().registryAccess());
		helper.assertTrue(loaded.offers().equals(Pacts.Data.get(server).offers()) && loaded.pacts().equals(Pacts.Data.get(server).pacts()),
			"the waiting offer or the pact changed through a save: " + loaded.offers() + " " + loaded.pacts());

		ChoiceMenu theirs = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		int accept = QaPactsRealmEdictsGameTests.find(theirs, "Accept an alliance with " + land.name(1));
		helper.assertTrue(accept >= 0 && QaPactsRealmEdictsGameTests.find(theirs, "Trade pact with " + land.name(1)) >= 0,
			"village 0's tab lacks the Accept or the pact");
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(theirs, "Offer " + land.name(1) + " an alliance") < 0,
			"village 0 is offered to send its own alliance offer while one from the partner waits");
		theirs.press(accept, land.owner());
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && pact.alliance() && pact.since() == 0, "not an alliance from the pact's day: " + pact);
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(1), land.hall(0)) && caravans.routesFrom(land.hall(1)).contains(land.hall(0)), "the route stopped on accepting");
		helper.succeed();
	}

	/**
	 * B582: a declined alliance offer leaves the trade pact as it was (routes allowed), and the tab offers the alliance
	 * again; with alliances switched off ({@code alliances}) the tab shows no alliance offer, and no second trade pact.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101PactAllianceDecline"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101PactAllianceDecline")
	public void aDeclinedAllianceKeepsTheTradePactAndAlliancesOffHidesTheOffer(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		QaPactsRealmEdictsGameTests.pact(land, false);
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		menu.press(QaPactsRealmEdictsGameTests.find(menu, "Offer " + land.name(1) + " an alliance"), land.owner());
		ChoiceMenu theirs = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
		int decline = QaPactsRealmEdictsGameTests.find(theirs, "Decline the offer from " + land.name(0));
		helper.assertTrue(decline >= 0, "no Decline for the alliance offer");
		theirs.press(decline, two.them());
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && !pact.alliance() && Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "declining changed the trade pact: " + pact);
		ChoiceMenu again = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(again, "Offer " + land.name(1) + " an alliance") >= 0, "after a decline the alliance can't be offered again");

		Pacts.ALLIANCES = false;
		ChoiceMenu off = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(off, "Offer " + land.name(1) + " an alliance") < 0, "with alliances off the tab still offers one");
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(off, "Offer " + land.name(1) + " a trade pact") < 0, "with alliances off a pact partner is offered a second trade pact");
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(off, "Trade pact with " + land.name(1)) >= 0, "with alliances off the pact isn't shown");
		helper.succeed();
	}
}
