package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * A village's colours (ROADMAP 30.13), set at the hall with a Village Banner. They show on its buildings (a builder hangs
 * a wall banner over a finished building's front door when a banner of the base colour is in their chests), on its
 * guards' and mercenaries' plain shields, on the hall's trade routes page and the Book of Edicts, and at its festivals
 * (a banner in the colours near the square makes the mood better and longer, see {@link Festivals}).
 * {@code villageBanners} in the config.
 */
public final class VillageBanners {
	/** {@code villageBanners} in the config. Off: no Village Banners can be crafted or set, and the colours show nowhere; they stay saved. */
	public static boolean ENABLED = true;
	/** How near the festival square a banner in the colours must fly. */
	public static final int FESTIVAL_RANGE = 16;

	private static final Map<DyeColor, Block> WALL_BANNERS = new EnumMap<>(DyeColor.class);

	/** A banner design: base colour and pattern layers. */
	public record Colours(DyeColor base, BannerPatternLayers patterns) {
		/** The vanilla banner item of this design. */
		public ItemStack banner() {
			return VillageBannerItem.asVanilla(base, patterns);
		}

		/** True if {@code banner} (a placed banner) shows this design. */
		public boolean on(BannerBlockEntity banner) {
			return banner.getBaseColor() == base && banner.getPatterns().equals(patterns);
		}
	}

	/** The colours of the hall's village, or null when none were set (or Village Banners are off). */
	@Nullable
	public static Colours of(VillageHallBlockEntity hall) {
		return ENABLED ? hall.colours() : null;
	}

	/** The colours of the village whose hall is at {@code hall}, or null. */
	@Nullable
	public static Colours of(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? of(entity) : null;
	}

	/** The colours of the village {@code pos} is in (its nearest hall), or null. */
	@Nullable
	public static Colours around(ServerLevel level, BlockPos pos) {
		if (!ENABLED) {
			return null;
		}
		return VillageHalls.nearest(level, pos).map(hall -> of(level, hall)).orElse(null);
	}

	/** True if a banner in {@code colours} stands or hangs within {@link #FESTIVAL_RANGE} blocks of {@code centre}. */
	public static boolean fliesNear(ServerLevel level, BlockPos centre, Colours colours) {
		int r = FESTIVAL_RANGE;
		for (int cx = (centre.getX() - r) >> 4; cx <= (centre.getX() + r) >> 4; cx++) {
			for (int cz = (centre.getZ() - r) >> 4; cz <= (centre.getZ() + r) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (BlockEntity entity : level.getChunk(cx, cz).getBlockEntities().values()) {
					BlockPos p = entity.getBlockPos();
					if (entity instanceof BannerBlockEntity banner && Math.abs(p.getX() - centre.getX()) <= r && Math.abs(p.getY() - centre.getY()) <= r
						&& Math.abs(p.getZ() - centre.getZ()) <= r && colours.on(banner)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Paints {@code shield} in {@code colours} if it is a plain shield (no base colour: never painted, by a player or
	 * anyone). Returns true if it was painted.
	 */
	public static boolean paint(ItemStack shield, Colours colours) {
		if (shield.isEmpty() || !(shield.getItem() instanceof ShieldItem) || shield.has(DataComponents.BASE_COLOR)) {
			return false;
		}
		shield.set(DataComponents.BASE_COLOR, colours.base());
		if (!colours.patterns().layers().isEmpty()) {
			shield.set(DataComponents.BANNER_PATTERNS, colours.patterns());
		}
		return true;
	}

	/** Paints the plain shield in {@code villager}'s off hand in their village's colours (a guard gearing up, a mercenary hired). */
	public static boolean paintShield(ServerLevel level, Villager villager) {
		ItemStack shield = villager.getItemBySlot(EquipmentSlot.OFFHAND);
		if (!(shield.getItem() instanceof ShieldItem) || shield.has(DataComponents.BASE_COLOR)) {
			return false;
		}
		Colours colours = around(level, villager.blockPosition());
		return colours != null && paint(shield, colours);
	}

	/** The wall banner block of {@code colour}. */
	public static Block wallBanner(DyeColor colour) {
		synchronized (WALL_BANNERS) {
			if (WALL_BANNERS.isEmpty()) {
				for (Block block : BuiltInRegistries.BLOCK) {
					if (block instanceof WallBannerBlock wall) {
						WALL_BANNERS.putIfAbsent(wall.getColor(), wall);
					}
				}
			}
			return WALL_BANNERS.get(colour);
		}
	}

	/** Where a banner hangs over a door: the spot and the way it faces (out of the building). */
	public record Spot(BlockPos pos, Direction out) {
	}

	/**
	 * A building inside {@code box} was just finished: if it stands in a village with colours and a banner of their base
	 * colour is in {@code supplies}, one is taken and hung over the building's front door in the colours. Returns where,
	 * or null (no colours, no banner in the chests, no door with a wall over it).
	 */
	@Nullable
	public static BlockPos hangOverDoor(ServerLevel level, BoundingBox box, List<BlockPos> supplies) {
		if (!ENABLED) {
			return null;
		}
		Optional<BlockPos> hall = VillageHalls.nearest(level, box.getCenter());
		Colours colours = hall.map(h -> of(level, h)).orElse(null);
		if (colours == null) {
			return null;
		}
		Spot spot = overFrontDoor(level, box, hall.get());
		Block wall = wallBanner(colours.base());
		if (spot == null || wall == null) {
			return null;
		}
		ItemStack taken = SupplyContainers.takeOne(level, supplies, s -> s.is(BannerBlock.byColor(colours.base()).asItem()));
		if (taken.isEmpty()) {
			return null;
		}
		BlockState state = wall.defaultBlockState().setValue(WallBannerBlock.FACING, spot.out());
		level.setBlock(spot.pos(), state, Block.UPDATE_ALL);
		if (level.getBlockEntity(spot.pos()) instanceof BannerBlockEntity banner) {
			banner.fromItem(colours.banner(), colours.base());
			banner.setChanged();
			level.sendBlockUpdated(spot.pos(), state, state, Block.UPDATE_ALL);
		}
		return spot.pos();
	}

	/**
	 * Where a banner goes over the front door of the building in {@code box}: the front door is the lowest door that
	 * leads out of the building soonest (the one nearest {@code hall} if several do); the banner hangs on the outside of
	 * the wall over it: a block above the door's top if the wall goes up that far (its cloth then falls just over the
	 * door), higher if a hood or an awning is in the way, else right on top of it; a block to the side if nothing over
	 * the door will do. Null when the front door has no sturdy wall with room in front of it.
	 */
	@Nullable
	public static Spot overFrontDoor(ServerLevel level, BoundingBox box, BlockPos hall) {
		BlockPos door = null;
		Direction out = null;
		int lowest = Integer.MAX_VALUE;
		int bestSteps = Integer.MAX_VALUE;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), Math.min(box.maxY(), box.minY() + 3), box.maxZ())) {
			BlockState state = level.getBlockState(p);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || p.getY() > lowest) {
				continue;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int steps = 1;
				BlockPos o = p.relative(side);
				while (box.isInside(o) && steps < 6) {
					o = o.relative(side);
					steps++;
				}
				if (box.isInside(o)) {
					continue;
				}
				double dist = o.distSqr(hall);
				if (p.getY() < lowest || steps < bestSteps || steps == bestSteps && dist < bestDist) {
					lowest = p.getY();
					bestSteps = steps;
					bestDist = dist;
					door = p.immutable();
					out = side;
				}
			}
		}
		if (door == null) {
			return null;
		}
		// Right over the door first, then a block to either side (past a lantern or a bell hung there); on each, a block
		// over the door's top (the cloth falls just over it), higher up past a hood or an awning, else right on top.
		for (int side : new int[] {0, -1, 1}) {
			for (int up : new int[] {3, 4, 5, 2}) {
				BlockPos wall = door.above(up).relative(out.getClockWise(), side);
				BlockPos at = wall.relative(out);
				boolean room = level.getBlockState(at).isAir() && (up == 2 || level.getBlockState(at.below()).isAir());
				if (room && level.getBlockState(wall).isFaceSturdy(level, wall, out)) {
					return new Spot(at, out);
				}
			}
		}
		return null;
	}

	private VillageBanners() {
	}
}
