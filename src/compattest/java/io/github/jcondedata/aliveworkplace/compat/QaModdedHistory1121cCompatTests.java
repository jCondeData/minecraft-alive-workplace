package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA of B1407's fix (lane-c-1121-1555), from the spec (tools/blueprints/REBUILD.md: "sites mid-build keep their old
 * drawing until a refresh"): a modded (furnished) build half-built from an older drawing keeps the modded copy of its day
 * after an update, through the aliases the jar ships. QaRebuildHistorySweep1120eBugTests checks one load of each case;
 * these add a site saved and loaded twice (its save keeps the vanilla key), a vanilla build of the same day untouched by
 * the modded aliases, a key with no alias (an older drawing whose day has no modded copy, and an unknown key) falling back
 * to today's modded copy without a crash, and every shipped alias pointing at a modded drawing that is really shipped.
 */
public class QaModdedHistory1121cCompatTests implements FabricGameTest {
	private static final String DIR = "blueprint_history";

	private interface Body {
		void run() throws java.io.IOException;
	}

	/** Runs {@code body} on the next tick, where a failed check fails the test instead of stopping the test server. */
	private static void later(GameTestHelper helper, Body body) {
		helper.runAfterDelay(1, () -> {
			try {
				body.run();
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}

	private static CompoundTag read(ServerLevel level, ResourceLocation structure, String key) throws java.io.IOException {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(structure.getNamespace(), DIR + "/" + structure.getPath() + "/" + key + ".nbt");
		try (InputStream in = level.getServer().getResourceManager().getResource(file).orElseThrow().open()) {
			return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		}
	}

	private static BuildSite site(GameTestHelper helper, ResourceLocation id, FurnitureChoice furniture) {
		BuildSite site = new BuildSite(new UUID(1121, id.hashCode()), new UUID(1121, 3), "Jesse", id,
			new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(0, 2, 0)), Rotation.NONE, Mirror.NONE));
		site.setFurniture(furniture);
		return site;
	}

	private static int differences(Map<BlockPos, BlockState> a, Map<BlockPos, BlockState> b) {
		Set<BlockPos> spots = new HashSet<>(a.keySet());
		spots.addAll(b.keySet());
		int n = 0;
		for (BlockPos p : spots) {
			BlockState x = a.get(p), y = b.get(p);
			boolean airX = x == null || x.isAir(), airY = y == null || y.isAir();
			if (airX || airY ? airX != airY : !MaterialRules.matches(x, y)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * A site begun by the jar whose drawing of {@code id} was the vanilla history's {@code begun}, saved half-way through
	 * its walls (the save that jar wrote).
	 */
	private static CompoundTag savedMidBuild(GameTestHelper helper, ResourceLocation id, String begun, FurnitureChoice furniture) throws java.io.IOException {
		ServerLevel level = helper.getLevel();
		level.getStructureManager().remove(id);
		level.getStructureManager().getOrCreate(id).load(Lookup.lookup(BuiltInRegistries.BLOCK), read(level, id, begun));
		try {
			BuildSite then = site(helper, id, furniture);
			BuildPlan plan = then.plan(level);
			helper.assertTrue(plan != null, "(setup) the older drawing " + begun + " of " + id + " didn't plan");
			CompoundTag save = then.save();
			save.putString("stage", BuildPlan.Stage.STRUCTURE.name());
			save.putInt("cursor", plan.steps(BuildPlan.Stage.STRUCTURE).size() / 2);
			return save;
		} finally {
			level.getStructureManager().remove(id);
		}
	}

	/** The plan of a site of {@code id} worked by the older drawing {@code key} (setBuiltFrom), today's for an unknown key. */
	private static BuildPlan planFrom(GameTestHelper helper, ResourceLocation id, FurnitureChoice furniture, String key) {
		BuildSite s = site(helper, id, furniture);
		s.setBuiltFrom(null, key);
		return s.plan(helper.getLevel());
	}

	/**
	 * A modded library build begun from library's vanilla drawing aec09cb26 (its modded copy of that day ships as v0.145.0,
	 * aliased), saved half-way, loaded by today's jar, saved again and loaded again (two server restarts mid-build): both
	 * loads plan the modded copy of its day, and the save keeps the vanilla key it was begun from. The inn's second alias
	 * (v0.154.0, shipped as 92b57580e) the same.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaModdedHistory1121cTwice"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaModdedHistory1121cTwice")
	public void aModdedSiteSavedAndLoadedTwiceMidBuildKeepsItsDaysModdedCopy(GameTestHelper helper) {
		later(helper, () -> twice(helper));
	}

	private static void twice(GameTestHelper helper) throws java.io.IOException {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ModdedLibrary.ENABLED, "(setup) the modded library is off in the compat run");
		FurnitureChoice modded = FurnitureChoice.VANILLA.withLibrary(true);
		List<String> off = new ArrayList<>();
		for (String c : List.of("library aec09cb26 v0.145.0", "inn v0.154.0 92b57580e")) {
			String[] p = c.split(" ");
			ResourceLocation id = AliveWorkplace.id(p[0]);
			helper.assertTrue(ModdedLibrary.use(id), "(setup) no complete modded copy of " + id);
			BuildPlan today = site(helper, id, modded).plan(level);
			BuildPlan old = planFrom(helper, id, modded, p[2]);
			helper.assertTrue(today != null && old != null && differences(today.targets(), old.targets()) > 0,
				"(setup) " + c + ": the modded copy of its day doesn't plan apart from today's");
			CompoundTag save = savedMidBuild(helper, id, p[1], modded);
			for (int load = 1; load <= 2; load++) {
				BuildSite loaded = BuildSite.load(save);
				BuildPlan plan = loaded == null ? null : loaded.plan(level);
				if (plan == null) {
					off.add(c + ", load " + load + ": no plan");
					break;
				}
				int offOld = differences(plan.targets(), old.targets());
				if (offOld != 0 || !p[1].equals(loaded.builtFrom())) {
					off.add(c + ", load " + load + ": built from '" + loaded.builtFrom() + "' (wanted the vanilla key " + p[1] + "), plans " + offOld + " spots unlike the modded copy of its day, " + differences(plan.targets(), today.targets())
						+ " unlike today's");
				}
				save = loaded.save();
			}
		}
		helper.assertTrue(off.isEmpty(), "after saves and loads mid-build: " + off);
		helper.succeed();
	}

	/**
	 * The vanilla build of the same blueprint and day (no furniture mods' library): begun from library's aec09cb26, saved
	 * half-way and loaded, it plans the vanilla drawing aec09cb26, never the modded copy the alias names nor today's.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaModdedHistory1121cVanilla"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaModdedHistory1121cVanilla")
	public void aVanillaSiteOfTheSameDayKeepsItsVanillaDrawing(GameTestHelper helper) {
		later(helper, () -> vanilla(helper));
	}

	private static void vanilla(GameTestHelper helper) throws java.io.IOException {
		ServerLevel level = helper.getLevel();
		FurnitureChoice vanilla = FurnitureChoice.VANILLA;
		FurnitureChoice modded = FurnitureChoice.VANILLA.withLibrary(true);
		ResourceLocation id = AliveWorkplace.id("library");
		helper.assertTrue(!vanilla.modded(), "(setup) the vanilla choice is modded");
		BuildPlan today = site(helper, id, vanilla).plan(level);
		BuildPlan old = planFrom(helper, id, vanilla, "aec09cb26");
		BuildPlan moddedOld = planFrom(helper, id, modded, "v0.145.0");
		helper.assertTrue(today != null && old != null && moddedOld != null && differences(today.targets(), old.targets()) > 0
			&& differences(old.targets(), moddedOld.targets()) > 0, "(setup) the vanilla drawing aec09cb26 doesn't plan apart from today's and the modded one");
		BuildSite loaded = BuildSite.load(savedMidBuild(helper, id, "aec09cb26", vanilla));
		BuildPlan plan = loaded == null ? null : loaded.plan(level);
		helper.assertTrue(plan != null, "a vanilla site of the library didn't plan after the load");
		int offOld = differences(plan.targets(), old.targets());
		helper.assertTrue(offOld == 0, "a vanilla library site begun from aec09cb26, loaded: built from '" + loaded.builtFrom() + "', plans " + offOld
			+ " spots unlike that drawing (" + differences(plan.targets(), moddedOld.targets()) + " unlike the modded copy, "
			+ differences(plan.targets(), today.targets()) + " unlike today's)");
		helper.succeed();
	}

	/**
	 * A key with no alias falls back safely: a modded inn begun from the vanilla drawing 42864073a (its day has no modded
	 * copy shipped and no alias) loads without an error and plans today's modded copy (the B1049 restart, nothing half of
	 * a drawing that isn't shipped); a modded site set to an unknown key plans today's modded copy too.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaModdedHistory1121cNoAlias"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaModdedHistory1121cNoAlias")
	public void aKeyWithNoAliasFallsBackToTodaysModdedCopy(GameTestHelper helper) {
		later(helper, () -> noAlias(helper));
	}

	private static void noAlias(GameTestHelper helper) throws java.io.IOException {
		ServerLevel level = helper.getLevel();
		FurnitureChoice modded = FurnitureChoice.VANILLA.withLibrary(true);
		ResourceLocation id = AliveWorkplace.id("inn");
		helper.assertTrue(ModdedLibrary.use(id), "(setup) no complete modded copy of the inn");
		BuildPlan today = site(helper, id, modded).plan(level);
		helper.assertTrue(today != null, "(setup) today's modded inn didn't plan");
		BuildSite loaded = BuildSite.load(savedMidBuild(helper, id, "42864073a", modded));
		BuildPlan plan = loaded == null ? null : loaded.plan(level);
		helper.assertTrue(plan != null && differences(plan.targets(), today.targets()) == 0,
			"a modded inn begun from 42864073a (no modded copy, no alias), loaded: " + (plan == null ? "no plan"
				: "built from '" + loaded.builtFrom() + "', " + differences(plan.targets(), today.targets()) + " spots unlike today's modded copy"));
		BuildPlan unknown = planFrom(helper, id, modded, "0000000ff");
		helper.assertTrue(unknown != null && differences(unknown.targets(), today.targets()) == 0,
			"a modded inn set to an unknown older key: " + (unknown == null ? "no plan" : differences(unknown.targets(), today.targets()) + " spots unlike today's"));
		helper.succeed();
	}

	/**
	 * Every alias the jar ships (blueprint_history/modded/&lt;id&gt;/aliases.json) names a modded drawing that is shipped in
	 * the same folder, and its key is a vanilla drawing shipped for that blueprint: an alias to nothing would send a site
	 * back to today's copy, the bug again.
	 */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '100'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void everyShippedAliasNamesAShippedDrawing(GameTestHelper helper) {
		later(helper, () -> aliases(helper));
	}

	private static void aliases(GameTestHelper helper) {
		var resources = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, Resource> files = resources.listResources(DIR + "/" + ModdedLibrary.PREFIX.substring(0, ModdedLibrary.PREFIX.length() - 1), f -> f.getPath().endsWith("/aliases.json"));
		List<String> off = new ArrayList<>();
		int aliases = 0;
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath();
			String folder = path.substring(0, path.length() - "aliases.json".length());
			String blueprint = folder.substring((DIR + "/" + ModdedLibrary.PREFIX).length(), folder.length() - 1);
			try (java.io.Reader in = e.getValue().openAsReader()) {
				for (Map.Entry<String, com.google.gson.JsonElement> a : com.google.gson.JsonParser.parseReader(in).getAsJsonObject().entrySet()) {
					aliases++;
					String target = a.getValue().getAsString();
					if (resources.getResource(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), folder + target + ".nbt")).isEmpty()) {
						off.add(blueprint + ": " + a.getKey() + " -> " + target + " (no such modded drawing)");
					}
					if (resources.getResource(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), DIR + "/" + blueprint + "/" + a.getKey() + ".nbt")).isEmpty()) {
						off.add(blueprint + ": alias key " + a.getKey() + " is no vanilla drawing shipped");
					}
				}
			} catch (Exception ex) {
				off.add(e.getKey() + ": " + ex);
			}
		}
		helper.assertTrue(files.size() >= 20 && aliases >= files.size(), "(setup) " + files.size() + " aliases files, " + aliases + " aliases");
		helper.assertTrue(off.isEmpty(), off.size() + " of " + aliases + " aliases are wrong: " + off);
		helper.succeed();
	}
}
