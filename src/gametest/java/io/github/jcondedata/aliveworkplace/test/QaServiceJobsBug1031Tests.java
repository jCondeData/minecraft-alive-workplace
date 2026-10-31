package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.school.Schools;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** QA 2026-10-31: where the service jobs disagree with their guide pages (bug tests: they fail until fixed). */
public class QaServiceJobsBug1031Tests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(11, 2, 11);
	private static final BlockPos STANDING = new BlockPos(12, 2, 12);

	/**
	 * move-tutor.md: "They need Cobblemon installed"; tutor-s-desk.md: "Needs Cobblemon: without it there's nothing to
	 * teach". Without Cobblemon (this run), a book by a Training Post shouldn't make a Move Tutor; the Training Post's
	 * own tooltip already leaves the Tutor out ({@link Stations#available}).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaTutorNoCobblemon"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaTutorNoCobblemon")
	public void noMoveTutorWithoutCobblemon(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.assertTrue(!Stations.available(ModVillagers.TUTOR), "setup: this run has Cobblemon");
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.BOOK), true);
		helper.assertTrue(v.getVillagerData().getProfession() != ModVillagers.TUTOR,
			"a book by a Training Post made a Move Tutor without Cobblemon (now "
				+ BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()) + ")");
		helper.succeed();
	}

	/**
	 * teacher.md: "every child within 5 blocks of the lectern has lessons while the teacher stands by it". A child
	 * standing 5 blocks from the lectern, on the same floor, is within 5 blocks.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaTeacherFive"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaTeacherFive")
	public void aChildFiveBlocksFromTheLecternHasLessons(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(3000);
		helper.setBlock(STATION, Blocks.LECTERN);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager teacher = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, teacher, new ItemStack(Items.BOOK), true);
		helper.assertTrue(teacher.getVillagerData().getProfession() == ModVillagers.TEACHER, "setup: no teacher");
		Villager child = helper.spawn(EntityType.VILLAGER, STATION.west(5));
		child.setAge(-24000);
		child.setNoAi(true);
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(Schools.lessons(child) > 0, "the child 5 blocks from the lectern had no lessons");
			helper.succeed();
		});
	}
}
