package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.move.MoveFit;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA (lane c, 2026-11-15, pass 3): adversarial checks of six landed fixes, written from their ROADMAP text and the wiki
 * (moving-buildings.md, village-hall.md), not the code.
 * <ul>
 * <li>B1259: the Mover keeps his crate as his job site whatever workstation stands where it "would have moved" (a lectern,
 * not only a barrel), through a save and reload mid-move, and that workstation is left free for its own villager.</li>
 * <li>B1264: a move called off before anything went up at its new spot S never keeps S (not even for another move's new
 * spot); called off once the first block stood at S it does, until the last of it is down.</li>
 * <li>B1274: every count of snow layers (1-7) on a crate is that many snow and never a snowball, also through a full move
 * (8 layers: QaVillageMoverFixes1115c3BugTests, unregistered).</li>
 * <li>B1275: every tall plant with either half on a crate is always its one item (64 tries each: tall grass, large fern,
 * rose bush, sunflower, lilac, peony, pitcher plant), also through a full move.</li>
 * <li>B1270: a nearer hall on higher OR lower ground takes the cell (walls, rank, painting line); the tie and one block
 * either side with a height difference; a diagonal offset.</li>
 * <li>B1271: per village-hall.md, a wall whose middle is nearer hall B is B's only with its corner inside A's radius, off
 * A's grid, and with B on higher ground; the corner still counts with no nearer hall, at a tie, and one block either way;
 * a middle inside A's own radius still counts for A.</li>
 * </ul>
 * Zone tests work at radius 12 (cells of one block), alone in their batch with leftover halls cleared, like
 * QaZoneNearerHall1115bGameTests.
 */
public class QaVillageMoverFixes1115c3GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	/** The chest's spot in MoverCrateNoRoomB1234GameTests' house: lands on his crate. */
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);

	// ---- B1259: his crate stays his job site -----------------------------------------------------------------------

	/** Whether the workstation at {@code pos} is free (unclaimed) for its own villager. */
	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getInRange(t -> true, pos, 0, PoiManager.Occupancy.HAS_SPACE).anyMatch(r -> r.getPos().equals(pos));
	}

	/** The house moves 2 south-east, {@code state} is put where his crate would have gone, optionally a reload; his job stays his crate. */
	private static void workstationAtCrateSpot(GameTestHelper helper, String name, BlockState state, boolean reload) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		BlockPos crate = helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE);
		BlockPos moved = crate.offset(2, 0, 2);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(s.to().subtract(s.from()).equals(new BlockPos(2, 0, 2)), "setup: the move isn't 2 south-east: " + s.to().subtract(s.from()));
			MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			helper.assertTrue(level.getBlockState(moved).isAir(), "setup: the spot 2 south-east of his crate holds " + level.getBlockState(moved));
			level.setBlock(moved, state, Block.UPDATE_ALL);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(free(level, moved), "setup: the " + state.getBlock() + " isn't a free workstation");
				if (reload) {
					MoverGameTests.reload(level, s);
				}
				MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
				helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
				String job = s.mover().getBrain().getMemory(MemoryModuleType.JOB_SITE)
					.map(g -> level.getBlockState(g.pos()).getBlock() + " at " + g.pos().subtract(crate) + " from his crate").orElse("none");
				helper.assertTrue(level.getBlockState(crate).is(ModBlocks.MOVERS_CRATE), "his crate no longer stands: " + level.getBlockState(crate));
				helper.assertTrue(MoverWork.hasCrate(level, s.mover()), (reload ? "after a reload mid-move, " : "")
					+ "the Mover's job site after the move is " + job + ", not his crate");
				helper.assertTrue(free(level, moved), "the " + state.getBlock() + " where his crate would have gone was claimed by the move" + (reload ? " (reloaded)" : ""));
				helper.succeed();
			});
		});
	}

	/** A player's lectern (a librarian's workstation) where his crate would have moved: his crate stays his, the lectern stays free. */
	//$ gametest_batch HUGE '"qaC3LecternAtCrateSpot"'
	@GameTest(template = HUGE, batch = "qaC3LecternAtCrateSpot")
	public void aLecternWhereHisCrateWouldHaveMovedLeavesHimHisCrate(GameTestHelper helper) {
		workstationAtCrateSpot(helper, "qac3lectern", Blocks.LECTERN.defaultBlockState(), false);
	}

	/** The barrel case, saved and loaded after the house came down: his crate stays his, the barrel stays free. */
	//$ gametest_batch HUGE '"qaC3BarrelAtCrateSpotReload"'
	@GameTest(template = HUGE, batch = "qaC3BarrelAtCrateSpotReload")
	public void aBarrelWhereHisCrateWouldHaveMovedLeavesHimHisCrateThroughAReload(GameTestHelper helper) {
		workstationAtCrateSpot(helper, "qac3barrelreload", Blocks.BARREL.defaultBlockState(), true);
	}

	// ---- B1264: a called-off move's new spot ------------------------------------------------------------------------

	/** Whether any move counts as keeping (moving to, or putting back off) a new spot. */
	private static int kept(ServerLevel level) {
		return MoveFit.moving(level, null).size();
	}

	/**
	 * Called off before the Mover's first swing (nothing taken down, nothing at S): S is never kept, neither for a builder
	 * nor as another move's new spot, and the move ends with A standing whole where it was.
	 */
	//$ gametest_ticks_batch BIG '300' '"qaC3MoveCancelAtOnce"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qaC3MoveCancelAtOnce")
	public void calledOffBeforeTheFirstSwingNeverKeepsS(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = s.v().level();
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			helper.assertTrue(kept(level) == 1, "setup: the move under way doesn't keep S: " + kept(level));
			helper.assertTrue(MoveSites.get(level).cancel(level, s.v().a()), "setup: A's move couldn't be called off");
			helper.assertTrue(site.canceled() && !site.puttingBack() && kept(level) == 0,
				"called off before the first swing: putting back at S " + site.puttingBack() + ", moves keeping a spot " + kept(level));
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			helper.assertTrue(!h.said().contains(QaMoveSpotCancel1114gGameTests.backLine(QaMoveSpotBuild1114bGameTests.hut())),
				"called off before the first swing, yet told the Mover is still taking it back down: " + h.said());
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, h, "called off before the first swing, a hut on S");
			helper.succeed();
		});
	}

	/**
	 * The boundary from the other side: called off right after the first block of A stood at S, S is kept (the put-back
	 * line, and counted as kept for another move's new spot); once the last of A is off S, before the Mover moves on,
	 * it isn't any more and a hut on S is taken.
	 */
	//$ gametest_ticks_batch BIG '300' '"qaC3MoveCancelFirstBlock"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qaC3MoveCancelFirstBlock")
	public void calledOffAfterTheFirstBlockAtSKeepsSUntilItIsDown(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = s.v().level();
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[1] >= 1);
			int atS = QaMoveSpotSaves1111eGameTests.count(helper)[1];
			helper.assertTrue(atS >= 1 && atS <= 3, "setup: " + atS + " blocks at S when called off (wanted the first)");
			helper.assertTrue(MoveSites.get(level).cancel(level, s.v().a()), "setup: A's move couldn't be called off");
			helper.assertTrue(site.stage() == MoveSite.Stage.RESTORE_DOWN && site.puttingBack() && kept(level) == 1,
				"called off with " + atS + " block(s) of A at S: " + site.stage() + ", putting back " + site.puttingBack() + ", kept " + kept(level));
			QaMoveSpotCancel1114gGameTests.assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3),
				atS + " block(s) of A at S, a hut on S");
			QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[1] == 0 || m.stage() != MoveSite.Stage.RESTORE_DOWN);
			helper.assertTrue(QaMoveSpotSaves1111eGameTests.count(helper)[1] == 0, "setup: A never came off S (" + site.stage() + ")");
			helper.assertTrue(!site.puttingBack() && kept(level) == 0, "S empty again (" + site.stage() + "), yet still kept: putting back "
				+ site.puttingBack() + ", kept " + kept(level));
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, h, "the last of A off S, a hut on S");
			helper.assertTrue(!h.said().contains(QaMoveSpotCancel1114gGameTests.backLine(QaMoveSpotBuild1114bGameTests.hut())), "S empty, yet " + h.said());
			helper.succeed();
		});
	}

	/** Called off early, the moves' records reloaded: still not counted as keeping S for another move's new spot. */
	//$ gametest_ticks_batch BIG '300' '"qaC3MoveCancelEarlyKept"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qaC3MoveCancelEarlyKept")
	public void calledOffEarlyIsNoOtherMovesObstacleThroughAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = s.v().level();
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[0] < before[0] / 2);
			helper.assertTrue(QaMoveSpotSaves1111eGameTests.count(helper)[1] == 0 && site.stage() == MoveSite.Stage.DISMANTLE, "setup: something at S already");
			helper.assertTrue(MoveSites.get(level).cancel(level, s.v().a()), "setup: A's move couldn't be called off");
			helper.assertTrue(kept(level) == 0, "called off early, S still kept for another move: " + MoveFit.moving(level, null));
			QaMoveSpotBuild1114bGameTests.reload(level);
			helper.assertTrue(kept(level) == 0, "called off early and reloaded, S kept for another move: " + MoveFit.moving(level, null));
			MoveSite loaded = MoveSites.get(level).forBuilding(s.v().a()).orElseThrow(() -> new AssertionError("the move didn't survive the reload"));
			QaMoveSpotBuild1114bGameTests.stepUntil(level, loaded, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			helper.succeed();
		});
	}

	// ---- B1274 / B1275: drops on his crate ---------------------------------------------------------------------------

	private static int count(List<ItemStack> stacks, Item item) {
		int n = 0;
		for (ItemStack s : stacks) {
			n += s.is(item) ? s.getCount() : 0;
		}
		return n;
	}

	private static int total(List<ItemStack> stacks) {
		int n = 0;
		for (ItemStack s : stacks) {
			n += s.getCount();
		}
		return n;
	}

	/**
	 * Every count of snow layers, 1 to 7, on a crate is exactly that many snow, never a snowball. Each 8 times. (8 layers:
	 * QaVillageMoverFixes1115c3BugTests, unregistered, a snow block plus 7 snow.)
	 */
	//$ gametest_batch HUGE '"qaC3SnowEveryCount"'
	@GameTest(template = HUGE, batch = "qaC3SnowEveryCount")
	public void everyCountOfSnowLayersIsThatManySnow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
		StringBuilder bad = new StringBuilder();
		for (int n = 1; n <= 7; n++) {
			for (int i = 0; i < 8; i++) {
				List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, n));
				if (count(drops, Items.SNOW) != n || total(drops) != n) {
					bad.append(n).append(" layer(s): ").append(drops).append("; ");
					break;
				}
			}
		}
		helper.assertTrue(bad.isEmpty(), "snow layers on a crate weren't that many snow: " + bad);
		helper.succeed();
	}

	/** {@code layers} snow layers on his crate in a full move: exactly that many snow, no snowball, none standing. */
	static void snowMove(GameTestHelper helper, int layers, String name) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
		helper.runAfterDelay(2, () -> {
			BlockState snow = level.getBlockState(helper.absolutePos(SPOT));
			helper.assertTrue(snow.is(Blocks.SNOW) && snow.getValue(SnowLayerBlock.LAYERS) == layers, "setup: the snow is now " + snow);
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, layers + " snow layer(s) on his crate");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOWBALL, 0, "snowballs from " + layers + " snow layer(s)");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOW, layers, layers + " snow layer(s) on his crate");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SNOW) == 0, "snow left standing");
			helper.succeed();
		});
	}

	/** One snow layer on his crate, a full move: one snow. */
	//$ gametest_batch HUGE '"qaC3SnowOneMove"'
	@GameTest(template = HUGE, batch = "qaC3SnowOneMove")
	public void oneSnowLayerOnHisCrateIsOneSnow(GameTestHelper helper) {
		snowMove(helper, 1, "qac3snow1");
	}

	/**
	 * Every tall plant, with its upper half over its lower and either half dropped on a crate, 64 times each: always its
	 * one item ("drops once, as the whole thing's one item"), never a seed or nothing by chance.
	 */
	//$ gametest_batch HUGE '"qaC3TallPlants"'
	@GameTest(template = HUGE, batch = "qaC3TallPlants")
	public void everyTallPlantEitherHalfIsAlwaysItsOneItem(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
		Block[] plants = {Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.ROSE_BUSH, Blocks.SUNFLOWER, Blocks.LILAC, Blocks.PEONY, Blocks.PITCHER_PLANT};
		List<String> wrong = new ArrayList<>();
		for (Block plant : plants) {
			BlockState lo = plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
			BlockState up = plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
			level.setBlock(lower, lo, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.setBlock(lower.above(), up, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			int bad = 0;
			String seen = "";
			for (int i = 0; i < 64; i++) {
				for (boolean upper : new boolean[] {false, true}) {
					List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, upper ? lower.above() : lower, upper ? up : lo);
					if (drops.size() != 1 || !drops.get(0).is(plant.asItem()) || drops.get(0).getCount() != 1) {
						bad++;
						seen = (upper ? "upper: " : "lower: ") + drops;
					}
				}
			}
			if (bad > 0) {
				wrong.add(plant + " " + bad + " of 128 (e.g. " + seen + ")");
			}
		}
		level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.assertTrue(wrong.isEmpty(), "tall plants on a crate that weren't their one item: " + wrong);
		helper.succeed();
	}

	/** A large fern whose upper half lands on his crate (its lower half put up under it first), a full move: one large fern. */
	//$ gametest_batch HUGE '"qaC3LargeFernUpper"'
	@GameTest(template = HUGE, batch = "qaC3LargeFernUpper")
	public void aLargeFernWhoseUpperHalfLandsOnHisCrateIsOneLargeFern(GameTestHelper helper) {
		tallPlantMove(helper, Blocks.LARGE_FERN, true, "qac3fern");
	}

	/** Tall grass whose lower half lands on his crate (its upper half over it), a full move: one tall grass. */
	//$ gametest_batch HUGE '"qaC3TallGrassLower"'
	@GameTest(template = HUGE, batch = "qaC3TallGrassLower")
	public void tallGrassWhoseLowerHalfLandsOnHisCrateIsOneTallGrass(GameTestHelper helper) {
		tallPlantMove(helper, Blocks.TALL_GRASS, false, "qac3grass");
	}

	private static void tallPlantMove(GameTestHelper helper, Block plant, boolean upperOnCrate, String name) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		BlockPos at = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		BlockPos lo = upperOnCrate ? at.below() : at;
		level.setBlock(lo, plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lo.above(), plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		String what = plant + "'s " + (upperOnCrate ? "upper" : "lower") + " half on his crate";
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, plant) == 2, "setup: no " + plant);
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, what);
			int items = QaMoverCrateDrop1114eGameTests.count(helper, plant.asItem());
			int halves = QaMoverCrateHolds1114cGameTests.standing(helper, plant);
			helper.assertTrue(halves == 0, what + ": " + halves + " half(s) left standing alone");
			helper.assertTrue(items == 1, what + ": " + items + " " + plant.asItem() + " round the test, not 1");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.WHEAT_SEEDS, 0, what + ", seeds instead");
			helper.succeed();
		});
	}

	// ---- B1270 / B1271: the nearer hall across the ground -------------------------------------------------------------

	/** Clears the spot, radius 12, hall A at {@code aRel}; returns A (absolute). */
	private static BlockPos zoneSetUp(GameTestHelper helper, BlockPos aRel) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 12;
		helper.setBlock(aRel, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertTrue(CityPlan.cellSize() == 1, "setup: a cell isn't one block at radius 12");
		return helper.absolutePos(aRel);
	}

	private static void paint(GameTestHelper helper, BlockPos aRel, BlockPos a, BlockPos... at) {
		BitSet cells = new BitSet();
		for (BlockPos p : at) {
			int c = CityPlan.cellAt(a, p);
			helper.assertTrue(c >= 0, "setup: " + p.subtract(a) + " from A isn't on A's grid");
			cells.set(c);
		}
		VillageHallBlockEntity entity = helper.getBlockEntity(aRel);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
	}

	private static BlockPos hall(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		return helper.absolutePos(rel);
	}

	/** A finished Stone Wall whose middle is {@code middle} (absolute), turned {@code turn}; returns its corner. */
	private static BlockPos wall(GameTestHelper helper, BlockPos middle, Rotation turn, String name) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = StarterBlueprints.STONE_WALL.id();
		BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, turn, Mirror.NONE));
		BlockPos origin = middle.offset(middle.getX() - first.getX(), 0, middle.getZ() - first.getZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, turn, Mirror.NONE);
		BlockPos spot = Builders.spot(level, id, placement);
		helper.assertTrue(spot.getX() == middle.getX() && spot.getZ() == middle.getZ(), "setup: the wall's middle is " + spot + ", not " + middle);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.nameUUIDFromBytes(("qa1115c3-" + name).getBytes(StandardCharsets.UTF_8)));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return origin;
	}

	/** The turn that puts the wall's corner farthest west of its middle (toward A). */
	private static Rotation westward(GameTestHelper helper, BlockPos middle) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = StarterBlueprints.STONE_WALL.id();
		Rotation best = Rotation.NONE;
		int bestDx = Integer.MAX_VALUE;
		for (Rotation r : Rotation.values()) {
			BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, r, Mirror.NONE));
			int dx = middle.getX() - first.getX(); // the corner's x offset from the middle
			if (dx < bestDx) {
				bestDx = dx;
				best = r;
			}
		}
		return best;
	}

	private static long across(BlockPos p, BlockPos q) {
		long dx = p.getX() - q.getX();
		long dz = p.getZ() - q.getZ();
		return dx * dx + dz * dz;
	}

	/**
	 * B1270 from below: hall A stands 10 blocks above the ground of hall B. A's painted cell is 14 across from A (on A's
	 * level) and 11 across from B, 10 blocks lower: B is the nearer across, so a Stone Wall there walls B only and adds
	 * to B's rank, not A's.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaC3ZoneLowerHall"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaC3ZoneLowerHall")
	public void aNearerHallOnLowerGroundTakesTheCell(GameTestHelper helper) {
		BlockPos aRel = new BlockPos(1, 12, 15);
		BlockPos a = zoneSetUp(helper, aRel);
		ServerLevel level = helper.getLevel();
		BlockPos cell = a.east(14);
		paint(helper, aRel, a, cell);
		helper.assertTrue(Reach.claims(level, a, cell), "setup: A's painted cell isn't A's before B stands");
		BlockPos b = hall(helper, aRel.east(25).below(10));
		int aBefore = VillageRanks.score(level, a, 0).buildings();
		int bBefore = VillageRanks.score(level, b, 0).buildings();
		helper.assertFalse(Reach.claims(level, a, cell), "hall B, 11 across and 10 lower, nearer the cell than A (14 across): still A's");
		wall(helper, cell, Rotation.CLOCKWISE_180, "lower");
		helper.assertTrue(Sieges.walled(level, b), "the wall 11 across from the lower hall B doesn't wall B");
		helper.assertFalse(Sieges.walled(level, a), "the wall in the cell nearer the lower hall B (across) walls A too");
		helper.assertTrue(VillageRanks.score(level, a, 0).buildings() == aBefore, "the wall nearer the lower hall B counted for A's rank");
		helper.assertTrue(VillageRanks.score(level, b, 0).buildings() == bBefore + 1, "the wall didn't count for B's rank: "
			+ bBefore + " -> " + VillageRanks.score(level, b, 0).buildings());
		helper.succeed();
	}

	/**
	 * B1270 at the boundary with a height difference: hall B 10 blocks up, 15, 14 (a tie) and 13 across from A's painted
	 * cell (A 14 across, level): 15 and the tie leave the cell A's, 13 takes it. And B 18 blocks up, 7 east and 8 south of
	 * the cell (113 across squared, under A's 196) takes it too.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaC3ZoneHigherBoundary"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaC3ZoneHigherBoundary")
	public void theTieAndOneBlockEitherWayHoldWithAHeightDifference(GameTestHelper helper) {
		BlockPos aRel = new BlockPos(0, 2, 12);
		BlockPos a = zoneSetUp(helper, aRel);
		ServerLevel level = helper.getLevel();
		BlockPos cell = a.east(14);
		paint(helper, aRel, a, cell);
		int[] across = {15, 14, 13};
		boolean[] takes = {false, false, true};
		for (int i = 0; i < across.length; i++) {
			BlockPos bRel = aRel.east(14 + across[i]).above(10);
			BlockPos b = hall(helper, bRel);
			boolean aHas = Reach.claims(level, a, cell);
			helper.assertTrue(aHas == !takes[i], "hall B " + across[i] + " across (A 14) and 10 up: the cell is " + (aHas ? "" : "not ") + "A's");
			level.destroyBlock(b, false);
			VillageHalls.forgetNearest();
			helper.assertTrue(Reach.claims(level, a, cell), "hall B broken (" + across[i] + " across): the cell isn't A's again");
		}
		BlockPos bRel = aRel.offset(14 + 7, 18, 8);
		BlockPos b = hall(helper, bRel);
		helper.assertTrue(across(b, cell) == 113, "setup: B is " + across(b, cell) + " across squared from the cell");
		helper.assertFalse(Reach.claims(level, a, cell), "hall B 7 east, 8 south and 18 up from the cell (113 across squared, A 196): still A's");
		helper.succeed();
	}

	/**
	 * B1270 through painting: with hall B 11 across and 10 up from a cell (A 14 across), a player painting that cell and
	 * one 10 east of A (B 15 across) is told "1 cell is nearer another village: left out", and only the cell nearer A is
	 * painted.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaC3ZoneHigherPaint"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaC3ZoneHigherPaint")
	public void paintingLeavesOutTheCellNearerAHigherHallAndSaysSo(GameTestHelper helper) {
		BlockPos aRel = new BlockPos(1, 2, 15);
		BlockPos a = zoneSetUp(helper, aRel);
		ServerLevel level = helper.getLevel();
		hall(helper, aRel.east(25).above(10));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.CREATIVE);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		int nearB = CityPlan.cellAt(a, a.east(14)); // 14 from A, 11 from B
		int nearA = CityPlan.cellAt(a, a.offset(10, 0, 0)); // 10 from A, 15 from B
		BitSet stroke = new BitSet();
		stroke.set(nearA);
		stroke.set(nearB);
		VillageHallBlockEntity entity = helper.getBlockEntity(aRel);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", ""));
		seen.clear();
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(a, 0, stroke))), "the stroke was refused whole");
		BitSet painted = entity.plan().zones().get(0).cells();
		helper.assertTrue(painted.get(nearA) && !painted.get(nearB) && painted.cardinality() == 1,
			"painted " + painted + "; expected only the cell 10 east of A (" + nearA + "), not 14 east (" + nearB + ", nearer the higher hall B)");
		List<String> lines = seen.stream().map(Component::getString).toList();
		helper.assertTrue(lines.contains("1 cell is nearer another village: left out"), "the painter read " + lines);
		helper.succeed();
	}

	/** Places A, a wall whose middle is 14 east of A with its corner toward A; returns {A, middle, corner}. */
	private static BlockPos[] cornerTowardA(GameTestHelper helper, BlockPos aRel, int middleDx, String name) {
		BlockPos a = zoneSetUp(helper, aRel);
		BlockPos middle = a.east(middleDx);
		BlockPos corner = wall(helper, middle, westward(helper, middle), name);
		return new BlockPos[] {a, middle, corner};
	}

	/**
	 * B1271 per village-hall.md: a wall whose middle is 14 from A (past its radius, unpainted) and 11 from hall B, with its
	 * corner inside A's radius, is B's only. Without B it was A's (the corner rule, B620). Rank counts follow.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaC3ZoneCornerInRadius"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaC3ZoneCornerInRadius")
	public void aWallWithItsCornerInsideARadiusButItsMiddleNearerBIsBsOnly(GameTestHelper helper) {
		BlockPos aRel = new BlockPos(1, 2, 15);
		BlockPos[] w = cornerTowardA(helper, aRel, 14, "radius");
		BlockPos a = w[0];
		ServerLevel level = helper.getLevel();
		helper.assertTrue(across(w[2], a) <= 144 && across(w[1], a) > 144, "setup: corner " + w[2].subtract(a) + ", middle " + w[1].subtract(a) + " from A");
		helper.assertTrue(Sieges.walled(level, a), "no hall B yet: the wall with its corner inside A's radius doesn't wall A (B620's corner rule)");
		int aBefore = VillageRanks.score(level, a, 0).buildings();
		BlockPos b = hall(helper, aRel.east(25));
		helper.assertTrue(Sieges.walled(level, b), "the wall whose middle is 11 from B doesn't wall B");
		helper.assertFalse(Sieges.walled(level, a), "the wall's middle is nearer hall B, but its corner inside A's radius still walls A");
		helper.assertTrue(VillageRanks.score(level, a, 0).buildings() == aBefore - 1, "A's rank count with B placed: " + aBefore + " -> "
			+ VillageRanks.score(level, a, 0).buildings() + " (the wall should no longer count)");
		helper.succeed();
	}

	/**
	 * The same with hall B on higher ground (B1270 and B1271 together): 11 across from the middle, 10 blocks up. The wall
	 * is B's only.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaC3ZoneCornerHigherB"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaC3ZoneCornerHigherB")
	public void aWallWithItsCornerInARadiusAndItsMiddleNearerAHigherBIsBsOnly(GameTestHelper helper) {
		BlockPos aRel = new BlockPos(1, 2, 15);
		BlockPos[] w = cornerTowardA(helper, aRel, 14, "higherb");
		BlockPos a = w[0];
		ServerLevel level = helper.getLevel();
		helper.assertTrue(across(w[2], a) <= 144, "setup: corner " + w[2].subtract(a) + " from A outside A's radius");
		BlockPos b = hall(helper, aRel.east(25).above(10));
		helper.assertTrue(Sieges.walled(level, b), "the wall 11 across from the higher hall B doesn't wall B");
		helper.assertFalse(Sieges.walled(level, a), "the wall's middle is nearer the higher hall B across, but its corner walls A too");
		helper.succeed();
	}

	/**
	 * The corner rule at its boundary: the middle 14 from A, hall B 15, 14 (a tie) and 13 from the middle (all past B's
	 * radius, so B never has it): the tie and 15 leave it A's by its corner (not nearer another hall), 13 doesn't.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaC3ZoneCornerTie"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaC3ZoneCornerTie")
	public void theCornerCountsAtATieAndStopsOneBlockNearerB(GameTestHelper helper) {
		BlockPos aRel = new BlockPos(0, 2, 15);
		BlockPos[] w = cornerTowardA(helper, aRel, 14, "tie");
		BlockPos a = w[0];
		ServerLevel level = helper.getLevel();
		helper.assertTrue(across(w[2], a) <= 144, "setup: corner " + w[2].subtract(a) + " from A outside A's radius");
		int[] fromB = {15, 14, 13};
		boolean[] aKeeps = {true, true, false};
		for (int i = 0; i < fromB.length; i++) {
			BlockPos b = hall(helper, aRel.east(14 + fromB[i]));
			helper.assertTrue(across(w[1], b) == (long) fromB[i] * fromB[i], "setup: the middle is " + across(w[1], b) + " across squared from B");
			helper.assertTrue(Sieges.walled(level, a) == aKeeps[i], "hall B " + fromB[i] + " from the wall's middle (A 14): A walled "
				+ Sieges.walled(level, a) + ", expected " + aKeeps[i]);
			helper.assertFalse(Sieges.walled(level, b), "B walled by a wall " + fromB[i] + " from it, past its radius of 12");
			level.destroyBlock(b, false);
			VillageHalls.forgetNearest();
		}
		helper.assertTrue(Sieges.walled(level, a), "hall B broken: the wall by its corner isn't A's again");
		helper.succeed();
	}

	/** A wall whose middle lies inside A's own radius (11) but nearer hall B (9) still counts for A, and for B. */
	//$ gametest_ticks_batch HUGE '100' '"qaC3ZoneMiddleInARadius"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaC3ZoneMiddleInARadius")
	public void aWallWhoseMiddleIsInsideARadiusStaysAsEvenNearerB(GameTestHelper helper) {
		BlockPos aRel = new BlockPos(1, 2, 15);
		BlockPos a = zoneSetUp(helper, aRel);
		ServerLevel level = helper.getLevel();
		BlockPos b = hall(helper, aRel.east(20));
		wall(helper, a.east(11), Rotation.CLOCKWISE_90, "inradius");
		helper.assertTrue(Sieges.walled(level, a), "the wall whose middle is 11 from A, inside A's radius, doesn't wall A (village-hall.md: still counts for A)");
		helper.assertTrue(Sieges.walled(level, b), "the wall whose middle is 9 from B doesn't wall B");
		helper.succeed();
	}

	/**
	 * A wall whose middle is off A's City Plan grid (17 east; the grid reaches 16) and whose corner is in a cell A painted:
	 * with no other hall it counts for A by its corner; with hall B 12 from the middle (inside B's radius) and 15 from the
	 * corner (A 14), the corner is still nearer A but the middle nearer B, so the wall is B's only.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaC3ZoneMiddleOffGrid"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaC3ZoneMiddleOffGrid")
	public void aWallWhoseMiddleIsOffTheGridGoesToTheHallNearerItsMiddle(GameTestHelper helper) {
		BlockPos aRel = new BlockPos(0, 2, 15);
		BlockPos[] w = cornerTowardA(helper, aRel, 17, "offgrid");
		BlockPos a = w[0];
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CityPlan.cellAt(a, w[1]) < 0, "setup: the middle " + w[1].subtract(a) + " is on A's grid");
		paint(helper, aRel, a, w[2]);
		helper.assertTrue(across(w[2], a) > 144 && Reach.claims(level, a, w[2]), "setup: the corner " + w[2].subtract(a) + " isn't A's by its painted cell alone");
		helper.assertTrue(Sieges.walled(level, a), "no other hall: the wall whose corner is in A's painted cell doesn't wall A");
		BlockPos b = hall(helper, aRel.east(29));
		helper.assertTrue(across(w[2], b) > across(w[2], a), "setup: the corner " + w[2].subtract(a) + " isn't nearer A than B");
		helper.assertTrue(Sieges.walled(level, b), "the wall 12 from B (its radius) doesn't wall B");
		helper.assertFalse(Sieges.walled(level, a), "the wall's middle (off A's grid) is 12 from B and 17 from A, yet its corner still walls A");
		helper.succeed();
	}
}
