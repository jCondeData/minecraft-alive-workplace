package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.JobPlate;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=job_plate (B221): looking at a working villager shows its job and level over its head. A Journeyman fisherman
 * with no name ("Fisherman, Journeyman"), Dara, an Expert builder with a name of her own ("Dara — Builder, Expert"), and
 * a jobless villager, who shows nothing.
 */
final class JobPlateScene {
	private static final BlockPos FISHER = new BlockPos(0, -60, 6);
	private static final BlockPos DARA = new BlockPos(3, -60, 6);
	private static final BlockPos JOBLESS = new BlockPos(6, -60, 6);
	private int tick;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(3000);
			});
		}
		if (tick == 60) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				spawn(level, FISHER, VillagerProfession.FISHERMAN, 3, null);
				spawn(level, DARA, ModVillagers.BUILDER, 4, "Dara");
				spawn(level, JOBLESS, VillagerProfession.NONE, 1, null);
				lookAt(server, FISHER);
			});
		}
		if (tick == 110) {
			expect(mc, "Fisherman, Journeyman", "a level-3 fisherman's plate");
			ScreenshotHarness.shot(mc, "01_fisherman_plate");
			server.execute(() -> lookAt(server, DARA));
		}
		if (tick == 150) {
			expect(mc, "Dara — Builder, Expert", "a named builder's plate keeps her name first");
			ScreenshotHarness.shot(mc, "02_named_plate");
			server.execute(() -> lookAt(server, JOBLESS));
		}
		if (tick == 190) {
			boolean jobless = mc.crosshairPickEntity instanceof Villager v && !JobPlate.shows(v);
			Showcase.check(jobless, "looking at a jobless villager shows no plate");
			ScreenshotHarness.shot(mc, "03_jobless_no_plate");
		}
		if (tick == 200) {
			mc.stop();
		}
	}

	private static void expect(Minecraft mc, String text, String what) {
		String seen = mc.crosshairPickEntity instanceof Villager v && JobPlate.shows(v)
			? JobPlate.plate(v, v.getDisplayName()).getString() : String.valueOf(mc.crosshairPickEntity);
		Showcase.check(seen.equals(text), what + " (" + seen + ")");
	}

	private static void spawn(ServerLevel level, BlockPos at, VillagerProfession job, int lvl, String name) {
		Villager v = EntityType.VILLAGER.spawn(level, at, MobSpawnType.COMMAND);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		v.setYRot(0); // facing south, to the camera
		v.setYHeadRot(0);
		v.setYBodyRot(0);
	}

	/** The player stands two blocks in front of the villager and looks at its chest, so it's the crosshair's target. */
	private static void lookAt(MinecraftServer server, BlockPos villager) {
		Vec3 feet = Vec3.atBottomCenterOf(villager);
		ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), feet.add(0, 0, 2.2), feet.add(0, 1.3, 0));
	}
}
