package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, Create and saves, 2026-11-03): the mod's blocks carried by Create's Mechanical Piston keep every saved
 * field, not only their items (QaCreateMovesOurBlocks1102eCompatTests counts the items), and the world saved and
 * loaded while the block is in the air on the moving contraption (a player logging off, the chunk unloading mid-push)
 * loses nothing either. "Don't break existing saves" (CLAUDE.md) and the block's own page: a Mailbox keeps its owner, a
 * Shop Counter its owner and stock, a Storehouse its owner and orders, a grave who lies there.
 * <p>
 * Each case: the block set up, its saved data read ({@code saveWithoutMetadata}, which leaves out the position),
 * pushed two steps; afterwards the block stands two steps on and its saved data reads the same.
 */
public class QaCreateMovedSaved1103eCompatTests implements FabricGameTest {

	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TARGET = new BlockPos(7, 2, 8);
	private static final UUID OWNER = UUID.fromString("5d1c7c1e-1103-4e00-9e00-00000000e103");

	/** QaCreateMovesOurBlocks1102eCompatTests' piston: one step east of {@link #TARGET}, facing west, two poles, a motor below. */
	private static void piston(GameTestHelper helper) {
		BlockPos piston = TARGET.east();
		helper.setBlock(piston.east(2), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston.east(), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("mechanical_piston").defaultBlockState(), "facing", "west"),
			"axis_along_first", "true"));
		helper.setBlock(piston.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
	}

	private static List<Entity> contraptions(GameTestHelper helper) {
		// a piston's is Create's stationary_contraption (ControlledContraptionEntity); any of Create's *contraption types counts
		return helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds().inflate(4), e -> {
			ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
			return e.isAlive() && id.getNamespace().equals("create") && id.getPath().endsWith("contraption");
		});
	}

	/**
	 * Sets {@code block} at TARGET, {@code fill}s its block entity, pushes it; with {@code reload}, the moment it is in
	 * the air on the contraption, the contraption is saved, removed and loaded back. 300 ticks on its saved data reads
	 * as before.
	 */
	private static void keepsEverything(GameTestHelper helper, Block block, Consumer<BlockEntity> fill, boolean reload) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TARGET, block);
		BlockEntity be = level.getBlockEntity(helper.absolutePos(TARGET));
		helper.assertTrue(be != null, "setup: no block entity in " + block.getDescriptionId());
		fill.accept(be);
		be.setChanged();
		CompoundTag before = be.saveWithoutMetadata(level.registryAccess());
		piston(helper);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (!reload || reloaded[0] || helper.getBlockState(TARGET).is(block)) {
				return;
			}
			List<Entity> moving = contraptions(helper);
			if (moving.isEmpty()) {
				return;
			}
			reloaded[0] = true;
			for (Entity e : moving) {
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(e.save(tag), "setup: the contraption wasn't saved");
				e.discard();
				Entity back = EntityType.loadEntityRecursive(tag, level, x -> x);
				helper.assertTrue(back != null, "the moving contraption didn't load from its save: " + tag.getAllKeys());
				level.addFreshEntity(back);
			}
		});
		helper.runAfterDelay(300, () -> {
			if (reload) {
				helper.assertTrue(reloaded[0], "setup: the block was never seen in the air on the contraption, so nothing was saved mid-move");
			}
			BlockPos to = TARGET.west(2);
			helper.assertTrue(helper.getBlockState(to).is(block), "the " + block.getDescriptionId() + " isn't two steps on" + (reload ? " after a save mid-move" : "")
				+ ": at the start " + helper.getBlockState(TARGET).getBlock().getDescriptionId() + ", one on " + helper.getBlockState(TARGET.west()).getBlock().getDescriptionId()
				+ ", two on " + helper.getBlockState(to).getBlock().getDescriptionId());
			BlockEntity moved = level.getBlockEntity(helper.absolutePos(to));
			helper.assertTrue(moved != null, "the moved " + block.getDescriptionId() + " has no block entity");
			CompoundTag after = moved.saveWithoutMetadata(level.registryAccess());
			helper.assertTrue(before.equals(after), "the " + block.getDescriptionId() + " moved by a Mechanical Piston" + (reload ? " and saved mid-move" : "")
				+ " doesn't keep its saved data: before " + before + ", after " + after);
			helper.succeed();
		});
	}

	private static void mailbox(BlockEntity be) {
		((MailboxBlockEntity) be).setOwner(OWNER, "Jesse");
		((Container) be).setItem(0, new ItemStack(Items.PAPER, 5));
		((Container) be).setItem(3, new ItemStack(Items.WRITABLE_BOOK));
	}

	private static void counter(BlockEntity be) {
		((ShopCounterBlockEntity) be).setOwner(OWNER, "Jesse");
		((Container) be).setItem(0, new ItemStack(Items.DIAMOND, 7));
		((Container) be).setItem(1, new ItemStack(Items.BREAD, 12));
	}

	private static void storehouse(BlockEntity be) {
		StorehouseBlockEntity s = (StorehouseBlockEntity) be;
		s.setOwner(OWNER, "Jesse");
		s.setOrders(Map.of(Items.WHEAT, 64, Items.OAK_LOG, 32));
	}

	private static void grave(BlockEntity be) {
		CompoundTag villager = new CompoundTag();
		villager.putString("id", "minecraft:villager");
		villager.putInt("Xp", 42);
		((GraveBlockEntity) be).fill(villager, Component.literal("Old Tom"), "minecraft:farmer", 3, 123456L);
		((GraveBlockEntity) be).setFuneral(true);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_saved_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saved_mailbox")
	public void aMailboxPushedByAMechanicalPistonKeepsItsOwnerAndLetters(GameTestHelper helper) {
		keepsEverything(helper, ModBlocks.MAILBOX, QaCreateMovedSaved1103eCompatTests::mailbox, false);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_saved_mailbox_mid"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saved_mailbox_mid")
	public void aMailboxSavedMidPushKeepsItsOwnerAndLetters(GameTestHelper helper) {
		keepsEverything(helper, ModBlocks.MAILBOX, QaCreateMovedSaved1103eCompatTests::mailbox, true);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_saved_counter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saved_counter")
	public void aShopCounterPushedByAMechanicalPistonKeepsItsOwnerAndStock(GameTestHelper helper) {
		keepsEverything(helper, ModBlocks.SHOP_COUNTER, QaCreateMovedSaved1103eCompatTests::counter, false);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_saved_counter_mid"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saved_counter_mid")
	public void aShopCounterSavedMidPushKeepsItsOwnerAndStock(GameTestHelper helper) {
		keepsEverything(helper, ModBlocks.SHOP_COUNTER, QaCreateMovedSaved1103eCompatTests::counter, true);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_saved_storehouse"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saved_storehouse")
	public void aStorehousePushedByAMechanicalPistonKeepsItsOwnerAndOrders(GameTestHelper helper) {
		keepsEverything(helper, ModBlocks.STOREHOUSE, QaCreateMovedSaved1103eCompatTests::storehouse, false);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_saved_storehouse_mid"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saved_storehouse_mid")
	public void aStorehouseSavedMidPushKeepsItsOwnerAndOrders(GameTestHelper helper) {
		keepsEverything(helper, ModBlocks.STOREHOUSE, QaCreateMovedSaved1103eCompatTests::storehouse, true);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_saved_grave"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saved_grave")
	public void aGravePushedByAMechanicalPistonKeepsWhoLiesThere(GameTestHelper helper) {
		keepsEverything(helper, ModBlocks.GRAVE, QaCreateMovedSaved1103eCompatTests::grave, false);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_saved_grave_mid"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_saved_grave_mid")
	public void aGraveSavedMidPushKeepsWhoLiesThere(GameTestHelper helper) {
		keepsEverything(helper, ModBlocks.GRAVE, QaCreateMovedSaved1103eCompatTests::grave, true);
	}
}
