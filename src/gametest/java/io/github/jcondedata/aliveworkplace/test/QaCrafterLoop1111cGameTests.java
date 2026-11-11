package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-11, lane c) for the owner's B1171, written from his words, not from lane A's theory: "the Carpenter and
 * the Mason get stuck in a loop making items for the builder; the Carpenter carries them to the builder's house but the
 * Porter never picks them up, the builder never gets them, and they keep making and delivering again". Expected: what a
 * crafter makes for a builder reaches the chests the build is fed from (or the Porter collects it), and a request
 * already filled is never made again.
 *
 * <p>Every test sets the village up the way a player does: he places the Storehouse (so it is his, as
 * {@code StorehouseBlock.setPlacedBy} makes it), a Blueprint Table with chests by it, and hands the builder a placed
 * blueprint ({@link Builders#assign}, which hires him); the Carpenter and the Mason are the village's own workers at
 * their own workshops with makings in their own chests; a Porter works the Storehouse. Then it watches the whole test
 * area: every log, plank, stone and part in every chest, bag, inventory, on the ground and built, so nothing can be made
 * twice or lost unseen, and for {@link #SETTLE} ticks after the build is done nothing more is crafted. Each test has its
 * own batch: crafters' claims and the village are shared state.
 */
public class QaCrafterLoop1111cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	/** How long after the build is done the crafters are watched for a second batch (a crafter looks every 100 ticks). */
	static final int SETTLE = 2000;
	static final int TIMEOUT = 7000;

	static final BlockPos BENCH = new BlockPos(4, 2, 4);
	static final BlockPos BENCH_CHEST = new BlockPos(2, 2, 4);
	static final BlockPos BENCH_CHEST_2 = new BlockPos(2, 2, 6);
	static final BlockPos SITE = new BlockPos(4, 2, 13);
	static final BlockPos STOREHOUSE = new BlockPos(24, 2, 4);
	static final BlockPos STORE_CHEST = new BlockPos(24, 2, 6);
	static final BlockPos STORE_CHEST_2 = new BlockPos(26, 2, 6);
	static final BlockPos CARPENTER_BENCH = new BlockPos(24, 2, 24);
	static final BlockPos CARPENTER_CHEST = new BlockPos(26, 2, 24);
	static final BlockPos CUTTER = new BlockPos(14, 2, 26);
	static final BlockPos MASON_CHEST = new BlockPos(12, 2, 26);
	/** Scenario 3: the builder's bed and the chest in his house, away from his table. */
	static final BlockPos BED_HEAD = new BlockPos(26, 2, 13);
	static final BlockPos HOUSE_CHEST = new BlockPos(27, 2, 15);
	/** Scenario 4: the second builder's table, chest and site. */
	static final BlockPos BENCH_B = new BlockPos(4, 2, 26);
	static final BlockPos BENCH_B_CHEST = new BlockPos(2, 2, 26);
	static final BlockPos SITE_B = new BlockPos(14, 2, 13);

	static final List<Item> WATCHED = List.of(Items.OAK_LOG, Items.OAK_PLANKS, Items.OAK_STAIRS, Items.STONE, Items.STONE_BRICKS,
		Items.STONE_BRICK_STAIRS);

	// ------------------------------------------------------------------------------------------------------ tests

	/**
	 * Scenario 1, the owner's village: a builder (no helpers) at his table with two chests, his blueprint needing 8 oak
	 * planks, 4 oak stairs, 4 stone bricks and 4 stone brick stairs; a Storehouse with 6 oak logs and 12 stone and its
	 * Porter; the Carpenter with 8 oak logs in his workshop chest, the Mason with 16 stone in his. Everything is made,
	 * reaches the build and is built; at no time do more than 4 oak stairs or 4 stone brick stairs exist; no more than 5
	 * logs and 12 stone are used; the wood adds up exactly at the end (nothing lost, nothing doubled); and nothing more is
	 * crafted for 2000 ticks after the build is done.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cPlain"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cPlain")
	public void theOwnersVillageMakesEachPartOnceAndBuildsIt(GameTestHelper helper) {
		Run run = village(helper, "plain", mixedRow(), true, true);
		chests(helper, false);
		watch(helper, run, List.of());
	}

	/**
	 * Scenario 2: both chests by the builder's table are full (of dirt from his last clearing). The parts must still be
	 * made once and reach a store the build takes from (the Storehouse), be built, and not be made again.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cFull"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cFull")
	public void withTheBuildersChestsFullThePartsAreStillMadeOnce(GameTestHelper helper) {
		Run run = village(helper, "full", mixedRow(), true, true);
		chests(helper, true);
		watch(helper, run, List.of());
	}

	/**
	 * Scenario 3: the builder lives in a house away from his table (his bed 22 blocks off, a chest in the house). The
	 * crafters bring the parts to the chests by his table: none of what they make ever lands in his house's chest, and
	 * the build is done with each part made once.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cHouse"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cHouse")
	public void aBuilderLivingAwayFromHisTableGetsThePartsAtTheTable(GameTestHelper helper) {
		Run run = village(helper, "house", mixedRow(), true, true);
		chests(helper, false);
		ServerLevel level = helper.getLevel();
		BlockPos foot = BED_HEAD.south();
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(BED_HEAD, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos head = helper.absolutePos(BED_HEAD);
		QaBuilderDisruptionGameTests.ensurePoi(level, head);
		level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 1);
		Villager builder = run.villager(run.builders.get(0));
		builder.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		helper.setBlock(HOUSE_CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(HOUSE_CHEST)).setItem(0, new ItemStack(Items.BREAD, 3));
		watch(helper, run, List.of(HOUSE_CHEST));
	}

	/**
	 * Scenario 1 with a big order: 48 oak stairs and 16 oak planks (more than one bagful of makings: 22 logs' worth), the
	 * Storehouse with 30 oak logs. Made a bagful at a time, each delivery coming off the list while the builder builds
	 * on: at no time more than 48 oak stairs exist, at most 23 logs are sawn, and nothing more is crafted after.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cBig"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cBig")
	public void aBigOrderIsMadeABagfulAtATimeAndNoMore(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (int x = 0; x < 12; x++) {
			for (int z = 0; z < 4; z++) {
				blocks.put(new BlockPos(x, 0, z), Blocks.OAK_STAIRS.defaultBlockState());
			}
		}
		for (int x = 0; x < 8; x++) {
			for (int z = 4; z < 6; z++) {
				blocks.put(new BlockPos(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
			}
		}
		Run run = village(helper, "big", blocks, true, false);
		for (BlockPos p : List.of(BENCH_CHEST, BENCH_CHEST_2, STORE_CHEST, STORE_CHEST_2, CARPENTER_CHEST)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		put(helper, STORE_CHEST, new ItemStack(Items.OAK_LOG, 30));
		watch(helper, run, List.of());
	}

	/**
	 * A builder with no chest by his table at all: what is made for him goes to the village's Storehouse, where he takes
	 * from, and is made once (the Porter's store is the build's store).
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cChestless"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cChestless")
	public void aBuilderWithNoChestsGetsHisPartsThroughTheStorehouseOnce(GameTestHelper helper) {
		Run run = village(helper, "chestless", mixedRow(), true, true);
		chests(helper, false);
		helper.setBlock(BENCH_CHEST, Blocks.AIR);
		helper.setBlock(BENCH_CHEST_2, Blocks.AIR);
		watch(helper, run, List.of());
	}

	/**
	 * The helper scenario's control, with no crafting at all: every part is in the chests by the lead's table from the
	 * start, no Carpenter or Mason, and the village's second builder comes to help. The build is done.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cHelperStocked"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cHelperStocked")
	public void aVillageBuilderHelpingWithEverythingInStockFinishesTheBuild(GameTestHelper helper) {
		Run run = village(helper, "helperstocked", mixedRow(), false, false);
		ServerLevel level = helper.getLevel();
		for (BlockPos p : List.of(BENCH_CHEST, BENCH_CHEST_2, STORE_CHEST, STORE_CHEST_2)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		put(helper, BENCH_CHEST, new ItemStack(Items.OAK_PLANKS, 8), new ItemStack(Items.OAK_STAIRS, 4), new ItemStack(Items.STONE_BRICKS, 4),
			new ItemStack(Items.STONE_BRICK_STAIRS, 4));
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		helper.setBlock(BENCH_B, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(BENCH_B_CHEST, Blocks.CHEST);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(BENCH_B));
		Villager mate = helper.spawn(EntityType.VILLAGER, BENCH_B.north());
		Builders.employ(level, mate, helper.absolutePos(BENCH_B));
		run.others.add(mate.getUUID());
		AtomicBoolean helped = new AtomicBoolean();
		helper.onEachTick(() -> {
			Villager v = run.villager(mate.getUUID());
			if (v != null && Builders.isHelping(v)) {
				helped.set(true);
			}
		});
		run.mustHappen.put("the second builder came to help", helped);
		watch(helper, run, List.of(BENCH_B_CHEST));
	}

	/**
	 * The wiki's own example: the makings (oak logs and stone) are in the chests by the builder's table, nothing in the
	 * Storehouse. The crafters fetch them, make the parts, bring them back, and make them once.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cOwnLogs"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cOwnLogs")
	public void makingsInTheBuildersOwnChestAreMadeOnce(GameTestHelper helper) {
		Run run = village(helper, "ownlogs", mixedRow(), true, true);
		for (BlockPos p : List.of(BENCH_CHEST, BENCH_CHEST_2, STORE_CHEST, STORE_CHEST_2, CARPENTER_CHEST, MASON_CHEST)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		put(helper, BENCH_CHEST, new ItemStack(Items.OAK_LOG, 6), new ItemStack(Items.STONE, 12));
		watch(helper, run, List.of());
	}

	/**
	 * Scenario 5, save and reload: every villager in the village (builder, Carpenter, Mason, Porter) is saved and loaded
	 * the first time a crafter is at his bench making, and again the first time one is carrying the parts to the builder.
	 * The parts still arrive, are built, and are made once.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cReload"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cReload")
	public void reloadedMidCraftAndMidDeliveryThePartsAreMadeOnce(GameTestHelper helper) {
		Run run = village(helper, "reload", mixedRow(), true, true);
		chests(helper, false);
		AtomicBoolean crafting = new AtomicBoolean();
		AtomicBoolean delivering = new AtomicBoolean();
		helper.onEachTick(() -> {
			for (UUID id : run.crafters) {
				Villager v = run.villager(id);
				CrafterJob job = v == null ? null : ModAttachments.CRAFTER_JOB.get(v);
				if (job == null) {
					continue;
				}
				if (!crafting.get() && job.phase() == CrafterJob.Phase.CRAFTING) {
					crafting.set(true);
					run.reloadAll();
					return;
				}
				if (!delivering.get() && job.phase() == CrafterJob.Phase.DELIVERING) {
					delivering.set(true);
					run.reloadAll();
					return;
				}
			}
		});
		run.mustHappen.put("a crafter caught at his bench making (reload 1)", crafting);
		run.mustHappen.put("a crafter caught carrying the parts (reload 2)", delivering);
		watch(helper, run, List.of());
	}

	// ------------------------------------------------------------------------------------------------------ set-up

	/** The run's state: who is who (by UUID, so a reloaded villager is found again), the sites, and what is counted. */
	static final class Run {
		final ServerLevel level;
		final long start;
		final ServerPlayer player;
		final List<UUID> builders = new ArrayList<>();
		final List<UUID> crafters = new ArrayList<>();
		final List<UUID> others = new ArrayList<>();
		final List<UUID> sites = new ArrayList<>();
		final ResourceLocation blueprint;
		final Map<BlockPos, BlockState> blocks;
		/** What the test area held at the start, of the watched items. */
		final Map<Item, Integer> initial = new HashMap<>();
		/** The most of an item that may ever exist (made once, nothing extra). */
		final Map<Item, Integer> caps = new HashMap<>();
		final Map<String, AtomicBoolean> mustHappen = new LinkedHashMap<>();

		Run(ServerLevel level, ServerPlayer player, ResourceLocation blueprint, Map<BlockPos, BlockState> blocks) {
			this.level = level;
			this.start = level.getGameTime();
			this.player = player;
			this.blueprint = blueprint;
			this.blocks = blocks;
		}

		long tick() {
			return level.getGameTime() - start;
		}

		Villager villager(UUID id) {
			return level.getEntity(id) instanceof Villager v ? v : null;
		}

		List<UUID> everyone() {
			List<UUID> out = new ArrayList<>(builders);
			out.addAll(crafters);
			out.addAll(others);
			return out;
		}

		void reloadAll() {
			for (UUID id : everyone()) {
				Villager v = villager(id);
				if (v != null) {
					QaCarpenterStorehouse1101GameTests.reload(level, v);
				}
			}
		}
	}

	/** 8 oak planks, 4 oak stairs, 4 stone bricks, 4 stone brick stairs. */
	static Map<BlockPos, BlockState> mixedRow() {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (int x = 0; x < 8; x++) {
			blocks.put(new BlockPos(x, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
		}
		for (int x = 0; x < 4; x++) {
			blocks.put(new BlockPos(x, 0, 1), Blocks.OAK_STAIRS.defaultBlockState());
			blocks.put(new BlockPos(x, 0, 2), Blocks.STONE_BRICKS.defaultBlockState());
			blocks.put(new BlockPos(x, 0, 3), Blocks.STONE_BRICK_STAIRS.defaultBlockState());
		}
		return blocks;
	}

	/**
	 * The village as a player sets it up: the village switch on, his Storehouse (placed by him) with its Porter, his
	 * builder at a Blueprint Table given the placed blueprint for {@code blocks} at {@link #SITE}, and the village's
	 * Carpenter and/or Mason at their own workshops.
	 */
	static Run village(GameTestHelper helper, String name, Map<BlockPos, BlockState> blocks, boolean carpenter, boolean mason) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(1000);
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
		ResourceLocation id = QaStalledCrafting1109bGameTests.blueprint(helper, "loop1111c_" + name, blocks);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Run run = new Run(level, player, id, blocks);
		// the player's Storehouse: placed by him, so it's his (StorehouseBlock.setPlacedBy)
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		level.getBlockState(store).getBlock().setPlacedBy(level, store, level.getBlockState(store), player, new ItemStack(ModBlocks.STOREHOUSE));
		QaBuilderDisruptionGameTests.ensurePoi(level, store);
		Villager porter = helper.spawn(EntityType.VILLAGER, STOREHOUSE.north());
		Porters.employ(level, porter, store);
		run.others.add(porter.getUUID());
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		run.builders.add(builder(helper, run, BENCH, SITE));
		if (carpenter) {
			helper.setBlock(CARPENTER_BENCH, ModBlocks.CARPENTERS_BENCH);
			Villager v = helper.spawn(EntityType.VILLAGER, CARPENTER_BENCH.north());
			Jobs.employ(level, v, helper.absolutePos(CARPENTER_BENCH), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
			run.crafters.add(v.getUUID());
		}
		if (mason) {
			helper.setBlock(CUTTER, Blocks.STONECUTTER);
			Villager v = helper.spawn(EntityType.VILLAGER, CUTTER.north());
			Jobs.employ(level, v, helper.absolutePos(CUTTER), PoiTypes.MASON, VillagerProfession.MASON);
			run.crafters.add(v.getUUID());
		}
		return run;
	}

	/** A builder at the Blueprint Table at {@code bench}, handed the player's blueprint placed at {@code site}. */
	static UUID builder(GameTestHelper helper, Run run, BlockPos bench, BlockPos site) {
		ServerLevel level = helper.getLevel();
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.north());
		Builders.employ(level, builder, helper.absolutePos(bench));
		ItemStack blueprint = BlueprintItem.create(run.blueprint, null);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(site), Rotation.NONE, Mirror.NONE))));
		InteractionResult given = Builders.assign(run.player, builder, blueprint);
		BuildSite started = Builders.activeSite(level, builder);
		if (started == null) {
			throw new GameTestAssertException("setup: the builder didn't take the blueprint (" + given + ")");
		}
		run.sites.add(started.id());
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(started.id()) != null) {
				BuildSiteManager.get(level).remove(started.id());
			}
		});
		return builder.getUUID();
	}

	/**
	 * Scenarios 1, 2, 3, 5: two chests by the builder's table ({@code full}: every slot dirt), the Storehouse's two chests
	 * with 6 oak logs and 12 stone, 8 oak logs in the Carpenter's workshop chest and 16 stone in the Mason's.
	 */
	static void chests(GameTestHelper helper, boolean full) {
		for (BlockPos p : List.of(BENCH_CHEST, BENCH_CHEST_2, STORE_CHEST, STORE_CHEST_2, CARPENTER_CHEST, MASON_CHEST)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		if (full) {
			for (BlockPos p : List.of(BENCH_CHEST, BENCH_CHEST_2)) {
				Container c = (Container) helper.getBlockEntity(p);
				for (int i = 0; i < c.getContainerSize(); i++) {
					c.setItem(i, new ItemStack(Items.DIRT, 64));
				}
			}
		}
		put(helper, STORE_CHEST, new ItemStack(Items.OAK_LOG, 6), new ItemStack(Items.STONE, 12));
		put(helper, CARPENTER_CHEST, new ItemStack(Items.OAK_LOG, 8));
		put(helper, MASON_CHEST, new ItemStack(Items.STONE, 16));
	}

	static void put(GameTestHelper helper, BlockPos chest, ItemStack... stacks) {
		Container c = (Container) helper.getBlockEntity(chest);
		int slot = 0;
		for (ItemStack s : stacks) {
			while (!c.getItem(slot).isEmpty()) {
				slot++;
			}
			c.setItem(slot, s.copy());
		}
	}

	// ------------------------------------------------------------------------------------------------------ watching

	/** Every watched item in the test area: chests and other containers, villagers' bags and inventories, the ground, built. */
	static Map<Item, Integer> tally(GameTestHelper helper, Run run) {
		Map<Item, Integer> out = new HashMap<>();
		Set<Item> watched = Set.copyOf(WATCHED);
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(4);
		// built blocks only above the floor (the ground below the test area is natural stone)
		int floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		for (BlockPos p : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ)) {
			BlockState state = level.getBlockState(p);
			if (p.getY() >= floor && !state.isAir() && watched.contains(state.getBlock().asItem())) {
				out.merge(state.getBlock().asItem(), 1, Integer::sum);
			}
			if (state.hasBlockEntity()) {
				BlockEntity be = level.getBlockEntity(p);
				if (be instanceof Container c) {
					for (int i = 0; i < c.getContainerSize(); i++) {
						ItemStack s = c.getItem(i);
						if (watched.contains(s.getItem())) {
							out.merge(s.getItem(), s.getCount(), Integer::sum);
						}
					}
				}
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box.inflate(16))) {
			for (Item item : watched) {
				int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
				if (n > 0) {
					out.merge(item, n, Integer::sum);
				}
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(16))) {
			if (watched.contains(e.getItem().getItem())) {
				out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** Wood in half-planks (a log 8, a plank 2, a stair 3: 6 planks make 4). */
	static int wood(Map<Item, Integer> t) {
		return 8 * t.getOrDefault(Items.OAK_LOG, 0) + 2 * t.getOrDefault(Items.OAK_PLANKS, 0) + 3 * t.getOrDefault(Items.OAK_STAIRS, 0);
	}

	/** Stone counting a stair as one stone (the stonecutter's way, the least it can cost). */
	static int stoneLow(Map<Item, Integer> t) {
		return t.getOrDefault(Items.STONE, 0) + t.getOrDefault(Items.STONE_BRICKS, 0) + t.getOrDefault(Items.STONE_BRICK_STAIRS, 0);
	}

	/** Stone in half-stones counting a stair as one and a half (the crafting table's way, the most it can cost). */
	static int stoneHigh(Map<Item, Integer> t) {
		return 2 * t.getOrDefault(Items.STONE, 0) + 2 * t.getOrDefault(Items.STONE_BRICKS, 0) + 3 * t.getOrDefault(Items.STONE_BRICK_STAIRS, 0);
	}

	/** What each site still waits for, each crafter's count, job and bag, and where the watched things are. */
	static String state(GameTestHelper helper, Run run) {
		StringBuilder out = new StringBuilder("; sites ");
		for (UUID id : run.sites) {
			BuildSite live = BuildSiteManager.get(run.level).get(id);
			out.append(live == null ? "done" : live.stage() + "/" + live.status() + " missing " + live.missing()).append("; ");
		}
		out.append("crafters ");
		for (UUID id : run.crafters) {
			Villager v = run.villager(id);
			if (v == null) {
				out.append("gone; ");
				continue;
			}
			out.append(v.getVillagerData().getProfession()).append(" made ").append(ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0)).append(", job ")
				.append(ModAttachments.CRAFTER_JOB.get(v)).append(", bag ").append(ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()).append("; ");
		}
		out.append("unbuilt ");
		for (Map.Entry<BlockPos, BlockState> e : run.blocks.entrySet()) {
			for (BlockPos site : run.sites.size() == 1 ? List.of(SITE) : List.of(SITE, SITE_B)) {
				BlockPos p = site.offset(e.getKey());
				if (!helper.getBlockState(p).is(e.getValue().getBlock())) {
					out.append(p.toShortString()).append(" (abs ").append(helper.absolutePos(p).toShortString()).append(") ");
				}
			}
		}
		out.append("; villagers ");
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(32))) {
			out.append(v.getVillagerData().getProfession()).append(" at ").append(helper.relativePos(v.blockPosition()).toShortString()).append(" (abs ").append(v.blockPosition().toShortString()).append(")")
				.append(Builders.isBuilder(v) ? " helping " + Builders.isHelping(v) + " job " + ModAttachments.BUILDER_JOB.get(v) + " bench "
					+ Builders.benchPos(v).map(BlockPos::toShortString).orElse(null) + " bag " + ModAttachments.BUILDER_BAG.getOrCreate(v).stacks() : "")
				.append("; ");
		}
		Map<String, String> where = new LinkedHashMap<>();
		where.put("bench", contents(helper, BENCH_CHEST) + contents(helper, BENCH_CHEST_2));
		where.put("store", contents(helper, STORE_CHEST) + contents(helper, STORE_CHEST_2));
		where.put("carpenter's", contents(helper, CARPENTER_CHEST));
		where.put("mason's", contents(helper, MASON_CHEST));
		where.put("house", contents(helper, HOUSE_CHEST));
		where.put("B's", contents(helper, BENCH_B_CHEST));
		out.append("chests ").append(where).append("; all ").append(tally(helper, run));
		return out.toString();
	}

	static String contents(GameTestHelper helper, BlockPos pos) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c)) {
			return "";
		}
		Map<Item, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && s.getItem() != Items.DIRT) {
				m.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return m.toString();
	}

	/**
	 * Watches the run. Every 20 ticks: no more of a part than {@link Run#caps} allows, no more wood or stone than there
	 * was (nothing doubled), and none of the made parts in any of {@code never} (chests the build never takes from).
	 * Once every site is done: for {@link #SETTLE} ticks no crafter's count grows; then the wood adds up exactly and the
	 * stone at least (nothing lost), and the logs and stone used stay within what the build needed plus one craft.
	 */
	static void watch(GameTestHelper helper, Run run, List<BlockPos> never) {
		Map<Item, Integer> start = tally(helper, run);
		run.initial.clear();
		run.initial.putAll(start);
		run.caps.putIfAbsent(Items.OAK_STAIRS, (int) run.blocks.values().stream().filter(s -> s.is(Blocks.OAK_STAIRS)).count());
		run.caps.putIfAbsent(Items.STONE_BRICK_STAIRS, (int) run.blocks.values().stream().filter(s -> s.is(Blocks.STONE_BRICK_STAIRS)).count());
		int sites = run.sites.size();
		int planksAsked = sites * (int) run.blocks.values().stream().filter(s -> s.is(Blocks.OAK_PLANKS)).count();
		int stairsAsked = sites * (int) run.blocks.values().stream().filter(s -> s.is(Blocks.OAK_STAIRS)).count();
		int bricksAsked = sites * (int) run.blocks.values().stream().filter(s -> s.is(Blocks.STONE_BRICKS)).count();
		int brickStairsAsked = sites * (int) run.blocks.values().stream().filter(s -> s.is(Blocks.STONE_BRICK_STAIRS)).count();
		// wood the build needs, in half-planks, rounded up to whole logs, plus one more log for a craft's leftovers
		int woodNeed = 2 * planksAsked + 3 * stairsAsked;
		int logsAllowed = (woodNeed + 7) / 8 + 1;
		// stone: at most a stone a brick and one and a half a stair, plus one stonecutter's or table's batch (6) more
		int stoneAllowed = bricksAsked + (3 * brickStairsAsked + 1) / 2 + (brickStairsAsked > 0 ? 6 : 0);
		int woodStart = wood(start);
		int stoneLowStart = stoneLow(start);
		int stoneHighStart = stoneHigh(start);
		int logsStart = start.getOrDefault(Items.OAK_LOG, 0);
		int stoneStart = start.getOrDefault(Items.STONE, 0);
		Map<UUID, Integer> craftedAtDone = new HashMap<>();
		AtomicLong doneAt = new AtomicLong(-1);
		helper.runAfterDelay(TIMEOUT - 2, () -> {
			throw new GameTestAssertException("at tick " + run.tick() + " not done: " + (doneAt.get() < 0 ? "the build isn't finished"
				: "built at tick " + doneAt.get()) + state(helper, run));
		});
		helper.onEachTick(() -> {
			if (run.tick() % 20 != 0) {
				return;
			}
			Map<Item, Integer> now = tally(helper, run);
			run.caps.forEach((item, cap) -> {
				int n = now.getOrDefault(item, 0);
				if (n > cap) {
					throw new GameTestAssertException("at tick " + run.tick() + " there are " + n + " " + item + " in the village, the build asked for "
						+ cap + ": a request already filled was made again" + state(helper, run));
				}
			});
			if (wood(now) > woodStart || stoneLow(now) > stoneLowStart) {
				throw new GameTestAssertException("at tick " + run.tick() + " there is more wood or stone than at the start (wood " + wood(now) + "/"
					+ woodStart + " half-planks, stone " + stoneLow(now) + "/" + stoneLowStart + "): something was doubled" + state(helper, run));
			}
			for (BlockPos p : never) {
				for (Item made : List.of(Items.OAK_PLANKS, Items.OAK_STAIRS, Items.STONE_BRICKS, Items.STONE_BRICK_STAIRS)) {
					int n = QaCrafterLoop1111cGameTests.count(helper, p, made);
					if (n > 0) {
						throw new GameTestAssertException("at tick " + run.tick() + " " + n + " " + made + " were brought to the chest in the builder's house at "
							+ p + ", which the build never takes from" + state(helper, run));
					}
				}
			}
			boolean allDone = run.sites.stream().allMatch(id -> BuildSiteManager.get(run.level).get(id) == null);
			if (!allDone) {
				return;
			}
			if (doneAt.get() < 0) {
				doneAt.set(run.tick());
				for (UUID id : run.crafters) {
					Villager v = run.villager(id);
					craftedAtDone.put(id, v == null ? -1 : ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0));
				}
				helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"[qaLoop1111c] built at tick " + run.tick() + ", crafted " + craftedAtDone.values() + ", " + now));
				return;
			}
			for (UUID id : run.crafters) {
				Villager v = run.villager(id);
				int crafted = v == null ? -1 : ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0);
				if (crafted != craftedAtDone.get(id)) {
					throw new GameTestAssertException("at tick " + run.tick() + ", " + (run.tick() - doneAt.get()) + " ticks after the build was done, "
						+ (v == null ? "a crafter" : v.getVillagerData().getProfession()) + " has crafted " + crafted + " (was " + craftedAtDone.get(id)
						+ " when done): made again for a filled request" + state(helper, run));
				}
			}
			if (run.tick() - doneAt.get() < SETTLE) {
				return;
			}
			// the end: nothing lost, and no more used than the build needed plus one craft's leftovers
			for (Map.Entry<BlockPos, BlockState> e : run.blocks.entrySet()) {
				for (BlockPos site : run.sites.size() == 1 ? List.of(SITE) : List.of(SITE, SITE_B)) {
					BlockState there = helper.getBlockState(site.offset(e.getKey()));
					if (!there.is(e.getValue().getBlock())) {
						throw new GameTestAssertException("the build is done but " + site.offset(e.getKey()) + " holds " + there + ", not "
							+ e.getValue().getBlock() + state(helper, run));
					}
				}
			}
			if (wood(now) != woodStart) {
				throw new GameTestAssertException("the wood adds up to " + wood(now) + " half-planks, not " + woodStart + ": some was lost" + state(helper, run));
			}
			if (stoneHigh(now) < stoneHighStart && stoneLow(now) < stoneLowStart - brickStairsAsked) {
				throw new GameTestAssertException("the stone adds up to " + stoneLow(now) + "-" + stoneHigh(now) / 2.0 + ", not " + stoneLowStart + ": some was lost"
					+ state(helper, run));
			}
			int logsUsed = logsStart - now.getOrDefault(Items.OAK_LOG, 0);
			if (logsUsed > logsAllowed) {
				throw new GameTestAssertException(logsUsed + " oak logs were sawn, the build needed " + (logsAllowed - 1) + ": made more than asked"
					+ state(helper, run));
			}
			int stoneUsed = stoneStart - now.getOrDefault(Items.STONE, 0);
			if (stoneUsed > stoneAllowed) {
				throw new GameTestAssertException(stoneUsed + " stone were cut, the build needed at most " + (stoneAllowed - 6) + ": made more than asked"
					+ state(helper, run));
			}
			for (Map.Entry<String, AtomicBoolean> e : run.mustHappen.entrySet()) {
				if (!e.getValue().get()) {
					throw new GameTestAssertException("never happened: " + e.getKey() + ", so the test showed nothing" + state(helper, run));
				}
			}
			helper.succeed();
		});
	}

	static int count(GameTestHelper helper, BlockPos pos, Item item) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c ? c.countItem(item) : 0;
	}
}
