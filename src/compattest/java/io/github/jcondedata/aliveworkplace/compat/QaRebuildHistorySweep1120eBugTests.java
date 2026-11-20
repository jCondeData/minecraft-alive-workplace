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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA lane E (2026-11-20): B1371 for a build from the MODDED library (37.12, the owner's server: the pack's furniture mods
 * installed). A site mid-build saves the fingerprint of the vanilla drawing of its day ({@code drawn}, before the
 * furniture and the modded copy are applied); a later jar finds that drawing's key in the VANILLA history
 * (Refreshes#drawnAs) and then looks the drawing up under the same key in the MODDED history (olderAsBuilt, historyId).
 * history.py ships a version only when its content is new, so where the modded copy didn't change in the commit that
 * changed the vanilla drawing, the modded copy of that day is shipped under ANOTHER key (git: `git show <rev>:.../structure/
 * modded/<id>.nbt` equals blueprint_history/modded/<id>/<other key>.nbt). Wanted (REBUILD.md "sites mid-build keep their
 * old drawing until a refresh"; BuildSite#builtFrom B1371): the site goes on with the modded copy of its day.
 *
 * <p>Each case: {@code <blueprint> <vanilla key the site was begun from> <modded key holding that day's modded copy>},
 * worked out from git by comparing every shipped vanilla key's revision with the modded history. The cases whose two
 * keys are equal are controls (the same commit changed both copies) and are expected to pass.
 *
 * <p>Not registered (it fails on today's code); register it in src/compattest/resources/fabric.mod.json once fixed.
 */
public class QaRebuildHistorySweep1120eBugTests implements FabricGameTest {
	private static final String DIR = "blueprint_history";

	/** From git (2026-11-20, main): blueprint, vanilla key, the modded key shipping that day's modded copy. */
	private static final List<String> CASES = List.of(
		// controls: both copies changed in the same commit
		"farmstead_3 a12542b71 a12542b71", "smithy_2 74f5b140b 74f5b140b", "terrace 11b48d517 11b48d517",
		// the modded copy of the day shipped under another key
		"artisans_house_2 aec09cb26 v0.145.0", "artisans_house_3 aec09cb26 v0.145.0", "carpenters_workshop 77e3792bf v0.145.0",
		"healing_center_2 aec09cb26 v0.145.0", "healing_center_3 1451f6936 a12542b71", "healing_center_3 aec09cb26 v0.145.0",
		"healing_center_4 1451f6936 a12542b71", "healing_center_4 aec09cb26 v0.145.0", "inn aec09cb26 v0.145.0",
		"inn v0.154.0 92b57580e", "inn_2 aec09cb26 v0.145.0", "inn_2 v0.154.0 92b57580e", "inn_3 aec09cb26 v0.145.0",
		"inn_3 v0.154.0 92b57580e", "library aec09cb26 v0.145.0", "library_2 aec09cb26 v0.145.0", "library_3 aec09cb26 v0.145.0",
		"library_4 aec09cb26 v0.145.0", "manor aec09cb26 v0.145.0", "manor_2 aec09cb26 v0.145.0", "manor_3 aec09cb26 v0.145.0",
		"manor_4 aec09cb26 v0.145.0", "schoolhouse aec09cb26 v0.145.0", "schoolhouse_2 aec09cb26 v0.145.0",
		"starter_cottage 15f3b652a v0.145.0", "starter_cottage_2 15f3b652a v0.145.0", "starter_cottage_3 15f3b652a v0.145.0",
		"starter_cottage_4 15f3b652a v0.145.0", "stone_house aec09cb26 v0.145.0", "stone_house_2 aec09cb26 v0.145.0",
		"stone_house_3 aec09cb26 v0.145.0", "stone_house_4 aec09cb26 v0.145.0", "supply_shop aec09cb26 v0.145.0",
		"supply_shop_2 aec09cb26 v0.145.0", "supply_shop_3 aec09cb26 v0.145.0", "supply_shop_4 aec09cb26 v0.145.0",
		"townhouse_2 aec09cb26 v0.145.0", "townhouse_3 aec09cb26 v0.145.0", "townhouse_4 aec09cb26 v0.145.0");

	private static CompoundTag read(ServerLevel level, ResourceLocation structure, String key) throws java.io.IOException {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(structure.getNamespace(), DIR + "/" + structure.getPath() + "/" + key + ".nbt");
		try (InputStream in = level.getServer().getResourceManager().getResource(file).orElseThrow().open()) {
			return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		}
	}

	private static BuildSite site(GameTestHelper helper, ResourceLocation id, FurnitureChoice furniture) {
		BuildSite site = new BuildSite(new UUID(1120, id.hashCode()), new UUID(1120, 2), "Jesse", id,
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
	 * A modded build begun from each case's older drawing (the vanilla drawing of its day made today's, so the save holds
	 * its fingerprint as that jar saved it), saved half-way through its walls, loaded by today's jar: it must plan the
	 * modded copy of its day (the case's modded key), not today's modded copy.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaRebuildHistoryModded1120e"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaRebuildHistoryModded1120e")
	public void aModdedSiteMidBuildKeepsTheModdedCopyOfItsDayAfterTheRebuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ModdedLibrary.ENABLED, "(setup) the modded library is off in the compat run");
		FurnitureChoice modded = FurnitureChoice.VANILLA.withLibrary(true);
		List<String> failures = new ArrayList<>();
		List<String> controls = new ArrayList<>();
		int checked = 0;
		for (String c : CASES) {
			String[] p = c.split(" ");
			ResourceLocation id = AliveWorkplace.id(p[0]);
			String begun = p[1], ofDay = p[2];
			boolean control = begun.equals(ofDay);
			List<String> out = control ? controls : failures;
			if (!ModdedLibrary.use(id)) {
				out.add(c + ": (setup) no complete modded copy here");
				continue;
			}
			try {
				// today's modded build, and the modded copy of the day as a build begun from it is built
				BuildPlan today = site(helper, id, modded).plan(level);
				BuildSite reference = site(helper, id, modded);
				reference.setBuiltFrom(null, ofDay);
				BuildPlan old = reference.plan(level);
				if (today == null || old == null || differences(today.targets(), old.targets()) == 0) {
					out.add(c + ": (setup) the modded copy of its day doesn't plan apart from today's");
					continue;
				}
				// the jar of its day: its vanilla drawing today's; the site saved half-way through the walls
				CompoundTag save;
				level.getStructureManager().remove(id);
				level.getStructureManager().getOrCreate(id).load(Lookup.lookup(BuiltInRegistries.BLOCK), read(level, id, begun));
				try {
					BuildSite then = site(helper, id, modded);
					BuildPlan plan = then.plan(level);
					if (plan == null) {
						out.add(c + ": (setup) the older vanilla drawing didn't plan");
						continue;
					}
					save = then.save();
					save.putString("stage", BuildPlan.Stage.STRUCTURE.name());
					save.putInt("cursor", plan.steps(BuildPlan.Stage.STRUCTURE).size() / 2);
				} finally {
					level.getStructureManager().remove(id);
				}
				checked++;
				BuildSite loaded = BuildSite.load(save);
				BuildPlan plan = loaded == null ? null : loaded.plan(level);
				if (plan == null) {
					out.add(c + ": no plan after the load");
					continue;
				}
				int offOld = differences(plan.targets(), old.targets());
				int offToday = differences(plan.targets(), today.targets());
				if (offOld != 0) {
					out.add(p[0] + " begun from " + begun + ": loaded as built from '" + loaded.builtFrom() + "', stage " + loaded.stage()
						+ " cursor " + loaded.cursor() + "; plans " + offOld + " spots unlike the modded copy of its day (" + ofDay + "), "
						+ offToday + " unlike today's");
				}
			} catch (Exception ex) {
				out.add(c + ": " + ex);
			}
		}
		AliveWorkplace.LOG.info("QaRebuildHistorySweep1120eBugTests: {} cases checked, controls failing: {}", checked, controls);
		helper.assertTrue(controls.isEmpty(), "(harness) the controls failed: " + controls);
		if (!failures.isEmpty()) {
			AliveWorkplace.LOG.error("QaRebuildHistorySweep1120eBugTests: {}", failures);
		}
		helper.assertTrue(failures.isEmpty(), failures.size() + " of " + (CASES.size() - 3) + " modded builds begun from an older drawing "
			+ "restart on today's modded copy after the rebuild (old half + new; details in the log), e.g. " + failures.get(0).substring(0,
			Math.min(300, failures.get(0).length())));
		helper.succeed();
	}
}
