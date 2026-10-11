package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.CaravanTrail;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * The Lost Caravan (ROADMAP 31.20): a caravan on a long road going missing (forced, and not when the switch is off or
 * the road is short), each chapter moving on by its objective, the carter following, waiting and joining, the
 * stockade's chest holding the shipment's own goods, the delivery quest paying, and the carter's own way home.
 */
public class LostCaravanGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	/** The receiving village: 320 blocks east of the sending one. */
	private static final int ROAD = 320;
	/** The test cage: its iron door, the straw at the back, the raiders' chest. */
	private static final BlockPos DOOR = new BlockPos(25, 2, 22);
	private static final BlockPos HAY = new BlockPos(26, 2, 25);
	private static final BlockPos CHEST = new BlockPos(20, 2, 25);

	/** Two villages 320 blocks apart, the sending one in the test area; the chance forced to 1 in 1. */
	private static BlockPos[] begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		int near = Arcs.NEAR;
		int chance = LostCaravanStory.CHANCE;
		int radius = VillageHalls.RADIUS;
		long travel = Caravans.MIN_TRAVEL;
		Arcs.NEAR = 0; // (the tests set the places down themselves)
		LostCaravanStory.CHANCE = 1;
		VillageHalls.RADIUS = 32;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = a.offset(ROAD, 0, 0);
		level.setBlock(b, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		Arcs.stopAll(level, a);
		Arcs.stopAll(level, b);
		((VillageHallBlockEntity) level.getBlockEntity(a)).setLastQuestDay(Long.MAX_VALUE / 2);
		((VillageHallBlockEntity) level.getBlockEntity(b)).setLastQuestDay(Long.MAX_VALUE / 2);
		Leftovers.after(helper, () -> {
			Arcs.NEAR = near;
			LostCaravanStory.CHANCE = chance;
			VillageHalls.RADIUS = radius;
			Caravans.MIN_TRAVEL = travel;
			Arcs.stopAll(level, a);
			Arcs.stopAll(level, b);
			Caravans.Data.get(level).clearRoad(a);
			Caravans.Data.get(level).remove(a);
			Caravans.Data.get(level).remove(b);
			level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
		});
		return new BlockPos[] {a, b};
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static boolean chronicled(ServerLevel level, BlockPos hall, String key) {
		return ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle().stream().anyMatch(e -> key(e.text()).equals(key));
	}

	/** The lost caravan's arc started by a forced loss of 32 logs and 12 bread from {@code a} to {@code b}. */
	private static ArcState lose(GameTestHelper helper, BlockPos a, BlockPos b) {
		ServerLevel level = helper.getLevel();
		boolean lost = LostCaravanStory.lose(level, a, b, List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BREAD, 12)), RandomSource.create(7));
		helper.assertTrue(lost, "the forced caravan didn't go missing");
		ArcState s = Arcs.running(level, a);
		helper.assertTrue(s != null && s.id.equals(LostCaravanStory.ARC.toString()), "no Lost Caravan running: " + s);
		return s;
	}

	/** The test cage the stockade's would be: an iron door, straw at the back and the raiders' chest; set down as built. */
	private static ArcState.ArcPlace cage(GameTestHelper helper, ArcState s) {
		helper.setBlock(DOOR, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(DOOR.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(HAY, Blocks.HAY_BLOCK);
		helper.setBlock(CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.ARROW, 5)); // the bandits' own things
		ArcState.ArcPlace p = s.place(LostCaravanStory.STOCKADE);
		p.spot = helper.absolutePos(new BlockPos(23, 2, 23));
		p.placed = 1;
		p.origin = p.spot;
		return p;
	}

	private static void open(GameTestHelper helper, boolean open) {
		for (BlockPos d : List.of(DOOR, DOOR.above())) {
			BlockState st = helper.getLevel().getBlockState(helper.absolutePos(d));
			helper.getLevel().setBlock(helper.absolutePos(d), st.setValue(DoorBlock.OPEN, open), 2);
		}
	}

	/**
	 * A caravan sets off from a village's Storehouse on a 320-block road: with {@code lostCaravanChance} 0 it's on the
	 * road; forced (1 in 1) the next day it goes missing: nothing on the road, the arc holds its own goods and its
	 * destination, both halls' chronicles have it overdue, and the wreck lies on its road a third of the way.
	 */
	//$ gametest_ticks_batch AREA '100' '"lostCaravanGoesMissing"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lostCaravanGoesMissing")
	public void aShipmentGoesMissingWhenForced(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		Caravans.MIN_TRAVEL = 0;
		helper.setBlock(new BlockPos(5, 2, 2), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(5, 2, 4), Blocks.CHEST);
		Container ours = helper.getBlockEntity(new BlockPos(5, 2, 4));
		ours.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		ours.setItem(1, new ItemStack(Items.OAK_LOG, 64));
		Caravans.Data data = Caravans.Data.get(level);
		helper.runAfterDelay(2, () -> {
			data.setWants(a, Component.literal("Thornholm"), List.of());
			data.setWants(b, Component.literal("Ashford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
			helper.assertTrue(data.toggleRoute(a, b), "no route");
			LostCaravanStory.CHANCE = 0;
			Caravans.round(level, a, null);
			helper.assertTrue(data.onTheRoad().stream().anyMatch(s -> s.from().equals(a)) && Arcs.running(level, a) == null,
				"with lostCaravanChance 0 the caravan didn't set off, or an arc started");
			data.clearRoad(a);
			data.sent(a, 0);
			LostCaravanStory.CHANCE = 1;
			Caravans.round(level, a, null);
			helper.assertTrue(data.onTheRoad().stream().noneMatch(s -> s.from().equals(a)), "the lost caravan is still on the road");
			ArcState s = Arcs.running(level, a);
			helper.assertTrue(s != null && s.id.equals(LostCaravanStory.ARC.toString()) && s.chapter == 0 && s.started, "the arc isn't in its first chapter: " + s);
			helper.assertTrue(b.equals(LostCaravanStory.to(s)), "the arc doesn't know where it was going");
			List<ItemStack> goods = LostCaravanStory.goods(s);
			helper.assertTrue(goods.stream().filter(g -> g.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum() == 32, "the arc holds " + goods);
			helper.assertTrue(ours.countItem(Items.OAK_LOG) == 64, "the store wasn't loaded: " + ours.countItem(Items.OAK_LOG));
			helper.assertTrue(chronicled(level, a, "chronicle.aliveworkplace.lost_caravan.overdue") && chronicled(level, b, "chronicle.aliveworkplace.lost_caravan.overdue"),
				"both halls don't have it overdue");
			ArcState.ArcPlace wreck = s.place(LostCaravanStory.WRECK);
			helper.assertTrue(wreck != null && wreck.spot.getX() - a.getX() >= ROAD / 3 - 1 && wreck.spot.getX() - a.getX() <= 150 && wreck.spot.getZ() == a.getZ(),
				"the wreck isn't on the road a third of the way: " + (wreck == null ? null : wreck.spot.subtract(a)));
			helper.assertTrue(s.places.stream().filter(p -> p.key.startsWith("trail")).count() == 5 && s.place(LostCaravanStory.STOCKADE) != null, "no stockade or five marks");
			// a short road never loses one, nor while an arc runs
			Arcs.stopAll(level, a);
			helper.assertFalse(LostCaravanStory.lose(level, a, a.offset(200, 0, 0), List.of(new ItemStack(Items.BREAD)), RandomSource.create(1)), "lost on a 200-block road");
			lose(helper, a, b);
			helper.assertFalse(LostCaravanStory.lose(level, b, a, List.of(new ItemStack(Items.BREAD)), RandomSource.create(1)), "lost while the other village has an arc running");
			helper.succeed();
		});
	}

	/**
	 * The whole story by its objectives: the wreck found (a player near it), the five marks followed, the carter caged
	 * with the shipment's own goods in the chest, the cage opened (he follows the player), he catches up from 46 blocks,
	 * waits from 80, and walks into the village: he joins it at Journeyman, both villages thank the helper (+150 and
	 * Wayfinder), he's +100 friendship, both chronicles tell it. The arc saves and reads back its own data.
	 */
	//$ gametest_ticks_batch AREA '100' '"lostCaravanChapters"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lostCaravanChapters")
	public void eachChapterMovesOnAndTobinComesHome(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		BlockPos ground = helper.absolutePos(new BlockPos(15, 2, 15));

		// 1. Overdue: the wreck is found
		ArcState.ArcPlace wreck = s.place(LostCaravanStory.WRECK);
		wreck.spot = ground;
		wreck.skipped = true;
		me.teleportTo(ground.getX() + 30.5, ground.getY(), ground.getZ() + 0.5);
		LostCaravanStory.tick(level);
		helper.assertTrue(s.chapter == 0, "found from 30 blocks off");
		me.teleportTo(ground.getX() + 5.5, ground.getY(), ground.getZ() + 0.5);
		LostCaravanStory.tick(level);
		helper.assertTrue(s.chapter == 1 && s.started, "finding the wreck didn't begin chapter 2: " + s.chapter);

		// 2. The tracks: the five marks in order
		Quest tracks = Stories.open(level, a).stream().filter(q -> s.id.equals(q.arc) && q.objectives.get(0) instanceof CaravanTrail.FollowTrail).findFirst().orElse(null);
		helper.assertTrue(tracks != null, "no follow_trail quest");
		List<ArcState.ArcPlace> marks = s.places.stream().filter(p -> p.key.startsWith("trail")).toList();
		for (int i = 0; i < marks.size(); i++) {
			marks.get(i).spot = ground.offset(-12 + 5 * i, 0, 8);
			marks.get(i).skipped = true;
		}
		for (ArcState.ArcPlace m : marks) {
			me.teleportTo(m.spot.getX() + 0.5, m.spot.getY(), m.spot.getZ() + 0.5);
			CaravanTrail.check(me);
		}
		helper.assertTrue(s.chapter == 2 && s.started, "the marks didn't begin chapter 3: " + s.chapter);
		helper.assertTrue(Stories.open(level, a).stream().anyMatch(q -> s.id.equals(q.arc) && q.objectives.get(0) instanceof LostCaravanStory.DeliverGoods d
			&& !d.village().isEmpty()), "no delivery quest naming the village");

		// 3. The captive: caged, the goods in the chest
		ArcState.ArcPlace stockade = cage(helper, s);
		Villager tobin = LostCaravanStory.setUp(level, s, stockade);
		helper.assertTrue(tobin != null && tobin.hasCustomName(), "no carter");
		helper.assertTrue(tobin.blockPosition().equals(helper.absolutePos(DOOR.offset(0, 0, 2))), "the carter isn't in the cage: " + tobin.blockPosition().subtract(helper.absolutePos(DOOR)));
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.countItem(Items.OAK_LOG) == 32 && chest.countItem(Items.BREAD) == 12 && chest.countItem(Items.ARROW) == 0,
			"the chest doesn't hold the shipment's own goods: " + chest.countItem(Items.OAK_LOG) + " logs, " + chest.countItem(Items.BREAD) + " bread");
		me.teleportTo(helper.absolutePos(DOOR).getX() + 0.5, ground.getY(), helper.absolutePos(DOOR).getZ() - 2.5);
		LostCaravanStory.tick(level);
		helper.assertTrue(LostCaravanStory.leader(s) == null, "he followed before the cage opened");
		open(helper, true);
		LostCaravanStory.tick(level);
		helper.assertTrue(me.getUUID().equals(LostCaravanStory.leader(s)), "opening the cage didn't make him follow the player");

		// it saves and reads back
		ArcState back = ArcState.load(s.save());
		helper.assertTrue(b.equals(LostCaravanStory.to(back)) && me.getUUID().equals(LostCaravanStory.leader(back)) && LostCaravanStory.goods(back).size() == 2,
			"the arc's own data didn't read back");

		// he waits further than 64 blocks, catches up from 32 to 64, and walks into the village
		BlockPos caged = tobin.blockPosition();
		me.teleportTo(a.getX() - 70.5, a.getY(), a.getZ() - 60.5);
		LostCaravanStory.tick(level);
		helper.assertTrue(tobin.blockPosition().equals(caged), "he didn't wait with the player 100 blocks off");
		me.teleportTo(a.getX() - 10.5, a.getY(), a.getZ() - 10.5);
		helper.assertTrue(tobin.distanceTo(me) > 32 && tobin.distanceTo(me) < 64, "the catch-up distance: " + tobin.distanceTo(me));
		LostCaravanStory.tick(level);
		helper.assertTrue(tobin.distanceTo(me) < 4, "he didn't catch up: " + tobin.distanceTo(me));
		helper.assertTrue(Arcs.running(level, a) == null, "the arc didn't end when he came home");
		helper.assertTrue(tobin.isAlive() && tobin.getTags().stream().noneMatch(t -> t.startsWith(Arcs.TAG) || t.startsWith("aliveworkplace_role_")),
			"he's still the arc's: " + tobin.getTags());
		helper.assertTrue(ModAttachments.HEAD_START.getOrElse(tobin, 1) == 3, "he doesn't start at Journeyman");
		helper.assertTrue(Reputation.points(level, a, me.getUUID()) >= 150 && Reputation.points(level, b, me.getUUID()) >= 150,
			"standing: " + Reputation.points(level, a, me.getUUID()) + " and " + Reputation.points(level, b, me.getUUID()));
		helper.assertTrue(Reputation.honours(level, a, me.getUUID()).contains("wayfinder") && Reputation.honours(level, b, me.getUUID()).contains("wayfinder"), "no Wayfinder");
		helper.assertTrue(Friendship.points(tobin, me.getUUID()) >= 100, "friendship: " + Friendship.points(tobin, me.getUUID()));
		helper.assertTrue(chronicled(level, a, "chronicle.aliveworkplace.lost_caravan.ending") && chronicled(level, b, "chronicle.aliveworkplace.lost_caravan.ending"),
			"not in both chronicles");
		tobin.discard();
		me.discard();
		helper.succeed();
	}

	/**
	 * The second quest: the goods from the stockade's chest, carried to the receiving hall, go into its Storehouse; the quest
	 * pays 8 emeralds and 50 standing there. Without every stack it doesn't count; not at the sending hall.
	 */
	//$ gametest_ticks_batch AREA '100' '"lostCaravanDelivery"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lostCaravanDelivery")
	public void theDeliveryQuestPays(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		level.setBlock(b.offset(2, 0, 0), ModBlocks.STOREHOUSE.defaultBlockState(), 3); // the receiving village's Storehouse and its chest
		level.setBlock(b.offset(2, 0, 2), Blocks.CHEST.defaultBlockState(), 3);
		helper.assertTrue(Caravans.storehouse(level, b).contains(b.offset(2, 0, 2)), "the receiving village's store isn't found");
		helper.assertTrue(Arcs.next(level, a) && Arcs.next(level, a) && s.chapter == 2, "not in chapter 3: " + s.chapter);
		Quest goods = Stories.open(level, a).stream().filter(q -> s.id.equals(q.arc) && q.objectives.get(0) instanceof LostCaravanStory.DeliverGoods).findFirst().orElse(null);
		helper.assertTrue(goods != null, "no delivery quest");
		helper.assertTrue(Objectives.parse(goods.objectives.get(0).json()).equals(goods.objectives.get(0)), "deliver_goods doesn't read back the same");
		me.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		me.getInventory().add(new ItemStack(Items.BREAD, 6));
		me.teleportTo(b.getX() + 3.5, b.getY(), b.getZ() + 0.5);
		LostCaravanStory.check(me);
		helper.assertTrue(Stories.open(level, a).contains(goods) && me.getInventory().countItem(Items.OAK_LOG) == 32, "counted without all the bread");
		me.getInventory().add(new ItemStack(Items.BREAD, 6));
		me.teleportTo(a.getX() + 3.5, a.getY(), a.getZ() + 0.5);
		LostCaravanStory.check(me);
		helper.assertTrue(Stories.open(level, a).contains(goods), "counted at the sending hall");
		int emeralds = me.getInventory().countItem(Items.EMERALD);
		me.teleportTo(b.getX() + 3.5, b.getY(), b.getZ() + 0.5);
		LostCaravanStory.check(me);
		helper.assertTrue(!Stories.open(level, a).contains(goods), "the delivery quest is still open");
		helper.assertTrue(me.getInventory().countItem(Items.OAK_LOG) == 0 && me.getInventory().countItem(Items.BREAD) == 0, "the goods weren't taken");
		Container store = (Container) level.getBlockEntity(b.offset(2, 0, 2));
		helper.assertTrue(store.countItem(Items.OAK_LOG) == 32 && store.countItem(Items.BREAD) == 12,
			"the goods aren't in the receiving village's store: " + store.countItem(Items.OAK_LOG) + " logs, " + store.countItem(Items.BREAD) + " bread");
		helper.assertTrue(Reputation.points(level, b, me.getUUID()) == 50, "standing there: " + Reputation.points(level, b, me.getUUID()));
		helper.assertTrue(me.getInventory().countItem(Items.EMERALD) - emeralds == 8 || !io.github.jcondedata.aliveworkplace.work.Bank.EXTENSION.call(bank -> false, true),
			"paid " + (me.getInventory().countItem(Items.EMERALD) - emeralds) + " emeralds");
		helper.assertTrue(chronicled(level, b, "chronicle.aliveworkplace.lost_caravan.delivered"), "not in the receiving chronicle");
		store.clearContent();
		level.setBlock(b.offset(2, 0, 2), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(b.offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 3);
		me.discard();
		helper.succeed();
	}

	/**
	 * Seven days and nobody came: the carter finds his own way home with nothing (a plain villager by the hall, no
	 * head start), the caged one is gone, the chest is empty, the arc fails and both chronicles say so.
	 */
	//$ gametest_ticks_batch AREA '100' '"lostCaravanOwnWay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lostCaravanOwnWay")
	public void afterSevenDaysHeFindsHisOwnWay(GameTestHelper helper) {
		BlockPos[] ab = begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = lose(helper, a, b);
		Villager tobin = LostCaravanStory.setUp(level, s, cage(helper, s));
		Component name = tobin.getCustomName();
		s.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS + 1;
		LostCaravanStory.tick(level);
		helper.assertTrue(Arcs.running(level, a) == s, "it failed before the seventh day");
		s.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS;
		LostCaravanStory.tick(level);
		helper.assertTrue(Arcs.running(level, a) == null, "the arc runs on after seven days");
		helper.assertTrue(!tobin.isAlive(), "the caged carter is still there");
		List<Villager> home = level.getEntitiesOfClass(Villager.class, new AABB(a).inflate(6, 40, 6), v -> v.getCustomName() != null && v.getCustomName().equals(name));
		helper.assertTrue(home.size() == 1 && ModAttachments.HEAD_START.getOrElse(home.get(0), 1) == 1
			&& home.get(0).getTags().stream().noneMatch(t -> t.startsWith(Arcs.TAG)), "he isn't home, plainly: " + home.size());
		helper.assertTrue(((Container) helper.getBlockEntity(CHEST)).isEmpty(), "the goods are still in the chest");
		helper.assertTrue(chronicled(level, a, "chronicle.aliveworkplace.lost_caravan.own_way") && chronicled(level, b, "chronicle.aliveworkplace.lost_caravan.own_way"),
			"not in both chronicles");
		home.forEach(Villager::discard);
		helper.succeed();
	}
}
