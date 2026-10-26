package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug test (lane-b-1025-0937), registered with the fix (B453, lane-a-1026-0022). A Field Marker whose field is too
 * far from the farmer's composter was refused with en_us.json's assign.too_far, "That spot is %s blocks from the
 * builder's Blueprint Table — keep it within %s.": a farmer has no Blueprint Table. Fields, Miners and AreaJobs all sent
 * the builder's sentence; now each names the block that worker works at (field.too_far, quarry.too_far and
 * &lt;kind&gt;.too_far: "That field is %s blocks from the %s the farmer works at — keep it within %s.").
 */
public class QaTooFarWordsBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_batch AREA '"qaTooFarFarmer"'
	@GameTest(template = AREA, batch = "qaTooFarFarmer")
	public void aFieldTooFarFromTheComposterDoesNotSpeakOfABlueprintTable(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos composter = new BlockPos(2, 2, 2);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos far = helper.absolutePos(composter).east(60);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(far), Optional.of(far.offset(4, 0, 4))));
		Fields.assign(player, farmer, marker);
		List<String> lines = seen.stream().map(Component::getString).toList();
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains("63") && l.contains("48")), "the field 63 blocks off (reach 48) wasn't refused for its distance: " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("Blueprint Table") || l.contains("builder")),
			"a farmer's too-far line speaks of the builder's Blueprint Table: " + lines);
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains("Composter the farmer works at")), "the farmer's line doesn't name his composter: " + lines);
		helper.succeed();
	}

	/** The miner's quarry too far from the Miner's Bench names the bench, not the builder's table. */
	//$ gametest_batch AREA '"qaTooFarMiner"'
	@GameTest(template = AREA, batch = "qaTooFarMiner")
	public void aQuarryTooFarFromTheBenchNamesTheMinersBench(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.MINERS_BENCH);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, miner, helper.absolutePos(bench), ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos far = helper.absolutePos(bench).east(Miners.MAX_DISTANCE + 6);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(far), Optional.of(far.offset(3, 0, 3)), 4));
		Miners.assign(player, miner, marker);
		List<String> lines = seen.stream().map(Component::getString).toList();
		helper.assertTrue(Miners.activeSite(level, miner) == null, "a quarry " + (Miners.MAX_DISTANCE + 6) + " blocks off was started");
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains("Miner's Bench the miner works at") && l.contains(String.valueOf(Miners.MAX_DISTANCE))),
			"the miner's too-far line doesn't name the Miner's Bench: " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("Blueprint Table") || l.contains("builder")), "the miner's line speaks of the builder: " + lines);
		helper.succeed();
	}

	/** An area job (the lumberjack's tree farm) too far from the Chopping Block names it. */
	//$ gametest_batch AREA '"qaTooFarLumberjack"'
	@GameTest(template = AREA, batch = "qaTooFarLumberjack")
	public void aTreeFarmTooFarFromTheChoppingBlockNamesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos block = new BlockPos(2, 2, 2);
		helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
		Villager lumberjack = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, lumberjack, helper.absolutePos(block), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos far = helper.absolutePos(block).east(60);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(far), Optional.of(far.offset(4, 0, 4))));
		TreeFarms.assign(player, lumberjack, marker);
		List<String> lines = seen.stream().map(Component::getString).toList();
		helper.assertTrue(TreeFarms.farm(lumberjack) == null, "a tree farm 63 blocks off was taken");
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains("63") && l.contains(String.valueOf(AreaJobs.MAX_DISTANCE))
			&& l.contains("Chopping Block the lumberjack works at")), "the lumberjack's too-far line doesn't name the Chopping Block: " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("Blueprint Table") || l.contains("builder")), "the lumberjack's line speaks of the builder: " + lines);
		helper.succeed();
	}
}
