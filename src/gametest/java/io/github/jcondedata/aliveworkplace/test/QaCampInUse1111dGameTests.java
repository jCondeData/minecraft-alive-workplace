package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane D, 2026-11-11, B209's sweep of old worlds (CampCleanup): old bandit camps are cleared "with no living foe of
 * ours near and no running story or bounty using them". QaCampInUse1109d covers a standing lair and a bounty's hideout;
 * this covers the other record that keeps a camp: a running Bandit King story's place (one of its camps), by the
 * place's spot or its built origin within 24 blocks of the camp's fire. The story's band is away (no living foe near),
 * so only the story's record keeps the camp. Lane D's 1111 mutation run of CampCleanup.inUse (:393) found no test
 * reaching the story check.
 */
public class QaCampInUse1111dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** The camp's fire: 12 and 16 blocks from the hall, 20 in all (inside the sweep's ring 0 to 30 set below). */
	private static final BlockPos FIRE = new BlockPos(13, 2, 17);
	/** The bandit camp blueprint's spot for its fire (as B209GameTests). */
	private static final BlockPos FIRE_SPOT = new BlockPos(7, 1, 6);

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

	/** A camp left from before B209 (unturned, no record); the area's roof over it opened. */
	private static void oldCamp(GameTestHelper helper, BlockPos middle) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(Lairs.BANDIT_CAMP).orElseThrow();
		BlockPos origin = middle.subtract(FIRE_SPOT);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(Rotation.NONE), RandomSource.create(41), 2);
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 1, -9), middle.offset(9, 40, 9))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** The village is to be swept again (as a world saved before B209). */
	private static void unswept(ServerLevel level, BlockPos hall) {
		Lairs.Data data = Lairs.Data.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		tag.putBoolean("b209", true);
		ListTag list = new ListTag();
		CompoundTag at = new CompoundTag();
		at.putLong("hall", hall.asLong());
		list.add(at);
		tag.put("unswept", list);
		data.read(tag);
	}

	/** Sweeps the ring 0 to 30; whether the camp was found (and is coming down). */
	private static boolean swept(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		CampCleanup.SWEEP_FROM = 0;
		CampCleanup.SWEEP_TO = 30;
		unswept(level, hall);
		CampCleanup.sweep(level, hall);
		return !CampCleanup.clearing(level).isEmpty();
	}

	/**
	 * A Bandit King story running for the village at {@code storyHall} (its state as the story keeps it, chapter 0, its
	 * band all away), with {@code place} its one camp. The story's village is far off; only its camp's record is here.
	 */
	private static ArcState runningKing(ServerLevel level, BlockPos storyHall, ArcState.ArcPlace place) throws ReflectiveOperationException {
		Stories.Data data = Stories.Data.get(level);
		java.lang.reflect.Method entry = Stories.Data.class.getDeclaredMethod("entry", BlockPos.class);
		entry.setAccessible(true);
		Object e = entry.invoke(data, storyHall);
		java.lang.reflect.Field arc = e.getClass().getDeclaredField("arc");
		arc.setAccessible(true);
		ArcState s = new ArcState(BanditKingStory.ARC.toString());
		s.started = true;
		s.nextChapterDay = Long.MAX_VALUE / 2;
		s.places.add(place);
		arc.set(e, s);
		return s;
	}

	/**
	 * A running Bandit King story's camp, its band away: the camp is not swept while the story runs, whether the
	 * story's record of it is on the fire or 24 blocks off it, by the place's spot or its built origin; 25 blocks from
	 * both, it's someone else's old camp and is swept.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCampInUse1111dKing"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCampInUse1111dKing")
	public void aRunningBanditKingsCampIsNotSweptWhileTheStoryRuns(GameTestHelper helper) {
		BlockPos hall = village(helper);
		BlockPos middle = helper.absolutePos(FIRE);
		ServerLevel level = helper.getLevel();
		BlockPos storyHall = hall.offset(0, 0, 900);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(storyHall));
		helper.runAfterDelay(2, () -> {
			oldCamp(helper, middle);
			ArcState.ArcPlace camp = new ArcState.ArcPlace("camp", middle, List.of("aliveworkplace:camp/bandit_camp"), 0);
			try {
				runningKing(level, storyHall, camp);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("setup: couldn't record the running story", e);
			}
			helper.assertTrue(BanditKingStory.running(level).stream().anyMatch(r -> r.hall().equals(storyHall) && r.state().places.contains(camp)),
				"setup: the Bandit King story isn't running");
			helper.assertTrue(!swept(helper, hall), "a running Bandit King story's camp (its band away) was swept as an old camp");
			camp.spot = middle.offset(24, 0, 0);
			helper.assertTrue(!swept(helper, hall), "a camp 24 blocks from a running Bandit King story's camp spot was swept");
			camp.spot = middle.offset(60, 0, 0);
			camp.origin = middle.offset(0, 0, -24);
			helper.assertTrue(!swept(helper, hall), "a camp 24 blocks from a running Bandit King story's built camp (its origin) was swept");
			camp.origin = middle.offset(0, 0, -25);
			helper.assertTrue(swept(helper, hall), "an old camp 25 blocks from the story's camp spot and origin wasn't swept");
			Stories.Data.get(level).remove(storyHall);
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				helper.assertTrue(CampCleanup.clearing(helper.getLevel()).isEmpty(), "the clearing never ended");
				helper.succeed();
			});
		});
	}
}
