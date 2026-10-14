package io.github.jcondedata.aliveworkplace.compat.ftbquests;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The questbook: our FTB Quests chapters, start to finish (tools/questbook/generate.py writes them into the jar's
 * {@code aliveworkplace/questbook/}). FTB Quests reads chapters only from the server's
 * {@code config/ftbquests/quests/chapters/}, so on server start, when FTB Quests is installed, this copies in each of
 * ours that isn't there yet. Data only: nothing here touches FTB Quests' classes, and without it nothing runs.
 *
 * <p>Never overwrites: a chapter file that is already there is left alone (the owner may have edited it, and FTB
 * Quests itself rewrites the files when it saves), and {@code config/aliveworkplace/questbook.txt} remembers every
 * chapter ever installed, so one the owner deleted stays deleted. Only chapters new to that list are copied: those of an
 * expansion that has just been switched on, or added by a later version. FTB Quests loads the folder after the server
 * has started, so chapters copied here show up on the same start.
 */
public final class FtbQuestsBook {
	/** Where the chapters are in our jar. */
	static final String RESOURCES = "/aliveworkplace/questbook/";
	/** The marker's first line; a later format can read an older marker by it. */
	static final String MARKER_HEADER = "# Alive Workplace questbook v1: chapters already installed in config/ftbquests/quests/chapters (one per line). "
		+ "Delete a line to have that chapter installed again on the next start.";

	/** One of our chapter files, with the expansion milestone it waits for (or null) and the mods it needs. */
	public record Chapter(String file, String milestone, List<String> mods) {
	}

	public static void init() {
		Platform.get().onServerStarting(server -> {
			try {
				int copied = install(Platform.get().configDir(), chapter -> chapter.milestone() == null || Expansions.isOpen(chapter.milestone()),
					modId -> Platform.get().isModLoaded(modId));
				if (copied > 0) {
					AliveWorkplace.LOG.info("Alive Workplace: added {} questbook chapter(s) to FTB Quests", copied);
				}
			} catch (IOException | RuntimeException e) {
				AliveWorkplace.LOG.warn("Alive Workplace: couldn't add the questbook to FTB Quests: {}", e.toString());
			}
		});
	}

	/**
	 * Copies each of our chapters whose expansion is {@code on} and whose mods are {@code loaded} into
	 * {@code config/ftbquests/quests/chapters}, unless it was installed before or a file of its name is already there.
	 * Returns how many it copied.
	 */
	public static int install(Path configDir, Predicate<Chapter> on, Predicate<String> loaded) throws IOException {
		Path chapters = configDir.resolve("ftbquests").resolve("quests").resolve("chapters");
		Path marker = configDir.resolve(AliveWorkplace.MOD_ID).resolve("questbook.txt");
		Set<String> installed = readMarker(marker);
		boolean changed = false;
		int copied = 0;
		for (Chapter chapter : manifest()) {
			if (installed.contains(chapter.file()) || !on.test(chapter) || !chapter.mods().stream().allMatch(loaded)) {
				continue;
			}
			Path target = chapters.resolve(chapter.file());
			if (!Files.exists(target)) {
				Files.createDirectories(chapters);
				try (InputStream in = resource(chapter.file())) {
					Files.copy(in, target);
				}
				copied++;
			}
			installed.add(chapter.file());
			changed = true;
		}
		if (changed) {
			Files.createDirectories(marker.getParent());
			List<String> lines = new ArrayList<>();
			lines.add(MARKER_HEADER);
			lines.addAll(installed);
			Files.write(marker, lines, StandardCharsets.UTF_8);
		}
		return copied;
	}

	/** Every chapter we ship, in order (the jar's manifest.txt). */
	public static List<Chapter> manifest() throws IOException {
		List<Chapter> result = new ArrayList<>();
		String text;
		try (InputStream in = resource("manifest.txt")) {
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		for (String line : text.split("\n")) {
			line = line.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] parts = line.split("\\s+");
			if (parts.length != 3) {
				throw new IOException("bad questbook manifest line: " + line);
			}
			result.add(new Chapter(parts[0], parts[1].equals("-") ? null : parts[1],
				parts[2].equals("-") ? List.of() : List.of(parts[2].split(","))));
		}
		return result;
	}

	/** One of our questbook files from the jar. */
	public static InputStream resource(String file) throws IOException {
		InputStream in = FtbQuestsBook.class.getResourceAsStream(RESOURCES + file);
		if (in == null) {
			throw new IOException("the questbook file " + file + " is missing from the jar");
		}
		return in;
	}

	private static Set<String> readMarker(Path marker) throws IOException {
		Set<String> installed = new LinkedHashSet<>();
		if (Files.exists(marker)) {
			for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
				line = line.strip();
				if (!line.isEmpty() && !line.startsWith("#")) {
					installed.add(line);
				}
			}
		}
		return installed;
	}

	private FtbQuestsBook() {
	}
}
