package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA findings for B548/B549 (the village is the circle {@link VillageHalls#contains} draws: RADIUS across, HEIGHT up or
 * down), from qa-lane-c-1027-0053.
 */
public class QaVillageCircleClassesBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/**
	 * B561: the hall's Classes page counts the village's households: a Peasant by the hall is one; a Peasant in the
	 * square's corner (15/15 out, 21 blocks away, outside the 16-block village) isn't one of them.
	 */
	//$ gametest_batch AREA '"qaCircleClassesPage"'
	@GameTest(template = AREA, batch = "qaCircleClassesPage")
	public void theClassesPageLeavesOutTheSquaresCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK;
			ClassNeeds.luxuryEvery = io.github.jcondedata.aliveworkplace.people.Luxuries.HOOK;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
			ClassPerks.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL))).setRank(VillageRanks.Rank.HAMLET);
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(AliveWorkplace.id("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1}"));
		files.put(AliveWorkplace.id("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5}"));
		SocialClasses.load(files);

		BlockPos h = helper.absolutePos(HALL);
		int r = VillageHalls.RADIUS;
		BlockPos corner = HALL.offset(r - 1, 0, r - 1);
		helper.assertTrue(VillageHalls.area(h).contains(helper.absolutePos(corner).getCenter())
			&& !VillageHalls.contains(h, helper.absolutePos(corner)), "staging: the corner isn't in the square and out of the circle");
		Villager uma = person(helper, HALL.offset(3, 0, 3), "Uma");
		Villager far = person(helper, corner, "Far");
		SocialClasses.SocialClass peasant = SocialClasses.get(AliveWorkplace.id("peasant"));
		SocialClasses.seed(uma, peasant);
		SocialClasses.seed(far, peasant);
		List<ClassesPage.Row> rows = ClassesPage.survey(level, h);
		int peasants = rows.get(0).households();
		int counted = ClassesPage.counts(level, h).getOrDefault(peasant, 0);
		uma.discard();
		far.discard();
		helper.assertTrue(rows.get(0).social() == peasant && peasants == 1 && counted == 1,
			"the Classes page counts " + peasants + " Peasant households (tab " + counted + "), not 1 (the villager outside the village's circle is counted)");
		helper.succeed();
	}

	private static Villager person(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}
}
