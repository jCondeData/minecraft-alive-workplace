package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * The daycare's screen and Pokémon (see {@link Daycare}): row 1 your Pokémon in the rancher's care, with the level they'd
 * come back at and the price (click to collect); row 3 your party (click twice to leave one).
 */
public final class CobblemonDaycare {
	public static final int INFO = 0;
	public static final int FIRST_BOARDER_SLOT = 2;
	public static final int FIRST_PARTY_SLOT = 18;
	static final double REACH = 8;

	private static final class State {
		@Nullable
		UUID pending;
	}

	public static void open(ServerPlayer player, Villager rancher) {
		if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.pokemon_trader.in_battle").withStyle(ChatFormatting.YELLOW));
			return;
		}
		State state = new State();
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.daycare", rancher.getDisplayName()),
			p -> rancher.isAlive() && p.isAlive() && p.distanceTo(rancher) <= REACH,
			menu -> render(menu, player, rancher, state));
	}

	/** The screen without showing it (tests). */
	public static ChoiceMenu menuForTest(ServerPlayer player, Villager rancher) {
		State state = new State();
		return ChoiceMenu.detached(player, menu -> render(menu, player, rancher, state));
	}

	private static void render(ChoiceMenu menu, ServerPlayer player, Villager rancher, State state) {
		menu.clearButtons();
		ItemStack info = named(Items.HAY_BLOCK, Component.translatable("screen.aliveworkplace.daycare", rancher.getDisplayName()));
		info.set(DataComponents.LORE, lore(Component.translatable("screen.aliveworkplace.daycare.info", Daycare.MAX_PER_PLAYER).withStyle(ChatFormatting.GRAY),
			Component.translatable("screen.aliveworkplace.daycare.pace", rancher.getVillagerData().getLevel() > 1
				? Component.translatable("screen.aliveworkplace.daycare.faster", Math.round(25 * (rancher.getVillagerData().getLevel() - 1)))
				: Component.empty()).withStyle(ChatFormatting.DARK_GRAY)));
		menu.button(INFO, info, null);
		// Row 1: yours in the rancher's care
		List<Daycare.Boarder> boarders = Daycare.boarders(rancher);
		int shown = 0;
		for (Daycare.Boarder boarder : boarders) {
			if (!boarder.owner().equals(player.getUUID())) {
				continue;
			}
			Pokemon pokemon = load(Players.level(player), boarder);
			if (pokemon == null) {
				continue;
			}
			int gained = Daycare.experience(rancher, player.level().getGameTime() - boarder.since());
			int levelAfter = levelAfter(pokemon, gained);
			int price = Daycare.price(levelAfter - pokemon.getLevel());
			ItemStack icon = PokemonItem.from(pokemon);
			icon.set(DataComponents.CUSTOM_NAME, plain(pokemon.getDisplayName(false).copy().withStyle(ChatFormatting.WHITE)));
			icon.set(DataComponents.LORE, lore(
				Component.translatable("screen.aliveworkplace.daycare.levels", pokemon.getLevel(), levelAfter).withStyle(ChatFormatting.AQUA),
				Component.translatable("screen.aliveworkplace.daycare.collect", Money.describe((long) price * Money.DOLLARS_PER_EMERALD, price))
					.withStyle(ChatFormatting.GREEN)));
			menu.button(FIRST_BOARDER_SLOT + shown, icon, p -> {
				Chat.chat(p, collect(p, rancher, boarder));
				render(menu, player, rancher, state);
			});
			shown++;
		}
		menu.divider(1);
		// Row 3: your party
		List<Pokemon> party = new ArrayList<>();
		for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			party.add(pokemon);
		}
		boolean full = shown >= Daycare.MAX_PER_PLAYER || boarders.size() >= Daycare.MAX_BOARDERS;
		for (int i = 0; i < party.size() && i < 6; i++) {
			Pokemon pokemon = party.get(i);
			Component refusal = full ? Component.translatable("screen.aliveworkplace.daycare.full", Daycare.MAX_PER_PLAYER)
				: party.size() <= 1 ? Component.translatable("screen.aliveworkplace.daycare.last")
				: !pokemon.canLevelUpFurther() ? Component.translatable("screen.aliveworkplace.daycare.max_level") : null;
			boolean pending = pokemon.getUuid().equals(state.pending);
			ItemStack icon = PokemonItem.from(pokemon);
			icon.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("message.aliveworkplace.tutor.pokemon", pokemon.getDisplayName(false),
				pokemon.getLevel()).withStyle(ChatFormatting.WHITE)));
			icon.set(DataComponents.LORE, lore(refusal != null ? refusal.copy().withStyle(ChatFormatting.RED)
				: pending ? Component.translatable("screen.aliveworkplace.daycare.confirm", pokemon.getDisplayName(false)).withStyle(ChatFormatting.YELLOW)
				: Component.translatable("screen.aliveworkplace.daycare.leave").withStyle(ChatFormatting.GREEN)));
			if (pending) {
				icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(FIRST_PARTY_SLOT + i, icon, refusal != null ? null : p -> {
				if (!pokemon.getUuid().equals(state.pending)) {
					state.pending = pokemon.getUuid();
				} else {
					state.pending = null;
					leave(p, rancher, pokemon);
				}
				render(menu, player, rancher, state);
			});
		}
	}

	/** The level {@code pokemon} would be at with {@code gained} more experience. */
	static int levelAfter(Pokemon pokemon, int gained) {
		var group = pokemon.getExperienceGroup();
		int max = group.getExperience(Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel());
		return Math.max(pokemon.getLevel(), group.getLevel((int) Math.min(max, (long) pokemon.getExperience() + gained)));
	}

	/** {@code player} leaves {@code pokemon} with the rancher. */
	public static boolean leave(ServerPlayer player, Villager rancher, Pokemon pokemon) {
		List<Daycare.Boarder> boarders = new ArrayList<>(Daycare.boarders(rancher));
		long mine = boarders.stream().filter(b -> b.owner().equals(player.getUUID())).count();
		if (BattleRegistry.getBattleByParticipatingPlayer(player) != null || mine >= Daycare.MAX_PER_PLAYER || boarders.size() >= Daycare.MAX_BOARDERS) {
			return false;
		}
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		int count = 0;
		boolean inParty = false;
		for (Pokemon member : party) {
			count++;
			inParty |= member == pokemon;
		}
		if (!inParty || count <= 1) {
			return false;
		}
		if (pokemon.getEntity() != null) {
			pokemon.recall();
		}
		if (!party.remove(pokemon)) {
			return false;
		}
		CompoundTag tag = pokemon.saveToNBT(player.registryAccess(), new CompoundTag());
		boarders.add(new Daycare.Boarder(player.getUUID(), player.getGameProfile().getName(), tag, player.level().getGameTime()));
		Daycare.setBoarders(rancher, boarders);
		Chat.chat(player, Component.translatable("message.aliveworkplace.daycare.left", pokemon.getDisplayName(false), rancher.getDisplayName())
			.withStyle(ChatFormatting.GREEN));
		rancher.level().playSound(null, rancher, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		return true;
	}

	/** {@code player} collects {@code boarder} (paying for what it gained); returns what to tell them. */
	public static Component collect(ServerPlayer player, Villager rancher, Daycare.Boarder boarder) {
		List<Daycare.Boarder> boarders = new ArrayList<>(Daycare.boarders(rancher));
		if (!boarders.contains(boarder) || !boarder.owner().equals(player.getUUID())) {
			return Component.empty();
		}
		Pokemon pokemon = load(Players.level(player), boarder);
		if (pokemon == null) {
			return Component.translatable("message.aliveworkplace.daycare.lost").withStyle(ChatFormatting.RED);
		}
		int gained = Daycare.experience(rancher, player.level().getGameTime() - boarder.since());
		int price = Daycare.price(levelAfter(pokemon, gained) - pokemon.getLevel());
		if (!Money.charge(player, (long) price * Money.DOLLARS_PER_EMERALD, price)) {
			return Component.translatable("message.aliveworkplace.daycare.cant_afford", Money.describe((long) price * Money.DOLLARS_PER_EMERALD, price))
				.withStyle(ChatFormatting.RED);
		}
		boarders.remove(boarder);
		Daycare.setBoarders(rancher, boarders);
		int before = pokemon.getLevel();
		if (gained > 0 && pokemon.canLevelUpFurther()) {
			pokemon.addExperienceWithPlayer(player, new SidemodExperienceSource(Daycare.XP_SOURCE), gained);
		}
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		if (!party.add(pokemon)) {
			Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
		}
		rancher.level().playSound(null, rancher, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1.1f);
		return Component.translatable("message.aliveworkplace.daycare.collected", pokemon.getDisplayName(false), before, pokemon.getLevel())
			.withStyle(ChatFormatting.GREEN);
	}

	/** The rancher is gone: every Pokémon in their care, with what it gained, goes to its trainer's PC. */
	public static void returnAll(ServerLevel level, Villager rancher) {
		for (Daycare.Boarder boarder : Daycare.boarders(rancher)) {
			Pokemon pokemon = load(level, boarder);
			if (pokemon == null) {
				continue;
			}
			int gained = Daycare.experience(rancher, level.getGameTime() - boarder.since());
			if (gained > 0 && pokemon.canLevelUpFurther()) {
				pokemon.addExperience(new SidemodExperienceSource(Daycare.XP_SOURCE), gained);
			}
			try {
				Cobblemon.INSTANCE.getStorage().getPC(boarder.owner(), level.registryAccess()).add(pokemon);
			} catch (RuntimeException e) {
				AliveWorkplace.LOG.error("Could not return {}'s Pokémon from the daycare", boarder.ownerName(), e);
			}
		}
		Daycare.setBoarders(rancher, List.of());
	}

	@Nullable
	static Pokemon load(ServerLevel level, Daycare.Boarder boarder) {
		try {
			return Pokemon.Companion.loadFromNBT(level.registryAccess(), boarder.pokemon().copy());
		} catch (RuntimeException e) {
			AliveWorkplace.LOG.error("A Pokémon in the daycare could not be read", e);
			return null;
		}
	}

	private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name));
		return stack;
	}

	private static Component plain(Component c) {
		return c.copy().withStyle(s -> s.withItalic(false));
	}

	private static ItemLore lore(@Nullable Component... lines) {
		List<Component> out = new ArrayList<>();
		for (Component line : lines) {
			if (line != null) {
				out.add(plain(line));
			}
		}
		return new ItemLore(out);
	}

	private CobblemonDaycare() {
	}
}
