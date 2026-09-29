package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The village's gates: in a village with a Village Hall and at least one guard, the gates of its finished Gatehouses and
 * Palisade Gates are shut at nightfall and opened again in the morning (the hall's round does it, so within half a
 * minute). Players can still open them by hand.
 */
public final class Gates {
	/** The blueprints whose fence gates are the village's gates. */
	static final Set<ResourceLocation> GATED = Set.of(StarterBlueprints.GATEHOUSE.id(), StarterBlueprints.PALISADE_GATE.id());

	/** Night for the gates: from dusk until just before dawn. */
	public static boolean shutTime(ServerLevel level) {
		long time = level.getDayTime() % VillageNeeds.DAY;
		return time >= 12500 && time < 23500;
	}

	/** The hall's round: shuts or opens the village's gates as the time of day wants. Returns how many gates moved. */
	public static int round(ServerLevel level, BlockPos hall, int guards) {
		if (guards <= 0) {
			return 0;
		}
		boolean shut = shutTime(level);
		int moved = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			if (!GATED.contains(BlueprintStyles.base(f.structure()))) {
				continue;
			}
			Blueprint blueprint = BlueprintLibrary.get(level, f.structure()).orElse(null);
			if (blueprint == null) {
				continue;
			}
			int here = 0;
			BlockPos first = null;
			for (Blueprint.Entry e : blueprint.blocks()) {
				if (!(e.state().getBlock() instanceof FenceGateBlock)) {
					continue;
				}
				BlockPos pos = f.placement().origin().offset(StructureTemplate.transform(e.pos(), f.placement().mirror(), f.placement().rotation(), BlockPos.ZERO));
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN) == shut) {
					level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, !shut), 10);
					here++;
					first = first == null ? pos : first;
				}
			}
			if (here > 0) {
				level.playSound(null, first, shut ? SoundEvents.FENCE_GATE_CLOSE : SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1f, 0.9f);
				moved += here;
			}
		}
		return moved;
	}

	private Gates() {
	}
}
