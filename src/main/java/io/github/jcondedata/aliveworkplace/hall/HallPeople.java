package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * The People table (ROADMAP 40.2, {@code docs/design/M40.md}): the hall's People section shows its villagers as a table
 * of Name, Job, Level and Mood, under "8 · 6 working · 2 jobless", scrolled for big villages. The server still lays the
 * people out as the main page's list (a window of {@link VillageHallScreen#PER_PAGE} at a time); this marks each
 * person's button with the table's columns, keeps the counts and the window's start in the menu's numbers, and moves
 * the window when the table scrolls (a menu-button click of {@link #SCROLL} plus the first person to show).
 */
public final class HallPeople {
	/** The mark on a person's button: the table's columns. */
	public static final String MARK = "aliveworkplace:person";
	/** A menu-button click of SCROLL + n shows the people list from person n. */
	public static final int SCROLL = 1000;

	/** What a row is, as the table marks it. */
	public enum Kind {
		WORKER, BUILDER, GUARD, JOBLESS, NITWIT, CHILD;

		public String id() {
			return name().toLowerCase();
		}
	}

	/** The mood word a row shows. */
	public enum MoodWord {
		NONE, HAPPY, CONTENT, UNHAPPY, NO_BED;

		public String id() {
			return name().toLowerCase();
		}

		/** "Happy", "Content", "Unhappy", "No bed" (nothing for none). */
		public Component word() {
			return this == NONE ? Component.literal("-") : Component.translatable("screen.aliveworkplace.hall.people.mood." + id());
		}
	}

	/** The village's children, nearest the hall first (the census only counts them). */
	static List<Villager> children(ServerLevel level, BlockPos hall) {
		return VillageHalls.villagers(level, hall).stream().filter(v -> v.isBaby() && !CaravanSights.isParty(v))
			.sorted(Comparator.comparingDouble(v -> v.distanceToSqr(hall.getCenter()))).toList();
	}

	/** Sets the table's numbers on {@code menu}: everyone listed, the workers, the jobless, the children and the window's start. */
	static void counts(ChoiceMenu menu, VillageHalls.Census census, int listed, int first) {
		menu.setState(HallSections.PEOPLE_TOTAL, Math.min(Short.MAX_VALUE, listed));
		menu.setState(HallSections.PEOPLE_WORKING, Math.min(Short.MAX_VALUE, census.workers().size()));
		menu.setState(HallSections.PEOPLE_JOBLESS, Math.min(Short.MAX_VALUE, census.jobless().size()));
		menu.setState(HallSections.PEOPLE_CHILDREN, Math.min(Short.MAX_VALUE, census.children()));
		menu.setState(HallSections.PEOPLE_FIRST, Math.min(Short.MAX_VALUE, first));
	}

	public static Kind kind(Villager villager) {
		if (villager.isBaby()) {
			return Kind.CHILD;
		}
		VillagerProfession job = villager.getVillagerData().getProfession();
		if (job == VillagerProfession.NITWIT) {
			return Kind.NITWIT;
		}
		if (job == VillagerProfession.NONE || villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
			return Kind.JOBLESS;
		}
		if (Builders.isBuilder(villager)) {
			return Kind.BUILDER;
		}
		return job == io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD ? Kind.GUARD : Kind.WORKER;
	}

	/** The word for {@code villager}'s mood: "No bed" when they have none and aren't happy, since that's the reason. */
	public static MoodWord mood(ServerLevel level, Villager villager) {
		Moods.Mood mood = Moods.of(villager);
		if (mood == null) {
			return MoodWord.NONE;
		}
		if (mood.score() < Moods.HAPPY && VillageNeeds.bed(level, villager) == null) {
			return MoodWord.NO_BED;
		}
		return mood.score() >= Moods.HAPPY ? MoodWord.HAPPY : mood.score() < Moods.UNHAPPY ? MoodWord.UNHAPPY : MoodWord.CONTENT;
	}

	/** What Job ▾ does for a row: "retrain" (a worker's Change job), "assign" (a jobless villager's free jobs) or "" (neither). */
	static String jobAction(ServerLevel level, Villager villager, Kind kind) {
		if (kind == Kind.JOBLESS && io.github.jcondedata.aliveworkplace.legend.Legends.of(villager).isEmpty()) {
			return "assign";
		}
		return kind != Kind.CHILD && kind != Kind.NITWIT && kind != Kind.JOBLESS && HallRetraining.canRetrain(level, villager) ? "retrain" : "";
	}

	/** Marks {@code icon}, a person's button, with the table's columns. */
	static void tag(ItemStack icon, ServerLevel level, Villager villager) {
		Kind kind = kind(villager);
		CompoundTag row = new CompoundTag();
		row.putString("uuid", villager.getStringUUID());
		row.putString("kind", kind.id());
		row.putString("job", switch (kind) {
			case CHILD -> "screen.aliveworkplace.hall.people.child";
			case JOBLESS -> "screen.aliveworkplace.hall.people.no_job";
			default -> "entity.minecraft.villager." + villager.getVillagerData().getProfession().name();
		});
		row.putInt("level", kind == Kind.CHILD || kind == Kind.JOBLESS || kind == Kind.NITWIT ? 0 : BuilderLevels.level(villager));
		row.putString("mood", mood(level, villager).id());
		row.putString("job_action", jobAction(level, villager, kind));
		row.putBoolean("named", villager.hasCustomName());
		CompoundTag tag = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.put(MARK, row);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** The table's columns on a person's button (null: not a person). */
	@Nullable
	public static CompoundTag row(ItemStack icon) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(MARK)) {
			return null;
		}
		return data.copyTag().getCompound(MARK);
	}

	/** The line over the table: "8 · 6 working · 2 jobless" (and the children when there are some). */
	public static Component header(int total, int working, int jobless, int children) {
		return children == 1 ? Component.translatable("screen.aliveworkplace.hall.people.header_child", total, working, jobless) // "1 child" (B547)
			: children > 0 ? Component.translatable("screen.aliveworkplace.hall.people.header_children", total, working, jobless, children)
			: Component.translatable("screen.aliveworkplace.hall.people.header", total, working, jobless);
	}

	/** The Level column: "Novice" ... "Master", or a dash for someone without a job. */
	public static Component level(int level) {
		return level <= 0 ? Component.literal("-") : BuilderLevels.levelName(level);
	}

	/** Whether {@code villager}, listed when the screen was laid out, has since died or left the village. */
	static boolean gone(ServerLevel level, BlockPos hall, Villager villager) {
		return !villager.isAlive() || villager.isRemoved() || villager.level() != level || !VillageHalls.contains(hall, villager.blockPosition());
	}

	/** Shows the people list from person {@code first} (the table's scroll). */
	static void scrollTo(ChoiceMenu menu, ServerLevel level, BlockPos hall, int first) {
		VillageHallScreen.renderFrom(menu, level, hall, Math.max(0, first));
		menu.broadcastChanges();
	}

	private HallPeople() {
	}
}
