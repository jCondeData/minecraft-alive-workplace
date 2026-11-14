package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.TrainingDummyBlock;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Lane E QA (lane-e-1114-1312), B1231 and B1232 checked from their decisions:
 * <ul>
 *   <li>B1231: a quest finished for a far village by a helper who then logs off "waits, done and yours" and is paid at the
 *   village's first round after they're back (CHANGELOG). Here: through a real world save to disk and the hall's chunk
 *   unloading and loading again, two offline helpers with a quest each, a one-time quest recorded as the helper's, and a
 *   hall broken before its helper returns.</li>
 *   <li>B1232: at a Village Hall nobody is near (its chunk loaded, not entity-ticking) the drill, the Shift Bell and the
 *   lamplit line don't happen and load nothing, at their exact boundary ticks; once someone is near they do.</li>
 * </ul>
 */
public class QaFarOffline1114eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String STORIES = "aliveworkplace_stories";
	private static final ResourceLocation DIAMOND = ResourceLocation.parse("minecraft:diamond");
	private static final ResourceLocation GOLD = ResourceLocation.parse("minecraft:gold_ingot");
	/** Holds a chunk loaded but not ticking, like the outer ring of a player's view (as QaFarHall1114cBugTests). */
	private static final TicketType<ChunkPos> BORDER = TicketType.create("qa1114e_offline_border", Comparator.comparingLong(ChunkPos::toLong));

	/** A far spot in the middle of its chunk, so blocks one or two off the hall stay in the hall's chunk. */
	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int x = ((origin.getX() + dx) >> 4 << 4) + 8;
		int z = ((origin.getZ() + dz) >> 4 << 4) + 8;
		return new BlockPos(x, 70, z);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static boolean loaded(ServerLevel level, int cx, int cz) {
		return level.getChunkSource().getChunkNow(cx, cz) != null;
	}

	/** After the test: the far hall goes (if one was put there) and so do its stories. */
	private static void cleanUp(GameTestHelper helper, ServerLevel level, BlockPos hall, boolean[] placed) {
		Leftovers.after(helper, () -> {
			if (placed[0] && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			Stories.Data.get(level).remove(hall);
		});
	}

	/** {@code profile} joins (again) at {@code at} (relative) as a real listed player keeping every chat line in {@code seen}. */
	private static ServerPlayer join(GameTestHelper helper, GameProfile profile, BlockPos at, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		Leftovers.after(helper, () -> logOff(level, player));
		return player;
	}

	private static void logOff(ServerLevel level, ServerPlayer player) {
		if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
			level.getServer().getPlayerList().remove(player);
		}
	}

	private static Quest killQuest(ServerLevel level, ResourceLocation file, ResourceLocation item, int count) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), file, "hall", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new Objectives.Kill("minecraft:zombie", 1, true)), new int[1], List.of(new Rewards.ItemReward(item, count)));
	}

	/** {@code player} kills a zombie in the test area (far from every village). */
	private static void kill(GameTestHelper helper, ServerPlayer player) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
		zombie.setNoAi(true);
		Stories.onKill(helper.getLevel(), zombie, player);
		zombie.discard();
	}

	/** The far hall's chunk (loaded, or read back from disk) with its hall, and the hall's round (no new quest this morning). */
	private static VillageHallBlockEntity loadAndRound(ServerLevel level, BlockPos hall, boolean[] placed) {
		level.getChunk(hall);
		placed[0] = true;
		if (!level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
			level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Stories.round(level, hall, entity);
		return entity;
	}

	/** What the server's autosave does (every dimension's saved data to disk), then the stories read back from that file, as at the next start. */
	private static void saveAndReadBackFromDisk(ServerLevel level) {
		Stories.Data.get(level);
		level.getDataStorage().save();
		java.nio.file.Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(STORIES + ".dat");
		if (!java.nio.file.Files.isRegularFile(file)) {
			throw new GameTestAssertException("saving the world wrote no " + file.getFileName());
		}
		CompoundTag root;
		try {
			root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("the stories file can't be read back: " + e);
		}
		level.getDataStorage().set(STORIES, Stories.Data.load(root.getCompound("data"), level.registryAccess()));
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static List<Component> doneLines(List<Component> seen) {
		return seen.stream().filter(c -> key(c).startsWith("message.aliveworkplace.quest.done")).toList();
	}

	private static List<String> chronicleLines(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().map(Chronicle.Entry::text).filter(t -> key(t).equals("chronicle.aliveworkplace.quest")).map(Component::getString).toList();
	}

	private static Quest open(ServerLevel level, BlockPos hall, UUID id) {
		return Stories.open(level, hall).stream().filter(q -> q.id.equals(id)).findFirst().orElse(null);
	}

	/** A sentence a player reads: its key is in en_us.json and nothing raw is left in it. */
	private static void assertPlain(GameTestHelper helper, Component line, String what) {
		String text = line.getString();
		helper.assertTrue(key(line).isEmpty() || Language.getInstance().has(key(line)), what + ": its key " + key(line) + " isn't in en_us.json");
		helper.assertTrue(!text.contains("%") && !text.contains("aliveworkplace.") && !text.isBlank(), what + " isn't plain English: '" + text + "'");
	}

	// ---- B1231 --------------------------------------------------------------------------------------------------------

	/**
	 * A one-time far quest Ana finishes, then logs off. The hall's round runs while she's away (kept, unpaid), the world
	 * saves to disk, the hall's chunk unloads, and the stories are read back from the file. Ana logs in again (her own saved
	 * player data), the hall's chunk loads from disk and its round pays her once: 3 diamonds, standing, a plain-English
	 * "Quest done" line and a chronicle line naming her. The one-time quest is hers: a new copy doesn't count her kill but
	 * counts Bo's; a further round pays nothing more.
	 */
	//$ gametest_ticks_batch AREA '900' '"qa1114eOfflineDiskRestart"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qa1114eOfflineDiskRestart")
	public void aWaitingQuestSurvivesTheWorldSaveAndIsPaidOnceItsHelperIsBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 9300, 9300);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");

		var manager = level.getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
		Resource any = real.values().iterator().next();
		Map<ResourceLocation, Resource> withOurs = new HashMap<>(real);
		String json = "{\"giver\": \"hall\", \"weight\": 0, \"repeatable\": false, \"objectives\": [{\"type\": \"bring\", \"item\": \"minecraft:apple\", \"count\": 1}]}";
		withOurs.put(AliveWorkplace.id(QuestFiles.FOLDER + "/qa1114eoff/once.json"), new Resource(any.source(),
			() -> new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
		QuestFiles.load(withOurs);
		Leftovers.after(helper, () -> QuestFiles.load(real));
		ResourceLocation once = AliveWorkplace.id("qa1114eoff/once");
		helper.assertTrue(QuestFiles.get(once).isPresent() && !QuestFiles.get(once).get().repeatable(), "setup: the one-time file didn't load");

		GameProfile anaProfile = new GameProfile(UUID.randomUUID(), "off-ana");
		ServerPlayer ana = join(helper, anaProfile, new BlockPos(2, 2, 2), new ArrayList<>());
		Quest q = killQuest(level, once, DIAMOND, 3);
		Stories.post(level, hall, q);
		kill(helper, ana);
		helper.assertTrue(q.done() && anaProfile.getId().equals(q.last) && !loaded(level, hall), "setup: Ana's kill didn't finish the far quest");
		logOff(level, ana);

		VillageHallBlockEntity first = loadAndRound(level, hall, placed);
		helper.assertTrue(open(level, hall, q.id) != null && chronicleLines(first).isEmpty(),
			"the round paid or dropped the quest while Ana was offline: open " + (open(level, hall, q.id) != null) + ", chronicle " + chronicleLines(first));
		helper.assertTrue(!Stories.finished(level, hall, once), "the quest counts as finished in the village before anyone was paid");

		boolean[] back = {false};
		helper.onEachTick(() -> {
			if (back[0] || loaded(level, hall)) {
				return; // the hall's chunk unloads (saved to disk with its hall)
			}
			back[0] = true;
			saveAndReadBackFromDisk(level); // a restart: the stories as the file holds them
			Quest kept = open(level, hall, q.id);
			helper.assertTrue(kept != null && kept.done() && anaProfile.getId().equals(kept.last),
				"after a save to disk the quest waiting for Ana isn't kept, done and hers: " + (kept == null ? "gone" : "done " + kept.done() + ", last " + kept.last));

			List<Component> seen = new ArrayList<>();
			ServerPlayer again = join(helper, anaProfile, new BlockPos(2, 2, 2), seen);
			int before = again.getInventory().countItem(Items.DIAMOND);
			VillageHallBlockEntity entity = loadAndRound(level, hall, placed);
			int diamonds = again.getInventory().countItem(Items.DIAMOND) - before;
			int points = Reputation.points(level, hall, anaProfile.getId());
			helper.assertTrue(diamonds == 3 && points > 0, "back after a restart, Ana got " + diamonds + " of 3 diamonds and " + points + " standing");
			helper.assertTrue(doneLines(seen).size() == 1, "Ana got " + doneLines(seen).size() + " 'Quest done' lines: " + seen.stream().map(Component::getString).toList());
			assertPlain(helper, doneLines(seen).get(0), "the 'Quest done' line");
			helper.assertTrue(doneLines(seen).get(0).getString().startsWith("Quest done: "), "the line reads '" + doneLines(seen).get(0).getString() + "'");
			List<String> lines = chronicleLines(entity);
			helper.assertTrue(lines.size() == 1 && lines.get(0).startsWith("off-ana did a quest: "), "the chronicle says " + lines);
			helper.assertTrue(open(level, hall, q.id) == null && Stories.finished(level, hall, once), "the paid quest is still up, or not recorded as finished");

			Stories.round(level, hall, entity);
			helper.assertTrue(again.getInventory().countItem(Items.DIAMOND) - before == 3 && doneLines(seen).size() == 1 && chronicleLines(entity).size() == 1,
				"a second round paid the quest again");

			// The one-time quest is hers now: a new copy, with the hall loaded.
			Quest copy = killQuest(level, once, DIAMOND, 3);
			Stories.post(level, hall, copy);
			kill(helper, again);
			helper.assertTrue(copy.progress[0] == 0, "Ana moved a one-time quest she already did (paid while she was away) on again");
			ServerPlayer bo = join(helper, new GameProfile(UUID.randomUUID(), "off-bo"), new BlockPos(6, 2, 6), new ArrayList<>());
			kill(helper, bo);
			helper.assertTrue(copy.progress[0] == 1 || open(level, hall, copy.id) == null, "Bo, who never did it, couldn't move the one-time quest on");
			helper.succeed();
		});
	}

	/**
	 * Two far quests, finished by Ana and Bo, who both log off. Cara is online by the test at the round: both quests wait
	 * and Cara gets nothing. Bo comes back first: the next round pays him his own (5 gold), not Ana's, which still waits
	 * (through a save to disk). Ana comes back: she gets her 3 diamonds. Each is named in the chronicle; nobody is "Someone".
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1114eTwoOffline"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1114eTwoOffline")
	public void twoOfflineHelpersAreEachPaidTheirOwnQuestWhenTheyAreBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -9300, 9300);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		GameProfile anaProfile = new GameProfile(UUID.randomUUID(), "off2-ana");
		GameProfile boProfile = new GameProfile(UUID.randomUUID(), "off2-bo");
		ServerPlayer ana = join(helper, anaProfile, new BlockPos(2, 2, 2), new ArrayList<>());
		ServerPlayer bo = join(helper, boProfile, new BlockPos(6, 2, 2), new ArrayList<>());
		List<Component> caraSeen = new ArrayList<>();
		ServerPlayer cara = join(helper, new GameProfile(UUID.randomUUID(), "off2-cara"), new BlockPos(10, 2, 2), caraSeen);
		Quest qa = killQuest(level, AliveWorkplace.id("personal/qa1114eoff_a"), DIAMOND, 3);
		Quest qb = killQuest(level, AliveWorkplace.id("personal/qa1114eoff_b"), GOLD, 5);
		Stories.post(level, hall, qa);
		Stories.post(level, hall, qb);
		kill(helper, ana);
		kill(helper, bo);
		helper.assertTrue(qa.done() && qb.done() && anaProfile.getId().equals(qa.last) && boProfile.getId().equals(qb.last),
			"setup: the kills didn't finish one quest each (Ana's " + qa.done() + " last " + qa.last + ", Bo's " + qb.done() + " last " + qb.last + ")");
		logOff(level, ana);
		logOff(level, bo);

		VillageHallBlockEntity entity = loadAndRound(level, hall, placed);
		helper.assertTrue(open(level, hall, qa.id) != null && open(level, hall, qb.id) != null, "a quest of an offline helper didn't wait");
		helper.assertTrue(cara.getInventory().countItem(Items.DIAMOND) + cara.getInventory().countItem(Items.GOLD_INGOT) == 0 && doneLines(caraSeen).isEmpty(),
			"Cara, online at the round, was paid a quest she never helped with");
		helper.assertTrue(chronicleLines(entity).isEmpty(), "the round wrote " + chronicleLines(entity) + " while both helpers were offline");

		List<Component> boSeen = new ArrayList<>();
		ServerPlayer boBack = join(helper, boProfile, new BlockPos(6, 2, 2), boSeen);
		Stories.round(level, hall, entity);
		helper.assertTrue(boBack.getInventory().countItem(Items.GOLD_INGOT) == 5 && boBack.getInventory().countItem(Items.DIAMOND) == 0 && doneLines(boSeen).size() == 1,
			"Bo back got " + boBack.getInventory().countItem(Items.GOLD_INGOT) + " of his 5 gold and " + boBack.getInventory().countItem(Items.DIAMOND) + " of Ana's diamonds");
		helper.assertTrue(open(level, hall, qb.id) == null && open(level, hall, qa.id) != null, "Bo's return didn't settle his quest alone");

		saveAndReadBackFromDisk(level);
		List<Component> anaSeen = new ArrayList<>();
		ServerPlayer anaBack = join(helper, anaProfile, new BlockPos(2, 2, 2), anaSeen);
		Stories.round(level, hall, entity);
		helper.assertTrue(anaBack.getInventory().countItem(Items.DIAMOND) == 3 && doneLines(anaSeen).size() == 1 && open(level, hall, qa.id) == null,
			"Ana back after Bo (and a save) got " + anaBack.getInventory().countItem(Items.DIAMOND) + " of 3 diamonds");
		List<String> lines = chronicleLines(entity);
		helper.assertTrue(lines.size() == 2 && lines.stream().anyMatch(l -> l.startsWith("off2-bo did a quest: ")) && lines.stream().anyMatch(l -> l.startsWith("off2-ana did a quest: ")),
			"the chronicle says " + lines);
		Stories.round(level, hall, entity);
		helper.assertTrue(anaBack.getInventory().countItem(Items.DIAMOND) == 3 && boBack.getInventory().countItem(Items.GOLD_INGOT) == 5 && chronicleLines(entity).size() == 2,
			"a further round paid again");
		helper.succeed();
	}

	/**
	 * Ana finishes a far quest and logs off; the round keeps it waiting; then the hall is broken before she's back. As for
	 * any quest of a broken hall (B1220's decision), it goes: once back, with a new hall at the same spot, she is paid
	 * nothing, it isn't up and isn't recorded, and nothing crashes.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114eOfflineHallBroken"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114eOfflineHallBroken")
	public void aWaitingQuestWhoseHallIsBrokenBeforeItsHelperIsBackIsDropped(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 9300, -9300);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		GameProfile anaProfile = new GameProfile(UUID.randomUUID(), "off3-ana");
		ServerPlayer ana = join(helper, anaProfile, new BlockPos(2, 2, 2), new ArrayList<>());
		Quest q = killQuest(level, AliveWorkplace.id("personal/qa1114eoff_c"), DIAMOND, 3);
		Stories.post(level, hall, q);
		kill(helper, ana);
		helper.assertTrue(q.done(), "setup: the kill didn't count");
		logOff(level, ana);
		loadAndRound(level, hall, placed);
		helper.assertTrue(open(level, hall, q.id) != null, "setup: the quest didn't wait for Ana");

		level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3); // broken while she's away
		helper.assertTrue(Stories.open(level, hall).isEmpty(), "the broken hall's waiting quest is still kept: " + Stories.open(level, hall));
		List<Component> seen = new ArrayList<>();
		ServerPlayer back = join(helper, anaProfile, new BlockPos(2, 2, 2), seen);
		VillageHallBlockEntity entity = loadAndRound(level, hall, placed); // a new hall at the same spot
		helper.assertTrue(back.getInventory().countItem(Items.DIAMOND) == 0 && doneLines(seen).isEmpty() && chronicleLines(entity).isEmpty(),
			"a broken hall's quest was paid to Ana once back: " + back.getInventory().countItem(Items.DIAMOND) + " diamonds, " + doneLines(seen).size() + " lines");
		helper.assertTrue(!Stories.finished(level, hall, q.file) && Reputation.points(level, hall, anaProfile.getId()) == 0,
			"the new hall counts the broken hall's quest (finished " + Stories.finished(level, hall, q.file) + ", standing " + Reputation.points(level, hall, anaProfile.getId()) + ")");
		helper.succeed();
	}

	// ---- B1232 --------------------------------------------------------------------------------------------------------

	/** Sets the time of day to {@code t} today and runs the reform stories' tick. */
	private static void tickAt(ServerLevel level, long t) {
		level.setDayTime(Math.floorDiv(level.getDayTime(), 24000L) * 24000L + t);
		ReformStories.tick(level);
	}

	/** Every chunk within 2 of the hall's but its own (the reach of the drill's dummy search) that isn't loaded now. */
	private static List<ChunkPos> unloadedRing(ServerLevel level, ChunkPos c) {
		List<ChunkPos> out = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if ((dx != 0 || dz != 0) && !loaded(level, c.x + dx, c.z + dz)) {
					out.add(new ChunkPos(c.x + dx, c.z + dz));
				}
			}
		}
		return out;
	}

	private static List<Component> storyLines(VillageHallBlockEntity entity, String prefix) {
		return entity.chronicle().stream().map(Chronicle.Entry::text).filter(t -> key(t).startsWith(prefix)).toList();
	}

	/** The far hall at the edge of a view (loaded, not ticking), with its stories' cleanup. */
	private static VillageHallBlockEntity borderHall(GameTestHelper helper, ServerLevel level, BlockPos hall) {
		ChunkPos chunk = new ChunkPos(hall);
		long time = level.getDayTime();
		ReformStories.Data data = ReformStories.Data.get(level);
		Leftovers.after(helper, () -> {
			level.setDayTime(time);
			ReformStories.Entry e = data.entry(hall);
			e.drillDay = -1;
			e.drillDone = false;
			e.lamplit = false;
			e.bell = Long.MIN_VALUE;
			data.setDirty();
			level.setChunkForced(chunk.x, chunk.z, false);
			level.getChunkSource().removeRegionTicket(BORDER, chunk, 0, chunk);
			if (loaded(level, hall)) {
				level.setBlock(hall.east(), Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(hall.west(), Blocks.AIR.defaultBlockState(), 3);
				if (level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
					level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		});
		level.getChunkSource().addRegionTicket(BORDER, chunk, 0, chunk);
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		helper.assertTrue(level.isLoaded(hall) && !level.isPositionEntityTicking(hall), "setup: the hall's chunk isn't loaded-but-not-ticking");
		return (VillageHallBlockEntity) level.getBlockEntity(hall);
	}

	/**
	 * A far drill day (a Training Dummy beside the hall), the hall loaded but nobody near, run at each boundary tick: just
	 * before the drill (5999), its first tick (6000), its last (7399), its end (7400) and the next beat (7420). No chunk
	 * round the hall loads, no drill line is written and the drill isn't marked done. Then someone comes near (the chunk
	 * force-loaded, ticking) at 7380: the drill runs and at 7400 it ends with one plain-English line about the dummies.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qa1114eFarDrillBounds"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qa1114eFarDrillBounds")
	public void aFarDrillDayDoesNothingAtItsBoundaryTicksAndEndsOnceSomeoneIsNear(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 9500, -9500);
		ChunkPos chunk = new ChunkPos(hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		VillageHallBlockEntity entity = borderHall(helper, level, hall);
		level.setBlock(hall.east(), ModBlocks.TRAINING_DUMMY.defaultBlockState(), 3);
		helper.assertTrue(level.getBlockState(hall.east()).getBlock() instanceof TrainingDummyBlock, "setup: no dummy by the hall");
		List<ChunkPos> ring = unloadedRing(level, chunk);
		helper.assertTrue(ring.size() >= 20, "setup: only " + ring.size() + " of the 24 chunks round the hall are unloaded");
		ReformStories.Data data = ReformStories.Data.get(level);
		ReformStories.Entry e = data.entry(hall);
		e.drillDay = Chronicle.day(level);
		e.drillDone = false;
		data.setDirty();

		for (long t : new long[]{ReformStories.DRILL_START - 1, ReformStories.DRILL_START, ReformStories.DRILL_END - 1, ReformStories.DRILL_END, ReformStories.DRILL_END + 20}) {
			tickAt(level, t);
			List<ChunkPos> nowLoaded = ring.stream().filter(c -> loaded(level, c.x, c.z)).toList();
			helper.assertTrue(nowLoaded.isEmpty(), "at time " + t + " the far drill loaded the chunks " + nowLoaded + " round a hall nobody is near");
			helper.assertTrue(!e.drillDone && storyLines(entity, "chronicle.aliveworkplace.reform_story.drilled").isEmpty(),
				"at time " + t + " a drill nobody was near was ended and written down: " + storyLines(entity, "chronicle.aliveworkplace.reform_story.drilled").stream().map(Component::getString).toList());
		}

		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] == 1) {
				level.setDayTime(Math.floorDiv(level.getDayTime(), 24000L) * 24000L + 100); // away from the drill while it loads
				level.setChunkForced(chunk.x, chunk.z, true);
				FarChunks.awaitEntityTicking(helper, hall); // by the clock: it waits for world generation (B1242)
				return;
			}
			helper.assertTrue(!e.drillDone, "the drill ended before its time once someone was near");
			tickAt(level, ReformStories.DRILL_END - 20);
			helper.assertTrue(!e.drillDone && storyLines(entity, "chronicle.aliveworkplace.reform_story.drilled").isEmpty(), "the drill ended at its last beat");
			tickAt(level, ReformStories.DRILL_END);
			tickAt(level, ReformStories.DRILL_END + 20);
			List<Component> lines = storyLines(entity, "chronicle.aliveworkplace.reform_story.drilled");
			helper.assertTrue(e.drillDone && lines.size() == 1, "once someone was near, the drill's end wrote " + lines.size() + " lines (done " + e.drillDone + ")");
			helper.assertTrue(!key(lines.get(0)).startsWith("chronicle.aliveworkplace.reform_story.drilled_hall"),
				"with a dummy beside the hall the line says they drilled by the hall: '" + lines.get(0).getString() + "'");
			assertPlain(helper, lines.get(0), "the drill line");
			helper.assertTrue(lines.get(0).getString().startsWith("Drill day in ") && lines.get(0).getString().endsWith("at the dummies."),
				"the drill line reads '" + lines.get(0).getString() + "'");
			helper.succeed();
		});
	}

	/**
	 * A far village's Shift Bell, its hall nobody is near: at the start and end of the work day (2000, 2019, 9000, 9019) it
	 * stays silent. Once someone is near it rings at 2000 and at 9000, and not at 2020.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qa1114eFarBellBounds"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qa1114eFarBellBounds")
	public void aFarShiftBellIsSilentAtItsRingTicksAndRingsOnceSomeoneIsNear(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -9500, 9500);
		ChunkPos chunk = new ChunkPos(hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		borderHall(helper, level, hall);
		BlockPos bell = hall.west();
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
		helper.assertTrue(level.getBlockEntity(bell) instanceof BellBlockEntity, "setup: no bell");
		ReformStories.Data data = ReformStories.Data.get(level);
		ReformStories.Entry e = data.entry(hall);
		e.bell = bell.asLong();
		data.setDirty();

		for (long t : new long[]{ReformStories.RING_START, ReformStories.RING_START + 19, ReformStories.RING_END, ReformStories.RING_END + 19}) {
			tickAt(level, t);
			helper.assertTrue(!((BellBlockEntity) level.getBlockEntity(bell)).shaking, "at time " + t + " the Shift Bell of a village nobody is near rang");
		}

		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] == 1) {
				level.setDayTime(Math.floorDiv(level.getDayTime(), 24000L) * 24000L + 100);
				level.setChunkForced(chunk.x, chunk.z, true);
				FarChunks.awaitEntityTicking(helper, hall); // by the clock: it waits for world generation (B1242)
				return;
			}
			BellBlockEntity be = (BellBlockEntity) level.getBlockEntity(bell);
			be.shaking = false;
			tickAt(level, ReformStories.RING_START + 20);
			helper.assertTrue(!be.shaking, "the bell rang at 2020, after its start-of-day ring");
			tickAt(level, ReformStories.RING_START);
			helper.assertTrue(be.shaking, "once someone was near, the Shift Bell didn't ring at 2000");
			be.shaking = false;
			tickAt(level, ReformStories.RING_END);
			helper.assertTrue(be.shaking, "once someone was near, the Shift Bell didn't ring at 9000");
			helper.succeed();
		});
	}

	/**
	 * A far village's first lamplit night, its hall nobody is near: at 13000 and 22999 no line is written and it still
	 * waits. Once someone is near, 12999 writes nothing and 13000 writes one plain-English line, once.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qa1114eFarLamplit"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qa1114eFarLamplit")
	public void aFarLamplitNightWaitsForANightSomeoneIsNear(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 9500, 9500);
		ChunkPos chunk = new ChunkPos(hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		VillageHallBlockEntity entity = borderHall(helper, level, hall);
		ReformStories.Data data = ReformStories.Data.get(level);
		ReformStories.Entry e = data.entry(hall);
		e.lamplit = true;
		data.setDirty();
		String lamplit = "chronicle.aliveworkplace.reform_story.lamplit";

		for (long t : new long[]{ReformStories.NIGHT, 22999}) {
			tickAt(level, t);
			helper.assertTrue(e.lamplit && storyLines(entity, lamplit).isEmpty(), "at time " + t + " the lamplit line was written for a village nobody is near");
		}

		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] == 1) {
				level.setDayTime(Math.floorDiv(level.getDayTime(), 24000L) * 24000L + 100);
				level.setChunkForced(chunk.x, chunk.z, true);
				FarChunks.awaitEntityTicking(helper, hall); // by the clock: it waits for world generation (B1242)
				return;
			}
			tickAt(level, ReformStories.NIGHT - 1);
			helper.assertTrue(e.lamplit && storyLines(entity, lamplit).isEmpty(), "the lamplit line came before night (12999)");
			tickAt(level, ReformStories.NIGHT);
			tickAt(level, ReformStories.NIGHT + 20);
			List<Component> lines = storyLines(entity, lamplit);
			helper.assertTrue(!e.lamplit && lines.size() == 1, "once someone was near, the lamplit night wrote " + lines.size() + " lines");
			assertPlain(helper, lines.get(0), "the lamplit line");
			helper.assertTrue(lines.get(0).getString().startsWith("The first lamplit night in ") && lines.get(0).getString().endsWith("nobody hurried home."),
				"the lamplit line reads '" + lines.get(0).getString() + "'");
			helper.succeed();
		});
	}
}
