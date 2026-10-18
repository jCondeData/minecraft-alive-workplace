package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The Village Hall's "Free job blocks" page (ROADMAP 36.9): every job block in the village nobody works at, nearest the
 * hall first, each with its job, the building it stands in and how far away it is. A click on one lists the villagers
 * without a job who could take it; a click on one of them gives them that block: they claim it and walk over. It's the
 * block-first way round of a jobless villager's own page ({@link VillageHallScreen#renderJobs}), so a block left empty in
 * a finished home can be filled without walking a villager into the house. Retraining a villager who already works (26.6,
 * {@link io.github.jcondedata.aliveworkplace.work.Retraining}) stays at the block, for a price. Nothing is saved here: the
 * page reads the village when it's drawn, and the job is the villager's own (their job-site memory and the block's
 * point of interest).
 */
public final class FreeJobsPage {
	public static final String PAGE = "free_jobs";
	/** Blocks on the page at most: the four rows under the divider, less the last slot when there are more. */
	public static final int SHOWN = ChoiceMenu.SIZE - VillageHallScreen.FIRST_ROW;
	/** How fast a villager given a block walks there. */
	static final float WALK_SPEED = 0.6f;

	/**
	 * A free job block: where it is and the job it gives ({@code station}), the building it stands in (null outside any),
	 * how many blocks from the hall, and the villagers without a job who could take it, nearest the hall first.
	 */
	public record FreeBlock(VillageHalls.FreeStation station, @Nullable Component building, int distance, List<Villager> takers) {
	}

	@Nullable
	private static HallPages.Page page;

	/** Adds the page, as one of the mod's own spare pages (its tab gives way to other mods' pages when the row is full). */
	public static void init() {
		page = HallPages.registerSpare(PAGE, FreeJobsPage::tab, FreeJobsPage::header, FreeJobsPage::fill);
	}

	/** The village's free job blocks, nearest the hall first. */
	public static List<FreeBlock> blocks(ServerLevel level, BlockPos hall) {
		List<Villager> jobless = jobless(level, hall);
		List<Building> buildings = buildings(level, hall);
		List<FreeBlock> out = new ArrayList<>();
		for (VillageHalls.FreeStation station : VillageHalls.freeStations(level, hall)) {
			Component building = null;
			for (Building b : buildings) {
				if (b.box().isInside(station.pos())) {
					building = b.name();
					break;
				}
			}
			List<Villager> takers = new ArrayList<>();
			for (Villager v : jobless) {
				if (canTake(v, station.profession())) {
					takers.add(v);
				}
			}
			out.add(new FreeBlock(station, building, distance(hall, station.pos()), List.copyOf(takers)));
		}
		return out;
	}

	/** The free block at {@code pos}, if it's still free. */
	@Nullable
	public static FreeBlock at(ServerLevel level, BlockPos hall, BlockPos pos) {
		for (FreeBlock b : blocks(level, hall)) {
			if (b.station().pos().equals(pos)) {
				return b;
			}
		}
		return null;
	}

	/**
	 * The grown villagers without a job in the village, the ones the hall may give one (not nitwits, Legends or caravan
	 * carters), nearest the hall first.
	 */
	static List<Villager> jobless(ServerLevel level, BlockPos hall) {
		List<Villager> out = new ArrayList<>();
		for (Villager v : VillageHalls.census(level, hall).jobless()) {
			if (v.getVillagerData().getProfession() != VillagerProfession.NITWIT && io.github.jcondedata.aliveworkplace.legend.Legends.of(v).isEmpty()) {
				out.add(v);
			}
		}
		return out;
	}

	/**
	 * Whether {@code villager}, without a job, may be given a block of {@code job}: one who never worked takes any; one who
	 * still has a job's trades but lost the block takes only a block of that job (another job is paid retraining, 26.6).
	 */
	static boolean canTake(Villager villager, VillagerProfession job) {
		VillagerProfession had = villager.getVillagerData().getProfession();
		return had == VillagerProfession.NONE || had == job;
	}

	/** Gives {@code villager} the block: it's claimed for them and they walk to it. False if it was taken meanwhile. */
	public static boolean give(ServerLevel level, Villager villager, VillageHalls.FreeStation station) {
		if (!VillageHalls.assign(level, villager, station)) {
			return false;
		}
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(station.pos(), WALK_SPEED, 1));
		return true;
	}

	/** Blocks from the hall, along the ground, as the hall's "34 blocks north-east" counts them. */
	static int distance(BlockPos hall, BlockPos pos) {
		double dx = pos.getX() - hall.getX();
		double dz = pos.getZ() - hall.getZ();
		return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	private record Building(BoundingBox box, Component name) {
	}

	/** The village's buildings, finished or going up, with their outlines. */
	private static List<Building> buildings(ServerLevel level, BlockPos hall) {
		List<Building> out = new ArrayList<>();
		BuildSiteManager sites = BuildSiteManager.get(level);
		int reach = VillageHalls.RADIUS + 48; // a building's origin can sit outside the village while the block is in it
		for (BuildSiteManager.Finished f : sites.finishedNear(level, hall, reach)) {
			add(out, level, f.structure(), f.placement());
		}
		for (BuildSite site : sites.all()) {
			if (!site.isDone() && !site.isQueued() && site.placement().dimension().equals(Ids.of(level.dimension()))
				&& site.placement().origin().distSqr(hall) <= (double) reach * reach) {
				add(out, level, site.structure(), site.placement());
			}
		}
		return out;
	}

	private static void add(List<Building> out, ServerLevel level, ResourceLocation structure, BlueprintData.Placement placement) {
		BlueprintLibrary.get(level, structure).ifPresent(b -> out.add(new Building(BlueprintOutline.bounds(placement, b.size()), Blueprints.displayName(structure))));
	}

	// --- The page -------------------------------------------------------------------------------------------------

	static ItemStack tab(ServerLevel level, BlockPos hall) {
		List<FreeBlock> blocks = blocks(level, hall);
		int jobless = jobless(level, hall).size();
		ItemStack stack = new ItemStack(Items.CRAFTING_TABLE, Math.max(1, Math.min(64, blocks.size())));
		return VillageHallScreen.icon(stack, Component.translatable("screen.aliveworkplace.free_jobs"), ChatFormatting.GOLD,
			VillageHallScreen.line(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.free_jobs.count", blocks.size(), blocks.size()),
				blocks.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.WHITE),
			VillageHallScreen.line(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.free_jobs.jobless", jobless, jobless),
				jobless > 0 && !blocks.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.free_jobs.hint", ChatFormatting.DARK_GRAY));
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.CRAFTING_TABLE, Component.translatable("screen.aliveworkplace.free_jobs.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, VillageHallScreen.line("screen.aliveworkplace.free_jobs.about", ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.free_jobs.retrain", ChatFormatting.DARK_GRAY));
	}

	/** The page: a button per free block, nearest first; with more than fit, the last slot says how many more. */
	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		List<FreeBlock> blocks = blocks(level, hall);
		if (blocks.isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.hall.no_free_stations"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.hall.no_free_stations_hint", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		int fit = blocks.size() > SHOWN ? SHOWN - 1 : SHOWN;
		int slot = VillageHallScreen.FIRST_ROW;
		for (FreeBlock block : blocks.subList(0, Math.min(fit, blocks.size()))) {
			menu.button(slot++, blockIcon(level, hall, block, true), p -> {
				renderBlock(menu, level, hall, block.station().pos(), p);
				menu.broadcastChanges();
			});
		}
		if (blocks.size() > fit) {
			menu.button(ChoiceMenu.SIZE - 1, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.free_jobs.more", blocks.size() - fit),
				ChatFormatting.GRAY), null);
		}
	}

	/** A free block as the page shows it: the block, its job, the building it's in, how far, who could take it. */
	public static ItemStack blockIcon(ServerLevel level, BlockPos hall, FreeBlock block, boolean clickable) {
		Item item = level.getBlockState(block.station().pos()).getBlock().asItem();
		Component job = Stations.name(block.station().profession());
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(block.building() != null ? Component.translatable("screen.aliveworkplace.free_jobs.in", block.building())
			: Component.translatable("screen.aliveworkplace.free_jobs.outside"), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line(VillageHallScreen.where(hall, block.station().pos()), ChatFormatting.GRAY));
		int takers = block.takers().size();
		lore.add(VillageHallScreen.line(takers == 0 ? Component.translatable("screen.aliveworkplace.free_jobs.nobody")
			: io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.free_jobs.takers", takers, takers),
			takers == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN));
		if (clickable) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.free_jobs.click", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(item == Items.AIR ? Items.PAPER : item, job.copy(), takers == 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE,
			lore.toArray(Component[]::new));
	}

	/** One free block's page: back to the list, the block, then the villagers without a job who could take it. */
	public static void renderBlock(ChoiceMenu menu, ServerLevel level, BlockPos hall, BlockPos pos, ServerPlayer viewer) {
		FreeBlock block = at(level, hall, pos);
		if (block == null) { // taken while the list was open
			taken(menu, level, hall, viewer);
			return;
		}
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.free_jobs.back"), ChatFormatting.WHITE), p -> {
			backToList(menu, level, hall, p);
		});
		menu.button(4, blockIcon(level, hall, block, false), null);
		menu.divider(1);
		Component job = Stations.name(block.station().profession());
		int slot = VillageHallScreen.FIRST_ROW;
		io.github.jcondedata.aliveworkplace.people.ClassNeeds.Village classes = new io.github.jcondedata.aliveworkplace.people.ClassNeeds.Village(level, hall, Chronicle.day(level));
		for (Villager villager : block.takers()) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			ItemStack icon = VillageHallScreen.person(level, hall, villager, classes, viewer);
			Component refusal = io.github.jcondedata.aliveworkplace.people.ClassJobs.refusal(hall, villager, block.station().profession());
			if (refusal != null) { // a job above their class (34.8) is greyed, with the class it needs; a click says why
				io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass need = io.github.jcondedata.aliveworkplace.people.ClassJobs.needed(block.station().profession());
				menu.button(slot++, withLine(icon, VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.job_needs_class", need.name()), ChatFormatting.RED)), p -> {
					Chat.actionBar(p, refusal.copy().withStyle(ChatFormatting.YELLOW));
					level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
				});
				continue;
			}
			menu.button(slot++, withLine(icon, VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.give_job", villager.getDisplayName(), job),
				ChatFormatting.GREEN)), p -> pick(menu, level, hall, block.station(), villager, p));
		}
		if (block.takers().isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.free_jobs.none_jobless"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.free_jobs.none_jobless_hint", ChatFormatting.DARK_GRAY)), null);
		}
	}

	/** A villager picked for the block: they take it, or, if the block or the villager was taken meanwhile, nothing changes. */
	static void pick(ChoiceMenu menu, ServerLevel level, BlockPos hall, VillageHalls.FreeStation station, Villager villager, ServerPlayer p) {
		Component job = Stations.name(station.profession());
		boolean stillJobless = villager.isAlive() && jobless(level, hall).contains(villager) && canTake(villager, station.profession());
		if (!stillJobless) {
			Chat.actionBar(p, Component.translatable("message.aliveworkplace.free_jobs.found_work", villager.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			renderBlock(menu, level, hall, station.pos(), p);
			menu.broadcastChanges();
			return;
		}
		if (give(level, villager, station)) {
			Chat.actionBar(p, Component.translatable("message.aliveworkplace.hall.assigned", villager.getDisplayName(), job).withStyle(ChatFormatting.GREEN));
			level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
			backToList(menu, level, hall, p);
		} else {
			taken(menu, level, hall, p);
		}
	}

	/** The block went to someone else first: said so, and the list again without it. */
	private static void taken(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer p) {
		Chat.actionBar(p, Component.translatable("message.aliveworkplace.hall.not_assigned").withStyle(ChatFormatting.YELLOW));
		level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
		backToList(menu, level, hall, p);
	}

	private static void backToList(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer p) {
		HallPages.Page shown = page;
		if (shown == null) {
			for (HallPages.Page each : HallPages.all()) {
				if (each.id().equals(PAGE)) {
					shown = each;
				}
			}
		}
		if (shown != null) {
			VillageHallScreen.renderPage(menu, level, hall, shown, p);
		} else {
			VillageHallScreen.showList(menu, level, hall, 0);
		}
		menu.broadcastChanges();
	}

	private static ItemStack withLine(ItemStack icon, Component line) {
		List<Component> lines = new ArrayList<>(icon.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		// The person's click hints are the main list's (find them, give them a job, their life story); here the click gives this block.
		lines.removeIf(l -> l.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
			&& (t.getKey().equals("screen.aliveworkplace.hall.jobless_click") || t.getKey().equals("screen.aliveworkplace.hall.click_to_find")
				|| t.getKey().equals("screen.aliveworkplace.hall.life_story_hint")));
		lines.add(line);
		icon.set(DataComponents.LORE, new ItemLore(lines));
		return icon;
	}

	private FreeJobsPage() {
	}
}
