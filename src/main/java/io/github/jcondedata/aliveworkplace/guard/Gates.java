package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
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
 *
 * <p>In a siege (ROADMAP 32.4, {@code threat/Sieges}) they shut at once, whatever the hour and with or without guards,
 * and the portcullis of every gate build drops; they stay shut until the first dawn after. On the day of an attack the
 * village was warned of ({@link Warnings}, 32.14) they shut from noon. A village with no guard opens the gates a siege
 * or a warned day shut once it is over (B1158), and the gates its last guard shut for the night at dawn (B1180).
 */
public final class Gates {
	/** The blueprints whose fence gates are the village's gates: the two we ship, and every wall kit's gate (27.18). */
	static Set<ResourceLocation> gated() {
		return io.github.jcondedata.aliveworkplace.threat.Sieges.gateBuilds();
	}

	/** Night for the gates: from dusk until just before dawn. */
	public static boolean shutTime(ServerLevel level) {
		long time = level.getDayTime() % VillageNeeds.DAY;
		return time >= 12500 && time < 23500;
	}

	/**
	 * The hall's round: shuts or opens the village's gates as the time of day wants; a siege keeps them shut, guards or
	 * none. Returns how many gates moved.
	 */
	public static int round(ServerLevel level, BlockPos hall, int guards) {
		// A siege keeps them shut; so does a warned attack's day, from noon (32.14).
		boolean siege = io.github.jcondedata.aliveworkplace.threat.Sieges.shut(level, hall) || Warnings.ready(level, hall);
		// a Great Wall's Great Gate shuts at night, guards or none (35.16)
		int moved = io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers.shutGates(level, hall, siege || shutTime(level));
		io.github.jcondedata.aliveworkplace.threat.ThreatData data = io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level);
		if (guards <= 0) {
			// No guard keeps the hours, so the gates a siege or a warned day shut open again once it is over (B1158), and
			// the gates the last guard shut for the night open at dawn (B1180); a village that never had them shut leaves
			// them as the players set them.
			if (siege) {
				data.gatesShut(hall, true);
				return moved + shut(level, hall, true);
			}
			boolean over = data.gatesShut(hall) || data.nightShut(hall) && !shutTime(level);
			if (!over) {
				return moved;
			}
			data.gatesShut(hall, false);
			data.nightShut(hall, false);
			return moved + shut(level, hall, false);
		}
		boolean shut = siege || shutTime(level);
		// remembered, so the gates still open at dawn if this guard falls in the night (B1180)
		data.nightShut(hall, shut);
		if (!shut) {
			data.gatesShut(hall, false); // a guard opened them: the guardless village's shut is over
		}
		return moved + shut(level, hall, shut);
	}

	/** Shuts ({@code shut}) or opens the gates of the village round {@code hall} now. Returns how many gates moved. */
	public static int shut(ServerLevel level, BlockPos hall, boolean shut) {
		int moved = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedInVillage(level, hall)) {
			if (!io.github.jcondedata.aliveworkplace.threat.Sieges.isGated(f.structure())) { // in any style or tier: Gatehouse II's two gates (32.20)
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
