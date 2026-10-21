package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B225 (the owner's live server): a bandit camp taken down (B209) left its levelled pad and its patch of trampled earth
 * and gravel for good, scarring the land as camps came and went. Now the ground a camp covers is recorded when it is made
 * and put back when it comes down (a save and reload mid-clearing carries the record over); left as ruins, its tents stay
 * but the open ground round them goes back; and a camp made before the fix (no record) has its pad blended into the land
 * round it: grass on top, no trampled earth or gravel, no sharp steps.
 */
public class B225GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** Ticks till a broken camp is all gone, with a little to spare. */
	private static final int GONE = CampCleanup.DELAY + CampCleanup.CLEAR_TICKS + 10;
	/** The part of the area looked at: the camp and a little round it (relative x and z, y from the floor up). */
	private static final int FROM = 4;
	private static final int TO = 26;
	private static final int TOP = 14;

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
			CampCleanup.RUINS = false;
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
		});
		return hall;
	}

	/** The land's height at relative column x: rising one block every four eastward (a slope across the camp). */
	private static int height(int x) {
		return 2 + Math.max(0, (x - 8) / 4);
	}

	/** A grassy slope over the floor; the camp's ground, its middle column's top. */
	private static BlockPos slope(GameTestHelper helper) {
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				int top = height(x);
				for (int y = 2; y < top; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
				}
				helper.setBlock(new BlockPos(x, top, z), Blocks.GRASS_BLOCK);
			}
		}
		return helper.absolutePos(new BlockPos(15, height(15), 15));
	}

	/** Every block of the part looked at. */
	private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(FROM, 1, FROM), new BlockPos(TO, TOP, TO))) {
			out.put(p.immutable(), helper.getBlockState(p));
		}
		return out;
	}

	/** The first block that isn't as it was, or null. */
	private static String changed(GameTestHelper helper, Map<BlockPos, BlockState> before) {
		int n = 0;
		String first = null;
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			BlockState now = helper.getBlockState(e.getKey());
			if (!now.equals(e.getValue())) {
				n++;
				if (first == null) {
					first = e.getKey().toShortString() + " is " + now + ", was " + e.getValue();
				}
			}
		}
		return first == null ? null : n + " blocks changed, e.g. " + first;
	}

	/** Whether {@code s} is the camp's rubble: its trampled earth, gravel or the fire's stones. */
	private static boolean rubble(BlockState s) {
		return s.is(Blocks.COARSE_DIRT) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRAVEL) || s.is(Blocks.COBBLESTONE) || s.is(BlockTags.FIRE);
	}

	private static Lairs.Lair camp(GameTestHelper helper, BlockPos hall, BlockPos ground) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BanditCamps.found(level, hall, ground) != null, "no camp was made");
		return Lairs.near(level, hall).orElseThrow();
	}

	private static void killChief(ServerLevel level, Lairs.Lair lair) {
		if (level.getEntity(lair.captain()) instanceof LivingEntity chief) {
			chief.kill();
		}
	}

	/** A camp on a slope taken down: every block round it is as it was before the camp (the slope, its grass), no rubble. */
	//$ gametest_ticks_batch AREA '400' '"b225Slope"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b225Slope")
	public void b225ACampOnASlopeLeavesTheLandAsItWas(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = slope(helper);
		helper.runAfterDelay(2, () -> {
			Map<BlockPos, BlockState> before = snapshot(helper);
			Lairs.Lair lair = camp(helper, hall, ground);
			helper.assertTrue(changed(helper, before) != null, "the camp changed nothing");
			killChief(level, lair);
			helper.assertTrue(CampCleanup.clearing(level).size() == 1 && CampCleanup.clearing(level).get(0).ground() != null,
				"the camp coming down has no record of its ground: " + CampCleanup.clearing(level));
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				String diff = changed(helper, before);
				helper.assertTrue(diff == null, "the land isn't as it was: " + diff);
				helper.succeed();
			});
		});
	}

	/**
	 * B288 (a showcase run of camp_cleared: one column of the rise came back a block too high): while the camp stands, grass
	 * creeps over the bare dirt it filled its floor with (and its trampled dirt); once it is down, that grass goes too and the
	 * land is as it was. (Here the creeping is done at once, as random ticks do over time.)
	 */
	//$ gametest_ticks_batch AREA '400' '"b288Grass"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b288Grass")
	public void b288GrassCreptOverTheCampsFloorStillGoes(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = slope(helper);
		helper.runAfterDelay(2, () -> {
			Map<BlockPos, BlockState> before = snapshot(helper);
			Lairs.Lair lair = camp(helper, hall, ground);
			// (the fire stands one above the camp's floor; relative y as the helper counts it)
			int floor = lair.pos().getY() - 1 - helper.absolutePos(BlockPos.ZERO).getY();
			int crept = 0;
			for (int x = FROM; x <= TO; x++) {
				for (int z = FROM; z <= TO; z++) {
					BlockPos p = new BlockPos(x, floor, z);
					if (helper.getBlockState(p).is(Blocks.DIRT) && helper.getBlockState(p.above()).isAir()) {
						helper.setBlock(p, Blocks.GRASS_BLOCK);
						if (before.get(p).isAir()) {
							crept++; // (camp's own fill, where the land was lower)
						}
					}
				}
			}
			helper.assertTrue(crept > 0, "no bare fill on the camp's floor for grass to creep over");
			killChief(level, lair);
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				String diff = changed(helper, before);
				helper.assertTrue(diff == null, "the land isn't as it was: " + diff);
				helper.succeed();
			});
		});
	}

	/** Saved and reloaded before and halfway through: the ground's record carries over and the land comes back. */
	//$ gametest_ticks_batch AREA '400' '"b225Reload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b225Reload")
	public void b225AReloadMidClearingStillPutsTheLandBack(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = slope(helper);
		helper.runAfterDelay(2, () -> {
			Map<BlockPos, BlockState> before = snapshot(helper);
			Lairs.Lair lair = camp(helper, hall, ground);
			Lairs.Data.get(level).roundTrip(level.registryAccess()); // (the standing camp's record saved)
			killChief(level, lair);
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(CampCleanup.clearing(level).size() == 1 && CampCleanup.clearing(level).get(0).ground() != null,
				"after a reload the record is gone: " + CampCleanup.clearing(level));
			helper.runAfterDelay(CampCleanup.DELAY + CampCleanup.CLEAR_TICKS / 2, () -> {
				helper.assertTrue(changed(helper, before) != null, "the land came back before the camp was down");
				Lairs.Data.get(level).roundTrip(level.registryAccess());
				helper.assertTrue(CampCleanup.clearing(level).size() == 1 && CampCleanup.clearing(level).get(0).ground() != null,
					"after a reload halfway the record is gone: " + CampCleanup.clearing(level));
				helper.runAfterDelay(CampCleanup.CLEAR_TICKS / 2 + 10, () -> {
					helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
					String diff = changed(helper, before);
					helper.assertTrue(diff == null, "the land isn't as it was: " + diff);
					helper.succeed();
				});
			});
		});
	}

	/** With campRuins on: the tents stay, the fire out; every column without ruins on it is as it was, no rubble in it. */
	//$ gametest_ticks_batch AREA '400' '"b225Ruins"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b225Ruins")
	public void b225RuinsKeepTheirTentsButNotAFlatScar(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = slope(helper);
		CampCleanup.RUINS = true;
		helper.runAfterDelay(2, () -> {
			Map<BlockPos, BlockState> before = snapshot(helper);
			Lairs.Lair lair = camp(helper, hall, ground);
			int wool = wool(helper);
			killChief(level, lair);
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the camp is still listed");
				helper.assertTrue(wool(helper) == wool && wool > 20, "the ruins' tents: " + wool(helper) + ", was " + wool);
				helper.assertTrue(!level.getBlockState(lair.pos()).getValue(CampfireBlock.LIT), "the fire still burns");
				int open = 0;
				for (int x = FROM; x <= TO; x++) {
					for (int z = FROM; z <= TO; z++) {
						boolean ruin = false;
						for (int y = 1; y <= TOP && !ruin; y++) {
							BlockState s = helper.getBlockState(new BlockPos(x, y, z));
							ruin = !s.isAir() && !s.is(Blocks.DIRT) && !s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.SMOOTH_STONE) && !rubble(s);
						}
						if (ruin) {
							continue; // (the ruins stand there, on the camp's ground)
						}
						open++;
						for (int y = 1; y <= TOP; y++) {
							BlockPos p = new BlockPos(x, y, z);
							helper.assertTrue(helper.getBlockState(p).equals(before.get(p)),
								"open ground by the ruins isn't as it was at " + p.toShortString() + ": " + helper.getBlockState(p) + ", was " + before.get(p));
						}
					}
				}
				helper.assertTrue(open > 300, "too few open columns were looked at: " + open);
				helper.succeed();
			});
		});
	}

	private static int wool(GameTestHelper helper) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(FROM, 1, FROM), new BlockPos(TO, TOP, TO))) {
			if (helper.getBlockState(p).is(BlockTags.WOOL)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * A camp made before the fix (its record saved without the ground's): it still comes down, and its pad is blended into
	 * the slope round it: grass on top of every column, no rubble anywhere, no step of more than one block, each column
	 * within two blocks of the slope's own height.
	 */
	//$ gametest_ticks_batch AREA '400' '"b225Old"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b225Old")
	public void b225ACampFromBeforeTheFixIsBlendedIntoTheLand(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = slope(helper);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = camp(helper, hall, ground);
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
			tag.remove("grounds"); // (as saved before B225)
			data.read(tag);
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the old save's camp was lost");
			killChief(level, lair);
			helper.assertTrue(CampCleanup.clearing(level).size() == 1 && CampCleanup.clearing(level).get(0).ground() == null,
				"the old camp: " + CampCleanup.clearing(level));
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				int[][] tops = new int[TO + 1][TO + 1];
				for (int x = FROM; x <= TO; x++) {
					for (int z = FROM; z <= TO; z++) {
						int top = 0;
						for (int y = 1; y <= TOP; y++) {
							BlockPos p = new BlockPos(x, y, z);
							BlockState s = helper.getBlockState(p);
							helper.assertTrue(!rubble(s), "rubble left at " + p.toShortString() + ": " + s);
							if (!s.isAir()) {
								top = y;
							}
						}
						BlockState surface = helper.getBlockState(new BlockPos(x, top, z));
						helper.assertTrue(surface.is(Blocks.GRASS_BLOCK), "the top at " + x + "," + z + " is " + surface);
						helper.assertTrue(Math.abs(top - height(x)) <= 2, "the land at " + x + "," + z + " is at " + top + ", the slope at " + height(x));
						tops[x][z] = top;
					}
				}
				for (int x = FROM; x < TO; x++) {
					for (int z = FROM; z < TO; z++) {
						helper.assertTrue(Math.abs(tops[x][z] - tops[x + 1][z]) <= 1 && Math.abs(tops[x][z] - tops[x][z + 1]) <= 1,
							"a sharp step at " + x + "," + z + ": " + tops[x][z] + " by " + tops[x + 1][z] + " and " + tops[x][z + 1]);
					}
				}
				helper.succeed();
			});
		});
	}
}
