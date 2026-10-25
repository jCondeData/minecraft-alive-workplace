package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane-b-1025-0937) on B394's fix ("useWithoutItem now uses the off hand's Book and Quill when the main hand is empty"),
 * at its edges the fixer's tests leave out: the Bench's change sits in BuildersBenchBlock, the class the Postal Desk also
 * uses, so an off-hand book must not stop the desk handing out waiting parcels; and an off-hand book on a Builder's Bench
 * with no build answers as the main hand's does ("no build at this bench") and keeps the book. Clicks go through the
 * player's own path, main hand then off hand (QaStageFourGameTests.press).
 */
public class QaOffHandBookGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** A parcel waiting at an old Postal Desk, the player's main hand empty and a Book and Quill in the off hand. */
	//$ gametest_batch AREA '"qaOffBookDesk"'
	@GameTest(template = AREA, batch = "qaOffBookDesk")
	public void anOffHandBookDoesNotStopThePostalDeskHandingOutParcels(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		PostOffice office = PostOffice.get(level.getServer());
		Parcel parcel = office.post(UUID.randomUUID(), "Alex", player.getUUID(), player.getGameProfile().getName(),
			GlobalPos.of(level.dimension(), helper.absolutePos(BlockPos.ZERO)), level.getGameTime(), List.of(new ItemStack(Items.DIAMOND, 3)));
		parcel.setStatus(Parcel.Status.IN_TRANSIT);
		office.dawn(level.getServer());
		BlockPos desk = new BlockPos(3, 2, 3);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WRITABLE_BOOK));
		QaStageFourGameTests.press(helper, player, desk, Direction.NORTH);
		helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3, "the waiting parcel's diamonds weren't handed out: "
			+ player.getInventory().countItem(Items.DIAMOND) + ", the player saw " + QaStageFourGameTests.keys(seen));
		helper.assertTrue(office.parcel(parcel.id()) == null, "the parcel is still waiting");
		helper.assertTrue(player.getOffhandItem().is(Items.WRITABLE_BOOK) && player.getOffhandItem().getCount() == 1,
			"the off-hand book changed at the desk: " + player.getOffhandItem());
		helper.succeed();
	}

	/**
	 * A Builder's Bench whose builder has no build: an off-hand Book and Quill (main hand empty) gets the main hand's
	 * answer, "no build at this bench", and stays a blank Book and Quill (nothing written, nothing used up).
	 */
	//$ gametest_batch AREA '"qaOffBookNoBuild"'
	@GameTest(template = AREA, batch = "qaOffBookNoBuild")
	public void anOffHandBookOnABenchWithNoBuildSaysSoAndIsKept(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(3, 2, 3);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.south());
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(bench));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WRITABLE_BOOK, 2));
		QaStageFourGameTests.press(helper, player, bench, Direction.NORTH);
		helper.assertTrue(QaStageFourGameTests.saw(seen, "message.aliveworkplace.site_list.no_build_at_bench"),
			"an off-hand book on a bench with no build: saw " + QaStageFourGameTests.keys(seen));
		helper.assertTrue(player.getOffhandItem().is(Items.WRITABLE_BOOK) && player.getOffhandItem().getCount() == 2,
			"the off-hand books changed: " + player.getOffhandItem());
		helper.assertTrue(player.getInventory().countItem(Items.WRITTEN_BOOK) == 0, "a list was written for no build");
		helper.succeed();
	}
}
