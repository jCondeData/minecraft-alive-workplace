package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Village Hall's Defence page (ROADMAP 32.3), opened from the guards icon: what threatens the village and how the
 * last attacks went.
 * <ul>
 * <li>Second row ({@link #LAIR} to {@link #STOOD}): the lair by the village — whose camp it is, its captain by name,
 * its strength (the icon counts it; the tooltip says how many are at the camp, out raiding and lost in the last raid),
 * roughly where it is (a direction and the distance to the nearest ten blocks) and the days it has stood. With no lair
 * the five slots say "No camp near", and for how long none will come after one was broken up.</li>
 * <li>Bottom row ({@link #HISTORY}): the last three attacks, newest first — the day, who came, how many came and fell,
 * and whether the village fought them off or the rest got away.</li>
 * </ul>
 * Everyone who may open the hall may read it. Later items of Milestone 32 add the gates, the scouts, the war party, the
 * next attack and At peace to the rows between. The page is open while lairs are {@link Lairs#live}.
 */
public final class DefencePage {
	public static final int BACK = 0;
	public static final int HEADER = 4;
	/** The lair: whose camp. */
	public static final int LAIR = 10;
	public static final int CAPTAIN = 11;
	public static final int STRENGTH = 12;
	public static final int WHERE = 13;
	public static final int STOOD = 14;
	/** The last three attacks, newest first. */
	public static final int[] HISTORY = {46, 48, 50};

	/** Whether the hall's guards icon opens the page. */
	public static boolean open() {
		return Lairs.live();
	}

	/** The line on the hall's guards icon: whose lair is camped where, or that none is near. */
	static Component hallLine(ServerLevel level, BlockPos hall) {
		Optional<Lairs.Lair> lair = Lairs.near(level, hall);
		if (lair.isEmpty()) {
			return VillageHallScreen.line("screen.aliveworkplace.defence.no_lair", ChatFormatting.DARK_GRAY);
		}
		Component where = VillageHallScreen.where(hall, lair.get().pos());
		return VillageHallScreen.line(Lairs.named(lair.get())
			? Component.translatable("screen.aliveworkplace.hall.lair", Lairs.captainName(lair.get()), where)
			: Component.translatable("screen.aliveworkplace.hall.lair_unnamed", Lairs.lairName(lair.get().culture()), where), ChatFormatting.RED);
	}

	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		menu.clearButtons();
		menu.button(BACK, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> {
			VillageHallScreen.render(menu, level, hall, 0);
			menu.broadcastChanges();
		});
		int guards = VillageHalls.census(level, hall).guards();
		menu.button(HEADER, VillageHallScreen.icon(Items.IRON_SWORD, Component.translatable("screen.aliveworkplace.defence.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.about"), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.guards", guards), guards > 0 ? ChatFormatting.GRAY : ChatFormatting.YELLOW)), null);
		Optional<Lairs.Lair> standing = Lairs.near(level, hall);
		if (standing.isPresent()) {
			lair(menu, level, hall, standing.get());
		} else {
			int rest = Lairs.resting(level, hall);
			for (int slot = LAIR; slot <= STOOD; slot++) {
				menu.button(slot, VillageHallScreen.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, Component.translatable("screen.aliveworkplace.defence.no_lair"),
					ChatFormatting.GRAY, VillageHallScreen.line(rest > 0 ? Words.counted("screen.aliveworkplace.defence.resting", rest, rest)
						: Component.translatable("screen.aliveworkplace.defence.no_lair_hint"), ChatFormatting.DARK_GRAY)), null);
			}
		}
		List<ThreatData.Past> past = ThreatData.get(level).past(hall);
		for (int i = 0; i < Math.min(HISTORY.length, past.size()); i++) {
			menu.button(HISTORY[i], attack(past.get(i)), null);
		}
		if (past.isEmpty()) {
			menu.button(HISTORY[1], VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.defence.no_attacks"), ChatFormatting.GRAY,
				VillageHallScreen.line("screen.aliveworkplace.defence.no_attacks_hint", ChatFormatting.DARK_GRAY)), null);
		}
	}

	private static void lair(ChoiceMenu menu, ServerLevel level, BlockPos hall, Lairs.Lair lair) {
		Optional<Culture> culture = Threats.get(lair.culture());
		Optional<Culture.Lair> spec = culture.flatMap(Culture::lair);
		Component captain = Lairs.captainName(lair);
		menu.button(LAIR, VillageHallScreen.icon(icon(lair.culture()), Lairs.lairName(lair.culture()).copy(), ChatFormatting.RED,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.lair_of", Lairs.cultureName(lair.culture())), ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.defence.lair_raids", ChatFormatting.YELLOW),
			VillageHallScreen.line("screen.aliveworkplace.defence.lair_break", ChatFormatting.GREEN)), null);
		Item weapon = culture.flatMap(Culture::captain).map(c -> c.gear().get(EquipmentSlot.MAINHAND)).filter(id -> !id.equals(Culture.OMINOUS_BANNER))
			.flatMap(id -> BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))).orElse(Items.IRON_AXE);
		menu.button(CAPTAIN, VillageHallScreen.icon(weapon, captain.copy(), ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.captain", Lairs.cultureName(lair.culture())), ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.defence.captain_home", ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.defence.captain_falls", ChatFormatting.GREEN)), null);
		List<Component> strength = new ArrayList<>();
		strength.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.at_home", lair.home()), ChatFormatting.GRAY));
		strength.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.out", lair.out()), lair.out() > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
		strength.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.lost", lair.lost()), lair.lost() > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		spec.filter(s -> s.growth() > 0).ifPresent(s -> strength.add(VillageHallScreen.line(
			Component.translatable("screen.aliveworkplace.defence.grows", s.growth(), s.max()), ChatFormatting.YELLOW)));
		strength.add(VillageHallScreen.line("screen.aliveworkplace.defence.strength_hint", ChatFormatting.DARK_GRAY));
		menu.button(STRENGTH, VillageHallScreen.icon(new ItemStack(Items.CROSSBOW, Math.max(1, Math.min(64, lair.strength()))),
			Component.translatable("screen.aliveworkplace.defence.strength", lair.strength()), ChatFormatting.WHITE, strength.toArray(Component[]::new)), null);
		menu.button(WHERE, VillageHallScreen.icon(Items.MAP, roughly(hall, lair.pos()).copy(), ChatFormatting.WHITE,
			VillageHallScreen.line("screen.aliveworkplace.defence.where_hint", ChatFormatting.GRAY)), null);
		long days = Math.max(0, Chronicle.day(level) - lair.day());
		menu.button(STOOD, VillageHallScreen.icon(new ItemStack(Items.CLOCK, (int) Math.max(1, Math.min(64, days))),
			days == 0 ? Component.translatable("screen.aliveworkplace.defence.stood_today") : Words.counted("screen.aliveworkplace.defence.stood", days, days),
			ChatFormatting.WHITE, VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.stood_since", lair.day()), ChatFormatting.GRAY)), null);
	}

	/** One past attack: its culture's icon counting those who came, the day and who, how many fell and how it ended. */
	private static ItemStack attack(ThreatData.Past past) {
		return VillageHallScreen.icon(new ItemStack(icon(past.culture()), Math.max(1, Math.min(64, past.came()))),
			Component.translatable("screen.aliveworkplace.defence.attack", past.day(), Lairs.cultureName(past.culture())), ChatFormatting.WHITE,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.attack_count", past.came(), past.fell()), ChatFormatting.GRAY),
			past.fled() ? VillageHallScreen.line(Words.counted("screen.aliveworkplace.defence.attack_fled", past.came() - past.fell(), past.came() - past.fell()),
					ChatFormatting.YELLOW)
				: VillageHallScreen.line("screen.aliveworkplace.defence.attack_won", ChatFormatting.GREEN));
	}

	/** The item that stands for a culture: its lair's {@code icon}, or a zombie's head for raiders from nowhere. */
	private static Item icon(ResourceLocation culture) {
		return Threats.get(culture).flatMap(Culture::lair).flatMap(lair -> BuiltInRegistries.ITEM.getOptional(lair.icon())).orElse(Items.ZOMBIE_HEAD);
	}

	/** "north-east, about 90 blocks": the side of the hall and the distance to the nearest ten. */
	public static Component roughly(BlockPos hall, BlockPos pos) {
		double dx = pos.getX() - hall.getX();
		double dz = pos.getZ() - hall.getZ();
		long distance = Math.max(10, Math.round(Math.sqrt(dx * dx + dz * dz) / 10) * 10);
		// 0 = south, counting clockwise in eighths (Minecraft's +z is south), as VillageHallScreen.where.
		int eighth = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
		String[] directions = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		return Component.translatable("screen.aliveworkplace.defence.where", Component.translatable("screen.aliveworkplace.hall.dir." + directions[eighth]), distance);
	}

	private DefencePage() {
	}
}
