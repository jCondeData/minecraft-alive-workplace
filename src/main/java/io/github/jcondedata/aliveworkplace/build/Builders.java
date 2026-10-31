package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import io.github.jcondedata.aliveworkplace.work.Reach;
import org.jetbrains.annotations.Nullable;

/** Builder job lifecycle: hand over a blueprint, report status, finish, cancel. */
public final class Builders {
	/**
	 * Outside the village, a build site's centre must be within this many blocks of the builder's bench
	 * ({@code maxSiteDistance}); in the village the bench stands in, anywhere in it will do (36.20, {@link Reach}).
	 */
	public static int MAX_SITE_DISTANCE = 128;

	/**
	 * The spot a build is measured from, for the builder's reach and for which village it is in: the centre of its
	 * outline, as placing it measures (B615: upkeep, refreshes and repair asks measured from the origin corner, so a build
	 * accepted at the reach's edge was never kept up). A build whose blueprint is gone is measured from its origin.
	 */
	public static BlockPos spot(ServerLevel level, net.minecraft.resources.ResourceLocation structure, BlueprintData.Placement placement) {
		return BlueprintLibrary.get(level, structure)
			.map(b -> BlueprintOutline.bounds(placement, b.size()).getCenter())
			.orElse(placement.origin());
	}

	/** True if the build of {@code structure} at {@code placement} is in reach of the bench at {@code bench} (the one rule placing uses). */
	public static boolean inReach(ServerLevel level, BlockPos bench, net.minecraft.resources.ResourceLocation structure, BlueprintData.Placement placement) {
		return Reach.inReach(level, bench, spot(level, structure, placement), MAX_SITE_DISTANCE);
	}

	/** How many blueprints a builder accepts on top of the one they are building. */
	public static final int MAX_QUEUE = 5;
	/** How many idle builders can help one site at a time. */
	public static final int MAX_HELPERS = 3;

	public static boolean isBuilder(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.BUILDER;
	}

	public static Optional<BlockPos> benchPos(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(g -> g.dimension() == villager.level().dimension())
			.map(GlobalPos::pos);
	}

	/**
	 * The bench whose chests feed {@code site}: the one the build was started from, while it stands. A builder's own job
	 * site can change mid-build (two builders' benches close together get swapped), and fetching by it would take the
	 * other builder's materials (B22). Only when the site's bench is gone does the builder's current bench take over.
	 */
	public static Optional<BlockPos> siteBench(ServerLevel level, Villager villager, BuildSite site) {
		BlockPos bench = site.bench();
		if (bench != null && !level.isLoaded(bench)) {
			return benchPos(villager); // can't tell whether it still stands: leave the site's record alone
		}
		if (bench != null && (level.getBlockState(bench).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH)
			|| level.getBlockState(bench).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.FOUNDATION_STONE))) {
			return Optional.of(bench); // (a Wonder's part, 35.4: the Foundation Stone's chests are its stockpile)
		}
		Optional<BlockPos> own = benchPos(villager);
		own.ifPresent(site::setBench); // the bench was moved: its new place is where the chests are now
		return own;
	}

	/**
	 * The site this builder is working on. When they have none (just finished, or it was cancelled)
	 * the next blueprint in their queue becomes the active one.
	 */
	@Nullable
	public static BuildSite activeSite(ServerLevel level, Villager villager) {
		BuilderJob job = ModAttachments.BUILDER_JOB.get(villager);
		if (job != null) {
			BuildSite site = BuildSiteManager.get(level).get(job.siteId());
			if (site != null && !site.isQueued() && !(job.helper() && site.isDone())) {
				return site;
			}
			if (job.helper()) {
				stopHelping(level, villager);
			} else {
				ModAttachments.BUILDER_JOB.remove(villager);
			}
		}
		// 35.5: a builder with no build of its own (or, once every builder is called, between builds) joins its
		// village's Wonder crew.
		BuildSite crew = io.github.jcondedata.aliveworkplace.wonder.WonderCrews.join(level, villager);
		if (crew != null) {
			return crew;
		}
		return startNext(level, villager);
	}

	/** The site to work on this shift: their own (or next queued) build, else one nearby to help with. */
	@Nullable
	static BuildSite workSite(ServerLevel level, Villager villager) {
		BuildSite site = activeSite(level, villager);
		if (site == null && villager.tickCount % 40 == 0) {
			site = recruit(level, villager);
		}
		return site;
	}

	public static boolean isHelping(Villager villager) {
		BuilderJob job = ModAttachments.BUILDER_JOB.get(villager);
		return job != null && job.helper();
	}

	/**
	 * An idle builder looks for a build near its bench that could use a hand (gamerule
	 * workplaceBuildersHelp) and joins it as a helper. Returns the site, or null.
	 */
	@Nullable
	public static BuildSite recruit(ServerLevel level, Villager villager) {
		if (!Rules.on(level, ModGameRules.BUILDERS_HELP) || ModAttachments.BUILDER_JOB.has(villager)) {
			return null;
		}
		Optional<BlockPos> bench = benchPos(villager);
		if (bench.isEmpty()) {
			return null;
		}
		BuildSite best = null;
		double bestDistance = Double.MAX_VALUE;
		Employer employer = ModAttachments.BUILDER_EMPLOYER.get(villager);
		boolean ownership = Rules.on(level, ModGameRules.BUILDER_OWNERSHIP);
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (ownership && employer != null && !Friends.get(level.getServer()).mayDirect(employer.id(), site.owner())) {
				continue; // a hired builder only helps its employer and their friends
			}
			if (site.isQueued() || site.isDone() || site.bench() == null || villager.getUUID().equals(site.builder())
				|| site.wonder() != null // a Wonder's crew joins through WonderCrews (35.5)
				|| site.helpers(level.getGameTime()).size() >= io.github.jcondedata.aliveworkplace.hall.Guilds.helpers(level, site.bench())) {
				continue;
			}
			double distance = Math.sqrt(site.bench().distSqr(bench.get()));
			if (distance <= MAX_SITE_DISTANCE && distance < bestDistance) {
				best = site;
				bestDistance = distance;
			}
		}
		if (best != null) {
			ModAttachments.BUILDER_JOB.set(villager, new BuilderJob(best.id(), true));
			best.seen(villager.getUUID(), level.getGameTime());
		}
		return best;
	}

	/** Stops helping: hands back anything fetched for the site and becomes idle. */
	public static void stopHelping(ServerLevel level, Villager villager) {
		BuilderJob job = ModAttachments.BUILDER_JOB.get(villager);
		ModAttachments.BUILDER_JOB.remove(villager);
		BuildSite site = job != null ? BuildSiteManager.get(level).get(job.siteId()) : null;
		if (site != null) {
			site.release(villager.getUUID());
		}
		// 35.5: a Wonder's crew member whose part is finished (its site gone) hands what it carries back to the stockpile
		// round the Wonder's stone, where the next stage takes it from, not to its own bench.
		BlockPos bench = site != null && site.bench() != null ? site.bench()
			: io.github.jcondedata.aliveworkplace.wonder.WonderCrews.stockpile(level, villager).orElseGet(() -> benchPos(villager).orElse(villager.blockPosition()));
		emptyBag(level, villager, bench, SupplyContainers.find(level, bench, null));
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	/**
	 * B670: a build is over (finished or canceled): its helpers stop now, while the site still names the lead's bench, so
	 * what they carry goes back to the lead's chests. A helper that only noticed later, once the site was gone, emptied
	 * its bag at its own bench, out of the lead's reach (the soak's builder stuck for good waiting on lanterns that lay in
	 * another builder's chest). A Wonder's crew keeps its own way back to the stockpile (35.5).
	 */
	static void sendHelpersBack(ServerLevel level, BuildSite site) {
		if (site.wonder() != null || site.bench() == null) {
			return;
		}
		AABB around = new AABB(site.bench()).inflate(MAX_SITE_DISTANCE);
		for (Villager helper : level.getEntitiesOfClass(Villager.class, around, v -> {
			BuilderJob job = ModAttachments.BUILDER_JOB.get(v);
			return job != null && job.helper() && job.siteId().equals(site.id());
		})) {
			stopHelping(level, helper);
		}
	}

	/** Sites waiting for this builder, in the order they were handed over. */
	public static List<BuildSite> queue(ServerLevel level, Villager villager) {
		List<BuildSite> out = new java.util.ArrayList<>();
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.isQueued() && villager.getUUID().equals(site.builder())) {
				out.add(site);
			}
		}
		return out;
	}

	@Nullable
	private static BuildSite startNext(ServerLevel level, Villager villager) {
		List<BuildSite> queue = queue(level, villager);
		if (queue.isEmpty()) {
			return null;
		}
		BuildSite next = queue.get(0);
		next.setQueued(false);
		ModAttachments.BUILDER_JOB.set(villager, new BuilderJob(next.id()));
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(next.owner());
		if (owner != null && next.wonder() != null) {
			// a Wonder part's structure is named like "Stage 1 0": the owner reads the Wonder and its stage (B678)
			io.github.jcondedata.aliveworkplace.wonder.WonderStages.partNextLine(level, next, villager.getDisplayName())
				.ifPresent(line -> tell(owner, line, ChatFormatting.GREEN));
		} else if (owner != null) {
			tell(owner, Component.translatable("message.aliveworkplace.queue.next", villager.getDisplayName(),
				Blueprints.displayName(next.structure())), ChatFormatting.GREEN);
		}
		return next;
	}

	// --- hand-over ---------------------------------------------------------------------------

	/** Player right-clicked a builder while holding a blueprint. */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack) {
		return assign(player, villager, stack, false);
	}

	/** {@code deconstruct}: take the building at the placement down instead of building it (sneak-give). */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack, boolean deconstruct) {
		ServerLevel level = Players.level(player);
		if (!Friends.mayCommand(player, villager)) {
			Employer employer = ModAttachments.BUILDER_EMPLOYER.get(villager);
			tell(player, Component.translatable("message.aliveworkplace.not_your_builder", villager.getDisplayName(),
				employer != null ? employer.name() : "?", player.getGameProfile().getName()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		if (isHelping(villager)) {
			stopHelping(level, villager); // their own build comes first
		}
		Optional<BlueprintData> data = BlueprintItem.data(stack);
		if (data.isEmpty()) {
			return InteractionResult.PASS;
		}
		Optional<BlueprintData.Placement> placement = data.get().placement();
		deconstruct |= data.get().takeDown(); // B526: a canceled take-down's blueprint carries on taking it down
		if (placement.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.assign.not_placed"), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		if (!placement.get().dimension().equals(Ids.of(level.dimension()))) {
			tell(player, Component.translatable("message.aliveworkplace.assign.wrong_dimension"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		if (Cellars.isJob(stack) && !Cellars.isCellarDig(level, data.get().structure(), placement.get())) {
			// B386: a "Dig the cellar" bought before the cellar was there is no job any more (it would be a whole build over
			// the standing one); the player keeps it
			tell(player, Component.translatable("message.aliveworkplace.cellar_no_dig", Blueprints.displayName(data.get().structure())), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		BuildSite existing = activeSite(level, villager);
		int queued = existing != null ? queue(level, villager).size() : 0;
		if (queued >= MAX_QUEUE) {
			tell(player, Component.translatable("message.aliveworkplace.queue.full", villager.getDisplayName(), MAX_QUEUE), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		Optional<BlockPos> bench = benchPos(villager);
		if (bench.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.assign.no_bench"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, data.get().structure());
		if (blueprint.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.blueprint.unknown", data.get().structure().toString()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BoundingBox box = BlueprintOutline.bounds(placement.get(), blueprint.get().size());
		double distance = Math.sqrt(box.getCenter().distSqr(bench.get()));
		if (!inReach(level, bench.get(), data.get().structure(), placement.get())) {
			// B378: rounded up, so a refused spot never reads as within the reach (48.5 blocks: 49)
			tell(player, Reach.refusal(level, bench.get(), "message.aliveworkplace.assign.too_far", (int) Math.ceil(distance), MAX_SITE_DISTANCE), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite other : manager.all()) {
			if (BlueprintLibrary.get(level, other.structure())
				.map(b -> BlueprintOutline.bounds(other.placement(), b.size()).intersects(box)).orElse(false)) {
				tell(player, Component.translatable("message.aliveworkplace.assign.overlaps", Blueprints.displayName(other.structure())), ChatFormatting.RED);
				return InteractionResult.CONSUME;
			}
		}

		Friends.hire(player, villager);
		if (existing != null && existing.isRepair() && existing.wonder() == null && RepairAsks.agreed(level, existing.placement())) {
			// 36.15: a repair the owner agreed to is put aside for the new job (what's put back stays, and it's still agreed:
			// the builder takes it up again when they're free)
			Component repair = Blueprints.displayName(existing.structure());
			cancel(level, existing);
			existing = activeSite(level, villager); // (their queue's next, if they had one)
			queued = existing != null ? queue(level, villager).size() : 0;
			tell(player, Component.translatable("message.aliveworkplace.repair_answer.put_aside", villager.getDisplayName(), repair), ChatFormatting.GRAY);
		}
		BuildSite site = existing == null
			? start(level, villager, player, data.get().structure(), placement.get())
			: enqueue(level, villager, player, data.get().structure(), placement.get());
		if (deconstruct) {
			site.setDeconstruction();
		}
		site.setLevelGround(data.get().levelGround());
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		if (existing == null && deconstruct) {
			tell(player, Component.translatable("message.aliveworkplace.assign.deconstruct", villager.getDisplayName(),
				Blueprints.displayName(site.structure()), SupplyContainers.RADIUS), ChatFormatting.GREEN);
		} else if (existing == null) {
			tell(player, Component.translatable("message.aliveworkplace.assign.started", villager.getDisplayName(),
				Blueprints.displayName(site.structure()), SupplyContainers.RADIUS), ChatFormatting.GREEN);
		}
		Component later = existing == null ? io.github.jcondedata.aliveworkplace.work.Jobs.startsLater(villager) : null; // B220
		if (later != null) {
			tell(player, later, ChatFormatting.YELLOW);
		}
		if (existing != null) {
			// B514: a queued take-down builds nothing and needs no materials, so it gets its own line
			tell(player, Component.translatable(deconstruct ? "message.aliveworkplace.queue.added_deconstruct" : "message.aliveworkplace.queue.added", villager.getDisplayName(),
				Blueprints.displayName(site.structure()), Blueprints.displayName(existing.structure()), queued + 1), ChatFormatting.GREEN);
		}
		return InteractionResult.SUCCESS;
	}

	/** Creates the site and gives the villager the job. Also used by tests and commands. */
	public static BuildSite start(ServerLevel level, Villager villager, @Nullable Player owner,
								  net.minecraft.resources.ResourceLocation structure, BlueprintData.Placement placement) {
		return start(level, villager, owner != null ? owner.getUUID() : villager.getUUID(),
			owner != null ? owner.getGameProfile().getName() : "", structure, placement);
	}

	/** {@link #start(ServerLevel, Villager, Player, net.minecraft.resources.ResourceLocation, BlueprintData.Placement)} for an owner who may be offline (the Steward's builds, 27.8). */
	public static BuildSite start(ServerLevel level, Villager villager, java.util.UUID owner, String ownerName,
								  net.minecraft.resources.ResourceLocation structure, BlueprintData.Placement placement) {
		BuildSite site = BuildSiteManager.get(level).create(owner, ownerName, structure, placement);
		if (Cellars.isCellarDig(level, structure, placement)) {
			site.setCellarOnly(); // 36.7: the build itself lined up over it, its cellar still ground: dig only the cellar
		}
		if (isHelping(villager)) {
			stopHelping(level, villager);
		}
		site.setBuilder(villager.getUUID());
		site.setBench(benchPos(villager).orElse(null));
		ModAttachments.BUILDER_JOB.set(villager, new BuilderJob(site.id()));
		return site;
	}

	/** Adds a site to a busy builder's queue. Also used by tests. */
	public static BuildSite enqueue(ServerLevel level, Villager villager, @Nullable Player owner,
									net.minecraft.resources.ResourceLocation structure, BlueprintData.Placement placement) {
		return enqueue(level, villager, owner != null ? owner.getUUID() : villager.getUUID(),
			owner != null ? owner.getGameProfile().getName() : "", structure, placement);
	}

	/** {@link #enqueue(ServerLevel, Villager, Player, net.minecraft.resources.ResourceLocation, BlueprintData.Placement)} for an owner who may be offline. */
	public static BuildSite enqueue(ServerLevel level, Villager villager, java.util.UUID owner, String ownerName,
									net.minecraft.resources.ResourceLocation structure, BlueprintData.Placement placement) {
		BuildSite site = BuildSiteManager.get(level).create(owner, ownerName, structure, placement);
		if (Cellars.isCellarDig(level, structure, placement)) {
			site.setCellarOnly(); // 36.7: the build itself lined up over it, its cellar still ground: dig only the cellar
		}
		site.setBuilder(villager.getUUID());
		site.setBench(benchPos(villager).orElse(null));
		site.setQueued(true);
		return site;
	}

	// --- status ------------------------------------------------------------------------------

	public static void sendStatus(Player player, Villager villager) {
		ServerLevel level = (ServerLevel) villager.level();
		BuildSite site = activeSite(level, villager);
		if (site == null) {
			tell(player, Component.translatable(benchPos(villager).isPresent()
				? "message.aliveworkplace.status.idle" : "message.aliveworkplace.status.no_bench", villager.getDisplayName()), ChatFormatting.GRAY);
			tell(player, Component.literal("  ").append(BuilderLevels.describe(villager)), ChatFormatting.DARK_AQUA);
			BenchMoves.statusLine(villager).ifPresent(line -> tell(player, Component.literal("  ").append(line), ChatFormatting.GRAY)); // B290
			return;
		}
		Chat.system(player, statusText(level, site, villager));
		if (!site.isQueued() && !site.isDeconstruction()) {
			Chat.system(player, SiteList.chat(level, site, villager)); // B279: the whole list, not just the first few
		}
	}

	public static Component statusText(ServerLevel level, BuildSite site, @Nullable Villager villager) {
		BuildPlan plan = site.plan(level);
		MutableComponent text = Component.empty();
		Component who = villager != null ? villager.getDisplayName() : Component.translatable("message.aliveworkplace.status.builder");
		if (plan == null) {
			return text.append(Component.translatable("message.aliveworkplace.status.blueprint_missing", site.structure().toString()).withStyle(ChatFormatting.RED));
		}
		if (site.isQueued()) {
			BuildSite current = villager != null ? activeSite(level, villager) : null;
			text.append(Component.translatable("message.aliveworkplace.status.queued", who, Blueprints.siteName(level, site),
				current != null ? Blueprints.siteName(level, current) : Component.literal("?")).withStyle(ChatFormatting.GOLD));
			appendCancel(text, site);
			return text;
		}
		int percent = Math.round(site.progress(plan) * 100);
		text.append(Component.translatable("message.aliveworkplace.status.header", who, Blueprints.siteName(level, site), percent)
			.withStyle(ChatFormatting.GOLD));
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.status.stage." + site.stage().name().toLowerCase()).withStyle(ChatFormatting.GRAY));
		text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		text.append(Component.translatable("message.aliveworkplace.status.state." + site.status().name().toLowerCase()).withStyle(
			site.status() == BuildSite.Status.WAITING_FOR_MATERIALS ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		if (villager != null) {
			text.append(Component.literal("\n  "));
			text.append(BuilderLevels.describe(villager).copy().withStyle(ChatFormatting.DARK_AQUA));
			Employer employer = ModAttachments.BUILDER_EMPLOYER.get(villager);
			if (employer != null) {
				text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
				text.append(Component.translatable("message.aliveworkplace.status.works_for", employer.name()).withStyle(ChatFormatting.DARK_AQUA));
			}
			BenchMoves.statusLine(villager).ifPresent(line -> text.append(Component.literal("\n  ")).append(line.copy().withStyle(ChatFormatting.GRAY)));
		}

		if (site.detail() != null) {
			text.append(Component.literal("\n  "));
			text.append(site.detail().copy().withStyle(ChatFormatting.YELLOW));
		}
		Optional<BlockPos> supplyBench = villager == null ? Optional.empty() : site.wonder() != null ? siteBench(level, villager, site) : benchPos(villager);
		if (villager != null && supplyBench.isPresent() && !site.isDeconstruction()) {
			List<BlockPos> supplies = SupplyContainers.find(level, supplyBench.get(), plan.supplyExclude());
			BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
			// B290: what the village's Storehouse and the other workers' chests can spare isn't missing either (counting the
			// bench's chests only said "Needs" for what the Storehouse held, and left that on the overhead line too)
			site.setMissing(computeMissing(level, site, plan, bag, supplies, BuilderWork.spareElsewhere(level, villager, site, plan, supplyBench.get())));
		}
		if (!site.missing().isEmpty()) {
			text.append(Component.literal("\n  "));
			text.append(Component.translatable("message.aliveworkplace.status.needs", formatMissing(site, 6)).withStyle(ChatFormatting.YELLOW));
			// B297: a Storehouse about that he doesn't count, and why (too far, not his village's, no chest it can read)
			Village.UnusedStore unused = villager == null || supplyBench.isEmpty() ? null
				: Village.unusedStorehouse(level, villager, supplyBench.get(), plan.supplyExclude());
			if (unused != null) {
				text.append(Component.literal("\n  "));
				text.append(unusedStoreLine(unused).copy().withStyle(ChatFormatting.GOLD));
			}
		}
		if (site.skipped() > 0) {
			text.append(Component.literal("\n  "));
			text.append(io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.status.skipped", site.skipped(), site.skipped()).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (villager != null) {
			List<BuildSite> queue = queue(level, villager);
			if (!queue.isEmpty()) {
				MutableComponent names = Component.empty();
				for (int i = 0; i < queue.size(); i++) {
					if (i > 0) {
						names.append(", ");
					}
					names.append(Blueprints.siteName(level, queue.get(i)));
				}
				text.append(Component.literal("\n  "));
				text.append(Component.translatable("message.aliveworkplace.status.next", names).withStyle(ChatFormatting.GRAY));
			}
		}
		appendCancel(text, site);
		return text;
	}

	/** B297: "Not counting the Storehouse at 10 64 -3: …", the reason in the player's words. */
	static Component unusedStoreLine(Village.UnusedStore unused) {
		BlockPos p = unused.storehouse();
		Component at = Component.literal(p.getX() + " " + p.getY() + " " + p.getZ());
		return switch (unused.why()) {
			case TOO_FAR -> Component.translatable("message.aliveworkplace.status.storehouse_far", at, unused.distance(), Village.RADIUS);
			case NOT_SHARED -> Component.translatable("message.aliveworkplace.status.storehouse_not_shared", at,
				unused.owner() != null ? Component.literal(unused.owner().name()) : Component.translatable("message.aliveworkplace.status.storehouse_village"));
			case NO_CHESTS -> Component.translatable("message.aliveworkplace.status.storehouse_no_chests", at, SupplyContainers.RADIUS);
		};
	}

	private static void appendCancel(MutableComponent text, BuildSite site) {
		String cancel = "/workplace cancel " + site.id();
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.status.cancel").withStyle(style -> style
			.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cancel))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.status.cancel_hover")))));
	}

	/**
	 * Materials the rest of the build takes (blocks not placed yet), grouped by material family (Chipped variants count as
	 * their plain block) and keyed by the plain block's item.
	 */
	public static Map<Item, Integer> remainingNeed(ServerLevel level, BuildSite site, BuildPlan plan) {
		return needOf(level, site.upcoming(plan, Integer.MAX_VALUE));
	}

	private static Map<Item, Integer> needOf(ServerLevel level, List<BuildPlan.Step> upcoming) {
		Map<Item, Integer> need = new LinkedHashMap<>();
		for (BuildPlan.Step step : upcoming) {
			if (!MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
				for (MaterialRules.Requirement r : step.requirements()) {
					need.merge(MaterialFamilies.key(r.item()), r.count(), Integer::sum);
				}
			}
		}
		return need;
	}

	/** How long another site's {@link #remainingNeed} is reused while that site hasn't moved on (25.3). */
	static final int NEED_CACHE_TICKS = 100;

	private record CachedNeed(long mark, long until, Map<Item, Integer> need) {
	}

	/** Server thread only; a site that's gone is dropped with it. */
	private static final Map<BuildSite, CachedNeed> NEED_CACHE = new java.util.WeakHashMap<>();

	/**
	 * {@link #remainingNeed} of a site other than the asker's, reused for up to {@link #NEED_CACHE_TICKS} while the site's
	 * {@link BuildSite#progressMark} stays the same (25.3: every waiting builder of a big village went through every other
	 * site's whole plan, block by block, which cost over 60 ms in one tick). Read-only for callers.
	 */
	public static Map<Item, Integer> remainingNeedOfOther(ServerLevel level, BuildSite site, BuildPlan plan) {
		long now = level.getGameTime();
		long mark = site.progressMark();
		CachedNeed c = NEED_CACHE.get(site);
		if (c != null && c.mark() == mark && now < c.until() && now >= c.until() - NEED_CACHE_TICKS) {
			return c.need();
		}
		if (!BuildBudget.mayRecount(level, now)) {
			// 25.4: this tick's recounts are spent. The last count stands a little longer; a site never counted keeps its
			// whole remaining plan back (more, never less, than it needs) until a later tick counts it from the world.
			return c != null ? c.need() : java.util.Collections.unmodifiableMap(plannedNeed(site, plan));
		}
		List<BuildPlan.Step> upcoming = site.upcoming(plan, Integer.MAX_VALUE);
		BuildBudget.read(level, now, upcoming.size());
		Map<Item, Integer> need = java.util.Collections.unmodifiableMap(needOf(level, upcoming));
		NEED_CACHE.put(site, new CachedNeed(mark, now + NEED_CACHE_TICKS, need));
		return need;
	}

	/** What the rest of {@code site}'s plan takes, without looking at the world: what's already in counts as needed. */
	static Map<Item, Integer> plannedNeed(BuildSite site, BuildPlan plan) {
		Map<Item, Integer> need = new LinkedHashMap<>();
		for (BuildPlan.Step step : site.upcoming(plan, Integer.MAX_VALUE)) {
			for (MaterialRules.Requirement r : step.requirements()) {
				need.merge(MaterialFamilies.key(r.item()), r.count(), Integer::sum);
			}
		}
		return need;
	}

	/**
	 * What the builds at {@code bench} (the one under way and those queued after it), other than {@code except}, still
	 * need, by family key: their builder's chests keep that much for them, so another builder only takes what is spare
	 * (B31: in a village of builders, one short of a glass pane emptied the inn's chests and left the inn waiting).
	 */
	public static Map<Item, Integer> reservedAt(ServerLevel level, BlockPos bench, @Nullable BuildSite except) {
		Map<Item, Integer> out = new java.util.HashMap<>();
		Set<UUID> builders = new java.util.HashSet<>();
		for (BuildSite other : BuildSiteManager.get(level).all()) {
			if (other == except || other.isDone() || other.isDeconstruction() || !bench.equals(other.bench())) {
				continue;
			}
			BuildPlan otherPlan = other.plan(level);
			if (otherPlan != null) {
				remainingNeedOfOther(level, other, otherPlan).forEach((item, n) -> out.merge(item, n, Integer::sum));
				if (other.builder() != null) {
					builders.add(other.builder());
				}
			}
		}
		// 23.5: what those builders already carry goes into those builds, so it needn't stay in the chests too (counting
		// it twice kept a builder's surplus from the storehouse locked up in its chests while another builder waited).
		for (UUID id : builders) {
			if (except != null && id.equals(except.builder())) {
				continue;
			}
			BuilderBag carried = level.getEntity(id) instanceof Villager v ? ModAttachments.BUILDER_BAG.get(v) : null;
			if (carried != null) {
				for (ItemStack stack : carried.stacks()) {
					if (!stack.isEmpty()) {
						out.computeIfPresent(MaterialFamilies.key(stack.getItem()), (k, n) -> n > stack.getCount() ? n - stack.getCount() : null);
					}
				}
			}
		}
		return out;
	}

	/**
	 * What the open builds other than {@code except} keep back in {@code chests}, by family key: the {@link #reservedAt}
	 * of every bench those chests are within a builder's reach of (B696: a Carpenter turned the logs stocked for one build
	 * into planks for another that shared the chest, and the first waited for ever).
	 */
	public static Map<Item, Integer> reservedIn(ServerLevel level, List<BlockPos> chests, @Nullable BuildSite except) {
		Map<Item, Integer> out = new java.util.HashMap<>();
		if (chests.isEmpty()) {
			return out;
		}
		Set<BlockPos> benches = new java.util.HashSet<>();
		for (BuildSite other : BuildSiteManager.get(level).all()) {
			BlockPos bench = other.bench();
			if (other == except || bench == null || other.isDone() || other.isDeconstruction() || benches.contains(bench)) {
				continue;
			}
			for (BlockPos chest : chests) {
				if (SupplyContainers.inReach(bench, chest)) {
					benches.add(bench);
					break;
				}
			}
		}
		for (BlockPos bench : benches) {
			reservedAt(level, bench, except).forEach((item, n) -> out.merge(item, n, Integer::sum));
		}
		return out;
	}

	/**
	 * What a village storehouse at {@code storehouse} keeps back for the other builders near it (23.5), by family key:
	 * what each other bench within the village's reach of it still needs for its builds, beyond what its own chests and
	 * its builders' bags hold. Without it a builder that could also reach another storehouse emptied this one of what
	 * the builders who can reach only this one were waiting for.
	 */
	public static Map<Item, Integer> reservedForOthers(ServerLevel level, BlockPos storehouse, @Nullable BuildSite except) {
		// Asked every tick while a builder walks to the storehouse: worked out once a second.
		ReservedKey key = new ReservedKey(level.dimension(), storehouse.immutable(), except == null ? null : except.id());
		long now = level.getGameTime();
		synchronized (RESERVED) {
			Reserved cached = RESERVED.get(key);
			if (cached != null && cached.until() > now) {
				return cached.items();
			}
			if (RESERVED.size() > 512) {
				RESERVED.clear();
			}
		}
		Map<Item, Integer> items = Map.copyOf(reservedForOthersNow(level, storehouse, except));
		synchronized (RESERVED) {
			RESERVED.put(key, new Reserved(now + 20, items));
		}
		return items;
	}

	private record ReservedKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos storehouse, @Nullable UUID site) {
	}

	private record Reserved(long until, Map<Item, Integer> items) {
	}

	private static final Map<ReservedKey, Reserved> RESERVED = new java.util.HashMap<>();

	private static Map<Item, Integer> reservedForOthersNow(ServerLevel level, BlockPos storehouse, @Nullable BuildSite except) {
		double reach = (double) Village.RADIUS * Village.RADIUS;
		BlockPos mine = except != null ? except.bench() : null;
		Map<BlockPos, Map<Item, Integer>> byBench = new java.util.HashMap<>();
		Map<BlockPos, Set<UUID>> buildersAt = new java.util.HashMap<>();
		for (BuildSite other : BuildSiteManager.get(level).all()) {
			BlockPos bench = other.bench();
			if (other == except || bench == null || bench.equals(mine) || other.isDone() || other.isDeconstruction()
				|| bench.distSqr(storehouse) > reach) {
				continue;
			}
			BuildPlan plan = other.plan(level);
			if (plan == null) {
				continue;
			}
			Map<Item, Integer> need = byBench.computeIfAbsent(bench, b -> new java.util.HashMap<>());
			remainingNeedOfOther(level, other, plan).forEach((item, n) -> need.merge(item, n, Integer::sum));
			if (other.builder() != null) {
				buildersAt.computeIfAbsent(bench, b -> new java.util.HashSet<>()).add(other.builder());
			}
		}
		Map<Item, Integer> out = new java.util.HashMap<>();
		byBench.forEach((bench, need) -> {
			Map<Item, Long> have = new java.util.HashMap<>();
			SupplyContainers.contents(level, SupplyContainers.find(level, bench, null))
				.forEach((item, n) -> have.merge(MaterialFamilies.key(item), n, Long::sum));
			for (UUID id : buildersAt.getOrDefault(bench, Set.of())) {
				BuilderBag carried = level.getEntity(id) instanceof Villager v ? ModAttachments.BUILDER_BAG.get(v) : null;
				if (carried != null) {
					for (ItemStack stack : carried.stacks()) {
						if (!stack.isEmpty()) {
							have.merge(MaterialFamilies.key(stack.getItem()), (long) stack.getCount(), Long::sum);
						}
					}
				}
			}
			need.forEach((item, n) -> {
				long short_ = n - have.getOrDefault(item, 0L);
				if (short_ > 0) {
					out.merge(item, (int) short_, Integer::sum);
				}
			});
		});
		return out;
	}

	/** How many of {@code item}'s family the {@code chests} hold beyond what is {@link #reservedAt reserved} in them. */
	public static long spare(ServerLevel level, List<BlockPos> chests, Item item, Map<Item, Integer> reserved) {
		long have = 0;
		for (Item member : MaterialFamilies.accepted(item)) {
			have += SupplyContainers.count(level, chests, member);
		}
		return Math.max(0, have - reserved.getOrDefault(MaterialFamilies.key(item), 0));
	}

	/** Materials still needed for the rest of the build, minus what the builder carries and what is in the supply chests. */
	public static Map<Item, Integer> computeMissing(ServerLevel level, BuildSite site, BuildPlan plan, BuilderBag bag, List<BlockPos> supplies) {
		return computeMissing(level, site, plan, bag, supplies, Map.of());
	}

	/** The same, counting {@code elsewhere} (by family key) as well: what other workers' chests can spare. */
	public static Map<Item, Integer> computeMissing(ServerLevel level, BuildSite site, BuildPlan plan, BuilderBag bag, List<BlockPos> supplies,
													Map<Item, Long> elsewhere) {
		Map<Item, Integer> need = remainingNeed(level, site, plan);
		// What the rest of the crew is carrying counts too: it goes into this build.
		List<BuilderBag> bags = new java.util.ArrayList<>(List.of(bag));
		List<UUID> crew = new java.util.ArrayList<>(site.helpers(level.getGameTime()));
		if (site.builder() != null) {
			crew.add(site.builder());
		}
		for (UUID id : crew) {
			if (level.getEntity(id) instanceof Villager mate) {
				BuilderBag mateBag = ModAttachments.BUILDER_BAG.getOrCreate(mate);
				if (mateBag != bag) {
					bags.add(mateBag);
				}
			}
		}
		Map<Item, Integer> missing = new LinkedHashMap<>();
		for (Map.Entry<Item, Integer> e : need.entrySet()) {
			long have = elsewhere.getOrDefault(e.getKey(), 0L);
			for (Item member : MaterialFamilies.accepted(e.getKey())) {
				have += SupplyContainers.count(level, supplies, member);
				for (BuilderBag b : bags) {
					have += b.count(member);
				}
			}
			long shortBy = e.getValue() - have;
			if (shortBy > 0) {
				missing.put(e.getKey(), (int) shortBy);
			}
		}
		return missing;
	}

	public static Component formatMissing(BuildSite site, int max) {
		MutableComponent out = Component.empty();
		List<Map.Entry<Item, Integer>> list = site.missingSorted();
		for (int i = 0; i < Math.min(max, list.size()); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(Component.literal(list.get(i).getValue() + "× ")).append(list.get(i).getKey().getDescription());
		}
		if (list.size() > max) {
			out.append(Component.translatable("message.aliveworkplace.status.and_more", list.size() - max));
		}
		return out;
	}

	static void notifyWaiting(ServerLevel level, Villager villager, BuildSite site) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		// At most once a minute per build: waiting often comes and goes while a crew passes materials around.
		if (owner != null && !site.missing().isEmpty() && site.shouldNotify(level.getGameTime(), 1200)) {
			tell(owner, Component.translatable("message.aliveworkplace.waiting", villager.getDisplayName(),
				Blueprints.siteName(level, site), formatMissing(site, 5)), ChatFormatting.YELLOW);
		}
	}

	// --- finishing / cancelling --------------------------------------------------------------

	/** A site's last step done: hands back what's left, tells the owner, records it. Also used by tests (27.21). */
	public static void finish(ServerLevel level, Villager villager, BuildSite site) {
		sendHelpersBack(level, site);
		BlockPos bench = (site.wonder() != null ? siteBench(level, villager, site) : benchPos(villager)).orElse(villager.blockPosition());
		List<BlockPos> supplies = SupplyContainers.find(level, bench, null);
		emptyBag(level, villager, bench, supplies);
		// Item frames, paintings and armor stands go up last, from the chests.
		BuildPlan plan = site.isDeconstruction() ? null : site.plan(level);
		if (site.isRepair()) {
			// A repair: nothing to hand back, nothing new to sell, the building's already on the books.
			if (plan != null) {
				// What it went for and couldn't put back (skipped: nowhere to stand within reach, say) is left be from now
				// on, or upkeep would send the builder for it again every time it looked (rc.11 playtest: a chimney's campfire).
				List<BlockPos> left = new java.util.ArrayList<>();
				for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
					for (BuildPlan.Step s : plan.steps(stage)) {
						if (BuildPlan.missing(level, s)) {
							left.add(s.pos());
						}
					}
				}
				BuildSiteManager.get(level).gaveUp(site.placement(), left);
			}
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(), 12, 0.5, 0.5, 0.5, 0.0);
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
			if (site.wonder() != null) {
				// a part of a damaged Wonder (35.8): counted again straight away, so its powers come back once enough stands
				io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep.recount(site.wonder());
				if (owner != null) {
					io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).site(site.wonder()).ifPresent(w -> tell(owner,
						Component.translatable("message.aliveworkplace.wonder.part_repaired", villager.getDisplayName(),
							io.github.jcondedata.aliveworkplace.wonder.Wonders.name(w.wonder())), ChatFormatting.GREEN));
				}
			} else if (owner != null) {
				tell(owner, Component.translatable("message.aliveworkplace.repaired", villager.getDisplayName(), Blueprints.displayName(site.structure())),
					ChatFormatting.GREEN);
			}
			if (site.wonder() == null) {
				BuildSiteManager.get(level).setRepairAsk(site.placement(), null); // 36.15: done, off the Repairs page
			}
			endJob(level, villager, site);
			BuilderLevels.addXp(level, villager, 2, site.owner());
			return;
		}
		if (io.github.jcondedata.aliveworkplace.city.Roads.isSegment(site.structure())) {
			// A road segment (27.15): marked built on the City Plan; no blueprint to hand back, not a building, no path.
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(), 8, 0.5, 0.5, 0.5, 0.0);
			io.github.jcondedata.aliveworkplace.city.Roads.segmentBuilt(level, site, villager);
			endJob(level, villager, site);
			BuilderLevels.addXp(level, villager, 2, site.owner());
			return;
		}
		int entitiesLeft = plan == null ? 0 : BuildEntities.placeAll(level, plan, supplies);
		if (plan != null) {
			lightPortals(level, plan.bounds(), supplies);
			// The village's colours over the front door, if a banner of their base colour is in the chests (30.13).
			io.github.jcondedata.aliveworkplace.hall.VillageBanners.hangOverDoor(level, plan.bounds(), supplies);
		}
		if (!io.github.jcondedata.aliveworkplace.world.VillagePieces.isOutside(site.structure())
			&& !io.github.jcondedata.aliveworkplace.city.Renewals.isScan(site.structure()) // (nor an old house's scan, 27.21)
			&& !site.isCellarOnly()) { // (nor a cellar dig's, 36.7: the build's own blueprint was the job, not a new one)
			returnBlueprint(level, site, bench, supplies); // (a house's new outside, 23.10a, came from no blueprint)
		}

		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(), 12, 0.5, 0.5, 0.5, 0.0);
		level.playSound(null, villager, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null) {
			if (site.wonder() != null) {
				// B138: a Wonder's part is told as a part of its stage (no blueprint comes back for it, 35.4)
				io.github.jcondedata.aliveworkplace.wonder.WonderStages.partFinishedLine(level, site, villager.getDisplayName())
					.ifPresent(line -> tell(owner, line, ChatFormatting.GREEN));
			} else {
				tell(owner, Component.translatable(site.isDeconstruction() ? "message.aliveworkplace.finished_deconstruct"
						: site.isCellarOnly() ? "message.aliveworkplace.cellar_dug" : "message.aliveworkplace.finished",
					villager.getDisplayName(), Blueprints.displayName(site.structure())), ChatFormatting.GREEN);
			}
			if (entitiesLeft > 0) {
				tell(owner, io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.entities_left", entitiesLeft, entitiesLeft), ChatFormatting.YELLOW);
			}
		}
		if (site.wonder() != null) {
			// B143: a Wonder's part isn't a building: no "built Stage 1 0" in the chronicle (the Wonder's own begun and
			// finished lines tell it), no record among the village's buildings, no upgrade, no lane or path to its "door"
			endJob(level, villager, site);
			BuilderLevels.onFinished(level, villager, site);
			return;
		}
		if (site.isDeconstruction()) {
			BuildSiteManager.get(level).forgetFinished(site.placement());
			io.github.jcondedata.aliveworkplace.habitat.VillageHabitats.onTakenDown(level, site.structure(), site.placement()); // 28.14
		} else {
			BuildSiteManager.get(level).recordFinished(site.structure(), site.placement(), site.owner());
			BuildSiteManager.get(level).setFurniture(site.placement(), site.isRefresh() // 37.3: upkeep keeps what was built
				? Refreshes.refreshedFurniture(level, site.structure(), site.furniture()) : site.furniture()); // B642: a refresh's, by today's spots
			io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds.finished(level, site.structure(), site.placement(), villager); // 39.4: start a windmill
			io.github.jcondedata.aliveworkplace.mechanic.Contraptions.finished(level, site, plan, villager); // 39.5: does it turn?
			io.github.jcondedata.aliveworkplace.city.Signposts.finished(level, site.structure(), site.placement()); // a Signpost's boards (36.4)
			BuiltAdvancements.award(level, site); // the questbook's build quests (none for a Wonder's part or an import)
			io.github.jcondedata.aliveworkplace.city.Walls.pieceBuilt(level, site, villager); // 27.18: the last piece finishes the wall
			io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, site.placement().origin(), io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.BUILT, Component.translatable("chronicle.aliveworkplace.built",
				villager.getDisplayName(), Blueprints.displayName(site.structure())));
			// Built something that has an upgrade: the builder sells its blueprint from now on.
			UpgradeOffers.offer(level, villager, site.structure()).ifPresent(upgrade -> {
				if (owner != null) {
					tell(owner, Component.translatable("message.aliveworkplace.upgrade_for_sale", villager.getDisplayName(),
						Blueprints.displayName(upgrade)), ChatFormatting.AQUA);
				}
			});
		}
		// The door joins the village's roads with a lane (27.15); a village without roads gets the dirt path to its bell.
		if (!io.github.jcondedata.aliveworkplace.city.Roads.joinNearest(level, site) && Paths.ENABLED) {
			Paths.afterBuild(level, villager, site);
		}
		endJob(level, villager, site);
		BuilderLevels.onFinished(level, villager, site);
		io.github.jcondedata.aliveworkplace.city.Renewals.siteFinished(level, villager, site); // 27.21: the old house down, the new one next
	}

	/**
	 * Lights the empty Nether portal frames inside {@code box} (a finished Nether Gate): a use of a flint and steel from
	 * {@code supplies} each, or a fire charge. Returns how many were lit (none without either).
	 */
	public static int lightPortals(ServerLevel level, BoundingBox box, List<BlockPos> supplies) {
		int lit = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.OBSIDIAN) || !level.getBlockState(pos.above()).isAir()) {
				continue;
			}
			for (net.minecraft.core.Direction.Axis axis : List.of(net.minecraft.core.Direction.Axis.X, net.minecraft.core.Direction.Axis.Z)) {
				Optional<net.minecraft.world.level.portal.PortalShape> shape = net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, pos.above(), axis);
				if (shape.isEmpty()) {
					continue;
				}
				ItemStack igniter = SupplyContainers.takeOne(level, supplies, s -> s.is(net.minecraft.world.item.Items.FLINT_AND_STEEL));
				if (!igniter.isEmpty()) {
					igniter.setDamageValue(igniter.getDamageValue() + 1);
					if (igniter.getDamageValue() < igniter.getMaxDamage()) {
						SupplyContainers.insert(level, supplies, igniter);
					}
				} else if (SupplyContainers.takeOne(level, supplies, s -> s.is(net.minecraft.world.item.Items.FIRE_CHARGE)).isEmpty()) {
					return lit;
				}
				shape.get().createPortalBlocks();
				level.playSound(null, pos.above(), net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, 1f);
				lit++;
				break;
			}
		}
		return lit;
	}

	/** Stops a build. Placed blocks stay; the blueprint goes back to the owner (or the bench). */
	public static void cancel(ServerLevel level, BuildSite site) {
		io.github.jcondedata.aliveworkplace.city.Renewals.siteCancelled(level, site); // 27.21
		Villager villager = site.builder() != null && level.getEntity(site.builder()) instanceof Villager v ? v : null;
		if (site.isQueued()) {
			// Not started: nothing to tidy up on the builder, just hand the blueprint back.
			BlockPos at = villager != null ? benchPos(villager).orElse(villager.blockPosition()) : site.placement().origin();
			giveBack(level, site, at, villager != null ? SupplyContainers.find(level, at, null) : List.of());
			BuildSiteManager.get(level).remove(site.id());
			return;
		}
		sendHelpersBack(level, site);
		BlockPos bench = villager != null ? benchPos(villager).orElse(villager.blockPosition()) : site.placement().origin();
		List<BlockPos> supplies = villager != null ? SupplyContainers.find(level, bench, null) : List.of();
		if (villager != null) {
			emptyBag(level, villager, bench, supplies);
		}
		giveBack(level, site, bench, supplies);
		if (villager != null) {
			endJob(level, villager, site);
		} else {
			BuildSiteManager.get(level).remove(site.id());
		}
	}

	/** The blueprint goes to the owner if online, else into the supply chests, else on the ground. */
	private static void giveBack(ServerLevel level, BuildSite site, BlockPos bench, List<BlockPos> supplies) {
		if (site.wonder() != null) {
			return; // a Wonder's part (35.4): the Wonder Plan comes back instead (WonderStages.cancel)
		}
		if (site.isSteward() || site.isRepair() || io.github.jcondedata.aliveworkplace.world.VillagePieces.isOutside(site.structure())) {
			return; // the Steward's build (27.8), a repair (36.15) or a house's new outside (23.10a): nobody handed a blueprint over, so none comes back
		}
		// Still placed where it was (23.8): hand it back to carry on there, or click the ground to move it first.
		ItemStack blueprint = blueprintFor(level, site);
		// B513: a take-down's blueprint keeps its placement too (the cancel line promises it's still placed there)
		if (!site.isRepair()) {
			io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(blueprint).ifPresent(data -> blueprint.set(
				io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, data.withPlacement(Optional.of(site.placement()))
					.withLevelGround(site.levelGround()).withMirrored(site.placement().mirror() != net.minecraft.world.level.block.Mirror.NONE)
					.withTakeDown(site.isDeconstruction()))); // B526: handed back as it is, the take-down carries on
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null && owner.getInventory().add(blueprint)) {
			return;
		}
		ItemStack rest = SupplyContainers.insert(level, supplies, blueprint);
		if (!rest.isEmpty()) {
			dropNear(level, bench, rest);
		}
	}

	/** The builder died: drop its bag and the blueprint where it fell, keep what was built. */
	public static void onBuilderDeath(ServerLevel level, Villager villager) {
		for (BuildSite queued : queue(level, villager)) {
			if (queued.wonder() != null) {
				// 35.5: a Wonder's part waiting for this builder goes to another; its site stays (a part whose site is
				// gone counts as built, so removing it finished the Wonder with the part never put up)
				io.github.jcondedata.aliveworkplace.wonder.WonderCrews.handOn(level, queued, villager);
				continue;
			}
			if (keptForTheNext(queued)) {
				leaveForTheNext(queued, villager);
				continue;
			}
			if (!queued.isSteward() && !io.github.jcondedata.aliveworkplace.world.VillagePieces.isOutside(queued.structure())) {
				dropNear(level, villager.blockPosition(), blueprintFor(level, queued));
			}
			BuildSiteManager.get(level).remove(queued.id());
		}
		BuilderJob job = ModAttachments.BUILDER_JOB.get(villager);
		BuildSite site = job != null ? BuildSiteManager.get(level).get(job.siteId()) : null;
		BuilderBag bag = ModAttachments.BUILDER_BAG.get(villager);
		if (bag != null) {
			for (ItemStack stack : bag.takeAll()) {
				dropNear(level, villager.blockPosition(), stack);
			}
		}
		if (site != null && site.wonder() != null) {
			// 35.5: a crew member's death leaves its part to the rest of the crew; the lead's hands the part on (its site
			// stays: a part whose site is gone counts as built, and a lead that fell off the dome finished the Wonder
			// with the dome's top never put up).
			ModAttachments.BUILDER_JOB.remove(villager);
			if (!job.helper() && villager.getUUID().equals(site.builder()) && !site.isDone()) {
				io.github.jcondedata.aliveworkplace.wonder.WonderCrews.handOn(level, site, villager);
			} else {
				site.release(villager.getUUID());
			}
		} else if (site != null && job.helper()) {
			// B516: a helper's death leaves the build to its lead and the rest of the crew; the steps it had claimed go
			// back to them (its bag dropped above), and the lead's site, blueprint and owner are left alone.
			ModAttachments.BUILDER_JOB.remove(villager);
			site.release(villager.getUUID());
		} else if (site != null) {
			if (!site.isSteward() && !io.github.jcondedata.aliveworkplace.world.VillagePieces.isOutside(site.structure())) {
				dropNear(level, villager.blockPosition(), blueprintFor(level, site));
			}
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
			if (owner != null) {
				BlockPos p = villager.blockPosition();
				tell(owner, Component.translatable("message.aliveworkplace.builder_died", Blueprints.siteName(level, site), p.getX(), p.getY(), p.getZ()), ChatFormatting.RED);
			}
			ModAttachments.BUILDER_JOB.remove(villager);
			if (keptForTheNext(site)) {
				leaveForTheNext(site, villager); // B488: the hall's round hands it to the next free builder
			} else {
				BuildSiteManager.get(level).remove(site.id());
			}
		}
	}

	/**
	 * B488: whether a site whose builder died stays open for the next one: a wall piece the Steward opened. Dropped, a
	 * half-built tower had neither a site nor a finished record, so the hall laid it out again on its own roof and a
	 * second tower went up on the first, which never finished.
	 */
	private static boolean keptForTheNext(BuildSite site) {
		return site.isSteward() && !site.isDone() && io.github.jcondedata.aliveworkplace.city.Walls.isPiece(site.structure());
	}

	/** The site keeps everything built and found so far, with nobody on it until {@link #takeOver} gives it to another. */
	private static void leaveForTheNext(BuildSite site, Villager dead) {
		site.release(dead.getUUID());
		site.setBuilder(null);
		site.setQueued(false);
	}

	/**
	 * {@code villager} takes over {@code site}, a build with nobody on it (its builder died, B488): it carries on where
	 * the site stands, from the supplies at its own bench.
	 */
	public static void takeOver(ServerLevel level, Villager villager, BuildSite site) {
		if (isHelping(villager)) {
			stopHelping(level, villager);
		}
		site.release(villager.getUUID());
		site.setBuilder(villager.getUUID());
		site.setBench(benchPos(villager).orElse(null));
		site.setQueued(false);
		ModAttachments.BUILDER_JOB.set(villager, new BuilderJob(site.id()));
	}

	private static void endJob(ServerLevel level, Villager villager, BuildSite site) {
		BuildSiteManager.get(level).remove(site.id());
		ModAttachments.BUILDER_JOB.remove(villager);
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	private static void emptyBag(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> supplies) {
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		List<BlockPos> store = null;
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, supplies, stack);
			if (!rest.isEmpty()) {
				// 23.5: our chests are full (or there are none): the village storehouse's, before the ground.
				if (store == null) {
					store = storehouseChests(level, villager, bench, null);
				}
				rest = SupplyContainers.insert(level, store, rest);
			}
			if (!rest.isEmpty()) {
				dropNear(level, bench, rest);
			}
		}
	}

	/**
	 * The chests of the nearest village storehouse (a porter's workstation) {@code villager} shares with, not counting
	 * any inside {@code exclude}; empty when there is none in reach or the village is off.
	 */
	public static List<BlockPos> storehouseChests(ServerLevel level, Villager villager, BlockPos bench, @Nullable BoundingBox exclude) {
		for (Village.Stash stash : Village.stashes(level, villager, bench, exclude)) {
			if (stash.job() == ModVillagers.PORTER && !stash.chests().isEmpty()) {
				return stash.chests();
			}
		}
		return List.of();
	}

	private static void returnBlueprint(ServerLevel level, BuildSite site, BlockPos bench, List<BlockPos> supplies) {
		if (site.wonder() != null) {
			return; // a Wonder's part (35.4): nobody handed its blueprint over, the Wonder Plan was
		}
		ItemStack blueprint = blueprintFor(level, site);
		MaterialLedger.gained(blueprint);
		ItemStack rest = SupplyContainers.insert(level, supplies, blueprint);
		if (!rest.isEmpty()) {
			dropNear(level, bench, rest);
		}
	}

	private static ItemStack blueprintFor(ServerLevel level, BuildSite site) {
		return BlueprintItem.create(site.structure(), BlueprintLibrary.get(level, site.structure()).map(Blueprint::size).orElse(null));
	}

	static void dropNear(ServerLevel level, BlockPos pos, ItemStack stack) {
		MaterialLedger.dropped(stack);
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	static void tell(Player player, Component message, ChatFormatting color) {
		Chat.system(player, message.copy().withStyle(color));
	}

	/**
	 * Makes {@code villager} a builder working at {@code bench} right away (the bench block must already be
	 * there). Normal play does this through vanilla job-site claiming; tests and admin commands use this.
	 */
	public static void employ(ServerLevel level, Villager villager, BlockPos bench) {
		// A bench put down this tick has no record yet (the level files it after the tick's work): without one the take
		// below holds nothing, the bench stays free to any jobless builder, and the two then fight over it (B132).
		if (level.getPoiManager().getType(bench).isEmpty()) {
			net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(level.getBlockState(bench))
				.ifPresent(type -> level.getPoiManager().add(bench.immutable(), type));
		}
		level.getPoiManager().take(h -> true, (h, p) -> p.equals(bench), bench, 1); // a Blueprint Table, or an old Builder's Bench
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), bench));
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.order(villager, ModVillagers.BUILDER);
		if (villager.getVillagerXp() == 0) {
			villager.setVillagerXp(1); // keeps the profession even if the bench is briefly missing
		}
		villager.refreshBrain(level);
	}

	/**
	 * Who may cancel a build: whoever handed it over and their friends, the builder's employer (and
	 * theirs), and operators — or anyone when workplaceBuilderOwnership is off.
	 */
	public static boolean isOwnerOrOp(Entity entity, BuildSite site) {
		if (entity.getUUID().equals(site.owner())) {
			return true;
		}
		if (!(entity instanceof ServerPlayer p)) {
			return false;
		}
		if (p.hasPermissions(2) || !Rules.on(Players.level(p), ModGameRules.BUILDER_OWNERSHIP)
			|| Friends.get(p.level().getServer()).mayDirect(site.owner(), p.getUUID())) {
			return true;
		}
		return site.builder() != null && Players.level(p).getEntity(site.builder()) instanceof Villager builder
			&& ModAttachments.BUILDER_EMPLOYER.get(builder) != null && Friends.mayCommand(p, builder);
	}

	private Builders() {
	}
}
