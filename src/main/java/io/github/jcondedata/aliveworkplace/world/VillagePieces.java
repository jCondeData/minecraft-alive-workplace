package io.github.jcondedata.aliveworkplace.world;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Our village houses as they stand in a village (ROADMAP 23.10a). Every one of them ({@code aliveworkplace:village/
 * <style>_<house>}, see {@link VillageHouses}) is the same 9 x 10 x 10 house: an outside shared by every house of its
 * style (plinth, timber frame, walls, windows, door, roof and chimney) round a room fitted out for its job. So a house can
 * take another style's outside: its outside blueprint ({@link #outsideId}) is that style's house without the room, and a
 * builder builds it over the house like an upgrade, leaving the room (its job block, bed, chests) as it is.
 *
 * <p>Houses are found by their bed: the house's own bed always stands at the same spot of the room, facing the back wall,
 * so a bed gives where the house would be and which way it faces; the house is there when enough of one style's outside
 * stands round it, and the room says which house it is.
 */
public final class VillagePieces {
	/** The room (in the template's own coordinates): floor, walls' insides and the tie beam. Everything else is the outside. */
	private static final int ROOM_MIN_X = 2, ROOM_MAX_X = 6, ROOM_MIN_Z = 3, ROOM_MAX_Z = 7, ROOM_MAX_Y = 4;
	/** Share of a style's outside that must stand for a house to count as one of ours (players change things). */
	static final float OUTSIDE_MATCH = 0.6f;
	/** Share of a house's room that must match for it to be named that house. */
	static final float ROOM_MATCH = 0.5f;
	private static final String OUTSIDE = "outside/";

	/**
	 * A village house: which one ({@code house}, as in {@link VillageHouses#houseNames()} or {@code builders_workshop}),
	 * the style it was built in (its room's), the style its outside has now ({@code look}) and where it stands.
	 */
	public record Piece(String house, String style, String look, BlueprintData.Placement placement) {
		public ResourceLocation template() {
			return VillagePieces.template(style, house);
		}
	}

	/** Every house a village grows: the builder's workshop and the staffed houses. */
	public static List<String> houses() {
		List<String> out = new ArrayList<>();
		out.add("builders_workshop");
		out.addAll(VillageHouses.houseNames());
		return out;
	}

	public static ResourceLocation template(String style, String house) {
		return AliveWorkplace.id("village/" + style + "_" + house);
	}

	/** A house's name ("Guard House"). */
	public static net.minecraft.network.chat.Component houseName(String house) {
		return net.minecraft.network.chat.Component.translatable("village_piece.aliveworkplace." + house);
	}

	/** A village style's name ("Desert"). */
	public static net.minecraft.network.chat.Component styleName(String style) {
		return net.minecraft.network.chat.Component.translatable("village_style.aliveworkplace." + style);
	}

	/** Whether {@code local} (template coordinates) is in the room, which an outside rebuild leaves alone. */
	public static boolean inRoom(BlockPos local) {
		return local.getX() >= ROOM_MIN_X && local.getX() <= ROOM_MAX_X && local.getZ() >= ROOM_MIN_Z && local.getZ() <= ROOM_MAX_Z
			&& local.getY() <= ROOM_MAX_Y;
	}

	// ---- outside blueprints ------------------------------------------------------------------------------------

	/** The blueprint of {@code house}'s outside in {@code style}: {@code aliveworkplace:outside/<style>/<house>}. */
	public static ResourceLocation outsideId(String style, String house) {
		return AliveWorkplace.id(OUTSIDE + style + "/" + house);
	}

	/** The village house an outside blueprint is taken from, or empty for any other id. */
	public static Optional<ResourceLocation> outsideOf(ResourceLocation id) {
		if (!id.getNamespace().equals(AliveWorkplace.MOD_ID) || !id.getPath().startsWith(OUTSIDE)) {
			return Optional.empty();
		}
		String[] parts = id.getPath().substring(OUTSIDE.length()).split("/");
		if (parts.length != 2 || !VillageHouses.STYLES.contains(parts[0]) || parts[1].isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(ResourceLocation.tryBuild(AliveWorkplace.MOD_ID, "village/" + parts[0] + "_" + parts[1]));
	}

	public static boolean isOutside(ResourceLocation id) {
		return outsideOf(id).isPresent();
	}

	/** {@code [style, house]} of an outside blueprint's id, or empty. */
	public static Optional<String[]> outsideParts(ResourceLocation id) {
		if (outsideOf(id).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(id.getPath().substring(OUTSIDE.length()).split("/"));
	}

	/**
	 * The outside of {@code house}: its blocks outside the room, without the jigsaws (they join the street; the game swaps
	 * them for what's under them) and without air at floor level and below (the ground round a house is left alone).
	 */
	public static Blueprint outside(Blueprint house, ResourceLocation id) {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		for (Blueprint.Entry e : house.blocks()) {
			if (inRoom(e.pos()) || e.state().is(Blocks.JIGSAW) || e.state().is(Blocks.STRUCTURE_VOID) || e.state().isAir() && e.pos().getY() <= 0) {
				continue;
			}
			blocks.add(e);
		}
		return new Blueprint(id, house.size(), List.copyOf(blocks), List.of());
	}

	// ---- finding houses ----------------------------------------------------------------------------------------

	/** Our village houses whose bed is within {@code radius} of {@code centre}, nearest first. */
	public static List<Piece> find(ServerLevel level, BlockPos centre, int radius) {
		MinecraftServer server = level.getServer();
		Optional<Blueprint> plains = BlueprintLibrary.get(server, template("plains", "builders_workshop"));
		if (plains.isEmpty()) {
			return List.of();
		}
		Optional<BlockPos> bed = bedHead(plains.get());
		if (bed.isEmpty()) {
			return List.of();
		}
		Map<String, Blueprint> outsides = new LinkedHashMap<>();
		for (String style : VillageHouses.STYLES) {
			BlueprintLibrary.get(server, template(style, "builders_workshop")).ifPresent(b -> outsides.put(style, b));
		}
		List<BlockPos> beds = level.getPoiManager().getInRange(h -> h.is(PoiTypes.HOME), centre, radius, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(centre))).toList();
		List<Piece> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos head : beds) {
			BlockState state = level.getBlockState(head);
			if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
				continue;
			}
			Optional<Rotation> rotation = rotationFor(state.getValue(BedBlock.FACING));
			if (rotation.isEmpty()) {
				continue;
			}
			BlockPos origin = head.subtract(StructureTemplate.transform(bed.get(), Mirror.NONE, rotation.get(), BlockPos.ZERO));
			if (!seen.add(origin)) {
				continue;
			}
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation.get(), Mirror.NONE);
			at(level, placement, outsides).ifPresent(out::add);
		}
		return out;
	}

	/** Our house standing at {@code placement}, if one does. */
	public static Optional<Piece> at(ServerLevel level, BlueprintData.Placement placement) {
		Map<String, Blueprint> outsides = new LinkedHashMap<>();
		for (String style : VillageHouses.STYLES) {
			BlueprintLibrary.get(level.getServer(), template(style, "builders_workshop")).ifPresent(b -> outsides.put(style, b));
		}
		return at(level, placement, outsides);
	}

	private static Optional<Piece> at(ServerLevel level, BlueprintData.Placement placement, Map<String, Blueprint> outsides) {
		String look = null;
		float best = OUTSIDE_MATCH;
		for (Map.Entry<String, Blueprint> e : outsides.entrySet()) {
			float score = match(level, e.getValue(), placement, false);
			if (score >= best) {
				best = score;
				look = e.getKey();
			}
		}
		if (look == null) {
			return Optional.empty();
		}
		String house = null;
		String style = look;
		best = ROOM_MATCH;
		for (String h : houses()) {
			for (String s : VillageHouses.STYLES) {
				Optional<Blueprint> b = BlueprintLibrary.get(level.getServer(), template(s, h));
				if (b.isEmpty()) {
					continue;
				}
				float score = match(level, b.get(), placement, true);
				if (score > best) {
					best = score;
					house = h;
					style = s;
				}
			}
		}
		return house == null ? Optional.empty() : Optional.of(new Piece(house, style, look, placement));
	}

	/** The style whose outside stands best at {@code placement} now (empty if none stands well enough). */
	public static Optional<String> lookAt(ServerLevel level, BlueprintData.Placement placement) {
		String look = null;
		float best = OUTSIDE_MATCH;
		for (String style : VillageHouses.STYLES) {
			Optional<Blueprint> b = BlueprintLibrary.get(level.getServer(), template(style, "builders_workshop"));
			float score = b.map(blueprint -> match(level, blueprint, placement, false)).orElse(0f);
			if (score >= best) {
				best = score;
				look = style;
			}
		}
		return Optional.ofNullable(look);
	}

	/** Share of the house's solid blocks (in the room, or outside it) that stand in the world as drawn (same block). */
	static float match(ServerLevel level, Blueprint house, BlueprintData.Placement placement, boolean room) {
		int total = 0;
		int same = 0;
		for (Blueprint.Entry e : house.blocks()) {
			if (inRoom(e.pos()) != room || e.state().isAir() || e.state().is(Blocks.JIGSAW) || e.state().is(Blocks.STRUCTURE_VOID)) {
				continue;
			}
			total++;
			BlockPos world = placement.origin().offset(StructureTemplate.transform(e.pos(), placement.mirror(), placement.rotation(), BlockPos.ZERO));
			Block block = level.getBlockState(world).getBlock();
			if (block == e.state().getBlock()) {
				same++;
			}
		}
		return total == 0 ? 0 : (float) same / total;
	}

	/** Where the house's own bed has its head (the template's coordinates). */
	static Optional<BlockPos> bedHead(Blueprint house) {
		for (Blueprint.Entry e : house.blocks()) {
			if (e.state().getBlock() instanceof BedBlock && e.state().getValue(BedBlock.PART) == BedPart.HEAD) {
				return Optional.of(e.pos());
			}
		}
		return Optional.empty();
	}

	/** The turn that makes the template's bed (facing south) face {@code facing}. */
	static Optional<Rotation> rotationFor(Direction facing) {
		for (Rotation r : Rotation.values()) {
			if (r.rotate(Direction.SOUTH) == facing) {
				return Optional.of(r);
			}
		}
		return Optional.empty();
	}

	private VillagePieces() {
	}
}
