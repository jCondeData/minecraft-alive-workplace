package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 23.2, stuck recovery proven: a builder builds the starter cottage while, every 150 to 300 ticks, something traps it
 * where it stands (anywhere but on the build's own blocks): a water pool, a lava pool across its way, a hole, a ring of
 * fences, a little room with a door, a hop up onto its own build, or its chunk unloading (the villager saved and loaded
 * again). Five fixed seeds, each its
 * own test and batch (the tester skill's chaos tests), so a failure can be replayed by its name.
 *
 * <p>Each must: finish the build with every block right; get out of every trap (or keep building from inside it)
 * within 600 ticks; never go 1,200 ticks on shift without moving on; and never break a block of the build once it was
 * placed (recovery is a hop, a re-path or a step back, never digging through its own work).
 */
public class BuilderChaosGameTests {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	static final long[] SEEDS = {23_201L, 23_202L, 23_203L, 23_204L, 23_205L};
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos ORIGIN = new BlockPos(14, 2, 14);
	/** A new trap every 150 to 300 ticks (the last one is taken away first). */
	private static final int MIN_GAP = 150;
	private static final int MAX_GAP = 300;
	/** Fewer traps than this and the run proved little: the build was too quick. */
	private static final int MIN_TRAPS = 8;
	private static final int ESCAPE_TICKS = 600;
	private static final int STALL_TICKS = 1200;
	private static final int TIMEOUT = 30_000;

	enum Trap { WATER, LAVA, HOLE, FENCES, DOOR_ROOM, ON_ITS_BUILD, UNLOAD }

	@GameTestGenerator
	public Collection<TestFunction> chaos() {
		List<TestFunction> out = new ArrayList<>();
		for (long seed : SEEDS) {
			String name = "builder_chaos_" + seed;
			out.add(new TestFunction(name, name, HUGE_AREA, TIMEOUT, 0, true, helper -> run(helper, seed)));
		}
		return out;
	}

	static void run(GameTestHelper helper, long seed) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		RandomSource rnd = RandomSource.create(seed);
		AliveWorkplace.LOG.info("[chaos] builder seed {}", seed);
		helper.setDayTime(2000);
		GameRules rules = level.getGameRules();
		boolean daylight = rules.getBoolean(GameRules.RULE_DAYLIGHT);
		boolean fire = rules.getBoolean(GameRules.RULE_DOFIRETICK);
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
		rules.getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			rules.getRule(GameRules.RULE_DAYLIGHT).set(daylight, level.getServer());
			rules.getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer());
		});
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());

		StarterBlueprints.Entry entry = StarterBlueprints.STARTER_COTTAGE;
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id())
			.orElseThrow(() -> new GameTestAssertException("missing starter blueprint " + entry.id()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE);
		BuildPlan materials = BuildPlan.create(blueprint, placement, level, rules.getInt(ModGameRules.FOUNDATION_DEPTH), 0);
		stock(helper, materials.materials());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager first = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, first, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, first, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + entry.id());
		}
		// Traps go anywhere but on the build's own blocks; lava stays well clear of it (keepOut).
		BoundingBox keepOut = plan.bounds().inflatedBy(1);
		Map<BlockPos, BlockState> wanted = new HashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				wanted.put(step.pos(), step.state());
				if (step.secondaryPos() != null) {
					wanted.put(step.secondaryPos(), step.secondaryState()); // a door's top half is the build's too
				}
			}
		}

		Villager[] builder = {first};
		Set<BlockPos> placed = new HashSet<>();
		Map<BlockPos, BlockState> trapBlocks = new LinkedHashMap<>();
		List<String> log = new ArrayList<>();
		int[] ticks = {0};
		int[] nextTrap = {MIN_GAP + rnd.nextInt(MAX_GAP - MIN_GAP)};
		int[] traps = {0};
		int[] since = {0};
		long[] last = {Long.MIN_VALUE};
		Trap[] trap = {null};
		Vec3[] trapAt = {null};
		int[] trapTick = {0};
		long[] trapMark = {0};
		boolean[] escaped = {true};
		String[] broken = {null};
		String[] stuck = {null};

		helper.onEachTick(() -> {
			if (site.isDone() || BuildSiteManager.get(level).get(site.id()) == null || broken[0] != null || stuck[0] != null) {
				removeTrap(level, trapBlocks); // the last trap goes, so the finished build can be checked
				return;
			}
			int t = ++ticks[0];
			Villager v = builder[0];
			long mark = TerrainStallGameTests.mark(site);
			// The last trap: out of it, or still building from inside it, within ESCAPE_TICKS.
			if (trap[0] != null && !escaped[0]) {
				if (mark != trapMark[0] || v.position().distanceTo(trapAt[0]) > 2.5) {
					escaped[0] = true;
					log.add(trap[0] + " left after " + (t - trapTick[0]));
				} else if (t - trapTick[0] > ESCAPE_TICKS) {
					stuck[0] = trap[0] + " at " + BlockPos.containing(trapAt[0]).subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " held the builder " + ESCAPE_TICKS
						+ " ticks (now at " + helper.relativeVec(v.position()) + ", stage " + site.stage() + "); seed " + seed + ", " + log;
					return;
				}
			}
			// Never long on shift without progress.
			boolean onShift = !v.isSleeping() && v.getBrain().isActive(Activity.WORK);
			if (mark != last[0] || !onShift) {
				last[0] = mark;
				since[0] = 0;
			} else if (++since[0] > STALL_TICKS) {
				stuck[0] = "no progress for " + STALL_TICKS + " ticks on shift: stage " + site.stage() + ", " + site.placed()
					+ " placed, builder at " + helper.relativeVec(v.position()) + "; seed " + seed + ", " + log;
				return;
			}
			// No block of the build, once placed, is ever taken away again.
			if (t % 5 == 0) {
				for (Map.Entry<BlockPos, BlockState> e : wanted.entrySet()) {
					boolean right = MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue());
					if (right) {
						placed.add(e.getKey());
					} else if (placed.contains(e.getKey())) {
						broken[0] = "the placed " + e.getValue() + " at " + helper.relativePos(e.getKey()) + " is now "
							+ level.getBlockState(e.getKey()) + " (stage " + site.stage() + "); seed " + seed + ", " + log;
						return;
					}
				}
			}
			if (t < nextTrap[0] || !escaped[0]) {
				return; // a trap stays until the builder is out of it (or the escape check above fails the test)
			}
			Trap next = Trap.values()[rnd.nextInt(Trap.values().length)];
			nextTrap[0] = t + MIN_GAP + rnd.nextInt(MAX_GAP - MIN_GAP);
			traps[0]++;
			removeTrap(level, trapBlocks);
			BlockPos feet = v.blockPosition();
			String what = spring(helper, next, builder, feet, keepOut, plan, wanted, trapBlocks, rnd);
			log.add(t + ":" + what);
			trap[0] = next;
			trapAt[0] = builder[0].position();
			trapTick[0] = t;
			trapMark[0] = mark;
			escaped[0] = next == Trap.UNLOAD; // nothing to get out of: the stall check covers it
		});
		helper.succeedWhen(() -> {
			if (broken[0] != null) {
				helper.fail(broken[0]);
			}
			if (stuck[0] != null) {
				helper.fail(stuck[0]);
			}
			helper.assertTrue(builder[0].isAlive(), "the builder died: " + builder[0].getRemovalReason() + "; seed " + seed + ", " + log);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: stage " + site.stage() + ", " + site.placed() + " placed; seed " + seed + ", " + log);
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build, e.g. "
				+ unfinished.stream().limit(3).map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList() + "; seed " + seed);
			helper.assertTrue(traps[0] >= MIN_TRAPS, "only " + traps[0] + " traps before the build was done; seed " + seed);
			helper.assertTrue(site.skipped() == 0, site.skipped() + " step(s) skipped; seed " + seed + ", " + log);
			AliveWorkplace.LOG.info("[chaos] builder seed {} finished in {} ticks: {}", seed, ticks[0], log);
		});
	}

	/** Sets the trap around the builder; returns what was done, for the log. */
	private static String spring(GameTestHelper helper, Trap trap, Villager[] builder, BlockPos feet, BoundingBox keepOut,
								 BuildPlan plan, Map<BlockPos, BlockState> wanted, Map<BlockPos, BlockState> trapBlocks, RandomSource rnd) {
		ServerLevel level = helper.getLevel();
		int floor = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
		BlockPos ground = new BlockPos(feet.getX(), floor, feet.getZ());
		switch (trap) {
			case WATER -> {
				// A pool sunk into the floor where it stands: it drops in and swims.
				for (BlockPos p : BlockPos.betweenClosed(ground.offset(-1, 0, -1), ground.offset(1, 0, 1))) {
					sink(level, p, Blocks.WATER.defaultBlockState(), wanted, trapBlocks);
				}
			}
			case LAVA -> {
				// A pool of lava three blocks away towards the build: its way there goes round it.
				BlockPos centre = plan.bounds().getCenter();
				Direction towards = Direction.getNearest(centre.getX() - feet.getX(), 0, centre.getZ() - feet.getZ());
				BlockPos pool = ground.relative(towards, 3);
				for (BlockPos p : BlockPos.betweenClosed(pool.offset(-1, 0, -1), pool.offset(1, 0, 1))) {
					if (!keepOut.inflatedBy(3).isInside(p)) {
						sink(level, p, Blocks.LAVA.defaultBlockState(), wanted, trapBlocks);
					}
				}
			}
			case HOLE -> ring(level, feet, 2, Blocks.COBBLESTONE.defaultBlockState(), wanted, trapBlocks, null);
			case FENCES -> ring(level, feet, 1, Blocks.OAK_FENCE.defaultBlockState(), wanted, trapBlocks, null);
			case DOOR_ROOM -> {
				BlockPos centre = plan.bounds().getCenter();
				Direction towards = Direction.getNearest(centre.getX() - feet.getX(), 0, centre.getZ() - feet.getZ());
				ring(level, feet, 2, Blocks.COBBLESTONE.defaultBlockState(), wanted, trapBlocks, feet.relative(towards));
			}
			case ON_ITS_BUILD -> {
				// Up on top of what it has built so far, as if it had climbed its own scaffold.
				BlockPos top = null;
				for (BlockPos p : wanted.keySet()) {
					BlockState now = level.getBlockState(p);
					if (MaterialRules.matches(now, wanted.get(p)) && now.isFaceSturdy(level, p, Direction.UP)
						&& level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()
						&& (top == null || p.getY() > top.getY() || p.getY() == top.getY() && rnd.nextBoolean())) {
						top = p.immutable();
					}
				}
				if (top == null) {
					return "on its build (nothing built yet)";
				}
				builder[0].teleportTo(top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5);
				return "on its build at " + top.above().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
			}
			case UNLOAD -> {
				// The chunk unloads and loads again: the builder comes back from its save, as a new entity.
				Villager old = builder[0];
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(old.save(tag), "the builder wasn't saved");
				old.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof Villager, "the builder's save didn't load as a villager: " + loaded);
				level.addFreshEntity(loaded);
				builder[0] = (Villager) loaded;
				return "unloaded";
			}
		}
		return trap.name().toLowerCase() + " at " + feet.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	/** Puts a fluid in the floor at {@code p}, where nothing stands on it and it is clear of the build. */
	private static void sink(ServerLevel level, BlockPos p, BlockState fluid, Map<BlockPos, BlockState> wanted, Map<BlockPos, BlockState> trapBlocks) {
		if (wanted.containsKey(p) || wanted.containsKey(p.above()) || !level.getBlockState(p.above()).isAir()
			|| !level.getBlockState(p).isSolid() || trapBlocks.containsKey(p)) {
			return;
		}
		trapBlocks.put(p.immutable(), level.getBlockState(p));
		level.setBlockAndUpdate(p, fluid);
	}

	/** A ring of {@code height} blocks round {@code feet} on the air cells clear of the build, with an optional door. */
	private static void ring(ServerLevel level, BlockPos feet, int height, BlockState block, Map<BlockPos, BlockState> wanted,
							 Map<BlockPos, BlockState> trapBlocks, BlockPos door) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				for (int dy = 0; dy < height; dy++) {
					BlockPos p = feet.offset(dx, dy, dz);
					if (wanted.containsKey(p) || !level.getBlockState(p).isAir() || trapBlocks.containsKey(p)) {
						continue;
					}
					BlockState state = block;
					if (door != null && p.getX() == door.getX() && p.getZ() == door.getZ()) {
						Direction facing = Direction.getNearest(door.getX() - feet.getX(), 0, door.getZ() - feet.getZ());
						state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
							.setValue(DoorBlock.HALF, dy == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
					}
					trapBlocks.put(p.immutable(), Blocks.AIR.defaultBlockState());
					level.setBlock(p, state, 2);
				}
			}
		}
	}

	/** Puts back what the last trap replaced. */
	private static void removeTrap(ServerLevel level, Map<BlockPos, BlockState> trapBlocks) {
		for (Map.Entry<BlockPos, BlockState> e : trapBlocks.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), 2);
		}
		trapBlocks.clear();
	}

	private static void stock(GameTestHelper helper, Map<Item, Integer> needs) {
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : needs.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		stock.add(new ItemStack(Items.DIRT, 64));
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "the test needs more barrels");
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}
}
