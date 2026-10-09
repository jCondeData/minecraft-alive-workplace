package io.github.jcondedata.aliveworkplace.story;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A villager's life story (ROADMAP 31.7): the page a shift-click on someone in the hall's list opens. It shows the
 * viewer's hearts with them, their name day, their family and partner, and the story so far: one line for every heart
 * event ({@link HeartEvents}) anyone was told, with who heard it. Other features add a section of their own with
 * {@link #section} (a villager's personal request, 31.9); a section with nothing to show now is left out.
 */
public final class LifeStory {
	/** Where the page's parts sit. */
	public static final int HEARTS = 19;
	public static final int NAME_DAY = 21;
	public static final int FAMILY = 23;
	public static final int PARTNER = 25;
	/** The row of the sections other features add, and the first slot of the story when there are none. */
	public static final int SECTIONS = 27;

	/** A part of the page another feature adds: its icon for this villager and viewer, or null when it has nothing to show. */
	public interface Section {
		@Nullable
		ItemStack icon(ServerLevel level, BlockPos hall, Villager villager, ServerPlayer viewer);
	}

	private static final List<Section> EXTRA = new CopyOnWriteArrayList<>();

	/** Adds a section to every life story page, after the ones already there. */
	public static void section(Section section) {
		EXTRA.add(section);
	}

	/** The first slot of the story's lines on a page with {@code sections} extra sections. */
	public static int storySlot(int sections) {
		return sections == 0 ? SECTIONS : SECTIONS + 9;
	}

	/** Lays out {@code villager}'s page for {@code viewer}; Back returns to the hall's list at {@code page}. */
	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, Villager villager, int page, ServerPlayer viewer) {
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE),
			p -> VillageHallScreen.showList(menu, level, hall, page));
		menu.button(4, VillageHallScreen.icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.life_story.title", villager.getDisplayName()),
			ChatFormatting.GOLD, VillageHallScreen.line("screen.aliveworkplace.life_story.about", ChatFormatting.GRAY)), null);
		menu.divider(1);
		if (Friendship.ENABLED) {
			menu.button(HEARTS, hearts(level, villager, viewer), null);
		}
		menu.button(NAME_DAY, VillageHallScreen.icon(Items.CAKE, Gifts.nameDayLine(villager, Chronicle.day(level)).copy(), ChatFormatting.LIGHT_PURPLE,
			VillageHallScreen.line("screen.aliveworkplace.life_story.name_day_hint", ChatFormatting.GRAY)), null);
		menu.button(FAMILY, family(level, villager), null);
		menu.button(PARTNER, partner(villager), null);
		List<ItemStack> sections = new ArrayList<>();
		for (Section section : EXTRA) {
			ItemStack icon = section.icon(level, hall, villager, viewer);
			if (icon != null && !icon.isEmpty()) {
				sections.add(icon);
			}
		}
		for (int i = 0; i < Math.min(9, sections.size()); i++) {
			menu.button(SECTIONS + i, sections.get(i), null);
		}
		int slot = storySlot(sections.size());
		Map<HeartEvents.Event, List<String>> told = HeartEvents.toldBy(villager);
		Object[] args = HeartEvents.args(level, villager, Component.literal(viewer.getGameProfile().getName()));
		for (Map.Entry<HeartEvents.Event, List<String>> e : told.entrySet()) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			List<Component> lore = new ArrayList<>();
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.life_story.at_hearts", e.getKey().hearts()), ChatFormatting.LIGHT_PURPLE));
			if (!e.getValue().isEmpty()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.life_story.told_to", String.join(", ", e.getValue())), ChatFormatting.GRAY));
			}
			menu.button(slot++, VillageHallScreen.icon(Items.BOOK, Component.translatable(e.getKey().story(), args), ChatFormatting.WHITE,
				lore.toArray(Component[]::new)), null);
		}
		if (told.isEmpty()) {
			menu.button(slot + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.life_story.empty"), ChatFormatting.GRAY,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.life_story.empty_hint", villager.getDisplayName()), ChatFormatting.DARK_GRAY)), null);
		}
	}

	/** "Your hearts: ♥♥♡…", with whether they have something to tell now, or at how many hearts they will. */
	private static ItemStack hearts(ServerLevel level, Villager villager, ServerPlayer viewer) {
		List<Component> lore = new ArrayList<>();
		if (HeartEvents.pending(level, villager, viewer.getUUID()) != null) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.life_story.to_tell", villager.getDisplayName()), ChatFormatting.GREEN));
		} else if (HeartEvents.ENABLED) {
			int next = HeartEvents.nextHearts(level, villager, viewer.getUUID());
			if (next > 0) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.life_story.next_hearts", next), ChatFormatting.GRAY));
			}
		}
		return VillageHallScreen.icon(Items.POPPY, Component.translatable("screen.aliveworkplace.hall.your_hearts",
			Friendship.heartRow(Friendship.points(villager, viewer.getUUID()))), ChatFormatting.LIGHT_PURPLE, lore.toArray(Component[]::new));
	}

	/** Their parents and the children of theirs living in the village. */
	private static ItemStack family(ServerLevel level, Villager villager) {
		List<Component> lore = new ArrayList<>();
		Families.Parents parents = Families.parents(villager);
		if (parents != null) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.child_of", parents.mother(), parents.father()), ChatFormatting.GRAY));
		}
		List<Component> children = HeartEvents.children(level, villager);
		if (!children.isEmpty()) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.life_story.children", HeartEvents.list(children, Component.empty())),
				ChatFormatting.GRAY));
		}
		if (lore.isEmpty()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.life_story.no_family", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(Items.OAK_SAPLING, Component.translatable("screen.aliveworkplace.life_story.family"), ChatFormatting.GREEN,
			lore.toArray(Component[]::new));
	}

	/** Who they're married to or courting, who they lost, or that they're with nobody. */
	private static ItemStack partner(Villager villager) {
		Couples.Partner partner = Couples.partner(villager);
		Couples.LatePartner late = ModAttachments.LATE_PARTNER.get(villager);
		List<Component> lore = new ArrayList<>();
		if (partner != null) {
			lore.add(VillageHallScreen.line(Component.translatable(partner.married() ? "screen.aliveworkplace.hall.married_to" : "screen.aliveworkplace.hall.courting",
				partner.name()), ChatFormatting.LIGHT_PURPLE));
		}
		if (late != null) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.life_story.widowed", late.name()), ChatFormatting.GRAY));
		}
		if (lore.isEmpty()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.life_story.single", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(Items.PINK_TULIP, Component.translatable("screen.aliveworkplace.life_story.partner"), ChatFormatting.LIGHT_PURPLE,
			lore.toArray(Component[]::new));
	}

	private LifeStory() {
	}
}
