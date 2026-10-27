package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.research.TreeWork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA for 29.11, research trees as data, written from the spec with the test tree: the lectern within 8 blocks of home
 * (8 yes, 9 no); what a topic needs, its top level and a busy Legend refuse a pick, on the screen too; a second level's
 * cost of two items is taken only whole; an exclusive group refuses the other way round and after a reload; an unlock
 * past its number, and its sentence; the tab goes when the Legend leaves; a strike mid-topic keeps the progress.
 */
public class QaResearchTreesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(17, 2, 17);
	private static final BlockPos BED = new BlockPos(6, 2, 6);
	private static final BlockPos LECTERN = new BlockPos(8, 2, 4);
	private static final BlockPos CHEST = new BlockPos(9, 2, 4);

	private record Setup(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Container chest, Villager sage, ServerPlayer player) {
	}

	private static Setup setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.forget();
			CivicEffects.forget();
			LegendPowers.forget();
			ResearchTreesGameTests.testCounter = 0;
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BED, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager sage = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Legends.make(level, sage, Legends.get(ResearchTreesGameTests.SAGE).orElseThrow(() -> new IllegalStateException("test Sage not loaded")), "test");
		sage.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(BED)));
		BlockPos hall = helper.absolutePos(HALL);
		return new Setup(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall), helper.getBlockEntity(CHEST), sage,
			helper.makeMockServerPlayerInLevel());
	}

	private static ResearchTree tree(GameTestHelper helper) {
		ResearchTree tree = ResearchTrees.get(ResearchTreesGameTests.TREE).orElse(null);
		helper.assertTrue(tree != null, "test tree not loaded");
		return tree;
	}

	private static ResearchTree.Topic topic(GameTestHelper helper, String id) {
		return tree(helper).topic(id).orElseThrow(() -> new IllegalStateException("no topic " + id));
	}

	private static Research.State levels(Map<String, Integer> topics) {
		Map<String, Integer> out = new java.util.HashMap<>();
		topics.forEach((k, v) -> out.put(ResearchTreesGameTests.TREE + "/" + k, v));
		return new Research.State(Map.copyOf(out), Optional.empty(), 0, false);
	}

	private static String why(Setup s, Research.State state, ResearchTree tree, ResearchTree.Topic topic) {
		return ResearchTrees.whyNot(s.level(), s.hall(), state, tree, topic).map(Component::getString).orElse("");
	}

	/** The lectern counts within 8 blocks of the Legend's home: at 8 they work there, at 9 they have no job. */
	//$ gametest_ticks_batch AREA '40' '"qaTreeLecternReach"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaTreeLecternReach")
	public void lecternEightBlocksFromHomeNotNine(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			ResearchTree tree = tree(helper);
			s.entity().setResearch(ResearchTrees.choose(s.entity().research(), tree, topic(helper, "calm_minds")));
			helper.setBlock(LECTERN, Blocks.AIR);
			BlockPos at8 = BED.offset(8, 0, 0);
			helper.setBlock(at8, Blocks.LECTERN);
			TreeWork.Job job = TreeWork.job(s.level(), s.sage());
			helper.assertTrue(job != null && job.lectern().equals(helper.absolutePos(at8)), "no job at a lectern 8 blocks from home: " + job);
			helper.setBlock(at8, Blocks.AIR);
			helper.setBlock(BED.offset(9, 0, 0), Blocks.LECTERN);
			helper.assertTrue(TreeWork.job(s.level(), s.sage()) == null, "a job at a lectern 9 blocks from home");
			helper.succeed();
		});
	}

	/**
	 * Refusals and their sentences: Herb Lore needs Calm Minds first; a topic at its top is fully researched; while a
	 * paid-for topic is under way nothing else is chosen, on the screen too. A second level costs 8 paper and a book:
	 * with only the paper nothing is taken; with both, both go.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaTreeRefusals"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaTreeRefusals")
	public void needsTopLevelBusyAndASecondLevelsCost(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(3, () -> {
			ResearchTree tree = tree(helper);
			ResearchTree.Topic calm = topic(helper, "calm_minds");
			ResearchTree.Topic herb = topic(helper, "herb_lore");
			ResearchScreen.View view = new ResearchScreen.View();
			ChoiceMenu menu = ResearchScreen.forTest(s.player(), s.hall(), view);
			menu.press(ResearchScreen.TAB_SLOTS[1], s.player());
			// needs
			helper.assertTrue(why(s, s.entity().research(), tree, herb).equals("Herb Lore can't be researched yet"), "needs line: " + why(s, s.entity().research(), tree, herb));
			menu.press(ResearchScreen.TOPIC_SLOTS[1], s.player());
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).isEmpty(), "Herb Lore chosen before Calm Minds: " + s.entity().research());
			// the second level of Calm Minds: 8 paper and a book
			s.entity().setResearch(ResearchTrees.choose(levels(Map.of("calm_minds", 1)), tree, calm));
			helper.assertTrue(why(s, s.entity().research(), tree, herb).isEmpty(), "Herb Lore still refused with Calm Minds 1");
			TreeWork.Job job = TreeWork.job(s.level(), s.sage());
			helper.assertTrue(job != null, "no job");
			s.chest().setItem(0, new ItemStack(Items.PAPER, 8));
			TreeWork.step(s.level(), s.sage(), job, false);
			helper.assertTrue(!ResearchTrees.current(s.entity().research(), tree).orElseThrow().paid() && s.chest().countItem(Items.PAPER) == 8,
				"paid level 2 without the book: paper " + s.chest().countItem(Items.PAPER));
			s.chest().setItem(1, new ItemStack(Items.BOOK, 1));
			TreeWork.step(s.level(), s.sage(), job, false);
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).orElseThrow().paid() && s.chest().countItem(Items.PAPER) == 0
				&& s.chest().countItem(Items.BOOK) == 0, "level 2's cost: paper " + s.chest().countItem(Items.PAPER) + ", book " + s.chest().countItem(Items.BOOK));
			// busy: a paid-for topic under way refuses another, on the screen too
			helper.assertTrue(why(s, s.entity().research(), tree, topic(helper, "warding")).equals("Busy with Calm Minds until it's done"),
				"busy line: " + why(s, s.entity().research(), tree, topic(helper, "warding")));
			menu.press(ResearchScreen.TOPIC_SLOTS[2], s.player());
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).map(c -> c.topic() == calm && c.paid()).orElse(false),
				"the paid-for topic was swapped: " + s.entity().research());
			// the top: fully researched, and not chosen again
			s.entity().setResearch(ResearchTrees.finish(s.entity().research(), tree));
			helper.assertTrue(ResearchTrees.level(s.entity().research(), tree, calm) == 2, "not level 2");
			helper.assertTrue(why(s, s.entity().research(), tree, calm).equals("Fully researched"), "top line: " + why(s, s.entity().research(), tree, calm));
			menu.press(ResearchScreen.TOPIC_SLOTS[0], s.player());
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).isEmpty(), "a third level of a two-level topic chosen");
			helper.succeed();
		});
	}

	/** The other way round: The Iron Pact researched refuses The Flame, and still does after the hall is saved and reloaded. */
	//$ gametest_ticks_batch AREA '40' '"qaTreeExclusive"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaTreeExclusive")
	public void exclusiveTheOtherWayAndAfterAReload(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			ResearchTree tree = tree(helper);
			ResearchTree.Topic flame = topic(helper, "flame");
			s.entity().setResearch(levels(Map.of("iron_pact", 1)));
			String line = why(s, s.entity().research(), tree, flame);
			helper.assertTrue(line.equals("The village took The Iron Pact for good: The Flame can't be researched"), "exclusive line: " + line);
			CompoundTag tag = s.entity().saveWithFullMetadata(s.level().registryAccess());
			VillageHallBlockEntity back = (VillageHallBlockEntity) BlockEntity.loadStatic(s.hall(), s.level().getBlockState(s.hall()), tag, s.level().registryAccess());
			helper.assertTrue(back != null && !why(s, back.research(), tree, flame).isEmpty(), "The Flame open again after a reload: " + (back == null ? null : back.research()));
			ChoiceMenu menu = ResearchScreen.forTest(s.player(), s.hall(), new ResearchScreen.View());
			menu.press(ResearchScreen.TAB_SLOTS[1], s.player());
			menu.press(ResearchScreen.TOPIC_SLOTS[6], s.player());
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).isEmpty(), "The Flame chosen after The Iron Pact");
			helper.succeed();
		});
	}

	/** An unlock opens at its number and past it; below it, the sentence names the number and the counter. */
	//$ gametest_ticks_batch AREA '40' '"qaTreeUnlock"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaTreeUnlock")
	public void unlockOpensPastItsNumberAndSaysWhy(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			ResearchTree tree = tree(helper);
			ResearchTree.Topic census = topic(helper, "census");
			ResearchTreesGameTests.testCounter = 0;
			String line = why(s, s.entity().research(), tree, census);
			helper.assertTrue(line.equals("Census opens when the village has 3 test things"), "unlock line: " + line);
			helper.assertTrue(ResearchTrees.waitsForCounter(s.level(), s.hall(), census), "doesn't wait at 0");
			ResearchTreesGameTests.testCounter = 4;
			helper.assertTrue(why(s, s.entity().research(), tree, census).isEmpty() && !ResearchTrees.waitsForCounter(s.level(), s.hall(), census), "closed at 4 of 3");
			helper.succeed();
		});
	}

	/** The tree's tab is there while its Legend lives in the village and goes when they leave it. */
	//$ gametest_ticks_batch AREA '40' '"qaTreeTabGoes"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaTreeTabGoes")
	public void theTabGoesWhenTheLegendLeaves(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = ResearchScreen.forTest(s.player(), s.hall(), new ResearchScreen.View());
			helper.assertTrue(menu.icon(ResearchScreen.TAB_SLOTS[1]).is(Items.AMETHYST_SHARD), "no tab with the Sage in the village");
			helper.assertTrue(ResearchTrees.inVillage(s.level(), s.hall()).contains(tree(helper)), "not in the village's trees");
			Legends.clear(s.sage());
			s.sage().discard();
			helper.assertTrue(!ResearchTrees.inVillage(s.level(), s.hall()).contains(tree(helper)), "the tree stays after the Sage left");
			ChoiceMenu after = ResearchScreen.forTest(s.player(), s.hall(), new ResearchScreen.View());
			helper.assertTrue(!after.icon(ResearchScreen.TAB_SLOTS[1]).is(Items.AMETHYST_SHARD), "the tab stays after the Sage left");
			helper.succeed();
		});
	}

	/** A strike mid-topic stops the work but keeps the points and what was paid; after it, work goes on from there. */
	//$ gametest_ticks_batch AREA '40' '"qaTreeStrikeKeeps"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaTreeStrikeKeeps")
	public void aStrikeMidTopicKeepsTheProgress(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			ResearchTree tree = tree(helper);
			s.entity().setResearch(ResearchTrees.withProgress(ResearchTrees.choose(s.entity().research(), tree, topic(helper, "warding")), tree, 17));
			ModAttachments.LEGEND.set(s.sage(), ModAttachments.LEGEND.get(s.sage()).checkedOn(1, Map.of(), 1, -1));
			LegendPowers.forget();
			helper.assertTrue(TreeWork.job(s.level(), s.sage()) == null, "works on strike");
			ResearchTrees.Current now = ResearchTrees.current(s.entity().research(), tree).orElse(null);
			helper.assertTrue(now != null && now.paid() && now.progress() == 17, "progress lost on strike: " + now);
			ModAttachments.LEGEND.set(s.sage(), ModAttachments.LEGEND.get(s.sage()).checkedOn(1, Map.of(), -1, -1));
			LegendPowers.forget();
			TreeWork.Job job = TreeWork.job(s.level(), s.sage());
			helper.assertTrue(job != null, "no job after the strike");
			TreeWork.step(s.level(), s.sage(), job, true);
			int after = ResearchTrees.current(s.entity().research(), tree).map(ResearchTrees.Current::progress).orElse(-1);
			helper.assertTrue(after > 17 || ResearchTrees.level(s.entity().research(), tree, topic(helper, "warding")) == 1, "not carried on from 17: " + after);
			helper.succeed();
		});
	}
}
