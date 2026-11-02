package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Bugs B803 and B807: the job block a jobless villager walks to on their own (vanilla holds it for them on the way) is
 * free for them, by a job item and in the hall's Job list. These are the other edges: it's free for them only (another
 * villager isn't given it), and a Blueprint Table they walk to makes them the Builder.
 */
public class B803GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COMPOSTER = new BlockPos(6, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 5);
	private static final BlockPos OTHER = new BlockPos(6, 2, 6);

	/** As vanilla's AcquirePoi does when a jobless villager picks a block to walk to: its place held, remembered. */
	private static void walkTo(GameTestHelper helper, Villager villager, BlockPos block) {
		ServerLevel level = helper.getLevel();
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		helper.assertTrue(level.getPoiManager().take(h -> true, (h, p) -> p.equals(block), block, 1).isPresent(), "setup: " + block + " couldn't be held");
		villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), block));
	}

	/** Another villager, handed sweet berries by the composter someone else walks to, is refused: it stays the walker's. */
	//$ gametest_ticks_batch AREA '100' '"b803Other"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b803Other")
	public void theBlockSomeoneElseWalksToIsNotFreeForAnother(GameTestHelper helper) {
		Leftovers.clear(helper);
		PoiManager poi = helper.getLevel().getPoiManager();
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		BlockPos composter = helper.absolutePos(COMPOSTER);
		Villager walker = helper.spawn(EntityType.VILLAGER, STANDING);
		Villager other = helper.spawn(EntityType.VILLAGER, OTHER);
		helper.runAfterDelay(2, () -> {
			other.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
			walkTo(helper, walker, composter);
			StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), other, new ItemStack(Items.SWEET_BERRIES), true);
			helper.assertTrue(other.getVillagerData().getProfession() != ModVillagers.ORCHARD_KEEPER
				&& other.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"the other villager was given the composter someone walks to: " + other.getVillagerData().getProfession());
			helper.assertTrue(walker.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(GlobalPos::pos).filter(composter::equals).isPresent()
				&& poi.getFreeTickets(composter) == 0, "the walker's composter was let go (free " + poi.getFreeTickets(composter) + ")");
			helper.succeed();
		});
	}

	/** A jobless villager walking to a Blueprint Table, handed a Blank Blueprint: the Builder there, the table held once. */
	//$ gametest_ticks_batch AREA '100' '"b803Table"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b803Table")
	public void aBlueprintTableTheyWalkToMakesThemTheBuilder(GameTestHelper helper) {
		Leftovers.clear(helper);
		PoiManager poi = helper.getLevel().getPoiManager();
		helper.setBlock(COMPOSTER, ModBlocks.BLUEPRINT_TABLE);
		BlockPos table = helper.absolutePos(COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.runAfterDelay(2, () -> {
			walkTo(helper, villager, table);
			StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), villager, new ItemStack(ModItems.BLANK_BLUEPRINT), true);
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.BUILDER
				&& table.equals(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"not made the Builder at the table they walked to: " + villager.getVillagerData().getProfession());
			helper.assertTrue(poi.getFreeTickets(table) == 0 && villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(),
				"the table isn't held once, for them (free " + poi.getFreeTickets(table) + ")");
			helper.succeed();
		});
	}

	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);

	/** In the hall, another jobless villager's Mason line still reads "place a Stonecutter first": it's Pip's, on his way. */
	//$ gametest_ticks_batch HUGE '200' '"b807Other"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "b807Other")
	public void theHallOffersTheWalkedToBlockToNobodyElse(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager pip = QaHallScreenGameTests.villager(helper, "Pip", new BlockPos(20, 2, 20));
		Villager ann = QaHallScreenGameTests.villager(helper, "Ann", new BlockPos(18, 2, 20));
		QaHallScreenGameTests.bed(helper, new BlockPos(10, 2, 10));
		QaHallScreenGameTests.bed(helper, new BlockPos(12, 2, 10));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		BlockPos cutter = helper.absolutePos(STONECUTTER);
		helper.runAfterDelay(3, () -> {
			walkTo(helper, pip, cutter);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, ann);
			List<ItemStack> lines = HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, ann)));
			ItemStack mason = QaHallScreenGameTests.jobLine(helper, lines, VillagerProfession.MASON);
			helper.assertTrue(mason.getHoverName().getString().equals("Mason · place a Stonecutter first"),
				"Ann's Mason line, the only Stonecutter Pip's on his way: '" + mason.getHoverName().getString() + "'");
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(ann.getVillagerData().getProfession() == VillagerProfession.NONE, "Ann was made " + ann.getVillagerData().getProfession());
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(cutter) == 0
				&& pip.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent(), "Pip's Stonecutter was let go");
			helper.succeed();
		});
	}
}
