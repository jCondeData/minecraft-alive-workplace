package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * B209 (the owner's live server): a bandit camp stood for good after its chief was killed and its band beaten, and could
 * still be found. Now the camp is gone from the village's records at once and its blocks come down a few seconds later
 * (only its own, still unchanged; its chests' loot spills out; its ground goes back to what it was, B225), or with {@code campRuins} it stays as
 * ruins with its fire out. A reload mid-clearing carries on, a camp broken up before the fix (its record gone, its tents
 * standing) is found and cleared by the hall's round in a world saved before it, and a new camp can be made after.
 */
public class B209GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);
	/** Ticks till a broken camp is all gone, with a little to spare. */
	private static final int GONE = CampCleanup.DELAY + CampCleanup.CLEAR_TICKS + 10;

	/** A hall, its camp's ground clear; all of it undone at the end. */
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

	private static Lairs.Lair camp(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp was made");
		return Lairs.near(level, hall).orElseThrow();
	}

	/** The camp's blocks round its middle: campfires, chests, wool (tents) and beds. */
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

	private static void killChief(ServerLevel level, Lairs.Lair lair) {
		Entity chief = level.getEntity(lair.captain());
		if (chief instanceof LivingEntity living) {
			living.kill();
		}
	}

	/** A wool block of the camp, which a player swaps for gold (it must stay). */
	private static BlockPos someWool(ServerLevel level, BlockPos middle) {
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 0, -9), middle.offset(9, 5, 9))) {
			if (level.getBlockState(p).is(BlockTags.WOOL)) {
				return p.immutable();
			}
		}
		return null;
	}

	/** The chief killed: the record and the scouts' report are gone at once; the camp's own blocks come down; the player's stays. */
	//$ gametest_ticks_batch AREA '400' '"b209Down"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b209Down")
	public void b209ABrokenCampComesDownAndIsForgotten(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			BlockState groundState = level.getBlockState(helper.absolutePos(CAMP)); // (the land before the camp, B225)
			Lairs.Lair lair = camp(helper, hall);
			BlockPos middle = lair.pos();
			int[] before = count(level, middle);
			helper.assertTrue(before[0] == 1 && before[1] == 2 && before[2] > 20 && before[3] == 10, "the camp as set down: " + str(before));
			BlockPos gold = someWool(level, middle);
			level.setBlockAndUpdate(gold, Blocks.GOLD_BLOCK.defaultBlockState());
			BlockPos ground = middle.below();
			helper.assertTrue(ground.equals(helper.absolutePos(CAMP)), "the camp's middle isn't over its ground: " + middle);
			killChief(level, lair);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && BanditCamps.all(level).isEmpty(), "the camp is still recorded after its chief fell");
			helper.assertTrue(Scouts.known(level, hall).isEmpty(), "the scouts still know a camp");
			helper.assertTrue(Lairs.band(level, lair).isEmpty(), "the band is still there");
			helper.assertTrue(CampCleanup.clearing(level).size() == 1, "the camp isn't coming down: " + CampCleanup.clearing(level));
			helper.assertTrue(count(level, middle)[0] == 1, "the camp went before its band's poof");
			helper.runAfterDelay(GONE, () -> {
				int[] after = count(level, middle);
				helper.assertTrue(after[0] == 0 && after[1] == 0 && after[2] == 0 && after[3] == 0, "the camp still stands: " + str(after));
				helper.assertTrue(level.getBlockState(gold).is(Blocks.GOLD_BLOCK), "the player's block was taken");
				helper.assertTrue(level.getBlockState(ground).equals(groundState), "the camp's ground isn't back to the land's: " + level.getBlockState(ground));
				long loot = level.getEntitiesOfClass(ItemEntity.class, new AABB(middle).inflate(10)).stream().mapToInt(i -> i.getItem().getCount()).sum();
				helper.assertTrue(loot > 0, "the chests' loot didn't spill out");
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				// A later band makes a new camp: nothing of the old one stands in the way.
				helper.assertTrue(Lairs.resting(level, hall) == Lairs.REST_DAYS, "the rest: " + Lairs.resting(level, hall));
				Lairs.Lair next = camp(helper, hall);
				helper.assertTrue(!next.captain().equals(lair.captain()) && level.getEntity(next.captain()) instanceof Mob m && m.isAlive(), "the new camp has no chief");
				Lairs.round(level, hall, false);
				helper.assertTrue(Lairs.near(level, hall).isPresent() && count(level, next.pos())[0] == 1, "the new camp didn't stand");
				helper.succeed();
			});
		});
	}

	/** With campRuins on, the broken camp is forgotten but stays, its fire out. */
	//$ gametest_ticks_batch AREA '300' '"b209Ruins"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "b209Ruins")
	public void b209WithCampRuinsOnABrokenCampStaysAsRuins(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		CampCleanup.RUINS = true;
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = camp(helper, hall);
			BlockPos middle = lair.pos();
			int[] before = count(level, middle);
			killChief(level, lair);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp is still recorded");
			helper.runAfterDelay(GONE, () -> {
				int[] after = count(level, middle);
				helper.assertTrue(after[0] == 1 && after[1] == before[1] && after[2] == before[2] && after[3] == before[3], "the ruins: " + str(after) + ", was " + str(before));
				helper.assertTrue(!level.getBlockState(middle).getValue(CampfireBlock.LIT), "the fire still burns");
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the camp is still listed");
				helper.succeed();
			});
		});
	}

	/** A save and reload before the camp starts coming down and another halfway through: it carries on and ends. */
	//$ gametest_ticks_batch AREA '400' '"b209Reload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b209Reload")
	public void b209AReloadMidClearingCarriesOn(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = camp(helper, hall);
			BlockPos middle = lair.pos();
			int[] before = count(level, middle);
			killChief(level, lair);
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && CampCleanup.clearing(level).size() == 1, "after a reload: " + CampCleanup.clearing(level));
			helper.runAfterDelay(CampCleanup.DELAY + CampCleanup.CLEAR_TICKS / 2, () -> {
				int[] half = count(level, middle);
				helper.assertTrue(half[0] == 1 && half[2] > 0 && half[2] < before[2] && half[3] == 10,
					"halfway down (the tents' tops gone, the fire and beds not yet): " + str(half) + ", was " + str(before));
				Lairs.Data.get(level).roundTrip(level.registryAccess());
				helper.assertTrue(CampCleanup.clearing(level).size() == 1 && CampCleanup.clearing(level).get(0).rotation() >= 0,
					"after a reload halfway: " + CampCleanup.clearing(level));
				helper.runAfterDelay(CampCleanup.CLEAR_TICKS / 2 + 10, () -> {
					int[] after = count(level, middle);
					helper.assertTrue(after[0] == 0 && after[1] == 0 && after[2] == 0 && after[3] == 0, "the camp still stands: " + str(after));
					helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * A world saved before B209 with a camp broken up then (no record, its tents standing): the hall's round finds it and
	 * takes it down, but not while a living bandit of ours is at it.
	 */
	//$ gametest_ticks_batch AREA '400' '"b209Old"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b209Old")
	public void b209ACampBrokenUpBeforeTheFixIsClearedAfterLoad(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		CampCleanup.SWEEP_FROM = 0;
		CampCleanup.SWEEP_TO = 26;
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = camp(helper, hall);
			BlockPos middle = lair.pos();
			// Open sky over the camp, as out in the world (the test area has a barrier roof, which the sweep would take for the ground).
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 1, -9), middle.offset(9, 40, 9))) {
				if (level.getBlockState(p).is(Blocks.BARRIER)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			// As the old code left it: the record dropped, a rest for the village, no camp coming down; saved without b209.
			Lairs.Data data = Lairs.Data.get(level);
			CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
			tag.put("camps", new ListTag());
			tag.remove("b209");
			tag.remove("unswept");
			ListTag rests = new ListTag();
			CompoundTag rest = new CompoundTag();
			rest.putLong("hall", hall.asLong());
			rest.putLong("day", Chronicle.day(level));
			rest.putInt("rest", Lairs.REST_DAYS);
			rests.add(rest);
			tag.put("broken_up", rests);
			data.read(tag);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && CampCleanup.clearing(level).isEmpty(), "the old save's camp: " + CampCleanup.clearing(level));
			// Its chief still alive there: it's someone's still, and stays.
			Lairs.round(level, hall, false);
			helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "a camp with its chief alive was cleared");
			Lairs.band(level, lair).forEach(Mob::discard);
			helper.runAfterDelay(2, () -> {
				Lairs.round(level, hall, false);
				helper.assertTrue(CampCleanup.clearing(level).size() == 1, "the old camp wasn't found: " + CampCleanup.clearing(level));
				Lairs.round(level, hall, false);
				helper.assertTrue(CampCleanup.clearing(level).size() == 1, "the old camp was found twice (the village wasn't marked swept)");
				helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
					int[] after = count(level, middle);
					helper.assertTrue(after[0] == 0 && after[1] == 0 && after[2] == 0 && after[3] == 0, "the old camp still stands: " + str(after));
					// Swept once: a new camp by the village isn't touched by a later round.
					Lairs.Lair next = camp(helper, hall);
					Lairs.band(level, next).forEach(Mob::discard);
					Lairs.forget(level); // (no record, as an old camp; but the village is swept now)
					Lairs.round(level, hall, false);
					helper.assertTrue(CampCleanup.clearing(level).isEmpty() && count(level, next.pos())[0] == 1, "a swept village was swept again");
					helper.succeed();
				});
			});
		});
	}

	/** The bandit camp blueprint's spot for its fire (Lairs.FIRE): the camp's middle. */
	private static final BlockPos FIRE_SPOT = new BlockPos(7, 1, 6);

	/**
	 * A camp left standing from before B209, as the old code set it down: unturned, no record, no band, no ground record,
	 * its village still to be swept. The test area's barrier roof over it is opened (the sweep reads the sky's height).
	 * Returns the blueprint's blocks above its ground layer, in the world.
	 */
	private static List<BlockPos> oldCamp(GameTestHelper helper, BlockPos hall, BlockPos middle) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(Lairs.BANDIT_CAMP).orElseThrow();
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);
		BlockPos origin = middle.subtract(FIRE_SPOT);
		template.placeInWorld(level, origin, origin, settings, RandomSource.create(41), 2);
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 1, -9), middle.offset(9, 40, 9))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		Lairs.Data data = Lairs.Data.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		tag.putBoolean("b209", true);
		ListTag unswept = new ListTag();
		CompoundTag at = new CompoundTag();
		at.putLong("hall", hall.asLong());
		unswept.add(at);
		tag.put("unswept", unswept);
		data.read(tag);
		List<BlockPos> standing = new ArrayList<>();
		for (Blueprint.Entry e : Blueprint.fromTemplate(Lairs.BANDIT_CAMP, template, Lookup.lookup(BuiltInRegistries.BLOCK)).blocks()) {
			if (e.pos().getY() >= 1 && !e.state().isAir()) {
				standing.add(origin.offset(e.pos()));
			}
		}
		return standing;
	}

	/** An old camp set down unturned (its turn 0) is found by the sweep with that turn and comes all the way down, its beds and fire too. */
	//$ gametest_ticks_batch AREA '300' '"b209OldUnturned"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "b209OldUnturned")
	public void b209AnOldUnturnedCampComesAllTheWayDown(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		CampCleanup.SWEEP_FROM = 0;
		CampCleanup.SWEEP_TO = 26;
		BlockPos middle = helper.absolutePos(CAMP.above());
		helper.runAfterDelay(2, () -> {
			oldCamp(helper, hall, middle);
			int[] before = count(level, middle);
			helper.assertTrue(before[0] == 1 && before[3] == 10, "the old camp as set down: " + str(before));
			CampCleanup.sweep(level, hall);
			helper.assertTrue(CampCleanup.clearing(level).size() == 1 && CampCleanup.clearing(level).get(0).rotation() == 0,
				"the old camp wasn't found unturned: " + CampCleanup.clearing(level));
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				int[] after = count(level, middle);
				helper.assertTrue(after[0] == 0 && after[1] == 0 && after[2] == 0 && after[3] == 0, "the old camp still stands: " + str(after));
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				helper.succeed();
			});
		});
	}

	/** A bounty's hideout far from an old camp doesn't keep the camp standing: only one at the camp marks it as in use. */
	//$ gametest_ticks_batch AREA '200' '"b209OldBounty"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b209OldBounty")
	public void b209ABountyFarOffDoesntStopTheSweep(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		CampCleanup.SWEEP_FROM = 0;
		CampCleanup.SWEEP_TO = 26;
		BlockPos middle = helper.absolutePos(CAMP.above());
		helper.runAfterDelay(2, () -> {
			oldCamp(helper, hall, middle);
			var bounties = Bounties.Data.get(level).bounties;
			UUID near = UUID.randomUUID();
			UUID far = UUID.randomUUID();
			try {
				// its own: a bounty's hideout at the camp keeps it
				bounties.put(near, new Bounties.Bounty(near, hall, 0, 0, ResourceLocation.withDefaultNamespace("test"),
					new ArcState.ArcPlace("hideout", middle.offset(3, 0, 3), List.of(), 0), 0, 1, false));
				CampCleanup.sweep(level, hall);
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "a bounty's hideout was swept away: " + CampCleanup.clearing(level));
				bounties.remove(near);
				// another's, 800 blocks off, doesn't
				bounties.put(far, new Bounties.Bounty(far, hall, 0, 0, ResourceLocation.withDefaultNamespace("test"),
					new ArcState.ArcPlace("hideout", middle.offset(600, 0, 600), List.of(), 0), 0, 1, false));
				oldCamp(helper, hall, middle); // (its village to be swept again)
				CampCleanup.sweep(level, hall);
				helper.assertTrue(CampCleanup.clearing(level).size() == 1, "a bounty 800 blocks off kept the old camp standing: " + CampCleanup.clearing(level));
			} finally {
				bounties.remove(near);
				bounties.remove(far);
			}
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				helper.succeed();
			});
		});
	}

	/** An old camp with exactly half its blocks still as set down is still known as the camp (the sweep's least share, met). */
	//$ gametest_ticks_batch AREA '200' '"b209OldHalf"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b209OldHalf")
	public void b209AnOldCampWithExactlyHalfStandingIsFound(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		CampCleanup.SWEEP_FROM = 0;
		CampCleanup.SWEEP_TO = 26;
		BlockPos middle = helper.absolutePos(CAMP.above());
		helper.runAfterDelay(2, () -> {
			List<BlockPos> standing = new ArrayList<>(oldCamp(helper, hall, middle));
			int keep = (int) Math.ceil(standing.size() * 0.5);
			// the lowest first (the fire among them), the rest taken away from the top, nothing round them updated
			standing.sort(Comparator.comparingInt(BlockPos::getY));
			helper.assertTrue(standing.remove(middle), "the fire isn't the camp's middle");
			standing.add(0, middle);
			for (BlockPos p : standing.subList(keep, standing.size())) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			CampCleanup.sweep(level, hall);
			helper.assertTrue(CampCleanup.clearing(level).size() == 1, keep + " of " + standing.size() + " blocks (half) standing: not found as a camp");
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty() && count(level, middle)[0] == 0, "the half camp wasn't taken down");
				helper.succeed();
			});
		});
	}
}
