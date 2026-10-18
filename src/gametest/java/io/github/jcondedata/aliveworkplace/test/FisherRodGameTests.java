package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.fish.FisherWork;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/** A hired fisherman's rod lasts as a player's: one point a catch, his own enchantments, never thrown away. */
public class FisherRodGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BARREL = new BlockPos(2, 2, 2);
	private static final String NAME = "Old Faithful";
	/** The barrel's spare has its own name: a catch can be a junk fishing rod, which goes into the barrel too (B159). */
	private static final String SPARE = "Spare";

	private static Villager fisherman(GameTestHelper helper, long dayTime) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime((int) dayTime);
		Leftovers.after(helper, () -> helper.setDayTime(2000));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BARREL, Blocks.BARREL);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(11, 1, 11))) {
			helper.setBlock(p, Blocks.WATER);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		return villager;
	}

	/** A rod with a name, to know it again (the bag keeps copies). */
	private static ItemStack rod(ServerLevel level, @Nullable ResourceKey<Enchantment> enchantment, int strength) {
		ItemStack rod = new ItemStack(Items.FISHING_ROD);
		rod.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
		if (enchantment != null) {
			Lookup.holder(Lookup.registry(level.registryAccess(), Registries.ENCHANTMENT), enchantment).ifPresent(h -> rod.enchant(h, strength));
		}
		return rod;
	}

	private static boolean isHisRod(ItemStack stack) {
		return stack.is(Items.FISHING_ROD) && stack.has(DataComponents.CUSTOM_NAME)
			&& NAME.equals(stack.get(DataComponents.CUSTOM_NAME).getString());
	}

	/** The named spare rod for the barrel. */
	private static ItemStack spare() {
		ItemStack rod = new ItemStack(Items.FISHING_ROD);
		rod.set(DataComponents.CUSTOM_NAME, Component.literal(SPARE));
		return rod;
	}

	/** How many named spares are in {@code barrel} (junk rods from the catch don't count). */
	private static int spares(Container barrel) {
		int n = 0;
		for (int i = 0; i < barrel.getContainerSize(); i++) {
			ItemStack stack = barrel.getItem(i);
			if (stack.is(Items.FISHING_ROD) && stack.has(DataComponents.CUSTOM_NAME)
				&& SPARE.equals(stack.get(DataComponents.CUSTOM_NAME).getString())) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** His named rod, in his hand or put away in his bag; EMPTY when it's gone. */
	private static ItemStack hisRod(Villager villager) {
		ItemStack hand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (isHisRod(hand)) {
			return hand;
		}
		return ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks().stream().filter(FisherRodGameTests::isHisRod).findFirst().orElse(ItemStack.EMPTY);
	}

	/** Fails the test the first tick the named rod is nowhere on him. */
	private static void watchRod(GameTestHelper helper, Villager villager) {
		watchRod(helper, villager, new String[] {null});
	}

	/** As above; {@code held[0]} is the success check that last failed, named in the message. */
	private static void watchRod(GameTestHelper helper, Villager villager, String[] held) {
		boolean[] failed = {false};
		helper.onEachTick(() -> {
			if (!failed[0] && hisRod(villager).isEmpty()) {
				failed[0] = true;
				helper.fail("after " + ModAttachments.FISH_CAUGHT.getOrElse(villager, 0) + " catches his rod is gone; the hand holds "
					+ villager.getItemBySlot(EquipmentSlot.MAINHAND) + " at day time " + helper.getLevel().getDayTime() % 24000
					+ (held[0] == null ? "" : "; the success check held by: " + held[0]));
			}
		});
	}

	/**
	 * The owner's report ("fishes once, then needs a new rod"): the owner stands by his fisherman as the shift ends. Off
	 * work, vanilla's trade display emptied the villager's hand and the rod was gone. Now it's put away for the evening,
	 * and next morning he fishes on with the same rod, worn one point a catch, and leaves the spare in the barrel.
	 */
	//$ gametest_ticks_batch AREA '6000' '"fisherKeepsHisRod"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "fisherKeepsHisRod")
	public void fisherKeepsHisRodPastTheShift(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager villager = fisherman(helper, 10500);
		Container barrel = helper.getBlockEntity(BARREL);
		barrel.setItem(0, spare());
		Fishers.start(level, villager, rod(level, null, 0));
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		String[] held = {null};
		watchRod(helper, villager, held);
		int[] tick = {0};
		int[] caughtByEvening = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			owner.teleportTo(villager.getX() + 1.5, villager.getY(), villager.getZ());
			if (tick[0] == 900) {
				caughtByEvening[0] = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0);
				helper.setDayTime(2000); // the next morning
			}
		});
		helper.succeedWhen(() -> {
			try {
				check(helper, level, villager, barrel, caughtByEvening[0]);
			} catch (RuntimeException e) {
				held[0] = e.getMessage();
				throw e;
			}
		});
	}

	private static void check(GameTestHelper helper, ServerLevel level, Villager villager, Container barrel, int caughtByEvening) {
		helper.assertTrue(caughtByEvening >= 0, "the evening isn't over");
		helper.assertTrue(level.getDayTime() % 24000 < 11000, "still evening");
		int caught = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0);
		helper.assertTrue(caught >= caughtByEvening + 3, "only " + (caught - caughtByEvening) + " caught next morning");
		ItemStack hand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		helper.assertTrue(isHisRod(hand), "his rod isn't in his hand: " + hand);
		helper.assertTrue(hand.getDamageValue() == caught, "rod damage " + hand.getDamageValue() + " after " + caught + " catches");
		helper.assertTrue(spares(barrel) == 1, "he took the spare rod");
	}

	/** Eight catches in a row (past a trip to the barrel) on one rod: one point of wear a catch, the spare left alone. */
	//$ gametest_ticks AREA '3000'
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void fisherWearsHisRodOneACatch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = fisherman(helper, 2000);
		Container barrel = helper.getBlockEntity(BARREL);
		barrel.setItem(0, spare());
		Fishers.start(level, villager, rod(level, null, 0));
		watchRod(helper, villager);
		helper.succeedWhen(() -> {
			int caught = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0);
			helper.assertTrue(caught >= 8, "only " + caught + " caught");
			ItemStack rod = hisRod(villager);
			helper.assertTrue(rod.getDamageValue() == caught, "rod damage " + rod.getDamageValue() + " after " + caught + " catches");
			helper.assertTrue(spares(barrel) == 1, "he took the spare rod");
		});
	}

	/**
	 * Unbreaking III: each catch wears the rod one time in four, as for a player. Twenty catches wear it well under twenty
	 * (15 or more would happen about once in 300,000 runs).
	 */
	//$ gametest_ticks AREA '6000'
	@GameTest(template = AREA, timeoutTicks = 6000)
	public void unbreakingRodWearsSlower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = fisherman(helper, 2000);
		Fishers.start(level, villager, rod(level, Enchantments.UNBREAKING, 3));
		watchRod(helper, villager);
		helper.succeedWhen(() -> {
			int caught = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0);
			helper.assertTrue(caught >= 20, "only " + caught + " caught");
			int wear = hisRod(villager).getDamageValue();
			helper.assertTrue(wear < 15, "an Unbreaking III rod wore " + wear + " in " + caught + " catches");
		});
	}

	/** Mending: each catch's 1 to 6 experience goes into the rod (two points a point), so a worn rod comes back. */
	//$ gametest_ticks AREA '3000'
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void mendingRodMendsWithTheCatch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = fisherman(helper, 2000);
		ItemStack rod = rod(level, Enchantments.MENDING, 1);
		rod.setDamageValue(30);
		Fishers.start(level, villager, rod);
		watchRod(helper, villager);
		helper.succeedWhen(() -> {
			int caught = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0);
			helper.assertTrue(caught >= 5, "only " + caught + " caught");
			int damage = hisRod(villager).getDamageValue();
			helper.assertTrue(damage <= 30 - caught, "a Mending rod at 30 damage is at " + damage + " after " + caught + " catches");
		});
	}

	/** Mending as vanilla does it: 2 durability a point of experience; what the rod doesn't need is left over. */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void mendingTakesWhatItNeeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack rod = rod(level, Enchantments.MENDING, 1);
		rod.setDamageValue(5);
		int left = FisherWork.mendWithXp(level, rod, 6);
		helper.assertTrue(rod.getDamageValue() == 0, "damage " + rod.getDamageValue() + " after 6 experience");
		helper.assertTrue(left == 4, left + " experience left over (vanilla's sum: 6 - 5 * 6 / 12 = 4)");
		ItemStack plain = rod(level, null, 0);
		plain.setDamageValue(5);
		helper.assertTrue(FisherWork.mendWithXp(level, plain, 6) == 6 && plain.getDamageValue() == 5, "a rod without Mending was mended");
		helper.succeed();
	}

	/** Luck of the Sea III adds 3 to the luck the catch is rolled with, as on a player's hook. */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void luckOfTheSeaRaisesTheLuck(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		float plain = FisherWork.catchLuck(level, villager, rod(level, null, 0));
		float lucky = FisherWork.catchLuck(level, villager, rod(level, Enchantments.LUCK_OF_THE_SEA, 3));
		helper.assertTrue(lucky == plain + 3, "Luck of the Sea III: luck " + lucky + ", without it " + plain);
		helper.succeed();
	}

	/** Lure III takes 15 seconds off a player's wait (30 steps of the fisher's, a tenth as long); never under the shortest. */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void lureShortensTheWait(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		ItemStack plainRod = rod(level, null, 0);
		ItemStack lureRod = rod(level, Enchantments.LURE, 3);
		int plainTotal = 0;
		int lureTotal = 0;
		for (long seed = 1; seed <= 40; seed++) {
			int plain = FisherWork.biteWait(level, villager, plainRod, RandomSource.create(seed));
			int lure = FisherWork.biteWait(level, villager, lureRod, RandomSource.create(seed));
			helper.assertTrue(plain >= 15 && plain <= 60, "a wait of " + plain + " without Lure");
			helper.assertTrue(lure == Math.max(FisherWork.MIN_BITE, plain - 30), "Lure III: " + lure + " where without it " + plain);
			plainTotal += plain;
			lureTotal += lure;
		}
		helper.assertTrue(lureTotal < plainTotal / 2, "Lure III waits " + lureTotal + " in all, without it " + plainTotal);
		helper.succeed();
	}
}
