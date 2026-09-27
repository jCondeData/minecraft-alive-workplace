package io.github.jcondedata.aliveworkplace.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** Attached to a builder: the player who hired it (the first to hand it a blueprint). */
public record Employer(UUID id, String name) {
	public static final Codec<Employer> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(Employer::id),
		Codec.STRING.optionalFieldOf("name", "").forGetter(Employer::name)
	).apply(i, Employer::new));
}
