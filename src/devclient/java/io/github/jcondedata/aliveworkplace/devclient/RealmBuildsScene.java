package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
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
 * SCENE=realm_builds (ROADMAP 33.14): the Colony Camp (with its hall and post where a colony puts them), the Royal Hall
 * and the Royal Hall II as drawn, then each in the five styles, placed in a row: a still of each from the front and
 * from behind. Checks every piece is placed, the camp has its Blueprint Table and one chest, and each Royal Hall has its
 * vault chests and shows on the village map as a hall.
 */
final class RealmBuildsScene {
	static final List<String> STYLES = List.of("stonework", "sandstone", "dark_oak", "cherry", "grand");
	private static final int SPACING = 40;
	private static final int FIRST = 60;
	private static final int EACH = 60;
	private static final ResourceLocation CAMP = ColonyArrival.COLONY_CAMP;

	private record Piece(String name, ResourceLocation id, Vec3i size) {
	}

	private static final List<Piece> PIECES = pieces();

	private static List<Piece> pieces() {
		List<Piece> out = new ArrayList<>();
		Vec3i camp = new Vec3i(15, 6, 13);
		out.add(new Piece("colony_camp", CAMP, camp));
		out.add(new Piece("royal_hall", StarterBlueprints.ROYAL_HALL.id(), StarterBlueprints.ROYAL_HALL.size()));
		out.add(new Piece("royal_hall_2", StarterBlueprints.ROYAL_HALL_2.id(), StarterBlueprints.ROYAL_HALL_2.size()));
		for (String style : STYLES) {
			out.add(new Piece(style + "_colony_camp", BlueprintStyles.styled(CAMP, style), camp));
			out.add(new Piece(style + "_royal_hall", BlueprintStyles.styled(StarterBlueprints.ROYAL_HALL.id(), style), StarterBlueprints.ROYAL_HALL.size()));
			out.add(new Piece(style + "_royal_hall_2", BlueprintStyles.styled(StarterBlueprints.ROYAL_HALL_2.id(), style), StarterBlueprints.ROYAL_HALL_2.size()));
		}
		return List.copyOf(out);
	}

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
		int front = tick - FIRST;
		if (front >= 0 && front % EACH == 0 && front / EACH < PIECES.size()) {
			look(server, front / EACH, false);
		}
		if (front >= 40 && (front - 40) % EACH == 0 && (front - 40) / EACH < PIECES.size()) {
			int i = (front - 40) / EACH;
			ScreenshotHarness.shot(mc, String.format("%02d_%s", i * 2 + 10, PIECES.get(i).name()));
			look(server, i, true);
		}
		if (front >= 55 && (front - 55) % EACH == 0 && (front - 55) / EACH < PIECES.size()) {
			int i = (front - 55) / EACH;
			ScreenshotHarness.shot(mc, String.format("%02d_%s_back", i * 2 + 11, PIECES.get(i).name()));
		}
		if (tick == FIRST + PIECES.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "the Colony Camp, the Royal Hall and the Royal Hall II stand as drawn and in the five styles, the camp with its table and chest, each hall with its vault and on the map as a hall"
				: String.join("; ", wrong));
			mc.stop();
		}
	}

	/** Places a piece's blueprint (a styled one with its swaps) and checks it; a camp gets its hall and post where a colony puts them. */
	private void place(ServerLevel level, Piece piece, BlockPos origin) {
		Blueprint blueprint = BlueprintLibrary.get(level, piece.id()).orElse(null);
		if (blueprint == null) {
			wrong.add(piece.name() + " is missing");
			return;
		}
		for (Blueprint.Entry e : blueprint.blocks()) {
			level.setBlock(origin.offset(e.pos()), e.state(), 2 | 16);
		}
		long chests = blueprint.blocks().stream().filter(e -> e.state().is(Blocks.CHEST)).count();
		if (piece.name().endsWith("colony_camp")) {
			level.setBlock(origin.offset(7, 1, 7), ModBlocks.VILLAGE_HALL.defaultBlockState(), 2 | 16);
			level.setBlock(origin.offset(10, 0, 3), ModBlocks.TRAVEL_POST.defaultBlockState(), 2 | 16);
			boolean table = blueprint.blocks().stream().anyMatch(e -> e.state().is(ModBlocks.BLUEPRINT_TABLE));
			if (!table || chests != 1) {
				wrong.add(piece.name() + " has " + (table ? "a" : "no") + " table and " + chests + " chests");
			}
		} else {
			if (chests < 4) {
				wrong.add(piece.name() + " has " + chests + " vault chests");
			}
			if (VillageMaps.kindOf(piece.id()).orElse(null) != VillageMaps.Kind.HALL) {
				wrong.add(piece.name() + " isn't a hall on the map");
			}
		}
	}

	private static BlockPos origin(int i) {
		return new BlockPos(i * SPACING, -60, 0);
	}

	/** The camera on piece {@code i}, from the front (north) or from behind. */
	private static void look(MinecraftServer server, int i, boolean back) {
		Vec3i size = PIECES.get(i).size();
		Vec3 center = Vec3.atLowerCornerOf(origin(i)).add(size.getX() / 2.0, size.getY() * 0.35, size.getZ() / 2.0);
		double dist = Math.max(Math.max(size.getX(), size.getZ()), size.getY()) * 0.75 + 5;
		Vec3 eye = back ? center.add(-dist * 0.55, size.getY() * 0.3 + 3, dist) : center.add(dist * 0.55, size.getY() * 0.3 + 2, -dist);
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), eye, center));
	}
}
