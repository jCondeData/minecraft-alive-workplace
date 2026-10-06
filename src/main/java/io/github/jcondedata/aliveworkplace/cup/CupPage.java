package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The hall's Cup page (ROADMAP 28.17), a gold block in the page row: the next Cup (theme, day, its rules in plain words),
 * the circuit's villages and who each sends, the players signed up, Sign up / Withdraw, the owner's theme button and the
 * host's roll of champions. A village on no circuit is told what a host needs.
 */
public final class CupPage {
	public static final String PAGE = "cup";
	public static final int CARD = VillageHallScreen.FIRST_ROW;
	public static final int THEME = CARD + 1;
	public static final int SIGN_UP = CARD + 3;
	public static final int WITHDRAW = CARD + 4;
	public static final int BRACKET = CARD + 6;
	/** [Watch] while a player bout is fought (28.20). */
	public static final int WATCH = CARD + 7;
	public static final int CHAMPIONS = CARD + 8;
	/** The circuit's villages (a row), the players signed up (a row), the seeds (a row). */
	public static final int CIRCUIT_ROW = CARD + 9;
	public static final int PLAYERS_ROW = CARD + 18;
	public static final int SEEDS_ROW = CARD + 27;

	private CupPage() {
	}

	public static void init() {
		HallPages.register(PAGE, CupPage::tab, CupPage::header, CupPage::fill);
	}

	static ItemStack tab(ServerLevel level, BlockPos hall) {
		Optional<BlockPos> host = Cups.ENABLED ? Cups.hostFor(level, hall) : Optional.empty();
		CupData.Cup cup = host.map(h -> CupData.get(level).existing(h)).orElse(null);
		CupThemes.Theme theme = cup == null ? null : Cups.theme(cup);
		return VillageHallScreen.icon(Items.GOLD_BLOCK, Component.translatable("screen.aliveworkplace.cup.tab"), ChatFormatting.GOLD,
			theme == null || cup.day < 0
				? VillageHallScreen.line("screen.aliveworkplace.cup.none", ChatFormatting.GRAY)
				: VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.next", Component.translatable(theme.name()), cup.day),
				ChatFormatting.YELLOW),
			VillageHallScreen.line("screen.aliveworkplace.cup.hint", ChatFormatting.DARK_GRAY));
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.GOLD_BLOCK, Component.translatable("screen.aliveworkplace.cup.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, VillageHallScreen.line("screen.aliveworkplace.cup.about", ChatFormatting.GRAY));
	}

	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		Optional<BlockPos> found = Cups.hostFor(level, hall);
		CupData.Cup cup = found.map(h -> CupData.get(level).existing(h)).orElse(null);
		CupThemes.Theme theme = cup == null ? null : Cups.theme(cup);
		if (found.isEmpty() || cup == null || theme == null || cup.day < 0) {
			String why = Cups.hostProblem(level, hall);
			menu.button(CARD, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.cup.none"), ChatFormatting.GRAY,
				VillageHallScreen.line(why == null ? "screen.aliveworkplace.cup.why.soon" : why, ChatFormatting.YELLOW),
				VillageHallScreen.line("screen.aliveworkplace.cup.needs", ChatFormatting.GRAY),
				VillageHallScreen.line("screen.aliveworkplace.cup.needs_circuit", ChatFormatting.GRAY)), null);
			champions(menu, level, cup);
			return;
		}
		BlockPos host = found.get();
		menu.button(CARD, card(level, host, cup, theme), null);
		menu.button(THEME, VillageHallScreen.icon(Items.PAINTING, Component.translatable("screen.aliveworkplace.cup.theme", Component.translatable(theme.name())),
			ChatFormatting.AQUA, VillageHallScreen.line(cup.themePicked ? "screen.aliveworkplace.cup.theme_picked" : "screen.aliveworkplace.cup.theme_in_order",
				ChatFormatting.GRAY), VillageHallScreen.line("screen.aliveworkplace.cup.theme_hint", ChatFormatting.DARK_GRAY)),
			p -> act(menu, level, hall, p, (l, pl) -> Cups.nextTheme(l, host, pl)));
		boolean signed = cup.signups.stream().anyMatch(s -> s.player().equals(viewer.getUUID()));
		menu.button(SIGN_UP, VillageHallScreen.icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.cup.sign_up", VillageHalls.name(level, hall)),
			ChatFormatting.GREEN, VillageHallScreen.line(Cups.mayStandFor(level, viewer.getUUID(), hall) ? "screen.aliveworkplace.cup.sign_up_may"
				: "screen.aliveworkplace.cup.sign_up_not", ChatFormatting.GRAY)),
			p -> act(menu, level, hall, p, (l, pl) -> Cups.signUp(l, hall, pl)));
		menu.button(WITHDRAW, VillageHallScreen.icon(Items.BARRIER, Component.translatable("screen.aliveworkplace.cup.withdraw"), ChatFormatting.RED,
			VillageHallScreen.line(signed ? "screen.aliveworkplace.cup.withdraw_hint" : "screen.aliveworkplace.cup.not_signed", ChatFormatting.GRAY)),
			p -> act(menu, level, hall, p, (l, pl) -> Cups.withdraw(l, hall, pl)));
		List<CupData.Entrant> entrants = Cups.shownEntrants(level, host, cup);
		int capacity = Cups.capacity(VillageRanks.of(level, host));
		List<CupData.Entrant> bracket = cup.closed ? cup.bracket : Cups.bracket(entrants, capacity);
		menu.button(BRACKET, VillageHallScreen.icon(Items.MAP, Component.translatable("screen.aliveworkplace.cup.bracket", capacity), ChatFormatting.WHITE,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.entrants", entrants.size()), ChatFormatting.GRAY),
			bracket.isEmpty() ? VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.why.few", Cups.MIN_ENTRANTS), ChatFormatting.YELLOW)
				: VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.byes", bracket.size(), Cups.byes(bracket)), ChatFormatting.GRAY)), null);
		champions(menu, level, cup);
		if (CupMatches.running(level, host) != null) {
			menu.button(WATCH, VillageHallScreen.icon(Items.ENDER_EYE, Component.translatable("screen.aliveworkplace.cup.watch"), ChatFormatting.AQUA,
				VillageHallScreen.line("screen.aliveworkplace.cup.watch_hint", ChatFormatting.GRAY)), p -> act(menu, level, hall, p, (l, pl) -> CupMatches.watch(pl)));
		}
		int slot = CIRCUIT_ROW;
		for (BlockPos village : Cups.circuit(level, host)) {
			CupData.Entrant sent = entrants.stream().filter(e -> e.village().equals(village) && e.kind() != CupData.Kind.PLAYER
				&& e.kind() != CupData.Kind.HOST_TRAINER).findFirst().orElse(null);
			menu.button(slot++, VillageHallScreen.icon(Items.BELL, VillageHalls.name(level, village), village.equals(host) ? ChatFormatting.GOLD : ChatFormatting.WHITE,
				village.equals(host) ? VillageHallScreen.line("screen.aliveworkplace.cup.host", ChatFormatting.GOLD)
					: VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.blocks", (int) Math.sqrt(village.distSqr(host))), ChatFormatting.GRAY),
				sent == null ? VillageHallScreen.line("screen.aliveworkplace.cup.sends_none", ChatFormatting.DARK_GRAY)
					: VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.sends", who(sent)), ChatFormatting.GREEN)), null);
		}
		slot = PLAYERS_ROW;
		if (cup.signups.isEmpty()) {
			menu.button(slot, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.cup.no_players"), ChatFormatting.GRAY), null);
		}
		for (CupData.Signup s : cup.signups) {
			if (slot >= PLAYERS_ROW + 9) {
				break;
			}
			menu.button(slot++, VillageHallScreen.icon(Items.PLAYER_HEAD, Component.literal(s.name()), ChatFormatting.AQUA,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.stands_for", VillageHalls.name(level, s.village())), ChatFormatting.GRAY)),
				null);
		}
		slot = SEEDS_ROW;
		for (int i = 0; i < entrants.size() && slot < ChoiceMenu.SIZE; i++) {
			CupData.Entrant e = entrants.get(i);
			menu.button(slot++, VillageHallScreen.icon(e.kind() == CupData.Kind.PLAYER ? Items.IRON_SWORD : Items.FILLED_MAP,
				Component.translatable("screen.aliveworkplace.cup.seed", i + 1, e.display()), ChatFormatting.WHITE,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.kind." + e.kind().name().toLowerCase(java.util.Locale.ROOT),
					VillageHalls.name(level, e.village())), ChatFormatting.GRAY)), null);
		}
	}

	/** "Mira, Expert" / "Trainer Leader". */
	static Component who(CupData.Entrant e) {
		return e.kind() == CupData.Kind.PLAYER ? e.display()
			: Component.translatable("screen.aliveworkplace.cup.who", e.display(), io.github.jcondedata.aliveworkplace.build.BuilderLevels.levelName(e.tier()));
	}

	/** The Cup's card: theme, host, day, where sign-up stands, the rules in plain words. */
	static ItemStack card(ServerLevel level, BlockPos host, CupData.Cup cup, CupThemes.Theme theme) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.at", VillageHalls.name(level, host), cup.day), ChatFormatting.YELLOW));
		VillageHallBlockEntity entity = level.getBlockEntity(host) instanceof VillageHallBlockEntity e ? e : null;
		Component status;
		if (cup.closed) {
			status = cup.noCup.isEmpty() ? Component.translatable("screen.aliveworkplace.cup.signup_closed")
				: Component.translatable(cup.noCup, Cups.MIN_ENTRANTS);
		} else if (entity != null && Cups.signupOpen(level, entity, cup)) {
			status = Component.translatable("screen.aliveworkplace.cup.signup_open");
		} else {
			status = Component.translatable("screen.aliveworkplace.cup.signup_opens", entity == null ? cup.day : Cups.opensDay(cup.day, entity));
		}
		lore.add(VillageHallScreen.line(status, cup.noCup.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.RED));
		for (Component rule : rules(theme)) {
			lore.add(VillageHallScreen.line(rule, ChatFormatting.GRAY));
		}
		return VillageHallScreen.icon(Items.GOLD_BLOCK, Component.translatable(theme.name()), ChatFormatting.GOLD, lore.toArray(Component[]::new));
	}

	/** A theme's rules, in plain words. */
	public static List<Component> rules(CupThemes.Theme theme) {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable("screen.aliveworkplace.cup.rules.format", Component.translatable("screen.aliveworkplace.cup.format." + theme.format()),
			theme.bring(), theme.level()));
		if (theme.types().isEmpty()) {
			out.add(Component.translatable("screen.aliveworkplace.cup.rules.any_type"));
		} else {
			net.minecraft.network.chat.MutableComponent types = Component.empty();
			for (int i = 0; i < theme.types().size(); i++) {
				String t = theme.types().get(i);
				types.append(i == 0 ? Component.empty() : Component.literal(", "))
					.append(Component.translatableWithFallback("cobblemon.type." + t, Character.toUpperCase(t.charAt(0)) + t.substring(1)));
			}
			out.add(Component.translatable("screen.aliveworkplace.cup.rules.types", types));
		}
		if (!theme.stage().equals("any")) {
			out.add(Component.translatable("screen.aliveworkplace.cup.rules.stage." + theme.stage()));
		}
		if (theme.partnerDays() > 0) {
			out.add(Component.translatable("screen.aliveworkplace.cup.rules.partner_days", theme.partnerDays()));
		}
		if (theme.workerTypes()) {
			out.add(Component.translatable("screen.aliveworkplace.cup.rules.worker_types"));
		}
		if (!theme.banned().isEmpty()) {
			net.minecraft.network.chat.MutableComponent banned = Component.empty();
			for (int i = 0; i < theme.banned().size(); i++) {
				String label = theme.banned().get(i);
				banned.append(i == 0 ? Component.empty() : Component.literal(", "))
					.append(Component.translatableWithFallback("screen.aliveworkplace.cup.label." + label, label.replace('_', ' ')));
			}
			out.add(Component.translatable("screen.aliveworkplace.cup.rules.banned", banned));
		}
		for (String rule : theme.rules()) {
			String key = rule.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
			out.add(Component.translatableWithFallback("screen.aliveworkplace.cup.rule." + key, rule));
		}
		out.add(Component.translatable("screen.aliveworkplace.cup.rules.hours", Cups.hour(theme.start()), Cups.hour(theme.end())));
		return out;
	}

	/** The host's roll of champions, the newest first. */
	static void champions(ChoiceMenu menu, ServerLevel level, CupData.Cup cup) {
		List<Component> lore = new ArrayList<>();
		if (cup == null || cup.champions.isEmpty()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.cup.no_champions", ChatFormatting.GRAY));
		} else {
			for (int i = cup.champions.size() - 1; i >= 0 && lore.size() < 10; i--) {
				CupData.Champion c = cup.champions.get(i);
				CupThemes.Theme theme = CupThemes.get(c.theme());
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.cup.champion", c.day(),
					theme == null ? Component.literal(c.theme().getPath()) : Component.translatable(theme.name()), c.name(),
					VillageHalls.name(level, c.village())), ChatFormatting.YELLOW));
			}
		}
		menu.button(CHAMPIONS, VillageHallScreen.icon(Items.GOLDEN_HELMET, Component.translatable("screen.aliveworkplace.cup.champions"), ChatFormatting.GOLD,
			lore.toArray(Component[]::new)), null);
	}

	/** Runs a button's action, tells the player and draws the page again. */
	static void act(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer player, BiFunction<ServerLevel, ServerPlayer, Component> action) {
		Chat.actionBar(player, action.apply(level, player));
		for (HallPages.Page page : HallPages.all()) {
			if (page.id().equals(PAGE)) {
				VillageHallScreen.renderPage(menu, level, hall, page, player);
			}
		}
	}
}
