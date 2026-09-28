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

/** Our houses in villages. */
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
			boolean villager = jigsaws.stream().anyMatch(j -> j.nbt() != null && j.nbt().getString("pool").equals("aliveworkplace:village/" + style + "/workers"));
			helper.assertTrue(entrance && villager, workshop + " needs a street connection and a villager spawn");
			// Only jobless adults move in (the village's own pool can give a nitwit, a baby or, with CobbleDollars, a merchant).
			StructureTemplatePool workers = pools.get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("village/" + style + "/workers"));
			helper.assertTrue(workers != null, "no worker pool for " + style);
			var elements = ((StructureTemplatePoolAccessor) workers).aliveworkplace$rawTemplates();
			helper.assertTrue(elements.size() == 1 && elements.get(0).getFirst().toString().contains("minecraft:village/" + style + "/villagers/unemployed"),
				style + " worker pool: " + elements);
			helper.assertTrue(level.getStructureManager().get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/" + style + "/villagers/unemployed")).isPresent(),
				"no vanilla unemployed villager template for " + style);
			boolean loot = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST).stream()
				.map(StructureTemplate.StructureBlockInfo::nbt)
				.anyMatch(n -> n != null && n.getString("LootTable").equals("aliveworkplace:chests/village_builders_workshop"));
			helper.assertTrue(loot, workshop + " should have a supply chest");
		}
		helper.succeed();
	}

	/** Trainer's houses, guard houses, clinics and post offices: in every village type's pool, each with its job block. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyVillageTypeCanGrowTheOtherHouses(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		java.util.Map<String, net.minecraft.world.level.block.Block> houses = java.util.Map.of(
			"trainers_house", ModBlocks.TRAINING_POST, "guard_house", ModBlocks.GUARD_POST,
			"clinic", ModBlocks.NURSE_STATION, "post_office", ModBlocks.POSTAL_DESK, "leaders_hall", ModBlocks.LEADERS_PODIUM,
			"school", ModBlocks.TUTORS_DESK, "trade_hall", ModBlocks.TRADE_BOARD, "orchard_house", ModBlocks.FRUIT_BASKET,
			"ball_workshop", ModBlocks.BALL_WORKBENCH, "ferry_house", ModBlocks.TRAVEL_POST);
		// No Cobblemon here: the Pokémon houses stay out of the pools.
		helper.assertTrue(VillageHouses.houseNames().equals(List.of("guard_house", "clinic", "post_office", "orchard_house", "ferry_house")),
			"houses without Cobblemon: " + VillageHouses.houseNames());
		for (String style : VillageHouses.STYLES) {
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			for (var house : houses.entrySet()) {
				var id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("village/" + style + "_" + house.getKey());
				boolean listed = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
					.anyMatch(p -> p.getFirst().toString().contains(id.toString()));
				helper.assertTrue(listed == VillageHouses.houseNames().contains(house.getKey()),
					style + " villages " + (listed ? "grow" : "can't grow") + " a " + house.getKey());
				StructureTemplate template = level.getStructureManager().get(id)
					.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("missing " + id));
				helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), house.getValue()).size() == 1,
					id + " should have one " + house.getValue().getName().getString());
				// Exactly one job block, so the villager who moves in takes ours (a lectern would make a librarian).
				long jobSites = jobSites(template);
				helper.assertTrue(jobSites == 1, id + " has " + jobSites + " job blocks");
			}
		}
		helper.succeed();
	}

	/** Job-site blocks (ours and vanilla's) in a template. */
	private static long jobSites(StructureTemplate template) {
		net.minecraft.nbt.CompoundTag tag = template.save(new net.minecraft.nbt.CompoundTag());
		net.minecraft.nbt.ListTag palette = tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
		java.util.List<net.minecraft.world.level.block.state.BlockState> states = new java.util.ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
		}
		long count = 0;
		net.minecraft.nbt.ListTag blocks = tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < blocks.size(); i++) {
			var state = states.get(blocks.getCompound(i).getInt("state"));
			if (net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state)
				.filter(h -> h.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent()) {
				count++;
			}
		}
		return count;
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
