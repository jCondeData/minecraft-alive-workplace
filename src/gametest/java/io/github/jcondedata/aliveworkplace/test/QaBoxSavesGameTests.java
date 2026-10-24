package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.DropBoxBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane E, saves): the Mailbox and the Drop Box, the two containers no other test saves and reads back. Each is
 * written as its chunk writes it and read back as a chunk load reads it ({@link BlockEntity#loadStatic}): every slot
 * with its count and components, the mailbox's owner and the name a player sees on it; a mailbox saved before it had an
 * owner reads as the plain "Mailbox".
 */
public class QaBoxSavesGameTests implements FabricGameTest {
	private static final BlockPos AT = new BlockPos(1, 2, 1);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMailboxKeepsItsMailAndOwnerOverASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(AT, ModBlocks.MAILBOX);
		BlockPos pos = helper.absolutePos(AT);
		MailboxBlockEntity box = (MailboxBlockEntity) level.getBlockEntity(pos);
		UUID owner = new UUID(11L, 22L);
		box.setOwner(owner, "Jesse");
		ItemStack letter = new ItemStack(Items.WRITABLE_BOOK);
		letter.set(DataComponents.CUSTOM_NAME, Component.literal("For the smith"));
		box.receive(letter);
		box.receive(new ItemStack(Items.EMERALD, 40));
		box.receive(new ItemStack(Items.EMERALD, 40)); // fills the first stack, then the next slot
		CompoundTag saved = box.saveWithFullMetadata(level.registryAccess());

		BlockEntity loaded = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
		helper.assertTrue(loaded instanceof MailboxBlockEntity, "not a mailbox after loading: " + loaded);
		MailboxBlockEntity back = (MailboxBlockEntity) loaded;
		helper.assertTrue(owner.equals(back.owner()) && back.ownerName().equals("Jesse"), "the owner: " + back.owner() + " " + back.ownerName());
		helper.assertTrue(back.getDisplayName().getString().equals("Jesse's Mailbox"), "the name on it: " + back.getDisplayName().getString());
		helper.assertTrue(back.getItem(0).is(Items.WRITABLE_BOOK) && "For the smith".equals(back.getItem(0).getHoverName().getString()), "the letter: " + back.getItem(0));
		helper.assertTrue(back.getItem(1).is(Items.EMERALD) && back.getItem(1).getCount() == 64 && back.getItem(2).getCount() == 16,
			"the emeralds: " + back.getItem(1) + " " + back.getItem(2));
		helper.assertTrue(back.getItem(3).isEmpty(), "an extra stack: " + back.getItem(3));

		CompoundTag old = new CompoundTag(); // a mailbox from before owners
		MailboxBlockEntity plain = (MailboxBlockEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos),
			withId(level, box, old), level.registryAccess());
		helper.assertTrue(plain.owner() == null && plain.isEmpty() && plain.getDisplayName().getString().equals("Mailbox"),
			"an old mailbox: " + plain.owner() + " " + plain.getDisplayName().getString());
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aDropBoxKeepsEverySlotOverASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(AT, ModBlocks.DROP_BOX);
		BlockPos pos = helper.absolutePos(AT);
		DropBoxBlockEntity box = (DropBoxBlockEntity) level.getBlockEntity(pos);
		box.setItem(0, new ItemStack(Items.WHEAT, 64));
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.setDamageValue(17);
		box.setItem(13, sword);
		box.setItem(DropBoxBlockEntity.SIZE - 1, new ItemStack(Items.COBBLESTONE, 5));
		CompoundTag saved = box.saveWithFullMetadata(level.registryAccess());

		BlockEntity loaded = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
		helper.assertTrue(loaded instanceof DropBoxBlockEntity, "not a drop box after loading: " + loaded);
		DropBoxBlockEntity back = (DropBoxBlockEntity) loaded;
		helper.assertTrue(back.getContainerSize() == DropBoxBlockEntity.SIZE, "the size: " + back.getContainerSize());
		helper.assertTrue(back.getItem(0).is(Items.WHEAT) && back.getItem(0).getCount() == 64, "slot 0: " + back.getItem(0));
		helper.assertTrue(back.getItem(13).is(Items.IRON_SWORD) && back.getItem(13).getDamageValue() == 17, "the sword: " + back.getItem(13));
		helper.assertTrue(back.getItem(DropBoxBlockEntity.SIZE - 1).getCount() == 5, "the last slot: " + back.getItem(DropBoxBlockEntity.SIZE - 1));
		int stacks = 0;
		for (int i = 0; i < back.getContainerSize(); i++) {
			stacks += back.getItem(i).isEmpty() ? 0 : 1;
		}
		helper.assertTrue(stacks == 3, "stacks after loading: " + stacks);
		helper.assertTrue(back.getDisplayName().getString().equals("Drop Box"), "the name on it: " + back.getDisplayName().getString());
		helper.succeed();
	}

	/** {@code tag} with the id and position a chunk saves with every block entity. */
	private static CompoundTag withId(ServerLevel level, BlockEntity like, CompoundTag tag) {
		CompoundTag full = like.saveWithFullMetadata(level.registryAccess());
		CompoundTag out = tag.copy();
		for (String key : new String[] {"id", "x", "y", "z"}) {
			out.put(key, full.get(key));
		}
		return out;
	}
}
