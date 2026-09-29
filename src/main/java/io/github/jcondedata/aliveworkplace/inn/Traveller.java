package io.github.jcondedata.aliveworkplace.inn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Attached to a traveller staying at an inn: when they arrived and the level they'd start a job at if hired. */
public record Traveller(long arrived, int level) {
	public static final Codec<Traveller> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("arrived").forGetter(Traveller::arrived),
		Codec.INT.optionalFieldOf("level", 2).forGetter(Traveller::level)
	).apply(i, Traveller::new));
}
