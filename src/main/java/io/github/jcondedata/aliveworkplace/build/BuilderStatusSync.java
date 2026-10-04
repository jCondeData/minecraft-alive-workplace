package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells nearby players what each builder is doing, once a second, so the client can show it above
 * the builder's head: the build's name and percentage, a progress bar, and one line of status. Under it, for the lead
 * builder (23.3): the 3 materials the build is shortest of with their counts (or that it has everything), and where it
 * takes them from, so one look says what a build needs without opening a screen.
 */
public final class BuilderStatusSync {
	public static final int INTERVAL = 20;
	/** How often the materials lines are worked out again (they count the whole rest of the build and every chest). */
	public static final int SUPPLY_INTERVAL = 100;
	/** How many of the missing materials the overhead names. */
	public static final int SHOWN_MISSING = 3;

	private record Supply(long at, List<Component> lines) {
	}

	private static final Map<UUID, Supply> SUPPLY = new HashMap<>();

	/** S2C: status of one builder. The client forgets it if no update arrives for a few seconds. */
	public record Status(int entityId, Component title, float progress, Component line, List<Component> more) implements CustomPacketPayload {
		public static final Type<Status> TYPE = new Type<>(AliveWorkplace.id("builder_status"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Status::entityId,
			ComponentSerialization.STREAM_CODEC, Status::title,
			ByteBufCodecs.FLOAT, Status::progress,
			ComponentSerialization.STREAM_CODEC, Status::line,
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(4)), Status::more,
			Status::new);

		public Status(int entityId, Component title, float progress, Component line) {
			this(entityId, title, progress, line, List.of());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		Platform.get().clientbound(Status.TYPE, Status.CODEC);
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % INTERVAL == 0 && !level.players().isEmpty()) {
				broadcast(level);
				broadcastQuarries(level);
				for (var e : io.github.jcondedata.aliveworkplace.work.WorkerStatus.fresh(level.getGameTime()).entrySet()) {
					if (e.getKey().level() == level && !e.getKey().isSleeping()) {
						send(e.getKey(), new Status(e.getKey().getId(), e.getValue().title(), e.getValue().progress(), e.getValue().line()));
					}
				}
			}
		});
	}

	private static void broadcast(ServerLevel level) {
		if (level.getGameTime() % SUPPLY_INTERVAL == 0) {
			java.util.Set<UUID> live = new java.util.HashSet<>();
			for (ServerLevel any : level.getServer().getAllLevels()) {
				for (BuildSite site : BuildSiteManager.get(any).all()) {
					live.add(site.id());
				}
			}
			SUPPLY.keySet().retainAll(live);
		}
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.builder() == null || site.isQueued()) {
				continue;
			}
			Entity entity = level.getEntity(site.builder());
			if (!(entity instanceof Villager villager) || villager.isSleeping()) {
				continue;
			}
			Status status = status(level, site, villager);
			if (status == null) {
				continue;
			}
			send(villager, status);
			for (java.util.UUID helperId : site.helpers(level.getGameTime())) {
				if (level.getEntity(helperId) instanceof Villager helper && !helper.isSleeping()) {
					send(helper, new Status(helper.getId(), status.title(), status.progress(),
						Component.translatable("message.aliveworkplace.status.helping", villager.getDisplayName()).withStyle(ChatFormatting.GRAY)));
				}
			}
		}
	}

	private static void broadcastQuarries(ServerLevel level) {
		for (io.github.jcondedata.aliveworkplace.mine.QuarrySite site : io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(level).all()) {
			if (site.miner() == null || !(level.getEntity(site.miner()) instanceof Villager miner) || miner.isSleeping()) {
				continue;
			}
			var box = site.box();
			Component title = Component.translatable("message.aliveworkplace.overhead.quarry", box.getXSpan(), box.getZSpan(), box.getYSpan(),
				Math.round(site.progress() * 100));
			Component line = Component.translatable("message.aliveworkplace.quarry.state." + site.status().name().toLowerCase())
				.withStyle(site.status() == io.github.jcondedata.aliveworkplace.mine.QuarrySite.Status.NEEDS_PICKAXE ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
			send(miner, new Status(miner.getId(), title, site.progress(), line));
		}
	}

	private static void send(Villager villager, Status status) {
		java.util.List<Component> partners = io.github.jcondedata.aliveworkplace.work.Partners.helpers(villager);
		if (!partners.isEmpty()) {
			status = new Status(status.entityId(), status.title(), status.progress(), status.line().copy()
				.append(Component.translatable("message.aliveworkplace.partners.overhead", io.github.jcondedata.aliveworkplace.work.Partners.names(partners))
					.withStyle(ChatFormatting.GREEN)), status.more());
		}
		for (ServerPlayer player : Platform.get().tracking(villager)) {
			if (Platform.get().canSend(player, Status.TYPE)) {
				Platform.get().send(player, status);
			}
		}
	}

	/** The status shown above a builder's head, or null if there is nothing to show. */
	public static Status status(ServerLevel level, BuildSite site, Villager villager) {
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			return null;
		}
		float progress = site.progress(plan);
		Component title = Component.translatable("message.aliveworkplace.overhead.title",
			Blueprints.displayName(site.structure()), Math.round(progress * 100));
		Component line;
		if (site.detail() != null) {
			line = site.detail().copy().withStyle(ChatFormatting.YELLOW);
		} else if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !site.missing().isEmpty()) {
			line = Component.translatable("message.aliveworkplace.status.needs", Builders.formatMissing(site, 2)).withStyle(ChatFormatting.YELLOW);
		} else if (site.status() == BuildSite.Status.FETCHING) {
			line = Component.translatable("message.aliveworkplace.status.state.fetching").withStyle(ChatFormatting.GRAY);
		} else {
			line = Component.translatable("message.aliveworkplace.status.stage." + site.stage().name().toLowerCase()).withStyle(ChatFormatting.GRAY);
		}
		return new Status(villager.getId(), title, progress, line, supplyLines(level, site, plan, villager));
	}

	/**
	 * The lines under the status: the 3 materials the build is shortest of (counting the builders' chests and what the
	 * crew carries) or that it has them all, then which chests or storehouse it takes from. Worked out every
	 * {@link #SUPPLY_INTERVAL} ticks. None with free materials, for deconstruction and repairs of nothing.
	 */
	static List<Component> supplyLines(ServerLevel level, BuildSite site, BuildPlan plan, Villager villager) {
		if (io.github.jcondedata.aliveworkplace.mc.Rules.on(level, io.github.jcondedata.aliveworkplace.registry.ModGameRules.FREE_MATERIALS)
				|| site.stage() == BuildPlan.Stage.DECONSTRUCT || site.stage() == BuildPlan.Stage.DONE) {
			return List.of();
		}
		Supply cached = SUPPLY.get(site.id());
		long now = level.getGameTime();
		if (cached != null && now - cached.at() < SUPPLY_INTERVAL && now >= cached.at()) {
			return cached.lines();
		}
		List<Component> lines = new ArrayList<>();
		BlockPos bench = Builders.siteBench(level, villager, site).orElse(site.bench());
		List<BlockPos> supplies = bench == null ? List.of() : SupplyContainers.find(level, bench, plan.bounds());
		Map<Item, Integer> missing = Builders.computeMissing(level, site, plan,
			io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(villager), supplies);
		lines.add(missingLine(missing));
		if (bench != null) {
			lines.add(sourcesLine(level, villager, bench, supplies, plan));
		}
		SUPPLY.put(site.id(), new Supply(now, List.copyOf(lines)));
		return lines;
	}

	/** "Short of: 40× Glass, 12× Oak Planks, 3× Lantern and 2 more", largest first, or that it has everything. */
	static Component missingLine(Map<Item, Integer> missing) {
		if (missing.isEmpty()) {
			return Component.translatable("message.aliveworkplace.overhead.has_all").withStyle(ChatFormatting.GREEN);
		}
		List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(missing.entrySet());
		sorted.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
		net.minecraft.network.chat.MutableComponent list = Component.empty();
		for (int i = 0; i < Math.min(SHOWN_MISSING, sorted.size()); i++) {
			if (i > 0) {
				list.append(", ");
			}
			list.append(Component.literal(sorted.get(i).getValue() + "× ")).append(sorted.get(i).getKey().getDescription());
		}
		if (sorted.size() > SHOWN_MISSING) {
			list.append(Component.translatable("message.aliveworkplace.status.and_more", sorted.size() - SHOWN_MISSING));
		}
		return Component.translatable("message.aliveworkplace.overhead.missing", list).withStyle(ChatFormatting.YELLOW);
	}

	/**
	 * "Takes from: 3 chests by its bench at 10 64 -3", plus ", and the storehouse at …" when the village has a porter's
	 * storehouse it may take from (what its own chests are short of comes from there), or that it has no chests.
	 */
	static Component sourcesLine(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> supplies, BuildPlan plan) {
		Component where = at(bench);
		BlockPos storehouse = null;
		for (io.github.jcondedata.aliveworkplace.work.Village.Stash stash
				: io.github.jcondedata.aliveworkplace.work.Village.stashes(level, villager, bench, plan.bounds())) {
			if (stash.job() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.PORTER) {
				storehouse = stash.station();
				break;
			}
		}
		if (supplies.isEmpty()) {
			return storehouse == null
				? Component.translatable("message.aliveworkplace.overhead.no_supplies", where).withStyle(ChatFormatting.RED)
				: Component.translatable("message.aliveworkplace.overhead.storehouse_only", where, at(storehouse)).withStyle(ChatFormatting.GRAY);
		}
		Component chests = Component.translatable(supplies.size() == 1 ? "message.aliveworkplace.overhead.chest" : "message.aliveworkplace.overhead.chests",
			supplies.size());
		return (storehouse == null
			? Component.translatable("message.aliveworkplace.overhead.supplies", chests, where)
			: Component.translatable("message.aliveworkplace.overhead.supplies_storehouse", chests, where, at(storehouse))).withStyle(ChatFormatting.GRAY);
	}

	private static Component at(BlockPos pos) {
		return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
	}

	private BuilderStatusSync() {
	}
}
