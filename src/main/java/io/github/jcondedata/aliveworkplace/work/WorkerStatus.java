package io.github.jcondedata.aliveworkplace.work;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

/**
 * What a worker without a saved job site (lumberjack, ...) is up to, for the line above its head.
 * Set by the worker's behaviour each time it changes; sent to nearby players by BuilderStatusSync
 * while fresh.
 */
public final class WorkerStatus {
	public record Entry(Component title, float progress, Component line, long gameTime) {
	}

	private static final Map<Villager, Entry> STATUS = new WeakHashMap<>();

	public static synchronized void set(Villager villager, Component title, float progress, Component line) {
		STATUS.put(villager, new Entry(title, progress, line, villager.level().getGameTime()));
	}

	/** What {@code villager} was last up to, if it's recent (null if not). */
	@org.jetbrains.annotations.Nullable
	public static synchronized Entry get(Villager villager, long gameTime) {
		Entry entry = STATUS.get(villager);
		return entry != null && gameTime - entry.gameTime() <= 100 ? entry : null;
	}

	public static synchronized Map<Villager, Entry> fresh(long gameTime) {
		STATUS.entrySet().removeIf(e -> e.getKey().isRemoved() || gameTime - e.getValue().gameTime() > 100);
		return Map.copyOf(STATUS);
	}

	private WorkerStatus() {
	}
}
