package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.jeweller.JewellerWork;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1024-0353) bug tests for the luxury jobs, each committed with its fix (B367).
 */
public class QaLuxuryJobsBugTests implements FabricGameTest {
	private static final String AREA = QaLuxuryJobsGameTests.AREA;

	/**
	 * A luxury maker fetching for one good says "fetching what 1 Amethyst Ring take" above their head
	 * (message.aliveworkplace.luxury.state.fetching "fetching what %s %s take"). Expected: a count of one reads as one
	 * ("fetching what 1 Amethyst Ring takes", or "fetching the makings of 1 Amethyst Ring"), and many as many.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaLuxTextBug"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaLuxTextBug")
	public void aMakerFetchingForOneGoodReadsAsOne(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.STONECUTTER);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(ModItems.AMETHYST_RING, 7));
		c.setItem(1, new ItemStack(Items.AMETHYST_SHARD, 2));
		c.setItem(2, new ItemStack(Items.COPPER_INGOT, 2));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.JEWELLER, 1);
		QaLuxuryJobsGameTests.run(helper, v, new JewellerWork());
		Set<String> lines = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
			if (e != null) {
				lines.add(QaLuxuryJobsGameTests.english(e.line()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(c.countItem(ModItems.AMETHYST_RING) == 8, c.countItem(ModItems.AMETHYST_RING) + " rings, expected 8");
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("fetching")), "lines seen: " + lines);
			for (String l : lines) {
				helper.assertTrue(!l.matches(".*\\b1 .* take$"), "one ring read as many: \"" + l + "\"");
			}
		});
	}
}
