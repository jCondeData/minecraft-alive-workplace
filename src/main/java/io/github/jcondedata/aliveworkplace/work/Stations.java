package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.fossil.FossilScientists;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Which block each job works at, and the item that picks it: the owner's "fewer job blocks" (ROADMAP 21.1a).
 * <p>
 * Related jobs share a block. A jobless villager by a vanilla one takes the vanilla job by itself, as in vanilla (jobless
 * villagers take a block for the first job registered at it, and vanilla's are registered first; a worker of ours who
 * lost their block takes a free one of its kind again, as vanilla workers do). The player
 * picks another job by sneak-right-clicking the villager with that job's item; the same switches a villager already
 * working there, and brings back the vanilla job with its own item. A villager with no job, or whose job is at another
 * kind of block, takes the nearest free block of the right kind within {@link #REACH} blocks. Choosing a job doesn't
 * hire the villager: that stays the vanilla jobs' item on a villager who already has the job ({@link Hiring}).
 * <p>
 * The blocks this replaced (the Builder's Bench, the Fruit Basket...) are still registered and still work where
 * they're placed: each profession holds its old point of interest too.
 */
public final class Stations {
	/** How far from the villager a free block can be for a job picked by item. */
	public static final double REACH = 4.5;

	/** A job at a station, and the items that pick it. */
	public record Job(Supplier<VillagerProfession> profession, Predicate<ItemStack> item) {
	}

	/** A kind of workstation: its points of interest, a block to name it by, and the jobs that share it. */
	public record Station(Predicate<Holder<PoiType>> poi, Block block, List<Job> jobs) {
		/** The job at this station that {@code stack} picks. */
		public Optional<Job> jobFor(ItemStack stack) {
			return jobs.stream().filter(j -> j.item().test(stack)).findFirst();
		}

		public boolean has(VillagerProfession profession) {
			return jobs.stream().anyMatch(j -> j.profession().get() == profession);
		}

		/** Whether a jobless villager takes this block by themselves (for its first job); if not, only an item gives a job. */
		public boolean byItself() {
			return !ONLY_BY_ITEM.contains(block);
		}
	}

	/** Blocks no job takes by itself: a crafting table, a beehive (or nest), a jukebox, a mailbox. */
	private static final Set<Block> ONLY_BY_ITEM = Set.of(Blocks.CRAFTING_TABLE, Blocks.BEEHIVE, Blocks.JUKEBOX, ModBlocks.MAILBOX);
	/** The jobs that need Cobblemon (their items are Cobblemon's, or they work with Pokémon). */
	private static final Set<String> COBBLEMON_JOBS = Set.of("ball_smith", "pokemon_trader", "trainer", "trainer_leader", "tutor",
		"fossil_scientist");

	private static Predicate<ItemStack> any(Item... items) {
		Set<Item> set = Set.of(items);
		return stack -> set.contains(stack.getItem());
	}

	private static Predicate<ItemStack> tag(net.minecraft.tags.TagKey<Item> tag) {
		return stack -> stack.is(tag);
	}

	/** A Cobblemon item, by id: {@code cobblemon:<path>} where {@code path} matches. Nothing without Cobblemon. */
	private static Predicate<ItemStack> cobblemon(Predicate<String> path) {
		return stack -> {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			return id.getNamespace().equals("cobblemon") && path.test(id.getPath());
		};
	}

	private static Job job(Supplier<VillagerProfession> profession, Predicate<ItemStack> item) {
		return new Job(profession, item);
	}

	@SafeVarargs
	private static Predicate<Holder<PoiType>> is(net.minecraft.resources.ResourceKey<PoiType>... keys) {
		return holder -> {
			for (var key : keys) {
				if (holder.is(key)) {
					return true;
				}
			}
			return false;
		};
	}

	/** Every shared workstation, the vanilla (or first) job first. */
	public static final List<Station> ALL = List.of(
		new Station(is(PoiTypes.FARMER), Blocks.COMPOSTER, List.of(
			job(() -> VillagerProfession.FARMER, any(Items.WHEAT, Items.WHEAT_SEEDS)),
			job(() -> ModVillagers.ORCHARD_KEEPER, any(Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.APPLE)),
			job(() -> ModVillagers.FLORIST, tag(ItemTags.SMALL_FLOWERS)),
			job(() -> ModVillagers.COMPOSTER, any(Items.BONE_MEAL)))),
		new Station(is(PoiTypes.ARMORER), Blocks.BLAST_FURNACE, List.of(
			job(() -> VillagerProfession.ARMORER, any(Items.COAL, Items.CHARCOAL)),
			job(() -> ModVillagers.MINER, tag(ItemTags.PICKAXES)))),
		new Station(is(PoiTypes.FLETCHER), Blocks.FLETCHING_TABLE, List.of(
			job(() -> VillagerProfession.FLETCHER, any(Items.FLINT)),
			job(() -> ModVillagers.LUMBERJACK, tag(ItemTags.AXES)))),
		new Station(is(PoiTypes.TOOLSMITH), Blocks.SMITHING_TABLE, List.of(
			job(() -> VillagerProfession.TOOLSMITH, any(Items.IRON_INGOT)),
			job(() -> ModVillagers.TINKERER, any(Items.REDSTONE)),
			job(() -> ModVillagers.BALL_SMITH, cobblemon(p -> p.endsWith("_apricorn"))))),
		new Station(is(PoiTypes.LEATHERWORKER), Blocks.CAULDRON, List.of(
			job(() -> VillagerProfession.LEATHERWORKER, any(Items.LEATHER)),
			job(() -> ModVillagers.SIFTER, any(Items.GRAVEL, Items.SAND, Items.RED_SAND, Items.SOUL_SAND)))),
		new Station(is(PoiTypes.LIBRARIAN), Blocks.LECTERN, List.of(
			job(() -> VillagerProfession.LIBRARIAN, any(Items.LAPIS_LAZULI)),
			job(() -> ModVillagers.SCHOLAR, any(Items.PAPER)),
			job(() -> ModVillagers.TEACHER, any(Items.BOOK)))),
		new Station(is(PoiTypes.CARTOGRAPHER), Blocks.CARTOGRAPHY_TABLE, List.of(
			job(() -> VillagerProfession.CARTOGRAPHER, any(Items.COMPASS)),
			job(() -> ModVillagers.NETHERWORKER, any(Items.NETHERRACK)))),
		new Station(is(PoiTypes.CLERIC), Blocks.BREWING_STAND, List.of(
			job(() -> VillagerProfession.CLERIC, any(Items.GLASS_BOTTLE)),
			job(() -> ModVillagers.NURSE, any(Items.HONEY_BOTTLE)),
			job(() -> ModVillagers.UNDERTAKER, any(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.TOTEM_OF_UNDYING)))),
		new Station(is(PoiTypes.BUTCHER), Blocks.SMOKER, List.of(
			job(() -> VillagerProfession.BUTCHER, any(Items.LEAD)),
			job(() -> ModVillagers.CHEF, any(Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT, Items.COD, Items.SALMON,
				Items.POTATO)),
			job(() -> ModVillagers.RANCHER, any(Items.SADDLE, Items.GOLDEN_CARROT)))),
		new Station(is(PoiTypes.WEAPONSMITH), Blocks.GRINDSTONE, List.of(
			job(() -> VillagerProfession.WEAPONSMITH, any(Items.IRON_INGOT)),
			job(() -> ModVillagers.GUARD, tag(ItemTags.SWORDS)))),
		new Station(is(ModVillagers.CRAFTING_TABLE_POI), Blocks.CRAFTING_TABLE, List.of(
			job(() -> ModVillagers.CARPENTER, tag(ItemTags.PLANKS)))),
		new Station(is(PoiTypes.BEEHIVE, PoiTypes.BEE_NEST), Blocks.BEEHIVE, List.of(
			job(() -> ModVillagers.BEEKEEPER, any(Items.GLASS_BOTTLE, Items.SHEARS)))),
		new Station(is(ModVillagers.JUKEBOX_POI), Blocks.JUKEBOX, List.of(
			job(() -> ModVillagers.BARD, stack -> stack.has(DataComponents.JUKEBOX_PLAYABLE)))),
		new Station(is(ModVillagers.MAILBOX_POI), ModBlocks.MAILBOX, List.of(
			job(() -> ModVillagers.POSTMAN, any(Items.PAPER)))),
		new Station(is(ModVillagers.SHOP_COUNTER_POI), ModBlocks.SHOP_COUNTER, List.of(
			job(() -> ModVillagers.SHOPKEEPER, any(Items.EMERALD)),
			job(() -> ModVillagers.INNKEEPER, tag(ItemTags.BEDS)),
			job(() -> ModVillagers.POKEMON_TRADER, cobblemon(p -> p.equals("poke_ball"))))),
		new Station(is(ModVillagers.TRAINING_POST_POI), ModBlocks.TRAINING_POST, List.of(
			job(() -> ModVillagers.TRAINER, cobblemon(p -> p.equals("poke_ball"))),
			job(() -> ModVillagers.TRAINER_LEADER, any(Items.GOLD_BLOCK)),
			job(() -> ModVillagers.TUTOR, any(Items.BOOK)),
			job(() -> ModVillagers.FOSSIL_SCIENTIST, FossilScientists::isFossil)))
	);

	/**
	 * Sneak-right-click with {@code stack}: gives {@code villager} the job that item picks, at the block they work at or
	 * the nearest free one of its kind. {@link InteractionResult#PASS} when the item picks no job, or the villager
	 * already has that one (so the item's other uses, such as hiring, still happen).
	 */
	public static InteractionResult choose(ServerPlayer player, Villager villager, ItemStack stack) {
		if (stack.isEmpty() || villager.isBaby() || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
			return InteractionResult.PASS;
		}
		ServerLevel level = (ServerLevel) villager.level();
		VillagerProfession now = villager.getVillagerData().getProfession();
		if (picks(stack, now)) {
			return InteractionResult.PASS; // they already do the job this item picks (at a new block or an old one)
		}
		Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(g -> g.dimension().equals(level.dimension()));
		Optional<Holder<PoiType>> here = site.flatMap(g -> level.getPoiManager().getType(g.pos()));
		// First the block they already work at, then the nearest free block where the item picks a job.
		for (Station station : ALL) {
			Optional<Job> job = station.jobFor(stack);
			if (job.isPresent() && here.isPresent() && station.poi().test(here.get())) {
				return take(player, villager, site.get().pos(), job.get().profession().get());
			}
		}
		record Found(BlockPos pos, VillagerProfession profession) {
		}
		List<Found> found = new ArrayList<>();
		List<Station> kinds = new ArrayList<>();
		for (Station station : ALL) {
			Optional<Job> job = station.jobFor(stack);
			if (job.isEmpty()) {
				continue;
			}
			kinds.add(station);
			free(level, station, villager.blockPosition()).ifPresent(pos -> found.add(new Found(pos, job.get().profession().get())));
		}
		if (kinds.isEmpty()) {
			return InteractionResult.PASS;
		}
		Optional<Found> nearest = found.stream().min(Comparator.comparingDouble(f -> f.pos().distToCenterSqr(villager.position())));
		if (nearest.isEmpty()) {
			// No block of the right kind close by: say which one the job needs.
			Station first = kinds.get(0);
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.job.needs_station",
				name(first.jobFor(stack).get().profession().get()), first.block().getName()).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.CONSUME;
		}
		return take(player, villager, nearest.get().pos(), nearest.get().profession());
	}

	/** Whether {@code stack} picks a job at some station, and that job is {@code profession}. */
	public static boolean picks(ItemStack stack, VillagerProfession profession) {
		return ALL.stream().anyMatch(s -> s.jobFor(stack).filter(j -> j.profession().get() == profession).isPresent());
	}

	/** Whether {@code stack} picks any job at all. */
	public static boolean picksAny(ItemStack stack) {
		return !stack.isEmpty() && ALL.stream().anyMatch(s -> s.jobFor(stack).isPresent());
	}

	/** The nearest block of {@code station}'s kind within {@link #REACH} of {@code near} that nobody works at. */
	static Optional<BlockPos> free(ServerLevel level, Station station, BlockPos near) {
		PoiManager poi = level.getPoiManager();
		int radius = (int) Math.ceil(REACH);
		// Beehives and nests have no tickets for anyone (bees don't take them): any of them will do.
		PoiManager.Occupancy occupancy = station.block() == Blocks.BEEHIVE ? PoiManager.Occupancy.ANY : PoiManager.Occupancy.HAS_SPACE;
		return poi.findClosest(station.poi(), near, radius, occupancy)
			.filter(pos -> pos.distToCenterSqr(near.getCenter()) <= REACH * REACH);
	}

	/**
	 * Gives {@code villager} {@code profession} at {@code station}, letting go of any other workstation. Only what the
	 * job is changes: an unhired villager stays the village's (so they keep sharing chests with its other workers, as
	 * when a villager took one of the old job blocks by itself), and someone else's hired worker can't be changed.
	 */
	static InteractionResult take(ServerPlayer player, Villager villager, BlockPos station, VillagerProfession profession) {
		if (!Friends.mayCommand(player, villager)) {
			return Hiring.hire(player, villager, Component.empty()); // refuses, and says whose they are
		}
		Component who = villager.getDisplayName(); // before: an unnamed villager is called by their job
		assign((ServerLevel) villager.level(), villager, station, profession);
		villager.level().playSound(null, villager, net.minecraft.sounds.SoundEvents.VILLAGER_YES, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
		Chat.chat(player, Component.translatable("message.aliveworkplace.job.chosen", who, name(profession),
			villager.level().getBlockState(station).getBlock().getName()).withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	/** Makes {@code villager} a {@code profession} working at {@code station} right away. */
	public static void assign(ServerLevel level, Villager villager, BlockPos station, VillagerProfession profession) {
		GlobalPos target = GlobalPos.of(level.dimension(), station);
		Optional<GlobalPos> old = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (old.isEmpty() || !old.get().equals(target)) {
			old.filter(g -> g.dimension().equals(level.dimension())).ifPresent(g -> level.getPoiManager().release(g.pos()));
			level.getPoiManager().take(h -> true, (h, p) -> p.equals(station), station, 1);
		}
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, target);
		villager.setVillagerData(villager.getVillagerData().setProfession(profession));
		if (villager.getVillagerXp() == 0) {
			villager.setVillagerXp(1); // keeps the profession even if the block is briefly missing
		}
		villager.refreshBrain(level);
	}

	/** The job's name, as the game shows it over a villager ("Orchard Keeper"). */
	public static Component name(VillagerProfession profession) {
		return Component.translatable("entity.minecraft.villager." + profession.name());
	}

	/** The station {@code block} is (a bee nest counts as a beehive, any cauldron as a cauldron), if it's one. */
	public static Optional<Station> at(Block block) {
		return PoiTypes.forState(block.defaultBlockState()).flatMap(h -> ALL.stream().filter(s -> s.poi().test(h)).findFirst());
	}

	/** Whether {@code job} can be had in this game (the Pokémon jobs need Cobblemon). */
	public static boolean available(Job job) {
		ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job.profession().get());
		return !COBBLEMON_JOBS.contains(id.getPath()) || io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon");
	}

	/** The translation key saying which items pick {@code job} ("Sweet berries, glow berries or an apple"). */
	public static String itemKey(Job job) {
		ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job.profession().get());
		return "station.aliveworkplace.item." + id.getNamespace() + "." + id.getPath();
	}

	/** The station {@code profession} works at since 21.1a (its old block aside), if it shares one. */
	public static Optional<Station> of(VillagerProfession profession) {
		return ALL.stream().filter(s -> s.has(profession)).findFirst();
	}

	private Stations() {
	}
}
