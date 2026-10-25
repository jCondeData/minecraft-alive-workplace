package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.school.Schools;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA (adversarial) tests for personal requests, ROADMAP 31.9 and 31.10, written from the spec. They hit what
 * {@code PersonalRequestGameTests} and {@code PersonalErrandGameTests} leave out: who is asked (the friend with the most
 * hearts within 24 blocks, never a 2-heart one, a spectator, a sleeping or baby villager); the village asks once a
 * morning, and an unanswered offer lapses with the day; joining at 2 vs 3 hearts with the sentences a player reads; the
 * reward (+25 standing and the friendship for every helper, here or away; nothing for a bystander) after a Bottle o'
 * Enchanting given as a Gift; near misses on a hand-in (wrong item, too few) and a save mid-way; the giver unloaded and
 * back, or dead; a tamed wolf just inside and just outside 10 blocks; the wrong child, the wrong building, a building
 * outside the village and the wrong grave; two players kept apart; and singular and plural in what a villager wants.
 */
public class QaPersonalRequestsGameTests implements FabricGameTest {
	static final String AREA = PersonalRequestGameTests.AREA;
	static final BlockPos HALL = PersonalRequestGameTests.HALL;

	// --- Helpers --------------------------------------------------------------------------------------------------

	/** The hall's request state (package-private in the mod): set today's roll as made, with someone to ask or not. */
	private static void roll(ServerLevel level, BlockPos hall, long day, boolean pending) {
		try {
			Object e = entry(level, hall);
			Field d = e.getClass().getDeclaredField("requestDay");
			d.setAccessible(true);
			d.setLong(e, day);
			Field p = e.getClass().getDeclaredField("requestPending");
			p.setAccessible(true);
			p.setBoolean(e, pending);
		} catch (ReflectiveOperationException x) {
			throw new IllegalStateException(x);
		}
	}

	private static boolean pending(ServerLevel level, BlockPos hall) {
		try {
			Object e = entry(level, hall);
			Field p = e.getClass().getDeclaredField("requestPending");
			p.setAccessible(true);
			return p.getBoolean(e);
		} catch (ReflectiveOperationException x) {
			throw new IllegalStateException(x);
		}
	}

	private static Object entry(ServerLevel level, BlockPos hall) throws ReflectiveOperationException {
		Method m = Stories.Data.class.getDeclaredMethod("entry", BlockPos.class);
		m.setAccessible(true);
		return m.invoke(Stories.Data.get(level), hall);
	}

	/** {@code player} right-clicks {@code villager} holding a Gift with a Bottle o' Enchanting in it (the player's own way to give XP). */
	private static void giveBottle(ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(Items.EXPERIENCE_BOTTLE), ""));
		UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** Lines with a raw translation key or an unfilled argument. */
	private static List<String> broken(List<String> lines) {
		return lines.stream().filter(s -> s.contains("%") || s.matches("(?s).*\\b(message|request|quest|screen|chronicle)\\.aliveworkplace\\..*")).toList();
	}

	private static ChoiceMenu journal(GameTestHelper helper, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		return ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.PERSONAL));
	}

	// --- Who is asked ---------------------------------------------------------------------------------------------

	/**
	 * Asked: the friend with the most hearts within 24 blocks, even when a 2-heart player stands closer; nobody at 24.5
	 * blocks; never a spectator; never by a villager asleep or a child.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRequestWho"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestWho")
	public void theFriendWithTheMostHeartsWithin24BlocksIsAsked(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
			long today = Chronicle.day(level);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer near = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 299);
			ServerPlayer friend = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Vec3 at = dara.position();
			friend.moveTo(at.x + 20, at.y, at.z, 0, 0);
			PersonalRequests.Offer o = PersonalRequests.pick(level, hall, entity, RandomSource.create(1), today);
			helper.assertTrue(o != null && o.player.equals(friend.getUUID()), "the 3-heart friend 20 blocks off wasn't the one asked: "
				+ (o == null ? "nobody" : o.player.equals(near.getUUID()) ? "the 2-heart player" : o.player));
			PersonalRequests.forget();
			friend.moveTo(at.x + 23.5, at.y, at.z, 0, 0);
			o = PersonalRequests.pick(level, hall, entity, RandomSource.create(2), today);
			helper.assertTrue(o != null && o.player.equals(friend.getUUID()), "not asked at 23.5 blocks");
			PersonalRequests.forget();
			friend.moveTo(at.x + 24.5, at.y, at.z, 0, 0);
			o = PersonalRequests.pick(level, hall, entity, RandomSource.create(3), today);
			helper.assertTrue(o == null, "asked " + (o == null ? "" : o.player.equals(near.getUUID()) ? "the 2-heart player" : "a friend 24.5 blocks away"));
			// A spectator isn't asked (a real player: the GameTest mock player never reports spectator mode).
			var watcher = QaStageSixGameTests.client(helper, GameType.SPECTATOR, QaStageSixGameTests.name("Spec"));
			Friendship.add(dara, watcher.player(), 300);
			watcher.player().moveTo(at.x + 5, at.y, at.z, 0, 0);
			helper.assertTrue(watcher.player().isSpectator() && PersonalRequests.pick(level, hall, entity, RandomSource.create(4), today) == null, "a spectator was asked");
			friend.moveTo(at.x + 5, at.y, at.z, 0, 0);
			// Of two friends near, the one with more hearts.
			ServerPlayer best = PersonalRequestGameTests.friend(helper, new BlockPos(6, 2, 6), dara, 450);
			best.moveTo(at.x + 10, at.y, at.z, 0, 0);
			for (int seed = 0; seed < 5; seed++) {
				PersonalRequests.forget();
				o = PersonalRequests.pick(level, hall, entity, RandomSource.create(seed), today);
				helper.assertTrue(o != null && o.player.equals(best.getUUID()), "seed " + seed + ": the 4-heart friend wasn't asked");
			}
			PersonalRequests.forget();
			// Asleep, she asks nobody.
			dara.startSleeping(dara.blockPosition());
			boolean asleep = dara.isSleeping();
			o = PersonalRequests.pick(level, hall, entity, RandomSource.create(5), today);
			dara.stopSleeping();
			helper.assertTrue(asleep && o == null, "a sleeping villager asked");
			// A child with three hearts asks nobody.
			dara.discard();
			Villager pip = HeartEventGameTests.villager(helper, new BlockPos(7, 2, 4), "Pip");
			pip.setAge(-24000);
			Friendship.add(pip, best, 300);
			helper.assertTrue(PersonalRequests.pick(level, hall, entity, RandomSource.create(6), today) == null, "a child asked for help");
			helper.succeed();
		});
	}

	/**
	 * The village asks once a morning: on a hit, exactly one villager asks, and nobody else that day (even after the
	 * offer is gone); a morning without a hit, nobody. An offer from yesterday nobody answered lapses in the round.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRequestDaily"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestDaily")
	public void oneVillagerAsksPerMorningAndOldOffersLapse(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
			long today = Chronicle.day(level);
			Villager ada = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Ada", "minecraft:farmer", 1);
			Villager bo = PersonalRequestGameTests.worker(helper, new BlockPos(8, 2, 4), "Bo", "minecraft:farmer", 1);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(6, 2, 6), ada, 300);
			Friendship.add(bo, me, 300);
			// A morning without a hit: nobody asks.
			roll(level, hall, today, false);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(PersonalRequests.offer(ada) == null && PersonalRequests.offer(bo) == null, "someone asked on a morning without a hit");
			// A hit: exactly one asks.
			roll(level, hall, today, true);
			VillageQuests.tick(level, hall, entity);
			int asked = (PersonalRequests.offer(ada) != null ? 1 : 0) + (PersonalRequests.offer(bo) != null ? 1 : 0);
			helper.assertTrue(asked == 1 && !pending(level, hall), asked + " villagers asked on one hit, pending " + pending(level, hall));
			// The offer turned down: still nobody else today.
			PersonalRequests.forget();
			for (int i = 0; i < 3; i++) {
				VillageQuests.tick(level, hall, entity);
			}
			helper.assertTrue(PersonalRequests.offer(ada) == null && PersonalRequests.offer(bo) == null, "a second villager asked the same day");
			// Yesterday's offer, never answered, lapses; today's stands.
			PersonalRequests.propose(level, hall, ada, me, PersonalRequestGameTests.resolve(helper, "level_up", ada, me), today - 1);
			PersonalRequests.propose(level, hall, bo, me, PersonalRequestGameTests.resolve(helper, "level_up", bo, me), today);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(PersonalRequests.offer(ada) == null, "yesterday's offer stood");
			helper.assertTrue(PersonalRequests.offer(bo) != null, "today's offer lapsed");
			helper.assertTrue(Friendship.points(ada, me.getUUID()) == 300 && PersonalRequests.of(ada) == null, "a lapsed offer cost something or opened");
			helper.succeed();
		});
	}

	// --- Helping --------------------------------------------------------------------------------------------------

	/** Joining an open request: refused at 299 points with the reason, accepted at 300, "already" after; every line filled in. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestJoin"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestJoin")
	public void joiningNeedsThreeHeartsAndEveryLineReads(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			var ana = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Ana"));
			var ben = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Ben"));
			String anaName = ana.player().getGameProfile().getName();
			Friendship.add(dara, ana.player(), 300);
			Friendship.add(dara, ben.player(), 299);
			Quest quest = PersonalRequestGameTests.accepted(helper, "level_up", dara, ana.player());
			String village = VillageHalls.name(level, hall).getString();
			List<String> anas = texts(ana.seen());
			helper.assertTrue(anas.contains("You're helping with \"Next level before the festival\". It's in the Personal tab of the quest journal at the hall of " + village + "."),
				"Ana read: " + anas);
			helper.assertTrue(anas.contains("Dara: Thank you, " + anaName + ". I knew I could ask you."), "Ana read: " + anas);
			// 299 points: refused, with the reason.
			PersonalRequestGameTests.command(ben.player(), "workplace quest accept " + quest.id);
			helper.assertTrue(!PersonalRequests.helps(quest, ben.player().getUUID()), "joined at 299 points");
			helper.assertTrue(texts(ben.seen()).contains("You need 3 hearts with Dara to help with this."), "Ben read: " + texts(ben.seen()));
			// 300: in.
			Friendship.add(dara, ben.player(), 1);
			PersonalRequestGameTests.command(ben.player(), "workplace quest accept " + quest.id);
			helper.assertTrue(PersonalRequests.helps(quest, ben.player().getUUID()), "not joined at 300 points");
			helper.assertTrue(texts(ben.seen()).contains("You're helping Dara too: Next level before the festival."), "Ben read: " + texts(ben.seen()));
			PersonalRequestGameTests.command(ben.player(), "workplace quest accept " + quest.id);
			helper.assertTrue(texts(ben.seen()).contains("You're already helping Dara.") && quest.helpers.size() == 2, "Ben read: " + texts(ben.seen()));
			List<String> all = new ArrayList<>(texts(ana.seen()));
			all.addAll(texts(ben.seen()));
			helper.assertTrue(broken(all).isEmpty(), "lines with a raw key or an unfilled argument: " + broken(all));
			helper.succeed();
		});
	}

	/**
	 * A Bottle o' Enchanting given as a Gift takes her to the next level: every helper gets +25 standing and +150
	 * friendship, here or away (left the game), the ones here 6 emeralds and the "You helped" line; a bystander friend
	 * gets nothing.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRequestReward"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestReward")
	public void everyHelperGetsStandingAndFriendshipEvenAway(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
			boolean reputation = Reputation.ENABLED;
			Reputation.ENABLED = true;
			Leftovers.after(helper, () -> Reputation.ENABLED = reputation);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			var ana = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Ana"));
			var ben = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Ben"));
			var cal = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Cal"));
			var dot = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Dot"));
			for (var c : List.of(ana, ben, cal, dot)) {
				Friendship.add(dara, c.player(), 300);
				c.player().moveTo(dara.getX() + 1, dara.getY(), dara.getZ() + 1, 0, 0);
			}
			Quest quest = PersonalRequestGameTests.accepted(helper, "level_up", dara, ana.player());
			PersonalRequestGameTests.command(ben.player(), "workplace quest accept " + quest.id);
			PersonalRequestGameTests.command(cal.player(), "workplace quest accept " + quest.id);
			helper.assertTrue(quest.helpers.size() == 3, "helpers: " + quest.helpers.size());
			UUID calId = cal.player().getUUID();
			level.getServer().getPlayerList().remove(cal.player()); // Cal leaves the game before it's done
			// Ana gives the bottle; then the hall's round.
			giveBottle(ana.player(), dara);
			helper.assertTrue(dara.getVillagerData().getLevel() == 2, "the gifted bottle didn't level her: " + dara.getVillagerData().getLevel());
			Map<UUID, Integer> friendship = new LinkedHashMap<>();
			Map<UUID, Integer> standing = new LinkedHashMap<>();
			List<UUID> ids = List.of(ana.player().getUUID(), ben.player().getUUID(), calId, dot.player().getUUID());
			for (UUID id : ids) {
				friendship.put(id, Friendship.points(dara, id));
				standing.put(id, Reputation.points(level, hall, id));
			}
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(PersonalRequests.of(dara) == null, "still open after the level");
			List<String> names = List.of("Ana", "Ben", "Cal (away)", "Dot (bystander)");
			for (int i = 0; i < ids.size(); i++) {
				UUID id = ids.get(i);
				int df = Friendship.points(dara, id) - friendship.get(id);
				int ds = Reputation.points(level, hall, id) - standing.get(id);
				int wantF = i < 3 ? Friendship.Favour.REQUEST.points : 0;
				int wantS = i < 3 ? Reputation.PERSONAL : 0;
				helper.assertTrue(df == wantF && ds == wantS, names.get(i) + ": friendship +" + df + " (want +" + wantF + "), standing +" + ds + " (want +" + wantS + ")");
			}
			helper.assertTrue(Friendship.Favour.REQUEST.points == 150 && Reputation.PERSONAL == 25, "the spec's numbers: " + Friendship.Favour.REQUEST.points + ", " + Reputation.PERSONAL);
			helper.assertTrue(ana.player().getInventory().countItem(Items.EMERALD) == 6 && ben.player().getInventory().countItem(Items.EMERALD) == 6
				&& dot.player().getInventory().countItem(Items.EMERALD) == 0, "emeralds");
			helper.assertTrue(texts(ben.seen()).contains("You helped Dara: Next level before the festival. You earned 6 emeralds and their friendship."), "Ben read: " + texts(ben.seen()));
			helper.assertTrue(texts(ben.seen()).stream().anyMatch(s -> s.startsWith("+25 standing in ")), "no standing line for Ben: " + texts(ben.seen()));
			helper.assertTrue(texts(dot.seen()).stream().noneMatch(s -> s.startsWith("You helped")), "the bystander was thanked");
			helper.assertTrue(broken(texts(ben.seen())).isEmpty(), "broken lines: " + broken(texts(ben.seen())));
			helper.succeed();
		});
	}

	/** Hand-in near misses: the wrong stew, raw mutton and too few count for nothing; partial progress survives a save; the rest finishes it, and extras stay with the player. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestHandIn"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestHandIn")
	public void handInNearMissesAndASaveMidway(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dee = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Dee");
			dee.setVillagerData(dee.getVillagerData().setType(VillagerType.DESERT));
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dee, 300);
			Quest stew = PersonalRequestGameTests.accepted(helper, "taste_of_home", dee, me);
			me.getInventory().add(new ItemStack(Items.MUSHROOM_STEW));
			me.getInventory().add(new ItemStack(Items.BEETROOT_SOUP));
			journal(helper, me).press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(dee) == stew && stew.progress[0] == 0 && me.getInventory().countItem(Items.MUSHROOM_STEW) == 1
				&& me.getInventory().countItem(Items.BEETROOT_SOUP) == 1, "the wrong stew counted or was taken: " + stew.progress[0]);
			me.getInventory().add(new ItemStack(Items.RABBIT_STEW));
			journal(helper, me).press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(dee) == null && me.getInventory().countItem(Items.EMERALD) == 4 && me.getInventory().countItem(Items.MUSHROOM_STEW) == 1,
				"the rabbit stew didn't finish it");
			// A savanna villager wants 4 cooked mutton: raw doesn't count, 3 isn't enough, and the 3 survive a save.
			me.getInventory().clearContent();
			Villager sam = HeartEventGameTests.villager(helper, new BlockPos(8, 2, 4), "Sam");
			sam.setVillagerData(sam.getVillagerData().setType(VillagerType.SAVANNA));
			Friendship.add(sam, me, 300);
			Quest mutton = PersonalRequestGameTests.accepted(helper, "taste_of_home", sam, me);
			me.getInventory().add(new ItemStack(Items.MUTTON, 5));
			me.getInventory().add(new ItemStack(Items.COOKED_MUTTON, 3));
			journal(helper, me).press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(sam) == mutton && mutton.progress[0] == 3 && me.getInventory().countItem(Items.MUTTON) == 5
				&& me.getInventory().countItem(Items.COOKED_MUTTON) == 0, "after 3 cooked and 5 raw: progress " + mutton.progress[0] + ", raw left " + me.getInventory().countItem(Items.MUTTON));
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 0, "paid for three of four");
			CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag again = Stories.Data.load(saved, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(saved.equals(again) && saved.toString().contains("progress:3"), "the 3 of 4 didn't survive a save: " + saved);
			me.getInventory().add(new ItemStack(Items.COOKED_MUTTON, 3));
			journal(helper, me).press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(sam) == null && me.getInventory().countItem(Items.COOKED_MUTTON) == 2 && me.getInventory().countItem(Items.EMERALD) == 4,
				"the last one: open " + (PersonalRequests.of(sam) != null) + ", cooked left " + me.getInventory().countItem(Items.COOKED_MUTTON));
			helper.succeed();
		});
	}

	// --- The giver gone -------------------------------------------------------------------------------------------

	/** The giver unloaded mid-request: nothing breaks and it stays open; loaded again (same villager), the gifted bottle finishes it and the friendship lands on them. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestUnloaded")
	public void aGiverUnloadedAndBackStillHasTheirRequest(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Quest quest = PersonalRequestGameTests.accepted(helper, "level_up", dara, me);
			CompoundTag tag = dara.saveWithoutId(new CompoundTag());
			UUID id = dara.getUUID();
			dara.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			VillageQuests.tick(level, hall, entity);
			PersonalRequests.look(level, hall, entity, true);
			helper.assertTrue(PersonalRequests.open(level, hall).contains(quest), "an unloaded giver's request came down");
			Villager back = EntityType.VILLAGER.create(level);
			back.load(tag);
			level.addFreshEntity(back);
			helper.assertTrue(back.getUUID().equals(id) && PersonalRequests.of(back) == quest, "loaded again, it isn't theirs");
			int before = Friendship.points(back, me.getUUID());
			helper.assertTrue(before == 300, "friendship lost with the unload: " + before);
			giveBottle(me, back);
			int afterGift = Friendship.points(back, me.getUUID());
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(PersonalRequests.of(back) == null && me.getInventory().countItem(Items.EMERALD) == 6, "not done after the reload: level " + back.getVillagerData().getLevel());
			helper.assertTrue(Friendship.points(back, me.getUUID()) - afterGift == Friendship.Favour.REQUEST.points, "friendship: " + afterGift + " -> " + Friendship.points(back, me.getUUID()));
			helper.succeed();
		});
	}

	/**
	 * The giver dies: an unanswered offer is withdrawn (accepting says it's gone); an accepted request never swallows a
	 * hand-in for nothing, and comes down by its deadline.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRequestDead"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestDead")
	public void aGiverWhoDiesMidRequest(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(8, 2, 4), "Odo", "minecraft:farmer", 1);
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Dara");
			var ana = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Ana"));
			ServerPlayer me = ana.player();
			me.moveTo(dara.getX() + 1, dara.getY(), dara.getZ() + 1, 0, 0);
			Friendship.add(dara, me, 300);
			Friendship.add(odo, me, 300);
			// An offer from Odo, who dies before it's answered.
			Quest offered = PersonalRequestGameTests.resolve(helper, "level_up", odo, me);
			PersonalRequests.propose(level, hall, odo, me, offered, Chronicle.day(level));
			odo.kill();
			PersonalRequests.tick(level, level.getGameTime());
			helper.assertTrue(PersonalRequests.offer(offered.id) == null, "a dead villager's offer stood");
			PersonalRequestGameTests.command(me, "workplace quest accept " + offered.id);
			helper.assertTrue(PersonalRequests.open(level, hall).stream().noneMatch(q -> q.id.equals(offered.id)) && texts(ana.seen()).contains("That request is no longer open."),
				"accepting a dead villager's offer: " + texts(ana.seen()));
			// Dara's taste of home, accepted; she dies; the pie handed in after.
			Quest quest = PersonalRequestGameTests.accepted(helper, "taste_of_home", dara, me);
			dara.kill();
			me.getInventory().add(new ItemStack(Items.PUMPKIN_PIE));
			journal(helper, me).press(QuestJournal.PERSONAL_ROW, me);
			boolean kept = me.getInventory().countItem(Items.PUMPKIN_PIE) == 1;
			boolean paid = me.getInventory().countItem(Items.EMERALD) == 4;
			helper.assertTrue(kept || paid, "the pie was taken for nothing after the giver died");
			// By its deadline it is down either way.
			Stories.round(level, hall, PersonalRequestGameTests.hallEntity(helper));
			PersonalRequests.overdue(level, hall, quest.due);
			helper.assertTrue(PersonalRequests.open(level, hall).stream().noneMatch(q -> q.id.equals(quest.id)), "a dead giver's request outlived its deadline");
			helper.succeed();
		});
	}

	// --- 31.10 near misses ----------------------------------------------------------------------------------------

	/** A cat by the door: a tamed wolf 11 blocks from the bed at dawn doesn't count; 9 blocks does. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestWolf"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestWolf")
	public void aTamedWolfJustInsideTenBlocksCounts(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			BlockPos bed = new BlockPos(2, 2, 2);
			dara.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(bed)));
			Wolf wolf = helper.spawn(EntityType.WOLF, bed.offset(11, 0, 0));
			wolf.setNoAi(true);
			wolf.tame(me);
			Quest quest = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "a tamed wolf " + Math.sqrt(wolf.distanceToSqr(helper.absolutePos(bed).getCenter())) + " blocks from the bed counted");
			Vec3 near = helper.absoluteVec(Vec3.atBottomCenterOf(bed.offset(9, 0, 0)));
			wolf.moveTo(near.x, near.y, near.z, 0, 0);
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 3,
				"a tamed wolf " + Math.sqrt(wolf.distanceToSqr(helper.absolutePos(bed).getCenter())) + " blocks from the bed at dawn didn't count");
			helper.succeed();
		});
	}

	/** Off to school: another parent's child schooled doesn't finish it. A chapel wedding: a Market Stall, or a Chapel outside the village, doesn't. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestWrongOne"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestWrongOne")
	public void theWrongChildAndTheWrongBuildingDontCount(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
			boolean couples = Couples.ENABLED;
			Couples.ENABLED = true;
			Leftovers.after(helper, () -> Couples.ENABLED = couples);
			long today = Chronicle.day(level);
			// School: Finn's child Pip; Odo's child Kit.
			Villager finn = PersonalRequestGameTests.worker(helper, new BlockPos(12, 2, 4), "Finn", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), finn, 300);
			Villager pip = HeartEventGameTests.villager(helper, new BlockPos(13, 2, 6), "Pip");
			pip.setAge(-24000);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Mae"), Component.literal("Finn"), "", "", false));
			Villager kit = HeartEventGameTests.villager(helper, new BlockPos(11, 2, 6), "Kit");
			kit.setAge(-24000);
			ModAttachments.PARENTS.set(kit, new Families.Parents(Component.literal("Una"), Component.literal("Odo"), "", "", false));
			Quest school = PersonalRequestGameTests.accepted(helper, "off_to_school", finn, me);
			Schools.teach(level, kit, Schools.LESSONS_NEEDED);
			PersonalRequests.look(level, hall, entity, true);
			helper.assertTrue(Schools.isSchooled(kit) && PersonalRequests.of(finn) == school, "another parent's child schooled finished Finn's request");
			Schools.teach(level, pip, Schools.LESSONS_NEEDED);
			PersonalRequests.look(level, hall, entity, true);
			helper.assertTrue(PersonalRequests.of(finn) == null, "Pip schooled didn't finish it");

			// Chapel: Dara courting Odo.
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(7, 2, 4), "Odo", "minecraft:mason", 2);
			Friendship.add(dara, me, 300);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 1, false));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 1, false));
			Quest chapel = PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			BuildSiteManager sites = BuildSiteManager.get(level);
			BlueprintData.Placement stall = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, Mirror.NONE);
			BlueprintData.Placement away = new BlueprintData.Placement(level.dimension().location(), hall.offset(VillageHalls.RADIUS * 4, -1, 0), Rotation.NONE, Mirror.NONE);
			sites.recordFinished(StarterBlueprints.MARKET_STALL.id(), stall, UUID.randomUUID());
			sites.recordFinished(StarterBlueprints.CHAPEL.id(), away, UUID.randomUUID());
			Leftovers.after(helper, () -> {
				sites.forgetFinished(stall);
				sites.forgetFinished(away);
			});
			PersonalRequests.look(level, hall, entity, true);
			helper.assertTrue(PersonalRequests.of(dara) == chapel && !Couples.partner(dara).married(),
				"a Market Stall, or a Chapel " + VillageHalls.RADIUS * 4 + " blocks off, finished the chapel wedding");
			helper.succeed();
		});
	}

	/** Bring back my love: another villager's grave revived doesn't finish it. */
	//$ gametest_ticks_batch AREA '100' '"qaRequestWrongGrave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRequestWrongGrave")
	public void anotherGraveRevivedDoesntCount(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Graves.ENABLED = true; // (put back by the village)
			long today = Chronicle.day(level);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(12, 2, 4), "Odo", "minecraft:mason", 3);
			Villager finn = PersonalRequestGameTests.worker(helper, new BlockPos(12, 2, 12), "Finn", "minecraft:farmer", 3);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 10, true));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 10, true));
			BlockPos odosGrave = Graves.onDeath(level, odo);
			Couples.onDeath(level, odo);
			odo.discard();
			BlockPos finnsGrave = Graves.onDeath(level, finn);
			finn.discard();
			helper.assertTrue(odosGrave != null && finnsGrave != null && !odosGrave.equals(finnsGrave), "graves: " + odosGrave + ", " + finnsGrave);
			helper.runAfterDelay(2, () -> {
				Quest quest = PersonalRequestGameTests.accepted(helper, "bring_back_my_love", dara, me);
				Villager back = Graves.revive(level, finnsGrave);
				helper.assertTrue(back != null, "Finn's grave gave nobody back");
				PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), true);
				helper.assertTrue(PersonalRequests.of(dara) == quest && me.getInventory().countItem(Items.EMERALD) == 0, "Finn revived finished Dara's request for Odo");
				helper.assertTrue(Couples.partner(dara) == null || !Couples.partner(dara).id().equals(back.getUUID()), "Dara married Finn");
				helper.succeed();
			});
		});
	}

	// --- Two players ----------------------------------------------------------------------------------------------

	/** Two players with offers from two villagers: neither can take or turn down the other's; finishing one pays only its helper; the other stands. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestApart"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestApart")
	public void twoPlayersKeptApart(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
			long today = Chronicle.day(level);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(9, 2, 4), "Odo", "minecraft:farmer", 1);
			var ana = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Ana"));
			var ben = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Ben"));
			Friendship.add(dara, ana.player(), 300);
			Friendship.add(odo, ben.player(), 300);
			ana.player().moveTo(dara.getX() + 1, dara.getY(), dara.getZ() + 1, 0, 0);
			ben.player().moveTo(odo.getX() + 1, odo.getY(), odo.getZ() + 1, 0, 0);
			Quest anas = PersonalRequestGameTests.resolve(helper, "level_up", dara, ana.player());
			Quest bens = PersonalRequestGameTests.resolve(helper, "level_up", odo, ben.player());
			PersonalRequests.propose(level, hall, dara, ana.player(), anas, today);
			PersonalRequests.propose(level, hall, odo, ben.player(), bens, today);
			PersonalRequestGameTests.command(ana.player(), "workplace quest accept " + bens.id);
			PersonalRequestGameTests.command(ana.player(), "workplace quest decline " + bens.id);
			helper.assertTrue(PersonalRequests.offer(odo) != null && PersonalRequests.of(odo) == null, "Ana took or turned down Ben's offer");
			helper.assertTrue(texts(ana.seen()).contains("That request was asked of someone else."), "Ana read: " + texts(ana.seen()));
			PersonalRequestGameTests.command(ana.player(), "workplace quest accept " + anas.id);
			PersonalRequestGameTests.command(ben.player(), "workplace quest accept " + bens.id);
			helper.assertTrue(PersonalRequests.of(dara) == anas && PersonalRequests.of(odo) == bens
				&& anas.helpers.keySet().equals(java.util.Set.of(ana.player().getUUID())) && bens.helpers.keySet().equals(java.util.Set.of(ben.player().getUUID())),
				"the helpers got mixed: " + anas.helpers.keySet() + " / " + bens.helpers.keySet());
			// Ana finishes Dara's: only she is paid; Ben's stands.
			giveBottle(ana.player(), dara);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(PersonalRequests.of(dara) == null && PersonalRequests.of(odo) == bens && bens.progress[0] == 0, "Ben's request moved with Ana's");
			helper.assertTrue(ana.player().getInventory().countItem(Items.EMERALD) == 6 && ben.player().getInventory().countItem(Items.EMERALD) == 0,
				"emeralds: Ana " + ana.player().getInventory().countItem(Items.EMERALD) + ", Ben " + ben.player().getInventory().countItem(Items.EMERALD));
			helper.assertTrue(Friendship.points(dara, ben.player().getUUID()) == 0 && texts(ben.seen()).stream().noneMatch(s -> s.startsWith("You helped")),
				"Ben got something for Ana's help");
			helper.succeed();
		});
	}

	// --- Sentences ------------------------------------------------------------------------------------------------

	/**
	 * What a villager wants reads right for every villager type, singular and plural ("1 Pumpkin Pie", "8 Cookies",
	 * "4 Baked Potatoes"), in the hall tooltip's line and the objective's line; a deadline of one day reads "by
	 * tomorrow" and two "within 2 days".
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRequestWords"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestWords")
	public void whatTheyWantReadsInSingularAndPlural(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Map<VillagerType, String> miss = new LinkedHashMap<>();
			miss.put(VillagerType.PLAINS, "1 Pumpkin Pie");
			miss.put(VillagerType.DESERT, "1 Rabbit Stew");
			miss.put(VillagerType.SAVANNA, "4 Cooked Mutton");
			miss.put(VillagerType.TAIGA, "16 Sweet Berries");
			miss.put(VillagerType.SNOW, "4 Baked Potatoes");
			miss.put(VillagerType.SWAMP, "1 Mushroom Stew");
			miss.put(VillagerType.JUNGLE, "8 Cookies");
			Villager odo = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Odo");
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), odo, 300);
			List<String> wrong = new ArrayList<>();
			Quest plains = null;
			for (Map.Entry<VillagerType, String> e : miss.entrySet()) {
				odo.setVillagerData(odo.getVillagerData().setType(e.getKey()));
				Quest quest = PersonalRequestGameTests.resolve(helper, "taste_of_home", odo, me);
				if (quest == null) {
					wrong.add(e.getKey() + ": nothing asked");
					continue;
				}
				plains = plains == null ? quest : plains;
				String wants = PersonalRequests.wants(level, quest).getString();
				String want = "Odo wants a taste of home: " + e.getValue() + " within 3 days";
				if (!wants.equals(want)) {
					wrong.add("\"" + wants + "\" (want \"" + want + "\")");
				}
				String line = quest.objectives.get(0).line().getString();
				if (!line.equals("Bring " + e.getValue())) {
					wrong.add("\"" + line + "\" (want \"Bring " + e.getValue() + "\")");
				}
			}
			// One day left reads "by tomorrow", a day and a tick "within 2 days".
			long now = level.getGameTime();
			for (long left : new long[] {1000, 24000, 24001}) {
				Quest q = new Quest(plains.id, plains.file, plains.giver, plains.name, plains.poster, now, now + left, plains.objectives, new int[1], plains.rewards);
				q.villager = odo.getUUID();
				q.text = plains.text;
				String wants = PersonalRequests.wants(level, q).getString();
				String want = "Odo wants a taste of home: 4 Cooked Mutton" + (left <= 24000 ? " by tomorrow" : " within 2 days");
				want = want.replace("4 Cooked Mutton", miss.get(VillagerType.PLAINS));
				if (!wants.equals(want)) {
					wrong.add("\"" + wants + "\" (want \"" + want + "\")");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "sentences that read wrong: " + wrong);
			helper.succeed();
		});
	}
}
