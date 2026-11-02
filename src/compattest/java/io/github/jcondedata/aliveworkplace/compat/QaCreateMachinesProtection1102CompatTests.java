package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, Create and the pack, 2026-11-02): Create's machines and a protected village (guide village-protection.md:
 * "only the owner, their friends and operators may break or place blocks", and B745: "a piston outside the village
 * can't push blocks into it, pull them out of it"). A stranger stands outside a protected village, sets a Mechanical
 * Drill down one step past its edge facing in and turns it with a Creative Motor (every click outside, so all allowed):
 * the drill must not break the village's edge block, as the vanilla piston may not move it. The control (the same drill
 * with the hall unprotected) proves the drill would break it, and a drill inside the village still works.
 *
 * <p>The hall is at (3, 2, 8) with the reach shrunk to 4, so on the hall's row x = 7 is the village's last column and
 * x = 8 is outside (VillageProtection counts columns within the reach's circle).
 */
public class QaCreateMachinesProtection1102CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(3, 2, 8);
	private static final BlockPos EDGE = new BlockPos(7, 2, 8);

	/** A hall owned by someone else (not a player in this test), protected or not, the reach shrunk to 4 (put back after). */
	private static VillageHallBlockEntity hall(GameTestHelper helper, boolean protect) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = VillageProtection.ENABLED;
		VillageHalls.RADIUS = 4;
		VillageProtection.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(new UUID(0x1102L, 0xE1L), "Owner");
		hall.setProtected(protect);
		Runnable undo = () -> {
			VillageHalls.RADIUS = radius;
			VillageProtection.ENABLED = enabled;
			if (helper.getBlockEntity(HALL) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
		};
		cleanups.put(helper, undo);
		return hall;
	}

	private static final java.util.Map<GameTestHelper, Runnable> cleanups = new java.util.concurrent.ConcurrentHashMap<>();

	private static void undo(GameTestHelper helper) {
		Runnable r = cleanups.remove(helper);
		if (r != null) {
			r.run();
		}
	}

	/** A drill at {@code drill} facing west, turned by a creative motor just east of it (facing west into the drill's back). */
	private static void westDrill(GameTestHelper helper, BlockPos drill) {
		helper.setBlock(drill.east(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "west"));
		helper.setBlock(drill, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_drill").defaultBlockState(), "facing", "west"));
	}

	private static float speed(GameTestHelper helper, BlockPos pos) {
		BlockEntity be = helper.getBlockEntity(pos);
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	/** The control: the hall unprotected, the stranger's drill outside breaks the village's edge block (the setup works). */
	//$ gametest_ticks_batch AREA '400' '"qa_create_drill_unprotected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_drill_unprotected")
	public void aDrillOutsideAnUnprotectedVillageBreaksItsEdge(GameTestHelper helper) {
		hall(helper, false);
		helper.setBlock(EDGE, Blocks.OAK_PLANKS);
		westDrill(helper, EDGE.east());
		helper.runAfterDelay(300, () -> {
			boolean broken = helper.getBlockState(EDGE).isAir();
			float turning = speed(helper, EDGE.east());
			undo(helper);
			helper.assertTrue(broken, "the drill didn't break the unprotected edge block in 300 ticks (drill speed " + turning + ")");
			helper.succeed();
		});
	}

	/** A stranger's drill one step outside a protected village, facing in, leaves the village's edge block standing. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_drill_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_drill_protected")
	public void aDrillOutsideAProtectedVillageCantBreakItsEdge(GameTestHelper helper) {
		hall(helper, true);
		helper.setBlock(EDGE, Blocks.OAK_PLANKS);
		BlockPos drill = EDGE.east();
		westDrill(helper, drill);
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper, drill);
			boolean kept = helper.getBlockState(EDGE).is(Blocks.OAK_PLANKS);
			undo(helper);
			helper.assertTrue(turning != 0, "the drill never turned, so the test proves nothing");
			helper.assertTrue(kept, "a stranger's Mechanical Drill outside the protected village broke its edge block at "
				+ helper.absolutePos(EDGE) + " (the drill at " + helper.absolutePos(drill) + ", speed " + turning + ")");
			helper.succeed();
		});
	}

	/** The owner's drill inside the protected village still breaks blocks inside it. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_drill_inside"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_drill_inside")
	public void aDrillInsideAProtectedVillageStillWorks(GameTestHelper helper) {
		hall(helper, true);
		BlockPos target = new BlockPos(5, 2, 8);
		helper.setBlock(target, Blocks.OAK_PLANKS);
		westDrill(helper, target.east());
		helper.runAfterDelay(300, () -> {
			boolean broken = helper.getBlockState(target).isAir();
			undo(helper);
			helper.assertTrue(broken, "the owner's drill inside the protected village didn't break the block in front of it in 300 ticks");
			helper.succeed();
		});
	}
	private static net.minecraft.world.level.block.state.BlockState westFacing(String id) {
		return MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create(id).defaultBlockState(), "facing", "west"),
			"axis_along_first", "true");
	}

	/** A stranger's Mechanical Saw one step outside a protected village, facing in, leaves the village's edge log standing. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_saw_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saw_protected")
	public void aSawOutsideAProtectedVillageCantCutItsEdge(GameTestHelper helper) {
		hall(helper, true);
		helper.setBlock(EDGE, Blocks.OAK_LOG);
		BlockPos saw = EDGE.east();
		helper.setBlock(saw.east(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "west"));
		helper.setBlock(saw, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_saw").defaultBlockState(), "facing", "west"));
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper, saw);
			boolean kept = helper.getBlockState(EDGE).is(Blocks.OAK_LOG);
			undo(helper);
			helper.assertTrue(turning != 0, "the saw never turned, so the test proves nothing");
			helper.assertTrue(kept, "a stranger's Mechanical Saw outside the protected village cut its edge log (saw speed " + turning + ")");
			helper.succeed();
		});
	}

	/** The control for the saw: the hall unprotected, the same saw cuts the edge log (the setup works). */
	//$ gametest_ticks_batch AREA '400' '"qa_create_saw_unprotected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saw_unprotected")
	public void aSawOutsideAnUnprotectedVillageCutsItsEdge(GameTestHelper helper) {
		hall(helper, false);
		helper.setBlock(EDGE, Blocks.OAK_LOG);
		BlockPos saw = EDGE.east();
		helper.setBlock(saw.east(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "west"));
		helper.setBlock(saw, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_saw").defaultBlockState(), "facing", "west"));
		helper.runAfterDelay(300, () -> {
			boolean cut = !helper.getBlockState(EDGE).is(Blocks.OAK_LOG);
			float turning = speed(helper, saw);
			undo(helper);
			helper.assertTrue(cut, "the saw didn't cut the unprotected edge log in 300 ticks (saw speed " + turning + ")");
			helper.succeed();
		});
	}

	/** A Mechanical Piston facing west (axis_along_first: it turns on Y) with two poles behind it, turned from below. */
	private static void westPiston(GameTestHelper helper, BlockPos piston) {
		helper.setBlock(piston.east(2), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston.east(), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston, westFacing("mechanical_piston"));
		helper.setBlock(piston.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
	}

	/** A stranger's Mechanical Piston one step outside a protected village can't push the village's edge block deeper in. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_piston_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_piston_protected")
	public void aMechanicalPistonOutsideAProtectedVillageCantPushItsEdgeIn(GameTestHelper helper) {
		hall(helper, true);
		helper.setBlock(EDGE, Blocks.OAK_PLANKS);
		BlockPos piston = EDGE.east();
		westPiston(helper, piston);
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper, piston);
			String row = helper.getBlockState(EDGE) + " / " + helper.getBlockState(EDGE.west()) + " / " + helper.getBlockState(EDGE.west(2));
			boolean kept = helper.getBlockState(EDGE).is(Blocks.OAK_PLANKS);
			undo(helper);
			helper.assertTrue(turning != 0, "the piston never turned, so the test proves nothing");
			helper.assertTrue(kept, "a stranger's Mechanical Piston outside the protected village moved its edge block: the row inward reads " + row);
			helper.succeed();
		});
	}

	/** The control for the piston: the hall unprotected, the same piston pushes the edge block in. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_piston_unprotected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_piston_unprotected")
	public void aMechanicalPistonOutsideAnUnprotectedVillagePushesItsEdgeIn(GameTestHelper helper) {
		hall(helper, false);
		helper.setBlock(EDGE, Blocks.OAK_PLANKS);
		BlockPos piston = EDGE.east();
		westPiston(helper, piston);
		helper.runAfterDelay(300, () -> {
			boolean moved = !helper.getBlockState(EDGE).is(Blocks.OAK_PLANKS);
			String row = helper.getBlockState(EDGE) + " / " + helper.getBlockState(EDGE.west()) + " / " + helper.getBlockState(EDGE.west(2));
			float turning = speed(helper, piston);
			undo(helper);
			helper.assertTrue(moved, "the piston didn't push the unprotected edge block (speed " + turning + "): " + row);
			helper.succeed();
		});
	}
	/**
	 * A Deployer at {@code deployer} facing west (it turns on Y), turned by a creative motor below it, its hand given {@code stack}
	 * once Create has made its fake player (by reflection: the compat tests don't compile against Create).
	 */
	private static void westDeployer(GameTestHelper helper, BlockPos deployer, net.minecraft.world.item.ItemStack stack) {
		helper.setBlock(deployer, westFacing("deployer"));
		helper.setBlock(deployer.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
		helper.runAfterDelay(2, () -> {
			try {
				BlockEntity be = helper.getBlockEntity(deployer);
				Object player = be.getClass().getMethod("getPlayer").invoke(be);
				((net.minecraft.world.entity.player.Player) player).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
			} catch (ReflectiveOperationException e) {
				helper.fail("couldn't give the deployer its item: " + e);
			}
		});
	}

	/** Whether any oak planks stand on the hall's row between the hall and {@code x} (inside the village). */
	private static String planksInside(GameTestHelper helper) {
		StringBuilder at = new StringBuilder();
		for (int x = HALL.getX() + 1; x <= EDGE.getX(); x++) {
			for (int y = 2; y <= 3; y++) {
				if (helper.getBlockState(new BlockPos(x, y, HALL.getZ())).is(Blocks.OAK_PLANKS)) {
					at.append(" ").append(x).append(",").append(y);
				}
			}
		}
		return at.toString();
	}

	/** A stranger's Deployer outside a protected village, holding planks and facing in, puts no block inside. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_deployer_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_deployer_protected")
	public void aDeployerOutsideAProtectedVillagePlacesNothingInside(GameTestHelper helper) {
		hall(helper, true);
		BlockPos deployer = EDGE.east(2);
		westDeployer(helper, deployer, new net.minecraft.world.item.ItemStack(Blocks.OAK_PLANKS, 16));
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper, deployer);
			String inside = planksInside(helper);
			undo(helper);
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			helper.assertTrue(inside.isEmpty(), "a stranger's Deployer outside the protected village placed planks inside it at x,y:" + inside);
			helper.succeed();
		});
	}

	/** The control for the deployer: the hall unprotected, the same deployer places planks in the village. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_deployer_unprotected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_deployer_unprotected")
	public void aDeployerOutsideAnUnprotectedVillagePlacesInside(GameTestHelper helper) {
		hall(helper, false);
		BlockPos deployer = EDGE.east(2);
		westDeployer(helper, deployer, new net.minecraft.world.item.ItemStack(Blocks.OAK_PLANKS, 16));
		helper.runAfterDelay(300, () -> {
			String inside = planksInside(helper);
			String front = helper.getBlockState(EDGE.east()) + " / " + helper.getBlockState(EDGE);
			undo(helper);
			helper.assertTrue(!inside.isEmpty(), "the deployer placed nothing inside the unprotected village; in front of it: " + front);
			helper.succeed();
		});
	}

	/**
	 * A drill on a moving contraption: a stranger's Mechanical Piston two steps outside the protected village with a Drill
	 * on its head, facing in (a classic tunnel bore). The piston carries the drill, and a drill on a contraption breaks what
	 * it moves into (Create's drill movement, not the drill block's own breaking), so this is a second way in.
	 */
	private static void pistonDrill(GameTestHelper helper) {
		BlockPos piston = EDGE.east(2);
		westPiston(helper, piston);
		helper.setBlock(piston.west(), MechanicCompatTests.with(MechanicCompatTests.create("mechanical_drill").defaultBlockState(), "facing", "west"));
	}

	/** A stranger's piston-carried Drill outside a protected village can't bore into its edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_piston_drill_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_piston_drill_protected")
	public void aDrillOnAPistonOutsideAProtectedVillageCantBoreIntoItsEdge(GameTestHelper helper) {
		hall(helper, true);
		helper.setBlock(EDGE, Blocks.OAK_PLANKS);
		pistonDrill(helper);
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper, EDGE.east(2));
			String row = helper.getBlockState(EDGE) + " / " + helper.getBlockState(EDGE.east()) + " / " + helper.getBlockState(EDGE.east(2));
			boolean kept = helper.getBlockState(EDGE).is(Blocks.OAK_PLANKS);
			undo(helper);
			helper.assertTrue(turning != 0, "the piston never turned, so the test proves nothing");
			helper.assertTrue(kept, "a stranger's Drill carried by a Mechanical Piston outside the protected village broke its edge block: the row"
				+ " outward reads " + row);
			helper.succeed();
		});
	}

	/** The control for the piston-carried drill: the hall unprotected, the same bore breaks the edge block. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_piston_drill_unprotected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_piston_drill_unprotected")
	public void aDrillOnAPistonOutsideAnUnprotectedVillageBoresIntoItsEdge(GameTestHelper helper) {
		hall(helper, false);
		helper.setBlock(EDGE, Blocks.OAK_PLANKS);
		pistonDrill(helper);
		helper.runAfterDelay(300, () -> {
			boolean broken = !helper.getBlockState(EDGE).is(Blocks.OAK_PLANKS);
			String row = helper.getBlockState(EDGE) + " / " + helper.getBlockState(EDGE.east()) + " / " + helper.getBlockState(EDGE.east(2));
			float turning = speed(helper, EDGE.east(2));
			undo(helper);
			helper.assertTrue(broken, "the piston-carried drill didn't break the unprotected edge block (piston speed " + turning + "): " + row);
			helper.succeed();
		});
	}
}
