package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.school.Schools;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/** Teachers give the village's children lessons; a villager who went to school starts their first job a level up. */
public class SchoolGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos DESK = new BlockPos(11, 2, 11);

	/** Two children across the area are called over and taught until they've been to school. */
	//$ gametest_ticks_batch AREA '1600' '"teacherTeachesTheChildren"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "teacherTeachesTheChildren")
	public void teacherTeachesTheChildren(GameTestHelper helper) {
		Leftovers.clear(helper);
		int needed = Schools.LESSONS_NEEDED;
		Schools.LESSONS_NEEDED = 200;
		Leftovers.after(helper, () -> Schools.LESSONS_NEEDED = needed);
		helper.setDayTime(3000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(DESK, ModBlocks.TEACHERS_DESK);
		Villager teacher = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(level, teacher, helper.absolutePos(DESK), ModVillagers.TEACHERS_DESK_POI, ModVillagers.TEACHER);
		Villager[] children = {helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3)), helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 19))};
		for (Villager child : children) {
			child.setAge(-24000);
		}
		helper.succeedWhen(() -> {
			for (Villager child : children) {
				helper.assertTrue(Schools.isSchooled(child), "a child hasn't been to school: " + Schools.lessons(child) + " ticks of lessons");
			}
			helper.assertTrue(ModAttachments.PUPILS_TAUGHT.getOrElse(teacher, 0) == 2, "pupils: "
				+ ModAttachments.PUPILS_TAUGHT.getOrElse(teacher, 0));
		});
	}

	/** A grown-up who went to school takes their first job as an Apprentice with trades for both levels; others as Novices. */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void aSchooledVillagerStartsAsApprentice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(15, 2, 5), ModBlocks.BUILDERS_BENCH);
		Villager schooled = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 6));
		ModAttachments.SCHOOLED.set(schooled, true);
		helper.runAfterDelay(2, () -> {
			Jobs.employ(level, schooled, helper.absolutePos(new BlockPos(5, 2, 5)), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
			Jobs.employ(level, other, helper.absolutePos(new BlockPos(15, 2, 5)), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
			helper.assertTrue(schooled.getVillagerData().getLevel() == 2, "the schooled one is level " + schooled.getVillagerData().getLevel());
			helper.assertTrue(schooled.getVillagerXp() >= 10, "xp " + schooled.getVillagerXp());
			helper.assertTrue(schooled.getOffers().size() >= 3, "offers: " + schooled.getOffers().size());
			helper.assertTrue(other.getVillagerData().getLevel() == 1, "the other is level " + other.getVillagerData().getLevel());
			helper.succeed();
		});
	}
}
