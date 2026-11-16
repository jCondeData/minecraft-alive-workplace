package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=house_t1_b_furnished (ROADMAP 36.23 and 37), filmed with -Pfurniture=true: House T1 B (the Daub House) as a
 * builder in a village with the furniture mods would build it (FurnitureChoice.forNewBuild, the same step the Builder
 * runs), then filmed at eye level from the door to the hearth, the kitchen, the loft, and from outside. Checks every
 * furniture spot holds the piece chosen and counts the pieces per mod.
 */
final class HouseFurnishedScene {
	private static final ResourceLocation HOUSE = AliveWorkplace.id("house_t1_b");
	private static final BlockPos AT = new BlockPos(0, -60, 0);
	private static final int FIRST = 80;
	private static final int EACH = 50;

	private record View(String name, Vec3 eye, Vec3 at, int fov) {
	}

	private static final List<View> VIEWS = List.of(
		new View("01_outside_front", new Vec3(-3, 3.5, -8), new Vec3(5, 4.5, 5), 70),
		new View("02_outside_back", new Vec3(15, 4.5, 19), new Vec3(5, 5, 7), 70),
		new View("03_hall_hearth", new Vec3(4.5, 2.6, 3.5), new Vec3(4.5, 1.6, 10), 80),
		new View("04_hall_table", new Vec3(5.6, 3.2, 4.6), new Vec3(3.0, 1.4, 7.0), 80),
		new View("05_kitchen_stove", new Vec3(8.6, 2.8, 5.5), new Vec3(9.6, 1.5, 8.4), 80),
		new View("06_kitchen_counters", new Vec3(8.6, 2.8, 7.6), new Vec3(10.2, 1.8, 4.5), 80),
		new View("07_loft_children", new Vec3(4.6, 6.5, 7.0), new Vec3(4.2, 5.4, 2.5), 80),
		new View("08_loft_parents", new Vec3(3.5, 6.5, 5.2), new Vec3(3.5, 5.3, 10.4), 80),
		new View("09_loft_railing", new Vec3(3.0, 6.8, 8.2), new Vec3(5.5, 5.4, 5.5), 80),
		new View("10_hall_lamp", new Vec3(6.5, 2.9, 5.7), new Vec3(6.5, 2.0, 3.5), 70));

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());
	private volatile String summary = "";

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.bobView().set(false);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				place(level);
			});
		}
		int t = tick - FIRST;
		if (t >= 0 && t % EACH == 0 && t / EACH < VIEWS.size()) {
			View view = VIEWS.get(t / EACH);
			mc.options.fov().set(view.fov());
			Vec3 base = Vec3.atLowerCornerOf(AT);
			server.execute(() -> {
				server.overworld().getEntitiesOfClass(Mob.class, new AABB(AT).inflate(40)).forEach(net.minecraft.world.entity.Entity::discard);
				ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), base.add(view.eye()), base.add(view.at()));
			});
		}
		if (t >= 40 && (t - 40) % EACH == 0 && (t - 40) / EACH < VIEWS.size()) {
			ScreenshotHarness.shot(mc, VIEWS.get((t - 40) / EACH).name());
		}
		if (tick == FIRST + VIEWS.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty() ? summary : String.join("; ", wrong));
			mc.stop();
		}
	}

	private void place(ServerLevel level) {
		Blueprint base = BlueprintLibrary.get(level, HOUSE).orElse(null);
		if (base == null) {
			wrong.add("no House T1 B blueprint");
			return;
		}
		for (BlockPos p : BlockPos.betweenClosed(AT.offset(-16, -1, -16), AT.offset(30, 30, 34))) {
			BlockState want = p.getY() == AT.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
			if (level.getBlockState(p) != want) {
				level.setBlock(p, want, 2 | 16);
			}
		}
		FurnitureChoice choice = FurnitureChoice.forNewBuild(HOUSE);
		for (Blueprint.Entry e : choice.apply(base, HOUSE).blocks()) {
			level.setBlock(AT.offset(e.pos()), e.state(), 2 | 16);
		}
		Map<String, Integer> perMod = new TreeMap<>();
		Map<String, Integer> perKind = new TreeMap<>();
		for (FurnitureChoice.Placed placed : choice.spots().values()) {
			perMod.merge(placed.mod(), 1, Integer::sum);
			perKind.merge(placed.kind() + "/" + placed.mod(), 1, Integer::sum);
			for (Map.Entry<BlockPos, BlockState> cell : placed.cells().entrySet()) {
				BlockState there = level.getBlockState(AT.offset(placed.pos()).offset(cell.getKey()));
				if (!there.is(cell.getValue().getBlock()) && !cell.getValue().isAir()) {
					wrong.add(placed.kind() + " at " + placed.pos().offset(cell.getKey()).toShortString() + " is "
						+ BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not " + BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock()));
				}
			}
		}
		AliveWorkplace.LOG.info("HOUSE_T1_B_FURNISHED spots={} pieces per mod {} per kind {}", choice.spots().size(), perMod, perKind);
		if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_chair"))) {
			wrong.add("the furniture mods aren't loaded: film this scene with -Pfurniture=true");
		}
		summary = "House T1 B furnished: " + choice.spots().size() + " spots placed, pieces per mod " + perMod;
	}
}
