package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane B, 2026-10-31, written from the bugs' specs:
 * <ul>
 * <li>B124: a worker's status, read the way a player reads it (sneak-right-click with an empty hand), names the
 * villager's own job in its level line, for an idle Miner (the reported case) and a Builder; every job the game knows
 * has a name for that line (no raw key, never "builder"); the level boundaries (novice at 0 XP, expert below the top,
 * master at the top).</li>
 * <li>B185: no villager is left standing inside a solid block or a gate shut on it: a block put down where a villager
 * stands, a fence gate shut on one in a gateway, a villager saved and loaded inside a block; let out to a safe spot
 * (never over lava), and one standing on a carpet under a hanging lantern is never moved.</li>
 * </ul>
 */
public class QaBuilderBugs1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos CELL = new BlockPos(4, 2, 4);
	/** The fix's own measure: let out within a few seconds (B185GameTests uses the same 40 ticks). */
	private static final int LET_OUT_BY = 40;

	// --- B124 ---------------------------------------------------------------------------------------------------

	/** An idle Miner (no quarry, no furnace) sneak-right-clicked with an empty hand: his level line says Miner. */
	//$ gametest_ticks_batch AREA '60' '"qaB124Miner"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaB124Miner")
	public void qaB124AnIdleMinersStatusNamesHimAMiner(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaB124m"));
		Villager miner = worker(helper, ModVillagers.MINER, 2, 10);
		List<String> lines = sneakClick(c, miner);
		String all = String.join(" | ", lines);
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains("Apprentice Miner · 10/70 XP to the next level · ")),
			"B124: the Miner's status has no 'Apprentice Miner · 10/70 XP…' line: " + all);
		helper.assertFalse(all.toLowerCase().contains("builder"), "B124: the Miner's status calls him a builder: " + all);
		helper.assertFalse(all.contains("message.aliveworkplace") || all.contains("entity.minecraft"), "an untranslated key: " + all);
		helper.succeed();
	}

	/** An idle Builder at his bench, read the same way: his line still says Builder (the fix didn't lose him). */
	//$ gametest_ticks_batch AREA '60' '"qaB124Builder"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaB124Builder")
	public void qaB124AnIdleBuildersStatusStillNamesHimABuilder(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaB124b"));
		BlockPos bench = new BlockPos(2, 2, 6);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 6));
		builder.setNoAi(true);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(bench));
		builder.setVillagerData(builder.getVillagerData().setLevel(3));
		builder.setVillagerXp(100);
		List<String> lines = sneakClick(c, builder);
		String all = String.join(" | ", lines);
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains("Journeyman Builder · 100/150 XP to the next level · ")),
			"B124: the Builder's status has no 'Journeyman Builder · 100/150 XP…' line: " + all);
		helper.succeed();
	}

	/**
	 * Every job registered in the game (vanilla's, ours, any optional mod's) at every level: the line names that job by
	 * a translated name, never "builder" for another job and never a raw key; levels 1-4 give the XP to the next level
	 * (0 XP is "0/10"), level 5 the top-level form.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaB124EveryJob"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaB124EveryJob")
	public void qaB124EveryJobAtEveryLevelHasItsOwnName(GameTestHelper helper) {
		Leftovers.clear(helper);
		Language lang = Language.getInstance();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		String[] levelNames = {"", "Novice", "Apprentice", "Journeyman", "Expert", "Master"};
		int[] next = {0, 10, 70, 150, 250};
		Villager v = worker(helper, VillagerProfession.FARMER, 1, 0);
		for (VillagerProfession job : BuiltInRegistries.VILLAGER_PROFESSION) {
			if (job == VillagerProfession.NONE || job == VillagerProfession.NITWIT) {
				continue;
			}
			String key = "entity.minecraft.villager." + job.name();
			if (!lang.has(key)) {
				problems.add(BuiltInRegistries.VILLAGER_PROFESSION.getKey(job) + " has no name (" + key + ")");
				continue;
			}
			String name = lang.getOrDefault(key);
			for (int level = 1; level <= 5; level++) {
				int xp = level == 1 ? 0 : next[level - 1];
				v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
				v.setVillagerXp(xp);
				String line = BuilderLevels.describe(v).getString();
				String want = level < 5 ? levelNames[level] + " " + name + " · " + xp + "/" + next[level] + " XP to the next level · "
					: levelNames[level] + " " + name + " (top level) · ";
				if (!line.startsWith(want)) {
					problems.add(job.name() + " level " + level + ": '" + line + "', not '" + want + "…'");
				}
				if (job != ModVillagers.BUILDER && line.toLowerCase().contains("builder")) {
					problems.add(job.name() + " level " + level + " is called a builder: " + line);
				}
				checked++;
			}
		}
		v.discard();
		helper.assertTrue(checked >= 5 * 20, "only " + checked + " job lines checked");
		helper.assertTrue(problems.isEmpty(), "B124: " + problems.size() + " wrong level lines, e.g. " + problems.subList(0, Math.min(5, problems.size())));
		helper.succeed();
	}

	private static Villager worker(GameTestHelper helper, VillagerProfession job, int level, int xp) {
		Villager v = helper.spawn(EntityType.VILLAGER, 4, 2, 4);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		v.setVillagerXp(xp);
		return v;
	}

	/** What the player reads after sneak-right-clicking {@code villager} with an empty hand, as the game routes it. */
	private static List<String> sneakClick(QaStageFiveGameTests.Client c, Villager villager) {
		ServerPlayer player = c.player();
		player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ());
		player.setShiftKeyDown(true);
		c.seen().clear();
		InteractionResult r = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
		List<String> out = new ArrayList<>();
		for (Component m : c.seen()) {
			for (String l : m.getString().split("\n")) {
				out.add(l.trim());
			}
		}
		if (r != InteractionResult.SUCCESS || out.isEmpty()) {
			throw new GameTestAssertException("the sneak-click gave " + r + " and said " + out);
		}
		return out;
	}

	// --- B185 ---------------------------------------------------------------------------------------------------

	/** Stone put down where a villager stands (feet and head, as a careless player or a falling build would): let out. */
	//$ gametest_ticks_batch AREA '300' '"qaB185Stone"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaB185Stone")
	public void qaB185AVillagerAStoneIsPutDownOnIsLetOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, CELL);
		BlockPos abs = helper.absolutePos(CELL);
		villager.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
		helper.runAfterDelay(2, () -> {
			helper.setBlock(CELL, Blocks.STONE);
			helper.setBlock(CELL.above(), Blocks.STONE);
		});
		letOutWithin(helper, villager, 2, Vec3.atBottomCenterOf(abs), "the stone");
	}

	/** A player shuts an oak fence gate on a villager standing in the gateway: it isn't left inside the gate. */
	//$ gametest_ticks_batch AREA '300' '"qaB185Gate"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaB185Gate")
	public void qaB185AVillagerAGateIsShutOnIsLetOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 1; x <= 7; x++) {
			if (x != CELL.getX()) {
				helper.setBlock(new BlockPos(x, 2, CELL.getZ()), Blocks.OAK_FENCE);
			}
		}
		BlockPos gate = helper.absolutePos(CELL);
		BlockState open = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH).setValue(FenceGateBlock.OPEN, true);
		level.setBlock(gate, open, Block.UPDATE_ALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, CELL);
		villager.moveTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5, 0, 0);
		helper.assertTrue(level.noCollision(villager, villager.getBoundingBox().deflate(0.01)), "the villager doesn't fit in the open gateway");
		helper.runAfterDelay(2, () -> {
			level.setBlock(gate, open.setValue(FenceGateBlock.OPEN, false), Block.UPDATE_ALL); // a player shuts it
			helper.assertTrue(stuck(level, villager), "the gate shut beside the villager, not on it: nothing to test");
		});
		letOutWithin(helper, villager, 2, Vec3.atBottomCenterOf(gate), "the shut gate");
	}

	/** A villager saved inside a block (the chunk unloads) and loaded again is still let out: nothing waits on a save. */
	//$ gametest_ticks_batch AREA '300' '"qaB185Reload"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaB185Reload")
	public void qaB185AVillagerLoadedInsideABlockIsLetOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager first = helper.spawn(EntityType.VILLAGER, CELL);
		BlockPos abs = helper.absolutePos(CELL);
		first.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
		helper.setBlock(CELL, Blocks.COBBLESTONE);
		helper.setBlock(CELL.above(), Blocks.COBBLESTONE);
		CompoundTag tag = new CompoundTag();
		first.saveWithoutId(tag);
		first.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		helper.assertTrue(stuck(level, copy), "the loaded villager isn't inside the cobblestone: nothing to test");
		letOutWithin(helper, copy, 0, Vec3.atBottomCenterOf(abs), "the cobblestone after a reload");
	}

	/**
	 * Stone put down on a villager whose nearest open ground is all lava pits: it is let out to safe ground further away,
	 * never into the lava (the spec: "the nearest walkable spot", nothing harmful).
	 */
	//$ gametest_ticks_batch AREA '300' '"qaB185Lava"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaB185Lava")
	public void qaB185AVillagerIsNeverLetOutIntoLava(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(CELL.offset(dx, -1, dz), Blocks.LAVA);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, CELL);
		BlockPos abs = helper.absolutePos(CELL);
		villager.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
		helper.setBlock(CELL, Blocks.STONE);
		helper.setBlock(CELL.above(), Blocks.STONE);
		boolean[] harmed = {false};
		helper.onEachTick(() -> {
			if (villager.isAlive() && (villager.isInLava() || villager.isOnFire())) {
				harmed[0] = true;
			}
		});
		helper.runAfterDelay(LET_OUT_BY + 40, () -> {
			helper.assertFalse(harmed[0], "B185: the villager was let out into the lava");
			helper.assertTrue(villager.isAlive(), "B185: the villager died (" + villager.getLastDamageSource() + ")");
			helper.assertFalse(stuck(level, villager), "B185: the villager is still inside the stone, at " + helper.relativePos(villager.blockPosition()).toShortString());
			BlockPos below = villager.blockPosition().below();
			helper.assertTrue(level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || villager.onGround(),
				"B185: the villager was let out with no floor under it, at " + helper.relativePos(villager.blockPosition()).toShortString());
			villager.discard();
			helper.succeed();
		});
	}

	/** A villager on a carpet, its head just under a hanging lantern, in a one-block pen: it is never moved. */
	//$ gametest_ticks_batch AREA '300' '"qaB185Carpet"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaB185Carpet")
	public void qaB185AVillagerOnACarpetUnderALanternIsNeverMoved(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dy = 0; dy < 3; dy++) {
				helper.setBlock(CELL.relative(d).above(dy), Blocks.STONE);
				helper.setBlock(CELL.relative(d).relative(d.getClockWise()).above(dy), Blocks.STONE);
			}
		}
		helper.setBlock(CELL.above(3), Blocks.STONE);
		helper.setBlock(CELL.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		helper.setBlock(CELL, Blocks.RED_CARPET);
		Villager villager = helper.spawn(EntityType.VILLAGER, CELL);
		BlockPos cell = helper.absolutePos(CELL);
		villager.moveTo(cell.getX() + 0.5, cell.getY() + 1 / 16.0, cell.getZ() + 0.5, 0, 0); // on the carpet, as one walks onto it
		helper.onEachTick(() -> {
			if (villager.getBlockX() != cell.getX() || villager.getBlockZ() != cell.getZ() || villager.getY() > cell.getY() + 0.5) {
				throw new GameTestAssertException("B185: a villager standing on a carpet under a lantern was moved, to "
					+ helper.relativePos(villager.blockPosition()).toShortString());
			}
		});
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(villager.isAlive(), "the villager died");
			villager.discard();
			helper.succeed();
		});
	}

	/** Checks from tick {@code from} on that the villager is out within {@link #LET_OUT_BY} ticks, alive, near where it was. */
	private static void letOutWithin(GameTestHelper helper, Villager villager, int from, Vec3 was, String what) {
		ServerLevel level = helper.getLevel();
		String[] problem = {null};
		Vec3[] freedAt = {null};
		helper.onEachTick(() -> {
			if (helper.getTick() <= from || !villager.isAlive()) {
				return;
			}
			if (freedAt[0] == null && !stuck(level, villager)) {
				freedAt[0] = villager.position();
			}
			if (problem[0] == null && helper.getTick() > from + LET_OUT_BY && stuck(level, villager)) {
				problem[0] = "B185: the villager is still inside " + what + " " + (helper.getTick() - from) + " ticks on, at "
					+ String.format("%.2f %.2f %.2f", villager.getX(), villager.getY(), villager.getZ());
			}
		});
		helper.runAfterDelay(from + LET_OUT_BY + 40, () -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(villager.isAlive(), "B185: the villager died inside " + what + " (" + villager.getLastDamageSource() + ")");
			helper.assertFalse(stuck(level, villager), "B185: the villager is inside " + what);
			helper.assertTrue(freedAt[0] != null, "B185: the villager was never out of " + what);
			helper.assertTrue(freedAt[0].distanceTo(was) < 6, "the villager was moved too far: " + freedAt[0].distanceTo(was));
			villager.discard();
			helper.succeed();
		});
	}

	/** The villager's body, a tenth of a block in from its edges, overlaps a block's collision shape (an open door's aside). */
	private static boolean stuck(ServerLevel level, Villager villager) {
		AABB body = villager.getBoundingBox().deflate(0.1);
		for (BlockPos p : BlockPos.betweenClosed(Mth.floor(body.minX), Mth.floor(body.minY), Mth.floor(body.minZ),
				Mth.floor(body.maxX), Mth.floor(body.maxY), Mth.floor(body.maxZ))) {
			BlockState state = level.getBlockState(p);
			var shape = state.getCollisionShape(level, p);
			if (shape.isEmpty()) {
				continue;
			}
			for (AABB box : shape.toAabbs()) {
				if (box.move(p).intersects(body)) {
					return true;
				}
			}
		}
		return false;
	}
}
