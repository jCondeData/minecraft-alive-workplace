package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One Legend, as its file {@code data/<ns>/legends/<id>.json} describes them (M29 design note, section 2): their rarity,
 * trade, title, lore and guest names (lang keys), what the village must have, the ways they come, the luxury they like,
 * their powers, the Masterwork of an inspired Legend and their outfit. {@code arrive} and {@code masterwork} are kept as
 * read: the ways (29.7 to 29.10) read their own settings.
 */
public record Legend(ResourceLocation id, Rarity rarity, ResourceLocation job, String title, String lore, List<String> names,
					 List<Condition> conditions, List<JsonObject> arrive, Optional<String> luxury, List<Power> powers,
					 @Nullable JsonObject masterwork, Optional<ResourceLocation> outfit) {
	public Component titleText() {
		return Component.translatable(title);
	}

	public Component loreText() {
		return Component.translatable(lore);
	}

	/** The powers of one kind. */
	public <P extends Power> List<P> powers(Class<P> kind) {
		return powers.stream().filter(kind::isInstance).map(kind::cast).toList();
	}

	/** The arrival ways named {@code way} ({@code visit}, {@code found}, {@code born}, {@code inspired}). */
	public List<JsonObject> ways(String way) {
		return arrive.stream().filter(a -> way.equals(a.get("way").getAsString())).toList();
	}
}
