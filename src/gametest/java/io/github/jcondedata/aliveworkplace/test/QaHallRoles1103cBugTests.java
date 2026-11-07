package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-c-1103-1253): the Village Hall's People table (ROADMAP 40.3 Job ▾, 40.4 Bed ▾) used on villagers who hold
 * other roles, every change through the hall's real menu clicks: the village's Steward retrained or given No job (the
 * hall's place freed for a new Steward), a Legend (whose job can't be changed), a retired elder (never re-employed,
 * 34.20), No job then a job again (nothing charged, the block free for the next), a worker's bed kept through a job
 * change, a swap with a partner asleep in his bed (woken first), a sleeper moved to a free bed, a dead villager's bed
 * given to the next, a settlers' wagon bed left off the list, and a price paid from emeralds split over stacks.
 */
public class QaHallRoles1103cBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos LECTERN = new BlockPos(6, 2, 6);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);
	private static final BlockPos NEAR = new BlockPos(10, 2, 10);
	private static final BlockPos FAR = new BlockPos(20, 2, 18);
	private static final BlockPos THIRD = new BlockPos(18, 2, 8);
	private static final ResourceLocation OLD_SAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "old_sage");

	private static Villager librarian(GameTestHelper helper, String name, int level) {
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager v = QaHallScreenGameTests.villager(helper, name, LECTERN.offset(1, 0, 1));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		v.setVillagerData(v.getVillagerData().setLevel(level));
		v.setVillagerXp(level == 1 ? 5 : 70 * level);
		return v;
	}

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static CompoundTag row(GameTestHelper helper, ChoiceMenu menu, Villager v) {
		return HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, v)));
	}

	private static List<ItemStack> bedDropdown(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager v) {
		helper.assertTrue(menu.clickMenuButton(player, HallBeds.OPEN + QaHallScreenGameTests.slotOf(helper, menu, v)), "Bed ▾ wasn't taken");
		return HallBeds.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, v)));
	}

	private static String state(ItemStack line) {
		return HallBeds.mark(line).getString("state");
	}

	private static ItemStack firstBed(GameTestHelper helper, List<ItemStack> lines, String state) {
		for (ItemStack line : lines) {
			if (state(line).equals(state)) {
				return line;
			}
		}
		helper.fail("no " + state + " line in " + names(lines));
		return ItemStack.EMPTY;
	}

	private static List<String> names(List<ItemStack> lines) {
		return lines.stream().map(l -> l.getHoverName().getString()).toList();
	}

	private static void pickBed(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, ItemStack line) {
		helper.assertTrue(menu.clickMenuButton(player, HallBeds.PICK + HallBeds.mark(line).getInt("id")), "the pick wasn't taken");
	}

	private static int free(GameTestHelper helper, BlockPos absolute) {
		return helper.getLevel().getPoiManager().getFreeTickets(absolute);
	}

	private static boolean occupied(GameTestHelper helper, BlockPos absolute) {
		var s = helper.getLevel().getBlockState(absolute);
		return s.hasProperty(BedBlock.OCCUPIED) && s.getValue(BedBlock.OCCUPIED);
	}

	// --- Job ▾ on villagers with roles (40.3) ---------------------------------------------------------------------------

	/**
	 * The village's Steward retrained as a Mason from his row (Novice, 5 emeralds): he's a Mason at the stonecutter, the
	 * hall's Steward place is free again (no Steward of this hall, the hall's one ticket free, so another can be named),
	 * and the City Plan he carried on his rounds isn't left in a Mason's hand (as the Steward's dismissal takes it).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrStewardRetrained"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrStewardRetrained")
	public void theStewardRetrainedFreesTheHall(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager sam = QaHallScreenGameTests.villager(helper, "Sam", new BlockPos(13, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		helper.runAfterDelay(3, () -> {
			io.github.jcondedata.aliveworkplace.work.Stations.assign(level, sam, hall, ModVillagers.STEWARD);
			sam.setVillagerData(sam.getVillagerData().setProfession(ModVillagers.STEWARD).setLevel(1));
			sam.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CITY_PLAN));
			helper.assertTrue(Stewards.stewardOf(level, hall) == sam && free(helper, hall) == 0, "setup: Sam isn't the Steward: " + free(helper, hall));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, sam);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(sam.getVillagerData().getProfession() == VillagerProfession.MASON && emeralds(player) == 0,
				"not retrained: " + sam.getVillagerData() + ", " + emeralds(player) + " left");
			helper.assertTrue(QaHallScreenGameTests.site(sam).equals(Optional.of(helper.absolutePos(STONECUTTER))), "his site: " + QaHallScreenGameTests.site(sam));
			helper.assertTrue(Stewards.stewardOf(level, hall) == null, "the hall still has a Steward");
			helper.assertTrue(free(helper, hall) == 1, "the hall's Steward place wasn't freed: " + free(helper, hall));
			helper.assertTrue(!sam.getItemBySlot(EquipmentSlot.MAINHAND).is(ModItems.CITY_PLAN), "a Mason still carries the Steward's City Plan");
			helper.succeed();
		});
	}

	/**
	 * A retired elder is never re-employed (34.20: "WorkerLimits.refuse turns down any job for a retired villager (by
	 * themselves at a workstation, or by order)"): a pick on his row leaves him jobless, the stonecutter free for someone
	 * else, and the player isn't told he's now a Mason.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrRetired"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrRetired")
	public void aRetiredElderIsNotGivenAJob(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager bram = QaHallScreenGameTests.villager(helper, "Bram", new BlockPos(13, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 10);
		helper.runAfterDelay(3, () -> {
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(Chronicle.day(level) - 1, "minecraft:farmer", 3));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, bram);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "the retired elder works again: " + bram.getVillagerData());
			helper.assertTrue(free(helper, helper.absolutePos(STONECUTTER)) == 1 && QaHallScreenGameTests.site(bram).isEmpty(),
				"the stonecutter is held for a retired elder: " + QaHallScreenGameTests.site(bram));
			helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("Bram is now a")), "told he has a job: " + seen);
			helper.assertTrue(emeralds(player) == 10, "money taken");
			helper.assertTrue(seen.contains("Bram has retired and takes no job now: they mentor the young instead."), "not told why: " + seen);
			helper.succeed();
		});
	}

	/** B885's other side: the same pick for a jobless villager who never retired still gives the job, for nothing. */
	//$ gametest_ticks_batch AREA '200' '"qaHrNotRetired"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrNotRetired")
	public void aJoblessVillagerIsStillGivenTheJob(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager bram = QaHallScreenGameTests.villager(helper, "Bram", new BlockPos(13, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 10);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, bram);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.MASON, "not a Mason: " + bram.getVillagerData());
			helper.assertTrue(free(helper, helper.absolutePos(STONECUTTER)) == 0
				&& QaHallScreenGameTests.site(bram).equals(Optional.of(helper.absolutePos(STONECUTTER))), "the stonecutter isn't his: " + QaHallScreenGameTests.site(bram));
			helper.assertTrue(seen.contains("Bram is now a Mason"), "not told: " + seen);
			helper.assertTrue(emeralds(player) == 10, "money taken");
			helper.succeed();
		});
	}

	/** B886 through No job: the Steward given No job puts the City Plan down too, as a dismissal does, and the hall is free. */
	//$ gametest_ticks_batch AREA '200' '"qaHrStewardNoJobPlan"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrStewardNoJobPlan")
	public void theStewardGivenNoJobPutsThePlanDown(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager sam = QaHallScreenGameTests.villager(helper, "Sam", new BlockPos(13, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			io.github.jcondedata.aliveworkplace.work.Stations.assign(level, sam, hall, ModVillagers.STEWARD);
			sam.setVillagerData(sam.getVillagerData().setProfession(ModVillagers.STEWARD).setLevel(2));
			sam.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CITY_PLAN));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, sam);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(sam.getItemBySlot(EquipmentSlot.MAINHAND).is(ModItems.CITY_PLAN), "the first click of No job took the plan");
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(sam.getVillagerData().getProfession() == VillagerProfession.NONE, "still a Steward: " + sam.getVillagerData());
			helper.assertTrue(sam.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "the jobless Sam still holds " + sam.getItemBySlot(EquipmentSlot.MAINHAND));
			helper.assertTrue(Stewards.stewardOf(level, hall) == null && free(helper, hall) == 1, "the hall's place is still held: " + free(helper, hall));
			helper.succeed();
		});
	}


	static int professionId(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}
}