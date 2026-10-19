package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Batches reuse the same spots, and only what stands inside a test's area is cleared between them: a villager or mob
 * that wandered outside (a guard backing away from a creeper, a builder hopping about) is still there when the next
 * batch starts, and walks into it. A leftover guard whose post was at the same spot took the new guard's sword; a
 * leftover villager can take a workstation. Tests that run alone in their batch clear everything around them first.
 */
final class Leftovers {
	/** How far around the test area to clear: more than the reach of any worker looking for its workstation. */
	private static final double RADIUS = 48;

	/** Removes every entity but players near the test area. Only for tests alone in their batch. */
	static void clear(GameTestHelper helper) {
		AABB around = helper.getBounds().inflate(RADIUS);
		for (Entity e : helper.getLevel().getEntitiesOfClass(Entity.class, around, e -> !(e instanceof Player))) {
			e.discard();
		}
		// 27.19: what an earlier batch's players built here isn't this test's
		io.github.jcondedata.aliveworkplace.city.PlayerBuilt.get(helper.getLevel()).forget(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			net.minecraft.core.BlockPos.containing(around.minX, around.minY, around.minZ), net.minecraft.core.BlockPos.containing(around.maxX, around.maxY, around.maxZ)));
	}

	/**
	 * Logs out every mock player near the test area. Many tests make one, teleport it into their area and never log it
	 * out, so it stands there into later batches at the same spot: a Legend guest who leaves only with nobody within 24
	 * blocks stayed because an earlier batch's player was standing by the hall. Only for tests alone in their batch
	 * (batches run one after another, so no other test's player is still in use).
	 */
	static void players(GameTestHelper helper) {
		AABB around = helper.getBounds().inflate(RADIUS);
		var list = helper.getLevel().getServer().getPlayerList();
		for (net.minecraft.server.level.ServerPlayer p : java.util.List.copyOf(helper.getLevel().players())) {
			if (around.contains(p.position())) {
				list.remove(p);
			}
		}
	}

	/**
	 * Removes every Village Hall whose village reaches the test area. A hall another batch's test left nearby (outside
	 * its own area, so never cleared) makes the villagers here its village, with its needs' pace and its edicts: a
	 * well-kept hall left by the Steward's homes tests sped up legendPace's builder past its own numbers. Only for tests
	 * alone in their batch.
	 */
	static void halls(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		// from the middle of the box to its farthest corner, height included: with half the width only, a hall 61 blocks
		// from a workstation near the corner (so in its village) was missed, and LuxuryWorkGameTests' maker used its store
		double halfDiagonal = Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize()) / 2;
		int reach = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS + (int) Math.ceil(halfDiagonal);
		java.util.List<net.minecraft.core.BlockPos> halls = level.getPoiManager().findAll(
			h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.VILLAGE_HALL_POI), p -> true,
			net.minecraft.core.BlockPos.containing(box.getCenter()), reach, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList();
		for (net.minecraft.core.BlockPos hall : halls) {
			level.removeBlock(hall, false);
		}
		io.github.jcondedata.aliveworkplace.hall.VillageNeeds.forget();
	}

	/**
	 * Forgets every build a Steward started from a hall in or near the test area, and removes every Storehouse whose
	 * village reaches it. Both outlive their test: a site is saved world data, and a Storehouse built outside its own
	 * area is never cleared. A Steward's rounds visit both, so a Steward test at the same spot as an earlier batch's
	 * StewardJobs test (same hall, an approved build) counted a third stop (CI 817, 2026-10-11). Only for tests alone in
	 * their batch.
	 */
	static void stewardSites(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		AABB around = helper.getBounds().inflate(RADIUS);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : java.util.List.copyOf(sites.all())) {
			if (site.stewardHall() != null && around.contains(net.minecraft.world.phys.Vec3.atCenterOf(site.stewardHall()))) {
				sites.remove(site.id());
			}
		}
		storehouses(helper);
	}

	/**
	 * Removes every Storehouse whose village (at {@code VillageHalls.RADIUS} as it is now) reaches the test area. One built
	 * outside its own test's area is never cleared, and a hall here counts its chests as its own: LostCaravanGameTests'
	 * caravan was loaded from an earlier batch's Storehouse nearby, so its own chest kept 96 logs, not 64 (B184).
	 * Only for tests alone in their batch.
	 */
	static void storehouses(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		int reach = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS + (int) Math.ceil(Math.max(box.getXsize(), box.getZsize()) / 2);
		java.util.List<net.minecraft.core.BlockPos> storehouses = level.getPoiManager().findAll(
			h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI), p -> true,
			net.minecraft.core.BlockPos.containing(box.getCenter()), reach, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList();
		for (net.minecraft.core.BlockPos storehouse : storehouses) {
			level.removeBlock(storehouse, false);
		}
	}

	/**
	 * Forgets every finished building recorded within a village's reach of the test area. The record is saved world data,
	 * so buildings another batch's test recorded at this spot (Steward and upgrade tests record many, and not all forget
	 * them) are still "finished" here: the Master Architect picked one of those for a rebuild when the test expected none
	 * (CI, 2026-10-05). Only for tests alone in their batch.
	 */
	static void finished(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		// a hall anywhere in the area: its reach plus half the area's diagonal
		int reach = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS
			+ (int) Math.ceil(Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize()) / 2);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		for (io.github.jcondedata.aliveworkplace.build.BuildSiteManager.Finished f : sites.finishedNear(level, net.minecraft.core.BlockPos.containing(box.getCenter()), reach)) {
			sites.forgetFinished(f.placement());
		}
	}

	/**
	 * Forgets every finished building and removes every Wonder site that a Wonder Plan placed for the hall at
	 * {@code hall} (absolute) would be checked against: what {@code WonderPlans.placeRefusal} looks at, the Wonder's
	 * reach plus two villages round the hall. Both are saved world data that outlive their test, and the area is wider
	 * than {@link #finished}'s, so a building or Wonder site an earlier batch left there refused a plan the test meant
	 * to go down ("not placed inside the village", "turn 1 refused", B125). Only for tests alone in their batch.
	 */
	static void wonderGround(GameTestHelper helper, net.minecraft.core.BlockPos hall) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		int reach = io.github.jcondedata.aliveworkplace.wonder.WonderPlans.REACH + 2 * io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		for (io.github.jcondedata.aliveworkplace.build.BuildSiteManager.Finished f : sites.finishedNear(level, hall, reach)) {
			sites.forgetFinished(f.placement());
		}
		net.minecraft.resources.ResourceLocation dimension = level.dimension().location();
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : java.util.List.copyOf(sites.all())) {
			if (site.structure().getPath().startsWith(io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.FOLDER)
				&& site.placement().dimension().equals(dimension) && site.placement().origin().distSqr(hall) <= (double) reach * reach) {
				sites.remove(site.id());
			}
		}
		// B143: a finished Wonder's parts are no longer finished buildings; placeRefusal checks the Wonder's own record
		io.github.jcondedata.aliveworkplace.wonder.WonderManager wonders = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level);
		for (io.github.jcondedata.aliveworkplace.wonder.WonderSite w : java.util.List.copyOf(wonders.sites())) {
			if (w.done() && w.placement().isPresent() && w.placement().get().dimension().equals(dimension)
				&& w.placement().get().origin().distSqr(hall) <= (double) reach * reach) {
				wonders.remove(w.id());
			}
		}
	}

	/**
	 * Removes every fruit plant (berry bushes, cave vines, cocoa, and anything else ripe) outside the test area within
	 * {@code radius} of {@code center} (absolute), from 4 below to 10 above. An Orchard Keeper picks ripe fruit within 16
	 * of the basket before planting, and an earlier batch's glow berries left just outside the area took the planting
	 * test's keeper out of it, into a chunk that doesn't tick, where he stood frozen for the rest of the test. Only for
	 * tests alone in their batch.
	 */
	static void fruit(GameTestHelper helper, net.minecraft.core.BlockPos center, int radius) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		AABB area = helper.getBounds();
		for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 10, radius))) {
			if (area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p))) {
				continue;
			}
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof net.minecraft.world.level.block.SweetBerryBushBlock || state.getBlock() instanceof net.minecraft.world.level.block.CaveVines
				|| state.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock || io.github.jcondedata.aliveworkplace.orchard.Fruit.isRipe(state)) {
				level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
	}

	/**
	 * Turns village sharing on for this test ({@code Village.RADIUS}, normally off in tests) and off again when the test
	 * ends, passed or failed — a failed test that left it on made the next batches' builders share chests and fail too.
	 */
	static void village(GameTestHelper helper, int radius) {
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = radius;
		after(helper, () -> io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0);
	}

	/** Runs {@code reset} when the test ends, passed or failed (a setting the test changed for itself). */
	static void after(GameTestHelper helper, Runnable reset) {
		try {
			java.lang.reflect.Field field = GameTestHelper.class.getDeclaredField("testInfo");
			field.setAccessible(true);
			net.minecraft.gametest.framework.GameTestInfo info = (net.minecraft.gametest.framework.GameTestInfo) field.get(helper);
			info.addListener(new net.minecraft.gametest.framework.GameTestListener() {
				@Override
				public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {
				}

				@Override
				public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) {
					reset.run();
				}

				@Override
				public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) {
					reset.run();
				}

				@Override
				public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old, net.minecraft.gametest.framework.GameTestInfo test,
						net.minecraft.gametest.framework.GameTestRunner runner) {
				}
			});
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private Leftovers() {
	}
}
