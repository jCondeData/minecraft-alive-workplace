package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B688: without Cobblemon (this run), the Pokémon jobs can't be picked by item. A book by a Training Post made a Move
 * Tutor, a gold block a Trainer Leader: jobs with nothing to do. Every case goes through the player's own sneak-right-click.
 */
public class B688GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(11, 2, 11);
	private static final BlockPos STANDING = new BlockPos(12, 2, 12);

	private static String job(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();
	}

	private static Villager sneakClick(GameTestHelper helper, ItemStack stack) {
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, v, stack, true);
		return v;
	}

	/** A book by a Training Post: no Move Tutor, the villager stays jobless and the post stays free (the bug's own case). */
	//$ gametest_ticks_batch AREA '60' '"b688Tutor"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "b688Tutor")
	public void aBookByATrainingPostMakesNoMoveTutor(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.assertTrue(!Stations.available(ModVillagers.TUTOR), "setup: this run has Cobblemon");
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		Villager v = sneakClick(helper, new ItemStack(Items.BOOK));
		helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.NONE,
			"a book by a Training Post gave a job without Cobblemon: " + job(v));
		helper.assertTrue(StationsSpecGameTests.site(v).isEmpty(), "the villager took the Training Post: " + StationsSpecGameTests.site(v));
		helper.assertTrue(Stations.freeAt(helper.getLevel(), helper.absolutePos(STATION)), "the Training Post isn't free");
		helper.succeed();
	}

	/** A gold block by a Training Post: no Trainer Leader either (one root cause for every Pokémon job). */
	//$ gametest_ticks_batch AREA '60' '"b688Leader"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "b688Leader")
	public void aGoldBlockByATrainingPostMakesNoTrainerLeader(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.assertTrue(!Stations.available(ModVillagers.TRAINER_LEADER), "setup: this run has Cobblemon");
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		Villager v = sneakClick(helper, new ItemStack(Items.GOLD_BLOCK));
		helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.NONE,
			"a gold block by a Training Post gave a job without Cobblemon: " + job(v));
		helper.assertTrue(!Stations.picksAny(new ItemStack(Items.GOLD_BLOCK)), "a gold block still picks a job without Cobblemon");
		helper.succeed();
	}

	/** A book by a Training Post and a lectern: the Teacher, the job the book can still pick. */
	//$ gametest_ticks_batch AREA '60' '"b688Teacher"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "b688Teacher")
	public void aBookByATrainingPostAndALecternMakesATeacher(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		helper.setBlock(STATION.east(2), Blocks.LECTERN);
		Villager v = sneakClick(helper, new ItemStack(Items.BOOK));
		helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.TEACHER, "a book by a lectern didn't make a Teacher: " + job(v));
		helper.assertTrue(StationsSpecGameTests.site(v).filter(p -> p.equals(helper.absolutePos(STATION.east(2)))).isPresent(),
			"the Teacher doesn't work at the lectern: " + StationsSpecGameTests.site(v));
		helper.succeed();
	}

	/** A normal job is untouched: a pickaxe by a blast furnace makes a Miner. */
	//$ gametest_ticks_batch AREA '60' '"b688Miner"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "b688Miner")
	public void aPickaxeByABlastFurnaceStillMakesAMiner(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, Blocks.BLAST_FURNACE);
		Villager v = sneakClick(helper, new ItemStack(Items.IRON_PICKAXE));
		helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.MINER, "a pickaxe by a blast furnace didn't make a Miner: " + job(v));
		helper.succeed();
	}
}
