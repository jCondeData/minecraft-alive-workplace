package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, 2026-11-10): the Mover (45.3) moving a building with Create's item-holding blocks in it. 45.3's Done when
 * and moving-buildings.md: "nothing dropped, nothing lost, containers packed with their contents kept apart". Each test
 * puts one kind of Create block holding items in a small house the hall knows, has a hired Mover move it a quarter
 * turned to a new spot, and counts every one of those items in the world afterwards: in the block at its new spot, and
 * lying loose round the test. The vault and the depot (whose block entities the owner's port makes {@code Clearable})
 * were the control. The Mechanical Crafter and the Weighted Ejector hold their items in Create's own inventories, which
 * Create spills as the block comes down: B1127, doubled before 45.5 (89a20402c) took what a block spills back. A belt is
 * in QaCreateMoverContents1110eBugCompatTests (B1128, on tests/create-mover-1110e: it fails on main).
 */
public class QaCreateMoverContents1110eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos MIN = new BlockPos(2, 2, 2);
	static final BlockPos MAX = new BlockPos(8, 6, 8);
	static final BlockPos TO = new BlockPos(16, 2, 16);
	static final BlockPos HALL = new BlockPos(15, 2, 2);
	/** Inside the house, on its floor's top. */
	static final BlockPos SPOT = new BlockPos(4, 3, 4);
	static final int MAX_STEPS = 5000;

	record Setup(MovePlan before, BlockPos hall, BlockPos from, BlockPos to, Villager mover, BlueprintData.Placement placement,
		ResourceLocation id) {
	}

	static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	/** The item storage of the block at {@code abs} that takes {@code item} (any side), and puts {@code count} in. */
	static Storage<ItemVariant> fill(ServerLevel level, BlockPos abs, Item item, int count) {
		List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
		sides.add(0, null);
		for (Direction side : sides) {
			Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, abs, side);
			if (storage == null) {
				continue;
			}
			try (Transaction tx = Transaction.openOuter()) {
				long in = storage.insert(ItemVariant.of(item), count, tx);
				if (in == count) {
					tx.commit();
					return storage;
				}
			}
		}
		// The Weighted Ejector takes nothing through its storage while it stands idle: its held stack (Create's depot data)
		// is set as a player's click would leave it.
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(abs);
		if (be != null) {
			net.minecraft.nbt.CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
			net.minecraft.nbt.CompoundTag held = new net.minecraft.nbt.CompoundTag();
			held.put("Item", new net.minecraft.world.item.ItemStack(item, count).save(level.registryAccess()));
			data.put("HeldItem", held);
			be.loadWithComponents(data, level.registryAccess());
			be.setChanged();
			if (held(level, abs, item) == count) {
				return Storage.empty();
			}
		}
		return null;
	}

	/** How many {@code item} the block at {@code abs} holds (by any side; the most seen). */
	static long held(ServerLevel level, BlockPos abs, Item item) {
		long most = 0;
		List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
		sides.add(null);
		for (Direction side : sides) {
			Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, abs, side);
			if (storage == null) {
				continue;
			}
			long n = 0;
			for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
				if (view.getResource().isOf(item)) {
					n += view.getAmount();
				}
			}
			most = Math.max(most, n);
		}
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(abs);
		if (be != null) {
			net.minecraft.nbt.CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
			if (data.getCompound("HeldItem").contains("Item")) {
				net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.parseOptional(level.registryAccess(),
					data.getCompound("HeldItem").getCompound("Item"));
				if (stack.is(item)) {
					most = Math.max(most, stack.getCount());
				}
			}
		}
		return most;
	}

	/** Every loose item round the test, by id and count. */
	static Map<String, Integer> loose(GameTestHelper helper) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(34))) {
			out.merge(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString(), e.getItem().getCount(), Integer::sum);
		}
		return out;
	}

	/** The house (cobblestone floor, plank walls, spruce roof) with {@code inside} placed at SPOT and filled; the move confirmed. */
	static Setup setup(GameTestHelper helper, String name, BlockState inside, Item item, int count) {
		if (inside != null) {
			PartnerShowsCompatTests.clearLeftovers(helper);
		}
		ServerLevel level = helper.getLevel();
		for (int x = MIN.getX(); x <= MAX.getX(); x++) {
			for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
				for (int y = MIN.getY(); y <= MAX.getY(); y++) {
					boolean edge = x == MIN.getX() || x == MAX.getX() || z == MIN.getZ() || z == MAX.getZ();
					BlockState state = y == MIN.getY() ? Blocks.COBBLESTONE.defaultBlockState()
						: y == MAX.getY() ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: edge ? Blocks.OAK_PLANKS.defaultBlockState() : null;
					if ((state != null || inside != null) && !new BlockPos(x, y, z).equals(SPOT)) {
						set(level, helper.absolutePos(new BlockPos(x, y, z)), state != null ? state : Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		if (inside != null) {
			if (!level.getBlockState(helper.absolutePos(SPOT)).is(inside.getBlock())) {
				set(level, helper.absolutePos(SPOT), inside);
			}
			helper.assertTrue(fill(level, helper.absolutePos(SPOT), item, count) != null,
				"couldn't put " + count + " " + item + " in " + level.getBlockState(helper.absolutePos(SPOT)));
			helper.assertTrue(held(level, helper.absolutePos(SPOT), item) == count, "the block holds " + held(level, helper.absolutePos(SPOT), item));
		}

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "qa_mover_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		BuildSiteManager.Finished finished = sites.finishedAt(level, id, helper.absolutePos(MIN)).orElseThrow();
		MovePlan before = MovePlan.capture(level, finished).orElseThrow();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos from = helper.absolutePos(MIN);
		BlockPos to = helper.absolutePos(TO);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, from, id, to, Rotation.CLOCKWISE_90, null));

		BlockPos at = new BlockPos(12, 2, 12);
		helper.setBlock(at.north(), ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, at);
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
			net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(at.north())));
		return new Setup(before, hall, from, to, mover, placement, id);
	}

	/** Lets the Mover work until {@code stop} or the end; the site. */
	static MoveSite work(ServerLevel level, Setup s, java.util.function.Predicate<MoveSite> stop) {
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		for (int i = 0; i < MAX_STEPS && site.stage() != MoveSite.Stage.DONE && !stop.test(site); i++) {
			MoverWork.work(level, s.mover(), site);
		}
		return site;
	}

	/** Everything this test left: the move finished or dropped, the hall's records forgotten, the loose items gone. */
	static void tidy(GameTestHelper helper, Setup s) {
		ServerLevel level = helper.getLevel();
		MoveSites.get(level).cancel(level, s.from());
		MoveSites.get(level).all().stream().filter(x -> x.from().equals(s.from())).forEach(x -> {
			for (int i = 0; i < MAX_STEPS && x.stage() != MoveSite.Stage.DONE; i++) {
				MoveSites.get(level).step(level, x);
			}
		});
		PendingMoves.get(level).remove(s.from());
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.forgetFinished(s.placement());
		sites.finishedIn(level).stream().filter(f -> f.structure().equals(s.id())).forEach(f -> sites.forgetFinished(f.placement()));
		PartnerShowsCompatTests.clearLeftovers(helper);
	}

	/**
	 * Moves the house with {@code inside} holding {@code count} {@code item}, and checks every one of them is in the block
	 * at its new spot and none lies loose, after the take-down and at the end.
	 */
	static void movesOnce(GameTestHelper helper, String name, BlockState inside, Item item, int count) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(SPOT), inside); // a few ticks before it's filled: Create's block entities start on their first tick
		helper.runAfterDelay(5, () -> {
		Setup s = setup(helper, name, inside, item, count);
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			MoveSite.Stage downStage = site.stage();
			Map<String, Integer> afterDown = loose(helper);
			work(level, s, x -> false);
			MoveSite.Stage end = site.stage();
			int skipped = site.skipped();
			BlockPos newSpot = s.before().at(SPOT.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
			BlockState there = level.getBlockState(newSpot);
			long inBlock = held(level, newSpot, item);
			Map<String, Integer> afterAll = loose(helper);
			String key = BuiltInRegistries.ITEM.getKey(item).toString();
			int looseItem = afterAll.getOrDefault(key, 0);
			tidy(helper, s);
			helper.assertTrue(downStage == MoveSite.Stage.FOUNDATION, "the house didn't come down: " + downStage);
			helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
			helper.assertTrue(there.is(inside.getBlock()), "the new spot " + newSpot + " has " + there);
			helper.assertTrue(inBlock + looseItem == count, inside.getBlock() + " held " + count + " " + key + "; after the move "
				+ inBlock + " in it at the new spot and " + looseItem + " loose (" + afterAll + "): " + (inBlock + looseItem > count ? "doubled" : "lost"));
			helper.assertTrue(afterDown.isEmpty(), inside.getBlock() + ": taking the house down dropped " + afterDown
				+ " (45.3: nothing dropped; the block is packed in the Mover's crates with its items)");
			helper.assertTrue(inBlock == count && afterAll.isEmpty(), inside.getBlock() + " at the new spot holds " + inBlock + " of " + count
				+ " " + key + ", loose: " + afterAll);
			helper.succeed();
		});
		});
	}

	/** The control: a one-block Item Vault with 48 emeralds (its block entity is Clearable in the owner's port). */
	//$ gametest_batch AREA '"qa_e1110_mover_vault"'
	@GameTest(template = AREA, batch = "qa_e1110_mover_vault")
	public void aMovedItemVaultKeepsItsItemsOnce(GameTestHelper helper) {
		movesOnce(helper, "vault", block("create:item_vault").defaultBlockState(), Items.EMERALD, 48);
	}

	/** The control: a Depot with 16 diamonds on it. */
	//$ gametest_batch AREA '"qa_e1110_mover_depot"'
	@GameTest(template = AREA, batch = "qa_e1110_mover_depot")
	public void aMovedDepotKeepsItsItemsOnce(GameTestHelper helper) {
		movesOnce(helper, "depot", block("create:depot").defaultBlockState(), Items.DIAMOND, 16);
	}

	/** A Mechanical Crafter holding an iron ingot in its slot. */
	//$ gametest_batch AREA '"qa_e1110_mover_crafter"'
	@GameTest(template = AREA, batch = "qa_e1110_mover_crafter")
	public void aMovedMechanicalCrafterKeepsItsItemOnce(GameTestHelper helper) {
		movesOnce(helper, "crafter", block("create:mechanical_crafter").defaultBlockState(), Items.IRON_INGOT, 1);
	}

	/** A Weighted Ejector with 16 gold ingots on it (unpowered: it holds them). */
	//$ gametest_batch AREA '"qa_e1110_mover_ejector"'
	@GameTest(template = AREA, batch = "qa_e1110_mover_ejector")
	public void aMovedWeightedEjectorKeepsItsItemsOnce(GameTestHelper helper) {
		movesOnce(helper, "ejector", block("create:weighted_ejector").defaultBlockState(), Items.GOLD_INGOT, 16);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static BlockState with(BlockState state, String name, String value) {
		net.minecraft.world.level.block.state.properties.Property p = state.getBlock().getStateDefinition().getProperty(name);
		return state.setValue(p, (Comparable) p.getValue(value).orElseThrow());
	}

	/** Puts one {@code item} on the belt segment at {@code abs} from above; whether the belt took it. */
	static boolean onto(ServerLevel level, BlockPos abs, Item item) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, abs, Direction.UP);
		if (storage == null) {
			return false;
		}
		try (Transaction tx = Transaction.openOuter()) {
			boolean took = storage.insert(ItemVariant.of(item), 1, tx) == 1;
			tx.commit();
			return took;
		}
	}

	/** The house built round whatever already stands inside it (nothing placed at SPOT), recorded and its move confirmed. */
	static Setup setupAround(GameTestHelper helper, String name) {
		return setup(helper, name, null, null, 0);
	}
}
