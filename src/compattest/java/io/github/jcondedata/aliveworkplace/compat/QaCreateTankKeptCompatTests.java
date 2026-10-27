package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
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
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane E, 2026-10-27) for B486, from its spec and the builder page, not from the code: "a scanned Create fluid tank
 * ... is built empty, keeping its size and windows as drawn", and a block a player changed since "still counts as built"
 * (nothing of theirs is taken back). Two sides of the fix: the emptied tank must still be the tall tank that was drawn
 * (B486 dropped the fluid, not the tank), and lava a player pours into a built tank is theirs: building the same
 * blueprint over it again (a repair) leaves it alone.
 */
public class QaCreateTankKeptCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(20, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);

	/**
	 * Two fluid tanks stacked into one tall tank holding three buckets of lava, scanned and built from its material list:
	 * the built tank holds no lava, and it is still one tall tank, so it takes more lava than a single tank block holds.
	 */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_create_tank_tall"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_create_tank_tall")
	public void aTallTankOfLavaIsBuiltEmptyAndStillTall(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, tank());
		helper.setBlock(SRC.above(), tank());
		BlockPos src = helper.absolutePos(SRC);
		// a sequence step that throws is run again next tick, so each step pours once and the checks come after
		long[] single = {0};
		long[] poured = {-1};
		long[] room = {0};
		helper.startSequence()
			.thenIdle(20)
			.thenExecute(() -> {
				if (poured[0] < 0) {
					single[0] = capacityOf(level, helper.absolutePos(SRC.north(6)));
					poured[0] = insert(level, src, 3 * FluidConstants.BUCKET, false);
					room[0] = insert(level, src, single[0] + FluidConstants.BUCKET, true);
				}
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(poured[0] == 3 * FluidConstants.BUCKET, "the tall tank took " + poured[0] + " droplets, not 3 buckets (setup)");
				helper.assertTrue(room[0] > single[0] - 3 * FluidConstants.BUCKET, "the two scanned tanks didn't join into one tall tank (setup)");
			})
			.thenExecute(() -> {
				ResourceLocation id = scan(level, src, new Vec3i(1, 2, 1), "qa_create_tank_tall");
				helper.setBlock(SRC.above(), Blocks.AIR);
				helper.setBlock(SRC, Blocks.AIR);
				BuildSite site = build(helper, id);
				BlockPos at = helper.absolutePos(ORIGIN);
				helper.succeedWhen(() -> {
					helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
						"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
					helper.assertTrue(level.getBlockState(at).is(tank()) && level.getBlockState(at.above()).is(tank()),
						"the tall tank wasn't built: " + level.getBlockState(at) + " / " + level.getBlockState(at.above()));
					long lava = lava(level, at) + lava(level, at.above());
					helper.assertTrue(lava == 0, "the built tank holds " + lava * 1000 / FluidConstants.BUCKET + " mB of lava for free");
					long space = insert(level, at, single[0] + FluidConstants.BUCKET, true);
					helper.assertTrue(space > single[0], "the built tank takes " + space * 1000 / FluidConstants.BUCKET
						+ " mB, no more than one tank block (" + single[0] * 1000 / FluidConstants.BUCKET + " mB): it was built as two"
						+ " separate tanks, not the tall tank drawn");
				});
			});
	}

	/**
	 * A tank built empty from a blueprint; a player pours a bucket of lava into it; the same blueprint is built again at the
	 * same spot (a repair). The tank still counts as built, so the player's lava stays.
	 */
	//$ gametest_ticks_batch HUGE_AREA '3600' '"qa_create_tank_rebuild"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 3600, batch = "qa_create_tank_rebuild")
	public void aPlayersLavaInABuiltTankStaysWhenItIsBuiltAgain(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, tank());
		helper.setBlock(SRC.east(), Blocks.STONE_BRICKS);
		ResourceLocation id = scan(level, helper.absolutePos(SRC), new Vec3i(2, 1, 1), "qa_create_tank_rebuild");
		helper.setBlock(SRC, Blocks.AIR);
		helper.setBlock(SRC.east(), Blocks.AIR);
		BlockPos at = helper.absolutePos(ORIGIN);
		BuildSite first = build(helper, id);
		BuildSite[] second = {null};
		long[] poured = {-1};
		// a sequence step that throws is run again next tick, so the pouring step runs once and the checks come after
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(BuildSiteManager.get(level).get(first.id()) == null && level.getBlockState(at).is(tank()),
				"the first build isn't done: " + first.stage() + " " + first.status()))
			.thenExecute(() -> {
				if (second[0] == null) {
					poured[0] = insert(level, at, FluidConstants.BUCKET, false);
					// the wall block beside it is knocked out, so the second build has something to repair
					level.setBlockAndUpdate(at.east(), Blocks.AIR.defaultBlockState());
					second[0] = build(helper, id);
				}
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(poured[0] == FluidConstants.BUCKET, "the built tank took " + poured[0] + " droplets of the player's lava, not a bucket");
				helper.assertTrue(BuildSiteManager.get(level).get(second[0].id()) == null,
					"the repair isn't done: " + second[0].stage() + " " + second[0].status() + " missing " + second[0].missing());
			})
			.thenExecute(() -> {
				helper.assertTrue(level.getBlockState(at.east()).is(Blocks.STONE_BRICKS), "the repair didn't put the wall back: " + level.getBlockState(at.east()));
				helper.assertTrue(level.getBlockState(at).is(tank()), "the tank is gone after the repair: " + level.getBlockState(at));
				long lava = lava(level, at);
				helper.assertTrue(lava == FluidConstants.BUCKET, "the player's bucket of lava in the built tank is now "
					+ lava * 1000 / FluidConstants.BUCKET + " mB after the same blueprint was built over it");
			})
			.thenSucceed();
	}

	/**
	 * Create's fluid tank, looked up when a test runs: a static field filled when the class loads can be read before
	 * Create's blocks are registered, and then holds air.
	 */
	private static Block tank() {
		Block tank = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:fluid_tank"));
		if (tank == Blocks.AIR) {
			throw new GameTestAssertException("no create:fluid_tank registered");
		}
		return tank;
	}

	/** Scans the box at {@code from} (absolute) into a new blueprint, with its block data, as the Scan Tool does. */
	private static ResourceLocation scan(ServerLevel level, BlockPos from, Vec3i size, String name) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, from, size, true, Blocks.STRUCTURE_VOID);
		return id;
	}

	/** A real builder at its bench builds {@code id} at ORIGIN from a chest holding exactly its material list. */
	private static BuildSite build(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		if (!level.getBlockState(helper.absolutePos(BENCH)).is(ModBlocks.BUILDERS_BENCH)) {
			helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		}
		Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().filter(Builders::isBuilder).findFirst().orElse(null);
		if (builder == null) {
			builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
			Builders.employ(level, builder, helper.absolutePos(BENCH));
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (!level.getBlockState(helper.absolutePos(CHEST)).is(Blocks.CHEST)) {
			helper.setBlock(CHEST, Blocks.CHEST);
		}
		Container chest = helper.getBlockEntity(CHEST);
		chest.clearContent();
		int slot = 0;
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		return site;
	}

	/** How much one lone tank block holds: a tank placed far off, filled to the brim in a transaction that is undone. */
	private static long capacityOf(ServerLevel level, BlockPos far) {
		level.setBlockAndUpdate(far, tank().defaultBlockState());
		long cap = insert(level, far, 1000 * FluidConstants.BUCKET, true);
		level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
		if (cap <= 0) {
			throw new GameTestAssertException("a lone fluid tank takes no lava (setup)");
		}
		return cap;
	}

	/** Pours up to {@code amount} lava into the block's fluid storage; with {@code simulate} it is undone. How much went in. */
	private static long insert(ServerLevel level, BlockPos pos, long amount, boolean simulate) {
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, Direction.UP);
		if (storage == null) {
			throw new GameTestAssertException("no fluid storage at " + pos + ": " + level.getBlockState(pos));
		}
		try (Transaction tx = Transaction.openOuter()) {
			long in = storage.insert(FluidVariant.of(Fluids.LAVA), amount, tx);
			if (!simulate) {
				tx.commit();
			}
			return in;
		}
	}

	/** How much lava the block holds, read through its fluid storage. */
	private static long lava(ServerLevel level, BlockPos pos) {
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, Direction.UP);
		long total = 0;
		if (storage != null) {
			for (var view : storage.nonEmptyViews()) {
				if (view.getResource().isOf(Fluids.LAVA)) {
					total += view.getAmount();
				}
			}
		}
		return total;
	}
}
