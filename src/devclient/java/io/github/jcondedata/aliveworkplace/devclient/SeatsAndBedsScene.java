package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=seats_and_beds (ROADMAP 37.6), filmed with -Pfurniture=true: a roofless furnished room (a Handcrafted table with
 * two chairs and a couch, two fancy beds, lanterns). At supper two villagers sit down on the chairs; at bedtime they get
 * up and sleep in the fancy beds. Checks both sat on a seat at supper and both slept in a fancy bed.
 */
final class SeatsAndBedsScene {
	private static final BlockPos ROOM = new BlockPos(0, -60, 0);
	private static final Vec3 EYE = new Vec3(10.5, 5.5, -3.5);
	private static final Vec3 AT = new Vec3(3.5, 0.5, 3.5);

	private int tick;
	private final List<Villager> villagers = new ArrayList<>();
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());
	private volatile boolean sat;
	private volatile boolean slept;
	private volatile boolean modsHere;

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
				level.setDayTime(FurnitureSeats.SUPPER + 400);
				place(level);
				ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), Vec3.atLowerCornerOf(ROOM).add(EYE),
					Vec3.atLowerCornerOf(ROOM).add(AT));
			});
		}
		if (tick > 20 && tick < 400 && tick % 10 == 0) {
			server.execute(() -> sat |= !villagers.isEmpty() && villagers.stream().allMatch(v -> v.getVehicle() instanceof FurnitureSeat));
		}
		if (tick == 260) {
			ScreenshotHarness.shot(mc, "01_supper");
		}
		if (tick == 400) {
			server.execute(() -> server.overworld().setDayTime(13000));
		}
		if (tick > 400 && tick % 10 == 0) {
			server.execute(() -> slept |= !villagers.isEmpty() && villagers.stream().allMatch(v -> v.isSleeping()
				&& v.getSleepingPos().map(p -> "handcrafted".equals(BuiltInRegistries.BLOCK.getKey(v.level().getBlockState(p).getBlock()).getNamespace()))
					.orElse(false)));
		}
		if (tick == 1000) {
			ScreenshotHarness.shot(mc, "02_bedtime");
		}
		if (tick == 1020) {
			if (!modsHere) {
				wrong.add("the furniture mods aren't loaded: film this scene with -Pfurniture=true");
			}
			if (!sat) {
				wrong.add("the villagers didn't both sit on the Handcrafted chairs at supper");
			}
			if (!slept) {
				wrong.add("the villagers didn't both sleep in the fancy beds at bedtime");
			}
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "two villagers sat on Handcrafted chairs at supper, then got up and slept in Handcrafted fancy beds" : String.join("; ", wrong));
			mc.stop();
		}
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	private void place(ServerLevel level) {
		modsHere = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_chair"));
		if (!modsHere) {
			return;
		}
		for (int x = -1; x <= 8; x++) {
			for (int z = -1; z <= 8; z++) {
				boolean wall = x == -1 || x == 8 || z == 8;
				level.setBlock(ROOM.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
				for (int y = 0; y < 4; y++) {
					level.setBlock(ROOM.offset(x, y, z), wall && y < 2 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		level.setBlock(ROOM.offset(3, 0, 2), block("handcrafted:oak_table").defaultBlockState(), 2);
		level.setBlock(ROOM.offset(2, 0, 2), block("handcrafted:oak_chair").defaultBlockState(), 2);
		level.setBlock(ROOM.offset(4, 0, 2), block("handcrafted:oak_chair").defaultBlockState(), 2);
		level.setBlock(ROOM.offset(3, 0, 7), block("handcrafted:oak_couch").defaultBlockState(), 2);
		for (int x : new int[] {1, 6}) {
			BlockState foot = block("handcrafted:oak_fancy_bed").defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
			level.setBlock(ROOM.offset(x, 0, 6), foot.setValue(BedBlock.PART, BedPart.FOOT), 2);
			level.setBlock(ROOM.offset(x, 0, 7), foot.setValue(BedBlock.PART, BedPart.HEAD), 2);
		}
		level.setBlock(ROOM.offset(0, 0, 0), Blocks.LANTERN.defaultBlockState(), 2);
		level.setBlock(ROOM.offset(7, 0, 0), Blocks.LANTERN.defaultBlockState(), 2);
		level.setBlock(ROOM.offset(3, 1, 2), Blocks.LANTERN.defaultBlockState(), 2);
		for (int x : new int[] {2, 4}) {
			Villager v = EntityType.VILLAGER.create(level);
			if (v != null) {
				v.moveTo(ROOM.getX() + x + 0.5, ROOM.getY(), ROOM.getZ() + 0.5, 0, 0);
				v.setPersistenceRequired();
				level.addFreshEntity(v);
				villagers.add(v);
			}
		}
	}
}
