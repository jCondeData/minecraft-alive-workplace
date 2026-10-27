package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Village Hall's Repairs page (ROADMAP 36.15): the village's damaged builds and their repairs, the questions waiting
 * first. A click on a build opens it: "Yes, repair it" and "Not now" while it waits (or was left), "Pause" and "Stop" while
 * a builder repairs it, "Carry on" when paused. Nothing is saved here: the answers live with the finished buildings
 * ({@link RepairAsks}).
 */
public final class RepairsPage {
	public static final String PAGE = "repairs";
	/** On a build's view: the answer buttons. */
	public static final int YES = VillageHallScreen.FIRST_ROW + 11;
	public static final int NOT_NOW = VillageHallScreen.FIRST_ROW + 15;
	/** While it's being repaired: pause it, or stop it. */
	public static final int PAUSE = VillageHallScreen.FIRST_ROW + 11;
	public static final int STOP = VillageHallScreen.FIRST_ROW + 15;
	/** On a build that can be refreshed (36.21): the button that hands it to a builder. */
	public static final int REFRESH = VillageHallScreen.FIRST_ROW + 13;
	/** Builds on the page at most: the four rows under the divider, less the last slot when there are more. */
	public static final int SHOWN = ChoiceMenu.SIZE - VillageHallScreen.FIRST_ROW;

	@Nullable
	private static HallPages.Page page;

	/** Adds the page, as one of the mod's own spare pages (its tab gives way to other mods' pages when the row is full). */
	public static void init() {
		page = HallPages.registerSpare(PAGE, RepairsPage::tab, RepairsPage::header, RepairsPage::fill);
	}

	static ItemStack tab(ServerLevel level, BlockPos hall) {
		List<RepairAsks.Entry> entries = RepairAsks.entries(level, hall);
		int waiting = (int) entries.stream().filter(RepairsPage::asked).count();
		int going = (int) entries.stream().filter(e -> e.repair() != null).count();
		ItemStack stack = new ItemStack(Items.ANVIL, Math.max(1, Math.min(64, waiting)));
		return VillageHallScreen.icon(stack, Component.translatable("screen.aliveworkplace.repairs"), ChatFormatting.GOLD,
			VillageHallScreen.line(Words.counted("screen.aliveworkplace.repairs.waiting", waiting, waiting), waiting > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
			VillageHallScreen.line(Words.counted("screen.aliveworkplace.repairs.going", going, going), going > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.repairs.hint", ChatFormatting.DARK_GRAY));
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.ANVIL, Component.translatable("screen.aliveworkplace.repairs.title", VillageHalls.name(level, hall)), ChatFormatting.GOLD,
			VillageHallScreen.line("screen.aliveworkplace.repairs.about", ChatFormatting.GRAY),
			VillageHallScreen.line(io.github.jcondedata.aliveworkplace.build.Upkeep.ASK ? "screen.aliveworkplace.repairs.threats" : "screen.aliveworkplace.repairs.automatic",
				ChatFormatting.DARK_GRAY));
	}

	/** The page: a button per build, the questions waiting first; with more than fit, the last slot says how many more. */
	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		List<RepairAsks.Entry> entries = RepairAsks.entries(level, hall);
		List<Refreshes.Entry> refreshes = Refreshes.entries(level, hall); // 36.21: after the repairs
		if (entries.isEmpty() && refreshes.isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.repairs.none"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.repairs.none_hint", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		int total = entries.size() + refreshes.size();
		int fit = total > SHOWN ? SHOWN - 1 : SHOWN;
		int slot = VillageHallScreen.FIRST_ROW;
		for (RepairAsks.Entry entry : entries.subList(0, Math.min(fit, entries.size()))) {
			BlockPos at = entry.building().placement().origin();
			menu.button(slot++, entryIcon(level, hall, entry, true), p -> {
				renderBuild(menu, level, hall, at, p);
				menu.broadcastChanges();
			});
		}
		for (Refreshes.Entry entry : refreshes.subList(0, Math.max(0, Math.min(fit - entries.size(), refreshes.size())))) {
			BlockPos at = entry.building().placement().origin();
			menu.button(slot++, refreshIcon(level, hall, entry, true), p -> {
				renderRefresh(menu, level, hall, at, p);
				menu.broadcastChanges();
			});
		}
		if (total > fit) {
			menu.button(ChoiceMenu.SIZE - 1, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.repairs.more", total - fit),
				ChatFormatting.GRAY), null);
		}
	}

	private static boolean asked(RepairAsks.Entry e) {
		return e.ask() != null && e.ask().state() == RepairAsks.State.ASKED;
	}

	/** A build as the page shows it: its name, where, what's missing, and where its question or repair stands. */
	public static ItemStack entryIcon(ServerLevel level, BlockPos hall, RepairAsks.Entry entry, boolean clickable) {
		List<Component> lore = new ArrayList<>();
		BlockPos at = entry.building().placement().origin();
		lore.add(VillageHallScreen.line(VillageHallScreen.where(hall, at), ChatFormatting.GRAY));
		Item item;
		BuildSite site = entry.repair();
		if (site != null) {
			item = Items.SCAFFOLDING;
			BuildPlan plan = site.plan(level);
			int percent = plan == null ? 0 : Math.round(site.progress(plan) * 100);
			Component builder = site.builder() != null && level.getEntity(site.builder()) instanceof Villager v ? v.getDisplayName()
				: Component.translatable("screen.aliveworkplace.repairs.a_builder"); // "by a builder" (B608)
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.repairs.repairing", builder, percent), ChatFormatting.AQUA));
			if (entry.ask() == null) {
				lore.add(VillageHallScreen.line("screen.aliveworkplace.repairs.threat", ChatFormatting.DARK_GRAY));
			}
		} else {
			RepairAsks.State state = entry.ask().state();
			int missing = entry.ask().missing();
			item = switch (state) {
				case ASKED -> Items.CRACKED_STONE_BRICKS;
				case YES -> Items.BRICKS;
				case PAUSED -> Items.CLOCK;
				case NOT_NOW -> Items.COBWEB;
			};
			lore.add(VillageHallScreen.line(Words.counted("screen.aliveworkplace.repairs.missing", missing, missing), ChatFormatting.GRAY));
			lore.add(VillageHallScreen.line("screen.aliveworkplace.repairs.state." + state.key(), switch (state) {
				case ASKED -> ChatFormatting.YELLOW;
				case YES -> ChatFormatting.GREEN;
				default -> ChatFormatting.DARK_GRAY;
			}));
		}
		if (clickable) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.repairs.click", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(item, Blueprints.displayName(entry.building().structure()).copy(), ChatFormatting.WHITE, lore.toArray(Component[]::new));
	}

	/** One build's view: back to the list, the build, then its answers. */
	public static void renderBuild(ChoiceMenu menu, ServerLevel level, BlockPos hall, BlockPos at, ServerPlayer viewer) {
		RepairAsks.Entry entry = RepairAsks.entries(level, hall).stream().filter(e -> e.building().placement().origin().equals(at)).findFirst().orElse(null);
		if (entry == null) { // repaired, or put right, while the list was open
			backToList(menu, level, hall, viewer);
			return;
		}
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.repairs.back"), ChatFormatting.WHITE),
			p -> backToList(menu, level, hall, p));
		menu.button(4, entryIcon(level, hall, entry, false), null);
		menu.divider(1);
		if (entry.repair() != null && entry.ask() == null) {
			menu.button(VillageHallScreen.FIRST_ROW + 13, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.repairs.threat_only"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.repairs.threat", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		if (entry.repair() != null) {
			menu.button(PAUSE, VillageHallScreen.icon(Items.CLOCK, Component.translatable("screen.aliveworkplace.repairs.pause"), ChatFormatting.YELLOW,
				VillageHallScreen.line("screen.aliveworkplace.repairs.pause_hint", ChatFormatting.GRAY)), p -> answered(menu, level, hall, p, RepairAsks.pause(p, at)));
			menu.button(STOP, VillageHallScreen.icon(Items.BARRIER, Component.translatable("screen.aliveworkplace.repairs.stop"), ChatFormatting.RED,
				VillageHallScreen.line("screen.aliveworkplace.repairs.stop_hint", ChatFormatting.GRAY)), p -> answered(menu, level, hall, p, RepairAsks.notNow(p, at)));
			return;
		}
		RepairAsks.State state = entry.ask().state();
		if (state != RepairAsks.State.YES) {
			menu.button(YES, VillageHallScreen.icon(Items.LIME_DYE, Component.translatable(state == RepairAsks.State.PAUSED
					? "screen.aliveworkplace.repairs.carry_on" : "screen.aliveworkplace.repairs.yes"), ChatFormatting.GREEN,
				VillageHallScreen.line("screen.aliveworkplace.repairs.yes_hint", ChatFormatting.GRAY)), p -> answered(menu, level, hall, p, RepairAsks.yes(p, at)));
		}
		if (state != RepairAsks.State.NOT_NOW) {
			menu.button(NOT_NOW, VillageHallScreen.icon(Items.RED_DYE, Component.translatable("screen.aliveworkplace.repairs.not_now"), ChatFormatting.RED,
				VillageHallScreen.line("screen.aliveworkplace.repairs.not_now_hint", ChatFormatting.GRAY)), p -> answered(menu, level, hall, p, RepairAsks.notNow(p, at)));
		}
	}

	/** A build that can be brought up to its latest look (36.21), as the page shows it: where, what changes, where its refresh stands. */
	public static ItemStack refreshIcon(ServerLevel level, BlockPos hall, Refreshes.Entry entry, boolean clickable) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(VillageHallScreen.where(hall, entry.building().placement().origin()), ChatFormatting.GRAY));
		BuildSite site = entry.site();
		if (site != null) {
			BuildPlan plan = site.plan(level);
			int percent = plan == null ? 0 : Math.round(site.progress(plan) * 100);
			Component builder = site.builder() != null && level.getEntity(site.builder()) instanceof Villager v ? v.getDisplayName()
				: Component.translatable("screen.aliveworkplace.piece_looks.a_builder");
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.refresh.going", builder, percent), ChatFormatting.AQUA));
		} else {
			lore.add(VillageHallScreen.line(entry.waiting() ? "screen.aliveworkplace.refresh.waiting" : "screen.aliveworkplace.refresh.offer",
				entry.waiting() ? ChatFormatting.YELLOW : ChatFormatting.AQUA));
			Component in = Refreshes.counts(entry.change().added());
			Component out = Refreshes.counts(entry.change().removed());
			if (in != null) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.refresh.put_in", in), ChatFormatting.GRAY));
			}
			if (out != null) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.refresh.take_away", out), ChatFormatting.GRAY));
			}
			if (clickable) {
				lore.add(VillageHallScreen.line("screen.aliveworkplace.refresh.click", ChatFormatting.DARK_GRAY));
			}
		}
		return VillageHallScreen.icon(site != null ? Items.SCAFFOLDING : Items.PAINTING, Blueprints.displayName(entry.building().structure()).copy(),
			ChatFormatting.WHITE, lore.toArray(Component[]::new));
	}

	/** A build's refresh view (36.21): back to the list, the build and what changes, then the button that hands it to a builder. */
	public static void renderRefresh(ChoiceMenu menu, ServerLevel level, BlockPos hall, BlockPos at, ServerPlayer viewer) {
		Refreshes.Entry entry = Refreshes.entries(level, hall).stream().filter(e -> e.building().placement().origin().equals(at)).findFirst().orElse(null);
		if (entry == null) { // refreshed, or put right, while the list was open
			backToList(menu, level, hall, viewer);
			return;
		}
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.repairs.back"), ChatFormatting.WHITE),
			p -> backToList(menu, level, hall, p));
		menu.button(4, refreshIcon(level, hall, entry, false), null);
		menu.divider(1);
		if (entry.site() != null || entry.waiting()) {
			return; // handed over already: the builder (or the first one free) sees to it
		}
		menu.button(REFRESH, VillageHallScreen.icon(Items.BRUSH, Component.translatable("screen.aliveworkplace.refresh.button"), ChatFormatting.GREEN,
				VillageHallScreen.line("screen.aliveworkplace.refresh.button_hint", ChatFormatting.GRAY),
				VillageHallScreen.line("screen.aliveworkplace.refresh.stays", ChatFormatting.DARK_GRAY)),
			p -> {
				Refreshes.Outcome outcome = Refreshes.refresh(p, at);
				level.playSound(null, p.blockPosition(), outcome.ok() ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
				backToList(menu, level, hall, p);
			});
	}

	private static void answered(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer p, RepairAsks.Outcome outcome) {
		level.playSound(null, p.blockPosition(), outcome.ok() ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
		backToList(menu, level, hall, p);
	}

	private static void backToList(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer p) {
		HallPages.Page shown = page;
		if (shown == null) {
			for (HallPages.Page each : HallPages.all()) {
				if (each.id().equals(PAGE)) {
					shown = each;
				}
			}
		}
		if (shown != null) {
			VillageHallScreen.renderPage(menu, level, hall, shown, p);
		} else {
			VillageHallScreen.showList(menu, level, hall, 0);
		}
		menu.broadcastChanges();
	}

	private RepairsPage() {
	}
}
