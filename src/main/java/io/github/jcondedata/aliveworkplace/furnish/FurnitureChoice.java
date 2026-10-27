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
public record FurnitureChoice(Map<Integer, Placed> spots, boolean modded) {
	/** No spot swapped: the vanilla build. */
	public static final FurnitureChoice VANILLA = new FurnitureChoice(Map.of());

	/**
	 * A choice of pieces only (the vanilla library). {@code modded} (37.12): the build is its modded library copy
	 * ({@link io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary}), chosen with the furniture and saved with it.
	 */
	public FurnitureChoice(Map<Integer, Placed> spots) {
		this(spots, false);
	}

	/** This choice from the modded library ({@code modded} true) or the vanilla one. */
	public FurnitureChoice withLibrary(boolean fromModded) {
		return fromModded == modded ? this : !fromModded && spots.isEmpty() ? VANILLA : new FurnitureChoice(spots, fromModded);
	}

	/** Whether a new build of {@code blueprint} comes from the modded library now (its switch, furnished builds on, every block of its copy here). */
	public static boolean moddedLibrary(ResourceLocation blueprint) {
		return Furnishings.ENABLED && io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary.use(blueprint);
	}

	/** A spot's piece: the spot (index in the blueprint's spots file, its position and kind, to check it still matches), the mod and every cell's state. */
	public record Placed(BlockPos pos, FurnitureSpot.Kind kind, String mod, Map<BlockPos, BlockState> cells) {
	}

	public boolean isVanilla() {
		return spots.isEmpty() && !modded;
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
		return choose(spots, mods, enabled, partner, false);
	}

	/**
	 * {@link #choose(List, List, boolean, boolean)}, and with {@code pokeBallDesks} (the Healing Center and the
	 * Schoolhouse, {@link #hasPokeBallDesks}; owner's answer 2026-10-18) every desk spot is CobbleFurnies' Poké Ball desk
	 * whatever the order, when that block is in the registry and CobbleFurnies is in the list at all.
	 */
	public static FurnitureChoice choose(List<FurnitureSpot> spots, List<String> mods, boolean enabled, boolean partner, boolean pokeBallDesks) {
		if (!enabled || spots.isEmpty()) {
			return VANILLA;
		}
		Map<Integer, Placed> out = new LinkedHashMap<>();
		if (pokeBallDesks && mods.contains(TRAINER_MOD)) {
			for (int i = 0; i < spots.size(); i++) {
				if (spots.get(i).kind() == FurnitureSpot.Kind.DESK) {
					Map<BlockPos, BlockState> cells = POKE_BALL_DESK.resolve(spots.get(i));
					if (cells != null) {
						out.put(i, new Placed(spots.get(i).pos(), spots.get(i).kind(), TRAINER_MOD, Collections.unmodifiableMap(cells)));
					}
				}
			}
		}
		if (partner && mods.contains(TRAINER_MOD)) {
			trainersRoom(spots).forEach((i, piece) -> {
				if (out.containsKey(i)) {
					return;
				}
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

	/**
	 * This choice with each spot not yet started taking the piece the chests hold (ROADMAP 37.13): for a spot whose piece
	 * is one of a set of equivalent pieces ({@link FurnitureEquivalents}: a spruce chair of any furniture mod), the first
	 * mod in {@code mods} whose piece is installed, has the same footprint as the chosen one and is in {@code stock}
	 * (each piece taken off what's left, so two spots don't count the same chair). A spot none of whose pieces is in stock
	 * keeps its piece (the builder waits for it, as before); a spot that {@code started} says is already going up, or
	 * whose piece isn't in a set (CobbleFurnies' Poké Ball chair), is never changed. Returns this choice when nothing changes.
	 */
	public FurnitureChoice restocked(List<FurnitureSpot> spotsOfBase, List<String> mods, java.util.function.IntPredicate started,
									 Map<net.minecraft.world.item.Item, Long> stock) {
		if (spots.isEmpty()) {
			return this;
		}
		Map<net.minecraft.world.item.Item, Long> left = new HashMap<>(stock);
		Map<Integer, Placed> out = new LinkedHashMap<>(spots);
		boolean changed = false;
		for (Map.Entry<Integer, Placed> e : new java.util.TreeMap<>(spots).entrySet()) {
			int i = e.getKey();
			Placed placed = e.getValue();
			if (i < 0 || i >= spotsOfBase.size()) {
				continue;
			}
			FurnitureSpot spot = spotsOfBase.get(i);
			if (!spot.pos().equals(placed.pos()) || spot.kind() != placed.kind()) {
				continue;
			}
			FurnitureEquivalents.Group group = FurnitureEquivalents.of(spot).orElse(null);
			net.minecraft.world.level.block.Block planned = group == null ? null : group.block(placed.mod());
			if (planned == null || placed.cells().values().stream().noneMatch(s -> s.is(planned)) || started.test(i)) {
				continue;
			}
			java.util.Set<BlockPos> footprint = footprint(placed.cells());
			Placed pick = null;
			Map<net.minecraft.world.item.Item, Integer> pickCost = null;
			for (String mod : group.installed(mods)) {
				Placed candidate = mod.equals(placed.mod()) ? placed : as(spot, mod, group.block(mod));
				if (candidate == null || !footprint(candidate.cells()).equals(footprint)
					|| (spot.kind() == FurnitureSpot.Kind.BED && !sleepable(candidate.cells()))) {
					continue;
				}
				Map<net.minecraft.world.item.Item, Integer> cost = cost(candidate.cells());
				if (!cost.isEmpty() && cost.entrySet().stream().allMatch(c -> left.getOrDefault(c.getKey(), 0L) >= c.getValue())) {
					pick = candidate;
					pickCost = cost;
					break;
				}
			}
			if (pick == null) {
				continue;
			}
			pickCost.forEach((item, n) -> left.merge(item, -(long) n, Long::sum));
			if (pick != placed) {
				out.put(i, pick);
				changed = true;
			}
		}
		return changed ? new FurnitureChoice(Collections.unmodifiableMap(out), modded) : this;
	}

	/** {@code spot} as {@code mod}'s piece {@code block}: its table's facing, states and footprint for the kind (one cell facing the spot's way if it has none). */
	@org.jetbrains.annotations.Nullable
	private static Placed as(FurnitureSpot spot, String mod, net.minecraft.world.level.block.Block block) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		FurnishingTable.Piece own = FurnishingTable.of(mod).map(t -> t.pieces().get(spot.kind())).orElse(null);
		FurnishingTable.Piece piece = own == null
			? new FurnishingTable.Piece(List.of(id), "front", Map.of(), "oak", "white", List.of(new FurnishingTable.FootCell(BlockPos.ZERO, Map.of())))
			: new FurnishingTable.Piece(List.of(id), own.facing(), own.states(), own.wood(), own.colour(), own.footprint(), own.needs());
		Map<BlockPos, BlockState> cells = piece.resolve(spot);
		return cells == null ? null : new Placed(spot.pos(), spot.kind(), mod, Collections.unmodifiableMap(cells));
	}

	/** Where a piece has blocks (not air), relative to its spot. */
	private static java.util.Set<BlockPos> footprint(Map<BlockPos, BlockState> cells) {
		java.util.Set<BlockPos> out = new java.util.HashSet<>();
		cells.forEach((at, state) -> {
			if (!state.isAir()) {
				out.add(at);
			}
		});
		return out;
	}

	/** What building a piece's cells uses up (as a builder counts it: a bed's head comes with its foot). */
	public static Map<net.minecraft.world.item.Item, Integer> cost(Map<BlockPos, BlockState> cells) {
		Map<net.minecraft.world.item.Item, Integer> out = new HashMap<>();
		for (BlockState state : cells.values()) {
			if (io.github.jcondedata.aliveworkplace.build.MaterialRules.classify(state) == io.github.jcondedata.aliveworkplace.build.MaterialRules.Kind.SKIP) {
				continue;
			}
			for (io.github.jcondedata.aliveworkplace.build.MaterialRules.Requirement r : io.github.jcondedata.aliveworkplace.build.MaterialRules.requirements(state, null)) {
				out.merge(r.item(), r.count(), Integer::sum);
			}
		}
		return out;
	}

	/** The choice for a new build of {@code blueprint} on this server now (the config's switch and order). */
	public static FurnitureChoice forNewBuild(ResourceLocation blueprint) {
		return choose(BlueprintFurniture.of(blueprint), Furnishings.MODS, Furnishings.ENABLED, false, hasPokeBallDesks(blueprint))
			.withLibrary(moddedLibrary(blueprint));
	}

	/**
	 * Whether the desks of {@code blueprint} are Poké Ball desks (37.5): the Healing Center's and the Schoolhouse's, at
	 * every level (our own blueprints only).
	 */
	public static boolean hasPokeBallDesks(ResourceLocation blueprint) {
		String path = blueprint.getPath();
		return blueprint.getNamespace().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID)
			&& (path.matches("healing_center(_\\d+)?") || path.matches("schoolhouse(_\\d+)?"));
	}

	/**
	 * The choice for a new build of {@code blueprint} placed at {@code origin}: as {@link #forNewBuild(ResourceLocation)},
	 * and a home with a Pokémon partner (one kept in a Pasture Block within {@link io.github.jcondedata.aliveworkplace.work.Partners#RADIUS}
	 * blocks of the plot, the partners' own reach; asked only through the Pokémon extension) gets the Poké Ball chair and desk.
	 */
	public static FurnitureChoice forNewBuild(ResourceLocation blueprint, ServerLevel level, BlockPos origin) {
		List<FurnitureSpot> spots = BlueprintFurniture.of(blueprint);
		boolean partner = Furnishings.ENABLED && !trainersRoom(spots).isEmpty() && hasPartner(level, origin);
		return choose(spots, Furnishings.MODS, Furnishings.ENABLED, partner, hasPokeBallDesks(blueprint)).withLibrary(moddedLibrary(blueprint));
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
		return swapped(base, swap);
	}

	/**
	 * B642: {@code base} (an older drawing of the build) with every saved piece where it was put, whatever today's spots
	 * file says. The choice is saved by spot index and place against the spots file of the day the build was furnished,
	 * and a redrawn blueprint's file moves, adds and drops spots, so {@link #apply(Blueprint, List)} against today's file
	 * would leave the older drawing unfurnished and the pieces standing in the build would read as a player's.
	 */
	public Blueprint applyAsBuilt(Blueprint base) {
		if (spots.isEmpty()) {
			return base;
		}
		Map<BlockPos, BlockState> swap = new HashMap<>();
		spots.values().forEach(placed -> placed.cells().forEach((at, state) -> swap.put(placed.pos().offset(at), state)));
		return swapped(base, swap);
	}

	/**
	 * B642: this choice brought to {@code today}'s spots (a redrawn blueprint's file): a spot of today's file where a
	 * piece of this choice stands (same place, same kind) keeps it; any other spot takes {@code fresh}'s piece for it (or
	 * stays vanilla). A vanilla build stays vanilla. The library ({@link #modded}) is kept.
	 */
	public FurnitureChoice refreshedFor(List<FurnitureSpot> today, FurnitureChoice fresh) {
		if (spots.isEmpty()) {
			return this;
		}
		Map<Integer, Placed> out = new LinkedHashMap<>();
		for (int i = 0; i < today.size(); i++) {
			FurnitureSpot spot = today.get(i);
			Placed kept = null;
			for (Placed placed : spots.values()) {
				if (placed.pos().equals(spot.pos()) && placed.kind() == spot.kind()) {
					kept = placed;
					break;
				}
			}
			Placed piece = kept != null ? kept : fresh.spots().get(i);
			if (piece != null) {
				out.put(i, piece);
			}
		}
		return out.isEmpty() && !modded ? VANILLA : new FurnitureChoice(Collections.unmodifiableMap(out), modded);
	}

	private static Blueprint swapped(Blueprint base, Map<BlockPos, BlockState> swap) {
		if (swap.isEmpty()) {
			return base;
		}
		List<Blueprint.Entry> entries = new ArrayList<>(base.blocks().size());
		java.util.Set<BlockPos> emptied = new java.util.HashSet<>();
		for (Blueprint.Entry e : base.blocks()) {
			BlockState to = swap.get(e.pos());
			entries.add(to == null ? e : new Blueprint.Entry(e.pos(), to, null));
			if (to != null && to.isAir() && !e.state().isAir()) {
				emptied.add(e.pos());
			}
		}
		// B300: a pot or lantern on a cell the piece left empty (a fence-and-carpet table become a one-block one) sits down on the piece
		return Resting.reseat(new Blueprint(base.id(), base.size(), List.copyOf(entries), base.entities()), emptied);
	}

	/** {@code base} as this choice builds it, the spots read from {@code blueprint}'s file. */
	public Blueprint apply(Blueprint base, ResourceLocation blueprint) {
		// 37.12: the modded library's copy has the vanilla build's shape and furniture spots, so the pieces go in the same places
		Blueprint built = modded ? io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary.copy(blueprint).orElse(base) : base;
		return spots.isEmpty() ? built : apply(built, BlueprintFurniture.of(blueprint));
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
		if (modded) {
			CompoundTag library = new CompoundTag();
			library.putString("library", "modded"); // 37.12 (read back by load; a version before it drops the entry: no kind)
			list.add(library);
		}
		return list;
	}

	/** A saved choice; a piece with a block that no longer exists is dropped (its spot builds vanilla). */
	public static FurnitureChoice load(ListTag list) {
		Map<Integer, Placed> out = new LinkedHashMap<>();
		boolean modded = false;
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = Nbt.compoundAt(list, i);
			if ("modded".equals(Nbt.getString(tag, "library"))) {
				modded = true; // 37.12: built from the modded library
				continue;
			}
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
		return out.isEmpty() && !modded ? VANILLA : new FurnitureChoice(Collections.unmodifiableMap(out), modded);
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
