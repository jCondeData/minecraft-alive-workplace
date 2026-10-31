package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-10-31) for ROADMAP 31.16, "The Bandit King, II: the story", from the spec: the edges of its allies (24
 * emeralds at the hall, 16 bread for Pell), the storm's 48-block Rally Banner, raids every second night and half again
 * as big, the tribute's third, and the ending's 10 days without bandit camps. Through the player's own entry points
 * where there is one (the hall's Story tab, a right-click on Pell, a sneak-right-click and a raise of the banner).
 * {@link BanditKingStoryGameTests} walks the whole arc; this covers its boundaries from both sides.
 */
public class QaBanditKingAllies1031GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final long DAY = VillageNeeds.DAY;

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		VillageHalls.RADIUS = 8;
		Arcs.NEAR = 0; // (nothing far is built or spawned unless the test asks)
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
			VillageRaids.ENABLED = raids;
		});
	}

	/** No roof of barriers over the area, so the heightmap (allies, Pell, raiders' ground) is the floor. */
	private static void open(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static Arcs.Arc arc(GameTestHelper helper) {
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null, "the Bandit King's arc didn't load");
		return arc;
	}

	/** The arc started and moved on to its second chapter (the fort and the allies), its camps stood in the test area. */
	private static ArcState toChapterTwo(GameTestHelper helper, BlockPos hall, BlockPos campsAt) {
		ServerLevel level = helper.getLevel();
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && s.chapter == 0, "the arc didn't start");
		s.mobs.forEach(m -> m.dead = true); // (the camps' captains and bandits are far off; not this test's)
		for (ArcState.ArcPlace p : s.places) {
			if (p.key.equals(BanditKingStory.FIRST_CAMP) || p.key.startsWith(BanditKingStory.CAMP)) {
				p.spot = helper.absolutePos(campsAt);
				p.placed = 1;
			}
		}
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 1 && s.started, "chapter 2 didn't begin: " + s.chapter);
		return s;
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		return player.getInventory().countItem(item);
	}

	private static List<UUID> following(ServerPlayer player) {
		List<UUID> out = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.RALLY_BANNER)) {
				out.addAll(RallyBannerItem.rally(stack).guards());
			}
		}
		return out;
	}

	private static long mercenaries(List<Villager> allies) {
		return allies.stream().filter(v -> v.getCustomName() != null && v.getCustomName().getString().equals("Mercenary")).count();
	}

	/**
	 * Swords for hire, at the hall's Story tab the way a player pays: with 23 emeralds the mercenaries aren't hired; with
	 * the 24th they are, exactly 24 are taken in all (of 30 carried), and 6 mercenaries (no one else) come for the storm.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaSwordsForHireAt24Emeralds"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaSwordsForHireAt24Emeralds")
	public void qaSwordsForHireAt24Emeralds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		settings(helper);
		open(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		setDay(level, Chronicle.day(level) + 1);
		ArcState s = toChapterTwo(helper, hall, new BlockPos(24, 2, 24));
		ServerPlayer player = player(helper, new BlockPos(7, 2, 5));
		player.getInventory().setItem(3, new ItemStack(Items.EMERALD, 23));
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		menu.press(QuestJournal.STORY_QUEST_SLOTS[0], player);
		int leftAfter23 = count(player, Items.EMERALD);
		System.out.println("QA31.16 swords: after handing in with 23 emeralds the player has " + leftAfter23 + " left");
		helper.assertTrue(!s.flags.containsKey(BanditKingStory.ALLY_MERCENARIES), "the mercenaries were hired for 23 emeralds");
		player.getInventory().setItem(5, new ItemStack(Items.EMERALD, 30 - 23));
		menu.press(QuestJournal.STORY_QUEST_SLOTS[0], player);
		helper.assertTrue(s.flags.containsKey(BanditKingStory.ALLY_MERCENARIES), "24 emeralds didn't hire the mercenaries (left " + count(player, Items.EMERALD) + ")");
		int left = count(player, Items.EMERALD);
		helper.assertTrue(left == 30 - 24, "emeralds taken in all: " + (30 - left) + " of 30 (left " + left + ", " + leftAfter23 + " after the first try)");
		level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.EMERALD)).forEach(ItemEntity::discard);
		// The storm: 6 mercenaries and nobody else (no pledge, no Pell).
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started, "the storm didn't begin: " + s.chapter);
		List<Villager> allies = BanditKingStory.allies(level, hall);
		helper.assertTrue(allies.size() == 6 && mercenaries(allies) == 6, "allies at the storm: " + allies.size() + ", mercenaries " + mercenaries(allies));
		helper.succeed();
	}

	/** Without paying (no ally won) nobody comes for the storm. */
	//$ gametest_ticks_batch HUGE '40' '"qaNoAlliesWithoutPaying"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaNoAlliesWithoutPaying")
	public void qaNoAlliesWithoutPaying(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		open(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		setDay(level, Chronicle.day(level) + 1);
		ArcState s = toChapterTwo(helper, hall, new BlockPos(24, 2, 24));
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started, "the storm didn't begin: " + s.chapter);
		helper.assertTrue(BanditKingStory.allies(level, hall).isEmpty(), "allies came unpaid: " + BanditKingStory.allies(level, hall).size());
		helper.succeed();
	}

	/**
	 * The deserter, right-clicked the way a player does (the use-entity event): with 15 bread Pell asks for 16, takes no
	 * bread and tells nothing (no postern); with 16 (in two stacks) all 16 are his, he tells of the postern; a second
	 * right-click with 16 more takes nothing.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaPellWantsSixteenBread"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaPellWantsSixteenBread")
	public void qaPellWantsSixteenBread(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		settings(helper);
		open(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		setDay(level, Chronicle.day(level) + 1);
		BlockPos campAt = new BlockPos(22, 2, 22);
		ArcState s = toChapterTwo(helper, hall, campAt);
		ServerPlayer player = player(helper, campAt.offset(1, 0, 0));
		Arcs.NEAR = 16;
		ArcEffects.check(level);
		Arcs.NEAR = 0;
		Villager pell = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> v.getTags().contains(ArcEffects.roleTag(BanditKingStory.PELL_ROLE)))
			.stream().findFirst().orElse(null);
		helper.assertTrue(pell != null, "Pell didn't come by the first camp");
		player.getInventory().setItem(0, new ItemStack(Items.BREAD, 9));
		player.getInventory().setItem(20, new ItemStack(Items.BREAD, 6));
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, pell, null);
		helper.assertTrue(count(player, Items.BREAD) == 15, "Pell took bread when offered 15: " + count(player, Items.BREAD) + " left");
		helper.assertTrue(!s.flags.containsKey(BanditKingStory.ALLY_PELL) && !s.flags.containsKey(BanditFort.POSTERN_FLAG), "Pell told of the postern for 15 bread");
		player.getInventory().setItem(30, new ItemStack(Items.BREAD, 1));
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, pell, null);
		helper.assertTrue(count(player, Items.BREAD) == 0, "Pell didn't take exactly 16: " + count(player, Items.BREAD) + " left of 16");
		helper.assertTrue(s.flags.containsKey(BanditKingStory.ALLY_PELL) && s.flags.containsKey(BanditFort.POSTERN_FLAG), "16 bread didn't win Pell (postern)");
		player.getInventory().setItem(1, new ItemStack(Items.BREAD, 16));
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, pell, null);
		helper.assertTrue(count(player, Items.BREAD) == 16, "Pell took bread again once he'd told: " + count(player, Items.BREAD) + " left of 16");
		helper.succeed();
	}

	/**
	 * The storm's Rally Banner, as a player does it: sneak-right-click one mercenary with a Rally Banner (enlist), raise
	 * it, and stand off the fort. 49 blocks out (and 34, 34 diagonally: 48.08) the allies don't follow; 48 out they all do.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaRallyBannerWithin48OfTheFort"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaRallyBannerWithin48OfTheFort")
	public void qaRallyBannerWithin48OfTheFort(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		settings(helper);
		open(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		setDay(level, Chronicle.day(level) + 1);
		ArcState s = toChapterTwo(helper, hall, new BlockPos(24, 2, 24));
		s.flags.put(BanditKingStory.ALLY_MERCENARIES, ArcState.FOREVER);
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started, "the storm didn't begin: " + s.chapter);
		ArcState.ArcPlace fort = s.place(BanditKingStory.FORT);
		helper.assertTrue(fort != null, "no fort place");
		BlockPos spot = helper.absolutePos(new BlockPos(15, 2, 15));
		fort.spot = spot;
		List<Villager> allies = BanditKingStory.allies(level, hall);
		helper.assertTrue(allies.size() == 6, "allies at the storm: " + allies.size());
		ServerPlayer player = player(helper, new BlockPos(7, 2, 5));
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		player.setItemInHand(InteractionHand.MAIN_HAND, banner);
		boolean raisedEmpty = banner.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction() && RallyBannerItem.rally(player.getMainHandItem()).raised();
		System.out.println("QA31.16 rally: an empty Rally Banner could be raised: " + raisedEmpty);
		if (RallyBannerItem.rally(player.getMainHandItem()).raised()) {
			player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND); // (lowered again for the walk below)
		}
		player.setShiftKeyDown(true);
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, allies.get(0), null);
		player.setShiftKeyDown(false);
		helper.assertTrue(following(player).size() == 1, "sneak-right-click didn't enlist the mercenary: " + following(player).size());
		player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
		helper.assertTrue(RallyBannerItem.rally(player.getMainHandItem()).raised(), "the banner didn't raise");
		// 49 blocks out, and 48.08 on the diagonal: too far.
		for (BlockPos off : List.of(new BlockPos(49, 0, 0), new BlockPos(0, 0, -49), new BlockPos(34, 0, 34))) {
			BlockPos at = spot.offset(off);
			player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			BanditKingStory.tick(level);
			helper.assertTrue(following(player).size() == 1, "the allies followed a banner raised " + Math.sqrt(off.distSqr(BlockPos.ZERO)) + " blocks from the fort: " + following(player).size());
		}
		// 48 blocks out: they follow.
		BlockPos at = spot.offset(-48, 0, 0);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		BanditKingStory.tick(level);
		List<UUID> on = following(player);
		helper.assertTrue(on.size() == 6 && allies.stream().allMatch(v -> on.contains(v.getUUID())), "the allies didn't follow a banner raised 48 blocks from the fort: " + on.size());
		helper.succeed();
	}

	/**
	 * Raids every second night until the King falls: the storm's first night (not before dusk) the King's raiders come,
	 * and only once; the second night none; the third night again; half again as big as the village's plain bandit raid
	 * (8 villagers: the plain band is 5, the King's at least 7); and none once the King has fallen.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaKingsRaidsEverySecondNight"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingsRaidsEverySecondNight")
	public void qaKingsRaidsEverySecondNight(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		settings(helper);
		open(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		for (int i = 0; i < 8; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3 + i % 4, 2, 9 + i / 4));
			v.setNoAi(true);
		}
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ArcState s = toChapterTwo(helper, hall, new BlockPos(26, 2, 26));
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started && s.chapterDay == day, "the storm didn't begin today: " + s.chapter + ", day " + s.chapterDay);
		ArcState.ArcPlace fort = s.place(BanditKingStory.FORT);
		BlockPos spot = helper.absolutePos(new BlockPos(22, 2, 22));
		fort.spot = spot;
		VillageRaids.ENABLED = true;
		VillageRaids.callOff(level, hall);
		// Day 1 of the storm, before dusk: nothing.
		level.setDayTime((day - 1) * DAY + VillageRaids.DUSK - 1);
		BanditKingStory.tick(level);
		helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the King's raiders came before dusk");
		// Its first night: they come, half again as big.
		level.setDayTime((day - 1) * DAY + 13000);
		BanditKingStory.tick(level);
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "no raid on the storm's first night");
		long kingsBand = VillageRaids.raiders(level, hall).stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG) && !m.getTags().contains(Lairs.CAPTAIN_TAG)).count();
		VillageRaids.callOff(level, hall);
		level.setDayTime((day - 1) * DAY + 14000);
		BanditKingStory.tick(level);
		helper.assertTrue(VillageRaids.active(hall).isEmpty(), "a second King's raid the same night");
		var culture = Threats.get(Threats.BANDITS).orElse(null);
		helper.assertTrue(culture != null, "no bandit culture");
		VillageRaids.Raid plain = VillageRaids.start(level, hall, 8, 0, Double.NaN, culture, RandomSource.create(31016));
		helper.assertTrue(plain != null, "no plain bandit raid to compare with");
		long plainBand = VillageRaids.raiders(level, hall).stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG) && !m.getTags().contains(Lairs.CAPTAIN_TAG)).count();
		VillageRaids.callOff(level, hall);
		System.out.println("QA31.16 raids: the King's band " + kingsBand + ", the plain bandit band " + plainBand + " (8 villagers)");
		helper.assertTrue(kingsBand >= plainBand * 3 / 2 && kingsBand > plainBand, "the King's raid isn't half again as big: " + kingsBand + " against the plain " + plainBand);
		// The second night: none.
		level.setDayTime(day * DAY + 13000);
		BanditKingStory.tick(level);
		helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the King's raiders came the storm's second night too");
		// The third night: again.
		level.setDayTime((day + 1) * DAY + 13000);
		BanditKingStory.tick(level);
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "no raid on the storm's third night");
		VillageRaids.callOff(level, hall);
		// The fourth day the King falls; its night (and the fifth, a raid night) nobody comes.
		level.setDayTime((day + 2) * DAY + 1000);
		ServerPlayer player = player(helper, new BlockPos(20, 2, 20));
		Mob king = BanditKing.spawn(level, spot, spot, spot.offset(0, 0, -3));
		helper.assertTrue(king != null, "no King");
		king.hurt(level.damageSources().playerAttack(player), 10000f);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end with the King's fall");
		for (long d : List.of(day + 2, day + 3, day + 4)) {
			level.setDayTime((d - 1) * DAY + 13000);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the King's raiders came on day " + (d - day + 1) + " after he fell");
		}
		helper.succeed();
	}

	/**
	 * The tribute at 10 days, to the tick: at the last tick of the storm's tenth day nothing is taken; at the first of
	 * the eleventh, a third of a treasury of 100 emeralds (33 or 34; the test says which), told in the chronicle.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaTributeIsAThirdAtTenDays"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaTributeIsAThirdAtTenDays")
	public void qaTributeIsAThirdAtTenDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ArcState s = toChapterTwo(helper, hall, new BlockPos(24, 2, 24));
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started && s.chapterDay == day, "the storm didn't begin today");
		entity.setTreasury(10000); // (hundredths: 100 emeralds)
		level.setDayTime((day + 9) * DAY - 1); // the last tick of the storm's tenth day
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == s && entity.treasury() == 10000, "the tribute came a tick early: " + entity.treasury());
		level.setDayTime((day + 9) * DAY); // the eleventh day: 10 whole days have passed
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "no tribute after 10 days");
		int taken = 10000 - entity.treasury();
		System.out.println("QA31.16 tribute: of 10000 hundredths the King took " + taken);
		helper.assertTrue(taken == 3333 || taken == 3334, "the tribute of 100 emeralds: " + taken / 100.0 + " emeralds taken");
		List<String> told = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
		helper.assertTrue(told.contains("The Bandit King took 33 emeralds in tribute and left his fort a ruin."), "the chronicle: " + told);
		helper.succeed();
	}

	/**
	 * The ending's 10 days without bandit camps: the King falls today (killed by a player); the ninth day after (the
	 * tenth day counting today) a camp that would surely come (a draw of 0) doesn't; the tenth day after, camps may come
	 * again. (A camp isn't built on that day: it would stand 80 to 104 blocks out, past any test area.)
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaNoBanditCampsForTenDays"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaNoBanditCampsForTenDays")
	public void qaNoBanditCampsForTenDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		settings(helper);
		open(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		for (int i = 0; i < 8; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(3 + i % 4, 2, 9 + i / 4)).setNoAi(true);
		}
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ArcState s = toChapterTwo(helper, hall, new BlockPos(26, 2, 26));
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		BlockPos spot = helper.absolutePos(new BlockPos(22, 2, 22));
		s.place(BanditKingStory.FORT).spot = spot;
		ServerPlayer player = player(helper, new BlockPos(20, 2, 20));
		Mob king = BanditKing.spawn(level, spot, spot, spot.offset(0, 0, -3));
		helper.assertTrue(king != null, "no King");
		king.hurt(level.damageSources().playerAttack(player), 10000f);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end with the King's fall");
		BanditCamps.ENABLED = true;
		entity.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN); // (the arc's own trigger needs a Town)
		RandomSource sure = new LegacyRandomSource(31016L) {
			@Override
			public float nextFloat() {
				return 0f;
			}
		};
		setDay(level, day + 9);
		helper.assertTrue(Stories.villageFlag(level, hall, "no_bandit_camps"), "bandit camps may come back the 10th day of the 10");
		helper.assertTrue(Lairs.comer(level, hall, true, RandomSource.create(1)).isPresent() && Lairs.dailyChance(level, hall) > 0
			&& Lairs.resting(level, hall) == 0 && !Threats.quiet(level, hall), "(setup) bandits wouldn't come here anyway: comer "
			+ Lairs.comer(level, hall, true, RandomSource.create(1)) + ", chance " + Lairs.dailyChance(level, hall) + ", resting " + Lairs.resting(level, hall));
		Lairs.round(level, hall, true, sure);
		helper.assertTrue(Lairs.of(level, hall, Threats.BANDITS).isEmpty(), "a bandit camp came the 10th day after the King fell");
		setDay(level, day + 10);
		helper.assertTrue(!Stories.villageFlag(level, hall, "no_bandit_camps"), "bandit camps still kept off on the 11th day");
		helper.succeed();
	}
}
