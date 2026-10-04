package io.github.jcondedata.aliveworkplace.berry;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The Berry Breeder (ROADMAP 28.9): a villager at a composter, picked with any Cobblemon berry, who breeds new berries
 * by planting the two parents of a mutation side by side. Her plot is the farmland within {@link #PLOT} blocks of the
 * composter, or the area of a Field Marker she's given. The berry book (sneak-right-click her) lists every berry
 * Cobblemon knows: the ones the village has found lit, the rest with the pair that makes them; clicking one makes it her
 * goal. The village's found berries are kept in its Village Hall. Needs Cobblemon; config {@code berryBreeders}.
 */
public final class BerryBreeders {
	/** Config switch {@code berryBreeders}: off, a berry picks no job and breeders already hired stand idle. */
	public static boolean ENABLED = true;
	/** How far round the composter her plot reaches when she has no Field Marker. */
	public static final int PLOT = 8;
	/** Berries of each kind left in her chests for the Camp Cook and the Habitat Keeper (porters take the rest). */
	public static final int KEEP_EACH = 16;
	/** How close a player stands to read the book. */
	static final double REACH = 8.0;
	private static final int PER_PAGE = 45;

	public static boolean isBreeder(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.BERRY_BREEDER;
	}

	/** Cobblemon's berry data, or null without Cobblemon. */
	@Nullable
	public static BerryChains.BerryData data() {
		return BerryChains.BerryData.EXTENSION.call(d -> d, null);
	}

	/** Whether the job can be had: Cobblemon is there and the config switch is on. */
	public static boolean available() {
		return ENABLED && Platform.get().isModLoaded("cobblemon") && data() != null;
	}

	/** Any Cobblemon berry (what picks the job at a composter). */
	public static boolean isBerry(ItemStack stack) {
		BerryChains.BerryData data = available() ? data() : null;
		return data != null && !stack.isEmpty() && data.berryOf(stack) != null;
	}

	/** Growth or Surprise Mulch. */
	public static boolean isMulch(ItemStack stack) {
		BerryChains.BerryData data = data();
		return data != null && !stack.isEmpty() && data.isMulch(stack);
	}

	// --- the plot -------------------------------------------------------------------------------

	/** Her plot: the Field Marker's area, or the ground round the composter. */
	@Nullable
	public static BoundingBox plot(Villager villager) {
		FieldJob job = ModAttachments.BERRY_PLOT.get(villager);
		if (job != null) {
			return job.box();
		}
		return Builders.benchPos(villager).map(c -> new BoundingBox(c.getX() - PLOT, c.getY() - 2, c.getZ() - PLOT,
			c.getX() + PLOT, c.getY() + 1, c.getZ() + PLOT)).orElse(null);
	}

	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack) {
		return AreaJobs.assign(player, villager, stack, ModAttachments.BERRY_PLOT, "berry_plot");
	}

	/** Gives her the plot. Also used by tests. */
	public static void start(Villager villager, BoundingBox box) {
		ModAttachments.BERRY_PLOT.set(villager, new FieldJob(box));
	}

	public static void release(ServerLevel level, Villager villager, @Nullable Player player) {
		AreaJobs.release(level, villager, player, ModAttachments.BERRY_PLOT);
	}

	public static void onDeath(ServerLevel level, Villager villager) {
		AreaJobs.onDeath(level, villager, ModAttachments.BERRY_PLOT);
	}

	// --- what the village has ------------------------------------------------------------------

	/** The berries the village has now: in her chests and bag, and growing in her plot. */
	public static Set<ResourceLocation> have(ServerLevel level, Villager villager) {
		Set<ResourceLocation> out = new LinkedHashSet<>();
		BerryChains.BerryData data = data();
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (data == null || station == null) {
			return out;
		}
		for (Item item : SupplyContainers.contents(level, SupplyContainers.find(level, station, null)).keySet()) {
			ResourceLocation id = data.berryOf(new ItemStack(item));
			if (id != null) {
				out.add(id);
			}
		}
		for (ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks()) {
			ResourceLocation id = data.berryOf(stack);
			if (id != null) {
				out.add(id);
			}
		}
		BoundingBox box = plot(villager);
		if (box != null) {
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + 1, box.maxZ())) {
				ResourceLocation id = data.plantOf(level.getBlockState(p));
				if (id != null) {
					out.add(id);
				}
			}
		}
		return out;
	}

	/** The village's hall, if there is one near her composter. */
	@Nullable
	static VillageHallBlockEntity hall(ServerLevel level, Villager villager) {
		BlockPos near = Builders.benchPos(villager).orElse(villager.blockPosition());
		Optional<BlockPos> hall = VillageHalls.nearest(level, near);
		return hall.isPresent() && level.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity ? entity : null;
	}

	/** The berries the village has found: those noted in its hall, and those it has now. */
	public static Set<ResourceLocation> found(ServerLevel level, Villager villager) {
		Set<ResourceLocation> out = new LinkedHashSet<>();
		VillageHallBlockEntity hall = hall(level, villager);
		if (hall != null) {
			out.addAll(hall.berriesFound());
		}
		out.addAll(have(level, villager));
		return out;
	}

	/** Notes {@code berry} in the village's hall; whether the village hadn't found it before. */
	static boolean note(ServerLevel level, Villager villager, ResourceLocation berry) {
		VillageHallBlockEntity hall = hall(level, villager);
		return hall != null && hall.findBerry(berry);
	}

	@Nullable
	public static ResourceLocation goal(Villager villager) {
		return ModAttachments.BERRY_GOAL.get(villager);
	}

	/** Makes {@code berry} her goal (null: none). */
	public static void setGoal(Villager villager, @Nullable ResourceLocation berry) {
		if (berry == null) {
			ModAttachments.BERRY_GOAL.remove(villager);
		} else {
			ModAttachments.BERRY_GOAL.set(villager, berry);
		}
	}

	/** The next pair to plant towards her goal, from what the village has; empty when there's no goal or no way there. */
	public static Optional<BerryChains.Mutation> step(ServerLevel level, Villager villager) {
		BerryChains.BerryData data = data();
		ResourceLocation goal = goal(villager);
		if (data == null || goal == null) {
			return Optional.empty();
		}
		return BerryChains.next(have(level, villager), data.mutations(), goal);
	}

	/** A berry's name ("Oran Berry"), or its id when it isn't loaded. */
	public static Component name(ResourceLocation berry) {
		BerryChains.BerryData data = data();
		ItemStack item = data == null ? ItemStack.EMPTY : data.item(berry);
		return item.isEmpty() ? Component.literal(berry.toString()) : item.getHoverName();
	}

	// --- the berry book ------------------------------------------------------------------------

	/** Sneak-right-click with an empty hand: the berry book. */
	public static void open(ServerPlayer player, Villager breeder) {
		if (data() == null) {
			return;
		}
		int[] page = {0};
		ChoiceMenu.open(player, Component.translatable("message.aliveworkplace.berry_breeder.book_title", breeder.getDisplayName()),
			p -> breeder.isAlive() && !breeder.isSleeping() && p.isAlive() && p.distanceTo(breeder) <= REACH,
			menu -> fill(menu, (ServerLevel) breeder.level(), breeder, page));
	}

	/** Lays out page {@code page[0]} of the book in {@code menu} (also used by tests, with a detached menu). */
	public static void fill(ChoiceMenu menu, ServerLevel level, Villager breeder, int[] page) {
		BerryChains.BerryData data = data();
		menu.clearButtons();
		if (data == null) {
			return;
		}
		List<ResourceLocation> berries = new ArrayList<>(data.berries());
		berries.sort(Comparator.comparing(ResourceLocation::toString));
		List<BerryChains.Mutation> mutations = data.mutations();
		Set<ResourceLocation> found = found(level, breeder);
		ResourceLocation goal = goal(breeder);
		int pages = Math.max(1, (berries.size() + PER_PAGE - 1) / PER_PAGE);
		page[0] = Math.floorMod(page[0], pages);
		for (int i = 0; i < PER_PAGE; i++) {
			int n = page[0] * PER_PAGE + i;
			if (n >= berries.size()) {
				break;
			}
			ResourceLocation berry = berries.get(n);
			menu.button(i, icon(data, berry, mutations, found, berry.equals(goal)), player -> {
				choose(player, breeder, berry);
				fill(menu, level, breeder, page);
			});
		}
		menu.divider(5);
		if (pages > 1) {
			menu.button(45, named(Items.ARROW, Component.translatable("message.aliveworkplace.berry_breeder.book.previous")), p -> {
				page[0]--;
				fill(menu, level, breeder, page);
			});
			menu.button(53, named(Items.ARROW, Component.translatable("message.aliveworkplace.berry_breeder.book.next")), p -> {
				page[0]++;
				fill(menu, level, breeder, page);
			});
		}
		// Her goal and the step she's on.
		ItemStack info = named(Items.BOOK, Component.translatable("message.aliveworkplace.berry_breeder.book.page", page[0] + 1, pages));
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("message.aliveworkplace.berry_breeder.book.found", found.stream().filter(berries::contains).count(),
			berries.size()).withStyle(ChatFormatting.GRAY));
		lines.add(goalLine(level, breeder));
		info.set(DataComponents.LORE, new ItemLore(lines.stream().map(BerryBreeders::plain).toList()));
		menu.button(49, info, null);
		if (goal != null) {
			menu.button(48, named(Items.BARRIER, Component.translatable("message.aliveworkplace.berry_breeder.book.clear")), p -> {
				setGoal(breeder, null);
				fill(menu, level, breeder, page);
			});
		}
		if (ModAttachments.BERRY_PLOT.get(breeder) != null) {
			menu.button(50, named(Items.MAP, Component.translatable("message.aliveworkplace.berry_breeder.book.stop_plot")), p -> {
				release(level, breeder, p);
				fill(menu, level, breeder, page);
			});
		}
	}

	/** What she's doing about her goal, for the book and her status. */
	public static Component goalLine(ServerLevel level, Villager breeder) {
		ResourceLocation goal = goal(breeder);
		if (goal == null) {
			return Component.translatable("message.aliveworkplace.berry_breeder.goal.none").withStyle(ChatFormatting.GRAY);
		}
		if (have(level, breeder).contains(goal)) {
			return Component.translatable("message.aliveworkplace.berry_breeder.goal.reached", name(goal)).withStyle(ChatFormatting.GREEN);
		}
		Optional<BerryChains.Mutation> step = step(level, breeder);
		if (step.isEmpty()) {
			return Component.translatable("message.aliveworkplace.berry_breeder.goal.out_of_reach", name(goal)).withStyle(ChatFormatting.RED);
		}
		BerryChains.Mutation m = step.get();
		return Component.translatable("message.aliveworkplace.berry_breeder.goal.step", name(goal), name(m.a()), name(m.b()), name(m.result()))
			.withStyle(ChatFormatting.GOLD);
	}

	private static ItemStack icon(BerryChains.BerryData data, ResourceLocation berry, List<BerryChains.Mutation> mutations,
			Set<ResourceLocation> found, boolean isGoal) {
		ItemStack icon = data.item(berry);
		if (icon.isEmpty()) {
			icon = new ItemStack(Items.SWEET_BERRIES);
		}
		boolean lit = found.contains(berry);
		Component name = icon.getHoverName().copy().withStyle(lit ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
		icon.set(DataComponents.CUSTOM_NAME, plain(name));
		List<Component> lines = new ArrayList<>();
		if (isGoal) {
			lines.add(Component.translatable("message.aliveworkplace.berry_breeder.book.goal").withStyle(ChatFormatting.GOLD));
		}
		if (lit) {
			lines.add(Component.translatable("message.aliveworkplace.berry_breeder.book.lit").withStyle(ChatFormatting.GREEN));
		}
		List<BerryChains.Mutation> pairs = BerryChains.madeFrom(mutations, berry);
		if (pairs.isEmpty()) {
			lines.add(Component.translatable("message.aliveworkplace.berry_breeder.book.wild").withStyle(ChatFormatting.GRAY));
		}
		for (BerryChains.Mutation m : pairs) {
			// Greyed while a parent is still missing.
			boolean ready = m.parents(found);
			lines.add(Component.translatable("message.aliveworkplace.berry_breeder.book.pair", name(m.a()), name(m.b()))
				.withStyle(ready ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
		}
		if (!lit && !isGoal) {
			lines.add(Component.translatable("message.aliveworkplace.berry_breeder.book.click").withStyle(ChatFormatting.GRAY));
		}
		icon.set(DataComponents.LORE, new ItemLore(lines.stream().map(BerryBreeders::plain).toList()));
		if (lit || isGoal) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
	}

	/** A click on a berry in the book: it becomes her goal (she says so, or why not). */
	public static void choose(ServerPlayer player, Villager breeder, ResourceLocation berry) {
		ServerLevel level = (ServerLevel) breeder.level();
		BerryChains.BerryData data = data();
		if (data == null) {
			return;
		}
		if (have(level, breeder).contains(berry)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.berry_breeder.goal.have", name(berry)).withStyle(ChatFormatting.YELLOW));
			return;
		}
		Optional<List<BerryChains.Mutation>> plan = BerryChains.plan(have(level, breeder), data.mutations(), berry);
		if (plan.isEmpty()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.berry_breeder.goal.cannot", name(berry)).withStyle(ChatFormatting.RED));
			return;
		}
		setGoal(breeder, berry);
		level.playSound(null, breeder, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.berry_breeder.goal.set", breeder.getDisplayName(), name(berry),
			plan.get().size()).withStyle(ChatFormatting.GREEN));
	}

	private static ItemStack named(Item item, Component name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name));
		return stack;
	}

	// Item names and lore are italic unless told otherwise.
	private static Component plain(Component c) {
		return c.copy().withStyle(s -> s.withItalic(false));
	}

	/** The farmland in her plot with room above it to plant (the spot to plant is above it). */
	static List<BlockPos> beds(ServerLevel level, BoundingBox box) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() - 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(p).is(Blocks.FARMLAND)) {
				out.add(p.above().immutable());
			}
		}
		return out;
	}

	private BerryBreeders() {
	}
}
