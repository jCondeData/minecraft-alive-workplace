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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;

/**
 * QA (lane-b-1026-0338) from the wiki (builder.md, "Stages, in order"): "CLEAR (top-down), FOUNDATION, STRUCTURE
 * (bottom-up), DECORATION, LANDSCAPE, DONE"; "DECORATION is what needs something to hang on or stand on (torches,
 * doors...), placed after the structure is up"; "LANDSCAPE (leveling the ground round the build, dirt) never waits for
 * materials"; "A step that cannot be done yet is put off once, then skipped (counted in skipped)"; "A fixed set of
 * blocks is never placed (bedrock, command blocks, spawners, portals, barrier, fire...)"; "FOUNDATION and LANDSCAPE
 * depend on the terrain, so a site loaded in the middle of those stages makes its list again from the terrain; steps
 * already done are skipped". Each is watched through real ticks of a real Builder, not by calling the stage code.
 */
public class QaBuildStagesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** The hut stands at x 6-10, z 6-10, from this y up. */
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private record Run(ServerLevel level, Villager[] builder, BuildSite site, BuildPlan plan) {
		boolean done() {
			return BuildSiteManager.get(level).get(site.id()) == null;
		}
	}

	// ------------------------------------------------------------------------------------------------- helpers

	private static ServerLevel rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
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

	private static ItemStack[] hutMaterials() {
		return new ItemStack[]{new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
	}

	private static Run start(GameTestHelper helper, ResourceLocation blueprint, BlockPos origin, ItemStack... chestItems) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chestItems.length; i++) {
			chest.setItem(i, chestItems[i].copy());
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, blueprint, placement);
		helper.assertTrue(site != null, "the Builder took no build");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for " + blueprint);
		return new Run(level, new Villager[]{villager}, site, plan);
	}

	/** A blueprint scanned from the world (as a player's Scan Tool does), the scanned blocks then taken away again. */
	private static ResourceLocation scan(GameTestHelper helper, String name, BlockPos origin, Vec3i size) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int y = size.getY() - 1; y >= 0; y--) {
			for (int x = 0; x < size.getX(); x++) {
				for (int z = 0; z < size.getZ(); z++) {
					level.setBlock(helper.absolutePos(origin.offset(x, y, z)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		return id;
	}

	private static String where(GameTestHelper helper, Run r) {
		BuildSite s = r.site();
		return s.stage() + " " + s.status() + " cursor " + s.cursor() + " skipped " + s.skipped() + " missing " + s.missing() + " done " + r.done()
			+ "; builder at " + helper.relativePos(r.builder()[0].blockPosition());
	}

	private static Map<Item, Integer> bag(Villager villager) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks()) {
			if (!stack.isEmpty()) {
				out.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	private static String rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	// ------------------------------------------------------------------------------------------------- the order

	/**
	 * The test hut with two layers of dirt in its way inside and five mounds round it, watched every tick: the stages
	 * come in the drawn order and never go back; the dirt in the way goes top layer first; every wall, floor and roof
	 * block goes down after the clearing, never lower than one already laid in STRUCTURE; the door and torch only once
	 * every structure block is up; the mounds only after the decoration, in LANDSCAPE.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaStagesOrder"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaStagesOrder")
	public void theStagesComeInOrderAndEachBlockInItsStage(GameTestHelper helper) {
		rules(helper);
		for (int x = 7; x <= 9; x++) {
			for (int z = 7; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 4, z), Blocks.DIRT);
			}
		}
		List<BlockPos> mounds = List.of(new BlockPos(5, 2, 8), new BlockPos(11, 2, 8), new BlockPos(8, 2, 11), new BlockPos(5, 2, 10), new BlockPos(11, 2, 6));
		for (BlockPos p : mounds) {
			helper.setBlock(p, Blocks.DIRT);
		}
		Run r = start(helper, TEST_HUT, HUT_ORIGIN, hutMaterials());
		// what stands in the way at the start (a CLEAR step on a block already air is nothing to watch)
		List<BuildPlan.Step> clear = r.plan().steps(BuildPlan.Stage.CLEAR).stream().filter(s -> !r.level().getBlockState(s.pos()).isAir()).toList();
		Map<BlockPos, Block> was = new HashMap<>();
		clear.forEach(s -> was.put(s.pos(), r.level().getBlockState(s.pos()).getBlock()));
		List<BuildPlan.Step> structure = r.plan().steps(BuildPlan.Stage.STRUCTURE).stream()
			.filter(s -> !r.level().getBlockState(s.pos()).is(s.state().getBlock())).toList();
		List<BuildPlan.Step> decoration = r.plan().steps(BuildPlan.Stage.DECORATION);
		helper.assertTrue(clear.size() >= 18, "setup: only " + clear.size() + " block(s) of dirt in the hut's way to clear");
		helper.assertTrue(structure.size() > 20, "setup: only " + structure.size() + " structure blocks");
		helper.assertTrue(decoration.stream().anyMatch(s -> s.state().is(Blocks.OAK_DOOR)) && decoration.stream().anyMatch(s -> s.state().getBlock().asItem() == Items.TORCH),
			"setup: the door and torch aren't decoration: " + decoration.stream().map(s -> s.state().getBlock().toString()).toList());

		List<BuildPlan.Stage> seen = new ArrayList<>();
		Map<BlockPos, Integer> clearedAt = new HashMap<>();
		Map<BlockPos, Integer> structureAt = new HashMap<>();
		Map<BlockPos, BuildPlan.Stage> structureStage = new HashMap<>();
		Map<BlockPos, Integer> decorationAt = new HashMap<>();
		Map<BlockPos, BuildPlan.Stage> decorationStage = new HashMap<>();
		Map<BlockPos, BuildPlan.Stage> moundStage = new HashMap<>();
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			BuildPlan.Stage stage = r.done() ? BuildPlan.Stage.DONE : r.site().stage();
			if (seen.isEmpty() || seen.get(seen.size() - 1) != stage) {
				seen.add(stage);
			}
			for (BuildPlan.Step s : clear) {
				if (!clearedAt.containsKey(s.pos()) && !r.level().getBlockState(s.pos()).is(was.get(s.pos()))) {
					clearedAt.put(s.pos(), tick[0]);
				}
			}
			for (BuildPlan.Step s : structure) {
				if (!structureAt.containsKey(s.pos()) && r.level().getBlockState(s.pos()).is(s.state().getBlock())) {
					structureAt.put(s.pos(), tick[0]);
					structureStage.put(s.pos(), stage);
				}
			}
			for (BuildPlan.Step s : decoration) {
				if (!decorationAt.containsKey(s.pos()) && r.level().getBlockState(s.pos()).is(s.state().getBlock())) {
					decorationAt.put(s.pos(), tick[0]);
					decorationStage.put(s.pos(), stage);
				}
			}
			for (BlockPos m : mounds) {
				if (!moundStage.containsKey(m) && helper.getBlockState(m).isAir()) {
					moundStage.put(m, stage);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(r.done(), "still building: " + where(helper, r));
			List<BlockPos> wrong = r.plan().unfinished(r.level());
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut not built, e.g. " + wrong.stream().limit(5).map(p -> rel(helper, p)).toList());
			// 1. the stages, in the drawn order, never back
			for (int i = 1; i < seen.size(); i++) {
				helper.assertTrue(seen.get(i).ordinal() > seen.get(i - 1).ordinal(), "the stages went " + seen + ": " + seen.get(i) + " came after " + seen.get(i - 1));
			}
			for (BuildPlan.Stage want : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
				helper.assertTrue(seen.contains(want), "the build never showed " + want + ": " + seen);
			}
			// 2. CLEAR top-down
			helper.assertTrue(clearedAt.size() == clear.size(), "only " + clearedAt.size() + " of " + clear.size() + " blocks in the way were cleared");
			List<BlockPos> clearOrder = clearedAt.keySet().stream().sorted((a, b) -> Integer.compare(clearedAt.get(a), clearedAt.get(b))).toList();
			for (int i = 1; i < clearOrder.size(); i++) {
				BlockPos a = clearOrder.get(i - 1);
				BlockPos b = clearOrder.get(i);
				helper.assertTrue(clearedAt.get(a).equals(clearedAt.get(b)) || b.getY() <= a.getY(), "CLEAR went up: " + rel(helper, b) + " (tick "
					+ clearedAt.get(b) + ") was taken after " + rel(helper, a) + " (tick " + clearedAt.get(a) + ")");
			}
			int lastCleared = clearedAt.values().stream().max(Integer::compare).orElse(0);
			// 3. STRUCTURE bottom-up, after the clearing, in its own stage
			helper.assertTrue(structureAt.size() == structure.size(), "only " + structureAt.size() + " of " + structure.size() + " structure blocks were seen laid");
			List<BlockPos> order = structureAt.keySet().stream().sorted((a, b) -> Integer.compare(structureAt.get(a), structureAt.get(b))).toList();
			int highest = Integer.MIN_VALUE;
			BlockPos highestAt = null;
			for (BlockPos p : order) {
				helper.assertTrue(structureAt.get(p) > lastCleared, "the structure block at " + rel(helper, p) + " went down (tick " + structureAt.get(p)
					+ ") before the clearing ended (tick " + lastCleared + ")");
				BuildPlan.Stage st = structureStage.get(p);
				helper.assertTrue(st == BuildPlan.Stage.STRUCTURE || st == BuildPlan.Stage.DECORATION && structureAt.get(p) == order.stream()
					.mapToInt(structureAt::get).max().orElse(0), "the structure block at " + rel(helper, p) + " went down in " + st);
				if (highestAt != null && p.getY() < highest && !structureAt.get(p).equals(structureAt.get(highestAt))) {
					helper.fail("STRUCTURE went down: " + rel(helper, p) + " (tick " + structureAt.get(p) + ") was laid after " + rel(helper, highestAt)
						+ " (tick " + structureAt.get(highestAt) + ")");
				}
				if (p.getY() > highest) {
					highest = p.getY();
					highestAt = p;
				}
			}
			int lastStructure = structureAt.values().stream().max(Integer::compare).orElse(0);
			// 4. DECORATION after the structure is up
			helper.assertTrue(decorationAt.size() == decoration.size(), "only " + decorationAt.size() + " of " + decoration.size() + " decorations were seen put up");
			for (Map.Entry<BlockPos, Integer> e : decorationAt.entrySet()) {
				helper.assertTrue(e.getValue() > lastStructure, "the " + helper.getBlockState(helper.relativePos(e.getKey())).getBlock() + " at "
					+ rel(helper, e.getKey()) + " went up at tick " + e.getValue() + ", before the last structure block (tick " + lastStructure + ")");
				helper.assertTrue(decorationStage.get(e.getKey()) == BuildPlan.Stage.DECORATION || decorationStage.get(e.getKey()) == BuildPlan.Stage.LANDSCAPE,
					"the decoration at " + rel(helper, e.getKey()) + " went up in " + decorationStage.get(e.getKey()));
			}
			// 5. LANDSCAPE last
			for (BlockPos m : mounds) {
				helper.assertTrue(moundStage.get(m) == BuildPlan.Stage.LANDSCAPE || moundStage.get(m) == BuildPlan.Stage.DONE,
					"the mound at " + m.toShortString() + " was dug away in " + moundStage.get(m) + " (stages seen " + seen + ")");
			}
		});
	}

	// ------------------------------------------------------------------------------------------------- LANDSCAPE without dirt

	/**
	 * Holes in the ground round the hut and not a block of dirt anywhere (the chest holds only the hut's materials):
	 * LANDSCAPE never waits for it. The build reaches DONE, is never shown waiting or short of dirt while landscaping,
	 * and the holes stay holes (nothing else is put in them).
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaStagesNoDirt"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaStagesNoDirt")
	public void landscapingWithNoDirtAnywhereStillFinishes(GameTestHelper helper) {
		rules(helper);
		List<BlockPos> holes = List.of(new BlockPos(5, 1, 8), new BlockPos(11, 1, 8), new BlockPos(8, 1, 5), new BlockPos(8, 1, 11));
		for (BlockPos h : holes) {
			helper.setBlock(h, Blocks.AIR);
		}
		Run r = start(helper, TEST_HUT, HUT_ORIGIN, hutMaterials());
		long planned = r.plan().steps(BuildPlan.Stage.LANDSCAPE).stream().filter(s -> holes.stream().anyMatch(h -> helper.absolutePos(h).equals(s.pos()))).count();
		helper.assertTrue(planned > 0, "setup: none of the holes is in the landscaping plan: "
			+ r.plan().steps(BuildPlan.Stage.LANDSCAPE).stream().map(s -> rel(helper, s.pos()) + "=" + s.state().getBlock()).toList());
		String[] bad = {null};
		boolean[] landscaped = {false};
		helper.onEachTick(() -> {
			if (bad[0] != null || r.done() || r.site().stage() != BuildPlan.Stage.LANDSCAPE) {
				return;
			}
			landscaped[0] = true;
			if (r.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS || r.site().missing().containsKey(Items.DIRT)) {
				bad[0] = "while landscaping the site was " + r.site().status() + ", short of " + r.site().missing();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(bad[0] == null, bad[0]);
			helper.assertTrue(r.done(), "with no dirt anywhere the build never finished: " + where(helper, r));
			helper.assertTrue(landscaped[0], "the build never showed LANDSCAPE");
			List<BlockPos> wrong = r.plan().unfinished(r.level());
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut not built");
			for (BlockPos h : holes) {
				helper.assertTrue(helper.getBlockState(h).isAir() || helper.getBlockState(h).is(Blocks.DIRT), "the hole at " + h.toShortString()
					+ " was filled with " + helper.getBlockState(h) + " though no dirt was anywhere");
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0 && chest.countItem(Items.OAK_PLANKS) == 0, "the hut's materials weren't all used: "
				+ chest.countItem(Items.COBBLESTONE) + " cobblestone, " + chest.countItem(Items.OAK_PLANKS) + " planks left");
		});
	}

	// ------------------------------------------------------------------------------------------------- never placed

	/**
	 * A scan holding a command block, a chain command block, a barrier, an end portal, an end gateway and fire (on
	 * netherrack, where fire burns for ever) between cobblestone: the Builder asks only for the cobblestone and the
	 * netherrack and finishes with none of the six put up, nor anything put in their place.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaStagesNeverPlaced"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaStagesNeverPlaced")
	public void commandBlocksBarrierPortalsAndFireAreLeftOut(GameTestHelper helper) {
		rules(helper);
		BlockPos origin = new BlockPos(6, 2, 9);
		List<Block> row = List.of(Blocks.COMMAND_BLOCK, Blocks.COBBLESTONE, Blocks.BARRIER, Blocks.CHAIN_COMMAND_BLOCK, Blocks.END_PORTAL, Blocks.COBBLESTONE,
			Blocks.END_GATEWAY, Blocks.NETHERRACK);
		for (int x = 0; x < row.size(); x++) {
			helper.setBlock(origin.offset(x, 0, 0), row.get(x));
		}
		BlockPos fire = origin.offset(row.size() - 1, 1, 0);
		helper.setBlock(fire, Blocks.FIRE);
		helper.assertBlockPresent(Blocks.FIRE, fire);
		ResourceLocation id = scan(helper, "qastages_never", origin, new Vec3i(row.size(), 2, 1));
		Run r = start(helper, id, origin);
		boolean[] stocked = {false};
		helper.runAfterDelay(600, () -> {
			Map<Item, Integer> missing = r.site().missing();
			helper.assertTrue(missing.equals(Map.of(Items.COBBLESTONE, 2, Items.NETHERRACK, 1)),
				"with an empty chest the Builder should be short of 2 cobblestone and 1 netherrack only: " + missing);
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
			chest.setItem(1, new ItemStack(Items.NETHERRACK, 1));
			stocked[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stocked[0], "not stocked yet");
			helper.assertTrue(r.done(), "still building: " + where(helper, r));
			for (int x = 0; x < row.size(); x++) {
				Block drawn = row.get(x);
				Block want = drawn == Blocks.COBBLESTONE || drawn == Blocks.NETHERRACK ? drawn : Blocks.AIR;
				helper.assertTrue(helper.getBlockState(origin.offset(x, 0, 0)).is(want), "where the scan had " + drawn + " the build has "
					+ helper.getBlockState(origin.offset(x, 0, 0)).getBlock() + ", not " + want);
			}
			helper.assertTrue(helper.getBlockState(fire).isAir(), "the Builder lit the fire: " + helper.getBlockState(fire));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0 && chest.countItem(Items.NETHERRACK) == 0, "materials left: "
				+ chest.countItem(Items.COBBLESTONE) + " cobblestone, " + chest.countItem(Items.NETHERRACK) + " netherrack");
		});
	}

	// ------------------------------------------------------------------------------------------------- put off, then skipped

	/**
	 * A scan of a wall torch hung on bedrock, with cobblestone beside it. Bedrock is never placed, so the torch has
	 * nothing to hang on: it is put off once (the site goes back over it), then skipped and counted in {@code skipped},
	 * and the build still finishes, the torch never put up (not even for a tick) and still in the chest.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaStagesSkipped"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaStagesSkipped")
	public void aTorchWithNothingToHangOnIsPutOffOnceThenSkipped(GameTestHelper helper) {
		rules(helper);
		BlockPos origin = new BlockPos(6, 2, 9);
		helper.setBlock(origin, Blocks.BEDROCK);
		helper.setBlock(origin.east(), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
		helper.setBlock(origin.east(2), Blocks.COBBLESTONE);
		helper.assertBlockPresent(Blocks.WALL_TORCH, origin.east());
		ResourceLocation id = scan(helper, "qastages_skip", origin, new Vec3i(3, 1, 1));
		Run r = start(helper, id, origin, new ItemStack(Items.COBBLESTONE, 1), new ItemStack(Items.TORCH, 1));
		helper.assertTrue(r.plan().steps(BuildPlan.Stage.DECORATION).stream().anyMatch(s -> s.pos().equals(helper.absolutePos(origin.east()))),
			"setup: the wall torch isn't a decoration step");
		boolean[] retried = {false};
		String[] torchSeen = {null};
		helper.onEachTick(() -> {
			if (!r.done() && r.site().stage() == BuildPlan.Stage.DECORATION && r.site().isRetrying()) {
				retried[0] = true;
			}
			if (torchSeen[0] == null && !helper.getBlockState(origin.east()).isAir()) {
				torchSeen[0] = helper.getBlockState(origin.east()) + " in " + (r.done() ? "DONE" : r.site().stage());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(r.done(), "still building: " + where(helper, r));
			helper.assertTrue(torchSeen[0] == null, "a torch with nothing to hang on was put up: " + torchSeen[0]);
			helper.assertTrue(retried[0], "the torch was never put off and gone back to (no retry seen in DECORATION)");
			helper.assertTrue(r.site().skipped() == 1, "skipped counts " + r.site().skipped() + ", not the 1 torch");
			helper.assertBlockPresent(Blocks.COBBLESTONE, origin.east(2));
			helper.assertTrue(helper.getBlockState(origin).isAir(), "bedrock's place holds " + helper.getBlockState(origin));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.TORCH) + bag(r.builder()[0]).getOrDefault(Items.TORCH, 0) == 1,
				"the unused torch is gone: chest " + chest.countItem(Items.TORCH) + ", bag " + bag(r.builder()[0]));
		});
	}

	// ------------------------------------------------------------------------------------------------- FOUNDATION restart

	/**
	 * The hut placed two blocks above the ground, so FOUNDATION fills under it, with the chest holding exactly what the
	 * plan says it needs. Halfway through the foundation the server restarts (site and Builder saved and loaded). The
	 * loaded site makes its foundation list again from the terrain: the shown progress doesn't drop, the part already
	 * filled isn't filled again, and the hut finishes standing on a whole foundation with nothing left over and nothing
	 * short.
	 */
	//$ gametest_ticks_batch BIG_AREA '5000' '"qaStagesFoundationRestart"'
	@GameTest(template = BIG_AREA, timeoutTicks = 5000, batch = "qaStagesFoundationRestart")
	public void aRestartMidFoundationRedoesTheListFromTheTerrain(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, TEST_HUT, new BlockPos(6, 4, 6));
		List<BuildPlan.Step> foundation = r.plan().steps(BuildPlan.Stage.FOUNDATION);
		helper.assertTrue(foundation.size() >= 25, "setup: the raised hut has only " + foundation.size() + " foundation steps");
		// the chest filled before the Builder's first tick with exactly what the plan says it needs
		Map<Item, Integer> needs = r.plan().materials();
		Container supplies = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : needs.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				helper.assertTrue(slot < supplies.getContainerSize(), "setup: the materials don't fit a chest: " + needs);
				supplies.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		String[] restarted = {null};
		helper.onEachTick(() -> {
			BuildSite live = BuildSiteManager.get(level).get(r.site().id());
			if (restarted[0] != null || live == null || live.stage() != BuildPlan.Stage.FOUNDATION || live.cursor() * 2 < foundation.size()) {
				return;
			}
			float before = live.progress(live.plan(level));
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			Villager old = r.builder()[0];
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(old.save(tag), "the builder wasn't saved");
			old.discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager");
			level.addFreshEntity(loaded);
			r.builder()[0] = (Villager) loaded;
			BuildSite back = BuildSiteManager.get(level).get(r.site().id());
			helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.FOUNDATION, "after the restart the site is " + (back == null ? "gone" : back.stage()));
			BuildPlan again = back.plan(level);
			float after = back.progress(again);
			helper.assertTrue(after >= before, "the shown progress dropped across the restart: " + before + " to " + after);
			int redo = again.steps(BuildPlan.Stage.FOUNDATION).size();
			helper.assertTrue(redo < foundation.size(), "after the restart the foundation list is " + redo + " steps again, as long as at the start ("
				+ foundation.size() + "): what was filled isn't counted as ground");
			List<String> refill = again.steps(BuildPlan.Stage.FOUNDATION).stream().filter(s -> !level.getBlockState(s.pos()).isAir()
				&& level.getBlockState(s.pos()).is(s.state().getBlock())).map(s -> rel(helper, s.pos())).toList();
			helper.assertTrue(refill.isEmpty(), "the remade foundation list fills blocks already filled: " + refill);
			restarted[0] = "cursor " + live.cursor() + " of " + foundation.size() + ", " + redo + " steps after";
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "never halfway through the foundation: " + where(helper, r));
			BuildSite live = BuildSiteManager.get(level).get(r.site().id());
			helper.assertTrue(live == null, "still building after the restart (" + restarted[0] + "): " + (live == null ? "" : live.stage() + " " + live.status()
				+ " missing " + live.missing()));
			List<BlockPos> wrong = r.plan().unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut wrong");
			List<String> holes = foundation.stream().filter(s -> level.getBlockState(s.pos()).isAir()).map(s -> rel(helper, s.pos())).toList();
			helper.assertTrue(holes.isEmpty(), "the foundation has holes after the restart: " + holes);
			Container chest = helper.getBlockEntity(CHEST);
			Map<Item, Integer> bagLeft = bag(r.builder()[0]);
			Map<Item, Integer> left = new LinkedHashMap<>();
			for (Item item : needs.keySet()) {
				int n = chest.countItem(item) + bagLeft.getOrDefault(item, 0);
				if (n != 0) {
					left.put(item, n);
				}
			}
			helper.assertTrue(left.isEmpty(), "after a restart mid-foundation the hut is built with materials left over: " + left + " (" + restarted[0] + ")");
		});
	}
}
