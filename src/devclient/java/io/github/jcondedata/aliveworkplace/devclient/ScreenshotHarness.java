package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only harness: stages three builders building the starter blueprints in a flat world, saves a
 * series of screenshots (stitched into a GIF by tools/screenshots/make_gif.py) and quits.
 * Only active with -Daliveworkplace.shots=true (the "screenshots" Gradle run).
 */
public class ScreenshotHarness implements ClientModInitializer {
	private static final int FRAME_EVERY = 30;
	private static final int GIVE_UP_AT = 16000;
	private static final Vec3 WIDE = new Vec3(0.5, -49, 23.5);

	private int tick;
	private int frame;
	private int doneAt = -1;
	private final AtomicBoolean allDone = new AtomicBoolean(false);
	private final List<Villager> builders = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("aliveworkplace.shots")) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
			return;
		}
		if ("table".equals(System.getProperty("aliveworkplace.scene"))) {
			tableScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("preview".equals(System.getProperty("aliveworkplace.scene"))) {
			previewScene(mc, mc.getSingleplayerServer());
			return;
		}
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.graphicsMode().set(GraphicsStatus.FAST);
			mc.options.framerateLimit().set(15);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 120) {
			shot(mc, "01_start");
		}
		if (tick == 140) {
			server.execute(() -> closeUp(server));
		}
		if (tick == 260) {
			shot(mc, "02_builder_closeup");
			server.execute(() -> camera(server, WIDE, 180, 22));
		}
		if (tick > 320 && doneAt < 0 && tick % FRAME_EVERY == 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(BuildSiteManager.get(server.overworld()).all().isEmpty()));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0) {
			int t = tick - doneAt;
			if (t == 40) {
				shot(mc, "03_finished_wide");
				server.execute(() -> camera(server, new Vec3(0.5, -56, 11.5), 180, 12));
			} else if (t == 140) {
				shot(mc, "04_cottage");
				server.execute(() -> camera(server, new Vec3(-12, -56, 9.5), 180, 10));
			} else if (t == 240) {
				shot(mc, "05_market_stall");
				server.execute(() -> camera(server, new Vec3(12.5, -52, 13.5), 180, 20));
			} else if (t == 340) {
				shot(mc, "06_lookout_tower");
			} else if (t == 360) {
				mc.stop();
			}
		}
		if (tick >= GIVE_UP_AT) {
			shot(mc, "99_timeout");
			mc.stop();
		}
	}

	// --- Blueprint Table scene ----------------------------------------------------------------

	private static final BlockPos TABLE = new BlockPos(0, -60, -3);

	private void tableScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.setDayTime(6000);
				level.setBlockAndUpdate(TABLE, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.setGameMode(GameType.CREATIVE);
				player.teleportTo(level, 0.5, -60, 0.5, 180, 30);
			});
		}
		if (tick == 80) {
			server.execute(() -> io.github.jcondedata.aliveworkplace.table.TableServer.open(server.getPlayerList().getPlayers().get(0), TABLE));
		}
		if (tick == 110 && mc.screen instanceof io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen screen) {
			screen.select(StarterBlueprints.STARTER_COTTAGE.id());
		}
		if (tick == 160) {
			shot(mc, "10_table_library");
			if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen screen) {
				screen.showFiles();
			}
		}
		if (tick == 200) {
			shot(mc, "11_table_upload");
			if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen screen) {
				screen.upload();
			}
		}
		if (tick == 280) {
			shot(mc, "12_table_uploaded");
		}
		if (tick == 300) {
			if (mc.screen == null) {
				shot(mc, "99_no_screen");
			}
			mc.stop();
		}
	}

	// --- See-through preview + status above the builder's head -----------------------------------

	private void previewScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.graphicsMode().set(GraphicsStatus.FAST);
			mc.options.framerateLimit().set(15);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				GameRules rules = level.getGameRules();
				rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
				rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				rules.getRule(ModGameRules.BUILD_DELAY).set(4, server);
				level.setDayTime(2500);
				BlueprintData.Placement placement = site(level, StarterBlueprints.STARTER_COTTAGE, new BlockPos(0, -60, 0));
				// The player holds the same blueprint: ghosts show what is still to be built.
				Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
				ItemStack held = BlueprintItem.create(blueprint.id(), blueprint.size());
				held.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
					BlueprintItem.data(held).orElseThrow().withPlacement(java.util.Optional.of(placement)));
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().setItem(player.getInventory().selected, held);
				hover(player, new Vec3(7.5, -55, 9.5), 145, 22);
			});
		}
		if (tick == 140) {
			shot(mc, "20_preview_start");
		}
		if (tick == 900) {
			shot(mc, "21_preview_half_built");
		}
		if (tick == 920) {
			server.execute(() -> {
				if (builders.isEmpty()) {
					return;
				}
				Villager v = builders.get(0);
				v.setNoAi(true);
				Vec3 eye = v.getEyePosition();
				Vec3 cam = eye.add(2.2, 0.9, 3.2);
				Vec3 d = eye.add(0, 0.8, 0).subtract(cam);
				float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
				hover(server.getPlayerList().getPlayers().get(0), cam, yaw, pitch);
			});
		}
		if (tick == 1000) {
			shot(mc, "22_status_closeup");
		}
		if (tick == 1020) {
			mc.stop();
		}
	}

	private static void hover(ServerPlayer player, Vec3 pos, float yaw, float pitch) {
		player.setGameMode(GameType.CREATIVE);
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, yaw, pitch);
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {
		});
	}

	// --- server-side scene ------------------------------------------------------------------

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		GameRules rules = level.getGameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		rules.getRule(ModGameRules.BUILD_DELAY).set(4, server);
		level.setDayTime(2500);
		level.setWeatherParameters(12000, 0, false, false);

		site(level, StarterBlueprints.STARTER_COTTAGE, new BlockPos(0, -60, 0));
		site(level, StarterBlueprints.MARKET_STALL, new BlockPos(-12, -60, 0));
		site(level, StarterBlueprints.LOOKOUT_TOWER, new BlockPos(12, -60, 0));
		camera(server, WIDE, 180, 22);
	}

	private BlueprintData.Placement site(ServerLevel level, StarterBlueprints.Entry entry, BlockPos anchor) {
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), anchor,
			BlueprintItem.rotationFacing(Direction.SOUTH));
		BuildPlan plan = BuildPlan.create(blueprint, placement);

		BlockPos bench = anchor.offset(3, 0, 4);
		level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int b = 0; b * 27 < stock.size(); b++) {
			BlockPos barrelPos = bench.offset(1 + b, 0, 0);
			level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
			BaseContainerBlockEntity barrel = (BaseContainerBlockEntity) level.getBlockEntity(barrelPos);
			for (int slot = 0; slot < 27 && b * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(b * 27 + slot));
			}
		}

		Villager villager = EntityType.VILLAGER.spawn(level, anchor.offset(0, 0, 3), MobSpawnType.COMMAND);
		if (villager == null) {
			return placement;
		}
		Builders.employ(level, villager, bench);
		Builders.start(level, villager, level.getServer().getPlayerList().getPlayers().get(0), entry.id(), placement);
		builders.add(villager);
		return placement;
	}

	private void closeUp(MinecraftServer server) {
		if (builders.isEmpty()) {
			return;
		}
		Villager v = builders.get(0);
		Vec3 eye = v.getEyePosition();
		Vec3 cam = eye.add(1.6, 0.4, 2.4);
		Vec3 d = eye.subtract(cam);
		float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
		v.setNoAi(true); // hold still for the portrait
		camera(server, cam, yaw, pitch);
		server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 130, () -> v.setNoAi(false)));
	}

	private static void camera(MinecraftServer server, Vec3 pos, float yaw, float pitch) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		player.setGameMode(GameType.SPECTATOR);
		player.teleportTo(server.overworld(), pos.x, pos.y, pos.z, yaw, pitch);
	}
}
