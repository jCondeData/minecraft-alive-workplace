package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.TradeDisplay;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * QA (lane E, 2026-11-01), ROADMAP 39.2 with the owner's Create port, B776's kin for the Mechanic: he sells Create's
 * parts, so the evening's trade display shows one in his main hand, the hand whose Create item "goes back to the store"
 * when his work stops. A shown part is a copy, not the store's: it must never end up in the store or on the ground. The
 * ware here is Andesite Alloy, which his line (shafts and a cogwheel) never uses, so any alloy found is the ware.
 */
public class QaMechanicWares1101CompatTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static int strayAlloy(GameTestHelper helper, Villager mechanic, Container chest, Item alloy) {
		int n = MechanicCompatTests.count(chest, alloy);
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, mechanic.getBoundingBox().inflate(24))) {
			if (drop.getItem().is(alloy)) {
				n += drop.getItem().getCount();
			}
		}
		var bag = ModAttachments.BUILDER_BAG.getOrElse(mechanic, null);
		if (bag != null) {
			n += bag.stacks().stream().filter(s -> s.is(alloy)).mapToInt(ItemStack::getCount).sum();
		}
		return n;
	}

	private static Villager restart(GameTestHelper helper, Villager villager) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(villager.save(tag), "the Mechanic wasn't saved");
		villager.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the Mechanic didn't load back: " + loaded);
		level.addFreshEntity(loaded);
		return (Villager) loaded;
	}

	/**
	 * The evening (after 11000) with the owner beside him holding emeralds, until the display shows the alloy; then (perhaps
	 * after a restart) a whole shift and its end: no alloy in the store, his bag or on the ground at any tick.
	 */
	private static void eveningShiftEvening(GameTestHelper helper, boolean restartAtTheTurn, boolean breakShaft) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item alloy = MechanicCompatTests.createItem("andesite_alloy");
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		Container chest = MechanicCompatTests.chest(helper, new ItemStack(MechanicCompatTests.createItem("shaft"), 1));
		Villager[] mechanic = {MechanicCompatTests.mechanic(helper)};
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(alloy, 4), 16, 1, 0.05f));
		mechanic[0].setOffers(offers);
		helper.setDayTime(11500); // the evening: workers work until 11000
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 8));
		boolean[] near = {true};
		helper.onEachTick(() -> {
			Villager v = mechanic[0];
			if (near[0]) {
				owner.teleportTo(v.getX() + 1.5, v.getY(), v.getZ());
			} else {
				owner.teleportTo(v.getX() + 60, v.getY() + 30, v.getZ());
			}
		});
		int[] after = {-1};
		int[] waited = {0};
		helper.onEachTick(() -> {
			Villager v = mechanic[0];
			if (after[0] < 0) {
				ItemStack held = v.getMainHandItem();
				if (held.is(alloy) && TradeDisplay.isWare(held)) {
					after[0] = 0;
					if (restartAtTheTurn) {
						near[0] = false;
						mechanic[0] = restart(helper, v);
					}
					helper.setDayTime(2100); // the shift starts
					if (breakShaft) {
						helper.setBlock(line.get(1), net.minecraft.world.level.block.Blocks.AIR); // and a shaft is broken
					}
				} else if (++waited[0] > 1200) {
					helper.fail("setup: the display never showed the alloy in 1200 ticks (hand: " + held + ")");
				}
				return;
			}
			after[0]++;
			if (after[0] == 600) {
				helper.setDayTime(11100); // the shift ends: his work stops
			}
			int stray = strayAlloy(helper, mechanic[0], chest, alloy);
			if (stray > 0) {
				helper.fail("the alloy the display showed turned into " + stray + " real alloy in the store, his bag or on the ground "
					+ after[0] + " ticks after the evening (hand: " + mechanic[0].getMainHandItem() + ")");
			}
			if (after[0] >= 900) {
				helper.assertTrue(!breakShaft || helper.getBlockState(line.get(1)).is(MechanicCompatTests.create("shaft")),
					"setup: the Mechanic never mended the shaft in the shift (hand: " + mechanic[0].getMainHandItem() + ")");
				helper.succeed();
			}
		});
	}

	/** The evening's display runs on into the shift: the shown alloy never reaches the store. */
	//$ gametest_ticks_batch AREA '3000' '"qa_mechanic_wares_1101"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_mechanic_wares_1101")
	public void qaAMechanicsShownAlloyNeverReachesTheStore(GameTestHelper helper) {
		eveningShiftEvening(helper, false, true);
	}

	/** Saved and loaded with the shown alloy in hand (a restart with the owner beside him): no alloy reaches the store. */
	//$ gametest_ticks_batch AREA '3000' '"qa_mechanic_wares_restart_1101"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_mechanic_wares_restart_1101")
	public void qaAMechanicRestartedWithShownAlloyMakesNoAlloy(GameTestHelper helper) {
		eveningShiftEvening(helper, true, true);
	}

	/** Restarted with the shown alloy in hand and nothing to mend all shift: when his work stops, no alloy reaches the store. */
	//$ gametest_ticks_batch AREA '3000' '"qa_mechanic_wares_idle_1101"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_mechanic_wares_idle_1101")
	public void qaAMechanicRestartedWithShownAlloyAndNothingToMendMakesNoAlloy(GameTestHelper helper) {
		eveningShiftEvening(helper, true, false);
	}
}
