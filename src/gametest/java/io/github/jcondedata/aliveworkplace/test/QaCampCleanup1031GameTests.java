package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-10-31), B209 and B225 from the spec, the gaps their own tests leave: a camp chest's loot all spills out (none
 * lost, none doubled); a player's block set on the camp's levelled pad stays when the land goes back; a camp on a desert
 * slope of sand leaves the sand as it was (none fallen, none floating, no holes); and after a camp's break-up a later
 * warband makes a new camp for the same village through the hall's own round, out in the ring, elsewhere.
 */
public class QaCampCleanup1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** Ticks till a broken camp is all gone, with a little to spare. */
	private static final int GONE = CampCleanup.DELAY + CampCleanup.CLEAR_TICKS + 10;
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

	private static int height(int x) {
		return 2 + Math.max(0, (x - 8) / 4);
	}

	/** A slope rising eastward over the floor: {@code top} on {@code under}, {@code surface} its top layer(s). */
	private static BlockPos slope(GameTestHelper helper, Block under, Block surface, int surfaceDepth) {
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				int top = height(x);
				for (int y = 2; y <= top; y++) {
					helper.setBlock(new BlockPos(x, y, z), y > top - surfaceDepth ? surface : under);
				}
			}
		}
		return helper.absolutePos(new BlockPos(15, height(15), 15));
	}

	private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(FROM, 1, FROM), new BlockPos(TO, TOP, TO))) {
			out.put(p.immutable(), helper.getBlockState(p));
		}
		return out;
	}

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

	/** The relative y of the camp's floor (the fire stands one above it). */
	private static int floor(GameTestHelper helper, Lairs.Lair lair) {
		return lair.pos().getY() - 1 - helper.absolutePos(BlockPos.ZERO).getY();
	}

	// 1. Chests spill their loot.

	private static final String NAMED = "QA bandit's lucky stick";

	/** How many of each test item lie on the ground round {@code middle} (named sticks counted apart). */
	private static Map<String, Integer> onGround(ServerLevel level, BlockPos middle) {
		Map<String, Integer> out = new HashMap<>();
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(middle).inflate(16, 40, 16))) {
			ItemStack s = e.getItem();
			String key = s.is(Items.STICK) && s.has(DataComponents.CUSTOM_NAME) ? "named:" + s.getHoverName().getString() : s.getItem().toString();
			out.merge(key, s.getCount(), Integer::sum);
		}
		return out;
	}

	/**
	 * A camp's two chests hold known items (a full stack, odd counts, a named item) when its chief falls: once it is down,
	 * every item lies on the ground, exactly as many as were in the chests, and no chest is left.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCampLoot"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCampLoot")
	public void aCampsChestLootAllSpillsOutNoneLostNoneDoubled(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = slope(helper, Blocks.DIRT, Blocks.GRASS_BLOCK, 1);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = camp(helper, hall, ground);
			BlockPos middle = lair.pos();
			// Open sky over the camp, as out in the world (loot pushed up out of the land comes to its top, not the area's barrier roof).
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-14, 1, -14), middle.offset(14, 40, 14))) {
				if (level.getBlockState(p).is(Blocks.BARRIER)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			List<ChestBlockEntity> chests = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, -2, -9), middle.offset(9, 6, 9))) {
				if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
					chests.add(chest);
				}
			}
			helper.assertTrue(chests.size() == 2, "the camp has " + chests.size() + " chests, not 2");
			Map<String, Integer> want = new HashMap<>();
			Item[][] items = {{Items.DIAMOND, Items.NETHER_STAR, Items.ENCHANTED_GOLDEN_APPLE}, {Items.DIAMOND, Items.HEART_OF_THE_SEA}};
			int[][] counts = {{64, 1, 3}, {37, 2}};
			for (int c = 0; c < 2; c++) {
				ChestBlockEntity chest = chests.get(c);
				chest.setLootTable(null); // (known contents, not the loot table's roll)
				chest.clearContent();
				for (int i = 0; i < items[c].length; i++) {
					chest.setItem(i * 5 + c, new ItemStack(items[c][i], counts[c][i]));
					want.merge(items[c][i].toString(), counts[c][i], Integer::sum);
				}
			}
			ItemStack named = new ItemStack(Items.STICK, 5);
			named.set(DataComponents.CUSTOM_NAME, Component.literal(NAMED));
			chests.get(1).setItem(26, named);
			want.put("named:" + NAMED, 5);
			killChief(level, lair);
			Map<String, Integer> early = onGround(level, middle);
			for (String key : want.keySet()) {
				helper.assertTrue(!early.containsKey(key), "chest items on the ground before the camp came down: " + key + " x" + early.get(key));
			}
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				int left = 0;
				for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, -2, -9), middle.offset(9, 6, 9))) {
					if (level.getBlockEntity(p) instanceof ChestBlockEntity) {
						left++;
					}
				}
				helper.assertTrue(left == 0, left + " chests still stand");
				Map<String, Integer> got = onGround(level, middle);
				for (Map.Entry<String, Integer> e : want.entrySet()) {
					int n = got.getOrDefault(e.getKey(), 0);
					helper.assertTrue(n == e.getValue(), e.getKey() + ": " + n + " on the ground, " + e.getValue() + " were in the chests (all on the ground: " + got + ")");
				}
				for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(middle).inflate(16, 40, 16))) {
					BlockPos at = e.blockPosition();
					helper.assertTrue(!level.getBlockState(at).isCollisionShapeFullBlock(level, at) && at.getY() <= middle.getY() + 6,
						"loot isn't lying on the land: " + e.getItem() + " at " + at.subtract(middle).toShortString() + " from the fire, in " + level.getBlockState(at));
				}
				helper.succeed();
			});
		});
	}

	// 2. A player's block on the camp's pad.

	/**
	 * While the camp stands a player sets blocks on its levelled pad: one where the camp cut the slope away, one where it
	 * filled a hollow, and one in place of a block of the camp's own floor. Once the camp is down and the land goes back,
	 * the three blocks are still there and every other block is as it was before the camp.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCampPlayerPad"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCampPlayerPad")
	public void aPlayersBlockOnTheCampsPadStaysWhenTheLandGoesBack(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = slope(helper, Blocks.DIRT, Blocks.GRASS_BLOCK, 1);
		helper.runAfterDelay(2, () -> {
			Map<BlockPos, BlockState> before = snapshot(helper);
			Lairs.Lair lair = camp(helper, hall, ground);
			int floor = floor(helper, lair);
			BlockPos cut = null;
			BlockPos filled = null;
			BlockPos replaced = null;
			for (int x = 8; x <= 22; x++) {
				for (int z = 8; z <= 22; z++) {
					BlockPos f = new BlockPos(x, floor, z);
					BlockPos up = f.above();
					boolean campFloor = !helper.getBlockState(f).isAir() && !helper.getBlockState(f).equals(before.get(f));
					if (!campFloor || !helper.getBlockState(up).isAir() || !helper.getBlockState(up.above()).isAir()) {
						continue;
					}
					if (cut == null && !before.get(up).isAir()) {
						cut = up; // (the slope stood here: the camp cut it away)
					} else if (filled == null && before.get(f).isAir()) {
						filled = up; // (a hollow here: the camp filled it with dirt)
					} else if (replaced == null && helper.getBlockState(f).getBlock() != Blocks.DIRT && (x + z) % 3 == 0) {
						replaced = f; // (the camp's own floor block)
					}
				}
			}
			helper.assertTrue(cut != null && filled != null && replaced != null, "(setup) no spot on the pad: cut " + cut + ", filled " + filled + ", floor " + replaced);
			helper.setBlock(cut, Blocks.LAPIS_BLOCK);
			helper.setBlock(filled, Blocks.EMERALD_BLOCK);
			helper.setBlock(replaced, Blocks.POLISHED_ANDESITE);
			BlockPos a = cut;
			BlockPos b = filled;
			BlockPos c = replaced;
			killChief(level, lair);
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				helper.assertTrue(helper.getBlockState(a).is(Blocks.LAPIS_BLOCK), "the player's block where the slope was cut is " + helper.getBlockState(a) + " at " + a.toShortString());
				helper.assertTrue(helper.getBlockState(b).is(Blocks.EMERALD_BLOCK), "the player's block on the filled hollow is " + helper.getBlockState(b) + " at " + b.toShortString());
				helper.assertTrue(helper.getBlockState(c).is(Blocks.POLISHED_ANDESITE), "the player's block in the camp's floor is " + helper.getBlockState(c) + " at " + c.toShortString());
				before.remove(a);
				before.remove(b);
				before.remove(c);
				String diff = changed(helper, before);
				helper.assertTrue(diff == null, "the rest of the land isn't as it was: " + diff);
				helper.succeed();
			});
		});
	}

	// 3. A camp in the desert.

	/**
	 * A camp on a desert slope (two layers of sand on sandstone): once it is down the land is as it was, block for block;
	 * each column's height is the slope's, no sand floats over air or fell into a hole, and nothing is still falling.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCampSand"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCampSand")
	public void aCampOnDesertSandLeavesTheSandAsItWas(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = slope(helper, Blocks.SANDSTONE, Blocks.SAND, 2);
		helper.runAfterDelay(4, () -> {
			Map<BlockPos, BlockState> before = snapshot(helper);
			Lairs.Lair lair = camp(helper, hall, ground);
			helper.assertTrue(changed(helper, before) != null, "the camp changed nothing");
			killChief(level, lair);
			helper.runAfterDelay(GONE + 20, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
				List<FallingBlockEntity> falling = level.getEntitiesOfClass(FallingBlockEntity.class, helper.getBounds().inflate(4));
				helper.assertTrue(falling.isEmpty(), falling.size() + " blocks still falling, e.g. " + (falling.isEmpty() ? "" : falling.get(0).getBlockState() + " at " + falling.get(0).blockPosition()));
				for (int x = FROM; x <= TO; x++) {
					for (int z = FROM; z <= TO; z++) {
						int top = 0;
						for (int y = 1; y <= TOP; y++) {
							BlockPos p = new BlockPos(x, y, z);
							BlockState s = helper.getBlockState(p);
							if (!s.isAir()) {
								helper.assertTrue(top == y - 1, "a hole under " + s + " at " + p.toShortString() + " (the column's ground ends at " + top + ")");
								top = y;
							}
						}
						int want = x >= 2 && x <= 28 ? height(x) : 1;
						helper.assertTrue(top == want, "the land at " + x + "," + z + " is at " + top + ", the slope at " + want);
						if (want >= 2) {
							helper.assertTrue(helper.getBlockState(new BlockPos(x, top, z)).is(Blocks.SAND), "the top at " + x + "," + z + " is " + helper.getBlockState(new BlockPos(x, top, z)));
						}
					}
				}
				String diff = changed(helper, before);
				helper.assertTrue(diff == null, "the desert isn't as it was: " + diff);
				helper.succeed();
			});
		});
	}

	// 4. A later warband makes a new camp.

	/** A random whose every float is 0 (any chance comes) but whose doubles and ints are seeded (each seed another way). */
	private static RandomSource comes(long seed) {
		return new LegacyRandomSource(seed) {
			@Override
			public float nextFloat() {
				return 0f;
			}
		};
	}

	private static String why(ServerLevel level, BlockPos hall) {
		int sites = 0;
		for (long seed = 0; seed < 40; seed++) {
			if (Lairs.site(level, hall, Lairs.NEAR, Lairs.FAR, RandomSource.create(seed)) != null) {
				sites++;
			}
		}
		return "comer=" + Lairs.comer(level, hall, true, comes(1)) + " chance=" + Lairs.dailyChance(level, hall) + " quiet="
			+ io.github.jcondedata.aliveworkplace.threat.Threats.quiet(level, hall) + " rest=" + Lairs.resting(level, hall) + " rank="
			+ VillageRanks.of(level, hall) + " sites=" + sites + "/40 lairs=" + Lairs.all(level) + " clearing=" + CampCleanup.clearing(level);
	}

	private static boolean roundsTillACamp(ServerLevel level, BlockPos hall, int seeds) {
		for (long seed = 0; seed < seeds && Lairs.near(level, hall).isEmpty(); seed++) {
			BanditCamps.round(level, hall); // (warm: the hall's own entry point, its random; then the sure rolls)
			if (Lairs.near(level, hall).isEmpty()) {
				Lairs.round(level, hall, true, comes(seed));
			}
		}
		return Lairs.near(level, hall).isPresent();
	}

	/**
	 * A camp is broken up and comes down. During the village's rest no camp comes however the dice fall; after it, the
	 * hall's round makes a new one for the same village, out in the ring (80-104 blocks off), with its own living chief, not
	 * on the old spot; and that one too comes down when its chief falls.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaCampNewWarband"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaCampNewWarband")
	public void afterABreakUpALaterWarbandMakesANewCampElsewhere(GameTestHelper helper) {
		Leftovers.halls(helper);
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean enabled = BanditCamps.ENABLED;
		BanditCamps.ENABLED = true;
		List<ChunkPos> forced = new ArrayList<>();
		Leftovers.after(helper, () -> {
			for (Lairs.Lair lair : Lairs.all(level)) {
				if (lair.hall().equals(hall)) {
					Lairs.leave(level, lair);
				}
			}
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(130, 48, 130),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
			BanditCamps.ENABLED = enabled;
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3)).setNoAi(true);
		}
		helper.runAfterDelay(2, () -> {
			long day = Chronicle.day(level) + 1;
			level.setDayTime((day - 1) * 24000L + 6000);
			Lairs.Lair first = camp(helper, hall, helper.absolutePos(new BlockPos(15, 1, 15)));
			killChief(level, first);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the broken camp is still recorded");
			helper.assertTrue(Lairs.resting(level, hall) == Lairs.REST_DAYS, "the rest: " + Lairs.resting(level, hall));
			for (long seed = 0; seed < 10; seed++) {
				Lairs.round(level, hall, true, comes(seed));
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a new camp came the day the old one was broken up");
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the old camp's clearing never ended");
				level.setDayTime((day - 1 + Lairs.REST_DAYS) * 24000L + 6000);
				helper.assertTrue(Lairs.resting(level, hall) == 0, "(setup) still resting " + Lairs.resting(level, hall) + " days");
				// The ring out round the village loaded (where a camp may come), the village a Town (bandits come).
				((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
				for (int cx = (hall.getX() - 124) >> 4; cx <= (hall.getX() + 124) >> 4; cx++) {
					for (int cz = (hall.getZ() - 124) >> 4; cz <= (hall.getZ() + 124) >> 4; cz++) {
						level.getChunk(cx, cz);
					}
				}
				helper.assertTrue(roundsTillACamp(level, hall, 40), "no new camp came after the rest: " + why(level, hall));
				Lairs.Lair next = Lairs.near(level, hall).orElseThrow();
				double dx = next.pos().getX() - hall.getX();
				double dz = next.pos().getZ() - hall.getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				helper.assertTrue(next.hall().equals(hall) && next.culture().equals(BanditCamps.CULTURE), "the new camp: " + next);
				helper.assertTrue(d >= Lairs.NEAR - 1.5 && d <= Lairs.FAR + 0.5, "the new camp is " + d + " blocks from the hall");
				helper.assertTrue(next.pos().distSqr(first.pos()) > 16 * 16, "the new camp is on the old one's spot: " + next.pos());
				helper.assertTrue(!(level.getBlockState(first.pos()).getBlock() instanceof CampfireBlock), "the old camp's fire is back");
				helper.assertTrue(!next.captain().equals(first.captain()) && level.getEntity(next.captain()) instanceof Mob m && m.isAlive(), "the new camp has no living chief");
				helper.assertTrue(level.getBlockState(next.pos()).getBlock() instanceof CampfireBlock, "the new camp has no fire: " + level.getBlockState(next.pos()));
				ChunkPos at = new ChunkPos(next.pos());
				for (int cx = at.x - 2; cx <= at.x + 2; cx++) {
					for (int cz = at.z - 2; cz <= at.z + 2; cz++) {
						if (level.setChunkForced(cx, cz, true)) {
							forced.add(new ChunkPos(cx, cz));
						}
					}
				}
				killChief(level, next);
				helper.assertTrue(Lairs.near(level, hall).isEmpty() && CampCleanup.clearing(level).size() == 1, "the new camp wasn't broken up: " + CampCleanup.clearing(level));
				helper.runAfterDelay(GONE, () -> {
					helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the new camp's clearing never ended");
					helper.assertTrue(!(level.getBlockState(next.pos()).getBlock() instanceof CampfireBlock), "the new camp still stands");
					helper.succeed();
				});
			});
		});
	}
}
