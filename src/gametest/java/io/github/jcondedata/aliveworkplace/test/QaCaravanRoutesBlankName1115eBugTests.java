package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA bug test (lane E, 2026-11-15; fails on main; B1229/B1244's kin, old saves): a neighbouring village whose saved
 * caravan-list name is blank (only spaces, or empty: what a hall's round wrote before B1229 for a hall named blank by a
 * name tag) is listed on a Village Hall's Routes page as a button with no name. B1229 made {@code nameIfLoaded} and B1244
 * the waystone network fall back to the made-up name; the Routes page takes {@code Caravans.Village.name()} as saved.
 * Found from a flake: QaCaravanNames1114dGameTests#theRoutesPageAndCharterListTheBrokenVillageByItsMadeUpName failed 2
 * of 10 in the nightly repeats when another test's whitespace-named village was in range ("... Placeholder,  \t ,
 * Placeholder"). Expected (B1229: "a blank saved name counts as missing and falls back to the made-up name"): each
 * village on the page by a name a player can read, the blank ones by their made-up names.
 */
public class QaCaravanRoutesBlankName1115eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** The names on {@code home}'s Routes page, row by row, as a player hovers them. */
	static List<String> routeNames(ServerPlayer player, ServerLevel level, BlockPos home) {
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.routesList(m, level, home, () -> { }));
		List<String> out = new ArrayList<>();
		for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
			if (!menu.icon(s).isEmpty()) {
				out.add(menu.icon(s).getHoverName().getString());
			}
		}
		return out;
	}

	/**
	 * Two far neighbours saved with blank names (three spaces; empty) and one named Goodmere, the list saved and read back
	 * as from an old world: the home hall's Routes page lists Goodmere, and the two blank ones by their made-up names.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1115eRoutesBlankName"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1115eRoutesBlankName")
	public void theRoutesPageNamesABlankNamedNeighbourByItsMadeUpName(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = QaFarVillageName1114eGameTests.far(helper, 9600);
		BlockPos spaces = QaFarVillageName1114eGameTests.far(helper, 10000);
		BlockPos empty = QaFarVillageName1114eGameTests.far(helper, 10400);
		BlockPos good = QaFarVillageName1114eGameTests.far(helper, 10800);
		QaFarVillageName1114eGameTests.setUp(helper, home);
		for (BlockPos h : List.of(spaces, empty, good)) {
			Leftovers.after(helper, () -> Caravans.Data.get(level).remove(h));
		}
		Caravans.Data live = Caravans.Data.get(level);
		live.setWants(home, Component.literal("Homestead"), List.of());
		live.setWants(spaces, Component.literal("   "), List.of());
		live.setWants(empty, Component.literal(""), List.of());
		live.setWants(good, Component.literal("Goodmere"), List.of());
		QaFarVillageName1114eGameTests.reload(helper, tag -> { });
		ServerPlayer player = QaFarVillageName1114eGameTests.player(helper, new ArrayList<>(), "qa1115e-routes");
		List<String> names = routeNames(player, level, home);
		helper.assertTrue(names.contains("Goodmere"), "setup: Goodmere isn't on the Routes page: " + names);
		helper.assertTrue(names.stream().noneMatch(String::isBlank), "the Routes page lists a village with no name: " + names);
		for (BlockPos h : List.of(spaces, empty)) {
			String made = VillageHalls.madeUpName(h).getString();
			helper.assertTrue(names.contains(made), "the blank-named village isn't listed by its made-up name " + made + ": " + names);
		}
		helper.succeed();
	}
}
