package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** A villager's home is the finished building their bed is in; better homes, better moods (Milestone 20). */
public class HomesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);

	/** A blueprint's tier goes by its name, styled or not. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBuildingsTierGoesByItsName(GameTestHelper helper) {
		helper.assertTrue(BlueprintUpgrades.tier(StarterBlueprints.STONE_HOUSE.id()) == 1, "stone_house");
		helper.assertTrue(BlueprintUpgrades.tier(StarterBlueprints.STONE_HOUSE_2.id()) == 2, "stone_house_2");
		helper.assertTrue(BlueprintUpgrades.tier(StarterBlueprints.STONE_HOUSE_3.id()) == 3, "stone_house_3");
		helper.assertTrue(BlueprintUpgrades.tier(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "styled/cherry/aliveworkplace/stone_house_2")) == 2,
			"a styled tier II");
		helper.assertTrue(BlueprintUpgrades.tier(ResourceLocation.fromNamespaceAndPath("x", "house_1")) == 1, "house_1");
		helper.assertTrue(BlueprintUpgrades.tier(ResourceLocation.fromNamespaceAndPath("x", "house_100")) == 1, "house_100");
		helper.succeed();
	}

	private static boolean says(Moods.Mood mood, String reason) {
		return mood.good().stream().map(Component::getContents)
			.anyMatch(c -> c instanceof TranslatableContents t && t.getKey().equals("mood.aliveworkplace.reason." + reason));
	}

	/**
	 * Three villagers whose beds are in a finished Stone House: the hall's "What next?" suggests better homes. Upgraded to
	 * a Stone House II their moods rise by 5 ("a fine home") and the tip goes; a Stone House III lifts them by 10.
	 */
	//$ gametest_ticks_batch AREA '100' '"betterHomesMakeHappierVillagers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "betterHomesMakeHappierVillagers")
	public void betterHomesMakeHappierVillagers(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)),
			Rotation.NONE, Mirror.NONE);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(placement);
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> villagers = List.of(helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 12)),
			helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 12)), helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 12)));
		helper.runAfterDelay(2, () -> {
			// Their beds are inside the house (x 2-12, z 2-10 here); the brain checks them later, so all of this is one tick
			for (int i = 0; i < villagers.size(); i++) {
				villagers.get(i).getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(4 + 2 * i, 3, 5))));
			}
			Villager villager = villagers.get(0);
			helper.assertTrue(Homes.of(level, villager).isEmpty(), "a home before anything was built");
			java.util.function.Supplier<List<String>> advice = () -> VillageAdvice.tips(level, hall).stream().map(VillageAdvice.Tip::key).toList();
			helper.assertTrue(advice.get().contains("homes"), "no better-homes tip with no built homes: " + advice.get());

			sites.recordFinished(StarterBlueprints.STONE_HOUSE.id(), placement, UUID.randomUUID());
			helper.assertTrue(Homes.of(level, villager).map(h -> h.tier() == 1 && h.structure().equals(StarterBlueprints.STONE_HOUSE.id())).orElse(false),
				"not at home in the Stone House: " + Homes.of(level, villager));
			Moods.Mood plain = Moods.work(level, villager);
			helper.assertTrue(!says(plain, "fine_home") && !says(plain, "grand_home"), "a tier I home lifts the mood: " + plain);
			helper.assertTrue(advice.get().contains("homes"), "no better-homes tip in tier I houses: " + advice.get());

			sites.recordFinished(StarterBlueprints.STONE_HOUSE_2.id(), placement, UUID.randomUUID());
			Moods.Mood fine = Moods.work(level, villager);
			helper.assertTrue(fine.score() == Math.min(100, plain.score() + Homes.TIER_2_MOOD) && says(fine, "fine_home"),
				"tier II: " + plain.score() + " -> " + fine);
			helper.assertTrue(!advice.get().contains("homes"), "the better-homes tip stays after the upgrade: " + advice.get());

			sites.recordFinished(StarterBlueprints.STONE_HOUSE_3.id(), placement, UUID.randomUUID());
			Moods.Mood grand = Moods.work(level, villager);
			helper.assertTrue(grand.score() == Math.min(100, plain.score() + Homes.TIER_3_MOOD) && says(grand, "grand_home"),
				"tier III: " + plain.score() + " -> " + grand);

			// A bed outside the building is no home of it
			villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(14, 2, 4))));
			helper.assertTrue(Homes.of(level, villager).isEmpty(), "a bed outside the house counts as in it");
			helper.succeed();
		});
	}
}
