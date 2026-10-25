package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-d-1025-0408): ROADMAP 32.19, from the spec. "Each with its job's block and no other job block", checked against
 * every profession the game knows (the mod's own and vanilla's, by their job sites), not a list. "What next? suggests each
 * when the village lacks its job and its disaster can come there", at the boundaries: the Fire Station when a village is
 * wooden enough for a fire (the fire's wood share of 0.3) and not just under it, nor with a Firewarden; the Physician's
 * House not for a village one rank under a town, and still for a city; a job held only outside the village doesn't count.
 */
public class QaAnsweringHousesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	/**
	 * Every block of each house that is the job site of some profession: exactly one, and it is the house's own job's
	 * (the Fire Station's Firewarden, the Well House's Well Keeper, the Physician's House's Physician).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void eachHouseHasOneJobSiteAndItIsItsOwnJobs(GameTestHelper helper) {
		Map<StarterBlueprints.Entry, VillagerProfession> houses = Map.of(StarterBlueprints.FIRE_STATION, ModVillagers.FIREWARDEN,
			StarterBlueprints.WELL_HOUSE, ModVillagers.WELL_KEEPER, StarterBlueprints.PHYSICIANS_HOUSE, ModVillagers.PHYSICIAN);
		List<VillagerProfession> jobs = BuiltInRegistries.VILLAGER_PROFESSION.stream()
			.filter(p -> p != VillagerProfession.NONE && p != VillagerProfession.NITWIT).toList();
		for (Map.Entry<StarterBlueprints.Entry, VillagerProfession> h : houses.entrySet()) {
			Blueprint b = BlueprintLibrary.get(helper.getLevel(), h.getKey().id()).orElseThrow();
			List<String> sites = new ArrayList<>();
			boolean own = false;
			for (Blueprint.Entry e : b.blocks()) {
				Optional<Holder<PoiType>> poi = PoiTypes.forState(e.state());
				if (poi.isEmpty()) {
					continue;
				}
				List<VillagerProfession> takers = jobs.stream().filter(p -> p.acquirableJobSite().test(poi.get())).toList();
				if (takers.isEmpty()) {
					continue;
				}
				sites.add(BuiltInRegistries.BLOCK.getKey(e.state().getBlock()) + " at " + e.pos().toShortString());
				own |= takers.contains(h.getValue());
			}
			helper.assertTrue(sites.size() == 1, h.getKey().id() + " has " + sites.size() + " job sites: " + sites);
			helper.assertTrue(own, h.getKey().id() + "'s job site isn't the " + h.getValue() + "'s: " + sites);
		}
		helper.succeed();
	}

	// --- What next? at the boundaries ----------------------------------------------------------------------------

	/** The finished build the wood share is read round (helper position), and the hall beside it, off the sampling grid. */
	private static final BlockPos BUILD = new BlockPos(14, 2, 14);
	private static final BlockPos HALL = new BlockPos(15, 2, 16);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10;
		BlockPos hall = helper.absolutePos(HALL);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(BUILD), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Disasters.DISABLED = Set.of();
			Disasters.forget();
			sites.forgetFinished(placement);
			new WorkplaceConfig().apply();
		});
		sites.recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), placement, UUID.randomUUID());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.HAMLET);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 17));
		farmer.setNoAi(true);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		return new Village(level, hall, entity);
	}

	/**
	 * Builds ten blocks on the points the wood share samples round the finished build ({@code wood} of them planks, the
	 * rest stone) and forgets the day's cached share, so the village's share is {@code wood} in ten.
	 */
	private static void woodShare(GameTestHelper helper, int wood) {
		List<BlockPos> points = new ArrayList<>();
		for (int dx = -12; dx <= 12; dx += 3) {
			points.add(BUILD.offset(dx, 0, 0));
		}
		points.add(BUILD.offset(0, 0, -3));
		for (int i = 0; i < points.size(); i++) {
			helper.setBlock(points.get(i), i < wood ? Blocks.OAK_PLANKS : Blocks.STONE);
		}
		Disasters.forget();
	}

	private static List<String> houses(ServerLevel level, BlockPos hall) {
		return Disasters.houseTips(level, hall, VillageHalls.census(level, hall)).stream().map(t -> key(t.args()[1])).toList();
	}

	private static String key(Object arg) {
		return arg instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(arg);
	}

	/**
	 * The fire can come to a village whose builds are at least 0.3 wood: at 3 in 10 What next? suggests the Fire Station,
	 * at 2 in 10 it doesn't; a Firewarden in the village stops it even at 10 in 10, and one outside the village doesn't.
	 */
	//$ gametest_batch HUGE_AREA '"qaFireStationTip"'
	@GameTest(template = HUGE_AREA, batch = "qaFireStationTip")
	public void theFireStationIsSuggestedOnlyForAWoodenVillageWithoutAFirewarden(GameTestHelper helper) {
		Village v = village(helper);
		String fire = "blueprint.aliveworkplace.fire_station";
		woodShare(helper, 2);
		helper.assertTrue(Disasters.woodShare(v.level(), v.hall()) < 0.3, "the share is " + Disasters.woodShare(v.level(), v.hall()));
		helper.assertFalse(houses(v.level(), v.hall()).contains(fire), "the Fire Station for a village of stone (share "
			+ Disasters.woodShare(v.level(), v.hall()) + "): " + houses(v.level(), v.hall()));
		woodShare(helper, 3);
		helper.assertTrue(Math.abs(Disasters.woodShare(v.level(), v.hall()) - 0.3) < 1e-9, "the share is " + Disasters.woodShare(v.level(), v.hall()));
		helper.assertTrue(houses(v.level(), v.hall()).contains(fire), "no Fire Station at a wood share of 0.3: " + houses(v.level(), v.hall()));
		woodShare(helper, 10);
		// A Firewarden far outside the village (the hall's reach is 10 here) isn't the village's.
		Villager away = helper.spawn(EntityType.VILLAGER, new BlockPos(28, 2, 2));
		away.setNoAi(true);
		away.setVillagerData(away.getVillagerData().setProfession(ModVillagers.FIREWARDEN));
		helper.assertTrue(houses(v.level(), v.hall()).contains(fire), "no Fire Station tip with the only Firewarden outside the village: " + houses(v.level(), v.hall()));
		Villager warden = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 17));
		warden.setNoAi(true);
		warden.setVillagerData(warden.getVillagerData().setProfession(ModVillagers.FIREWARDEN));
		helper.assertFalse(houses(v.level(), v.hall()).contains(fire), "still told to build a Fire Station with a Firewarden: " + houses(v.level(), v.hall()));
		warden.discard();
		Disasters.DISABLED = Set.of("village_fire");
		helper.assertFalse(houses(v.level(), v.hall()).contains(fire), "the Fire Station with fires switched off");
		helper.succeed();
	}

	/** The plague comes to a town or bigger: no Physician's House for a village (one rank under), one for a town and a city. */
	//$ gametest_batch HUGE_AREA '"qaPhysicianTip"'
	@GameTest(template = HUGE_AREA, batch = "qaPhysicianTip")
	public void thePhysiciansHouseIsSuggestedFromATownUp(GameTestHelper helper) {
		Village v = village(helper);
		String physician = "blueprint.aliveworkplace.physicians_house";
		v.entity().setRank(VillageRanks.Rank.VILLAGE);
		helper.assertFalse(houses(v.level(), v.hall()).contains(physician), "the Physician's House for a village: " + houses(v.level(), v.hall()));
		v.entity().setRank(VillageRanks.Rank.TOWN);
		helper.assertTrue(houses(v.level(), v.hall()).contains(physician), "no Physician's House for a town: " + houses(v.level(), v.hall()));
		v.entity().setRank(VillageRanks.Rank.CITY);
		helper.assertTrue(houses(v.level(), v.hall()).contains(physician), "no Physician's House for a city: " + houses(v.level(), v.hall()));
		// The Well House (drought can come anywhere) is suggested at every rank.
		for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
			v.entity().setRank(rank);
			helper.assertTrue(houses(v.level(), v.hall()).contains("blueprint.aliveworkplace.well_house"), "no Well House for a " + rank + ": " + houses(v.level(), v.hall()));
		}
		helper.succeed();
	}
}
