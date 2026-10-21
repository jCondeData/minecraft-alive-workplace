package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * B290: moving a builder to another Blueprint Table without breaking the one they work at. Sneak-right-click a table
 * with an empty hand, standing next to the builder (or pick them on the Village Hall's free job blocks page): they take
 * that table, and their build and queued builds draw on its chests from then on (with the village's Storehouse and the
 * workers nearby, as from any table). Nothing new is saved: the table is the builder's job site and each site's bench.
 */
public final class BenchMoves {
	/** How close to the player a builder must stand to be the one moved. */
	public static final int REACH = 6;

	/**
	 * The sneak-click on the table at {@code table}: the builder nearest {@code player}, within {@link #REACH}, moves to
	 * it, or is told why not. False when no builder is that close (the table then opens as usual).
	 */
	public static boolean sneakUse(ServerPlayer player, BlockPos table) {
		ServerLevel level = (ServerLevel) player.level();
		List<Villager> near = level.getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(REACH),
			v -> v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == ModVillagers.BUILDER);
		if (near.isEmpty()) {
			return false;
		}
		Villager builder = near.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(player))).orElseThrow();
		if (!Friends.mayCommand(player, builder)) {
			Employer employer = ModAttachments.BUILDER_EMPLOYER.get(builder);
			Chat.chat(player, Component.translatable("message.aliveworkplace.not_your_builder", builder.getDisplayName(),
				employer != null ? employer.name() : "?", player.getGameProfile().getName()).withStyle(ChatFormatting.RED));
			return true;
		}
		Component refusal = refusal(level, builder, table);
		if (refusal != null) {
			Chat.chat(player, refusal.copy().withStyle(ChatFormatting.YELLOW));
			level.playSound(null, builder, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
			return true;
		}
		move(level, builder, table);
		level.playSound(null, builder, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Chat.chat(player, Component.translatable("message.aliveworkplace.builder.moved", builder.getDisplayName(), at(table))
			.withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Why {@code builder} can't move to {@code table}, or null if they can: it isn't a table, it's theirs already, or another's. */
	@Nullable
	public static Component refusal(ServerLevel level, Villager builder, BlockPos table) {
		if (!isTable(level, table)) {
			return Component.translatable("message.aliveworkplace.builder.not_a_table");
		}
		if (Builders.benchPos(builder).filter(table::equals).isPresent()) {
			return Component.translatable("message.aliveworkplace.builder.already_here", builder.getDisplayName());
		}
		if (level.getPoiManager().getType(table).isPresent() && level.getPoiManager().getFreeTickets(table) <= 0) {
			return Component.translatable("message.aliveworkplace.builder.table_taken");
		}
		return null;
	}

	/**
	 * Gives {@code builder} the table at {@code table} (letting go of their old one, which stays where it is, free), and
	 * points their build and queued builds at it, so the chests by it are the ones they fetch from and count.
	 */
	public static void move(ServerLevel level, Villager builder, BlockPos table) {
		if (level.getPoiManager().getType(table).isEmpty()) { // put down this tick: the level files it after the tick (B132)
			net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(level.getBlockState(table))
				.ifPresent(type -> level.getPoiManager().add(table.immutable(), type));
		}
		Stations.assign(level, builder, table, ModVillagers.BUILDER);
		BuildSite active = Builders.activeSite(level, builder);
		if (active != null && active.wonder() == null && !active.isDone() && builder.getUUID().equals(active.builder())) {
			active.setBench(table.immutable());
		}
		for (BuildSite queued : Builders.queue(level, builder)) {
			queued.setBench(table.immutable());
		}
		Village.forget(builder);
	}

	/** A Blueprint Table, or an old Builder's Bench. */
	public static boolean isTable(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(ModBlocks.BLUEPRINT_TABLE) || level.getBlockState(pos).is(ModBlocks.BUILDERS_BENCH);
	}

	/** "Works at the table at 10 64 -3", for the builder's status. */
	public static Optional<Component> statusLine(Villager builder) {
		return Builders.benchPos(builder).map(pos -> Component.translatable("message.aliveworkplace.status.table", at(pos)));
	}

	public static Component at(BlockPos pos) {
		return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
	}

	private BenchMoves() {
	}
}
