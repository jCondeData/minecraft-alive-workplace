package io.github.jcondedata.aliveworkplace.wonder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * One Wonder going up or finished in a village (M35), saved in {@link WonderManager}: its id, which Wonder, the
 * village's hall, the player who started it, the current stage (1 up; past the last stage once finished) and the days
 * it was begun and finished ({@code Chronicle.day}). 35.2 keeps what the caps and {@code /workplace wonders} read; 35.4
 * adds the placement, the Foundation Stone, the build sites of the current stage's parts and the builder it was handed to
 * (who takes on each next stage: a finished part's site is gone, and with it who built it). Every field but the first three has a default.
 */
public record WonderSite(UUID id, ResourceLocation wonder, BlockPos hall, Optional<UUID> owner, String ownerName, int stage,
						 long begun, Optional<Long> finished, Optional<BlueprintData.Placement> placement, Optional<BlockPos> stone,
						 List<UUID> parts, Optional<UUID> builder) {
	/** A site as 35.2 recorded it: no placement, no stone, no parts, no builder. */
	public WonderSite(UUID id, ResourceLocation wonder, BlockPos hall, Optional<UUID> owner, String ownerName, int stage, long begun,
					  Optional<Long> finished) {
		this(id, wonder, hall, owner, ownerName, stage, begun, finished, Optional.empty(), Optional.empty(), List.of(), Optional.empty());
	}

	public static final Codec<WonderSite> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(WonderSite::id),
		ResourceLocation.CODEC.fieldOf("wonder").forGetter(WonderSite::wonder),
		BlockPos.CODEC.fieldOf("hall").forGetter(WonderSite::hall),
		UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(WonderSite::owner),
		Codec.STRING.optionalFieldOf("ownerName", "").forGetter(WonderSite::ownerName),
		Codec.INT.optionalFieldOf("stage", 1).forGetter(WonderSite::stage),
		Codec.LONG.optionalFieldOf("begun", 0L).forGetter(WonderSite::begun),
		Codec.LONG.optionalFieldOf("finished").forGetter(WonderSite::finished),
		BlueprintData.Placement.CODEC.optionalFieldOf("placement").forGetter(WonderSite::placement),
		BlockPos.CODEC.optionalFieldOf("stone").forGetter(WonderSite::stone),
		UUIDUtil.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(WonderSite::parts),
		UUIDUtil.CODEC.optionalFieldOf("builder").forGetter(WonderSite::builder)
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
		return new WonderSite(id, wonder, hall, owner, ownerName, next, begun, finished, placement, stone, parts, builder);
	}

	WonderSite finishedOn(long day) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, Optional.of(day), placement, stone, parts, builder);
	}

	WonderSite placedAt(BlueprintData.Placement where, BlockPos stonePos) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, Optional.of(where), Optional.of(stonePos.immutable()), parts, builder);
	}

	WonderSite withBuilder(UUID who) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, Optional.of(who));
	}

	WonderSite withParts(List<UUID> sites) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, List.copyOf(sites), builder);
	}
}
