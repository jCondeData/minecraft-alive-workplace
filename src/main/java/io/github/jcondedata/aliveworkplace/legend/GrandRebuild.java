package io.github.jcondedata.aliveworkplace.legend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Grander buildings, the Master Architect's {@code grand_rebuild} (29.12). Every {@link GrandRebuildPower#days} days a
 * settled Architect picks a building the village's builders finished (homes first; never a decoration or a defence, and
 * never anything a player built by hand, which the finished list doesn't hold) and hands the least busy builder its next
 * upgrade ({@link BlueprintUpgrades}). A building with none is redrawn in the Grand style on the same spot: the
 * builder works it like an upgrade, so only the blocks that differ are taken down and placed, with materials from the
 * chests as usual. One at a time; a sneak-right-click pauses it; a strike stops the one under way; each goes in the
 * chronicle. The Architect's own state (the {@code ARCHITECT} attachment) has a default for every field.
 */
public final class GrandRebuild {
	public static final String GRAND = "grand";

	/** The Architect's last rebuild day (Chronicle day, -1: never), whether a player paused it, and the site under way. */
	public record State(long lastDay, boolean paused, Optional<UUID> site) {
		public static final State EMPTY = new State(-1, false, Optional.empty());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(State::lastDay),
			Codec.BOOL.optionalFieldOf("paused", false).forGetter(State::paused),
			UUIDUtil.CODEC.optionalFieldOf("site").forGetter(State::site)
		).apply(i, State::new));
	}

	/** What a rebuild will be: the building, the blueprint it becomes, and whether that's its upgrade (else a redrawing). */
	public record Choice(BuildSiteManager.Finished building, ResourceLocation blueprint, boolean upgrade) {
	}

	public static State state(Villager architect) {
		State s = ModAttachments.ARCHITECT.get(architect);
		return s == null ? State.EMPTY : s;
	}

	private static void save(Villager architect, State state) {
		ModAttachments.ARCHITECT.set(architect, state);
	}

	/** Every 200 ticks of a Legend's life ({@link Legends#tick}): the Architect's round. */
	static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !data.settled()) {
			return;
		}
		Legends.get(data.id()).ifPresent(legend -> legend.powers(GrandRebuildPower.class).stream().findFirst()
			.ifPresent(power -> round(level, villager, data, power)));
	}

	/**
	 * The Architect's round: a strike stops the rebuild under way; paused, busy with one, or less than {@code days} days
	 * since the last, nothing; else the next rebuild starts. Returns the site started, if any.
	 */
	@Nullable
	public static BuildSite round(ServerLevel level, Villager architect, LegendData data, GrandRebuildPower power) {
		State state = state(architect);
		BuildSite current = state.site().map(id -> BuildSiteManager.get(level).get(id)).orElse(null);
		if (state.site().isPresent() && current == null) {
			state = new State(state.lastDay(), state.paused(), Optional.empty()); // finished or taken down
			save(architect, state);
		}
		BlockPos hall = data.hall().orElseGet(() -> VillageHalls.nearest(level, architect.blockPosition()).orElse(null));
		if (data.onStrike()) {
			if (current != null) {
				Builders.cancel(level, current);
				save(architect, new State(state.lastDay(), state.paused(), Optional.empty()));
				if (hall != null) {
					Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.legend.grand_stopped",
						architect.getDisplayName(), title(data), Blueprints.displayName(current.structure())));
				}
			}
			return null;
		}
		if (state.paused() || current != null || hall == null) {
			return null;
		}
		long today = Chronicle.day(level);
		if (state.lastDay() >= 0 && today - state.lastDay() < power.days()) {
			return null;
		}
		Optional<Choice> choice = choose(level, hall, power.style());
		if (choice.isEmpty()) {
			return null;
		}
		Optional<Villager> builder = leastBusy(level, hall, choice.get());
		if (builder.isEmpty()) {
			return null;
		}
		BuildSite site = hand(level, hall, builder.get(), choice.get());
		save(architect, new State(today, state.paused(), Optional.of(site.id())));
		Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable(
			choice.get().upgrade() ? "chronicle.aliveworkplace.legend.grand_upgrade" : "chronicle.aliveworkplace.legend.grand_redraw",
			architect.getDisplayName(), title(data), Blueprints.displayName(choice.get().building().structure()), builder.get().getDisplayName()));
		return site;
	}

	private static Component title(LegendData data) {
		return Legends.get(data.id()).map(Legend::titleText).orElse(Component.literal(data.id().toString()));
	}

	/** Decorations and defences are never rebuilt (their families). */
	private static Set<ResourceLocation> never() {
		Set<ResourceLocation> out = new HashSet<>();
		StarterBlueprints.DECORATIONS.forEach(e -> out.add(e.id()));
		StarterBlueprints.DEFENCES.forEach(e -> out.add(e.id()));
		return out;
	}

	/**
	 * The next rebuild in the village round {@code hall}: of the buildings its builders finished there with no site on
	 * them now, homes (a blueprint with a bed) first, then the oldest; its upgrade if the server has one, else the
	 * building redrawn in {@code style}; a building already in that style with no upgrade is left alone.
	 */
	public static Optional<Choice> choose(ServerLevel level, BlockPos hall, String style) {
		if (BlueprintStyles.get(style).isEmpty()) {
			return Optional.empty();
		}
		Set<ResourceLocation> never = never();
		List<BuildSite> sites = List.copyOf(BuildSiteManager.get(level).all());
		List<BuildSiteManager.Finished> finished = BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream()
			.filter(f -> !never.contains(StewardConditions.family(f.structure())))
			.filter(f -> sites.stream().noneMatch(s -> overlaps(level, s.structure(), s.placement(), f)))
			.sorted(Comparator.comparing((BuildSiteManager.Finished f) -> StewardConditions.beds(level, f.structure()) > 0 ? 0 : 1))
			.toList();
		for (BuildSiteManager.Finished f : finished) {
			ResourceLocation up = BlueprintUpgrades.upgradeOf(f.structure());
			if (!up.equals(f.structure()) && BlueprintLibrary.get(level, up).isPresent()) {
				return Optional.of(new Choice(f, up, true));
			}
			if (!style.equals(BlueprintStyles.styleOf(f.structure()).orElse(""))) {
				ResourceLocation grand = BlueprintStyles.styled(BlueprintStyles.base(f.structure()), style);
				if (BlueprintLibrary.get(level, grand).isPresent()) {
					return Optional.of(new Choice(f, grand, false));
				}
			}
		}
		return Optional.empty();
	}

	private static boolean overlaps(ServerLevel level, ResourceLocation structure, io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement placement,
									BuildSiteManager.Finished f) {
		if (!placement.dimension().equals(f.placement().dimension())) {
			return false;
		}
		if (placement.equals(f.placement())) {
			return true;
		}
		Optional<BoundingBox> a = BlueprintLibrary.get(level, structure).map(b -> BlueprintOutline.bounds(placement, b.size()));
		Optional<BoundingBox> b = BlueprintLibrary.get(level, f.structure()).map(x -> BlueprintOutline.bounds(f.placement(), x.size()));
		return a.isPresent() && b.isPresent() && a.get().intersects(b.get());
	}

	/** The village's builder with the fewest builds waiting whose bench reaches the building (ties: the nearest to it). */
	public static Optional<Villager> leastBusy(ServerLevel level, BlockPos hall, Choice choice) {
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, choice.blueprint());
		if (blueprint.isEmpty()) {
			return Optional.empty();
		}
		BlockPos centre = BlueprintOutline.bounds(choice.building().placement(), blueprint.get().size()).getCenter();
		return level.getEntities(EntityType.VILLAGER, VillageHalls.area(hall), v -> v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).isPresent())
			.stream()
			.filter(v -> Math.sqrt(centre.distSqr(Builders.benchPos(v).orElseThrow())) <= Builders.MAX_SITE_DISTANCE)
			.filter(v -> busy(level, v) <= Builders.MAX_QUEUE)
			.min(Comparator.comparingInt((Villager v) -> busy(level, v)).thenComparingDouble(v -> v.blockPosition().distSqr(centre)));
	}

	private static int busy(ServerLevel level, Villager builder) {
		return Builders.activeSite(level, builder) == null ? 0 : 1 + Builders.queue(level, builder).size();
	}

	/** Starts (or queues) the rebuild for {@code builder}: owned by the building's owner, a village build (no blueprint item). */
	private static BuildSite hand(ServerLevel level, BlockPos hall, Villager builder, Choice choice) {
		UUID owner = choice.building().owner();
		String ownerName = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && owner.equals(entity.owner()) ? entity.ownerName() : "";
		BuildSite site = Builders.activeSite(level, builder) != null
			? Builders.enqueue(level, builder, owner, ownerName, choice.blueprint(), choice.building().placement())
			: Builders.start(level, builder, owner, ownerName, choice.blueprint(), choice.building().placement());
		site.setStewardHall(hall);
		BuildSiteManager.get(level).setDirty();
		return site;
	}

	/** Whether {@code villager} is a settled Legend with this power (who pauses on a sneak-right-click). */
	public static boolean isArchitect(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		return data != null && data.settled() && Legends.get(data.id()).map(l -> !l.powers(GrandRebuildPower.class).isEmpty()).orElse(false);
	}

	/** A sneak-right-click: pauses grander buildings, or carries on with them (the one under way is finished either way). */
	public static void togglePause(ServerPlayer player, Villager architect) {
		State state = state(architect);
		boolean paused = !state.paused();
		save(architect, new State(state.lastDay(), paused, state.site()));
		Chat.actionBar(player, Component.translatable(paused ? "message.aliveworkplace.legend.grand_paused" : "message.aliveworkplace.legend.grand_resumed",
			architect.getDisplayName()).withStyle(paused ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
	}

	private GrandRebuild() {
	}
}
