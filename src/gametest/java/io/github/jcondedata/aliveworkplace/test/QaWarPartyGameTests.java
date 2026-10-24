package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA for ROADMAP 32.13 "Marching on the lair", written from the spec: the muster and the Siege-breakers through the
 * Defence page's own buttons (what a click on the hall's screen runs), the muster's share and its rest, no banner,
 * the Commerce discount at its edges, the two dawns' stay, a lair broken without a war party, exactly double experience
 * at a lair, and every new sentence a player reads.
 */
public class QaWarPartyGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos HALL = new BlockPos(1, 2, 1);
	static final BlockPos POST = new BlockPos(4, 2, 4);
	static final BlockPos LAIR = new BlockPos(13, 2, 13);

	static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
		Expansions.openForTests = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(80),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(40), v -> v.getTags().contains(WarParties.SIEGE_TAG)).forEach(Villager::discard);
			Lairs.forget(level);
			WarParties.forget(level);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
		});
		level.setDayTime(1000);
		return hall;
	}

	static Villager guard(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		return villager;
	}

	/** A player with a real connection, standing by the hall, whose chat is kept in {@code seen}. */
	static QaStageFiveGameTests.Client client(GameTestHelper helper) {
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			c.player().getInventory().clearContent();
			Escorts.scan(helper.getLevel().getServer());
		});
		return c;
	}

	/** The hall's screen as the player sees it, turned to the Defence page. */
	static ChoiceMenu defencePage(ServerPlayer player, ServerLevel level, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		DefencePage.render(menu, level, hall);
		return menu;
	}

	static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	static String last(List<Component> seen) {
		return seen.isEmpty() ? "" : seen.get(seen.size() - 1).getString();
	}

	static Lairs.Lair lair(GameTestHelper helper, BlockPos hall, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(at);
		Vindicator captain = EntityType.VINDICATOR.create(level);
		captain.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		captain.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
		captain.setNoAi(true);
		captain.setPersistenceRequired();
		captain.addTag(Lairs.TAG);
		captain.addTag(Lairs.CAPTAIN_TAG);
		level.addFreshEntity(captain);
		Lairs.Lair lair = new Lairs.Lair(pos, hall, captain.getUUID(), Chronicle.day(level), Threats.BANDITS, 7, Chronicle.day(level), 0, 0, 3);
		Lairs.putForTest(level, lair);
		return lair;
	}

	static Vindicator bandit(GameTestHelper helper, BlockPos at) {
		Vindicator bandit = helper.spawn(EntityType.VINDICATOR, at);
		bandit.setNoAi(true);
		bandit.addTag(Lairs.TAG);
		return bandit;
	}

	static List<Villager> siegeBreakers(ServerLevel level, BlockPos hall) {
		return Mercenaries.near(level, hall).stream().filter(WarParties::isSiegeBreaker).toList();
	}

	/**
	 * Eight guards, a Rally Banner deep in the backpack (not the hotbar): clicking "Muster a war party" on the page enlists
	 * six of them on it (three in four), raises it, and the chat says six gather and two keep the walls; the two left are
	 * not following anyone. Clicking it again doesn't enlist anyone twice.
	 */
	//$ gametest_batch AREA '"qaWpMuster8"'
	@GameTest(template = AREA, batch = "qaWpMuster8")
	public void qaMusterOnThePageEnlistsSixOfEightAndTwoKeepTheWalls(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = client(helper);
		ServerPlayer p = c.player();
		List<Villager> guards = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			guards.add(guard(helper, new BlockPos(3 + i, 2, 7)));
		}
		p.getInventory().setItem(30, new ItemStack(ModItems.RALLY_BANNER));
		ChoiceMenu menu = defencePage(p, level, hall);
		click(menu, DefencePage.MUSTER, p);
		RallyBannerItem.Rally rally = RallyBannerItem.rally(WarParties.banner(p));
		helper.assertTrue(rally.raised(), "the banner isn't raised");
		helper.assertTrue(rally.guards().size() == 6 && new HashSet<>(rally.guards()).size() == 6, "enlisted " + rally.guards());
		helper.assertTrue(guards.stream().filter(g -> rally.guards().contains(g.getUUID())).count() == 6, "enlisted someone who isn't a guard here");
		List<Villager> left = guards.stream().filter(g -> !rally.guards().contains(g.getUUID())).toList();
		helper.assertTrue(left.size() == 2 && left.stream().noneMatch(Escorts::isEscorting), "the two who keep the walls follow the banner");
		helper.assertTrue(guards.stream().filter(Escorts::isEscorting).count() == 6, guards.stream().filter(Escorts::isEscorting).count() + " follow");
		String said = last(c.seen());
		helper.assertTrue(said.equals("6 guards gather by the hall under your Rally Banner; 2 keep the walls"), "said: " + said);
		// Again: still the same six, nobody twice.
		click(menu, DefencePage.MUSTER, p);
		RallyBannerItem.Rally again = RallyBannerItem.rally(WarParties.banner(p));
		helper.assertTrue(again.raised() && again.guards().size() == 6 && new HashSet<>(again.guards()).equals(new HashSet<>(rally.guards())),
			"a second muster changed the party: " + again.guards());
		helper.succeed();
	}

	/**
	 * No Rally Banner, only a plain white banner: the page's muster says to craft one and enlists nobody, nobody follows
	 * the player, and the white banner is untouched.
	 */
	//$ gametest_batch AREA '"qaWpNoBanner"'
	@GameTest(template = AREA, batch = "qaWpNoBanner")
	public void qaMusterWithoutARallyBannerSaysCraftOneAndEnlistsNobody(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = client(helper);
		ServerPlayer p = c.player();
		List<Villager> guards = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			guards.add(guard(helper, new BlockPos(3 + i, 2, 7)));
		}
		p.getInventory().add(new ItemStack(Items.WHITE_BANNER));
		ChoiceMenu menu = defencePage(p, level, hall);
		click(menu, DefencePage.MUSTER, p);
		String said = last(c.seen());
		helper.assertTrue(said.equals("Craft a Rally Banner first: the war party gathers under it"), "said: " + said);
		Escorts.scan(level.getServer());
		helper.assertTrue(guards.stream().noneMatch(Escorts::isEscorting), "a guard follows without a banner");
		ItemStack white = p.getInventory().getItem(p.getInventory().findSlotMatchingItem(new ItemStack(Items.WHITE_BANNER)));
		helper.assertTrue(white.is(Items.WHITE_BANNER) && white.get(ModComponents.RALLY) == null, "the white banner was enlisted on: " + white);
		helper.succeed();
	}

	/**
	 * Commerce level 2: the page prices the Siege-breakers at 24 emeralds (30, 3 less a level). 23 emeralds (in two
	 * stacks) is refused, says the price, keeps every emerald and hires nobody; one more buys them at exactly 24, leaving
	 * none: two crossbows, two knights and a medic, and the chat says five join. Clicking again while they're here
	 * charges nothing more.
	 */
	//$ gametest_batch AREA '"qaWpCommerce"'
	@GameTest(template = AREA, batch = "qaWpCommerce")
	public void qaSiegeBreakersCostThreeLessPerCommerceLevelAtTheEdges(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = client(helper);
		ServerPlayer p = c.player();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setResearch(new Research.State(Map.of("commerce", 2), Optional.empty(), 0, false));
		ChoiceMenu menu = defencePage(p, level, hall);
		List<String> lore = QaStageSixGameTests.lore(menu.icon(DefencePage.SIEGE_BREAKERS));
		helper.assertTrue(lore.contains("For 24 emeralds"), "the page's price: " + lore);
		p.getInventory().add(new ItemStack(Items.EMERALD, 20));
		p.getInventory().add(new ItemStack(Items.EMERALD, 3));
		click(menu, DefencePage.SIEGE_BREAKERS, p);
		helper.assertTrue(last(c.seen()).equals("The Siege-breakers cost 24 emeralds"), "one short said: " + last(c.seen()));
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 23, "one short took emeralds: " + p.getInventory().countItem(Items.EMERALD));
		helper.assertTrue(siegeBreakers(level, hall).isEmpty(), "hired one short");
		p.getInventory().add(new ItemStack(Items.EMERALD, 1));
		click(menu, DefencePage.SIEGE_BREAKERS, p);
		helper.assertTrue(last(c.seen()).startsWith("5 Siege-breakers join you"), "hired said: " + last(c.seen()));
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 0, "left " + p.getInventory().countItem(Items.EMERALD));
		List<Villager> band = siegeBreakers(level, hall);
		helper.assertTrue(band.size() == 5, "band of " + band.size());
		helper.assertTrue(band.stream().filter(v -> Guards.kind(v) == Guards.Kind.ARCHER).count() == 2
			&& band.stream().filter(v -> v.getOffhandItem().is(Items.CROSSBOW)).count() == 2, "not two crossbows");
		helper.assertTrue(band.stream().filter(v -> Guards.kind(v) == Guards.Kind.KNIGHT).count() == 2, "not two knights");
		helper.assertTrue(band.stream().filter(v -> Guards.kind(v) == Guards.Kind.MEDIC).count() == 1, "not one medic");
		p.getInventory().add(new ItemStack(Items.EMERALD, 24));
		click(menu, DefencePage.SIEGE_BREAKERS, p);
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 24 && siegeBreakers(level, hall).size() == 5,
			"a second click charged or hired more: " + p.getInventory().countItem(Items.EMERALD) + " emeralds, " + siegeBreakers(level, hall).size());
		helper.succeed();
	}

	/**
	 * Hired an hour before dawn with no Rally Banner at all: they still follow the player who hired them, and they stay
	 * until the second dawn from now (the one 25,000 ticks away), not the dawn an hour away.
	 */
	//$ gametest_batch AREA '"qaWpLateHire"'
	@GameTest(template = AREA, batch = "qaWpLateHire")
	public void qaSiegeBreakersHiredBeforeDawnWithoutABannerStayTwoDawnsAndFollowTheHirer(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = client(helper);
		ServerPlayer p = c.player();
		lair(helper, hall, LAIR);
		level.setDayTime(23000);
		p.getInventory().add(new ItemStack(Items.EMERALD, 30));
		ChoiceMenu menu = defencePage(p, level, hall);
		long now = level.getGameTime();
		click(menu, DefencePage.SIEGE_BREAKERS, p);
		List<Villager> band = siegeBreakers(level, hall);
		helper.assertTrue(band.size() == 5, "band of " + band.size() + ", said " + last(c.seen()));
		Escorts.scan(level.getServer());
		helper.assertTrue(band.stream().allMatch(v -> Escorts.leader(v).map(l -> l == p).orElse(false)), "they don't follow the player without a banner");
		for (Villager v : band) {
			long until = ModAttachments.MERCENARY_UNTIL.get(v);
			helper.assertTrue(until == now + 1000 + 24000, "stays " + (until - now) + " ticks, expected 25000");
		}
		helper.succeed();
	}

	/**
	 * Siege-breakers hired while the lair stands: a tick before the second dawn they're all here, at the second dawn they
	 * all leave, and the lair still stands (it isn't their leaving that breaks it).
	 */
	//$ gametest_batch AREA '"qaWpTwoDawns"'
	@GameTest(template = AREA, batch = "qaWpTwoDawns")
	public void qaSiegeBreakersLeaveAtTheSecondDawnWhileTheLairStands(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = client(helper);
		ServerPlayer p = c.player();
		lair(helper, hall, LAIR);
		p.getInventory().add(new ItemStack(Items.EMERALD, 30));
		click(defencePage(p, level, hall), DefencePage.SIEGE_BREAKERS, p);
		List<Villager> band = siegeBreakers(level, hall);
		helper.assertTrue(band.size() == 5, "band of " + band.size());
		long secondDawn = level.getGameTime() + (24000 - level.getDayTime() % 24000) + 24000;
		var data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
		data.setGameTime(secondDawn - 1);
		band.forEach(Mercenaries::tick);
		helper.assertTrue(band.stream().allMatch(Villager::isAlive), "gone before the second dawn");
		data.setGameTime(secondDawn);
		band.forEach(Mercenaries::tick);
		helper.assertTrue(band.stream().noneMatch(Villager::isAlive), band.stream().filter(Villager::isAlive).count() + " still here at the second dawn");
		helper.assertTrue(Lairs.near(level, hall).isPresent(), "the lair fell");
		helper.succeed();
	}

	/**
	 * A player alone (no guards, no siege-breakers, no banner) breaks the lair: the normal 5 days' peace, not 10; no
	 * "warband is broken" mood for the villagers; no war-party chronicle line.
	 */
	//$ gametest_batch AREA '"qaWpAlone"'
	@GameTest(template = AREA, batch = "qaWpAlone")
	public void qaALairBrokenWithoutAWarPartyGivesTheUsualFiveDays(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Lairs.Lair lair = lair(helper, hall, LAIR);
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(Lairs.resting(level, hall) == 5, "rest " + Lairs.resting(level, hall) + " days, not 5");
		helper.assertTrue(WarParties.mood(level, baker) == null, "cheered without a war party: " + WarParties.mood(level, baker));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity.chronicle().stream().noneMatch(e -> e.text().getString().contains("war party")), "a war party line without one");
		helper.succeed();
	}

	private static float arrowKill(ServerLevel level, Villager guard, Mob foe) {
		Arrow arrow = new Arrow(level, guard, new ItemStack(Items.ARROW), null);
		int before = guard.getVillagerXp();
		foe.hurt(level.damageSources().arrow(arrow, guard), 1000f);
		return guard.getVillagerXp() - before;
	}

	/**
	 * The same guard on a raised banner, killing one of a lair's band with a crossbow bolt: far from the lair (70 blocks)
	 * it earns the usual experience; at the lair it earns exactly twice that.
	 */
	//$ gametest_batch AREA '"qaWpDouble"'
	@GameTest(template = AREA, batch = "qaWpDouble")
	public void qaAGuardEarnsExactlyDoubleExperienceForAKillAtTheLair(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Villager guard = guard(helper, new BlockPos(11, 2, 12));
		ServerPlayer player = client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		banner.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.of(guard.getUUID()), true));
		player.getInventory().add(banner);
		Escorts.scan(level.getServer());
		helper.assertTrue(Escorts.isEscorting(guard), "setup: the guard isn't on the march");
		// The lair 70 blocks off: a kill here isn't at it.
		BlockPos far = helper.absolutePos(LAIR).offset(70, 0, 0);
		Lairs.putForTest(level, new Lairs.Lair(far, hall, UUID.randomUUID(), Chronicle.day(level), Threats.BANDITS, 7, Chronicle.day(level), 0, 0, 3));
		float usual = arrowKill(level, guard, bandit(helper, new BlockPos(13, 2, 12)));
		Lairs.forget(level);
		lair(helper, hall, LAIR);
		float atLair = arrowKill(level, guard, bandit(helper, new BlockPos(14, 2, 13)));
		helper.assertTrue(usual > 0, "no experience for the usual kill");
		helper.assertTrue(atLair == 2 * usual, "at the lair " + atLair + ", away " + usual + ": not double");
		helper.succeed();
	}

	/**
	 * Every sentence 32.13 adds, read in English: each line exists, every %s is filled, and the counts read right where
	 * the grammar doesn't depend on a singular "rest".
	 */
	//$ gametest_batch AREA '"qaWpWords"'
	@GameTest(template = AREA, batch = "qaWpWords")
	public void qaEveryWarPartySentenceReadsRight(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, Component.translatable("screen.aliveworkplace.defence.muster"), "Muster a war party");
		check(wrong, Component.translatable("message.aliveworkplace.war_party.no_guards"), "No guards to spare: the walls need everyone");
		check(wrong, Component.translatable("message.aliveworkplace.war_party.mustered", 9, 3), "9 guards gather by the hall under your Rally Banner; 3 keep the walls");
		check(wrong, Component.translatable("message.aliveworkplace.war_party.broken", "Oakvale", WarParties.REST_DAYS), "The warband is broken! Oakvale will have peace for 10 days");
		check(wrong, Component.translatable("message.aliveworkplace.siege_breakers.hired", WarParties.BAND), "5 Siege-breakers join you: they stay until the lair falls or two dawns pass");
		check(wrong, Component.translatable("message.aliveworkplace.siege_breakers.cant_afford", Component.translatable("message.aliveworkplace.money.emeralds", 30)), "The Siege-breakers cost 30 emeralds");
		check(wrong, Component.translatable("screen.aliveworkplace.defence.siege_breakers_who"), "Five hired swords: two crossbows, two knights and a medic");
		check(wrong, Component.translatable("screen.aliveworkplace.defence.siege_breakers_stay"), "They stay until the lair falls or two dawns pass");
		check(wrong, Component.translatable("screen.aliveworkplace.defence.siege_breakers_here", 5), "5 siege-breakers are with you");
		check(wrong, Component.translatable("screen.aliveworkplace.defence.siege_breakers_here.one", 1), "1 siege-breaker is with you");
		check(wrong, Component.translatable("chronicle.aliveworkplace.siege_breakers", "Jesse"), "Jesse hired the Siege-breakers to march on the lair.");
		check(wrong, Component.translatable("chronicle.aliveworkplace.war_party.broken", "Jesse", "Chief Harl Ashgrave", "Ada, Bram"),
			"Jesse led the war party that broke the warband of Chief Harl Ashgrave. Those who killed there: Ada, Bram.");
		check(wrong, Component.translatable("chronicle.aliveworkplace.war_party.broken_none", "Jesse", "Chief Harl Ashgrave"),
			"Jesse led the war party that broke the warband of Chief Harl Ashgrave.");
		check(wrong, Component.translatable("mood.aliveworkplace.reason.warband_broken"), "the warband is broken");
		helper.assertTrue(wrong.isEmpty(), "reads wrong: " + wrong);
		helper.succeed();
	}

	static void check(List<String> wrong, Component c, String expected) {
		String s = c.getString();
		if (!s.equals(expected)) {
			wrong.add("'" + s + "' (expected '" + expected + "')");
		}
	}
}
