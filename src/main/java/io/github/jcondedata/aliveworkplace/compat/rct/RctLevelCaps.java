package io.github.jcondedata.aliveworkplace.compat.rct;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.lang.reflect.Method;
import java.util.OptionalInt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A player's level cap from Radical Cobblemon Trainers (in the Cobbleverse pack), so village trainers can
 * match it. Looked up by reflection on RCT's public {@code LevelUtils.levelCap(Player)}: RCT isn't needed
 * to build or run this mod, and if its API ever changes we just stop matching the cap.
 */
public final class RctLevelCaps {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("rctmod");
	private static Method levelCap;
	private static boolean broken;

	/** The player's current RCT level cap, or empty without RCT. */
	public static OptionalInt levelCap(ServerPlayer player) {
		if (!LOADED || broken) {
			return OptionalInt.empty();
		}
		try {
			if (levelCap == null) {
				levelCap = Class.forName("com.gitlab.srcmc.rctmod.api.utils.LevelUtils").getMethod("levelCap", Player.class);
			}
			Object cap = levelCap.invoke(null, player);
			return cap instanceof Integer i && i > 0 ? OptionalInt.of(i) : OptionalInt.empty();
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			broken = true;
			AliveWorkplace.LOG.warn("Couldn't read level caps from Radical Cobblemon Trainers; village trainers won't match them", e);
			return OptionalInt.empty();
		}
	}

	private RctLevelCaps() {
	}
}
