package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The tester's spec tests for older village features (0.131.0-0.134.0) where the mutation spot-check showed a line no
 * test would notice going wrong: the Village Map's water, the bandits who raid, and the config's feature switches.
 */
public class HallSpecGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * The Village Map draws "the land as it is that day" the way a vanilla map does: shallow water light, deep water
	 * darker (vanilla shades water by its depth).
	 */
	//$ gametest_ticks_batch AREA '100' '"theVillageMapShadesWaterByDepth"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theVillageMapShadesWaterByDepth")
	public void theVillageMapShadesWaterByDepth(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hallAt = new BlockPos(11, 2, 11);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos shallow = new BlockPos(4, 1, 4);
		helper.setBlock(shallow, Blocks.WATER); // one deep, on the floor
		BlockPos deep = new BlockPos(8, 6, 4);
		for (int y = 1; y <= 6; y++) { // six deep, in a glass tank
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(7, y, 3), new BlockPos(9, y, 5))) {
				helper.setBlock(p, Blocks.GLASS);
			}
			helper.setBlock(new BlockPos(8, y, 4), Blocks.WATER);
		}
		BlockPos hall = helper.absolutePos(hallAt);
		ItemStack map = VillageMaps.map(level, hall);
		MapItemSavedData data = level.getMapData(map.get(DataComponents.MAP_ID));
		helper.assertTrue(data != null, "no map data");
		int[] light = pixel(data, hall, helper.absolutePos(shallow));
		int[] dark = pixel(data, hall, helper.absolutePos(deep));
		helper.assertTrue(light[0] == MapColor.WATER.id && dark[0] == MapColor.WATER.id, "not drawn as water: " + light[0] + ", " + dark[0]);
		helper.assertTrue(light[1] == MapColor.Brightness.HIGH.id, "shallow water isn't light: brightness " + light[1]);
		helper.assertTrue(dark[1] != MapColor.Brightness.HIGH.id, "six-deep water is as light as a puddle: brightness " + dark[1]);
		helper.succeed();
	}

	/** The map colour id and brightness id drawn for the column at {@code pos}. */
	private static int[] pixel(MapItemSavedData data, BlockPos hall, BlockPos pos) {
		int px = pos.getX() - hall.getX() + 64;
		int pz = pos.getZ() - hall.getZ() + 64;
		int packed = data.colors[px + pz * 128] & 0xFF;
		return new int[] {packed >> 2, packed & 3};
	}

	/** While a bandit camp stands, the village's night raiders are its bandits (0.131.0): named Bandit, of the band. */
	//$ gametest_ticks_batch AREA '100' '"banditsRaidAsBandits"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "banditsRaidAsBandits")
	public void banditsRaidAsBandits(GameTestHelper helper) {
		List<Mob> raiders = banditRaid(helper);
		for (Mob m : raiders) {
			helper.assertTrue(m.getCustomName() != null && m.getCustomName().getString().equals("Bandit"), "a raider called " + m.getCustomName());
			helper.assertTrue(m.getTags().contains(BanditCamps.TAG), "a raider who isn't one of the bandits: " + m.getTags());
		}
		raiders.forEach(Mob::discard);
		helper.succeed();
	}

	/** A village with a bandit camp by it and a night raid started: the raiders (called on the test's first tick). */
	static List<Mob> banditRaid(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageRaids.forget();
			BanditCamps.forget(level);
		});
		BlockPos hallAt = new BlockPos(1, 2, 1);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(new BlockPos(12, 1, 12))) != null, "no camp");
		helper.assertTrue(VillageRaids.start(level, hall, 8, 0) != null, "no raid");
		List<Mob> raiders = VillageRaids.raiders(level, hall);
		helper.assertFalse(raiders.isEmpty(), "no raiders");
		return raiders;
	}

	/**
	 * Outside gametests the village features are on by default and each config switch turns its own one off (the
	 * gametest server turns most of them off for the tests; this looks at what players get).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"theConfigsSwitchesOutsideTests"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "theConfigsSwitchesOutsideTests")
	public void theConfigsSwitchesOutsideTests(GameTestHelper helper) {
		String property = System.getProperty("fabric-api.gametest");
		try {
			System.clearProperty("fabric-api.gametest");
			new WorkplaceConfig().apply();
			String on = flags();
			helper.assertTrue(!on.contains("false"), "a feature is off by default for players: " + on);
			WorkplaceConfig.parse("{\"villagerNames\": false, \"villagerTraits\": false, \"villagerSickness\": false, \"villagerMoods\": false,"
				+ " \"builderRepairs\": false, \"marketDays\": false, \"villageRaids\": false, \"banditCamps\": false, \"festivals\": false,"
				+ " \"villagerChatter\": false, \"villagerCouples\": false, \"villageTreasury\": false, \"villageProtection\": false,"
				+ " \"builderPaths\": false}").apply();
			String off = flags();
			helper.assertTrue(!off.contains("true"), "a feature stays on with its switch off: " + off);
		} finally {
			if (property != null) {
				System.setProperty("fabric-api.gametest", property);
			}
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	private static String flags() {
		return "names=" + io.github.jcondedata.aliveworkplace.people.Names.ENABLED
			+ " traits=" + io.github.jcondedata.aliveworkplace.people.Traits.ENABLED
			+ " sickness=" + io.github.jcondedata.aliveworkplace.people.Sickness.ENABLED
			+ " moods=" + io.github.jcondedata.aliveworkplace.people.Moods.ENABLED
			+ " repairs=" + io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED
			+ " markets=" + io.github.jcondedata.aliveworkplace.hall.MarketDays.ENABLED
			+ " raids=" + VillageRaids.ENABLED
			+ " bandits=" + BanditCamps.ENABLED
			+ " festivals=" + io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED
			+ " chatter=" + io.github.jcondedata.aliveworkplace.people.Chatter.ENABLED
			+ " couples=" + io.github.jcondedata.aliveworkplace.people.Couples.ENABLED
			+ " treasury=" + io.github.jcondedata.aliveworkplace.hall.Treasury.ENABLED
			+ " protection=" + io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED
			+ " paths=" + io.github.jcondedata.aliveworkplace.build.Paths.ENABLED;
	}
}
