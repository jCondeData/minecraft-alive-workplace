package io.github.jcondedata.aliveworkplace.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** Attached to a builder villager: the build site it is assigned to. */
public record BuilderJob(UUID siteId) {
	public static final Codec<BuilderJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("site").forGetter(BuilderJob::siteId)
	).apply(i, BuilderJob::new));
}
