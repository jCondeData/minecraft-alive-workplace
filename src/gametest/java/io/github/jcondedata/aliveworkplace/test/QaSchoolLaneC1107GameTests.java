package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.school.Schools;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA (lane-c-1107-0353), school from its spec (Schools' contract, the school feature): a child needs
 * {@link Schools#LESSONS_NEEDED} ticks of lessons, not one fewer; schooling counts once. A villager who went to school
 * takes their FIRST job a level up (Apprentice), a hired traveller at their own level (never past Master), and the
 * higher of the two when both; a Nitwit gets no head start, and a second job none either.
 */
public class QaSchoolLaneC1107GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** One tick short of the lessons needed is not school; the last tick is; more lessons after it change nothing. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaOneTickShortOfTheLessonsIsNotSchool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		child.setAge(-24000);
		helper.assertFalse(Schools.teach(level, child, Schools.LESSONS_NEEDED - 1), "schooled one tick short");
		helper.assertFalse(Schools.isSchooled(child), "schooled one tick short of " + Schools.LESSONS_NEEDED + ": " + Schools.lessons(child));
		helper.assertTrue(Schools.lessons(child) == Schools.LESSONS_NEEDED - 1, "lessons kept: " + Schools.lessons(child));
		helper.assertTrue(Schools.teach(level, child, 1), "the last tick of lessons should finish school");
		helper.assertTrue(Schools.isSchooled(child), "not schooled after " + Schools.lessons(child) + " ticks");
		helper.assertFalse(Schools.teach(level, child, 500), "a schooled child finished school again");
		helper.succeed();
	}

	/** A Nitwit who went to school stays a Novice; a schooled builder's second job gets no second head start. */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void qaANitwitAndASecondJobGetNoHeadStart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(5, 2, 5);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager nitwit = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		ModAttachments.SCHOOLED.set(nitwit, true);
		ModAttachments.SCHOOLED.set(builder, true);
		helper.runAfterDelay(2, () -> {
			nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
			helper.assertTrue(nitwit.getVillagerData().getLevel() == 1, "a schooled Nitwit is level " + nitwit.getVillagerData().getLevel());
			Jobs.employ(level, builder, helper.absolutePos(bench), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
			helper.assertTrue(builder.getVillagerData().getLevel() == 2, "setup: the schooled builder is level " + builder.getVillagerData().getLevel());
			// out of work and back to level 1 with no experience, then a job again: no second head start
			builder.setVillagerXp(0);
			builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
			builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
			helper.assertTrue(builder.getVillagerData().getLevel() == 1, "a second first-job head start: level " + builder.getVillagerData().getLevel());
			helper.succeed();
		});
	}

	/** A hired traveller of level 3 starts at 3 even if schooled; one recorded past Master starts at Master (5). */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void qaATravellersOwnLevelAndNeverPastMaster(GameTestHelper helper) {
		Villager journeyman = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Villager legend = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Villager pupil = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 12));
		ModAttachments.SCHOOLED.set(journeyman, true);
		ModAttachments.HEAD_START.set(journeyman, 3);
		ModAttachments.HEAD_START.set(legend, 9);
		ModAttachments.SCHOOLED.set(pupil, true);
		ModAttachments.HEAD_START.set(pupil, 1);
		helper.runAfterDelay(2, () -> {
			for (Villager v : new Villager[] {journeyman, legend, pupil}) {
				v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
			}
			helper.assertTrue(journeyman.getVillagerData().getLevel() == 3, "a schooled level-3 traveller starts at " + journeyman.getVillagerData().getLevel());
			helper.assertTrue(legend.getVillagerData().getLevel() == 5, "a head start of 9 starts at " + legend.getVillagerData().getLevel() + ", not Master");
			helper.assertTrue(pupil.getVillagerData().getLevel() == 2, "a schooled villager with a head start of 1 starts at " + pupil.getVillagerData().getLevel());
			helper.assertTrue(journeyman.getOffers().size() >= 5, "a level-3 start should come with the trades on the way: " + journeyman.getOffers().size());
			// "the Novice and Apprentice trades": a Librarian's Novice trades are paper for an emerald, a bookshelf, an
			// enchanted book (two of the three, so paper or a bookshelf is always one); Apprentice ones are none of those
			boolean novice = pupil.getOffers().stream().anyMatch(o -> o.getCostA().is(net.minecraft.world.item.Items.PAPER)
				|| o.getResult().is(net.minecraft.world.item.Items.BOOKSHELF));
			helper.assertTrue(novice, "a schooled Apprentice Librarian has no Novice trade: " + pupil.getOffers().stream()
				.map(o -> o.getCostA().getItem() + "->" + o.getResult().getItem()).toList());
			helper.succeed();
		});
	}
}
