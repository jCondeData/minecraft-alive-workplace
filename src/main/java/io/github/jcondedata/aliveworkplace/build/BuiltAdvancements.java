package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLooks;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A hidden advancement per shipped blueprint, {@code aliveworkplace:built/<blueprint path>}, granted when a build of it
 * finishes: the questbook's build quests tick off on them. The files are written by {@code tools/questbook/generate.py}
 * (one per {@code StarterBlueprints} entry), each with a single "impossible" criterion only this class grants. An
 * imported, uploaded or scanned blueprint has no file, so it awards nothing.
 */
public final class BuiltAdvancements {
	public static final String PREFIX = "built/";
	/** The criterion every built advancement has. */
	public static final String CRITERION = "built";

	/** The advancement for a build of {@code structure} (its look or style counts as the blueprint itself). */
	public static ResourceLocation idFor(ResourceLocation structure) {
		ResourceLocation base = BlueprintLooks.base(BlueprintStyles.base(structure));
		return AliveWorkplace.id(PREFIX + base.getPath());
	}

	/** The advancement {@code structure} awards, if it's one of ours that has one. */
	public static Optional<AdvancementHolder> advancementFor(ServerLevel level, ResourceLocation structure) {
		ResourceLocation base = BlueprintLooks.base(BlueprintStyles.base(structure));
		if (!base.getNamespace().equals(AliveWorkplace.MOD_ID)) {
			return Optional.empty();
		}
		return Optional.ofNullable(level.getServer().getAdvancements().get(idFor(structure)));
	}

	/**
	 * A build finished: its advancement goes to the player who started the site, or, when they aren't online (or no
	 * player started it, like the Steward's), to the players within a village's range of it. A Wonder's part (it has its
	 * own), a repair or a deconstruction awards nothing. Returns how many players got it.
	 */
	public static int award(ServerLevel level, BuildSite site) {
		if (site.wonder() != null || site.isRepair() || site.isDeconstruction()) {
			return 0;
		}
		Optional<AdvancementHolder> advancement = advancementFor(level, site.structure());
		if (advancement.isEmpty()) {
			return 0;
		}
		ServerPlayer owner = site.owner() == null ? null : level.getServer().getPlayerList().getPlayer(site.owner());
		List<ServerPlayer> players;
		if (owner != null) {
			players = List.of(owner);
		} else {
			BlockPos origin = site.placement().origin();
			double r2 = (double) VillageHalls.RADIUS * VillageHalls.RADIUS;
			players = level.players().stream().filter(p -> p.blockPosition().distSqr(origin) <= r2).toList();
		}
		int awarded = 0;
		for (ServerPlayer player : players) {
			if (player.getAdvancements().award(advancement.get(), CRITERION)) {
				awarded++;
			}
		}
		return awarded;
	}

	private BuiltAdvancements() {
	}
}
