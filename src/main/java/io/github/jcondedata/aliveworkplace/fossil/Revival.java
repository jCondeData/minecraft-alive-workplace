package io.github.jcondedata.aliveworkplace.fossil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * A fossil a Fossil Scientist is bringing back to life for a player: which fossil (Cobblemon's id), the fossil items it was
 * made from (for the messages), how long the work takes and how much of it is left (0: ready to hand over).
 */
public record Revival(UUID owner, String ownerName, ResourceLocation fossil, List<ResourceLocation> items, int total, int left) {
	public static final Codec<Revival> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("owner").forGetter(Revival::owner),
		Codec.STRING.optionalFieldOf("ownerName", "").forGetter(Revival::ownerName),
		ResourceLocation.CODEC.fieldOf("fossil").forGetter(Revival::fossil),
		ResourceLocation.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Revival::items),
		Codec.INT.fieldOf("total").forGetter(Revival::total),
		Codec.INT.fieldOf("left").forGetter(Revival::left)
	).apply(i, Revival::new));

	public boolean ready() {
		return left <= 0;
	}

	public Revival worked(int ticks) {
		return new Revival(owner, ownerName, fossil, items, total, Math.max(0, left - ticks));
	}

	/** How far along, 0 to 1. */
	public float progress() {
		return total <= 0 ? 1f : 1f - (float) left / total;
	}
}
