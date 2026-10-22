package io.github.jcondedata.aliveworkplace.compat.mobsbegone;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Foes;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MobsBeGone's spawn blocklist (B301), read from its data file, never its classes: {@code config/mobsbegone-blacklist.json}
 * names the entity types it discards when they're added (Cobbleverse ships it with zombies, skeletons, spiders and nearly
 * every other vanilla mob on it, B208). It fills {@link Foes#BLOCKLIST}, so the hall stops offering "Clear out 8 monsters"
 * where none can come. Every quoted entity id in the file counts ({@code "zombie"} is {@code minecraft:zombie}), so it
 * reads whatever the file's layout. The file is read again when it changes; no file: nothing is refused.
 */
public final class MobsBeGoneBlocklist {
	/** The file, in the game's config folder. */
	public static final String FILE = "mobsbegone-blacklist.json";
	private static final Pattern QUOTED = Pattern.compile("\"([a-z0-9_.\\-]+(?::[a-z0-9_./\\-]+)?)\"");

	private static volatile long readAt = Long.MIN_VALUE;
	private static volatile Set<String> ids = Set.of();
	private static boolean logged;

	/** Called once at startup: fills the blocklist extension point (it reads the file only when asked). */
	public static void init() {
		Foes.BLOCKLIST.register("mobsbegone", id -> refused().contains(id.toString()));
	}

	/** The entity ids the file names now (read again when it changed). */
	static synchronized Set<String> refused() {
		Path file = Platform.get().configDir().resolve(FILE);
		long modified;
		try {
			modified = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
		} catch (IOException e) {
			modified = -1;
		}
		if (modified != readAt) {
			readAt = modified;
			try {
				ids = modified < 0 ? Set.of() : parse(Files.readString(file));
			} catch (IOException | RuntimeException e) {
				ids = Set.of();
				AliveWorkplace.LOG.warn("Alive Workplace: can't read MobsBeGone's {}: {}", file, e.toString());
			}
			if (!ids.isEmpty() && !logged) {
				logged = true;
				AliveWorkplace.LOG.info("Alive Workplace: MobsBeGone's blocklist names {} entity types; village quests to clear out monsters "
					+ "follow it", ids.size());
			}
		}
		return ids;
	}

	/** Every quoted id in {@code json}, with {@code minecraft:} added where it names no namespace. */
	public static Set<String> parse(String json) {
		Set<String> out = new LinkedHashSet<>();
		Matcher m = QUOTED.matcher(json);
		while (m.find()) {
			String id = m.group(1);
			out.add(id.contains(":") ? id : "minecraft:" + id);
		}
		return out;
	}

	private MobsBeGoneBlocklist() {
	}
}
