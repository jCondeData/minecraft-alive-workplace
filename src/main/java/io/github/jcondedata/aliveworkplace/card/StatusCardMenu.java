package io.github.jcondedata.aliveworkplace.card;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link StatusCard}'s menu (ROADMAP 43.3, for 42.5 too): no slots, just the card and its buttons, sent as vanilla's
 * menu-button clicks. {@link #CLOSE} closes; any other button of the card closes the card and runs the opener's
 * {@link Action} with its id, which does exactly what a click on the villager did before the card (its own checks,
 * messages and sounds, so a button pressed while the card showed it grayed out still answers with the reason). The
 * client's copy has no villager.
 */
public class StatusCardMenu extends AbstractContainerMenu {
	/** The Close button every card may have. */
	public static final int CLOSE = 99;
	/** How far (squared) the player may walk from the villager before the card closes. */
	private static final double REACH_SQR = 24 * 24;

	/** What a card's button does, on the server: {@code button} is its id. */
	public interface Action {
		void press(ServerPlayer player, Villager villager, int button);
	}

	private final StatusCard card;
	@Nullable
	private final Villager villager;
	@Nullable
	private final Action action;

	/** The client's copy. */
	public StatusCardMenu(int id, Inventory inventory, StatusCard card) {
		this(id, inventory, card, null, null);
	}

	public StatusCardMenu(int id, Inventory inventory, StatusCard card, @Nullable Villager villager, @Nullable Action action) {
		super(ModBlocks.STATUS_CARD_MENU, id);
		this.card = card;
		this.villager = villager;
		this.action = action;
	}

	/** Opens {@code card} about {@code villager} for {@code player}; its buttons (but Close) run {@code action}. */
	public static void open(ServerPlayer player, Villager villager, StatusCard card, Action action) {
		Platform.get().openMenu(player, new SimpleMenuProvider((id, inventory, p) -> new StatusCardMenu(id, inventory, card, villager, action),
			card.title()), card);
	}

	public StatusCard card() {
		return card;
	}

	@Nullable
	public Villager villager() {
		return villager;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (!(player instanceof ServerPlayer serverPlayer) || villager == null || action == null || card.button(id) == null) {
			return false;
		}
		serverPlayer.closeContainer();
		if (id != CLOSE) {
			action.press(serverPlayer, villager, id);
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return villager == null || villager.isAlive() && player.distanceToSqr(villager) <= REACH_SQR;
	}
}
