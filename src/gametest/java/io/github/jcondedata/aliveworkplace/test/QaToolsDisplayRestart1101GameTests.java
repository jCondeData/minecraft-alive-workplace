package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.TradeDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-e-1101-1913), B766 from its spec: "a worker's own tool stays" by the trade display. The fix's tests keep the
 * owner beside a worker for one evening; here the server restarts in the middle of that evening (the villager saved and
 * loaded with the owner still beside him), so the display starts over on a villager whose hand already holds his tool.
 * The tool must still be his (hand, bag or his chest) for the rest of the evening, and must never be taken for a
 * display's ware (marked) by the restart.
 */
public class QaToolsDisplayRestart1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String NAME = "QA Restart Tool 1101";
	private static final BlockPos WORK = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos AT = new BlockPos(3, 2, 3);

	/** A Farmer with a field, holding the village's named hoe; the owner stands by; a restart 100 ticks in. */
	//$ gametest_ticks_batch AREA '1400' '"qaToolRestartFarmer1101"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaToolRestartFarmer1101")
	public void qaAFarmersHoeOutlastsARestartWithTheOwnerBeside(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		helper.setBlock(WORK, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(new BlockPos(5, 1, 5)), helper.absolutePos(new BlockPos(7, 1, 7))));
		villager.setItemSlot(EquipmentSlot.MAINHAND, named(Items.IRON_HOE));
		eveningWithARestart(helper, villager, "Farmer's hoe");
	}

	/** A Shepherd holding the village's named shears; the owner stands by; a restart 100 ticks in. */
	//$ gametest_ticks_batch AREA '1400' '"qaToolRestartShepherd1101"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaToolRestartShepherd1101")
	public void qaAShepherdsShearsOutlastARestartWithTheOwnerBeside(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		helper.setBlock(WORK, Blocks.LOOM);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, named(Items.SHEARS));
		eveningWithARestart(helper, villager, "Shepherd's shears");
	}

	/**
	 * Off work (9500) with the owner 1.5 blocks off, empty-handed; at tick 100 the villager is saved and loaded; 1000 ticks
	 * in all. Fails the first tick the tool is nowhere, or the tool is ever marked as the display's ware.
	 */
	private static void eveningWithARestart(GameTestHelper helper, Villager first, String what) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		Villager[] villager = {first};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] == 100) {
				villager[0] = reload(level, villager[0]);
			}
			Villager v = villager[0];
			owner.teleportTo(v.getX() + 1.5, v.getY(), v.getZ());
			String where = whereIs(helper, v);
			if (where == null) {
				helper.fail("the " + what + " was gone " + tick[0] + " ticks into the evening (restart at 100) with the owner beside; the hand holds " + v.getMainHandItem());
			}
			if ("hand".equals(where) && TradeDisplay.isWare(v.getMainHandItem())) {
				helper.fail("the " + what + " in the hand was marked as a display ware " + tick[0] + " ticks in (restart at 100)");
			}
			if (tick[0] >= 1000) {
				helper.succeed();
			}
		});
	}

	private static ItemStack named(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
		return stack;
	}

	private static boolean isIt(ItemStack stack) {
		return !stack.isEmpty() && NAME.equals(stack.getHoverName().getString());
	}

	/** Where the named tool is: "hand", "bag", "chest", …, or null when it's gone. */
	private static String whereIs(GameTestHelper helper, Villager villager) {
		if (isIt(villager.getItemBySlot(EquipmentSlot.MAINHAND))) {
			return "hand";
		}
		if (isIt(villager.getItemBySlot(EquipmentSlot.OFFHAND))) {
			return "off hand";
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrElse(villager, null);
		if (bag != null && bag.stacks().stream().anyMatch(QaToolsDisplayRestart1101GameTests::isIt)) {
			return "bag";
		}
		for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
			if (isIt(villager.getInventory().getItem(i))) {
				return "inventory";
			}
		}
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (isIt(chest.getItem(i))) {
				return "chest";
			}
		}
		for (net.minecraft.world.entity.item.ItemEntity drop : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			villager.getBoundingBox().inflate(16))) {
			if (isIt(drop.getItem())) {
				return "on the ground";
			}
		}
		return null;
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
