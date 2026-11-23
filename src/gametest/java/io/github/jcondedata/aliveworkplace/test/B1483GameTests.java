package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * B1483 (and B1470): a traveller arriving at an inn was made a nitwit, but kept the job-hunting brain he was created
 * with (a villager's brain is built for the jobless, and changing the trade doesn't rebuild it). Until the world was
 * next loaded he claimed the nearest free job block like a jobless villager: he never took the job (a nitwit can't), and
 * never gave the block up (only the jobless hand one over), so the block looked free and a real jobless villager stood
 * beside it without work. Now a traveller arrives with a nitwit's brain and leaves job blocks alone.
 *
 * <p>How it showed: StationsSpecGameTests.theKeptBlocksTakeAJoblessVillagerByThemselves failed now and then with
 * 'nobody works at the storehouse' (or the travel post). When the Shop Counter's villager turned Innkeeper (one time in
 * two) and a bed an earlier batch left stood within the inn's reach (only in a full run), a traveller arrived in the
 * morning and claimed one of the two other blocks before its own villager did.
 */
public class B1483GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos COUNTER = new BlockPos(11, 2, 11);
	private static final BlockPos JOB_BLOCK = new BlockPos(11, 2, 7);

	private static boolean free(GameTestHelper helper, BlockPos block) {
		return helper.getLevel().getPoiManager().getInRange(t -> true, helper.absolutePos(block), 0, PoiManager.Occupancy.HAS_SPACE).findAny().isPresent();
	}

	private static String claims(Villager v) {
		return "job site " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).map(BlockPos::toShortString).orElse("none")
			+ ", pick " + v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(GlobalPos::pos).map(BlockPos::toShortString).orElse("none");
	}

	/**
	 * A traveller who arrives with a free job block a few steps away doesn't claim it: it stays free, and a jobless
	 * villager who comes later takes it.
	 */
	//$ gametest_ticks_batch AREA '900' '"b1483TravellerLeavesJobBlocks"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "b1483TravellerLeavesJobBlocks")
	public void aTravellerLeavesAFreeJobBlockToTheJobless(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(COUNTER, ModBlocks.INN_COUNTER);
		helper.setBlock(JOB_BLOCK, ModBlocks.STOREHOUSE);
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(level, innkeeper, helper.absolutePos(COUNTER), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
		Villager guest = Innkeepers.arrive(level, innkeeper, helper.absolutePos(COUNTER));
		helper.assertTrue(guest != null && Innkeepers.isTraveller(guest), "(setup) no traveller arrived");
		helper.assertTrue(guest.getVillagerData().getProfession() == VillagerProfession.NITWIT, "(setup) the traveller isn't a nitwit");
		helper.assertTrue(free(helper, JOB_BLOCK), "(setup) the Storehouse isn't a free job block");
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(free(helper, JOB_BLOCK), "the Storehouse was claimed with no jobless villager about; the traveller: " + claims(guest));
			helper.assertTrue(guest.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()
				&& guest.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(), "the traveller claims a job block: " + claims(guest));
			Villager jobless = helper.spawn(EntityType.VILLAGER, JOB_BLOCK.offset(1, 0, 1));
			jobless.setOnGround(true); // B1478: a mob in the air on its first tick skips the nearest job blocks for a while
			helper.succeedWhen(() -> {
				helper.assertTrue(jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(JOB_BLOCK)::equals).isPresent(),
					"the jobless villager didn't take the Storehouse: " + claims(jobless) + "; the traveller: " + claims(guest));
				helper.assertTrue(jobless.getVillagerData().getProfession() == ModVillagers.PORTER, "not a Porter");
			});
		});
	}

	/**
	 * The failing run itself: the three kept blocks with a jobless villager each, the Shop Counter's an Innkeeper, and a
	 * free bed within the inn's reach, so a traveller arrives in the morning. Every block still gets its own villager.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"b1483KeptBlocksWithATraveller"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "b1483KeptBlocksWithATraveller")
	public void theKeptBlocksKeepTheirVillagersWhenATravellerArrives(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos counter = new BlockPos(3, 2, 3);
		BlockPos storehouse = new BlockPos(25, 2, 3);
		BlockPos post = new BlockPos(3, 2, 25);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(post, ModBlocks.TRAVEL_POST);
		for (int x = 10; x <= 18; x += 2) { // a bed each for the four of them, and one free for the traveller
			helper.setBlock(new BlockPos(x, 2, 14), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(new BlockPos(x, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, counter.offset(1, 0, 1));
		Jobs.employ(level, innkeeper, helper.absolutePos(counter), ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
		// B1557: put down on the ground. One in the air on his first tick can't path to his own block (B1478) and marks it
		// failed for a while, so on a loaded run the two could swap blocks (the porter walked to the Travel Post) and
		// the check below, which wants each at his own block, failed.
		Villager porter = helper.spawn(EntityType.VILLAGER, storehouse.offset(1, 0, 1));
		porter.setOnGround(true);
		Villager ferryman = helper.spawn(EntityType.VILLAGER, post.offset(1, 0, 1));
		ferryman.setOnGround(true);
		helper.succeedWhen(() -> {
			java.util.List<Villager> guests = Innkeepers.guests(level, helper.absolutePos(counter));
			helper.assertTrue(!guests.isEmpty(), "no traveller has arrived yet");
			helper.assertTrue(helper.getTick() >= 1200, "waiting: the traveller's job search runs for a while");
			String traveller = "; the traveller: " + claims(guests.get(0)) + "; trades: porter " + porter.getVillagerData().getProfession()
				+ ", ferryman " + ferryman.getVillagerData().getProfession() + "; the ferryman: " + claims(ferryman) + "; blocks at " + helper.absolutePos(storehouse).toShortString()
				+ " and " + helper.absolutePos(post).toShortString();
			helper.assertTrue(porter.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(storehouse)::equals).isPresent()
				&& porter.getVillagerData().getProfession() == ModVillagers.PORTER, "nobody works at the Storehouse: " + claims(porter) + traveller);
			helper.assertTrue(ferryman.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(post)::equals).isPresent()
				&& ferryman.getVillagerData().getProfession() == ModVillagers.FERRYMAN, "nobody works at the Travel Post: " + claims(ferryman) + traveller);
			for (Villager guest : guests) {
				helper.assertTrue(guest.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()
					&& guest.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(), "a traveller claims a job block: " + claims(guest));
			}
		});
	}
}
