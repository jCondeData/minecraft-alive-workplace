package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA for the Arena (ROADMAP 28.16), from the spec's words about the build: the Arena is at most 25 x 19; every tier
 * keeps a 15 x 9 battle ring of packed mud inside a white line round the ring centre (one block outside it isn't
 * ring); the first tier has two banner poles and a lamp post by each trainer's box.
 */
public class QaArenaGameTests implements FabricGameTest {
	/** The ring's half sizes: 15 x 9 is 7 either side of the centre along x, 4 along z. */
	private static final int HALF_X = 7;
	private static final int HALF_Z = 4;

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyArenaHasAFifteenByNineRingAndTheFirstFits(GameTestHelper helper) {
		Blueprint first = blueprint(helper, StarterBlueprints.ARENA);
		helper.assertTrue(first.size().getX() <= 25 && first.size().getZ() <= 19, "the Arena is " + first.size().getX() + " x " + first.size().getZ()
			+ ", more than 25 x 19");
		BlockPos centre = Arenas.RING_CENTRE.below();
		for (StarterBlueprints.Entry entry : StarterBlueprints.ARENAS) {
			Map<BlockPos, BlockState> at = states(blueprint(helper, entry));
			int mud = 0;
			for (int dx = -HALF_X; dx <= HALF_X; dx++) {
				for (int dz = -HALF_Z; dz <= HALF_Z; dz++) {
					BlockPos p = centre.offset(dx, 0, dz);
					BlockState s = at.getOrDefault(p, Blocks.AIR.defaultBlockState());
					boolean edge = Math.abs(dx) == HALF_X || Math.abs(dz) == HALF_Z;
					boolean white = s.is(Blocks.WHITE_CONCRETE) || s.is(Blocks.WHITE_GLAZED_TERRACOTTA) || s.is(Blocks.WHITE_WOOL)
						|| s.is(Blocks.WHITE_TERRACOTTA) || s.is(Blocks.WHITE_CONCRETE_POWDER) || s.is(Blocks.QUARTZ_BLOCK) || s.is(Blocks.CALCITE);
					helper.assertTrue(edge ? white : white || s.is(Blocks.PACKED_MUD), entry.id() + ": the ring at " + p.toShortString() + " is " + s);
					helper.assertTrue(at.getOrDefault(p.above(), Blocks.AIR.defaultBlockState()).isAir(), entry.id() + ": something stands in the ring at "
						+ p.above().toShortString());
					mud += s.is(Blocks.PACKED_MUD) ? 1 : 0;
				}
			}
			helper.assertTrue(mud >= 15 * 9 / 2, entry.id() + ": a ring of " + mud + " packed mud");
			for (int dx = -HALF_X - 1; dx <= HALF_X + 1; dx++) {
				for (int dz : new int[]{-HALF_Z - 1, HALF_Z + 1}) {
					BlockPos p = centre.offset(dx, 0, dz);
					helper.assertFalse(at.getOrDefault(p, Blocks.AIR.defaultBlockState()).is(Blocks.PACKED_MUD), entry.id() + ": the ring is wider than 9 at "
						+ p.toShortString());
				}
			}
			for (int dz = -HALF_Z; dz <= HALF_Z; dz++) {
				for (int dx : new int[]{-HALF_X - 1, HALF_X + 1}) {
					BlockPos p = centre.offset(dx, 0, dz);
					helper.assertFalse(at.getOrDefault(p, Blocks.AIR.defaultBlockState()).is(Blocks.PACKED_MUD), entry.id() + ": the ring is longer than 15 at "
						+ p.toShortString());
				}
			}
		}

		// The Arena: two banner poles, a lamp post by each trainer's box
		Map<BlockPos, BlockState> at = states(first);
		Set<Integer> poles = new TreeSet<>();
		at.forEach((p, s) -> {
			if (s.getBlock() instanceof AbstractBannerBlock) {
				poles.add(p.getX());
			}
		});
		helper.assertTrue(poles.size() == 2, "banner poles at x " + poles + ", not two");
		for (BlockPos box : Arenas.BOXES) {
			boolean lamp = at.entrySet().stream().anyMatch(e -> e.getValue().getLightEmission() >= 10
				&& Math.abs(e.getKey().getX() - box.getX()) <= 4 && Math.abs(e.getKey().getZ() - box.getZ()) <= 4
				&& Integer.signum(e.getKey().getX() - Arenas.RING_CENTRE.getX()) == Integer.signum(box.getX() - Arenas.RING_CENTRE.getX()));
			helper.assertTrue(lamp, "no lamp by the trainer's box at " + box.toShortString());
		}
		helper.succeed();
	}

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new GameTestAssertException("missing " + entry.id()));
	}

	private static Map<BlockPos, BlockState> states(Blueprint blueprint) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			out.put(e.pos(), e.state());
		}
		return out;
	}
}
