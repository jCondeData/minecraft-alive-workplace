package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * B261 (owner, live server): a Builder repairing a Storehouse Shed waited for a Hay Bale and an Oak Ladder that were in
 * the chests by its bench. Every supply lookup left out the chests inside the build's own box (for a new build, the
 * chests it puts up are its owner's), so a repair's building, the Storehouse Shed with all its chests or a shed the
 * owner keeps the builder's chests in, gave the builder nothing. A repair's building stands: its chests count.
 */
public class B261GameTests {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation STOREHOUSE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "storehouse");
	/** The test shed: walls x 6-10, z 6-10, y 2-3, a roof at y 4, its door at x 8 on the front (z 6). */
	private static final BlockPos SHED = new BlockPos(6, 2, 6);
	private static final BlockPos BENCH = new BlockPos(8, 2, 4);
	private static final BlockPos HAY = new BlockPos(9, 2, 9);
	private static final BlockPos LADDER = new BlockPos(9, 2, 7);
	/** Where the shed keeps its container. */
	private static final BlockPos STORE = new BlockPos(7, 2, 8);

	private enum Kind { CHEST, BARREL, DOUBLE_CHEST, TRAPPED_CHEST, CHEST_ABOVE }

	/** The Storehouse Shed itself, finished, its hay bale and bottom ladder gone, both in one of its own chests: repaired. */
	//$ gametest_ticks_batch HUGE '3000' '"b261StorehouseShed"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b261StorehouseShed")
	public void aStorehouseShedIsRepairedFromItsOwnChests(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		Leftovers.storehouses(helper);
		liveServer(helper);
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, STOREHOUSE).orElseThrow(() -> new GameTestAssertException("no " + STOREHOUSE));
		BlockPos origin = new BlockPos(10, 6, 12);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		// the ground at the building's floor (its vault below it)
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y < origin.getY(); y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		for (BuildPlan.Stage stage : java.util.List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
		BlockPos hay = origin.offset(2, 1, 5);
		BlockPos ladder = origin.offset(2, 1, 3);
		helper.assertBlockPresent(Blocks.HAY_BLOCK, hay);
		helper.assertBlockPresent(Blocks.LADDER, ladder);
		helper.setBlock(hay, Blocks.AIR);
		helper.setBlock(ladder, Blocks.AIR);
		BlockPos shedChest = origin.offset(3, 1, 6);
		helper.assertBlockPresent(Blocks.CHEST, shedChest);
		Container chest = helper.getBlockEntity(shedChest);
		chest.setItem(0, new ItemStack(Items.HAY_BLOCK));
		chest.setItem(1, new ItemStack(Items.LADDER));
		BlockPos bench = origin.offset(4, 1, -2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = startRepair(helper, STOREHOUSE, placement, bench, bench.offset(1, 0, -1));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.HAY_BLOCK, hay);
			helper.assertBlockPresent(Blocks.LADDER, ladder);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "still repairing");
			helper.assertTrue(chest.countItem(Items.HAY_BLOCK) == 0 && chest.countItem(Items.LADDER) == 0, "the shed's chest still holds them");
		});
	}

	/** A shed with the bench's chest inside it, its hay bale and ladder gone, both in that chest: repaired from it. */
	//$ gametest_ticks_batch BIG '2000' '"b261Chest"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "b261Chest")
	public void aRepairTakesFromAChestInsideTheBuilding(GameTestHelper helper) {
		shedRepair(helper, Kind.CHEST, false);
	}

	//$ gametest_ticks_batch BIG '2000' '"b261Barrel"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "b261Barrel")
	public void aRepairTakesFromABarrelInsideTheBuilding(GameTestHelper helper) {
		shedRepair(helper, Kind.BARREL, false);
	}

	/** The double chest's materials in its second half. */
	//$ gametest_ticks_batch BIG '2000' '"b261DoubleChest"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "b261DoubleChest")
	public void aRepairTakesFromADoubleChestInsideTheBuilding(GameTestHelper helper) {
		shedRepair(helper, Kind.DOUBLE_CHEST, false);
	}

	//$ gametest_ticks_batch BIG '2000' '"b261TrappedChest"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "b261TrappedChest")
	public void aRepairTakesFromATrappedChestInsideTheBuilding(GameTestHelper helper) {
		shedRepair(helper, Kind.TRAPPED_CHEST, false);
	}

	/** The chest one block above the bench (on a shelf of planks). */
	//$ gametest_ticks_batch BIG '2000' '"b261ChestAbove"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "b261ChestAbove")
	public void aRepairTakesFromAChestOneBlockAboveTheBench(GameTestHelper helper) {
		shedRepair(helper, Kind.CHEST_ABOVE, false);
	}

	/** Saved and loaded while waiting to start: the builder still finds the chest in the shed and finishes. */
	//$ gametest_ticks_batch BIG '2000' '"b261Reload"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "b261Reload")
	public void aRepairTakesFromTheChestInsideTheBuildingAfterAReload(GameTestHelper helper) {
		shedRepair(helper, Kind.CHEST, true);
	}

	/** A new build still leaves the chests inside its own box alone: they are what it puts up, its owner's. */
	//$ gametest 'BIG'
	@GameTest(template = BIG)
	public void aNewBuildStillLeavesOutTheChestsInItsBox(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SHED), Rotation.NONE, Mirror.NONE);
		Blueprint blueprint = BlueprintLibrary.get(level, STOREHOUSE).orElseThrow(() -> new GameTestAssertException("no " + STOREHOUSE));
		BuildPlan fresh = BuildPlan.create(blueprint, placement);
		helper.assertTrue(fresh.supplyExclude() != null && fresh.supplyExclude().equals(fresh.bounds()), "a new build counts its own box's chests");
		BuildPlan repair = BuildPlan.repair(blueprint, placement, level);
		helper.assertTrue(repair.supplyExclude() == null, "a repair leaves out its building's chests: " + repair.supplyExclude());
		helper.succeed();
	}

	private static void shedRepair(GameTestHelper helper, Kind kind, boolean reload) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		liveServer(helper);
		ServerLevel level = helper.getLevel();
		// the shed: a plank floor's worth of cobblestone walls, a plank roof, the door in the front
		for (int x = 6; x <= 10; x++) {
			for (int z = 6; z <= 10; z++) {
				boolean wall = x == 6 || x == 10 || z == 6 || z == 10;
				for (int y = 2; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.COBBLESTONE : Blocks.AIR);
				}
				helper.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
			}
		}
		helper.setBlock(new BlockPos(8, 2, 6), Blocks.AIR);
		helper.setBlock(new BlockPos(8, 3, 6), Blocks.AIR);
		helper.setBlock(HAY, Blocks.HAY_BLOCK);
		helper.setBlock(LADDER, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		BlockPos store = kind == Kind.CHEST_ABOVE ? STORE.above() : STORE;
		BlockPos other = STORE.relative(Direction.SOUTH);
		switch (kind) {
			case CHEST -> helper.setBlock(store, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
			case BARREL -> helper.setBlock(store, Blocks.BARREL);
			case TRAPPED_CHEST -> helper.setBlock(store, Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
			case CHEST_ABOVE -> {
				helper.setBlock(STORE, Blocks.OAK_PLANKS);
				helper.setBlock(store, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
			}
			case DOUBLE_CHEST -> {
				// facing east, the left half's other half is to its south
				BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
				helper.setBlock(store, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
				helper.setBlock(other, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"b261_" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SHED), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(HAY, Blocks.AIR);
		helper.setBlock(LADDER, Blocks.AIR);
		Container container = helper.getBlockEntity(kind == Kind.DOUBLE_CHEST ? other : store);
		container.setItem(3, new ItemStack(Items.HAY_BLOCK));
		container.setItem(4, new ItemStack(Items.LADDER));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SHED), Rotation.NONE, Mirror.NONE);
		Villager first = startRepair(helper, id, placement, BENCH, new BlockPos(8, 2, 2));
		Villager[] builder = {first};
		if (reload) {
			helper.runAfterDelay(20, () -> builder[0] = reload(level, builder[0]));
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(!reload || helper.getTick() > 20, "not reloaded yet");
			helper.assertBlockPresent(Blocks.HAY_BLOCK, HAY);
			helper.assertBlockPresent(Blocks.LADDER, LADDER);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder[0]), "still repairing");
			helper.assertTrue(container.countItem(Items.HAY_BLOCK) == 0 && container.countItem(Items.LADDER) == 0,
				"the " + kind + " still holds them: " + container.countItem(Items.HAY_BLOCK) + " hay, " + container.countItem(Items.LADDER) + " ladders");
		});
	}

	/** A builder employed at {@code bench}, {@code structure} at {@code placement} on their books as finished: upkeep sends them to repair it. */
	private static Villager startRepair(GameTestHelper helper, ResourceLocation structure, BlueprintData.Placement placement, BlockPos bench, BlockPos spawn) {
		ServerLevel level = helper.getLevel();
		Villager builder = helper.spawn(EntityType.VILLAGER, spawn);
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSiteManager.get(level).recordFinished(structure, placement, builder.getUUID());
		helper.runAfterDelay(2, () -> {
			BuildSite site = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
			helper.assertTrue(site != null && site.isRepair(), "no repair started");
		});
		return builder;
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}

	/**
	 * As on the live server: repairs on without asking (the ask has RepairAskGameTests), no scaffolding, in the builder's
	 * work hours. The day cycle runs in the suite, so the clock here is wherever earlier batches left it: started after
	 * about 11000 a builder rests through the whole timeout and never puts the blocks back (B512).
	 */
	private static void liveServer(GameTestHelper helper) {
		helper.setDayTime(2000);
		boolean upkeep = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		boolean scaffolds = Scaffolds.ENABLED;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Scaffolds.ENABLED = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = upkeep;
			Upkeep.ASK = asks;
			Scaffolds.ENABLED = scaffolds;
		});
	}
}
