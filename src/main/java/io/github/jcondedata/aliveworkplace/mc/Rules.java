package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/** Game rules. Change: they move to {@code level.gamerules} and a registry in 1.21.11 (porting.md). */
public final class Rules {
	public static boolean on(Level level, GameRules.Key<GameRules.BooleanValue> rule) {
		return level.getGameRules().getBoolean(rule);
	}

	public static int number(Level level, GameRules.Key<GameRules.IntegerValue> rule) {
		return level.getGameRules().getInt(rule);
	}

	public static void set(Level level, GameRules.Key<GameRules.BooleanValue> rule, boolean value, MinecraftServer server) {
		level.getGameRules().getRule(rule).set(value, server);
	}

	private Rules() {
	}
}
