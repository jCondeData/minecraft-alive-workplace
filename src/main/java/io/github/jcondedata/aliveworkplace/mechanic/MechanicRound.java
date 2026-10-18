package io.github.jcondedata.aliveworkplace.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A Mechanic's round (ROADMAP 39.2, saved as {@code mechanic_round}): the village's machines in the order they're
 * walked, and the next one to visit. Both fields are optional, so an empty or partial record loads as a fresh round
 * (found again from the toolbox).
 */
public record MechanicRound(List<BlockPos> machines, int next) {
	public static final MechanicRound EMPTY = new MechanicRound(List.of(), 0);

	public static final Codec<MechanicRound> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.listOf().optionalFieldOf("machines", List.of()).forGetter(MechanicRound::machines),
		Codec.INT.optionalFieldOf("next", 0).forGetter(MechanicRound::next)
	).apply(i, MechanicRound::new));

	public MechanicRound {
		machines = List.copyOf(machines);
		next = machines.isEmpty() ? 0 : Math.floorMod(next, machines.size());
	}

	/** The machine to visit now, or null for an empty round. */
	public BlockPos current() {
		return machines.isEmpty() ? null : machines.get(next);
	}

	/** The round one machine on; wrapping back to the start. */
	public MechanicRound advance() {
		return new MechanicRound(machines, next + 1);
	}

	/** Whether the round has just come back to its start (time to look for machines again). */
	public boolean atStart() {
		return next == 0;
	}
}
