package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * A Wonder's crew (35.5): every builder whose Blueprint Table is within the Wonder's village joins it when they have no
 * build of their own, up to {@code wonderCrew} (and the Wonder's own {@code crew}); once the owner calls every builder
 * ({@link WonderSite#called}), the busy ones come too as they finish what they're on. Members work the current stage's
 * parts as helpers ({@link BuilderJob#helper}, saved on each builder), each on a column of its own (BuilderWork); the
 * lead is the Master Architect when there is one, else the crew's highest-level builder. Nothing here is saved apart
 * from the jobs and the Wonder's lead and call, so a crew comes back together after a restart by itself.
 */
public final class WonderCrews {
	/** Most builders on one Wonder's crew, lead included (config {@code wonderCrew}). */
	public static volatile int LIMIT = 12;

	/** How many builders {@code site} may have on its crew: {@link #LIMIT}, and no more than the Wonder's file allows. */
	public static int limit(WonderSite site) {
		Wonder wonder = Wonders.loaded().get(site.wonder());
		return Math.max(1, Math.min(LIMIT, wonder == null ? LIMIT : wonder.crew()));
	}

	/**
	 * How many builders {@code site} may have on its crew in its village: {@link #limit(WonderSite)}, and 2 more a level of
	 * the village's Engineering while its Great Library stands (35.14), no more than {@link Wonders#MAX_CREW}.
	 */
	public static int limit(ServerLevel level, WonderSite site) {
		int base = limit(site);
		if (!(level.getBlockEntity(site.hall()) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall)) {
			return base;
		}
		int engineering = io.github.jcondedata.aliveworkplace.research.Research.bonus(level, site.hall(), hall.research(),
			io.github.jcondedata.aliveworkplace.research.Research.Topic.ENGINEERING);
		return engineering <= 0 ? base : Math.min(Math.max(base, Wonders.MAX_CREW), base + 2 * engineering);
	}

	/** The builders on {@code site}'s crew now: the lead and every member seen working on the current stage's parts. */
	public static List<UUID> crew(ServerLevel level, WonderSite site) {
		List<UUID> out = new ArrayList<>();
		BuildSiteManager sites = BuildSiteManager.get(level);
		site.builder().ifPresent(out::add);
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			if (build == null || build.isDone()) {
				continue;
			}
			if (build.builder() != null && !out.contains(build.builder())) {
				out.add(build.builder());
			}
			for (UUID helper : build.helpers(level.getGameTime())) {
				if (!out.contains(helper)) {
					out.add(helper);
				}
			}
		}
		return out;
	}

	/**
	 * {@code villager}, a builder with nothing on hand, joins the crew of a Wonder going up in its village, if one has room
	 * (with builds of its own queued, only a Wonder that called every builder). Returns the part it joins, or null.
	 */
	@Nullable
	public static BuildSite join(ServerLevel level, Villager villager) {
		if (!Wonders.ENABLED) {
			return null;
		}
		Collection<WonderSite> wonders = WonderManager.get(level).sites();
		if (wonders.isEmpty()) {
			return null;
		}
		boolean anyCalled = wonders.stream().anyMatch(w -> w.goingUp() && w.called());
		if (!anyCalled && villager.tickCount % 20 != 0) {
			return null; // nothing calls: an idle builder looks once a second
		}
		Optional<BlockPos> bench = Builders.benchPos(villager);
		if (bench.isEmpty()) {
			return null;
		}
		List<BuildSite> queue = Builders.queue(level, villager);
		if (queue.stream().anyMatch(q -> q.wonder() != null)) {
			return null; // it leads a Wonder's part: it starts that, not another's
		}
		boolean free = queue.isEmpty();
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (WonderSite wonder : wonders) {
			if (!wonder.goingUp() || wonder.placement().isEmpty() || !free && !wonder.called()
				|| bench.get().distSqr(wonder.hall()) > (double) VillageHalls.RADIUS * VillageHalls.RADIUS
				|| crew(level, wonder).size() >= limit(level, wonder)) {
				continue;
			}
			BuildSite best = null;
			for (UUID id : wonder.parts()) {
				BuildSite part = sites.get(id);
				if (part == null || part.isDone() || part.isQueued() || villager.getUUID().equals(part.builder())) {
					continue;
				}
				if (best == null || part.helpers(level.getGameTime()).size() < best.helpers(level.getGameTime()).size()) {
					best = part;
				}
			}
			if (best != null) {
				MEMBERS.computeIfAbsent(wonder.id(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(villager.getUUID());
				if (best.builder() == null) {
					// a part whose lead died with nobody on its crew (handOn): the first builder to come leads it
					best.setBuilder(villager.getUUID());
					ModAttachments.BUILDER_JOB.set(villager, new BuilderJob(best.id(), false));
					if (wonder.builder().isEmpty()) {
						WonderManager.get(level).setBuilder(wonder.id(), villager.getUUID()); // B467: the Wonder's lead too
					}
					return best;
				}
				ModAttachments.BUILDER_JOB.set(villager, new BuilderJob(best.id(), true));
				best.seen(villager.getUUID(), level.getGameTime());
				return best;
			}
		}
		return null;
	}

	/**
	 * Who leads {@code site}'s next stage: the Master Architect settled in its village if they are one of its builders,
	 * else the highest-level of {@code crew} (ties: the first, so the lead so far keeps it), else {@code fallback}.
	 */
	@Nullable
	public static UUID lead(ServerLevel level, WonderSite site, List<UUID> crew, @Nullable UUID fallback) {
		Optional<Villager> architect = Legends.in(level, site.hall(), Wonders.MASTER_ARCHITECT);
		if (architect.isPresent() && Builders.isBuilder(architect.get()) && Builders.benchPos(architect.get()).isPresent()) {
			return architect.get().getUUID();
		}
		UUID best = null;
		int bestLevel = -1;
		for (UUID id : crew) {
			if (level.getEntity(id) instanceof Villager v && v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).isPresent()) {
				int l = BuilderLevels.level(v);
				if (l > bestLevel) {
					best = id;
					bestLevel = l;
				}
			}
		}
		return best != null ? best : fallback;
	}

	/** Who has joined each Wonder's crew since the server started (by Wonder id): who a next stage's lead is chosen from. */
	private static final java.util.Map<UUID, java.util.Set<UUID>> MEMBERS = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * {@code dead}, the lead of (or next in line for) the Wonder's part {@code part}, died (35.5): the part goes to the
	 * highest-level member of its crew still on it, else to the lead the Wonder would choose for a next stage (in their
	 * queue), else it waits, unled, for the next builder to join. The part's site stays either way: a part whose site is
	 * gone counts as built.
	 */
	public static void handOn(ServerLevel level, BuildSite part, Villager dead) {
		UUID best = null;
		int bestLevel = -1;
		for (UUID id : part.helpers(level.getGameTime())) {
			if (!id.equals(dead.getUUID()) && level.getEntity(id) instanceof Villager v && v.isAlive() && Builders.isBuilder(v)) {
				int l = BuilderLevels.level(v);
				if (l > bestLevel) {
					best = id;
					bestLevel = l;
				}
			}
		}
		if (best != null && level.getEntity(best) instanceof Villager v) {
			part.release(best);
			part.setBuilder(best);
			part.setQueued(false);
			ModAttachments.BUILDER_JOB.set(v, new BuilderJob(part.id(), false));
			succeed(level, part, dead, best);
			return;
		}
		UUID next = part.wonder() == null ? null : WonderManager.get(level).site(part.wonder())
			.map(w -> lead(level, w, candidates(w, null), null)).orElse(null);
		if (next != null && !next.equals(dead.getUUID())) {
			part.setBuilder(next);
			part.setQueued(true); // in their queue: picked up when they finish what they're on
			succeed(level, part, dead, next);
		} else {
			part.setBuilder(null);
			part.setQueued(false);
			succeed(level, part, dead, null);
		}
	}

	/**
	 * B467: when {@code gone} was the Wonder's own lead, whoever took {@code part} over leads it now (named on the stone
	 * and the hall, and the one counted on its crew); with nobody yet, it has no lead until the next builder joins.
	 */
	private static void succeed(ServerLevel level, BuildSite part, Villager gone, @Nullable UUID successor) {
		if (part.wonder() == null) {
			return;
		}
		WonderManager manager = WonderManager.get(level);
		Optional<WonderSite> wonder = manager.site(part.wonder());
		if (wonder.isEmpty() || !wonder.get().builder().equals(Optional.of(gone.getUUID()))) {
			return;
		}
		if (successor != null) {
			manager.setBuilder(part.wonder(), successor);
		} else {
			manager.clearBuilder(part.wonder());
		}
	}

	/**
	 * Who {@code site}'s next stage's lead is chosen from: {@code lead} (the lead so far) first, then the builders who
	 * have worked on its crew (since the server started: after a restart, until they join again, the lead keeps it).
	 */
	public static List<UUID> candidates(WonderSite site, @Nullable UUID lead) {
		List<UUID> out = new ArrayList<>();
		if (lead != null) {
			out.add(lead);
		}
		for (UUID member : MEMBERS.getOrDefault(site.id(), java.util.Set.of())) {
			if (!out.contains(member)) {
				out.add(member);
			}
		}
		return out;
	}

	/** The stone of the Wonder {@code villager} has worked on the crew of (its chests are the stockpile), if any. */
	public static Optional<BlockPos> stockpile(ServerLevel level, Villager villager) {
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (MEMBERS.getOrDefault(site.id(), java.util.Set.of()).contains(villager.getUUID()) && site.stone().isPresent()) {
				return site.stone();
			}
		}
		return Optional.empty();
	}

	/** Forgets who worked on an abandoned Wonder. */
	public static void forget(UUID wonder) {
		MEMBERS.remove(wonder);
	}

	/** The lead's name, for the stone and the hall: their name if they're loaded, else "?". */
	public static Component leadName(ServerLevel level, WonderSite site) {
		return site.builder().map(level::getEntity).map(e -> (Component) e.getDisplayName()).orElse(Component.literal("?"));
	}

	/**
	 * The owner (or an operator) calls every builder of the village to {@code site}'s crew: the busy ones come as they
	 * finish what they're on. Saved with the Wonder.
	 */
	public static void callEveryone(ServerLevel level, WonderSite site) {
		WonderManager.get(level).setCalled(site.id(), true);
	}

	/** How near its stone a player must be to call a Wonder's builders ({@code /workplace wonders call}). */
	public static final int CALL_REACH = 8;

	/**
	 * {@code /workplace wonders call}, the stone's "Call every builder": the owner (or an operator) of the Wonder whose
	 * stone is within {@link #CALL_REACH} blocks calls every builder of the village to its crew. Returns 1 if called.
	 */
	public static int call(net.minecraft.server.level.ServerPlayer player) {
		ServerLevel level = io.github.jcondedata.aliveworkplace.mc.Players.level(player);
		Optional<WonderSite> found = WonderManager.get(level).sites().stream()
			.filter(w -> w.goingUp() && w.stone().map(s -> s.distSqr(player.blockPosition()) <= CALL_REACH * CALL_REACH).orElse(false))
			.findFirst();
		if (found.isEmpty() || !Wonders.ENABLED) {
			io.github.jcondedata.aliveworkplace.mc.Chat.system(player, Component.translatable("message.aliveworkplace.wonder.call.none", CALL_REACH)
				.withStyle(net.minecraft.ChatFormatting.RED));
			return 0;
		}
		WonderSite site = found.get();
		if (!site.owner().map(player.getUUID()::equals).orElse(false) && !player.hasPermissions(2)) {
			io.github.jcondedata.aliveworkplace.mc.Chat.system(player, Component.translatable("message.aliveworkplace.wonder.call.not_yours",
				Wonders.name(site.wonder()), site.ownerName().isEmpty() ? "?" : site.ownerName()).withStyle(net.minecraft.ChatFormatting.RED));
			return 0;
		}
		callEveryone(level, site);
		io.github.jcondedata.aliveworkplace.mc.Chat.system(player, Component.translatable("message.aliveworkplace.wonder.call.done",
			Wonders.name(site.wonder()), limit(level, site)).withStyle(net.minecraft.ChatFormatting.GREEN));
		return 1;
	}

	private WonderCrews() {
	}
}
