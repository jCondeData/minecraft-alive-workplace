package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1104-0938), bug (builders at the top of the world). A player on a mountain top places a blueprint whose roof pokes
 * out over the world's build limit (y 320 in the overworld): nothing can stand there. CLAUDE.md's invariant: "Steps that
 * can't be done yet are deferred once, then skipped (counted in skipped)", and the builder never makes or loses a
 * material. The test hut (4 layers) goes up on a stone platform built two blocks under the limit, so its lower two
 * layers fit and its top two don't: the build must end, every block under the limit built, nothing above it, and every
 * material the hut's list was stocked with still loose (chests, bags, ground) but what the built blocks took.
 */
public class QaBuildHeightLimit1104BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-test");
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '9000' '"qaBuildHeightLimit"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaBuildHeightLimit")
	public void aHutWhoseRoofPokesOverTheWorldsTopEndsWithTheRestBuiltAndNothingLost(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		int max = level.getMaxBuildHeight(); // the first y nothing can be placed at
		int g = max - 2 - helper.absolutePos(BlockPos.ZERO).getY(); // the hut's lowest layer, in the helper's terms
		// the mountain top: a stone platform under the bench, chests, builder and hut, the open sky above
		for (int x = -1; x <= 16; x++) {
			for (int z = -1; z <= 16; z++) {
				for (int y = g - 1; y <= g + 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == g - 1 ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		Leftovers.after(helper, () -> {
			AABB top = new AABB(helper.absolutePos(new BlockPos(-4, g - 2, -4))).expandTowards(24, 10, 24);
			for (Entity e : level.getEntitiesOfClass(Entity.class, top, e -> e instanceof Villager || e instanceof ItemEntity)) {
				e.discard();
			}
			for (int x = -1; x <= 16; x++) {
				for (int z = -1; z <= 16; z++) {
					for (int y = g - 1; y < max - helper.absolutePos(BlockPos.ZERO).getY(); y++) {
						level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		});
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, g, new BlockPos(6, g, 6));
		int above = 0;
		Map<Item, Integer> built = new HashMap<>();
		for (Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> e : r.plan().targets().entrySet()) {
			if (!e.getValue().isAir() && e.getKey().getY() >= max) {
				above++;
			}
		}
		helper.assertTrue(above > 0 && r.plan().bounds().minY() < max, "setup: the hut isn't across the limit: bounds " + r.plan().bounds()
			+ ", limit " + max);
		int steps = above;
		LOGGER.info("qa height limit: the hut {} across the limit {}, {} block(s) above it", r.plan().bounds(), max, steps);
		helper.succeedWhen(() -> {
			BuildSite s = r.site();
			if (s != null) {
				Map<Item, Integer> loose = new HashMap<>();
				for (Item item : r.plan().materials().keySet()) {
					loose.put(item, QaBuilderTerrain1102GameTests.loose(helper, r, item));
				}
				helper.fail("the hut whose roof is over the world's top never ends: " + QaBuilderTerrain1102GameTests.state(helper, r)
					+ "; loose (chests, bags, ground) " + loose + " of the stock " + r.plan().materials() + " + " + QaBuilderTerrain1102GameTests.EXTRA
					+ " each; " + r.plan().unfinished(level).stream().filter(p -> p.getY() < max).count() + " block(s) under the top still unbuilt; "
					+ "the drawing's blocks under the top " + count(r.plan(), max, true) + " and over it " + count(r.plan(), max, false));
			}
			// every block under the top is built, but a door whose upper half would be over it (it can't stand there)
			List<BlockPos> unbuilt = new ArrayList<>();
			for (BlockPos p : r.plan().unfinished(level)) {
				if (p.getY() < max && !(r.plan().targets().get(p).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
					unbuilt.add(helper.relativePos(p));
				}
			}
			helper.assertTrue(unbuilt.isEmpty(), "blocks under the world's top left unbuilt: " + unbuilt);
			// what stands of the hut took exactly its blocks: what's loose is the stock less what stands (a door once)
			for (Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> e : r.plan().targets().entrySet()) {
				net.minecraft.world.level.block.state.BlockState there = level.getBlockState(e.getKey());
				if (e.getValue().isAir() || e.getKey().getY() >= max || !io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(there, e.getValue())
					|| there.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
					&& there.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
						== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
					continue;
				}
				built.merge(there.getBlock().asItem(), 1, Integer::sum);
			}
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int want = e.getValue() + QaBuilderTerrain1102GameTests.EXTRA - built.getOrDefault(e.getKey(), 0);
				int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey());
				if (got != want) {
					off.put(e.getKey(), "loose " + got + ", expected " + want);
				}
			}
			helper.assertTrue(off.isEmpty(), "materials made or lost by the hut over the world's top (" + steps + " block(s) over it): " + off);
			built.clear();
		});
	}

	/** The drawing's blocks by item under ({@code under}) or over the world's top {@code max}. */
	private static Map<Item, Integer> count(io.github.jcondedata.aliveworkplace.build.BuildPlan plan, int max, boolean under) {
		Map<Item, Integer> out = new HashMap<>();
		plan.targets().forEach((p, state) -> {
			if (!state.isAir() && (p.getY() < max) == under) {
				out.merge(state.getBlock().asItem(), 1, Integer::sum);
			}
		});
		return out;
	}
}
