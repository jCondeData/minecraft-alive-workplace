package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (1104c) of B893, B901, B902 and B903, written from the spec (ROADMAP 37.3, 37.5 and the bugs' "Expected"): the
 * Blueprint Table's "All materials" list and 3D preview and the Steward desk's "Needs most:" counts show exactly what the
 * builder will build and ask for, follow the config's furniture order and switch at once (no stale cache either way), a
 * build already going up keeps the pieces it began with (also over a save and reload) while a new one follows the new
 * order, and a styled Healing Center or Schoolhouse at every level keeps its Poke Ball desks.
 *
 * <p>The oracle is a real {@link BuildSite} planned at a plot (what the builder will place and ask for), not the code
 * path the views use. The test pack's furniture "mods" stand in for real ones: {@code aliveworkplace_test_pieces} (its
 * chair an upturned stair, its table a block of planks) and {@code aliveworkplace_test_alt} (its chair a block of wood).
 * The real CobbleFurnies Poke Ball desk build needs the mod, so it belongs to the compat suite and isn't checked here.
 */
public class QaFurnishedViews1104cGameTests implements FabricGameTest {
	static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	static final String PIECES = "aliveworkplace_test_pieces";
	static final String ALT = "aliveworkplace_test_alt";

	// ---- helpers -----------------------------------------------------------------------------------------------

	/** The config as a server owner writes it: the switch and the order. */
	private static void config(boolean furnished, String... mods) {
		StringBuilder list = new StringBuilder();
		for (String m : mods) {
			list.append(list.length() == 0 ? "" : ", ").append('"').append(m).append('"');
		}
		WorkplaceConfig.parse("{\"furnishedBuilds\": " + furnished + ", \"furnitureMods\": [" + list + "]}").apply();
	}

	private static BlueprintData.Placement plot(GameTestHelper helper, int x) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(x, 2, 2)), Rotation.NONE, Mirror.NONE);
	}

	/** A new build site of {@code id} at {@code at}, planned as the builder plans it (no foundation: nothing terrain-made). */
	private static BuildSite site(ServerLevel level, ResourceLocation id, BlueprintData.Placement at) {
		BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "QA", id, at);
		site.setFoundation(false);
		return site;
	}

	/** What the builder of {@code site} will ask for, each material as the views group it (its family's plain block). */
	private static Map<String, Integer> asks(ServerLevel level, BuildSite site) {
		BuildPlan plan = site.plan(level);
		Map<String, Integer> out = new TreeMap<>();
		if (plan != null) {
			plan.materials().forEach((item, n) -> out.merge(BuiltInRegistries.ITEM.getKey(MaterialFamilies.key(item)).toString(), n, Integer::sum));
		}
		return out;
	}

	/** The blocks the builder of {@code site} will build, as a preview draws them. */
	private static Map<String, String> builtPreview(ServerLevel level, ResourceLocation id, BuildSite site) {
		site.plan(level); // the builder chooses its furniture when it first plans
		Blueprint drawn = BlueprintLibrary.get(level, id).orElseThrow();
		return QaBuildViews1103gGameTests.cells(TableServer.buildPreview(id, site.furniture().apply(drawn, id)));
	}

	private static Map<String, Integer> tableList(ServerLevel level, ResourceLocation id) {
		return TableServer.details(level, null, id).map(d -> QaBuildViews1103gGameTests.asMap(d.materials())).orElse(null);
	}

	private static Map<String, String> tablePreview(ServerLevel level, ResourceLocation id) {
		return TableServer.preview(level.getServer(), id, true).map(QaBuildViews1103gGameTests::cells).orElse(null);
	}

	private static Map<String, Integer> deskNeeds(ServerLevel level, GameTestHelper helper, ResourceLocation id, BlueprintData.Placement at) {
		StewardDesk.Proposal proposal = new StewardDesk.Proposal(1, AliveWorkplace.id("qa_rule"), false, id, at, "", "", "",
			List.of(), 0L, 0, false, List.of(), "");
		Map<String, Integer> out = new TreeMap<>();
		for (StewardDesk.Need need : StewardDesk.needs(level, helper.absolutePos(new BlockPos(0, 2, 0)), proposal)) {
			out.put(BuiltInRegistries.ITEM.getKey(need.item()).toString(), need.count());
		}
		return out;
	}

	private static String diff(Map<String, Integer> shown, Map<String, Integer> want) {
		List<String> out = new ArrayList<>();
		java.util.Set<String> keys = new java.util.TreeSet<>(shown.keySet());
		keys.addAll(want.keySet());
		for (String k : keys) {
			int a = shown.getOrDefault(k, 0);
			int b = want.getOrDefault(k, 0);
			if (a != b) {
				out.add(k + " " + a + " (builder asks " + b + ")");
			}
			if (out.size() >= 4) {
				out.add("...");
				break;
			}
		}
		return String.join(", ", out);
	}

	/** Every build the Table lists that has furniture spots, plus the cottage and the first Healing Center in every style. */
	private static List<ResourceLocation> furnishedBuilds(ServerLevel level) {
		List<ResourceLocation> out = new ArrayList<>();
		for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), false)) {
			if (!BlueprintFurniture.of(id).isEmpty()) {
				out.add(id);
			}
		}
		for (ResourceLocation base : List.of(COTTAGE, AliveWorkplace.id("healing_center"))) {
			for (BlueprintStyles.Style style : BlueprintStyles.all()) {
				ResourceLocation styled = BlueprintStyles.styled(base, style.name());
				if (style.isFor(base) && BlueprintLibrary.get(level, styled).isPresent() && !BlueprintFurniture.of(styled).isEmpty()) {
					out.add(styled);
				}
			}
		}
		return out;
	}

	private static String first(List<String> problems) {
		return problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(6, problems.size())));
	}

	// ---- B893 / B901 / B902: the views agree with the builder for every furnished build ------------------------

	/**
	 * For every furnished build (and the cottage and Healing Center in every style), with a furniture mod first in the
	 * order, the Table's "All materials" list is exactly what a new builder's site of that build asks for.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaFurnishedViews1104c")
	public void theTablesListIsWhatTheBuilderAsksForInEveryFurnishedBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		int changed = 0;
		try {
			config(true, PIECES);
			for (ResourceLocation id : furnishedBuilds(level)) {
				Map<String, Integer> shown = tableList(level, id);
				if (shown == null) {
					continue; // a "???" or a style not taken: nothing listed
				}
				Map<String, Integer> want = asks(level, site(level, id, plot(helper, 2)));
				checked++;
				if (!want.equals(QaBuildViews1103gGameTests.asMap(TableServer.materials(BlueprintLibrary.get(level, id).orElseThrow())))) {
					changed++;
				}
				if (!shown.equals(want)) {
					problems.add(id + ": " + diff(shown, want));
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(checked > 10, "only " + checked + " furnished builds listed by the Table");
		helper.assertTrue(changed > 0, "the test pieces change no build's materials, the test proves nothing");
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}

	/** For every furnished build, the Table's 3D preview draws the blocks a new builder's site of it will build. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaFurnishedViews1104c")
	public void theTablesPreviewIsWhatTheBuilderBuildsInEveryFurnishedBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		int changed = 0;
		try {
			config(true, PIECES);
			for (ResourceLocation id : furnishedBuilds(level)) {
				Map<String, String> shown = tablePreview(level, id);
				if (shown == null) {
					continue; // not offered at the Table
				}
				BuildSite site = site(level, id, plot(helper, 2));
				Map<String, String> want = builtPreview(level, id, site);
				checked++;
				if (!site.furniture().isVanilla()) {
					changed++;
				}
				if (!shown.equals(want)) {
					problems.add(id + ": " + QaBuildViews1103gGameTests.diff(shown, want));
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(checked > 10, "only " + checked + " furnished builds previewed by the Table");
		helper.assertTrue(changed > 0, "the test pieces furnish no build, the test proves nothing");
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}

	/**
	 * For every furnished build, the Steward desk's "Needs most:" lines are the build's top materials, each counted as
	 * the builder of a site at the proposal's plot will ask for them.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaFurnishedViews1104c")
	public void theDesksNeedsAreWhatTheBuilderAsksForInEveryFurnishedBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		try {
			config(true, PIECES);
			BlueprintData.Placement at = plot(helper, 40);
			for (ResourceLocation id : furnishedBuilds(level)) {
				Map<String, Integer> listed = deskNeeds(level, helper, id, at);
				if (listed.isEmpty()) {
					continue;
				}
				checked++;
				Map<String, Integer> want = asks(level, site(level, id, at));
				int lowestListed = Integer.MAX_VALUE;
				for (Map.Entry<String, Integer> e : listed.entrySet()) {
					lowestListed = Math.min(lowestListed, e.getValue());
					int asked = want.getOrDefault(e.getKey(), 0);
					if (asked != e.getValue()) {
						problems.add(id + ": the desk says " + e.getValue() + " " + e.getKey() + " (the builder asks " + asked + ")");
					}
				}
				for (Map.Entry<String, Integer> e : want.entrySet()) {
					if (!listed.containsKey(e.getKey()) && e.getValue() > lowestListed) {
						problems.add(id + ": the desk leaves out " + e.getValue() + " " + e.getKey() + " but lists one needing only " + lowestListed);
					}
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(checked > 10, "only " + checked + " furnished builds with needs");
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}

	// ---- no stale views when the switch or the order changes -----------------------------------------------------

	/**
	 * A player opens the cottage at the Table (list and preview) and the Steward desk, the owner turns furnishedBuilds
	 * off, then on again: each time all three show what a new builder would build then, never the earlier answer.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedViews1104c")
	public void theSwitchOffThenOnAgainNeverLeavesAStaleView(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		List<Map<String, Integer>> lists = new ArrayList<>();
		try {
			for (ResourceLocation id : List.of(COTTAGE, BlueprintStyles.styled(COTTAGE, "dark_oak"))) {
				lists.clear();
				int step = 0;
				for (boolean on : new boolean[] {true, false, true, false}) {
					config(on, PIECES);
					helper.assertTrue(Furnishings.ENABLED == on, "furnishedBuilds " + on + " didn't take (the M37 gate), the test proves nothing");
					String when = id + " (step " + step++ + ", furnishedBuilds " + on + ")";
					BuildSite site = site(level, id, plot(helper, 2));
					Map<String, Integer> want = asks(level, site);
					lists.add(want);
					Map<String, Integer> shown = tableList(level, id);
					if (!want.equals(shown)) {
						problems.add(when + ": the Table lists " + (shown == null ? "nothing" : diff(shown, want)));
					}
					Map<String, String> drawn = tablePreview(level, id);
					Map<String, String> built = builtPreview(level, id, site);
					if (!built.equals(drawn)) {
						problems.add(when + ": the Table's preview " + (drawn == null ? "is missing" : QaBuildViews1103gGameTests.diff(drawn, built)));
					}
					BlueprintData.Placement at = plot(helper, 40);
					Map<String, Integer> needs = deskNeeds(level, helper, id, at);
					Map<String, Integer> asked = asks(level, site(level, id, at));
					needs.forEach((item, n) -> {
						if (asked.getOrDefault(item, 0).intValue() != n) {
							problems.add(when + ": the desk says " + n + " " + item + " (the builder asks " + asked.getOrDefault(item, 0) + ")");
						}
					});
				}
				helper.assertTrue(!lists.get(0).equals(lists.get(1)), id + ": the switch changes nothing, the test proves nothing");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}

	/**
	 * Between two Table opens the owner reorders furnitureMods (the test pieces first, then the alternative mod first,
	 * then back): each open shows the furniture of the mod now first, as a new build would get, never the last order's.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedViews1104c")
	public void anOrderChangedBetweenTwoTableOpensShowsTheNewOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		List<Map<String, String>> previews = new ArrayList<>();
		try {
			List<String[]> orders = List.of(new String[] {PIECES, ALT}, new String[] {ALT, PIECES}, new String[] {PIECES, ALT}, new String[] {ALT});
			for (String[] order : orders) {
				config(true, order);
				String when = "order " + String.join(",", order);
				BuildSite site = site(level, COTTAGE, plot(helper, 2));
				Map<String, Integer> want = asks(level, site);
				Map<String, Integer> shown = tableList(level, COTTAGE);
				if (!want.equals(shown)) {
					problems.add(when + ": the Table lists " + (shown == null ? "nothing" : diff(shown, want)));
				}
				Map<String, String> built = builtPreview(level, COTTAGE, site);
				previews.add(built);
				Map<String, String> drawn = tablePreview(level, COTTAGE);
				if (!built.equals(drawn)) {
					problems.add(when + ": the Table's preview " + (drawn == null ? "is missing" : QaBuildViews1103gGameTests.diff(drawn, built)));
				}
			}
			helper.assertTrue(!previews.get(0).equals(previews.get(1)), "the order changes nothing in the cottage, the test proves nothing");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}

	/**
	 * A mod in the order that isn't installed is passed over (Furnishings: "a mod that isn't installed is passed over"):
	 * first in the order, the next mod's pieces are shown and built; alone, the build is the vanilla one, in the Table's
	 * list, its preview and the desk.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedViews1104c")
	public void aModInTheOrderThatIsntInstalledIsPassedOver(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		try {
			config(true, PIECES);
			Map<String, Integer> pieces = asks(level, site(level, COTTAGE, plot(helper, 2)));
			Map<String, String> piecesDrawn = builtPreview(level, COTTAGE, site(level, COTTAGE, plot(helper, 2)));
			config(false);
			Map<String, Integer> vanilla = asks(level, site(level, COTTAGE, plot(helper, 2)));
			Map<String, String> vanillaDrawn = builtPreview(level, COTTAGE, site(level, COTTAGE, plot(helper, 2)));
			helper.assertTrue(!pieces.equals(vanilla), "the test pieces change nothing, the test proves nothing");
			record Case(String name, String[] order, Map<String, Integer> list, Map<String, String> drawn) {
			}
			for (Case c : List.of(new Case("missing mod first", new String[] {"qa_not_installed", PIECES}, pieces, piecesDrawn),
				new Case("missing mod alone", new String[] {"qa_not_installed"}, vanilla, vanillaDrawn))) {
				config(true, c.order());
				Map<String, Integer> built = asks(level, site(level, COTTAGE, plot(helper, 2)));
				if (!built.equals(c.list())) {
					problems.add(c.name() + ": the builder asks " + diff(built, c.list()));
				}
				Map<String, Integer> shown = tableList(level, COTTAGE);
				if (!c.list().equals(shown)) {
					problems.add(c.name() + ": the Table lists " + (shown == null ? "nothing" : diff(shown, c.list())));
				}
				Map<String, String> drawn = tablePreview(level, COTTAGE);
				if (!c.drawn().equals(drawn)) {
					problems.add(c.name() + ": the Table's preview " + (drawn == null ? "is missing" : QaBuildViews1103gGameTests.diff(drawn, c.drawn())));
				}
				deskNeeds(level, helper, COTTAGE, plot(helper, 40)).forEach((item, n) -> {
					if (c.list().getOrDefault(item, 0).intValue() != n) {
						problems.add(c.name() + ": the desk says " + n + " " + item + " (the builder asks " + c.list().getOrDefault(item, 0) + ")");
					}
				});
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}

	// ---- a build going up keeps its pieces; a new build follows the new order ---------------------------------

	/**
	 * ROADMAP 37.3 / FurnitureChoice: the pieces are "chosen once when the build is planned and saved on its site, so a
	 * restart, a config change or an added mod never changes a build that is going up". A cottage begun with the test
	 * pieces first keeps them after the order changes, after a replan and over a save and reload; a cottage begun
	 * after the change, and the Table, follow the new order.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedViews1104c")
	public void aBuildGoingUpKeepsItsPiecesWhileANewOneFollowsTheNewOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		try {
			config(true, PIECES, ALT);
			BuildSite begun = site(level, COTTAGE, plot(helper, 2));
			Map<String, Integer> before = asks(level, begun);
			FurnitureChoice chosen = begun.furniture();
			helper.assertTrue(!chosen.isVanilla(), "the test pieces don't furnish the cottage, the test proves nothing");

			config(true, ALT, PIECES);
			// the site is replanned (a levelGround change drops the plan) under the new order
			begun.setLevelGround(true);
			begun.setLevelGround(false);
			Map<String, Integer> replanned = asks(level, begun);
			if (!begun.furniture().equals(chosen) || !replanned.equals(before)) {
				problems.add("the build going up changed its pieces with the order: " + diff(replanned, before));
			}
			BuildSite reloaded = BuildSite.load(begun.save());
			Map<String, Integer> afterLoad = asks(level, reloaded);
			if (!reloaded.furniture().equals(chosen) || !afterLoad.equals(before)) {
				problems.add("after a save and reload the build going up asks " + diff(afterLoad, before));
			}

			BuildSite fresh = site(level, COTTAGE, plot(helper, 40));
			Map<String, Integer> now = asks(level, fresh);
			if (now.equals(before) || fresh.furniture().equals(chosen)) {
				problems.add("a new cottage begun after the change still gets the old order's pieces");
			}
			Map<String, Integer> shown = tableList(level, COTTAGE);
			if (!now.equals(shown)) {
				problems.add("the Table lists " + (shown == null ? "nothing" : diff(shown, now)) + " (should be a new build's, in the new order)");
			}
			Map<String, String> drawn = tablePreview(level, COTTAGE);
			Map<String, String> built = builtPreview(level, COTTAGE, fresh);
			if (!built.equals(drawn)) {
				problems.add("the Table's preview " + (drawn == null ? "is missing" : QaBuildViews1103gGameTests.diff(drawn, built)));
			}

			// a site saved before it first planned chooses when it plans, so it follows the order of that day
			config(true, PIECES, ALT);
			BuildSite unplanned = site(level, COTTAGE, plot(helper, 80));
			BuildSite unplannedLoaded = BuildSite.load(unplanned.save());
			config(true, ALT, PIECES);
			asks(level, unplannedLoaded);
			if (!unplannedLoaded.furniture().equals(fresh.furniture())) {
				problems.add("a site saved before it planned didn't choose with the order when it first planned");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}

	// ---- B903: every Healing Center and Schoolhouse, every level, every style -----------------------------------

	/**
	 * The owner's answer (37.5): the Healing Center's and the Schoolhouse's desks are CobbleFurnies' Poke Ball desks at
	 * every level, and a style swaps only the wood and stone. Every level by name (not found by the predicate under test),
	 * as drawn and in every style, and in a village-colour style; builds that aren't those two never get them.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedViews1104c")
	public void everyHealingCenterAndSchoolhouseInEveryStyleHasPokeBallDesks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int styled = 0;
		List<ResourceLocation> levels = new ArrayList<>();
		for (String name : List.of("healing_center", "healing_center_2", "healing_center_3", "healing_center_4", "schoolhouse", "schoolhouse_2")) {
			ResourceLocation id = AliveWorkplace.id(name);
			if (BlueprintLibrary.get(level, id).isEmpty()) {
				problems.add(id + " isn't in the library");
				continue;
			}
			levels.add(id);
		}
		for (ResourceLocation base : levels) {
			if (!FurnitureChoice.hasPokeBallDesks(base)) {
				problems.add(base + " (as drawn)");
			}
			// the id exactly as the bug names it
			ResourceLocation literal = ResourceLocation.parse("aliveworkplace:styled/spruce/aliveworkplace/" + base.getPath());
			if (!FurnitureChoice.hasPokeBallDesks(literal)) {
				problems.add(literal.toString());
			}
			ResourceLocation colours = BlueprintStyles.styled(base, BlueprintStyles.coloursStyle(net.minecraft.world.item.DyeColor.BLUE, net.minecraft.world.item.DyeColor.WHITE));
			if (!FurnitureChoice.hasPokeBallDesks(colours)) {
				problems.add(colours.toString());
			}
			for (BlueprintStyles.Style style : BlueprintStyles.all()) {
				if (!style.isFor(base)) {
					continue;
				}
				ResourceLocation id = BlueprintStyles.styled(base, style.name());
				styled++;
				if (!FurnitureChoice.hasPokeBallDesks(id)) {
					problems.add(id.toString());
				}
			}
		}
		// never for another build, another mod's build of that name, or a styled one of those
		for (ResourceLocation not : List.of(COTTAGE, BlueprintStyles.styled(COTTAGE, "spruce"), ResourceLocation.parse("othermod:healing_center"),
			BlueprintStyles.styled(ResourceLocation.parse("othermod:schoolhouse"), "spruce"), AliveWorkplace.id("healing_center_wing"))) {
			if (FurnitureChoice.hasPokeBallDesks(not)) {
				problems.add(not + " gets Poke Ball desks but isn't a Healing Center or Schoolhouse of ours");
			}
		}
		helper.assertTrue(styled >= 30, "only " + styled + " styled Healing Centers and Schoolhouses (the bug names 30)");
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}

	/**
	 * A styled Healing Center or Schoolhouse is furnished on the same spots as the plain one: with a furniture mod first,
	 * a new build of it in a style gets a piece on every spot the plain build does (a style swaps only wood and stone).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedViews1104c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedViews1104c")
	public void aStyledHealingCenterIsFurnishedOnTheSameSpotsAsThePlainOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		try {
			config(true, PIECES);
			for (String name : List.of("healing_center", "healing_center_2", "healing_center_3", "healing_center_4", "schoolhouse", "schoolhouse_2")) {
				ResourceLocation base = AliveWorkplace.id(name);
				BuildSite plain = site(level, base, plot(helper, 2));
				plain.plan(level);
				java.util.Set<String> plainSpots = new java.util.TreeSet<>();
				plain.furniture().spots().values().forEach(p -> plainSpots.add(p.pos().toShortString() + " " + p.kind()));
				for (BlueprintStyles.Style style : BlueprintStyles.all()) {
					ResourceLocation id = BlueprintStyles.styled(base, style.name());
					if (!style.isFor(base) || BlueprintLibrary.get(level, id).isEmpty()) {
						continue;
					}
					BuildSite site = site(level, id, plot(helper, 2));
					site.plan(level);
					java.util.Set<String> spots = new java.util.TreeSet<>();
					site.furniture().spots().values().forEach(p -> spots.add(p.pos().toShortString() + " " + p.kind()));
					checked++;
					if (!spots.equals(plainSpots)) {
						problems.add(id + ": furnished on " + spots + ", the plain build on " + plainSpots);
					}
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(checked > 0, "no styled Healing Center or Schoolhouse, the test proves nothing");
		helper.assertTrue(problems.isEmpty(), first(problems));
		helper.succeed();
	}
}
