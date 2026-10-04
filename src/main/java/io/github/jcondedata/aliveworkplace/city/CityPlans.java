package io.github.jcondedata.aliveworkplace.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.BitSet;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * Changing a village's plan (ROADMAP 27.2): the one packet the plan screen sends, checked on the server. Who may: the
 * hall's owner, their friends and operators; a hall nobody owns becomes the first painter's.
 */
public final class CityPlans {
	/** What an edit does. */
	public enum Op implements StringRepresentable {
		ADD_ZONE, EDIT_ZONE, REMOVE_ZONE, PAINT, ERASE, MODE;

		public static final Codec<Op> CODEC = StringRepresentable.fromEnum(Op::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** One change to the plan of the hall at {@code hall}. Unused fields are left at their defaults. */
	public record Edit(BlockPos hall, Op op, int zone, String kind, String name, String style, boolean renew, BitSet cells,
					   CityPlan.Mode mode) implements CustomPacketPayload {
		public static final Type<Edit> TYPE = new Type<>(AliveWorkplace.id("city_plan_edit"));
		private static final Codec<Edit> RECORD = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("hall").forGetter(Edit::hall),
			Op.CODEC.fieldOf("op").forGetter(Edit::op),
			Codec.INT.optionalFieldOf("zone", -1).forGetter(Edit::zone),
			Codec.STRING.optionalFieldOf("kind", "").forGetter(Edit::kind),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Edit::name),
			Codec.STRING.optionalFieldOf("style", "").forGetter(Edit::style),
			Codec.BOOL.optionalFieldOf("renew", false).forGetter(Edit::renew),
			Codec.LONG_STREAM.xmap(s -> BitSet.valueOf(s.toArray()), b -> java.util.Arrays.stream(b.toLongArray()))
				.optionalFieldOf("cells", new BitSet()).forGetter(Edit::cells),
			CityPlan.Mode.CODEC.optionalFieldOf("mode", CityPlan.Mode.ASK).forGetter(Edit::mode)
		).apply(i, Edit::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = ByteBufCodecs.fromCodecWithRegistries(RECORD);

		public static Edit addZone(BlockPos hall, String kind, String name, String style) {
			return new Edit(hall, Op.ADD_ZONE, -1, kind, name, style, false, new BitSet(), CityPlan.Mode.ASK);
		}

		public static Edit editZone(BlockPos hall, int zone, String kind, String name, String style, boolean renew) {
			return new Edit(hall, Op.EDIT_ZONE, zone, kind, name, style, renew, new BitSet(), CityPlan.Mode.ASK);
		}

		public static Edit removeZone(BlockPos hall, int zone) {
			return new Edit(hall, Op.REMOVE_ZONE, zone, "", "", "", false, new BitSet(), CityPlan.Mode.ASK);
		}

		public static Edit paint(BlockPos hall, int zone, BitSet cells) {
			return new Edit(hall, Op.PAINT, zone, "", "", "", false, cells, CityPlan.Mode.ASK);
		}

		public static Edit erase(BlockPos hall, BitSet cells) {
			return new Edit(hall, Op.ERASE, -1, "", "", "", false, cells, CityPlan.Mode.ASK);
		}

		public static Edit mode(BlockPos hall, CityPlan.Mode mode) {
			return new Edit(hall, Op.MODE, -1, "", "", "", false, new BitSet(), mode);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		CityZones.init();
		Platform.get().serverbound(Edit.TYPE, Edit.CODEC, (edit, player) -> apply(player, edit));
	}

	/** Carries out {@code edit} for {@code player} if they may; true if the plan changed. */
	public static boolean apply(ServerPlayer player, Edit edit) {
		ServerLevel level = player.serverLevel();
		if (!level.isLoaded(edit.hall()) || !(level.getBlockEntity(edit.hall()) instanceof VillageHallBlockEntity entity)) {
			return false;
		}
		if (!mayChange(level, entity, player)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.city_plan.denied", VillageHalls.name(level, edit.hall()),
				entity.ownerName()).withStyle(ChatFormatting.RED));
			return false;
		}
		CityPlan plan = entity.plan();
		CityPlan next = switch (edit.op()) {
			case ADD_ZONE -> plan.addZone(edit.kind(), edit.name(), edit.style());
			case EDIT_ZONE -> plan.editZone(edit.zone(), edit.kind(), edit.name(), edit.style(), edit.renew());
			case REMOVE_ZONE -> plan.removeZone(edit.zone());
			case PAINT -> plan.paint(edit.zone(), ownCells(level, edit.hall(), edit.cells(), player));
			case ERASE -> plan.erase(edit.cells());
			case MODE -> plan.withMode(edit.mode());
		};
		if (next == null || next.equals(plan)) {
			return false;
		}
		if (entity.owner() == null) {
			entity.setOwner(player.getUUID(), player.getGameProfile().getName()); // a hall nobody owns: the first painter's
		}
		entity.setPlan(next);
		return true;
	}

	/** The hall's owner, their friends and operators; anyone while nobody owns it. */
	public static boolean mayChange(ServerLevel level, VillageHallBlockEntity hall, ServerPlayer player) {
		return VillageProtection.mayBuild(level, hall, player);
	}

	/** {@code cells} inside the grid and not nearer another hall (those are refused, and the player told). */
	static BitSet ownCells(ServerLevel level, BlockPos hall, BitSet cells, @Nullable ServerPlayer player) {
		BitSet out = new BitSet();
		int refused = 0;
		for (int cell = cells.nextSetBit(0); cell >= 0 && cell < CityPlan.GRID * CityPlan.GRID; cell = cells.nextSetBit(cell + 1)) {
			if (CityPlan.nearerAnotherHall(level, hall, cell)) {
				refused++;
			} else {
				out.set(cell);
			}
		}
		if (refused > 0 && player != null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.city_plan.other_village", refused).withStyle(ChatFormatting.YELLOW));
		}
		return out;
	}

	private CityPlans() {
	}
}
