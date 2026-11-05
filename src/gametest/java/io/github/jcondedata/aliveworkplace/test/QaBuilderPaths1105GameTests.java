package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b, 2026-11-05), the builders' dirt paths (25.5, docs/wiki/builder.md "Upkeep and paths", the
 * {@code builderPaths} switch in config-switches.md), from the spec: "After a build, it can lay a dirt path to the
 * village bell or hall"; the bell or hall "within 48 blocks", the nearer of the two, "their bench if there's neither";
 * "only natural ground on it is turned into path; stone, gravel, sand and roads already there are walked over as they
 * are"; the route goes "round water"; "the grass and flowers on it cleared away". Edges the earlier path tests
 * (BuilderGameTests, QaBenchMovePathsPaceGameTests, PathLedgerGameTests) didn't cover: the ground kinds, a pond in the
 * way, the bench fallback, a hall nearer than the bell, plants on the path, and a planned path through a restart.
 * The test hut (5x4x5, door on its north side) goes up at (12, 2, 2) of a huge area, so its path leaves northwards and
 * goes round it.
 */
public class QaBuilderPaths1105GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-000000001105");
	private static final BlockPos HUT = new BlockPos(12, 2, 2);

	/**
	 * "Stone, gravel, sand and roads already there are walked over as they are": a band right across the way from the
	 * hut to the bell (a row each of stone, gravel, sand and cobblestone) is crossed and left as it was, and grass on
	 * both sides of it is turned into path. (An old dirt path across the way: QaBuilderPaths1105BugTests.)
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaPaths1105Ground"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaPaths1105Ground")
	public void aPathOverStoneGravelAndSandLeavesThemAsTheyAre(GameTestHelper helper) {
		ServerLevel level = arena(helper);
		Block[] band = {Blocks.STONE, Blocks.GRAVEL, Blocks.SAND, Blocks.COBBLESTONE};
		for (int x = 0; x < 30; x++) {
			for (int i = 0; i < band.length; i++) {
				helper.setBlock(new BlockPos(x, 0, 14 + i), Blocks.STONE); // under the gravel and sand
				helper.setBlock(new BlockPos(x, 1, 14 + i), band[i]);
			}
		}
		BlockPos bell = new BlockPos(14, 2, 27);
		helper.setBlock(bell, Blocks.BELL);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 24), QaBenchMovePathsPaceGameTests.hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, HUT));
		helper.succeedWhen(() -> {
			done(helper, level, site, builder);
			for (int x = 0; x < 30; x++) {
				for (int i = 0; i < band.length; i++) {
					BlockPos p = new BlockPos(x, 1, 14 + i);
					helper.assertTrue(helper.getBlockState(p).is(band[i]), "the " + band[i].getName().getString() + " at " + p + " is now " + helper.getBlockState(p));
				}
			}
			int before = paths(helper, 0, 13);
			int after = paths(helper, 18, 29);
			helper.assertTrue(before >= 4 && after >= 4, "the path should run on both sides of the band: " + before + " north, " + after + " south");
			helper.assertTrue(near(helper, bell, 3), "the path doesn't reach the bell");
			helper.succeed();
		});
	}

	/**
	 * "Round water": a pond across most of the way (x 1 to 25, open past x 26) is gone round. Its water is all still
	 * there, nothing in it turned into path, and the path still reaches the bell.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaPaths1105Pond"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaPaths1105Pond")
	public void aPathGoesRoundAPondAndLeavesItsWaterAlone(GameTestHelper helper) {
		ServerLevel level = arena(helper);
		List<BlockPos> pond = new ArrayList<>();
		for (int x = 1; x <= 25; x++) {
			for (int z = 12; z <= 13; z++) {
				BlockPos p = new BlockPos(x, 1, z);
				helper.setBlock(p.below(), Blocks.STONE);
				helper.setBlock(p, Blocks.WATER);
				pond.add(p);
			}
		}
		BlockPos bell = new BlockPos(14, 2, 27);
		helper.setBlock(bell, Blocks.BELL);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 24), QaBenchMovePathsPaceGameTests.hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, HUT));
		helper.succeedWhen(() -> {
			done(helper, level, site, builder);
			for (BlockPos p : pond) {
				helper.assertTrue(helper.getBlockState(p).is(Blocks.WATER) && helper.getBlockState(p).getFluidState().isSource(), "the pond at " + p + " is now " + helper.getBlockState(p));
			}
			helper.assertTrue(near(helper, bell, 3), "the path doesn't reach the bell past the pond (" + paths(helper, 0, 29) + " blocks of path)");
			helper.assertTrue(paths(helper, 14, 29) >= 4, "no path south of the pond");
			helper.succeed();
		});
	}

	/**
	 * "Their bench if there's neither": with no bell and no Village Hall anywhere near, the finished hut's path leads to
	 * the builder's Blueprint Table.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaPaths1105Bench"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaPaths1105Bench")
	public void withNoBellOrHallThePathLeadsToTheBench(GameTestHelper helper) {
		ServerLevel level = arena(helper);
		BlockPos table = new BlockPos(24, 2, 24);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, table, QaBenchMovePathsPaceGameTests.hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, HUT));
		helper.succeedWhen(() -> {
			done(helper, level, site, builder);
			helper.assertTrue(paths(helper, 0, 29) >= 6, "only " + paths(helper, 0, 29) + " blocks of path");
			helper.assertTrue(near(helper, table, 4), "the path doesn't lead to the Blueprint Table");
			helper.succeed();
		});
	}

	/**
	 * "The village bell or hall", the nearer: a Village Hall 12 blocks south of the hut and a bell 25 blocks south. The
	 * path ends at the hall and none is laid on the way on to the bell.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaPaths1105Nearer"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaPaths1105Nearer")
	public void aHallNearerThanTheBellGetsThePath(GameTestHelper helper) {
		ServerLevel level = arena(helper);
		BlockPos hall = new BlockPos(14, 2, 15);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		ensurePoi(level, helper.absolutePos(hall));
		((VillageHallBlockEntity) helper.getBlockEntity(hall)).setOwner(JESSE, "Jesse");
		VillageHalls.forgetNearest();
		BlockPos hallAbs = helper.absolutePos(hall);
		Leftovers.after(helper, () -> {
			level.removeBlock(hallAbs, false);
			VillageHalls.forgetNearest();
		});
		BlockPos bell = new BlockPos(14, 2, 28);
		helper.setBlock(bell, Blocks.BELL);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 26), QaBenchMovePathsPaceGameTests.hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, HUT));
		helper.succeedWhen(() -> {
			done(helper, level, site, builder);
			helper.assertTrue(near(helper, hall, 4), "the path doesn't reach the nearer hall (" + paths(helper, 0, 29) + " blocks of path)");
			helper.assertTrue(paths(helper, 20, 29) == 0, paths(helper, 20, 29) + " blocks of path past the hall, towards the farther bell");
			helper.succeed();
		});
	}

	/**
	 * "The grass and flowers on it cleared away": a planned path under a poppy, a dandelion, short grass, a tall grass
	 * and a snow layer is laid as dirt path all the way, with nothing left standing on it (the tall grass's top half
	 * included).
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaPaths1105Plants"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaPaths1105Plants")
	public void plantsOnAPlannedPathAreClearedAndThePathLaidUnderThem(GameTestHelper helper) {
		ServerLevel level = arena(helper);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 14), QaBenchMovePathsPaceGameTests.hutMaterials());
		List<BlockPos> spots = row(helper, 6, 14, 8);
		helper.setBlock(new BlockPos(7, 2, 14), Blocks.POPPY);
		helper.setBlock(new BlockPos(8, 2, 14), Blocks.DANDELION);
		helper.setBlock(new BlockPos(9, 2, 14), Blocks.SHORT_GRASS);
		helper.setBlock(new BlockPos(10, 2, 14), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(10, 3, 14), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		helper.setBlock(new BlockPos(11, 2, 14), Blocks.SNOW);
		ModAttachments.PATH.set(builder, absolute(helper, spots));
		helper.succeedWhen(() -> {
			helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the path: " + ModAttachments.PATH.getOrElse(builder, List.of()).size() + " to go");
			for (BlockPos p : spots) {
				helper.assertTrue(helper.getBlockState(p).is(Blocks.DIRT_PATH), p + " is " + helper.getBlockState(p) + ", not dirt path");
				helper.assertTrue(helper.getBlockState(p.above()).isAir() && helper.getBlockState(p.above(2)).isAir(),
					"left standing on the path at " + p.above() + ": " + helper.getBlockState(p.above()) + " / " + helper.getBlockState(p.above(2)));
			}
			helper.succeed();
		});
	}

	/**
	 * "Only natural ground on it is turned into path", when the ground changed after the path was planned: of a planned
	 * row of grass, three spots became stone, cobblestone and a gravel since (a villager or the world, not a player). They
	 * are left as they are, and the grass between them is still turned into path.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaPaths1105Changed"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaPaths1105Changed")
	public void groundThatIsNoLongerNaturalIsLeftAsItIs(GameTestHelper helper) {
		arena(helper);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 14), QaBenchMovePathsPaceGameTests.hutMaterials());
		List<BlockPos> spots = row(helper, 6, 14, 8);
		ModAttachments.PATH.set(builder, absolute(helper, spots));
		helper.setBlock(new BlockPos(7, 1, 14), Blocks.STONE);
		helper.setBlock(new BlockPos(9, 1, 14), Blocks.COBBLESTONE);
		helper.setBlock(new BlockPos(11, 0, 14), Blocks.STONE);
		helper.setBlock(new BlockPos(11, 1, 14), Blocks.GRAVEL);
		helper.succeedWhen(() -> {
			helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the path: " + ModAttachments.PATH.getOrElse(builder, List.of()).size() + " to go");
			helper.assertTrue(helper.getBlockState(new BlockPos(7, 1, 14)).is(Blocks.STONE), "the stone is now " + helper.getBlockState(new BlockPos(7, 1, 14)));
			helper.assertTrue(helper.getBlockState(new BlockPos(9, 1, 14)).is(Blocks.COBBLESTONE), "the cobblestone is now " + helper.getBlockState(new BlockPos(9, 1, 14)));
			helper.assertTrue(helper.getBlockState(new BlockPos(11, 1, 14)).is(Blocks.GRAVEL), "the gravel is now " + helper.getBlockState(new BlockPos(11, 1, 14)));
			for (int x : new int[] {6, 8, 10, 12, 13}) {
				BlockPos p = new BlockPos(x, 1, 14);
				helper.assertTrue(helper.getBlockState(p).is(Blocks.DIRT_PATH), p + " is " + helper.getBlockState(p) + ", not dirt path");
			}
			helper.succeed();
		});
	}

	/**
	 * A server restart while the builder still has his path to lay: the path is part of his save, so after the load
	 * he lays every block of it.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaPaths1105Restart"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaPaths1105Restart")
	public void aPlannedPathIsLaidAfterARestart(GameTestHelper helper) {
		ServerLevel level = arena(helper);
		Villager[] builder = {QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 14), QaBenchMovePathsPaceGameTests.hutMaterials())};
		List<BlockPos> spots = row(helper, 6, 14, 10);
		ModAttachments.PATH.set(builder[0], absolute(helper, spots));
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(builder[0].save(tag), "the builder wasn't saved");
		builder[0].discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		builder[0] = (Villager) loaded;
		helper.assertTrue(ModAttachments.PATH.getOrElse(builder[0], List.of()).size() == spots.size(),
			"the path to lay after the load: " + ModAttachments.PATH.getOrElse(builder[0], List.of()).size() + " of " + spots.size());
		helper.succeedWhen(() -> {
			helper.assertTrue(!ModAttachments.PATH.has(builder[0]), "still laying the path: " + ModAttachments.PATH.getOrElse(builder[0], List.of()).size() + " to go");
			for (BlockPos p : spots) {
				helper.assertTrue(helper.getBlockState(p).is(Blocks.DIRT_PATH), p + " is " + helper.getBlockState(p) + ", not dirt path");
			}
			helper.succeed();
		});
	}

	// --- helpers -------------------------------------------------------------------------------------------------------

	/** Leftovers cleared, builds quick, paths on, no bells or halls left nearby by other batches, grass all over. */
	static ServerLevel arena(GameTestHelper helper) {
		ServerLevel level = QaBenchMovePathsPaceGameTests.prepare(helper, 2);
		boolean was = Paths.ENABLED;
		Paths.ENABLED = true;
		Leftovers.after(helper, () -> Paths.ENABLED = was);
		AABB box = helper.getBounds();
		for (BlockPos bell : level.getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), p -> !box.contains(Vec3.atCenterOf(p)),
			BlockPos.containing(box.getCenter()), 96, PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(bell, false);
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		return level;
	}

	static void done(GameTestHelper helper, ServerLevel level, BuildSite site, Villager builder) {
		helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building");
		helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the path: " + ModAttachments.PATH.getOrElse(builder, List.of()).size() + " to go");
	}

	/** Blocks of dirt path on the ground (y 1) in rows z from..to. */
	static int paths(GameTestHelper helper, int fromZ, int toZ) {
		int n = 0;
		for (int x = 0; x < 30; x++) {
			for (int z = fromZ; z <= toZ; z++) {
				n += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DIRT_PATH) ? 1 : 0;
			}
		}
		return n;
	}

	/** Whether some dirt path lies within {@code r} blocks (each way) of {@code target}'s column. */
	static boolean near(GameTestHelper helper, BlockPos target, int r) {
		for (int x = Math.max(0, target.getX() - r); x <= Math.min(29, target.getX() + r); x++) {
			for (int z = Math.max(0, target.getZ() - r); z <= Math.min(29, target.getZ() + r); z++) {
				if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DIRT_PATH)) {
					return true;
				}
			}
		}
		return false;
	}

	/** {@code n} ground blocks (y 1) eastwards from {@code x, z}. */
	static List<BlockPos> row(GameTestHelper helper, int x, int z, int n) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(new BlockPos(x + i, 1, z));
		}
		return out;
	}

	static List<BlockPos> absolute(GameTestHelper helper, List<BlockPos> rel) {
		return rel.stream().map(helper::absolutePos).map(BlockPos::immutable).toList();
	}

	static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}
}
