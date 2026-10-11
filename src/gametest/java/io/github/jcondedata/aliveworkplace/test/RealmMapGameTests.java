package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmMaps;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * ROADMAP 33.21, the Realm Map: drawn from the Realm tab for an empty map, a banner for every member in the realm's
 * colour where its hall is (the capital's named "... (capital)"), colonies, allies and rivals in their own colours, a
 * caravan route's dots between its two halls, a colony on the road a red cross, the legend on the tooltip; scaled so
 * every member fits; land nobody has loaded left parchment; the map survives save and reload; the switch. Realms are
 * off in GameTests, so each test turns them on alone in its batch ({@link RealmGameTests#land}).
 */
public class RealmMapGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Pacts and feuds on for the test, their records emptied before and after. */
	private static void pactsAndFeuds(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		boolean pacts = Pacts.ENABLED, alliances = Pacts.ALLIANCES, feuds = Feuds.ENABLED, map = RealmMaps.ENABLED;
		Pacts.ENABLED = true;
		Pacts.ALLIANCES = true;
		Feuds.ENABLED = true;
		RealmMaps.ENABLED = true;
		Pacts.Data.get(server).clear();
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Pacts.ALLIANCES = alliances;
			Feuds.ENABLED = feuds;
			RealmMaps.ENABLED = map;
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
		});
	}

	/** The map's saved data. */
	private static MapItemSavedData data(ServerLevel level, ItemStack map) {
		return level.getMapData(map.get(DataComponents.MAP_ID));
	}

	/** The decoration whose name reads {@code name}, or null. */
	private static MapDecoration named(MapItemSavedData data, String name) {
		for (MapDecoration d : data.getDecorations()) {
			if (d.name().map(c -> c.getString().equals(name)).orElse(false)) {
				return d;
			}
		}
		return null;
	}

	/** Whether {@code d} sits where block {@code pos} falls on {@code data}'s map (a decoration's unit is half a pixel). */
	private static boolean at(MapItemSavedData data, MapDecoration d, BlockPos pos) {
		double x = 2.0 * (pos.getX() - data.centerX) / (1 << data.scale), z = 2.0 * (pos.getZ() - data.centerZ) / (1 << data.scale);
		return Math.abs(d.x() - x) <= 1.01 && Math.abs(d.y() - z) <= 1.01;
	}

	/**
	 * Three members (the capital, two more), an ally, a rival in a feud, a colony outside the realm and one on the road,
	 * a caravan route from the capital: the map from the Realm tab's button has each banner where its hall is, in its
	 * colour, the route's dots between its halls, the cross on the colony's spot, the legend, parchment where nothing is
	 * loaded, and it all survives save and reload.
	 */
	//$ gametest_ticks_batch AREA '100' '"realmMap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmMap")
	public void realmMapShowsTheRealm(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.HAMLET,
			VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.HAMLET);
		pactsAndFeuds(helper);
		ServerLevel level = land.level();
		var server = level.getServer();
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.add(level, land.hall(0), land.at(1), land.owner()).ok(), "hall 1 didn't join");
		helper.assertTrue(Realms.add(level, land.hall(0), land.at(2), land.owner()).ok(), "hall 2 didn't join");
		RealmData.Realm realm = land.data().realmOf(land.at(0));
		land.data().putRealm(realm.withArms(Optional.of(new VillageBanners.Colours(DyeColor.BLUE, BannerPatternLayers.EMPTY))));
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(3), true, 0, -1, false, false));
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(4), land.at(1), 0));
		land.data().recordFounded(land.at(0), land.at(5), 1);
		BlockPos spot = land.hall(0).offset(-60, 0, 40);
		land.data().putOrder(new RealmData.Order(land.at(0), spot, Optional.empty(), RealmData.ON_ROAD, 0, 0, 0));
		// a far member (its land not loaded), with a caravan route from the capital
		BlockPos far = land.hall(0).offset(200, 0, 120);
		RealmData.Realm armed = land.data().realmOf(land.at(0));
		List<RealmData.Member> members = new ArrayList<>(armed.members());
		members.add(new RealmData.Member(GlobalPos.of(level.dimension(), far), 0));
		land.data().putRealm(armed.withMembers(members));
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), far), "no route");
		Leftovers.after(helper, () -> caravans.toggleRoute(land.hall(0), far));

		// the player's way: an empty map in the inventory, the Realm tab's map button
		land.owner().getInventory().add(new ItemStack(Items.MAP));
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 1, land.owner());
		ItemStack button = menu.icon(RealmMaps.BUTTON);
		helper.assertTrue(button.is(Items.FILLED_MAP) && button.getHoverName().getString().equals("Draw the Realm Map"), "no map button: " + button);
		menu.press(RealmMaps.BUTTON, land.owner());
		ItemStack map = ItemStack.EMPTY;
		boolean emptyLeft = false;
		for (int i = 0; i < land.owner().getInventory().getContainerSize(); i++) {
			ItemStack s = land.owner().getInventory().getItem(i);
			map = s.is(Items.FILLED_MAP) ? s : map;
			emptyLeft |= s.is(Items.MAP);
		}
		helper.assertTrue(!map.isEmpty() && !emptyLeft, "the empty map wasn't drawn on");
		String title = Realms.title(server, land.data().realmOf(land.at(0))).getString();
		helper.assertTrue(map.getHoverName().getString().equals("Map of " + title), "the map's name: " + map.getHoverName().getString());
		MapItemSavedData data = data(level, map);
		helper.assertTrue(data.locked && data.scale == RealmMaps.MIN_SCALE, "scale " + data.scale + ", locked " + data.locked);

		// the banners: where the halls are, in their colours
		List<MapBanner> banners = new ArrayList<>(data.getBanners());
		helper.assertTrue(banners.size() == 7, "banners: " + banners);
		MapDecoration farMark = named(data, Realms.name(server, GlobalPos.of(level.dimension(), far)).getString());
		helper.assertTrue(farMark != null && at(data, farMark, far), "the far member's mark: " + farMark);
		String[] names = {land.name(0) + " (capital)", land.name(1), land.name(2), land.name(3), land.name(4), land.name(5)};
		DyeColor[] colours = {DyeColor.BLUE, DyeColor.BLUE, DyeColor.BLUE, RealmMaps.ALLY, RealmMaps.RIVAL, RealmMaps.COLONY};
		for (int i = 0; i < 6; i++) {
			int n = i;
			MapBanner banner = banners.stream().filter(b -> b.pos().equals(land.hall(n))).findFirst().orElse(null);
			helper.assertTrue(banner != null && banner.color() == colours[i] && banner.name().map(c -> c.getString().equals(names[n])).orElse(false),
				"hall " + i + "'s banner: " + banner);
			MapDecoration d = named(data, names[i]);
			helper.assertTrue(d != null && at(data, d, land.hall(i)), "hall " + i + "'s mark on the map: " + d);
		}

		// the route's dots lie between its two halls
		int ax = RealmMaps.pixel(land.hall(0).getX(), data.centerX, data.scale), az = RealmMaps.pixel(land.hall(0).getZ(), data.centerZ, data.scale);
		int bx = RealmMaps.pixel(far.getX(), data.centerX, data.scale), bz = RealmMaps.pixel(far.getZ(), data.centerZ, data.scale);
		int dots = 0;
		for (int i = 0; i < data.colors.length; i++) {
			if (data.colors[i] != RealmMaps.ROUTE) {
				continue;
			}
			dots++;
			int px = i % 128, pz = i / 128;
			double len2 = (double) (bx - ax) * (bx - ax) + (double) (bz - az) * (bz - az);
			double t = ((px - ax) * (double) (bx - ax) + (pz - az) * (double) (bz - az)) / len2;
			double qx = ax + t * (bx - ax) - px, qz = az + t * (bz - az) - pz;
			helper.assertTrue(t > 0 && t < 1 && qx * qx + qz * qz <= 1.0, "a dot off the route at " + px + "," + pz);
		}
		helper.assertTrue(dots >= 10, "too few route dots (" + dots + ") dots between " + ax + "," + az + " and " + bx + "," + bz);

		// the colony on the road: a red cross on its spot
		MapDecorations crosses = map.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
		helper.assertTrue(crosses.decorations().values().stream().anyMatch(e -> e.type().equals(MapDecorationTypes.RED_X)
			&& e.x() == spot.getX() && e.z() == spot.getZ()), "no cross on the colony's spot: " + crosses);

		// the legend
		List<String> lore = RealmGameTests.lore(map);
		helper.assertTrue(lore.contains("Blue: the realm's villages (4)") && lore.contains("Light Blue: allies (1)") && lore.contains("Red: rivals (1)")
			&& lore.contains("Lime: colonies outside the realm (1)") && lore.contains("Dotted lines: caravan routes (1)")
			&& lore.contains("Red crosses: colonies on the road (1)"), "the legend: " + lore);

		// parchment where the server has nothing loaded, land where it has
		int land0 = 0;
		for (int i = 0; i < data.colors.length; i++) {
			int x = data.centerX - (64 << data.scale) + (i % 128) * (1 << data.scale);
			int z = data.centerZ - (64 << data.scale) + (i / 128) * (1 << data.scale);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				helper.assertTrue(data.colors[i] == 0 || data.colors[i] == RealmMaps.ROUTE, "unloaded land drawn at " + x + "," + z);
			} else if (data.colors[i] != 0) {
				land0++;
			}
		}
		helper.assertTrue(land0 > 0, "no loaded land drawn");

		// save and reload: the map's data and the stack's cross
		MapItemSavedData reloaded = MapItemSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(reloaded.getBanners().size() == 7 && java.util.Arrays.equals(reloaded.colors, data.colors) && reloaded.scale == data.scale,
			"the reloaded map differs");
		Tag saved = map.save(level.registryAccess());
		ItemStack again = ItemStack.parse(level.registryAccess(), saved).orElseThrow();
		helper.assertTrue(again.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY).decorations().size() == 1
			&& again.get(DataComponents.MAP_ID).equals(map.get(DataComponents.MAP_ID)), "the reloaded stack lost its cross or map");
		helper.succeed();
	}

	/** Members far apart: the map scales out until they all fit (2 to 4), and the scale's choice by distance. */
	//$ gametest_ticks_batch AREA '100' '"realmMapScale"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmMapScale")
	public void realmMapScalesToFitEveryMember(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN);
		pactsAndFeuds(helper);
		ServerLevel level = land.level();
		GlobalPos far = GlobalPos.of(level.dimension(), land.hall(0).offset(900, 0, -300));
		land.data().putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), land.at(0),
			List.of(new RealmData.Member(land.at(0), 0), new RealmData.Member(far, 0)), Optional.empty(), "barony", 0));
		ItemStack map = RealmMaps.map(level, land.hall(0));
		MapItemSavedData data = data(level, map);
		helper.assertTrue(data.scale == 3, "scale " + data.scale + " for members 900 blocks apart");
		helper.assertTrue(data.getBanners().size() == 2 && named(data, land.name(0) + " (capital)") != null
			&& named(data, Realms.name(level.getServer(), far).getString()) != null, "both members on the map");
		MapDecoration farMark = named(data, Realms.name(level.getServer(), far).getString());
		helper.assertTrue(at(data, farMark, far.pos()), "the far member's mark: " + farMark);
		BlockPos c = BlockPos.ZERO;
		helper.assertTrue(RealmMaps.scaleFor(c, List.of(c.offset(100, 0, -100))) == 2, "a 100-block realm isn't scale 2");
		helper.assertTrue(RealmMaps.scaleFor(c, List.of(c.offset(-400, 0, 0))) == 3, "a 400-block realm isn't scale 3");
		helper.assertTrue(RealmMaps.scaleFor(c, List.of(c.offset(0, 0, 900))) == 4, "a 900-block realm isn't scale 4");
		helper.assertTrue(RealmMaps.scaleFor(c, List.of(c.offset(5000, 0, 0))) == 4, "the scale goes past 4");
		helper.succeed();
	}

	/** The switch: off, the Realm tab has no map button and nothing is drawn; outside a realm nothing is drawn either. */
	//$ gametest_ticks_batch AREA '100' '"realmMapOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmMapOff")
	public void realmMapSwitchOff(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE);
		pactsAndFeuds(helper);
		land.owner().getInventory().add(new ItemStack(Items.MAP));
		helper.assertTrue(!RealmMaps.draw(land.level(), land.hall(0), land.owner()).ok(), "a map drawn outside a realm");
		RealmGameTests.found(helper, land);
		RealmMaps.ENABLED = false;
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 0, land.owner());
		helper.assertTrue(!menu.icon(RealmMaps.BUTTON).is(Items.FILLED_MAP), "a map button with the switch off");
		helper.assertTrue(!RealmMaps.draw(land.level(), land.hall(0), land.owner()).ok(), "a map drawn with the switch off");
		helper.assertTrue(land.owner().getInventory().countItem(Items.MAP) == 1 && land.owner().getInventory().countItem(Items.FILLED_MAP) == 0,
			"the empty map was used");
		helper.succeed();
	}
}
