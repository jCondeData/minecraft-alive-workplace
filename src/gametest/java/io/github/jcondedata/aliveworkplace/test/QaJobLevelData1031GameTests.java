package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * QA (lane-e-1031-1613, the player-facing sentences; verifies B707): every {@code job_level} requirement the mod's own
 * data asks for (the Wonders' stages, the Legends, quests, story arcs, Steward rules and the other files that hold
 * conditions) reads in the trade's own plural on its line, as a player sees it on the hall's pages: "Fishermen at
 * Master or above: 0 of 1", never "Fishermans", never the singular fallback "Villagers working as ..." (that is for
 * another mod's trade), never an untranslated key. And each names a trade the game has, so the requirement can be met.
 */
public class QaJobLevelData1031GameTests {
	private static final List<String> FOLDERS = List.of("wonders", "legends", "quests", "arcs", "steward_rules", "guilds", "bounties",
		"realm_research", "research_trees", "edicts", "cups", "classes", "disasters", "heart_events", "services");

	private static void find(JsonElement json, List<JsonObject> out) {
		if (json.isJsonObject()) {
			JsonObject o = json.getAsJsonObject();
			if (o.has("type") && o.get("type").isJsonPrimitive() && "job_level".equals(o.get("type").getAsString())) {
				out.add(o);
			}
			o.entrySet().forEach(e -> find(e.getValue(), out));
		} else if (json.isJsonArray()) {
			json.getAsJsonArray().forEach(e -> find(e, out));
		}
	}

	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '40' '"qaJobLevelData"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 40, batch = "qaJobLevelData")
	public void everyJobLevelTheModAsksForReadsInThePlural(GameTestHelper helper) throws Exception {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(9, 2, 9), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(9, 2, 9));
		ResourceManager manager = helper.getLevel().getServer().getResourceManager();
		int found = 0;
		List<String> bad = new ArrayList<>();
		for (String folder : FOLDERS) {
			for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(folder, p -> p.getPath().endsWith(".json")).entrySet()) {
				if (!AliveWorkplace.MOD_ID.equals(e.getKey().getNamespace())) {
					continue; // the mod's own data only (the test pack's broken files are its own business)
				}
				List<JsonObject> conditions = new ArrayList<>();
				try (Reader reader = e.getValue().openAsReader()) {
					find(JsonParser.parseReader(reader), conditions);
				}
				for (JsonObject c : conditions) {
					found++;
					ResourceLocation job = ResourceLocation.parse(c.get("job").getAsString());
					if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job)) {
						bad.add(e.getKey() + ": " + job + " isn't a trade the game has");
						continue;
					}
					String line = Conditions.parse(c).progress(helper.getLevel(), hall).line().getString();
					if (line.startsWith("Villagers working as") || line.contains("entity.minecraft.villager") || line.contains("plural.aliveworkplace")
						|| line.contains("mans at ") || line.contains("merchant.level") || !line.contains(" or above: ")) {
						bad.add(e.getKey() + ": " + line);
					}
				}
			}
		}
		helper.assertTrue(found >= 9, "found only " + found + " job_level requirements in the mod's data (the 8 Wonders and the Pathfinder ask for some)");
		helper.assertTrue(bad.isEmpty(), "job_level lines that don't read right: " + bad);
		helper.succeed();
	}
}
