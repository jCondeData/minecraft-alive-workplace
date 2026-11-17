package io.github.jcondedata.aliveworkplace.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;

/**
 * QA (lane E, saves, 2026-11-17): CLAUDE.md's "Don't break existing saves: registry ids are never renamed". A world
 * stores this mod's blocks, items, entities, block entities, professions, workstations (POIs), menus, data components and
 * game rules by id; an id that disappears empties those blocks to air, drops those items and villagers' jobs, and a
 * renamed game rule goes back to its default. Every id the released 0.156.0 registered (the fixture, read from the
 * registries of main's code, which is 0.156.0's, on 2026-11-17) must still be registered, and every game rule still
 * known. New ids are fine. Remaking the fixture: run it with the environment variable {@code ALIVEWORKPLACE_DUMP_IDS=<file>}.
 */
public class QaSavedIdsStay1117eGameTests implements FabricGameTest {
	static final String FIXTURE = "/fixtures/registry_ids_0.156.0.txt";
	static final String NAMESPACE = "aliveworkplace";

	/** Every id of ours now, as "registry id" lines: each built-in registry's entries, then "gamerule name". */
	static TreeSet<String> now() {
		TreeSet<String> out = new TreeSet<>();
		for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
			ResourceLocation name = registry.key().location();
			for (ResourceLocation id : registry.keySet()) {
				if (id.getNamespace().equals(NAMESPACE)) {
					out.add(name + " " + id);
				}
			}
		}
		GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
			@Override
			public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
				if (key.getId().startsWith("workplace")) { // ours are workplaceFreeMaterials, workplaceBuildDelay...
					out.add("gamerule " + key.getId());
				}
			}
		});
		return out;
	}

	static List<String> fixture() throws IOException {
		try (InputStream in = QaSavedIdsStay1117eGameTests.class.getResourceAsStream(FIXTURE)) {
			if (in == null) {
				throw new IOException("no " + FIXTURE);
			}
			List<String> lines = new ArrayList<>();
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				if (!line.isBlank() && !line.startsWith("#")) {
					lines.add(line.trim());
				}
			}
			return lines;
		}
	}

	/** Every id 0.156.0 registered is registered still (a save made with it loads its blocks, items, jobs and rules). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyIdTheReleaseSavedIsStillRegistered(GameTestHelper helper) throws IOException {
		TreeSet<String> now = now();
		String dump = System.getenv("ALIVEWORKPLACE_DUMP_IDS");
		if (dump != null) {
			Files.writeString(Path.of(dump), "# Every registry id and game rule of ours in 0.156.0 (QaSavedIdsStay1117eGameTests)\n"
				+ String.join("\n", now) + "\n");
		}
		List<String> released = fixture();
		helper.assertTrue(released.size() > 100, "the fixture lists only " + released.size() + " ids");
		List<String> gone = released.stream().filter(id -> !now.contains(id)).toList();
		helper.assertTrue(gone.isEmpty(), gone.size() + " id(s) a 0.156.0 world saved are no longer registered (renamed or removed: "
			+ "those blocks load as air, the items vanish, the rules reset): " + String.join(", ", gone.subList(0, Math.min(20, gone.size()))));
		helper.succeed();
	}

	/** The fixture covers the kinds of ids a world keeps: blocks, items, entities, block entities, professions, POIs, rules. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theFixtureCoversEveryKindOfSavedId(GameTestHelper helper) throws IOException {
		List<String> released = fixture();
		List<String> missing = new ArrayList<>();
		for (String kind : List.of("minecraft:block ", "minecraft:item ", "minecraft:entity_type ", "minecraft:block_entity_type ",
			"minecraft:villager_profession ", "minecraft:point_of_interest_type ", "minecraft:menu ", "gamerule ")) {
			if (released.stream().noneMatch(l -> l.startsWith(kind))) {
				missing.add(kind.trim());
			}
		}
		helper.assertTrue(missing.isEmpty(), "the fixture has no " + missing);
		helper.succeed();
	}
}
