package io.github.jcondedata.aliveworkplace.compat.ftbquests;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The questbook: our FTB Quests chapters, start to finish (tools/questbook/generate.py writes them into the jar's
 * {@code aliveworkplace/questbook/}). FTB Quests reads chapters only from the server's
 * {@code config/ftbquests/quests/chapters/}, so on server start, when FTB Quests is installed, this copies in each of
 * ours that isn't there yet. Data only: nothing here touches FTB Quests' classes, and without it nothing runs.
 *
 * <p>Updates only what nobody touched: {@code config/aliveworkplace/questbook.txt} records every chapter ever
 * installed with a SHA-256 of exactly the bytes we wrote. On each start a chapter whose file still has that hash is
 * replaced with the jar's version (so a new layout reaches servers that have the old one); a file whose hash differs
 * was edited by the owner (or rewritten by FTB Quests when it saved) and is left alone, and deleting it later brings
 * back a fresh copy; an unedited chapter the owner deleted stays deleted. Chapters new to the list are copied: those of an expansion that has just been switched on, or added by
 * a later version. FTB Quests loads the folder after the server has started, so chapters copied here show up on the
 * same start. The chapter files themselves are FTB Quests' own format, untouched.
 */
public final class FtbQuestsBook {
	/** Where the chapters are in our jar. */
	static final String RESOURCES = "/aliveworkplace/questbook/";
	/** The marker's first line; a later format can read an older marker by it. */
	static final String MARKER_HEADER = "# Alive Workplace questbook v2: chapters already installed in config/ftbquests/quests/chapters, "
		+ "each with the SHA-256 of what we last wrote (\"-\": a file that was already there; \"edited\": edited by you, never updated, "
		+ "delete the file for a fresh copy). A file that still matches is updated on start; delete a line to have that chapter installed again.";
	/** Recorded for a chapter we must never write: a file of its name was already there (or, from a v1 marker, it was deleted). */
	static final String NOT_OURS = "-";
	/** Recorded for a chapter the owner edited: kept as it is, and deleting the file brings back a fresh copy. */
	static final String EDITED = "edited";
	/**
	 * Every chapter file we shipped before the marker had hashes (the layout of commit c66a0461, the only one with a v1
	 * marker). A v1 marker gives no hash, so a file byte-identical to one of these, or to the jar's current version, counts
	 * as untouched; anything else counts as the owner's. New layouts don't belong here: v2 markers record their own hashes.
	 */
	private static final Set<String> SHIPPED_BEFORE_HASHES = Set.of(
		"7d8655cb5450f258b98898897c068212ecfc2ca9ba85716cbf8ddd75153ddef7", // 01_getting_started
		"8c3a29b31e6ebe0f566d52223b536865bde17419deb75f17ba18899d586de375", // 02_builder
		"35044ef4d9615bea44f284bedb9efacc75a7b7035e9d48c93a512314aed088e7", // 03_jobs
		"73bc79bff0bf48402ce102ee229322677c66b6b72bb21b6c4875f6b0f19ffb88", // 04_village_hall
		"d8adb000576942f998980f66d4d7981e615e310deef1ed849bf5951e23c5d527", // 05_pokemon_village
		"70b0d1cfe720287d4557322e6d55f4baf44eb1be163ea78990874189a180322e", // 11_steward
		"5d66233b51a0f748c93d881b419ed759024f2fe5bcd240eb8044822b9df67faa", // 12_pokemon_jobs
		"bd154b16311ac47ddd73143f8dc011a0d37dd5246892b05d245a76ac0189595b", // 13_legends
		"763f62190b3c1081b532649b788a51ab91beec7fd54cb5a1e52a5261584bda7a", // 14_edicts
		"757a28d3685f05202260e8ba0f9b1ee2df69bbfdf22e63fa609e0fa32b29f192", // 15_story_arcs
		"16933d9ba01ca25e5d5060abccc920a31119421745b1f198acd59e19df6161c7", // 16_threats
		"5bf327751a61c2ca3409a6efedb3e3406760f7d12552835f37814e7931e60e0e", // 17_realms
		"8c01a702b7a80224f61112188a43d1d2e39e14d1f1d0be97592dc0a181fdbced", // 18_classes
		"5519d8ca40e4cf07579a6aa1df2dd39b17959158e1e18be2d2424cb0ebcaee94"); // 20_wonders

	/** One of our chapter files, with the expansion milestone it waits for (or null) and the mods it needs. */
	public record Chapter(String file, String milestone, List<String> mods) {
	}

	public static void init() {
		Platform.get().onServerStarting(server -> {
			try {
				int copied = install(Platform.get().configDir(), chapter -> chapter.milestone() == null || Expansions.isOpen(chapter.milestone()),
					modId -> Platform.get().isModLoaded(modId));
				if (copied > 0) {
					AliveWorkplace.LOG.info("Alive Workplace: added or updated {} questbook chapter(s) in FTB Quests", copied);
				}
			} catch (IOException | RuntimeException e) {
				AliveWorkplace.LOG.warn("Alive Workplace: couldn't add the questbook to FTB Quests: {}", e.toString());
			}
		});
	}

	/**
	 * Copies each of our chapters whose expansion is {@code on} and whose mods are {@code loaded} into
	 * {@code config/ftbquests/quests/chapters} if it was never installed and no file of its name is there, and replaces
	 * an installed one whose file still matches the hash we recorded. Returns how many files it wrote.
	 */
	public static int install(Path configDir, Predicate<Chapter> on, Predicate<String> loaded) throws IOException {
		Path chapters = configDir.resolve("ftbquests").resolve("quests").resolve("chapters");
		Path marker = configDir.resolve(AliveWorkplace.MOD_ID).resolve("questbook.txt");
		boolean hashless = Files.exists(marker) && !Files.readString(marker, StandardCharsets.UTF_8).startsWith("# Alive Workplace questbook v2");
		Map<String, String> installed = readMarker(marker);
		boolean changed = hashless;
		int written = 0;
		for (Chapter chapter : manifest()) {
			Path target = chapters.resolve(chapter.file());
			byte[] ours;
			try (InputStream in = resource(chapter.file())) {
				ours = in.readAllBytes();
			}
			String oursHash = sha256(ours);
			if (installed.containsKey(chapter.file())) {
				String recorded = installed.get(chapter.file());
				if (recorded.equals(EDITED) && !Files.exists(target)) {
					// The owner deleted a chapter they had edited: that asks for a fresh copy.
					Files.createDirectories(chapters);
					Files.write(target, ours);
					installed.put(chapter.file(), oursHash);
					written++;
					changed = true;
					continue;
				}
				if (!Files.exists(target) || recorded.equals(oursHash) || (!hashless && (recorded.equals(NOT_OURS) || recorded.equals(EDITED)))) {
					continue; // deleted by the owner, already the jar's version, or the owner's file
				}
				String current = sha256(Files.readAllBytes(target));
				boolean untouched = hashless ? current.equals(oursHash) || SHIPPED_BEFORE_HASHES.contains(current) : current.equals(recorded);
				if (untouched) {
					if (!current.equals(oursHash)) {
						Files.write(target, ours);
						written++;
					}
					installed.put(chapter.file(), oursHash);
				} else {
					// Logged once: from then on the file is the owner's and never updated (deleting it gets a fresh copy).
					AliveWorkplace.LOG.info("Alive Workplace: kept your edited questbook chapter {} (delete it to get the new version)", chapter.file());
					installed.put(chapter.file(), EDITED);
				}
				changed = true;
				continue;
			}
			if (!on.test(chapter) || !chapter.mods().stream().allMatch(loaded)) {
				continue;
			}
			if (Files.exists(target)) {
				installed.put(chapter.file(), NOT_OURS);
			} else {
				Files.createDirectories(chapters);
				Files.write(target, ours);
				installed.put(chapter.file(), oursHash);
				written++;
			}
			changed = true;
		}
		if (changed) {
			Files.createDirectories(marker.getParent());
			List<String> lines = new ArrayList<>();
			lines.add(MARKER_HEADER);
			installed.forEach((file, hash) -> lines.add(file + " " + hash));
			Files.write(marker, lines, StandardCharsets.UTF_8);
		}
		return written;
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

	/** File to recorded hash; a v1 line (no hash) reads as {@link #NOT_OURS} and is migrated by {@link #install}. */
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
