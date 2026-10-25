package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-10-25), from the builder's promise (wiki builder.md, "Builders never copy what is inside
 * containers") on Create's blocks that hold an item without being chests, not from the code: a depot, a deployer
 * and a basin, each holding a diamond, are scanned into a blueprint, and a real builder builds
 * that blueprint from a chest holding exactly what its material list asks. The finished build holds no diamond.
 * (A tank's or spout's fluid is B486.)
 */
public class QaCreateItemsHeldCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(18, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	/** Each block two apart, so none hands its item on to the next. */
	private static final String[] BLOCKS = {"create:depot", "create:deployer", "create:basin"};

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_create_items_held"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_create_items_held")
	public void aScannedDepotDeployerAndBasinHoldingADiamondAreBuiltEmpty(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < BLOCKS.length; i++) {
			BlockPos at = SRC.east(2 * i);
			helper.setBlock(at, block(BLOCKS[i]));
			long in = fill(level, helper.absolutePos(at));
			helper.assertTrue(in == 1, BLOCKS[i] + " took " + in + " diamonds to be scanned, not one");
		}
		for (int i = 0; i < BLOCKS.length; i++) {
			long held = diamonds(level, helper.absolutePos(SRC.east(2 * i)));
			helper.assertTrue(held == 1, BLOCKS[i] + " holds " + held + " diamonds when scanned");
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "qa_create_items_held_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(2 * BLOCKS.length - 1, 1, 1), true, Blocks.STRUCTURE_VOID);
		for (int dx = 0; dx < 2 * BLOCKS.length - 1; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.AIR);
		}

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		Map<Item, Integer> asked = plan.materials();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<String> free = new ArrayList<>();
			for (int i = 0; i < BLOCKS.length; i++) {
				BlockPos at = helper.absolutePos(ORIGIN.east(2 * i));
				helper.assertTrue(level.getBlockState(at).is(block(BLOCKS[i])), "no " + BLOCKS[i] + " built: " + level.getBlockState(at));
				long diamonds = diamonds(level, at);
				if (diamonds > 0 && !asked.containsKey(Items.DIAMOND)) {
					free.add(BLOCKS[i] + " holding " + diamonds + " diamond");
				}
			}
			helper.assertTrue(free.isEmpty(), "the builder gave away for the material list " + list + ": " + free);
		});
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	/** Puts a diamond into the block at {@code pos} through its item storage; how many it took. */
	private static long fill(ServerLevel level, BlockPos pos) {
		Storage<ItemVariant> storage = storage(level, pos);
		if (storage == null) {
			throw new GameTestAssertException("no item storage at " + pos + ": " + level.getBlockState(pos));
		}
		try (Transaction tx = Transaction.openOuter()) {
			long in = storage.insert(ItemVariant.of(Items.DIAMOND), 1, tx);
			tx.commit();
			return in;
		}
	}

	private static Storage<ItemVariant> storage(ServerLevel level, BlockPos pos) {
		Storage<ItemVariant> up = ItemStorage.SIDED.find(level, pos, Direction.UP);
		return up != null ? up : ItemStorage.SIDED.find(level, pos, null);
	}

	/** How many diamonds the block at {@code pos} holds, read through its item storage. */
	private static long diamonds(ServerLevel level, BlockPos pos) {
		Storage<ItemVariant> storage = storage(level, pos);
		long total = 0;
		if (storage != null) {
			for (var view : storage.nonEmptyViews()) {
				if (view.getResource().isOf(Items.DIAMOND)) {
					total += view.getAmount();
				}
			}
		}
		return total;
	}
}
