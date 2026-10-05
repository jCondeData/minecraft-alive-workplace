package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The hall's Legends page (29.4), a nether star in the page row: the village's own Legends first (settled ones from
 * {@link LegendRecord}, guests standing in the village), then every other Legend in the roster as a card with its
 * rarity, how it comes, each condition with the village's progress, the luxury it likes, its powers, "lives in Thornholm"
 * for a Legendary already taken and the Mythic line. Terraria-style: what a village must do is never a secret.
 */
public final class LegendsPage {
	public static final String PAGE = "legends";
	/** "What next?" names at most this many Legends that lack one condition. */
	static final int MAX_TIPS = 2;

	static void init() {
		HallPages.register(PAGE, LegendsPage::tab, LegendsPage::header, (menu, level, hall, viewer) -> fill(menu, level, hall));
	}

	/** The page row's tab: how many Legends live here. */
	static ItemStack tab(ServerLevel level, BlockPos hall) {
		int here = Legends.ENABLED ? own(level, hall).size() : 0;
		return VillageHallScreen.icon(Items.NETHER_STAR, Component.translatable("screen.aliveworkplace.legends.tab"), ChatFormatting.GOLD,
			VillageHallScreen.line(Legends.ENABLED ? Component.translatable("screen.aliveworkplace.legends.here", here)
				: Component.translatable("message.aliveworkplace.legend.off"), here > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.legends.hint", ChatFormatting.DARK_GRAY));
	}

	/** The page's header: the village's name, what the page is, and the Mythic line. */
	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.NETHER_STAR, Component.translatable("screen.aliveworkplace.legends.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD,
			VillageHallScreen.line("screen.aliveworkplace.legends.about", ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.legends.here", Legends.ENABLED ? own(level, hall).size() : 0),
				ChatFormatting.GRAY),
			mythicLine(level, hall));
	}

	/** "Mythic Legends: 0 of 1, as a Town". */
	public static Component mythicLine(ServerLevel level, BlockPos hall) {
		VillageRanks.Rank rank = VillageRanks.of(level, hall);
		String dim = level.dimension().location().toString();
		long have = LegendRecord.get(level).holding(e -> e.rarity() == Rarity.MYTHIC && e.in(dim, hall));
		return Component.translatable("screen.aliveworkplace.legends.mythic", have, LegendSlots.mythicCap(rank), rank.title())
			.withStyle(Rarity.MYTHIC.color);
	}

	/** The page's rows: a card per Legend, the village's own first. */
	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		int slot = VillageHallScreen.FIRST_ROW;
		for (ItemStack card : cards(level, hall)) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			menu.button(slot++, card, null);
		}
	}

	/** Every card the page shows, in order (tests read them). */
	public static List<ItemStack> cards(ServerLevel level, BlockPos hall) {
		List<ItemStack> out = new ArrayList<>();
		if (!Legends.ENABLED) {
			out.add(VillageHallScreen.icon(Items.PAPER, Component.translatable("message.aliveworkplace.legend.off"), ChatFormatting.GRAY));
			return out;
		}
		List<Own> own = own(level, hall);
		for (Own o : own) {
			out.add(ownCard(level, o));
		}
		Set<ResourceLocation> here = new HashSet<>();
		own.forEach(o -> here.add(o.legend().id()));
		for (Legend legend : Legends.all()) {
			if (legend.rarity() != Rarity.MYTHIC && here.contains(legend.id())) {
				continue; // a Rare or Legendary one living here has their own card above
			}
			out.add(card(level, hall, legend));
		}
		if (out.isEmpty()) {
			out.add(VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.legends.none"), ChatFormatting.GRAY));
		}
		return out;
	}

	/** A Legend of this village: on the record (alive, a zombie or in a grave), or a guest standing here. */
	record Own(Legend legend, Component name, Optional<LegendRecord.Entry> entry, Optional<Villager> villager) {
	}

	static List<Own> own(ServerLevel level, BlockPos hall) {
		List<Own> out = new ArrayList<>();
		Set<java.util.UUID> seen = new HashSet<>();
		String dim = level.dimension().location().toString();
		for (LegendRecord.Entry e : LegendRecord.get(level).entries()) {
			if (!e.holds() || !e.in(dim, hall)) {
				continue;
			}
			Optional<Legend> legend = Legends.get(e.id());
			if (legend.isEmpty()) {
				continue;
			}
			Entity entity = level.getEntity(e.villager());
			Optional<Villager> villager = entity instanceof Villager v && v.isAlive() ? Optional.of(v) : Optional.empty();
			Component name = villager.<Component>map(Villager::getDisplayName).orElse(Component.literal(e.name()));
			out.add(new Own(legend.get(), name, Optional.of(e), villager));
			seen.add(e.villager());
		}
		for (Villager v : villagers(level, hall)) {
			LegendData data = ModAttachments.LEGEND.get(v);
			if (data != null && data.guest() && !seen.contains(v.getUUID())) {
				Legends.get(data.id()).ifPresent(l -> out.add(new Own(l, v.getDisplayName(), Optional.empty(), Optional.of(v))));
			}
		}
		return out;
	}

	/** The Legend villagers standing in the village round {@code hall} (settled here, or guests), while Legends are on. */
	public static List<Villager> villagers(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED) {
			return List.of();
		}
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> {
			LegendData data = ModAttachments.LEGEND.get(v);
			return v.isAlive() && data != null && Legends.get(data.id()).isPresent()
				&& (data.hall().isEmpty() || data.hall().get().equals(hall));
		});
	}

	static ItemStack ownCard(ServerLevel level, Own o) {
		List<Component> lore = new ArrayList<>();
		lore.add(LegendText.rarityLine(o.legend()));
		LegendData data = o.villager().map(ModAttachments.LEGEND::get).orElse(null);
		if (o.entry().isPresent()) {
			LegendRecord.Entry e = o.entry().get();
			lore.add(Component.translatable("screen.aliveworkplace.legends.since", e.settled()).withStyle(ChatFormatting.GRAY));
			if (e.zombie()) {
				lore.add(Component.translatable("screen.aliveworkplace.legends.zombie").withStyle(ChatFormatting.RED));
			} else if (e.grave().isPresent()) {
				lore.add(Component.translatable("screen.aliveworkplace.legends.grave").withStyle(ChatFormatting.YELLOW));
			} else if (e.gone() >= 0) {
				long left = Math.max(0, LegendRecord.FALL_DAYS - (io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level) - e.gone()));
				lore.add(Component.translatable("screen.aliveworkplace.legends.dead", left).withStyle(ChatFormatting.RED));
			}
		} else if (data != null && data.guest()) {
			lore.add((data.lastDay() >= 0 ? Component.translatable("legend.aliveworkplace.guest_until", data.lastDay())
				: Component.translatable("legend.aliveworkplace.guest")).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		lore.addAll(LegendText.powerLines(o.legend(), data != null && data.onStrike()));
		if (data != null && data.settled()) {
			lore.addAll(LegendText.needLines(o.legend(), data));
			lore.addAll(LegendText.strikeLines(o.legend(), data));
		}
		ItemStack icon = VillageHallScreen.icon(Items.NETHER_STAR, LegendText.name(o.name(), o.legend()), ChatFormatting.GOLD, lore.toArray(Component[]::new));
		icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return icon;
	}

	/** A Legend who doesn't live here: what they are, how they come, what the village must have and how far it has got. */
	static ItemStack card(ServerLevel level, BlockPos hall, Legend legend) {
		List<Component> lore = new ArrayList<>();
		lore.add(LegendText.rarityLine(legend));
		lore.add(Component.translatable("screen.aliveworkplace.legends.how").withStyle(ChatFormatting.GRAY));
		for (Component way : LegendText.wayLines(legend)) {
			lore.add(Component.translatable("screen.aliveworkplace.legends.item", way).withStyle(ChatFormatting.WHITE));
		}
		lore.add(Component.translatable("screen.aliveworkplace.legends.needs").withStyle(ChatFormatting.GRAY));
		boolean all = true;
		for (Condition c : legend.conditions()) {
			Condition.Progress p = c.progress(level, hall);
			all &= p.met();
			lore.add(LegendText.tick(p.met(), p.line()));
		}
		if (legend.conditions().isEmpty()) {
			lore.add(Component.translatable("screen.aliveworkplace.legends.needs_nothing").withStyle(ChatFormatting.GREEN));
		}
		legend.luxury().ifPresent(l -> lore.add(Component.translatable("screen.aliveworkplace.legends.likes", LegendText.luxury(l))
			.withStyle(ChatFormatting.DARK_AQUA)));
		if (!legend.powers().isEmpty()) {
			lore.add(Component.translatable("screen.aliveworkplace.legends.powers").withStyle(ChatFormatting.GRAY));
			lore.addAll(LegendText.powerLines(legend, false));
		}
		Optional<Component> livesIn = legend.rarity() == Rarity.LEGENDARY ? livesIn(level, legend) : Optional.empty();
		livesIn.ifPresent(lore::add);
		if (legend.rarity() == Rarity.MYTHIC) {
			lore.add(mythicLine(level, hall));
		}
		boolean free = LegendSlots.whyNot(level, hall, legend, null).isEmpty();
		if (all && free) {
			lore.add(Component.translatable("screen.aliveworkplace.legends.ready").withStyle(ChatFormatting.GREEN));
		}
		Item item = livesIn.isPresent() ? Items.GRAY_DYE : switch (legend.rarity()) {
			case RARE -> Items.AMETHYST_SHARD;
			case LEGENDARY -> Items.GOLD_INGOT;
			case MYTHIC -> Items.END_CRYSTAL;
		};
		return VillageHallScreen.icon(item, legend.titleText().copy(), livesIn.isPresent() ? ChatFormatting.GRAY : legend.rarity().color,
			lore.toArray(Component[]::new));
	}

	/** "Lives in Thornholm" for a Legendary Legend already holding their one place in the world (any dimension). */
	public static Optional<Component> livesIn(ServerLevel level, Legend legend) {
		for (LegendRecord.Entry e : LegendRecord.get(level).entries()) {
			if (!e.holds() || !e.id().equals(legend.id())) {
				continue;
			}
			Component village = Component.translatable("message.aliveworkplace.legend.the_wilds");
			ResourceLocation dim = ResourceLocation.tryParse(e.dimension());
			ServerLevel there = dim == null ? null : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
			if (e.hall().isPresent() && there != null) {
				village = VillageHalls.name(there, e.hall().get());
			}
			return Optional.of(Component.translatable("screen.aliveworkplace.legends.lives_in", Component.literal(e.name()), village)
				.withStyle(ChatFormatting.RED));
		}
		return Optional.empty();
	}

	/**
	 * "What next?" (29.4): a tip for each Legend (up to {@link #MAX_TIPS}) whose place is free and who lacks only one of
	 * their conditions, naming it with the village's progress.
	 */
	public static List<VillageAdvice.Tip> tips(ServerLevel level, BlockPos hall) {
		List<VillageAdvice.Tip> out = new ArrayList<>();
		for (Legend legend : Legends.all()) {
			if (out.size() >= MAX_TIPS) {
				break;
			}
			if (legend.conditions().isEmpty() || LegendSlots.whyNot(level, hall, legend, null).isPresent()) {
				continue;
			}
			Condition.Progress missing = null;
			int unmet = 0;
			for (Condition c : legend.conditions()) {
				Condition.Progress p = c.progress(level, hall);
				if (!p.met()) {
					unmet++;
					missing = p;
				}
			}
			if (unmet == 1) {
				out.add(new VillageAdvice.Tip("legend", Items.NETHER_STAR, legend.titleText(), missing.line()));
			}
		}
		return out;
	}

	private LegendsPage() {
	}
}
