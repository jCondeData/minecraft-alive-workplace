package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA bugs found for ROADMAP 37.7 (not registered: they fail until the mod is fixed). 37.7 says job blocks are swapped only
 * where Carved Wood's version is the same villager job site; the crafting table is the Carpenter's job site
 * ({@code aliveworkplace:crafting_table}), but the Spruce style swaps it for Carved Wood's spruce crafting table, which
 * is no job site.
 */
public class QaCarvedLibraryBugTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSpruceStyleKeepsTheCarpentersCraftingTable(GameTestHelper helper) {
		boolean was = Furnishings.ENABLED;
		try {
			Furnishings.ENABLED = true;
			BlueprintStyles.Style spruce = BlueprintStyles.get("spruce").orElseThrow(() -> new GameTestAssertException("no spruce style"));
			helper.assertTrue(PoiTypes.forState(Blocks.CRAFTING_TABLE.defaultBlockState()).map(h -> h.is(ModVillagers.CRAFTING_TABLE_POI)).orElse(false),
				"the vanilla crafting table is no longer the Carpenter's job site: this test checks nothing");
			BlockState styled = BlueprintStyles.apply(spruce, Blocks.CRAFTING_TABLE.defaultBlockState());
			helper.assertTrue(PoiTypes.forState(styled).map(h -> h.is(ModVillagers.CRAFTING_TABLE_POI)).orElse(false),
				"the Spruce style's crafting table " + BuiltInRegistries.BLOCK.getKey(styled.getBlock()) + " is no Carpenter's job site");
		} finally {
			Furnishings.ENABLED = was;
		}
		helper.succeed();
	}
}
