package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * B810: the hall's Advice button counts its suggestions in the singular for one ("1 suggestion, first: Hire a builder")
 * and in the plural for two or more; the button itself reads the same line as its village's own tips.
 */
public class HallCountWordsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** One tip: "1 suggestion, first: ...", two: "2 suggestions, first: ...", none: the "nothing pressing" line. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneSuggestionReadsInTheSingular(GameTestHelper helper) {
		VillageAdvice.Tip builder = new VillageAdvice.Tip("builder", Items.LECTERN);
		VillageAdvice.Tip storehouse = new VillageAdvice.Tip("storehouse", Items.CHEST);
		String one = VillageHallScreen.adviceCount(List.of(builder)).getString();
		helper.assertTrue(one.equals("1 suggestion, first: Hire a builder"), "one tip: '" + one + "'");
		String two = VillageHallScreen.adviceCount(List.of(builder, storehouse)).getString();
		helper.assertTrue(two.equals("2 suggestions, first: Hire a builder"), "two tips: '" + two + "'");
		String none = VillageHallScreen.adviceCount(List.of()).getString();
		helper.assertTrue(none.equals("Nothing pressing: the village has what it needs"), "no tips: '" + none + "'");
		helper.succeed();
	}

	/** A real hall's Advice button: its first lore line is the count of that village's own tips, in the right form. */
	//$ gametest_ticks_batch AREA '40' '"hallAdviceCountWords"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "hallAdviceCountWords")
	public void theAdviceButtonCountsTheVillagesTips(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos at = new BlockPos(3, 2, 3);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		ItemLore lore = menu.icon(VillageHallScreen.ADVICE).get(DataComponents.LORE);
		helper.assertTrue(lore != null && !lore.lines().isEmpty(), "the Advice button has no lore");
		List<VillageAdvice.Tip> tips = VillageAdvice.tips(helper.getLevel(), hall);
		String first = lore.lines().get(0).getString();
		helper.assertTrue(first.equals(VillageHallScreen.adviceCount(tips).getString()), "the Advice button reads '" + first + "' for " + tips.size() + " tips");
		helper.assertTrue(tips.size() < 2 || first.startsWith(tips.size() + " suggestions, first: "), "the plural for " + tips.size() + ": '" + first + "'");
		helper.succeed();
	}
}
