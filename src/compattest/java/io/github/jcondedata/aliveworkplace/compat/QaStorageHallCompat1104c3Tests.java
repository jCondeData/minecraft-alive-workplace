package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * QA (lane-c-1104-0953), written from the specs of B302 and B257, beyond their own tests.
 * <p>
 * B302: the Village Hall's Free job blocks page lists every free CobbleFurnies Furni Crafter as a Furnicrafter: two in a
 * finished cottage are one "Furnicrafter ×2" line in that cottage, and after the hall gives one away the other is still
 * offered; two the save never had a record of (as a world from before the update) both
 * get one when their sections load; one a villager took by themselves isn't offered.
 * <p>
 * B257: what lies behind a Sophisticated Storage storage_link touching a controller's network is counted once at the
 * Storehouse: a link a player places without the Storage Tool; a barrel behind two links (built, then tool-linked); a
 * tool-linked link broken later; a builder taking from the barrel behind a tool-linked link.
 */
public class QaStorageHallCompat1104c3Tests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	/** A finished Starter Cottage's outline: x 5..15, z 4..13 (11 × 10), from y 2. */
	private static final BlockPos COTTAGE = new BlockPos(5, 2, 4);
	private static final BlockPos CRAFTER_NEAR = new BlockPos(8, 2, 7);
	private static final BlockPos CRAFTER_FAR = new BlockPos(9, 2, 9);

	// --- B302 -------------------------------------------------------------------------------------------------------

	private static Block furnicrafter() {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblefurnies", "furnicrafter"));
	}

	private static String furnicrafterName() {
		return Stations.name(ModVillagers.FURNICRAFTER).getString();
	}

	/** The hall, its village only this area, and a finished Starter Cottage; the player's menu is closed with the test. */
	private static void village(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.assertTrue(furnicrafter() != Blocks.AIR, "CobbleFurnies' Furni Crafter isn't loaded");
		helper.assertTrue(ModVillagers.FURNICRAFTER != null, "no Furnicrafter profession with CobbleFurnies installed");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10; // this area only (the far Furni Crafter is 9.9 blocks from the hall), not the areas beside it
		PartnerShowsCompatTests.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlueprintData.Placement cottage = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(COTTAGE), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), cottage, UUID.randomUUID());
		PartnerShowsCompatTests.after(helper, () -> sites.forgetFinished(cottage));
	}

	/** A Furni Crafter walled in on four sides with a roof over it, as in a room of the cottage. */
	private static void walledIn(GameTestHelper helper, BlockPos crafter, boolean asPlaced) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.setBlock(crafter.relative(d), Blocks.SPRUCE_PLANKS);
		}
		helper.setBlock(crafter.above(), Blocks.SPRUCE_SLAB);
		if (asPlaced) {
			helper.setBlock(crafter, furnicrafter());
		}
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at, boolean ai) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		villager.setNoAi(!ai);
		return villager;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		int tab = HallPages.slot(FreeJobsPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Free job blocks page has no tab: " + tab);
		menu.press(tab, player);
		helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "the page's header: " + menu.icon(4));
		return menu;
	}

	/** The page's lines as "name | lore lines". */
	private static List<String> lines(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (!icon.isEmpty()) {
				out.add(slot + ":" + icon.getHoverName().getString() + " " + lore(icon));
			}
		}
		return out;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The slot whose line is named exactly {@code name}, or -1. */
	private static int line(ChoiceMenu menu, String name) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().equals(name)) {
				return slot;
			}
		}
		return -1;
	}

	/** How many lines of the page name the Furnicrafter at all (alone or "×n"). */
	private static long furnicrafterLines(ChoiceMenu menu) {
		String name = furnicrafterName();
		long n = 0;
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().startsWith(name)) {
				n++;
			}
		}
		return n;
	}

	/** The hall's free Furni Crafters in this test's area (not one a test beside it left standing within the hall's reach). */
	private static List<BlockPos> freeCrafters(GameTestHelper helper) {
		net.minecraft.world.level.levelgen.structure.BoundingBox area = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(16, 7, 16)));
		return VillageHalls.freeStations(helper.getLevel(), helper.absolutePos(HALL)).stream()
			.filter(s -> ModVillagers.isFurnicrafter(s.profession()) && area.isInside(s.pos())).map(VillageHalls.FreeStation::pos).toList();
	}

	private static String rel(GameTestHelper helper, List<BlockPos> abs) {
		return abs.stream().map(p -> helper.relativePos(p).toShortString()).toList().toString();
	}

	/** Picks the line at {@code slot}, then the first villager on the block's page: returns the menu. */
	private static void hire(GameTestHelper helper, ChoiceMenu menu, ServerPlayer player, int slot) {
		menu.press(slot, player);
		helper.assertTrue(menu.icon(4).is(furnicrafter().asItem()), "the Furni Crafter's page header: " + menu.icon(4));
		helper.assertTrue(!menu.icon(VillageHallScreen.FIRST_ROW).isEmpty(), "the Furni Crafter's page offers nobody to take it");
		menu.press(VillageHallScreen.FIRST_ROW, player);
	}

	/**
	 * Two free Furni Crafters, each walled in in a finished Starter Cottage: the page shows one line "Furnicrafter ×2", in
	 * the cottage, 2 free; the hall gives the nearest to a jobless villager, who becomes the Furnicrafter there, and the
	 * page then offers the other alone as "Furnicrafter", the given one no more.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104c3_two_crafters"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104c3_two_crafters")
	public void twoFreeFurniCraftersInACottageAreOneLineAndTheHallGivesOneAway(GameTestHelper helper) {
		village(helper);
		walledIn(helper, CRAFTER_NEAR, true);
		walledIn(helper, CRAFTER_FAR, true);
		Villager villager = jobless(helper, new BlockPos(3, 2, 8), false);
		ServerPlayer player = player(helper);
		BlockPos near = helper.absolutePos(CRAFTER_NEAR);
		BlockPos far = helper.absolutePos(CRAFTER_FAR);
		helper.runAfterDelay(2, () -> {
			List<BlockPos> free = freeCrafters(helper);
			helper.assertTrue(free.equals(List.of(near, far)), "the hall's free Furni Crafters, nearest first: " + rel(helper, free) + ", wanted "
				+ CRAFTER_NEAR.toShortString() + " then " + CRAFTER_FAR.toShortString());
			ChoiceMenu menu = openPage(helper, player);
			String two = Component.translatable("screen.aliveworkplace.free_jobs.many", Stations.name(ModVillagers.FURNICRAFTER), 2).getString();
			int slot = line(menu, two);
			helper.assertTrue(slot >= 0 && furnicrafterLines(menu) == 1, "no single \"" + two + "\" line: " + lines(menu));
			List<String> lore = lore(menu.icon(slot));
			String in = Component.translatable("screen.aliveworkplace.free_jobs.in", Blueprints.displayName(StarterBlueprints.STARTER_COTTAGE.id())).getString();
			String here = Component.translatable("screen.aliveworkplace.free_jobs.here", 2).getString();
			helper.assertTrue(lore.contains(in) && lore.contains(here), "the line's text, wanted \"" + in + "\" and \"" + here + "\": " + lore);
			helper.assertTrue(menu.icon(slot).is(furnicrafter().asItem()), "the line shows " + menu.icon(slot));

			hire(helper, menu, player, slot);
			helper.assertTrue(ModVillagers.isFurnicrafter(villager.getVillagerData().getProfession()),
				"after the hall's pick the villager is " + villager.getVillagerData().getProfession());
			helper.assertTrue(near.equals(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"job site (wanted the nearest Furni Crafter): " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			List<BlockPos> after = freeCrafters(helper);
			helper.assertTrue(after.equals(List.of(far)), "free Furni Crafters after the pick: " + rel(helper, after) + ", wanted only " + CRAFTER_FAR.toShortString());
			ChoiceMenu again = openPage(helper, player);
			int alone = line(again, furnicrafterName());
			helper.assertTrue(alone >= 0 && furnicrafterLines(again) == 1, "the other Furni Crafter isn't one \"" + furnicrafterName() + "\" line: " + lines(again));
			helper.succeed();
		});
	}

	/**
	 * Two Furni Crafters in the cottage with no workstation record (a world saved before the update, or while the mod
	 * was off): the hall doesn't see them; once their sections load both are filed, the page offers "Furnicrafter ×2",
	 * and the hall's pick makes a jobless villager the Furnicrafter.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104c3_old_crafters"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104c3_old_crafters")
	public void twoFurniCraftersFromAnOldSaveAreBothFiledWhenTheirSectionLoads(GameTestHelper helper) {
		village(helper);
		walledIn(helper, CRAFTER_NEAR, false);
		walledIn(helper, CRAFTER_FAR, false);
		helper.setBlock(new BlockPos(6, 2, 12), Blocks.BARREL); // a block with a record in the section, as in the owner's house
		ServerLevel level = helper.getLevel();
		BlockPos near = helper.absolutePos(CRAFTER_NEAR);
		BlockPos far = helper.absolutePos(CRAFTER_FAR);
		helper.setBlock(CRAFTER_NEAR, furnicrafter());
		helper.setBlock(CRAFTER_FAR, furnicrafter());
		// As a save from before the update: the blocks stand, with no workstation record.
		level.getPoiManager().remove(near);
		level.getPoiManager().remove(far);
		Villager villager = jobless(helper, new BlockPos(3, 2, 8), false);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(near).is(furnicrafter()) && level.getBlockState(far).is(furnicrafter()), "setup: the Furni Crafters aren't there");
			helper.assertTrue(level.getPoiManager().getType(near).isEmpty() && level.getPoiManager().getType(far).isEmpty(), "setup: the old Furni Crafters have records");
			helper.assertTrue(freeCrafters(helper).isEmpty(), "setup: listed without a record: " + freeCrafters(helper) + " (the old Furni Crafters "
				+ near + ", " + far + ")");
			for (SectionPos section : new java.util.LinkedHashSet<>(List.of(SectionPos.of(near), SectionPos.of(far)))) {
				LevelChunk chunk = level.getChunk(section.x(), section.z());
				level.getPoiManager().checkConsistencyWithBlocks(section, chunk.getSection(chunk.getSectionIndex(section.minBlockY())));
			}
			for (BlockPos pos : List.of(near, far)) {
				helper.assertTrue(level.getPoiManager().getType(pos).map(h -> h.is(ModVillagers.FURNICRAFTER_POI)).orElse(false),
					"after its section loaded the Furni Crafter at " + helper.relativePos(pos).toShortString() + " has the record " + level.getPoiManager().getType(pos));
			}
			List<BlockPos> free = freeCrafters(helper);
			helper.assertTrue(free.equals(List.of(near, far)), "free Furni Crafters after the load: " + rel(helper, free));
			ChoiceMenu menu = openPage(helper, player);
			String two = Component.translatable("screen.aliveworkplace.free_jobs.many", Stations.name(ModVillagers.FURNICRAFTER), 2).getString();
			int slot = line(menu, two);
			helper.assertTrue(slot >= 0, "no \"" + two + "\" line: " + lines(menu));
			hire(helper, menu, player, slot);
			helper.assertTrue(ModVillagers.isFurnicrafter(villager.getVillagerData().getProfession()),
				"after the hall's pick the villager is " + villager.getVillagerData().getProfession());
			helper.assertTrue(freeCrafters(helper).equals(List.of(far)), "free after the pick: " + rel(helper, freeCrafters(helper)));
			helper.succeed();
		});
	}

	/**
	 * A jobless villager takes a Furni Crafter by themselves (the game's own job search): the hall doesn't offer it. A
	 * second one placed afterwards is offered as "Furnicrafter", alone, and the hall gives it to a second jobless villager.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1104c3_taken_crafter"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1104c3_taken_crafter")
	public void aFurniCrafterAVillagerTookByThemselvesIsNotOffered(GameTestHelper helper) {
		village(helper);
		helper.setBlock(CRAFTER_NEAR, furnicrafter());
		Villager worker = jobless(helper, new BlockPos(9, 2, 9), true);
		helper.setDayTime(2000);
		BlockPos near = helper.absolutePos(CRAFTER_NEAR);
		BlockPos far = helper.absolutePos(CRAFTER_FAR);
		boolean[] placed = {false};
		Villager[] second = {null};
		ServerPlayer player = player(helper);
		helper.succeedWhen(() -> {
			if (!placed[0]) {
				helper.assertTrue(ModVillagers.isFurnicrafter(worker.getVillagerData().getProfession())
					&& near.equals(worker.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
					"the villager hasn't taken the Furni Crafter by themselves yet: " + worker.getVillagerData().getProfession());
				List<BlockPos> free = freeCrafters(helper);
				if (!free.isEmpty()) {
					throw new GameTestAssertException("the hall offers the Furni Crafter " + helper.relativePos(worker.blockPosition()).toShortString()
						+ "'s villager works at: " + rel(helper, free));
				}
				ChoiceMenu menu = openPage(helper, player);
				if (furnicrafterLines(menu) != 0) {
					throw new GameTestAssertException("the page offers the taken Furni Crafter: " + lines(menu));
				}
				helper.setBlock(CRAFTER_FAR, furnicrafter());
				second[0] = jobless(helper, new BlockPos(3, 2, 8), false);
				placed[0] = true;
				throw new GameTestAssertException("placing the second Furni Crafter");
			}
			ChoiceMenu menu = openPage(helper, player);
			int slot = line(menu, furnicrafterName());
			if (slot < 0 || furnicrafterLines(menu) != 1) {
				throw new GameTestAssertException("the second Furni Crafter isn't one \"" + furnicrafterName() + "\" line: " + lines(menu));
			}
			hire(helper, menu, player, slot);
			if (!ModVillagers.isFurnicrafter(second[0].getVillagerData().getProfession())
				|| !far.equals(second[0].getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null))) {
				throw new GameTestAssertException("after the hall's pick the second villager is " + second[0].getVillagerData().getProfession()
					+ " at " + second[0].getBrain().getMemory(MemoryModuleType.JOB_SITE));
			}
			if (!freeCrafters(helper).isEmpty()) {
				throw new GameTestAssertException("still free after both were taken: " + rel(helper, freeCrafters(helper)));
			}
			if (!ModVillagers.isFurnicrafter(worker.getVillagerData().getProfession())) {
				throw new GameTestAssertException("the first villager lost the job: " + worker.getVillagerData().getProfession());
			}
		});
	}

	// --- B257 -------------------------------------------------------------------------------------------------------

	private static Block sophisticated(String id) {
		return SophisticatedControllerCompatTests.block(id);
	}

	private static void networks(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = true;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
	}

	private static void barrel(ServerLevel level, BlockPos pos, ItemStack goods, GameTestHelper helper) {
		level.setBlockAndUpdate(pos, sophisticated("barrel").defaultBlockState());
		if (!goods.isEmpty()) {
			helper.assertTrue(SupplyContainers.insert(level, List.of(pos), goods).isEmpty(), "a barrel took nothing at " + helper.relativePos(pos).toShortString());
		}
	}

	private static BlockPos link(ServerLevel level, BlockPos pos, Direction facing) {
		level.setBlock(pos, TomsStorageCompatTests.facing(sophisticated("storage_link").defaultBlockState(), facing), Block.UPDATE_ALL);
		return pos;
	}

	/** Links the link to {@code controller} the way the mod's Storage Tool does (its ILinkable.linkToController, by name). */
	private static void toolLink(GameTestHelper helper, BlockPos link, BlockPos controller) {
		BlockEntity be = helper.getLevel().getBlockEntity(link);
		helper.assertTrue(be != null, "the link has no block entity");
		try {
			be.getClass().getMethod("linkToController", BlockPos.class).invoke(be, controller);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("the link can't be linked: " + e);
		}
	}

	private static String counted(ServerLevel level, List<BlockPos> store, GameTestHelper helper) {
		return "the Storehouse's stores " + rel(helper, store) + " count " + SupplyContainers.count(level, store, Items.OAK_PLANKS) + " planks and "
			+ SupplyContainers.count(level, store, Items.COBBLESTONE) + " cobblestone";
	}

	/**
	 * A storage link a player places (the block's own placing, by a player, without the Storage Tool) between a barrel of
	 * the controller's network and a barrel with 7 planks: the barrel behind is counted once, as a store of its own; a
	 * builder taking 5 planks gets them out of that barrel, and the Storehouse counts the 2 left, once.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa1104c3_player_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1104c3_player_link")
	public void aLinkAPlayerPlacesWithoutTheToolCountsTheBarrelBehindOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer player = player(helper);
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		barrel(level, first, ItemStack.EMPTY, helper);
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, sophisticated("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the first barrel took nothing");
		BlockPos behind = helper.absolutePos(new BlockPos(6, 2, 4));
		barrel(level, behind, new ItemStack(Items.OAK_PLANKS, 7), helper);
		BlockPos at = helper.absolutePos(new BlockPos(5, 2, 4));
		Block linkBlock = sophisticated("storage_link");
		BlockState state = TomsStorageCompatTests.facing(linkBlock.defaultBlockState(), Direction.WEST);
		level.setBlock(at, state, Block.UPDATE_ALL);
		linkBlock.setPlacedBy(level, at, level.getBlockState(at), player, new ItemStack(linkBlock.asItem()));
		boolean[] took = {false};
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			if (!took[0]) {
				helper.assertTrue(SupplyContainers.count(level, store, Items.OAK_PLANKS) == 7 && SupplyContainers.count(level, store, Items.COBBLESTONE) == 10
					&& store.contains(controller), counted(level, store, helper) + ", wanted 7 and 10 (once each), with the controller "
					+ helper.relativePos(controller).toShortString());
				int taken = SupplyContainers.extract(level, store, Items.OAK_PLANKS, 5);
				took[0] = true;
				helper.assertTrue(taken == 5, "a builder took " + taken + " of 5 planks");
				store = SupplyContainers.find(level, storehouse, null);
			}
			long inBarrel = SupplyContainers.count(level, List.of(behind), Items.OAK_PLANKS);
			long atStore = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			helper.assertTrue(inBarrel == 2 && atStore == 2, "after a builder took 5: the barrel behind holds " + inBarrel + " and " + counted(level, store, helper)
				+ ", wanted 2 and 2");
		});
	}

	/**
	 * One barrel with 7 planks behind two storage links, each touching the controller's network: built (the builder's
	 * after-block hook on each), the barrel is counted once; then both linked to the controller with the Storage Tool,
	 * still once (through the controller, not as a store of its own as well).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa1104c3_two_links"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1104c3_two_links")
	public void aBarrelBehindTwoLinksIsCountedOnceBuiltAndToolLinked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer player = player(helper);
		// The network: controller 3,2,5; barrels 4,2,5 (10 cobblestone), 4,2,4 and 5,2,4.
		BlockPos a = helper.absolutePos(new BlockPos(4, 2, 5));
		BlockPos b = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos e = helper.absolutePos(new BlockPos(5, 2, 4));
		barrel(level, a, ItemStack.EMPTY, helper);
		barrel(level, b, ItemStack.EMPTY, helper);
		barrel(level, e, ItemStack.EMPTY, helper);
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 5));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, sophisticated("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(a), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the network took nothing");
		// The barrel behind both links, 6,2,5, then link 1 at 5,2,5 (touching 4,2,5, 5,2,4 and it) and link 2 at 6,2,4 (touching 5,2,4 and it).
		BlockPos behind = helper.absolutePos(new BlockPos(6, 2, 5));
		barrel(level, behind, new ItemStack(Items.OAK_PLANKS, 7), helper);
		BlockPos link1 = link(level, helper.absolutePos(new BlockPos(5, 2, 5)), Direction.WEST);
		BlockPos link2 = link(level, helper.absolutePos(new BlockPos(6, 2, 4)), Direction.NORTH);
		StorageNetworks.afterBuilt(level, link1, level.getBlockState(link1));
		StorageNetworks.afterBuilt(level, link2, level.getBlockState(link2));
		boolean[] linked = {false};
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			long cobble = SupplyContainers.count(level, store, Items.COBBLESTONE);
			if (!linked[0]) {
				helper.assertTrue(planks == 7 && cobble == 10 && store.contains(behind) && store.contains(controller),
					"built links: " + counted(level, store, helper) + ", wanted 7 planks (the barrel " + helper.relativePos(behind).toShortString()
						+ " a store of its own) and 10 cobblestone; linked by our walk: " + rel(helper, List.copyOf(StorageNetworks.linkedTo(level, controller))));
				toolLink(helper, link1, controller);
				toolLink(helper, link2, controller);
				linked[0] = true;
				throw new GameTestAssertException("linking both links with the tool");
			}
			helper.assertTrue(planks == 7 && cobble == 10 && !store.contains(behind) && store.contains(controller),
				"tool-linked links: " + counted(level, store, helper) + ", wanted 7 planks through the controller only and 10 cobblestone; linked by our walk: "
					+ rel(helper, List.copyOf(StorageNetworks.linkedTo(level, controller))) + "; the controller itself holds "
					+ SupplyContainers.count(level, List.of(controller), Items.OAK_PLANKS) + " planks");
		});
	}

	/**
	 * A storage link linked to the controller with the Storage Tool, with 7 planks behind it, counted through the
	 * controller; the link is broken: the barrel behind becomes a store of its own and is still counted once (the
	 * controller lets it go, nothing counts it twice or drops it).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa1104c3_link_broken"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1104c3_link_broken")
	public void aToolLinkedLinkBrokenLeavesTheBarrelBehindCountedOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer player = player(helper);
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		barrel(level, first, ItemStack.EMPTY, helper);
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, sophisticated("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the first barrel took nothing");
		BlockPos behind = helper.absolutePos(new BlockPos(6, 2, 4));
		barrel(level, behind, new ItemStack(Items.OAK_PLANKS, 7), helper);
		BlockPos link = link(level, helper.absolutePos(new BlockPos(5, 2, 4)), Direction.WEST);
		toolLink(helper, link, controller);
		boolean[] broken = {false};
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			long cobble = SupplyContainers.count(level, store, Items.COBBLESTONE);
			if (!broken[0]) {
				helper.assertTrue(planks == 7 && cobble == 10 && !store.contains(behind), "linked: " + counted(level, store, helper)
					+ ", wanted 7 and 10 through the controller");
				level.destroyBlock(link, false);
				broken[0] = true;
				throw new GameTestAssertException("breaking the link");
			}
			long viaController = SupplyContainers.count(level, List.of(controller), Items.OAK_PLANKS);
			helper.assertTrue(planks == 7 && cobble == 10 && store.contains(behind) && store.contains(controller),
				"link broken: " + counted(level, store, helper) + ", wanted 7 planks (the barrel " + helper.relativePos(behind).toShortString()
					+ " a store of its own) and 10 cobblestone; the controller itself still holds " + viaController + " planks");
		});
	}

	/**
	 * A builder takes from the barrel behind a tool-linked link, through the Storehouse: asking for 10 of its 7 planks
	 * gets 7 (each plank given once, never twice through the controller and the barrel), the barrel is empty and the
	 * Storehouse counts none.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa1104c3_take_behind_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1104c3_take_behind_link")
	public void aBuilderTakesWhatLiesBehindALinkedLinkOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer player = player(helper);
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		barrel(level, first, ItemStack.EMPTY, helper);
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, sophisticated("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the first barrel took nothing");
		BlockPos behind = helper.absolutePos(new BlockPos(6, 2, 4));
		barrel(level, behind, new ItemStack(Items.OAK_PLANKS, 7), helper);
		BlockPos link = link(level, helper.absolutePos(new BlockPos(5, 2, 4)), Direction.WEST);
		toolLink(helper, link, controller);
		int[] taken = {-1};
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			if (taken[0] < 0) {
				helper.assertTrue(SupplyContainers.count(level, store, Items.OAK_PLANKS) == 7 && !store.contains(behind),
					"linked: " + counted(level, store, helper) + ", wanted 7 planks through the controller");
				taken[0] = SupplyContainers.extract(level, store, Items.OAK_PLANKS, 10);
			}
			helper.assertTrue(taken[0] == 7, "a builder asking for 10 of the 7 planks got " + taken[0]);
			long left = SupplyContainers.count(level, List.of(behind), Items.OAK_PLANKS);
			helper.assertTrue(left == 0 && SupplyContainers.count(level, store, Items.OAK_PLANKS) == 0 && SupplyContainers.count(level, store, Items.COBBLESTONE) == 10,
				"after the builder took them: the barrel behind holds " + left + " and " + counted(level, store, helper));
		});
	}
}
