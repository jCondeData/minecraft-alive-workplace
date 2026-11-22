package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * QA bug test (lane C, 2026-11-22, lane-c-1122-0653; not registered in the suite): the owner's rule 3, "plates and
 * crockery anywhere but a kitchen or a table with a chair at it", for the Supply Shop as a server with Handcrafted
 * furnishes it. B1450 took the plates off the modded copy's display tables, but the shop's furniture spots file
 * (blueprint_furniture/supply_shop*.json) has crockery spots on the shelves at (2, 3, 3) and (8, 3, 3) (a flower pot and
 * a potted cactus in the vanilla build), which Handcrafted turns into a white crockery set: plates in a hardware shop
 * with no stove, furnace, smoker or sink and no seat. check.py lets them through only because its crockery rule counts
 * the shop's sales counter spots as a kitchen (the rule B1450 kept).
 */
public class QaShopCrockery1122cBugCompatTests implements FabricGameTest {
	/** The Supply Shop I-IV furnished with Handcrafted and Beautify, as a new build on this server: no crockery anywhere. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFurnishedSupplyShopHasNoCrockeryAnywhere(GameTestHelper helper) {
		List<String> found = new ArrayList<>();
		try {
			new WorkplaceConfig().apply();
			Furnishings.MODS = List.of("handcrafted", "beautify");
			Furnishings.ENABLED = true;
			for (String name : List.of("supply_shop", "supply_shop_2", "supply_shop_3", "supply_shop_4")) {
				ResourceLocation id = AliveWorkplace.id(name);
				Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new GameTestAssertException("(setup) no " + name));
				FurnitureChoice choice = FurnitureChoice.forNewBuild(id);
				for (Blueprint.Entry e : choice.apply(base, id).blocks()) {
					if (QaLabShopLadders1122cCompatTests.crockery(e.state())) {
						found.add(name + " " + e.pos().toShortString() + " " + BuiltInRegistries.BLOCK.getKey(e.state().getBlock()));
					}
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(found.isEmpty(), "crockery in the furnished Supply Shop (no kitchen, no seat): " + found);
		helper.succeed();
	}
}
