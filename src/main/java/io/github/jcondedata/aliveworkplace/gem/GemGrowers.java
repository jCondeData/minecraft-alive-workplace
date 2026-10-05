package io.github.jcondedata.aliveworkplace.gem;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * The Gem Grower (ROADMAP 28.11): a villager at a stonecutter, picked with an amethyst shard, who tends the gem beds
 * round it ({@link GemBeds}): she picks the ripe clusters (by their own loot, with a pickaxe), never breaking what they
 * grow on, and plants what a bed is planted with (a tumblestone against lava or magma, a Type Gem Block against a
 * Deepslate Crystal Core). With Cobblemon 1.8 and glass in her chests she also makes Blank TMs from shards, by
 * Cobblemon's own recipe. Works without Cobblemon (amethyst); config {@code gemGrowers}.
 */
public final class GemGrowers {
	/** Config switch {@code gemGrowers}: off, an amethyst shard picks no job at a stonecutter and growers already hired stand idle. */
	public static boolean ENABLED = true;
	/** How far from the stonecutter beds are found. */
	public static final int RANGE = 16;
	/** The most blocks the bed scan looks at in one tick. */
	public static final int SCAN_PER_TICK = 4096;
	/** The most beds she remembers. */
	public static final int MAX_BEDS = 64;
	/** How far round a bed's touch block she looks for ripe clusters (a Type Gem's grow on the Gem Block beside the core). */
	public static final int CLUSTER_REACH = 2;
	/** The most Blank TMs she keeps in her chests. */
	public static final int BLANK_TMS_KEPT = 8;
	public static final ResourceLocation BLANK_TM = ResourceLocation.fromNamespaceAndPath("cobblemon", "blank_tm");
	/** Cobblemon's recipe for Blank TMs (two shards and two glass make two). */
	public static final ResourceLocation BLANK_TM_RECIPE = ResourceLocation.fromNamespaceAndPath("cobblemon", "blank_tm");

	public static void init() {
		Platform.get().onDataReload(GemBeds.ID, GemBeds.create());
	}

	public static boolean isGrower(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.GEM_GROWER;
	}

	/** An amethyst shard: what picks the job at a stonecutter (with config {@code gemGrowers} on). */
	public static boolean isShard(ItemStack stack) {
		return ENABLED && stack.is(Items.AMETHYST_SHARD);
	}

	// --- orders -------------------------------------------------------------------------------------

	/** The beds she has been asked to keep; empty means every bed she has the makings for. */
	public static List<ResourceLocation> orders(Villager villager) {
		return ModAttachments.GEM_ORDERS.getOrElse(villager, List.of());
	}

	public static boolean wants(Villager villager, GemBeds.Bed bed) {
		List<ResourceLocation> orders = orders(villager);
		return orders.isEmpty() || orders.contains(bed.name());
	}

	/** Switches keeping {@code bed} on or off. */
	public static void toggle(Villager villager, ResourceLocation bed) {
		List<ResourceLocation> orders = new ArrayList<>(orders(villager));
		if (!orders.remove(bed)) {
			orders.add(bed);
		}
		if (orders.isEmpty()) {
			ModAttachments.GEM_ORDERS.remove(villager);
		} else {
			ModAttachments.GEM_ORDERS.set(villager, List.copyOf(orders));
		}
	}

	/** Sneak-right-click with an empty hand: which beds she keeps (owner, friends and ops; the first to give orders hires her). */
	public static void openOrders(ServerPlayer player, Villager villager) {
		if (!Friends.mayCommand(player, villager)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.gem_grower.not_yours", villager.getDisplayName())
				.withStyle(ChatFormatting.RED));
			return;
		}
		Friends.hire(player, villager);
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.gem_grower.orders", villager.getDisplayName()),
			p -> villager.isAlive() && p.isAlive() && p.distanceTo(villager) <= 8,
			menu -> render(menu, villager));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu ordersMenuForTest(ServerPlayer player, Villager villager) {
		return ChoiceMenu.detached(player, menu -> render(menu, villager));
	}

	/** First slot of the bed buttons (the top row explains). */
	public static final int FIRST_BED_SLOT = 9;

	private static void render(ChoiceMenu menu, Villager villager) {
		menu.clearButtons();
		ItemStack info = new ItemStack(Items.BOOK);
		info.set(DataComponents.CUSTOM_NAME, Component.translatable("screen.aliveworkplace.gem_grower.info")
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
		info.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable(orders(villager).isEmpty() ? "screen.aliveworkplace.gem_grower.every" : "screen.aliveworkplace.gem_grower.only")
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
		menu.button(4, info, null);
		int slot = FIRST_BED_SLOT;
		for (GemBeds.Bed bed : GemBeds.beds()) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			boolean on = orders(villager).contains(bed.name());
			ItemStack icon = new ItemStack(icon(bed));
			icon.set(DataComponents.CUSTOM_NAME, Component.translatable(bed.nameKey())
				.withStyle(style -> style.withItalic(false).withColor(on ? ChatFormatting.GREEN : ChatFormatting.WHITE)));
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on);
			List<Component> lore = new ArrayList<>();
			lore.add(Component.translatable(on ? "screen.aliveworkplace.gem_grower.on" : "screen.aliveworkplace.gem_grower.off")
				.withStyle(style -> style.withItalic(false).withColor(on ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
			bed.plantItem().ifPresent(item -> lore.add(Component.translatable("screen.aliveworkplace.gem_grower.planted_with", new ItemStack(item).getHoverName())
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))));
			icon.set(DataComponents.LORE, new ItemLore(lore));
			ResourceLocation name = bed.name();
			menu.button(slot++, icon, p -> {
				toggle(villager, name);
				render(menu, villager);
			});
		}
	}

	/** A bed's icon: its ripe cluster's item (or what it's planted with). */
	static Item icon(GemBeds.Bed bed) {
		Item ripe = BuiltInRegistries.BLOCK.get(bed.ripe()).asItem();
		if (ripe != Items.AIR) {
			return ripe;
		}
		return bed.plantItem().orElse(Items.AMETHYST_SHARD);
	}

	// --- the beds -----------------------------------------------------------------------------------

	/** The beds {@code state} is the touch block of, among those she keeps (her orders; every one for a null villager). */
	public static List<GemBeds.Bed> bedsAt(BlockState state, @Nullable Villager villager) {
		List<GemBeds.Bed> out = new ArrayList<>();
		for (GemBeds.Bed bed : GemBeds.beds()) {
			if (bed.touch().matches(state) && (villager == null || wants(villager, bed))) {
				out.add(bed);
			}
		}
		return out;
	}

	/** Whether {@code state} is any bed's touch block. */
	public static boolean isTouch(BlockState state) {
		for (GemBeds.Bed bed : GemBeds.beds()) {
			if (bed.touch().matches(state)) {
				return true;
			}
		}
		return false;
	}

	/** How many blocks the bed scan covers round one stonecutter. */
	public static int scanSize() {
		int side = RANGE * 2 + 1;
		return side * side * side;
	}

	/** The block at step {@code index} of the scan round {@code center}. */
	public static BlockPos scanPos(BlockPos center, int index) {
		int side = RANGE * 2 + 1;
		int x = index % side;
		int z = (index / side) % side;
		int y = index / (side * side);
		return center.offset(x - RANGE, y - RANGE, z - RANGE);
	}

	/**
	 * Looks at up to {@code budget} blocks of the scan round {@code center} from step {@code cursor}, adding each bed's
	 * touch block to {@code found} once (up to {@link #MAX_BEDS}), and returns the next step (0 once it has covered
	 * everything). Unloaded chunks are passed over.
	 */
	public static int scan(Level level, BlockPos center, int cursor, int budget, List<BlockPos> found) {
		int size = scanSize();
		int index = Math.max(0, cursor) % size;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int n = 0; n < Math.min(budget, SCAN_PER_TICK); n++) {
			pos.set(scanPos(center, index));
			if (found.size() < MAX_BEDS && level.isLoaded(pos) && isTouch(level.getBlockState(pos)) && !found.contains(pos)) {
				found.add(pos.immutable());
			}
			index++;
			if (index >= size) {
				return 0;
			}
		}
		return index;
	}

	/** Her remembered beds (their touch blocks). */
	public static List<BlockPos> beds(Villager villager) {
		return ModAttachments.GEM_BEDS.getOrElse(villager, List.of());
	}

	/** A ripe cluster of {@code bed} round the touch block at {@code touch}, nearest first, or null. */
	@Nullable
	public static BlockPos ripe(Level level, BlockPos touch, GemBeds.Bed bed) {
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(touch.offset(-CLUSTER_REACH, -CLUSTER_REACH, -CLUSTER_REACH),
				touch.offset(CLUSTER_REACH, CLUSTER_REACH, CLUSTER_REACH))) {
			if (!p.equals(touch) && bed.isRipe(level.getBlockState(p)) && (best == null || p.distSqr(touch) < best.distSqr(touch))) {
				best = p.immutable();
			}
		}
		return best;
	}

	/**
	 * Picks the ripe cluster at {@code pos} as a player with a pickaxe would (its own loot) and returns what it dropped; the
	 * block it grew on stays. Empty if it isn't ripe.
	 */
	public static List<ItemStack> pick(ServerLevel level, BlockPos pos, GemBeds.Bed bed, @Nullable Villager picker) {
		BlockState state = level.getBlockState(pos);
		if (!bed.isRipe(state)) {
			return List.of();
		}
		List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, level.getBlockEntity(pos), picker, new ItemStack(Items.IRON_PICKAXE)));
		level.destroyBlock(pos, false, picker);
		return drops;
	}

	/** How many plantings of any of {@code beds} (those sharing one touch block) stand against the touch block at {@code touch}. */
	public static int planted(Level level, BlockPos touch, List<GemBeds.Bed> beds) {
		int n = 0;
		for (Direction d : Direction.values()) {
			BlockState state = level.getBlockState(touch.relative(d));
			if (beds.stream().anyMatch(b -> b.isGrowth(state))) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Whether the touch block at {@code touch} wants one more planting of {@code bed} ({@code beds} are all the beds she
	 * keeps that share it): it's planted with something, and has room for it.
	 */
	public static boolean wantsPlanting(Level level, BlockPos touch, GemBeds.Bed bed, List<GemBeds.Bed> beds) {
		return bed.plantBlock().isPresent() && planted(level, touch, beds) < bed.plants() && plantSpot(level, touch, bed) != null;
	}

	/** Where the next planting goes against {@code touch} and the state it is set as, or null if there's no room. */
	@Nullable
	public static Planting plantSpot(Level level, BlockPos touch, GemBeds.Bed bed) {
		Block block = bed.plantBlock().orElse(null);
		if (block == null) {
			return null;
		}
		Direction[] order = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
		for (Direction d : order) {
			BlockPos p = touch.relative(d);
			BlockState here = level.getBlockState(p);
			if (!here.isAir() || !here.getFluidState().isEmpty()) {
				continue;
			}
			BlockState state = placed(level, p, block, touch);
			if (state != null) {
				return new Planting(p, state);
			}
		}
		return null;
	}

	/** One planting: where, and the block state it is set as. */
	public record Planting(BlockPos pos, BlockState state) {
	}

	/**
	 * {@code block} as it is set at {@code pos}: a directional one (a tumblestone bud) sits on a sturdy face, the touch block
	 * first, facing away from it (as Cobblemon's own item places it); null if there's none to sit on.
	 */
	@Nullable
	static BlockState placed(Level level, BlockPos pos, Block block, BlockPos touch) {
		BlockState state = block.defaultBlockState();
		Property<?> facing = block.getStateDefinition().getProperty("facing");
		if (!(facing instanceof DirectionProperty direction)) {
			return state;
		}
		List<Direction> supports = new ArrayList<>(List.of(Direction.values()));
		supports.sort(Comparator.comparingInt(d -> pos.relative(d).equals(touch) ? 0 : d == Direction.DOWN ? 1 : 2));
		for (Direction d : supports) {
			BlockPos support = pos.relative(d);
			Direction away = d.getOpposite();
			if (level.getBlockState(support).isFaceSturdy(level, support, away) && direction.getPossibleValues().contains(away)) {
				return state.setValue(direction, away);
			}
		}
		return null;
	}

	/** Plants {@code bed}'s block against {@code touch}: true if it took. */
	public static boolean plant(Level level, BlockPos touch, GemBeds.Bed bed) {
		Planting planting = plantSpot(level, touch, bed);
		if (planting == null) {
			return false;
		}
		boolean set = level.setBlock(planting.pos(), planting.state(), Block.UPDATE_ALL);
		if (set) {
			level.playSound(null, planting.pos(), planting.state().getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.9f);
		}
		return set;
	}

	// --- Blank TMs ----------------------------------------------------------------------------------

	/** Cobblemon's Blank TM recipe, with Cobblemon 1.8 (TMs came in with the Type Gems), or empty. */
	public static Optional<Recipe<?>> blankTmRecipe(ServerLevel level) {
		if (!PokemonFeatures.TYPE_GEMS.available()) {
			return Optional.empty();
		}
		for (RecipeHolder<?> holder : Recipes.all(Recipes.manager(level))) {
			if (Recipes.id(holder).equals(BLANK_TM_RECIPE)) {
				return Optional.of(holder.value());
			}
		}
		return Optional.empty();
	}

	/**
	 * Makes Blank TMs at the stonecutter from the shards and glass in {@code chests} (Cobblemon's recipe), while there are
	 * fewer than {@link #BLANK_TMS_KEPT}: true if she made some.
	 */
	public static boolean makeBlankTms(ServerLevel level, List<BlockPos> chests) {
		Recipe<?> recipe = blankTmRecipe(level).orElse(null);
		if (recipe == null) {
			return false;
		}
		ItemStack result = Recipes.result(recipe, level.registryAccess());
		if (result.isEmpty() || SupplyContainers.count(level, chests, result.getItem()) >= BLANK_TMS_KEPT) {
			return false;
		}
		// What the recipe needs, one item kind per ingredient (the first in the chests that fits it).
		List<Item> needs = new ArrayList<>();
		for (Ingredient ingredient : Recipes.ingredients(recipe)) {
			if (ingredient.isEmpty()) {
				continue;
			}
			Item found = null;
			for (ItemStack option : Recipes.options(ingredient)) {
				long have = SupplyContainers.count(level, chests, option.getItem());
				long already = needs.stream().filter(i -> i == option.getItem()).count();
				if (have > already) {
					found = option.getItem();
					break;
				}
			}
			if (found == null) {
				return false;
			}
			needs.add(found);
		}
		for (Item item : needs) {
			if (SupplyContainers.extract(level, chests, item, 1) < 1) {
				return false;
			}
		}
		ItemStack rest = SupplyContainers.insert(level, chests, result.copy());
		if (!rest.isEmpty()) {
			Block.popResource(level, chests.get(0).above(), rest);
		}
		return true;
	}

	/** How many of what she plants or makes Blank TMs with her chests keep; the rest of her picking goes to the store. */
	public static final int KEEP = 16;

	/** How many of {@code stack}'s kind her chests keep (the porter takes the rest): her makings, up to {@link #KEEP}. */
	public static int keeps(ItemStack stack) {
		if (stack.is(Items.GLASS) || stack.is(Items.AMETHYST_SHARD)) {
			return KEEP;
		}
		for (GemBeds.Bed bed : GemBeds.beds()) {
			if (bed.plantItem().map(stack::is).orElse(false)) {
				return KEEP;
			}
		}
		return 0;
	}

	/** Whether the stonecutter she works at is still there. */
	public static boolean isStonecutter(BlockState state) {
		return state.is(Blocks.STONECUTTER);
	}

	private GemGrowers() {
	}
}
