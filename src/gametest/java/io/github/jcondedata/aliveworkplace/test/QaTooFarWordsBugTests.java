package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
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
 * QA bug test (lane-b-1025-0937), not registered until fixed: register this class with the fix. A Field Marker whose
 * field is too far from the farmer's composter is refused with en_us.json's assign.too_far, "That spot is %s blocks from
 * the builder's Blueprint Table — keep it within %s.": a farmer has no Blueprint Table. Fields, Miners and AreaJobs all
 * send the builder's sentence.
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
		helper.succeed();
	}
}
