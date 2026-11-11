package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.*;
import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.ArrayList;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane C, 2026-11-11), the bug half of {@link QaMoveHallBed1111cGameTests}: a bed the player gives a resident from
 * the Village Hall's People page while his house is on the move is undone when the Mover puts the house's bed up again;
 * a worker let go at the Hall while his workstation is packed is given it back when it goes up.
 */
public class QaMoveHallBed1111cBugTests implements FabricGameTest {
	/**
	 * The player gives Tomas the bed in the open from the Hall while his house is packed; the Mover then puts the house up
	 * at its new spot. Tomas keeps the bed the player gave him (his home memory, the census's bed, its one place held for
	 * him), and the moved bed is free for someone else.
	 */
	//$ gametest_batch AREA '"qaMoveHallBedPick"'
	@GameTest(template = AREA, batch = "qaMoveHallBedPick")
	public void aBedGivenAtTheHallWhileTheHouseIsAwayIsKept(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "pick");
		helper.runAfterDelay(2, () -> {
			Villager tomas = tomas(helper, s);
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			helper.assertTrue(helper.absolutePos(INN_BED).equals(home(tomas)), "with his house packed Tomas sleeps at " + at(home(tomas)) + ", not the inn");
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			pickTheFreeBed(helper, player, tomas);
			BlockPos chosen = helper.absolutePos(OTHER_BED);
			helper.assertTrue(chosen.equals(home(tomas)), "the Hall's pick didn't give Tomas the bed in the open: he sleeps at " + at(home(tomas)));
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(INN_BED)) == 1, "the inn's bed wasn't let go when he moved out");
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(BED.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(moved).getBlock() instanceof BedBlock, "the moved bed isn't standing at " + at(moved));
			helper.assertTrue(chosen.equals(home(tomas)),
				"the Mover undid the player's Hall pick: Tomas was given the bed in the open at " + at(chosen) + " while his house was away, but now sleeps in the moved bed at " + at(home(tomas)));
			helper.assertTrue(chosen.equals(VillageNeeds.bed(level, tomas)), "the People page shows Tomas's bed as " + at(VillageNeeds.bed(level, tomas)));
			helper.assertTrue(level.getPoiManager().getFreeTickets(chosen) == 0, "the bed the player gave Tomas is free for anyone");
			helper.assertTrue(level.getPoiManager().getFreeTickets(moved) == 1, "the moved bed nobody sleeps in is held: " + level.getPoiManager().getFreeTickets(moved) + " places free");
			helper.succeed();
		});
	}

	/**
	 * Juniper, a Librarian at the house's lectern, is let go at the Hall (Job ▾, No job, twice) while the lectern is packed.
	 * When the Mover puts the lectern up at the new spot he stays jobless as the player said, and the lectern is free for
	 * whoever takes the job (the Free job blocks page offers it), not held for him.
	 */
	//$ gametest_batch AREA '"qaMoveHallLetGo"'
	@GameTest(template = AREA, batch = "qaMoveHallLetGo")
	public void aWorkerLetGoAtTheHallWhileHisLecternIsAwayStaysJobless(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "letgo");
		helper.runAfterDelay(2, () -> {
			PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.CLOCKWISE_90, null));
			Villager juniper = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 6));
			juniper.setNoAi(true);
			juniper.setCustomName(Component.literal("Juniper"));
			Jobs.employ(level, juniper, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			juniper.setVillagerXp(5);
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			List<String> seen = new ArrayList<>();
			letGoAtTheHall(helper, listeningPlayer(helper, seen), juniper);
			helper.assertTrue(juniper.getVillagerData().getProfession() == VillagerProfession.NONE, "the Hall's No job didn't let Juniper go mid-move: " + juniper.getVillagerData().getProfession() + "; told " + seen);
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos lectern = s.before().at(LECTERN.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(lectern).is(Blocks.LECTERN), "the lectern isn't standing at " + at(lectern));
			String job = juniper.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().toShortString()).orElse("none");
			helper.assertTrue(juniper.getVillagerData().getProfession() == VillagerProfession.NONE && job.equals("none"),
				"the Mover gave the let-go Juniper his lectern back: he's a " + juniper.getVillagerData().getProfession() + " with his job site at " + job);
			helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 1, "the moved lectern is held for nobody's job: " + level.getPoiManager().getFreeTickets(lectern) + " places free");
			helper.succeed();
		});
	}
}
