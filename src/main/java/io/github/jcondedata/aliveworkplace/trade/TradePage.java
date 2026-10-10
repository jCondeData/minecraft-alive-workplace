package io.github.jcondedata.aliveworkplace.trade;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The hall's Trade page (ROADMAP 33.4, design note M33): what the minecart in the hall's divider opens while the
 * village economy is on. Row 1 is the page's own: the way back, then a tab for each part of trade that has landed,
 * from {@link #FIRST_TAB} on in {@link Tab}'s order (Routes, Prices; Pacts, Realm and Colonies stay hidden until their
 * items {@link #register} them), the open one marked {@link #OPEN}. Row 2 is the divider; the open tab fills rows 3
 * to 6 ({@link VillageHallScreen#FIRST_ROW} onwards).
 *
 * <p><b>Prices</b> lists every trade good by its icon: what the village sells a bundle for and what it pays for one
 * (in CobbleDollars with the pack), how the price moved since yesterday, whether the village is known for it or short
 * of it, and the dearer and the cheaper village among those it has routes with. The icon carries its marks
 * ({@link #marks}: {@link #STAR}, {@link #SHORT}, and one of {@link #UP}, {@link #DOWN}, {@link #STEADY}), which the
 * hall's own screen draws over it; the tooltip says the same in words, so nothing depends on the drawing.
 *
 * <p><b>Trading</b> (33.5, {@link Board}): a click on a good sells the village a bundle if the player carries one, and
 * buys one otherwise; a right click always buys; with shift, as many bundles as will go. The tooltip says what a click
 * will do and what the village can spare, and after a trade what came of it. Without a Storehouse the tab says the
 * market needs one. The village's treasury sits in the row ({@link #TREASURY}): what it holds and its cap, and a click
 * collects it for those who may ({@link Treasury#mayCollect}). A stranger who right-clicks the hall of a protected
 * village gets this page with the Prices tab and nothing else ({@link #openForStranger}).
 */
public final class TradePage {
	/** The page's tabs, in the row's order. */
	public enum Tab {
		ROUTES, PRICES, PACTS, REALM, COLONIES;

		/** The tab's slot in the page's first row. */
		public int slot() {
			return FIRST_TAB + ordinal();
		}
	}

	public static final int BACK = 0;
	/** The village's treasury, in the page's row (33.5). */
	public static final int TREASURY = 2;
	/** The Routes tab's slot (where the routes page's title always was); the other tabs follow it. */
	public static final int FIRST_TAB = 4;
	/** Goods the Prices tab shows: rows 3 to 6. */
	public static final int GOODS = ChoiceMenu.SIZE - VillageHallScreen.FIRST_ROW;
	/** The village sells a bundle at this many percent of what it pays for one (33.5). */
	public static final int SELL_PERCENT = 110;

	/** The marks an icon carries for the hall's screen to draw (custom data key {@link #MARKS}, comma-separated). */
	public static final String MARKS = "aliveworkplace_marks";
	public static final String STAR = "star";
	public static final String SHORT = "short";
	public static final String UP = "up";
	public static final String DOWN = "down";
	public static final String STEADY = "steady";
	/** The open tab. */
	public static final String OPEN = "open";

	/** Fills a tab's rows; {@code again} lays the page out again on the same tab (after a click that changed something). */
	public interface Content {
		void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer, Runnable again);
	}

	/** A tab that has landed: its icon in the row and what fills the page under it. */
	private record Entry(HallPages.Icon icon, Content content) {
	}

	private static final Map<Tab, Entry> TABS = new EnumMap<>(Tab.class);

	/** What the last trade on an open page came to, shown once in its good's tooltip. */
	private record Note(ResourceLocation good, Component text) {
	}

	private static final Map<ChoiceMenu, Note> NOTES = new WeakHashMap<>();

	static {
		TABS.put(Tab.ROUTES, new Entry(VillageHallScreen::routesHeader, (menu, level, hall, viewer, again) ->
			VillageHallScreen.routesList(menu, level, hall, again)));
		TABS.put(Tab.PRICES, new Entry(TradePage::pricesTab, TradePage::prices));
	}

	/** Gives {@code tab} its icon and contents: from then on it shows in the row. A later item's tab calls it once, at start-up. */
	public static synchronized void register(Tab tab, HallPages.Icon icon, Content content) {
		TABS.put(tab, new Entry(icon, content));
	}

	/** Whether the minecart opens this page (the village economy is on); off, it opens the routes page as it always did. */
	public static boolean shown() {
		return Economy.ENABLED;
	}

	/** The tabs in the row now, in order (Prices only while the village economy is on). */
	public static synchronized List<Tab> tabs() {
		List<Tab> out = new ArrayList<>();
		for (Tab tab : Tab.values()) {
			if (TABS.containsKey(tab) && (tab != Tab.PRICES || Economy.ENABLED)) {
				out.add(tab);
			}
		}
		return out;
	}

	/** The tabs' names, "Routes, Prices" (the hall's minecart names them). */
	public static Component tabNames() {
		MutableComponent out = Component.empty();
		for (Tab tab : tabs()) {
			if (!out.getSiblings().isEmpty()) {
				out.append(", ");
			}
			out.append(Component.translatable("screen.aliveworkplace.trade.tab." + tab.name().toLowerCase(java.util.Locale.ROOT)));
		}
		return out;
	}

	/** The page on {@code tab}, not shown to anyone (tests). */
	public static ChoiceMenu forTest(ServerPlayer player, BlockPos hall, Tab tab) {
		return ChoiceMenu.detached(player, menu -> render(menu, Players.level(player), hall, tab, null, player));
	}

	/** The page with only its Prices tab, not shown to anyone (tests): what a stranger in a protected village gets. */
	public static ChoiceMenu forStrangerTest(ServerPlayer player, BlockPos hall) {
		return ChoiceMenu.detached(player, menu -> render(menu, Players.level(player), hall, Tab.PRICES, null, player, true));
	}

	/**
	 * Opens the page for a stranger in a protected village (33.5): the Prices tab and nothing else, so they can trade
	 * there; no way to the hall's screen, the routes or any other tab.
	 */
	public static void openForStranger(ServerPlayer player, BlockPos hall) {
		ServerLevel level = Players.level(player);
		ChoiceMenu.openHall(player, Component.translatable("screen.aliveworkplace.hall.title", VillageHalls.name(level, hall)),
			p -> p.isAlive() && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL) && p.position().distanceToSqr(Vec3.atCenterOf(hall)) <= 64,
			menu -> render(menu, level, hall, Tab.PRICES, null, player, true));
	}

	/**
	 * Lays the page out in {@code menu} with {@code tab} open (a tab that hasn't landed opens Routes). {@code back}
	 * (null: none) returns to the hall's screen.
	 */
	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, Tab wanted, @Nullable Runnable back, @Nullable ServerPlayer viewer) {
		render(menu, level, hall, wanted, back, viewer, false);
	}

	/** The same; {@code pricesOnly}: the row holds the Prices tab alone (a stranger in a protected village). */
	private static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, Tab wanted, @Nullable Runnable back, @Nullable ServerPlayer viewer,
							   boolean pricesOnly) {
		List<Tab> tabs = pricesOnly ? List.of(Tab.PRICES) : tabs();
		Tab tab = tabs.contains(wanted) ? wanted : tabs.get(0);
		menu.clearButtons();
		if (back != null) {
			menu.button(BACK, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE),
				p -> back.run());
		}
		for (Tab t : tabs) {
			Entry entry;
			synchronized (TradePage.class) {
				entry = TABS.get(t);
			}
			ItemStack icon = entry.icon().icon(level, hall);
			if (t == tab) {
				mark(icon, OPEN);
				menu.button(t.slot(), icon, null);
			} else {
				menu.button(t.slot(), icon, p -> {
					render(menu, level, hall, t, back, p, pricesOnly);
					menu.broadcastChanges();
				});
			}
		}
		Runnable again = () -> {
			render(menu, level, hall, tab, back, viewer, pricesOnly);
			menu.broadcastChanges();
		};
		if (Economy.ENABLED && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			menu.button(TREASURY, treasuryIcon(level, entity, viewer), p -> {
				Chat.chat(p, Treasury.collect(level, hall, p));
				again.run();
			});
		}
		menu.divider(1);
		Entry open;
		synchronized (TradePage.class) {
			open = TABS.get(tab);
		}
		open.content().fill(menu, level, hall, viewer, again);
	}

	/** The treasury in the page's row: what it holds (to the cent), its cap, and who may collect it. */
	static ItemStack treasuryIcon(ServerLevel level, VillageHallBlockEntity entity, @Nullable ServerPlayer viewer) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.treasury_cap", money(Treasury.cap(entity) * 100)), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.treasury_about", ChatFormatting.GRAY));
		if (viewer == null || Treasury.mayCollect(level, entity, viewer)) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.treasury_collect", ChatFormatting.DARK_GRAY));
		} else {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.treasury_theirs", entity.ownerName()), ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(Items.GOLD_NUGGET, Component.translatable("screen.aliveworkplace.trade.treasury", money(entity.treasury())),
			ChatFormatting.GOLD, lore.toArray(Component[]::new));
	}

	/** The Prices tab's icon: an emerald, with what the page's marks mean. */
	static ItemStack pricesTab(ServerLevel level, BlockPos hall) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.prices_about", SELL_PERCENT - 100), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.prices_marks", ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.prices_trade", ChatFormatting.GRAY));
		if (Caravans.Data.get(level).market(hall).priceDay() < 0) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.prices_not_yet", ChatFormatting.YELLOW));
		}
		if (Caravans.storehouse(level, hall).isEmpty()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.needs_storehouse", ChatFormatting.RED));
		}
		return VillageHallScreen.icon(Items.EMERALD, Component.translatable("screen.aliveworkplace.trade.prices_title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, lore.toArray(Component[]::new));
	}

	/** The Prices tab: every good, in the goods' own order; a click trades ({@link Board}). */
	static void prices(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer, Runnable again) {
		Note note = NOTES.remove(menu);
		List<BlockPos> chests = Caravans.storehouse(level, hall);
		List<ItemStack> stock = chests.isEmpty() ? null : Board.stock(level, chests);
		List<TradeGoods.Good> goods = TradeGoods.all();
		if (goods.isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.trade.no_goods"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.trade.no_goods_hint", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		int slot = VillageHallScreen.FIRST_ROW;
		for (TradeGoods.Good good : goods) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			ItemStack icon = goodIcon(level, hall, good, stock, viewer, note != null && note.good().equals(good.id()) ? note.text() : null);
			menu.button(slot++, icon, p -> {
				boolean buying = menu.rightClicked() || Board.carried(p, good) < good.bundle();
				Board.Result result = buying ? Board.buy(level, hall, p, good, menu.shiftClicked()) : Board.sell(level, hall, p, good, menu.shiftClicked());
				Component said = Board.message(level, hall, result);
				if (!result.traded()) {
					level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1f);
				}
				Chat.actionBar(p, said);
				NOTES.put(menu, new Note(good.id(), said));
				again.run();
			});
		}
	}

	/**
	 * A good on the Prices tab: its icon with its marks, the tooltip that says the same in words, and how to trade it:
	 * what the village can spare of {@code stock} (null: it has no Storehouse, and the tooltip says the market needs
	 * one), what a click does for {@code viewer}, and under the name what their last trade came to ({@code note}).
	 */
	public static ItemStack goodIcon(ServerLevel level, BlockPos hall, TradeGoods.Good good, @Nullable List<ItemStack> stock, @Nullable ServerPlayer viewer,
									 @Nullable Component note) {
		Caravans.Data data = Caravans.Data.get(level);
		Market market = data.market(hall);
		Market.Price price = market.prices().get(good.id());
		int pays = price == null ? good.basePrice() : price.cents();
		int yesterday = price == null ? pays : price.yesterday();
		Component village = VillageHalls.name(level, hall);
		Set<String> marks = new LinkedHashSet<>();
		List<Component> lore = new ArrayList<>();
		if (note != null) {
			lore.add(VillageHallScreen.line(note, ChatFormatting.WHITE));
		}
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.bundle", good.bundle()), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.sells", money(selling(pays))), ChatFormatting.GREEN));
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.pays", money(pays)), ChatFormatting.YELLOW));
		if (pays > yesterday) {
			marks.add(UP);
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.up", money(yesterday)), ChatFormatting.GREEN));
		} else if (pays < yesterday) {
			marks.add(DOWN);
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.down", money(yesterday)), ChatFormatting.RED));
		} else {
			marks.add(STEADY);
			lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.steady", ChatFormatting.GRAY));
		}
		if (market.knownFor().contains(good.id())) {
			marks.add(STAR);
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.known_for", village), ChatFormatting.GOLD));
		} else if (market.shortOf().contains(good.id())) {
			marks.add(SHORT);
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.short_of", village), ChatFormatting.RED));
		}
		Partner dearest = null;
		Partner cheapest = null;
		Set<BlockPos> partners = data.partners(hall);
		for (BlockPos other : partners) {
			Partner p = partner(data, other, good.id());
			if (p == null) {
				continue;
			}
			if (p.cents() > pays && (dearest == null || p.cents() > dearest.cents())) {
				dearest = p;
			}
			if (p.cents() < pays && (cheapest == null || p.cents() < cheapest.cents())) {
				cheapest = p;
			}
		}
		if (dearest != null) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.dearer", dearest.name(), money(dearest.cents())), ChatFormatting.GRAY));
		}
		if (cheapest != null) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.cheaper", cheapest.name(), money(cheapest.cents())), ChatFormatting.GRAY));
		}
		if (partners.isEmpty()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.no_routes", ChatFormatting.DARK_GRAY));
		} else if (dearest == null && cheapest == null) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.same_everywhere", ChatFormatting.DARK_GRAY));
		}
		if (stock == null) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.needs_storehouse", ChatFormatting.RED));
		} else {
			int spare = Board.spareBundles(good, stock);
			lore.add(VillageHallScreen.line(spare > 0 ? Component.translatable("screen.aliveworkplace.trade.spare", spare)
				: Component.translatable("screen.aliveworkplace.trade.none_spare", Caravans.KEEP), spare > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
			int carried = viewer == null ? 0 : Board.carried(viewer, good);
			if (carried >= good.bundle()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.trade.click_sell", carried), ChatFormatting.DARK_GRAY));
				lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.click_buy_right", ChatFormatting.DARK_GRAY));
			} else {
				lore.add(VillageHallScreen.line("screen.aliveworkplace.trade.click_buy", ChatFormatting.DARK_GRAY));
			}
		}
		ItemStack shown = new ItemStack(good.icon());
		if (shown.is(Items.POTION)) {
			shown.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING)); // remedies: the board trades potions that heal
		}
		ItemStack icon = VillageHallScreen.icon(shown, good.name().copy(), ChatFormatting.WHITE, lore.toArray(Component[]::new));
		marks.forEach(m -> mark(icon, m));
		return icon;
	}

	/**
	 * The hall's name icon's line: "Known for: Timber, Wool. Short of: Bread" (either half alone when the other is empty);
	 * null with the economy off or nothing to say.
	 */
	@Nullable
	public static Component knownAndShort(ServerLevel level, BlockPos hall) {
		if (!Economy.ENABLED) {
			return null;
		}
		Market market = Caravans.Data.get(level).market(hall);
		Component known = names(market.knownFor());
		Component shortOf = names(market.shortOf());
		if (known == null && shortOf == null) {
			return null;
		}
		return known == null ? Component.translatable("screen.aliveworkplace.hall.short_of", shortOf)
			: shortOf == null ? Component.translatable("screen.aliveworkplace.hall.known_for", known)
			: Component.translatable("screen.aliveworkplace.hall.known_and_short", known, shortOf);
	}

	/** The goods' names, "Timber, Wool"; null when none of them is a good any more. */
	@Nullable
	private static Component names(List<ResourceLocation> goods) {
		MutableComponent out = null;
		for (ResourceLocation id : goods) {
			TradeGoods.Good good = TradeGoods.get(id);
			if (good != null) {
				out = out == null ? Component.empty().append(good.name()) : out.append(", ").append(good.name());
			}
		}
		return out;
	}

	/** A village on one of ours' routes and what it pays for a bundle of a good. */
	public record Partner(BlockPos hall, Component name, int cents) {
	}

	/** {@code other}'s price for {@code good}, or null while it's off the caravans' list or has no price for it yet. */
	@Nullable
	static Partner partner(Caravans.Data data, BlockPos other, ResourceLocation good) {
		Caravans.Village village = data.village(other);
		Market.Price price = data.market(other).prices().get(good);
		return village == null || price == null ? null : new Partner(other, village.name(), price.cents());
	}

	/** What the village sells a bundle for when it pays {@code cents} for one: 10% over, to the cent. */
	public static int selling(int cents) {
		return (int) Math.round(cents * SELL_PERCENT / 100.0);
	}

	/** A price as players read it: "0.88 emeralds", "1.4 emeralds", "1 emerald"; with the pack, "88 CobbleDollars". */
	public static Component money(int cents) {
		if (Money.cobbleDollars()) {
			return Money.describe(Math.round(cents * (double) Money.DOLLARS_PER_EMERALD / 100), 0);
		}
		return cents == 100 ? Component.translatable("screen.aliveworkplace.trade.emerald")
			: Component.translatable("screen.aliveworkplace.trade.emeralds", decimal(cents));
	}

	/** Hundredths as a number without needless zeros: 88 is "0.88", 140 is "1.4", 200 is "2". */
	public static String decimal(int cents) {
		String whole = Integer.toString(cents / 100);
		int rest = Math.abs(cents % 100);
		if (rest == 0) {
			return whole;
		}
		return whole + "." + (rest % 10 == 0 ? Integer.toString(rest / 10) : (rest < 10 ? "0" : "") + rest);
	}

	/** Adds {@code mark} to the marks {@code icon} carries. */
	public static void mark(ItemStack icon, String mark) {
		Set<String> marks = new LinkedHashSet<>(marks(icon));
		if (marks.add(mark)) {
			CompoundTag tag = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			tag.putString(MARKS, String.join(",", marks));
			icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
	}

	/** The marks {@code icon} carries (none for any other icon). */
	public static Set<String> marks(ItemStack icon) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(MARKS)) {
			return Set.of();
		}
		String marks = data.copyTag().getString(MARKS);
		return marks.isEmpty() ? Set.of() : new LinkedHashSet<>(List.of(marks.split(",")));
	}

	private TradePage() {
	}
}
