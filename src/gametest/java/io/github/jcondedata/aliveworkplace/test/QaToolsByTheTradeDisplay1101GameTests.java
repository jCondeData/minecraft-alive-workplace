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
 * QA (lane E, 2026-11-01): a worker's tool, taken from the village's chest, is never lost because a player stood by
 * them after work. Vanilla's trade display ({@code ShowTradesToPlayer}) shows a villager's wares in its main hand and
 * empties that hand when a player is close (B-fisher: "fishes once, then needs a new rod"). Fishers and conscripts are
 * kept from it; here the other workers whose tool lives in that hand: the Miner's pickaxe (mid-quarry, the shift ends),
 * the Lumberjack's axe and the Physician's Remedy (the Farmer's hoe and the Shepherd's shears: QaToolsByTheTradeDisplay1101BugTests, a bug). Each tool carries a name to know it again; after the
 * evening with the owner beside them it must still be on the worker (hand or bag) or back in their chest.
 */
public class QaToolsByTheTradeDisplay1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
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
		if (bag != null && bag.stacks().stream().anyMatch(QaToolsByTheTradeDisplay1101GameTests::isIt)) {
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

	/**
	 * A Miner mid-quarry, with the village's named diamond pickaxe from the chest: the shift ends (the meeting hour) and
	 * the owner stands by them for the evening. The pickaxe is still theirs (hand or bag) or put back in the chest.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaToolMiner1101"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaToolMiner1101")
	public void qaAMinersPickaxeOutlastsAnEveningWithTheOwnerBeside(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(200, level.getServer()); // slow: the quarry outlasts the test
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, 2, 5), new BlockPos(8, 2, 8))) {
			helper.setBlock(p, Blocks.STONE);
		}
		helper.setBlock(WORK, ModBlocks.MINERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, named(Items.DIAMOND_PICKAXE));
		Villager miner = helper.spawn(EntityType.VILLAGER, AT);
		Miners.employ(level, miner, helper.absolutePos(WORK));
		QuarrySite site = Miners.start(level, miner, null, BoundingBox.fromCorners(helper.absolutePos(new BlockPos(5, 2, 5)),
			helper.absolutePos(new BlockPos(8, 2, 8))), 1);
		site.setStairs(false);
		boolean[] evening = {false};
		int[] waited = {0};
		helper.onEachTick(() -> {
			if (!evening[0] && isIt(miner.getMainHandItem())) {
				evening[0] = true; // they took the pickaxe and started: now the shift ends
				helper.setDayTime(9500);
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()); // back as the other tests expect
			} else if (!evening[0] && ++waited[0] > 1500) {
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
				helper.fail("the Miner never took the named pickaxe from the chest: it's in the " + whereIs(helper, miner));
			}
		});
		eveningWithTheOwner(helper, miner, 1200, "Miner's pickaxe", () -> evening[0]);
	}

	/** A Lumberjack holding the village's named iron axe, off work with the owner beside them. */
	//$ gametest_ticks_batch AREA '2000' '"qaToolLumberjack1101"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaToolLumberjack1101")
	public void qaALumberjacksAxeOutlastsAnEveningWithTheOwnerBeside(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		helper.setBlock(WORK, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		villager.setItemSlot(EquipmentSlot.MAINHAND, named(Items.IRON_AXE));
		eveningWithTheOwner(helper, villager, 1200, "Lumberjack's axe", () -> true);
	}

	/** A Physician holding a named Remedy from the chest by the stand (ready for a patient), off work with the owner beside them. */
	//$ gametest_ticks_batch AREA '2000' '"qaToolPhysician1101"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaToolPhysician1101")
	public void qaAPhysiciansRemedyOutlastsAnEveningWithTheOwnerBeside(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		helper.setBlock(WORK, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, named(io.github.jcondedata.aliveworkplace.registry.ModItems.REMEDY));
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		io.github.jcondedata.aliveworkplace.work.Stations.assign(level, villager, helper.absolutePos(WORK), ModVillagers.PHYSICIAN);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.work.PhysicianWork.ready(level, villager, helper.absolutePos(WORK))
			&& isIt(villager.getMainHandItem()), "the Physician didn't take the named Remedy from the chest: hand " + villager.getMainHandItem());
		eveningWithTheOwner(helper, villager, 1200, "Physician's Remedy", () -> true);
	}
}
