package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.move.MoveResidents;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane c, 2026-11-11) for B1177, from the spec: "an inn is a Shop Counter worked by an Innkeeper (or an old Inn
 * Counter), the same way the innkeeper's job finds it; a Shopkeeper's Shop Counter is no inn". Edges the fix's own tests
 * leave out: the counter broken, the innkeeper asleep (an inn at night is still an inn), the innkeeper given No job or
 * retrained as a Shopkeeper at the same counter, two inns and an old Inn Counter at once (each counter once), the Seer's
 * look ahead ({@link LegendGuests#rollAhead}) and the inn's real morning ({@link Innkeepers#tend}) at a Shop Counter.
 */
public class QaInnShopCounter1111cGameTests implements FabricGameTest {
	static final String AREA = QaMoverResidents1110cGameTests.AREA;
	static final BlockPos INN = QaMoverResidents1110cGameTests.INN;
	static final BlockPos HALL = QaMoverResidents1110cGameTests.HALL;

	/** The move's setup with no Inn Counter, the inn's two beds and a Shop Counter at the inn spot (its POI a few ticks later). */
	static QaMoverResidents1110cGameTests.Setup shopInn(GameTestHelper helper, String name) {
		QaMoverResidents1110cGameTests.Setup s = QaMoverResidents1110cGameTests.setup(helper, name, false);
		QaMoverResidents1110cGameTests.bed(helper, QaMoverResidents1110cGameTests.INN_BED_1);
		QaMoverResidents1110cGameTests.bed(helper, QaMoverResidents1110cGameTests.INN_BED_2);
		helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
		return s;
	}

	static boolean innRolls(GameTestHelper helper) {
		return LegendGuests.rollsOn(helper.getLevel(), helper.absolutePos(HALL), null, "inn", 1);
	}

	static Optional<BlockPos> stayBed(GameTestHelper helper, QaMoverResidents1110cGameTests.Setup s) {
		QaMoverResidents1110cGameTests.confirm(helper, s);
		MoveSite site = QaMoverResidents1110cGameTests.start(helper.getLevel(), s);
		return MoveResidents.stayBed(helper.getLevel(), site);
	}

	static boolean atTheInn(GameTestHelper helper, Optional<BlockPos> bed) {
		return bed.isPresent() && (bed.get().equals(helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_1))
			|| bed.get().equals(helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_2)));
	}

	static boolean byTheHall(GameTestHelper helper, Optional<BlockPos> bed) {
		return bed.isPresent() && bed.get().equals(helper.absolutePos(QaMoverResidents1110cGameTests.HALL_BED));
	}

	/** The inn's counter broken: the village has no inn any more. No legend rolls there and residents stay by the Hall. */
	//$ gametest_batch AREA '"qaInn1111cBroken"'
	@GameTest(template = AREA, batch = "qaInn1111cBroken")
	public void aBrokenShopCounterIsNoInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = shopInn(helper, "qa1111cbroken");
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, s.hall());
			InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
			helper.assertTrue(innRolls(helper), "setup: the Innkeeper's Shop Counter isn't an inn");
			helper.destroyBlock(INN);
		});
		helper.runAfterDelay(5, () -> {
			helper.assertFalse(innRolls(helper), "the inn's Shop Counter was broken, yet a legend still rolls at the inn");
			helper.assertTrue(Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS).isEmpty(),
				"a broken counter is still an inn counter: " + Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS));
			Optional<BlockPos> bed = stayBed(helper, s);
			helper.assertTrue(byTheHall(helper, bed), "with the inn's counter broken the residents should stay by the Hall, got " + bed);
			helper.succeed();
		});
	}

	/** An inn at night is still an inn: the Innkeeper asleep in his bed still makes his Shop Counter the inn. */
	//$ gametest_batch AREA '"qaInn1111cAsleep"'
	@GameTest(template = AREA, batch = "qaInn1111cAsleep")
	public void anInnkeeperAsleepStillKeepsTheInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = shopInn(helper, "qa1111casleep");
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, s.hall());
			Villager keeper = InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
			BlockPos own = helper.absolutePos(QaMoverResidents1110cGameTests.OUT_BED);
			keeper.startSleeping(own);
			helper.assertTrue(keeper.isSleeping(), "setup: the Innkeeper isn't asleep");
			helper.assertTrue(innRolls(helper), "the Innkeeper is asleep in his bed and his Shop Counter no longer counts as the inn");
			Optional<BlockPos> bed = stayBed(helper, s);
			helper.assertTrue(atTheInn(helper, bed), "with the Innkeeper asleep the residents should still stay at the inn, got " + bed);
			helper.succeed();
		});
	}

	/** The Hall's "No job" for the Innkeeper: his Shop Counter is no inn any more. */
	//$ gametest_batch AREA '"qaInn1111cNoJob"'
	@GameTest(template = AREA, batch = "qaInn1111cNoJob")
	public void anInnkeeperGivenNoJobLeavesNoInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = shopInn(helper, "qa1111cnojob");
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, s.hall());
			Villager keeper = InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
			helper.assertTrue(innRolls(helper), "setup: the Innkeeper's Shop Counter isn't an inn");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Retraining.unemploy(level, player, keeper);
			helper.assertFalse(Innkeepers.isInnkeeper(keeper), "setup: still an Innkeeper after No job");
			helper.assertFalse(innRolls(helper), "the Innkeeper was given No job, yet a legend still rolls at his old counter");
			Optional<BlockPos> bed = stayBed(helper, s);
			helper.assertTrue(byTheHall(helper, bed), "with no Innkeeper left the residents should stay by the Hall, got " + bed);
			helper.succeed();
		});
	}

	/** The Innkeeper retrained as a Shopkeeper at his own counter: it's a shop now, no inn. */
	//$ gametest_batch AREA '"qaInn1111cRetrained"'
	@GameTest(template = AREA, batch = "qaInn1111cRetrained")
	public void anInnkeeperRetrainedAsAShopkeeperMakesTheCounterAShop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = shopInn(helper, "qa1111cretrain");
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, s.hall());
			Villager keeper = InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
			helper.assertTrue(innRolls(helper), "setup: the Innkeeper's Shop Counter isn't an inn");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Retraining.retrain(level, player, keeper, helper.absolutePos(INN), ModVillagers.SHOPKEEPER);
			helper.assertTrue(keeper.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER, "setup: not retrained: " + keeper.getVillagerData());
			helper.assertFalse(innRolls(helper), "the counter's Innkeeper became its Shopkeeper, yet a legend still rolls at the inn");
			Optional<BlockPos> bed = stayBed(helper, s);
			helper.assertTrue(byTheHall(helper, bed), "a shop is no inn: the residents should stay by the Hall, got " + bed);
			helper.succeed();
		});
	}

	/**
	 * Two inns (two Innkeepers at two Shop Counters) and an old Inn Counter with its own Innkeeper: three counters, each
	 * once. A Shopkeeper's counter beside them isn't one, and a Librarian at a lectern changes nothing.
	 */
	//$ gametest_batch AREA '"qaInn1111cMany"'
	@GameTest(template = AREA, batch = "qaInn1111cMany")
	public void eachInnCounterIsFoundOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos shopA = new BlockPos(25, 2, 3);
		BlockPos shopB = new BlockPos(21, 2, 3);
		BlockPos old = new BlockPos(28, 2, 3);
		BlockPos shop = new BlockPos(18, 2, 3);
		BlockPos lectern = new BlockPos(12, 2, 3);
		helper.setBlock(shopA, ModBlocks.SHOP_COUNTER);
		helper.setBlock(shopB, ModBlocks.SHOP_COUNTER);
		helper.setBlock(old, ModBlocks.INN_COUNTER);
		helper.setBlock(shop, ModBlocks.SHOP_COUNTER);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			helper.setBlock(old, ModBlocks.INN_COUNTER); // noOtherInns took every Inn Counter round the hall, ours too
		});
		helper.runAfterDelay(5, () -> {
			Villager a = QaMoverResidents1110cGameTests.villager(helper, shopA.south(), "Ina");
			Jobs.employ(level, a, helper.absolutePos(shopA), ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
			Villager b = QaMoverResidents1110cGameTests.villager(helper, shopB.south(), "Ibb");
			Jobs.employ(level, b, helper.absolutePos(shopB), ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
			Villager c = QaMoverResidents1110cGameTests.villager(helper, old.south(), "Ole");
			Jobs.employ(level, c, helper.absolutePos(old), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
			Villager d = QaMoverResidents1110cGameTests.villager(helper, shop.south(), "Sol");
			Jobs.employ(level, d, helper.absolutePos(shop), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
			Villager e = QaMoverResidents1110cGameTests.villager(helper, lectern.south(), "Lib");
			Jobs.employ(level, e, helper.absolutePos(lectern), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			List<BlockPos> found = Innkeepers.counters(level, hall, VillageHalls.RADIUS);
			List<BlockPos> want = List.of(helper.absolutePos(shopA), helper.absolutePos(shopB), helper.absolutePos(old));
			helper.assertTrue(found.size() == 3 && found.containsAll(want),
				"want the three inn counters " + want + " each once, found " + found);
			helper.assertTrue(innRolls(helper), "three inns and no legend roll at the inn");
			helper.succeed();
		});
	}

	/**
	 * The village's inn is one whose counter is in the village: an Innkeeper standing by the hall whose Shop Counter is
	 * exactly at the hall's reach still keeps an inn; one block more and it's no inn of this village. (The reach is set to
	 * 10 here: the counter is 10 blocks east of the hall.)
	 */
	//$ gametest_batch AREA '"qaInn1111cReach"'
	@GameTest(template = AREA, batch = "qaInn1111cReach")
	public void aCounterJustOutsideTheVillageIsNoInnOfIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos at = HALL.east(10);
		helper.setBlock(at, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			Villager keeper = QaMoverResidents1110cGameTests.villager(helper, HALL.east(2).south(), "Ina");
			Jobs.employ(level, keeper, helper.absolutePos(at), ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
			try {
				VillageHalls.RADIUS = 10;
				helper.assertTrue(innRolls(helper), "the Innkeeper's Shop Counter exactly at the hall's reach (10) isn't the village's inn");
				VillageHalls.RADIUS = 9;
				helper.assertFalse(innRolls(helper), "a Shop Counter 10 blocks from the hall counted as the inn of a village 9 blocks wide: "
					+ Innkeepers.counters(level, hall, VillageHalls.RADIUS));
			} finally {
				VillageHalls.RADIUS = radius;
			}
			helper.succeed();
		});
	}

	// --- The Seer's look ahead and the inn's real morning.

	/** A Rare Mason Legend who always visits the inn. */
	static Legend innLegend(String name) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"" + name + "\", \"lore\": \"the lore\", \"names\": [\"Ada of " + name + "\"],"
				+ " \"arrive\": [{\"way\": \"visit\", \"place\": \"inn\", \"chance\": 1.0}],"
				+ " \"powers\": [{\"type\": \"pace\", \"trades\": [\"minecraft:farmer\"], \"factor\": 2.0}]}").getAsJsonObject());
	}

	/** A Village Hall at {@link #HALL}, a Shop Counter at the inn spot and a free bed by it; legends and guests made go when the test ends. */
	static void legendVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
		QaMoverResidents1110cGameTests.bed(helper, QaMoverResidents1110cGameTests.INN_BED_1);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	/** Runs {@code body} with only {@code legend} loaded, moods off; puts the roster, switches and clock back before the tick ends. */
	static void staged(GameTestHelper helper, Legend legend, Runnable body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		boolean enabled = Legends.ENABLED;
		long time = level.getDayTime();
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = true;
			Legends.ENABLED = true;
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(legend.id(), legend);
			Legends.setForTest(map);
			body.run();
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.ENABLED = enabled;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	/**
	 * The Seer's foretelling ({@link LegendGuests#rollAhead}) gives the inn its legend when the village's only inn is an
	 * Innkeeper's Shop Counter, and nobody when the counter is a Shopkeeper's.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaInn1111cSeer"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaInn1111cSeer")
	public void theSeerForetellsALegendAtAShopCounterInn(GameTestHelper helper) {
		legendVillage(helper);
		Legend legend = innLegend("qa1111c_inn_seer");
		helper.runAfterDelay(2, () -> staged(helper, legend, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			level.setDayTime(4 * VillageNeeds.DAY + 20000); // the evening before day 6
			long tomorrow = Chronicle.day(level) + 1;
			Villager shopkeeper = InnShopCounterB1177GameTests.counter(helper, ModVillagers.SHOPKEEPER, "Sol");
			Map<String, Seer.Told> told = LegendGuests.rollAhead(level, hall, tomorrow, false, RandomSource.create(7L));
			helper.assertTrue(told.containsKey("inn") && told.get("inn").legend().isEmpty(),
				"the Seer foretold a legend at a Shopkeeper's Shop Counter: " + told);
			shopkeeper.discard();
			InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
			told = LegendGuests.rollAhead(level, hall, tomorrow, false, RandomSource.create(7L));
			helper.assertTrue(told.containsKey("inn") && told.get("inn").legend().equals(Optional.of(legend.id())),
				"the Seer foretold nobody at the Innkeeper's Shop Counter inn (a sure inn legend): " + told);
			helper.succeed();
		}));
	}

	/**
	 * The inn's real morning at a Shop Counter: the Innkeeper tends it with a free bed and a sure inn legend, and the
	 * legend comes as the inn's guest, by the counter.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaInn1111cMorning"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaInn1111cMorning")
	public void aShopCounterInnHostsALegendInTheMorning(GameTestHelper helper) {
		legendVillage(helper);
		Legend legend = innLegend("qa1111c_inn_morning");
		helper.runAfterDelay(2, () -> staged(helper, legend, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos counter = helper.absolutePos(INN);
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			level.setDayTime(4 * VillageNeeds.DAY + 1000); // the morning of day 5
			Villager keeper = InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
			String state = Innkeepers.tend(level, keeper, counter);
			helper.assertTrue("hosting".equals(state), "the Shop Counter inn isn't hosting in the morning: " + state);
			Villager guest = LegendGuests.guest(level, hall);
			helper.assertTrue(guest != null, "no legend guest came to the Shop Counter inn");
			LegendData data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data != null && data.id().equals(legend.id()) && "visit:inn".equals(data.way()), "the inn's guest: " + data);
			helper.assertTrue(guest.blockPosition().closerThan(counter, 9), "the inn's guest stands at " + guest.blockPosition() + ", not by the counter " + counter);
			helper.succeed();
		}));
	}
}
