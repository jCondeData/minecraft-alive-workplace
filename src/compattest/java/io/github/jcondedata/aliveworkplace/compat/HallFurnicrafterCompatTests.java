package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Bug B302 with the real CobbleFurnies: the Village Hall's Free job blocks page lists a free Furni Crafter as a
 * Furnicrafter, and a pick there makes a jobless villager one. Also for a Furni Crafter the world has no workstation
 * record of (placed before the update that made it a workstation, in a house whose section already had records: the
 * game never looks at such a section's blocks again when it loads), which is what the owner's server had: the record is
 * filed when the section loads, and the block is listed.
 */
public class HallFurnicrafterCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos CRAFTER = new BlockPos(8, 2, 8);
	/** A barrel by it, a Fisherman's block, as in the owner's house (the section already has a record). */
	private static final BlockPos BARREL = new BlockPos(8, 2, 6);

	private static Block furnicrafter() {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblefurnies", "furnicrafter"));
	}

	/** The hall, the Furni Crafter and a barrel, and a jobless villager; the hall's village is only this area. */
	private static Villager stage(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10;
		PartnerShowsCompatTests.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.assertTrue(furnicrafter() != Blocks.AIR, "CobbleFurnies' Furni Crafter isn't loaded");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(CRAFTER, furnicrafter());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		villager.setNoAi(true); // it waits for the hall's pick, rather than taking the block by itself
		return villager;
	}

	/** The Furnicrafter's line on the hall's Free job blocks page, or -1. */
	private static int furnicrafterLine(ChoiceMenu menu) {
		String name = Stations.name(ModVillagers.FURNICRAFTER).getString();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().equals(name)) {
				return slot;
			}
		}
		return -1;
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		int tab = HallPages.slot(FreeJobsPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Free job blocks page has no tab: " + tab);
		menu.press(tab, player);
		helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "the page's header: " + menu.icon(4));
		return menu;
	}

	/** Picks the Furnicrafter's line, then the villager: they become the Furnicrafter at the Furni Crafter. */
	private static void hireThroughTheHall(GameTestHelper helper, Villager villager) {
		ServerLevel level = helper.getLevel();
		BlockPos crafter = helper.absolutePos(CRAFTER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = openPage(helper, player);
		int line = furnicrafterLine(menu);
		helper.assertTrue(line >= 0, "the hall lists no Furnicrafter; its free blocks: "
			+ VillageHalls.freeStations(level, helper.absolutePos(HALL)).stream().map(s -> s.profession() + "@" + helper.relativePos(s.pos())).toList());
		helper.assertTrue(menu.icon(line).is(furnicrafter().asItem()), "the Furnicrafter's line shows " + menu.icon(line));
		menu.press(line, player);
		helper.assertTrue(menu.icon(4).is(furnicrafter().asItem()), "the Furni Crafter's page header: " + menu.icon(4));
		menu.press(VillageHallScreen.FIRST_ROW, player);
		helper.assertTrue(ModVillagers.isFurnicrafter(villager.getVillagerData().getProfession()),
			"after the hall's pick the villager is " + villager.getVillagerData().getProfession());
		helper.assertTrue(crafter.equals(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
			"job site: " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
		helper.assertTrue(VillageHalls.freeStations(level, helper.absolutePos(HALL)).stream().noneMatch(s -> s.pos().equals(crafter)),
			"the Furni Crafter is still listed as free");
	}

	/** Files the section's workstations again the way the game does when the chunk loads from the save. */
	private static void loadSection(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		LevelChunk chunk = level.getChunkAt(pos);
		level.getPoiManager().checkConsistencyWithBlocks(SectionPos.of(pos), chunk.getSection(chunk.getSectionIndex(pos.getY())));
	}

	/** A free Furni Crafter is on the hall's list as a Furnicrafter, and the hall's pick makes a jobless villager one. */
	//$ gametest_ticks_batch AREA '100' '"hall_furnicrafter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hall_furnicrafter")
	public void theHallListsAFreeFurniCrafter(GameTestHelper helper) {
		Villager villager = stage(helper);
		helper.runAfterDelay(2, () -> {
			hireThroughTheHall(helper, villager);
			helper.succeed();
		});
	}

	/**
	 * A Furni Crafter the save has no record of (placed before it was a workstation), by a barrel that has one: when its
	 * section loads it gets its record, and the hall lists it and gives it to a jobless villager.
	 */
	//$ gametest_ticks_batch AREA '100' '"hall_furnicrafter_old_world"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hall_furnicrafter_old_world")
	public void aFurniCrafterPlacedBeforeTheUpdateIsListed(GameTestHelper helper) {
		Villager villager = stage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos crafter = helper.absolutePos(CRAFTER);
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			level.getPoiManager().remove(crafter); // as an old save: the block stands, with no workstation record
			helper.assertTrue(level.getPoiManager().getType(crafter).isEmpty(), "setup: the Furni Crafter still has its record");
			helper.assertTrue(level.getPoiManager().getType(helper.absolutePos(BARREL)).isPresent(), "setup: the barrel has no record");
			helper.assertTrue(VillageHalls.freeStations(level, hall).stream().noneMatch(s -> s.pos().equals(crafter)), "setup: listed without a record");
			loadSection(helper, crafter);
			helper.assertTrue(level.getPoiManager().getType(crafter).map(h -> h.is(ModVillagers.FURNICRAFTER_POI)).orElse(false),
				"after the section loaded the Furni Crafter's record is " + level.getPoiManager().getType(crafter));
			hireThroughTheHall(helper, villager);
			helper.succeed();
		});
	}

	/**
	 * A Furni Crafter someone works at stays theirs when its section loads again: its record isn't filed twice or freed,
	 * and the hall doesn't offer it.
	 */
	//$ gametest_ticks_batch AREA '100' '"hall_furnicrafter_taken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hall_furnicrafter_taken")
	public void aWorkedFurniCrafterStaysTakenWhenItsSectionLoads(GameTestHelper helper) {
		Villager villager = stage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos crafter = helper.absolutePos(CRAFTER);
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			hireThroughTheHall(helper, villager);
			loadSection(helper, crafter);
			List<BlockPos> records = level.getPoiManager().getInRange(h -> h.is(ModVillagers.FURNICRAFTER_POI), crafter, 0, PoiManager.Occupancy.ANY)
				.map(r -> r.getPos().immutable()).toList();
			helper.assertTrue(records.equals(List.of(crafter)), "the Furni Crafter's records: " + records);
			helper.assertTrue(level.getPoiManager().getFreeTickets(crafter) == 0, "the worked Furni Crafter was freed");
			helper.assertTrue(VillageHalls.freeStations(level, hall).stream().noneMatch(s -> s.pos().equals(crafter)), "the hall offers the worked Furni Crafter");
			helper.succeed();
		});
	}
}
