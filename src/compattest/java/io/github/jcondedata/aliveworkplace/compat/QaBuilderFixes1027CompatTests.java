package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (qa-lane-b-1027-0638), B486 from its spec ("a builder copies the fluid a Create block holds in its block data
 * ... honey, chocolate, potions"; builder.md: builders never copy what is inside containers), past the bug's own lava
 * test: a two-block-tall fluid tank holding two buckets of Create's honey and a spout holding a bucket of water, scanned
 * into a blueprint and built by a real builder turned a quarter and mirrored, from exactly its material list. Nothing
 * built holds any fluid unless the list asked for a bucket of it, and the tall tank goes up whole.
 */
public class QaBuilderFixes1027CompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(18, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	/** Local spots in the scan: the tank's two blocks, then the spout two blocks east (a spout pours only downwards, onto nothing). */
	private static final BlockPos TANK_LOW = BlockPos.ZERO;
	private static final BlockPos TANK_HIGH = new BlockPos(0, 1, 0);
	private static final BlockPos SPOUT = new BlockPos(2, 0, 0);

	//$ gametest_ticks_batch HUGE_AREA '3000' '"qa1027_create_fluids"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 3000, batch = "qa1027_create_fluids")
	public void b486ATallTankOfHoneyAndASpoutOfWaterAreBuiltEmptyTurnedAndMirrored(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Block tank = block("create:fluid_tank");
		Block spout = block("create:spout");
		Fluid honey = BuiltInRegistries.FLUID.get(ResourceLocation.parse("create:honey"));
		helper.assertTrue(honey != Fluids.EMPTY, "Create has no honey fluid");
		helper.setBlock(SRC.offset(TANK_LOW), tank);
		helper.setBlock(SRC.offset(TANK_HIGH), tank);
		helper.setBlock(SRC.offset(SPOUT), spout);
		helper.runAfterDelay(10, () -> {
			// the two tank blocks have joined into one tank by now, so two buckets fit through either
			long inTank = fill(level, helper.absolutePos(SRC.offset(TANK_LOW)), honey, 2 * FluidConstants.BUCKET);
			helper.assertTrue(inTank == 2 * FluidConstants.BUCKET, "the tall tank took " + inTank + " droplets of honey, not two buckets");
			long inSpout = fill(level, helper.absolutePos(SRC.offset(SPOUT)), Fluids.WATER, FluidConstants.BUCKET);
			helper.assertTrue(inSpout == FluidConstants.BUCKET, "the spout took " + inSpout + " droplets of water");
		});
		helper.runAfterDelay(15, () -> {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "qa1027_create_fluids_" + Long.toHexString(level.getGameTime()));
			level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(3, 2, 1), true, Blocks.STRUCTURE_VOID);
			for (BlockPos rel : List.of(TANK_HIGH, TANK_LOW, SPOUT)) {
				helper.setBlock(SRC.offset(rel), Blocks.AIR);
			}
			build(helper, level, id);
		});
	}

	private static void build(GameTestHelper helper, ServerLevel level, ResourceLocation id) {
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		Rotation turn = Rotation.CLOCKWISE_90;
		Mirror mirror = Mirror.LEFT_RIGHT;
		BlockPos corner = helper.absolutePos(ORIGIN);
		BoundingBox box = BuildPlan.create(blueprint, new BlueprintData.Placement(level.dimension().location(), corner, turn, mirror)).bounds();
		BlockPos origin = corner.offset(corner.getX() - box.minX(), 0, corner.getZ() - box.minZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, turn, mirror);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		Map<Item, Integer> asked = site.plan(level).materials();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		helper.succeedWhen(() -> {
			try {
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
				List<String> wrong = new ArrayList<>();
				for (BlockPos rel : List.of(TANK_LOW, TANK_HIGH, SPOUT)) {
					BlockPos at = origin.offset(StructureTemplate.transform(rel, mirror, turn, BlockPos.ZERO));
					Block want = rel.equals(SPOUT) ? block("create:spout") : block("create:fluid_tank");
					if (!level.getBlockState(at).is(want)) {
						wrong.add("no " + want + " built at " + at + ": " + level.getBlockState(at));
						continue;
					}
					Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, at, Direction.UP);
					if (storage == null) {
						wrong.add(want + " at " + at + " has no fluid storage (not whole?)");
						continue;
					}
					for (var view : storage.nonEmptyViews()) {
						Item bucket = view.getResource().getFluid().getBucket();
						if (view.getAmount() > 0 && !asked.containsKey(bucket)) {
							wrong.add(want + " holding " + (view.getAmount() * 1000 / FluidConstants.BUCKET) + " mB of "
								+ BuiltInRegistries.FLUID.getKey(view.getResource().getFluid()));
						}
					}
				}
				helper.assertTrue(wrong.isEmpty(), "from the material list " + list + ": " + wrong);
			} finally {
				if (BuildSiteManager.get(level).get(site.id()) == null) {
					level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
					level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
					level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
				}
			}
		});
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	/** Pours {@code amount} of {@code fluid} into the block at {@code pos} through its fluid storage; how many droplets it took. */
	private static long fill(ServerLevel level, BlockPos pos, Fluid fluid, long amount) {
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, Direction.UP);
		if (storage == null) {
			throw new GameTestAssertException("no fluid storage at " + pos + ": " + level.getBlockState(pos));
		}
		try (Transaction tx = Transaction.openOuter()) {
			long in = storage.insert(FluidVariant.of(fluid), amount, tx);
			tx.commit();
			return in;
		}
	}
}
