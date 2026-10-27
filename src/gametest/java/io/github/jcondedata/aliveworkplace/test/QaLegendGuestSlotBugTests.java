package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.OTHER_HALL;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.PACE;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.at;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.home;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.legend;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.setUp;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.staged;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.wellbeing;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA bug tests (lane d, 2026-10-27), failing on main: a Legend guest isn't on the record (29.3) while they stay, and
 * {@code LegendGuests.settle} doesn't ask {@code Legends.canCome} again, so the caps of 29.3 can be passed by a guest who
 * settles: one Legendary visiting two villages at once settles in both, and a Rare guest settles where another of
 * their kind came to live during their stay. Spec: 29.3 "Legendary: one of each per world, in any dimension"; "Rare:
 * one of each per village"; 29.8 "a Legend ... whose slot is free (29.3)". Either fix passes (refuse the second guest,
 * or refuse the settling): the tests only check that the caps hold at the end.
 */
public class QaLegendGuestSlotBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static long holding(ServerLevel level, Legend legend) {
		return LegendRecord.get(level).holding(e -> e.id().equals(legend.id()));
	}

	/** One Legendary, guests in two villages at once, both villages meet every need: at most one of them settles. */
	//$ gametest_ticks_batch AREA '100' '"qaBugLegendaryTwice"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBugLegendaryTwice")
	public void qaOneLegendaryNeverSettlesInTwoVillages(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.setBlock(OTHER_HALL, ModBlocks.VILLAGE_HALL);
		Legend legendary = legend("qa_bug_legendary", "legendary", ", \"chance\": 1.0", "", PACE);
		helper.runAfterDelay(2, () -> staged(helper, List.of(legendary), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos a = helper.absolutePos(HALL);
			BlockPos b = helper.absolutePos(OTHER_HALL);
			wellbeing(helper, HALL, 0.8f);
			wellbeing(helper, OTHER_HALL, 0.8f);
			at(level, 1, 1000);
			Villager first = LegendGuests.visit(level, a, "hall", a, RandomSource.create(61L));
			helper.assertTrue(first != null, "no Legendary guest in the first village");
			Villager second = LegendGuests.visit(level, b, "hall", b, RandomSource.create(62L));
			// a home of their own in each village, then the hall rounds
			home(helper, "qa_bug_leg_manor_3", new BlockPos(8, 2, 8), new BlockPos(10, 2, 10), recorded);
			home(helper, "qa_bug_leg_villa_3", new BlockPos(14, 2, 2), new BlockPos(16, 2, 4), recorded);
			at(level, 1, 2000);
			LegendGuests.tend(level, a);
			LegendGuests.tend(level, b);
			helper.assertTrue(holding(level, legendary) == 1, "the Legendary on the record " + holding(level, legendary) + " times (second guest came: "
				+ (second != null) + "); one of each per world");
			helper.succeed();
		}));
	}

	/** A Rare guest staying; another of their kind settles in the village meanwhile; the guest's needs met: they must not settle too. */
	//$ gametest_ticks_batch AREA '100' '"qaBugRareTwice"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBugRareTwice")
	public void qaARareGuestDoesNotSettleWhereTheirKindNowLives(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend rare = legend("qa_bug_rare", "rare", ", \"chance\": 1.0", "", PACE);
		helper.runAfterDelay(2, () -> staged(helper, List.of(rare), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos a = helper.absolutePos(HALL);
			wellbeing(helper, HALL, 0.8f);
			at(level, 1, 1000);
			Villager guest = LegendGuests.visit(level, a, "hall", a, RandomSource.create(63L));
			helper.assertTrue(guest != null, "no Rare guest");
			// the same Rare comes to live in the village another way (a strange mood, a child grown up: canCome is asked first)
			Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 16));
			other.setNoAi(true);
			if (Legends.canCome(level, a, rare)) {
				Legends.make(level, other, rare, "test", Optional.of(a));
			}
			home(helper, "qa_bug_rare_manor_3", new BlockPos(8, 2, 8), new BlockPos(10, 2, 10), recorded);
			at(level, 2, 1000);
			LegendGuests.tend(level, a);
			String dim = level.dimension().location().toString();
			long here = LegendRecord.get(level).holding(e -> e.id().equals(rare.id()) && e.in(dim, a));
			helper.assertTrue(here == 1, "the Rare on the record " + here + " times in one village; one of each per village");
			helper.succeed();
		}));
	}
}
