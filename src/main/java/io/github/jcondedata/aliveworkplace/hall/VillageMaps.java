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
		banners.add(new MapBanner(hall.immutable(), Kind.HALL.color, Optional.of(village)));
		counts.merge(Kind.HALL, 1, Integer::sum);
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS + 16)) {
			Optional<Kind> kind = kindOf(f.structure());
			if (kind.isEmpty()) {
				continue;
			}
			BlockPos centre = BlueprintLibrary.get(level, f.structure())
				.map(b -> BlueprintOutline.bounds(f.placement(), b.size()).getCenter())
				.orElse(f.placement().origin());
			if (Math.abs(centre.getX() - hall.getX()) > HALF - 1 || Math.abs(centre.getZ() - hall.getZ()) > HALF - 1) {
				continue;
			}
			boolean named = kind.get() != Kind.HOMES && kind.get() != Kind.DECORATIONS;
			banners.add(new MapBanner(new BlockPos(centre.getX(), hall.getY(), centre.getZ()), kind.get().color,
				named ? Optional.of(Blueprints.displayName(f.structure())) : Optional.empty()));
			counts.merge(kind.get(), 1, Integer::sum);
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("dimension", Ids.of(level.dimension()).toString());
		tag.putInt("xCenter", hall.getX());
		tag.putInt("zCenter", hall.getZ());
		tag.putByte("scale", (byte) 0);
		tag.putBoolean("trackingPosition", true);
		tag.putBoolean("unlimitedTracking", false);
		tag.putBoolean("locked", true);
		tag.putByteArray("colors", colors(level, hall));
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

	/**
	 * The land round {@code hall} as map colours, a pixel a block, north-lit like a vanilla map: the top block of each
	 * column (water shaded by its depth). Columns in chunks that aren't loaded stay blank.
	 */
	static byte[] colors(ServerLevel level, BlockPos hall) {
		byte[] colors = new byte[128 * 128];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int px = 0; px < 128; px++) {
			int x = hall.getX() - HALF + px;
			double north = Double.NaN;
			for (int pz = -1; pz < 128; pz++) {
				int z = hall.getZ() - HALF + pz;
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
