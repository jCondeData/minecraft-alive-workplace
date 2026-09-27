package io.github.jcondedata.aliveworkplace.farm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** The field a farmer tends (saved on the villager). {@code box} spans the corner blocks the player marked. */
public record FieldJob(BoundingBox box) {
	public static final Codec<FieldJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		BoundingBox.CODEC.fieldOf("box").forGetter(FieldJob::box)
	).apply(i, FieldJob::new));
}
