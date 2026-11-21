package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallMap;
import io.github.jcondedata.aliveworkplace.mechanic.VillageMachines;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA lane C (2026-11-21, lane-c-1121-1255): the follow-ups B1374's fix left (places that still read a finished building's
 * box or blocks from TODAY's drawing under its id), tested from their specs on a village whose house stands as an OLDER
 * drawing (Milestones 49-52; REBUILD.md "standing buildings ... keep their old drawing until a refresh"). Built on
 * {@link RebuildDrawings1117e}: the old house is 5x5 (local x 0-4, z 0-4, its bed on row 3 and back wall on row 4), today's
 * drawing under the same id is 7x3 (z 0-2), so the old house's back two rows lie outside today's box. Each place gets a
 * control with a house of today's drawing (the old one in the history), which must keep passing. The older-drawing cases
 * that fail on today's main are {@code QaOldHouseNeighbors1121cBugGameTests} (branch tests/old-house-neighbors-1121c);
 * the hall's map (through MoveFit.others, B1374's fix) already draws the old house as it stands.
 *
 * <ul>
 *   <li>Homes (grander-homes.md, village-hall.md People table): a bed in a finished house gives its sleeper that home.</li>
 *   <li>VillageMachines (create.md): a chest counts as the village's store when it stands inside a finished building.</li>
 *   <li>VillageFire (village-fire.md): the fire burns the builders' blocks (and they are put back), never a player's.</li>
 *   <li>BuildSiteManager.underAnotherBuild (builder.md, B838): a neighbor's levelling never digs or fills a finished
 *   building's ground.</li>
 *   <li>The Steward's plot search (steward.md "Finding a plot"): a new plot keeps 2 free blocks beside every building.</li>
 *   <li>The hall's map (village-hall.md): every building drawn at its own outline.</li>
 * </ul>
 */
public class QaOldHouseNeighbors1121cGameTests implements FabricGameTest {

	/** The old house's back wall, middle (oak planks), outside today's box. */
	static final BlockPos OLD_BACK_WALL = new BlockPos(2, 1, 4);
	/** The old house's floor under its back wall. */
	static final BlockPos OLD_BACK_FLOOR = new BlockPos(2, 0, 4);
	/** Inside the old house's back row 3, by the bed: a chest a player keeps there. */
	static final BlockPos OLD_BACK_CHEST = new BlockPos(3, 1, 3);
	/** Today's house: its back wall (spruce), floor, and inside it a bed and a chest. */
	static final BlockPos NEW_BACK_WALL = new BlockPos(1, 1, 2);
	static final BlockPos NEW_BACK_FLOOR = new BlockPos(1, 0, 2);
	static final BlockPos NEW_BED_FOOT = new BlockPos(1, 1, 1);
	static final BlockPos NEW_BED_HEAD = new BlockPos(2, 1, 1);
	static final BlockPos NEW_CHEST = new BlockPos(4, 1, 1);

	/** The village after the update: the old 5x5 house standing (furnished: its bed on row 3), today's drawing 7x3. */
	static Stage oldVillage(GameTestHelper helper, String what) {
		ResourceLocation id = id(helper, what);
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		rebuild(s.level(), id, true);
		reload(helper, s.level(), null);
		forgetCaches();
		return s;
	}

	/** Control: after the update a house built from TODAY's drawing (the old one in the history), a bed and a chest in it. */
	static Stage todayVillage(GameTestHelper helper, String what) {
		ResourceLocation id = id(helper, what);
		Stage s = stage(helper, id, false);
		rebuild(s.level(), id, false);
		for (Blueprint.Entry e : newDrawing(false)) {
			put(helper, at(e.pos()), e.state());
		}
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		put(helper, at(NEW_BED_FOOT), bed.setValue(BedBlock.PART, BedPart.FOOT));
		put(helper, at(NEW_BED_HEAD), bed.setValue(BedBlock.PART, BedPart.HEAD));
		put(helper, at(NEW_CHEST), Blocks.CHEST.defaultBlockState());
		BuildSiteManager.get(s.level()).recordFinished(id, s.placement(), s.owner().getUUID());
		reload(helper, s.level(), null);
		forgetCaches();
		return s;
	}

	static void forgetCaches() {
		VillageFire.forgetBuilds();
		HallMap.forget();
	}

	static BlockPos abs(GameTestHelper helper, BlockPos local) {
		return helper.absolutePos(at(local));
	}

	// Homes

	/** Control: a bed in a house of today's drawing gives that home. */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cHomeToday"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cHomeToday")
	public void theBedOfATodaysDrawingHouseIsInThatHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = todayVillage(helper, "hometoday");
			Optional<Homes.Home> home = Homes.at(s.level(), abs(helper, NEW_BED_HEAD));
			helper.assertTrue(home.isPresent() && home.get().structure().equals(s.id()), "the bed in today's house gives no home: " + home);
			helper.succeed();
		});
	}

	// VillageMachines

	/** Control: a chest inside a house of today's drawing counts. */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cStoreToday"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cStoreToday")
	public void aChestInATodaysDrawingHouseIsTheVillagesStore(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = todayVillage(helper, "storetoday");
			helper.assertTrue(VillageMachines.village(s.level(), abs(helper, NEW_CHEST)), "a chest inside today's house doesn't count");
			helper.succeed();
		});
	}

	// VillageFire

	/** Control: today's house's spruce wall is the builders' and a fire on it burns this house. */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cFireToday"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cFireToday")
	public void theWallsOfATodaysDrawingHouseAreTheBuildersForTheFire(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = todayVillage(helper, "firetoday");
			helper.assertTrue(VillageFire.builderMade(s.level(), abs(helper, NEW_BACK_WALL)), "today's house's back wall isn't the builders'");
			Optional<BuildSiteManager.Finished> burning = VillageFire.buildBurning(s.level(), abs(helper, NEW_BACK_WALL).above());
			helper.assertTrue(burning.isPresent() && burning.get().placement().equals(s.placement()), "a fire on today's house burns " + burning);
			helper.succeed();
		});
	}

	// BuildSiteManager.underAnotherBuild (B838)

	/** A placement of another build just behind the house (its levelled ring would reach the house). */
	static BlueprintData.Placement neighbor(GameTestHelper helper, Stage s) {
		return new BlueprintData.Placement(s.level().dimension().location(), abs(helper, new BlockPos(0, 0, 6)), Rotation.NONE, Mirror.NONE);
	}

	/** Control: the ground under today's house's back wall is kept. */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cGroundToday"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cGroundToday")
	public void theGroundUnderTheBackOfATodaysDrawingHouseIsKeptFromANeighbor(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = todayVillage(helper, "groundtoday");
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			helper.assertTrue(manager.underAnotherBuild(s.level(), neighbor(helper, s), abs(helper, NEW_BACK_FLOOR)),
				"a neighbor's levelling may dig today's house's back floor");
			helper.assertTrue(manager.underAnotherBuild(s.level(), neighbor(helper, s), abs(helper, NEW_BACK_WALL)),
				"a neighbor's levelling may dig today's house's back wall");
			helper.succeed();
		});
	}

	// The Steward's plot search

	/** The ground the Steward's plot search keeps free round builds (Plots.builds, each box grown by Plots.CLEAR). */
	static List<BoundingBox> keptFree(GameTestHelper helper, ServerLevel level) {
		try {
			Class<?> plots = Class.forName("io.github.jcondedata.aliveworkplace.city.Plots");
			Method builds = plots.getDeclaredMethod("builds", ServerLevel.class, BoundingBox.class);
			builds.setAccessible(true);
			BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(30, 17, 30)));
			List<BoundingBox> out = new ArrayList<>();
			for (Object build : (List<?>) builds.invoke(null, level, area)) {
				Method near = build.getClass().getDeclaredMethod("near");
				near.setAccessible(true);
				out.add((BoundingBox) near.invoke(build));
			}
			return out;
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("(setup) couldn't read the Steward's plot search: " + e);
		}
	}

	static boolean kept(List<BoundingBox> boxes, BlockPos pos) {
		return boxes.stream().anyMatch(b -> b.isInside(pos));
	}

	/** Control: behind today's house (back wall on row 2) rows 3 and 4 are kept free. */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cPlotToday"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cPlotToday")
	public void theStewardsPlotsKeepTwoBlocksBehindATodaysDrawingHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = todayVillage(helper, "plottoday");
			List<BoundingBox> boxes = keptFree(helper, s.level());
			for (int row = 0; row <= 4; row++) {
				helper.assertTrue(kept(boxes, abs(helper, new BlockPos(2, 0, row))), "the Steward's plot search leaves today's house's row " + row + " free");
			}
			helper.succeed();
		});
	}

	// The hall's map

	/** The hall's map (on the Map button) for the village round the hall, as the hall's screen gets it. */
	static CompoundTag hallMap(GameTestHelper helper, ServerLevel level) {
		try {
			ItemStack icon = new ItemStack(Items.FILLED_MAP);
			Method attach = HallMap.class.getDeclaredMethod("attach", ItemStack.class, ServerLevel.class, BlockPos.class);
			attach.setAccessible(true);
			attach.invoke(null, icon, level, helper.absolutePos(HALL));
			CompoundTag view = HallMap.view(icon);
			helper.assertTrue(view != null, "(setup) the Map button carries no map");
			return view;
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("(setup) couldn't draw the hall's map: " + e);
		}
	}

	/** The hall's map draws the old house at its own 5x5 outline, and its back row (row 4) is that house on the map. */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cMapOld"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cMapOld")
	public void theHallMapDrawsAnOldDrawingHouseAsItStands(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "mapold");
			CompoundTag view = hallMap(helper, s.level());
			BlockPos min = abs(helper, BlockPos.ZERO);
			BlockPos max = abs(helper, new BlockPos(4, 0, 4));
			List<String> boxes = new ArrayList<>();
			boolean found = false;
			for (int i = 0; i < HallMap.buildings(view); i++) {
				int[] b = HallMap.box(view, i);
				boxes.add(java.util.Arrays.toString(b));
				found |= b[0] == min.getX() && b[1] == min.getZ() && b[2] == max.getX() && b[3] == max.getZ();
			}
			helper.assertTrue(found, "the hall's map has no box " + min.getX() + "," + min.getZ() + " to " + max.getX() + "," + max.getZ()
				+ " for the old 5x5 house: " + boxes);
			helper.assertTrue(HallMap.buildingAt(view, max.getX(), max.getZ()) >= 0, "the old house's back corner isn't a building on the hall's map");
			helper.succeed();
		});
	}
}
