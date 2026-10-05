package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Book of Edicts (ROADMAP 30.4): the village's page of laws, opened from the hall's lectern button (slot 9) or by
 * sneak-using a Village Ledger. At the top the village's name; the second row holds its edict slots (one per rank: in
 * force with its days, free, or locked with the rank that opens it); below, every edict with its boost and cost. Click
 * an edict twice to proclaim it, once to lift one in force. Only the hall's owner, friends and operators may click;
 * everyone else reads it and is told why. The last row is kept for the civic items and guilds (30.11 onwards).
 */
public final class EdictBook {
	/** Back to the hall's screen (only when opened from it). */
	public static final int BACK = 0;
	/** The village's name and rank. */
	public static final int HEADER = 4;
	/** The edict slots, one per rank (Hamlet's first). */
	public static final int[] SLOTS = {10, 12, 14, 16};
	/** Where the list of every edict starts, and the slot after its last. */
	public static final int FIRST_EDICT = 27;
	public static final int END_EDICTS = 45;
	/** The last row, kept for the civic items and guilds. */
	public static final int RESERVED_ROW = 5;

	/** Opens the Book on its own (from a Village Ledger: it stays open while the hall stands). */
	public static void open(ServerPlayer player, BlockPos hall) {
		ServerLevel level = Players.level(player);
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.edicts.title", VillageHalls.name(level, hall)),
			p -> p.isAlive() && p.level() == level && level.isLoaded(hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL),
			menu -> render(menu, level, hall, null, -1));
	}

	/** The Book on its own, not shown to anyone (tests). */
	public static ChoiceMenu forTest(ServerPlayer player, BlockPos hall) {
		return ChoiceMenu.detached(player, menu -> render(menu, Players.level(player), hall, null, -1));
	}

	/**
	 * Lays the Book out in {@code menu}. {@code back} (null: none) returns to the hall's screen; {@code armed} is the
	 * slot of an edict clicked once (-1: none), which a second click proclaims.
	 */
	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable Runnable back, int armed) {
		menu.clearButtons();
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		Component village = VillageHalls.name(level, hall);
		VillageRanks.Rank rank = entity.rank();
		int open = Edicts.slots(rank);
		List<Edicts.InForce> inForce = entity.edicts();
		long today = Chronicle.day(level);

		if (back != null) {
			menu.button(BACK, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE),
				p -> back.run());
		}
		List<Component> header = new ArrayList<>();
		header.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.rank", rank.title()), ChatFormatting.AQUA));
		header.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.in_force", inForce.size(),
			Words.counted("message.aliveworkplace.edict.count", open, open)), ChatFormatting.GRAY));
		if (!Edicts.ENABLED) {
			header.add(VillageHallScreen.line("message.aliveworkplace.edict.disabled", ChatFormatting.RED));
		}
		header.add(VillageHallScreen.line("screen.aliveworkplace.edicts.how", ChatFormatting.DARK_GRAY));
		menu.button(HEADER, VillageHallScreen.icon(Items.LECTERN, Component.translatable("screen.aliveworkplace.edicts.title", village),
			ChatFormatting.GOLD, header.toArray(Component[]::new)), null);
		filler(menu, 1);

		// The second row: the slots.
		VillageRanks.Rank[] ranks = VillageRanks.Rank.values();
		for (int i = 0; i < SLOTS.length; i++) {
			int slot = SLOTS[i];
			if (i < inForce.size()) {
				Edicts.InForce f = inForce.get(i);
				menu.button(slot, inForceIcon(f, today), p -> lift(menu, level, hall, back, p, f.id()));
			} else if (i < open) {
				menu.button(slot, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.edicts.free"), ChatFormatting.WHITE,
					VillageHallScreen.line("screen.aliveworkplace.edicts.free_hint", ChatFormatting.GRAY)), null);
			} else {
				VillageRanks.Rank opens = ranks[Math.min(i, ranks.length - 1)];
				menu.button(slot, VillageHallScreen.icon(Items.GRAY_DYE, Component.translatable("screen.aliveworkplace.edicts.locked"), ChatFormatting.DARK_GRAY,
					VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.locked_hint", opens.title()), ChatFormatting.GRAY)), null);
			}
		}
		menu.divider(2);

		// Every edict, in its order.
		int slot = FIRST_EDICT;
		for (Edicts.Edict edict : Edicts.all()) {
			if (slot >= END_EDICTS) {
				break;
			}
			String id = edict.id().toString();
			Optional<Edicts.InForce> held = inForce.stream().filter(f -> f.id().equals(id)).findFirst();
			int here = slot++;
			if (held.isPresent()) {
				menu.button(here, edictIcon(edict, held.get(), today, false), p -> lift(menu, level, hall, back, p, id));
			} else {
				boolean ready = here == armed;
				menu.button(here, edictIcon(edict, null, today, ready), p -> {
					if (!VillageProtection.mayBuild(level, entity, p)) {
						Chat.chat(p, Component.translatable("message.aliveworkplace.edict.not_allowed", entity.ownerName(), village)
							.withStyle(ChatFormatting.RED));
						return;
					}
					if (!ready) {
						refresh(menu, level, hall, back, here);
						return;
					}
					Edicts.Result result = Edicts.proclaim(level, hall, p, edict);
					if (!result.told().contains(p)) {
						Chat.chat(p, result.message());
					}
					refresh(menu, level, hall, back, -1);
				});
			}
		}
		filler(menu, RESERVED_ROW);
	}

	private static void lift(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable Runnable back, ServerPlayer p, String id) {
		Edicts.Result result = Edicts.lift(level, hall, p, id);
		if (!result.told().contains(p)) {
			Chat.chat(p, result.message());
		}
		refresh(menu, level, hall, back, -1);
	}

	private static void refresh(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable Runnable back, int armed) {
		render(menu, level, hall, back, armed);
		menu.broadcastChanges();
	}

	/** Light glass across a row (kept for later pages' buttons). */
	private static void filler(ChoiceMenu menu, int row) {
		for (int x = 0; x < 9; x++) {
			int slot = row * 9 + x;
			if (!menu.icon(slot).isEmpty()) {
				continue;
			}
			ItemStack pane = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
			pane.set(DataComponents.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
			menu.button(slot, pane, null);
		}
	}

	/** An edict in its slot of the second row: its name, since when, and when it can be lifted. */
	private static ItemStack inForceIcon(Edicts.InForce f, long today) {
		ResourceLocation id = ResourceLocation.tryParse(f.id());
		Optional<Edicts.Edict> edict = id == null ? Optional.empty() : Edicts.get(id);
		List<Component> lore = new ArrayList<>();
		edict.ifPresent(e -> effects(e, lore));
		days(f, today, lore);
		ItemStack icon = VillageHallScreen.icon(edict.map(e -> item(e.icon())).orElse(Items.PAPER), Edicts.name(f.id()).copy(), ChatFormatting.GOLD,
			lore.toArray(Component[]::new));
		icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return icon;
	}

	/** An edict in the list: its description, boost (green), cost (red) and what a click does. */
	private static ItemStack edictIcon(Edicts.Edict edict, @Nullable Edicts.InForce held, long today, boolean armed) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(edict.description(), ChatFormatting.GRAY));
		effects(edict, lore);
		if (held != null) {
			days(held, today, lore);
		} else {
			lore.add(VillageHallScreen.line(armed ? "screen.aliveworkplace.edicts.click_again" : "screen.aliveworkplace.edicts.click_proclaim",
				armed ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
		}
		ItemStack icon = VillageHallScreen.icon(item(edict.icon()), edict.name().copy(), held != null ? ChatFormatting.GOLD : ChatFormatting.WHITE,
			lore.toArray(Component[]::new));
		if (held != null || armed) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
	}

	private static void effects(Edicts.Edict edict, List<Component> lore) {
		for (CivicEffects.Effect e : edict.boost()) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.boost", describe(e)), ChatFormatting.GREEN));
		}
		for (CivicEffects.Effect e : edict.cost()) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.cost", describe(e)), ChatFormatting.RED));
		}
	}

	/** Since when an edict is in force, and from which day it can be lifted (or that it can be now). */
	private static void days(Edicts.InForce f, long today, List<Component> lore) {
		long days = Math.max(0, today - f.day());
		lore.add(VillageHallScreen.line(Words.counted("screen.aliveworkplace.edicts.since", (int) days, f.day(), days), ChatFormatting.AQUA));
		long from = f.day() + Edicts.MIN_DAYS;
		long left = from - today;
		lore.add(left > 0
			? VillageHallScreen.line(Words.counted("screen.aliveworkplace.edicts.lift_in", (int) left, left, from), ChatFormatting.YELLOW)
			: VillageHallScreen.line("screen.aliveworkplace.edicts.click_lift", ChatFormatting.DARK_GRAY));
	}

	/** One effect in words: "20% faster work", "10 less happy (long shifts)". */
	static Component describe(CivicEffects.Effect effect) {
		if (effect instanceof CivicEffects.WorkPace pace) {
			return Component.translatable(pace.jobs().isEmpty() ? "screen.aliveworkplace.edicts.effect.pace" : "screen.aliveworkplace.edicts.effect.pace_jobs",
				pace.percent(), pace.jobs().size());
		}
		if (effect instanceof CivicEffects.Mood mood) {
			return Component.translatable(mood.points() >= 0 ? "screen.aliveworkplace.edicts.effect.happier" : "screen.aliveworkplace.edicts.effect.sadder",
				Math.abs(mood.points()), mood.reason());
		}
		return Component.literal(effect.type().getPath().replace('_', ' '));
	}

	private static Item item(ResourceLocation id) {
		Item item = BuiltInRegistries.ITEM.get(id);
		return item == Items.AIR ? Items.PAPER : item;
	}

	private EdictBook() {
	}
}
