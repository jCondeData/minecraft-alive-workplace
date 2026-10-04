package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A map of the village, drawn at the Village Hall for an empty map: the land round the hall as it is today (a finished,
 * locked map, like one copied on a cartography table), centred on the hall, with a banner for every building the
 * builders finished there — coloured by what it is, the workplaces named — and the legend on the map's tooltip.
 */
public final class VillageMaps {
	/** Half a map's width at the closest scale: the village's reach. */
	static final int HALF = 64;

	/** What a building is, for its banner's colour. */
	public enum Kind {
		HALL(DyeColor.RED), HOMES(DyeColor.WHITE), LEARNING(DyeColor.BLUE), CARE(DyeColor.PINK), TRADE(DyeColor.YELLOW),
		FARMS(DyeColor.LIME), DEFENCE(DyeColor.BLACK), WORKSHOPS(DyeColor.ORANGE), DECORATIONS(DyeColor.LIGHT_BLUE), OTHER(DyeColor.PURPLE);

		public final DyeColor color;

		Kind(DyeColor color) {
			this.color = color;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The kind of building {@code blueprint} is (styled blueprints go by their base; upgrades by their first tier), or empty to leave it off the map. */
	public static Optional<Kind> kindOf(ResourceLocation blueprint) {
		ResourceLocation id = BlueprintStyles.parse(blueprint).map(BlueprintStyles.Styled::base).orElse(blueprint);
		String path = id.getPath();
		if (path.startsWith("shapes/") || path.startsWith("camp/")) {
			return Optional.empty();
		}
		String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("_\\d+$", "");
		return Optional.ofNullable(switch (name) {
			case "starter_cottage", "stone_house", "terrace", "inn" -> Kind.HOMES;
			case "schoolhouse", "library", "research_lab" -> Kind.LEARNING;
			case "healing_center", "pokemon_center", "graveyard" -> Kind.CARE;
			case "storehouse", "supply_shop", "market_stall", "market_square" -> Kind.TRADE;
			case "berry_farm", "ranch", "apiary_garden", "flower_shop", "compost_yard", "sifting_shed" -> Kind.FARMS;
			case "lookout_tower", "barracks", "gatehouse", "wall_tower", "palisade_gate" -> Kind.DEFENCE;
			case "tinkers_workshop", "nether_gate" -> Kind.WORKSHOPS;
			case "well", "fountain", "gazebo", "chapel" -> Kind.DECORATIONS;
			case "town_hall" -> Kind.HALL;
			case "street_lamp", "park_bench", "palisade", "stone_wall" -> null;
			default -> Kind.OTHER;
		});
	}

	/**
	 * Draws the map of the village round {@code hall} for an empty map from {@code player}'s inventory (none needed in
	 * creative) and gives it to them; returns what to tell them.
	 */
	public static Component draw(ServerLevel level, BlockPos hall, ServerPlayer player) {
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize() && slot < 0; i++) {
			if (player.getInventory().getItem(i).is(Items.MAP)) {
				slot = i;
			}
		}
		if (slot < 0 && !player.getAbilities().instabuild) {
			return Component.translatable("message.aliveworkplace.hall.map_needs_map").withStyle(ChatFormatting.YELLOW);
		}
		ItemStack map = map(level, hall);
		if (slot >= 0 && !player.getAbilities().instabuild) {
			player.getInventory().getItem(slot).shrink(1);
		}
		if (!player.getInventory().add(map)) {
			player.drop(map, false);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
		return Component.translatable("message.aliveworkplace.hall.map_drawn", VillageHalls.name(level, hall)).withStyle(ChatFormatting.GREEN);
	}

	/** The map of the village round {@code hall}, as a filled map. */
	public static ItemStack map(ServerLevel level, BlockPos hall) {
		List<MapBanner> banners = new ArrayList<>();
		Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
		Component village = VillageHalls.name(level, hall);
		for (Mark mark : marks(level, hall, HALF)) {
			boolean named = mark.kind() == Kind.HALL || (mark.kind() != Kind.HOMES && mark.kind() != Kind.DECORATIONS);
			banners.add(new MapBanner(mark.pos(), mark.kind().color, named ? Optional.of(mark.name()) : Optional.empty()));
			counts.merge(mark.kind(), 1, Integer::sum);
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("dimension", Ids.of(level.dimension()).toString());
		tag.putInt("xCenter", hall.getX());
		tag.putInt("zCenter", hall.getZ());
		tag.putByte("scale", (byte) 0);
		tag.putBoolean("trackingPosition", true);
		tag.putBoolean("unlimitedTracking", false);
		tag.putBoolean("locked", true);
		byte[] colors = colors(level, hall);
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && !entity.plan().isEmpty()) {
			drawPlan(colors, entity.plan()); // the plan on the map (27.4), so it can hang by the hall
		}
		tag.putByteArray("colors", colors);
		MapBanner.LIST_CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), banners).result()
			.ifPresent(t -> tag.put("banners", t));
		tag.put("frames", new ListTag());
		MapItemSavedData data = MapItemSavedData.load(tag, level.registryAccess());
		MapId id = level.getFreeMapId();
		level.setMapData(id, data);
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, id);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.aliveworkplace.village_map", village).withStyle(s -> s.withItalic(false)));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("item.aliveworkplace.village_map.drawn", Chronicle.day(level)).withStyle(ChatFormatting.GRAY));
		counts.forEach((kind, n) -> lore.add(Component.translatable("item.aliveworkplace.village_map.legend",
			Component.translatable("color.minecraft." + kind.color.getName()), Component.translatable("item.aliveworkplace.village_map." + kind.key()), n)
			.withStyle(ChatFormatting.DARK_GRAY)));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	/** A road on the village map: the colour of a dirt path. */
	public static final byte ROAD = MapColor.DIRT.getPackedId(MapColor.Brightness.HIGH);
	/** The wall line on the village map: dark stone. */
	public static final byte WALL = MapColor.STONE.getPackedId(MapColor.Brightness.LOWEST);

	/**
	 * Draws {@code plan} on a village map's {@code colors} (128×128, a pixel a block, centred on the hall; ROADMAP 27.4):
	 * every zone cell's land blended half and half with its kind's map tint, the pixels along a zone's edge in the tint
	 * itself, then the roads (as wide as they are) and the wall line over them.
	 */
	public static void drawPlan(byte[] colors, io.github.jcondedata.aliveworkplace.city.CityPlan plan) {
		int size = 128;
		int[] of = new int[size * size];
		java.util.Arrays.fill(of, -1);
		BlockPos origin = BlockPos.ZERO;
		for (int pz = 0; pz < size; pz++) {
			for (int px = 0; px < size; px++) {
				int cell = io.github.jcondedata.aliveworkplace.city.CityPlan.cellAt(origin, new BlockPos(px - HALF, 0, pz - HALF));
				for (int i = 0; i < plan.zones().size() && cell >= 0; i++) {
					if (plan.zones().get(i).has(cell)) {
						of[px + pz * size] = i;
						break;
					}
				}
			}
		}
		for (int pz = 0; pz < size; pz++) {
			for (int px = 0; px < size; px++) {
				int zone = of[px + pz * size];
				if (zone < 0) {
					continue;
				}
				int tint = io.github.jcondedata.aliveworkplace.city.CityZones.get(plan.zones().get(zone).kind())
					.map(io.github.jcondedata.aliveworkplace.city.CityZones.Kind::mapTint).orElse(0x808080);
				boolean edge = px == 0 || pz == 0 || px == size - 1 || pz == size - 1 || of[px - 1 + pz * size] != zone
					|| of[px + 1 + pz * size] != zone || of[px + (pz - 1) * size] != zone || of[px + (pz + 1) * size] != zone;
				colors[px + pz * size] = edge ? nearest(tint) : tinted(colors[px + pz * size], tint);
			}
		}
		for (io.github.jcondedata.aliveworkplace.city.CityPlan.Road road : plan.roads()) {
			line(colors, road.points(), false, road.width(), ROAD);
		}
		plan.wall().ifPresent(w -> line(colors, w.points(), w.closed(), 2, WALL));
	}

	/** A zone's land pixel: the land's colour and {@code tint} half and half (the tint alone on land nobody has seen). */
	public static byte tinted(byte land, int tint) {
		if (land == 0) {
			return nearest(tint);
		}
		int abgr = MapColor.getColorFromPackedId(land & 0xFF);
		int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
		return nearest((((r + ((tint >> 16) & 0xFF)) / 2) << 16) | (((g + ((tint >> 8) & 0xFF)) / 2) << 8) | ((b + (tint & 0xFF)) / 2));
	}

	/** The map colour nearest {@code rgb} (0xRRGGBB). */
	public static byte nearest(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		int best = 0;
		long bestD = Long.MAX_VALUE;
		for (int packed = 4; packed < 256; packed++) {
			MapColor base = MapColor.byId(packed >> 2);
			if (base == null || base == MapColor.NONE) {
				continue;
			}
			int abgr = MapColor.getColorFromPackedId(packed);
			int dr = (abgr & 0xFF) - r, dg = ((abgr >> 8) & 0xFF) - g, db = ((abgr >> 16) & 0xFF) - b;
			long d = 3L * dr * dr + 4L * dg * dg + 2L * db * db;
			if (d < bestD) {
				bestD = d;
				best = packed;
			}
		}
		return (byte) best;
	}

	/** {@code points} (offsets from the hall) drawn {@code width} pixels wide in {@code color}. */
	private static void line(byte[] colors, List<BlockPos> points, boolean closed, int width, byte color) {
		int n = points.size();
		int r0 = -(width - 1) / 2, r1 = width / 2;
		for (int i = 0; i + 1 < n || (closed && n > 2 && i < n); i++) {
			BlockPos a = points.get(i);
			BlockPos b = points.get((i + 1) % n);
			int steps = Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ()));
			for (int s = 0; s <= steps; s++) {
				int x = a.getX() + (steps == 0 ? 0 : Math.round((b.getX() - a.getX()) * s / (float) steps)) + HALF;
				int z = a.getZ() + (steps == 0 ? 0 : Math.round((b.getZ() - a.getZ()) * s / (float) steps)) + HALF;
				for (int dz = r0; dz <= r1; dz++) {
					for (int dx = r0; dx <= r1; dx++) {
						int px = x + dx, pz = z + dz;
						if (px >= 0 && pz >= 0 && px < 128 && pz < 128) {
							colors[px + pz * 128] = color;
						}
					}
				}
			}
		}
	}

	/** A banner on the village map: where (at the hall's height), what the building is and its name. */
	public record Mark(BlockPos pos, Kind kind, Component name) {
	}

	/** The hall and every finished building whose middle is within {@code half} blocks of {@code hall} (east-west and north-south). */
	public static List<Mark> marks(ServerLevel level, BlockPos hall, int half) {
		List<Mark> out = new ArrayList<>();
		out.add(new Mark(hall.immutable(), Kind.HALL, VillageHalls.name(level, hall)));
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, Math.max(VillageHalls.RADIUS, half) + 16)) {
			Optional<Kind> kind = kindOf(f.structure());
			if (kind.isEmpty()) {
				continue;
			}
			BlockPos centre = BlueprintLibrary.get(level, f.structure())
				.map(b -> BlueprintOutline.bounds(f.placement(), b.size()).getCenter())
				.orElse(f.placement().origin());
			if (Math.abs(centre.getX() - hall.getX()) > half - 1 || Math.abs(centre.getZ() - hall.getZ()) > half - 1) {
				continue;
			}
			out.add(new Mark(new BlockPos(centre.getX(), hall.getY(), centre.getZ()), kind.get(), Blueprints.displayName(f.structure())));
		}
		return out;
	}

	/**
	 * The land round {@code hall} as map colours, a pixel a block, north-lit like a vanilla map: the top block of each
	 * column (water shaded by its depth). Columns in chunks that aren't loaded stay blank.
	 */
	static byte[] colors(ServerLevel level, BlockPos hall) {
		return colors(level, hall, HALF, 1);
	}

	/**
	 * The same for the square of {@code half} blocks round {@code hall}, 128 pixels a side, a pixel every {@code step}
	 * blocks (1 for a 128-block square): the City Plan screen's map (27.3).
	 */
	public static byte[] colors(ServerLevel level, BlockPos hall, int half, int step) {
		byte[] colors = new byte[128 * 128];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int px = 0; px < 128; px++) {
			int x = hall.getX() - half + px * step;
			double north = Double.NaN;
			for (int pz = -1; pz < 128; pz++) {
				int z = hall.getZ() - half + pz * step;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					north = Double.NaN;
					continue;
				}
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				pos.set(x, y, z);
				BlockState state = level.getBlockState(pos);
				while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinBuildHeight()) {
					pos.setY(--y);
					state = level.getBlockState(pos);
				}
				int depth = 0;
				if (!state.getFluidState().isEmpty()) {
					BlockPos.MutableBlockPos below = pos.mutable();
					while (depth < 16 && below.getY() > level.getMinBuildHeight() && !level.getBlockState(below.move(0, -1, 0)).getFluidState().isEmpty()) {
						depth++;
					}
				}
				MapColor color = state.getMapColor(level, pos);
				MapColor.Brightness brightness;
				if (color == MapColor.WATER) {
					double f = depth * 0.1 + ((px + pz) & 1) * 0.2;
					brightness = f < 0.5 ? MapColor.Brightness.HIGH : f > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				} else if (Double.isNaN(north)) {
					brightness = MapColor.Brightness.NORMAL;
				} else {
					double slope = (y - north) * 4 / 5.0 + (((px + pz) & 1) - 0.5) * 0.4;
					brightness = slope > 0.6 ? MapColor.Brightness.HIGH : slope < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				}
				north = y;
				if (pz >= 0 && color != MapColor.NONE) {
					colors[px + pz * 128] = color.getPackedId(brightness);
				}
			}
		}
		return colors;
	}

	private VillageMaps() {
	}
}
