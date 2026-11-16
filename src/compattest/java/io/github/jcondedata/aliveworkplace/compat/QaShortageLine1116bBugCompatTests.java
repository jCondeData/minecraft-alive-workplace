package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1116-1238), with the compat pack, a bug past B1308. crafter.md: a stone a player mines "is cut from another
 * form of it only when one is in the chest (B1308)", and B1308's fix says "cut from polished andesite in the chest still
 * works". It doesn't: with 3 polished andesite in the builder's chest and a Mason at his stonecutter, no plan is made
 * (Crafting.chain is null), the Mason cuts nothing in 4000 ticks, and the builder reads "Nobody here makes: Andesite"
 * although CraftJobs.maker(andesite) is the Mason, who is right there (crafter.md: only what no job makes says "Nobody
 * here makes"). The same setup with 3 stone for 3 stone bricks is cut at once.
 */
public class QaShortageLine1116bBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A real plan: 3 andesite cut from 3 polished andesite (Create's stone-type stonecutting). */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaShortLine1116bPolishedPlan"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaShortLine1116bPolishedPlan")
	public void threePolishedAndesitePlanThreeAndesite(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			Crafting.Plan plan = Crafting.chain(helper.getLevel(), Crafting.Kind.STONECUTTING, Items.ANDESITE, 3, Map.of(Items.POLISHED_ANDESITE, 3L));
			helper.assertTrue(plan != null, "no plan for 3 andesite from 3 polished andesite" + (Crafting.planningSpent(helper.getLevel())
				? " (the tick's planning ran out)" : ""));
			helper.succeed();
		});
	}

	/**
	 * Through the player's entry point: a builder short of 3 andesite with 3 polished andesite in his chest and a Mason at
	 * his stonecutter. The Mason cuts the andesite, and the builder's line never names andesite as its own making.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaShortLine1116bPolishedMason"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaShortLine1116bPolishedMason")
	public void theMasonCutsAndesiteFromPolishedAndesiteInTheChest(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.ANDESITE, 3, 1,
			"qa1116bpolished"), Blocks.CHEST.defaultBlockState(), new ItemStack(Items.POLISHED_ANDESITE, 3));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(station), PoiTypes.MASON, VillagerProfession.MASON);
		List<String> self = new ArrayList<>();
		helper.onEachTick(() -> {
			if (helper.getLevel().getGameTime() % 20 == 0) {
				String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
				if (text != null && text.contains("Andesite to make")) {
					self.add(text);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(self.isEmpty(), "the builder's line named andesite as its own making: " + self);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0);
			helper.assertTrue(made >= 3, "the Mason cut " + made + " of 3 andesite from 3 polished andesite; line '"
				+ QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "', site " + QaCreateRivals1115cCompatTests.site(helper, a));
		});
	}

}
