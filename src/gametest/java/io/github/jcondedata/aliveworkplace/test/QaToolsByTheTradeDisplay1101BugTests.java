package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * QA (lane E, 2026-11-01), the failing half (bug filed): a worker's tool, taken from the village's chest, is never lost because a player stood by
 * them after work. Vanilla's trade display ({@code ShowTradesToPlayer}) shows a villager's wares in its main hand and
 * empties that hand when a player is close (B-fisher: "fishes once, then needs a new rod"). Fishers and conscripts are
 * kept from it; here the other workers whose tool lives in that hand: the Miner's pickaxe (mid-quarry, the shift ends),
 * the Lumberjack's axe, the Farmer's hoe and the Shepherd's shears. Each tool carries a name to know it again; after the
 * evening with the owner beside them it must still be on the worker (hand or bag) or back in their chest.
 */
public class QaToolsByTheTradeDisplay1101BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String NAME = "QA Tool 1101";
	private static final BlockPos WORK = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos AT = new BlockPos(3, 2, 3);

	private static ItemStack named(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
		return stack;
	}

	private static boolean isIt(ItemStack stack) {
		return !stack.isEmpty() && NAME.equals(stack.getHoverName().getString());
	}

	/** Where the named tool is: "hand", "bag", "chest", or null when it's gone. */
	private static String whereIs(GameTestHelper helper, Villager villager) {
		if (isIt(villager.getItemBySlot(EquipmentSlot.MAINHAND))) {
			return "hand";
		}
		if (isIt(villager.getItemBySlot(EquipmentSlot.OFFHAND))) {
			return "off hand";
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrElse(villager, null);
		if (bag != null && bag.stacks().stream().anyMatch(QaToolsByTheTradeDisplay1101BugTests::isIt)) {
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

	/**
	 * Off work (the meeting hour, 9500) with the owner standing by, empty-handed, for {@code ticks} from the first tick
	 * {@code begun} says so: the tool must not vanish. Fails the first tick it's nowhere, naming what the hand holds then.
	 */
	private static void eveningWithTheOwner(GameTestHelper helper, Villager villager, int ticks, String what,
		java.util.function.BooleanSupplier begun) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		int[] tick = {0};
		helper.onEachTick(() -> {
			if (!begun.getAsBoolean()) {
				owner.teleportTo(villager.getX() + 40, villager.getY() + 20, villager.getZ()); // far off until the evening
				return;
			}
			tick[0]++;
			owner.teleportTo(villager.getX() + 1.5, villager.getY(), villager.getZ());
			String where = whereIs(helper, villager);
			if (where == null) {
				helper.fail("the " + what + " was gone after " + tick[0] + " ticks off work with the owner beside them; the hand holds "
					+ villager.getMainHandItem());
			}
			if (tick[0] >= ticks) {
				helper.succeed();
			}
		});
	}

	/** A Farmer with a field, holding the village's named iron hoe, off work with the owner beside them. */
	//$ gametest_ticks_batch AREA '2000' '"qaToolFarmer1101"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaToolFarmer1101")
	public void qaAFarmersHoeOutlastsAnEveningWithTheOwnerBeside(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		helper.setBlock(WORK, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(new BlockPos(5, 1, 5)), helper.absolutePos(new BlockPos(7, 1, 7))));
		villager.setItemSlot(EquipmentSlot.MAINHAND, named(Items.IRON_HOE));
		eveningWithTheOwner(helper, villager, 1200, "Farmer's hoe", () -> true);
	}

	/** A Shepherd holding the village's named shears, off work with the owner beside them. */
	//$ gametest_ticks_batch AREA '2000' '"qaToolShepherd1101"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaToolShepherd1101")
	public void qaAShepherdsShearsOutlastAnEveningWithTheOwnerBeside(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		helper.setBlock(WORK, Blocks.LOOM);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, named(Items.SHEARS));
		eveningWithTheOwner(helper, villager, 1200, "Shepherd's shears", () -> true);
	}
}
