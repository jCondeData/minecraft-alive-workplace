package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmEdicts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The realm's part of the Book of Edicts (ROADMAP 33.22): the third row's realm slots ({@link RealmEdicts#SLOTS}: in
 * force with its days and its reform, free, or locked with what opens it) and, at the capital, the realm edicts in the
 * list. At the capital its owner (or a friend, or an operator) proclaims with two clicks and lifts with one; a member's
 * book shows the same slots to read.
 */
final class RealmBook {
	private RealmBook() {
	}

	/** The realm's slots on the third row. */
	static void slots(ChoiceMenu menu, ServerLevel level, BlockPos hall, RealmData.Realm realm, boolean capital, long today, Runnable again) {
		MinecraftServer server = level.getServer();
		List<Edicts.InForce> inForce = RealmEdicts.inForce(server, realm);
		int open = RealmEdicts.slots(server, realm);
		for (int i = 0; i < RealmEdicts.SLOTS.length; i++) {
			int slot = RealmEdicts.SLOTS[i];
			if (i < inForce.size()) {
				Edicts.InForce f = inForce.get(i);
				menu.button(slot, inForceIcon(server, realm, f, today, capital), capital ? p -> lift(level, hall, p, f.id(), again) : null);
			} else if (i < open) {
				menu.button(slot, VillageHallScreen.icon(Items.MAP, Component.translatable("screen.aliveworkplace.realm_edict.free"), ChatFormatting.LIGHT_PURPLE,
					VillageHallScreen.line(capital ? "screen.aliveworkplace.realm_edict.free_hint" : "screen.aliveworkplace.realm_edict.free_member", ChatFormatting.GRAY)), null);
			} else {
				menu.button(slot, VillageHallScreen.icon(Items.GRAY_DYE, Component.translatable("screen.aliveworkplace.realm_edict.locked"), ChatFormatting.DARK_GRAY,
					VillageHallScreen.line("screen.aliveworkplace.realm_edict.locked_" + i, ChatFormatting.GRAY)), null);
			}
		}
	}

	/** A realm edict in the capital's list: click once to choose it, again to proclaim; one in force lifts with a click. */
	static void edict(ChoiceMenu menu, ServerLevel level, BlockPos hall, RealmData.Realm realm, Edicts.Edict edict, int slot, boolean armed, long today,
					  IntConsumer again) {
		MinecraftServer server = level.getServer();
		String id = edict.id().toString();
		Optional<Edicts.InForce> held = RealmEdicts.inForce(server, realm).stream().filter(f -> f.id().equals(id)).findFirst();
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(edict.description(), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.realm_edict.scope", ChatFormatting.LIGHT_PURPLE));
		effects(server, realm, edict, lore);
		if (held.isPresent()) {
			days(held.get(), today, lore, true);
		} else {
			lore.add(VillageHallScreen.line(armed ? "screen.aliveworkplace.edicts.click_again" : "screen.aliveworkplace.edicts.click_proclaim",
				armed ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
		}
		ItemStack icon = VillageHallScreen.icon(item(edict.icon()), edict.name().copy(), held.isPresent() ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE,
			lore.toArray(Component[]::new));
		if (held.isPresent() || armed) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		menu.button(slot, icon, p -> {
			if (held.isPresent()) {
				lift(level, hall, p, id, () -> again.accept(-1));
				return;
			}
			if (!Realms.mayRule(server, GlobalPos.of(level.dimension(), hall), p)) {
				Chat.chat(p, Component.translatable("message.aliveworkplace.realm.rule_not_yours", ownerName(level, hall), Realms.title(server, realm))
					.withStyle(ChatFormatting.RED));
				return;
			}
			if (!armed) {
				again.accept(slot);
				return;
			}
			Edicts.Result result = RealmEdicts.proclaim(level, hall, p, edict);
			if (!result.told().contains(p)) {
				Chat.chat(p, result.message());
			}
			again.accept(-1);
		});
	}

	private static String ownerName(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.ownerName() : "";
	}

	private static void lift(ServerLevel level, BlockPos hall, ServerPlayer p, String id, Runnable again) {
		Edicts.Result result = RealmEdicts.lift(level, hall, p, id);
		if (!result.told().contains(p)) {
			Chat.chat(p, result.message());
		}
		again.run();
	}

	/** A realm edict in its slot: its effects, its reform, since when and when it can be lifted (at the capital). */
	private static ItemStack inForceIcon(MinecraftServer server, RealmData.Realm realm, Edicts.InForce f, long today, boolean capital) {
		ResourceLocation id = ResourceLocation.tryParse(f.id());
		Optional<Edicts.Edict> edict = id == null ? Optional.empty() : Edicts.get(id);
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm_edict.in_force", Realms.title(server, realm)), ChatFormatting.LIGHT_PURPLE));
		edict.ifPresent(e -> effects(server, realm, e, lore));
		days(f, today, lore, capital);
		ItemStack icon = VillageHallScreen.icon(edict.map(e -> item(e.icon())).orElse(Items.PAPER), Edicts.name(f.id()).copy(), ChatFormatting.GOLD,
			lore.toArray(Component[]::new));
		icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return icon;
	}

	/**
	 * Its boost (green) and cost (red, gone once the realm reformed it), then its reform: the capital's quest step and
	 * the count across the realm, or "Reformed".
	 */
	private static void effects(MinecraftServer server, RealmData.Realm realm, Edicts.Edict edict, List<Component> lore) {
		String id = edict.id().toString();
		boolean reformed = edict.reform().isPresent() && RealmEdicts.reformed(server, realm, id);
		for (CivicEffects.Effect e : edict.boost()) {
			Component said = EdictBook.describe(e);
			if (said != null) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.boost", said), ChatFormatting.GREEN));
			}
		}
		for (CivicEffects.Effect e : reformed ? edict.reform().get().effects() : edict.cost()) {
			Component said = EdictBook.describe(e);
			if (said != null) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.cost", said), ChatFormatting.RED));
			}
		}
		edict.reform().ifPresent(reform -> {
			if (reformed) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.reformed", reform.name()), ChatFormatting.LIGHT_PURPLE));
				return;
			}
			int steps = reform.steps().size();
			int done = RealmEdicts.progress(server, realm, id).step();
			lore.add(VillageHallScreen.line(done >= steps ? Component.translatable("screen.aliveworkplace.realm_edict.reform_quest_done", reform.name())
				: Component.translatable("screen.aliveworkplace.edicts.reform", reform.name(), done + 1, steps), ChatFormatting.LIGHT_PURPLE));
			if (!reform.line().getString().isEmpty()) {
				lore.add(VillageHallScreen.line(reform.line(), ChatFormatting.DARK_PURPLE));
			}
			if (reform.count().isPresent()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm_edict.reform_count", RealmEdicts.countLine(server, realm, id)),
					ChatFormatting.DARK_PURPLE));
			}
		});
	}

	private static void days(Edicts.InForce f, long today, List<Component> lore, boolean liftable) {
		long days = Math.max(0, today - f.day());
		lore.add(VillageHallScreen.line(Words.counted("screen.aliveworkplace.edicts.since", (int) days, f.day(), days), ChatFormatting.AQUA));
		long from = f.day() + Edicts.MIN_DAYS;
		long left = from - today;
		if (left > 0) {
			lore.add(VillageHallScreen.line(Words.counted("screen.aliveworkplace.edicts.lift_in", (int) left, left, from), ChatFormatting.YELLOW));
		} else {
			lore.add(VillageHallScreen.line(liftable ? "screen.aliveworkplace.edicts.click_lift" : "screen.aliveworkplace.realm_edict.capital_lifts", ChatFormatting.DARK_GRAY));
		}
	}

	private static Item item(ResourceLocation id) {
		Item item = BuiltInRegistries.ITEM.get(id);
		return item == Items.AIR ? Items.PAPER : item;
	}
}
