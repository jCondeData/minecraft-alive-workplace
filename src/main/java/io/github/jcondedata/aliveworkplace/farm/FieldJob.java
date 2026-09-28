package io.github.jcondedata.aliveworkplace.farm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The field a farmer tends (saved on the villager). {@code box} spans the corner blocks the player marked. {@code adopted}:
 * a village farmer took it on by themselves (no marker to give back when it stops).
 */
public record FieldJob(BoundingBox box, boolean adopted) {
	public FieldJob(BoundingBox box) {
		this(box, false);
	}

	public static final Codec<FieldJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		BoundingBox.CODEC.fieldOf("box").forGetter(FieldJob::box),
		Codec.BOOL.optionalFieldOf("adopted", false).forGetter(FieldJob::adopted)
	).apply(i, FieldJob::new));
}
