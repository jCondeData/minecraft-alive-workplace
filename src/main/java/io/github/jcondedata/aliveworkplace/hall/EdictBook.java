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
 * everyone else reads it and is told why. The last row is kept for the civic items and guilds: the Work Horn's state first (30.11), then the Cradle (30.12), then the village's colours (30.13).
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
	/** The Work Horn on the last row (30.11): ready, rushing or used today. */
	public static final int HORN = RESERVED_ROW * 9;
	/** The Cradle on the last row (30.12): whether the village is a nursery. */
	public static final int CRADLE = RESERVED_ROW * 9 + 1;
	/** The village's colours on the last row (30.13): its banner, or how to give it one. */
	public static final int BANNER = RESERVED_ROW * 9 + 2;

	/** Opens the Book on its own (from a Village Ledger: it stays open while the hall stands). */
	public static void open(ServerPlayer player, BlockPos hall) {
		ServerLevel level = Players.level(player);
		ChoiceMenu.openHall(player, Component.translatable("screen.aliveworkplace.edicts.title", VillageHalls.name(level, hall)),
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
		// The village by its own banner when it has colours (30.13).
		VillageBanners.Colours colours = VillageBanners.of(entity);
		menu.button(HEADER, VillageHallScreen.icon(colours != null ? colours.banner() : new ItemStack(Items.LECTERN), Component.translatable("screen.aliveworkplace.edicts.title", village),
			ChatFormatting.GOLD, header.toArray(Component[]::new)), null);
		filler(menu, 1);

		// The second row: the slots.
		VillageRanks.Rank[] ranks = VillageRanks.Rank.values();
		for (int i = 0; i < SLOTS.length; i++) {
			int slot = SLOTS[i];
			if (i < inForce.size()) {
				Edicts.InForce f = inForce.get(i);
				menu.button(slot, inForceIcon(entity, f, today), p -> lift(menu, level, hall, back, p, f.id()));
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
				menu.button(here, edictIcon(entity, edict, held.get(), today, false), p -> lift(menu, level, hall, back, p, id));
			} else {
				boolean ready = here == armed;
				menu.button(here, edictIcon(entity, edict, null, today, ready), p -> {
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
		// The last row: the civic items.
		menu.button(HORN, VillageHallScreen.icon(io.github.jcondedata.aliveworkplace.registry.ModItems.WORK_HORN,
			Component.translatable("screen.aliveworkplace.edicts.horn"), ChatFormatting.GOLD,
			WorkHorn.status(level, entity).toArray(Component[]::new)), null);
		menu.button(CRADLE, VillageHallScreen.icon(io.github.jcondedata.aliveworkplace.registry.ModBlocks.CRADLE.asItem(),
			Component.translatable("screen.aliveworkplace.edicts.cradle"), ChatFormatting.GOLD, Cradles.status(level, hall)), null);
		menu.button(BANNER, bannerIcon(colours), null);
		filler(menu, RESERVED_ROW);
	}

	/** The last row's banner: the village's colours, or how to set them. */
	static ItemStack bannerIcon(@Nullable VillageBanners.Colours colours) {
		if (colours != null) {
			return VillageHallScreen.icon(colours.banner(), Component.translatable("screen.aliveworkplace.edicts.banner"), ChatFormatting.GOLD,
				VillageHallScreen.line("screen.aliveworkplace.edicts.banner_shown", ChatFormatting.GRAY),
				VillageHallScreen.line("screen.aliveworkplace.edicts.banner_change", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(io.github.jcondedata.aliveworkplace.registry.ModItems.VILLAGE_BANNER, Component.translatable("screen.aliveworkplace.edicts.banner_none"),
			ChatFormatting.GOLD, VillageHallScreen.line(VillageBanners.ENABLED ? "screen.aliveworkplace.edicts.banner_how" : "message.aliveworkplace.village_banner.off",
				VillageBanners.ENABLED ? ChatFormatting.GRAY : ChatFormatting.RED));
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
	private static ItemStack inForceIcon(VillageHallBlockEntity entity, Edicts.InForce f, long today) {
		ResourceLocation id = ResourceLocation.tryParse(f.id());
		Optional<Edicts.Edict> edict = id == null ? Optional.empty() : Edicts.get(id);
		List<Component> lore = new ArrayList<>();
		edict.ifPresent(e -> effects(entity, e, lore));
		days(f, today, lore);
		ItemStack icon = VillageHallScreen.icon(edict.map(e -> item(e.icon())).orElse(Items.PAPER), Edicts.name(f.id()).copy(), ChatFormatting.GOLD,
			lore.toArray(Component[]::new));
		icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return icon;
	}

	/** An edict in the list: its description, boost (green), cost (red) and what a click does. */
	private static ItemStack edictIcon(VillageHallBlockEntity entity, Edicts.Edict edict, @Nullable Edicts.InForce held, long today, boolean armed) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(edict.description(), ChatFormatting.GRAY));
		effects(entity, edict, lore);
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

	/**
	 * Its boost (green) and cost (red), then its reform (30.5): "Reform: The Shift Bell, step 1 of 3" with its line, or
	 * "Reformed: The Shift Bell" once the village has reformed it (then the reform's effects replace the cost).
	 */
	private static void effects(VillageHallBlockEntity entity, Edicts.Edict edict, List<Component> lore) {
		String id = edict.id().toString();
		boolean reformed = edict.reform().isPresent() && Reforms.reformed(entity, id);
		for (CivicEffects.Effect e : edict.boost()) {
			Component said = describe(e);
			if (said != null) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.boost", said), ChatFormatting.GREEN));
			}
		}
		List<CivicEffects.Effect> cost = reformed ? edict.reform().get().effects() : edict.cost();
		for (CivicEffects.Effect e : cost) {
			Component said = describe(e);
			if (said != null) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.cost", said), ChatFormatting.RED));
			}
		}
		edict.reform().ifPresent(reform -> {
			if (reformed) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.reformed", reform.name()), ChatFormatting.LIGHT_PURPLE));
				return;
			}
			int done = Math.min(Reforms.progress(entity, id).step(), reform.steps().size() - 1);
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.reform", reform.name(), done + 1, reform.steps().size()),
				ChatFormatting.LIGHT_PURPLE));
			if (!reform.line().getString().isEmpty()) {
				lore.add(VillageHallScreen.line(reform.line(), ChatFormatting.DARK_PURPLE));
			}
		});
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

	/**
	 * One effect in words: "20% faster work", "10 less happy (long shifts)"; null for one nothing reads yet
	 * ({@code legend_visits} before M29's inn visitors), which the Book leaves out.
	 */
	@org.jetbrains.annotations.Nullable
	public static Component describe(CivicEffects.Effect effect) {
		if (effect instanceof CivicEffects.Inn inn) {
			List<Component> parts = new ArrayList<>();
			inn.guests().ifPresent(n -> parts.add(Component.translatable("screen.aliveworkplace.edicts.effect.inn_guests", n,
				io.github.jcondedata.aliveworkplace.inn.Innkeepers.MAX_GUESTS)));
			inn.arrivals().ifPresent(n -> parts.add(Words.counted("screen.aliveworkplace.edicts.effect.inn_arrivals", n, n)));
			return parts.isEmpty() ? Component.translatable("screen.aliveworkplace.edicts.effect.inn_usual") : joined(parts);
		}
		if (effect instanceof CivicEffects.MarketTraders market) {
			int n = Math.abs(market.extra());
			return Words.counted(market.extra() >= 0 ? "screen.aliveworkplace.edicts.effect.traders_more" : "screen.aliveworkplace.edicts.effect.traders_fewer", n, n);
		}
		if (effect instanceof CivicEffects.LegendVisits visits) {
			return CivicEffects.LEGEND_VISITS_READ ? often("screen.aliveworkplace.edicts.effect.legends", visits.factor()) : null;
		}
		if (effect instanceof CivicEffects.BanditCampChance bandits) {
			return often("screen.aliveworkplace.edicts.effect.bandits", bandits.factor());
		}
		if (effect instanceof CivicEffects.FestivalEvery every) {
			return Words.counted("screen.aliveworkplace.edicts.effect.festival_every", every.days(), every.days(), Festivals.EVERY_DAYS);
		}
		if (effect instanceof CivicEffects.FestivalCost cost) {
			return cost.perVillagers() > 0
				? Component.translatable("screen.aliveworkplace.edicts.effect.festival_cost_per", cost.emeralds(), cost.perVillagers())
				: Words.counted("screen.aliveworkplace.edicts.effect.festival_cost", cost.emeralds(), cost.emeralds());
		}
		if (effect instanceof CivicEffects.CurfewRules curfew) {
			List<Component> parts = new ArrayList<>();
			if (curfew.raids() != 1f) {
				parts.add(Math.abs(curfew.raids() - 0.5f) < 1e-4f ? Component.translatable("screen.aliveworkplace.edicts.effect.raids.half")
					: often("screen.aliveworkplace.edicts.effect.raids", curfew.raids()));
			}
			if (curfew.safeNights()) {
				parts.add(Component.translatable("screen.aliveworkplace.edicts.effect.safe_nights"));
			}
			if (curfew.stayIn()) {
				parts.add(Component.translatable("screen.aliveworkplace.edicts.effect.stay_in"));
			}
			return parts.isEmpty() ? null : joined(parts);
		}
		if (effect instanceof CivicEffects.Militia militia) {
			return Component.translatable("screen.aliveworkplace.edicts.effect.militia", militia.damage() == Math.round(militia.damage())
				? Integer.toString(Math.round(militia.damage())) : String.format(java.util.Locale.ROOT, "%.1f", militia.damage()), militia.range());
		}
		if (effect instanceof CivicEffects.WorkStops stops) {
			if (stops.near().isPresent()) {
				return Component.translatable("screen.aliveworkplace.edicts.effect.work_stops_near", stops.near().get());
			}
			return Component.translatable(stops.untilNoon() ? "screen.aliveworkplace.edicts.effect.work_stops_noon" : "screen.aliveworkplace.edicts.effect.work_stops");
		}
		if (effect instanceof CivicEffects.TitheShare tithe) {
			return Component.translatable("screen.aliveworkplace.edicts.effect.tithe", tithe.percent());
		}
		if (effect instanceof CivicEffects.TradePrices prices) {
			return Component.translatable(prices.percent() >= 0 ? "screen.aliveworkplace.edicts.effect.prices_up" : "screen.aliveworkplace.edicts.effect.prices_down",
				Math.abs(prices.percent()));
		}
		if (effect instanceof CivicEffects.WorkPace pace) {
			return Component.translatable(pace.jobs().isEmpty() ? "screen.aliveworkplace.edicts.effect.pace" : "screen.aliveworkplace.edicts.effect.pace_jobs",
				pace.percent(), pace.jobs().size());
		}
		if (effect instanceof CivicEffects.Mood mood) {
			String key = mood.points() >= 0 ? "screen.aliveworkplace.edicts.effect.happier" : "screen.aliveworkplace.edicts.effect.sadder";
			return Component.translatable(mood.when() == CivicEffects.When.FED_TODAY ? key + "_fed" : key, Math.abs(mood.points()), mood.reason());
		}
		if (effect instanceof CivicEffects.FoodUse food) {
			return Component.translatable(food.percent() >= 0 ? "screen.aliveworkplace.edicts.effect.eats_more" : "screen.aliveworkplace.edicts.effect.eats_less",
				Math.abs(food.percent()));
		}
		if (effect instanceof CivicEffects.Illness illness) {
			return Component.translatable(illness.percent() >= 0 ? "screen.aliveworkplace.edicts.effect.ill_more" : "screen.aliveworkplace.edicts.effect.ill_less",
				Math.abs(illness.percent()));
		}
		if (effect instanceof CivicEffects.Births births) {
			List<Component> parts = new ArrayList<>();
			if (births.perDay() > 1) {
				parts.add(Component.translatable("screen.aliveworkplace.edicts.effect.babies", births.perDay()));
			}
			births.foodNeeded().ifPresent(n -> parts.add(Component.translatable("screen.aliveworkplace.edicts.effect.baby_food", n, VillageGrowth.FOOD_NEEDED)));
			births.familyMeals().ifPresent(n -> parts.add(Component.translatable("screen.aliveworkplace.edicts.effect.family_meals", n, VillageGrowth.MEALS)));
			if (parts.isEmpty()) {
				return Component.translatable("screen.aliveworkplace.edicts.effect.babies_usual");
			}
			return joined(parts);
		}
		return Component.literal(effect.type().getPath().replace('_', ' '));
	}

	private static Component joined(List<Component> parts) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(Component.literal(", "));
			}
			out.append(parts.get(i));
		}
		return out;
	}

	/** "{@code key}.twice" for 2, "{@code key}.usual" for 1, "{@code key}.less" below 1, else "{@code key}" with the factor ("3"). */
	private static Component often(String key, float factor) {
		if (Math.abs(factor - 2f) < 1e-4f) {
			return Component.translatable(key + ".twice");
		}
		if (Math.abs(factor - 1f) < 1e-4f) {
			return Component.translatable(key + ".usual");
		}
		String n = factor == Math.round(factor) ? Integer.toString(Math.round(factor)) : String.format(java.util.Locale.ROOT, "%.1f", factor);
		return Component.translatable(factor < 1f ? key + ".less" : key, n);
	}

	private static Item item(ResourceLocation id) {
		Item item = BuiltInRegistries.ITEM.get(id);
		return item == Items.AIR ? Items.PAPER : item;
	}

	private EdictBook() {
	}
}
