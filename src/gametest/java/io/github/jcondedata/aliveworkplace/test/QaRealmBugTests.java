package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * Failing QA tests (lane-c-1024-0353) for bugs in the settlers' setting out (33.9). Not registered; the coordinator
 * ships them on tests/lane-c-qa-1024 and the builder commits each with its fix.
 */
public class QaRealmBugTests implements FabricGameTest {
	private static final String AREA = QaRealmGameTests.AREA;

	/**
	 * 33.9: "Right-click the hall with the charter, its spot chosen". With the charter in the OFF hand and the main hand
	 * empty, the owner's right-click opens the hall's page and sends nobody (as B338's insignia did); the off hand
	 * should send the settlers as the main hand does.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmBugOffHand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmBugOffHand")
	public void theCharterInTheOffHandSendsTheSettlers(GameTestHelper helper) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		QaRealmGameTests.plainVillage(helper);
		QaRealmGameTests.storehouse(helper, QaRealmGameTests.allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		v.owner().setItemInHand(InteractionHand.OFF_HAND, QaRealmGameTests.charter(v));
		QaStageFourGameTests.press(helper, v.owner(), QaRealmGameTests.HALL);
		helper.assertTrue(v.order() != null, "the off hand's charter sent nobody (saw " + QaStageFourGameTests.keys(v.seen()) + ", menu "
			+ v.owner().containerMenu.getClass().getSimpleName() + ")");
		helper.assertTrue(v.owner().getOffhandItem().isEmpty(), "the charter stayed in the off hand");
		helper.succeed();
	}

	/**
	 * The same with the charter in the main hand on a sneak-click: vanilla skips the hall's own use for a sneaking
	 * player holding anything (B336), the charter's ground click passes on the hall, and its air click opens the map,
	 * so nobody is sent. Expected: the sneak-click on the hall sends the settlers too.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmBugSneak"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmBugSneak")
	public void aSneakClickOnTheHallWithTheCharterSendsTheSettlers(GameTestHelper helper) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		QaRealmGameTests.plainVillage(helper);
		QaRealmGameTests.storehouse(helper, QaRealmGameTests.allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, QaRealmGameTests.charter(v));
		v.owner().setShiftKeyDown(true);
		QaStageFourGameTests.press(helper, v.owner(), QaRealmGameTests.HALL);
		v.owner().setShiftKeyDown(false);
		helper.assertTrue(v.order() != null, "a sneak-click on the hall sent nobody (saw " + QaStageFourGameTests.keys(v.seen()) + ")");
		helper.assertTrue(!v.owner().getMainHandItem().is(ModItems.COLONY_CHARTER), "the charter stayed in hand");
		helper.succeed();
	}

	/**
	 * The Colonies tab's lines with one settler on the road and with a day left to wait: "1 settlers" and "in 1 days".
	 * Expected: "1 settler" and "in 1 day" (or "tomorrow").
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmBugPlural"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmBugPlural")
	public void theTabSaysOneSettlerAndOneDay(GameTestHelper helper) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		long now = v.level().getGameTime();
		CompoundTag bram = QaRealmGameTests.saved(helper, "Bram", io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER, 1);
		RealmData.Order road = new RealmData.Order(v.mother(), v.hall().offset(400, 0, 0), Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD,
			now - 10, now + 2400, Colonies.COST * 100, now - 100, List.of(), Optional.of(bram.getUUID("UUID")), Map.of(), List.of(bram));
		List<String> lines = Settlers.lines(v.level(), road).stream().map(Component::getString).toList();
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("1 settlers")), "one settler on the road: " + lines);

		Villager tomas = QaRealmGameTests.villager(helper, new net.minecraft.core.BlockPos(10, 2, 11), "Tomas",
			net.minecraft.world.entity.npc.VillagerProfession.NONE, 1);
		// ordered 2 days and a bit ago: a day of the 3 left to wait
		RealmData.Order waiting = new RealmData.Order(v.mother(), v.hall().offset(400, 0, 0), Optional.of(Component.literal("Newbrook")), RealmData.GATHERING,
			0, 0, Colonies.COST * 100, now - 50_000, List.of(tomas.getUUID()), Optional.of(tomas.getUUID()), Map.of(), List.of());
		List<String> wait = Settlers.lines(v.level(), waiting).stream().map(Component::getString).toList();
		helper.assertTrue(wait.stream().noneMatch(l -> l.contains("in 1 days")), "a day left: " + wait);
		helper.succeed();
	}
}
