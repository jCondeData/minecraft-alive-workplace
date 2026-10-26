package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA for ROADMAP 35.2 (Wonder data, requirements and caps), written from the spec: every cap boundary from both sides
 * (a City's second allowed and its third refused, one going up per village and not per server, a site still going up
 * counts against {@code wonderServerLimit}, a cancelled one frees it), the config keys reaching the refusals, each kind
 * of file mistake skipped with its path and reason, the Master Architect's stand-in (a level-4 builder, a Master of
 * another trade and a Master builder outside the village are no stand-in), {@code coastal} at 48 blocks, and every
 * refusal and {@code /workplace wonders} line rendered in English with its real arguments.
 */
public class QaWonderRulesGameTests implements FabricGameTest {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation ONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_folly_one");
	private static final ResourceLocation TWO = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_folly_two");
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_folly_three");
	private static final ResourceLocation GONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_gone");

	// ---------------------------------------------------------------- helpers

	/** The test folly and three copies of it under ids no other test uses (so no other test's sites count). */
	private static Map<ResourceLocation, Wonder> withCopies(GameTestHelper helper) {
		Map<ResourceLocation, Wonder> map = new LinkedHashMap<>(Wonders.loaded());
		Wonder folly = map.get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		for (ResourceLocation id : List.of(ONE, TWO, THREE)) {
			map.put(id, new Wonder(id, folly.name(), folly.description(), folly.icon(), true, folly.crew(), folly.requirements(), folly.stages(), folly.powers()));
		}
		return map;
	}

	/** Removes every site this class's ids hold anywhere, and every site at {@code halls} (an earlier batch's, same spot). */
	private static void forgetSites(ServerLevel level, BlockPos... halls) {
		for (WonderManager.Located l : WonderManager.everywhere(level.getServer())) {
			boolean ours = Set.of(ONE, TWO, THREE, GONE).contains(l.site().wonder());
			for (BlockPos h : halls) {
				ours |= l.level() == level && l.site().hall().equals(h);
			}
			if (ours) {
				WonderManager.get(l.level()).remove(l.site().id());
			}
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at, VillageRanks.Rank rank) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(at);
		entity.setRank(rank);
		return entity;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** Every translatable in {@code c} (arguments too) has an English line. */
	private static void allTranslated(GameTestHelper helper, Component c, String what) {
		if (c.getContents() instanceof TranslatableContents t) {
			helper.assertTrue(Language.getInstance().has(t.getKey()), what + ": no en_us line for " + t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component inner) {
					allTranslated(helper, inner, what);
				}
			}
		}
		for (Component sibling : c.getSiblings()) {
			allTranslated(helper, sibling, what);
		}
	}

	/** The refusal is there, reads exactly {@code english}, and every key in it is a real lang line. */
	private static void reads(GameTestHelper helper, Optional<Component> refusal, String english, String what) {
		helper.assertTrue(refusal.isPresent(), what + ": not refused (expected \"" + english + "\")");
		allTranslated(helper, refusal.get(), what);
		String text = refusal.get().getString();
		helper.assertTrue(text.equals(english), what + ": reads \"" + text + "\", expected \"" + english + "\"");
	}

	private static void allowed(GameTestHelper helper, Optional<Component> refusal, String what) {
		helper.assertTrue(refusal.isEmpty(), what + ": refused with \"" + refusal.map(Component::getString).orElse("") + "\"");
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	// ---------------------------------------------------------------- the caps by rank

	/**
	 * Each rank's cap from both sides: a Hamlet and a Village none (the cap is said before any requirement), a Town its
	 * first and not its second, a City its second and not its third; the same kind twice is refused even in a City.
	 */
	//$ gametest_batch SMALL '"qaWonderCaps"'
	@GameTest(template = SMALL, batch = "qaWonderCaps")
	public void aCityTakesTwoAndRefusesAThird(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(8, 2, 8);
		BlockPos hall = helper.absolutePos(at);
		forgetSites(level, hall);
		VillageHallBlockEntity entity = hall(helper, at, VillageRanks.Rank.HAMLET);
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		WonderManager manager = WonderManager.get(level);
		Wonders.setForTest(withCopies(helper));
		try {
			reads(helper, Wonders.capRefusal(level, hall, ONE), "Wonders: 0 of 0 for a Hamlet.", "a Hamlet");
			reads(helper, Wonders.startRefusal(level, hall, ONE), "Wonders: 0 of 0 for a Hamlet.", "a Hamlet's start (the cap before the requirements)");
			entity.setRank(VillageRanks.Rank.VILLAGE);
			reads(helper, Wonders.capRefusal(level, hall, ONE), "Wonders: 0 of 0 for a Village.", "a Village");
			entity.setRank(VillageRanks.Rank.TOWN);
			allowed(helper, Wonders.capRefusal(level, hall, ONE), "a Town's first");
			WonderSite first = manager.begin(ONE, hall, null, "", 1);
			manager.finish(first.id(), 2);
			reads(helper, Wonders.capRefusal(level, hall, TWO), "Wonders: 1 of 1 for a Town.", "a Town's second");
			entity.setRank(VillageRanks.Rank.CITY);
			allowed(helper, Wonders.capRefusal(level, hall, TWO), "a City's second");
			WonderSite second = manager.begin(TWO, hall, null, "", 3);
			manager.finish(second.id(), 4);
			reads(helper, Wonders.capRefusal(level, hall, THREE), "Wonders: 2 of 2 for a City.", "a City's third");
			reads(helper, Wonders.capRefusal(level, hall, ONE), "This village already has the Test Folly.", "the same kind twice in a City");
			Component line = Wonders.capLine(level, hall);
			allTranslated(helper, line, "the cap line");
			helper.assertTrue(line.getString().equals("Wonders: 2 of 2 for a City"), "the cap line reads: " + line.getString());
			// a City that lost its rank keeps its Wonders but may not start more
			entity.setRank(VillageRanks.Rank.TOWN);
			reads(helper, Wonders.capRefusal(level, hall, THREE), "Wonders: 2 of 1 for a Town.", "a Town holding two");
			AliveWorkplace.LOG.info("[test] qa wonder caps: Hamlet 0, Village 0, Town 1, City 2, same kind refused");
		} finally {
			Wonders.setForTest(before);
			forgetSites(level, hall);
		}
		helper.succeed();
	}

	/**
	 * One going up at a time is per village: while one rises a City may not start another, a second village beside it
	 * may, and once it's finished the City may too.
	 */
	//$ gametest_batch SMALL '"qaWonderRising"'
	@GameTest(template = SMALL, batch = "qaWonderRising")
	public void oneGoingUpIsPerVillageNotPerServer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos b = helper.absolutePos(new BlockPos(14, 2, 14));
		forgetSites(level, a, b);
		hall(helper, new BlockPos(2, 2, 2), VillageRanks.Rank.CITY);
		hall(helper, new BlockPos(14, 2, 14), VillageRanks.Rank.CITY);
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		WonderManager manager = WonderManager.get(level);
		Wonders.setForTest(withCopies(helper));
		try {
			WonderSite rising = manager.begin(ONE, a, null, "", 1);
			manager.setStage(rising.id(), 2);
			reads(helper, Wonders.capRefusal(level, a, TWO), "The Test Folly is still going up: one Wonder at a time.", "a second while one rises");
			allowed(helper, Wonders.capRefusal(level, b, TWO), "another village while the first village's rises");
			allowed(helper, Wonders.capRefusal(level, b, ONE), "another village, the same kind (no server limit)");
			manager.finish(rising.id(), 5);
			allowed(helper, Wonders.capRefusal(level, a, TWO), "a City's second once the first is finished");
		} finally {
			Wonders.setForTest(before);
			forgetSites(level, a, b);
		}
		helper.succeed();
	}

	/**
	 * {@code wonderServerLimit}: a site still going up holds its kind's slot (1 refuses a second village), 2 lets it,
	 * 0 is no limit, and a site that's removed (given up) frees the slot.
	 */
	//$ gametest_batch SMALL '"qaWonderLimit"'
	@GameTest(template = SMALL, batch = "qaWonderLimit")
	public void theServerLimitCountsOneStillGoingUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos b = helper.absolutePos(new BlockPos(14, 2, 14));
		forgetSites(level, a, b);
		hall(helper, new BlockPos(2, 2, 2), VillageRanks.Rank.TOWN);
		hall(helper, new BlockPos(14, 2, 14), VillageRanks.Rank.TOWN);
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		WonderManager manager = WonderManager.get(level);
		int limit = Wonders.SERVER_LIMIT;
		Wonders.setForTest(withCopies(helper));
		try {
			WonderSite rising = manager.begin(ONE, a, null, "", 1);
			Wonders.SERVER_LIMIT = 1;
			reads(helper, Wonders.capRefusal(level, b, ONE), "The Test Folly may only be built in 1 village on this server, and it's taken.",
				"limit 1, another village's still going up");
			allowed(helper, Wonders.capRefusal(level, b, TWO), "limit 1 is per kind");
			Wonders.SERVER_LIMIT = 2;
			allowed(helper, Wonders.capRefusal(level, b, ONE), "limit 2, one taken");
			Wonders.SERVER_LIMIT = 0;
			allowed(helper, Wonders.capRefusal(level, b, ONE), "limit 0 (no limit)");
			Wonders.SERVER_LIMIT = 1;
			manager.remove(rising.id());
			allowed(helper, Wonders.capRefusal(level, b, ONE), "limit 1 after the only site was removed");
		} finally {
			Wonders.SERVER_LIMIT = limit;
			Wonders.setForTest(before);
			forgetSites(level, a, b);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the config

	/**
	 * The config file's keys reach the refusals players read: {@code wonderCaps} gives a Village one and takes a Town's,
	 * {@code wonderServerLimit}, {@code disabledWonders} (that kind only), and {@code wonders: false} refuses everything
	 * and empties {@code /workplace wonders}; a spot without a hall says so.
	 */
	//$ gametest_batch SMALL '"qaWonderConfig"'
	@GameTest(template = SMALL, batch = "qaWonderConfig")
	public void theConfigKeysReachTheRefusals(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(8, 2, 8);
		BlockPos hall = helper.absolutePos(at);
		forgetSites(level, hall);
		VillageHallBlockEntity entity = hall(helper, at, VillageRanks.Rank.VILLAGE);
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		Wonders.setForTest(withCopies(helper));
		try {
			WorkplaceConfig.parse("{\"wonderCaps\": [0, 1, 1, 2]}").apply();
			allowed(helper, Wonders.capRefusal(level, hall, ONE), "wonderCaps [0, 1, 1, 2]: a Village's first");
			WorkplaceConfig.parse("{\"wonderCaps\": [0, 0, 0, 2]}").apply();
			entity.setRank(VillageRanks.Rank.TOWN);
			reads(helper, Wonders.capRefusal(level, hall, ONE), "Wonders: 0 of 0 for a Town.", "wonderCaps [0, 0, 0, 2]: a Town");
			WorkplaceConfig.parse("{\"wonderServerLimit\": 1}").apply();
			helper.assertTrue(Wonders.SERVER_LIMIT == 1 && Wonders.cap(VillageRanks.Rank.TOWN) == 1, "wonderServerLimit 1 (and the caps back to default): "
				+ Wonders.SERVER_LIMIT + ", Town " + Wonders.cap(VillageRanks.Rank.TOWN));
			WorkplaceConfig.parse("{\"disabledWonders\": [\"aliveworkplace_test:qa_folly_one\"]}").apply();
			reads(helper, Wonders.capRefusal(level, hall, ONE), "There's no Wonder aliveworkplace_test:qa_folly_one on this server.", "a disabled Wonder");
			allowed(helper, Wonders.capRefusal(level, hall, TWO), "a Wonder beside the disabled one");
			helper.assertTrue(Wonders.all().stream().noneMatch(w -> w.id().equals(ONE)) && Wonders.all().stream().anyMatch(w -> w.id().equals(TWO)),
				"the disabled one listed, or the others gone");
			WorkplaceConfig.parse("{\"wonders\": false}").apply();
			reads(helper, Wonders.capRefusal(level, hall, TWO), "Wonders are switched off on this server.", "wonders: false");
			List<Component> said = run(helper, "workplace wonders");
			helper.assertTrue(said.size() == 1 && said.get(0).getString().equals("Wonders are off."), "/workplace wonders while off: "
				+ said.stream().map(Component::getString).toList());
			WorkplaceConfig.parse("{\"wonders\": true}").apply();
			reads(helper, Wonders.capRefusal(level, helper.absolutePos(new BlockPos(1, 2, 1)), TWO), "A Wonder needs a Village Hall.", "no hall");
		} finally {
			new WorkplaceConfig().apply();
			Wonders.setForTest(before);
			forgetSites(level, hall);
		}
		helper.assertTrue(Wonders.ENABLED && Wonders.SERVER_LIMIT == 0 && Wonders.DISABLED.isEmpty() && Wonders.cap(VillageRanks.Rank.CITY) == 2, "the config wasn't reset");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the files

	/** A good Wonder file, as a server owner would write one. */
	private static final String GOOD = WonderDataGameTests.GOOD;

	/**
	 * Each kind of mistake a server owner makes is skipped with its own path and a reason naming it, and the good files
	 * beside them still load: crew 1 and 32 load, 0 and 33 don't; a switched-off file is skipped quietly even when the
	 * rest of it is broken; a file in a subfolder loads under its folder's id; a file that isn't {@code .json} is ignored.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWonderFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWonderFiles")
	public void everyKindOfMistakeIsSkippedWithItsPath(GameTestHelper helper) {
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		files.put(ResourceLocation.parse("qa_pack:wonders/good.json"), GOOD);
		files.put(ResourceLocation.parse("qa_pack:wonders/crew_one.json"), GOOD.replaceFirst("\\{", "{\"crew\": 1, "));
		files.put(ResourceLocation.parse("qa_pack:wonders/crew_max.json"), GOOD.replaceFirst("\\{", "{\"crew\": 32, "));
		files.put(ResourceLocation.parse("qa_pack:wonders/said_on.json"), GOOD.replaceFirst("\\{", "{\"enabled\": true, "));
		files.put(ResourceLocation.parse("qa_pack:wonders/sub/deep.json"), GOOD);
		files.put(ResourceLocation.parse("qa_pack:wonders/notes.txt"), "{not json");
		files.put(ResourceLocation.parse("qa_pack:other/elsewhere.json"), "{not json");
		files.put(ResourceLocation.parse("qa_pack:wonders/off_and_broken.json"), "{\"enabled\": false, \"stages\": 7}");
		Map<String, String> broken = new LinkedHashMap<>(); // file name -> what its reason must name
		broken.put("crew_zero", GOOD.replaceFirst("\\{", "{\"crew\": 0, ") + "|crew");
		broken.put("crew_over", GOOD.replaceFirst("\\{", "{\"crew\": 33, ") + "|crew");
		broken.put("no_name", GOOD.replace("\"name\": \"wonder.owner.arch.name\", ", "") + "|name");
		broken.put("no_icon", GOOD.replace("\"icon\": \"minecraft:bell\",", "") + "|icon");
		broken.put("bad_icon_id", GOOD.replace("minecraft:bell", "Minecraft:Bell") + "|icon");
		broken.put("a_list", "[" + GOOD + "]|JSON object");
		broken.put("stages_not_a_list", GOOD.replaceAll("\"stages\": \\[.*\\]}\\],", "\"stages\": \"three\",") + "|stages");
		broken.put("stage_not_an_object", GOOD.replaceAll("\"stages\": \\[.*\\]}\\],", "\"stages\": [\"one\"],") + "|stage 1");
		broken.put("short_offset", GOOD.replace("\"offset\": [0, 0, 0]", "\"offset\": [0, 0]") + "|offset");
		broken.put("item_and_tag", GOOD.replace("\"item\": \"minecraft:bread\"", "\"item\": \"minecraft:bread\", \"tag\": \"minecraft:flowers\"") + "|'item' or a 'tag'");
		broken.put("neither_item_nor_tag", GOOD.replace("\"item\": \"minecraft:bread\", ", "") + "|'item' or a 'tag'");
		broken.put("no_count", GOOD.replace("\"count\": 16, ", "") + "|count");
		broken.put("bad_potion", GOOD.replace("\"count\": 16", "\"count\": 16, \"potion\": \"minecraft:no_such_potion\"") + "|no_such_potion");
		broken.put("helper_not_found", GOOD.replace("\"count\": 16", "\"count\": 16, \"helper\": {\"days\": 3, \"condition\": {\"type\": \"villagers\", \"count\": 1}}") + "|helper");
		broken.put("level_six", GOOD.replace("{\"type\": \"rank_at_least\", \"rank\": \"town\"}",
			"{\"type\": \"job_level\", \"job\": \"minecraft:cleric\", \"level\": 6, \"count\": 1}") + "|level");
		broken.put("requirements_not_a_list", GOOD.replace("\"requirements\": [{\"type\": \"rank_at_least\", \"rank\": \"town\"}]", "\"requirements\": {\"type\": \"villagers\", \"count\": 1}")
			+ "|requirements");
		for (Map.Entry<String, String> e : broken.entrySet()) {
			String text = e.getValue().substring(0, e.getValue().lastIndexOf('|'));
			helper.assertTrue(!text.equals(GOOD), e.getKey() + ": the mistake wasn't planted (the replacement missed)");
			files.put(ResourceLocation.parse("qa_pack:wonders/" + e.getKey() + ".json"), text);
		}
		Map<ResourceLocation, Wonder> loaded = Wonders.load(files);
		Map<ResourceLocation, String> problems = Wonders.problems();
		Set<ResourceLocation> wanted = Set.of(ResourceLocation.parse("qa_pack:good"), ResourceLocation.parse("qa_pack:crew_one"), ResourceLocation.parse("qa_pack:crew_max"),
			ResourceLocation.parse("qa_pack:said_on"), ResourceLocation.parse("qa_pack:sub/deep"));
		helper.assertTrue(loaded.keySet().equals(wanted), "loaded: " + loaded.keySet() + ", skipped: " + problems);
		helper.assertTrue(loaded.get(ResourceLocation.parse("qa_pack:crew_one")).crew() == 1 && loaded.get(ResourceLocation.parse("qa_pack:crew_max")).crew() == 32,
			"the crews read");
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, String> e : broken.entrySet()) {
			ResourceLocation path = ResourceLocation.parse("qa_pack:wonders/" + e.getKey() + ".json");
			String must = e.getValue().substring(e.getValue().lastIndexOf('|') + 1);
			String why = problems.get(path);
			if (why == null || !why.contains(must)) {
				wrong.add(e.getKey() + " (wants \"" + must + "\"): " + why);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the reasons: " + wrong);
		helper.assertTrue(problems.size() == broken.size(), "skipped " + problems.size() + " files, " + broken.size() + " broken: " + problems.keySet());
		AliveWorkplace.LOG.info("[test] qa wonder files: {} loaded, {} skipped with their reasons", loaded.size(), problems.size());
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Master Architect

	/**
	 * The Master Architect's stand-in (Legends off) is a Master builder in the village and nothing less: a level-4 builder,
	 * a Master mason, and a Master builder just outside the village aren't; once one stands in, the folly may start; with
	 * Legends on the builder stands in no longer. The refusal reads in English.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaWonderArchitect"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWonderArchitect")
	public void aLevelFourBuilderIsNoMasterArchitect(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BlockPos at = new BlockPos(5, 2, 15);
		BlockPos hall = helper.absolutePos(at);
		forgetSites(level, hall);
		VillageHallBlockEntity entity = hall(helper, at, VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			entity.setRank(VillageRanks.Rank.TOWN); // the hall works its rank out again as it ticks
			Condition architect = Wonders.masterArchitect();
			boolean legends = Wonders.LEGENDS;
			try {
				Wonders.LEGENDS = false;
				villager(helper, new BlockPos(7, 2, 15), ModVillagers.BUILDER, 4);
				villager(helper, new BlockPos(7, 2, 17), VillagerProfession.MASON, 5);
				helper.assertTrue(!architect.met(level, hall), "a level-4 builder or a Master mason stood in for the Master Architect");
				reads(helper, Wonders.startRefusal(level, hall, FOLLY), "Not yet: Standing in for the Master Architect: Builders at Master or above: 0 of 1",
					"the folly with a level-4 builder");
				villager(helper, new BlockPos(27, 2, 15), ModVillagers.BUILDER, 5); // 22 blocks from the hall, the village reaches 10
				helper.assertTrue(!architect.met(level, hall), "a Master builder outside the village stood in");
				villager(helper, new BlockPos(9, 2, 13), ModVillagers.BUILDER, 5);
				helper.assertTrue(architect.met(level, hall), "a Master builder in the village didn't stand in");
				allowed(helper, Wonders.startRefusal(level, hall, FOLLY), "a Town with three villagers and a Master builder");
				Wonders.LEGENDS = true;
				helper.assertTrue(!architect.met(level, hall), "with Legends on, a Master builder stood in for the Master Architect");
			} finally {
				Wonders.LEGENDS = legends;
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- coastal

	/**
	 * {@code coastal} reaches 48 blocks: a beach 42 to 58 blocks east of the hall counts, one 42 to 58 north too; one
	 * 62 to 80 blocks east doesn't.
	 */
	//$ gametest_batch SMALL '"qaWonderCoast"'
	@GameTest(template = SMALL, batch = "qaWonderCoast")
	public void theCoastIsSeenOutToFortyEightBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		Condition coastal = Conditions.parse(com.google.gson.JsonParser.parseString("{\"type\": \"coastal\"}").getAsJsonObject());
		int[][] bands = {{42, 58, -6, 6}, {-6, 6, -58, -42}, {62, 80, -6, 6}};
		boolean[] expected = {true, true, false};
		String[] names = {"42-58 east", "42-58 north", "62-80 east"};
		io.github.jcondedata.aliveworkplace.threat.Conditions.forgetCoasts();
		helper.assertTrue(!coastal.met(level, hall), "a coast here already: " + old);
		for (int i = 0; i < bands.length; i++) {
			int[] b = bands[i];
			try {
				fill(level, hall, b, "minecraft:beach");
				BlockPos middle = hall.offset((b[0] + b[1]) / 2, 0, (b[2] + b[3]) / 2);
				String now = level.getBiome(middle).unwrapKey().map(k -> k.location().toString()).orElse("?");
				helper.assertTrue(now.equals("minecraft:beach"), names[i] + ": the beach wasn't laid (" + now + ")");
				io.github.jcondedata.aliveworkplace.threat.Conditions.forgetCoasts();
				Condition.Progress p = coastal.progress(level, hall);
				helper.assertTrue(p.met() == expected[i], names[i] + ": coastal " + p.met() + ", expected " + expected[i]);
				if (i == 0) {
					allTranslated(helper, p.line(), "the coastal line");
					helper.assertTrue(p.line().getString().equals("The sea or a beach within 48 blocks of the hall"), "the coastal line reads: " + p.line().getString());
				}
			} finally {
				fill(level, hall, b, old);
				io.github.jcondedata.aliveworkplace.threat.Conditions.forgetCoasts();
			}
		}
		helper.succeed();
	}

	private static void fill(ServerLevel level, BlockPos hall, int[] band, String biome) {
		BlockPos from = hall.offset(band[0], -4, band[2]);
		BlockPos to = hall.offset(band[1], 4, band[3]);
		for (int cx = from.getX() >> 4; cx <= to.getX() >> 4; cx++) {
			for (int cz = from.getZ() >> 4; cz <= to.getZ() >> 4; cz++) {
				level.getChunk(cx, cz); // loaded now, so fillbiome can reach it
			}
		}
		String cmd = "fillbiome " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	// ---------------------------------------------------------------- /workplace wonders

	/**
	 * {@code /workplace wonders} reads in English: a site with no owner says "no owner" with its stage's name, a site
	 * whose Wonder's file has gone shows its id and bare stage, and an owner's name is shown; no line shows a raw key.
	 */
	//$ gametest_batch SMALL '"qaWonderList"'
	@GameTest(template = SMALL, batch = "qaWonderList")
	public void theWondersListReadsInEnglish(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos b = helper.absolutePos(new BlockPos(14, 2, 14));
		forgetSites(level, a, b);
		hall(helper, new BlockPos(2, 2, 2), VillageRanks.Rank.TOWN);
		hall(helper, new BlockPos(14, 2, 14), VillageRanks.Rank.TOWN);
		WonderManager manager = WonderManager.get(level);
		try {
			WonderSite folly = manager.begin(FOLLY, a, null, "", 1);
			manager.setStage(folly.id(), 2);
			WonderSite gone = manager.begin(GONE, b, UUID.randomUUID(), "Ana", 1);
			manager.setStage(gone.id(), 4);
			List<Component> said = run(helper, "workplace wonders");
			List<String> text = said.stream().map(Component::getString).toList();
			int all = WonderManager.everywhere(level.getServer()).size();
			helper.assertTrue(text.size() == all + 1 && text.get(0).equals("Wonders on this server: " + all), "the header: " + text);
			for (Component line : said) {
				allTranslated(helper, line, "a /workplace wonders line");
			}
			String follyLine = "Test Folly in " + VillageHalls.name(level, a).getString() + " (no owner): stage 2 of 3, The Upper Story";
			String goneLine = GONE + " in " + VillageHalls.name(level, b).getString() + " (Ana): stage 4";
			helper.assertTrue(text.contains(follyLine), "no line \"" + follyLine + "\" in " + text);
			helper.assertTrue(text.contains(goneLine), "no line \"" + goneLine + "\" in " + text);
		} finally {
			forgetSites(level, a, b);
		}
		helper.succeed();
	}

	private static List<Component> run(GameTestHelper helper, String command) {
		List<Component> said = new ArrayList<>();
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message);
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
		CommandSourceStack source = new CommandSourceStack(out, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))), Vec2.ZERO, helper.getLevel(), 4,
			"test", Component.literal("test"), helper.getLevel().getServer(), null);
		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
		return said;
	}
}
