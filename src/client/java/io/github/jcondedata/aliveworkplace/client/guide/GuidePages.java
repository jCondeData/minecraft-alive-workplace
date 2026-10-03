package io.github.jcondedata.aliveworkplace.client.guide;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Guide Book's pages, in reading order (ROADMAP 26.2a). Each page is an in-game screenshot
 * ({@code textures/gui/guide/<id>.png}, made by tools/guide/build.py from the screenshot scenes) with a title and a few
 * short steps from the language file ({@code guide.aliveworkplace.page.<id>.title} and {@code .text}). The Pokémon
 * chapter only shows with Cobblemon installed.
 */
public final class GuidePages {
	/** The screenshots' size in pixels (drawn smaller, at {@link GuideScreen#IMAGE_W} by {@link GuideScreen#IMAGE_H}). */
	public static final int TEXTURE_W = 384;
	public static final int TEXTURE_H = 216;

	public record Page(String id, String chapter) {
		public Component title() {
			return Component.translatable("guide.aliveworkplace.page." + id + ".title");
		}

		public Component text() {
			return Component.translatable("guide.aliveworkplace.page." + id + ".text");
		}

		public ResourceLocation image() {
			return AliveWorkplace.id("textures/gui/guide/" + id + ".png");
		}

		public Component chapterName() {
			return GuidePages.chapterName(chapter);
		}
	}

	/** Every page, chapter by chapter. */
	public static final List<Page> ALL = List.of(
		new Page("welcome", "start"), new Page("camp", "start"), new Page("jobs", "start"), new Page("status", "start"),
		new Page("table", "builders"), new Page("place", "builders"), new Page("stock", "builders"), new Page("build", "builders"),
		new Page("styles", "builders"), new Page("library", "builders"), new Page("shapes", "builders"),
		new Page("hall", "village"), new Page("people", "village"), new Page("requests", "village"), new Page("storehouse", "village"),
		new Page("villages", "village"),
		new Page("miner", "gathering"), new Page("lumberjack", "gathering"), new Page("farmer", "gathering"),
		new Page("orchard", "gathering"), new Page("fisher", "gathering"), new Page("beekeeper", "gathering"),
		new Page("carpenter", "making"), new Page("chef", "making"), new Page("mail", "making"), new Page("shop", "making"),
		new Page("ferry", "making"),
		new Page("guard", "safety"), new Page("training", "safety"), new Page("nurse", "safety"),
		new Page("trainer", "pokemon"), new Page("tutor", "pokemon"), new Page("ball_smith", "pokemon"), new Page("trader", "pokemon"),
		new Page("fossil", "pokemon"));

	/** The chapters, in order. */
	public static final List<String> CHAPTERS = List.of("start", "builders", "village", "gathering", "making", "safety", "pokemon");

	public static Component chapterName(String chapter) {
		return Component.translatable("guide.aliveworkplace.chapter." + chapter);
	}

	/** The pages this game shows: the Pokémon chapter only with Cobblemon. */
	public static List<Page> shown() {
		boolean cobblemon = Platform.get().isModLoaded("cobblemon");
		return ALL.stream().filter(p -> cobblemon || !p.chapter().equals("pokemon")).toList();
	}

	private GuidePages() {
	}
}
