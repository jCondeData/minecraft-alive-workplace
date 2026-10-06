package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The Pokémon Ranger (ROADMAP 29.22), a Rare Legend without a trade of their own, {@code legends/pokemon_ranger.json}
 * (only with Cobblemon): a guest at the Village Hall, 1 morning in 3, while a wild Alpha Pokémon is within 96 blocks of
 * the hall ({@code alpha_near}, found by {@link WildPokemon}). Likes clothes. Their powers:
 * <ul>
 *   <li>{@code calm_alphas}: each morning they walk to an Alpha within 96 blocks of the hall not yet calmed; beside it
 *   (sparkles, a calm chime) it's calmed for good ({@link ModAttachments#RANGER_CALMED}, saved on the Pokémon), and
 *   never again hurts a villager or a player in a village ({@link #allowDamage});</li>
 *   <li>{@code befriend}: once a day they walk to a wild Pokémon within 64 blocks (never a banned one) and lead it to a
 *   Pasture Block of the village with room, where it becomes the hall owner's ({@link WildPokemon#befriend}); the
 *   chronicle notes each one. None when the pastures are full or the hall has no owner.</li>
 * </ul>
 * What they're doing is saved on them ({@link ModAttachments#RANGER}), so a walk survives a reload; a walk that takes
 * longer than {@link #GIVE_UP} ticks is given up for the day.
 */
public final class PokemonRanger {
	public static final ResourceLocation ID = AliveWorkplace.id("pokemon_ranger");
	/** The level a wild Pokémon counts as an Alpha from, when it has no Alpha mark (the stand-in). */
	public static final int ALPHA_LEVEL = 50;
	/** How far from the hall the Ranger looks for Alphas, in blocks. */
	public static final int CALM_RADIUS = 96;
	/** How far from the hall the Ranger looks for a wild Pokémon to befriend, in blocks. */
	public static final int BEFRIEND_RADIUS = 64;
	/** How close is beside: the Ranger calms, or the Pokémon starts to follow, or joins its pasture. */
	public static final double BESIDE = 3.0;
	/** Ticks a walk may take before it's given up for the day. */
	public static final int GIVE_UP = 2400;
	/** How far a led Pokémon may fall behind before it catches up at once. */
	static final double LEASH = 10.0;

	public static final int NONE = 0;
	public static final int CALM = 1;
	public static final int FETCH = 2;
	public static final int LEAD = 3;

	/** {@code calm_alphas}: each morning, an Alpha near the hall calmed for good. */
	public record CalmPower() implements Power {
		static CalmPower read(JsonObject json) {
			return new CalmPower();
		}

		@Override
		public String type() {
			return "calm_alphas";
		}
	}

	/** {@code befriend}: once a day, a wild Pokémon led to the village's pasture as the hall owner's. */
	public record BefriendPower() implements Power {
		static BefriendPower read(JsonObject json) {
			return new BefriendPower();
		}

		@Override
		public String type() {
			return "befriend";
		}
	}

	/**
	 * What a Ranger is doing: the days they last calmed and befriended (or found nothing to), the task ({@link #NONE},
	 * {@link #CALM}, {@link #FETCH}, {@link #LEAD}), its Pokémon, the pasture they lead it to and when it began.
	 */
	public record State(long calmDay, long befriendDay, int task, Optional<UUID> target, Optional<BlockPos> pasture, long since) {
		public static final State EMPTY = new State(-1, -1, NONE, Optional.empty(), Optional.empty(), 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("calm_day", -1L).forGetter(State::calmDay),
			Codec.LONG.optionalFieldOf("befriend_day", -1L).forGetter(State::befriendDay),
			Codec.INT.optionalFieldOf("task", NONE).forGetter(State::task),
			UUIDUtil.CODEC.optionalFieldOf("target").forGetter(State::target),
			BlockPos.CODEC.optionalFieldOf("pasture").forGetter(State::pasture),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(State::since)
		).apply(i, State::new));

		State idle() {
			return new State(calmDay, befriendDay, NONE, Optional.empty(), Optional.empty(), 0);
		}

		State doing(int task, UUID target, Optional<BlockPos> pasture, long now) {
			return new State(calmDay, befriendDay, task, Optional.of(target), pasture, now);
		}

		State calmedOn(long day) {
			return new State(day, befriendDay, task, target, pasture, since);
		}

		State befriendedOn(long day) {
			return new State(calmDay, day, task, target, pasture, since);
		}
	}

	static void register() {
		Powers.register("calm_alphas", CalmPower::read);
		Powers.register("befriend", BefriendPower::read);
	}

	/** A calmed Alpha never hurts a villager or a player in a village. */
	public static void init() {
		Platform.get().allowDamage(PokemonRanger::allowDamage);
	}

	/** False when {@code source} is a calmed Alpha and {@code entity} a villager or player inside a village. */
	public static boolean allowDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker == null || !calmed(attacker) || !(entity instanceof Villager || entity instanceof Player)
			|| !(entity.level() instanceof ServerLevel level)) {
			return true;
		}
		if (VillageHalls.nearest(level, entity.blockPosition()).isEmpty()) {
			return true;
		}
		if (attacker instanceof Mob mob && mob.getTarget() == entity) {
			mob.setTarget(null);
		}
		return false;
	}

	/** Whether {@code entity} is an Alpha a Ranger has calmed. */
	public static boolean calmed(Entity entity) {
		return Boolean.TRUE.equals(ModAttachments.RANGER_CALMED.get(entity));
	}

	// ---- who ----

	private static Optional<Legend> legend(Villager villager) {
		if (!Legends.ENABLED) {
			return Optional.empty();
		}
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !data.settled()) {
			return Optional.empty();
		}
		return Legends.get(data.id());
	}

	/** Whether {@code villager} is a settled Ranger at work (not on strike): a Legend with either power. */
	public static boolean working(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		return data != null && !data.onStrike()
			&& legend(villager).map(l -> !l.powers(CalmPower.class).isEmpty() || !l.powers(BefriendPower.class).isEmpty()).orElse(false);
	}

	private static boolean has(Villager villager, Class<? extends Power> kind) {
		return legend(villager).map(l -> !l.powers(kind).isEmpty()).orElse(false);
	}

	/** The Ranger's village hall: the one they settled at, else the nearest. */
	static Optional<BlockPos> hall(ServerLevel level, Villager ranger) {
		LegendData data = ModAttachments.LEGEND.get(ranger);
		Optional<BlockPos> settled = data == null ? Optional.empty() : data.hall();
		return settled.filter(h -> level.getBlockEntity(h) instanceof VillageHallBlockEntity)
			.or(() -> VillageHalls.nearest(level, ranger.blockPosition()));
	}

	public static State state(Villager ranger) {
		return ModAttachments.RANGER.getOrElse(ranger, State.EMPTY);
	}

	static void set(Villager ranger, State state) {
		ModAttachments.RANGER.set(ranger, state);
	}

	// ---- the day ----

	/** Every second of a Legend's life (from {@code Legends.tick}): a Ranger at work starts or carries on the day's walks. */
	public static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level) || !WildPokemon.EXTENSION.present() || !working(villager)) {
			return;
		}
		Optional<BlockPos> hall = hall(level, villager);
		if (hall.isEmpty()) {
			return;
		}
		State state = state(villager);
		if (state.task() == NONE) {
			state = choose(level, hall.get(), villager, state);
			set(villager, state);
			if (state.task() == NONE) {
				return;
			}
		}
		carryOn(level, hall.get(), villager, state);
	}

	/** The next walk: in the morning an Alpha to calm, then once a day a wild Pokémon to befriend. */
	static State choose(ServerLevel level, BlockPos hall, Villager ranger, State state) {
		long today = Chronicle.day(level);
		long time = level.getDayTime() % VillageNeeds.DAY;
		boolean morning = time >= LegendGuests.MORNING_FROM && time < LegendGuests.MORNING_TO;
		if (morning && state.calmDay() != today && has(ranger, CalmPower.class)) {
			Optional<Entity> alpha = alphaToCalm(level, hall, ranger);
			if (alpha.isPresent()) {
				return state.calmedOn(today).doing(CALM, alpha.get().getUUID(), Optional.empty(), level.getGameTime());
			}
			state = state.calmedOn(today);
		}
		if (state.befriendDay() != today && has(ranger, BefriendPower.class)) {
			state = state.befriendedOn(today);
			Optional<UUID> owner = owner(level, hall);
			if (owner.isEmpty()) {
				return state;
			}
			Optional<BlockPos> pasture = WildPokemon.EXTENSION.call(w -> w.pastureWithRoom(level, hall, owner.get()), Optional.empty());
			if (pasture.isEmpty()) {
				return state;
			}
			List<Entity> wild = WildPokemon.EXTENSION.call(w -> w.befriendable(level, ranger.blockPosition(), BEFRIEND_RADIUS), List.<Entity>of());
			wild = wild.stream().filter(e -> e.blockPosition().closerThan(hall, BEFRIEND_RADIUS + 0.5)).toList();
			if (!wild.isEmpty()) {
				return state.doing(FETCH, wild.get(0).getUUID(), pasture, level.getGameTime());
			}
		}
		return state;
	}

	/** An Alpha within {@link #CALM_RADIUS} of the hall not yet calmed, the nearest to the Ranger. */
	public static Optional<Entity> alphaToCalm(ServerLevel level, BlockPos hall, Villager ranger) {
		return WildPokemon.alphasNear(level, hall, CALM_RADIUS).stream().filter(e -> !calmed(e))
			.min(java.util.Comparator.comparingDouble(ranger::distanceToSqr));
	}

	/** The hall's owner, if it has one. */
	public static Optional<UUID> owner(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? Optional.ofNullable(entity.owner()) : Optional.empty();
	}

	/** One step of the walk under way: toward the Pokémon, or leading it to the pasture; beside, it's done. */
	static void carryOn(ServerLevel level, BlockPos hall, Villager ranger, State state) {
		Entity target = state.target().map(level::getEntity).orElse(null);
		if (target == null || !target.isAlive() || level.getGameTime() - state.since() > GIVE_UP) {
			set(ranger, state.idle());
			return;
		}
		switch (state.task()) {
			case CALM -> {
				if (calmed(target)) {
					set(ranger, state.idle());
				} else if (ranger.distanceTo(target) <= BESIDE) {
					calm(level, hall, ranger, target);
					set(ranger, state.idle());
				} else {
					walk(ranger, target);
				}
			}
			case FETCH -> {
				if (!WildPokemon.EXTENSION.call(w -> w.isWild(target) && !w.banned(target), false)) {
					set(ranger, state.idle());
				} else if (ranger.distanceTo(target) <= BESIDE) {
					set(ranger, new State(state.calmDay(), state.befriendDay(), LEAD, state.target(), state.pasture(), level.getGameTime()));
					level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 4, 0.3, 0.2, 0.3, 0);
				} else {
					walk(ranger, target);
				}
			}
			case LEAD -> {
				BlockPos pasture = state.pasture().orElse(null);
				if (pasture == null || !WildPokemon.EXTENSION.call(w -> w.isWild(target), false)) {
					set(ranger, state.idle());
					return;
				}
				follow(target, ranger);
				if (ranger.blockPosition().closerThan(pasture, BESIDE + 1)) {
					befriend(level, hall, ranger, target, pasture);
					set(ranger, state.idle());
				} else {
					ranger.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pasture, 0.5f, 2));
				}
			}
			default -> set(ranger, state.idle());
		}
	}

	private static void walk(Villager ranger, Entity target) {
		ranger.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), 0.55f, 2));
	}

	/** The led Pokémon walks after the Ranger, and catches up at once if it falls far behind. */
	static void follow(Entity pokemon, Villager ranger) {
		if (pokemon.distanceTo(ranger) > LEASH) {
			pokemon.teleportTo(ranger.getX(), ranger.getY(), ranger.getZ());
		} else if (pokemon instanceof Mob mob && pokemon.distanceTo(ranger) > 2) {
			mob.getNavigation().moveTo(ranger, 1.0);
		}
	}

	// ---- the powers' acts ----

	/** {@code ranger} beside {@code alpha}: sparkles, a calm chime, and it's calmed for good. */
	public static void calm(ServerLevel level, BlockPos hall, Villager ranger, Entity alpha) {
		ModAttachments.RANGER_CALMED.set(alpha, true);
		if (alpha instanceof Mob mob) {
			mob.setTarget(null);
			mob.setPersistenceRequired();
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, alpha.getX(), alpha.getY() + alpha.getBbHeight() * 0.6, alpha.getZ(), 14,
			alpha.getBbWidth() * 0.5, alpha.getBbHeight() * 0.4, alpha.getBbWidth() * 0.5, 0);
		level.sendParticles(ParticleTypes.END_ROD, alpha.getX(), alpha.getY() + alpha.getBbHeight() + 0.4, alpha.getZ(), 6, 0.3, 0.2, 0.3, 0.01);
		level.playSound(null, alpha.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.2f, 0.8f);
		Component name = WildPokemon.EXTENSION.call(w -> w.name(alpha), alpha.getName());
		Component text = Component.translatable("message.aliveworkplace.ranger.calmed", Component.literal(ranger.getName().getString()), name,
			VillageHalls.name(level, hall)).withStyle(ChatFormatting.AQUA);
		double r = VillageHalls.RADIUS + CALM_RADIUS;
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(hall.getCenter()) < r * r)) {
			Chat.chat(player, text);
		}
	}

	/**
	 * {@code ranger} brings {@code wild} to the Pasture Block at {@code pasture}: it becomes the hall owner's (into their
	 * PC first, then the pasture), with hearts and a line in the chronicle. Returns how it ended (null: no owner).
	 */
	@Nullable
	public static WildPokemon.Befriended befriend(ServerLevel level, BlockPos hall, Villager ranger, Entity wild, BlockPos pasture) {
		Optional<UUID> owner = owner(level, hall);
		if (owner.isEmpty() || !WildPokemon.EXTENSION.present()) {
			return null;
		}
		Component name = WildPokemon.EXTENSION.call(w -> w.name(wild), wild.getName());
		double x = wild.getX();
		double y = wild.getY() + wild.getBbHeight();
		double z = wild.getZ();
		WildPokemon.Befriended result = WildPokemon.EXTENSION.call(w -> w.befriend(level, wild, pasture, owner.get()), WildPokemon.Befriended.REFUSED);
		if (result == WildPokemon.Befriended.PASTURED || result == WildPokemon.Befriended.IN_PC) {
			level.sendParticles(ParticleTypes.HEART, x, y, z, 6, 0.4, 0.3, 0.4, 0);
			level.playSound(null, pasture, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.3f);
			String ownerName = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.ownerName() : "";
			Chronicle.record(level, hall, Chronicle.Kind.ARRIVED, Component.translatable(result == WildPokemon.Befriended.PASTURED
				? "chronicle.aliveworkplace.ranger.befriended" : "chronicle.aliveworkplace.ranger.befriended_pc",
				Component.literal(ranger.getName().getString()), name, Component.literal(ownerName)));
		}
		return result;
	}

	private PokemonRanger() {
	}
}
