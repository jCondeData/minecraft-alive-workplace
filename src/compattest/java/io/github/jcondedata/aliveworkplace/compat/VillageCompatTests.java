package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Our houses in other mods' villages (Repurposed Structures). */
public class VillageCompatTests implements FabricGameTest {
	/** Every Repurposed Structures village we support has our workshop and staffed houses in its house pool, in the right style. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void repurposedStructuresVillagesGetOurHouses(GameTestHelper helper) {
		var pools = helper.getLevel().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		for (var entry : VillageHouses.MODDED_HOUSE_POOLS.entrySet()) {
			StructureTemplatePool pool = pools.get(entry.getKey());
			helper.assertTrue(pool != null, "no pool " + entry.getKey() + " (did Repurposed Structures rename it?)");
			String style = entry.getValue();
			java.util.List<String> houses = new java.util.ArrayList<>(VillageHouses.houseNames());
			houses.add("builders_workshop");
			helper.assertTrue(houses.contains("leaders_hall") && houses.contains("school") && houses.contains("trade_hall") && houses.contains("ball_workshop"),
				"Cobblemon is installed: the Pokémon houses should be in: " + houses);
			for (String house : houses) {
				String id = "aliveworkplace:village/" + style + "_" + house;
				boolean found = ((StructureTemplatePoolAccessor) pool).aliveworkplace$templates().stream().anyMatch(e -> e.toString().contains(id));
				helper.assertTrue(found, id + " missing from " + entry.getKey());
			}
		}
		// Nether and ocean villages are left alone.
		StructureTemplatePool crimson = pools.get(ResourceLocation.fromNamespaceAndPath("repurposed_structures", "villages/crimson/houses"));
		helper.assertTrue(crimson == null || ((StructureTemplatePoolAccessor) crimson).aliveworkplace$templates().stream()
			.noneMatch(e -> e.toString().contains("aliveworkplace:")), "our houses turned up in a nether village");
		helper.succeed();
	}
}
