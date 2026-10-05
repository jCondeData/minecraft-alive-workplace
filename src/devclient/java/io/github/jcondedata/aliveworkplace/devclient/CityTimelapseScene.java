package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.command.CitySoak;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=city_timelapse (ROADMAP 27.22, the GIF that leads the 1.1 release notes): the city soak ({@link CitySoak}) filmed
 * from above. A plains village round its hall, a plan with Homes, Workshops, Farms, Market, Gardens and Keep Clear, two
 * streets and a wall line; its Steward runs it for 6 in-game days at full speed and the village grows into the plan:
 * homes, workplaces, the streets, the wall and the old house renewed. A still every 30 seconds makes the time-lapse.
 */
final class CityTimelapseScene {
	/** Away from the other scenes; the ground is laid at y -59, its stone above the world's floor. */
	private static final BlockPos HALL = new BlockPos(600, -58, 600);
	private static final int DAYS = CitySoak.DAYS;
	/** Client ticks: 40 minutes at most, for 6 days and the builders' last 2 at full speed. */
	private static final int GIVE_UP = 48000;
	private static final int STILL_EVERY = 600;
	private static final int START = 1400;
	private int tick;
	private int frame;
	private int doneAt = -1;
	private volatile int finished;
	private volatile int started;
	private volatile boolean over;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(8);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.graphicsMode().set(GraphicsStatus.FAST);
			mc.options.framerateLimit().set(15);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				run(server, "forceload add " + (HALL.getX() - CitySoak.REACH) + " " + (HALL.getZ() - CitySoak.REACH) + " "
					+ (HALL.getX() + CitySoak.REACH) + " " + (HALL.getZ() + CitySoak.REACH));
				ServerPlayer player = player(server);
				// Night vision: the nights go by in seconds and stay readable in the time-lapse.
				player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, -1, 0, false, false));
				ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(HALL).add(0, 78, 92), Vec3.atCenterOf(HALL.offset(0, 0, -4)));
			});
		}
		if (tick == 160) {
			server.execute(() -> {
				CitySoak.begin(server.overworld(), HALL, DAYS);
				// Frozen while software rendering draws the new ground, so the first still shows the start.
				run(server, "tick freeze");
			});
		}
		if (tick == START) {
			ScreenshotHarness.shot(mc, "01_city_start");
			server.execute(() -> {
				run(server, "tick unfreeze");
				run(server, "tick sprint " + (DAYS + CitySoak.WIND_DOWN_DAYS) * 24000L);
			});
		}
		if (tick > START && doneAt < 0) {
			if (tick % STILL_EVERY == 0) {
				ScreenshotHarness.shot(mc, String.format("frame_%03d", frame++));
			}
			if (tick % 20 == 0) {
				server.execute(() -> {
					int[] progress = CitySoak.progress();
					finished = progress[0];
					started = progress[1];
					over = progress[2] == 0 && CitySoak.lastResult != null;
				});
			}
			if (over) {
				doneAt = tick;
				server.execute(() -> run(server, "tick sprint stop"));
			}
		}
		if (doneAt > 0 && tick == doneAt + 60) {
			String result = CitySoak.lastResult;
			java.util.regex.Matcher m = result == null ? null : java.util.regex.Pattern.compile("City result: (\\d+)/(\\d+) builds").matcher(result);
			boolean all = m != null && m.find() && m.group(1).equals(m.group(2)) && Integer.parseInt(m.group(2)) > 0;
			Showcase.check(all && result.contains("in Keep Clear: none;")
					&& java.util.regex.Pattern.compile("items off: none(;|$)").matcher(result).find(),
				"the village grew into its plan: every build the Steward started finished, each in its zone, nothing duplicated or lost: " + result);
			ScreenshotHarness.shot(mc, "02_city_done");
			server.execute(() -> ScreenshotHarness.hoverLookingAt(player(server), Vec3.atCenterOf(HALL).add(-30, 26, 40),
				Vec3.atCenterOf(HALL.offset(-20, 0, -10))));
		}
		if (doneAt > 0 && tick == doneAt + 160) {
			ScreenshotHarness.shot(mc, "03_city_close");
			mc.stop();
		}
		if (tick >= GIVE_UP && doneAt < 0) {
			doneAt = Integer.MAX_VALUE / 2;
			server.execute(() -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("City scene gave up: {}", CitySoak.finish()));
			Showcase.check(false, "the village grew into its plan in time (" + finished + " of " + started + " builds finished)");
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static void run(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}
}
