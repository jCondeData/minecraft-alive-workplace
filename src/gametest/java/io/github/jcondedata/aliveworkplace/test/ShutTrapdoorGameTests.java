package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;

/**
 * B156: vanilla's path finder counts every trapdoor as open, shut or not, so it walked villagers into the cell under a
 * window's flower box (a shut top-half trapdoor at head height). The villager's head hit the box and it stood pressed
 * against it for the rest of the day: the Printer coming up to the Print Shop's door, about 1 time in 100. A villager's
 * path now goes round such a trapdoor; a shut trapdoor underfoot is still walked on.
 */
public class ShutTrapdoorGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	private static BlockState trapdoor(Half half) {
		return Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, half).setValue(TrapDoorBlock.OPEN, false);
	}

	/**
	 * A wall three high across the villager's way (x 1 to 11 at z 6), its only gap at x = 6, and the gap's cell at head
	 * height a shut top-half trapdoor: a flower box with nothing under it. The way through the gap is the short one.
	 */
	private static void wallWithGap(GameTestHelper helper, BlockState head, BlockState feet) {
		for (int x = 1; x <= 11; x++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE_BRICKS);
			}
		}
		helper.setBlock(new BlockPos(6, 2, 6), feet);
		helper.setBlock(new BlockPos(6, 3, 6), head);
	}

	private static boolean passes(GameTestHelper helper, Path path, BlockPos cell) {
		BlockPos abs = helper.absolutePos(cell);
		for (int i = 0; i < path.getNodeCount(); i++) {
			if (path.getNodePos(i).equals(abs)) {
				return true;
			}
		}
		return false;
	}

	/** The path from one side of the wall to the other goes round its end, not under the flower box in the gap. */
	//$ gametest_batch AREA '"b156Path"'
	@GameTest(template = AREA, batch = "b156Path")
	public void aVillagersPathGoesRoundAFlowerBoxNotUnderIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		wallWithGap(helper, trapdoor(Half.TOP), Blocks.AIR.defaultBlockState());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		DoorwayHeadroomGameTests.nitwit(helper, villager); // nothing to walk to of its own
		helper.runAfterDelay(10, () -> { // once it stands on the ground: a villager in the air works out no path
			Path path = pathFromStart(helper, villager);
			helper.assertTrue(path != null && path.canReach(), "no path round the wall: " + path);
			helper.assertTrue(!passes(helper, path, new BlockPos(6, 2, 6)), "the path runs under the shut trapdoor at head height, where a villager sticks");
			helper.succeed();
		});
	}

	/** A shut top-half trapdoor at the hips (a shelf in a doorway) is in the way too. */
	//$ gametest_batch AREA '"b156PathHips"'
	@GameTest(template = AREA, batch = "b156PathHips")
	public void aVillagersPathGoesRoundAShelfAtItsHips(GameTestHelper helper) {
		Leftovers.clear(helper);
		wallWithGap(helper, Blocks.AIR.defaultBlockState(), trapdoor(Half.TOP));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		DoorwayHeadroomGameTests.nitwit(helper, villager); // nothing to walk to of its own
		helper.runAfterDelay(10, () -> { // once it stands on the ground: a villager in the air works out no path
			Path path = pathFromStart(helper, villager);
			helper.assertTrue(path != null && path.canReach(), "no path round the wall: " + path);
			helper.assertTrue(!passes(helper, path, new BlockPos(6, 2, 6)), "the path runs through the shut top-half trapdoor at the hips");
			helper.succeed();
		});
	}

	/**
	 * The path from the start, (6, 2, 3), to the far side of the wall. The nitwit strolls in the ten ticks before (up to
	 * 3/4 of a block in 8 runs), so the path came out of wherever it had got to: a full build once saw "Path(length=3)",
	 * three cells from (6, 2, 9), which can only start beyond the gap, where a path has no gap to cross. It is put back
	 * on the start first, so every path is worked out from the same cell.
	 */
	private static Path pathFromStart(GameTestHelper helper, Villager villager) {
		villager.getNavigation().stop();
		BlockPos start = helper.absolutePos(new BlockPos(6, 2, 3));
		villager.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
		villager.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		helper.assertTrue(villager.blockPosition().equals(start), "the villager isn't on its start: " + villager.position());
		return villager.getNavigation().createPath(helper.absolutePos(new BlockPos(6, 2, 9)), 0);
	}

	/** What isn't in the way stays walkable: a shut trapdoor underfoot, and an open one (a shutter) overhead. */
	//$ gametest_batch AREA '"b156PathUnderfoot"'
	@GameTest(template = AREA, batch = "b156PathUnderfoot")
	public void aShutTrapdoorUnderfootOrAnOpenOneOverheadIsStillWalked(GameTestHelper helper) {
		Leftovers.clear(helper);
		wallWithGap(helper, Blocks.AIR.defaultBlockState(), trapdoor(Half.BOTTOM));
		helper.setBlock(new BlockPos(6, 4, 6), Blocks.AIR); // three high: room over the trapdoor's 3/16
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		DoorwayHeadroomGameTests.nitwit(helper, villager); // nothing to walk to of its own
		helper.runAfterDelay(10, () -> { // once it stands on the ground: a villager in the air works out no path
			Path path = pathFromStart(helper, villager);
			helper.assertTrue(path != null && path.canReach() && passes(helper, path, new BlockPos(6, 2, 6)),
				"the path doesn't go over the shut trapdoor in the gap's floor: " + path);
			helper.setBlock(new BlockPos(6, 2, 6), Blocks.AIR);
			helper.setBlock(new BlockPos(6, 3, 6), trapdoor(Half.TOP).setValue(TrapDoorBlock.OPEN, true));
			Path open = pathFromStart(helper, villager);
			helper.assertTrue(open != null && open.canReach() && passes(helper, open, new BlockPos(6, 2, 6)),
				"the path doesn't go under the open trapdoor in the gap: " + open);
			helper.succeed();
		});
	}

	/**
	 * As the player saw it: a Printer in front of a Print Shop (placed from its blueprint, on the ground), standing where
	 * the stuck one stood, at the end of the flower box under the front window. It goes round the box, in by the door,
	 * and takes the cartography table.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"b156PrintShop"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "b156PrintShop")
	public void aPrinterBesideThePrintShopsFlowerBoxWalksRoundItAndTakesTheTable(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "print_shop")).orElseThrow();
		BlockPos at = new BlockPos(8, 2, 8); // the blueprint's y = 0 on top of the floor, as a builder builds it
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(156L), 2);
		BlockPos table = helper.absolutePos(at.offset(3, 1, 5));
		helper.assertTrue(level.getBlockState(table).is(Blocks.CARTOGRAPHY_TABLE), "no cartography table at blueprint (3, 1, 5)");
		helper.assertTrue(level.getBlockState(helper.absolutePos(at.offset(2, 1, 0))).getBlock() instanceof TrapDoorBlock,
			"no flower box at blueprint (2, 1, 0)");
		// blueprint (1, 0, 0): in front of the wall at the flower box's end; the door is at (5, 1, 1), up the step (5, 0, 0)
		Villager printer = helper.spawn(EntityType.VILLAGER, at.offset(1, 0, 0));
		printer.getRandom().setSeed(156L);
		printer.setVillagerData(printer.getVillagerData().setProfession(ModVillagers.PRINTER));
		printer.setVillagerXp(1);
		printer.refreshBrain(level);
		helper.succeedWhen(() -> helper.assertTrue(printer.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).map(table::equals).orElse(false),
			"the Printer hasn't taken the table: at " + helper.relativePos(printer.blockPosition()).toShortString() + " (" + printer.position()
				+ "), job site " + printer.getBrain().getMemory(MemoryModuleType.JOB_SITE) + ", picked "
				+ printer.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE)));
	}

	/**
	 * B412: the path finder's check reads each of the body's cells once for both rules (this one and B339's small blocks
	 * a mod calls passable). It says exactly what the two rules said apart, for every pair of blocks at the feet and head
	 * among trapdoors shut and open, both halves, and the small blocks, steps and doors a village has.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void thePathCheckReadOnceSaysWhatBothRulesSay(GameTestHelper helper) {
		Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		BlockState[] blocks = {Blocks.AIR.defaultBlockState(), trapdoor(Half.TOP), trapdoor(Half.BOTTOM),
			trapdoor(Half.TOP).setValue(TrapDoorBlock.OPEN, true), Blocks.LANTERN.defaultBlockState(), Blocks.CHAIN.defaultBlockState(),
			Blocks.END_ROD.defaultBlockState(), Blocks.WHITE_CARPET.defaultBlockState(), Blocks.COBBLESTONE_WALL.defaultBlockState(),
			Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(), Blocks.BREWING_STAND.defaultBlockState(),
			Blocks.FLOWER_POT.defaultBlockState(), Blocks.CAKE.defaultBlockState(), Blocks.SCAFFOLDING.defaultBlockState(),
			Blocks.STONE_BRICKS.defaultBlockState(), Blocks.OAK_FENCE_GATE.defaultBlockState(), Blocks.HOPPER.defaultBlockState()};
		BlockPos feet = new BlockPos(8, 2, 8);
		BlockPos abs = helper.absolutePos(feet);
		ServerLevel level = helper.getLevel();
		int shut = 0;
		for (BlockState low : blocks) {
			for (BlockState high : blocks) {
				helper.setBlock(feet, low);
				helper.setBlock(feet.above(), high);
				helper.setBlock(feet.above(2), Blocks.AIR);
				boolean apart = io.github.jcondedata.aliveworkplace.work.ShutTrapdoors.inTheWay(level, villager, abs.getX(), abs.getY(), abs.getZ())
					|| io.github.jcondedata.aliveworkplace.work.PathObstacles.inTheWay(level, villager, abs.getX(), abs.getY(), abs.getZ());
				boolean once = io.github.jcondedata.aliveworkplace.work.PathObstacles.blocksVillager(level, villager, abs.getX(), abs.getY(), abs.getZ());
				helper.assertTrue(apart == once, "feet " + low + ", head " + high + ": the two rules say " + apart + ", the one pass " + once);
				shut += once ? 1 : 0;
			}
		}
		helper.assertTrue(shut > 0, "no pair was in the way: the check never saw a shut trapdoor at head height");
		helper.succeed();
	}
}
