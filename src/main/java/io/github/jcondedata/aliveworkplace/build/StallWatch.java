package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Logs a line whenever a build site with a builder makes no progress for 30 seconds (the builder soak test, 23.1,
 * counts these lines). One line per stall: it is written again only after the site has moved on and stalled anew.
 * Sites waiting in a builder's queue, without a builder, or in unloaded chunks are not watched.
 */
public final class StallWatch {
	/** 30 seconds. */
	public static final int STALL_TICKS = 600;
	private static final int EVERY = 20;
	/** The words every stall line starts with, for log searches. */
	public static final String PREFIX = "Builder stalled";

	private record Watch(long mark, long since, boolean logged) {
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	private static int stalls;

	private StallWatch() {
	}

	public static void init() {
		Platform.get().onServerTick(StallWatch::tick);
	}

	/** Stalls logged since the server started. */
	public static int stalls() {
		return stalls;
	}

	/** Whether the site's current stall has been logged (false again once it moves on). */
	public static boolean isStalled(UUID site) {
		Watch w = WATCHES.get(site);
		return w != null && w.logged();
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		Set<UUID> seen = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (BuildSite site : BuildSiteManager.get(level).all()) {
				if (site.builder() == null || site.isQueued() || site.isDone()
						|| !level.isLoaded(site.placement().origin())) {
					continue;
				}
				seen.add(site.id());
				watch(site, now);
			}
		}
		WATCHES.keySet().retainAll(seen);
	}

	static void watch(BuildSite site, long now) {
		long mark = site.progressMark();
		Watch w = WATCHES.get(site.id());
		if (w == null || w.mark() != mark) {
			WATCHES.put(site.id(), new Watch(mark, now, false));
			return;
		}
		if (!w.logged() && now - w.since() >= STALL_TICKS) {
			stalls++;
			WATCHES.put(site.id(), new Watch(mark, w.since(), true));
			AliveWorkplace.LOG.info("{} {} s: {} at {} (stage {}, status {}, {} placed, {} skipped, {} kinds missing) [stall #{}]",
					PREFIX, (now - w.since()) / 20, site.structure(), site.placement().origin().toShortString(),
					site.stage(), site.status(), site.placed(), site.skipped(), site.missing().size(), stalls);
		}
	}
}
