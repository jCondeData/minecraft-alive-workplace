package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.wonder.ObservatoryPowers;
import io.github.jcondedata.aliveworkplace.wonder.SkyChart;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.ForecastPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.SkyChartPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The Observatory's powers (ROADMAP 35.18), with a stub structure finder: the hall's map button draws the Sky Chart
 * with a named marker per structure found; the hall's search starts one kind an in-game hour and searches again each
 * week, and keeps what it found through a save and load; the calendar's Coming days list a staged festival, wedding and
 * caravan with their days; a damaged Observatory, or Wonders off, pauses both powers.
 */
public class ObservatoryPowerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation OBSERVATORY = AliveWorkplace.id("observatory");
	private static final ResourceLocation VILLAGES = ResourceLocation.withDefaultNamespace("villages");
	private static final ResourceLocation PORTALS = ResourceLocation.withDefaultNamespace("ruined_portals");
	private static final ResourceLocation STRONGHOLDS = ResourceLocation.withDefaultNamespace("strongholds");
	private static final List<ResourceLocation> KINDS = List.of(VILLAGES, PORTALS, STRONGHOLDS);

	/** A stand-in for the world: these starts, each of its kind, reported only for the square asked about. */
	record Stub(List<SkyChart.Found> starts) implements SkyChart.Finder {
		@Override
		public void find(ServerLevel level, ResourceLocation kind, ChunkPos min, int chunks, java.util.function.BiConsumer<ResourceLocation, BlockPos> found) {
			for (SkyChart.Found f : starts) {
				int cx = f.pos().getX() >> 4;
				int cz = f.pos().getZ() >> 4;
				if (f.kind().equals(kind) && cx >= min.x && cz >= min.z && cx < min.x + chunks && cz < min.z + chunks) {
					found.accept(f.structure(), f.pos());
				}
			}
		}
	}

	private static Stub stub(BlockPos hall) {
		return new Stub(List.of(
			new SkyChart.Found(VILLAGES, ResourceLocation.withDefaultNamespace("village_plains"), hall.offset(800, 0, -600)),
			new SkyChart.Found(PORTALS, ResourceLocation.withDefaultNamespace("ruined_portal"), hall.offset(-1500, 0, 200)),
			new SkyChart.Found(STRONGHOLDS, ResourceLocation.withDefaultNamespace("stronghold"), hall.offset(2000, -40, 2500))));
	}

	/** A hall with a finished, standing Observatory, the stub finder in place of the world's. */
	private static BlockPos observatory(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(2, 2, 2));
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().containsKey(OBSERVATORY), "the Observatory isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(OBSERVATORY, hall, null, "", 1);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), hall.offset(40, 0, 40), Rotation.NONE, Mirror.NONE), hall.offset(40, 0, 39));
		manager.finish(site.id(), Chronicle.day(level));
		standing(level, site.id(), true);
		ObservatoryPowers.finder = stub(hall);
		ObservatoryPowers.kindsForTest = KINDS;
		UUID uuid = site.id();
		Leftovers.after(helper, () -> {
			manager.remove(uuid);
			Wonders.ENABLED = true;
			Wonders.forgetPowers();
			ObservatoryPowers.finder = SkyChart.VANILLA;
			ObservatoryPowers.kindsForTest = null;
		});
		return hall;
	}

	private static void standing(ServerLevel level, UUID id, boolean whole) {
		WonderManager.get(level).setKept(id, new WonderSite.Kept(whole ? 100 : 70, Chronicle.day(level) + 10_000, !whole, 0));
		Wonders.forgetPowers();
	}

	private static UUID site(ServerLevel level, BlockPos hall) {
		return WonderManager.get(level).in(hall).get(0).id();
	}

	/** Runs the hall's search on game time {@code from} to {@code to}, every step tick; returns the regions searched. */
	private static int search(GameTestHelper helper, BlockPos hall, long from, long to) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		int before = entity.skyChart().steps();
		for (long t = from; t < to; t += SkyChart.STEP_TICKS) {
			ObservatoryPowers.tick(helper.getLevel(), hall, entity, t, true);
		}
		return entity.skyChart().steps() - before;
	}

	private static List<ItemStack> filledMaps(ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Items.FILLED_MAP)) {
				out.add(player.getInventory().getItem(i));
			}
		}
		return out;
	}

	private static boolean isChart(ItemStack stack) {
		Component name = stack.get(DataComponents.ITEM_NAME);
		return name != null && name.getContents() instanceof TranslatableContents t && t.getKey().equals("item.aliveworkplace.sky_chart");
	}

	private static long lines(ItemStack stack, String key) {
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		return lore.lines().stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)).count();
	}

	/** Presses the hall's map button with two empty maps; returns the filled maps it gave. */
	private static List<ItemStack> pressMap(GameTestHelper helper, BlockPos hall) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(Items.MAP, 2));
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.MAP, player);
		return filledMaps(player);
	}

	//$ gametest_batch AREA '"observatoryChart"'
	@GameTest(template = AREA, batch = "observatoryChart")
	public void theSkyChartNamesEveryStructureFound(GameTestHelper helper) {
		BlockPos hall = observatory(helper);
		int steps = search(helper, hall, 0, 3 * SkyChart.HOUR_TICKS + 1_000);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		helper.assertTrue(entity.skyChart().done() && steps == 3 * 144, "the search didn't walk 3 kinds of 144 regions: " + steps);
		helper.assertTrue(entity.skyChart().found().size() == 3, "found " + entity.skyChart().found());
		List<ItemStack> maps = pressMap(helper, hall);
		helper.assertTrue(maps.size() == 2, "the map button gave " + maps.size() + " maps, not the village map and the Sky Chart");
		ItemStack chart = maps.stream().filter(ObservatoryPowerGameTests::isChart).findFirst().orElse(null);
		helper.assertTrue(chart != null, "no Sky Chart among " + maps);
		MapDecorations marks = chart.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
		for (SkyChart.Found f : entity.skyChart().found()) {
			helper.assertTrue(marks.decorations().containsKey(f.key()), "no marker for " + f + " in " + marks.decorations().keySet());
		}
		helper.assertTrue(lines(chart, "item.aliveworkplace.sky_chart.found") == 3, "the tooltip doesn't name the 3 structures: " + chart.get(DataComponents.LORE));
		String lore = String.join(" / ", chart.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList());
		helper.assertTrue(lore.contains("Village Plains") || lore.contains("Plains Village"), "the village isn't named: " + lore);
		helper.assertTrue(lore.contains("Stronghold") && lore.contains("Ruined Portal"), "the stronghold and the portal aren't named: " + lore);
		helper.succeed();
	}

	/**
	 * B140: the chart a player gets from the map button is drawn, not blank, and holding it shows its markers. The land
	 * is sketched into the map's colours, carrying it in hand (the inventory tick, as every tick does) puts a marker for
	 * each structure on the map, and the packet the server sends the holder carries both the colours and the markers.
	 */
	//$ gametest_batch AREA '"observatoryChartInHand"'
	@GameTest(template = AREA, batch = "observatoryChartInHand")
	public void theSkyChartInHandIsDrawnAndMarked(GameTestHelper helper) {
		BlockPos hall = observatory(helper);
		search(helper, hall, 0, 3 * SkyChart.HOUR_TICKS + 1_000);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		int found = entity.skyChart().found().size();
		helper.assertTrue(found == 3, "found " + entity.skyChart().found());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(Items.MAP, 2));
		VillageHallScreen.forTest(player, hall).press(VillageHallScreen.MAP, player);
		ItemStack chart = filledMaps(player).stream().filter(ObservatoryPowerGameTests::isChart).findFirst().orElse(null);
		helper.assertTrue(chart != null, "no Sky Chart from the map button");
		MapItemSavedData data = MapItem.getSavedData(chart, helper.getLevel());
		helper.assertTrue(data != null, "the Sky Chart has no map data");
		int drawn = 0;
		for (byte colour : data.colors) {
			if (colour != 0) {
				drawn++;
			}
		}
		helper.assertTrue(drawn > SkyChart.PIXELS * SkyChart.PIXELS / 2, "the Sky Chart is blank: " + drawn + " of 16384 pixels drawn");

		player.setItemInHand(InteractionHand.MAIN_HAND, chart);
		chart.inventoryTick(helper.getLevel(), player, player.getInventory().selected, true);
		List<MapDecoration> marks = new ArrayList<>();
		data.getDecorations().forEach(marks::add);
		helper.assertTrue(marks.size() >= found, "holding the Sky Chart shows " + marks.size() + " markers, not the " + found + " structures found");

		MapId id = chart.get(DataComponents.MAP_ID);
		helper.assertTrue(data.getUpdatePacket(id, player) instanceof ClientboundMapItemDataPacket packet
			&& packet.colorPatch().map(p -> p.width() == SkyChart.PIXELS && p.height() == SkyChart.PIXELS).orElse(false)
			&& packet.decorations().map(d -> d.size() >= found).orElse(false),
			"the packet to the player holding the Sky Chart doesn't carry the whole land and the " + found + " markers");
		helper.succeed();
	}

	//$ gametest_batch AREA '"observatorySearch"'
	@GameTest(template = AREA, batch = "observatorySearch")
	public void theSearchKeepsToOneKindAnHourAndComesBackWeekly(GameTestHelper helper) {
		BlockPos hall = observatory(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		int first = search(helper, hall, 0, SkyChart.HOUR_TICKS);
		helper.assertTrue(first == 144, "the first hour searched " + first + " regions, not one kind's 144");
		int none = 0;
		for (long t = SkyChart.HOUR_TICKS; t < SkyChart.HOUR_TICKS + 200; t += SkyChart.STEP_TICKS) {
			none += ObservatoryPowers.tick(level, hall, entity, t, false) ? 1 : 0;
		}
		helper.assertTrue(none == 0, "searched " + none + " regions while the tick had no room");
		int second = search(helper, hall, SkyChart.HOUR_TICKS + 200, 2 * SkyChart.HOUR_TICKS);
		helper.assertTrue(second == 144, "the second hour searched " + second + " regions");
		search(helper, hall, 2 * SkyChart.HOUR_TICKS, 4 * SkyChart.HOUR_TICKS);
		helper.assertTrue(entity.skyChart().done() && entity.skyChart().found().size() == 3, "the week's search isn't done: " + entity.skyChart().found());
		// save and load: what was found and the week stay
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		VillageHallBlockEntity loaded = new VillageHallBlockEntity(hall, level.getBlockState(hall));
		loaded.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(loaded.skyChart().found().equals(entity.skyChart().found()) && loaded.skyChart().week() == entity.skyChart().week() && loaded.skyChart().done(),
			"the search wasn't kept through a save and load: " + loaded.skyChart().found());
		// an old hall with no search saved reads as none begun
		VillageHallBlockEntity old = new VillageHallBlockEntity(hall, level.getBlockState(hall));
		CompoundTag without = tag.copy();
		without.remove("skyChart");
		old.loadWithComponents(without, level.registryAccess());
		helper.assertTrue(old.skyChart().found().isEmpty() && old.skyChart().week() == -1, "a hall from before 35.18 doesn't load with an empty search");
		// a week on: the search begins again, keeping what it found until each region is searched again
		long dayTime = level.getDayTime();
		level.setDayTime(dayTime + ObservatoryPowers.WEEK_DAYS * 24_000L);
		try {
			int again = search(helper, hall, 10 * SkyChart.HOUR_TICKS, 10 * SkyChart.HOUR_TICKS + SkyChart.HOUR_TICKS);
			helper.assertTrue(again == 144 && !entity.skyChart().done() && entity.skyChart().found().size() == 3,
				"a new week's search didn't begin (" + again + " regions, found " + entity.skyChart().found().size() + ")");
		} finally {
			level.setDayTime(dayTime);
		}
		helper.succeed();
	}

	//$ gametest_batch AREA '"observatoryComing"'
	@GameTest(template = AREA, batch = "observatoryComing")
	public void theComingDaysListAFestivalAWeddingAndACaravan(GameTestHelper helper) {
		BlockPos hall = observatory(helper);
		// festivals, couples, market days and raids are off in GameTest runs (the config): on for this test only
		boolean festivals = Festivals.ENABLED;
		boolean couples = Couples.ENABLED;
		boolean markets = MarketDays.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		Festivals.ENABLED = true;
		Couples.ENABLED = true;
		MarketDays.ENABLED = true;
		VillageRaids.ENABLED = true;
		try {
			comingDays(helper, hall);
		} finally {
			Festivals.ENABLED = festivals;
			Couples.ENABLED = couples;
			MarketDays.ENABLED = markets;
			VillageRaids.ENABLED = raids;
		}
		helper.succeed();
	}

	private static void comingDays(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		long today = Chronicle.day(level);
		entity.setFestivalDay(today + 3);
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		a.setCustomName(Component.literal("Ada"));
		b.setCustomName(Component.literal("Bram"));
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), today - 1, false));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), today - 1, false));
		BlockPos far = hall.offset(900, 0, 0);
		Caravans.Shipment caravan = Caravans.sendGranary(level, far, hall, List.of(new ItemStack(Items.BREAD, 4)));
		long arrives = today + (level.getDayTime() % 24_000L + caravan.arrives() - level.getGameTime()) / 24_000L;
		List<ObservatoryPowers.Coming> coming = ObservatoryPowers.comingDays(level, hall);
		helper.assertTrue(coming.stream().anyMatch(c -> key(c.what()).equals("screen.aliveworkplace.coming.festival") && c.day() == today + 3),
			"no festival on day " + (today + 3) + ": " + describe(coming));
		helper.assertTrue(coming.stream().filter(c -> key(c.what()).equals("screen.aliveworkplace.coming.wedding")).count() == 1
				&& coming.stream().anyMatch(c -> key(c.what()).equals("screen.aliveworkplace.coming.wedding") && c.day() == today + 1 && c.what().getString().contains("Ada")),
			"Ada and Bram's wedding isn't due tomorrow, once: " + describe(coming));
		helper.assertTrue(coming.stream().anyMatch(c -> key(c.what()).equals("screen.aliveworkplace.coming.caravan_in") && c.day() == arrives && c.where().isPresent()),
			"no caravan arriving on day " + arrives + ": " + describe(coming));
		helper.assertTrue(coming.stream().anyMatch(c -> key(c.what()).equals("screen.aliveworkplace.coming.full_moon") && c.day() >= today && c.day() < today + 8),
			"no full moon within the week: " + describe(coming));
		// on the hall's calendar page, under the seasons
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		HallPages.Page calendar = HallPages.all().stream().filter(p -> p.id().equals(Seasons.PAGE)).findFirst().orElseThrow();
		VillageHallScreen.renderPage(menu, level, hall, calendar, player);
		helper.assertFalse(menu.icon(ObservatoryPowers.HEADER).isEmpty(), "the calendar page has no Coming days");
		int shown = 0;
		for (int slot = ObservatoryPowers.FIRST; slot < ChoiceMenu.SIZE; slot++) {
			shown += menu.icon(slot).isEmpty() ? 0 : 1;
		}
		helper.assertTrue(shown == Math.min(coming.size(), ObservatoryPowers.SHOWN), "the page shows " + shown + " of " + coming.size() + " coming days");
		helper.assertTrue(coming.stream().anyMatch(c -> key(c.what()).equals("screen.aliveworkplace.coming.raid_risk") && c.day() == -1), "no raid risk for tonight: " + describe(coming));
	}

	//$ gametest_batch AREA '"observatoryPaused"'
	@GameTest(template = AREA, batch = "observatoryPaused")
	public void aDamagedObservatoryOrWondersOffPausesItsPowers(GameTestHelper helper) {
		BlockPos hall = observatory(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(ObservatoryPowers.charts(level, hall) && ObservatoryPowers.foretells(level, hall), "the standing Observatory has no powers");
		helper.assertTrue(ObservatoryPowers.chartSize(level, hall).orElse(0) == SkyChart.DEFAULT_SIZE, "the chart isn't 6,144 blocks");
		helper.assertFalse(WonderPowers.DECLARED.contains(SkyChartPower.TYPE) || WonderPowers.DECLARED.contains(ForecastPower.TYPE), "still declared");
		for (int round = 0; round < 2; round++) {
			if (round == 0) {
				standing(level, site(level, hall), false);
			} else {
				standing(level, site(level, hall), true);
				Wonders.ENABLED = false;
			}
			String why = round == 0 ? "damaged" : "with Wonders off";
			helper.assertFalse(ObservatoryPowers.charts(level, hall) || ObservatoryPowers.foretells(level, hall), "the powers stay on " + why);
			helper.assertTrue(search(helper, hall, 0, SkyChart.HOUR_TICKS) == 0, "the search went on " + why);
			List<ItemStack> maps = pressMap(helper, hall);
			helper.assertTrue(maps.size() == 1 && !isChart(maps.get(0)), "the map button drew a Sky Chart " + why);
			helper.assertTrue(ObservatoryPowers.comingDays(level, hall).isEmpty(), "the Coming days are shown " + why);
		}
		Wonders.ENABLED = true;
		Wonders.forgetPowers();
		helper.assertTrue(search(helper, hall, 0, SkyChart.HOUR_TICKS) == 144 && entity.skyChart().found().size() == 1, "the search didn't resume once repaired");
		helper.succeed();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static String describe(List<ObservatoryPowers.Coming> coming) {
		return String.join(", ", coming.stream().map(c -> key(c.what()) + "@" + c.day()).toList());
	}
}
