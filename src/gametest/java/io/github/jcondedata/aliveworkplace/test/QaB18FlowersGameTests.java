package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-1003-1833, B18): B18's expected result is "every flower (#minecraft:flowers) makes a Florist". These check
 * the flowers players know that QaGuideFloristGameTests doesn't try, and the composter's own tooltip, which tells the
 * player which items pick each job.
 */
public class QaB18FlowersGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * B18: "every flower (#minecraft:flowers) makes a Florist". The newer flowers (torchflower, pitcher plant), the
	 * wither rose, and the flowers that are only in #minecraft:flowers (pink petals, spore blossom).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaB18EveryFlowerInTheFlowersTagMakesAFlorist(GameTestHelper helper) {
		List<String> wrong = QaGuideClaimsGameTests.follow(helper, Blocks.COMPOSTER, new ItemStack(Items.WHEAT), VillagerProfession.FARMER, ModVillagers.FLORIST,
			List.of(Items.TORCHFLOWER, Items.PITCHER_PLANT, Items.WITHER_ROSE, Items.PINK_PETALS, Items.SPORE_BLOSSOM));
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The composter's tooltip ("Sneak-right-click a villager by it holding: Florist: …") must not tell players a tall
	 * flower won't do, now that one does (B18).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaB18TheComposterTooltipDoesntSayOnlySmallFlowers(GameTestHelper helper) {
		Stations.Station composter = Stations.at(Blocks.COMPOSTER).orElseThrow();
		Stations.Job florist = composter.jobs().stream().filter(j -> j.profession().get() == ModVillagers.FLORIST).findFirst().orElseThrow();
		String text = Language.getInstance().getOrDefault(Stations.itemKey(florist));
		helper.assertTrue(!text.toLowerCase(java.util.Locale.ROOT).contains("small"),
			"the composter's tooltip says the Florist needs '" + text + "', but a sunflower or lilac works too");
		helper.succeed();
	}
}
