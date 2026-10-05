package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * One Festival Cup bout between two villager entrants (ROADMAP 28.18), as data: both teams, whose Pokémon is out, how
 * much health each has left, and how far the bout has got. It plays out one move at a time ({@link #step}): of the two
 * Pokémon out, the faster moves first; a move hits or misses on its accuracy and does damage by a small formula (the
 * level, the move's power, attack against defence, the same-type bonus, the {@link TypeChart} and a roll of 85-100%). A
 * Pokémon at 0 is out and the next comes on; a side with none left loses. After {@link #MAX_TICKS} the side with more
 * health left wins. Every roll comes from the bout's seed and the step number, so the same seed plays the same bout, and
 * one saved halfway carries on exactly as it would have.
 */
public final class CupBout {
	/** Ticks between moves. */
	public static final int ACTION_TICKS = 30;
	/** 90 seconds at most. */
	public static final int MAX_TICKS = 90 * 20;

	/** A move: its id (Showdown's), type, power, accuracy (0: never misses) and whether it's physical. */
	public record Move(String id, String type, int power, int accuracy, boolean physical) {
		/** The move's name for players (Cobblemon's lang key). */
		public String nameKey() {
			return "cobblemon.move." + id;
		}
	}

	/**
	 * A Pokémon in a bout: its species id, the lang key of its name, its types (lower case), level, stats at that level
	 * and its attacking moves.
	 */
	public record Fighter(String species, String nameKey, List<String> types, int level, int hp, int attack, int defence, int spAttack,
		int spDefence, int speed, List<Move> moves) {
	}

	/** What one step did: who moved (0 or 1), with what, the damage, how effective it was, and whether the target fainted. */
	public record Action(int side, Move move, boolean hit, int damage, double effectiveness, boolean fainted) {
	}

	/** The move every Pokémon has when it has no attack of its own. */
	public static final Move STRUGGLE = new Move("struggle", "normal", 50, 0, true);

	public final UUID[] trainers = new UUID[2];
	@SuppressWarnings("unchecked")
	public final List<Fighter>[] teams = new List[] {new ArrayList<>(), new ArrayList<>()};
	public final int round;
	public final long seed;
	/** Where it's fought: the ring's centre and each side's box. */
	public final BlockPos ring;
	public final BlockPos[] boxes = new BlockPos[2];
	/** Health left, by side and Pokémon. */
	public final int[][] health = new int[2][];
	/** Which Pokémon each side has out. */
	public final int[] out = new int[2];
	public int step;
	/** Ticks the bout has run. */
	public int ticks;
	/** Within an exchange: 0 before the first mover's move, 1 before the second's. */
	public int phase;
	/** The side that moves first in the current exchange. */
	public int first = -1;
	/** -1 while it runs; then the winning side. */
	public int winner = -1;
	/** Whether it ended on time (more health left) rather than a knockout. */
	public boolean onTime;

	public CupBout(UUID a, UUID b, List<Fighter> teamA, List<Fighter> teamB, int round, long seed, BlockPos ring, BlockPos boxA, BlockPos boxB) {
		trainers[0] = a;
		trainers[1] = b;
		teams[0].addAll(teamA);
		teams[1].addAll(teamB);
		this.round = round;
		this.seed = seed;
		this.ring = ring.immutable();
		boxes[0] = boxA.immutable();
		boxes[1] = boxB.immutable();
		for (int s = 0; s < 2; s++) {
			health[s] = teams[s].stream().mapToInt(Fighter::hp).toArray();
		}
		if (teams[0].isEmpty() || teams[1].isEmpty()) {
			winner = teams[0].isEmpty() && !teams[1].isEmpty() ? 1 : 0; // no Pokémon to field: a walkover (both: the higher seed)
		}
	}

	/** A bout's seed: the host, the Cup's day, the round and both trainers. */
	public static long seed(BlockPos host, long day, int round, UUID a, UUID b) {
		long s = host.asLong() * 0x9E3779B97F4A7C15L + day * 31 + round;
		s ^= a.getMostSignificantBits() * 17 ^ a.getLeastSignificantBits();
		s ^= Long.rotateLeft(b.getMostSignificantBits() * 13 ^ b.getLeastSignificantBits(), 21);
		return s;
	}

	public boolean over() {
		return winner >= 0;
	}

	public boolean walkover() {
		return teams[0].isEmpty() || teams[1].isEmpty();
	}

	/** The Pokémon side {@code s} has out, or null when it has none left. */
	@Nullable
	public Fighter current(int s) {
		return out[s] < teams[s].size() ? teams[s].get(out[s]) : null;
	}

	/** Side {@code s}'s health left, as a share of its whole team's (0 to 1, a Pokémon still to come counting in full). */
	public double share(int s) {
		int max = 0;
		int left = 0;
		for (int i = 0; i < teams[s].size(); i++) {
			max += teams[s].get(i).hp();
			left += Math.max(0, health[s][i]);
		}
		return max == 0 ? 0 : left / (double) max;
	}

	/** Moves the bout on by one tick; returns the action it took this tick, if any. */
	@Nullable
	public Action tick() {
		if (over()) {
			return null;
		}
		ticks++;
		if (ticks >= MAX_TICKS) {
			double a = share(0);
			double b = share(1);
			winner = a > b ? 0 : b > a ? 1 : roll(-1).nextBoolean() ? 0 : 1;
			onTime = true;
			return null;
		}
		return ticks % ACTION_TICKS == 0 ? step() : null;
	}

	/** One move: the next mover's, in turn. */
	@Nullable
	public Action step() {
		if (over()) {
			return null;
		}
		Random random = roll(step);
		Fighter[] f = {current(0), current(1)};
		if (phase == 0 || first < 0) {
			first = f[0].speed() > f[1].speed() ? 0 : f[1].speed() > f[0].speed() ? 1 : random.nextBoolean() ? 0 : 1;
			phase = 0;
		}
		int side = phase == 0 ? first : 1 - first;
		int other = 1 - side;
		Fighter attacker = f[side];
		Fighter target = f[other];
		Move move = choose(attacker, target, random);
		boolean hit = move.accuracy() <= 0 || random.nextInt(100) < move.accuracy();
		double effectiveness = TypeChart.multiplier(move.type(), target.types());
		int damage = hit ? damage(attacker, target, move, effectiveness, random) : 0;
		health[other][out[other]] = Math.max(0, health[other][out[other]] - damage);
		boolean fainted = health[other][out[other]] == 0;
		step++;
		if (fainted) {
			out[other]++;
			phase = 0;
			if (out[other] >= teams[other].size()) {
				winner = side;
			}
		} else {
			phase = 1 - phase;
		}
		return new Action(side, move, hit, damage, effectiveness, fainted);
	}

	/** The roll for step {@code n}: the same seed and step always give the same numbers. */
	Random roll(int n) {
		return new Random(seed ^ (0x9E3779B97F4A7C15L * (n + 2)));
	}

	/** Mostly its best move on this target (power, accuracy, same type, effectiveness), sometimes another. */
	static Move choose(Fighter attacker, Fighter target, Random random) {
		if (attacker.moves().isEmpty()) {
			return STRUGGLE;
		}
		Move best = attacker.moves().get(0);
		double bestScore = -1;
		for (Move m : attacker.moves()) {
			double accuracy = m.accuracy() <= 0 ? 100 : m.accuracy();
			double score = m.power() * accuracy / 100.0 * (attacker.types().contains(m.type()) ? 1.5 : 1)
				* TypeChart.multiplier(m.type(), target.types());
			if (score > bestScore) {
				bestScore = score;
				best = m;
			}
		}
		return random.nextInt(4) == 0 ? attacker.moves().get(random.nextInt(attacker.moves().size())) : best;
	}

	/**
	 * The damage: ((2 x level / 5 + 2) x power x attack / defence) / 50 + 2, then x1.5 for a move of its own type, x the
	 * type chart and x a roll of 85-100%; at least 1 unless the chart says 0.
	 */
	public static int damage(Fighter attacker, Fighter target, Move move, double effectiveness, Random random) {
		if (effectiveness <= 0) {
			return 0;
		}
		double attack = move.physical() ? attacker.attack() : attacker.spAttack();
		double defence = Math.max(1, move.physical() ? target.defence() : target.spDefence());
		double base = Math.floor(Math.floor(2.0 * attacker.level() / 5 + 2) * move.power() * attack / defence / 50) + 2;
		double stab = attacker.types().contains(move.type()) ? 1.5 : 1;
		double roll = (85 + random.nextInt(16)) / 100.0;
		return Math.max(1, (int) Math.floor(base * stab * effectiveness * roll));
	}

	/** Plays the bout to its end at once (the same bout as ticking it through); returns every action. */
	public List<Action> playOut() {
		List<Action> log = new ArrayList<>();
		while (!over()) {
			Action a = tick();
			if (a != null) {
				log.add(a);
			}
		}
		return log;
	}

	// ---------------------------------------------------------------- saving

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		Nbt.putUuid(t, "a", trainers[0]);
		Nbt.putUuid(t, "b", trainers[1]);
		t.put("teamA", saveTeam(teams[0]));
		t.put("teamB", saveTeam(teams[1]));
		t.putInt("round", round);
		t.putLong("seed", seed);
		t.putLong("ring", ring.asLong());
		t.putLong("boxA", boxes[0].asLong());
		t.putLong("boxB", boxes[1].asLong());
		t.put("healthA", new IntArrayTag(health[0]));
		t.put("healthB", new IntArrayTag(health[1]));
		t.putInt("outA", out[0]);
		t.putInt("outB", out[1]);
		t.putInt("step", step);
		t.putInt("ticks", ticks);
		t.putInt("phase", phase);
		t.putInt("first", first);
		t.putInt("winner", winner);
		t.putBoolean("onTime", onTime);
		return t;
	}

	@Nullable
	public static CupBout load(CompoundTag t) {
		if (!Nbt.hasUuid(t, "a") || !Nbt.hasUuid(t, "b")) {
			return null;
		}
		CupBout bout = new CupBout(Nbt.getUuid(t, "a"), Nbt.getUuid(t, "b"), loadTeam(Nbt.getList(t, "teamA", Tag.TAG_COMPOUND)),
			loadTeam(Nbt.getList(t, "teamB", Tag.TAG_COMPOUND)), Nbt.getInt(t, "round"), Nbt.getLong(t, "seed"), BlockPos.of(Nbt.getLong(t, "ring")),
			BlockPos.of(Nbt.getLong(t, "boxA")), BlockPos.of(Nbt.getLong(t, "boxB")));
		int[] a = Nbt.getIntArray(t, "healthA");
		int[] b = Nbt.getIntArray(t, "healthB");
		if (a.length == bout.health[0].length && b.length == bout.health[1].length) {
			bout.health[0] = a;
			bout.health[1] = b;
		}
		bout.out[0] = Nbt.getInt(t, "outA");
		bout.out[1] = Nbt.getInt(t, "outB");
		bout.step = Nbt.getInt(t, "step");
		bout.ticks = Nbt.getInt(t, "ticks");
		bout.phase = Nbt.getInt(t, "phase");
		bout.first = t.contains("first") ? Nbt.getInt(t, "first") : -1;
		bout.winner = t.contains("winner") ? Nbt.getInt(t, "winner") : bout.winner;
		bout.onTime = Nbt.getBoolean(t, "onTime");
		return bout;
	}

	private static ListTag saveTeam(List<Fighter> team) {
		ListTag list = new ListTag();
		for (Fighter f : team) {
			CompoundTag t = new CompoundTag();
			t.putString("species", f.species());
			t.putString("name", f.nameKey());
			ListTag types = new ListTag();
			f.types().forEach(type -> types.add(StringTag.valueOf(type)));
			t.put("types", types);
			t.putIntArray("stats", new int[] {f.level(), f.hp(), f.attack(), f.defence(), f.spAttack(), f.spDefence(), f.speed()});
			ListTag moves = new ListTag();
			for (Move m : f.moves()) {
				CompoundTag mt = new CompoundTag();
				mt.putString("id", m.id());
				mt.putString("type", m.type());
				mt.putInt("power", m.power());
				mt.putInt("accuracy", m.accuracy());
				mt.putBoolean("physical", m.physical());
				moves.add(mt);
			}
			t.put("moves", moves);
			list.add(t);
		}
		return list;
	}

	private static List<Fighter> loadTeam(ListTag list) {
		List<Fighter> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = Nbt.compoundAt(list, i);
			int[] s = Nbt.getIntArray(t, "stats");
			if (s.length < 7) {
				continue;
			}
			List<String> types = new ArrayList<>();
			ListTag tl = Nbt.getList(t, "types", Tag.TAG_STRING);
			for (int j = 0; j < tl.size(); j++) {
				types.add(Nbt.stringAt(tl, j));
			}
			List<Move> moves = new ArrayList<>();
			ListTag ml = Nbt.getList(t, "moves", Tag.TAG_COMPOUND);
			for (int j = 0; j < ml.size(); j++) {
				CompoundTag mt = Nbt.compoundAt(ml, j);
				moves.add(new Move(Nbt.getString(mt, "id"), Nbt.getString(mt, "type"), Nbt.getInt(mt, "power"), Nbt.getInt(mt, "accuracy"),
					Nbt.getBoolean(mt, "physical")));
			}
			out.add(new Fighter(Nbt.getString(t, "species"), Nbt.getString(t, "name"), List.copyOf(types), s[0], s[1], s[2], s[3], s[4], s[5], s[6],
				List.copyOf(moves)));
		}
		return out;
	}
}
