package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.city.Roads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

/**
 * B1467: every blueprint the mod ships that a player can see by name (the Blueprint Table, the blueprint item, the
 * Steward, a build site), every level ({@code _2} to {@code _4}) and every culture look ({@code looks/<culture>/...}),
 * has its own {@code blueprint.aliveworkplace.*} name in en_us.json, not the fallback made from its id ("Stone Wall 2"
 * where every other upgrade reads "Stone Wall II"). Named through {@link Blueprints#displayName}, as the game names them.
 * Left out: the pieces never shown by their own name ({@link BlueprintLibrary#isWorldgenPiece}: village pieces, modded
 * copies built under their vanilla build's id, Wonder parts named by their Wonder, camps and story pieces set down at
 * once) and a Steward's road segments.
 */
public class BlueprintLangNamesB1467GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	/** Every shipped blueprint shown by name reads its own lang name, not the one prettified from its id. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyShippedBlueprintHasALangName(GameTestHelper helper) {
		List<ResourceLocation> ids = helper.getLevel().getServer().getResourceManager()
			.listResources("structure", file -> file.getPath().endsWith(".nbt")).keySet().stream()
			.filter(file -> file.getNamespace().equals(AliveWorkplace.MOD_ID))
			.map(file -> ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				file.getPath().substring("structure/".length(), file.getPath().length() - ".nbt".length())))
			.filter(id -> !BlueprintLibrary.isWorldgenPiece(id) && !Roads.isSegment(id))
			.sorted()
			.toList();
		helper.assertTrue(ids.size() > 100, "only " + ids.size() + " shipped blueprints found");
		helper.assertTrue(ids.contains(AliveWorkplace.id("stone_wall_2")), "stone_wall_2 isn't among the shipped blueprints");
		helper.assertTrue(ids.contains(AliveWorkplace.id("looks/desert/starter_cottage_2")), "the culture looks aren't among the shipped blueprints");
		List<String> unnamed = new ArrayList<>();
		for (ResourceLocation id : ids) {
			Component name = Blueprints.displayName(id);
			if (name.getContents() instanceof TranslatableContents t && !Language.getInstance().has(t.getKey())) {
				unnamed.add(id.getPath() + " (\"" + name.getString() + "\")");
			}
		}
		helper.assertTrue(unnamed.isEmpty(), unnamed.size() + " blueprints show the fallback name: " + unnamed);
		// The three walls of the report, by the names every other upgrade has
		helper.assertTrue(Blueprints.displayName(AliveWorkplace.id("stone_wall_2")).getString().equals("Stone Wall II"),
			"stone_wall_2 reads " + Blueprints.displayName(AliveWorkplace.id("stone_wall_2")).getString());
		helper.assertTrue(Blueprints.displayName(AliveWorkplace.id("wall_tower_2")).getString().equals("Wall Tower II"),
			"wall_tower_2 reads " + Blueprints.displayName(AliveWorkplace.id("wall_tower_2")).getString());
		helper.assertTrue(Blueprints.displayName(AliveWorkplace.id("palisade_2")).getString().equals("Palisade II"),
			"palisade_2 reads " + Blueprints.displayName(AliveWorkplace.id("palisade_2")).getString());
		helper.succeed();
	}
}
