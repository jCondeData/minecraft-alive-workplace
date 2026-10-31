package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.BLOCK;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.PIP;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.freeTab;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.jobDropdown;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.jobless;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.lineOf;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.openPeople;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.pick;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.site;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.state;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.village;
import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** QA bug tests for the hall's shared stations (B635 follow-ups): each fails on the current mod. */
public class QaHallSharedStationsBugTests {
	/** Job ▾'s line for {@code job} with only a free {@code block} in the village, then its one-click job. */
	private static void jobAt(GameTestHelper helper, Block block, VillagerProfession job, String want, String told) {
		village(helper, 8);
		helper.setBlock(BLOCK, block);
		Villager pip = jobless(helper, PIP, "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			BlockPos at = helper.absolutePos(BLOCK);
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack line = lineOf(helper, jobDropdown(helper, player, menu, pip), job);
			helper.assertTrue(line.getHoverName().getString().equals(want) && state(line).equals("free"),
				"with a free " + block.getName().getString() + " standing 5 blocks from the hall, Job ▾ says \"" + line.getHoverName().getString()
					+ "\" (" + state(line) + "), wanted \"" + want + "\" (free)");
			pick(helper, player, menu, job);
			helper.assertTrue(pip.getVillagerData().getProfession() == job && site(pip).equals(Optional.of(at)),
				"Pip isn't the " + job + " at the block: " + pip.getVillagerData().getProfession() + " " + site(pip));
			helper.assertTrue(seen.contains(told), "not told \"" + told + "\": " + seen);
			helper.assertTrue(freeTab(helper, player).contains("0 free job blocks"), "the tab after: " + freeTab(helper, player));
			helper.succeed();
		});
	}

	/**
	 * B635's "whether vanilla blocks shared with our jobs are counted": a beehive is the Beekeeper's (21.1a), a block no
	 * villager takes by themselves, like the crafting table. Job ▾ must not say to place a Beehive while one stands free.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSharedBeehive"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedBeehive")
	public void aFreeBeehiveIsOfferedToTheBeekeeper(GameTestHelper helper) {
		jobAt(helper, Blocks.BEEHIVE, ModVillagers.BEEKEEPER, "Beekeeper · 1 free Beehive", "Pip is now a Beekeeper");
	}

	/**
	 * The Carpenter's own block: with only a free Carpenter's Bench, Job ▾ names the free block it counts (40.3: "Mason ·
	 * 1 free Stonecutter"), not a Crafting Table that isn't there.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSharedBench"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedBench")
	public void aFreeCarpentersBenchIsNamedAsSuch(GameTestHelper helper) {
		jobAt(helper, ModBlocks.CARPENTERS_BENCH, ModVillagers.CARPENTER, "Carpenter · 1 free Carpenter's Bench", "Pip is now a Carpenter");
	}
}
