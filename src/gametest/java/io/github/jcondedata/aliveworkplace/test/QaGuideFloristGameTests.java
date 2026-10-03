package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug (qa-1003-1533): the Guide Book's Beekeepers page and ROADMAP 21.1a say "A flower at a composter makes a Florist",
 * but only small flowers do. A sunflower, lilac, rose bush or peony silently does nothing (the farmer stays a farmer,
 * no message).
 */
public class QaGuideFloristGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * Guide, Beekeepers page (and ROADMAP 21.1a's plan): "A flower at a composter makes a Florist." Every flower, the
	 * tall ones too (vanilla's #minecraft:flowers): a player holding a sunflower or a lilac follows the page.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaGuideAnyFlowerAtAComposterMakesAFlorist(GameTestHelper helper) {
		List<String> wrong = QaGuideClaimsGameTests.follow(helper, Blocks.COMPOSTER, new ItemStack(Items.WHEAT), VillagerProfession.FARMER, ModVillagers.FLORIST,
			List.of(Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.SUNFLOWER, Items.LILAC, Items.ROSE_BUSH, Items.PEONY));
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] guide: small and tall flowers each make a florist");
		helper.succeed();
	}
}
