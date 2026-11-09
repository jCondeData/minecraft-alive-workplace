package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
 * QA lane D, 2026-11-09, B209's sweep of old worlds (CampCleanup: the hall's round clears old bandit camps "with no
 * living foe of ours near and no running story or bounty using them"; a standing lair's own camp is never swept). The
 * band's living men are one guard ({@code guarded}); these tests take the band away, so only the records keep the camp:
 * a standing lair whose band is out (its captain not in the world) within 24 blocks of the camp's fire, or a bounty whose
 * hideout is there. Lane D's 1109-0711 mutation run of CampCleanup left inUse's lair check (:394) with no test reaching
 * it, masked by the band being home.
 */
public class QaCampInUse1109dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** The camp's fire: 12 and 16 blocks from the hall, 20 in all (inside the sweep's ring 0 to 30 set below). */
	private static final BlockPos FIRE = new BlockPos(13, 2, 17);
	/** The bandit camp blueprint's spot for its fire (as B209GameTests). */
	private static final BlockPos FIRE_SPOT = new BlockPos(7, 1, 6);
	/** The captains of the lairs these tests made. */
	private static final java.util.Set<UUID> MINE = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
			lairs(level, List.of());
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

	/**
	 * The world's standing lairs become exactly {@code at}: each a bandits lair of strength 4 with all 4 out on a raid,
	 * its captain a UUID with nobody in the world (the band away), preying on a village 300 blocks off, as a saved file
	 * holds them. Everything else the file holds stays.
	 */
	private static void lairs(ServerLevel level, List<BlockPos> at) {
		Lairs.Data data = Lairs.Data.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		ListTag camps = new ListTag();
		for (Tag t : tag.getList("camps", Tag.TAG_COMPOUND)) {
			if (!MINE.contains(Nbt.getUuid((CompoundTag) t, "chief"))) {
				camps.add(t); // (other tests' lairs stay as they are)
			}
		}
		int n = 0;
		for (BlockPos pos : at) {
			CompoundTag c = new CompoundTag();
			c.putLong("pos", pos.asLong());
			c.putLong("hall", pos.offset(300 + 40 * n++, 0, 0).asLong());
			UUID chief = UUID.randomUUID();
			MINE.add(chief);
			Nbt.putUuid(c, "chief", chief);
			c.putLong("day", 0);
			c.putString("culture", "aliveworkplace:bandits");
			c.putInt("strength", 4);
			c.putLong("grown", 0);
			c.putInt("out", 4);
			c.putInt("lost", 0);
			c.putInt("name", 0);
			camps.add(c);
		}
		tag.put("camps", camps);
		data.read(tag);
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

	private static int standingLairs(ServerLevel level) {
		CompoundTag tag = Lairs.Data.get(level).save(new CompoundTag(), level.registryAccess());
		return (int) tag.getList("camps", Tag.TAG_COMPOUND).stream().filter(t -> MINE.contains(Nbt.getUuid((CompoundTag) t, "chief"))).count();
	}

	/**
	 * A lair still standing by the camp, its band all out: its camp is not swept, whether the lair's record is on the
	 * fire itself or exactly 24 blocks off it (the reach of "a standing lair is there"). With the lair 25 blocks off the
	 * camp is someone else's old camp and is swept.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCampInUse1109dLair"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCampInUse1109dLair")
	public void aStandingLairWithItsBandAwayKeepsItsCampFromTheSweep(GameTestHelper helper) {
		BlockPos hall = village(helper);
		BlockPos middle = helper.absolutePos(FIRE);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			oldCamp(helper, middle);
			lairs(level, List.of(middle));
			helper.assertTrue(standingLairs(level) == 1, "setup: the lair's record didn't load");
			helper.assertTrue(!swept(helper, hall), "a standing lair's camp (its band out on a raid) was swept as an old camp");
			lairs(level, List.of(middle.offset(24, 0, 0)));
			helper.assertTrue(!swept(helper, hall), "a camp 24 blocks from a standing lair was swept");
			lairs(level, List.of(middle.offset(0, 0, -24)));
			helper.assertTrue(!swept(helper, hall), "a camp 24 blocks (north) from a standing lair was swept");
			lairs(level, List.of(middle.offset(25, 0, 0)));
			helper.assertTrue(swept(helper, hall), "an old camp 25 blocks from the nearest standing lair wasn't swept");
			lairs(level, List.of());
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				helper.assertTrue(CampCleanup.clearing(helper.getLevel()).isEmpty(), "the clearing never ended");
				helper.succeed();
			});
		});
	}

	/**
	 * A bounty's hideout is the camp (its captain away from it): the camp is not swept while the bounty is up, by its
	 * spot or its origin within 24 blocks; once the bounty is gone, the camp is an old one and goes.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCampInUse1109dBounty"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCampInUse1109dBounty")
	public void aBountysHideoutIsNotSweptWhileTheBountyIsUp(GameTestHelper helper) {
		BlockPos hall = village(helper);
		BlockPos middle = helper.absolutePos(FIRE);
		ServerLevel level = helper.getLevel();
		Bounties.Data bounties = Bounties.Data.get(level);
		UUID id = UUID.randomUUID();
		Leftovers.after(helper, () -> bounties.bounties.remove(id));
		helper.runAfterDelay(2, () -> {
			oldCamp(helper, middle);
			ArcState.ArcPlace hideout = new ArcState.ArcPlace("hideout", middle, List.of(), 0);
			Bounties.Bounty b = new Bounties.Bounty(id, hall.offset(400, 0, 0), 0, 0, ResourceLocation.parse("aliveworkplace:none"), hideout, 0, 10, false);
			bounties.bounties.put(id, b);
			helper.assertTrue(!swept(helper, hall), "a bounty's hideout was swept as an old camp while the bounty is up");
			hideout.spot = middle.offset(-24, 0, 0);
			helper.assertTrue(!swept(helper, hall), "a camp 24 blocks from a bounty's hideout spot was swept");
			hideout.spot = middle.offset(-60, 0, 0);
			hideout.origin = middle.offset(0, 0, 24);
			helper.assertTrue(!swept(helper, hall), "a camp 24 blocks from a bounty's hideout origin was swept");
			hideout.origin = middle.offset(0, 0, 25);
			helper.assertTrue(swept(helper, hall), "an old camp 25 blocks from the hideout's spot and origin wasn't swept");
			bounties.bounties.remove(id);
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				helper.assertTrue(CampCleanup.clearing(helper.getLevel()).isEmpty(), "the clearing never ended");
				helper.succeed();
			});
		});
	}
}
