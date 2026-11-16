package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Conditions;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-d-1116-0408, the first mutation pass on threat/Ships.java): what a player sees of the pirate ship (ROADMAP
 * 32.8) that the earlier tests let a planted bug through on, written from the spec. On {@link PirateGameTests}'s coast
 * (as {@link QaShips1103dGameTests} lays it out): a beach strip along the north with the hall, a pool six deep to the
 * south, the ship from x 2 to 28, z 10 to 18, its bow west, its raft off its north side at (15, 7, 7).
 */
public class QaShipsMutants1116dGameTests {
	private static final String AREA = "aliveworkplace_test:tall_area";
	private static final BlockPos HALL = new BlockPos(1, 8, 1);
	private static final BlockPos SHIP = new BlockPos(15, 7, 14);
	private static final BlockPos NORTH_RAFT = new BlockPos(15, 7, 7);
	private static final long DAY = 24000;
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** {@link PirateGameTests}'s coast: the beach and the sea, a Town hall in a beach biome, radius 16, four villagers. */
	static BlockPos coast(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		boolean open = Expansions.openForTests;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		VillageHalls.RADIUS = 16;
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y <= 7; y++) {
					BlockState block = z <= 3 ? (y == 7 ? Blocks.SAND : Blocks.STONE).defaultBlockState()
						: x == 0 || x == 29 || z == 29 ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), block, 2);
				}
				for (int y = 8; y <= 9; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hall, "minecraft:beach");
		Conditions.forgetCoasts();
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))
				.forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Threats.DISABLED = off;
			Expansions.openForTests = open;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			fill(level, hall, old);
			Conditions.forgetCoasts();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(3 + i, 8, 1));
		}
		return hall;
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	/** A Town by the sea at night, with the pirate ship anchored off it. */
	static Lairs.Lair anchored(GameTestHelper helper, BlockPos hall, long day) {
		ServerLevel level = helper.getLevel();
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		at(level, day, 13000);
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
		helper.assertTrue(lair != null && lair.culture().equals(PIRATES) && Ships.of(level, hall).isPresent(), "no pirate ship anchored: " + lair);
		return lair;
	}

	static void captainFalls(ServerLevel level, Lairs.Lair lair) {
		((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
	}

	/**
	 * Captain Silas Brine falls: the ship's loot is on the raft, in its chest and nowhere else (no loot floats in the sea
	 * from the chest the ship carried), and his ship's sinking is marked to be saved at once, so a restart right after
	 * doesn't bring the ship back to anchor: after a save and load it is still sinking, and then it's gone.
	 */
	//$ gametest_ticks_batch AREA '320' '"qa1116dShipLootSaved"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "qa1116dShipLootSaved")
	public void theLootIsOnlyOnTheRaftAndTheSinkingIsSaved(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchored(helper, hall, 160);
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			Ships.Data data = Ships.Data.get(level);
			data.setDirty(false); // as just after an autosave
			GameRules.BooleanValue mobLoot = level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
			boolean wasMobLoot = mobLoot.get();
			mobLoot.set(false, level.getServer()); // the captain's own drops (rotten flesh, his trident) aren't the ship's loot
			captainFalls(level, lair);
			mobLoot.set(wasMobLoot, level.getServer());
			helper.assertTrue(Ships.of(level, hall).isEmpty() && Ships.sinking(level).stream().anyMatch(s -> s.ship().equals(ship)), "the ship isn't sinking");
			helper.assertTrue(data.isDirty(), "the captain fell but the ships' data isn't marked to be saved: after a restart the ship is back at anchor");
			data.roundTrip(level.registryAccess());
			helper.assertTrue(Ships.of(level, hall).isEmpty() && Ships.sinking(level).stream().anyMatch(s -> s.ship().equals(ship)),
				"after a save and load the sinking ship: anchored " + Ships.of(level, hall) + ", going " + Ships.sinking(level));
			helper.assertTrue(level.getBlockEntity(helper.absolutePos(NORTH_RAFT.above())) instanceof ChestBlockEntity c && c.getLootTable() != null
				&& c.getLootTable().location().equals(AliveWorkplace.id("chests/pirate_ship")), "no loot chest on the raft: "
				+ level.getBlockState(helper.absolutePos(NORTH_RAFT.above())));
			helper.runAfterDelay(10, () -> {
				List<ItemEntity> loose = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8));
				helper.assertTrue(loose.isEmpty(), "loot floats loose in the sea besides the raft's chest: "
					+ loose.stream().map(i -> i.getItem().getCount() + " " + i.getItem().getItem()).toList());
			});
			helper.runAfterDelay(Ships.SINK_TICKS + 10, () -> {
				List<ItemEntity> loose = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8));
				helper.assertTrue(loose.isEmpty(), "after the sinking loot floats loose in the sea: "
					+ loose.stream().map(i -> i.getItem().getCount() + " " + i.getItem().getItem()).toList());
				helper.assertTrue(Ships.sinking(level).stream().noneMatch(s -> s.ship().equals(ship)) && Ships.of(level, hall).isEmpty(), "the ship is still sinking");
				helper.succeed();
			});
		});
	}

	/**
	 * The ship goes down top down at an even pace over its ten seconds: each second (every tick a layer isn't due to go)
	 * the layers the pace has reached are gone, every one of its own blocks, and the next layer down still stands. After
	 * the ten seconds what was below the waterline is sea and what was above it is open air: no water hangs where the
	 * cabin and the masts were.
	 */
	//$ gametest_ticks_batch AREA '320' '"qa1116dShipSinkPace"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "qa1116dShipSinkPace")
	public void theShipSinksLayerByLayerAtAnEvenPaceAndLeavesNoWaterAboveTheSea(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		Ships.Placed[] ship = {null};
		long[] began = {-1};
		List<Map<BlockPos, BlockState>> blocks = new ArrayList<>();
		int[] checked = {0};
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchored(helper, hall, 150);
			ship[0] = Ships.of(level, hall).orElseThrow();
			blocks.add(Ships.blocks(level, ship[0]));
			captainFalls(level, lair);
			began[0] = level.getGameTime();
		});
		for (int t = 4; t < 2 + Ships.SINK_TICKS; t++) {
			helper.runAfterDelay(t, () -> {
				if (ship[0] == null) {
					return;
				}
				Map<BlockPos, BlockState> all = blocks.get(0);
				int top = all.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
				int bottom = all.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow();
				int height = top - bottom + 1;
				long elapsed = level.getGameTime() - began[0];
				// The layers an even pace has taken by now; only ticks where that is the same a tick either side of now.
				int gone = (int) Math.ceil(height * (double) elapsed / Ships.SINK_TICKS);
				if (elapsed < 2 || elapsed >= Ships.SINK_TICKS - 1 || gone != (int) Math.ceil(height * (double) (elapsed - 1) / Ships.SINK_TICKS)
					|| gone != (int) Math.ceil(height * (double) (elapsed + 1) / Ships.SINK_TICKS)) {
					return;
				}
				int lowestGone = top - gone + 1;
				int standing = lowestGone - 1;
				boolean layerStands = false;
				boolean layerHasBlocks = false;
				for (Map.Entry<BlockPos, BlockState> e : all.entrySet()) {
					if (e.getValue().isAir() || e.getValue().is(Blocks.CHEST)) {
						continue;
					}
					boolean still = MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue());
					int y = e.getKey().getY();
					if (y >= lowestGone) {
						helper.assertFalse(still, elapsed + " ticks into the sinking (" + gone + " of " + height + " layers due) a ship block still stands at "
							+ e.getKey() + " (y " + y + ", the top " + top + "): " + level.getBlockState(e.getKey()));
					} else if (y == standing) {
						layerHasBlocks = true;
						layerStands |= still;
					}
				}
				helper.assertTrue(!layerHasBlocks || layerStands, elapsed + " ticks into the sinking (" + gone + " of " + height
					+ " layers due) the layer at y " + standing + ", next below those due, is already gone");
				checked[0]++;
			});
		}
		helper.runAfterDelay(2 + Ships.SINK_TICKS + 10, () -> {
			helper.assertTrue(checked[0] > 50, "only " + checked[0] + " ticks of the sinking checked");
			helper.assertTrue(Ships.sinking(level).stream().noneMatch(s -> s.ship().equals(ship[0])), "the ship is still sinking");
			int water = ship[0].waterTop();
			List<String> hanging = new ArrayList<>();
			List<String> dry = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.get(0).entrySet()) {
				BlockPos p = e.getKey();
				if (p.getY() > water) {
					if (!level.getFluidState(p).isEmpty()) {
						hanging.add(helper.relativePos(p) + " " + level.getBlockState(p));
					}
				} else if (!level.getFluidState(p).is(FluidTags.WATER)) {
					dry.add(helper.relativePos(p) + " " + level.getBlockState(p) + " (was " + e.getValue() + ")");
				}
			}
			helper.assertTrue(hanging.isEmpty(), "water hangs above the sea (its top at y " + helper.relativePos(new BlockPos(0, water, 0)).getY()
				+ ") where the ship was, at " + hanging.size() + ": " + hanging.subList(0, Math.min(6, hanging.size())));
			helper.assertTrue(dry.isEmpty(), "no sea where the hull was (the hold isn't flooded) at " + dry.size() + ": " + dry.subList(0, Math.min(6, dry.size())));
			helper.succeed();
		});
	}

	/**
	 * A village's beach is anywhere within it, out to its very edge: with the village's radius 2, the only ground at the
	 * water's edge two blocks east of the hall is its beach, and then (the water moved) the only one two blocks south.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:huge_area"' '40' '"qa1116dShipBeachEdge"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 40, batch = "qa1116dShipBeachEdge")
	public void aBeachAtTheVillagesVeryEdgeIsItsBeach(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 2;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		// A stone floor round the village (top at y 2) with open air over it, and one pool block in it at a time.
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				level.setBlock(helper.absolutePos(new BlockPos(10 + dx, 2, 10 + dz)), Blocks.STONE.defaultBlockState(), 2);
				for (int y = 3; y <= 20; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(10 + dx, y, 10 + dz)), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		BlockPos hallRel = new BlockPos(10, 3, 10);
		BlockPos east = new BlockPos(13, 2, 10); // the pool block three east: its edge two east of the hall is in the village
		BlockPos south = new BlockPos(10, 2, 13);
		helper.setBlock(east, Blocks.WATER);
		helper.runAfterDelay(1, () -> {
			BlockPos hall = helper.absolutePos(hallRel);
			helper.assertTrue(level.getFluidState(helper.absolutePos(east)).is(FluidTags.WATER), "the pool's water ran off");
			BlockPos beach = Ships.beach(level, hall, helper.absolutePos(east));
			helper.assertTrue(helper.absolutePos(new BlockPos(12, 3, 10)).equals(beach),
				"the ground at the water's edge two blocks east of the hall (the village's edge) isn't its beach: " + (beach == null ? null : helper.relativePos(beach)));
			helper.setBlock(east, Blocks.STONE);
			helper.setBlock(south, Blocks.WATER);
			BlockPos southBeach = Ships.beach(level, hall, helper.absolutePos(south));
			helper.assertTrue(helper.absolutePos(new BlockPos(10, 3, 12)).equals(southBeach),
				"the ground at the water's edge two blocks south of the hall (the village's edge) isn't its beach: "
					+ (southBeach == null ? null : helper.relativePos(southBeach)));
			helper.succeed();
		});
	}
}
