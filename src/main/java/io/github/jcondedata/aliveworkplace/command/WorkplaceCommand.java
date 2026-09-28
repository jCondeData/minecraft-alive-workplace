package io.github.jcondedata.aliveworkplace.command;

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
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
 * /workplace friend add|remove &lt;player&gt;, /workplace friend list — who may give orders to your builders
 */
public final class WorkplaceCommand {
	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
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
			.then(Commands.literal("mail")
				.executes(WorkplaceCommand::trackMail))
			.then(Commands.literal("cancel")
				.then(Commands.argument("site", UuidArgument.uuid())
					.executes(WorkplaceCommand::cancel)))
			.then(Commands.literal("friend")
				.then(Commands.literal("add")
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(ctx -> friend(ctx, true))))
				.then(Commands.literal("remove")
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(ctx -> friend(ctx, false))))
				.then(Commands.literal("list")
					.executes(WorkplaceCommand::listFriends))));
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
		ctx.getSource().sendSuccess(() -> Component.translatable("command.aliveworkplace.blueprints", ids.size()).withStyle(ChatFormatting.GOLD), false);
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
