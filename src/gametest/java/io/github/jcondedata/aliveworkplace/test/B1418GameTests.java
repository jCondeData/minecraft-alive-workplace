package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * B1418: the first question about a build's older drawings after a start or a reload read every shipped older drawing
 * (over a thousand files) on the server thread, freezing the server for seconds the first time a Village Hall opened
 * (VillageAdvice's cellar tip, Cellars.needsDigging -> Refreshes.builtFrom). Now only the files of the blueprint asked
 * about are read, once; the answers are the same as reading them all (same keys, same order, same drawings).
 */
public class B1418GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String DIR = "blueprint_history";
	private static final ResourceLocation STONE_HOUSE = AliveWorkplace.id("stone_house");

	/**
	 * Every shipped older drawing by blueprint, sorted by key, read as Refreshes read the whole history before B1418 (the
	 * drawings of the blueprints in {@code parse} only; the others are listed with no drawing).
	 */
	private static Map<ResourceLocation, List<Map.Entry<String, Blueprint>>> readAll(MinecraftServer server, java.util.Set<ResourceLocation> parse) {
		Map<ResourceLocation, List<Map.Entry<String, Blueprint>>> out = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : server.getResourceManager().listResources(DIR, id -> id.getPath().endsWith(".nbt")).entrySet()) {
			String path = e.getKey().getPath().substring(DIR.length() + 1, e.getKey().getPath().length() - ".nbt".length());
			int slash = path.lastIndexOf('/');
			if (slash <= 0) {
				continue;
			}
			ResourceLocation structure = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(0, slash));
			Blueprint drawing = null;
			if (parse.contains(structure)) {
				try (InputStream in = e.getValue().open()) {
					CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
					drawing = StarterBlueprints.withBasement(StarterBlueprints.withFeatures(
						Blueprint.fromStructureNbt(structure, tag, Lookup.lookup(BuiltInRegistries.BLOCK))));
				} catch (Exception ex) {
					continue; // (as Refreshes skips an unreadable file)
				}
			}
			out.computeIfAbsent(structure, k -> new ArrayList<>()).add(new java.util.AbstractMap.SimpleEntry<>(path.substring(slash + 1), drawing));
		}
		out.values().forEach(list -> list.sort(Map.Entry.comparingByKey()));
		return out;
	}

	/**
	 * The hall's cellar question about a Stone House (what froze the first hall open) reads only the Stone House's older
	 * drawings, once, never the rest of the history; a blueprint with no older drawing reads nothing; a reload forgets them.
	 */
	//$ gametest_ticks_batch AREA '100' '"b1418History"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b1418History")
	public void b1418AskingOneBuildReadsOnlyItsOlderDrawings(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Map<ResourceLocation, List<Map.Entry<String, Blueprint>>> files = readAll(server, java.util.Set.of());
		int total = files.values().stream().mapToInt(List::size).sum();
		int stoneHouse = files.getOrDefault(STONE_HOUSE, List.of()).size();
		helper.assertTrue(stoneHouse > 0, "no older drawing of the Stone House is shipped");
		helper.assertTrue(total > stoneHouse * 10, "the history is too small to tell: " + total + " files, " + stoneHouse + " of the Stone House");

		Refreshes.forgetShipped(); // (as after a start or /reload)
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, Mirror.NONE);
		int before = Refreshes.filesRead();
		Cellars.needsDigging(helper.getLevel(), STONE_HOUSE, placement);
		int read = Refreshes.filesRead() - before;
		helper.assertTrue(read == stoneHouse, "the first cellar question read " + read + " drawing files, not the Stone House's " + stoneHouse
			+ " (of " + total + ")");

		before = Refreshes.filesRead();
		Cellars.needsDigging(helper.getLevel(), STONE_HOUSE, placement);
		Refreshes.olderKeys(server, STONE_HOUSE);
		Refreshes.olderKeys(server, AliveWorkplace.id("b1418_no_such_blueprint"));
		helper.assertTrue(Refreshes.filesRead() == before, "asked again (or about a blueprint with no older drawing), files were read: "
			+ (Refreshes.filesRead() - before));

		Refreshes.forgetShipped();
		before = Refreshes.filesRead();
		Refreshes.olderKeys(server, STONE_HOUSE);
		helper.assertTrue(Refreshes.filesRead() - before == stoneHouse, "after a reload the Stone House's drawings weren't read again");
		helper.succeed();
	}

	/**
	 * Read one blueprint at a time, the older drawings are those of reading the whole history at once: the same keys in the
	 * same order and the same drawings (a sample of the blueprints, a modded copy and a look among them).
	 */
	//$ gametest_ticks_batch AREA '200' '"b1418History"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b1418History")
	public void b1418EachBlueprintsHistoryIsAsTheWholeRead(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Map<ResourceLocation, List<Map.Entry<String, Blueprint>>> listed = readAll(server, java.util.Set.of());
		List<ResourceLocation> sample = new ArrayList<>();
		List<ResourceLocation> all = new ArrayList<>(listed.keySet());
		all.sort(Comparator.naturalOrder());
		for (int i = 0; i < all.size(); i += 15) {
			sample.add(all.get(i));
		}
		sample.add(STONE_HOUSE);
		all.stream().filter(s -> s.getPath().startsWith("modded/")).findFirst().ifPresent(sample::add);
		all.stream().filter(s -> s.getPath().startsWith("looks/")).findFirst().ifPresent(sample::add);
		Map<ResourceLocation, List<Map.Entry<String, Blueprint>>> whole = readAll(server, java.util.Set.copyOf(sample));

		Refreshes.forgetShipped();
		for (ResourceLocation structure : sample) {
			List<Map.Entry<String, Blueprint>> expected = whole.getOrDefault(structure, List.of());
			List<String> keys = Refreshes.olderKeys(server, structure);
			helper.assertTrue(keys.equals(expected.stream().map(Map.Entry::getKey).toList()),
				structure + ": keys " + keys + " not " + expected.stream().map(Map.Entry::getKey).toList());
			for (Map.Entry<String, Blueprint> e : expected) {
				Optional<Blueprint> got = Refreshes.olderDrawing(server, structure, e.getKey());
				helper.assertTrue(got.isPresent() && got.get().equals(e.getValue()), structure + " " + e.getKey() + ": not the drawing the whole read has");
			}
		}
		helper.succeed();
	}
}
