package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane E (2026-11-22), B1410 from its spec, without Create: a player's own fountain whose water source has a dry stair
 * (and a dry slab) two cells away on the same level. Expected: "water drawn as running runs, as in the player's original;
 * only water that would actually flood a dry neighbor stays still". The runs side: a raised basin's source spilling over
 * its open front into a lower basin, built straight, turned and across a save and load. The still side: a stair with no
 * water beside it that two sources would reach (vanilla's two-source rule) stays dry.
 */
public class QaWaterRuns1122eGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos ORIGINAL = new BlockPos(20, 2, 20);
	private static final Vec3i SIZE = new Vec3i(5, 4, 5);
	/** The raised basin's source, its open front (over the drop) and the lower basin's floor spot under that. */
	private static final BlockPos SOURCE = new BlockPos(2, 3, 1);
	private static final BlockPos FRONT = new BlockPos(2, 3, 2);
	private static final BlockPos FALL = new BlockPos(2, 2, 2);
	private static final BlockPos POOL = new BlockPos(2, 1, 2);
	/** Dry waterloggables two cells from the source: a stair straight along the rim, a slab diagonally. */
	private static final BlockPos STAIR = new BlockPos(0, 3, 1);
	private static final BlockPos SLAB = new BlockPos(3, 3, 0);

	/** The player's fountain, 5 x 4 x 5: a stone floor, a solid wall 2 deep under the raised basin, a lower basin. */
	private static Map<BlockPos, BlockState> fountain() {
		Map<BlockPos, BlockState> out = new java.util.HashMap<>();
		BlockState stone = Blocks.STONE.defaultBlockState();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				out.put(new BlockPos(x, 0, z), stone);
				for (int y = 1; y <= 3; y++) {
					out.put(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z <= 1; z++) {
				out.put(new BlockPos(x, 1, z), stone);
				out.put(new BlockPos(x, 2, z), stone);
			}
			out.put(new BlockPos(x, 3, 0), stone); // the raised basin's back rim
			out.put(new BlockPos(x, 1, 4), stone); // the lower basin's front wall
		}
		for (int z = 2; z <= 3; z++) { // the lower basin's side walls
			out.put(new BlockPos(0, 1, z), stone);
			out.put(new BlockPos(4, 1, z), stone);
		}
		out.put(new BlockPos(1, 3, 1), stone);
		out.put(new BlockPos(3, 3, 1), stone);
		out.put(new BlockPos(4, 3, 1), stone);
		out.put(STAIR, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		out.put(SLAB, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		return out;
	}

	private record Run(ServerLevel level, BuildSite site, BuildPlan plan, BlueprintData.Placement placement, Villager[] builder) {
		BlockPos world(BlockPos local) {
			return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO));
		}
	}

	/**
	 * Stands the player's fountain up (its water running), saves it with a Structure Block's own call once the water has
	 * run, takes it away (no water left), and has a builder build it at {@code origin} turned by {@code rotation} from
	 * barrels holding exactly its materials. {@code started} gets the run on the tick the build starts.
	 */
	private static void start(GameTestHelper helper, String name, BlockPos origin, Rotation rotation, java.util.function.Consumer<Run> started) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		fountain().forEach((p, s) -> helper.setBlock(ORIGINAL.offset(p), s));
		helper.setBlock(ORIGINAL.offset(SOURCE), Blocks.WATER);
		helper.runAfterDelay(60, () -> {
			// setup: as the player stood it, the water spilled over the front into the lower basin; nothing waterlogged
			helper.assertTrue(!helper.getBlockState(ORIGINAL.offset(POOL)).getFluidState().isEmpty(), "setup: the player's own fountain doesn't run");
			helper.assertTrue(!helper.getBlockState(ORIGINAL.offset(STAIR)).getValue(BlockStateProperties.WATERLOGGED)
				&& !helper.getBlockState(ORIGINAL.offset(SLAB)).getValue(BlockStateProperties.WATERLOGGED), "setup: the stair or slab got waterlogged");
			ResourceLocation id = AliveWorkplace.id("test/qa1122e_" + name + "_" + Long.toHexString(level.getGameTime()));
			StructureTemplate template = level.getStructureManager().getOrCreate(id);
			BlockPos corner = helper.absolutePos(ORIGINAL);
			template.fillFromWorld(level, corner, SIZE, false, Blocks.STRUCTURE_VOID);
			for (int y = SIZE.getY() - 1; y >= 0; y--) {
				for (int x = 0; x < SIZE.getX(); x++) {
					for (int z = 0; z < SIZE.getZ(); z++) {
						level.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
					}
				}
			}
			level.getFluidTicks().clearArea(BoundingBox.fromCorners(corner, corner.offset(SIZE.getX() - 1, SIZE.getY() - 1, SIZE.getZ() - 1)));
			helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
			Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
			Builders.employ(level, builder, helper.absolutePos(BENCH));
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), rotation, Mirror.NONE);
			BuildSite site = Builders.start(level, builder, null, id, placement);
			helper.assertTrue(site != null && site.plan(level) != null, "the fountain wasn't found as a blueprint");
			BuildPlan plan = site.plan(level);
			stock(helper, plan);
			started.accept(new Run(level, site, plan, placement, new Villager[] {builder}));
		});
	}

	private static void stock(GameTestHelper helper, BuildPlan plan) {
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i, 2, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}

	/** Done, and the water runs as in the player's original: over the front, down and into the lower basin; nothing waterlogged, nothing spilled. */
	private static void assertRuns(GameTestHelper helper, Run run, long[] doneAt) {
		helper.assertTrue(BuildSiteManager.get(run.level).get(run.site.id()) == null,
			"still building: " + run.site.stage() + " " + run.site.status() + " missing " + run.site.missing());
		if (doneAt[0] < 0) {
			doneAt[0] = helper.getTick();
		}
		helper.assertTrue(helper.getTick() >= doneAt[0] + 40, "watching the finished fountain a while");
		ServerLevel level = run.level;
		helper.assertTrue(level.getFluidState(run.world(SOURCE)).isSource(), "no water poured in the raised basin: " + level.getBlockState(run.world(SOURCE)));
		for (BlockPos p : List.of(FRONT, FALL, POOL)) {
			helper.assertTrue(!level.getFluidState(run.world(p)).isEmpty(), "the poured water never ran: at " + p.toShortString() + " is "
				+ level.getBlockState(run.world(p)) + " (source tick pending: " + level.getFluidTicks().hasScheduledTick(run.world(SOURCE),
				level.getFluidState(run.world(SOURCE)).getType()) + ")");
		}
		for (BlockPos p : List.of(STAIR, SLAB)) {
			BlockState s = level.getBlockState(run.world(p));
			helper.assertTrue(s.hasProperty(BlockStateProperties.WATERLOGGED) && !s.getValue(BlockStateProperties.WATERLOGGED),
				"the dry block at " + p.toShortString() + " is " + s);
		}
		// nothing ran out of the fountain
		for (int x = -2; x <= 6; x++) {
			for (int z = -2; z <= 6; z++) {
				for (int y = 0; y <= 4; y++) {
					if (x >= 0 && x < 5 && z >= 0 && z < 5 && y < 4) {
						continue;
					}
					BlockPos w = run.world(new BlockPos(x, y, z));
					helper.assertTrue(level.getFluidState(w).isEmpty(), "water ran out of the fountain at " + new BlockPos(x, y, z).toShortString());
				}
			}
		}
	}

	/** The fountain built as it stands: when it's done the water spills over the front into the lower basin. */
	//$ gametest_ticks_batch HUGE '6000' '"qa1122eFountain"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1122eFountain")
	public void aPlayersFountainWithAStairTwoFromItsSourceRuns(GameTestHelper helper) {
		Run[] run = {null};
		long[] doneAt = {-1};
		start(helper, "straight", new BlockPos(8, 2, 8), Rotation.NONE, r -> run[0] = r);
		helper.succeedWhen(() -> {
			helper.assertTrue(run[0] != null, "the build hasn't started");
			assertRuns(helper, run[0], doneAt);
		});
	}

	/** The same fountain built turned a quarter: the stair and the slab turn with it, and the water still runs. */
	//$ gametest_ticks_batch HUGE '6000' '"qa1122eFountainTurned"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1122eFountainTurned")
	public void aPlayersFountainTurnedAQuarterStillRuns(GameTestHelper helper) {
		Run[] run = {null};
		long[] doneAt = {-1};
		start(helper, "turned", new BlockPos(14, 2, 8), Rotation.CLOCKWISE_90, r -> run[0] = r);
		helper.succeedWhen(() -> {
			helper.assertTrue(run[0] != null, "the build hasn't started");
			assertRuns(helper, run[0], doneAt);
		});
	}

	/**
	 * The world is saved and loaded the tick the builder has poured the source (the site and the builder from their
	 * saves): the build finishes and the water runs.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qa1122eFountainReload"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1122eFountainReload")
	public void aPlayersFountainSavedAndLoadedAsItsWaterIsPouredRuns(GameTestHelper helper) {
		Run[] run = {null};
		long[] doneAt = {-1};
		boolean[] reloaded = {false};
		start(helper, "reload", new BlockPos(8, 2, 8), Rotation.NONE, r -> run[0] = r);
		helper.onEachTick(() -> {
			if (run[0] == null || reloaded[0] || BuildSiteManager.get(run[0].level).get(run[0].site.id()) == null) {
				return;
			}
			if (run[0].level.getFluidState(run[0].world(SOURCE)).isSource()) {
				reloaded[0] = true;
				ServerLevel level = run[0].level;
				UUID id = run[0].site.id();
				BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
				level.getDataStorage().set(BuildSiteManager.NAME, loaded);
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(run[0].builder[0].save(tag), "the builder wasn't saved");
				run[0].builder[0].discard();
				Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
				level.addFreshEntity(back);
				run[0].builder[0] = (Villager) back;
				helper.assertTrue(BuildSiteManager.get(level).get(id) != null, "the site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(run[0] != null, "the build hasn't started");
			helper.assertTrue(reloaded[0], "the source was never seen poured mid-build (nothing reloaded)");
			assertRuns(helper, run[0], doneAt);
		});
	}

	// ---- the still side: a stair two sources would reach ---------------------------------------------------------------

	/** 5 x 2 x 5 drawn quietly: a stone floor; three sources on a diagonal, two drawn-air spots and a dry stair past them. */
	private static final List<BlockPos> DIAGONAL = List.of(new BlockPos(1, 1, 3), new BlockPos(2, 1, 2), new BlockPos(3, 1, 1));
	private static final List<BlockPos> GROWS = List.of(new BlockPos(2, 1, 3), new BlockPos(3, 1, 2));
	private static final BlockPos FLOODABLE = new BlockPos(3, 1, 3);

	private static BlockState still(BlockPos p) {
		if (p.getY() == 0) {
			return Blocks.STONE.defaultBlockState();
		}
		if (DIAGONAL.contains(p)) {
			return Blocks.WATER.defaultBlockState();
		}
		if (GROWS.contains(p)) {
			return Blocks.AIR.defaultBlockState();
		}
		return p.equals(FLOODABLE) ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST)
			: Blocks.STONE.defaultBlockState();
	}

	/**
	 * Each drawn-air spot has two sources beside it over the stone floor, so in vanilla it would become a source, and the
	 * stair (no water directly beside it, every source two cells off) would then have two sources beside it and fill. The
	 * builder must keep it as drawn: the stair dry, nothing spilled, and so a while after the builder is done.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1122eFloodable"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1122eFloodable")
	public void aStairTwoSourcesWouldReachIsStillBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = AliveWorkplace.id("test/qa1122e_floodable_" + Long.toHexString(level.getGameTime()));
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		BlockPos corner = helper.absolutePos(ORIGINAL);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			level.setBlock(corner.offset(p), still(p), quiet);
		}
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, corner, new Vec3i(5, 2, 5), false, null);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			level.setBlock(corner.offset(p), Blocks.AIR.defaultBlockState(), quiet);
		}
		level.getFluidTicks().clearArea(BoundingBox.fromCorners(corner, corner.offset(4, 1, 4)));
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlockPos origin = new BlockPos(8, 2, 8);
		BuildSite site = Builders.start(level, builder, null, id,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null && site.plan(level) != null, "the drawing wasn't found as a blueprint");
		stock(helper, site.plan(level));
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing());
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			BlockState stair = helper.getBlockState(origin.offset(FLOODABLE));
			helper.assertTrue(stair.is(Blocks.STONE_BRICK_STAIRS) && !stair.getValue(BlockStateProperties.WATERLOGGED), "the stair is " + stair + ", drawn dry");
			for (BlockPos p : DIAGONAL) {
				helper.assertTrue(helper.getBlockState(origin.offset(p)).getFluidState().isSource(), "no source at " + p.toShortString());
			}
			for (int x = -2; x <= 6; x++) {
				for (int z = -2; z <= 6; z++) {
					for (int y = 0; y <= 2; y++) {
						boolean inside = x >= 0 && x <= 4 && z >= 0 && z <= 4 && y <= 1;
						if (!inside) {
							helper.assertTrue(helper.getBlockState(origin.offset(x, y, z)).getFluidState().isEmpty(), "water ran out at " + x + " " + y + " " + z);
						}
					}
				}
			}
			helper.assertTrue(helper.getTick() >= doneAt[0] + 60, "watching the finished build a while");
		});
	}
}
