package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon Raid Dens' running raids, as the raid helpers see them (ROADMAP 38.1a): which raid a player is in, the
 * boss's health, how the raid ended, and a blow at the boss. Raid Dens isn't on the compile classpath, so
 * {@code compat/raiddens} fills this in by reflection when it finds what 0.11.3 has; without it the helpers still cheer
 * (their join and the win come from the raid mod's own statistics), but can't reach the boss.
 */
public interface RaidAssist {
	Extension<RaidAssist> EXTENSION = new Extension<>("the raid dens' raids");

	/** The raid {@code player} is in now, if any. */
	Optional<Raid> raidOf(ServerPlayer player);

	/** The raid {@code id}, while the raid mod still holds it. */
	Optional<Raid> raid(MinecraftServer server, UUID id);

	enum State { RUNNING, WON, LOST }

	/** One raid: its boss's health (0 to {@link #maxHealth}) and a way to wear it down. */
	interface Raid {
		UUID id();

		float health();

		float maxHealth();

		State state();

		/** Takes {@code fraction} of the boss's full health off (the raid mod syncs every battle's health bar). */
		void hurt(float fraction);
	}
}
