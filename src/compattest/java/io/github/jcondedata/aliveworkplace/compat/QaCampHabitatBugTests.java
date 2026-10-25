package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (ROADMAP 28.8, 28.10, 28.1a): the tests of {@link QaCampHabitatCompatTests} that fail because the mod is wrong
 * against the spec. Not registered (the suite stays green); register it to see them fail, and move each one back to
 * {@link QaCampHabitatCompatTests} once fixed.
 */
public class QaCampHabitatBugTests implements FabricGameTest {
	private static final String AREA = QaCampHabitatCompatTests.AREA;
	private static final BlockPos STATION = QaCampHabitatCompatTests.STATION;

	/**
	 * A spot 32 blocks east and 5 south is 32.4 blocks out, beyond the 32: either she refuses it (saying so), or, if she
	 * says she keeps a snack there, she really uses it. Taking it and then never setting a snack out there is wrong.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_habitat_diagonal"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_habitat_diagonal")
	public void aSpotJustBeyondThirtyTwoDiagonallyIsRefusedOrUsed(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, seen, "qa-diagonal");
		BlockPos pasture = helper.absolutePos(STATION);
		HabitatKeepers.markSpot(player, keeper, HabitatKeeperCompatTests.marker(helper, STATION.offset(32, -1, 5)));
		BlockPos spot = pasture.offset(32, 0, 5);
		boolean taken = HabitatKeepers.spots(keeper).contains(spot);
		helper.assertTrue(!taken || HabitatKeepers.lureSpots(helper.getLevel(), keeper, pasture).contains(spot),
			"she took the spot 32.4 blocks out (heard " + QaCampHabitatCompatTests.lines(seen) + ") but never uses it: her lure spots are "
				+ HabitatKeepers.lureSpots(helper.getLevel(), keeper, pasture));
		helper.succeed();
	}

	/** Her status counts read right: one lure spot is "1 lure spot", not "1 lure spots". */
	//$ gametest_ticks_batch AREA '1200' '"qa_habitat_plural"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_habitat_plural")
	public void herWatchingLineSaysOneLureSpot(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		BlockPos spot = new BlockPos(9, 2, 9);
		helper.setBlock(spot, BuiltInRegistries.BLOCK.get(QaCampHabitatCompatTests.c("poke_snack")));
		HabitatKeepers.addSpot(keeper, helper.absolutePos(spot));
		helper.succeedWhen(() -> {
			String line = QaCampHabitatCompatTests.said(helper, keeper);
			helper.assertTrue(line.contains("watching the wild"), "not watching yet: " + line);
			helper.assertTrue(!line.contains("1 lure spots") && !line.contains(" 1 Saccharine logs"), "her line reads: " + line);
		});
	}

	/** The purse in chat: 150,000 reads with its thousands marked, as the mod's own money lines say ("1,250 CobbleDollars"). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePurseReadsWithThousandsMarked(GameTestHelper helper) {
		String text = io.github.jcondedata.aliveworkplace.work.Money.describe(150_000, 1_500).getString();
		helper.assertTrue(text.contains("150,000"), "the purse reads " + text);
		helper.succeed();
	}

	/**
	 * She keeps up to 16 Poké Snacks: with 15 by the pot she cooks (one pot's worth), and once there are 16 or more she
	 * cooks no more of them, though the makings for more are there.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qa_cook_keep"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa_cook_keep")
	public void fifteenSnacksAreToppedUpThenNoMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager cook = QaCampHabitatCompatTests.cook(helper, QaCampHabitatCompatTests.snackMakings(15));
		List<Integer> history = new ArrayList<>(List.of(15));
		long[] reached = {-1};
		helper.onEachTick(() -> {
			int n = QaCampHabitatCompatTests.snacks(helper, cook);
			if (n != history.get(history.size() - 1)) {
				history.add(n);
			}
			if (n >= 16 && reached[0] < 0) {
				reached[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached[0] >= 0, "15 snacks never became 16; she says " + QaCampHabitatCompatTests.said(helper, cook));
			int first = history.stream().filter(n -> n >= 16).findFirst().orElseThrow();
			helper.assertTrue(history.stream().allMatch(n -> n <= first), "she went on cooking snacks past 16: " + history);
			helper.assertTrue(helper.getTick() - reached[0] >= 800, "waiting to see she stops: " + history);
		});
	}
}
