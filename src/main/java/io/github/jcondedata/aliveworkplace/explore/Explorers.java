package io.github.jcondedata.aliveworkplace.explore;

import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.work.Hiring;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Cartographers as the village's explorers (see {@link ExplorerWork}): what they eat on the way, what they fight with,
 * what they find, and the maps they draw to places nearby.
 */
public final class Explorers {
	/** What turns up anywhere, and what each kind of land adds (one roll at every stop). */
	public static final ResourceKey<LootTable> FINDS = table("explorer/finds");
	/** What an explorer with a sword or axe brings back from the animals and monsters about. */
	public static final ResourceKey<LootTable> HUNTING = table("explorer/hunting");
	/** Apricorns, berries, evolution stones, fossils... (the table only loads with Cobblemon). */
	public static final ResourceKey<LootTable> COBBLEMON = table("explorer/cobblemon");
	/** The places an explorer draws maps to. */
	public static final TagKey<Structure> MAP_PLACES = TagKey.create(Registries.STRUCTURE, AliveWorkplace.id("explorer_maps"));
	/** How far out (in chunks) an explorer looks for a place to draw a map to (a vanilla treasure map's reach). */
	static final int MAP_SEARCH_CHUNKS = 50;
	/** Food worth packing: filling, and with no bad effects (no raw chicken, rotten flesh or golden apples). */
	static final int MIN_NUTRITION = 4;

	private static ResourceKey<LootTable> table(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id(path));
	}

	public static boolean isExplorer(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.CARTOGRAPHER;
	}

	/** Food an explorer packs: one for every stop of the trip. */
	public static boolean isFood(ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food != null && food.nutrition() >= MIN_NUTRITION && food.effects().isEmpty();
	}

	/** A sword or an axe to hunt with (and keep the monsters off). */
	public static boolean isWeapon(ItemStack stack) {
		return (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) && stack.isDamageableItem()
			&& stack.getMaxDamage() - stack.getDamageValue() > 4;
	}

	/** What {@code explorer} finds searching around {@code pos}: the land's finds, the hunt's with a weapon, and Cobblemon's. */
	public static List<ItemStack> finds(ServerLevel level, Villager explorer, BlockPos pos, boolean armed) {
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
			.withOptionalParameter(LootContextParams.THIS_ENTITY, explorer)
			.withLuck(BuilderLevels.level(explorer) - 1 + io.github.jcondedata.aliveworkplace.legend.Gifted.lootLuck(explorer))
			.create(LootContextParamSets.CHEST);
		List<ItemStack> out = new ArrayList<>(roll(level, FINDS, params));
		if (armed) {
			out.addAll(roll(level, HUNTING, params));
		}
		out.addAll(roll(level, COBBLEMON, params)); // an empty table without Cobblemon
		out.removeIf(ItemStack::isEmpty);
		return out;
	}

	private static List<ItemStack> roll(ServerLevel level, ResourceKey<LootTable> key, LootParams params) {
		return level.getServer().reloadableRegistries().getLootTable(key).getRandomItems(params);
	}

	/** Somewhere worth a map, with what it is. */
	public record Place(BlockPos pos, Holder<Structure> structure) {
	}

	/**
	 * The nearest place worth a map around {@code from} that nobody has a map to yet (vanilla's rule for explorer maps:
	 * each place is drawn once). Null when there is none within {@link #MAP_SEARCH_CHUNKS} chunks, or the world has no
	 * structures.
	 */
	@Nullable
	public static Place findPlace(ServerLevel level, BlockPos from) {
		if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
			return null;
		}
		Optional<HolderSet.Named<Structure>> places = Lookup.tag(Lookup.registry(level.registryAccess(), Registries.STRUCTURE), MAP_PLACES);
		if (places.isEmpty()) {
			return null;
		}
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, places.get(), from, MAP_SEARCH_CHUNKS, true);
		return found == null ? null : new Place(found.getFirst(), found.getSecond());
	}

	/** A map to {@code place}, like a vanilla explorer map: the land around it sketched, the place marked and named. */
	public static ItemStack mapTo(ServerLevel level, Place place) {
		ItemStack map = MapItem.create(level, place.pos().getX(), place.pos().getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, place.pos(), "+", marker(place.structure()));
		map.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.explorer_map", placeName(place.structure())));
		return map;
	}

	/** The map marker for a place: the village or temple icons vanilla maps use, a red X for the rest. */
	static Holder<MapDecorationType> marker(Holder<Structure> structure) {
		String path = structure.unwrapKey().map(k -> Ids.of(k).getPath()).orElse("");
		if (path.contains("village")) {
			return path.contains("desert") ? MapDecorationTypes.DESERT_VILLAGE : path.contains("savanna") ? MapDecorationTypes.SAVANNA_VILLAGE
				: path.contains("snowy") ? MapDecorationTypes.SNOWY_VILLAGE : path.contains("taiga") ? MapDecorationTypes.TAIGA_VILLAGE
				: MapDecorationTypes.PLAINS_VILLAGE;
		}
		if (path.contains("jungle")) {
			return MapDecorationTypes.JUNGLE_TEMPLE;
		}
		if (path.contains("swamp_hut")) {
			return MapDecorationTypes.SWAMP_HUT;
		}
		if (path.contains("mansion")) {
			return MapDecorationTypes.WOODLAND_MANSION;
		}
		if (path.contains("monument")) {
			return MapDecorationTypes.OCEAN_MONUMENT;
		}
		if (path.contains("trial_chambers")) {
			return MapDecorationTypes.TRIAL_CHAMBERS;
		}
		return MapDecorationTypes.RED_X;
	}

	/** "Plains Village", "Ruined Portal": ours in the language file, or made from the id for other mods' places. */
	static Component placeName(Holder<Structure> structure) {
		ResourceLocation id = structure.unwrapKey().map(ResourceKey::location).orElse(AliveWorkplace.id("unknown"));
		String key = "structure." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		return Component.translatableWithFallback(key, pretty(id.getPath()));
	}

	/** "village/plains_town" → "Plains Town". */
	static String pretty(String path) {
		String last = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder out = new StringBuilder();
		for (String word : last.split("_")) {
			if (!word.isEmpty()) {
				if (!out.isEmpty()) {
					out.append(' ');
				}
				out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
			}
		}
		return out.toString();
	}

	/** Hires a Cartographer (sneak-right-click with a compass). */
	public static InteractionResult hire(ServerPlayer player, Villager villager) {
		return Hiring.hire(player, villager, Component.translatable("message.aliveworkplace.explorer.hired", villager.getDisplayName()));
	}

	private Explorers() {
	}
}
