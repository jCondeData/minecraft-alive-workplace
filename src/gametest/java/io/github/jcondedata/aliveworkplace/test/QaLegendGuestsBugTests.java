package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.*;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA for 29.8: B584 (a guest takes no job), B585 (a killed guest frees the village), B586 (they settle in the host
 * village) and B587 (one Legendary never settles twice). Helpers are {@link QaLegendGuestsGameTests}'.
 */
public class QaLegendGuestsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = QaLegendGuestsGameTests.AREA;

	/** A guest takes no job: a stonecutter beside them stays free for 200 ticks of their own AI, and is theirs when they settle as a Master. */
	//$ gametest_ticks_batch AREA '400' '"qaLegendGuestNoJob"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLegendGuestNoJob")
	public void qaGuestTakesNoJobUntilTheySettle(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend plain = rare("qa_nojob_mason", "", "");
		BlockPos stationRel = new BlockPos(6, 2, 3);
		helper.setBlock(stationRel, Blocks.STONECUTTER);
		Villager[] guest = new Villager[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 1, 1000);
			guest[0] = LegendGuests.come(level, hall, plain, "hall", helper.absolutePos(new BlockPos(5, 2, 3)), RandomSource.create(30L));
			helper.assertTrue(guest[0] != null, "the guest didn't come");
		}));
		StringBuilder seen = new StringBuilder();
		for (int t = 3; t < 220; t += 10) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				BlockPos station = helper.absolutePos(stationRel);
				boolean free = helper.getLevel().getPoiManager().getInRange(h -> true, station, 0, PoiManager.Occupancy.HAS_SPACE).anyMatch(r -> r.getPos().equals(station));
				seen.append(tick).append(free ? " free" : " taken").append(guest[0] == null ? "" : " pot=" + guest[0].getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent()).append("; ");
			});
		}
		helper.runAfterDelay(220, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos station = helper.absolutePos(stationRel);
			Villager g = guest[0];
			helper.assertTrue(g.isAlive() && LegendGuests.isGuest(g), "no longer a guest after 200 ticks");
			helper.assertTrue(g.getVillagerData().getProfession() == VillagerProfession.NITWIT, "a guest took a job: " + g.getVillagerData());
			helper.assertTrue(g.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty() && g.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(),
				"a guest claimed a workstation: " + g.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(level.getPoiManager().getInRange(h -> true, station, 0, PoiManager.Occupancy.HAS_SPACE).anyMatch(r -> r.getPos().equals(station)),
				"the stonecutter isn't free while a guest stands by it (" + seen + "), claimed by "
				+ level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48), v -> v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(station::equals).isPresent()
					|| v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(GlobalPos::pos).filter(station::equals).isPresent()));
			at(level, 1, 3000);
			wellbeing(helper, HALL, 0.8f);
			home(helper, "qa_nojob_manor_3", new BlockPos(10, 2, 10), new BlockPos(12, 2, 12), recorded);
			LegendGuests.tend(level, hall);
			LegendData data = ModAttachments.LEGEND.get(g);
			helper.assertTrue(data != null && data.settled(), "didn't settle with every need met: " + data);
			helper.assertTrue(g.getVillagerData().getProfession() == VillagerProfession.MASON
				&& g.getVillagerData().getLevel() == VillagerData.MAX_VILLAGER_LEVEL, "not a Master Mason: " + g.getVillagerData());
			BlockPos site = g.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
			helper.assertTrue(station.equals(site), "didn't take the free stonecutter on settling: " + site);
			helper.succeed();
		}));
	}

	/**
	 * They settle in the village that hosted them: a guest of this hall standing nearer the next village's hall settles
	 * here, so this village's Rare slot is the one taken and the next village's stays free.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLegendGuestHostHall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendGuestHostHall")
	public void qaGuestSettlesInTheHostVillage(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.setBlock(OTHER_HALL, ModBlocks.VILLAGE_HALL);
		Legend plain = rare("qa_host_mason", "", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(OTHER_HALL);
			wellbeing(helper, HALL, 0.8f);
			at(level, 1, 1000);
			// they came to this hall's market, whose square is nearer the next village's hall
			Villager guest = LegendGuests.come(level, hall, plain, "market", helper.absolutePos(new BlockPos(16, 2, 15)), RandomSource.create(31L));
			helper.assertTrue(guest != null && guest.blockPosition().distSqr(other) < guest.blockPosition().distSqr(hall), "the guest stands nearer this hall: " + guest);
			home(helper, "qa_host_manor_3", new BlockPos(6, 2, 6), new BlockPos(8, 2, 8), recorded);
			LegendGuests.tend(level, hall);
			LegendData data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data != null && data.settled() && data.hall().equals(Optional.of(hall)), "not settled here: " + data);
			Optional<BlockPos> recordHall = LegendRecord.get(level).entry(guest.getUUID()).flatMap(LegendRecord.Entry::hall);
			helper.assertTrue(recordHall.equals(Optional.of(hall)), "the record puts them in the village round " + recordHall + ", not " + hall);
			helper.assertTrue(LegendSlots.whyNot(level, hall, plain, null).isPresent(), "this village's slot is still free");
			helper.assertTrue(LegendSlots.whyNot(level, other, plain, null).isEmpty(), "the next village's slot is taken by a Legend who settled here");
			helper.succeed();
		}));
	}

	/**
	 * A guest killed mid-stay is no guest: once the body is gone (20 ticks), the next morning another Legend may come
	 * ("a village with no Legend guest may get one"), rather than the hall waiting out the dead guest's stay.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLegendGuestKilled"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendGuestKilled")
	public void qaGuestKilledMidStayFreesTheVillage(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		QaLegendGuestsGameTests.setUp(helper, recorded);
		Legend first = QaLegendGuestsGameTests.rare("qa_killed_mason", ", \"chance\": 1.0", "");
		Legend second = QaLegendGuestsGameTests.rare("qa_next_mason", ", \"chance\": 1.0", "");
		Villager[] guest = new Villager[1];
		helper.runAfterDelay(2, () -> QaLegendGuestsGameTests.staged(helper, List.of(first, second), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(QaLegendGuestsGameTests.HALL);
			QaLegendGuestsGameTests.at(level, 1, 1000);
			guest[0] = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(40L));
			helper.assertTrue(guest[0] != null, "no guest on day 1 at chance 1");
			guest[0].kill();
		}));
		// the body is gone after 20 ticks; the hall's own round (every 600 ticks) may well not have looked in between
		helper.runAfterDelay(60, () -> QaLegendGuestsGameTests.staged(helper, List.of(first, second), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(QaLegendGuestsGameTests.HALL);
			helper.assertTrue(level.getEntity(guest[0].getUUID()) == null, "the dead guest's body is still in the level");
			helper.assertTrue(LegendRecord.get(level).entry(guest[0].getUUID()).isEmpty(), "a dead guest went on the record");
			QaLegendGuestsGameTests.at(level, 2, 1000);
			Villager next = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(41L));
			helper.assertTrue(next != null && ModAttachments.LEGEND.get(next).id().equals(second.id()),
				"no guest the morning after the guest was killed: the hall still holds the dead one (" + QaLegendGuestsGameTests.hall(helper,
					QaLegendGuestsGameTests.HALL).legendGuests().guest() + ")");
			helper.succeed();
		}));
	}

	/**
	 * A Legendary is one per world: a Legendary guest in each of two villages, both villages meeting every need, never
	 * makes two of them settled.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLegendGuestTwoLegendary"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendGuestTwoLegendary")
	public void qaOneLegendaryNeverSettlesInTwoVillages(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		QaLegendGuestsGameTests.setUp(helper, recorded);
		helper.setBlock(QaLegendGuestsGameTests.OTHER_HALL, ModBlocks.VILLAGE_HALL);
		Legend legendary = QaLegendGuestsGameTests.legend("qa_twice_legendary", "legendary", ", \"chance\": 1.0", "", QaLegendGuestsGameTests.PACE);
		helper.runAfterDelay(2, () -> QaLegendGuestsGameTests.staged(helper, List.of(legendary), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(QaLegendGuestsGameTests.HALL);
			BlockPos other = helper.absolutePos(QaLegendGuestsGameTests.OTHER_HALL);
			QaLegendGuestsGameTests.wellbeing(helper, QaLegendGuestsGameTests.HALL, 0.8f);
			QaLegendGuestsGameTests.wellbeing(helper, QaLegendGuestsGameTests.OTHER_HALL, 0.8f);
			QaLegendGuestsGameTests.at(level, 1, 1000);
			Villager a = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(42L));
			helper.assertTrue(a != null, "no Legendary guest at the first village");
			Villager b = LegendGuests.visit(level, other, "hall", other, RandomSource.create(43L));
			QaLegendGuestsGameTests.home(helper, "qa_twice_manor_3", new BlockPos(5, 2, 9), new BlockPos(7, 2, 11), recorded);
			QaLegendGuestsGameTests.home(helper, "qa_twice_villa_3", new BlockPos(12, 2, 4), new BlockPos(14, 2, 6), recorded);
			LegendGuests.tend(level, hall);
			LegendGuests.tend(level, other);
			long settled = LegendRecord.get(level).holding(e -> e.id().equals(legendary.id()));
			helper.assertTrue(settled <= 1, settled + " of one Legendary settled in the world (second guest: " + (b == null ? "none" : "came") + ")");
			helper.succeed();
		}));
	}
}
