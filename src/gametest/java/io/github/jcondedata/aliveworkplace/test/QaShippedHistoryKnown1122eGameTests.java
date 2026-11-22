package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane E (2026-11-22, lane-e-1122-1913): B1371's promise for every drawing the mod ships, not only the Fire Pit
 * (QaRebuildShipped1122e). A player's site begun under an older jar saved the fingerprint of that jar's drawing, read
 * the way the blueprint library reads a structure file. Each file in blueprint_history is exactly such a jar's drawing,
 * so read the same way it must be recognised as one of its blueprint's older drawings; otherwise a site begun from it
 * restarts as the new drawing after the update. Also closes the mutation survivor at Refreshes.libraryPrint (reading the
 * history's own conversion instead of the file): it only matters for a file whose two readings differ.
 */
public class QaShippedHistoryKnown1122eGameTests implements FabricGameTest {

	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '200'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void everyShippedOlderDrawingIsKnownAsAnOlderDrawingOfItsBlueprint(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Method drawnAs;
		Method fingerprint;
		try {
			drawnAs = Refreshes.class.getDeclaredMethod("drawnAs", MinecraftServer.class, ResourceLocation.class, int.class);
			drawnAs.setAccessible(true);
			fingerprint = BuildSite.class.getDeclaredMethod("fingerprint", Blueprint.class);
			fingerprint.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("(setup) Refreshes.drawnAs or BuildSite.fingerprint is gone", e);
		}
		Map<ResourceLocation, Resource> files = server.getResourceManager().listResources("blueprint_history",
			id -> id.getNamespace().equals(AliveWorkplace.MOD_ID) && id.getPath().endsWith(".nbt"));
		helper.assertTrue(files.size() > 500, "(setup) only " + files.size() + " blueprint_history files found");
		List<String> unknown = new ArrayList<>();
		int checked = 0;
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath(); // blueprint_history/<blueprint path>/<key>.nbt
			int slash = path.lastIndexOf('/');
			String blueprintPath = path.substring("blueprint_history/".length(), slash);
			ResourceLocation structure = AliveWorkplace.id(blueprintPath);
			String key = path.substring(slash + 1, path.length() - ".nbt".length());
			try (InputStream in = e.getValue().open()) {
				CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
				StructureTemplate template = new StructureTemplate();
				template.load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
				Blueprint read = StarterBlueprints.withBasement(StarterBlueprints.withFeatures(
					Blueprint.fromTemplate(structure, template, Lookup.lookup(BuiltInRegistries.BLOCK))));
				int print = (int) fingerprint.invoke(null, read);
				Object answer = drawnAs.invoke(null, server, structure, print);
				if (answer == null) {
					unknown.add(blueprintPath + "/" + key);
				}
				checked++;
			} catch (Exception ex) {
				unknown.add(blueprintPath + "/" + key + " (" + ex + ")");
			}
		}
		helper.assertTrue(unknown.isEmpty(), unknown.size() + " of " + files.size() + " shipped older drawings, read as the jar"
			+ " that shipped them read them, aren't known as an older drawing of their blueprint (a site begun from one would"
			+ " restart as today's drawing): " + unknown.subList(0, Math.min(12, unknown.size())));
		AliveWorkplace.LOG.info("[qa1122e] all {} shipped older drawings are known as their blueprint's older drawings", checked);
		helper.succeed();
	}
}
