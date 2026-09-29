package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Scholars research what the village chooses, paid for from their chests; Architecture draws up the Town Hall. */
public class ResearchGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(17, 2, 17);
	private static final BlockPos DESK = new BlockPos(8, 2, 8);
	private static final BlockPos CHEST = new BlockPos(8, 2, 10);

	/** Swift Hands I chosen on the screen: the scholar takes 16 paper and 4 emeralds, works on it, and it's done. */
	@GameTest(template = AREA, timeoutTicks = 600, batch = "scholarResearchesSwiftHands")
	public void scholarResearchesSwiftHands(GameTestHelper helper) {
		Setup s = setup(helper);
		s.chest().setItem(0, new ItemStack(Items.PAPER, 20));
		s.chest().setItem(1, new ItemStack(Items.EMERALD, 5));
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = ResearchScreen.forTest(s.player(), s.hall());
			menu.press(ResearchScreen.TOPIC_SLOTS[Research.Topic.SWIFT_HANDS.ordinal()], s.player());
			helper.assertTrue(s.entity().research().currentTopic() == Research.Topic.SWIFT_HANDS, "not chosen");
			helper.succeedWhen(() -> {
				helper.assertTrue(s.entity().research().level(Research.Topic.SWIFT_HANDS) == 1, "Swift Hands: " + s.entity().research());
				helper.assertTrue(s.chest().countItem(Items.PAPER) == 4 && s.chest().countItem(Items.EMERALD) == 1, "paper "
					+ s.chest().countItem(Items.PAPER) + ", emeralds " + s.chest().countItem(Items.EMERALD));
				helper.assertTrue(s.scholar().getAttachedOrElse(ModAttachments.RESEARCH_DONE, 0) == 1, "research done");
			});
		});
	}

	/** Locked topics can't be chosen; Architecture, once researched, puts the Town Hall blueprint in the chest. */
	@GameTest(template = AREA, timeoutTicks = 600, batch = "architectureDrawsUpTheTownHall")
	public void architectureDrawsUpTheTownHall(GameTestHelper helper) {
		Setup s = setup(helper);
		s.chest().setItem(0, new ItemStack(Items.PAPER, 16));
		s.chest().setItem(1, new ItemStack(Items.BOOK, 4));
		s.chest().setItem(2, new ItemStack(Items.EMERALD, 4));
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = ResearchScreen.forTest(s.player(), s.hall());
			menu.press(ResearchScreen.TOPIC_SLOTS[Research.Topic.ARCHITECTURE.ordinal()], s.player());
			helper.assertTrue(s.entity().research().currentTopic() == null, "Architecture chosen before its needs");
			s.entity().setResearch(new Research.State(Map.of("swift_hands", 2, "hearth", 1), Optional.empty(), 0, false));
			menu.press(ResearchScreen.TOPIC_SLOTS[Research.Topic.ARCHITECTURE.ordinal()], s.player());
			helper.assertTrue(s.entity().research().currentTopic() == Research.Topic.ARCHITECTURE, "Architecture not chosen");
			helper.succeedWhen(() -> {
				helper.assertTrue(s.entity().research().level(Research.Topic.ARCHITECTURE) == 1, "not researched");
				boolean blueprint = false;
				for (int i = 0; i < s.chest().getContainerSize(); i++) {
					ItemStack stack = s.chest().getItem(i);
					blueprint |= stack.is(ModItems.BLUEPRINT) && BlueprintItem.data(stack).map(d -> d.structure().toString()).orElse("").equals("aliveworkplace:research/town_hall");
				}
				helper.assertTrue(blueprint, "no Town Hall blueprint in the chest");
			});
		});
	}

	private record Setup(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Container chest, Villager scholar, ServerPlayer player) {
	}

	private static Setup setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		int points = Research.POINTS;
		VillageHalls.RADIUS = 16;
		Research.POINTS = 100;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.POINTS = points;
			Research.forget();
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DESK, ModBlocks.SCHOLARS_DESK);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Jobs.employ(level, scholar, helper.absolutePos(DESK), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos hall = helper.absolutePos(HALL);
		return new Setup(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall), helper.getBlockEntity(CHEST), scholar, player);
	}

	/**
	 * The new topics unlock after what they need, and their bonuses show up: Commerce II makes mercenaries 6 emeralds
	 * cheaper, Green Thumb II a bone meal three layers of compost, Medicine II illness a third as likely.
	 */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "researchBonuses")
	public void researchBonuses(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 15));
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
			helper.assertFalse(Research.State.EMPTY.available(Research.Topic.EXPEDITIONS), "Expeditions without Logistics");
			helper.assertTrue(new Research.State(Map.of("logistics", 1), Optional.empty(), 0, false).available(Research.Topic.EXPEDITIONS),
				"Expeditions locked after Logistics I");
			float before = io.github.jcondedata.aliveworkplace.people.Sickness.dailyChance(level, villager);
			entity.setResearch(new Research.State(Map.of("hearth", 1, "commerce", 2, "green_thumb", 2, "medicine", 2), Optional.empty(), 0, false));
			Research.forget();
			helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Mercenaries.price(level, helper.absolutePos(HALL))
				== io.github.jcondedata.aliveworkplace.guard.Mercenaries.PRICE_EMERALDS - 6, "mercenaries' price");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.compost.CompostWork.layersPerBoneMeal(villager) == 3f, "compost layers");
			float after = io.github.jcondedata.aliveworkplace.people.Sickness.dailyChance(level, villager);
			helper.assertTrue(Math.abs(after - before / 3f) < 1e-4, "illness " + before + " -> " + after);
			Research.forget();
			helper.succeed();
		});
	}
}
