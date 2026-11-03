package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane-e-1103-0412, the player's sentences; kin of B406, B824, B833, B842): the Patrol Map handed to a guard says
 * "%s will walk your route of %s points by day". A route of one marked point (one click, nothing asks for two) reads
 * "route of 1 points". Expected "route of 1 point"; two points still read "2 points".
 */
public class QaPatrolOnePoint1103eBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static List<String> handOver(GameTestHelper helper, int points, String name) {
		Leftovers.clear(helper);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		ServerPlayer player = QaLostCaravanGameTests.listener(helper, seen, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, name);
		List<BlockPos> route = new ArrayList<>();
		for (int i = 0; i < points; i++) {
			route.add(helper.absolutePos(new BlockPos(2 + 2 * i, 2, 5)));
		}
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(helper.getLevel().dimension().location()), List.copyOf(route)));
		PatrolMapItem.giveTo(player, guard, map);
		return QaLostCaravanGameTests.texts(seen, true);
	}

	/** One marked point: the action bar reads "route of 1 point". */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaARouteOfOnePointReadsSingular(GameTestHelper helper) {
		List<String> bar = handOver(helper, 1, "QaPatrolOne");
		helper.assertTrue(bar.size() == 1, "the action bar said " + bar);
		helper.assertTrue(bar.get(0).contains("route of 1 point by day"), "a one-point route reads: " + bar.get(0));
		helper.succeed();
	}

	/** Control: two points read "route of 2 points". */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaARouteOfTwoPointsReadsPlural(GameTestHelper helper) {
		List<String> bar = handOver(helper, 2, "QaPatrolTwo");
		helper.assertTrue(bar.size() == 1, "the action bar said " + bar);
		helper.assertTrue(bar.get(0).contains("route of 2 points by day"), "a two-point route reads: " + bar.get(0));
		helper.succeed();
	}
}
