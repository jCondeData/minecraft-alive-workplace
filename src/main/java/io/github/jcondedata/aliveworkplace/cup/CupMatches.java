package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Players in the Festival Cup (ROADMAP 28.20). A bout with a signed-up player is called in chat, with a bell and a
 * clickable [I'm ready]; each player has {@link #WINDOW} ticks to click it inside the Arena (within {@link #INSIDE} of
 * the ring), or loses by walkover (offline too). Once every player in it is ready the bout is a real battle through
 * {@link CupBattles} (Cobblemon): against a villager with the entrant or their delegate as the trainer, against a player
 * player-versus-player; both in the theme's format, level and rules, with healed copies. Fleeing loses. A battle that
 * vanished without a result (a restart) is called again. The winner goes on; a player who wins is paid the purse
 * ({@link #PURSE} a bout, {@link #FINAL_PURSE} more for the final, half again at a City host; emeralds at
 * {@link Money#DOLLARS_PER_EMERALD} without CobbleDollars). [Watch] on the Cup page and the Arena's notice board puts a
 * player in the battle's spectator view.
 */
public final class CupMatches {
	/** Two minutes to click [I'm ready]. */
	public static final int WINDOW = 2 * 60 * 20;
	/** How near the ring a player must be to be ready ("inside the Arena"). */
	public static final double INSIDE = 24;
	/** The purse, in PokéDollars: for each bout won, and more for the final. */
	public static final long PURSE = 100_000;
	public static final long FINAL_PURSE = 500_000;
	static final int TICK_EVERY = 20;

	/** A called bout with a player: the round, both entrants (bracket order), the ring and boxes, when it was called, who's ready. */
	public static final class Call {
		public final int round;
		public final UUID[] sides = new UUID[2];
		public final BlockPos ring;
		public final BlockPos[] boxes = new BlockPos[2];
		public long calledAt;
		public final boolean[] ready = new boolean[2];
		/** Both ready and the battle started (or being started). */
		public boolean fighting;
		/** The running battle; not saved (a restart ends it, and the bout is called again). */
		@Nullable
		public UUID battle;

		public Call(int round, UUID a, UUID b, BlockPos ring, BlockPos boxA, BlockPos boxB, long calledAt) {
			this.round = round;
			sides[0] = a;
			sides[1] = b;
			this.ring = ring.immutable();
			boxes[0] = boxA.immutable();
			boxes[1] = boxB.immutable();
			this.calledAt = calledAt;
		}

		/** The side of {@code id}, or -1. */
		public int side(UUID id) {
			return sides[0].equals(id) ? 0 : sides[1].equals(id) ? 1 : -1;
		}

		public CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putInt("round", round);
			Nbt.putUuid(t, "a", sides[0]);
			Nbt.putUuid(t, "b", sides[1]);
			t.putLong("ring", ring.asLong());
			t.putLong("boxA", boxes[0].asLong());
			t.putLong("boxB", boxes[1].asLong());
			t.putLong("calledAt", calledAt);
			t.putBoolean("readyA", ready[0]);
			t.putBoolean("readyB", ready[1]);
			t.putBoolean("fighting", fighting);
			return t;
		}

		@Nullable
		public static Call load(CompoundTag t) {
			if (!Nbt.hasUuid(t, "a") || !Nbt.hasUuid(t, "b")) {
				return null;
			}
			Call c = new Call(Nbt.getInt(t, "round"), Nbt.getUuid(t, "a"), Nbt.getUuid(t, "b"), BlockPos.of(Nbt.getLong(t, "ring")),
				BlockPos.of(Nbt.getLong(t, "boxA")), BlockPos.of(Nbt.getLong(t, "boxB")), Nbt.getLong(t, "calledAt"));
			c.ready[0] = Nbt.getBoolean(t, "readyA");
			c.ready[1] = Nbt.getBoolean(t, "readyB");
			c.fighting = Nbt.getBoolean(t, "fighting");
			return c;
		}
	}

	private CupMatches() {
	}

	public static void init() {
		Platform.get().onUseBlock((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			return noticeBoard(server, sp, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	// ---------------------------------------------------------------- the call

	/** Whether {@code p} is a bout to call (one with a player) rather than an exhibition. */
	public static boolean withPlayer(CupBouts.Pairing p) {
		return p.a().kind() == CupData.Kind.PLAYER || p.b().kind() == CupData.Kind.PLAYER;
	}

	/** Calls the bout {@code p} at {@code ring}: each player told in chat, with a bell and [I'm ready]; everyone at the Arena told who's next. */
	public static Call call(ServerLevel level, BlockPos host, CupData.Cup cup, CupBouts.Pairing p, BlockPos ring, BlockPos boxA, BlockPos boxB) {
		Call call = new Call(p.round(), p.a().id(), p.b().id(), ring, boxA, boxB, level.getGameTime());
		cup.call = call;
		CupData.get(level).setDirty();
		ring(level, host, cup, call);
		return call;
	}

	/** Tells each player in the call that their bout is next (again, when it's called again). */
	static void ring(ServerLevel level, BlockPos host, CupData.Cup cup, Call call) {
		for (int s = 0; s < 2; s++) {
			CupData.Entrant me = CupBouts.entrant(cup, call.sides[s], host);
			CupData.Entrant them = CupBouts.entrant(cup, call.sides[1 - s], host);
			ServerPlayer player = me.kind() == CupData.Kind.PLAYER ? level.getServer().getPlayerList().getPlayer(me.id()) : null;
			if (player != null) {
				Chat.system(player, callText(level, host, call, them));
				player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1f, 1f);
			}
		}
		CupData.Entrant a = CupBouts.entrant(cup, call.sides[0], host);
		CupData.Entrant b = CupBouts.entrant(cup, call.sides[1], host);
		tellAt(level, call.ring, Component.translatable("message.aliveworkplace.cup.called", a.display(), CupBouts.villageName(level, a.village()),
			b.display(), CupBouts.villageName(level, b.village())).withStyle(ChatFormatting.YELLOW));
	}

	/** The call in a player's chat: who they meet, where, the two minutes, and the [I'm ready] button. */
	public static Component callText(ServerLevel level, BlockPos host, Call call, CupData.Entrant opponent) {
		MutableComponent line = Component.translatable("message.aliveworkplace.cup.call", call.round, opponent.display(),
			CupBouts.villageName(level, opponent.village()), CupBouts.villageName(level, host),
			Component.literal(call.ring.getX() + ", " + call.ring.getY() + ", " + call.ring.getZ()), WINDOW / 1200).withStyle(ChatFormatting.GOLD);
		line.append(" ").append(Component.translatable("message.aliveworkplace.cup.ready_button").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/workplace cup ready"))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.cup.ready_hover")))));
		return line;
	}

	/** {@code /workplace cup ready}: the player clicked [I'm ready]. Returns what to tell them. */
	public static Component ready(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		for (Map.Entry<BlockPos, CupData.Cup> e : CupData.get(level).all().entrySet()) {
			Call call = e.getValue().call;
			int s = call == null ? -1 : call.side(player.getUUID());
			if (s < 0 || call.fighting) {
				continue;
			}
			if (player.position().distanceToSqr(Vec3.atCenterOf(call.ring)) > INSIDE * INSIDE) {
				return Component.translatable("message.aliveworkplace.cup.come_inside", CupBouts.villageName(level, e.getKey())).withStyle(ChatFormatting.YELLOW);
			}
			call.ready[s] = true;
			CupData.get(level).setDirty();
			if (allReady(e.getValue(), call, e.getKey())) {
				start(level, e.getKey(), e.getValue());
				return Component.translatable("message.aliveworkplace.cup.ready_go").withStyle(ChatFormatting.GREEN);
			}
			return Component.translatable("message.aliveworkplace.cup.ready_wait").withStyle(ChatFormatting.GREEN);
		}
		return Component.translatable("message.aliveworkplace.cup.no_call").withStyle(ChatFormatting.GRAY);
	}

	static boolean allReady(CupData.Cup cup, Call call, BlockPos host) {
		for (int s = 0; s < 2; s++) {
			if (CupBouts.entrant(cup, call.sides[s], host).kind() == CupData.Kind.PLAYER && !call.ready[s]) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- tick by tick

	/** The called bout at {@code host}, one step: the walkover once the window is over, a battle that vanished called again. */
	public static void tick(ServerLevel level, BlockPos host, CupData.Cup cup) {
		Call call = cup.call;
		if (call == null) {
			return;
		}
		if (call.fighting) {
			if (call.battle == null || !CupBattles.EXTENSION.call(b -> b.running(call.battle), false)) {
				// no result came (a restart, or the battle was stopped): called again
				call.fighting = false;
				call.battle = null;
				call.ready[0] = call.ready[1] = false;
				call.calledAt = level.getGameTime();
				CupData.get(level).setDirty();
				ring(level, host, cup, call);
			}
			return;
		}
		if (level.getGameTime() - call.calledAt < WINDOW) {
			return;
		}
		// the window is over: a player who isn't ready loses by walkover (both: the second in the bracket)
		int loser = -1;
		for (int s = 1; s >= 0; s--) {
			if (CupBouts.entrant(cup, call.sides[s], host).kind() == CupData.Kind.PLAYER && !call.ready[s]) {
				loser = s;
				break;
			}
		}
		if (loser < 0) {
			start(level, host, cup);
			return;
		}
		finish(level, host, cup, call.sides[1 - loser], How.ABSENT);
	}

	/** How a bout with a player was decided. */
	public enum How {
		BATTLE, FLED, ABSENT, NO_TEAM, NO_OPPONENT
	}

	/** Starts the battle: each player told who goes and who's left out (and why); a player with nobody eligible loses. */
	static void start(ServerLevel level, BlockPos host, CupData.Cup cup) {
		Call call = cup.call;
		CupThemes.Theme theme = Cups.theme(cup);
		if (call == null || theme == null) {
			return;
		}
		CupData.Entrant[] who = {CupBouts.entrant(cup, call.sides[0], host), CupBouts.entrant(cup, call.sides[1], host)};
		ServerPlayer[] players = new ServerPlayer[2];
		for (int s = 0; s < 2; s++) {
			if (who[s].kind() != CupData.Kind.PLAYER) {
				continue;
			}
			players[s] = level.getServer().getPlayerList().getPlayer(who[s].id());
			if (players[s] == null || players[s].level() != level) {
				finish(level, host, cup, call.sides[1 - s], How.ABSENT);
				return;
			}
		}
		for (int s = 0; s < 2; s++) {
			ServerPlayer p = players[s];
			if (p == null) {
				continue;
			}
			CupBattles.Team team = CupBattles.EXTENSION.call(b -> b.team(p, theme), new CupBattles.Team(java.util.List.of(), java.util.List.of()));
			if (!team.leftOut().isEmpty()) {
				Chat.system(p, Component.translatable("message.aliveworkplace.cup.left_out", team.leftOut().size()).withStyle(ChatFormatting.YELLOW));
				team.leftOut().forEach(line -> Chat.system(p, Component.literal(" - ").append(line).withStyle(ChatFormatting.GRAY)));
			}
			if (team.going().isEmpty()) {
				Chat.system(p, Component.translatable("message.aliveworkplace.cup.no_team").withStyle(ChatFormatting.RED));
				finish(level, host, cup, call.sides[1 - s], How.NO_TEAM);
				return;
			}
			Chat.system(p, Component.translatable("message.aliveworkplace.cup.going", join(team.going())).withStyle(ChatFormatting.GREEN));
		}
		UUID battle;
		if (players[0] != null && players[1] != null) {
			battle = CupBattles.EXTENSION.call(b -> b.versusPlayer(players[0], players[1], theme), null);
		} else {
			int ps = players[0] != null ? 0 : 1;
			int vs = 1 - ps;
			if (!(CupDays.standIn(level, who[vs].id(), call.ring) instanceof Villager trainer) || !trainer.isAlive()) {
				finish(level, host, cup, call.sides[ps], How.NO_OPPONENT);
				return;
			}
			Vec3 box = Vec3.atBottomCenterOf(call.boxes[vs]);
			if (trainer.position().distanceToSqr(box) < CupBouts.AT_THE_ARENA * CupBouts.AT_THE_ARENA) {
				trainer.teleportTo(box.x, box.y, box.z);
				trainer.getNavigation().stop();
			}
			battle = CupBattles.EXTENSION.call(b -> b.versusVillager(players[ps], trainer, who[vs].display(), Math.max(1, who[vs].tier()), who[vs].id(), theme), null);
		}
		if (battle == null) { // a player already in a battle, or Cobblemon said no: ready again, in a new window
			for (ServerPlayer p : players) {
				if (p != null) {
					Chat.system(p, Component.translatable("message.aliveworkplace.cup.cant_start").withStyle(ChatFormatting.RED));
				}
			}
			call.ready[0] = call.ready[1] = false;
			call.calledAt = level.getGameTime();
			CupData.get(level).setDirty();
			return;
		}
		call.fighting = true;
		call.battle = battle;
		CupData.get(level).setDirty();
		tellAt(level, call.ring, Component.translatable("message.aliveworkplace.cup.bout_start", call.round, Component.translatable(theme.name()),
			who[0].display(), CupBouts.villageName(level, who[0].village()), who[1].display(), CupBouts.villageName(level, who[1].village()))
			.withStyle(ChatFormatting.GOLD).append(" ").append(watchButton()));
	}

	private static Component join(java.util.List<Component> parts) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			out.append(i == 0 ? Component.empty() : Component.literal(", ")).append(parts.get(i));
		}
		return out;
	}

	/** A battle of a called bout ended (from {@link CupBattles}'s filling): {@code winner} (an entrant's id) goes on. */
	public static void decided(MinecraftServer server, UUID battle, UUID winner, boolean fled) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Map.Entry<BlockPos, CupData.Cup> e : CupData.get(level).all().entrySet()) {
				Call call = e.getValue().call;
				if (call != null && battle.equals(call.battle) && call.side(winner) >= 0) {
					finish(level, e.getKey(), e.getValue(), winner, fled ? How.FLED : How.BATTLE);
					return;
				}
			}
		}
	}

	/** The bout is decided: the result kept, trainer XP, the purse to a player who won, everyone at the Arena told. */
	static void finish(ServerLevel level, BlockPos host, CupData.Cup cup, UUID winner, How how) {
		Call call = cup.call;
		if (call == null) {
			return;
		}
		CupData.Entrant win = CupBouts.entrant(cup, winner, host);
		CupData.Entrant lose = CupBouts.entrant(cup, call.sides[1 - call.side(winner)], host);
		cup.call = null;
		cup.results.add(new CupData.Result(call.round, win.id(), lose.id()));
		CupBouts.award(level, win, true);
		CupBouts.award(level, lose, false);
		CupData.get(level).setDirty();
		String key = switch (how) {
			case BATTLE -> "message.aliveworkplace.cup.match_won";
			case FLED -> "message.aliveworkplace.cup.match_fled";
			case ABSENT -> "message.aliveworkplace.cup.match_absent";
			case NO_TEAM -> "message.aliveworkplace.cup.match_no_team";
			case NO_OPPONENT -> "message.aliveworkplace.cup.match_no_opponent";
		};
		Component line = Component.translatable(key, win.display(), CupBouts.villageName(level, win.village()), lose.display(),
			CupBouts.villageName(level, lose.village())).withStyle(ChatFormatting.GOLD);
		tellAt(level, call.ring, line);
		for (CupData.Entrant e : new CupData.Entrant[] {win, lose}) {
			ServerPlayer p = e.kind() == CupData.Kind.PLAYER ? level.getServer().getPlayerList().getPlayer(e.id()) : null;
			if (p != null && p.position().distanceToSqr(Vec3.atCenterOf(call.ring)) > CupBouts.AT_THE_ARENA * CupBouts.AT_THE_ARENA) {
				Chat.system(p, line); // a player away from the ring reads it too
			}
		}
		if (win.kind() == CupData.Kind.PLAYER) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(win.id());
			if (p != null) {
				long dollars = purse(level, host, cup, win.id());
				int emeralds = (int) (dollars / Math.max(1, Money.DOLLARS_PER_EMERALD));
				Money.pay(p, dollars, emeralds);
				Chat.system(p, Component.translatable("message.aliveworkplace.cup.purse", Money.describe(dollars, emeralds)).withStyle(ChatFormatting.GREEN));
			}
		}
	}

	/** What a player who just won a bout gets: {@link #PURSE}, {@link #FINAL_PURSE} more if it was the final, half again at a City host. */
	public static long purse(ServerLevel level, BlockPos host, CupData.Cup cup, UUID winner) {
		CupData.Entrant champ = CupBouts.champion(cup);
		long dollars = PURSE + (champ != null && champ.id().equals(winner) ? FINAL_PURSE : 0);
		return VillageRanks.of(level, host) == VillageRanks.Rank.CITY ? dollars * 3 / 2 : dollars;
	}

	// ---------------------------------------------------------------- watching

	/** The [Watch] button in chat. */
	public static Component watchButton() {
		return Component.translatable("message.aliveworkplace.cup.watch_button").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/workplace cup watch"))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.cup.watch_hover"))));
	}

	/** The battle of the running player bout at {@code host}, or null. */
	@Nullable
	public static UUID running(ServerLevel level, BlockPos host) {
		CupData.Cup cup = CupData.get(level).existing(host);
		Call call = cup == null ? null : cup.call;
		return call != null && call.fighting && call.battle != null && CupBattles.EXTENSION.call(b -> b.running(call.battle), false) ? call.battle : null;
	}

	/** {@code /workplace cup watch}, or [Watch] on the Cup page: the spectator view of the nearest running player bout. */
	public static Component watch(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		UUID best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<BlockPos, CupData.Cup> e : CupData.get(level).all().entrySet()) {
			UUID battle = running(level, e.getKey());
			Call call = e.getValue().call;
			if (battle == null || call.side(player.getUUID()) >= 0) {
				continue;
			}
			double d = player.position().distanceToSqr(Vec3.atCenterOf(call.ring));
			if (d < bestDistance) {
				bestDistance = d;
				best = battle;
			}
		}
		if (best == null) {
			return Component.translatable("message.aliveworkplace.cup.nothing_to_watch").withStyle(ChatFormatting.GRAY);
		}
		UUID battle = best;
		return CupBattles.EXTENSION.call(b -> b.watch(player, battle), false)
			? Component.translatable("message.aliveworkplace.cup.watching").withStyle(ChatFormatting.AQUA)
			: Component.translatable("message.aliveworkplace.cup.cant_watch").withStyle(ChatFormatting.RED);
	}

	/** A click on an Arena's notice board while a player bout is fought there: the bout and [Watch] in chat. */
	static boolean noticeBoard(ServerLevel level, ServerPlayer player, BlockPos clicked) {
		for (Map.Entry<BlockPos, CupData.Cup> e : CupData.get(level).all().entrySet()) {
			Call call = e.getValue().call;
			if (call == null || !call.fighting || !clicked.closerThan(call.ring, 40) || running(level, e.getKey()) == null) {
				continue;
			}
			BlockPos board = Arenas.find(level, e.getKey()).map(Arenas.Arena::noticeBoard).orElse(null);
			if (board == null || !clicked.closerThan(board, 2)) {
				continue;
			}
			CupData.Entrant a = CupBouts.entrant(e.getValue(), call.sides[0], e.getKey());
			CupData.Entrant b = CupBouts.entrant(e.getValue(), call.sides[1], e.getKey());
			Chat.system(player, Component.translatable("message.aliveworkplace.cup.board", a.display(), b.display()).withStyle(ChatFormatting.GOLD)
				.append(" ").append(watchButton()));
			return true;
		}
		return false;
	}

	private static void tellAt(ServerLevel level, BlockPos ring, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(Vec3.atCenterOf(ring)) <= CupBouts.AT_THE_ARENA * CupBouts.AT_THE_ARENA) {
				Chat.chat(player, message);
			}
		}
	}
}
