package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Festival Cup's bouts between villagers (ROADMAP 28.18). On the Cup's day, from the theme's start until its end,
 * the next bout of the bracket starts at the host's Arena whenever the ring is free: a round starts when the last
 * round's bouts are done. A bout between two villager entrants is an exhibition ({@link CupBout}): each trainer stands
 * in their box, their Pokémon come out one at a time beside the ring (real Pokémon, through {@link CupBattlers}: never
 * catchable, gone on load), face each other and take turns. Afterwards the winner goes on, both trainers get trainer XP
 * (the win bonus to the winner; a trainer who isn't here has it banked on their village's caravans' entry, paid when
 * that village next loads) and everyone at the Arena reads the result. Bouts with a player wait for 28.20.
 */
public final class CupBouts {
	/** Players this close to the ring read the bout. */
	static final double AT_THE_ARENA = 48;
	/** How far from the ring towards each box a side's Pokémon stands (share of the way). */
	static final double SPOT = 0.4;

	/** A bout to fight: the round and the two entrants (bracket order). */
	public record Pairing(int round, CupData.Entrant a, CupData.Entrant b) {
	}

	/** The Pokémon out at a ring: which of the side's team, and its entity. */
	private record Shown(Entity entity, int index) {
	}

	/** By dimension and host: what each side has out. Not saved: the entities are gone on load and sent out again. */
	private static final Map<String, Shown[]> OUT = new HashMap<>();

	private CupBouts() {
	}

	public static void init() {
		Platform.get().onServerTick(CupBouts::tick);
	}

	static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			tick(level);
		}
	}

	/** Every host's ring in {@code level}, one tick. */
	public static void tick(ServerLevel level) {
		CupData data = CupData.get(level);
		for (Map.Entry<BlockPos, CupData.Cup> e : data.all().entrySet()) {
			BlockPos host = e.getKey();
			CupData.Cup cup = e.getValue();
			if (!level.isLoaded(host)) {
				continue;
			}
			if (cup.bout != null) {
				if (!Cups.ENABLED) {
					stop(level, host, cup);
					data.setDirty();
				} else {
					tickBout(level, host, cup);
					data.setDirty();
				}
			} else if (Cups.ENABLED && level.getGameTime() % 20 == 0) {
				startNext(level, host, cup);
			}
		}
	}

	// ---------------------------------------------------------------- the bracket

	/** Whether it's the Cup's day, between the theme's start and end. */
	public static boolean boutTime(ServerLevel level, CupData.Cup cup, CupThemes.Theme theme) {
		long dayStart = (cup.day - 1) * Cups.DAY;
		long now = level.getDayTime();
		return cup.day >= 1 && now >= dayStart + theme.start() && now < dayStart + theme.end();
	}

	/**
	 * The next bout to fight: the first undecided pair of the earliest round still going, of two villagers (a pair with a
	 * player waits for 28.20); null while none can be fought or once the final is decided.
	 */
	@Nullable
	public static Pairing next(CupData.Cup cup) {
		List<CupData.Entrant> slots = new ArrayList<>(cup.bracket);
		int round = 1;
		while (slots.size() >= 2) {
			List<CupData.Entrant> winners = new ArrayList<>();
			boolean done = true;
			Pairing pending = null;
			for (int i = 0; i + 1 < slots.size(); i += 2) {
				CupData.Entrant a = slots.get(i);
				CupData.Entrant b = slots.get(i + 1);
				if (a == null || b == null) {
					winners.add(a == null ? b : a);
					continue;
				}
				CupData.Result r = result(cup, round, a.id(), b.id());
				if (r != null) {
					winners.add(r.winner().equals(a.id()) ? a : b);
					continue;
				}
				done = false;
				winners.add(null);
				if (pending == null && a.kind() != CupData.Kind.PLAYER && b.kind() != CupData.Kind.PLAYER) {
					pending = new Pairing(round, a, b);
				}
			}
			if (!done) {
				return pending;
			}
			slots = winners;
			round++;
		}
		return null;
	}

	/** The champion once the final is decided, else null. */
	@Nullable
	public static CupData.Entrant champion(CupData.Cup cup) {
		List<CupData.Entrant> slots = new ArrayList<>(cup.bracket);
		int round = 1;
		while (slots.size() >= 2) {
			List<CupData.Entrant> winners = new ArrayList<>();
			for (int i = 0; i + 1 < slots.size(); i += 2) {
				CupData.Entrant a = slots.get(i);
				CupData.Entrant b = slots.get(i + 1);
				if (a == null || b == null) {
					winners.add(a == null ? b : a);
					continue;
				}
				CupData.Result r = result(cup, round, a.id(), b.id());
				if (r == null) {
					return null;
				}
				winners.add(r.winner().equals(a.id()) ? a : b);
			}
			slots = winners;
			round++;
		}
		return slots.isEmpty() ? null : slots.get(0);
	}

	@Nullable
	static CupData.Result result(CupData.Cup cup, int round, UUID a, UUID b) {
		for (CupData.Result r : cup.results) {
			if (r.round() == round && (r.winner().equals(a) && r.loser().equals(b) || r.winner().equals(b) && r.loser().equals(a))) {
				return r;
			}
		}
		return null;
	}

	/** Starts the next bout at the host's Arena if it's time and the ring is free. */
	static void startNext(ServerLevel level, BlockPos host, CupData.Cup cup) {
		if (!cup.closed || !cup.noCup.isEmpty() || cup.bracket.isEmpty()) {
			return;
		}
		CupThemes.Theme theme = CupThemes.get(cup.theme);
		if (theme == null || !boutTime(level, cup, theme) || !CupBattlers.EXTENSION.present()) {
			return;
		}
		Pairing pairing = next(cup);
		if (pairing == null) {
			return;
		}
		Optional<Arenas.Arena> arena = Arenas.find(level, host);
		if (arena.isEmpty()) {
			return;
		}
		Arenas.Arena a = arena.get();
		begin(level, host, cup, new CupBout(pairing.a().id(), pairing.b().id(), team(pairing.a(), theme), team(pairing.b(), theme), pairing.round(),
			CupBout.seed(host, cup.day, pairing.round(), pairing.a().id(), pairing.b().id()), a.ring(), a.boxes().get(0), a.boxes().get(1)));
	}

	/** The entrant's team for this theme (none without Cobblemon). */
	public static List<CupBout.Fighter> team(CupData.Entrant entrant, CupThemes.Theme theme) {
		return CupBattlers.EXTENSION.call(b -> b.team(entrant.id(), Math.max(1, entrant.tier()), theme), List.of());
	}

	/** Puts {@code bout} in the ring: the trainers to their boxes, and everyone at the Arena told. */
	public static void begin(ServerLevel level, BlockPos host, CupData.Cup cup, CupBout bout) {
		cup.bout = bout;
		CupData.get(level).setDirty();
		OUT.remove(key(level, host));
		for (int s = 0; s < 2; s++) {
			toBox(level, bout, s, true);
		}
		CupData.Entrant a = entrant(cup, bout.trainers[0], host);
		CupData.Entrant b = entrant(cup, bout.trainers[1], host);
		CupThemes.Theme theme = CupThemes.get(cup.theme);
		tell(level, bout, Component.translatable("message.aliveworkplace.cup.bout_start", bout.round,
			theme == null ? Component.translatable("screen.aliveworkplace.cup.title", villageName(level, host)) : Component.translatable(theme.name()),
			a.display(), villageName(level, a.village()), b.display(), villageName(level, b.village())).withStyle(ChatFormatting.GOLD), false);
	}

	// ---------------------------------------------------------------- a bout, tick by tick

	static void tickBout(ServerLevel level, BlockPos host, CupData.Cup cup) {
		CupBout bout = cup.bout;
		String key = key(level, host);
		Shown[] shown = OUT.computeIfAbsent(key, k -> new Shown[2]);
		CupBout.Action action = bout.tick();
		if (action != null) {
			show(level, bout, shown, action);
		}
		int phase = bout.ticks % CupBout.ACTION_TICKS;
		for (int s = 0; s < 2; s++) {
			Shown sh = shown[s];
			if (sh != null && (sh.entity().isRemoved() || !sh.entity().isAlive())) {
				shown[s] = null;
			} else if (sh != null && sh.index() != bout.out[s] && phase >= 10) {
				CupBattlers.EXTENSION.run(b -> b.recall(level, sh.entity())); // fainted: called back
				shown[s] = null;
			}
			CupBout.Fighter f = bout.current(s);
			if (f != null && shown[s] == null && !bout.over() && (bout.ticks <= 1 || phase >= CupBout.ACTION_TICKS / 2)) {
				Vec3 at = spot(bout, s);
				Vec3 facing = spot(bout, 1 - s);
				Entity e = CupBattlers.EXTENSION.call(b -> b.sendOut(level, f, at, facing), null);
				if (e != null) {
					shown[s] = new Shown(e, bout.out[s]);
					if (bout.ticks > 1) {
						CupData.Entrant who = entrant(cup, bout.trainers[s], host);
						tell(level, bout, Component.translatable("message.aliveworkplace.cup.sends_out", who.display(), Component.translatable(f.nameKey())), true);
					}
				}
			}
		}
		if (bout.ticks % 20 == 0) {
			for (int s = 0; s < 2; s++) {
				toBox(level, bout, s, false);
			}
		}
		if (bout.over()) {
			finish(level, host, cup);
		}
	}

	/** Shows a move: the attack animation and impact, and what happened on everyone's action bar. */
	private static void show(ServerLevel level, CupBout bout, Shown[] shown, CupBout.Action action) {
		int side = action.side();
		Shown attacker = shown[side];
		Shown target = shown[1 - side];
		if (attacker != null && target != null) {
			CupBattlers.EXTENSION.run(b -> b.useMove(level, attacker.entity(), target.entity(), action.move().type(), action.move().physical()));
		}
		CupBout.Fighter user = bout.teams[side].get(bout.out[side]); // the mover's side never changes Pokémon on its own move
		CupBout.Fighter hit = bout.teams[1 - side].get(action.fainted() ? bout.out[1 - side] - 1 : bout.out[1 - side]);
		Component who = Component.translatable(user.nameKey());
		Component move = Component.translatable(action.move().nameKey());
		Component line;
		if (!action.hit()) {
			line = Component.translatable("message.aliveworkplace.cup.move_miss", who, move);
		} else if (action.effectiveness() <= 0) {
			line = Component.translatable("message.aliveworkplace.cup.move_none", who, move, Component.translatable(hit.nameKey()));
		} else if (action.effectiveness() > 1) {
			line = Component.translatable("message.aliveworkplace.cup.move_super", who, move);
		} else if (action.effectiveness() < 1) {
			line = Component.translatable("message.aliveworkplace.cup.move_weak", who, move);
		} else {
			line = Component.translatable("message.aliveworkplace.cup.move", who, move);
		}
		if (action.fainted()) {
			line = Component.translatable("message.aliveworkplace.cup.fainted", line, Component.translatable(hit.nameKey()));
		}
		tell(level, bout, line, true);
	}

	/** The bout is decided: the result kept, XP given (or banked), everyone at the Arena told, the Pokémon called back. */
	static void finish(ServerLevel level, BlockPos host, CupData.Cup cup) {
		CupBout bout = cup.bout;
		int w = bout.winner;
		CupData.Entrant win = entrant(cup, bout.trainers[w], host);
		CupData.Entrant lose = entrant(cup, bout.trainers[1 - w], host);
		cup.results.add(new CupData.Result(bout.round, win.id(), lose.id()));
		award(level, win, true);
		award(level, lose, false);
		Component line;
		if (bout.walkover()) {
			line = Component.translatable("message.aliveworkplace.cup.bout_walkover", win.display(), villageName(level, win.village()), lose.display(),
				villageName(level, lose.village()));
		} else if (bout.onTime) {
			line = Component.translatable("message.aliveworkplace.cup.bout_time", win.display(), villageName(level, win.village()), lose.display(),
				villageName(level, lose.village()));
		} else {
			CupBout.Fighter stands = bout.current(w);
			line = Component.translatable("message.aliveworkplace.cup.bout_won", win.display(), villageName(level, win.village()), lose.display(),
				villageName(level, lose.village()), stands == null ? Component.literal("?") : Component.translatable(stands.nameKey()));
		}
		tell(level, bout, line.copy().withStyle(ChatFormatting.GOLD), false);
		stop(level, host, cup);
	}

	/** Ends whatever is in the ring without a result: every Pokémon called back. */
	public static void stop(ServerLevel level, BlockPos host, CupData.Cup cup) {
		CupBout bout = cup.bout;
		cup.bout = null;
		Shown[] shown = OUT.remove(key(level, host));
		if (shown != null) {
			for (Shown sh : shown) {
				if (sh != null && sh.entity().isAlive()) {
					CupBattlers.EXTENSION.run(b -> b.recall(level, sh.entity()));
				}
			}
		}
		if (bout != null) {
			AABB box = new AABB(bout.ring).inflate(24, 12, 24);
			for (Entity e : CupBattlers.EXTENSION.call(b -> b.inBox(level, box), List.<Entity>of())) {
				if (shown == null || java.util.Arrays.stream(shown).noneMatch(sh -> sh != null && sh.entity() == e)) {
					e.discard(); // one left over from before
				}
			}
		}
		CupData.get(level).setDirty();
	}

	/** Trainer XP for a bout: given to the trainer if they're here, else banked on their village's caravans' entry. */
	static void award(ServerLevel level, CupData.Entrant entrant, boolean won) {
		if (entrant.kind() == CupData.Kind.PLAYER) {
			return;
		}
		int xp = Trainers.XP_PER_BATTLE + (won ? Trainers.XP_FOR_WIN : 0);
		if (level.getEntity(entrant.id()) instanceof Villager v && v.isAlive()) {
			ModAttachments.TRAINER_BATTLES.set(v, ModAttachments.TRAINER_BATTLES.getOrElse(v, 0) + 1);
			BuilderLevels.addXp(level, v, Guilds.trainerXp(v, xp), null);
		} else {
			Caravans.Data.get(level).bankXp(entrant.village(), entrant.id(), xp);
		}
	}

	/** The trainer XP banked on the village round {@code hall}: given to each trainer who's here now (the hall's round, as the village loads). */
	public static void payBanked(ServerLevel level, BlockPos hall) {
		Caravans.Data data = Caravans.Data.get(level);
		for (Map.Entry<UUID, Integer> e : data.banked(hall).entrySet()) {
			if (level.getEntity(e.getKey()) instanceof Villager v && v.isAlive()) {
				int xp = data.takeBanked(hall, e.getKey());
				ModAttachments.TRAINER_BATTLES.set(v, ModAttachments.TRAINER_BATTLES.getOrElse(v, 0) + 1);
				BuilderLevels.addXp(level, v, Guilds.trainerXp(v, xp), null);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Where side {@code s}'s Pokémon stands: from the ring's centre towards its box. */
	public static Vec3 spot(CupBout bout, int s) {
		Vec3 ring = Vec3.atBottomCenterOf(bout.ring);
		Vec3 box = Vec3.atBottomCenterOf(bout.boxes[s]);
		return new Vec3(ring.x + (box.x - ring.x) * SPOT, ring.y, ring.z + (box.z - ring.z) * SPOT);
	}

	/** Keeps a trainer who's at the Arena in their box, facing the ring. */
	private static void toBox(ServerLevel level, CupBout bout, int s, boolean start) {
		if (!(level.getEntity(bout.trainers[s]) instanceof Villager v) || !v.isAlive() || v.isSleeping()) {
			return;
		}
		Vec3 box = Vec3.atBottomCenterOf(bout.boxes[s]);
		double d = v.position().distanceToSqr(box);
		if (d > AT_THE_ARENA * AT_THE_ARENA) {
			return;
		}
		if (start || d > 9) {
			v.teleportTo(box.x, box.y, box.z);
		}
		v.getNavigation().stop();
		v.getLookControl().setLookAt(Vec3.atBottomCenterOf(bout.ring).add(0, 1, 0));
	}

	/** Tells everyone at the Arena: in chat, or on the action bar. */
	private static void tell(ServerLevel level, CupBout bout, Component message, boolean actionBar) {
		Vec3 ring = Vec3.atCenterOf(bout.ring);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(ring) <= AT_THE_ARENA * AT_THE_ARENA) {
				if (actionBar) {
					Chat.actionBar(player, message);
				} else {
					Chat.chat(player, message);
				}
			}
		}
	}

	/** The entrant with this id (one not in the Cup's list, as a test's, stands for the host). */
	static CupData.Entrant entrant(CupData.Cup cup, UUID id, BlockPos host) {
		for (CupData.Entrant e : cup.entrants) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		for (CupData.Entrant e : cup.bracket) {
			if (e != null && e.id().equals(id)) {
				return e;
			}
		}
		return new CupData.Entrant(CupData.Kind.TRAINER, id, "", 1, 0, host);
	}

	/** A village's name: its hall's while it's loaded, else as the caravans' list has it. */
	static Component villageName(ServerLevel level, BlockPos hall) {
		if (!level.isLoaded(hall)) {
			Caravans.Village v = Caravans.Data.get(level).village(hall);
			if (v != null && !v.name().getString().isEmpty()) {
				return v.name();
			}
		}
		return VillageHalls.name(level, hall);
	}

	private static String key(ServerLevel level, BlockPos host) {
		return level.dimension().location() + "@" + host.asLong();
	}

	/** The Pokémon side {@code s} of the host's ring has out now (tests), or null. */
	@Nullable
	public static Entity shown(ServerLevel level, BlockPos host, int s) {
		Shown[] shown = OUT.get(key(level, host));
		return shown == null || shown[s] == null ? null : shown[s].entity();
	}
}
