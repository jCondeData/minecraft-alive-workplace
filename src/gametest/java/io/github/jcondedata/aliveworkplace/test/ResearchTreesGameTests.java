package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.research.TreeEffects;
import io.github.jcondedata.aliveworkplace.research.TreeWork;
import io.github.jcondedata.aliveworkplace.sift.SifterWork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
 * The Legends' research trees as data (ROADMAP 29.11), with the test tree {@code aliveworkplace_test:test_tree} worked by
 * the test Sage: it loads; the Sage pays a level from the chests by a lectern near home and researches it; levels are
 * kept in the hall and survive a reload; an exclusive group refuses a second pick; an unlock waits for its counter; each
 * new effect works; an old hall's research loads unchanged; a Legend on strike doesn't work; scholars help.
 */
public class ResearchTreesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	static final ResourceLocation TREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_tree");
	static final ResourceLocation SAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_sage");
	private static final BlockPos HALL = new BlockPos(17, 2, 17);
	private static final BlockPos BED = new BlockPos(6, 2, 6);
	private static final BlockPos LECTERN = new BlockPos(8, 2, 4);
	private static final BlockPos CHEST = new BlockPos(9, 2, 4);
	/** The test counter the Census topic waits for. */
	static int testCounter;

	static {
		ResearchTrees.counter("test_counter", Component.literal("test things"), (level, hall) -> testCounter);
	}

	private static ResearchTree tree(GameTestHelper helper) {
		ResearchTree tree = ResearchTrees.get(TREE).orElse(null);
		helper.assertTrue(tree != null, "test tree not loaded: " + ResearchTrees.all().stream().map(ResearchTree::id).toList());
		return tree;
	}

	private static ResearchTree.Topic topic(GameTestHelper helper, String id) {
		return tree(helper).topic(id).orElseThrow(() -> new IllegalStateException("no topic " + id));
	}

	/** The levels as {@code <tree>/<topic>} keys, for staging a hall. */
	private static Research.State levels(Map<String, Integer> topics) {
		Map<String, Integer> out = new HashMap<>();
		topics.forEach((k, v) -> out.put(TREE + "/" + k, v));
		return new Research.State(Map.copyOf(out), Optional.empty(), 0, false);
	}

	private record Setup(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Container chest, Villager sage, ServerPlayer player) {
	}

	/** A hall, the test Sage settled in it with a bed and a lectern near it, a chest by the lectern. */
	private static Setup setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.forget();
			CivicEffects.forget();
			LegendPowers.forget();
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BED, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager sage = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Legends.make(level, sage, Legends.get(SAGE).orElseThrow(() -> new IllegalStateException("test Sage not loaded")), "test");
		sage.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(BED)));
		BlockPos hall = helper.absolutePos(HALL);
		return new Setup(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall), helper.getBlockEntity(CHEST), sage,
			helper.makeMockServerPlayerInLevel());
	}

	/** The test tree's file loads with its Legend, icon, topics and effects; broken files fail, naming the problem. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void researchTreeLoads(GameTestHelper helper) {
		ResearchTree tree = tree(helper);
		helper.assertTrue(tree.legend().equals(SAGE) && tree.icon() == Items.AMETHYST_SHARD && tree.topics().size() == 8, "legend, icon or topics: " + tree);
		ResearchTree.Topic calm = topic(helper, "calm_minds");
		helper.assertTrue(calm.levels() == 2 && calm.cost(1).equals(Map.of(Items.PAPER, 4)) && calm.cost(2).equals(Map.of(Items.PAPER, 8, Items.BOOK, 1))
			&& calm.points(2) == 80, "levels, costs or points: " + calm);
		helper.assertTrue(topic(helper, "herb_lore").needs().equals(Map.of("calm_minds", 1)), "needs");
		helper.assertTrue(topic(helper, "census").unlock().equals(Optional.of(new ResearchTree.Unlock("test_counter", 3))), "unlock");
		helper.assertTrue(topic(helper, "flame").exclusive().equals(Optional.of("last_pick")) && tree.rivals(topic(helper, "flame")).equals(List.of(topic(helper, "iron_pact"))), "exclusive");
		helper.assertTrue(topic(helper, "herb_lore").effects().get(0) instanceof TreeEffects.Illness i && i.percent() == -20 && i.days() == -1, "effects");
		helper.assertTrue(ResearchTrees.of(SAGE).equals(List.of(tree)), "the Sage's trees");
		String good = "{\"id\": \"a\", \"name\": \"A\", \"cost\": [{\"minecraft:paper\": 1}], \"points\": 10}";
		for (String bad : List.of(
			"{\"legend\": \"x:y\", \"name\": \"T\", \"topics\": [{\"id\": \"a\", \"name\": \"A\", \"cost\": [{\"minecraft:paper\": 1}], \"points\": 10, \"needs\": {\"nope\": 1}}]}",
			"{\"legend\": \"x:y\", \"name\": \"T\", \"topics\": [" + good + ", " + good + "]}",
			"{\"legend\": \"x:y\", \"name\": \"T\", \"topics\": [{\"id\": \"a\", \"name\": \"A\", \"cost\": [{\"minecraft:paper\": 1}], \"points\": 10, \"effects\": [{\"type\": \"aliveworkplace:luck_of_the_irish\"}]}]}",
			"{\"legend\": \"x:y\", \"name\": \"T\", \"topics\": []}")) {
			boolean failed;
			try {
				ResearchTrees.read(ResourceLocation.fromNamespaceAndPath("t", "bad"), JsonParser.parseString(bad));
				failed = false;
			} catch (RuntimeException e) {
				failed = true;
			}
			helper.assertTrue(failed, "a broken tree loaded: " + bad);
		}
		helper.assertTrue(ResearchTrees.read(ResourceLocation.fromNamespaceAndPath("t", "needs_mod"),
			JsonParser.parseString("{\"legend\": \"x:y\", \"name\": \"T\", \"requires\": [\"no_such_mod\"], \"topics\": [" + good + "]}")) == null,
			"a tree for a missing mod loaded");
		helper.succeed();
	}

	/**
	 * The tree's tab shows once the Sage lives in the village: Calm Minds chosen there, the Sage takes 4 paper from the
	 * chest by the lectern near their home, works on it there, and the level is done.
	 */
	//$ gametest_ticks_batch AREA '900' '"legendResearchesTree"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "legendResearchesTree")
	public void legendResearchesTree(GameTestHelper helper) {
		Setup s = setup(helper);
		s.chest().setItem(0, new ItemStack(Items.PAPER, 6));
		helper.runAfterDelay(3, () -> {
			ResearchScreen.View view = new ResearchScreen.View();
			ChoiceMenu menu = ResearchScreen.forTest(s.player(), s.hall(), view);
			helper.assertTrue(menu.icon(ResearchScreen.TAB_SLOTS[1]).is(Items.AMETHYST_SHARD), "no tab for the tree: " + menu.icon(ResearchScreen.TAB_SLOTS[1]));
			menu.press(ResearchScreen.TAB_SLOTS[1], s.player());
			helper.assertTrue(TREE.equals(view.tree()), "the tab didn't open the tree");
			helper.assertTrue(menu.icon(ResearchScreen.TOPIC_SLOTS[0]).is(Items.CAMPFIRE), "Calm Minds isn't the first topic");
			menu.press(ResearchScreen.TOPIC_SLOTS[0], s.player());
			Optional<ResearchTrees.Current> now = ResearchTrees.current(s.entity().research(), tree(helper));
			helper.assertTrue(now.isPresent() && now.get().topic().id().equals("calm_minds") && !now.get().paid(), "not chosen: " + s.entity().research());
			helper.assertTrue(s.entity().research().currentTopic() == null, "the scholars' tree changed");
			helper.succeedWhen(() -> {
				helper.assertTrue(ResearchTrees.level(s.entity().research(), tree(helper), topic(helper, "calm_minds")) == 1, "Calm Minds: " + s.entity().research());
				helper.assertTrue(s.chest().countItem(Items.PAPER) == 2, "paper left: " + s.chest().countItem(Items.PAPER));
				helper.assertTrue(ResearchTrees.current(s.entity().research(), tree(helper)).isEmpty(), "still researching");
			});
		});
	}

	/**
	 * Paying and working, one round at a time: without the cost nothing is taken; with it the cost goes, and points
	 * come only at the lectern. A Legend on strike has no job; no home or no lectern near it, neither. Scholars with
	 * nothing of their own help at half speed, but not while the Legend strikes.
	 */
	//$ gametest_ticks_batch AREA '100' '"treeCostsAndHelp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "treeCostsAndHelp")
	public void treeCostsAndHelp(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(3, () -> {
			ResearchTree tree = tree(helper);
			ResearchTree.Topic calm = topic(helper, "calm_minds");
			helper.assertTrue(TreeWork.job(s.level(), s.sage()) == null, "a job with nothing chosen");
			s.entity().setResearch(ResearchTrees.choose(s.entity().research(), tree, calm));
			TreeWork.Job job = TreeWork.job(s.level(), s.sage());
			helper.assertTrue(job != null && job.lectern().equals(helper.absolutePos(LECTERN)), "no job at the lectern: " + job);
			s.chest().setItem(0, new ItemStack(Items.PAPER, 3));
			TreeWork.step(s.level(), s.sage(), job, true);
			helper.assertTrue(!ResearchTrees.current(s.entity().research(), tree).orElseThrow().paid() && s.chest().countItem(Items.PAPER) == 3,
				"paid without the cost: " + s.entity().research());
			s.chest().setItem(1, new ItemStack(Items.PAPER, 2));
			TreeWork.step(s.level(), s.sage(), job, false);
			ResearchTrees.Current paid = ResearchTrees.current(s.entity().research(), tree).orElseThrow();
			helper.assertTrue(paid.paid() && paid.progress() == 0 && s.chest().countItem(Items.PAPER) == 1, "not paid: " + paid + ", paper " + s.chest().countItem(Items.PAPER));
			TreeWork.step(s.level(), s.sage(), job, false);
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).orElseThrow().progress() == 0, "points away from the lectern");
			TreeWork.step(s.level(), s.sage(), job, true);
			int sagePoints = ResearchTrees.current(s.entity().research(), tree).orElseThrow().progress();
			helper.assertTrue(sagePoints > 0, "no points at the lectern");
			// a scholar with nothing to research helps at half speed
			helper.setBlock(new BlockPos(12, 2, 12), ModBlocks.SCHOLARS_DESK);
			Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 12));
			Jobs.employ(s.level(), scholar, helper.absolutePos(new BlockPos(12, 2, 12)), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
			LegendPowers.forget();
			helper.assertTrue(ResearchTrees.help(s.level(), scholar, s.hall(), s.entity(), 20, true) == tree, "the scholar didn't help");
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).orElseThrow().progress() == sagePoints + 10, "not half speed: "
				+ ResearchTrees.current(s.entity().research(), tree).orElseThrow().progress());
			// on strike: no job, and the scholars don't help
			ModAttachments.LEGEND.set(s.sage(), ModAttachments.LEGEND.get(s.sage()).checkedOn(1, Map.of(), 1, -1));
			LegendPowers.forget();
			helper.assertTrue(TreeWork.job(s.level(), s.sage()) == null, "works on strike");
			helper.assertTrue(ResearchTrees.help(s.level(), scholar, s.hall(), s.entity(), 20, true) == null, "scholars help a striking Legend");
			ModAttachments.LEGEND.set(s.sage(), ModAttachments.LEGEND.get(s.sage()).checkedOn(1, Map.of(), -1, -1));
			// no lectern near home: no job
			helper.setBlock(LECTERN, Blocks.AIR);
			helper.assertTrue(TreeWork.job(s.level(), s.sage()) == null, "a job without a lectern");
			helper.setBlock(LECTERN, Blocks.LECTERN);
			s.sage().getBrain().eraseMemory(MemoryModuleType.HOME);
			helper.assertTrue(TreeWork.job(s.level(), s.sage()) == null, "a job without a home");
			helper.succeed();
		});
	}

	/** Levels and the topic in progress are kept in the hall's research map and come back after a save and reload. */
	//$ gametest_ticks_batch AREA '40' '"treeLevelsSaved"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "treeLevelsSaved")
	public void treeLevelsSaved(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			ResearchTree tree = tree(helper);
			Research.State state = new Research.State(Map.of("swift_hands", 1), Optional.of("hearth"), 30, true);
			state = ResearchTrees.choose(state, tree, topic(helper, "calm_minds"));
			state = ResearchTrees.withProgress(state, tree, 0);
			state = ResearchTrees.finish(state, tree);
			state = ResearchTrees.choose(state, tree, topic(helper, "herb_lore"));
			state = ResearchTrees.withProgress(state, tree, 17);
			s.entity().setResearch(state);
			CompoundTag tag = s.entity().saveWithFullMetadata(s.level().registryAccess());
			VillageHallBlockEntity reloaded = (VillageHallBlockEntity) BlockEntity.loadStatic(s.hall(), s.level().getBlockState(s.hall()), tag, s.level().registryAccess());
			helper.assertTrue(reloaded != null, "no hall");
			Research.State back = reloaded.research();
			helper.assertTrue(back.levels().get(TREE + "/calm_minds") == 1, "level lost: " + back);
			ResearchTrees.Current now = ResearchTrees.current(back, tree).orElse(null);
			helper.assertTrue(now != null && now.topic().id().equals("herb_lore") && now.paid() && now.progress() == 17, "progress lost: " + back);
			helper.assertTrue(back.level(Research.Topic.SWIFT_HANDS) == 1 && back.currentTopic() == Research.Topic.HEARTH && back.progress() == 30 && back.paid(),
				"the scholars' research changed: " + back);
			helper.assertTrue(back.totalLevels() == 2, "levels counted wrong (the topic in progress counted?): " + back.totalLevels());
			helper.succeed();
		});
	}

	/** The last pick: once The Flame is taken (paid for, or researched) The Iron Pact is refused, for good. */
	//$ gametest_ticks_batch AREA '40' '"treeExclusivePick"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "treeExclusivePick")
	public void treeExclusivePick(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			ResearchTree tree = tree(helper);
			ResearchTree.Topic flame = topic(helper, "flame");
			ResearchTree.Topic iron = topic(helper, "iron_pact");
			ResearchScreen.View view = new ResearchScreen.View();
			ChoiceMenu menu = ResearchScreen.forTest(s.player(), s.hall(), view);
			menu.press(ResearchScreen.TAB_SLOTS[1], s.player());
			// chosen but not paid: the village may still change its mind
			menu.press(ResearchScreen.TOPIC_SLOTS[6], s.player());
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).map(c -> c.topic() == flame).orElse(false), "The Flame not chosen");
			helper.assertTrue(ResearchTrees.whyNot(s.level(), s.hall(), s.entity().research(), tree, iron).isEmpty(), "Iron Pact refused before The Flame was paid for");
			// researched: Iron Pact refused on the screen
			s.entity().setResearch(ResearchTrees.finish(ResearchTrees.withProgress(s.entity().research(), tree, 0), tree));
			helper.assertTrue(ResearchTrees.level(s.entity().research(), tree, flame) == 1, "The Flame not researched");
			menu.press(ResearchScreen.TOPIC_SLOTS[7], s.player());
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).isEmpty(), "Iron Pact chosen after The Flame: " + s.entity().research());
			Optional<Component> why = ResearchTrees.whyNot(s.level(), s.hall(), s.entity().research(), tree, iron);
			helper.assertTrue(why.isPresent() && why.get().getString().contains("The Flame"), "no reason: " + why);
			String lore = String.join(" / ", menu.icon(ResearchScreen.TOPIC_SLOTS[7]).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY, null,
				net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(Component::getString).toList());
			helper.assertTrue(lore.contains("The Flame") && !lore.contains("Click"), "the Iron Pact's icon: " + lore);
			// paid for (not yet researched) counts as taken too
			Research.State fresh = ResearchTrees.withProgress(ResearchTrees.choose(levels(Map.of()), tree, flame), tree, 5);
			helper.assertTrue(ResearchTrees.whyNot(s.level(), s.hall(), fresh, tree, iron).isPresent(), "Iron Pact open while The Flame is paid for");
			helper.succeed();
		});
	}

	/** Census waits for the village's test counter to reach 3, on the screen and when clicked. */
	//$ gametest_ticks_batch AREA '40' '"treeUnlockWaits"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "treeUnlockWaits")
	public void treeUnlockWaits(GameTestHelper helper) {
		Setup s = setup(helper);
		Leftovers.after(helper, () -> testCounter = 0);
		helper.runAfterDelay(2, () -> {
			ResearchTree tree = tree(helper);
			ResearchTree.Topic census = topic(helper, "census");
			testCounter = 2;
			ResearchScreen.View view = new ResearchScreen.View();
			ChoiceMenu menu = ResearchScreen.forTest(s.player(), s.hall(), view);
			menu.press(ResearchScreen.TAB_SLOTS[1], s.player());
			String lore = String.join(" / ", menu.icon(ResearchScreen.TOPIC_SLOTS[5]).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY, null,
				net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(Component::getString).toList());
			helper.assertTrue(lore.contains("Opens at 3 test things (now 2)"), "the icon: " + lore);
			menu.press(ResearchScreen.TOPIC_SLOTS[5], s.player());
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).isEmpty(), "chosen at 2 of 3");
			testCounter = 3;
			menu.press(ResearchScreen.TOPIC_SLOTS[5], s.player());
			helper.assertTrue(ResearchTrees.current(s.entity().research(), tree).map(c -> c.topic() == census).orElse(false), "not chosen at 3: " + s.entity().research());
			helper.succeed();
		});
	}

	/**
	 * Each effect: wellbeing (points and the floor), illness (chance and days), raid_chance, xp, loot_luck and flag, at
	 * the levels the hall keeps; nothing without them.
	 */
	//$ gametest_ticks_batch AREA '60' '"treeEffectsWork"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "treeEffectsWork")
	public void treeEffectsWork(GameTestHelper helper) {
		Setup s = setup(helper);
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 12));
		helper.runAfterDelay(2, () -> {
			ServerLevel level = s.level();
			float wellBefore = VillageNeeds.count(level, s.hall()).wellbeing();
			float illBefore = Sickness.dailyChance(level, a);
			float raidBefore = VillageRaids.chance(level, s.hall(), 30);
			int xpBefore = Traits.xp(a, 100, RandomSource.create(7));
			float luckBefore = SifterWork.params(level, a, a.blockPosition()).getLuck();
			helper.assertTrue(Sickness.recovery(a) == Sickness.RECOVERY && !TreeEffects.flag(level, s.hall(), "test_flag") && !TreeEffects.flag(a, "test_flag"),
				"effects with nothing researched");
			helper.assertTrue(wellBefore < 0.75f, "the staged village is too well already: " + wellBefore);
			s.entity().setResearch(levels(Map.of("calm_minds", 2, "herb_lore", 2, "warding", 1, "deep_memory", 1, "fortune", 1, "census", 1)));
			CivicEffects.forget();
			float well = VillageNeeds.count(level, s.hall()).wellbeing();
			helper.assertTrue(Math.abs(well - (wellBefore + 0.2f)) < 1e-4f, "wellbeing " + wellBefore + " -> " + well);
			float ill = Sickness.dailyChance(level, a);
			helper.assertTrue(Math.abs(ill - illBefore * 0.6f) < 1e-5f, "illness chance " + illBefore + " -> " + ill);
			helper.assertTrue(Sickness.recovery(a) == Sickness.RECOVERY - 2 * VillageNeeds.DAY, "illness lasts " + Sickness.recovery(a));
			float raid = VillageRaids.chance(level, s.hall(), 30);
			helper.assertTrue(Math.abs(raid - raidBefore * 0.8f) < 1e-5f, "raid chance " + raidBefore + " -> " + raid);
			int xp = Traits.xp(a, 100, RandomSource.create(7));
			helper.assertTrue(xp == xpBefore + 15, "xp " + xpBefore + " -> " + xp);
			float luck = SifterWork.params(level, a, a.blockPosition()).getLuck();
			helper.assertTrue(luck == luckBefore + 2, "loot luck " + luckBefore + " -> " + luck);
			helper.assertTrue(TreeEffects.flag(level, s.hall(), "test_flag") && TreeEffects.flag(b, "test_flag") && !TreeEffects.flag(level, s.hall(), "iron"),
				"flags");
			// The Flame: wellbeing never below 90%
			s.entity().setResearch(levels(Map.of("flame", 1)));
			helper.assertTrue(VillageNeeds.count(level, s.hall()).wellbeing() >= 0.9f - 1e-4f, "below the floor: " + VillageNeeds.count(level, s.hall()).wellbeing());
			helper.assertTrue(TreeEffects.flag(level, s.hall(), "flame"), "flame flag");
			helper.succeed();
		});
	}

	/**
	 * An old hall's research (saved before the trees) loads unchanged: the same levels and topic, no tree progress, no
	 * tree effects; and with no Legend in the village the screen has no tabs.
	 */
	//$ gametest_ticks_batch AREA '40' '"oldHallResearchUnchanged"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "oldHallResearchUnchanged")
	public void oldHallResearchUnchanged(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			CompoundTag research = new CompoundTag();
			CompoundTag lv = new CompoundTag();
			lv.putInt("swift_hands", 2);
			lv.putInt("hearth", 1);
			research.put("levels", lv);
			research.putString("current", "drill");
			research.putInt("progress", 50);
			research.putBoolean("paid", true);
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			tag.put("research", research.copy());
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(old != null, "no hall");
			Research.State state = old.research();
			helper.assertTrue(state.levels().equals(Map.of("swift_hands", 2, "hearth", 1)) && state.currentTopic() == Research.Topic.DRILL
				&& state.progress() == 50 && state.paid() && state.totalLevels() == 3, "changed: " + state);
			helper.assertTrue(ResearchTrees.current(state, tree(helper)).isEmpty() && ResearchTrees.effects(old).isEmpty(), "tree progress or effects from nothing");
			Tag again = Research.State.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
			helper.assertTrue(again.equals(research), "saved differently: " + again + " vs " + research);
			ChoiceMenu menu = ResearchScreen.forTest(helper.makeMockServerPlayerInLevel(), hall);
			helper.assertTrue(menu.icon(ResearchScreen.TAB_SLOTS[0]).isEmpty() && menu.icon(ResearchScreen.TAB_SLOTS[1]).isEmpty(), "tabs without a Legend");
			helper.succeed();
		});
	}
}
