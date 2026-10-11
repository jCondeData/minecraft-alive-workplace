package io.github.jcondedata.aliveworkplace.wonder;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One Wonder (M35), read from {@code data/<ns>/wonders/<id>.json} by {@link Wonders}: its name and description (lang
 * keys), its icon item, what a village needs before it may draw up the plans (the Master Architect's line is added by
 * the loader, so every Wonder has it), its stages, its powers, the most builders on its crew, and whether it's on.
 */
public record Wonder(ResourceLocation id, String name, String description, ResourceLocation icon, boolean enabled, int crew,
					 List<Condition> requirements, List<Stage> stages, List<Power> powers) {
	/** One stage: its name (a lang key), its blueprint parts with their offsets, and the goods the village must bring. */
	public record Stage(String name, List<Part> parts, List<Good> goods) {
		public Component nameText() {
			return Component.translatable(name);
		}
	}

	/** A blueprint part ({@code structure/wonder/<id>/stage_<n>_<part>.nbt}) and its offset from the Wonder's corner. */
	public record Part(ResourceLocation part, BlockPos offset) {
	}

	/**
	 * A goods line: an {@code item} or a {@code tag}, how many, the job that makes it (a profession id), and for a potion
	 * the kind ({@code potion}, optional).
	 */
	public record Good(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, int count, ResourceLocation job,
					   Optional<ResourceLocation> potion) {
	}

	/** A power: its type and its numbers, as written ({@code {"type": "beauty", "points": 8}}). */
	public record Power(String type, JsonObject numbers) {
	}

	public Component nameText() {
		return Component.translatable(name);
	}

	public Component descriptionText() {
		return Component.translatable(description);
	}
}
