package io.github.jcondedata.aliveworkplace.blueprint;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Small naming helpers for blueprints. */
public final class Blueprints {
	/**
	 * Human name for a structure id. Uses the lang key {@code blueprint.<namespace>.<path>} when one
	 * exists, otherwise turns {@code houses/big_barn} into "Big Barn".
	 */
	public static Component displayName(ResourceLocation id) {
		java.util.Optional<BlueprintStyles.Styled> styled = BlueprintStyles.parse(id);
		if (styled.isPresent()) {
			String style = styled.get().style();
			return Component.translatable("blueprint.aliveworkplace.styled", displayName(styled.get().base()),
				Component.translatableWithFallback("style.aliveworkplace." + style, BlueprintStyles.prettify(style)));
		}
		java.util.Optional<String[]> outside = io.github.jcondedata.aliveworkplace.world.VillagePieces.outsideParts(id);
		if (outside.isPresent()) {
			// A village house's new outside (23.10a): "Guard House: Desert outside"
			return Component.translatable("blueprint.aliveworkplace.outside",
				io.github.jcondedata.aliveworkplace.world.VillagePieces.houseName(outside.get()[1]),
				io.github.jcondedata.aliveworkplace.world.VillagePieces.styleName(outside.get()[0]));
		}
		String key = "blueprint." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		return Component.translatableWithFallback(key, prettify(id));
	}

	public static String prettify(ResourceLocation id) {
		String path = id.getPath();
		String last = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder out = new StringBuilder();
		for (String word : last.split("[_\\-]+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
		}
		return out.isEmpty() ? path : out.toString();
	}

	private Blueprints() {
	}
}
