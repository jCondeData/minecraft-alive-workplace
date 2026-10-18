package io.github.jcondedata.aliveworkplace.furnish;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Which modded piece each furniture spot of a build became (ROADMAP 37.3), chosen once when the build is planned and
 * saved on its site ({@code BuildSite}) and, once finished, with the finished building, so a restart, a config change or
 * an added mod never changes a build that is going up or standing. A spot not in it keeps its vanilla blocks; that is
 * also every spot of a site saved before 37.3 ({@link #VANILLA}). A piece whose block is gone (the mod was removed) is
 * dropped when the choice is read, so the vanilla piece is built (and put back by upkeep) there.
 */
public record FurnitureChoice(Map<Integer, Placed> spots) {
	/** No spot swapped: the vanilla build. */
	public static final FurnitureChoice VANILLA = new FurnitureChoice(Map.of());

	/** A spot's piece: the spot (index in the blueprint's spots file, its position and kind, to check it still matches), the mod and every cell's state. */
	public record Placed(BlockPos pos, FurnitureSpot.Kind kind, String mod, Map<BlockPos, BlockState> cells) {
	}

	public boolean isVanilla() {
		return spots.isEmpty();
	}

	/**
	 * The choice for a new build of these spots: each spot becomes the piece of the first mod in {@code mods} whose table
	 * has a piece for it that the registry knows and that fits; none, and it stays vanilla. Off ({@code enabled} false): vanilla.
	 */
	public static FurnitureChoice choose(List<FurnitureSpot> spots, List<String> mods, boolean enabled) {
		return choose(spots, mods, enabled, false);
	}

	/**
	 * {@link #choose(List, List, boolean)}, and with {@code partner} (a Pokémon partner lives at the home, owner's answer
	 * 2026-10-18) the home's Trainer's room ({@link #trainersRoom}) gets CobbleFurnies' Poké Ball chair and desk whatever
	 * the order, when those blocks are in the registry and CobbleFurnies is in the list at all; otherwise those spots follow the order like the rest.
	 */
	public static FurnitureChoice choose(List<FurnitureSpot> spots, List<String> mods, boolean enabled, boolean partner) {
		if (!enabled || spots.isEmpty()) {
			return VANILLA;
		}
		Map<Integer, Placed> out = new LinkedHashMap<>();
		if (partner && mods.contains(TRAINER_MOD)) {
			trainersRoom(spots).forEach((i, piece) -> {
				Map<BlockPos, BlockState> cells = piece.resolve(spots.get(i));
				if (cells != null) {
					out.put(i, new Placed(spots.get(i).pos(), spots.get(i).kind(), TRAINER_MOD, Collections.unmodifiableMap(cells)));
				}
			});
		}
		for (int i = 0; i < spots.size(); i++) {
			FurnitureSpot spot = spots.get(i);
			if (out.containsKey(i)) {
				continue;
			}
			for (String mod : mods) {
				FurnishingTable table = FurnishingTable.of(mod).orElse(null);
				FurnishingTable.Piece piece = table == null ? null : table.pieces().get(spot.kind());
				Map<BlockPos, BlockState> cells = piece == null ? null : piece.resolve(spot);
				if (cells != null && (spot.kind() != FurnitureSpot.Kind.BED || sleepable(cells))) {
					out.put(i, new Placed(spot.pos(), spot.kind(), mod, Collections.unmodifiableMap(cells)));
					break;
				}
			}
		}
		return out.isEmpty() ? VANILLA : new FurnitureChoice(Collections.unmodifiableMap(out));
	}

	/** The choice for a new build of {@code blueprint} on this server now (the config's switch and order). */
	public static FurnitureChoice forNewBuild(ResourceLocation blueprint) {
		return choose(BlueprintFurniture.of(blueprint), Furnishings.MODS, Furnishings.ENABLED);
	}

	/**
	 * The choice for a new build of {@code blueprint} placed at {@code origin}: as {@link #forNewBuild(ResourceLocation)},
	 * and a home with a Pokémon partner (one kept in a Pasture Block within {@link io.github.jcondedata.aliveworkplace.work.Partners#RADIUS}
	 * blocks of the plot, the partners' own reach; asked only through the Pokémon extension) gets the Poké Ball chair and desk.
	 */
	public static FurnitureChoice forNewBuild(ResourceLocation blueprint, ServerLevel level, BlockPos origin) {
		List<FurnitureSpot> spots = BlueprintFurniture.of(blueprint);
		boolean partner = Furnishings.ENABLED && !trainersRoom(spots).isEmpty() && hasPartner(level, origin);
		return choose(spots, Furnishings.MODS, Furnishings.ENABLED, partner);
	}

	/** The mod the Trainer's room pieces come from. */
	public static final String TRAINER_MOD = "cobblefurnies";
	/** The Trainer's room's chair and desk (CobbleFurnies' Poké Ball pieces, by block id only). */
	public static final FurnishingTable.Piece POKE_BALL_CHAIR = trainerPiece(TRAINER_MOD + ":poke_ball_chair");
	public static final FurnishingTable.Piece POKE_BALL_DESK = trainerPiece(TRAINER_MOD + ":poke_ball_desk");
	/** How far (blocks, each way) the desk may stand from the chair to be in the same room. */
	private static final int ROOM_REACH = 3;

	private static FurnishingTable.Piece trainerPiece(String block) {
		return new FurnishingTable.Piece(List.of(block), "front", Map.of(), "oak", "white", List.of(new FurnishingTable.FootCell(BlockPos.ZERO, Map.of())));
	}

	/**
	 * A home's Trainer's room: its first chair spot and the desk or table spot nearest it (within {@link #ROOM_REACH}),
	 * by index, with the piece each becomes. Only in a home (a build with a bed spot) and only when CobbleFurnies' Poké Ball
	 * chair is in the registry; otherwise empty.
	 */
	public static Map<Integer, FurnishingTable.Piece> trainersRoom(List<FurnitureSpot> spots) {
		ResourceLocation chairId = ResourceLocation.tryParse(TRAINER_MOD + ":poke_ball_chair");
		if (chairId == null || !BuiltInRegistries.BLOCK.containsKey(chairId) || spots.stream().noneMatch(s -> s.kind() == FurnitureSpot.Kind.BED)) {
			return Map.of();
		}
		int chair = -1;
		for (int i = 0; i < spots.size() && chair < 0; i++) {
			if (spots.get(i).kind() == FurnitureSpot.Kind.CHAIR) {
				chair = i;
			}
		}
		if (chair < 0) {
			return Map.of();
		}
		Map<Integer, FurnishingTable.Piece> out = new LinkedHashMap<>();
		out.put(chair, POKE_BALL_CHAIR);
		BlockPos at = spots.get(chair).pos();
		int desk = -1;
		long best = Long.MAX_VALUE;
		for (int i = 0; i < spots.size(); i++) {
			FurnitureSpot spot = spots.get(i);
			if (spot.kind() != FurnitureSpot.Kind.DESK && spot.kind() != FurnitureSpot.Kind.TABLE) {
				continue;
			}
			BlockPos d = spot.pos().subtract(at);
			if (Math.abs(d.getX()) > ROOM_REACH || Math.abs(d.getY()) > 1 || Math.abs(d.getZ()) > ROOM_REACH) {
				continue;
			}
			long dist = (long) d.getX() * d.getX() + (long) d.getZ() * d.getZ() + (spot.kind() == FurnitureSpot.Kind.DESK ? 0 : 1);
			if (dist < best) {
				best = dist;
				desk = i;
			}
		}
		if (desk >= 0) {
			out.put(desk, POKE_BALL_DESK);
		}
		return out;
	}

	/** Whether a Pokémon is kept in a pasture near {@code origin} (always false without Cobblemon). */
	public static boolean hasPartner(ServerLevel level, BlockPos origin) {
		return io.github.jcondedata.aliveworkplace.work.PokemonPartners.EXTENSION.call(
			p -> !p.pastured(level, origin, io.github.jcondedata.aliveworkplace.work.Partners.RADIUS).isEmpty(), false);
	}

	/**
	 * {@code base} with the chosen pieces in place of their spots' vanilla blocks (a spot whose index, position or kind no
	 * longer matches the spots file is left vanilla).
	 */
	public Blueprint apply(Blueprint base, List<FurnitureSpot> spotsOfBase) {
		if (spots.isEmpty()) {
			return base;
		}
		Map<BlockPos, BlockState> swap = new HashMap<>();
		spots.forEach((i, placed) -> {
			if (i < spotsOfBase.size() && spotsOfBase.get(i).pos().equals(placed.pos()) && spotsOfBase.get(i).kind() == placed.kind()) {
				placed.cells().forEach((at, state) -> swap.put(placed.pos().offset(at), state));
			}
		});
		if (swap.isEmpty()) {
			return base;
		}
		List<Blueprint.Entry> entries = new ArrayList<>(base.blocks().size());
		for (Blueprint.Entry e : base.blocks()) {
			BlockState to = swap.get(e.pos());
			entries.add(to == null ? e : new Blueprint.Entry(e.pos(), to, null));
		}
		return new Blueprint(base.id(), base.size(), List.copyOf(entries), base.entities());
	}

	/** {@code base} as this choice builds it, the spots read from {@code blueprint}'s file. */
	public Blueprint apply(Blueprint base, ResourceLocation blueprint) {
		return spots.isEmpty() ? base : apply(base, BlueprintFurniture.of(blueprint));
	}

	public ListTag save() {
		ListTag list = new ListTag();
		spots.forEach((i, placed) -> {
			CompoundTag tag = new CompoundTag();
			tag.putInt("spot", i);
			tag.putLong("pos", placed.pos().asLong());
			tag.putString("kind", placed.kind().id());
			tag.putString("mod", placed.mod());
			ListTag cells = new ListTag();
			placed.cells().forEach((at, state) -> {
				CompoundTag cell = new CompoundTag();
				cell.putLong("at", at.asLong());
				cell.putString("state", BlockStateParser.serialize(state));
				cells.add(cell);
			});
			tag.put("cells", cells);
			list.add(tag);
		});
		return list;
	}

	/** A saved choice; a piece with a block that no longer exists is dropped (its spot builds vanilla). */
	public static FurnitureChoice load(ListTag list) {
		Map<Integer, Placed> out = new LinkedHashMap<>();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = Nbt.compoundAt(list, i);
			FurnitureSpot.Kind kind = FurnitureSpot.Kind.of(Nbt.getString(tag, "kind"));
			if (kind == null) {
				continue;
			}
			Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
			boolean whole = true;
			ListTag saved = Nbt.getList(tag, "cells", Tag.TAG_COMPOUND);
			for (int j = 0; j < saved.size() && whole; j++) {
				CompoundTag cell = Nbt.compoundAt(saved, j);
				BlockState state = parse(Nbt.getString(cell, "state"));
				if (state == null) {
					whole = false;
				} else {
					cells.put(BlockPos.of(Nbt.getLong(cell, "at")), state);
				}
			}
			if (whole && !cells.isEmpty() && (kind != FurnitureSpot.Kind.BED || sleepable(cells))) {
				out.put(Nbt.getInt(tag, "spot"), new Placed(BlockPos.of(Nbt.getLong(tag, "pos")), kind, Nbt.getString(tag, "mod"), Collections.unmodifiableMap(cells)));
			}
		}
		return out.isEmpty() ? VANILLA : new FurnitureChoice(Collections.unmodifiableMap(out));
	}

	/**
	 * Whether a bed piece's cells hold a bed a villager can claim and sleep in (B166): a bed block (a {@link BedBlock} in
	 * {@code #minecraft:beds}) whose head is one of the HOME point of interest's states. Handcrafted adds its fancy beds'
	 * heads to HOME itself; a furniture mod whose bed isn't a HOME keeps the spot's vanilla bed, so every bed of a
	 * furnished home stays a home for a villager.
	 */
	public static boolean sleepable(Map<BlockPos, BlockState> cells) {
		for (BlockState state : cells.values()) {
			if (state.getBlock() instanceof BedBlock && state.is(BlockTags.BEDS) && state.hasProperty(BedBlock.PART)
				&& state.getValue(BedBlock.PART) == BedPart.HEAD
				&& PoiTypes.forState(state).map(h -> h.is(PoiTypes.HOME)).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	private static BlockState parse(String text) {
		int bracket = text.indexOf('[');
		ResourceLocation id = ResourceLocation.tryParse(bracket < 0 ? text : text.substring(0, bracket));
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			return null;
		}
		try {
			return BlockStateParser.parseForBlock(Lookup.lookup(BuiltInRegistries.BLOCK), text, false).blockState();
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			return null;
		}
	}
}
