package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Which job crafts what for builders (ROADMAP 46.1, owner 2026-11-09: "the carpenter shouldn't build brewing stands").
 * The split is data, an item tag per job ({@code aliveworkplace:crafts/<job>}), so a pack can change it. An item in no
 * tag that some stonecutter recipe makes is the Mason's (every stonecutter cut is his); else it goes by its recipe's
 * ingredients ({@link #byRecipe}): logs or planks to the Carpenter, stone or bricks to the Mason, ingots to the Tinkerer,
 * wool or dye to the Shepherd, else to the Carpenter only if it has some wood in it. A vanilla thing on no list and
 * fitting no rule stays with whoever made it before 46.1 (the Carpenter at the crafting table, the Mason at his kiln).
 *
 * <p>The Shepherd weaves his own list at the loom since 46.2 ({@link WeaverWork}). Until 46.3-46.6 teach the Florist,
 * Cleric and Beekeeper to craft and bring the Glassblower, what their lists name is made by a stand-in
 * ({@link Job#standIn}) who made it before, so nothing that was made becomes unmakable: the Mason for the Glassblower's
 * glass (he fires it already), the Leatherworker (the village's dyer) for the Florist's and Beekeeper's, and the
 * Librarian for the Cleric's. Players read the job that makes it today ({@link #maker}).
 *
 * <p>What a job gathers rather than makes is data too ({@code aliveworkplace:gathers/<job>}): the Storehouse's details
 * pane names the gatherer (logs the Lumberjack, ore, sand and clay the Miner, wool the Shepherd).
 */
public final class CraftJobs {
	/** A job of the table, in the order its tag is looked at (an item in two tags is the first one's). */
	public enum Job {
		CARPENTER("carpenter", () -> ModVillagers.CARPENTER, null),
		MASON("mason", () -> VillagerProfession.MASON, null),
		GLASSBLOWER("glassblower", () -> null, "mason"),
		TINKERER("tinkerer", () -> ModVillagers.TINKERER, null),
		SHEPHERD("shepherd", () -> VillagerProfession.SHEPHERD, null),
		FLORIST("florist", () -> ModVillagers.FLORIST, "leatherworker"),
		CLERIC("cleric", () -> VillagerProfession.CLERIC, "librarian"),
		BEEKEEPER("beekeeper", () -> ModVillagers.BEEKEEPER, "leatherworker"),
		LEATHERWORKER("leatherworker", () -> VillagerProfession.LEATHERWORKER, null),
		LIBRARIAN("librarian", () -> VillagerProfession.LIBRARIAN, null);

		private final String key;
		private final Supplier<VillagerProfession> profession;
		@Nullable
		private final String standIn;
		private final TagKey<Item> tag;

		Job(String key, Supplier<VillagerProfession> profession, @Nullable String standIn) {
			this.key = key;
			this.profession = profession;
			this.standIn = standIn;
			this.tag = TagKey.create(Registries.ITEM, AliveWorkplace.id("crafts/" + key));
		}

		public String key() {
			return key;
		}

		public TagKey<Item> tag() {
			return tag;
		}

		/** The villager job, or null for one that isn't in the game yet (the Glassblower until 46.6). */
		@Nullable
		public VillagerProfession profession() {
			return profession.get();
		}

		/** Whether this job crafts for builders in this version (else its {@link #standIn} makes its list). */
		public boolean craftsNow() {
			return standIn == null;
		}

		/** Who makes this job's list meanwhile: itself once it crafts. */
		public Job standIn() {
			return standIn == null ? this : byKey(standIn);
		}

		static Job byKey(String key) {
			for (Job job : values()) {
				if (job.key.equals(key)) {
					return job;
				}
			}
			throw new IllegalArgumentException(key);
		}
	}

	/** A job that gathers raw things (the Storehouse's "Gathered by" line). */
	public enum Gatherer {
		LUMBERJACK("lumberjack", () -> ModVillagers.LUMBERJACK),
		MINER("miner", () -> ModVillagers.MINER),
		SHEPHERD("shepherd", () -> VillagerProfession.SHEPHERD),
		FARMER("farmer", () -> VillagerProfession.FARMER),
		FISHERMAN("fisherman", () -> VillagerProfession.FISHERMAN),
		BEEKEEPER("beekeeper", () -> ModVillagers.BEEKEEPER),
		FLORIST("florist", () -> ModVillagers.FLORIST);

		private final String key;
		private final Supplier<VillagerProfession> profession;
		private final TagKey<Item> tag;

		Gatherer(String key, Supplier<VillagerProfession> profession) {
			this.key = key;
			this.profession = profession;
			this.tag = TagKey.create(Registries.ITEM, AliveWorkplace.id("gathers/" + key));
		}

		public String key() {
			return key;
		}

		public TagKey<Item> tag() {
			return tag;
		}

		public VillagerProfession profession() {
			return profession.get();
		}
	}

	private static final TagKey<Item> INGOTS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots"));
	private static final TagKey<Item> DYES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dyes"));

	private CraftJobs() {
	}

	/** The job whose list {@code item} is on (its tag, a stonecutter cut, else its recipe), or null for none. */
	@Nullable
	public static Job of(ServerLevel level, Item item) {
		Job tagged = tagged(item);
		if (tagged != null) {
			return tagged;
		}
		if (!Crafting.recipesFor(level, Crafting.Kind.STONECUTTING, item).isEmpty()) {
			return Job.MASON;
		}
		Job byRecipe = byRecipe(level, item);
		String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
		if (byRecipe != null || !"minecraft".equals(namespace) && !AliveWorkplace.MOD_ID.equals(namespace)) {
			return byRecipe;
		}
		// Nothing made before 46.1 becomes unmakable: a vanilla thing (or one of ours) on no job's list (bread, a diamond
		// helmet, a Price Tag) stays with whoever made it then, the Carpenter at the crafting table, the Mason at his kiln
		if (!Crafting.recipesFor(level, Crafting.Kind.CRAFTING, item).isEmpty()) {
			return Job.CARPENTER;
		}
		return Crafting.recipesFor(level, Crafting.Kind.KILN, item).isEmpty() ? null : Job.MASON;
	}

	/** The job that makes {@code item} today: its own, or the stand-in while that one doesn't craft yet. */
	@Nullable
	public static Job maker(ServerLevel level, Item item) {
		Job job = of(level, item);
		return job == null ? null : job.standIn();
	}

	/** Whether a villager of {@code job} makes {@code item} for builders today. */
	public static boolean makes(ServerLevel level, Job job, Item item) {
		return maker(level, item) == job;
	}

	/** The first job whose tag holds {@code item}, or null. */
	@Nullable
	public static Job tagged(Item item) {
		for (Job job : Job.values()) {
			if (item.builtInRegistryHolder().is(job.tag)) {
				return job;
			}
		}
		return null;
	}

	/** The first gatherer whose tag holds {@code item}, or null. */
	@Nullable
	public static Gatherer gatherer(Item item) {
		for (Gatherer g : Gatherer.values()) {
			if (item.builtInRegistryHolder().is(g.tag)) {
				return g;
			}
		}
		return null;
	}

	/**
	 * The rule for an item in no tag (a modded one): its crafting recipe's ingredients. Logs or planks (or wooden slabs,
	 * stairs, fences) make it the Carpenter's, else stone or bricks the Mason's, else ingots the Tinkerer's, else wool or
	 * dye the Shepherd's, else the Carpenter's only if some ingredient is wood (a stick, a chest). Null when none fits.
	 */
	@Nullable
	public static Job byRecipe(ServerLevel level, Item item) {
		List<RecipeHolder<?>> recipes = Crafting.recipesFor(level, Crafting.Kind.CRAFTING, item);
		if (recipes.isEmpty()) {
			return null;
		}
		List<ItemStack> makings = new ArrayList<>();
		for (Ingredient ingredient : Recipes.ingredients(recipes.get(0).value())) {
			ItemStack[] options = Recipes.options(ingredient);
			if (options.length > 0) {
				makings.add(options[0]);
			}
		}
		if (makings.stream().anyMatch(CraftJobs::isTimber)) {
			return Job.CARPENTER;
		}
		if (makings.stream().anyMatch(CraftJobs::isStone)) {
			return Job.MASON;
		}
		if (makings.stream().anyMatch(s -> s.is(INGOTS) || path(s).endsWith("_ingot"))) {
			return Job.TINKERER;
		}
		if (makings.stream().anyMatch(s -> s.is(ItemTags.WOOL) || s.is(DYES) || path(s).endsWith("_wool") || path(s).endsWith("_dye"))) {
			return Job.SHEPHERD;
		}
		if (makings.stream().anyMatch(CraftJobs::isWood)) {
			return Job.CARPENTER;
		}
		return null;
	}

	private static String path(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	private static boolean isTimber(ItemStack s) {
		return s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS) || s.is(ItemTags.WOODEN_SLABS) || s.is(ItemTags.WOODEN_STAIRS)
			|| s.is(ItemTags.WOODEN_FENCES);
	}

	private static boolean isStone(ItemStack s) {
		String path = path(s);
		return s.is(ItemTags.STONE_CRAFTING_MATERIALS) || s.is(ItemTags.STONE_TOOL_MATERIALS) || s.is(ItemTags.STONE_BRICKS)
			|| s.is(ItemTags.TERRACOTTA) || path.contains("stone") || path.contains("brick") || path.contains("cobble");
	}

	private static boolean isWood(ItemStack s) {
		String path = path(s);
		return isTimber(s) || path.equals("stick") || path.contains("wood") || path.contains("plank") || path.contains("log")
			|| path.equals("chest") || path.equals("barrel");
	}

	/** The working villagers of {@code profession} within {@code radius} of {@code pos}, nearest first. */
	public static List<Villager> workersNear(ServerLevel level, BlockPos pos, int radius, @Nullable VillagerProfession profession) {
		if (profession == null) {
			return List.of();
		}
		List<Villager> found = new ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(radius),
			v -> v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == profession));
		found.sort(Comparator.comparingDouble(v -> v.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
		return found;
	}

	/** "the Carpenter (Ada)", or "the Carpenter" for a villager with no name of their own. */
	static Component who(Villager villager, String named, String unnamed) {
		Component job = io.github.jcondedata.aliveworkplace.work.Stations.name(villager.getVillagerData().getProfession());
		return villager.hasCustomName() ? Component.translatable(named, job, villager.getCustomName())
			: Component.translatable(unnamed, job);
	}

	/**
	 * The Storehouse's details line for {@code item} (46.1): who gathers it, or else who makes it, by name when one works
	 * within {@code radius} of {@code pos}, else the job to hire; "No job makes or gathers this" when no job does either.
	 */
	public static List<Component> storeLines(ServerLevel level, BlockPos pos, int radius, Item item) {
		List<Component> lines = new ArrayList<>();
		Gatherer gatherer = gatherer(item);
		if (gatherer != null) {
			// a raw thing: its gatherer only (raw iron is dug, though it can be unpacked from a block of it)
			List<Villager> here = workersNear(level, pos, radius, gatherer.profession());
			lines.add(here.isEmpty() ? Component.translatable("screen.aliveworkplace.storehouse.hire_gatherer." + gatherer.key())
				: who(here.get(0), "screen.aliveworkplace.storehouse.gathered_by", "screen.aliveworkplace.storehouse.gathered_by_unnamed"));
			return lines;
		}
		Job maker = maker(level, item);
		if (maker != null) {
			List<Villager> here = workersNear(level, pos, radius, maker.profession());
			lines.add(here.isEmpty()
				? Component.translatable("screen.aliveworkplace.storehouse.hire_maker", io.github.jcondedata.aliveworkplace.work.Stations.name(maker.profession()))
				: who(here.get(0), "screen.aliveworkplace.storehouse.made_by_worker", "screen.aliveworkplace.storehouse.made_by_worker_unnamed"));
		}
		if (lines.isEmpty()) {
			lines.add(Component.translatable("screen.aliveworkplace.storehouse.no_job"));
		}
		return lines;
	}
}
