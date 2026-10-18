package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * B185: in the city time-lapse villagers who aren't builders stood inside blocks for good: a jobless villager inside a
 * roof's stair, another inside a lantern on a fence (both just grown up: a baby fits in a nook a grown villager
 * doesn't, and vanilla finds no room for the bigger body there), and a fisherman inside a door shut on him while he
 * stood in the doorway. Here: each of them is let out within a few seconds (what the showcase's check sees: three
 * seconds in a row), a villager merely penned in tight is never moved, and a builder never puts a lantern down on a
 * villager standing where it goes.
 */
public class B185GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	/** The cell the villager is in: walled in on all four sides, two high. */
	private static final BlockPos CELL = new BlockPos(4, 2, 4);
	/** Within the showcase's three one-second checks, with a second to spare. */
	private static final int LET_OUT_BY = 40;

	/** A baby under a roof's bottom stair (deepslate tiles, as in the city) grows up: it isn't left inside the stair. */
	//$ gametest_ticks_batch AREA '400' '"b185GrownUnderStair"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b185GrownUnderStair")
	public void b185AVillagerGrownUpUnderARoofStairIsLetOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		pen(helper, CELL, true);
		helper.setBlock(CELL.above(), Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
			.setValue(StairBlock.HALF, Half.BOTTOM).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
		grownUpIsLetOut(helper, new Vec3(CELL.getX() + 0.5, CELL.getY(), CELL.getZ() + 0.5), "the stair");
	}

	/** A baby beside a lantern standing on a fence post (in the cell's corner) grows up: it isn't left inside them. */
	//$ gametest_ticks_batch AREA '400' '"b185GrownByLantern"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b185GrownByLantern")
	public void b185AVillagerGrownUpBesideALanternOnAFenceIsLetOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		pen(helper, CELL, true);
		helper.setBlock(CELL, Blocks.DARK_OAK_FENCE);
		helper.setBlock(CELL.above(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		grownUpIsLetOut(helper, new Vec3(CELL.getX() + 0.16, CELL.getY(), CELL.getZ() + 0.16), "the fence and lantern");
	}

	private static void grownUpIsLetOut(GameTestHelper helper, Vec3 at, String what) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, CELL);
		villager.setBaby(true);
		Vec3 abs = helper.absoluteVec(at);
		villager.moveTo(abs.x, abs.y, abs.z, 0, 0);
		helper.assertTrue(level.noCollision(villager, villager.getBoundingBox().deflate(0.01)), "the baby doesn't fit beside " + what + " to begin with");
		villager.setAge(-20); // grows up in a second
		int[] grownAt = {-1};
		String[] problem = {null};
		helper.onEachTick(() -> {
			if (!villager.isAlive()) {
				problem[0] = "the villager died at tick " + helper.getTick();
				return;
			}
			if (grownAt[0] < 0 && !villager.isBaby()) {
				grownAt[0] = (int) helper.getTick();
			}
			if (problem[0] == null && grownAt[0] >= 0 && helper.getTick() > grownAt[0] + LET_OUT_BY && stuck(level, villager)) {
				problem[0] = "B185: the grown villager is still inside " + what + " " + (helper.getTick() - grownAt[0]) + " ticks after growing up, at "
					+ String.format("%.2f %.2f %.2f", villager.getX(), villager.getY(), villager.getZ());
			}
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(grownAt[0] >= 0, "the baby didn't grow up");
			helper.assertFalse(stuck(level, villager), "B185: the grown villager is inside " + what);
			helper.assertTrue(villager.position().distanceTo(abs) < 6, "the villager was moved too far: " + villager.position().distanceTo(abs));
			villager.discard();
			helper.succeed();
		});
	}

	/** A neighbour shuts a wooden door on a villager standing in its doorway: the villager isn't left inside it. */
	//$ gametest_ticks_batch AREA '400' '"b185DoorShut"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b185DoorShut")
	public void b185AVillagerADoorIsShutOnIsLetOut(GameTestHelper helper) {
		doorShutOnAVillager(helper, Blocks.DARK_OAK_DOOR);
	}

	/** The same with an iron door, which a villager can't open: it is still let out. */
	//$ gametest_ticks_batch AREA '400' '"b185IronDoorShut"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b185IronDoorShut")
	public void b185AVillagerAnIronDoorIsShutOnIsLetOut(GameTestHelper helper) {
		doorShutOnAVillager(helper, Blocks.IRON_DOOR);
	}

	private static void doorShutOnAVillager(GameTestHelper helper, Block kind) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// a doorway in a wall along x, stone bricks over it (as in the city's corner)
		for (int x = 1; x <= 7; x++) {
			for (int y = 2; y <= 4; y++) {
				if (x != CELL.getX() || y == 4) {
					helper.setBlock(new BlockPos(x, y, CELL.getZ()), Blocks.STONE_BRICKS);
				}
			}
		}
		BlockPos door = helper.absolutePos(CELL);
		BlockState lower = kind.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
			.setValue(DoorBlock.OPEN, true);
		level.setBlock(door, lower, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
		// where the shut door's panel will be: on its line
		Vec3 line = kind.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).getCollisionShape(level, door).bounds().move(door).getCenter();
		Villager villager = helper.spawn(EntityType.VILLAGER, CELL);
		villager.moveTo(line.x, door.getY(), line.z, 0, 0);
		helper.assertTrue(level.noCollision(villager, villager.getBoundingBox().deflate(0.01)), "the villager doesn't fit in the open doorway");
		int[] shutAt = {-1};
		String[] problem = {null};
		helper.runAfterDelay(2, () -> {
			BlockState open = level.getBlockState(door);
			((DoorBlock) open.getBlock()).setOpen(null, level, open, door, false); // a neighbour passing through shuts it
			shutAt[0] = (int) helper.getTick();
			helper.assertTrue(stuck(level, villager), "the door shut beside the villager, not on it: nothing to test");
		});
		helper.onEachTick(() -> {
			if (problem[0] == null && shutAt[0] >= 0 && helper.getTick() > shutAt[0] + LET_OUT_BY && stuck(level, villager)) {
				problem[0] = "B185: the villager is still inside the shut " + kind.getName().getString() + " " + (helper.getTick() - shutAt[0])
					+ " ticks after it was shut, at " + String.format("%.2f %.2f %.2f", villager.getX(), villager.getY(), villager.getZ());
			}
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertFalse(stuck(level, villager), "B185: the villager is inside the door");
			helper.assertTrue(villager.isAlive(), "the villager died");
			villager.discard();
			helper.succeed();
		});
	}

	/** A villager in a tight pen (walls on all sides, a slab floor) presses on the walls but is never moved out of it. */
	//$ gametest_ticks_batch AREA '400' '"b185Penned"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b185Penned")
	public void b185AVillagerOnlyPennedInIsNeverMoved(GameTestHelper helper) {
		Leftovers.clear(helper);
		pen(helper, CELL, true);
		helper.setBlock(CELL, Blocks.STONE_SLAB);
		Villager villager = helper.spawn(EntityType.VILLAGER, CELL.above());
		BlockPos cell = helper.absolutePos(CELL);
		helper.onEachTick(() -> {
			if (villager.getBlockX() != cell.getX() || villager.getBlockZ() != cell.getZ() || villager.getY() < cell.getY() + 0.4) {
				throw new GameTestAssertException("B185: a villager only penned in was moved, to " + helper.relativePos(villager.blockPosition()).toShortString());
			}
		});
		helper.runAfterDelay(200, () -> {
			villager.discard();
			helper.succeed();
		});
	}

	/** A villager stands on the roof where the hut's lantern goes: the builder never puts the lantern down on it. */
	//$ gametest_ticks_batch BIG_AREA '3600' '"b185LanternOnVillager"'
	@GameTest(template = BIG_AREA, timeoutTicks = 3600, batch = "b185LanternOnVillager")
	public void b185ALanternIsNeverPutDownOnAVillagerWhereItGoes(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		ResourceLocation hut = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut_2");
		BuildSite site = Builders.start(level, builder, null, hut,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException(hut + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		BuildPlan.Step lantern = null;
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				if (step.state().is(Blocks.LANTERN)) {
					lantern = step;
				}
			}
		}
		if (lantern == null) {
			throw new GameTestAssertException("test_hut_2 has no lantern");
		}
		BlockPos at = lantern.pos();
		Villager[] other = {null};
		int[] builtAt = {-1};
		String[] problem = {null};
		Leftovers.after(helper, () -> {
			if (other[0] != null) {
				other[0].discard();
			}
		});
		helper.onEachTick(() -> {
			if (other[0] == null && level.getBlockState(at).isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
				Villager v = helper.spawn(EntityType.VILLAGER, helper.relativePos(at));
				v.setNoAi(true); // stays where it is put until somebody moves it
				v.setNoGravity(true);
				v.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				other[0] = v;
			}
			if (other[0] == null || !other[0].isAlive()) {
				return;
			}
			if (problem[0] == null && level.getBlockState(at).is(Blocks.LANTERN) && stuck(level, other[0])) {
				problem[0] = "B185: the lantern was put down on the villager standing where it goes, at tick " + helper.getTick();
			}
			if (builtAt[0] < 0 && level.getBlockState(at).is(Blocks.LANTERN)) {
				builtAt[0] = (int) helper.getTick();
			}
			if (builtAt[0] >= 0 && helper.getTick() > builtAt[0] + 40) {
				other[0].discard();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(other[0] != null, "nobody was stood on the lantern's spot: stage " + site.stage());
			helper.assertTrue(builtAt[0] >= 0, "the lantern isn't built yet: stage " + site.stage() + ", " + site.detail());
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: stage " + site.stage());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
		});
	}

	// --- helpers ---------------------------------------------------------------------------

	/** Stone walls around {@code cell} on all four sides, one or two high. */
	private static void pen(GameTestHelper helper, BlockPos cell, boolean twoHigh) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dy = 0; dy < (twoHigh ? 2 : 1); dy++) {
				helper.setBlock(cell.relative(d).above(dy), Blocks.STONE);
				helper.setBlock(cell.relative(d).relative(d.getClockWise()).above(dy), Blocks.STONE);
			}
		}
	}

	/** What the showcase's check sees: the villager's body, a tenth of a block in from its edges, in a block. */
	private static boolean stuck(ServerLevel level, Villager villager) {
		AABB body = villager.getBoundingBox().deflate(0.1);
		if (level.noCollision(villager, body)) {
			return false;
		}
		for (BlockPos p : BlockPos.betweenClosed(Mth.floor(body.minX), Mth.floor(body.minY), Mth.floor(body.minZ),
				Mth.floor(body.maxX), Mth.floor(body.maxY), Mth.floor(body.maxZ))) {
			BlockState state = level.getBlockState(p);
			if (state.getCollisionShape(level, p).isEmpty() || !state.getCollisionShape(level, p).bounds().move(p).intersects(body)) {
				continue;
			}
			if (!(state.getBlock() instanceof DoorBlock) || !state.getValue(DoorBlock.OPEN)) {
				return true; // (a swung-open door it brushes doesn't count, as in the showcase)
			}
		}
		return false;
	}
}
