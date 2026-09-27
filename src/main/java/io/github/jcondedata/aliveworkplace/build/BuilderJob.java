package io.github.jcondedata.aliveworkplace.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Attached to a builder villager: the build site it works on. {@code helper} is true when it is
 * helping another builder's site rather than leading its own.
 */
public record BuilderJob(UUID siteId, boolean helper) {
	public static final Codec<BuilderJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("site").forGetter(BuilderJob::siteId),
		Codec.BOOL.optionalFieldOf("helper", false).forGetter(BuilderJob::helper)
	).apply(i, BuilderJob::new));

	public BuilderJob(UUID siteId) {
		this(siteId, false);
	}
}
