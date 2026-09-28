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
				&& keeper.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.FRUIT_PICKED, 0) >= orchardFruit));
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
