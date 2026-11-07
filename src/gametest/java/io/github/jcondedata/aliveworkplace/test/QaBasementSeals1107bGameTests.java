package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.BENCH;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ORIGIN;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.TALL_AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.VILLAGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertBuilt;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertNoFluidInBasement;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.fill;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.placement;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.rules;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.stacks;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.wet;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Extent;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Setup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane-b-1107-0637, 2026-11-07), from the spec in docs/wiki/basements.md ("Water or lava in the earth beside the
 * dig ... is plugged with dirt the moment a spot below the ground is dug next to it ... the room stays dry, the builder
 * never stands in water or burns, and the walls and floor go up as drawn"; "seagrass or kelp is plugged like water")
 * after the fix of B923/B924: corners the earlier rounds (QaBasementGround1104*, QaBasementFluids1104f*) left open.
 * An aquifer all round the room (the front wall too), lava at the floor's own level and just under the ground, lava
 * with a server restart mid-dig, kelp, a builder with no materials at all (the dig still dry while he waits), and the
 * Level 1 cellars of two fresh builds (the Butcher's freezer beside water, the Storehouse's vault beside lava).
 */
public class QaBasementSeals1107bGameTests implements FabricGameTest {

	// ---------------------------------------------------------------------------------------------- aquifer all round

	/**
	 * Water sources at every spot of earth just outside all four of the Cottage IV basement's walls (the front too) at
	 * y -2, an aquifer round the whole room: the build finishes as drawn with no water in the basement, the builder
	 * alive and never stuck in water under the ground, and the aquifer plugged rather than run into the room.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaSealsAquifer"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaSealsAquifer")
	public void anAquiferAllRoundTheBasementLeavesItDry(GameTestHelper helper) {
		List<BlockPos> ring = new ArrayList<>();
		Setup s = site(helper, StarterBlueprints.STARTER_COTTAGE_4, StarterBlueprints.STARTER_COTTAGE_3, b -> {
			Extent ex = Extent.of(b);
			Set<BlockPos> drawn = drawn(b);
			for (int x = ex.minX(); x <= ex.maxX(); x++) {
				ring.add(new BlockPos(x, -2, ex.minZ() - 1));
				ring.add(new BlockPos(x, -2, ex.maxZ() + 1));
			}
			for (int z = ex.minZ(); z <= ex.maxZ(); z++) {
				ring.add(new BlockPos(ex.minX() - 1, -2, z));
				ring.add(new BlockPos(ex.maxX() + 1, -2, z));
			}
			for (BlockPos rel : ring) {
				helper.assertFalse(drawn.contains(rel), "setup: " + rel.toShortString() + " is a spot of the build");
				helper.setBlock(ORIGIN.offset(rel), Blocks.WATER);
			}
		}, true);
		for (BlockPos rel : ring) {
			helper.assertTrue(helper.getBlockState(ORIGIN.offset(rel)).is(Blocks.WATER), "setup: no water at " + rel.toShortString());
		}
		int[] stuck = watchStuckInWater(helper, s);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int checked = assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			long plugged = ring.stream().filter(r -> helper.getBlockState(ORIGIN.offset(r)).is(Blocks.DIRT)).count();
			LOGGER.info("qa seals aquifer: {} water sources round the room, built at tick {}, {} basement spots dry, {} now dirt, longest in water {} ticks",
				ring.size(), helper.getTick(), checked, plugged, stuck[1]);
		});
	}

	// ------------------------------------------------------------------------------------- lava at the dig's top and floor

	/**
	 * Lava sources just outside the basement's front (north) wall at the floor's own level (the deepest dug layer) and
	 * just outside its back wall one under the ground (the shallowest), fire spread on: the build finishes as drawn
	 * with no lava or fire in or round it, and the builder alive and not burning.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaSealsLavaLevels"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaSealsLavaLevels")
	public void lavaAtTheDigsTopAndFloorLevelsBurnsNothing(GameTestHelper helper) {
		QaBasementFluids1104fGameTests.fireTick(helper);
		List<BlockPos> hot = new ArrayList<>();
		Setup s = site(helper, StarterBlueprints.STARTER_COTTAGE_4, StarterBlueprints.STARTER_COTTAGE_3, b -> {
			Extent ex = Extent.of(b);
			Set<BlockPos> drawn = drawn(b);
			hot.add(new BlockPos(ex.midX(), ex.minY(), ex.minZ() - 1));
			hot.add(new BlockPos(ex.midX() - 2, -1, ex.maxZ() + 1));
			for (BlockPos rel : hot) {
				helper.assertFalse(drawn.contains(rel), "setup: " + rel.toShortString() + " is a spot of the build");
				helper.assertTrue(ORIGIN.offset(rel).getY() >= 2, "setup: " + rel.toShortString() + " is under the area's ground");
				helper.setBlock(ORIGIN.offset(rel), Blocks.LAVA);
			}
		}, true);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int checked = assertNoFluidInBasement(helper, s);
			QaBasementFluids1104fGameTests.noFire(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			helper.assertFalse(s.villager().isOnFire(), "the builder is burning at " + helper.relativePos(s.villager().blockPosition()));
			LOGGER.info("qa seals lava levels: built at tick {}, {} spots dry, the lava spots now {}", helper.getTick(), checked, names(helper, hot));
		});
	}

	// --------------------------------------------------------------------------------------------- lava and a restart

	/**
	 * Lava sources beside the basement (the B924 spots: outside the west, east and south walls and under the floor),
	 * fire spread on, and the build sites saved and loaded from NBT mid-CLEAR once the room's middle at y -2 is dug: the
	 * build still finishes as drawn with no lava or fire, the builder alive and not burning.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaSealsLavaRestart"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaSealsLavaRestart")
	public void lavaBesideTheBasementWithARestartMidDigBurnsNothing(GameTestHelper helper) {
		QaBasementFluids1104fGameTests.fireTick(helper);
		List<BlockPos> hot = new ArrayList<>();
		Setup s = site(helper, StarterBlueprints.STARTER_COTTAGE_4, StarterBlueprints.STARTER_COTTAGE_3, b -> hot.addAll(wet(helper, b, Blocks.LAVA)), true);
		ServerLevel level = s.level();
		int[] restartedAt = {-1};
		BlockPos dugProbe = s.site().placement().origin().offset(QaBasementFluids1104fGameTests.ROOM_MIDDLE);
		helper.onEachTick(() -> {
			if (restartedAt[0] < 0) {
				BuildSite live = BuildSiteManager.get(level).get(s.site().id());
				if (live != null && live.stage() == BuildPlan.Stage.CLEAR && level.getBlockState(dugProbe).isAir()) {
					QaBasementFluids1104fGameTests.restart(helper);
					restartedAt[0] = (int) helper.getTick();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restartedAt[0] >= 0, "the server never restarted mid-CLEAR (the room's middle at y -2 never dug)");
			assertBuilt(helper, s);
			assertNoFluidInBasement(helper, s);
			QaBasementFluids1104fGameTests.noFire(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			helper.assertFalse(s.villager().isOnFire(), "the builder is burning at " + helper.relativePos(s.villager().blockPosition()));
			LOGGER.info("qa seals lava restart: restarted at tick {}, built at tick {}, the lava spots now {}", restartedAt[0], helper.getTick(), names(helper, hot));
		});
	}

	// ----------------------------------------------------------------------------------------------------------- kelp

	/**
	 * Kelp (always in water) in the earth just outside the basement's west, east and south walls: "seagrass or kelp is
	 * plugged like water", so the build finishes as drawn with no water in the basement and the builder alive.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaSealsKelp"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaSealsKelp")
	public void kelpBesideTheBasementIsPluggedLikeWater(GameTestHelper helper) {
		List<BlockPos> kelp = new ArrayList<>();
		Setup s = site(helper, StarterBlueprints.STARTER_COTTAGE_4, StarterBlueprints.STARTER_COTTAGE_3, b -> {
			List<BlockPos> spots = Extent.of(b).wetSpots().subList(0, 3);
			Set<BlockPos> drawn = drawn(b);
			for (BlockPos rel : spots) {
				helper.assertFalse(drawn.contains(rel), "setup: " + rel.toShortString() + " is a spot of the build");
				helper.setBlock(ORIGIN.offset(rel), Blocks.KELP);
				kelp.add(rel);
			}
		}, true);
		for (BlockPos rel : kelp) {
			helper.assertTrue(helper.getBlockState(ORIGIN.offset(rel)).is(Blocks.KELP)
				&& s.level().getFluidState(helper.absolutePos(ORIGIN.offset(rel))).is(FluidTags.WATER), "setup: no kelp in water at " + rel.toShortString());
		}
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int checked = assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa seals kelp: built at tick {}, {} spots dry, the kelp spots now {}", helper.getTick(), checked, names(helper, kelp));
		});
	}

	// ------------------------------------------------------------------------------------------------- no materials

	/**
	 * Water beside the basement (the B923 spots) and a builder with no materials at all: clearing never waits for
	 * materials, so he digs the room out and then waits for them; 400 ticks into the wait the dug room still holds no
	 * water, and the builder is alive and not standing in water under the ground.
	 */
	//$ gametest_ticks_batch TALL_AREA '16000' '"qaSealsNoStock"'
	@GameTest(template = TALL_AREA, timeoutTicks = 16000, batch = "qaSealsNoStock")
	public void waterBesideTheDigStaysOutWhileTheBuilderWaitsForMaterials(GameTestHelper helper) {
		List<BlockPos> wet = new ArrayList<>();
		Setup s = site(helper, StarterBlueprints.STARTER_COTTAGE_4, StarterBlueprints.STARTER_COTTAGE_3, b -> wet.addAll(wet(helper, b, Blocks.WATER)), false);
		int[] stuck = watchStuckInWater(helper, s);
		long[] waitingSince = {-1};
		BlockPos dugProbe = s.site().placement().origin().offset(QaBasementFluids1104fGameTests.ROOM_MIDDLE);
		helper.onEachTick(() -> {
			if (waitingSince[0] < 0 && s.site().stage().ordinal() > BuildPlan.Stage.CLEAR.ordinal()
					&& s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				waitingSince[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waitingSince[0] >= 0, "never got past clearing to wait for materials: stage=" + s.site().stage() + " status=" + s.site().status());
			helper.assertTrue(helper.getTick() >= waitingSince[0] + 400, "waiting only since tick " + waitingSince[0]);
			helper.assertTrue(s.level().getBlockState(dugProbe).isAir(), "the room's middle isn't dug: " + s.level().getBlockState(dugProbe));
			int checked = assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			helper.assertFalse(s.level().getFluidState(s.villager().blockPosition()).is(FluidTags.WATER) && s.villager().getY() < s.site().placement().origin().getY(),
				"the builder stands in water under the ground at " + helper.relativePos(s.villager().blockPosition()));
			LOGGER.info("qa seals no stock: waiting since tick {}, {} spots dry at tick {}, the wet spots now {}, longest in water {} ticks",
				waitingSince[0], checked, helper.getTick(), names(helper, wet), stuck[1]);
		});
	}

	// ------------------------------------------------------------------------------------------- Level 1 cellars, fresh

	/**
	 * A fresh Butcher's Shop on deep earth, water in the earth beside its freezer (outside the west, east and south
	 * walls and under its floor): the shop finishes as drawn with no water in the freezer, its blue-ice path and packed
	 * ice whole (the ice never melts), and the builder alive.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaSealsButcher"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaSealsButcher")
	public void aButchersFreezerDugBesideWaterStaysDryAndFrozen(GameTestHelper helper) {
		List<BlockPos> wet = new ArrayList<>();
		Setup s = site(helper, StarterBlueprints.BUTCHERS_SHOP, null, b -> wet.addAll(wet(helper, b, Blocks.WATER)), true);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int checked = assertNoFluidInBasement(helper, s);
			helper.assertBlockPresent(Blocks.BLUE_ICE, ORIGIN.offset(5, -4, 5));
			long packed = s.blueprint().blocks().stream().filter(e -> e.state().is(Blocks.PACKED_ICE)).count();
			long packedNow = s.blueprint().blocks().stream().filter(e -> e.state().is(Blocks.PACKED_ICE))
				.filter(e -> helper.getBlockState(ORIGIN.offset(e.pos())).is(Blocks.PACKED_ICE)).count();
			helper.assertTrue(packed > 0 && packedNow == packed, packedNow + " of " + packed + " packed ice blocks in the freezer");
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa seals butcher: built at tick {}, {} spots dry, {} packed ice whole, the wet spots now {}", helper.getTick(), checked, packedNow, names(helper, wet));
		});
	}

	/**
	 * A fresh Storehouse on deep earth, lava in the earth beside its vault (outside the west, east and south walls and
	 * under its floor), fire spread on: the Storehouse finishes as drawn with no lava or fire in or round it, every
	 * chest of the vault in place, and the builder alive and not burning.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaSealsVault"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaSealsVault")
	public void aStorehouseVaultDugBesideLavaBurnsNothing(GameTestHelper helper) {
		QaBasementFluids1104fGameTests.fireTick(helper);
		List<BlockPos> hot = new ArrayList<>();
		Setup s = site(helper, StarterBlueprints.STOREHOUSE, null, b -> hot.addAll(wet(helper, b, Blocks.LAVA)), true);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int checked = assertNoFluidInBasement(helper, s);
			QaBasementFluids1104fGameTests.noFire(helper, s);
			long chests = s.blueprint().blocks().stream().filter(e -> e.pos().getY() < 0 && e.state().is(Blocks.CHEST)).count();
			long chestsNow = s.blueprint().blocks().stream().filter(e -> e.pos().getY() < 0 && e.state().is(Blocks.CHEST))
				.filter(e -> helper.getBlockState(ORIGIN.offset(e.pos())).is(Blocks.CHEST)).count();
			helper.assertTrue(chests > 0 && chestsNow == chests, chestsNow + " of " + chests + " chests in the vault");
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			helper.assertFalse(s.villager().isOnFire(), "the builder is burning at " + helper.relativePos(s.villager().blockPosition()));
			LOGGER.info("qa seals vault: built at tick {}, {} spots dry, {} vault chests, the lava spots now {}", helper.getTick(), checked, chestsNow, names(helper, hot));
		});
	}

	// -------------------------------------------------------------------------------------------------------- helpers

	/**
	 * {@code entry} clicked at {@link QaBasementGround1104GameTests#ORIGIN} on deep earth, over a finished {@code over}
	 * (or on bare ground when null), the earth shaped by {@code shapeTheGround}, the builder stocked with what the
	 * supply report says is missing when {@code stocked}, else with nothing.
	 */
	static Setup site(GameTestHelper helper, StarterBlueprints.Entry entry, StarterBlueprints.Entry over, Consumer<Blueprint> shapeTheGround, boolean stocked) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlueprintData.Placement placement = placement(helper, ORIGIN);
		if (over != null) {
			level.getStructureManager().get(over.id()).orElseThrow()
				.placeInWorld(level, helper.absolutePos(ORIGIN), helper.absolutePos(ORIGIN), new StructurePlaceSettings(), RandomSource.create(1L), 2);
			BuildSiteManager.get(level).recordFinished(over.id(), placement, UUID.randomUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		}
		ResourceLocation id = entry.id();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		shapeTheGround.accept(blueprint);
		rules(helper);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		List<ItemStack> needed = new ArrayList<>();
		if (stocked) {
			var report = BlueprintSupplies.check(level, new BlueprintData(id, Optional.of(entry.size()), Optional.of(placement)))
				.orElseThrow(() -> new GameTestAssertException("no supply report"));
			report.missing().forEach(m -> needed.addAll(stacks(m.item(), m.count())));
		}
		fill(helper, needed);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, id, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		Setup s = new Setup(level, villager, site, plan, blueprint);
		watchDeath(helper, s);
		return s;
	}

	static Set<BlockPos> drawn(Blueprint blueprint) {
		Set<BlockPos> out = new HashSet<>();
		blueprint.blocks().forEach(e -> out.add(e.pos()));
		return out;
	}

	/**
	 * Fails the test once the builder has stood in water under the ground for 400 ticks in a row (the B923 stall);
	 * returns {current run, longest run} of ticks in water, for the log.
	 */
	static int[] watchStuckInWater(GameTestHelper helper, Setup s) {
		int[] run = {0, 0};
		helper.onEachTick(() -> {
			Villager v = s.villager();
			boolean inWater = v.isAlive() && v.getY() < s.site().placement().origin().getY() && s.level().getFluidState(v.blockPosition()).is(FluidTags.WATER);
			run[0] = inWater ? run[0] + 1 : 0;
			run[1] = Math.max(run[1], run[0]);
			if (run[0] >= 400) {
				throw new GameTestAssertException("the builder has stood in water under the ground for 400 ticks at " + helper.relativePos(v.blockPosition())
					+ " (stage " + s.site().stage() + ", cursor " + s.site().cursor() + ")");
			}
		});
		return run;
	}

	static List<String> names(GameTestHelper helper, List<BlockPos> rels) {
		return rels.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).getBlock().getName().getString()).toList();
	}
}
