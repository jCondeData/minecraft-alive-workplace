package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;

/**
 * QA (lane B, 2026-11-14) of the builders' config switches and game rules, written from {@code docs/wiki/config-switches.md}
 * and not from the code. Earlier passes (QaBuilderSwitches*, QaBuilderSwitchEdges, QaBuilderConfig1102, QaBuilderRules1109,
 * QaConfigWiring, FarSite, WonderCrew) already turn most switches in a running build; the gaps this class closes are the
 * guide's own tables: each rule's default and name as an operator types it, the rules and switches coming back after a
 * world save or a server restart, the numeric switches at both ends of their documented ranges and one past each end as the
 * game applies them, and the words the Edit Game Rules screen shows for each builder rule.
 *
 * <p>Every test here changes shared state (the world's game rules, the applied config) only inside its own tick and puts it
 * back before returning, so tests running beside it never see a changed rule.
 */
public class QaBuilderSwitches1114bGameTests implements FabricGameTest {

	/** The guide's game rule table: the builders' eight rules and their defaults. */
	private static final Map<String, Object> GUIDE_DEFAULTS = guideDefaults();

	private static Map<String, Object> guideDefaults() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("workplaceFreeMaterials", false);
		m.put("workplaceBuildDelay", 8);
		m.put("workplaceAllowUploads", true);
		m.put("workplaceFoundationDepth", 12);
		m.put("workplaceBuildersHelp", true);
		m.put("workplaceBuilderOwnership", true);
		m.put("workplaceKeepWorkLoaded", true);
		m.put("workplaceLevelGround", 2);
		return m;
	}

	// ------------------------------------------------------------------------------------------------- game rules

	/** A new world's builder rules are the guide's defaults: false, 8, true, 12, true, true, true, 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aNewWorldsBuilderRulesAreTheGuidesDefaults(GameTestHelper helper) {
		GameRules fresh = new GameRules();
		List<String> wrong = new ArrayList<>();
		expect(wrong, "workplaceFreeMaterials", fresh.getBoolean(ModGameRules.FREE_MATERIALS));
		expect(wrong, "workplaceBuildDelay", fresh.getInt(ModGameRules.BUILD_DELAY));
		expect(wrong, "workplaceAllowUploads", fresh.getBoolean(ModGameRules.ALLOW_UPLOADS));
		expect(wrong, "workplaceFoundationDepth", fresh.getInt(ModGameRules.FOUNDATION_DEPTH));
		expect(wrong, "workplaceBuildersHelp", fresh.getBoolean(ModGameRules.BUILDERS_HELP));
		expect(wrong, "workplaceBuilderOwnership", fresh.getBoolean(ModGameRules.BUILDER_OWNERSHIP));
		expect(wrong, "workplaceKeepWorkLoaded", fresh.getBoolean(ModGameRules.KEEP_WORK_LOADED));
		expect(wrong, "workplaceLevelGround", fresh.getInt(ModGameRules.LEVEL_GROUND));
		helper.assertTrue(wrong.isEmpty(), "a new world's builder rules differ from config-switches.md: " + wrong);
		helper.succeed();
	}

	private static void expect(List<String> wrong, String name, Object actual) {
		Object want = GUIDE_DEFAULTS.get(name);
		if (!want.equals(actual)) {
			wrong.add(name + " is " + actual + ", the guide says " + want);
		}
	}

	/**
	 * Each rule name in the guide's table works as an operator types it: {@code /gamerule <name> <value>} turns every
	 * boolean rule both ways, and the numeric rules take a value inside their range, including the large end of the open
	 * ranges ("1 or more", "0 or more": 2147483647). Everything is put back in the same tick.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa1114bGameruleNames"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa1114bGameruleNames")
	public void everyBuilderRuleNameInTheGuideWorksWithTheGameruleCommand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules saved = level.getGameRules().copy();
		List<String> wrong = new ArrayList<>();
		Map<String, GameRules.Key<GameRules.BooleanValue>> switches = new LinkedHashMap<>();
		switches.put("workplaceFreeMaterials", ModGameRules.FREE_MATERIALS);
		switches.put("workplaceAllowUploads", ModGameRules.ALLOW_UPLOADS);
		switches.put("workplaceBuildersHelp", ModGameRules.BUILDERS_HELP);
		switches.put("workplaceBuilderOwnership", ModGameRules.BUILDER_OWNERSHIP);
		switches.put("workplaceKeepWorkLoaded", ModGameRules.KEEP_WORK_LOADED);
		try {
			for (Map.Entry<String, GameRules.Key<GameRules.BooleanValue>> e : switches.entrySet()) {
				for (boolean value : new boolean[] {!level.getGameRules().getBoolean(e.getValue()), true, false}) {
					gamerule(level, e.getKey() + " " + value);
					if (level.getGameRules().getBoolean(e.getValue()) != value) {
						wrong.add("/gamerule " + e.getKey() + " " + value + " left it " + level.getGameRules().getBoolean(e.getValue()));
					}
				}
			}
			intRule(level, wrong, "workplaceBuildDelay", ModGameRules.BUILD_DELAY, 3);
			intRule(level, wrong, "workplaceBuildDelay", ModGameRules.BUILD_DELAY, Integer.MAX_VALUE);
			intRule(level, wrong, "workplaceFoundationDepth", ModGameRules.FOUNDATION_DEPTH, 5);
			intRule(level, wrong, "workplaceFoundationDepth", ModGameRules.FOUNDATION_DEPTH, Integer.MAX_VALUE);
			intRule(level, wrong, "workplaceLevelGround", ModGameRules.LEVEL_GROUND, 5);
		} finally {
			level.getGameRules().assignFrom(saved, level.getServer());
		}
		helper.assertTrue(level.getGameRules().getInt(ModGameRules.BUILD_DELAY) == saved.getInt(ModGameRules.BUILD_DELAY)
			&& level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS) == saved.getBoolean(ModGameRules.FREE_MATERIALS), "the rules weren't put back");
		helper.assertTrue(wrong.isEmpty(), "the guide's rule names don't all work with /gamerule: " + wrong);
		helper.succeed();
	}

	private static void intRule(ServerLevel level, List<String> wrong, String name, GameRules.Key<GameRules.IntegerValue> key, int value) {
		gamerule(level, name + " " + value);
		if (level.getGameRules().getInt(key) != value) {
			wrong.add("/gamerule " + name + " " + value + " left it " + level.getGameRules().getInt(key));
		}
	}

	private static void gamerule(ServerLevel level, String args) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), "gamerule " + args);
	}

	/**
	 * "Game rules are saved by vanilla with each world": every builder rule set away from its default comes back the same
	 * when the world's rules are written to level.dat and read again (as at a server restart).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyBuilderRuleComesBackAfterAWorldSaveAndReload(GameTestHelper helper) {
		GameRules rules = new GameRules();
		rules.getRule(ModGameRules.FREE_MATERIALS).set(true, null);
		rules.getRule(ModGameRules.BUILD_DELAY).set(3, null);
		rules.getRule(ModGameRules.ALLOW_UPLOADS).set(false, null);
		rules.getRule(ModGameRules.FOUNDATION_DEPTH).set(0, null);
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, null);
		rules.getRule(ModGameRules.BUILDER_OWNERSHIP).set(false, null);
		rules.getRule(ModGameRules.KEEP_WORK_LOADED).set(false, null);
		rules.getRule(ModGameRules.LEVEL_GROUND).set(8, null);
		CompoundTag saved = rules.createTag();
		GameRules loaded = new GameRules(new Dynamic<>(NbtOps.INSTANCE, saved));
		List<String> wrong = new ArrayList<>();
		check(wrong, "workplaceFreeMaterials", true, loaded.getBoolean(ModGameRules.FREE_MATERIALS));
		check(wrong, "workplaceBuildDelay", 3, loaded.getInt(ModGameRules.BUILD_DELAY));
		check(wrong, "workplaceAllowUploads", false, loaded.getBoolean(ModGameRules.ALLOW_UPLOADS));
		check(wrong, "workplaceFoundationDepth", 0, loaded.getInt(ModGameRules.FOUNDATION_DEPTH));
		check(wrong, "workplaceBuildersHelp", false, loaded.getBoolean(ModGameRules.BUILDERS_HELP));
		check(wrong, "workplaceBuilderOwnership", false, loaded.getBoolean(ModGameRules.BUILDER_OWNERSHIP));
		check(wrong, "workplaceKeepWorkLoaded", false, loaded.getBoolean(ModGameRules.KEEP_WORK_LOADED));
		check(wrong, "workplaceLevelGround", 8, loaded.getInt(ModGameRules.LEVEL_GROUND));
		helper.assertTrue(wrong.isEmpty(), "builder rules lost in a world save and reload: " + wrong);
		helper.succeed();
	}

	private static void check(List<String> wrong, String name, Object want, Object got) {
		if (!want.equals(got)) {
			wrong.add(name + " saved as " + want + " came back " + got);
		}
	}

	/**
	 * A level.dat edited by hand (or written by a mod version with other limits) holding a numeric builder rule outside the
	 * guide's range (workplaceLevelGround 50 or -1, workplaceBuildDelay 0, workplaceFoundationDepth -3) loads with the rule
	 * inside its range: 0 to 8, 1 or more, 0 or more. A value inside the range is kept.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aWorldFileWithABuilderRuleOutOfRangeLoadsItInsideTheRange(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String[] odd : new String[][] {{"50", "0", "-3"}, {"-1", "-8", "-1"}, {"9", "-2147483648", "-2147483648"}}) {
			CompoundTag tag = new GameRules().createTag();
			tag.putString("workplaceLevelGround", odd[0]);
			tag.putString("workplaceBuildDelay", odd[1]);
			tag.putString("workplaceFoundationDepth", odd[2]);
			GameRules loaded = new GameRules(new Dynamic<>(NbtOps.INSTANCE, tag));
			int ground = loaded.getInt(ModGameRules.LEVEL_GROUND);
			int delay = loaded.getInt(ModGameRules.BUILD_DELAY);
			int depth = loaded.getInt(ModGameRules.FOUNDATION_DEPTH);
			if (ground < 0 || ground > 8) {
				wrong.add("workplaceLevelGround " + odd[0] + " loaded as " + ground);
			}
			if (delay < 1) {
				wrong.add("workplaceBuildDelay " + odd[1] + " loaded as " + delay);
			}
			if (depth < 0) {
				wrong.add("workplaceFoundationDepth " + odd[2] + " loaded as " + depth);
			}
		}
		CompoundTag inside = new GameRules().createTag();
		inside.putString("workplaceLevelGround", "8");
		inside.putString("workplaceBuildDelay", "1");
		inside.putString("workplaceFoundationDepth", "0");
		GameRules kept = new GameRules(new Dynamic<>(NbtOps.INSTANCE, inside));
		check(wrong, "workplaceLevelGround", 8, kept.getInt(ModGameRules.LEVEL_GROUND));
		check(wrong, "workplaceBuildDelay", 1, kept.getInt(ModGameRules.BUILD_DELAY));
		check(wrong, "workplaceFoundationDepth", 0, kept.getInt(ModGameRules.FOUNDATION_DEPTH));
		helper.assertTrue(wrong.isEmpty(), "a world file's builder rule outside the guide's range: " + wrong);
		helper.succeed();
	}

	/**
	 * The Edit Game Rules screen shows each builder rule in words: every one of the guide's eight builder rules has its
	 * {@code gamerule.<name>} line in en_us.json (the guide: "the game rules' as gamerule.workplaceBuildDelay"), in
	 * American English, naming the mod.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyBuilderRuleHasItsWordsOnTheGameRulesScreen(GameTestHelper helper) {
		JsonObject lang = lang();
		List<String> wrong = new ArrayList<>();
		for (String name : GUIDE_DEFAULTS.keySet()) {
			String key = "gamerule." + name;
			if (!lang.has(key)) {
				wrong.add(key + " is missing");
				continue;
			}
			String text = lang.get(key).getAsString();
			if (!text.startsWith("Alive Workplace: ") || text.length() < 25) {
				wrong.add(key + " reads \"" + text + "\"");
			}
			for (String british : new String[] {"colour", "defence", "levelled", "levelling", "neighbour"}) {
				if (text.toLowerCase(java.util.Locale.ROOT).contains(british)) {
					wrong.add(key + " uses British \"" + british + "\": " + text);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the game rules screen's words for the builder rules: " + wrong);
		helper.succeed();
	}

	static JsonObject lang() {
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
			.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
	}

	// ------------------------------------------------------------------------------------------------- config switches

	/**
	 * A server with no config file gets the guide's defaults for the builders' switches: maxSiteDistance 128, builderRepairs,
	 * repairAsk and blueprintUnlocks on, builderScaffolding on, wonderCrew 12, furnishedBuilds on (its expansions are open
	 * in GameTests), and the file written for the owner says so.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noConfigFileGivesTheGuidesBuilderDefaults(GameTestHelper helper) {
		Path dir = tempDir();
		WorkplaceConfig config = WorkplaceConfig.load(dir);
		List<String> wrong = builderSwitches(config, 128, true, true, true, true, 12, true);
		config.save(dir);
		String file = read(dir.resolve(WorkplaceConfig.FILE));
		for (String key : new String[] {"maxSiteDistance", "builderRepairs", "repairAsk", "blueprintUnlocks", "builderScaffolding", "wonderCrew",
			"furnishedBuilds"}) {
			if (!file.contains("\"" + key + "\"")) {
				wrong.add("the written file has no " + key);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "with no config file the builder switches aren't the guide's defaults: " + wrong);
		helper.succeed();
	}

	/**
	 * An owner who switches every builder switch off on the settings screen (and the reach and crew to their least) keeps
	 * that through two restarts: saved, read and written back at a start, read again at the next. None of them is taken for
	 * a closed expansion's "old default" and switched back on.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void builderSwitchesTurnedOffStayOffThroughTwoRestarts(GameTestHelper helper) {
		Path dir = tempDir();
		WorkplaceConfig config = WorkplaceConfig.load(dir);
		for (String key : new String[] {"builderRepairs", "repairAsk", "blueprintUnlocks", "builderScaffolding", "furnishedBuilds"}) {
			config.setBoolean(key, false);
		}
		config.setInt("maxSiteDistance", 16);
		config.setInt("wonderCrew", 1);
		config.save(dir);
		WorkplaceConfig first = WorkplaceConfig.load(dir);
		List<String> wrong = new ArrayList<>(prefixed("first restart: ", builderSwitches(first, 16, false, false, false, false, 1, false)));
		first.save(dir); // a start writes the file back complete
		WorkplaceConfig second = WorkplaceConfig.load(dir);
		wrong.addAll(prefixed("second restart: ", builderSwitches(second, 16, false, false, false, false, 1, false)));
		// and switched on again, they come back on
		for (String key : new String[] {"builderRepairs", "repairAsk", "blueprintUnlocks", "builderScaffolding", "furnishedBuilds"}) {
			second.setBoolean(key, true);
		}
		second.setInt("maxSiteDistance", 256);
		second.setInt("wonderCrew", 32);
		second.save(dir);
		wrong.addAll(prefixed("on again: ", builderSwitches(WorkplaceConfig.load(dir), 256, true, true, true, true, 32, true)));
		helper.assertTrue(wrong.isEmpty(), "builder switches lost across restarts: " + wrong);
		helper.succeed();
	}

	/**
	 * maxSiteDistance (16 to 256) and wonderCrew (1 to 32) at each end and one past it, as the game applies the file: the
	 * builder's reach and the Wonder crew limit are 16 and 256, 1 and 32 at the ends, and one past an end gives that end.
	 * The settings screen's way (set, saved, read at the next start) holds them to the same ends.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa1114bReachCrewEnds"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa1114bReachCrewEnds")
	public void reachAndWonderCrewAtEachEndAndOnePastReachTheGameInRange(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int[][] cases = {{16, 1, 16, 1}, {15, 0, 16, 1}, {256, 32, 256, 32}, {257, 33, 256, 32}, {17, 2, 17, 2}, {255, 31, 255, 31}};
		try {
			for (int[] c : cases) {
				WorkplaceConfig.parse("{\"configVersion\": 5, \"maxSiteDistance\": " + c[0] + ", \"wonderCrew\": " + c[1] + "}").apply();
				if (Builders.MAX_SITE_DISTANCE != c[2]) {
					wrong.add("maxSiteDistance " + c[0] + " gave the builder " + Builders.MAX_SITE_DISTANCE + ", not " + c[2]);
				}
				if (WonderCrews.LIMIT != c[3]) {
					wrong.add("wonderCrew " + c[1] + " gave the crew limit " + WonderCrews.LIMIT + ", not " + c[3]);
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		Path dir = tempDir();
		WorkplaceConfig screen = WorkplaceConfig.load(dir);
		screen.setInt("maxSiteDistance", 257);
		screen.setInt("wonderCrew", 33);
		screen.save(dir);
		WorkplaceConfig next = WorkplaceConfig.load(dir);
		if (next.maxSiteDistance != 256 || next.wonderCrew != 32) {
			wrong.add("the screen's 257 and 33 came back as " + next.maxSiteDistance + " and " + next.wonderCrew);
		}
		screen.setInt("maxSiteDistance", 15);
		screen.setInt("wonderCrew", 0);
		screen.save(dir);
		next = WorkplaceConfig.load(dir);
		if (next.maxSiteDistance != 16 || next.wonderCrew != 1) {
			wrong.add("the screen's 15 and 0 came back as " + next.maxSiteDistance + " and " + next.wonderCrew);
		}
		helper.assertTrue(Builders.MAX_SITE_DISTANCE == 128 && WonderCrews.LIMIT == 12, "the defaults weren't put back: "
			+ Builders.MAX_SITE_DISTANCE + "/" + WonderCrews.LIMIT);
		helper.assertTrue(wrong.isEmpty(), "reach and crew at their ends: " + wrong);
		helper.succeed();
	}

	/**
	 * Applying the file reaches the builders: repairAsk off makes them repair without asking, builderScaffolding off stops
	 * scaffolding, furnishedBuilds off gives vanilla builds; each back on when the file says on again. (builderRepairs and
	 * blueprintUnlocks are held off in every GameTest by design, so their applying isn't checkable here.)
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa1114bSwitchesApplied"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa1114bSwitchesApplied")
	public void builderSwitchesInTheFileReachTheBuildersBothWays(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		boolean ask = Upkeep.ASK;
		boolean scaffolds = Scaffolds.ENABLED;
		boolean furnish = Furnishings.ENABLED;
		try {
			WorkplaceConfig.parse("{\"configVersion\": 5, \"repairAsk\": false, \"builderScaffolding\": false, \"furnishedBuilds\": false}").apply();
			if (Upkeep.ASK) {
				wrong.add("repairAsk false left builders asking");
			}
			if (Scaffolds.ENABLED) {
				wrong.add("builderScaffolding false left scaffolding on");
			}
			if (Furnishings.ENABLED) {
				wrong.add("furnishedBuilds false left furnished builds on");
			}
			WorkplaceConfig.parse("{\"configVersion\": 5, \"repairAsk\": true, \"builderScaffolding\": true, \"furnishedBuilds\": true}").apply();
			if (!Upkeep.ASK) {
				wrong.add("repairAsk true didn't bring the question back");
			}
			if (!Scaffolds.ENABLED) {
				wrong.add("builderScaffolding true didn't bring scaffolding back");
			}
			if (!Furnishings.ENABLED) {
				wrong.add("furnishedBuilds true didn't bring furnished builds back");
			}
		} finally {
			new WorkplaceConfig().apply();
			Upkeep.ASK = ask;
			Scaffolds.ENABLED = scaffolds;
			Furnishings.ENABLED = furnish;
		}
		helper.assertTrue(wrong.isEmpty(), "builder switches applied from the file: " + wrong);
		helper.succeed();
	}

	/**
	 * wonderCrew at its least, 1 ("the most builders on one Wonder's crew, its lead included"), set in the config file: with
	 * three idle builders in the town the lead works the Wonder alone on every tick for 600 ticks; the file changed to 2,
	 * exactly one of the others joins him and the crew never passes 2.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:tall_area"' '3000' '"qa1114bCrewOfOne"'
	@GameTest(template = "aliveworkplace_test:tall_area", timeoutTicks = 3000, batch = "qa1114bCrewOfOne")
	public void aWonderCrewOfOneIsTheLeadAloneAndTwoTakesExactlyOneMore(GameTestHelper helper) {
		QaWonderCrewGameTests.Village v = QaWonderCrewGameTests.village(helper, 3, 4);
		ServerLevel level = v.level();
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		WorkplaceConfig.parse("{\"configVersion\": 5, \"wonderCrew\": 1}").apply();
		helper.assertTrue(WonderCrews.LIMIT == 1, "wonderCrew 1 in the file gave a limit of " + WonderCrews.LIMIT);
		Villager lead = v.builders().get(0);
		WonderSite begun = QaWonderCrewGameTests.begin(helper, v, lead);
		java.util.UUID id = begun.id();
		long start = level.getGameTime();
		java.util.concurrent.atomic.AtomicInteger aloneTicks = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicInteger after = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicLong raisedAt = new java.util.concurrent.atomic.AtomicLong(-1);
		helper.onEachTick(() -> {
			WonderSite now = QaWonderCrewGameTests.now(level, id);
			if (now == null || now.done()) {
				return;
			}
			java.util.Collection<?> crew = WonderCrews.crew(level, now);
			if (raisedAt.get() < 0) {
				helper.assertTrue(crew.size() <= 1, "a crew of " + crew.size() + " with wonderCrew 1 at tick " + (level.getGameTime() - start));
				if (crew.size() == 1) {
					aloneTicks.incrementAndGet();
				}
				if (level.getGameTime() - start >= 600) {
					helper.assertTrue(aloneTicks.get() > 0, "with wonderCrew 1 the lead never worked the Wonder");
					WorkplaceConfig.parse("{\"configVersion\": 5, \"wonderCrew\": 2}").apply();
					raisedAt.set(level.getGameTime());
				}
			} else {
				helper.assertTrue(crew.size() <= 2, "a crew of " + crew.size() + " with wonderCrew 2");
				after.set(Math.max(after.get(), crew.size()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raisedAt.get() >= 0, "not yet raised");
			helper.assertTrue(after.get() == 2, "raised to 2, the crew is at most " + after.get());
			helper.assertTrue(level.getGameTime() - raisedAt.get() >= 200, "watching the crew of 2 for 200 ticks");
		});
	}

	// ------------------------------------------------------------------------------------------------- helpers

	private static List<String> builderSwitches(WorkplaceConfig c, int reach, boolean repairs, boolean ask, boolean unlocks, boolean scaffolding,
		int crew, boolean furnished) {
		List<String> wrong = new ArrayList<>();
		if (c.maxSiteDistance != reach) {
			wrong.add("maxSiteDistance " + c.maxSiteDistance + ", not " + reach);
		}
		if (c.builderRepairs != repairs) {
			wrong.add("builderRepairs " + c.builderRepairs);
		}
		if (c.repairAsk != ask) {
			wrong.add("repairAsk " + c.repairAsk);
		}
		if (c.blueprintUnlocks != unlocks) {
			wrong.add("blueprintUnlocks " + c.blueprintUnlocks);
		}
		if (c.builderScaffolding != scaffolding) {
			wrong.add("builderScaffolding " + c.builderScaffolding);
		}
		if (c.wonderCrew != crew) {
			wrong.add("wonderCrew " + c.wonderCrew + ", not " + crew);
		}
		if (c.furnishedBuilds != furnished) {
			wrong.add("furnishedBuilds " + c.furnishedBuilds);
		}
		return wrong;
	}

	private static List<String> prefixed(String prefix, List<String> lines) {
		return lines.stream().map(l -> prefix + l).toList();
	}

	private static Path tempDir() {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-qa1114b");
			dir.toFile().deleteOnExit();
			return dir;
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new GameTestAssertException("can't read " + file + ": " + e);
		}
	}
}
