package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane-d-1107-0108), from B225's spec, for a bandit camp made before that fix (no record of its ground: its pad is
 * blended into the land round it). Mutation on CampGround left two lines of the blend unchecked:
 * <ul>
 *   <li>the land round the camp is read as the lie of the land, but not a player's wall standing in it (a wall is "not
 *   the lie of the land"): the pad beside a wall comes back level with the open land, not heaped up towards the wall's
 *   top;</li>
 *   <li>anyone standing on the pad when the land is blended back up over it is set on top of the new ground, not left
 *   inside it.</li>
 * </ul>
 */
public class QaCampBlend1107dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** Ticks from the clearing's start till the camp is all gone, with a little to spare. */
	private static final int GONE = CampCleanup.CLEAR_TICKS + 10;
	/** The flat land's top (relative y). */
	private static final int LAND = 3;

	/** A hall; everything undone at the end. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		BanditCamps.forget(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(Pig.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
		});
		return hall;
	}

	/** Flat grassland over the area's floor; the camp's ground, its middle column's top. */
	private static BlockPos flat(GameTestHelper helper) {
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				for (int y = 2; y < LAND; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
				}
				helper.setBlock(new BlockPos(x, LAND, z), Blocks.GRASS_BLOCK);
			}
		}
		return helper.absolutePos(new BlockPos(15, LAND, 15));
	}

	/** A camp made, its record of the ground taken away (as saved before B225), and the sky over it opened. */
	private static Lairs.Lair oldCamp(GameTestHelper helper, BlockPos hall, BlockPos ground) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BanditCamps.found(level, hall, ground) != null, "no camp was made");
		Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
		// Open sky over the camp and round it, as out in the world (the area's barrier roof would read as the land).
		BlockPos middle = lair.pos();
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-14, 1, -14), middle.offset(14, 40, 14))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		Lairs.Data data = Lairs.Data.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(tag.contains("grounds"), "the standing camp's ground isn't saved");
		tag.remove("grounds");
		data.read(tag);
		helper.assertTrue(Lairs.near(level, hall).isPresent(), "the old save's camp was lost");
		return Lairs.near(level, hall).orElseThrow();
	}

	/** The chief killed: the camp starts coming down, with no record of its ground. */
	private static void breakUp(GameTestHelper helper, Lairs.Lair lair) {
		ServerLevel level = helper.getLevel();
		if (level.getEntity(lair.captain()) instanceof LivingEntity chief) {
			chief.kill();
		}
		helper.assertTrue(CampCleanup.clearing(level).size() == 1 && CampCleanup.clearing(level).get(0).ground() == null,
			"the old camp isn't coming down without a record: " + CampCleanup.clearing(level));
	}

	/** Once the clearing has begun (its turn worked out), the camp's box (absolute). */
	private static BoundingBox box(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CampCleanup.clearing(level).size() == 1 && CampCleanup.clearing(level).get(0).rotation() >= 0,
			"(setup) the clearing hasn't begun: " + CampCleanup.clearing(level));
		CampCleanup.Clearing c = CampCleanup.clearing(level).get(0);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.values()[c.rotation()]);
		BlockPos origin = c.middle().subtract(StructureTemplate.calculateRelativePosition(settings, c.spot()));
		StructureTemplate template = level.getStructureManager().get(c.structure()).orElseThrow();
		return template.getBoundingBox(settings, origin);
	}

	/** The highest block that isn't air in the column (absolute x, z), from the area's floor up. */
	private static int top(ServerLevel level, int x, int z, int from, int to) {
		int top = from;
		for (int y = from; y <= to; y++) {
			if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
				top = y;
			}
		}
		return top;
	}

	/**
	 * A player's stone-brick wall, five blocks high, stands right beside the old camp all along one side. When the camp's
	 * pad is blended back, the wall is not the lie of the land: every column of the pad comes back at the flat land's
	 * height with grass on top (none heaped up towards the wall), and the wall still stands.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCampBlendWall1107d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCampBlendWall1107d")
	public void anOldCampBesideAPlayersWallBlendsToTheLandNotTheWall(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = flat(helper);
		int land = ground.getY();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = oldCamp(helper, hall, ground);
			breakUp(helper, lair);
			helper.runAfterDelay(CampCleanup.DELAY + 5, () -> {
				BoundingBox box = box(helper);
				// The wall: all three columns of the land round the camp on its east side, from the land up five blocks.
				for (int x = box.maxX() + 1; x <= box.maxX() + 3; x++) {
					for (int z = box.minZ() - 3; z <= box.maxZ() + 3; z++) {
						for (int y = land + 1; y <= land + 5; y++) {
							level.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
						}
					}
				}
				helper.runAfterDelay(GONE, () -> {
					helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
					for (int x = box.minX(); x <= box.maxX(); x++) {
						for (int z = box.minZ(); z <= box.maxZ(); z++) {
							int top = top(level, x, z, land - 1, land + 12);
							BlockState s = level.getBlockState(new BlockPos(x, top, z));
							helper.assertTrue(top == land && s.is(Blocks.GRASS_BLOCK), "the pad at " + x + "," + z + " (" + (box.maxX() - x + 1)
								+ " from the wall) came back at " + (top - land) + " over the land, its top " + s);
						}
					}
					for (int x = box.maxX() + 1; x <= box.maxX() + 3; x++) {
						for (int z = box.minZ() - 3; z <= box.maxZ() + 3; z++) {
							helper.assertTrue(level.getBlockState(new BlockPos(x, land + 5, z)).is(Blocks.STONE_BRICKS), "the player's wall is gone at " + x + "," + z);
						}
					}
					helper.succeed();
				});
			});
		});
	}

	/**
	 * While the old camp stands the land round it rises two blocks (a player builds it up). When the camp comes down its
	 * pad is blended up to that land, and a pig standing on the pad is set on top of the new ground, not left inside it.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCampBlendLift1107d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCampBlendLift1107d")
	public void anOldCampsPadBlendedUpLeavesNoOneInsideTheGround(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = flat(helper);
		int land = ground.getY();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = oldCamp(helper, hall, ground);
			breakUp(helper, lair);
			helper.runAfterDelay(CampCleanup.DELAY + 5, () -> {
				BoundingBox box = box(helper);
				// The land all round the camp raised two blocks (dirt, grass on top).
				for (int x = box.minX() - 3; x <= box.maxX() + 3; x++) {
					for (int z = box.minZ() - 3; z <= box.maxZ() + 3; z++) {
						if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
							continue;
						}
						level.setBlock(new BlockPos(x, land, z), Blocks.DIRT.defaultBlockState(), 2);
						level.setBlock(new BlockPos(x, land + 1, z), Blocks.DIRT.defaultBlockState(), 2);
						level.setBlock(new BlockPos(x, land + 2, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
					}
				}
				// A pig on the pad's corner (it can't wander, and a tent's cloth can't hurt it while the camp comes down).
				Pig pig = EntityType.PIG.create(level);
				pig.moveTo(box.minX() + 0.5, land + 1, box.minZ() + 0.5, 0, 0);
				pig.setNoAi(true);
				pig.setInvulnerable(true);
				level.addFreshEntity(pig);
				helper.runAfterDelay(GONE, () -> {
					helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
					int top = top(level, box.minX(), box.minZ(), land - 1, land + 12);
					helper.assertTrue(top == land + 2, "(setup) the pad's corner wasn't blended up to the raised land: its top at " + (top - land));
					helper.assertTrue(pig.isAlive() && level.noCollision(pig) && pig.getY() >= top + 1,
						"the pig was left inside the ground that came back: at " + (pig.getY() - land) + " over the old land, the ground's top at "
							+ (top - land) + ", in a block: " + !level.noCollision(pig));
					helper.succeed();
				});
			});
		});
	}
}
