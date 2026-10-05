package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * ROADMAP 27.20, old houses found and measured. A village on flat grass (a hall, a Homes zone east of it with "renew old
 * houses" on) and vanilla's plains small house 1 placed from its own template the way a village places it (its air and
 * structure voids skipped, its jigsaws turned into their final blocks): 7×7×7, 153 blocks, two beds and nothing else
 * with a block entity. Each test runs alone in its batch, so no neighbouring test's hall is nearer the house.
 */
public class OldHousesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	/** The small house's origin (its template's 0,0,0). */
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
	private static final ResourceLocation MEDIUM_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_medium_house_1");
	private static final int SMALL_HOUSE_BLOCKS = 153;

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 14; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 0; z <= 29; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
		entity.setPlan(plan.editZone(0, "homes", "Homes 1", "", true));
		OldHouses.forget(level, hall);
		return new Village(level, hall, owner);
	}

	/** Places a vanilla template as a village does; returns its box. */
	static BoundingBox placeVanilla(GameTestHelper helper, ResourceLocation id, BlockPos at) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(id).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		StructurePlaceSettings settings = new StructurePlaceSettings();
		settings.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		settings.addProcessor(JigsawReplacementProcessor.INSTANCE);
		template.placeInWorld(level, origin, origin, settings, net.minecraft.util.RandomSource.create(27_20L), 2);
		return BoundingBox.fromCorners(origin, origin.offset(template.getSize().getX() - 1, template.getSize().getY() - 1, template.getSize().getZ() - 1));
	}

	/** Starts the hall's survey and checks the result once it is done. */
	private static void surveyThen(GameTestHelper helper, Village v, java.util.function.Consumer<List<OldHouses.House>> check) {
		helper.assertTrue(OldHouses.request(v.level(), v.hall()).isPresent(), "no survey: the renew zone isn't seen");
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(v.level(), v.hall()).isPresent(), "the survey hasn't finished");
			check.accept(OldHouses.result(v.level(), v.hall()).get());
		});
	}

	private static OldHouses.House only(GameTestHelper helper, List<OldHouses.House> houses) {
		helper.assertTrue(houses.size() == 1, "expected the one house, found " + houses);
		return houses.get(0);
	}

	/** The vanilla plains house is found, renewable, and its box measured exactly: the template's 7×7×7, all 153 blocks. */
	//$ gametest_ticks_batch AREA '200' '"oldHousePlains"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldHousePlains")
	public void aVanillaPlainsHouseIsFoundAndMeasured(GameTestHelper helper) {
		Village v = village(helper);
		BoundingBox expected = placeVanilla(helper, SMALL_HOUSE, HOUSE);
		surveyThen(helper, v, houses -> {
			OldHouses.House house = only(helper, houses);
			helper.assertTrue(house.box().equals(expected), "measured " + house.box() + ", the template fills " + expected);
			helper.assertTrue(house.blocks() == SMALL_HOUSE_BLOCKS, "measured " + house.blocks() + " blocks, the template has " + SMALL_HOUSE_BLOCKS);
			helper.assertTrue(house.villageBlocks() == SMALL_HOUSE_BLOCKS, "only " + house.villageBlocks() + " village blocks");
			helper.assertTrue(house.renewable(), "not renewable: " + house.verdict());
		});
	}

	/** The same shape in deepslate and quartz: measured, but not an old village house. */
	//$ gametest_ticks_batch AREA '200' '"oldHouseDeepslate"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldHouseDeepslate")
	public void theSameShapeInDeepslateAndQuartzIsNot(GameTestHelper helper) {
		Village v = village(helper);
		BoundingBox box = placeVanilla(helper, SMALL_HOUSE, HOUSE);
		Map<Block, Block> swap = Map.of(Blocks.COBBLESTONE, Blocks.DEEPSLATE_BRICKS, Blocks.OAK_PLANKS, Blocks.QUARTZ_BLOCK,
			Blocks.OAK_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.STRIPPED_OAK_LOG, Blocks.QUARTZ_PILLAR);
		int[] swapped = {0};
		BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(p -> {
			BlockState state = v.level().getBlockState(p);
			Block to = swap.get(state.getBlock());
			if (to != null) {
				v.level().setBlock(p, to.withPropertiesOf(state), 2);
				swapped[0]++;
			}
		});
		helper.assertTrue(swapped[0] == 143, "setup: swapped " + swapped[0] + " blocks, the house has 143 of cobblestone, planks, stairs and logs");
		surveyThen(helper, v, houses -> {
			OldHouses.House house = only(helper, houses);
			helper.assertTrue(house.box().equals(box), "measured " + house.box() + ", the house fills " + box);
			helper.assertTrue(house.verdict() == OldHouses.Verdict.NOT_VILLAGE, "a deepslate and quartz house counted as " + house.verdict());
		});
	}

	/** A chest inside: not an old village house. */
	//$ gametest_ticks_batch AREA '200' '"oldHouseChest"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldHouseChest")
	public void oneWithAChestIsNot(GameTestHelper helper) {
		Village v = village(helper);
		placeVanilla(helper, SMALL_HOUSE, HOUSE);
		helper.setBlock(HOUSE.offset(2, 1, 4), Blocks.CHEST);
		surveyThen(helper, v, houses -> {
			OldHouses.House house = only(helper, houses);
			helper.assertTrue(house.verdict() == OldHouses.Verdict.CONTAINER, "a house with a chest counted as " + house.verdict());
		});
	}

	/** A lantern a player put down in it since 1.1 (the ledger, 27.19): not an old village house. */
	//$ gametest_ticks_batch AREA '200' '"oldHousePlayer"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldHousePlayer")
	public void onePlayerChangedIsNot(GameTestHelper helper) {
		Village v = village(helper);
		placeVanilla(helper, SMALL_HOUSE, HOUSE);
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), helper.absolutePos(HOUSE.offset(2, 1, 4)), Blocks.LANTERN),
			"setup: the player's lantern wasn't placed");
		surveyThen(helper, v, houses -> {
			OldHouses.House house = only(helper, houses);
			helper.assertTrue(house.verdict() == OldHouses.Verdict.PLAYER_BUILT, "a house a player changed counted as " + house.verdict());
		});
	}

	/** The house reaching out of the renew zone (the zone ends inside it): kept. A zone with renew off: not looked at. */
	//$ gametest_ticks_batch AREA '200' '"oldHouseZone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldHouseZone")
	public void oneReachingOutOfTheZoneIsNot(GameTestHelper helper) {
		Village v = village(helper);
		placeVanilla(helper, SMALL_HOUSE, HOUSE);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) v.level().getBlockEntity(v.hall());
		BitSet out = new BitSet();
		out.set(CityPlan.cellAt(v.hall(), helper.absolutePos(HOUSE.offset(6, 0, 6))));
		entity.setPlan(entity.plan().erase(out));
		surveyThen(helper, v, houses -> {
			OldHouses.House house = only(helper, houses);
			helper.assertTrue(house.verdict() == OldHouses.Verdict.OUTSIDE_ZONE, "a house reaching out of the zone counted as " + house.verdict());
			entity.setPlan(entity.plan().editZone(0, "homes", "Homes 1", "", false));
			helper.assertTrue(OldHouses.request(v.level(), v.hall()).isEmpty(), "a survey with no renew zone");
		});
	}

	/** Two houses, 153 and over 400 blocks: the fill never reads more than 256 blocks in a tick, so it takes several. */
	//$ gametest_ticks_batch AREA '300' '"oldHouseBudget"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "oldHouseBudget")
	public void theFillNeverPasses256BlocksInATick(GameTestHelper helper) {
		Village v = village(helper);
		placeVanilla(helper, SMALL_HOUSE, HOUSE);
		placeVanilla(helper, MEDIUM_HOUSE, new BlockPos(10, 2, 15));
		int[] most = {0};
		int[] busy = {0};
		helper.onEachTick(() -> {
			int n = OldHouses.blocksLastTick(v.level());
			most[0] = Math.max(most[0], n);
			if (n > 0) {
				busy[0]++;
			}
		});
		OldHouses.Survey survey = OldHouses.request(v.level(), v.hall()).orElseThrow();
		helper.succeedWhen(() -> {
			helper.assertTrue(survey.done(), "the survey hasn't finished");
			helper.assertTrue(most[0] <= OldHouses.BLOCKS_PER_TICK, "the fill read " + most[0] + " blocks in one tick");
			helper.assertTrue(survey.reads() > 2 * OldHouses.BLOCKS_PER_TICK, "setup: only " + survey.reads() + " blocks read");
			helper.assertTrue(busy[0] >= 3, "the reads were spread over only " + busy[0] + " ticks");
			helper.assertTrue(survey.houses().size() == 2 && survey.houses().stream().allMatch(OldHouses.House::renewable),
				"expected both houses renewable: " + survey.houses());
		});
	}

	/** The desk: "Old houses: 1, 1 can be renewed", and Show me outlines it. */
	//$ gametest_ticks_batch AREA '200' '"oldHouseDesk"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldHouseDesk")
	public void theDeskListsThemAndShowMeOutlinesEach(GameTestHelper helper) {
		Village v = village(helper);
		placeVanilla(helper, SMALL_HOUSE, HOUSE);
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(v.level(), v.owner(), plan, v.hall());
		helper.assertTrue(Stewards.appoint(v.owner(), steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		ChoiceMenu first = VillageHallScreen.forTest(v.owner(), v.hall());
		first.press(VillageHallScreen.ADVICE, v.owner());
		helper.assertTrue(key(first.icon(StewardDeskPage.OLD_HOUSES).getHoverName()).equals("screen.aliveworkplace.desk.old_houses.looking"),
			"the desk doesn't say it's looking: " + first.icon(StewardDeskPage.OLD_HOUSES).getHoverName());
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(v.level(), v.hall()).isPresent(), "the survey hasn't finished");
			ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
			menu.press(VillageHallScreen.ADVICE, v.owner());
			Component name = menu.icon(StewardDeskPage.OLD_HOUSES).getHoverName();
			helper.assertTrue(key(name).equals("screen.aliveworkplace.desk.old_houses_count") && args(name).equals("[1, 1]"),
				"the desk says " + name);
			String lore = String.valueOf(menu.icon(StewardDeskPage.OLD_HOUSES).get(DataComponents.LORE));
			helper.assertTrue(lore.contains("screen.aliveworkplace.desk.old_houses.verdict.renewable"), "the house isn't listed: " + lore);
			menu.press(StewardDeskPage.OLD_HOUSES, v.owner());
			helper.assertTrue(BlueprintOutline.glowingBoxes(v.owner()) == 1, "Show me outlined " + BlueprintOutline.glowingBoxes(v.owner()) + " houses");
		});
	}

	private static String key(Component text) {
		return text.getContents() instanceof TranslatableContents t ? t.getKey() : text.getString();
	}

	private static String args(Component text) {
		return text.getContents() instanceof TranslatableContents t ? java.util.Arrays.toString(t.getArgs()) : "";
	}
}
