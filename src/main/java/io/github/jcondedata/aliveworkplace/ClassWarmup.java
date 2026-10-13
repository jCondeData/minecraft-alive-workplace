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
			try {
				shapes();
			} catch (Throwable e) { // nothing depends on it: the same code still runs, and loads, the first time a villager grows
				AliveWorkplace.LOG.debug("Shape warm-up failed: {}", e.toString());
			}
		}, "Alive Workplace class warm-up");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	/**
	 * Runs, on fresh shapes and collections of its own, the work an entity does when it changes size (a child of the
	 * Cradle growing up: {@code Entity.fudgePositionAfterSizeChange} joins its box with the blocks round it and asks for
	 * the closest free point). On the pack another mod (Lithium) does that with fastutil's double sets, lists and maps,
	 * which nothing else had loaded by then, and loading them on the server thread was a 50 to 250 ms tick in
	 * {@code Cradles.round} on day 1 (B103). Loading them here, and the arm-swing packet a worker's swing makes (with no
	 * player online nothing else does), takes that off the tick. Pure functions of what is passed in: nothing in the
	 * world is read or changed. Returns the free point found, for tests.
	 */
	public static java.util.Optional<net.minecraft.world.phys.Vec3> shapes() {
		it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet coords = new it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet();
		coords.addAll(net.minecraft.world.phys.shapes.Shapes.block().getCoords(net.minecraft.core.Direction.Axis.X));
		coords.add(0.5);
		it.unimi.dsi.fastutil.doubles.DoubleArrayList sorted = new it.unimi.dsi.fastutil.doubles.DoubleArrayList(coords);
		sorted.sort(null);
		it.unimi.dsi.fastutil.doubles.Double2IntOpenHashMap index = new it.unimi.dsi.fastutil.doubles.Double2IntOpenHashMap();
		for (int i = 0; i < sorted.size(); i++) {
			index.put(sorted.getDouble(i), i);
		}
		new java.util.BitSet(index.size()).set(index.get(0.5));
		Class<?> swing = net.minecraft.network.protocol.game.ClientboundAnimatePacket.class; // loaded, not initialised
		AliveWorkplace.LOG.debug("Warmed {} with {} coordinates", swing.getSimpleName(), index.size());
		net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(0.5, 0.2, 0.5);
		net.minecraft.world.phys.shapes.VoxelShape box = net.minecraft.world.phys.shapes.Shapes.create(
			net.minecraft.world.phys.AABB.ofSize(at, 1.2, 2.0, 1.2));
		net.minecraft.world.phys.shapes.VoxelShape blocks = net.minecraft.world.phys.shapes.Shapes.or(
			net.minecraft.world.phys.shapes.Shapes.box(0, -1, 0, 1, 0, 1), net.minecraft.world.phys.shapes.Shapes.box(1, 0, 0, 2, 1, 1));
		net.minecraft.world.phys.shapes.VoxelShape free = net.minecraft.world.phys.shapes.Shapes.join(box, blocks,
			net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
		return free.closestPointTo(at);
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
