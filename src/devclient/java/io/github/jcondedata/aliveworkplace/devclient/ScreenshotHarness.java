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
		if ("staff".equals(System.getProperty("aliveworkplace.scene"))) {
			staffScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("guard".equals(System.getProperty("aliveworkplace.scene")) || "guard_pokemon".equals(System.getProperty("aliveworkplace.scene"))) {
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
		if ("tutor".equals(System.getProperty("aliveworkplace.scene"))) {
			tutorScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("trader".equals(System.getProperty("aliveworkplace.scene"))) {
			traderScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("missing".equals(System.getProperty("aliveworkplace.scene"))) {
			missingScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("battle".equals(System.getProperty("aliveworkplace.scene"))) {
			battleScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("smith".equals(System.getProperty("aliveworkplace.scene"))) {
			smithScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("chef".equals(System.getProperty("aliveworkplace.scene"))) {
			chefScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("carpenter".equals(System.getProperty("aliveworkplace.scene"))) {
			carpenterScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("camp".equals(System.getProperty("aliveworkplace.scene"))) {
			campScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("hall".equals(System.getProperty("aliveworkplace.scene"))) {
			hallScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("porter".equals(System.getProperty("aliveworkplace.scene"))) {
			porterScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("shop".equals(System.getProperty("aliveworkplace.scene"))) {
			shopScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("orchard".equals(System.getProperty("aliveworkplace.scene"))) {
			orchardScene(mc, mc.getSingleplayerServer());
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
		if ("gallery".equals(System.getProperty("aliveworkplace.scene")) || "decor".equals(System.getProperty("aliveworkplace.scene"))
			|| "styles".equals(System.getProperty("aliveworkplace.scene")) || "defences".equals(System.getProperty("aliveworkplace.scene"))
			|| "workshops".equals(System.getProperty("aliveworkplace.scene"))) {
			galleryScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("fish".equals(System.getProperty("aliveworkplace.scene"))) {
			fishScene(mc, mc.getSingleplayerServer());
			return;
		}
		if ("extras".equals(System.getProperty("aliveworkplace.scene"))) {
			extrasScene(mc, mc.getSingleplayerServer());
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
				// A low hill around the quarry, so the pit is cut into the ground (and the stairs down its walls show).
				for (BlockPos p : BlockPos.betweenClosed(min.offset(-3, 0, -3), max.offset(3, 0, 3))) {
					level.setBlock(p, (p.getY() == max.getY() ? Blocks.GRASS_BLOCK : p.getY() >= max.getY() - 2 ? Blocks.DIRT : Blocks.STONE)
						.defaultBlockState(), 2);
				}
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
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(-8.5, -47, -18.5), -45, 38);
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

	// --- Smith: a Ball Smith at the Ball Workbench, next to an Orchard Keeper's basket ----------------------

	private void smithScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doPokemonSpawning false");
				level.setDayTime(2500);
				BlockPos bench = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(bench, ModBlocks.BALL_WORKBENCH.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(bench.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(bench.east());
				chest.setItem(0, new ItemStack(com.cobblemon.mod.common.CobblemonItems.RED_APRICORN, 32));
				chest.setItem(1, new ItemStack(com.cobblemon.mod.common.CobblemonItems.BLUE_APRICORN, 16));
				chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 8));
				chest.setItem(3, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));
				Villager smith = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, smith, bench,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.BALL_WORKBENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BALL_SMITH);
				BlockPos basket = new BlockPos(-3, -60, 0);
				level.setBlockAndUpdate(basket, ModBlocks.FRUIT_BASKET.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				Villager keeper = EntityType.VILLAGER.spawn(level, basket.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, keeper, basket,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.FRUIT_BASKET_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.ORCHARD_KEEPER);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(-0.5, -58.3, 5.5), 180, 12);
			});
		}
		if (tick == 330) {
			shot(mc, "01_smith_working");
		}
		if (tick == 340) {
			mc.stop();
		}
	}

	// --- Chef: cooking at the Kitchen Stove -----------------------------------------------------------

	private void chefScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos stove = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(stove, ModBlocks.KITCHEN_STOVE.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(stove.east(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(stove.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(stove.east());
				chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.WHEAT, 24));
				chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.BEEF, 8));
				chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.POTATO, 8));
				chest.setItem(3, new ItemStack(net.minecraft.world.item.Items.COD, 6));
				Villager chef = EntityType.VILLAGER.spawn(level, stove.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, chef, stove,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.KITCHEN_STOVE_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHEF);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -58.3, 5.0), 180, 14);
			});
		}
		if (tick >= 100 && tick <= 700 && tick % 50 == 0) {
			shot(mc, String.format("%02d_chef", tick / 50));
		}
		if (tick == 710) {
			mc.stop();
		}
	}

	// --- Carpenter: making what a builder is waiting for ----------------------------------------------

	private void carpenterScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos bench = new BlockPos(-4, -60, -2);
				level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
				level.setBlockAndUpdate(bench.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(bench.west());
				// Everything for a Market Stall but the woodwork: spruce logs instead.
				Object[][] stock = {{net.minecraft.world.item.Items.SPRUCE_LOG, 20}, {net.minecraft.world.item.Items.MELON, 1}, {net.minecraft.world.item.Items.PUMPKIN, 1},
					{net.minecraft.world.item.Items.LANTERN, 1}, {net.minecraft.world.item.Items.HAY_BLOCK, 2}, {net.minecraft.world.item.Items.RED_WOOL, 20},
					{net.minecraft.world.item.Items.WHITE_WOOL, 15}};
				for (int i = 0; i < stock.length; i++) {
					chest.setItem(i, new ItemStack((net.minecraft.world.item.Item) stock[i][0], (Integer) stock[i][1]));
				}
				Villager builder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				Builders.employ(level, builder, bench);
				Builders.start(level, builder, null, io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.MARKET_STALL.id(),
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), new BlockPos(-4, -60, -12),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE));
				BlockPos carpenters = new BlockPos(3, -60, -2);
				level.setBlockAndUpdate(carpenters, ModBlocks.CARPENTERS_BENCH.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				Villager carpenter = EntityType.VILLAGER.spawn(level, carpenters.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, carpenter, carpenters,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.CARPENTERS_BENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CARPENTER);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(-0.5, -54.5, 5.5), 180, 32);
			});
		}
		if (tick >= 60 && tick <= 1500 && tick % 60 == 0) {
			shot(mc, String.format("%02d_carpenter", tick / 60));
		}
		if (tick == 1510) {
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(3.5, -58.3, 2.5), 180, 20));
		}
		if (tick == 1560) {
			shot(mc, "30_carpenter_closeup");
			mc.stop();
		}
	}

	// --- Village Hall: the village at a glance ----------------------------------------------------------

	// --- Camp: a Settler's Wagon used on open ground --------------------------------------------------

	private void campScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(12600);
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				hover(player, new Vec3(0.5, -57.5, -6.5), 0, 20);
				io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem.makeCamp(level, player, new BlockPos(0, -60, 0));
			});
		}
		if (tick == 60) {
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0), new Vec3(-7.5, -55.5, -6.5), new Vec3(0.5, -59, 4.5)));
		}
		if (tick == 140) {
			shot(mc, "01_camp");
		}
		if (tick == 150) {
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0), new Vec3(8.5, -56.5, 12.5), new Vec3(0.5, -59, 4.5)));
		}
		if (tick == 220) {
			shot(mc, "02_camp_back");
			mc.stop();
		}
	}

	private void hallScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos hall = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				// A builder waiting for materials, a lumberjack without an axe, a porter with food in the store, a guard, a
				// rancher, a florist, one villager without a job and a child; three beds.
				BlockPos builderBench = new BlockPos(-8, -60, -6);
				level.setBlockAndUpdate(builderBench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
				Villager builder = EntityType.VILLAGER.spawn(level, builderBench.south(), MobSpawnType.COMMAND);
				Builders.employ(level, builder, builderBench);
				Builders.start(level, builder, null, StarterBlueprints.MARKET_STALL.id(),
					new BlueprintData.Placement(level.dimension().location(), new BlockPos(-18, -60, -16),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE));
				BlockPos chopping = new BlockPos(8, -60, -6);
				level.setBlockAndUpdate(chopping, ModBlocks.CHOPPING_BLOCK.defaultBlockState());
				level.setBlockAndUpdate(chopping.east(), Blocks.CHEST.defaultBlockState());
				Villager lumberjack = EntityType.VILLAGER.spawn(level, chopping.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, lumberjack, chopping,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHOPPING_BLOCK_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.LUMBERJACK);
				BlockPos storehouse = new BlockPos(10, -60, 8);
				level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState());
				level.setBlockAndUpdate(storehouse.east(), Blocks.CHEST.defaultBlockState());
				BaseContainerBlockEntity store = (BaseContainerBlockEntity) level.getBlockEntity(storehouse.east());
				store.setItem(0, new ItemStack(net.minecraft.world.item.Items.BREAD, 32));
				store.setItem(1, new ItemStack(net.minecraft.world.item.Items.BAKED_POTATO, 18));
				store.setItem(2, new ItemStack(net.minecraft.world.item.Items.APPLE, 9));
				Villager porter = EntityType.VILLAGER.spawn(level, storehouse.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.store.Porters.employ(level, porter, storehouse);
				employ(level, new BlockPos(-10, -60, 8), ModBlocks.GUARD_POST, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
				employ(level, new BlockPos(-4, -60, 14), ModBlocks.FEED_TROUGH, io.github.jcondedata.aliveworkplace.registry.ModVillagers.FEED_TROUGH_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.RANCHER);
				employ(level, new BlockPos(5, -60, 14), ModBlocks.FLOWER_STAND, io.github.jcondedata.aliveworkplace.registry.ModVillagers.FLOWER_STAND_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.FLORIST);
				EntityType.VILLAGER.spawn(level, new BlockPos(2, -60, 6), MobSpawnType.COMMAND);
				Villager child = EntityType.VILLAGER.spawn(level, new BlockPos(-2, -60, 6), MobSpawnType.COMMAND);
				child.setAge(-24000);
				for (int x = -3; x <= 1; x += 2) {
					level.setBlockAndUpdate(new BlockPos(x, -60, -2), Blocks.RED_BED.defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
					level.setBlockAndUpdate(new BlockPos(x, -60, -1), Blocks.RED_BED.defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
				}
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -58.3, 7.5), 180, 20);
			});
		}
		if (tick == 190) {
			// The hall's first round: everyone gets a name.
			server.execute(() -> io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(server.overworld(), new BlockPos(0, -60, 3)));
		}
		if (tick == 200) {
			shot(mc, "01_hall_block");
			server.execute(() -> io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(server.getPlayerList().getPlayers().get(0), new BlockPos(0, -60, 3)));
		}
		if (tick == 220) {
			pointAt(mc, 1);
		}
		if (tick == 230) {
			shot(mc, "02_hall_people");
			pointAt(mc, 18);
		}
		if (tick == 245) {
			shot(mc, "03_hall_builder");
			pointAt(mc, 5);
		}
		if (tick == 260) {
			shot(mc, "04_hall_wellbeing");
			pointAt(mc, 6);
		}
		if (tick == 275) {
			shot(mc, "05_hall_requests");
			pointAt(mc, 19);
		}
		if (tick == 290) {
			shot(mc, "06_hall_worker");
			mc.stop();
		}
	}

	private static void employ(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block station,
							   net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
							   net.minecraft.world.entity.npc.VillagerProfession job) {
		level.setBlockAndUpdate(pos, station.defaultBlockState());
		level.setBlockAndUpdate(pos.east(), Blocks.CHEST.defaultBlockState());
		Villager villager = EntityType.VILLAGER.spawn(level, pos.south(), MobSpawnType.COMMAND);
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, villager, pos, poi, job);
	}

	// --- Porter: carrying a miner's goods to the storehouse ------------------------------------------

	private void porterScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos bench = new BlockPos(-5, -60, 0);
				level.setBlockAndUpdate(bench, ModBlocks.MINERS_BENCH.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(bench.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(bench.west());
				for (int i = 0; i < 6; i++) {
					chest.setItem(i, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
				}
				chest.setItem(6, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 24));
				chest.setItem(7, new ItemStack(net.minecraft.world.item.Items.COAL, 30));
				chest.setItem(8, new ItemStack(net.minecraft.world.item.Items.TORCH, 16));
				Villager miner = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				miner.setNoAi(true);
				io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, miner, bench);
				BlockPos storehouse = new BlockPos(5, -60, 0);
				level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.store.StorehouseBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(storehouse.east(), Blocks.BARREL.defaultBlockState()
					.setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
				level.setBlockAndUpdate(storehouse.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
				Villager porter = EntityType.VILLAGER.spawn(level, storehouse.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.store.Porters.employ(level, porter, storehouse);
				// A builder behind, with nothing to build with: what they're missing goes on the storehouse's board.
				BlockPos builderBench = new BlockPos(-2, -60, -10);
				level.setBlockAndUpdate(builderBench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
				Villager builder = EntityType.VILLAGER.spawn(level, builderBench.south(), MobSpawnType.COMMAND);
				Builders.employ(level, builder, builderBench);
				Builders.start(level, builder, null, io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.MARKET_STALL.id(),
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), new BlockPos(-12, -60, -20),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE));
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 64));
				player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.OAK_FENCE, 12));
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -57.0, 8.5), 180, 20);
			});
		}
		if (tick >= 50 && tick <= 350 && tick % 25 == 0) {
			shot(mc, String.format("%02d_porter", tick / 25));
		}
		if (tick == 360) {
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(5.5, -58.3, 4.5), 180, 20));
		}
		if (tick == 420) {
			shot(mc, "20_storehouse_closeup");
			mc.options.hideGui = false;
			server.execute(() -> io.github.jcondedata.aliveworkplace.store.StorehouseBoard.open(server.getPlayerList().getPlayers().get(0), new BlockPos(5, -60, 0)));
		}
		if (tick == 470) {
			shot(mc, "21_request_board");
			mc.stop();
		}
	}

	// --- Shop: the shop screen with CobbleDollars prices ---------------------------------------------

	private void shopScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos counterPos = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(counterPos, ModBlocks.SHOP_COUNTER.defaultBlockState());
				level.setBlockAndUpdate(counterPos.east(), Blocks.CHEST.defaultBlockState());
				var counter = (io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity) level.getBlockEntity(counterPos);
				counter.setOwner(java.util.UUID.randomUUID(), "Jesse");
				int columns = io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity.COLUMNS;
				Object[][] stock = {
					{new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 16), 2}, {new ItemStack(net.minecraft.world.item.Items.BREAD, 6), 1},
					{new ItemStack(net.minecraft.world.item.Items.TORCH, 32), 1}, {new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 8), 5},
					{new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 1), 12}};
				BaseContainerBlockEntity chest = (BaseContainerBlockEntity) level.getBlockEntity(counterPos.east());
				for (int i = 0; i < stock.length; i++) {
					ItemStack goods = (ItemStack) stock[i][0];
					counter.setItem(i, goods.copy());
					counter.setItem(columns + i, new ItemStack(net.minecraft.world.item.Items.EMERALD, (Integer) stock[i][1]));
					chest.setItem(i, goods.copyWithCount(Math.min(64, goods.getCount() * 4)));
				}
				Villager keeper = EntityType.VILLAGER.spawn(level, counterPos.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, keeper, counterPos,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOP_COUNTER_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOPKEEPER);
				player.setGameMode(GameType.SURVIVAL);
				io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 1500);
				player.teleportTo(level, 2.5, -60, 6.5, 135, 20);
				io.github.jcondedata.aliveworkplace.shop.Shops.openMenu(player, keeper);
			});
		}
		if (tick == 90) {
			mc.getToasts().clear();
			pointAt(mc, io.github.jcondedata.aliveworkplace.shop.Shops.FIRST_GOODS_SLOT + 3);
		}
		if (tick == 100) {
			shot(mc, "01_shop_menu");
		}
		if (tick == 110) {
			mc.stop();
		}
	}

	// --- Orchard: an orchard keeper picks berries, cocoa and (with Cobblemon) apricorns and berry plants -----

	private Villager keeper;
	private int orchardFruit;

	private static BlockState cobblemonBlock(String id) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", id))
			.map(net.minecraft.world.level.block.Block::defaultBlockState).orElse(null);
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, String property, T value) {
		for (var p : state.getProperties()) {
			if (p.getName().equals(property)) {
				@SuppressWarnings("unchecked")
				var typed = (net.minecraft.world.level.block.state.properties.Property<T>) p;
				return state.setValue(typed, value);
			}
		}
		return state;
	}

	private void orchardScene(Minecraft mc, MinecraftServer server) {
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
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(6, server);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doPokemonSpawning false");
				level.setDayTime(2500);
				int fruit = 0;
				// A hedge of sweet berry bushes.
				for (int x = -5; x <= -1; x++) {
					level.setBlockAndUpdate(new BlockPos(x, -60, 5), Blocks.SWEET_BERRY_BUSH.defaultBlockState()
						.setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 3));
					fruit++;
				}
				// A jungle trunk with cocoa pods.
				for (int y = -60; y <= -56; y++) {
					level.setBlockAndUpdate(new BlockPos(-7, y, -2), Blocks.JUNGLE_LOG.defaultBlockState());
				}
				for (int y = -59; y <= -57; y++) {
					level.setBlockAndUpdate(new BlockPos(-6, y, -2), Blocks.COCOA.defaultBlockState()
						.setValue(net.minecraft.world.level.block.CocoaBlock.FACING, Direction.WEST).setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 2));
					fruit++;
				}
				// With Cobblemon: an apricorn tree and a bed of berry plants.
				BlockState leaves = cobblemonBlock("apricorn_leaves");
				if (leaves != null) {
					leaves = with(leaves, "persistent", true);
					for (int y = -60; y <= -56; y++) {
						level.setBlockAndUpdate(new BlockPos(6, y, -2), cobblemonBlock("apricorn_log"));
					}
					for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, -56, -3), new BlockPos(7, -55, -1))) {
						if (!(p.getX() == 6 && p.getZ() == -2 && p.getY() == -56)) {
							level.setBlockAndUpdate(p, leaves);
						}
					}
					level.setBlockAndUpdate(new BlockPos(6, -54, -2), leaves);
					Object[][] apricorns = {{"red_apricorn", new BlockPos(4, -56, -2), Direction.EAST}, {"yellow_apricorn", new BlockPos(8, -56, -2), Direction.WEST},
						{"blue_apricorn", new BlockPos(6, -56, 0), Direction.NORTH}, {"pink_apricorn", new BlockPos(5, -56, 0), Direction.NORTH}};
					for (Object[] a : apricorns) {
						BlockState state = with(with(cobblemonBlock((String) a[0]), "facing", (Direction) a[2]), "age", 3);
						level.setBlockAndUpdate((BlockPos) a[1], state);
						fruit++;
					}
					String[] berries = {"oran_berry", "pecha_berry", "cheri_berry"};
					for (int i = 0; i < berries.length; i++) {
						BlockPos p = new BlockPos(3 + i, -60, 5);
						level.setBlockAndUpdate(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
						level.setBlockAndUpdate(p, with(cobblemonBlock(berries[i]), "age", 5));
						if (level.getBlockEntity(p) instanceof com.cobblemon.mod.common.block.entity.BerryBlockEntity plant) {
							plant.generateSimpleYields();
						}
						fruit++;
					}
				}
				orchardFruit = fruit;
				BlockPos basket = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(basket, ModBlocks.FRUIT_BASKET.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(basket.east(), Blocks.CHEST.defaultBlockState());
				keeper = EntityType.VILLAGER.spawn(level, basket.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, keeper, basket,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.FRUIT_BASKET_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.ORCHARD_KEEPER);
				// A Bulbasaur in a pasture nearby helps (a Pokémon partner).
				if (leaves != null) {
					BlockPos pasture = new BlockPos(-3, -60, -2);
					level.setBlockAndUpdate(pasture, with(with(cobblemonBlock("pasture"), "waterlogged", false), "part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
					level.setBlockAndUpdate(pasture.above(), with(with(cobblemonBlock("pasture"), "waterlogged", false), "part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
					ServerPlayer player = server.getPlayerList().getPlayers().get(0);
					var bulbasaur = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("bulbasaur level=12", " ", "=").create();
					com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(bulbasaur);
					if (level.getBlockEntity(pasture) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
						pen.tether(player, bulbasaur, Direction.SOUTH);
					}
				}
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -55.5, 10.5), 180, 24);
			});
		}
		if (tick == 80) {
			shot(mc, "10_orchard_start");
		}
		if (tick == 150) {
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), keeper.position().add(0, 1.2, 3.5), 180, 8));
		}
		if (tick == 175) {
			shot(mc, "20_orchard_partner");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(0.5, -55.5, 10.5), 180, 24));
		}
		if (tick > 60 && tick % 10 == 0 && doneAt < 0 && (tick < 150 || tick > 185)) {
			shot(mc, String.format("frame_%03d", frame++));
			server.execute(() -> allDone.set(keeper != null
				&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.FRUIT_PICKED.getOrElse(keeper, 0) >= orchardFruit));
			if (allDone.get()) {
				doneAt = tick;
			}
		}
		if (doneAt > 0 && tick < doneAt + 200 && tick % 10 == 0) {
			shot(mc, String.format("frame_%03d", frame++)); // the harvest goes to the chest
		}
		if (doneAt > 0 && tick == doneAt + 200) {
			shot(mc, "50_orchard_done");
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
				&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.TREES_FELLED.getOrElse(lumberjack, 0) >= FOREST_TREES));
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

	// --- Staff: every workstation with its villager, for a look at the textures ------------------------

	private void staffScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				var V = io.github.jcondedata.aliveworkplace.registry.ModVillagers.class;
				Object[][] staff = {
					{ModBlocks.BUILDERS_BENCH, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDERS_BENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER},
					{ModBlocks.MINERS_BENCH, io.github.jcondedata.aliveworkplace.registry.ModVillagers.MINERS_BENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.MINER},
					{ModBlocks.CHOPPING_BLOCK, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHOPPING_BLOCK_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.LUMBERJACK},
					{ModBlocks.POSTAL_DESK, io.github.jcondedata.aliveworkplace.registry.ModVillagers.POSTAL_DESK_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.POSTMAN},
					{ModBlocks.GUARD_POST, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD},
					{ModBlocks.NURSE_STATION, io.github.jcondedata.aliveworkplace.registry.ModVillagers.NURSE_STATION_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.NURSE},
					{ModBlocks.SHOP_COUNTER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOP_COUNTER_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOPKEEPER},
					{ModBlocks.TRAVEL_POST, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAVEL_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.FERRYMAN},
				};
				for (int i = 0; i < staff.length; i++) {
					BlockPos block = new BlockPos(i * 3 - 10, -60, 0);
					net.minecraft.world.level.block.Block b = (net.minecraft.world.level.block.Block) staff[i][0];
					BlockState state = b.defaultBlockState();
					if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
						state = state.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH);
					}
					level.setBlockAndUpdate(block, state);
					Villager v = EntityType.VILLAGER.spawn(level, block.south(2), MobSpawnType.COMMAND);
					v.setNoAi(true);
					v.setYRot(0);
					v.setYHeadRot(0);
					io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, v, block,
						(net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType>) staff[i][1],
						(net.minecraft.world.entity.npc.VillagerProfession) staff[i][2]);
				}
				BlockPos mailbox = new BlockPos(14, -60, 0);
				level.setBlockAndUpdate(mailbox, ModBlocks.MAILBOX.defaultBlockState().setValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.FACING, Direction.SOUTH)
					.setValue(io.github.jcondedata.aliveworkplace.mail.MailboxBlock.HAS_MAIL, true));
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(1.5, -58.2, 9.5), 180, 8);
			});
		}
		if (tick == 140) {
			shot(mc, "01_staff");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(1.5, -55.5, 7.5), 180, 30));
		}
		if (tick == 200) {
			shot(mc, "02_staff_above");
			mc.stop();
		}
	}

	// --- Guard: a guard gears up and fights off three husks ------------------------------------------

	private final List<net.minecraft.world.entity.Entity> husks = new ArrayList<>();

	private Villager guardForShot;
	private int trainedAt = -1;

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
				guardForShot = guard;
				guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
				guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
				guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
				guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.CHAINMAIL_LEGGINGS));
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, guard, post,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
				// SCENE=guard_pokemon: a Machop and a Dratini in a pasture by the post fight beside the guard.
				if ("guard_pokemon".equals(System.getProperty("aliveworkplace.scene"))) {
					BlockPos pasture = new BlockPos(-3, -60, 2);
					level.setBlockAndUpdate(pasture, with(with(cobblemonBlock("pasture"), "waterlogged", false), "part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
					level.setBlockAndUpdate(pasture.above(), with(with(cobblemonBlock("pasture"), "waterlogged", false), "part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
					ServerPlayer player = server.getPlayerList().getPlayers().get(0);
					for (String species : List.of("machop level=30", "dratini level=30")) {
						var pokemon = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(species, " ", "=").create();
						com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
						if (level.getBlockEntity(pasture) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
							pen.tether(player, pokemon, species.startsWith("machop") ? Direction.NORTH : Direction.WEST);
						}
					}
				}
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(7.5, -55, 9.5), 145, 28);
			});
		}
		if (tick == 110 && guardForShot != null) {
			// A close look at the guard in their armor, before the husks come.
			server.execute(() -> {
				Villager g = guardForShot;
				// Slimes from the superflat world's slime chunks would draw the guard away.
				for (net.minecraft.world.entity.monster.Slime slime : g.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
					g.getBoundingBox().inflate(64))) {
					slime.discard();
				}
				g.setTarget(null);
				g.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
				g.getNavigation().stop();
				g.setNoAi(true);
				g.setYRot(-34.5f);
				g.setYHeadRot(-34.5f);
				g.setYBodyRot(-34.5f);
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(g.getX() + 2.2, g.getY() + 1.9, g.getZ() + 3.2), 146, 18);
			});
		}
		if (tick == 125) {
			shot(mc, "10_guard_armor");
			server.execute(() -> {
				guardForShot.setYRot(60f);
				guardForShot.setYHeadRot(60f);
				guardForShot.setYBodyRot(60f);
			});
		}
		if (tick == 132) {
			shot(mc, "11_guard_armor_side");
			server.execute(() -> {
				guardForShot.setNoAi(false);
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
		if (tick >= 150 && tick % 20 == 0 && "guard_pokemon".equals(System.getProperty("aliveworkplace.scene")) && guardForShot != null) {
			// Follow the guard from close by, to see the Pokémon's moves land.
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), guardForShot.position().add(4, 2.5, 5), 141, 20));
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
			// Then a Training Dummy by the post: the guard spars with it.
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.setBlockAndUpdate(new BlockPos(3, -60, 3), ModBlocks.TRAINING_DUMMY.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
				// First a close look at the dummy itself, from the front.
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(4.3, -58.6, 5.4), 150, 20);
			});
		}
		if (doneAt > 0 && tick == doneAt + 88) {
			shot(mc, "55_training_dummy");
			server.execute(() -> hover(server.getPlayerList().getPlayers().get(0), new Vec3(7.5, -58.2, 4.2), 100, 16));
		}
		if (doneAt > 0 && tick > doneAt + 80 && tick % 5 == 0 && trainedAt < 0 && guardForShot != null
			&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.DUMMY_HITS.getOrElse(guardForShot, 0) >= 2) {
			trainedAt = tick;
		}
		if (trainedAt > 0 && tick > trainedAt && tick <= trainedAt + 40 && tick % 4 == 0) {
			shot(mc, String.format("frame_%03d", frame++));
		}
		if (trainedAt > 0 && tick == trainedAt + 9) {
			shot(mc, "60_guard_training");
		}
		if (trainedAt > 0 && tick == trainedAt + 44) {
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
				io.github.jcondedata.aliveworkplace.platform.Platform.get().openMenu(player, box, box.getBlockPos());
			});
		}
		if (tick == 80 && mc.screen instanceof io.github.jcondedata.aliveworkplace.client.MailboxScreen) {
			String[] texts = {"Friend", "Diamonds for the new roof!"};
			int n = 0;
			for (var child : mc.screen.children()) {
				if (child instanceof net.minecraft.client.gui.components.EditBox edit && n < texts.length) {
					edit.setValue(texts[n++]);
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

	// --- Tutor: the Move Tutor's lesson screen (needs Cobblemon: run.sh adds it for this scene) ------

	private void tutorScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos desk = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(desk, ModBlocks.TUTORS_DESK.defaultBlockState());
				Villager tutor = EntityType.VILLAGER.spawn(level, desk.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, tutor, desk,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.TUTORS_DESK_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TUTOR);
				tutor.setVillagerData(tutor.getVillagerData().setLevel(3));
				var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				for (String spec : List.of("pikachu level=30", "bulbasaur level=24", "eevee level=18")) {
					party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(spec, " ", "=").create());
				}
				player.setGameMode(GameType.SURVIVAL);
				player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, 40));
				player.teleportTo(level, 2.5, -60, 5.5, 135, 20);
				io.github.jcondedata.aliveworkplace.tutor.Tutors.open(player, tutor);
			});
		}
		if (tick == 90) {
			mc.getToasts().clear();
			pointAt(mc, 13); // an empty slot, so no tooltip covers the screen
		}
		if (tick == 100) {
			shot(mc, "01_tutor_screen");
			pointAt(mc, 19); // the second lesson: its tooltip
		}
		if (tick == 120) {
			mc.getToasts().clear();
			shot(mc, "02_tutor_lesson");
		}
		if (tick == 130) {
			mc.stop();
		}
	}

	// --- Missing: a placed blueprint's tooltip says what the builder's chests are short of ------------

	private void missingScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.setDayTime(2500);
				BlockPos bench = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
				level.setBlockAndUpdate(bench.east(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
				if (level.getBlockEntity(bench.east()) instanceof net.minecraft.world.Container chest) {
					chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 64));
					chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
				}
				ItemStack blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(StarterBlueprints.STARTER_COTTAGE.id(), null);
				var bp = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
				var placement = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.placementAt(level.dimension().location(), bp.size(),
					new BlockPos(0, -60, 12), net.minecraft.world.level.block.Rotation.NONE);
				blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
					io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(blueprint).orElseThrow().withSize(bp.size()).withPlacement(java.util.Optional.of(placement)));
				player.getInventory().setItem(0, blueprint);
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(level, 2.5, -60, 6.5, 180, 10);
			});
		}
		if (tick == 120) {
			mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
		}
		if (tick == 130) {
			double scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			setMouse(mc, (left + 8 + 8) * scale, (top + 142 + 8) * scale);
		}
		if (tick == 150) {
			mc.getToasts().clear();
			shot(mc, "01_blueprint_missing");
		}
		if (tick == 160) {
			mc.stop();
		}
	}

	// --- Trader: the Pokémon Trader's offers (needs Cobblemon too) ------------------------------------

	private Villager battleTrainer;
	private final AtomicBoolean megaSeen = new AtomicBoolean(false);
	private final AtomicBoolean battleOver = new AtomicBoolean(false);
	private int battleShots;

	/**
	 * SCENE=battle (Cobblemon + Mega Showdown): the player challenges a Master trainer whose lead holds its Mega Stone.
	 * The trainer's Pokémon come out beside them and Mega Evolve; the player's side always uses its first move.
	 */
	private void battleScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				com.cobblemon.mod.common.api.events.CobblemonEvents.MEGA_EVOLUTION.subscribe(com.cobblemon.mod.common.api.Priority.NORMAL, event -> {
					megaSeen.set(true);
					System.out.println("[battle scene] MEGA EVOLUTION: " + event.getPokemon().getEffectedPokemon().getSpecies().getName());
					return kotlin.Unit.INSTANCE;
				});
				// A trainer whose Master team leads with the Pokémon holding the Mega Stone.
				java.util.UUID id = null;
				for (int i = 0; i < 500 && id == null; i++) {
					java.util.UUID u = new java.util.UUID(0xba771eL, i);
					var team = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(u, 5);
					if (!team.isEmpty() && io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonMegas.holdsItsStone(team.get(0))) {
						id = u;
					}
				}
				System.out.println("[battle scene] trainer " + id);
				BlockPos post = new BlockPos(0, -60, 6);
				level.setBlockAndUpdate(post, ModBlocks.TRAINING_POST.defaultBlockState());
				Villager trainer = new Villager(EntityType.VILLAGER, level);
				trainer.setUUID(id);
				trainer.moveTo(0.5, -60, 5.5, 180, 0);
				level.addFreshEntity(trainer);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, trainer, post,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINING_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINER);
				trainer.setVillagerData(trainer.getVillagerData().setLevel(5));
				battleTrainer = trainer;
				var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				for (String p : List.of("snorlax level=100", "blissey level=100", "skarmory level=100", "tyranitar level=100", "dragonite level=100", "garchomp level=100")) {
					party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(p, " ", "=").create());
				}
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(level, 0.5, -60, -6.5, 0, 10);
			});
		}
		if (tick == 80) {
			server.execute(() -> io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(server.getPlayerList().getPlayers().get(0), battleTrainer));
		}
		if (tick > 80 && tick % 4 == 0) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				var battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player);
				if (battle == null) {
					battleOver.set(tick > 200);
					return;
				}
				var us = battle.getActor(player);
				if (tick % 100 == 0) {
					StringBuilder state = new StringBuilder("[battle scene] tick " + tick + " turn " + battle.getTurn());
					battle.getActors().forEach(a -> state.append(" | ").append(a.getName().getString()).append(" request=").append(a.getRequest() != null)
						.append(" mustChoose=").append(a.getMustChoose()).append(" responses=").append(a.getResponses().size()));
					System.out.println(state);
				}
				if (us == null || us.getRequest() == null || !us.getMustChoose() || !us.getResponses().isEmpty()) {
					return;
				}
				var request = us.getRequest();
				List<com.cobblemon.mod.common.battles.ShowdownActionResponse> choice = new ArrayList<>();
				if (request.getForceSwitch() != null && request.getForceSwitch().contains(true)) {
					// Send in the next Pokémon that can still fight.
					for (var pokemon : us.getPokemonList()) {
						if (pokemon.getHealth() > 0 && us.getActivePokemon().stream().noneMatch(a -> a.getBattlePokemon() == pokemon)) {
							choice.add(new com.cobblemon.mod.common.battles.SwitchActionResponse(pokemon.getUuid()));
							break;
						}
					}
				} else if (request.getActive() != null && !request.getActive().isEmpty()) {
					var moves = request.getActive().get(0).getMoves();
					var usable = moves.stream().filter(m -> m.canBeUsed()).findFirst().orElse(moves.get(0));
					var targets = usable.getTargets(us.getActivePokemon().get(0));
					String target = targets == null || targets.isEmpty() ? null : targets.stream().map(t -> t.getPNX())
						.filter(pnx -> pnx.startsWith("p2")).findFirst().orElse(targets.get(0).getPNX());
					choice.add(new com.cobblemon.mod.common.battles.MoveActionResponse(usable.getId(), target, null));
				}
				if (!choice.isEmpty()) {
					us.setActionResponses(choice);
				}
			});
		}
		if (tick > 100 && tick % 10 == 0 && battleShots < 250) {
			// Keep an eye on the trainer's side.
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.teleportTo(server.overworld(), 8.5, -58.2, 2.5, 72, 14);
			});
			shot(mc, String.format("battle_%03d", battleShots++));
		}
		if ((battleOver.get() || tick >= 8000) && tick % 20 == 0) {
			System.out.println("[battle scene] over at tick " + tick + ", mega " + megaSeen.get());
			shot(mc, "99_battle_end");
			mc.stop();
		}
	}

	private void traderScene(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 40) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				BlockPos board = new BlockPos(0, -60, 3);
				level.setBlockAndUpdate(board, ModBlocks.TRADE_BOARD.defaultBlockState());
				Villager trader = EntityType.VILLAGER.spawn(level, board.south(), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, trader, board,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRADE_BOARD_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.POKEMON_TRADER);
				trader.setVillagerData(trader.getVillagerData().setLevel(5));
				var offer = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(trader).get(0);
				var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("pikachu level=30", " ", "=").create());
				// One that fits the first offer.
				for (var species : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getImplemented()) {
					boolean fits = false;
					for (var type : species.getTypes()) {
						fits |= type.getName().equals(offer.wanted().getName());
					}
					if (fits && species.getEvolutions().isEmpty()) {
						party.add(species.create(offer.minLevel() + 3));
						break;
					}
				}
				party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("eevee level=12", " ", "=").create());
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(level, 2.5, -60, 5.5, 135, 20);
				io.github.jcondedata.aliveworkplace.trader.PokemonTraders.open(player, trader);
			});
		}
		if (tick == 90) {
			mc.getToasts().clear();
			pointAt(mc, 0); // the first offer
		}
		if (tick == 100) {
			shot(mc, "01_trader_offer");
			pointAt(mc, 19); // the Pokémon that fits
		}
		if (tick == 120) {
			mc.getToasts().clear();
			shot(mc, "02_trader_party");
			pointAt(mc, 18); // one that doesn't
		}
		if (tick == 140) {
			shot(mc, "03_trader_refused");
		}
		if (tick == 150) {
			mc.stop();
		}
	}

	/** Moves the mouse over slot {@code slot} of an open six-row chest screen. */
	private static void pointAt(Minecraft mc, int slot) {
		double scale = mc.getWindow().getGuiScale();
		int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
		int top = (mc.getWindow().getGuiScaledHeight() - 222) / 2;
		setMouse(mc, (left + 8 + (slot % 9) * 18 + 8) * scale, (top + 18 + (slot / 9) * 18 + 8) * scale);
	}

	/** Puts the mouse at window pixel ({@code x}, {@code y}); the harness has no real mouse. */
	private static void setMouse(Minecraft mc, double x, double y) {
		try {
			var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
			var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
			xpos.setAccessible(true);
			ypos.setAccessible(true);
			xpos.setDouble(mc.mouseHandler, x);
			ypos.setDouble(mc.mouseHandler, y);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
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
	private final List<BlockPos> otherHouses = new ArrayList<>();

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
						for (net.minecraft.world.level.block.Block job : List.of(ModBlocks.TRAINING_POST, ModBlocks.GUARD_POST, ModBlocks.NURSE_STATION, ModBlocks.POSTAL_DESK)) {
							if (st.is(job) && otherHouses.stream().noneMatch(h -> level.getBlockState(h).is(job))) {
								otherHouses.add(p.immutable());
							}
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
				workshops.addAll(otherHouses);
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

	// --- Fish: a fisherman casting into a pond (the bobber and its line) --------------------------

	private void fishScene(Minecraft mc, MinecraftServer server) {
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
				BlockPos barrel = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(2, -61, 2), new BlockPos(6, -61, 6))) {
					level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
				}
				Villager fisher = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 1), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, fisher, barrel, net.minecraft.world.entity.ai.village.poi.PoiTypes.FISHERMAN,
					net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN);
				io.github.jcondedata.aliveworkplace.fish.Fishers.start(level, fisher, new ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
				hover(server.getPlayerList().getPlayers().get(0), new Vec3(-3.5, -57.2, 7.5), 215, 28);
			});
		}
		for (int i = 0; i < 6; i++) {
			if (tick == 120 + i * 30) {
				shot(mc, "60_fish_" + i);
			}
		}
		if (tick == 320) {
			mc.stop();
		}
	}

	// --- Extras: a fisher out in a boat, a guard on horseback, a ferry ride -------------------------

	private void extrasScene(Minecraft mc, MinecraftServer server) {
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
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, server);
				level.setDayTime(2500);
				// A: a fisher with a boat by a lake
				BlockPos barrel = new BlockPos(0, -60, 0);
				level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
				if (level.getBlockEntity(barrel) instanceof BaseContainerBlockEntity c) {
					c.setItem(0, new ItemStack(net.minecraft.world.item.Items.SPRUCE_BOAT));
				}
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(3, -61, 3), new BlockPos(22, -61, 22))) {
					level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
				}
				Villager fisher = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 1), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, fisher, barrel, net.minecraft.world.entity.ai.village.poi.PoiTypes.FISHERMAN,
					net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN);
				io.github.jcondedata.aliveworkplace.fish.Fishers.start(level, fisher, new ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
				// B: a guard with a saddled horse by the post
				BlockPos post = new BlockPos(48, -60, 0);
				level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
				BlockPos chest = post.south(2);
				level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
				if (level.getBlockEntity(chest) instanceof BaseContainerBlockEntity c) {
					c.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
					c.setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
					c.setItem(2, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
					c.setItem(3, new ItemStack(net.minecraft.world.item.Items.SHIELD));
				}
				Villager guard = EntityType.VILLAGER.spawn(level, post.offset(1, 0, 1), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, guard, post, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
				net.minecraft.world.entity.animal.horse.Horse horse = EntityType.HORSE.spawn(level, post.offset(4, 0, 4), MobSpawnType.COMMAND);
				horse.setTamed(true);
				horse.equipSaddle(new ItemStack(net.minecraft.world.item.Items.SADDLE), null);
				// C: two travel posts, a ferryman at the first, and a pond to row across
				BlockPos harbor = new BlockPos(96, -60, 0);
				BlockPos far = new BlockPos(96, -60, 48);
				level.setBlockAndUpdate(harbor, ModBlocks.TRAVEL_POST.defaultBlockState());
				level.setBlockAndUpdate(far, ModBlocks.TRAVEL_POST.defaultBlockState());
				var network = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.get(server);
				network.add(net.minecraft.core.GlobalPos.of(level.dimension(), harbor), "Harbor");
				var farPost = network.add(net.minecraft.core.GlobalPos.of(level.dimension(), far), "Far Shore");
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(92, -61, 3), new BlockPos(104, -61, 20))) {
					level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
				}
				Villager ferryman = EntityType.VILLAGER.spawn(level, harbor.offset(1, 0, 1), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, ferryman, harbor, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAVEL_POST_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.FERRYMAN);
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				network.visit(player.getUUID(), farPost);
				hoverLookingAt(player, new Vec3(-5, -54, -5), new Vec3(11, -61, 11));
			});
		}
		for (int i = 0; i < 6; i++) {
			if (tick == 240 + i * 60) {
				shot(mc, "70_boat_" + i);
			}
		}
		if (tick >= 400 && tick < 600) {
			// Close up on the fisher once he's out in the boat (the wide view from above before that)
			Villager fisher = mc.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(new BlockPos(12, -61, 12)).inflate(16),
				v -> v.getVehicle() != null).stream().findFirst().orElse(null);
			if (fisher != null) {
				follow(mc, fisher.getVehicle(), 4.5, 0, 1.6);
			} else {
				stopFollowing(mc, net.minecraft.client.CameraType.FIRST_PERSON);
			}
		}
		if (tick == 600) {
			stopFollowing(mc, net.minecraft.client.CameraType.FIRST_PERSON);
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Villager guard = server.overworld().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(new BlockPos(48, -60, 0)).inflate(30),
					v -> v.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD).stream().findFirst().orElse(null);
				Vec3 at = guard != null ? guard.position() : new Vec3(48, -60, 0);
				hoverLookingAt(player, at.add(6, 4, -6), at.add(0, 1, 0));
			});
		}
		if (tick >= 620 && tick < 740) {
			// The mounted guard from the side, from the front and from further off, the camera following the horse
			Villager rider = mc.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(new BlockPos(48, -60, 0)).inflate(40),
				v -> v.getVehicle() != null).stream().findFirst().orElse(null);
			if (rider != null) {
				int view = tick < 650 ? 0 : tick < 690 ? 1 : 2;
				switch (view) {
					case 0 -> follow(mc, rider.getVehicle(), 4.5, 0, 1.8);
					case 1 -> follow(mc, rider.getVehicle(), 2.5, 3.5, 2.0);
					default -> follow(mc, rider.getVehicle(), -6, -3, 3.5);
				}
			}
		}
		for (int i = 0; i < 3; i++) {
			if (tick == 640 + i * 40) {
				shot(mc, "71_cavalry_" + i);
			}
		}
		if (tick == 740) {
			stopFollowing(mc, net.minecraft.client.CameraType.FIRST_PERSON);
		}
		if (tick == 780) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(server.overworld(), 97.5, -61, 5.5, 180f, 15f);
			});
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}
		if (tick == 820) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				var network = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.get(server);
				var far = network.known(player.getUUID(), null).stream().filter(p -> p.name().equals("Far Shore")).findFirst().orElseThrow();
				io.github.jcondedata.aliveworkplace.travel.Ferrymen.travel(player, io.github.jcondedata.aliveworkplace.travel.Ferrymen.ticket(far));
			});
		}
		if (tick >= 824 && tick < 856 && mc.player.getVehicle() != null) {
			// The boat from the side, the front quarter and the front (the ferryman at the oars, the player behind).
			// The player's own third-person camera turned round: another camera entity would hide the player.
			int view = tick < 834 ? 0 : tick < 844 ? 1 : 2;
			float yaw = mc.player.getVehicle().getYRot() + (view == 0 ? 90 : view == 1 ? 40 : 10);
			mc.setCameraEntity(mc.player);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
			mc.player.setYRot(yaw);
			mc.player.yRotO = yaw;
			mc.player.setYHeadRot(yaw);
			mc.player.yHeadRotO = yaw;
			mc.player.setXRot(-25);
			mc.player.xRotO = -25;
		}
		for (int i = 0; i < 3; i++) {
			if (tick == 832 + i * 10) {
				shot(mc, "72_ferry_" + i);
			}
		}
		if (tick == 856) {
			stopFollowing(mc, net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}
		if (tick == 920) {
			shot(mc, "72_ferry_arrived");
		}
		if (tick == 940) {
			mc.stop();
		}
	}

	// --- Gallery: every starter blueprint placed instantly, one shot each -------------------------

	private void galleryScene(Minecraft mc, MinecraftServer server) {
		tick++;
		List<StarterBlueprints.Entry> all = "decor".equals(System.getProperty("aliveworkplace.scene")) ? StarterBlueprints.DECORATIONS
			: "defences".equals(System.getProperty("aliveworkplace.scene")) ? StarterBlueprints.DEFENCES
			: "workshops".equals(System.getProperty("aliveworkplace.scene")) ? List.of(StarterBlueprints.TINKERS_WORKSHOP, StarterBlueprints.TINKERS_WORKSHOP_2, StarterBlueprints.NETHER_GATE, StarterBlueprints.NETHER_GATE_2)
			: "styles".equals(System.getProperty("aliveworkplace.scene")) ? styledGallery() : StarterBlueprints.ALL;
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
				for (int i = 0; i < all.size(); i++) {
					BlockPos origin = new BlockPos(i * GALLERY_SPACING, -60, 0);
					if (io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.parse(all.get(i).id()).isPresent()) {
						// A styled blueprint isn't a structure file: set its blocks one by one.
						var blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, all.get(i).id()).orElseThrow();
						for (var e : blueprint.blocks()) {
							level.setBlock(origin.offset(e.pos()), e.state(), 2);
						}
						continue;
					}
					net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate t =
						level.getStructureManager().get(all.get(i).id()).orElseThrow();
					t.placeInWorld(level, origin, origin, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
						level.getRandom(), 2);
					if (all.get(i).id().getPath().startsWith("nether_gate")) {
						// Lit, as the builder leaves it when there's a flint and steel in the chests.
						net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, origin.offset(4, 2, 3), Direction.Axis.X)
							.ifPresent(net.minecraft.world.level.portal.PortalShape::createPortalBlocks);
					}
				}
			});
		}
		if (tick % 20 == 0) {
			// Slimes from the superflat world's slime chunks hop into the shots.
			server.execute(() -> {
				for (net.minecraft.world.entity.Entity e : server.overworld().getAllEntities()) {
					if (e instanceof net.minecraft.world.entity.monster.Slime) {
						e.discard();
					}
				}
			});
		}
		int index = (tick - 60) / 60;
		if (tick == 40) {
			// Look at the first build early so its chunks are drawn by the first shot.
			StarterBlueprints.Entry e = all.get(0);
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0), galleryCenter(0, e).add(8, 6, -14), galleryCenter(0, e)));
		}
		if (tick >= 60 && (tick - 60) % 60 == 0 && index < all.size()) {
			StarterBlueprints.Entry e = all.get(index);
			Vec3 center = galleryCenter(index, e);
			double dist = Math.max(Math.max(e.size().getX(), e.size().getZ()), e.size().getY()) * 0.75 + 5;
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0),
				center.add(dist * 0.55, e.size().getY() * 0.35 + 2, -dist), center));
		}
		if (tick >= 100 && (tick - 100) % 60 == 0 && (tick - 100) / 60 < all.size()) {
			shot(mc, "30_" + all.get((tick - 100) / 60).id().getPath().replace('/', '_'));
		}
		// And from behind, where upgrades often add their part.
		if (tick >= 105 && (tick - 105) % 60 == 0 && (tick - 105) / 60 < all.size()) {
			int i = (tick - 105) / 60;
			StarterBlueprints.Entry e = all.get(i);
			Vec3 center = galleryCenter(i, e);
			double dist = Math.max(Math.max(e.size().getX(), e.size().getZ()), e.size().getY()) * 0.75 + 5;
			server.execute(() -> hoverLookingAt(server.getPlayerList().getPlayers().get(0),
				center.add(-dist * 0.55, e.size().getY() * 0.35 + 3, dist), center));
		}
		if (tick >= 118 && (tick - 118) % 60 == 0 && (tick - 118) / 60 < all.size()) {
			shot(mc, "31_" + all.get((tick - 118) / 60).id().getPath().replace('/', '_') + "_back");
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

	private static final int GALLERY_SPACING = 48;

	/** The Starter Cottage II and the Stone House II as drawn and in every style. */
	private static List<StarterBlueprints.Entry> styledGallery() {
		List<StarterBlueprints.Entry> out = new java.util.ArrayList<>();
		for (StarterBlueprints.Entry base : List.of(StarterBlueprints.STARTER_COTTAGE_2, StarterBlueprints.STONE_HOUSE_2)) {
			out.add(base);
			for (var style : io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.all()) {
				out.add(new StarterBlueprints.Entry(io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.styled(base.id(), style.name()), base.size()));
			}
		}
		return out;
	}

	/** The middle of the gallery's {@code index}th build. */
	private static Vec3 galleryCenter(int index, StarterBlueprints.Entry e) {
		return new Vec3(index * GALLERY_SPACING + e.size().getX() / 2.0, -60 + e.size().getY() * 0.4, e.size().getZ() / 2.0);
	}

	/** Hovers at {@code pos} looking at {@code target}. */
	private static void hoverLookingAt(ServerPlayer player, Vec3 pos, Vec3 target) {
		Vec3 d = target.subtract(pos);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		hover(player, pos, yaw, pitch);
	}

	private static void hover(ServerPlayer player, Vec3 pos, float yaw, float pitch) {
		player.setGameMode(GameType.CREATIVE);
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, yaw, pitch);
	}

	/** A still camera that isn't the player (an armor stand only the client knows about). */
	private net.minecraft.world.entity.decoration.ArmorStand camera;

	/**
	 * Films {@code subject} from {@code side} blocks to its right, {@code ahead} blocks in front of it and {@code up} blocks
	 * above, going by the way it faces (a moving horse or boat stays in the frame).
	 */
	private void follow(Minecraft mc, net.minecraft.world.entity.Entity subject, double side, double ahead, double up) {
		Vec3 forward = Vec3.directionFromRotation(0, subject.getYRot());
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 at = subject.position().add(0, 0.8, 0);
		Vec3 eye = at.add(right.scale(side)).add(forward.scale(ahead)).add(0, up - 0.8, 0);
		if (camera == null || camera.level() != mc.level) {
			camera = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, eye.x, eye.y, eye.z);
			camera.setInvisible(true);
		}
		Vec3 d = at.subtract(eye);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		camera.moveTo(eye.x, eye.y - camera.getEyeHeight(), eye.z, yaw, pitch);
		camera.setYHeadRot(yaw);
		mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		mc.setCameraEntity(camera);
	}

	private void stopFollowing(Minecraft mc, net.minecraft.client.CameraType type) {
		mc.setCameraEntity(mc.player);
		mc.options.setCameraType(type);
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
