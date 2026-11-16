package io.github.jcondedata.aliveworkplace.devclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=house_shots (ROADMAP 36.23): a house drawn for review, placed whole on flat grass at noon, filmed from the four
 * sides at eye level, from the front corner a little raised, and then from inside at eye level, room by room.
 * <p>
 * Each house has a shots file, {@code tools/blueprints/shots/<structure>.json}:
 * {@code {"structure": "house_t1_a", "size": [w, h, d], "shots": [{"label": "...", "eye": [x, y, z], "look": [x, y, z]}]}},
 * coordinates in blocks from the structure's origin corner (y = 0 its bottom layer), the template
 * {@code aliveworkplace:<structure>}. The folder is {@code -Daliveworkplace.shotsDir} or {@code SHOTS_DIR}, else the
 * project's {@code tools/blueprints/shots} (found above the client's working folder); {@code SHOTS=house_t1_a,house_t1_b}
 * films only those. Pictures: {@code screenshots/<structure>_<nn>_<slug>.png}. Checks: each house placed, every inside
 * eye in the open (not in a wall), and at least one picture taken.
 */
final class HouseShotsScene {
	private static final int SPACING = 128;
	private static final int SETTLE = 100;
	private static final int EACH = 40;
	private static final int OUTSIDE_FOV = 70;
	private static final int INSIDE_FOV = 80;
	private static final double EYE_HEIGHT = 1.62;

	private record Shot(String label, Vec3 eye, Vec3 look, boolean inside) {
	}

	private record House(String structure, Vec3i size, List<JsonObject> inside) {
	}

	private final List<House> houses = new ArrayList<>();
	private final List<String> wrong = Collections.synchronizedList(new ArrayList<>());
	private final Path dir;
	private int tick;
	private int wait;
	private int house = -1;
	private volatile List<Shot> shots = List.of();
	private int next;
	private Shot pending;
	private int taken;
	private boolean finished;

	HouseShotsScene() {
		dir = shotsDir();
		Set<String> only = Arrays.stream(env("SHOTS", "aliveworkplace.shotsOnly").split("[,\\s]+"))
			.filter(s -> !s.isBlank()).collect(Collectors.toSet());
		if (dir == null || !Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
				try {
					JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
					String structure = json.has("structure") ? json.get("structure").getAsString()
						: file.getFileName().toString().replaceFirst("\\.json$", "");
					if (!only.isEmpty() && !only.contains(structure)) {
						continue;
					}
					Vec3i size = json.has("size") ? vec(json.get("size")) : null;
					List<JsonObject> inside = new ArrayList<>();
					if (json.has("shots")) {
						json.getAsJsonArray("shots").forEach(e -> inside.add(e.getAsJsonObject()));
					}
					houses.add(new House(structure, size, inside));
				} catch (RuntimeException | IOException e) {
					wrong.add(file.getFileName() + " can't be read: " + e.getMessage());
				}
			}
		} catch (IOException e) {
			wrong.add("can't list " + dir + ": " + e.getMessage());
		}
	}

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (finished) {
			return;
		}
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.bobView().set(false);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				GameRules rules = level.getGameRules();
				rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
				rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				level.setWeatherParameters(12000, 0, false, false);
			});
		}
		if (tick < 40) {
			return;
		}
		if (tick % 20 == 0 && house >= 0) {
			// Slimes from the superflat world's slime chunks, and anything else that walked in.
			BlockPos origin = origin(house);
			server.execute(() -> server.overworld().getEntitiesOfClass(Mob.class, new AABB(origin).inflate(60))
				.forEach(net.minecraft.world.entity.Entity::discard));
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (pending != null) {
			String name = String.format(Locale.ROOT, "%s_%02d_%s", houses.get(house).structure(), next, slug(pending.label()));
			ScreenshotHarness.shot(mc, name);
			taken++;
			pending = null;
			wait = 2;
			return;
		}
		if (house >= 0 && next < shots.size()) {
			pending = shots.get(next++);
			mc.options.fov().set(pending.inside() ? INSIDE_FOV : OUTSIDE_FOV);
			Shot shot = pending;
			server.execute(() -> view(server, shot));
			wait = EACH;
			return;
		}
		if (house + 1 < houses.size()) {
			house++;
			next = 0;
			int i = house;
			House h = houses.get(i);
			shots = List.of();
			server.execute(() -> shots = place(server, h, origin(i)));
			wait = SETTLE;
			return;
		}
		finished = true;
		if (houses.isEmpty()) {
			Showcase.check(false, "no shots files in " + dir + (env("SHOTS", "aliveworkplace.shotsOnly").isBlank() ? "" : " for SHOTS=" + env("SHOTS", "aliveworkplace.shotsOnly")));
		} else {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? houses.stream().map(House::structure).collect(Collectors.joining(", ")) + " placed whole on flat ground and filmed outside and in, every eye in the open"
				: String.join("; ", wrong));
			Showcase.check(taken > 0, taken + " pictures taken");
		}
		mc.stop();
	}

	/** Clears a box around the site down to a grass floor, places the house and returns its shots (outside, then inside). */
	private List<Shot> place(MinecraftServer server, House h, BlockPos origin) {
		ServerLevel level = server.overworld();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace", h.structure());
		StructureTemplate template = server.getStructureManager().get(id).orElse(null);
		Vec3i size = template != null ? template.getSize() : h.size();
		if (size == null) {
			wrong.add(h.structure() + " has no template and no size");
			return List.of();
		}
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		// Over the site first, so its chunks are loaded and sent before the blocks change.
		ScreenshotHarness.hoverLookingAt(player, Vec3.atLowerCornerOf(origin).add(size.getX() / 2.0, size.getY() + 10, -10),
			Vec3.atLowerCornerOf(origin).add(size.getX() / 2.0, 0, size.getZ() / 2.0));
		int margin = 16;
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-margin, -1, -margin), origin.offset(size.getX() + margin, size.getY() + 24, size.getZ() + margin))) {
			BlockState want = p.getY() == origin.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
			if (level.getBlockState(p) != want) {
				level.setBlock(p, want, 2 | 16);
			}
		}
		if (template == null) {
			wrong.add(h.structure() + " isn't a structure (" + id + ")");
		} else {
			boolean placed = template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1), 2);
			long blocks = BlockPos.betweenClosedStream(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))
				.filter(p -> !level.getBlockState(p).isAir()).count();
			if (!placed || blocks == 0) {
				wrong.add(h.structure() + " wasn't placed");
			}
			if (h.size() != null && !h.size().equals(size)) {
				wrong.add(h.structure() + "'s shots file says size " + h.size().toShortString() + " but the template is " + size.toShortString());
			}
		}
		List<Shot> out = new ArrayList<>();
		Vec3 o = Vec3.atLowerCornerOf(origin);
		double w = size.getX();
		double ht = size.getY();
		double d = size.getZ();
		Vec3 center = o.add(w / 2, Math.min(ht * 0.35, 4), d / 2);
		double out2 = Math.max(w, d) / 2 + Math.max(6, Math.max(Math.max(w, d), ht) * 0.6);
		double eyeY = EYE_HEIGHT;
		out.add(new Shot("front", o.add(w / 2, eyeY, d / 2 - out2), center, false));
		out.add(new Shot("right side", o.add(w / 2 + out2, eyeY, d / 2), center, false));
		out.add(new Shot("back", o.add(w / 2, eyeY, d / 2 + out2), center, false));
		out.add(new Shot("left side", o.add(w / 2 - out2, eyeY, d / 2), center, false));
		double corner = out2 * 0.65;
		out.add(new Shot("three quarter front", o.add(w / 2 + corner, ht * 0.5 + 2, d / 2 - corner), center, false));
		for (JsonObject s : h.inside()) {
			String label = s.has("label") ? s.get("label").getAsString() : "inside";
			try {
				Vec3 eye = o.add(vec3(s.get("eye")));
				Vec3 look = o.add(vec3(s.get("look")));
				BlockState at = level.getBlockState(BlockPos.containing(eye));
				if (!at.getCollisionShape(level, BlockPos.containing(eye)).isEmpty()) {
					wrong.add(h.structure() + " \"" + label + "\": the eye is inside " + at.getBlock().getName().getString());
				}
				out.add(new Shot(label, eye, look, true));
			} catch (RuntimeException e) {
				wrong.add(h.structure() + " \"" + label + "\" can't be read: " + e.getMessage());
			}
		}
		return List.copyOf(out);
	}

	/** The camera at {@code shot.eye()} (the eyes, not the feet) looking at {@code shot.look()}, no hand, no hotbar. */
	private static void view(MinecraftServer server, Shot shot) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		player.setGameMode(GameType.SPECTATOR);
		Vec3 delta = shot.look().subtract(shot.eye());
		float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
		player.teleportTo(player.serverLevel(), shot.eye().x, shot.eye().y - player.getEyeHeight(), shot.eye().z, yaw, pitch);
	}

	private static BlockPos origin(int i) {
		return new BlockPos(i * SPACING, -60, 0);
	}

	private static String slug(String label) {
		String s = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		return s.isEmpty() ? "shot" : s.length() > 48 ? s.substring(0, 48) : s;
	}

	private static Vec3i vec(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		return new Vec3i(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
	}

	private static Vec3 vec3(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
	}

	private static String env(String variable, String property) {
		String value = System.getProperty(property);
		if (value == null) {
			value = System.getenv(variable);
		}
		return value == null ? "" : value;
	}

	/** The shots folder: set explicitly, or the nearest {@code tools/blueprints/shots} above the working folder. */
	private static Path shotsDir() {
		String set = env("SHOTS_DIR", "aliveworkplace.shotsDir");
		if (!set.isBlank()) {
			return Path.of(set).toAbsolutePath();
		}
		for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
			Path candidate = p.resolve("tools/blueprints/shots");
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
			if (Files.isDirectory(p.resolve("tools/blueprints"))) {
				return candidate; // the project root, without shots yet
			}
		}
		return null;
	}
}
