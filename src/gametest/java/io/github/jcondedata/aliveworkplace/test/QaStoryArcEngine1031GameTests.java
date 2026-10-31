package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31) of the story arc engine (ROADMAP 31.4), written from the spec: the cooldown's edge (day 7 against day
 * 8), {@code arcsAtOnce} across villages with side arcs beside the main one and not counted, a spawned mob waiting for
 * a player and coming with the 40-tick check, a far {@code place} never over anything but natural ground and built once
 * across a reload, {@code announce} only to the players in and near the village, the {@code any} and named-list endings of a chapter, the effects toolbox ({@code flag}, {@code festival},
 * {@code start_quest}, {@code chronicle}) and the operators' commands' edges.
 */
public class QaStoryArcEngine1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(24, 2, 24);
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");
	private static final ResourceLocation FAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/far_hut");
	private static final ResourceLocation TALK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/talk");
	private static final long DAY = VillageNeeds.DAY;

	// ---- helpers (as StoryArcGameTests) ----------------------------------------------------------------------------

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		int near = Arcs.NEAR;
		boolean enabled = Arcs.ENABLED;
		Set<String> disabled = Arcs.DISABLED;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.NEAR = near;
			Arcs.ENABLED = enabled;
			Arcs.DISABLED = disabled;
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static Arcs.Arc arc(GameTestHelper helper, ResourceLocation id) {
		Arcs.Arc arc = Arcs.get(id).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc " + id + " didn't load");
		return arc;
	}

	/** An arc written here (chance 0, so no other test's roll ever starts it), added as a data pack would. */
	private static Arcs.Arc inline(String path, String json) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/" + path);
		Arcs.Arc arc = Arcs.read(id, JsonParser.parseString(json).getAsJsonObject());
		Arcs.add(arc);
		return arc;
	}

	/** Every loaded arc but {@code keep} switched off, so a roll can only pick that one. */
	private static void only(ResourceLocation keep) {
		Arcs.DISABLED = Arcs.all().stream().map(a -> a.id().toString()).filter(s -> !s.equals(keep.toString())).collect(Collectors.toSet());
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	/** A logged-in player whose chat lines are kept in {@code heard}. */
	private static ServerPlayer listener(GameTestHelper helper, String name, List<String> heard) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				if (!overlay) {
					heard.add(message.getString());
				}
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, p, cookie);
		Leftovers.after(helper, () -> server.getPlayerList().remove(p));
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static List<Zombie> grubbs(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Zombie.class, new AABB(hall).inflate(24), z -> z.isAlive() && z.getTags().contains(ArcEffects.roleTag("grubb")));
	}

	private static void toGrubb(ServerLevel level, BlockPos hall) {
		while (Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter < 2) {
			Arcs.next(level, hall);
		}
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** Hands in the Story tab's quest shown with {@code item} (a bring), as the player's click does. */
	private static void handIn(GameTestHelper helper, ServerLevel level, BlockPos hall, ServerPlayer player, net.minecraft.world.item.Item item) {
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		for (int slot : QuestJournal.STORY_QUEST_SLOTS) {
			if (menu.icon(slot).is(item)) {
				menu.press(slot, player);
				return;
			}
		}
		helper.fail("no quest for " + item + " on the Story tab");
	}

	// ---- the tests -------------------------------------------------------------------------------------------------

	/**
	 * {@code arcCooldownDays} 8 between two arcs in one village: the arc that ended on day D keeps the next away on day
	 * D+7 and lets it start on day D+8 (the chance roll is certain, every other arc switched off).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaArcCooldownEdge1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaArcCooldownEdge1031")
	public void qaTheCooldownHoldsOnDaySevenAndEndsOnDayEight(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.COOLDOWN_DAYS = 8;
		Arcs.AT_ONCE = Arcs.runningCount(level.getServer()) + 5;
		only(THREE);
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		helper.assertTrue(Arcs.start(level, hall, arc(helper, THREE)) != null, "the first arc didn't start");
		helper.assertTrue(Arcs.stop(level, hall) && Arcs.lastArcDay(level, hall) == day, "the cooldown's day: " + Arcs.lastArcDay(level, hall) + " vs " + day);
		setDay(level, day + 7);
		helper.assertTrue(Arcs.tryStart(level, hall, RandomSource.create(11), 1) == null && Arcs.running(level, hall) == null,
			"an arc started on the 7th day after the last (arcCooldownDays 8)");
		setDay(level, day + 8);
		ArcState s = Arcs.tryStart(level, hall, RandomSource.create(11), 1);
		helper.assertTrue(s != null && Arcs.running(level, hall) == s && s.id.equals(THREE.toString()),
			"no arc started on the 8th day after the last: " + (s == null ? "none" : s.id));
		helper.succeed();
	}

	/**
	 * {@code arcsAtOnce} counts the main arcs of every village on the server: one running in village A fills a cap of
	 * one, so village B's roll starts nothing. A side arc runs beside A's main one, isn't counted, shows on the Story tab
	 * as "Also unfolding", and with {@code storyArcs} off both end at the village's next round.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaArcsAtOnceSide1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaArcsAtOnceSide1031")
	public void qaArcsAtOnceCountsEveryVillageButNotSideArcs(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		hall(helper, OTHER_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(OTHER_HALL);
		Arcs.Arc side = inline("side_tale", """
			{"name": "The QA Side Tale", "side": true, "trigger": {"chance": 0},
			 "chapters": [{"name": "Beside it", "needs_flags": ["never_set"]}]}""");
		setDay(level, Chronicle.day(level) + 1);
		Arcs.COOLDOWN_DAYS = 0;
		int before = Arcs.runningCount(level.getServer());
		ArcState main = Arcs.start(level, a, arc(helper, THREE));
		ArcState beside = Arcs.start(level, a, side);
		only(THREE); // (B's rolls below can only pick the test arc)
		helper.assertTrue(main != null && beside != null, "main " + main + " side " + beside);
		helper.assertTrue(Arcs.running(level, a) == main && Arcs.side(level, a).equals(List.of(beside)), "the side arc took the main arc's place");
		helper.assertTrue(Arcs.runningCount(level.getServer()) == before + 1, "a side arc was counted: " + Arcs.runningCount(level.getServer()) + " vs " + (before + 1));
		Arcs.AT_ONCE = before + 1;
		helper.assertTrue(Arcs.tryStart(level, b, RandomSource.create(5), 1) == null && Arcs.running(level, b) == null,
			"village B started one over the server's cap of " + Arcs.AT_ONCE + " (A's counts)");
		Arcs.AT_ONCE = before + 2;
		helper.assertTrue(Arcs.tryStart(level, b, RandomSource.create(5), 1) != null, "B didn't start with room under the cap");
		Arcs.stop(level, b);

		ServerPlayer player = player(helper, HALL.south(2));
		ChoiceMenu tab = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, a, player, QuestJournal.Tab.STORY));
		helper.assertTrue(lore(tab.icon(QuestJournal.CHAPTER_ROW)).contains("Also unfolding: The QA Side Tale"),
			"the Story tab doesn't show the side arc: " + lore(tab.icon(QuestJournal.CHAPTER_ROW)));

		Arcs.ENABLED = false;
		Arcs.round(level, a, entity);
		helper.assertTrue(Arcs.running(level, a) == null && Arcs.side(level, a).isEmpty(), "storyArcs off left " + Arcs.running(level, a) + " / " + Arcs.side(level, a));
		helper.succeed();
	}

	/**
	 * A spawned mob comes only once a player is near its spot, by the server's own 40-tick check (no check called by
	 * hand): a check passes with nobody near and Grubb isn't there; a player walks up and he is, by the next check.
	 */
	//$ gametest_ticks_batch AREA '140' '"qaSpawnWaitsForAPlayer1031"'
	@GameTest(template = AREA, timeoutTicks = 140, batch = "qaSpawnWaitsForAPlayer1031")
	public void qaASpawnedMobWaitsForAPlayerAndComesWithTheFortyTickCheck(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.NEAR = 6; // (Grubb's spot is 4,4 from the hall; the player waits 20 blocks off, outside it)
		ServerPlayer player = player(helper, new BlockPos(26, 2, 26));
		setDay(level, Chronicle.day(level) + 1);
		Arcs.start(level, hall, arc(helper, THREE));
		toGrubb(level, hall);
		ArcState s = Arcs.running(level, hall);
		helper.assertTrue(s != null && s.chapter == 2 && s.mobs.size() == 1, "not at Grubb's chapter");
		helper.runAfterDelay(Arcs.CHECK_EVERY + 5, () -> {
			helper.assertTrue(grubbs(level, hall).isEmpty() && s.mobs.get(0).uuid == null, "Grubb came with nobody near: " + grubbs(level, hall));
			BlockPos near = hall.offset(4, 0, 2);
			player.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		});
		helper.runAfterDelay(2 * Arcs.CHECK_EVERY + 10, () -> {
			List<Zombie> g = grubbs(level, hall);
			helper.assertTrue(g.size() == 1 && g.get(0).getUUID().equals(s.mobs.get(0).uuid), "Grubb didn't come within a check of the player: " + g);
			helper.succeed();
		});
	}

	/**
	 * A far {@code place} is never built over anything but natural ground: the spot it picked is paved with planks
	 * before a player comes; once one does, the hut goes up off the planks (which stay), once. The waiting place and
	 * the built one both come through a round trip of {@code aliveworkplace_stories}, and the reloaded one is never
	 * built again.
	 */
	//$ gametest_ticks_batch AREA '240' '"qaPlaceNaturalGround1031"'
	@GameTest(template = AREA, timeoutTicks = 240, batch = "qaPlaceNaturalGround1031")
	public void qaAFarPlaceIsNeverBuiltOverPavingAndOnlyOnceAcrossAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.NEAR = 6;
		ServerPlayer player = player(helper, HALL.south(1));
		setDay(level, Chronicle.day(level) + 1);
		int placed = ArcEffects.PLACED.get();
		ArcState s0 = Arcs.start(level, hall, arc(helper, FAR));
		helper.assertTrue(s0 != null && s0.places.size() == 1 && s0.places.get(0).waiting(), "the hut isn't waiting");
		BlockPos spot = s0.places.get(0).spot;
		int groundY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot).getY() - 1;
		List<BlockPos> paving = new ArrayList<>();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				BlockPos q = new BlockPos(spot.getX() + dx, groundY, spot.getZ() + dz);
				paving.add(q);
				level.setBlock(q, Blocks.OAK_PLANKS.defaultBlockState(), 2);
			}
		}
		BoundingBox[] box = new BoundingBox[1];
		Leftovers.after(helper, () -> {
			for (BlockPos q : paving) {
				level.setBlock(q, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			}
			if (box[0] != null) {
				for (BlockPos q : BlockPos.betweenClosed(box[0].minX(), box[0].minY(), box[0].minZ(), box[0].maxX(), box[0].maxY() + 1, box[0].maxZ())) {
					level.setBlock(q, q.getY() == box[0].minY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		});
		// The waiting place through a save and load.
		Stories.Data copy = Stories.Data.load(Stories.Data.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set("aliveworkplace_stories", copy);
		ArcState s = Arcs.running(level, hall);
		helper.assertTrue(s != null && s.places.size() == 1 && s.places.get(0).waiting() && spot.equals(s.places.get(0).spot),
			"the waiting hut didn't come through the reload: " + (s == null ? "no arc" : s.places));
		BlockPos near = spot.offset(0, 0, -5);
		player.teleportTo(near.getX() + 0.5, groundY + 1, near.getZ() + 0.5);
		helper.runAfterDelay(2 * Arcs.CHECK_EVERY + 10, () -> {
			ArcState.ArcPlace hut = s.places.get(0);
			helper.assertTrue(ArcEffects.PLACED.get() == placed + 1 && hut.origin != null, "built " + (ArcEffects.PLACED.get() - placed) + " times, origin " + hut.origin);
			StructureTemplate template = level.getStructureManager().get(ResourceLocation.parse(hut.stages.get(0))).orElseThrow();
			Rotation rotation = Rotation.values()[Math.floorMod(hut.rotation, 4)];
			box[0] = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE), hut.origin);
			for (BlockPos q : paving) {
				helper.assertTrue(level.getBlockState(q).is(Blocks.OAK_PLANKS), "the paving at " + q + " became " + level.getBlockState(q));
				helper.assertTrue(!(q.getX() >= box[0].minX() && q.getX() <= box[0].maxX() && q.getZ() >= box[0].minZ() && q.getZ() <= box[0].maxZ()),
					"the hut " + box[0] + " was built over the paving at " + q);
			}
			// The built place through a save and load: never built again.
			Stories.Data again = Stories.Data.load(Stories.Data.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			level.getDataStorage().set("aliveworkplace_stories", again);
			ArcState r = Arcs.running(level, hall);
			helper.assertTrue(r != null && !r.places.get(0).waiting() && hut.origin.equals(r.places.get(0).origin), "the built hut didn't come through the reload");
			for (int i = 0; i < 3; i++) {
				ArcEffects.check(level);
			}
			helper.assertTrue(ArcEffects.PLACED.get() == placed + 1, "built again after the reload");
			helper.succeed();
		});
	}

	/**
	 * {@code announce} and a chapter's intro reach the players in and near the village, and not a player far away.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaAnnounceAudience1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaAnnounceAudience1031")
	public void qaAnAnnouncementReachesOnlyThePlayersInAndNearTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.Arc herald = inline("herald", """
			{"name": "The QA Herald", "trigger": {"chance": 0},
			 "chapters": [{"name": "The crier", "intro": [{"text": "A crier walks the lanes."}],
			   "on_start": [{"type": "announce", "text": "Hear ye, the QA fair is open!"}], "needs_flags": ["never_set"]}]}""");
		List<String> nearHeard = new ArrayList<>();
		List<String> farHeard = new ArrayList<>();
		ServerPlayer near = listener(helper, "qaNear", nearHeard);
		ServerPlayer far = listener(helper, "qaFar", farHeard);
		near.teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		far.teleportTo(hall.getX() + 400.5, hall.getY(), hall.getZ() + 0.5);
		nearHeard.clear();
		farHeard.clear();
		setDay(level, Chronicle.day(level) + 1);
		helper.assertTrue(Arcs.start(level, hall, herald) != null, "didn't start");
		helper.assertTrue(nearHeard.contains("The QA Herald, chapter 1: The crier") && nearHeard.contains("A crier walks the lanes.")
			&& nearHeard.contains("Hear ye, the QA fair is open!"), "the near player heard: " + nearHeard);
		helper.assertTrue(farHeard.stream().noneMatch(l -> l.contains("QA")  || l.contains("crier")), "a player 400 blocks off heard: " + farHeard);
		helper.succeed();
	}

	/**
	 * A chapter done when {@code any} of its quests is: one brought ends it and its other quest comes down. A chapter
	 * done with the quests it names: the unnamed one brought doesn't end it, the named one does, and the arc ends.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaChapterCompleteModes1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaChapterCompleteModes1031")
	public void qaAChapterEndsOnAnyQuestOrOnlyOnTheQuestsItNames(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.Arc arc = inline("complete_modes", """
			{"name": "The QA Choices", "trigger": {"chance": 0},
			 "chapters": [
			   {"name": "Either one", "complete": "any", "quests": [
			     {"name": "QA apples", "objectives": [{"type": "bring", "item": "minecraft:apple", "count": 2}]},
			     {"name": "QA bread", "objectives": [{"type": "bring", "item": "minecraft:bread", "count": 2}]}]},
			   {"name": "The named one", "complete": ["aliveworkplace_test:qa/named_carrots"], "quests": [
			     {"name": "QA melons", "objectives": [{"type": "bring", "item": "minecraft:melon_slice", "count": 2}]},
			     {"id": "aliveworkplace_test:qa/named_carrots", "name": "QA carrots", "objectives": [{"type": "bring", "item": "minecraft:carrot", "count": 2}]}]}
			 ]}""");
		ServerPlayer player = player(helper, HALL.south(2));
		for (var item : List.of(Items.APPLE, Items.BREAD, Items.MELON_SLICE, Items.CARROT)) {
			player.getInventory().add(new ItemStack(item, 16));
		}
		setDay(level, Chronicle.day(level) + 1);
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && s.chapter == 0 && arcQuests(level, hall).size() == 2, "chapter 1's two quests: " + arcQuests(level, hall));
		handIn(helper, level, hall, player, Items.BREAD);
		helper.assertTrue(Arcs.running(level, hall) == s && s.chapter == 1 && s.started, "one of 'any' didn't end chapter 1: chapter " + s.chapter);
		List<String> names = arcQuests(level, hall).stream().map(q -> q.name.map(Component::getString).orElse("")).toList();
		helper.assertTrue(names.size() == 2 && !names.contains("QA apples"), "chapter 1's other quest stayed up, or chapter 2's didn't go up: " + names);
		handIn(helper, level, hall, player, Items.MELON_SLICE);
		helper.assertTrue(Arcs.running(level, hall) == s && s.chapter == 1, "the quest the chapter doesn't name ended it");
		handIn(helper, level, hall, player, Items.CARROT);
		helper.assertTrue(Arcs.running(level, hall) == null, "the named quest didn't end the chapter (and the arc)");
		helper.succeed();
	}

	/**
	 * The effects toolbox on a chapter's start: {@code flag} for two days (today and tomorrow, then gone; the village
	 * keeps it too), {@code festival} the next day, {@code start_quest} written inline, and {@code chronicle}.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaArcEffectsToolbox1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaArcEffectsToolbox1031")
	public void qaTheEffectsToolboxFlagsAFestivalAQuestAndTheChronicle(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true;
		Leftovers.after(helper, () -> Festivals.ENABLED = festivals);
		Arcs.Arc arc = inline("toolbox", """
			{"name": "The QA Toolbox", "trigger": {"chance": 0},
			 "chapters": [{"name": "Tools", "needs_flags": ["never_set"], "on_start": [
			   {"type": "flag", "flag": "qa_bunting", "days": 2},
			   {"type": "festival"},
			   {"type": "start_quest", "quest": {"name": "QA extra errand", "objectives": [{"type": "bring", "item": "minecraft:wheat", "count": 3}]}},
			   {"type": "chronicle", "line": "The QA toolbox was opened."}]}]}""");
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null, "didn't start");
		helper.assertTrue(s.flag("qa_bunting", day) && s.flag("qa_bunting", day + 1) && !s.flag("qa_bunting", day + 2),
			"a two-day flag: " + s.flags);
		helper.assertTrue(Stories.villageFlag(level, hall, "qa_bunting"), "the village doesn't keep the flag");
		helper.assertTrue(entity.festivalDay() == day + 1, "the festival isn't the next day: " + entity.festivalDay() + " vs " + (day + 1));
		List<String> quests = arcQuests(level, hall).stream().map(q -> q.name.map(Component::getString).orElse("")).toList();
		helper.assertTrue(quests.equals(List.of("QA extra errand")), "start_quest: " + quests);
		List<String> told = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
		helper.assertTrue(told.contains("The QA toolbox was opened."), "the chronicle: " + told);
		setDay(level, day + 2);
		helper.assertTrue(!Stories.villageFlag(level, hall, "qa_bunting"), "the village's two-day flag outlived its two days");
		helper.succeed();
	}

	/**
	 * The operators' commands' edges: a player who isn't an operator can't use them; {@code start} takes an arc's path
	 * alone; {@code next} on the last chapter ends the tale ("... is over."); {@code stop} with none running says so; a
	 * side arc starts by command beside the main one.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaStoryCommandEdges1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaStoryCommandEdges1031")
	public void qaTheStoryCommandsEdges(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		inline("side_cmd", """
			{"name": "The QA Side Errand", "side": true, "trigger": {"chance": 0},
			 "chapters": [{"name": "Beside it", "needs_flags": ["never_set"]}]}""");
		setDay(level, Chronicle.day(level) + 1);
		String village = VillageHalls.name(level, hall).getString();
		var commands = level.getServer().getCommands();
		List<String> said = new ArrayList<>();
		commands.performPrefixedCommand(source(level, hall, said, 0), "workplace story start aliveworkplace_test:test/three_chapters");
		helper.assertTrue(Arcs.running(level, hall) == null, "a player who isn't an operator started an arc");
		CommandSourceStack op = source(level, hall, said, 2);
		said.clear();
		commands.performPrefixedCommand(op, "workplace story stop");
		helper.assertTrue(said.contains("No story is running in " + village + "."), "stop with none: " + said);
		commands.performPrefixedCommand(op, "workplace story start test/three_chapters");
		helper.assertTrue(Arcs.running(level, hall) != null && said.contains("The Test of Three begins in " + village + "."), "start by path: " + said);
		commands.performPrefixedCommand(op, "workplace story start aliveworkplace_test:qa/side_cmd");
		helper.assertTrue(Arcs.side(level, hall).size() == 1 && said.contains("The QA Side Errand begins in " + village + "."), "a side arc by command: " + said);
		commands.performPrefixedCommand(op, "workplace story next");
		commands.performPrefixedCommand(op, "workplace story next");
		helper.assertTrue(said.contains("The Test of Three moves on to chapter 3."), "next to chapter 3: " + said);
		commands.performPrefixedCommand(op, "workplace story next");
		helper.assertTrue(Arcs.running(level, hall) == null && said.contains("The Test of Three is over."), "next on the last chapter: " + said);
		helper.assertTrue(Arcs.side(level, hall).size() == 1, "the side arc ended with the main one");
		helper.succeed();
	}

	private static CommandSourceStack source(ServerLevel level, BlockPos at, List<String> said, int permission) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, permission, "test", Component.literal("test"), level.getServer(), null);
	}
}
