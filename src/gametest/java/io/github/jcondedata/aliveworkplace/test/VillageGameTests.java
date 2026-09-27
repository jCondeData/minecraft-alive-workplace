package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Builder's workshops in villages. */
public class VillageGameTests implements FabricGameTest {
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyVillageTypeCanGrowABuildersWorkshop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		for (String style : VillageHouses.STYLES) {
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			helper.assertTrue(pool != null, "no village house pool for " + style);
			String workshop = VillageHouses.workshop(style).toString();
			boolean listed = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
				.anyMatch(p -> p.getFirst().toString().contains(workshop));
			helper.assertTrue(listed, style + " villages can't grow a builder's workshop");

			StructureTemplate template = level.getStructureManager().get(VillageHouses.workshop(style))
				.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("missing " + workshop));
			List<StructureTemplate.StructureBlockInfo> benches = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.BUILDERS_BENCH);
			helper.assertTrue(benches.size() == 1, workshop + " should have one Builder's Bench");
			List<StructureTemplate.StructureBlockInfo> jigsaws = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW);
			boolean entrance = jigsaws.stream().anyMatch(j -> j.nbt() != null && j.nbt().getString("name").equals("minecraft:building_entrance"));
			boolean villager = jigsaws.stream().anyMatch(j -> j.nbt() != null && j.nbt().getString("pool").equals("minecraft:village/" + style + "/villagers"));
			helper.assertTrue(entrance && villager, workshop + " needs a street connection and a villager spawn");
			boolean loot = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST).stream()
				.map(StructureTemplate.StructureBlockInfo::nbt)
				.anyMatch(n -> n != null && n.getString("LootTable").equals("aliveworkplace:chests/village_builders_workshop"));
			helper.assertTrue(loot, workshop + " should have a supply chest");
		}
		helper.succeed();
	}

	/** A jobless villager inside a freshly generated workshop takes the bench and becomes a builder. */
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 2400)
	public void aVillagerMovesIntoTheWorkshop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(VillageHouses.workshop("plains")).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 9));
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.setDayTime(2000);
		helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.BUILDER,
			"villager is still " + villager.getVillagerData().getProfession()));
	}
}
