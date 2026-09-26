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
			.then(Commands.literal("cancel")
				.then(Commands.argument("site", UuidArgument.uuid())
					.executes(WorkplaceCommand::cancel))));
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
		source.sendFailure(Component.translatable("command.aliveworkplace.no_such_site"));
		return 0;
	}

	private WorkplaceCommand() {
	}
}
