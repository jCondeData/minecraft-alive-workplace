package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA 2026-11-07 (lane c) of B999 and B1000 from their spec: hired help leaves the hall's villager count, and so its rank,
 * as they were, while the People table still lists them ({@code everyone()} keeps them). The cases the earlier tests
 * (QaHiredRank1107dBugTests, QaHiredHelpCounts1107cBugTests, QaHiredCounts1107aGameTests) leave out: a rank already earned
 * by the village's own people is neither raised, lowered nor announced again when a band comes and goes; the threshold
 * exactly (10 of its own rank up, 9 and a band don't, and a band doesn't hold a rank the village lost); and the People
 * table opened by a player. The rank is counted by the hall's own round (its block entity's tick).
 */
public class QaHiredRank1107cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** {@code n} finished buildings (park benches) in the village, forgotten after the test. */
	private static void buildings(GameTestHelper helper, int n) {
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BlueprintData.Placement> placed = new ArrayList<>();
		Leftovers.after(helper, () -> placed.forEach(sites::forgetFinished));
		for (int i = 0; i < n; i++) {
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(),
				helper.absolutePos(new BlockPos(1 + i * 2, 2, 16)), Rotation.NONE, Mirror.NONE);
			placed.add(placement);
			sites.recordFinished(StarterBlueprints.PARK_BENCH.id(), placement, UUID.randomUUID());
		}
	}

	/** {@code n} more still villagers of the village's own (the village already has 2 in beds). */
	private static List<Villager> own(GameTestHelper helper, int n, int row) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1 + i, 2, row));
			v.setNoAi(true);
			out.add(v);
		}
		return out;
	}

	/** The hall's round, as its block entity's tick runs it. */
	private static VillageRanks.Rank round(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setNeeds(null);
		VillageHallBlockEntity.serverTick(level, hall, level.getBlockState(hall), entity);
		return entity.rank();
	}

	private static long rankEntries(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RANK).count();
	}

	private static List<Villager> hire(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		Mercenaries.hire(level, hall, player);
		List<Villager> band = Mercenaries.near(level, hall);
		helper.assertTrue(band.size() == Mercenaries.BAND, "setup: no band hired (" + band.size() + ")");
		band.forEach(v -> v.setNoAi(true));
		return band;
	}

	private static void leave(List<Villager> band) {
		for (Villager v : band) {
			if (v.getVehicle() != null) {
				v.getVehicle().discard();
			}
			v.discard();
		}
	}

	/** The hall's name button line "To be a Village: 10 villagers (N now), ...". */
	private static String nextRankLine(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		List<String> lines = QaHiredHelpCounts1107cGameTests.lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.NAME));
		return lines.stream().filter(l -> l.startsWith("To be a")).findFirst()
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no next-rank line on the hall's name: " + lines));
	}

	/**
	 * A Village earned by its own 10 villagers and 5 buildings hires a band of three and sees it leave: the hall's rounds
	 * keep it a Village throughout, and the rank-up is in the Chronicle once (no second 'is now a Village!').
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHiredRank1107cKept"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredRank1107cKept")
	public void aVillageKeepsItsRankWhileABandComesAndGoes(GameTestHelper helper) {
		BlockPos hall = QaHiredHelp1107dGameTests.village(helper);
		buildings(helper, 5);
		own(helper, 8, 14);
		ServerPlayer player = QaHiredHelp1107dGameTests.payer(helper, hall);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(round(helper, hall) == VillageRanks.Rank.VILLAGE, "setup: 10 villagers and 5 buildings aren't a Village");
			helper.assertTrue(rankEntries(helper, hall) == 1, "setup: the rank-up is in the Chronicle " + rankEntries(helper, hall) + " times");
			List<Villager> band = hire(helper, player, hall);
			VillageRanks.Rank with = round(helper, hall);
			helper.assertTrue(with == VillageRanks.Rank.VILLAGE, "a Village of 10 with a band of " + Mercenaries.BAND + " hired is a " + with);
			leave(band);
			VillageRanks.Rank after = round(helper, hall);
			helper.assertTrue(after == VillageRanks.Rank.VILLAGE, "a Village of 10 is a " + after + " once its hired band has left");
			helper.assertTrue(rankEntries(helper, hall) == 1, "the band's coming and going put the rank in the Chronicle "
				+ rankEntries(helper, hall) + " times (expected once, when the village earned it)");
			helper.succeed();
		});
	}

	/**
	 * The threshold: 9 of its own and a band of three (12 people) stay a Hamlet, and the next-rank line reads '9 now';
	 * a 10th of its own makes it a Village, once; one of its own gone again (9 and the band) and it is a Hamlet again:
	 * the band neither earns the rank nor holds it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHiredRank1107cEdge"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredRank1107cEdge")
	public void ownVillagersAloneCrossTheRankThreshold(GameTestHelper helper) {
		BlockPos hall = QaHiredHelp1107dGameTests.village(helper);
		buildings(helper, 5);
		own(helper, 7, 14);
		ServerPlayer player = QaHiredHelp1107dGameTests.payer(helper, hall);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(round(helper, hall) == VillageRanks.Rank.HAMLET, "setup: 9 villagers and 5 buildings already a Village");
			hire(helper, player, hall);
			VillageRanks.Rank short1 = round(helper, hall);
			helper.assertTrue(short1 == VillageRanks.Rank.HAMLET, "9 villagers of its own and a band of " + Mercenaries.BAND + " made a " + short1);
			String line = nextRankLine(helper, player, hall);
			helper.assertTrue(line.contains("10 villagers (9 now)"), "with the band hired the hall reads '" + line + "' (expected '10 villagers (9 now)')");
			Villager tenth = own(helper, 1, 15).get(0);
			VillageRanks.Rank ten = round(helper, hall);
			helper.assertTrue(ten == VillageRanks.Rank.VILLAGE, "10 villagers of its own and 5 buildings (and a band) made a " + ten);
			helper.assertTrue(rankEntries(helper, hall) == 1, "the rank-up is in the Chronicle " + rankEntries(helper, hall) + " times");
			tenth.discard();
			VillageRanks.Rank back = round(helper, hall);
			helper.assertTrue(back == VillageRanks.Rank.HAMLET, "with one of its own gone (9 and a band of " + Mercenaries.BAND + ") the village is still a " + back);
			helper.succeed();
		});
	}

	/**
	 * B1000's other half: the People button's title reads 'Villagers: 2' with a band of three hired, while the People
	 * table on the hall's page still lists all five, the band among them.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHiredRank1107cTable"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredRank1107cTable")
	public void thePeopleTableListsTheBandTheTitleCountsTheVillagesOwn(GameTestHelper helper) {
		BlockPos hall = QaHiredHelp1107dGameTests.village(helper);
		ServerPlayer player = QaHiredHelp1107dGameTests.payer(helper, hall);
		helper.runAfterDelay(3, () -> {
			List<Villager> band = hire(helper, player, hall);
			String title = VillageHallScreen.forTest(player, hall).icon(1).getHoverName().getString();
			String two = QaHiredHelpCounts1107cGameTests.text("screen.aliveworkplace.hall.villagers", 2);
			helper.assertTrue(title.equals(two), "with a band hired the People button reads '" + title + "', not '" + two + "'");
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall); // the hall's page as a player sees it, the People table on it
			Set<String> rows = new HashSet<>();
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
				CompoundTag row = HallPeople.row(menu.icon(slot));
				if (row != null) {
					rows.add(row.getString("uuid"));
				}
			}
			for (Villager v : band) {
				helper.assertTrue(rows.contains(v.getStringUUID()), "the People table doesn't list the hired " + v.getName().getString()
					+ " (" + rows.size() + " rows)");
			}
			helper.assertTrue(rows.size() == 2 + Mercenaries.BAND, "the People table lists " + rows.size() + " people, not the 2 and the band of " + Mercenaries.BAND);
			helper.succeed();
		});
	}
}
