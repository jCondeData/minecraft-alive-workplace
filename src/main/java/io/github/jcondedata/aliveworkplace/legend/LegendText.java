package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * What players read about Legends (29.4): their name and title, rarity, powers, needs and strike on the hall's list,
 * and how each comes on the Legends page. Every line is a lang key; colours are set here so the hall and the page agree.
 */
public final class LegendText {
	/** The three needs (29.5 checks them and counts the days each goes unmet in {@link LegendData#unmet} under these keys). */
	public static final String HOME = "home";
	public static final String LUXURY = "luxury";
	public static final String HAPPY = "happy";
	/** The places a guest visits and the sites a Legend is found at that have a sentence of their own. */
	static final java.util.Set<String> PLACES = java.util.Set.of("inn", "market", "festival", "chapel", "hall");
	static final java.util.Set<String> SITES = java.util.Set.of("ruined_portal", "outpost", "shipwreck", "hermit_hut");

	/** "Builder", from a profession id (the id itself if no such profession). */
	public static Component trade(ResourceLocation id) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getOptional(id)
			.<Component>map(p -> Component.translatable("entity.minecraft.villager." + p.name()))
			.orElse(Component.literal(id.toString()));
	}

	/** "Builder", "Builder or Mason", "Builder, Mason or Chef". */
	public static Component trades(List<ResourceLocation> ids) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) {
				out.append(Component.translatable(i == ids.size() - 1 ? "legend.aliveworkplace.or" : "legend.aliveworkplace.comma"));
			}
			out.append(trade(ids.get(i)));
		}
		return out;
	}

	/** 2.0 as "2", 1.5 as "1.5". */
	public static String number(float f) {
		return f == Math.rint(f) ? Integer.toString(Math.round(f)) : String.format(Locale.ROOT, "%.2f", f).replaceAll("0+$", "");
	}

	/** "Ada Stonewright, Master Architect", in gold. */
	public static Component name(Component villager, Legend legend) {
		return Component.translatable("legend.aliveworkplace.name", villager, legend.titleText()).withStyle(ChatFormatting.GOLD);
	}

	/** "Rare Legend · Builder", in the rarity's colour. */
	public static Component rarityLine(Legend legend) {
		Component job = legend.job().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("legend"))
			? Component.translatable("legend.aliveworkplace.no_trade") : trade(legend.job());
		return Component.translatable("legend.aliveworkplace.rarity_line", legend.rarity().title(), job).withStyle(legend.rarity().color);
	}

	/** Each power on a line: "★ Every Builder within 32 blocks works 2× as fast". */
	public static List<Component> powerLines(Legend legend, boolean striking) {
		List<Component> out = new ArrayList<>();
		for (Power power : legend.powers()) {
			out.add(Component.translatable("legend.aliveworkplace.power_line", power.describe())
				.withStyle(striking ? ChatFormatting.DARK_GRAY : ChatFormatting.YELLOW));
		}
		return out;
	}

	/** "✔ text" in green or "✘ text" in red. */
	public static Component tick(boolean met, Component text) {
		return Component.translatable(met ? "legend.aliveworkplace.tick" : "legend.aliveworkplace.cross", text)
			.withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	/** The needs the Legend has (a home, their luxury if they like one, a happy village), each with a tick or a cross. */
	public static List<Component> needLines(Legend legend, LegendData data) {
		List<Component> out = new ArrayList<>();
		out.add(tick(met(data, HOME), Component.translatable("legend.aliveworkplace.need.home")));
		legend.luxury().ifPresent(l -> out.add(tick(met(data, LUXURY), Component.translatable("legend.aliveworkplace.need.luxury", luxury(l)))));
		out.add(tick(met(data, HAPPY), Component.translatable("legend.aliveworkplace.need.happy")));
		return out;
	}

	/** Whether a need is met as the last round saw it (not unmet on the latest day counted). */
	public static boolean met(LegendData data, String need) {
		return data.unmet().getOrDefault(need, 0) <= 0;
	}

	/** "books", "wine"... */
	public static Component luxury(String kind) {
		return Component.translatable("legend.aliveworkplace.luxury." + kind);
	}

	/** "On strike since day 12", in red; null when they aren't. */
	@Nullable
	public static Component strikeLine(LegendData data) {
		return data.onStrike() ? Component.translatable("legend.aliveworkplace.strike", data.strikeSince()).withStyle(ChatFormatting.RED) : null;
	}

	/** "On strike since day 12" and "Wants: a home of my own", both in red; none when they aren't on strike. */
	public static List<Component> strikeLines(Legend legend, LegendData data) {
		Component strike = strikeLine(data);
		if (strike == null) {
			return List.of();
		}
		return List.of(strike, Component.translatable("legend.aliveworkplace.strike_wants", LegendNeeds.wants(legend, data)).withStyle(ChatFormatting.RED));
	}

	/** The hall's list's name for {@code villager} if they are a Legend: "Ada Stonewright, Master Architect" in gold. */
	@Nullable
	public static Component hallName(Villager villager) {
		return Legends.of(villager).map(l -> name(villager.getDisplayName(), l)).orElse(null);
	}

	/**
	 * The hall's list's lines for a Legend (none for anyone else): rarity, a guest's stay, each power, each need with a
	 * tick or a cross, and a strike in red.
	 */
	public static List<Component> hallLines(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		Legend legend = data == null ? null : Legends.get(data.id()).orElse(null);
		if (legend == null) {
			return List.of();
		}
		List<Component> out = new ArrayList<>();
		out.add(rarityLine(legend));
		if (data.guest()) {
			out.add((data.lastDay() >= 0 ? Component.translatable("legend.aliveworkplace.guest_until", data.lastDay())
				: Component.translatable("legend.aliveworkplace.guest")).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		out.addAll(powerLines(legend, data.onStrike()));
		out.addAll(needLines(legend, data));
		out.addAll(strikeLines(legend, data));
		return out;
	}

	/** How a Legend comes, a line per way: "Visits the inn", "Found at a ruined portal", "Born to a Bard"... */
	public static List<Component> wayLines(Legend legend) {
		List<Component> out = new ArrayList<>();
		for (JsonObject way : legend.arrive()) {
			out.add(way(way));
		}
		if (out.isEmpty()) {
			out.add(Component.translatable("legend.aliveworkplace.way.none"));
		}
		return out;
	}

	static Component way(JsonObject way) {
		String kind = way.get("way").getAsString();
		return switch (kind) {
			case "visit" -> {
				String place = string(way, "place", "inn");
				yield "festival".equals(place) && way.has("crowd")
					? Component.translatable("legend.aliveworkplace.way.visit.festival_crowd", way.get("crowd").getAsInt())
					: PLACES.contains(place) ? Component.translatable("legend.aliveworkplace.way.visit." + place)
					: Component.translatable("legend.aliveworkplace.way.visit.other", place);
			}
			case "found" -> {
				String site = string(way, "site", "");
				int slash = site.lastIndexOf('/');
				String name = (slash >= 0 ? site.substring(slash + 1) : site).replace("#", "").replace("aliveworkplace:", "");
				yield SITES.contains(name) ? Component.translatable("legend.aliveworkplace.way.found." + name)
					: Component.translatable("legend.aliveworkplace.way.found.other");
			}
			case "born" -> Component.translatable("legend.aliveworkplace.way.born", trades(ids(way)));
			case "inspired" -> way.has("founder") && way.get("founder").getAsBoolean()
				? Component.translatable("legend.aliveworkplace.way.inspired.founder")
				: Component.translatable("legend.aliveworkplace.way.inspired", trades(ids(way)));
			default -> Component.literal(kind);
		};
	}

	private static String string(JsonObject json, String field, String fallback) {
		return json.has(field) ? json.get(field).getAsString() : fallback;
	}

	private static List<ResourceLocation> ids(JsonObject way) {
		List<ResourceLocation> out = new ArrayList<>();
		if (way.has("trades")) {
			for (JsonElement e : way.getAsJsonArray("trades")) {
				ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
				if (id != null) {
					out.add(id);
				}
			}
		}
		return out;
	}

	private LegendText() {
	}
}
