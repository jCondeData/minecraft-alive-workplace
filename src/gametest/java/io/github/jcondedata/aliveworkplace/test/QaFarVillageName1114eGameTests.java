package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;

/**
 * QA (qa-1114e), B1217 from the spec, with saves and old saves in mind: {@code /workplace quests} and a letter name a far
 * village without loading its hall: a loaded hall its own name, an unloaded one the name its last round saved on the
 * caravan list, else the made-up name. A renamed village keeps its name word for word through a save and load of the
 * caravan list; an old or broken entry on the list never leaves the line without a name a player can read.
 */
public class QaFarVillageName1114eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** {@code Caravans.Data}'s saved-data name. */
	static final String CARAVANS = "aliveworkplace_caravans";

	/** A spot far from every test area and player (no other test uses z + 9200), so its chunk is never loaded unless something loads it. */
	static BlockPos far(GameTestHelper helper, int dx) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + 9200);
	}

	static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	static Quest request(ServerLevel level, Objectives.Objective objective) {
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114e"), "villager", Optional.empty(), "Lia", now, now + 24000L * 5,
			List.of(objective), new int[1], List.of());
		q.villager = UUID.randomUUID();
		return q;
	}

	/** A connected player whose chat lines land in {@code seen}, near this test's area (far from the far hall). */
	static ServerPlayer player(GameTestHelper helper, List<Component> seen, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			QuestTracker.untrack(player);
			level.getServer().getPlayerList().remove(player);
		});
		return player;
	}

	/** Posts a bring-bread request at the far {@code hall}, the player helping and tracking it. */
	static Quest track(GameTestHelper helper, ServerPlayer me, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		Quest quest = request(level, new Objectives.Bring("minecraft:bread", 3, "hall", Optional.empty()));
		quest.helpers.put(me.getUUID(), 0);
		Stories.post(level, hall, quest);
		helper.assertTrue(QuestJournal.trackById(me, quest.id), "tracking the far request by id failed");
		return quest;
	}

	/** The quest line {@code /workplace quests} (typed by the player) prints. */
	static Component questLine(GameTestHelper helper, ServerPlayer me, List<Component> seen) {
		seen.clear();
		helper.getLevel().getServer().getCommands().performPrefixedCommand(me.createCommandSourceStack(), "workplace quests");
		Component line = seen.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("command.aliveworkplace.quests.line"))
			.findFirst().orElse(null);
		helper.assertTrue(line != null, "/workplace quests printed no quest line: " + seen.stream().map(Component::getString).toList());
		return line;
	}

	/** The village argument of a quest line. */
	static Component village(Component line) {
		return (Component) ((TranslatableContents) line.getContents()).getArgs()[1];
	}

	/** The usual setup: a clean area and the far hall gone from the stories and the caravan list afterwards. */
	static void setUp(GameTestHelper helper, BlockPos hall) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			Stories.Data.get(level).remove(hall);
			Caravans.Data.get(level).remove(hall);
		});
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
	}

	/**
	 * The caravan list as the server would read it back from disk, after {@code edit} changed the saved tag; it takes
	 * the live list's place until the test ends.
	 */
	static Caravans.Data reload(GameTestHelper helper, Consumer<CompoundTag> edit) {
		ServerLevel level = helper.getLevel();
		Caravans.Data live = Caravans.Data.get(level);
		CompoundTag tag = live.save(new CompoundTag(), level.registryAccess());
		edit.accept(tag);
		Caravans.Data read = Caravans.Data.load(tag, level.registryAccess());
		level.getDataStorage().set(CARAVANS, read);
		Leftovers.after(helper, () -> level.getDataStorage().set(CARAVANS, live));
		helper.assertTrue(Caravans.Data.get(level) == read, "setup: the reloaded caravan list didn't take the live one's place");
		return read;
	}

	/** The saved village entry for {@code hall} in a caravan-list tag. */
	static CompoundTag entry(CompoundTag tag, BlockPos hall) {
		ListTag list = tag.getList("villages", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			if (list.getCompound(i).getLong("hall") == hall.asLong()) {
				return list.getCompound(i);
			}
		}
		throw new AssertionError("no saved entry for " + hall);
	}

	/**
	 * Case 1: a village a player renamed, its round has put the name on the caravan list, and its hall's chunk is not
	 * loaded: {@code /workplace quests} names it by that name (not the made-up one), and the chunk is still not loaded.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114eRenamedFarVillage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114eRenamedFarVillage")
	public void aRenamedFarVillageIsListedByItsOwnName(GameTestHelper helper) {
		BlockPos hall = far(helper, 0);
		setUp(helper, hall);
		ServerLevel level = helper.getLevel();
		Component renamed = Component.literal("Willowmere Hollow");
		helper.assertTrue(!VillageHalls.madeUpName(hall).getString().equals(renamed.getString()), "setup: the made-up name is the custom one");
		Caravans.Data.get(level).setWants(hall, renamed, List.of()); // what the hall's round writes (Caravans.round)
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = player(helper, seen, "qa1114e-renamed");
		track(helper, me, hall);
		Component line = questLine(helper, me, seen);
		helper.assertTrue(village(line).getString().equals("Willowmere Hollow") && line.getString().contains(" (Willowmere Hollow): "),
			"the line doesn't name the renamed village: " + line.getString());
		helper.assertTrue(!loaded(level, hall), "/workplace quests loaded the far renamed village's hall chunk");
		helper.succeed();
	}

	/**
	 * Case 1, the letter: a helper who lost the reply gets it written again by the relative, far from the giver's
	 * renamed village: the reply says it is for the poster in the village's custom name, and the far hall stays unloaded.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114eRenamedFarLetter"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114eRenamedFarLetter")
	public void aReplyWrittenAgainNamesTheRenamedFarVillage(GameTestHelper helper) {
		BlockPos hall = far(helper, 400);
		setUp(helper, hall);
		ServerLevel level = helper.getLevel();
		Caravans.Data.get(level).setWants(hall, Component.literal("Willowmere Hollow"), List.of());
		Villager hal = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Hal");
		ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
		RequestErrands.Deliver deliver = new RequestErrands.Deliver(Optional.empty(), Optional.of(hal.getUUID()), "Hal", "Ashford", "brother");
		Quest quest = request(level, deliver);
		quest.villager = UUID.randomUUID();
		quest.helpers.put(me.getUUID(), 0);
		quest.progress[0] = 1; // the letter was delivered; the reply is on its way back
		Stories.post(level, hall, quest);
		helper.assertTrue(RequestErrands.use(me, level, InteractionHand.MAIN_HAND, hal), "the lost reply wasn't written again");
		ItemStack reply = RequestErrands.isCarried(me.getMainHandItem()) ? me.getMainHandItem()
			: me.getInventory().items.stream().filter(RequestErrands::isCarried).findFirst().orElse(ItemStack.EMPTY);
		ItemLore lore = reply.get(DataComponents.LORE);
		List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
		helper.assertTrue(lines.contains("For Lia in Willowmere Hollow"), "the reply's lines: " + lines);
		helper.assertTrue(!loaded(level, hall), "writing the reply again loaded the giver's far hall chunk");
		me.discard();
		helper.succeed();
	}

	/**
	 * Case 2: the caravan list saved and read back (a server restart): the renamed village's name survives word for
	 * word, with its color and italics (an anvil name), and the line names it by it with the hall still unloaded.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114eRenamedAfterReload"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114eRenamedAfterReload")
	public void aRenamedFarVillageKeepsItsNameThroughASaveAndLoad(GameTestHelper helper) {
		BlockPos hall = far(helper, 800);
		setUp(helper, hall);
		ServerLevel level = helper.getLevel();
		Component renamed = Component.literal("Saint-Brieuc d'en Haut été — \"Old\" Port #2").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
		Caravans.Data.get(level).setWants(hall, renamed, List.of());
		Caravans.Data read = reload(helper, tag -> { });
		Caravans.Village back = read.village(hall);
		helper.assertTrue(back != null, "the renamed village is gone from the caravan list after a save and load");
		helper.assertTrue(back.name().equals(renamed), "the saved name changed in the round trip: " + back.name() + " (was " + renamed + ")");
		helper.assertTrue(VillageHalls.nameIfLoaded(level, hall).equals(renamed), "nameIfLoaded after the reload: " + VillageHalls.nameIfLoaded(level, hall));
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = player(helper, seen, "qa1114e-reload");
		track(helper, me, hall);
		Component line = questLine(helper, me, seen);
		helper.assertTrue(village(line).getString().equals(renamed.getString()), "the line after the reload: " + line.getString());
		helper.assertTrue(!loaded(level, hall), "/workplace quests loaded the far hall's chunk after the reload");
		helper.succeed();
	}

	/**
	 * Case 3: an old or broken caravan-list entry: no "name" key, or a name that isn't text JSON. Loading the list
	 * doesn't fail, and the far village's line still names it with a name a player can read (the made-up one, since
	 * nothing usable was saved), with the hall unloaded.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114eOldSaveNoName"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114eOldSaveNoName")
	public void anOldSaveEntryWithoutANameStillNamesTheVillage(GameTestHelper helper) {
		BlockPos hall = far(helper, 1200);
		BlockPos broken = far(helper, 1600);
		setUp(helper, hall);
		Leftovers.after(helper, () -> {
			Stories.Data.get(helper.getLevel()).remove(broken);
			Caravans.Data.get(helper.getLevel()).remove(broken);
		});
		ServerLevel level = helper.getLevel();
		Caravans.Data.get(level).setWants(hall, Component.literal("Willowmere Hollow"), List.of());
		Caravans.Data.get(level).setWants(broken, Component.literal("Ferncombe"), List.of());
		reload(helper, tag -> {
			entry(tag, hall).remove("name");
			entry(tag, broken).putString("name", "{\"text\":\"Ferncombe\""); // cut off mid-write
		});
		for (BlockPos h : List.of(hall, broken)) {
			Component name = VillageHalls.nameIfLoaded(level, h);
			helper.assertTrue(!name.getString().isBlank(), "a far village with an old or broken saved name has no name: '" + name.getString() + "'");
			helper.assertTrue(name.getString().equals(VillageHalls.madeUpName(h).getString()),
				"with nothing usable saved, the far village isn't called by its made-up name: " + name.getString());
		}
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = player(helper, seen, "qa1114e-oldsave");
		track(helper, me, hall);
		Component line = questLine(helper, me, seen);
		String made = VillageHalls.madeUpName(hall).getString();
		helper.assertTrue(village(line).getString().equals(made) && !line.getString().contains("()"), "the line for the old-save village: " + line.getString());
		helper.assertTrue(!loaded(level, hall) && !loaded(level, broken), "naming an old-save far village loaded its hall chunk");
		helper.succeed();
	}

	/** Case 4: a far village never on the caravan list (its round never ran): the made-up name, its hall unloaded. */
	//$ gametest_ticks_batch AREA '40' '"qa1114eNeverListed"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114eNeverListed")
	public void aFarVillageNeverOnTheCaravanListGetsItsMadeUpName(GameTestHelper helper) {
		BlockPos hall = far(helper, 2000);
		setUp(helper, hall);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Caravans.Data.get(level).village(hall) == null, "setup: the far village is already on the caravan list");
		helper.assertTrue(VillageHalls.nameIfLoaded(level, hall).equals(VillageHalls.madeUpName(hall)), "nameIfLoaded: " + VillageHalls.nameIfLoaded(level, hall));
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = player(helper, seen, "qa1114e-never");
		track(helper, me, hall);
		Component line = questLine(helper, me, seen);
		String made = VillageHalls.madeUpName(hall).getString();
		helper.assertTrue(!made.isBlank() && village(line).getString().equals(made) && line.getString().contains(" (" + made + "): "),
			"the line for a never-listed far village: " + line.getString() + " (made-up name " + made + ")");
		helper.assertTrue(!loaded(level, hall), "/workplace quests loaded a never-listed far village's hall chunk");
		helper.succeed();
	}
}
