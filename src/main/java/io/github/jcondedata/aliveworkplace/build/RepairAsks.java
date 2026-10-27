package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Builder asks before repairing a house (ROADMAP 36.15). A builder who finds blocks missing from a finished build
 * ({@link Upkeep}) no longer sets about it by themselves: the build is put on the list with a question for its owner,
 * "Repair? Yes / Not now", in chat and on the Village Hall's Repairs page ({@code hall/RepairsPage}), and only a build
 * the owner (or a friend, the village's leader or an operator) said yes to is repaired. A repair going on can be paused
 * or stopped from the same page; the builder ends it there and then and goes straight on to their next job, and a
 * builder handed a new blueprint mid-repair puts the repair aside for it ({@link Builders#assign}), carrying on with it
 * afterwards. {@code repairAsk} off ({@link Upkeep#ASK}) keeps the old automatic repairs.
 *
 * <p>Not asked: what threats took (32.16a, {@code ThreatData#damaged}: a gate a ram broke, a block a village fire
 * burned), which is put back by itself as before, and a damaged Wonder's parts (35.8). The answers are saved with the
 * finished building ({@link BuildSiteManager#repairAsk}); a building with nothing missing any more comes off the list.
 */
public final class RepairAsks {
	/** Where a build's question stands. */
	public enum State {
		/** Asked, waiting for an answer. */
		ASKED,
		/** Yes: a builder repairs it when they're free. */
		YES,
		/** Not now: left as it is; asked again only once more goes missing. */
		NOT_NOW,
		/** Paused: left as it is until the owner says to carry on. */
		PAUSED;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Optional<State> of(String key) {
			for (State s : values()) {
				if (s.key().equals(key)) {
					return Optional.of(s);
				}
			}
			return Optional.empty();
		}
	}

	/** A build's question: its state, and the blocks missing when it was last looked at. Saved with the finished building. */
	public record Ask(State state, int missing) {
	}

	/** A build on the Repairs page: the finished building, its question (null: none, for a repair a threat started) and its repair going on. */
	public record Entry(BuildSiteManager.Finished building, @Nullable Ask ask, @Nullable BuildSite repair) {
	}

	/** What an answer came to. */
	public enum Outcome {
		YES, NOT_NOW, PAUSED, NOT_YOURS, GONE;

		public boolean ok() {
			return this == YES || this == NOT_NOW || this == PAUSED;
		}
	}

	/**
	 * {@link Upkeep} found {@code missing} blocks gone from {@code f}, a building nothing threatened: whether a builder may
	 * set about it now. Asks the first time (and again once more is missing than when the owner said not now); false while
	 * the question waits, after "not now" and while paused.
	 */
	static boolean mayStart(ServerLevel level, BuildSiteManager manager, BuildSiteManager.Finished f, int missing, Villager builder) {
		Ask ask = manager.repairAsk(f.placement());
		if (ask != null && ask.state() == State.YES) {
			return true;
		}
		if (ask == null || ask.state() == State.NOT_NOW && missing > ask.missing()) {
			manager.setRepairAsk(f.placement(), new Ask(State.ASKED, missing));
			tellAsked(level, f, missing, builder);
			return false;
		}
		if (ask.missing() != missing && ask.state() == State.ASKED) {
			manager.setRepairAsk(f.placement(), new Ask(State.ASKED, missing)); // (the page shows what's missing now)
		}
		return false;
	}

	/** The question in chat: to the build's owner, else (a build nobody hired the builder for) the village's leader. */
	private static void tellAsked(ServerLevel level, BuildSiteManager.Finished f, int missing, Villager builder) {
		ServerPlayer to = level.getServer().getPlayerList().getPlayer(f.owner());
		if (to == null) {
			to = io.github.jcondedata.aliveworkplace.work.Reach.village(level, Builders.spot(level, f.structure(), f.placement())) // B616: a zone past the radius too
				.map(hall -> level.getBlockEntity(hall) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity e && e.owner() != null
					? level.getServer().getPlayerList().getPlayer(e.owner()) : null).orElse(null);
		}
		if (to == null) {
			return; // nobody on: the Repairs page holds the question
		}
		BlockPos at = f.placement().origin();
		Chat.system(to, io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.repair_ask", missing, builder.getDisplayName(), missing,
				Blueprints.displayName(f.structure()), at.getX(), at.getY(), at.getZ()).withStyle(ChatFormatting.YELLOW)
			.append(" ").append(button("yes", at, ChatFormatting.GREEN)).append(" ").append(button("notnow", at, ChatFormatting.RED)));
	}

	private static MutableComponent button(String action, BlockPos at, ChatFormatting colour) {
		return Component.translatable("message.aliveworkplace.repair_ask." + action).withStyle(s -> s.withColor(colour)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command(action, at)))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.repair_ask." + action + "_hover"))));
	}

	/** The command the chat's [Yes] or [Not now] runs. */
	public static String command(String action, BlockPos at) {
		return "/workplace repair " + action + " " + at.getX() + " " + at.getY() + " " + at.getZ();
	}

	// ---- answers ---------------------------------------------------------------------------------------------------

	/** The finished building whose origin is {@code at} in {@code level}, if there is one. */
	public static Optional<BuildSiteManager.Finished> building(ServerLevel level, BlockPos at) {
		return BuildSiteManager.get(level).finishedIn(level).stream().filter(f -> f.placement().origin().equals(at)).findFirst();
	}

	/** Whether {@code player} may answer for {@code f}: its owner, their friends, the leader of the village it stands in, an operator. */
	public static boolean mayAnswer(ServerLevel level, ServerPlayer player, BuildSiteManager.Finished f) {
		if (player.hasPermissions(2) || Friends.get(level.getServer()).mayDirect(f.owner(), player.getUUID())) {
			return true;
		}
		return io.github.jcondedata.aliveworkplace.work.Reach.village(level, Builders.spot(level, f.structure(), f.placement())) // B616
			.map(hall -> level.getBlockEntity(hall) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity e
				&& e.owner() != null && Friends.get(level.getServer()).mayDirect(e.owner(), player.getUUID()))
			.orElse(false);
	}

	/** Whether the owner said yes to repairing the building at {@code placement} (and hasn't taken it back). */
	public static boolean agreed(ServerLevel level, BlueprintData.Placement placement) {
		Ask ask = BuildSiteManager.get(level).repairAsk(placement);
		return ask != null && ask.state() == State.YES;
	}

	/** The repair of {@code placement} going on or queued (not a Wonder's part), if any. */
	@Nullable
	public static BuildSite repairOf(ServerLevel level, BlueprintData.Placement placement) {
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.isRepair() && !site.isRefresh() && site.wonder() == null && site.placement().equals(placement)) { // (a refresh, 36.21, has its own line)
				return site;
			}
		}
		return null;
	}

	/** {@code player} says yes to repairing the building at {@code at}: a free builder who looks after it sets about it straight away. */
	public static Outcome yes(ServerPlayer player, BlockPos at) {
		ServerLevel level = Players.level(player);
		Optional<BuildSiteManager.Finished> f = building(level, at);
		if (f.isEmpty()) {
			return said(player, null, Outcome.GONE);
		}
		if (!mayAnswer(level, player, f.get())) {
			return said(player, f.get(), Outcome.NOT_YOURS);
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		Ask ask = manager.repairAsk(f.get().placement());
		manager.setRepairAsk(f.get().placement(), new Ask(State.YES, ask != null ? ask.missing() : 0));
		said(player, f.get(), Outcome.YES);
		if (repairOf(level, f.get().placement()) == null) {
			startNow(level, f.get());
		}
		return Outcome.YES;
	}

	/** {@code player} says not now (or stops the repair going on): the build is left as it is, and asked about again once more goes missing. */
	public static Outcome notNow(ServerPlayer player, BlockPos at) {
		return leave(player, at, State.NOT_NOW);
	}

	/** {@code player} pauses the repair of the building at {@code at}: the builder leaves it until they say to carry on. */
	public static Outcome pause(ServerPlayer player, BlockPos at) {
		return leave(player, at, State.PAUSED);
	}

	private static Outcome leave(ServerPlayer player, BlockPos at, State state) {
		ServerLevel level = Players.level(player);
		Optional<BuildSiteManager.Finished> f = building(level, at);
		if (f.isEmpty()) {
			return said(player, null, Outcome.GONE);
		}
		if (!mayAnswer(level, player, f.get())) {
			return said(player, f.get(), Outcome.NOT_YOURS);
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		Ask ask = manager.repairAsk(f.get().placement());
		int missing = ask != null ? ask.missing() : 0;
		BuildSite site = repairOf(level, f.get().placement());
		if (site != null) {
			missing = Math.max(missing, missingNow(level, f.get()));
			Builders.cancel(level, site); // what's put back stays; the builder goes straight on to their next job
		}
		manager.setRepairAsk(f.get().placement(), new Ask(state, missing));
		return said(player, f.get(), state == State.PAUSED ? Outcome.PAUSED : Outcome.NOT_NOW);
	}

	private static int missingNow(ServerLevel level, BuildSiteManager.Finished f) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		return io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, f.structure())
			.map(b -> manager.furniture(f.placement()).apply(b, f.structure()))
			.map(b -> BuildPlan.repair(b, f.placement(), level, manager.gaveUp(f.placement())).size()).orElse(0);
	}

	/** After a yes: the nearest free builder who looks after {@code f} looks at once (instead of at their next round). */
	private static void startNow(ServerLevel level, BuildSiteManager.Finished f) {
		BlockPos origin = Builders.spot(level, f.structure(), f.placement()); // B615: measured as placing measures
		List<Villager> builders = new ArrayList<>(level.getEntities(EntityType.VILLAGER, new AABB(origin).inflate(Builders.MAX_SITE_DISTANCE * 2),
			v -> v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).isPresent() && !ModAttachments.BUILDER_JOB.has(v)
				&& Upkeep.looksAfter(level, v, f.owner())
				&& Builders.inReach(level, Builders.benchPos(v).get(), f.structure(), f.placement())));
		builders.sort(Comparator.comparingDouble(v -> v.distanceToSqr(origin.getX(), origin.getY(), origin.getZ())));
		for (Villager v : builders) {
			if (Builders.activeSite(level, v) == null && !ModAttachments.BUILDER_JOB.has(v)) {
				Upkeep.look(level, v);
				if (repairOf(level, f.placement()) != null) {
					return;
				}
			}
		}
	}

	private static Outcome said(ServerPlayer player, @Nullable BuildSiteManager.Finished f, Outcome outcome) {
		Component name = f != null ? Blueprints.displayName(f.structure()) : Component.empty();
		Chat.actionBar(player, switch (outcome) {
			case YES -> Component.translatable("message.aliveworkplace.repair_answer.agreed", name).withStyle(ChatFormatting.GREEN);
			case NOT_NOW -> Component.translatable("message.aliveworkplace.repair_answer.left", name).withStyle(ChatFormatting.YELLOW);
			case PAUSED -> Component.translatable("message.aliveworkplace.repair_answer.paused", name).withStyle(ChatFormatting.YELLOW);
			case NOT_YOURS -> Component.translatable("message.aliveworkplace.repair_answer.not_yours", name).withStyle(ChatFormatting.RED);
			case GONE -> Component.translatable("message.aliveworkplace.repair_answer.gone").withStyle(ChatFormatting.RED);
		});
		return outcome;
	}

	// ---- the list ----------------------------------------------------------------------------------------------------

	/**
	 * The builds round {@code hall} on the Repairs page: every one with a question, and every repair going on (a threat's
	 * too, which isn't asked), questions waiting first, then repairs going on, agreed, paused, and those left for now.
	 */
	public static List<Entry> entries(ServerLevel level, BlockPos hall) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		List<Entry> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : manager.finishedInVillage(level, hall)) { // B616: the village's claimed area
			Ask ask = manager.repairAsk(f.placement());
			BuildSite site = repairOf(level, f.placement());
			if (ask != null || site != null) {
				out.add(new Entry(f, ask, site));
			}
		}
		out.sort(Comparator.comparingInt(RepairAsks::order).thenComparingDouble(e -> e.building().placement().origin().distSqr(hall)));
		return out;
	}

	private static int order(Entry e) {
		if (e.ask() != null && e.ask().state() == State.ASKED) {
			return 0;
		}
		if (e.repair() != null) {
			return 1;
		}
		return e.ask() == null ? 5 : switch (e.ask().state()) {
			case YES -> 2;
			case PAUSED -> 3;
			default -> 4;
		};
	}

	/** Questions waiting round {@code hall}. */
	public static int waiting(ServerLevel level, BlockPos hall) {
		return (int) entries(level, hall).stream().filter(e -> e.ask() != null && e.ask().state() == State.ASKED).count();
	}

	private RepairAsks() {
	}
}
