package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** B281: the Terrace's homes are furnished alike in kind: every one gets the installed furniture mods' pieces. */
public class TerraceFurnitureCompatTests implements FabricGameTest {
	/**
	 * B281 (the owner, 2026-10-21: one Terrace home had a Handcrafted table and chair downstairs, the other spruce stand-ins):
	 * with Handcrafted and Beautify (and with CobbleFurnies first), every furniture spot of the Terrace and Terrace II is a
	 * mod's piece, none left vanilla, and each home's kitchen has a modded table and chair.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyTerraceHomeGetsTheModsFurniture(GameTestHelper helper) {
		Map<String, Integer> homes = Map.of("terrace", 2, "terrace_2", 3);
		for (List<String> order : List.of(List.of("handcrafted", "beautify"), List.of("cobblefurnies", "handcrafted", "beautify"))) {
			homes.forEach((name, count) -> {
				List<FurnitureSpot> spots = BlueprintFurniture.of(AliveWorkplace.id(name));
				FurnitureChoice choice = FurnitureChoice.choose(spots, order, true);
				for (int i = 0; i < spots.size(); i++) {
					helper.assertTrue(choice.spots().containsKey(i), name + " with " + order + ": its " + spots.get(i).kind().id() + " at "
						+ spots.get(i).pos().toShortString() + " stays vanilla");
				}
				for (int home = 0; home < count; home++) {
					int x0 = 1 + 5 * home;
					for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.CHAIR)) {
						long modded = choice.spots().values().stream().filter(p -> p.kind() == kind && p.pos().getY() == 1
							&& p.pos().getX() > x0 && p.pos().getX() < x0 + 5 && !p.mod().equals("minecraft")).count();
						helper.assertTrue(modded == 1, name + " home " + (home + 1) + " with " + order + " has " + modded + " modded " + kind.id() + "s");
					}
				}
			});
		}
		helper.succeed();
	}
}
