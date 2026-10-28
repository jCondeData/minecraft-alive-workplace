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

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;

/**
 * QA (lane-c-1028-0953): B657's fix from the other side, written from its text ("a beehive nobody works at is free",
 * 40.3's "those without are grayed"). A beehive keeps no worker count, so the hall must tell a free one from a worked
 * one by who works there: a hive a Beekeeper already works at is never offered, one hive is never given to two
 * villagers, a hive comes free again when its Beekeeper is gone, and a bee nest (also a Beekeeper's block) is offered by
 * its own name. Every check goes through the hall's own clicks.
 */
public class QaHallBeehives1028GameTests {
	private static final BlockPos BEA = new BlockPos(18, 2, 19);
	private static final BlockPos QUINN = new BlockPos(10, 2, 22);

	/** A Beekeeper already working at the hive at {@link #BLOCK}. */
	private static Villager beekeeper(GameTestHelper helper) {
		Villager v = helper.spawn(EntityType.VILLAGER, BEA);
		v.setNoAi(true);
		v.setCustomName(Component.literal("Bea"));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(BLOCK), PoiTypes.BEEHIVE, ModVillagers.BEEKEEPER);
		return v;
	}

	private static void assertNotOffered(GameTestHelper helper, ItemStack line, String why) {
		helper.assertTrue(state(line).equals("no_block") && !line.getHoverName().getString().contains("free"),
			why + ": Job ▾ says \"" + line.getHoverName().getString() + "\" (" + state(line) + "), wanted the grayed no-block line");
	}

	/** A hive a Beekeeper works at is not free: grayed for a jobless villager, not counted, and a pick changes nothing. */
	//$ gametest_ticks_batch AREA '200' '"qaHiveWorked"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiveWorked")
	public void aHiveABeekeeperWorksAtIsNotOffered(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(BLOCK, Blocks.BEEHIVE);
		Villager bea = beekeeper(helper);
		Villager pip = jobless(helper, PIP, "Pip");
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			BlockPos at = helper.absolutePos(BLOCK);
			helper.assertTrue(site(bea).equals(Optional.of(at)), "Bea doesn't work at the hive: " + site(bea));
			helper.assertTrue(freeTab(helper, player).contains("0 free job blocks"), "the tab counts Bea's hive: " + freeTab(helper, player));
			ChoiceMenu menu = openPeople(helper, player);
			assertNotOffered(helper, lineOf(helper, jobDropdown(helper, player, menu, pip), ModVillagers.BEEKEEPER), "with Bea's hive the only one");
			pick(helper, player, menu, ModVillagers.BEEKEEPER);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE && site(pip).isEmpty(),
				"Pip took Bea's hive: " + pip.getVillagerData().getProfession() + " " + site(pip));
			helper.assertTrue(bea.getVillagerData().getProfession() == ModVillagers.BEEKEEPER && site(bea).equals(Optional.of(at)),
				"Bea lost her hive: " + bea.getVillagerData().getProfession() + " " + site(bea));
			helper.succeed();
		});
	}

	/** One free hive, two jobless villagers: the first to be given it has it, the second's list grays it and a pick fails. */
	//$ gametest_ticks_batch AREA '200' '"qaHiveTwice"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiveTwice")
	public void oneHiveIsNotGivenTwice(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(BLOCK, Blocks.BEEHIVE);
		Villager pip = jobless(helper, PIP, "Pip");
		Villager quinn = jobless(helper, QUINN, "Quinn");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			BlockPos at = helper.absolutePos(BLOCK);
			ChoiceMenu menu = openPeople(helper, player);
			lineOf(helper, jobDropdown(helper, player, menu, pip), ModVillagers.BEEKEEPER);
			pick(helper, player, menu, ModVillagers.BEEKEEPER);
			helper.assertTrue(pip.getVillagerData().getProfession() == ModVillagers.BEEKEEPER && site(pip).equals(Optional.of(at)),
				"Pip wasn't given the hive: " + pip.getVillagerData().getProfession() + " " + site(pip));
			ChoiceMenu again = openPeople(helper, player);
			assertNotOffered(helper, lineOf(helper, jobDropdown(helper, player, again, quinn), ModVillagers.BEEKEEPER), "with Pip at the only hive");
			pick(helper, player, again, ModVillagers.BEEKEEPER);
			helper.assertTrue(quinn.getVillagerData().getProfession() == VillagerProfession.NONE && site(quinn).isEmpty(),
				"Quinn was given Pip's hive too: " + quinn.getVillagerData().getProfession() + " " + site(quinn));
			helper.assertTrue(site(pip).equals(Optional.of(at)), "Pip lost the hive: " + site(pip));
			helper.assertTrue(freeTab(helper, player).contains("0 free job blocks"), "the tab after: " + freeTab(helper, player));
			helper.succeed();
		});
	}

	/** The hive's Beekeeper gone (dead), the hive is free again: offered by name and given in one click. */
	//$ gametest_ticks_batch AREA '200' '"qaHiveFreedByDeath"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiveFreedByDeath")
	public void aHiveIsFreeAgainWhenItsBeekeeperIsGone(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(BLOCK, Blocks.BEEHIVE);
		Villager bea = beekeeper(helper);
		Villager pip = jobless(helper, PIP, "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> bea.kill());
		helper.runAfterDelay(30, () -> { // the body is gone 20 ticks after the death
			BlockPos at = helper.absolutePos(BLOCK);
			helper.assertTrue(bea.isRemoved(), "Bea's body is still there");
			helper.assertTrue(freeTab(helper, player).contains("1 free job block"), "the tab with Bea gone: " + freeTab(helper, player));
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack line = lineOf(helper, jobDropdown(helper, player, menu, pip), ModVillagers.BEEKEEPER);
			helper.assertTrue(line.getHoverName().getString().equals("Beekeeper · 1 free Beehive") && state(line).equals("free"),
				"with Bea gone, Job ▾ says \"" + line.getHoverName().getString() + "\" (" + state(line) + ")");
			pick(helper, player, menu, ModVillagers.BEEKEEPER);
			helper.assertTrue(pip.getVillagerData().getProfession() == ModVillagers.BEEKEEPER && site(pip).equals(Optional.of(at)),
				"Pip isn't the Beekeeper at the hive: " + pip.getVillagerData().getProfession() + " " + site(pip));
			helper.assertTrue(seen.contains("Pip is now a Beekeeper"), "not told: " + seen);
			helper.succeed();
		});
	}

	/** A bee nest is a Beekeeper's block too (21.1a): a free one is offered by its own name and given in one click. */
	//$ gametest_ticks_batch AREA '200' '"qaHiveNest"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiveNest")
	public void aFreeBeeNestIsOfferedByItsName(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(BLOCK, Blocks.BEE_NEST);
		Villager pip = jobless(helper, PIP, "Pip");
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			BlockPos at = helper.absolutePos(BLOCK);
			helper.assertTrue(freeTab(helper, player).contains("1 free job block"), "the tab: " + freeTab(helper, player));
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack line = lineOf(helper, jobDropdown(helper, player, menu, pip), ModVillagers.BEEKEEPER);
			helper.assertTrue(line.getHoverName().getString().equals("Beekeeper · 1 free Bee Nest") && state(line).equals("free"),
				"with a free bee nest, Job ▾ says \"" + line.getHoverName().getString() + "\" (" + state(line) + ")");
			pick(helper, player, menu, ModVillagers.BEEKEEPER);
			helper.assertTrue(pip.getVillagerData().getProfession() == ModVillagers.BEEKEEPER && site(pip).equals(Optional.of(at)),
				"Pip isn't the Beekeeper at the nest: " + pip.getVillagerData().getProfession() + " " + site(pip));
			helper.succeed();
		});
	}
}
