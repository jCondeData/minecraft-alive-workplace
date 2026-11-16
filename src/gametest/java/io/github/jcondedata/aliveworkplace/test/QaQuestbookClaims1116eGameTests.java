package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.compat.ftbquests.FtbQuestsBook;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMoments;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1116) for 26.8, the FTB Quests questbook, from the player's side: what each quest's description tells
 * the player is checked against the running mod, written from the questbook's TEXT (the spec), not the code.
 * Recipes the quests spell out are crafted through the loaded RecipeManager exactly as described ("together" ones in
 * scattered slots and both orders). Thresholds are tested at their boundary on both sides through the mod's own entry
 * points (the hall's rank, festival, treasury and growth rounds, the Steward's appointment, the cake call, edicts, the
 * caravans' list, the raid clock). Advancement tasks are checked to reach the hall's owner and a player 64 blocks from
 * the hall but not one at 65 ("for the hall's owner and for anyone within 64 blocks of the hall"). The reward
 * commands are read from the chapter files and run as FTB Quests runs them. Disagreements between text and mod are in
 * {@code QaQuestbookClaims1116eBugTests} (not registered). Each behaviour test is alone in its batch.
 */
public class QaQuestbookClaims1116eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);

	// ================================================================================== helpers

	static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	/** What the crafting grid of {@code w} by {@code h} makes from {@code ids} (row by row, "" for an empty slot). */
	static ItemStack craft(GameTestHelper helper, int w, int h, String... ids) {
		ServerLevel level = helper.getLevel();
		List<ItemStack> stacks = new ArrayList<>();
		for (String id : ids) {
			if (id.isEmpty()) {
				stacks.add(ItemStack.EMPTY);
			} else {
				Item it = item(id);
				helper.assertTrue(it != Items.AIR, "no such item: " + id);
				stacks.add(new ItemStack(it));
			}
		}
		CraftingInput input = CraftingInput.of(w, h, stacks);
		return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	/** The 3 by 3 grid with {@code ids} in the given slots (0 top left, 8 bottom right), everything else empty. */
	static ItemStack craftIn(GameTestHelper helper, int[] slots, String... ids) {
		String[] grid = {"", "", "", "", "", "", "", "", ""};
		for (int i = 0; i < slots.length; i++) {
			grid[slots[i]] = ids[i];
		}
		return craft(helper, 3, 3, grid);
	}

	static void assertMakes(GameTestHelper helper, ItemStack made, String id, int count, String what) {
		helper.assertTrue(made.is(item(id)), what + " makes " + (made.isEmpty() ? "nothing" : made.toString()) + ", not " + id);
		if (count > 0) {
			helper.assertTrue(made.getCount() == count, what + " makes " + made.getCount() + ", not " + count);
		}
	}

	/** "X and Y together": anywhere in the grid, in either order. */
	static void together(GameTestHelper helper, String result, String... ids) {
		int[][] layouts = ids.length == 2 ? new int[][] {{0, 8}, {5, 1}}
			: ids.length == 3 ? new int[][] {{2, 4, 6}, {7, 0, 5}}
			: new int[][] {{0, 2, 3, 5, 6, 8}, {8, 7, 4, 1, 0, 3}};
		for (int[] layout : layouts) {
			assertMakes(helper, craftIn(helper, layout, ids), result, 0, String.join(" + ", ids) + " in slots " + java.util.Arrays.toString(layout));
		}
		String[] reversed = new String[ids.length];
		for (int i = 0; i < ids.length; i++) {
			reversed[i] = ids[ids.length - 1 - i];
		}
		assertMakes(helper, craftIn(helper, layouts[0], reversed), result, 0, "reversed " + String.join(" + ", reversed));
	}

	static AdvancementHolder advancement(ServerPlayer player, String path) {
		return player.getServer().getAdvancements().get(AliveWorkplace.id(path));
	}

	static boolean has(ServerPlayer player, String path) {
		AdvancementHolder advancement = advancement(player, path);
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	/** A survival mock player at {@code absolute}, logged out after the test. */
	static ServerPlayer playerAt(GameTestHelper helper, Vec3 absolute) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(absolute);
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		return player;
	}

	/** A player standing {@code blocks} east of the hall, on its level. */
	static ServerPlayer east(GameTestHelper helper, BlockPos hall, int blocks) {
		return playerAt(helper, hall.offset(blocks, 0, 0).getCenter());
	}

	/** The test's own hall, alone: other batches' players, halls and finished buildings near it gone. */
	static VillageHallBlockEntity hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		VillageNeeds.forget();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		BlockPos at = entity.getBlockPos();
		Leftovers.after(helper, () -> {
			if (helper.getLevel().getBlockEntity(at) instanceof VillageHallBlockEntity e) {
				e.setEdicts(List.of());
			}
			VillageHalls.forgetNearest();
			VillageNeeds.forget();
			CivicEffects.forget();
		});
		return entity;
	}

	/** The day time put back after the test. */
	static void keepTime(GameTestHelper helper) {
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> helper.getLevel().setDayTime(time));
	}

	/** Sets the clock to {@code timeOfDay} on {@code day} (as {@link Chronicle#day} counts days). */
	static void at(ServerLevel level, long day, long timeOfDay) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + timeOfDay);
	}

	static Research.State levels(int n) {
		return new Research.State(Map.of("qa_topic", n), Optional.empty(), 0, false);
	}

	/** {@code n} finished decorations recorded round {@code hall}, forgotten after the test. */
	static List<BlueprintData.Placement> finished(GameTestHelper helper, BlockPos hall, int n) {
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BlueprintData.Placement> placed = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(),
				hall.offset(4 + (i % 5) * 6, 0, 4 + (i / 5) * 6), Rotation.NONE, Mirror.NONE);
			placed.add(placement);
			sites.recordFinished(StarterBlueprints.PARK_BENCH.id(), placement, UUID.randomUUID());
		}
		Leftovers.after(helper, () -> placed.forEach(sites::forgetFinished));
		return placed;
	}

	/** Every {@code /workplace unlock {p} <id>} reward in the questbook's chapters, by chapter file. */
	static Set<ResourceLocation> rewardUnlocks() {
		Set<ResourceLocation> ids = new LinkedHashSet<>();
		Pattern unlock = Pattern.compile("command: \"/workplace unlock \\{p\\} ([a-z0-9_:/.-]+)\"");
		try {
			for (FtbQuestsBook.Chapter chapter : FtbQuestsBook.manifest()) {
				String text;
				try (InputStream in = FtbQuestsBook.resource(chapter.file())) {
					text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}
				Matcher m = unlock.matcher(text);
				while (m.find()) {
					ids.add(ResourceLocation.parse(m.group(1)));
				}
			}
		} catch (IOException e) {
			throw new AssertionError("can't read the questbook", e);
		}
		return ids;
	}

	/**
	 * Runs a questbook reward command as FTB Quests does with {@code elevate_perms}: as the player, with operator
	 * permission ({@code {p}} is the player; here {@code @s}, as every mock player has the same name).
	 */
	static void reward(ServerLevel level, ServerPlayer player, ResourceLocation id) {
		level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
			"workplace unlock @s " + id);
	}

	// ================================================================================== recipes

	/**
	 * "A Blueprint Table": "blue dye in the top middle, a cartography table in the center with planks on its left and
	 * right, and planks in the two bottom corners" makes a Blueprint Table, with oak planks and with mixed planks.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaBlueprintTableCraftsAsTheQuestSays(GameTestHelper helper) {
		assertMakes(helper, craft(helper, 3, 3,
			"", "minecraft:blue_dye", "",
			"minecraft:oak_planks", "minecraft:cartography_table", "minecraft:oak_planks",
			"minecraft:oak_planks", "", "minecraft:oak_planks"), "aliveworkplace:blueprint_table", 0, "the oak layout");
		assertMakes(helper, craft(helper, 3, 3,
			"", "minecraft:blue_dye", "",
			"minecraft:birch_planks", "minecraft:cartography_table", "minecraft:spruce_planks",
			"minecraft:cherry_planks", "", "minecraft:oak_planks"), "aliveworkplace:blueprint_table", 0, "mixed planks");
		helper.succeed();
	}

	/** "craft paper and blue dye to get two": two Blank Blueprints, anywhere in the grid. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaPaperAndBlueDyeMakeTwoBlankBlueprints(GameTestHelper helper) {
		assertMakes(helper, craftIn(helper, new int[] {0, 8}, "minecraft:paper", "minecraft:blue_dye"), "aliveworkplace:blank_blueprint", 2, "paper + blue dye");
		assertMakes(helper, craftIn(helper, new int[] {4, 3}, "minecraft:blue_dye", "minecraft:paper"), "aliveworkplace:blank_blueprint", 2, "blue dye + paper");
		helper.succeed();
	}

	/**
	 * "No village nearby?": "three white wool on top; a hay bale, a block of emerald and a hay bale in the middle; planks,
	 * a chest and planks at the bottom" makes a Settler's Wagon.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSettlersWagonCraftsAsTheQuestSays(GameTestHelper helper) {
		assertMakes(helper, craft(helper, 3, 3,
			"minecraft:white_wool", "minecraft:white_wool", "minecraft:white_wool",
			"minecraft:hay_block", "minecraft:emerald_block", "minecraft:hay_block",
			"minecraft:oak_planks", "minecraft:chest", "minecraft:oak_planks"), "aliveworkplace:settlers_wagon", 0, "the wagon's layout");
		helper.succeed();
	}

	/**
	 * Every "X and Y together" recipe the quests give (Guide Book, Village Ledger, Field Marker, Drop Box, Quarry Marker,
	 * Scan Tool, Shape Planner, City Plan, Guild Charter, Rally Banner with any banner, Patrol Map) crafts anywhere in the
	 * grid, in either order.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryTogetherRecipeCraftsAnywhereInTheGrid(GameTestHelper helper) {
		together(helper, "aliveworkplace:guide_book", "minecraft:book", "minecraft:wheat");                    // The Guide Book
		together(helper, "aliveworkplace:village_ledger", "minecraft:book", "minecraft:emerald");              // The Village Ledger
		together(helper, "aliveworkplace:field_marker", "minecraft:stick", "minecraft:wheat_seeds", "minecraft:paper"); // More fields
		together(helper, "aliveworkplace:drop_box", "minecraft:barrel", "minecraft:hopper");                   // A Drop Box
		together(helper, "aliveworkplace:quarry_marker", "minecraft:stick", "minecraft:red_dye", "minecraft:paper"); // A Miner
		together(helper, "aliveworkplace:scan_tool", "aliveworkplace:blank_blueprint", "minecraft:spyglass"); // the Scan Tool
		together(helper, "aliveworkplace:shape_planner", "aliveworkplace:blank_blueprint", "minecraft:compass"); // The Shape Planner
		together(helper, "aliveworkplace:city_plan", "minecraft:map", "aliveworkplace:blank_blueprint", "minecraft:heart_of_the_sea"); // The City Plan
		together(helper, "aliveworkplace:guild_charter", "minecraft:paper", "minecraft:paper", "minecraft:paper",
			"minecraft:emerald", "minecraft:gold_ingot", "minecraft:red_dye");                                 // The Guild Charter
		together(helper, "aliveworkplace:rally_banner", "minecraft:white_banner", "minecraft:iron_ingot");    // "any banner"
		together(helper, "aliveworkplace:rally_banner", "minecraft:lime_banner", "minecraft:iron_ingot");
		together(helper, "aliveworkplace:patrol_map", "minecraft:map", "minecraft:red_dye");                   // A map and red dye
		helper.succeed();
	}

	/**
	 * "A Training Dummy": "wool in the top middle; a stick, a hay bale and a stick across the middle; a stick in the bottom
	 * middle", with white wool and with colored wool; "A village Trainer": "a target block on top of three planks".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTrainingDummyAndTrainingPostCraftAsTheQuestsSay(GameTestHelper helper) {
		for (String wool : List.of("minecraft:white_wool", "minecraft:red_wool")) {
			assertMakes(helper, craft(helper, 3, 3,
				"", wool, "",
				"minecraft:stick", "minecraft:hay_block", "minecraft:stick",
				"", "minecraft:stick", ""), "aliveworkplace:training_dummy", 0, "the dummy with " + wool);
		}
		assertMakes(helper, craft(helper, 3, 2,
			"", "minecraft:target", "",
			"minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks"), "aliveworkplace:training_post", 0, "a target on three planks");
		helper.succeed();
	}

	// ================================================================================== ranks

	/**
	 * "A Village at last": "10 villagers and 5 finished buildings ... decorations too". 9 villagers with 5 buildings and
	 * 10 with 4 stay a Hamlet and grant nothing; 10 and 5 make a Village, and its quest completes "for the hall's owner
	 * [online, far away] and for anyone within 64 blocks of the hall": the player 64 blocks away gets it, the one at 65
	 * doesn't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaQb1116eRankVillage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaQb1116eRankVillage")
	public void qaVillageRankNeedsTenAndFiveAndReachesOwnerAnd64Blocks(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			ServerPlayer owner = east(helper, hall, 400);
			ServerPlayer at64 = east(helper, hall, 64);
			ServerPlayer at65 = east(helper, hall, 65);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setResearch(levels(0));
			String moment = VillageMoments.PREFIX + VillageMoments.rank(VillageRanks.Rank.VILLAGE);
			List<BlueprintData.Placement> five = finished(helper, hall, 5);
			helper.assertTrue(VillageRanks.score(level, hall, 0).buildings() == 5, "setup: buildings " + VillageRanks.score(level, hall, 0).buildings());
			helper.assertTrue(VillageRanks.round(level, hall, entity, 9) == VillageRanks.Rank.HAMLET, "9 villagers and 5 buildings made a Village");
			BuildSiteManager.get(level).forgetFinished(five.get(4));
			helper.assertTrue(VillageRanks.round(level, hall, entity, 10) == VillageRanks.Rank.HAMLET, "10 villagers and 4 buildings made a Village");
			helper.assertFalse(has(owner, moment) || has(at64, moment), "\"A Village at last\" completed while still a Hamlet");
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.PARK_BENCH.id(), five.get(4), UUID.randomUUID());
			helper.assertTrue(VillageRanks.round(level, hall, entity, 10) == VillageRanks.Rank.VILLAGE, "10 villagers and 5 buildings aren't a Village");
			helper.assertTrue(has(owner, moment), "the hall's owner, online 400 blocks away, didn't get the Village rank's advancement");
			helper.assertTrue(has(at64, moment), "a player 64 blocks from the hall didn't get the Village rank's advancement");
			helper.assertFalse(has(at65, moment), "a player 65 blocks from the hall got the Village rank's advancement");
			helper.succeed();
		});
	}

	/**
	 * "A Town": "20 villagers, 12 finished buildings and 3 levels of research": one short of each stays a Village; all
	 * three make a Town and both the Village and the Town quests complete for the owner (a Hamlet jumping straight to
	 * Town still finishes "A Village at last").
	 */
	//$ gametest_ticks_batch AREA '100' '"qaQb1116eRankTown"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaQb1116eRankTown")
	public void qaTownRankNeedsTwentyTwelveAndThreeResearch(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			ServerPlayer owner = east(helper, hall, 3);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setRank(VillageRanks.Rank.HAMLET);
			String village = VillageMoments.PREFIX + VillageMoments.rank(VillageRanks.Rank.VILLAGE);
			String town = VillageMoments.PREFIX + VillageMoments.rank(VillageRanks.Rank.TOWN);
			List<BlueprintData.Placement> twelve = finished(helper, hall, 12);
			helper.assertTrue(VillageRanks.score(level, hall, 0).buildings() == 12, "setup: buildings " + VillageRanks.score(level, hall, 0).buildings());
			entity.setResearch(levels(2));
			helper.assertTrue(VillageRanks.round(level, hall, entity, 20) == VillageRanks.Rank.VILLAGE, "2 research levels made a Town");
			entity.setResearch(levels(3));
			helper.assertTrue(VillageRanks.round(level, hall, entity, 19) == VillageRanks.Rank.VILLAGE, "19 villagers made a Town");
			BuildSiteManager.get(level).forgetFinished(twelve.get(11));
			helper.assertTrue(VillageRanks.round(level, hall, entity, 20) == VillageRanks.Rank.VILLAGE, "11 buildings made a Town");
			helper.assertFalse(has(owner, town), "\"A Town\" completed while still a Village");
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.PARK_BENCH.id(), twelve.get(11), UUID.randomUUID());
			helper.assertTrue(VillageRanks.round(level, hall, entity, 20) == VillageRanks.Rank.TOWN, "20 villagers, 12 buildings and 3 research aren't a Town");
			helper.assertTrue(has(owner, town), "the owner didn't get the Town rank's advancement");
			helper.assertTrue(has(owner, village), "the owner didn't get the Village rank's advancement on the way");
			helper.succeed();
		});
	}

	/** "A City": "35 villagers, 25 finished buildings and 7 levels of research": one short of each stays a Town. */
	//$ gametest_ticks_batch AREA '100' '"qaQb1116eRankCity"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaQb1116eRankCity")
	public void qaCityRankNeedsThirtyFiveTwentyFiveAndSevenResearch(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			ServerPlayer owner = east(helper, hall, 3);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setRank(VillageRanks.Rank.TOWN);
			String city = VillageMoments.PREFIX + VillageMoments.rank(VillageRanks.Rank.CITY);
			List<BlueprintData.Placement> all = finished(helper, hall, 25);
			helper.assertTrue(VillageRanks.score(level, hall, 0).buildings() == 25, "setup: buildings " + VillageRanks.score(level, hall, 0).buildings());
			entity.setResearch(levels(6));
			helper.assertTrue(VillageRanks.round(level, hall, entity, 35) == VillageRanks.Rank.TOWN, "6 research levels made a City");
			entity.setResearch(levels(7));
			helper.assertTrue(VillageRanks.round(level, hall, entity, 34) == VillageRanks.Rank.TOWN, "34 villagers made a City");
			BuildSiteManager.get(level).forgetFinished(all.get(24));
			helper.assertTrue(VillageRanks.round(level, hall, entity, 35) == VillageRanks.Rank.TOWN, "24 buildings made a City");
			helper.assertFalse(has(owner, city), "\"A City\" completed while still a Town");
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.PARK_BENCH.id(), all.get(24), UUID.randomUUID());
			helper.assertTrue(VillageRanks.round(level, hall, entity, 35) == VillageRanks.Rank.CITY, "35 villagers, 25 buildings and 7 research aren't a City");
			helper.assertTrue(has(owner, city), "the owner didn't get the City rank's advancement");
			helper.succeed();
		});
	}

	// ================================================================================== the Blueprint Table

	/**
	 * Every build a quest says to "pick" at the Blueprint Table with no word of unlocking it (chapters 1 and 2 and the
	 * Library, Post Office and Clinic) is unlocked in a new village; the Starter Cottage and the Village Hall even before
	 * the village has a hall; the Schoolhouse, whose quest says "Its name is gray at the Blueprint Table at first: it's
	 * locked", is locked (named, not a ???).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaQb1116eTablePicks"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaQb1116eTablePicks")
	public void qaEveryBuildAQuestSaysToPickIsOnANewVillagesTable(GameTestHelper helper) {
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			VillageHalls.forgetNearest();
			helper.assertTrue(hall.equals(BlueprintUnlocks.village(level, hall.offset(3, 0, 3))), "a Table by the hall isn't in its village");
			for (String id : List.of("starter_cottage", "village_hall")) {
				ResourceLocation rl = AliveWorkplace.id(id);
				helper.assertTrue(BlueprintUnlocks.state(level, null, rl) == BlueprintUnlocks.State.UNLOCKED,
					id + " isn't on a Table with no village yet: " + BlueprintUnlocks.state(level, null, rl));
			}
			for (String id : List.of("starter_cottage", "village_hall", "farmstead", "storehouse", "kitchen", "stone_house", "terrace",
					"builders_workshop", "guard_house", "carpenters_workshop", "smithy", "masons_yard", "library", "post_office", "clinic")) {
				ResourceLocation rl = AliveWorkplace.id(id);
				helper.assertTrue(BlueprintLibrary.get(level, rl).isPresent(), "no blueprint " + id);
				helper.assertTrue(BlueprintUnlocks.state(level, hall, rl) == BlueprintUnlocks.State.UNLOCKED,
					id + " (a quest says to pick it) isn't unlocked in a new village: " + BlueprintUnlocks.state(level, hall, rl));
			}
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.SCHOOLHOUSE.id()) == BlueprintUnlocks.State.LOCKED,
				"the Schoolhouse isn't gray (locked, named) at first: " + BlueprintUnlocks.state(level, hall, StarterBlueprints.SCHOOLHOUSE.id()));
			helper.succeed();
		});
	}

	/**
	 * Every reward command in the book ("this quest's reward puts X in your Blueprint Table") names a real blueprint and,
	 * run for the hall's owner standing in the village, leaves X unlocked at the village's Table.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaQb1116eRewardsHere"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaQb1116eRewardsHere")
	public void qaEveryRewardUnlockPutsItsBuildOnTheVillagesTable(GameTestHelper helper) {
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			VillageHalls.forgetNearest();
			ServerPlayer owner = east(helper, hall, 3);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			Set<ResourceLocation> ids = rewardUnlocks();
			helper.assertTrue(ids.size() >= 12, "only " + ids.size() + " unlock rewards found in the book: " + ids);
			for (ResourceLocation id : ids) {
				helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "a reward unlocks " + id + ", which isn't a blueprint");
				reward(level, owner, id);
				helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == BlueprintUnlocks.State.UNLOCKED,
					"the reward for " + id + " didn't put it on the village's Table: " + BlueprintUnlocks.state(level, hall, id));
			}
			helper.succeed();
		});
	}

	/**
	 * "Buy the Market Stall blueprint from an Apprentice Builder (level 2) for 8 emeralds"; "Buy the Lookout Tower blueprint
	 * from a Journeyman Builder (level 3) for 12 emeralds": the builders' own trades.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaQb1116eBuilderSales"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaQb1116eBuilderSales")
	public void qaApprenticeAndJourneymanBuildersSellTheirBlueprintsAtTheQuestsPrices(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (int[] want : new int[][] {{2, 8}, {3, 12}}) {
			ResourceLocation id = want[0] == 2 ? StarterBlueprints.MARKET_STALL.id() : StarterBlueprints.LOOKOUT_TOWER.id();
			Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + want[0], 2, 4));
			builder.setNoAi(true);
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(want[0]));
			MerchantOffer found = null;
			for (MerchantOffer offer : builder.getOffers()) {
				if (BlueprintItem.data(offer.getResult()).map(d -> d.structure().equals(id)).orElse(false)) {
					found = offer;
				}
			}
			helper.assertTrue(found != null, "a level " + want[0] + " Builder doesn't sell " + id);
			helper.assertTrue(found.getCostA().is(Items.EMERALD) && found.getCostA().getCount() == want[1] && found.getCostB().isEmpty(),
				id + " costs " + found.getCostA() + " + " + found.getCostB() + ", not " + want[1] + " emeralds");
		}
		helper.succeed();
	}

	// ================================================================================== the Steward

	/**
	 * "Appoint a Steward": a Builder of Journeyman level or higher, next to the hall, sneak-right-clicked with the hall's
	 * City Plan. An Apprentice is refused (no advancement); the Journeyman becomes the Steward and the quest completes for
	 * the owner and a player 64 blocks away, not one 65 blocks away.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaQb1116eSteward"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaQb1116eSteward")
	public void qaOnlyAJourneymanBuilderByTheHallBecomesSteward(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		Leftovers.stewardSites(helper);
		Villager apprentice = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 4));
		Villager journeyman = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			ServerPlayer owner = playerAt(helper, hall.offset(1, 0, 3).getCenter());
			ServerPlayer at64 = east(helper, hall, 64);
			ServerPlayer at65 = east(helper, hall, 65);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
			CityPlanItem.bind(level, owner, plan, hall);
			String moment = VillageMoments.PREFIX + VillageMoments.STEWARD;

			apprentice.setVillagerData(apprentice.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
			apprentice.refreshBrain(level);
			apprentice.setVillagerXp(69);
			helper.assertFalse(Stewards.appoint(owner, apprentice, plan) == InteractionResult.SUCCESS, "an Apprentice Builder was made Steward");
			helper.assertTrue(apprentice.getVillagerData().getProfession() == ModVillagers.BUILDER, "the Apprentice lost his job");
			helper.assertFalse(has(owner, moment), "\"Appoint a Steward\" completed for a refused Apprentice");

			journeyman.setVillagerData(journeyman.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(3));
			journeyman.refreshBrain(level);
			journeyman.setVillagerXp(70);
			helper.assertTrue(Stewards.appoint(owner, journeyman, plan) == InteractionResult.SUCCESS, "a Journeyman Builder by the hall wasn't made Steward");
			helper.assertTrue(Stewards.isSteward(journeyman), "the Journeyman isn't a Steward");
			helper.assertTrue(has(owner, moment), "the owner didn't get the Steward advancement");
			helper.assertTrue(has(at64, moment), "a player 64 blocks from the hall didn't get the Steward advancement");
			helper.assertFalse(has(at65, moment), "a player 65 blocks from the hall got the Steward advancement");
			helper.succeed();
		});
	}

	// ================================================================================== festivals

	/**
	 * "The first festival": "Every eight days, a village with a hall and at least six villagers holds a festival". On its
	 * day, five villagers hold none and six do; the day before holds none; the feast completes the quest for the owner
	 * (far away) and a player 64 blocks away, not one 65 blocks away; and the next one is eight days later.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaQb1116eFestivalDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaQb1116eFestivalDay")
	public void qaAFestivalNeedsSixVillagersAndComesEveryEightDays(GameTestHelper helper) {
		keepTime(helper);
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true; // off in the GameTest server
		Leftovers.after(helper, () -> Festivals.ENABLED = festivals);
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			ServerPlayer owner = east(helper, hall, 400);
			ServerPlayer at64 = east(helper, hall, 64);
			ServerPlayer at65 = east(helper, hall, 65);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue(Festivals.every(entity) == 8, "festivals every " + Festivals.every(entity) + " days, not eight");
			long offset = Festivals.offset(level, hall);
			long day = Chronicle.day(level) + 2;
			while (Math.floorMod(day - offset, 8) != 0) {
				day++;
			}
			String moment = VillageMoments.PREFIX + VillageMoments.FESTIVAL;
			at(level, day - 1, 1000);
			Festivals.round(level, hall, entity, 6);
			helper.assertTrue(entity.festivalDay() < day - 1, "a festival planned the day before the village's festival day");
			at(level, day, 1000);
			Festivals.round(level, hall, entity, 5);
			helper.assertTrue(entity.festivalDay() < day, "five villagers held a festival");
			Festivals.round(level, hall, entity, 6);
			helper.assertTrue(entity.festivalDay() == day, "six villagers on the festival day held none: " + entity.festivalDay() + " vs " + day);
			helper.assertFalse(has(owner, moment), "\"The first festival\" completed before the feast");
			at(level, day, Festivals.START + 100);
			Festivals.round(level, hall, entity, 6);
			helper.assertTrue(Festivals.isOn(level, hall), "the festival isn't on in the afternoon");
			helper.assertTrue(has(owner, moment), "the owner didn't get the festival advancement");
			helper.assertTrue(has(at64, moment), "a player 64 blocks from the hall didn't get the festival advancement");
			helper.assertFalse(has(at65, moment), "a player 65 blocks from the hall got the festival advancement");
			at(level, day, Festivals.END + 2000);
			helper.assertTrue(Festivals.nextDay(level, hall, entity) == day + 8, "the next festival is on day " + Festivals.nextDay(level, hall, entity)
				+ ", not " + (day + 8));
			helper.succeed();
		});
	}

	/**
	 * "Carry a cake and click the firework rocket: the festival is held today or tomorrow (at most once every three
	 * days)." Called in the morning: today, and the cake is eaten. Two days later: refused, the cake kept. Three days
	 * after the call, in the afternoon: tomorrow. Later with no cake: refused.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaQb1116eFestivalCake"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaQb1116eFestivalCake")
	public void qaACakeCallsAFestivalTodayOrTomorrowAtMostEveryThreeDays(GameTestHelper helper) {
		keepTime(helper);
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true; // off in the GameTest server
		Leftovers.after(helper, () -> Festivals.ENABLED = festivals);
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			ServerPlayer player = east(helper, hall, 3);
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			long day = Chronicle.day(level) + 2;
			at(level, day, 1000);
			player.getInventory().setItem(0, new ItemStack(Items.CAKE));
			Festivals.call(level, hall, player);
			helper.assertTrue(entity.festivalDay() == day, "a morning cake call didn't plan today's festival: " + entity.festivalDay() + " vs " + day);
			helper.assertTrue(player.getInventory().countItem(Items.CAKE) == 0, "the cake wasn't used");

			at(level, day + 2, 1000);
			player.getInventory().setItem(0, new ItemStack(Items.CAKE));
			Festivals.call(level, hall, player);
			helper.assertTrue(entity.festivalDay() == day, "a second festival called two days after the first call: " + entity.festivalDay());
			helper.assertTrue(player.getInventory().countItem(Items.CAKE) == 1, "a refused call took the cake");

			at(level, day + 3, Festivals.START + 1000);
			Festivals.call(level, hall, player);
			helper.assertTrue(entity.festivalDay() == day + 4, "an afternoon call three days on didn't plan tomorrow's: " + entity.festivalDay() + " vs " + (day + 4));
			helper.assertTrue(player.getInventory().countItem(Items.CAKE) == 0, "the second cake wasn't used");

			at(level, day + 7, 1000);
			Festivals.call(level, hall, player);
			helper.assertTrue(entity.festivalDay() == day + 4, "a festival called with no cake: " + entity.festivalDay());
			helper.succeed();
		});
	}

	// ================================================================================== food and babies

	/**
	 * "Food in store" and "A bed for a newcomer": each meal item counts once (bread, carrots, potatoes, baked potatoes,
	 * apples, cooked meat and fish), golden food, rotten flesh, spider eyes, raw chicken and cake don't; 15 meals is too
	 * little and 16 is enough; wellbeing exactly half is enough and less isn't; two grown villagers are needed; at most
	 * one baby a day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaQb1116eFood"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaQb1116eFood")
	public void qaABabyNeedsSixteenMealsHalfWellbeingTwoGrownAndADay(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
		Leftovers.storehouses(helper);
		BlockPos hallAt = new BlockPos(8, 2, 8);
		BlockPos chestAt = new BlockPos(12, 2, 10);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(12, 2, 8), ModBlocks.STOREHOUSE);
		helper.setBlock(chestAt, Blocks.CHEST);
		helper.setBlock(new BlockPos(4, 2, 12), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(4, 2, 13), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 5)).setNoAi(true);
		helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 5)).setNoAi(true);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallAt);
			VillageHalls.forgetNearest();
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of());
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
			Container chest = helper.getBlockEntity(chestAt);
			int slot = 0;
			// 15 meals of the kinds the quest names
			for (Object[] meal : new Object[][] {{Items.BREAD, 3}, {Items.CARROT, 2}, {Items.POTATO, 2}, {Items.BAKED_POTATO, 2}, {Items.APPLE, 2},
					{Items.COOKED_BEEF, 1}, {Items.COOKED_PORKCHOP, 1}, {Items.COOKED_COD, 1}, {Items.COOKED_SALMON, 1}}) {
				chest.setItem(slot++, new ItemStack((Item) meal[0], (Integer) meal[1]));
			}
			// and plenty of what the quest says isn't a meal
			for (Item not : List.of(Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.CHICKEN, Items.CAKE)) {
				chest.setItem(slot++, new ItemStack(not, not.getDefaultMaxStackSize()));
			}
			long now = level.getGameTime();
			VillageNeeds.Needs half = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 0.5f);
			helper.assertTrue(VillageGrowth.meals(level, VillageNeeds.store(level, hall)) == 15,
				"the store counts " + VillageGrowth.meals(level, VillageNeeds.store(level, hall)) + " meals, not 15");
			helper.assertTrue(VillageGrowth.blocker(level, hall, half, 0) == VillageGrowth.Blocker.FOOD,
				"15 meals (and many non-meals) let a baby come: " + VillageGrowth.blocker(level, hall, half, 0));
			chest.setItem(slot, new ItemStack(Items.COOKED_CHICKEN, 1));
			helper.assertTrue(VillageGrowth.blocker(level, hall, half, 0) == VillageGrowth.Blocker.NONE,
				"16 meals, a free bed, two grown villagers and half wellbeing: " + VillageGrowth.blocker(level, hall, half, 0));
			helper.assertTrue(VillageGrowth.blocker(level, hall, new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 0.49f), 0) == VillageGrowth.Blocker.WELLBEING,
				"wellbeing under half let a baby come");
			helper.assertTrue(VillageGrowth.blocker(level, hall, new VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, 1f), 0) == VillageGrowth.Blocker.TOO_FEW,
				"one grown villager could have a baby");
			helper.assertTrue(VillageGrowth.blocker(level, hall, half, now - VillageNeeds.DAY + 1) == VillageGrowth.Blocker.TOO_SOON,
				"a second baby less than a day after the last");
			helper.assertTrue(VillageGrowth.blocker(level, hall, half, now - VillageNeeds.DAY) == VillageGrowth.Blocker.NONE,
				"no baby a whole day after the last: " + VillageGrowth.blocker(level, hall, half, now - VillageNeeds.DAY));
			helper.succeed();
		});
	}

	// ================================================================================== edicts, treasury, raids, caravans

	/** "A Hamlet has room for one edict, a City for four": the second in a Hamlet and the fifth in a City are refused. */
	//$ gametest_ticks_batch AREA '60' '"qaQb1116eEdictRoom"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaQb1116eEdictRoom")
	public void qaAHamletHasRoomForOneEdictAndACityForFour(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		boolean enabled = Edicts.ENABLED;
		Edicts.setEnabled(true);
		Leftovers.after(helper, () -> Edicts.setEnabled(enabled));
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			ServerPlayer owner = east(helper, hall, 3);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setEdicts(List.of());
			entity.setRank(VillageRanks.Rank.HAMLET);
			List<String> five = List.of("long_shifts", "free_bread", "large_families", "tithe", "conscription");
			helper.assertTrue(Edicts.proclaim(level, hall, owner, Edicts.get(AliveWorkplace.id(five.get(0))).orElseThrow()).done(), "a Hamlet's first edict refused");
			helper.assertFalse(Edicts.proclaim(level, hall, owner, Edicts.get(AliveWorkplace.id(five.get(1))).orElseThrow()).done(), "a Hamlet took a second edict");
			entity.setRank(VillageRanks.Rank.CITY);
			for (int i = 1; i < 4; i++) {
				Edicts.Result r = Edicts.proclaim(level, hall, owner, Edicts.get(AliveWorkplace.id(five.get(i))).orElseThrow());
				helper.assertTrue(r.done(), "a City's edict " + (i + 1) + " (" + five.get(i) + ") refused: " + r.message().getString());
			}
			helper.assertFalse(Edicts.proclaim(level, hall, owner, Edicts.get(AliveWorkplace.id(five.get(4))).orElseThrow()).done(), "a City took a fifth edict");
			helper.assertTrue(entity.edicts().size() == 4, "a City holds " + entity.edicts().size() + " edicts");
			helper.succeed();
		});
	}

	/**
	 * "The treasury": "about a fifth of an emerald for each worker, a quarter more for each rank", and "holds only a
	 * stack of emeralds per rank": through the hall's daily takings round, 10 workers bring 2 emeralds a day in a Hamlet,
	 * 2.5 in a Village and 3 in a Town; a Hamlet's treasury stops at 64 emeralds and a Village's at 128.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaQb1116eTreasury"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaQb1116eTreasury")
	public void qaTakingsAreAFifthPerWorkerAQuarterMoreARankUpToAStackARank(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			long day = Chronicle.day(level);
			int[][] cases = {{0, 0, 200}, {1, 0, 250}, {2, 0, 300}, {0, 6390, 6400}, {0, 6400, 6400}, {1, 12790, 12800}};
			for (int[] c : cases) {
				VillageRanks.Rank rank = VillageRanks.Rank.values()[c[0]];
				entity.setRank(rank);
				entity.setTreasury(c[1]);
				entity.setLastTaxDay(day - 1);
				entity.setNeeds(null); // wellbeing as an average village's (a half)
				Treasury.round(level, hall, entity, 10);
				helper.assertTrue(entity.treasury() == c[2], rank + " with " + c[1] + " cents and 10 workers holds " + entity.treasury() + " after a day, not " + c[2]);
			}
			helper.succeed();
		});
	}

	/**
	 * "Once a village with a hall has 8 villagers, monsters may raid it at night": no chance at 7, a chance at 8. "Bigger
	 * villages draw bigger raids: 3 raiders, plus one for every 4 villagers" (no guards): 3 at 3 villagers, 6 at 12 and at
	 * 15, 7 at 16.
	 */
	//$ gametest_batch AREA '"qaQb1116eRaids"'
	@GameTest(template = AREA, batch = "qaQb1116eRaids")
	public void qaRaidsComeFromEightVillagersWithThreePlusOneInFour(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(VillageRaids.nightlyChance(7) == 0f, "a village of 7 may be raided: " + VillageRaids.nightlyChance(7));
		helper.assertTrue(VillageRaids.nightlyChance(8) > 0f, "a village of 8 is never raided");
		helper.assertTrue(Lairs.near(level, hall).isEmpty() && Threats.LEVEL < 3, "setup: a lair near, or a hard threat level");
		for (int[] c : new int[][] {{3, 3}, {12, 6}, {15, 6}, {16, 7}}) {
			int n = VillageRaids.expected(level, hall, c[0], 0, Threats.MONSTERS);
			helper.assertTrue(n == c[1], c[0] + " villagers draw " + n + " raiders, not " + c[1]);
		}
		helper.succeed();
	}

	/**
	 * "The first caravan": "a second village with a Village Hall within 2048 blocks": one 2048 blocks away is on the
	 * hall's Trade routes list, one 2049 blocks away isn't.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaQb1116eCaravanReach"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaQb1116eCaravanReach")
	public void qaTheTradeRoutesListReaches2048BlocksAndNoFurther(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 2, 0));
		BlockPos inReach = hall.offset(Caravans.RANGE, 0, 0);
		BlockPos tooFar = hall.offset(0, 0, Caravans.RANGE + 1);
		helper.assertTrue(Caravans.RANGE == 2048, "caravans reach " + Caravans.RANGE);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.remove(inReach);
			data.remove(tooFar);
		});
		data.setWants(inReach, Component.literal("Edge"), List.of());
		data.setWants(tooFar, Component.literal("Beyond"), List.of());
		List<BlockPos> listed = Caravans.neighbours(level, hall).stream().map(Caravans.Village::hall).toList();
		helper.assertTrue(listed.contains(inReach), "a village 2048 blocks away isn't on the Trade routes list");
		helper.assertFalse(listed.contains(tooFar), "a village 2049 blocks away is on the Trade routes list");
		helper.succeed();
	}
}
