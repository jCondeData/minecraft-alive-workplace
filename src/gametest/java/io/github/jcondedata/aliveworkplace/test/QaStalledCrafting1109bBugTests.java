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
 * QA (2026-11-09) bug tests for B1111 and B1112 (failing until fixed): a builder whose table stands by a Storehouse with
 * full chests (a live village's store, hauled full) waits for things the village's Mason and Carpenter make from what is
 * in that store. Expected: they are made, or the request says plainly what is missing. Setup and checks are in
 * {@link QaStalledCrafting1109bGameTests}, whose tests (store with room, own chest full, big build, reload) pass.
 */
public class QaStalledCrafting1109bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = QaStalledCrafting1109bGameTests.BIG;

	/**
	 * B1111, the full Storehouse: the builder's table stands by the Storehouse, whose chests are full of what a village
	 * hauls in (cobblestone) and hold 16 stone bricks. The builder waits for 4 stone brick slabs. Expected: the Mason cuts
	 * them (the bricks he takes free room), or the builder's request says plainly why nothing is made.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaStall1109FullStore"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaStall1109FullStore")
	public void b1111TheMasonCutsSlabsForABuilderByAFullStorehouse(GameTestHelper helper) {
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "fullstore", QaStalledCrafting1109bGameTests.slabRow(), true, false,
			List.of(new ItemStack(Items.STONE_BRICKS, 16)), true, false, false);
		QaStalledCrafting1109bGameTests.madeOrSaid(helper, run, Map.of(Blocks.STONE_BRICK_SLAB, 4), Items.STONE_BRICK_SLAB, 1800);
	}

	/**
	 * B1112, the owner's whole list at once: one builder waits for a brewing stand (no blaze rod anywhere: nobody can
	 * make it), a flower pot (no bricks: nobody can), stone brick slabs, a chest, an oak ladder and a spruce door, with
	 * the Storehouse full of everything else a village hauls in. Expected: the makeable ones are made, or the request
	 * says plainly why not.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaStall1109FullStoreAll"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaStall1109FullStoreAll")
	public void b1112AFullStorehouseStallsEveryRequestWithoutAWord(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(QaStalledCrafting1109bGameTests.carpenterRow());
		blocks.put(new BlockPos(8, 0, 2), Blocks.BREWING_STAND.defaultBlockState());
		blocks.put(new BlockPos(9, 0, 2), Blocks.FLOWER_POT.defaultBlockState());
		QaStalledCrafting1109bGameTests.slabRow().forEach((p, s) -> blocks.put(p.offset(0, 0, 4), s));
		List<ItemStack> store = new ArrayList<>(QaStalledCrafting1109bGameTests.woodStore());
		store.add(new ItemStack(Items.STONE_BRICKS, 16));
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "fullall", blocks, true, true, store, true, false, false);
		QaStalledCrafting1109bGameTests.madeOrSaid(helper, run, Map.of(Blocks.CHEST, 1, Blocks.LADDER, 1, Blocks.SPRUCE_DOOR, 1, Blocks.STONE_BRICK_SLAB, 4), Items.CHEST, 1800);
	}
}
