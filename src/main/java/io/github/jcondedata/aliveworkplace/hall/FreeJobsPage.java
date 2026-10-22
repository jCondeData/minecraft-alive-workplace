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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiType;
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
 * a finished home can be filled without walking a villager into the house. Retraining a villager who already works, for a price, is their
 * page's Change job ({@link HallRetraining}, 36.18) or a sneak-click at the block (26.6). Nothing is saved here: the
 * page reads the village when it's drawn, and the job is the villager's own (their job-site memory and the block's
 * point of interest).
 * <p>
 * B303: the list groups the free blocks of one kind in one building into a line with a count ("Fisherman ×4", "In the
 * Storehouse Shed"), and a block several jobs share ({@link VillageHalls#jobs}: a composter, a lectern, a Shop
 * Counter...) says how many it offers ("Composter: 4 jobs") and lists them; a click on it lets the player pick the job
 * first, then who takes it, who becomes that job.
 */
public final class FreeJobsPage {
	public static final String PAGE = "free_jobs";
	/** Blocks on the page at most: the four rows under the divider, less the last slot when there are more. */
	public static final int SHOWN = ChoiceMenu.SIZE - VillageHallScreen.FIRST_ROW;
	/** How fast a villager given a block walks there. */
	static final float WALK_SPEED = 0.6f;

	/**
	 * A free job block: where it is and the job it gives ({@code station}), the building it stands in (null outside any),
	 * how many blocks from the hall, the villagers without a job who could take it for that job, nearest the hall first,
	 * every job the block offers ({@code jobs}, the station's first: B303) and the outline of its building.
	 */
	public record FreeBlock(VillageHalls.FreeStation station, @Nullable Component building, int distance, List<Villager> takers,
		List<VillagerProfession> jobs, @Nullable BoundingBox buildingBox) {
		/** Whether the block offers more than one job (B303). */
		public boolean shared() {
			return jobs.size() > 1;
		}
	}

	/** The free blocks of one kind in one building (or all out in the open), nearest the hall first: one line on the list (B303). */
	public record Group(List<FreeBlock> blocks) {
		public FreeBlock nearest() {
			return blocks.get(0);
		}
	}

	@Nullable
	private static HallPages.Page page;

	/** Adds the page, as one of the mod's own spare pages (its tab gives way to other mods' pages when the row is full). */
	public static void init() {
		page = HallPages.registerSpare(PAGE, FreeJobsPage::tab, FreeJobsPage::header, FreeJobsPage::fill);
	}

	/** The village's free job blocks, nearest the hall first. */
	public static List<FreeBlock> blocks(ServerLevel level, BlockPos hall) {
		VillageHalls.Census census = VillageHalls.census(level, hall);
		List<Villager> jobless = jobless(census);
		// B290: a free Blueprint Table can also go to one of the village's builders, who moves over from their own table
		List<Villager> builders = builders(census);
		List<Building> buildings = buildings(level, hall);
		Map<Holder<PoiType>, List<VillagerProfession>> jobs = new HashMap<>(); // once per kind of block, not per block
		List<FreeBlock> out = new ArrayList<>();
		for (VillageHalls.FreeStation station : VillageHalls.freeStations(level, hall)) {
			Building in = null;
			for (Building b : buildings) {
				if (b.box().isInside(station.pos())) {
					in = b;
					break;
				}
			}
			out.add(new FreeBlock(station, in == null ? null : in.name(), distance(hall, station.pos()), takers(level, station, jobless, builders),
				jobs.computeIfAbsent(station.poi(), poi -> VillageHalls.jobs(station)), in == null ? null : in.box()));
		}
		return out;
	}

	/** The free blocks as the list shows them (B303): those of one kind in one building on one line, nearest the hall first. */
	public static List<Group> groups(List<FreeBlock> blocks) {
		Map<List<Object>, List<FreeBlock>> by = new LinkedHashMap<>();
		for (FreeBlock b : blocks) {
			List<Object> key = new ArrayList<>();
			key.add(b.station().poi());
			key.add(b.buildingBox() == null ? "outside" : b.buildingBox());
			by.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
		}
		List<Group> out = new ArrayList<>();
		for (List<FreeBlock> each : by.values()) {
			out.add(new Group(List.copyOf(each)));
		}
		return out;
	}

	private static List<Villager> builders(VillageHalls.Census census) {
		return census.workers().stream().filter(FreeJobsPage::isBuilder).toList();
	}

	/** Who could take {@code station} for its job: the jobless who may, and (B290) for a free Blueprint Table the village's builders. */
	private static List<Villager> takers(ServerLevel level, VillageHalls.FreeStation station, List<Villager> jobless, List<Villager> builders) {
		List<Villager> takers = new ArrayList<>();
		for (Villager v : jobless) {
			if (canTake(v, station.profession())) {
				takers.add(v);
			}
		}
		if (station.profession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER
			&& io.github.jcondedata.aliveworkplace.build.BenchMoves.isTable(level, station.pos())) {
			takers.addAll(builders);
		}
		return List.copyOf(takers);
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
	 * The free block at {@code pos} as {@code job} (B303: one of the jobs a shared block offers), with who could take it
	 * for that job; null if the block isn't free any more or doesn't offer the job.
	 */
	@Nullable
	public static FreeBlock at(ServerLevel level, BlockPos hall, BlockPos pos, VillagerProfession job) {
		FreeBlock block = at(level, hall, pos);
		if (block == null || block.station().profession() == job) {
			return block;
		}
		if (!block.jobs().contains(job)) {
			return null;
		}
		VillageHalls.Census census = VillageHalls.census(level, hall);
		VillageHalls.FreeStation as = new VillageHalls.FreeStation(block.station().pos(), job, block.station().poi());
		return new FreeBlock(as, block.building(), block.distance(), takers(level, as, jobless(census), builders(census)), block.jobs(), block.buildingBox());
	}

	/**
	 * The grown villagers without a job in the village, the ones the hall may give one (not nitwits, Legends or caravan
	 * carters), nearest the hall first.
	 */
	static List<Villager> jobless(ServerLevel level, BlockPos hall) {
		return jobless(VillageHalls.census(level, hall));
	}

	private static List<Villager> jobless(VillageHalls.Census census) {
		List<Villager> out = new ArrayList<>();
		for (Villager v : census.jobless()) {
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
	/** A builder at work at a table of their own (B290: one the free table's page may move over). */
	static boolean isBuilder(Villager villager) {
		return villager.isAlive() && !villager.isBaby() && villager.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER
			&& villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent();
	}

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

	/**
	 * The page: a line per kind of free block in each building (B303: with how many, and how many jobs a shared block
	 * offers), nearest first; with more than fit, the last slot says how many more blocks.
	 */
	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		List<FreeBlock> blocks = blocks(level, hall);
		if (blocks.isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.hall.no_free_stations"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.hall.no_free_stations_hint", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		List<Group> groups = groups(blocks);
		List<Villager> jobless = jobless(level, hall);
		int fit = groups.size() > SHOWN ? SHOWN - 1 : SHOWN;
		int slot = VillageHallScreen.FIRST_ROW;
		int listed = 0;
		for (Group group : groups.subList(0, Math.min(fit, groups.size()))) {
			BlockPos pos = group.nearest().station().pos();
			boolean shared = group.nearest().shared();
			listed += group.blocks().size();
			menu.button(slot++, groupIcon(level, hall, group, jobless), p -> {
				if (shared) {
					renderJobs(menu, level, hall, pos, p);
				} else {
					renderBlock(menu, level, hall, pos, p);
				}
				menu.broadcastChanges();
			});
		}
		if (groups.size() > fit) {
			menu.button(ChoiceMenu.SIZE - 1, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.free_jobs.more", blocks.size() - listed),
				ChatFormatting.GRAY), null);
		}
	}

	/**
	 * One line of the list (B303): a lone block with one job as before ({@link #blockIcon}); several of a kind in one
	 * building as "Fisherman ×4" with how many; a block several jobs share as "Composter: 4 jobs", each job below, and who
	 * could take any of them.
	 */
	public static ItemStack groupIcon(ServerLevel level, BlockPos hall, Group group, List<Villager> jobless) {
		FreeBlock block = group.nearest();
		int count = group.blocks().size();
		if (!block.shared()) {
			return count == 1 ? blockIcon(level, hall, block, true)
				: blockIcon(level, hall, block, Component.translatable("screen.aliveworkplace.free_jobs.many", Stations.name(block.station().profession()), count),
					count, block.takers(), false, "screen.aliveworkplace.free_jobs.click");
		}
		List<Villager> takers = new ArrayList<>();
		for (Villager v : jobless) {
			if (block.jobs().stream().anyMatch(job -> canTake(v, job))) {
				takers.add(v);
			}
		}
		return blockIcon(level, hall, block, sharedName(level, block, count), count, takers, true, "screen.aliveworkplace.free_jobs.click_job");
	}

	/** "Composter: 4 jobs", or with several such blocks in the building "Composter ×3: 4 jobs" (B303). */
	static Component sharedName(ServerLevel level, FreeBlock block, int count) {
		Component name = level.getBlockState(block.station().pos()).getBlock().getName();
		return count == 1 ? Component.translatable("screen.aliveworkplace.free_jobs.shared", name, block.jobs().size())
			: Component.translatable("screen.aliveworkplace.free_jobs.shared_many", name, block.jobs().size(), count);
	}

	/** A free block as the page shows it: the block, its job, the building it's in, how far, who could take it. */
	public static ItemStack blockIcon(ServerLevel level, BlockPos hall, FreeBlock block, boolean clickable) {
		return blockIcon(level, hall, block, Stations.name(block.station().profession()), 1, block.takers(), false, clickable ? "screen.aliveworkplace.free_jobs.click" : null);
	}

	private static ItemStack blockIcon(ServerLevel level, BlockPos hall, FreeBlock block, Component title, int count, List<Villager> could,
		boolean listJobs, @Nullable String click) {
		Item item = level.getBlockState(block.station().pos()).getBlock().asItem();
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(block.building() != null ? Component.translatable("screen.aliveworkplace.free_jobs.in", block.building())
			: Component.translatable("screen.aliveworkplace.free_jobs.outside"), ChatFormatting.GRAY));
		if (count > 1) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.free_jobs.here", count), ChatFormatting.GRAY));
		}
		lore.add(VillageHallScreen.line(VillageHallScreen.where(hall, block.station().pos()), ChatFormatting.GRAY));
		if (listJobs) {
			for (VillagerProfession job : block.jobs()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.free_jobs.job_line", Stations.name(job)), ChatFormatting.WHITE));
			}
		}
		int movers = (int) could.stream().filter(FreeJobsPage::isBuilder).count(); // B290: builders who could move here
		int takers = could.size() - movers;
		if (takers > 0 || movers == 0) {
			lore.add(VillageHallScreen.line(takers == 0 ? Component.translatable("screen.aliveworkplace.free_jobs.nobody")
				: io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.free_jobs.takers", takers, takers),
				takers == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN));
		}
		if (movers > 0) {
			lore.add(VillageHallScreen.line(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.free_jobs.builders", movers, movers),
				ChatFormatting.GREEN));
		}
		takers += movers;
		if (click != null) {
			lore.add(VillageHallScreen.line(click, ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(item == Items.AIR ? Items.PAPER : item, title.copy(), takers == 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE,
			lore.toArray(Component[]::new));
	}

	/**
	 * A shared block's page (B303): back to the list, the block, then a line per job it offers, with who could take each;
	 * a click on a job shows who could take the block as that job.
	 */
	public static void renderJobs(ChoiceMenu menu, ServerLevel level, BlockPos hall, BlockPos pos, ServerPlayer viewer) {
		FreeBlock block = at(level, hall, pos);
		if (block == null) { // taken while the list was open
			taken(menu, level, hall, viewer);
			return;
		}
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.free_jobs.back"), ChatFormatting.WHITE), p -> {
			backToList(menu, level, hall, p);
		});
		Item item = level.getBlockState(pos).getBlock().asItem();
		Item shown = item == Items.AIR ? Items.PAPER : item;
		menu.button(4, VillageHallScreen.icon(shown, sharedName(level, block, 1), ChatFormatting.WHITE,
			VillageHallScreen.line(block.building() != null ? Component.translatable("screen.aliveworkplace.free_jobs.in", block.building())
				: Component.translatable("screen.aliveworkplace.free_jobs.outside"), ChatFormatting.GRAY),
			VillageHallScreen.line(VillageHallScreen.where(hall, pos), ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.free_jobs.pick_job", ChatFormatting.DARK_GRAY)), null);
		menu.divider(1);
		VillageHalls.Census census = VillageHalls.census(level, hall);
		List<Villager> jobless = jobless(census);
		List<Villager> builders = builders(census);
		int slot = VillageHallScreen.FIRST_ROW;
		for (VillagerProfession job : block.jobs()) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			int could = takers(level, new VillageHalls.FreeStation(pos, job, block.station().poi()), jobless, builders).size();
			menu.button(slot++, VillageHallScreen.icon(shown, Stations.name(job), could == 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE,
				VillageHallScreen.line(could == 0 ? Component.translatable("screen.aliveworkplace.free_jobs.nobody")
					: io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.free_jobs.takers", could, could),
					could == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN),
				VillageHallScreen.line("screen.aliveworkplace.free_jobs.click", ChatFormatting.DARK_GRAY)), p -> {
				renderBlock(menu, level, hall, pos, job, p);
				menu.broadcastChanges();
			});
		}
	}

	/** One free block's page: back to the list, the block, then the villagers without a job who could take it. */
	public static void renderBlock(ChoiceMenu menu, ServerLevel level, BlockPos hall, BlockPos pos, ServerPlayer viewer) {
		renderBlock(menu, level, hall, pos, null, viewer);
	}

	/**
	 * One free block's page as {@code job} (null: its first job): back (to its jobs, for a shared block: B303), the block,
	 * then the villagers without a job who could take it as that job.
	 */
	public static void renderBlock(ChoiceMenu menu, ServerLevel level, BlockPos hall, BlockPos pos, @Nullable VillagerProfession job, ServerPlayer viewer) {
		FreeBlock block = job == null ? at(level, hall, pos) : at(level, hall, pos, job);
		if (block == null) { // taken while the list was open
			taken(menu, level, hall, viewer);
			return;
		}
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable(block.shared() ? "screen.aliveworkplace.free_jobs.back_jobs"
			: "screen.aliveworkplace.free_jobs.back"), ChatFormatting.WHITE), p -> {
			if (block.shared()) {
				renderJobs(menu, level, hall, pos, p);
				menu.broadcastChanges();
			} else {
				backToList(menu, level, hall, p);
			}
		});
		menu.button(4, blockIcon(level, hall, block, false), null);
		menu.divider(1);
		Component jobName = Stations.name(block.station().profession());
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
			Component click = isBuilder(villager)
				? Component.translatable("screen.aliveworkplace.free_jobs.move_builder", villager.getDisplayName(),
					io.github.jcondedata.aliveworkplace.build.Builders.benchPos(villager).map(io.github.jcondedata.aliveworkplace.build.BenchMoves::at).orElse(Component.literal("?")))
				: Component.translatable("screen.aliveworkplace.hall.give_job", villager.getDisplayName(), jobName);
			menu.button(slot++, withLine(icon, VillageHallScreen.line(click, ChatFormatting.GREEN)), p -> pick(menu, level, hall, block.station(), villager, p));
		}
		if (block.takers().isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.free_jobs.none_jobless"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.free_jobs.none_jobless_hint", ChatFormatting.DARK_GRAY)), null);
		}
	}

	/** A villager picked for the block: they take it, or, if the block or the villager was taken meanwhile, nothing changes. */
	static void pick(ChoiceMenu menu, ServerLevel level, BlockPos hall, VillageHalls.FreeStation station, Villager villager, ServerPlayer p) {
		Component job = Stations.name(station.profession());
		if (isBuilder(villager) && station.profession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER) {
			moveBuilder(menu, level, hall, station, villager, p);
			return;
		}
		boolean stillJobless = villager.isAlive() && jobless(level, hall).contains(villager) && canTake(villager, station.profession());
		if (!stillJobless) {
			Chat.actionBar(p, Component.translatable("message.aliveworkplace.free_jobs.found_work", villager.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			renderBlock(menu, level, hall, station.pos(), station.profession(), p);
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

	/** B290: one of the village's builders picked for a free table: they move over to it, their builds with them. */
	private static void moveBuilder(ChoiceMenu menu, ServerLevel level, BlockPos hall, VillageHalls.FreeStation station, Villager builder, ServerPlayer p) {
		if (!io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(p, builder)) {
			io.github.jcondedata.aliveworkplace.work.Hiring.hire(p, builder, Component.empty()); // refuses, and says whose they are
			return;
		}
		Component refusal = io.github.jcondedata.aliveworkplace.build.BenchMoves.refusal(level, builder, station.pos());
		if (refusal != null) {
			Chat.actionBar(p, refusal.copy().withStyle(ChatFormatting.YELLOW));
			level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			backToList(menu, level, hall, p);
			return;
		}
		io.github.jcondedata.aliveworkplace.build.BenchMoves.move(level, builder, station.pos());
		builder.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(station.pos(), WALK_SPEED, 1));
		Chat.actionBar(p, Component.translatable("message.aliveworkplace.builder.moved", builder.getDisplayName(),
			io.github.jcondedata.aliveworkplace.build.BenchMoves.at(station.pos())).withStyle(ChatFormatting.GREEN));
		level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
		backToList(menu, level, hall, p);
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
