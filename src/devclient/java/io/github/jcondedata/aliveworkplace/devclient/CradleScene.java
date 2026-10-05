package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.CradleSeat;
import io.github.jcondedata.aliveworkplace.hall.Cradles;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=cradle (ROADMAP 30.12): a nursery village (a hall, a bed and a Cradle beside it). At night the hall's round puts
 * the child of the house to sleep in the cradle, seated in it; then, by day, a time-lapse of a newborn growing up: each
 * step is a hall round of {@link VillageNeeds#CHECK_EVERY} ticks with the ticks between rounds added, so 20 steps are the
 * 12000 ticks a child takes in a nursery village. Its checks: the child seated in the cradle at night, still a child half-way
 * and grown up after 20 steps (the hall's own rounds meanwhile only make it sooner; the GameTests time it exactly).
 */
final class CradleScene {
	private static final BlockPos HALL = new BlockPos(6, -60, 4);
	private static final BlockPos BED_HEAD = new BlockPos(0, -60, 0);
	private static final BlockPos CRADLE = new BlockPos(2, -60, 0);
	private static final int LAPSE = 200;
	private static final int STEP = 15;
	private static final int STEPS = 20;
	private int tick;
	private Villager sleeper;
	private Villager growing;

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
				level.setDayTime(18000);
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick == 60) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Cradles.round(level, HALL, 0);
				Showcase.check(sleeper.getVehicle() instanceof CradleSeat, "the child is asleep in the cradle at night");
			});
		}
		if (tick == 120) {
			ScreenshotHarness.shot(mc, "01_cradle_night");
		}
		if (tick == LAPSE - 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.setDayTime(1000); // morning: the sleeper gets up, the time-lapse starts
				growing = baby(level, new Vec3(3.5, -60, 2.5));
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ScreenshotHarness.hoverLookingAt(player, new Vec3(5.5, -58.3, 5.5), new Vec3(3.0, -59.5, 2.0));
			});
		}
		if (tick == LAPSE) {
			ScreenshotHarness.shot(mc, "02_cradle_newborn");
		}
		if (tick > LAPSE && tick <= LAPSE + STEP * STEPS && (tick - LAPSE) % STEP == 0) {
			int step = (tick - LAPSE) / STEP;
			server.execute(() -> {
				ServerLevel level = server.overworld();
				int round = VillageNeeds.CHECK_EVERY;
				growing.setAge(Math.min(0, growing.getAge() + round));
				Cradles.round(level, HALL, round);
				if (step == STEPS / 2) {
					Showcase.check(growing.isBaby(), "still a child half-way, after 6000 ticks");
				}
				if (step == STEPS) {
					Showcase.check(!growing.isBaby(), "grown up after 12000 ticks in a nursery village (24000 without a cradle)");
				}
			});
			if (step == STEPS / 2) {
				ScreenshotHarness.shot(mc, "03_cradle_growing");
			}
		}
		if (tick == LAPSE + STEP * STEPS + 30) {
			ScreenshotHarness.shot(mc, "04_cradle_grown");
		}
		if (tick == LAPSE + STEP * STEPS + 50) {
			mc.stop();
		}
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.WEST));
		level.setBlockAndUpdate(BED_HEAD, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		level.setBlockAndUpdate(BED_HEAD.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		level.setBlockAndUpdate(CRADLE, ModBlocks.CRADLE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
		level.setBlockAndUpdate(new BlockPos(4, -60, 0), Blocks.LANTERN.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(-1, -60, 2), Blocks.LANTERN.defaultBlockState());
		sleeper = baby(level, new Vec3(1.5, -60, 2.5));
		Showcase.check(Cradles.nursery(level, HALL), "the cradle beside the bed makes a nursery village");
		ScreenshotHarness.hoverLookingAt(player, new Vec3(4.5, -58.4, 3.0), new Vec3(2.5, -59.6, 0.5));
	}

	private static Villager baby(ServerLevel level, Vec3 at) {
		Villager v = EntityType.VILLAGER.create(level);
		v.moveTo(at.x, at.y, at.z, 180f, 0f);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.COMMAND, null);
		v.setAge(-24000);
		v.setNoAi(true);
		level.addFreshEntity(v);
		return v;
	}
}
