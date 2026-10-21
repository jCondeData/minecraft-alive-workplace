package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.FurnicrafterWork;
import io.github.jcondedata.aliveworkplace.craft.FurnitureRecipes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;

/**
 * ROADMAP 38.12 without CobbleFurnies (this suite's runtime has none; {@code FurnicrafterCompatTests} has it): nothing
 * changes. There's no Furnicrafter profession and no job site, no furniture recipes, nothing a Furnicrafter would plan,
 * and a villager saved as a Furnicrafter in a world CobbleFurnies was removed from loads jobless.
 */
public class FurnicrafterGameTests implements FabricGameTest {
	/** Without CobbleFurnies the job isn't registered at all: no profession, no POI, no recipes. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutCobbleFurniesThereIsNoFurnicrafter(GameTestHelper helper) {
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblefurnies"), "CobbleFurnies is loaded in the plain suite");
		helper.assertTrue(ModVillagers.FURNICRAFTER == null, "a Furnicrafter profession without CobbleFurnies");
		helper.assertTrue(!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(AliveWorkplace.id("furnicrafter")),
			"aliveworkplace:furnicrafter is registered without CobbleFurnies");
		helper.assertTrue(!helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).containsKey(ModVillagers.FURNICRAFTER_ID),
			"the Furnicrafter POI is registered without CobbleFurnies");
		helper.assertTrue(!ModVillagers.isFurnicrafter(VillagerProfession.NONE) && !ModVillagers.isFurnicrafter(VillagerProfession.MASON),
			"another job counts as a Furnicrafter");
		helper.assertTrue(!FurnitureRecipes.SOURCE.present(), "furniture recipes filled in without a furniture mod");
		helper.assertTrue(FurnitureRecipes.all(helper.getLevel().getServer()).isEmpty(), "furniture recipes without a furniture mod");
		helper.assertTrue(!FurnitureRecipes.makes(helper.getLevel().getServer(), Items.OAK_STAIRS), "oak stairs count as furniture");
		helper.assertTrue(FurnicrafterWork.plan(helper.getLevel(), Items.OAK_STAIRS, 4, Map.of(Items.OAK_PLANKS, 64L)) == null,
			"a Furnicrafter's plan without a furniture mod");
		helper.succeed();
	}

	/** A villager saved as a Furnicrafter (CobbleFurnies since removed) loads as a jobless villager, not a broken one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSavedFurnicrafterLoadsJoblessWithoutCobbleFurnies(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		CompoundTag saved = new CompoundTag();
		villager.saveWithoutId(saved);
		CompoundTag data = saved.getCompound("VillagerData");
		data.put("profession", StringTag.valueOf("aliveworkplace:furnicrafter"));
		saved.put("VillagerData", data);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(saved);
		helper.assertTrue(copy.getVillagerData().getProfession() == VillagerProfession.NONE,
			"a saved Furnicrafter loads as " + copy.getVillagerData().getProfession());
		helper.assertTrue(!FurnicrafterWork.isFurnicrafter(copy), "the loaded villager counts as a Furnicrafter");
		copy.discard();
		villager.discard();
		helper.succeed();
	}

	/** The switch's text is there, and it's on in GameTests (M38's gate is open for them). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFurnicraftersSwitchHasItsText(GameTestHelper helper) {
		helper.assertTrue(FurnicrafterWork.ENABLED, "furnicrafters should be on in GameTests");
		String name = JewellerGameTests.english(net.minecraft.network.chat.Component.translatable("aliveworkplace.config.furnicrafters"));
		helper.assertTrue(name.equals("Furnicrafters"), "the switch is called " + name);
		String tip = JewellerGameTests.english(net.minecraft.network.chat.Component.translatable("aliveworkplace.config.furnicrafters.tooltip"));
		helper.assertTrue(tip.contains("CobbleFurnies") && tip.contains("Off:"), "the tooltip says " + tip);
		helper.succeed();
	}
}
