package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LecternBlock;

/**
 * QA lane (qa-1003-1633), around bugs B8 and B17: three ways two villagers still end up on one block. Failing on main
 * (2026-10-03); see the bugs in ROADMAP.md that name these tests.
 */
public class QaB17BugGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B8's hand-over by item, with the new owner unloaded: an orchard keeper's composter is broken while they're away and
	 * put back, a second villager becomes its orchard keeper, then the second one's chunk unloads. The player sneak-right-
	 * clicks the first (25 blocks off, by a free composter) with bone meal: they must take the free one beside them, not
	 * the far one the unloaded second worker holds (two workers on one block), and that one must stay taken.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaB17ByHandUnloaded"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaB17ByHandUnloaded")
	public void qaAFarOffWorkerDoesntTakeTheBlockOfAnUnloadedOwnerByHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos old = new BlockPos(3, 2, 3);
		BlockPos oldAt = helper.absolutePos(old);
		helper.setBlock(old, Blocks.COMPOSTER);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(StationsSpecGameTests.job(first) == ModVillagers.ORCHARD_KEEPER, "setup: the first isn't an orchard keeper");
		BlockPos away = helper.absolutePos(new BlockPos(25, 2, 25));
		first.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		helper.setBlock(old, Blocks.AIR);
		BlockPos fresh = new BlockPos(26, 2, 25);
		helper.setBlock(fresh, Blocks.COMPOSTER);
		helper.runAfterDelay(5, () -> {
			helper.setBlock(old, Blocks.COMPOSTER);
			helper.runAfterDelay(2, () -> {
				Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
				StationsSpecGameTests.rightClick(player, second, new ItemStack(Items.SWEET_BERRIES), true);
				helper.assertTrue(StationsSpecGameTests.site(second).equals(Optional.of(oldAt)),
					"setup: the second works at " + StationsSpecGameTests.site(second));
				helper.assertTrue(StationsSpecGameTests.site(first).equals(Optional.of(oldAt)), "setup: the first already forgot the old composter");
				CompoundTag saved = new CompoundTag();
				helper.assertTrue(second.save(saved), "setup: the second villager couldn't be saved");
				second.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);

				StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.BONE_MEAL), true);
				helper.assertTrue(StationsSpecGameTests.job(first) == ModVillagers.COMPOSTER
						&& StationsSpecGameTests.site(first).equals(Optional.of(helper.absolutePos(fresh))),
					"the first, 1 block from a free composter, became a " + StationsSpecGameTests.name(StationsSpecGameTests.job(first))
						+ " at " + StationsSpecGameTests.site(first) + ": the composter at " + oldAt
						+ " is held by a second worker whose chunk is unloaded, so two workers share it");
				helper.assertTrue(level.getPoiManager().getFreeTickets(oldAt) == 0,
					"giving the first worker a new job by hand freed the unloaded second worker's composter");
				helper.succeed();
			});
		});
	}

	/**
	 * A worker's composter is broken while they're away and put back; they come back beside it and the player gives them
	 * another job there by hand (wheat). They now work at it, so they must hold its place: a third villager by it,
	 * sneak-right-clicked with sweet berries, must not be put on the same composter.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaB17ReplacedRetaken"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaB17ReplacedRetaken")
	public void qaAJobChosenAtAReplacedBlockTakesItsPlace(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos spot = new BlockPos(3, 2, 3);
		BlockPos at = helper.absolutePos(spot);
		helper.setBlock(spot, Blocks.COMPOSTER);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(StationsSpecGameTests.job(first) == ModVillagers.ORCHARD_KEEPER, "setup: the first isn't an orchard keeper");
		BlockPos away = helper.absolutePos(new BlockPos(25, 2, 25));
		first.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		helper.setBlock(spot, Blocks.AIR);
		helper.runAfterDelay(5, () -> {
			helper.setBlock(spot, Blocks.COMPOSTER);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(StationsSpecGameTests.site(first).equals(Optional.of(at)), "setup: the first already forgot the composter");
				BlockPos back = helper.absolutePos(new BlockPos(4, 2, 4));
				first.teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
				StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.WHEAT), true);
				helper.assertTrue(StationsSpecGameTests.job(first) == VillagerProfession.FARMER
						&& StationsSpecGameTests.site(first).equals(Optional.of(at)),
					"setup: wheat by the composter made the first a " + StationsSpecGameTests.name(StationsSpecGameTests.job(first))
						+ " at " + StationsSpecGameTests.site(first));
				int free = level.getPoiManager().getFreeTickets(at);
				Villager third = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3));
				StationsSpecGameTests.rightClick(player, third, new ItemStack(Items.SWEET_BERRIES), true);
				helper.assertFalse(StationsSpecGameTests.site(third).equals(Optional.of(at)),
					"a third villager was given the composter the first now works at (its place was left free: " + free
						+ " free ticket after the first was given a job there by hand)");
				helper.succeed();
			});
		});
	}

	/**
	 * The Village Hall offers a block that someone takes before the player picks it: the assignment fails, and the worker
	 * must keep their own block (still their job, still theirs), not be left working at a block that anyone can take.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaB17HallFails"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB17HallFails")
	public void qaAFailedHallAssignmentKeepsTheWorkersOwnBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		worker.setNoAi(true);
		Jobs.employ(level, worker, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		var farmer = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.FARMER);
		var offered = new VillageHalls.FreeStation(composter, VillagerProfession.FARMER, farmer);
		Villager quicker = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 9));
		quicker.setNoAi(true);
		Jobs.employ(level, quicker, composter, PoiTypes.FARMER, VillagerProfession.FARMER);

		helper.assertFalse(VillageHalls.assign(level, worker, offered), "the Hall gave the worker a composter someone else had taken");
		helper.assertTrue(StationsSpecGameTests.job(worker) == VillagerProfession.LIBRARIAN
				&& StationsSpecGameTests.site(worker).equals(Optional.of(lectern)),
			"after the failed assignment the worker is a " + StationsSpecGameTests.name(StationsSpecGameTests.job(worker)) + " at "
				+ StationsSpecGameTests.site(worker));
		int free = level.getPoiManager().getFreeTickets(lectern);
		helper.assertTrue(free == 0, "a failed Village Hall assignment freed the librarian's own lectern (" + free
			+ " free ticket) while they still work there: another villager can take it too");
		helper.succeed();
	}

}
