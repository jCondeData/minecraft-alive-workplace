package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * B132: in the city time-lapse a porter stood stuck in a wall between a stair and a slab a builder had just put down. A
 * builder checked for room for the block in its blueprint but put down the block shaped by its neighbours: an
 * outer-corner stair whose neighbour isn't built yet goes down straight, which fills a quarter more, and that quarter
 * went down onto whoever stood there. Here: a villager in that quarter is never inside the stair, and the stair still
 * gets built; and a mob left inside a block by a builder's placement is moved out to the nearest room to stand.
 */
public class B132GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BUILDER = new BlockPos(3, 2, 3);
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 6);

	/** A villager stands where a straight stair reaches but the blueprint's outer-corner stair doesn't. */
	//$ gametest_ticks_batch AREA '2400' '"b132CornerStair"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b132CornerStair")
	public void b132AStairPutDownStraightNeverGoesOntoAVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// The blueprint: one oak stair facing north, captured as an outer corner (a stair facing east in front of it).
		BlockPos src = new BlockPos(12, 2, 12);
		BlockState north = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		helper.setBlock(src.north(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		helper.setBlock(src, north);
		BlockState outer = Block.updateFromNeighbourShapes(north, level, helper.absolutePos(src));
		helper.assertTrue(outer.getValue(StairBlock.SHAPE) == StairsShape.OUTER_LEFT || outer.getValue(StairBlock.SHAPE) == StairsShape.OUTER_RIGHT,
			"the blueprint's stair should be an outer corner, is " + outer);
		helper.setBlock(src, outer);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "b132_stair_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(src), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(src, Blocks.AIR);
		helper.setBlock(src.north(), Blocks.AIR);

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		BuildPlan.Step stair = null;
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			if (plan == null || stage == BuildPlan.Stage.DONE) {
				continue;
			}
			for (BuildPlan.Step step : plan.steps(stage)) {
				if (step.state().getBlock() instanceof StairBlock) {
					stair = step;
				}
			}
		}
		if (stair == null) {
			throw new GameTestAssertException("the plan has no stair");
		}
		BlockPos at = stair.pos();
		BlockState planned = stair.state();
		BlockState straight = planned.setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
		// Stand the villager on the stair's bottom half, where the straight stair's top reaches and the corner's doesn't.
		Vec3 spot = null;
		for (double x : new double[] {0.15, 0.85}) {
			Vec3 feet = new Vec3(at.getX() + x, at.getY() + 0.5, at.getZ() + 0.3);
			AABB body = EntityType.VILLAGER.getDimensions().makeBoundingBox(feet).deflate(0.1);
			boolean inCorner = planned.getCollisionShape(level, at).toAabbs().stream().anyMatch(b -> b.move(at).intersects(body));
			boolean inStraight = straight.getCollisionShape(level, at).toAabbs().stream().anyMatch(b -> b.move(at).intersects(body));
			if (!inCorner && inStraight) {
				spot = feet;
			}
		}
		if (spot == null) {
			throw new GameTestAssertException("no place in the stair's block that only the straight stair fills");
		}
		Villager other = helper.spawn(EntityType.VILLAGER, helper.relativePos(at));
		other.setNoAi(true);
		other.setNoGravity(true);
		other.teleportTo(spot.x, spot.y, spot.z);
		Leftovers.after(helper, other::discard);
		String[] problem = {null};
		int[] builtAt = {-1};
		helper.onEachTick(() -> {
			if (!other.isAlive()) {
				return;
			}
			if (problem[0] == null && !level.noCollision(other, other.getBoundingBox().deflate(0.1))) {
				problem[0] = "B132: the villager is inside " + level.getBlockState(at) + " at tick " + helper.getTick() + " (it is at "
					+ String.format("%.2f %.2f %.2f", other.getX() - at.getX(), other.getY() - at.getY(), other.getZ() - at.getZ()) + " from the stair)";
			}
			if (builtAt[0] < 0 && level.getBlockState(at).getBlock() instanceof StairBlock) {
				builtAt[0] = (int) helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(builtAt[0] >= 0, "the stair isn't built yet: stage " + site.stage() + ", " + site.detail());
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: stage " + site.stage() + ", " + site.detail());
		});
	}

	/** A villager a placement left inside a block is moved to room to stand; one beside the block is left where it is. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void b132AVillagerLeftInsideAPlacedBlockIsMovedOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager inside = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		Villager beside = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 2));
		for (Villager v : new Villager[] {inside, beside}) {
			v.setNoAi(true);
			v.setNoGravity(true);
		}
		Vec3 besideAt = helper.absoluteVec(new Vec3(5.5, 2, 2.5));
		beside.teleportTo(besideAt.x, besideAt.y, besideAt.z);
		BlockPos block = helper.absolutePos(new BlockPos(2, 3, 2));
		level.setBlock(block, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		BlockPos next = helper.absolutePos(new BlockPos(4, 2, 2));
		level.setBlock(next, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		helper.assertTrue(BuilderWork.insideBlock(level, inside) != null, "the villager should start inside the stone");
		BuilderWork.freeBystanders(level, block, null);
		BuilderWork.freeBystanders(level, next, null);
		helper.assertTrue(BuilderWork.insideBlock(level, inside) == null, "the villager is still inside "
			+ level.getBlockState(BuilderWork.insideBlock(level, inside) == null ? block : BuilderWork.insideBlock(level, inside)));
		helper.assertTrue(beside.position().distanceTo(besideAt) < 0.01, "the villager beside the block was moved to " + beside.position());
		helper.succeed();
	}

	/**
	 * B132, the city time-lapse's unfinished palisade: a builder takes from the chests only what its work still needs.
	 * Every trip took one block's worth too many (the block it fetched for was counted twice), and two builders sharing a
	 * storehouse stocked with exactly their lists each carried the one log or slab the other waited for, so neither
	 * finished. Here a row of 8 planks, with twice that in the chest: the builder never carries more planks than the
	 * row still needs, and the rest stays in the chest for whoever else builds from it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"b132TakesOnlyWhatItNeeds"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b132TakesOnlyWhatItNeeds")
	public void b132ABuilderTakesOnlyWhatItsWorkNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int row = 8;
		BlockPos src = new BlockPos(2, 2, 12);
		for (int x = 0; x < row; x++) {
			helper.setBlock(src.east(x), Blocks.OAK_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "b132_row_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(src), new Vec3i(row, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < row; x++) {
			helper.setBlock(src.east(x), Blocks.AIR);
		}
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		BlockPos chest = BENCH.south(2);
		helper.setBlock(chest, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BuildPlan plan = site == null ? null : site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		helper.assertTrue(plan.materials().getOrDefault(Items.OAK_PLANKS, 0) == row, "the row lists " + plan.materials());
		// Twice what the build lists, of everything it lists.
		Container box = (Container) helper.getBlockEntity(chest);
		int slot = 0;
		for (var e : plan.materials().entrySet()) {
			for (int left = 2 * e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				box.setItem(slot++, new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		String[] problem = {null};
		int[] most = {0};
		helper.onEachTick(() -> {
			int carried = ModAttachments.BUILDER_BAG.getOrCreate(builder).count(Items.OAK_PLANKS);
			most[0] = Math.max(most[0], carried);
			int needed = 0;
			for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
				if (stage == BuildPlan.Stage.DONE) {
					continue;
				}
				for (BuildPlan.Step step : plan.steps(stage)) {
					if (step.state().is(Blocks.OAK_PLANKS) && !level.getBlockState(step.pos()).is(Blocks.OAK_PLANKS)) {
						needed++;
					}
				}
			}
			if (problem[0] == null && BuildSiteManager.get(level).get(site.id()) != null && carried > needed) {
				problem[0] = "B132: at tick " + helper.getTick() + " the builder carries " + carried + " oak planks, but the row still needs "
					+ needed + ": it took more than its work needs, which another builder sharing the chest would wait for";
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problem[0] == null, problem[0] == null ? "" : problem[0]);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: stage " + site.stage() + ", " + site.detail());
			helper.assertTrue(most[0] > 0, "the builder never carried a plank");
			int left = 0;
			for (int i = 0; i < box.getContainerSize(); i++) {
				left += box.getItem(i).is(Items.OAK_PLANKS) ? box.getItem(i).getCount() : 0;
			}
			helper.assertTrue(left == row, "the chest holds " + left + " oak planks after the row, not the " + row + " the row left");
		});
	}
	/**
	 * B132 (the soak's "item_frame -1"): an item frame a finished build puts up is paid for from the chest, and the
	 * soak's ledger counts it as built in, as it does a block; it was taken from the chest uncounted, so the soak called
	 * it lost. Its own batch: the ledger counts the whole server.
	 */
	//$ gametest_ticks_batch AREA '100' '"b132FrameLedger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b132FrameLedger")
	public void b132AnItemFramePutUpIsCountedAsBuiltIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(4, 2, 5), Blocks.STONE); // the wall the frame hangs on (south of it)
		helper.setBlock(BENCH, Blocks.CHEST);
		Container chest = helper.getBlockEntity(BENCH);
		chest.setItem(0, new ItemStack(Items.ITEM_FRAME));
		net.minecraft.nbt.CompoundTag frame = new net.minecraft.nbt.CompoundTag();
		frame.putString("id", "minecraft:item_frame");
		frame.putByte("Facing", (byte) Direction.NORTH.get3DDataValue());
		io.github.jcondedata.aliveworkplace.blueprint.Blueprint blueprint = new io.github.jcondedata.aliveworkplace.blueprint.Blueprint(
			io.github.jcondedata.aliveworkplace.AliveWorkplace.id("test/b132_frame"), new Vec3i(1, 1, 1), java.util.List.of(),
			java.util.List.of(new io.github.jcondedata.aliveworkplace.blueprint.Blueprint.EntityEntry(new Vec3(0.5, 0.5, 0.97), frame)));
		BuildPlan plan = BuildPlan.create(blueprint, new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(4, 2, 4)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(plan.entities().size() == 1, "the plan has no item frame to put up: " + plan.entities());
		io.github.jcondedata.aliveworkplace.build.MaterialLedger.start();
		int left;
		java.util.Map<net.minecraft.world.item.Item, Integer> used;
		try {
			left = io.github.jcondedata.aliveworkplace.build.BuildEntities.placeAll(level, plan, java.util.List.of(helper.absolutePos(BENCH)));
		} finally {
			io.github.jcondedata.aliveworkplace.build.MaterialLedger.stop();
			used = new java.util.HashMap<>(io.github.jcondedata.aliveworkplace.build.MaterialLedger.used());
		}
		helper.assertTrue(left == 0, left + " entities left out");
		helper.assertTrue(chest.getItem(0).isEmpty(), "the item frame is still in the chest: " + chest.getItem(0));
		helper.assertTrue(!level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
			new AABB(helper.absolutePos(new BlockPos(4, 2, 4))).inflate(1)).isEmpty(), "no item frame was put up");
		helper.assertTrue(used.getOrDefault(Items.ITEM_FRAME, 0) == 1, "the ledger didn't count the item frame built in: " + used);
		helper.succeed();
	}
}
