package io.github.jcondedata.aliveworkplace.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * Attached to a builder villager: the build site it works on. {@code helper} is true when it is
 * helping another builder's site rather than leading its own. {@code bench} (B645) is the lead's bench a helper
 * joined at: where its bag goes back to when the site is already gone by the time it stops (the build ended while the
 * helper was unloaded or out of reach). Empty for a lead, a Wonder's crew and jobs saved before the field existed.
 */
public record BuilderJob(UUID siteId, boolean helper, Optional<BlockPos> bench) {
	public static final Codec<BuilderJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("site").forGetter(BuilderJob::siteId),
		Codec.BOOL.optionalFieldOf("helper", false).forGetter(BuilderJob::helper),
		BlockPos.CODEC.optionalFieldOf("bench").forGetter(BuilderJob::bench)
	).apply(i, BuilderJob::new));

	public BuilderJob(UUID siteId) {
		this(siteId, false);
	}

	public BuilderJob(UUID siteId, boolean helper) {
		this(siteId, helper, Optional.empty());
	}

	/** A helper on {@code site}, remembering the lead's bench. */
	public static BuilderJob helping(BuildSite site) {
		return new BuilderJob(site.id(), true, Optional.ofNullable(site.bench()));
	}
}
