package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * ROADMAP 36.8: how many houses a village grows, per village type. Not a test: a measurement, registered in no
 * entrypoint, so {@code build} and {@code runGameTest} never run it. {@code tools/modtest/village_density.sh} registers
 * it alone, runs it and restores the test mod's entrypoints.
 *
 * <p>Generates 50 village starts per type (fixed seeds and chunks, no blocks placed) and counts each start's house
 * pieces: vanilla ones (a template under {@code minecraft:village/<type>/houses/}) and ours (an
 * {@code aliveworkplace:village/} template that isn't a villager), plus the start's footprint (its bounding box's
 * x by z). Prints one {@code VILLAGE-DENSITY} line per type.
 */
public class VillageDensityMeasure implements FabricGameTest {
	private static final int VILLAGES = Integer.getInteger("aliveworkplace.densityVillages", 50);
	private static final Pattern ID = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_/.-]+)");

	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '400'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void measureVillageDensity(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		List<ResourceKey<Structure>> keys = List.of(BuiltinStructures.VILLAGE_PLAINS, BuiltinStructures.VILLAGE_DESERT,
			BuiltinStructures.VILLAGE_SAVANNA, BuiltinStructures.VILLAGE_SNOWY, BuiltinStructures.VILLAGE_TAIGA);
		StringBuilder report = new StringBuilder();
		for (int t = 0; t < keys.size(); t++) {
			Structure village = structures.getOrThrow(keys.get(t));
			String type = keys.get(t).location().getPath().replace("village_", "");
			List<int[]> rows = new ArrayList<>();
			for (int i = 0; i < VILLAGES; i++) {
				long seed = 3608L * 1000 + t * 100 + i;
				ChunkPos chunk = new ChunkPos(4000 + i * 40, 4000 + t * 40);
				StructureStart start = village.generate(level.registryAccess(), generator, generator.getBiomeSource(),
					level.getChunkSource().randomState(), level.getStructureManager(), seed, chunk, 0, level, biome -> true);
				if (!start.isValid()) {
					continue;
				}
				int vanilla = 0;
				int ours = 0;
				for (StructurePiece piece : start.getPieces()) {
					if (!(piece instanceof PoolElementStructurePiece pool)) {
						continue;
					}
					Matcher m = ID.matcher(pool.getElement().toString());
					if (!m.find()) {
						continue;
					}
					String id = m.group(1);
					if (id.startsWith("minecraft:village/" + type + "/houses/")) {
						vanilla++;
					} else if (id.startsWith("aliveworkplace:village/") && !id.contains("/villagers/") && !id.contains("/workers")) {
						ours++;
					}
				}
				BoundingBox box = start.getBoundingBox();
				rows.add(new int[] {vanilla, ours, box.getXSpan() * box.getZSpan()});
			}
			int n = rows.size();
			double houses = rows.stream().mapToInt(r -> r[0] + r[1]).average().orElse(0);
			int lowest = rows.stream().mapToInt(r -> r[0] + r[1]).min().orElse(0);
			double vanilla = rows.stream().mapToInt(r -> r[0]).average().orElse(0);
			double ours = rows.stream().mapToInt(r -> r[1]).average().orElse(0);
			long withOurs = rows.stream().filter(r -> r[1] > 0).count();
			double footprint = rows.stream().mapToInt(r -> r[2]).average().orElse(0);
			String line = String.format(java.util.Locale.ROOT,
				"VILLAGE-DENSITY %s villages=%d houses_mean=%.2f houses_lowest=%d vanilla_mean=%.2f ours_mean=%.2f with_ours=%d/%d footprint_mean=%.0f",
				type, n, houses, lowest, vanilla, ours, withOurs, n, footprint);
			System.out.println(line);
			report.append(line).append('\n');
		}
		try {
			java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("aliveworkplace.densityOut", "village-density.txt")), report);
		} catch (java.io.IOException e) {
			System.out.println("VILLAGE-DENSITY could not write the report: " + e);
		}
		helper.succeed();
	}
}
