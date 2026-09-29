package io.github.jcondedata.aliveworkplace.mail;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The mailbox screen: a row of outgoing slots with a "To:" field and a Send button, the mail that
 * arrived (take-only), and the player's inventory.
 */
public class MailboxMenu extends AbstractContainerMenu {
	public static final int OUT = 9;
	public static final int OUT_Y = 54;
	public static final int INBOX_Y = 88;
	public static final int INVENTORY_Y = 158;
	public static final int HOTBAR_Y = 216;
	private static final int INBOX_START = OUT;
	private static final int PLAYER_START = OUT + MailboxBlockEntity.SIZE;
	private static final int END = PLAYER_START + 36;

	private final SimpleContainer outgoing = new SimpleContainer(OUT);
	private final Container inbox;
	private final BlockPos pos;
	@Nullable
	private final MailboxBlockEntity mailbox;

	/** Client side. */
	public MailboxMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, new SimpleContainer(MailboxBlockEntity.SIZE), pos, null);
	}

	/** Server side. */
	public MailboxMenu(int id, Inventory inventory, MailboxBlockEntity mailbox) {
		this(id, inventory, mailbox, mailbox.getBlockPos(), mailbox);
	}

	private MailboxMenu(int id, Inventory inventory, Container inbox, BlockPos pos, @Nullable MailboxBlockEntity mailbox) {
		super(ModBlocks.MAILBOX_MENU, id);
		this.inbox = inbox;
		this.pos = pos;
		this.mailbox = mailbox;
		inbox.startOpen(inventory.player);
		for (int i = 0; i < OUT; i++) {
			addSlot(new Slot(outgoing, i, 8 + i * 18, OUT_Y));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inbox, col + row * 9, 8 + col * 18, INBOX_Y + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return false; // mail only comes in through the post
					}
				});
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
		}
	}

	public BlockPos pos() {
		return pos;
	}

	@Nullable
	public MailboxBlockEntity mailbox() {
		return mailbox;
	}

	/** Empties the outgoing slots and returns what was in them. */
	public List<ItemStack> takeOutgoing() {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < OUT; i++) {
			ItemStack stack = outgoing.removeItemNoUpdate(i);
			if (!stack.isEmpty()) {
				out.add(stack);
			}
		}
		broadcastChanges();
		return out;
	}

	public boolean hasOutgoing() {
		return !outgoing.isEmpty();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < PLAYER_START) {
			if (!moveItemStackTo(stack, PLAYER_START, END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 0, OUT, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return mailbox == null || Container.stillValidBlockEntity(mailbox, player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		inbox.stopOpen(player);
		if (!player.level().isClientSide()) {
			clearContainer(player, outgoing); // unsent items go back to the player
		}
	}
}
