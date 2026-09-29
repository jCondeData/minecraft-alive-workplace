package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * A part of the mod another mod fills in when it's installed — Cobblemon's Pokémon, CobbleDollars' money. The mod's
 * own code calls it without knowing who fills it (or whether anyone does: then it gets the fallback, and plays exactly
 * as without that mod); the integration in {@code compat/<mod>/} registers the filling at startup, once it knows the
 * mod is there. If a newer version of that mod changed its API (a {@link LinkageError}), the filling is turned off
 * with a line in the log and the game carries on as if the mod weren't installed.
 */
public final class Extension<T> {
	private final String what;
	@Nullable
	private volatile T filling;
	private volatile String source = "";

	public Extension(String what) {
		this.what = what;
	}

	/** {@code source} (a mod id) fills this in. */
	public void register(String source, T filling) {
		this.source = source;
		this.filling = filling;
	}

	/** Whether some mod fills this in (and it still works). */
	public boolean present() {
		return filling != null;
	}

	/** Asks the filling, or returns {@code fallback} when there's none (or it broke). */
	public <R> R call(Function<T, R> ask, R fallback) {
		T t = filling;
		if (t == null) {
			return fallback;
		}
		try {
			return ask.apply(t);
		} catch (LinkageError e) {
			broke(t, e);
			return fallback;
		}
	}

	/** Tells the filling to do something (nothing when there's none, or it broke). */
	public void run(Consumer<T> action) {
		T t = filling;
		if (t == null) {
			return;
		}
		try {
			action.accept(t);
		} catch (LinkageError e) {
			broke(t, e);
		}
	}

	private void broke(T t, LinkageError e) {
		if (filling == t) {
			filling = null;
			AliveWorkplace.LOG.error("Alive Workplace: {} from {} turned off: that mod's code isn't what this version was built against ({})",
				what, source, e.toString());
		}
	}
}
