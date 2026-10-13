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
 * {@code villageColours} (the file's {@code "village_colours": true}, the Stadium's seats, 35.23): its red and white
 * blocks are built in the village's two banner colours when it has a Village Banner ({@link WonderStages}).
 */
public record Wonder(ResourceLocation id, String name, String description, ResourceLocation icon, boolean enabled, int crew,
					 List<Condition> requirements, List<Stage> stages, List<Power> powers, boolean villageColours) {
	public Wonder(ResourceLocation id, String name, String description, ResourceLocation icon, boolean enabled, int crew,
				  List<Condition> requirements, List<Stage> stages, List<Power> powers) {
		this(id, name, description, icon, enabled, crew, requirements, stages, powers, false);
	}

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

	/**
	 * A power: its type and its numbers, as written ({@code {"type": "beauty", "points": 8}}), and the power its type's
	 * reader made of them (35.8, {@link io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers}).
	 */
	public record Power(String type, JsonObject numbers, io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower power) {
		/** A power read from its numbers (its {@code type} added back for the reader). */
		public Power(String type, JsonObject numbers) {
			this(type, numbers, read(type, numbers));
		}

		private static io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower read(String type, JsonObject numbers) {
			JsonObject json = numbers.deepCopy();
			json.addProperty("type", type);
			return io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers.parse(json);
		}
	}

	public Component nameText() {
		return Component.translatable(name);
	}

	public Component descriptionText() {
		return Component.translatable(description);
	}
}
