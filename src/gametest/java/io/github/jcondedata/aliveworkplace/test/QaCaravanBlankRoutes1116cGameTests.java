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
 * QA (lane-c-1116-1852) of B1298 from its spec (docs/wiki/village-hall.md: "A blank name an old save still has on the
 * list is read the same way on another hall's Routes page: the neighbor's button, and the lines its click shows, use its
 * made-up name"): a name of tabs and line breaks is blank too, a real name with spaces around it still wins, and the
 * lines a click on a blank-named neighbor shows (route started, stopped, sister village) name it by its made-up name,
 * the same after the list is saved and read back again. QaCaravanRoutesBlankName1115eBugTests covers spaces and empty.
 */
public class QaCaravanBlankRoutes1116cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	static ChoiceMenu routes(ServerPlayer player, ServerLevel level, BlockPos home) {
		return ChoiceMenu.detached(player, m -> VillageHallScreen.routesList(m, level, home, () -> { }));
	}

	/** The Routes page's slot whose button reads {@code name}, or -1. */
	static int slotOf(ChoiceMenu menu, String name) {
		for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
			if (!menu.icon(s).isEmpty() && menu.icon(s).getHoverName().getString().equals(name)) {
				return s;
			}
		}
		return -1;
	}

	static List<String> names(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
			if (!menu.icon(s).isEmpty()) {
				out.add(menu.icon(s).getHoverName().getString());
			}
		}
		return out;
	}

	/** The last line {@code seen} got, as text (the action bar's line after a click). */
	static String last(List<Component> seen) {
		return seen.isEmpty() ? "(nothing)" : seen.get(seen.size() - 1).getString();
	}

	/**
	 * A far neighbor saved with a name of a tab, a line break and a space, and one saved as " Riverend " (a real name,
	 * spaces and all), the list saved and read back: the blank one is on the Routes page by its made-up name and Riverend
	 * by its own; a click on the blank one says "Caravans will go to <made-up name> once a day", a second "No more
	 * caravans to <made-up name>"; read back again, it is still by its made-up name and its route kept; as a sister village
	 * its click says "<made-up name> is a sister village...".
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1116cRoutesBlankClick"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1116cRoutesBlankClick")
	public void aBlankNamedNeighborsClickLinesUseItsMadeUpName(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = QaFarVillageName1114eGameTests.far(helper, 14400);
		BlockPos blank = QaFarVillageName1114eGameTests.far(helper, 14800);
		BlockPos spaced = QaFarVillageName1114eGameTests.far(helper, 15200);
		QaFarVillageName1114eGameTests.setUp(helper, home);
		for (BlockPos h : List.of(blank, spaced)) {
			Leftovers.after(helper, () -> Caravans.Data.get(level).remove(h));
		}
		Caravans.Data live = Caravans.Data.get(level);
		live.setWants(home, Component.literal("Homestead"), List.of());
		live.setWants(blank, Component.literal("\t\n "), List.of());
		live.setWants(spaced, Component.literal(" Riverend "), List.of());
		QaFarVillageName1114eGameTests.reload(helper, tag -> { });
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaFarVillageName1114eGameTests.player(helper, seen, "qa1116c-routes");
		String made = VillageHalls.madeUpName(blank).getString();
		helper.assertTrue(!made.isBlank(), "setup: no made-up name");
		ChoiceMenu menu = routes(player, level, home);
		List<String> names = names(menu);
		helper.assertTrue(names.stream().noneMatch(String::isBlank), "a village with no name on the Routes page: " + names);
		helper.assertTrue(names.contains(made), "the tab-and-newline-named village isn't listed as " + made + ": " + names);
		helper.assertTrue(names.contains(" Riverend "), "the real name with spaces around it didn't win: " + names);
		helper.assertTrue(!names.contains(VillageHalls.madeUpName(spaced).getString()), "Riverend listed by its made-up name: " + names);
		// A click starts the route, a second stops it: both lines name it.
		seen.clear();
		menu.press(slotOf(menu, made), player);
		helper.assertValueEqual(last(seen), "Caravans will go to " + made + " once a day", "the line after the first click");
		helper.assertTrue(Caravans.Data.get(level).routesFrom(home).contains(blank), "the route didn't start");
		// Read back again: still by its made-up name, the route kept.
		// (by hand: the helper's own put-back would run twice; the first reload's put-back restores the live list at the end)
		net.minecraft.nbt.CompoundTag tag = Caravans.Data.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
		level.getDataStorage().set(QaFarVillageName1114eGameTests.CARAVANS, Caravans.Data.load(tag, level.registryAccess()));
		helper.assertTrue(Caravans.Data.get(level).routesFrom(home).contains(blank), "the route was lost on reload");
		menu = routes(player, level, home);
		helper.assertTrue(slotOf(menu, made) >= 0, "after a reload, not listed as " + made + ": " + names(menu));
		seen.clear();
		menu.press(slotOf(menu, made), player);
		helper.assertValueEqual(last(seen), "No more caravans to " + made, "the line after the second click");
		helper.assertTrue(!Caravans.Data.get(level).routesFrom(home).contains(blank), "the route didn't stop");
		// As a sister village.
		Caravans.Data.get(level).addSisters(home, blank);
		menu = routes(player, level, home);
		seen.clear();
		menu.press(slotOf(menu, made), player);
		helper.assertValueEqual(last(seen), made + " is a sister village: its route stays open and doesn't count toward the limit", "the sister line");
		helper.succeed();
	}
}
