package io.github.jcondedata.aliveworkplace.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.AtPeace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Peaceful settings and the threat level (ROADMAP 32.21). The tester's config matrix: each system's own switch off
 * ({@code villageRaids}, {@code banditCamps}, {@code raiderCultures}, {@code sieges}, {@code siegeDamage},
 * {@code disasters}) and each {@code threatLevel} (0: nothing starts; 1: half as often, no ram, ladder, sand ramp or hex,
 * no plague; 2: as before; 3: half again as often and a quarter more raiders), and Peaceful. At peace on the Defence
 * page: the owner sets it (no one else can), the guards icon says so, a lair standing leaves at the next dawn and no new
 * one comes, and the setting survives a save and load (a hall saved before loads with it off). The operators'
 * {@code /workplace threat} starts and ends each culture's raid and each disaster, and clears a lair. Every new
 * sentence a player reads.
 */
public class ThreatLevelGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;
	private static final ResourceLocation BLIGHT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_blight");
	private static final ResourceLocation PLAGUE = AliveWorkplace.id("plague");
	private static final ResourceLocation DROUGHT = AliveWorkplace.id("drought");
	private static final ResourceLocation WARBAND = AliveWorkplace.id("pillager_warband");
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A hall with a village radius of 16 and four villagers; the config, the clock and the threats go back when it ends. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
				e.setAtPeace(false, -1);
			}
			VillageRaids.forget();
			BanditCamps.forget(level);
			Disasters.forget();
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	private static VillageHallBlockEntity entity(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = helper.getLevel().getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		helper.assertTrue(entity != null, "no hall");
		return entity;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** Every line of an icon: its name, then its tooltip. */
	private static List<String> lines(ItemStack icon) {
		List<String> lines = new ArrayList<>();
		lines.add(icon.getHoverName().getString());
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(line -> lines.add(line.getString()));
		}
		return lines;
	}

	private static String last(VillageHallBlockEntity entity) {
		List<Chronicle.Entry> chronicle = entity.chronicle();
		return chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
	}

	/** A random whose every float is 0: any chance above 0 comes. */
	private static RandomSource always() {
		return new LegacyRandomSource(7L) {
			@Override
			public float nextFloat() {
				return 0f;
			}

			@Override
			public double nextDouble() {
				return 0d;
			}
		};
	}

	private static Disaster blight(GameTestHelper helper) {
		Disaster blight = Disasters.get(BLIGHT).orElse(null);
		helper.assertTrue(blight != null, "the test datapack's disaster isn't loaded: " + Disasters.all());
		return blight;
	}

	private static Culture culture(GameTestHelper helper, ResourceLocation id) {
		Culture culture = Threats.get(id).orElse(null);
		helper.assertTrue(culture != null, "no culture " + id + " in " + Threats.all());
		return culture;
	}

	/** The chances at the threat level the config gives: of a raid on a village of 20, of a lair, of the test blight. */
	private record Chances(float raid, float lair, double blight) {
	}

	private static Chances chances(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		return new Chances(VillageRaids.chance(level, hall, 20), Lairs.dailyChance(level, hall),
			Disasters.chance(level, hall, blight(helper), VillageHalls.census(level, hall)));
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-5;
	}

	// The threat levels.

	/** The default is 2, the file is clamped to 0-3, and it is on the settings screen. */
	//$ gametest_batch EMPTY_STRUCTURE '"threatLevelConfig"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "threatLevelConfig")
	public void theThreatLevelIsTwoUnlessTheFileSays(GameTestHelper helper) {
		try {
			helper.assertTrue(new WorkplaceConfig().threatLevel == 2 && Threats.LEVEL == 2, "the default threat level: " + Threats.LEVEL);
			helper.assertTrue(WorkplaceConfig.parse("{\"threatLevel\": 9}").threatLevel == 3, "9 isn't clamped to 3");
			helper.assertTrue(WorkplaceConfig.parse("{\"threatLevel\": -4}").threatLevel == 0, "-4 isn't clamped to 0");
			WorkplaceConfig.parse("{\"threatLevel\": 1}").apply();
			helper.assertTrue(Threats.LEVEL == 1 && Threats.gentle(), "the file's 1 isn't applied: " + Threats.LEVEL);
			helper.assertTrue(WorkplaceConfig.optionNames().contains("threatLevel") && !WorkplaceConfig.isSwitch("threatLevel"),
				"threatLevel isn't a number on the settings screen");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * Level 0: nothing starts. No raid (the chance is 0, no night rolls one, and an attack already on the clock is taken
	 * off it), no lair, no disaster (and one under way ends), no hex.
	 */
	//$ gametest_ticks_batch AREA '40' '"threatLevel0"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "threatLevel0")
	public void atThreatLevelZeroNothingStarts(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		helper.runAfterDelay(2, () -> {
			at(level, 60, 15000);
			Chances normal = chances(helper, hall);
			helper.assertTrue(normal.raid() > 0 && normal.lair() > 0 && normal.blight() > 0, "at level 2 something comes: " + normal);
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			helper.assertTrue(Threats.quiet(level, hall), "the village isn't quiet at level 0");
			Chances none = chances(helper, hall);
			helper.assertTrue(none.raid() == 0 && none.lair() == 0 && none.blight() == 0, "something comes at level 0: " + none);
			for (long seed = 0; seed < 20; seed++) {
				helper.assertFalse(VillageRaids.rollNight(level, hall, 20, -100, RandomSource.create(seed)).raid(), "a night rolled a raid at level 0");
			}
			// An attack set on the clock before: it never comes, and it's off the clock (no warning of it).
			long day = Chronicle.day(level);
			ThreatData.get(level).rolled(hall, day - 1, new ThreatData.Attack(day, Threats.MONSTERS, 0, 13500));
			VillageRaids.tick(level, hall, 20, 0, -100, d -> helper.fail("a raid came at level 0"));
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty() && ThreatData.get(level).attacks(hall).isEmpty(),
				"the clock's attack is still set: " + ThreatData.get(level).attacks(hall));
			// No lair makes camp, even when every roll comes up.
			for (int i = 0; i < 10; i++) {
				Lairs.round(level, hall, true, always());
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a lair made camp at level 0");
			// No disaster starts; one under way ends.
			helper.assertTrue(Disasters.roll(level, hall, entity, VillageHalls.census(level, hall), always()).isEmpty() && entity.disaster() == null,
				"a disaster started at level 0: " + entity.disaster());
			entity.setDisaster(BLIGHT, Chronicle.day(level), Chronicle.day(level) + 1, -100);
			Disasters.round(level, hall, entity, VillageHalls.census(level, hall));
			helper.assertTrue(entity.disaster() == null, "the blight went on at level 0");
			helper.succeed();
		});
	}

	/**
	 * Level 1, gentle: raids, lairs and disasters half as often; no ram, ladder, sand ramp or hex is used (no siege is
	 * laid, the tactics are left out, nobody is hexed), and a plague under way ends.
	 */
	//$ gametest_ticks_batch AREA '40' '"threatLevel1"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "threatLevel1")
	public void atThreatLevelOneThereIsNoSiegeHexOrPlague(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		helper.runAfterDelay(2, () -> {
			at(level, 61, 15000);
			Chances normal = chances(helper, hall);
			Culture warband = culture(helper, WARBAND);
			Culture desert = culture(helper, DESERT);
			Culture coven = culture(helper, COVEN);
			Culture piglins = culture(helper, PIGLINS);
			helper.assertTrue(Threats.tactics(warband).size() == 2 && Threats.tactics(desert).size() == 1 && Threats.tactics(coven).size() == 1
				&& Threats.tactics(piglins).size() == 2 && Sieges.lays(warband) && Sieges.lays(desert), "at level 2 the tactics are all there");
			WorkplaceConfig.parse("{\"threatLevel\": 1}").apply();
			Chances gentle = chances(helper, hall);
			helper.assertTrue(near(gentle.raid(), normal.raid() / 2) && near(gentle.lair(), normal.lair() / 2) && near(gentle.blight(), normal.blight() / 2),
				"not half as often at level 1: " + gentle + " against " + normal);
			helper.assertTrue(Threats.tactics(warband).isEmpty(), "the warband still rams or ladders: " + warband.tactics());
			helper.assertTrue(Threats.tactics(desert).isEmpty(), "the desert raiders still build sand ramps");
			helper.assertTrue(Threats.tactics(coven).isEmpty(), "the coven still hexes");
			helper.assertTrue(Threats.tactics(piglins).size() == 1, "the piglins still ram (or lost their plunder): " + Threats.tactics(piglins).size());
			helper.assertFalse(Sieges.lays(warband) || Sieges.lays(desert) || Sieges.lays(piglins), "a culture still lays sieges at level 1");
			helper.assertFalse(Threats.quiet(level, hall), "level 1 isn't quiet");
			// No hex: a coven's lair by the village hexes nobody.
			Lairs.putForTest(level, new Lairs.Lair(helper.absolutePos(CAMP), hall, new java.util.UUID(1, 1), Chronicle.day(level), COVEN, 6, Chronicle.day(level), 0, 0, 0));
			Mob villager = level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, helper.getBounds(), v -> true).stream().findFirst().orElse(null);
			helper.assertTrue(villager instanceof net.minecraft.world.entity.npc.Villager, "no villager");
			helper.assertFalse(Covens.cursed(level, (net.minecraft.world.entity.npc.Villager) villager), "a villager is under the coven's hex at level 1");
			BanditCamps.forget(level);
			// No plague: one under way ends.
			if (Disasters.get(PLAGUE).isPresent()) {
				entity.setDisaster(PLAGUE, Chronicle.day(level), Chronicle.day(level), -100);
				Disasters.round(level, hall, entity, VillageHalls.census(level, hall));
				helper.assertTrue(entity.disaster() == null, "the plague went on at level 1");
			} else {
				helper.fail("the plague isn't loaded: " + Disasters.all());
			}
			helper.succeed();
		});
	}

	/** Level 2, normal: everything as it was (the chances the systems give, every tactic, sieges). */
	//$ gametest_ticks_batch AREA '40' '"threatLevel2"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "threatLevel2")
	public void atThreatLevelTwoEverythingIsAsBefore(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 62, 15000);
			WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
			helper.assertTrue(near(VillageRaids.chance(level, hall, 20), VillageRaids.nightlyChance(20) * Threats.chanceFactor(level, hall)),
				"a raid's chance at level 2: " + VillageRaids.chance(level, hall, 20));
			helper.assertTrue(near(Lairs.dailyChance(level, hall), Lairs.DAILY_CHANCE), "a lair's chance at level 2: " + Lairs.dailyChance(level, hall));
			helper.assertTrue(near(chances(helper, hall).blight(), blight(helper).chance()), "the blight's chance at level 2: " + chances(helper, hall).blight());
			helper.assertTrue(Threats.tactics(culture(helper, WARBAND)).size() == 2 && Sieges.lays(culture(helper, WARBAND)), "the warband's rams and ladders");
			helper.assertTrue(VillageRaids.expected(level, hall, 20, 0, Threats.MONSTERS) == 8, "raiders at level 2: " + VillageRaids.expected(level, hall, 20, 0, Threats.MONSTERS));
			helper.succeed();
		});
	}

	/** Level 3, hard: half again as often, and a quarter more raiders (a raid of 8 is 10; it really sends them). */
	//$ gametest_ticks_batch AREA '60' '"threatLevel3"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "threatLevel3")
	public void atThreatLevelThreeMoreComeMoreOften(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 63, 15000);
			Chances normal = chances(helper, hall);
			WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
			Chances hard = chances(helper, hall);
			helper.assertTrue(near(hard.raid(), normal.raid() * 1.5f) && near(hard.lair(), normal.lair() * 1.5f) && near(hard.blight(), Math.min(1, normal.blight() * 1.5)),
				"not half again as often at level 3: " + hard + " against " + normal);
			helper.assertTrue(VillageRaids.expected(level, hall, 20, 0, Threats.MONSTERS) == 10, "raiders at level 3: " + VillageRaids.expected(level, hall, 20, 0, Threats.MONSTERS));
			helper.assertTrue(VillageRaids.start(level, hall, 20, 0, Double.NaN, culture(helper, Threats.MONSTERS), RandomSource.create(3L)) != null, "no raid started");
			int came = ThreatData.get(level).raid(hall).map(ThreatData.Under::raiders).orElse(0);
			helper.assertTrue(came == 10, "a raid of 8 at level 3 sent " + came);
			helper.succeed();
		});
	}

	/**
	 * Level 3 for the lairs too (B383): a raid comes from the band at the lair, so on hard every lair culture's band is a
	 * quarter bigger when it makes camp and grows to a quarter more; at levels 1 and 2 it is what its culture says. A
	 * warband's lair at level 3 sends its whole bigger band.
	 */
	//$ gametest_ticks_batch AREA '100' '"threatLevel3Lairs"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatLevel3Lairs")
	public void atThreatLevelThreeEveryLairIsAQuarterBigger(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 64, 13000);
			for (ResourceLocation id : List.of(WARBAND, DESERT, COVEN, PIGLINS, Threats.BANDITS)) {
				Culture.Lair spec = culture(helper, id).lair().orElse(null);
				helper.assertTrue(spec != null, "no lair for " + id);
				for (int threat = 1; threat <= 3; threat++) {
					WorkplaceConfig.parse("{\"threatLevel\": " + threat + "}").apply();
					Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), id);
					int want = threat == 3 ? (int) Math.ceil(spec.strength() * 1.25) : spec.strength();
					helper.assertTrue(lair != null && lair.strength() == want, id + " at threat level " + threat + ": " + lair + ", not " + want);
					helper.assertTrue(Lairs.most(spec) == (threat == 3 ? (int) Math.ceil(spec.max() * 1.25) : spec.max()), id + "'s most at level " + threat + ": " + Lairs.most(spec));
					level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(Lairs.TAG)).forEach(Mob::discard);
				}
			}
			// It grows past its culture's max on hard: a warband at 18 (its max) grows by 2 at the next dawn, to 20 of 23.
			Culture.Lair warband = culture(helper, WARBAND).lair().orElseThrow();
			long today = Chronicle.day(level);
			Lairs.putForTest(level, new Lairs.Lair(helper.absolutePos(CAMP), hall, new java.util.UUID(1, 1), today - 1, WARBAND, warband.max(), today - 1, 0, 0, 0));
			Lairs.dawn(level, hall, today);
			int grown = Lairs.near(level, hall).map(Lairs.Lair::strength).orElse(-1);
			helper.assertTrue(grown == warband.max() + warband.growth(), "a warband at its max at level 3 grew to " + grown);
			WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
			Lairs.putForTest(level, new Lairs.Lair(helper.absolutePos(CAMP), hall, new java.util.UUID(1, 1), today - 1, WARBAND, warband.max(), today - 1, 0, 0, 0));
			Lairs.dawn(level, hall, today);
			grown = Lairs.near(level, hall).map(Lairs.Lair::strength).orElse(-1);
			helper.assertTrue(grown == warband.max(), "a warband at its max at level 2 grew to " + grown);
			// The raid from a warband's lair made on hard: the whole band, a quarter more than at level 2.
			WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no warband lair");
			at(level, 64, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, culture(helper, WARBAND), RandomSource.create(6L));
			int whole = (int) Math.ceil(warband.strength() * 1.25);
			helper.assertTrue(raid != null && raid.raiders() == whole, "a warband's raid at level 3: " + raid + ", not " + whole);
			helper.succeed();
		});
	}

	/** On Peaceful nothing comes, whatever the threat level; At peace does the same for one village. */
	//$ gametest_batch EMPTY_STRUCTURE '"threatPeaceful"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "threatPeaceful")
	public void onPeacefulNothingComes(GameTestHelper helper) {
		for (int level = 0; level <= 3; level++) {
			helper.assertTrue(Threats.quiet(level, Difficulty.PEACEFUL, false), "something comes on Peaceful at level " + level);
			helper.assertTrue(Threats.quiet(level, Difficulty.HARD, true), "something comes At peace at level " + level);
			helper.assertTrue(Threats.quiet(level, Difficulty.NORMAL, false) == (level == 0), "level " + level + " on Normal");
		}
		helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL, "the test world is on Peaceful");
		helper.succeed();
	}

	// Each system's own switch off.

	/** {@code villageRaids} off: the monsters never come, not even on an order. */
	//$ gametest_ticks_batch AREA '40' '"threatSwitchRaids"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "threatSwitchRaids")
	public void villageRaidsOffMeansNoMonsters(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			WorkplaceConfig.parse("{\"villageRaids\": false}").apply();
			helper.assertTrue(!VillageRaids.ENABLED && Threats.on(Threats.MONSTERS).isEmpty() && Threats.on(Threats.BANDITS).isPresent(),
				"villageRaids off: " + Threats.DISABLED);
			helper.assertTrue(VillageRaids.start(level, hall, 4, 0) == null && ThreatData.get(level).raid(hall).isEmpty(), "the monsters came with villageRaids off");
			helper.succeed();
		});
	}

	/** {@code banditCamps} off: the bandits never make camp, and never raid. */
	//$ gametest_ticks_batch AREA '40' '"threatSwitchBandits"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "threatSwitchBandits")
	public void banditCampsOffMeansNoBandits(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			WorkplaceConfig.parse("{\"banditCamps\": false}").apply();
			helper.assertTrue(!BanditCamps.ENABLED && Threats.on(Threats.BANDITS).isEmpty() && Threats.on(Threats.MONSTERS).isPresent(),
				"banditCamps off: " + Threats.DISABLED);
			for (int i = 0; i < 5; i++) {
				helper.assertFalse(Lairs.comer(level, hall, BanditCamps.ENABLED, RandomSource.create(i)).filter(Threats.BANDITS::equals).isPresent(),
					"the bandits would make camp with banditCamps off");
			}
			helper.succeed();
		});
	}

	/** {@code raiderCultures} with the warband off: it is never picked and never comes from a lair. */
	//$ gametest_ticks_batch AREA '40' '"threatSwitchCultures"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "threatSwitchCultures")
	public void aCultureSwitchedOffNeverComes(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			WorkplaceConfig.parse("{\"raiderCultures\": {\"pillager_warband\": false}}").apply();
			helper.assertTrue(Threats.on(WARBAND).isEmpty() && !Threats.enabled(WARBAND) && Threats.on(Threats.MONSTERS).isPresent(),
				"raiderCultures with the warband off: " + Threats.DISABLED);
			Lairs.putForTest(level, new Lairs.Lair(helper.absolutePos(CAMP), hall, new java.util.UUID(2, 2), Chronicle.day(level), WARBAND, 6, Chronicle.day(level), 0, 0, 0));
			helper.assertFalse(VillageRaids.cultureFor(level, hall, 20, RandomSource.create(1L)).map(Culture::id).filter(WARBAND::equals).isPresent(),
				"the warband came from its lair while switched off");
			helper.succeed();
		});
	}

	/** {@code sieges} off: a warband's raid is a plain one. */
	//$ gametest_batch EMPTY_STRUCTURE '"threatSwitchSieges"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "threatSwitchSieges")
	public void siegesOffMeansNoSiege(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"sieges\": false}").apply();
			helper.assertTrue(!Sieges.ENABLED, "sieges off isn't applied");
			helper.assertTrue(Sieges.begin(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), culture(helper, WARBAND), List.of()).isEmpty(),
				"a siege began with sieges off");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(Sieges.ENABLED, "sieges aren't back on");
		helper.succeed();
	}

	/** {@code siegeDamage} off: the rams' blows cost nothing. */
	//$ gametest_batch EMPTY_STRUCTURE '"threatSwitchSiegeDamage"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "threatSwitchSiegeDamage")
	public void siegeDamageOffMeansGatesHold(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"siegeDamage\": false}").apply();
			helper.assertTrue(Sieges.ENABLED && !Sieges.DAMAGE && !Sieges.damaging(helper.getLevel()), "siegeDamage off isn't applied");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(Sieges.DAMAGE, "siege damage isn't back on");
		helper.succeed();
	}

	/** {@code disasters} with the blight off: it never starts, and one under way ends. */
	//$ gametest_ticks_batch AREA '40' '"threatSwitchDisasters"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "threatSwitchDisasters")
	public void aDisasterSwitchedOffNeverStarts(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		helper.runAfterDelay(2, () -> {
			WorkplaceConfig.parse("{\"disasters\": {\"" + BLIGHT + "\": false}}").apply();
			helper.assertTrue(Disasters.on(BLIGHT).isEmpty() && Disasters.on(DROUGHT).isPresent(), "the blight is still on: " + Disasters.DISABLED);
			helper.assertTrue(Disasters.roll(level, hall, entity, VillageHalls.census(level, hall), always()).map(Disaster::id).filter(BLIGHT::equals).isEmpty(),
				"the blight started while switched off");
			entity.setDisaster(BLIGHT, Chronicle.day(level), Chronicle.day(level) + 1, -100);
			Disasters.round(level, hall, entity, VillageHalls.census(level, hall));
			helper.assertTrue(entity.disaster() == null || !entity.disaster().equals(BLIGHT), "the blight went on while switched off");
			helper.succeed();
		});
	}

	// At peace.

	/**
	 * The owner sets the village At peace on the Defence page (nobody else can); the guards icon says so and that the
	 * camp leaves at the next dawn. The same day it stands; at the next dawn it leaves (its band gone, the chronicle
	 * says so), no new one comes however the dice fall, and no raid or disaster can come either.
	 */
	//$ gametest_ticks_batch AREA '100' '"atPeace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "atPeace")
	public void atPeaceTheLairLeavesAtDawnAndNoneComes(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		ServerPlayer owner = player(helper);
		ServerPlayer stranger = player(helper);
		helper.runAfterDelay(2, () -> {
			at(level, 70, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			int band = Lairs.band(level, lair).size();
			helper.assertTrue(band > 1, "the camp has no band: " + band);

			// The owner opens the Defence page and sets the village at peace.
			ChoiceMenu menu = VillageHallScreen.forTest(owner, hall);
			menu.press(VillageHallScreen.GUARDS, owner);
			helper.assertTrue(lines(menu.icon(DefencePage.AT_PEACE)).get(0).equals("At peace: off") && menu.icon(DefencePage.AT_PEACE).is(Items.GRAY_DYE),
				"the page's At peace before: " + lines(menu.icon(DefencePage.AT_PEACE)));
			helper.assertTrue(lines(menu.icon(DefencePage.AT_PEACE)).contains("Click to set the village at peace"), "the click line: " + lines(menu.icon(DefencePage.AT_PEACE)));
			menu.press(DefencePage.AT_PEACE, owner);
			helper.assertTrue(entity.atPeace() && entity.peaceSince() == 70 && owner.getUUID().equals(entity.owner()), "the click didn't set it at peace");
			List<String> page = lines(menu.icon(DefencePage.AT_PEACE));
			helper.assertTrue(page.get(0).equals("At peace") && menu.icon(DefencePage.AT_PEACE).is(Items.WHITE_TULIP)
				&& page.contains("At peace since day 70") && page.contains("The Bandit camp leaves at the next dawn")
				&& page.contains("Click to let threats come again"), "the page's At peace after: " + page);
			helper.assertTrue(last(entity).contains("set the village at peace"), "the chronicle: " + last(entity));

			// Nobody else may change it.
			Component refused = AtPeace.toggle(level, hall, stranger);
			helper.assertTrue(entity.atPeace() && refused.getString().contains("can set the village at peace"), "a stranger changed it: " + refused.getString());
			ChoiceMenu theirs = VillageHallScreen.forTest(stranger, hall);
			theirs.press(VillageHallScreen.GUARDS, stranger);
			helper.assertTrue(lines(theirs.icon(DefencePage.AT_PEACE)).stream().anyMatch(l -> l.startsWith("Only ") && l.endsWith("can change this")),
				"the stranger's page: " + lines(theirs.icon(DefencePage.AT_PEACE)));

			// The guards icon says so.
			List<String> guards = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(guards.contains("At peace: no raiders, camps or disasters come") && guards.contains("The Bandit camp leaves at the next dawn"),
				"the guards icon: " + guards);

			// Nothing comes.
			helper.assertTrue(Threats.quiet(level, hall) && VillageRaids.chance(level, hall, 20) == 0 && Lairs.dailyChance(level, hall) == 0
				&& Disasters.chance(level, hall, blight(helper), VillageHalls.census(level, hall)) == 0, "something may still come At peace");

			// The same day the camp stands; at the next dawn it leaves.
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left before dawn");
			at(level, 71, 100);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp is still there at dawn");
			helper.assertTrue(Lairs.band(level, lair).isEmpty() && level.getEntity(lair.captain()) == null, "the band stayed: " + Lairs.band(level, lair).size());
			helper.assertTrue(last(entity).equals("The raiders of the Bandit camp packed up and left the village in peace"), "the chronicle: " + last(entity));
			helper.assertTrue(Lairs.resting(level, hall) == 0, "leaving in peace counts as broken up");
			List<String> after = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(after.contains("At peace: no raiders, camps or disasters come") && after.stream().noneMatch(l -> l.contains("leaves at")),
				"the guards icon after: " + after);

			// No new one comes, day after day, however the dice fall.
			for (long day = 71; day < 75; day++) {
				at(level, day, 6000);
				Lairs.round(level, hall, true, always());
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a new lair came At peace");

			// Off again: threats may come.
			Component off = AtPeace.toggle(level, hall, owner);
			helper.assertTrue(!entity.atPeace() && entity.peaceSince() == -1 && !Threats.quiet(level, hall) && Lairs.dailyChance(level, hall) > 0,
				"At peace didn't turn off: " + off.getString());
			helper.succeed();
		});
	}

	/** A raid the clock set for tonight doesn't come to a village set At peace. */
	//$ gametest_ticks_batch AREA '40' '"atPeaceRaid"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "atPeaceRaid")
	public void atPeaceNoRaidComes(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		helper.runAfterDelay(2, () -> {
			at(level, 72, 15000);
			entity.setAtPeace(true, 72);
			long day = Chronicle.day(level);
			ThreatData.get(level).rolled(hall, day - 1, new ThreatData.Attack(day, Threats.MONSTERS, 0, 13500));
			VillageRaids.tick(level, hall, 20, 0, -100, d -> helper.fail("a raid came At peace"));
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty() && ThreatData.get(level).attacks(hall).isEmpty(), "the clock's attack is still set");
			entity.setDisaster(BLIGHT, day, day + 1, -100);
			Disasters.round(level, hall, entity, VillageHalls.census(level, hall));
			helper.assertTrue(entity.disaster() == null, "the blight went on At peace");
			helper.succeed();
		});
	}

	/** At peace survives a save and load of the hall; a hall saved before 32.21 loads with it off. */
	//$ gametest_batch AREA '"atPeaceSave"'
	@GameTest(template = AREA, batch = "atPeaceSave")
	public void atPeaceIsSavedWithTheHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		entity.setAtPeace(true, 33);
		CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
		entity.setAtPeace(false, -1);
		entity.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(entity.atPeace() && entity.peaceSince() == 33, "At peace was lost: " + entity.atPeace() + " since " + entity.peaceSince());
		CompoundTag old = tag.copy();
		old.remove("atPeace");
		old.remove("peaceSince");
		entity.loadWithComponents(old, level.registryAccess());
		helper.assertTrue(!entity.atPeace() && entity.peaceSince() == -1, "a hall saved before loads At peace");
		helper.assertTrue(!entity.saveWithoutMetadata(level.registryAccess()).contains("atPeace"), "a hall not at peace saves the field");
		helper.succeed();
	}

	// The command.

	private static CommandSourceStack op(ServerLevel level, BlockPos at, List<String> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, 2, "test", Component.literal("test"), level.getServer(), null);
	}

	private static int run(ServerLevel level, CommandSourceStack source, String command) {
		try {
			return level.getServer().getCommands().getDispatcher().execute(command, source);
		} catch (CommandSyntaxException e) {
			throw new IllegalStateException(command + ": " + e.getMessage());
		}
	}

	/**
	 * {@code /workplace threat start <culture>} starts each culture's raid at the nearest hall, and {@code end} ends it
	 * (its raiders gone, nothing in the past attacks); {@code disaster <id>} starts each disaster and {@code end} ends it.
	 * A culture or disaster nobody loaded, a second raid and a raid with no hall are refused, with words.
	 */
	//$ gametest_ticks_batch AREA '100' '"threatCommand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatCommand")
	public void theCommandStartsAndEndsEachRaidAndDisaster(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			at(level, 80, 15000);
			CommandSourceStack op = op(level, hall, said);
			String village = VillageHalls.name(level, hall).getString();
			int cultures = 0;
			for (Culture culture : List.copyOf(Threats.all())) {
				if (!culture.id().getNamespace().equals(AliveWorkplace.MOD_ID)) {
					continue; // (the test pack's own)
				}
				cultures++;
				said.clear();
				int sent = run(level, op, "workplace threat start " + culture.id());
				helper.assertTrue(sent > 0 && ThreatData.get(level).raid(hall).map(r -> r.culture().equals(culture.id())).orElse(false),
					"no raid by " + culture.id() + ": " + said);
				helper.assertTrue(!VillageRaids.raiders(level, hall).isEmpty(), "no raiders of " + culture.id());
				String name = Lairs.cultureName(culture.id()).getString();
				helper.assertTrue(said.contains(name + " are raiding " + village + ": " + sent + " of them."), "what the command said: " + said);
				said.clear();
				run(level, op, "workplace threat end");
				helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "the raid by " + culture.id() + " didn't end");
				helper.assertTrue(VillageRaids.raiders(level, hall).isEmpty(), "raiders of " + culture.id() + " stayed: " + VillageRaids.raiders(level, hall).size());
				helper.assertTrue(said.contains(village + ": the raid and the disaster under way are over."), "what end said: " + said);
			}
			helper.assertTrue(cultures == 7, "expected our 7 cultures, found " + cultures);
			helper.assertTrue(ThreatData.get(level).past(hall).isEmpty(), "a called-off raid went into the past attacks: " + ThreatData.get(level).past(hall));
			// A culture of ours by its path, and a second raid refused.
			run(level, op, "workplace threat start bandits");
			helper.assertTrue(ThreatData.get(level).raid(hall).map(r -> r.culture().equals(Threats.BANDITS)).orElse(false), "bandits by their path");
			said.clear();
			helper.assertTrue(run(level, op, "workplace threat start monsters") == 0 && ThreatData.get(level).raid(hall).map(r -> r.culture().equals(Threats.BANDITS)).orElse(false)
				&& said.contains("A raid is already under way at " + village + ": /workplace threat end first."), "a second raid: " + said);
			run(level, op, "workplace threat end");
			said.clear();
			helper.assertTrue(run(level, op, "workplace threat start nobody:nothing") == 0 && said.contains("No raider culture nobody:nothing is loaded."), "an unknown culture: " + said);

			// Each disaster.
			int disasters = 0;
			for (Disaster disaster : List.copyOf(Disasters.all())) {
				disasters++;
				said.clear();
				helper.assertTrue(run(level, op, "workplace threat disaster " + disaster.id()) == 1 && disaster.id().equals(entity.disaster()),
					"no " + disaster.id() + ": " + said);
				helper.assertTrue(said.contains(Disasters.name(disaster).getString() + " has come to " + village + "."), "what the command said: " + said);
				said.clear();
				helper.assertTrue(run(level, op, "workplace threat disaster " + disaster.id()) == 0 && said.stream().anyMatch(s -> s.contains("already has a disaster under way")),
					"a second disaster: " + said);
				run(level, op, "workplace threat end");
				helper.assertTrue(entity.disaster() == null, disaster.id() + " didn't end");
			}
			helper.assertTrue(disasters >= 4, "expected our 3 disasters and the test blight, found " + disasters);
			said.clear();
			helper.assertTrue(run(level, op, "workplace threat end") == 0 && said.contains("Nothing is under way at " + village + "."), "end with nothing: " + said);
			said.clear();
			helper.assertTrue(run(level, op, "workplace threat disaster nobody:nothing") == 0 && said.contains("No disaster nobody:nothing is loaded."), "an unknown disaster: " + said);
			helper.succeed();
		});
	}

	/**
	 * On Peaceful (B704) {@code lair} says the world is Peaceful and sets nothing down (its band can't exist there), and
	 * a raid ordered there says the same instead of "found nowhere to gather".
	 */
	//$ gametest_ticks_batch AREA '60' '"threatCommandPeaceful"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "threatCommandPeaceful")
	public void theCommandSaysPeacefulOnPeaceful(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Difficulty before = level.getDifficulty();
		Leftovers.after(helper, () -> level.getServer().setDifficulty(before, true));
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			at(level, 82, 15000);
			CommandSourceStack op = op(level, hall, said);
			String village = VillageHalls.name(level, hall).getString();
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			int lair = run(level, op, "workplace threat lair bandits");
			int raid = run(level, op, "workplace threat start bandits");
			level.getServer().setDifficulty(before, true);
			helper.assertTrue(lair == 0 && Lairs.near(level, hall).isEmpty(), "a lair on Peaceful: " + lair + ", " + Lairs.near(level, hall));
			helper.assertTrue(said.contains("Bandits can't make camp by " + village
				+ ": the world is on Peaceful, where hostile mobs can't exist. Raise the difficulty for camps."), "the lair on Peaceful said: " + said);
			helper.assertTrue(raid == 0 && ThreatData.get(level).raid(hall).isEmpty(), "a raid on Peaceful: " + raid);
			helper.assertTrue(said.contains("Bandits can't come to " + village
				+ ": the world is on Peaceful, where hostile mobs can't exist. Raise the difficulty for raids."), "the raid on Peaceful said: " + said);
			helper.succeed();
		});
	}

	/**
	 * {@code lair} refuses a culture without one and a second lair; {@code clear} sends the lair away, ends the raid and
	 * takes the clock's attacks off; far from any hall the command says there's none.
	 */
	//$ gametest_ticks_batch AREA '60' '"threatCommandClear"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "threatCommandClear")
	public void theCommandClearsTheVillage(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			at(level, 82, 15000);
			CommandSourceStack op = op(level, hall, said);
			String village = VillageHalls.name(level, hall).getString();
			helper.assertTrue(run(level, op, "workplace threat lair monsters") == 0 && said.contains("Monsters have no lair: they come from nowhere in particular."),
				"a lair for the monsters: " + said);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			said.clear();
			helper.assertTrue(run(level, op, "workplace threat lair bandits") == 0
				&& said.contains(village + " already has a Bandit camp by it: /workplace threat clear first."), "a second lair: " + said);
			run(level, op, "workplace threat start bandits");
			long day = Chronicle.day(level);
			ThreatData.get(level).rolled(hall, day, new ThreatData.Attack(day + 1, Threats.MONSTERS, 0, 13500));
			said.clear();
			helper.assertTrue(run(level, op, "workplace threat clear") >= 3, "clear cleared less than the raid, the lair and the attack");
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && ThreatData.get(level).raid(hall).isEmpty() && ThreatData.get(level).attacks(hall).isEmpty()
				&& Lairs.band(level, lair).isEmpty(), "clear left something");
			helper.assertTrue(said.contains(village + " is clear: no raid, camp, disaster or attack on the clock."), "what clear said: " + said);
			said.clear();
			CommandSourceStack nowhere = op(level, hall.offset(4000, 0, 4000), said);
			helper.assertTrue(run(level, nowhere, "workplace threat start bandits") == 0 && said.contains("There's no Village Hall here."), "far from any hall: " + said);
			helper.succeed();
		});
	}

	/** Every new sentence a player reads has its words. */
	//$ gametest_batch EMPTY_STRUCTURE '"threatLevelWords"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "threatLevelWords")
	public void everyNewSentenceHasWords(GameTestHelper helper) {
		for (String key : List.of("aliveworkplace.config.threatLevel", "aliveworkplace.config.threatLevel.tooltip",
			"screen.aliveworkplace.defence.peace_on", "screen.aliveworkplace.defence.peace_off", "screen.aliveworkplace.defence.peace_what",
			"screen.aliveworkplace.defence.peace_since", "screen.aliveworkplace.defence.peace_click_on", "screen.aliveworkplace.defence.peace_click_off",
			"screen.aliveworkplace.defence.peace_owner", "screen.aliveworkplace.hall.at_peace", "screen.aliveworkplace.hall.threats_off",
			"screen.aliveworkplace.hall.threats_peaceful", "screen.aliveworkplace.hall.lair_leaves", "message.aliveworkplace.peace.on",
			"message.aliveworkplace.peace.on_lair", "message.aliveworkplace.peace.off", "message.aliveworkplace.peace.not_owner",
			"chronicle.aliveworkplace.peace_on", "chronicle.aliveworkplace.peace_off", "message.aliveworkplace.lair.left", "chronicle.aliveworkplace.lair.left",
			"message.aliveworkplace.raid.called_off", "command.aliveworkplace.threat.no_hall", "command.aliveworkplace.threat.unknown_culture",
			"command.aliveworkplace.threat.unknown_disaster", "command.aliveworkplace.threat.raid_under_way", "command.aliveworkplace.threat.start_failed",
			"command.aliveworkplace.threat.started", "command.aliveworkplace.threat.no_lair", "command.aliveworkplace.threat.lair_standing",
			"command.aliveworkplace.threat.lair_failed", "command.aliveworkplace.threat.lair_made", "command.aliveworkplace.threat.disaster_under_way",
			"command.aliveworkplace.threat.disaster_started", "command.aliveworkplace.threat.nothing", "command.aliveworkplace.threat.ended",
			"command.aliveworkplace.threat.cleared", "command.aliveworkplace.threat.peaceful_raid", "command.aliveworkplace.threat.peaceful_lair")) {
			helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
		}
		helper.assertTrue(Component.translatable("message.aliveworkplace.peace.on_lair", "Oakvale", Lairs.lairName(Threats.BANDITS)).getString()
			.equals("Oakvale is at peace: no raids, raiders' camps or disasters will come to it, and the Bandit camp leaves at the next dawn"),
			"the at peace line: " + Component.translatable("message.aliveworkplace.peace.on_lair", "Oakvale", Lairs.lairName(Threats.BANDITS)).getString());
		helper.assertTrue(Component.translatable("command.aliveworkplace.threat.lair_made", Lairs.lairName(Threats.BANDITS), "Oakvale", 1, 64, -3).getString()
			.equals("The Bandit camp now stands by Oakvale, at 1, 64, -3."), "the lair line");
		helper.succeed();
	}
}
