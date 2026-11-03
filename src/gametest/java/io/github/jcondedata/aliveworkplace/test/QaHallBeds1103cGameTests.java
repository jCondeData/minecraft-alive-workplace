package io.github.jcondedata.aliveworkplace.test;

import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.jeweller.Jewellers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.BedLadders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA (2026-11-03) of two owner-reported fixes, written from the bugs' expected behavior.
 * <p>
 * B303: the hall's Free job blocks list offers every job a shared block gives ("Composter: 4 jobs", a line per job),
 * groups blocks of one kind by building, and a pick gives that job. Here: the exact English the player reads (singular and
 * plural, the ×count form's argument order), the per-job takers rule (an old job's trades take only that job's block),
 * the block broken while its job picker is open, grouping across two buildings and two kinds in one building, and a
 * job's config switch (Jewellers) turned off, before and while the picker is open.
 * <p>
 * B304: a villager without a bed takes a free one up the Terrace's other ladder and sleeps; one with a bed climbs his
 * own home's ladder. Here: the Terrace turned a quarter (another variant of the same house), a save and reload before
 * bedtime, a bed claimed by someone else and freed again, the ladder broken, and doors between the ladder and the bed.
 */
public class QaHallBeds1103cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String AREA_HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	// --- B303 helpers ------------------------------------------------------------------------------------------------

	private static List<Villager> stage(GameTestHelper helper, BlockPos hall, int jobless) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < jobless; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(hall.getX() - 2 - i, 2, hall.getZ() + 3));
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
			out.add(v);
		}
		return out;
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(hall));
		menu.press(HallPages.slot(FreeJobsPage.PAGE), player);
		helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "the Free job blocks page's header: " + menu.icon(4));
		return menu;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static String name(ItemStack stack) {
		return stack.getHoverName().getString();
	}

	private static String job(VillagerProfession p) {
		return Stations.name(p).getString();
	}

	private static List<VillagerProfession> jobsAt(ServerLevel level, BlockPos hall, BlockPos pos) {
		VillageHalls.FreeStation s = VillageHalls.freeStations(level, hall).stream().filter(f -> f.pos().equals(pos)).findFirst()
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("not free: " + pos));
		return VillageHalls.jobs(s);
	}

	/** The picker's slot for {@code job}. */
	private static int jobSlot(GameTestHelper helper, ChoiceMenu menu, VillagerProfession job) {
		for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
			if (name(menu.icon(s)).equals(job(job))) {
				return s;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("the picker has no line for " + job(job));
	}

	/**
	 * The exact English on two composters out in the open with one jobless villager: "Composter ×2: N jobs" (the count
	 * after the block, the jobs last), "Not in a building", "2 free here; the nearest:", "· Florist", the singular
	 * "1 villager without a job could take it" and the shared click hint. The picker says "Composter: N jobs" and the
	 * pick-job hint; the takers page's back goes to the picker, the picker's back to the list. After one is given, the
	 * one left reads "Composter: N jobs" (no ×1, no "free here") and nobody else can take it.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaHbExactWords"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaHbExactWords")
	public void theSharedLinesReadExactlyAsWritten(GameTestHelper helper) {
		Villager villager = stage(helper, HALL, 1).get(0);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(15, 2, 4), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(17, 2, 4), Blocks.COMPOSTER);
		BlockPos nearer = helper.absolutePos(new BlockPos(15, 2, 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			List<VillagerProfession> jobs = jobsAt(level, hall, nearer);
			int n = jobs.size();
			helper.assertTrue(n >= 4, "setup: a composter's jobs " + jobs);
			ChoiceMenu menu = openPage(helper, player, HALL);
			ItemStack line = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(name(line).equals("Composter ×2: " + n + " jobs"), "the two composters' line: '" + name(line) + "'");
			List<String> l = lore(line);
			helper.assertTrue(l.get(0).equals("Not in a building"), "first under the name: " + l);
			helper.assertTrue(l.contains("2 free here; the nearest:"), "how many: " + l);
			helper.assertTrue(l.contains("· " + job(ModVillagers.FLORIST)) && l.contains("· " + job(VillagerProfession.FARMER)), "the job lines: " + l);
			helper.assertTrue(l.contains("1 villager without a job could take it"), "the singular takers line: " + l);
			helper.assertTrue(l.contains("Click: choose the job, then who takes it"), "the shared click hint: " + l);
			helper.assertTrue(!l.contains("Click: choose who takes it"), "the one-job click hint on a shared line: " + l);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(menu.icon(4).is(Items.COMPOSTER) && name(menu.icon(4)).equals("Composter: " + n + " jobs"), "the picker's header: " + name(menu.icon(4)));
			helper.assertTrue(lore(menu.icon(4)).contains("This block can give any of these jobs: pick one, then who takes it"), "the picker's hint: " + lore(menu.icon(4)));
			helper.assertTrue(name(menu.icon(0)).equals("Back to the free job blocks"), "the picker's back: " + name(menu.icon(0)));
			menu.press(jobSlot(helper, menu, ModVillagers.FLORIST), player);
			helper.assertTrue(name(menu.icon(0)).equals("Back to the block's jobs"), "the takers page's back: " + name(menu.icon(0)));
			menu.press(0, player);
			helper.assertTrue(menu.icon(4).is(Items.COMPOSTER) && name(menu.icon(4)).equals("Composter: " + n + " jobs"), "back didn't go to the picker: " + name(menu.icon(4)));
			menu.press(0, player);
			helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "the picker's back didn't go to the list: " + name(menu.icon(4)));
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(jobSlot(helper, menu, ModVillagers.FLORIST), player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.FLORIST, "not a Florist: " + villager.getVillagerData().getProfession());
			helper.assertTrue(nearer.equals(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"not the nearer composter: " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			ItemStack left = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE) && name(left).equals("Composter: " + n + " jobs"), "the one composter left: '" + name(left) + "'");
			helper.assertTrue(lore(left).stream().noneMatch(s -> s.contains("free here")), "a lone composter counted: " + lore(left));
			helper.assertTrue(lore(left).contains("No villager without a job can take it"), "nobody left to take it: " + lore(left));
			helper.succeed();
		});
	}

	/**
	 * Retraining is paid (26.6): a villager who still has the Florist's trades but lost his composter may take a composter
	 * only as a Florist. The picker counts him for the Florist and for no other job; the Farmer's takers page says nobody
	 * can; picked as the Florist he gets it. A new villager (no job ever) is counted for every job.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHbOldTrade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHbOldTrade")
	public void anOldJobsVillagerIsOfferedOnlyThatJobOfASharedBlock(GameTestHelper helper) {
		Villager florist = stage(helper, HALL, 1).get(0);
		florist.setVillagerData(florist.getVillagerData().setProfession(ModVillagers.FLORIST).setLevel(2));
		florist.setVillagerXp(20);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(16, 2, 4), Blocks.COMPOSTER);
		BlockPos composter = helper.absolutePos(new BlockPos(16, 2, 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(florist.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "setup: the Florist has a composter already");
			ChoiceMenu menu = openPage(helper, player, HALL);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			int floristSlot = jobSlot(helper, menu, ModVillagers.FLORIST);
			int farmerSlot = jobSlot(helper, menu, VillagerProfession.FARMER);
			helper.assertTrue(lore(menu.icon(floristSlot)).contains("1 villager without a job could take it"), "the Florist's line: " + lore(menu.icon(floristSlot)));
			helper.assertTrue(lore(menu.icon(farmerSlot)).contains("No villager without a job can take it"), "the Farmer's line counts an old Florist: " + lore(menu.icon(farmerSlot)));
			menu.press(farmerSlot, player);
			helper.assertTrue(name(menu.icon(VillageHallScreen.FIRST_ROW)).equals("Nobody without a job can take this block")
				|| menu.icon(VillageHallScreen.FIRST_ROW).isEmpty(), "the Farmer's takers page lists the old Florist: " + name(menu.icon(VillageHallScreen.FIRST_ROW)));
			helper.assertTrue(name(menu.icon(VillageHallScreen.FIRST_ROW + 4)).equals("Nobody without a job can take this block"), "the Farmer's empty page: "
				+ name(menu.icon(VillageHallScreen.FIRST_ROW + 4)));
			menu.press(VillageHallScreen.FIRST_ROW, player); // nothing there to press
			helper.assertTrue(florist.getVillagerData().getProfession() == ModVillagers.FLORIST && florist.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"the old Florist was made something else for free: " + florist.getVillagerData().getProfession());
			menu.press(0, player); // back to the jobs
			menu.press(jobSlot(helper, menu, ModVillagers.FLORIST), player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(florist.getVillagerData().getProfession() == ModVillagers.FLORIST
				&& composter.equals(florist.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"the Florist didn't get the composter back: " + florist.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(florist.getVillagerData().getLevel() == 2, "the Florist lost his level: " + florist.getVillagerData().getLevel());
			helper.succeed();
		});
	}

	/**
	 * The composter broken while its job picker is open: a job's click finds it gone, nobody is given anything and the
	 * list says there is nothing free. And broken while its takers page is open: the villager's click changes nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHbBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHbBroken")
	public void aSharedBlockBrokenWhileItsPickerIsOpenGivesNoJob(GameTestHelper helper) {
		Villager villager = stage(helper, HALL, 1).get(0);
		helper.setBlock(new BlockPos(16, 2, 4), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.COMPOSTER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, player, HALL);
			helper.assertTrue(name(menu.icon(VillageHallScreen.FIRST_ROW)).startsWith("Composter ×2: "), "setup: the two composters' line: " + name(menu.icon(VillageHallScreen.FIRST_ROW)));
			// the line's composter (the nearer, the one its picker is for) broken with its picker open
			menu.press(VillageHallScreen.FIRST_ROW, player);
			BlockPos first = nearestComposter(helper);
			helper.getLevel().setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
			menu.press(jobSlot(helper, menu, ModVillagers.ORCHARD_KEEPER), player);
			helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "a broken block's job opened a page: " + name(menu.icon(4)));
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE, "given a job at a broken block: " + villager.getVillagerData().getProfession());
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.COMPOSTER) && menu.icon(VillageHallScreen.FIRST_ROW + 1).isEmpty(),
				"the list after: " + menu.icon(VillageHallScreen.FIRST_ROW) + ", " + menu.icon(VillageHallScreen.FIRST_ROW + 1));
			// the other broken with its takers page open
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(jobSlot(helper, menu, ModVillagers.ORCHARD_KEEPER), player);
			BlockPos second = nearestComposter(helper);
			helper.getLevel().setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"given a job at a broken block: " + villager.getVillagerData().getProfession() + " " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE) && name(menu.icon(VillageHallScreen.FIRST_ROW + 4)).equals("No free workstations in the village"),
				"the empty list: " + name(menu.icon(VillageHallScreen.FIRST_ROW + 4)));
			helper.succeed();
		});
	}

	private static BlockPos nearestComposter(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(HALL);
		return VillageHalls.freeStations(helper.getLevel(), hall).stream().map(VillageHalls.FreeStation::pos)
			.filter(p -> helper.getLevel().getBlockState(p).is(Blocks.COMPOSTER)).findFirst()
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no free composter"));
	}

	/**
	 * Grouping is by kind and building: a composter in each of two Wells is two lines (each "Composter: N jobs", not
	 * ×2), and a barrel beside the first Well's composter is a line of its own; nearest the hall first.
	 */
	//$ gametest_ticks_batch AREA_HUGE '100' '"qaHbTwoWells"'
	@GameTest(template = AREA_HUGE, timeoutTicks = 100, batch = "qaHbTwoWells")
	public void sharedBlocksInTwoBuildingsAreTwoLines(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(13, 2, 13);
		stage(helper, hallRel, 1);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(hallRel);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement wellA = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, Mirror.NONE);
		BlueprintData.Placement wellB = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(20, 2, 20)), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.WELL.id(), wellA, UUID.randomUUID());
		sites.recordFinished(StarterBlueprints.WELL.id(), wellB, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			sites.forgetFinished(wellA);
			sites.forgetFinished(wellB);
		});
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.COMPOSTER); // Well A, furthest (14.1)
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.BARREL); // Well A (12.7)
		helper.setBlock(new BlockPos(21, 2, 21), Blocks.COMPOSTER); // Well B, nearest (11.3)
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			int n = jobsAt(level, hall, helper.absolutePos(new BlockPos(21, 2, 21))).size();
			ChoiceMenu menu = openPage(helper, player, hallRel);
			String shared = "Composter: " + n + " jobs";
			ItemStack first = menu.icon(VillageHallScreen.FIRST_ROW);
			ItemStack second = menu.icon(VillageHallScreen.FIRST_ROW + 1);
			ItemStack third = menu.icon(VillageHallScreen.FIRST_ROW + 2);
			helper.assertTrue(first.is(Items.COMPOSTER) && name(first).equals(shared) && lore(first).get(0).startsWith("In the "), "line 1, Well B's composter: "
				+ name(first) + " " + lore(first));
			helper.assertTrue(second.is(Items.BARREL) && lore(second).get(0).equals(lore(first).get(0)), "line 2, Well A's barrel: " + name(second) + " " + lore(second));
			helper.assertTrue(third.is(Items.COMPOSTER) && name(third).equals(shared), "line 3, Well A's composter, not grouped with Well B's: " + name(third));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 3).isEmpty(), "a fourth line: " + menu.icon(VillageHallScreen.FIRST_ROW + 3));
			helper.assertTrue(lore(first).stream().noneMatch(s -> s.contains("free here")) && lore(third).stream().noneMatch(s -> s.contains("free here")),
				"a lone composter counted: " + lore(first) + " " + lore(third));
			helper.succeed();
		});
	}

	/**
	 * The Jeweller's switch: on, a stonecutter offers the Mason and the Jeweller; off, it doesn't offer the Jeweller. And
	 * switched off while the picker is open, a click on the Jeweller's line makes nobody a Jeweller.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHbJewellerOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHbJewellerOff")
	public void aJobSwitchedOffIsNotOfferedAtASharedBlock(GameTestHelper helper) {
		Villager villager = stage(helper, HALL, 1).get(0);
		boolean was = Jewellers.ENABLED;
		Jewellers.ENABLED = true;
		Leftovers.after(helper, () -> Jewellers.ENABLED = was);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(16, 2, 4), Blocks.STONECUTTER);
		BlockPos cutter = helper.absolutePos(new BlockPos(16, 2, 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			List<VillagerProfession> on = jobsAt(level, hall, cutter);
			helper.assertTrue(on.get(0) == VillagerProfession.MASON && on.contains(ModVillagers.JEWELLER), "setup: the stonecutter's jobs " + on);
			ChoiceMenu menu = openPage(helper, player, HALL);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			int jewellerSlot = jobSlot(helper, menu, ModVillagers.JEWELLER);
			Jewellers.ENABLED = false;
			menu.press(jewellerSlot, player);
			if (menu.icon(4).is(Items.STONECUTTER)) { // a takers page opened anyway: its click must not make a Jeweller
				menu.press(VillageHallScreen.FIRST_ROW, player);
			}
			helper.assertTrue(villager.getVillagerData().getProfession() != ModVillagers.JEWELLER, "made a Jeweller with the Jeweller switched off");
			List<VillagerProfession> off = jobsAt(level, hall, cutter);
			helper.assertTrue(!off.contains(ModVillagers.JEWELLER) && off.get(0) == VillagerProfession.MASON, "with the Jeweller off, the stonecutter's jobs " + off);
			ChoiceMenu again = openPage(helper, player, HALL);
			ItemStack line = again.icon(VillageHallScreen.FIRST_ROW);
			String expected = off.size() == 1 ? job(VillagerProfession.MASON) : "Stonecutter: " + off.size() + " jobs";
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE, "the villager was given " + villager.getVillagerData().getProfession());
			helper.assertTrue(name(line).equals(expected),
				"the stonecutter's line with the Jeweller off ('" + expected + "'): '" + name(line) + "'");
			helper.assertTrue(lore(line).stream().noneMatch(s -> s.equals("· " + job(ModVillagers.JEWELLER))), "the Jeweller still listed: " + lore(line));
			helper.succeed();
		});
	}

	// --- B304 helpers ------------------------------------------------------------------------------------------------

	/** The Terrace placed with {@code settings} at {@code origin}: its beds' heads by blueprint x (west home first) and the spawn spot in the east kitchen. */
	private record Terrace(BlockPos origin, List<BlockPos> beds, BlockPos eastKitchen) {
	}

	private static Terrace terrace(GameTestHelper helper, BlockPos at, Rotation rotation, long seed) {
		ServerLevel level = helper.getLevel();
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("terrace")).orElseThrow();
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
		BlockPos origin = helper.absolutePos(at);
		house.placeInWorld(level, origin, origin, settings, RandomSource.create(seed), 2);
		BoundingBox box = house.getBoundingBox(settings, origin);
		StructurePlaceSettings back = new StructurePlaceSettings().setRotation(Rotation.values()[(4 - rotation.ordinal()) % 4]);
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable)
			.sorted(Comparator.comparingInt(b -> StructureTemplate.calculateRelativePosition(back, b.subtract(origin)).getX())).toList();
		helper.assertTrue(beds.size() == 4, "setup: the Terrace's beds are " + beds.stream().map(helper::relativePos).toList());
		BlockPos kitchen = origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(8, 1, 5)));
		return new Terrace(origin, beds, kitchen);
	}

	private static Villager spawnAt(GameTestHelper helper, BlockPos abs) {
		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(abs));
		villager.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
		return villager;
	}

	private static String where(GameTestHelper helper, Villager v) {
		return "at " + helper.relativePos(v.blockPosition()).toShortString() + ", home "
			+ v.getBrain().getMemory(MemoryModuleType.HOME).map(g -> helper.relativePos(g.pos()).toShortString())
			+ ", activity " + v.getBrain().getActiveNonCoreActivity();
	}

	/**
	 * The Terrace turned a quarter (as a player's rotated blueprint places it): a villager without a bed in its east
	 * kitchen, the east home's beds someone else's, claims a free bed of the west home and sleeps in it at night.
	 */
	//$ gametest_ticks_batch AREA_HUGE '1200' '"qaHbTerraceTurned"'
	@GameTest(template = AREA_HUGE, timeoutTicks = 1200, batch = "qaHbTerraceTurned")
	public void aHomelessVillagerSleepsUpTheOtherLadderOfATurnedTerrace(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Terrace t = terrace(helper, new BlockPos(20, 2, 8), Rotation.CLOCKWISE_90, 3042L);
		for (BlockPos taken : t.beds().subList(2, 4)) {
			level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(taken), taken, 1);
		}
		List<BlockPos> free = t.beds().subList(0, 2);
		Villager villager = spawnAt(helper, t.eastKitchen());
		helper.setDayTime(13000);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> free.contains(g.pos())).isPresent(),
				"no free bed of the turned Terrace's west home claimed: " + where(helper, villager));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(free::contains).isPresent(), "not asleep in the west home: " + where(helper, villager));
		});
	}

	/**
	 * Saved and loaded before bedtime (the ladder chosen isn't saved): the west home's villager, standing in the east
	 * kitchen, still climbs his own home's ladder and sleeps in his bed.
	 */
	//$ gametest_ticks_batch AREA_HUGE '900' '"qaHbTerraceReload"'
	@GameTest(template = AREA_HUGE, timeoutTicks = 900, batch = "qaHbTerraceReload")
	public void aTerraceVillagerReloadedStillClimbsHisOwnLadder(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Terrace t = terrace(helper, new BlockPos(8, 2, 8), Rotation.NONE, 304L);
		BlockPos bed = t.beds().get(0);
		Villager first = spawnAt(helper, t.eastKitchen());
		level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
		first.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		Villager[] copy = new Villager[1];
		helper.runAfterDelay(5, () -> {
			CompoundTag tag = new CompoundTag();
			first.saveWithoutId(tag);
			first.discard();
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(tag);
			level.addFreshEntity(loaded);
			copy[0] = loaded;
			helper.setDayTime(13000);
		});
		helper.succeedWhen(() -> {
			Villager v = copy[0];
			helper.assertTrue(v != null, "not reloaded yet");
			helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.pos().equals(bed)).isPresent(), "his bed lost over the reload: " + where(helper, v));
			helper.assertTrue(v.isSleeping() && v.getSleepingPos().filter(bed::equals).isPresent(), "not asleep in his bed after a reload: " + where(helper, v));
		});
	}

	/** A bed on a 3-high stone tower at {@code x}, its ladder on the south face (as TerraceSleepGameTests'). Returns the bed's head. */
	private static BlockPos towerBed(GameTestHelper helper, int x) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int z = 2; z <= 4; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x + dx, y, z), Blocks.STONE);
				}
			}
		}
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(x, y, 5), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		helper.setBlock(new BlockPos(x, 5, 4), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 5, 3), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(new BlockPos(x, 5, 3));
	}

	static Villager still(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setOnGround(true);
		return villager;
	}

	static Set<Pair<Holder<PoiType>, BlockPos>> home(ServerLevel level, BlockPos bed) {
		return Set.of(Pair.of(level.getPoiManager().getType(bed).orElseThrow(), bed));
	}

	/**
	 * Both sides of "free": a bed up a ladder that someone else has claimed isn't claimable; freed, it is; with the
	 * ladder taken down (broken mid-way through the night's plans), it isn't again.
	 */
	//$ gametest_ticks_batch AREA_HUGE '200' '"qaHbLadderClaim"'
	@GameTest(template = AREA_HUGE, timeoutTicks = 200, batch = "qaHbLadderClaim")
	public void aBedUpALadderIsClaimableOnlyWhileFreeAndClimbable(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bed = towerBed(helper, 12);
		Villager villager = still(helper, new BlockPos(14, 2, 10));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(level.getPoiManager().getType(bed).isPresent(), "setup: the bed isn't a home yet"))
			.thenExecute(() -> {
				Path open = BedLadders.pathUpALadder(villager, home(level, bed));
				helper.assertTrue(open != null && open.getTarget().equals(bed) && open.canReach(), "setup: the free bed up its ladder isn't claimable: " + open);
				level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
				helper.assertTrue(BedLadders.pathUpALadder(villager, home(level, bed)) == null, "a bed someone else claimed was claimable up its ladder");
				level.getPoiManager().release(bed);
				Path freed = BedLadders.pathUpALadder(villager, home(level, bed));
				helper.assertTrue(freed != null && freed.getTarget().equals(bed), "the bed freed again isn't claimable: " + freed);
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(12, y, 5), Blocks.AIR);
				}
				helper.assertTrue(BedLadders.pathUpALadder(villager, home(level, bed)) == null, "the bed was claimable with its ladder taken down");
				helper.assertTrue(BedLadders.pathUpALadder(villager, Set.of()) == null, "an empty set of beds gave a path");
			})
			.thenSucceed();
	}

	/**
	 * A wall between the ladder's top and the bed, as wide as the walk's reach: an oak door in it (shut) lets the walk
	 * through, so the bed is claimable. Expected for an iron door (which a villager can't open): not claimable.
	 */
	private static void doorWall(GameTestHelper helper, int x, Block door) {
		for (int wx = x - 9; wx <= x + 9; wx++) {
			if (wx != x) {
				helper.setBlock(new BlockPos(wx, 5, 1), Blocks.STONE);
				helper.setBlock(new BlockPos(wx, 6, 1), Blocks.STONE);
			}
		}
		helper.setBlock(new BlockPos(x, 5, 1), door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(x, 6, 1), door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** A 5-deep tower with its ladder on the south face and a bed (east-west) at its north end, behind {@link #doorWall}. Returns the bed's head. */
	static BlockPos walledTowerBed(GameTestHelper helper, int x, Block door) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int z = 0; z <= 4; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x + dx, y, z), Blocks.STONE);
				}
			}
		}
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(x, y, 5), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		helper.setBlock(new BlockPos(x + 1, 5, 0), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 5, 0), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.HEAD));
		doorWall(helper, x, door);
		return helper.absolutePos(new BlockPos(x, 5, 0));
	}

	//$ gametest_ticks_batch AREA_HUGE '200' '"qaHbOakDoor"'
	@GameTest(template = AREA_HUGE, timeoutTicks = 200, batch = "qaHbOakDoor")
	public void aBedBehindAShutOakDoorUpALadderIsClaimable(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bed = walledTowerBed(helper, 13, Blocks.OAK_DOOR);
		Villager villager = still(helper, new BlockPos(15, 2, 9));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(level.getPoiManager().getType(bed).isPresent(), "setup: the bed isn't a home yet"))
			.thenExecute(() -> {
				helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(13, 6, 1))).getBlock() instanceof DoorBlock, "setup: the door's top half is gone");
				Path p = BedLadders.pathUpALadder(villager, home(level, bed));
				helper.assertTrue(p != null && p.getTarget().equals(bed), "a bed behind a shut oak door, up a ladder, isn't claimable: " + p);
				helper.setBlock(new BlockPos(13, 5, 1), Blocks.STONE); // the doorway walled up
				helper.setBlock(new BlockPos(13, 6, 1), Blocks.STONE);
				helper.assertTrue(BedLadders.pathUpALadder(villager, home(level, bed)) == null, "the bed was claimable with its doorway walled up");
			})
			.thenSucceed();
	}

	/** Expected: a ladder with its middle rung broken can't be climbed, so the bed at its top isn't claimable up it. */
	//$ gametest_ticks_batch AREA_HUGE '200' '"qaHbRungGap"'
	@GameTest(template = AREA_HUGE, timeoutTicks = 200, batch = "qaHbRungGap")
	public void aBedUpALadderWithAMissingRungIsNotClaimable(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bed = towerBed(helper, 12);
		helper.setBlock(new BlockPos(12, 3, 5), Blocks.AIR);
		Villager villager = still(helper, new BlockPos(14, 2, 10));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(level.getPoiManager().getType(bed).isPresent(), "setup: the bed isn't a home yet"))
			.thenExecute(() -> {
				Path p = BedLadders.pathUpALadder(villager, home(level, bed));
				helper.assertTrue(p == null, "a bed up a ladder with its middle rung missing was claimable");
			})
			.thenSucceed();
	}
}
