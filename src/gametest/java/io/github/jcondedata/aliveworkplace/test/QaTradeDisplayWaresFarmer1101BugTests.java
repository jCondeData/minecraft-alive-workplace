package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.TradeDisplay;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane E, 2026-11-01), the failing half of B776 (filed by lane C; here the Farmer and a restart): B766 from the other side: the trade display's wares are copies it puts in an empty hand to
 * show a player what a villager sells; they are never the village's goods. A ware still in hand when the worker picks up
 * a tool (the evening's display running on as the shift starts, or a villager saved and loaded mid-display, as at a
 * server restart with a player beside them) must never end up in the worker's bag, their chest or on the ground: that
 * would be goods from nothing. Each villager here sells one ware nobody else makes (bread, a painting), so any of it
 * found outside the hand came from the display.
 */
public class QaTradeDisplayWaresFarmer1101BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos WORK = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos AT = new BlockPos(3, 2, 3);

	private static void sells(Villager villager, ItemStack ware) {
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), ware, 16, 1, 0.05f));
		villager.setOffers(offers);
	}

	/** A mock player who stays 1.5 blocks off the villager while {@code near[0]} holds, holding {@code inHand}. */
	private static ServerPlayer playerBeside(GameTestHelper helper, Villager[] villager, boolean[] near, ItemStack inHand) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, inHand);
		helper.onEachTick(() -> {
			Villager v = villager[0];
			if (near[0]) {
				player.teleportTo(v.getX() + 1.5, v.getY(), v.getZ());
			} else {
				player.teleportTo(v.getX() + 60, v.getY() + 30, v.getZ());
			}
		});
		return player;
	}

	/** Where {@code ware} is found other than the villager's main hand (bag, inventory, off hand, chest, ground), or null. */
	private static String strayWare(GameTestHelper helper, Villager villager, Item ware) {
		if (villager.getItemBySlot(EquipmentSlot.OFFHAND).is(ware)) {
			return "off hand";
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrElse(villager, null);
		if (bag != null && bag.stacks().stream().anyMatch(s -> s.is(ware))) {
			return "bag " + bag.stacks();
		}
		for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
			if (villager.getInventory().getItem(i).is(ware)) {
				return "inventory";
			}
		}
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(ware)) {
				return "chest (" + chest.getItem(i) + ")";
			}
		}
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(24))) {
			if (drop.getItem().is(ware)) {
				return "on the ground (" + drop.getItem() + ")";
			}
		}
		return null;
	}

	/** Saves {@code villager}, takes it out and loads it back, as a server restart does. */
	private static Villager restart(GameTestHelper helper, Villager villager) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(villager.save(tag), "the villager wasn't saved");
		villager.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the villager didn't load back: " + loaded);
		level.addFreshEntity(loaded);
		return (Villager) loaded;
	}

	/**
	 * Runs the evening, then the shift: the player stands by with emeralds until the display shows {@code ware}; then
	 * {@code turn} (the shift starts, perhaps after a restart). From then on, for {@code ticks}, the ware must never be
	 * found outside the hand, and {@code done} must hold by the end (the worker really fetched their tool).
	 */
	private static void eveningThenShift(GameTestHelper helper, Villager[] villager, Item ware, boolean restartAtTheTurn,
		int ticks, java.util.function.BooleanSupplier done, String what, Item tool) {
		boolean[] near = {true};
		playerBeside(helper, villager, near, new ItemStack(Items.EMERALD, 8));
		int[] after = {-1};
		int[] waited = {0};
		helper.onEachTick(() -> {
			Villager v = villager[0];
			if (after[0] < 0) {
				ItemStack held = v.getMainHandItem();
				if (held.is(ware) && TradeDisplay.isWare(held)) {
					after[0] = 0;
					if (restartAtTheTurn) {
						near[0] = false; // the player is gone after the restart
						villager[0] = restart(helper, v);
						helper.assertTrue(TradeDisplay.isWare(villager[0].getMainHandItem()),
							"setup: the ware wasn't in hand after the restart: " + villager[0].getMainHandItem());
					}
					// The tool reaches the chest as the shift starts (before it, the worker would fetch it in the evening).
					((Container) helper.getBlockEntity(CHEST)).setItem(1, new ItemStack(tool));
					helper.setDayTime(2100); // the shift starts
				} else if (++waited[0] > 1200) {
					helper.fail("setup: the display never showed the " + what + "'s ware in 1200 ticks (hand: " + held + ")");
				}
				return;
			}
			after[0]++;
			String where = strayWare(helper, villager[0], ware);
			if (where != null) {
				helper.fail("a ware the display showed (" + ware + ") turned into a real one: in the " + what + "'s " + where
					+ " " + after[0] + " ticks into the shift");
			}
			if (after[0] >= ticks) {
				helper.assertTrue(done.getAsBoolean(), "setup: the " + what + " never fetched the tool in " + ticks + " ticks (hand: "
					+ villager[0].getMainHandItem() + ")");
				helper.succeed();
			}
		});
	}

	/** A Farmer with a field of bare dirt to till, seeds and a hoe in the chest, selling bread. */
	private static Villager[] farmer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		Leftovers.after(helper, () -> helper.setDayTime(2000));
		helper.setBlock(WORK, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
		for (int x = 5; x <= 7; x++) {
			for (int z = 5; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(new BlockPos(5, 1, 5)), helper.absolutePos(new BlockPos(7, 1, 7))));
		sells(villager, new ItemStack(Items.BREAD, 6));
		return new Villager[]{villager};
	}

	/** The meeting's display still shows bread as the shift starts and the Farmer goes for the hoe: no bread is made. */
	//$ gametest_ticks_batch AREA '3000' '"qaWaresFarmerShift1101"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWaresFarmerShift1101")
	public void qaAFarmersShownBreadNeverReachesHisBagOrChestAsTheShiftStarts(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] farmer = farmer(helper);
		eveningThenShift(helper, farmer, Items.BREAD, false, 1200, () -> farmer[0].getMainHandItem().is(Items.IRON_HOE), "Farmer", Items.IRON_HOE);
	}

	/** The same Farmer saved and loaded with the bread in hand (a restart with the player beside him): no bread is made. */
	//$ gametest_ticks_batch AREA '3000' '"qaWaresFarmerRestart1101"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWaresFarmerRestart1101")
	public void qaAFarmerRestartedWithShownBreadInHandMakesNoBread(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] farmer = farmer(helper);
		eveningThenShift(helper, farmer, Items.BREAD, true, 1200, () -> farmer[0].getMainHandItem().is(Items.IRON_HOE), "Farmer", Items.IRON_HOE);
	}

	/** A Shepherd whose evening display still shows a painting as the shift starts and goes for the shears: no painting is made. */
	//$ gametest_ticks_batch AREA '3000' '"qaWaresShepherdShift1101"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWaresShepherdShift1101")
	public void qaAShepherdsShownPaintingNeverReachesHerBagOrChestAsTheShiftStarts(GameTestHelper helper) {
		shepherd(helper, false);
	}

	/** A Shepherd saved and loaded with a shown painting in hand, shears in the chest and sheep to shear: no painting is made. */
	//$ gametest_ticks_batch AREA '3000' '"qaWaresShepherdRestart1101"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWaresShepherdRestart1101")
	public void qaAShepherdRestartedWithAShownPaintingInHandMakesNoPainting(GameTestHelper helper) {
		shepherd(helper, true);
	}

	/** A Shepherd with sheep to shear (the shears come to the chest as the shift starts), selling a painting. */
	private static void shepherd(GameTestHelper helper, boolean restart) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		Leftovers.after(helper, () -> helper.setDayTime(2000));
		helper.setBlock(WORK, Blocks.LOOM);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		sells(villager, new ItemStack(Items.PAINTING));
		Sheep a = helper.spawn(EntityType.SHEEP, new BlockPos(9, 2, 9));
		Sheep b = helper.spawn(EntityType.SHEEP, new BlockPos(11, 2, 8));
		a.setNoAi(true);
		b.setNoAi(true);
		Villager[] shepherd = {villager};
		eveningThenShift(helper, shepherd, Items.PAINTING, restart, 1200, () -> a.isSheared() || b.isSheared()
			|| shepherd[0].getMainHandItem().is(Items.SHEARS), "Shepherd", Items.SHEARS);
	}
}
