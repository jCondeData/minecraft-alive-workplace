package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * QA (2026-11-09) for B1111 and B1112, from the owner's report: on the live server a builder's requests sat in the
 * Storehouse list ("For the Builder, 1 block away", "you have none") and no crafter made them: the Mason no Stone Brick
 * Slab with stone bricks in the store, the Carpenter no Chest, Oak Ladder or Spruce Door, and a Brewing Stand, Flower Pot,
 * beds and banners unmade with them. Expected (both bugs): the crafter makes them when the store holds the materials, or
 * the request says plainly what is missing. Each test sets up what a live village looks like (a real Storehouse block
 * with its chest, the builder's table by it, a real blueprint site, the crafters at their own workstations) and varies
 * one thing toward the owner's village. These pass; the failing ones are in QaStalledCrafting1109bBugTests. Each test has its own batch: crafters' claims and the village are shared state.
 */
public class QaStalledCrafting1109bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	/** The builder's Blueprint Table, by the Storehouse as in the owner's village (the list said "1 block away"). */
	static final BlockPos BENCH = new BlockPos(3, 2, 3);
	static final BlockPos STOREHOUSE = new BlockPos(5, 2, 3);
	static final BlockPos STORE_CHEST = new BlockPos(5, 2, 5);
	static final BlockPos STORE_CHEST_2 = new BlockPos(6, 2, 5);
	/** A chest by the builder's own table, only in the tests that give him one. */
	static final BlockPos BUILDER_CHEST = new BlockPos(1, 2, 3);
	static final BlockPos CUTTER = new BlockPos(16, 2, 3);
	static final BlockPos CARPENTER_BENCH = new BlockPos(16, 2, 8);
	/** Where the blueprint stands. */
	static final BlockPos SITE = new BlockPos(4, 2, 13);

	// ------------------------------------------------------------------------------------------------------ B1111


	/**
	 * B1111, a big build: the blueprint is a stone brick wall (20 bricks) with 4 stone brick slabs on top, and the
	 * Storehouse holds 18 stone bricks: two short for the wall. Expected: the slabs are made (2 bricks cut into 4 slabs),
	 * or the builder's request says plainly what is missing ("short of ... Stone Bricks").
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaStall1109BigBuild"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaStall1109BigBuild")
	public void b1111ABigBuildShortOfBricksStillSaysWhyTheSlabsWait(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (int x = 0; x < 10; x++) {
			for (int y = 0; y < 2; y++) {
				blocks.put(new BlockPos(x, y, 0), Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		for (int x = 0; x < 4; x++) {
			blocks.put(new BlockPos(x, 0, 2), Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
		}
		Run run = setUp(helper, "bigbuild", blocks, true, false, List.of(new ItemStack(Items.STONE_BRICKS, 18)), false, false, false);
		madeOrSaid(helper, run, Map.of(Blocks.STONE_BRICK_SLAB, 4), Items.STONE_BRICK_SLAB, 1800);
	}

	// ------------------------------------------------------------------------------------------------------ B1112

	/**
	 * B1112, the builder's own chest full: a live builder's chest by his table fills up with what he cleared off his site.
	 * The Storehouse by him holds planks, sticks and spruce planks. Expected: the Carpenter makes the chest, the oak ladder
	 * and the spruce door (into the Storehouse he also takes from), or the request says plainly why not.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaStall1109OwnChestFull"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaStall1109OwnChestFull")
	public void b1112TheCarpenterServesABuilderWhoseOwnChestIsFull(GameTestHelper helper) {
		Run run = setUp(helper, "ownfull", carpenterRow(), false, true, woodStore(), false, true, false);
		madeOrSaid(helper, run, Map.of(Blocks.CHEST, 1, Blocks.LADDER, 1, Blocks.SPRUCE_DOOR, 1), Items.CHEST, 1800);
	}


	// ------------------------------------------------------------------------------------------------------ baselines

	/** B1111's own test: stone bricks in the Storehouse, the builder by it waits for 4 stone brick slabs: the Mason cuts them. */
	//$ gametest_ticks_batch BIG '2400' '"qaStall1109Slabs"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaStall1109Slabs")
	public void theMasonCutsStoneBrickSlabsFromTheStorehousesBricks(GameTestHelper helper) {
		Run run = setUp(helper, "slabs", slabRow(), true, false, List.of(new ItemStack(Items.STONE_BRICKS, 16)), false, false, false);
		madeOrSaid(helper, run, Map.of(Blocks.STONE_BRICK_SLAB, 4), Items.STONE_BRICK_SLAB, Integer.MAX_VALUE);
	}

	/** B1111, only stone in the Storehouse: the Mason makes the slabs (stone to stone brick slab at the stonecutter). */
	//$ gametest_ticks_batch BIG '2400' '"qaStall1109SlabsStone"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaStall1109SlabsStone")
	public void theMasonCutsStoneBrickSlabsFromPlainStone(GameTestHelper helper) {
		Run run = setUp(helper, "slabstone", slabRow(), true, false, List.of(new ItemStack(Items.STONE, 16)), false, false, false);
		madeOrSaid(helper, run, Map.of(Blocks.STONE_BRICK_SLAB, 4), Items.STONE_BRICK_SLAB, Integer.MAX_VALUE);
	}

	/** B1112's own test: planks, sticks and spruce planks in the Storehouse: the Carpenter makes the chest, ladder and door. */
	//$ gametest_ticks_batch BIG '2400' '"qaStall1109Wood"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaStall1109Wood")
	public void theCarpenterMakesTheChestLadderAndSpruceDoor(GameTestHelper helper) {
		Run run = setUp(helper, "wood", carpenterRow(), false, true, woodStore(), false, false, false);
		madeOrSaid(helper, run, Map.of(Blocks.CHEST, 1, Blocks.LADDER, 1, Blocks.SPRUCE_DOOR, 1), Items.CHEST, Integer.MAX_VALUE);
	}

	/**
	 * The owner's list at once, the store with room: an unmakeable brewing stand and flower pot ahead of stone brick
	 * slabs, a chest, a ladder and a door. One stuck request mustn't hold up the rest: all the makeable ones are built.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaStall1109All"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaStall1109All")
	public void anUnmakeableBrewingStandIsNamedAndTheChestBeforeItBuilt(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(carpenterRow());
		blocks.put(new BlockPos(8, 0, 2), Blocks.BREWING_STAND.defaultBlockState());
		blocks.put(new BlockPos(9, 0, 2), Blocks.FLOWER_POT.defaultBlockState());
		slabRow().forEach((p, s) -> blocks.put(p.offset(0, 0, 4), s));
		List<ItemStack> store = new ArrayList<>(woodStore());
		store.add(new ItemStack(Items.STONE_BRICKS, 16));
		Run run = setUp(helper, "all", blocks, true, true, store, false, false, false);
		// The builder reaches the brewing stand before the ladder, door and slabs and waits there (they aren't asked for
		// yet); the chest before it is made and built, and the stuck ones are named with what they lack.
		madeOrSaid(helper, run, Map.of(Blocks.CHEST, 1, Blocks.BREWING_STAND, 1, Blocks.FLOWER_POT, 1), Items.CHEST, 1500);
	}

	/**
	 * Saved and loaded mid-wait: the Mason and the Carpenter are reloaded after the builder has been waiting a while and
	 * before anything is made; afterwards they still make the slabs, chest, ladder and door.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaStall1109Reload"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaStall1109Reload")
	public void craftersReloadedMidWaitStillMakeEverything(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(carpenterRow());
		slabRow().forEach((p, s) -> blocks.put(p.offset(0, 0, 4), s));
		List<ItemStack> store = new ArrayList<>(woodStore());
		store.add(new ItemStack(Items.STONE_BRICKS, 16));
		Run run = setUp(helper, "reload", blocks, true, true, store, false, false, false);
		helper.runAfterDelay(60, () -> {
			for (int i = 0; i < run.crafters().size(); i++) {
				run.crafters().set(i, QaCarpenterStorehouse1101GameTests.reload(helper.getLevel(), run.crafters().get(i)));
			}
		});
		madeOrSaid(helper, run, Map.of(Blocks.CHEST, 1, Blocks.LADDER, 1, Blocks.SPRUCE_DOOR, 1, Blocks.STONE_BRICK_SLAB, 4), Items.CHEST,
			Integer.MAX_VALUE);
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	record Run(ServerLevel level, long start, BuildSite site, Villager builder, List<Villager> crafters) {
		long tick() {
			return level.getGameTime() - start;
		}
	}

	/** A row of 4 bottom stone brick slabs. */
	static Map<BlockPos, BlockState> slabRow() {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (int x = 0; x < 4; x++) {
			blocks.put(new BlockPos(x, 0, 0), Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
		}
		return blocks;
	}

	/** A stone brick post with an oak ladder on its front, a chest, and a spruce door (both halves). */
	static Map<BlockPos, BlockState> carpenterRow() {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		blocks.put(new BlockPos(0, 0, 1), Blocks.STONE_BRICKS.defaultBlockState());
		blocks.put(new BlockPos(0, 0, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		blocks.put(new BlockPos(2, 0, 0), Blocks.CHEST.defaultBlockState());
		blocks.put(new BlockPos(4, 0, 0), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		blocks.put(new BlockPos(4, 1, 0), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		return blocks;
	}

	/** What the Carpenter's three need, and a stone brick for the ladder's post. */
	static List<ItemStack> woodStore() {
		return List.of(new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.STICK, 16), new ItemStack(Items.SPRUCE_PLANKS, 16),
			new ItemStack(Items.STONE_BRICKS, 2));
	}

	/**
	 * A village: a Storehouse with its chest(s) holding {@code store}, the builder at his table by it with a site for
	 * {@code blocks}, a Mason at a stonecutter and/or a Carpenter at his bench. {@code fullStore}: the store's chests are
	 * filled to the last slot with cobblestone around {@code store}. {@code builderChestFull}: a chest by the builder's
	 * table full of dirt.
	 */
	static Run setUp(GameTestHelper helper, String name, Map<BlockPos, BlockState> blocks, boolean mason, boolean carpenter, List<ItemStack> store,
		boolean fullStore, boolean builderChestFull, boolean secondStoreChest) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		ResourceLocation id = blueprint(helper, name, blocks);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(STOREHOUSE));
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		fill(helper.getBlockEntity(STORE_CHEST), store, fullStore ? Items.COBBLESTONE : null);
		if (secondStoreChest) {
			helper.setBlock(STORE_CHEST_2, Blocks.CHEST);
		}
		if (builderChestFull) {
			helper.setBlock(BUILDER_CHEST, Blocks.CHEST);
			fill(helper.getBlockEntity(BUILDER_CHEST), List.of(), Items.DIRT);
		}
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(BENCH));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.north());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SITE),
			Rotation.NONE, Mirror.NONE));
		if (site.plan(level) == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		List<Villager> crafters = new ArrayList<>();
		if (mason) {
			helper.setBlock(CUTTER, Blocks.STONECUTTER);
			Villager v = helper.spawn(EntityType.VILLAGER, CUTTER.north());
			Jobs.employ(level, v, helper.absolutePos(CUTTER), PoiTypes.MASON, VillagerProfession.MASON);
			crafters.add(v);
		}
		if (carpenter) {
			helper.setBlock(CARPENTER_BENCH, ModBlocks.CARPENTERS_BENCH);
			Villager v = helper.spawn(EntityType.VILLAGER, CARPENTER_BENCH.north());
			Jobs.employ(level, v, helper.absolutePos(CARPENTER_BENCH), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
			crafters.add(v);
		}
		return new Run(level, level.getGameTime(), site, builder, crafters);
	}

	/** {@code store} first, then (with a {@code filler}) every slot left filled with a full stack of it. */
	static void fill(Container chest, List<ItemStack> store, Item filler) {
		int slot = 0;
		for (ItemStack stack : store) {
			chest.setItem(slot++, stack.copy());
		}
		if (filler != null) {
			for (; slot < chest.getContainerSize(); slot++) {
				chest.setItem(slot, new ItemStack(filler, 64));
			}
		}
	}

	/** {@code blocks} drawn at {@link #SITE}, saved as a blueprint, and the spot cleared again. */
	static ResourceLocation blueprint(GameTestHelper helper, String name, Map<BlockPos, BlockState> blocks) {
		ServerLevel level = helper.getLevel();
		int sx = 1, sy = 1, sz = 1;
		for (BlockPos p : blocks.keySet()) {
			sx = Math.max(sx, p.getX() + 1);
			sy = Math.max(sy, p.getY() + 1);
			sz = Math.max(sz, p.getZ() + 1);
		}
		blocks.forEach((p, s) -> helper.getLevel().setBlock(helper.absolutePos(SITE.offset(p)), s, 2 | 16));
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1109b_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SITE), new Vec3i(sx, sy, sz), false, Blocks.STRUCTURE_VOID);
		List<BlockPos> top = new ArrayList<>(blocks.keySet());
		top.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos p : top) {
			helper.getLevel().setBlock(helper.absolutePos(SITE.offset(p)), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		return id;
	}

	/** How many of {@code block} stand on the site. */
	static int placed(GameTestHelper helper, Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(SITE, SITE.offset(12, 3, 8))) {
			BlockState s = helper.getBlockState(p);
			if (s.is(block) && !(s.hasProperty(DoorBlock.HALF) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)) {
				n++;
			}
		}
		return n;
	}

	/** {@code item} anywhere it could be made to: the store's chests, the builder's chest, the bags. */
	static int made(GameTestHelper helper, Item item) {
		int n = 0;
		for (BlockPos p : List.of(STORE_CHEST, STORE_CHEST_2, BUILDER_CHEST)) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				n += c.countItem(item);
			}
		}
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		return n;
	}

	/**
	 * Succeeds once every one of {@code want} stands on the site (made and built), or, after {@code sayBy} ticks, if the
	 * builder's request line says plainly why the wanted things wait. Fails at the timeout with what stands, what was
	 * made, the site's status and missing list, and each crafter's job.
	 */
	static void madeOrSaid(GameTestHelper helper, Run run, Map<Block, Integer> want, Item probe, int sayBy) {
		AtomicReference<String> said = new AtomicReference<>();
		AtomicBoolean anyMade = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (made(helper, probe) > 0) {
				anyMade.set(true);
			}
			if (run.tick() % 100 == 0) {
				BuildSite live = BuildSiteManager.get(run.level()).get(run.site().id());
				if (live != null) {
					Component line = JobShortages.line(run.level(), live.id(), live.missing());
					said.set(line == null ? null : line.getString());
				}
			}
		});
		helper.succeedWhen(() -> {
			boolean allPlaced = want.entrySet().stream().allMatch(e -> placed(helper, e.getKey()) >= e.getValue());
			if (allPlaced) {
				return;
			}
			String line = said.get();
			// "Says plainly what is missing": the line names every wanted thing still unbuilt and still asked for, and never
			// claims nobody here makes what a crafter at work here does make. (B1112's fix: a thing made and waiting in his
			// store or bag is no longer asked for, so the line, which lists only what's missing, can't name it: it was made.)
			BuildSite asked = BuildSiteManager.get(run.level()).get(run.site().id());
			boolean namesAll = line != null && want.entrySet().stream().filter(e -> placed(helper, e.getKey()) < e.getValue())
				.filter(e -> asked == null || asked.missing().containsKey(e.getKey().asItem()))
				.allMatch(e -> line.contains(e.getKey().getName().getString()));
			if (run.tick() >= sayBy && namesAll && !line.contains("Nobody here makes")) {
				helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa1109b] said instead: " + line));
				return;
			}
			StringBuilder placedNow = new StringBuilder();
			want.forEach((b, n) -> placedNow.append(b.getName().getString()).append(' ').append(placed(helper, b)).append('/').append(n).append(", "));
			BuildSite live = BuildSiteManager.get(run.level()).get(run.site().id());
			StringBuilder jobs = new StringBuilder();
			for (Villager v : run.crafters()) {
				jobs.append(v.getVillagerData().getProfession()).append(": ").append(ModAttachments.CRAFTER_JOB.get(v)).append(" bag ")
					.append(ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()).append("; ");
			}
			throw new GameTestAssertException("at tick " + run.tick() + " nothing made and nothing said: placed " + placedNow + "made "
				+ made(helper, probe) + " " + probe + " (ever: " + anyMade.get() + "), site " + (live == null ? "gone" : live.status() + " missing "
				+ live.missing()) + ", builder's line " + line + ", crafters " + jobs);
		});
	}
}
