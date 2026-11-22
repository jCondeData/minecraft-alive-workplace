package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Tester, lane d 2026-11-22 (the Research Lab rebuilt in 50.12, with Cobblemon): checks of the rebuilt Cobblemon
 * buildings from their briefs' words.
 */
public class ResearchLabJobSiteB1468CompatTests implements FabricGameTest {
	static final List<String> RESEARCH_LABS = List.of("research_lab", "research_lab_2", "research_lab_3",
		"modded/research_lab", "modded/research_lab_2", "modded/research_lab_3");

	/** The professions whose job site {@code poi} is, by their registered job-site test; empty when it is no job site. */
	static List<ResourceLocation> jobsAt(Holder<PoiType> poi) {
		List<ResourceLocation> jobs = new ArrayList<>();
		for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
			if (profession != VillagerProfession.NONE && profession.acquirableJobSite().test(poi)) {
				jobs.add(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession));
			}
		}
		return jobs;
	}

	/**
	 * B1468: the brief's "no other workstation anywhere": no block in any level of the Research Lab (and its modded copy)
	 * is a job site of any villager profession but the Fossil Analyzer, so no jobless villager walks in and takes a second
	 * job there (the old worktop was a crafting table, the Carpenter's job site).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theResearchLabsHaveNoJobSiteButTheAnalyzer(GameTestHelper helper) {
		List<String> stray = new ArrayList<>();
		for (String name : RESEARCH_LABS) {
			Optional<Blueprint> blueprint = BlueprintLibrary.get(helper.getLevel(), AliveWorkplace.id(name));
			helper.assertTrue(blueprint.isPresent(), "no drawing " + name);
			int analyzers = 0;
			for (Blueprint.Entry entry : blueprint.get().blocks()) {
				Optional<Holder<PoiType>> poi = PoiTypes.forState(entry.state());
				if (poi.isEmpty()) {
					continue;
				}
				if (poi.get().is(ModVillagers.FOSSIL_ANALYZER_POI)) {
					analyzers++;
					continue;
				}
				List<ResourceLocation> jobs = jobsAt(poi.get());
				if (!jobs.isEmpty()) {
					stray.add(name + " " + entry.pos().toShortString() + " "
						+ BuiltInRegistries.BLOCK.getKey(entry.state().getBlock()) + " (job site of " + jobs + ")");
				}
			}
			helper.assertTrue(analyzers > 0, name + " has no Fossil Analyzer");
		}
		helper.assertTrue(stray.isEmpty(), "job sites other than the Fossil Analyzer: " + stray);
		helper.succeed();
	}
}
