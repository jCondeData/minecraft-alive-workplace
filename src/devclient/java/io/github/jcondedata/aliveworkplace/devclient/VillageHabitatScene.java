package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.habitat.VillageHabitats;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=village_habitat (ROADMAP 28.14, Cobblemon 1.8.1): a finished Habitat Garden by a Village Hall and an Expert
 * Habitat Keeper at its pasture. She founds the village's own Habitat Block in place of the mossy centre stone (it
 * still looks like moss); the scene checks the block is natural, has the biome's pool and that the hall's line names
 * today's Pokémon.
 */
final class VillageHabitatScene {
	private static final BlockPos ORIGIN = new BlockPos(0, -60, 0);
	/** The garden's Pasture Block (template spot), where the keeper works. */
	private static final BlockPos PASTURE = new BlockPos(14, 1, 7);
	private static final int GIVE_UP = 1200;

	private int tick;
	private int foundAt = -1;
	private volatile String line = "";
	private volatile boolean founded;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		BlockPos centre = ORIGIN.offset(StarterBlueprints.HABITAT_GARDEN_CENTRE);
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(3000);
				level.getStructureManager().get(StarterBlueprints.HABITAT_GARDEN.id()).orElseThrow()
					.placeInWorld(level, ORIGIN, ORIGIN, new StructurePlaceSettings(), level.getRandom(), 2);
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE);
				BuildSiteManager.get(level).recordFinished(StarterBlueprints.HABITAT_GARDEN.id(), placement, UUID.randomUUID());
				level.setBlockAndUpdate(ORIGIN.offset(-4, 0, 7), ModBlocks.VILLAGE_HALL.defaultBlockState());
			});
			look(server, false);
		}
		if (tick == 60) {
			ScreenshotHarness.shot(mc, "01_garden");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Villager keeper = EntityType.VILLAGER.create(level);
				keeper.moveTo(Vec3.atBottomCenterOf(ORIGIN.offset(PASTURE).offset(-1, 0, 1)));
				level.addFreshEntity(keeper);
				Jobs.employ(level, keeper, ORIGIN.offset(PASTURE), ModVillagers.PASTURE_POI, ModVillagers.HABITAT_KEEPER);
				keeper.setVillagerData(keeper.getVillagerData().setLevel(VillageHabitats.EXPERT));
			});
		}
		if (tick > 60 && tick % 10 == 0 && foundAt < 0) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				if (VillageHabitats.isNatural(level.getBlockState(centre))) {
					founded = true;
					line = VillageHabitats.todayLine(level, centre).getString();
				}
			});
			if (founded) {
				foundAt = tick;
			}
		}
		if (foundAt > 0 && tick == foundAt + 40) {
			ScreenshotHarness.shot(mc, "02_habitat");
			look(server, true);
		}
		if (foundAt > 0 && tick == foundAt + 80) {
			ScreenshotHarness.shot(mc, "03_hall_line");
			boolean ok = line.contains("today:") && !line.contains("cobblemon.species");
			Showcase.check(ok, ok ? "the keeper founded the village habitat: " + line : "founded, but the hall line reads " + line);
			mc.stop();
		}
		if (foundAt < 0 && tick == GIVE_UP) {
			Showcase.check(false, "no Habitat Block under the centre stone after " + GIVE_UP + " ticks");
			mc.stop();
		}
	}

	/** The camera on the garden, or close on its centre stone. */
	private static void look(MinecraftServer server, boolean close) {
		Vec3 centre = Vec3.atCenterOf(ORIGIN.offset(StarterBlueprints.HABITAT_GARDEN_CENTRE));
		Vec3 eye = close ? centre.add(3, 3, -4) : centre.add(9, 9, -14);
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), eye, centre));
	}
}
