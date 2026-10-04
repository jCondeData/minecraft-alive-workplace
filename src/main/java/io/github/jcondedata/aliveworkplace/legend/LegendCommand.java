package io.github.jcondedata.aliveworkplace.legend;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /workplace legend list|make <id>|clear} (ops): lists the Legends loaded, makes the nearest villager (within
 * {@link #REACH} blocks) a Legend, or makes the nearest Legend an ordinary villager again.
 */
public final class LegendCommand {
	public static final int REACH = 8;

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("legend")
			.requires(s -> s.hasPermission(2))
			.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
			.then(Commands.literal("make")
				.then(Commands.argument("id", ResourceLocationArgument.id())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(Legends.all().stream().map(Legend::id), b))
					.executes(ctx -> make(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id")))))
			.then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())));
	}

	private static boolean off(CommandSourceStack source) {
		if (!Legends.ENABLED) {
			source.sendFailure(Component.translatable("message.aliveworkplace.legend.off"));
			return true;
		}
		return false;
	}

	public static int list(CommandSourceStack source) {
		if (off(source)) {
			return 0;
		}
		List<Legend> all = Legends.all().stream().sorted(Comparator.comparing(l -> l.id().toString())).toList();
		source.sendSuccess(() -> Component.translatable("message.aliveworkplace.legend.list", all.size()), false);
		for (Legend legend : all) {
			source.sendSuccess(() -> Component.literal("  ").append(legend.titleText().copy().withStyle(ChatFormatting.GOLD))
				.append(" (").append(legend.rarity().title()).append(") ")
				.append(Component.literal(legend.id().toString()).withStyle(ChatFormatting.GRAY)), false);
		}
		return all.size();
	}

	public static int make(CommandSourceStack source, ResourceLocation id) {
		if (off(source)) {
			return 0;
		}
		Optional<Legend> legend = Legends.get(id);
		if (legend.isEmpty()) {
			source.sendFailure(Component.translatable("message.aliveworkplace.legend.unknown", id.toString()));
			return 0;
		}
		Villager villager = nearest(source, false);
		if (villager == null) {
			source.sendFailure(Component.translatable("message.aliveworkplace.legend.no_villager", REACH));
			return 0;
		}
		Legends.make(source.getLevel(), villager, legend.get(), "command");
		source.sendSuccess(() -> Component.translatable("message.aliveworkplace.legend.made", villager.getDisplayName(), legend.get().titleText()), true);
		return 1;
	}

	public static int clear(CommandSourceStack source) {
		if (off(source)) {
			return 0;
		}
		Villager villager = nearest(source, true);
		if (villager == null) {
			source.sendFailure(Component.translatable("message.aliveworkplace.legend.no_legend", REACH));
			return 0;
		}
		Legends.clear(villager);
		source.sendSuccess(() -> Component.translatable("message.aliveworkplace.legend.cleared", villager.getDisplayName()), true);
		return 1;
	}

	@Nullable
	private static Villager nearest(CommandSourceStack source, boolean legendsOnly) {
		ServerLevel level = source.getLevel();
		return level.getEntitiesOfClass(Villager.class, new AABB(source.getPosition(), source.getPosition()).inflate(REACH),
				v -> v.isAlive() && !v.isBaby() && (!legendsOnly || ModAttachments.LEGEND.has(v)))
			.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(source.getPosition()))).orElse(null);
	}

	private LegendCommand() {
	}
}
