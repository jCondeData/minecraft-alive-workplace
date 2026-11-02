package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1102-1008) for the drowned pirates (ROADMAP 32.8), written from the spec, the wiki ({@code pirates.md})
 * and the guide ({@code drowned-pirates.md}) for what PirateGameTests and QaPiratesThreatLevelGameTests leave out:
 * the raid the village's own clock sets (not a test's {@code start}) comes
 * only once night has fallen; a ship at anchor sends no raid once {@code drowned_pirates} is switched off, and does
 * again once it's back on; the chest a player has already opened rides the raft with what's in it; a hull block broken
 * mid-attack is sea again once the ship has sunk, and a deck block broken stays gone; the chest's loot; the ship's
 * strength (8, +1 a day, at most 14, at most 8 at the ship); the lines a player reads.
 * The sea is QaPiratesThreatLevelGameTests' (and PirateGameTests'): a beach along the north, a pool 6 deep to the south.
 */
public class QaPirates1102dGameTests {
	static final String TALL = "aliveworkplace_test:tall_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	static final BlockPos SHIP = new BlockPos(15, 7, 14);
	static final BlockPos BEACH = new BlockPos(15, 8, 3);
	/** Where the raft goes in this sea (PirateGameTests: off the ship's north side, the bow's water being taken). */
	static final BlockPos RAFT = new BlockPos(15, 8, 7);
	static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");

	static double horizontal(BlockPos a, double x, double z) {
		double dx = a.getX() + 0.5 - x;
		double dz = a.getZ() + 0.5 - z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	static Lairs.Lair anchor(GameTestHelper helper, BlockPos hall, long day) {
		ServerLevel level = helper.getLevel();
		QaPiratesThreatLevelGameTests.rank(level, hall, VillageRanks.Rank.TOWN);
		QaPiratesThreatLevelGameTests.at(level, day, 13000);
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
		helper.assertTrue(lair != null && lair.culture().equals(PIRATES) && Ships.of(level, hall).isPresent(), "no pirate ship: " + lair);
		return lair;
	}

	/** Raids on (the test server runs with them off), back as they were when the test ends. */
	static void raidsOn(GameTestHelper helper) {
		boolean raids = VillageRaids.ENABLED;
		VillageRaids.ENABLED = true;
		Leftovers.after(helper, () -> VillageRaids.ENABLED = raids);
	}

	/** The side the clock says a lair's raid comes from: towards the lair, in the middle of its eighth (VillageRaids.roll). */
	static double side(BlockPos hall, Lairs.Lair lair) {
		double eighth = Math.PI / 4;
		double angle = Math.atan2(lair.pos().getZ() - hall.getZ(), lair.pos().getX() - hall.getX());
		return Math.round(angle / eighth) * eighth;
	}

	/** Tonight's attack by the pirates, as the clock sets it at the dusk before, at {@code at}. */
	static void setTonight(ServerLevel level, BlockPos hall, Lairs.Lair lair, long day, long at) {
		ThreatData.get(level).rolled(hall, day - 1, new ThreatData.Attack(day, PIRATES, side(hall, lair), at));
	}

	static String lastChronicle(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().isEmpty() ? "" : entity.chronicle().get(entity.chronicle().size() - 1).text().getString();
	}

	/**
	 * The raid the village's own clock set for tonight (the way every real pirate raid comes, not a test's start): at
	 * 13,499 (still day) nobody comes; at 13,500 the crew of 8 comes. (Where they come ashore on this path is in
	 * QaPirates1102dBugTests.) The ship's chronicle line names Captain Silas Brine once.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaPirates1102dClock"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPirates1102dClock")
	public void qaTheClocksPirateRaidComesAtNightfallAndNotBefore(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		raidsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchor(helper, hall, 70);
			String camp = lastChronicle(level, hall);
			helper.assertTrue(camp.startsWith("Captain Silas Brine dropped anchor ") && camp.endsWith(" with his drowned crew") && camp.contains(" blocks ")
				&& !camp.contains("%") && !camp.contains("Captain Captain"), "the chronicle's anchor line: " + camp);
			BlockPos beach = Ships.beach(level, hall, lair.pos());
			helper.assertTrue(helper.absolutePos(BEACH).equals(beach), "the beach nearest the ship: " + beach);
			setTonight(level, hall, lair, 70, 13500);
			QaPiratesThreatLevelGameTests.at(level, 70, 13499);
			VillageRaids.tick(level, hall, 52, 0, 0, d -> { });
			helper.assertTrue(VillageRaids.raiders(level, hall).isEmpty() && ThreatData.get(level).raid(hall).isEmpty(), "the pirates came by day (13,499)");
			QaPiratesThreatLevelGameTests.at(level, 70, 13500);
			List<Long> raided = new ArrayList<>();
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raided.equals(List.of(70L)) && raiders.size() == 8, "tonight's raid at nightfall: " + raided + ", " + raiders.size() + " raiders");
			helper.succeed();
		});
	}

	/**
	 * {@code drowned_pirates} off in the config: "one at anchor sends no raids". The ship is already at anchor and the
	 * clock set tonight's raid; with the switch off night falls and nobody comes. Back on, the next raid comes.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaPirates1102dSwitch"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPirates1102dSwitch")
	public void qaAShipAtAnchorSendsNoRaidOnceSwitchedOff(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		raidsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchor(helper, hall, 74);
			Threats.DISABLED = Set.of("drowned_pirates");
			setTonight(level, hall, lair, 74, 14000);
			QaPiratesThreatLevelGameTests.at(level, 74, 15000);
			VillageRaids.tick(level, hall, 52, 0, 0, d -> { });
			helper.assertTrue(VillageRaids.raiders(level, hall).isEmpty() && ThreatData.get(level).raid(hall).isEmpty(),
				"a ship at anchor sent a raid with drowned_pirates off: " + VillageRaids.raiders(level, hall).size());
			helper.assertTrue(VillageRaids.start(level, hall, 52, 0, Double.NaN) == null || VillageRaids.raiders(level, hall).stream()
				.noneMatch(m -> m.getTags().contains(Lairs.TAG)), "an ordered raid brought the switched-off ship's crew");
			VillageRaids.forget();
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			ThreatData.get(level).end(hall);
			// Back on: the next night's raid comes.
			Threats.DISABLED = Set.of();
			Lairs.Lair still = Lairs.near(level, hall).orElse(null);
			helper.assertTrue(still != null && still.culture().equals(PIRATES), "the ship left while switched off: " + still);
			setTonight(level, hall, still, 78, 14000);
			QaPiratesThreatLevelGameTests.at(level, 78, 15000);
			VillageRaids.tick(level, hall, 52, 0, 0, d -> { });
			helper.assertTrue(!VillageRaids.raiders(level, hall).isEmpty(), "switched back on, the ship sent no raid");
			helper.succeed();
		});
	}

	/**
	 * A player opened the captain's chest (its loot rolled) and took some: when the captain falls the raft's chest holds
	 * what was left in it, the ship's chest is emptied (nothing doubles), and the raft is 3 x 3 spruce logs on the water.
	 */
	//$ gametest_ticks_batch TALL '60' '"qaPirates1102dOpened"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaPirates1102dOpened")
	public void qaAnOpenedChestRidesTheRaftWithWhatIsLeftInIt(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchor(helper, hall, 82);
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			BlockPos at = null;
			for (BlockPos p : Ships.blocks(level, ship).keySet()) {
				if (level.getBlockEntity(p) instanceof ChestBlockEntity) {
					at = p;
					break;
				}
			}
			helper.assertTrue(at != null, "no chest on the ship");
			ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(at);
			chest.unpackLootTable(null); // a player opened it
			for (int i = 0; i < chest.getContainerSize(); i++) {
				chest.setItem(i, ItemStack.EMPTY); // and took everything but what's put back below
			}
			chest.setItem(4, new ItemStack(Items.DIAMOND, 3));
			chest.setItem(20, new ItemStack(Items.HEART_OF_THE_SEA));
			BlockPos from = at;
			((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			BlockPos raft = helper.absolutePos(RAFT);
			helper.assertTrue(level.getBlockEntity(raft) instanceof ChestBlockEntity, "no chest on the raft at " + raft + ": " + level.getBlockState(raft));
			ChestBlockEntity floating = (ChestBlockEntity) level.getBlockEntity(raft);
			int diamonds = 0;
			int hearts = 0;
			for (int i = 0; i < floating.getContainerSize(); i++) {
				ItemStack s = floating.getItem(i);
				diamonds += s.is(Items.DIAMOND) ? s.getCount() : 0;
				hearts += s.is(Items.HEART_OF_THE_SEA) ? s.getCount() : 0;
			}
			helper.assertTrue(diamonds == 3 && hearts == 1 && floating.getLootTable() == null, "the raft's chest: " + diamonds + " diamonds, " + hearts + " hearts, table "
				+ floating.getLootTable());
			helper.assertFalse(level.getBlockEntity(from) instanceof ChestBlockEntity c && !c.isEmpty(), "the ship's chest still holds the loot (doubled)");
			List<net.minecraft.world.entity.item.ItemEntity> spilled = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(from).inflate(3), e -> e.getItem().is(Items.DIAMOND) || e.getItem().is(Items.HEART_OF_THE_SEA));
			helper.assertTrue(spilled.isEmpty(), "the ship's chest spilled what went onto the raft (doubled): " + spilled);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos log = raft.below().offset(dx, 0, dz);
					helper.assertTrue(level.getBlockState(log).is(Blocks.SPRUCE_LOG), "the raft isn't 3 x 3 spruce logs at " + log + ": " + level.getBlockState(log));
					helper.assertTrue(level.getFluidState(log.below()).is(FluidTags.WATER), "the raft isn't on the water at " + log.below());
				}
			}
			helper.succeed();
		});
	}

	/**
	 * The ship's hull broken mid-attack (a player cut a hole below the waterline and broke a rail on deck): when the captain
	 * falls and the ship has sunk, the sea is back where the broken hull block was (no air pocket in the sea), and the deck
	 * block that was broken is still gone (the sinking puts nothing back). It goes top down (5 ticks in, the second layer
	 * from the top still stands), leaves no water above the sea, and what its gunports and barrels held doesn't spill.
	 */
	//$ gametest_ticks_batch TALL '320' '"qaPirates1102dHole"'
	@GameTest(template = TALL, timeoutTicks = 320, batch = "qaPirates1102dHole")
	public void qaAHullBrokenMidAttackIsSeaOnceTheShipHasSunk(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchor(helper, hall, 84);
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			Map<BlockPos, BlockState> blocks = Ships.blocks(level, ship);
			// The hull's outer plank just under the waterline on the village side (lowest z), and a block on deck.
			BlockPos hole = blocks.entrySet().stream().filter(e -> !e.getValue().isAir() && e.getKey().getY() == ship.waterTop() - 1
				&& MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue()) && level.getBlockEntity(e.getKey()) == null)
				.map(Map.Entry::getKey).min((a, b) -> Integer.compare(a.getZ(), b.getZ())).orElse(null);
			BlockPos rail = blocks.entrySet().stream().filter(e -> !e.getValue().isAir() && e.getKey().getY() == ship.waterTop() + 2
				&& MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue()) && level.getBlockEntity(e.getKey()) == null)
				.map(Map.Entry::getKey).findFirst().orElse(null);
			helper.assertTrue(hole != null && rail != null, "no hull plank under the water or block on deck: " + hole + " " + rail);
			level.destroyBlock(hole, false);
			level.destroyBlock(rail, false);
			// The ship's other containers (gunports, barrels) hold something: it goes down with her, nothing spills.
			int stocked = 0;
			for (BlockPos p : blocks.keySet()) {
				if (level.getBlockEntity(p) instanceof net.minecraft.world.Container box && !(level.getBlockEntity(p) instanceof ChestBlockEntity)) {
					box.setItem(0, new ItemStack(Items.ECHO_SHARD, 5));
					stocked++;
				}
			}
			int top = blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
			((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Ships.sinking(level).size() == 1, "the ship isn't sinking");
			// Top down over ten seconds: a few ticks in only the topmost layer has gone, the one under it still stands.
			helper.runAfterDelay(5, () -> {
				long standing = blocks.entrySet().stream().filter(e -> e.getKey().getY() == top - 1 && !e.getValue().isAir()
					&& MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue())).count();
				long there = blocks.entrySet().stream().filter(e -> e.getKey().getY() == top - 1 && !e.getValue().isAir()).count();
				helper.assertTrue(there > 0 && standing == there, "5 ticks into the sinking, " + standing + " of " + there + " blocks of the second layer stand");
			});
			int boxes = stocked;
			helper.runAfterDelay(Ships.SINK_TICKS + 10, () -> {
				helper.assertTrue(boxes > 0, "the ship has no gunports or barrels");
				List<net.minecraft.world.entity.item.ItemEntity> spilled = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					helper.getBounds().inflate(8), e -> e.getItem().is(Items.ECHO_SHARD));
				helper.assertTrue(spilled.isEmpty(), "the sinking ship's gunports and barrels spilled: " + spilled.size());
				int raised = 0;
				for (BlockPos p : blocks.keySet()) {
					if (p.getY() > ship.waterTop() && !level.getFluidState(p).isEmpty()) {
						raised++;
					}
				}
				helper.assertTrue(raised == 0, raised + " blocks of water left above the sea where the ship stood");
				helper.assertTrue(Ships.sinking(level).isEmpty(), "the ship is still sinking");
				helper.assertTrue(level.getFluidState(hole).is(FluidTags.WATER), "no sea where the hull was broken mid-attack at " + hole + ": " + level.getBlockState(hole));
				helper.assertTrue(level.getBlockState(rail).isAir(), "the deck block broken mid-attack came back at " + rail + ": " + level.getBlockState(rail));
				int pockets = 0;
				for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
					if (e.getKey().getY() <= ship.waterTop() && !level.getFluidState(e.getKey()).is(FluidTags.WATER)) {
						pockets++;
					}
				}
				helper.assertTrue(pockets == 0, pockets + " blocks of the sunk hull aren't sea");
				helper.succeed();
			});
		});
	}

	/**
	 * The chest's loot (chests/pirate_ship): every chest holds gold, emeralds, a trident and nautilus shells; now and then
	 * a diamond; one time in ten a heart of the sea (200 seeded rolls: between 8 and 36).
	 */
	//$ gametest_ticks_batch BIG '40' '"qaPirates1102dLoot"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaPirates1102dLoot")
	public void qaThePirateChestHoldsTheSpecsTreasure(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id("chests/pirate_ship")));
		helper.assertTrue(table != LootTable.EMPTY, "no chests/pirate_ship loot table");
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))))
			.create(LootContextParamSets.CHEST);
		int hearts = 0;
		int diamonds = 0;
		for (int i = 0; i < 200; i++) {
			List<ItemStack> items = table.getRandomItems(params, 11020L + i);
			boolean gold = items.stream().anyMatch(s -> s.is(Items.GOLD_INGOT));
			boolean emerald = items.stream().anyMatch(s -> s.is(Items.EMERALD));
			boolean trident = items.stream().anyMatch(s -> s.is(Items.TRIDENT));
			boolean shell = items.stream().anyMatch(s -> s.is(Items.NAUTILUS_SHELL));
			helper.assertTrue(gold && emerald && trident && shell, "roll " + i + " lacks gold, emeralds, a trident or shells: " + items);
			hearts += items.stream().anyMatch(s -> s.is(Items.HEART_OF_THE_SEA)) ? 1 : 0;
			diamonds += items.stream().anyMatch(s -> s.is(Items.DIAMOND)) ? 1 : 0;
		}
		helper.assertTrue(hearts >= 8 && hearts <= 36, "a heart of the sea in " + hearts + " of 200 chests, not about one in ten");
		helper.assertTrue(diamonds > 0 && diamonds < 200, "a diamond in " + diamonds + " of 200 chests, not now and then");
		helper.succeed();
	}

	/**
	 * The ship's strength: 8 when it anchors, 1 more each dawn, never past 14; however strong it grows, at most 8 of the
	 * crew stand at the ship (with the captain, 9 aboard).
	 */
	//$ gametest_ticks_batch TALL '100' '"qaPirates1102dGrowth"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPirates1102dGrowth")
	public void qaTheShipGrowsByOneADayToFourteenWithEightAboard(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchor(helper, hall, 60);
			helper.assertTrue(lair.strength() == 8, "a new ship's strength: " + lair.strength());
			for (long day = 61; day <= 69; day++) {
				QaPiratesThreatLevelGameTests.at(level, day, 1000);
				Lairs.round(level, hall, true, RandomSource.create(day));
				Lairs.Lair now = Lairs.near(level, hall).orElse(null);
				helper.assertTrue(now != null, "the ship left on day " + day);
				int want = (int) Math.min(14, 8 + (day - 60));
				helper.assertTrue(now.strength() == want, "the ship's strength on day " + day + ": " + now.strength() + ", not " + want);
				int aboard = Lairs.band(level, now).size();
				helper.assertTrue(aboard <= 9, "on day " + day + " " + aboard + " stand at the ship (8 and the captain at most)");
			}
			helper.succeed();
		});
	}

	/**
	 * The lines a player reads when the ship sends one pirate (its band nearly all out): a raid of exactly one comes, the
	 * chat line for 8 reads right and the chronicle for one has no "1 drowned pirates" and no placeholder. (The chat line
	 * for one is in QaPirates1102dBugTests.)
	 */
	//$ gametest_ticks_batch TALL '100' '"qaPirates1102dOne"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPirates1102dOne")
	public void qaARaidOfOnePirateReadsRight(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchor(helper, hall, 86);
			Lairs.sent(level, hall, 7); // 7 out already: one at home to send
			QaPiratesThreatLevelGameTests.at(level, 86, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, QaPiratesThreatLevelGameTests.pirates(helper), RandomSource.create(5L));
			helper.assertTrue(raid != null && VillageRaids.raiders(level, hall).size() == 1, "a raid of one: " + VillageRaids.raiders(level, hall).size());
			Lairs.Lair now = Lairs.near(level, hall).orElseThrow();
			String eight = Lairs.message(now, "raid", true, 8, io.github.jcondedata.aliveworkplace.hall.VillageHalls.name(level, hall), Lairs.captainName(now)).getString();
			helper.assertTrue(eight.contains("drowned pirates wade ashore"), "the line for 8: " + eight);
			String chronicle = lastChronicle(level, hall);
			helper.assertFalse(chronicle.contains("1 drowned pirates") || chronicle.contains("%"), "the chronicle for one pirate: " + chronicle);
			helper.succeed();
		});
	}
}
