package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Trainer screen's menu (ROADMAP 43.1): no slots, just its {@link TrainerView} and three buttons sent as vanilla's
 * menu-button clicks. Challenge does what a click on the trainer did before the screen ({@link Trainers#challenge}),
 * Trade opens the trainer's trades as a sneak-click does, Close closes. The client's copy has no trainer.
 */
public class TrainerMenu extends AbstractContainerMenu {
	public static final int CHALLENGE = 0;
	public static final int TRADE = 1;
	public static final int CLOSE = 2;
	/** How far (squared) the player may walk from the trainer before the screen closes. */
	private static final double REACH_SQR = 24 * 24;

	private final TrainerView view;
	@Nullable
	private final Villager trainer;

	/** The client's copy. */
	public TrainerMenu(int id, Inventory inventory, TrainerView view) {
		this(id, inventory, view, null);
	}

	public TrainerMenu(int id, Inventory inventory, TrainerView view, @Nullable Villager trainer) {
		super(ModBlocks.TRAINER_MENU, id);
		this.view = view;
		this.trainer = trainer;
	}

	public TrainerView view() {
		return view;
	}

	@Nullable
	public Villager trainer() {
		return trainer;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (!(player instanceof ServerPlayer serverPlayer) || trainer == null) {
			return false;
		}
		switch (id) {
			case CHALLENGE -> {
				serverPlayer.closeContainer();
				Trainers.challenge(serverPlayer, trainer);
			}
			case TRADE -> {
				serverPlayer.closeContainer();
				trainer.mobInteract(serverPlayer, InteractionHand.MAIN_HAND); // the trades, as a sneak-click opens them
			}
			case CLOSE -> serverPlayer.closeContainer();
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return trainer == null || trainer.isAlive() && player.distanceToSqr(trainer) <= REACH_SQR;
	}
}
