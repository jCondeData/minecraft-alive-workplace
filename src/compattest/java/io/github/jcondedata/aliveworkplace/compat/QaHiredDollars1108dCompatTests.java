package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA, lane D (2026-11-08): the hall's hired swords in the Cobbleverse pack, where CobbleDollars keeps the money
 * ({@code work/Money}: "Amounts are given in both, and only the one in use counts"). The Mercenaries cost 12 emeralds,
 * so 1,200 CobbleDollars; the Siege-breakers 30, so 3,000. With CobbleDollars installed: a player a dollar short is
 * refused with the price in CobbleDollars however many emeralds they carry, and nothing is taken; with the exact
 * sum the band comes, the balance goes to 0 and every emerald stays in the bag. No other test hires a band with
 * CobbleDollars.
 */
public class QaHiredDollars1108dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	private static BlockPos village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 12;
		Expansions.openForTests = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		PartnerShowsCompatTests.after(helper, () -> {
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), Mercenaries::isMercenary).forEach(Villager::discard);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		level.setDayTime(18000);
		return helper.absolutePos(HALL);
	}

	/** A survival player by the hall with {@code dollars} CobbleDollars exactly and a stack of emeralds. */
	private static ServerPlayer player(GameTestHelper helper, BlockPos at, long dollars) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		PartnerShowsCompatTests.after(helper, () -> {
			player.getInventory().clearContent();
			Escorts.scan(helper.getLevel().getServer());
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		long had = CobbleDollarsBank.balance(player);
		if (had > 0) {
			CobbleDollarsBank.take(player, had);
		}
		CobbleDollarsBank.add(player, dollars);
		player.getInventory().add(new ItemStack(Items.EMERALD, 64));
		return player;
	}

	private static String key(Component said) {
		return said.getContents() instanceof TranslatableContents t ? t.getKey() : said.getString();
	}

	private interface Hire {
		Component hire(ServerLevel level, BlockPos hall, ServerPlayer player);
	}

	/**
	 * One band's price in CobbleDollars from both sides: {@code emeralds * 100 - 1} refused (the message names the
	 * price in CobbleDollars, nothing taken, nobody comes), then one more dollar hires {@code size} at once, the
	 * balance 0 and the 64 emeralds untouched.
	 */
	private static void priceFromBothSides(GameTestHelper helper, int emeralds, Hire hire, String refusedKey, String hiredKey,
		Supplier<List<Villager>> band, int size, String what) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		long price = (long) emeralds * Money.DOLLARS_PER_EMERALD;
		ServerPlayer player = player(helper, hall.above().south(3), price - 1);
		Component refused = hire.hire(level, hall, player);
		helper.assertTrue(key(refused).equals(refusedKey), what + " hired with " + (price - 1) + " CobbleDollars and 64 emeralds: " + refused.getString());
		String text = refused.getString();
		String sum = String.format(Locale.ROOT, "%,d", price);
		helper.assertTrue(text.contains(sum + " CobbleDollars") && !text.toLowerCase(Locale.ROOT).contains("emerald"),
			"the refusal should name " + sum + " CobbleDollars: '" + text + "'");
		helper.assertTrue(CobbleDollarsBank.balance(player) == price - 1 && player.getInventory().countItem(Items.EMERALD) == 64,
			"a refused hire took something: " + CobbleDollarsBank.balance(player) + " CobbleDollars, " + player.getInventory().countItem(Items.EMERALD) + " emeralds left");
		helper.assertTrue(band.get().isEmpty(), "a refused hire brought " + band.get().size() + " anyway");
		CobbleDollarsBank.add(player, 1);
		Component hired = hire.hire(level, hall, player);
		helper.assertTrue(key(hired).equals(hiredKey), what + " not hired with exactly " + price + " CobbleDollars: " + hired.getString());
		helper.assertTrue(CobbleDollarsBank.balance(player) == 0, "the hire left " + CobbleDollarsBank.balance(player) + " of " + price + " CobbleDollars");
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 64,
			"the hire took emeralds where CobbleDollars keeps the money: " + player.getInventory().countItem(Items.EMERALD) + " of 64 left");
		helper.assertTrue(band.get().size() == size, what + ": " + band.get().size() + " came, not " + size);
	}

	/** The Mercenaries: 1,199 CobbleDollars refused ("Mercenaries cost 1,200 CobbleDollars"), 1,200 hires three, emeralds kept. */
	//$ gametest_ticks_batch AREA '100' '"qah1108dMercs"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qah1108dMercs")
	public void mercenariesCostTwelveHundredCobbleDollarsFromBothSides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		priceFromBothSides(helper, Mercenaries.PRICE_EMERALDS, Mercenaries::hire, "message.aliveworkplace.mercenaries.cant_afford",
			"message.aliveworkplace.mercenaries.hired", () -> Mercenaries.near(level, hall), Mercenaries.BAND, "the Mercenaries");
		helper.succeed();
	}

	/** The Siege-breakers: 2,999 CobbleDollars refused ("The Siege-breakers cost 3,000 CobbleDollars"), 3,000 hires five, emeralds kept. */
	//$ gametest_ticks_batch AREA '100' '"qah1108dSiege"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qah1108dSiege")
	public void siegeBreakersCostThreeThousandCobbleDollarsFromBothSides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		priceFromBothSides(helper, WarParties.PRICE_EMERALDS, WarParties::hire, "message.aliveworkplace.siege_breakers.cant_afford",
			"message.aliveworkplace.siege_breakers.hired", () -> Mercenaries.near(level, hall).stream().filter(WarParties::isSiegeBreaker).toList(),
			WarParties.BAND, "the Siege-breakers");
		helper.succeed();
	}
}
