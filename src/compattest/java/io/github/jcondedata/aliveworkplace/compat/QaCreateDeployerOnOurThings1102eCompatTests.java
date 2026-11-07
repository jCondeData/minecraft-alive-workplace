package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlockEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * QA (lane-e-1102-1915, Create and the pack): Create's Deployer, a fake player with no real client, right-clicking with
 * what it holds on the mod's blocks, tested from the guide's promises for a player's own right-click.
 *
 * <p>village-hall.md: "Whoever places the hall owns it", and in a protected village "only you, your friends and operators
 * can break or place blocks". A Deployer acts as the player who placed it (Create gives its fake player that player's
 * UUID) or, placed by no one, as a nameless "Deployer". So a stranger's deployer inside Frank's protected village places
 * no block and pours no water, while Frank's own deployer does (the control, in the same village). A Name Tag on an open
 * hall renames it and isn't used up (village-hall.md), from a deployer as from a hand; at a protected hall a stranger's
 * deployer changes nothing. A deployer Frank placed, setting down a Village Hall, makes Frank its owner.
 *
 * <p>Each test calibrates on vanilla: the first one shows a deployer placing cobblestone two blocks in front of it, and
 * the others carry a control deployer that really clicks (it places, pours, renames or flips a lever).
 */
public class QaCreateDeployerOnOurThings1102eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	/** The deployer under test, facing west; its creative motor sits north of it; it acts on {@link #TARGET}. */
	static final BlockPos DEPLOYER = new BlockPos(12, 2, 8);
	static final BlockPos TARGET = DEPLOYER.west(2);
	/** The control deployer, six blocks south, acting on {@link #CONTROL_TARGET}. */
	static final BlockPos CONTROL = DEPLOYER.south(6);
	static final BlockPos CONTROL_TARGET = CONTROL.west(2);
	static final BlockPos HALL = new BlockPos(3, 2, 22);
	static final UUID FRANK = UUID.fromString("0f7a2c1e-4b3d-4e5f-8a9b-0c1d2e3f4a5b");

	static BlockState create(String id) {
		return MechanicCompatTests.create(id).defaultBlockState();
	}

	/**
	 * A deployer at {@code at} facing west, owned by {@code owner} (null: placed by nobody), handed {@code held} through its
	 * item storage, as a funnel would; two ticks later a creative motor behind it starts it.
	 */
	static void deployer(GameTestHelper helper, BlockPos at, @Nullable UUID owner, ItemStack held) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(at, MechanicCompatTests.with(create("deployer"), "facing", "west"));
		BlockEntity be = level.getBlockEntity(helper.absolutePos(at));
		if (be == null) {
			throw new GameTestAssertException("set-up: no deployer block entity at " + at);
		}
		if (owner != null) {
			CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
			tag.putUUID("Owner", owner);
			be.loadWithComponents(tag, level.registryAccess());
		}
		helper.runAfterDelay(2, () -> {
			if (!held.isEmpty()) {
				long in = insert(level, helper.absolutePos(at), held);
				helper.assertTrue(in == held.getCount(), "set-up: the deployer took " + in + " of " + held);
			}
			// a deployer takes its shaft from a side, not its back: a motor north of it, and its axis turned to meet it
			helper.setBlock(at.north(), MechanicCompatTests.with(create("creative_motor"), "facing", "south"));
		});
		helper.runAfterDelay(6, () -> {
			if (speed(helper, at) == 0) {
				BlockState state = helper.getBlockState(at);
				boolean first = Boolean.parseBoolean(state.getValue(state.getBlock().getStateDefinition().getProperty("axis_along_first")).toString());
				helper.getLevel().setBlock(helper.absolutePos(at), MechanicCompatTests.with(state, "axis_along_first", String.valueOf(!first)), 3);
			}
		});
	}

	static float speed(GameTestHelper helper, BlockPos pos) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	static long insert(ServerLevel level, BlockPos pos, ItemStack stack) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, Direction.EAST);
		if (storage == null) {
			storage = ItemStorage.SIDED.find(level, pos, null);
		}
		if (storage == null) {
			throw new GameTestAssertException("set-up: no item storage on the deployer");
		}
		try (Transaction tx = Transaction.openOuter()) {
			long in = storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
			tx.commit();
			return in;
		}
	}

	/** What the deployer at {@code at} holds, from its saved tag. */
	static ItemStack held(GameTestHelper helper, BlockPos at) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(at));
		if (be == null) {
			return ItemStack.EMPTY;
		}
		CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
		return tag.contains("HeldItem") ? ItemStack.parseOptional(helper.getLevel().registryAccess(), Nbt.getCompound(tag, "HeldItem")) : ItemStack.EMPTY;
	}

	/** Frank's Village Hall at {@link #HALL}, protected or open. */
	static VillageHallBlockEntity franksHall(GameTestHelper helper, boolean protect) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(FRANK, "Frank");
		hall.setCustomName(Component.literal("Frankton"));
		if (protect) {
			hall.setProtected(true);
		}
		return hall;
	}

	static void start(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
	}

	/** Runs {@code check} {@code ticks} from now, after turning every hall's protection in the test area off again. */
	static void thenOpenUpAndCheck(GameTestHelper helper, int ticks, Runnable check) {
		helper.runAfterDelay(ticks, () -> {
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(29, 4, 29))) {
				if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof VillageHallBlockEntity h && h.isProtected()) {
					h.setProtected(false);
				}
			}
			check.run();
			helper.succeed();
		});
	}


	// ---------------------------------------------------------------- the calibration: vanilla

	/** A deployer placed by nobody, holding cobblestone, puts it two blocks in front of it, and holds none after. */
	//$ gametest_ticks_batch AREA '400' '"qa_cdep_calibrate"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdep_calibrate")
	public void aDeployerPlacesCobblestoneInFrontOfIt(GameTestHelper helper) {
		start(helper);
		deployer(helper, DEPLOYER, null, new ItemStack(Items.COBBLESTONE, 1));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.COBBLESTONE), "the deployer hasn't placed its cobblestone at " + TARGET + ": "
				+ helper.getBlockState(TARGET) + " (one nearer: " + helper.getBlockState(DEPLOYER.west()) + "; speed " + speed(helper, DEPLOYER) + ", holding " + held(helper, DEPLOYER) + ")");
			helper.assertTrue(held(helper, DEPLOYER).isEmpty(), "the deployer still holds " + held(helper, DEPLOYER) + " after placing its one cobblestone");
		});
	}

	// ---------------------------------------------------------------- protection

	// ---------------------------------------------------------------- the hall and a name tag

	static ItemStack nameTag(String name) {
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return tag;
	}

	/** An open hall: a deployer's named Name Tag renames it as a hand's does, keeps Frank its owner, and isn't used up. */
	//$ gametest_ticks_batch AREA '400' '"qa_cdep_hall_nametag"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdep_hall_nametag")
	public void aDeployersNameTagRenamesAnOpenHallAndIsNotUsedUp(GameTestHelper helper) {
		start(helper);
		helper.setBlock(TARGET, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity at = helper.getBlockEntity(TARGET);
		at.setOwner(FRANK, "Frank");
		deployer(helper, DEPLOYER, null, nameTag("Oakvale"));
		helper.succeedWhen(() -> {
			Component name = at.getCustomName();
			helper.assertTrue(name != null && name.getString().equals("Oakvale"), "the deployer's name tag didn't rename the hall: it is " + name);
			helper.assertTrue(FRANK.equals(at.owner()) && "Frank".equals(at.ownerName()), "renaming made " + at.ownerName() + " (" + at.owner() + ") the hall's owner, not Frank");
			ItemStack kept = held(helper, DEPLOYER);
			helper.assertTrue(kept.is(Items.NAME_TAG) && kept.getCount() == 1, "the name tag was used up or doubled: the deployer holds " + kept);
		});
	}

	// ---------------------------------------------------------------- placing and claiming our blocks

	/** "Whoever places the hall owns it": a deployer Frank placed, setting a Village Hall down, makes Frank its owner. */
	//$ gametest_ticks_batch AREA '400' '"qa_cdep_place_hall_owned"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdep_place_hall_owned")
	public void aDeployerFrankPlacedSetsDownAHallFrankOwns(GameTestHelper helper) {
		start(helper);
		deployer(helper, DEPLOYER, FRANK, new ItemStack(ModBlocks.VILLAGE_HALL.asItem()));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(TARGET).is(ModBlocks.VILLAGE_HALL), "the deployer hasn't placed the hall: " + helper.getBlockState(TARGET));
			VillageHallBlockEntity hall = helper.getBlockEntity(TARGET);
			helper.assertTrue(FRANK.equals(hall.owner()), "the hall Frank's deployer set down is owned by " + hall.ownerName() + " (" + hall.owner() + "), not Frank");
			helper.assertTrue(held(helper, DEPLOYER).isEmpty(), "the deployer still holds " + held(helper, DEPLOYER) + " after placing its one hall");
		});
	}

	/** Control for the claim tests: a deployer placed by nobody, holding a stick, flips a lever in front of it. */
	static void leverControl(GameTestHelper helper) {
		helper.setBlock(CONTROL_TARGET.below(), Blocks.STONE);
		helper.setBlock(CONTROL_TARGET, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
		deployer(helper, CONTROL, null, new ItemStack(Items.STICK));
	}

	static void leverFlipped(GameTestHelper helper) {
		helper.assertTrue(helper.getBlockState(CONTROL_TARGET).getValue(LeverBlock.POWERED),
			"the control: the deployer holding a stick didn't flip the lever: " + helper.getBlockState(CONTROL_TARGET));
	}

	/** Frank's mailbox: a stranger's deployer clicking it takes none of his mail and doesn't make it theirs. */
	//$ gametest_ticks_batch AREA '400' '"qa_cdep_franks_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdep_franks_mailbox")
	public void aStrangersDeployerTakesNothingFromFranksMailbox(GameTestHelper helper) {
		start(helper);
		helper.setBlock(TARGET, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(TARGET);
		box.setOwner(FRANK, "Frank");
		helper.assertTrue(box.receive(new ItemStack(Items.DIAMOND, 3)).isEmpty(), "set-up: the post couldn't deliver 3 diamonds");
		leverControl(helper);
		deployer(helper, DEPLOYER, null, new ItemStack(Items.STICK));
		helper.runAfterDelay(240, () -> {
			leverFlipped(helper);
			int diamonds = 0;
			for (int s = 0; s < box.getContainerSize(); s++) {
				diamonds += box.getItem(s).is(Items.DIAMOND) ? box.getItem(s).getCount() : 0;
			}
			helper.assertTrue(diamonds == 3, "Frank's mailbox holds " + diamonds + " of his 3 diamonds after a stranger's deployer clicked it");
			helper.assertTrue(FRANK.equals(box.owner()), "Frank's mailbox is now " + box.ownerName() + "'s");
			helper.assertTrue(held(helper, DEPLOYER).is(Items.STICK), "the deployer's stick became " + held(helper, DEPLOYER));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- villagers, a bounty, and our blocks clicked

	/** The entity control: a deployer's named Name Tag names the jobless villager in front of it, used up once (vanilla). */
	//$ gametest_ticks_batch AREA '400' '"qa_cdep_villager_tag"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdep_villager_tag")
	public void aDeployersNameTagNamesAVillagerOnce(GameTestHelper helper) {
		start(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, TARGET);
		villager.setNoAi(true);
		ItemStack tags = nameTag("Bob");
		tags.setCount(2);
		deployer(helper, DEPLOYER, null, tags);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.isAlive(), "the villager died");
			helper.assertTrue(villager.hasCustomName() && villager.getCustomName().getString().equals("Bob"), "the deployer's name tag didn't name the villager: " + villager.getCustomName());
			ItemStack kept = held(helper, DEPLOYER);
			int onGround = 0;
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), e -> e.getItem().is(Items.NAME_TAG))) {
				onGround += e.getItem().getCount();
			}
			helper.assertTrue(kept.is(Items.NAME_TAG) && kept.getCount() == 1 && onGround == 0,
				"naming one villager should use up one of 2 tags: the deployer holds " + kept + ", " + onGround + " lie on the ground");
		});
	}

	/**
	 * captains-insignia.md: right-click the hall of the village that posted him; "the reward goes to whoever hands it in,
	 * once", and "a bounty paid is a bounty off the board". Frank's deployer holding the insignia (it stacks to one) at
	 * that hall: the insignia is taken once and the bounty is off the board.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_cdep_insignia"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdep_insignia")
	public void aDeployerHandsInACaptainsInsigniaOnce(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TARGET, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(TARGET);
		hall.setOwner(FRANK, "Frank");
		BlockPos hallAt = helper.absolutePos(TARGET);
		Bounties.Bounty bounty = Bounties.post(level, hallAt, AliveWorkplace.id("hexer"), 0, 0, hallAt.offset(160, 0, 0));
		helper.assertTrue(bounty != null && Bounties.open(level, hallAt).stream().anyMatch(b -> b.id.equals(bounty.id)), "set-up: no bounty posted at the hall");
		deployer(helper, DEPLOYER, FRANK, Bounties.insignia(level, bounty));
		helper.succeedWhen(() -> {
			boolean open = Bounties.open(level, hallAt).stream().anyMatch(b -> b.id.equals(bounty.id));
			helper.assertTrue(!open, "the bounty is still on the board after Frank's deployer clicked the hall with its insignia (it holds " + held(helper, DEPLOYER) + ")");
			ItemStack kept = held(helper, DEPLOYER);
			int lying = 0;
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(ModItems.CAPTAINS_INSIGNIA))) {
				lying += e.getItem().getCount();
			}
			helper.assertTrue(kept.isEmpty() && lying == 0, "the insignia for the paid bounty wasn't taken: the deployer holds " + kept + ", " + lying + " lie on the ground");
		});
	}

	/**
	 * A stick (which none of them takes) clicked on Frank's open hall, a Blueprint Table holding 3 blank blueprints, a
	 * Builder's Bench and a Drop Box holding 9 emeralds: whatever opens for the deployer, nothing is taken, changed or
	 * claimed, and the stick is kept. The control: the same deployer row flips a lever.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_cdep_stick_blocks"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdep_stick_blocks")
	public void aDeployerClickingOurBlocksWithAStickTakesAndChangesNothing(GameTestHelper helper) {
		start(helper);
		BlockPos[] rows = {new BlockPos(12, 2, 3), new BlockPos(12, 2, 8), new BlockPos(12, 2, 13), new BlockPos(12, 2, 18), new BlockPos(12, 2, 23)};
		helper.setBlock(rows[0].west(2), ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(rows[0].west(2));
		hall.setOwner(FRANK, "Frank");
		hall.setCustomName(Component.literal("Frankton"));
		helper.setBlock(rows[1].west(2), ModBlocks.BLUEPRINT_TABLE);
		BlueprintTableBlockEntity table = helper.getBlockEntity(rows[1].west(2));
		for (int i = 0; i < 3; i++) {
			table.put(new ItemStack(ModItems.BLANK_BLUEPRINT));
		}
		helper.setBlock(rows[2].west(2), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(rows[3].west(2), ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(rows[3].west(2));
		box.setItem(0, new ItemStack(Items.EMERALD, 9));
		helper.setBlock(rows[4].west(2).below(), Blocks.STONE);
		helper.setBlock(rows[4].west(2), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
		for (BlockPos row : rows) {
			deployer(helper, row, null, new ItemStack(Items.STICK));
		}
		helper.runAfterDelay(240, () -> {
			helper.assertTrue(helper.getBlockState(rows[4].west(2)).getValue(LeverBlock.POWERED), "the control: the deployer row didn't flip its lever");
			helper.assertTrue(FRANK.equals(hall.owner()) && hall.getCustomName() != null && hall.getCustomName().getString().equals("Frankton"),
				"Frank's hall changed after a deployer clicked it: " + hall.ownerName() + "'s, named " + hall.getCustomName());
			helper.assertTrue(table.count() == 3, "the Blueprint Table holds " + table.count() + " of its 3 blueprints after a deployer clicked it");
			helper.assertTrue(helper.getBlockState(rows[2].west(2)).is(ModBlocks.BUILDERS_BENCH), "the bench became " + helper.getBlockState(rows[2].west(2)));
			int emeralds = 0;
			for (int s = 0; s < box.getContainerSize(); s++) {
				emeralds += box.getItem(s).is(Items.EMERALD) ? box.getItem(s).getCount() : 0;
			}
			helper.assertTrue(emeralds == 9, "the Drop Box holds " + emeralds + " of its 9 emeralds after a deployer clicked it");
			int blueprintsOut = 0;
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), e -> e.getItem().is(ModItems.BLANK_BLUEPRINT) || e.getItem().is(Items.EMERALD))) {
				blueprintsOut += e.getItem().getCount();
			}
			helper.assertTrue(blueprintsOut == 0, blueprintsOut + " blueprints or emeralds lie on the ground");
			for (BlockPos row : rows) {
				helper.assertTrue(held(helper, row).is(Items.STICK), "the deployer at " + row + " lost its stick: it holds " + held(helper, row));
			}
			helper.succeed();
		});
	}
}
