package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B1346 and B1347: with CobbleDollars, the sentences that name the server's CobbleDollar rate say the real one
 * (dollarsPerEmerald, 1 to 10,000), thousands marked, and read right at a rate of 1. A Shop Counter placed by a player
 * names the rate in its welcome line (B1346: it always said 100); the realm treasury's hint says "1 CobbleDollar" in
 * the singular (B1347).
 */
public class DollarRateTextCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static String placed(String rate) {
		return "Your Shop Counter is up. Open it to set prices: the top row is what one sale hands over, the slot below is what it costs."
			+ " Stock chests within 8 blocks with the goods; a villager takes the counter and sells from them, and the takings go into those"
			+ " chests. With CobbleDollars installed, emerald prices are paid in CobbleDollars (" + rate + " per emerald) straight to you."
			+ " Sneak-right-click the counter to see your sales.";
	}

	/** A player places a Shop Counter from their hand at each rate and reads the rate it names. */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void aPlacedShopCounterNamesTheServersRate(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaDaycareWaiting1115d3CompatTests.login(helper, seen, UUID.randomUUID(), "qaDollarRate");
		int before = Money.DOLLARS_PER_EMERALD;
		try {
			int[] rates = {1, 100, 1_000};
			String[] shown = {"1", "100", "1,000"};
			for (int i = 0; i < rates.length; i++) {
				Money.DOLLARS_PER_EMERALD = rates[i];
				seen.clear();
				BlockPos floor = helper.absolutePos(new BlockPos(1 + 2 * i, 1, 1));
				BlockPos at = floor.above();
				ItemStack stack = new ItemStack(ModBlocks.SHOP_COUNTER.asItem());
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
				stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
				helper.assertTrue(helper.getLevel().getBlockState(at).is(ModBlocks.SHOP_COUNTER), "the counter wasn't placed at rate " + rates[i]);
				helper.assertValueEqual(seen, List.of(placed(shown[i])), "the welcome line at rate " + rates[i]);
			}
		} finally {
			Money.DOLLARS_PER_EMERALD = before;
		}
		helper.succeed();
	}

	/** The treasury's hint at 1 (singular), 100 and 1,000. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theTreasuryHintReadsRightAtEveryRate(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		helper.assertValueEqual(QaTreasuryHint1116cCompatTests.hint(1), "Click to give 1 CobbleDollar (shift-click: 64), or click with emeralds", "at 1");
		helper.assertValueEqual(QaTreasuryHint1116cCompatTests.hint(100), "Click to give 100 CobbleDollars (shift-click: 6,400), or click with emeralds", "at 100");
		helper.assertValueEqual(QaTreasuryHint1116cCompatTests.hint(1_000), "Click to give 1,000 CobbleDollars (shift-click: 64,000), or click with emeralds", "at 1,000");
		helper.succeed();
	}
}
