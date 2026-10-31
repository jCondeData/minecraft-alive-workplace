package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mixin.EntityAccessor;
import io.github.jcondedata.aliveworkplace.mixin.ServerLevelEntitiesAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

/**
 * Puts the village's foes in the world (B208): a bandit camp's band, a raid's raiders, the Bandit King's men, a bounty's
 * band, the Pharaoh's risen. Each spawn's result is checked, never assumed.
 * <ul>
 * <li>Another mod may refuse the mob at {@code addEntity}: Cobbleverse ships MobsBeGone with a blocklist naming
 * pillagers, vindicators, zombies and nearly every other vanilla mob, which discards them there. Camps then stood empty
 * and were "broken up" at once, and raids never came. While {@link #FORCE} ({@code forceThreatSpawns}) the refused mob is
 * put in the world directly (the game's entity manager, past {@code addEntity}), and the log says once per mob type
 * which mob was refused and placed anyway.</li>
 * <li>Still refused (the switch off, or the entity manager refuses it too): the mob is gone, the call returns false, and
 * the log says so plainly once per mob type, so the caller neither counts it nor treats its camp as defeated.</li>
 * <li>On Peaceful difficulty hostile mobs can't exist (the game discards them every tick): nothing is spawned, the log
 * says why, and each player near is told once a day.</li>
 * <li>The village's own animals and golems (B211) go the same way through {@link #addVillage}: the Golem Smith's and the
 * Iron Pact's iron golems, relief riders' horses, the Beastmaster's foals and wolf pups, a caravan's pack llamas. The
 * same blocklist names iron golems, wolves, horses, cats and llamas; while {@link #FORCE_VILLAGE}
 * ({@code forceVillageSpawns}) they are placed directly too. Only the mod's own spawns come here, never vanilla's.</li>
 * </ul>
 */
public final class HostileSpawns {
	private HostileSpawns() {}

	/** Whether a mob another mod refused is put in the world directly ({@code forceThreatSpawns}, on by default). */
	public static volatile boolean FORCE = true;

	/** Whether a village animal or golem another mod refused is put in the world directly ({@code forceVillageSpawns}, on). */
	public static volatile boolean FORCE_VILLAGE = true;

	/** GameTests only: the entities the world refuses at {@code addEntity}, as a mob blocklist would (null: none). */
	@Nullable
	public static volatile Predicate<Entity> REFUSED_FOR_TESTS = null;

	/** How far from a refused foe players are told it's Peaceful. */
	static final double TELL_RADIUS = 96;

	private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
	private static final Map<String, Long> PEACEFUL_TOLD = new ConcurrentHashMap<>();
	private static volatile String lastWarning = "";

	/** Why a foe couldn't be put in the world. */
	public enum Refusal { PEACEFUL, REFUSED, REFUSED_VILLAGE, REFUSED_FORCED }

	/**
	 * Puts {@code mob} (made and set up, not yet in the world) and its passengers in {@code level}, as the {@code what}
	 * named in the log ("bandit camp by the village hall at 10, 64, 20"). True if it's in the world now.
	 */
	public static boolean add(ServerLevel level, Mob mob, String what) {
		return add(level, mob, what, level.getDifficulty());
	}

	/** As {@link #add(ServerLevel, Mob, String)}, as if the world's difficulty were {@code difficulty} (tests). */
	public static boolean add(ServerLevel level, Mob mob, String what, Difficulty difficulty) {
		return add(level, mob, what, difficulty, true);
	}

	/**
	 * Keeps a foe the game itself just made and tried to add, such as the mob {@code Mob.convertTo} returns (a fallen
	 * Warlord's band turning zombified, B356): in the world already, nothing is done; refused by another mod's blocklist,
	 * it goes the way of {@link #add(ServerLevel, Mob, String)} (placed directly while {@link #FORCE}, else left out and
	 * logged once). True if it's in the world now.
	 */
	public static boolean keep(ServerLevel level, Mob mob, String what) {
		if (in(level, mob)) {
			return true;
		}
		for (Entity e : mob.getSelfAndPassengers().toList()) {
			if (e.isRemoved()) {
				((EntityAccessor) e).aliveworkplace$unsetRemoved();
			}
		}
		return add(level, mob, what);
	}

	/**
	 * Puts the village's own {@code mob} (an iron golem, a relief horse, a foal, a pack llama: made and set up, not yet in
	 * the world) and its passengers in {@code level}, forced past another mod's blocklist while {@link #FORCE_VILLAGE}
	 * (B211). True if it's in the world now.
	 */
	public static boolean addVillage(ServerLevel level, Mob mob, String what) {
		return add(level, mob, what, level.getDifficulty(), false);
	}

	private static boolean add(ServerLevel level, Mob mob, String what, Difficulty difficulty, boolean threat) {
		List<Entity> all = mob.getSelfAndPassengers().toList();
		if (threat && difficulty == Difficulty.PEACEFUL && mob instanceof Enemy) {
			all.forEach(Entity::discard);
			refused(level, mob, what, Refusal.PEACEFUL);
			return false;
		}
		Map<Entity, Entity> rides = new LinkedHashMap<>();
		for (Entity e : all) {
			if (e.getVehicle() != null) {
				rides.put(e, e.getVehicle());
			}
		}
		level.addFreshEntityWithPassengers(mob);
		List<Entity> refused = all.stream().filter(e -> !in(level, e)).toList();
		if (refused.isEmpty()) {
			return true;
		}
		if (!(threat ? FORCE : FORCE_VILLAGE)) {
			refused.forEach(Entity::discard);
			refused(level, mob, what, threat ? Refusal.REFUSED : Refusal.REFUSED_VILLAGE);
			return in(level, mob);
		}
		for (Entity e : refused) {
			if (e.isRemoved()) {
				((EntityAccessor) e).aliveworkplace$unsetRemoved();
			}
			if (!((ServerLevelEntitiesAccessor) level).aliveworkplace$entities().addNewEntity(e)) {
				e.discard();
			}
		}
		rides.forEach((passenger, vehicle) -> {
			if (passenger.getVehicle() != vehicle && in(level, passenger) && in(level, vehicle)) {
				passenger.startRiding(vehicle, true);
			}
		});
		if (!in(level, mob)) {
			refused(level, mob, what, Refusal.REFUSED_FORCED);
			return false;
		}
		String type = type(mob);
		if (!threat) {
			log("forced_village|" + type, String.format("Another mod refused to let %s spawn for the %s (a mob blocklist such as MobsBeGone, "
				+ "which Cobbleverse ships with iron golems, wolves, horses, cats and llamas on it); Alive Workplace placed it directly. "
				+ "Set forceVillageSpawns to false in config/aliveworkplace.json to leave such animals and golems out instead.", type, what));
			return true;
		}
		log("forced|" + type, String.format("Another mod refused to let %s spawn for the %s (a mob blocklist such as MobsBeGone, which "
			+ "Cobbleverse ships with pillagers and zombies on it); Alive Workplace placed it directly so its camp or raid isn't empty. "
			+ "Set forceThreatSpawns to false in config/aliveworkplace.json to leave such mobs out instead.", type, what));
		return true;
	}

	/** The refusal said: in the log once per mob type and reason, and on Peaceful to the players near, once a day. */
	private static void refused(ServerLevel level, Mob mob, String what, Refusal why) {
		String type = type(mob);
		String reason = switch (why) {
			case PEACEFUL -> "the world is on Peaceful difficulty, where hostile mobs can't exist; raise the difficulty for raids and camps";
			case REFUSED -> "another mod refused it (a mob blocklist such as MobsBeGone's config/mobsbegone-blacklist.json?) and "
				+ "forceThreatSpawns is off";
			case REFUSED_VILLAGE -> "another mod refused it (a mob blocklist such as MobsBeGone's config/mobsbegone-blacklist.json?) and "
				+ "forceVillageSpawns is off";
			case REFUSED_FORCED -> "another mod refused it, and the world refused it again when placed directly";
		};
		log(why + "|" + type, String.format("Could not spawn %s for the %s at %s: %s.", type, what, mob.blockPosition().toShortString(), reason));
		if (why == Refusal.PEACEFUL) {
			// Each player near is told once a day: the day is marked per player, so a refusal at a village nobody is at
			// doesn't use up the word for a player by his own (B708).
			long day = level.getGameTime() / 24000L;
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(mob) > TELL_RADIUS * TELL_RADIUS) {
					continue;
				}
				String key = level.dimension().location() + "|" + player.getUUID();
				Long told = PEACEFUL_TOLD.get(key);
				if (told == null || told < day) {
					PEACEFUL_TOLD.put(key, day);
					Chat.chat(player, Component.translatable("message.aliveworkplace.threat.peaceful").withStyle(ChatFormatting.YELLOW));
				}
			}
		}
	}

	private static void log(String key, String line) {
		lastWarning = line;
		if (LOGGED.add(key)) {
			AliveWorkplace.LOG.warn(line);
		}
	}

	private static boolean in(ServerLevel level, Entity e) {
		return !e.isRemoved() && level.getEntity(e.getUUID()) == e;
	}

	private static String type(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
	}

	/** The last warning written (or that would have been, had it not been said already); tests read it. */
	public static String lastWarning() {
		return lastWarning;
	}

	/** Forgets which warnings were said and when players were told (tests). */
	public static void forget() {
		LOGGED.clear();
		PEACEFUL_TOLD.clear();
		lastWarning = "";
	}
}
