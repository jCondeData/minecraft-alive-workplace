package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.school.Schools;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Six more personal requests (ROADMAP 31.10), each with its objective: a letter to family ({@code deliver}), bring back
 * my love ({@code revive}), a cat by the door ({@code pet_near_home}), off to school ({@code schooled}), a chapel wedding
 * ({@code build}) and the lost heirloom ({@code fetch}). The objectives are registered in {@link Objectives}; this class
 * also holds the letter and the heirloom (paper and vanilla items with a name, lore and a quest mark, no new art), the
 * right-clicks that carry them, the heirloom's placement when a helper comes near, and what happens when a request is
 * done (the second wedding, the chapel wedding and its feast, the heirloom's story in the chronicle).
 */
public final class RequestErrands {
	/** How near (blocks, across the ground) a helper comes to the heirloom's place before it is put there. */
	public static final int PLACE_NEAR = 64;
	/** How far round the place (chunks) its chests are looked for. */
	public static int PLACE_CHUNKS = 3;
	/** How often (ticks) waiting heirlooms are looked at. */
	public static final int CHECK_EVERY = 40;
	/** How long (days) a courting couple's wedding waits for a Chapel, instead of {@link Couples#COURTSHIP_DAYS}. */
	public static final int CHAPEL_WAIT_DAYS = 5;

	/** The structures a heirloom may have been lost at (the request file's place). */
	public static final ResourceLocation HEIRLOOM_PLACES = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("heirloom_places");

	static final String LETTER = "aliveworkplace_letter";
	static final String REPLY = "aliveworkplace_reply";
	static final String HEIRLOOM = "aliveworkplace_heirloom";

	/** The heirlooms a family may have lost, by key. */
	public static final Map<String, Item> HEIRLOOMS = new LinkedHashMap<>();

	static {
		HEIRLOOMS.put("compass", Items.COMPASS);
		HEIRLOOMS.put("pocket_watch", Items.CLOCK);
		HEIRLOOMS.put("spyglass", Items.SPYGLASS);
		HEIRLOOMS.put("horn", Items.GOAT_HORN);
		HEIRLOOMS.put("crystal", Items.AMETHYST_SHARD);
		HEIRLOOMS.put("diary", Items.WRITTEN_BOOK);
	}

	/** Who the letter's relative is to the giver, by key. */
	static final List<String> RELATIONS = List.of("cousin", "second_cousin", "aunt_or_uncle");

	public static void init() {
		Platform.get().onUseEntity(RequestErrands::use);
	}

	// --- The objectives -------------------------------------------------------------------------------------------

	/**
	 * {@code deliver}: carry a sealed letter to a relative of the giver's in another village with a hall within caravan
	 * range, and their reply back. Progress 1 when the relative has the letter, 2 when the giver has the reply. The
	 * relative is picked among that village's named grown-ups when the request is asked, so that village must be loaded.
	 */
	public record Deliver(Optional<BlockPos> to, Optional<UUID> relative, String name, String village, String relation)
		implements Objectives.Objective, Objectives.Started {
		static Deliver read(JsonObject json) {
			Optional<BlockPos> to = json.has("to") ? Optional.of(BlockPos.of(json.get("to").getAsLong())) : Optional.empty();
			Optional<UUID> relative = json.has("relative") ? Optional.of(UUID.fromString(GsonHelper.getAsString(json, "relative"))) : Optional.empty();
			String relation = GsonHelper.getAsString(json, "relation", RELATIONS.get(0));
			return new Deliver(to, relative, GsonHelper.getAsString(json, "name", ""), GsonHelper.getAsString(json, "village", ""),
				RELATIONS.contains(relation) ? relation : RELATIONS.get(0));
		}

		@Override
		public String type() {
			return "deliver";
		}

		@Override
		public int need() {
			return 2;
		}

		@Override
		public Component line() {
			return relative.isEmpty() ? Component.translatable("quest.aliveworkplace.deliver.somewhere")
				: Component.translatable("quest.aliveworkplace.deliver", name, village);
		}

		/** "cousin Mira in Elmford". */
		@Override
		public Component what() {
			return Component.translatable("quest.aliveworkplace.deliver_what", Component.translatable("request.aliveworkplace.relative." + relation), name, village);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			to.ifPresent(t -> o.addProperty("to", t.asLong()));
			relative.ifPresent(r -> o.addProperty("relative", r.toString()));
			o.addProperty("name", name);
			o.addProperty("village", village);
			o.addProperty("relation", relation);
			return o;
		}

		@Override
		@Nullable
		public Objectives.Objective resolve(Objectives.Context context) {
			if (relative.isPresent() && to.isPresent()) {
				return this;
			}
			Villager giver = context.giver;
			if (giver == null) {
				return null;
			}
			ServerLevel level = context.level;
			List<BlockPos> halls = new ArrayList<>();
			for (Caravans.Village v : Caravans.neighbours(level, context.hall)) {
				if (level.isLoaded(v.hall()) && level.getBlockEntity(v.hall()) instanceof VillageHallBlockEntity) {
					halls.add(v.hall());
				}
			}
			halls.sort(Comparator.comparingLong(BlockPos::asLong)); // the same order every time, so a fixed random picks the same village
			while (!halls.isEmpty()) {
				BlockPos there = halls.remove(context.random.nextInt(halls.size()));
				List<Villager> people = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(there),
					v -> v != giver && !v.isBaby() && Friendship.eligible(v) && !VillageHalls.area(context.hall).contains(v.position())));
				if (people.isEmpty()) {
					continue;
				}
				people.sort(Comparator.comparing(Villager::getUUID));
				Villager relative = people.get(context.random.nextInt(people.size()));
				Caravans.Village known = Caravans.Data.get(level).village(there);
				String village = (known != null ? known.name() : VillageHalls.name(level, there)).getString();
				return new Deliver(Optional.of(there.immutable()), Optional.of(relative.getUUID()), Objectives.name(relative), village,
					RELATIONS.get(context.random.nextInt(RELATIONS.size())));
			}
			return null;
		}

		/** Whoever takes it on gets the sealed letter, unless the relative has it already or they carry one. */
		@Override
		public void start(ServerLevel level, BlockPos hall, Quest quest, ServerPlayer player) {
			int i = quest.objectives.indexOf(this);
			if (i >= 0 && quest.progress[i] == 0 && !carries(player, quest.id, false)) {
				give(player, letter(level, hall, quest, this, false));
			}
		}
	}

	/**
	 * {@code revive}: the giver, widowed and not with anyone new, wants their late partner back from the grave in the
	 * village ({@link Graves#revive}, an Undertaker's work). Done when that partner is alive again; they then marry again.
	 */
	public record Revive(Optional<UUID> partner, String name) implements Objectives.Looked {
		static Revive read(JsonObject json) {
			return new Revive(json.has("partner") ? Optional.of(UUID.fromString(GsonHelper.getAsString(json, "partner"))) : Optional.empty(),
				GsonHelper.getAsString(json, "name", ""));
		}

		@Override
		public String type() {
			return "revive";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.revive", name);
		}

		@Override
		public Component what() {
			return Component.literal(name);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			partner.ifPresent(p -> o.addProperty("partner", p.toString()));
			o.addProperty("name", name);
			return o;
		}

		@Override
		@Nullable
		public Objectives.Objective resolve(Objectives.Context context) {
			if (partner.isPresent()) {
				return this;
			}
			Villager giver = context.giver;
			Couples.LatePartner late = giver == null ? null : ModAttachments.LATE_PARTNER.get(giver);
			if (late == null || Couples.partner(giver) != null) {
				return null;
			}
			return grave(context.level, context.hall, late.id()) == null ? null : new Revive(Optional.of(late.id()), late.name().getString());
		}

		@Override
		public int look(ServerLevel level, BlockPos hall, Villager giver, Quest quest, boolean dawn) {
			return partner.isPresent() && level.getEntity(partner.get()) instanceof Villager v && v.isAlive() ? 1 : 0;
		}
	}

	/** The grave in the village round {@code hall} of the villager {@code id}, or null. */
	@Nullable
	public static BlockPos grave(ServerLevel level, BlockPos hall, UUID id) {
		for (BlockPos pos : Graves.near(level, hall, VillageHalls.RADIUS + 8)) {
			if (VillageHalls.area(hall).contains(pos.getCenter()) && level.getBlockEntity(pos) instanceof GraveBlockEntity grave
				&& Nbt.hasUuid(grave.villager(), "UUID") && Nbt.getUuid(grave.villager(), "UUID").equals(id)) {
				return pos;
			}
		}
		return null;
	}

	/** {@code pet_near_home}: at dawn, a cat or a tamed wolf within {@code radius} blocks of the giver's bed. */
	public record PetNearHome(int radius) implements Objectives.Looked {
		static PetNearHome read(JsonObject json) {
			int radius = GsonHelper.getAsInt(json, "radius", 10);
			if (radius < 1) {
				throw new IllegalArgumentException("'radius' below 1");
			}
			return new PetNearHome(radius);
		}

		@Override
		public String type() {
			return "pet_near_home";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.pet_near_home", radius);
		}

		@Override
		public Component what() {
			return Component.translatable("quest.aliveworkplace.pet_near_home.what");
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("radius", radius);
			return o;
		}

		/** Not asked by someone with no bed, or with a pet by it already. */
		@Override
		@Nullable
		public Objectives.Objective resolve(Objectives.Context context) {
			BlockPos bed = context.giver == null ? null : bed(context.level, context.giver);
			return bed == null || pets(context.level, bed, radius) > 0 ? null : this;
		}

		@Override
		public int look(ServerLevel level, BlockPos hall, Villager giver, Quest quest, boolean dawn) {
			if (!dawn) {
				return -1;
			}
			BlockPos bed = bed(level, giver);
			return bed != null && pets(level, bed, radius) > 0 ? 1 : 0;
		}
	}

	/** {@code villager}'s bed in this dimension, or null. */
	@Nullable
	static BlockPos bed(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.HOME).filter(p -> p.dimension() == level.dimension()).map(GlobalPos::pos).orElse(null);
	}

	/**
	 * The cats and tamed wolves within {@code radius} blocks of {@code bed}, measured from the bed's floor to their feet
	 * (B423: from the bed's centre, half a block up, a cat sitting 10 blocks along the floor was 10.01 away).
	 */
	public static int pets(ServerLevel level, BlockPos bed, int radius) {
		AABB box = new AABB(bed).inflate(radius);
		double r2 = (double) radius * radius;
		Vec3 floor = Vec3.atBottomCenterOf(bed);
		return level.getEntitiesOfClass(Cat.class, box, c -> c.isAlive() && c.distanceToSqr(floor) <= r2).size()
			+ level.getEntitiesOfClass(Wolf.class, box, w -> w.isAlive() && w.isTame() && w.distanceToSqr(floor) <= r2).size();
	}

	/** {@code schooled}: the giver's child, who hasn't been to school, is schooled ({@link Schools#isSchooled}; it needs a Teacher). */
	public record Schooled(Optional<UUID> child, String name) implements Objectives.Looked {
		static Schooled read(JsonObject json) {
			return new Schooled(json.has("child") ? Optional.of(UUID.fromString(GsonHelper.getAsString(json, "child"))) : Optional.empty(),
				GsonHelper.getAsString(json, "name", ""));
		}

		@Override
		public String type() {
			return "schooled";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.schooled", name);
		}

		@Override
		public Component what() {
			return Component.literal(name);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			child.ifPresent(c -> o.addProperty("child", c.toString()));
			o.addProperty("name", name);
			return o;
		}

		@Override
		@Nullable
		public Objectives.Objective resolve(Objectives.Context context) {
			if (child.isPresent()) {
				return this;
			}
			Villager giver = context.giver;
			if (giver == null) {
				return null;
			}
			String parent = Objectives.name(giver);
			List<Villager> children = new ArrayList<>(context.level.getEntitiesOfClass(Villager.class, VillageHalls.area(context.hall), v -> {
				Families.Parents p = v.isAlive() && v.isBaby() && !Schools.isSchooled(v) ? Families.parents(v) : null;
				return p != null && (p.mother().getString().equals(parent) || p.father().getString().equals(parent));
			}));
			if (children.isEmpty()) {
				return null;
			}
			children.sort(Comparator.comparing(Villager::getUUID));
			Villager kid = children.get(context.random.nextInt(children.size()));
			return new Schooled(Optional.of(kid.getUUID()), Objectives.name(kid));
		}

		@Override
		public int look(ServerLevel level, BlockPos hall, Villager giver, Quest quest, boolean dawn) {
			return child.isPresent() && level.getEntity(child.get()) instanceof Villager kid ? Schools.isSchooled(kid) ? 1 : 0 : -1;
		}
	}

	/**
	 * {@code build}: a building of the blueprint {@code blueprint} (any of its styles) is finished in the village. With
	 * {@code wedding} (a chapel wedding), only asked by one of a courting couple, and done, they marry there with a feast.
	 */
	public record Build(ResourceLocation blueprint, boolean wedding) implements Objectives.Looked {
		static Build read(JsonObject json) {
			ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "blueprint", ""));
			if (id == null || id.getPath().isEmpty()) {
				throw new IllegalArgumentException("missing 'blueprint'");
			}
			return new Build(id, GsonHelper.getAsBoolean(json, "wedding", false));
		}

		@Override
		public String type() {
			return "build";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return io.github.jcondedata.aliveworkplace.work.Words.articled(wedding ? "quest.aliveworkplace.build.wedding" : "quest.aliveworkplace.build", 0, what());
		}

		@Override
		public Component what() {
			return Component.translatable("blueprint." + blueprint.getNamespace() + "." + blueprint.getPath());
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("blueprint", blueprint.toString());
			if (wedding) {
				o.addProperty("wedding", true);
			}
			return o;
		}

		@Override
		@Nullable
		public Objectives.Objective resolve(Objectives.Context context) {
			if (context.giver == null || finished(context.level, context.hall, blueprint)) {
				return null;
			}
			return !wedding || courting(context.level, context.hall, context.giver) != null ? this : null;
		}

		@Override
		public int look(ServerLevel level, BlockPos hall, Villager giver, Quest quest, boolean dawn) {
			return finished(level, hall, blueprint) ? 1 : 0;
		}
	}

	/** Whether a building of {@code blueprint} (any style of it) is finished in the village round {@code hall}. */
	public static boolean finished(ServerLevel level, BlockPos hall, ResourceLocation blueprint) {
		return BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream()
			.anyMatch(f -> BlueprintStyles.base(f.structure()).equals(blueprint));
	}

	/** Who {@code villager} is courting (not married yet), if they're loaded in the village round {@code hall}; else null. */
	@Nullable
	static Villager courting(ServerLevel level, BlockPos hall, Villager villager) {
		Couples.Partner p = Couples.partner(villager);
		return p != null && !p.married() && level.getEntity(p.id()) instanceof Villager other && other.isAlive()
			&& VillageHalls.area(hall).contains(other.position()) ? other : null;
	}

	/**
	 * {@code fetch}: the giver's family heirloom ({@link #HEIRLOOMS}), lost at a place (by default a structure of
	 * {@code #aliveworkplace:heirloom_places}) within {@code max} blocks, found and brought back to them. The place is
	 * looked up once, when it is asked; the heirloom is put there when a helper comes near ({@link #placeHeirlooms}),
	 * and whoever takes it on gets a map.
	 */
	public record Fetch(String heirloom, Places.Place place, int max) implements Objectives.Objective, Objectives.Started {
		static Fetch read(JsonObject json) {
			String heirloom = GsonHelper.getAsString(json, "heirloom", "");
			if (!heirloom.isEmpty() && !HEIRLOOMS.containsKey(heirloom)) {
				throw new IllegalArgumentException("unknown heirloom '" + heirloom + "'");
			}
			int max = GsonHelper.getAsInt(json, "max", 1500);
			if (max < 1) {
				throw new IllegalArgumentException("'max' below 1");
			}
			return new Fetch(heirloom, Places.read(json.get("place")), max);
		}

		@Override
		public String type() {
			return "fetch";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.fetch", what(), Places.name(place));
		}

		@Override
		public Component what() {
			return Component.translatable("request.aliveworkplace.heirloom." + (heirloom.isEmpty() ? "compass" : heirloom));
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("heirloom", heirloom);
			o.add("place", place.json());
			o.addProperty("max", max);
			return o;
		}

		@Override
		@Nullable
		public Objectives.Objective resolve(Objectives.Context context) {
			if (place.located() && !heirloom.isEmpty()) {
				return this;
			}
			if (context.giver == null) {
				return null;
			}
			Places.Place found = place.located() ? place : located(context.level, context.hall, place);
			if (found == null || found.found().get().distSqr(context.hall.atY(found.found().get().getY())) > (double) max * max) {
				return null;
			}
			List<String> keys = List.copyOf(HEIRLOOMS.keySet());
			return new Fetch(heirloom.isEmpty() ? keys.get(context.random.nextInt(keys.size())) : heirloom, found, max);
		}

		/** Whoever takes it on gets a map to where it was lost. */
		@Override
		public void start(ServerLevel level, BlockPos hall, Quest quest, ServerPlayer player) {
			if (place.located()) {
				ItemStack map = Places.map(level, place);
				map.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.heirloom_map", what()));
				give(player, map);
			}
		}
	}

	/** Places already looked up today, per hall (the asking resolves every villager's requests; a structure is looked up once a day). */
	private static final Map<String, Optional<Places.Place>> LOOKED = new LinkedHashMap<>();

	@Nullable
	static Places.Place located(ServerLevel level, BlockPos hall, Places.Place place) {
		String key = level.dimension().location() + "|" + hall.asLong() + "|" + Chronicle.day(level) + "|" + place.options();
		synchronized (LOOKED) {
			Optional<Places.Place> known = LOOKED.get(key);
			if (known != null) {
				return known.orElse(null);
			}
		}
		Places.Place found = Places.locate(level, hall, place);
		synchronized (LOOKED) {
			if (LOOKED.size() > 64) {
				LOOKED.clear();
			}
			LOOKED.put(key, Optional.ofNullable(found));
		}
		return found;
	}

	// --- The letter and the heirloom ------------------------------------------------------------------------------

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static Component plain(Component text) {
		return text.copy().withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY));
	}

	/** The quest mark: a glint, and the quest's id in the item's data. */
	private static void mark(ItemStack stack, String key, UUID quest, boolean reply) {
		CompoundTag tag = new CompoundTag();
		tag.putString(key, quest.toString());
		if (reply) {
			tag.putBoolean(REPLY, true);
		}
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
	}

	/** The giver's village name, as the letter says it. */
	private static Component home(ServerLevel level, BlockPos hall) {
		return VillageHalls.name(level, hall);
	}

	/** The sealed letter (paper) to {@code deliver}'s relative, or ({@code reply}) their reply to the giver. */
	public static ItemStack letter(ServerLevel level, BlockPos hall, Quest quest, Deliver deliver, boolean reply) {
		ItemStack paper = new ItemStack(Items.PAPER);
		mark(paper, LETTER, quest.id, reply);
		if (reply) {
			paper.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.letter.reply", deliver.name()));
			paper.set(DataComponents.LORE, new ItemLore(List.of(
				plain(Component.translatable("item.aliveworkplace.letter.reply_from", deliver.name(), deliver.village())),
				plain(Component.translatable("item.aliveworkplace.letter.for", quest.poster, home(level, hall))),
				plain(Component.translatable("item.aliveworkplace.letter.hint", quest.poster)))));
		} else {
			paper.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.letter.sealed", deliver.name()));
			paper.set(DataComponents.LORE, new ItemLore(List.of(
				plain(Component.translatable("item.aliveworkplace.letter.from", quest.poster, home(level, hall))),
				plain(Component.translatable("item.aliveworkplace.letter.for", deliver.name(), deliver.village())),
				plain(Component.translatable("item.aliveworkplace.letter.hint", deliver.name())))));
		}
		return paper;
	}

	/** The heirloom of {@code quest}: the vanilla item with its name, lore and the quest mark (the diary with three pages of her story). */
	public static ItemStack heirloom(Quest quest, Fetch fetch) {
		String key = fetch.heirloom().isEmpty() ? "compass" : fetch.heirloom();
		ItemStack stack = new ItemStack(HEIRLOOMS.get(key));
		mark(stack, HEIRLOOM, quest.id, false);
		if (key.equals("diary")) {
			List<Filterable<Component>> pages = new ArrayList<>();
			for (int page = 1; page <= 3; page++) {
				pages.add(Filterable.passThrough(Component.translatable("request.aliveworkplace.heirloom.diary.page" + page, quest.poster)));
			}
			stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(""), "", 0, pages, true));
		}
		stack.set(DataComponents.CUSTOM_NAME, fetch.what().copy().withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			plain(Component.translatable("item.aliveworkplace.heirloom.lore", quest.poster)),
			plain(Component.translatable("item.aliveworkplace.heirloom.hint", quest.poster)))));
		return stack;
	}

	/** The quest {@code stack} is marked for under {@code key} (and whether it's a reply), or null. */
	@Nullable
	static UUID marked(ItemStack stack, String key) {
		if (stack.isEmpty()) {
			return null;
		}
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		try {
			return tag.contains(key) ? UUID.fromString(tag.getString(key)) : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static boolean isReply(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(REPLY);
	}

	/** Whether {@code player} carries the letter (or the reply) of the quest {@code id}. */
	public static boolean carries(Player player, UUID id, boolean reply) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (id.equals(marked(s, LETTER)) && isReply(s) == reply) {
				return true;
			}
		}
		return false;
	}

	/** An open personal request by its id, with its hall, or null. */
	@Nullable
	static Map.Entry<BlockPos, Quest> find(ServerLevel level, UUID id) {
		for (BlockPos hall : Stories.halls(level)) {
			for (Quest q : PersonalRequests.open(level, hall)) {
				if (q.id.equals(id)) {
					return Map.entry(hall, q);
				}
			}
		}
		return null;
	}

	// --- Right-clicks ---------------------------------------------------------------------------------------------

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, @Nullable net.minecraft.world.phys.EntityHitResult hit) {
		if (player.isSpectator() || !(entity instanceof Villager villager)) {
			return InteractionResult.PASS;
		}
		// B341: vanilla asks the main hand first and, when it's empty, opens the villager's trades, so a letter or heirloom
		// in the off hand was never asked; with the main hand empty, it is handed over on the main hand's turn.
		InteractionHand from = hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty() && isCarried(player.getOffhandItem())
			? InteractionHand.OFF_HAND : hand;
		boolean ours = isCarried(player.getItemInHand(from));
		if (level.isClientSide()) {
			return ours ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return use((ServerPlayer) player, (ServerLevel) level, from, villager) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Whether {@code stack} is a request's letter (or reply) or heirloom, carried to a villager by hand. */
	public static boolean isCarried(ItemStack stack) {
		return marked(stack, LETTER) != null || marked(stack, HEIRLOOM) != null;
	}

	/**
	 * {@code player} right-clicks {@code villager} with what they hold in {@code hand}: the letter to its relative, the
	 * reply or the heirloom to the giver; or, empty-handed, a giver or relative whose letter they've lost writes it again.
	 * Returns whether it was one of these (and the click is used up).
	 */
	public static boolean use(ServerPlayer player, ServerLevel level, InteractionHand hand, Villager villager) {
		ItemStack held = player.getItemInHand(hand);
		UUID letter = marked(held, LETTER);
		if (letter != null) {
			Map.Entry<BlockPos, Quest> found = find(level, letter);
			int i = found == null ? -1 : found.getValue().current();
			if (i < 0 || !(found.getValue().objectives.get(i) instanceof Deliver deliver)) {
				return false;
			}
			Quest quest = found.getValue();
			if (!isReply(held) && quest.progress[i] == 0 && deliver.relative().isPresent() && deliver.relative().get().equals(villager.getUUID())) {
				held.shrink(1);
				delivered(level, found.getKey(), quest, deliver, i, villager, player);
				return true;
			}
			if (isReply(held) && quest.progress[i] == 1 && villager.getUUID().equals(quest.villager)) {
				held.shrink(1);
				say(villager, player, Component.translatable("request.aliveworkplace.letter.reply_read", Component.literal(player.getGameProfile().getName()), deliver.name()));
				progress(level, found.getKey(), quest, i, player);
				return true;
			}
			return false;
		}
		UUID heirloom = marked(held, HEIRLOOM);
		if (heirloom != null) {
			Map.Entry<BlockPos, Quest> found = find(level, heirloom);
			int i = found == null ? -1 : found.getValue().current();
			if (i < 0 || !(found.getValue().objectives.get(i) instanceof Fetch) || !villager.getUUID().equals(found.getValue().villager)) {
				return false;
			}
			held.shrink(1);
			progress(level, found.getKey(), found.getValue(), i, player);
			return true;
		}
		if (!held.isEmpty() || hand != InteractionHand.MAIN_HAND) {
			return false;
		}
		// Empty-handed: a lost letter (or reply) written out again.
		for (BlockPos hall : Stories.halls(level)) {
			for (Quest quest : PersonalRequests.open(level, hall)) {
				int i = quest.current();
				if (i < 0 || !(quest.objectives.get(i) instanceof Deliver deliver) || !PersonalRequests.helps(quest, player.getUUID())) {
					continue;
				}
				boolean giver = quest.progress[i] == 0 && villager.getUUID().equals(quest.villager);
				boolean relative = quest.progress[i] == 1 && deliver.relative().isPresent() && deliver.relative().get().equals(villager.getUUID());
				if ((giver || relative) && !carries(player, quest.id, relative)) {
					give(player, letter(level, hall, quest, deliver, relative));
					say(villager, player, Component.translatable(giver ? "request.aliveworkplace.letter.again" : "request.aliveworkplace.letter.reply_again",
						Component.literal(player.getGameProfile().getName())));
					return true;
				}
			}
		}
		return false;
	}

	/** The relative has the letter: friendship with them, a line in their village's chronicle, and the reply for the giver. */
	private static void delivered(ServerLevel level, BlockPos hall, Quest quest, Deliver deliver, int i, Villager relative, ServerPlayer player) {
		Friendship.favour(relative, player, Friendship.Favour.REQUEST);
		say(relative, player, Component.translatable("request.aliveworkplace.letter.read", quest.poster, Component.literal(player.getGameProfile().getName())));
		give(player, letter(level, hall, quest, deliver, true));
		Chat.chat(player, Component.translatable("message.aliveworkplace.letter.delivered", deliver.name(), quest.poster).withStyle(ChatFormatting.GREEN));
		BlockPos there = deliver.to().orElse(null);
		if (there != null && level.getBlockEntity(there) instanceof VillageHallBlockEntity) {
			Chronicle.atHall(level, there, Chronicle.Kind.FRIEND, Component.translatable("chronicle.aliveworkplace.letter",
				player.getGameProfile().getName(), deliver.name(), quest.poster, home(level, hall)));
		}
		level.playSound(null, relative.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1f, 1f);
		progress(level, hall, quest, i, player);
	}

	private static void progress(ServerLevel level, BlockPos hall, Quest quest, int i, ServerPlayer player) {
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			Stories.progress(level, hall, entity, quest, i, 1, player);
		}
	}

	private static void say(Villager villager, ServerPlayer player, Component line) {
		io.github.jcondedata.aliveworkplace.work.WorkerStatus.set(villager, villager.getDisplayName().copy().withStyle(ChatFormatting.GRAY), -1f,
			line.copy().withStyle(ChatFormatting.ITALIC));
		villager.playSound(SoundEvents.VILLAGER_YES, 0.6f, villager.getVoicePitch());
		Chat.chat(player, HeartEvents.said(villager.getDisplayName(), line));
	}

	// --- The heirloom's place -------------------------------------------------------------------------------------

	/**
	 * Puts each waiting heirloom at its place once a helper comes within {@link #PLACE_NEAR} blocks of it (and its chunk
	 * is loaded): in the nearest chest or barrel there, else a chest minecart, else a suspicious block to brush, else a
	 * new barrel on the ground. Returns how many were put.
	 */
	public static int placeHeirlooms(ServerLevel level) {
		int placed = 0;
		for (BlockPos hall : Stories.halls(level)) {
			for (Quest quest : PersonalRequests.open(level, hall)) {
				int i = quest.current();
				if (i < 0 || quest.placed != null || !(quest.objectives.get(i) instanceof Fetch fetch) || fetch.place().found().isEmpty()) {
					continue;
				}
				BlockPos at = fetch.place().found().get();
				if (!level.isLoaded(at)) {
					continue;
				}
				List<ServerPlayer> near = level.players().stream().filter(p -> PersonalRequests.helps(quest, p.getUUID()) && p.isAlive()
					&& Math.pow(p.getX() - at.getX() - 0.5, 2) + Math.pow(p.getZ() - at.getZ() - 0.5, 2) <= (double) PLACE_NEAR * PLACE_NEAR).toList();
				if (near.isEmpty()) {
					continue;
				}
				quest.placed = put(level, at, heirloom(quest, fetch));
				Stories.Data.get(level).setDirty();
				placed++;
				for (ServerPlayer p : near) {
					Chat.chat(p, Component.translatable("message.aliveworkplace.heirloom.near", quest.poster, fetch.what()).withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		return placed;
	}

	/** Puts {@code item} at the place {@code at}; returns where it went. */
	static BlockPos put(ServerLevel level, BlockPos at, ItemStack item) {
		ChunkPos centre = new ChunkPos(at);
		RandomizableContainerBlockEntity chest = null;
		BrushableBlockEntity brush = null;
		for (int dx = -PLACE_CHUNKS; dx <= PLACE_CHUNKS; dx++) {
			for (int dz = -PLACE_CHUNKS; dz <= PLACE_CHUNKS; dz++) {
				if (!level.getChunkSource().hasChunk(centre.x + dx, centre.z + dz)) {
					continue;
				}
				for (BlockEntity be : level.getChunk(centre.x + dx, centre.z + dz).getBlockEntities().values()) {
					if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && (chest == null
						|| be.getBlockPos().distSqr(at) < chest.getBlockPos().distSqr(at))) {
						chest = (RandomizableContainerBlockEntity) be;
					} else if (be instanceof BrushableBlockEntity b && (brush == null || b.getBlockPos().distSqr(at) < brush.getBlockPos().distSqr(at))) {
						brush = b;
					}
				}
			}
		}
		if (chest != null) {
			chest.unpackLootTable(null);
			chest.setItem(slot(chest.getContainerSize(), chest::getItem, level), item);
			chest.setChanged();
			return chest.getBlockPos().immutable();
		}
		double reach = PLACE_CHUNKS * 16;
		MinecartChest cart = level.getEntitiesOfClass(MinecartChest.class, new AABB(at).inflate(reach), MinecartChest::isAlive).stream()
			.min(Comparator.comparingDouble(c -> c.distanceToSqr(at.getCenter()))).orElse(null);
		if (cart != null) {
			cart.unpackChestVehicleLootTable(null);
			cart.setChestVehicleItem(slot(cart.getContainerSize(), cart::getChestVehicleItem, level), item);
			return cart.blockPosition().immutable();
		}
		if (brush != null) {
			CompoundTag tag = brush.saveWithoutMetadata(level.registryAccess());
			tag.remove("LootTable");
			tag.remove("LootTableSeed");
			tag.put("item", item.save(level.registryAccess()));
			brush.loadWithComponents(tag, level.registryAccess());
			brush.setChanged();
			return brush.getBlockPos().immutable();
		}
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		level.setBlockAndUpdate(ground, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, net.minecraft.core.Direction.UP));
		if (level.getBlockEntity(ground) instanceof BarrelBlockEntity barrel) {
			barrel.setItem(barrel.getContainerSize() / 2, item);
			barrel.setChanged();
		}
		return ground.immutable();
	}

	/** A free slot of a container (a random one), else a random slot. */
	private static int slot(int size, java.util.function.IntFunction<ItemStack> get, ServerLevel level) {
		List<Integer> free = new ArrayList<>();
		for (int s = 0; s < size; s++) {
			if (get.apply(s).isEmpty()) {
				free.add(s);
			}
		}
		return free.isEmpty() ? level.random.nextInt(size) : free.get(level.random.nextInt(free.size()));
	}

	// --- Events ---------------------------------------------------------------------------------------------------

	/** Someone came back from the grave ({@link Graves#revive}): a request to bring them back moves on. */
	public static void onRevived(ServerLevel level, UUID was, Villager back) {
		for (BlockPos hall : Stories.halls(level)) {
			for (Quest quest : PersonalRequests.open(level, hall)) {
				int i = quest.current();
				if (i >= 0 && quest.objectives.get(i) instanceof Revive revive && revive.partner().isPresent()
					&& (revive.partner().get().equals(was) || revive.partner().get().equals(back.getUUID()))
					&& level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
					Villager giver = PersonalRequests.giver(level, quest);
					if (giver != null) {
						remarry(level, hall, giver, back); // (here, as they may come back under a new id)
					}
					Stories.progress(level, hall, entity, quest, i, 1, null);
				}
			}
		}
	}

	/** {@code giver}, widowed and alone, marries {@code back} again: a second wedding. */
	static void remarry(ServerLevel level, BlockPos hall, Villager giver, Villager back) {
		if (Couples.partner(giver) != null) {
			return;
		}
		ModAttachments.LATE_PARTNER.remove(giver);
		ModAttachments.WIDOWED_DAY.remove(giver);
		Couples.wed(level, hall, giver, back);
		Chronicle.atHall(level, hall, Chronicle.Kind.WEDDING, Component.translatable("chronicle.aliveworkplace.second_wedding",
			back.getDisplayName(), giver.getDisplayName()));
	}

	/** Whether {@code a} or {@code b} has a chapel-wedding request open: their wedding waits for the Chapel. */
	public static boolean awaitsChapel(Villager a, Villager b) {
		for (Villager v : List.of(a, b)) {
			Quest quest = PersonalRequests.of(v);
			if (quest != null && quest.objectives.stream().anyMatch(o -> o instanceof Build build && build.wedding())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A request is done ({@link PersonalRequests#done}): who was brought back marries again; a couple whose Chapel is
	 * finished marry there with a feast for the whole village; a heirloom's story goes in the chronicle.
	 */
	static void onDone(ServerLevel level, BlockPos hall, Quest quest, @Nullable Villager giver, List<String> helpers) {
		Component who = helpers.isEmpty() ? Component.translatable("chronicle.aliveworkplace.someone") : Component.literal(String.join(", ", helpers));
		for (Objectives.Objective o : quest.objectives) {
			if (o instanceof Revive revive && giver != null && revive.partner().isPresent()
				&& level.getEntity(revive.partner().get()) instanceof Villager back && back.isAlive()) {
				remarry(level, hall, giver, back);
			} else if (o instanceof Build build && build.wedding() && giver != null) {
				Villager other = courting(level, hall, giver);
				if (other != null) {
					Couples.wed(level, hall, giver, other); // at the Chapel, now that it's finished (Couples.venue)
					Festivals.feast(level, hall);
					Chronicle.atHall(level, hall, Chronicle.Kind.WEDDING, Component.translatable("chronicle.aliveworkplace.chapel_wedding",
						giver.getDisplayName(), other.getDisplayName()));
				}
			} else if (o instanceof Fetch fetch) {
				Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.heirloom." + fetch.heirloom(),
					quest.poster, fetch.what(), Places.name(fetch.place()), who));
			}
		}
	}

	private RequestErrands() {
	}
}
