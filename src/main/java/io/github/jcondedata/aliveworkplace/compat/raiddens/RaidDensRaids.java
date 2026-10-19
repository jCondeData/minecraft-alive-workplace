package io.github.jcondedata.aliveworkplace.compat.raiddens;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.trainer.RaidAssist;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon Raid Dens' running raids for the raid helpers (38.1a), read by name: Raid Dens isn't on the compile
 * classpath. Checked against 0.11.3: {@code RaidHelper.ACTIVE_RAIDS} (raid id to {@code RaidInstance}), and on the
 * instance {@code isPlayerIn(ServerPlayer)}, {@code getCurrentHealth()}, {@code getMaxHealth()},
 * {@code getRaidState()} (NOT_STARTED, IN_PROGRESS, FAILED, SUCCESS, CANCELLED) and {@code healBoss(float)}, which adds
 * that share of the boss's full health and sends every battle the new health (a negative share is a blow; the raid
 * is only won when a player's own battle brings the health to 0, so the helpers' blows never end it). If any of them
 * is missing the filling stays off and the helpers only cheer.
 */
public final class RaidDensRaids implements RaidAssist {
	static final String HELPER = "com.necro.raid.dens.common.raids.helpers.RaidHelper";
	static final String INSTANCE = "com.necro.raid.dens.common.raids.RaidInstance";

	private final Field active;
	private final Method isPlayerIn;
	private final Method health;
	private final Method maxHealth;
	private final Method state;
	private final Method heal;

	private RaidDensRaids(Field active, Method isPlayerIn, Method health, Method maxHealth, Method state, Method heal) {
		this.active = active;
		this.isPlayerIn = isPlayerIn;
		this.health = health;
		this.maxHealth = maxHealth;
		this.state = state;
		this.heal = heal;
	}

	/** Fills {@link RaidAssist#EXTENSION} when Raid Dens' raids can be reached. */
	public static void init() {
		try {
			Class<?> helper = Class.forName(HELPER);
			Class<?> instance = Class.forName(INSTANCE);
			Field active = helper.getField("ACTIVE_RAIDS");
			if (!Map.class.isAssignableFrom(active.getType())) {
				throw new NoSuchFieldException("ACTIVE_RAIDS isn't a map");
			}
			RaidAssist.EXTENSION.register("cobblemonraiddens", new RaidDensRaids(active, instance.getMethod("isPlayerIn", ServerPlayer.class),
				instance.getMethod("getCurrentHealth"), instance.getMethod("getMaxHealth"), instance.getMethod("getRaidState"),
				instance.getMethod("healBoss", float.class)));
		} catch (ReflectiveOperationException | LinkageError e) {
			AliveWorkplace.LOG.warn("Alive Workplace: can't reach Raid Dens' raids ({}); raid helpers only cheer", e.toString());
		}
	}

	private Map<?, ?> raids() throws IllegalAccessException {
		return active.get(null) instanceof Map<?, ?> map ? map : Map.of();
	}

	@Override
	public Optional<Raid> raidOf(ServerPlayer player) {
		try {
			for (Map.Entry<?, ?> e : raids().entrySet()) {
				if (e.getKey() instanceof UUID id && e.getValue() != null && Boolean.TRUE.equals(isPlayerIn.invoke(e.getValue(), player))) {
					return Optional.of(new Instance(id, e.getValue()));
				}
			}
		} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
			AliveWorkplace.LOG.debug("Alive Workplace: reading Raid Dens' raids: {}", e.toString());
		}
		return Optional.empty();
	}

	@Override
	public Optional<Raid> raid(MinecraftServer server, UUID id) {
		try {
			Object raid = raids().get(id);
			return raid == null ? Optional.empty() : Optional.of(new Instance(id, raid));
		} catch (IllegalAccessException | RuntimeException e) {
			return Optional.empty();
		}
	}

	private final class Instance implements Raid {
		private final UUID id;
		private final Object raid;

		Instance(UUID id, Object raid) {
			this.id = id;
			this.raid = raid;
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public float health() {
			return number(health);
		}

		@Override
		public float maxHealth() {
			return number(maxHealth);
		}

		private float number(Method m) {
			try {
				return m.invoke(raid) instanceof Number n ? n.floatValue() : 0f;
			} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
				return 0f;
			}
		}

		@Override
		public State state() {
			try {
				String name = state.invoke(raid) instanceof Enum<?> en ? en.name() : "";
				return switch (name) {
					case "SUCCESS" -> State.WON;
					case "FAILED", "CANCELLED" -> State.LOST;
					default -> State.RUNNING;
				};
			} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
				return State.RUNNING;
			}
		}

		@Override
		public void hurt(float fraction) {
			try {
				heal.invoke(raid, -fraction);
			} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
				AliveWorkplace.LOG.warn("Alive Workplace: a raid helper's blow at a Raid Dens boss failed: {}", e.toString());
			}
		}
	}
}
