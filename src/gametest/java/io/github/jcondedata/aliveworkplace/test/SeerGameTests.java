package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendText;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Pathfinder;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * 29.16, the Seer: they come to a finished Chapel only at midnight under a full moon (1 time in 2), born to a Cleric;
 * a raid foretold at dawn comes that night from the side told and none comes when none is foretold; the festival, market
 * day and next day's guest told are right (and said in chat and on "What next?"); a blessed wedding's mood and baby.
 * Each test changes the clock, the roster and the switches and puts them back within one tick ({@link #staged}).
 */
public class SeerGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = VillageNeeds.DAY;

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** The hall; when the test ends the villagers it made (with their record entries) and the buildings it recorded go. */
	private static void setUp(GameTestHelper helper, List<BlueprintData.Placement> recorded) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	/** Runs {@code body} with the Seer (and {@code more}) loaded, a village radius of 20 and moods off, then puts it all back. */
	private static void staged(GameTestHelper helper, List<Legend> more, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		int radius = VillageHalls.RADIUS;
		int min = VillageRaids.MIN_VILLAGERS;
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		boolean markets = MarketDays.ENABLED;
		boolean festivals = Festivals.ENABLED;
		Legend seer = Legends.get(Seer.ID).orElse(null);
		helper.assertTrue(seer != null, "seer.json not loaded");
		try {
			VillageHalls.RADIUS = 20;
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = true;
			VillageRaids.ENABLED = MarketDays.ENABLED = Festivals.ENABLED = true; // (off in game tests)
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(seer.id(), seer);
			more.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.accept(seer);
		} finally {
			VillageHalls.RADIUS = radius;
			VillageRaids.MIN_VILLAGERS = min;
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			VillageRaids.ENABLED = raids;
			MarketDays.ENABLED = markets;
			Festivals.ENABLED = festivals;
			BlockPos hall = helper.absolutePos(HALL);
			VillageRaids.raiders(level, hall).forEach(Mob::discard);
			VillageRaids.forget(hall);
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static BlueprintData.Placement building(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin, List<BlueprintData.Placement> recorded) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		recorded.add(placement);
		return placement;
	}

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	/** A settled Seer by the hall. */
	private static Villager settledSeer(GameTestHelper helper, Legend seer, BlockPos at) {
		Villager v = villager(helper, at);
		Legends.make(helper.getLevel(), v, seer, "test");
		return v;
	}

	/** A Rare Mason Legend who always visits the hall in the morning. */
	private static Legend mason(String id, String title) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", id), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"" + title + "\", \"lore\": \"the lore\", \"names\": [\"Ada\"],"
				+ " \"arrive\": [{\"way\": \"visit\", \"place\": \"hall\", \"chance\": 1.0}], \"powers\": []}").getAsJsonObject());
	}

	private static List<String> strings(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	/** Only at midnight, only under a full moon, only to a finished Chapel; 1 time in 2; born to a Cleric; likes jewels. */
	//$ gametest_ticks_batch AREA '100' '"seerArrives"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "seerArrives")
	public void seerComesOnlyAtMidnightUnderAFullMoonToAChapel(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), seer -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			RandomSource random = RandomSource.create(16L);
			helper.assertTrue(seer.ways("born").stream().anyMatch(w -> w.getAsJsonArray("trades").asList().stream()
				.map(JsonElement::getAsString).anyMatch("minecraft:cleric"::equals)), "the Seer isn't born to a Cleric");
			helper.assertTrue(seer.luxury().orElse("").equals("jewels"), "the Seer doesn't like jewels");
			helper.assertTrue(seer.job().equals(ResourceLocation.parse("aliveworkplace:legend")), "the Seer's trade: " + seer.job());
			helper.assertTrue(Math.abs(LegendGuests.chance(level, hall, seer.ways("visit").get(0)) - 0.5f) < 1e-4, "not 1 time in 2");
			// No Chapel: nobody, even at a full moon's midnight.
			for (long day = 1; day <= 81; day += 8) {
				at(level, day, 18000);
				LegendGuests.round(level, hall, random);
			}
			helper.assertTrue(LegendGuests.guest(level, hall) == null, "the Seer came with no Chapel");
			building(helper, StarterBlueprints.CHAPEL, new BlockPos(2, 1, 2), recorded);
			BlockPos chapel = LegendGuests.chapel(level, hall).orElseThrow();
			// Not in the evening, not in the morning, not when the moon isn't full.
			for (long day = 89; day <= 89 + 8 * 10; day += 8) {
				for (long time : new long[]{1000, 6000, 14000, 21000}) {
					at(level, day, time);
					LegendGuests.round(level, hall, random);
				}
				for (long other = day + 1; other < day + 8; other++) {
					at(level, other, 18000);
					helper.assertTrue(level.getMoonPhase() != 0, "a full moon on day " + other);
					LegendGuests.round(level, hall, random);
				}
			}
			helper.assertTrue(LegendGuests.guest(level, hall) == null, "the Seer came outside a full moon's midnight");
			// At a full moon's midnight: about half the time, at the Chapel, as a guest.
			int came = 0;
			for (int moon = 0; moon < 24; moon++) {
				long day = 1 + 8L * (20 + moon);
				at(level, day, 18000);
				helper.assertTrue(level.getMoonPhase() == 0, "not a full moon");
				LegendGuests.round(level, hall, random);
				Villager guest = LegendGuests.guest(level, hall);
				if (guest != null) {
					came++;
					LegendData data = ModAttachments.LEGEND.get(guest);
					helper.assertTrue(data.id().equals(Seer.ID) && data.guest() && data.way().equals("visit:chapel"), "the Chapel's guest: " + data);
					helper.assertTrue(guest.blockPosition().closerThan(chapel, 9), "the Seer stands at " + guest.blockPosition() + ", not by the Chapel " + chapel);
					LegendGuests.leave(level, hall, guest);
				}
			}
			helper.assertTrue(came >= 6 && came <= 18, "1 time in 2: came " + came + " times in 24 full moons");
			helper.assertTrue(LegendText.powerLines(seer, false).size() == 2, "the Seer's powers: " + LegendText.powerLines(seer, false));
			helper.succeed();
		}));
	}

	/**
	 * A raid foretold at dawn comes that night, from the side told, and not before its hour; on a night foretold quiet none
	 * comes all night. Through the hall's own round, with a fixed RandomSource each dawn.
	 */
	//$ gametest_ticks_batch AREA '100' '"seerRaids"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "seerRaids")
	public void aRaidForetoldComesAndNoneWhenNoneIs(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), seer -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = entity(helper);
			VillageRaids.MIN_VILLAGERS = 2;
			helper.assertTrue(Legends.foretold(level, hall, "raid") == 0, "warned without a Seer");
			Villager v = settledSeer(helper, seer, HALL.east(2));
			for (int i = 0; i < 3; i++) {
				villager(helper, HALL.south(2).east(i));
			}
			helper.assertTrue(Legends.foretold(level, hall, "raid") == 2, "the Seer's warning: " + Legends.foretold(level, hall, "raid") + " days");
			int villagers = VillageHalls.census(level, hall).villagers();
			boolean sawRaid = false;
			boolean sawCalm = false;
			RandomSource random = RandomSource.create(1616L); // one fixed source for every dawn (seeds in a row start alike)
			for (long day = 2; day < 120 && !(sawRaid && sawCalm); day++) {
				entity.setLastRaidDay(-100);
				at(level, day, 300);
				LegendGuests.round(level, hall, random);
				Seer.State told = entity.seer();
				helper.assertTrue(told.toldDay() == Chronicle.day(level), "no foretelling at dawn on day " + day);
				List<String> lines = strings(Seer.lines(level, hall, told));
				if (told.raid() && !sawRaid) {
					sawRaid = true;
					String side = told.night().side(hall).getString();
					helper.assertTrue(lines.get(0).equals("  Raiders will come tonight, from the " + side + "."), "the raid line: " + lines.get(0));
					helper.assertTrue(VillageAdvice.tips(level, hall).get(0).title().getString().equals("The Seer foretells raiders tonight, from the " + side),
						"What next?: " + VillageAdvice.tips(level, hall).get(0).title().getString());
					at(level, day, 13500);
					VillageRaids.tick(level, hall, villagers, 0, entity.lastRaidDay(), entity::setLastRaidDay);
					helper.assertTrue(VillageRaids.active(hall).isEmpty() == told.raidAt() > 13500, "the raid came before its hour " + told.raidAt());
					at(level, day, told.raidAt() + 1);
					VillageRaids.tick(level, hall, villagers, 0, entity.lastRaidDay(), entity::setLastRaidDay);
					helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid foretold didn't come at " + told.raidAt());
					helper.assertTrue(entity.lastRaidDay() == day && entity.seer().raidStarted(), "the raid isn't counted");
					List<Mob> raiders = VillageRaids.raiders(level, hall);
					Vec3 middle = raiders.stream().map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / raiders.size());
					String came = Pathfinder.direction(hall, BlockPos.containing(middle)).getString();
					helper.assertTrue(came.equals(side), "foretold from the " + side + ", came from the " + came);
					// Only one raid that night.
					raiders.forEach(Mob::discard);
					VillageRaids.tick(level, hall, villagers, 0, entity.lastRaidDay(), entity::setLastRaidDay);
					at(level, day, 21000);
					VillageRaids.tick(level, hall, villagers, 0, entity.lastRaidDay(), entity::setLastRaidDay);
					helper.assertTrue(VillageRaids.active(hall).isEmpty(), "a second raid the same night");
				} else if (!told.raid() && !sawCalm) {
					sawCalm = true;
					helper.assertTrue(lines.get(0).equals("  No raiders will come tonight."), "the calm line: " + lines.get(0));
					helper.assertTrue(VillageAdvice.tips(level, hall).get(0).title().getString().equals("The Seer foretells a quiet night"), "What next?");
					for (long time = 13500; time < 22000; time += 100) {
						at(level, day, time);
						VillageRaids.tick(level, hall, villagers, 0, entity.lastRaidDay(), entity::setLastRaidDay);
						helper.assertTrue(VillageRaids.active(hall).isEmpty(), "raiders came on a night foretold quiet, at " + time);
					}
				}
			}
			helper.assertTrue(sawRaid && sawCalm, "raid seen " + sawRaid + ", calm seen " + sawCalm + " (villagers " + villagers + ", chance "
				+ VillageRaids.chance(level, hall, villagers) + ", last raid " + entity.lastRaidDay() + ")");
			// The foretelling is saved with the hall.
			CompoundTag tag = new CompoundTag();
			entity.seer().save(tag);
			helper.assertTrue(Seer.State.load(tag).equals(entity.seer()), "the foretelling didn't save: " + Seer.State.load(tag));
			helper.assertTrue(Seer.State.load(new CompoundTag()).equals(Seer.State.EMPTY), "an older hall");
			// Once a day only; and with no Seer, no foretelling.
			long day = entity.seer().toldDay();
			LegendGuests.round(level, hall, RandomSource.create(5L));
			helper.assertTrue(entity.seer().toldDay() == day, "told twice");
			Legends.clear(v);
			at(level, day + 1, 300);
			LegendGuests.round(level, hall, RandomSource.create(5L));
			helper.assertTrue(entity.seer().toldDay() == day, "foretold with no Seer");
			helper.assertTrue(Seer.tips(level, hall).isEmpty(), "What next? foretells with no Seer");
			helper.succeed();
		}));
	}

	/**
	 * QA (B82): every raid foretold comes from the side told, not only the first: twelve foretold raids, each from its own
	 * dawn's RandomSource, at a small village radius (16, as PeopleGameTests uses) and at 20.
	 */
	//$ gametest_ticks_batch AREA '100' '"seerRaidSides"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "seerRaidSides")
	public void everyRaidForetoldComesFromTheSideTold(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), seer -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = entity(helper);
			VillageRaids.MIN_VILLAGERS = 2;
			settledSeer(helper, seer, HALL.east(2));
			for (int i = 0; i < 3; i++) {
				villager(helper, HALL.south(2).east(i));
			}
			List<String> wrong = new ArrayList<>();
			int raids = 0;
			long day = 2;
			for (int radius : new int[] {16, 20}) {
				VillageHalls.RADIUS = radius;
				int villagers = VillageHalls.census(level, hall).villagers();
				int seen = 0;
				for (long seed = 1; seed < 400 && seen < 12; seed++, day++) {
					entity.setLastRaidDay(-100);
					at(level, day, 300);
					LegendGuests.round(level, hall, RandomSource.create(seed * 7919L));
					Seer.State told = entity.seer();
					if (!told.raid()) {
						continue;
					}
					seen++;
					String side = told.night().side(hall).getString();
					at(level, day, told.raidAt() + 1);
					VillageRaids.tick(level, hall, villagers, 0, entity.lastRaidDay(), entity::setLastRaidDay);
					helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid foretold didn't come (radius " + radius + ", seed " + seed + ")");
					List<Mob> raiders = VillageRaids.raiders(level, hall);
					helper.assertTrue(!raiders.isEmpty(), "a raid with no raiders (radius " + radius + ", seed " + seed + ")");
					Vec3 middle = raiders.stream().map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / raiders.size());
					String came = Pathfinder.direction(hall, BlockPos.containing(middle)).getString();
					if (!came.equals(side)) {
						wrong.add("radius " + radius + " seed " + seed + ": told " + side + ", came " + came);
					}
					raiders.forEach(Mob::discard);
					VillageRaids.forget(hall);
				}
				raids += seen;
				helper.assertTrue(seen == 12, "only " + seen + " raids foretold at radius " + radius);
			}
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + raids + " raids came from another side: " + wrong);
			helper.succeed();
		}));
	}

	/** The next festival and market day told at dawn are the days they come; the next day's guest told is the one who comes. */
	//$ gametest_ticks_batch AREA '100' '"seerDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "seerDays")
	public void theFestivalMarketAndGuestToldAreRight(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend first = mason("first_mason", "First Mason");
		Legend second = mason("second_mason", "Second Mason");
		helper.runAfterDelay(2, () -> staged(helper, List.of(first, second), seer -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = entity(helper);
			settledSeer(helper, seer, HALL.east(2));
			for (int i = 0; i < Festivals.MIN_VILLAGERS; i++) {
				villager(helper, HALL.south(2).east(i - 3));
			}
			int villagers = VillageHalls.census(level, hall).villagers();
			// Day 3's dawn, the first foretelling: today is rolled too (the First Mason, at the hall), so nobody tomorrow.
			at(level, 3, 300);
			LegendGuests.round(level, hall, RandomSource.create(3L));
			helper.assertTrue(entity.seer().guestOn(3).map(e -> e.getKey().equals("hall") && e.getValue().equals(first.id())).orElse(false),
				"today's guest: " + entity.seer().guests());
			helper.assertTrue(entity.seer().guestOn(4).isEmpty(), "a guest told for the day the first is staying: " + entity.seer().guests());
			helper.assertTrue(strings(Seer.lines(level, hall, entity.seer())).contains("  No Legend will visit tomorrow."), "the no-guest line");
			helper.assertTrue(VillageAdvice.tips(level, hall).stream().anyMatch(t -> t.title().getString().equals("No Legend will visit tomorrow")),
				"What next? doesn't say nobody comes");
			// No Market Square: no market day told.
			helper.assertTrue(entity.seer().market() == -1, "a market day told with no square: " + entity.seer().market());
			helper.assertTrue(strings(Seer.lines(level, hall, entity.seer())).contains("  No market day: the village has no Market Square."), "no-market line");
			at(level, 3, 2000);
			LegendGuests.round(level, hall, RandomSource.create(3L));
			Villager guest = LegendGuests.guest(level, hall);
			helper.assertTrue(guest != null && ModAttachments.LEGEND.get(guest).id().equals(first.id()), "today's guest didn't come");
			LegendGuests.leave(level, hall, guest);
			// Day 4's dawn: the Second Mason is told for day 5 (the first waits 7 days)...
			at(level, 4, 300);
			LegendGuests.round(level, hall, RandomSource.create(4L));
			helper.assertTrue(entity.seer().guestOn(5).map(e -> e.getKey().equals("hall") && e.getValue().equals(second.id())).orElse(false),
				"tomorrow's guest: " + entity.seer().guests());
			helper.assertTrue(strings(Seer.lines(level, hall, entity.seer())).contains("  Tomorrow the Second Mason will come, to the Village Hall."),
				"the guest line: " + strings(Seer.lines(level, hall, entity.seer())));
			helper.assertTrue(VillageAdvice.tips(level, hall).stream().anyMatch(t -> t.title().getString().equals("Tomorrow's guest: the Second Mason, at the Village Hall")),
				"What next? doesn't tell the guest");
			// ... so on day 4 nobody comes, though the Second Mason always would, and on day 5 they do.
			at(level, 4, 2000);
			LegendGuests.round(level, hall, RandomSource.create(4L));
			helper.assertTrue(LegendGuests.guest(level, hall) == null, "a guest came on a day foretold empty");
			at(level, 5, 300);
			LegendGuests.round(level, hall, RandomSource.create(5L));
			at(level, 5, 2000);
			LegendGuests.round(level, hall, RandomSource.create(5L));
			guest = LegendGuests.guest(level, hall);
			helper.assertTrue(guest != null && ModAttachments.LEGEND.get(guest).id().equals(second.id()), "the guest told didn't come");
			LegendGuests.leave(level, hall, guest);

			// With a Market Square: the market day told is the first market morning, the festival day told the first festival.
			building(helper, StarterBlueprints.MARKET_SQUARE, new BlockPos(2, 1, 20), recorded);
			for (long start = 10; start < 40; start += 5) {
				at(level, start, 300);
				entity.setFestivalDay(-1);
				LegendGuests.round(level, hall, RandomSource.create(start));
				Seer.State told = entity.seer();
				helper.assertTrue(told.toldDay() == start, "no foretelling on day " + start);
				helper.assertTrue(told.market() >= start && told.market() < start + MarketDays.EVERY_DAYS, "market day told: " + told.market());
				helper.assertTrue(told.festival() >= start && told.festival() < start + Festivals.EVERY_DAYS, "festival day told: " + told.festival());
				long market = -1;
				long festival = -1;
				for (long day = start; day < start + 9; day++) {
					at(level, day, 2000);
					if (market < 0 && MarketDays.isMarketMorning(level, hall, entity.lastMarketDay())) {
						market = day;
					}
					at(level, day, 300);
					Festivals.round(level, hall, entity, villagers);
					if (festival < 0 && entity.festivalDay() == day) {
						festival = day;
					}
				}
				helper.assertTrue(market == told.market(), "market day told " + told.market() + ", came " + market);
				helper.assertTrue(festival == told.festival(), "festival told " + told.festival() + ", came " + festival);
				String when = Seer.when(told.market(), start).getString();
				helper.assertTrue(strings(Seer.lines(level, hall, told)).contains("  The next market day: " + when + " (day " + told.market() + ")."),
					"the market line: " + strings(Seer.lines(level, hall, told)));
			}
			helper.assertTrue(Seer.when(5, 5).getString().equals("today") && Seer.when(6, 5).getString().equals("tomorrow")
				&& Seer.when(8, 5).getString().equals("in 3 days"), "the days' words");
			helper.succeed();
		}));
	}

	private static void bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static void court(ServerLevel level, Villager a, Villager b) {
		long today = Chronicle.day(level);
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), today - 2, false));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), today - 2, false));
	}

	/**
	 * A wedding at the Chapel with the Seer there is blessed: +10 mood for 7 days, and the first baby comes when a bed is
	 * free, past the daily wait (once). Not without the Seer, nor at the bell.
	 */
	//$ gametest_ticks_batch AREA '100' '"seerWeddings"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "seerWeddings")
	public void aBlessedWeddingsMoodAndBaby(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		bed(helper, new BlockPos(25, 2, 25));
		helper.runAfterDelay(2, () -> staged(helper, List.of(), seer -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 30, 7000);
			Villager a = villager(helper, new BlockPos(20, 2, 20));
			Villager b = villager(helper, new BlockPos(21, 2, 20));
			Villager c = villager(helper, new BlockPos(22, 2, 22));
			Villager d = villager(helper, new BlockPos(23, 2, 22));
			// At the bell (no Chapel), with the Seer there: not blessed.
			Villager s = settledSeer(helper, seer, new BlockPos(8, 2, 8));
			court(level, c, d);
			Couples.wed(level, hall, c, d);
			helper.assertTrue(!ModAttachments.SEER_BLESSING.has(c), "a wedding at the bell blessed");
			// At the Chapel without the Seer: not blessed.
			building(helper, StarterBlueprints.CHAPEL, new BlockPos(2, 1, 2), recorded);
			BlockPos chapel = LegendGuests.chapel(level, hall).orElseThrow();
			helper.assertTrue(Couples.venue(level, hall).equals(chapel), "weddings aren't at the Chapel");
			Legends.clear(s);
			court(level, c, d);
			Couples.wed(level, hall, c, d);
			helper.assertTrue(!ModAttachments.SEER_BLESSING.has(c), "a wedding blessed with no Seer");
			// At the Chapel with the Seer there: blessed.
			Legends.make(level, s, seer, "test");
			court(level, a, b);
			Couples.wed(level, hall, a, b);
			Seer.Blessing blessing = ModAttachments.SEER_BLESSING.get(a);
			helper.assertTrue(blessing != null && ModAttachments.SEER_BLESSING.has(b) && blessing.mood() == 10 && blessing.days() == 7 && blessing.babyDue(),
				"not blessed: " + blessing);
			helper.assertTrue(entity(helper).chronicle().stream().anyMatch(e -> e.text().getString().endsWith("blessed the wedding of "
				+ a.getDisplayName().getString() + " and " + b.getDisplayName().getString() + " at the Chapel")), "no chronicle line");
			// The mood: 10 more, with its reason, for 7 days.
			Moods.forget();
			Moods.Mood blessed = Moods.work(level, a);
			ModAttachments.SEER_BLESSING.remove(a);
			Moods.Mood plain = Moods.work(level, a);
			ModAttachments.SEER_BLESSING.set(a, blessing);
			helper.assertTrue(blessed.score() - plain.score() == 10, "blessed " + blessed.score() + ", plain " + plain.score());
			helper.assertTrue(strings(blessed.good()).contains("a wedding the Seer blessed"), "the reason: " + strings(blessed.good()));
			at(level, 36, 7000);
			helper.assertTrue(Seer.blessedMood(level, a) != null, "the mood ended before 7 days");
			at(level, 37, 7000);
			helper.assertTrue(Seer.blessedMood(level, a) == null, "the mood outlasted 7 days");
			// The baby: past the daily wait and a poor mood, once; not after the blessing's 2 days.
			at(level, 32, 1000);
			VillageNeeds.Needs needs = new VillageNeeds.Needs(4, 4, 5, 4, 4, 0, 0, 0.1f);
			long justNow = level.getGameTime();
			helper.assertTrue(VillageGrowth.blocker(level, hall, needs, justNow) != VillageGrowth.Blocker.NONE, "the village could grow anyway");
			at(level, 33, 1000);
			helper.assertTrue(VillageGrowth.grow(level, hall, needs, justNow) == null, "the blessed baby came after its 2 days");
			at(level, 32, 1000);
			Villager baby = VillageGrowth.grow(level, hall, needs, justNow);
			helper.assertTrue(baby != null && baby.isBaby(), "no blessed baby");
			Families.Parents parents = Families.parents(baby);
			helper.assertTrue(parents != null && List.of(parents.mother().getString(), parents.father().getString()).containsAll(
				List.of(a.getDisplayName().getString(), b.getDisplayName().getString())), "the baby's parents: " + parents);
			helper.assertTrue(!ModAttachments.SEER_BLESSING.get(a).babyDue() && !ModAttachments.SEER_BLESSING.get(b).babyDue(), "the baby is still due");
			bed(helper, new BlockPos(27, 2, 25));
			helper.assertTrue(VillageGrowth.grow(level, hall, needs, justNow) == null, "a second early baby");
			baby.discard();
			helper.succeed();
		}));
	}
}
