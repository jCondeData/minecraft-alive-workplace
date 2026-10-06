package io.github.jcondedata.aliveworkplace.hall;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Diet;
import io.github.jcondedata.aliveworkplace.people.Households;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Classes at the Village Hall (ROADMAP 34.6): the hall's Classes tab (its tooltip: how many households of each class) and
 * page, a button per class listing each need and want with how many of the households who are or could next become that
 * class have it ("Fine Clothes: 2 of 5"), what the class gives, and the households closest to rising into it with what
 * they lack. Also the class lines of the people list, the luxuries in store on the food icon, and the "What next?" class
 * tips. Everything is worked out when the screen is drawn; nothing is saved.
 */
public final class ClassesPage {
	public static final String PAGE = "classes";
	/** Households named under "closest to rising" at most. */
	public static final int CLOSEST = 3;
	/** Class tips on "What next?" at most. */
	public static final int TIPS = 3;
	/** Needs named after "lacks" at most. */
	private static final int LACKS_SHOWN = 3;

	/** A household and its class (the higher of a couple's). */
	public record Placed(Households.Household household, SocialClass social) {
	}

	/** One need's count among a class's households: how many have it, of how many. */
	public record NeedCount(ClassNeeds.Need need, boolean want, int have, int of) {
	}

	/** A household one step below {@code into}, and the needs of {@code into} it lacks. */
	public record Close(Households.Household household, List<ClassNeeds.Need> lacking) {
	}

	/** A class as the page shows it: households in it, its needs' and wants' counts, the closest to rising into it. */
	public record Row(SocialClass social, int households, List<NeedCount> counts, List<Close> closest) {
	}

	/**
	 * Adds the Classes tab, once 1.8 (Milestone 34) is finished: until then no hall shows it. It's a spare page: it gives
	 * its tab up to other features' pages rather than take one of the six kept for them (22.5).
	 */
	public static void init() {
		if (io.github.jcondedata.aliveworkplace.Expansions.on(io.github.jcondedata.aliveworkplace.Expansions.M34)) {
			HallPages.registerSpare(PAGE, ClassesPage::tab, ClassesPage::header, ClassesPage::fill);
		}
	}

	/** The village's grown households with a class (those not seeded yet are left out), in a fixed order. */
	public static List<Placed> households(ServerLevel level, BlockPos hall) {
		List<Villager> people = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive);
		List<Placed> out = new ArrayList<>();
		for (Households.Household h : Households.of(people)) {
			SocialClass best = null;
			for (Villager v : h.members()) {
				SocialClass c = SocialClasses.of(v);
				if (c != null && (best == null || c.tier() > best.tier())) {
					best = c;
				}
			}
			if (best != null) {
				out.add(new Placed(h, best));
			}
		}
		return out;
	}

	/** Each class of the ladder, lowest first, counted over the village round {@code hall}. */
	public static List<Row> survey(ServerLevel level, BlockPos hall) {
		List<Placed> placed = households(level, hall);
		ClassNeeds.Village village = new ClassNeeds.Village(level, hall, Chronicle.day(level));
		List<Row> rows = new ArrayList<>();
		for (SocialClass c : SocialClasses.ladder()) {
			SocialClass below = SocialClasses.step(c, -1);
			List<Placed> in = new ArrayList<>();
			List<Placed> candidates = new ArrayList<>();
			for (Placed p : placed) {
				if (p.social() == c) {
					in.add(p);
					candidates.add(p);
				} else if (below != null && p.social() == below) {
					candidates.add(p);
				}
			}
			List<NeedCount> counts = new ArrayList<>();
			for (int w = 0; w < 2; w++) {
				for (ClassNeeds.Need need : w == 0 ? c.needs() : c.wants()) {
					int have = 0;
					for (Placed p : candidates) {
						if (ClassNeeds.holds(need, p.household().members(), village)) {
							have++;
						}
					}
					counts.add(new NeedCount(need, w == 1, have, candidates.size()));
				}
			}
			List<Close> closest = new ArrayList<>();
			if (below != null) {
				for (Placed p : candidates) {
					if (p.social() != below) {
						continue;
					}
					List<ClassNeeds.Need> lacking = new ArrayList<>();
					for (ClassNeeds.Need need : c.needs()) {
						if (!ClassNeeds.holds(need, p.household().members(), village)) {
							lacking.add(need);
						}
					}
					closest.add(new Close(p.household(), lacking));
				}
				closest.sort(Comparator.comparingInt(cl -> cl.lacking().size())); // stable: households keep their order
			}
			rows.add(new Row(c, in.size(), counts, closest.subList(0, Math.min(CLOSEST, closest.size()))));
		}
		return rows;
	}

	/** How many households of each class, lowest first. */
	public static Map<SocialClass, Integer> counts(ServerLevel level, BlockPos hall) {
		Map<SocialClass, Integer> out = new LinkedHashMap<>();
		SocialClasses.ladder().forEach(c -> out.put(c, 0));
		for (Placed p : households(level, hall)) {
			out.merge(p.social(), 1, Integer::sum);
		}
		return out;
	}

	static ItemStack tab(ServerLevel level, BlockPos hall) {
		List<Component> lore = new ArrayList<>();
		if (!SocialClasses.ENABLED || SocialClasses.ladder().isEmpty()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.classes.off", ChatFormatting.GRAY));
		} else {
			for (Map.Entry<SocialClass, Integer> e : counts(level, hall).entrySet()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.classes.count", e.getKey().name(), e.getValue()),
					e.getValue() > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
			}
			lore.add(VillageHallScreen.line("screen.aliveworkplace.classes.hint", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(Items.GOLDEN_HELMET, Component.translatable("screen.aliveworkplace.classes"), ChatFormatting.GOLD,
			lore.toArray(Component[]::new));
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.GOLDEN_HELMET, Component.translatable("screen.aliveworkplace.classes.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, VillageHallScreen.line("screen.aliveworkplace.classes.about", ChatFormatting.GRAY));
	}

	/** The page: a button per class, lowest first, in the first row below the divider. */
	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		int slot = VillageHallScreen.FIRST_ROW;
		if (!SocialClasses.ENABLED || SocialClasses.ladder().isEmpty()) {
			menu.button(slot + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.classes.off"), ChatFormatting.GRAY), null);
			return;
		}
		List<Row> rows = survey(level, hall);
		int gap = rows.size() <= 4 ? 2 : 1;
		slot += Math.max(0, (9 - (rows.size() - 1) * gap - 1) / 2);
		for (Row row : rows) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			menu.button(slot, classButton(row), null);
			slot += gap;
		}
	}

	/** One class's button: its households, its needs and wants with their counts, what it gives, who's closest to rising into it. */
	public static ItemStack classButton(Row row) {
		SocialClass c = row.social();
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.classes.households", row.households()), ChatFormatting.WHITE));
		SocialClass below = SocialClasses.step(c, -1);
		if (!row.counts().isEmpty()) {
			int of = row.counts().get(0).of();
			lore.add(VillageHallScreen.line(below == null ? Component.translatable("screen.aliveworkplace.classes.counted_own", of)
				: Component.translatable("screen.aliveworkplace.classes.counted", of, c.name(), below.name()), ChatFormatting.GRAY));
		}
		boolean wantsHeader = false;
		if (row.counts().stream().anyMatch(n -> !n.want())) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.classes.needs", ChatFormatting.GOLD));
		} else {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.classes.no_needs", ChatFormatting.GOLD));
		}
		for (NeedCount n : row.counts()) {
			if (n.want() && !wantsHeader) {
				lore.add(VillageHallScreen.line("screen.aliveworkplace.classes.wants", ChatFormatting.AQUA));
				wantsHeader = true;
			}
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.classes.need_count", needName(n.need()), n.have(), n.of()),
				n.of() > 0 && n.have() == n.of() ? ChatFormatting.GREEN : n.have() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		}
		lore.add(VillageHallScreen.line("screen.aliveworkplace.classes.gives", ChatFormatting.GOLD));
		for (Component g : gives(c)) {
			lore.add(VillageHallScreen.line(g, ChatFormatting.WHITE));
		}
		if (below != null) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.classes.closest", c.name()), ChatFormatting.GOLD));
			if (row.closest().isEmpty()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.classes.closest_none", below.name()), ChatFormatting.DARK_GRAY));
			}
			for (Close close : row.closest()) {
				lore.add(VillageHallScreen.line(close.lacking().isEmpty()
					? Component.translatable("screen.aliveworkplace.classes.rising", names(close.household()))
					: Component.translatable("screen.aliveworkplace.classes.lacks", names(close.household()), needList(close.lacking())),
					close.lacking().isEmpty() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			}
		}
		ItemStack stack = new ItemStack(classIcon(c), Math.max(1, Math.min(64, row.households())));
		return VillageHallScreen.icon(stack, c.name().copy(), ChatFormatting.GOLD, lore.toArray(Component[]::new));
	}

	/** The class's icon: straw for the floor, then leather, iron, gold, diamond going up. */
	static Item classIcon(SocialClass c) {
		Item[] icons = {Items.WHEAT, Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE, Items.GOLDEN_HELMET, Items.DIAMOND_HELMET};
		return icons[Math.min(icons.length - 1, Math.max(0, SocialClasses.ladder().indexOf(c)))];
	}

	/** "Odo", "Odo and Pia". */
	static Component names(Households.Household h) {
		Component first = h.members().get(0).getDisplayName();
		return h.members().size() < 2 ? first : Component.translatable("class.aliveworkplace.household.two", first, h.members().get(1).getDisplayName());
	}

	/** "Fine Clothes, Berry Wine and 2 more". */
	static Component needList(List<ClassNeeds.Need> needs) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < Math.min(LACKS_SHOWN, needs.size()); i++) {
			out.append(i == 0 ? Component.empty() : Component.literal(", ")).append(needName(needs.get(i)));
		}
		if (needs.size() > LACKS_SHOWN) {
			out.append(Component.translatable("screen.aliveworkplace.classes.more", needs.size() - LACKS_SHOWN));
		}
		return out;
	}

	/** A need in a few words: "A tier II home", "Berry Wine", "School nearby". */
	public static Component needName(ClassNeeds.Need need) {
		return switch (need) {
			case ClassNeeds.Home h -> Component.translatable("class.aliveworkplace.need.home", Component.translatable("enchantment.level." + h.grade()));
			case ClassNeeds.FedDays f -> Component.translatable("class.aliveworkplace.need.fed_days", f.days());
			case ClassNeeds.DietNeed d -> Component.translatable(d.kind() == Diet.Kind.VARIED ? "class.aliveworkplace.need.diet_varied" : "class.aliveworkplace.need.diet_plain");
			case ClassNeeds.Beauty b -> Component.translatable("class.aliveworkplace.need.beauty", b.points());
			case ClassNeeds.Building b -> b.tier() > 1
				? Component.translatable("class.aliveworkplace.need.building_tier", Blueprints.displayName(b.blueprint()), Component.translatable("enchantment.level." + b.tier()))
				: Component.translatable("class.aliveworkplace.need.building", Blueprints.displayName(b.blueprint()));
			case ClassNeeds.VillageRank r -> Component.translatable("class.aliveworkplace.need.village_rank", r.rank().title());
			case ClassNeeds.Services s -> services(s);
			case ClassNeeds.Luxury l -> luxuryName(l.id());
			case ClassNeeds.Unknown u -> Component.translatable("class.aliveworkplace.need.unknown", u.type());
		};
	}

	private static Component services(ClassNeeds.Services s) {
		if (!s.all().isEmpty() && s.any().isEmpty()) {
			return Component.translatable("class.aliveworkplace.need.services_all", serviceList(s.all()));
		}
		if (s.all().isEmpty()) {
			return s.count() <= 1 && s.any().size() > 1 ? Component.translatable("class.aliveworkplace.need.services_one", serviceList(s.any()))
				: s.any().size() == 1 ? Component.translatable("class.aliveworkplace.need.services_all", serviceList(s.any()))
				: Component.translatable("class.aliveworkplace.need.services_some", s.count(), serviceList(s.any()));
		}
		return Component.translatable("class.aliveworkplace.need.services_both", serviceList(s.all()), s.count(), serviceList(s.any()));
	}

	private static Component serviceList(List<ResourceLocation> ids) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < ids.size(); i++) {
			out.append(i == 0 ? Component.empty() : Component.literal(i == ids.size() - 1 ? " / " : ", ")).append(serviceName(ids.get(i)));
		}
		return out;
	}

	static Component serviceName(ResourceLocation id) {
		Services.Service s = Services.all().get(id);
		return s != null ? s.name() : Component.translatable("service." + id.getNamespace() + "." + id.getPath());
	}

	/** A luxury's name ({@code luxury.<namespace>.<path>}). */
	public static Component luxuryName(ResourceLocation id) {
		// A data pack's luxury without a name of its own reads as its id's path: test_trinket is "Test Trinket".
		StringBuilder fallback = new StringBuilder();
		for (String word : id.getPath().split("_")) {
			if (!word.isEmpty()) {
				fallback.append(fallback.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return Component.translatableWithFallback("luxury." + id.getNamespace() + "." + id.getPath(), fallback.toString());
	}

	/** What a class gives: its tax, the jobs it opens, each effect. */
	public static List<Component> gives(SocialClass c) {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable("class.aliveworkplace.gives.tax", fmt(c.tax())));
		if (!c.jobs().isEmpty()) {
			MutableComponent jobs = Component.empty();
			for (int i = 0; i < c.jobs().size(); i++) {
				jobs.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Component.translatable("entity.minecraft.villager." + c.jobs().get(i).getPath()));
			}
			out.add(Component.translatable("class.aliveworkplace.gives.jobs", jobs));
		}
		for (JsonElement e : c.effects()) {
			Component line = effect(e);
			if (line != null) {
				out.add(line);
			}
		}
		return out;
	}

	@Nullable
	static Component effect(JsonElement json) {
		if (!json.isJsonObject()) {
			return null;
		}
		JsonObject o = json.getAsJsonObject();
		ResourceLocation type = ResourceLocation.tryParse(GsonHelper.getAsString(o, "type", ""));
		if (type == null) {
			return null;
		}
		String key = "class." + type.getNamespace() + ".gives." + type.getPath();
		return switch (type.getPath()) {
			case "work_pace", "research_pace" -> Component.translatable(key, GsonHelper.getAsInt(o, "percent", 0));
			case "caravan_routes", "market_traders" -> Component.translatable(key, GsonHelper.getAsInt(o, "extra", 1), GsonHelper.getAsInt(o, "households", 1));
			case "wellbeing" -> Component.translatable(key, GsonHelper.getAsInt(o, "percent", 0), GsonHelper.getAsInt(o, "max", 0));
			case "festival" -> Component.translatable(key, GsonHelper.getAsInt(o, "replace_every", 1));
			default -> Component.translatable("class.aliveworkplace.gives.other", type.toString());
		};
	}

	private static String fmt(float f) {
		return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.valueOf(f);
	}

	/**
	 * The people list's class line: "Burgher · married to Tomas", or the class alone ("Artisan"); null without a class.
	 */
	@Nullable
	public static Component classLine(Villager villager) {
		SocialClass c = SocialClasses.ENABLED ? SocialClasses.of(villager) : null;
		if (c == null) {
			return null;
		}
		Couples.Partner partner = Couples.partner(villager);
		return partner != null && partner.married()
			? Component.translatable("screen.aliveworkplace.hall.class_married", c.name(), partner.name())
			: Component.translatable("screen.aliveworkplace.hall.class_alone", c.name());
	}

	/**
	 * A villager's page lines: the needs of their class, then of the next one, each ticked or crossed for them ({@code
	 * village} is the screen's one read of the village). Empty without a class.
	 */
	public static List<Component> needLines(Villager villager, ClassNeeds.Village village) {
		SocialClass c = SocialClasses.ENABLED ? SocialClasses.of(villager) : null;
		List<Component> out = new ArrayList<>();
		if (c == null) {
			return out;
		}
		if (!c.needs().isEmpty()) {
			out.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.class_needs", c.name()), ChatFormatting.GOLD));
			ticks(out, c.needs(), villager, village);
		}
		SocialClass next = SocialClasses.step(c, 1);
		if (next != null && !next.needs().isEmpty()) {
			out.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.class_next", next.name()), ChatFormatting.GOLD));
			ticks(out, next.needs(), villager, village);
		}
		return out;
	}

	private static void ticks(List<Component> out, List<ClassNeeds.Need> needs, Villager villager, ClassNeeds.Village village) {
		for (ClassNeeds.Need need : needs) {
			boolean ok = ClassNeeds.holds(need, villager, village);
			out.add(VillageHallScreen.line(Component.translatable(ok ? "screen.aliveworkplace.hall.need_met" : "screen.aliveworkplace.hall.need_lacking", needName(need)),
				ok ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
	}

	/** The luxuries in the village store, by luxury, most first: what the food icon's tooltip adds. */
	public static Map<ResourceLocation, Long> luxuriesInStore(ServerLevel level, BlockPos hall) {
		Map<ResourceLocation, Long> out = new LinkedHashMap<>();
		if (Luxuries.all().isEmpty()) {
			return out;
		}
		Map<Item, Long> contents = SupplyContainers.contents(level, VillageNeeds.store(level, hall));
		for (Luxuries.Luxury luxury : Luxuries.all().values()) {
			long n = 0;
			for (Map.Entry<Item, Long> e : contents.entrySet()) {
				if (luxury.matches(new ItemStack(e.getKey()))) {
					n += e.getValue();
				}
			}
			if (n > 0) {
				out.put(luxury.id(), n);
			}
		}
		List<Map.Entry<ResourceLocation, Long>> sorted = new ArrayList<>(out.entrySet());
		sorted.sort(Map.Entry.<ResourceLocation, Long>comparingByValue().reversed());
		Map<ResourceLocation, Long> result = new LinkedHashMap<>();
		sorted.forEach(e -> result.put(e.getKey(), e.getValue()));
		return result;
	}

	/** The food icon's luxuries line: "Luxuries in store: Berry Wine ×3, Cider ×1", or that there are none; null with classes off. */
	@Nullable
	public static Component luxuriesLine(ServerLevel level, BlockPos hall) {
		if (!SocialClasses.ENABLED) {
			return null;
		}
		Map<ResourceLocation, Long> store = luxuriesInStore(level, hall);
		if (store.isEmpty()) {
			return VillageHallScreen.line("screen.aliveworkplace.hall.luxuries_none", ChatFormatting.DARK_GRAY);
		}
		MutableComponent list = Component.empty();
		int i = 0;
		for (Map.Entry<ResourceLocation, Long> e : store.entrySet()) {
			list.append(i++ == 0 ? Component.empty() : Component.literal(", "))
				.append(Component.translatable("screen.aliveworkplace.hall.luxury_count", luxuryName(e.getKey()), e.getValue()));
		}
		return VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.luxuries", list), ChatFormatting.LIGHT_PURPLE);
	}

	/** A class tip: {@code households} of {@code from} lack {@code need} to become {@code to}. */
	public record ClassTip(int households, SocialClass from, SocialClass to, ClassNeeds.Need need) {
	}

	/**
	 * Up to {@link #TIPS} class tips for "What next?", most households first: for each class, each need of the class
	 * above that the households below it lack (needs every household already has aren't tips).
	 */
	public static List<ClassTip> classTips(ServerLevel level, BlockPos hall) {
		List<ClassTip> tips = new ArrayList<>();
		if (!SocialClasses.ENABLED || SocialClasses.ladder().isEmpty()) {
			return tips;
		}
		List<Placed> placed = households(level, hall);
		if (placed.isEmpty()) {
			return tips;
		}
		ClassNeeds.Village village = new ClassNeeds.Village(level, hall, Chronicle.day(level));
		for (SocialClass from : SocialClasses.ladder()) {
			SocialClass to = SocialClasses.step(from, 1);
			if (to == null) {
				continue;
			}
			List<Placed> in = placed.stream().filter(p -> p.social() == from).toList();
			if (in.isEmpty()) {
				continue;
			}
			for (ClassNeeds.Need need : to.needs()) {
				int lacking = 0;
				for (Placed p : in) {
					if (!ClassNeeds.holds(need, p.household().members(), village)) {
						lacking++;
					}
				}
				if (lacking > 0) {
					tips.add(new ClassTip(lacking, from, to, need));
				}
			}
		}
		tips.sort(Comparator.comparingInt(ClassTip::households).reversed()); // stable: lower classes first, then file order
		return tips.subList(0, Math.min(TIPS, tips.size()));
	}

	/** How to meet a need, for the tip's second line. */
	public static Component how(ClassNeeds.Need need) {
		return switch (need) {
			case ClassNeeds.Home h -> Component.translatable("advice.aliveworkplace.rise.home", Component.translatable("enchantment.level." + h.grade()));
			case ClassNeeds.FedDays f -> Component.translatable("advice.aliveworkplace.rise.fed_days", f.days());
			case ClassNeeds.DietNeed d -> Component.translatable("advice.aliveworkplace.rise.diet", io.github.jcondedata.aliveworkplace.people.Diet.VARIED_KINDS);
			case ClassNeeds.Beauty b -> Component.translatable("advice.aliveworkplace.rise.beauty", b.points());
			case ClassNeeds.Building b -> Component.translatable("advice.aliveworkplace.rise.building", Blueprints.displayName(b.blueprint()));
			case ClassNeeds.VillageRank r -> Component.translatable("advice.aliveworkplace.rise.village_rank", r.rank().title());
			case ClassNeeds.Services s -> Component.translatable("advice.aliveworkplace.rise.services");
			case ClassNeeds.Luxury l -> Component.translatable("advice.aliveworkplace.rise.luxury", luxuryName(l.id()));
			case ClassNeeds.Unknown u -> Component.translatable("advice.aliveworkplace.rise.unknown");
		};
	}

	/** The tips as "What next?" shows them. */
	static List<VillageAdvice.Tip> adviceTips(ServerLevel level, BlockPos hall) {
		List<VillageAdvice.Tip> out = new ArrayList<>();
		for (ClassTip t : classTips(level, hall)) {
			out.add(new VillageAdvice.Tip(t.households() == 1 ? "class_one" : "class", classIcon(t.to()), t.households(), t.from().name(), needName(t.need()), t.to().name(), how(t.need())));
		}
		return out;
	}

	private ClassesPage() {
	}
}
