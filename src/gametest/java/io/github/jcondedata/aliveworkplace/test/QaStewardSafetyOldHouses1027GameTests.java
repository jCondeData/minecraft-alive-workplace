package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-10-27) for ROADMAP 27.19 and 27.20, written from the spec, filling what the earlier QA classes left: the
 * ledger's section edges along y and z for a plan (and the owner's approval by hand lifting only the ledger), a single
 * Keep Clear cell inside a plan at the default hall radius, an old house reaching the renew zone's last column (and one
 * column more), and a fisherman's house whose job block is a container (an empty barrel). The village is
 * {@link QaOldHousesGameTests#village}'s; each test runs alone in its batch.
 */
public class QaStewardSafetyOldHouses1027GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A player's block marks its section and nothing past its edges: a plan wholly below that section, or wholly south of
	 * it, is allowed; the same plan one block taller, or one block further north, reaches in and is refused; approved by
	 * the owner by hand, it is allowed (the ledger is the only refusal the owner lifts).
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1027LedgerEdges"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1027LedgerEdges")
	public void aPlanAtASectionsEdgeIsAllowedAndOneBlockInIsNot(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		BlockPos at = helper.absolutePos(new BlockPos(16, 2, 12));
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), at, Blocks.COBBLESTONE), "setup: the player's block wasn't placed");
		helper.assertTrue(PlayerBuilt.get(v.level()).marked(at), "setup: the player's block didn't mark its section");
		int sMinY = at.getY() & ~15;
		int sMaxZ = (at.getZ() & ~15) + 15;
		int x0 = at.getX() - 1;
		int x1 = at.getX() + 1;
		// along y: the plan's top on the last block of the section below, then one block into the marked one
		BoundingBox below = new BoundingBox(x0, sMinY - 4, at.getZ() - 1, x1, sMinY - 1, at.getZ() + 1);
		BoundingBox belowIn = new BoundingBox(x0, sMinY - 4, at.getZ() - 1, x1, sMinY, at.getZ() + 1);
		// along z: the plan's north side on the first block of the section south, then one block back into the marked one
		BoundingBox south = new BoundingBox(x0, at.getY(), sMaxZ + 1, x1, at.getY() + 3, sMaxZ + 4);
		BoundingBox southIn = new BoundingBox(x0, at.getY(), sMaxZ, x1, at.getY() + 3, sMaxZ + 4);
		for (BoundingBox ok : List.of(below, south)) {
			Optional<StewardSafety.Refusal> r = StewardSafety.check(v.level(), v.hall(), ok, false);
			helper.assertTrue(r.isEmpty(), "a plan in an untouched section next to a marked one is refused (" + r + "): " + ok);
		}
		for (BoundingBox in : List.of(belowIn, southIn)) {
			Optional<StewardSafety.Refusal> r = StewardSafety.check(v.level(), v.hall(), in, false);
			helper.assertTrue(r.equals(Optional.of(StewardSafety.Refusal.PLAYER_BUILT)),
				"a plan reaching one block into the marked section isn't refused for the ledger (" + r + "): " + in);
			Optional<StewardSafety.Refusal> byHand = StewardSafety.check(v.level(), v.hall(), in, true);
			helper.assertTrue(byHand.isEmpty(), "the owner's own approval doesn't lift the ledger (" + byHand + "): " + in);
		}
		helper.succeed();
	}

	/**
	 * Never in Keep Clear: one Keep Clear cell of the plan (at the default hall radius, 4×4 blocks), lying inside a 9×9 plan
	 * and touching none of its corners or its middle, still refuses it, by hand or not.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1027KeepClearCell"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1027KeepClearCell")
	public void oneKeepClearCellInsideAPlanRefusesIt(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		helper.assertTrue(VillageHalls.RADIUS == 64 && CityPlan.cellSize() == 4, "setup: not the default radius: " + VillageHalls.RADIUS);
		keepClearCellInsideAPlan(helper, v);
		helper.succeed();
	}

	/** Paints one Keep Clear cell round helper (14, 2, 14) and checks a 9×9 plan holding it (from one block west and north of it) is refused. */
	static void keepClearCellInsideAPlan(GameTestHelper helper, QaOldHousesGameTests.Village v) {
		BlockPos p = helper.absolutePos(new BlockPos(14, 2, 14));
		int cell = CityPlan.cellAt(v.hall(), p);
		int minX = p.getX();
		while (CityPlan.cellAt(v.hall(), new BlockPos(minX - 1, p.getY(), p.getZ())) == cell) {
			minX--;
		}
		int minZ = p.getZ();
		while (CityPlan.cellAt(v.hall(), new BlockPos(p.getX(), p.getY(), minZ - 1)) == cell) {
			minZ--;
		}
		BitSet cells = new BitSet();
		cells.set(cell);
		CityPlan plan = v.entity().plan().addZone(StewardSafety.KEEP_CLEAR, "Keep Clear", "");
		helper.assertTrue(plan != null, "setup: no Keep Clear zone");
		v.entity().setPlan(plan.paint(plan.zones().size() - 1, cells));
		BlockPos cellCorner = new BlockPos(minX, p.getY(), minZ);
		helper.assertTrue(v.entity().plan().zoneAt(v.hall(), cellCorner).map(z -> StewardSafety.KEEP_CLEAR.equals(z.kind())).orElse(false),
			"setup: the cell isn't Keep Clear: " + v.entity().plan().zoneAt(v.hall(), cellCorner));
		BoundingBox box = new BoundingBox(minX - 1, p.getY(), minZ - 1, minX + 7, p.getY() + 4, minZ + 7);
		for (boolean byHand : new boolean[] {false, true}) {
			Optional<StewardSafety.Refusal> r = StewardSafety.check(v.level(), v.hall(), box, byHand);
			helper.assertTrue(r.equals(Optional.of(StewardSafety.Refusal.KEEP_CLEAR)), "a plan " + box + " holding the Keep Clear cell at "
				+ cellCorner.toShortString() + " (" + CityPlan.cellSize() + " blocks wide) is " + (r.isEmpty() ? "allowed" : "refused for " + r)
				+ (byHand ? " (approved by hand)" : ""));
		}
	}

	/**
	 * Wholly inside the renew zone, from both sides: a house whose east wall stands on the zone's last column is renewable,
	 * and the same house one column longer, its east wall just outside the zone, is not (and not listed as renewable).
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1027ZoneEdge"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1027ZoneEdge")
	public void aHouseOnTheZonesLastColumnIsRenewableAndOneColumnMoreIsNot(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		BitSet cells = new BitSet();
		for (int x = 6; x <= 17; x++) {
			for (int z = 0; z <= 29; z++) {
				cells.set(CityPlan.cellAt(v.hall(), helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
		v.entity().setPlan(plan.editZone(0, "homes", "Homes 1", "", true));
		int edge = -1;
		for (int x = 6; x <= 29; x++) {
			int fx = x;
			boolean all = true;
			for (int z : new int[] {2, 3, 4, 14, 15, 16}) {
				all &= v.entity().plan().zoneAt(v.hall(), helper.absolutePos(new BlockPos(fx, 2, z))).map(CityPlan.Zone::renew).orElse(false);
			}
			if (all) {
				edge = x;
			} else if (edge >= 0) {
				break;
			}
		}
		helper.assertTrue(edge >= 10 && edge <= 27, "setup: the zone's last column is at " + edge);
		// A: x from edge-4 to edge; B: x from edge-4 to edge+1
		house(helper, new BlockPos(edge - 4, 2, 2), 5);
		house(helper, new BlockPos(edge - 4, 2, 14), 6);
		int last = edge;
		QaOldHousesGameTests.surveyThen(helper, v, houses -> {
			BlockPos aCorner = helper.absolutePos(new BlockPos(last, 2, 4));
			OldHouses.House a = houses.stream().filter(h -> h.box().isInside(aCorner)).findFirst().orElse(null);
			helper.assertTrue(a != null, "the house on the zone's last column isn't found: " + houses);
			BoundingBox want = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(last - 4, 2, 2)), helper.absolutePos(new BlockPos(last, 3, 4)));
			helper.assertTrue(a.box().equals(want), "measured " + a.box() + ", not " + want);
			helper.assertTrue(a.renewable(), "a house wholly inside the zone, up to its last column, counted as " + a.verdict() + QaOldHousesGameTests.why(v, a));
			BlockPos bOut = helper.absolutePos(new BlockPos(last + 1, 2, 15));
			OldHouses.House b = houses.stream().filter(h -> h.box().isInside(bOut)).findFirst().orElse(null);
			helper.assertTrue(b == null || b.verdict() == OldHouses.Verdict.OUTSIDE_ZONE,
				"a house one column past the zone's edge counted as " + (b == null ? "-" : b.verdict()));
			helper.assertTrue(houses.stream().filter(OldHouses.House::renewable).count() == 1, "renewable: " + houses);
		});
	}

	/** A floor of oak planks {@code w} along x and 3 along z from {@code corner}, a red bed on it facing east. */
	private static void house(GameTestHelper helper, BlockPos corner, int w) {
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(corner.offset(x, 0, z), Blocks.OAK_PLANKS);
			}
		}
		bed(helper, corner.offset(1, 1, 1), Direction.EAST);
	}

	private static void bed(GameTestHelper helper, BlockPos foot, Direction facing) {
		BlockState state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
		helper.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.relative(facing), state.setValue(BedBlock.PART, BedPart.HEAD));
	}

	/**
	 * Job blocks may be held, containers or not: a fisherman's house (a bed and an empty barrel, his job block, with a
	 * composter beside it) is renewable; the same house with a chest instead of the barrel is not.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1027Barrel"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1027Barrel")
	public void aFishermansHouseWithItsBarrelIsRenewableAndWithAChestIsNot(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		BlockPos a = new BlockPos(12, 2, 3);
		BlockPos b = new BlockPos(12, 2, 16);
		for (BlockPos corner : List.of(a, b)) {
			house(helper, corner, 6);
			helper.setBlock(corner.offset(5, 1, 2), Blocks.COMPOSTER);
		}
		helper.setBlock(a.offset(4, 1, 1), Blocks.BARREL);
		helper.setBlock(b.offset(4, 1, 1), Blocks.CHEST);
		QaOldHousesGameTests.surveyThen(helper, v, houses -> {
			BlockPos inA = helper.absolutePos(a.offset(4, 1, 1));
			OldHouses.House fisher = houses.stream().filter(h -> h.box().isInside(inA)).findFirst().orElse(null);
			helper.assertTrue(fisher != null && fisher.blocks() == 18 + 2 + 2, "the fisherman's house measured as " + fisher + " in " + houses);
			helper.assertTrue(fisher.renewable(), "a house holding its job block (a barrel) counted as " + fisher.verdict() + QaOldHousesGameTests.why(v, fisher));
			BlockPos inB = helper.absolutePos(b.offset(4, 1, 1));
			OldHouses.House chest = houses.stream().filter(h -> h.box().isInside(inB)).findFirst().orElse(null);
			helper.assertTrue(chest == null || chest.verdict() == OldHouses.Verdict.CONTAINER, "a house holding a chest counted as " + (chest == null ? "-" : chest.verdict()));
		});
	}
}
