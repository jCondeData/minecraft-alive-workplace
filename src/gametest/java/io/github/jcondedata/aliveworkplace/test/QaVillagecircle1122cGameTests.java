package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.Banquets;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.WorkHorn;
import io.github.jcondedata.aliveworkplace.legend.BanquetPower;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * QA (lane C, 2026-11-22), written from the bug texts.
 *
 * <p>B555: a lookup meaning "this village's villagers" takes the village's circle, not the square box round the hall. The
 * fix's own tests put a villager deep in the square's corner (14/14 out); these press on the circle's edge itself from
 * both sides, in a 16-block village: exactly 16 out on an axis and the farthest diagonal inside (11/11) are counted,
 * while 16/1 and 11/12, a block past the circle but still well inside the square, are left out. Festival, banquet,
 * growth (who the parents are), Friendship.named and the Work Horn's count and its player-visible sentence.
 *
 * <p>B1266: {@link CityPlan#nearerAnotherHall} ignores a hall point of interest with no hall standing on it. Scaled like
 * OrphanHallPointGameTests (radius 12, one-block cells); hall A at (4,2,15), hall B 22 blocks east at (26,2,15). Here the
 * second hall is a real one first and is then broken by a player, or swapped away in its chunk without a block update
 * (as region edits do, which leaves its point behind), then put back.
 */
public class QaVillagecircle1122cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos A_AT = new BlockPos(4, 2, 15);
	private static final BlockPos B_AT = new BlockPos(26, 2, 15);
	private static final BlockPos C_AT = new BlockPos(4, 2, 29);

	// --- B555 helpers -----------------------------------------------------------------------------------------------

	/** A Village Hall at {@link #HALL} with a 16-block village; radius put back afterwards. */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			VillageHalls.forgetNearest();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	/** The edge spots inside: 16 out east and the farthest diagonal (11/11) south-east. */
	private static List<BlockPos> edgeIn(BlockPos hall) {
		int r = VillageHalls.RADIUS;
		int d = QaVillageCircleGameTests.diagonal();
		return List.of(hall.offset(r, 0, 0), hall.offset(d, 0, d));
	}

	/** One block past each edge spot: 16/1 and 11/12, still inside the square {@link VillageHalls#area}. */
	private static List<BlockPos> edgeOut(BlockPos hall) {
		int r = VillageHalls.RADIUS;
		int d = QaVillageCircleGameTests.diagonal();
		return List.of(hall.offset(r, 0, 1), hall.offset(d, 0, d + 1));
	}

	/** Villagers on the given spots, after checking the spots are on the side of the circle the test means. */
	private static List<Villager> stand(GameTestHelper helper, BlockPos hall, List<BlockPos> spots, boolean inside, String name) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < spots.size(); i++) {
			BlockPos p = spots.get(i);
			helper.assertTrue(VillageHalls.area(hall).contains(p.getCenter()), "staging: " + p.subtract(hall) + " isn't in the square");
			helper.assertTrue(VillageHalls.contains(hall, p) == inside, "staging: " + p.subtract(hall) + (inside ? " isn't" : " is") + " in the village");
			out.add(QaVillageCircleGameTests.at(helper, p, name + i));
		}
		return out;
	}

	private static void discard(List<Villager> who) {
		who.forEach(Villager::discard);
	}

	private static String names(List<Villager> who) {
		return who.stream().map(v -> v.getName().getString()).toList().toString();
	}

	// --- B555 -------------------------------------------------------------------------------------------------------

	/** The festival's feast takes the villagers on the circle's edge and leaves out those a block past it. */
	//$ gametest_ticks_batch BIG '100' '"qaVc1122cFestival"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVc1122cFestival")
	public void aFestivalTakesTheEdgeNotOnePast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> in = stand(helper, hall, edgeIn(hall), true, "Edge");
		List<Villager> out = stand(helper, hall, edgeOut(hall), false, "Past");
		Festivals.feast(level, hall);
		long today = Chronicle.day(level);
		List<Villager> missed = in.stream().filter(v -> !Long.valueOf(today).equals(ModAttachments.FESTIVAL_DAY.get(v))).toList();
		List<Villager> crashed = out.stream().filter(v -> ModAttachments.FESTIVAL_DAY.get(v) != null).toList();
		helper.assertTrue(missed.isEmpty(), "villagers on the village's edge didn't come to its festival: " + names(missed));
		helper.assertTrue(crashed.isEmpty(), "villagers a block past the village's edge came to its festival: " + names(crashed));
		discard(in);
		discard(out);
		helper.succeed();
	}

	/** A banquet's count of who came is the two on the edge, not the two a block past it. */
	//$ gametest_ticks_batch BIG '100' '"qaVc1122cBanquet"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVc1122cBanquet")
	public void aBanquetTakesTheEdgeNotOnePast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = village(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> in = stand(helper, hall, edgeIn(hall), true, "Edge");
		List<Villager> out = stand(helper, hall, edgeOut(hall), false, "Past");
		Banquets.Feast feast = Banquets.feast(level, hall, entity, new BanquetPower(7, 1, 2, 3, 3));
		helper.assertTrue(in.stream().allMatch(ModAttachments.BANQUET::has), "a villager on the village's edge didn't come to its banquet");
		helper.assertTrue(out.stream().noneMatch(ModAttachments.BANQUET::has), "a villager a block past the village's edge came to its banquet");
		helper.assertTrue(feast.came() == 2, "came to the banquet: " + feast.came() + ", not the 2 villagers on the edge");
		discard(in);
		discard(out);
		helper.succeed();
	}

	/**
	 * A birth's parents are the two grown villagers in the village nearest the free bed: with the bed out by the
	 * south-east edge, that is the one on the diagonal edge (11/11) and the one by the hall, never the one a block past
	 * the edge (11/12), though he stands nearer the bed than the one by the hall.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaVc1122cGrowth"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVc1122cGrowth")
	public void aBirthsParentsTakeTheEdgeNotOnePast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos hall = helper.absolutePos(HALL);
		int d = QaVillageCircleGameTests.diagonal();
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(5, 2, 7), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(5, 2, 7));
		chest.setItem(0, new ItemStack(Items.BREAD, 64));
		// the bed 7 out on both axes, its head to the south
		helper.setBlock(new BlockPos(18, 2, 17), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(18, 2, 18), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		VillageNeeds.Needs happy = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
		Villager edge = stand(helper, hall, List.of(hall.offset(d, 0, d)), true, "Edge").get(0);
		Villager past = stand(helper, hall, List.of(hall.offset(d, 0, d + 1)), false, "Past").get(0);
		Villager near = stand(helper, hall, List.of(hall.offset(-2, 0, -2)), true, "Near").get(0);
		BlockPos bed = helper.absolutePos(new BlockPos(18, 2, 17));
		helper.assertTrue(past.distanceToSqr(bed.getCenter()) < near.distanceToSqr(bed.getCenter()),
			"staging: the villager past the edge isn't nearer the bed than the one by the hall");
		helper.assertTrue(VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.NONE,
			"staging: the village can't grow: " + VillageGrowth.blocker(level, hall, happy, 0));
		Villager baby = VillageGrowth.grow(level, hall, happy, 0);
		helper.assertTrue(baby != null && baby.isBaby(), "the villager on the edge and the one by the hall had no baby");
		Families.Parents parents = Families.parents(baby);
		helper.assertTrue(parents != null, "the baby has no parents noted");
		List<UUID> ids = new ArrayList<>();
		ids.add(parents.motherId());
		ids.add(parents.fatherId());
		helper.assertTrue(!ids.contains(past.getUUID()), "a villager a block past the village's edge was a parent of its baby: " + ids);
		helper.assertTrue(ids.contains(edge.getUUID()) && ids.contains(near.getUUID()),
			"the parents aren't the villager on the edge and the one by the hall: " + parents.mother().getString() + ", " + parents.father().getString());
		baby.discard();
		discard(List.of(edge, past, near));
		helper.succeed();
	}

	/** A friend asked for by name is found on the circle's edge (axis and diagonal), not a block past it. */
	//$ gametest_ticks_batch BIG '100' '"qaVc1122cFriend"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVc1122cFriend")
	public void aFriendByNameTakesTheEdgeNotOnePast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> in = stand(helper, hall, edgeIn(hall), true, "Edge");
		List<Villager> out = stand(helper, hall, edgeOut(hall), false, "Past");
		for (Villager v : in) {
			helper.assertTrue(Friendship.named(level, hall, v.getName().getString()) == v, v.getName().getString() + " on the village's edge wasn't found by name");
		}
		for (Villager v : out) {
			helper.assertTrue(Friendship.named(level, hall, v.getName().getString()) == null,
				v.getName().getString() + ", a block past the village's edge, was found as one of its people");
		}
		discard(in);
		discard(out);
		helper.succeed();
	}

	/**
	 * A player blows the Work Horn by the hall: the two on the edge and the one by the hall are worn out by the rush and
	 * the sentence says 3 villagers, not 5; the two a block past the edge aren't worn out.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaVc1122cHorn"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVc1122cHorn")
	public void theWorkHornCountsTheEdgeNotOnePast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> in = new ArrayList<>(stand(helper, hall, edgeIn(hall), true, "Edge"));
		in.addAll(stand(helper, hall, List.of(hall.offset(2, 0, -2)), true, "Near"));
		List<Villager> out = stand(helper, hall, edgeOut(hall), false, "Past");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(hall.getX() - 1.5, hall.getY(), hall.getZ() + 0.5);
		WorkHorn.Result result = WorkHorn.blow(level, player, player.blockPosition());
		helper.assertTrue(result.outcome() == WorkHorn.Outcome.RUSH, "no rush: " + result.outcome());
		String said = result.message() == null ? "" : result.message().getString();
		String name = VillageHalls.name(level, hall).getString();
		helper.assertTrue(said.equals(name + " answers the horn: 3 villagers rush to work!"), "the horn said: " + said);
		helper.assertTrue(in.stream().allMatch(ModAttachments.WORN_OUT::has), "a villager in the village wasn't worn out by the rush");
		helper.assertTrue(out.stream().noneMatch(ModAttachments.WORN_OUT::has), "a villager a block past the village's edge was worn out by its rush");
		discard(in);
		discard(out);
		helper.succeed();
	}

	// --- B1266 ------------------------------------------------------------------------------------------------------

	/**
	 * A real hall B refuses cells nearer it; a player breaks it, and the same cells, painted again, go on and count for
	 * village A (Reach.claims); hall B set down again refuses the next stroke there.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaVc1122cBrokenHall"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVc1122cBrokenHall")
	public void aBrokenHallsCellsGoToTheRemainingVillage(GameTestHelper helper) {
		ServerLevel level = alone(helper);
		VillageHallBlockEntity a = hall(helper, A_AT);
		BlockPos hall = a.getBlockPos();
		hall(helper, B_AT);
		BlockPos b = helper.absolutePos(B_AT);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no zone added");
		BitSet stroke = cells(hall, 13, 15, -1, 1);
		BlockPos middle = hall.offset(14, 0, 0);
		helper.assertTrue(CityPlan.nearerAnotherHall(level, hall, CityPlan.cellAt(hall, middle)), "staging: a cell nearer the real hall B isn't another village's");
		helper.assertFalse(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, stroke))), "a stroke nearer a real hall B was painted");

		player.gameMode.destroyBlock(b);
		helper.assertTrue(!(level.getBlockState(b).getBlock() instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlock), "staging: the player didn't break hall B");
		helper.assertFalse(CityPlan.nearerAnotherHall(level, hall, CityPlan.cellAt(hall, middle)), "a cell by a broken hall still counts as another village's");
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, stroke))), "the stroke by a broken hall was refused");
		helper.assertTrue(a.plan().zones().get(0).cells().equals(stroke),
			"by a broken hall the plan painted " + a.plan().zones().get(0).cells().cardinality() + " of " + stroke.cardinality() + " cells");
		helper.assertTrue(Reach.claims(level, hall, middle), "a cell painted by a broken hall isn't village A's");

		helper.setBlock(B_AT, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertTrue(CityPlan.nearerAnotherHall(level, hall, CityPlan.cellAt(hall, hall.offset(14, 0, 3))), "hall B set down again isn't another village");
		helper.assertFalse(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, cells(hall, 13, 15, 2, 4)))),
			"a stroke nearer hall B, set down again, was painted");
		helper.assertFalse(Reach.claims(level, hall, middle), "a cell nearer hall B, set down again, still counts for village A");
		helper.succeed();
	}

	/**
	 * Hall B's block is swapped away in its chunk with no block update, as a region edit does: its point of interest
	 * stays. Cells nearer it paint and count for village A, while a real hall C still refuses cells nearer it; swapping
	 * hall B's block back the same way makes it a village again.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaVc1122cSwappedHall"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVc1122cSwappedHall")
	public void aHallSwappedAwayLeavesItsPointButNoVillage(GameTestHelper helper) {
		ServerLevel level = alone(helper);
		VillageHallBlockEntity a = hall(helper, A_AT);
		BlockPos hall = a.getBlockPos();
		hall(helper, B_AT);
		hall(helper, C_AT);
		BlockPos b = helper.absolutePos(B_AT);
		BlockState hallState = level.getBlockState(b);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no zone added");

		LevelChunk chunk = level.getChunkAt(b);
		chunk.setBlockState(b, Blocks.AIR.defaultBlockState(), false);
		VillageHalls.forgetNearest();
		helper.assertTrue(level.getBlockState(b).isAir(), "staging: hall B's block wasn't swapped away");
		helper.assertTrue(level.getPoiManager().getType(b).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent(),
			"staging: the swap took hall B's point of interest with it (no orphan to test)");

		BitSet stroke = cells(hall, 13, 15, -1, 1);
		BlockPos middle = hall.offset(14, 0, 0);
		helper.assertFalse(CityPlan.nearerAnotherHall(level, hall, CityPlan.cellAt(hall, middle)), "a cell nearer a hall point with its hall swapped away counts as another village's");
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, stroke))), "the stroke by the swapped-away hall was refused whole");
		helper.assertTrue(a.plan().zones().get(0).cells().equals(stroke),
			"by the swapped-away hall the plan painted " + a.plan().zones().get(0).cells().cardinality() + " of " + stroke.cardinality() + " cells");
		helper.assertTrue(Reach.claims(level, hall, middle), "a cell painted by the swapped-away hall isn't village A's");

		// the real hall C, 14 south of A: a cell 12 south of A is 2 from C, still another village's
		BitSet toC = cells(hall, -1, 1, 12, 13);
		helper.assertTrue(CityPlan.nearerAnotherHall(level, hall, CityPlan.cellAt(hall, hall.offset(0, 0, 12))), "a cell nearer the real hall C isn't another village's");
		helper.assertFalse(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, toC))), "a stroke nearer the real hall C was painted");
		helper.assertTrue(a.plan().zones().get(0).cells().equals(stroke), "a stroke nearer the real hall C added cells to village A's zone");

		chunk.setBlockState(b, hallState, false);
		VillageHalls.forgetNearest();
		helper.assertTrue(VillageHalls.standsAt(level, b), "staging: hall B's block wasn't swapped back");
		helper.assertTrue(CityPlan.nearerAnotherHall(level, hall, CityPlan.cellAt(hall, middle)), "hall B swapped back isn't another village");
		helper.assertFalse(Reach.claims(level, hall, middle), "a cell nearer hall B, swapped back, still counts for village A");
		helper.succeed();
	}

	// --- B1266 helpers (as OrphanHallPointGameTests) -----------------------------------------------------------------

	/** Radius 12, nothing left by other batches; every hall point within 64 blocks removed; put back after. */
	private static ServerLevel alone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 12;
		BlockPos centre = helper.absolutePos(new BlockPos(15, 2, 15));
		removeHallPoints(level, centre);
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			removeHallPoints(level, centre);
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		return level;
	}

	private static void removeHallPoints(ServerLevel level, BlockPos centre) {
		for (BlockPos old : level.getPoiManager().findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> true, centre, 64, PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(old, false);
			if (level.getPoiManager().getType(old).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent()) {
				level.getPoiManager().remove(old);
			}
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertTrue(CityPlan.half() == 16, "the plan's grid reaches " + CityPlan.half() + " blocks from the hall, not 16");
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setPlan(CityPlan.EMPTY);
		return entity;
	}

	private static BitSet cells(BlockPos hall, int dx0, int dx1, int dz0, int dz1) {
		BitSet out = new BitSet();
		for (int dx = dx0; dx <= dx1; dx++) {
			for (int dz = dz0; dz <= dz1; dz++) {
				int cell = CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
				if (cell < 0) {
					throw new IllegalStateException("offset " + dx + "," + dz + " is off the plan's grid");
				}
				out.set(cell);
			}
		}
		return out;
	}
}
