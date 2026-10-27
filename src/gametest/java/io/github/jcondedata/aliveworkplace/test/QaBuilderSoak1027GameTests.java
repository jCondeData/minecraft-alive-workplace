package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialLedger;
import io.github.jcondedata.aliveworkplace.build.StallWatch;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-b-1027-1241) for ROADMAP 23.1, the builder soak test, in a form small enough for the GameTest suite. From
 * the spec, not the code: builders build on hilly, wooded ground with materials only in chests and the storehouse and no
 * player help; every build finishes; no item is duplicated or lost (counted before and after, by hand: every container,
 * every bag, every villager's own pockets and every item lying on the ground, against the ledger's gains and uses but
 * never its own "dropped" count); a builder making no progress for 30 seconds is logged, once per stuck stretch; and a
 * soak with full materials logs no stall at all. Also: a server restart in the middle (the build site and the builders
 * saved and loaded) and a storehouse emptied of one material mid-build (the build waits, then finishes when it is
 * stocked again, with nothing lost or doubled at any moment).
 */
public class QaBuilderSoak1027GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final List<Item> HUT_ITEMS = List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.OAK_DOOR, Items.TORCH);

	// ------------------------------------------------------------------------------------------------ the crew soak layout

	/** Two benches in the north corners, their huts south of them, the storehouse in the south middle. */
	private static final BlockPos[] BENCHES = {new BlockPos(3, 0, 3), new BlockPos(26, 0, 3)};
	private static final BlockPos[] HUTS = {new BlockPos(2, 0, 10), new BlockPos(22, 0, 10)};
	private static final BlockPos STOREHOUSE = new BlockPos(14, 0, 25);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(11, 0, 25), new BlockPos(17, 0, 25), new BlockPos(14, 0, 28)};
	/** Trees: one in each hut's footprint (felled while clearing), the rest in the way between benches, huts and store. */
	private static final BlockPos[] TREES = {new BlockPos(4, 0, 12), new BlockPos(24, 0, 12), new BlockPos(14, 0, 9),
		new BlockPos(9, 0, 19), new BlockPos(20, 0, 18)};

	// ------------------------------------------------------------------------------------------------ shared helpers

	private static ServerLevel rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		int ticking = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		// No leaf decay: leaves dropping saplings and apples by themselves would be items nobody's ledger made.
		level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
		Leftovers.after(helper, () -> {
			MaterialLedger.stop();
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(ticking, level.getServer());
		});
		return level;
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	/** Gentle hills, 0 to 4 blocks over the floor, a fixed seed's wobble on top. */
	private static int[][] hills(RandomSource random) {
		int[][] h = new int[30][30];
		float px = random.nextFloat() * 6f;
		float pz = random.nextFloat() * 6f;
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				h[x][z] = Mth.clamp(Math.round(2f + 1.4f * Mth.sin(x / 4f + px) + 1.0f * Mth.cos(z / 3.5f + pz)), 0, 4);
			}
		}
		return h;
	}

	/** Grass over dirt: columns from y 2 up to {@code 1 + h}. The ground's top block is at {@code 1 + h}. */
	private static void ground(GameTestHelper helper, int[][] h) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y <= 1 + h[x][z]; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == 1 + h[x][z] ? Blocks.GRASS_BLOCK : Blocks.DIRT);
				}
			}
		}
	}

	/** Flattens a square pad of {@code radius} round {@code at} (x, z) to the height there; returns the first air above it. */
	private static BlockPos pad(GameTestHelper helper, int[][] h, BlockPos at, int radius) {
		int top = h[at.getX()][at.getZ()];
		for (int x = Math.max(0, at.getX() - radius); x <= Math.min(29, at.getX() + radius); x++) {
			for (int z = Math.max(0, at.getZ() - radius); z <= Math.min(29, at.getZ() + radius); z++) {
				for (int y = 2; y <= 6; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < 1 + top ? Blocks.DIRT : y == 1 + top && top > 0 ? Blocks.GRASS_BLOCK : Blocks.AIR);
				}
				h[x][z] = top;
			}
		}
		return new BlockPos(at.getX(), 2 + top, at.getZ());
	}

	/** A small oak: a 4-high trunk and two layers of leaves round its top. */
	private static void tree(GameTestHelper helper, int[][] h, BlockPos at, RandomSource random) {
		int base = 2 + h[at.getX()][at.getZ()];
		int height = 4 + random.nextInt(2);
		for (int dy = 0; dy < height; dy++) {
			helper.setBlock(new BlockPos(at.getX(), base + dy, at.getZ()), Blocks.OAK_LOG);
		}
		for (int dy = height - 2; dy <= height; dy++) {
			int r = dy == height ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = new BlockPos(at.getX() + dx, base + dy, at.getZ() + dz);
					if (p.getX() < 0 || p.getX() > 29 || p.getZ() < 0 || p.getZ() > 29 || (dx == 0 && dz == 0 && dy < height)) {
						continue;
					}
					if (helper.getLevel().getBlockState(helper.absolutePos(p)).isAir()) {
						helper.setBlock(p, Blocks.OAK_LEAVES);
					}
				}
			}
		}
	}

	private static Map<Item, Integer> bag(Villager villager) {
		Map<Item, Integer> out = new TreeMap<>(QaBuilderSoak1027GameTests::byId);
		for (ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks()) {
			if (!stack.isEmpty()) {
				out.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/**
	 * Everything held near the test, counted by hand: every container's slots, every villager's builder bag and own
	 * pockets, and every item lying on the ground.
	 */
	private static Map<Item, Integer> heldEverywhere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(16);
		Map<Item, Integer> out = new TreeMap<>(QaBuilderSoak1027GameTests::byId);
		BlockPos from = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos to = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
		for (int cx = from.getX() >> 4; cx <= to.getX() >> 4; cx++) {
			for (int cz = from.getZ() >> 4; cz <= to.getZ() >> 4; cz++) {
				for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (be instanceof Container box2 && box.contains(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))) {
						for (int i = 0; i < box2.getContainerSize(); i++) {
							ItemStack stack = box2.getItem(i);
							if (!stack.isEmpty()) {
								out.merge(stack.getItem(), stack.getCount(), Integer::sum);
							}
						}
					}
				}
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, Entity::isAlive)) {
			bag(v).forEach((item, n) -> out.merge(item, n, Integer::sum));
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				ItemStack stack = v.getInventory().getItem(i);
				if (!stack.isEmpty()) {
					out.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
			out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
		}
		return out;
	}

	/**
	 * stocked + gained − built in − eaten, item by item, against what is held: "none" or every item off ("+" doubled,
	 * "−" lost). The ledger's own "dropped" count is not used: dropped items are counted where they lie.
	 */
	private static String itemsOff(Map<Item, Integer> stocked, Map<Item, Integer> heldBefore, Map<Item, Integer> heldAfter) {
		Map<Item, Integer> expected = new TreeMap<>(QaBuilderSoak1027GameTests::byId);
		heldBefore.forEach((item, n) -> expected.merge(item, n, Integer::sum));
		MaterialLedger.gained().forEach((item, n) -> expected.merge(item, n, Integer::sum));
		MaterialLedger.used().forEach((item, n) -> expected.merge(item, -n, Integer::sum));
		MaterialLedger.eaten().forEach((item, n) -> expected.merge(item, -n, Integer::sum));
		heldAfter.keySet().forEach(item -> expected.putIfAbsent(item, 0));
		List<String> off = new ArrayList<>();
		expected.forEach((item, want) -> {
			int now = heldAfter.getOrDefault(item, 0);
			if (now != want) {
				off.add(BuiltInRegistries.ITEM.getKey(item).getPath() + " " + (now > want ? "+" : "") + (now - want));
			}
		});
		return off.isEmpty() ? "none" : String.join(", ", off);
	}

	private static int byId(Item a, Item b) {
		return BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b));
	}

	private static void fill(Container chest, List<ItemStack> stacks) {
		int slot = 0;
		for (ItemStack stack : stacks) {
			while (!chest.getItem(slot).isEmpty()) {
				slot++;
			}
			chest.setItem(slot, stack);
		}
	}

	/** {@code items} as full stacks. */
	private static List<ItemStack> stacks(Map<Item, Integer> items) {
		List<ItemStack> out = new ArrayList<>();
		items.forEach((item, n) -> {
			int left = n;
			while (left > 0) {
				int take = Math.min(left, item.getDefaultMaxStackSize());
				out.add(new ItemStack(item, take));
				left -= take;
			}
		});
		return out;
	}

	/** A server restart: every build site and every one of {@code builders} saved and loaded (each a fresh entity). */
	private static void restart(GameTestHelper helper, Villager[] builders) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		for (int i = 0; i < builders.length; i++) {
			Villager old = builders[i];
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(old.save(tag), "builder " + i + " wasn't saved");
			old.discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "builder " + i + " didn't load as a villager: " + loaded);
			level.addFreshEntity(loaded);
			builders[i] = (Villager) loaded;
		}
	}

	private static String where(GameTestHelper helper, BuildSite site, Villager builder) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(site.id());
		return live == null ? "done" : live.stage() + " " + live.status() + ", " + live.placed() + " placed, " + live.skipped() + " skipped, missing "
			+ live.missing() + "; builder at " + helper.relativePos(builder.blockPosition()) + " bag " + bag(builder);
	}

	// ------------------------------------------------------------------------------------------------ the crew soak

	/**
	 * Two builders on hilly ground with trees, each with its own hut to build, every material only in the storehouse's
	 * chests (none by the benches), no player about. With {@code restartMid}, a server restart in the middle (the first tick
	 * both sites are building their walls with hut materials in a bag, or 1,500 ticks in). Both huts must be finished exactly as drawn,
	 * neither site ever logged as stalled, and every item must add up by hand.
	 */
	private static void crewSoak(GameTestHelper helper, long seed, boolean restartMid, boolean help) {
		ServerLevel level = rules(helper);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		Leftovers.village(helper, 48);
		RandomSource random = RandomSource.create(seed);
		int[][] h = hills(random);
		ground(helper, h);
		// One pad for the storehouse and its chests (each chest 3 from it).
		BlockPos store = pad(helper, h, STOREHOUSE, 4);
		BlockPos[] chests = new BlockPos[STORE_CHESTS.length];
		for (int i = 0; i < STORE_CHESTS.length; i++) {
			chests[i] = STORE_CHESTS[i].atY(store.getY());
		}
		BlockPos[] benches = new BlockPos[BENCHES.length];
		for (int i = 0; i < BENCHES.length; i++) {
			benches[i] = pad(helper, h, BENCHES[i], 2);
			// an earlier batch's chest just outside the area, in the bench's reach, would be this builder's own
			Leftovers.stores(helper, helper.absolutePos(benches[i]));
		}
		for (BlockPos t : TREES) {
			tree(helper, h, t, random);
		}
		helper.setBlock(store, ModBlocks.STOREHOUSE);
		ensurePoi(level, helper.absolutePos(store));
		for (BlockPos c : chests) {
			helper.setBlock(c, Blocks.CHEST);
		}
		Villager[] builders = new Villager[BENCHES.length];
		BuildSite[] sites = new BuildSite[BENCHES.length];
		BuildPlan[] plans = new BuildPlan[BENCHES.length];
		Map<Item, Integer> stocked = new TreeMap<>(QaBuilderSoak1027GameTests::byId);
		for (int i = 0; i < BENCHES.length; i++) {
			helper.setBlock(benches[i], ModBlocks.BUILDERS_BENCH);
			builders[i] = helper.spawn(EntityType.VILLAGER, benches[i].south());
			builders[i].setPersistenceRequired();
			Builders.employ(level, builders[i], helper.absolutePos(benches[i]));
			BlockPos hut = new BlockPos(HUTS[i].getX(), 2 + h[HUTS[i].getX()][HUTS[i].getZ()], HUTS[i].getZ());
			sites[i] = Builders.start(level, builders[i], null, TEST_HUT,
				new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE));
			plans[i] = sites[i].plan(level);
			helper.assertTrue(plans[i] != null, "no test hut blueprint");
			plans[i].materials().forEach((item, n) -> stocked.merge(item, n, Integer::sum));
		}
		helper.assertTrue(!plans[0].steps(BuildPlan.Stage.CLEAR).isEmpty() || !plans[1].steps(BuildPlan.Stage.CLEAR).isEmpty(),
			"the hills and trees left nothing to clear: the test no longer covers wooded ground");
		// Every material in the storehouse's chests, spread over all three.
		List<ItemStack> all = stacks(stocked);
		for (int i = 0; i < all.size(); i++) {
			fill((Container) helper.getBlockEntity(chests[i % chests.length]), List.of(all.get(i)));
		}
		Map<Item, Integer> before = heldEverywhere(helper);
		MaterialLedger.start();
		boolean[] stalled = new boolean[BENCHES.length];
		String[] restarted = {restartMid ? null : "no restart"};
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			for (int i = 0; i < sites.length; i++) {
				stalled[i] |= StallWatch.isStalled(sites[i].id());
			}
			if (restarted[0] != null) {
				return;
			}
			BuildSite a = BuildSiteManager.get(level).get(sites[0].id());
			BuildSite b = BuildSiteManager.get(level).get(sites[1].id());
			// Both walls going up, one builder carrying hut materials: the middle of the build.
			boolean walls = a != null && b != null && a.stage() == BuildPlan.Stage.STRUCTURE && b.stage() == BuildPlan.Stage.STRUCTURE
				&& a.placed() > 0 && b.placed() > 0;
			boolean somethingCarried = HUT_ITEMS.stream().anyMatch(item -> bag(builders[0]).containsKey(item) || bag(builders[1]).containsKey(item));
			if (!(walls && somethingCarried) && ticks[0] < 1500) {
				return;
			}
			Map<Item, Integer> heldBefore = heldEverywhere(helper);
			Map<Item, Integer> bag0 = bag(builders[0]);
			Map<Item, Integer> bag1 = bag(builders[1]);
			String stages = (a == null ? "done" : a.stage() + " " + a.cursor()) + " / " + (b == null ? "done" : b.stage() + " " + b.cursor());
			restart(helper, builders);
			helper.assertTrue(bag(builders[0]).equals(bag0) && bag(builders[1]).equals(bag1),
				"a restart changed the builders' bags from " + bag0 + " / " + bag1 + " to " + bag(builders[0]) + " / " + bag(builders[1]));
			Map<Item, Integer> heldAfter = heldEverywhere(helper);
			helper.assertTrue(heldAfter.equals(heldBefore), "a restart changed what is held from " + heldBefore + " to " + heldAfter);
			restarted[0] = ticks[0] + " ticks in, at " + stages + ", bags " + bag0 + " / " + bag1;
			level.getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-soak] restarted " + restarted[0]));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "never restarted: " + where(helper, sites[0], builders[0]) + " | " + where(helper, sites[1], builders[1]));
			for (int i = 0; i < sites.length; i++) {
				helper.assertTrue(BuildSiteManager.get(level).get(sites[i].id()) == null, "hut " + i + " isn't finished (" + restarted[0] + "): "
					+ where(helper, sites[i], builders[i]));
			}
			for (int i = 0; i < sites.length; i++) {
				List<BlockPos> wrong = plans[i].unfinished(level);
				helper.assertTrue(wrong.isEmpty(), "hut " + i + " has " + wrong.size() + " block(s) wrong, e.g. "
					+ wrong.stream().limit(5).map(p -> helper.relativePos(p).toShortString() + "=" + level.getBlockState(p)).toList());
			}
			MaterialLedger.stop();
			for (int i = 0; i < sites.length; i++) {
				helper.assertFalse(stalled[i], "hut " + i + "'s builder was logged as stalled in a soak with every material in the storehouse ("
					+ restarted[0] + ")");
			}
			String off = itemsOff(stocked, before, heldEverywhere(helper));
			helper.assertTrue(off.equals("none"), "items off after the crew soak (" + restarted[0] + "): " + off + "; ledger used "
				+ MaterialLedger.used() + ", gained " + MaterialLedger.gained());
			// What the huts asked for is gone from the store: nothing of their own materials left over (doubled).
			Map<Item, Integer> left = heldEverywhere(helper);
			for (Item item : HUT_ITEMS) {
				int spare = left.getOrDefault(item, 0) - (before.getOrDefault(item, 0) - stocked.getOrDefault(item, 0));
				helper.assertTrue(spare == 0, "after both huts are built " + spare + " " + BuiltInRegistries.ITEM.getKey(item).getPath()
					+ " are still held: the huts' materials were stocked exactly, so these are doubled");
			}
		});
	}

	/**
	 * Two builders sharing one storehouse on hilly ground with trees: both huts are finished from the storehouse alone,
	 * neither builder is ever logged as stalled, and every item adds up by hand before and after.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaSoak1027Crew"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaSoak1027Crew")
	public void qaTwoBuildersShareAStorehouseOnWoodedHillsAndEveryItemAddsUp(GameTestHelper helper) {
		crewSoak(helper, 23_101L, false, false);
	}

	/** The same crew soak with a server restart in the middle: both huts still finish once, nothing lost or doubled. */
	//$ gametest_ticks_batch HUGE '4800' '"qaSoak1027CrewRestart"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaSoak1027CrewRestart")
	public void qaARestartMidSoakFinishesBothHutsWithEveryItemAccountedFor(GameTestHelper helper) {
		crewSoak(helper, 23_102L, true, false);
	}

	/**
	 * The same crew soak with builders helping each other (the server's default): the first to finish joins the other's
	 * hut as a helper. Both huts finish, nobody is logged as stalled, and every item still adds up, helpers' bags included.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaSoak1027CrewHelp"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaSoak1027CrewHelp")
	public void qaTwoBuildersHelpingEachOtherFromAStorehouseLoseNothing(GameTestHelper helper) {
		crewSoak(helper, 23_103L, false, true);
	}

	// ------------------------------------------------------------------------------------------------ the storehouse runs dry

	/** Hut materials in the world: the hut's own blocks (a door counts once, by its lower half). */
	private static Map<Item, Integer> builtIn(GameTestHelper helper, BlockPos hutOrigin) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		HUT_ITEMS.forEach(item -> out.put(item, 0));
		for (BlockPos p : BlockPos.betweenClosed(hutOrigin.offset(-1, -1, -1), hutOrigin.offset(5, 5, 5))) {
			var state = helper.getLevel().getBlockState(p);
			if (state.is(Blocks.COBBLESTONE)) {
				out.merge(Items.COBBLESTONE, 1, Integer::sum);
			} else if (state.is(Blocks.OAK_PLANKS)) {
				out.merge(Items.OAK_PLANKS, 1, Integer::sum);
			} else if (state.is(Blocks.OAK_DOOR) && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
				== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
				out.merge(Items.OAK_DOOR, 1, Integer::sum);
			} else if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
				out.merge(Items.TORCH, 1, Integer::sum);
			}
		}
		return out;
	}

	/**
	 * While the builder walks to the storehouse for its materials, the storehouse is emptied of planks (taken out by the
	 * test; a small hut's whole list fits one trip, so this is the moment the builder counts on planks that are no longer
	 * there). It builds what it has and waits, never building planks it hasn't got; when the planks come back 40 s later
	 * the hut is finished. Every 20 ticks the hut's materials add up
	 * exactly: built in + held anywhere + taken out by the test = what was stocked.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaSoak1027Dry"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaSoak1027Dry")
	public void qaAStorehouseEmptiedOfPlanksMidSupplyRunWaitsThenFinishesWithNothingLost(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Leftovers.village(helper, 48);
		BlockPos bench = new BlockPos(3, 2, 3);
		BlockPos hut = new BlockPos(3, 2, 10);
		BlockPos store = new BlockPos(14, 2, 25);
		BlockPos[] chests = {new BlockPos(11, 2, 25), new BlockPos(17, 2, 25)};
		Leftovers.stores(helper, helper.absolutePos(bench));
		// Whatever an earlier batch left in chests nearby, outside the area: not this test's.
		Map<Item, Integer> baseline = heldEverywhere(helper);
		helper.setBlock(store, ModBlocks.STOREHOUSE);
		ensurePoi(level, helper.absolutePos(store));
		for (BlockPos c : chests) {
			helper.setBlock(c, Blocks.CHEST);
		}
		Map<Item, Integer> stocked = new LinkedHashMap<>();
		stocked.put(Items.COBBLESTONE, 25);
		stocked.put(Items.OAK_PLANKS, 55);
		stocked.put(Items.OAK_DOOR, 1);
		stocked.put(Items.TORCH, 1);
		fill((Container) helper.getBlockEntity(chests[0]), List.of(new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH)));
		fill((Container) helper.getBlockEntity(chests[1]), List.of(new ItemStack(Items.OAK_PLANKS, 55)));
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager[] builder = {helper.spawn(EntityType.VILLAGER, bench.south())};
		Builders.employ(level, builder[0], helper.absolutePos(bench));
		BuildSite site = Builders.start(level, builder[0], null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		BlockPos hutAbs = helper.absolutePos(hut);
		int[] aside = {0};
		long[] t = {-1, -1, 0}; // taken at, put back at, ticks
		boolean[] waited = {false};
		helper.onEachTick(() -> {
			t[2]++;
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			boolean storeHasPlanks = ((Container) helper.getBlockEntity(chests[1])).countItem(Items.OAK_PLANKS) > 0
				|| ((Container) helper.getBlockEntity(chests[0])).countItem(Items.OAK_PLANKS) > 0;
			// On its way to the storehouse (it has counted the planks there), they are taken.
			if (t[0] < 0 && live != null && live.status() == BuildSite.Status.FETCHING && storeHasPlanks) {
				// Take every plank out of the storehouse's chests (the builder may still carry some).
				for (BlockPos c : chests) {
					Container chest = helper.getBlockEntity(c);
					for (int i = 0; i < chest.getContainerSize(); i++) {
						if (chest.getItem(i).is(Items.OAK_PLANKS)) {
							aside[0] += chest.getItem(i).getCount();
							chest.setItem(i, ItemStack.EMPTY);
						}
					}
				}
				t[0] = t[2];
			}
			if (t[0] >= 0 && t[1] < 0 && live != null && live.missing().containsKey(Items.OAK_PLANKS)) {
				waited[0] = true;
			}
			if (t[0] >= 0 && t[1] < 0 && t[2] - t[0] >= 800) {
				fill((Container) helper.getBlockEntity(chests[1]), stacks(Map.of(Items.OAK_PLANKS, aside[0])));
				aside[0] = 0;
				t[1] = t[2];
			}
			if (t[2] % 20 == 0) {
				Map<Item, Integer> built = builtIn(helper, hutAbs);
				Map<Item, Integer> held = heldEverywhere(helper);
				for (Item item : HUT_ITEMS) {
					int sum = built.get(item) + held.getOrDefault(item, 0) - baseline.getOrDefault(item, 0) + (item == Items.OAK_PLANKS ? aside[0] : 0);
					helper.assertTrue(sum == stocked.get(item), "at tick " + t[2] + " " + BuiltInRegistries.ITEM.getKey(item).getPath() + " adds up to "
						+ sum + ", not the " + stocked.get(item) + " stocked (built in " + built.get(item) + ", held " + held.getOrDefault(item, 0)
						+ ", set aside " + (item == Items.OAK_PLANKS ? aside[0] : 0) + "): " + where(helper, site, builder[0]));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(t[0] >= 0, "the builder never set out for the storehouse while it still had planks: " + where(helper, site, builder[0]));
			helper.assertTrue(aside[0] > 0 || t[1] >= 0, "the storehouse had no planks to take out: the test no longer covers running dry");
			helper.assertTrue(t[1] >= 0, "the planks aren't back yet: " + where(helper, site, builder[0]));
			helper.assertTrue(waited[0], "with the storehouse out of planks the site never said it was missing planks: " + where(helper, site, builder[0]));
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the hut isn't finished after the planks came back: "
				+ where(helper, site, builder[0]));
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut wrong, e.g. "
				+ wrong.stream().limit(5).map(p -> helper.relativePos(p).toShortString() + "=" + level.getBlockState(p)).toList());
			Map<Item, Integer> held = heldEverywhere(helper);
			for (Item item : HUT_ITEMS) {
				int spare = held.getOrDefault(item, 0) - baseline.getOrDefault(item, 0);
				helper.assertTrue(spare == 0, spare + " " + item + " still held after the hut was built from exactly its list");
			}
		});
	}

	// ------------------------------------------------------------------------------------------------ the stall line

	/**
	 * The stall line, both sides of 30 s and once per stuck stretch: a builder with 3 cobblestone of the hut's 25 builds
	 * and then waits. Its stall is logged when 600 ticks (30 s) have passed since the watch last saw progress, not one
	 * look earlier and not later; it is logged once however long the wait goes on (another 600 ticks); then 3 more
	 * cobblestone come, the site moves on, runs dry again, and that new stretch is logged once more, again at 30 s.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaSoak1027Stall"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaSoak1027Stall")
	public void qaAStallIsLoggedOncePerStuckStretchAtThirtySeconds(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlockPos benchPos = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(benchPos, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(benchPos));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		UUID id = site.id();
		int stallsBefore = StallWatch.stalls();
		// mark, since (the watch's view: its next look after the mark changed), restocked at
		long[] s = {site.progressMark(), (level.getGameTime() + 19) / 20 * 20, -1};
		boolean[] was = {false};
		List<Long> edges = new ArrayList<>(); // ticks without progress at each new stall line
		List<Long> edgeAt = new ArrayList<>();
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			boolean offShift = villager.isSleeping() || !villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK);
			if (site.progressMark() != s[0]) {
				s[0] = site.progressMark();
				s[1] = (now + 19) / 20 * 20; // the watch sees the new mark at its next look
			} else if (offShift && now % 20 == 0) {
				s[1] = now; // the watch restarts the count while the builder is off shift
			}
			boolean is = StallWatch.isStalled(id);
			if (is && !was[0]) {
				edges.add(now - s[1]);
				edgeAt.add(now);
			}
			helper.assertTrue(edges.size() <= 2, "a third stall line: " + edges + " (status " + site.status() + ", " + site.placed() + " placed)");
			was[0] = is;
			// One line so far, and it has stood 600 more ticks unrepeated: restock 3 cobblestone for a second stretch.
			if (s[2] < 0 && edges.size() == 1 && now - edgeAt.get(0) >= 600) {
				ItemStack rest = chest.getItem(0);
				chest.setItem(0, new ItemStack(Items.COBBLESTONE, rest.getCount() + 3));
				s[2] = now;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(s[2] >= 0, "the first stall never came, or came twice before the restock: " + edges + " (status " + site.status()
				+ ", " + site.placed() + " placed)");
			helper.assertTrue(edges.size() == 2, "after the restock the site didn't stall again (" + edges.size() + " lines; status " + site.status()
				+ ", " + site.placed() + " placed)");
			helper.assertTrue(level.getGameTime() - edgeAt.get(1) >= 200, "waiting 200 ticks past the second line");
			for (long after : edges) {
				helper.assertTrue(after >= StallWatch.STALL_TICKS && after <= StallWatch.STALL_TICKS + 2,
					"a stall line came " + after + " ticks after the last progress, not at 30 s (" + edges + ")");
			}
			helper.assertTrue(edgeAt.get(1) > s[2], "the second line came before the restock");
			helper.assertTrue(StallWatch.stalls() - stallsBefore >= 2, "the server's stall count rose by " + (StallWatch.stalls() - stallsBefore));
		});
	}
}
