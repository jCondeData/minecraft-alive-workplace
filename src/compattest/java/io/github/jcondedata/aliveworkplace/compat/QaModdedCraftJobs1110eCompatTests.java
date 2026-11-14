package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

/**
 * QA (lane E, 2026-11-10, third run): Milestone 46 with the pack's other mods (Create, Supplementaries...). The
 * milestone's rule: "Nothing that is made today may become unmakable", and before 46.1 the Carpenter took any crafting
 * recipe, modded ones included (the milestone: "Today the Carpenter and the Mason take any recipe of their kind").
 * JobListsGameTests#everythingMadeBeforeStillHasAJob checks it without other mods; this checks every other mod's item
 * with a crafting-table recipe still has a job that makes it, with Create's Gearbox (a part Create builds need) by name.
 */
public class QaModdedCraftJobs1110eCompatTests implements FabricGameTest {

	/** Create's Gearbox (Andesite Casing and cogwheels at the crafting table) still has a job that makes it for builders. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void createsGearboxStillHasAJob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item shaft = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:gearbox"));
		helper.assertTrue(shaft != BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:air")), "Create isn't loaded");
		helper.assertTrue(!Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.CRAFTING, shaft).isEmpty(),
			"no crafting recipe for the Gearbox: the Carpenter didn't make it before 46.1 either");
		helper.assertTrue(CraftJobs.maker(level, shaft) != null, "no job makes Create's Gearbox since 46.1 (the Carpenter did before)");
		helper.succeed();
	}

	/** Every other mod's item with a crafting-table recipe (the Carpenter's before 46.1) still has a job that makes it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyModdedItemMadeBeforeStillHasAJob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, List<String>> lost = new TreeMap<>();
		int checked = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (id.getNamespace().equals("minecraft") || id.getNamespace().equals("aliveworkplace")) {
				continue;
			}
			if (Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.CRAFTING, item).isEmpty()) {
				continue;
			}
			checked++;
			if (CraftJobs.maker(level, item) == null) {
				lost.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id.getPath());
			}
		}
		StringBuilder out = new StringBuilder();
		int total = 0;
		for (var e : lost.entrySet()) {
			total += e.getValue().size();
			out.append(e.getKey()).append(" (").append(e.getValue().size()).append("): ")
				.append(String.join(", ", e.getValue().subList(0, Math.min(3, e.getValue().size()))))
				.append(e.getValue().size() > 3 ? ", ..." : "").append("; ");
		}
		helper.assertTrue(checked > 100, "only " + checked + " modded items have a crafting recipe?");
		helper.assertTrue(total == 0, total + " of " + checked + " modded items the Carpenter made before 46.1 have no job now: " + out);
		helper.succeed();
	}
}
