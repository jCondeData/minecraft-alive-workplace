package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA (lane c, 2026-10-25) bug tests for ROADMAP 34.20, failing on the mod as it is. 34.20: "with nobody [to take
 * over], the elder keeps working and the hall says 'Bram would like to retire: the village needs a jobless villager to
 * take over'". Once that line is up, it stays even when a jobless villager is there and the elder waits for another
 * reason, so the hall tells the player to find a jobless villager the village already has.
 */
public class QaElderLifeBugTests implements FabricGameTest {
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String WAITING = "Bram would like to retire: the village needs a jobless villager to take over";

	/** Bram waits for a successor; Ida (jobless) comes, but Bram has a building site now: the hall still says the village needs a jobless villager. */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '100' '"qaElderWaitingHolds"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 100, batch = "qaElderWaitingHolds")
	public void qaTheWaitingLineStaysWhileAJoblessVillagerIsThereAndTheElderHoldsASite(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaElderLifeGameTests.village(helper);
		Villager bram = QaElderLifeGameTests.villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.MASON, 3);
		Villager ida = QaElderLifeGameTests.villager(helper, new BlockPos(7, 2, 6), "Ida", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			QaElderLifeGameTests.ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1250);
			LifeStages.setAdultSince(ida, today - 20);
			Retirement.round(level, hallPos, List.of(bram));
			helper.assertTrue(QaElderLifeGameTests.lore(level, hallPos, bram).contains(WAITING), "the line with nobody: " + QaElderLifeGameTests.lore(level, hallPos, bram));
			ModAttachments.BUILDER_JOB.set(bram, new BuilderJob(UUID.nameUUIDFromBytes("qa-site".getBytes())));
			Retirement.round(level, hallPos, List.of(bram, ida));
			helper.assertTrue(Retirement.shadow(ida) == null, "retiring while holding a site");
			helper.assertTrue(!QaElderLifeGameTests.lore(level, hallPos, bram).contains(WAITING),
				"Ida is jobless and here, yet the hall says: " + QaElderLifeGameTests.lore(level, hallPos, bram));
			helper.succeed();
		});
	}

	/** Bram waits for a successor; Odo retired yesterday (one a village every 3 days) and Ida (jobless) comes: the hall still says nobody can take over. */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '100' '"qaElderWaitingCooldown"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 100, batch = "qaElderWaitingCooldown")
	public void qaTheWaitingLineStaysWhileAJoblessVillagerWaitsOutTheThreeDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaElderLifeGameTests.village(helper);
		Villager bram = QaElderLifeGameTests.villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.MASON, 3);
		Villager ida = QaElderLifeGameTests.villager(helper, new BlockPos(7, 2, 6), "Ida", VillagerProfession.NONE, 1);
		Villager odo = QaElderLifeGameTests.villager(helper, new BlockPos(9, 2, 6), "Odo", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			QaElderLifeGameTests.ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1250);
			LifeStages.setAdultSince(odo, today - 1300);
			LifeStages.setAdultSince(ida, today - 20);
			Retirement.round(level, hallPos, List.of(bram));
			helper.assertTrue(QaElderLifeGameTests.lore(level, hallPos, bram).contains(WAITING), "the line with nobody");
			ModAttachments.RETIRED.set(odo, new Retirement.Retired(today - 1, "minecraft:farmer", 3));
			Retirement.round(level, hallPos, List.of(bram, ida, odo));
			helper.assertTrue(Retirement.shadow(ida) == null, "a second retirement inside three days");
			helper.assertTrue(!QaElderLifeGameTests.lore(level, hallPos, bram).contains(WAITING),
				"Ida is jobless and here, yet the hall says: " + QaElderLifeGameTests.lore(level, hallPos, bram));
			helper.succeed();
		});
	}
}
