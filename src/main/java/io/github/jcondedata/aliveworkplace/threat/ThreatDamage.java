package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import org.jetbrains.annotations.Nullable;

/**
 * What threats may damage (ROADMAP 32.16a, the owner's rule of 2026-10-12): of the builders' work only the gate blocks
 * of walls and gates break, under a siege's rams ({@link Sieges}), and the blocks a village fire burns
 * ({@link VillageFire}); the builders put back everything either took, even with {@code builderRepairs} off
 * ({@link ThreatData#damaged}, {@code build/Upkeep}). Nothing else a threat does breaks a builder's block: a raider's
 * blast (a creeper, a ghast's fireball, TNT it lit), a ravager trampling a roof's leaves, a vindicator or zombie
 * breaking a house's door. A build still under way is never touched either, so no builder's progress is lost.
 * Players' own blocks follow the game's rules as before.
 */
public final class ThreatDamage {
	/** Set while a siege's ram breaks the gate block it is allowed to break. */
	private static boolean ramming;

	/** Whether {@code entity} is a threat's: a raider ({@link Threats#RAIDER_TAG}) or what one shot. */
	public static boolean isThreat(@Nullable Entity entity) {
		if (entity == null) {
			return false;
		}
		if (entity.getTags().contains(Threats.RAIDER_TAG)) {
			return true;
		}
		return entity instanceof Projectile p && p.getOwner() != null && p.getOwner() != entity && p.getOwner().getTags().contains(Threats.RAIDER_TAG);
	}

	/** Whether the block at {@code pos} is the builders': a finished build's block as drawn, or inside a build under way. */
	public static boolean builders(ServerLevel level, BlockPos pos) {
		if (VillageFire.builderMade(level, pos)) {
			return true;
		}
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (!site.placement().dimension().equals(Ids.of(level.dimension()))) {
				continue;
			}
			boolean inside = BlueprintLibrary.get(level, site.structure())
				.map(b -> BlueprintOutline.bounds(site.placement(), b.size()).isInside(pos)).orElse(false);
			if (inside) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code by} must leave the block at {@code pos} standing: a threat's, and the builders' block that isn't a ram's gate. */
	public static boolean spares(ServerLevel level, BlockPos pos, @Nullable Entity by) {
		return !ramming && isThreat(by) && !level.getBlockState(pos).isAir() && builders(level, pos);
	}

	/** Takes the builders' blocks out of what a threat's explosion would blow up. */
	public static void spare(ServerLevel level, Explosion explosion, List<BlockPos> toBlow) {
		if (toBlow.isEmpty()) {
			return;
		}
		Entity by = explosion.getDirectSourceEntity();
		if (!isThreat(by) && !isThreat(explosion.getIndirectSourceEntity())) {
			return;
		}
		toBlow.removeIf(pos -> !level.getBlockState(pos).isAir() && builders(level, pos));
	}

	/** Runs {@code blow} as a ram's: the gate block it breaks is the one break a threat is allowed. */
	static void asRam(Runnable blow) {
		boolean was = ramming;
		ramming = true;
		try {
			blow.run();
		} finally {
			ramming = was;
		}
	}

	/** A threat took the builders' block at {@code pos}: it goes on the list the builders repair from. */
	public static void took(ServerLevel level, BlockPos pos) {
		ThreatData.get(level).damaged(pos);
	}

	private ThreatDamage() {
	}
}
