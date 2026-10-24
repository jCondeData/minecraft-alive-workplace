package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bug tests from the lane-c QA pass (lane-c-1024-0353) on ROADMAP 37.14a, failing on today's code: not registered in
 * fabric.mod.json, so main stays green. The builder registers each with its fix. Helpers are QaCrafterFurnitureGameTests's.
 */
public class QaCrafterFurnitureBugTests {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;

	/**
	 * The Carpenter's bench is broken while the chain is half made (planks in the bag): the logs and planks aren't lost
	 * with it. Within a while nothing is left in the bag of a carpenter with no bench, no job stays saved on them, and the
	 * wood (logs, planks, slabs) is in a chest or on the ground for the player.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaChainBenchBroken"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaChainBenchBroken")
	public void b353TheCarpentersBenchBrokenMidChainLosesNothing(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		AtomicInteger since = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (since.get() < 0 && job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() >= 1) {
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).count(Items.OAK_PLANKS) > 0, "no planks made at step 2");
				helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench
				since.set(0);
			} else if (since.get() >= 0) {
				since.incrementAndGet();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(since.get() >= 0, "the carpenter never got to the chain's second step");
			helper.assertTrue(since.get() > 1200, "giving the carpenter a minute without a bench");
			var bag = ModAttachments.BUILDER_BAG.getOrCreate(carpenter);
			helper.assertTrue(bag.isEmpty(), "the wood is stuck in the bag of a carpenter with no bench: " + bag.stacks() + " (profession "
				+ carpenter.getVillagerData().getProfession() + ", job " + ModAttachments.CRAFTER_JOB.get(carpenter) + ")");
			Container chest = helper.getBlockEntity(a.chest());
			int wood = 4 * (chest.countItem(Items.OAK_LOG) + QaCrafterFurnitureGameTests.loose(helper, Items.OAK_LOG)) + chest.countItem(Items.OAK_PLANKS) + QaCrafterFurnitureGameTests.loose(helper, Items.OAK_PLANKS)
				+ chest.countItem(Items.OAK_SLAB) + QaCrafterFurnitureGameTests.loose(helper, Items.OAK_SLAB);
			helper.assertTrue(wood > 0, "the six logs are gone: nothing in the chest or on the ground");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "a job is still saved on the carpenter with no bench");
		});
	}


	/**
	 * The kiln's fuel counts as a raw material (a coal for every eight fired): twelve clay and one coal for brick stairs
	 * is a coal short, so the Mason takes nothing, and the builder's line names the coal.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaChainMasonFuel"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaChainMasonFuel")
	public void b352AMasonShortOfCoalSaysSoAndTakesNothing(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 1));
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		helper.runAfterDelay(700, () -> {
			ServerLevel level = helper.getLevel();
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals("Can't be made yet: short of 1 Coal to make 3 Brick Stairs"),
				"the builder's line with one coal for twelve clay: " + (line == null ? null : line.getString()));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 12 && chest.countItem(Items.COAL) == 1, "the mason took some: "
				+ chest.countItem(Items.CLAY_BALL) + " clay, " + chest.countItem(Items.COAL) + " coal left");
			helper.assertTrue(chest.countItem(Items.BRICK) == 0 && QaCrafterFurnitureGameTests.loose(helper, Items.BRICK) == 0, "bricks were fired without the fuel for the rest");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(mason).isEmpty() && ModAttachments.CRAFTER_JOB.get(mason) == null,
				"the mason took a job it can't finish: " + ModAttachments.CRAFTER_JOB.get(mason));
			helper.succeed();
		});
	}
}
