package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA tests for 34.7 (lane-c-1024-1553) that found a bug: B398, a class with one payer read "1 pay" (fixed).
 */
public class QaClassPerksBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);

	private static Villager classed(GameTestHelper helper, int x, String cls, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 3));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		return v;
	}

	/** The name tag's split by class with one payer in a class reads "Burgher: 1 pays ...", not "1 pay". */
	//$ gametest_batch AREA '"qaBugTaxLineOne"'
	@GameTest(template = AREA, batch = "qaBugTaxLineOne")
	public void aClassWithOnePayerReadsAsOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL))).setRank(VillageRanks.Rank.HAMLET);
		List<Villager> workers = List.of(classed(helper, 3, "peasant", VillagerProfession.FARMER), classed(helper, 6, "peasant", VillagerProfession.FISHERMAN),
			classed(helper, 9, "burgher", ModVillagers.SCHOLAR));
		List<String> text = Treasury.taxLines(level, helper.absolutePos(HALL), workers, List.of()).stream().map(Component::getString).toList();
		helper.assertTrue(text.size() == 3, "a heading and two classes: " + text);
		helper.assertTrue(text.get(1).startsWith("Peasant: 2 pay "), "two payers read as many: " + text.get(1));
		helper.assertTrue(!text.get(2).contains(" 1 pay "), "one payer reads as one, found: '" + text.get(2) + "'");
		helper.succeed();
	}
}
