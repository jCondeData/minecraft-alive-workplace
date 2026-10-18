package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=modded_storehouse, part one (ROADMAP 37.9), filmed with -Pfurniture=true (which brings Sophisticated Storage; with
 * -Pcobblemon=true, as CobbleFurnies in that set needs it): a
 * Storehouse with a Sophisticated Storage controller on it and three Sophisticated barrels and two chests linked to it,
 * goods in each. With {@code moddedStorageNetworks} on, the Storehouse reads them as one store through the controller;
 * its board says "Linked through a storage controller: 5 blocks". Checks the store is the controller alone, its count is
 * the blocks' total (not twice), and the board line. Part two (37.10) adds a Tom's Simple Storage connector.
 */
final class ModdedStorehouseScene {
	private static final BlockPos ROOM = new BlockPos(0, -60, 0);
	private static final Vec3 EYE = new Vec3(3.5, 3.2, -4.5);
	private static final Vec3 AT = new Vec3(3.5, 0.8, 2.5);

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());
	private volatile boolean modsHere;
	private volatile boolean oneStore;
	private volatile int linked;

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
				StorageNetworks.ENABLED = true; // M37's switch (off on main until the milestone ships)
				place(level);
				ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), Vec3.atLowerCornerOf(ROOM).add(EYE),
					Vec3.atLowerCornerOf(ROOM).add(AT));
			});
		}
		if (tick == 80) {
			ScreenshotHarness.shot(mc, "01_storehouse");
		}
		if (tick == 100) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (modsHere) {
					StorehouseBoard.open(player, ROOM.offset(3, 0, 2));
				}
			});
		}
		if (tick == 140) {
			mc.options.hideGui = false;
		}
		if (tick == 160) {
			ScreenshotHarness.shot(mc, "02_board");
		}
		if (tick == 180) {
			if (!modsHere) {
				wrong.add("Sophisticated Storage isn't loaded: film this scene with -Pfurniture=true");
			}
			if (!oneStore) {
				wrong.add("the Storehouse didn't read the controller's five blocks as one store, counted once");
			}
			if (linked != 5) {
				wrong.add("the board's linked line counted " + linked + " blocks, not 5");
			}
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "the Storehouse reads a Sophisticated controller and its five linked blocks as one store, counted once, and its board says so"
				: String.join("; ", wrong));
			mc.stop();
		}
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	private void place(ServerLevel level) {
		modsHere = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("sophisticatedstorage:controller"));
		if (!modsHere) {
			return;
		}
		for (int x = -1; x <= 8; x++) {
			for (int z = -1; z <= 6; z++) {
				boolean wall = x == -1 || x == 8 || z == 6;
				level.setBlock(ROOM.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
				for (int y = 0; y < 4; y++) {
					level.setBlock(ROOM.offset(x, y, z), wall && y < 3 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		level.setBlockAndUpdate(ROOM.offset(3, 0, 2), ModBlocks.STOREHOUSE.defaultBlockState());
		List<BlockPos> stores = List.of(ROOM.offset(2, 0, 3), ROOM.offset(3, 0, 3), ROOM.offset(4, 0, 3), ROOM.offset(2, 1, 3), ROOM.offset(4, 1, 3));
		String[] kinds = {"sophisticatedstorage:barrel", "sophisticatedstorage:iron_barrel", "sophisticatedstorage:gold_barrel",
			"sophisticatedstorage:chest", "sophisticatedstorage:chest"};
		ItemStack[] goods = {new ItemStack(Items.WHEAT, 40), new ItemStack(Items.RAW_IRON, 24), new ItemStack(Items.OAK_LOG, 48),
			new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.BREAD, 20)};
		for (int i = 0; i < stores.size(); i++) {
			level.setBlockAndUpdate(stores.get(i), block(kinds[i]).defaultBlockState());
			SupplyContainers.insert(level, List.of(stores.get(i)), goods[i]);
		}
		BlockPos controller = ROOM.offset(3, 1, 3);
		Block c = block("sophisticatedstorage:controller");
		level.setBlockAndUpdate(controller, c.defaultBlockState());
		BlockState state = level.getBlockState(controller);
		c.setPlacedBy(level, controller, state, null, new ItemStack(c.asItem())); // linked as a player's placing links it
		level.setBlock(ROOM.offset(0, 0, 0), Blocks.LANTERN.defaultBlockState(), 2);
		level.setBlock(ROOM.offset(7, 0, 0), Blocks.LANTERN.defaultBlockState(), 2);

		StorageNetworks.Folded folded = SupplyContainers.networks(level, ROOM.offset(3, 0, 2));
		long counted = SupplyContainers.contents(level, folded.stores()).values().stream().mapToLong(Long::longValue).sum();
		oneStore = folded.stores().equals(List.of(controller)) && counted == 40 + 24 + 48 + 64 + 20;
		linked = folded.linked();
		if (!oneStore) {
			wrong.add("stores " + folded.stores() + ", " + counted + " items counted");
		}
	}
}
