package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA lane c, 2026-11-02, B809 from its spec: the postman's status (sneak-right-click with an empty hand) reads "1 parcel
 * delivered" and "1 courier route" for one, the plural for 0 and 2+, each count picking its own form. The fix's tests
 * cover one and two parcels with no routes; these cover the route count and its mixes (1 and 1, 0 and 2, 2 and 1), each
 * message read whole, word for word, with its route lines.
 */
public class QaCountWords1102cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String END = "Give them a blank Delivery Note to end all routes.";
	private static final String EVERYTHING = "Carries everything but tools, weapons and armor";

	/** The whole status message the player sees for a postman with {@code delivered} parcels and {@code routes} routes. */
	private static String status(GameTestHelper helper, int delivered, int routes, List<String> routeLines, String[] name) {
		Leftovers.clear(helper);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		postman.setNoAi(true);
		postman.setVillagerData(postman.getVillagerData().setProfession(ModVillagers.POSTMAN));
		ModAttachments.MAIL_DELIVERED.set(postman, delivered);
		List<RouteData> list = new ArrayList<>();
		for (int i = 0; i < routes; i++) {
			BlockPos from = helper.absolutePos(new BlockPos(1, 2, 1 + i));
			BlockPos to = helper.absolutePos(new BlockPos(5, 2, 1 + i));
			list.add(new RouteData(Optional.of(from), Optional.of(to), List.of()));
			routeLines.add(from.getX() + " " + from.getY() + " " + from.getZ() + " → " + to.getX() + " " + to.getY() + " " + to.getZ() + ": " + EVERYTHING);
		}
		if (!list.isEmpty()) {
			ModAttachments.COURIER_ROUTES.set(postman, List.copyOf(list));
		}
		name[0] = postman.getDisplayName().getString();
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, "PostC" + delivered + routes);
		client.seen().clear();
		client.player().setShiftKeyDown(true);
		StationsSpecGameTests.rightClick(client.player(), postman, ItemStack.EMPTY, true);
		return client.seen().stream().map(Component::getString).filter(s -> s.contains("delivered")).findFirst().orElse("(no status)");
	}

	private static String expected(String name, String header, List<String> routeLines) {
		StringBuilder b = new StringBuilder(name + " — " + header);
		for (String line : routeLines) {
			b.append("\n  ").append(line);
		}
		if (!routeLines.isEmpty()) {
			b.append("\n  ").append(END);
		}
		return b.toString();
	}

	private static void check(GameTestHelper helper, int delivered, int routes, String header) {
		List<String> lines = new ArrayList<>();
		String[] name = new String[1];
		String seen = status(helper, delivered, routes, lines, name);
		String want = expected(name[0], header, lines);
		helper.assertTrue(seen.equals(want), "the postman's status for " + delivered + " parcels and " + routes + " routes reads '" + seen + "', expected '" + want + "'");
		helper.succeed();
	}

	/** One parcel and one route: both in the singular. */
	//$ gametest_ticks_batch AREA '40' '"qaWords1102cOneOne"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWords1102cOneOne")
	public void qaOneParcelAndOneRouteBothReadInTheSingular(GameTestHelper helper) {
		check(helper, 1, 1, "1 parcel delivered · 1 courier route");
	}

	/** No parcels and two routes: both in the plural. */
	//$ gametest_ticks_batch AREA '40' '"qaWords1102cZeroTwo"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWords1102cZeroTwo")
	public void qaNoParcelsAndTwoRoutesReadInThePlural(GameTestHelper helper) {
		check(helper, 0, 2, "0 parcels delivered · 2 courier routes");
	}

	/** Two parcels and one route: each count picks its own form. */
	//$ gametest_ticks_batch AREA '40' '"qaWords1102cTwoOne"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWords1102cTwoOne")
	public void qaTwoParcelsAndOneRouteEachPickTheirOwnForm(GameTestHelper helper) {
		check(helper, 2, 1, "2 parcels delivered · 1 courier route");
	}

	/** No parcels and no routes: "0 parcels delivered · 0 courier routes", and no route lines or how-to-end line. */
	//$ gametest_ticks_batch AREA '40' '"qaWords1102cZeroZero"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWords1102cZeroZero")
	public void qaNoParcelsAndNoRoutesReadInThePlural(GameTestHelper helper) {
		check(helper, 0, 0, "0 parcels delivered · 0 courier routes");
	}
}
