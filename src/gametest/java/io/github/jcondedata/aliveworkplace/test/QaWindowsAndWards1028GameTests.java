package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;

/**
 * QA (qa-lane-b-1028-0037) for B640 (the Terrace's front panes didn't join), B648 (every window's glass meets a jamb at
 * both ends, in every drawing; the check of every drawing is in QaWindowsAndWards1028BugTests) and B639 (the Clinic's ward has real beds and a closed ridge), from their specs:
 * <ul>
 * <li>a builder puts up a Townhouse (the other window-heavy home), saved and loaded back mid-STRUCTURE while its glass
 * goes in: every pane, bar, fence and wall is joined as the game joins it, and no glass ends in the air;</li>
 * <li>a builder puts up the Clinic: the ward's beds stand whole in the world (head and foot, the same facing), no cot of
 * slab and carpet, and the ridge is closed as built.</li>
 * </ul>
 */
public class QaWindowsAndWards1028GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	/** The ends B648 leaves as drawn, with their reason. */
	static boolean allowed(String path, BlockPos pos) {
		// The village print shop's lamp arm ends over the lantern it holds (B648GameTests.ALLOWED, check.py)
		return path.endsWith("_print_shop") && pos.equals(new BlockPos(6, 5, 0));
	}

	static boolean glass(BlockState state) {
		return state.getBlock() instanceof IronBarsBlock;
	}

	/** A block getter over a drawing's blocks, air elsewhere: what the drawing's glass stands next to once placed. */
	static BlockGetter over(Map<BlockPos, BlockState> blocks) {
		return new BlockGetter() {
			@Override
			public BlockEntity getBlockEntity(BlockPos pos) {
				return null;
			}

			@Override
			public BlockState getBlockState(BlockPos pos) {
				return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
			}

			@Override
			public FluidState getFluidState(BlockPos pos) {
				return getBlockState(pos).getFluidState();
			}

			@Override
			public int getHeight() {
				return 384;
			}

			@Override
			public int getMinBuildHeight() {
				return -64;
			}
		};
	}

	/** Which sides of the glass at pos the game joins, from its real neighbours (IronBarsBlock's own placement rule). */
	static boolean[] joins(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		IronBarsBlock bars = (IronBarsBlock) state.getBlock();
		boolean[] out = new boolean[4];
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState n = level.getBlockState(pos.relative(d));
			out[d.get2DDataValue()] = bars.attachsTo(n, n.isFaceSturdy(level, pos.relative(d), d.getOpposite()));
		}
		return out;
	}

	/** The glass at pos, joined as the game joins it, with a side against open air: the reason, or null. */
	static String endsInAir(BlockGetter level, BlockPos pos) {
		boolean[] j = joins(level, pos);
		int joined = 0;
		for (boolean b : j) {
			joined += b ? 1 : 0;
		}
		// The end of a run: joined on one side only (a corner or a T joins two or three, and its faces look out on the
		// air as every window's do)
		for (Direction d : Direction.Plane.HORIZONTAL) {
			boolean open = level.getBlockState(pos.relative(d.getOpposite())).isAir();
			if (joined == 1 && j[d.get2DDataValue()] && open) {
				return "joins to the " + d.getName() + ", open air to the " + d.getOpposite().getName();
			}
		}
		if (joined == 0) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (level.getBlockState(pos.relative(d)).isAir()) {
					return "a lone post joined to nothing, open air to the " + d.getName();
				}
			}
		}
		return null;
	}

	/**
	 * A builder puts up a Townhouse (balcony railings and many windows), the site and the builder saved and loaded back
	 * once the STRUCTURE stage has some of its glass in: every pane, bar, fence and wall stands joined as the game joins
	 * it, and no glass in the finished house has a side against open air.
	 */
	//$ gametest_ticks_batch AREA '60000' '"qaTownhouseGlassReload"'
	@GameTest(template = AREA, timeoutTicks = 60000, batch = "qaTownhouseGlassReload")
	public void qaABuiltTownhousesGlassJoinsAfterAReloadMidStructure(GameTestHelper helper) {
		Build b = start(helper, StarterBlueprints.TOWNHOUSE);
		ServerLevel level = helper.getLevel();
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite site = BuildSiteManager.get(level).get(b.id());
			if (reloaded[0] || site == null || site.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			long in = 0;
			for (BlockPos p : BlockPos.betweenClosed(b.box().minX(), b.box().minY(), b.box().minZ(), b.box().maxX(), b.box().maxY(), b.box().maxZ())) {
				if (glass(level.getBlockState(p))) {
					in++;
				}
			}
			if (in >= 2) {
				reloaded[0] = true;
				b.builder()[0] = reload(helper, level, b.builder()[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(b.id()) != null, "the Townhouse's site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saved and loaded while the Townhouse's glass was going in");
			assertFinished(helper, b, "Townhouse");
			int glass = 0;
			List<String> problems = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(b.box().minX(), b.box().minY(), b.box().minZ(), b.box().maxX(), b.box().maxY(), b.box().maxZ())) {
				BlockState state = level.getBlockState(p);
				if (state.getBlock() instanceof CrossCollisionBlock || state.getBlock() instanceof WallBlock) {
					BlockState vanilla = Block.updateFromNeighbourShapes(state, level, p);
					if (vanilla != state) {
						problems.add(state + " at " + p.subtract(b.origin()).toShortString() + " should be " + vanilla);
					}
				}
				if (glass(state)) {
					glass++;
					String why = endsInAir(level, p.immutable());
					if (why != null) {
						problems.add(state.getBlock() + " at " + p.subtract(b.origin()).toShortString() + " " + why);
					}
				}
			}
			LOGGER.info("QA B640/B648: the built Townhouse (reloaded mid-STRUCTURE) has {} panes and bars, {} wrong", glass, problems.size());
			helper.assertTrue(glass >= 6, "only " + glass + " panes and bars in the built Townhouse");
			helper.assertTrue(problems.isEmpty(), "the built Townhouse's glass: " + problems.size() + " wrong, e.g. "
				+ String.join("; ", problems.subList(0, Math.min(5, problems.size()))));
		});
	}

	/**
	 * A builder puts up the Clinic: in the world, every bed in it is whole (a head and a foot, the same facing and
	 * colour), the ward has at least four, no carpet lies on a floor-standing slab, and every cell the drawing closes
	 * under its ridge is a solid block as built.
	 */
	//$ gametest_ticks_batch AREA '60000' '"qaClinicWardBuilt"'
	@GameTest(template = AREA, timeoutTicks = 60000, batch = "qaClinicWardBuilt")
	public void qaABuiltClinicHasWholeBedsAndAClosedRidge(GameTestHelper helper) {
		Build b = start(helper, StarterBlueprints.CLINIC);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> drawing = new HashMap<>();
		BlueprintLibrary.get(level, StarterBlueprints.CLINIC.id()).orElseThrow().blocks().forEach(x -> drawing.put(x.pos(), x.state()));
		List<BlockPos> ridge = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : drawing.entrySet()) {
			BlockState slab = e.getValue();
			if (!(slab.getBlock() instanceof SlabBlock) || slab.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
				continue;
			}
			BlockPos under = e.getKey().below();
			for (Direction up : new Direction[] {Direction.EAST, Direction.SOUTH}) {
				if (climbsTo(drawing.get(under.relative(up.getOpposite())), up) && climbsTo(drawing.get(under.relative(up)), up.getOpposite())) {
					ridge.add(under);
				}
			}
		}
		helper.assertTrue(ridge.size() >= 5, "only " + ridge.size() + " ridge cells in the Clinic's drawing");
		helper.succeedWhen(() -> {
			assertFinished(helper, b, "Clinic");
			int heads = 0;
			List<String> problems = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(b.box().minX(), b.box().minY(), b.box().minZ(), b.box().maxX(), b.box().maxY(), b.box().maxZ())) {
				BlockState state = level.getBlockState(p);
				String rel = p.subtract(b.origin()).toShortString();
				if (state.getBlock() instanceof BedBlock) {
					Direction facing = state.getValue(BedBlock.FACING);
					boolean head = state.getValue(BedBlock.PART) == BedPart.HEAD;
					BlockState other = level.getBlockState(head ? p.relative(facing.getOpposite()) : p.relative(facing));
					if (other.getBlock() != state.getBlock() || other.getValue(BedBlock.FACING) != facing || other.getValue(BedBlock.PART) == state.getValue(BedBlock.PART)) {
						problems.add("half a bed at " + rel + ": " + state + " beside " + other);
					}
					heads += head ? 1 : 0;
				}
				if (state.getBlock() instanceof CarpetBlock) {
					BlockState below = level.getBlockState(p.below());
					if (below.getBlock() instanceof SlabBlock && below.getValue(SlabBlock.TYPE) == SlabType.BOTTOM && !level.getBlockState(p.below(2)).isAir()
						&& level.getBlockState(p.below(2)).isFaceSturdy(level, p.below(2), Direction.UP)) {
						problems.add("carpet on a floor slab (a stand-in cot) at " + rel);
					}
				}
			}
			for (BlockPos r : ridge) {
				BlockState built = level.getBlockState(b.origin().offset(r));
				if (built.isAir()) {
					problems.add("open under the ridge at " + r.toShortString());
				}
			}
			LOGGER.info("QA B639: the built Clinic has {} whole beds, {} ridge cells, {} problems", heads, ridge.size(), problems.size());
			helper.assertTrue(heads >= 4, "B639: the built Clinic has " + heads + " whole beds, wants at least 4 in the ward");
			helper.assertTrue(problems.isEmpty(), "B639: the built Clinic: " + problems.size() + " problems, e.g. "
				+ String.join("; ", problems.subList(0, Math.min(5, problems.size()))));
		});
	}

	private static boolean climbsTo(BlockState state, Direction facing) {
		return state != null && state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM && state.getValue(StairBlock.FACING) == facing;
	}

	private record Build(UUID id, BlockPos origin, BoundingBox box, Villager[] builder) {
	}

	private static Build start(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, entry.id(), placement);
		helper.assertTrue(site != null && site.plan(level) != null, entry.id() + " didn't start");
		return new Build(site.id(), helper.absolutePos(ORIGIN), BlueprintOutline.bounds(placement, entry.size()), new Villager[] {villager});
	}

	private static void assertFinished(GameTestHelper helper, Build b, String what) {
		BuildSite site = BuildSiteManager.get(helper.getLevel()).get(b.id());
		helper.assertTrue(site == null, site == null ? "" : "the " + what + " is still building: " + site.stage() + " " + site.status() + " step "
			+ site.cursor() + ", " + site.placed() + " placed, " + site.detail() + (b.builder()[0].isAlive() ? "" : " (builder dead)"));
	}

	/** As a save and a load of the world would (B640GameTests' way): the build sites and the builder from their saves. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}
}
