package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.grave.UndertakerWork;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * {@code undertakers} (35.10, the Great Cathedral's): while the Wonder stands, its village's Undertakers need no golden
 * apple, healing potion or totem; with {@code "search": "village"} they look for graves across the whole village (the
 * hall's reach) and not only {@link UndertakerWork#RADIUS} blocks round their table; and with
 * {@code "revive_by": "morning"} anyone still in a grave of the village at dawn is brought back by the village's
 * Undertaker in the night ({@link #morning}), so nobody lies there past the next morning.
 */
public record UndertakersPower(boolean village, boolean byMorning) implements WonderPower {
	public static final String TYPE = "undertakers";
	/** Up to this time of day the hall's round counts as the morning, when the night's graves are emptied. */
	public static final long MORNING_TO = 2000;

	static UndertakersPower read(JsonObject json) {
		String search = json.has("search") ? json.get("search").getAsString() : "village";
		if (!search.equals("village") && !search.equals("table")) {
			throw new IllegalArgumentException("'search' must be \"village\" or \"table\", not \"" + search + "\"");
		}
		String by = json.has("revive_by") ? json.get("revive_by").getAsString() : "morning";
		if (!by.equals("morning") && !by.equals("none")) {
			throw new IllegalArgumentException("'revive_by' must be \"morning\" or \"none\", not \"" + by + "\"");
		}
		return new UndertakersPower(search.equals("village"), by.equals("morning"));
	}

	@Override
	public String type() {
		return TYPE;
	}

	/** The power reaching the village of the Undertaker whose table is at {@code table}, if a finished, standing Wonder grants it. */
	public static Optional<UndertakersPower> at(ServerLevel level, BlockPos table) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, table);
		if (hall.isEmpty()) {
			return Optional.empty();
		}
		return Wonders.power(level, hall.get(), TYPE).filter(p -> p instanceof UndertakersPower).map(p -> (UndertakersPower) p);
	}

	/**
	 * The graves the Undertaker at {@code table} tends, nearest the table first: those within {@code radius} of it, and with
	 * the power's {@code search: village}, every grave in the village too.
	 */
	public static List<BlockPos> graves(ServerLevel level, BlockPos table, int radius) {
		List<BlockPos> out = new ArrayList<>(Graves.near(level, table, radius));
		Optional<BlockPos> hall = VillageHalls.nearest(level, table);
		Optional<UndertakersPower> power = at(level, table);
		if (hall.isPresent() && power.isPresent() && power.get().village()) {
			AABB area = VillageHalls.area(hall.get());
			for (BlockPos p : Graves.near(level, hall.get(), VillageHalls.RADIUS * 2)) {
				if (area.contains(p.getCenter()) && !out.contains(p)) {
					out.add(p);
				}
			}
			out.sort(Comparator.comparingDouble(p -> p.distSqr(table)));
		}
		return out;
	}

	/**
	 * The hall's round in the morning: in a village with the power's {@code revive_by: morning} and an Undertaker at their
	 * table, everyone who died before this morning and still lies in a grave of the village is brought back, the
	 * Undertaker credited for each. Returns how many came back.
	 */
	public static int morning(ServerLevel level, BlockPos hall) {
		long time = level.getDayTime() % VillageNeeds.DAY;
		if (time >= MORNING_TO) {
			return 0;
		}
		Optional<UndertakersPower> power = Wonders.power(level, hall, TYPE).filter(p -> p instanceof UndertakersPower).map(p -> (UndertakersPower) p);
		if (power.isEmpty() || !power.get().byMorning()) {
			return 0;
		}
		AABB area = VillageHalls.area(hall);
		Villager undertaker = level.getEntitiesOfClass(Villager.class, area, v -> v.isAlive() && UndertakerWork.isUndertaker(v)
			&& Builders.benchPos(v).isPresent()).stream().findFirst().orElse(null);
		if (undertaker == null) {
			return 0;
		}
		long dawn = level.getGameTime() - time;
		int back = 0;
		for (BlockPos grave : Graves.near(level, hall, VillageHalls.RADIUS * 2)) {
			if (!area.contains(grave.getCenter()) || !(level.getBlockEntity(grave) instanceof GraveBlockEntity g) || g.died() >= dawn) {
				continue;
			}
			Component name = g.name();
			Villager revived = Graves.revive(level, grave);
			if (revived == null) {
				continue;
			}
			back++;
			ModAttachments.VILLAGERS_REVIVED.set(undertaker, ModAttachments.VILLAGERS_REVIVED.getOrElse(undertaker, 0) + 1);
			BuilderLevels.addXp(level, undertaker, 5, null);
			for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(revived) < 48 * 48)) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.grave.revived_by_morning", name, undertaker.getDisplayName())
					.withStyle(ChatFormatting.GREEN));
			}
		}
		return back;
	}
}
