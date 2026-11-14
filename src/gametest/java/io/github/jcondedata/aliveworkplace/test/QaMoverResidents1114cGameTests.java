package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.BED_A;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.BED_B;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.HALL_BED;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.INN_BED_1;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.INN_BED_2;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.OUT_BED;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.bed;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.confirm;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.memory;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.moved;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.noted;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.remembers;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.setup;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.start;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.villager;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.work;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.Setup;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane c, 2026-11-14) for 45.4 "The residents move too", from the wiki's words: "While it's away, those with a bed
 * in it sleep in a free bed at the village's inn ..., or else the free bed nearest the Village Hall; with none free they
 * have no bed until theirs is back" and "The moment the Mover puts a bed ... up at the new spot it's theirs again: nobody
 * else can take it, the bed at the inn is given back". Edges the earlier tests leave out: the inn wins over a free bed
 * by the Hall, a full inn falls back to the Hall's bed, no free bed anywhere, and the moment the bed goes up (before the
 * move is over). Reuses {@link QaMoverResidents1110cGameTests}'s house, inn and Mover.
 */
public class QaMoverResidents1114cGameTests implements FabricGameTest {
	static final String AREA = QaMoverResidents1110cGameTests.AREA;

	private static BlockPos home(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static void noOtherInn(GameTestHelper helper, Setup s, BlockPos keep) {
		ServerLevel level = helper.getLevel();
		level.getPoiManager().findAll(h -> h.is(ModVillagers.INN_COUNTER_POI), p -> keep == null || !p.equals(keep), s.hall(), 80, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).toList().forEach(p -> level.removeBlock(p, false));
	}

	/** An inn with a free bed and a free bed nearer the Hall: the resident sleeps at the inn, and the Hall's bed stays free. */
	//$ gametest_batch AREA '"qaMoverResidents1114cInnFirst"'
	@GameTest(template = AREA, batch = "qaMoverResidents1114cInnFirst")
	public void theInnComesBeforeAFreeBedByTheHall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "innfirst", true);
		bed(helper, HALL_BED);
		helper.runAfterDelay(2, () -> {
			noOtherInn(helper, s, helper.absolutePos(QaMoverResidents1110cGameTests.INN));
			confirm(helper, s);
			Villager tenant = villager(helper, new BlockPos(10, 2, 3), "Tomas");
			helper.assertTrue(HallBeds.moveIn(level, tenant, helper.absolutePos(BED_A)), "setup: Tomas didn't get his bed");
			MoveSite site = start(level, s);
			helper.assertTrue(noted(site, tenant), "Tomas isn't noted as a resident");
			BlockPos stay = home(tenant);
			helper.assertTrue(helper.absolutePos(INN_BED_1).equals(stay) || helper.absolutePos(INN_BED_2).equals(stay),
				"with a free inn bed Tomas sleeps at " + stay + " (the bed by the Hall is " + helper.absolutePos(HALL_BED).toShortString() + ")");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(HALL_BED)) == 1, "the bed by the Hall was taken too");
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(remembers(level, tenant, MemoryModuleType.HOME, moved(s, BED_A)), "Tomas's bed is " + memory(tenant, MemoryModuleType.HOME));
			helper.succeed();
		});
	}

	/** Both inn beds already someone's: the resident gets the free bed by the Hall instead, and it's given back after. */
	//$ gametest_batch AREA '"qaMoverResidents1114cInnFull"'
	@GameTest(template = AREA, batch = "qaMoverResidents1114cInnFull")
	public void aFullInnFallsBackToTheBedByTheHall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "innfull", true);
		bed(helper, HALL_BED);
		helper.runAfterDelay(2, () -> {
			noOtherInn(helper, s, helper.absolutePos(QaMoverResidents1110cGameTests.INN));
			confirm(helper, s);
			Villager guest1 = villager(helper, new BlockPos(22, 2, 10), "Guest One");
			Villager guest2 = villager(helper, new BlockPos(26, 2, 10), "Guest Two");
			helper.assertTrue(HallBeds.moveIn(level, guest1, helper.absolutePos(INN_BED_1)) && HallBeds.moveIn(level, guest2, helper.absolutePos(INN_BED_2)),
				"setup: the inn's guests didn't get their beds");
			Villager tenant = villager(helper, new BlockPos(10, 2, 3), "Tomas");
			helper.assertTrue(HallBeds.moveIn(level, tenant, helper.absolutePos(BED_A)), "setup: Tomas didn't get his bed");
			MoveSite site = start(level, s);
			helper.assertTrue(remembers(level, tenant, MemoryModuleType.HOME, helper.absolutePos(HALL_BED)),
				"with the inn full Tomas sleeps at " + memory(tenant, MemoryModuleType.HOME) + ", not the free bed by the Hall " + helper.absolutePos(HALL_BED).toShortString());
			helper.assertTrue(remembers(level, guest1, MemoryModuleType.HOME, helper.absolutePos(INN_BED_1))
				&& remembers(level, guest2, MemoryModuleType.HOME, helper.absolutePos(INN_BED_2)), "an inn guest lost their bed: "
				+ memory(guest1, MemoryModuleType.HOME) + ", " + memory(guest2, MemoryModuleType.HOME));
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(remembers(level, tenant, MemoryModuleType.HOME, moved(s, BED_A)), "Tomas's bed is " + memory(tenant, MemoryModuleType.HOME));
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(HALL_BED)) == 1, "the bed by the Hall wasn't given back");
			helper.succeed();
		});
	}

	/** No inn and no free bed in the village: the residents have no bed while the house is away, then their own moved beds. */
	//$ gametest_batch AREA '"qaMoverResidents1114cNoBed"'
	@GameTest(template = AREA, batch = "qaMoverResidents1114cNoBed")
	public void withNoFreeBedAnywhereTheyHaveNoneUntilTheirsIsBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "nobed", false);
		helper.runAfterDelay(2, () -> {
			noOtherInn(helper, s, null);
			level.removeBlock(helper.absolutePos(HALL_BED), false);
			level.removeBlock(helper.absolutePos(HALL_BED.north()), false);
			// beds other test areas left nearby (an earlier batch's inn) would be free: this village has none
			java.util.Set<BlockPos> ours = java.util.Set.of(helper.absolutePos(BED_A), helper.absolutePos(BED_B), helper.absolutePos(OUT_BED));
			level.getPoiManager().findAll(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), p -> !ours.contains(p), s.hall(), 96, PoiManager.Occupancy.ANY)
				.map(BlockPos::immutable).toList().forEach(p -> {
					BlockState bedState = level.getBlockState(p);
					if (bedState.getBlock() instanceof BedBlock) {
						level.removeBlock(p.relative(bedState.getValue(BedBlock.FACING).getOpposite()), false);
					}
					level.removeBlock(p, false);
				});
			confirm(helper, s);
			// the only other bed in the village is the neighbor's
			Villager neighbor = villager(helper, new BlockPos(10, 2, 24), "Nell");
			helper.assertTrue(HallBeds.moveIn(level, neighbor, helper.absolutePos(OUT_BED)), "setup: Nell didn't get her bed");
			Villager a = villager(helper, new BlockPos(10, 2, 3), "Tomas");
			Villager b = villager(helper, new BlockPos(10, 2, 5), "Dara");
			helper.assertTrue(HallBeds.moveIn(level, a, helper.absolutePos(BED_A)) && HallBeds.moveIn(level, b, helper.absolutePos(BED_B)), "setup: beds");
			MoveSite site = start(level, s);
			helper.assertTrue(noted(site, a) && noted(site, b), "both residents should be noted: " + site.residents().size());
			work(level, s, site, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house didn't come down: " + site.stage());
			helper.assertTrue(remembers(level, neighbor, MemoryModuleType.HOME, helper.absolutePos(OUT_BED)), "Nell was moved out of her bed to " + memory(neighbor, MemoryModuleType.HOME));
			BlockPos oldA = helper.absolutePos(BED_A);
			BlockPos oldB = helper.absolutePos(BED_B);
			for (Villager v : new Villager[] {a, b}) {
				BlockPos h = home(v);
				helper.assertTrue(h == null || !(level.getBlockState(h).getBlock() instanceof BedBlock),
					v.getCustomName().getString() + " was given the bed at " + h + " while the house is away and no bed is free (old beds " + oldA.toShortString() + ", " + oldB.toShortString() + ")");
			}
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(remembers(level, a, MemoryModuleType.HOME, moved(s, BED_A)), "Tomas's bed is " + memory(a, MemoryModuleType.HOME) + ", not " + moved(s, BED_A).toShortString());
			helper.assertTrue(remembers(level, b, MemoryModuleType.HOME, moved(s, BED_B)), "Dara's bed is " + memory(b, MemoryModuleType.HOME) + ", not " + moved(s, BED_B).toShortString());
			helper.assertTrue(remembers(level, neighbor, MemoryModuleType.HOME, helper.absolutePos(OUT_BED)), "Nell's bed is now " + memory(neighbor, MemoryModuleType.HOME));
			helper.succeed();
		});
	}

	/** Both halves of the moved bed standing, the move not over yet: it's already hers, held, and the inn bed is free again. */
	//$ gametest_batch AREA '"qaMoverResidents1114cMoment"'
	@GameTest(template = AREA, batch = "qaMoverResidents1114cMoment")
	public void theBedIsHersTheMomentItGoesUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "moment", true);
		helper.runAfterDelay(2, () -> {
			noOtherInn(helper, s, helper.absolutePos(QaMoverResidents1110cGameTests.INN));
			confirm(helper, s);
			Villager dara = villager(helper, new BlockPos(10, 2, 3), "Dara");
			helper.assertTrue(HallBeds.moveIn(level, dara, helper.absolutePos(BED_A)), "setup: Dara didn't get her bed");
			MoveSite site = start(level, s);
			BlockPos stay = home(dara);
			helper.assertTrue(helper.absolutePos(INN_BED_1).equals(stay) || helper.absolutePos(INN_BED_2).equals(stay), "while the house is away Dara sleeps at " + stay);
			BlockPos head = moved(s, BED_A);
			work(level, s, site, x -> x.stage() != MoveSite.Stage.DISMANTLE && whole(level, head));
			helper.assertTrue(whole(level, head), "the moved bed never stood at " + head.toShortString() + " (" + site.stage() + ")");
			helper.assertTrue(site.stage() != MoveSite.Stage.DONE, "setup: the move was already over when the bed stood");
			helper.assertTrue(remembers(level, dara, MemoryModuleType.HOME, head), "the moved bed stands but Dara's bed is still " + memory(dara, MemoryModuleType.HOME) + " (" + site.stage() + ")");
			helper.assertTrue(level.getPoiManager().getFreeTickets(head) == 0, "the moved bed stands free for anyone to take (" + site.stage() + ")");
			helper.assertTrue(level.getPoiManager().getFreeTickets(stay) == 1, "the inn bed wasn't given back once hers stood (" + site.stage() + ")");
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(remembers(level, dara, MemoryModuleType.HOME, head), "after the move Dara's bed is " + memory(dara, MemoryModuleType.HOME));
			helper.succeed();
		});
	}

	private static boolean whole(ServerLevel level, BlockPos head) {
		BlockState state = level.getBlockState(head);
		if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
			return false;
		}
		BlockState foot = level.getBlockState(head.relative(state.getValue(BedBlock.FACING).getOpposite()));
		return foot.getBlock() instanceof BedBlock && foot.getValue(BedBlock.PART) == BedPart.FOOT;
	}
}
