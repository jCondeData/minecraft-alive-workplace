package io.github.jcondedata.aliveworkplace.fossil;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * The Fossil Scientist's card (ROADMAP 43.3): an empty hand on a Fossil Scientist opens it (with Cobblemon) instead of
 * hearing how it's going in chat. The price and how many fossils they're working on, then a line per fossil of the
 * player's with how far along it is and a bar ("Ready" once it's done), and Hand over and Close. Hand over does exactly
 * what the empty-hand click did ({@link FossilScientists#check}: the Pokémon that are ready come to the player); with
 * nothing ready it's grayed out with the reason. A fossil in hand is still handed over at once, as before.
 */
public final class FossilCard {
	public static final int HAND_OVER = 0;

	/** Opens the card on {@code scientist} for {@code player}. */
	public static void open(ServerPlayer player, Villager scientist) {
		StatusCardMenu.open(player, scientist, card(player, scientist), (p, v, button) -> {
			if (button == HAND_OVER) {
				FossilScientists.check(p, v);
			}
		});
	}

	/** What the card shows {@code player} about {@code scientist} right now. */
	public static StatusCard card(ServerPlayer player, Villager scientist) {
		List<Revival> queue = FossilScientists.queue(scientist);
		List<StatusCard.Line> lines = new ArrayList<>();
		boolean ready = false;
		for (Revival r : queue) {
			if (!r.owner().equals(player.getUUID())) {
				continue;
			}
			ItemStack icon = r.items().isEmpty() ? ItemStack.EMPTY : new ItemStack(Lookup.value(BuiltInRegistries.ITEM, r.items().get(0)));
			if (r.ready()) {
				ready = true;
				lines.add(new StatusCard.Line(icon, FossilScientists.what(r.items()), Component.translatable("screen.aliveworkplace.card.fossil.ready"),
					StatusCard.GREEN, 1f));
			} else {
				lines.add(new StatusCard.Line(icon, FossilScientists.what(r.items()),
					Component.translatable("screen.aliveworkplace.card.fossil.percent", Math.round(r.progress() * 100)), StatusCard.GOLD, r.progress()));
			}
		}
		int price = FossilScientists.price(scientist);
		Component cost = Money.describe((long) price * Money.DOLLARS_PER_EMERALD, price);
		Component refusal = ready ? null : lines.isEmpty()
			? Component.translatable("screen.aliveworkplace.card.fossil.none", scientist.getDisplayName())
			: Component.translatable("screen.aliveworkplace.card.fossil.not_ready");
		Component tag = Component.translatable("screen.aliveworkplace.card.tag", BuilderLevels.jobName(scientist),
			BuilderLevels.levelName(BuilderLevels.level(scientist)));
		return new StatusCard(scientist.getId(), scientist.getDisplayName(), tag,
			Component.translatable("screen.aliveworkplace.card.fossil.subtitle", cost, queue.size(), FossilScientists.MAX_QUEUE),
			Component.translatable("screen.aliveworkplace.card.fossil.heading"), List.copyOf(lines),
			Component.translatable("message.aliveworkplace.fossil.hint", scientist.getDisplayName(), cost),
			List.of(StatusCard.Button.of(HAND_OVER, Component.translatable("screen.aliveworkplace.card.fossil.hand_over"), refusal),
				StatusCard.Button.of(StatusCardMenu.CLOSE, Component.translatable("screen.aliveworkplace.card.close"), null)),
			Component.translatable("screen.aliveworkplace.card.fossil.hint"));
	}

	private FossilCard() {
	}
}
