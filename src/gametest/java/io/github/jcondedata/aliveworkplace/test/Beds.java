package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Test setup: gives a villager a home the way the game does, with a real bed there. Since B362 a HOME memory with no bed
 * behind it houses nobody ({@code VillageNeeds.bed} asks whether the bed is still there), so a test that stages a
 * housed villager puts a bed at his home first.
 */
final class Beds {
	private Beds() {
	}

	/**
	 * Makes {@code head} (an absolute position) the villager's home. If no bed is there yet, puts one there (its foot on
	 * an empty side) and claims it, as a villager who found the bed would.
	 */
	static void home(Villager villager, BlockPos head) {
		ServerLevel level = (ServerLevel) villager.level();
		PoiManager pois = level.getPoiManager();
		if (!pois.existsAtPosition(PoiTypes.HOME, head)) {
			Direction facing = Direction.NORTH;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (level.getBlockState(head.relative(d.getOpposite())).isAir()) {
					facing = d;
					break;
				}
			}
			BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
			BlockPos foot = head.relative(facing.getOpposite());
			boolean footFree = level.getBlockState(foot).isAir();
			if (footFree) {
				level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
			}
			level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
			// The level registers a new bed's point of interest a moment later; register it now (the later add is a no-op).
			PoiTypes.forState(level.getBlockState(head)).ifPresent(type -> pois.add(head, type));
			pois.take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 1);
		}
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
	}
}
