package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The hall's Wonders page (35.3), a spare tab: every Wonder on the server's list as a card (its icon, name and power),
 * each requirement ticked or crossed with what's missing, the village's cap ("Wonders: 0 of 1 for a Town"), and for one
 * going up here its stage and how far along it is. With every line ticked, a click on the card draws up the plans
 * ({@link WonderPlans#draw}); before that a click says what's still missing. Also "What next?"'s Wonder line: once the village is a Town, the Wonder closest to ready.
 */
public final class WondersPage {
	public static final String PAGE = "wonders";
	/** The first card's slot: the cards fill the rows below the page row, seven to a row. */
	public static final int FIRST = VillageHallScreen.FIRST_ROW + 1;

	/** One Wonder as the page shows it here: its requirements' progress, why it can't be started (null: it can), its site here. */
	public record Card(Wonder wonder, List<Condition.Progress> lines, Optional<Component> capped, Optional<WonderSite> site) {
		/** Every requirement met and the caps clear: the plans may be drawn up. */
		public boolean ready() {
			return capped.isEmpty() && site.isEmpty() && lines.stream().allMatch(Condition.Progress::met);
		}

		public int unmet() {
			return (int) lines.stream().filter(p -> !p.met()).count();
		}
	}

	/** Adds the Wonders tab, while 2.0 isn't finished only where the gate is open (tests, the showcase). */
	public static void init() {
		if (Expansions.on(Expansions.M35)) {
			HallPages.registerSpare(PAGE, WondersPage::tab, WondersPage::header, WondersPage::fill);
		}
	}

	/** The slot of the {@code i}th card. */
	public static int slot(int i) {
		return FIRST + i / 7 * 9 + i % 7;
	}

	/** Every Wonder on the list as the village round {@code hall} sees it, in file order. */
	public static List<Card> cards(ServerLevel level, BlockPos hall) {
		List<Card> out = new ArrayList<>();
		List<WonderSite> here = WonderManager.get(level).in(hall);
		for (Wonder wonder : Wonders.all()) {
			Optional<WonderSite> site = here.stream().filter(s -> s.wonder().equals(wonder.id())).findFirst();
			out.add(new Card(wonder, Wonders.requirements(level, hall, wonder), site.isPresent() ? Optional.empty() : Wonders.capRefusal(level, hall, wonder.id()), site));
		}
		return out;
	}

	static ItemStack tab(ServerLevel level, BlockPos hall) {
		List<Component> lore = new ArrayList<>();
		if (!Wonders.ENABLED) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.wonders.off", ChatFormatting.GRAY));
		} else {
			lore.add(VillageHallScreen.line(Wonders.capLine(level, hall), ChatFormatting.WHITE));
			lore.add(VillageHallScreen.line("screen.aliveworkplace.wonders.hint", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(ModItems.WONDER_PLAN, Component.translatable("screen.aliveworkplace.wonders"), ChatFormatting.GOLD, lore.toArray(Component[]::new));
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(ModItems.WONDER_PLAN, Component.translatable("screen.aliveworkplace.wonders.title", VillageHalls.name(level, hall)), ChatFormatting.GOLD,
			VillageHallScreen.line("screen.aliveworkplace.wonders.about", ChatFormatting.GRAY),
			VillageHallScreen.line(Wonders.capLine(level, hall), ChatFormatting.WHITE),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.wonders.cost", WonderPlans.money(WonderPlans.COST)), ChatFormatting.GRAY));
	}

	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		if (!Wonders.ENABLED) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.wonders.off"), ChatFormatting.GRAY), null);
			return;
		}
		List<Card> cards = cards(level, hall);
		if (cards.isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.wonders.none"), ChatFormatting.GRAY), null);
			return;
		}
		for (int i = 0; i < cards.size(); i++) {
			int slot = slot(i);
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			Card card = cards.get(i);
			menu.button(slot, card(level, hall, card), card.site().isPresent() ? null : p -> {
				WonderPlans.Drawn drawn = WonderPlans.draw(level, hall, p, card.wonder().id());
				Chat.chat(p, drawn.message());
				VillageHallScreen.renderPage(menu, level, hall, HallPages.all().stream().filter(pg -> pg.id().equals(PAGE)).findFirst().orElseThrow(), p);
				menu.broadcastChanges();
			});
		}
	}

	/** A Wonder's card: its name and power, each requirement ticked or crossed, the cap, its stage here, and what a click does. */
	public static ItemStack card(ServerLevel level, BlockPos hall, Card card) {
		Wonder wonder = card.wonder();
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(wonder.descriptionText(), ChatFormatting.AQUA));
		if (card.site().isPresent()) {
			WonderSite site = card.site().get();
			int stages = Math.max(1, wonder.stages().size());
			if (site.done()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.wonders.finished", site.finished().get()), ChatFormatting.GREEN));
				// 35.8: how much of it stands, and its powers: on, or stopped while it's damaged
				lore.add(VillageHallScreen.line(WonderStages.standingLine(site), site.kept().damaged() ? ChatFormatting.RED : ChatFormatting.DARK_GREEN));
				for (Wonder.Power power : wonder.powers()) {
					lore.add(VillageHallScreen.line(Component.translatable(site.empowered() ? "screen.aliveworkplace.wonders.power_on" : "screen.aliveworkplace.wonders.power_off",
						power.power().describe()), site.empowered() ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
				}
			} else {
				int stage = Math.max(1, Math.min(site.stage(), stages));
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.wonders.going_up", stage, stages,
					wonder.stages().get(stage - 1).nameText(), (stage - 1) * 100 / stages), ChatFormatting.YELLOW));
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.wonders.crew", WonderCrews.leadName(level, site),
					WonderCrews.crew(level, site).size(), WonderCrews.limit(site)), ChatFormatting.AQUA));
			}
			return VillageHallScreen.icon(icon(wonder), wonder.nameText().copy(), ChatFormatting.GOLD, lore.toArray(Component[]::new));
		}
		lore.add(VillageHallScreen.line("screen.aliveworkplace.wonders.needs", ChatFormatting.GOLD));
		for (Condition.Progress p : card.lines()) {
			lore.add(VillageHallScreen.line(Component.translatable(p.met() ? "screen.aliveworkplace.wonders.tick" : "screen.aliveworkplace.wonders.cross", p.line()),
				p.met() ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
		lore.add(VillageHallScreen.line(Wonders.capLine(level, hall), card.capped().isPresent() ? ChatFormatting.RED : ChatFormatting.WHITE));
		if (card.ready()) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.wonders.draw", WonderPlans.money(WonderPlans.COST)), ChatFormatting.GREEN));
		} else if (card.capped().isPresent()) {
			lore.add(VillageHallScreen.line(card.capped().get(), ChatFormatting.GRAY));
		} else {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.wonders.not_yet", ChatFormatting.GRAY));
		}
		ItemStack stack = VillageHallScreen.icon(icon(wonder), wonder.nameText().copy(), card.ready() ? ChatFormatting.GREEN : ChatFormatting.GOLD, lore.toArray(Component[]::new));
		if (card.ready()) {
			stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	static Item icon(Wonder wonder) {
		Item item = BuiltInRegistries.ITEM.get(wonder.icon());
		return item == Items.AIR ? ModItems.WONDER_PLAN : item;
	}

	/**
	 * "What next?" (35.3): once the village is a Town (and has room for a Wonder), the Wonder closest to ready: its plans
	 * if every line is ticked, else the first line it lacks and how many more.
	 */
	public static List<VillageAdvice.Tip> tips(ServerLevel level, BlockPos hall) {
		if (!Wonders.ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)
			|| entity.rank().ordinal() < VillageRanks.Rank.TOWN.ordinal()) {
			return List.of();
		}
		Card best = null;
		for (Card card : cards(level, hall)) {
			if (card.capped().isPresent() || card.site().isPresent()) {
				continue;
			}
			if (best == null || card.unmet() < best.unmet()) {
				best = card;
			}
		}
		if (best == null) {
			return List.of();
		}
		if (best.ready()) {
			return List.of(new VillageAdvice.Tip("wonder_ready", ModItems.WONDER_PLAN, best.wonder().nameText(), WonderPlans.money(WonderPlans.COST)));
		}
		Condition.Progress missing = best.lines().stream().filter(p -> !p.met()).findFirst().orElseThrow();
		return List.of(best.unmet() == 1
			? new VillageAdvice.Tip("wonder", ModItems.WONDER_PLAN, best.wonder().nameText(), missing.line())
			: new VillageAdvice.Tip("wonder_more", ModItems.WONDER_PLAN, best.wonder().nameText(), missing.line(), best.unmet() - 1));
	}

	private WondersPage() {
	}
}
