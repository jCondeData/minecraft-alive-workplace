package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-15, adversarial, from B805's spec and docs/wiki/village-hall.md, not the code): "A painted cell whose
 * middle is nearer another hall (one placed after it was painted) is that hall's village's only: a Stone Wall there makes
 * the nearer village walled, not both", the same rule painting already enforces ("N cells are nearer another village:
 * left out"). Checked through what a player sees: whether each village is walled, its rank's building count, the
 * farmer's Field Marker refusal, and the painting line; on both sides of the boundary (one block nearer each hall and a
 * tie), with hall B broken and put back, and over a save and reload.
 *
 * <p>Every test runs alone in its batch in a huge area, clears entities, finished-building records and every Village Hall
 * an earlier batch left within a village's reach first (a leftover hall nearer a cell is exactly what this rule reacts to),
 * then works at a radius of 12, where a City Plan cell is one block.
 */
public class QaZoneNearerHall1115bGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos A = new BlockPos(1, 2, 15);
	/** The painted cell, 14 east of A: past A's radius of 12, on A's grid (half 16). */
	private static final int CELL_DX = 14;

	/** The two villages of a test: their halls (absolute) and the painted cell's middle. */
	record Pair(ServerLevel level, BlockPos a, BlockPos cell) {
	}

	/** Clears the spot, sets the radius to 12, places hall A and paints a Homes zone on the one cell 14 east of it. */
	private static Pair setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 12;
		helper.setBlock(A, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos a = helper.absolutePos(A);
		BlockPos cell = a.east(CELL_DX);
		helper.assertTrue(CityPlan.cellSize() == 1, "the setup: a cell isn't one block at radius 12 but " + CityPlan.cellSize());
		VillageHallBlockEntity entity = helper.getBlockEntity(A);
		BitSet cells = new BitSet();
		cells.set(CityPlan.cellAt(a, cell));
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		helper.assertTrue(Reach.claims(level, a, cell), "the setup: A's painted cell isn't A's before any other hall stands");
		return new Pair(level, a, cell);
	}

	private static BlockPos hall(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		return helper.absolutePos(rel);
	}

	private static void breakHall(GameTestHelper helper, BlockPos abs) {
		helper.getLevel().destroyBlock(abs, false);
		VillageHalls.forgetNearest();
	}

	/**
	 * Records a finished Stone Wall whose middle is {@code middle}, turned so its corner lies east of the middle (off A's
	 * grid, away from A), and forgets it when the test ends. Returns its placement.
	 */
	private static BlockPos wall(GameTestHelper helper, BlockPos middle, Rotation turn, String name) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = StarterBlueprints.STONE_WALL.id();
		BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, turn, Mirror.NONE));
		BlockPos origin = middle.offset(middle.getX() - first.getX(), 0, middle.getZ() - first.getZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, turn, Mirror.NONE);
		BlockPos spot = Builders.spot(level, id, placement);
		helper.assertTrue(spot.getX() == middle.getX() && spot.getZ() == middle.getZ(), "the setup: the wall's middle is " + spot + ", not " + middle);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.nameUUIDFromBytes(("qa1115b-" + name).getBytes(StandardCharsets.UTF_8)));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return origin;
	}

	private static int buildings(ServerLevel level, BlockPos hall) {
		return VillageRanks.score(level, hall, 0).buildings();
	}

	/** A Stone Wall finished in A's painted cell, nearer hall B (and inside B's radius), walls B and counts for B's rank only. */
	//$ gametest_ticks_batch AREA '100' '"qa1115bZoneOnlyB"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1115bZoneOnlyB")
	public void aWallInACellNearerALaterHallCountsForThatVillageOnly(GameTestHelper helper) {
		Pair p = setUp(helper);
		BlockPos b = hall(helper, A.east(CELL_DX + 9));
		int aBefore = buildings(p.level(), p.a());
		int bBefore = buildings(p.level(), b);
		BlockPos origin = wall(helper, p.cell(), Rotation.CLOCKWISE_180, "onlyB");
		helper.assertFalse(Reach.claims(p.level(), p.a(), origin), "the setup: the wall's corner " + origin + " is in village A");

		helper.assertFalse(Reach.claims(p.level(), p.a(), p.cell()), "A's painted cell, 9 from hall B and 14 from A, is still A's");
		helper.assertTrue(Reach.claims(p.level(), b, p.cell()), "the cell inside B's radius isn't B's");
		helper.assertTrue(Sieges.walled(p.level(), b), "the wall 9 blocks from hall B doesn't make village B walled");
		helper.assertFalse(Sieges.walled(p.level(), p.a()), "the wall in the cell nearer hall B also makes village A walled");
		helper.assertTrue(buildings(p.level(), b) == bBefore + 1, "B's rank buildings " + bBefore + " -> " + buildings(p.level(), b) + ", expected +1");
		helper.assertTrue(buildings(p.level(), p.a()) == aBefore, "A's rank buildings " + aBefore + " -> " + buildings(p.level(), p.a()) + ", expected no change");
		helper.succeed();
	}

	/**
	 * The boundary from both sides, by the cell's middle: B one block nearer (13 against A's 14) takes the cell from A; a tie
	 * (14 and 14) and B one block farther (15) leave it A's, and the wall there walls A. B stands past its own radius each
	 * time, so only A's claim is in question.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1115bZoneBoundary"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1115bZoneBoundary")
	public void theCellGoesToTheStrictlyNearerHallOneBlockEitherWay(GameTestHelper helper) {
		Pair p = setUp(helper);
		wall(helper, p.cell(), Rotation.CLOCKWISE_180, "boundary");
		helper.assertTrue(Sieges.walled(p.level(), p.a()), "the setup: the wall in A's own painted cell doesn't wall A");
		int[] gaps = {13, 14, 15};
		boolean[] aKeeps = {false, true, true};
		for (int i = 0; i < gaps.length; i++) {
			BlockPos b = hall(helper, A.east(CELL_DX + gaps[i]));
			String where = "with hall B " + gaps[i] + " from the cell (A 14)";
			helper.assertTrue(Reach.claims(p.level(), p.a(), p.cell()) == aKeeps[i], where + ": A claims the cell = " + !aKeeps[i]);
			helper.assertTrue(Sieges.walled(p.level(), p.a()) == aKeeps[i], where + ": village A walled = " + !aKeeps[i]);
			breakHall(helper, b);
		}
		helper.assertTrue(Sieges.walled(p.level(), p.a()), "with hall B gone the wall in A's cell no longer walls A");
		helper.succeed();
	}

	/** The same boundary south of the cell, B in another direction: 13 blocks south takes it, 15 doesn't. */
	//$ gametest_ticks_batch AREA '100' '"qa1115bZoneBoundarySouth"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1115bZoneBoundarySouth")
	public void theBoundaryHoldsForAHallOffToTheSide(GameTestHelper helper) {
		Pair p = setUp(helper);
		// the cell is at (15, 2, 15) relative: 13 south of it is z = 28, 15 north is z = 0
		BlockPos south = hall(helper, A.east(CELL_DX).south(13));
		helper.assertFalse(Reach.claims(p.level(), p.a(), p.cell()), "hall B 13 south of the cell (A 14 west) left it A's");
		breakHall(helper, south);
		BlockPos north = hall(helper, A.east(CELL_DX).north(15));
		helper.assertTrue(Reach.claims(p.level(), p.a(), p.cell()), "hall B 15 north of the cell (A 14 west) took it from A");
		breakHall(helper, north);
		// diagonal: 9, 10 is 13.45 (nearer), 10, 11 is 14.87 (farther)
		BlockPos near = hall(helper, A.east(CELL_DX + 9).south(10));
		helper.assertTrue(Math.sqrt(near.distSqr(p.cell())) < 14, "the setup: " + Math.sqrt(near.distSqr(p.cell())));
		helper.assertFalse(Reach.claims(p.level(), p.a(), p.cell()), "hall B 13.5 blocks off diagonally left the cell A's");
		breakHall(helper, near);
		BlockPos far = hall(helper, A.east(CELL_DX + 10).north(11));
		helper.assertTrue(Math.sqrt(far.distSqr(p.cell())) > 14, "the setup: " + Math.sqrt(far.distSqr(p.cell())));
		helper.assertTrue(Reach.claims(p.level(), p.a(), p.cell()), "hall B 14.9 blocks off diagonally took the cell from A");
		helper.succeed();
	}

	/** Hall B broken: the cell is A's again (A walled, B not, the rank count moves back); B put back takes it again. */
	//$ gametest_ticks_batch AREA '100' '"qa1115bZoneBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1115bZoneBroken")
	public void breakingTheNearerHallGivesTheCellBack(GameTestHelper helper) {
		Pair p = setUp(helper);
		BlockPos bRel = A.east(CELL_DX + 9);
		BlockPos b = hall(helper, bRel);
		wall(helper, p.cell(), Rotation.CLOCKWISE_180, "broken");
		int aWith = buildings(p.level(), p.a());
		helper.assertFalse(Sieges.walled(p.level(), p.a()), "the setup: A walled while B stands nearer");
		breakHall(helper, b);
		helper.assertTrue(p.level().getBlockState(b).isAir(), "the setup: hall B wasn't broken");
		helper.assertTrue(Sieges.walled(p.level(), p.a()), "with hall B broken, the wall in A's painted cell doesn't wall A");
		helper.assertTrue(buildings(p.level(), p.a()) == aWith + 1, "with hall B broken A's rank buildings " + aWith + " -> " + buildings(p.level(), p.a()));
		hall(helper, bRel);
		helper.assertFalse(Sieges.walled(p.level(), p.a()), "with hall B put back, A is still walled by the cell nearer B");
		helper.assertTrue(Sieges.walled(p.level(), b), "with hall B put back, B isn't walled");
		helper.assertTrue(buildings(p.level(), p.a()) == aWith, "with hall B put back A's rank buildings " + buildings(p.level(), p.a()) + ", expected " + aWith);
		helper.succeed();
	}

	/** Both halls and the finished buildings saved and loaded: the cell is still B's only, and A's plan still holds it. */
	//$ gametest_ticks_batch AREA '100' '"qa1115bZoneReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1115bZoneReload")
	public void theAnswerSurvivesASaveAndReload(GameTestHelper helper) {
		Pair p = setUp(helper);
		BlockPos bRel = A.east(CELL_DX + 9);
		BlockPos b = hall(helper, bRel);
		wall(helper, p.cell(), Rotation.CLOCKWISE_180, "reload");
		ServerLevel level = p.level();
		HolderLookup.Provider registries = level.registryAccess();
		VillageHallBlockEntity aHall = helper.getBlockEntity(A);
		VillageHallBlockEntity bHall = helper.getBlockEntity(bRel);
		CompoundTag aTag = aHall.saveWithFullMetadata(registries);
		CompoundTag bTag = bHall.saveWithFullMetadata(registries);
		aHall.loadWithComponents(aTag, registries);
		bHall.loadWithComponents(bTag, registries);
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		VillageHalls.forgetNearest();
		helper.assertTrue(aHall.plan().zoneAt(p.a(), p.cell()).isPresent(), "A's painted cell was lost from its plan on reload");
		helper.assertFalse(Reach.claims(level, p.a(), p.cell()), "after a reload the cell nearer B is A's again");
		helper.assertTrue(Sieges.walled(level, b), "after a reload B isn't walled");
		helper.assertFalse(Sieges.walled(level, p.a()), "after a reload A is walled by the cell nearer B");
		helper.succeed();
	}

	/**
	 * A's farmer, through the Field Marker: a field centred on A's painted cell is taken while A stands alone; once hall B
	 * stands nearer the cell it is refused, the line naming A, and once B is broken it is taken again. The cell is 18.4
	 * blocks from the composter, past a long reach of 16.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1115bZoneFarmer"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1115bZoneFarmer")
	public void aFarmersFieldInTheCellFollowsTheNearerHall(GameTestHelper helper) {
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		QaVillageReachGameTests.setUp(helper);
		QaVillageReachGameTests.radius(12);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		QaVillageReachGameTests.Kit kit = QaVillageReachGameTests.kit(helper, "farmer", seen);
		int reach = Fields.MAX_DISTANCE;
		Leftovers.after(helper, () -> Fields.MAX_DISTANCE = reach);
		kit.setReach().accept(16);
		// the kit's hall at (1, 2, 1), its composter at (3, 2, 3); the cell 15, 15 from the hall (21.2 off, past 12)
		BlockPos aRel = new BlockPos(1, 2, 1);
		BlockPos a = helper.absolutePos(aRel);
		BlockPos work = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos cell = a.offset(15, 0, 15);
		VillageHallBlockEntity entity = helper.getBlockEntity(aRel);
		BitSet cells = new BitSet();
		cells.set(CityPlan.cellAt(a, cell));
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		String village = VillageHalls.name(level, a).getString();
		Vec3i off = cell.subtract(work);
		QaVillageReachGameTests.taken(helper, kit, seen, off, "a field in A's painted cell with A alone");

		BlockPos b = hall(helper, new BlockPos(24, 2, 24)); // 11.3 from the cell, nearer than A's 21.2
		// 13, 13 across is 18.4, which the line rounds up to 19, as it reads 128.7 as 129 (QaVillageReachGameTests.boundaries)
		String line = "That field is outside " + village + " and 19 blocks from the Composter the farmer works at — keep it within 16.";
		QaVillageReachGameTests.refused(helper, kit, seen, off, "a field in A's painted cell nearer hall B", l -> l.equals(line), "\"" + line + "\"");
		breakHall(helper, b);
		QaVillageReachGameTests.taken(helper, kit, seen, off, "a field in A's painted cell after hall B was broken");
		helper.succeed();
	}

	/**
	 * A player painting three cells: one nearer A, one a tie and one nearer B. The cell nearer B is left out with "1 cell
	 * is nearer another village: left out"; the tie is painted (not nearer), the same rule as the claim.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1115bZonePaint"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1115bZonePaint")
	public void paintingLeavesOutOnlyTheCellNearerAnotherHallAndSaysSo(GameTestHelper helper) {
		Pair p = setUp(helper);
		ServerLevel level = p.level();
		// B 28 east of A: cells 13, 14 and 15 east of A are 15, 14 and 13 from B
		hall(helper, A.east(28));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.CREATIVE);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		int nearA = CityPlan.cellAt(p.a(), p.a().east(13));
		int tie = CityPlan.cellAt(p.a(), p.a().east(14));
		int nearB = CityPlan.cellAt(p.a(), p.a().east(15));
		BitSet stroke = new BitSet();
		stroke.set(nearA);
		stroke.set(tie);
		stroke.set(nearB);
		VillageHallBlockEntity entity = helper.getBlockEntity(A);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", ""));
		seen.clear();
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(p.a(), 0, stroke))), "the stroke was refused whole");
		BitSet painted = entity.plan().zones().get(0).cells();
		helper.assertTrue(painted.get(nearA) && painted.get(tie) && !painted.get(nearB) && painted.cardinality() == 2,
			"painted " + painted + "; expected the cells 13 and 14 east of A (" + nearA + ", " + tie + ") and not 15 (" + nearB + ")");
		List<String> lines = seen.stream().map(Component::getString).toList();
		helper.assertTrue(lines.contains("1 cell is nearer another village: left out"), "the painter read " + lines);
		helper.succeed();
	}
}
