package io.github.jcondedata.aliveworkplace.wonder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * One Wonder going up or finished in a village (M35), saved in {@link WonderManager}: its id, which Wonder, the
 * village's hall, the player who started it, the current stage (1 up; past the last stage once finished) and the days
 * it was begun and finished ({@code Chronicle.day}). 35.2 keeps what the caps and {@code /workplace wonders} read; 35.4
 * adds the placement, the stone and the parts' build sites. Every field but the first three has a default.
 */
public record WonderSite(UUID id, ResourceLocation wonder, BlockPos hall, Optional<UUID> owner, String ownerName, int stage,
						 long begun, Optional<Long> finished) {
	public static final Codec<WonderSite> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(WonderSite::id),
		ResourceLocation.CODEC.fieldOf("wonder").forGetter(WonderSite::wonder),
		BlockPos.CODEC.fieldOf("hall").forGetter(WonderSite::hall),
		UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(WonderSite::owner),
		Codec.STRING.optionalFieldOf("ownerName", "").forGetter(WonderSite::ownerName),
		Codec.INT.optionalFieldOf("stage", 1).forGetter(WonderSite::stage),
		Codec.LONG.optionalFieldOf("begun", 0L).forGetter(WonderSite::begun),
		Codec.LONG.optionalFieldOf("finished").forGetter(WonderSite::finished)
	).apply(i, WonderSite::new));

	/** Whether it's finished. */
	public boolean done() {
		return finished.isPresent();
	}

	/** Whether it's still going up. */
	public boolean goingUp() {
		return finished.isEmpty();
	}

	WonderSite atStage(int next) {
		return new WonderSite(id, wonder, hall, owner, ownerName, next, begun, finished);
	}

	WonderSite finishedOn(long day) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, Optional.of(day));
	}
}
