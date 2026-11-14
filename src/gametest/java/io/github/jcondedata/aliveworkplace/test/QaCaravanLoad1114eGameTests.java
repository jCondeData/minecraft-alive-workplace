package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-14, B1233) from the spec ("Don't break existing saves"; B1217: a missing name falls back to the
 * made-up name): an old or hand-edited caravan list whose village entry has no "name" key, or a name that isn't text
 * JSON, keeps that village's other saved data through a load: its place on the list, what it wants, when it last sent
 * a caravan, its trade routes and its banner color. Only the name is lost (the made-up one stands in). Before the fix
 * (B1233) Caravans.load dropped the whole entry ("Skipping a saved caravan village").
 */
public class QaCaravanLoad1114eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch AREA '40' '"qa1114eCaravanLoadNoName"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114eCaravanLoadNoName")
	public void aSavedVillageWithoutAUsableNameKeepsItsRoutesAndData(GameTestHelper helper) {
		BlockPos noName = QaFarVillageName1114eGameTests.far(helper, 6000);
		BlockPos broken = QaFarVillageName1114eGameTests.far(helper, 6400);
		BlockPos other = QaFarVillageName1114eGameTests.far(helper, 6800);
		QaFarVillageName1114eGameTests.setUp(helper, noName);
		ServerLevel level = helper.getLevel();
		for (BlockPos h : List.of(broken, other)) {
			Leftovers.after(helper, () -> Caravans.Data.get(level).remove(h));
		}
		Caravans.Data live = Caravans.Data.get(level);
		live.setWants(other, Component.literal("Ferncombe"), List.of());
		for (BlockPos h : List.of(noName, broken)) {
			live.setWants(h, Component.literal("Willowmere Hollow"), List.of(new Caravans.Want(Items.BREAD, 7)));
			live.sent(h, 12);
			live.setColour(h, DyeColor.LIME);
			helper.assertTrue(live.toggleRoute(h, other), "setup: the trade route from " + h + " didn't open");
		}
		Caravans.Data read = QaFarVillageName1114eGameTests.reload(helper, tag -> {
			QaFarVillageName1114eGameTests.entry(tag, noName).remove("name"); // an old or hand-edited save
			QaFarVillageName1114eGameTests.entry(tag, broken).putString("name", "{\"text\":\"Willowmere"); // cut off mid-write
		});
		for (BlockPos h : List.of(noName, broken)) {
			String which = h.equals(noName) ? "no name" : "a broken name";
			Caravans.Village v = read.village(h);
			helper.assertTrue(v != null, "the saved village with " + which + " was dropped from the caravan list on load (its routes and data with it)");
			helper.assertTrue(v.wants().size() == 1 && v.wants().get(0).item() == Items.BREAD && v.wants().get(0).count() == 7, "its wants after the load (" + which + "): " + v.wants());
			helper.assertTrue(v.lastCaravanDay() == 12, "its last caravan day after the load (" + which + "): " + v.lastCaravanDay());
			helper.assertTrue(read.routesFrom(h).contains(other), "its trade route after the load (" + which + "): " + read.routesFrom(h));
			helper.assertTrue(read.colour(h) == DyeColor.LIME, "its banner color after the load (" + which + "): " + read.colour(h));
			Component name = VillageHalls.nameIfLoaded(level, h);
			helper.assertTrue(name.getString().equals(VillageHalls.madeUpName(h).getString()), "its name after the load (" + which + "): '" + name.getString() + "'");
		}
		helper.succeed();
	}
}
