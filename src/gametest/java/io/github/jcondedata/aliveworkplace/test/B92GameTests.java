package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * B92: in the soak a builder stood inside a closed door (both halves in its column) for three checks in a row. A builder
 * puts a door or a bed down as two blocks and only looked for somebody in the way of the first; and a builder standing
 * still in a doorway stayed there when the door was shut on it. Here: nobody, the builder or another villager, is ever
 * inside a door or a bed a builder puts down, a builder steps out of a door shut on it (also when the world is saved
 * and loaded while it does), a villager merely next to a block is left alone, and the builds still finish.
 */
public class B92GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BUILDER = new BlockPos(3, 2, 3);
	/** The hut stands at x 6..10, z 6..10; its door is in the middle of the z = 6 side, one block up. */
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);
	private static final BlockPos COTTAGE_ORIGIN = new BlockPos(9, 2, 9);
	/** In front of the hut's door: a builder waiting for materials within three blocks of its bench stands still. */
	private static final BlockPos DOOR_BENCH = new BlockPos(8, 2, 4);
	private static final BlockPos DOOR_CHEST = new BlockPos(8, 2, 2);
	private static final BlockPos DOOR_BUILDER = new BlockPos(9, 2, 3);
	/** A second builder's bench, close enough to the first to join its build. */
	private static final BlockPos MATE_BENCH = new BlockPos(2, 2, 5);
	private static final BlockPos MATE = new BlockPos(3, 2, 6);

	private record Started(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan) {
	}

	/** (a) The builder itself stands in the doorway, on the line the door goes on, when the door's turn comes. */
	//$ gametest_ticks_batch AREA '2400' '"b92BuilderInDoorway"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b92BuilderInDoorway")
	public void b92ABuilderStandingInTheDoorwayIsNeverInsideItsDoor(GameTestHelper helper) {
		Started s = startFree(helper, TEST_HUT, HUT_ORIGIN);
		BuildPlan.Step door = twoBlockStep(s.plan(), DoorBlock.class);
		Vec3 line = middleOf(s.level(), door.state(), door.pos());
		int[] stoodAt = {-1};
		String[] problem = {null};
		helper.onEachTick(() -> {
			if (stoodAt[0] < 0 && s.site().stage() == BuildPlan.Stage.DECORATION) {
				stoodAt[0] = (int) helper.getTick();
				if (!s.level().getBlockState(door.pos()).isAir()) {
					problem[0] = "the door was up before the builder could be stood in its doorway: " + s.level().getBlockState(door.pos());
				}
				standStill(s.builder(), line.x, door.pos().getY(), line.z);
			}
			if (problem[0] == null) {
				problem[0] = inClosedDoor(s.level(), s.builder(), helper);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(stoodAt[0] >= 0, "the build hasn't reached its door yet: stage " + s.site().stage());
			assertBuilt(helper, s);
			helper.assertTrue(s.level().getBlockState(door.pos()).getBlock() instanceof DoorBlock
				&& s.level().getBlockState(door.secondaryPos()).getBlock() instanceof DoorBlock, "the door wasn't built: " + s.level().getBlockState(door.pos()));
		});
	}

	/** (b) Another villager stands in the doorway, on the line the door goes on: it is moved, then the door goes in. */
	//$ gametest_ticks_batch AREA '2400' '"b92VillagerInDoorway"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b92VillagerInDoorway")
	public void b92ADoorIsNeverPutDownOnAVillagerInTheDoorway(GameTestHelper helper) {
		doorWithSomebodyInTheWay(helper, false);
	}

	/**
	 * (b) Another villager is where only the door's top half goes (a small one in the air there, as one jumping through
	 * would be): the top half went down unchecked, onto it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"b92VillagerAtDoorTop"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b92VillagerAtDoorTop")
	public void b92ADoorIsNeverPutDownOnAVillagerAtItsTopHalf(GameTestHelper helper) {
		doorWithSomebodyInTheWay(helper, true);
	}

	private void doorWithSomebodyInTheWay(GameTestHelper helper, boolean top) {
		Started s = startFree(helper, TEST_HUT, HUT_ORIGIN);
		BuildPlan.Step door = twoBlockStep(s.plan(), DoorBlock.class);
		Vec3 line = middleOf(s.level(), door.state(), door.pos());
		Villager[] other = {null};
		int[] builtAt = {-1};
		String[] problem = {null};
		Leftovers.after(helper, () -> {
			if (other[0] != null) {
				other[0].discard();
			}
		});
		helper.onEachTick(() -> {
			if (other[0] == null && s.site().stage() == BuildPlan.Stage.DECORATION) {
				if (!s.level().getBlockState(door.pos()).isAir()) {
					problem[0] = "the door was up before a villager could be put in its way: " + s.level().getBlockState(door.pos());
				}
				other[0] = bystander(helper, top, line.x, door.pos().getY() + (top ? 1 : 0), line.z);
			}
			if (other[0] == null || !other[0].isAlive()) {
				return;
			}
			if (problem[0] == null) {
				problem[0] = inClosedDoor(s.level(), other[0], helper);
			}
			if (builtAt[0] < 0 && s.level().getBlockState(door.pos()).getBlock() instanceof DoorBlock) {
				builtAt[0] = (int) helper.getTick();
			}
			if (builtAt[0] >= 0 && helper.getTick() > builtAt[0] + 40) {
				other[0].discard(); // it has done its part; the rest of the build goes on without it
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(builtAt[0] >= 0, "the door isn't built yet: stage " + s.site().stage() + ", " + s.site().detail());
			assertBuilt(helper, s);
			helper.assertTrue(s.level().getBlockState(door.secondaryPos()).getBlock() instanceof DoorBlock, "the door has no top half");
		});
	}

	/**
	 * A villager stands where a bed's head goes: the head went down unchecked, with the villager in it. Also the check
	 * that an ordinary house with a door and beds still gets finished.
	 */
	//$ gametest_ticks_batch BIG_AREA '9000' '"b92VillagerAtBedHead"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "b92VillagerAtBedHead")
	public void b92ABedIsNeverPutDownOnAVillagerStandingAtItsHead(GameTestHelper helper) {
		Started s = startFree(helper, StarterBlueprints.STARTER_COTTAGE.id(), COTTAGE_ORIGIN);
		ServerLevel level = s.level();
		Villager[] other = {null};
		BuildPlan.Step[] bed = {null};
		int[] builtAt = {-1};
		String[] problem = {null};
		Leftovers.after(helper, () -> {
			if (other[0] != null) {
				other[0].discard();
			}
		});
		helper.onEachTick(() -> {
			if (other[0] == null && s.site().stage() == BuildPlan.Stage.DECORATION) {
				for (BuildPlan.Step step : s.plan().steps(BuildPlan.Stage.DECORATION)) {
					BlockPos head = step.secondaryPos();
					if (step.state().getBlock() instanceof BedBlock && head != null && level.getBlockState(step.pos()).isAir() && level.getBlockState(head).isAir()
						&& level.getBlockState(head.above()).isAir() && level.getBlockState(head.below()).isFaceSturdy(level, head.below(), net.minecraft.core.Direction.UP)) {
						bed[0] = step;
						break;
					}
				}
				if (bed[0] == null) {
					problem[0] = "the cottage has no bed with room to stand at its head when its decoration starts";
					other[0] = s.builder(); // (nothing more to set up)
					return;
				}
				BlockPos head = bed[0].secondaryPos();
				other[0] = bystander(helper, false, head.getX() + 0.5, head.getY(), head.getZ() + 0.5);
			}
			if (bed[0] == null || other[0] == null || !other[0].isAlive()) {
				return;
			}
			if (problem[0] == null && !level.noCollision(other[0], other[0].getBoundingBox().deflate(0.1))) {
				problem[0] = "B92: a villager is inside " + level.getBlockState(other[0].blockPosition()).getBlock().getName().getString() + " at "
					+ helper.relativePos(other[0].blockPosition()).toShortString() + " at tick " + helper.getTick();
			}
			if (builtAt[0] < 0 && level.getBlockState(bed[0].pos()).getBlock() instanceof BedBlock) {
				builtAt[0] = (int) helper.getTick();
			}
			if (builtAt[0] >= 0 && helper.getTick() > builtAt[0] + 40) {
				other[0].discard();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(builtAt[0] >= 0, "the bed isn't built yet: stage " + s.site().stage() + ", " + s.site().detail());
			assertBuilt(helper, s);
			helper.assertTrue(level.getBlockState(bed[0].secondaryPos()).getBlock() instanceof BedBlock, "the bed has no head");
		});
	}

	/**
	 * A builder waiting for a torch by its bench stands in its hut's open doorway, and the door is shut on it (a
	 * neighbour passing through, a player): it walks out of the door instead of standing in it, and finishes the hut
	 * when the torch comes.
	 */
	//$ gametest_ticks_batch AREA '3600' '"b92DoorShutOnBuilder"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "b92DoorShutOnBuilder")
	public void b92ABuilderStepsOutOfADoorShutOnIt(GameTestHelper helper) {
		doorShutOnTheBuilder(helper, false);
	}

	/**
	 * The same, and the world is saved and loaded again just as the builder starts to step out (its chunk unloads): the
	 * step out isn't saved, so the builder loaded from the save must find itself in the door again and still get out.
	 */
	//$ gametest_ticks_batch AREA '3600' '"b92DoorShutThenReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "b92DoorShutThenReload")
	public void b92ABuilderReloadedWhileSteppingOutOfADoorStillGetsOut(GameTestHelper helper) {
		doorShutOnTheBuilder(helper, true);
	}

	private void doorShutOnTheBuilder(GameTestHelper helper, boolean reload) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(DOOR_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(DOOR_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(DOOR_CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR)); // everything but the torch
		Villager[] builder = {helper.spawn(EntityType.VILLAGER, DOOR_BUILDER)};
		Builders.employ(level, builder[0], helper.absolutePos(DOOR_BENCH));
		Started s = started(helper, builder[0], TEST_HUT, HUT_ORIGIN);
		BuildPlan.Step door = twoBlockStep(s.plan(), DoorBlock.class);
		Vec3 line = middleOf(level, door.state(), door.pos());
		int[] stoodAt = {-1};
		int[] shutAt = {-1};
		int[] reloadedAt = {-1};
		boolean[] torchGiven = {false};
		String[] problem = {null};
		Leftovers.after(helper, () -> builder[0].discard());
		helper.onEachTick(() -> {
			int tick = (int) helper.getTick();
			if (stoodAt[0] < 0 && s.site().stage() == BuildPlan.Stage.DECORATION && s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				// The door is in and open (put in by hand if the torch's turn came first), the builder in its doorway.
				if (!(level.getBlockState(door.pos()).getBlock() instanceof DoorBlock)) {
					level.setBlock(door.pos(), door.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					level.setBlock(door.secondaryPos(), door.secondaryState(), Block.UPDATE_ALL);
				}
				BlockState lower = level.getBlockState(door.pos());
				((DoorBlock) lower.getBlock()).setOpen(null, level, lower, door.pos(), true);
				standStill(builder[0], line.x, door.pos().getY(), line.z);
				stoodAt[0] = tick;
			} else if (stoodAt[0] >= 0 && shutAt[0] < 0 && tick > stoodAt[0]) {
				BlockState lower = level.getBlockState(door.pos());
				((DoorBlock) lower.getBlock()).setOpen(null, level, lower, door.pos(), false);
				shutAt[0] = tick;
				if (inClosedDoor(level, builder[0], helper) == null) {
					problem[0] = "the door didn't shut on the builder: it is at " + builder[0].position() + ", the door at " + door.pos().toShortString();
				} else if (BuilderWork.insideBlock(level, builder[0]) == null) {
					problem[0] = "the builder doesn't notice the door shut on it: it is at " + builder[0].position();
				}
			} else if (reload && shutAt[0] >= 0 && reloadedAt[0] < 0 && tick >= shutAt[0] + 2) {
				// Saved and loaded as its chunk does it: a new villager, whose work starts from nothing.
				Villager old = builder[0];
				CompoundTag tag = new CompoundTag();
				if (!old.save(tag)) {
					problem[0] = "the builder wasn't saved";
					reloadedAt[0] = tick;
					return;
				}
				old.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				if (!(loaded instanceof Villager again)) {
					problem[0] = "the builder's save didn't load as a villager: " + loaded;
					reloadedAt[0] = tick;
					return;
				}
				level.addFreshEntity(again);
				builder[0] = again;
				reloadedAt[0] = tick;
			} else if (shutAt[0] >= 0 && problem[0] == null && tick >= (reload ? Math.max(reloadedAt[0], shutAt[0]) + 40 : shutAt[0] + 30)
				&& (!reload || reloadedAt[0] >= 0)) {
				// Well inside the showcase's three checks a second apart, and from then on.
				String in = inClosedDoor(level, builder[0], helper);
				if (in != null) {
					problem[0] = in + ", " + (tick - shutAt[0]) + " ticks after it was shut on the builder" + (reload ? " (reloaded at " + reloadedAt[0] + ")" : "");
				}
			}
			if (shutAt[0] >= 0 && tick >= shutAt[0] + 60 && !torchGiven[0]) {
				torchGiven[0] = true;
				chest.setItem(5, new ItemStack(Items.TORCH));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(torchGiven[0], "the builder isn't waiting for its torch yet: stage " + s.site().stage() + " status " + s.site().status());
			helper.assertTrue(!reload || reloadedAt[0] >= 0, "the builder wasn't reloaded");
			assertBuilt(helper, new Started(level, builder[0], s.site(), s.plan()));
		});
	}

	/**
	 * A crewmate (a second builder, helping with the hut) stands in the doorway, on the line the door goes on, when the
	 * door's turn comes. Whoever of the two puts the door in, neither is ever inside it, and the hut gets finished.
	 */
	//$ gametest_ticks_batch AREA '2400' '"b92CrewmateInDoorway"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b92CrewmateInDoorway")
	public void b92ADoorIsNeverPutDownOnACrewmateInTheDoorway(GameTestHelper helper) {
		Started s = startFree(helper, TEST_HUT, HUT_ORIGIN);
		ServerLevel level = s.level();
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer()));
		helper.setBlock(MATE_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, MATE);
		Builders.employ(level, mate, helper.absolutePos(MATE_BENCH));
		// (it joins by itself, as in play: an idle builder looks for a build to help with every two seconds)
		BuildPlan.Step door = twoBlockStep(s.plan(), DoorBlock.class);
		Vec3 line = middleOf(level, door.state(), door.pos());
		int[] stoodAt = {-1};
		String[] problem = {null};
		helper.onEachTick(() -> {
			if (stoodAt[0] < 0 && s.site().stage() == BuildPlan.Stage.DECORATION) {
				stoodAt[0] = (int) helper.getTick();
				if (!level.getBlockState(door.pos()).isAir()) {
					problem[0] = "the door was up before the crewmate could be stood in its doorway: " + level.getBlockState(door.pos());
				} else if (!mate.isAlive() || !Builders.isHelping(mate)) {
					problem[0] = "the second builder had left the crew before the door's turn came";
				}
				standStill(mate, line.x, door.pos().getY(), line.z);
			}
			if (problem[0] == null && mate.isAlive()) {
				problem[0] = inClosedDoor(level, mate, helper);
			}
			if (problem[0] == null) {
				problem[0] = inClosedDoor(level, s.builder(), helper);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(stoodAt[0] >= 0, "the build hasn't reached its door yet: stage " + s.site().stage());
			assertBuilt(helper, s);
			helper.assertTrue(level.getBlockState(door.pos()).getBlock() instanceof DoorBlock
				&& level.getBlockState(door.secondaryPos()).getBlock() instanceof DoorBlock, "the door wasn't built: " + level.getBlockState(door.pos()));
		});
	}

	/**
	 * What counts as standing inside a block, and what doesn't: a villager on the floor, flush against a wall or a fence
	 * post, on a slab or in the middle of an open doorway is where it may be and must be left alone; one on the line of
	 * a shut door or in a solid block is inside it.
	 */
	//$ gametest_ticks AREA '40'
	@GameTest(template = AREA, timeoutTicks = 40)
	public void b92OnlyAVillagerInsideABlockCountsAsInsideOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos wall = new BlockPos(4, 2, 4);
		BlockPos slab = new BlockPos(2, 2, 2);
		BlockPos fence = new BlockPos(6, 2, 2);
		BlockPos doorAt = new BlockPos(3, 2, 6);
		helper.setBlock(wall, Blocks.STONE);
		helper.setBlock(wall.above(), Blocks.STONE);
		helper.setBlock(slab, Blocks.STONE_SLAB);
		helper.setBlock(fence, Blocks.OAK_FENCE);
		BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		BlockPos doorAbs = helper.absolutePos(doorAt);
		level.setBlock(doorAbs, lower, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(doorAbs.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 6));
		villager.setNoAi(true);
		villager.setNoGravity(true);
		double half = villager.getBbWidth() / 2;

		notInside(helper, villager, new Vec3(2.5, 2, 6.5), "standing on the floor");
		notInside(helper, villager, new Vec3(wall.getX() - half, 2, wall.getZ() + 0.5), "flush against a wall");
		notInside(helper, villager, new Vec3(wall.getX() + 1 + half, 2, wall.getZ() + 1 + half), "at a wall's corner");
		notInside(helper, villager, new Vec3(slab.getX() + 0.5, 2.5, slab.getZ() + 0.5), "standing on a slab");
		notInside(helper, villager, new Vec3(fence.getX() + 0.375 - half, 2, fence.getZ() + 0.5), "flush against a fence post");
		notInside(helper, villager, new Vec3(fence.getX() + 0.5, 3.5, fence.getZ() + 0.5), "standing on a fence post");

		// The shut door: on its line the villager is inside it; in the middle of the doorway it is only if the door is thick enough to reach it.
		Vec3 line = middleOf(level, level.getBlockState(doorAbs), doorAbs);
		villager.teleportTo(line.x, doorAbs.getY(), line.z);
		BlockPos in = BuilderWork.insideBlock(level, villager);
		helper.assertTrue(in != null && in.getX() == doorAbs.getX() && in.getZ() == doorAbs.getZ() && level.getBlockState(in).getBlock() instanceof DoorBlock,
			"a villager on the line of a shut door should be inside the door, was inside " + (in == null ? "nothing" : level.getBlockState(in).toString()));
		BlockState shut = level.getBlockState(doorAbs);
		((DoorBlock) shut.getBlock()).setOpen(null, level, shut, doorAbs, true);
		helper.assertTrue(level.getBlockState(doorAbs.above()).getValue(DoorBlock.OPEN), "the door's top half didn't open with it");
		notInside(helper, villager, new Vec3(doorAt.getX() + 0.5, 2, doorAt.getZ() + 0.5), "in the middle of an open doorway");

		// Above the door's top half only (a small villager in the air there): the top half counts as much as the bottom.
		BlockState open = level.getBlockState(doorAbs);
		((DoorBlock) open.getBlock()).setOpen(null, level, open, doorAbs, false);
		villager.setBaby(true);
		villager.teleportTo(line.x, doorAbs.getY() + 1.2, line.z);
		in = BuilderWork.insideBlock(level, villager);
		helper.assertTrue(doorAbs.above().equals(in), "a villager in a shut door's top half should be inside it, was inside " + in);
		villager.setBaby(false);

		Vec3 inWall = helper.absoluteVec(new Vec3(wall.getX() + 0.5, 2, wall.getZ() + 0.5));
		villager.teleportTo(inWall.x, inWall.y, inWall.z);
		in = BuilderWork.insideBlock(level, villager);
		helper.assertTrue(in != null && level.getBlockState(in).is(Blocks.STONE), "a villager in a stone wall should be inside it, was inside " + in);
		villager.discard();
		helper.succeed();
	}

	private static void notInside(GameTestHelper helper, Villager villager, Vec3 relative, String what) {
		Vec3 at = helper.absoluteVec(relative);
		villager.teleportTo(at.x, at.y, at.z);
		BlockPos in = BuilderWork.insideBlock(helper.getLevel(), villager);
		helper.assertTrue(in == null, "a villager " + what + " isn't inside a block, but counted as inside "
			+ (in == null ? "" : helper.getLevel().getBlockState(in) + " at " + helper.relativePos(in).toShortString()));
	}

	// --- helpers ---------------------------------------------------------------------------

	/** A builder at its bench with free materials, starting {@code blueprint}. Alone in its batch. */
	private static Started startFree(GameTestHelper helper, ResourceLocation blueprint, BlockPos origin) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		return started(helper, builder, blueprint, origin);
	}

	private static Started started(GameTestHelper helper, Villager builder, ResourceLocation blueprint, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, blueprint, placement);
		if (site == null) {
			throw new GameTestAssertException(blueprint + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for " + blueprint);
		}
		return new Started(level, builder, site, plan);
	}

	/** The plan's first two-block step of a kind (a door, a bed). */
	private static BuildPlan.Step twoBlockStep(BuildPlan plan, Class<? extends Block> kind) {
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				if (kind.isInstance(step.state().getBlock()) && step.secondaryPos() != null && step.secondaryState() != null) {
					if (stage != BuildPlan.Stage.DECORATION) {
						throw new GameTestAssertException("expected the " + kind.getSimpleName() + " among the decoration, found it in " + stage);
					}
					return step;
				}
			}
		}
		throw new GameTestAssertException("the blueprint has no " + kind.getSimpleName());
	}

	/** The middle of the block's collision shape in the world: for a shut door, the line it stands on. */
	private static Vec3 middleOf(ServerLevel level, BlockState state, BlockPos pos) {
		return state.getCollisionShape(level, pos).bounds().move(pos).getCenter();
	}

	/** Puts a villager somewhere and has it stand there (until its work sends it elsewhere). */
	private static void standStill(Villager villager, double x, double y, double z) {
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.teleportTo(x, y, z);
		villager.setDeltaMovement(Vec3.ZERO);
	}

	/**
	 * A villager that stays where it is put until somebody moves it (no AI, so it neither wanders off nor falls); a small
	 * one fits in the single block of a door's top half.
	 */
	private static Villager bystander(GameTestHelper helper, boolean small, double x, double y, double z) {
		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(BlockPos.containing(x, y, z)));
		villager.setNoAi(true);
		villager.setNoGravity(true);
		if (small) {
			villager.setBaby(true);
		}
		villager.teleportTo(x, y, z);
		return villager;
	}

	/** What the showcase's check sees (B92): the villager's body, a tenth of a block in from its edges, in a shut door. */
	private static String inClosedDoor(ServerLevel level, Villager villager, GameTestHelper helper) {
		AABB body = villager.getBoundingBox().deflate(0.1);
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ), BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
			BlockState state = level.getBlockState(p);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.OPEN)) {
				continue;
			}
			for (AABB part : state.getCollisionShape(level, p).toAabbs()) {
				if (part.move(p).intersects(body)) {
					return "B92: a villager is inside the shut door at " + helper.relativePos(p).toShortString() + " (it is at "
						+ String.format("%.2f %.2f %.2f", villager.getX(), villager.getY(), villager.getZ()) + ") at tick " + helper.getTick();
				}
			}
		}
		return null;
	}

	private static void assertBuilt(GameTestHelper helper, Started s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage=" + s.site().stage() + " status=" + s.site().status()
			+ " progress=" + Math.round(s.site().progress(s.plan()) * 100) + "% builder at " + helper.relativePos(s.builder().blockPosition()).toShortString());
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build" + (unfinished.isEmpty() ? "" : ", e.g. "
			+ helper.relativePos(unfinished.get(0)).toShortString() + "=" + s.level().getBlockState(unfinished.get(0))));
	}
}
