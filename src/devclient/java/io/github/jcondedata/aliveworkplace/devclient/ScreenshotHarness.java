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
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.BlockState;
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
		if ("quarry".equals(System.getProperty("aliveworkplace.scene"))) {
			quarryScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("guard".equals(System.getProperty("aliveworkplace.scene"))) {
			guardScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("mail".equals(System.getProperty("aliveworkplace.scene"))) {
			mailScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("farm".equals(System.getProperty("aliveworkplace.scene"))) {
			farmScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("forest".equals(System.getProperty("aliveworkplace.scene"))) {
			forestScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("village".equals(System.getProperty("aliveworkplace.scene"))) {
			villageScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("gallery".equals(System.getProperty("aliveworkplace.scene"))) {
			galleryScene(mc, mc.getSingleplayerServer());
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

	// --- Quarry: a miner digs a block of stone and ore out, layer by layer ------------------------

	private io.github.jcondedata.aliveworkplace.mine.QuarrySite quarry;

	private void quarryScene(Minecraft mc, MinecraftServer server) {
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
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, server);
				level.setDayTime(2500);
				java.util.Random rnd = new java.util.Random(4);
				BlockPos min = new BlockPos(-3, -60, -12);
				BlockPos max = new BlockPos(4, -55, -5);
				for (BlockPos p : BlockPos.betweenClosed(min, max)) {
					int r = rnd.nextInt(100);
					level.setBlock(p, (r < 4 ? Blocks.COAL_ORE : r < 6 ? Blocks.IRON_ORE : r < 7 ? Blocks.COPPER_ORE : r < 30 ? Blocks.ANDESITE : Blocks.STONE)
						.defaultBlockState(), 2);
				}
				BlockPos bench = new BlockPos(0, -60, 2);
				level.setBlockAndUpdate(bench, ModBlocks.MINERS_BENCH.defaultBlockState());
				level.setBlockAndUpdate(bench.east(), Blocks.CHEST.defaultBlockState());
				level.setBlockAndUpdate(bench.east(2), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(bench.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
				chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.TORCH, 16));
				Villager miner = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, miner, bench);
				quarry = io.github.jcondedata.aliveworkplace.mine.Miners.start(level, miner, server.getPlayerList().getPlayers().get(0),
					net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(min, max), 6);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(10.5, -50, 5.5), 135, 30);
			});
		}
		if (tick > 60 && tick % 60 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(quarry != null && io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(server.overworld()).get(quarry.id()) == null));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick == doneAt + 40) {
			shot(mc, "50_quarry_done");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			shot(mc, "99_timeout");
			mc.stop();
		}
	}

	// --- Forest: a lumberjack cuts and replants a few trees -----------------------------------------

	private Villager lumberjack;
	private static final int FOREST_TREES = 4;

	private void forestScene(Minecraft mc, MinecraftServer server) {
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
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, server);
				level.setDayTime(2500);
				var features = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
				var kinds = List.of(net.minecraft.data.worldgen.features.TreeFeatures.OAK, net.minecraft.data.worldgen.features.TreeFeatures.BIRCH,
					net.minecraft.data.worldgen.features.TreeFeatures.SPRUCE, net.minecraft.data.worldgen.features.TreeFeatures.OAK);
				BlockPos[] spots = {new BlockPos(-6, -60, -6), new BlockPos(3, -60, -9), new BlockPos(9, -60, -3), new BlockPos(-4, -60, 5)};
				net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7);
				for (int i = 0; i < spots.length; i++) {
					features.getHolderOrThrow(kinds.get(i)).value().place(level, level.getChunkSource().getGenerator(), random, spots[i]);
				}
				BlockPos block = new BlockPos(1, -60, 1);
				level.setBlockAndUpdate(block, ModBlocks.CHOPPING_BLOCK.defaultBlockState());
				level.setBlockAndUpdate(block.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(block.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_AXE));
				lumberjack = EntityType.VILLAGER.spawn(level, block.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, lumberjack, block,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHOPPING_BLOCK_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.LUMBERJACK);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(11.5, -53, 12.5), 140, 28);
			});
		}
		if (tick > 60 && tick % 10 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(lumberjack != null
				&& lumberjack.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.TREES_FELLED, 0) >= FOREST_TREES));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick < doneAt + 100 && tick % 10 == 0) {
			shot(mc, String.format("frame_%03d", frame++)); // the last sapling goes in
		}
		if (doneAt > 0 && tick == doneAt + 200) {
			shot(mc, "50_forest_done");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			shot(mc, "99_timeout");
			mc.stop();
		}
	}

	// --- Guard: a guard gears up and fights off three husks ------------------------------------------

	private final List<net.minecraft.world.entity.Entity> husks = new ArrayList<>();

	private void guardScene(Minecraft mc, MinecraftServer server) {
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
				level.setDayTime(2500);
				BlockPos post = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(post.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(post.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
				Villager guard = EntityType.VILLAGER.spawn(level, post.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, guard, post,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(7.5, -55, 9.5), 145, 28);
			});
		}
		if (tick == 140) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				for (BlockPos p : List.of(new BlockPos(-10, -60, -8), new BlockPos(-12, -60, -4), new BlockPos(-8, -60, -12))) {
					husks.add(EntityType.HUSK.spawn(level, p, MobSpawnType.COMMAND));
				}
			});
		}
		if (tick > 100 && tick % 5 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(!husks.isEmpty() && husks.stream().noneMatch(net.minecraft.world.entity.Entity::isAlive)));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick < doneAt + 60 && tick % 5 == 0) {
			shot(mc, String.format("frame_%03d", frame++));
		}
		if (doneAt > 0 && tick == doneAt + 80) {
			shot(mc, "50_guard_done");
			mc.stop();
		}
		if (tick >= 3000) {
			shot(mc, "99_timeout");
			mc.stop();
		}
	}

	// --- Mail: the mailbox screen, then a postman carrying a parcel to a friend's mailbox --------------

	private java.util.UUID parcelId;

	private void mailScene(Minecraft mc, MinecraftServer server) {
		tick++;
		BlockPos mine = new BlockPos(3, -60, -2);
		BlockPos theirs = new BlockPos(-9, -60, -12);
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				var office = io.github.jcondedata.aliveworkplace.mail.PostOffice.get(server);
				level.setBlockAndUpdate(mine, ModBlocks.MAILBOX.defaultBlockState().setValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.FACING, Direction.SOUTH));
				var box = (io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity) level.getBlockEntity(mine);
				box.setOwner(player.getUUID(), player.getGameProfile().getName());
				office.register(player.getUUID(), net.minecraft.core.GlobalPos.of(level.dimension(), mine));
				box.receive(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 5));
				box.receive(new ItemStack(net.minecraft.world.item.Items.COOKED_SALMON, 12));
				box.receive(new ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK));
				java.util.UUID friend = java.util.UUID.nameUUIDFromBytes("friend".getBytes());
				level.setBlockAndUpdate(theirs, ModBlocks.MAILBOX.defaultBlockState().setValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.FACING, Direction.SOUTH));
				((io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity) level.getBlockEntity(theirs)).setOwner(friend, "Friend");
				office.register(friend, net.minecraft.core.GlobalPos.of(level.dimension(), theirs));
				BlockPos desk = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(desk, ModBlocks.POSTAL_DESK.defaultBlockState());
				Villager postman = EntityType.VILLAGER.spawn(level, desk.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, postman, desk,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.POSTAL_DESK_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.POSTMAN);
				hover(player, new Vec3(4.5, -58, 3.5), 170, 30); // within reach, or the menu closes at once
				player.openMenu(box);
			});
		}
		if (tick == 80 && mc.screen instanceof io.github.jcondedata.aliveworkplace.client.MailboxScreen) {
			for (var child : mc.screen.children()) {
				if (child instanceof net.minecraft.client.gui.components.EditBox edit) {
					edit.setValue("Friend");
				}
			}
		}
		if (tick == 100) {
			shot(mc, "01_mailbox_screen");
		}
		if (tick == 110) {
			mc.setScreen(null);
			mc.options.hideGui = true;
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				hover(player, new Vec3(8.5, -53, 6.5), 135, 32);
				java.util.UUID friend = java.util.UUID.nameUUIDFromBytes("friend".getBytes());
				parcelId = io.github.jcondedata.aliveworkplace.mail.PostOffice.get(server).post(player.getUUID(), player.getGameProfile().getName(), friend,
					"Friend", net.minecraft.core.GlobalPos.of(level.dimension(), mine), level.getGameTime(),
					List.of(new ItemStack(net.minecraft.world.item.Items.CAKE))).id();
			});
		}
		if (tick > 120 && tick % 10 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(parcelId != null && io.github.jcondedata.aliveworkplace.mail.PostOffice.get(server).parcel(parcelId) == null));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick == doneAt + 40) {
			shot(mc, "50_mail_delivered");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			shot(mc, "99_timeout");
			mc.stop();
		}
	}

	// --- Farm: a farmer harvests, replants, tills and sows a marked field ----------------------------

	private Villager farmer;

	private void farmScene(Minecraft mc, MinecraftServer server) {
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
				level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
				level.setDayTime(2500);
				BlockPos water = new BlockPos(0, -61, -8);
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(-4, -61, -12), new BlockPos(4, -61, -4))) {
					if (p.equals(water)) {
						level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
					} else if (p.getZ() <= -7) {
						level.setBlock(p, Blocks.FARMLAND.defaultBlockState(), 2);
						var crop = p.getX() < 0 ? Blocks.WHEAT : Blocks.CARROTS;
						level.setBlock(p.above(), crop.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 2);
					}
				}
				BlockPos composter = new BlockPos(0, -60, 2);
				level.setBlockAndUpdate(composter, Blocks.COMPOSTER.defaultBlockState());
				level.setBlockAndUpdate(composter.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(composter.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.STONE_HOE));
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 16));
				farmer = EntityType.VILLAGER.spawn(level, composter.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, farmer, composter,
					net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER, net.minecraft.world.entity.npc.VillagerProfession.FARMER);
				io.github.jcondedata.aliveworkplace.farm.Fields.start(level, farmer,
					net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(new BlockPos(-4, -61, -12), new BlockPos(4, -61, -4)));
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(9.5, -53, 4.5), 140, 35);
			});
		}
		if (tick > 60 && tick % 10 == 0 && doneAt < 0) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(tick > 300 && farmer != null && io.github.jcondedata.aliveworkplace.farm.Fields.vanillaMayRun(farmer)));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick == doneAt + 40) {
			shot(mc, "50_farm_done");
			mc.stop();
		}
		if (tick >= GIVE_UP_AT) {
			shot(mc, "99_timeout");
			mc.stop();
		}
	}

	// --- Villages: one of each type, find the builder's workshops, one shot each ----------------

	private final List<BlockPos> workshops = new ArrayList<>();

	private void villageScene(Minecraft mc, MinecraftServer server) {
		tick++;
		List<String> styles = io.github.jcondedata.aliveworkplace.world.VillageHouses.STYLES;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.setDayTime(3000);
				for (int i = 0; i < styles.size(); i++) {
					int x = i * 256;
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
						"forceload add " + (x - 96) + " -96 " + (x + 96) + " 96");
				}
			});
		}
		if (tick == 400) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				for (int i = 0; i < styles.size(); i++) {
					int x = i * 256;
					net.minecraft.commands.CommandSource logger = new net.minecraft.commands.CommandSource() {
						@Override
						public void sendSystemMessage(net.minecraft.network.chat.Component message) {
							io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[village] command: {}", message.getString());
						}

						@Override
						public boolean acceptsSuccess() {
							return true;
						}

						@Override
						public boolean acceptsFailure() {
							return true;
						}

						@Override
						public boolean shouldInformAdmins() {
							return false;
						}
					};
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(logger),
						"place structure minecraft:village_" + styles.get(i) + " " + x + " -60 0");
					BlockPos found = null;
					int built = 0;
					for (BlockPos p : BlockPos.betweenClosed(x - 96, -62, -96, x + 96, -52, 96)) {
						BlockState st = level.getBlockState(p);
						if (st.is(ModBlocks.BUILDERS_BENCH) && found == null) {
							found = p.immutable();
						}
						if (st.is(Blocks.BELL) || st.is(net.minecraft.tags.BlockTags.BEDS)) {
							built++;
						}
					}
					io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[village] {} village: {} bells/beds, workshop bench at {}", styles.get(i), built, found);
					if (found != null) {
						workshops.add(found);
					}
				}
			});
		}
		int shots = workshops.size();
		if (tick >= 500 && (tick - 500) % 80 == 0 && (tick - 500) / 80 < shots) {
			BlockPos bench = workshops.get((tick - 500) / 80);
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(bench.getX() + 9.5, bench.getY() + 6, bench.getZ() + 9.5), 135, 25));
		}
		if (tick >= 560 && (tick - 560) % 80 == 0 && (tick - 560) / 80 < shots) {
			shot(mc, "40_workshop_" + ((tick - 560) / 80));
		}
		if (tick == 580 + Math.max(1, shots) * 80) {
			mc.stop();
		}
	}

	// --- Gallery: every starter blueprint placed instantly, one shot each -------------------------

	private void galleryScene(Minecraft mc, MinecraftServer server) {
		tick++;
		List<StarterBlueprints.Entry> all = StarterBlueprints.ALL;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.setDayTime(3000);
				for (int i = 0; i < all.size(); i++) {
					net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate t =
						level.getStructureManager().get(all.get(i).id()).orElseThrow();
					BlockPos origin = new BlockPos(i * 24, -60, 0);
					t.placeInWorld(level, origin, origin, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
						level.getRandom(), 2);
				}
			});
		}
		int index = (tick - 60) / 60;
		if (tick >= 60 && (tick - 60) % 60 == 0 && index < all.size()) {
			StarterBlueprints.Entry e = all.get(index);
			double cx = index * 24 + e.size().getX() / 2.0 + 0.5;
			double dist = Math.max(e.size().getX(), e.size().getY()) * 1.3 + 4;
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0),
				new Vec3(cx + dist * 0.45, -60 + e.size().getY() * 0.7 + 2, -dist), 20, 22));
		}
		if (tick >= 100 && (tick - 100) % 60 == 0 && (tick - 100) / 60 < all.size()) {
			shot(mc, "30_" + all.get((tick - 100) / 60).id().getPath());
		}
		if (tick == 30 || tick == 90) {
			// Materials tooltip: the first call asks the server, a later one has the answer.
			for (Component line : io.github.jcondedata.aliveworkplace.client.BlueprintTooltip.materialLines(StarterBlueprints.HEALING_CENTER.id())) {
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[tooltip] {}", line.getString());
			}
		}
		if (tick == 100 + all.size() * 60) {
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
