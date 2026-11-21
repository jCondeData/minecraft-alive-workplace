package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * QA lane E (2026-11-20), save upgrades after Milestones 49-52 rebuilt dozens of families from scratch. The existing
 * B1371 tests (QaRebuildMidBuild1117eBugTests, QaRebuildSaves1117eGameTests) use a synthetic drawing added by hand; this
 * class sweeps the REAL shipped history ({@code data/aliveworkplace/blueprint_history/<structure>/<key>.nbt}).
 *
 * <p>Promises tested (BuildSite's field comments, Refreshes#drawnAs, tools/blueprints/REBUILD.md "sites mid-build keep
 * their old drawing until a refresh"):
 * <ul>
 * <li>a site begun from an older drawing while it was today's (its save: the drawing's fingerprint, no built_from) that
 * is loaded by the jar with the rebuilt drawing keeps building the older drawing: the same plan as the older drawing
 * had, not today's, and records that drawing so later loads keep it;</li>
 * <li>a site of today's drawing of a rebuilt family keeps its stage, place and "no older drawing" across a reload in
 * every stage;</li>
 * <li>older saves: one with no fingerprint and no built_from (before B1049) and one naming an older drawing no longer
 * shipped both go on with today's drawing without crashing or sticking.</li>
 * </ul>
 */
public class QaRebuildHistorySweep1120eGameTests implements FabricGameTest {
	private static final String DIR = "blueprint_history";

	/** Rebuilt families named by Milestones 49-52 (each must be among those checked by the sweep). */
	private static final List<String> RECENT = List.of("pump_house", "terminal_nook", "train_station", "quarry", "sifting_shed",
		"crushing_mill", "windmill", "berry_farm", "apiary_garden", "greenhouse", "compost_yard", "butchers_shop", "winery",
		"watermill", "ferry_house");

	/** Every shipped older drawing, by blueprint id, keys in order. */
	private static Map<ResourceLocation, List<String>> shipped(MinecraftServer server) {
		Map<ResourceLocation, List<String>> out = new TreeMap<>();
		Set<String> seen = new HashSet<>();
		for (ResourceLocation file : server.getResourceManager().listResources(DIR, id -> id.getPath().endsWith(".nbt")).keySet()) {
			String path = file.getPath().substring(DIR.length() + 1, file.getPath().length() - ".nbt".length());
			int slash = path.lastIndexOf('/');
			if (slash <= 0 || !seen.add(file.toString())) {
				continue;
			}
			out.computeIfAbsent(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(0, slash)), k -> new ArrayList<>())
				.add(path.substring(slash + 1));
		}
		out.values().forEach(l -> l.sort(String::compareTo));
		return out;
	}

	private static CompoundTag read(MinecraftServer server, ResourceLocation structure, String key) throws java.io.IOException {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(structure.getNamespace(), DIR + "/" + structure.getPath() + "/" + key + ".nbt");
		Resource res = server.getResourceManager().getResource(file).orElseThrow();
		try (InputStream in = res.open()) {
			return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		}
	}

	/** The jar of the day {@code tag} was today's drawing: the blueprint {@code id} is it. */
	private static void installAsToday(ServerLevel level, ResourceLocation id, CompoundTag tag) {
		StructureTemplateManager manager = level.getStructureManager();
		manager.remove(id);
		manager.getOrCreate(id).load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
	}

	/** Back to the drawing this jar ships (read from the resources again). */
	private static void restore(ServerLevel level, ResourceLocation id) {
		level.getStructureManager().remove(id);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(0, 2, 0)),
			Rotation.NONE, Mirror.NONE);
	}

	/** A new vanilla build of {@code id}, planned (as a player's order makes one, without furniture mods). */
	private static BuildSite planned(GameTestHelper helper, ResourceLocation id) {
		BuildSite site = new BuildSite(new UUID(1120, id.hashCode()), new UUID(1120, 1), "Jesse", id, placement(helper));
		site.setFurniture(FurnitureChoice.VANILLA);
		return site;
	}

	private static boolean same(BlockState a, BlockState b) {
		boolean airA = a == null || a.isAir();
		boolean airB = b == null || b.isAir();
		if (airA || airB) {
			return airA && airB;
		}
		return MaterialRules.matches(a, b);
	}

	/** How many spots the two plans put different things on. */
	private static int differences(Map<BlockPos, BlockState> a, Map<BlockPos, BlockState> b) {
		Set<BlockPos> spots = new HashSet<>(a.keySet());
		spots.addAll(b.keySet());
		int n = 0;
		for (BlockPos p : spots) {
			if (!same(a.get(p), b.get(p))) {
				n++;
			}
		}
		return n;
	}

	private static String counts(BuildPlan plan) {
		return "structure " + plan.steps(BuildPlan.Stage.STRUCTURE).size() + ", decoration " + plan.steps(BuildPlan.Stage.DECORATION).size();
	}

	/** A failure list short enough for a GameTest's message (the framework refuses one over 1,024 characters). */
	private static String brief(List<String> failures) {
		String all = failures.toString();
		return all.length() <= 700 ? all : all.substring(0, 700) + "...";
	}

	/** What a site begun from one older drawing saved, and what it planned then. */
	private record Old(String key, int hash, CompoundTag save, Map<BlockPos, BlockState> targets, String counts) {
	}

	// ---- 1. every rebuilt family: a site begun from an older drawing, saved and loaded by today's jar --------------------

	/**
	 * For every blueprint with a shipped older drawing (modded library copies and Wonder parts aside: their mods aren't in
	 * this run, and Wonders go by their own drawing record, WonderDrawingsGameTests): the older drawing is made today's (the
	 * jar of its day), a new build is planned from it and saved half-way through its STRUCTURE, the jar of today comes back
	 * (today's drawing), and the save is loaded. Wanted: it plans the older drawing exactly as it did (every spot), names it
	 * as built from that drawing, is not moved past its STRUCTURE, and a second save and load keeps the drawing and its place
	 * (no second restart). One blueprint per tick; every failing family named in one message.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '1200' '"qaRebuildHistorySweep1120e"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "qaRebuildHistorySweep1120e")
	public void everyRebuiltFamilyKeepsTheOlderDrawingASiteWasBegunFromAfterSaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Map<ResourceLocation, List<String>> history = shipped(server);
		helper.assertTrue(history.size() > 50, "only " + history.size() + " blueprints have an older drawing under " + DIR);
		List<ResourceLocation> structures = new ArrayList<>(history.keySet());
		Set<ResourceLocation> touched = new HashSet<>();
		Leftovers.after(helper, () -> touched.forEach(id -> restore(level, id)));
		List<String> failures = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		List<String> unchanged = new ArrayList<>();
		Set<String> checkedFamilies = new HashSet<>();
		int[] checked = {0};
		int[] distinct = {0};
		for (int i = 0; i < structures.size(); i++) {
			ResourceLocation id = structures.get(i);
			helper.runAfterDelay(2 + i, () -> {
				String path = id.getPath();
				if (path.startsWith(io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary.PREFIX) || path.startsWith("wonder/")) {
					skipped.add(id.toString());
					return;
				}
				try {
					BuildSite today = planned(helper, id);
					BuildPlan todayPlan = today.plan(level);
					if (todayPlan == null) {
						skipped.add(id + " (no blueprint today)");
						return;
					}
					int todayHash = today.save().getInt("blueprint_hash");
					Map<BlockPos, BlockState> todayTargets = todayPlan.targets();
					// the jar of each older drawing's day: a site begun from it, half-way through its walls, saved
					Map<String, Old> olds = new LinkedHashMap<>();
					for (String key : history.get(id)) {
						touched.add(id);
						installAsToday(level, id, read(server, id, key));
						try {
							BuildSite begun = planned(helper, id);
							BuildPlan plan = begun.plan(level);
							if (plan == null) {
								failures.add(id + "@" + key + ": the older drawing didn't plan as today's (setup)");
								continue;
							}
							CompoundTag save = begun.save();
							helper.assertTrue(save.contains("blueprint_hash"), "(setup) no fingerprint saved");
							helper.assertFalse(save.contains("built_from"), "(setup) a new build of today's drawing saved built_from");
							int walls = plan.steps(BuildPlan.Stage.STRUCTURE).size();
							save.putString("stage", BuildPlan.Stage.STRUCTURE.name());
							save.putInt("cursor", walls / 2);
							save.putInt("placed", walls / 2);
							olds.put(key, new Old(key, save.getInt("blueprint_hash"), save, plan.targets(), counts(plan)));
						} finally {
							restore(level, id);
						}
					}
					// today's jar again: each save loaded
					for (Old old : olds.values()) {
						String name = id + "@" + old.key();
						if (old.hash() == todayHash) {
							unchanged.add(name);
							continue;
						}
						checked[0]++;
						checkedFamilies.add(path);
						if (differences(old.targets(), todayTargets) > 0) {
							// control: the same save made to go by today's drawing ("" ) must be told apart from the older one, so
							// a site that went on with today's drawing (a mix of the two) would be caught below
							distinct[0]++;
							CompoundTag forced = old.save().copy();
							forced.putString("built_from", "");
							BuildSite control = BuildSite.load(forced);
							BuildPlan controlPlan = control == null ? null : control.plan(level);
							if (controlPlan == null || differences(controlPlan.targets(), old.targets()) == 0) {
								failures.add(name + ": (control) a load forced to today's drawing plans like the older one");
							}
						}
						BuildSite loaded = BuildSite.load(old.save().copy());
						if (loaded == null) {
							failures.add(name + ": the save didn't load");
							continue;
						}
						BuildPlan plan = loaded.plan(level);
						if (plan == null || loaded.status() == BuildSite.Status.BLUEPRINT_MISSING) {
							failures.add(name + ": no plan after the load (" + loaded.status() + ")");
							continue;
						}
						String from = loaded.builtFrom();
						int offOld = differences(plan.targets(), old.targets());
						int offToday = differences(plan.targets(), todayTargets);
						Old named = from == null ? null : olds.get(from);
						if (from == null || from.isEmpty() || named == null || named.hash() != old.hash()) {
							failures.add(name + ": begun from it, loaded as built from '" + from + "' (" + offOld + " spots off the older drawing, "
								+ offToday + " off today's; " + counts(plan) + " vs the older drawing's " + old.counts() + ")");
							continue;
						}
						if (offOld != 0) {
							failures.add(name + ": plans " + offOld + " spots unlike the older drawing it was begun from (" + offToday
								+ " unlike today's; " + counts(plan) + " vs " + old.counts() + ")");
							continue;
						}
						if (loaded.stage().ordinal() > BuildPlan.Stage.STRUCTURE.ordinal()) {
							failures.add(name + ": the load moved it on to " + loaded.stage());
							continue;
						}
						// a later restart: the drawing and the place in its walls kept
						CompoundTag again = loaded.save();
						if (!from.equals(again.getString("built_from"))) {
							failures.add(name + ": built_from not saved (" + again + ")");
							continue;
						}
						int walls = plan.steps(BuildPlan.Stage.STRUCTURE).size();
						again.putString("stage", BuildPlan.Stage.STRUCTURE.name());
						again.putInt("cursor", walls / 3);
						BuildSite second = BuildSite.load(again);
						BuildPlan plan2 = second == null ? null : second.plan(level);
						if (plan2 == null || !from.equals(second.builtFrom()) || second.stage() != BuildPlan.Stage.STRUCTURE
							|| second.cursor() != walls / 3 || differences(plan2.targets(), old.targets()) != 0) {
							failures.add(name + ": a second load lost it: " + (second == null ? "no site" : second.builtFrom() + " " + second.stage()
								+ " cursor " + second.cursor() + " of " + walls + " (wanted " + walls / 3 + ")"
								+ (plan2 == null ? ", no plan" : ", " + differences(plan2.targets(), old.targets()) + " spots off")));
						}
					}
				} catch (Exception ex) {
					restore(level, id);
					failures.add(id + ": " + ex);
				}
			});
		}
		helper.runAfterDelay(4 + structures.size(), () -> {
			AliveWorkplace.LOG.info("QaRebuildHistorySweep1120e: {} blueprints with history, {} older drawings checked ({} planning differently from today's), "
				+ "{} same as today ({}), {} skipped ({})", structures.size(), checked[0], distinct[0], unchanged.size(), unchanged, skipped.size(), skipped);
			List<String> notChecked = RECENT.stream().filter(f -> !checkedFamilies.contains(f)).toList();
			if (!failures.isEmpty()) {
				AliveWorkplace.LOG.error("QaRebuildHistorySweep1120e failures: {}", failures);
			}
			helper.assertTrue(failures.isEmpty(), failures.size() + " of " + checked[0] + " older drawings: a site begun from one, loaded by "
				+ "today's jar, doesn't keep building it (all in the log): " + brief(failures));
			helper.assertTrue(distinct[0] * 2 > checked[0], "(setup) only " + distinct[0] + " of " + checked[0] + " older drawings plan differently from today's");
			helper.assertTrue(notChecked.isEmpty(), "rebuilt families with no older drawing checked: " + notChecked + " (" + checked[0] + " checked)");
			helper.succeed();
		});
	}

	// ---- 2. today's drawing of a rebuilt family, saved in every stage ---------------------------------------------------

	/**
	 * A new build of today's drawing of each recently rebuilt family, saved in each stage (CLEAR, FOUNDATION, STRUCTURE,
	 * DECORATION, LANDSCAPE) with blocks placed: loaded, it keeps its stage and placed count, its place in the list (the
	 * foundation and landscaping lists start again from the top, "what's already done is skipped"), today's drawing (no
	 * built_from: "" or none, never an older key) and the same plan; and so on a second load.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaRebuildHistoryStages1120e"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaRebuildHistoryStages1120e")
	public void todaysDrawingOfARebuiltFamilyKeepsItsStageAndDrawingAcrossALoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> failures = new ArrayList<>();
		int[] checked = {0};
		for (String family : RECENT) {
			ResourceLocation id = AliveWorkplace.id(family);
			BuildSite site = planned(helper, id);
			BuildPlan plan = site.plan(level);
			if (plan == null) {
				failures.add(family + ": no blueprint");
				continue;
			}
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE,
				BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
				int size = plan.steps(stage).size();
				int cursor = size / 2;
				CompoundTag save = site.save();
				save.putString("stage", stage.name());
				save.putInt("cursor", cursor);
				save.putInt("placed", 40);
				BuildSite loaded = BuildSite.load(save);
				BuildPlan again = loaded == null ? null : loaded.plan(level);
				String name = family + " in " + stage;
				checked[0]++;
				if (again == null) {
					failures.add(name + ": no plan after the load");
					continue;
				}
				boolean restarts = stage == BuildPlan.Stage.FOUNDATION || stage == BuildPlan.Stage.LANDSCAPE;
				int wantCursor = restarts ? 0 : cursor;
				String from = loaded.builtFrom();
				CompoundTag resaved = loaded.save();
				if (loaded.stage() != stage || loaded.placed() != 40 || loaded.cursor() != wantCursor) {
					failures.add(name + ": loaded as " + loaded.stage() + " cursor " + loaded.cursor() + " (wanted " + wantCursor + " of " + size
						+ ") placed " + loaded.placed());
				} else if (from != null && !from.isEmpty()) {
					failures.add(name + ": a build of today's drawing loaded as built from the older drawing '" + from + "'");
				} else if (differences(again.targets(), plan.targets()) != 0) {
					failures.add(name + ": plans " + differences(again.targets(), plan.targets()) + " spots unlike today's drawing");
				} else if (resaved.getInt("blueprint_hash") != save.getInt("blueprint_hash")) {
					failures.add(name + ": its drawing's fingerprint changed on the load");
				} else {
					BuildSite second = BuildSite.load(resaved);
					BuildPlan plan2 = second == null ? null : second.plan(level);
					if (plan2 == null || second.stage() != stage || (second.cursor() != wantCursor)
						|| second.builtFrom() != null && !second.builtFrom().isEmpty()) {
						failures.add(name + ": a second load: " + (second == null ? "none" : second.stage() + " cursor " + second.cursor()
							+ " built from '" + second.builtFrom() + "'"));
					}
				}
			}
		}
		helper.assertTrue(failures.isEmpty(), failures.size() + " of " + checked[0] + " saves: " + brief(failures));
		helper.succeed();
	}

	// ---- 3. older saves --------------------------------------------------------------------------------------------------

	/**
	 * Saves older than B1049 (no fingerprint) and B1371 (no built_from) half-way through the walls of each recently rebuilt
	 * family: "older saves keep their cursor and record it then" (BuildSite#drawnHash): they plan today's drawing, keep
	 * their stage and place (inside the list), never name an older drawing they can't know, and record today's fingerprint.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaRebuildHistoryOldSaves1120e"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaRebuildHistoryOldSaves1120e")
	public void aSaveFromBeforeFingerprintsGoesOnWithTodaysDrawing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> failures = new ArrayList<>();
		for (String family : RECENT) {
			ResourceLocation id = AliveWorkplace.id(family);
			BuildSite site = planned(helper, id);
			BuildPlan plan = site.plan(level);
			if (plan == null) {
				failures.add(family + ": no blueprint");
				continue;
			}
			int walls = plan.steps(BuildPlan.Stage.STRUCTURE).size();
			CompoundTag save = site.save();
			int hash = save.getInt("blueprint_hash");
			save.remove("blueprint_hash");
			save.remove("built_from");
			save.remove("built_from_of");
			save.putString("stage", BuildPlan.Stage.STRUCTURE.name());
			save.putInt("cursor", walls / 2);
			save.putInt("placed", walls / 2);
			BuildSite loaded = BuildSite.load(save);
			BuildPlan again = loaded == null ? null : loaded.plan(level);
			if (again == null) {
				failures.add(family + ": no plan after the load");
				continue;
			}
			CompoundTag resaved = loaded.save();
			if (loaded.stage() != BuildPlan.Stage.STRUCTURE || loaded.cursor() != walls / 2 || loaded.current(again) == null) {
				failures.add(family + ": loaded as " + loaded.stage() + " cursor " + loaded.cursor() + " of " + walls + " (wanted " + walls / 2 + ")");
			} else if (loaded.builtFrom() != null && !loaded.builtFrom().isEmpty()) {
				failures.add(family + ": named the older drawing '" + loaded.builtFrom() + "'");
			} else if (differences(again.targets(), plan.targets()) != 0) {
				failures.add(family + ": plans " + differences(again.targets(), plan.targets()) + " spots unlike today's drawing");
			} else if (!resaved.contains("blueprint_hash") || resaved.getInt("blueprint_hash") != hash) {
				failures.add(family + ": today's fingerprint not recorded (" + resaved.get("blueprint_hash") + " vs " + hash + ")");
			}
		}
		helper.assertTrue(failures.isEmpty(), failures.size() + " older saves: " + brief(failures));
		helper.succeed();
	}

	/**
	 * A save naming an older drawing the jar no longer ships (Refreshes#drawing: "null, or that drawing no longer shipped:
	 * today's"), and one naming today's ("" ), half-way through the walls of each recently rebuilt family: both plan
	 * today's drawing, no crash, no missing blueprint, the place in the walls inside the list.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaRebuildHistoryGoneKey1120e"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaRebuildHistoryGoneKey1120e")
	public void aSaveNamingADrawingNoLongerShippedGoesOnWithTodays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> failures = new ArrayList<>();
		for (String family : RECENT) {
			ResourceLocation id = AliveWorkplace.id(family);
			BuildSite site = planned(helper, id);
			BuildPlan plan = site.plan(level);
			if (plan == null) {
				failures.add(family + ": no blueprint");
				continue;
			}
			int walls = plan.steps(BuildPlan.Stage.STRUCTURE).size();
			for (String from : List.of("0gone0000", "")) {
				CompoundTag save = site.save();
				save.putString("built_from", from);
				save.putString("stage", BuildPlan.Stage.STRUCTURE.name());
				save.putInt("cursor", walls / 2);
				BuildSite loaded = BuildSite.load(save);
				BuildPlan again = loaded == null ? null : loaded.plan(level);
				String name = family + " built from '" + from + "'";
				if (again == null || loaded.status() == BuildSite.Status.BLUEPRINT_MISSING) {
					failures.add(name + ": no plan after the load");
				} else if (differences(again.targets(), plan.targets()) != 0) {
					failures.add(name + ": plans " + differences(again.targets(), plan.targets()) + " spots unlike today's drawing");
				} else if (loaded.stage() != BuildPlan.Stage.STRUCTURE || loaded.cursor() != walls / 2 || loaded.current(again) == null) {
					failures.add(name + ": loaded as " + loaded.stage() + " cursor " + loaded.cursor() + " of " + walls);
				}
			}
		}
		helper.assertTrue(failures.isEmpty(), failures.size() + " saves: " + brief(failures));
		helper.succeed();
	}
}
