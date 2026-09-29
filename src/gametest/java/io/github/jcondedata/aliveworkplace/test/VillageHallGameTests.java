package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
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

	/** Grown villagers who haven't eaten for a day eat from the store — bread, never the golden carrots. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villagersEatFromTheStore")
	public void villagersEatFromTheStore(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 19), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(17, 2, 19));
		chest.setItem(0, new ItemStack(Items.GOLDEN_CARROT, 2));
		chest.setItem(1, new ItemStack(Items.BREAD, 3));
		Villager[] villagers = {helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8)), helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8))};
		for (Villager v : villagers) {
			v.setAttached(ModAttachments.LAST_MEAL, level.getGameTime() - VillageNeeds.DAY - 1);
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageNeeds.Needs needs = VillageNeeds.check(level, helper.absolutePos(HALL));
			helper.assertTrue(chest.countItem(Items.BREAD) == 1, "bread left: " + chest.countItem(Items.BREAD));
			helper.assertTrue(chest.countItem(Items.GOLDEN_CARROT) == 2, "golden carrots left: " + chest.countItem(Items.GOLDEN_CARROT));
			helper.assertTrue(needs.adults() == 2 && needs.fed() == 2, "fed " + needs.fed() + " of " + needs.adults());
			for (Villager v : villagers) {
				helper.assertTrue(!VillageNeeds.isHungry(v, level.getGameTime()), "still hungry");
			}
			helper.succeed();
		});
	}

	/** A hungry village with no beds, guards or light works 20% slower; a village without a hall at the usual pace. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aHungryVillageWorksSlower")
	public void aHungryVillageWorksSlower(GameTestHelper helper) {
		helper.assertTrue(VillageNeeds.factor(0f) == 1.25f && VillageNeeds.factor(0.5f) == 1f && Math.abs(VillageNeeds.factor(1f) - 0.8f) < 1e-6,
			"pace: " + VillageNeeds.factor(0f) + " / " + VillageNeeds.factor(0.5f) + " / " + VillageNeeds.factor(1f));
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
		});
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		Jobs.employ(level, builder, helper.absolutePos(new BlockPos(5, 2, 5)), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		builder.setAttached(ModAttachments.LAST_MEAL, level.getGameTime() - 2 * VillageNeeds.DAY);
		VillageNeeds.forget();
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(100, builder) == 100, "no hall: usual pace");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageNeeds.forget();
			int delay = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(100, builder);
			helper.assertTrue(delay == 125, "delay in a hungry village: " + delay);
			helper.succeed();
		});
	}

	/** With a free bed, 16 meals in the store and a happy village, two villagers have a baby; the family eats 8 meals. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aVillageWithFoodAndABedGrows")
	public void aVillageWithFoodAndABedGrows(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 19), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(17, 2, 19));
		chest.setItem(0, new ItemStack(Items.BREAD, 15));
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		for (int x : new int[] {3, 5, 7}) {
			helper.setBlock(new BlockPos(x, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(new BlockPos(x, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageNeeds.Needs happy = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.FOOD, "15 meals: "
				+ VillageGrowth.blocker(level, hall, happy, 0));
			chest.setItem(1, new ItemStack(Items.BAKED_POTATO, 5));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, level.getGameTime() - 100) == VillageGrowth.Blocker.TOO_SOON, "a baby just born");
			helper.assertTrue(VillageGrowth.blocker(level, hall, new VillageNeeds.Needs(2, 0, 2, 0, 0, 0, 0, 0.2f), 0) == VillageGrowth.Blocker.WELLBEING,
				"an unhappy village grows");
			Villager baby = VillageGrowth.grow(level, hall, happy, 0);
			helper.assertTrue(baby != null && baby.isBaby() && baby.isAlive(), "no baby");
			int meals = chest.countItem(Items.BREAD) + chest.countItem(Items.BAKED_POTATO);
			helper.assertTrue(meals == 12, "meals left: " + meals);
			var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity.chronicle().size() == 1 && entity.chronicle().get(0).kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.BIRTH,
				"chronicle: " + entity.chronicle());
			helper.succeed();
		});
	}

	/** With little food in the store the village asks for bread; handing it in at the hall fills the store and pays. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "questsAreHandedInAtTheHall")
	public void questsAreHandedInAtTheHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 19), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(17, 2, 19));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.BREAD, 20));
		helper.runAfterDelay(3, () -> {
			VillageQuests.Quest quest = VillageQuests.make(level, hall);
			helper.assertTrue(quest != null && quest.kind() == VillageQuests.Kind.BRING && quest.itemType() == Items.BREAD && quest.count() == 16,
				"quest: " + quest);
			var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setQuests(java.util.List.of(quest));
			ChoiceMenu menu = io.github.jcondedata.aliveworkplace.work.ChoiceMenu.detached(player, m -> VillageHallScreen.renderQuests(m, level, hall, player));
			helper.assertTrue(menu.icon(VillageHallScreen.QUEST_SLOTS[0]).is(Items.BREAD), "the quest isn't on the page");
			menu.press(VillageHallScreen.QUEST_SLOTS[0], player);
			helper.assertTrue(chest.countItem(Items.BREAD) == 16, "bread in the store: " + chest.countItem(Items.BREAD));
			helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 4, "bread left on the player: " + player.getInventory().countItem(Items.BREAD));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == quest.reward(), "emeralds: " + player.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(entity.quests().isEmpty() && entity.questsDone() == 1, "the quest is still up");
			helper.succeed();
		});
	}

	/** Monsters players defeat in the village count towards its clearing-out quest. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "monstersCountTowardsAQuest")
	public void monstersCountTowardsAQuest(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		helper.runAfterDelay(3, () -> {
			var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setQuests(java.util.List.of(new VillageQuests.Quest(java.util.UUID.randomUUID(), VillageQuests.Kind.SLAY, "minecraft:air", 2, 0, 6,
				level.getGameTime(), "", java.util.Optional.empty())));
			for (int i = 0; i < 2; i++) {
				var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5 + i * 3, 2, 5));
				zombie.hurt(level.damageSources().playerAttack(player), 1000f);
			}
			helper.assertTrue(entity.quests().isEmpty(), "quest: " + entity.quests());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 6, "emeralds: " + player.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/** Decorations finished near the hall make the village prettier: a point of beauty is 1% more wellbeing, up to 10%. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "decorationsMakeAVillagePrettier")
	public void decorationsMakeAVillagePrettier(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		var sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		java.util.List<io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement> placed = new java.util.ArrayList<>();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			placed.forEach(sites::forgetFinished);
		});
		java.util.function.BiConsumer<io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.Entry, BlockPos> finish = (entry, at) -> {
			var placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at),
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
			placed.add(placement);
			sites.recordFinished(entry.id(), placement, java.util.UUID.randomUUID());
		};
		// (Builds finished by tests that ran here before count too: go by the difference.)
		VillageNeeds.Needs before = VillageNeeds.count(level, hall);
		int base = before.beauty();
		float plain = before.wellbeing() - io.github.jcondedata.aliveworkplace.hall.Decorations.bonus(base);
		finish.accept(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.FOUNTAIN, new BlockPos(3, 2, 3));
		finish.accept(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.PARK_BENCH, new BlockPos(3, 2, 12));
		// Not a decoration: counts for nothing.
		finish.accept(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STOREHOUSE, new BlockPos(14, 2, 3));
		// Too far from the hall.
		finish.accept(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.GAZEBO, new BlockPos(11, 2, 11 + 40));
		VillageNeeds.Needs needs = VillageNeeds.count(level, hall);
		helper.assertTrue(needs.beauty() == base + 4, "beauty: " + base + " -> " + needs.beauty());
		helper.assertTrue(Math.abs(needs.wellbeing() - (plain + io.github.jcondedata.aliveworkplace.hall.Decorations.bonus(base + 4))) < 0.001f,
			"wellbeing " + before.wellbeing() + " -> " + needs.wellbeing());
		// An upgrade on the same spot counts instead of its base.
		finish.accept(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.WELL, new BlockPos(14, 2, 14));
		finish.accept(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.WELL_2, new BlockPos(14, 2, 14));
		helper.assertTrue(VillageNeeds.count(level, hall).beauty() == base + 7, "beauty with a well: " + VillageNeeds.count(level, hall).beauty());
		for (int i = 0; i < 3; i++) {
			finish.accept(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.MARKET_SQUARE, new BlockPos(2 + i, 2, 18));
		}
		needs = VillageNeeds.count(level, hall);
		helper.assertTrue(needs.beauty() == base + 22, "beauty: " + needs.beauty());
		helper.assertTrue(Math.abs(needs.wellbeing() - (plain + 0.10f)) < 0.001f, "decorations add at most 10%: " + needs.wellbeing());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		String lore = String.valueOf(menu.icon(5).get(DataComponents.LORE));
		helper.assertTrue(lore.contains("Beauty") || lore.contains("beauty"), "the wellbeing icon doesn't show beauty: " + lore);
		helper.succeed();
	}

	/** The chronicle keeps what happened in the village — a death, a quest done — newest first on its page, and it's saved. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theChronicleRemembers")
	public void theChronicleRemembers(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			Villager doomed = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
			doomed.setCustomName(Component.literal("Mira"));
			doomed.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			entity.setQuests(java.util.List.of(new VillageQuests.Quest(java.util.UUID.randomUUID(), VillageQuests.Kind.SLAY, "minecraft:air", 1, 0, 2,
				level.getGameTime(), "", java.util.Optional.empty())));
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 5));
			zombie.hurt(level.damageSources().playerAttack(player), 1000f);
			var lines = entity.chronicle();
			helper.assertTrue(lines.size() == 2, "chronicle: " + lines);
			helper.assertTrue(lines.get(0).kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.DEATH
				&& lines.get(0).text().getString().contains("Mira"), "death: " + lines.get(0).text().getString());
			helper.assertTrue(lines.get(1).kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.QUEST, "quest: " + lines.get(1));
			helper.assertTrue(lines.get(1).day() == io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level), "day");

			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			menu.press(VillageHallScreen.CHRONICLE, player);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_PERSON).is(Items.MAP), "newest first: " + menu.icon(VillageHallScreen.FIRST_PERSON));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_PERSON + 1).is(Items.BONE), "then the death: " + menu.icon(VillageHallScreen.FIRST_PERSON + 1));

			// Saved with the hall.
			var tag = entity.saveWithFullMetadata(level.registryAccess());
			var copy = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) net.minecraft.world.level.block.entity.BlockEntity
				.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && copy.chronicle().size() == 2
				&& copy.chronicle().get(0).text().getString().equals(lines.get(0).text().getString()), "not saved: " + (copy == null ? null : copy.chronicle()));
			helper.succeed();
		});
	}

	/** A jobless villager on the hall's list: clicking them lists the village's free workstations; clicking one gives them that job. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theHallHandsOutJobs")
	public void theHallHandsOutJobs(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(4, 2, 4), ModBlocks.BUILDERS_BENCH);
		BlockPos hall = helper.absolutePos(HALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 14));
		villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			var free = VillageHalls.freeStations(level, hall);
			helper.assertTrue(free.size() == 1 && free.get(0).profession() == ModVillagers.BUILDER, "free stations: " + free);
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_PERSON).is(Items.PAPER), "the jobless villager: " + menu.icon(VillageHallScreen.FIRST_PERSON));
			menu.press(VillageHallScreen.FIRST_PERSON, player);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_PERSON).is(ModBlocks.BUILDERS_BENCH.asItem()), "jobs page: " + menu.icon(VillageHallScreen.FIRST_PERSON));
			menu.press(VillageHallScreen.FIRST_PERSON, player);
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.BUILDER, "profession: " + villager.getVillagerData().getProfession());
			helper.assertTrue(villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).map(g -> g.pos())
				.orElse(null) != null, "no job site");
			helper.assertTrue(VillageHalls.freeStations(level, hall).isEmpty(), "the bench is still free");
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_PERSON).is(ModBlocks.BUILDERS_BENCH.asItem()), "back on the list as a builder");
			helper.succeed();
		});
	}

	/** "Call everyone home": villagers whose bed is in the village but who wandered off come back to the hall; strangers don't. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "callingEveryoneHome")
	public void callingEveryoneHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Villager ours = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		ours.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,
			net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(13, 2, 13))));
		Villager stranger = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 20));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			menu.press(VillageHallScreen.RECALL, player);
			helper.assertTrue(ours.blockPosition().distSqr(hall) <= 6 * 6, "not home: " + ours.blockPosition() + ", the hall at " + hall);
			helper.assertTrue(stranger.blockPosition().distSqr(helper.absolutePos(new BlockPos(20, 2, 20))) <= 4, "the stranger was moved");
			helper.succeed();
		});
	}

	/** Once a week, in the morning, a village with a Market Square holds a market: traders come, one with a blueprint to sell. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "marketDayBringsTraders")
	public void marketDayBringsTraders(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.MarketDays.ENABLED;
		VillageHalls.RADIUS = 32;
		io.github.jcondedata.aliveworkplace.hall.MarketDays.ENABLED = true;
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(20, 2, 20), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(20, 2, 20));
		var sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		var placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(2, 2, 2)), net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.hall.MarketDays.ENABLED = enabled;
			sites.forgetFinished(placement);
		});
		var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.MarketDays.square(level, hall).isEmpty(), "a square before one was built");
		sites.recordFinished(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.MARKET_SQUARE.id(), placement, java.util.UUID.randomUUID());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.MarketDays.square(level, hall).isPresent(), "no square");
		// A market morning: the day after one that divides evenly.
		long day = 7;
		while (Math.floorMod(day + hall.hashCode(), io.github.jcondedata.aliveworkplace.hall.MarketDays.EVERY_DAYS) != 0) {
			day++;
		}
		helper.setDayTime((int) ((day - 1) * 24000 + 2000));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.MarketDays.isMarketMorning(level, hall, -1), "not market day");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.MarketDays.tick(level, hall, entity), "no market");
		helper.assertFalse(io.github.jcondedata.aliveworkplace.hall.MarketDays.tick(level, hall, entity), "two markets in a day");
		var traders = level.getEntitiesOfClass(net.minecraft.world.entity.npc.WanderingTrader.class, new net.minecraft.world.phys.AABB(hall).inflate(30));
		helper.assertTrue(traders.size() == io.github.jcondedata.aliveworkplace.hall.MarketDays.TRADERS, "traders: " + traders.size());
		helper.assertTrue(traders.stream().allMatch(t -> t.getOffers().stream().anyMatch(o -> o.getResult().is(io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT))),
			"a trader without a blueprint");
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.MARKET), "not in the chronicle");
		traders.forEach(net.minecraft.world.entity.Entity::discard);
		helper.succeed();
	}

	/** A trade route: once a day a caravan takes the other village what it's waiting for (keeping some back), and it arrives in its storehouse. */
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravansCarryWhatAnotherVillageNeeds")
	public void caravansCarryWhatAnotherVillageNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		long travel = io.github.jcondedata.aliveworkplace.hall.Caravans.MIN_TRAVEL;
		VillageHalls.RADIUS = 6;
		io.github.jcondedata.aliveworkplace.hall.Caravans.MIN_TRAVEL = 0;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.hall.Caravans.MIN_TRAVEL = travel;
		});
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(5, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.CHEST);
		Container ours = helper.getBlockEntity(new BlockPos(5, 2, 5));
		ours.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		ours.setItem(1, new ItemStack(Items.BREAD, 10));
		helper.setBlock(new BlockPos(18, 2, 18), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(16, 2, 18), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(16, 2, 20), Blocks.CHEST);
		Container theirs = helper.getBlockEntity(new BlockPos(16, 2, 20));
		BlockPos a = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos b = helper.absolutePos(new BlockPos(18, 2, 18));
		var data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
		helper.runAfterDelay(2, () -> {
			data.setWants(a, Component.literal("Ashford"), java.util.List.of());
			data.setWants(b, Component.literal("Bramble"), java.util.List.of(new io.github.jcondedata.aliveworkplace.hall.Caravans.Want(Items.OAK_LOG, 32),
				new io.github.jcondedata.aliveworkplace.hall.Caravans.Want(Items.BREAD, 16)));
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.Caravans.neighbours(level, a).stream().anyMatch(v -> v.hall().equals(b)), "B isn't a neighbour");
			helper.assertTrue(data.toggleRoute(a, b), "no route");
			io.github.jcondedata.aliveworkplace.hall.Caravans.round(level, a, null);
			helper.assertTrue(ours.countItem(Items.OAK_LOG) == 32 && ours.countItem(Items.BREAD) == 10, "loaded: " + ours.countItem(Items.OAK_LOG) + " logs, "
				+ ours.countItem(Items.BREAD) + " bread (bread is short, so it stays)");
			io.github.jcondedata.aliveworkplace.hall.Caravans.round(level, a, null);
			helper.assertTrue(ours.countItem(Items.OAK_LOG) == 32, "two caravans in a day");
		});
		helper.runAfterDelay(40, () -> {
			io.github.jcondedata.aliveworkplace.hall.Caravans.round(level, b, null);
			helper.assertTrue(theirs.countItem(Items.OAK_LOG) == 32, "arrived: " + theirs.countItem(Items.OAK_LOG));
			var chronicleA = ((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(a)).chronicle();
			var chronicleB = ((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(b)).chronicle();
			helper.assertTrue(chronicleA.stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.CARAVAN)
				&& chronicleB.stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.CARAVAN), "not in the chronicles");
			// A hall taken away leaves the list, and its routes go.
			helper.setBlock(new BlockPos(18, 2, 18), Blocks.AIR);
			helper.assertTrue(data.village(b) == null && data.routesFrom(a).isEmpty(), "B is still on the list");
			helper.succeed();
		});
	}

	/** Ranks go by villagers, finished buildings and research; each pays; a rank up is celebrated and remembered. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aVillageRanksUp")
	public void aVillageRanksUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		var ranks = io.github.jcondedata.aliveworkplace.hall.VillageRanks.class;
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageRanks.rank(new io.github.jcondedata.aliveworkplace.hall.VillageRanks.Score(9, 20, 10))
			== io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.HAMLET, "9 villagers");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageRanks.rank(new io.github.jcondedata.aliveworkplace.hall.VillageRanks.Score(22, 14, 3))
			== io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN, "a town");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageRanks.rank(new io.github.jcondedata.aliveworkplace.hall.VillageRanks.Score(40, 30, 9))
			== io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.CITY, "a city");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageRanks.questRewardFactor(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN) == 1.5f
			&& io.github.jcondedata.aliveworkplace.hall.VillageRanks.caravanRoutes(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.CITY) == 6, "perks");
		var sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		java.util.List<io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement> placed = new java.util.ArrayList<>();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			placed.forEach(sites::forgetFinished);
		});
		var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
		int before = io.github.jcondedata.aliveworkplace.hall.VillageRanks.score(level, hall, 0).buildings();
		for (int i = 0; i < 5; i++) {
			var placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(),
				helper.absolutePos(new BlockPos(2 + i * 3, 2, 18)), net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
			placed.add(placement);
			sites.recordFinished(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.PARK_BENCH.id(), placement, java.util.UUID.randomUUID());
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageRanks.score(level, hall, 10).buildings() == before + 5, "buildings");
		var now = io.github.jcondedata.aliveworkplace.hall.VillageRanks.round(level, hall, entity, 10);
		helper.assertTrue(now.ordinal() >= io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE.ordinal() && entity.rank() == now, "rank: " + now);
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.RANK), "not in the chronicle");
		helper.assertTrue(!level.getEntitiesOfClass(net.minecraft.world.entity.projectile.FireworkRocketEntity.class, new net.minecraft.world.phys.AABB(hall).inflate(8)).isEmpty(),
			"no fireworks");
		helper.succeed();
	}

	/** Mercenaries: hired at the hall for emeralds, three guards in iron turn up; one band at a time; at their time they leave. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "mercenaries")
	public void mercenariesComeAndGo(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,
			io.github.jcondedata.aliveworkplace.guard.Mercenaries.PRICE_EMERALDS));
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			try {
				var said = io.github.jcondedata.aliveworkplace.guard.Mercenaries.hire(level, hall, player);
				helper.assertTrue(said.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
					&& t.getKey().equals("message.aliveworkplace.mercenaries.hired"), "not hired: " + said.getString());
				var band = io.github.jcondedata.aliveworkplace.guard.Mercenaries.near(level, hall);
				helper.assertTrue(band.size() == io.github.jcondedata.aliveworkplace.guard.Mercenaries.BAND, "band of " + band.size());
				helper.assertTrue(band.stream().allMatch(v -> io.github.jcondedata.aliveworkplace.guard.Guards.isGuard(v)
					&& v.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD)), "not armed guards");
				helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 0, "the emeralds weren't paid");
				var again = io.github.jcondedata.aliveworkplace.guard.Mercenaries.hire(level, hall, player);
				helper.assertTrue(again.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
					&& t.getKey().equals("message.aliveworkplace.mercenaries.already"), "a second band: " + again.getString());
				for (var merc : band) {
					merc.setAttached(ModAttachments.MERCENARY_UNTIL, level.getGameTime());
					io.github.jcondedata.aliveworkplace.guard.Mercenaries.tick(merc);
				}
				helper.assertTrue(band.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved), "the band didn't leave");
				helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Mercenaries.near(level, hall).isEmpty(), "mercenaries still here");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}

	/**
	 * A festival called with a cake: planned for today; after work the villagers feast from the store, a player in the
	 * village is a Hero of the Village, moods lift, the villagers gather at the bell, and fireworks can fly.
	 */
	@GameTest(template = AREA, timeoutTicks = 600, batch = "aFestivalIsHeld")
	public void aFestivalIsHeld(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.hall.Festivals.forget();
		});
		ServerLevel level = helper.getLevel();
		helper.setDayTime(1000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos bell = new BlockPos(4, 2, 4);
		helper.setBlock(bell, Blocks.BELL);
		helper.setBlock(new BlockPos(17, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 5), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(17, 2, 5));
		chest.setItem(0, new ItemStack(Items.BREAD, 10));
		chest.setItem(1, new ItemStack(Items.COOKED_BEEF, 10));
		java.util.List<Villager> villagers = new java.util.ArrayList<>();
		for (int i = 0; i < 3; i++) {
			villagers.add(helper.spawn(EntityType.VILLAGER, new BlockPos(15 + i, 2, 17)));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos stand = helper.absolutePos(new BlockPos(10, 2, 10));
		player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		player.getInventory().add(new ItemStack(Items.CAKE));
		player.getAbilities().instabuild = false; // (the mock player is in creative)
		BlockPos hall = helper.absolutePos(HALL);
		boolean[] ready = {false};
		helper.runAfterDelay(5, () -> {
			var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			io.github.jcondedata.aliveworkplace.hall.Festivals.call(level, hall, player);
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			helper.assertTrue(entity.festivalDay() == today, "not planned for today: " + entity.festivalDay() + " vs " + today);
			helper.assertFalse(player.getInventory().contains(new ItemStack(Items.CAKE)), "the cake wasn't taken");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.Festivals.nextDay(level, hall, entity) == today, "next festival");
			helper.setDayTime(9500);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.Festivals.isOn(level, hall), "the festival isn't on after work");
			io.github.jcondedata.aliveworkplace.hall.Festivals.round(level, hall, entity, villagers.size());
			for (Villager v : villagers) {
				helper.assertTrue(v.hasAttached(ModAttachments.FESTIVAL_DAY) && v.hasAttached(ModAttachments.LAST_MEAL), "didn't feast: " + v);
				var mood = io.github.jcondedata.aliveworkplace.people.Moods.work(level, v);
				helper.assertTrue(mood.good().stream().anyMatch(c -> c.getString().contains("festival")), "mood: " + mood.good());
			}
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.FESTIVAL), "not in the chronicle");
			helper.assertTrue(player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE), "the player isn't a hero");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.Festivals.launch(level, helper.absolutePos(bell).above()).isAlive(), "no firework");
			ready[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ready[0], "not yet");
			helper.assertTrue(villagers.stream().anyMatch(v -> v.position().closerThan(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(bell)), 7)),
				"nobody came to the bell");
		});
	}

	/** A Village Ledger bound at the hall opens the hall's screen from afar; unbound, it doesn't. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villageLedger")
	public void aVillageLedgerOpensTheHallFromAfar(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		ItemStack ledger = new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.VILLAGE_LEDGER);
		player.setItemInHand(InteractionHand.MAIN_HAND, ledger);
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(ledger.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "an unbound ledger opened something");
			io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.bind(level, player, ledger, helper.absolutePos(HALL));
			var bound = ledger.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.LEDGER);
			helper.assertTrue(bound != null && bound.hall().pos().equals(helper.absolutePos(HALL)), "not bound: " + bound);
			BlockPos far = helper.absolutePos(new BlockPos(20, 2, 20));
			player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			helper.assertTrue(ledger.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "the bound ledger didn't open");
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "no hall screen: " + player.containerMenu);
			player.closeContainer();
			helper.succeed();
		});
	}
}
