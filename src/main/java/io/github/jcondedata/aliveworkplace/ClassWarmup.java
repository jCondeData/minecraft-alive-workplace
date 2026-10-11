package io.github.jcondedata.aliveworkplace;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Loads the mod's classes ahead of use, on a background thread while the server starts (B103). The first time a class
 * is used the game loads it, and on a big pack every class loaded goes through each mod's class transformers first (the
 * Cobbleverse's LibJF reads the class and its interfaces), and on the pack's first day ticks of 50 to 150 ms were mostly
 * that, in a hall round, a farmer's first look at a field or a caravan's first save. Classes are only loaded, never
 * initialised, so no code of theirs runs early; the game still initialises each one the first time it's used. Mixins,
 * client code and tests are left alone, and so are the integrations of mods that aren't installed.
 */
public final class ClassWarmup {
	/** Packages never loaded ahead: mixins can't be loaded on their own, client code isn't on a server. */
	private static final Set<String> SKIPPED = Set.of("mixin", "client", "devclient", "test");
	/** Integrations ({@code compat.<mod id>.}): loaded ahead only when their mod is. */
	private static final String COMPAT = "compat";
	private static final String PACKAGE = "io.github.jcondedata.aliveworkplace.";
	private static boolean started;

	/** How many classes were loaded, how many couldn't be, and how long it took. */
	public record Result(int loaded, int failed, long millis) {
	}

	/** Starts the warm-up once per game (a single-player world opened twice starts it once). */
	public static synchronized void start() {
		if (started) {
			return;
		}
		started = true;
		Thread thread = new Thread(() -> {
			Result result = run(Platform.get().modRoots());
			AliveWorkplace.LOG.info("Loaded {} classes ahead of use in {} ms ({} skipped)", result.loaded(), result.millis(), result.failed());
		}, "Alive Workplace class warm-up");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	/** Loads (without initialising) every class of ours under {@code roots} that {@link #wanted} names. */
	public static Result run(List<Path> roots) {
		return run(roots, Platform.get()::isModLoaded);
	}

	/** {@link #run(List)}, with the integrations of the mods {@code modLoaded} says are installed. */
	public static Result run(List<Path> roots, Predicate<String> modLoaded) {
		long start = System.nanoTime();
		ClassLoader loader = ClassWarmup.class.getClassLoader();
		int loaded = 0;
		int failed = 0;
		for (String name : classNames(roots, modLoaded)) {
			try {
				Class.forName(name, false, loader);
				loaded++;
			} catch (Throwable e) { // a class that can't load now fails the same way when used: nothing to do here
				failed++;
				AliveWorkplace.LOG.debug("Class warm-up skipped {}: {}", name, e.toString());
			}
		}
		return new Result(loaded, failed, (System.nanoTime() - start) / 1_000_000);
	}

	/** The names of our classes under {@code roots} that are loaded ahead, with the integrations of the mods {@code modLoaded} names. */
	public static List<String> classNames(List<Path> roots, Predicate<String> modLoaded) {
		List<String> out = new ArrayList<>();
		for (Path root : roots) {
			try (Stream<Path> files = Files.walk(root)) {
				files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
					String rel = root.relativize(p).toString().replace('\\', '/');
					String name = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
					if (wanted(name, modLoaded)) {
						out.add(name);
					}
				});
			} catch (IOException | RuntimeException e) {
				AliveWorkplace.LOG.debug("Class warm-up couldn't list {}: {}", root, e.toString());
			}
		}
		return out;
	}

	/**
	 * Whether the class {@code name} is loaded ahead: ours, outside {@link #SKIPPED}, and in an integration only when
	 * {@code modLoaded} says its mod is installed.
	 */
	public static boolean wanted(String name, Predicate<String> modLoaded) {
		if (!name.startsWith(PACKAGE) || name.endsWith("package-info") || name.endsWith("module-info")) {
			return false;
		}
		String rest = name.substring(PACKAGE.length());
		int dot = rest.indexOf('.');
		if (dot < 0) {
			return true;
		}
		String top = rest.substring(0, dot);
		if (top.equals(COMPAT)) {
			String mod = rest.substring(dot + 1);
			int end = mod.indexOf('.');
			return end > 0 && modLoaded.test(mod.substring(0, end)); // compat.Compat itself is loaded at start anyway
		}
		return !SKIPPED.contains(top);
	}

	private ClassWarmup() {
	}
}
