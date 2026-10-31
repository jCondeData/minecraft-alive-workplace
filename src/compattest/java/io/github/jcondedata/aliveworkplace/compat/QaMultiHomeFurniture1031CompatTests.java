package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-b-1031-0637) for B281 from its spec: "go through every multi-home build (Terrace, Terrace II, townhouses,
 * inns)": with the pack's furniture mods installed, no table or chair spot of any tier of the Townhouse or the Inn is
 * left with a vanilla stand-in (TerraceFurnitureCompatTests covers the Terraces), in either order the pack may list the
 * mods in.
 */
public class QaMultiHomeFurniture1031CompatTests implements FabricGameTest {
	private static final List<String> BUILDS = List.of("townhouse", "townhouse_2", "townhouse_3", "townhouse_4", "inn", "inn_2", "inn_3");
	private static final Set<FurnitureSpot.Kind> SEATS_AND_TABLES = EnumSet.of(FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.CHAIR);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noTableOrChairOfATownhouseOrInnStaysVanilla(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (List<String> order : List.of(List.of("handcrafted", "beautify"), List.of("cobblefurnies", "handcrafted", "beautify"))) {
			for (String name : BUILDS) {
				List<FurnitureSpot> spots = BlueprintFurniture.of(AliveWorkplace.id(name));
				helper.assertTrue(spots.stream().anyMatch(s -> SEATS_AND_TABLES.contains(s.kind())), name + " has no table or chair spot");
				FurnitureChoice choice = FurnitureChoice.choose(spots, order, true);
				for (int i = 0; i < spots.size(); i++) {
					FurnitureSpot spot = spots.get(i);
					if (!SEATS_AND_TABLES.contains(spot.kind())) {
						continue;
					}
					checked++;
					var piece = choice.spots().get(i);
					if (piece == null || piece.mod().equals("minecraft")) {
						problems.add(name + " " + spot.kind().id() + "@" + spot.pos().toShortString() + " with " + order);
					}
				}
			}
		}
		helper.assertTrue(checked > 20, "only " + checked + " table and chair spots in the townhouses and inns");
		helper.assertTrue(problems.isEmpty(), problems.size() + " tables and chairs stay vanilla with the furniture mods there (B281), e.g. "
			+ problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}
}
