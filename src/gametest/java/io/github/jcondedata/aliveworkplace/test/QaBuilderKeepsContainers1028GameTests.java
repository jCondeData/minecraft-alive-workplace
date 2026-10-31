package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-b-1028-0037), from the Builder's promises (CLAUDE.md, "How the builder works"; builder.md, "Stages, in
 * order"): "CLEAR takes away what is in the way, but never breaks containers, its own bench or any other Builder's
 * Bench"; "A step that cannot be done yet is put off once, then skipped (counted in skipped)"; "BuilderWork must stay
 * restartable at any tick". The existing tests put a chest and a barrel where the hut wants air
 * (QaStageThreeGameTests) and a neighbor's bench where it wants air (B132); these put containers of every vanilla
 * kind, a neighbor's bench and the builder's own Blueprint Table where the hut wants a WALL (so the drawn block can
 * never go in), restart the server between "put off once" and "skipped", and drop a full chest into an unbuilt wall
 * mid-STRUCTURE.
 */
public class QaBuilderKeepsContainers1028GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private record Run(ServerLevel level, Villager[] builder, UUID site, BuildPlan plan, BlueprintData.Placement placement) {
		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
		}
	}

	// ------------------------------------------------------------------------------------------------- helpers

	private static ServerLevel rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		return level;
	}

	/** A Builder at {@code jobSite} (a bench or Blueprint Table), the chest at {@code chestAt} holding the hut's materials. */
	private static Run start(GameTestHelper helper, Block jobBlock, BlockPos jobSite, BlockPos chestAt, BlockPos villagerAt) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(jobSite, jobBlock);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		chest.setItem(3, new ItemStack(Items.TORCH));
		Villager villager = helper.spawn(EntityType.VILLAGER, villagerAt);
		Builders.employ(level, villager, helper.absolutePos(jobSite));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, TEST_HUT, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no test hut blueprint");
		return new Run(level, new Villager[]{villager}, site.id(), plan, placement);
	}

	/**
	 * {@code n} spots (relative) where the hut draws a full wall block one layer above its floor, spread round the walls
	 * (no two side by side), so a block standing there is always in the way of the drawing.
	 */
	private static List<BlockPos> wallSpots(GameTestHelper helper, BuildPlan plan, int n) {
		int floorY = plan.bounds().minY();
		List<BlockPos> walls = new ArrayList<>();
		for (BuildPlan.Step step : plan.steps(BuildPlan.Stage.STRUCTURE)) {
			if (step.pos().getY() == floorY + 1 && step.state().isCollisionShapeFullBlock(helper.getLevel(), step.pos())) {
				walls.add(step.pos().immutable());
			}
		}
		walls.sort(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : walls) {
			if (out.stream().noneMatch(q -> q.distManhattan(p) <= 2)) {
				out.add(rel(helper, p));
			}
			if (out.size() == n) {
				break;
			}
		}
		helper.assertTrue(out.size() == n, "setup: only " + out.size() + " wall spots on the hut's second layer: " + walls.size() + " walls");
		return out;
	}

	/** A spot (relative) inside the hut, one layer above its floor, where the drawing has air, not beside a wall. */
	private static BlockPos insideSpot(GameTestHelper helper, BuildPlan plan) {
		int floorY = plan.bounds().minY();
		Map<BlockPos, net.minecraft.world.level.block.state.BlockState> targets = plan.targets();
		for (int x = plan.bounds().minX() + 1; x < plan.bounds().maxX(); x++) {
			for (int z = plan.bounds().minZ() + 1; z < plan.bounds().maxZ(); z++) {
				BlockPos p = new BlockPos(x, floorY + 1, z);
				var t = targets.get(p);
				if ((t == null || t.isAir()) && !plan.wantsBlockAt(p) && !plan.wantsBlockAt(p.above())) {
					return rel(helper, p);
				}
			}
		}
		helper.assertTrue(false, "setup: no air spot inside the hut, bounds " + plan.bounds());
		return null;
	}

	/**
	 * {@code abs} relative to the test's corner, the inverse of {@code helper.absolutePos} (unturned tests);
	 * {@code helper.relativePos} gave positions 12 blocks off here.
	 */
	private static BlockPos rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	/** A server restart: every build site and the Builder saved and loaded again (the Builder as a fresh entity). */
	private static void restart(GameTestHelper helper, ServerLevel level, Villager[] builder) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		Villager old = builder[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		builder[0] = (Villager) loaded;
	}

	private static int inBag(Villager villager, Item item) {
		int n = 0;
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		for (ItemStack stack : bag.stacks()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static String where(GameTestHelper helper, Run r) {
		BuildSite site = r.live();
		return site == null ? "(no site)" : site.stage() + " " + site.status() + " cursor " + site.cursor() + " retrying " + site.isRetrying()
			+ " skipped " + site.skipped() + " missing " + site.missing() + "; builder at " + rel(helper, r.builder()[0].blockPosition());
	}

	/** Watches, every tick, that each of {@code blocks} still stands; the first one broken is noted with its tick. */
	private static void watchStanding(GameTestHelper helper, Map<BlockPos, Block> blocks, List<String> broken, Run r) {
		helper.onEachTick(() -> {
			blocks.forEach((pos, block) -> {
				if (!helper.getBlockState(pos).is(block) && broken.stream().noneMatch(s -> s.startsWith(pos.toShortString()))) {
					broken.add(pos.toShortString() + " " + block + " became " + helper.getBlockState(pos) + " in " + where(helper, r));
				}
			});
		});
	}

	// ------------------------------------------------------------------------------------------------- tests

	/**
	 * A barrel, a trapped chest, a shulker box and a hopper, each holding something, stand where the hut's walls go
	 * before the build starts. CLEAR must leave every one of them standing with what it holds, the steps they block
	 * are skipped (at least four counted in {@code skipped}), and the hut still finishes.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaKeepContainersWalls1028"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaKeepContainersWalls1028")
	public void everyKindOfContainerInTheWallsSurvivesWithItsThingsAndIsSkipped(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, ModBlocks.BUILDERS_BENCH, BENCH, CHEST, new BlockPos(3, 2, 3));
		List<BlockPos> spots = wallSpots(helper, r.plan(), 4);
		Block[] kinds = {Blocks.BARREL, Blocks.TRAPPED_CHEST, Blocks.SHULKER_BOX, Blocks.HOPPER};
		Item[] things = {Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.LAPIS_LAZULI};
		Map<BlockPos, Block> watched = new LinkedHashMap<>();
		for (int i = 0; i < 4; i++) {
			helper.setBlock(spots.get(i), kinds[i]);
			((Container) helper.getBlockEntity(spots.get(i))).setItem(0, new ItemStack(things[i], 3 + i));
			watched.put(spots.get(i), kinds[i]);
		}
		List<String> broken = new ArrayList<>();
		int[] maxSkipped = {0};
		watchStanding(helper, watched, broken, r);
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (s != null) {
				maxSkipped[0] = Math.max(maxSkipped[0], s.skipped());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "a container in the hut's walls was broken: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			for (int i = 0; i < 4; i++) {
				Container c = (Container) helper.getBlockEntity(spots.get(i));
				helper.assertTrue(c != null && c.countItem(things[i]) == 3 + i, kinds[i] + " at " + spots.get(i).toShortString() + " lost its "
					+ things[i] + ": " + (c == null ? "no container" : c.countItem(things[i])));
			}
			helper.assertTrue(maxSkipped[0] >= 4, "four wall steps were blocked by containers but only " + maxSkipped[0] + " were counted as skipped; the hut's"
				+ " drawing there: " + spots.stream().map(p -> p.toShortString() + "=" + r.plan().targets().get(helper.absolutePos(p))).toList()
				+ "; unfinished " + r.plan().unfinished(level).stream().map(p -> rel(helper, p)).toList());
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24)).stream()
				.noneMatch(e -> List.of(things).contains(e.getItem().getItem())), "a container's things were dropped on the ground");
		});
	}

	/**
	 * The Builder works at a Blueprint Table standing inside the hut (where the drawing has air), and another Builder's
	 * Bench (nobody's job site) stands where a wall goes. CLEAR breaks neither: the Builder keeps his table as his job
	 * site, and the hut is finished round both.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaKeepTableInBox1028"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaKeepTableInBox1028")
	public void aBuilderAtATableInsideTheBoxKeepsItAndAWallBenchStays(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		// work out the hut's spots from its plan first (the same placement, so the same plan)
		BuildPlan probe = BuildPlan.create(io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level.getServer(), TEST_HUT).orElseThrow(),
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE));
		BlockPos table = insideSpot(helper, probe);
		BlockPos bench = wallSpots(helper, probe, 1).get(0);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Run r = start(helper, ModBlocks.BLUEPRINT_TABLE, table, new BlockPos(4, 2, 4), new BlockPos(3, 2, 3));
		Map<BlockPos, Block> watched = new LinkedHashMap<>();
		watched.put(table, ModBlocks.BLUEPRINT_TABLE);
		watched.put(bench, ModBlocks.BUILDERS_BENCH);
		List<String> broken = new ArrayList<>();
		watchStanding(helper, watched, broken, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "CLEAR broke a job block inside the hut's box: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			var site = r.builder()[0].getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
			helper.assertTrue(site.isPresent() && site.get().pos().equals(helper.absolutePos(table)),
				"the Builder lost his Blueprint Table as his job site: " + site);
		});
	}

	/**
	 * A chest (holding diamonds) and a barrel stand where the hut's walls go. The server restarts the first tick
	 * STRUCTURE is past both their steps (put off once, not yet gone back over and skipped; the going back over is done
	 * within one tick, so no tick falls inside it): the restart keeps the cursor, the retry state and the skipped count,
	 * the two steps are then skipped (not lost: the count only ever goes up, by at least two in STRUCTURE), both
	 * containers stand with their things, and the hut finishes.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaKeepContainersReload1028"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaKeepContainersReload1028")
	public void aRestartBetweenPutOffAndSkippedKeepsTheCountAndTheContainers(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, ModBlocks.BUILDERS_BENCH, BENCH, CHEST, new BlockPos(3, 2, 3));
		List<BlockPos> spots = wallSpots(helper, r.plan(), 2);
		helper.setBlock(spots.get(0), Blocks.CHEST);
		((Container) helper.getBlockEntity(spots.get(0))).setItem(5, new ItemStack(Items.DIAMOND, 9));
		helper.setBlock(spots.get(1), Blocks.BARREL);
		((Container) helper.getBlockEntity(spots.get(1))).setItem(0, new ItemStack(Items.EMERALD, 4));
		Map<BlockPos, Block> watched = new LinkedHashMap<>();
		watched.put(spots.get(0), Blocks.CHEST);
		watched.put(spots.get(1), Blocks.BARREL);
		List<String> broken = new ArrayList<>();
		watchStanding(helper, watched, broken, r);
		List<BuildPlan.Step> structure = r.plan().steps(BuildPlan.Stage.STRUCTURE);
		int lastFound = -1;
		for (int i = 0; i < structure.size(); i++) {
			if (structure.get(i).pos().equals(helper.absolutePos(spots.get(0))) || structure.get(i).pos().equals(helper.absolutePos(spots.get(1)))) {
				lastFound = i;
			}
		}
		int lastContainerStep = lastFound;
		helper.assertTrue(lastContainerStep >= 0 && lastContainerStep < structure.size() - 1, "setup: the container spots aren't STRUCTURE steps before its last");
		String[] restarted = {null};
		int[] last = {0};
		int[] atStructure = {-1};
		int[] structureEnd = {-1};
		List<String> wentDown = new ArrayList<>();
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (s == null) {
				return;
			}
			if (s.skipped() < last[0]) {
				wentDown.add(last[0] + " -> " + s.skipped() + " in " + where(helper, r));
			}
			last[0] = s.skipped();
			if (s.stage() == BuildPlan.Stage.STRUCTURE && atStructure[0] < 0) {
				atStructure[0] = s.skipped();
			}
			if (s.stage().ordinal() > BuildPlan.Stage.STRUCTURE.ordinal() && structureEnd[0] < 0) {
				structureEnd[0] = s.skipped();
			}
			// both container steps passed once (put off) and the list not yet gone back over: the restart falls between
			if (restarted[0] == null && s.stage() == BuildPlan.Stage.STRUCTURE && (s.isRetrying() || s.cursor() > lastContainerStep)) {
				int skipped = s.skipped();
				int cursor = s.cursor();
				boolean retrying = s.isRetrying();
				int left = s.retryLeft();
				restart(helper, level, r.builder());
				BuildSite back = r.live();
				helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.STRUCTURE, "after the restart the site is " + (back == null ? "gone" : back.stage()));
				helper.assertTrue(back.isRetrying() == retrying, "the restart changed whether the site was going back over its put-off steps");
				helper.assertTrue(back.skipped() == skipped, "the restart changed skipped from " + skipped + " to " + back.skipped());
				helper.assertTrue(back.cursor() == cursor && back.retryLeft() == left, "the restart moved the retry from cursor " + cursor + " (" + left
					+ " left) to " + back.cursor() + " (" + back.retryLeft() + " left)");
				restarted[0] = "skipped " + skipped + ", cursor " + cursor + " of " + structure.size() + ", retrying " + retrying + ", " + left + " put-off steps left";
				level.getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-keep-containers] restarted with " + restarted[0]));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "STRUCTURE never got past the two container steps for a restart: " + where(helper, r));
			helper.assertTrue(broken.isEmpty(), "a container in the walls was broken: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(wentDown.isEmpty(), "the skipped count went down: " + wentDown);
			helper.assertTrue(structureEnd[0] - atStructure[0] >= 2, "STRUCTURE counted " + (structureEnd[0] - atStructure[0])
				+ " skipped steps; the two container spots should both be counted (" + restarted[0] + ")");
			helper.assertTrue(((Container) helper.getBlockEntity(spots.get(0))).countItem(Items.DIAMOND) == 9, "the wall chest's diamonds are gone");
			helper.assertTrue(((Container) helper.getBlockEntity(spots.get(1))).countItem(Items.EMERALD) == 4, "the wall barrel's emeralds are gone");
		});
	}

	/**
	 * Mid-STRUCTURE, a player sets a chest holding 16 diamonds into a wall spot the Builder hasn't reached yet. Whatever
	 * the Builder does with the spot, the player's diamonds are never lost: they are still in that chest, or in the
	 * Builder's chest or bag, none on the ground, and the hut finishes.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaKeepContainersMid1028"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaKeepContainersMid1028")
	public void aFullChestPutInAnUnbuiltWallMidStructureLosesNothing(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, ModBlocks.BUILDERS_BENCH, BENCH, CHEST, new BlockPos(3, 2, 3));
		// of the second layer's full-block wall spots, the one STRUCTURE reaches last
		List<BuildPlan.Step> structure = r.plan().steps(BuildPlan.Stage.STRUCTURE);
		BlockPos spot = null;
		int index = -1;
		for (BlockPos candidate : wallSpots(helper, r.plan(), 3)) {
			BlockPos abs = helper.absolutePos(candidate);
			for (int i = 0; i < structure.size(); i++) {
				if (structure.get(i).pos().equals(abs) && i > index) {
					index = i;
					spot = candidate;
				}
			}
		}
		helper.assertTrue(index > 0, "setup: the wall spot isn't a STRUCTURE step");
		int stepIndex = index;
		BlockPos spotAt = spot;
		boolean[] placed = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (!placed[0] && s != null && s.stage() == BuildPlan.Stage.STRUCTURE && s.cursor() > 0 && s.cursor() < stepIndex
				&& helper.getBlockState(spotAt).isAir()) {
				helper.setBlock(spotAt, Blocks.CHEST);
				((Container) helper.getBlockEntity(spotAt)).setItem(13, new ItemStack(Items.DIAMOND, 16));
				placed[0] = true;
				level.getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-keep-containers] chest put in at cursor " + s.cursor()
					+ " of " + structure.size() + " (its step " + stepIndex + ")"));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(placed[0], "setup: the chest was never put in mid-STRUCTURE: " + where(helper, r));
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			int inWall = level.getBlockEntity(helper.absolutePos(spotAt)) instanceof Container c ? c.countItem(Items.DIAMOND) : 0;
			int inSupply = ((Container) helper.getBlockEntity(CHEST)).countItem(Items.DIAMOND);
			int bag = inBag(r.builder()[0], Items.DIAMOND);
			long onGround = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24)).stream()
				.filter(e -> e.getItem().is(Items.DIAMOND)).count();
			helper.assertTrue(inWall + inSupply + bag == 16 && onGround == 0, "the player's 16 diamonds: " + inWall + " in the wall chest, " + inSupply
				+ " in the Builder's chest, " + bag + " in his bag, " + onGround + " stacks on the ground (now " + helper.getBlockState(spotAt) + ")");
		});
	}
}
