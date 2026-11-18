package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * B843: machines that act as players (fake players, such as Create's Deployer) and whom they act for. A machine a
 * player placed may act for that player (a deployer of theirs setting their hall down makes them its owner); one
 * nobody placed, or a fake player no mod tells us about, acts for nobody, so it never becomes the owner of a hall,
 * mailbox, shop counter or storehouse (as {@link VillageHallBlock#built} refuses "the builder standing in for nobody").
 */
public final class StandIns {
	/**
	 * For a fake player a mod knows (filled by {@code compat/<mod>/}): the real player it acts for, empty for nobody, or
	 * null when it isn't that mod's.
	 */
	public static final Extension<Function<Player, Optional<UUID>>> ACTS_FOR = new Extension<>("machines acting for players");

	private StandIns() {
	}

	/** Whether {@code player} is a machine's stand-in rather than a person logged in. */
	public static boolean machine(Player player) {
		return Platform.get().isFakePlayer(player);
	}

	/** The person {@code player} is or acts for, or null when it's a machine acting for nobody. */
	@Nullable
	public static UUID person(Player player) {
		if (!machine(player)) {
			return player.getUUID();
		}
		Optional<UUID> forWhom = ACTS_FOR.call(f -> f.apply(player), null);
		return forWhom == null ? null : forWhom.orElse(null);
	}

	/** Whether {@code player} may become an owner: a person, or a machine acting for one. */
	public static boolean mayOwn(Player player) {
		return person(player) != null;
	}

	/** The name of the person {@code player} is or acts for (online, or as the server last knew them). */
	public static String name(Player player) {
		UUID id = person(player);
		if (machine(player) && id != null && player instanceof ServerPlayer server) {
			ServerPlayer online = server.server.getPlayerList().getPlayer(id);
			if (online != null && !machine(online)) {
				return online.getGameProfile().getName();
			}
			if (server.server.getProfileCache() != null) {
				var known = server.server.getProfileCache().get(id);
				if (known.isPresent()) {
					return known.get().getName();
				}
			}
		}
		return player.getGameProfile().getName();
	}
}
