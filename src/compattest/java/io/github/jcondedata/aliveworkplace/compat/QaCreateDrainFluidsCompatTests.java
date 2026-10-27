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
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane E, 2026-10-25), from the builder's promise (wiki builder.md, "Builders never copy what is inside
 * containers"; B429: what a block holds is never copied, so a scanned lava cauldron is built as an empty cauldron) on
 * Create's blocks, not from the code. Create keeps a fluid in its block's data, not its state: an item drain (B486's kin)
 * that drained a lava bucket, so its tank holds a bucket of lava, is scanned into a blueprint, and a real builder builds that blueprint from a
 * chest holding exactly what its material list asks. Whatever fluid the finished build holds must have been paid for:
 * lava only if the list asked for a lava bucket.
 */
public class QaCreateDrainFluidsCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(18, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	/** Each block two apart, so two tanks never join and the spout never pours into the basin. */
	private static final String[] BLOCKS = {"create:item_drain"};

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_create_drain_fluids"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_create_drain_fluids")
	public void aScannedItemDrainOfLavaIsBuiltWithNoLavaForFree(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < BLOCKS.length; i++) {
			BlockPos at = SRC.east(2 * i);
			helper.setBlock(at, block(BLOCKS[i]));
			net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> items = null;
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
				if (items == null) {
					items = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, helper.absolutePos(at), side);
				}
			}
			helper.assertTrue(items != null, "setup: no item storage on any side of " + BLOCKS[i]);
			try (Transaction tx = Transaction.openOuter()) {
				long in = items.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(net.minecraft.world.item.Items.LAVA_BUCKET), 1, tx);
				tx.commit();
				helper.assertTrue(in == 1, "setup: " + BLOCKS[i] + " took no lava bucket");
			}
		}
		helper.runAfterDelay(100, () -> scanAndBuild(helper, level));
	}

	private static void scanAndBuild(GameTestHelper helper, ServerLevel level) {
		for (int i = 0; i < BLOCKS.length; i++) {
			long held = lava(level, helper.absolutePos(SRC.east(2 * i)));
			helper.assertTrue(held == FluidConstants.BUCKET, "setup: " + BLOCKS[i] + " holds " + held + " droplets of lava after draining a bucket");
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "qa_create_drain_fluids_" + Long.toHexString(level.getGameTime()));
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
				long lava = lava(level, at);
				if (lava > 0 && !asked.containsKey(net.minecraft.world.item.Items.LAVA_BUCKET)) {
					free.add(BLOCKS[i] + " holding " + (lava * 1000 / FluidConstants.BUCKET) + " mB of lava");
				}
			}
			helper.assertTrue(free.isEmpty(), "the builder gave away for the material list " + list + ": " + free);
		});
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	/** Pours a bucket of lava into the block at {@code pos} through its fluid storage; how many droplets it took. */
	private static long fill(ServerLevel level, BlockPos pos) {
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, net.minecraft.core.Direction.UP);
		if (storage == null) {
			throw new GameTestAssertException("no fluid storage at " + pos + ": " + level.getBlockState(pos));
		}
		try (Transaction tx = Transaction.openOuter()) {
			long in = storage.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET, tx);
			tx.commit();
			return in;
		}
	}

	/** The block's fluid storage from whichever side has one. */
	private static Storage<FluidVariant> storage(ServerLevel level, BlockPos pos) {
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
			Storage<FluidVariant> found = FluidStorage.SIDED.find(level, pos, side);
			if (found != null) {
				return found;
			}
		}
		return FluidStorage.SIDED.find(level, pos, null);
	}

	/** How much lava the block at {@code pos} holds, read through its fluid storage. */
	private static long lava(ServerLevel level, BlockPos pos) {
		Storage<FluidVariant> storage = storage(level, pos);
		long total = 0;
		if (storage != null) {
			for (var view : storage.nonEmptyViews()) {
				if (view.getResource().isOf(Fluids.LAVA)) {
					total += view.getAmount();
				}
			}
		}
		return total;
	}
}
