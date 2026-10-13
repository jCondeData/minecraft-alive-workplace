package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.explore.Explorers;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.wonder.powers.ForecastPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.SkyChartPower;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Observatory's powers (ROADMAP 35.18), while a finished, standing Wonder of the village grants them
 * ({@link Wonders#power}: a damaged one, or Wonders off, pauses both; what was found stays in the hall):
 * <ul>
 * <li>{@code sky_chart}: the hall searches the land for the chart ({@link SkyChart.Search}, saved in the hall), one
 * kind of structure an in-game hour while the server's tick has room, and searches again each week; the hall's map
 * button also draws, on a second empty map, the Sky Chart ({@link #chart}) with a marker for every structure found,
 * each named in its tooltip, and the bandit camps the Coming days name;</li>
 * <li>{@code forecast}: the hall's calendar page shows the Coming days ({@link #comingDays}).</li>
 * </ul>
 */
public final class ObservatoryPowers {
	/** A new search starts every this many days. */
	public static final int WEEK_DAYS = 7;
	/** The structures the chart's tooltip names one by one, nearest first, before "and N more". */
	public static final int NAMED = 12;
	/** Coming days the calendar page has room for (its last two rows). */
	public static final int SHOWN = 18;
	/** The Coming days' title on the calendar page, under the seasons. */
	public static final int HEADER = VillageHallScreen.FIRST_ROW + 13;
	/** The first Coming day's slot (the two rows under the title). */
	public static final int FIRST = VillageHallScreen.FIRST_ROW + 18;

	/** Where the search looks (the world's own structure check; tests put a stub here). */
	public static volatile SkyChart.Finder finder = SkyChart.VANILLA;
	/** The kinds to search, or null for the world's own ({@link SkyChart#kinds}; tests set their stub's). */
	@Nullable
	public static volatile List<ResourceLocation> kindsForTest;

	private ObservatoryPowers() {
	}

	/** The Sky Chart's size while the village's Observatory stands, or empty. */
	public static Optional<Integer> chartSize(ServerLevel level, BlockPos hall) {
		return Wonders.power(level, hall, SkyChartPower.TYPE).map(p -> p instanceof SkyChartPower s ? s.size() : SkyChart.DEFAULT_SIZE);
	}

	public static boolean charts(ServerLevel level, BlockPos hall) {
		return chartSize(level, hall).isPresent();
	}

	public static boolean foretells(ServerLevel level, BlockPos hall) {
		return Wonders.power(level, hall, ForecastPower.TYPE).isPresent();
	}

	private static List<ResourceLocation> kinds(ServerLevel level) {
		List<ResourceLocation> stub = kindsForTest;
		return stub != null ? stub : SkyChart.kinds(level);
	}

	/** The hall's tick: a step of the search when it's due ({@link SkyChart.Search#tick}), a new search each week. */
	public static void tick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (level.getGameTime() % SkyChart.STEP_TICKS != 0) {
			return;
		}
		tick(level, hall, entity, level.getGameTime(), SkyChart.tickHasRoom(level.getServer()));
	}

	/** One tick of the search at {@code gameTime}; returns whether a region was searched. */
	public static boolean tick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long gameTime, boolean room) {
		Optional<Integer> size = chartSize(level, hall);
		if (size.isEmpty()) {
			return false;
		}
		SkyChart.Search search = entity.skyChart();
		List<ResourceLocation> kinds = kinds(level);
		long week = Math.floorDiv(Chronicle.day(level) - 1, WEEK_DAYS);
		if (search.week() != week) {
			search.restart(kinds);
			search.setWeek(week);
			entity.setChanged();
		}
		boolean stepped = search.tick(level, SkyChart.Area.around(hall, size.get()), kinds, finder, gameTime, room);
		if (stepped) {
			entity.setChanged();
		}
		return stepped;
	}

	/**
	 * Draws the Sky Chart for a second empty map from {@code player}'s inventory (none needed in creative), after the
	 * village map; empty while the Observatory doesn't stand, else what to tell them.
	 */
	public static Optional<Component> draw(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!charts(level, hall)) {
			return Optional.empty();
		}
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize() && slot < 0; i++) {
			if (player.getInventory().getItem(i).is(Items.MAP)) {
				slot = i;
			}
		}
		if (slot < 0 && !player.getAbilities().instabuild) {
			return Optional.of(Component.translatable("message.aliveworkplace.sky_chart.needs_map").withStyle(ChatFormatting.YELLOW));
		}
		ItemStack chart = chart(level, hall);
		if (slot >= 0 && !player.getAbilities().instabuild) {
			player.getInventory().getItem(slot).shrink(1);
		}
		if (!player.getInventory().add(chart)) {
			player.drop(chart, false);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1.2f);
		int found = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.skyChart().found().size() : 0;
		return Optional.of(Component.translatable("message.aliveworkplace.sky_chart.drawn", found).withStyle(ChatFormatting.GREEN));
	}

	/** The Sky Chart of the village round {@code hall}: what the Observatory found, the bandit camps, each named in the tooltip. */
	public static ItemStack chart(ServerLevel level, BlockPos hall) {
		SkyChart.Area area = SkyChart.Area.around(hall, chartSize(level, hall).orElse(SkyChart.DEFAULT_SIZE));
		List<SkyChart.Found> found = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.skyChart().found() : List.of();
		ItemStack map = SkyChart.chart(level, area, found);
		MapItemSavedData data = level.getMapData(map.get(DataComponents.MAP_ID));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("item.aliveworkplace.sky_chart.about", VillageHalls.name(level, hall), area.blocksAcross(), Chronicle.day(level))
			.withStyle(ChatFormatting.GRAY));
		List<BanditCamps.Camp> camps = camps(level, hall);
		for (BanditCamps.Camp camp : camps) {
			if (data != null && area.contains(camp.pos())) {
				MapItemSavedData.addTargetDecoration(map, area.markerPos(data, camp.pos()), "sky_camp_" + camp.pos().asLong(), MapDecorationTypes.RED_MARKER);
			}
			lore.add(Component.translatable("item.aliveworkplace.sky_chart.camp", DefencePage.roughly(hall, camp.pos())).withStyle(ChatFormatting.RED));
		}
		List<SkyChart.Found> near = found.stream().filter(f -> area.contains(f.pos()))
			.sorted(Comparator.comparingDouble(f -> f.pos().distSqr(hall))).toList();
		for (int i = 0; i < near.size() && i < NAMED; i++) {
			lore.add(Component.translatable("item.aliveworkplace.sky_chart.found", name(near.get(i).structure()), DefencePage.roughly(hall, near.get(i).pos()))
				.withStyle(ChatFormatting.DARK_GRAY));
		}
		if (near.size() > NAMED) {
			lore.add(Component.translatable("item.aliveworkplace.sky_chart.more", near.size() - NAMED).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (near.isEmpty()) {
			lore.add(Component.translatable("item.aliveworkplace.sky_chart.searching").withStyle(ChatFormatting.DARK_GRAY));
		}
		map.set(DataComponents.LORE, new ItemLore(lore));
		return map;
	}

	/** "Plains Village", "Ruined Portal": the language file's name, or made from the id. */
	public static Component name(ResourceLocation structure) {
		return Component.translatableWithFallback("structure." + structure.getNamespace() + "." + structure.getPath().replace('/', '.'),
			Explorers.pretty(structure.getPath()));
	}

	private static List<BanditCamps.Camp> camps(ServerLevel level, BlockPos hall) {
		return BanditCamps.ENABLED ? BanditCamps.all(level).stream().filter(c -> c.hall().equals(hall)).toList() : List.of();
	}

	/** One of the Coming days: what, when (the day, or -1 for tonight's), where if it has a place. */
	public record Coming(Item icon, Component what, long day, Optional<BlockPos> where) {
	}

	/** What's ahead for the village round {@code hall}, soonest first (empty while the Observatory doesn't stand). */
	public static List<Coming> comingDays(ServerLevel level, BlockPos hall) {
		if (!foretells(level, hall) || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return List.of();
		}
		long today = Chronicle.day(level);
		List<Coming> out = new ArrayList<>();
		if (Festivals.ENABLED) {
			out.add(new Coming(Items.CAKE, Component.translatable("screen.aliveworkplace.coming.festival"), Festivals.nextDay(level, hall, entity), Optional.empty()));
		}
		if (MarketDays.ENABLED) {
			out.add(new Coming(Items.EMERALD, Component.translatable("screen.aliveworkplace.coming.market"), MarketDays.nextDay(level, hall, entity.lastMarketDay()),
				MarketDays.square(level, hall)));
		}
		Set<UUID> seen = new HashSet<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			Couples.Partner p = Couples.partner(v);
			if (Couples.ENABLED && p != null && !p.married() && seen.add(v.getUUID()) && seen.add(p.id())) {
				out.add(new Coming(Items.POPPY, Component.translatable("screen.aliveworkplace.coming.wedding", v.getDisplayName(), p.name()),
					Math.max(today, p.since() + Couples.COURTSHIP_DAYS), Optional.empty()));
			}
		}
		long now = level.getGameTime();
		long timeOfDay = level.getDayTime() % VillageNeeds.DAY;
		for (Caravans.Shipment s : Caravans.Data.get(level).onTheRoad()) {
			if (s.to().equals(hall) || s.from().equals(hall) && !s.back()) {
				boolean coming = s.to().equals(hall);
				long day = today + (timeOfDay + Math.max(0, s.arrives() - now)) / VillageNeeds.DAY;
				BlockPos other = coming ? s.from() : s.to();
				out.add(new Coming(Items.CHEST_MINECART, Component.translatable(coming ? "screen.aliveworkplace.coming.caravan_in" : "screen.aliveworkplace.coming.caravan_out",
					VillageHalls.name(level, other)), day, Optional.of(other)));
			}
		}
		Optional<Villager> innkeeper = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && Innkeepers.isInnkeeper(v)).stream().findFirst();
		if (innkeeper.isPresent()) {
			boolean thisMorning = timeOfDay < Innkeepers.MORNING && Innkeepers.arrivedOn(innkeeper.get(), level.getDayTime() / VillageNeeds.DAY) < Innkeepers.arrivalsPerMorning(innkeeper.get());
			out.add(new Coming(Items.WHITE_BED, Component.translatable("screen.aliveworkplace.coming.traveller"), thisMorning ? today : today + 1,
				Optional.of(innkeeper.get().blockPosition())));
		}
		long index = level.getDayTime() / VillageNeeds.DAY; // vanilla's moon: phase (day % 8), 0 full
		out.add(new Coming(Items.ENDER_EYE, Component.translatable("screen.aliveworkplace.coming.full_moon"), today + Math.floorMod(-index, 8), Optional.empty()));
		Seer.State seer = entity.seer();
		for (Map.Entry<String, Seer.Told> e : seer.guests().entrySet()) {
			if (e.getValue().day() >= today && e.getValue().legend().isPresent()) {
				ResourceLocation legend = e.getValue().legend().get();
				out.add(new Coming(Items.NETHER_STAR, Component.translatable("screen.aliveworkplace.coming.legend",
					Component.translatable("legend." + legend.getNamespace() + "." + legend.getPath() + ".title")), e.getValue().day(), Optional.empty()));
			}
		}
		out.sort(Comparator.comparingLong(Coming::day));
		for (BanditCamps.Camp camp : camps(level, hall)) {
			out.add(new Coming(Items.CROSSBOW, Component.translatable("screen.aliveworkplace.coming.camp", DefencePage.roughly(hall, camp.pos())), -1, Optional.of(camp.pos())));
		}
		if (VillageRaids.ENABLED) {
			Component risk;
			if (seer.toldDay() == today && !seer.raidStarted()) {
				risk = seer.raid() ? Component.translatable("screen.aliveworkplace.coming.raid_foretold") : Component.translatable("screen.aliveworkplace.coming.quiet_foretold");
			} else {
				int villagers = VillageHalls.census(level, hall).villagers();
				risk = Component.translatable("screen.aliveworkplace.coming.raid_risk", Math.round(VillageRaids.chance(level, hall, villagers) * 100));
			}
			out.add(new Coming(Items.IRON_SWORD, risk, -1, Optional.empty()));
		}
		return out;
	}

	/** "today", "tomorrow", "in 3 days (day 12)", "tonight". */
	public static Component when(ServerLevel level, long day) {
		long today = Chronicle.day(level);
		if (day < 0) {
			return Component.translatable("screen.aliveworkplace.coming.tonight");
		}
		return day <= today ? Component.translatable("screen.aliveworkplace.coming.today")
			: day == today + 1 ? Component.translatable("screen.aliveworkplace.coming.tomorrow")
			: Component.translatable("screen.aliveworkplace.coming.in", day - today, day);
	}

	/** The calendar page's Coming days, under the seasons, while the Observatory stands. */
	public static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		List<Coming> coming = comingDays(level, hall);
		if (coming.isEmpty()) {
			return;
		}
		menu.button(HEADER, VillageHallScreen.icon(Items.SPYGLASS, Component.translatable("screen.aliveworkplace.coming"), ChatFormatting.GOLD,
			VillageHallScreen.line("screen.aliveworkplace.coming.about", ChatFormatting.GRAY)), null);
		for (int i = 0; i < coming.size() && i < SHOWN; i++) {
			Coming c = coming.get(i);
			List<Component> lore = new ArrayList<>();
			lore.add(VillageHallScreen.line(when(level, c.day()), c.day() >= 0 && c.day() <= Chronicle.day(level) + 1 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
			c.where().ifPresent(pos -> lore.add(VillageHallScreen.line(DefencePage.roughly(hall, pos), ChatFormatting.DARK_GRAY)));
			menu.button(FIRST + i, VillageHallScreen.icon(c.icon(), c.what().copy(), ChatFormatting.WHITE, lore.toArray(Component[]::new)), null);
		}
	}
}
