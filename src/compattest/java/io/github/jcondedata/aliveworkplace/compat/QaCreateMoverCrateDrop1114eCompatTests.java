package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setup;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane e, 2026-11-14) for B1184 with Create, from B1172's promise (moving-buildings.md: "nothing packed is ever
 * destroyed: the block waits, goes back, or is dropped with its contents"). The house is moved a quarter turned and a
 * Mover's Crate is set down where the block at SPOT goes up, after the move was confirmed. A Depot with 16 diamonds and a
 * Fluid Tank (only its item judged) must be dropped there with what they hold.
 */
public class QaCreateMoverCrateDrop1114eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/**
	 * A crate set down where the block at SPOT goes up; the move run until the house is down, {@code between} run, then
	 * the move run to its end. The move's end ("stage, skipped n") in {@code end[0]}; what lies loose after it.
	 */
	static Map<String, Integer> moveOntoACrate(GameTestHelper helper, Setup s, String[] end, Runnable between) {
		ServerLevel level = helper.getLevel();
		BlockPos landing = moved(s, SPOT);
		set(level, landing, ModBlocks.MOVERS_CRATE.defaultBlockState());
		MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
		String down = site.stage().toString();
		between.run();
		site = work(level, s, x -> false);
		end[0] = site.stage() + " (" + down + " once down), skipped " + site.skipped() + ", the spot " + level.getBlockState(landing).getBlock();
		return loose(helper);
	}

	static String id(Item item) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** A Depot with 16 diamonds whose new spot holds a Mover's Crate: the depot and the 16 are dropped there. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114_mover_crate_depot"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114_mover_crate_depot")
	public void aDepotWhoseSpotHoldsACrateKeepsItsItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(SPOT), block("create:depot").defaultBlockState()); // a few ticks before it's filled
		helper.runAfterDelay(5, () -> {
			Setup s = setup(helper, "crate_depot_e1114", block("create:depot").defaultBlockState(), Items.DIAMOND, 16);
			helper.runAfterDelay(2, () -> {
				String[] end = new String[1];
				Map<String, Integer> looseNow;
				long inOld;
				long inNew;
				try {
					looseNow = moveOntoACrate(helper, s, end, () -> { });
					inOld = held(level, helper.absolutePos(SPOT), Items.DIAMOND);
					inNew = held(level, moved(s, SPOT), Items.DIAMOND);
				} finally {
					tidy(helper, s);
				}
				int lying = looseNow.getOrDefault("minecraft:diamond", 0);
				helper.assertTrue(end[0].startsWith("DONE") && end[0].contains("skipped 1,"), "the move: " + end[0]);
				helper.assertTrue(inOld + inNew + lying == 16, "a depot holding 16 diamonds went up over a Mover's Crate (move " + end[0] + "): " + inOld
					+ " at the old spot, " + inNew + " at the new and " + lying + " loose; loose " + looseNow + " (nothing packed may be destroyed, B1172)");
				helper.assertTrue(looseNow.getOrDefault("create:depot", 0) == 1, "the depot itself isn't dropped: loose " + looseNow);
				helper.succeed();
			});
		});
	}

	/** A Fluid Tank whose new spot holds a Mover's Crate: the tank is dropped there as an item (only items judged). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114_mover_crate_tank"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114_mover_crate_tank")
	public void aFluidTankWhoseSpotHoldsACrateIsDroppedThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(SPOT), block("create:fluid_tank").defaultBlockState());
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(level.getBlockEntity(helper.absolutePos(SPOT)) != null, "setup: the tank has no block entity");
			Setup s = setupAround(helper, "crate_tank_e1114");
			helper.runAfterDelay(2, () -> {
				String[] end = new String[1];
				Map<String, Integer> looseNow;
				try {
					looseNow = moveOntoACrate(helper, s, end, () -> { });
				} finally {
					tidy(helper, s);
				}
				helper.assertTrue(end[0].startsWith("DONE") && end[0].contains("skipped 1,"), "the move: " + end[0]);
				helper.assertTrue(looseNow.getOrDefault("create:fluid_tank", 0) == 1, "a fluid tank went up over a Mover's Crate (move " + end[0]
					+ "): it isn't dropped there; loose " + looseNow + " (nothing packed may be destroyed, B1172)");
				helper.succeed();
			});
		});
	}

	/** Stone in the 16 blocks right above {@code landing}: no free spot there. */
	static void roofAbove(ServerLevel level, BlockPos landing) {
		for (int dy = 1; dy <= 16; dy++) {
			set(level, landing.above(dy), Blocks.STONE.defaultBlockState());
		}
	}
}
