package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-03), tests that kill two mutants the nightly mutation run left alive in the builder.
 * <ul>
 * <li>B865 (MaterialRules.matches): a scanned melon's or pumpkin's grown (attached) stem is planted as a fresh stem, paid
 * with one seeds, and that fresh stem counts as built: the build finishes with nothing skipped, pays one seeds per stem
 * and never digs the fresh stem up or plants it again. The older tests let random ticks run, and only looked at the
 * block's kind at the end, which a stem dug and planted again also passes; these hold random ticks at 0 (no stem
 * grows or turns to the grown stem by itself during the test) and watch the stem every tick.</li>
 * <li>B831 (Scaffolds.rewait): the build-delay rule lowered while a scaffold waits shortens that wait now, down to the
 * last tick: a wait with one tick left and the rule lowered to 0 is over at once.</li>
 * </ul>
 */
public class QaBuilderSurvivors1103cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = QaScanRebuild1102dGameTests.SRC;
	/** QaBuilders1102bGameTests' build origin and supply chest. */
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 5);
	private static final BlockPos SUPPLY = new BlockPos(2, 2, 4);

	// ------------------------------------------------------------------------------------------------ B865, pure logic

	/** The fresh stem a builder plants counts as the grown stem the drawing wants, melon and pumpkin; never across kinds. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFreshStemCountsAsTheGrownStemOfItsOwnKindOnly(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			BlockState melonGrown = Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, facing);
			BlockState pumpkinGrown = Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, facing);
			for (int age = 0; age <= 7; age++) {
				BlockState melon = Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, age);
				BlockState pumpkin = Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE, age);
				if (!MaterialRules.matches(melon, melonGrown)) {
					wrong.add("melon stem age " + age + " vs attached melon stem " + facing + ": expected built, seen not built");
				}
				if (!MaterialRules.matches(pumpkin, pumpkinGrown)) {
					wrong.add("pumpkin stem age " + age + " vs attached pumpkin stem " + facing + ": expected built, seen not built");
				}
				if (MaterialRules.matches(melon, pumpkinGrown)) {
					wrong.add("melon stem age " + age + " vs attached pumpkin stem " + facing + ": expected not built, seen built");
				}
				if (MaterialRules.matches(pumpkin, melonGrown)) {
					wrong.add("pumpkin stem age " + age + " vs attached melon stem " + facing + ": expected not built, seen built");
				}
			}
			// what grownAs plants for it is that fresh stem, and it counts as built
			BlockState planted = MaterialRules.grownAs(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), melonGrown);
			if (!planted.is(Blocks.MELON_STEM) || !MaterialRules.matches(planted, melonGrown)) {
				wrong.add("attached melon stem " + facing + " planted as " + planted + ", expected a melon stem that counts as built");
			}
			if (!MaterialRules.matches(melonGrown, melonGrown)) {
				wrong.add("attached melon stem " + facing + " vs itself: expected built, seen not built");
			}
			if (MaterialRules.matches(Blocks.AIR.defaultBlockState(), melonGrown) || MaterialRules.matches(Blocks.WHEAT.defaultBlockState(), melonGrown)) {
				wrong.add("air or wheat counted as an attached melon stem " + facing);
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong, first: " + String.join("; ", wrong.subList(0, Math.min(3, wrong.size()))));
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------ B865, real builds

	/** Holds random ticks at 0 for the test (no stem grows, no farmland dries), the old value put back afterwards. */
	private static void noRandomTicks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int was = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(was, level.getServer()));
		level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
	}

	/** Stone floor, a pool at z1, a melon on its grown stem at z0 and a pumpkin on its grown stem at z2 (fruit east). */
	private static QaScanRebuild1102dGameTests.Scene field() {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
		}
		s.put(0, 0, 1, Blocks.WATER);
		row(s, 0, Blocks.ATTACHED_MELON_STEM, Blocks.MELON);
		row(s, 2, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.PUMPKIN);
		return s;
	}

	private static void row(QaScanRebuild1102dGameTests.Scene s, int z, Block attached, Block fruit) {
		s.put(0, 0, z, Blocks.FARMLAND);
		s.put(1, 0, z, Blocks.DIRT);
		s.put(0, 1, z, attached.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST));
		s.put(1, 1, z, fruit);
	}

	/** Sets the scene at SRC and scans it with real clicks. */
	private static ResourceLocation scan(GameTestHelper helper, QaScanRebuild1102dGameTests.Scene scene) {
		for (Map.Entry<BlockPos, BlockState> e : scene.blocks.entrySet()) {
			helper.setBlock(SRC.offset(e.getKey()), e.getValue());
		}
		return QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(scene.max()));
	}

	/** Fills the chest at SUPPLY with {@code items} (stacks split as a player would). */
	private static Container stock(GameTestHelper helper, Map<Item, Integer> items) {
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : items.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, new ItemStack(e.getKey()).getMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		return chest;
	}

	/** What the chest and the builder's bag hold of {@code item}. */
	private static int held(GameTestHelper helper, Container chest, Item item) {
		int n = chest.countItem(item);
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds())) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		return n;
	}

	/**
	 * Watches the stem at {@code pos} each tick: once it stands as {@code fresh}, it must stay that block (never dug up,
	 * never planted again: its age is kept too, random ticks being off). Returns what it saw go wrong, empty if nothing.
	 */
	private static List<String> watch(GameTestHelper helper, BlockPos pos, Block fresh, String name) {
		List<String> seen = new ArrayList<>();
		BlockState[] first = {null};
		helper.onEachTick(() -> {
			BlockState now = helper.getLevel().getBlockState(pos);
			if (first[0] == null) {
				if (now.is(fresh)) {
					first[0] = now;
				}
			} else if (now != first[0] && seen.size() < 3) {
				seen.add(name + " stem planted as " + first[0] + " was " + now + " at tick " + helper.getTick());
				first[0] = now.is(fresh) ? now : first[0];
			}
		});
		return seen;
	}

	/**
	 * A melon and a pumpkin on their grown stems, scanned and built back from a chest holding the list and one spare of
	 * each seeds: the build finishes with nothing skipped, each stem is planted once as a fresh stem of its kind and
	 * stays (never dug up or planted again), exactly one seeds is paid per stem (the spare is left), and once built
	 * the drawing counts every block as standing.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1103cSurvivorStems"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1103cSurvivorStems")
	public void scannedGrownStemsArePlantedOnceAndPaidOneSeedsEach(GameTestHelper helper) {
		Leftovers.clear(helper);
		noRandomTicks(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = scan(helper, field());
		helper.setBlock(SUPPLY, Blocks.CHEST);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		BuildPlan plan = b.site().plan(level);
		Map<Item, Integer> asked = plan.materials();
		helper.assertTrue(asked.getOrDefault(Items.MELON_SEEDS, 0) == 1 && asked.getOrDefault(Items.PUMPKIN_SEEDS, 0) == 1,
			"expected the list to ask 1 melon seeds and 1 pumpkin seeds, seen " + asked);
		Map<Item, Integer> stocked = new java.util.LinkedHashMap<>(asked);
		stocked.merge(Items.MELON_SEEDS, 1, Integer::sum);
		stocked.merge(Items.PUMPKIN_SEEDS, 1, Integer::sum);
		Container chest = stock(helper, stocked);
		BlockPos melonStem = b.origin().offset(0, 1, 0);
		BlockPos pumpkinStem = b.origin().offset(0, 1, 2);
		List<String> melonSeen = watch(helper, melonStem, Blocks.MELON_STEM, "melon");
		List<String> pumpkinSeen = watch(helper, pumpkinStem, Blocks.PUMPKIN_STEM, "pumpkin");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "expected the build done, seen still building: "
				+ b.site().stage() + " " + b.site().status() + " missing " + b.site().missing());
			helper.assertTrue(b.site().skipped() == 0, "expected 0 steps skipped, seen " + b.site().skipped());
			helper.assertTrue(melonSeen.isEmpty() && pumpkinSeen.isEmpty(), "expected each fresh stem left as planted, seen " + melonSeen + pumpkinSeen);
			helper.assertTrue(level.getBlockState(melonStem).is(Blocks.MELON_STEM) && level.getBlockState(pumpkinStem).is(Blocks.PUMPKIN_STEM),
				"expected a fresh melon stem and a fresh pumpkin stem (random ticks off), seen " + level.getBlockState(melonStem) + " and "
					+ level.getBlockState(pumpkinStem));
			int melonLeft = held(helper, chest, Items.MELON_SEEDS);
			int pumpkinLeft = held(helper, chest, Items.PUMPKIN_SEEDS);
			helper.assertTrue(melonLeft == 1 && pumpkinLeft == 1, "expected exactly 1 seeds paid per stem (the spare of each left), seen "
				+ melonLeft + " melon seeds and " + pumpkinLeft + " pumpkin seeds left");
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "expected the finished build to count every block as standing, seen not standing: " + unfinished);
		});
	}

	/**
	 * A melon stem already growing (age 5) where the drawing has the melon's grown stem, on the field's own farmland
	 * and pool: it is the stem the build wants, so the builder leaves it (it isn't cleared, dug up or planted again, so
	 * it keeps its age) and pays no seeds for it; the pumpkin's stem beside it, missing, is planted from one seeds.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1103cSurvivorGrowing"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1103cSurvivorGrowing")
	public void aStemAlreadyGrowingWhereTheGrownStemIsDrawnIsLeftStanding(GameTestHelper helper) {
		Leftovers.clear(helper);
		noRandomTicks(helper);
		ServerLevel level = helper.getLevel();
		QaScanRebuild1102dGameTests.Scene scene = field();
		ResourceLocation id = scan(helper, scene);
		// the field's ground at the build's origin, and a melon stem of age 5 on its farmland
		for (Map.Entry<BlockPos, BlockState> e : scene.blocks.entrySet()) {
			if (e.getKey().getY() == 0) {
				helper.setBlock(ORIGIN.offset(e.getKey()), e.getValue());
			}
		}
		BlockState growing = Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, 5);
		helper.setBlock(ORIGIN.above(), growing);
		helper.assertTrue(helper.getBlockState(ORIGIN.above()) == growing, "setup: expected a melon stem of age 5, seen " + helper.getBlockState(ORIGIN.above()));
		helper.setBlock(SUPPLY, Blocks.CHEST);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		helper.assertTrue(b.origin().equals(helper.absolutePos(ORIGIN)), "setup: expected the build at " + helper.absolutePos(ORIGIN) + ", seen " + b.origin());
		Map<Item, Integer> stocked = new java.util.LinkedHashMap<>(b.site().plan(level).materials());
		stocked.put(Items.MELON_SEEDS, 1); // a spare: the standing stem needs none
		stocked.put(Items.PUMPKIN_SEEDS, 1);
		Container chest = stock(helper, stocked);
		BlockPos melonStem = b.origin().above();
		List<String> changed = new ArrayList<>();
		helper.onEachTick(() -> {
			BlockState now = level.getBlockState(melonStem);
			if (now != growing && changed.size() < 3) {
				changed.add(now + " at tick " + helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "expected the build done, seen still building: "
				+ b.site().stage() + " " + b.site().status() + " missing " + b.site().missing());
			helper.assertTrue(b.site().skipped() == 0, "expected 0 steps skipped, seen " + b.site().skipped());
			helper.assertTrue(changed.isEmpty(), "expected the growing melon stem (age 5) left standing all along, seen it become " + changed);
			int melonLeft = held(helper, chest, Items.MELON_SEEDS);
			int pumpkinLeft = held(helper, chest, Items.PUMPKIN_SEEDS);
			helper.assertTrue(melonLeft == 1, "expected no melon seeds paid for the standing stem (1 left), seen " + melonLeft + " left");
			helper.assertTrue(pumpkinLeft == 0 && level.getBlockState(b.origin().offset(0, 1, 2)).is(Blocks.PUMPKIN_STEM),
				"expected the pumpkin's stem planted from its 1 seeds, seen " + pumpkinLeft + " pumpkin seeds left and "
					+ level.getBlockState(b.origin().offset(0, 1, 2)));
		});
	}

	// ------------------------------------------------------------------------------------------------ B831, scaffolds

	private static final java.lang.reflect.Field TIMER;
	private static final java.lang.reflect.Field TIMER_RULE;
	private static final java.lang.reflect.Method REWAIT;

	static {
		try {
			TIMER = Scaffolds.class.getDeclaredField("timer");
			TIMER_RULE = Scaffolds.class.getDeclaredField("timerRule");
			REWAIT = Scaffolds.class.getDeclaredMethod("rewait", ServerLevel.class, Villager.class);
			TIMER.setAccessible(true);
			TIMER_RULE.setAccessible(true);
			REWAIT.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Scaffolds' wait (timer, timerRule, rewait) has changed shape: update this test", e);
		}
	}

	/** A scaffold wait of {@code remaining} ticks begun at rule {@code then}, looked at again with the rule at {@code now}. */
	private static int rewaited(GameTestHelper helper, Villager builder, int remaining, int then, int now) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(now, level.getServer());
		try {
			Scaffolds scaffolds = new Scaffolds();
			TIMER.setInt(scaffolds, remaining);
			TIMER_RULE.setInt(scaffolds, then);
			REWAIT.invoke(scaffolds, level, builder);
			return TIMER.getInt(scaffolds);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * B831 at its last tick: a scaffold wait with one tick left, the delay rule lowered to 0, is over now (no tick more),
	 * as a longer wait lowered to 0 is; the rule lowered to a delay still longer than what's left, or raised, leaves it.
	 * There's no deterministic way to hold the builder's own wait at exactly one tick (after each scaffold he climbs for
	 * a number of ticks the pathing decides, with the timer paused), so the wait is driven directly.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aScaffoldWaitWithOneTickLeftEndsAtOnceWhenTheDelayIsLoweredToZero(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int was = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(was, level.getServer()));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 2, 0));
		List<String> wrong = new ArrayList<>();
		int[][] cases = { // remaining, rule then, rule now, expected remaining
			{1, 100, 0, 0}, {2, 100, 0, 0}, {40, 100, 0, 0}, {1, 1, 0, 0}, {1, 100, 1000, 1}, {1, 0, 0, 1}, {0, 100, 0, 0}};
		for (int[] c : cases) {
			int seen = rewaited(helper, builder, c[0], c[1], c[2]);
			if (seen != c[3]) {
				wrong.add(c[0] + " tick(s) left, rule " + c[1] + " -> " + c[2] + ": expected " + c[3] + " left, seen " + seen);
			}
		}
		// lowered to a rule whose delay is still above what's left: the wait stays at its one tick
		int lowered = 50;
		int full = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(lowered, builder);
		if (full >= 1) {
			int seen = rewaited(helper, builder, 1, 100, lowered);
			if (seen != 1) {
				wrong.add("1 tick left, rule 100 -> " + lowered + " (full delay " + full + "): expected 1 left, seen " + seen);
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong, first: " + String.join("; ", wrong.subList(0, Math.min(3, wrong.size()))));
		helper.succeed();
	}
}
