package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-11, qa-lane-b-1111-0937) for the owner's B1171 on his live Cobbleverse server: "the Carpenter and the Mason
 * get stuck in a loop making items for the builder; the Carpenter carries them to the builder's house, the Porter never
 * picks them up, the builder never gets them, and they keep making and delivering again". The vanilla-chest village is
 * covered by QaCrafterLoop1111cGameTests; the lead left open is modded storage by the Blueprint Table. So the same village
 * (a builder at his Blueprint Table, his blueprint needing 8 oak planks, 4 oak stairs, 4 oak slabs, 4 stone bricks and 4
 * stone brick stairs; a Storehouse with a Porter; a Carpenter and a Mason with makings), with the pack's storage by the table:
 * Sophisticated Storage chests, barrels (one with a stack upgrade), a vanilla chest beside a Sophisticated one, Tom's
 * inventory connector, and a Storehouse whose chests are Sophisticated; vanilla chests as the control. Each test watches every watched item in the area (chests of any kind, bags,
 * inventories, the ground, built): no part made beyond the build's need at any time, nothing doubled or lost, the build
 * finished, and nothing more crafted for {@link #SETTLE} ticks after. Each test has its own batch.
 */
public class QaCraftersModdedStore1111bCompatTests implements FabricGameTest {
	static final String HUGE = "aliveworkplace_compat:huge_area";
	static final int SETTLE = 1200;
	static final int TIMEOUT = 7000;

	static final BlockPos BENCH = new BlockPos(4, 2, 4);
	static final BlockPos BENCH_CHEST = new BlockPos(2, 2, 4);
	static final BlockPos BENCH_CHEST_2 = new BlockPos(2, 2, 7);
	static final BlockPos SITE = new BlockPos(4, 2, 13);
	static final BlockPos STOREHOUSE = new BlockPos(24, 2, 4);
	static final BlockPos STORE_CHEST = new BlockPos(24, 2, 6);
	static final BlockPos STORE_CHEST_2 = new BlockPos(27, 2, 6);
	static final BlockPos CARPENTER_BENCH = new BlockPos(24, 2, 24);
	static final BlockPos CARPENTER_CHEST = new BlockPos(26, 2, 24);
	static final BlockPos CUTTER = new BlockPos(14, 2, 26);
	static final BlockPos MASON_CHEST = new BlockPos(12, 2, 26);
	static final BlockPos BED_HEAD = new BlockPos(26, 2, 13);
	static final BlockPos HOUSE_CHEST = new BlockPos(27, 2, 15);

	static final List<Item> WATCHED = List.of(Items.OAK_LOG, Items.OAK_PLANKS, Items.OAK_STAIRS, Items.OAK_SLAB, Items.STONE, Items.STONE_BRICKS,
		Items.STONE_BRICK_STAIRS);
	static final List<Item> MADE = List.of(Items.OAK_PLANKS, Items.OAK_STAIRS, Items.OAK_SLAB, Items.STONE_BRICKS, Items.STONE_BRICK_STAIRS);

	// ------------------------------------------------------------------------------------------------------ tests

	/**
	 * (a) Two Sophisticated Storage chests by the Blueprint Table (the builder's only supply chests), and the builder
	 * living in a house away from his table with a Sophisticated chest there. Every part is made once, lands where he
	 * takes from (never in his house's chest), comes off his list, and is built.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaCraftModStoreSophChest"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaCraftModStoreSophChest")
	public void sophisticatedChestsByTheTableGetEachPartOnce(GameTestHelper helper) {
		Run run = village(helper, "sophchest", true);
		helper.setBlock(BENCH_CHEST, soph("chest"));
		helper.setBlock(BENCH_CHEST_2, soph("chest"));
		makings(helper);
		ServerLevel level = helper.getLevel();
		BlockPos foot = BED_HEAD.south();
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(BED_HEAD, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos head = helper.absolutePos(BED_HEAD);
		ensurePoi(level, head);
		level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 1);
		run.villager(run.builders.get(0)).getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		helper.setBlock(HOUSE_CHEST, soph("chest"));
		put(helper, HOUSE_CHEST, new ItemStack(Items.BREAD, 3));
		suppliesInclude(helper, BENCH_CHEST, BENCH_CHEST_2);
		watch(helper, run, List.of(HOUSE_CHEST));
	}

	/**
	 * The control: the same village with two vanilla chests by the table (QaCrafterLoop1111cGameTests' scenario 1, here
	 * with the pack's mods loaded).
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaCraftModStoreVanilla"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaCraftModStoreVanilla")
	public void vanillaChestsByTheTableGetEachPartOnce(GameTestHelper helper) {
		Run run = village(helper, "vanilla", true);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		helper.setBlock(BENCH_CHEST_2, Blocks.CHEST);
		makings(helper);
		suppliesInclude(helper, BENCH_CHEST, BENCH_CHEST_2);
		watch(helper, run, List.of());
	}

	/** (b) Two Sophisticated barrels by the table: every part made once, reaches them, is built. */
	//$ gametest_ticks_batch HUGE '7000' '"qaCraftModStoreSophBarrel"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaCraftModStoreSophBarrel")
	public void sophisticatedBarrelsByTheTableGetEachPartOnce(GameTestHelper helper) {
		Run run = village(helper, "sophbarrel", true);
		helper.setBlock(BENCH_CHEST, soph("barrel"));
		helper.setBlock(BENCH_CHEST_2, soph("barrel"));
		makings(helper);
		suppliesInclude(helper, BENCH_CHEST, BENCH_CHEST_2);
		watch(helper, run, List.of());
	}

	/**
	 * (b) with an upgrade: one Sophisticated barrel by the table, a tier-2 stack upgrade put in by the player (shift-use on
	 * it, as in game), its slots holding more than a stack. Every part made once, reaches it, is built.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaCraftModStoreUpgraded"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaCraftModStoreUpgraded")
	public void aStackUpgradedSophisticatedBarrelByTheTableGetsEachPartOnce(GameTestHelper helper) {
		Run run = village(helper, "upgraded", true);
		helper.setBlock(BENCH_CHEST, soph("barrel"));
		makings(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(BENCH_CHEST);
		ServerPlayer player = run.player;
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
		ItemStack upgrade = new ItemStack(SophisticatedStorageCompatTests.item("stack_upgrade_tier_2"));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, upgrade);
		player.setShiftKeyDown(true);
		Direction front = SophisticatedStorageCompatTests.front(level.getBlockState(at));
		player.gameMode.useItemOn(player, level, upgrade, net.minecraft.world.InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at).relative(front, 0.5), front, at, false));
		player.setShiftKeyDown(false);
		player.teleportTo(at.getX() + 0.5, at.getY() + 40, at.getZ() + 0.5);
		var room = SupplyContainers.room(level, List.of(at));
		helper.assertTrue(room.bigSlots(), "setup: the stack upgrade didn't go into the barrel: " + room);
		suppliesInclude(helper, BENCH_CHEST);
		watch(helper, run, List.of());
	}

	/**
	 * (c) A vanilla chest (full of dirt from his last clearing) and a Sophisticated chest by the table: the parts can only
	 * go into the Sophisticated one (or the Storehouse), and are made once and built.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaCraftModStoreMixed"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaCraftModStoreMixed")
	public void aFullVanillaChestBesideASophisticatedChestGetsEachPartOnce(GameTestHelper helper) {
		Run run = village(helper, "mixed", true);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Container c = (Container) helper.getBlockEntity(BENCH_CHEST);
		for (int i = 0; i < c.getContainerSize(); i++) {
			c.setItem(i, new ItemStack(Items.DIRT, 64));
		}
		helper.setBlock(BENCH_CHEST_2, soph("chest"));
		makings(helper);
		suppliesInclude(helper, BENCH_CHEST, BENCH_CHEST_2);
		watch(helper, run, List.of());
	}

	/**
	 * (d) Tom's Simple Storage by the table: an inventory connector with a vanilla chest on it, the switch for modded
	 * storage networks on. Every part made once, reaches the network, is built.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaCraftModStoreToms"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaCraftModStoreToms")
	public void tomsConnectorByTheTableGetsEachPartOnce(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = true;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
		Run run = village(helper, "toms", true);
		helper.setBlock(BENCH_CHEST, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(BENCH_CHEST.west(), Blocks.CHEST);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(BENCH_CHEST);
		level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), Block.UPDATE_ALL);
		makings(helper);
		helper.runAfterDelay(1, () -> {
			List<BlockPos> supplies = SupplyContainers.find(level, helper.absolutePos(BENCH), null);
			helper.assertTrue(supplies.contains(helper.absolutePos(BENCH_CHEST)) || supplies.contains(helper.absolutePos(BENCH_CHEST.west())),
				"setup: the builder's table lists neither Tom's connector nor its chest: " + supplies);
		});
		watch(helper, run, List.of());
	}

	/**
	 * (e) A builder with no chest by his table, whose village's Storehouse chests are two Sophisticated chests (the oak
	 * logs and stone in one of them) with a Porter: the parts go through the Storehouse, are made once and built.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaCraftModStoreStorehouse"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaCraftModStoreStorehouse")
	public void aSophisticatedStorehouseWithAPorterFeedsAChestlessBuilderOnce(GameTestHelper helper) {
		Run run = village(helper, "storehouse", true);
		helper.setBlock(STORE_CHEST, soph("chest"));
		helper.setBlock(STORE_CHEST_2, soph("chest"));
		helper.setBlock(CARPENTER_CHEST, Blocks.CHEST);
		helper.setBlock(MASON_CHEST, Blocks.CHEST);
		put(helper, STORE_CHEST, new ItemStack(Items.OAK_LOG, 6), new ItemStack(Items.STONE, 12));
		put(helper, CARPENTER_CHEST, new ItemStack(Items.OAK_LOG, 8));
		put(helper, MASON_CHEST, new ItemStack(Items.STONE, 16));
		List<BlockPos> store = SupplyContainers.find(helper.getLevel(), helper.absolutePos(STOREHOUSE), null);
		helper.assertTrue(store.contains(helper.absolutePos(STORE_CHEST)) && store.contains(helper.absolutePos(STORE_CHEST_2)),
			"setup: the Storehouse doesn't list its Sophisticated chests: " + store.stream().map(helper::relativePos).toList());
		watch(helper, run, List.of());
	}

	/**
	 * (a) saved and loaded: Sophisticated chests by the table; every villager is saved and loaded the first time a crafter
	 * is at his bench making, and again the first time one is carrying the parts. Made once, delivered, built.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaCraftModStoreReload"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaCraftModStoreReload")
	public void sophisticatedChestsReloadedMidCraftAndMidDeliveryGetEachPartOnce(GameTestHelper helper) {
		Run run = village(helper, "reload", true);
		helper.setBlock(BENCH_CHEST, soph("chest"));
		helper.setBlock(BENCH_CHEST_2, soph("chest"));
		makings(helper);
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

		void reloadAll() {
			List<UUID> all = new ArrayList<>(builders);
			all.addAll(crafters);
			all.addAll(others);
			for (UUID id : all) {
				Villager v = villager(id);
				if (v != null) {
					CompoundTag tag = new CompoundTag();
					v.saveWithoutId(tag);
					v.discard();
					Villager copy = EntityType.VILLAGER.create(level);
					if (copy == null) {
						throw new GameTestAssertException("no villager made");
					}
					copy.load(tag);
					level.addFreshEntity(copy);
					Village.forget(copy);
				}
			}
		}
	}

	static Block soph(String id) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("sophisticatedstorage", id))
			.orElseThrow(() -> new GameTestAssertException("no block sophisticatedstorage:" + id));
	}

	static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	/** 8 oak planks, 4 oak stairs, 4 oak slabs, 4 stone bricks, 4 stone brick stairs. */
	static Map<BlockPos, BlockState> mixedRow() {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (int x = 0; x < 8; x++) {
			blocks.put(new BlockPos(x, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
		}
		for (int x = 0; x < 4; x++) {
			blocks.put(new BlockPos(x, 0, 1), Blocks.OAK_STAIRS.defaultBlockState());
			blocks.put(new BlockPos(x, 0, 2), Blocks.STONE_BRICKS.defaultBlockState());
			blocks.put(new BlockPos(x, 0, 3), Blocks.STONE_BRICK_STAIRS.defaultBlockState());
			blocks.put(new BlockPos(x, 0, 4), Blocks.OAK_SLAB.defaultBlockState());
		}
		return blocks;
	}

	/** The woodwork: 8 oak planks, 4 oak stairs, 4 oak slabs. */
	static Map<BlockPos, BlockState> woodRow() {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (int x = 0; x < 8; x++) {
			blocks.put(new BlockPos(x, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
		}
		for (int x = 0; x < 4; x++) {
			blocks.put(new BlockPos(x, 0, 1), Blocks.OAK_STAIRS.defaultBlockState());
			blocks.put(new BlockPos(x, 0, 2), Blocks.OAK_SLAB.defaultBlockState());
		}
		return blocks;
	}

	static ResourceLocation blueprint(GameTestHelper helper, String name, Map<BlockPos, BlockState> blocks) {
		ServerLevel level = helper.getLevel();
		blocks.forEach((p, s) -> level.setBlock(helper.absolutePos(SITE.offset(p)), s, 2 | 16));
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "qa1111b_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SITE), new Vec3i(8, 1, 5), false, Blocks.STRUCTURE_VOID);
		blocks.keySet().forEach(p -> level.setBlock(helper.absolutePos(SITE.offset(p)), Blocks.AIR.defaultBlockState(), 2 | 16));
		return id;
	}

	/**
	 * The village as a player sets it up (as QaCrafterLoop1111cGameTests): the village on, his Storehouse placed by him
	 * with its Porter, his builder at a Blueprint Table given the placed blueprint, the Carpenter and the Mason at their
	 * workshops. Chests are each test's own.
	 */
	static Run village(GameTestHelper helper, String name, boolean stone) {
		ServerLevel level = helper.getLevel();
		AABB around = helper.getBounds().inflate(48);
		for (Entity e : level.getEntitiesOfClass(Entity.class, around, e -> !(e instanceof Player))) {
			e.discard();
		}
		io.github.jcondedata.aliveworkplace.city.PlayerBuilt.get(level).forget(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			BlockPos.containing(around.minX, around.minY, around.minZ), BlockPos.containing(around.maxX, around.maxY, around.maxZ)));
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : List.copyOf(manager.all())) {
			if (site.bench() != null && around.inflate(Builders.MAX_SITE_DISTANCE).contains(net.minecraft.world.phys.Vec3.atCenterOf(site.bench()))) {
				manager.remove(site.id());
			}
		}
		AABB box = helper.getBounds();
		for (BlockPos storehouse : level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI),
			p -> !box.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), BlockPos.containing(box.getCenter()), 48 + 40,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(storehouse, false);
		}
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		// the compat world's clock stands still: in work hours (2000-9000), or the Mason (on the vanilla schedule) idles all test
		helper.setDayTime(3000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		PartnerShowsCompatTests.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		Map<BlockPos, BlockState> blocks = stone ? mixedRow() : woodRow();
		ResourceLocation id = blueprint(helper, name, blocks);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Run run = new Run(level, player, id, blocks);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		level.getBlockState(store).getBlock().setPlacedBy(level, store, level.getBlockState(store), player, new ItemStack(ModBlocks.STOREHOUSE));
		ensurePoi(level, store);
		Villager porter = helper.spawn(EntityType.VILLAGER, STOREHOUSE.north());
		Porters.employ(level, porter, store);
		run.others.add(porter.getUUID());
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(BENCH));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.north());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ItemStack paper = BlueprintItem.create(id, null);
		paper.set(ModComponents.BLUEPRINT, BlueprintItem.data(paper).orElseThrow().withPlacement(Optional.of(
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SITE), Rotation.NONE, Mirror.NONE))));
		InteractionResult given = Builders.assign(player, builder, paper);
		BuildSite started = Builders.activeSite(level, builder);
		if (started == null) {
			throw new GameTestAssertException("setup: the builder didn't take the blueprint (" + given + ")");
		}
		run.sites.add(started.id());
		PartnerShowsCompatTests.after(helper, () -> {
			if (BuildSiteManager.get(level).get(started.id()) != null) {
				BuildSiteManager.get(level).remove(started.id());
			}
		});
		run.builders.add(builder.getUUID());
		helper.setBlock(CARPENTER_BENCH, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, CARPENTER_BENCH.north());
		Jobs.employ(level, carpenter, helper.absolutePos(CARPENTER_BENCH), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		run.crafters.add(carpenter.getUUID());
		if (stone) {
			helper.setBlock(CUTTER, Blocks.STONECUTTER);
			Villager mason = helper.spawn(EntityType.VILLAGER, CUTTER.north());
			Jobs.employ(level, mason, helper.absolutePos(CUTTER), PoiTypes.MASON, VillagerProfession.MASON);
			run.crafters.add(mason.getUUID());
		}
		return run;
	}

	/** The Storehouse's two vanilla chests with 6 oak logs and 12 stone, 8 logs in the Carpenter's chest, 16 stone in the Mason's. */
	static void makings(GameTestHelper helper) {
		for (BlockPos p : List.of(STORE_CHEST, STORE_CHEST_2, CARPENTER_CHEST, MASON_CHEST)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		put(helper, STORE_CHEST, new ItemStack(Items.OAK_LOG, 6), new ItemStack(Items.STONE, 12));
		put(helper, CARPENTER_CHEST, new ItemStack(Items.OAK_LOG, 8));
		put(helper, MASON_CHEST, new ItemStack(Items.STONE, 16));
	}

	/** Puts the stacks into the store at {@code rel} (any kind: vanilla or modded), as a hopper would. */
	static void put(GameTestHelper helper, BlockPos rel, ItemStack... stacks) {
		for (ItemStack s : stacks) {
			ItemStack rest = SupplyContainers.insert(helper.getLevel(), List.of(helper.absolutePos(rel)), s.copy());
			helper.assertTrue(rest.isEmpty(), "setup: " + rest + " didn't go into " + rel);
		}
	}

	static void suppliesInclude(GameTestHelper helper, BlockPos... rel) {
		List<BlockPos> supplies = SupplyContainers.find(helper.getLevel(), helper.absolutePos(BENCH), null);
		for (BlockPos p : rel) {
			helper.assertTrue(supplies.contains(helper.absolutePos(p)), "setup: the builder's table doesn't list " + p + " as a supply: "
				+ supplies.stream().map(helper::relativePos).toList());
		}
	}

	// ------------------------------------------------------------------------------------------------------ watching

	/** How many of {@code item} the store at absolute {@code pos} holds (any kind of storage). */
	static long held(ServerLevel level, BlockPos pos, Item item) {
		return SupplyContainers.count(level, List.of(pos), item);
	}

	/**
	 * Every watched item in the area: every block entity's store (Tom's blocks left out: they only show the chests on
	 * them, counted themselves), villagers' bags and inventories, the ground, built.
	 */
	static Map<Item, Integer> tally(GameTestHelper helper) {
		Map<Item, Integer> out = new HashMap<>();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(4);
		int floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		for (BlockPos p : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ)) {
			BlockState state = level.getBlockState(p);
			if (state.isAir()) {
				continue;
			}
			if (p.getY() >= floor && WATCHED.contains(state.getBlock().asItem())) {
				out.merge(state.getBlock().asItem(), 1, Integer::sum);
			}
			if (state.hasBlockEntity() && !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("toms_storage")) {
				for (Item item : WATCHED) {
					long n = held(level, p.immutable(), item);
					if (n > 0) {
						out.merge(item, (int) n, Integer::sum);
					}
				}
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box.inflate(16))) {
			for (Item item : WATCHED) {
				int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
				if (n > 0) {
					out.merge(item, n, Integer::sum);
				}
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(16))) {
			if (WATCHED.contains(e.getItem().getItem())) {
				out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
		}
		return out;
	}

	static int wood(Map<Item, Integer> t) {
		return 8 * t.getOrDefault(Items.OAK_LOG, 0) + 2 * t.getOrDefault(Items.OAK_PLANKS, 0) + 3 * t.getOrDefault(Items.OAK_STAIRS, 0) + t.getOrDefault(Items.OAK_SLAB, 0);
	}

	static int stoneLow(Map<Item, Integer> t) {
		return t.getOrDefault(Items.STONE, 0) + t.getOrDefault(Items.STONE_BRICKS, 0) + t.getOrDefault(Items.STONE_BRICK_STAIRS, 0);
	}

	static int stoneHigh(Map<Item, Integer> t) {
		return 2 * t.getOrDefault(Items.STONE, 0) + 2 * t.getOrDefault(Items.STONE_BRICKS, 0) + 3 * t.getOrDefault(Items.STONE_BRICK_STAIRS, 0);
	}

	static String where(GameTestHelper helper, BlockPos rel) {
		Map<Item, Long> m = new LinkedHashMap<>();
		for (Item item : WATCHED) {
			long n = held(helper.getLevel(), helper.absolutePos(rel), item);
			if (n > 0) {
				m.put(item, n);
			}
		}
		return m.toString();
	}

	static String state(GameTestHelper helper, Run run) {
		StringBuilder out = new StringBuilder("; makers ");
		for (Item made : MADE) {
			out.append(BuiltInRegistries.ITEM.getKey(made).getPath()).append('=')
				.append(io.github.jcondedata.aliveworkplace.craft.CraftJobs.maker(run.level, made)).append(' ');
		}
		out.append("; sites ");
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
				.append(ModAttachments.CRAFTER_JOB.get(v)).append(", activity ").append(v.getBrain().getActiveNonCoreActivity().orElse(null))
				.append(", time ").append(run.level.getDayTime() % 24000).append(", bag ").append(ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()).append("; ");
		}
		out.append("unbuilt ");
		for (Map.Entry<BlockPos, BlockState> e : run.blocks.entrySet()) {
			BlockPos p = SITE.offset(e.getKey());
			if (!helper.getBlockState(p).is(e.getValue().getBlock())) {
				out.append(p.toShortString()).append(' ');
			}
		}
		out.append("; villagers ");
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(32))) {
			out.append(v.getVillagerData().getProfession()).append(" at ").append(helper.relativePos(v.blockPosition()).toShortString())
				.append(Builders.isBuilder(v) ? " job " + ModAttachments.BUILDER_JOB.get(v) + " bag " + ModAttachments.BUILDER_BAG.getOrCreate(v).stacks() : "")
				.append("; ");
		}
		Map<String, String> w = new LinkedHashMap<>();
		w.put("bench", where(helper, BENCH_CHEST) + where(helper, BENCH_CHEST_2) + where(helper, BENCH_CHEST.west()));
		w.put("store", where(helper, STORE_CHEST) + where(helper, STORE_CHEST_2));
		w.put("carpenter's", where(helper, CARPENTER_CHEST));
		w.put("mason's", where(helper, MASON_CHEST));
		w.put("house", where(helper, HOUSE_CHEST));
		out.append("chests ").append(w).append("; all ").append(tally(helper));
		return out.toString();
	}

	/**
	 * Every 20 ticks: no more of a part than the build asked for (made once), no more wood or stone than at the start
	 * (nothing doubled), none of the parts in {@code never}. Once built: for {@link #SETTLE} ticks no crafter's count grows;
	 * then every block is in place, the wood adds up exactly (nothing lost), and no more logs and stone were used than
	 * the build needed plus one craft's leftovers.
	 */
	static void watch(GameTestHelper helper, Run run, List<BlockPos> never) {
		Map<Item, Integer> start = tally(helper);
		// a craft makes 4 stairs and 6 slabs: the build's 4 stairs and up to one batch of slabs may ever exist
		run.caps.putIfAbsent(Items.OAK_STAIRS, 4);
		run.caps.putIfAbsent(Items.OAK_SLAB, 6);
		run.caps.putIfAbsent(Items.STONE_BRICK_STAIRS, 4);
		// 8 planks + 4 stairs (+ 4 slabs) = 16 + 12 (+ 4) half-planks: 4 logs, plus one more for a craft's leftovers
		int logsAllowed = (2 * 8 + 3 * 4 + 4 + 7) / 8 + 1;
		int stoneAllowed = 4 + (3 * 4 + 1) / 2 + 6;
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
			Map<Item, Integer> now = tally(helper);
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
				for (Item made : MADE) {
					long n = held(helper.getLevel(), helper.absolutePos(p), made);
					if (n > 0) {
						throw new GameTestAssertException("at tick " + run.tick() + " " + n + " " + made + " were brought to the chest at " + p
							+ ", which the build never takes from" + state(helper, run));
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
					"[qaCraftModStore1111b] built at tick " + run.tick() + ", crafted " + craftedAtDone.values() + ", " + now));
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
			for (Map.Entry<BlockPos, BlockState> e : run.blocks.entrySet()) {
				BlockState there = helper.getBlockState(SITE.offset(e.getKey()));
				if (!there.is(e.getValue().getBlock())) {
					throw new GameTestAssertException("the build is done but " + SITE.offset(e.getKey()) + " holds " + there + ", not "
						+ e.getValue().getBlock() + state(helper, run));
				}
			}
			if (wood(now) != woodStart) {
				throw new GameTestAssertException("the wood adds up to " + wood(now) + " half-planks, not " + woodStart + ": some was lost" + state(helper, run));
			}
			if (stoneHigh(now) < stoneHighStart && stoneLow(now) < stoneLowStart - 4) {
				throw new GameTestAssertException("the stone adds up to " + stoneLow(now) + "-" + stoneHigh(now) / 2.0 + ", not " + stoneLowStart + ": some was lost"
					+ state(helper, run));
			}
			int logsUsed = logsStart - now.getOrDefault(Items.OAK_LOG, 0);
			if (logsUsed > logsAllowed) {
				throw new GameTestAssertException(logsUsed + " oak logs were sawn, the build needed " + (logsAllowed - 1) + ": made more than asked" + state(helper, run));
			}
			int stoneUsed = stoneStart - now.getOrDefault(Items.STONE, 0);
			if (stoneUsed > stoneAllowed) {
				throw new GameTestAssertException(stoneUsed + " stone were cut, the build needed at most " + (stoneAllowed - 6) + ": made more than asked"
					+ state(helper, run));
			}
			int crafted = craftedAtDone.values().stream().mapToInt(Integer::intValue).sum();
			if (crafted <= 0) {
				throw new GameTestAssertException("the build was done but the crafters made nothing: the test showed nothing" + state(helper, run));
			}
			for (Map.Entry<String, AtomicBoolean> e : run.mustHappen.entrySet()) {
				if (!e.getValue().get()) {
					throw new GameTestAssertException("never happened: " + e.getKey() + ", so the test showed nothing" + state(helper, run));
				}
			}
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qaCraftModStore1111b] PASS at tick " + run.tick() + ", built at " + doneAt.get() + ", crafted " + craftedAtDone.values()));
			helper.succeed();
		});
	}
}
