package io.github.jcondedata.aliveworkplace.client.guide;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guide.GuideBook;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Guide Book's pages, in reading order (ROADMAP 26.2a), with their words and pictures. The list and the chapters
 * live in {@link GuideBook} (core, so the GameTests open every page). Each page is an in-game screenshot
 * ({@code textures/gui/guide/<id>.png}, made by tools/guide/build.py from the screenshot scenes) with a title and a few
 * short steps from the language file ({@code guide.aliveworkplace.page.<id>.title} and {@code .text}). The Pokémon
 * chapter only shows with Cobblemon installed, the City Plan's page once 1.1's Steward is on.
 */
public final class GuidePages {
	/** The screenshots' size in pixels (drawn smaller, as big as the screen leaves room for). */
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
	public static final List<Page> ALL = GuideBook.ALL.stream().map(e -> new Page(e.id(), e.chapter())).toList();

	/** The chapters, in order. */
	public static final List<String> CHAPTERS = GuideBook.CHAPTERS;

	public static Component chapterName(String chapter) {
		return Component.translatable("guide.aliveworkplace.chapter." + chapter);
	}

	/** The pages this game shows: the Pokémon chapter only with Cobblemon, the City Plan's page once its expansion is on. */
	public static List<Page> shown() {
		return GuideBook.shown().stream().map(e -> new Page(e.id(), e.chapter())).toList();
	}

	private GuidePages() {
	}
}
