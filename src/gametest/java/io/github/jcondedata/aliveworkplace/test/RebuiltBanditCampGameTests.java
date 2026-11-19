package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 52.5: the Bandit Camp was drawn again from scratch. Camps already standing on a live server were set down from the
 * old drawing; when their chief falls they still come down, all of them (the cleanup finds the drawing a camp was set
 * down with among today's and the older ones the mod ships in {@code blueprint_history}), and so does a camp of the new
 * drawing. The old-world sweep finds an old drawing's camp too.
 */
public class RebuiltBanditCampGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);
	/** The camp's fire in every drawing (Lairs.FIRE): its middle. */
	private static final BlockPos FIRE_SPOT = new BlockPos(7, 1, 6);
	/** The drawing on the owner's server before 52.5 (every release from 0.150.0 to 0.156.0). */
	private static final String OLD = "v0.150.0";
	/**
	 * The drawing before that (B537, 2026-10-26): camps set down before it may still stand too. Above its ground layer it
	 * is the same as {@link #OLD} (B537 changed only its ground), so the cleanup may know such a camp by either key.
	 */
	private static final String OLDER = "a12542b71";
	private static final int GONE = CampCleanup.DELAY + CampCleanup.CLEAR_TICKS + 10;

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
			CampCleanup.SWEEP_FROM = Lairs.NEAR - 16;
			CampCleanup.SWEEP_TO = Lairs.FAR + 16;
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
		});
		return hall;
	}

	private static Blueprint today(ServerLevel level) {
		StructureTemplate template = level.getStructureManager().get(Lairs.BANDIT_CAMP).orElseThrow();
		return Blueprint.fromTemplate(Lairs.BANDIT_CAMP, template, Lookup.lookup(BuiltInRegistries.BLOCK));
	}

	private static Blueprint older(GameTestHelper helper, String key) {
		return Refreshes.olderDrawing(helper.getLevel().getServer(), Lairs.BANDIT_CAMP, key)
			.orElseThrow(() -> new IllegalStateException("the Bandit Camp's drawing " + key + " isn't shipped in blueprint_history"));
	}

	private static StructurePlaceSettings turn(int r) {
		return new StructurePlaceSettings().setRotation(Rotation.values()[r]);
	}

	private static BlockPos origin(BlockPos middle, int r) {
		return middle.subtract(StructureTemplate.calculateRelativePosition(turn(r), FIRE_SPOT));
	}

	private static BlockPos at(BlockPos middle, int r, BlockPos cell) {
		return origin(middle, r).offset(StructureTemplate.calculateRelativePosition(turn(r), cell));
	}

	/** The drawing's blocks above its ground layer (air left out). */
	private static List<Blueprint.Entry> standing(Blueprint drawing) {
		return drawing.blocks().stream().filter(e -> e.pos().getY() >= 1 && !e.state().isAir()).toList();
	}

	/** How many of the drawing's standing blocks are in the world as drawn, turned {@code r} about {@code middle}. */
	private static int asDrawn(ServerLevel level, Blueprint drawing, BlockPos middle, int r) {
		int n = 0;
		for (Blueprint.Entry e : standing(drawing)) {
			if (MaterialRules.matches(level.getBlockState(at(middle, r, e.pos())), e.state().mirror(Mirror.NONE).rotate(Rotation.values()[r]))) {
				n++;
			}
		}
		return n;
	}

	/** The turn today's camp was set down with round {@code middle}: the one where all of it stands. */
	private static int turnOf(GameTestHelper helper, BlockPos middle) {
		ServerLevel level = helper.getLevel();
		Blueprint drawing = today(level);
		for (int r = 0; r < 4; r++) {
			if (asDrawn(level, drawing, middle, r) == standing(drawing).size()) {
				return r;
			}
		}
		throw new IllegalStateException("today's camp doesn't stand whole round " + middle);
	}

	/** Takes {@code from}'s standing blocks away and sets {@code to} down in their place, with the same turn (as on a live server). */
	private static void redraw(ServerLevel level, Blueprint from, Blueprint to, BlockPos middle, int r) {
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (Blueprint.Entry e : standing(from)) {
			level.setBlock(at(middle, r, e.pos()), Blocks.AIR.defaultBlockState(), flags);
		}
		placeDrawing(level, to, middle, r);
	}

	private static void placeDrawing(ServerLevel level, Blueprint drawing, BlockPos middle, int r) {
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (Blueprint.Entry e : drawing.blocks()) {
			if (!e.state().isAir()) {
				level.setBlock(at(middle, r, e.pos()), e.state().mirror(Mirror.NONE).rotate(Rotation.values()[r]), flags);
			}
		}
	}

	/** The camp's fires, chests, wool and beds within 9 blocks of its middle, up to 5 above it. */
	private static int[] count(ServerLevel level, BlockPos middle) {
		int[] n = new int[4];
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 0, -9), middle.offset(9, 10, 9))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof CampfireBlock) {
				n[0]++;
			} else if (s.is(Blocks.CHEST)) {
				n[1]++;
			} else if (s.is(BlockTags.WOOL)) {
				n[2]++;
			} else if (s.is(BlockTags.BEDS)) {
				n[3]++;
			}
		}
		return n;
	}

	private static String str(int[] n) {
		return "fires " + n[0] + ", chests " + n[1] + ", wool " + n[2] + ", beds " + n[3];
	}

	private static void killChief(ServerLevel level, Lairs.Lair lair) {
		if (level.getEntity(lair.captain()) instanceof LivingEntity chief) {
			chief.kill();
		}
	}

	/**
	 * A camp the old drawing set down (as on the owner's server before 52.5), with its lair's record and ground: its chief
	 * falls, and every block of it comes down, its ground back to the land's.
	 */
	//$ gametest_ticks_batch AREA '400' '"rebuiltCampOld"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "rebuiltCampOld")
	public void anOldDrawingsCampComesDownWhenItsChiefFalls(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			BlockState land = level.getBlockState(helper.absolutePos(CAMP));
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			BlockPos middle = lair.pos();
			int r = turnOf(helper, middle);
			Blueprint old = older(helper, OLD);
			redraw(level, today(level), old, middle, r);
			int standing = standing(old).size();
			helper.assertTrue(asDrawn(level, old, middle, r) == standing, "setup: the old camp doesn't stand whole");
			int[] before = count(level, middle);
			helper.assertTrue(before[0] == 1 && before[1] == 2 && before[2] > 20 && before[3] > 0, "setup: the old camp as set down: " + str(before));
			killChief(level, lair);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && CampCleanup.clearing(level).size() == 1, "the old camp isn't coming down: " + CampCleanup.clearing(level));
			helper.runAfterDelay(CampCleanup.DELAY + 5, () -> {
				List<CampCleanup.Clearing> going = CampCleanup.clearing(level);
				helper.assertTrue(going.size() == 1 && OLD.equals(going.get(0).drawing()) && going.get(0).rotation() == r,
					"the old camp wasn't known as the old drawing turned " + r + ": " + going);
			});
			helper.runAfterDelay(GONE, () -> {
				int left = asDrawn(level, old, middle, r);
				helper.assertTrue(left == 0, left + " of the old camp's " + standing + " blocks still stand: " + str(count(level, middle)));
				int[] after = count(level, middle);
				helper.assertTrue(after[0] == 0 && after[1] == 0 && after[2] == 0 && after[3] == 0, "the old camp still stands: " + str(after));
				helper.assertTrue(level.getBlockState(middle.below()).equals(land), "the camp's ground isn't back to the land's: " + level.getBlockState(middle.below()));
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				helper.succeed();
			});
		});
	}

	/** A camp of today's drawing: known as today's, and every block of it comes down when its chief falls. */
	//$ gametest_ticks_batch AREA '400' '"rebuiltCampNew"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "rebuiltCampNew")
	public void aNewDrawingsCampComesDownWhenItsChiefFalls(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			BlockPos middle = lair.pos();
			int r = turnOf(helper, middle);
			Blueprint drawing = today(level);
			int[] before = count(level, middle);
			helper.assertTrue(before[0] == 1 && before[1] == 2 && before[2] > 20 && before[3] == 10, "the new camp as set down: " + str(before));
			killChief(level, lair);
			helper.runAfterDelay(CampCleanup.DELAY + 5, () -> {
				List<CampCleanup.Clearing> going = CampCleanup.clearing(level);
				helper.assertTrue(going.size() == 1 && "".equals(going.get(0).drawing()) && going.get(0).rotation() == r,
					"the new camp wasn't known as today's drawing turned " + r + ": " + going);
			});
			helper.runAfterDelay(GONE, () -> {
				int left = asDrawn(level, drawing, middle, r);
				helper.assertTrue(left == 0, left + " of the new camp's " + standing(drawing).size() + " blocks still stand");
				int[] after = count(level, middle);
				helper.assertTrue(after[0] == 0 && after[1] == 0 && after[2] == 0 && after[3] == 0, "the new camp still stands: " + str(after));
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				helper.succeed();
			});
		});
	}

	/**
	 * A camp left standing from before B209 and from the drawing before last (no record, no ground record, unturned): the
	 * hall's sweep of an old world finds it by its fire and an older drawing (not today's), and it comes all the way down.
	 */
	//$ gametest_ticks_batch AREA '300' '"rebuiltCampSweep"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "rebuiltCampSweep")
	public void theSweepFindsAnOlderDrawingsCamp(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		CampCleanup.SWEEP_FROM = 0;
		CampCleanup.SWEEP_TO = 26;
		BlockPos middle = helper.absolutePos(CAMP.above());
		helper.runAfterDelay(2, () -> {
			Blueprint old = older(helper, OLDER);
			placeDrawing(level, old, middle, 0);
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 1, -9), middle.offset(9, 40, 9))) {
				if (level.getBlockState(p).is(Blocks.BARRIER)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2); // (the sweep reads the sky's height)
				}
			}
			Lairs.Data data = Lairs.Data.get(level);
			CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
			tag.putBoolean("b209", true);
			ListTag unswept = new ListTag();
			CompoundTag here = new CompoundTag();
			here.putLong("hall", hall.asLong());
			unswept.add(here);
			tag.put("unswept", unswept);
			data.read(tag);
			int standing = standing(old).size();
			helper.assertTrue(asDrawn(level, old, middle, 0) == standing && count(level, middle)[0] == 1, "setup: the older camp doesn't stand");
			CampCleanup.sweep(level, hall);
			List<CampCleanup.Clearing> going = CampCleanup.clearing(level);
			helper.assertTrue(going.size() == 1 && going.get(0).rotation() == 0 && (OLDER.equals(going.get(0).drawing()) || OLD.equals(going.get(0).drawing())),
				"the older camp wasn't found as an older drawing (" + OLDER + " or " + OLD + "), unturned: " + going);
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				int left = asDrawn(level, old, middle, 0);
				helper.assertTrue(left == 0, left + " of the older camp's " + standing + " blocks still stand: " + str(count(level, middle)));
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				helper.succeed();
			});
		});
	}

	/** Which drawings the cleanup can fall back to: both of the camp's older ones ship. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theCampsOlderDrawingsShip(GameTestHelper helper) {
		List<String> keys = new ArrayList<>(Refreshes.olderKeys(helper.getLevel().getServer(), Lairs.BANDIT_CAMP));
		helper.assertTrue(keys.contains(OLD) && keys.contains(OLDER), "the Bandit Camp's older drawings shipped: " + keys);
		for (String key : keys) {
			Blueprint drawing = older(helper, key);
			helper.assertTrue(drawing.blocks().stream().anyMatch(e -> e.pos().equals(FIRE_SPOT) && e.state().getBlock() instanceof CampfireBlock),
				"the drawing " + key + " has no fire at " + FIRE_SPOT + ": it can't be found by the camp's middle");
		}
		helper.succeed();
	}
}
