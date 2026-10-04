package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanGround;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=city_plan_ground (ROADMAP 27.4): a plains village with a planned Village Hall (zones, a street, a lane and a
 * closed wall line drawn through the plan screen's packets); the player walks it holding the City Plan, so the borders
 * show on the ground; then the hall's map of the plan hangs in an item frame beside the hall.
 */
final class CityPlanGroundScene {
	private int tick;
	private volatile BlockPos hall;
	private volatile BlockPos frame;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -80 -80 80 80");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		// walking the village with the plan in hand: three steps along the street
		for (int step = 0; step < 3; step++) {
			if (tick == 400 + step * 20) {
				int s = step;
				server.execute(() -> walk(server, -6 + s * 6, 19));
			}
		}
		if (tick == 470) {
			ScreenshotHarness.shot(mc, "01_city_plan_ground");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				VillageHallBlockEntity entity = (VillageHallBlockEntity) server.overworld().getBlockEntity(hall);
				List<CityPlanGround.Dot> dots = CityPlanGround.dots(server.overworld(), hall, entity.plan(), player.blockPosition());
				long road = dots.stream().filter(d -> d.color() == CityPlanGround.ROAD_COLOR).count();
				long zones = dots.stream().filter(d -> d.color() != CityPlanGround.ROAD_COLOR && d.color() != CityPlanGround.WALL_COLOR).count();
				Showcase.check(road > 0 && zones > 0, "zone edges and the street show on the ground round the player (" + zones + " zone dots, " + road + " road dots)");
				walk(server, -22, 22);
			});
		}
		if (tick == 500) {
			ScreenshotHarness.shot(mc, "02_city_plan_wall");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				VillageHallBlockEntity entity = (VillageHallBlockEntity) server.overworld().getBlockEntity(hall);
				long wall = CityPlanGround.dots(server.overworld(), hall, entity.plan(), player.blockPosition()).stream()
					.filter(d -> d.color() == CityPlanGround.WALL_COLOR).count();
				Showcase.check(wall > 0, "the wall line shows on the ground by the village's corner (" + wall + " dots)");
				hangTheMap(server);
			});
		}
		if (tick == 560) {
			ScreenshotHarness.shot(mc, "03_city_plan_framed");
		}
		if (tick == 590) {
			mc.stop();
		}
	}

	/** The village, its hall, the plan (zones, a street, a lane, a closed wall line) and the City Plan in the player's hand. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"place structure minecraft:village_plains 0 -60 0");
		BlockPos spot = null;
		for (int r = 2; r <= 16 && spot == null; r++) {
			for (int dx = -r; dx <= r && spot == null; dx++) {
				for (int dz = -r; dz <= r && spot == null; dz++) {
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);
					BlockPos below = new BlockPos(dx, top - 1, dz);
					if (top <= -59 && (level.getBlockState(below).is(Blocks.GRASS_BLOCK) || level.getBlockState(below).is(Blocks.DIRT_PATH))) {
						spot = below.above();
					}
				}
			}
		}
		hall = spot != null ? spot : new BlockPos(4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4, 4), 4);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, hall);
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "homes", "Homes", ""));
		CityPlans.apply(player, CityPlans.Edit.paint(hall, 0, rect(-6, -6, -1, 2)));
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "workshops", "Smithy Row", ""));
		CityPlans.apply(player, CityPlans.Edit.paint(hall, 1, rect(1, -6, 6, -1)));
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "gardens", "Gardens", ""));
		CityPlans.apply(player, CityPlans.Edit.paint(hall, 2, rect(1, 1, 6, 5)));
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "market", "Market", ""));
		CityPlans.apply(player, CityPlans.Edit.paint(hall, 3, rect(-6, 3, -1, 5)));
		CityPlans.apply(player, CityPlans.Edit.addRoad(hall, List.of(p(-30, 14), p(30, 14)), CityPlan.Road.STREET, ""));
		CityPlans.apply(player, CityPlans.Edit.addRoad(hall, List.of(p(1, -2), p(1, -28)), CityPlan.Road.LANE, ""));
		CityPlans.apply(player, CityPlans.Edit.wall(hall, List.of(p(-30, -30), p(30, -30), p(30, 26), p(-30, 26)), true));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Showcase.check(entity.plan().roads().size() == 2 && entity.plan().wall().isPresent() && entity.plan().roads().stream().allMatch(CityPlan.Road::approved),
			"a street, a lane and a closed wall line on the plan, the roads approved (" + entity.plan().roads().size() + " roads)");
		walk(server, -6, 19);
	}

	/** The player stands {@code dx}, {@code dz} from the hall, on the ground, looking north-east over the plan. */
	private void walk(MinecraftServer server, int dx, int dz) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		int x = hall.getX() + dx, z = hall.getZ() + dz;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		ScreenshotHarness.hover(player, new Vec3(x + 0.5, y + 0.6, z + 0.5), -150, 38);
	}

	/** The hall's map of the plan in an item frame on a block beside the hall, and the camera in front of it. */
	private void hangTheMap(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		BlockPos wall = hall.east(2);
		level.setBlockAndUpdate(wall, Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(wall.above(), Blocks.STONE_BRICKS.defaultBlockState());
		frame = wall.above().south();
		level.setBlockAndUpdate(frame, Blocks.AIR.defaultBlockState());
		ItemStack map = VillageMaps.map(level, hall);
		ItemFrame itemFrame = new ItemFrame(level, frame, Direction.SOUTH);
		itemFrame.setItem(map, false);
		level.addFreshEntity(itemFrame);
		MapItemSavedData data = level.getMapData(map.get(DataComponents.MAP_ID));
		byte[] land = VillageMaps.colors(level, hall, 64, 1);
		int changed = 0;
		for (int i = 0; i < land.length; i++) {
			if (data != null && data.colors[i] != land[i]) {
				changed++;
			}
		}
		Showcase.check(changed > 1000, "the framed map shows the zones and roads (" + changed + " pixels tinted or drawn)");
		Vec3 face = new Vec3(frame.getX() + 0.5, frame.getY() + 0.5, frame.getZ() + 0.1);
		ScreenshotHarness.hoverLookingAt(player, face.add(0.0, 0.2, 1.6), face);
	}

	private static BlockPos p(int dx, int dz) {
		return new BlockPos(dx, 0, dz);
	}

	/** The cells from {@code x0},{@code z0} to {@code x1},{@code z1}, counted in cells from the hall's own. */
	private BitSet rect(int x0, int z0, int x1, int z1) {
		int size = CityPlan.cellSize();
		BitSet out = new BitSet();
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int cell = CityPlan.cellAt(hall, hall.offset(x * size, 0, z * size));
				if (cell >= 0) {
					out.set(cell);
				}
			}
		}
		return out;
	}
}
