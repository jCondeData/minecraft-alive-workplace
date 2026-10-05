package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.HarvestIdols;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=harvest_idol (ROADMAP 30.14): two wheat fields side by side through a harvest season (autumn on the village
 * calendar), one with a Harvest Idol at its edge and the other out of its 32 blocks' reach. Random ticks are sped up for
 * the time-lapse. A close look at the idol first (golden sparkles rise from it in harvest season), then the two fields from
 * above: the idol's field ripens first. Its checks: it's harvest season, the idol reaches the near field and not the far
 * one, and half-way the near field has grown more stages than the far one.
 */
final class HarvestIdolScene {
	private static final int Y = -60;
	/** The idol, at the west edge of the near field. */
	private static final BlockPos IDOL = new BlockPos(-2, Y, 4);
	/** The fields' north-west corners, 9 x 9 each with water in the middle; the far one starts 34 blocks from the idol. */
	private static final BlockPos NEAR = new BlockPos(0, Y, 0);
	private static final BlockPos FAR = new BlockPos(32, Y, 0);
	private static final int SIZE = 9;
	/** Random ticks a section a tick during the lapse (vanilla: 3): a stage every 80 ticks or so in a full field. */
	private static final int SPEED = 300;
	private static final int LAPSE = 200;
	private static final int HALF = LAPSE + 250;
	private static final int END = LAPSE + 520;
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
				level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server); // no growing until the lapse
				// Noon on the first day of autumn: harvest season.
				level.setDayTime((2L * Seasons.DAYS) * VillageNeeds.DAY + 6000);
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick == 120) {
			ScreenshotHarness.shot(mc, "01_harvest_idol");
		}
		if (tick == LAPSE - 20) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ScreenshotHarness.hoverLookingAt(player, new Vec3(19, Y + 16, -16), new Vec3(19, Y, 5));
			});
		}
		if (tick == LAPSE) {
			ScreenshotHarness.shot(mc, "02_harvest_idol_fields");
			server.execute(() -> server.overworld().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(SPEED, server));
		}
		if (tick == HALF) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				int near = stages(level, NEAR);
				int far = stages(level, FAR);
				Showcase.check(near > far, "half-way the field with the idol has grown more: " + near + " stages, the other " + far);
			});
			ScreenshotHarness.shot(mc, "03_harvest_idol_growing");
		}
		if (tick == END) {
			server.execute(() -> server.overworld().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(3, server));
			ScreenshotHarness.shot(mc, "04_harvest_idol_ripe");
		}
		if (tick == END + 20) {
			mc.stop();
		}
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		field(level, NEAR);
		field(level, FAR);
		level.setBlockAndUpdate(IDOL, ModBlocks.HARVEST_IDOL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
		Showcase.check(HarvestIdols.harvestSeason(level), "it's harvest season: " + Seasons.seasonLine(Seasons.today(level)).getString());
		Showcase.check(HarvestIdols.idols(level).contains(IDOL), "the placed idol is in its dimension's set");
		Showcase.check(HarvestIdols.near(level, NEAR.offset(SIZE - 1, 0, SIZE - 1)) && !HarvestIdols.near(level, FAR),
			"the idol reaches the whole near field and none of the far one");
		ScreenshotHarness.hoverLookingAt(player, new Vec3(-2.5, Y + 1.6, 0.5), new Vec3(-1.5, Y + 1.2, 4.5));
	}

	/** A 9 x 9 wheat field on wet farmland, a water source in the middle. */
	private static void field(ServerLevel level, BlockPos corner) {
		for (int dx = 0; dx < SIZE; dx++) {
			for (int dz = 0; dz < SIZE; dz++) {
				BlockPos p = corner.offset(dx, 0, dz);
				if (dx == SIZE / 2 && dz == SIZE / 2) {
					level.setBlockAndUpdate(p.below(), Blocks.WATER.defaultBlockState());
					continue;
				}
				level.setBlockAndUpdate(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
				level.setBlockAndUpdate(p, ((CropBlock) Blocks.WHEAT).getStateForAge(0));
			}
		}
	}

	/** The wheat stages grown in the field at {@code corner}. */
	private static int stages(ServerLevel level, BlockPos corner) {
		CropBlock wheat = (CropBlock) Blocks.WHEAT;
		int total = 0;
		for (int dx = 0; dx < SIZE; dx++) {
			for (int dz = 0; dz < SIZE; dz++) {
				var state = level.getBlockState(corner.offset(dx, 0, dz));
				if (state.is(Blocks.WHEAT)) {
					total += wheat.getAge(state);
				}
			}
		}
		return total;
	}
}
