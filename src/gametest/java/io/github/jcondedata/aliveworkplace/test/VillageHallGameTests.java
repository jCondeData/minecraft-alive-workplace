package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/** The Village Hall counts the village round it and lists its people. */
public class VillageHallGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** Workers, the jobless, children, beds, food in the store and guards are counted; the screen lists the workers. */
	@GameTest(template = AREA, timeoutTicks = 200, batch = "villageHallCountsTheVillage")
	public void villageHallCountsTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16; // just this test's area
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		Jobs.employ(level, builder, helper.absolutePos(new BlockPos(5, 2, 5)), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		helper.setBlock(new BlockPos(17, 2, 5), ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 6));
		Jobs.employ(level, guard, helper.absolutePos(new BlockPos(17, 2, 5)), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 14));
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 14));
		child.setAge(-24000);
		for (int x : new int[] {3, 5}) {
			helper.setBlock(new BlockPos(x, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(new BlockPos(x, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 19), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(17, 2, 19));
		chest.setItem(0, new ItemStack(Items.BREAD, 10));
		chest.setItem(1, new ItemStack(Items.COBBLESTONE, 5));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(5, () -> {
			VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
			helper.assertTrue(census.workers().size() == 2, "workers: " + census.workers().size());
			helper.assertTrue(census.jobless().size() == 1, "jobless: " + census.jobless().size());
			helper.assertTrue(census.children() == 1, "children: " + census.children());
			helper.assertTrue(census.beds() == 2 && census.freeBeds() <= 2, "beds: " + census.beds() + ", free " + census.freeBeds());
			helper.assertTrue(census.food() == 10, "food: " + census.food());
			helper.assertTrue(census.guards() == 1, "guards: " + census.guards());

			ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
			ItemStack first = menu.icon(VillageHallScreen.FIRST_PERSON);
			helper.assertTrue(first.is(ModBlocks.BUILDERS_BENCH.asItem()), "first listed: " + first);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_PERSON + 1).is(ModBlocks.GUARD_POST.asItem()), "second listed: "
				+ menu.icon(VillageHallScreen.FIRST_PERSON + 1));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_PERSON + 2).is(Items.PAPER), "the jobless villager isn't listed last");
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_PERSON + 3).isEmpty(), "the child is listed");
			menu.press(VillageHallScreen.FIRST_PERSON, player);
			helper.assertTrue(builder.hasEffect(MobEffects.GLOWING), "the builder isn't glowing");
			helper.succeed();
		});
	}

	/** A named Name Tag names the village; without one it has a made-up name that stays the same. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villageHallTakesANameTag")
	public void villageHallTakesANameTag(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Component madeUp = VillageHalls.name(level, hall);
		helper.assertTrue(madeUp.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getArgs().length == 2,
			"made-up name: " + madeUp);
		helper.assertTrue(VillageHalls.name(level, hall).equals(madeUp), "the made-up name changed");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal("Testville"));
		player.setItemInHand(InteractionHand.MAIN_HAND, tag);
		helper.useBlock(HALL, player);
		helper.assertTrue(VillageHalls.name(level, hall).getString().equals("Testville"), "name: " + VillageHalls.name(level, hall).getString());
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.NAME_TAG), "the name tag was used up");
		helper.succeed();
	}
}
