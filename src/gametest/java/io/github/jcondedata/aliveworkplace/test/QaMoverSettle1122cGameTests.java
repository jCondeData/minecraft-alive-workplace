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
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane C, 2026-11-22), from the spec of B1216 (45.3, "the building comes back as it was"): once the whole building
 * stands at its new spot (or back at the old one after a call-off) every moved block gets one neighbor update and tells
 * its neighbors; shapes are not updated; stateless, so a reload just runs it again. These tests ask the other side of
 * that pass: it must not CHANGE a house that was at rest. A dispenser already powered (and fired) beside its redstone
 * block, a hopper locked by a redstone block over a chest, an observer watching a stone with an unpowered dispenser
 * behind it, an extended piston held by a redstone block and a retracted one with no power, a lever switched off beside
 * a lamp and a TNT block, and a lamp lit by a redstone block. A redstone block in the west wall lights a lamp outside it.
 * The house leaving its old spot is in {@link QaMoverSettle1122cBugTests}.
 */
public class QaMoverSettle1122cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos MIN = new BlockPos(2, 2, 2);
	static final BlockPos MAX = new BlockPos(8, 6, 8);
	static final BlockPos TO = new BlockPos(16, 2, 16);
	static final BlockPos HALL = new BlockPos(15, 2, 2);
	// a dispenser that already fired, held by the redstone block on it
	static final BlockPos DISP_FIRED = new BlockPos(3, 3, 3);
	static final BlockPos DISP_FIRED_POWER = new BlockPos(3, 4, 3);
	// a hopper locked by a redstone block, over a chest
	static final BlockPos HOPPER = new BlockPos(7, 4, 3);
	static final BlockPos HOPPER_CHEST = new BlockPos(7, 3, 3);
	static final BlockPos HOPPER_POWER = new BlockPos(6, 4, 3);
	// an observer watching a stone, an unpowered dispenser behind it
	static final BlockPos WATCHED = new BlockPos(3, 3, 5);
	static final BlockPos OBSERVER = new BlockPos(3, 3, 6);
	static final BlockPos DISP_IDLE = new BlockPos(3, 3, 7);
	// an extended piston held by a redstone block, its head, the stone it pushed
	static final BlockPos PISTON_OUT = new BlockPos(5, 3, 5);
	static final BlockPos PISTON_HEAD = new BlockPos(6, 3, 5);
	static final BlockPos PUSHED = new BlockPos(7, 3, 5);
	static final BlockPos PISTON_OUT_POWER = new BlockPos(5, 4, 5);
	// a retracted piston, facing up, nothing powering it
	static final BlockPos PISTON_IN = new BlockPos(4, 3, 6);
	// a lever switched off between a TNT block and a lamp
	static final BlockPos TNT = new BlockPos(5, 3, 7);
	static final BlockPos LEVER = new BlockPos(6, 3, 7);
	static final BlockPos LAMP_DARK = new BlockPos(7, 3, 7);
	// a lamp lit by a redstone block
	static final BlockPos LAMP_LIT_POWER = new BlockPos(6, 5, 6);
	static final BlockPos LAMP_LIT = new BlockPos(7, 5, 6);
	// a redstone block in the west wall, and the lamp a player put outside it (not part of the building)
	static final BlockPos WALL_POWER = new BlockPos(2, 4, 5);
	static final BlockPos OUT_LAMP = new BlockPos(1, 4, 5);
	static final int COBBLE = 5;
	static final int IRON = 5;
	static final int SETTLE = 12;
	/** Long enough for an unlocked hopper to pass all five ingots down (one per 8 ticks). */
	static final int HOPPER_WAIT = 50;

	private static void quiet(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static void quiet(GameTestHelper helper, BlockPos rel, BlockState state) {
		quiet(helper.getLevel(), helper.absolutePos(rel), state);
	}

	/** The house at rest, every block set without telling its neighbors (as if it had stood like this for days). */
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
		quiet(helper, DISP_FIRED, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST).setValue(DispenserBlock.TRIGGERED, true));
		((Container) level.getBlockEntity(helper.absolutePos(DISP_FIRED))).setItem(0, new ItemStack(Items.COBBLESTONE, COBBLE));
		quiet(helper, DISP_FIRED_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());

		quiet(helper, HOPPER_CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		quiet(helper, HOPPER_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState()); // first: a hopper reads its power as it's placed
		quiet(helper, HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN).setValue(HopperBlock.ENABLED, false));
		((Container) level.getBlockEntity(helper.absolutePos(HOPPER))).setItem(0, new ItemStack(Items.IRON_INGOT, IRON));

		quiet(helper, WATCHED, Blocks.STONE.defaultBlockState());
		quiet(helper, OBSERVER, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.NORTH));
		quiet(helper, DISP_IDLE, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
		((Container) level.getBlockEntity(helper.absolutePos(DISP_IDLE))).setItem(0, new ItemStack(Items.COBBLESTONE, COBBLE));

		quiet(helper, PISTON_OUT_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState()); // first: a piston reads its power as it's placed
		quiet(helper, PISTON_OUT, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST).setValue(PistonBaseBlock.EXTENDED, true));
		quiet(helper, PISTON_HEAD, Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, Direction.EAST)
			.setValue(PistonHeadBlock.TYPE, PistonType.DEFAULT).setValue(PistonHeadBlock.SHORT, false));
		quiet(helper, PUSHED, Blocks.STONE.defaultBlockState());
		quiet(helper, PISTON_IN, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));

		quiet(helper, TNT, Blocks.TNT.defaultBlockState());
		quiet(helper, LEVER, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
		quiet(helper, LAMP_DARK, Blocks.REDSTONE_LAMP.defaultBlockState());

		quiet(helper, LAMP_LIT, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
		quiet(helper, LAMP_LIT_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());

		quiet(helper, OUT_LAMP, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
		quiet(helper, WALL_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());
	}

	/** Where a block of the house (helper coordinates at the old spot) stands after the move. */
	static BlockPos moved(Setup s, Rotation rotation, BlockPos helperPos) {
		return s.before().at(helperPos.subtract(MIN), s.to(), rotation);
	}

	/** Just outside where the wall's redstone block lands at the new spot. */
	static BlockPos newSpotLamp(Setup s, Rotation rotation) {
		return moved(s, rotation, WALL_POWER).relative(rotation.rotate(Direction.WEST));
	}

	/** The hall's record, the plan, the move confirmed (turned {@code rotation}), a Mover, a dark lamp by the new spot. */
	static Setup capture(GameTestHelper helper, String name, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		String wrong = atRest(helper, level, p -> helper.absolutePos(p));
		helper.assertTrue(wrong == null, "setup: the house isn't as meant: " + wrong);
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
		PendingMoves.get(level).put(new PendingMoves.Move(hall, from, id, to, rotation, null));
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
		quiet(level, newSpotLamp(s, rotation), Blocks.REDSTONE_LAMP.defaultBlockState());
		return s;
	}

	private static int count(ServerLevel level, BlockPos abs) {
		if (!(level.getBlockEntity(abs) instanceof Container c)) {
			return -1;
		}
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			n += c.getItem(i).getCount();
		}
		return n;
	}

	/** What isn't as the house was left at rest, its blocks found by {@code where} (null: all as it was). */
	static String atRest(GameTestHelper helper, ServerLevel level, Function<BlockPos, BlockPos> where) {
		List<String> wrong = new ArrayList<>();
		wrong.addAll(dispensers(level, where));
		wrong.addAll(hopper(level, where));
		wrong.addAll(pistons(level, where));
		wrong.addAll(leverOff(level, where));
		BlockState lit = level.getBlockState(where.apply(LAMP_LIT));
		if (!lit.is(Blocks.REDSTONE_LAMP) || !lit.getValue(RedstoneLampBlock.LIT)) {
			wrong.add("the lamp by its redstone block is " + lit);
		}
		return wrong.isEmpty() ? null : wrong.size() + " wrong: " + String.join("; ", wrong);
	}

	static List<String> dispensers(ServerLevel level, Function<BlockPos, BlockPos> where) {
		List<String> wrong = new ArrayList<>();
		int fired = count(level, where.apply(DISP_FIRED));
		if (fired != COBBLE) {
			wrong.add("the dispenser that had fired already (powered by the redstone block on it) holds " + fired + " cobblestone, not " + COBBLE
				+ " (" + level.getBlockState(where.apply(DISP_FIRED)) + ")");
		}
		int idle = count(level, where.apply(DISP_IDLE));
		if (idle != COBBLE) {
			wrong.add("the dispenser behind the observer holds " + idle + " cobblestone, not " + COBBLE + " (observer "
				+ level.getBlockState(where.apply(OBSERVER)) + ")");
		}
		return wrong;
	}

	static List<String> hopper(ServerLevel level, Function<BlockPos, BlockPos> where) {
		List<String> wrong = new ArrayList<>();
		BlockState h = level.getBlockState(where.apply(HOPPER));
		if (!h.is(Blocks.HOPPER) || h.getValue(HopperBlock.ENABLED)) {
			wrong.add("the hopper beside its redstone block is " + h);
		}
		int in = count(level, where.apply(HOPPER));
		int chest = count(level, where.apply(HOPPER_CHEST));
		if (in != IRON || chest != 0) {
			wrong.add("the locked hopper holds " + in + " iron and the chest under it " + chest + " (was " + IRON + " and 0)");
		}
		return wrong;
	}

	static List<String> pistons(ServerLevel level, Function<BlockPos, BlockPos> where) {
		List<String> wrong = new ArrayList<>();
		BlockState out = level.getBlockState(where.apply(PISTON_OUT));
		if (!out.is(Blocks.PISTON) || !out.getValue(PistonBaseBlock.EXTENDED)) {
			wrong.add("the powered piston is " + out);
		}
		BlockState head = level.getBlockState(where.apply(PISTON_HEAD));
		if (!head.is(Blocks.PISTON_HEAD)) {
			wrong.add("in front of the powered piston: " + head);
		}
		BlockState pushed = level.getBlockState(where.apply(PUSHED));
		if (!pushed.is(Blocks.STONE)) {
			wrong.add("the stone it pushed: " + pushed);
		}
		BlockState in = level.getBlockState(where.apply(PISTON_IN));
		if (!in.is(Blocks.PISTON) || in.getValue(PistonBaseBlock.EXTENDED)) {
			wrong.add("the unpowered piston is " + in);
		}
		BlockState above = level.getBlockState(where.apply(PISTON_IN.above()));
		if (!above.isAir()) {
			wrong.add("above the unpowered piston: " + above);
		}
		return wrong;
	}

	static List<String> leverOff(ServerLevel level, Function<BlockPos, BlockPos> where) {
		List<String> wrong = new ArrayList<>();
		BlockState lever = level.getBlockState(where.apply(LEVER));
		if (!lever.is(Blocks.LEVER) || lever.getValue(LeverBlock.POWERED)) {
			wrong.add("the lever is " + lever);
		}
		BlockState lamp = level.getBlockState(where.apply(LAMP_DARK));
		if (!lamp.is(Blocks.REDSTONE_LAMP) || lamp.getValue(RedstoneLampBlock.LIT)) {
			wrong.add("the lamp by the switched-off lever is " + lamp);
		}
		BlockState tnt = level.getBlockState(where.apply(TNT));
		if (!tnt.is(Blocks.TNT)) {
			wrong.add("the TNT by the switched-off lever is " + tnt);
		}
		return wrong;
	}

	/** Item entities and lit TNT anywhere in the test's area (a fired dispenser or a primed TNT leaves one). */
	static String loose(GameTestHelper helper) {
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO).getCenter(), helper.absolutePos(new BlockPos(29, 29, 29)).getCenter()).inflate(2);
		List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box);
		List<PrimedTnt> tnt = helper.getLevel().getEntitiesOfClass(PrimedTnt.class, box);
		if (items.isEmpty() && tnt.isEmpty()) {
			return null;
		}
		StringBuilder out = new StringBuilder();
		items.forEach(e -> out.append(e.getItem().getCount()).append(' ').append(e.getItem().getItem()).append(" at ").append(e.blockPosition().toShortString()).append("; "));
		tnt.forEach(e -> out.append("lit TNT at ").append(e.blockPosition().toShortString()).append("; "));
		return out.toString();
	}

	/** The whole move in one go, a save and reload before every step of {@code reloadIn} (null: never). */
	static MoveSite run(ServerLevel level, Setup s, MoveSite.Stage reloadIn, int[] reloads) {
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		int steps = 0;
		while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
			if (reloadIn != null && site.stage() == reloadIn) {
				site = MoverGameTests.reload(level, s);
				reloads[0]++;
			}
			MoverWork.work(level, s.mover(), site);
		}
		return site;
	}

	/** Builds, moves turned {@code rotation} in tick 5 and runs {@code check} (with the moved spot's finder) {@code after} ticks later. */
	static void moveThen(GameTestHelper helper, String name, Rotation rotation, int after,
								 java.util.function.BiConsumer<Setup, Function<BlockPos, BlockPos>> check) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = capture(helper, name, rotation);
			MoveSite site = run(level, s[0], null, new int[1]);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled() && site.skipped() == 0,
				"the move ended " + site.stage() + (site.canceled() ? " canceled" : "") + ", skipped " + site.skipped());
		});
		helper.runAfterDelay(5 + after, () -> {
			check.accept(s[0], p -> moved(s[0], rotation, p));
			helper.succeed();
		});
	}

	/** 1. A dispenser that had fired already, still held by its redstone block, doesn't fire again when the house arrives turned. */
	//$ gametest_batch AREA '"qa_c1122_settle_dispenser"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_dispenser")
	public void aPoweredDispenserDoesNotFireAgainWhenItsHouseArrives(GameTestHelper helper) {
		moveThen(helper, "qa_settle_dispenser", Rotation.CLOCKWISE_90, SETTLE, (s, where) -> {
			List<String> wrong = dispensers(helper.getLevel(), where);
			helper.assertTrue(wrong.isEmpty(), SETTLE + " ticks after the move: " + String.join("; ", wrong));
			String loose = loose(helper);
			helper.assertTrue(loose == null, "loose after the move (a dispenser fired?): " + loose);
		});
	}

	/** 2. A hopper locked by a redstone block stays locked at the new spot: its ingots stay in it, the chest under it stays empty. */
	//$ gametest_batch AREA '"qa_c1122_settle_hopper"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_hopper")
	public void aLockedHopperStaysLockedAfterTheMove(GameTestHelper helper) {
		moveThen(helper, "qa_settle_hopper", Rotation.CLOCKWISE_90, HOPPER_WAIT, (s, where) -> {
			List<String> wrong = hopper(helper.getLevel(), where);
			helper.assertTrue(wrong.isEmpty(), HOPPER_WAIT + " ticks after the move: " + String.join("; ", wrong));
		});
	}

	/** 3. Pistons keep their state: the powered one stays out with its head and the stone it pushed; the unpowered one stays in. */
	//$ gametest_batch AREA '"qa_c1122_settle_pistons"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_pistons")
	public void pistonsStayOutOrInAsTheyWereAfterTheMove(GameTestHelper helper) {
		moveThen(helper, "qa_settle_pistons", Rotation.CLOCKWISE_90, SETTLE, (s, where) -> {
			List<String> wrong = pistons(helper.getLevel(), where);
			helper.assertTrue(wrong.isEmpty(), SETTLE + " ticks after the move: " + String.join("; ", wrong));
		});
	}

	/** 4. A lever switched off leaves its lamp dark and the TNT beside it a TNT block; the lamp by a redstone block stays lit. */
	//$ gametest_batch AREA '"qa_c1122_settle_lever_off"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_lever_off")
	public void aSwitchedOffLeverLeavesItsLampDarkAndTheTntAlone(GameTestHelper helper) {
		moveThen(helper, "qa_settle_lever_off", Rotation.CLOCKWISE_90, SETTLE, (s, where) -> {
			List<String> wrong = leverOff(helper.getLevel(), where);
			BlockState lit = helper.getLevel().getBlockState(where.apply(LAMP_LIT));
			if (!lit.is(Blocks.REDSTONE_LAMP) || !lit.getValue(RedstoneLampBlock.LIT)) {
				wrong.add("the lamp by its redstone block is " + lit);
			}
			helper.assertTrue(wrong.isEmpty(), SETTLE + " ticks after the move: " + String.join("; ", wrong));
			String loose = loose(helper);
			helper.assertTrue(loose == null, "loose after the move: " + loose);
		});
	}

	/** 5. "Comes back as it was": every block of the house, a while after the move, is exactly the state it was packed in (turned). */
	//$ gametest_batch AREA '"qa_c1122_settle_exact"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_exact")
	public void everyBlockStandsExactlyAsItWasPackedAfterTheMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		moveThen(helper, "qa_settle_exact", Rotation.CLOCKWISE_90, HOPPER_WAIT, (s, where) -> {
			List<String> wrong = new ArrayList<>();
			for (MovePlan.Placed p : s.before().placed(s.to(), Rotation.CLOCKWISE_90)) {
				BlockState now = level.getBlockState(p.pos());
				if (now != p.state()) {
					wrong.add(p.pos().toShortString() + " is " + now + ", was " + p.state());
				}
			}
			helper.assertTrue(wrong.isEmpty(), HOPPER_WAIT + " ticks after the move " + wrong.size() + " blocks changed: "
				+ String.join("; ", wrong.subList(0, Math.min(6, wrong.size()))));
		});
	}

	/** 6. Moved without a turn: the lamp a player set beside the new spot lights when the wall's redstone block lands beside it. */
	//$ gametest_batch AREA '"qa_c1122_settle_plain_new_lamp"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_plain_new_lamp")
	public void movedWithoutATurnTheLampBesideTheNewSpotLights(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		moveThen(helper, "qa_settle_plain_new_lamp", Rotation.NONE, SETTLE, (s, where) -> {
			BlockPos lampAt = newSpotLamp(s, Rotation.NONE);
			helper.assertTrue(level.getBlockState(where.apply(WALL_POWER)).is(Blocks.REDSTONE_BLOCK),
				"the wall's redstone block isn't at " + where.apply(WALL_POWER).toShortString());
			helper.assertTrue(level.getBlockState(lampAt).getValue(RedstoneLampBlock.LIT), "the lamp beside the new spot at "
				+ lampAt.toShortString() + " stays dark with the wall's redstone block beside it");
			String wrong = atRest(helper, level, where);
			helper.assertTrue(wrong == null, "moved without a turn: " + wrong);
		});
	}

	/** 8. Saved and reloaded before every step of the last stage (so right before the settling too): nothing fires twice, the new lamp lights. */
	//$ gametest_batch AREA '"qa_c1122_settle_reload"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_reload")
	public void reloadedRightBeforeTheSettlingNothingFiresAndTheLampLights(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = capture(helper, "qa_settle_reload", Rotation.CLOCKWISE_90);
			int[] reloads = new int[1];
			MoveSite site = run(level, s[0], MoveSite.Stage.DECORATION, reloads);
			helper.assertTrue(reloads[0] > 0, "never reloaded in DECORATION");
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled() && site.skipped() == 0,
				"the move ended " + site.stage() + ", skipped " + site.skipped());
		});
		helper.runAfterDelay(5 + HOPPER_WAIT, () -> {
			String wrong = atRest(helper, level, p -> moved(s[0], Rotation.CLOCKWISE_90, p));
			helper.assertTrue(wrong == null, "after reloads in the last stage: " + wrong);
			String loose = loose(helper);
			helper.assertTrue(loose == null, "loose after the move: " + loose);
			BlockPos lampAt = newSpotLamp(s[0], Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(lampAt).getValue(RedstoneLampBlock.LIT), "the lamp beside the new spot at " + lampAt.toShortString() + " stays dark");
			helper.succeed();
		});
	}

	/** 9. Called off half way up at the new spot: back at the old spot nothing fired, the hopper is locked, the outside lamp lit again. */
	//$ gametest_batch AREA '"qa_c1122_settle_cancel"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_cancel")
	public void calledOffTheHouseIsBackAtRestAndTheOutsideLampLit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = capture(helper, "qa_settle_cancel", Rotation.CLOCKWISE_90);
			MoveSite site = MoverGameTests.work(level, s[0], x -> x.stage() == MoveSite.Stage.DECORATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.DECORATION, "it didn't get half way up: " + site.stage());
			PendingMoves.get(level).remove(s[0].from()); // the Move page's Call off
			site = MoverGameTests.work(level, s[0], x -> false);
			helper.assertTrue(site.canceled() && site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage() + (site.canceled() ? " canceled" : ""));
		});
		helper.runAfterDelay(5 + HOPPER_WAIT, () -> {
			String wrong = atRest(helper, level, p -> helper.absolutePos(p));
			helper.assertTrue(wrong == null, "back at the old spot: " + wrong);
			String loose = loose(helper);
			helper.assertTrue(loose == null, "loose after the call-off: " + loose);
			BlockState lamp = level.getBlockState(helper.absolutePos(OUT_LAMP));
			helper.assertTrue(lamp.getValue(RedstoneLampBlock.LIT), "the lamp outside the west wall is " + lamp);
			BlockState newLamp = level.getBlockState(newSpotLamp(s[0], Rotation.CLOCKWISE_90));
			helper.assertTrue(!newLamp.getValue(RedstoneLampBlock.LIT), "the lamp by the new spot is lit with nothing there");
			helper.succeed();
		});
	}
}
