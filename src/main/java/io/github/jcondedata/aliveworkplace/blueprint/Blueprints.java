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
		if (io.github.jcondedata.aliveworkplace.city.CaravanTrains.isTrainSite(id)) {
			return Component.translatable("blueprint.aliveworkplace.village_train"); // the village's own Create train (39.7b)
		}
		if (io.github.jcondedata.aliveworkplace.city.CaravanTrains.isTrackSite(id)) {
			return Component.translatable("blueprint.aliveworkplace.railway"); // track along a road to another village (39.7)
		}
		if (io.github.jcondedata.aliveworkplace.city.Roads.isSegment(id)) {
			return Component.translatable("blueprint.aliveworkplace.road"); // a road segment (27.15)
		}
		String key = "blueprint." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		return Component.translatableWithFallback(key, prettify(id));
	}

	/** The longest a blueprint's description may be (36.6): one plain sentence that fits a few lines of the Table's pane. */
	public static final int DESCRIPTION_MAX = 110;

	/**
	 * The lang key of the one-line description of {@code id} (36.6): {@code blueprint_desc.<namespace>.<path>}, the path's
	 * slashes as dots like the names'. A build in another style is described as its base; a village house's outside and a
	 * road segment have none (null). An imported build gets a key no lang file holds, so {@link #description} finds nothing.
	 */
	@org.jetbrains.annotations.Nullable
	public static String descriptionKey(ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		if (io.github.jcondedata.aliveworkplace.world.VillagePieces.outsideParts(base).isPresent()
			|| io.github.jcondedata.aliveworkplace.city.Roads.isSegment(base)) {
			return null;
		}
		return "blueprint_desc." + base.getNamespace() + "." + base.getPath().replace('/', '.');
	}

	/**
	 * The description the Blueprint Table shows under a build's size (36.6), or null when there is none ({@code has} says
	 * whether the language holds a key: an imported build's doesn't, and its pane shows nothing there).
	 */
	@org.jetbrains.annotations.Nullable
	public static Component description(ResourceLocation id, java.util.function.Predicate<String> has) {
		String key = descriptionKey(id);
		return key != null && has.test(key) ? Component.translatable(key) : null;
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
