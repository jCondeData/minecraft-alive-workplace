package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildBudget;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildReserve;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA (lane-b-1116, tester b) for ROADMAP 25.3 and 25.4, the builders' per-tick budgets, from the spec: builders change
 * at most {@link BuildBudget#CHANGES_PER_TICK} blocks a level a tick between them; past it a builder waits a tick and
 * nothing is lost; the budget is never saved and a fresh one starts every tick; it is per level. Recounts of other
 * sites' needs read at most {@link BuildBudget#RECOUNT_READS_PER_TICK} blocks a tick a level; a recount once started
 * finishes; past it the last count is reused, or for a site never counted its whole plan is kept back, until a later
 * tick. Real builders work real sites (their AI ticking) wherever the budget meets them; block changes are counted from
 * the world each tick, not from the mod's own counters.
 */
public class QaBuildBudget1116bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final int CAP = BuildBudget.CHANGES_PER_TICK;

	// ------------------------------------------------------------------------------------------------ shared helpers

	private static void log(GameTestHelper helper, String line) {
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa-budget-1116b] " + line));
	}

	/** Free materials, the fastest build delay (a block every tick per builder), no helping; restored afterwards. */
	private static ServerLevel rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		return level;
	}

	/** {@code n} builders (up to 16), each with his own bench and a test hut, on a 4 x 4 grid in the huge area. */
	private static Crew crew(GameTestHelper helper, int n) {
		ServerLevel level = helper.getLevel();
		Villager[] builders = new Villager[n];
		UUID[] ids = new UUID[n];
		BuildPlan[] plans = new BuildPlan[n];
		for (int i = 0; i < n; i++) {
			int ox = 1 + 7 * (i % 4);
			int oz = 3 + 7 * (i / 4);
			BlockPos bench = new BlockPos(ox + 5, 2, oz);
			helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
			builders[i] = helper.spawn(EntityType.VILLAGER, new BlockPos(ox + 5, 2, oz + 2));
			Builders.employ(level, builders[i], helper.absolutePos(bench));
			BuildSite site = Builders.start(level, builders[i], null, TEST_HUT,
				new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(ox, 2, oz)), Rotation.NONE, Mirror.NONE));
			plans[i] = site.plan(level);
			if (plans[i] == null) {
				throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
			}
			ids[i] = site.id();
			Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		}
		return new Crew(level, builders, ids, plans);
	}

	private record Crew(ServerLevel level, Villager[] builders, UUID[] ids, BuildPlan[] plans) {
	}

	/** Every block in the area's lowest rows (floor to two over the huts' roofs), in a fixed order. */
	private static final class Watch {
		private final List<BlockPos> positions = new ArrayList<>();
		private BlockState[] last;

		Watch(GameTestHelper helper, int sizeX, int maxY, int sizeZ) {
			for (int x = 0; x < sizeX; x++) {
				for (int y = 1; y <= maxY; y++) {
					for (int z = 0; z < sizeZ; z++) {
						positions.add(helper.absolutePos(new BlockPos(x, y, z)));
					}
				}
			}
			last = read(helper.getLevel());
		}

		private BlockState[] read(ServerLevel level) {
			BlockState[] now = new BlockState[positions.size()];
			for (int i = 0; i < now.length; i++) {
				now[i] = level.getBlockState(positions.get(i));
			}
			return now;
		}

		/**
		 * Blocks that became a different block since the last call. A door's top half goes in with its bottom (one block
		 * change), so it isn't counted on its own; a door opening (same block, other state) isn't a change by a builder.
		 */
		int changes(ServerLevel level, List<String> sample) {
			BlockState[] now = read(level);
			int changed = 0;
			for (int i = 0; i < now.length; i++) {
				Block was = last[i].getBlock();
				if (was != now[i].getBlock() && !upperHalf(last[i]) && !upperHalf(now[i])) {
					changed++;
					if (sample != null && sample.size() < 16) {
						sample.add(positions.get(i).toShortString() + " " + last[i].getBlock().getDescriptionId() + "->" + now[i].getBlock().getDescriptionId());
					}
				}
			}
			last = now;
			return changed;
		}

		private static boolean upperHalf(BlockState state) {
			return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
		}
	}

	/** A server restart: the build sites and every builder saved and loaded (each builder a fresh entity). */
	private static void restart(GameTestHelper helper, Villager[] builders) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		for (int i = 0; i < builders.length; i++) {
			Villager old = builders[i];
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(old.save(tag), "builder " + i + " wasn't saved");
			old.discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "builder " + i + " didn't load as a villager: " + loaded);
			level.addFreshEntity(loaded);
			builders[i] = (Villager) loaded;
		}
	}

	/** Takes every block change {@code level} allows at {@code gameTime}, so builders there get none that tick. */
	private static void spend(Object level, long gameTime) {
		int guard = 0;
		while (BuildBudget.take(level, gameTime) && guard++ < 1000) {
			// spend it all
		}
	}

	/** Each site's progress as last seen while it was still on the list (a finished site leaves it). */
	private static final class Progress {
		final int[] placed;
		final int[] skipped;

		Progress(int n) {
			placed = new int[n];
			skipped = new int[n];
		}

		int update(ServerLevel level, UUID[] ids) {
			int live = 0;
			for (int i = 0; i < ids.length; i++) {
				BuildSite site = BuildSiteManager.get(level).get(ids[i]);
				if (site != null) {
					placed[i] = site.placed();
					skipped[i] = site.skipped();
					live++;
				}
			}
			return live;
		}

		int total() {
			int t = 0;
			for (int p : placed) {
				t += p;
			}
			return t;
		}
	}

	/** Every hut exactly as drawn, nothing skipped, and every builder placed as many blocks as every other. */
	private static void assertAllBuilt(GameTestHelper helper, Crew c, Progress p, String context) {
		for (int i = 0; i < c.ids().length; i++) {
			List<BlockPos> unfinished = c.plans()[i].unfinished(c.level());
			helper.assertTrue(unfinished.isEmpty(), "hut " + i + " (" + context + ") has " + unfinished.size() + " block(s) unlike its blueprint, e.g. "
				+ unfinished.stream().limit(4).map(pos -> helper.relativePos(pos).toShortString() + "=" + c.level().getBlockState(pos)).toList());
			helper.assertTrue(p.skipped[i] == 0, "hut " + i + " (" + context + ") skipped " + p.skipped[i] + " step(s): the budget lost work");
			helper.assertTrue(p.placed[i] == p.placed[0], "hut " + i + " (" + context + ") placed " + p.placed[i] + " blocks, hut 0 placed " + p.placed[0]
				+ ": same blueprint, same ground, so the same work");
		}
	}

	// ------------------------------------------------------------------------------------------------ a crowd of builders

	/**
	 * Sixteen builders, each building his own test hut a block a tick, all in one level: in no tick does the world change
	 * by more than 12 blocks (counted from the world, every block in the huts' rows), the budget really is reached (the
	 * crowd wants more than 12 in some ticks), and every hut still finishes exactly as drawn with no step skipped, each
	 * builder having placed as many blocks as every other (nothing lost to a refused tick).
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaBudget1116bCrowd"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaBudget1116bCrowd")
	public void sixteenBuildersChangeAtMostTwelveBlocksATickAndLoseNothing(GameTestHelper helper) {
		crowd(helper, false);
	}

	/**
	 * The same crowd with a server restart (the build sites and all sixteen builders saved and loaded) in a tick where the
	 * budget had been reached: the budget isn't saved, so the builders go on in the next tick, still never past 12 a
	 * tick, and every hut finishes exactly as drawn with nothing skipped or lost.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaBudget1116bRestart"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaBudget1116bRestart")
	public void aRestartWhileTheCrowdIsHeldBackLosesNothing(GameTestHelper helper) {
		crowd(helper, true);
	}

	private static void crowd(GameTestHelper helper, boolean restartMid) {
		rules(helper);
		Crew c = crew(helper, 16);
		ServerLevel level = c.level();
		Watch watch = new Watch(helper, 30, 9, 30);
		Progress progress = new Progress(16);
		int hutSteps = c.plans()[0].steps(BuildPlan.Stage.STRUCTURE).size() + c.plans()[0].steps(BuildPlan.Stage.DECORATION).size();
		int[] max = {0};
		int[] atCap = {0};
		int[] ticks = {0};
		int[] changedAll = {0};
		String[] restarted = {restartMid ? null : "no restart"};
		helper.onEachTick(() -> {
			ticks[0]++;
			List<String> sample = new ArrayList<>();
			int changes = watch.changes(level, sample);
			changedAll[0] += changes;
			if (changes > CAP) {
				throw new GameTestAssertException(changes + " blocks changed in one tick (tick " + ticks[0] + ", budget " + CAP + "): " + sample);
			}
			max[0] = Math.max(max[0], changes);
			if (changes == CAP) {
				atCap[0]++;
			}
			int live = progress.update(level, c.ids());
			if (restarted[0] == null && changes == CAP && live == 16 && progress.total() >= 16 * hutSteps / 4) {
				restart(helper, c.builders());
				restarted[0] = "tick " + ticks[0] + ", " + progress.total() + " blocks placed in all";
				log(helper, "restarted the crowd at " + restarted[0]);
			}
		});
		helper.succeedWhen(() -> {
			for (int i = 0; i < 16; i++) {
				BuildSite live = BuildSiteManager.get(level).get(c.ids()[i]);
				if (live != null) {
					throw new GameTestAssertException("hut " + i + " still building at tick " + ticks[0] + " (" + live.stage() + ", placed "
						+ progress.placed[i] + "); restart: " + restarted[0]);
				}
			}
			helper.assertTrue(restarted[0] != null, "never restarted: no tick at the budget with every hut a quarter built");
			helper.assertTrue(atCap[0] > 0, "the crowd never reached the budget (most in a tick " + max[0] + "): the test didn't hold anyone back");
			assertAllBuilt(helper, c, progress, "restart: " + restarted[0]);
			log(helper, (restartMid ? "restart" : "crowd") + ": 16 huts built exactly in " + ticks[0] + " ticks, " + changedAll[0]
				+ " block changes, at most " + max[0] + " a tick, " + atCap[0] + " ticks at the budget of " + CAP + ", each hut "
				+ progress.placed[0] + " placed, 0 skipped; restart " + restarted[0]);
		});
	}

	// ------------------------------------------------------------------------------------------------ the boundary

	/**
	 * The budget's edge from both sides on the real levels: 12 changes in a tick are allowed and the 13th and 14th are
	 * refused; the next tick (and one after a gap) starts fresh; the Nether has its own 12 in the same tick the overworld's
	 * are spent; and in the live tick, through the builders' own call, no more than 12 are given.
	 */
	//$ gametest_batch EMPTY_STRUCTURE '"qaBudget1116bEdge"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaBudget1116bEdge")
	public void twelveATickThenRefusedThenFreshAndEachLevelItsOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the server has no Nether level");
		long t = level.getGameTime() + 100_000L; // a tick no builder has reached yet
		for (int i = 1; i <= CAP; i++) {
			helper.assertTrue(BuildBudget.take(level, t), "change " + i + " of " + CAP + " refused");
		}
		helper.assertFalse(BuildBudget.take(level, t), "change " + (CAP + 1) + " in one tick allowed");
		helper.assertFalse(BuildBudget.take(level, t), "change " + (CAP + 2) + " in one tick allowed (asking again after a refusal)");
		int nether12 = 0;
		while (BuildBudget.take(nether, t) && nether12 < 100) {
			nether12++;
		}
		helper.assertTrue(nether12 == CAP, "the Nether gave " + nether12 + " changes in the tick the overworld's were spent (want its own " + CAP + ")");
		helper.assertFalse(BuildBudget.take(level, t), "the Nether's budget refilled the overworld's");
		int next = 0;
		while (BuildBudget.take(level, t + 1) && next < 100) {
			next++;
		}
		helper.assertTrue(next == CAP, "the next tick gave " + next + " changes (want a fresh " + CAP + ")");
		int gap = 0;
		while (BuildBudget.take(level, t + 40) && gap < 100) {
			gap++;
		}
		helper.assertTrue(gap == CAP, "a tick after a 39-tick gap gave " + gap + " (want " + CAP + ")");
		// The live tick, through take(ServerLevel): at most 12, then refused.
		int live = 0;
		while (BuildBudget.take(level) && live < 100) {
			live++;
		}
		helper.assertTrue(live <= CAP, "the live tick gave " + live + " changes");
		helper.assertFalse(BuildBudget.take(level), "the live tick still gave a change after " + live);
		long liveTick = level.getGameTime();
		final int liveGiven = live;
		final int netherGiven = nether12;
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(level.getGameTime() > liveTick, "no tick went by");
			int n = 0;
			while (BuildBudget.take(level) && n < 100) {
				n++;
			}
			helper.assertTrue(n == CAP, "a later live tick gave " + n + " changes (want a fresh " + CAP + "; no builders here)");
			log(helper, "edge: " + CAP + " allowed, 13th/14th refused, next tick and after a gap " + CAP + ", Nether " + netherGiven
				+ " in the same tick, live tick " + liveGiven + " then refused, later live tick " + n);
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------------ a builder starved

	/**
	 * One builder in the overworld. While the Nether's budget is spent every tick, he builds on as usual (the budget is
	 * per level). While the overworld's is spent every tick for 100 ticks, not one block changes and his site doesn't move
	 * on (he waits, a tick at a time). Once it is free again he finishes the hut exactly as drawn, nothing skipped.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaBudget1116bStarved"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaBudget1116bStarved")
	public void aBuilderWithNoBudgetWaitsAndAnotherLevelsDoesntStopHim(GameTestHelper helper) {
		rules(helper);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the server has no Nether level");
		Crew c = crew(helper, 1);
		ServerLevel level = c.level();
		Watch watch = new Watch(helper, 12, 9, 12);
		Progress progress = new Progress(1);
		int hutSteps = c.plans()[0].steps(BuildPlan.Stage.STRUCTURE).size();
		// phase 0: the Nether starved until the hut is a quarter built; 1: the overworld starved for 100 ticks; 2: free
		int[] phase = {0};
		int[] ticks = {0};
		int[] starvedAt = {0};
		int[] placedAtStarve = {0};
		int[] netherTicks = {0};
		int[] changedWhileNetherStarved = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			long next = level.getGameTime() + 1;
			List<String> sample = new ArrayList<>();
			int changes = watch.changes(level, sample);
			progress.update(level, c.ids());
			if (phase[0] == 0) {
				changedWhileNetherStarved[0] += changes;
				netherTicks[0]++;
				helper.assertFalse(BuildBudget.take(nether, level.getGameTime()) && netherTicks[0] > 1,
					"the Nether's budget, spent for this tick, gave a change");
				if (progress.placed[0] >= hutSteps / 4) {
					phase[0] = 1;
					starvedAt[0] = ticks[0];
					placedAtStarve[0] = progress.placed[0];
				} else {
					helper.assertTrue(ticks[0] < 600, "with only the Nether's budget spent the builder placed " + progress.placed[0] + " blocks in 600 ticks");
					spend(nether, next);
					return;
				}
			}
			if (phase[0] == 1) {
				if (ticks[0] > starvedAt[0] + 1) { // the tick he was starved from onwards
					if (changes != 0) {
						throw new GameTestAssertException(changes + " block(s) changed in a tick with the level's budget spent: " + sample);
					}
					helper.assertTrue(progress.placed[0] == placedAtStarve[0], "the site moved on from " + placedAtStarve[0] + " to " + progress.placed[0]
						+ " placed with no budget");
				}
				if (ticks[0] < starvedAt[0] + 100) {
					spend(level, next);
					helper.assertFalse(BuildBudget.take(level, next), "the spent budget still gave a change");
				} else {
					phase[0] = 2;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "still in phase " + phase[0] + " at tick " + ticks[0] + ", placed " + progress.placed[0]);
			BuildSite live = BuildSiteManager.get(level).get(c.ids()[0]);
			helper.assertTrue(live == null, "still building at tick " + ticks[0] + ": " + (live == null ? "" : live.stage() + ", placed " + live.placed()));
			helper.assertTrue(changedWhileNetherStarved[0] > 0, "nothing changed while only the Nether was starved");
			assertAllBuilt(helper, c, progress, "starved 100 ticks from tick " + starvedAt[0]);
			log(helper, "starved: " + changedWhileNetherStarved[0] + " changes in " + netherTicks[0] + " ticks with the Nether's budget spent, "
				+ "then 0 in 100 ticks with the overworld's, then the hut finished exactly at tick " + ticks[0] + " (placed " + progress.placed[0] + ", 0 skipped)");
		});
	}

	// ------------------------------------------------------------------------------------------------ the recount budget

	private record Store(ServerLevel level, BuildSite site, BuildPlan plan, BuildSite second, BuildPlan secondPlan, List<BlockPos> chests) {
	}

	private static final BlockPos BENCH = new BlockPos(1, 2, 1);
	private static final BlockPos STORE = new BlockPos(1, 2, 3);
	private static final Item PLANKS = Items.OAK_PLANKS;

	/**
	 * A builder (standing still) with a test hut to build and a second one queued, and a store chest by his bench with 55
	 * oak planks, all the hut's. Ten of each hut's plank blocks are already in the world, so each needs 10 fewer than its
	 * plan says.
	 */
	private static Store store(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(STORE, Blocks.CHEST);
		((Container) helper.getBlockEntity(STORE)).setItem(0, new ItemStack(PLANKS, 55));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(4, 2, 4)), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		BuildSite second = Builders.enqueue(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(10, 2, 4)), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(second.id()));
		BuildPlan plan = site.plan(level);
		BuildPlan secondPlan = second.plan(level);
		if (plan == null || secondPlan == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		putIn(level, site, plan, 10);
		putIn(level, second, secondPlan, 10);
		return new Store(level, site, plan, second, secondPlan, List.of(helper.absolutePos(STORE)));
	}

	/** Puts {@code n} more of the site's plank blocks in the world by hand (the site doesn't move on). */
	private static void putIn(ServerLevel level, BuildSite site, BuildPlan plan, int n) {
		int done = 0;
		for (BuildPlan.Step step : site.upcoming(plan, Integer.MAX_VALUE)) {
			if (done < n && !step.requirements().isEmpty() && MaterialFamilies.key(step.requirements().get(0).item()) == MaterialFamilies.key(PLANKS)
				&& !MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
				level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS);
				done++;
			}
		}
		if (done < n) {
			throw new GameTestAssertException("only " + done + " plank blocks to put in");
		}
	}

	/** The site's whole remaining plan in planks, the world not looked at. */
	private static int planned(BuildSite site, BuildPlan plan) {
		int n = 0;
		for (BuildPlan.Step step : site.upcoming(plan, Integer.MAX_VALUE)) {
			for (MaterialRules.Requirement r : step.requirements()) {
				if (MaterialFamilies.key(r.item()) == MaterialFamilies.key(PLANKS)) {
					n += r.count();
				}
			}
		}
		return n;
	}

	private static int exact(Store s, BuildSite site, BuildPlan plan) {
		return Builders.remainingNeed(s.level(), site, plan).getOrDefault(MaterialFamilies.key(PLANKS), 0);
	}

	private static int reserved(Store s, BuildSite site) {
		return BuildReserve.of(s.level(), s.chests(), List.of(site)).reserved(MaterialFamilies.key(PLANKS));
	}

	/**
	 * A hut never counted, asked about in a tick whose recount reads are spent: the store keeps back its whole plan of
	 * planks (none of the store's 55 is spare, though 10 of its blocks are in already) and nothing is read; the next tick
	 * counts it from the world, and the 10 planks are spare again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBudget1116bRefused"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBudget1116bRefused")
	public void aRefusedRecountKeepsTheWholePlanBackUntilALaterTick(GameTestHelper helper) {
		Store s = store(helper);
		ServerLevel level = s.level();
		int plan = planned(s.site(), s.plan());
		int exact = exact(s, s.site(), s.plan());
		helper.assertTrue(plan == exact + 10, "the hut's plan takes " + plan + " planks, the world says " + exact + " are still needed (want 10 fewer)");
		long now = level.getGameTime();
		long before = BuildBudget.readsUsed(level, now);
		BuildBudget.read(level, now, (int) Math.max(0, BuildBudget.RECOUNT_READS_PER_TICK - before));
		long spentAt = BuildBudget.readsUsed(level, now);
		BuildReserve refused = BuildReserve.of(level, s.chests(), List.of(s.site()));
		int keptBack = refused.reserved(MaterialFamilies.key(PLANKS));
		helper.assertTrue(keptBack == plan, "with the recount refused the store keeps back " + keptBack + " planks (want the whole plan, " + plan + ")");
		helper.assertTrue(refused.spare(level, PLANKS) == 0, "with the recount refused the store has " + refused.spare(level, PLANKS) + " spare planks (want 0)");
		helper.assertTrue(BuildBudget.readsUsed(level, now) == spentAt, "a refused recount read " + (BuildBudget.readsUsed(level, now) - spentAt) + " blocks");
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(level.getGameTime() > now, "no tick went by");
			BuildReserve later = BuildReserve.of(level, s.chests(), List.of(s.site()));
			int counted = later.reserved(MaterialFamilies.key(PLANKS));
			helper.assertTrue(counted == exact, "a later tick keeps back " + counted + " planks (want the counted " + exact + ")");
			helper.assertTrue(later.spare(level, PLANKS) == 55 - exact, "a later tick has " + later.spare(level, PLANKS) + " spare (want " + (55 - exact) + ")");
			helper.assertTrue(BuildBudget.readsUsed(level, level.getGameTime()) > 0, "the later count read nothing");
			log(helper, "refused: plan " + plan + " kept back with reads spent (0 spare), next tick " + counted + " (" + (55 - exact) + " spare)");
			helper.succeed();
		});
	}

	/**
	 * A recount that starts with one read left in the tick finishes (the count is exact and every one of the site's steps
	 * is read, past the budget); the next never-counted site that tick is refused and keeps its whole plan back, and is
	 * counted exactly in a later tick.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBudget1116bLastRead"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBudget1116bLastRead")
	public void aRecountStartedOnTheLastReadFinishes(GameTestHelper helper) {
		Store s = store(helper);
		ServerLevel level = s.level();
		long now = level.getGameTime();
		int budget = BuildBudget.RECOUNT_READS_PER_TICK;
		BuildBudget.read(level, now, (int) Math.max(0, budget - 1 - BuildBudget.readsUsed(level, now)));
		helper.assertTrue(BuildBudget.readsUsed(level, now) == budget - 1, "couldn't set up one read left: " + BuildBudget.readsUsed(level, now));
		int steps = s.site().upcoming(s.plan(), Integer.MAX_VALUE).size();
		int exact = exact(s, s.site(), s.plan());
		int first = reserved(s, s.site());
		long read = BuildBudget.readsUsed(level, now) - (budget - 1);
		helper.assertTrue(first == exact, "the recount started on the last read gave " + first + " planks (want the exact " + exact + ")");
		helper.assertTrue(read >= steps, "the recount read " + read + " blocks of the site's " + steps + " steps: cut short");
		int secondPlan = planned(s.second(), s.secondPlan());
		int secondExact = exact(s, s.second(), s.secondPlan());
		int second = reserved(s, s.second());
		helper.assertTrue(second == secondPlan, "the second hut, over the budget, kept back " + second + " planks (want its whole plan, " + secondPlan + ")");
		helper.runAtTickTime(2, () -> {
			int later = reserved(s, s.second());
			helper.assertTrue(later == secondExact, "a later tick kept back " + later + " for the second hut (want " + secondExact + ")");
			log(helper, "last read: " + steps + "-step recount from " + (budget - 1) + " reads finished (" + read + " read, " + first
				+ " planks exact); the next hut kept " + second + " back, then " + later + " a tick later");
			helper.succeed();
		});
	}

	/**
	 * A hut counted once, then moved on (ten more planks in, a supply run): asked again in a tick with the reads spent, the
	 * last count stands (not the whole plan, not a new count); the next tick counts it again from the world.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBudget1116bReused"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBudget1116bReused")
	public void pastTheReadsTheLastCountIsReused(GameTestHelper helper) {
		Store s = store(helper);
		ServerLevel level = s.level();
		long now = level.getGameTime();
		int plan = planned(s.site(), s.plan());
		int counted = reserved(s, s.site());
		int exact = exact(s, s.site(), s.plan());
		helper.assertTrue(counted == exact, "the first count kept back " + counted + " (want " + exact + ")");
		putIn(level, s.site(), s.plan(), 10);
		s.site().supplied(); // the site has moved on
		int newExact = exact(s, s.site(), s.plan());
		helper.assertTrue(newExact == exact - 10, "ten more planks in, the world says " + newExact + " (want " + (exact - 10) + ")");
		BuildBudget.read(level, now, (int) Math.max(0, BuildBudget.RECOUNT_READS_PER_TICK - BuildBudget.readsUsed(level, now)));
		int stale = reserved(s, s.site());
		helper.assertTrue(stale == counted, "with the reads spent it kept back " + stale + " (want the last count, " + counted + "; plan " + plan
			+ ", new count " + newExact + ")");
		helper.runAtTickTime(2, () -> {
			int later = reserved(s, s.site());
			helper.assertTrue(later == newExact, "a later tick kept back " + later + " (want the new count " + newExact + ")");
			log(helper, "reused: counted " + counted + ", moved on, reads spent -> " + stale + " (last count), next tick " + later);
			helper.succeed();
		});
	}
}
