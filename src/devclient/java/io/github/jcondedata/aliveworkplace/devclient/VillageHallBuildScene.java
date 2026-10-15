package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=village_hall: Village Hall I, II and III placed finished side by side, then Hall III in Sandstone and in Cherry
 * (the styles the Blueprint Table offers), each filmed as a player walking round it would see it: a front three-quarter
 * view from either side, both sides, the back and the room inside. Checks that every level keeps the hall block at
 * (6, 1, 10) and the bell at (6, 4, 0).
 */
final class VillageHallBuildScene {
	private static final int SPACING = 48;
	private static final int FIRST = 60;
	private static final int VIEW = 20;
	private static final String[] VIEWS = {"front", "front_west", "east", "west", "back", "inside"};
	private static final int EACH = VIEW * VIEWS.length;
	private static final BlockPos HALL = new BlockPos(6, 1, 10);
	private static final BlockPos BELL = new BlockPos(6, 4, 0);

	private record Piece(String name, ResourceLocation id, Vec3i size) {
	}

	private static final List<Piece> PIECES = List.of(
		new Piece("village_hall", StarterBlueprints.VILLAGE_HALL.id(), StarterBlueprints.VILLAGE_HALL.size()),
		new Piece("village_hall_2", StarterBlueprints.VILLAGE_HALL_2.id(), StarterBlueprints.VILLAGE_HALL_2.size()),
		new Piece("village_hall_3", StarterBlueprints.VILLAGE_HALL_3.id(), StarterBlueprints.VILLAGE_HALL_3.size()),
		new Piece("sandstone_village_hall_3", BlueprintStyles.styled(StarterBlueprints.VILLAGE_HALL_3.id(), "sandstone"),
			StarterBlueprints.VILLAGE_HALL_3.size()),
		new Piece("cherry_village_hall_3", BlueprintStyles.styled(StarterBlueprints.VILLAGE_HALL_3.id(), "cherry"),
			StarterBlueprints.VILLAGE_HALL_3.size()));

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
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
				for (int i = 0; i < PIECES.size(); i++) {
					place(level, PIECES.get(i), origin(i));
				}
			});
		}
		if (tick % 20 == 0) {
			// Slimes from the superflat world's slime chunks hop into the shots.
			server.execute(() -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
				new AABB(origin(0)).inflate(SPACING * PIECES.size())).forEach(net.minecraft.world.entity.Entity::discard));
		}
		int t = tick - FIRST;
		if (t >= 0 && t < PIECES.size() * EACH) {
			int piece = t / EACH;
			int view = (t % EACH) / VIEW;
			int at = t % VIEW;
			if (at == 0) {
				look(server, piece, view);
			}
			if (at == VIEW - 2) {
				ScreenshotHarness.shot(mc, String.format("%02d_%s_%s", 10 + piece * VIEWS.length + view, PIECES.get(piece).name(), VIEWS[view]));
			}
		}
		if (tick == FIRST + PIECES.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "Village Hall I, II and III and Hall III in Sandstone and Cherry stand finished, each with its hall block and bell in place"
				: String.join("; ", wrong));
			mc.stop();
		}
	}

	private void place(ServerLevel level, Piece piece, BlockPos origin) {
		Blueprint blueprint = BlueprintLibrary.get(level, piece.id()).orElse(null);
		if (blueprint == null) {
			wrong.add(piece.name() + " is missing");
			return;
		}
		for (Blueprint.Entry e : blueprint.blocks()) {
			level.setBlock(origin.offset(e.pos()), e.state(), 2 | 16);
		}
		if (!level.getBlockState(origin.offset(HALL)).is(ModBlocks.VILLAGE_HALL)) {
			wrong.add(piece.name() + " has no hall block at " + HALL.toShortString());
		}
		if (!level.getBlockState(origin.offset(BELL)).is(Blocks.BELL)) {
			wrong.add(piece.name() + " has no bell at " + BELL.toShortString());
		}
	}

	private static BlockPos origin(int i) {
		return new BlockPos(i * SPACING, -60, 0);
	}

	/** The camera on piece {@code i}: the front is the street side (north, z = 0). */
	private static void look(MinecraftServer server, int i, int view) {
		Vec3i size = PIECES.get(i).size();
		int height = Math.min(size.getY(), 14);
		Vec3 base = Vec3.atLowerCornerOf(origin(i));
		Vec3 center = base.add(size.getX() / 2.0, height * 0.4, size.getZ() / 2.0);
		double dist = Math.max(size.getX(), size.getZ()) * 0.8 + 6;
		Vec3 eye;
		Vec3 target = center;
		switch (VIEWS[view]) {
			case "front" -> eye = center.add(dist * 0.6, 4, -dist * 0.9);
			case "front_west" -> eye = center.add(-dist * 0.6, 4, -dist * 0.9);
			case "east" -> eye = center.add(dist * 1.05, 3, 0);
			case "west" -> eye = center.add(-dist * 1.05, 3, 0);
			case "back" -> eye = center.add(dist * 0.35, 4, dist * 1.05);
			default -> { // inside the hall, by the door, looking down the runner at the hall block
				eye = base.add(6.5, 2.7, 4.5);
				target = base.add(6.5, 2.0, 10.5);
			}
		}
		Vec3 from = eye;
		Vec3 to = target;
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), from, to));
	}
}
