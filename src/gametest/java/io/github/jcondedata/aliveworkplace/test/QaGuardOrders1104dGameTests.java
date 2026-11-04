package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Cavalry;
import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.GuardPatrol;
import io.github.jcondedata.aliveworkplace.guard.GuardRally;
import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-04) for guards' orders, written from the player guide (docs/guide/guard.md and
 * training-dummy.md), not from the code: the Training Dummy's 12-block reach and its Expert cap, no sparring at night,
 * the Patrol Map's 8 points, its 64-block reach, night, a blank map and a reload, the 24 blocks a guard looks for a
 * horse, the bell's minute and a half, and a raised Rally Banner that never turns a guard on a villager or a pet.
 * Each test is alone in its batch: guards look 24 blocks round, further than the gap between test areas.
 */
public class QaGuardOrders1104dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A guard employed at a Guard Post at {@code post}, standing next to it, by day. */
	private static Villager guard(GameTestHelper helper, BlockPos post) {
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, post.east());
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return guard;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Vec3 center(GameTestHelper helper, BlockPos rel) {
		return Vec3.atBottomCenterOf(helper.absolutePos(rel));
	}

	private static ItemStack map(GameTestHelper helper, List<BlockPos> absolutePoints) {
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(helper.getLevel().dimension().location()), absolutePoints));
		return map;
	}

	/** The player sneak-right-clicks {@code guard} holding {@code held}, through the interact packet. */
	private static void sneakClick(ServerPlayer player, Villager guard, ItemStack held, List<Component> seen) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.setShiftKeyDown(true);
		QaStageFourGameTests.pressOn(player, guard, seen);
		player.setShiftKeyDown(false);
	}

	// --- The Training Dummy -----------------------------------------------------------------------------------------

	/** training-dummy.md: "within 12 blocks of the grindstone" — a dummy exactly 12 blocks off is used. */
	//$ gametest_ticks_batch AREA '1400' '"qaGo4dDummy12"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaGo4dDummy12")
	public void aDummyTwelveBlocksFromThePostIsUsed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(2, 2, 14));
		helper.setBlock(new BlockPos(14, 2, 14), ModBlocks.TRAINING_DUMMY);
		helper.succeedWhen(() -> helper.assertTrue(ModAttachments.DUMMY_HITS.getOrElse(guard, 0) >= 1,
			"the guard never sparred with the dummy 12 blocks from the post; the guard is at " + helper.relativePos(guard.blockPosition())));
	}

	/** The other side of the boundary: a dummy 13 blocks from the post is never sparred with. */
	//$ gametest_ticks_batch AREA '1000' '"qaGo4dDummy13"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaGo4dDummy13")
	public void aDummyThirteenBlocksFromThePostIsNotUsed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(2, 2, 14));
		helper.setBlock(new BlockPos(15, 2, 14), ModBlocks.TRAINING_DUMMY);
		helper.onEachTick(() -> {
			if (ModAttachments.DUMMY_HITS.getOrElse(guard, 0) > 0) {
				helper.fail("the guard sparred with a dummy 13 blocks from the post");
			}
		});
		helper.runAfterDelay(900, helper::succeed);
	}

	/**
	 * guard.md: guards level up at the dummy "as far as Expert". A Journeyman one point short of Expert becomes an
	 * Expert in one session at the dummy and goes no further: still an Expert, with fewer points than a Master needs.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaGo4dDummyExpert"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaGo4dDummyExpert")
	public void aJourneymanSparsUpToExpertAndNoFurther(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(14, 2, 14));
		guard.setVillagerData(guard.getVillagerData().setLevel(3));
		guard.setVillagerXp(VillagerData.getMaxXpPerLevel(3) - 1);
		helper.setBlock(new BlockPos(18, 2, 14), ModBlocks.TRAINING_DUMMY);
		helper.onEachTick(() -> {
			if (guard.getVillagerData().getLevel() > 4) {
				helper.fail("sparring made a Master: level " + guard.getVillagerData().getLevel() + ", " + guard.getVillagerXp() + " xp");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.DUMMY_HITS.getOrElse(guard, 0) >= GuardPatrol.HITS,
				"not a whole session yet: " + ModAttachments.DUMMY_HITS.getOrElse(guard, 0) + " hits");
			helper.assertTrue(guard.getVillagerData().getLevel() == 4, "after a session the Journeyman is level " + guard.getVillagerData().getLevel());
			helper.assertTrue(guard.getVillagerXp() < VillagerData.getMaxXpPerLevel(4), "sparring took the Expert to " + guard.getVillagerXp() + " xp");
			helper.assertFalse(GuardPatrol.canTrain(guard), "the Expert can still learn from the dummy");
		});
	}

	/**
	 * training-dummy.md: guards spar "by day; at night they stay by their post". At night a guard with a dummy 6 blocks
	 * off never hits it, and stays near the post; once it's day again, they do spar (so the dummy was within reach).
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaGo4dDummyNight"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaGo4dDummyNight")
	public void noSparringAtNight(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(18000);
		BlockPos post = new BlockPos(14, 2, 14);
		Villager guard = guard(helper, post);
		helper.setBlock(new BlockPos(20, 2, 14), ModBlocks.TRAINING_DUMMY);
		boolean[] day = {false};
		double[] farthest = {0};
		helper.onEachTick(() -> {
			if (!day[0]) {
				if (ModAttachments.DUMMY_HITS.getOrElse(guard, 0) > 0) {
					helper.fail("the guard sparred at night");
				}
				farthest[0] = Math.max(farthest[0], horizontal(guard.position(), center(helper, post)));
			}
		});
		helper.runAfterDelay(700, () -> {
			helper.assertTrue(helper.getLevel().isNight(), "setup: not night");
			helper.assertTrue(farthest[0] < 12, "at night the guard went " + farthest[0] + " blocks from the post");
			day[0] = true;
			helper.setDayTime(2000);
		});
		helper.succeedWhen(() -> helper.assertTrue(day[0] && ModAttachments.DUMMY_HITS.getOrElse(guard, 0) > 0,
			"day " + day[0] + ": the guard didn't spar by day either, hits " + ModAttachments.DUMMY_HITS.getOrElse(guard, 0)));
	}

	// --- The Patrol Map ---------------------------------------------------------------------------------------------

	/**
	 * guard.md: "right-click the ground at up to 8 places". The ninth click is refused with the limit's line, and the
	 * map keeps the first eight in the order they were clicked.
	 */
	//$ gametest_batch AREA '"qaGo4dNinth"'
	@GameTest(template = AREA, batch = "qaGo4dNinth")
	public void aNinthPointIsRefusedAndTheFirstEightKept(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PATROL_MAP));
		List<BlockPos> expected = new ArrayList<>();
		InteractionResult ninth = InteractionResult.PASS;
		for (int i = 0; i < 9; i++) {
			BlockPos ground = new BlockPos(2 + i * 3, 1, 6 + i);
			helper.setBlock(ground, Blocks.GRASS_BLOCK);
			InteractionResult result = QaStageFourGameTests.press(helper, player, ground, Direction.UP);
			if (i < 8) {
				expected.add(helper.absolutePos(ground).above());
			} else {
				ninth = result;
			}
		}
		List<BlockPos> points = PatrolMapItem.route(player.getMainHandItem()).points();
		helper.assertTrue(points.equals(expected), "the map holds " + points + ", the first eight clicked were " + expected);
		helper.assertTrue(ninth == InteractionResult.FAIL, "the ninth click answered " + ninth);
		Component full = QaStageFourGameTests.find(seen, "message.aliveworkplace.patrol.full");
		helper.assertTrue(full != null, "no 'at most' line for the ninth point: saw " + QaStageFourGameTests.keys(seen));
		QaStageFourGameTests.assertRendered(helper, full, "8");
		helper.succeed();
	}

	/**
	 * guard.md: "By day they walk the route, within 64 blocks of their post." A route whose first point is 66 blocks
	 * off and second 25 blocks off: the guard walks to the near one, over and over, and never sets off for the far one.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaGo4dReach"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaGo4dReach")
	public void aRoutePointBeyondSixtyFourBlocksIsNeverWalked(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		Villager guard = guard(helper, post);
		BlockPos near = new BlockPos(20, 2, 20);
		BlockPos far = helper.absolutePos(post).east(66);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		sneakClick(player, guard, map(helper, List.of(far, helper.absolutePos(near))), seen);
		helper.assertTrue(ModAttachments.PATROL_ROUTE.getOrElse(guard, List.of()).size() == 2, "setup: no route, saw " + QaStageFourGameTests.keys(seen));
		player.teleportTo(helper.absolutePos(post).getX() + 0.5, helper.absolutePos(post).getY() + 12, helper.absolutePos(post).getZ() + 0.5);
		int[] visits = {0};
		boolean[] there = {false};
		helper.onEachTick(() -> {
			double fromPost = horizontal(guard.position(), center(helper, post));
			if (fromPost > 34) {
				helper.fail("the guard set off for the point 66 blocks out: " + fromPost + " blocks from the post");
			}
			boolean atNear = horizontal(guard.position(), center(helper, near)) < 3;
			if (atNear && !there[0]) {
				visits[0]++;
			}
			if (horizontal(guard.position(), center(helper, near)) > 5) {
				there[0] = false;
			} else if (atNear) {
				there[0] = true;
			}
		});
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(visits[0] >= 1, "the guard never walked to the point 25 blocks off");
			helper.succeed();
		});
	}

	/**
	 * guard.md: "at night they stay by the post." A guard with a route out to the far corner stays near the post at night;
	 * by day the same guard walks it (so the route was theirs all along).
	 */
	//$ gametest_ticks_batch AREA '2200' '"qaGo4dRouteNight"'
	@GameTest(template = AREA, timeoutTicks = 2200, batch = "qaGo4dRouteNight")
	public void theRouteWaitsTillDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(18000);
		BlockPos post = new BlockPos(14, 2, 14);
		Villager guard = guard(helper, post);
		BlockPos corner = new BlockPos(27, 2, 27);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PatrolMapItem.giveTo(player, guard, map(helper, List.of(helper.absolutePos(corner))));
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.assertTrue(ModAttachments.PATROL_ROUTE.has(guard), "setup: no route");
		boolean[] day = {false};
		double[] farthest = {0};
		helper.onEachTick(() -> {
			if (!day[0]) {
				farthest[0] = Math.max(farthest[0], horizontal(guard.position(), center(helper, post)));
			}
		});
		helper.runAfterDelay(800, () -> {
			helper.assertTrue(farthest[0] < 12, "at night the guard walked " + farthest[0] + " blocks from the post");
			day[0] = true;
			helper.setDayTime(2000);
		});
		helper.succeedWhen(() -> helper.assertTrue(day[0] && horizontal(guard.position(), center(helper, corner)) < 3,
			"day " + day[0] + ": the guard is at " + helper.relativePos(guard.blockPosition()) + ", not at the route's point; route " + ModAttachments.PATROL_ROUTE.get(guard)
			+ ", activity " + guard.getBrain().getActiveNonCoreActivity() + ", night " + helper.getLevel().isNight() + ", day time " + helper.getLevel().getDayTime()));
	}

	/**
	 * guard.md: "A blank map takes the route away." Sneak-right-clicked with a blank map, a guard who walked to the far
	 * corner says he's back to patrolling round the post, loses the route and doesn't go back out there.
	 */
	//$ gametest_ticks_batch AREA '2200' '"qaGo4dBlank"'
	@GameTest(template = AREA, timeoutTicks = 2200, batch = "qaGo4dBlank")
	public void aBlankMapTakesTheRouteAway(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(3, 2, 3);
		Villager guard = guard(helper, post);
		BlockPos corner = new BlockPos(26, 2, 26);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		sneakClick(player, guard, map(helper, List.of(helper.absolutePos(corner))), seen);
		helper.assertTrue(ModAttachments.PATROL_ROUTE.has(guard), "setup: no route, saw " + QaStageFourGameTests.keys(seen));
		player.teleportTo(helper.absolutePos(post).getX() + 0.5, helper.absolutePos(post).getY() + 12, helper.absolutePos(post).getZ() + 0.5);
		long[] takenAt = {-1};
		helper.onEachTick(() -> {
			if (takenAt[0] < 0 && horizontal(guard.position(), center(helper, corner)) < 3) {
				// Out at the corner: the blank map, then home.
				sneakClick(player, guard, new ItemStack(ModItems.PATROL_MAP), seen);
				takenAt[0] = helper.getTick();
				Component taken = QaStageFourGameTests.find(seen, "message.aliveworkplace.patrol.taken");
				helper.assertTrue(taken != null, "no line for the blank map: saw " + QaStageFourGameTests.keys(seen));
				QaStageFourGameTests.assertRendered(helper, taken, guard.getDisplayName().getString());
				helper.assertFalse(ModAttachments.PATROL_ROUTE.has(guard), "the blank map left the route");
				player.teleportTo(helper.absolutePos(post).getX() + 0.5, helper.absolutePos(post).getY() + 12, helper.absolutePos(post).getZ() + 0.5);
			}
			if (takenAt[0] >= 0 && helper.getTick() - takenAt[0] > 600 && horizontal(guard.position(), center(helper, corner)) < 3) {
				helper.fail("back at the corner of a route taken away");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(takenAt[0] >= 0, "never reached the corner to take the route away");
			helper.assertTrue(helper.getTick() - takenAt[0] > 1200, "waiting");
			helper.assertTrue(horizontal(guard.position(), center(helper, post)) < 20, "the guard isn't back round the post: "
				+ helper.relativePos(guard.blockPosition()));
		});
	}

	/**
	 * Don't break saves: a guard with a route, saved and loaded (a chunk unloading, a restart), still has the route and
	 * walks it.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaGo4dReload"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaGo4dReload")
	public void aRouteSurvivesASaveAndIsStillWalked(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(3, 2, 3);
		Villager before = guard(helper, post);
		BlockPos corner = new BlockPos(24, 2, 24);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PatrolMapItem.giveTo(player, before, map(helper, List.of(helper.absolutePos(corner))));
		helper.getLevel().getServer().getPlayerList().remove(player);
		CompoundTag tag = before.saveWithoutId(new CompoundTag());
		before.discard();
		Villager guard = EntityType.VILLAGER.create(helper.getLevel());
		guard.load(tag);
		helper.getLevel().addFreshEntity(guard);
		helper.assertTrue(ModAttachments.PATROL_ROUTE.getOrElse(guard, List.of()).equals(List.of(helper.absolutePos(corner))),
			"the reloaded guard's route: " + ModAttachments.PATROL_ROUTE.get(guard));
		helper.succeedWhen(() -> helper.assertTrue(horizontal(guard.position(), center(helper, corner)) < 3,
			"the reloaded guard is at " + helper.relativePos(guard.blockPosition()) + ", never walked the route"));
	}

	// --- On horseback -----------------------------------------------------------------------------------------------

	private static AbstractHorse mount(GameTestHelper helper, EntityType<? extends AbstractHorse> type, BlockPos at) {
		AbstractHorse horse = helper.spawn(type, at);
		horse.setTamed(true);
		horse.equipSaddle(new ItemStack(Items.SADDLE), null);
		horse.setNoAi(true);
		return horse;
	}

	/**
	 * guard.md: a tame, saddled horse, donkey or mule "within 24 blocks of the post". One 24 blocks off is the guard's;
	 * one 26 blocks off isn't; a mule closer by is picked over nothing, and over the far horse.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaGo4dHorseRange"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGo4dHorseRange")
	public void aHorseTwentyFourBlocksOffIsRiddenOneTwentySixIsNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos post = new BlockPos(2, 2, 14);
		Villager guard = guard(helper, post);
		guard.setNoAi(true);
		AbstractHorse at24 = mount(helper, EntityType.HORSE, new BlockPos(26, 2, 14));
		helper.assertTrue(Cavalry.freeHorse(helper.getLevel(), guard, helper.absolutePos(post)) == at24,
			"a horse 24 blocks from the post wasn't found");
		at24.discard();
		AbstractHorse at26 = mount(helper, EntityType.HORSE, new BlockPos(28, 2, 14));
		helper.assertTrue(Cavalry.freeHorse(helper.getLevel(), guard, helper.absolutePos(post)) == null,
			"a horse 26 blocks from the post was found");
		AbstractHorse mule = mount(helper, EntityType.MULE, new BlockPos(20, 2, 14));
		helper.assertTrue(Cavalry.freeHorse(helper.getLevel(), guard, helper.absolutePos(post)) == mule,
			"a mule 18 blocks off wasn't found (with a horse 26 off): " + Cavalry.freeHorse(helper.getLevel(), guard, helper.absolutePos(post)));
		at26.discard();
		mule.discard();
		helper.succeed();
	}

	// --- The bell ---------------------------------------------------------------------------------------------------

	/**
	 * guard.md: "Ring the bell and the guards rush to it and fight anything near it for a minute and a half." Still
	 * answering it 1790 ticks after hearing it, at the bell; done by 1820, and nothing calls them there any more.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaGo4dBell"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGo4dBell")
	public void theBellCallsGuardsForAMinuteAndAHalf(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(3, 2, 3));
		BlockPos bell = new BlockPos(20, 2, 20);
		helper.setBlock(bell, Blocks.BELL);
		helper.runAfterDelay(5, () -> ((BellBlock) Blocks.BELL).attemptToRing(helper.getLevel(), helper.absolutePos(bell), Direction.NORTH));
		long[] heard = {-1};
		boolean[] checked = {false, false};
		helper.onEachTick(() -> {
			long now = helper.getTick();
			if (heard[0] < 0 && GuardRally.isRallying(guard)) {
				heard[0] = now;
			}
			if (heard[0] < 0) {
				return;
			}
			if (!checked[0] && now - heard[0] >= 1790) {
				checked[0] = true;
				helper.assertTrue(GuardRally.isRallying(guard), "the guard stopped answering the bell before a minute and a half");
				helper.assertTrue(guard.position().closerThan(center(helper, bell), 5), "rallying but not at the bell: "
					+ helper.relativePos(guard.blockPosition()));
			}
			if (!checked[1] && now - heard[0] >= 1820) {
				checked[1] = true;
				helper.assertFalse(GuardRally.isRallying(guard), "still answering the bell " + (now - heard[0]) + " ticks after it rang");
				helper.assertTrue(GuardRally.rallyPoint(guard).isEmpty(), "still a rally point");
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(checked[0] && checked[1], "heard at " + heard[0] + ", now " + helper.getTick()));
	}

	// --- The Rally Banner -------------------------------------------------------------------------------------------

	/**
	 * guard.md: under a raised banner "they fight whatever goes for you, or whatever you go for"; "Players, villagers,
	 * pets and Pokémon are always safe from them." The player hits a villager and their own tamed wolf (and a tamed wolf
	 * is set on them): the following guard leaves both alone. Then the player hits a pig, and the guard does go for it.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaGo4dRallySafe"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaGo4dRallySafe")
	public void aRallyBannerNeverTurnsAGuardOnAVillagerOrAPet(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(3, 2, 3));
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		sneakClick(player, guard, banner, seen);
		ItemStack held = player.getMainHandItem();
		helper.assertTrue(RallyBannerItem.rally(held).guards().contains(guard.getUUID()), "setup: not enlisted, saw " + QaStageFourGameTests.keys(seen));
		QaStageFourGameTests.pressAir(helper, player);
		helper.assertTrue(RallyBannerItem.rally(player.getMainHandItem()).raised(), "setup: not raised, saw " + QaStageFourGameTests.keys(seen));
		player.teleportTo(helper.absolutePos(new BlockPos(14, 2, 14)).getX() + 0.5, helper.absolutePos(new BlockPos(14, 2, 14)).getY(),
			helper.absolutePos(new BlockPos(14, 2, 14)).getZ() + 0.5);
		Villager neighbour = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 14));
		neighbour.setNoAi(true);
		Wolf pet = helper.spawn(EntityType.WOLF, new BlockPos(14, 2, 17));
		pet.tame(player);
		pet.setNoAi(true);
		Wolf someonesPet = helper.spawn(EntityType.WOLF, new BlockPos(11, 2, 14));
		someonesPet.setOwnerUUID(java.util.UUID.randomUUID());
		someonesPet.setTame(true, true);
		someonesPet.setNoAi(true);
		someonesPet.setTarget(player);
		Pig[] pig = {null};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (pig[0] == null) {
				if (t % 40 == 0) {
					player.setLastHurtMob(t % 80 == 0 ? neighbour : pet);
				}
				for (var friend : List.of(neighbour, pet, someonesPet, player)) {
					if (friend.getHealth() < friend.getMaxHealth()) {
						helper.fail("the guard hurt " + friend.getName().getString() + ": " + friend.getHealth() + " of " + friend.getMaxHealth());
					}
				}
				if (t >= 600) {
					helper.assertTrue(Escorts.isEscorting(guard) && guard.distanceTo(player) < 12, "setup: the guard isn't following the player, "
						+ guard.distanceTo(player) + " blocks off");
					pig[0] = helper.spawn(EntityType.PIG, new BlockPos(14, 2, 11));
					pig[0].setNoAi(true);
				}
			} else if (t % 20 == 0) {
				player.setLastHurtMob(pig[0]);
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(pig[0] != null && !pig[0].isAlive(), "the guard didn't go for the pig the player hit"));
	}

	/**
	 * guard.md: "Lower it and they go back to their posts." A guard who followed the raised banner 20-odd blocks off
	 * walks back round their post once the player lowers it with a right-click.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaGo4dRallyHome"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaGo4dRallyHome")
	public void aLoweredBannerSendsTheGuardBackToTheirPost(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(3, 2, 3);
		Villager guard = guard(helper, post);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		sneakClick(player, guard, new ItemStack(ModItems.RALLY_BANNER), seen);
		QaStageFourGameTests.pressAir(helper, player);
		helper.assertTrue(RallyBannerItem.rally(player.getMainHandItem()).raised(), "setup: not raised, saw " + QaStageFourGameTests.keys(seen));
		BlockPos away = helper.absolutePos(new BlockPos(26, 2, 26));
		player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		long[] lowered = {-1};
		helper.onEachTick(() -> {
			if (lowered[0] < 0 && guard.distanceTo(player) < 5) {
				QaStageFourGameTests.pressAir(helper, player);
				helper.assertFalse(RallyBannerItem.rally(player.getMainHandItem()).raised(), "a second right-click didn't lower it");
				Component line = QaStageFourGameTests.find(seen, "message.aliveworkplace.rally.lowered");
				helper.assertTrue(line != null, "no line for lowering it: saw " + QaStageFourGameTests.keys(seen));
				QaStageFourGameTests.assertRendered(helper, line, "posts");
				lowered[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(lowered[0] >= 0, "the guard never caught up with the player: " + guard.distanceTo(player) + " blocks off");
			helper.assertFalse(Escorts.isEscorting(guard), "still following a lowered banner");
			helper.assertTrue(horizontal(guard.position(), center(helper, post)) < 15, "the guard is " + horizontal(guard.position(), center(helper, post))
				+ " blocks from the post");
		});
	}
}
