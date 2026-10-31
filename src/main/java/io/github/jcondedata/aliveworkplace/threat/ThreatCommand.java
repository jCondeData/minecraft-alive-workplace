package io.github.jcondedata.aliveworkplace.threat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;

/**
 * {@code /workplace threat start <culture> | lair <culture> | disaster <id> | end | clear} (operators, ROADMAP 32.21), at
 * the nearest Village Hall, for events and testing: starts a raid by a culture now, sets a culture's lair down by the
 * village, starts a disaster, ends the raid and the disaster under way, or clears the village of every threat (the raid,
 * the disaster, the lair and the attacks on the clock). It is an operator's order: it holds whatever the switches, the
 * threat level or At peace say (a lair by a quiet village still leaves at the next dawn), but not Peaceful, where
 * hostile mobs can't exist: it says so instead.
 * A culture or disaster of ours may be named by its path ({@code bandits}).
 */
public final class ThreatCommand {
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("threat")
			.requires(s -> s.hasPermission(2))
			.then(Commands.literal("start")
				.then(Commands.argument("culture", ResourceLocationArgument.id())
					.suggests((ctx, b) -> suggest(Threats.all().stream().map(Culture::id), b))
					.executes(ctx -> start(ctx.getSource(), ResourceLocationArgument.getId(ctx, "culture")))))
			.then(Commands.literal("lair")
				.then(Commands.argument("culture", ResourceLocationArgument.id())
					.suggests((ctx, b) -> suggest(Threats.all().stream().filter(Culture::needsLair).map(Culture::id), b))
					.executes(ctx -> lair(ctx.getSource(), ResourceLocationArgument.getId(ctx, "culture")))))
			.then(Commands.literal("disaster")
				.then(Commands.argument("id", ResourceLocationArgument.id())
					.suggests((ctx, b) -> suggest(Disasters.all().stream().map(Disaster::id), b))
					.executes(ctx -> disaster(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id")))))
			.then(Commands.literal("end").executes(ctx -> end(ctx.getSource())))
			.then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())));
	}

	/**
	 * Suggests the ids that fit what is typed, by the whole id or by its path (B705): vanilla's suggestions match a bare
	 * path only in the minecraft namespace, so {@code pill} suggested nothing though {@code pillager_warband} is accepted.
	 */
	static CompletableFuture<Suggestions> suggest(Stream<ResourceLocation> ids, SuggestionsBuilder builder) {
		String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
		ids.forEach(id -> {
			if (SharedSuggestionProvider.matchesSubStr(typed, id.toString()) || SharedSuggestionProvider.matchesSubStr(typed, id.getPath())) {
				builder.suggest(id.toString());
			}
		});
		return builder.buildFuture();
	}

	/** Ours by its path: {@code bandits} reads as {@code minecraft:bandits}, which is {@code aliveworkplace:bandits} when no pack has it. */
	static ResourceLocation ours(ResourceLocation id, boolean known) {
		return !known && id.getNamespace().equals("minecraft") ? AliveWorkplace.id(id.getPath()) : id;
	}

	private static Optional<BlockPos> hall(CommandSourceStack source) {
		Optional<BlockPos> hall = VillageHalls.nearest(source.getLevel(), BlockPos.containing(source.getPosition()));
		if (hall.isEmpty()) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.no_hall"));
		}
		return hall;
	}

	public static int start(CommandSourceStack source, ResourceLocation id) {
		ServerLevel level = source.getLevel();
		Optional<Culture> culture = Threats.get(ours(id, Threats.get(id).isPresent()));
		if (culture.isEmpty()) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.unknown_culture", id.toString()));
			return 0;
		}
		Optional<BlockPos> at = hall(source);
		if (at.isEmpty()) {
			return 0;
		}
		BlockPos hall = at.get();
		Component village = VillageHalls.name(level, hall);
		if (ThreatData.get(level).raid(hall).isPresent()) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.raid_under_way", village));
			return 0;
		}
		VillageHalls.Census census = VillageHalls.census(level, hall);
		VillageRaids.start(level, hall, census.villagers(), census.guards(), Double.NaN, culture.get(), level.random);
		Optional<ThreatData.Under> raid = ThreatData.get(level).raid(hall);
		Component name = Lairs.cultureName(culture.get().id());
		if (raid.isEmpty()) {
			// On Peaceful the game refuses hostile mobs (HostileSpawns), so that's why none came (B704), not the ground.
			source.sendFailure(level.getDifficulty() == Difficulty.PEACEFUL
				? Component.translatable("command.aliveworkplace.threat.peaceful_raid", name, village)
				: Component.translatable("command.aliveworkplace.threat.start_failed", name, village));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.aliveworkplace.threat.started", name, village, raid.get().raiders()), true);
		return Math.max(1, raid.get().raiders());
	}

	public static int lair(CommandSourceStack source, ResourceLocation id) {
		ServerLevel level = source.getLevel();
		Optional<Culture> culture = Threats.get(ours(id, Threats.get(id).isPresent()));
		if (culture.isEmpty()) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.unknown_culture", id.toString()));
			return 0;
		}
		Component name = Lairs.cultureName(culture.get().id());
		if (!culture.get().needsLair()) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.no_lair", name));
			return 0;
		}
		Optional<BlockPos> at = hall(source);
		if (at.isEmpty()) {
			return 0;
		}
		BlockPos hall = at.get();
		Component village = VillageHalls.name(level, hall);
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			// Its captain and band can't exist on Peaceful (HostileSpawns), and a lair by a quiet village leaves at the
			// next dawn anyway: say so rather than "nowhere to make camp" (B704).
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.peaceful_lair", name, village));
			return 0;
		}
		Optional<Lairs.Lair> standing = Lairs.near(level, hall);
		if (standing.isPresent()) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.lair_standing", village, Lairs.lairName(standing.get().culture())));
			return 0;
		}
		Lairs.Lair lair = Lairs.settle(level, hall, culture.get().id(), level.random);
		if (lair == null) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.lair_failed", name, village));
			return 0;
		}
		BlockPos pos = lair.pos();
		source.sendSuccess(() -> Component.translatable("command.aliveworkplace.threat.lair_made", Lairs.lairName(lair.culture()), village,
			pos.getX(), pos.getY(), pos.getZ()), true);
		return 1;
	}

	public static int disaster(CommandSourceStack source, ResourceLocation id) {
		ServerLevel level = source.getLevel();
		Optional<Disaster> disaster = Disasters.get(ours(id, Disasters.get(id).isPresent()));
		if (disaster.isEmpty()) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.unknown_disaster", id.toString()));
			return 0;
		}
		Optional<BlockPos> at = hall(source);
		if (at.isEmpty() || !(level.getBlockEntity(at.get()) instanceof VillageHallBlockEntity entity)) {
			return 0;
		}
		BlockPos hall = at.get();
		Component village = VillageHalls.name(level, hall);
		if (entity.disaster() != null) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.disaster_under_way", village,
				Disasters.get(entity.disaster()).map(Disasters::name).orElse(Component.literal(entity.disaster().toString()))));
			return 0;
		}
		Disasters.start(level, hall, entity, disaster.get(), VillageHalls.census(level, hall), level.random);
		Component name = Disasters.name(disaster.get());
		source.sendSuccess(() -> Component.translatable("command.aliveworkplace.threat.disaster_started", name, village), true);
		return 1;
	}

	/** Ends the raid and the disaster under way at the nearest hall. */
	public static int end(CommandSourceStack source) {
		Optional<BlockPos> at = hall(source);
		if (at.isEmpty()) {
			return 0;
		}
		int ended = end(source.getLevel(), at.get());
		Component village = VillageHalls.name(source.getLevel(), at.get());
		if (ended == 0) {
			source.sendFailure(Component.translatable("command.aliveworkplace.threat.nothing", village));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.aliveworkplace.threat.ended", village), true);
		return ended;
	}

	/** Ends the raid and the disaster under way at {@code hall}; how many of the two there were. */
	public static int end(ServerLevel level, BlockPos hall) {
		int ended = VillageRaids.callOff(level, hall) ? 1 : 0;
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.disaster() != null) {
			Disasters.end(level, hall, entity, Disasters.get(entity.disaster()).orElse(null), Chronicle.day(level));
			ended++;
		}
		return ended;
	}

	/** Clears the nearest hall's village of every threat: the raid, the disaster, the lair and the attacks on the clock. */
	public static int clear(CommandSourceStack source) {
		Optional<BlockPos> at = hall(source);
		if (at.isEmpty()) {
			return 0;
		}
		int cleared = clear(source.getLevel(), at.get());
		Component village = VillageHalls.name(source.getLevel(), at.get());
		source.sendSuccess(() -> Component.translatable("command.aliveworkplace.threat.cleared", village), true);
		return Math.max(1, cleared);
	}

	/** Clears the village round {@code hall} of every threat; how many there were. */
	public static int clear(ServerLevel level, BlockPos hall) {
		int cleared = end(level, hall);
		Optional<Lairs.Lair> lair = Lairs.near(level, hall);
		if (lair.isPresent()) {
			Lairs.leave(level, lair.get());
			cleared++;
		}
		ThreatData data = ThreatData.get(level);
		cleared += data.attacks(hall).size();
		data.drop(hall, Long.MAX_VALUE, false);
		return cleared;
	}

	private ThreatCommand() {
	}
}
