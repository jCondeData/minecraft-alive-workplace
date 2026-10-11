package io.github.jcondedata.aliveworkplace.realm;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The weekly leaderboard (ROADMAP 33.19). Once a week, on {@link #DAY} at {@link #HOUR}:00 server time (configs
 * {@code leaderboardDay}, {@code leaderboardHour}; the owner may later choose 7 in-game days instead), everyone online
 * is told in chat how the villages did: the {@link Board#BIGGEST Biggest} (villagers), {@link Board#RICHEST Richest}
 * (emeralds earned that week: takings, board trades and caravan sales, counted by {@link #earned}), {@link Board#HAPPIEST
 * Happiest} (the week's average wellbeing from the hall's rounds, {@link #round}), Most Legends (Most Masters while
 * Legends are off) and the {@link Board#REALM Greatest realm} (members, then villagers), the top three of each with the
 * village, its owner and the number. A tie goes to the bigger village. Each winner's chronicle notes it and its hall's
 * name icon wears a ribbon for a week. Only villages with an owner are listed, and an owner can leave theirs off with the
 * toggle on the Trade page ({@link #TOGGLE}). The numbers are kept in {@code aliveworkplace_realms} ({@link RealmData}),
 * so unloaded villages count at their last-known values; a week missed while the server was down is announced at the
 * next start, once, and lines nobody was online to hear wait for the next player. {@code /workplace leaderboard} shows
 * the standings so far. Config {@code weeklyLeaderboard}.
 */
public final class Leaderboard {
	/** Config {@code weeklyLeaderboard}: off, nothing is counted, announced or shown; what was counted stays saved. */
	public static boolean ENABLED = true;
	/** Config {@code leaderboardDay}. */
	public static DayOfWeek DAY = DayOfWeek.SUNDAY;
	/** Config {@code leaderboardHour}, 0 to 23. */
	public static int HOUR = 19;
	/** The server's clock (GameTests set their own). */
	public static Clock CLOCK = Clock.systemDefaultZone();
	/** A week, in ms. */
	public static final long WEEK = 7L * 24 * 3600 * 1000;
	/** Places shown on each board. */
	public static final int SHOWN = 3;
	/** The toggle's slot in the Trade page's row. */
	public static final int TOGGLE = 1;
	/** The mark the hall's name icon carries while it wears a ribbon (drawn by the hall's screen). */
	public static final String RIBBON = "ribbon";

	/** The boards, in the order they're read out. */
	public enum Board {
		BIGGEST, RICHEST, HAPPIEST, LEGENDS, REALM;

		/** Its id in the words: "legends" reads "masters" while Legends are off. */
		public String id() {
			if (this == LEGENDS && !io.github.jcondedata.aliveworkplace.legend.Legends.ENABLED) {
				return "masters";
			}
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A place on a board: the village (a realm's capital), its name, its owner's name, its number and its villagers. */
	public record Standing(GlobalPos hall, Component name, String owner, long value, int villagers) {
	}

	private static MinecraftServer lastServer;

	public static void init() {
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % 200 == 0) {
				boolean starting = server != lastServer;
				lastServer = server;
				settle(server, starting);
			}
		});
	}

	// ---- the clock --------------------------------------------------------------------------------------------------------

	/** The last time the board was due at or before {@code millis}: {@link #DAY} at {@link #HOUR}:00, server time. */
	public static long boundary(long millis) {
		ZonedDateTime now = Instant.ofEpochMilli(millis).atZone(CLOCK.getZone());
		ZonedDateTime due = now.with(TemporalAdjusters.previousOrSame(DAY)).truncatedTo(ChronoUnit.DAYS).withHour(Math.max(0, Math.min(23, HOUR)));
		if (due.isAfter(now)) {
			due = due.minusWeeks(1);
		}
		return due.toInstant().toEpochMilli();
	}

	/**
	 * The check (every 10 seconds, and from GameTests with their clock): when the board fell due since the week began it
	 * is announced ({@code starting}: the server's first check, so the week was missed while it was down), and lines held
	 * for want of anyone online go out once someone is.
	 */
	public static void settle(MinecraftServer server, boolean starting) {
		RealmData data = RealmData.get(server);
		long due = boundary(CLOCK.millis());
		if (!ENABLED) {
			if (data.weekStarted() != 0 && due > data.weekStarted()) {
				data.newWeek(due); // off: the week passes unannounced, so switching back on doesn't read out a stale one
			}
			return;
		}
		if (data.weekStarted() == 0) {
			data.newWeek(due); // a new world, or one from before 1.7: the first week starts now
		} else if (due > data.weekStarted()) {
			announce(server, due, starting);
		}
		List<Component> held = data.heldLines();
		if (!held.isEmpty() && !server.getPlayerList().getPlayers().isEmpty()) {
			data.setHeldLines(List.of());
			tellEveryone(server, held);
		}
	}

	/** Reads the boards out, gives the winners their ribbons and chronicle lines, and starts a new week from {@code due}. */
	static void announce(MinecraftServer server, long due, boolean late) {
		RealmData data = RealmData.get(server);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(late ? "message.aliveworkplace.leaderboard.title_late" : "message.aliveworkplace.leaderboard.title")
			.withStyle(ChatFormatting.GOLD));
		long now = CLOCK.millis();
		List<RealmData.Ribbon> ribbons = new ArrayList<>(data.ribbons().stream().filter(r -> r.until() > now).toList());
		long day = Chronicle.day(server.overworld());
		for (Board board : Board.values()) {
			List<Standing> top = standings(server, board);
			lines.add(line(board, top));
			if (!top.isEmpty()) {
				Standing winner = top.get(0);
				String id = board.id();
				ribbons.removeIf(r -> r.hall().equals(winner.hall()) && r.board().equals(id));
				ribbons.add(new RealmData.Ribbon(winner.hall(), id, winner.value(), due + WEEK, day, false));
			}
		}
		data.setRibbons(ribbons);
		data.newWeek(due);
		for (RealmData.Ribbon r : ribbons) {
			note(server, r.hall());
		}
		if (server.getPlayerList().getPlayers().isEmpty()) {
			data.setHeldLines(lines);
		} else {
			data.setHeldLines(List.of());
			tellEveryone(server, lines);
		}
	}

	private static void tellEveryone(MinecraftServer server, List<Component> lines) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			lines.forEach(line -> Chat.chat(player, line));
		}
	}

	/** Writes the win of every ribbon of {@code hall} not yet noted into its chronicle, if the hall is loaded. */
	static void note(MinecraftServer server, GlobalPos hall) {
		ServerLevel level = server.getLevel(hall.dimension());
		if (level == null || !level.isLoaded(hall.pos()) || !(level.getBlockEntity(hall.pos()) instanceof VillageHallBlockEntity)) {
			return;
		}
		RealmData data = RealmData.get(server);
		List<RealmData.Ribbon> all = new ArrayList<>(data.ribbons());
		boolean changed = false;
		for (int i = 0; i < all.size(); i++) {
			RealmData.Ribbon r = all.get(i);
			if (r.hall().equals(hall) && !r.noted()) {
				Chronicle.atHall(level, hall.pos(), Chronicle.Kind.CUP, Component.translatable("chronicle.aliveworkplace.leaderboard.won",
					Component.translatable("leaderboard.aliveworkplace.title." + r.board()), value(r.board(), r.value(), -1)), r.day());
				all.set(i, r.asNoted());
				changed = true;
			}
		}
		if (changed) {
			data.setRibbons(all);
		}
	}

	// ---- counting ---------------------------------------------------------------------------------------------------------

	/** The village at {@code hall} earned {@code cents} hundredths of an emerald (takings, a board trade, a caravan sale). */
	public static void earned(ServerLevel level, BlockPos hall, long cents) {
		if (!ENABLED || cents <= 0) {
			return;
		}
		RealmData data = RealmData.get(level.getServer());
		RealmData.Tally t = data.tally(GlobalPos.of(level.dimension(), hall));
		data.putTally(new RealmData.Tally(t.hall(), t.earned() + cents, t.wellbeing(), t.rounds(), t.legends(), t.masters()));
	}

	/** The hall's round: its wellbeing counts toward the week's average, its Legends and Masters are counted. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, VillageHalls.Census census) {
		if (!ENABLED) {
			return;
		}
		int legends = 0;
		int masters = 0;
		for (Villager v : census.workers()) {
			masters += v.getVillagerData().getLevel() >= 5 ? 1 : 0;
			legends += io.github.jcondedata.aliveworkplace.legend.Legends.of(v).isPresent() ? 1 : 0;
		}
		for (Villager v : census.jobless()) {
			legends += io.github.jcondedata.aliveworkplace.legend.Legends.of(v).isPresent() ? 1 : 0;
		}
		round(level, hall, entity.needs() == null ? -1 : Math.round(entity.needs().wellbeing() * 100), legends, masters);
	}

	/** The same with the numbers counted: {@code wellbeing} in percent (-1: not known yet, the round doesn't count). */
	public static void round(ServerLevel level, BlockPos hall, int wellbeing, int legends, int masters) {
		if (!ENABLED) {
			return;
		}
		RealmData data = RealmData.get(level.getServer());
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		RealmData.Tally t = data.tally(at);
		boolean counts = wellbeing >= 0;
		data.putTally(new RealmData.Tally(at, t.earned(), t.wellbeing() + (counts ? wellbeing : 0), t.rounds() + (counts ? 1 : 0), legends, masters));
		note(level.getServer(), at);
	}

	// ---- the standings ----------------------------------------------------------------------------------------------------

	/** {@code board}'s places so far this week, best first (every one, not only the top three). */
	public static List<Standing> standings(MinecraftServer server, Board board) {
		RealmData data = RealmData.get(server);
		List<Standing> out = new ArrayList<>();
		if (board == Board.REALM) {
			if (!Realms.ENABLED) {
				return out;
			}
			for (RealmData.Realm realm : data.realms()) {
				RealmData.Known capital = data.known(realm.capital());
				if (capital == null || capital.owner().isEmpty() || data.leftOff(realm.capital())) {
					continue;
				}
				int villagers = realm.members().stream().mapToInt(m -> Realms.villagers(server, m.hall())).sum();
				out.add(new Standing(realm.capital(), Realms.title(server, realm), Realms.ownerName(server, realm.capital()), realm.members().size(), villagers));
			}
		} else {
			boolean legends = io.github.jcondedata.aliveworkplace.legend.Legends.ENABLED;
			for (RealmData.Known k : data.knownVillages()) {
				if (k.owner().isEmpty() || data.leftOff(k.hall())) {
					continue;
				}
				RealmData.Tally t = data.tally(k.hall());
				long value = switch (board) {
					case BIGGEST -> k.villagers();
					case RICHEST -> t.earned();
					case HAPPIEST -> t.rounds() > 0 ? Math.round((double) t.wellbeing() / t.rounds()) : k.wellbeing();
					default -> legends ? t.legends() : t.masters();
				};
				if (value > 0) {
					out.add(new Standing(k.hall(), Realms.name(server, k.hall()), Realms.ownerName(server, k.hall()), value, k.villagers()));
				}
			}
		}
		out.sort(Comparator.comparingLong(Standing::value).reversed().thenComparing(Comparator.comparingInt(Standing::villagers).reversed())
			.thenComparing(s -> s.name().getString()));
		return out;
	}

	/** "Biggest: 1. Thornholm (Alex) 24 villagers, 2. ..." */
	public static Component line(Board board, List<Standing> places) {
		MutableComponent entries = Component.empty();
		for (int i = 0; i < Math.min(SHOWN, places.size()); i++) {
			Standing s = places.get(i);
			if (i > 0) {
				entries.append(", ");
			}
			entries.append(Component.translatable("leaderboard.aliveworkplace.entry", i + 1, s.name(), s.owner(), value(board.id(), s.value(), s.villagers())));
		}
		if (places.isEmpty()) {
			entries.append(Component.translatable(board == Board.REALM ? "leaderboard.aliveworkplace.no_realm" : "leaderboard.aliveworkplace.nobody"));
		}
		return Component.translatable("message.aliveworkplace.leaderboard.line",
			Component.translatable("leaderboard.aliveworkplace.board." + board.id()).withStyle(ChatFormatting.YELLOW), entries);
	}

	/** A number as its board reads it: "24 villagers", "31.5 emeralds", "78% wellbeing"; a realm's {@code villagers} -1: members only. */
	public static Component value(String board, long value, int villagers) {
		String one = value == 1 ? ".one" : ""; // "1 Legend", "1 village"
		return switch (board) {
			case "richest" -> TradePage.money((int) Math.min(Integer.MAX_VALUE, value));
			case "happiest" -> Component.translatable("leaderboard.aliveworkplace.value.happiest", value);
			case "realm" -> villagers < 0 ? Component.translatable("leaderboard.aliveworkplace.value.realm_members" + one, value)
				: Component.translatable("leaderboard.aliveworkplace.value.realm" + one, value, villagers);
			default -> Component.translatable("leaderboard.aliveworkplace.value." + board + one, value);
		};
	}

	/** {@code /workplace leaderboard}: the standings so far this week. */
	public static List<Component> soFar(MinecraftServer server) {
		if (!ENABLED) {
			return List.of(Component.translatable("message.aliveworkplace.leaderboard.off").withStyle(ChatFormatting.GRAY));
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("message.aliveworkplace.leaderboard.so_far",
			Component.translatable("leaderboard.aliveworkplace.day." + DAY.name().toLowerCase(Locale.ROOT)), String.format(Locale.ROOT, "%02d:00", HOUR))
			.withStyle(ChatFormatting.GOLD));
		for (Board board : Board.values()) {
			lines.add(line(board, standings(server, board)));
		}
		return lines;
	}

	// ---- the hall ---------------------------------------------------------------------------------------------------------

	/** The ribbons {@code hall} wears now (none with the leaderboard off). */
	public static List<RealmData.Ribbon> ribbons(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return List.of();
		}
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		long now = CLOCK.millis();
		return RealmData.get(level.getServer()).ribbons().stream().filter(r -> r.hall().equals(at) && r.until() > now).toList();
	}

	/** The hall's name icon's lines: "Ribbon of the week: Biggest village (24 villagers)". */
	public static List<Component> hallLines(ServerLevel level, BlockPos hall) {
		return ribbons(level, hall).stream().map(r -> (Component) Component.translatable("screen.aliveworkplace.hall.ribbon",
			Component.translatable("leaderboard.aliveworkplace.title." + r.board()), value(r.board(), r.value(), -1))).toList();
	}

	/** The Trade page's toggle, or null for a village without an owner (or with the leaderboard off). */
	@Nullable
	public static ItemStack toggleIcon(ServerLevel level, BlockPos hall) {
		if (!ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || entity.owner() == null) {
			return null;
		}
		Component name = VillageHalls.name(level, hall);
		boolean off = RealmData.get(level.getServer()).leftOff(GlobalPos.of(level.dimension(), hall));
		Component when = Component.translatable("screen.aliveworkplace.leaderboard.when",
			Component.translatable("leaderboard.aliveworkplace.day." + DAY.name().toLowerCase(Locale.ROOT)), String.format(Locale.ROOT, "%02d:00", HOUR));
		return VillageHallScreen.icon(off ? Items.PAPER : Items.GOLD_INGOT,
			Component.translatable(off ? "screen.aliveworkplace.leaderboard.left_off" : "screen.aliveworkplace.leaderboard.listed"),
			off ? ChatFormatting.GRAY : ChatFormatting.GOLD,
			VillageHallScreen.line(when, ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable(off ? "screen.aliveworkplace.leaderboard.click_list" : "screen.aliveworkplace.leaderboard.click_leave", name),
				ChatFormatting.DARK_GRAY));
	}

	/** {@code player} leaves the village at {@code hall} off the leaderboard, or puts it back; what to tell them. */
	public static Component toggle(ServerLevel level, BlockPos hall, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Component name = VillageHalls.name(level, hall);
		if (!Realms.mayRule(server, at, player)) {
			return Component.translatable("message.aliveworkplace.leaderboard.not_yours", name).withStyle(ChatFormatting.RED);
		}
		RealmData data = RealmData.get(server);
		boolean off = !data.leftOff(at);
		data.setLeftOff(at, off);
		return Component.translatable(off ? "message.aliveworkplace.leaderboard.left_off" : "message.aliveworkplace.leaderboard.listed", name)
			.withStyle(off ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
	}

	private Leaderboard() {
	}
}
