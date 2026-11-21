package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * QA (lane-d-1121-1309) for B1379, from its spec: with CobbleDollars every money sum (the Village Hall's treasury, a
 * quest's reward, a retraining price, the family tree's price, the cup's purse) reads "1 CobbleDollar" for a sum of 1
 * and "N CobbleDollars" from 2 up (and for 0), at any rate; without CobbleDollars the sums stay in emeralds, "1 emerald",
 * "2 emeralds". Each sum is worked out the way its line works it out (an emerald price times the rate), and the
 * treasury and quest reward are read off the hall's own buttons.
 */
public class QaMoneyWording1121dCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** What the spec says a sum of {@code dollars} reads. */
	static String dollars(long dollars) {
		return String.format(java.util.Locale.ROOT, "%,d", dollars) + (dollars == 1 ? " CobbleDollar" : " CobbleDollars");
	}

	static String emeralds(long emeralds) {
		return emeralds + (emeralds == 1 ? " emerald" : " emeralds");
	}

	private static <T> T atRate(int rate, java.util.function.Supplier<T> what) {
		int before = Money.DOLLARS_PER_EMERALD;
		try {
			Money.DOLLARS_PER_EMERALD = rate;
			return what.get();
		} finally {
			Money.DOLLARS_PER_EMERALD = before;
		}
	}

	/**
	 * Every listed line's sum at the rates 1, 21 and 100 (the default), for an emerald price of 0, 1 and 2, the family
	 * tree's own price, and the cup purses (a sum in CobbleDollars, its emeralds the sum over the rate).
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void everyMoneyLineCountsItsSumAtEveryRate(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		List<String> wrong = new ArrayList<>();
		for (int rate : new int[] {1, 21, 100}) {
			atRate(rate, () -> {
				// treasury, quest reward, retraining price: Money.describe(emeralds * rate, emeralds)
				for (int e : new int[] {0, 1, 2, 3}) {
					long d = (long) e * Money.DOLLARS_PER_EMERALD;
					String got = Money.describe(d, e).getString();
					if (!got.equals(dollars(d))) {
						wrong.add("rate " + rate + ", " + e + " emeralds: '" + got + "', expected '" + dollars(d) + "'");
					}
				}
				// the family tree's price
				long tree = (long) FamilyBook.TREE_EMERALDS * Money.DOLLARS_PER_EMERALD;
				String got = Money.describe(tree, FamilyBook.TREE_EMERALDS).getString();
				if (!got.equals(dollars(tree))) {
					wrong.add("rate " + rate + ", family tree: '" + got + "', expected '" + dollars(tree) + "'");
				}
				// the cup's purse (CupMatches works its emeralds out from the dollars)
				for (long purse : new long[] {0, 1, 2, CupMatches.PURSE, CupMatches.PURSE + CupMatches.FINAL_PURSE}) {
					int e = (int) (purse / Math.max(1, Money.DOLLARS_PER_EMERALD));
					String p = Money.describe(purse, e).getString();
					if (!p.equals(dollars(purse))) {
						wrong.add("rate " + rate + ", purse " + purse + ": '" + p + "', expected '" + dollars(purse) + "'");
					}
				}
				return null;
			});
		}
		helper.assertTrue(wrong.isEmpty(), "money sums worded wrong: " + wrong);
		helper.succeed();
	}

	/** Without CobbleDollars (its Bank gone) the same sums read in emeralds: "0 emeralds", "1 emerald", "2 emeralds". */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"qaMoneyWording1121dNoBank"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "qaMoneyWording1121dNoBank")
	public void withoutCobbleDollarsTheSumsStayInEmeralds(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		List<String> wrong = new ArrayList<>();
		withoutBank(() -> {
			helper.assertFalse(Money.cobbleDollars(), "setup: the Bank is still there");
			for (int rate : new int[] {1, 21, 100}) {
				atRate(rate, () -> {
					for (int e : new int[] {0, 1, 2, FamilyBook.TREE_EMERALDS, 64}) {
						String got = Money.describe((long) e * Money.DOLLARS_PER_EMERALD, e).getString();
						if (!got.equals(emeralds(e))) {
							wrong.add("rate " + rate + ", " + e + " emeralds: '" + got + "'");
						}
					}
					return null;
				});
			}
		});
		helper.assertTrue(Money.cobbleDollars(), "the test didn't put the Bank back");
		helper.assertTrue(wrong.isEmpty(), "emerald sums worded wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * The hall's own lines at a rate of 1: a treasury of 0, 1 and 2 emeralds reads "Treasury: 0 CobbleDollars",
	 * "... 1 CobbleDollar", "... 2 CobbleDollars"; a quest paying 1 emerald "Reward: 1 CobbleDollar" and one paying 2
	 * "Reward: 2 CobbleDollars". At a rate of 21 the treasury of 1 reads "21 CobbleDollars".
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaMoneyWording1121dHall"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaMoneyWording1121dHall")
	public void theHallsTreasuryAndQuestRewardReadInTheirNumber(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		int rate = Money.DOLLARS_PER_EMERALD;
		VillageHalls.RADIUS = 16;
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Money.DOLLARS_PER_EMERALD = rate;
			level.getServer().getPlayerList().remove(player);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		long now = level.getGameTime();
		entity.setQuests(List.of(
			new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.BRING, "minecraft:bread", 3, 0, 1, now, "Ana", Optional.empty()),
			new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.BRING, "minecraft:apple", 3, 0, 2, now, "Bo", Optional.empty())));
		Money.DOLLARS_PER_EMERALD = 1;
		for (int e : new int[] {0, 1, 2}) {
			entity.setTreasury(e * 100);
			List<String> lines = pageLines(level, player, VillageHallScreen.forTest(player, hall));
			String want = "Treasury: " + dollars(e) + " (click to collect)";
			helper.assertTrue(lines.contains(want), "rate 1, treasury of " + e + " emeralds: no line '" + want + "' in " + money(lines));
		}
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.QUESTS, player);
		List<String> quests = pageLines(level, player, menu);
		helper.assertTrue(quests.contains("Reward: 1 CobbleDollar"), "rate 1, a quest paying 1 emerald: " + money(quests));
		helper.assertTrue(quests.contains("Reward: 2 CobbleDollars"), "rate 1, a quest paying 2 emeralds: " + money(quests));
		Money.DOLLARS_PER_EMERALD = 21;
		entity.setTreasury(100);
		List<String> lines = pageLines(level, player, VillageHallScreen.forTest(player, hall));
		helper.assertTrue(lines.contains("Treasury: 21 CobbleDollars (click to collect)"), "rate 21, treasury of 1 emerald: " + money(lines));
		helper.succeed();
	}

	/** Every tooltip line of every button on the menu's page, as the player reads it. */
	private static List<String> pageLines(ServerLevel level, ServerPlayer player, ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (!icon.isEmpty()) {
				for (Component line : icon.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL)) {
					out.add(line.getString());
				}
			}
		}
		return out;
	}

	private static List<String> money(List<String> lines) {
		return lines.stream().filter(l -> l.contains("CobbleDollar") || l.contains("emerald") || l.contains("Treasury") || l.contains("Reward")).toList();
	}

	/** Runs {@code body} with CobbleDollars' Bank taken out (as if the mod weren't installed), then puts it back. */
	private static void withoutBank(Runnable body) {
		try {
			java.lang.reflect.Field filling = io.github.jcondedata.aliveworkplace.work.Extension.class.getDeclaredField("filling");
			filling.setAccessible(true);
			Object bank = filling.get(Bank.EXTENSION);
			filling.set(Bank.EXTENSION, null);
			try {
				body.run();
			} finally {
				filling.set(Bank.EXTENSION, bank);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
