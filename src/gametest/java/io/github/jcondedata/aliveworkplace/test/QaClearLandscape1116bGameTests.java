package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1116-2138), the Builder's CLEAR and LANDSCAPE stages from their contract (CLAUDE.md, "How the builder
 * works"; docs/wiki/builder.md, "Stages, in order"): "CLEAR (top-down)"; "Builders never break containers or the bench
 * while clearing, and never copy container contents"; "LANDSCAPE never waits for materials"; "FOUNDATION and LANDSCAPE
 * ... a site loaded mid-stage restarts that list; steps already done are skipped"; "All progress lives in BuildSite
 * (saved). BuilderWork must stay restartable at any tick"; "Steps that can't be done yet are deferred once, then
 * skipped (counted in skipped)". What is new here, beside the existing container tests (QaBuilderInvariants1031,
 * QaBuilderKeepsContainers1028): the order CLEAR takes blocks in, watched block by block, also across a restart; a
 * chest and barrel in the room whose things are counted every tick (never copied into the bag or the supply chest); the
 * Builder's own bench and a neighbor's bench standing on the ground LANDSCAPE levels; and LANDSCAPE restarted again and
 * again with no dirt, its skipped count and status line checked.
 */
public class QaClearLandscape1116bGameTests {
	private static final Logger LOGGER = LoggerFactory.getLogger("QaClearLandscape1116b");
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** The hut stands at x 6-10, y 2-5, z 6-10. */
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);
	/** Andesite columns through the hut's box: two in the room, two through the side walls. */
	private static final int[][] COLUMNS = {{8, 8}, {7, 9}, {6, 8}, {10, 9}};

	private record Run(ServerLevel level, Villager[] builder, UUID site, BuildPlan plan) {
		Villager villager() {
			return builder[0];
		}

		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
		}
	}

	// ------------------------------------------------------------------------------------------------- helpers

	private static ServerLevel rules(GameTestHelper helper, boolean freeMaterials) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(freeMaterials, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		return level;
	}

	/** A Builder at his bench at {@code bench}, the supply chest at CHEST, starting the test hut. */
	private static Run start(GameTestHelper helper, BlockPos bench) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, TEST_HUT, placement);
		helper.assertTrue(site != null, "setup: the test hut wasn't started");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no test hut blueprint");
		return new Run(level, new Villager[]{villager}, site.id(), plan);
	}

	/** Fills the supply chest with exactly the hut's material list (no dirt in it). */
	private static void fillExactly(GameTestHelper helper, Map<Item, Integer> needs) {
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : needs.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
	}

	private static String rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	private static String where(GameTestHelper helper, Run r) {
		BuildSite site = r.live();
		return site == null ? "(no site)" : site.stage() + " " + site.status() + " cursor " + site.cursor() + " skipped " + site.skipped()
			+ " missing " + site.missing() + "; builder at " + rel(helper, r.villager().blockPosition());
	}

	/** A server restart: every build site and the Builder saved and loaded again (the Builder as a fresh entity). */
	private static void restart(GameTestHelper helper, Run r) {
		ServerLevel level = r.level();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		Villager old = r.builder()[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		r.builder()[0] = (Villager) loaded;
	}

	/** Every item of {@code item} on the test's ground (dropped stacks). */
	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static int inBag(Run r, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(r.villager()).count(item);
	}

	private static int inSupply(GameTestHelper helper, Item item) {
		return helper.getBlockEntity(CHEST) instanceof Container c ? c.countItem(item) : 0;
	}

	/** Andesite in every y 2-5 of the COLUMNS (relative positions), set before the plan is made. */
	private static List<BlockPos> andesiteColumns(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (int[] c : COLUMNS) {
			for (int y = 2; y <= 5; y++) {
				BlockPos p = new BlockPos(c[0], y, c[1]);
				helper.setBlock(p, Blocks.ANDESITE);
				out.add(p);
			}
		}
		return out;
	}

	/** Of {@code placed}, those the hut's CLEAR list takes away. */
	private static List<BlockPos> inClearList(GameTestHelper helper, Run r, List<BlockPos> placed) {
		List<BlockPos> out = new ArrayList<>();
		for (BuildPlan.Step st : r.plan().steps(BuildPlan.Stage.CLEAR)) {
			for (BlockPos p : placed) {
				if (helper.absolutePos(p).equals(st.pos())) {
					out.add(p);
				}
			}
		}
		return out;
	}

	/**
	 * Watches each andesite block every tick: the tick and stage it was taken away in, and whether one taken away ever
	 * comes back as andesite.
	 */
	private static void watchTaken(GameTestHelper helper, Run r, List<BlockPos> blocks, Map<BlockPos, Long> takenAt, Map<BlockPos, BuildPlan.Stage> takenIn,
		List<String> back) {
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			for (BlockPos p : blocks) {
				boolean andesite = helper.getBlockState(p).is(Blocks.ANDESITE);
				if (!andesite && !takenAt.containsKey(p)) {
					takenAt.put(p, helper.getTick());
					takenIn.put(p, s == null ? null : s.stage());
				} else if (andesite && takenAt.containsKey(p) && back.size() < 5) {
					back.add(p.toShortString() + " (taken at tick " + takenAt.get(p) + ") is andesite again at tick " + helper.getTick() + " in " + where(helper, r));
				}
			}
		});
	}

	/** Notes, with where each item is, every time there are more andesite items about than andesite blocks taken. */
	private static void trackAndesite(GameTestHelper helper, Run r, List<BlockPos> placed, int[] most, List<String> extra) {
		int taken = (int) placed.stream().filter(p -> !helper.getBlockState(p).is(Blocks.ANDESITE)).count();
		int supply = inSupply(helper, Items.ANDESITE);
		int bag = inBag(r, Items.ANDESITE);
		int ground = onGround(helper, Items.ANDESITE);
		int hand = r.villager().getMainHandItem().is(Items.ANDESITE) ? r.villager().getMainHandItem().getCount() : 0;
		int items = supply + bag + ground + hand;
		if (items - taken > most[0]) {
			most[0] = items - taken;
			if (extra.size() < 4) {
				extra.add("tick " + helper.getTick() + ": " + taken + " taken, " + supply + " in the chest, " + bag + " in the bag, " + ground + " on the ground, "
					+ hand + " in hand, in " + where(helper, r));
			}
		}
	}

	/**
	 * Every pair of andesite blocks taken in CLEAR, the higher one taken no later than the lower one (top-down); a lower
	 * one taken first is listed, with its column's blocks left floating over it.
	 */
	private static List<String> outOfOrder(GameTestHelper helper, List<BlockPos> cleared, Map<BlockPos, Long> takenAt) {
		List<String> wrong = new ArrayList<>();
		for (BlockPos low : cleared) {
			for (BlockPos high : cleared) {
				if (high.getY() > low.getY() && takenAt.get(high) > takenAt.get(low) && wrong.size() < 6) {
					boolean sameColumn = high.getX() == low.getX() && high.getZ() == low.getZ();
					wrong.add(low.toShortString() + " taken at tick " + takenAt.get(low) + " before " + high.toShortString() + " at tick " + takenAt.get(high)
						+ (sameColumn ? " (left floating over the gap)" : ""));
				}
			}
		}
		return wrong;
	}

	// ------------------------------------------------------------------------------------------------- CLEAR

	/**
	 * Four andesite columns, floor to roof, stand in the hut's box (two in the room, two through the side walls). CLEAR
	 * goes top-down: of the andesite it takes away, no block is taken before one higher up (so a column is never left
	 * floating over a gap), none of it comes back, the room's columns are all gone, and the hut finishes as drawn.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaClearLand1116bTopDown"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaClearLand1116bTopDown")
	public void clearTakesTheColumnsInTheBoxFromTheTopDown(GameTestHelper helper) {
		rules(helper, true);
		List<BlockPos> placed = andesiteColumns(helper);
		Run r = start(helper, BENCH);
		List<BlockPos> cleared = inClearList(helper, r, placed);
		helper.assertTrue(cleared.stream().mapToInt(BlockPos::getY).distinct().count() >= 2,
			"setup: the CLEAR list takes andesite on fewer than two layers: " + cleared);
		Map<BlockPos, Long> takenAt = new HashMap<>();
		Map<BlockPos, BuildPlan.Stage> takenIn = new HashMap<>();
		List<String> back = new ArrayList<>();
		watchTaken(helper, r, placed, takenAt, takenIn, back);
		int[] mostAndesite = {0};
		List<String> extra = new ArrayList<>();
		helper.onEachTick(() -> trackAndesite(helper, r, placed, mostAndesite, extra));
		helper.succeedWhen(() -> {
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(back.isEmpty(), "andesite CLEAR took away came back: " + back);
			helper.assertTrue(mostAndesite[0] <= 0, "andesite was made: at one point " + mostAndesite[0] + " more andesite items than blocks taken: " + extra);
			List<String> notTaken = cleared.stream().filter(p -> !takenAt.containsKey(p)).map(BlockPos::toShortString).toList();
			helper.assertTrue(notTaken.isEmpty(), "CLEAR's andesite still stands: " + notTaken);
			List<String> lateClear = cleared.stream().filter(p -> takenIn.get(p) != BuildPlan.Stage.CLEAR)
				.map(p -> p.toShortString() + " in " + takenIn.get(p)).toList();
			helper.assertTrue(lateClear.isEmpty(), "andesite on CLEAR's list taken away outside CLEAR: " + lateClear);
			List<String> wrong = outOfOrder(helper, cleared, takenAt);
			helper.assertTrue(wrong.isEmpty(), "CLEAR didn't go top-down: " + wrong);
			for (BlockPos p : placed) {
				helper.assertFalse(helper.getBlockState(p).is(Blocks.ANDESITE), "andesite left in the finished hut at " + p.toShortString());
			}
			List<String> unfinished = r.plan().unfinished(r.level()).stream().map(p -> rel(helper, p)).toList();
			helper.assertTrue(unfinished.isEmpty(), "the hut isn't as drawn: " + unfinished);
			LOGGER.info("qa clear/land 1116b top-down: {} andesite blocks on CLEAR's list, taken at ticks {}", cleared.size(),
				cleared.stream().map(p -> p.getY() + "@" + takenAt.get(p)).toList());
		});
	}

	/**
	 * The same columns, the server restarted once CLEAR has taken some andesite and not all. The restart keeps the site
	 * (its id), its stage, its cursor and its skipped count; CLEAR carries on top-down from where it was, nothing it took
	 * comes back, no andesite is made (all of it is in the chest, the bag or on the ground, never more than was taken),
	 * and the hut finishes as drawn.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaClearLand1116bClearRestart"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaClearLand1116bClearRestart")
	public void aRestartMidClearKeepsTheSiteAndTheOrderAndMakesNothing(GameTestHelper helper) {
		rules(helper, true);
		List<BlockPos> placed = andesiteColumns(helper);
		Run r = start(helper, BENCH);
		List<BlockPos> cleared = inClearList(helper, r, placed);
		helper.assertTrue(cleared.size() >= 4, "setup: the CLEAR list takes only " + cleared.size() + " andesite blocks");
		Map<BlockPos, Long> takenAt = new HashMap<>();
		Map<BlockPos, BuildPlan.Stage> takenIn = new HashMap<>();
		List<String> back = new ArrayList<>();
		watchTaken(helper, r, placed, takenAt, takenIn, back);
		String[] restarted = {null};
		int[] mostAndesite = {0};
		List<String> extra = new ArrayList<>();
		helper.onEachTick(() -> {
			trackAndesite(helper, r, placed, mostAndesite, extra);
			BuildSite s = r.live();
			long clearedSoFar = cleared.stream().filter(takenAt::containsKey).count();
			if (restarted[0] == null && s != null && s.stage() == BuildPlan.Stage.CLEAR && clearedSoFar >= 2 && clearedSoFar <= cleared.size() - 2) {
				int cursor = s.cursor();
				int skipped = s.skipped();
				restart(helper, r);
				BuildSite loaded = r.live();
				helper.assertTrue(loaded != null, "after the restart there is no site with the id " + r.site());
				helper.assertTrue(loaded.stage() == BuildPlan.Stage.CLEAR, "the restart moved the site from CLEAR to " + loaded.stage());
				helper.assertTrue(loaded.cursor() == cursor, "the restart moved CLEAR's cursor from " + cursor + " to " + loaded.cursor());
				helper.assertTrue(loaded.skipped() == skipped, "the restart changed skipped from " + skipped + " to " + loaded.skipped());
				restarted[0] = "cursor " + cursor + ", " + clearedSoFar + " of " + cleared.size() + " andesite blocks taken";
				r.level().getServer().sendSystemMessage(Component.literal("[qa-clear-land-1116b] restarted mid-CLEAR at " + restarted[0]));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "CLEAR never stood between two andesite blocks for a restart: " + where(helper, r));
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(back.isEmpty(), "andesite CLEAR took away came back after the restart: " + back);
			List<String> notTaken = cleared.stream().filter(p -> !takenAt.containsKey(p)).map(BlockPos::toShortString).toList();
			helper.assertTrue(notTaken.isEmpty(), "CLEAR's andesite still stands after the restart: " + notTaken);
			List<String> wrong = outOfOrder(helper, cleared, takenAt);
			helper.assertTrue(wrong.isEmpty(), "CLEAR didn't go top-down across the restart (" + restarted[0] + "): " + wrong);
			helper.assertTrue(mostAndesite[0] <= 0, "andesite was made: at one point " + mostAndesite[0] + " more andesite items than blocks taken: " + extra);
			List<String> unfinished = r.plan().unfinished(r.level()).stream().map(p -> rel(helper, p)).toList();
			helper.assertTrue(unfinished.isEmpty(), "the hut isn't as drawn after the restart: " + unfinished);
		});
	}

	/**
	 * A chest (13 emeralds in slot 0, a named diamond sword in slot 7) and a barrel (5 name tags in slot 3, 2 golden apples
	 * in its last slot) stand in the hut's room where the drawing has air, with another Builder's Bench (nobody's) on the
	 * chest, free materials off. CLEAR breaks none of the three. Counted every tick: each
	 * of their things exists exactly as often as it was put in (never copied into the Builder's bag or supply chest, never
	 * dropped); at the end all three stand, every slot exactly as set, and the hut is finished round them.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaClearLand1116bNoCopy"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaClearLand1116bNoCopy")
	public void containersAndABenchInTheRoomStandAndNothingIsCopied(GameTestHelper helper) {
		rules(helper, false);
		BlockPos chestAt = new BlockPos(8, 3, 8);
		BlockPos barrelAt = new BlockPos(7, 3, 9);
		BlockPos benchAt = new BlockPos(8, 4, 8);
		helper.setBlock(chestAt, Blocks.CHEST);
		helper.setBlock(benchAt, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(barrelAt, Blocks.BARREL);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Grandpa's Sword"));
		Container chest = helper.getBlockEntity(chestAt);
		Container barrel = helper.getBlockEntity(barrelAt);
		Map<Integer, ItemStack> chestSlots = Map.of(0, new ItemStack(Items.EMERALD, 13), 7, sword);
		Map<Integer, ItemStack> barrelSlots = Map.of(3, new ItemStack(Items.NAME_TAG, 5), barrel.getContainerSize() - 1, new ItemStack(Items.GOLDEN_APPLE, 2));
		chestSlots.forEach((slot, stack) -> chest.setItem(slot, stack.copy()));
		barrelSlots.forEach((slot, stack) -> barrel.setItem(slot, stack.copy()));
		Run r = start(helper, BENCH);
		for (BlockPos p : List.of(chestAt, barrelAt, benchAt)) {
			BlockState drawn = r.plan().targets().get(helper.absolutePos(p));
			helper.assertTrue(drawn == null || drawn.isAir(), "setup: the drawing has " + drawn + " at " + p.toShortString() + ", not air");
		}
		Map<Item, Integer> needs = r.plan().materials();
		for (Item thing : List.of(Items.EMERALD, Items.DIAMOND_SWORD, Items.NAME_TAG, Items.GOLDEN_APPLE)) {
			helper.assertFalse(needs.containsKey(thing), "setup: the hut's list asks for " + thing);
		}
		fillExactly(helper, needs);
		Map<Item, Integer> expected = new LinkedHashMap<>();
		expected.put(Items.EMERALD, 13);
		expected.put(Items.DIAMOND_SWORD, 1);
		expected.put(Items.NAME_TAG, 5);
		expected.put(Items.GOLDEN_APPLE, 2);
		Map<BlockPos, Block> watched = Map.of(chestAt, Blocks.CHEST, barrelAt, Blocks.BARREL, benchAt, ModBlocks.BUILDERS_BENCH);
		List<String> wrong = new ArrayList<>();
		boolean[] cleared = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (s != null && s.stage() == BuildPlan.Stage.CLEAR) {
				cleared[0] = true;
			}
			watched.forEach((pos, block) -> {
				if (!helper.getBlockState(pos).is(block) && wrong.size() < 6) {
					wrong.add(pos.toShortString() + " " + block + " became " + helper.getBlockState(pos) + " at tick " + helper.getTick() + " in " + where(helper, r));
				}
			});
			expected.forEach((item, n) -> {
				int inChest = helper.getBlockEntity(chestAt) instanceof Container c ? c.countItem(item) : 0;
				int inBarrel = helper.getBlockEntity(barrelAt) instanceof Container c ? c.countItem(item) : 0;
				int bag = inBag(r, item);
				int supply = inSupply(helper, item);
				int ground = onGround(helper, item);
				if ((inChest + inBarrel + bag + supply + ground != n || bag + supply + ground != 0) && wrong.size() < 6) {
					wrong.add(item + " at tick " + helper.getTick() + " in " + where(helper, r) + ": " + inChest + " in the chest, " + inBarrel + " in the barrel, "
						+ bag + " in the bag, " + supply + " in the supply chest, " + ground + " on the ground (put in: " + n + ")");
				}
			});
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(wrong.isEmpty(), "a container in the room or its things: " + wrong);
			helper.assertTrue(cleared[0], "the build never showed CLEAR");
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			Container c = helper.getBlockEntity(chestAt);
			Container b = helper.getBlockEntity(barrelAt);
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack want = chestSlots.getOrDefault(i, ItemStack.EMPTY);
				helper.assertTrue(ItemStack.matches(c.getItem(i), want), "the chest's slot " + i + " holds " + c.getItem(i) + " " + c.getItem(i).getComponents()
					+ ", not " + want);
			}
			for (int i = 0; i < b.getContainerSize(); i++) {
				ItemStack want = barrelSlots.getOrDefault(i, ItemStack.EMPTY);
				helper.assertTrue(ItemStack.matches(b.getItem(i), want), "the barrel's slot " + i + " holds " + b.getItem(i) + ", not " + want);
			}
			// the containers' own spots are the only ones left not as drawn (the drawing has air there)
			List<String> unfinished = r.plan().unfinished(r.level()).stream().filter(p -> !p.equals(helper.absolutePos(chestAt)) && !p.equals(helper.absolutePos(barrelAt))
				&& !p.equals(helper.absolutePos(benchAt)))
				.map(p -> rel(helper, p)).toList();
			helper.assertTrue(unfinished.isEmpty(), "the hut isn't finished round the containers: " + unfinished);
		});
	}

	// ------------------------------------------------------------------------------------------------- LANDSCAPE

	/**
	 * The Builder's own bench stands on the ground of the leveled ring (beside the hut's side wall) and another Builder's
	 * Bench (nobody's job site) on a dirt mound in the ring; two other mounds stand in the ring too, and once the plan is
	 * made a player swaps one planned mound for a third Builder's Bench (so a landscaping step meets it). LANDSCAPE levels
	 * the other mound but breaks no bench, the Builder keeps his bench as his job site, and the build is done.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaClearLand1116bBenchRing"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaClearLand1116bBenchRing")
	public void landscapingLeavesTheBuildersBenchAndANeighborsBenchOnTheRing(GameTestHelper helper) {
		rules(helper, true);
		BlockPos ownBench = new BlockPos(11, 2, 9);
		BlockPos neighborMound = new BlockPos(5, 2, 9);
		BlockPos neighborBench = neighborMound.above();
		List<BlockPos> mounds = List.of(new BlockPos(11, 2, 7), new BlockPos(8, 2, 11));
		for (BlockPos p : mounds) {
			helper.setBlock(p, Blocks.DIRT);
		}
		helper.setBlock(neighborMound, Blocks.DIRT);
		helper.setBlock(neighborBench, ModBlocks.BUILDERS_BENCH);
		Run r = start(helper, ownBench);
		List<BuildPlan.Step> landscape = r.plan().steps(BuildPlan.Stage.LANDSCAPE);
		long plannedMounds = landscape.stream().filter(s -> mounds.stream().anyMatch(m -> helper.absolutePos(m).equals(s.pos()))).count();
		helper.assertTrue(plannedMounds >= 1, "setup: none of the mounds is in the LANDSCAPE list: "
			+ landscape.stream().map(s -> rel(helper, s.pos()) + "=" + s.state()).limit(12).toList());
		LOGGER.info("qa clear/land 1116b bench ring: LANDSCAPE list at the benches: {}", landscape.stream()
			.filter(s -> s.pos().equals(helper.absolutePos(ownBench)) || s.pos().equals(helper.absolutePos(neighborBench))
				|| s.pos().equals(helper.absolutePos(neighborMound)))
			.map(s -> rel(helper, s.pos()) + "=" + s.state()).toList());
		// a player swaps a planned mound for a third Builder's Bench (nobody's) after the plan was made: a step meets it
		BlockPos swapped = mounds.stream().filter(m -> landscape.stream().anyMatch(s -> s.pos().equals(helper.absolutePos(m)))).findFirst().orElseThrow();
		helper.setBlock(swapped, ModBlocks.BUILDERS_BENCH);
		List<BlockPos> benches = List.of(ownBench, neighborBench, swapped);
		List<String> broken = new ArrayList<>();
		boolean[] landscaped = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (s != null && s.stage() == BuildPlan.Stage.LANDSCAPE) {
				landscaped[0] = true;
			}
			for (BlockPos p : benches) {
				if (!helper.getBlockState(p).is(ModBlocks.BUILDERS_BENCH) && broken.size() < 4) {
					broken.add(p.toShortString() + " became " + helper.getBlockState(p) + " at tick " + helper.getTick() + " in " + where(helper, r));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "a Builder's Bench on the leveled ring was broken: " + broken);
			helper.assertTrue(landscaped[0], "the build never went through LANDSCAPE");
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			for (BlockPos m : mounds) {
				if (m.equals(swapped)) {
					continue;
				}
				boolean planned = landscape.stream().anyMatch(s -> s.pos().equals(helper.absolutePos(m)));
				helper.assertTrue(!planned || helper.getBlockState(m).isAir(), "the mound at " + m.toShortString() + " wasn't leveled: " + helper.getBlockState(m));
			}
			var site = r.villager().getBrain().getMemory(MemoryModuleType.JOB_SITE);
			helper.assertTrue(site.isPresent() && site.get().pos().equals(helper.absolutePos(ownBench)), "the Builder lost his bench on the ring as his job site: "
				+ site);
		});
	}

	/**
	 * Holes in the ring's ground and two dirt mounds, the chest holding exactly the hut's list (no dirt), and the server
	 * restarted every 3 ticks of LANDSCAPE (up to 5 times; LANDSCAPE makes its list again from the ground each time).
	 * LANDSCAPE never waits: never WAITING_FOR_MATERIALS or short of dirt, its status line reads "Leveling the ground
	 * around it" and, once something is skipped, "N blocks skipped (unbuildable or unreachable)" with the site's count;
	 * the site keeps its id and is done within 1500 ticks of LANDSCAPE; no dirt is made; and each step of the ring is
	 * counted in skipped once (no more, no less), however often it was restarted.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaClearLand1116bLandRestarts"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaClearLand1116bLandRestarts")
	public void landscapeRestartedAgainAndAgainWithNoDirtNeverWaitsAndCountsEachStepOnce(GameTestHelper helper) {
		landscapeNoDirt(helper, 5, 3);
	}

	/**
	 * As above with no restart: the same ring, no dirt in the chest. Every planned step left undone at the end (a hole
	 * with no dirt to fill it) is counted in skipped, once.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaClearLand1116bLandNoRestart"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaClearLand1116bLandNoRestart")
	public void landscapeWithNoDirtCountsEachUndoneStepInSkippedOnce(GameTestHelper helper) {
		landscapeNoDirt(helper, 0, 3);
	}

	private static void landscapeNoDirt(GameTestHelper helper, int maxRestarts, int every) {
		rules(helper, false);
		List<BlockPos> holes = List.of(new BlockPos(5, 1, 8), new BlockPos(11, 1, 8), new BlockPos(8, 1, 11), new BlockPos(5, 1, 10));
		for (BlockPos p : holes) {
			helper.setBlock(p, Blocks.AIR);
		}
		List<BlockPos> mounds = List.of(new BlockPos(11, 2, 10), new BlockPos(9, 2, 11));
		for (BlockPos p : mounds) {
			helper.setBlock(p, Blocks.DIRT);
		}
		Run r = start(helper, BENCH);
		Map<Item, Integer> needs = r.plan().materials();
		helper.assertFalse(needs.containsKey(Items.DIRT), "setup: the hut's list asks for dirt: " + needs);
		fillExactly(helper, needs);
		List<BuildPlan.Step> landscape = r.plan().steps(BuildPlan.Stage.LANDSCAPE);
		long plannedHoles = landscape.stream().filter(s -> holes.stream().anyMatch(h -> helper.absolutePos(h).equals(s.pos()))).count();
		helper.assertTrue(plannedHoles >= 1, "setup: none of the holes is in the LANDSCAPE list");
		List<BlockPos> watchedDirt = new ArrayList<>(holes);
		watchedDirt.addAll(mounds);
		long[] landscapeAt = {-1};
		int[] skippedBefore = {-1};
		int[] restarts = {0};
		int[] mostDirt = {0};
		List<String> bad = new ArrayList<>();
		String[] status = {null};
		BuildSite[] lastSite = {null};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (s == null) {
				return;
			}
			lastSite[0] = s;
			int dirt = inSupply(helper, Items.DIRT) + inBag(r, Items.DIRT) + onGround(helper, Items.DIRT);
			for (BlockPos p : watchedDirt) {
				if (helper.getBlockState(p).is(Blocks.DIRT) || helper.getBlockState(p).is(Blocks.GRASS_BLOCK)) {
					dirt++;
				}
			}
			mostDirt[0] = Math.max(mostDirt[0], dirt);
			if (s.stage() != BuildPlan.Stage.LANDSCAPE) {
				return;
			}
			if (landscapeAt[0] < 0) {
				landscapeAt[0] = helper.getTick();
				skippedBefore[0] = s.skipped();
			}
			if ((s.status() == BuildSite.Status.WAITING_FOR_MATERIALS || s.missing().containsKey(Items.DIRT)) && bad.size() < 4) {
				bad.add("tick " + helper.getTick() + ": " + where(helper, r));
			}
			String line = Builders.statusText(r.level(), s, r.villager()).getString();
			status[0] = line;
			String skippedLine = s.skipped() == 1 ? "1 block skipped (unbuildable or unreachable)" : s.skipped() + " blocks skipped (unbuildable or unreachable)";
			if ((!line.contains("Leveling the ground around it") || line.contains("Needs:") || (s.skipped() > 0 && !line.contains(skippedLine))) && bad.size() < 4) {
				bad.add("status at tick " + helper.getTick() + " (skipped " + s.skipped() + "): " + line.replace('\n', '|'));
			}
			helper.assertTrue(helper.getTick() - landscapeAt[0] <= 1500, "still in LANDSCAPE " + (helper.getTick() - landscapeAt[0]) + " ticks after it began: "
				+ where(helper, r));
			if (restarts[0] < maxRestarts && (helper.getTick() - landscapeAt[0]) % every == every - 1) {
				int skipped = s.skipped();
				restart(helper, r);
				BuildSite loaded = r.live();
				helper.assertTrue(loaded != null && loaded.stage() == BuildPlan.Stage.LANDSCAPE, "after a restart mid-LANDSCAPE the site with id " + r.site() + " is "
					+ (loaded == null ? "gone" : "in " + loaded.stage()));
				helper.assertTrue(loaded.skipped() == skipped, "the restart changed skipped from " + skipped + " to " + loaded.skipped());
				restarts[0]++;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(landscapeAt[0] >= 0, "the build never showed LANDSCAPE");
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(restarts[0] >= Math.min(2, maxRestarts), "LANDSCAPE was restarted only " + restarts[0] + " time(s): too short to test");
			helper.assertTrue(bad.isEmpty(), "LANDSCAPE waited, asked for dirt or showed the wrong status: " + bad);
			helper.assertTrue(mostDirt[0] <= mounds.size(), "dirt was made: " + mostDirt[0] + " dirt in the chest, bag, ground, holes and mounds, from "
				+ mounds.size() + " mounds");
			List<String> unfinished = r.plan().unfinished(r.level()).stream().map(p -> rel(helper, p)).toList();
			helper.assertTrue(unfinished.isEmpty(), "the hut isn't finished: " + unfinished);
			// the ring's steps not done at the end (each may be counted in skipped once)
			List<String> notDone = new ArrayList<>();
			for (BuildPlan.Step st : landscape) {
				BlockState now = r.level().getBlockState(st.pos());
				boolean done = st.state().isAir() ? now.isAir() || now.canBeReplaced() : !now.isAir() && !now.canBeReplaced();
				if (!done) {
					notDone.add(rel(helper, st.pos()) + "=" + now.getBlock().getDescriptionId());
				}
			}
			int skippedNow = lastSite[0] == null ? -1 : lastSite[0].skipped();
			LOGGER.info("qa clear/land 1116b landscape restarts: {} restarts, LANDSCAPE list {} steps, not done {}, skipped before LANDSCAPE {}, status {}",
				restarts[0], landscape.size(), notDone, skippedBefore[0], status[0] == null ? null : status[0].replace('\n', '|'));
			int skippedInLandscape = Math.max(skippedNow, lastSkipped(status[0])) - skippedBefore[0];
			helper.assertTrue(skippedInLandscape >= notDone.size(), notDone.size() + " step(s) of the ring left undone " + notDone + " after " + restarts[0]
				+ " restart(s), but skipped went up by only " + skippedInLandscape + " in LANDSCAPE: a step that can't be done is counted in skipped");
			helper.assertTrue(skippedInLandscape <= notDone.size(), "skipped went up by " + skippedInLandscape + " in LANDSCAPE over " + restarts[0]
				+ " restarts, but only " + notDone.size() + " of its steps were left undone " + notDone + ": a step counted more than once (site now "
				+ skippedNow + ")");
		});
	}

	/** The skipped count the last LANDSCAPE status line showed (0 when it showed none). */
	private static int lastSkipped(String status) {
		if (status == null) {
			return 0;
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) blocks? skipped").matcher(status);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}
}
