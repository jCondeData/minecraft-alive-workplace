package io.github.jcondedata.aliveworkplace.test;

import com.mojang.authlib.GameProfile;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1114-1252), B1231 from its expected behavior: "a quest done is never lost; keep it waiting while its last
 * helper is offline, or pay at their next login". The fix's tests cover a kill and a place reached, past the deadline and
 * through a save; these test the edges: paid exactly once over several rounds and a save, nobody else online is paid,
 * a waiting done quest takes no board slot, a one-time quest becomes the helper's once paid, and a villager's own request
 * still finishes at once.
 */
public class QaFarQuestOffline1114c2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** A real (listed) player with this profile at {@code at} (relative); removed when the test ends. */
	private static ServerPlayer join(GameTestHelper helper, GameProfile profile, BlockPos at) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		Leftovers.after(helper, () -> {
			if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
				level.getServer().getPlayerList().remove(player);
			}
		});
		return player;
	}

	private static void leave(ServerLevel level, ServerPlayer player) {
		level.getServer().getPlayerList().remove(player);
	}

	/** The far hall and its stories go after the test. */
	private static void cleanUp(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		Leftovers.after(helper, () -> {
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			Stories.Data.get(level).remove(hall);
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(4))) {
				item.discard();
			}
		});
	}

	private static Quest killQuest(ServerLevel level, ResourceLocation file, int diamonds, long due) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), file, "hall", Optional.empty(), "Ana", now, due,
			List.of(new Objectives.Kill("minecraft:zombie", 1, true)), new int[1], List.of(new Rewards.ItemReward(ResourceLocation.parse("minecraft:diamond"), diamonds)));
	}

	/** {@code player} kills a zombie in the test area (far from every village). */
	private static void kill(GameTestHelper helper, ServerPlayer player) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
		zombie.setNoAi(true);
		Stories.onKill(helper.getLevel(), zombie, player);
		zombie.discard();
	}

	/** The far hall's chunk loads with its hall (no new quest this morning); its block entity. */
	private static VillageHallBlockEntity load(ServerLevel level, BlockPos hall) {
		level.getChunk(hall);
		if (!level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
			level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		return entity;
	}

	private static Optional<Quest> waiting(ServerLevel level, BlockPos hall, UUID id) {
		return Stories.open(level, hall).stream().filter(x -> x.id.equals(id)).findFirst();
	}

	private static List<String> didAQuest(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().map(e -> e.text().getString()).filter(t -> t.contains(" did a quest: ")).toList();
	}

	private static int dropped(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(6), i -> i.getItem().is(Items.DIAMOND))
			.stream().mapToInt(i -> i.getItem().getCount()).sum();
	}

	/**
	 * Ana finishes a far kill quest (its deadline already passed) and logs off. Two rounds and a save of the stories go by:
	 * it waits, done and hers, paid to nobody. Ana logs in; the next round pays her 3 diamonds once, and two more rounds pay
	 * nothing more (no more diamonds, standing or chronicle lines).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarQuestOffline1114c2Once"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarQuestOffline1114c2Once")
	public void aWaitingQuestIsPaidExactlyOnceWhenItsHelperComesBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8700, -8700);
		cleanUp(helper, hall);
		GameProfile profile = new GameProfile(UUID.randomUUID(), "far-ana-c2");
		ServerPlayer ana = join(helper, profile, new BlockPos(2, 2, 2));
		Quest q = killQuest(level, AliveWorkplace.id("personal/qa1114c2once"), 3, level.getGameTime());
		Stories.post(level, hall, q);
		kill(helper, ana);
		helper.assertTrue(q.done() && ana.getUUID().equals(q.last) && !loaded(level, hall), "setup: Ana's kill didn't finish the far quest");
		leave(level, ana);

		VillageHallBlockEntity entity = load(level, hall);
		Stories.round(level, hall, entity);
		Stories.round(level, hall, entity);
		helper.assertTrue(waiting(level, hall, q.id).map(x -> x.done() && profile.getId().equals(x.last)).orElse(false),
			"a far quest Ana finished (past its deadline) didn't wait for her over two rounds while she was offline: " + Stories.open(level, hall));
		helper.assertTrue(didAQuest(entity).isEmpty() && dropped(level, hall) == 0,
			"a quest waiting for Ana was paid while she was offline: chronicle " + didAQuest(entity) + ", " + dropped(level, hall) + " diamonds dropped at the hall");
		var registries = level.registryAccess();
		level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(Stories.Data.get(level).save(new CompoundTag(), registries), registries));
		helper.assertTrue(waiting(level, hall, q.id).map(x -> x.done() && profile.getId().equals(x.last)).orElse(false),
			"the kill quest waiting for Ana wasn't kept, done and hers, through a save");

		ServerPlayer back = join(helper, profile, new BlockPos(2, 2, 2));
		Stories.round(level, hall, entity);
		int diamonds = back.getInventory().countItem(Items.DIAMOND);
		int points = Reputation.points(level, hall, profile.getId());
		helper.assertTrue(diamonds == 3 && points > 0, "Ana, back online, wasn't paid at the round: " + diamonds + " of 3 diamonds, " + points + " standing");
		helper.assertTrue(waiting(level, hall, q.id).isEmpty(), "the quest paid to Ana is still on the board");
		Stories.round(level, hall, entity);
		Stories.round(level, hall, entity);
		int after = back.getInventory().countItem(Items.DIAMOND) + dropped(level, hall);
		List<String> lines = didAQuest(entity);
		helper.assertTrue(after == 3 && Reputation.points(level, hall, profile.getId()) == points && lines.size() == 1,
			"Ana's waiting quest was paid more than once over three rounds: " + after + " diamonds (3 expected), standing " + points + " then "
				+ Reputation.points(level, hall, profile.getId()) + ", chronicle " + lines);
		helper.assertTrue(lines.get(0).startsWith("far-ana-c2 did a quest: "), "the chronicle doesn't name Ana: " + lines);
		helper.succeed();
	}

	/**
	 * Ana finishes a far quest and logs off; Bo is online when the hall's round runs, and kills a zombie too: he gets nothing
	 * and doesn't take the quest over. Ana, back, is paid; Bo still has nothing.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarQuestOffline1114c2Bo"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarQuestOffline1114c2Bo")
	public void anotherPlayerOnlineAtTheRoundIsNotPaidAQuestWaitingForItsHelper(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8700, 8700);
		cleanUp(helper, hall);
		GameProfile anaProfile = new GameProfile(UUID.randomUUID(), "far-ana-c2b");
		ServerPlayer ana = join(helper, anaProfile, new BlockPos(2, 2, 2));
		ServerPlayer bo = join(helper, new GameProfile(UUID.randomUUID(), "far-bo-c2"), new BlockPos(10, 2, 10));
		Quest q = killQuest(level, AliveWorkplace.id("personal/qa1114c2bo"), 3, level.getGameTime() + 24000L * 3);
		Stories.post(level, hall, q);
		kill(helper, ana);
		helper.assertTrue(q.done() && ana.getUUID().equals(q.last), "setup: Ana's kill didn't finish the far quest");
		leave(level, ana);

		VillageHallBlockEntity entity = load(level, hall);
		Stories.round(level, hall, entity);
		kill(helper, bo);
		Stories.round(level, hall, entity);
		helper.assertTrue(bo.getInventory().countItem(Items.DIAMOND) == 0 && Reputation.points(level, hall, bo.getUUID()) == 0,
			"Bo, online at the round, got the quest Ana finished while she was away: " + bo.getInventory().countItem(Items.DIAMOND) + " diamonds, "
				+ Reputation.points(level, hall, bo.getUUID()) + " standing");
		helper.assertTrue(waiting(level, hall, q.id).map(x -> x.done() && anaProfile.getId().equals(x.last) && !x.helpers.containsKey(bo.getUUID())).orElse(false),
			"Ana's waiting quest isn't hers alone after Bo's kill: " + waiting(level, hall, q.id).map(x -> x.last + " " + x.helpers).orElse("gone"));
		helper.assertTrue(didAQuest(entity).isEmpty(), "the chronicle credits someone with Ana's quest while she is away: " + didAQuest(entity));

		ServerPlayer back = join(helper, anaProfile, new BlockPos(2, 2, 2));
		Stories.round(level, hall, entity);
		helper.assertTrue(back.getInventory().countItem(Items.DIAMOND) == 3, "Ana, back, wasn't paid: " + back.getInventory().countItem(Items.DIAMOND) + " of 3 diamonds");
		helper.assertTrue(bo.getInventory().countItem(Items.DIAMOND) == 0, "Bo was paid Ana's quest too");
		helper.assertTrue(didAQuest(entity).equals(List.of("far-ana-c2b did a quest: " + q.title().getString())), "the chronicle: " + didAQuest(entity));
		helper.succeed();
	}

	/**
	 * A quest finished and waiting for its offline helper takes no slot on the board: with room for one quest, the morning's
	 * round posts a new one beside it. Not even far past its deadline does it go.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarQuestOffline1114c2Slot"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarQuestOffline1114c2Slot")
	public void aWaitingQuestTakesNoBoardSlot(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8700, 8700);
		cleanUp(helper, hall);
		int max = VillageQuests.MAX_OPEN;
		long time = level.getDayTime();
		boolean enabled = Stories.ENABLED;
		Leftovers.after(helper, () -> {
			VillageQuests.MAX_OPEN = max;
			level.setDayTime(time);
			Stories.ENABLED = enabled;
		});
		GameProfile profile = new GameProfile(UUID.randomUUID(), "far-ana-c2s");
		ServerPlayer ana = join(helper, profile, new BlockPos(2, 2, 2));
		Quest q = killQuest(level, AliveWorkplace.id("personal/qa1114c2slot"), 1, level.getGameTime());
		Stories.post(level, hall, q);
		kill(helper, ana);
		helper.assertTrue(q.done(), "setup: Ana's kill didn't finish the far quest");
		leave(level, ana);

		VillageQuests.MAX_OPEN = 1;
		Stories.ENABLED = true;
		long day = Math.floorDiv(time, 24000L) + 2;
		level.setDayTime(day * 24000L + 1000); // a morning, when the board posts its quest
		VillageHallBlockEntity entity = load(level, hall);
		entity.setLastQuestDay(-1);
		Stories.round(level, hall, entity);
		List<Quest> open = Stories.open(level, hall);
		helper.assertTrue(waiting(level, hall, q.id).isPresent(), "the quest waiting for Ana went at the round, past its deadline: " + open);
		helper.assertTrue(open.stream().anyMatch(x -> !x.id.equals(q.id) && !x.done() && x.giver.equals("hall")),
			"with room for one quest on the board, the quest waiting for Ana (offline) kept the morning's new quest off it: " + open.size() + " quests, "
				+ open.stream().map(x -> x.file + (x.done() ? " (done)" : "")).toList());
		helper.succeed();
	}

	/**
	 * A one-time quest Ana finished at a far village while she was offline at the round becomes hers once she is paid: the
	 * same quest put up again counts nothing for her, and counts for Bo.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarQuestOffline1114c2OneTime"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarQuestOffline1114c2OneTime")
	public void aOneTimeQuestWaitingForItsHelperBecomesTheirsWhenPaid(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8700, -8700);
		cleanUp(helper, hall);
		var manager = level.getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
		Resource any = real.values().iterator().next();
		Map<ResourceLocation, Resource> withOurs = new HashMap<>(real);
		String json = "{\"giver\": \"hall\", \"weight\": 0, \"repeatable\": false, \"objectives\": [{\"type\": \"bring\", \"item\": \"minecraft:apple\", \"count\": 1}]}";
		withOurs.put(AliveWorkplace.id(QuestFiles.FOLDER + "/qa1114c2/once.json"), new Resource(any.source(),
			() -> new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
		QuestFiles.load(withOurs);
		Leftovers.after(helper, () -> QuestFiles.load(real));
		ResourceLocation once = AliveWorkplace.id("qa1114c2/once");
		helper.assertTrue(QuestFiles.get(once).isPresent() && !QuestFiles.get(once).get().repeatable(), "setup: the one-time file didn't load");

		GameProfile profile = new GameProfile(UUID.randomUUID(), "far-ana-c2o");
		ServerPlayer ana = join(helper, profile, new BlockPos(2, 2, 2));
		ServerPlayer bo = join(helper, new GameProfile(UUID.randomUUID(), "far-bo-c2o"), new BlockPos(10, 2, 10));
		Quest first = killQuest(level, once, 2, level.getGameTime() + 24000L * 3);
		Stories.post(level, hall, first);
		kill(helper, ana);
		helper.assertTrue(first.done(), "setup: Ana's kill didn't finish the far one-time quest");
		leave(level, ana);
		VillageHallBlockEntity entity = load(level, hall);
		Stories.round(level, hall, entity);
		helper.assertFalse(Stories.finished(level, hall, once), "a one-time quest waiting for its offline helper counts as finished already");
		ServerPlayer back = join(helper, profile, new BlockPos(2, 2, 2));
		Stories.round(level, hall, entity);
		helper.assertTrue(back.getInventory().countItem(Items.DIAMOND) == 2 && Stories.finished(level, hall, once), "setup: Ana, back, wasn't paid the one-time quest");

		Quest again = killQuest(level, once, 2, level.getGameTime() + 24000L * 3);
		Stories.post(level, hall, again);
		kill(helper, back);
		helper.assertTrue(again.progress[0] == 0,
			"Ana, paid a one-time quest that waited for her while she was offline, could do it again: her kill counted (" + again.progress[0] + ")");
		kill(helper, bo);
		helper.assertTrue(again.progress[0] == 1 && bo.getUUID().equals(again.last), "Bo, who never did the one-time quest, can't do it: " + again.progress[0]);
		helper.succeed();
	}

	/**
	 * A villager's own request (31.9) finished while its hall was unloaded by a helper who is now offline is done at the
	 * round at once (it pays every helper, away ones included): it doesn't wait, and the chronicle names the helper.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarQuestOffline1114c2Request"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarQuestOffline1114c2Request")
	public void aVillagersOwnRequestFinishedByAnOfflineHelperIsDoneAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8800, 0);
		cleanUp(helper, hall);
		GameProfile profile = new GameProfile(UUID.randomUUID(), "far-ana-c2r");
		ServerPlayer ana = join(helper, profile, new BlockPos(2, 2, 2));
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114c2request"), "villager", Optional.empty(), "Mira", now, now + 24000L * 3,
			List.of(new Objectives.Kill("minecraft:zombie", 1, true)), new int[] {1}, List.of(new Rewards.ItemReward(ResourceLocation.parse("minecraft:diamond"), 2)));
		q.villager = UUID.randomUUID();
		q.helpers.put(profile.getId(), 1);
		q.last = profile.getId();
		helper.assertTrue(q.personal() && q.done(), "setup: not a finished personal request");
		Stories.post(level, hall, q);
		leave(level, ana);

		VillageHallBlockEntity entity = load(level, hall);
		Stories.round(level, hall, entity);
		helper.assertTrue(waiting(level, hall, q.id).isEmpty(),
			"a villager's own request finished by a helper now offline waits at the round instead of being done: " + Stories.open(level, hall));
		List<String> lines = entity.chronicle().stream().map(e -> e.text().getString()).filter(t -> t.contains(" helped Mira: ")).toList();
		helper.assertTrue(lines.size() == 1, "the chronicle has no line for the request done (\"... helped Mira: ...\"): "
			+ entity.chronicle().stream().map(e -> e.text().getString()).toList());
		helper.succeed();
	}
}
