package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * QA (lane D, 2026-11-11) for the Rally Banner, written from the player guide (docs/guide/rally-banner.md, guard.md)
 * and the item's own lines, through the player's clicks: "your guards follow you anywhere" (a guard left more than 32
 * blocks behind is with you at once, one under 32 walks; a guard asleep in bed gets up), the banner only calls while
 * you carry it (put in a chest, nobody follows), letting the last guard go leaves a lowered banner that a click on the
 * air can't raise ("No guards on this banner yet"), and the tooltip and the raised line count the guards, one and many.
 * Each test is alone in its batch: guards look 24 blocks round, further than the gap between test areas.
 */
public class QaRallyBanner1111dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A guard employed at a Guard Post at {@code post}, standing next to it. */
	private static Villager guard(GameTestHelper helper, BlockPos post) {
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, post.east());
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return guard;
	}

	/** The player sneak-right-clicks each of {@code guards} with the banner in hand (through the interact packet). */
	private static void enlist(ServerPlayer player, List<Component> seen, Villager... guards) {
		player.setShiftKeyDown(true);
		for (Villager guard : guards) {
			QaStageFourGameTests.pressOn(player, guard, seen);
		}
		player.setShiftKeyDown(false);
	}

	private static void standAt(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		BlockPos abs = helper.absolutePos(rel);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
	}

	private static List<String> tooltip(ItemStack banner) {
		List<Component> lines = new ArrayList<>();
		banner.getItem().appendHoverText(banner, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
		return lines.stream().map(Component::getString).toList();
	}

	/**
	 * guard.md/rally-banner.md: raised, "your guards follow you anywhere". The player walks off 40 blocks (further than a
	 * guard can walk in a few seconds): the guard is beside them within 3 seconds, as a tame wolf catches up.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaRally1111dCatchUp"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRally1111dCatchUp")
	public void aGuardLeftFortyBlocksBehindIsWithThePlayerAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(2, 2, 2));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RALLY_BANNER));
		enlist(player, seen, guard);
		QaStageFourGameTests.pressAir(helper, player);
		helper.assertTrue(RallyBannerItem.rally(player.getMainHandItem()).raised(), "setup: not raised, saw " + QaStageFourGameTests.keys(seen));
		standAt(helper, player, new BlockPos(30, 2, 30)); // about 39 blocks from the guard, past the 32 of a catch-up
		double[] jump = {0};
		net.minecraft.world.phys.Vec3[] last = {guard.position()};
		helper.onEachTick(() -> {
			jump[0] = Math.max(jump[0], guard.position().distanceTo(last[0]));
			last[0] = guard.position();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(guard.distanceTo(player) < 8, "the guard is still " + guard.distanceTo(player) + " blocks behind");
			// Walking (even hurrying) covers well under a block a tick; caught up, the guard is put down near the player.
			helper.assertTrue(jump[0] > 10, "the guard walked the 39 blocks (its longest step " + jump[0] + "): not caught up at once");
			helper.assertTrue(Escorts.isEscorting(guard), "the guard isn't following the raised banner");
		});
	}

	/**
	 * The other side of the catch-up: 30 blocks off (under 32), the guard walks; it is not put beside the player at once
	 * (no step longer than 2 blocks), and it still arrives.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaRally1111dWalk"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRally1111dWalk")
	public void aGuardThirtyBlocksBehindWalksAfterThePlayer(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(2, 2, 6));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RALLY_BANNER));
		enlist(player, seen, guard);
		QaStageFourGameTests.pressAir(helper, player);
		standAt(helper, player, new BlockPos(28, 2, 18)); // about 28 blocks from the guard
		double[] jump = {0};
		net.minecraft.world.phys.Vec3[] last = {guard.position()};
		helper.onEachTick(() -> {
			jump[0] = Math.max(jump[0], guard.position().distanceTo(last[0]));
			last[0] = guard.position();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(guard.distanceTo(player) < 5, "the guard is " + guard.distanceTo(player) + " blocks from the player");
			helper.assertTrue(jump[0] < 2, "under 32 blocks the guard was put beside the player (a step of " + jump[0] + " blocks), not walked");
		});
	}

	/** guard.md: the banner calls them at any hour; a guard asleep in bed at night gets up and follows. */
	//$ gametest_ticks_batch AREA '800' '"qaRally1111dBed"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaRally1111dBed")
	public void aGuardAsleepInBedGetsUpForTheRaisedBanner(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(18000);
		Villager guard = guard(helper, new BlockPos(2, 2, 2));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RALLY_BANNER));
		enlist(player, seen, guard);
		BlockPos bed = new BlockPos(6, 2, 2);
		helper.setBlock(bed, Blocks.RED_BED.defaultBlockState());
		guard.startSleeping(helper.absolutePos(bed));
		helper.assertTrue(guard.isSleeping(), "setup: the guard isn't asleep");
		standAt(helper, player, new BlockPos(18, 2, 16));
		QaStageFourGameTests.pressAir(helper, player);
		helper.assertTrue(RallyBannerItem.rally(player.getMainHandItem()).raised(), "setup: not raised, saw " + QaStageFourGameTests.keys(seen));
		helper.succeedWhen(() -> {
			helper.assertFalse(guard.isSleeping(), "the guard is still asleep under a raised banner");
			helper.assertTrue(guard.distanceTo(player) < 5, "the guard got up but is " + guard.distanceTo(player) + " blocks from the player");
		});
	}

	/**
	 * The banner calls the guards while the player carries it: raised and then put away in a chest, it calls nobody and
	 * the guard stops following (rally-banner.md: "while it's raised, they follow you").
	 */
	//$ gametest_ticks_batch AREA '600' '"qaRally1111dChest"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRally1111dChest")
	public void aRaisedBannerPutInAChestCallsNobody(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(2, 2, 2));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RALLY_BANNER));
		enlist(player, seen, guard);
		QaStageFourGameTests.pressAir(helper, player);
		standAt(helper, player, new BlockPos(12, 2, 10));
		BlockPos chest = new BlockPos(14, 2, 10);
		helper.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.NORTH));
		boolean[] stored = {false};
		helper.onEachTick(() -> {
			if (!stored[0] && Escorts.isEscorting(guard) && guard.distanceTo(player) < 5) {
				ItemStack banner = player.getMainHandItem().copy();
				helper.assertTrue(RallyBannerItem.rally(banner).raised(), "the banner isn't raised");
				((ChestBlockEntity) helper.getBlockEntity(chest)).setItem(0, banner);
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				stored[0] = true;
				helper.runAfterDelay(60, () -> {
					helper.assertFalse(Escorts.isEscorting(guard), "3 seconds after the raised banner went into a chest, the guard still follows the player");
					helper.succeed();
				});
			}
		});
	}

	/**
	 * Letting the last guard go (a sneak-click on them again) leaves an empty banner that is lowered: the guard no longer
	 * follows, the tooltip says "Guards: 0 / 12" and "Lowered", and a right-click on the air doesn't raise it but says
	 * "No guards on this banner yet".
	 */
	//$ gametest_ticks_batch AREA '600' '"qaRally1111dLast"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRally1111dLast")
	public void lettingTheLastGuardGoLeavesALoweredBannerThatWontRise(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager guard = guard(helper, new BlockPos(2, 2, 2));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RALLY_BANNER));
		enlist(player, seen, guard);
		QaStageFourGameTests.pressAir(helper, player);
		helper.assertTrue(RallyBannerItem.rally(player.getMainHandItem()).raised(), "setup: not raised");
		standAt(helper, player, new BlockPos(14, 2, 12));
		boolean[] released = {false};
		helper.onEachTick(() -> {
			if (released[0] || !Escorts.isEscorting(guard) || guard.distanceTo(player) > 4) {
				return;
			}
			released[0] = true;
			enlist(player, seen, guard); // the second sneak-click lets them go
			ItemStack banner = player.getMainHandItem();
			RallyBannerItem.Rally rally = RallyBannerItem.rally(banner);
			helper.assertTrue(rally.guards().isEmpty(), "the guard is still on the banner: saw " + QaStageFourGameTests.keys(seen));
			Component line = QaStageFourGameTests.find(seen, "message.aliveworkplace.rally.released");
			helper.assertTrue(line != null, "no line for letting the guard go: saw " + QaStageFourGameTests.keys(seen));
			QaStageFourGameTests.assertRendered(helper, line, "off the banner");
			helper.assertFalse(rally.raised(), "an empty banner stays raised");
			List<String> tip = tooltip(banner);
			helper.assertTrue(tip.contains("Guards: 0 / 12") && tip.contains("Lowered"), "the empty banner's tooltip: " + tip);
			seen.clear();
			QaStageFourGameTests.pressAir(helper, player);
			helper.assertFalse(RallyBannerItem.rally(player.getMainHandItem()).raised(), "a right-click on the air raised an empty banner");
			Component none = QaStageFourGameTests.find(seen, "message.aliveworkplace.rally.no_guards");
			helper.assertTrue(none != null, "no line for raising an empty banner: saw " + QaStageFourGameTests.keys(seen));
			QaStageFourGameTests.assertRendered(helper, none, "No guards on this banner yet");
			helper.runAfterDelay(60, () -> {
				helper.assertFalse(Escorts.isEscorting(guard), "the guard let go still follows the player");
				helper.succeed();
			});
		});
	}

	/**
	 * The lines that count the guards, one and many: "Banner raised: 1 guard follows you" and "...: 2 guards follow
	 * you", the enlisting line "(2)", and the tooltip "Guards: 2 / 12" with "Raised: they follow you".
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRally1111dWords"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRally1111dWords")
	public void theRaisedLineAndTooltipCountOneGuardAndTwo(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager first = guard(helper, new BlockPos(2, 2, 2));
		Villager second = guard(helper, new BlockPos(8, 2, 2));
		first.setNoAi(true);
		second.setNoAi(true);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RALLY_BANNER));
		enlist(player, seen, first);
		QaStageFourGameTests.pressAir(helper, player);
		Component one = QaStageFourGameTests.find(seen, "message.aliveworkplace.rally.raised.one");
		helper.assertTrue(one != null, "no singular raised line for one guard: saw " + QaStageFourGameTests.keys(seen));
		helper.assertTrue(one.getString().equals("Banner raised: 1 guard follows you"), "one guard: '" + one.getString() + "'");
		QaStageFourGameTests.pressAir(helper, player); // lowered
		seen.clear();
		enlist(player, seen, second);
		Component enlisted = QaStageFourGameTests.find(seen, "message.aliveworkplace.rally.enlisted");
		helper.assertTrue(enlisted != null, "no line for enlisting the second guard: saw " + QaStageFourGameTests.keys(seen));
		QaStageFourGameTests.assertRendered(helper, enlisted, "is on the banner (2)");
		QaStageFourGameTests.pressAir(helper, player);
		Component many = QaStageFourGameTests.find(seen, "message.aliveworkplace.rally.raised");
		helper.assertTrue(many != null, "no raised line for two guards: saw " + QaStageFourGameTests.keys(seen));
		helper.assertTrue(many.getString().equals("Banner raised: 2 guards follow you"), "two guards: '" + many.getString() + "'");
		List<String> tip = tooltip(player.getMainHandItem());
		helper.assertTrue(tip.contains("Guards: 2 / 12") && tip.contains("Raised: they follow you"), "the raised banner's tooltip: " + tip);
		helper.succeed();
	}
}
