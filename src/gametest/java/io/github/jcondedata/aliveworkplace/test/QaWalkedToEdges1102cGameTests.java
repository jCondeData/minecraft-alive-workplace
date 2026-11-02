package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallJobs;
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
 * QA for B803 and B807 from their specs ("the block he walks to counts as free for him; he becomes the Orchard Keeper at
 * it, the composter held once, for him"; "Mason · 1 free Stonecutter, and one click makes him the Mason at it") at the
 * edges the fix's own tests leave: the walked-to block is one of several (counted for him and not for another, and
 * the one he isn't given is let go), a nearer free block wins over the walked-to one, and a walked-to block out of the
 * job item's reach is neither given nor let go.
 */
public class QaWalkedToEdges1102cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** As vanilla's AcquirePoi does when a jobless villager picks a block to walk to: its place held, remembered. */
	private static void walkTo(GameTestHelper helper, Villager villager, BlockPos block) {
		ServerLevel level = helper.getLevel();
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		helper.assertTrue(level.getPoiManager().take(h -> true, (h, p) -> p.equals(block), block, 1).isPresent(), "setup: " + block + " couldn't be held");
		villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), block));
	}

	private static Villager still(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	/**
	 * Walking to a composter 3 blocks off with a free one right beside him: sweet berries make him the Orchard Keeper at
	 * the nearer one, and the composter he was walking to is let go (free again), not held for nobody.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaWalkedToNearer1102c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaWalkedToNearer1102c")
	public void qaANearerFreeComposterWinsAndTheWalkedToOneIsLetGo(GameTestHelper helper) {
		Leftovers.clear(helper);
		PoiManager poi = helper.getLevel().getPoiManager();
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(4, 2, 8), Blocks.COMPOSTER);
		BlockPos near = helper.absolutePos(new BlockPos(5, 2, 5));
		BlockPos walked = helper.absolutePos(new BlockPos(4, 2, 8));
		Villager villager = still(helper, new BlockPos(4, 2, 5));
		helper.runAfterDelay(2, () -> {
			villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
			walkTo(helper, villager, walked);
			StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), villager, new ItemStack(Items.SWEET_BERRIES), true);
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER
				&& StationsSpecGameTests.site(villager).filter(near::equals).isPresent(),
				"not the Orchard Keeper at the composter beside him: " + villager.getVillagerData().getProfession() + " at " + StationsSpecGameTests.site(villager));
			helper.assertTrue(poi.getFreeTickets(near) == 0, "the composter he was given isn't held (free " + poi.getFreeTickets(near) + ")");
			helper.assertTrue(poi.getFreeTickets(walked) == 1 && villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(),
				"the composter he walked to stays held for nobody (free " + poi.getFreeTickets(walked) + ")");
			helper.succeed();
		});
	}

	/**
	 * Walking to the only composter, 8 blocks off (out of the job item's reach): sweet berries are refused with "stand them
	 * next to one" (no composter here is anyone's), and his walk is left alone: the composter still held for him.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaWalkedToFar1102c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaWalkedToFar1102c")
	public void qaAWalkedToComposterOutOfReachIsNeitherGivenNorLetGo(GameTestHelper helper) {
		Leftovers.clear(helper);
		PoiManager poi = helper.getLevel().getPoiManager();
		helper.setBlock(new BlockPos(12, 2, 4), Blocks.COMPOSTER);
		BlockPos far = helper.absolutePos(new BlockPos(12, 2, 4));
		Villager villager = still(helper, new BlockPos(4, 2, 4));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		helper.runAfterDelay(2, () -> {
			walkTo(helper, villager, far);
			StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.SWEET_BERRIES), true);
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE
				&& villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"given a composter 8 blocks off: " + villager.getVillagerData().getProfession() + " at " + StationsSpecGameTests.site(villager));
			helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("The Orchard Keeper job needs a Composter: stand them next to one")),
				"the refusal for a composter out of reach; saw: " + seen);
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(GlobalPos::pos).filter(far::equals).isPresent()
				&& poi.getFreeTickets(far) == 0, "the refusal let go of his walk (free " + poi.getFreeTickets(far) + ")");
			helper.succeed();
		});
	}

	/**
	 * Two free Stonecutters, Pip walking to one: his Mason line counts both, Ann's counts one (Pip's is his). One click
	 * makes Pip the Mason at one of them; the other is free again, and Ann's click then makes her the Mason at it. Each
	 * Stonecutter ends up held once.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaWalkedToTwoCutters1102c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaWalkedToTwoCutters1102c")
	public void qaTwoStonecuttersOneWalkedToAreCountedForEachAndBothEndUpHeldOnce(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(new BlockPos(22, 2, 22), Blocks.STONECUTTER);
		helper.setBlock(new BlockPos(20, 2, 20), Blocks.STONECUTTER);
		Villager pip = QaHallScreenGameTests.villager(helper, "Pip", new BlockPos(23, 2, 20));
		Villager ann = QaHallScreenGameTests.villager(helper, "Ann", new BlockPos(18, 2, 20));
		QaHallScreenGameTests.bed(helper, new BlockPos(10, 2, 10));
		QaHallScreenGameTests.bed(helper, new BlockPos(12, 2, 10));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		PoiManager poi = helper.getLevel().getPoiManager();
		BlockPos walked = helper.absolutePos(new BlockPos(22, 2, 22));
		BlockPos other = helper.absolutePos(new BlockPos(20, 2, 20));
		helper.runAfterDelay(3, () -> {
			walkTo(helper, pip, walked);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, ann);
			String annsLine = QaHallScreenGameTests.jobLine(helper, HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, ann))),
				VillagerProfession.MASON).getHoverName().getString();
			helper.assertTrue(annsLine.startsWith("Mason · 1 free Stonecutter") && !annsLine.contains("Stonecutters"),
				"Ann's Mason line, one of the two Stonecutters Pip's on his way to: '" + annsLine + "'");
			menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, pip);
			String pipsLine = QaHallScreenGameTests.jobLine(helper, HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, pip))),
				VillagerProfession.MASON).getHoverName().getString();
			helper.assertTrue(pipsLine.startsWith("Mason · 2 free Stonecutters"), "Pip's Mason line, one of the two his: '" + pipsLine + "'");
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			BlockPos pips = QaHallScreenGameTests.site(pip).orElse(null);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.MASON && (walked.equals(pips) || other.equals(pips)),
				"one click didn't make Pip the Mason at a Stonecutter: " + pip.getVillagerData().getProfession() + " at " + pips + "; told " + seen);
			BlockPos left = walked.equals(pips) ? other : walked;
			helper.assertTrue(poi.getFreeTickets(pips) == 0 && poi.getFreeTickets(left) == 1
				&& pip.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(),
				"after Pip's click: his Stonecutter free " + poi.getFreeTickets(pips) + ", the other free " + poi.getFreeTickets(left) + " (want 0 and 1)");
			menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, ann);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(ann.getVillagerData().getProfession() == VillagerProfession.MASON
				&& QaHallScreenGameTests.site(ann).filter(left::equals).isPresent(),
				"Ann wasn't made the Mason at the Stonecutter Pip left: " + ann.getVillagerData().getProfession() + " at " + QaHallScreenGameTests.site(ann)
					+ "; told " + seen);
			helper.assertTrue(poi.getFreeTickets(pips) == 0 && poi.getFreeTickets(left) == 0,
				"each Stonecutter held once: free " + poi.getFreeTickets(pips) + " and " + poi.getFreeTickets(left));
			helper.succeed();
		});
	}
}
