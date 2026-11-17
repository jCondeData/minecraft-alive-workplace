package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=terrace_sleep (B304): the Terrace (two homes under one roof, each bedroom up a ladder) at dusk. Two neighbours
 * have the east home's beds; a villager without a bed stands outside the east home's door. Before B304 he never got one
 * of the west home's free beds (vanilla claims only a bed it can walk to, and it can't walk up a ladder) and stood about
 * the house all night; and a villager of the west home coming in by the east home's door climbed the wrong ladder. Now he
 * claims a free bed next door, goes in by the west home's door, climbs its ladder and sleeps.
 */
final class TerraceSleepScene {
	/** The superflat world's grass. */
	private static final int LAWN = -61;
	private static final BlockPos ORIGIN = new BlockPos(8, LAWN + 1, 10);
	private static final int SIZE = 30;
	/** Client ticks from the move-in to the timeout (the old fixed timeline's 2400 - 25). */
	private static final int LIMIT = 2375;
	/** Client ticks the scene waits after the placing for the Terrace's 4 beds to be POIs before it gives up (B533). */
	private static final int MOVE_IN_WAIT = 600;

	private int tick;
	/** The client tick the move-in was seen done; the rest of the scene's timeline counts from it (B533). */
	private int movedInAt = -1;
	private int neighboursCheckedAt = -1;
	private volatile boolean movedIn;
	private volatile int bedsFound = -1;
	private volatile boolean neighboursAsleep;
	private int sleptAt = -1;
	private boolean ladderShot;
	private boolean inKitchen;
	private boolean inBedroom;
	private ArmorStand camera;
	private Villager newcomer;
	private final List<Villager> neighbours = new ArrayList<>();
	private final List<BlockPos> westBeds = new ArrayList<>();
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());
	private volatile boolean claimed;
	private volatile boolean climbing;
	private volatile boolean asleep;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> setUp(server.overworld()));
			server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0),
				Vec3.atLowerCornerOf(ORIGIN).add(9.5, 5, -11), Vec3.atLowerCornerOf(ORIGIN).add(7.5, 3, 4)));
		}
		if (tick % 20 == 0) {
			server.execute(() -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
				new net.minecraft.world.phys.AABB(BlockPos.ZERO).inflate(60)).forEach(net.minecraft.world.entity.Entity::discard));
		}
		if (tick >= 25 && movedInAt < 0 && tick % 5 == 0) {
			// B533: the placed beds' POIs are registered by tasks the placing queues at the end of the server's task queue
			// (ServerLevel.onBlockStateChange's server.execute is queued, not run, because the placing itself runs in a
			// task). On a slow runner the server hasn't run setUp by tick 25, so a move-in sent then sits ahead of those
			// POI tasks and finds no beds. So it asks again every 5 ticks until the 4 beds are POIs.
			if (movedIn) {
				movedInAt = tick;
			} else if (tick <= 25 + MOVE_IN_WAIT) {
				server.execute(() -> moveIn(server.overworld()));
			} else {
				ScreenshotHarness.shot(mc, "99_timeout");
				Showcase.check(false, "the villager without a bed didn't sleep in the west home in time: the Terrace has "
					+ bedsFound + " beds, not 4, " + MOVE_IN_WAIT / 20 + " seconds after it was placed"
					+ (wrong.isEmpty() ? "" : "; " + String.join("; ", wrong)));
				finish(mc);
				return;
			}
		}
		if (movedInAt < 0) {
			return;
		}
		int t = tick - movedInAt + 25; // the scene's timeline as it was with the move-in at tick 25
		if (t == 70) {
			ScreenshotHarness.shot(mc, "01_dusk");
			server.execute(() -> server.overworld().setDayTime(13000));
		}
		if (t > 70 && tick % 5 == 0 && sleptAt < 0) {
			server.execute(this::watch);
		}
		if (claimed && !inKitchen) {
			inKitchen = true; // the west home's kitchen, its ladder up in the corner
			look(mc, Vec3.atLowerCornerOf(ORIGIN).add(2.4, 3.3, 3.2), Vec3.atLowerCornerOf(ORIGIN).add(5.5, 2.2, 7.5));
		}
		if (climbing && !ladderShot) {
			ladderShot = true;
			ScreenshotHarness.shot(mc, "02_ladder");
		}
		if ((ladderShot || asleep) && !inBedroom) {
			inBedroom = true; // the west home's bedroom, from beside the ladder's top
			look(mc, Vec3.atLowerCornerOf(ORIGIN).add(4.6, 7.9, 7.4), Vec3.atLowerCornerOf(ORIGIN).add(2.5, 6.3, 3.6));
		}
		if (asleep && sleptAt < 0) {
			sleptAt = tick;
		}
		if (sleptAt > 0 && tick == sleptAt + 40) {
			if (!ladderShot) {
				ladderShot = true;
				wrong.add("he was never seen on the west home's ladder");
				ScreenshotHarness.shot(mc, "02_ladder");
			}
			ScreenshotHarness.shot(mc, "03_asleep");
		}
		// the neighbours: asked every 5 ticks from then (B533: one may still be lying down), given up 10 seconds on
		if (sleptAt > 0 && tick >= sleptAt + 40 && neighboursCheckedAt < 0 && tick % 5 == 0) {
			if (neighboursAsleep) {
				neighboursCheckedAt = tick;
			} else if (tick <= sleptAt + 240) {
				server.execute(() -> neighboursAsleep = neighbours.size() == 2 && neighbours.stream().allMatch(Villager::isSleeping));
			} else {
				neighboursCheckedAt = tick;
				wrong.add("a neighbour isn't asleep in the east home");
			}
		}
		if (neighboursCheckedAt > 0 && tick == neighboursCheckedAt + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "the villager without a bed claimed a free bed of the Terrace's west home, climbed its ladder and slept in it; the neighbours slept in the east home"
				: String.join("; ", wrong));
			finish(mc);
		}
		if (sleptAt < 0 && tick == movedInAt + LIMIT) {
			ScreenshotHarness.shot(mc, "99_timeout");
			Showcase.check(false, "the villager without a bed didn't sleep in the west home in time: claimed " + claimed + ", climbing " + climbing
				+ (newcomer == null ? "" : ", at " + newcomer.blockPosition().subtract(ORIGIN).toShortString() + " from the Terrace's corner")
				+ (wrong.isEmpty() ? "" : "; " + String.join("; ", wrong)));
			finish(mc);
		}
	}

	/** The lawn and the Terrace on it, at dusk. */
	private void setUp(ServerLevel level) {
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
		level.setDayTime(11800);
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				for (int y = LAWN + 1; y < LAWN + 20; y++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
				level.setBlock(new BlockPos(x, LAWN, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			}
		}
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("terrace")).orElse(null);
		if (house == null) {
			wrong.add("no Terrace structure");
			return;
		}
		house.placeInWorld(level, ORIGIN, ORIGIN, new StructurePlaceSettings(), RandomSource.create(304L), 2);
	}

	/** The two neighbours by the east home's beds and the newcomer outside the east door, once the beds count as beds. */
	private void moveIn(ServerLevel level) {
		if (movedIn) {
			return; // an earlier ask, queued before this one, found the beds
		}
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("terrace")).orElse(null);
		if (house == null) {
			return;
		}
		BoundingBox box = BoundingBox.fromCorners(ORIGIN, ORIGIN.offset(house.getSize()).offset(-1, -1, -1));
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(Comparator.comparingInt(BlockPos::getX)).toList();
		bedsFound = beds.size();
		if (beds.size() != 4) {
			return; // not all POIs yet: asked again in 5 ticks, for up to MOVE_IN_WAIT
		}
		westBeds.addAll(beds.subList(0, 2));
		for (BlockPos bed : beds.subList(2, 4)) {
			Villager v = EntityType.VILLAGER.create(level);
			if (v == null) {
				continue;
			}
			v.moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() - 1.5, 0, 0); // on the bedroom floor at the bed's foot
			v.setPersistenceRequired();
			level.addFreshEntity(v);
			level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
			v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
			neighbours.add(v);
		}
		newcomer = EntityType.VILLAGER.create(level);
		if (newcomer != null) {
			newcomer.moveTo(ORIGIN.getX() + 11.5, ORIGIN.getY(), ORIGIN.getZ() - 1.5, 0, 0); // on the lawn by the east home's door
			newcomer.setPersistenceRequired();
			level.addFreshEntity(newcomer);
		}
		movedIn = true;
	}

	/** Whether the newcomer has claimed a west bed, is on the west home's ladder, is asleep in his bed. */
	private void watch() {
		if (newcomer == null) {
			return;
		}
		claimed |= newcomer.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> westBeds.contains(g.pos())).isPresent();
		BlockPos feet = newcomer.blockPosition();
		climbing |= feet.getX() == ORIGIN.getX() + 5 && feet.getZ() == ORIGIN.getZ() + 7 && feet.getY() >= ORIGIN.getY() + 2;
		asleep |= claimed && newcomer.isSleeping() && newcomer.getSleepingPos().filter(westBeds::contains).isPresent();
	}

	/** A still camera that isn't the player (a client-only armor stand): it can stand inside the house without walls or villagers in the way. */
	private void look(Minecraft mc, Vec3 eye, Vec3 at) {
		if (camera == null || camera.level() != mc.level) {
			camera = new ArmorStand(mc.level, eye.x, eye.y, eye.z);
			camera.setInvisible(true);
		}
		Vec3 d = at.subtract(eye);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		camera.moveTo(eye.x, eye.y - camera.getEyeHeight(), eye.z, yaw, pitch);
		camera.setYHeadRot(yaw);
		mc.options.setCameraType(CameraType.FIRST_PERSON);
		mc.setCameraEntity(camera);
	}

	private void finish(Minecraft mc) {
		mc.setCameraEntity(mc.player);
		mc.stop();
	}
}
