package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * At peace (ROADMAP 32.21): the hall's owner can set the village At peace on its Defence page, as they protect it from
 * the name tag. No raids, raiders' camps or disasters come to it then ({@link Threats#quiet}), and a camp already
 * standing leaves at the next dawn ({@link Lairs#round}); the hall's guards icon says so. The setting is saved on the
 * hall (off in halls saved before). The server's threat level 0 and Peaceful difficulty keep every village quiet, and
 * the same lines say that too.
 */
public final class AtPeace {
	/**
	 * Sets the village round {@code hall} At peace, or lets threats come again, if {@code player} may: the hall's owner,
	 * or an operator; a hall nobody owns yet becomes {@code player}'s (as protection does). What to tell them.
	 */
	public static Component toggle(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Component.empty();
		}
		if (entity.owner() == null) {
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		} else if (!entity.owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
			return Component.translatable("message.aliveworkplace.peace.not_owner", entity.ownerName()).withStyle(ChatFormatting.RED);
		}
		boolean on = !entity.atPeace();
		entity.setAtPeace(on, Chronicle.day(level));
		Chronicle.atHall(level, hall, Chronicle.Kind.PROTECTION,
			Component.translatable(on ? "chronicle.aliveworkplace.peace_on" : "chronicle.aliveworkplace.peace_off", player.getDisplayName()));
		Component name = VillageHalls.name(level, hall);
		if (!on) {
			return Component.translatable("message.aliveworkplace.peace.off", name).withStyle(ChatFormatting.YELLOW);
		}
		Optional<Lairs.Lair> lair = Lairs.near(level, hall);
		return (lair.isPresent()
			? Component.translatable("message.aliveworkplace.peace.on_lair", name, Lairs.lairName(lair.get().culture()))
			: Component.translatable("message.aliveworkplace.peace.on", name)).withStyle(ChatFormatting.GREEN);
	}

	/** Why nothing comes to the village round {@code hall}, if nothing does: the server's threat level, Peaceful, or At peace. */
	public static Optional<String> reason(ServerLevel level, BlockPos hall) {
		if (Threats.LEVEL <= 0) {
			return Optional.of("screen.aliveworkplace.hall.threats_off");
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return Optional.of("screen.aliveworkplace.hall.threats_peaceful");
		}
		if (Threats.atPeace(level, hall)) {
			return Optional.of("screen.aliveworkplace.hall.at_peace");
		}
		return Optional.empty();
	}

	/** The guards icon's lines (empty when threats come): that the village is at peace, and that a camp standing leaves at dawn. */
	public static List<Component> hallLines(ServerLevel level, BlockPos hall) {
		List<Component> lines = new ArrayList<>();
		reason(level, hall).ifPresent(key -> {
			lines.add(VillageHallScreen.line(key, ChatFormatting.GREEN));
			Lairs.near(level, hall).ifPresent(lair -> lines.add(VillageHallScreen.line(
				Component.translatable("screen.aliveworkplace.hall.lair_leaves", Lairs.lairName(lair.culture())), ChatFormatting.YELLOW)));
		});
		return lines;
	}

	/** The Defence page's At peace slot: on or off, what it does, why it holds anyway, and who may click it. */
	public static ItemStack icon(ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer) {
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		boolean on = entity != null && entity.atPeace();
		List<Component> lines = new ArrayList<>();
		lines.add(VillageHallScreen.line("screen.aliveworkplace.defence.peace_what", ChatFormatting.GRAY));
		if (on) {
			lines.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.peace_since", entity.peaceSince()), ChatFormatting.GRAY));
			Lairs.near(level, hall).ifPresent(lair -> lines.add(VillageHallScreen.line(
				Component.translatable("screen.aliveworkplace.hall.lair_leaves", Lairs.lairName(lair.culture())), ChatFormatting.YELLOW)));
		}
		reason(level, hall).filter(key -> !key.equals("screen.aliveworkplace.hall.at_peace"))
			.ifPresent(key -> lines.add(VillageHallScreen.line(key, ChatFormatting.AQUA)));
		boolean may = entity != null && viewer != null && (entity.owner() == null || entity.owner().equals(viewer.getUUID()) || viewer.hasPermissions(2));
		if (may) {
			lines.add(VillageHallScreen.line(on ? "screen.aliveworkplace.defence.peace_click_off" : "screen.aliveworkplace.defence.peace_click_on", ChatFormatting.YELLOW));
		} else if (entity != null) {
			lines.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.peace_owner", entity.ownerName()), ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(on ? Items.WHITE_TULIP : Items.GRAY_DYE,
			Component.translatable(on ? "screen.aliveworkplace.defence.peace_on" : "screen.aliveworkplace.defence.peace_off"),
			on ? ChatFormatting.GREEN : ChatFormatting.WHITE, lines.toArray(Component[]::new));
	}

	private AtPeace() {
	}
}
