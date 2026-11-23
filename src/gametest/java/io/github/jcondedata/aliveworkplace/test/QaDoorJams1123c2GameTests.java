package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane C, 2026-11-23: B1512 ("Stop villagers jamming on the way to work"), tested from its spec. Villagers pressed
 * together on one doorstep get through at any time of day, a walker held up by a villager who stands still shoulders
 * past, and nobody is moved who isn't walking. A walled yard (x 8..20, z 4..10) with the doorway at (14, 2, 10) into a
 * room (z 11..19); three smiths' blocks (or two beds) at the room's back wall. Every walker is watched for a jump of
 * more than 1.5 blocks in one tick (a teleport, not a step).
 */
public class QaDoorJams1123c2GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int DOOR_X = 14;
	private static final int WALL_Z = 10;
	/** Work blocks at the back of the room: blast furnace, smithing table, grindstone. */
	private static final BlockPos[] BLOCKS = {new BlockPos(11, 2, 18), new BlockPos(14, 2, 18), new BlockPos(17, 2, 18)};
	private static final VillagerProfession[] SMITHS = {VillagerProfession.ARMORER, VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH};
	/** Pressed together on the doorstep (14, 2, 9), two abreast and one behind, as the Smithy II's traces had them. */
	private static final double[][] STEP = {{14.3, 9.6}, {14.75, 9.55}, {14.5, 9.05}};
	private static final int LIMIT = 900;

	// --- the yard and room ---------------------------------------------------------------------------------------

	private static void room(GameTestHelper helper, boolean jobs) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 8; x <= 20; x++) {
			for (int z = 4; z <= 20; z++) {
				boolean wall = x == 8 || x == 20 || z == 4 || z == 20 || z == WALL_Z;
				for (int y = 2; y <= 4; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), wall ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		if (jobs) {
			level.setBlock(helper.absolutePos(BLOCKS[0]), Blocks.BLAST_FURNACE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(helper.absolutePos(BLOCKS[1]), Blocks.SMITHING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(helper.absolutePos(BLOCKS[2]), Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR), Block.UPDATE_ALL);
		}
	}

	private static void door(GameTestHelper helper, int x, Block block, DoorHingeSide hinge, boolean open) {
		BlockState s = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, open);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 2, WALL_Z)), s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 3, WALL_Z)), s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
	}

	/** A bed with its foot at {@code foot}, head towards {@code facing}; returns the head (the home POI). */
	private static BlockPos bed(GameTestHelper helper, BlockPos foot, Direction facing) {
		BlockState s = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
		BlockPos f = helper.absolutePos(foot);
		helper.getLevel().setBlock(f, s.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
		helper.getLevel().setBlock(f.relative(facing), s.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
		return f.relative(facing).immutable();
	}

	private static Villager at(GameTestHelper helper, double x, double z) {
		BlockPos zero = helper.absolutePos(BlockPos.ZERO);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos((int) x, 2, (int) z));
		v.moveTo(zero.getX() + x, zero.getY() + 2, zero.getZ() + z, 0, 0);
		return v;
	}

	/** A smith of {@code profession} without a work block yet (xp 1, so he keeps his trade while he looks), landed. */
	private static Villager smith(GameTestHelper helper, VillagerProfession profession, double x, double z) {
		Villager v = at(helper, x, z);
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		v.setVillagerXp(1);
		v.refreshBrain(helper.getLevel());
		v.setOnGround(true); // landed, so his first job search finds its way (B1478)
		v.setDeltaMovement(0, -0.1, 0);
		return v;
	}

	private static Villager sleeper(GameTestHelper helper, BlockPos head, double x, double z) {
		ServerLevel level = helper.getLevel();
		Villager v = at(helper, x, z);
		level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 1);
		v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		return v;
	}

	private static String where(GameTestHelper helper, Villager v) {
		BlockPos zero = helper.absolutePos(BlockPos.ZERO);
		Vec3 p = v.position().subtract(zero.getX(), zero.getY(), zero.getZ());
		return String.format("%.2f, %.2f, %.2f", p.x, p.y, p.z) + " walking to "
			+ v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString()).orElse("nothing");
	}

	/** Fails on any tracked villager moving more than 1.5 blocks in one tick while awake (a teleport). */
	private static void noTeleports(GameTestHelper helper, List<Villager> watched) {
		Map<Villager, Vec3> last = new HashMap<>();
		helper.onEachTick(() -> {
			for (Villager v : watched) {
				if (v.isRemoved() || v.isSleeping()) {
					last.remove(v); // lying down in a bed puts him on it: that's no teleport
					continue;
				}
				Vec3 before = last.put(v, v.position());
				if (before != null && before.distanceTo(v.position()) > 1.5) {
					helper.fail("a villager jumped " + String.format("%.2f", before.distanceTo(v.position())) + " blocks in one tick, to " + where(helper, v));
				}
			}
		});
	}

	/** Each smith in {@code smiths} (by index into {@link #SMITHS}) works at his own block in the room within {@link #LIMIT} ticks. */
	private static void smithsSettle(GameTestHelper helper, List<Villager> smiths, int[] which) {
		noTeleports(helper, smiths);
		helper.onEachTick(() -> {
			StringBuilder why = new StringBuilder();
			for (int i = 0; i < smiths.size(); i++) {
				Villager v = smiths.get(i);
				BlockPos want = helper.absolutePos(BLOCKS[which[i]]);
				BlockPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
				if (!want.equals(site) || v.position().distanceTo(Vec3.atCenterOf(want)) > 2.5) {
					why.append(SMITHS[which[i]]).append(" works at ").append(site == null ? "null" : helper.relativePos(site).toShortString())
						.append(" (potential ").append(v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(g -> helper.relativePos(g.pos()).toShortString()).orElse("none"))
						.append(") at ").append(where(helper, v)).append("; ");
				}
			}
			if (why.isEmpty()) {
				helper.succeed();
			} else if (helper.getTick() > LIMIT) {
				helper.fail("not at their blocks " + LIMIT + " ticks into the working day: " + why);
			}
		});
	}

	private static List<Villager> threeSmithsOnTheStep(GameTestHelper helper) {
		List<Villager> smiths = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			smiths.add(smith(helper, SMITHS[i], STEP[i][0], STEP[i][1]));
		}
		return smiths;
	}

	// --- by day, at work start ---------------------------------------------------------------------------------

	/** Three smiths pressed together on one oak door's step at work start all reach their blocks inside. */
	//$ gametest_ticks_batch HUGE '1100' '"qaDoorJams1123c2Three"'
	@GameTest(template = HUGE, timeoutTicks = 1100, batch = "qaDoorJams1123c2Three")
	public void qaThreeSmithsThroughOneOakDoorAtWorkStart(GameTestHelper helper) {
		room(helper, true);
		door(helper, DOOR_X, Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		helper.setDayTime(2000);
		smithsSettle(helper, threeSmithsOnTheStep(helper), new int[] {0, 1, 2});
	}

	/** The same through an iron door stood open (villagers can't open or close it): it is a door all the same. */
	//$ gametest_ticks_batch HUGE '1100' '"qaDoorJams1123c2Iron"'
	@GameTest(template = HUGE, timeoutTicks = 1100, batch = "qaDoorJams1123c2Iron")
	public void qaThreeSmithsThroughAnOpenIronDoor(GameTestHelper helper) {
		room(helper, true);
		door(helper, DOOR_X, Blocks.IRON_DOOR, DoorHingeSide.LEFT, true);
		helper.setDayTime(2000);
		smithsSettle(helper, threeSmithsOnTheStep(helper), new int[] {0, 1, 2});
	}

	/** A double door: two leaves, two doorways side by side. Nobody is held back by the give-way. */
	//$ gametest_ticks_batch HUGE '1100' '"qaDoorJams1123c2Double"'
	@GameTest(template = HUGE, timeoutTicks = 1100, batch = "qaDoorJams1123c2Double")
	public void qaThreeSmithsThroughADoubleDoor(GameTestHelper helper) {
		room(helper, true);
		door(helper, DOOR_X, Blocks.SPRUCE_DOOR, DoorHingeSide.LEFT, false);
		door(helper, DOOR_X + 1, Blocks.SPRUCE_DOOR, DoorHingeSide.RIGHT, false);
		helper.setDayTime(2000);
		List<Villager> smiths = new ArrayList<>();
		double[][] step = {{14.8, 9.6}, {15.2, 9.6}, {15.0, 9.05}};
		for (int i = 0; i < 3; i++) {
			smiths.add(smith(helper, SMITHS[i], step[i][0], step[i][1]));
		}
		smithsSettle(helper, smiths, new int[] {0, 1, 2});
	}

	/** An open fence gate in the wall, no door: three smiths pressed in it still get through (nobody to step back from). */
	//$ gametest_ticks_batch HUGE '1100' '"qaDoorJams1123c2Gate"'
	@GameTest(template = HUGE, timeoutTicks = 1100, batch = "qaDoorJams1123c2Gate")
	public void qaThreeSmithsThroughAnOpenFenceGate(GameTestHelper helper) {
		room(helper, true);
		ServerLevel level = helper.getLevel();
		level.setBlock(helper.absolutePos(new BlockPos(DOOR_X, 2, WALL_Z)), Blocks.OAK_FENCE_GATE.defaultBlockState()
			.setValue(FenceGateBlock.FACING, Direction.SOUTH).setValue(FenceGateBlock.OPEN, true), Block.UPDATE_CLIENTS);
		level.setBlock(helper.absolutePos(new BlockPos(DOOR_X, 3, WALL_Z)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.setDayTime(2000);
		smithsSettle(helper, threeSmithsOnTheStep(helper), new int[] {0, 1, 2});
	}

	/** A nitwit stands still in the open doorway (he isn't going anywhere): the Armorer shoulders past him to his furnace. */
	//$ gametest_ticks_batch HUGE '1100' '"qaDoorJams1123c2Doorway"'
	@GameTest(template = HUGE, timeoutTicks = 1100, batch = "qaDoorJams1123c2Doorway")
	public void qaAnArmorerGetsPastANitwitStoodInTheDoorway(GameTestHelper helper) {
		room(helper, true);
		door(helper, DOOR_X, Blocks.OAK_DOOR, DoorHingeSide.LEFT, true);
		helper.setDayTime(2000);
		Villager nitwit = at(helper, 14.5, 10.5);
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		nitwit.setNoAi(true);
		Villager armorer = smith(helper, VillagerProfession.ARMORER, 14.5, 9.4);
		noTeleports(helper, List.of(nitwit));
		smithsSettle(helper, List.of(armorer), new int[] {0});
	}

	/** The door is broken under them mid-jam, then a spruce door put in its place: they still all get through. */
	//$ gametest_ticks_batch HUGE '1100' '"qaDoorJams1123c2Broken"'
	@GameTest(template = HUGE, timeoutTicks = 1100, batch = "qaDoorJams1123c2Broken")
	public void qaTheDoorBrokenAndReplacedMidJamStillLetsThemIn(GameTestHelper helper) {
		room(helper, true);
		door(helper, DOOR_X, Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		helper.setDayTime(2000);
		List<Villager> smiths = threeSmithsOnTheStep(helper);
		helper.runAfterDelay(30, () -> {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(DOOR_X, 3, WALL_Z)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(DOOR_X, 2, WALL_Z)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		});
		helper.runAfterDelay(60, () -> door(helper, DOOR_X, Blocks.SPRUCE_DOOR, DoorHingeSide.RIGHT, false));
		smithsSettle(helper, smiths, new int[] {0, 1, 2});
	}

	/** Saved and loaded mid-jam (each smith replaced by his own saved copy, as a chunk reload does): all still get through. */
	//$ gametest_ticks_batch HUGE '1100' '"qaDoorJams1123c2Reload"'
	@GameTest(template = HUGE, timeoutTicks = 1100, batch = "qaDoorJams1123c2Reload")
	public void qaSmithsReloadedMidJamStillGetThrough(GameTestHelper helper) {
		room(helper, true);
		door(helper, DOOR_X, Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		helper.setDayTime(2000);
		List<Villager> smiths = threeSmithsOnTheStep(helper);
		helper.runAfterDelay(30, () -> {
			ServerLevel level = helper.getLevel();
			for (int i = 0; i < smiths.size(); i++) {
				Villager old = smiths.get(i);
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(old.save(tag), "(setup) a smith wouldn't save");
				old.discard();
				Entity copy = EntityType.create(tag, level).orElseThrow();
				helper.assertTrue(level.addFreshEntity(copy), "(setup) a saved smith wouldn't load back");
				smiths.set(i, (Villager) copy);
			}
		});
		smithsSettle(helper, smiths, new int[] {0, 1, 2});
	}

	// --- by night, to bed ----------------------------------------------------------------------------------------

	/** An adult and a baby pressed on the doorstep at bedtime: both are through the door and keep their beds. */
	//$ gametest_ticks_batch HUGE '900' '"qaDoorJams1123c2Baby"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qaDoorJams1123c2Baby")
	public void qaAnAdultAndABabyThroughOneDoorToBed(GameTestHelper helper) {
		room(helper, false);
		door(helper, DOOR_X, Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		helper.setDayTime(2000);
		BlockPos[] heads = {bed(helper, new BlockPos(11, 2, 17), Direction.SOUTH), bed(helper, new BlockPos(17, 2, 17), Direction.SOUTH)};
		Villager adult = sleeper(helper, heads[0], 14.5, 9.1);
		Villager baby = sleeper(helper, heads[1], 14.6, 9.7);
		baby.setAge(-24000);
		helper.setDayTime(13000);
		List<Villager> both = List.of(adult, baby);
		throughToBed(helper, both, heads, 700);
	}

	/**
	 * A villager already asleep in a bed just inside the door, while two others jam on the step on their way to the beds
	 * at the back: the sleeper isn't moved or woken (he isn't walking), and both walkers get in.
	 */
	//$ gametest_ticks_batch HUGE '900' '"qaDoorJams1123c2Sleeper"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qaDoorJams1123c2Sleeper")
	public void qaASleeperByTheDoorIsNotMovedWhileTwoGetPast(GameTestHelper helper) {
		room(helper, false);
		door(helper, DOOR_X, Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		helper.setDayTime(13000);
		BlockPos near = bed(helper, new BlockPos(12, 2, 11), Direction.EAST);
		BlockPos[] heads = {bed(helper, new BlockPos(11, 2, 17), Direction.SOUTH), bed(helper, new BlockPos(17, 2, 17), Direction.SOUTH)};
		Villager asleep = sleeper(helper, near, 13.5, 11.5);
		asleep.startSleeping(near);
		Vec3[] lying = {null};
		Villager a = sleeper(helper, heads[0], 14.3, 9.6);
		Villager b = sleeper(helper, heads[1], 14.75, 9.55);
		helper.onEachTick(() -> {
			helper.assertTrue(asleep.isSleeping(), "the sleeper by the door was woken, at " + where(helper, asleep));
			if (helper.getTick() < 10) {
				return; // he settles onto the mattress first
			}
			if (lying[0] == null) {
				lying[0] = asleep.position();
			}
			Vec3 now = asleep.position();
			helper.assertTrue(Math.abs(now.x - lying[0].x) < 1.0E-3 && Math.abs(now.z - lying[0].z) < 1.0E-3, "the sleeper by the door was moved, to " + where(helper, asleep));
		});
		throughToBed(helper, List.of(a, b), heads, 700);
	}

	/**
	 * Head on at bedtime: one villager inside whose bed is out in the yard and one outside whose bed is in the room meet in
	 * the open doorway, face to face. Both get to their own side and keep their beds.
	 */
	//$ gametest_ticks_batch HUGE '900' '"qaDoorJams1123c2HeadOn"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qaDoorJams1123c2HeadOn")
	public void qaTwoMeetingHeadOnInTheDoorwayBothGetToBed(GameTestHelper helper) {
		room(helper, false);
		door(helper, DOOR_X, Blocks.OAK_DOOR, DoorHingeSide.LEFT, true);
		helper.setDayTime(2000);
		BlockPos out = bed(helper, new BlockPos(14, 2, 6), Direction.NORTH);
		BlockPos in = bed(helper, new BlockPos(14, 2, 17), Direction.SOUTH);
		Villager goingOut = sleeper(helper, out, 14.5, 10.95);
		Villager goingIn = sleeper(helper, in, 14.5, 10.05);
		helper.setDayTime(13000);
		List<Villager> both = List.of(goingOut, goingIn);
		noTeleports(helper, both);
		int wall = helper.absolutePos(BlockPos.ZERO).getZ() + WALL_Z;
		helper.onEachTick(() -> {
			for (Villager v : both) {
				helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.HOME).isPresent(), "a villager gave his bed up, at " + where(helper, v));
			}
			if (goingOut.getBlockZ() < wall - 1 && goingIn.getBlockZ() > wall + 1) {
				helper.succeed();
			} else if (helper.getTick() > 700) {
				helper.fail("not past each other 700 ticks after bedtime: going out at " + where(helper, goingOut) + ", going in at " + where(helper, goingIn));
			}
		});
	}

	private static void throughToBed(GameTestHelper helper, List<Villager> walkers, BlockPos[] heads, int limit) {
		noTeleports(helper, walkers);
		int inside = helper.absolutePos(BlockPos.ZERO).getZ() + WALL_Z + 2;
		helper.onEachTick(() -> {
			StringBuilder why = new StringBuilder();
			for (int i = 0; i < walkers.size(); i++) {
				Villager v = walkers.get(i);
				BlockPos head = heads[i];
				boolean kept = v.getBrain().getMemory(MemoryModuleType.HOME).map(g -> g.pos().equals(head)).orElse(false);
				if (!kept) {
					helper.fail("a villager gave his bed up, at " + where(helper, v));
				}
				if (v.getBlockZ() < inside) {
					why.append(v.isBaby() ? "the baby" : "a villager").append(" at ").append(where(helper, v)).append(", can't reach since ")
						.append(v.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).map(String::valueOf).orElse("-")).append("; ");
				}
			}
			if (why.isEmpty()) {
				helper.succeed();
			} else if (helper.getTick() > limit) {
				helper.fail("not through the door " + limit + " ticks after bedtime: " + why);
			}
		});
	}
}
