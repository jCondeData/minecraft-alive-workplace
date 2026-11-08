package io.github.jcondedata.aliveworkplace.nurse;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The Nurse's card (ROADMAP 43.3): an empty hand on a Nurse opens it instead of being treated at once. The player's
 * health and the bad effects she'd clear, then (with Cobblemon) a line per Pokémon in the party with its HP and a bar,
 * and Heal and Close. Heal does exactly what a click on her did ({@link Nurses#treat}); when she'd refuse, it's grayed
 * out with her reason under it.
 */
public final class NurseCard {
	public static final int HEAL = 0;
	private static final boolean COBBLEMON = Platform.get().isModLoaded("cobblemon");

	/** Opens the card on {@code nurse} for {@code player}. */
	public static void open(ServerPlayer player, Villager nurse) {
		StatusCardMenu.open(player, nurse, card(player, nurse), (p, v, button) -> {
			if (button == HEAL) {
				Nurses.treat(p, v);
			}
		});
	}

	/** What the card shows {@code player} about {@code nurse} right now. */
	public static StatusCard card(ServerPlayer player, Villager nurse) {
		List<StatusCard.Line> lines = new ArrayList<>();
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
		int health = (int) Math.ceil(player.getHealth());
		int max = (int) Math.ceil(player.getMaxHealth());
		lines.add(new StatusCard.Line(head, Component.translatable("screen.aliveworkplace.card.nurse.you"),
			Component.translatable("screen.aliveworkplace.card.nurse.hp", health, max), health >= max ? StatusCard.GREEN : StatusCard.ORANGE,
			max <= 0 ? 0f : Math.min(1f, player.getHealth() / player.getMaxHealth())));
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				lines.add(new StatusCard.Line(ItemStack.EMPTY, effect.getEffect().value().getDisplayName(),
					Component.translatable("screen.aliveworkplace.card.nurse.effect"), StatusCard.RED, StatusCard.NO_BAR));
			}
		}
		if (COBBLEMON) {
			lines.addAll(PokemonHealing.EXTENSION.call(h -> h.party(player), List.of()));
		}
		int seconds = Nurses.secondsLeft(BuilderLevels.delay(Nurses.COOLDOWN, nurse));
		Component subtitle = Nurses.machine(io.github.jcondedata.aliveworkplace.mc.Players.level(player), nurse) != null
			? Component.translatable("screen.aliveworkplace.card.nurse.subtitle_machine", seconds)
			: Component.translatable(COBBLEMON ? "screen.aliveworkplace.card.nurse.subtitle_party" : "screen.aliveworkplace.card.nurse.subtitle", seconds);
		Component tag = Component.translatable("screen.aliveworkplace.card.tag", BuilderLevels.jobName(nurse),
			BuilderLevels.levelName(BuilderLevels.level(nurse)));
		return new StatusCard(nurse.getId(), nurse.getDisplayName(), tag, subtitle,
			Component.translatable(COBBLEMON ? "screen.aliveworkplace.card.nurse.heading_party" : "screen.aliveworkplace.card.nurse.heading"),
			List.copyOf(lines), Component.empty(),
			List.of(StatusCard.Button.of(HEAL, Component.translatable("screen.aliveworkplace.card.nurse.heal"), Nurses.refusal(player, nurse)),
				StatusCard.Button.of(StatusCardMenu.CLOSE, Component.translatable("screen.aliveworkplace.card.close"), null)),
			Component.translatable("screen.aliveworkplace.card.nurse.hint"));
	}

	private NurseCard() {
	}
}
