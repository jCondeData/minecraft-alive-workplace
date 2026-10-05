package io.github.jcondedata.aliveworkplace.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
		ADD_ZONE, EDIT_ZONE, REMOVE_ZONE, PAINT, ERASE, MODE, UNDO, ADD_ROAD, REMOVE_ROAD, WALL;

		public static final Codec<Op> CODEC = StringRepresentable.fromEnum(Op::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * One change to the plan of the hall at {@code hall}. Unused fields are left at their defaults. Roads and the wall
	 * line (27.4) use {@code points} (offsets from the hall), {@code width} and {@code closed}; {@code zone} is the road's
	 * index for {@link Op#REMOVE_ROAD}.
	 */
	public record Edit(BlockPos hall, Op op, int zone, String kind, String name, String style, boolean renew, BitSet cells,
					   CityPlan.Mode mode, List<BlockPos> points, int width, boolean closed) implements CustomPacketPayload {
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
			CityPlan.Mode.CODEC.optionalFieldOf("mode", CityPlan.Mode.ASK).forGetter(Edit::mode),
			BlockPos.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(Edit::points),
			Codec.INT.optionalFieldOf("width", CityPlan.Road.STREET).forGetter(Edit::width),
			Codec.BOOL.optionalFieldOf("closed", true).forGetter(Edit::closed)
		).apply(i, Edit::new));

		public Edit(BlockPos hall, Op op, int zone, String kind, String name, String style, boolean renew, BitSet cells, CityPlan.Mode mode) {
			this(hall, op, zone, kind, name, style, renew, cells, mode, List.of(), CityPlan.Road.STREET, true);
		}

		public Edit {
			points = List.copyOf(points);
		}

		/** A road through {@code points} (offsets from the hall), {@code width} wide, in {@code style} ("" for its first zone's). */
		public static Edit addRoad(BlockPos hall, List<BlockPos> points, int width, String style) {
			return new Edit(hall, Op.ADD_ROAD, -1, "", "", style, false, new BitSet(), CityPlan.Mode.ASK, points, width, true);
		}

		public static Edit removeRoad(BlockPos hall, int road) {
			return new Edit(hall, Op.REMOVE_ROAD, road, "", "", "", false, new BitSet(), CityPlan.Mode.ASK);
		}

		/** The wall line through {@code points}, open or closed; no points takes the wall line off the plan. */
		public static Edit wall(BlockPos hall, List<BlockPos> points, boolean closed) {
			return new Edit(hall, Op.WALL, -1, "", "", "", false, new BitSet(), CityPlan.Mode.ASK, points, CityPlan.Road.STREET, closed);
		}
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

		/** Puts back the plan as it was before the last change (27.3: up to {@link #UNDO_STEPS}). */
		public static Edit undo(BlockPos hall) {
			return new Edit(hall, Op.UNDO, -1, "", "", "", false, new BitSet(), CityPlan.Mode.ASK);
		}

		public static Edit mode(BlockPos hall, CityPlan.Mode mode) {
			return new Edit(hall, Op.MODE, -1, "", "", "", false, new BitSet(), mode);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** How many changes the plan screen can undo (27.3). */
	public static final int UNDO_STEPS = 10;
	/** Pixels along a side of the plan screen's map. */
	public static final int MAP = 128;
	/** The most a zone, village or style name may be on the plan screen. */
	public static final int MAX_NAME = 32;

	/** A zone kind as the plan screen draws it. */
	public record KindInfo(String id, int color, int tint, ResourceLocation icon, boolean buildable) {
	}

	/** A style as the plan screen lists it. */
	public record StyleInfo(String name, Component title, ResourceLocation icon) {
	}

	/** A banner on the screen's map: offset from the hall in blocks, its dye colour and whether it is the hall. */
	public record Mark(int dx, int dz, int color, boolean hall) {
	}

	/** A rectangle on the screen's map, offsets from the hall in blocks: a build site going up or a Steward's proposal. */
	public record Outline(int minDx, int minDz, int maxDx, int maxDz, boolean proposal) {
	}

	/** Opens the plan screen (27.3): everything it draws. */
	public record Open(BlockPos hall, Component village, int cellSize, CityPlan plan, int undo, boolean mayEdit, byte[] colors,
					   List<Mark> marks, List<Outline> outlines, List<KindInfo> kinds, List<StyleInfo> styles) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(AliveWorkplace.id("city_plan_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((buf, o) -> {
			buf.writeBlockPos(o.hall());
			ComponentSerialization.STREAM_CODEC.encode(buf, o.village());
			buf.writeVarInt(o.cellSize());
			CityPlan.STREAM_CODEC.encode(buf, o.plan());
			buf.writeVarInt(o.undo());
			buf.writeBoolean(o.mayEdit());
			buf.writeByteArray(o.colors());
			buf.writeCollection(o.marks(), (b, m) -> {
				b.writeVarInt(m.dx());
				b.writeVarInt(m.dz());
				b.writeVarInt(m.color());
				b.writeBoolean(m.hall());
			});
			buf.writeCollection(o.outlines(), (b, l) -> {
				b.writeVarInt(l.minDx());
				b.writeVarInt(l.minDz());
				b.writeVarInt(l.maxDx());
				b.writeVarInt(l.maxDz());
				b.writeBoolean(l.proposal());
			});
			buf.writeCollection(o.kinds(), (b, k) -> {
				b.writeUtf(k.id());
				b.writeInt(k.color());
				b.writeInt(k.tint());
				b.writeResourceLocation(k.icon());
				b.writeBoolean(k.buildable());
			});
			buf.writeVarInt(o.styles().size());
			for (StyleInfo st : o.styles()) {
				buf.writeUtf(st.name());
				ComponentSerialization.STREAM_CODEC.encode(buf, st.title());
				buf.writeResourceLocation(st.icon());
			}
		}, buf -> {
			BlockPos hall = buf.readBlockPos();
			Component village = ComponentSerialization.STREAM_CODEC.decode(buf);
			int cellSize = buf.readVarInt();
			CityPlan plan = CityPlan.STREAM_CODEC.decode(buf);
			int undo = buf.readVarInt();
			boolean mayEdit = buf.readBoolean();
			byte[] colors = buf.readByteArray(MAP * MAP);
			List<Mark> marks = buf.readList(b -> new Mark(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
			List<Outline> outlines = buf.readList(b -> new Outline(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
			List<KindInfo> kinds = buf.readList(b -> new KindInfo(b.readUtf(), b.readInt(), b.readInt(), b.readResourceLocation(), b.readBoolean()));
			int n = buf.readVarInt();
			List<StyleInfo> styles = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				styles.add(new StyleInfo(buf.readUtf(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readResourceLocation()));
			}
			return new Open(hall, village, cellSize, plan, undo, mayEdit, colors, marks, outlines, kinds, styles);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The plan after a change, sent to whoever made it (and anyone else with the screen open on that hall). */
	public record Sync(BlockPos hall, CityPlan plan, int undo) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(AliveWorkplace.id("city_plan_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of((buf, o) -> {
			buf.writeBlockPos(o.hall());
			CityPlan.STREAM_CODEC.encode(buf, o.plan());
			buf.writeVarInt(o.undo());
		}, buf -> new Sync(buf.readBlockPos(), CityPlan.STREAM_CODEC.decode(buf), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		CityZones.init();
		CityPlanGround.init();
		Platform.get().clientbound(Open.TYPE, Open.CODEC);
		Platform.get().clientbound(Sync.TYPE, Sync.CODEC);
		Platform.get().serverbound(Edit.TYPE, Edit.CODEC, (edit, player) -> apply(player, edit));
	}

	/** Everything the plan screen of the hall at {@code hall} shows {@code player}. */
	public static Open screen(ServerLevel level, VillageHallBlockEntity entity, ServerPlayer player) {
		BlockPos hall = entity.getBlockPos();
		int cell = CityPlan.cellSize();
		int half = cell * CityPlan.GRID / 2;
		byte[] colors = VillageMaps.colors(level, hall, half, Math.max(1, half * 2 / MAP));
		List<Mark> marks = new ArrayList<>();
		for (VillageMaps.Mark m : VillageMaps.marks(level, hall, half)) {
			marks.add(new Mark(m.pos().getX() - hall.getX(), m.pos().getZ() - hall.getZ(), m.kind().color.getId(), m.kind() == VillageMaps.Kind.HALL));
		}
		List<Outline> outlines = new ArrayList<>();
		// Build sites going up. The Steward's proposals (27.8) join these as Outline(..., true) once they exist.
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (!site.placement().dimension().equals(level.dimension().location())) {
				continue;
			}
			BlueprintLibrary.get(level, site.structure()).ifPresent(b -> {
				BoundingBox box = BlueprintOutline.bounds(site.placement(), b.size());
				if (box.maxX() >= hall.getX() - half && box.minX() < hall.getX() + half && box.maxZ() >= hall.getZ() - half && box.minZ() < hall.getZ() + half) {
					outlines.add(new Outline(box.minX() - hall.getX(), box.minZ() - hall.getZ(), box.maxX() - hall.getX(), box.maxZ() - hall.getZ(), false));
				}
			});
		}
		List<KindInfo> kinds = CityZones.all().stream()
			.map(k -> new KindInfo(k.id(), k.color().getTextureDiffuseColor() & 0xFFFFFF, k.mapTint(), k.icon(), k.buildable())).toList();
		List<StyleInfo> styles = BlueprintStyles.all().stream().map(st -> new StyleInfo(st.name(), st.title(), st.icon())).toList();
		return new Open(hall, VillageHalls.name(level, hall), cell, entity.plan(), entity.planUndoSteps(), mayChange(level, entity, player),
			colors, marks, outlines, kinds, styles);
	}

	/** Opens the plan screen of the hall at {@code entity} for {@code player}. */
	public static void open(ServerPlayer player, VillageHallBlockEntity entity) {
		if (Platform.get().canSend(player, Open.TYPE)) {
			Platform.get().send(player, screen(player.serverLevel(), entity, player));
		}
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
		if (edit.op() == Op.UNDO) {
			boolean undone = entity.undoPlan();
			sync(player, entity);
			return undone;
		}
		CityPlan next = switch (edit.op()) {
			case ADD_ZONE -> plan.addZone(edit.kind(), edit.name(), edit.style());
			case EDIT_ZONE -> plan.editZone(edit.zone(), edit.kind(), edit.name(), edit.style(), edit.renew());
			case REMOVE_ZONE -> plan.removeZone(edit.zone());
			case PAINT -> plan.paint(edit.zone(), ownCells(level, edit.hall(), edit.cells(), player));
			case ERASE -> plan.erase(edit.cells());
			case MODE -> plan.withMode(edit.mode());
			case ADD_ROAD -> road(player, edit, plan);
			case REMOVE_ROAD -> plan.removeRoad(edit.zone());
			case WALL -> edit.points().isEmpty() ? plan.withWall(null) : wall(player, edit, plan);
			case UNDO -> plan;
		};
		if (next == null || next.equals(plan)) {
			sync(player, entity); // the screen drew a change that didn't happen: put it right
			return false;
		}
		if (entity.owner() == null) {
			entity.setOwner(player.getUUID(), player.getGameProfile().getName()); // a hall nobody owns: the first painter's
		}
		entity.changePlan(next, UNDO_STEPS);
		sync(player, entity);
		return true;
	}

	/**
	 * The plan with the road {@code edit} draws, or null (and the player told why) for a 25th road, more than
	 * {@link CityPlan#MAX_ROAD_POINTS} points, a point off the grid or a width that isn't a lane, street or avenue. A
	 * player's road is approved (27.15); with no style it takes the style of the zone its first point is in.
	 */
	@Nullable
	static CityPlan road(@Nullable ServerPlayer player, Edit edit, CityPlan plan) {
		Component problem = null;
		if (plan.drawnRoads() >= CityPlan.MAX_ROADS) {
			problem = Component.translatable("message.aliveworkplace.city_plan.too_many_roads", CityPlan.MAX_ROADS);
		} else if (edit.points().size() > CityPlan.MAX_ROAD_POINTS) {
			problem = Component.translatable("message.aliveworkplace.city_plan.too_many_points", CityPlan.MAX_ROAD_POINTS);
		} else if (edit.points().size() < 2 || !CityPlan.Road.validWidth(edit.width())
			|| edit.points().stream().anyMatch(p -> !CityPlan.onGrid(p)) || edit.style().length() > MAX_NAME) {
			problem = Component.translatable("message.aliveworkplace.city_plan.bad_line");
		}
		if (problem != null) {
			if (player != null) {
				Chat.actionBar(player, problem.copy().withStyle(ChatFormatting.RED));
			}
			return null;
		}
		List<BlockPos> points = flat(edit.points());
		String style = edit.style();
		if (style.isEmpty()) {
			style = plan.zoneAt(BlockPos.ZERO, points.get(0)).map(CityPlan.Zone::style).orElse("");
		}
		return plan.addRoad(new CityPlan.Road(points, edit.width(), style, true));
	}

	/** The plan with the wall line {@code edit} draws, or null (and the player told) for too many points or one off the grid. */
	@Nullable
	static CityPlan wall(@Nullable ServerPlayer player, Edit edit, CityPlan plan) {
		Component problem = null;
		if (edit.points().size() > CityPlan.MAX_ROAD_POINTS) {
			problem = Component.translatable("message.aliveworkplace.city_plan.too_many_points", CityPlan.MAX_ROAD_POINTS);
		} else if (edit.points().size() < 2 || edit.points().stream().anyMatch(p -> !CityPlan.onGrid(p))) {
			problem = Component.translatable("message.aliveworkplace.city_plan.bad_line");
		}
		if (problem != null) {
			if (player != null) {
				Chat.actionBar(player, problem.copy().withStyle(ChatFormatting.RED));
			}
			return null;
		}
		return plan.withWall(new CityPlan.Wall(flat(edit.points()), edit.closed()));
	}

	/** {@code points} with y set to 0: the plan keeps only where a line runs, not how high. */
	private static List<BlockPos> flat(List<BlockPos> points) {
		return points.stream().map(p -> new BlockPos(p.getX(), 0, p.getZ())).toList();
	}

	/** Sends the hall's plan to {@code player}, if their game can take it. */
	static void sync(ServerPlayer player, VillageHallBlockEntity entity) {
		if (Platform.get().canSend(player, Sync.TYPE)) {
			Platform.get().send(player, new Sync(entity.getBlockPos(), entity.plan(), entity.planUndoSteps()));
		}
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
