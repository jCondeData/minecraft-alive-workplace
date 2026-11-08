package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import net.minecraft.world.entity.Mob;

/**
 * What a mob turns into in vanilla is still what the mod made of it (B1054). {@code Mob.convertTo} (a husk under water
 * becoming a zombie, a zombie a drowned, a skeleton in powder snow a stray, a villager a zombie villager and back)
 * copies the name, the gear and "never despawn" but not the entity's tags, so a raider that turned was no longer its
 * raid's raider ({@code VillageRaids.own} counts by tag): the raid ended as beaten while it still walked the village,
 * it wasn't sent away at dawn and never despawned, and ThreatDamage no longer spared builders' blocks from it. The same
 * went for every other threat tag ({@link Threats#THREAT_TAGS}) and the raid's stamp (B1063).
 *
 * <p>Every one of the mod's tags ({@link #PREFIX}) goes over to the new mob, before it is added to the world. Mobs
 * without them are left as vanilla has them.
 */
public final class ConvertedMobs {
	/** Every tag the mod puts on an entity starts with this. */
	public static final String PREFIX = "aliveworkplace_";

	public static void init() {
		Platform.get().onMobConversion(ConvertedMobs::carry);
	}

	/** Puts the mod's tags {@code before} carried on {@code after}, its other tags left alone. */
	public static void carry(Mob before, Mob after) {
		if (before == null || after == null || before == after) {
			return;
		}
		for (String tag : List.copyOf(before.getTags())) {
			if (tag.startsWith(PREFIX)) {
				after.addTag(tag);
			}
		}
	}

	private ConvertedMobs() {
	}
}
