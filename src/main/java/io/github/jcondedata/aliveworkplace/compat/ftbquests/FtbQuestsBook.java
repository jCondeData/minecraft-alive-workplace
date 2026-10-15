package io.github.jcondedata.aliveworkplace.compat.ftbquests;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The questbook: our FTB Quests chapters, start to finish (tools/questbook/generate.py writes them into the jar's
 * {@code aliveworkplace/questbook/}). FTB Quests reads chapters only from the server's
 * {@code config/ftbquests/quests/chapters/}, so on server start, when FTB Quests is installed, this copies ours in.
 * Data only: nothing here touches FTB Quests' classes, and without it nothing runs.
 *
 * <p>Our chapters ({@code aliveworkplace_*.snbt}, with our object ids) are ours: {@code config/aliveworkplace/questbook.txt}
 * records every chapter ever installed with a SHA-256 of what we last wrote, and on each start a chapter whose shipped
 * version differs from that record is overwritten. The file itself isn't compared: FTB Quests rewrites every chapter it
 * saves (reformatted, its titles and descriptions moved to {@code quests/lang/<locale>.snbt}), so a comparison would
 * take every chapter for an owner's edit and no update would ever arrive. Whenever we write a chapter, the lang
 * entries of its objects ({@code <type>.<ID>.<field>}, FTB Quests 2101's TranslationManager.makeKey) are removed
 * from every lang file, so the new text in the chapter is the one FTB Quests reads; no other entry is touched. A
 * chapter the owner deleted stays deleted (a line in the marker and no file); with {@code questbookKeepEdits} on, every
 * existing file of ours is left as it is. FTB Quests loads its folder on SERVER_STARTED (FTBQuestsEventHandler,
 * 1.21.1/main), after this runs on SERVER_STARTING, so what is written here shows up on the same start.
 */
public final class FtbQuestsBook {
	/** Where the chapters are in our jar. */
	static final String RESOURCES = "/aliveworkplace/questbook/";
	/** The marker's first line; a later format can read an older marker by it. */
	static final String MARKER_HEADER = "# Alive Workplace questbook v2: chapters already installed in config/ftbquests/quests/chapters, "
		+ "each with the SHA-256 of what we last wrote. A chapter whose new version differs is overwritten on start (questbookKeepEdits "
		+ "in aliveworkplace.json keeps your files); a deleted chapter stays deleted; delete a line to have that chapter installed again.";
	/** Recorded for a chapter from a v1 marker (no hash): its file is updated when it's there. */
	static final String NOT_OURS = "-";
	/** Recorded by earlier versions for a chapter they took for the owner's edit (most were FTB Quests' own rewrites). */
	static final String EDITED = "edited";
	/** An FTB Quests object id, as chapter files and lang keys write it ({@code QuestObjectBase.getCodeString}). */
	private static final Pattern OBJECT_ID = Pattern.compile("\\bid:\\s*\"([0-9A-F]{16})\"");
	/** A lang key: {@code <object type>.<id>.<field>}, such as {@code quest.44C38D2C09D0F61A.quest_desc}. */
	private static final Pattern LANG_KEY = Pattern.compile("^[a-z_]+\\.([0-9A-F]{16})\\.[a-z_]+$");

	/** One of our chapter files, with the expansion milestone it waits for (or null) and the mods it needs. */
	public record Chapter(String file, String milestone, List<String> mods) {
	}

	public static void init() {
		Platform.get().onServerStarting(server -> {
			try {
				Path config = Platform.get().configDir();
				install(config, chapter -> chapter.milestone() == null || Expansions.isOpen(chapter.milestone()),
					modId -> Platform.get().isModLoaded(modId), WorkplaceConfig.load(config).questbookKeepEdits);
			} catch (IOException | RuntimeException e) {
				AliveWorkplace.LOG.warn("Alive Workplace: couldn't add the questbook to FTB Quests: {}", e.toString());
			}
		});
	}

	/** {@link #install(Path, Predicate, Predicate, boolean)} with {@code questbookKeepEdits} off. */
	public static int install(Path configDir, Predicate<Chapter> on, Predicate<String> loaded) throws IOException {
		return install(configDir, on, loaded, false);
	}

	/**
	 * Copies each of our chapters whose expansion is {@code on} and whose mods are {@code loaded} into
	 * {@code config/ftbquests/quests/chapters} if it was never installed, and overwrites an installed one that is still
	 * there when the jar's version differs from the one recorded (unless {@code keepEdits}). Returns how many files it wrote.
	 */
	public static int install(Path configDir, Predicate<Chapter> on, Predicate<String> loaded, boolean keepEdits) throws IOException {
		Path chapters = configDir.resolve("ftbquests").resolve("quests").resolve("chapters");
		Path marker = configDir.resolve(AliveWorkplace.MOD_ID).resolve("questbook.txt");
		boolean hashless = Files.exists(marker) && !Files.readString(marker, StandardCharsets.UTF_8).startsWith("# Alive Workplace questbook v2");
		Map<String, String> installed = readMarker(marker);
		boolean changed = hashless;
		int added = 0;
		int updated = 0;
		Set<String> ourIds = new HashSet<>();
		for (Chapter chapter : manifest()) {
			Path target = chapters.resolve(chapter.file());
			byte[] ours;
			try (InputStream in = resource(chapter.file())) {
				ours = in.readAllBytes();
			}
			String oursHash = sha256(ours);
			String recorded = installed.get(chapter.file());
			boolean exists = Files.exists(target);
			if (recorded != null) {
				boolean freshCopy = !exists && recorded.equals(EDITED); // earlier versions said: delete an edited chapter for a fresh copy
				if ((!exists && !freshCopy) || recorded.equals(oursHash) || (exists && keepEdits)) {
					continue; // deleted by the owner, already the jar's version, or kept by questbookKeepEdits
				}
			} else if (!on.test(chapter) || !chapter.mods().stream().allMatch(loaded)) {
				continue;
			} else if (exists && keepEdits) {
				installed.put(chapter.file(), NOT_OURS); // a file of our name already there, kept
				changed = true;
				continue;
			}
			Files.createDirectories(chapters);
			Files.write(target, ours);
			installed.put(chapter.file(), oursHash);
			ourIds.addAll(objectIds(new String(ours, StandardCharsets.UTF_8)));
			if (exists) {
				updated++;
			} else {
				added++;
			}
			changed = true;
		}
		int langRemoved = ourIds.isEmpty() ? 0 : removeLangEntries(configDir.resolve("ftbquests").resolve("quests").resolve("lang"), ourIds);
		if (changed) {
			Files.createDirectories(marker.getParent());
			List<String> lines = new ArrayList<>();
			lines.add(MARKER_HEADER);
			installed.forEach((file, hash) -> lines.add(file + " " + hash));
			Files.write(marker, lines, StandardCharsets.UTF_8);
		}
		AliveWorkplace.LOG.info("Alive Workplace: questbook: {} FTB Quests chapter(s) added, {} updated{}{}", added, updated,
			langRemoved > 0 ? ", " + langRemoved + " old lang entr" + (langRemoved == 1 ? "y" : "ies") + " removed" : "",
			keepEdits ? " (questbookKeepEdits on: existing files kept)" : "");
		return added + updated;
	}

	/** Every FTB Quests object id in a chapter file: the chapter's, its quests', tasks' and rewards'. */
	public static Set<String> objectIds(String chapterText) {
		Set<String> ids = new HashSet<>();
		Matcher m = OBJECT_ID.matcher(chapterText);
		while (m.find()) {
			ids.add(m.group(1));
		}
		return ids;
	}

	/** Removes from each {@code <locale>.snbt} in {@code langFolder} the entries of {@code ids}' objects. Returns how many. */
	static int removeLangEntries(Path langFolder, Set<String> ids) throws IOException {
		if (!Files.isDirectory(langFolder)) {
			return 0;
		}
		int removed = 0;
		List<Path> files;
		try (Stream<Path> s = Files.list(langFolder)) {
			files = s.filter(p -> p.getFileName().toString().matches("\\w+\\.snbt") && Files.isRegularFile(p)).sorted().toList();
		}
		for (Path file : files) {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			int[] count = {0};
			String edited = removeLangEntries(text, key -> {
				Matcher m = LANG_KEY.matcher(key);
				return m.matches() && ids.contains(m.group(1));
			}, count);
			if (count[0] > 0) {
				Files.writeString(file, edited, StandardCharsets.UTF_8);
				removed += count[0];
			}
		}
		return removed;
	}

	/**
	 * The SNBT compound {@code text} without its top-level entries whose key {@code drop} accepts (counted in
	 * {@code removed[0]}); every other byte is kept. Reads FTB Library's style (one entry per line, no commas, lists over
	 * several lines) and vanilla's (commas, quoted keys). Text that isn't a compound comes back as it is.
	 */
	public static String removeLangEntries(String text, Predicate<String> drop, int[] removed) {
		int open = text.indexOf('{');
		if (open < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.substring(0, open + 1));
		int n = text.length();
		int i = open + 1;
		boolean first = true;
		while (i < n) {
			int gap = i;
			while (i < n && (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == ',')) {
				i++;
			}
			if (i >= n || text.charAt(i) == '}') {
				out.append(text, gap, n);
				return out.toString();
			}
			int keyStart = i;
			String key;
			char c = text.charAt(i);
			if (c == '"' || c == '\'') {
				i = skipString(text, i);
				key = unquote(text.substring(keyStart, i));
			} else {
				while (i < n && text.charAt(i) != ':' && !Character.isWhitespace(text.charAt(i))) {
					i++;
				}
				key = text.substring(keyStart, i);
			}
			while (i < n && text.charAt(i) != ':') {
				i++;
			}
			if (i >= n) {
				return text; // not SNBT we can read: leave the file alone
			}
			i = skipValue(text, i + 1);
			if (drop.test(key)) {
				removed[0]++;
			} else {
				String before = text.substring(gap, keyStart);
				out.append(first ? before.replace(",", "") : before).append(text, keyStart, i);
				first = false;
			}
		}
		return text; // no closing brace: leave the file alone
	}

	/** The index just past the SNBT value starting at (or after whitespace from) {@code i}. */
	private static int skipValue(String text, int i) {
		int n = text.length();
		while (i < n && Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		if (i >= n) {
			return n;
		}
		char c = text.charAt(i);
		if (c == '"' || c == '\'') {
			return skipString(text, i);
		}
		if (c == '{' || c == '[') {
			int depth = 0;
			while (i < n) {
				char d = text.charAt(i);
				if (d == '"' || d == '\'') {
					i = skipString(text, i);
					continue;
				}
				if (d == '{' || d == '[') {
					depth++;
				} else if (d == '}' || d == ']') {
					depth--;
					if (depth == 0) {
						return i + 1;
					}
				}
				i++;
			}
			return n;
		}
		while (i < n && ",}]".indexOf(text.charAt(i)) < 0 && !Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		return i;
	}

	/** The index just past the quoted string starting at {@code i}. */
	private static int skipString(String text, int i) {
		char quote = text.charAt(i++);
		while (i < text.length()) {
			char c = text.charAt(i++);
			if (c == '\\') {
				i++;
			} else if (c == quote) {
				return i;
			}
		}
		return text.length();
	}

	private static String unquote(String quoted) {
		return quoted.substring(1, Math.max(1, quoted.length() - 1)).replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
	}

	/** The SHA-256 of some bytes, in lowercase hex, as the marker records it. */
	public static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // every JVM has SHA-256
		}
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

	/** File to recorded hash; a v1 line (no hash) reads as {@link #NOT_OURS}. */
	private static Map<String, String> readMarker(Path marker) throws IOException {
		Map<String, String> installed = new LinkedHashMap<>();
		if (Files.exists(marker)) {
			for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
				line = line.strip();
				if (!line.isEmpty() && !line.startsWith("#")) {
					String[] parts = line.split("\\s+", 2);
					installed.put(parts[0], parts.length == 2 ? parts[1] : NOT_OURS);
				}
			}
		}
		return installed;
	}

	private FtbQuestsBook() {
	}
}
