package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, Create and the pack, 2026-11-02): Create's Mechanical Piston moving the mod's own blocks. Create moves
 * blocks with block entities, which vanilla's piston never does, so the mod's blocks meet it only with Create installed.
 * A Wonder's Foundation Stone can't be pushed (wiki wonders.md: "pistons can't push it"), and a Mailbox, a Shop
 * Counter, a Drop Box or a Blueprint Table pushed along keeps exactly what was in it: nothing doubled on the ground, nothing
 * lost. Each case has the piston turning (checked) and a plank control that the same piston does push.
 */
public class QaCreateMovesOurBlocks1102eCompatTests implements FabricGameTest {

	private static final String AREA = CompatGameTests.AREA;
	/** The block in front of the piston; pushed two steps west it ends up at {@code TARGET.west(2)}. */
	private static final BlockPos TARGET = new BlockPos(7, 2, 8);

	/** A Mechanical Piston one step east of {@link #TARGET} facing west, two poles behind it, turned from below. */
	private static void piston(GameTestHelper helper) {
		BlockPos piston = TARGET.east();
		helper.setBlock(piston.east(2), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston.east(), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("mechanical_piston").defaultBlockState(), "facing", "west"),
			"axis_along_first", "true"));
		helper.setBlock(piston.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
	}

	/** The block entity at {@code pos}, or null (the helper's own getter throws when there's none). */
	private static BlockEntity be(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	private static float speed(GameTestHelper helper) {
		BlockEntity be = be(helper, TARGET.east());
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	/** The row the block may be in, west to east, as read. */
	private static String row(GameTestHelper helper) {
		StringBuilder out = new StringBuilder();
		for (int i = 4; i >= 0; i--) {
			BlockPos p = TARGET.west(i);
			out.append(p.getX()).append(": ").append(helper.getBlockState(p).getBlock().getDescriptionId()).append("; ");
		}
		return out.toString();
	}

	/** Where along the row {@code block} stands (x in the test's frame), or -1 when it's nowhere on it. */
	private static int where(GameTestHelper helper, Block block) {
		for (int i = 0; i <= 4; i++) {
			if (helper.getBlockState(TARGET.west(i)).is(block)) {
				return TARGET.west(i).getX();
			}
		}
		return -1;
	}

	/** {@code item} on the ground in and around the test area. */
	private static int onTheGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** {@code item} in any container block entity along the row. */
	private static int inContainers(GameTestHelper helper, Item item) {
		int n = 0;
		for (int i = 0; i <= 4; i++) {
			if (be(helper, TARGET.west(i)) instanceof Container c) {
				for (int s = 0; s < c.getContainerSize(); s++) {
					if (c.getItem(s).is(item)) {
						n += c.getItem(s).getCount();
					}
				}
			}
		}
		return n;
	}

	/** The control: the same piston pushes a plank two steps (the setup moves blocks at all). */
	//$ gametest_ticks_batch AREA '400' '"qa_create_moves_plank"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_moves_plank")
	public void theControlPistonPushesAPlankTwoSteps(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(TARGET, Blocks.OAK_PLANKS);
		piston(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(speed(helper) != 0, "the piston never turned");
			helper.assertTrue(where(helper, Blocks.OAK_PLANKS) == TARGET.west(2).getX(),
				"the control plank wasn't pushed two steps: " + row(helper));
			helper.succeed();
		});
	}

	/** A Wonder's Foundation Stone stays where it was laid: Create's piston can't push it, as vanilla's can't. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_moves_foundation"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_moves_foundation")
	public void aMechanicalPistonCantPushAWondersFoundationStone(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(TARGET, ModBlocks.FOUNDATION_STONE);
		piston(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(speed(helper) != 0, "the piston never turned, so the test proves nothing");
			helper.assertTrue(helper.getBlockState(TARGET).is(ModBlocks.FOUNDATION_STONE),
				"a Mechanical Piston moved the Wonder's Foundation Stone (wonders.md: pistons can't push it): " + row(helper));
			helper.succeed();
		});
	}

	/** Pushes a container block holding {@code count} of {@code item}; afterwards exactly {@code count} are in the world. */
	private static void containerKeepsItsItems(GameTestHelper helper, Block block, Item item, int count) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(TARGET, block);
		Container box = (Container) helper.getBlockEntity(TARGET);
		box.setItem(0, new ItemStack(item, count));
		piston(helper);
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper);
			int at = where(helper, block);
			int kept = inContainers(helper, item);
			int ground = onTheGround(helper, item);
			helper.assertTrue(turning != 0, "the piston never turned, so the test proves nothing");
			helper.assertTrue(kept + ground == count, "a " + block.getDescriptionId() + " holding " + count + " " + item
				+ " moved by a Mechanical Piston: " + kept + " in it and " + ground + " on the ground (block at x " + at + "): " + row(helper));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_moves_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_moves_mailbox")
	public void aMailboxPushedByAMechanicalPistonKeepsItsLettersOnce(GameTestHelper helper) {
		containerKeepsItsItems(helper, ModBlocks.MAILBOX, Items.PAPER, 5);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_moves_counter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_moves_counter")
	public void aShopCounterPushedByAMechanicalPistonKeepsItsStockOnce(GameTestHelper helper) {
		containerKeepsItsItems(helper, ModBlocks.SHOP_COUNTER, Items.DIAMOND, 7);
	}

	//$ gametest_ticks_batch AREA '400' '"qa_create_moves_dropbox"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_moves_dropbox")
	public void aDropBoxPushedByAMechanicalPistonKeepsWhatWasDroppedOnce(GameTestHelper helper) {
		containerKeepsItsItems(helper, ModBlocks.DROP_BOX, Items.EMERALD, 9);
	}

	/** A Blueprint Table with three blueprints stored, pushed along: three blueprints in the world afterwards, not six. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_moves_table"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_moves_table")
	public void aBlueprintTablePushedByAMechanicalPistonKeepsItsBlueprintsOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(TARGET, ModBlocks.BLUEPRINT_TABLE);
		BlueprintTableBlockEntity table = helper.getBlockEntity(TARGET);
		for (int i = 0; i < 3; i++) {
			table.put(new ItemStack(ModItems.BLANK_BLUEPRINT));
		}
		piston(helper);
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper);
			int stored = 0;
			for (int i = 0; i <= 4; i++) {
				if (be(helper, TARGET.west(i)) instanceof BlueprintTableBlockEntity t) {
					stored += t.count();
				}
			}
			int ground = onTheGround(helper, ModItems.BLANK_BLUEPRINT);
			helper.assertTrue(turning != 0, "the piston never turned, so the test proves nothing");
			helper.assertTrue(stored + ground == 3, "a Blueprint Table holding 3 blueprints moved by a Mechanical Piston: " + stored
				+ " in it and " + ground + " on the ground: " + row(helper));
			helper.succeed();
		});
	}
}
