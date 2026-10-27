package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.CaravanTrail;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * QA for the Lost Caravan (ROADMAP 31.19 and 31.20), written from the spec: the 300-block road and the chance, an arc
 * running in either village, the news and the map, the trail's marks and the action bar, the marks placed within 96
 * blocks and never over a player's blocks, Tobin's follow distances (32 and 64), his joining at Journeyman, the chest and
 * the delivery quest, the seven days, save and reload, rewards only to helpers, and every sentence a player reads.
 */
public class QaLostCaravanGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	private static final String TALL = "aliveworkplace_test:tall_area";
	static final BlockPos HALL = new BlockPos(2, 2, 2);
	static final int ROAD = 320;
	static final BlockPos DOOR = new BlockPos(25, 2, 22);
	private static final BlockPos HAY = new BlockPos(26, 2, 25);
	static final BlockPos CHEST = new BlockPos(20, 2, 25);
	/** British spellings the owner wants American (whole words, so a name like Greyson doesn't count). */
	private static final java.util.regex.Pattern BRITISH = java.util.regex.Pattern.compile(
		"\\b(honour\\w*|travell\\w*|colour\\w*|grey|defence|armour\\w*|cancelled|jeweller\\w*)\\b");

	/** A message a test player saw: its text and whether it was on the action bar. */
	record Seen(Component text, boolean overlay) {
	}

	/** Two villages {@link #ROAD} blocks apart (Thornholm in the test area, Ashford east), the chance forced to 1 in 1. */
	static BlockPos[] begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		int near = Arcs.NEAR;
		int chance = LostCaravanStory.CHANCE;
		int radius = VillageHalls.RADIUS;
		Arcs.NEAR = 0; // (the tests place what they need; a test that wants the 96 sets it back)
		LostCaravanStory.CHANCE = 1;
		VillageHalls.RADIUS = 32;
		Leftovers.storehouses(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = a.offset(ROAD, 0, 0);
		level.setBlock(b, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		Arcs.stopAll(level, a);
		Arcs.stopAll(level, b);
		((VillageHallBlockEntity) level.getBlockEntity(a)).setLastQuestDay(Long.MAX_VALUE / 2);
		((VillageHallBlockEntity) level.getBlockEntity(b)).setLastQuestDay(Long.MAX_VALUE / 2);
		((VillageHallBlockEntity) level.getBlockEntity(a)).setCustomName(Component.literal("Thornholm")); // (the hall's name, as the caravans' list has it)
		((VillageHallBlockEntity) level.getBlockEntity(b)).setCustomName(Component.literal("Ashford"));
		Caravans.Data.get(level).setWants(a, Component.literal("Thornholm"), List.of());
		Caravans.Data.get(level).setWants(b, Component.literal("Ashford"), List.of());
		Leftovers.after(helper, () -> {
			Arcs.NEAR = near;
			LostCaravanStory.CHANCE = chance;
			VillageHalls.RADIUS = radius;
			Arcs.stopAll(level, a);
			Arcs.stopAll(level, b);
			Caravans.Data.get(level).clearRoad(a);
			Caravans.Data.get(level).remove(a);
			Caravans.Data.get(level).remove(b);
			if (level.getBlockState(b).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
			}
		});
		return new BlockPos[] {a, b};
	}

	static ArcState lose(GameTestHelper helper, BlockPos a, BlockPos b) {
		ServerLevel level = helper.getLevel();
		boolean lost = LostCaravanStory.lose(level, a, b, List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BREAD, 12)), RandomSource.create(7));
		helper.assertTrue(lost, "the forced caravan didn't go missing");
		ArcState s = Arcs.running(level, a);
		helper.assertTrue(s != null && s.id.equals(LostCaravanStory.ARC.toString()), "no Lost Caravan running: " + s);
		return s;
	}

	/** A test player who records what they're told (chat and action bar), logged out when the test ends. */
	static ServerPlayer listener(GameTestHelper helper, List<Seen> seen, double x, double y, double z, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(new Seen(message, overlay));
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(x, y, z);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	/** The test cage the stockade's would be: an iron door, straw at the back, the raiders' chest with their own arrows. */
	static ArcState.ArcPlace cage(GameTestHelper helper, ArcState s) {
		helper.setBlock(DOOR, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(DOOR.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(HAY, Blocks.HAY_BLOCK);
		helper.setBlock(CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.ARROW, 5));
		ArcState.ArcPlace p = s.place(LostCaravanStory.STOCKADE);
		p.spot = helper.absolutePos(new BlockPos(23, 2, 23));
		p.placed = 1;
		p.origin = p.spot;
		return p;
	}

	static void open(GameTestHelper helper) {
		for (BlockPos d : List.of(DOOR, DOOR.above())) {
			BlockState st = helper.getLevel().getBlockState(helper.absolutePos(d));
			helper.getLevel().setBlock(helper.absolutePos(d), st.setValue(DoorBlock.OPEN, true), 2);
		}
	}

	/** Tobin caged in the test cage and freed by {@code me} (standing by the door): he follows {@code me}. */
	static Villager freed(GameTestHelper helper, ArcState s, ServerPlayer me) {
		ServerLevel level = helper.getLevel();
		Villager tobin = LostCaravanStory.setUp(level, s, cage(helper, s));
		helper.assertTrue(tobin != null, "no carter");
		BlockPos door = helper.absolutePos(DOOR);
		me.teleportTo(door.getX() + 0.5, door.getY(), door.getZ() - 2.5);
		open(helper);
		LostCaravanStory.tick(level);
		helper.assertTrue(me.getUUID().equals(LostCaravanStory.leader(s)), "opening the cage didn't make him follow");
		return tobin;
	}

	/** Tobin, far in the corner of the area, catches up with {@code me} by the sending hall and so walks into the village. */
	static void walkHome(GameTestHelper helper, Villager tobin, ServerPlayer me) {
		BlockPos far = helper.absolutePos(new BlockPos(28, 2, 28));
		BlockPos by = helper.absolutePos(new BlockPos(3, 2, 5));
		tobin.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		me.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		LostCaravanStory.tick(helper.getLevel());
	}

	/** The arc in its third chapter (the captive), by the operators' way on. */
	static void toTheCaptive(GameTestHelper helper, BlockPos a, ArcState s) {
		helper.assertTrue(Arcs.next(helper.getLevel(), a) && Arcs.next(helper.getLevel(), a) && s.chapter == 2, "not in chapter 3: " + s.chapter);
	}

	static List<String> texts(List<Seen> seen, boolean overlay) {
		return seen.stream().filter(m -> m.overlay() == overlay).map(m -> m.text().getString()).toList();
	}

	/** Every sentence: no leftover argument, no raw key, American English. */
	static void english(GameTestHelper helper, List<String> lines) {
		for (String line : lines) {
			String lower = line.toLowerCase(java.util.Locale.ROOT);
			helper.assertFalse(line.contains("%") || line.contains("aliveworkplace.") || line.contains("{") , "a raw key or argument in: " + line);
			java.util.regex.Matcher m = BRITISH.matcher(lower);
			helper.assertFalse(m.find(), "British spelling in: " + line);
		}
	}

	static int count(Container box, Item item) {
		return box.countItem(item);
	}

	// ---- going missing ------------------------------------------------------------------------------------------------

	/**
	 * "A road longer than 300 blocks": exactly 300 never loses one, 301 does, along an axis and on the diagonal (212,212
	 * is 299.8; 213,213 is 301.2).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLostCaravanRoad300"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLostCaravanRoad300")
	public void onlyARoadLongerThan300Loses(GameTestHelper helper) {
		BlockPos a = begin(helper)[0];
		ServerLevel level = helper.getLevel();
		List<ItemStack> goods = List.of(new ItemStack(Items.BREAD, 3));
		helper.assertFalse(LostCaravanStory.lose(level, a, a.offset(300, 0, 0), goods, RandomSource.create(1)), "lost on a road of exactly 300");
		helper.assertTrue(Arcs.running(level, a) == null, "an arc started on a road of 300");
		helper.assertTrue(LostCaravanStory.lose(level, a, a.offset(301, 0, 0), goods, RandomSource.create(1)), "not lost on a road of 301");
		Arcs.stopAll(level, a);
		helper.assertFalse(LostCaravanStory.lose(level, a, a.offset(212, 0, 212), goods, RandomSource.create(1)), "lost on a diagonal road of 299.8");
		helper.assertTrue(LostCaravanStory.lose(level, a, a.offset(-213, 0, 213), goods, RandomSource.create(1)), "not lost on a diagonal road of 301.2");
		Arcs.stopAll(level, a);
		helper.assertFalse(LostCaravanStory.lose(level, a, a.offset(400, 0, 0), List.of(), RandomSource.create(1)), "a caravan with nothing went missing");
		helper.succeed();
	}

	/**
	 * {@code lostCaravanChance}: 0 never (over 60 rolls), 1 always, the default 20 about one in twenty (fixed seeds); the
	 * config file's value is kept in 0 to 1000 and is 20 when unset.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLostCaravanChance"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLostCaravanChance")
	public void theChanceIsTheConfigs(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		List<ItemStack> goods = List.of(new ItemStack(Items.BREAD, 3));
		LostCaravanStory.CHANCE = 0;
		for (int seed = 0; seed < 60; seed++) {
			helper.assertFalse(LostCaravanStory.lose(level, a, b, goods, RandomSource.create(seed)), "lost with lostCaravanChance 0 (seed " + seed + ")");
		}
		LostCaravanStory.CHANCE = 1;
		helper.assertTrue(LostCaravanStory.lose(level, a, b, goods, RandomSource.create(99)), "not lost with lostCaravanChance 1");
		Arcs.stopAll(level, a);
		LostCaravanStory.CHANCE = 20;
		int lost = 0;
		for (int seed = 0; seed < 200; seed++) {
			if (LostCaravanStory.lose(level, a, b, goods, RandomSource.create(seed))) {
				lost++;
				Arcs.stopAll(level, a);
			}
		}
		helper.assertTrue(lost >= 3 && lost <= 25, "1 in 20 lost " + lost + " of 200");
		helper.assertTrue(new WorkplaceConfig().lostCaravanChance == 20, "the default isn't 20");
		helper.assertTrue(WorkplaceConfig.parse("{\"lostCaravanChance\": 0}").lostCaravanChance == 0, "0 isn't kept");
		helper.assertTrue(WorkplaceConfig.parse("{\"lostCaravanChance\": -5}").lostCaravanChance == 0, "-5 isn't kept at 0");
		helper.assertTrue(WorkplaceConfig.parse("{\"lostCaravanChance\": 5000}").lostCaravanChance == 1000, "5000 isn't kept at 1000");
		AliveWorkplace.LOG.info("QA Lost Caravan: 1 in 20 lost {} of 200", lost);
		helper.succeed();
	}

	/**
	 * "If neither village has an arc running": another story running in the sending village, or in the receiving one,
	 * keeps the caravan on its way (either way round); once it's over, it can go missing.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLostCaravanArcRunning"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLostCaravanArcRunning")
	public void anArcInEitherVillageKeepsItOnTheRoad(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		Arcs.Arc hold = Arcs.read(AliveWorkplace.id("qa_lost_caravan_hold"), JsonParser.parseString(
			"{\"name\": \"QA hold\", \"chapters\": [{\"name\": \"Wait\", \"needs_flags\": [\"qa_never\"]}]}").getAsJsonObject());
		Arcs.add(hold);
		List<ItemStack> goods = List.of(new ItemStack(Items.BREAD, 3));
		for (BlockPos busy : List.of(a, b)) {
			ArcState other = Arcs.start(level, busy, hold);
			helper.assertTrue(other != null && Arcs.running(level, busy) == other, "the other story didn't start");
			helper.assertFalse(LostCaravanStory.lose(level, a, b, goods, RandomSource.create(1)), "lost from a to b while a story runs at " + (busy == a ? "a" : "b"));
			helper.assertFalse(LostCaravanStory.lose(level, b, a, goods, RandomSource.create(1)), "lost from b to a while a story runs at " + (busy == a ? "a" : "b"));
			helper.assertTrue(Arcs.running(level, busy) == other, "the running story was replaced");
			Arcs.stopAll(level, busy);
		}
		helper.assertTrue(LostCaravanStory.lose(level, b, a, goods, RandomSource.create(1)), "not lost once both villages were free");
		helper.assertTrue(Arcs.running(level, b) != null && Arcs.running(level, a) == null, "the arc isn't the sending village's");
		Arcs.stopAll(level, b);
		helper.succeed();
	}

	/**
	 * Chapter 1, Overdue: a player in each village is told, in chat, "The caravan from Thornholm to Ashford is a day
	 * overdue"; the sending hall's player gets a map (to the wreck), the receiving one's doesn't. The intro reads clean.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLostCaravanOverdue"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLostCaravanOverdue")
	public void bothHallsAreToldAndTheSenderGivesAMap(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		List<Seen> atA = new ArrayList<>();
		List<Seen> atB = new ArrayList<>();
		ServerPlayer here = listener(helper, atA, a.getX() + 4.5, a.getY(), a.getZ() + 4.5, "QaThornholm");
		ServerPlayer there = listener(helper, atB, b.getX() + 4.5, b.getY(), b.getZ() + 4.5, "QaAshford");
		ArcState s = lose(helper, a, b);
		String overdue = "The caravan from Thornholm to Ashford is a day overdue";
		helper.assertTrue(texts(atA, false).contains(overdue), "the sending village's player wasn't told: " + texts(atA, false));
		helper.assertTrue(texts(atB, false).contains(overdue), "the receiving village's player wasn't told: " + texts(atB, false));
		helper.assertTrue(here.getInventory().countItem(Items.FILLED_MAP) == 1, "the sending hall gave " + here.getInventory().countItem(Items.FILLED_MAP) + " maps");
		helper.assertTrue(there.getInventory().countItem(Items.FILLED_MAP) == 0, "the receiving hall gave a map");
		helper.assertTrue(s.place(LostCaravanStory.WRECK) != null, "no wreck");
		english(helper, texts(atA, false));
		english(helper, texts(atB, false));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(a);
		english(helper, hall.chronicle().stream().map(e -> e.text().getString()).toList());
		helper.succeed();
	}

	// ---- the trail ----------------------------------------------------------------------------------------------------

	/**
	 * The real arc's follow_trail, marks running north-east and the stockade east of the last: mark 2 first counts nothing
	 * and says nothing; at mark 1 the action bar says "The tracks go on to the north-east" (and again at 2 to 4); mark 1
	 * twice counts once; at mark 5 "The tracks go on to the east"; then the captive's chapter begins.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLostCaravanTrailText"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLostCaravanTrailText")
	public void theActionBarSaysWhereTheTracksGo(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		helper.assertTrue(Arcs.next(level, a) && s.chapter == 1, "not in chapter 2");
		Quest tracks = Stories.open(level, a).stream().filter(q -> s.id.equals(q.arc) && q.objectives.get(0) instanceof CaravanTrail.FollowTrail).findFirst().orElse(null);
		helper.assertTrue(tracks != null, "no follow_trail quest");
		helper.assertTrue(tracks.objectives.get(0).line().getString().equals("Follow the tracks"), "the quest line: " + tracks.objectives.get(0).line().getString());
		BlockPos start = helper.absolutePos(new BlockPos(4, 2, 26));
		List<ArcState.ArcPlace> marks = s.places.stream().filter(p -> p.key.startsWith("trail")).toList();
		helper.assertTrue(marks.size() == 5, "marks: " + marks.size());
		for (int i = 0; i < 5; i++) {
			marks.get(i).spot = start.offset(5 * i, 0, -5 * i);
			marks.get(i).skipped = true;
		}
		ArcState.ArcPlace stockade = s.place(LostCaravanStory.STOCKADE);
		stockade.spot = marks.get(4).spot.offset(40, 0, 0);
		stockade.placed = 1;
		stockade.origin = stockade.spot;
		s.place(LostCaravanStory.WRECK).skipped = true;
		List<Seen> seen = new ArrayList<>();
		ServerPlayer me = listener(helper, seen, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, "QaTracker");
		java.util.function.IntConsumer at = i -> {
			BlockPos m = marks.get(i).spot;
			me.teleportTo(m.getX() + 0.5, m.getY(), m.getZ() + 1.5);
			CaravanTrail.check(me);
		};
		at.accept(1);
		helper.assertTrue(tracks.progress[0] == 0 && texts(seen, true).isEmpty(), "mark 2 first counted or spoke: " + tracks.progress[0] + " " + texts(seen, true));
		at.accept(0);
		helper.assertTrue(tracks.progress[0] == 1, "mark 1 didn't count");
		helper.assertTrue(texts(seen, true).equals(List.of("The tracks go on to the north-east")), "at mark 1: " + texts(seen, true));
		at.accept(0);
		helper.assertTrue(tracks.progress[0] == 1 && texts(seen, true).size() == 1, "mark 1 twice counted or spoke again: " + tracks.progress[0] + " " + texts(seen, true));
		for (int i = 1; i < 4; i++) {
			at.accept(i);
			helper.assertTrue(tracks.progress[0] == i + 1, "mark " + (i + 1) + " didn't count");
		}
		at.accept(4);
		List<String> bar = texts(seen, true).stream().filter(t -> t.startsWith("The tracks")).toList();
		helper.assertTrue(bar.size() == 5 && bar.subList(0, 4).stream().allMatch("The tracks go on to the north-east"::equals)
			&& bar.get(4).equals("The tracks go on to the east"), "the action bar along the trail: " + bar);
		helper.assertTrue(s.chapter == 2, "the trail didn't bring the captive's chapter: " + s.chapter);
		english(helper, texts(seen, false));
		helper.succeed();
	}

	/** Ground in a tall area: dirt, then grass at y 3, and no barrier above (so the height map finds the grass). */
	private static void ground(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	/** The arc of a forced loss from a hall at (2, 4, 2) of a tall area, every place set aside but mark 3, whose spot is {@code spot}. */
	private static ArcState.ArcPlace onlyMark3(GameTestHelper helper, BlockPos spot) {
		ground(helper);
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		int near = Arcs.NEAR;
		int chance = LostCaravanStory.CHANCE;
		int radius = VillageHalls.RADIUS;
		Arcs.NEAR = 96;
		LostCaravanStory.CHANCE = 1;
		VillageHalls.RADIUS = 8;
		helper.setBlock(new BlockPos(2, 4, 2), ModBlocks.VILLAGE_HALL);
		BlockPos a = helper.absolutePos(new BlockPos(2, 4, 2));
		Arcs.stopAll(level, a);
		((VillageHallBlockEntity) level.getBlockEntity(a)).setLastQuestDay(Long.MAX_VALUE / 2);
		Leftovers.after(helper, () -> {
			Arcs.NEAR = near;
			LostCaravanStory.CHANCE = chance;
			VillageHalls.RADIUS = radius;
			Arcs.stopAll(level, a);
		});
		ArcState s = lose(helper, a, a.offset(ROAD, 0, 0));
		ArcState.ArcPlace mark = s.place("trail#3");
		helper.assertTrue(mark != null && mark.stages.equals(List.of(CaravanTrail.MARKS.get(2).toString())), "no mark 3 of trail_3: " + (mark == null ? null : mark.stages));
		for (ArcState.ArcPlace p : s.places) {
			p.skipped = p != mark;
		}
		mark.spot = spot;
		mark.placed = 0;
		mark.origin = null;
		return mark;
	}

	/** A mark is built once a player comes within 96 blocks: 97 off (either way) nothing, 96 off it's down. */
	//$ gametest_ticks_batch TALL '60' '"qaLostCaravanMarkAt96"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaLostCaravanMarkAt96")
	public void aMarkIsPlacedWhenAPlayerComesWithin96(GameTestHelper helper) {
		BlockPos spot = helper.absolutePos(new BlockPos(15, 4, 15));
		ArcState.ArcPlace mark = onlyMark3(helper, spot);
		ServerLevel level = helper.getLevel();
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		me.teleportTo(spot.getX() + 0.5 + 97, spot.getY(), spot.getZ() + 0.5);
		ArcEffects.check(level);
		helper.assertTrue(mark.placed == 0, "built with the player 97 blocks east");
		me.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5 - 97);
		ArcEffects.check(level);
		helper.assertTrue(mark.placed == 0, "built with the player 97 blocks north");
		me.teleportTo(spot.getX() + 0.5 - 96, spot.getY(), spot.getZ() + 0.5);
		ArcEffects.check(level);
		helper.assertTrue(mark.placed == 1 && !mark.skipped && mark.origin != null, "not built with the player 96 blocks west: placed " + mark.placed + ", skipped " + mark.skipped);
		me.discard();
		helper.succeed();
	}

	/**
	 * A mark whose spot is a player's: planks they set in the middle and grass the ledger says they set beside it. The
	 * mark goes down elsewhere (or not at all), never over them: the planks and the grass are as they were.
	 */
	//$ gametest_ticks_batch TALL '60' '"qaLostCaravanMarkNotOverPlayers"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaLostCaravanMarkNotOverPlayers")
	public void aMarkIsNeverPlacedOverAPlayersBlocks(GameTestHelper helper) {
		BlockPos spot = helper.absolutePos(new BlockPos(15, 4, 15));
		ArcState.ArcPlace mark = onlyMark3(helper, spot);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(15, 4, 15), Blocks.OAK_PLANKS);
		BlockPos earth = helper.absolutePos(new BlockPos(16, 3, 15));
		PlayerBuilt.get(level).mark(earth);
		Leftovers.after(helper, () -> PlayerBuilt.get(level).forget(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(earth, earth)));
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		me.teleportTo(spot.getX() + 0.5 + 20, spot.getY(), spot.getZ() + 0.5);
		ArcEffects.check(level);
		helper.assertTrue(mark.placed == 1 || mark.skipped, "the mark neither went down nor was given up");
		helper.assertTrue(level.getBlockState(spot).is(Blocks.OAK_PLANKS), "the player's planks are gone: " + level.getBlockState(spot));
		helper.assertTrue(level.getBlockState(earth).is(Blocks.GRASS_BLOCK) && level.getBlockState(earth.above()).isAir(),
			"the player's ground was built over: " + level.getBlockState(earth) + " / " + level.getBlockState(earth.above()));
		if (mark.placed == 1) {
			helper.assertTrue(Math.abs(mark.spot.getX() - spot.getX()) > 3 || Math.abs(mark.spot.getZ() - spot.getZ()) > 3,
				"the mark lies over the player's blocks: its middle " + mark.spot.subtract(spot));
		}
		AliveWorkplace.LOG.info("QA Lost Caravan: the mark beside a player's blocks: placed {}, skipped {}, middle {}", mark.placed, mark.skipped,
			mark.spot.subtract(spot));
		me.discard();
		helper.succeed();
	}

	// ---- Tobin --------------------------------------------------------------------------------------------------------

	/**
	 * Tobin follows the one who freed him "as a rallied guard does": within 32 he walks after them (no jump), from 33 to
	 * 64 he catches up (beside them), at 65 he waits where he is. The far spots' chunks are kept loaded.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaLostCaravanFollow"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaLostCaravanFollow")
	public void tobinCatchesUpFrom32AndWaitsBeyond64(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		toTheCaptive(helper, a, s);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		Villager tobin = freed(helper, s, me);
		BlockPos home = tobin.blockPosition();
		List<ChunkPos> chunks = new ArrayList<>();
		for (int d : new int[] {32, 33, 64, 65}) {
			ChunkPos c = new ChunkPos(home.offset(d, 0, 0));
			if (!chunks.contains(c)) {
				chunks.add(c);
			}
		}
		// (forced here and let go in the test itself before it succeeds: the next batch's area is set down along x, and a
		// chunk let go from a listener after it was forced for that area left the run waiting forever for its chunks)
		List<ChunkPos> forced = new ArrayList<>();
		for (ChunkPos c : chunks) {
			if (!level.getForcedChunks().contains(c.toLong())) {
				level.setChunkForced(c.x, c.z, true);
				forced.add(c);
			}
		}
		Leftovers.after(helper, () -> {
			tobin.discard();
			me.discard();
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(chunks.stream().allMatch(c -> level.areEntitiesLoaded(c.toLong()) && level.isPositionEntityTicking(c.getMiddleBlockPosition(home.getY()))),
				"the far chunks aren't loaded yet"))
			.thenExecute(() -> {
				Map<Integer, String> wrong = new HashMap<>();
				try {
					for (int d : new int[] {32, 33, 64, 65}) {
						tobin.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
						tobin.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
						me.teleportTo(home.getX() + 0.5 + d, home.getY(), home.getZ() + 0.5);
						helper.assertTrue(Math.abs(tobin.distanceTo(me) - d) < 0.01, "set up " + d + " apart, but " + tobin.distanceTo(me));
						LostCaravanStory.tick(level);
						helper.assertTrue(LostCaravanStory.carter(level, s) == tobin, "Tobin isn't the arc's carter any more at " + d);
						double after = tobin.distanceTo(me);
						boolean jumped = !tobin.blockPosition().equals(home);
						boolean walking = tobin.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent();
						String got = (jumped ? "caught up to " + Math.round(after) : "stayed") + (walking ? ", walking" : "");
						AliveWorkplace.LOG.info("QA Lost Caravan: {} blocks off: {}", d, got);
						boolean right = switch (d) {
							case 32 -> !jumped && walking;
							case 33, 64 -> jumped && after < 5;
							default -> !jumped && !walking;
						};
						if (!right) {
							wrong.put(d, got);
						}
					}
				} finally {
					tobin.discard();
					me.discard();
					forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
				}
				helper.assertTrue(wrong.isEmpty(), "following at the boundaries: " + wrong);
				helper.succeed();
			});
	}

	/**
	 * The ending: Tobin joins the village he reached and "takes a free workstation" at Journeyman. With none free he
	 * stays jobless and keeps his Journeyman start; a fletching table set down by him becomes his, at level 3. (He waits in a
	 * glass pen: out on the test area's floor a jobless villager strolled off and found no path back to the table.)
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaLostCaravanJourneyman"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaLostCaravanJourneyman")
	public void tobinTakesAFreeWorkstationAtJourneyman(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		toTheCaptive(helper, a, s);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		Villager tobin = freed(helper, s, me);
		walkHome(helper, tobin, me);
		helper.assertTrue(Arcs.running(level, a) == null, "he didn't come home");
		Leftovers.after(helper, () -> {
			tobin.discard();
			me.discard();
		});
		me.teleportTo(a.getX() + 0.5, a.getY() + 40, a.getZ() + 0.5); // (out of his way)
		// a glass pen in the village round him, so he doesn't stroll off past the workstation (or to one an earlier test left)
		for (int x = 13; x <= 17; x++) {
			for (int z = 13; z <= 17; z++) {
				if (x == 13 || x == 17 || z == 13 || z == 17) {
					helper.setBlock(new BlockPos(x, 2, z), Blocks.GLASS);
					helper.setBlock(new BlockPos(x, 3, z), Blocks.GLASS);
				}
			}
		}
		BlockPos pen = helper.absolutePos(new BlockPos(15, 2, 15));
		tobin.teleportTo(pen.getX() + 0.5, pen.getY(), pen.getZ() + 0.5);
		BlockPos[] table = {helper.absolutePos(new BlockPos(16, 2, 14))};
		helper.startSequence()
			.thenIdle(200)
			.thenExecute(() -> {
				helper.assertTrue(tobin.isAlive(), "Tobin is gone");
				helper.assertTrue(ModAttachments.HEAD_START.getOrElse(tobin, 1) == 3, "with no workstation he lost his Journeyman start");
				VillagerProfession job = tobin.getVillagerData().getProfession();
				helper.assertTrue(job == VillagerProfession.NONE || tobin.getVillagerData().getLevel() == 3,
					"he took a job (" + job + ") at level " + tobin.getVillagerData().getLevel());
				level.setBlockAndUpdate(table[0], Blocks.FLETCHING_TABLE.defaultBlockState());
			})
			.thenWaitUntil(() -> helper.assertTrue(tobin.getVillagerData().getProfession() != VillagerProfession.NONE, "he hasn't taken the free workstation: "
				+ "at table" + tobin.blockPosition().subtract(table[0]) + ", alive " + tobin.isAlive() + ", noAI " + tobin.isNoAi()
				+ ", poi " + level.getPoiManager().getType(table[0]).map(h -> h.unwrapKey().map(Object::toString).orElse("?")).orElse("NONE")
				+ ", job site " + tobin.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().subtract(table[0]).toString()).orElse("-")
				+ ", potential " + tobin.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(g -> g.pos().subtract(table[0]).toString()).orElse("-")
				+ ", activity " + tobin.getBrain().getActiveNonCoreActivity() + ", tags " + tobin.getTags() + ", restricted " + tobin.hasRestriction()
				+ ", free job sites within 48: " + level.getPoiManager().getCountInRange(h -> h.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE), tobin.blockPosition(), 48,
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
				+ ", table free " + (level.getPoiManager().getCountInRange(h -> true, table[0], 0, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE) > 0)
				+ ", path " + java.util.Optional.ofNullable(tobin.getNavigation().createPath(table[0], 1)).map(pt -> pt.canReach() ? "reaches" : "short").orElse("none")
				+ ", day time " + level.getDayTime() % 24000 + ", baby " + tobin.isBaby() + ", retired " + ModAttachments.RETIRED.has(tobin)))
			.thenExecute(() -> {
				helper.assertTrue(tobin.getVillagerData().getLevel() == 3, "he took his first job at level " + tobin.getVillagerData().getLevel() + ", not Journeyman");
				AliveWorkplace.LOG.info("QA Lost Caravan: Tobin took {} at level {}", tobin.getVillagerData().getProfession(), tobin.getVillagerData().getLevel());
				helper.succeed();
			});
	}

	/** Tobin's following and the trail's progress are saved: the story's data and Tobin read back from a save go on as before. */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanReloadTobin"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanReloadTobin")
	public void tobinStillFollowsAfterAReload(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		toTheCaptive(helper, a, s);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		Villager tobin = freed(helper, s, me);
		// a restart: the story's data and Tobin, saved and read back
		reloadStories(level);
		ArcState back = Arcs.running(level, a);
		helper.assertTrue(back != null && back != s && back.chapter == 2 && me.getUUID().equals(LostCaravanStory.leader(back)), "the arc didn't read back following: " + back);
		CompoundTag tag = tobin.saveWithoutId(new CompoundTag());
		tobin.discard();
		Villager again = EntityType.VILLAGER.create(level);
		again.load(tag);
		level.addFreshEntity(again);
		Leftovers.after(helper, () -> {
			again.discard();
			me.discard();
		});
		helper.assertTrue(again.isAlive(), "Tobin didn't come back after the reload");
		helper.assertTrue(LostCaravanStory.carter(level, back) == again, "the reloaded Tobin isn't the arc's carter");
		walkHome(helper, again, me);
		helper.assertTrue(again.distanceTo(me) < 5, "he didn't catch up after the reload: " + again.distanceTo(me));
		helper.assertTrue(Arcs.running(level, a) == null, "he came home but the arc runs on");
		helper.assertTrue(ModAttachments.HEAD_START.getOrElse(again, 1) == 3, "he doesn't start at Journeyman");
		helper.assertTrue(Reputation.honours(level, a, me.getUUID()).contains("wayfinder"), "no Wayfinder after a reload");
		helper.succeed();
	}

	/** The story's saved data, written and read back into the world (a server restart). */
	static void reloadStories(ServerLevel level) {
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(saved, level.registryAccess()));
	}

	/** Two marks reached, a restart: the quest is at 2; mark 2 again counts nothing, mark 3 counts. */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanReloadTrail"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanReloadTrail")
	public void theTrailsProgressSurvivesAReload(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		helper.assertTrue(Arcs.next(level, a), "not in chapter 2");
		BlockPos start = helper.absolutePos(new BlockPos(1, 2, 24)); // (7 apart: more than a mark's 6 blocks)
		List<ArcState.ArcPlace> marks = s.places.stream().filter(p -> p.key.startsWith("trail")).toList();
		for (int i = 0; i < 5; i++) {
			marks.get(i).spot = start.offset(7 * i, 0, 0);
			marks.get(i).skipped = true;
		}
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		java.util.function.IntConsumer at = i -> {
			BlockPos m = start.offset(7 * i, 0, 0);
			me.teleportTo(m.getX() + 0.5, m.getY(), m.getZ() + 0.5);
			CaravanTrail.check(me);
		};
		at.accept(0);
		at.accept(1);
		reloadStories(level);
		Quest tracks = Stories.open(level, a).stream().filter(q -> q.objectives.get(0) instanceof CaravanTrail.FollowTrail).findFirst().orElse(null);
		helper.assertTrue(tracks != null && tracks.progress[0] == 2, "the trail after a reload: " + (tracks == null ? "no quest" : tracks.progress[0]));
		ArcState back = Arcs.running(level, a);
		helper.assertTrue(back != null && back.places.stream().filter(p -> p.key.startsWith("trail")).map(p -> p.spot).toList()
			.equals(marks.stream().map(p -> p.spot).toList()), "the marks' places didn't read back");
		at.accept(1);
		helper.assertTrue(tracks.progress[0] == 2, "mark 2 counted again after a reload");
		at.accept(2);
		helper.assertTrue(tracks.progress[0] == 3, "mark 3 didn't count after a reload: " + tracks.progress[0]);
		me.discard();
		helper.succeed();
	}

	// ---- the goods ----------------------------------------------------------------------------------------------------

	/**
	 * A real caravan lost from the Storehouse: what left the store is what the arc holds, and the stockade's chest holds
	 * exactly that (each item, as many), nothing of the raiders' own.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanChest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanChest")
	public void theChestHoldsExactlyWhatLeftTheStore(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		long travel = Caravans.MIN_TRAVEL;
		Leftovers.after(helper, () -> Caravans.MIN_TRAVEL = travel);
		Caravans.MIN_TRAVEL = 0;
		helper.setBlock(new BlockPos(5, 2, 2), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(5, 2, 4), Blocks.CHEST);
		Container ours = helper.getBlockEntity(new BlockPos(5, 2, 4));
		ours.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		ours.setItem(1, new ItemStack(Items.OAK_LOG, 64));
		ours.setItem(2, new ItemStack(Items.WHEAT, 64));
		ours.setItem(3, new ItemStack(Items.COBBLESTONE, 64));
		Caravans.Data data = Caravans.Data.get(level);
		helper.runAfterDelay(2, () -> {
			data.setWants(b, Component.literal("Ashford"), List.of(new Caravans.Want(Items.OAK_LOG, 20), new Caravans.Want(Items.WHEAT, 9)));
			helper.assertTrue(data.toggleRoute(a, b), "no route");
			Map<Item, Integer> before = Map.of(Items.OAK_LOG, ours.countItem(Items.OAK_LOG), Items.WHEAT, ours.countItem(Items.WHEAT), Items.COBBLESTONE, ours.countItem(Items.COBBLESTONE));
			Caravans.round(level, a, null);
			ArcState s = Arcs.running(level, a);
			helper.assertTrue(s != null && s.id.equals(LostCaravanStory.ARC.toString()), "the forced caravan didn't go missing");
			Map<Item, Integer> held = new HashMap<>();
			LostCaravanStory.goods(s).forEach(g -> held.merge(g.getItem(), g.getCount(), Integer::sum));
			Map<Item, Integer> left = new HashMap<>();
			before.forEach((item, n) -> {
				int gone = n - ours.countItem(item);
				if (gone != 0) {
					left.put(item, gone);
				}
			});
			helper.assertTrue(!held.isEmpty() && held.equals(left), "the arc holds " + held + " but the store lost " + left);
			Villager tobin = LostCaravanStory.setUp(level, s, cage(helper, s));
			Container chest = helper.getBlockEntity(CHEST);
			Map<Item, Integer> inChest = new HashMap<>();
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack st = chest.getItem(i);
				if (!st.isEmpty()) {
					inChest.merge(st.getItem(), st.getCount(), Integer::sum);
					helper.assertTrue(st.getCount() <= st.getMaxStackSize(), "an overfull stack in the chest: " + st);
				}
			}
			helper.assertTrue(inChest.equals(held), "the chest holds " + inChest + ", the shipment was " + held);
			AliveWorkplace.LOG.info("QA Lost Caravan: the shipment {} is the chest's {}", held, inChest);
			if (tobin != null) {
				tobin.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * The delivery quest pays at the receiving hall only: Ashford +50 exactly and 8 emeralds, Thornholm's standing
	 * unchanged; the chat says "The lost goods are in Ashford's store at last."; a second load of the same goods pays
	 * nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanDelivery"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanDelivery")
	public void theDeliveryPaysOnlyAtTheReceivingHallAndOnce(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		toTheCaptive(helper, a, s);
		List<Seen> seen = new ArrayList<>();
		ServerPlayer me = listener(helper, seen, a.getX() + 3.5, a.getY(), a.getZ() + 0.5, "QaCarrier");
		int atA = Reputation.points(level, a, me.getUUID());
		int atB = Reputation.points(level, b, me.getUUID());
		me.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		me.getInventory().add(new ItemStack(Items.BREAD, 12));
		LostCaravanStory.check(me);
		helper.assertTrue(me.getInventory().countItem(Items.OAK_LOG) == 32, "taken at the sending hall");
		int emeralds = me.getInventory().countItem(Items.EMERALD);
		me.teleportTo(b.getX() + 3.5, b.getY(), b.getZ() + 0.5);
		LostCaravanStory.check(me);
		helper.assertTrue(me.getInventory().countItem(Items.OAK_LOG) == 0, "not taken at the receiving hall");
		int paid = me.getInventory().countItem(Items.EMERALD) - emeralds;
		helper.assertTrue(paid == 8 || !io.github.jcondedata.aliveworkplace.work.Bank.EXTENSION.call(bank -> false, true), "paid " + paid + " emeralds");
		helper.assertTrue(Reputation.points(level, b, me.getUUID()) - atB == 50, "Ashford gave " + (Reputation.points(level, b, me.getUUID()) - atB));
		helper.assertTrue(Reputation.points(level, a, me.getUUID()) == atA, "Thornholm gave " + (Reputation.points(level, a, me.getUUID()) - atA) + " for the delivery");
		helper.assertTrue(texts(seen, false).contains("The lost goods are in Ashford's store at last."), "the chat: " + texts(seen, false));
		me.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		me.getInventory().add(new ItemStack(Items.BREAD, 12));
		int standing = Reputation.points(level, b, me.getUUID());
		emeralds = me.getInventory().countItem(Items.EMERALD);
		LostCaravanStory.check(me);
		helper.assertTrue(me.getInventory().countItem(Items.OAK_LOG) == 32 && Reputation.points(level, b, me.getUUID()) == standing
			&& me.getInventory().countItem(Items.EMERALD) == emeralds, "a second load was taken or paid");
		english(helper, texts(seen, false));
		english(helper, ((VillageHallBlockEntity) level.getBlockEntity(b)).chronicle().stream().map(e -> e.text().getString()).toList());
		helper.succeed();
	}

	// ---- the endings --------------------------------------------------------------------------------------------------

	/**
	 * The whole story with three players: one finds the wreck, one follows the trail and frees and walks Tobin home, one
	 * only stands in the village. The two who helped get +150 in both villages, Wayfinder in both and +100 with Tobin;
	 * the bystander gets nothing. Every chat line and action bar the players saw reads clean, with its names filled in.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanHelpers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanHelpers")
	public void onlyHelpersAreThanked(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		List<Seen> finderSaw = new ArrayList<>();
		List<Seen> rescuerSaw = new ArrayList<>();
		List<Seen> bystanderSaw = new ArrayList<>();
		ServerPlayer finder = listener(helper, finderSaw, a.getX() + 2.5, a.getY(), a.getZ() + 6.5, "QaFinder");
		ServerPlayer rescuer = listener(helper, rescuerSaw, a.getX() + 3.5, a.getY(), a.getZ() + 6.5, "QaRescuer");
		ServerPlayer bystander = listener(helper, bystanderSaw, a.getX() + 1.5, a.getY(), a.getZ() + 8.5, "QaBystander");
		ArcState s = lose(helper, a, b);
		// 1. the wreck, found by the finder alone
		ArcState.ArcPlace wreck = s.place(LostCaravanStory.WRECK);
		wreck.spot = helper.absolutePos(new BlockPos(20, 2, 8));
		wreck.skipped = true;
		rescuer.teleportTo(a.getX() + 0.5, a.getY() + 30, a.getZ() + 0.5);
		bystander.teleportTo(a.getX() + 0.5, a.getY() + 30, a.getZ() + 2.5);
		finder.teleportTo(wreck.spot.getX() + 2.5, wreck.spot.getY(), wreck.spot.getZ() + 0.5);
		LostCaravanStory.tick(level);
		helper.assertTrue(s.chapter == 1, "the wreck wasn't found");
		finder.teleportTo(a.getX() + 2.5, a.getY() + 30, a.getZ() + 6.5);
		// 2. the marks, by the rescuer
		List<ArcState.ArcPlace> marks = s.places.stream().filter(p -> p.key.startsWith("trail")).toList();
		BlockPos start = helper.absolutePos(new BlockPos(6, 2, 14));
		for (int i = 0; i < marks.size(); i++) {
			marks.get(i).spot = start.offset(4 * i, 0, 0);
			marks.get(i).skipped = true;
		}
		for (ArcState.ArcPlace m : marks) {
			rescuer.teleportTo(m.spot.getX() + 0.5, m.spot.getY(), m.spot.getZ() + 0.5);
			CaravanTrail.check(rescuer);
		}
		helper.assertTrue(s.chapter == 2, "the trail didn't bring chapter 3: " + s.chapter);
		// 3. freed and walked home by the rescuer
		Villager tobin = freed(helper, s, rescuer);
		Leftovers.after(helper, tobin::discard);
		String name = tobin.getCustomName().getString();
		walkHome(helper, tobin, rescuer);
		helper.assertTrue(Arcs.running(level, a) == null, "he came home but the arc runs on");
		for (ServerPlayer p : List.of(finder, rescuer)) {
			String who = p.getGameProfile().getName();
			helper.assertTrue(Reputation.points(level, a, p.getUUID()) >= 150 && Reputation.points(level, b, p.getUUID()) >= 150,
				who + " standing: " + Reputation.points(level, a, p.getUUID()) + " and " + Reputation.points(level, b, p.getUUID()));
			helper.assertTrue(Reputation.honours(level, a, p.getUUID()).contains("wayfinder") && Reputation.honours(level, b, p.getUUID()).contains("wayfinder"),
				who + " isn't Wayfinder in both villages");
			helper.assertTrue(Friendship.points(tobin, p.getUUID()) >= 100, who + " friendship with Tobin: " + Friendship.points(tobin, p.getUUID()));
		}
		helper.assertTrue(Reputation.points(level, a, bystander.getUUID()) == 0 && Reputation.points(level, b, bystander.getUUID()) == 0,
			"the bystander was given standing: " + Reputation.points(level, a, bystander.getUUID()) + " and " + Reputation.points(level, b, bystander.getUUID()));
		helper.assertTrue(Reputation.honours(level, a, bystander.getUUID()).isEmpty() && Reputation.honours(level, b, bystander.getUUID()).isEmpty(), "the bystander was honored");
		helper.assertTrue(Friendship.points(tobin, bystander.getUUID()) == 0, "the bystander is Tobin's friend");
		// what they read
		helper.assertTrue(texts(finderSaw, true).contains("The caravan's wreck: tracks lead away from the road"), "the finder's action bar: " + texts(finderSaw, true));
		helper.assertTrue(texts(rescuerSaw, false).contains(name + ": \"You came for me! Take me home, I'll keep up.\""), "the rescuer's chat: " + texts(rescuerSaw, false));
		helper.assertTrue(texts(rescuerSaw, false).contains(name + " is home safe, and stays in Thornholm."), "the homecoming: " + texts(rescuerSaw, false));
		helper.assertTrue(texts(rescuerSaw, false).stream().anyMatch(t -> t.equals("Thornholm names you Wayfinder.")), "the honor in Thornholm: " + texts(rescuerSaw, false));
		helper.assertTrue(texts(bystanderSaw, false).stream().noneMatch(t -> t.contains("names you")), "the bystander was named something");
		for (List<Seen> seen : List.of(finderSaw, rescuerSaw, bystanderSaw)) {
			english(helper, texts(seen, false));
			english(helper, texts(seen, true));
		}
		english(helper, ((VillageHallBlockEntity) level.getBlockEntity(a)).chronicle().stream().map(e -> e.text().getString()).toList());
		english(helper, ((VillageHallBlockEntity) level.getBlockEntity(b)).chronicle().stream().map(e -> e.text().getString()).toList());
		helper.assertTrue(((VillageHallBlockEntity) level.getBlockEntity(a)).chronicle().stream().anyMatch(e -> e.text().getString()
			.equals(name + ", the carter the raiders took, came home and settled in Thornholm.")), "Thornholm's chronicle doesn't tell it");
		helper.succeed();
	}

	/**
	 * Seven days without a rescue (never freed): on day 6 nothing; on day 7 the goods are lost (the arc holds none, the
	 * chest is empty), Tobin is home by the hall with nothing (no head start, no job), the delivery quest is gone, nobody
	 * is thanked, and the players are told "Nobody came for Tobin, who slipped away ... The goods are lost."
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanSevenDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanSevenDays")
	public void afterSevenDaysTheGoodsAreLost(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		List<Seen> seen = new ArrayList<>();
		ServerPlayer me = listener(helper, seen, a.getX() + 2.5, a.getY() + 30, a.getZ() + 6.5, "QaLate");
		ArcState s = lose(helper, a, b);
		ArcState.ArcPlace wreck = s.place(LostCaravanStory.WRECK);
		wreck.spot = helper.absolutePos(new BlockPos(20, 2, 8));
		wreck.skipped = true;
		me.teleportTo(wreck.spot.getX() + 0.5, wreck.spot.getY(), wreck.spot.getZ() + 0.5);
		LostCaravanStory.tick(level); // (he helped: found the wreck)
		helper.assertTrue(s.chapter == 1, "the wreck wasn't found");
		helper.assertTrue(Arcs.next(level, a) && s.chapter == 2, "not in chapter 3");
		Villager tobin = LostCaravanStory.setUp(level, s, cage(helper, s));
		Component name = tobin.getCustomName();
		me.teleportTo(a.getX() + 2.5, a.getY() + 30, a.getZ() + 6.5);
		int standing = Reputation.points(level, a, me.getUUID());
		s.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS + 1;
		LostCaravanStory.tick(level);
		helper.assertTrue(Arcs.running(level, a) == s && tobin.isAlive() && !LostCaravanStory.goods(s).isEmpty(), "it failed on day 6");
		s.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS;
		LostCaravanStory.tick(level);
		helper.assertTrue(Arcs.running(level, a) == null, "the arc runs on after seven days");
		helper.assertTrue(LostCaravanStory.goods(s).isEmpty(), "the arc still holds goods: " + LostCaravanStory.goods(s));
		helper.assertTrue(((Container) helper.getBlockEntity(CHEST)).isEmpty(), "the goods are still in the chest");
		helper.assertTrue(Stories.open(level, a).stream().noneMatch(q -> s.id.equals(q.arc)), "an arc quest is still open");
		List<Villager> home = level.getEntitiesOfClass(Villager.class, new AABB(a).inflate(6, 40, 6), v -> name.equals(v.getCustomName()));
		Leftovers.after(helper, () -> home.forEach(Villager::discard));
		helper.assertTrue(home.size() == 1, "Tobins at home: " + home.size());
		Villager back = home.get(0);
		helper.assertTrue(!ModAttachments.HEAD_START.has(back) && back.getVillagerData().getProfession() == VillagerProfession.NONE, "he came home with something");
		helper.assertTrue(Reputation.points(level, a, me.getUUID()) == standing && Reputation.points(level, b, me.getUUID()) == 0
			&& !Reputation.honours(level, a, me.getUUID()).contains("wayfinder"), "a failed rescue was thanked");
		String told = "Nobody came for " + name.getString() + ", who slipped away from the raiders and found the way home with nothing. The goods are lost.";
		helper.assertTrue(texts(seen, false).contains(told), "the chat: " + texts(seen, false));
		english(helper, texts(seen, false));
		english(helper, ((VillageHallBlockEntity) level.getBlockEntity(b)).chronicle().stream().map(e -> e.text().getString()).toList());
		helper.succeed();
	}

	/** Seven days and the stockade never built (nobody went): no carter was caged, yet he comes home by the hall and the arc fails. */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanNeverCaged"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanNeverCaged")
	public void afterSevenDaysHeComesHomeEvenIfNeverCaged(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		helper.assertTrue(LostCaravanStory.carter(level, s) == null, "a carter before the stockade stands");
		s.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS;
		int before = level.getEntitiesOfClass(Villager.class, new AABB(a).inflate(6, 40, 6)).size();
		LostCaravanStory.tick(level);
		helper.assertTrue(Arcs.running(level, a) == null, "the arc runs on after seven days");
		List<Villager> home = level.getEntitiesOfClass(Villager.class, new AABB(a).inflate(6, 40, 6), Villager::hasCustomName);
		Leftovers.after(helper, () -> home.forEach(Villager::discard));
		helper.assertTrue(home.size() == 1 && before == 0, "carters home: " + home.size() + " (villagers before " + before + ")");
		helper.assertTrue(((VillageHallBlockEntity) level.getBlockEntity(b)).chronicle().stream().anyMatch(e -> e.text().getString()
			.startsWith(home.get(0).getCustomName().getString() + " came home alone")), "the receiving village's chronicle doesn't name him");
		helper.succeed();
	}
}
