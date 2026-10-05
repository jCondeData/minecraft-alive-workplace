package io.github.jcondedata.aliveworkplace.habitat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Extension;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Habitat Keeper (ROADMAP 28.10): a villager at a Pasture Block, picked with a honey bottle, who keeps the wild
 * Pokémon round her pasture: Poké Snacks set out on lure spots, Saccharine logs slathered with honey, and the shiny,
 * rare and Alpha ones she sights told to the village. Cobblemon's blocks are known by id only; its snacks and wild
 * Pokémon through {@link Snacks}, filled by the compat layer. Needs Cobblemon; config {@code habitatKeepers} (and
 * {@code habitatSightings} for the sightings).
 */
public final class HabitatKeepers {
	/** Config switch {@code habitatKeepers}: off, a honey bottle picks no job at a pasture and keepers already hired stand idle. */
	public static boolean ENABLED = true;
	/** Config switch {@code habitatSightings}: off, she tells nobody of the Pokémon she sights. */
	public static boolean SIGHTINGS = true;
	/** How far from the pasture she keeps lure spots and slathers logs. */
	public static final int RANGE = 32;
	/** How far below and above the pasture she looks for Saccharine logs. */
	public static final int LOG_HEIGHT = 16;
	/** How far from the pasture she sights wild Pokémon. */
	public static final int SIGHT_RANGE = 48;
	/** The most blocks the log scan looks at in one tick. */
	public static final int SCAN_PER_TICK = 4096;
	/** The most lure spots she keeps. */
	public static final int MAX_SPOTS = 3;
	/** Unmarked lure spots: on grass this far from the pasture. */
	public static final int GRASS_MIN = 16;
	public static final int GRASS_MAX = 32;
	/** How often she looks round for wild Pokémon (a minute). */
	public static final int SIGHT_EVERY = 1200;
	/** How many of her sightings she remembers (the hall shows them). */
	public static final int SIGHTINGS_KEPT = 5;
	/** How many sighted Pokémon she remembers, so each is told once. */
	public static final int SIGHTED_KEPT = 64;
	/** How close to a lure spot a Saccharine sapling (or the tree it grew into) counts as planted round it. */
	public static final int SAPLING_RADIUS = 4;
	/** The lure for Alphas (Cobblemon 1.8 and later): snacks seasoned with Hopo Berries. */
	public static final String ALPHA = "alpha";
	public static final ResourceLocation HOPO_BERRY = ResourceLocation.fromNamespaceAndPath("cobblemon", "hopo_berry");

	public static final ResourceLocation SACCHARINE_LOG = ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_log");
	public static final ResourceLocation SLATHERED_LOG = ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_log_slathered");
	public static final ResourceLocation SACCHARINE_SAPLING = ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_sapling");
	public static final ResourceLocation POKE_SNACK = ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_snack");

	/** One sighting: what she saw ("a shiny Eevee"), its kind (shiny, rare, alpha), where, and the day. */
	public record Sighting(Component what, String kind, BlockPos pos, long day) {
		public static final Codec<Sighting> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.fieldOf("what").forGetter(Sighting::what),
			Codec.STRING.fieldOf("kind").forGetter(Sighting::kind),
			BlockPos.CODEC.fieldOf("pos").forGetter(Sighting::pos),
			Codec.LONG.fieldOf("day").forGetter(Sighting::day)
		).apply(i, Sighting::new));
	}

	/** A wild Pokémon near the pasture, as the compat layer sees it. */
	public record Wild(UUID id, ResourceLocation species, Component name, boolean shiny, boolean alpha, BlockPos pos) {
	}

	/** Cobblemon's Poké Snacks and wild Pokémon, filled by the compat layer. */
	public interface Snacks {
		Extension<Snacks> EXTENSION = new Extension<>("Poké Snacks");

		/** Whether {@code stack} is a Poké Snack that can be set out. */
		boolean isSnack(ItemStack stack);

		/** Whether {@code state} is a Poké Snack set out (not yet eaten up). */
		boolean isSnackBlock(BlockState state);

		/** {@code placer} sets {@code stack} (one snack) out at {@code pos}, as a player places it, facing {@code facing}: true if it is there. */
		boolean setOut(ServerLevel level, BlockPos pos, ItemStack stack, Direction facing, net.minecraft.world.entity.LivingEntity placer);

		/** The seasonings (item ids) a snack was cooked with. */
		Set<ResourceLocation> seasonings(ItemStack stack);

		/** The wild Pokémon (no owner, not in battle) in {@code box}. */
		List<Wild> wild(ServerLevel level, AABB box);

		/** The phase a Habitat Block is in now (from 1), or 0 if unknown (ROADMAP 28.14). */
		default int habitatPhase(@Nullable net.minecraft.world.level.block.entity.BlockEntity habitat) {
			return 0;
		}
	}

	@Nullable
	public static Snacks snacks() {
		return Snacks.EXTENSION.call(s -> s, null);
	}

	public static void init() {
		Platform.get().onDataReload(HabitatData.ID, HabitatData.create());
		CampCooks.onSeasoningAsk(HabitatKeepers::askedSeasonings);
	}

	public static boolean isKeeper(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.HABITAT_KEEPER;
	}

	/** Whether the job can be had: Cobblemon is there with its Pasture Block, and the config switch is on. */
	public static boolean available() {
		return ENABLED && Platform.get().isModLoaded("cobblemon") && BuiltInRegistries.BLOCK.containsKey(ModVillagers.PASTURE_BLOCK);
	}

	/** A honey bottle: what picks the job at a Pasture Block. */
	public static boolean isHoney(ItemStack stack) {
		return available() && stack.is(Items.HONEY_BOTTLE);
	}

	private static boolean is(BlockState state, ResourceLocation id) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
	}

	/** A bare Saccharine log, which honey slathers. */
	public static boolean isBareLog(BlockState state) {
		return is(state, SACCHARINE_LOG);
	}

	/** A Saccharine log slathered with honey. */
	public static boolean isSlathered(BlockState state) {
		return is(state, SLATHERED_LOG);
	}

	/**
	 * The slathered log that honey makes of a bare one, with its honey on the side facing {@code toward} (as Cobblemon's
	 * own honey bottle does on the face a player clicks); null without Cobblemon's slathered log.
	 */
	@Nullable
	public static BlockState slathered(Direction toward) {
		Block block = BuiltInRegistries.BLOCK.getOptional(SLATHERED_LOG).orElse(null);
		if (block == null) {
			return null;
		}
		BlockState state = block.defaultBlockState();
		Property<?> facing = block.getStateDefinition().getProperty("facing");
		if (facing instanceof DirectionProperty direction && direction.getPossibleValues().contains(toward)) {
			state = state.setValue(direction, toward);
		}
		return state;
	}

	/** Slathers the bare log at {@code pos} with one honey bottle: true if it is now slathered. */
	public static boolean slather(Level level, BlockPos pos, Direction toward) {
		BlockState state = level.getBlockState(pos);
		BlockState honeyed = slathered(toward.getAxis().isHorizontal() ? toward : Direction.NORTH);
		if (!isBareLog(state) || honeyed == null) {
			return false;
		}
		return level.setBlock(pos, honeyed, Block.UPDATE_ALL);
	}

	/** The side of the log at {@code log} that faces {@code from} (the keeper), as a horizontal direction. */
	public static Direction faceToward(BlockPos log, BlockPos from) {
		int dx = from.getX() - log.getX();
		int dz = from.getZ() - log.getZ();
		if (dx == 0 && dz == 0) {
			return Direction.NORTH;
		}
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** How many blocks the log scan covers round one pasture. */
	public static int scanSize() {
		int side = RANGE * 2 + 1;
		return side * side * (LOG_HEIGHT * 2 + 1);
	}

	/** The block at step {@code index} of the log scan round {@code center}. */
	public static BlockPos scanPos(BlockPos center, int index) {
		int side = RANGE * 2 + 1;
		int x = index % side;
		int z = (index / side) % side;
		int y = index / (side * side);
		return center.offset(x - RANGE, y - LOG_HEIGHT, z - RANGE);
	}

	/**
	 * Looks at up to {@code budget} blocks of the scan round {@code center} from step {@code cursor}, adding each
	 * Saccharine log (bare or slathered) to {@code found} once, and returns the next step (back to 0 when the scan has
	 * covered everything). Unloaded chunks are passed over.
	 */
	public static int scan(Level level, BlockPos center, int cursor, int budget, List<BlockPos> found) {
		int size = scanSize();
		int index = Math.max(0, cursor) % size;
		for (int n = 0; n < Math.min(budget, SCAN_PER_TICK); n++) {
			BlockPos pos = scanPos(center, index);
			if (level.isLoaded(pos)) {
				BlockState state = level.getBlockState(pos);
				if ((isBareLog(state) || isSlathered(state)) && !found.contains(pos)) {
					found.add(pos.immutable());
				}
			}
			index++;
			if (index >= size) {
				return 0;
			}
		}
		return index;
	}

	// --- the lure ---------------------------------------------------------------------------------

	/** Her lure ("typing/fire", "egg_group/field", "alpha"), or null for any snack. */
	@Nullable
	public static String lure(Villager keeper) {
		String lure = ModAttachments.HABITAT_LURE.get(keeper);
		return lure == null || lure.isEmpty() ? null : lure;
	}

	public static void setLure(Villager keeper, @Nullable String lure) {
		if (lure == null) {
			ModAttachments.HABITAT_LURE.remove(keeper);
		} else {
			ModAttachments.HABITAT_LURE.set(keeper, lure);
		}
	}

	/** The lures she can be asked for: each type and egg group Cobblemon's bait data seasons for, and Alphas on 1.8. */
	public static List<String> lures() {
		List<String> out = new ArrayList<>(HabitatData.lureNames());
		if (PokemonFeatures.ALPHAS.available()) {
			out.add(ALPHA);
		}
		return out;
	}

	/** The berries (item ids) that season a snack for {@code lure}. */
	public static List<ResourceLocation> berries(@Nullable String lure) {
		if (lure == null) {
			return List.of();
		}
		if (lure.equals(ALPHA)) {
			return List.of(HOPO_BERRY);
		}
		return HabitatData.lures().getOrDefault(lure, List.of());
	}

	/** "Fire type", "Field egg group", "Alphas", "any Pokémon". */
	public static Component lureName(@Nullable String lure) {
		if (lure == null) {
			return Component.translatable("message.aliveworkplace.habitat_keeper.lure.any");
		}
		if (lure.equals(ALPHA)) {
			return Component.translatable("message.aliveworkplace.habitat_keeper.lure.alpha");
		}
		int slash = lure.indexOf('/');
		String kind = slash < 0 ? lure : lure.substring(0, slash);
		String what = slash < 0 ? lure : lure.substring(slash + 1);
		return Component.translatable("message.aliveworkplace.habitat_keeper.lure." + kind, Component.translatable("cobblemon." + kind.replace("typing", "type") + "." + what));
	}

	/** Whether {@code stack} is a snack seasoned for her lure (any snack, with no lure). */
	public static boolean seasonedFor(ItemStack stack, @Nullable String lure) {
		Snacks snacks = snacks();
		if (snacks == null || !snacks.isSnack(stack)) {
			return false;
		}
		if (lure == null) {
			return true;
		}
		Set<ResourceLocation> seasonings = snacks.seasonings(stack);
		return berries(lure).stream().anyMatch(seasonings::contains);
	}

	/**
	 * What the Camp Cooks are asked to season the keepers' snacks with: for a dish asked by Habitat Keepers, the berries
	 * of the lures of the keepers within {@link CampCooks#ASK_RANGE} of the pot.
	 */
	static Set<ResourceLocation> askedSeasonings(ServerLevel level, BlockPos pot, CampCooks.Dish dish) {
		if (!dish.askedBy().contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.HABITAT_KEEPER))) {
			return Set.of();
		}
		Set<ResourceLocation> out = new LinkedHashSet<>();
		for (Villager keeper : level.getEntitiesOfClass(Villager.class, new AABB(pot).inflate(CampCooks.ASK_RANGE), HabitatKeepers::isKeeper)) {
			out.addAll(berries(lure(keeper)));
		}
		return out;
	}

	/** Sneak-right-click with an empty hand: the lure picker (a type, an egg group, Alphas on 1.8, or any snack). */
	public static void openLures(ServerPlayer player, Villager keeper) {
		int[] page = {0};
		ChoiceMenu.open(player, Component.translatable("message.aliveworkplace.habitat_keeper.lures_title", keeper.getDisplayName()),
			p -> keeper.isAlive() && p.isAlive() && p.distanceTo(keeper) <= 8,
			menu -> fillLures(menu, keeper, page));
	}

	private static final int PER_PAGE = 45;

	/** Lays out the lure picker (also used by tests, with a detached menu). */
	public static void fillLures(ChoiceMenu menu, Villager keeper, int[] page) {
		menu.clearButtons();
		List<String> lures = lures();
		int pages = Math.max(1, (lures.size() + PER_PAGE - 1) / PER_PAGE);
		page[0] = Math.floorMod(page[0], pages);
		String current = lure(keeper);
		for (int i = 0; i < PER_PAGE; i++) {
			int n = page[0] * PER_PAGE + i;
			if (n >= lures.size()) {
				break;
			}
			String lure = lures.get(n);
			menu.button(i, lureIcon(lure, lure.equals(current)), p -> {
				setLure(keeper, lure);
				Chat.actionBar(p, Component.translatable("message.aliveworkplace.habitat_keeper.lure_set", keeper.getDisplayName(), lureName(lure)));
				fillLures(menu, keeper, page);
			});
		}
		menu.divider(5);
		if (pages > 1) {
			menu.button(45, named(Items.ARROW, Component.translatable("message.aliveworkplace.habitat_keeper.lures.previous")), p -> {
				page[0]--;
				fillLures(menu, keeper, page);
			});
			menu.button(53, named(Items.ARROW, Component.translatable("message.aliveworkplace.habitat_keeper.lures.next")), p -> {
				page[0]++;
				fillLures(menu, keeper, page);
			});
		}
		ItemStack any = named(current == null ? Items.HONEY_BOTTLE : Items.GLASS_BOTTLE,
			Component.translatable("message.aliveworkplace.habitat_keeper.lures.any"));
		any.set(DataComponents.LORE, new ItemLore(List.of(plain(Component.translatable("message.aliveworkplace.habitat_keeper.lures.now",
			lureName(current)).withStyle(ChatFormatting.GRAY)))));
		menu.button(49, any, p -> {
			setLure(keeper, null);
			Chat.actionBar(p, Component.translatable("message.aliveworkplace.habitat_keeper.lure_set", keeper.getDisplayName(), lureName(null)));
			fillLures(menu, keeper, page);
		});
	}

	private static ItemStack lureIcon(String lure, boolean chosen) {
		List<ResourceLocation> berries = berries(lure);
		Item icon = berries.isEmpty() ? Items.SWEET_BERRIES : BuiltInRegistries.ITEM.getOptional(berries.get(0)).orElse(Items.SWEET_BERRIES);
		ItemStack stack = named(icon, lureName(lure).copy().withStyle(chosen ? ChatFormatting.GREEN : ChatFormatting.WHITE));
		List<Component> lore = new ArrayList<>();
		net.minecraft.network.chat.MutableComponent list = Component.empty();
		for (int i = 0; i < berries.size(); i++) {
			Item berry = BuiltInRegistries.ITEM.getOptional(berries.get(i)).orElse(Items.AIR);
			list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(new ItemStack(berry).getHoverName());
		}
		lore.add(plain(Component.translatable("message.aliveworkplace.habitat_keeper.lures.seasoned", list).withStyle(ChatFormatting.GRAY)));
		if (chosen) {
			lore.add(plain(Component.translatable("message.aliveworkplace.habitat_keeper.lures.chosen").withStyle(ChatFormatting.GREEN)));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		if (chosen) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	private static ItemStack named(Item item, Component name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name));
		return stack;
	}

	private static Component plain(Component c) {
		return c.copy().withStyle(s -> s.withItalic(false));
	}

	// --- lure spots --------------------------------------------------------------------------------

	/**
	 * A Field Marker handed to her: the marked area's middle becomes a lure spot (up to {@link #MAX_SPOTS}, the oldest
	 * given up for a fourth). The marker stays with the player.
	 */
	public static InteractionResult markSpot(ServerPlayer player, Villager keeper, ItemStack marker) {
		ServerLevel level = Players.level(player);
		if (!Friends.mayCommand(player, keeper)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.habitat_keeper.not_yours", keeper.getDisplayName()).withStyle(ChatFormatting.RED));
			return InteractionResult.CONSUME;
		}
		FieldData data = FieldMarkerItem.data(marker);
		Optional<BoundingBox> area = data.area();
		if (area.isEmpty() || !data.dimension().map(Ids.of(level.dimension())::equals).orElse(false)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.field.not_marked").withStyle(ChatFormatting.YELLOW));
			return InteractionResult.CONSUME;
		}
		BlockPos station = Builders.benchPos(keeper).orElse(null);
		if (station == null) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.habitat_keeper.no_pasture").withStyle(ChatFormatting.RED));
			return InteractionResult.CONSUME;
		}
		BoundingBox box = area.get();
		BlockPos spot = new BlockPos(box.getCenter().getX(), box.maxY() + 1, box.getCenter().getZ());
		int distance = (int) Math.sqrt(spot.distSqr(station));
		if (distance > RANGE) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.habitat_keeper.spot_too_far", distance, RANGE).withStyle(ChatFormatting.RED));
			return InteractionResult.CONSUME;
		}
		addSpot(keeper, spot);
		Chat.chat(player, Component.translatable("message.aliveworkplace.habitat_keeper.spot_marked", keeper.getDisplayName(),
			spot.getX(), spot.getY(), spot.getZ(), spots(keeper).size(), MAX_SPOTS));
		return InteractionResult.SUCCESS;
	}

	/** Adds a marked lure spot (tests too): the oldest goes when there are more than {@link #MAX_SPOTS}. */
	public static void addSpot(Villager keeper, BlockPos spot) {
		List<BlockPos> spots = new ArrayList<>(spots(keeper));
		spots.remove(spot);
		spots.add(spot.immutable());
		while (spots.size() > MAX_SPOTS) {
			spots.remove(0);
		}
		ModAttachments.LURE_SPOTS.set(keeper, List.copyOf(spots));
	}

	/** Her marked lure spots. */
	public static List<BlockPos> spots(Villager keeper) {
		return ModAttachments.LURE_SPOTS.getOrElse(keeper, List.of());
	}

	/** Where she sets snacks out: her marked spots within range, or with none marked, spots on grass 16 to 32 blocks out. */
	public static List<BlockPos> lureSpots(ServerLevel level, Villager keeper, BlockPos pasture) {
		List<BlockPos> marked = spots(keeper).stream().filter(p -> p.distSqr(pasture) <= (double) RANGE * RANGE).toList();
		return marked.isEmpty() ? grassSpots(level, pasture) : marked;
	}

	/** Up to three spots on grass, a third of a turn apart round the pasture, the nearest to it from 16 to 32 blocks out. */
	public static List<BlockPos> grassSpots(Level level, BlockPos pasture) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < MAX_SPOTS; i++) {
			double angle = Math.PI * 2 * i / MAX_SPOTS + 0.4;
			for (int r = GRASS_MIN; r <= GRASS_MAX; r++) {
				int x = pasture.getX() + (int) Math.round(Math.cos(angle) * r);
				int z = pasture.getZ() + (int) Math.round(Math.sin(angle) * r);
				if (!level.isLoaded(new BlockPos(x, pasture.getY(), z))) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos spot = new BlockPos(x, y, z);
				Snacks snacks = snacks();
				boolean snackThere = snacks != null && snacks.isSnackBlock(level.getBlockState(spot));
				if (level.getBlockState(spot.below()).is(Blocks.GRASS_BLOCK) && (level.getBlockState(spot).isAir() || snackThere)
					&& Math.abs(y - pasture.getY()) <= LOG_HEIGHT) {
					out.add(spot);
					break;
				}
			}
		}
		return out;
	}

	/** Whether a snack can be set out at {@code spot}: free air over a sturdy top. */
	public static boolean free(Level level, BlockPos spot) {
		return level.getBlockState(spot).canBeReplaced() && level.getBlockState(spot).getFluidState().isEmpty()
			&& level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP);
	}

	/** The chests her snacks come from: those by her pasture, then those of the Camp Cooks within their asking range. */
	public static List<BlockPos> snackChests(ServerLevel level, BlockPos pasture) {
		List<BlockPos> out = new ArrayList<>(SupplyContainers.find(level, pasture, null));
		level.getPoiManager().findAll(h -> h.is(ModVillagers.CAMPFIRE_POT_POI), p -> true, pasture, CampCooks.ASK_RANGE, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(Comparator.comparingDouble(p -> p.distSqr(pasture)))
			.forEach(pot -> SupplyContainers.find(level, pot, null).stream().filter(p -> !p.equals(pot) && !out.contains(p)).forEach(out::add));
		return out;
	}

	/** Where to plant a Saccharine sapling round {@code spot}, or null if one (or its tree) is already there or there's no room. */
	@Nullable
	public static BlockPos saplingSpot(Level level, BlockPos spot) {
		for (BlockPos p : BlockPos.betweenClosed(spot.offset(-SAPLING_RADIUS, -2, -SAPLING_RADIUS), spot.offset(SAPLING_RADIUS, 6, SAPLING_RADIUS))) {
			BlockState state = level.getBlockState(p);
			if (is(state, SACCHARINE_SAPLING) || isBareLog(state) || isSlathered(state)) {
				return null;
			}
		}
		int[][] offsets = {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
		for (int[] o : offsets) {
			for (int dy = 1; dy >= -1; dy--) {
				BlockPos p = spot.offset(o[0], dy, o[1]);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).is(BlockTags.DIRT) && level.getBlockState(p.above()).isAir()) {
					return p;
				}
			}
		}
		return null;
	}

	/** Plants a Saccharine sapling at {@code pos}: true if it took. */
	public static boolean plantSapling(Level level, BlockPos pos) {
		Block sapling = BuiltInRegistries.BLOCK.getOptional(SACCHARINE_SAPLING).orElse(null);
		if (sapling == null || !sapling.defaultBlockState().canSurvive(level, pos) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		return level.setBlock(pos, sapling.defaultBlockState(), Block.UPDATE_ALL);
	}

	public static boolean isSapling(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(SACCHARINE_SAPLING);
	}

	// --- sightings ---------------------------------------------------------------------------------

	/** Her last sightings, newest first. */
	public static List<Sighting> sightings(Villager keeper) {
		return ModAttachments.SIGHTINGS.getOrElse(keeper, List.of());
	}

	/** What kind of sighting {@code wild} is worth telling (shiny, alpha, rare), or null. */
	@Nullable
	public static String kindOf(Wild wild) {
		if (wild.shiny()) {
			return "shiny";
		}
		if (wild.alpha()) {
			return "alpha";
		}
		return HabitatData.rareOnly(wild.species()) ? "rare" : null;
	}

	/**
	 * She looks round her pasture (an entity query, {@link #SIGHT_RANGE} blocks): each shiny, rare or Alpha wild Pokémon
	 * she hasn't told of yet is told to the players in the village, written in the chronicle and kept in her last five.
	 * Returns the new sightings.
	 */
	public static List<Sighting> sight(ServerLevel level, Villager keeper, BlockPos pasture) {
		Snacks snacks = snacks();
		if (!SIGHTINGS || !ENABLED || snacks == null) {
			return List.of();
		}
		List<Sighting> found = new ArrayList<>();
		List<UUID> told = new ArrayList<>(ModAttachments.SIGHTED.getOrElse(keeper, List.of()));
		List<Sighting> kept = new ArrayList<>(sightings(keeper));
		for (Wild wild : snacks.wild(level, new AABB(pasture).inflate(SIGHT_RANGE))) {
			String kind = kindOf(wild);
			if (kind == null || told.contains(wild.id()) || wild.pos().distSqr(pasture) > (double) SIGHT_RANGE * SIGHT_RANGE) {
				continue;
			}
			told.add(wild.id());
			Component what = Component.translatable("message.aliveworkplace.habitat_keeper.sighting." + kind, wild.name());
			Sighting sighting = new Sighting(what, kind, wild.pos().immutable(), Chronicle.day(level));
			found.add(sighting);
			kept.add(0, sighting);
			Component text = Component.translatable("message.aliveworkplace.habitat_keeper.sighted", keeper.getDisplayName(), what, where(pasture, wild.pos()));
			BlockPos center = VillageHalls.nearest(level, pasture).orElse(pasture);
			for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(center.getCenter()) < (double) VillageHalls.RADIUS * VillageHalls.RADIUS
				|| p.distanceToSqr(pasture.getCenter()) < (double) SIGHT_RANGE * SIGHT_RANGE)) {
				Chat.chat(player, text.copy().withStyle(ChatFormatting.AQUA));
			}
			Chronicle.record(level, pasture, Chronicle.Kind.SIGHTING, text);
			PartnerShows.cue(keeper, "sighting", wild.pos());
		}
		while (told.size() > SIGHTED_KEPT) {
			told.remove(0);
		}
		while (kept.size() > SIGHTINGS_KEPT) {
			kept.remove(kept.size() - 1);
		}
		if (!found.isEmpty()) {
			ModAttachments.SIGHTED.set(keeper, List.copyOf(told));
			ModAttachments.SIGHTINGS.set(keeper, List.copyOf(kept));
		}
		return found;
	}

	/** "by her pasture", "north-east of her pasture". */
	static Component where(BlockPos pasture, BlockPos pos) {
		double dx = pos.getX() - pasture.getX();
		double dz = pos.getZ() - pasture.getZ();
		if (dx * dx + dz * dz < 16) {
			return Component.translatable("message.aliveworkplace.habitat_keeper.by_pasture");
		}
		int eighth = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
		String[] directions = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		return Component.translatable("message.aliveworkplace.habitat_keeper.of_pasture",
			Component.translatable("screen.aliveworkplace.hall.dir." + directions[eighth]));
	}

	/** Her last sightings for the hall's list: "Sighted on day 12: a shiny Eevee". */
	public static List<Component> sightingLines(Villager keeper) {
		List<Component> out = new ArrayList<>();
		for (Sighting s : sightings(keeper)) {
			out.add(Component.translatable("screen.aliveworkplace.hall.sighting", s.day(), s.what()));
		}
		return out;
	}

	private HabitatKeepers() {
	}
}
