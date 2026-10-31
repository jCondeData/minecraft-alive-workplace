package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B691: right-clicking a Travel Post tells you how many other posts you know, "1 other post" in the singular and "2
 * other posts" in the plural, both on finding it and on clicking it again. Adapted from QA's
 * QaShopFerryBug1031Tests#rightClickingASecondPostSaysOneOtherPostSingular (lane-c-1031-0053).
 */
public class TravelPostWords1031GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A player who finds a second, then a third post reads "1 other post", then "2 other posts". */
	//$ gametest_ticks_batch HUGE '100' '"travelPostWords1031"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "travelPostWords1031")
	public void travelPostCountsOtherPostsInTheSingularAndPlural(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos a = new BlockPos(5, 2, 5);
		BlockPos b = new BlockPos(15, 2, 5);
		BlockPos c = new BlockPos(25, 2, 5);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		String[] names = {"Alder", "Brook", "Cedar"};
		BlockPos[] posts = {a, b, c};
		for (int i = 0; i < posts.length; i++) {
			helper.setBlock(posts[i], ModBlocks.TRAVEL_POST);
			network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(posts[i])), names[i]);
		}
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("postWords"));
		ServerPlayer p = client.player();
		click(helper, p, a);
		String first = said(client, "Found the Travel Post \"Alder\"");
		helper.assertTrue(first.endsWith("You know 0 other posts."), "the first post found says: " + first);

		click(helper, p, b);
		String found = said(client, "Found the Travel Post \"Brook\"");
		helper.assertTrue(found.equals("Found the Travel Post \"Brook\"! Ferrymen elsewhere can now take you here. You know 1 other post."),
			"the second post found says: " + found);
		click(helper, p, b);
		String known = said(client, "Travel Post \"Brook\"");
		helper.assertTrue(known.equals("Travel Post \"Brook\" · you know 1 other post"), "clicking it again says: " + known);

		click(helper, p, c);
		String third = said(client, "Found the Travel Post \"Cedar\"");
		helper.assertTrue(third.endsWith("You know 2 other posts."), "the third post found says: " + third);
		click(helper, p, c);
		String knownTwo = said(client, "Travel Post \"Cedar\"");
		helper.assertTrue(knownTwo.equals("Travel Post \"Cedar\" · you know 2 other posts"), "clicking the third again says: " + knownTwo);
		helper.succeed();
	}

	private static String said(QaStageFiveGameTests.Client client, String start) {
		String line = client.seen().stream().map(Component::getString).filter(s -> s.startsWith(start)).findFirst().orElse("<nothing>");
		client.seen().clear();
		return line;
	}

	private static void click(GameTestHelper helper, ServerPlayer p, BlockPos post) {
		BlockPos at = helper.absolutePos(post);
		p.moveTo(Vec3.atCenterOf(at.south()));
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.gameMode.useItemOn(p, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.SOUTH, 0.5), Direction.SOUTH, at, false));
	}
}
