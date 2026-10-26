package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.wonder.GardensPowers;
import io.github.jcondedata.aliveworkplace.wonder.LibraryPowers;
import io.github.jcondedata.aliveworkplace.wonder.LighthousePowers;
import io.github.jcondedata.aliveworkplace.wonder.ObservatoryPowers;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.BeautyPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.MoodPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.TravellersPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.UndertakersPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WeddingsPower;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * QA for the Wonders' powers (ROADMAP 35.10, 35.12, 35.14, 35.18, 35.20, 35.22), written from the items' text: each
 * power reaches exactly as far as the spec says (the Gardens' harvest and the Colossus's mood across the realm, one
 * caravan route either way and no further; everything else its own village only), the Lighthouse's range ends at
 * 4,096 blocks to the block, the powers stop when the Wonder is damaged or Wonders are off, and come back from a save.
 */
public class QaWonderPowersGameTests implements FabricGameTest {
	private static WonderSite finished(GameTestHelper helper, String wonder, BlockPos hall) {
		WonderManager manager = WonderManager.get(helper.getLevel());
		WonderSite site = manager.begin(AliveWorkplace.id(wonder), hall, UUID.randomUUID(), "Jesse", 5);
		manager.finish(site.id(), 9);
		Wonders.forgetPowers();
		Leftovers.after(helper, () -> {
			manager.remove(site.id());
			Wonders.forgetPowers();
		});
		return manager.site(site.id()).orElseThrow();
	}

	private static void damage(ServerLevel level, WonderSite site, boolean damaged) {
		WonderManager.get(level).setKept(site.id(), new WonderSite.Kept(damaged ? 70 : 100, 10, damaged, 9));
		Wonders.forgetPowers();
	}

	/** Opens a caravan route from {@code from} to {@code to}, closed again after the test. */
	private static void route(GameTestHelper helper, BlockPos from, BlockPos to) {
		Caravans.Data data = Caravans.Data.get(helper.getLevel());
		helper.assertTrue(data.toggleRoute(from, to, 10), "the route " + from + " → " + to + " didn't open");
		Leftovers.after(helper, () -> {
			if (data.routesFrom(from).contains(to)) {
				data.toggleRoute(from, to, 10);
			}
		});
	}

	private static boolean has(ServerLevel level, BlockPos hall, String type) {
		return Wonders.power(level, hall, type).isPresent();
	}

	/**
	 * The Colossus's mood reaches its partner village (a route either way, here from the partner to it) but not that
	 * partner's own partner; its travellers and beauty stay at home; damaged or with Wonders off, nothing anywhere.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theColossusReachesOneRouteAwayAndNoFurther(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = new BlockPos(-27100, 70, 27100);
		BlockPos partner = home.offset(600, 0, 0);
		BlockPos beyond = home.offset(1200, 0, 0);
		route(helper, partner, home); // the partner's route to the Colossus's village: either way counts
		route(helper, partner, beyond);
		WonderSite site = finished(helper, "colossus", home);
		List<Wonders.Granted> there = Wonders.powers(level, partner, MoodPower.TYPE);
		helper.assertTrue(there.size() == 1 && !there.get(0).home() && ((MoodPower) there.get(0).power()).points(false) == 8,
			"the partner's mood grant: " + there);
		helper.assertTrue(((MoodPower) Wonders.power(level, home, MoodPower.TYPE).orElseThrow()).points(true) == 12, "the home village's 12");
		helper.assertTrue(Wonders.powers(level, beyond, MoodPower.TYPE).isEmpty(), "two routes away was reached: " + Wonders.powers(level, beyond, MoodPower.TYPE));
		helper.assertTrue(!has(level, partner, TravellersPower.TYPE) && has(level, home, TravellersPower.TYPE),
			"the travellers: at home " + has(level, home, TravellersPower.TYPE) + ", in the partner " + has(level, partner, TravellersPower.TYPE));
		helper.assertTrue(BeautyPower.beauty(level, partner) == 0 && BeautyPower.beauty(level, home) == 8,
			"beauty at home " + BeautyPower.beauty(level, home) + ", in the partner " + BeautyPower.beauty(level, partner));
		damage(level, site, true);
		helper.assertTrue(Wonders.powers(level, partner, MoodPower.TYPE).isEmpty() && !has(level, home, MoodPower.TYPE)
			&& BeautyPower.beauty(level, home) == 0, "a damaged Colossus still gives");
		damage(level, site, false);
		boolean on = Wonders.ENABLED;
		try {
			Wonders.ENABLED = false;
			helper.assertTrue(Wonders.powers(level, partner, MoodPower.TYPE).isEmpty() && !has(level, home, TravellersPower.TYPE), "Wonders off still gives");
		} finally {
			Wonders.ENABLED = on;
		}
		helper.assertTrue(Wonders.powers(level, partner, MoodPower.TYPE).size() == 1, "repaired, the partner's mood didn't come back");
		helper.succeed();
	}

	/**
	 * The Hanging Gardens: the harvest reaches the partner village, not two routes off; the births (its own village, as
	 * the wiki has it) don't reach the partner: 16 meals and one a day there, 12 and two at home; damaged, neither.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGardensHarvestReachesThePartnerButItsBirthsStayHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = new BlockPos(-27100, 70, 28400);
		BlockPos partner = home.offset(0, 0, 700);
		BlockPos beyond = home.offset(0, 0, 1400);
		route(helper, home, partner);
		route(helper, beyond, partner);
		WonderSite site = finished(helper, "hanging_gardens", home);
		helper.assertTrue(has(level, home, GardensPowers.HARVEST) && has(level, partner, GardensPowers.HARVEST), "the harvest at home and in the partner");
		helper.assertTrue(!has(level, beyond, GardensPowers.HARVEST), "two routes away got the harvest");
		helper.assertTrue(GardensPowers.mealsNeeded(level, home, 16) == 12 && GardensPowers.birthsPerDay(level, home, 1) == 2,
			"home: " + GardensPowers.mealsNeeded(level, home, 16) + " meals, " + GardensPowers.birthsPerDay(level, home, 1) + " a day");
		helper.assertTrue(GardensPowers.mealsNeeded(level, partner, 16) == 16 && GardensPowers.birthsPerDay(level, partner, 1) == 1,
			"the partner's births changed: " + GardensPowers.mealsNeeded(level, partner, 16) + " meals");
		// never fewer than the usual a day
		helper.assertTrue(GardensPowers.birthsPerDay(level, home, 3) == 3, "the Gardens lowered a usual 3 a day");
		damage(level, site, true);
		helper.assertTrue(!has(level, partner, GardensPowers.HARVEST) && GardensPowers.mealsNeeded(level, home, 16) == 16
			&& GardensPowers.birthsPerDay(level, home, 1) == 1, "a damaged Garden still gives");
		helper.succeed();
	}

	/**
	 * The Great Cathedral's powers are its own village's: a partner village's Undertakers get no village-wide search
	 * and its weddings no Cathedral venue; damaged, the home village loses them too.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCathedralsPowersStayInItsVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = new BlockPos(-25700, 70, 27100);
		BlockPos partner = home.offset(500, 0, 0);
		route(helper, home, partner);
		route(helper, partner, home);
		WonderSite site = finished(helper, "great_cathedral", home);
		helper.assertTrue(has(level, home, UndertakersPower.TYPE) && WeddingsPower.granted(level, home).isPresent()
			&& BeautyPower.beauty(level, home) == 8, "the Cathedral's powers at home");
		helper.assertTrue(!has(level, partner, UndertakersPower.TYPE) && WeddingsPower.granted(level, partner).isEmpty()
			&& WeddingsPower.venue(level, partner).isEmpty() && BeautyPower.beauty(level, partner) == 0, "a partner village got the Cathedral's powers");
		damage(level, site, true);
		helper.assertTrue(!has(level, home, UndertakersPower.TYPE) && WeddingsPower.granted(level, home).isEmpty()
			&& WeddingsPower.venue(level, home).isEmpty() && BeautyPower.beauty(level, home) == 0, "a damaged Cathedral still gives");
		helper.succeed();
	}

	/**
	 * The Great Library and the Observatory are their own village's: a caravan partner doesn't get the second research
	 * tier, the Sky Chart or the Coming days; damaged, neither does the home village; repaired, back.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theLibraryAndObservatoryStayInTheirVillages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = new BlockPos(-25700, 70, 28400);
		BlockPos partner = home.offset(0, 0, 500);
		route(helper, home, partner);
		WonderSite library = finished(helper, "great_library", home);
		WonderSite observatory = finished(helper, "observatory", home);
		helper.assertTrue(LibraryPowers.stands(level, home) && ObservatoryPowers.charts(level, home) && ObservatoryPowers.foretells(level, home),
			"the Library and Observatory at home");
		helper.assertTrue(ObservatoryPowers.chartSize(level, home).orElse(0) == 6144, "the chart's size: " + ObservatoryPowers.chartSize(level, home));
		helper.assertTrue(!LibraryPowers.stands(level, partner) && !ObservatoryPowers.charts(level, partner) && !ObservatoryPowers.foretells(level, partner),
			"a partner village got the Library's tier or the Observatory's chart");
		damage(level, library, true);
		helper.assertTrue(!LibraryPowers.stands(level, home) && ObservatoryPowers.charts(level, home), "the Library damaged: " + LibraryPowers.stands(level, home)
			+ ", the Observatory " + ObservatoryPowers.charts(level, home));
		damage(level, observatory, true);
		helper.assertTrue(!ObservatoryPowers.charts(level, home) && !ObservatoryPowers.foretells(level, home), "a damaged Observatory still charts");
		damage(level, library, false);
		damage(level, observatory, false);
		helper.assertTrue(LibraryPowers.stands(level, home) && ObservatoryPowers.charts(level, home), "repaired, not back");
		helper.succeed();
	}

	/**
	 * The Lighthouse's range to the block: 4,096 blocks off is on the trade routes list and 4,097 isn't; without it
	 * 2,048 is and 2,049 isn't. Its powers are its village's: a partner village's caravans neither reach further nor go
	 * faster.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theLighthousesRangeEndsAt4096ToTheBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(-29900, 70, -29900);
		BlockPos[] off = {hall.offset(2048, 0, 0), hall.offset(0, 0, 2049), hall.offset(0, 0, 4096), hall.offset(4097, 0, 0)};
		BlockPos partner = hall.offset(-300, 0, 0);
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(hall, Component.literal("Thornholm"), List.of());
		for (BlockPos p : off) {
			data.setWants(p, Component.literal("Far " + p.getX()), List.of());
		}
		Leftovers.after(helper, () -> {
			data.remove(hall);
			for (BlockPos p : off) {
				data.remove(p);
			}
		});
		route(helper, hall, partner);
		java.util.function.Predicate<BlockPos> listed = p -> Caravans.neighbours(level, hall).stream().anyMatch(v -> v.hall().equals(p));
		helper.assertTrue(listed.test(off[0]) && !listed.test(off[1]) && !listed.test(off[2]), "without the Lighthouse: 2,048 "
			+ listed.test(off[0]) + ", 2,049 " + listed.test(off[1]) + ", 4,096 " + listed.test(off[2]));
		WonderSite site = finished(helper, "lighthouse", hall);
		helper.assertTrue(listed.test(off[1]) && listed.test(off[2]) && !listed.test(off[3]), "with the Lighthouse: 2,049 " + listed.test(off[1])
			+ ", 4,096 " + listed.test(off[2]) + ", 4,097 " + listed.test(off[3]));
		helper.assertTrue(LighthousePowers.caravanRange(level, partner) == Caravans.RANGE && LighthousePowers.caravanTrip(level, partner, 1000) == 1000,
			"the partner's caravans: range " + LighthousePowers.caravanRange(level, partner) + ", trip " + LighthousePowers.caravanTrip(level, partner, 1000));
		helper.assertTrue(LighthousePowers.caravanTrip(level, hall, 1000) == 500, "home trip " + LighthousePowers.caravanTrip(level, hall, 1000));
		damage(level, site, true);
		helper.assertTrue(!listed.test(off[1]) && LighthousePowers.caravanTrip(level, hall, 1000) == 1000, "a damaged Lighthouse's range or speed");
		helper.succeed();
	}

	/**
	 * After a save and reload (the manager read back from its own save, as after a restart) the Gardens' realm harvest and
	 * the Lighthouse's speed come from what was loaded, and a Lighthouse saved damaged loads powerless.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePowersComeBackFromASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		BlockPos gardensHall = new BlockPos(-28400, 70, -29900);
		BlockPos partner = gardensHall.offset(400, 0, 0);
		BlockPos lighthouseHall = gardensHall.offset(0, 0, 900);
		route(helper, partner, gardensHall);
		WonderManager manager = WonderManager.get(level);
		finished(helper, "hanging_gardens", gardensHall);
		WonderSite lighthouse = finished(helper, "lighthouse", lighthouseHall);
		try {
			CompoundTag saved = manager.save(new CompoundTag(), registries);
			WonderManager back = WonderManager.load(saved, registries);
			level.getDataStorage().set(WonderManager.NAME, back);
			Wonders.forgetPowers();
			helper.assertTrue(has(level, partner, GardensPowers.HARVEST) && LighthousePowers.caravanTrip(level, lighthouseHall, 1000) == 500,
				"after the reload: harvest " + has(level, partner, GardensPowers.HARVEST) + ", trip " + LighthousePowers.caravanTrip(level, lighthouseHall, 1000));
			back.setKept(lighthouse.id(), new WonderSite.Kept(70, 10, true, 9));
			WonderManager again = WonderManager.load(back.save(new CompoundTag(), registries), registries);
			level.getDataStorage().set(WonderManager.NAME, again);
			Wonders.forgetPowers();
			helper.assertTrue(LighthousePowers.caravanTrip(level, lighthouseHall, 1000) == 1000 && LighthousePowers.caravanRange(level, lighthouseHall) == Caravans.RANGE,
				"a Lighthouse saved damaged loaded with its powers");
			helper.assertTrue(has(level, partner, GardensPowers.HARVEST), "the Gardens lost their harvest in the second reload");
		} finally {
			level.getDataStorage().set(WonderManager.NAME, manager);
			Wonders.forgetPowers();
		}
		helper.succeed();
	}
	/**
	 * Halved fares only at the Lighthouse's village's posts: a post at the village's edge (VillageHalls.RADIUS from the
	 * hall) pays half, one a block beyond it the full fare, and so does a post in its caravan partner's village; both
	 * ways round; damaged, full fare everywhere.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void halfFaresEndAtTheVillagesEdge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.MinecraftServer server = level.getServer();
		BlockPos hall = new BlockPos(-29900, 70, -26900);
		BlockPos partner = hall.offset(0, 0, 1200);
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(hall, Component.literal("Thornholm"), List.of());
		data.setWants(partner, Component.literal("Pinevale"), List.of());
		Leftovers.after(helper, () -> {
			data.remove(hall);
			data.remove(partner);
		});
		route(helper, hall, partner);
		int r = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		net.minecraft.core.GlobalPos edge = net.minecraft.core.GlobalPos.of(level.dimension(), hall.offset(r, 0, 0));
		net.minecraft.core.GlobalPos outside = net.minecraft.core.GlobalPos.of(level.dimension(), hall.offset(-r - 1, 0, 0));
		net.minecraft.core.GlobalPos partnerPost = net.minecraft.core.GlobalPos.of(level.dimension(), partner.offset(3, 0, 0));
		net.minecraft.core.GlobalPos away = net.minecraft.core.GlobalPos.of(level.dimension(), hall.offset(0, 0, -2500));
		UUID rider = UUID.randomUUID();
		WonderSite site = finished(helper, "lighthouse", hall);
		int full = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(edge, away);
		int edgeFare = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(server, edge, away, rider);
		int backFare = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(server, away, edge, rider);
		helper.assertTrue(full >= 8 && edgeFare == Math.round(full * 0.5f) && backFare == edgeFare,
			"a post at the village's edge: " + edgeFare + " and back " + backFare + " of " + full);
		int outFull = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(outside, away);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(server, outside, away, rider) == outFull,
			"a post a block outside the village was halved");
		int partnerFull = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(partnerPost, away);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(server, partnerPost, away, rider) == partnerFull
			&& io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(server, away, partnerPost, rider) == partnerFull,
			"a post in the partner village was halved");
		damage(level, site, true);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.travel.TravelNetwork.fare(server, edge, away, rider) == full, "a damaged Lighthouse halved the fare");
		helper.succeed();
	}
}
