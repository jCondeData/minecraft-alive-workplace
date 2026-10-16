package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=build_fixes: the builds fixed after the owner's playtest of 2026-10-16 (Starter Cottage II, the Storehouse and
 * its levels, the Terrace I and II, the Weaver's Cottage I and II, the Smithy I and II, the Apiary Garden I and II),
 * placed finished side by side and filmed from the front, both sides and the back.
 */
final class BuildFixesScene {
	private static final int SPACING = 40;
	private static final int FIRST = 60;
	private static final int VIEW = 20;
	private static final String[] VIEWS = {"front", "east", "west", "back"};
	private static final int EACH = VIEW * VIEWS.length;

	private static final List<StarterBlueprints.Entry> PIECES = List.of(
		StarterBlueprints.STARTER_COTTAGE_2, StarterBlueprints.STOREHOUSE, StarterBlueprints.STOREHOUSE_2,
		StarterBlueprints.STOREHOUSE_3, StarterBlueprints.TERRACE, StarterBlueprints.TERRACE_2,
		StarterBlueprints.WEAVERS_COTTAGE, StarterBlueprints.WEAVERS_COTTAGE_2, StarterBlueprints.SMITHY,
		StarterBlueprints.SMITHY_2, StarterBlueprints.APIARY_GARDEN, StarterBlueprints.APIARY_GARDEN_2);

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
				ScreenshotHarness.shot(mc, String.format("%02d_%s_%s", 10 + piece * VIEWS.length + view,
					PIECES.get(piece).id().getPath(), VIEWS[view]));
			}
		}
		if (tick == FIRST + PIECES.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "the twelve fixed builds stand finished side by side"
				: String.join("; ", wrong));
			mc.stop();
		}
	}

	private void place(ServerLevel level, StarterBlueprints.Entry piece, BlockPos origin) {
		Blueprint blueprint = BlueprintLibrary.get(level, piece.id()).orElse(null);
		if (blueprint == null) {
			wrong.add(piece.id().getPath() + " is missing");
			return;
		}
		for (Blueprint.Entry e : blueprint.blocks()) {
			level.setBlock(origin.offset(e.pos()), e.state(), 2 | 16);
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
		Vec3 eye = switch (VIEWS[view]) {
			case "front" -> center.add(dist * 0.3, 4, -dist * 1.05);
			case "east" -> center.add(dist * 1.05, 3, 0);
			case "west" -> center.add(-dist * 1.05, 3, 0);
			default -> center.add(dist * 0.3, 5, dist * 1.05);
		};
		Vec3 target = center;
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), eye, target));
	}
}
