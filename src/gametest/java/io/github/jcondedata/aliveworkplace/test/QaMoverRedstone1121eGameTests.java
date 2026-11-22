package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.MoverGameTests.Setup;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

/**
 * QA (lane E, 2026-11-21): the Mover and redstone (45.3, "the building comes back as it was"; B1216: once the whole
 * building stands at its new spot, or back at its old one after a call-off, every moved block reads its power). A
 * house wired inside (a redstone block feeding a repeater into a lamp, dust into a second lamp, a lever holding an
 * iron door open), with redstone blocks in its outer walls feeding a lamp and dust outside it, a torch and a ladder a
 * player hung on its outside walls, and a lamp waiting beside the new spot where a wall redstone block will land.
 * Moved with a quarter turn: whole, through a save and reload every other step, and called off half way.
 */
public class QaMoverRedstone1121eGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos MIN = new BlockPos(2, 2, 2);
	static final BlockPos MAX = new BlockPos(8, 6, 8);
	static final BlockPos TO = new BlockPos(16, 2, 16);
	static final BlockPos HALL = new BlockPos(15, 2, 2);
	// inside the house
	static final BlockPos POWER = new BlockPos(4, 3, 4);
	static final BlockPos REPEATER = new BlockPos(5, 3, 4);
	static final BlockPos LAMP_REPEATER = new BlockPos(6, 3, 4);
	static final BlockPos DUST_A = new BlockPos(4, 3, 5);
	static final BlockPos DUST_B = new BlockPos(4, 3, 6);
	static final BlockPos LAMP_DUST = new BlockPos(4, 3, 7);
	static final BlockPos DOOR = new BlockPos(6, 3, 8);
	static final BlockPos LEVER = new BlockPos(7, 3, 7);
	// in the outer walls
	static final BlockPos WALL_POWER_WEST = new BlockPos(2, 3, 5);
	static final BlockPos WALL_POWER_NORTH = new BlockPos(5, 3, 2);
	// outside the house (not part of the building)
	static final BlockPos OUT_LAMP = new BlockPos(1, 3, 5);
	static final BlockPos OUT_DUST = new BlockPos(5, 3, 1);
	static final BlockPos OUT_DUST_LAMP = new BlockPos(5, 3, 0);
	static final BlockPos OUT_TORCH = new BlockPos(9, 4, 5);
	static final BlockPos OUT_LADDER = new BlockPos(3, 3, 9);
	/** Ticks after the move ends before the checks: a lamp losing power goes dark 4 ticks after it hears of it. */
	static final int SETTLE = 12;

	private static void quiet(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	static BlockState dustNS() {
		return Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
			.setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE);
	}

	/** Where a block of the house (helper coordinates at its old spot) stands after the move, turned a quarter. */
	static BlockPos moved(Setup s, BlockPos helperPos) {
		return s.before().at(helperPos.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	/** Beside the new spot: just outside where the west wall's redstone block lands (the west wall faces north after the turn). */
	static BlockPos newSpotLamp(Setup s) {
		return moved(s, WALL_POWER_WEST).relative(Rotation.CLOCKWISE_90.rotate(Direction.WEST));
	}

	/**
	 * Tick 0: the house and its wiring, built as a player would (redstone placed with its neighbors told), and the lamp
	 * beside the new spot. The lamp goes where {@link #newSpotLamp} says once the plan exists; it's placed in {@link #capture}.
	 */
	static void build(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = MIN.getX(); x <= MAX.getX(); x++) {
			for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
				for (int y = MIN.getY(); y <= MAX.getY(); y++) {
					boolean edge = x == MIN.getX() || x == MAX.getX() || z == MIN.getZ() || z == MAX.getZ();
					BlockState state = y == MIN.getY() ? Blocks.COBBLESTONE.defaultBlockState()
						: y == MAX.getY() ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: edge ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					quiet(level, helper.absolutePos(new BlockPos(x, y, z)), state);
				}
			}
		}
		// lamps and wires first, the power last, so each is told the power arrived
		helper.setBlock(LAMP_REPEATER, Blocks.REDSTONE_LAMP);
		helper.setBlock(REPEATER, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.WEST));
		helper.setBlock(LAMP_DUST, Blocks.REDSTONE_LAMP);
		helper.setBlock(DUST_B, dustNS());
		helper.setBlock(DUST_A, dustNS());
		helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
		BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		quiet(level, helper.absolutePos(DOOR), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		quiet(level, helper.absolutePos(DOOR.above()), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(LEVER, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.NORTH));
		helper.pullLever(LEVER);
		// outside, fed by the walls
		helper.setBlock(OUT_LAMP, Blocks.REDSTONE_LAMP);
		helper.setBlock(OUT_DUST.below(), Blocks.STONE);
		helper.setBlock(OUT_DUST_LAMP, Blocks.REDSTONE_LAMP);
		helper.setBlock(OUT_DUST, dustNS());
		helper.setBlock(WALL_POWER_WEST, Blocks.REDSTONE_BLOCK);
		helper.setBlock(WALL_POWER_NORTH, Blocks.REDSTONE_BLOCK);
		// a player's torch and ladder on the outside walls
		helper.setBlock(OUT_TORCH, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
		helper.setBlock(OUT_LADDER, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
	}

	/** A few ticks after {@link #build}: everything is powered; the hall's record, the plan, the move confirmed, a Mover. */
	static Setup capture(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		String wrong = poweredInside(helper, level, p -> helper.absolutePos(p));
		helper.assertTrue(wrong == null, "setup: the house isn't wired as meant: " + wrong);
		helper.assertTrue(level.getBlockState(helper.absolutePos(OUT_LAMP)).getValue(RedstoneLampBlock.LIT), "setup: the lamp outside isn't lit");
		helper.assertTrue(level.getBlockState(helper.absolutePos(OUT_DUST_LAMP)).getValue(RedstoneLampBlock.LIT), "setup: the dust's lamp outside isn't lit");
		helper.assertTrue(level.getBlockState(helper.absolutePos(OUT_DUST)).getValue(RedStoneWireBlock.POWER) == 15, "setup: the dust outside isn't powered");

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "mover_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		BuildSiteManager.Finished finished = sites.finishedAt(level, id, helper.absolutePos(MIN)).orElseThrow();
		MovePlan before = MovePlan.capture(level, finished).orElseThrow();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos from = helper.absolutePos(MIN);
		BlockPos to = helper.absolutePos(TO);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, from, id, to, Rotation.CLOCKWISE_90, null));
		Leftovers.after(helper, () -> {
			MoveSites.get(level).cancel(level, from);
			MoveSites.get(level).all().stream().filter(x -> x.from().equals(from)).forEach(x -> {
				while (x.stage() != MoveSite.Stage.DONE) {
					MoveSites.get(level).step(level, x);
				}
			});
			PendingMoves.get(level).remove(from);
			sites.forgetFinished(placement);
			sites.finishedIn(level).stream().filter(f -> f.structure().equals(id)).forEach(f -> sites.forgetFinished(f.placement()));
		});
		BlockPos at = new BlockPos(12, 2, 12);
		helper.setBlock(at.north(), ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, at);
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
			net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(at.north())));
		Setup s = new Setup(placement, before, hall, from, to, mover);
		// a lamp a player put beside the new spot, right where the west wall's redstone block will land next to it
		level.setBlock(newSpotLamp(s), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
		return s;
	}

	/** What's wrong with the wiring inside the house, its blocks found by {@code where} (null: lamps lit, repeater on, door open). */
	static String poweredInside(GameTestHelper helper, ServerLevel level, java.util.function.Function<BlockPos, BlockPos> where) {
		List<String> wrong = new ArrayList<>();
		BlockState lamp1 = level.getBlockState(where.apply(LAMP_REPEATER));
		if (!lamp1.is(Blocks.REDSTONE_LAMP) || !lamp1.getValue(RedstoneLampBlock.LIT)) {
			wrong.add("the repeater's lamp at " + where.apply(LAMP_REPEATER).toShortString() + " is " + lamp1);
		}
		BlockState rep = level.getBlockState(where.apply(REPEATER));
		if (!rep.is(Blocks.REPEATER) || !rep.getValue(RepeaterBlock.POWERED)) {
			wrong.add("the repeater at " + where.apply(REPEATER).toShortString() + " is " + rep);
		}
		BlockState lamp2 = level.getBlockState(where.apply(LAMP_DUST));
		if (!lamp2.is(Blocks.REDSTONE_LAMP) || !lamp2.getValue(RedstoneLampBlock.LIT)) {
			wrong.add("the dust's lamp at " + where.apply(LAMP_DUST).toShortString() + " is " + lamp2);
		}
		BlockState dust = level.getBlockState(where.apply(DUST_A));
		if (!dust.is(Blocks.REDSTONE_WIRE) || dust.getValue(RedStoneWireBlock.POWER) != 15) {
			wrong.add("the dust at " + where.apply(DUST_A).toShortString() + " is " + dust);
		}
		for (BlockPos half : List.of(DOOR, DOOR.above())) {
			BlockState door = level.getBlockState(where.apply(half));
			if (!door.is(Blocks.IRON_DOOR) || !door.getValue(DoorBlock.OPEN)) {
				wrong.add("the iron door at " + where.apply(half).toShortString() + " is " + door);
			}
		}
		BlockState lever = level.getBlockState(where.apply(LEVER));
		if (!lever.is(Blocks.LEVER) || !lever.getValue(LeverBlock.POWERED)) {
			wrong.add("the lever at " + where.apply(LEVER).toShortString() + " is " + lever);
		}
		return wrong.isEmpty() ? null : wrong.size() + " wrong: " + String.join("; ", wrong);
	}

	/** Runs the whole move (or until {@code stop}); every {@code reloadEvery} steps a save and reload (0: never). The stages reloaded in. */
	static MoveSite run(ServerLevel level, Setup s, int reloadEvery, java.util.Set<MoveSite.Stage> reloadedIn) {
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		int steps = 0;
		while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
			MoverWork.work(level, s.mover(), site);
			if (reloadEvery > 0 && steps % reloadEvery == 0 && site.stage() != MoveSite.Stage.DONE) {
				reloadedIn.add(site.stage());
				site = MoverGameTests.reload(level, s);
			}
		}
		return site;
	}

	/** 1. Moved whole with a quarter turn: the repeater's lamp and the dust's lamp lit, the iron door open, at DONE and after. */
	//$ gametest_batch AREA '"qa_e1121_mover_redstone_whole"'
	@GameTest(template = AREA, batch = "qa_e1121_mover_redstone_whole")
	public void aWiredHouseMovedTurnedStillHasItsLampsLitAndDoorOpen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = capture(helper, "qa_redstone_whole");
			MoveSite site = run(level, s[0], 0, java.util.EnumSet.noneOf(MoveSite.Stage.class));
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled() && site.skipped() == 0,
				"the move ended " + site.stage() + ", skipped " + site.skipped());
			String wrong = poweredInside(helper, level, p -> moved(s[0], p));
			helper.assertTrue(wrong == null, "right after the move: " + wrong);
		});
		helper.runAfterDelay(5 + SETTLE, () -> {
			String wrong = poweredInside(helper, level, p -> moved(s[0], p));
			helper.assertTrue(wrong == null, SETTLE + " ticks after the move: " + wrong);
			helper.succeed();
		});
	}

	/** 1b. B1216 outward: a lamp a player set beside the new spot lights when the wall's redstone block lands beside it. */
	//$ gametest_batch AREA '"qa_e1121_mover_redstone_new_neighbor"'
	@GameTest(template = AREA, batch = "qa_e1121_mover_redstone_new_neighbor")
	public void aLampBesideTheNewSpotLightsWhenTheWallsPowerArrives(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = capture(helper, "qa_redstone_new_neighbor");
			helper.assertTrue(!level.getBlockState(newSpotLamp(s[0])).getValue(RedstoneLampBlock.LIT), "setup: the lamp by the new spot is lit already");
			MoveSite site = run(level, s[0], 0, java.util.EnumSet.noneOf(MoveSite.Stage.class));
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(level.getBlockState(moved(s[0], WALL_POWER_WEST)).is(Blocks.REDSTONE_BLOCK), "the wall's redstone block isn't at "
				+ moved(s[0], WALL_POWER_WEST).toShortString() + ": " + level.getBlockState(moved(s[0], WALL_POWER_WEST)));
		});
		helper.runAfterDelay(5 + SETTLE, () -> {
			BlockState lamp = level.getBlockState(newSpotLamp(s[0]));
			helper.assertTrue(lamp.getValue(RedstoneLampBlock.LIT), "the lamp beside the new spot at " + newSpotLamp(s[0]).toShortString()
				+ " stays dark with the wall's redstone block beside it at " + moved(s[0], WALL_POWER_WEST).toShortString());
			helper.succeed();
		});
	}

	/** 3. Saved and reloaded every other step of the Mover's (in every stage, the last block and the settling too): still lit and open. */
	//$ gametest_batch AREA '"qa_e1121_mover_redstone_reload"'
	@GameTest(template = AREA, batch = "qa_e1121_mover_redstone_reload")
	public void aWiredHouseMovedThroughReloadsStillHasItsLampsLit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = capture(helper, "qa_redstone_reload");
			java.util.Set<MoveSite.Stage> reloadedIn = java.util.EnumSet.noneOf(MoveSite.Stage.class);
			MoveSite site = run(level, s[0], 2, reloadedIn);
			helper.assertTrue(reloadedIn.containsAll(List.of(MoveSite.Stage.DISMANTLE, MoveSite.Stage.FOUNDATION, MoveSite.Stage.STRUCTURE,
				MoveSite.Stage.DECORATION)), "reloaded only in " + reloadedIn);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled() && site.skipped() == 0,
				"the move ended " + site.stage() + ", skipped " + site.skipped());
		});
		helper.runAfterDelay(5 + SETTLE, () -> {
			String wrong = poweredInside(helper, level, p -> moved(s[0], p));
			helper.assertTrue(wrong == null, "after a move reloaded every other step: " + wrong);
			helper.assertTrue(level.getBlockState(newSpotLamp(s[0])).getValue(RedstoneLampBlock.LIT), "the lamp beside the new spot stays dark");
			helper.succeed();
		});
	}

	/** 4. Called off half way up at the new spot: back at the old spot, wired as it was, and the lamp outside lit again. */
	//$ gametest_batch AREA '"qa_e1121_mover_redstone_cancel"'
	@GameTest(template = AREA, batch = "qa_e1121_mover_redstone_cancel")
	public void aWiredHouseCalledOffIsBackLitAndOpen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = capture(helper, "qa_redstone_cancel");
			MoveSite site = MoverGameTests.work(level, s[0], x -> x.stage() == MoveSite.Stage.DECORATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.DECORATION, "it didn't get half way up: " + site.stage());
			PendingMoves.get(level).remove(s[0].from()); // the Move page's Call off
			site = MoverGameTests.work(level, s[0], x -> false);
			helper.assertTrue(site.canceled() && site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage() + (site.canceled() ? " canceled" : ""));
			for (MovePlan.Placed p : s[0].before().placed(s[0].to(), Rotation.CLOCKWISE_90)) {
				helper.assertTrue(level.getBlockState(p.pos()).isAir(), "the new spot still has " + level.getBlockState(p.pos()) + " at " + p.pos().toShortString());
			}
		});
		helper.runAfterDelay(5 + SETTLE, () -> {
			String wrong = poweredInside(helper, level, p -> helper.absolutePos(p));
			helper.assertTrue(wrong == null, "back at the old spot: " + wrong);
			helper.assertTrue(level.getBlockState(helper.absolutePos(OUT_LAMP)).getValue(RedstoneLampBlock.LIT), "the lamp outside the wall is dark");
			helper.assertTrue(level.getBlockState(helper.absolutePos(OUT_DUST)).getValue(RedStoneWireBlock.POWER) == 15,
				"the dust outside: " + level.getBlockState(helper.absolutePos(OUT_DUST)));
			helper.assertTrue(level.getBlockState(helper.absolutePos(OUT_TORCH)).is(Blocks.WALL_TORCH), "the torch on the wall: " + level.getBlockState(helper.absolutePos(OUT_TORCH)));
			helper.assertTrue(!level.getBlockState(newSpotLamp(s[0])).getValue(RedstoneLampBlock.LIT), "the lamp by the new spot is lit with nothing there");
			helper.succeed();
		});
	}
}
