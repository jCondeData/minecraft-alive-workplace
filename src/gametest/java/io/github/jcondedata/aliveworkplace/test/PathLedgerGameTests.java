package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * B575: the dirt path a builder lays from a finished house to the bell is his own idea, so, like the Steward's own lanes
 * (B520), it keeps out of every 16x16x16 section the ledger of what players built (27.19) marks, and still runs through
 * the sections nobody marked.
 */
public class PathLedgerGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/**
	 * Hut near one corner, bell in the far one, a player's mark on the section in between: the finished hut gets no path
	 * in that section (neither planned nor laid), and its path still runs in the unmarked sections.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"pathLedger"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "pathLedger")
	public void thePathToTheBellKeepsOutOfAPlayersSection(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = QaBenchMovePathsPaceGameTests.prepare(helper, 2);
		boolean was = Paths.ENABLED;
		Paths.ENABLED = true;
		Leftovers.after(helper, () -> Paths.ENABLED = was);
		Leftovers.halls(helper);
		clearBells(helper);
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox area = new BoundingBox(a.getX() - 20, level.getMinBuildHeight(), a.getZ() - 20, a.getX() + 50, level.getMaxBuildHeight(), a.getZ() + 50);
		PlayerBuilt ledger = PlayerBuilt.get(level);
		ledger.forget(area);
		Leftovers.after(helper, () -> PlayerBuilt.get(level).forget(area));
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		helper.setBlock(new BlockPos(27, 2, 27), Blocks.BELL);
		// a player built in the section halfway between the hut and the bell
		ledger.mark(helper.absolutePos(new BlockPos(19, 1, 19)));
		long marked = SectionPos.asLong(helper.absolutePos(new BlockPos(19, 1, 19)));
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(8, 2, 8)));
		int[] planned = {0};
		helper.succeedWhen(() -> {
			List<BlockPos> plan = ModAttachments.PATH.getOrElse(builder, List.of());
			planned[0] = Math.max(planned[0], plan.size());
			for (BlockPos p : plan) {
				helper.assertTrue(SectionPos.asLong(p) != marked, "a path block planned in the player's section at " + helper.relativePos(p));
			}
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building");
			helper.assertTrue(planned[0] > 0, "no path planned");
			helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the path: " + plan.size() + " to go");
			int inside = 0;
			int outside = 0;
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					BlockPos rel = new BlockPos(x, 1, z);
					if (helper.getBlockState(rel).is(Blocks.DIRT_PATH)) {
						if (SectionPos.asLong(helper.absolutePos(rel)) == marked) {
							inside++;
						} else {
							outside++;
						}
					}
				}
			}
			helper.assertTrue(inside == 0, inside + " blocks of path in the section a player built in");
			helper.assertTrue(outside >= 2, "only " + outside + " blocks of path outside the player's section");
		});
	}

	/**
	 * A path planned before a player built there: the builder skips the blocks in the section marked since (PathWork
	 * checks the ledger again as it lays each block), and paves on in the unmarked one.
	 */
	//$ gametest_ticks_batch HUGE '600' '"pathLedgerLate"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "pathLedgerLate")
	public void aSectionMarkedAfterThePlanIsSkippedWhenLaying(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = QaBenchMovePathsPaceGameTests.prepare(helper, 2);
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox area = new BoundingBox(a.getX() - 20, level.getMinBuildHeight(), a.getZ() - 20, a.getX() + 50, level.getMaxBuildHeight(), a.getZ() + 50);
		PlayerBuilt ledger = PlayerBuilt.get(level);
		ledger.forget(area);
		Leftovers.after(helper, () -> PlayerBuilt.get(level).forget(area));
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		// a straight row of ground at z = 6 from x = 3 to x = 27: it crosses at least one section border
		BlockPos first = helper.absolutePos(new BlockPos(3, 1, 6));
		BlockPos last = helper.absolutePos(new BlockPos(27, 1, 6));
		java.util.ArrayList<BlockPos> row = new java.util.ArrayList<>();
		for (int x = 3; x <= 27; x++) {
			row.add(helper.absolutePos(new BlockPos(x, 1, 6)));
		}
		helper.assertTrue(SectionPos.asLong(first) != SectionPos.asLong(last), "setup: the row stays in one section");
		ModAttachments.PATH.set(builder, List.copyOf(row));
		// then a player builds in the last block's section
		ledger.mark(last);
		long marked = SectionPos.asLong(last);
		helper.assertTrue(!Paths.mayPave(level, last) && Paths.mayPave(level, first), "mayPave ignores the ledger");
		helper.succeedWhen(() -> {
			helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the path: "
				+ ModAttachments.PATH.getOrElse(builder, List.of()).size() + " to go");
			int inside = 0;
			int outside = 0;
			for (BlockPos p : row) {
				if (level.getBlockState(p).is(Blocks.DIRT_PATH)) {
					if (SectionPos.asLong(p) == marked) {
						inside++;
					} else {
						outside++;
					}
				}
			}
			helper.assertTrue(inside == 0, inside + " blocks of path laid in the section a player built in since");
			helper.assertTrue(outside >= 1, "no path laid in the unmarked section");
		});
	}

	/** Bells another batch left beside this area (never cleared) would be nearer than this test's own. */
	private static void clearBells(GameTestHelper helper) {
		AABB box = helper.getBounds();
		for (BlockPos bell : helper.getLevel().getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), p -> !box.contains(Vec3.atCenterOf(p)),
			BlockPos.containing(box.getCenter()), 96, PoiManager.Occupancy.ANY).toList()) {
			helper.getLevel().removeBlock(bell, false);
		}
	}
}
