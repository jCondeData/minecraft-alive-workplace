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
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMoments;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Marching on the lair (ROADMAP 32.13): the muster enlists three in four guards (at most 12) on the player's Rally
 * Banner and raises it, says to craft one without, and does nothing with Milestone 32 closed; the Siege-breakers are two
 * crossbows, two knights and a medic for 30 emeralds, stay past a dawn while the lair stands (through a save and load)
 * and leave at the dawn after it falls; a lair broken with a war party gives 10 days' rest, double XP for kills there, a
 * chronicle line naming the player and the guards who killed, and "the warband is broken" mood; the band turns out six at
 * a time to meet a war party.
 */
public class WarPartyGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos POST = new BlockPos(4, 2, 4);
	private static final BlockPos LAIR = new BlockPos(13, 2, 13);

	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
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

	private static Villager guard(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		return villager;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			Escorts.scan(helper.getLevel().getServer());
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		return player;
	}

	/** A bandit lair at {@link #LAIR} with its captain standing there (still, mortal), strength 7. */
	private static Lairs.Lair lair(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(LAIR);
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

	/** One of the lair's band, still, by the lair. */
	private static Vindicator bandit(GameTestHelper helper, BlockPos at) {
		Vindicator bandit = helper.spawn(EntityType.VINDICATOR, at);
		bandit.setNoAi(true);
		bandit.addTag(Lairs.TAG);
		return bandit;
	}

	private static String key(Component said) {
		return said.getContents() instanceof TranslatableContents t ? t.getKey() : said.getString();
	}

	/**
	 * Four guards and a player with a Rally Banner: "Muster a war party" enlists three on it and raises it (the fourth
	 * keeps the walls), and the message says so. Twenty guards: twelve march. Without a banner the player is told to craft
	 * one; with Milestone 32 closed nothing happens.
	 */
	//$ gametest_batch AREA '"warPartyMuster"'
	@GameTest(template = AREA, batch = "warPartyMuster")
	public void theMusterEnlistsThreeInFourGuardsOnTheBannerAndRaisesIt(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		List<Villager> guards = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			guards.add(guard(helper, new BlockPos(3 + i, 2, 6)));
		}
		WarParties.Muster none = WarParties.muster(level, hall, player);
		helper.assertTrue(none == WarParties.Muster.NO_BANNER, "mustered without a banner: " + none);
		helper.assertTrue(key(WarParties.said(level, hall, none, 0)).equals("message.aliveworkplace.war_party.no_banner"), "no craft-one message");
		player.getInventory().add(new ItemStack(ModItems.RALLY_BANNER));
		Expansions.openForTests = false;
		helper.assertTrue(WarParties.muster(level, hall, player) == WarParties.Muster.CLOSED, "mustered with Milestone 32 closed");
		helper.assertTrue(RallyBannerItem.rally(WarParties.banner(player)).guards().isEmpty(), "enlisted with Milestone 32 closed");
		Expansions.openForTests = true;
		WarParties.Muster result = WarParties.muster(level, hall, player);
		helper.assertTrue(result == WarParties.Muster.MUSTERED, "not mustered: " + result);
		RallyBannerItem.Rally rally = RallyBannerItem.rally(WarParties.banner(player));
		helper.assertTrue(rally.raised(), "the banner isn't raised");
		helper.assertTrue(rally.guards().size() == 3, "enlisted " + rally.guards().size() + " of 4");
		helper.assertTrue(guards.stream().filter(g -> rally.guards().contains(g.getUUID())).count() == 3, "enlisted someone else");
		long following = guards.stream().filter(Escorts::isEscorting).count();
		helper.assertTrue(following == 3, following + " follow the banner");
		Component said = WarParties.said(level, hall, result, rally.guards().size());
		helper.assertTrue(said.getString().contains("3") && said.getString().contains("1"), "said " + said.getString());
		for (int i = 0; i < 16; i++) {
			guards.add(guard(helper, new BlockPos(3 + i % 8, 2, 8 + i / 8)));
		}
		WarParties.muster(level, hall, player);
		helper.assertTrue(RallyBannerItem.rally(WarParties.banner(player)).guards().size() == RallyBannerItem.MAX_GUARDS, "not 12 of 20");
		helper.succeed();
	}

	/**
	 * Hiring the Siege-breakers for 30 emeralds: two crossbows, two knights and a medic, following the player; they stay
	 * past the next dawn (their stay is the second dawn, kept through a save and load) while the lair stands, and when it
	 * falls they leave at the next dawn.
	 */
	//$ gametest_batch AREA '"warPartySiegeBreakers"'
	@GameTest(template = AREA, batch = "warPartySiegeBreakers")
	public void siegeBreakersStayPastADawnAndLeaveAtTheDawnAfterTheLairFalls(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Lairs.Lair lair = lair(helper, hall);
		helper.assertTrue(WarParties.price(level, hall) == WarParties.PRICE_EMERALDS, "price " + WarParties.price(level, hall));
		Component broke = WarParties.hire(level, hall, player);
		helper.assertTrue(key(broke).equals("message.aliveworkplace.siege_breakers.cant_afford"), "hired for nothing: " + broke.getString());
		player.getInventory().add(new ItemStack(Items.EMERALD, WarParties.PRICE_EMERALDS));
		player.getInventory().add(new ItemStack(ModItems.RALLY_BANNER));
		Component said = WarParties.hire(level, hall, player);
		helper.assertTrue(key(said).equals("message.aliveworkplace.siege_breakers.hired"), "not hired: " + said.getString());
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "not paid");
		List<Villager> band = Mercenaries.near(level, hall).stream().filter(WarParties::isSiegeBreaker).toList();
		helper.assertTrue(band.size() == WarParties.BAND, "band of " + band.size());
		helper.assertTrue(band.stream().filter(v -> Guards.kind(v) == Guards.Kind.ARCHER).count() == 2, "not two crossbows");
		helper.assertTrue(band.stream().filter(v -> Guards.kind(v) == Guards.Kind.KNIGHT).count() == 2, "not two knights");
		helper.assertTrue(band.stream().filter(v -> Guards.kind(v) == Guards.Kind.MEDIC).count() == 1, "no medic");
		helper.assertTrue(band.stream().allMatch(v -> Escorts.leader(v).map(p -> p == player).orElse(false)), "they don't follow the player");
		helper.assertTrue(RallyBannerItem.rally(WarParties.banner(player)).guards().size() == WarParties.BAND, "not on the banner");
		long now = level.getGameTime();
		long toDawn = 24000 - level.getDayTime() % 24000;
		Villager merc = band.get(0);
		long until = ModAttachments.MERCENARY_UNTIL.get(merc);
		helper.assertTrue(until == now + toDawn + 24000, "stay until " + (until - now) + " ticks, dawn in " + toDawn);
		// A save and load of one of them keeps the stay and who they follow.
		CompoundTag saved = merc.saveWithoutId(new CompoundTag());
		Villager loaded = EntityType.VILLAGER.create(level);
		loaded.load(saved);
		helper.assertTrue(Long.valueOf(until).equals(ModAttachments.MERCENARY_UNTIL.get(loaded)) && WarParties.isSiegeBreaker(loaded)
			&& WarParties.hirer(loaded).map(player.getUUID()::equals).orElse(false), "lost on a load");
		// Past the next dawn, the lair standing: still here.
		Mercenaries.tick(merc);
		helper.assertTrue(merc.isAlive(), "gone before the second dawn");
		// The lair falls: they leave at the next dawn.
		Villager killer = guard(helper, new BlockPos(12, 2, 12));
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().mobAttack(killer), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		for (Villager v : band) {
			long left = ModAttachments.MERCENARY_UNTIL.get(v);
			helper.assertTrue(left == now + toDawn, v.getUUID() + " stays until " + (left - now) + ", dawn in " + toDawn);
		}
		helper.succeed();
	}

	/**
	 * Guards on a raised banner at a lair: a guard who kills one of the band there earns the kill's experience again; the
	 * kills survive a save and load; the captain falling gives 10 days' rest, a chronicle line naming the player and both
	 * guards who killed, and the village "the warband is broken" for two days (through a save and load). With Milestone 32
	 * closed a kill at a lair pays nothing extra.
	 */
	//$ gametest_batch AREA '"warPartyBroken"'
	@GameTest(template = AREA, batch = "warPartyBroken")
	public void aLairBrokenWithAWarPartyGivesTenDaysDoubleXpAndTheirNames(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Lairs.Lair lair = lair(helper, hall);
		Villager first = guard(helper, new BlockPos(11, 2, 12));
		Villager second = guard(helper, new BlockPos(12, 2, 11));
		Villager stay = guard(helper, new BlockPos(3, 2, 3));
		Villager home = guard(helper, new BlockPos(3, 2, 6));
		// Each with a name of their own, as a village names everyone (off in tests by default); the chronicle names them.
		first.setCustomName(io.github.jcondedata.aliveworkplace.people.Names.name(0));
		second.setCustomName(io.github.jcondedata.aliveworkplace.people.Names.name(1));
		player.getInventory().add(new ItemStack(ModItems.RALLY_BANNER));
		helper.assertTrue(WarParties.muster(level, hall, player) == WarParties.Muster.MUSTERED, "not mustered");
		// The muster takes the nearest three of four; make sure the two by the lair are on it.
		ItemStack banner = WarParties.banner(player);
		banner.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.RALLY, new RallyBannerItem.Rally(List.of(first.getUUID(), second.getUUID()), true));
		Escorts.scan(level.getServer());
		helper.assertTrue(Escorts.isEscorting(first) && Escorts.isEscorting(second) && !Escorts.isEscorting(home), "the war party isn't marching");
		// Closed: no extra experience.
		Expansions.openForTests = false;
		int before = first.getVillagerXp();
		bandit(helper, new BlockPos(14, 2, 12)).hurt(level.damageSources().mobAttack(first), 1000f);
		helper.assertTrue(first.getVillagerXp() == before, "paid with Milestone 32 closed");
		Expansions.openForTests = true;
		before = first.getVillagerXp();
		bandit(helper, new BlockPos(14, 2, 14)).hurt(level.damageSources().mobAttack(first), 1000f);
		helper.assertTrue(first.getVillagerXp() > before, "no extra experience at the lair");
		bandit(helper, new BlockPos(12, 2, 14)).hurt(level.damageSources().mobAttack(second), 1000f);
		WarParties.Data.get(level).roundTrip(level.registryAccess());
		helper.assertTrue(WarParties.killers(level, hall).size() == 2, "killers " + WarParties.killers(level, hall));
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().mobAttack(second), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(Lairs.resting(level, hall) == WarParties.REST_DAYS, "rest " + Lairs.resting(level, hall));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		List<Chronicle.Entry> chronicle = entity.chronicle();
		String line = chronicle.stream().map(e -> e.text().getString()).filter(s -> s.contains("war party")).findFirst().orElse("");
		helper.assertTrue(line.contains(player.getDisplayName().getString()) && line.contains(first.getDisplayName().getString())
			&& line.contains(second.getDisplayName().getString()), "the chronicle says: " + line);
		WarParties.Data.get(level).roundTrip(level.registryAccess());
		var mood = WarParties.mood(level, stay);
		helper.assertTrue(mood != null && mood.points() == WarParties.MOOD
			&& mood.reason().getString().equals("the warband is broken"), "mood " + mood);
		helper.succeed();
	}

	/** A war party at a lair of 7: the band turns out six at a time and goes for them. */
	//$ gametest_batch AREA '"warPartyTurnOut"'
	@GameTest(template = AREA, batch = "warPartyTurnOut")
	public void theBandTurnsOutSixAtATime(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = lair(helper, hall);
		Villager guard = guard(helper, new BlockPos(10, 2, 10));
		int out = Lairs.turnOut(level, lair, WarParties.WAVE, List.of(guard));
		List<Mob> band = Lairs.band(level, lair).stream().filter(m -> !m.getUUID().equals(lair.captain())).toList();
		helper.assertTrue(out == WarParties.WAVE && band.size() == WarParties.WAVE, "turned out " + out + ", band " + band.size());
		helper.assertTrue(band.stream().allMatch(m -> m.getTarget() == guard), "not after the war party");
		helper.assertTrue(Lairs.turnOut(level, lair, WarParties.WAVE, List.of(guard)) == 0, "more than six at once");
		helper.succeed();
	}

	private static boolean campBroken(ServerPlayer player) {
		var advancement = player.getServer().getAdvancements().get(AliveWorkplace.id(VillageMoments.PREFIX + VillageMoments.CAMP_BROKEN));
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	private static ServerPlayer leading(GameTestHelper helper, BlockPos at, Villager guard) {
		ServerPlayer player = player(helper);
		player.moveTo(helper.absolutePos(at).getCenter());
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		banner.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.RALLY, new RallyBannerItem.Rally(List.of(guard.getUUID()), true));
		player.getInventory().add(banner);
		return player;
	}

	/**
	 * 36.3: two players, each leading a guard of the war party at a lair; the first strikes the captain down. "Take the
	 * fight to them" completes for both (aliveworkplace:village/camp_broken), and breaking the lair is the only way.
	 */
	//$ gametest_batch AREA '"warPartyCampBroken"'
	@GameTest(template = AREA, batch = "warPartyCampBroken")
	public void aLairBrokenWithTheWarPartyCompletesTheQuestForEveryPlayerInIt(GameTestHelper helper) {
		BlockPos hall = village(helper);
		Expansions.openForTests = true;
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = lair(helper, hall);
		Villager first = guard(helper, new BlockPos(11, 2, 12));
		Villager second = guard(helper, new BlockPos(12, 2, 11));
		ServerPlayer striker = leading(helper, new BlockPos(11, 2, 11), first);
		ServerPlayer friend = leading(helper, new BlockPos(10, 2, 12), second);
		Escorts.scan(level.getServer());
		helper.assertTrue(Escorts.isEscorting(first) && Escorts.isEscorting(second), "the war party isn't marching");
		helper.assertFalse(campBroken(striker) || campBroken(friend), "granted before the camp broke");
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(striker), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(campBroken(striker), "the player who struck the last blow didn't get camp_broken");
		helper.assertTrue(campBroken(friend), "the second player in the war party didn't get camp_broken");
		helper.succeed();
	}

	/** 36.3: a lair broken by a player alone, with no war party there and no guard kills, completes nothing. */
	//$ gametest_batch AREA '"warPartyCampAlone"'
	@GameTest(template = AREA, batch = "warPartyCampAlone")
	public void aLairBrokenWithNoWarPartyGrantsNothing(GameTestHelper helper) {
		BlockPos hall = village(helper);
		Expansions.openForTests = true;
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = lair(helper, hall);
		ServerPlayer player = player(helper);
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertFalse(campBroken(player), "camp_broken granted with no war party");
		helper.succeed();
	}

	/**
	 * 36.3 QA (lane D, 2026-10-26): a guard of the war party strikes the captain down, not a player: the player leading
	 * that guard is in the war party and still completes "Take the fight to them"; a player standing by the lair who
	 * leads no one, and isn't the striker, gets nothing.
	 */
	//$ gametest_batch AREA '"qaWarPartyGuardBlow"'
	@GameTest(template = AREA, batch = "qaWarPartyGuardBlow")
	public void aGuardsLastBlowCompletesItForThePartyButNotForABystander(GameTestHelper helper) {
		BlockPos hall = village(helper);
		Expansions.openForTests = true;
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = lair(helper, hall);
		Villager first = guard(helper, new BlockPos(11, 2, 12));
		ServerPlayer leader = leading(helper, new BlockPos(11, 2, 11), first);
		ServerPlayer bystander = player(helper);
		bystander.moveTo(helper.absolutePos(new BlockPos(14, 2, 11)).getCenter());
		Escorts.scan(level.getServer());
		helper.assertTrue(Escorts.isEscorting(first), "the war party isn't marching");
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().mobAttack(first), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands after the guard's blow");
		helper.assertTrue(campBroken(leader), "the player leading the guard who struck the last blow didn't get camp_broken");
		helper.assertFalse(campBroken(bystander), "a bystander by the lair, in no war party, got camp_broken");
		helper.succeed();
	}

	/**
	 * 36.3 QA: the player who strikes the last blow gets it, with the war party there, even when he leads none of its
	 * guards himself (another player raised the banner).
	 */
	//$ gametest_batch AREA '"qaWarPartyHelperBlow"'
	@GameTest(template = AREA, batch = "qaWarPartyHelperBlow")
	public void aPlayerStrikingTheLastBlowBesideAnotherPlayersWarPartyGetsIt(GameTestHelper helper) {
		BlockPos hall = village(helper);
		Expansions.openForTests = true;
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = lair(helper, hall);
		Villager first = guard(helper, new BlockPos(11, 2, 12));
		ServerPlayer leader = leading(helper, new BlockPos(11, 2, 11), first);
		ServerPlayer striker = player(helper);
		striker.moveTo(helper.absolutePos(new BlockPos(12, 2, 12)).getCenter());
		Escorts.scan(level.getServer());
		helper.assertTrue(Escorts.isEscorting(first), "the war party isn't marching");
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(striker), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(campBroken(striker), "the player who struck the last blow didn't get camp_broken");
		helper.assertTrue(campBroken(leader), "the player leading the war party didn't get camp_broken");
		helper.succeed();
	}
}
