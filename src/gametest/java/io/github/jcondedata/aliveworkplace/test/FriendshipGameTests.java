package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Friendship (ROADMAP 31.5): each favour and its once-a-day rule, the hit penalty, two players kept apart, the hearts
 * in the action bar and the hall's tooltip, the {@code friendship} quest reward, a save and reload, and the switch off.
 */
public class FriendshipGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** The hall, a named villager by it and a player (survival), alone in the batch; {@code then} runs once the hall's POI is in. */
	private static void village(GameTestHelper helper, java.util.function.BiConsumer<Villager, ServerPlayer> then) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			CivicEffects.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara");
		ServerPlayer player = player(helper, new BlockPos(5, 2, 8));
		helper.runAfterDelay(2, () -> {
			CivicEffects.forget(); // the hall's POI goes in after the tick it was placed
			then.accept(dara, player);
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	/** Turns {@code player} to look at {@code target}'s chest. */
	private static void face(ServerPlayer player, Villager target) {
		Vec3 d = target.position().add(0, 1.2, 0).subtract(player.getEyePosition());
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		player.moveTo(player.getX(), player.getY(), player.getZ(), yaw, pitch);
		player.setYHeadRot(yaw);
	}

	private static void trade(Villager villager, ServerPlayer player) {
		villager.setTradingPlayer(player);
		villager.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD), 12, 1, 0.05f));
		villager.setTradingPlayer(null);
	}

	/** A hall quest {@code poster} put up: bring {@code count} bread to the hall, paying {@code rewards}. */
	private static Quest breadQuest(ServerLevel level, BlockPos hall, String poster, int count, List<Rewards.Reward> rewards) {
		long now = level.getGameTime();
		Quest quest = new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/worker_request"), "hall", Optional.empty(), poster, now, now + 24000,
			List.of(new Objectives.Bring("minecraft:bread", count, "hall", Optional.empty())), new int[1], rewards);
		Stories.post(level, hall, quest);
		return quest;
	}

	/** Trading (+5), a hand-in (+10, also at the Storehouse board) and a finished hall quest she posted (+40): once a day each. */
	//$ gametest_ticks_batch AREA '80' '"friendshipFavours"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "friendshipFavours")
	public void tradesQuestsAndHandInsAreFavoursOnceADay(GameTestHelper helper) {
		village(helper, (dara, player) -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			UUID me = player.getUUID();
			long dayTime = level.getDayTime();
			Leftovers.after(helper, () -> level.setDayTime(dayTime));
			helper.assertTrue(Friendship.eligible(dara), "Dara by the hall keeps friendships");
			helper.assertTrue(!ModAttachments.FRIENDSHIP.has(dara), "nobody has points before a favour");

			trade(dara, player);
			helper.assertTrue(Friendship.points(dara, me) == 5, "a trade: " + Friendship.points(dara, me));
			trade(dara, player);
			helper.assertTrue(Friendship.points(dara, me) == 5, "a second trade the same day counted: " + Friendship.points(dara, me));

			// Her quest: the bread handed in (+10) finishes it (+40).
			player.getInventory().add(new ItemStack(Items.BREAD, 4));
			Quest first = breadQuest(level, hall, "Dara", 2, List.of());
			helper.assertTrue(Stories.handIn(player, hall, first.id) == 2, "the bread went in");
			helper.assertTrue(Stories.open(level, hall).stream().noneMatch(q -> q.id.equals(first.id)), "the quest is done");
			helper.assertTrue(Friendship.points(dara, me) == 55, "hand-in and quest: " + Friendship.points(dara, me));
			Quest second = breadQuest(level, hall, "Dara", 2, List.of());
			Stories.handIn(player, hall, second.id);
			helper.assertTrue(Friendship.points(dara, me) == 55, "a second quest the same day counted: " + Friendship.points(dara, me));

			// The Storehouse board: handing over what Bram waits for.
			Villager bram = villager(helper, new BlockPos(3, 2, 5), "Bram");
			Requests.given(player, new Requests.Request(bram, bram.blockPosition(), new ItemStack(Items.WHEAT_SEEDS), 4,
				Component.literal("seeds"), s -> true), 4);
			helper.assertTrue(Friendship.points(bram, me) == 10, "the board's hand-in: " + Friendship.points(bram, me));
			Requests.given(player, new Requests.Request(bram, bram.blockPosition(), new ItemStack(Items.WHEAT_SEEDS), 4,
				Component.literal("seeds"), s -> true), 4);
			helper.assertTrue(Friendship.points(bram, me) == 10, "a second hand-in the same day: " + Friendship.points(bram, me));

			// The next day each counts again.
			level.setDayTime(dayTime + 24000);
			trade(dara, player);
			player.getInventory().add(new ItemStack(Items.BREAD, 2));
			Quest third = breadQuest(level, hall, "Dara", 2, List.of());
			Stories.handIn(player, hall, third.id);
			helper.assertTrue(Friendship.points(dara, me) == 110, "the next day: " + Friendship.points(dara, me));
			helper.assertTrue(Friendship.of(dara).bond(me).favours().get("trade") == Chronicle.day(level), "the trade's day is kept");

			// A villager without a name keeps no friendships.
			Villager nobody = villager(helper, new BlockPos(7, 2, 3), null);
			trade(nobody, player);
			helper.assertTrue(!ModAttachments.FRIENDSHIP.has(nobody), "an unnamed villager got points");
			helper.succeed();
		});
	}

	/** A monster that hurt her killed within 10 seconds (+15, every time), a wedding (+30), a festival (+10), a request (+150), and hits (-50, once a minute). */
	//$ gametest_ticks_batch AREA '320' '"friendshipDefended"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "friendshipDefended")
	public void defendingHerCountsAndHittingHerCosts(GameTestHelper helper) {
		village(helper, (dara, player) -> {
			ServerLevel level = helper.getLevel();
			UUID me = player.getUUID();
			Friendship.add(dara, player, 200);
			for (int i = 0; i < 2; i++) {
				Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4 + i, 2, 3));
				zombie.setNoAi(true);
				dara.invulnerableTime = 0; // (a second blow within half a second would be ignored)
				dara.hurt(level.damageSources().mobAttack(zombie), 1f);
				zombie.hurt(level.damageSources().playerAttack(player), 1000f);
			}
			helper.assertTrue(Friendship.points(dara, me) == 230, "two zombies that hurt her: " + Friendship.points(dara, me));
			Zombie bystander = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 3));
			bystander.setNoAi(true);
			bystander.hurt(level.damageSources().playerAttack(player), 1000f);
			helper.assertTrue(Friendship.points(dara, me) == 230, "a zombie that never hurt her counted");
			dara.heal(20f);

			// Hits: 50 off, once a minute.
			dara.invulnerableTime = 0;
			dara.hurt(level.damageSources().playerAttack(player), 0.5f);
			helper.assertTrue(Friendship.points(dara, me) == 180, "a hit: " + Friendship.points(dara, me));
			dara.invulnerableTime = 0;
			dara.hurt(level.damageSources().playerAttack(player), 0.5f);
			helper.assertTrue(Friendship.points(dara, me) == 180, "a second hit within the minute: " + Friendship.points(dara, me));
			dara.heal(20f);

			// The wedding of Dara and Bram, with the player there.
			Villager bram = villager(helper, new BlockPos(3, 2, 5), "Bram");
			Couples.wed(level, helper.absolutePos(HALL), dara, bram);
			helper.assertTrue(Friendship.points(dara, me) == 210 && Friendship.points(bram, me) == 30,
				"the wedding: " + Friendship.points(dara, me) + ", " + Friendship.points(bram, me));
			// A festival she came to (the hall's round calls this for each player there while it's on).
			Friendship.onFestival(level, helper.absolutePos(HALL), player);
			Friendship.onFestival(level, helper.absolutePos(HALL), player);
			helper.assertTrue(Friendship.points(dara, me) == 220, "the festival, once: " + Friendship.points(dara, me));
			helper.assertTrue(Friendship.favour(dara, player, Friendship.Favour.REQUEST) == 150 && Friendship.points(dara, me) == 370,
				"a personal request done: " + Friendship.points(dara, me));

			// A zombie that hurt her more than 10 seconds ago doesn't count.
			Zombie late = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 3));
			late.setNoAi(true);
			dara.invulnerableTime = 0;
			dara.hurt(level.damageSources().mobAttack(late), 1f);
			helper.runAfterDelay(Friendship.DEFEND_WINDOW + 5, () -> {
				late.hurt(level.damageSources().playerAttack(player), 1000f);
				helper.assertTrue(Friendship.points(dara, me) == 370, "a kill 10 seconds late counted: " + Friendship.points(dara, me));
				helper.succeed();
			});
		});
	}

	/** Two players' friendships are their own: the action bar, the hall's tooltip and the quest reward to helpers. */
	//$ gametest_ticks_batch AREA '80' '"friendshipTwoPlayers"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "friendshipTwoPlayers")
	public void twoPlayersAreKeptApart(GameTestHelper helper) {
		village(helper, (dara, alex) -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer sam = player(helper, new BlockPos(6, 2, 8));
			trade(dara, alex);
			Friendship.add(dara, alex, 300);
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 305 && Friendship.points(dara, sam.getUUID()) == 0,
				"alex's trade reached sam: " + Friendship.of(dara));

			// The action bar: each sees their own hearts.
			face(alex, dara);
			face(sam, dara);
			Component alexLine = Friendship.lookLine(alex);
			Component samLine = Friendship.lookLine(sam);
			helper.assertTrue(alexLine != null && alexLine.getString().equals("Dara ♥♥♥♡♡♡♡♡♡♡"), "alex sees: " + alexLine);
			helper.assertTrue(samLine != null && samLine.getString().equals("Dara ♡♡♡♡♡♡♡♡♡♡"), "sam sees: " + samLine);
			alex.moveTo(alex.getX(), alex.getY(), alex.getZ(), alex.getYRot() + 180, 0);
			alex.setYHeadRot(alex.getYRot());
			helper.assertTrue(Friendship.lookLine(alex) == null, "hearts shown looking away");
			Vec3 far = helper.absoluteVec(new Vec3(5.5, 2, 14.5));
			alex.moveTo(far.x, far.y, far.z, 0, 0);
			face(alex, dara);
			helper.assertTrue(Friendship.lookLine(alex) == null, "hearts shown from 9 blocks");
			Villager unnamed = villager(helper, new BlockPos(7, 2, 6), null);
			Vec3 near = helper.absoluteVec(new Vec3(7.5, 2, 9.5));
			sam.moveTo(near.x, near.y, near.z, 0, 0);
			face(sam, unnamed);
			helper.assertTrue(Friendship.lookLine(sam) == null, "hearts shown for an unnamed villager");

			// The quest reward to helpers: both handed in, sam finished it.
			Quest quest = breadQuest(level, hall, "Dara", 2, List.of(Rewards.parse(JsonParser.parseString(
				"{\"type\": \"friendship\", \"points\": 20, \"to\": \"giver\", \"who\": \"helpers\"}").getAsJsonObject())));
			alex.getInventory().add(new ItemStack(Items.BREAD));
			sam.getInventory().add(new ItemStack(Items.BREAD));
			Stories.handIn(alex, hall, quest.id);
			Stories.handIn(sam, hall, quest.id);
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 305 + 10 + 20, "alex, a helper: " + Friendship.points(dara, alex.getUUID()));
			helper.assertTrue(Friendship.points(dara, sam.getUUID()) == 10 + 40 + 20, "sam, who finished it: " + Friendship.points(dara, sam.getUUID()));
			Rewards.Reward reward = quest.rewards.get(0);
			helper.assertTrue(Rewards.parse(reward.json()).equals(reward), "the reward's json: " + reward.json());
			boolean refused;
			try {
				Rewards.parse(JsonParser.parseString("{\"type\": \"friendship\", \"points\": 5, \"who\": \"everyone\"}").getAsJsonObject());
				refused = false;
			} catch (IllegalArgumentException e) {
				refused = true;
			}
			helper.assertTrue(refused, "a bad 'who' was read");

			// The hall's tooltip: your hearts, and the two best friends among the players.
			Friendship.add(dara, UUID.randomUUID(), "Jesse", 520);
			Friendship.add(dara, UUID.randomUUID(), "Rook", 10);
			ChoiceMenu menu = VillageHallScreen.forTest(alex, hall);
			ItemStack icon = null;
			for (int slot = VillageHallScreen.FIRST_PERSON; slot < ChoiceMenu.SIZE; slot++) {
				ItemStack s = menu.icon(slot);
				if (s.has(DataComponents.CUSTOM_NAME) && s.get(DataComponents.CUSTOM_NAME).getString().equals("Dara")) {
					icon = s;
				}
			}
			helper.assertTrue(icon != null, "Dara isn't on the hall's list");
			List<String> lore = icon.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
			helper.assertTrue(lore.contains("Your hearts: ♥♥♥♡♡♡♡♡♡♡"), "alex's hearts in the tooltip: " + lore);
			helper.assertTrue(lore.contains("Best friends: Jesse, " + alex.getGameProfile().getName()), "best friends: " + lore);
			List<String> samLore = VillageHallScreen.person(level, hall, dara, sam).get(DataComponents.LORE).lines().stream().map(Component::getString).toList();
			helper.assertTrue(samLore.contains("Your hearts: ♡♡♡♡♡♡♡♡♡♡"), "sam's hearts in the tooltip: " + samLore);
			helper.succeed();
		});
	}

	/** The attachment survives a save and reload; a villager from an old save has none and reads as 0. */
	//$ gametest_ticks_batch AREA '40' '"friendshipSaved"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "friendshipSaved")
	public void friendshipIsSavedOnTheVillager(GameTestHelper helper) {
		village(helper, (dara, player) -> {
			ServerLevel level = helper.getLevel();
			trade(dara, player);
			Friendship.add(dara, player, 140);
			Friendship.add(dara, UUID.randomUUID(), "Jesse", 75);
			dara.hurt(level.damageSources().playerAttack(player), 0.5f);
			Friendship.Data before = Friendship.of(dara);
			helper.assertTrue(before.bond(player.getUUID()).points() == 95 && before.bond(player.getUUID()).hitTick() >= 0, "before: " + before);
			CompoundTag saved = dara.saveWithoutId(new CompoundTag());
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(saved);
			helper.assertTrue(before.equals(ModAttachments.FRIENDSHIP.get(copy)), "after a reload: " + ModAttachments.FRIENDSHIP.get(copy) + " vs " + before);
			Villager old = EntityType.VILLAGER.create(level);
			helper.assertTrue(!ModAttachments.FRIENDSHIP.has(old) && Friendship.points(old, player.getUUID()) == 0, "an old villager has friendship");
			// A save written with only the points (a later version may add fields) reads with defaults.
			Friendship.Data sparse = Friendship.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString(
				"{\"players\": {\"" + player.getUUID() + "\": {\"points\": 300}}}")).getOrThrow();
			helper.assertTrue(sparse.bond(player.getUUID()).points() == 300 && sparse.bond(player.getUUID()).favours().isEmpty()
				&& sparse.bond(player.getUUID()).hitTick() == -1, "sparse: " + sparse);
			copy.discard();
			old.discard();
			helper.succeed();
		});
	}

	/** {@code friendship} off: no points move, no hearts show, saved friendship stays, and quests still work. */
	//$ gametest_ticks_batch AREA '60' '"friendshipOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "friendshipOff")
	public void switchedOffNothingMovesOrShows(GameTestHelper helper) {
		village(helper, (dara, player) -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Friendship.add(dara, player, 300);
			WorkplaceConfig.parse("{\"friendship\": false}").apply();
			Leftovers.after(helper, () -> new WorkplaceConfig().apply());
			helper.assertTrue(!Friendship.ENABLED, "the switch didn't turn it off");
			trade(dara, player);
			player.getInventory().add(new ItemStack(Items.BREAD, 2));
			Quest quest = breadQuest(level, hall, "Dara", 2, List.of(new Rewards.Money_(3, 0, 0, false)));
			Stories.handIn(player, hall, quest.id);
			helper.assertTrue(Stories.open(level, hall).stream().noneMatch(q -> q.id.equals(quest.id)) && player.getInventory().countItem(Items.EMERALD) == 3,
				"the quest still finishes and pays");
			dara.hurt(level.damageSources().playerAttack(player), 0.5f);
			helper.assertTrue(Friendship.points(dara, player.getUUID()) == 300, "points moved while off: " + Friendship.points(dara, player.getUUID()));
			face(player, dara);
			helper.assertTrue(Friendship.lookLine(player) == null, "hearts shown while off");
			List<String> lore = VillageHallScreen.person(level, hall, dara, player).get(DataComponents.LORE).lines().stream().map(Component::getString).toList();
			helper.assertTrue(lore.stream().noneMatch(l -> l.contains("hearts") || l.contains("Best friends")), "the tooltip shows friendship while off: " + lore);
			new WorkplaceConfig().apply();
			helper.assertTrue(Friendship.points(dara, player.getUUID()) == 300 && Friendship.lookLine(player) != null, "back on, the hearts are kept");
			helper.succeed();
		});
	}
}
