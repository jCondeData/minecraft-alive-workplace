package io.github.jcondedata.aliveworkplace.devclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.io.Reader;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=wonder_gallery (ROADMAP 35.9; each Wonder's design adds itself to {@link #WONDERS}): every Wonder placed whole,
 * stage after stage from its data file's parts and offsets, in a row (a coastal one, the Lighthouse, with its sea
 * flooded in first): a still of each from the front and from behind.
 * Checks every part of every stage loaded and placed blocks.
 */
final class WonderGalleryScene {
	/**
	 * The Wonders, by id, with their size (x, y, z) for the camera, and for a Wonder by the water the first row (z) of
	 * its sea (-1: none), which the scene floods three deep, sides and back, before placing it.
	 */
	private record Wonder(String id, Vec3i size, int sea) {
	}

	private static final List<Wonder> WONDERS = List.of(
		new Wonder("great_cathedral", new Vec3i(39, 53, 55), -1), // 35.9
		new Wonder("lighthouse", new Vec3i(25, 46, 33), 11), // 35.11: the mole, the jetty and the boathouse in the sea
		new Wonder("great_library", new Vec3i(41, 34, 41), -1), // 35.13
		new Wonder("observatory", new Vec3i(31, 43, 31), -1), // 35.17
		new Wonder("great_wall", new Vec3i(97, 25, 15), -1), // 35.15: front (z = 0) is the village side
		new Wonder("hanging_gardens", new Vec3i(47, 33, 44), -1), // 35.19
		new Wonder("colossus", new Vec3i(29, 47, 29), -1)); // 35.21: the road runs front to back through its arch
	/** How far the sea reaches past a coastal Wonder's sides and back. */
	private static final int SEA_MARGIN = 10;
	private static final int SPACING = 80;
	private static final int FIRST = 80;
	private static final int EACH = 80;

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(8);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(3000);
				for (int i = 0; i < WONDERS.size(); i++) {
					if (WONDERS.get(i).sea() >= 0) {
						flood(level, WONDERS.get(i), origin(i));
					}
					place(level, WONDERS.get(i).id(), origin(i));
				}
			});
		}
		if (tick % 20 == 0) {
			// Slimes from the superflat world's slime chunks hop into the shots.
			server.execute(() -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
				new AABB(origin(0)).inflate(origin(WONDERS.size()).getX() + 64)).forEach(net.minecraft.world.entity.Entity::discard));
		}
		int front = tick - FIRST;
		if (front >= 0 && front % EACH == 0 && front / EACH < WONDERS.size()) {
			look(server, front / EACH, false);
		}
		if (front >= 50 && (front - 50) % EACH == 0 && (front - 50) / EACH < WONDERS.size()) {
			int i = (front - 50) / EACH;
			ScreenshotHarness.shot(mc, String.format("%02d_%s", i * 2 + 10, WONDERS.get(i).id()));
			look(server, i, true);
		}
		if (front >= 75 && (front - 75) % EACH == 0 && (front - 75) / EACH < WONDERS.size()) {
			int i = (front - 75) / EACH;
			ScreenshotHarness.shot(mc, String.format("%02d_%s_back", i * 2 + 11, WONDERS.get(i).id()));
		}
		if (tick == FIRST + WONDERS.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "every Wonder stands whole, every part of every stage placed: " + WONDERS.stream().map(Wonder::id).toList()
				: String.join("; ", wrong));
			mc.stop();
		}
	}

	/** Places Wonder {@code id} whole: each stage's parts, in order, at their offsets from {@code origin}. */
	private void place(ServerLevel level, String id, BlockPos origin) {
		ResourceLocation path = AliveWorkplace.id("wonders/" + id + ".json");
		JsonObject file;
		try (Reader reader = level.getServer().getResourceManager().getResource(path).orElseThrow().openAsReader()) {
			file = JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			wrong.add(id + ": no data file (" + e + ")");
			return;
		}
		boolean first = true;
		for (JsonElement stage : file.getAsJsonArray("stages")) {
			boolean clearing = first;
			first = false;
			for (JsonElement p : stage.getAsJsonObject().getAsJsonArray("parts")) {
				ResourceLocation part = ResourceLocation.parse(p.getAsJsonObject().get("part").getAsString());
				JsonArray o = p.getAsJsonObject().getAsJsonArray("offset");
				BlockPos at = origin.offset(o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt());
				Blueprint blueprint = BlueprintLibrary.get(level, part).orElse(null);
				// The first stage is saved whole, air included, so a part of it may be only air: it clears the site.
				if (blueprint == null || blueprint.solidBlockCount() == 0 && !(clearing && !blueprint.blocks().isEmpty())) {
					wrong.add(part + " is missing or empty");
					continue;
				}
				for (Blueprint.Entry e : blueprint.blocks()) {
					level.setBlock(at.offset(e.pos()), e.state(), 2 | 16);
					if (e.nbt() != null) {
						var be = level.getBlockEntity(at.offset(e.pos()));
						if (be != null) {
							be.loadWithComponents(e.nbt(), level.registryAccess());
						}
					}
				}
			}
		}
	}

	/** The sea of a Wonder by the water: the ground under it and round its sides and back dug out three deep and filled. */
	private static void flood(ServerLevel level, Wonder w, BlockPos origin) {
		net.minecraft.world.level.block.state.BlockState water = net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
		for (int x = -SEA_MARGIN; x < w.size().getX() + SEA_MARGIN; x++) {
			for (int z = w.sea(); z < w.size().getZ() + SEA_MARGIN; z++) {
				for (int dy = 1; dy <= 3; dy++) {
					level.setBlock(origin.offset(x, -dy, z), water, 2 | 16);
				}
			}
		}
	}

	/** Wonder {@code i}'s corner: SPACING apart, or the one before it and 30 blocks clear where it's longer (the Wall). */
	private static BlockPos origin(int i) {
		int x = 0;
		for (int j = 0; j < i; j++) {
			x += Math.max(SPACING, WONDERS.get(j).size().getX() + 30);
		}
		return new BlockPos(x, -60, 0);
	}

	/** The camera on Wonder {@code i}, from the front (north) or from behind. */
	private static void look(MinecraftServer server, int i, boolean back) {
		Vec3i size = WONDERS.get(i).size();
		Vec3 center = Vec3.atLowerCornerOf(origin(i)).add(size.getX() / 2.0, size.getY() * 0.35, size.getZ() / 2.0);
		double dist = Math.max(Math.max(size.getX(), size.getZ()), size.getY()) * 0.75 + 8;
		Vec3 eye = back ? center.add(-dist * 0.55, size.getY() * 0.25 + 3, dist) : center.add(dist * 0.55, size.getY() * 0.25 + 2, -dist);
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), eye, center));
	}
}
