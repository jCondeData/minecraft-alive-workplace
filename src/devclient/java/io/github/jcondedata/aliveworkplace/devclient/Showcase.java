package io.github.jcondedata.aliveworkplace.devclient;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * The nightly showcase's record of one scene run (ROADMAP 22.4, tools/showcase): small frames for the GIF, the scene's
 * pass/fail checks, and what makes a picture look broken (villagers stuck in walls, text shown as a raw translation
 * key). Everything goes to {@code run/screenshots/showcase.json}, rewritten whenever it changes, so a crash still
 * leaves what was known. tools/showcase/process.py judges the scene from it.
 */
final class Showcase {
	/** A GIF frame every this many client ticks (half a second), 480x270. */
	private static final int FRAME_EVERY = 10;
	private static final int FRAME_WIDTH = 480;
	private static final int FRAME_HEIGHT = 270;
	/** A villager in a wall for this many checks in a row (a second apart) is stuck. */
	private static final int STUCK_CHECKS = 3;

	private static final String SCENE = System.getProperty("aliveworkplace.scene", "builders");
	private static final List<Map<String, Object>> CHECKS = new ArrayList<>();
	private static final Set<String> PROBLEMS = new LinkedHashSet<>();
	private static final Set<String> MISSING_KEYS = new TreeSet<>();
	private static final Set<String> OTHER_MISSING_KEYS = new TreeSet<>();
	private static final Map<UUID, Integer> IN_WALL = new HashMap<>();
	private static final long STARTED = System.currentTimeMillis();
	private static File out;
	private static int ticks;
	private static int frames;
	private static int outOfWorld;
	private static boolean stopped;

	/** Hooks in; only called when the harness is on (-Daliveworkplace.shots=true). */
	static void install() {
		ClientTickEvents.END_CLIENT_TICK.register(Showcase::clientTick);
		ServerTickEvents.END_SERVER_TICK.register(Showcase::serverTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			synchronized (Showcase.class) {
				stopped = true;
			}
			write();
		});
	}

	/** Records whether the scene's job visibly did its work. A scene passes only if every check it makes passes. */
	static synchronized void check(boolean pass, String what) {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("pass", pass);
		c.put("what", what);
		c.put("tick", ticks);
		CHECKS.add(c);
		System.out.println("[showcase] " + (pass ? "PASS " : "FAIL ") + what);
		write();
	}

	/** Something that makes the scene's pictures look broken. */
	static synchronized void problem(String what) {
		if (PROBLEMS.add(what)) {
			System.out.println("[showcase] PROBLEM " + what);
			write();
		}
	}

	private static void clientTick(Minecraft mc) {
		if (out == null) {
			out = mc.gameDirectory;
		}
		watchLanguage();
		if (mc.level == null || mc.player == null) {
			// Out of the world after being in it: the integrated server stopped (a crash, most likely). Say so and quit,
			// rather than wait on a title screen until the scene's time runs out.
			if (ticks > 0 && ++outOfWorld == 60) {
				problem("the game left the world mid-scene (the server stopped or crashed: see the game's log)");
				mc.stop();
			}
			return;
		}
		outOfWorld = 0;
		ticks++;
		if (ticks >= 20 && ticks % FRAME_EVERY == 0) {
			frame(mc);
		}
		if (ticks == 20 || ticks % 200 == 0) {
			write();
		}
	}

	/** A small copy of what's on screen, written in the background: screenshots/gif/NNNNN.png. */
	private static void frame(Minecraft mc) {
		NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget());
		NativeImage small = new NativeImage(FRAME_WIDTH, FRAME_HEIGHT, false);
		full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), small);
		full.close();
		File dir = new File(mc.gameDirectory, "screenshots/gif");
		File file = new File(dir, String.format("%05d.png", ticks));
		frames++;
		Util.ioPool().execute(() -> {
			try {
				dir.mkdirs();
				small.writeToFile(file);
			} catch (IOException e) {
				System.out.println("[showcase] couldn't write " + file + ": " + e);
			} finally {
				small.close();
			}
		});
	}

	/** Every second: villagers whose head or body is inside a block, three checks in a row. */
	private static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (!(e instanceof Villager v) || !v.isAlive() || v.isPassenger() || v.isSleeping() || v.isInvisible()) {
					continue;
				}
				boolean inWall = v.isInWall() || !level.noCollision(v, v.getBoundingBox().deflate(0.1));
				int n = inWall ? IN_WALL.merge(v.getUUID(), 1, Integer::sum) : 0;
				if (!inWall) {
					IN_WALL.remove(v.getUUID());
				}
				if (n == STUCK_CHECKS) {
					String job = v.getVillagerData().getProfession().name();
					problem(String.format("a villager (%s%s) is stuck in a wall at %d %d %d, in %s", job.isEmpty() ? "no job" : job,
						v.hasCustomName() ? " " + v.getCustomName().getString() : "", v.getBlockX(), v.getBlockY(), v.getBlockZ(),
						level.getBlockState(v.blockPosition().above()).getBlock().getName().getString()));
				}
			}
		}
	}

	/** Puts a watcher in front of the game's language, to catch text shown as its raw key (a missing translation). */
	private static void watchLanguage() {
		Language current = Language.getInstance();
		if (!(current instanceof Watching)) {
			Language.inject(new Watching(current));
		}
	}

	private static synchronized void missingKey(String key) {
		boolean ours = key.contains("aliveworkplace");
		if ((ours ? MISSING_KEYS : OTHER_MISSING_KEYS).add(key)) {
			System.out.println("[showcase] no translation for " + key);
			write();
		}
	}

	private static synchronized void write() {
		if (out == null) {
			return;
		}
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("scene", SCENE);
		json.put("checks", CHECKS);
		json.put("problems", new ArrayList<>(PROBLEMS));
		json.put("missingKeys", new ArrayList<>(MISSING_KEYS));
		json.put("otherMissingKeys", new ArrayList<>(OTHER_MISSING_KEYS));
		json.put("ticks", ticks);
		json.put("frames", frames);
		json.put("seconds", (System.currentTimeMillis() - STARTED) / 1000);
		json.put("stopped", stopped);
		try {
			Files.writeString(new File(out, "showcase.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(json),
				StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("[showcase] couldn't write showcase.json: " + e);
		}
	}

	/** The game's language, noting every key asked for that it has no text for (the key itself is what shows). */
	private static final class Watching extends Language {
		private final Language inner;

		Watching(Language inner) {
			this.inner = inner;
		}

		@Override
		public String getOrDefault(String key, String fallback) {
			if (key.equals(fallback) && !inner.has(key)) {
				missingKey(key);
			}
			return inner.getOrDefault(key, fallback);
		}

		@Override
		public boolean has(String key) {
			return inner.has(key);
		}

		@Override
		public boolean isDefaultRightToLeft() {
			return inner.isDefaultRightToLeft();
		}

		@Override
		public FormattedCharSequence getVisualOrder(FormattedText text) {
			return inner.getVisualOrder(text);
		}
	}

	private Showcase() {
	}
}
