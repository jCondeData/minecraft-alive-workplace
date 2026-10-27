package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Job ▾ in the hall's People table (ROADMAP 40.3): a list of every job under the villager's row. A job with a free work
 * block in the village says how many ("Mason · 1 free Stonecutter"), one without is grayed ("Mason · place a Stonecutter
 * first"), a job above their class says the class it needs, and "No job" is last. It is today's retraining
 * ({@link HallRetraining}, 36.18): the price by their level is on top before anything is paid, a first click on a job
 * shows the offer and a second pays; the old job's trades and level go, and a builder hands his builds back. A jobless
 * villager is given a free block for nothing, in one click, as the free jobs page does. Children, nitwits, Legends and
 * caravan carters can't; game rule {@code workplaceRetraining} off hides the button.
 * <p>
 * No new packet: the client sends menu-button clicks ({@link #OPEN} + the person's slot, {@link #PICK} + the job's
 * registry number, {@link #CLOSE}), and the list goes to the client inside the open person's button (a container of one
 * stack per job, each with its line, its tooltip and a mark). Nothing is saved: which list is open, and the job clicked
 * once, live with the open screen.
 */
public final class HallJobs {
	/** A menu-button click of OPEN + slot opens the Job list of the person in that slot. */
	public static final int OPEN = 100_000;
	/** A menu-button click of CLOSE closes the open list. */
	public static final int CLOSE = 199_999;
	/** A menu-button click of PICK + n picks the job whose registry number is n ("No job" is {@link VillagerProfession#NONE}'s). */
	public static final int PICK = 200_000;
	/** The mark on each line of the list. */
	public static final String MARK = "aliveworkplace:job_option";

	/** What a line of the list is. */
	public enum State {
		/** The lines over the list: who, and the price and what's lost. */
		HEADER,
		/** A job with a free block: can be picked. */
		FREE,
		/** The job clicked once: a second click pays. */
		PICKED,
		/** No free block for it in the village. */
		NO_BLOCK,
		/** A job above their class (34.8). */
		CLASS,
		/** The job they have now. */
		CURRENT,
		/** No job. */
		NO_JOB,
		/** No job, clicked once. */
		NO_JOB_PICKED;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public static State of(String id) {
			try {
				return valueOf(id.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return NO_BLOCK;
			}
		}

		/** Whether a click on the line does something (picks, or pays). */
		public boolean pickable() {
			return this == FREE || this == PICKED || this == NO_JOB || this == NO_JOB_PICKED;
		}
	}

	/** A job on the list: its block, the village's free blocks of it (nearest the hall first), and why it can't be had. */
	public record Option(VillagerProfession job, Block block, List<VillageHalls.FreeStation> free, @Nullable Component refusal, boolean current) {
	}

	/** The list open on a hall screen: whose, and the job clicked once (null: none yet). */
	private record Open(UUID villager, @Nullable VillagerProfession picked) {
	}

	private static final Map<ChoiceMenu, Open> OPEN_LISTS = new WeakHashMap<>();

	/** Whether Job ▾ is shown at all: game rule {@code workplaceRetraining} on. */
	public static boolean shown(ServerLevel level) {
		return Retraining.on(level);
	}

	/** Whether {@code villager}'s job can be changed from the table: a jobless grown villager, or one {@link HallRetraining} can retrain. */
	public static boolean canChange(ServerLevel level, Villager villager) {
		return shown(level) && !HallPeople.jobAction(level, villager, HallPeople.kind(villager)).isEmpty();
	}

	/** Whether {@code villager} has a job to lose (a change is a paid retraining), not a jobless villager given their first. */
	static boolean retraining(ServerLevel level, Villager villager) {
		return HallRetraining.canRetrain(level, villager);
	}

	/** Whether {@code job} goes on the list: a job a villager takes at a work block. The Steward is named at the hall itself. */
	static boolean listed(VillagerProfession job) {
		return job != VillagerProfession.NONE && job != VillagerProfession.NITWIT && job != ModVillagers.LEGEND && job != ModVillagers.STEWARD
			&& job != ModVillagers.TRAINER_LEADER && Stations.available(job);
	}

	/** The block {@code job} works at, as the list names it (null: none, a job nobody takes). */
	@Nullable
	static Block block(VillagerProfession job) {
		if (job == ModVillagers.BUILDER) {
			return ModBlocks.BLUEPRINT_TABLE;
		}
		Block shared = Stations.of(job).map(Stations.Station::block).orElse(Blocks.AIR);
		if (shared != Blocks.AIR) {
			return shared;
		}
		for (Holder.Reference<PoiType> poi : BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders().toList()) {
			if (job.acquirableJobSite().test(poi) && !poi.value().matchingStates().isEmpty()) {
				return poi.value().matchingStates().stream().map(s -> s.getBlock())
					.min(Comparator.comparingInt(b -> BuiltInRegistries.BLOCK.getId(b))).orElse(null);
			}
		}
		return null;
	}

	/** Every job on {@code villager}'s list, those with a free block first, then the rest, each in the registry's order. */
	public static List<Option> options(ServerLevel level, BlockPos hall, Villager villager) {
		Map<VillagerProfession, List<VillageHalls.FreeStation>> free = new LinkedHashMap<>();
		for (VillageHalls.FreeStation station : VillageHalls.freeStations(level, hall)) {
			for (VillagerProfession job : VillageHalls.jobs(station)) {
				free.computeIfAbsent(job, j -> new ArrayList<>()).add(new VillageHalls.FreeStation(station.pos(), job, station.poi()));
			}
		}
		VillagerProfession now = villager.getVillagerData().getProfession();
		List<Option> open = new ArrayList<>();
		List<Option> rest = new ArrayList<>();
		for (VillagerProfession job : BuiltInRegistries.VILLAGER_PROFESSION) {
			Block block = listed(job) ? block(job) : null;
			if (block == null || block == Blocks.AIR) {
				continue;
			}
			List<VillageHalls.FreeStation> blocks = free.getOrDefault(job, List.of());
			Option option = new Option(job, block, blocks, io.github.jcondedata.aliveworkplace.people.ClassJobs.refusal(hall, villager, job), job == now);
			(option.current() || blocks.isEmpty() || option.refusal() != null ? rest : open).add(option);
		}
		open.addAll(rest);
		return open;
	}

	/** Whether the list is open for {@code villager} on {@code menu}. */
	public static boolean isOpen(ChoiceMenu menu, Villager villager) {
		Open open = OPEN_LISTS.get(menu);
		return open != null && open.villager().equals(villager.getUUID());
	}

	/** The job clicked once on {@code menu}'s open list (null: none). */
	@Nullable
	public static VillagerProfession picked(ChoiceMenu menu) {
		Open open = OPEN_LISTS.get(menu);
		return open == null ? null : open.picked();
	}

	/**
	 * Puts the list on {@code icon}, {@code villager}'s button, when it's open for them (called as the main page is laid
	 * out), and marks whether Job ▾ is shown.
	 */
	static void attach(ChoiceMenu menu, ItemStack icon, ServerLevel level, BlockPos hall, Villager villager) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(HallPeople.MARK)) {
			return;
		}
		CompoundTag tag = data.copyTag();
		CompoundTag row = tag.getCompound(HallPeople.MARK);
		row.putBoolean("job_hidden", !shown(level));
		boolean open = isOpen(menu, villager) && canChange(level, villager);
		row.putBoolean("jobs_open", open);
		tag.put(HallPeople.MARK, row);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		if (open) {
			icon.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(lines(level, hall, villager, picked(menu))));
		}
	}

	/** The list's lines as stacks: the header, a line per job, then No job (for one who has a job). */
	public static List<ItemStack> lines(ServerLevel level, BlockPos hall, Villager villager, @Nullable VillagerProfession picked) {
		List<ItemStack> out = new ArrayList<>();
		Component who = villager.getDisplayName();
		boolean paid = retraining(level, villager);
		Component price = HallRetraining.price(villager);
		List<Component> header = new ArrayList<>();
		if (paid) {
			header.add(Component.translatable("screen.aliveworkplace.hall_retrain.price", price, Retraining.levelName(villager)).withStyle(ChatFormatting.GOLD));
			header.add(Component.translatable("screen.aliveworkplace.hall_retrain.lost").withStyle(ChatFormatting.GRAY));
		} else {
			header.add(Component.translatable("screen.aliveworkplace.hall.jobs.free_for_jobless").withStyle(ChatFormatting.GRAY));
		}
		out.add(line(Items.PAPER, Component.translatable("screen.aliveworkplace.hall.jobs.title", who), State.HEADER, -1, header));
		for (Option option : options(level, hall, villager)) {
			Component job = Stations.name(option.job());
			Component blockName = option.block().getName();
			Item item = option.block().asItem() == Items.AIR ? Items.PAPER : option.block().asItem();
			int id = BuiltInRegistries.VILLAGER_PROFESSION.getId(option.job());
			List<Component> tip = new ArrayList<>();
			if (option.current()) {
				out.add(line(item, Component.translatable("screen.aliveworkplace.hall.jobs.current", job), State.CURRENT, id, tip));
			} else if (option.free().isEmpty()) {
				tip.add(Component.translatable("screen.aliveworkplace.hall.jobs.none_hint", blockName).withStyle(ChatFormatting.GRAY));
				out.add(line(item, Words.articled("screen.aliveworkplace.hall.jobs.none", 1, job, blockName), State.NO_BLOCK, id, tip));
			} else if (option.refusal() != null) {
				var need = io.github.jcondedata.aliveworkplace.people.ClassJobs.needed(option.job());
				tip.add(option.refusal().copy().withStyle(ChatFormatting.RED));
				out.add(line(item, Component.translatable("screen.aliveworkplace.hall.jobs.class", job, need == null ? Component.empty() : need.name()),
					State.CLASS, id, tip));
			} else {
				int n = option.free().size();
				Component label = Component.translatable("screen.aliveworkplace.hall.jobs.free", job, n, Words.itemName(item, n));
				tip.add(Component.translatable("screen.aliveworkplace.hall.jobs.nearest", VillageHallScreen.where(hall, option.free().get(0).pos()))
					.withStyle(ChatFormatting.GRAY));
				boolean chosen = option.job() == picked;
				if (paid) {
					tip.add(Component.translatable("screen.aliveworkplace.hall_retrain.price", price, Retraining.levelName(villager)).withStyle(ChatFormatting.GOLD));
					tip.add(Component.translatable("screen.aliveworkplace.hall_retrain.lost").withStyle(ChatFormatting.GRAY));
					tip.add((chosen ? Words.articled("screen.aliveworkplace.hall_retrain.pay", 2, price, who, job)
						: Words.articled("screen.aliveworkplace.hall_retrain.pick", 1, who, job)).withStyle(ChatFormatting.GREEN));
				} else {
					tip.add(Words.articled("screen.aliveworkplace.hall.give_job", 1, who, job).withStyle(ChatFormatting.GREEN));
				}
				out.add(line(item, label, chosen ? State.PICKED : State.FREE, id, tip));
			}
		}
		if (paid) {
			boolean chosen = picked == VillagerProfession.NONE;
			List<Component> tip = new ArrayList<>();
			tip.add(Component.translatable("screen.aliveworkplace.hall.jobs.no_job_about").withStyle(ChatFormatting.GRAY));
			tip.add(Words.articled(chosen ? "screen.aliveworkplace.hall.jobs.no_job_pay" : "screen.aliveworkplace.hall.jobs.no_job_pick", 1, who,
				Stations.name(villager.getVillagerData().getProfession())).withStyle(ChatFormatting.GREEN));
			out.add(line(Items.BARRIER, Component.translatable("screen.aliveworkplace.hall.jobs.no_job"), chosen ? State.NO_JOB_PICKED : State.NO_JOB,
				BuiltInRegistries.VILLAGER_PROFESSION.getId(VillagerProfession.NONE), tip));
		}
		return out;
	}

	private static ItemStack line(Item item, Component label, State state, int id, List<Component> tip) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, label.copy().withStyle(s -> s.withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(tip.stream().map(c -> (Component) c.copy().withStyle(s -> s.withItalic(false))).toList()));
		CompoundTag mark = new CompoundTag();
		mark.putString("state", state.id());
		mark.putInt("id", id);
		CompoundTag tag = new CompoundTag();
		tag.put(MARK, mark);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** The list's lines on a person's button (empty: the list isn't open for them). */
	public static List<ItemStack> lines(ItemStack personIcon) {
		ItemContainerContents contents = personIcon.get(DataComponents.CONTAINER);
		return contents == null ? List.of() : contents.nonEmptyStream().toList();
	}

	/** A line's mark: its state and the job's registry number. */
	@Nullable
	public static CompoundTag mark(ItemStack line) {
		CustomData data = line.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(MARK) ? null : data.copyTag().getCompound(MARK);
	}

	// --- the clicks -------------------------------------------------------------------------------------------------

	/** Whether {@code id} is one of the list's menu-button clicks. */
	public static boolean handles(int id) {
		return id >= OPEN && id < PICK + 100_000;
	}

	/** A menu-button click on the hall's screen ({@link #handles}): opens, closes or picks from the list. */
	static boolean click(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer player, int id) {
		if (id == CLOSE) {
			OPEN_LISTS.remove(menu);
			relayout(menu, level, hall);
			return true;
		}
		if (id < CLOSE) {
			int slot = id - OPEN;
			Villager villager = slot >= 0 && slot < ChoiceMenu.SIZE ? personIn(menu, level, slot) : null;
			if (villager == null || HallPeople.gone(level, hall, villager)) {
				OPEN_LISTS.remove(menu);
			} else if (!canChange(level, villager)) {
				OPEN_LISTS.remove(menu);
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.jobs.cant", villager.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			} else if (isOpen(menu, villager)) {
				OPEN_LISTS.remove(menu); // Job ▾ again closes it
			} else {
				HallBeds.close(menu); // one list at a time (40.4)
				OPEN_LISTS.put(menu, new Open(villager.getUUID(), null));
			}
			relayout(menu, level, hall);
			return true;
		}
		Open open = OPEN_LISTS.get(menu);
		Villager villager = open == null ? null : level.getEntity(open.villager()) instanceof Villager v ? v : null;
		if (villager == null || HallPeople.gone(level, hall, villager) || !canChange(level, villager)) {
			OPEN_LISTS.remove(menu);
			relayout(menu, level, hall);
			return true;
		}
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.byId(id - PICK);
		if (job == null) {
			return true;
		}
		if (job == VillagerProfession.NONE) {
			noJob(menu, level, hall, player, villager, open);
			return true;
		}
		Option option = options(level, hall, villager).stream().filter(o -> o.job() == job).findFirst().orElse(null);
		if (option == null || option.current()) {
			return true;
		}
		if (option.free().isEmpty()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.jobs.no_block", Stations.name(job), option.block().getName())
				.withStyle(ChatFormatting.YELLOW));
			no(level, player);
			return true;
		}
		if (option.refusal() != null) {
			Chat.actionBar(player, option.refusal().copy().withStyle(ChatFormatting.YELLOW));
			no(level, player);
			return true;
		}
		VillageHalls.FreeStation station = option.free().get(0);
		if (!retraining(level, villager)) {
			// A jobless villager: their first job, for nothing, as the free jobs page gives it (36.9).
			if (VillageHalls.assign(level, villager, station)) {
				Chat.actionBar(player, Words.articled("message.aliveworkplace.hall.assigned", 1, villager.getDisplayName(), Stations.name(job))
					.withStyle(ChatFormatting.GREEN));
				level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
				OPEN_LISTS.remove(menu);
			} else {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.not_assigned").withStyle(ChatFormatting.YELLOW));
				no(level, player);
			}
			relayout(menu, level, hall);
			return true;
		}
		if (open.picked() != job) {
			// The first click: the offer and its price, nothing paid (as the Change job page's first click, 36.18).
			Chat.chat(player, Words.articled("message.aliveworkplace.retrain.offer", 3, villager.getDisplayName(),
				Stations.name(villager.getVillagerData().getProfession()), Retraining.levelName(villager), Stations.name(job), HallRetraining.price(villager))
				.withStyle(ChatFormatting.GOLD));
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall_retrain.confirm").withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1f);
			OPEN_LISTS.put(menu, new Open(open.villager(), job));
			relayout(menu, level, hall);
			return true;
		}
		HallRetraining.Outcome outcome = HallRetraining.retrain(player, level, hall, villager, station);
		if (outcome == HallRetraining.Outcome.DONE) {
			OPEN_LISTS.remove(menu);
		} else {
			no(level, player);
			OPEN_LISTS.put(menu, new Open(open.villager(), null)); // nothing changed: the list stays, nothing picked
		}
		relayout(menu, level, hall);
		return true;
	}

	/** No job: the first click says what goes, the second takes the job away (for nothing). */
	private static void noJob(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer player, Villager villager, Open open) {
		if (!retraining(level, villager)) {
			return; // jobless already
		}
		Component who = villager.getDisplayName();
		if (!Friends.mayCommand(player, villager)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall_retrain.not_yours", who).withStyle(ChatFormatting.YELLOW));
			no(level, player);
			return;
		}
		Component old = Stations.name(villager.getVillagerData().getProfession());
		if (open.picked() != VillagerProfession.NONE) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.hall.jobs.no_job_offer", who, old, Retraining.levelName(villager))
				.withStyle(ChatFormatting.GOLD));
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall_retrain.confirm").withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1f);
			OPEN_LISTS.put(menu, new Open(open.villager(), VillagerProfession.NONE));
			relayout(menu, level, hall);
			return;
		}
		Retraining.unemploy(level, player, villager);
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.RETRAIN_WALK.remove(villager);
		level.playSound(null, villager, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
		Chat.chat(player, Component.translatable("message.aliveworkplace.hall.jobs.no_job_done", who, old).withStyle(ChatFormatting.GREEN));
		OPEN_LISTS.remove(menu);
		relayout(menu, level, hall);
	}

	private static void no(ServerLevel level, ServerPlayer player) {
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
	}

	/** The villager whose row is in {@code slot} (null: none, or not loaded). */
	@Nullable
	private static Villager personIn(ChoiceMenu menu, ServerLevel level, int slot) {
		CompoundTag row = HallPeople.row(menu.icon(slot));
		if (row == null || row.getString("uuid").isEmpty()) {
			return null;
		}
		try {
			return level.getEntity(UUID.fromString(row.getString("uuid"))) instanceof Villager v ? v : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Lays the main page out again where the table is, so the rows (and the open list) show what's true now. */
	private static void relayout(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		VillageHallScreen.renderFrom(menu, level, hall, menu.state(HallSections.PEOPLE_FIRST));
		menu.broadcastChanges();
	}

	/** Closes {@code menu}'s list (Bed ▾ opening its own list, 40.4). */
	static void close(ChoiceMenu menu) {
		OPEN_LISTS.remove(menu);
	}

	/** Forgets every open list (tests). */
	public static void forget() {
		OPEN_LISTS.clear();
	}

	private HallJobs() {
	}
}
