package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatCommand;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA, lane D (2026-11-10): a hand-picked mutation run of {@code Lairs.breakUp} and {@code Lairs.leave} (17 mutants, the
 * 57 threat and lair test classes) left these alive, each a thing a player would notice:
 * <ul>
 * <li>breakUp without {@code forgetEverywhere}: the scouts' report of a broken camp outlives it, so the next camp made
 * on the same ground is "known" (its place on the Defense page) though no scout ever saw it (lairs.md: "Its record,
 * the scouts' report of it and its Defense page" go; B209);</li>
 * <li>leave without {@code CampCleanup.broken}: after {@code /workplace threat clear} the camp's tents, fire, beds and
 * chests stood for good (lairs.md: "A lair that leaves in peace ... packs up the same way");</li>
 * <li>leave without {@code Covens.lift}: a coven cleared off a village left its hexed villagers hexed, though a hex
 * "doesn't pass by itself" (covens.md) and the coven is gone.</li>
 * </ul>
 * ({@code takenByPeace.remove} in breakUp also survived: a stale captain id in the save, which nobody sees.)
 */
public class QaLairBreakUp1110dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	/** Ticks till a camp that is packing up is all gone, with a little to spare. */
	private static final int GONE = CampCleanup.DELAY + CampCleanup.CLEAR_TICKS + 10;

	/** A hall (radius 20) by day, Milestone 32 open and lairs on; everything put back afterwards. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		boolean lairs = Lairs.ENABLED;
		VillageHalls.RADIUS = 20;
		Expansions.openForTests = true;
		Lairs.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Lairs.forget(level);
		BanditCamps.forget(level);
		Scouts.forget(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)
				&& (m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)
					|| m.getTags().contains(Lairs.CAPTAIN_TAG))).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4), v -> true).forEach(v -> Sickness.recover(level, v));
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Covens.Data.get(level).forget(hall);
			Covens.forget();
			Lairs.forget(level);
			BanditCamps.forget(level);
			Scouts.forget(level);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
		});
		level.setDayTime(40 * 24000L + 1000);
		return hall;
	}

	/** The camp's blocks round its middle: campfires, chests, wool (tents) and beds (as B209GameTests counts them). */
	private static int[] count(ServerLevel level, BlockPos middle) {
		int[] n = new int[4];
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 0, -9), middle.offset(9, 5, 9))) {
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

	/** A scout of the village (a guard at a post with a spyglass in his off hand), standing still. */
	private static Villager scout(GameTestHelper helper) {
		BlockPos post = new BlockPos(4, 2, 4);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		villager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		villager.setInvulnerable(true);
		villager.setNoAi(true);
		return villager;
	}

	/**
	 * A scout trails the bandits home, so the village knows its camp. The chief is killed: the camp is broken up. A new
	 * band then makes camp on the very same ground (B209GameTests shows a new camp is made there): no scout has seen this
	 * one, so the village knows nothing of it (the Defense page shows only a rough "where" until a scout goes).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLairBreakUp1110dScouts"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLairBreakUp1110dScouts")
	public void aNewCampOnABrokenCampsGroundIsNotKnownFromTheOldReport(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		scout(helper);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "setup: no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(Scouts.trail(level, hall, Threats.BANDITS), "setup: the scout didn't trail the bandits home");
			Optional<Scouts.Known> known = Scouts.known(level, hall);
			helper.assertTrue(known.isPresent() && known.get().lair().equals(lair.pos()), "setup: the camp isn't known: " + known);
			helper.assertTrue(level.getEntity(lair.captain()) instanceof LivingEntity, "setup: the camp has no chief");
			((LivingEntity) level.getEntity(lair.captain())).kill();
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp is still recorded after its chief fell");
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "setup: the old camp is still coming down");
				helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "setup: no new camp was made on the same ground");
				Lairs.Lair next = Lairs.near(level, hall).orElseThrow();
				helper.assertTrue(next.pos().equals(lair.pos()) && !next.captain().equals(lair.captain()),
					"setup: expected a new camp at " + lair.pos() + ", got one at " + next.pos());
				Optional<Scouts.Known> after = Scouts.known(level, hall);
				helper.assertTrue(after.isEmpty(), "the village knows the new camp from the old camp's report, though no scout has seen it: " + after);
				helper.succeed();
			});
		});
	}

	/**
	 * {@code /workplace threat clear} by a bandit camp: the camp leaves in peace. Its chief and band go at once, and its
	 * tents, fire, beds and chests come down as a broken camp's do; the village is given no rest, for nobody broke it.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLairBreakUp1110dPacksUp"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLairBreakUp1110dPacksUp")
	public void aCampClearedByTheThreatCommandPacksUpItsTents(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "setup: no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			BlockPos middle = lair.pos();
			int[] before = count(level, middle);
			helper.assertTrue(before[0] == 1 && before[1] == 2 && before[2] > 20 && before[3] == 10, "setup: the camp as set down: " + str(before));
			int cleared = ThreatCommand.clear(level, hall);
			helper.assertTrue(cleared >= 1, "the threat command cleared nothing: " + cleared);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp is still recorded after it left");
			helper.assertTrue(!(level.getEntity(lair.captain()) instanceof Mob m && m.isAlive()), "the chief is still there");
			helper.assertTrue(Lairs.band(level, lair).isEmpty(), "the band is still there: " + Lairs.band(level, lair).size());
			helper.assertTrue(Lairs.resting(level, hall) == 0, "a camp that left in peace gave the village a rest of " + Lairs.resting(level, hall) + " days");
			helper.runAfterDelay(GONE, () -> {
				int[] after = count(level, middle);
				helper.assertTrue(after[0] == 0 && after[1] == 0 && after[2] == 0 && after[3] == 0,
					"the camp that left in peace still stands " + (GONE / 20) + " seconds on: " + str(after));
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the packing up never ended");
				helper.succeed();
			});
		});
	}

	/**
	 * A coven hut by the village and a hexed villager in it. {@code /workplace threat clear}: the coven leaves in peace,
	 * and with no coven left by the village its hex lifts at once (a hex doesn't pass by itself, covens.md).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLairBreakUp1110dHex"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLairBreakUp1110dHex")
	public void aCovenClearedByTheThreatCommandLiftsItsHex(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Threats.get(COVEN).isPresent() && Covens.hexes(COVEN), "setup: witch_coven.json isn't loaded or doesn't hex");
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 20)), COVEN);
			helper.assertTrue(lair != null && lair.culture().equals(COVEN), "setup: expected a coven hut, got " + lair);
			Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
			villager.setNoAi(true);
			villager.setInvulnerable(true);
			Sickness.hex(level, villager);
			helper.assertTrue(Sickness.isHexed(villager) && Covens.cursed(level, villager), "setup: the villager isn't hexed by the coven");
			ThreatCommand.clear(level, hall);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the coven is still recorded after it left");
			helper.assertFalse(Sickness.isHexed(villager), "the coven left the village in peace but its villager is still hexed");
			helper.succeed();
		});
	}
}
