package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-02, lane-c-1102-0053) of the fixes for B761, B772, B779 and B780, written from the bugs' expected
 * behaviour, from the side of each fix its own tests don't reach:
 * <ul>
 *   <li>B761 (a brown or green bed put straight back where a wagon bedroll stood is a home): a green bed, and the
 *   spot stays off the list across a restart; a bedroll someone sleeps in, and a brown bed put somewhere else in the
 *   village, leave the camp's bedrolls listed.</li>
 *   <li>B772 (drill day counts the same with or without a restart): drillers across two restarts, one coming after
 *   the first; one driller reads "1 villager"; the next drill day starts its count again after a restart.</li>
 *   <li>B779 (a grave goes to the Graveyard of the village it lies in): where two villages overlap, a grave nearer this
 *   village's hall is laid in its Graveyard, one nearer the other hall stays.</li>
 *   <li>B780 (any death inside the village is written in its chronicle): the height edge (HEIGHT above the hall
 *   written, a block more not) and two overlapping villages (each death in exactly the nearer hall's chronicle).</li>
 *   <li>31.22's drill day: with no dummies only the guards drill; a baby at the dummies doesn't.</li>
 * </ul>
 */
public class QaVillageFixesVerify1102GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- B761: a bed put back where a wagon bedroll stood -------------------------------------------------------------

	private static final BlockPos HALL = new BlockPos(15, 2, 20);

	interface CampBody {
		void run(ServerLevel level, List<BlockPos> rolls);
	}

	/** A real settlers' camp and a village founded round it; once the hall's rounds hold the bedrolls, {@code body}. */
	private static void camp(GameTestHelper helper, CampBody body) {
		QaVillageGuards1031GameTests.campArea(helper);
		int was = VillageNeeds.CHECK_EVERY;
		VillageNeeds.CHECK_EVERY = 20;
		Leftovers.after(helper, () -> VillageNeeds.CHECK_EVERY = was);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BoundingBox[] campBox = new BoundingBox[1];
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(15, 2, 4)), b -> campBox[0] = b);
		helper.assertTrue(!settlers.isEmpty() && campBox[0] != null, "set-up: no camp was placed");
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> rolls = new ArrayList<>();
		BoundingBox box = campBox[0];
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.BEDS) && s.getValue(BedBlock.PART) == BedPart.HEAD && (s.is(Blocks.BROWN_BED) || s.is(Blocks.GREEN_BED))) {
				rolls.add(p.immutable());
			}
		}
		helper.assertTrue(rolls.size() >= 2, "set-up: the camp has " + rolls.size() + " bedrolls, not 2 or more");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(45, () -> {
			for (BlockPos roll : rolls) {
				helper.assertTrue(CampBeds.isWagonBed(level, roll) && level.getPoiManager().getFreeTickets(roll) == 0,
					"set-up: the hall didn't hold the camp's bedroll at " + helper.relativePos(roll));
			}
			body.run(level, rolls);
		});
	}

	/** Takes away the camp's campfires, hay and table (the camp is cleared away), so no bed has a camp's look. */
	private static void clearCampLook(GameTestHelper helper) {
		BoundingBox b = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 10, 29)));
		for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
			BlockState s = helper.getLevel().getBlockState(p);
			if (s.is(BlockTags.CAMPFIRES) || s.is(Blocks.HAY_BLOCK) || s.is(ModBlocks.BLUEPRINT_TABLE) || s.is(ModBlocks.BUILDERS_BENCH)) {
				helper.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
		}
	}

	private static Direction breakBed(ServerLevel level, BlockPos head) {
		Direction facing = level.getBlockState(head).getValue(BedBlock.FACING);
		level.setBlock(head.relative(facing.getOpposite()), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		level.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		return facing;
	}

	private static void placeBed(ServerLevel level, BlockPos head, Direction facing, Block bed) {
		level.setBlockAndUpdate(head.relative(facing.getOpposite()), bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT));
		level.setBlockAndUpdate(head, bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** The live wagon-bed list saved and loaded again, put in its place (a server stop and start). */
	private static CampBeds.Data restartBeds(ServerLevel level) {
		CompoundTag saved = CampBeds.Data.get(level).save(new CompoundTag(), level.registryAccess());
		CampBeds.Data loaded = CampBeds.Data.load(saved, level.registryAccess());
		level.getDataStorage().set(CampBeds.Data.NAME, loaded);
		return loaded;
	}

	/**
	 * B761 with a green bed (the spec: "his own brown (or green) bed") put straight back on a broken bedroll's spot, the
	 * camp cleared away, and the server restarted before anyone takes it: the spot is still off the list after the
	 * restart, a villager takes the bed as a home and keeps it at the next rounds, and Beds counts it.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaFix1102GreenBedRestart"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaFix1102GreenBedRestart")
	public void qaAGreenBedPutBackAtOnceStaysAHomeAfterARestart(GameTestHelper helper) {
		camp(helper, (level, rolls) -> {
			clearCampLook(helper);
			BlockPos spot = rolls.get(0);
			Direction facing = breakBed(level, spot);
			placeBed(level, spot, facing, Blocks.GREEN_BED);
			helper.assertTrue(!CampBeds.Data.get(level).has(spot), "the green bed put back on the bedroll's spot is still on the wagon-bed list");
			CampBeds.Data after = restartBeds(level);
			helper.assertTrue(!after.has(spot) && !after.unverified(spot) && !CampBeds.isWagonBed(level, spot),
				"after a restart the green bed is a wagon bed again: listed " + after.has(spot) + ", unverified " + after.unverified(spot));
			Villager resident = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 26));
			resident.setNoAi(true);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(CampBeds.claim(level, resident, spot), "the green bed can't be taken (wagon " + CampBeds.isWagonBed(level, spot) + ")");
				helper.runAfterDelay(45, () -> {
					helper.assertTrue(spot.equals(QaVillageGuards1031GameTests.home(resident)) && !CampBeds.isWagonBed(level, spot),
						"the green bed's resident lost it at the hall's round: home " + QaVillageGuards1031GameTests.home(resident));
					VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
					helper.assertTrue(census.beds() == 1 && census.wagonBeds() == rolls.size() - 1,
						"census: beds " + census.beds() + " (want 1), wagon beds " + census.wagonBeds() + " (want " + (rolls.size() - 1) + ")");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * B761 through the People table's Bed list (40.4: "only beds in that village"; a wagon's bedrolls are no homes): the
	 * camp's held bedrolls are never offered, and a brown bed put straight back on a broken bedroll's spot is offered as a
	 * free bed at once, before the hall's next round, and still after it; choosing it makes it the villager's home.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaFix1102BedListPutBack"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaFix1102BedListPutBack")
	public void qaABedPutBackOnABedrollsSpotIsOfferedFreeInTheBedList(GameTestHelper helper) {
		camp(helper, (level, rolls) -> {
			BlockPos hall = helper.absolutePos(HALL);
			List<BlockPos> offered = io.github.jcondedata.aliveworkplace.hall.HallBeds.options(level, hall).stream().map(o -> o.bed()).toList();
			helper.assertTrue(rolls.stream().noneMatch(offered::contains), "the Bed list offers the camp's bedrolls: " + offered);
			clearCampLook(helper);
			BlockPos spot = rolls.get(0);
			Direction facing = breakBed(level, spot);
			placeBed(level, spot, facing, Blocks.BROWN_BED);
			java.util.function.Supplier<String> check = () -> {
				List<io.github.jcondedata.aliveworkplace.hall.HallBeds.Option> options = io.github.jcondedata.aliveworkplace.hall.HallBeds.options(level, hall);
				boolean free = options.stream().anyMatch(o -> o.bed().equals(spot) && !o.taken());
				boolean bedroll = options.stream().anyMatch(o -> !o.bed().equals(spot) && rolls.contains(o.bed()));
				return free && !bedroll ? null : "the Bed list " + options.stream().map(o -> helper.relativePos(o.bed()) + (o.taken() ? " taken" : " free")).toList()
					+ " (the bed put back at " + helper.relativePos(spot) + ")";
			};
			String now = check.get();
			helper.assertTrue(now == null, "at once: " + now);
			helper.runAfterDelay(45, () -> {
				String later = check.get();
				helper.assertTrue(later == null, "after the hall's round: " + later);
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 26));
				v.setNoAi(true);
				helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.HallBeds.moveIn(level, v, spot) && spot.equals(QaVillageGuards1031GameTests.home(v)),
					"choosing the bed put back didn't make it the villager's home: " + QaVillageGuards1031GameTests.home(v));
				helper.succeed();
			});
		});
	}

	/**
	 * The other side of B761's fix: someone lying down in a bedroll (the bed's own state changes) and a player putting a
	 * brown bed somewhere else in the village leave every bedroll of the camp listed and held, and the new bed is a home.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaFix1102BedrollsStay"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaFix1102BedrollsStay")
	public void qaSleepingInABedrollOrABedElsewhereKeepsTheBedrollsListed(GameTestHelper helper) {
		camp(helper, (level, rolls) -> {
			BlockPos spot = rolls.get(0);
			BlockState head = level.getBlockState(spot);
			level.setBlock(spot, head.setValue(BedBlock.OCCUPIED, true), Block.UPDATE_ALL);
			helper.assertTrue(CampBeds.Data.get(level).has(spot), "lying down in a bedroll took it off the wagon-bed list");
			level.setBlock(spot, level.getBlockState(spot).setValue(BedBlock.OCCUPIED, false), Block.UPDATE_ALL);
			BlockPos elsewhere = helper.absolutePos(new BlockPos(25, 2, 27));
			helper.assertTrue(level.getBlockState(elsewhere).isAir() && level.getBlockState(elsewhere.north()).isAir(), "set-up: the spot for the new bed isn't free");
			placeBed(level, elsewhere, Direction.SOUTH, Blocks.BROWN_BED);
			for (BlockPos roll : rolls) {
				helper.assertTrue(CampBeds.Data.get(level).has(roll), "a bed put elsewhere took the bedroll at " + helper.relativePos(roll) + " off the list");
			}
			helper.runAfterDelay(45, () -> {
				for (BlockPos roll : rolls) {
					helper.assertTrue(CampBeds.isWagonBed(level, roll) && level.getPoiManager().getFreeTickets(roll) == 0,
						"after a round the bedroll at " + helper.relativePos(roll) + " isn't a held wagon bed");
				}
				helper.assertTrue(!CampBeds.isWagonBed(level, elsewhere), "the player's own brown bed elsewhere is taken for a bedroll");
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == rolls.size(),
					"census: beds " + census.beds() + " (want 1), wagon beds " + census.wagonBeds() + " (want " + rolls.size() + ")");
				helper.succeed();
			});
		});
	}

	// ---- B772: drill day's count with restarts ------------------------------------------------------------------------

	/**
	 * Drill day with the server restarted twice mid-drill (after a morning autosave): Ana drills, restart, Ana goes off
	 * and Ben drills, restart, Ben drills again. The line counts each of them once: "2 villagers", as without restarts.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFix1102DrillTwoRestarts"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFix1102DrillTwoRestarts")
	public void qaTwoRestartsMidDrillCountEachDrillerOnce(GameTestHelper helper) {
		QaVillageEconomySaves1101GameTests.drillScene(helper, (level, hall) -> {
			Villager ana = QaVillageEconomySaves1101GameTests.driller(helper, 0);
			long today = Chronicle.day(level);
			String village = VillageHalls.name(level, hall).getString();
			QaVillageEconomySaves1101GameTests.drillToday(level, hall, today);
			QaVillageEconomySaves1101GameTests.beat(level, today, 1000);
			QaVillageEconomySaves1101GameTests.restart(level, false); // the morning's autosave
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_START + 20);
			QaVillageEconomySaves1101GameTests.restart(level, false);
			ana.discard(); // she drilled and went off: only the saved count remembers her
			QaVillageEconomySaves1101GameTests.driller(helper, 1);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_START + 40);
			QaVillageEconomySaves1101GameTests.restart(level, false);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_START + 60);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_END + 20);
			List<String> lines = QaVillageEconomySaves1101GameTests.drillLines(level, hall, village);
			String expected = "Drill day in " + village + ": 2 villagers sparred at the dummies.";
			helper.assertTrue(lines.equals(List.of(expected)), "after two restarts mid-drill the chronicle reads " + lines + ", not [" + expected + "]");
		});
	}

	/**
	 * One villager drills and the server restarts mid-drill: the line reads "1 villager" (the singular). After another
	 * restart, the next drill day counts afresh: Cal drills in its first beat and leaves, then (after a restart) two
	 * newcomers drill; the second line counts 3, not 4 (Ana, who drilled the first day and has left, isn't counted again).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFix1102DrillNextDay"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFix1102DrillNextDay")
	public void qaOneDrillerIsSingularAndTheNextDrillDayCountsAfresh(GameTestHelper helper) {
		QaVillageEconomySaves1101GameTests.drillScene(helper, (level, hall) -> {
			Villager ana = QaVillageEconomySaves1101GameTests.driller(helper, 0);
			long today = Chronicle.day(level);
			String village = VillageHalls.name(level, hall).getString();
			QaVillageEconomySaves1101GameTests.drillToday(level, hall, today);
			QaVillageEconomySaves1101GameTests.beat(level, today, 1000);
			QaVillageEconomySaves1101GameTests.restart(level, false);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_START + 20);
			QaVillageEconomySaves1101GameTests.restart(level, false);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_END + 20);
			String one = "Drill day in " + village + ": 1 villager sparred at the dummies.";
			List<String> first = QaVillageEconomySaves1101GameTests.drillLines(level, hall, village);
			helper.assertTrue(first.equals(List.of(one)), "one driller with a restart: the chronicle reads " + first + ", not [" + one + "]");

			// The next drill day: Cal drills in its first beat and leaves; Ana and Ben drill after a restart.
			ana.discard();
			Villager cal = QaVillageEconomySaves1101GameTests.driller(helper, 0);
			ReformStories.Data data = ReformStories.Data.get(level);
			ReformStories.Entry e = data.entry(hall);
			e.drillDay = today + 1;
			e.drillDone = false;
			data.setDirty();
			QaVillageEconomySaves1101GameTests.restart(level, false);
			QaVillageEconomySaves1101GameTests.beat(level, today + 1, ReformStories.DRILL_START + 20);
			cal.discard();
			QaVillageEconomySaves1101GameTests.restart(level, false);
			QaVillageEconomySaves1101GameTests.driller(helper, 0);
			QaVillageEconomySaves1101GameTests.driller(helper, 1);
			QaVillageEconomySaves1101GameTests.beat(level, today + 1, ReformStories.DRILL_START + 40);
			QaVillageEconomySaves1101GameTests.restart(level, false);
			QaVillageEconomySaves1101GameTests.beat(level, today + 1, ReformStories.DRILL_END + 20);
			String three = "Drill day in " + village + ": 3 villagers sparred at the dummies.";
			List<String> both = QaVillageEconomySaves1101GameTests.drillLines(level, hall, village);
			helper.assertTrue(both.equals(List.of(one, three)), "the second drill day (Cal, then Ana and Ben) reads " + both + ", not [" + one + ", " + three + "]");
		});
	}

	// ---- B779: graves where two villages overlap ----------------------------------------------------------------------

	/**
	 * Two villages (radius 12) overlap: this one with the Undertaker and Graveyard, the next one's hall 17 blocks east.
	 * Ada dies 7 blocks from this hall and 10 from the next (in both, nearer this one): her grave is laid in this
	 * Graveyard. Bo dies 11 from this hall and 6 from the next (in both, nearer that one): his grave stays where he fell
	 * the whole time, 400 ticks after Ada's was laid too.
	 */
	//$ gametest_ticks_batch HUGE '2600' '"qaFix1102GravesOverlap"'
	@GameTest(template = HUGE, timeoutTicks = 2600, batch = "qaFix1102GravesOverlap")
	public void qaWhereVillagesOverlapAGraveGoesToTheNearerHallsGraveyard(GameTestHelper helper) {
		QaVillageReachGraves1101GameTests.graveVillage(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = helper.absolutePos(QaVillageReachGraves1101GameTests.G_HALL);
		BlockPos hallBRel = new BlockPos(20, 2, 3);
		helper.setBlock(hallBRel, ModBlocks.VILLAGE_HALL);
		BlockPos hallB = helper.absolutePos(hallBRel);
		VillageHalls.forgetNearest();
		BlockPos adaAt = new BlockPos(10, 2, 3);
		BlockPos boAt = new BlockPos(14, 2, 4);
		for (BlockPos p : List.of(adaAt, boAt)) {
			BlockPos abs = helper.absolutePos(p);
			helper.assertTrue(VillageHalls.contains(hallA, abs) && VillageHalls.contains(hallB, abs), "set-up: " + p + " isn't in both villages");
		}
		helper.assertTrue(VillageHalls.containing(level, helper.absolutePos(adaAt)).equals(Optional.of(hallA)), "set-up: Ada's spot isn't this village's");
		helper.assertTrue(VillageHalls.containing(level, helper.absolutePos(boAt)).equals(Optional.of(hallB)), "set-up: Bo's spot isn't the next village's");
		BlockPos boFell = QaVillageReachGraves1101GameTests.dies(helper, boAt, "Bo", 1);
		BlockPos adaFell = QaVillageReachGraves1101GameTests.dies(helper, adaAt, "Ada", 0);
		QaVillageReachGraves1101GameTests.undertaker(helper);
		helper.onEachTick(() -> {
			if (!level.getBlockState(boFell).is(ModBlocks.GRAVE)) {
				throw new GameTestAssertException("Bo's grave, in both villages but nearer the next hall, was moved (at tick " + helper.getTick() + ")");
			}
		});
		AtomicLong laid = new AtomicLong(-1);
		helper.succeedWhen(() -> {
			boolean adaLaid = "Ada".equals(QaVillageReachGraves1101GameTests.nameAt(level, QaVillageReachGraves1101GameTests.slot(helper, 1)))
				&& !level.getBlockState(adaFell).is(ModBlocks.GRAVE);
			helper.assertTrue(adaLaid, "Ada's grave, in both villages but nearer this hall, wasn't laid in this Graveyard");
			laid.compareAndSet(-1, helper.getTick());
			helper.assertTrue(helper.getTick() >= laid.get() + 400, "watching Bo's grave a while longer");
		});
	}

	// ---- B780: chronicle lines at the village's height edge and where villages overlap --------------------------------

	/**
	 * In a 16-block village, Mira killed exactly HEIGHT (32) blocks above the hall (inside the village) is written in the
	 * hall's chronicle; Tam killed 33 above (in no village) isn't.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFix1102ChronicleHeight"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFix1102ChronicleHeight")
	public void qaADeathAtTheVillagesTopIsWrittenAndOneAboveItIsNot(GameTestHelper helper) {
		BlockPos h = QaVillageReachGraves1101GameTests.mourningVillage(helper, QaVillageReachGraves1101GameTests.M_HALL, 16);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			QaVillageReachGraves1101GameTests.ready(helper, h);
			BlockPos top = h.offset(0, 32, 3);
			BlockPos above = h.offset(3, 33, 0);
			QaVillageCircleGameTests.staged(helper, h, List.of(top), List.of(above));
			helper.assertTrue(VillageHalls.containing(level, above).isEmpty(), "setup: the spot above the village is in a village");
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(h);
			int before = hall.chronicle().size();
			QaVillageReachGraves1101GameTests.farmerAt(helper, above, "Tam").kill();
			QaVillageReachGraves1101GameTests.farmerAt(helper, top, "Mira").kill();
			List<String> deaths = deaths(hall, before);
			helper.assertTrue(deaths.stream().anyMatch(l -> l.startsWith("Mira")), "Mira's death at the village's top wasn't written: " + deaths);
			helper.assertTrue(deaths.stream().noneMatch(l -> l.startsWith("Tam")), "Tam's death above the village was written: " + deaths);
			helper.succeed();
		});
	}

	/**
	 * Two 16-block villages whose halls stand 24 apart overlap. Ada is killed 10 from the west hall and 14 from the east
	 * one, Bea 14 from the west and 10 from the east: each death is written once, in the chronicle of the hall nearer
	 * across (the village it is in), and not in the other's.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFix1102ChronicleOverlap"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFix1102ChronicleOverlap")
	public void qaWhereVillagesOverlapADeathIsWrittenInTheNearerHallsChronicleOnly(GameTestHelper helper) {
		BlockPos west = QaVillageReachGraves1101GameTests.mourningVillage(helper, QaVillageReachGraves1101GameTests.M_HALL, 16);
		BlockPos eastRel = QaVillageReachGraves1101GameTests.M_HALL.offset(24, 0, 0);
		helper.setBlock(eastRel, ModBlocks.VILLAGE_HALL);
		BlockPos east = helper.absolutePos(eastRel);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			QaVillageReachGraves1101GameTests.ready(helper, west);
			QaVillageReachGraves1101GameTests.ready(helper, east);
			BlockPos ada = west.offset(10, 0, 2);
			BlockPos bea = west.offset(14, 0, -2);
			QaVillageCircleGameTests.staged(helper, west, List.of(ada, bea), List.of());
			QaVillageCircleGameTests.staged(helper, east, List.of(ada, bea), List.of());
			VillageHallBlockEntity w = (VillageHallBlockEntity) level.getBlockEntity(west);
			VillageHallBlockEntity e = (VillageHallBlockEntity) level.getBlockEntity(east);
			int wBefore = w.chronicle().size();
			int eBefore = e.chronicle().size();
			QaVillageReachGraves1101GameTests.farmerAt(helper, ada, "Ada").kill();
			QaVillageReachGraves1101GameTests.farmerAt(helper, bea, "Bea").kill();
			List<String> wDeaths = deaths(w, wBefore);
			List<String> eDeaths = deaths(e, eBefore);
			helper.assertTrue(wDeaths.stream().filter(l -> l.startsWith("Ada")).count() == 1 && wDeaths.stream().noneMatch(l -> l.startsWith("Bea")),
				"the west hall's chronicle (Ada's village, not Bea's) reads " + wDeaths);
			helper.assertTrue(eDeaths.stream().filter(l -> l.startsWith("Bea")).count() == 1 && eDeaths.stream().noneMatch(l -> l.startsWith("Ada")),
				"the east hall's chronicle (Bea's village, not Ada's) reads " + eDeaths);
			helper.succeed();
		});
	}

	// ---- 31.22: drill day's edges (who drills) ------------------------------------------------------------------------

	/** Drill day with no Training Dummy in reach (every stray one taken away); then {@code body}. */
	static void noDummyScene(GameTestHelper helper, QaVillageEconomySaves1101GameTests.DrillBody body) {
		Leftovers.clear(helper);
		helper.setBlock(QaVillageEconomySaves1101GameTests.HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(QaVillageEconomySaves1101GameTests.HALL);
			for (BlockPos stray : ReformStories.dummies(level, hall)) {
				level.removeBlock(stray, false);
			}
			ReformStories.forget(level, hall);
			helper.assertTrue(ReformStories.dummies(level, hall).isEmpty(), "set-up: dummies about: " + ReformStories.dummies(level, hall));
			body.run(level, hall);
			ReformStories.Data.get(level).entry(hall).drillDone = true;
			ReformStories.Data.get(level).setDirty();
			helper.succeed();
		});
	}

	static Villager standing(GameTestHelper helper, BlockPos rel, net.minecraft.world.entity.npc.VillagerProfession job, int age) {
		Villager v = helper.spawn(EntityType.VILLAGER, rel);
		v.setNoAi(true);
		v.setAge(age);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	/** One whole drill day at {@code hall} today: the morning, a beat at noon, the end. The day's drill lines. */
	static List<String> drillDay(ServerLevel level, BlockPos hall) {
		long today = Chronicle.day(level);
		QaVillageEconomySaves1101GameTests.drillToday(level, hall, today);
		QaVillageEconomySaves1101GameTests.beat(level, today, 1000);
		QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_START + 20);
		QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_END + 20);
		return QaVillageEconomySaves1101GameTests.drillLines(level, hall, VillageHalls.name(level, hall).getString());
	}

	/**
	 * 31.22 ("at noon the next day every grown villager spars ... at the village's Training Dummies (the guards alone if
	 * there are none)"): with no dummy in reach, a Guard and a Farmer standing by the hall at noon: only the Guard drills,
	 * so the line counts 1.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFix1102DrillGuardsAlone"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFix1102DrillGuardsAlone")
	public void qaWithNoDummiesOnlyTheGuardsDrill(GameTestHelper helper) {
		noDummyScene(helper, (level, hall) -> {
			standing(helper, QaVillageEconomySaves1101GameTests.HALL.offset(1, 0, 0), io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD, 0);
			standing(helper, QaVillageEconomySaves1101GameTests.HALL.offset(-1, 0, 0), net.minecraft.world.entity.npc.VillagerProfession.FARMER, 0);
			List<String> lines = drillDay(level, hall);
			String village = VillageHalls.name(level, hall).getString();
			helper.assertTrue(lines.size() == 1 && lines.get(0).startsWith("Drill day in " + village + ": 1 villager "),
				"with no dummies a Guard and a Farmer by the hall: the drill line reads " + lines + ", not one counting 1 villager (the Guard)");
		});
	}

	/**
	 * 31.22 ("every grown villager spars"): at the dummy a grown Farmer and a baby stand side by side at noon; only the
	 * grown one drills, so the line counts 1.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFix1102DrillNoBabies"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFix1102DrillNoBabies")
	public void qaABabyAtTheDummiesDoesNotDrill(GameTestHelper helper) {
		QaVillageEconomySaves1101GameTests.drillScene(helper, (level, hall) -> {
			standing(helper, QaVillageEconomySaves1101GameTests.DUMMY.offset(1, 0, 0), net.minecraft.world.entity.npc.VillagerProfession.FARMER, 0);
			standing(helper, QaVillageEconomySaves1101GameTests.DUMMY.offset(-1, 0, 0), net.minecraft.world.entity.npc.VillagerProfession.NONE, -24000);
			List<String> lines = drillDay(level, hall);
			String village = VillageHalls.name(level, hall).getString();
			String expected = "Drill day in " + village + ": 1 villager sparred at the dummies.";
			helper.assertTrue(lines.equals(List.of(expected)), "a grown Farmer and a baby at the dummy: the drill line reads " + lines + ", not [" + expected + "]");
		});
	}

	private static List<String> deaths(VillageHallBlockEntity hall, int before) {
		return hall.chronicle().stream().skip(before).filter(c -> c.kind() == Chronicle.Kind.DEATH).map(c -> c.text().getString()).toList();
	}
}
