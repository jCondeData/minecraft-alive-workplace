package io.github.jcondedata.aliveworkplace.command;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.authlib.GameProfile;
import io.github.jcondedata.aliveworkplace.build.Friends;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * /workplace blueprints            — list blueprints the server knows (ops)
 * /workplace blueprint &lt;id&gt;        — get a blueprint item (ops)
 * /workplace sites                 — your build sites and their status (ops see all)
 * /workplace cancel &lt;site&gt;         — stop a build and get the blueprint back
 * /workplace import                — import .litematic/.schem/.nbt files from &lt;world&gt;/aliveworkplace/import (ops)
 * /workplace legend list|make &lt;id&gt;|clear — the Legends loaded; make the nearest villager a Legend, or clear one (ops)
 * /workplace friend add|remove &lt;player&gt;, /workplace friend list — who may give orders to your builders
 * /workplace strip &lt;height&gt;       — the Quarry Marker in hand digs a strip mine at that height, down a ladder shaft
 * /workplace edict proclaim|lift &lt;id&gt; — proclaims or lifts an edict in the village you stand in (ops)
 * /workplace quests [track &lt;id&gt;|untrack] — your quests in chat with [Track]/[Untrack] (the quest journal, 31.3)
 * /workplace story start &lt;arc&gt;|next|stop — starts a story arc in the village you stand in, moves it on a chapter, or ends it (ops)
 * /workplace steward explain       — the Steward's rules for the nearest Village Hall, each condition's number and whether it held
 */
public final class WorkplaceCommand {
	public static void init() {
		Platform.get().onRegisterCommands(dispatcher -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("workplace")
			.then(Commands.literal("blueprints")
				.requires(s -> s.hasPermission(2))
				.executes(WorkplaceCommand::listBlueprints))
			.then(Commands.literal("blueprint")
				.requires(s -> s.hasPermission(2))
				.then(Commands.argument("structure", ResourceLocationArgument.id())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
						BlueprintLibrary.list(ctx.getSource().getServer(), false), builder))
					.executes(WorkplaceCommand::giveBlueprint)))
			.then(Commands.literal("import")
				.requires(s -> s.hasPermission(2))
				.executes(WorkplaceCommand::importFolder))
			.then(Commands.literal("sites")
				.executes(WorkplaceCommand::listSites))
			.then(Commands.literal("expedition")
				.then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(io.github.jcondedata.aliveworkplace.legend.Pathfinder.KINDS, builder))
					.executes(ctx -> io.github.jcondedata.aliveworkplace.legend.Pathfinder.choose(ctx.getSource().getPlayerOrException(),
						com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind")) ? 1 : 0)))
			.then(Commands.literal("mail")
				.executes(WorkplaceCommand::trackMail))
			.then(Commands.literal("cup") // the Festival Cup's [I'm ready] and [Watch] (28.20)
				.then(Commands.literal("ready")
					.executes(ctx -> {
						net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayerOrException();
						io.github.jcondedata.aliveworkplace.mc.Chat.system(p, io.github.jcondedata.aliveworkplace.cup.CupMatches.ready(p));
						return 1;
					}))
				.then(Commands.literal("watch")
					.executes(ctx -> {
						net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayerOrException();
						io.github.jcondedata.aliveworkplace.mc.Chat.system(p, io.github.jcondedata.aliveworkplace.cup.CupMatches.watch(p));
						return 1;
					})))
			.then(Commands.literal("quests") // the quest journal in chat (31.3)
				.executes(ctx -> quests(ctx.getSource().getPlayerOrException(), null, false))
				.then(Commands.literal("untrack")
					.executes(ctx -> quests(ctx.getSource().getPlayerOrException(), null, true)))
				.then(Commands.literal("track")
					.then(Commands.argument("quest", UuidArgument.uuid())
						.executes(ctx -> quests(ctx.getSource().getPlayerOrException(), UuidArgument.getUuid(ctx, "quest"), false)))))
			.then(Commands.literal("quest") // a villager's request: [I'll help] and [Not now] (31.9)
				.then(Commands.literal("accept")
					.then(Commands.argument("id", UuidArgument.uuid())
						.executes(ctx -> io.github.jcondedata.aliveworkplace.story.PersonalRequests.accept(ctx.getSource().getPlayerOrException(),
							UuidArgument.getUuid(ctx, "id")) ? 1 : 0)))
				.then(Commands.literal("decline")
					.then(Commands.argument("id", UuidArgument.uuid())
						.executes(ctx -> io.github.jcondedata.aliveworkplace.story.PersonalRequests.decline(ctx.getSource().getPlayerOrException(),
							UuidArgument.getUuid(ctx, "id")) ? 1 : 0))))
			.then(Commands.literal("steward")
				.then(Commands.literal("explain")
					.executes(WorkplaceCommand::explainSteward)))
			.then(Commands.literal("strip")
				.then(Commands.argument("height", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-2048, 2048))
					.executes(ctx -> stripHeight(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "height")))))
			.then(Commands.literal("cancel")
				.then(Commands.argument("site", UuidArgument.uuid())
					.executes(WorkplaceCommand::cancel)))
			.then(Commands.literal("edict")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("proclaim")
					.then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
							io.github.jcondedata.aliveworkplace.hall.Edicts.all().stream().map(e -> io.github.jcondedata.aliveworkplace.hall.Edicts.shortId(e.id())), builder))
						.executes(ctx -> edict(ctx, true))))
				.then(Commands.literal("lift")
					.then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
							io.github.jcondedata.aliveworkplace.hall.Edicts.all().stream().map(e -> io.github.jcondedata.aliveworkplace.hall.Edicts.shortId(e.id())), builder))
						.executes(ctx -> edict(ctx, false)))))
			.then(Commands.literal("friend")
				.then(Commands.literal("add")
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(ctx -> friend(ctx, true))))
				.then(Commands.literal("remove")
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(ctx -> friend(ctx, false))))
				.then(Commands.literal("list")
					.executes(WorkplaceCommand::listFriends)))
			.then(Commands.literal("story") // story arcs (31.4)
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("start")
					.then(Commands.argument("arc", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
							io.github.jcondedata.aliveworkplace.story.Arcs.all().stream().map(a -> a.id().toString()), builder))
						.executes(ctx -> story(ctx.getSource(), "start", com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "arc").trim()))))
				.then(Commands.literal("next")
					.executes(ctx -> story(ctx.getSource(), "next", "")))
				.then(Commands.literal("stop")
					.executes(ctx -> story(ctx.getSource(), "stop", ""))))
			.then(io.github.jcondedata.aliveworkplace.legend.LegendCommand.node()));
	}

	/** {@code /workplace story start <arc>|next|stop} in the village the command runs in (an operator's tool, 31.4). */
	public static int story(CommandSourceStack source, String what, String arcName) {
		ServerLevel level = source.getLevel();
		net.minecraft.core.BlockPos hall = io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level,
			net.minecraft.core.BlockPos.containing(source.getPosition())).orElse(null);
		if (hall == null) {
			source.sendFailure(Component.translatable("command.aliveworkplace.story.no_hall"));
			return 0;
		}
		Component village = io.github.jcondedata.aliveworkplace.hall.VillageHalls.name(level, hall);
		io.github.jcondedata.aliveworkplace.story.ArcState now = io.github.jcondedata.aliveworkplace.story.Arcs.running(level, hall);
		switch (what) {
			case "start" -> {
				if (!io.github.jcondedata.aliveworkplace.story.Arcs.ENABLED) {
					source.sendFailure(Component.translatable("command.aliveworkplace.story.off"));
					return 0;
				}
				var arc = io.github.jcondedata.aliveworkplace.story.Arcs.find(arcName);
				if (arc.isEmpty()) {
					source.sendFailure(Component.translatable("command.aliveworkplace.story.unknown", arcName));
					return 0;
				}
				if (!arc.get().side() && now != null) {
					source.sendFailure(Component.translatable("command.aliveworkplace.story.running", village, storyName(now)));
					return 0;
				}
				var started = io.github.jcondedata.aliveworkplace.story.Arcs.start(level, hall, arc.get());
				if (started == null) {
					source.sendFailure(Component.translatable("command.aliveworkplace.story.cannot", arc.get().name(), village));
					return 0;
				}
				source.sendSuccess(() -> Component.translatable("command.aliveworkplace.story.started", arc.get().name(), village), true);
				return 1;
			}
			case "next" -> {
				if (now == null) {
					source.sendFailure(Component.translatable("command.aliveworkplace.story.none", village));
					return 0;
				}
				Component name = storyName(now);
				io.github.jcondedata.aliveworkplace.story.Arcs.next(level, hall);
				io.github.jcondedata.aliveworkplace.story.ArcState after = io.github.jcondedata.aliveworkplace.story.Arcs.running(level, hall);
				source.sendSuccess(() -> after == now
					? Component.translatable("command.aliveworkplace.story.next", name, after.chapter + 1)
					: Component.translatable("command.aliveworkplace.story.ended", name), true);
				return 1;
			}
			default -> {
				if (now == null) {
					source.sendFailure(Component.translatable("command.aliveworkplace.story.none", village));
					return 0;
				}
				Component name = storyName(now);
				io.github.jcondedata.aliveworkplace.story.Arcs.stop(level, hall);
				source.sendSuccess(() -> Component.translatable("command.aliveworkplace.story.stopped", name, village), true);
				return 1;
			}
		}
	}

	private static Component storyName(io.github.jcondedata.aliveworkplace.story.ArcState state) {
		ResourceLocation id = ResourceLocation.tryParse(state.id);
		return id == null ? Component.literal(state.id) : io.github.jcondedata.aliveworkplace.story.Arcs.get(id)
			.map(io.github.jcondedata.aliveworkplace.story.Arcs.Arc::name).orElse(Component.literal(state.id));
	}

	/**
	 * {@code /workplace quests}: your quests in chat with a clickable [Track] or [Untrack]; {@code track <id>} and
	 * {@code untrack} are what those buttons run (ROADMAP 31.3). Behind milestone 31's gate.
	 */
	public static int quests(net.minecraft.server.level.ServerPlayer player, @org.jetbrains.annotations.Nullable java.util.UUID track, boolean untrack) {
		if (!io.github.jcondedata.aliveworkplace.story.QuestTracker.enabled()) {
			io.github.jcondedata.aliveworkplace.mc.Chat.system(player, Component.translatable("command.aliveworkplace.quests.off"));
			return 0;
		}
		if (untrack) {
			io.github.jcondedata.aliveworkplace.story.QuestTracker.untrack(player);
			io.github.jcondedata.aliveworkplace.mc.Chat.system(player, Component.translatable("command.aliveworkplace.quests.untracked"));
			return 1;
		}
		if (track != null) {
			if (!io.github.jcondedata.aliveworkplace.hall.QuestJournal.trackById(player, track)) {
				io.github.jcondedata.aliveworkplace.mc.Chat.system(player, Component.translatable("command.aliveworkplace.quests.gone"));
				return 0;
			}
			return 1;
		}
		for (Component line : io.github.jcondedata.aliveworkplace.hall.QuestJournal.chatList(player)) {
			io.github.jcondedata.aliveworkplace.mc.Chat.system(player, line);
		}
		return 1;
	}

	/** {@code /workplace steward explain}: every Steward's rule for the nearest Village Hall (ROADMAP 27.6). */
	private static int explainSteward(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		Optional<net.minecraft.core.BlockPos> hall = io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level,
			net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
		if (hall.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("command.aliveworkplace.steward.explain.no_hall"));
			return 0;
		}
		List<io.github.jcondedata.aliveworkplace.city.StewardRules.Rule> rules = io.github.jcondedata.aliveworkplace.city.StewardRules.all();
		for (Component line : io.github.jcondedata.aliveworkplace.city.StewardWishes.explain(level, hall.get(), rules)) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return rules.size();
	}

	/** {@code /workplace strip <height>}: the held Quarry Marker becomes a strip mine at that height, down a ladder shaft. */
	private static int stripHeight(CommandContext<CommandSourceStack> ctx, int height) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		net.minecraft.world.InteractionHand hand = player.getMainHandItem().is(io.github.jcondedata.aliveworkplace.registry.ModItems.QUARRY_MARKER)
			? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND;
		net.minecraft.world.item.ItemStack marker = player.getItemInHand(hand);
		if (!marker.is(io.github.jcondedata.aliveworkplace.registry.ModItems.QUARRY_MARKER)) {
			ctx.getSource().sendFailure(Component.translatable("message.aliveworkplace.quarry.strip_command.no_marker"));
			return 0;
		}
		ServerLevel level = Players.level(player);
		int low = level.getMinBuildHeight() + 1;
		int high = level.getMaxBuildHeight() - 3;
		if (height < low || height > high) {
			ctx.getSource().sendFailure(Component.translatable("message.aliveworkplace.quarry.strip_command.out_of_world", low, high));
			return 0;
		}
		io.github.jcondedata.aliveworkplace.mine.QuarryData data = io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem.data(marker).withStripLevel(height);
		marker.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.QUARRY, data);
		Chat.chat(player, Component.translatable("message.aliveworkplace.quarry.strip_level", height,
			io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem.oresAt(height)));
		return 1;
	}

	/** {@code /workplace edict proclaim|lift <id>}: in the village the command runs in (an admin's tool; the Book is 30.4). */
	private static int edict(CommandContext<CommandSourceStack> ctx, boolean proclaim) {
		CommandSourceStack source = ctx.getSource();
		String text = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id").trim();
		ServerLevel level = source.getLevel();
		net.minecraft.core.BlockPos hall = io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level,
			net.minecraft.core.BlockPos.containing(source.getPosition())).orElse(null);
		if (hall == null) {
			source.sendFailure(Component.translatable("message.aliveworkplace.edict.no_hall"));
			return 0;
		}
		io.github.jcondedata.aliveworkplace.hall.Edicts.Result result;
		if (proclaim) {
			var edict = io.github.jcondedata.aliveworkplace.hall.Edicts.find(text);
			if (edict.isEmpty()) {
				source.sendFailure(Component.translatable("message.aliveworkplace.edict.unknown", text));
				return 0;
			}
			result = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, hall, source.getPlayer(), edict.get());
		} else {
			String id = io.github.jcondedata.aliveworkplace.hall.Edicts.id(text).toString();
			result = io.github.jcondedata.aliveworkplace.hall.Edicts.lift(level, hall, source.getPlayer(), id);
		}
		if (!result.done()) {
			source.sendFailure(result.message());
			return 0;
		}
		if (source.getPlayer() == null || !result.told().contains(source.getPlayer())) {
			source.sendSuccess(result::message, true);
		}
		return 1;
	}

	private static int friend(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
		ServerPlayer me = ctx.getSource().getPlayerOrException();
		Friends friends = Friends.get(ctx.getSource().getServer());
		int changed = 0;
		for (GameProfile profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
			if (profile.getId().equals(me.getUUID())) {
				ctx.getSource().sendFailure(Component.translatable("command.aliveworkplace.friend.self"));
				continue;
			}
			boolean ok = add ? friends.add(me.getUUID(), profile.getId(), profile.getName()) : friends.remove(me.getUUID(), profile.getId());
			String key = add ? (ok ? "command.aliveworkplace.friend.added" : "command.aliveworkplace.friend.already")
				: (ok ? "command.aliveworkplace.friend.removed" : "command.aliveworkplace.friend.not_found");
			ctx.getSource().sendSuccess(() -> Component.translatable(key, profile.getName()).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
			changed += ok ? 1 : 0;
		}
		return changed;
	}

	private static int listFriends(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer me = ctx.getSource().getPlayerOrException();
		java.util.Map<UUID, String> list = Friends.get(ctx.getSource().getServer()).of(me.getUUID());
		if (list.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.translatable("command.aliveworkplace.friend.none").withStyle(ChatFormatting.GRAY), false);
		} else {
			String names = String.join(", ", list.values());
			ctx.getSource().sendSuccess(() -> Component.translatable("command.aliveworkplace.friend.list", names).withStyle(ChatFormatting.GOLD), false);
		}
		return list.size();
	}

	private static int listBlueprints(CommandContext<CommandSourceStack> ctx) {
		List<ResourceLocation> ids = BlueprintLibrary.list(ctx.getSource().getServer(), false);
		ctx.getSource().sendSuccess(() -> io.github.jcondedata.aliveworkplace.work.Words.counted("command.aliveworkplace.blueprints", ids.size(), ids.size()).withStyle(ChatFormatting.GOLD), false);
		for (ResourceLocation id : ids) {
			ctx.getSource().sendSuccess(() -> Component.literal("  " + id).withStyle(ChatFormatting.GRAY), false);
		}
		return ids.size();
	}

	private static int importFolder(CommandContext<CommandSourceStack> ctx) {
		List<Component> messages = BlueprintImporter.importFolder(ctx.getSource().getServer());
		for (Component message : messages) {
			ctx.getSource().sendSuccess(() -> message, true);
		}
		return messages.size();
	}

	private static int giveBlueprint(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ResourceLocation id = ResourceLocationArgument.getId(ctx, "structure");
		Optional<Blueprint> blueprint = BlueprintLibrary.get(ctx.getSource().getServer(), id);
		if (blueprint.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("message.aliveworkplace.blueprint.unknown", id.toString()));
			return 0;
		}
		player.getInventory().placeItemBackInInventory(BlueprintItem.create(id, blueprint.get().size()));
		ctx.getSource().sendSuccess(() -> Component.translatable("command.aliveworkplace.given", Blueprints.displayName(id)), false);
		return 1;
	}

	/** Parcels on their way to and from the player: waiting for a postman, carried, or travelling overnight. */
	private static int trackMail(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer me = ctx.getSource().getPlayerOrException();
		List<Component> lines = io.github.jcondedata.aliveworkplace.mail.Mail.tracking(ctx.getSource().getServer(), me.getUUID());
		if (lines.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.translatable("command.aliveworkplace.mail_none"), false);
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("command.aliveworkplace.mail_header").withStyle(ChatFormatting.GOLD), false);
		for (Component line : lines) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return lines.size();
	}

	private static int listSites(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean all = source.hasPermission(2);
		UUID me = source.getEntity() != null ? source.getEntity().getUUID() : null;
		int count = 0;
		for (ServerLevel level : source.getServer().getAllLevels()) {
			for (BuildSite site : BuildSiteManager.get(level).all()) {
				if (!all && !site.owner().equals(me)) {
					continue;
				}
				count++;
				Villager builder = site.builder() != null && level.getEntity(site.builder()) instanceof Villager v ? v : null;
				Component text = Builders.statusText(level, site, builder);
				source.sendSuccess(() -> text, false);
			}
			for (io.github.jcondedata.aliveworkplace.mine.QuarrySite quarry : io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(level).all()) {
				if (!all && !quarry.owner().equals(me)) {
					continue;
				}
				count++;
				Villager miner = quarry.miner() != null && level.getEntity(quarry.miner()) instanceof Villager v ? v : null;
				Component text = io.github.jcondedata.aliveworkplace.mine.Miners.statusText(level, quarry, miner);
				source.sendSuccess(() -> text, false);
			}
		}
		if (count == 0) {
			source.sendSuccess(() -> Component.translatable("command.aliveworkplace.no_sites").withStyle(ChatFormatting.GRAY), false);
		}
		return count;
	}

	private static int cancel(CommandContext<CommandSourceStack> ctx) {
		UUID id = UuidArgument.getUuid(ctx, "site");
		CommandSourceStack source = ctx.getSource();
		for (ServerLevel level : source.getServer().getAllLevels()) {
			io.github.jcondedata.aliveworkplace.mine.QuarrySite quarry = io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(level).get(id);
			if (quarry != null) {
				if (!source.hasPermission(2) && (source.getEntity() == null || !io.github.jcondedata.aliveworkplace.mine.Miners.isOwnerOrOp(source.getEntity(), quarry))) {
					source.sendFailure(Component.translatable("command.aliveworkplace.not_owner"));
					return 0;
				}
				io.github.jcondedata.aliveworkplace.mine.Miners.cancel(level, quarry);
				source.sendSuccess(() -> Component.translatable("command.aliveworkplace.quarry_cancelled"), false);
				return 1;
			}
			BuildSite site = BuildSiteManager.get(level).get(id);
			if (site == null) {
				continue;
			}
			boolean allowed = source.hasPermission(2) || source.getEntity() != null && Builders.isOwnerOrOp(source.getEntity(), site);
			if (!allowed) {
				source.sendFailure(Component.translatable("command.aliveworkplace.not_owner"));
				return 0;
			}
			Builders.cancel(level, site);
			source.sendSuccess(() -> Component.translatable("command.aliveworkplace.cancelled", Blueprints.displayName(site.structure())), false);
			return 1;
		}
		// A farmer's field has no site of its own: the id is the farmer's.
		for (ServerLevel level : source.getServer().getAllLevels()) {
			if (level.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager fisher
				&& io.github.jcondedata.aliveworkplace.fish.Fishers.isHired(fisher)) {
				boolean allowed = source.hasPermission(2)
					|| source.getEntity() instanceof net.minecraft.world.entity.player.Player p && Friends.mayCommand(p, fisher);
				if (!allowed) {
					source.sendFailure(Component.translatable("command.aliveworkplace.not_owner"));
					return 0;
				}
				io.github.jcondedata.aliveworkplace.fish.Fishers.release(level, fisher);
				source.sendSuccess(() -> Component.translatable("command.aliveworkplace.fisher_released", fisher.getDisplayName()), false);
				return 1;
			}
			if (level.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager keeper
				&& io.github.jcondedata.aliveworkplace.orchard.Orchards.orchard(keeper) != null) {
				boolean allowed = source.hasPermission(2)
					|| source.getEntity() instanceof net.minecraft.world.entity.player.Player p && Friends.mayCommand(p, keeper);
				if (!allowed) {
					source.sendFailure(Component.translatable("command.aliveworkplace.not_owner"));
					return 0;
				}
				io.github.jcondedata.aliveworkplace.orchard.Orchards.release(level, keeper,
					source.getEntity() instanceof net.minecraft.world.entity.player.Player p ? p : null);
				source.sendSuccess(() -> Component.translatable("command.aliveworkplace.orchard_released", keeper.getDisplayName()), false);
				return 1;
			}
			if (level.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager lumberjack
				&& io.github.jcondedata.aliveworkplace.wood.TreeFarms.farm(lumberjack) != null) {
				boolean allowed = source.hasPermission(2)
					|| source.getEntity() instanceof net.minecraft.world.entity.player.Player p && Friends.mayCommand(p, lumberjack);
				if (!allowed) {
					source.sendFailure(Component.translatable("command.aliveworkplace.not_owner"));
					return 0;
				}
				io.github.jcondedata.aliveworkplace.wood.TreeFarms.release(level, lumberjack,
					source.getEntity() instanceof net.minecraft.world.entity.player.Player p ? p : null);
				source.sendSuccess(() -> Component.translatable("command.aliveworkplace.tree_farm_released", lumberjack.getDisplayName()), false);
				return 1;
			}
			if (level.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager farmer
				&& io.github.jcondedata.aliveworkplace.farm.Fields.hasField(farmer)) {
				boolean allowed = source.hasPermission(2)
					|| source.getEntity() instanceof net.minecraft.world.entity.player.Player p && Friends.mayCommand(p, farmer);
				if (!allowed) {
					source.sendFailure(Component.translatable("command.aliveworkplace.not_owner"));
					return 0;
				}
				io.github.jcondedata.aliveworkplace.farm.Fields.release(level, farmer,
					source.getEntity() instanceof net.minecraft.world.entity.player.Player p ? p : null);
				source.sendSuccess(() -> Component.translatable("command.aliveworkplace.field_released", farmer.getDisplayName()), false);
				return 1;
			}
		}
		source.sendFailure(Component.translatable("command.aliveworkplace.no_such_site"));
		return 0;
	}

	private WorkplaceCommand() {
	}
}
