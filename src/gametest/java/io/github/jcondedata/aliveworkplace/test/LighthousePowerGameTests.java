package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.guard.GuardRally;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.wonder.LighthousePowers;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The Lighthouse's powers (ROADMAP 35.12): a hall 3,000 blocks off is on the trade routes list only with it; a caravan
 * from its village takes half as long; a fare to or from its Travel Posts is halved; its jetty's post joins the network
 * as "<Village> Lighthouse"; the day before a pirate raid the village is told (and not for a land raid, or without it),
 * the chronicle has it and at dusk the guards gather on the shore; a damaged Lighthouse turns them all off.
 */
public class LighthousePowerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation LIGHTHOUSE = AliveWorkplace.id("lighthouse");
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");
	/** Day 41 begins here: far from any other test's days. */
	private static final long BASE = 40 * VillageNeeds.DAY;

	private static WonderSite lighthouse(GameTestHelper helper, BlockPos hall) {
		WonderManager manager = WonderManager.get(helper.getLevel());
		WonderSite site = manager.begin(LIGHTHOUSE, hall, UUID.randomUUID(), "Jesse", 3);
		manager.finish(site.id(), 7);
		Leftovers.after(helper, () -> {
			manager.remove(site.id());
			Wonders.forgetPowers();
		});
		return manager.site(site.id()).orElseThrow();
	}

	private static void damage(ServerLevel level, WonderSite site, boolean damaged) {
		WonderManager.get(level).setKept(site.id(), new WonderSite.Kept(damaged ? 70 : 100, 9, damaged, 7));
	}

	/** Caravans and ferries, with the Lighthouse, without it, and with it damaged. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theLighthouseSendsCaravansFurtherAndHalvesFares(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(-21000, 70, 21000);
		BlockPos far = hall.offset(3000, 0, 0);
		BlockPos near = hall.offset(1000, 0, 0);
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(hall, Component.literal("Thornholm"), List.of());
		data.setWants(far, Component.literal("Farport"), List.of());
		data.setWants(near, Component.literal("Nearby"), List.of());
		Leftovers.after(helper, () -> List.of(hall, far, near).forEach(data::remove));
		GlobalPos post = GlobalPos.of(level.dimension(), hall.offset(10, 0, 5));
		GlobalPos away = GlobalPos.of(level.dimension(), hall.offset(-2000, 0, 0));
		UUID rider = UUID.randomUUID();
		// without it
		helper.assertTrue(Caravans.neighbours(level, hall).stream().noneMatch(v -> v.hall().equals(far))
			&& Caravans.neighbours(level, hall).stream().anyMatch(v -> v.hall().equals(near)), "3,000 blocks off on the list without the Lighthouse");
		long plain = Caravans.trip(level, hall, far, data);
		int fare = TravelNetwork.fare(level.getServer(), post, away, rider);
		helper.assertTrue(plain == 1500 && fare == 8, "plain trip " + plain + ", fare " + fare);
		// with it
		WonderSite site = lighthouse(helper, hall);
		helper.assertTrue(Caravans.neighbours(level, hall).stream().anyMatch(v -> v.hall().equals(far)), "3,000 blocks off not on the list with the Lighthouse");
		helper.assertTrue(LighthousePowers.caravanRange(level, hall) == 2 * Caravans.RANGE, "range " + LighthousePowers.caravanRange(level, hall));
		helper.assertTrue(Caravans.trip(level, hall, far, data) == plain / 2, "the trip: " + Caravans.trip(level, hall, far, data) + " of " + plain);
		helper.assertTrue(Caravans.trip(level, far, hall, data) == plain, "a caravan from elsewhere went faster");
		helper.assertTrue(TravelNetwork.fare(level.getServer(), post, away, rider) == 4 && TravelNetwork.fare(level.getServer(), away, post, rider) == 4,
			"the fare: " + TravelNetwork.fare(level.getServer(), post, away, rider));
		GlobalPos short1 = GlobalPos.of(level.dimension(), hall.offset(20, 0, 0));
		helper.assertTrue(TravelNetwork.fare(level.getServer(), post, short1, rider) == 1, "less than an emerald");
		// damaged: all off
		damage(level, site, true);
		helper.assertTrue(Caravans.neighbours(level, hall).stream().noneMatch(v -> v.hall().equals(far)), "a damaged Lighthouse's range");
		helper.assertTrue(Caravans.trip(level, hall, far, data) == plain, "a damaged Lighthouse's speed");
		helper.assertTrue(TravelNetwork.fare(level.getServer(), post, away, rider) == 8, "a damaged Lighthouse's fares");
		damage(level, site, false);
		helper.assertTrue(Caravans.trip(level, hall, far, data) == plain / 2, "repaired, the speed didn't come back");
		helper.succeed();
	}

	/** The jetty's Travel Post joins the network as "<Village> Lighthouse" once the Lighthouse is finished. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theJettysPostJoinsTheNetwork(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 40, 0));
		WonderManager manager = WonderManager.get(level);
		WonderSite begun = lighthouse(helper, hall);
		manager.place(begun.id(), new BlueprintData.Placement(Ids.of(level.dimension()), hall.offset(8, 0, 8), Rotation.NONE, Mirror.NONE), hall.offset(8, 0, 7));
		WonderSite site = manager.site(begun.id()).orElseThrow();
		BlockPos post = WonderUpkeep.blocks(level, site).stream().filter(b -> b.state().is(ModBlocks.TRAVEL_POST)).map(WonderUpkeep.Block::pos)
			.findFirst().orElse(null);
		helper.assertTrue(post != null, "no Travel Post in the Lighthouse's drawing");
		level.getChunk(post.getX() >> 4, post.getZ() >> 4);
		TravelNetwork network = TravelNetwork.get(level.getServer());
		GlobalPos where = GlobalPos.of(level.dimension(), post);
		Leftovers.after(helper, () -> {
			level.setBlockAndUpdate(post, Blocks.AIR.defaultBlockState());
			network.remove(where);
		});
		helper.assertTrue(LighthousePowers.jetty(level, site) == null, "a post that isn't built joined");
		level.setBlockAndUpdate(post, ModBlocks.TRAVEL_POST.defaultBlockState());
		damage(level, site, true);
		helper.assertTrue(LighthousePowers.jetty(level, manager.site(site.id()).orElseThrow()) == null, "a damaged Lighthouse named its post");
		damage(level, site, false);
		TravelNetwork.Post joined = LighthousePowers.jetty(level, manager.site(site.id()).orElseThrow());
		String village = VillageHalls.name(level, hall).getString();
		helper.assertTrue(joined != null && joined.name().equals(village + " Lighthouse") && network.at(where) != null
			&& network.at(where).name().equals(village + " Lighthouse"), "the jetty's post: " + (joined == null ? null : joined.name()));
		helper.succeed();
	}

	/**
	 * The early warning: a pirate raid the clock set at dusk is told that day, a day ahead (the players in the village,
	 * in the chronicle), not without the Lighthouse, not for a raid by land, not with it damaged; at dusk on the day the
	 * guards gather on the shore.
	 */
	//$ gametest_ticks_batch AREA '200' '"lighthouseWarning"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "lighthouseWarning")
	public void theLighthouseWarnsOfPiratesADayAhead(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true;
		BlockPos hallRel = new BlockPos(15, 2, 15);
		BlockPos hall = helper.absolutePos(hallRel);
		Leftovers.after(helper, () -> {
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		// the sea: a strip of water by the hall
		for (int x = 2; x <= 28; x++) {
			for (int z = 24; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
			}
		}
		List<Component> heard = new ArrayList<>();
		WonderFinishedGameTests.listener(helper, heard, "Keeper").teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
		BlockPos postRel = new BlockPos(15, 2, 8);
		helper.setBlock(postRel, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 6));
		Jobs.employ(level, guard, helper.absolutePos(postRel), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Culture land = Threats.all().stream().filter(c -> c.arrival() != Culture.Arrival.SHORE).findFirst().orElseThrow();
		helper.assertTrue(Threats.get(PIRATES).map(c -> c.arrival() == Culture.Arrival.SHORE).orElse(false), "no drowned pirates");
		ThreatData data = ThreatData.get(level);
		level.setDayTime(BASE + 12100); // dusk of day 41: the clock sets tomorrow night's
		ThreatData.Attack pirates = new ThreatData.Attack(42, PIRATES, Math.PI / 2, 13600);
		// without the Lighthouse: not told
		data.forgetClock(hall);
		data.rolled(hall, 41, pirates);
		helper.assertFalse(Warnings.warn(level, hall, 10, 1), "told of pirates without a Lighthouse");
		WonderSite site = lighthouse(helper, hall);
		// a raid by land: the Lighthouse watches the sea only
		data.forgetClock(hall);
		data.rolled(hall, 41, new ThreatData.Attack(42, land.id(), Math.PI / 2, 13600));
		helper.assertFalse(Warnings.warn(level, hall, 10, 1), "the Lighthouse told of a raid by land (" + land.id() + ")");
		// damaged: off
		data.forgetClock(hall);
		data.rolled(hall, 41, pirates);
		damage(level, site, true);
		helper.assertFalse(Warnings.warn(level, hall, 10, 1), "a damaged Lighthouse told of pirates");
		damage(level, site, false);
		helper.assertTrue(heard.stream().noneMatch(c -> c.getString().startsWith("Sails off")), "told too soon: " + heard);
		// pirates, with the Lighthouse standing: told at dusk the day before
		helper.assertTrue(Warnings.warn(level, hall, 10, 1), "not told of the pirates");
		String village = VillageHalls.name(level, hall).getString();
		helper.assertTrue(heard.stream().anyMatch(c -> c.getString().equals("Sails off the south shore: Drowned Pirates will strike " + village + " tomorrow night.")
			|| c.getString().startsWith("Sails off the south shore: ") && c.getString().endsWith(" will strike " + village + " tomorrow night.")), "the warning: " + heard);
		List<String> chronicle = entity.chronicle().stream().map(e -> e.text().getString()).toList();
		helper.assertTrue(chronicle.stream().anyMatch(l -> l.startsWith("The keeper of " + village + " Lighthouse saw them coming: ") && l.endsWith(" from the south")),
			"the chronicle: " + chronicle);
		for (Component c : heard) {
			helper.assertTrue(!c.getString().contains("aliveworkplace."), "untranslated: " + c.getString());
		}
		// before dusk on the day nobody gathers; at dusk the guard goes to the shore
		level.setDayTime(BASE + VillageNeeds.DAY + 7000);
		helper.assertTrue(LighthousePowers.watch(level, hall) == 0 && GuardRally.rallyPoint(guard).isEmpty(), "gathered before dusk");
		level.setDayTime(BASE + VillageNeeds.DAY + 12100);
		helper.assertTrue(LighthousePowers.watch(level, hall) == 1, "the guard didn't gather at dusk");
		BlockPos spot = GuardRally.rallyPoint(guard).orElseThrow();
		boolean byWater = false;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				byWater |= level.getFluidState(spot.offset(dx, -1, dz)).is(net.minecraft.tags.FluidTags.WATER);
			}
		}
		helper.assertTrue(byWater, "the guard's spot isn't on the shore: " + spot);
		helper.succeed();
	}
}
