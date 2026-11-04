package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.BENCH;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ORIGIN;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.TALL_AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.VILLAGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertBuilt;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertNoFluidInBasement;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.cottageFour;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.fill;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.oldBuild;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.rules;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.stacks;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.wet;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Extent;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Setup;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b, 2026-11-04, round f), from the spec in docs/wiki/basements.md and cellars.md after the fix of B923/B924
 * ({@code BuilderWork.sealFluids}): a builder digging a basement or a cellar next to water or lava in the earth finishes
 * the build with no water, lava or fire in the basement, alive, and the house unburnt. Edge cases the first QA round
 * didn't cover: water and lava pockets inside the room's own volume, a pond at ground level beside the basement, a
 * server restart mid-dig, and a Smithy cellar dug beside lava. Also two edges of the 32-scaffold column (B929).
 * Setup and helpers are {@link QaBasementGround1104GameTests}'s; failing edges are in {@link QaBasementFluids1104fBugTests}.
 */
public class QaBasementFluids1104fGameTests implements FabricGameTest {
	/** A spot in the middle of the Cottage IV's basement room (relative to the origin), drawn as air (5, -2, 5 is a table). */
	static final BlockPos ROOM_MIDDLE = new BlockPos(6, -2, 5);

	// ------------------------------------------------------------------------------------------ pockets in the room

	/**
	 * A water source in the middle of the basement's room at y -2 (a spot the drawing wants air), in earth otherwise
	 * dry: the dig reaches the earth beside it before the pocket. When done, no water is in the basement, the builder
	 * is alive and the build is as drawn.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaFluidsRoomWater"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaFluidsRoomWater")
	public void aWaterPocketInsideTheBasementRoomLeavesItDry(GameTestHelper helper) {
		pocketInTheRoom(helper, Blocks.WATER);
	}

	/**
	 * A lava source in the middle of the basement's room at y -2, fire spread on: when done, no lava or fire in or round
	 * the cottage, the builder alive and not burning, and the whole build as drawn.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaFluidsRoomLava"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaFluidsRoomLava")
	public void aLavaPocketInsideTheBasementRoomBurnsNothing(GameTestHelper helper) {
		fireTick(helper);
		pocketInTheRoom(helper, Blocks.LAVA);
	}

	static void pocketInTheRoom(GameTestHelper helper, net.minecraft.world.level.block.Block fluid) {
		BlockPos at = ORIGIN.offset(ROOM_MIDDLE);
		Setup s = cottageFour(helper, b -> {
			BlockState drawn = b.blocks().stream().filter(e -> e.pos().equals(ROOM_MIDDLE)).map(Blueprint.Entry::state).findFirst().orElse(null);
			helper.assertTrue(drawn != null && drawn.isAir(), "setup: " + ROOM_MIDDLE.toShortString() + " isn't a spot of the room drawn as air: " + drawn);
			helper.setBlock(at, fluid);
		});
		helper.assertTrue(s.level().getBlockState(helper.absolutePos(at)).is(fluid), "setup: no " + fluid + " in the room's middle");
		int[] firstFlow = {-1};
		int[] pocketGone = {-1};
		String[] pocketBecame = {"?"};
		helper.onEachTick(() -> {
			if (firstFlow[0] < 0) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (!s.level().getFluidState(helper.absolutePos(at.relative(d))).isEmpty()) {
						firstFlow[0] = (int) helper.getTick();
					}
				}
			}
			if (pocketGone[0] < 0 && !helper.getBlockState(at).is(fluid)) {
				pocketGone[0] = (int) helper.getTick();
				pocketBecame[0] = helper.getBlockState(at).getBlock().getName().getString();
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int checked = assertNoFluidInBasement(helper, s);
			noFire(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			helper.assertFalse(s.villager().isOnFire(), "the builder is burning at " + helper.relativePos(s.villager().blockPosition()));
			helper.assertTrue(pocketGone[0] >= 0, "the pocket was never touched");
			LOGGER.info("qa fluids room {}: built at tick {}, pocket became {} at tick {}, ran beside it at tick {}, {} basement spots dry, the room's middle now {}",
				fluid, helper.getTick(), pocketBecame[0], pocketGone[0], firstFlow[0], checked, helper.getBlockState(at));
		});
	}

	// ------------------------------------------------------------------------------------------------- ground pond

	/**
	 * A pond at the top of the ground (y -1) along the whole west side of the basement, just outside its wall (air
	 * over it): when done, no water is in the basement, the builder is alive and the build is as drawn.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaFluidsPond"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaFluidsPond")
	public void aPondAtGroundLevelBesideTheBasementLeavesItDry(GameTestHelper helper) {
		List<BlockPos> pond = new ArrayList<>();
		Setup s = cottageFour(helper, b -> {
			Extent ex = Extent.of(b);
			java.util.Set<BlockPos> drawn = new java.util.HashSet<>();
			b.blocks().forEach(e -> drawn.add(e.pos()));
			for (int z = ex.minZ(); z <= ex.maxZ(); z++) {
				BlockPos rel = new BlockPos(ex.minX() - 1, -1, z);
				helper.assertFalse(drawn.contains(rel), "setup: " + rel.toShortString() + " is drawn");
				helper.setBlock(ORIGIN.offset(rel), Blocks.WATER);
				pond.add(rel);
			}
		});
		for (BlockPos rel : pond) {
			helper.assertTrue(helper.getBlockState(ORIGIN.offset(rel)).is(Blocks.WATER), "setup: the pond isn't water at " + rel.toShortString());
		}
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa fluids pond: built at tick {}, the pond now {}", helper.getTick(),
				pond.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).getBlock().getName().getString()).toList());
		});
	}

	// ------------------------------------------------------------------------------------------------ restart mid-dig

	/**
	 * Water sources beside the basement (the B923 spots), and the build sites are saved and loaded from NBT
	 * mid-CLEAR, once the dig has reached the second layer under the ground: the build still finishes with
	 * no water in the basement and the builder alive.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaFluidsRestart"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaFluidsRestart")
	public void waterBesideTheBasementWithARestartMidDigLeavesItDry(GameTestHelper helper) {
		List<BlockPos> wet = new ArrayList<>();
		Setup s = cottageFour(helper, b -> wet.addAll(wet(helper, b, Blocks.WATER)));
		ServerLevel level = s.level();
		int[] restartedAt = {-1};
		BlockPos dugProbe = s.site().placement().origin().offset(ROOM_MIDDLE);
		helper.onEachTick(() -> {
			if (restartedAt[0] < 0) {
				BuildSite live = BuildSiteManager.get(level).get(s.site().id());
				if (live != null && live.stage() == BuildPlan.Stage.CLEAR && level.getBlockState(dugProbe).isAir()) {
					restart(helper);
					restartedAt[0] = (int) helper.getTick();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restartedAt[0] >= 0, "the server never restarted mid-CLEAR (the room's middle at y -2 never dug)");
			assertBuilt(helper, s);
			assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa fluids restart: restarted at tick {}, built at tick {}, beside the basement now {}", restartedAt[0], helper.getTick(),
				wet.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).getBlock().getName().getString()).toList());
		});
	}

	/**
	 * A server restart for the build: every build site saved and loaded from its NBT (as QaFurnishedMidJob1102GameTests
	 * does), so the dig carries on from the saved site. The builder stays the same entity.
	 */
	static void restart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}

	// --------------------------------------------------------------------------------------------- Smithy cellar lava

	/**
	 * A Smithy finished before cellars, lava in the earth beside its coal store and under it, fire spread on; its
	 * builder's "Dig the cellar" job: when done, no lava or fire in or round the Smithy, the builder alive and not
	 * burning, and the whole Smithy as drawn.
	 */
	//$ gametest_ticks_batch TALL_AREA '20000' '"qaFluidsCellarLava"'
	@GameTest(template = TALL_AREA, timeoutTicks = 20000, batch = "qaFluidsCellarLava")
	public void lavaInTheEarthBesideASmithysCellarDigBurnsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		fireTick(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		List<BlockPos> hot = wet(helper, blueprint, Blocks.LAVA);
		for (BlockPos rel : hot) {
			helper.assertTrue(helper.getBlockState(ORIGIN.offset(rel)).is(Blocks.LAVA), "setup: no lava at " + rel.toShortString());
		}
		rules(helper);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		Cellars.offer(level, villager);
		MerchantOffer trade = villager.getOffers().stream().filter(o -> BlueprintItem.data(o.getResult())
				.map(d -> d.structure().equals(id) && d.placement().equals(Optional.of(placement))).orElse(false))
			.findFirst().orElseThrow(() -> new GameTestAssertException("setup: no \"Dig the cellar\" trade for the old Smithy"));
		List<ItemStack> stock = new ArrayList<>();
		BuildPlan.create(Cellars.cellarOnly(blueprint), placement).materials().forEach((item, count) -> stock.addAll(stacks(item, count)));
		fill(helper, stock);
		BlueprintData data = BlueprintItem.data(trade.getResult()).orElseThrow();
		BuildSite site = Builders.start(level, villager, null, data.structure(), data.placement().orElseThrow());
		helper.assertTrue(site.isCellarOnly(), "setup: the job's blueprint didn't start a cellar dig");
		Setup s = new Setup(level, villager, site, site.plan(level), blueprint);
		watchDeath(helper, s);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			assertNoFluidInBasement(helper, s);
			noFire(helper, new Setup(level, villager, site, BuildPlan.create(blueprint, placement), blueprint));
			List<BlockPos> wrong = BuildPlan.create(blueprint, placement).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the whole Smithy wrong after the dig, e.g. "
				+ wrong.stream().limit(4).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(villager.isAlive(), "the builder died");
			helper.assertFalse(villager.isOnFire(), "the builder is burning at " + helper.relativePos(villager.blockPosition()));
			LOGGER.info("qa fluids cellar lava: dug at tick {}, beside the cellar now {}", helper.getTick(),
				hot.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).getBlock().getName().getString()).toList());
		});
	}

	// ------------------------------------------------------------------------------------------- scaffolds (B929)

	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos SHAFT = new BlockPos(6, 1, 6);
	private static final Method COLUMN;
	private static final Method CELLS;
	private static final Method STAND;

	static {
		try {
			COLUMN = Scaffolds.class.getDeclaredMethod("column", ServerLevel.class, BuildPlan.class, BlockPos.class, BlockPos.class, double.class);
			COLUMN.setAccessible(true);
			Class<?> column = Class.forName("io.github.jcondedata.aliveworkplace.build.Scaffolds$Column");
			CELLS = column.getDeclaredMethod("cells");
			STAND = column.getDeclaredMethod("stand");
			CELLS.setAccessible(true);
			STAND.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Scaffolds.column has changed shape: update this test", e);
		}
	}

	/**
	 * A layer of {@code base} one over the shaft's floor, under every place the builder could stand, and a block
	 * {@code 1 + n + 6} over the floor: the column planned for it as {cells, stand y over the floor}, or null.
	 */
	static int[] plannedOn(GameTestHelper helper, BlockState base, int n) {
		ServerLevel level = helper.getLevel();
		BlockPos floor = helper.absolutePos(SHAFT);
		helper.assertTrue(level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP), "setup: the floor under the shaft isn't solid");
		BlockPos target = floor.above(1 + n + 6);
		for (int y = 1; y <= n + 9; y++) {
			for (int dx = -5; dx <= 5; dx++) {
				for (int dz = -5; dz <= 5; dz++) {
					BlockPos p = floor.offset(dx, y, dz);
					BlockState want = y == 1 ? base : Blocks.AIR.defaultBlockState();
					if (level.getBlockState(p) != want) {
						level.setBlock(p, want, 2);
					}
				}
			}
		}
		helper.assertTrue(level.getBlockState(floor.above()) == base, "setup: the base layer isn't " + base);
		Blueprint pillar = BlueprintLibrary.get(level, PILLAR).orElseThrow(() -> new GameTestAssertException("no blueprint " + PILLAR));
		BuildPlan plan = BuildPlan.create(pillar, new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(20, 2, 20)), Rotation.NONE, Mirror.NONE));
		try {
			Object column = COLUMN.invoke(null, level, plan, target, floor.above(2), 4.5);
			if (column == null) {
				return null;
			}
			List<?> cells = (List<?>) CELLS.invoke(column);
			BlockPos stand = (BlockPos) STAND.invoke(column);
			return new int[]{cells.size(), stand.getY() - floor.getY()};
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Scaffolding already standing one over the floor everywhere round the shaft (a column already standing, per the
	 * page), a block 39 over the floor: a column of exactly 32 new scaffolds on the old ones, the builder 34 up. (On the
	 * bare floor it would need 33 and get none: QaScaffoldHeight1104GameTests.)
	 */
	//$ gametest 'HUGE'
	@GameTest(template = HUGE)
	public void aColumnOfThirtyTwoOnStandingScaffoldingIsPlanned(GameTestHelper helper) {
		int[] c = plannedOn(helper, Blocks.SCAFFOLDING.defaultBlockState().setValue(BlockStateProperties.STABILITY_DISTANCE, 0), 32);
		helper.assertTrue(c != null, "no column planned on standing scaffolding for a block 39 over the floor (32 new scaffolds reach it)");
		helper.assertTrue(c[0] == 32 && c[1] == 34, "expected 32 scaffolds standing 34 up, seen " + c[0] + " scaffolds, standing " + c[1] + " up");
		helper.succeed();
	}

	/** The same on top slabs (a full, sturdy top): a column of exactly 32. */
	//$ gametest 'HUGE'
	@GameTest(template = HUGE)
	public void aColumnOfThirtyTwoOnTopSlabsIsPlanned(GameTestHelper helper) {
		int[] c = plannedOn(helper, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 32);
		helper.assertTrue(c != null, "no column planned on top slabs for a block 39 over the floor (32 scaffolds reach it)");
		helper.assertTrue(c[0] == 32 && c[1] == 34, "expected 32 scaffolds standing 34 up, seen " + c[0] + " scaffolds, standing " + c[1] + " up");
		helper.succeed();
	}

	/**
	 * Bottom slabs one over the floor everywhere round the shaft (no sturdy top: a scaffold on one would fall): no
	 * column is planned onto them, neither one of 32 (a block 39 over the floor) nor a short one of 4 (11 over it).
	 */
	//$ gametest 'HUGE'
	@GameTest(template = HUGE)
	public void aColumnNeverStandsOnABottomSlab(GameTestHelper helper) {
		int[] c = plannedOn(helper, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 32);
		helper.assertTrue(c == null, "a column was planned ending on bottom slabs: " + (c == null ? "" : c[0] + " scaffolds, standing " + c[1] + " up"));
		int[] low = plannedOn(helper, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 4);
		helper.assertTrue(low == null, "a short column was planned ending on bottom slabs: " + (low == null ? "" : low[0] + " scaffolds, standing " + low[1] + " up"));
		helper.succeed();
	}

	// -------------------------------------------------------------------------------------------------------- helpers

	static void fireTick(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
	}

	/** No fire in or round (2 blocks) the whole build. */
	static void noFire(GameTestHelper helper, Setup s) {
		BoundingBox box = s.plan().bounds().inflatedBy(2);
		List<String> burning = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState there = s.level().getBlockState(p);
			if (there.is(BlockTags.FIRE)) {
				burning.add(p.subtract(s.site().placement().origin()).toShortString());
			}
		}
		helper.assertTrue(burning.isEmpty(), burning.size() + " fire block(s) in or round the finished build, e.g. " + burning.stream().limit(4).toList());
	}
}
